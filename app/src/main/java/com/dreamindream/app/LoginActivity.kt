package com.dreamindream.app

import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
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
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.view.isVisible
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import android.view.MotionEvent
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import android.text.Editable
import android.text.TextWatcher

class LoginActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var loginCardContent: FrameLayout

    private val appPrefs by lazy { getSharedPreferences("app", MODE_PRIVATE) }

    private var lastBackPressedAt = 0L
    private val BACK_INTERVAL_MS = 1500L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        auth = FirebaseAuth.getInstance()

        // 타이틀 그라디언트
        findViewById<TextView>(R.id.login_title)?.let { tv ->
            tv.post { applyGradientTitle(tv) }
            tv.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
                if ((r - l) != (or_ - ol) || (b - t) != (ob - ot)) applyGradientTitle(tv)
            }
            tv.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) { applyGradientTitle(tv) }
            })
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val skipLoginButton = findViewById<Button>(R.id.btn_skip_login)
        skipLoginButton.applyPressEffect {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    markPendingHomeFade()
                    saveUserInfoToFirestore()
                    navigateToMain()
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
                            navigateToMain()
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

        // 언어 팝업(🌐 버튼)
        setupGlobeLanguageMenu()

        // 시스템 뒤로가기
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                currentFocus?.let { v ->
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(v.windowToken, 0)
                }
                when {
                    signupPanel.isVisible || findPwPanel.isVisible -> {
                        signupPanel.visibility = View.GONE
                        findPwPanel.visibility = View.GONE
                        loginPanel.visibility = View.VISIBLE
                    }
                    loginPanel.isVisible -> {
                        val now = System.currentTimeMillis()
                        if (now - lastBackPressedAt <= BACK_INTERVAL_MS) {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        } else {
                            lastBackPressedAt = now
                            Toast.makeText(
                                this@LoginActivity,
                                getString(R.string.press_back_again_to_exit),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        // (예전 드롭다운 초기화는 삭제)
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
                        navigateToMain()
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


    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        finish()
    }
    private fun setupLoginButtonEffects() {
        val scaleDown = 0.93f
        val duration = 80L

        // 눌림 효과 줄 버튼들 지정
        val buttons = listOf<View>(
            findViewById<Button>(R.id.btn_login),
            findViewById<Button>(R.id.btn_skip_login)
        )

        buttons.forEach { btn ->
            btn.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> v.animate()
                        .scaleX(scaleDown)
                        .scaleY(scaleDown)
                        .setDuration(duration)
                        .start()

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(duration)
                        .start()
                }
                false
            }
        }
    }
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
            ).apply { this.duration = duration; fillAfter = true }
            this.startAnimation(anim)
            this.postDelayed({ this.clearAnimation(); action() }, duration)
        }
    }

    /** 🌐 언어 팝업: 왼쪽 정렬 + 배경 D0BFBFBF + 텍스트 #222 안내 */
    private fun setupGlobeLanguageMenu() {
        val btn = findViewById<ImageButton?>(R.id.btn_lang) ?: return

        data class Lang(val label: String, val tag: String)
        val langs = listOf(
            Lang("한국어", "ko"),
            Lang("English", "en"),
            Lang("हिन्दी", "hi"),
            Lang("العربية", "ar"),
            Lang("中文", "zh")
        )
        val labels = langs.map { it.label }

        val popup = ListPopupWindow(this).apply {
            anchorView = btn
            width = dp(180)
            isModal = true
            // 배경색을 연회색(#D0BFBFBF)으로
// 변경 코드
            setBackgroundDrawable(
                android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.parseColor("#FBFBFBFF"))
                }
            )

            setAdapter(object : ArrayAdapter<String>(this@LoginActivity, android.R.layout.simple_list_item_1, labels) {
                private fun style(tv: TextView) {
                    tv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    tv.gravity = Gravity.START
                    tv.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    tv.textDirection = View.TEXT_DIRECTION_LTR
                    tv.setPadding(dp(14), dp(12), dp(14), dp(12))
                    // 항목 글자색: 진회색(배경과 대비)
                    tv.setTextColor(Color.parseColor("#222222"))
                }
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getView(position, convertView, parent) as TextView
                    style(v); return v
                }
                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getDropDownView(position, convertView, parent) as TextView
                    style(v); return v
                }
            })
            setOnItemClickListener { _, _, position, _ ->
                setLanguage(langs[position].tag, showToast = true)
                dismiss()
            }
        }

        btn.setOnClickListener { popup.show() }
    }

    // 코치마크 (그대로 유지)
    private fun showLanguageCoachmarkIfNeeded() {
        if (appPrefs.getBoolean("coach_lang_done", false)) return
        val anchor = findViewById<View?>(R.id.loginPanel) ?: return
        val container = findViewById<ViewGroup>(android.R.id.content)

        val tip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.parseColor("#BF000000"))
            elevation = 10f
        }

        val icon = ImageView(this).apply { setImageResource(android.R.drawable.ic_dialog_info) }
        val tipText = getString(R.string.tutorial_lang_tip)
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
        ).apply { gravity = Gravity.TOP or Gravity.END }
        container.addView(tip, lp)

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
        LocaleKit.apply(tag) // hi / ar / zh 지원
        if (showToast) {
            Toast.makeText(this, getString(R.string.language_changed), Toast.LENGTH_SHORT).show()
        }
        recreate()
    }

    private fun applyGradientTitle(tv: TextView) {
        val text = tv.text?.toString().orEmpty()
        if (text.isEmpty()) return
        tv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        val width = tv.paint.measureText(text)
        val shader = LinearGradient(
            0f, 0f, width, tv.textSize,
            intArrayOf(Color.parseColor("#F9B84A"), Color.parseColor("#7B61FF")),
            null,
            Shader.TileMode.CLAMP
        )
        tv.paint.shader = shader
        tv.invalidate()
    }
}
