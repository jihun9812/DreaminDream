package com.example.dreamindream

import android.content.Context
import android.content.SharedPreferences
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
        val prefs = context.getSharedPreferences("dream_history", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        db.collection("users")
            .document(userId)
            .collection("dreams")
            .get()
            .addOnSuccessListener { snapshot ->
                val dateSet = mutableSetOf<LocalDate>()
                for (doc in snapshot.documents) {
                    val dateStr = doc.id
                    try {
                        val parsedDate = LocalDate.parse(dateStr)
                        dateSet.add(parsedDate)

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
                            }

                    } catch (_: Exception) {}
                }
                onResult(dateSet)
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
}
