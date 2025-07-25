const {onSchedule} = require("firebase-functions/v2/scheduler");
const admin = require("firebase-admin");
admin.initializeApp();

exports.sendDailyPush = onSchedule(
    {
        schedule: "0 8 * * *",
        timeZone: "Asia/Seoul",
    },
    async (event) => {
        const usersSnapshot = await admin.firestore().collection("users").get();

        const messages = [];
        usersSnapshot.forEach((doc) => {
            const user = doc.data();
            if (user.fcmToken) {
                messages.push({
                    notification: {
                        title: "오늘의 꿈해몽 🔮",
                        body: ""새로운 하루, 새로운 해몽을 받아보세요!",
                    },
                    token: user.fcmToken,
                });
            }
        });

        if (messages.length > 0) {
            await admin.messaging().sendAll(messages);
            console.log("푸시 발송 성공:", messages.length);
        } else {
            console.log("푸시 보낼 토큰 없음");
        }
        return null;
    },
);
