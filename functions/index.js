/* functions/index.js  — Dream in Dream (Enterprise Billing + Mail/FCM)
   - Android Publisher Subscriptions v2 검증(verifyPlaySubscription)
   - RTDN(Pub/Sub) 수신(onPlayRtdn)
   - 매일 재동기화(reconcileSubscriptions)
   - 기존 SMTP, FCM, 이메일 인증/패스워드 재설정/피드백 처리 포함
*/

require("dotenv").config();

const functionsV1 = require("firebase-functions/v1");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");
const { google } = require("googleapis");

admin.initializeApp();
const db = admin.firestore();

/* ───────────────────────── SMTP ───────────────────────── */
let cachedTransporter = null;
function ensureTransporter() {
  const pass = process.env.SMTP_PASS; // Secrets 권장: firebase functions:secrets:set SMTP_PASS
  const user = process.env.SMTP_USER || "dreamindream@dreamindream.app";
  const host = process.env.SMTP_HOST || "smtp.zoho.com";
  const port = Number(process.env.SMTP_PORT || 465);

  if (!pass) {
    console.error("⛔ SMTP_PASS 미설정");
    return null;
  }
  if (cachedTransporter) return cachedTransporter;

  cachedTransporter = nodemailer.createTransport({
    host, port, secure: port === 465, auth: { user, pass },
  });
  return cachedTransporter;
}

async function sendMail(to, subject, html, replyTo) {
  const t = ensureTransporter();
  if (!t) return;
  const from = process.env.SMTP_FROM || "Dream in Dream <dreamindream@dreamindream.app>";
  await t.sendMail({ from, to, subject, html, replyTo });
}

