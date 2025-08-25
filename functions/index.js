// functions/index.js
require("dotenv").config();

const functionsV1 = require("firebase-functions/v1");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");

admin.initializeApp();

/* ───────────── SMTP (Secrets 또는 .env) ───────────── */
let cachedTransporter = null;
function ensureTransporter() {
  // Secrets 또는 .env에서 주입
  const pass = process.env.SMTP_PASS;
  const user = process.env.SMTP_USER || "dreamindream@dreamindream.app";
  const host = process.env.SMTP_HOST || "smtp.zoho.com";
  const port = Number(process.env.SMTP_PORT || 465);

  if (!pass) {
    console.error("⛔ SMTP_PASS 미설정 (firebase functions:secrets:set SMTP_PASS 필요)");
    return null;
  }
  if (cachedTransporter) return cachedTransporter;

  cachedTransporter = nodemailer.createTransport({
    host,
    port,
    secure: port === 465,
    auth: { user, pass },
  });
  return cachedTransporter;
}

async function sendMail(to, subject, html) {
  const transporter = ensureTransporter();
  if (!transporter) {
    console.error("⛔ transporter 없음 - 메일 전송 생략");
    return;
  }
  try {
    await transporter.sendMail({
      from: `DreamInDream <${process.env.SMTP_FROM || "dreamindream@dreamindream.app"}>`,
      to,
      subject,
      html,
    });
    console.log(`📨 메일 전송 성공: ${to} (${subject})`);
  } catch (e) {
    console.error("❌ 메일 전송 실패:", e.message);
    if (e.response) console.error("↪️ SMTP 응답:", e.response);
  }
}

