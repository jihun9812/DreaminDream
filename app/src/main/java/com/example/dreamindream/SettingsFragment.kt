package com.example.dreamindream

import android.app.Activity
import android.graphics.Color
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.example.dreamindream.databinding.FragmentSettingsBinding
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.auth.*
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SettingsFragment : Fragment() {

    private enum class Mode { APP, EDIT }

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private var mode: Mode = Mode.APP

    /** 유저 프로필(닉네임/생년월일 등) 저장용 SharedPreferences */
    private lateinit var profilePrefs: SharedPreferences

    private var isSaving = false
    private var lastSaveClickMs = 0L
    private var isBirthPickerShowing = false
    private var lastBirthClickMs = 0L

    private val ISO = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // ───────────────── Birth time (KO/EN 자동) ─────────────────
    private data class BirthSlot(val code: String, val ko: String, val en: String)

    private fun isKo(): Boolean =
        resources.configuration.locales[0].language.startsWith("ko")

    private fun birthSlots(): List<BirthSlot> = listOf(
        BirthSlot("none",
            getStringSafe(R.string.birthtime_none, "선택안함"),
            getStringSafe(R.string.birthtime_none, "None")),
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

    private fun birthLabels(): List<String> =
        birthSlots().map { if (isKo()) it.ko else it.en }

    /** KO/EN 레이블 → 코드 역매핑 (레거시 호환용) */
    private fun labelToBirthCode(label: String?): String {
        if (label.isNullOrBlank()) return "none"
        val t = label.trim()
        birthSlots().forEach { s ->
            if (t.equals(s.ko, ignoreCase = true) || t.equals(s.en, ignoreCase = true)) return s.code
            // 간단 매칭(괄호 안 시간대만 저장된 경우)
            if (t.contains("23:00") && t.contains("01:00")) return "23_01"
            if (t.contains("01:00") && t.contains("03:00")) return "01_03"
            if (t.contains("03:00") && t.contains("05:00")) return "03_05"
            if (t.contains("05:00") && t.contains("07:00")) return "05_07"
            if (t.contains("07:00") && t.contains("09:00")) return "07_09"
            if (t.contains("09:00") && t.contains("11:00")) return "09_11"
            if (t.contains("11:00") && t.contains("13:00")) return "11_13"
            if (t.contains("13:00") && t.contains("15:00")) return "13_15"
            if (t.contains("15:00") && t.contains("17:00")) return "15_17"
            if (t.contains("17:00") && t.contains("19:00")) return "17_19"
            if (t.contains("19:00") && t.contains("21:00")) return "19_21"
            if (t.contains("21:00") && t.contains("23:00")) return "21_23"
        }
        // 선택안함/None
        if (t.contains("선택") || t.equals("none", true)) return "none"
        return "none"
    }

    private fun codeToLocalizedLabel(code: String?): String {
        val slot = birthSlots().firstOrNull { it.code == (code ?: "none") } ?: birthSlots().first()
        return if (isKo()) slot.ko else slot.en
    }
    // ───────────────────────────────────────────────────────────

    private val mbtiItems by lazy {
        listOf(
            getStringSafe(R.string.select_none, "선택안함"),
            "INTJ","INTP","ENTJ","ENTP",
            "INFJ","INFP","ENFJ","ENFP",
            "ISTJ","ISFJ","ESTJ","ESFJ",
            "ISTP","ISFP","ESTP","ESFP"
        )
    }

    // ───────── Google 링크 런처 ─────────
    private val linkGoogleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val auth = FirebaseAuth.getInstance()

            fun doneUI() {
                binding.progressAccountLink.visibility = View.GONE
                updateAccountLinkUi()
                if (auth.currentUser?.providerData?.any { it.providerId == "google.com" } != true) {
                    binding.btnLinkGoogle.isEnabled = true
                }
                binding.btnDeleteAccount.visibility = if (canShowDeleteAccount()) View.VISIBLE else View.GONE
            }

            if (result.resultCode != Activity.RESULT_OK) {
                doneUI(); return@registerForActivityResult
            }

            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                val user = auth.currentUser
                val wasAnonymous = (user?.isAnonymous == true)
                val anonUid = user?.uid

                val linkOp = when {
                    user == null -> auth.signInWithCredential(credential)
                    else -> user.linkWithCredential(credential)
                }

                linkOp.addOnCompleteListener { t ->
                    if (t.isSuccessful) {
                        if (wasAnonymous) {
                            FirebaseAuth.getInstance().currentUser?.uid?.let { migrateGuestLocalDataToUid(it) }
                        }
                        Toast.makeText(requireContext(), getString(R.string.toast_google_linked), Toast.LENGTH_SHORT).show()
                        doneUI()
                    } else {
                        val e = t.exception
                        if (e is FirebaseAuthUserCollisionException ||
                            (e as? FirebaseAuthException)?.errorCode == "ERROR_CREDENTIAL_ALREADY_IN_USE") {

                            auth.signInWithCredential(credential)
                                .addOnSuccessListener { res ->
                                    val newUid = res.user?.uid
                                    if (anonUid != null && newUid != null && anonUid != newUid) {
                                        FirebaseFunctions.getInstance()
                                            .getHttpsCallable("mergeUserData")
                                            .call(mapOf("oldUid" to anonUid, "newUid" to newUid))
                                            .addOnSuccessListener {
                                                migrateGuestLocalDataToUid(newUid)
                                                Toast.makeText(requireContext(), getString(R.string.toast_merge_done), Toast.LENGTH_LONG).show()
                                                doneUI()
                                            }
                                            .addOnFailureListener { fe ->
                                                minimallyMergeTopProfileDoc(anonUid, newUid) {
                                                    migrateGuestLocalDataToUid(newUid)
                                                    Toast.makeText(requireContext(), getString(R.string.toast_merge_partial, fe.localizedMessage), Toast.LENGTH_LONG).show()
                                                    doneUI()
                                                }
                                            }
                                    } else {
                                        Toast.makeText(requireContext(), getString(R.string.toast_google_signed_in), Toast.LENGTH_SHORT).show()
                                        doneUI()
                                    }
                                }
                                .addOnFailureListener { se ->
                                    Toast.makeText(requireContext(), getString(R.string.toast_google_login_failed, se.localizedMessage ?: "-"), Toast.LENGTH_LONG).show()
                                    doneUI()
                                }
                        } else {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.toast_link_failed, e?.localizedMessage ?: "-"),
                                Toast.LENGTH_LONG
                            ).show()
                            doneUI()
                        }
                    }
                }
            } catch (e: ApiException) {
                Toast.makeText(requireContext(), getString(R.string.toast_login_cancel_failed_code, e.statusCode), Toast.LENGTH_SHORT).show()
                doneUI()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), getString(R.string.toast_error_with_reason, e.localizedMessage ?: "-"), Toast.LENGTH_SHORT).show()
                doneUI()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profilePrefs = resolveProfilePrefs()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)

        binding.adViewSettings.loadAd(AdRequest.Builder().build())

        initProfileEditor()
        // ✅ Language 섹션 제거됨 (로그인에서만 변경)
        refreshQuickStatus()
        setupGoogleLinkSection()
        updateAccountLinkUi()
        enterCorrectMode()

        // 카드 버튼들
        binding.btnProfileEdit.setOnClickListener { showEditMode() }
        binding.btnPremium.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.dlg_premium_title))
                .setMessage(getString(R.string.dlg_premium_msg))
                .setPositiveButton(getString(R.string.ok), null)
                .show()
        }
        binding.btnContact.setOnClickListener {
            startActivity(Intent(requireContext(), FeedbackActivity::class.java))
        }
        binding.btnTerms.setOnClickListener {
            startActivity(Intent(requireContext(), TermsActivity::class.java))
        }

        // ✅ 계정삭제 버튼 가시성
        binding.btnDeleteAccount.visibility = if (canShowDeleteAccount()) View.VISIBLE else View.GONE

        // ✅ 계정 삭제(7일 유예)
        binding.btnDeleteAccount.setOnClickListener {
            if (!canShowDeleteAccount()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.dlg_delete_title))
                    .setMessage(getString(R.string.dlg_delete_need_login_msg))
                    .setPositiveButton(getString(R.string.ok), null)
                    .show()
                return@setOnClickListener
            }
            showDeactivateDialog()
        }

        // 로그아웃
        binding.btnLogout.visibility = View.VISIBLE
        binding.btnLogout.setOnClickListener { showLogoutConfirm() }

        binding.tvGptUsageLabel.text = getString(R.string.gpt_usage_today)
        updateAppProfileSummary()
    }

    override fun onResume() {
        super.onResume()
        refreshQuickStatus()
        updateAccountLinkUi()
        enterCorrectMode()
        binding.btnDeleteAccount.visibility = if (canShowDeleteAccount()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        isBirthPickerShowing = false
        _binding = null
        super.onDestroyView()
    }

    // ───────────────── 에디터/요약/저장 ─────────────────
    private fun initProfileEditor() {
        binding.btnCancel.setOnClickListener { showAppMode() }
        binding.btnSave.setOnClickListener {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastSaveClickMs < 800 || isSaving) return@setOnClickListener
            lastSaveClickMs = now
            if (!validate()) return@setOnClickListener
            confirmAndSave()
        }

        setDone(binding.editNickname)
        binding.editNickname.doAfterTextChanged { binding.tilNickname.error = null }

        binding.editBirthdate.setOnClickListener {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastBirthClickMs < 600) return@setOnClickListener
            lastBirthClickMs = now
            showDatePicker()
        }

        fun makeAdapter(items: List<String>) =
            ArrayAdapter(requireContext(), R.layout.spinner_item, items).apply {
                setDropDownViewResource(R.layout.spinner_dropdown_item)
            }

        binding.spinnerMbti.adapter = makeAdapter(mbtiItems)
        binding.spinnerBirthtime.adapter = makeAdapter(birthLabels())

        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirestoreManager.getUserProfile(uid) { map ->
                if (map != null) {
                    val nn = (map["nickname"] as? String).orEmpty()
                    val bd = normalizeDate((map["birthdate_iso"] as? String) ?: (map["birthdate"] as? String))
                    val gd = (map["gender"] as? String).orEmpty()
                    val mb = (map["mbti"] as? String).orEmpty()

                    // 코드 우선, 없으면 레거시 레이블 → 코드
                    val btCodeLoaded = (map["birth_time_code"] as? String)
                        ?: labelToBirthCode(map["birth_time"] as? String)

                    profilePrefs.edit().apply {
                        if (nn.isNotBlank()) putString("nickname", nn)
                        if (bd.isNotBlank()) { putString("birthdate_iso", bd); putString("birthdate", bd) }
                        if (gd.isNotBlank()) putString("gender", gd)
                        if (mb.isNotBlank()) putString("mbti", mb)
                        putString("birth_time_code", btCodeLoaded)
                        // 표시용 레이블도 저장(로컬 표시 최적화)
                        putString("birth_time", codeToLocalizedLabel(btCodeLoaded))
                    }.apply()

                    // 에디터 바인딩 & 요약 업데이트
                    loadUserIntoEditor()
                    updateAppProfileSummary()
                    refreshQuickStatus()
                    enterCorrectMode()
                }
            }
        }
        loadUserIntoEditor()
    }

    private fun enterCorrectMode() {
        if (isProfileIncomplete()) showEditMode() else showAppMode()
    }

    private fun isProfileIncomplete(): Boolean {
        val nn = profilePrefs.getString("nickname", "").orEmpty().trim()
        val bd = (profilePrefs.getString("birthdate_iso", null) ?: profilePrefs.getString("birthdate", "")).orEmpty().trim()
        val gd = profilePrefs.getString("gender", "").orEmpty().trim()
        return nn.isBlank() || bd.isBlank() || gd.isBlank()
    }

    private fun showAppMode() {
        mode = Mode.APP
        binding.cardAppSettings.visibility = View.VISIBLE
        binding.cardProfile.visibility = View.GONE
        binding.sectionEdit?.visibility = View.GONE
        binding.textTitle.text = getString(R.string.settings_title)
        updateAppProfileSummary()
        binding.scrollView.post { binding.scrollView.smoothScrollTo(0, binding.cardAppSettings.top - 24) }
    }

    private fun showEditMode() {
        mode = Mode.EDIT
        binding.cardAppSettings.visibility = View.GONE
        binding.cardProfile.visibility = View.VISIBLE
        binding.sectionEdit?.visibility = View.VISIBLE
        binding.textTitle.text = getString(R.string.profile_edit_title)
        binding.scrollView.post { binding.scrollView.smoothScrollTo(0, binding.cardProfile.top - 24) }
    }

    private fun updateAppProfileSummary() {
        val nn = profilePrefs.getString("nickname","") ?: ""
        val bd = (profilePrefs.getString("birthdate_iso", null) ?: profilePrefs.getString("birthdate","") ?: "")
        val gd = profilePrefs.getString("gender","") ?: ""
        val mb = (profilePrefs.getString("mbti","") ?: "").uppercase(Locale.ROOT)

        // 코드 → 현지화 레이블
        val btCode = profilePrefs.getString("birth_time_code", null)
            ?: labelToBirthCode(profilePrefs.getString("birth_time", null))
        val btLabel = codeToLocalizedLabel(btCode)

        val age = calcAge(bd)
        val (cz, czIcon) = chineseZodiac(bd)
        val (wz, wzIcon) = westernZodiac(bd)

        val labelColor = ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light)
        fun line(label: String, value: String): CharSequence {
            val s = SpannableStringBuilder()
            val prefix = "$label "
            s.append(prefix).append(value)
            s.setSpan(ForegroundColorSpan(labelColor), 0, prefix.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(StyleSpan(android.graphics.Typeface.BOLD), prefix.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return s
        }

        val sb = SpannableStringBuilder()
            .append(line(getString(R.string.summary_name_prefix), if (nn.isBlank()) getString(R.string.value_placeholder_dash) else nn)).append("\n")
            .append(line(getString(R.string.summary_birth_prefix), if (bd.isBlank()) getString(R.string.value_placeholder_dash) else bd)).append("\n")
            .append(line(getString(R.string.summary_gender_prefix), if (gd.isBlank()) getString(R.string.value_placeholder_dash) else gd)).append("\n")
            .append(line(getString(R.string.summary_mbti_prefix), if (mb.isBlank()) getString(R.string.value_placeholder_dash) else mb)).append("\n")
            .append(line(getString(R.string.summary_age_prefix), if (age >= 0) getString(R.string.summary_age_value, age) else getString(R.string.value_placeholder_dash))).append("\n")
            .append(line(getString(R.string.summary_cz_prefix, czIcon), cz)).append("\n")
            .append(line(getString(R.string.summary_wz_prefix, wzIcon), wz)).append("\n")
            .append(line(getString(R.string.summary_birthtime_prefix), btLabel))

        binding.tvAppProfileSummary.text = sb
    }

    // ───────── 검증/저장 ─────────
    private fun validate(): Boolean {
        binding.tilNickname.error = null
        var ok = true

        if (binding.editNickname.text.isNullOrBlank()) {
            binding.tilNickname.error = getString(R.string.err_enter_name)
            ok = false
        }

        val birthIso = normalizeDate(binding.editBirthdate.text?.toString())
        if (birthIso.isBlank()) {
            if (binding.tilNickname.error.isNullOrBlank()) {
                binding.tilNickname.error = getString(R.string.err_select_birthdate)
            }
            ok = false
        }

        if (binding.radioGroupGender.checkedRadioButtonId == -1) {
            Snackbar.make(requireView(), getString(R.string.err_select_gender), Snackbar.LENGTH_SHORT).show()
            ok = false
        }
        return ok
    }

    private fun confirmAndSave() {
        if (isSaving) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dlg_save_title))
            .setMessage(getString(R.string.dlg_save_msg))
            .setPositiveButton(getString(R.string.btn_save)) { _, _ -> save() }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun save() {
        if (isSaving) return
        isSaving = true
        binding.progressSaving.visibility = View.VISIBLE

        val gender = when (binding.radioGroupGender.checkedRadioButtonId) {
            R.id.radio_male -> getString(R.string.gender_male)
            R.id.radio_female -> getString(R.string.gender_female)
            else -> ""
        }
        val birthIso = normalizeDate(binding.editBirthdate.text?.toString())
        val nickname = binding.editNickname.text?.toString()?.trim().orEmpty()
        val mbti = (binding.spinnerMbti.selectedItem as? String)?.takeIf { it != getString(R.string.select_none) } ?: ""

        // 선택 코드/레이블
        val btIndex = binding.spinnerBirthtime.selectedItemPosition.coerceIn(0, birthSlots().lastIndex)
        val btSlot = birthSlots()[btIndex]
        val btCode = btSlot.code
        val btLabel = codeToLocalizedLabel(btCode)

        profilePrefs.edit().apply {
            putString("nickname", nickname)
            putString("birthdate_iso", birthIso); putString("birthdate", birthIso)
            putString("gender", gender)
            putString("mbti", mbti)
            putString("birth_time_code", btCode)
            putString("birth_time", btLabel) // 표시용(레거시 호환)
            putLong("profile_last_saved", System.currentTimeMillis())
        }.apply()

        FirebaseAuth.getInstance().currentUser?.let { user ->
            val data = mapOf(
                "nickname" to nickname,
                "birthdate_iso" to birthIso,
                "birthdate" to birthIso,
                "gender" to gender,
                "mbti" to mbti,
                "birth_time_code" to btCode,
                "birth_time" to btLabel
            )
            FirestoreManager.saveUserProfile(user.uid, data) { onSaved() }
        } ?: onSaved()
    }

    private fun onSaved() {
        isSaving = false
        binding.progressSaving.visibility = View.GONE
        showAppMode()
        Snackbar.make(requireView(), getString(R.string.toast_saved), Snackbar.LENGTH_SHORT).show()
    }

    private fun loadUserIntoEditor() {
        val nn = profilePrefs.getString("nickname","") ?: ""
        val bd = (profilePrefs.getString("birthdate_iso", null) ?: profilePrefs.getString("birthdate","") ?: "")
        val gd = profilePrefs.getString("gender","") ?: ""
        val mb = (profilePrefs.getString("mbti","") ?: "").uppercase(Locale.ROOT)

        // 코드 복원 (없으면 라벨→코드)
        val btCode = profilePrefs.getString("birth_time_code", null)
            ?: labelToBirthCode(profilePrefs.getString("birth_time", null))
        val idx = birthSlots().indexOfFirst { it.code == btCode }.let { if (it >= 0) it else 0 }

        binding.editNickname.setText(nn)
        binding.editBirthdate.setText(bd)
        when (gd) {
            getString(R.string.gender_male) -> binding.radioMale.isChecked = true
            getString(R.string.gender_female) -> binding.radioFemale.isChecked = true
            else   -> binding.radioGroupGender.clearCheck()
        }
        binding.spinnerMbti.setSelection(
            mbtiItems.indexOf(if (mb.isBlank()) getString(R.string.select_none) else mb).coerceAtLeast(0),
            false
        )

        // 스피너 어댑터(언어 바뀐 경우에도 즉시 레이블 반영)
        (binding.spinnerBirthtime.adapter as? ArrayAdapter<String>)?.apply {
            clear(); addAll(birthLabels()); notifyDataSetChanged()
        } ?: run { binding.spinnerBirthtime.adapter = ArrayAdapter(requireContext(), R.layout.spinner_item, birthLabels()).apply { setDropDownViewResource(R.layout.spinner_dropdown_item) } }

        binding.spinnerBirthtime.setSelection(idx, false)
    }

    // ───────── 빠른 상태 ─────────
    private fun dreamHistoryPrefs(): SharedPreferences {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        return if (uid != null)
            requireContext().getSharedPreferences("dream_history_$uid", Context.MODE_PRIVATE)
        else
            requireContext().getSharedPreferences("dream_history", Context.MODE_PRIVATE)
    }

    private fun countDreamEntriesTotalLocal(): Int {
        val prefs = dreamHistoryPrefs()
        var total = 0
        val dateRegex = Regex("""\d{4}-\d{2}-\d{2}""")
        prefs.all.forEach { (key, value) ->
            if (dateRegex.matches(key)) {
                val s = value as? String ?: return@forEach
                total += runCatching { JSONArray(s).length() }.getOrDefault(0)
            }
        }
        return total
    }

    private fun refreshQuickStatus() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null || !isAdded) {
            val todayInterpret = profilePrefs.getInt(
                "interpret_used_today",
                profilePrefs.getInt("gpt_used_today", 0) + profilePrefs.getInt("gpt_reward_used_today", 0)
            )
            binding.tvGptUsageValue.text = getString(R.string.unit_times_value, todayInterpret)

            val totalLocal = countDreamEntriesTotalLocal()
            binding.tvDreamCountValue.text = getString(R.string.unit_entries_value, totalLocal)
            profilePrefs.edit().putInt("dream_total_count", totalLocal).apply()
            return
        }

        binding.tvGptUsageLabel.text = getString(R.string.gpt_usage_today)

        FirestoreManager.countDreamEntriesToday(uid) { todayCount ->
            if (!isAdded) return@countDreamEntriesToday
            binding.tvGptUsageValue.text = getString(R.string.unit_times_value, todayCount)
            profilePrefs.edit().putInt("interpret_used_today", todayCount).apply()
        }

        val totalLocal = countDreamEntriesTotalLocal()
        binding.tvDreamCountValue.text = getString(R.string.unit_entries_value, totalLocal)
        profilePrefs.edit().putInt("dream_total_count", totalLocal).apply()
    }

    // ───────── DatePicker/유틸 ─────────
    private fun showDatePicker() {
        if (!isAdded || parentFragmentManager.isStateSaved) return
        if (isBirthPickerShowing) return
        if (parentFragmentManager.findFragmentByTag("birthdate_picker") != null) return

        val constraints = CalendarConstraints.Builder()
            .setEnd(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.dlg_birthdate_title))
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .setCalendarConstraints(constraints)
            .build()

        isBirthPickerShowing = true
        picker.addOnPositiveButtonClickListener { millis ->
            binding.editBirthdate.setText(ISO.format(Date(millis)))
            binding.tilNickname.error = null
        }
        picker.addOnDismissListener { isBirthPickerShowing = false }

        if (!parentFragmentManager.isStateSaved &&
            parentFragmentManager.findFragmentByTag("birthdate_picker") == null) {
            picker.show(parentFragmentManager, "birthdate_picker")
        } else {
            isBirthPickerShowing = false
        }
    }

    private fun setDone(edit: EditText) {
        edit.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { v.hideIme(); true } else false
        }
    }

    private fun View.hideIme() {
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
            ?.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun normalizeDate(s: String?): String = runCatching {
        if (s.isNullOrBlank()) return ""
        val d = ISO.parse(s.trim()) ?: return ""
        ISO.format(d)
    }.getOrDefault("")

    private fun calcAge(iso: String): Int = runCatching {
        if (iso.isBlank()) return -1
        val dob = ISO.parse(iso) ?: return -1
        val calDob = Calendar.getInstance().apply { time = dob }
        val now = Calendar.getInstance()
        var age = now.get(Calendar.YEAR) - calDob.get(Calendar.YEAR)
        if (now.get(Calendar.DAY_OF_YEAR) < calDob.get(Calendar.DAY_OF_YEAR)) age--
        age
    }.getOrElse { -1 }

    private fun westernZodiac(iso: String): Pair<String,String> = runCatching {
        if (iso.isBlank()) return "Zodiac -" to "✨"
        val (m,d) = iso.substring(5).split("-").map { it.toInt() }
        val arr = listOf(
            Triple(1,20,"♑ Capricorn"), Triple(2,19,"♒ Aquarius"), Triple(3,21,"♓ Pisces"),
            Triple(4,20,"♈ Aries"),     Triple(5,21,"♉ Taurus"),   Triple(6,22,"♊ Gemini"),
            Triple(7,23,"♋ Cancer"),    Triple(8,23,"♌ Leo"),      Triple(9,24,"♍ Virgo"),
            Triple(10,24,"♎ Libra"),    Triple(11,23,"♏ Scorpio"), Triple(12,22,"♐ Sagittarius"),
            Triple(12,32,"♑ Capricorn")
        )
        val key = m*100 + d
        val name = arr.first { (mm,dd,_) -> key < (mm*100+dd) }.third
        name to "✨"
    }.getOrElse { "Zodiac -" to "✨" }

    private fun chineseZodiac(iso: String): Pair<String,String> = run cut@{
        if (iso.isBlank()) return@cut getString(R.string.cz_unknown) to "🧿"
        val y = iso.substring(0,4).toIntOrNull() ?: return@cut getString(R.string.cz_unknown) to "🧿"
        val names = listOf(
            getString(R.string.cz_rat), getString(R.string.cz_ox), getString(R.string.cz_tiger),
            getString(R.string.cz_rabbit), getString(R.string.cz_dragon), getString(R.string.cz_snake),
            getString(R.string.cz_horse), getString(R.string.cz_goat), getString(R.string.cz_monkey),
            getString(R.string.cz_rooster), getString(R.string.cz_dog), getString(R.string.cz_pig)
        )
        val idx = (y - 1900) % 12
        val name = names[(idx + 12) % 12]
        val icon = when(name){
            getString(R.string.cz_rat)->"🐭"; getString(R.string.cz_ox)->"🐮"; getString(R.string.cz_tiger)->"🐯"
            getString(R.string.cz_rabbit)->"🐰"; getString(R.string.cz_dragon)->"🐲"; getString(R.string.cz_snake)->"🐍"
            getString(R.string.cz_horse)->"🐴"; getString(R.string.cz_goat)->"🐑"; getString(R.string.cz_monkey)->"🐵"
            getString(R.string.cz_rooster)->"🐔"; getString(R.string.cz_dog)->"🐶"; else->"🐷"
        }
        getString(R.string.cz_format, name) to icon
    }

    // ───────────────── 구글 계정 연결 UI ─────────────────
    private fun setupGoogleLinkSection() {
        binding.btnLinkGoogle.setOnClickListener {
            binding.btnLinkGoogle.isEnabled = false
            binding.progressAccountLink.visibility = View.VISIBLE

            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            val client = GoogleSignIn.getClient(requireActivity(), gso)

            client.signOut().addOnCompleteListener {
                try {
                    linkGoogleLauncher.launch(client.signInIntent)
                } catch (e: Exception) {
                    binding.progressAccountLink.visibility = View.GONE
                    binding.btnLinkGoogle.isEnabled = true
                    Toast.makeText(requireContext(), getString(R.string.toast_google_signin_start_failed, e.localizedMessage ?: "-"), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateAccountLinkUi() {
        val user = FirebaseAuth.getInstance().currentUser

        fun statusChip(label: String, hex: String): CharSequence {
            val s = SpannableStringBuilder(getString(R.string.status_prefix))
            val start = s.length
            s.append(label)
            s.setSpan(ForegroundColorSpan(Color.parseColor(hex)), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(StyleSpan(android.graphics.Typeface.BOLD), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return s
        }

        val colorAnon   = "#FDCA60"
        val colorGoogle = "#37C2D0"
        val colorEmail  = "#FFFFFF"

        if (user == null) {
            binding.tvAccountStatus.text = statusChip(getString(R.string.status_logged_out), "#B3FFFFFF")
            binding.btnLinkGoogle.isEnabled = true
            binding.btnLinkGoogle.text = getString(R.string.btn_google_login)
            binding.btnDeleteAccount.visibility = View.GONE
            return
        }

        val providers = user.providerData.map { it.providerId }.toSet()

        when {
            user.isAnonymous -> {
                binding.tvAccountStatus.text = statusChip(getString(R.string.status_guest), colorAnon)
                binding.btnLinkGoogle.isEnabled = true
                binding.btnLinkGoogle.text = getString(R.string.btn_google_merge)
            }
            "google.com" in providers -> {
                binding.tvAccountStatus.text = statusChip(getString(R.string.status_google), colorGoogle)
                binding.btnLinkGoogle.isEnabled = false
                binding.btnLinkGoogle.text = getString(R.string.btn_google_connected)
            }
            else -> {
                binding.tvAccountStatus.text = statusChip(getString(R.string.status_email), colorEmail)
                binding.btnLinkGoogle.isEnabled = true
                binding.btnLinkGoogle.text = getString(R.string.btn_google_connect)
            }
        }

        binding.btnDeleteAccount.visibility = if (canShowDeleteAccount()) View.VISIBLE else View.GONE
    }

    /** 구글/이메일 계정일 때만 계정삭제 노출 */
    private fun canShowDeleteAccount(): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        if (user.isAnonymous) return false
        val providers = user.providerData.map { it.providerId }.toSet()
        val isGoogle = providers.contains("google.com")
        val isEmail = providers.contains(EmailAuthProvider.PROVIDER_ID)
        return isGoogle || isEmail
    }

    // ───────────────── 계정 비활성화(소프트 삭제) ─────────────────
    private fun showDeactivateDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dlg_delete_title))
            .setMessage(getString(R.string.dlg_delete_soft_msg))
            .setPositiveButton(getString(R.string.deactivate)) { _, _ -> softDeleteAccount() }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun softDeleteAccount() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val uid = user.uid
        val db = FirebaseFirestore.getInstance()
        theNowAndPurge(uid, db) { data ->
            db.collection("users").document(uid).set(data, SetOptions.merge())
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), getString(R.string.toast_deactivated), Toast.LENGTH_LONG).show()
                    FirebaseAuth.getInstance().signOut()
                    val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), getString(R.string.toast_process_failed_with_reason, it.localizedMessage ?: "-"), Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun theNowAndPurge(uid: String, db: FirebaseFirestore, cb: (Map<String, Any>) -> Unit) {
        val now = Timestamp.now()
        val purgeAt = Timestamp(now.seconds + 7 * 24 * 60 * 60, 0)
        cb(mapOf("status" to "deactivated", "deactivatedAt" to now, "purgeAt" to purgeAt))
    }

    // ───────────────── 체험→연결 로컬 데이터 마이그레이션 ─────────────────
    private fun migrateGuestLocalDataToUid(uid: String) {
        try {
            val androidId = Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID) ?: "device"
            val oldProfileName = "dreamindream_profile_guest-$androidId"
            val newProfileName = "dreamindream_profile_$uid"
            copySharedPrefs(oldProfileName, newProfileName, clearOld = false)
            copySharedPrefs("dream_history", "dream_history_$uid", clearOld = false)
            requireContext().getSharedPreferences("migrations", Context.MODE_PRIVATE)
                .edit().putBoolean("guest_to_$uid", true).apply()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.toast_local_migration_failed, e.localizedMessage ?: "-"), Toast.LENGTH_LONG).show()
        }
    }

    private fun copySharedPrefs(oldName: String, newName: String, clearOld: Boolean) {
        val old = requireContext().getSharedPreferences(oldName, Context.MODE_PRIVATE)
        val all = old.all
        if (all.isEmpty()) return
        val dst = requireContext().getSharedPreferences(newName, Context.MODE_PRIVATE)
        val edit = dst.edit()
        for ((k, v) in all) {
            when (v) {
                is String  -> edit.putString(k, v)
                is Int     -> edit.putInt(k, v)
                is Long    -> edit.putLong(k, v)
                is Float   -> edit.putFloat(k, v)
                is Boolean -> edit.putBoolean(k, v)
            }
        }
        edit.apply()
        if (clearOld) old.edit().clear().apply()
    }

    private fun minimallyMergeTopProfileDoc(oldUid: String, newUid: String, onDone: () -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val oldRef = db.collection("users").document(oldUid)
        val newRef = db.collection("users").document(newUid)
        oldRef.get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    newRef.set(snap.data ?: emptyMap<String, Any>(), SetOptions.merge())
                        .addOnCompleteListener { onDone() }
                } else onDone()
            }
            .addOnFailureListener { onDone() }
    }

    private fun showLogoutConfirm() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dlg_logout_title))
            .setMessage(getString(R.string.dlg_logout_msg))
            .setPositiveButton(getString(R.string.btn_logout)) { _, _ -> performLogout() }
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .show()
    }

    private fun performLogout() {
        FirebaseAuth.getInstance().signOut()
        profilePrefs.edit().clear().apply()
        val intent = Intent(requireContext(), LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    private fun resolveProfilePrefs(): SharedPreferences =
        requireContext().getSharedPreferences(profilePrefName(requireContext()), Context.MODE_PRIVATE)

    private fun profilePrefName(ctx: Context): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val key = uid ?: "guest-" + (Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "device")
        return "dreamindream_profile_$key"
    }

    // Fragment가 아직 attach 안된 순간 대비 안전 호출
    private fun getStringSafe(id: Int, fallback: String) = runCatching { getString(id) }.getOrElse { fallback }
}
