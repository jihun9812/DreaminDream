const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

exports.sendDailyPush = functions.pubsub.schedule("0 9 * * *")  // 한국 오전 9시
    .timeZone("Asia/Seoul")
    .onRun(async (context) => {
        const snapshot = await admin.firestore().collection("users").get();

        const messages = [];

        snapshot.forEach((doc) => {
            const user = doc.data();
            if (user.fcmToken) {
                messages.push({
                    notification: {
                        title: "오늘의 꿈해몽 🔮",
                        body: "새로운 하루, 새로운 해몽을 받아보세요!",
                    },
                    data: {
                        navigateTo: "dream",
                    },
                    token: user.fcmToken,
                });
            }
        });

        if (messages.length > 0) {
            await admin.messaging().sendAll(messages);
            console.log(`✅ 예약 푸시 완료: ${messages.length}명 대상`);
        } else {
            console.log("⚠️ 예약 푸시 대상 없음");
        }
    });
