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
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*
import android.content.res.Configuration
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsFragment : Fragment() {

    private lateinit var prefs: SharedPreferences
    private var isEditMode = false

    private lateinit var genderGroup: RadioGroup
    private lateinit var birthEdit: EditText
    private lateinit var nicknameEdit: EditText
    private lateinit var mbtiEdit: EditText
    private lateinit var saveButton: Button
    private lateinit var infoSummary: TextView
    private lateinit var infoDetails: TextView
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var birthTimeSpinner: Spinner

    private val birthTimes = listOf(
        "선택안함", "자시 (23:00~01:00)", "축시 (01:00~03:00)", "인시 (03:00~05:00)",
        "묘시 (05:00~07:00)", "진시 (07:00~09:00)", "사시 (09:00~11:00)",
        "오시 (11:00~13:00)", "미시 (13:00~15:00)", "신시 (15:00~17:00)",
        "유시 (17:00~19:00)", "술시 (19:00~21:00)", "해시 (21:00~23:00)"
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        prefs = requireContext().getSharedPreferences("user_info_$userId", Context.MODE_PRIVATE)

        genderGroup = view.findViewById(R.id.radioGroup_gender)
        birthEdit = view.findViewById(R.id.edit_birthdate)
        nicknameEdit = view.findViewById(R.id.edit_nickname)
        mbtiEdit = view.findViewById(R.id.edit_MBTI)
        saveButton = view.findViewById(R.id.btn_save)
        infoSummary = view.findViewById(R.id.text_info_summary)
        infoDetails = view.findViewById(R.id.text_user_info)
        loadingSpinner = view.findViewById(R.id.progress_saving)
        birthTimeSpinner = view.findViewById(R.id.spinner_birthtime)

        val adapter = object : ArrayAdapter<String>(requireContext(), R.layout.spinner_item, birthTimes) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(resources.getColor(if (position == 0) R.color.spinner_hint_gray else R.color.spinner_text, null))
                return v
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent) as TextView
                v.setTextColor(resources.getColor(if (position == 0) R.color.spinner_hint_gray else R.color.spinner_text, null))
                return v
            }
        }
        adapter.setDropDownViewResource(R.layout.spinner_item)
        birthTimeSpinner.adapter = adapter
        birthTimeSpinner.setSelection(0, false)

        view.findViewById<AdView>(R.id.adView_settings).loadAd(AdRequest.Builder().build())

        view.findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right, R.anim.slide_in_right, R.anim.slide_out_left)
                .replace(R.id.fragment_container, HomeFragment())
                .commit()
        }

        birthEdit.setOnClickListener { showDatePicker() }

        mbtiEdit.doAfterTextChanged {
            val upper = it.toString().uppercase(Locale.ROOT)
            if (mbtiEdit.text.toString() != upper) {
                mbtiEdit.setText(upper)
                mbtiEdit.setSelection(upper.length)
            }
        }

        loadingSpinner.visibility = View.GONE

        // 🔥 Firebase에서 프로필 동기화
        FirestoreManager.getUserProfile(userId) { profileMap ->
            if (profileMap != null) {
                prefs.edit().apply {
                    profileMap.forEach { (key, value) -> putString(key, value.toString()) }
                    apply()
                }
            }
            loadUserInfo(view)

        }

        saveButton.setOnClickListener {
            it.startAnimation(AnimationUtils.loadAnimation(requireContext(), R.anim.scale_up))

            when (saveButton.text.toString()) {
                "수정" -> toggleEditMode(true)
                "저장", "저장하기" -> {
                    if (!validateInput()) return@setOnClickListener
                    showLoading()
                    saveUserInfo()
                    hideLoading()
                    toggleEditMode(false)
                    Snackbar.make(requireView(), "저장되었습니다!", Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        val logoutButton = view.findViewById<Button>(R.id.btn_logout)
        logoutButton.setOnClickListener {
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
        val korean = Locale.KOREA
        Locale.setDefault(korean)
        val config = Configuration()
        config.setLocale(korean)
        requireContext().createConfigurationContext(config)

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("🌙 생년월일 선택")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .setTheme(R.style.MyDatePickerStyle)
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            val sdf = SimpleDateFormat("yyyy년-M월-d일 (E)", korean)
            birthEdit.setText(sdf.format(Date(selection)))
        }

        picker.show(requireActivity().supportFragmentManager, picker.toString())
    }

    private fun loadUserInfo(view: View) {
        val gender = prefs.getString("gender", "")
        val birth = prefs.getString("birthdate", "")
        val nickname = prefs.getString("nickname", "")
        val mbti = prefs.getString("mbti", "")
        val birthTime = prefs.getString("birth_time", "선택안함")

        birthEdit.setText(birth)
        nicknameEdit.setText(nickname)
        mbtiEdit.setText(mbti)
        birthTimeSpinner.setSelection(birthTimes.indexOf(birthTime))

        if (gender == "남성") view.findViewById<RadioButton>(R.id.radio_male)?.isChecked = true
        if (gender == "여성") view.findViewById<RadioButton>(R.id.radio_female)?.isChecked = true

        val hasAllInfo = !gender.isNullOrBlank() && !birth.isNullOrBlank() && !nickname.isNullOrBlank() && !mbti.isNullOrBlank()

        if (hasAllInfo && !isEditMode) {
            updateInfoDisplay(view, gender!!, birth!!, birthTime ?: "정보 없음", nickname!!, mbti!!)
            toggleEditMode(false)
        } else {
            view.findViewById<View>(R.id.card_user_info).visibility = View.GONE
            toggleEditMode(true)
        }
    }

    private fun updateInfoDisplay(view: View, gender: String, birth: String, birthTime: String, nickname: String, mbti: String) {
        val mbtiMeaning = getMbtiMeaning(mbti)

        val displayText = buildString {
            append("🧑 닉네임: ").append(nickname).append("\n")
            append("🎂 생일: ").append(birth).append("\n")
            append("🕰️ 태어난 시간: ").append(birthTime).append("\n")
            append("⚧️ 성별: ").append(gender).append("\n")
            append("🔮 MBTI: ").append(mbti).append("\n")
            append("💬 ").append(mbtiMeaning)
        }

        infoDetails.text = displayText
        view.findViewById<View>(R.id.card_user_info).visibility = View.VISIBLE
    }

    private fun getMbtiMeaning(mbti: String): String {
        return when (mbti) {
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
    }

    private fun validateInput(): Boolean {
        val gender = when (genderGroup.checkedRadioButtonId) {
            R.id.radio_male -> "남성"
            R.id.radio_female -> "여성"
            else -> ""
        }
        val nickname = nicknameEdit.text.toString().trim()
        val birth = birthEdit.text.toString().trim()
        val mbti = mbtiEdit.text.toString().trim()
        val birthTime = birthTimeSpinner.selectedItem as String

        if (gender.isEmpty()) {
            Toast.makeText(requireContext(), "성별을 선택해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (nickname.isEmpty()) {
            Toast.makeText(requireContext(), "닉네임을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (birth.isEmpty()) {
            Toast.makeText(requireContext(), "생년월일을 선택해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (mbti.isEmpty()) {
            Toast.makeText(requireContext(), "MBTI를 입력해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }
        if (birthTime == "선택안함") {
            Toast.makeText(requireContext(), "태어난 시간을 선택해주세요.", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun saveUserInfo() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val selectedGender = when (genderGroup.checkedRadioButtonId) {
            R.id.radio_male -> "남성"
            R.id.radio_female -> "여성"
            else -> ""
        }

        val birth = birthEdit.text.toString()
        val nickname = nicknameEdit.text.toString()
        val mbti = mbtiEdit.text.toString().uppercase(Locale.ROOT)
        val birthTime = birthTimeSpinner.selectedItem as String
        val mbtiMeaning = getMbtiMeaning(mbti)

        val profileMap = mapOf(
            "gender" to selectedGender,
            "birthdate" to birth,
            "nickname" to nickname,
            "mbti" to mbti,
            "birth_time" to birthTime,
            "mbti_meaning" to mbtiMeaning
        )

        prefs.edit().apply {
            profileMap.forEach { (key, value) -> putString(key, value) }
            apply()
        }

        FirestoreManager.saveUserProfile(userId, profileMap)
        updateInfoDisplay(requireView(), selectedGender, birth, birthTime, nickname, mbti)
    }

    private fun toggleEditMode(editMode: Boolean) {
        isEditMode = editMode
        val view = view ?: return

        val editViews = listOf(
            genderGroup, birthEdit, nicknameEdit, mbtiEdit, birthTimeSpinner,
            view.findViewById(R.id.label_gender),
            view.findViewById(R.id.label_birthdate),
            view.findViewById(R.id.label_nickname),
            view.findViewById(R.id.label_mbti),
            view.findViewById(R.id.label_birthtime)
        )

        val showViews = listOf(infoSummary, infoDetails)
        val cardUserInfo = view.findViewById<View>(R.id.card_user_info)

        if (editMode) {
            editViews.forEach { it.visibility = View.VISIBLE }
            showViews.forEach { it.visibility = View.GONE }
            cardUserInfo.visibility = View.GONE
            saveButton.text = "저장"
        } else {
            editViews.forEach { it.visibility = View.GONE }
            showViews.forEach { it.visibility = View.VISIBLE }
            cardUserInfo.visibility = View.VISIBLE
            saveButton.text = "수정"
        }

        if (editMode) {
            loadUserDataToFields()
        }
    }

    private fun loadUserDataToFields() {
        val gender = prefs.getString("gender", "")
        val birth = prefs.getString("birthdate", "")
        val nickname = prefs.getString("nickname", "")
        val mbti = prefs.getString("mbti", "")
        val birthTime = prefs.getString("birth_time", "선택안함")

        birthEdit.setText(birth)
        nicknameEdit.setText(nickname)
        mbtiEdit.setText(mbti)
        birthTimeSpinner.setSelection(birthTimes.indexOf(birthTime))

        genderGroup.clearCheck()
        if (gender == "남성") requireView().findViewById<RadioButton>(R.id.radio_male)?.isChecked = true
        if (gender == "여성") requireView().findViewById<RadioButton>(R.id.radio_female)?.isChecked = true
    }

    private fun showLoading() {
        infoDetails.text = "로딩 중..."
        loadingSpinner.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        loadingSpinner.visibility = View.GONE
    }
}
