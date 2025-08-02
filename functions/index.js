// ✅ Firebase Functions 전체 코드 (.env 기반 / SMTP 오류 로그 포함)
require("dotenv").config(); // .env 파일 읽기

const functionsV1 = require("firebase-functions/v1");
const functions = require("firebase-functions");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");
const { onSchedule } = require("firebase-functions/v2/scheduler");

admin.initializeApp();

const smtpPass = process.env.SMTP_PASS;
let transporter;

if (!smtpPass) {
  console.warn("⚠️ SMTP_PASS 환경변수가 설정되지 않았습니다");
} else {
  transporter = nodemailer.createTransport({
    host: "smtp.zoho.com",
    port: 465,
    secure: true,
    auth: {
      user: "dreamindream@dreamindream.app",
      pass: smtpPass,
    },
  });
}

// ✅ 메일 전송 함수 (에러 로그 포함)
async function sendMail(to, subject, html) {
  if (!transporter) {
    console.error("⛔ transporter 없음 - 메일 전송 생략됨");
    return;
  }

  try {
    await transporter.sendMail({
      from: 'DreamInDream <dreamindream@dreamindream.app>',
      to,
      subject,
      html,
    });
    console.log(`📨 메일 전송 성공: ${to}`);
  } catch (e) {
    console.error("❌ 메일 전송 실패:", e.message);
    console.error("↪️ 응답 내용:", e.response || "(응답 없음)");
  }
}

// ✅ v2: 매일 푸시
exports.sendDailyPush = onSchedule(
  {
    schedule: "0 9 * * *",
    timeZone: "Asia/Seoul",
  },
  async () => {
    const db = admin.firestore();
    const usersSnapshot = await db.collection("users").get();
    const messages = [];

    usersSnapshot.forEach((doc) => {
      const user = doc.data();
      if (user.fcmToken) {
        messages.push({
          notification: {
            title: "오늘의 꿈해몽 🔮",
            body: "새로운 하루, 새로운 해몽을 받아보세요!",
          },
          token: user.fcmToken,
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

// ✅ 가입 시 이메일 인증 메일 발송
exports.sendWelcomeEmail = functionsV1.auth.user().onCreate(async (user) => {
  const verifyLink = await admin.auth().generateEmailVerificationLink(user.email);
  const html = `
  <html><body style="font-family: Pretendard, sans-serif; background: #FFF9E5; padding: 20px;">
    <div style="max-width: 600px; margin: auto; background: white; border-radius: 12px; padding: 32px; text-align: center;">
      <img src="https://dreamindream-439e6.web.app/assets/star_cloud.png" width="80" />
      <h2 style="color: #222;">🌙 Dream in Dream 가입을 환영합니다!</h2>
      <p>안녕하세요 ${user.displayName || '사용자'} 님,</p>
      <p>계정을 활성화하려면 아래 버튼을 눌러 이메일 주소를 인증해주세요:</p>
      <a href="${verifyLink}" style="display:inline-block;margin-top:20px;padding:12px 24px;background:#FBC02D;color:#000;text-decoration:none;border-radius:6px;font-weight:bold;">이메일 인증하기</a>
      <p style="margin-top: 32px; font-size: 13px; color: gray;">이 인증 링크는 일정 시간 후 만료됩니다.</p>
      <p style="font-size: 13px; color: gray;">잘못된 메일이라면 무시해주세요.</p>
    </div></body></html>`;
  await sendMail(user.email, "DreamInDream - 이메일 인증 안내", html);
});

// ✅ 해몽 결과 저장 시 이메일 전송
exports.sendDreamResult = functionsV1.firestore
  .document("users/{userId}/dreams/{date}/entries/{entryId}")
  .onCreate(async (snap, context) => {
    const { dream, result } = snap.data();
    const userId = context.params.userId;
    const userRecord = await admin.auth().getUser(userId);
    const email = userRecord.email;

    if (!email) {
      console.error("❌ 이메일 없음 - 전송 생략");
      return;
    }

    const html = `
    <html><body style="font-family: Pretendard, sans-serif; background: #FFF9E5; padding: 20px;">
      <div style="max-width: 600px; margin: auto; background: white; border-radius: 12px; padding: 32px;">
        <h2 style="text-align:center; color: #4B0082;">🔮 오늘의 꿈 해몽 결과</h2>
        <p><strong>당신의 꿈:</strong></p>
        <blockquote style="background:#f9f1d6;padding:12px;border-radius:8px;">${dream}</blockquote>
        <p><strong>AI 해석:</strong></p>
        <blockquote style="background:#e2f7f7;padding:12px;border-radius:8px;">${result}</blockquote>
        <p style="margin-top: 24px; font-size: 13px; color: gray; text-align: center;">오늘 하루도 꿈처럼 빛나길 바랍니다 ✨</p>
      </div></body></html>`;

    await sendMail(email, "DreamInDream - 오늘의 해몽 결과", html);
  });
