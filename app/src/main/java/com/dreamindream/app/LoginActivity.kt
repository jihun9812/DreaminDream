package com.dreamindream.app

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.ScaleAnimation
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class LoginActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var loginCardContent: FrameLayout

    // 앱 전역 설정(언어 등)
    private val appPrefs by lazy { getSharedPreferences("app", MODE_PRIVATE) }

    // 로그인 화면에서 “두 번 눌러 종료”
    private var lastBackPressedAt = 0L
    private val BACK_INTERVAL_MS = 1500L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val skipLoginButton = findViewById<Button>(R.id.btn_skip_login)
        skipLoginButton.applyPressEffect {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    // 홈 페이드인 예약
                    markPendingHomeFade()
                    saveUserInfoToFirestore()
                    showCardLoadingAndDownloadHolidays()
                }
                .addOnFailureListener {
                    Toast.makeText(
                        this,
                        getString(R.string.toast_anonymous_failed_fmt, it.message ?: "-"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }

        loginCardContent = findViewById(R.id.loginCardContent)
        val loginPanel = findViewById<LinearLayout>(R.id.loginPanel)
        val signupPanel = findViewById<LinearLayout>(R.id.signupPanel)
        val findPwPanel = findViewById<LinearLayout>(R.id.findPwPanel)

        val emailInput = findViewById<EditText>(R.id.input_email)
        val passwordInput = findViewById<EditText>(R.id.input_password)

        val loginButton = findViewById<Button>(R.id.btn_login)
        val googleButton = findViewById<ImageButton>(R.id.btn_google)
        val goSignup = findViewById<TextView>(R.id.goSignup)
        val goFindPw = findViewById<TextView>(R.id.goFindPw)

        val signupEmail = findViewById<EditText>(R.id.signup_email)
        val signupPw = findViewById<EditText>(R.id.signup_password)
        val signupPwConfirm = findViewById<EditText>(R.id.signup_password_confirm)
        val signupBtn = findViewById<Button>(R.id.btn_signup)
        val signupCancelBtn = findViewById<Button>(R.id.btn_signup_cancel)

        val findPwEmail = findViewById<EditText>(R.id.findpw_email)
        val findPwBtn = findViewById<Button>(R.id.btn_findpw)
        val findPwCancelBtn = findViewById<Button>(R.id.btn_findpw_cancel)

        // 패널 전환
        goSignup.applyPressEffect {
            loginPanel.visibility = View.GONE
            findPwPanel.visibility = View.GONE
            signupPanel.visibility = View.VISIBLE
        }
        goFindPw.applyPressEffect {
            loginPanel.visibility = View.GONE
            signupPanel.visibility = View.GONE
            findPwPanel.visibility = View.VISIBLE
        }
        signupCancelBtn.applyPressEffect {
            signupPanel.visibility = View.GONE
            findPwPanel.visibility = View.GONE
            loginPanel.visibility = View.VISIBLE
        }
        findPwCancelBtn.applyPressEffect {
            findPwPanel.visibility = View.GONE
            signupPanel.visibility = View.GONE
            loginPanel.visibility = View.VISIBLE
        }

        // 로그인
        loginButton.applyPressEffect {
            val email = emailInput.text.toString().trim()
            val pw = passwordInput.text.toString()
            if (email.isBlank() || pw.isBlank()) {
                Toast.makeText(
                    this,
                    getString(R.string.toast_fill_email_password),
                    Toast.LENGTH_SHORT
                ).show()
                return@applyPressEffect
            }
            auth.signInWithEmailAndPassword(email, pw)
                .addOnSuccessListener {
                    if (auth.currentUser?.isEmailVerified == true) {
                        saveUserInfoToFirestore()
                        val uid = auth.currentUser?.uid ?: return@addOnSuccessListener
                        checkReactivation(uid) {
                            markPendingHomeFade()
                            showCardLoadingAndDownloadHolidays()
                        }
                    } else {
                        Toast.makeText(
                            this,
                            getString(R.string.toast_email_verify_first),
                            Toast.LENGTH_LONG
                        ).show()
                        auth.signOut()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(
                        this,
                        getString(R.string.toast_login_failed_fmt, it.message ?: "-"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }

        // 구글 로그인
        googleButton.applyPressEffect {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        // 회원가입 처리
        signupBtn.applyPressEffect {
            val email = signupEmail.text.toString().trim()
            val pw = signupPw.text.toString()
            val pw2 = signupPwConfirm.text.toString()
            if (email.isBlank() || pw.isBlank() || pw2.isBlank()) {
                Toast.makeText(this, getString(R.string.toast_fill_all_fields), Toast.LENGTH_SHORT).show()
                return@applyPressEffect
            }
            if (pw != pw2) {
                Toast.makeText(this, getString(R.string.toast_password_mismatch), Toast.LENGTH_SHORT).show()
                return@applyPressEffect
            }
            auth.createUserWithEmailAndPassword(email, pw)
                .addOnSuccessListener {
                    Toast.makeText(this, getString(R.string.toast_signup_success), Toast.LENGTH_LONG).show()
                    signupPanel.visibility = View.GONE
                    loginPanel.visibility = View.VISIBLE
                }
                .addOnFailureListener {
                    Toast.makeText(this, getString(R.string.toast_signup_failed_fmt, it.message ?: "-"), Toast.LENGTH_SHORT).show()
                }
        }

        // 비밀번호 재설정
        findPwBtn.applyPressEffect {
            val email = findPwEmail.text.toString().trim()
            if (email.isBlank()) {
                Toast.makeText(this, getString(R.string.toast_enter_email), Toast.LENGTH_SHORT).show()
                return@applyPressEffect
            }
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Toast.makeText(this, getString(R.string.toast_reset_sent), Toast.LENGTH_LONG).show()
                    findPwPanel.visibility = View.GONE
                    loginPanel.visibility = View.VISIBLE
                }
                .addOnFailureListener {
                    Toast.makeText(this, getString(R.string.toast_reset_failed_fmt, it.message ?: "-"), Toast.LENGTH_SHORT).show()
                }
        }

        // ====== 시스템 뒤로가기 처리 ======
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 1) 키보드 닫기
                currentFocus?.let { v ->
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                }

                // 2) 보이는 패널 기준 분기
                when {
                    signupPanel.isVisible || findPwPanel.isVisible -> {
                        // 회원가입/비번재설정 → 로그인으로 복귀
                        signupPanel.visibility = View.GONE
                        findPwPanel.visibility = View.GONE
                        loginPanel.visibility = View.VISIBLE
                    }
                    loginPanel.isVisible -> {
                        // 로그인 화면 → 두 번 눌러 종료
                        val now = System.currentTimeMillis()
                        if (now - lastBackPressedAt <= BACK_INTERVAL_MS) {
                            // 기본 뒤로가기(액티비티 종료) 위임
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        } else {
                            lastBackPressedAt = now
                            val resId = resources.getIdentifier(
                                "press_back_again_to_exit", "string", packageName
                            )
                            val msg = if (resId != 0)
                                getString(resId)
                            else
                                "한 번 더 누르면 앱이 종료됩니다."
                            Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> {
                        // 예외 케이스는 기본 동작
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        // ===== 언어 칩 & 코치마크 =====
        setupLanguageChips()
        showLanguageCoachmarkIfNeeded()
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            auth.signInWithCredential(credential)
                .addOnSuccessListener {
                    saveUserInfoToFirestore()
                    val uid = auth.currentUser?.uid ?: return@addOnSuccessListener
                    checkReactivation(uid) {
                        markPendingHomeFade()
                        showCardLoadingAndDownloadHolidays()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, getString(R.string.toast_google_login_failed_fmt, it.message ?: "-"), Toast.LENGTH_SHORT).show()
                }
        } catch (e: ApiException) {
            Toast.makeText(this, getString(R.string.toast_google_login_error_fmt, e.message ?: "-"), Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkReactivation(uid: String, onContinue: () -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val status = doc.getString("status") ?: "active"
                if (status != "deactivated") {
                    onContinue(); return@addOnSuccessListener
                }
                val purgeMs = doc.getTimestamp("purgeAt")?.toDate()?.time ?: 0L
                if (System.currentTimeMillis() < purgeMs) {
                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(getString(R.string.dialog_reactivate_title))
                        .setMessage(getString(R.string.dialog_reactivate_message))
                        .setPositiveButton(getString(R.string.dialog_reactivate_positive)) { _, _ ->
                            db.collection("users").document(uid)
                                .set(mapOf("status" to "active"), SetOptions.merge())
                                .addOnSuccessListener { onContinue() }
                                .addOnFailureListener {
                                    Toast.makeText(
                                        this,
                                        getString(R.string.toast_reactivate_failed_fmt, it.localizedMessage ?: "-"),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                        .setNegativeButton(getString(R.string.dialog_reactivate_negative)) { _, _ ->
                            FirebaseAuth.getInstance().signOut()
                        }
                        .show()
                } else {
                    Toast.makeText(this, getString(R.string.toast_account_deleted), Toast.LENGTH_LONG).show()
                    FirebaseAuth.getInstance().signOut()
                }
            }
            .addOnFailureListener { onContinue() }
    }

    private fun showCardLoadingAndDownloadHolidays() {
        loginCardContent.removeAllViews()
        val loadingLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 120, 40, 120)
        }
        val loadingBar = ProgressBar(this).apply { isIndeterminate = true }
        val loadingText = TextView(this).apply {
            text = getString(R.string.loading_calendar_prep)
            textSize = 17f
            setTextColor(0xFF7A55D3.toInt())
            setPadding(0, 22, 0, 0)
            gravity = Gravity.CENTER
        }
        loadingLayout.addView(loadingBar)
        loadingLayout.addView(loadingText)
        loginCardContent.addView(loadingLayout)

        val launchMain = {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
        }

        val targetYears = listOf(2025, 2026)
        val holidays = mutableListOf<Holiday>()
        var doneCount = 0
        targetYears.forEach { year ->
            HolidayApi.fetchHolidays(
                year,
                onSuccess = { list ->
                    holidays.addAll(list)
                    doneCount++
                    if (doneCount == targetYears.size) {
                        HolidayStorage.saveHolidays(this, holidays)
                        launchMain()
                    }
                },
                onError = {
                    doneCount++
                    if (doneCount == targetYears.size) {
                        HolidayStorage.saveHolidays(this, holidays)
                        launchMain()
                    }
                }
            )
        }
    }

    /** 홈 첫 진입에서 페이드인을 실행시키도록 사용자별 프리퍼런스에 예약 표시 */
    private fun markPendingHomeFade() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        getSharedPreferences("dream_history_$uid", MODE_PRIVATE)
            .edit().putBoolean("pending_home_fade", true).apply()
    }

    private fun saveUserInfoToFirestore() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(user.uid)

        val userData = mapOf(
            "email" to (if (user.isAnonymous) "guest" else user.email ?: "unknown"),
            "name" to (user.displayName
                ?: if (user.isAnonymous) getString(R.string.name_guest_default)
                else getString(R.string.name_anonymous_default)),
            "last_login" to System.currentTimeMillis()
        )

        userRef.set(userData, SetOptions.merge())
    }

    private fun View.applyPressEffect(scale: Float = 0.95f, duration: Long = 100L, action: () -> Unit) {
        this.setOnClickListener {
            val anim = ScaleAnimation(
                1f, scale, 1f, scale,
                (this.width / 2).toFloat(), (this.height / 2).toFloat()
            ).apply {
                this.duration = duration
                fillAfter = true
            }
            this.startAnimation(anim)
            this.postDelayed({
                this.clearAnimation()
                action()
            }, duration)
        }
    }

    /* ===== 언어: 칩만 사용, 첫 실행 코치마크 ===== */
    private fun setupLanguageChips() {
        val chips = findViewById<ChipGroup?>(R.id.chips_language) ?: return
        val chipKo = findViewById<Chip?>(R.id.chip_ko) ?: return
        val chipEn = findViewById<Chip?>(R.id.chip_en) ?: return

        val savedTag = appPrefs.getString("app_lang_tag", null)
        when (savedTag) {
            "en" -> chips.check(chipEn.id)
            "ko" -> chips.check(chipKo.id)
            else -> {
                if (resources.configuration.locales.get(0).language.startsWith("en")) chips.check(chipEn.id)
                else chips.check(chipKo.id)
            }
        }

        chipKo.setOnClickListener { setLanguage("ko", showToast = true) }
        chipEn.setOnClickListener { setLanguage("en", showToast = true) }
    }

    // 버튼 옆 코치마크(한 번만)
    private fun showLanguageCoachmarkIfNeeded() {
        if (appPrefs.getBoolean("coach_lang_done", false)) return
        val anchor = findViewById<View?>(R.id.chips_language) ?: return
        val container = findViewById<ViewGroup>(android.R.id.content)

        val tip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#BF000000"))
            }
            elevation = 10f
        }

        val icon = ImageView(this).apply { setImageResource(android.R.drawable.ic_dialog_info) }

        val resId = resources.getIdentifier("tutorial_lang_tip", "string", packageName)
        val tipText = if (resId != 0) getString(resId)
        else "언어는 여기에서 바꿀 수 있어요. 누르면 한국어/English 전환됩니다."

        val msg = TextView(this).apply {
            text = tipText
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(8), 0, dp(10), 0)
        }
        val ok = TextView(this).apply {
            text = getString(android.R.string.ok)
            setTextColor(Color.parseColor("#FDCA60"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setOnClickListener {
                container.removeView(tip)
                appPrefs.edit().putBoolean("coach_lang_done", true).apply()
            }
        }
        tip.addView(icon); tip.addView(msg); tip.addView(ok)

        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        container.addView(tip, lp)

        container.post {
            val anchorWin = IntArray(2)
            val contWin = IntArray(2)
            anchor.getLocationInWindow(anchorWin)
            container.getLocationInWindow(contWin)

            val x = (anchorWin[0] - contWin[0]) + anchor.width / 2f - tip.width / 2f
            val y = (anchorWin[1] - contWin[1]).toFloat() + anchor.height.toFloat() + dp(8).toFloat()
            tip.x = x; tip.y = y
        }

        tip.postDelayed({
            if (tip.parent != null) {
                container.removeView(tip)
                appPrefs.edit().putBoolean("coach_lang_done", true).apply()
            }
        }, 6000L)
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun setLanguage(tag: String, showToast: Boolean) {
        appPrefs.edit().putString("app_lang_tag", tag).apply()
        LocaleKit.apply(tag)
        if (showToast) {
            val resId = resources.getIdentifier("language_changed", "string", packageName)
            val msg = if (resId != 0) getString(resId) else "Language changed."
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        recreate()
    }
}
