// functions/index.js
require("dotenv").config();

const functionsV1 = require("firebase-functions/v1");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");

admin.initializeApp();

/* ───────── SMTP ───────── */
let cachedTransporter = null;
function ensureTransporter() {
  const pass = process.env.SMTP_PASS; // Secret 또는 .env
  const user = process.env.SMTP_USER || "dreamindream@dreamindream.app";
  const host = process.env.SMTP_HOST || "smtp.zoho.com";
  const port = Number(process.env.SMTP_PORT || 465);

  if (!pass) {
    console.error("⛔ SMTP_PASS 미설정 (firebase functions:secrets:set SMTP_PASS 필요)");
    return null;
  }
  if (cachedTransporter) return cachedTransporter;

  cachedTransporter = nodemailer.createTransport({
    host, port, secure: port === 465, auth: { user, pass },
  });
  return cachedTransporter;
}

async function sendMail(to, subject, html) {
  const t = ensureTransporter();
  if (!t) return;
  try {
    await t.sendMail({
      from: `DreamInDream <${process.env.SMTP_FROM || "dreamindream@dreamindream.app"}>`,
      to, subject, html,
    });
    console.log(`📨 메일 전송 성공: ${to} (${subject})`);
  } catch (e) {
    console.error("❌ 메일 전송 실패:", e.message);
    if (e.response) console.error("↪️ SMTP 응답:", e.response);
  }
}

/* HTML escape */
function esc(s = "") {
  return String(s)
    .replace(/&/g, "&amp;").replace(/</g, "&lt;")
    .replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

/* ───────── FCM 전송(버전 호환 래퍼) ───────── */
const messaging = admin.messaging();

/** admin.messaging().sendAll이 없으면 개별 send로 폴백 */
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

/* ───────── 매일 한국시간 09:00 푸시 ───────── */
exports.sendDailyPush = onSchedule(
  { schedule: "0 9 * * *", timeZone: "Asia/Seoul" },
  async () => {
    const db = admin.firestore();
    const snap = await db.collection("users").get();

    // uid와 token 같이 보관 (죽은 토큰 정리용)
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
    let totalSent = 0, totalFail = 0, totalCleaned = 0;

    for (let i = 0; i < targets.length; i += chunk) {
      const slice = targets.slice(i, i + chunk);

      const messages = slice.map(({ token }) => ({
        token,
        android: {
          priority: "high",
          notification: { channelId, title, body },
        },
        notification: { title, body },
        data: { navigateTo: "fortune", origin: "daily_9_kst" },
      }));

      const res = await sendAllCompat(messages);
      totalSent += res.successCount;
      totalFail += res.failureCount;

      // 죽은 토큰 정리
      const cleanups = [];
      res.responses.forEach((r, idx) => {
        if (!r.success) {
          const errCode =
            r.error?.errorInfo?.code || r.error?.code || r.error?.message || "";
          const { uid, token } = slice[idx];
          console.warn("⚠️ send error:", errCode, "uid:", uid, "tokenSuffix:", token?.slice(-8));

          const mustDelete =
            errCode.includes("registration-token-not-registered") ||
            errCode.includes("invalid-registration-token") ||
            errCode.includes("NOT_FOUND");

          if (mustDelete) {
            cleanups.push(
              db.collection("users").doc(uid)
                .update({ fcmToken: admin.firestore.FieldValue.delete() })
                .then(() => { totalCleaned += 1; console.log("🧹 dead token 삭제 완료 uid:", uid); })
                .catch(e => console.error("🧹 dead token 삭제 실패 uid:", uid, e.message))
            );
          }
        }
      });
      if (cleanups.length) await Promise.allSettled(cleanups);

      console.log(`🧩 배치 ${i / chunk + 1}: 성공 ${res.successCount} / 실패 ${res.failureCount} / 정리 ${cleanups.length}`);
    }

    console.log(`✅ 푸시 전송 완료: 성공 ${totalSent} / 실패 ${totalFail} / 정리 ${totalCleaned} / 총 대상 ${targets.length}`);
  }
);

/* ───────── 신규 가입: 이메일 인증 ───────── */
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

/* ───────── 비밀번호 재설정(Callable) ───────── */
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

/* ───────── 꿈 저장 시: 결과 메일 ───────── */
exports.sendDreamResult = functionsV1
  .runWith({ secrets: ["SMTP_PASS"] })
  .firestore
  .document("users/{userId}/dreams/{date}/entries/{entryId}")
  .onCreate(async (snap, context) => {
    const { dream, result } = snap.data() || {};
    const uid = context.params.userId;
    try {
      const user = await admin.auth().getUser(uid);
      const email = user.email; if (!email) return;
      const html = `
      <html><body style="font-family:Pretendard,system-ui,Segoe UI,Roboto,sans-serif;background:#0D0B1E;padding:30px;">
        <div style="max-width:640px;margin:auto;background:rgba(29,27,58,0.7);backdrop-filter:blur(12px);border-radius:18px;padding:40px;color:#F3F8FC;">
          <h2 style="text-align:center;color:#9BE7FF;">🔮 오늘의 꿈 해몽 결과</h2>
          <p><strong>당신의 꿈:</strong></p>
          <blockquote style="background:#1A1333;padding:16px;border-radius:8px;">${esc(dream || "")}</blockquote>
          <p><strong>AI 해석:</strong></p>
          <blockquote style="background:#132A40;padding:16px;border-radius:8px;">${esc(result || "")}</blockquote>
          <p style="margin-top:28px;font-size:13px;color:#AAA;text-align:center;">오늘 하루도 꿈처럼 빛나길 바랍니다 ✨</p>
        </div>
      </body></html>`;
      await sendMail(email, "DreamInDream - 오늘의 해몽 결과", html);
    } catch (e) {
      console.error("sendDreamResult error:", e);
    }
  });
