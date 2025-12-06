package com.dreamindream.app.ui.settings

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dreamindream.app.FirestoreManager
import com.dreamindream.app.R
import com.dreamindream.app.SubscriptionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val PREFS_NAME_PREFIX = "dream_profile_"

    private fun str(id: Int, vararg args: Any): String =
        runCatching { ctx.getString(id, *args) }.getOrElse { "" }

    private fun getPrefs() = ctx.getSharedPreferences(
        PREFS_NAME_PREFIX + (FirebaseAuth.getInstance().currentUser?.uid ?: getDeviceId()),
        Context.MODE_PRIVATE
    )

    private fun getDeviceId(): String {
        return Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    init {
        initialLoad()
        viewModelScope.launch {
            SubscriptionManager.isSubscribed.collect { subscribed ->
                _ui.update { it.copy(isPremium = subscribed) }
            }
        }
    }

    private fun initialLoad() {
        loadFromLocal()
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            syncWithFirestore(user.uid)
        }
        refreshStats()
        updateAccountStatus()
    }

    private fun refreshStats() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirestoreManager.getDreamStats(uid) { total, today ->
            _ui.update { it.copy(dreamTotalCount = total, gptUsedToday = today) }
        }
    }

    private fun loadFromLocal() {
        val prefs = getPrefs()
        val birthIso = prefs.getString("birthdate_iso", "") ?: ""
        val btCode = prefs.getString("birth_time_code", "none") ?: "none"

        // ★ 저장된 프로필이 있는지 확인 (있으면 잠금 상태로 시작)
        val hasSavedProfile = prefs.getBoolean("has_saved_profile", false)

        val (zodiacSign, _) = calculateWesternZodiac(birthIso)
        val (zodiacAnimal, icon) = calculateChineseZodiac(birthIso)
        val age = calculateAge(birthIso)

        _ui.update {
            it.copy(
                nickname = prefs.getString("nickname", "") ?: "",
                birthIso = birthIso,
                gender = prefs.getString("gender", "") ?: "",
                mbti = prefs.getString("mbti", "") ?: "",
                birthTimeCode = btCode,
                birthTimeLabel = codeToLocalizedLabel(btCode),
                countryCode = prefs.getString("country_code", "KR") ?: "KR",
                countryName = prefs.getString("country_name", "South Korea") ?: "South Korea",
                countryFlag = prefs.getString("country_flag", "🇰🇷") ?: "🇰🇷",
                isProfileLocked = hasSavedProfile, // UI에서 경고창 띄우는 기준
                age = age,
                zodiacSign = zodiacSign,
                zodiacAnimal = icon
            )
        }
    }

    private fun syncWithFirestore(uid: String) {
        _ui.update { it.copy(isLoading = true) }
        FirestoreManager.getUserProfile(uid) { data ->
            if (data != null) {
                getPrefs().edit().apply {
                    putString("nickname", data["nickname"] as? String ?: "")
                    putString("birthdate_iso", data["birthdate_iso"] as? String ?: "")
                    putString("gender", data["gender"] as? String ?: "")
                    putString("mbti", data["mbti"] as? String ?: "")
                    putString("birth_time_code", data["birth_time_code"] as? String ?: "none")
                    putString("country_code", data["country_code"] as? String ?: "KR")
                    putString("country_name", data["country_name"] as? String ?: "South Korea")
                    putString("country_flag", data["country_flag"] as? String ?: "🇰🇷")
                    putBoolean("has_saved_profile", true)
                }.apply()
                loadFromLocal()
            }
            _ui.update { it.copy(isLoading = false) }
        }
    }

    // 편집 모드 진입 (경고창 확인 후 호출됨)
    fun enterEditMode() {
        loadFromLocal() // 최신 데이터 로드
        _ui.update { it.copy(isEditMode = true) }
    }

    // 편집 취소
    fun cancelEditMode() {
        _ui.update { it.copy(isEditMode = false) }
        loadFromLocal() // 원래 값 복구
    }

    // ★ 저장 로직 개선: Country 포함, 저장 후 잠금 설정
    fun saveProfile(
        nickname: String, birthIso: String, gender: String, mbti: String, birthTimeCode: String,
        countryCode: String, countryName: String, countryFlag: String
    ) {
        if (nickname.isBlank() || birthIso.isBlank() || gender.isBlank() || countryCode.isBlank()) {
            showToast(str(R.string.toast_input_error))
            return
        }

        _ui.update { it.copy(saving = true) }

        getPrefs().edit().apply {
            putString("nickname", nickname)
            putString("birthdate_iso", birthIso)
            putString("gender", gender)
            putString("mbti", mbti)
            putString("birth_time_code", birthTimeCode)
            putString("country_code", countryCode)
            putString("country_name", countryName)
            putString("country_flag", countryFlag)
            putBoolean("has_saved_profile", true) // 저장 완료 시 잠금 플래그 설정
        }.apply()

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            val data = mapOf(
                "nickname" to nickname,
                "birthdate_iso" to birthIso,
                "gender" to gender,
                "mbti" to mbti,
                "birth_time_code" to birthTimeCode,
                "country_code" to countryCode,
                "country_name" to countryName,
                "country_flag" to countryFlag,
                "updatedAt" to System.currentTimeMillis()
            )
            FirestoreManager.updateUserProfile(uid, data) {
                onSaveComplete()
            }
        } else {
            onSaveComplete()
        }
    }

    private fun onSaveComplete() {
        _ui.update { it.copy(saving = false, isEditMode = false) }
        showToast(str(R.string.toast_saved))
        loadFromLocal() // UI 갱신 (잠금 상태 반영)
    }

    fun logout() {
        FirebaseAuth.getInstance().signOut()
        showToast(str(R.string.btn_logout))
        initialLoad()
    }

    private fun updateAccountStatus() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            _ui.update {
                it.copy(
                    isGuest = true,
                    email = "Guest Mode",
                    accountProviderLabel = str(R.string.status_guest),
                    googleButtonLabel = str(R.string.btn_google_login),
                    googleButtonEnabled = true
                )
            }
        } else {
            val isGoogle = user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
            _ui.update {
                it.copy(
                    isGuest = user.isAnonymous,
                    email = user.email ?: "No Email",
                    accountProviderLabel = if (isGoogle) "Google Connected" else "Guest Account",
                    googleButtonLabel = if (isGoogle) str(R.string.btn_google_connected) else str(R.string.btn_google_merge),
                    googleButtonEnabled = !isGoogle
                )
            }
        }
    }

    // --- Google Link Logic ---
    fun startLinkGoogle() { _ui.update { it.copy(linkInProgress = true) } }
    fun endLinkGoogle() { _ui.update { it.copy(linkInProgress = false) } }

    fun linkGoogleWithIdToken(idToken: String, onToast: (String) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val currentUser = auth.currentUser

        if (currentUser != null && currentUser.isAnonymous) {
            currentUser.linkWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        onToast(str(R.string.toast_google_linked))
                        initialLoad()
                        endLinkGoogle()
                    } else {
                        signInDirectly(credential, onToast)
                    }
                }
        } else {
            signInDirectly(credential, onToast)
        }
    }

    private fun signInDirectly(credential: com.google.firebase.auth.AuthCredential, onToast: (String) -> Unit) {
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnSuccessListener {
                onToast("Login Successful")
                initialLoad()
                endLinkGoogle()
            }
            .addOnFailureListener { e ->
                onToast("Login Failed: ${e.message}")
                endLinkGoogle()
            }
    }

    fun handleGoogleError(msg: String) {
        showToast(msg)
        endLinkGoogle()
    }

    fun showToast(msg: String) {
        _ui.update { it.copy(toastMessage = msg) }
    }

    fun toastShown() {
        _ui.update { it.copy(toastMessage = null) }
    }

    fun setTestPremium(isPremium: Boolean) {
        SubscriptionManager.markSubscribed(ctx, isPremium)
    }

    // --- Calculations ---
    private fun calculateAge(iso: String): Int {
        if (iso.isBlank()) return -1
        return try {
            val birth = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(iso) ?: return -1
            val today = Calendar.getInstance()
            val dob = Calendar.getInstance().apply { time = birth }
            var age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) age--
            age
        } catch (e: Exception) { -1 }
    }

    private fun calculateWesternZodiac(iso: String): Pair<String, String> = runCatching {
        if (iso.isBlank()) return str(R.string.wz_unknown) to "✨"
        val (m, d) = iso.substring(5).split("-").map { it.toInt() }

        data class Sign(val month: Int, val day: Int, val emoji: String, val labelId: Int)
        val signs = listOf(
            Sign(1, 20, "♑", R.string.wz_capricorn),
            Sign(2, 19, "♒", R.string.wz_aquarius),
            Sign(3, 21, "♓", R.string.wz_pisces),
            Sign(4, 20, "♈", R.string.wz_aries),
            Sign(5, 21, "♉", R.string.wz_taurus),
            Sign(6, 22, "♊", R.string.wz_gemini),
            Sign(7, 23, "♋", R.string.wz_cancer),
            Sign(8, 23, "♌", R.string.wz_leo),
            Sign(9, 24, "♍", R.string.wz_virgo),
            Sign(10, 24, "♎", R.string.wz_libra),
            Sign(11, 23, "♏", R.string.wz_scorpio),
            Sign(12, 22, "♐", R.string.wz_sagittarius),
            Sign(12, 32, "♑", R.string.wz_capricorn)
        )

        val key = m * 100 + d
        val sign = signs.first { key < it.month * 100 + it.day }
        val name = str(sign.labelId)
        name to sign.emoji
    }.getOrElse { str(R.string.wz_unknown) to "✨" }

    private fun calculateChineseZodiac(iso: String): Pair<String, String> = run {
        if (iso.isBlank()) return@run str(R.string.cz_unknown) to "🧿"
        val y = iso.substring(0, 4).toIntOrNull() ?: return@run str(R.string.cz_unknown) to "🧿"

        val names = listOf(
            str(R.string.cz_rat), str(R.string.cz_ox), str(R.string.cz_tiger),
            str(R.string.cz_rabbit), str(R.string.cz_dragon), str(R.string.cz_snake),
            str(R.string.cz_horse), str(R.string.cz_goat), str(R.string.cz_monkey),
            str(R.string.cz_rooster), str(R.string.cz_dog), str(R.string.cz_pig)
        )
        val idx = (y - 1900) % 12
        val positiveIdx = if (idx < 0) (idx + 12) else idx
        val name = names[(positiveIdx + 12) % 12]
        val icon = listOf("🐭", "🐮", "🐯", "🐰", "🐲", "🐍", "🐴", "🐑", "🐵", "🐔", "🐶", "🐷")[(positiveIdx + 12) % 12]
        val formattedName = runCatching { ctx.getString(R.string.cz_format, name) }.getOrElse { name }
        formattedName to icon
    }

    private fun codeToLocalizedLabel(code: String): String {
        return if(code == "none" || code.isBlank()) "Unknown Time" else code
    }
}