/* HTML 이스케이프 */
function esc(s = "") {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

/* ───────────── V2 스케줄 푸시 (그대로 유지) ───────────── */
exports.sendDailyPush = onSchedule(
  { schedule: "0 9 * * *", timeZone: "Asia/Seoul" },
  async () => {
    const db = admin.firestore();
    const snap = await db.collection("users").get();
    const messages = [];
    snap.forEach((doc) => {
      const u = doc.data();
      if (u.fcmToken) {
        messages.push({
          notification: { title: "오늘의 꿈해몽 🔮", body: "새로운 하루, 새로운 해몽을 받아보세요!" },
          token: u.fcmToken,
        });
      }
    });
    if (messages.length > 0) {
      await admin.messaging().sendAll(messages);
      console.log(`${messages.length}개의 푸시 메시지 전송 완료`);
    } else {
      console.log("푸시 전송 대상 없음");
    }
  }
);

/* ───────────── 가입 시: 인증 메일 (검정 인사/안내) ───────────── */
exports.sendWelcomeEmail = functionsV1
  .runWith({ secrets: ["SMTP_PASS"] })
  .auth.user()
  .onCreate(async (user) => {
    const verifyLink = await admin.auth().generateEmailVerificationLink(user.email);

    const html = `
    <html><body style="font-family:Pretendard,system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:#0D0B1E;padding:30px;">
      <div style="max-width:640px;margin:auto;background:rgba(255,255,255,0.08);backdrop-filter:blur(12px);border-radius:18px;padding:40px;text-align:center;color:#F3F8FC;font-size:15px;">
        <img src="https://dreamindream-439e6.web.app/assets/star_cloud.png" width="90" />
        <h2 style="color:#C6A0FF;margin-top:16px;">🌙 Dream in Dream 가입을 환영합니다!</h2>
        <p style="margin:16px 0; color:#000;">안녕하세요 ${esc(user.displayName) || "사용자"} 님,</p>
        <p style="color:#000;">계정을 활성화하려면 아래 버튼을 눌러 이메일 주소를 인증해주세요:</p>
        <a href="${verifyLink}" style="display:inline-block;margin-top:28px;padding:14px 28px;background:#7A55D3;color:#FFF;text-decoration:none;border-radius:8px;font-weight:bold;box-shadow:0 0 12px rgba(122,85,211,0.6);">이메일 인증하기</a>
        <p style="margin-top:32px;font-size:13px;color:#AAA;">이 인증 링크는 일정 시간 후 만료됩니다.</p>
        <p style="font-size:13px;color:#AAA;">잘못된 메일이라면 무시해주세요.</p>
      </div>
    </body></html>`;

    await sendMail(user.email, "DreamInDream - 이메일 인증 안내", html);
  });

/* ───────────── 비밀번호 재설정 (Callable) ─────────────
   - 없는 이메일이어도 계정 열거 방지 위해 항상 ok:true 반환
---------------------------------------------------------------- */
exports.sendCustomPasswordResetEmail = functionsV1
  .runWith({ secrets: ["SMTP_PASS"] })
  .https.onCall(async (data, context) => {
    const email = String(data?.email || "").trim();
    const displayName = (data?.displayName || "사용자").toString();
    if (!email) {
      throw new functionsV1.https.HttpsError("invalid-argument", "email이 필요합니다.");
    }

    try {
      const link = await admin.auth().generatePasswordResetLink(email, {
        url: "https://dreamindream.app",
        handleCodeInApp: false,
      });

      const html = `
      <html><body style="font-family:Pretendard,system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:#0D0B1E;padding:30px;">
        <div style="max-width:640px;margin:auto;background:rgba(255,255,255,0.08);backdrop-filter:blur(12px);border-radius:18px;padding:40px;text-align:center;color:#F3F8FC;font-size:15px;">
          <img src="https://dreamindream-439e6.web.app/assets/star_cloud.png" width="90" />
          <h2 style="color:#FFB3C1;margin-top:16px;">🔑 비밀번호 재설정 안내</h2>
          <p style="margin:16px 0; color:#000;">안녕하세요 ${esc(displayName)} 님,</p>
          <p style="color:#000;">아래 버튼을 눌러 새 비밀번호를 설정해 주세요.</p>
          <a href="${link}" style="display:inline-block;margin-top:28px;padding:14px 28px;background:#E84545;color:#FFF;text-decoration:none;border-radius:8px;font-weight:bold;box-shadow:0 0 12px rgba(232,69,69,0.6);">비밀번호 재설정하기</a>
          <p style="margin-top:32px;font-size:13px;color:#AAA;">이 링크는 일정 시간 후 만료됩니다.</p>
          <p style="font-size:13px;color:#AAA;">본인이 요청하지 않았다면 무시해 주세요.</p>
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

/* ───────────── 꿈 저장 시: 해몽 결과 메일 ───────────── */
exports.sendDreamResult = functionsV1
  .runWith({ secrets: ["SMTP_PASS"] })
  .firestore
  .document("users/{userId}/dreams/{date}/entries/{entryId}")
  .onCreate(async (snap, context) => {
    const { dream, result } = snap.data() || {};
    const uid = context.params.userId;

    // 1) Auth에서 이메일
    let email = null;
    try {
      const userRecord = await admin.auth().getUser(uid);
      email = userRecord.email || null;
    } catch (_) {}

    // 2) Firestore 보조
    if (!email) {
      try {
        const doc = await admin.firestore().collection("users").doc(uid).get();
        if (doc.exists) email = doc.data().email || null;
      } catch (_) {}
    }

    if (!email || email === "guest" || email === "unknown") {
      console.log(`ℹ️ dream mail skip: no valid email (uid=${uid})`);
      return;
    }

    const html = `
    <html><body style="font-family:Pretendard,system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:#0D0B1E;padding:30px;">
      <div style="max-width:640px;margin:auto;background:rgba(255,255,255,0.08);backdrop-filter:blur(12px);border-radius:18px;padding:40px;color:#F3F8FC;">
        <h2 style="text-align:center;color:#9BE7FF;">🔮 오늘의 꿈 해몽 결과</h2>
        <p><strong>당신의 꿈:</strong></p>
        <blockquote style="background:#1A1333;padding:16px;border-radius:8px;">${esc(dream)}</blockquote>
        <p><strong>AI 해석:</strong></p>
        <blockquote style="background:#132A40;padding:16px;border-radius:8px;">${esc(result)}</blockquote>
        <p style="margin-top:28px;font-size:13px;color:#AAA;text-align:center;">오늘 하루도 꿈처럼 빛나길 바랍니다 ✨</p>
      </div>
    </body></html>`;

    await sendMail(email, "DreamInDream - 오늘의 해몽 결과", html);
  });
