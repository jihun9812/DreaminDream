package com.example.dreamindream

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
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

    fun getDreamsByDate(userId: String, date: String, onResult: (List<DreamEntry>) -> Unit) {
        db.collection("users")
            .document(userId)
            .collection("dreams")
            .document(date)
            .collection("entries")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.documents.mapNotNull {
                    val dream = it.getString("dream")
                    val result = it.getString("result")
                    if (dream != null && result != null) DreamEntry(dream, result) else null
                }
                onResult(list)
            }
            .addOnFailureListener {
                Log.e("Firestore", "꿈 불러오기 실패", it)
                onResult(emptyList())
            }
    }
}
