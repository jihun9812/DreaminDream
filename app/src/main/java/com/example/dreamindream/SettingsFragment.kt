package com.example.dreamindream

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
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
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : Fragment() {

    private lateinit var prefs: SharedPreferences
    private var isEditMode = true
    private var isSaving = false

    private lateinit var genderGroup: RadioGroup
    private lateinit var tilNickname: TextInputLayout
    private lateinit var tilBirthdate: TextInputLayout
    private lateinit var tilMBTI: TextInputLayout
    private lateinit var birthEdit: TextInputEditText
    private lateinit var nicknameEdit: TextInputEditText
    private lateinit var mbtiEdit: TextInputEditText
    private lateinit var saveButton: com.google.android.material.button.MaterialButton
    private lateinit var infoSummary: TextView
    private lateinit var infoDetails: TextView
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var birthTimeSpinner: Spinner

    private val birthTimes = listOf(
        "선택안함",
        "자시 (23:00~01:00)", "축시 (01:00~03:00)", "인시 (03:00~05:00)",
        "묘시 (05:00~07:00)", "진시 (07:00~09:00)", "사시 (09:00~11:00)",
        "오시 (11:00~13:00)", "미시 (13:00~15:00)", "신시 (15:00~17:00)",
        "유시 (17:00~19:00)", "술시 (19:00~21:00)", "해시 (21:00~23:00)"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        prefs = requireContext().getSharedPreferences("user_info_$userId", Context.MODE_PRIVATE)

        genderGroup     = view.findViewById(R.id.radioGroup_gender)
        tilNickname     = view.findViewById(R.id.tilNickname)
        tilBirthdate    = view.findViewById(R.id.tilBirthdate)
        tilMBTI         = view.findViewById(R.id.tilMBTI)
        birthEdit       = view.findViewById(R.id.edit_birthdate)
        nicknameEdit    = view.findViewById(R.id.edit_nickname)
        mbtiEdit        = view.findViewById(R.id.edit_MBTI)
        saveButton      = view.findViewById(R.id.btn_save)
        infoSummary     = view.findViewById(R.id.text_info_summary)
        infoDetails     = view.findViewById(R.id.text_user_info)
        loadingSpinner  = view.findViewById(R.id.progress_saving)
        birthTimeSpinner= view.findViewById(R.id.spinner_birthtime)

        // 광고
        view.findViewById<AdView>(R.id.adView_settings).loadAd(AdRequest.Builder().build())

        // 스피너 톤
        val hintColor = Color.parseColor("#86A1B3")
        val textColor = Color.parseColor("#E8F1F8")
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
                val v = layoutInflater.inflate(R.layout.spinner_dropdown_item, parent, false) as TextView
                v.text = getItem(position)
                v.setTextColor(if (position == 0) hintColor else textColor)
                return v
            }
        }
        birthTimeSpinner.adapter = adapter
        birthTimeSpinner.setSelection(birthTimes.indexOf(prefs.getString("birth_time", "선택안함")).coerceAtLeast(0), false)

        // 생년월일: 아이콘/필드 클릭 모두 달력
        fun openBirthPicker() = showDatePicker()
        tilBirthdate.setEndIconOnClickListener { openBirthPicker() }
        birthEdit.setOnClickListener { openBirthPicker() }
        view.findViewById<View>(R.id.label_birthdate).setOnClickListener { openBirthPicker() }

        // MBTI 대문자 + 에러 클리어
        mbtiEdit.doAfterTextChanged {
            val up = it.toString().uppercase(Locale.ROOT)
            if (mbtiEdit.text.toString() != up) {
                mbtiEdit.setText(up)
                mbtiEdit.setSelection(up.length)
            }
            tilMBTI.error = null
        }
        // 닉네임/생년월일 에러 클리어
        nicknameEdit.doAfterTextChanged { tilNickname.error = null }

        // 원격 → 로컬 캐시 → UI
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        FirestoreManager.getUserProfile(uid) { map ->
            if (map != null) {
                prefs.edit().apply { map.forEach { (k, v) -> putString(k, v.toString()) }; apply() }
            }
            loadUserInfo()
        }

        saveButton.setOnClickListener {
            it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up))
            if (isEditMode) {
                if (!validateInput()) return@setOnClickListener
                confirmAndSave()
            } else {
                toggleEditMode(true)
            }
        }

        view.findViewById<View>(R.id.btn_logout).setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("로그아웃")
                .setMessage("정말 로그아웃 하시겠어요?")
                .setPositiveButton("확인") { _, _ ->
                    FirebaseAuth.getInstance().signOut()
                    val intent = android.content.Intent(requireContext(), LoginActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
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
                { _, y, m, d -> birthEdit.setText("${y}년 ${m + 1}월 ${d}일"); tilBirthdate.error = null },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun loadUserInfo() {
        val gender   = prefs.getString("gender", "") ?: ""
        val birth    = prefs.getString("birthdate", "") ?: ""
        val nickname = prefs.getString("nickname", "") ?: ""
        val mbti     = prefs.getString("mbti", "") ?: ""
        val birthTime= prefs.getString("birth_time", "선택안함") ?: "선택안함"

        birthEdit.setText(birth)
        nicknameEdit.setText(nickname)
        mbtiEdit.setText(mbti)
        birthTimeSpinner.setSelection(birthTimes.indexOf(birthTime).coerceAtLeast(0), false)

        when (gender) {
            "남성" -> view?.findViewById<RadioButton>(R.id.radio_male)?.isChecked = true
            "여성" -> view?.findViewById<RadioButton>(R.id.radio_female)?.isChecked = true
            else -> genderGroup.clearCheck()
        }

        val hasAll = gender.isNotBlank() && birth.isNotBlank() && nickname.isNotBlank() &&
                mbti.length == 4 && birthTime != "선택안함"
        toggleEditMode(!hasAll)
        if (hasAll) updateInfoDisplay(gender, birth, birthTime, nickname, mbti)
    }

    private fun updateInfoDisplay(gender: String, birth: String, birthTime: String, nickname: String, mbti: String) {
        infoSummary.text = "$nickname 님의 프로필"
        val mbtiMeaning = getMbtiMeaning(mbti)
        val text = buildString {
            append("🧑 닉네임: ").append(nickname).append("\n")
            append("🎂 생일: ").append(birth).append("\n")
            append("🕰️ 태어난 시간: ").append(birthTime).append("\n")
            append("⚧️ 성별: ").append(gender).append("\n")
            append("🔮 MBTI: ").append(mbti).append("\n")
            append("💬 ").append(mbtiMeaning)
        }
        infoDetails.text = text
        view?.findViewById<View>(R.id.card_user_info)?.visibility = View.VISIBLE
    }

    private fun toggleEditMode(enableEdit: Boolean) {
        isEditMode = enableEdit
        val v = view ?: return
        val editors = listOf<View>(
            v.findViewById(R.id.label_nickname),
            v.findViewById(R.id.label_birthdate),
            v.findViewById(R.id.label_birthtime),
            v.findViewById(R.id.label_gender),
            v.findViewById(R.id.label_mbti),
            v.findViewById(R.id.tilNickname),
            v.findViewById(R.id.tilBirthdate),
            v.findViewById(R.id.spinner_birthtime),
            v.findViewById(R.id.radioGroup_gender),
            v.findViewById(R.id.tilMBTI)
        )
        val summaryCard = v.findViewById<View>(R.id.card_user_info)

        if (enableEdit) {
            editors.forEach { it.visibility = View.VISIBLE }
            summaryCard.visibility = View.GONE
            saveButton.text = "저장"
            saveButton.isEnabled = true
        } else {
            editors.forEach { it.visibility = View.GONE }
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

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val gender = when (genderGroup.checkedRadioButtonId) {
            R.id.radio_male -> "남성"
            R.id.radio_female -> "여성"
            else -> ""
        }
        val birth    = birthEdit.text?.toString()?.trim().orEmpty()
        val nickname = nicknameEdit.text?.toString()?.trim().orEmpty()
        val mbti     = mbtiEdit.text?.toString()?.trim()?.uppercase(Locale.ROOT).orEmpty()
        val birthTime= birthTimeSpinner.selectedItem as String
        val mbtiMeaning = getMbtiMeaning(mbti)

        val profile = mapOf(
            "gender" to gender,
            "birthdate" to birth,
            "nickname" to nickname,
            "mbti" to mbti,
            "birth_time" to birthTime,
            "mbti_meaning" to mbtiMeaning
        )

        // 로컬 먼저
        prefs.edit().apply { profile.forEach { (k, v) -> putString(k, v) }; apply() }

        // 원격 저장
        FirestoreManager.saveUserProfile(userId, profile) {
            loadingSpinner.visibility = View.GONE
            Snackbar.make(requireView(), "저장되었습니다!", Snackbar.LENGTH_SHORT).show()
            updateInfoDisplay(gender, birth, birthTime, nickname, mbti)
            toggleEditMode(false)
            isSaving = false
        }
    }

    private fun getMbtiMeaning(mbti: String): String = when (mbti) {
        "INFP" -> "이상주의적이며 감성적인 사람. 몽환적이고 의미 있는 꿈을 많이 꿉니다."
        "INFJ" -> "통찰력 있고 조용한 성격. 상징적인 꿈과 연결됨."
        "ENFP" -> "열정과 상상력이 풍부. 감성적이고 자유로운 꿈을 꿉니다."
        "ENFJ" -> "타인을 이해하고 돕고자 하는 성향. 관계 중심의 꿈을 자주 꿉니다."
        "INTP" -> "논리적이고 탐구적인 성향. 퍼즐 구조나 원리 기반의 꿈을 자주 꿉니다."
        "INTJ" -> "전략적이고 계획적인 성향. 미래 예측형 꿈을 자주 꿉니다."
        "ENTP" -> "아이디어가 넘치고 토론을 즐김. 창의적이고 스토리 있는 꿈 유형."
        "ENTJ" -> "리더십이 강하고 목표지향적. 도전과 통제 관련 꿈을 자주 꿉니다."
        "ISFP" -> "감성적이고 섬세한 예술가형. 풍경이나 감각 중심의 꿈이 많습니다."
        "ISTP" -> "탐험적이고 현실적인 성향. 행동 중심의 꿈을 자주 꿉니다."
        "ESFP" -> "즉흥적이고 즐거움을 추구. 화려하고 생생한 꿈을 잘 꿉니다."
        "ESTP" -> "스릴과 모험을 즐김. 액션이나 위기 상황의 꿈이 많습니다."
        "ISFJ" -> "헌신적이고 배려심 많은 성향. 가족이나 보호에 관련된 꿈이 많습니다."
        "ISTJ" -> "책임감 있고 실용적인 성격. 정돈되고 사실적인 꿈을 잘 꿉니다."
        "ESFJ" -> "친절하고 협동적인 성향. 사람들과 조화로운 상황의 꿈이 많습니다."
        "ESTJ" -> "조직적이고 실용적인 리더형. 목표 달성이나 구조화된 꿈이 많습니다."
        else -> "MBTI 유형 기반 해석 정보가 없습니다."
    }

    private fun validateInput(): Boolean {
        // 에러 초기화
        tilNickname.error = null
        tilBirthdate.error = null
        tilMBTI.error = null

        val gender = when (genderGroup.checkedRadioButtonId) {
            R.id.radio_male -> "남성"
            R.id.radio_female -> "여성"
            else -> ""
        }
        val nickname = nicknameEdit.text?.toString()?.trim().orEmpty()
        val birth = birthEdit.text?.toString()?.trim().orEmpty()
        val mbti = mbtiEdit.text?.toString()?.trim()?.uppercase(Locale.ROOT).orEmpty()
        val birthTime = birthTimeSpinner.selectedItem as String

        var ok = true
        if (nickname.isEmpty()) { tilNickname.error = "닉네임을 입력해주세요."; ok = false }
        if (birth.isEmpty())    { tilBirthdate.error = "생년월일을 선택해주세요."; ok = false }
        if (mbti.length != 4 || !"^(I|E)(N|S)(F|T)(P|J)$".toRegex().matches(mbti)) {
            tilMBTI.error = "MBTI 형식을 확인해주세요. (예: INFP)"; ok = false
        }
        if (gender.isEmpty()) { Snackbar.make(requireView(), "성별을 선택해주세요.", Snackbar.LENGTH_SHORT).show(); ok = false }
        if (birthTime == "선택안함") { Snackbar.make(requireView(), "태어난 시간을 선택해주세요.", Snackbar.LENGTH_SHORT).show(); ok = false }

        return ok
    }
}