function esc(s = "") {
  return String(s)
    .replace(/&/g, "&amp;").replace(/</g, "&lt;")
    .replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

/* ────────────────────── Android Publisher ────────────────────── */
async function androidPublisher() {
  const auth = await google.auth.getClient({
    scopes: ["https://www.googleapis.com/auth/androidpublisher"],
  });
  google.options({ auth });
  return google.androidpublisher("v3");
}

function normalizeSubState(v2Data) {
  // subscriptionState: 0=EXPIRED,1=ACTIVE,2=PAUSED,3=IN_GRACE,4=ON_HOLD,5=CANCELED,6=REVOKED
  const map = {
    1: "ENTITLED",
    3: "GRACE",
    4: "HOLD",
    2: "PAUSED",
    5: "CANCELED",
    0: "EXPIRED",
    6: "REVOKED",
  };
  return map[Number(v2Data?.subscriptionState)] || "UNKNOWN";
}

async function getSubV2({ packageName, token }) {
  const ap = await androidPublisher();
  const res = await ap.purchases.subscriptionsv2.get({ packageName, token });
  return res.data;
}

async function writeServerEntitlement({ uid, pkg, token, data }) {
  const now = Date.now();
  const state = normalizeSubState(data);
  const li = data?.lineItems?.[0] || {};
  const node = {
    serverState: state,
    packageName: pkg,
    productId: li.productId || "",
    basePlanId: li.offerDetails?.basePlanId || "",
    offerId: li.offerDetails?.offerId || "",
    purchaseToken: token,
    expiryTimeMillis: Number(li.expiryTimeMillis || 0),
    serverCheckedAt: now,
  };
  await db.doc(`users/${uid}/billing/state`).set(node, { merge: true });
  return state;
}

/* ───────── 1) 구매 직후 검증 (Callable) ───────── */
exports.verifyPlaySubscription = functionsV1
  .runWith({ memory: "512MB", timeoutSeconds: 30, secrets: ["PLAY_PACKAGE"] })
  .https.onCall(async (data, context) => {
    if (!context.auth?.uid) {
      throw new functionsV1.https.HttpsError("unauthenticated", "로그인이 필요합니다.");
    }
    const uid = context.auth.uid;
    const token = String(data?.purchaseToken || "").trim();
    const pkg = process.env.PLAY_PACKAGE || "com.dreamindream.app";

    if (!token) {
      throw new functionsV1.https.HttpsError("invalid-argument", "purchaseToken 누락");
    }

    try {
      const info = await getSubV2({ packageName: pkg, token });
      const state = await writeServerEntitlement({ uid, pkg, token, data: info });
      return { ok: true, state, raw: { subscriptionState: info?.subscriptionState } };
    } catch (e) {
      console.error("verifyPlaySubscription error:", e?.message || e);
      throw new functionsV1.https.HttpsError("internal", "구독 검증 실패");
    }
  });

/* ───────── 2) RTDN (Real-Time Developer Notifications) ─────────
   - Play Console → Pub/Sub 토픽(예: playstore-subscriptions) 연결 필요
*/
exports.onPlayRtdn = functionsV1.pubsub
  .topic("playstore-subscriptions")
  .onPublish(async (message) => {
    try {
      const data = JSON.parse(Buffer.from(message.data, "base64").toString("utf8")) || {};
      const pkg = data.packageName || process.env.PLAY_PACKAGE || "com.dreamindream.app";

      const token =
        data?.subscriptionNotification?.purchaseToken ||
        data?.oneTimeProductNotification?.purchaseToken ||
        null;

      if (!token) {
        console.log("RTDN: token 없음(테스트 알림이거나 비구독):", data?.testNotification || data);
        return;
      }

      // token ↔ uid 역조회: 기존 저장된 state 문서에서 추적
      const q = await db.collectionGroup("state").where("purchaseToken", "==", token).get();
      const uids = [];
      q.forEach((d) => uids.push(d.ref.path.split("/")[1]));

      const info = await getSubV2({ packageName: pkg, token });
      if (uids.length === 0) {
        console.log("RTDN: uid 미발견. 검증만 수행:", { tokenSuffix: String(token).slice(-8) });
        return;
      }
      for (const uid of uids) {
        await writeServerEntitlement({ uid, pkg, token, data: info });
      }
      console.log("RTDN 처리 완료:", { tokenSuffix: String(token).slice(-8), users: uids.length });
    } catch (e) {
      console.error("RTDN handler error:", e?.message || e);
    }
  });

/* ───────── 3) 매일 재동기화 (서버 권한이 최종 권위) ───────── */
exports.reconcileSubscriptions = onSchedule(
  { schedule: "0 3 * * *", timeZone: "Asia/Seoul" }, // 매일 03:00 KST
  async () => {
    const pkg = process.env.PLAY_PACKAGE || "com.dreamindream.app";
    const snap = await db.collectionGroup("state").get();
    let ok = 0, fail = 0;

    for (const doc of snap.docs) {
      const uid = doc.ref.path.split("/")[1];
      const token = doc.get("purchaseToken");
      if (!token) continue;
      try {
        const info = await getSubV2({ packageName: pkg, token });
        await writeServerEntitlement({ uid, pkg, token, data: info });
        ok++;
      } catch (e) {
        console.warn("reconcile error:", uid, String(token).slice(-8), e?.message);
        fail++;
      }
    }
    console.log(`reconcile done: ok=${ok} fail=${fail} total=${snap.size}`);
  }
);

/* ───────── FCM 전송 래퍼(기존 유지) ───────── */
const messaging = admin.messaging();
async function sendAllCompat(messages) {
  if (typeof messaging.sendAll === "function") {
    return await messaging.sendAll(messages);
  }
  const settled = await Promise.allSettled(messages.map(m => messaging.send(m)));
  const responses = settled.map(s =>
    s.status === "fulfilled" ? { success: true, messageId: s.value }
                              : { success: false, error: s.reason }
  );
  return {
    responses,
    successCount: responses.filter(r => r.success).length,
    failureCount: responses.filter(r => !r.success).length,
  };
}

/* ───────── 매일 한국시간 09:00 푸시 (기존 유지) ───────── */
exports.sendDailyPush = onSchedule(
  { schedule: "0 9 * * *", timeZone: "Asia/Seoul" },
  async () => {
    const snap = await db.collection("users").get();
    const targets = [];
    snap.forEach(doc => {
      const u = doc.data();
      if (u && u.fcmToken) targets.push({ uid: doc.id, token: u.fcmToken });
    });

    if (targets.length === 0) {
      console.log("ℹ️ 푸시 전송 대상 없음 (fcmToken 미등록)");
      return;
    }

    const title = "오늘의 꿈해몽 🔮";
    const body  = "새로운 하루, 새로운 해몽을 받아보세요!";
    const channelId = "dreamin_channel";

    const chunk = 500;
    for (let i = 0; i < targets.length; i += chunk) {
      const slice = targets.slice(i, i + chunk);
      const messages = slice.map(({ token }) => ({
        token,
        android: { priority: "high", notification: { channelId, title, body } },
        notification: { title, body },
        data: { navigateTo: "fortune", origin: "daily_9_kst" },
      }));

      const res = await sendAllCompat(messages);

      // 죽은 토큰 정리
      const cleanups = [];
      res.responses.forEach((r, idx) => {
        if (!r.success) {
          const { uid, token } = slice[idx];
          const code = r.error?.errorInfo?.code || r.error?.code || "";
          const mustDelete =
            code.includes("registration-token-not-registered") ||
            code.includes("invalid-registration-token") ||
            code.includes("NOT_FOUND");
          if (mustDelete) {
            cleanups.push(
              db.collection("users").doc(uid)
                .update({ fcmToken: admin.firestore.FieldValue.delete() })
            );
          }
        }
      });
      if (cleanups.length) await Promise.allSettled(cleanups);
    }
  }
);

/* ───────── 이메일 인증/패스워드 재설정/결과/피드백 (기존 유지) ───────── */
exports.sendVerificationEmailOnSignup = functionsV1
  .runWith({ secrets: ["SMTP_PASS"] })
  .auth.user().onCreate(async (user) => {
    if (!user.email) return;
    const link = await admin.auth().generateEmailVerificationLink(user.email, {
      url: "https://dreamindream.app", handleCodeInApp: false,
    });
    const html = `
    <html><body style="font-family:Pretendard,system-ui,Segoe UI,Roboto,sans-serif;background:#0D0B1E;padding:30px;">
      <div style="max-width:640px;margin:auto;background:rgba(29,27,58,0.7);backdrop-filter:blur(12px);border-radius:18px;padding:40px;color:#F3F8FC;">
        <h2 style="color:#C6A0FF;margin-top:16px;">🌙 Dream in Dream 가입을 환영합니다!</h2>
        <p style="margin:16px 0; color:#000;">안녕하세요 ${esc(user.displayName) || "사용자"} 님, 이메일 주소를 인증해주세요.</p>
        <a href="${link}" style="display:inline-block;margin-top:12px;padding:12px 18px;border-radius:10px;background:#7A55D3;color:#fff;text-decoration:none;font-weight:700;">이메일 인증하기</a>
      </div>
    </body></html>`;
    await sendMail(user.email, "DreamInDream - 이메일 인증 안내", html);
  });

exports.sendCustomPasswordResetEmail = functionsV1
  .runWith({ secrets: ["SMTP_PASS"] })
  .https.onCall(async (data) => {
    const email = String(data?.email || "").trim();
    const displayName = (data?.displayName || "사용자").toString();
    if (!email) throw new functionsV1.https.HttpsError("invalid-argument", "email이 필요합니다.");

    try {
      const link = await admin.auth().generatePasswordResetLink(email, {
        url: "https://dreamindream.app", handleCodeInApp: false,
      });
      const html = `
      <html><body style="font-family:Pretendard,system-ui,Segoe UI,Roboto,sans-serif;background:#0D0B1E;padding:30px;">
        <div style="max-width:640px;margin:auto;background:rgba(29,27,58,0.7);backdrop-filter:blur(12px);border-radius:18px;padding:40px;color:#F3F8FC;">
          <h2 style="color:#E84545;margin-top:16px;">🔐 비밀번호 재설정</h2>
          <p style="margin:16px 0; color:#000;">안녕하세요 ${esc(displayName)} 님, 아래 버튼을 눌러 새 비밀번호를 설정해 주세요.</p>
          <a href="${link}" style="display:inline-block;margin-top:12px;padding:12px 18px;border-radius:10px;background:#E84545;color:#fff;text-decoration:none;font-weight:700;">비밀번호 재설정하기</a>
        </div>
      </body></html>`;
      await sendMail(email, "DreamInDream - 비밀번호 재설정", html);
      return { ok: true, sent: true };
    } catch (err) {
      const code = err?.code || err?.errorInfo?.code || "";
      if (code.includes("user-not-found") || code.includes("NOT_FOUND")) {
        console.log(`ℹ️ reset: user-not-found (${email}) → 보안상 성공처럼 응답`);
        return { ok: true, sent: false };
      }
      console.error("reset error:", err);
      throw new functionsV1.https.HttpsError("internal", "비밀번호 재설정 처리 오류");
    }
  });

exports.sendDreamResult = functionsV1
  .runWith({ secrets: ["SMTP_PASS"] })
  .firestore
  .document("users/{userId}/dreams/{date}/entries/{entryId}")
  .onCreate(async (snap, context) => {
    const { dream, result, title } = snap.data() || {};
    const uid = context.params.userId;
    const entryId = context.params.entryId;

    try {
      const user = await admin.auth().getUser(uid);
      const email = user.email; if (!email) return;

      const pre = (text = "") =>
        `<pre style="white-space:pre-wrap;word-break:break-word;margin:0;line-height:1.6">${esc(text)}</pre>`;

      const card = ({ heading, contentHtml, tone }) => `
        <section style="border:1px solid #2b2a50;border-radius:14px;padding:16px;margin:12px 0;background:${tone === "cyan" ? "#0E1630" : "#151233"};color:#E6E9F4">
          <h3 style="margin:0 0 8px 0;font-size:16px;color:#C6A0FF">${esc(heading)}</h3>
          <div style="font-size:14px">${contentHtml}</div>
        </section>`;

      const bodyHtml = `
        ${title ? card({ heading: "제목", contentHtml: esc(title), tone: "rose" }) : ""}
        ${card({ heading: "당신의 꿈", contentHtml: pre(dream || "작성된 내용이 없습니다."), tone: "indigo" })}
        ${card({ heading: "AI 인사이트", contentHtml: pre(result || "해석 내용이 없습니다."), tone: "cyan" })}
      `;

      const deepLink = `dreamindream://dream?entryId=${encodeURIComponent(entryId)}&uid=${encodeURIComponent(uid)}`;

      const html = `
      <html><body style="font-family:Pretendard,system-ui,Segoe UI,Roboto,sans-serif;background:#0D0B1E;padding:30px;">
        <div style="max-width:640px;margin:auto;background:rgba(29,27,58,0.7);backdrop-filter:blur(12px);border-radius:18px;padding:40px;color:#F3F8FC;">
          <h2 style="color:#C6A0FF;margin-top:16px;">🔮 오늘의 꿈 해몽 결과</h2>
          ${bodyHtml}
          <a href="${deepLink}" style="display:inline-block;margin-top:12px;padding:12px 18px;border-radius:10px;background:#7A55D3;color:#fff;text-decoration:none;font-weight:700;">앱에서 자세히 보기</a>
        </div>
      </body></html>`;

      await sendMail(email, "DreamInDream - 오늘의 해몽 결과", html);
    } catch (e) {
      console.error("sendDreamResult error:", e);
    }
  });

exports.onFeedbackCreated = functionsV1
  .runWith({ secrets: ["SMTP_PASS"] })
  .firestore
  .document("feedback/{id}")
  .onCreate(async (snap) => {
    const d = snap.data() || {};
    const to = process.env.SMTP_TO || "dreamindream@dreamindream.app";
    const created = d.createdAtStr || "";
    const info = d.info || {};

    const pre = (text = "") =>
      `<pre style="white-space:pre-wrap;word-break:break-word;margin:0;line-height:1.6">${esc(text)}</pre>`;

    const section = (heading = "", innerHtml = "") => `
      <section style="border:1px solid #dfe3ee;border-radius:12px;padding:14px 16px;margin:12px 0;background:#fafbff">
        ${heading ? `<h3 style="margin:0 0 8px 0;font-size:15px;color:#374151">${esc(heading)}</h3>` : ""}
        <div style="font-size:14px;color:#111827">${innerHtml || ""}</div>
      </section>`;

    const rawTitle = (d.title || "").toString().trim();
    const msg = (d.message || "").toString();
    const fallbackTitle = (msg.split(/\r?\n/)[0] || "").slice(0, 60);
    const finalTitle = rawTitle || fallbackTitle || "제목 없음";

    const metaTable = `
      <table cellpadding="0" cellspacing="0" style="width:100%;font-size:13px;color:#111827">
        <tr><td style="padding:6px 0;width:120px;opacity:.7">앱</td><td>${esc(d.app || "DreamInDream")}</td></tr>
        <tr><td style="padding:6px 0;opacity:.7">앱버전</td><td>${esc(info.appVersion || "")}</td></tr>
        <tr><td style="padding:6px 0;opacity:.7">OS</td><td>${esc(info.os || "")} (SDK ${esc(String(info.sdk || ""))})</td></tr>
        <tr><td style="padding:6px 0;opacity:.7">디바이스</td><td>${esc(info.device || "")}</td></tr>
        <tr><td style="padding:6px 0;opacity:.7">유저ID</td><td>${esc(info.userId || "")}</td></tr>
        <tr><td style="padding:6px 0;opacity:.7">설치ID</td><td>${esc(info.installId || "")}</td></tr>
      </table>`;

    const bodyHtml = `
      ${section("제목", esc(finalTitle))}
      ${d.contact ? section("보낸이(연락처)", esc(d.contact)) : ""}
      ${section("", pre(msg))}
      ${section("디바이스/앱 정보", metaTable)}
      ${d.attachmentUrl ? section("첨부", `<a href="${esc(d.attachmentUrl)}" style="color:#2563eb">첨부 보기</a>`) : ""}`

    const html = `
      <html>
        <body style="font-family:system-ui,-apple-system,Segoe UI,Roboto,Pretendard,sans-serif;background:#f5f7fb;margin:0;padding:24px;color:#111827">
          <div style="max-width:720px;margin:0 auto;background:#fff;border:1px solid #e5e7eb;border-radius:14px;padding:18px 20px">
            <header style="margin:0 0 12px 0">
              <h1 style="margin:0 0 4px 0;font-size:18px;color:#111827">📮 새 문의/피드백 도착</h1>
              ${created ? `<div style="font-size:12px;color:#6b7280">${esc(created)}</div>` : ""}
            </header>
            ${bodyHtml}
            <footer style="margin-top:16px;font-size:12px;color:#6b7280;opacity:.8">
              Dream in Dream • ${new Date().getFullYear()}
            </footer>
          </div>
        </body>
      </html>`;

    const opts = {
      to,
      subject: `[DreamInDream] ${finalTitle}${created ? ` (${created})` : ""}`,
      html,
    };
    if (d.contact && String(d.contact).includes("@")) {
      opts.replyTo = d.contact;
    }
    await sendMail(opts.to, opts.subject, opts.html, opts.replyTo);
  });
