// file: app/src/main/java/com/example/dreamindream/fortune/FortuneStorage.kt
package com.example.dreamindream.fortune

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.example.dreamindream.FirestoreManager
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class FortuneStorage(private val ctx: Context) {

    data class UserInfo(val nickname: String, val mbti: String, val birth: String, val gender: String, val birthTime: String)

    val prefs: SharedPreferences by lazy { resolvePrefs() }
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // ---------- Profile / Account ----------
    private fun currentUserKey(): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        return uid ?: "guest-" + (Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "device")
    }
    private fun profilePrefName(): String = "dreamindream_profile_${currentUserKey()}"

    private fun resolvePrefs(): SharedPreferences {
        val fixed = ctx.getSharedPreferences(profilePrefName(), Context.MODE_PRIVATE)
        if (fixed.all.isEmpty()) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            val deviceId = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "device"
            val legacyNow = ctx.getSharedPreferences("user_info_${uid ?: deviceId}", Context.MODE_PRIVATE)
            val legacyUid = uid?.let { ctx.getSharedPreferences("user_info_$it", Context.MODE_PRIVATE) }
            val legacyDev = ctx.getSharedPreferences("user_info_$deviceId", Context.MODE_PRIVATE)

            val merged = mutableMapOf<String, Any?>()
            listOfNotNull(legacyNow, legacyUid, legacyDev).forEach { sp -> if (sp.all.isNotEmpty()) merged.putAll(sp.all) }
            if (merged.isNotEmpty()) {
                val e = fixed.edit()
                merged.forEach { (k, v) ->
                    when (v) {
                        is String -> e.putString(k, v)
                        is Int -> e.putInt(k, v)
                        is Boolean -> e.putBoolean(k, v)
                        is Float -> e.putFloat(k, v)
                        is Long -> e.putLong(k, v)
                    }
                }
                e.apply()
            }
        }
        return fixed
    }

    fun syncProfileFromFirestore(then: () -> Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) { then(); return }
        FirestoreManager.getUserProfile(uid) { data ->
            data?.let { map ->
                val nickname = (map["nickname"] as? String)?.trim().orEmpty()
                val gender = (map["gender"] as? String)?.trim().orEmpty()
                val birthIso = normalizeDate((map["birthdate_iso"] as? String) ?: (map["birthdate"] as? String) ?: "")
                val mbti = (map["mbti"] as? String)?.trim()
                val birthTime = (map["birth_time"] as? String)?.trim()
                if (nickname.isNotBlank() && birthIso.isNotBlank() && gender.isNotBlank()) {
                    prefs.edit().apply {
                        putString("nickname", nickname)
                        putString("birthdate_iso", birthIso)
                        putString("birthdate", birthIso)
                        putString("gender", gender)
                        if (!mbti.isNullOrBlank()) putString("mbti", mbti)
                        if (!birthTime.isNullOrBlank()) putString("birth_time", birthTime)
                    }.apply()
                }
            }
            then()
        }
    }

    fun isProfileComplete(): Boolean {
        val nn = prefs.getString("nickname","") ?: ""
        val bd = prefs.getString("birthdate_iso","") ?: normalizeDate(prefs.getString("birthdate","") ?: "")
        val gd = prefs.getString("gender","") ?: ""
        val dateOk = bd.isNotBlank() && Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(bd)
        return nn.isNotBlank() && dateOk && gd.isNotBlank()
    }

    fun loadUserInfoStrict(): UserInfo {
        val nn = prefs.getString("nickname","") ?: ""
        val mb = (prefs.getString("mbti","") ?: "").uppercase(Locale.ROOT)
        val bd = prefs.getString("birthdate_iso","") ?: normalizeDate(prefs.getString("birthdate","") ?: "")
        val gd = prefs.getString("gender","") ?: ""
        val bt = prefs.getString("birth_time","선택안함") ?: "선택안함"
        return UserInfo(nn, mb, bd, gd, bt)
    }

    // ---------- Keys / Today ----------
    fun todayKey(): String = dateFmt.format(Date())
    fun todayPersonaKey(): String = "${todayKey()}_${personaKey()}"
    fun isFortuneSeenToday(): Boolean = prefs.getBoolean("fortune_seen_${todayPersonaKey()}", false)
    fun markSeenToday() { prefs.edit().putBoolean("fortune_seen_${todayPersonaKey()}", true).apply() }

    private fun personaKey(): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        val nn = prefs.getString("nickname","") ?: ""
        val mb = prefs.getString("mbti","") ?: ""
        val bd = prefs.getString("birthdate_iso","") ?: normalizeDate(prefs.getString("birthdate","") ?: "")
        val gd = prefs.getString("gender","") ?: ""
        val bt = prefs.getString("birth_time","") ?: ""
        val src = "uid:$uid|$nn|$mb|$bd|$gd|$bt"
        val md = MessageDigest.getInstance("MD5").digest(src.toByteArray())
        return md.joinToString("") { "%02x".format(it) }
    }

    fun seedForToday(u: UserInfo): Int {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        val base = "$uid|${u.nickname}|${u.birth}|${u.gender}|${u.mbti}|${u.birthTime}|${todayKey()}"
        val md = MessageDigest.getInstance("MD5").digest(base.toByteArray())
        return ((md[0].toInt() and 0xFF) shl 24) or ((md[1].toInt() and 0xFF) shl 16) or ((md[2].toInt() and 0xFF) shl 8) or (md[3].toInt() and 0xFF)
    }

    // ---------- Cache ----------
    fun cacheTodayPayload(payload: JSONObject) {
        prefs.edit().putString("fortune_payload_${todayPersonaKey()}", payload.toString()).apply()
    }
    fun getCachedTodayPayload(): JSONObject? {
        return prefs.getString("fortune_payload_${todayPersonaKey()}", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    }
    fun cacheDeep(todayPersonaKey: String, deep: JSONObject) {
        prefs.edit().putString("fortune_deep_$todayPersonaKey", deep.toString()).apply()
    }
    fun getCachedDeep(todayPersonaKey: String): JSONObject? {
        return prefs.getString("fortune_deep_$todayPersonaKey", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
    }

    // ---------- History ----------
    fun getRecentLuckyColors(limit: Int = 5): List<String> {
        val arr = JSONArray(prefs.getString("lucky_history_colors", "[]")); return (0 until arr.length()).mapNotNull { arr.optString(it) }.takeLast(limit)
    }
    fun getRecentLuckyTimes(limit: Int = 5): List<String> {
        val arr = JSONArray(prefs.getString("lucky_history_times", "[]")); return (0 until arr.length()).mapNotNull { arr.optString(it) }.takeLast(limit)
    }
    fun getRecentLuckyNumbers(limit: Int = 5): List<Int> {
        val arr = JSONArray(prefs.getString("lucky_history_numbers", "[]"))
        return (0 until arr.length()).mapNotNull { arr.optString(it).toIntOrNull() }.takeLast(limit)
    }
    fun pushHistory(key: String, value: String) {
        val arr = JSONArray(prefs.getString(key, "[]"))
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) list += arr.optString(i)
        list += value
        prefs.edit().putString(key, JSONArray(list.takeLast(10)).toString()).apply()
    }

    // ---------- Utils ----------
    fun ageOf(birthIso: String): Int = try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(birthIso)!!
        val now = Calendar.getInstance(); val bd = Calendar.getInstance().apply { time = d }
        var age = now.get(Calendar.YEAR) - bd.get(Calendar.YEAR); if (now.get(Calendar.DAY_OF_YEAR) < bd.get(Calendar.DAY_OF_YEAR)) age--; age.coerceIn(0,120)
    } catch (_: Exception) { 25 }

    private fun normalizeDate(src: String): String {
        val s = src.trim(); if (s.isBlank()) return ""
        if (Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(s)) return s
        val cleaned = s.replace('.', '-').replace('/', '-')
            .replace("년","-").replace("월","-")
            .replace(Regex("일\\s*\\(.+\\)"),"").replace("일","")
            .replace(Regex("\\s+"),"").trim('-')
        val parts = when {
            Regex("^\\d{8}$").matches(cleaned) -> listOf(cleaned.substring(0,4), cleaned.substring(4,6), cleaned.substring(6,8))
            cleaned.count{it=='-'}==2 -> cleaned.split('-')
            else -> emptyList()
        }
        return if (parts.size==3) "%04d-%02d-%02d".format(parts[0].toInt(), parts[1].toInt(), parts[2].toInt()) else ""
    }
}
