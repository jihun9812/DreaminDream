package com.example.dreamindream

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
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.example.dreamindream.databinding.FragmentSettingsBinding
import com.google.android.gms.ads.AdRequest
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
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
    private lateinit var prefs: SharedPreferences

    private var isSaving = false
    private var lastSaveClickMs = 0L
    private var isBirthPickerShowing = false
    private var lastBirthClickMs = 0L

    private val ISO = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val birthTimes = listOf(
        "선택안함",
        "자시 (23:00~01:00)","축시 (01:00~03:00)","인시 (03:00~05:00)",
        "묘시 (05:00~07:00)","진시 (07:00~09:00)","사시 (09:00~11:00)",
        "오시 (11:00~13:00)","미시 (13:00~15:00)","신시 (15:00~17:00)",
        "유시 (17:00~19:00)","술시 (19:00~21:00)","해시 (21:00~23:00)"
    )
    private val mbtiItems = listOf(
        "선택안함",
        "INTJ","INTP","ENTJ","ENTP",
        "INFJ","INFP","ENFJ","ENFP",
        "ISTJ","ISFJ","ESTJ","ESFJ",
        "ISTP","ISFP","ESTP","ESFP"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = resolvePrefs()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        prefs = resolvePrefs()

        // 광고
        binding.adViewSettings.loadAd(AdRequest.Builder().build())

        initProfileEditor()
        setupPreferenceOnlySection()
        refreshQuickStatus()

        // 진입 시 현재 프로필 상태에 맞는 모드로 자동 전환
        enterCorrectMode()

        // 카드 버튼
        binding.btnProfileEdit.setOnClickListener { showEditMode() }
        binding.btnPremium.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("출시 준비중")
                .setMessage("프리미엄(광고 제거)은 출시 준비중입니다.")
                .setPositiveButton("확인", null)
                .show()
        }
        binding.btnContact.setOnClickListener {
            startActivity(Intent(requireContext(), FeedbackActivity::class.java))
        }
        binding.btnTerms.setOnClickListener {
            startActivity(Intent(requireContext(), TermsActivity::class.java))
        }

        // 로그아웃
        binding.btnLogout.visibility = View.VISIBLE
        binding.btnLogout.setOnClickListener { showLogoutConfirm() }

        // 라벨 고정
        binding.tvGptUsageLabel.text = "오늘 해몽"

        updateAppProfileSummary()
    }

    override fun onResume() {
        super.onResume()
        refreshQuickStatus()
        // 돌아왔을 때도 상태 확인 (다른 화면에서 저장했을 수 있음)
        enterCorrectMode()
    }

    override fun onDestroyView() {
        isBirthPickerShowing = false
        _binding = null
        super.onDestroyView()
    }

    // ───────────────── 프로필 편집 ─────────────────
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
        binding.spinnerBirthtime.adapter = makeAdapter(birthTimes)

        // 서버 → prefs 동기화
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirestoreManager.getUserProfile(uid) { map ->
                if (map != null) {
                    val nn = (map["nickname"] as? String).orEmpty()
                    val bd = normalizeDate((map["birthdate_iso"] as? String) ?: (map["birthdate"] as? String))
                    val gd = (map["gender"] as? String).orEmpty()
                    val mb = (map["mbti"] as? String).orEmpty()
                    val bt = (map["birth_time"] as? String) ?: "선택안함"
                    prefs.edit().apply {
                        if (nn.isNotBlank()) putString("nickname", nn)
                        if (bd.isNotBlank()) { putString("birthdate_iso", bd); putString("birthdate", bd) }
                        if (gd.isNotBlank()) putString("gender", gd)
                        if (mb.isNotBlank()) putString("mbti", mb)
                        putString("birth_time", bt)
                    }.apply()
                    loadUserIntoEditor()
                    updateAppProfileSummary()
                    refreshQuickStatus()
                    // 서버 동기화 후 상태 재판단
                    enterCorrectMode()
                }
            }
        }
        loadUserIntoEditor()
    }

    /** 현재 프로필 상태를 보고 올바른 모드로 진입 */
    private fun enterCorrectMode() {
        if (isProfileIncomplete()) {
            showEditMode()
        } else {
            showAppMode()
        }
    }

    /** 닉네임, 생일, 성별 중 하나라도 비었으면 미완성으로 간주 */
    private fun isProfileIncomplete(): Boolean {
        val nn = prefs.getString("nickname", "").orEmpty().trim()
        val bd = (prefs.getString("birthdate_iso", null) ?: prefs.getString("birthdate", "")).orEmpty().trim()
        val gd = prefs.getString("gender", "").orEmpty().trim()
        // MBTI/출생시간은 선택값(필수 아님)
        return nn.isBlank() || bd.isBlank() || gd.isBlank()
    }

    private fun showAppMode() {
        mode = Mode.APP
        binding.cardAppSettings.visibility = View.VISIBLE
        binding.cardProfile.visibility = View.GONE
        binding.sectionEdit.visibility = View.GONE
        binding.textTitle.text = "설정"
        updateAppProfileSummary()
        binding.scrollView.post { binding.scrollView.smoothScrollTo(0, binding.cardAppSettings.top - 24) }
    }

    private fun showEditMode() {
        mode = Mode.EDIT
        binding.cardAppSettings.visibility = View.GONE
        binding.cardProfile.visibility = View.VISIBLE
        binding.sectionEdit.visibility = View.VISIBLE
        binding.textTitle.text = "프로필 편집"
        binding.scrollView.post { binding.scrollView.smoothScrollTo(0, binding.cardProfile.top - 24) }
    }

    // ───────────────── 앱 설정 요약 텍스트 ─────────────────
    private fun updateAppProfileSummary() {
        val nn = prefs.getString("nickname","") ?: ""
        val bd = (prefs.getString("birthdate_iso", null) ?: prefs.getString("birthdate","") ?: "")
        val gd = prefs.getString("gender","") ?: ""
        val mb = (prefs.getString("mbti","") ?: "").uppercase(Locale.ROOT)
        val bt = prefs.getString("birth_time","선택안함") ?: "선택안함"

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
            .append(line("🧑 이름:", if (nn.isBlank()) "-" else nn)).append("\n")
            .append(line("🎂 생일:", if (bd.isBlank()) "-" else bd)).append("\n")
            .append(line("⚧️ 성별:", if (gd.isBlank()) "-" else gd)).append("\n")
            .append(line("🔮 MBTI:", if (mb.isBlank()) "-" else mb)).append("\n")
            .append(line("🎂 나이:", if (age >= 0) "${age}세" else "-")).append("\n")
            .append(line("$czIcon 띠:", cz)).append("\n")
            .append(line("$wzIcon 별자리:", wz)).append("\n")
            .append(line("⏰ 출생시간:", (bt.split(' ').firstOrNull() ?: bt)))

        binding.tvAppProfileSummary.text = sb
    }

    // ───────────────── 언어만 유지 ─────────────────
    private fun setupPreferenceOnlySection() {
        val langs = listOf("한국어","English","日本語","中文")
        val adapter = ArrayAdapter(requireContext(), R.layout.spinner_item, langs).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        binding.spinnerLang.adapter = adapter
        binding.spinnerLang.setSelection(langs.indexOf(prefs.getString("app_lang","한국어")).coerceAtLeast(0), false)
        binding.spinnerLang.setOnItemSelectedListener(null)
        binding.spinnerLang.setOnItemSelectedListener(object: android.widget.AdapterView.OnItemSelectedListener{
            override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, pos: Int, id: Long) {
                prefs.edit().putString("app_lang", langs[pos]).apply()
            }
            override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
        })
    }

    // ───────────────── 퀵 상태칩 갱신(오늘 해몽 / 전체 꿈 기록) ─────────────────
    private fun refreshQuickStatus() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null || !isAdded) {
            val todayInterpret = prefs.getInt(
                "interpret_used_today",
                prefs.getInt("gpt_used_today", 0) + prefs.getInt("gpt_reward_used_today", 0)
            )
            binding.tvGptUsageValue.text = "$todayInterpret 회"
            binding.tvDreamCountValue.text = "${prefs.getInt("dream_total_count", 0)}개"
            return
        }

        binding.tvGptUsageLabel.text = "오늘 해몽"

        FirestoreManager.countDreamEntriesToday(uid) { todayCount ->
            if (!isAdded) return@countDreamEntriesToday
            binding.tvGptUsageValue.text = "$todayCount 회"
            prefs.edit().putInt("interpret_used_today", todayCount).apply()
        }

        FirestoreManager.countDreamEntriesTotal(uid) { total ->
            if (!isAdded) return@countDreamEntriesTotal
            binding.tvDreamCountValue.text = "${total}개"
            prefs.edit().putInt("dream_total_count", total).apply()
        }
    }

    // ───────────────── 에디터 바인딩/검증/저장 ─────────────────
    private fun loadUserIntoEditor() {
        val nn = prefs.getString("nickname","") ?: ""
        val bd = (prefs.getString("birthdate_iso", null) ?: prefs.getString("birthdate","") ?: "")
        val gd = prefs.getString("gender","") ?: ""
        val mb = (prefs.getString("mbti","") ?: "").uppercase(Locale.ROOT)
        val bt = prefs.getString("birth_time","선택안함") ?: "선택안함"

        binding.editNickname.setText(nn)
        binding.editBirthdate.setText(bd)
        when (gd) {
            "남성" -> binding.radioMale.isChecked = true
            "여성" -> binding.radioFemale.isChecked = true
            else   -> binding.radioGroupGender.clearCheck()
        }
        binding.spinnerMbti.setSelection(
            mbtiItems.indexOf(if (mb.isBlank()) "선택안함" else mb).coerceAtLeast(0),
            false
        )
        binding.spinnerBirthtime.setSelection(birthTimes.indexOf(bt).coerceAtLeast(0), false)
    }

    private fun validate(): Boolean {
        binding.tilNickname.error = null
        var ok = true
        if (binding.editNickname.text.isNullOrBlank()) { binding.tilNickname.error = "이름을 입력해주세요."; ok = false }
        val birthIso = normalizeDate(binding.editBirthdate.text?.toString())
        if (birthIso.isBlank()) { binding.tilNickname.error = "생년월일을 선택해주세요."; ok = false }
        // 성별은 라디오 체크 유무로
        if (binding.radioGroupGender.checkedRadioButtonId == -1) {
            Snackbar.make(requireView(), "성별을 선택해주세요.", Snackbar.LENGTH_SHORT).show()
            ok = false
        }
        return ok
    }

    private fun confirmAndSave() {
        if (isSaving) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("저장하시겠어요?")
            .setMessage("입력한 정보로 프로필을 저장합니다.")
            .setPositiveButton("저장") { _, _ -> save() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun save() {
        if (isSaving) return
        isSaving = true
        binding.progressSaving.visibility = View.VISIBLE

        val gender = when (binding.radioGroupGender.checkedRadioButtonId) {
            R.id.radio_male -> "남성"
            R.id.radio_female -> "여성"
            else -> ""
        }
        val birthIso = normalizeDate(binding.editBirthdate.text?.toString())
        val nickname = binding.editNickname.text?.toString()?.trim().orEmpty()
        val mbti = (binding.spinnerMbti.selectedItem as? String)?.takeIf { it != "선택안함" } ?: ""
        val birthTime = (binding.spinnerBirthtime.selectedItem as? String) ?: "선택안함"

        prefs.edit().apply {
            putString("nickname", nickname)
            putString("birthdate_iso", birthIso); putString("birthdate", birthIso)
            putString("gender", gender)
            putString("mbti", mbti)
            putString("birth_time", birthTime)
            putLong("profile_last_saved", System.currentTimeMillis())
        }.apply()

        FirebaseAuth.getInstance().currentUser?.let { user ->
            val data = mapOf(
                "nickname" to nickname,
                "birthdate_iso" to birthIso,
                "birthdate" to birthIso,
                "gender" to gender,
                "mbti" to mbti,
                "birth_time" to birthTime
            )
            FirestoreManager.saveUserProfile(user.uid, data) { onSaved() }
        } ?: onSaved()
    }

    private fun onSaved() {
        isSaving = false
        binding.progressSaving.visibility = View.GONE
        // 저장 후에는 요약 모드로
        showAppMode()
        Snackbar.make(requireView(), "저장되었습니다!", Snackbar.LENGTH_SHORT).show()
    }

    // ───────────────── DatePicker ─────────────────
    private fun showDatePicker() {
        if (!isAdded || parentFragmentManager.isStateSaved) return
        if (isBirthPickerShowing) return
        if (parentFragmentManager.findFragmentByTag("birthdate_picker") != null) return

        val constraints = CalendarConstraints.Builder()
            .setEnd(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("🌙 생년월일 선택")
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

    // ───────────────── 유틸 ─────────────────
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
        if (iso.isBlank()) return "별자리 -" to "✨"
        val (m,d) = iso.substring(5).split("-").map { it.toInt() }
        val arr = listOf(
            Triple(1,20,"♑ 염소자리"), Triple(2,19,"♒ 물병자리"), Triple(3,21,"♓ 물고기자리"),
            Triple(4,20,"♈ 양자리"),   Triple(5,21,"♉ 황소자리"), Triple(6,22,"♊ 쌍둥이자리"),
            Triple(7,23,"♋ 게자리"),   Triple(8,23,"♌ 사자자리"), Triple(9,24,"♍ 처녀자리"),
            Triple(10,24,"♎ 천칭자리"),Triple(11,23,"♏ 전갈자리"),Triple(12,22,"♐ 사수자리"),
            Triple(12,32,"♑ 염소자리")
        )
        val key = m*100 + d
        val name = arr.first { (mm,dd,_) -> key < (mm*100+dd) }.third
        name to "✨"
    }.getOrElse { "별자리 -" to "✨" }

    private fun chineseZodiac(iso: String): Pair<String,String> = runCutting@ run {
        if (iso.isBlank()) return@run "띠 -" to "🧿"
        val y = iso.substring(0,4).toIntOrNull() ?: return@run "띠 -" to "🧿"
        val names = listOf("쥐","소","호랑이","토끼","용","뱀","말","양","원숭이","닭","개","돼지")
        val idx = (y - 1900) % 12
        val name = names[(idx + 12) % 12]
        val icon = when(name){
            "쥐"->"🐭"; "소"->"🐮"; "호랑이"->"🐯"; "토끼"->"🐰"; "용"->"🐲"; "뱀"->"🐍";
            "말"->"🐴"; "양"->"🐑"; "원숭이"->"🐵"; "닭"->"🐔"; "개"->"🐶"; else->"🐷"
        }
        return@run name + "띠" to icon
    }

    private fun showLogoutConfirm() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("로그아웃")
            .setMessage("로그인 화면으로 이동합니다. 계속할까요?")
            .setPositiveButton("로그아웃") { _, _ -> performLogout() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun performLogout() {
        FirebaseAuth.getInstance().signOut()
        prefs.edit().clear().apply()
        val intent = Intent(requireContext(), LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    private fun resolvePrefs(): SharedPreferences =
        requireContext().getSharedPreferences(profilePrefName(requireContext()), Context.MODE_PRIVATE)

    private fun profilePrefName(ctx: Context): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        val key = uid ?: "guest-" + (Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "device")
        return "dreamindream_profile_$key"
    }
}
