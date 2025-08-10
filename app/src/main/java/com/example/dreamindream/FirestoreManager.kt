package com.example.dreamindream

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*

object FirestoreManager {
    private val db = FirebaseFirestore.getInstance()

    fun saveDream(userId: String, dream: String, result: String, onComplete: (() -> Unit)? = null) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
        val dateKey = sdf.format(Date())
        val entry = mapOf(
            "dream" to dream,
            "result" to result,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("users")
            .document(userId)
            .collection("dreams")
            .document(dateKey)
            .collection("entries")
            .add(entry)
            .addOnSuccessListener {
                Log.d("Firestore", "꿈 저장 성공")
                onComplete?.invoke()
            }
            .addOnFailureListener {
                Log.e("Firestore", "꿈 저장 실패", it)
            }
    }

    fun getAllDreamDates(context: Context, userId: String, onResult: (Set<LocalDate>) -> Unit) {
        val prefs = context.getSharedPreferences("dream_history_$userId", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        db.collection("users")
            .document(userId)
            .collection("dreams")
            .get()
            .addOnSuccessListener { snapshot ->
                val dateSet = mutableSetOf<LocalDate>()
                val documents = snapshot.documents
                var completed = 0

                if (documents.isEmpty()) {
                    onResult(emptySet())
                    return@addOnSuccessListener
                }

                for (doc in documents) {
                    val dateStr = doc.id
                    val parsedDate = try { LocalDate.parse(dateStr) } catch (_: Exception) { null }
                    if (parsedDate != null) dateSet.add(parsedDate)

                    doc.reference.collection("entries").get()
                        .addOnSuccessListener { entries ->
                            val jsonArray = JSONArray()
                            for (entry in entries) {
                                val dream = entry.getString("dream") ?: ""
                                val result = entry.getString("result") ?: ""
                                val obj = JSONObject().apply {
                                    put("dream", dream)
                                    put("result", result)
                                }
                                jsonArray.put(obj)
                            }

                            editor.putString(dateStr, jsonArray.toString())
                            editor.apply()

                            completed++
                            if (completed == documents.size) {
                                onResult(dateSet)
                            }
                        }
                        .addOnFailureListener {
                            completed++
                            if (completed == documents.size) {
                                onResult(dateSet)
                            }
                        }
                }
            }
            .addOnFailureListener {
                onResult(emptySet())
            }
    }

    fun saveUserProfile(userId: String, userProfile: Map<String, Any>, onComplete: (() -> Unit)? = null) {
        db.collection("users")
            .document(userId)
            .set(userProfile, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("Firestore", "프로필 저장 성공")
                onComplete?.invoke()
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "프로필 저장 실패", e)
                onComplete?.invoke()
            }
    }

    fun getUserProfile(userId: String, onResult: (Map<String, Any>?) -> Unit) {
        db.collection("users")
            .document(userId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    onResult(doc.data)
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    fun saveWeeklyReport(userId: String, feeling: String, keywords: List<String>, analysis: String) {
        val sdf = SimpleDateFormat("yyyy-'W'ww", Locale.KOREA)
        val weekKey = sdf.format(Date())
        val report = mapOf(
            "feeling" to feeling,
            "keywords" to keywords,
            "analysis" to analysis,
            "timestamp" to System.currentTimeMillis()
        )
        db.collection("users")
            .document(userId)
            .collection("weekly_reports")
            .document(weekKey)
            .set(report)
    }

    fun loadWeeklyReport(userId: String, onResult: (String, List<String>, String) -> Unit) {
        val sdf = SimpleDateFormat("yyyy-'W'ww", Locale.KOREA)
        val weekKey = sdf.format(Date())
        loadWeeklyReport(userId, weekKey, onResult)
    }

    fun loadWeeklyReport(userId: String, weekKey: String, onResult: (String, List<String>, String) -> Unit) {
        db.collection("users")
            .document(userId)
            .collection("weekly_reports")
            .document(weekKey)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val feeling = doc.getString("feeling") ?: ""
                    val keywords = doc.get("keywords") as? List<String> ?: emptyList()
                    val analysis = doc.getString("analysis") ?: ""
                    onResult(feeling, keywords, analysis)
                } else {
                    onResult("", emptyList(), "")
                }
            }
            .addOnFailureListener {
                onResult("", emptyList(), "")
            }
    }

    fun deleteWeeklyReport(userId: String, weekKey: String, onComplete: (() -> Unit)? = null) {
        db.collection("users")
            .document(userId)
            .collection("weekly_reports")
            .document(weekKey)
            .delete()
            .addOnSuccessListener {
                Log.d("Firestore", "리포트 삭제 성공")
                onComplete?.invoke()
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "리포트 삭제 실패", e)
                onComplete?.invoke()
            }
    }

    // ✅ 레거시 로컬 데이터 마이그레이션 (+옵션 Firestore 업로드)
    fun migrateLocalDreams(context: Context, userId: String, alsoUploadToFirestore: Boolean = true, onComplete: ((Int) -> Unit)? = null) {
        val legacyPrefs = context.getSharedPreferences("dream_history", Context.MODE_PRIVATE)
        val newPrefs = context.getSharedPreferences("dream_history_$userId", Context.MODE_PRIVATE)

        if (newPrefs.getBoolean("migrated_from_legacy", false)) {
            onComplete?.invoke(0)
            return
        }

        val all = legacyPrefs.all
        var migratedCount = 0
        val editor = newPrefs.edit()

        all.forEach { (key, value) ->
            val isDateKey = Regex("""\d{4}-\d{2}-\d{2}""").matches(key)
            if (isDateKey && value is String) {
                if (!newPrefs.contains(key)) {
                    editor.putString(key, value)
                    migratedCount++

                    if (alsoUploadToFirestore) {
                        try {
                            val jsonArr = JSONArray(value)
                            for (i in 0 until jsonArr.length()) {
                                val obj = jsonArr.optJSONObject(i) ?: continue
                                val dream = obj.optString("dream", "")
                                val result = obj.optString("result", "")
                                if (dream.isNotBlank() || result.isNotBlank()) {
                                    db.collection("users")
                                        .document(userId)
                                        .collection("dreams")
                                        .document(key)
                                        .collection("entries")
                                        .add(
                                            mapOf(
                                                "dream" to dream,
                                                "result" to result,
                                                "timestamp" to System.currentTimeMillis()
                                            )
                                        )
                                }
                            }
                        } catch (_: Exception) { }
                    }
                }
            }
        }

        editor.putBoolean("migrated_from_legacy", true).apply()
        onComplete?.invoke(migratedCount)
    }
}
