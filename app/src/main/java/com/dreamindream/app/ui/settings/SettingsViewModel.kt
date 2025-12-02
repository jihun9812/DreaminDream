package com.dreamindream.app.ui.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import com.dreamindream.app.R
import com.google.firebase.Timestamp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val ISO = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val prefs: SharedPreferences by lazy { resolveProfilePrefs() }
    private fun resolveProfilePrefs(): SharedPreferences {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val key = uid
            ?: "guest-" + (Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "device")
        return ctx.getSharedPreferences("dreamindream_profile_$key", Context.MODE_PRIVATE)
    }

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui

    init {
        loadFromPrefs()
        refreshQuickStatus()
        updateAccountLinkUi()
        enterCorrectMode()
    }

    private fun enterCorrectMode() {
        val incomplete =
            _ui.value.nickname.isBlank() ||
                    _ui.value.birthIso.isBlank() ||
                    _ui.value.gender.isBlank()
        _ui.value = _ui.value.copy(isEditMode = incomplete)
    }

    fun setEditMode(enabled: Boolean) {
        _ui.value = _ui.value.copy(isEditMode = enabled)
    }

    fun loadFromPrefs() {
        val nn = prefs.getString("nickname", "") ?: ""
        val bd = (prefs.getString("birthdate_iso", null)
            ?: prefs.getString("birthdate", "")).orEmpty()
        val gd = prefs.getString("gender", "") ?: ""
        val mb = (prefs.getString("mbti", "") ?: "").uppercase(Locale.ROOT)
        val btCode = prefs.getString("birth_time_code", null)
            ?: labelToBirthCode(prefs.getString("birth_time", null))
        val btLabel = codeToLocalizedLabel(btCode)

        val age = calcAge(bd)
        val (cz, czIcon) = chineseZodiac(bd)
        val (wz, _) = westernZodiac(bd)

        _ui.value = _ui.value.copy(
            nickname = nn,
            birthIso = bd,
            gender = gd,
            mbti = mb,
            birthTimeCode = btCode,
            birthTimeLabel = btLabel,
            age = age,
            chineseZodiacText = cz,
            chineseZodiacIcon = czIcon,
            westernZodiacText = wz
        )
    }

    // ====== 저장 ======
    fun save(
        nickname: String,
        birthIso: String,
        gender: String,
        mbti: String,
        birthTimeCode: String
    ) {
        if (nickname.isBlank() || birthIso.isBlank() || gender.isBlank()) {
            _ui.value = _ui.value.copy(
                toast = ctx.getString(R.string.err_select_birthdate)
            )
            return
        }
        _ui.value = _ui.value.copy(saving = true)

        val btLabel = codeToLocalizedLabel(birthTimeCode)

        prefs.edit().apply {
            putString("nickname", nickname)
            putString("birthdate_iso", birthIso)
            putString("birthdate", birthIso)
            putString("gender", gender)
            putString("mbti", mbti)
            putString("birth_time_code", birthTimeCode)
            putString("birth_time", btLabel)
            putLong("profile_last_saved", System.currentTimeMillis())
        }.apply()

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            doneSaved()
        } else {
            val data = mapOf(
                "nickname" to nickname,
                "birthdate_iso" to birthIso,
                "birthdate" to birthIso,
                "gender" to gender,
                "mbti" to mbti,
                "birth_time_code" to birthTimeCode,
                "birth_time" to btLabel
            )
            FirebaseFirestore.getInstance()
                .collection("users").document(user.uid)
                .set(data, SetOptions.merge())
                .addOnCompleteListener { doneSaved() }
        }
    }

    private fun doneSaved() {
        _ui.value = _ui.value.copy(
            saving = false,
            isEditMode = false,
            toast = ctx.getString(R.string.toast_saved)
        )
        loadFromPrefs()
        refreshQuickStatus()
        updateAccountLinkUi()
    }

    // ====== 빠른 통계 ======
    private fun dreamHistoryPrefs(): SharedPreferences {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        return if (uid != null)
            ctx.getSharedPreferences("dream_history_$uid", Context.MODE_PRIVATE)
        else
            ctx.getSharedPreferences("dream_history", Context.MODE_PRIVATE)
    }

    private fun countDreamEntriesTotalLocal(): Int {
        val p = dreamHistoryPrefs()
        var total = 0
        val dateRegex = Regex("""\d{4}-\d{2}-\d{2}""")
        p.all.forEach { (key, value) ->
            if (dateRegex.matches(key)) {
                val s = value as? String ?: return@forEach
                total += runCatching { JSONArray(s).length() }.getOrDefault(0)
            }
        }
        return total
    }

    fun refreshQuickStatus() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            val todayInterpret = prefs.getInt(
                "interpret_used_today",
                prefs.getInt("gpt_used_today", 0) +
                        prefs.getInt("gpt_reward_used_today", 0)
            )
            _ui.value = _ui.value.copy(
                gptUsedToday = todayInterpret,
                dreamTotalLocal = countDreamEntriesTotalLocal()
            )
            return
        }
        com.dreamindream.app.FirestoreManager.countDreamEntriesToday(uid) { today ->
            _ui.value = _ui.value.copy(
                gptUsedToday = today,
                dreamTotalLocal = countDreamEntriesTotalLocal()
            )
            prefs.edit().putInt("interpret_used_today", today).apply()
        }
    }

    // ====== 계정 링크 / 상태 ======
    fun updateAccountLinkUi() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            _ui.value = _ui.value.copy(
                accountStatusLabel = ctx.getString(R.string.status_logged_out),
                canDeleteAccount = false,
                googleButtonLabel = ctx.getString(R.string.btn_google_login),
                googleButtonEnabled = true
            )
            return
        }
        val providers = user.providerData.map { it.providerId }.toSet()
        when {
            user.isAnonymous -> {
                _ui.value = _ui.value.copy(
                    accountStatusLabel = ctx.getString(R.string.status_guest),
                    canDeleteAccount = false,
                    googleButtonLabel = ctx.getString(R.string.btn_google_merge),
                    googleButtonEnabled = true
                )
            }

            "google.com" in providers -> {
                _ui.value = _ui.value.copy(
                    accountStatusLabel = ctx.getString(R.string.status_google),
                    canDeleteAccount = true,
                    googleButtonLabel = ctx.getString(R.string.btn_google_connected),
                    googleButtonEnabled = false
                )
            }

            EmailAuthProvider.PROVIDER_ID in providers -> {
                _ui.value = _ui.value.copy(
                    accountStatusLabel = ctx.getString(R.string.status_email),
                    canDeleteAccount = true,
                    googleButtonLabel = ctx.getString(R.string.btn_google_connect),
                    googleButtonEnabled = true
                )
            }

            else -> {
                _ui.value = _ui.value.copy(
                    canDeleteAccount = false,
                    googleButtonLabel = ctx.getString(R.string.btn_google_connect),
                    googleButtonEnabled = true
                )
            }
        }
    }

    fun startLinkGoogle() {
        _ui.value = _ui.value.copy(linkInProgress = true)
    }

    fun endLinkGoogle() {
        _ui.value = _ui.value.copy(linkInProgress = false)
    }

    fun linkGoogleWithIdToken(idToken: String, onToast: (String) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val credential =
            com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
        val user = auth.currentUser
        val wasAnonymous = (user?.isAnonymous == true)
        val anonUid = user?.uid

        val linkOp =
            if (user == null) auth.signInWithCredential(credential)
            else user.linkWithCredential(credential)

        linkOp.addOnCompleteListener { t ->
            if (t.isSuccessful) {
                if (wasAnonymous) {
                    FirebaseAuth.getInstance().currentUser?.uid?.let {
                        migrateGuestLocalDataToUid(it)
                    }
                }
                onToast(ctx.getString(R.string.toast_google_linked))
                updateAccountLinkUi()
                endLinkGoogle()
            } else {
                val e = t.exception
                if (e is com.google.firebase.auth.FirebaseAuthUserCollisionException ||
                    (e as? com.google.firebase.auth.FirebaseAuthException)?.errorCode ==
                    "ERROR_CREDENTIAL_ALREADY_IN_USE"
                ) {
                    auth.signInWithCredential(credential)
                        .addOnSuccessListener { res ->
                            val newUid = res.user?.uid
                            if (anonUid != null && newUid != null && anonUid != newUid) {
                                FirebaseFunctions.getInstance()
                                    .getHttpsCallable("mergeUserData")
                                    .call(
                                        mapOf(
                                            "oldUid" to anonUid,
                                            "newUid" to newUid
                                        )
                                    )
                                    .addOnSuccessListener {
                                        migrateGuestLocalDataToUid(newUid)
                                        onToast(ctx.getString(R.string.toast_merge_done))
                                        updateAccountLinkUi()
                                        endLinkGoogle()
                                    }
                                    .addOnFailureListener { fe ->
                                        minimallyMergeTopProfileDoc(anonUid, newUid) {
                                            migrateGuestLocalDataToUid(newUid)
                                            onToast(
                                                ctx.getString(
                                                    R.string.toast_merge_partial,
                                                    fe.localizedMessage ?: "-"
                                                )
                                            )
                                            updateAccountLinkUi()
                                            endLinkGoogle()
                                        }
                                    }
                            } else {
                                onToast(ctx.getString(R.string.toast_google_signed_in))
                                updateAccountLinkUi()
                                endLinkGoogle()
                            }
                        }
                        .addOnFailureListener { se ->
                            onToast(
                                ctx.getString(
                                    R.string.toast_google_login_failed,
                                    se.localizedMessage ?: "-"
                                )
                            )
                            endLinkGoogle()
                        }
                } else {
                    onToast(
                        ctx.getString(
                            R.string.toast_link_failed,
                            e?.localizedMessage ?: "-"
                        )
                    )
                    endLinkGoogle()
                }
            }
        }
    }

    fun logout() {
        FirebaseAuth.getInstance().signOut()
        prefs.edit().clear().apply()
        _ui.value = _ui.value.copy(
            toast = ctx.getString(R.string.btn_logout)
        )
        updateAccountLinkUi()
        setEditMode(false)
    }

    fun softDeleteAccount(onToast: (String) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val uid = user.uid
        val db = FirebaseFirestore.getInstance()
        val now = Timestamp.now()
        val purgeAt = Timestamp(now.seconds + 7 * 24 * 60 * 60, 0)
        val data =
            mapOf("status" to "deactivated", "deactivatedAt" to now, "purgeAt" to purgeAt)
        db.collection("users").document(uid).set(data, SetOptions.merge())
            .addOnSuccessListener {
                onToast(ctx.getString(R.string.toast_deactivated))
                logout()
            }
            .addOnFailureListener {
                onToast(
                    ctx.getString(
                        R.string.toast_process_failed_with_reason,
                        it.localizedMessage ?: "-"
                    )
                )
            }
    }

    // ====== 유틸/도메인 ======
    fun birthSlots(): List<BirthSlot> = listOf(
        BirthSlot("none", str(R.string.birthtime_none, "선택안함"), str(R.string.birthtime_none, "None")),
        BirthSlot("23_01", "자시 (23:00~01:00)", "Zi (23:00–01:00)"),
        BirthSlot("01_03", "축시 (01:00~03:00)", "Chou (01:00–03:00)"),
        BirthSlot("03_05", "인시 (03:00~05:00)", "Yin (03:00–05:00)"),
        BirthSlot("05_07", "묘시 (05:00~07:00)", "Mao (05:00–07:00)"),
        BirthSlot("07_09", "진시 (07:00~09:00)", "Chen (07:00–09:00)"),
        BirthSlot("09_11", "사시 (09:00~11:00)", "Si (09:00–11:00)"),
        BirthSlot("11_13", "오시 (11:00~13:00)", "Wu (11:00–13:00)"),
        BirthSlot("13_15", "미시 (13:00~15:00)", "Wei (13:00–15:00)"),
        BirthSlot("15_17", "신시 (15:00~17:00)", "Shen (15:00–17:00)"),
        BirthSlot("17_19", "유시 (17:00~19:00)", "You (17:00–19:00)"),
        BirthSlot("19_21", "술시 (19:00~21:00)", "Xu (19:00–21:00)"),
        BirthSlot("21_23", "해시 (21:00~23:00)", "Hai (21:00–23:00)")
    )

    data class BirthSlot(val code: String, val ko: String, val en: String)

    private fun str(id: Int, fb: String) =
        runCatching { ctx.getString(id) }.getOrElse { fb }

    fun codeToLocalizedLabel(code: String?): String =
        (birthSlots().firstOrNull { it.code == (code ?: "none") }
            ?: birthSlots().first()).let {
            val isKo =
                ctx.resources.configuration.locales[0].language.startsWith("ko")
            if (isKo) it.ko else it.en
        }

    fun labelToBirthCode(label: String?): String {
        if (label.isNullOrBlank()) return "none"
        val t = label.trim()
        birthSlots().forEach { s ->
            if (t.equals(s.ko, true) || t.equals(s.en, true)) return s.code
        }
        return "none"
    }

    fun calcAge(iso: String): Int = runCatching {
        if (iso.isBlank()) return -1
        val dob = ISO.parse(iso) ?: return -1
        val calDob = Calendar.getInstance().apply { time = dob }
        val now = Calendar.getInstance()
        var age = now.get(Calendar.YEAR) - calDob.get(Calendar.YEAR)
        if (now.get(Calendar.DAY_OF_YEAR) < calDob.get(Calendar.DAY_OF_YEAR)) age--
        age
    }.getOrElse { -1 }

    // 별자리 표시 – 다국어 대응 (문자열 리소스 기반)
    fun westernZodiac(iso: String): Pair<String, String> = runCatching {
        if (iso.isBlank()) return str(R.string.wz_unknown, "Zodiac -") to "✨"
        val (m, d) = iso.substring(5).split("-").map { it.toInt() }
        data class Sign(
            val month: Int,
            val day: Int,
            val emoji: String,
            val labelId: Int,
            val fallback: String
        )

        val signs = listOf(
            Sign(1, 20, "♑", R.string.wz_capricorn, "Capricorn"),
            Sign(2, 19, "♒", R.string.wz_aquarius, "Aquarius"),
            Sign(3, 21, "♓", R.string.wz_pisces, "Pisces"),
            Sign(4, 20, "♈", R.string.wz_aries, "Aries"),
            Sign(5, 21, "♉", R.string.wz_taurus, "Taurus"),
            Sign(6, 22, "♊", R.string.wz_gemini, "Gemini"),
            Sign(7, 23, "♋", R.string.wz_cancer, "Cancer"),
            Sign(8, 23, "♌", R.string.wz_leo, "Leo"),
            Sign(9, 24, "♍", R.string.wz_virgo, "Virgo"),
            Sign(10, 24, "♎", R.string.wz_libra, "Libra"),
            Sign(11, 23, "♏", R.string.wz_scorpio, "Scorpio"),
            Sign(12, 22, "♐", R.string.wz_sagittarius, "Sagittarius"),
            Sign(12, 32, "♑", R.string.wz_capricorn, "Capricorn")
        )

        val key = m * 100 + d
        val sign = signs.first { key < it.month * 100 + it.day }
        val name = str(sign.labelId, sign.fallback)
        "${sign.emoji} $name" to "✨"
    }.getOrElse { str(R.string.wz_unknown, "Zodiac -") to "✨" }

    fun chineseZodiac(iso: String): Pair<String, String> = run {
        if (iso.isBlank()) return@run str(R.string.cz_unknown, "-") to "🧿"
        val y = iso.substring(0, 4).toIntOrNull()
            ?: return@run str(R.string.cz_unknown, "-") to "🧿"
        val names = listOf(
            str(R.string.cz_rat, "Rat"),
            str(R.string.cz_ox, "Ox"),
            str(R.string.cz_tiger, "Tiger"),
            str(R.string.cz_rabbit, "Rabbit"),
            str(R.string.cz_dragon, "Dragon"),
            str(R.string.cz_snake, "Snake"),
            str(R.string.cz_horse, "Horse"),
            str(R.string.cz_goat, "Goat"),
            str(R.string.cz_monkey, "Monkey"),
            str(R.string.cz_rooster, "Rooster"),
            str(R.string.cz_dog, "Dog"),
            str(R.string.cz_pig, "Pig")
        )
        val idx = (y - 1900) % 12
        val name = names[(idx + 12) % 12]
        val icon = listOf("🐭", "🐮", "🐯", "🐰", "🐲", "🐍", "🐴", "🐑", "🐵", "🐔", "🐶", "🐷")[
            (idx + 12) % 12
        ]
        ctx.getString(R.string.cz_format, name) to icon
    }

    // ====== 게스트 → UID 로컬 이전 & 최소 병합 ======
    private fun migrateGuestLocalDataToUid(uid: String) {
        try {
            val androidId =
                Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID)
                    ?: "device"
            val oldProfileName = "dreamindream_profile_guest-$androidId"
            val newProfileName = "dreamindream_profile_$uid"
            copySharedPrefs(oldProfileName, newProfileName, clearOld = false)
            copySharedPrefs("dream_history", "dream_history_$uid", clearOld = false)
            ctx.getSharedPreferences("migrations", Context.MODE_PRIVATE)
                .edit().putBoolean("guest_to_$uid", true).apply()
        } catch (_: Exception) {
        }
    }

    private fun copySharedPrefs(oldName: String, newName: String, clearOld: Boolean) {
        val old = ctx.getSharedPreferences(oldName, Context.MODE_PRIVATE)
        val all = old.all
        if (all.isEmpty()) return
        val dst = ctx.getSharedPreferences(newName, Context.MODE_PRIVATE).edit()
        for ((k, v) in all) when (v) {
            is String -> dst.putString(k, v)
            is Int -> dst.putInt(k, v)
            is Long -> dst.putLong(k, v)
            is Float -> dst.putFloat(k, v)
            is Boolean -> dst.putBoolean(k, v)
        }
        dst.apply()
        if (clearOld) old.edit().clear().apply()
    }

    private fun minimallyMergeTopProfileDoc(
        oldUid: String,
        newUid: String,
        onDone: () -> Unit
    ) {
        val db = FirebaseFirestore.getInstance()
        val oldRef = db.collection("users").document(oldUid)
        val newRef = db.collection("users").document(newUid)
        oldRef.get()
            .addOnSuccessListener { snap ->
                if (snap.exists())
                    newRef.set(snap.data ?: emptyMap<String, Any>(), SetOptions.merge())
                        .addOnCompleteListener { onDone() }
                else onDone()
            }
            .addOnFailureListener { onDone() }
    }
}
