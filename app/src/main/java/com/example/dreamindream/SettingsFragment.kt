// file: app/src/main/java/com/example/dreamindream/SettingsFragment.kt
package com.example.dreamindream

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.*
import android.view.animation.AnimationUtils
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
import android.transition.TransitionSet
import android.transition.Fade
import android.transition.Slide
import android.view.Gravity
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : Fragment() {

    private lateinit var prefs: SharedPreferences
    private var isEditMode = true
    private var isSaving = false

    // Views
    private lateinit var editGroup: View
    private lateinit var summaryCard: View
    private lateinit var saveButton: com.google.android.material.button.MaterialButton
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

    private val birthTimes = listOf(
        "선택안함",
        "자시 (23:00~01:00)", "축시 (01:00~03:00)", "인시 (03:00~05:00)",
        "묘시 (05:00~07:00)", "진시 (07:00~09:00)", "사시 (09:00~11:00)",
        "오시 (11:00~13:00)", "미시 (13:00~15:00)", "신시 (15:00~17:00)",
        "유시 (17:00~19:00)", "술시 (19:00~21:00)", "해시 (21:00~23:00)"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        prefs = requireContext().getSharedPreferences("user_info_$uid", Context.MODE_PRIVATE)

        // Find views
        editGroup       = view.findViewById(R.id.edit_group)
        summaryCard     = view.findViewById(R.id.card_user_info)
        saveButton      = view.findViewById(R.id.btn_save)
        loadingSpinner  = view.findViewById(R.id.progress_saving)

        genderGroup     = view.findViewById(R.id.radioGroup_gender)
        tilNickname     = view.findViewById(R.id.tilNickname)
        tilBirthdate    = view.findViewById(R.id.tilBirthdate)
        tilMBTI         = view.findViewById(R.id.tilMBTI)
        birthEdit       = view.findViewById(R.id.edit_birthdate)
        nicknameEdit    = view.findViewById(R.id.edit_nickname)
        mbtiEdit        = view.findViewById(R.id.edit_MBTI)
        birthTimeSpinner= view.findViewById(R.id.spinner_birthtime)
        infoSummary     = view.findViewById(R.id.text_info_summary)
        infoDetails     = view.findViewById(R.id.text_user_info)

        // Ad
        view.findViewById<AdView>(R.id.adView_settings).loadAd(AdRequest.Builder().build())

        // Spinner adapter (프리미엄 톤 유지)
        val hintColor = 0xFF86A1B3.toInt()
        val textColor = 0xFFE8F1F8.toInt()
        val adapter = object : ArrayAdapter<String>(
            requireContext(),
            R.layout.spinner_item,
            android.R.id.text1,
            birthTimes
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(if (position == 0) hintColor else textColor)
                return v
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = layoutInflater.inflate(R.layout.spinner_item, parent, false) as TextView
                tv.text = getItem(position)
                tv.setTextColor(if (position == 0) hintColor else textColor)
                return tv
            }
        }
        birthTimeSpinner.adapter = adapter
        birthTimeSpinner.setSelection(
            birthTimes.indexOf(prefs.getString("birth_time", "선택안함")).coerceAtLeast(0), false
        )

        // 🔹 로컬 캐시로 즉시 UI 모드/필드 세팅 → 깜빡임 제거
        loadUserInfo()

        // 생년월일 피커
        fun openBirthPicker() = showDatePicker()
        tilBirthdate.setEndIconOnClickListener { openBirthPicker() }
        birthEdit.setOnClickListener { openBirthPicker() }
        view.findViewById<View>(R.id.label_birthdate).setOnClickListener { openBirthPicker() }

        // MBTI 대문자화(옵션 필드)
        mbtiEdit.doAfterTextChanged {
            val up = it.toString().uppercase(Locale.ROOT)
            if (mbtiEdit.text.toString() != up) {
                mbtiEdit.setText(up)
                mbtiEdit.setSelection(up.length)
            }
            tilMBTI.error = null
        }
        nicknameEdit.doAfterTextChanged { tilNickname.error = null }

        // 원격 → 로컬 → UI 최신화 (조용히 갱신)
        FirestoreManager.getUserProfile(uid) { map ->
            if (map != null) {
                prefs.edit().apply {
                    map.forEach { (k, v) -> putString(k, v.toString()) }
                    apply()
                }
            }
            loadUserInfo() // 최신값 반영 (무음 업데이트)
        }

        // 저장/수정 버튼
        saveButton.setOnClickListener {
            it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up))
            if (isEditMode) {
                if (!validateInput()) return@setOnClickListener
                confirmAndSave()
            } else {
                toggleEditMode(true)
            }
        }

        // 로그아웃
        view.findViewById<View>(R.id.btn_logout).setOnClickListener {
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

    private fun showDatePicker() {
        try {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("🌙 생년월일 선택")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build()
            picker.addOnPositiveButtonClickListener { millis ->
                val sdf = SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREA)
                birthEdit.setText(sdf.format(Date(millis)))
                tilBirthdate.error = null
            }
            picker.show(parentFragmentManager, "birth_picker")
        } catch (_: Exception) {
            val cal = Calendar.getInstance()
            android.app.DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    birthEdit.setText("${y}년 ${m + 1}월 ${d}일")
                    tilBirthdate.error = null
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    /** 로컬 캐시 기반으로 즉시 화면 구성 */
    private fun loadUserInfo() {
        val gender    = prefs.getString("gender", "") ?: ""
        val birth     = prefs.getString("birthdate", "") ?: ""
        val nickname  = prefs.getString("nickname", "") ?: ""
        val mbti      = prefs.getString("mbti", "") ?: ""
        val birthTime = prefs.getString("birth_time", "선택안함") ?: "선택안함"

        birthEdit.setText(birth)
        nicknameEdit.setText(nickname)
        mbtiEdit.setText(mbti)
        birthTimeSpinner.setSelection(birthTimes.indexOf(birthTime).coerceAtLeast(0), false)

        when (gender) {
            "남성" -> view?.findViewById<RadioButton>(R.id.radio_male)?.isChecked = true
            "여성" -> view?.findViewById<RadioButton>(R.id.radio_female)?.isChecked = true
            else   -> genderGroup.clearCheck()
        }

        // ✅ 필수값만 확인 (닉네임/생년월일/성별). MBTI, 출생시간은 옵션.
        val hasRequired = nickname.isNotBlank() && birth.isNotBlank() && gender.isNotBlank()
        toggleEditMode(!hasRequired)
        if (hasRequired) updateInfoDisplay(gender, birth, birthTime, nickname, mbti)
    }

    private fun updateInfoDisplay(
        gender: String, birth: String, birthTime: String, nickname: String, mbti: String
    ) {
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
    }

    /** 부드러운 전환으로 ‘대기업 느낌’ */
    private fun toggleEditMode(enableEdit: Boolean) {
        isEditMode = enableEdit
        val root = view ?: return

        val transition = TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            addTransition(Fade(Fade.OUT))
            addTransition(Slide(Gravity.TOP))
            addTransition(Fade(Fade.IN))
            duration = 180
        }
        TransitionManager.beginDelayedTransition(root as ViewGroup, transition)

        if (enableEdit) {
            editGroup.visibility = View.VISIBLE
            summaryCard.visibility = View.GONE
            saveButton.text = "저장"
            saveButton.isEnabled = true
        } else {
            editGroup.visibility = View.GONE
            summaryCard.visibility = View.VISIBLE
            saveButton.text = "수정"
            saveButton.isEnabled = true
        }
    }

    private fun confirmAndSave() {
        if (isSaving) return
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

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val gender = when (genderGroup.checkedRadioButtonId) {
            R.id.radio_male -> "남성"
            R.id.radio_female -> "여성"
            else -> ""
        }
        val birth     = birthEdit.text?.toString()?.trim().orEmpty()
        val nickname  = nicknameEdit.text?.toString()?.trim().orEmpty()
        val mbti      = mbtiEdit.text?.toString()?.trim()?.uppercase(Locale.ROOT).orEmpty()
        val birthTime = birthTimeSpinner.selectedItem as String

        val profile = mapOf(
            "gender" to gender,
            "birthdate" to birth,
            "nickname" to nickname,
            "mbti" to mbti,                     // 옵션
            "birth_time" to birthTime           // 옵션
        )

        // 로컬 선저장 → 즉시 반영
        prefs.edit().apply { profile.forEach { (k, v) -> putString(k, v) }; apply() }

        // 원격 저장 후 UI 업데이트
        FirestoreManager.saveUserProfile(uid, profile) {
            loadingSpinner.visibility = View.GONE
            Snackbar.make(requireView(), "저장되었습니다!", Snackbar.LENGTH_SHORT).show()
            updateInfoDisplay(gender, birth, birthTime, nickname, mbti)
            toggleEditMode(false)   // 저장 후 즉시 요약 모드
            isSaving = false
        }
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

    /** 필수: 닉네임/생년월일/성별만 검사 (MBTI/출생시간은 옵션) */
    private fun validateInput(): Boolean {
        tilNickname.error = null
        tilBirthdate.error = null
        tilMBTI.error = null

        val nickname = nicknameEdit.text?.toString()?.trim().orEmpty()
        val birth    = birthEdit.text?.toString()?.trim().orEmpty()
        val gender = when (genderGroup.checkedRadioButtonId) {
            R.id.radio_male -> "남성"
            R.id.radio_female -> "여성"
            else -> ""
        }

        var ok = true
        if (nickname.isEmpty()) { tilNickname.error = "닉네임을 입력해주세요."; ok = false }
        if (birth.isEmpty())    { tilBirthdate.error = "생년월일을 선택해주세요."; ok = false }
        if (gender.isEmpty())   { Snackbar.make(requireView(), "성별을 선택해주세요.", Snackbar.LENGTH_SHORT).show(); ok = false }

        // MBTI는 형식 검사만 “입력했을 때” 적용 (옵션)
        val mbtiRaw = mbtiEdit.text?.toString()?.trim().orEmpty()
        if (mbtiRaw.isNotEmpty() && !Regex("^(I|E)(N|S)(F|T)(P|J)$").matches(mbtiRaw.uppercase(Locale.ROOT))) {
            tilMBTI.error = "MBTI 형식을 확인해주세요. (예: INFP)"; ok = false
        }
        return ok
    }
}