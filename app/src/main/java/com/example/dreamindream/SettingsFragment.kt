// app/src/main/java/com/example/dreamindream/SettingsFragment.kt
package com.example.dreamindream

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import android.transition.TransitionManager
import android.transition.Fade
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Color
import androidx.core.view.doOnLayout

class SettingsFragment : Fragment() {

    private fun currentUserKey(ctx: Context): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        return uid ?: "guest-" + (Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "device")
    }
    private fun profilePrefName(ctx: Context): String = "dreamindream_profile_${currentUserKey(ctx)}"
    private fun resolvePrefs(): SharedPreferences =
        requireContext().getSharedPreferences(profilePrefName(requireContext()), Context.MODE_PRIVATE)

    private val ISO_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private fun normalizeDate(src: String?): String {
        if (src.isNullOrBlank()) return ""
        val s = src.trim()
            .replace('.', '-').replace('/', '-')
            .replace("년","-").replace("월","-")
            .replace(Regex("일\\s*\\(.+\\)"),"").replace("일","")
            .replace(Regex("\\s+"),"").trim('-')
        return when {
            Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(s) -> s
            Regex("^\\d{8}$").matches(s) ->
                "%s-%s-%s".format(s.substring(0,4), s.substring(4,6), s.substring(6,8))
            s.count { it=='-' }==2 -> runCatching {
                val p = s.split('-'); "%04d-%02d-%02d".format(p[0].toInt(), p[1].toInt(), p[2].toInt())
            }.getOrElse { "" }
            else -> ""
        }
    }

    private lateinit var prefs: SharedPreferences
    private var isSaving = false

    private lateinit var editCard: View
    private lateinit var summaryCard: View
    private lateinit var saveButton: com.google.android.material.button.MaterialButton
    private lateinit var editModeButton: com.google.android.material.button.MaterialButton
    private lateinit var loadingSpinner: ProgressBar

    private lateinit var genderGroup: RadioGroup
    private lateinit var tilNickname: TextInputLayout
    private lateinit var tilBirthdate: TextInputLayout
    private lateinit var tilMBTI: TextInputLayout
    private lateinit var birthEdit: TextInputEditText
    private lateinit var nicknameEdit: TextInputEditText
    private lateinit var mbtiEdit: TextInputEditText
    private lateinit var birthTimeSpinner: Spinner
    private lateinit var infoSummary: TextView
    private lateinit var infoDetails: TextView
    private lateinit var loginProviderText: TextView

    private val birthTimes = listOf(
        "선택안함",
        "자시 (23:00~01:00)", "축시 (01:00~03:00)", "인시 (03:00~05:00)",
        "묘시 (05:00~07:00)", "진시 (07:00~09:00)", "사시 (09:00~11:00)",
        "오시 (11:00~13:00)", "미시 (13:00~15:00)", "신시 (15:00~17:00)",
        "유시 (17:00~19:00)", "술시 (19:00~21:00)", "해시 (21:00~23:00)"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        prefs = resolvePrefs()

        editCard         = view.findViewById(R.id.edit_group)
        summaryCard      = view.findViewById(R.id.card_user_info)
        saveButton       = view.findViewById(R.id.btn_save)
        editModeButton   = view.findViewById(R.id.btn_edit_mode)
        loadingSpinner   = view.findViewById(R.id.progress_saving)
        genderGroup      = view.findViewById(R.id.radioGroup_gender)
        tilNickname      = view.findViewById(R.id.tilNickname)
        tilBirthdate     = view.findViewById(R.id.tilBirthdate)
        tilMBTI          = view.findViewById(R.id.tilMBTI)
        birthEdit        = view.findViewById(R.id.edit_birthdate)
        nicknameEdit     = view.findViewById(R.id.edit_nickname)
        mbtiEdit         = view.findViewById(R.id.edit_MBTI)
        birthTimeSpinner = view.findViewById(R.id.spinner_birthtime)
        infoSummary      = view.findViewById(R.id.text_info_summary)
        infoDetails      = view.findViewById(R.id.text_user_info)
        loginProviderText= view.findViewById(R.id.text_login_provider)

        // 광고
        view.findViewById<AdView>(R.id.adView_settings)?.loadAd(AdRequest.Builder().build())

        // === 색상 통일 ===
        val hintGray = Color.parseColor("#B0B0B0")   // 회색 힌트
        val white    = Color.WHITE

        // TextInput 힌트 -> 회색
        listOf(nicknameEdit, birthEdit, mbtiEdit).forEach { it.setHintTextColor(hintGray) }

        // Spinner 어댑터: "선택안함"(index 0)은 회색, 선택값은 화이트
        val adapter = object : ArrayAdapter<String>(
            requireContext(),
            R.layout.spinner_item,
            android.R.id.text1,
            birthTimes
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                val isHint = (birthTimeSpinner.selectedItemPosition == 0)
                v.setTextColor(if (isHint) hintGray else white)
                return v
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = layoutInflater.inflate(R.layout.spinner_dropdown_item, parent, false) as TextView
                tv.text = getItem(position)
                // 드롭다운에서도 0번은 회색, 나머지는 화이트
                tv.setTextColor(if (position == 0) hintGray else white)
                return tv
            }
        }
        birthTimeSpinner.adapter = adapter

        // 저장된 값 반영
        birthTimeSpinner.setSelection(
            birthTimes.indexOf(prefs.getString("birth_time", "선택안함")).coerceAtLeast(0),
            false
        )

        // 드롭다운 위치: 스피너 아래로 2dp 내려서 가림 없음
        birthTimeSpinner.doOnLayout {
            birthTimeSpinner.dropDownWidth = birthTimeSpinner.width
            birthTimeSpinner.dropDownHorizontalOffset = 0
            val offset2dp = (resources.displayMetrics.density * 2f).toInt()
            birthTimeSpinner.dropDownVerticalOffset = offset2dp
            // 선택 상태에 따라 메인 뷰 텍스트 색 재적용
            (birthTimeSpinner.selectedView as? TextView)?.setTextColor(
                if (birthTimeSpinner.selectedItemPosition == 0) hintGray else white
            )
        }

        loadUserInfo()
        updateLoginProviderUI()

        // 생일 선택
        fun openBirthPicker() = showDatePicker()
        tilBirthdate.setEndIconOnClickListener { openBirthPicker() }
        birthEdit.setOnClickListener { openBirthPicker() }
        view.findViewById<View>(R.id.label_birthdate)?.setOnClickListener { openBirthPicker() }

        // MBTI 대문자 강제
        mbtiEdit.doAfterTextChanged {
            val up = it.toString().uppercase(Locale.ROOT)
            if (mbtiEdit.text.toString() != up) {
                mbtiEdit.setText(up); mbtiEdit.setSelection(up.length)
            }
            tilMBTI.error = null
        }
        nicknameEdit.doAfterTextChanged { tilNickname.error = null }

        // 서버 -> 로컬 동기화
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirestoreManager.getUserProfile(uid) { map ->
                if (map != null) {
                    val nn = (map["nickname"] as? String).orEmpty()
                    val bdIso = normalizeDate((map["birthdate_iso"] as? String) ?: (map["birthdate"] as? String))
                    val gd = (map["gender"] as? String).orEmpty()
                    val mb = (map["mbti"] as? String).orEmpty()
                    val bt = (map["birth_time"] as? String) ?: "선택안함"
                    prefs.edit().apply {
                        if (nn.isNotBlank()) putString("nickname", nn)
                        if (bdIso.isNotBlank()) { putString("birthdate_iso", bdIso); putString("birthdate", bdIso) }
                        if (gd.isNotBlank()) putString("gender", gd)
                        if (mb.isNotBlank()) putString("mbti", mb)
                        putString("birth_time", bt)
                    }.apply()
                    loadUserInfo()
                }
            }
        }

        // 저장
        saveButton.setOnClickListener {
            if (!validateInput()) return@setOnClickListener
            confirmAndSave()
        }

        // 보기 → 편집
        editModeButton.setOnClickListener { toggleEditMode(true) }

        // 로그아웃
        view.findViewById<View>(R.id.btn_logout)?.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("로그아웃")
                .setMessage("정말 로그아웃 하시겠어요?")
                .setPositiveButton("확인") { _, _ ->
                    FirebaseAuth.getInstance().signOut()
                    val intent = android.content.Intent(requireContext(), LoginActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }
                .setNegativeButton("취소", null)
                .show()
        }

        return view
    }

    private fun updateLoginProviderUI() {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        val label = when {
            user == null -> "로그인: 없음"
            user.isAnonymous -> "로그인: 게스트(익명)"
            else -> {
                val providers = user.providerData.mapNotNull { it.providerId }.toSet()
                when {
                    providers.contains("google.com") -> "로그인: Google"
                    providers.contains("password") -> "로그인: 이메일"
                    providers.contains("phone") -> "로그인: 휴대폰"
                    else -> "로그인: 기타"
                }
            }
        }
        val email = user?.email
        loginProviderText.text = if (!email.isNullOrBlank()) "🔐 $label · $email" else "🔐 $label"
    }

    private fun showDatePicker() {
        try {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("🌙 생년월일 선택")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build()
            picker.addOnPositiveButtonClickListener { millis ->
                val iso = ISO_FMT.format(Date(millis))
                birthEdit.setText(iso)
                tilBirthdate.error = null
            }
            picker.show(parentFragmentManager, "birth_picker")
        } catch (_: Exception) {
            val cal = Calendar.getInstance()
            android.app.DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    val iso = "%04d-%02d-%02d".format(y, m + 1, d)
                    birthEdit.setText(iso)
                    tilBirthdate.error = null
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun loadUserInfo() {
        val gender    = prefs.getString("gender", "") ?: ""
        val birth     = (prefs.getString("birthdate_iso", null)
            ?: prefs.getString("birthdate", "") ?: "")
        val nickname  = prefs.getString("nickname", "") ?: ""
        val mbti      = (prefs.getString("mbti", "") ?: "").uppercase(Locale.ROOT)
        val birthTime = prefs.getString("birth_time", "선택안함") ?: "선택안함"

        view?.findViewById<TextInputEditText>(R.id.edit_birthdate)?.setText(birth)
        view?.findViewById<TextInputEditText>(R.id.edit_nickname)?.setText(nickname)
        view?.findViewById<TextInputEditText>(R.id.edit_MBTI)?.setText(mbti)
        birthTimeSpinner.setSelection(birthTimes.indexOf(birthTime).coerceAtLeast(0), false)

        when (gender) {
            "남성" -> view?.findViewById<RadioButton>(R.id.radio_male)?.isChecked = true
            "여성" -> view?.findViewById<RadioButton>(R.id.radio_female)?.isChecked = true
            else   -> genderGroup.clearCheck()
        }

        val hasRequired = nickname.isNotBlank() && birth.isNotBlank() && gender.isNotBlank()
        if (hasRequired) {
            updateInfoDisplay(gender, birth, birthTime, nickname, mbti)
            toggleEditMode(false)
        } else {
            toggleEditMode(true)
        }
    }

    private fun updateInfoDisplay(gender: String, birth: String, birthTime: String, nickname: String, mbti: String) {
        infoSummary.text = "$nickname 님의 프로필"

        val sb = StringBuilder()
            .append("🧑 닉네임: ").append(nickname).append("\n")
            .append("🎂 생일: ").append(birth).append("\n")
            .append("🕰️ 태어난 시간: ").append(birthTime).append("\n")
            .append("⚧️ 성별: ").append(gender)

        if (mbti.isNotBlank()) {
            sb.append("\n").append("🔮 MBTI: ").append(mbti)
            val meaning = getMbtiMeaning(mbti)
            if (meaning.isNotBlank()) sb.append("\n").append("💬 ").append(meaning)
        }

        infoDetails.text = sb.toString()
        summaryCard.visibility = View.VISIBLE
        updateLoginProviderUI()
    }

    private fun toggleEditMode(enableEdit: Boolean) {
        (view as? ViewGroup)?.let { vg ->
            TransitionManager.beginDelayedTransition(vg, Fade().apply { duration = 150 })
        }
        editCard.visibility = if (enableEdit) View.VISIBLE else View.GONE
        summaryCard.visibility = if (enableEdit) View.GONE else View.VISIBLE
    }

    private fun confirmAndSave() {
        if (isSaving) return
        if (!validateInput()) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("저장하시겠어요?")
            .setMessage("입력하신 정보로 프로필을 저장합니다.")
            .setPositiveButton("저장") { _, _ -> saveUserInfo() }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun saveUserInfo() {
        if (isSaving) return
        isSaving = true
        saveButton.isEnabled = false
        saveButton.text = "저장 중…"
        loadingSpinner.visibility = View.VISIBLE

        val gender = when (genderGroup.checkedRadioButtonId) {
            R.id.radio_male -> "남성"
            R.id.radio_female -> "여성"
            else -> ""
        }
        val birthRaw  = birthEdit.text?.toString()?.trim().orEmpty()
        val birthIso  = normalizeDate(birthRaw)
        val nickname  = nicknameEdit.text?.toString()?.trim().orEmpty()
        val mbti      = mbtiEdit.text?.toString()?.trim()?.uppercase(Locale.ROOT).orEmpty()
        val birthTime = birthTimeSpinner.selectedItem as String

        prefs.edit().apply {
            putString("nickname", nickname)
            putString("birthdate_iso", birthIso)
            putString("birthdate", birthIso)
            putString("gender", gender)
            putString("mbti", mbti)
            putString("birth_time", birthTime)
        }.apply()

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            val profile = mapOf(
                "nickname" to nickname,
                "birthdate_iso" to birthIso,
                "birthdate" to birthIso,
                "gender" to gender,
                "mbti" to mbti,
                "birth_time" to birthTime
            )
            FirestoreManager.saveUserProfile(uid, profile) {
                onSavedDone(gender, birthIso, birthTime, nickname, mbti)
            }
        } else {
            onSavedDone(gender, birthIso, birthTime, nickname, mbti)
        }
    }

    private fun onSavedDone(gender: String, birthIso: String, birthTime: String, nickname: String, mbti: String) {
        loadingSpinner.visibility = View.GONE
        Snackbar.make(requireView(), "저장되었습니다!", Snackbar.LENGTH_SHORT).show()
        updateInfoDisplay(gender, birthIso, birthTime, nickname, mbti)
        toggleEditMode(false)
        isSaving = false
        saveButton.isEnabled = true
        saveButton.text = "저장"
    }

    private fun getMbtiMeaning(mbti: String): String = when (mbti.uppercase(Locale.ROOT)) {
        "INFP" -> "이상주의적이며 감성적인 경향. 상징과 감정이 풍부한 꿈을 꾸는 편."
        "INFJ" -> "통찰이 깊고 조용한 성향. 상징적 메시지가 담긴 꿈과 연결되는 경우가 많음."
        "ENFP" -> "상상력이 풍부하고 에너지가 높음. 스토리텔링이 강한 꿈을 자주 경험."
        "ENFJ" -> "관계 중심적이고 배려심 많음. 사람 간 상호작용이 두드러진 꿈을 꾸기 쉬움."
        "INTP" -> "논리·탐구형. 구조와 규칙성이 드러나는 꿈을 선호하는 경향."
        "INTJ" -> "전략적·계획적. 미래 지향적 시나리오의 꿈을 경험하기도 함."
        "ENTP" -> "아이디어가 넘치고 변주를 즐김. 창의적 반전이 있는 꿈을 자주 경험."
        "ENTJ" -> "목표지향 리더형. 도전·조직화 관련 테마의 꿈을 볼 수 있음."
        "ISFP" -> "섬세한 감수성. 감각·풍경 묘사가 선명한 꿈이 특징."
        "ISTP" -> "현실적·탐험가형. 행동 중심·문제 해결형 꿈 경향."
        "ESFP" -> "즉흥적·경험추구. 생생하고 화려한 장면의 꿈 빈도 높음."
        "ESTP" -> "스릴 선호. 액션과 긴장감 있는 꿈이 잦을 수 있음."
        "ISFJ" -> "헌신적·보호지향. 가족·케어 테마가 자주 등장."
        "ISTJ" -> "책임감·실용성. 사실적·정돈된 꿈 경향."
        "ESFJ" -> "협동적·친화형. 조화로운 관계가 핵심인 꿈."
        "ESTJ" -> "조직·규범 중시. 목표 달성과 절차 중심 꿈."
        else -> ""
    }

    private fun validateInput(): Boolean {
        tilNickname.error = null
        tilBirthdate.error = null
        tilMBTI.error = null

        val nickname = nicknameEdit.text?.toString()?.trim().orEmpty()
        val birthRaw = birthEdit.text?.toString()?.trim().orEmpty()
        val birthIso = normalizeDate(birthRaw)
        val gender = when (genderGroup.checkedRadioButtonId) {
            R.id.radio_male -> "남성"
            R.id.radio_female -> "여성"
            else -> ""
        }

        var ok = true
        if (nickname.isEmpty()) { tilNickname.error = "닉네임을 입력해주세요."; ok = false }
        if (birthIso.isEmpty()) { tilBirthdate.error = "생년월일을 선택해주세요."; ok = false }
        if (gender.isEmpty())   { Snackbar.make(requireView(), "성별을 선택해주세요.", Snackbar.LENGTH_SHORT).show(); ok = false }

        val mbtiRaw = mbtiEdit.text?.toString()?.trim().orEmpty()
        if (mbtiRaw.isNotEmpty() && !Regex("^(I|E)(N|S)(F|T)(P|J)$").matches(mbtiRaw.uppercase(Locale.ROOT))) {
            tilMBTI.error = "MBTI 형식을 확인해주세요. (예: INFP)"; ok = false
        }
        return ok
    }
}
