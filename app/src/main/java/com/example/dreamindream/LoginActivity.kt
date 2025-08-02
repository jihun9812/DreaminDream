package com.example.dreamindream

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.ScaleAnimation
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class LoginActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var loginCardContent: FrameLayout

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
                    saveUserInfoToFirestore()
                    showCardLoadingAndDownloadHolidays()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "비로그인 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        loginCardContent = findViewById(R.id.loginCardContent)
        val loginPanel = findViewById<LinearLayout>(R.id.loginPanel)
        val signupPanel = findViewById<LinearLayout>(R.id.signupPanel)
        val findPwPanel = findViewById<LinearLayout>(R.id.findPwPanel)

        val btnSignupClose = findViewById<ImageButton>(R.id.btn_signup_close)
        val btnFindpwClose = findViewById<ImageButton>(R.id.btn_findpw_close)
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

        goSignup.applyPressEffect {
            loginPanel.visibility = View.GONE
            signupPanel.visibility = View.VISIBLE
        }

        goFindPw.applyPressEffect {
            loginPanel.visibility = View.GONE
            findPwPanel.visibility = View.VISIBLE
        }

        signupCancelBtn.applyPressEffect {
            signupPanel.visibility = View.GONE
            loginPanel.visibility = View.VISIBLE
        }

        findPwCancelBtn.applyPressEffect {
            findPwPanel.visibility = View.GONE
            loginPanel.visibility = View.VISIBLE
        }

        btnSignupClose.applyPressEffect {
            signupPanel.visibility = View.GONE
            loginPanel.visibility = View.VISIBLE
        }

        btnFindpwClose.applyPressEffect {
            findPwPanel.visibility = View.GONE
            loginPanel.visibility = View.VISIBLE
        }

        loginButton.applyPressEffect {
            val email = emailInput.text.toString().trim()
            val pw = passwordInput.text.toString()
            if (email.isBlank() || pw.isBlank()) {
                Toast.makeText(this, "이메일/비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show()
                return@applyPressEffect
            }
            auth.signInWithEmailAndPassword(email, pw)
                .addOnSuccessListener {
                    if (auth.currentUser?.isEmailVerified == true) {
                        saveUserInfoToFirestore()
                        showCardLoadingAndDownloadHolidays()
                    } else {
                        Toast.makeText(this, "이메일 인증 후 로그인하세요.", Toast.LENGTH_LONG).show()
                        auth.signOut()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "로그인 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        signupBtn.applyPressEffect {
            val email = signupEmail.text.toString().trim()
            val pw = signupPw.text.toString()
            val pw2 = signupPwConfirm.text.toString()
            if (email.isBlank() || pw.isBlank() || pw2.isBlank()) {
                Toast.makeText(this, "모든 항목을 입력하세요.", Toast.LENGTH_SHORT).show()
                return@applyPressEffect
            }
            if (pw != pw2) {
                Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show()
                return@applyPressEffect
            }
            auth.createUserWithEmailAndPassword(email, pw)
                .addOnSuccessListener {
                    auth.currentUser?.sendEmailVerification()?.addOnSuccessListener {
                        Toast.makeText(this, "회원가입 성공! 인증 메일이 발송되었습니다. 인증 후 로그인하세요.", Toast.LENGTH_LONG).show()
                        signupPanel.visibility = View.GONE
                        loginPanel.visibility = View.VISIBLE
                    }?.addOnFailureListener {
                        Toast.makeText(this, "이메일 인증 발송 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "회원가입 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        findPwBtn.applyPressEffect {
            val email = findPwEmail.text.toString().trim()
            if (email.isBlank()) {
                Toast.makeText(this, "이메일을 입력하세요.", Toast.LENGTH_SHORT).show()
                return@applyPressEffect
            }
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Toast.makeText(this, "비밀번호 재설정 메일을 보냈습니다.", Toast.LENGTH_LONG).show()
                    findPwPanel.visibility = View.GONE
                    loginPanel.visibility = View.VISIBLE
                }
                .addOnFailureListener {
                    Toast.makeText(this, "메일 발송 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        googleButton.applyPressEffect {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
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
                    showCardLoadingAndDownloadHolidays()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "구글 로그인 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: ApiException) {
            Toast.makeText(this, "구글 로그인 오류: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCardLoadingAndDownloadHolidays() {
        loginCardContent.removeAllViews()
        val loadingLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(40, 120, 40, 120)
        }
        val loadingBar = ProgressBar(this).apply { isIndeterminate = true }
        val loadingText = TextView(this).apply {
            text = "달력 데이터를 준비중입니다.\n잠시만 기다려주세요."
            textSize = 17f
            setTextColor(0xFF7A55D3.toInt())
            setPadding(0, 22, 0, 0)
            gravity = android.view.Gravity.CENTER
        }
        loadingLayout.addView(loadingBar)
        loadingLayout.addView(loadingText)
        loginCardContent.addView(loadingLayout)

        val targetYears = listOf(2025, 2026)
        val holidays = mutableListOf<Holiday>()
        var doneCount = 0
        targetYears.forEach { year ->
            HolidayApi.fetchHolidays(year, onSuccess = { list ->
                holidays.addAll(list)
                doneCount++
                if (doneCount == targetYears.size) {
                    HolidayStorage.saveHolidays(this, holidays)
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }, onError = {
                doneCount++
                if (doneCount == targetYears.size) {
                    HolidayStorage.saveHolidays(this, holidays)
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            })
        }
    }

    private fun saveUserInfoToFirestore() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(user.uid)

        val userData = mapOf(
            "email" to (if (user.isAnonymous) "guest" else user.email ?: "unknown"),
            "name" to (user.displayName ?: if (user.isAnonymous) "익명 사용자" else "익명"),
            "last_login" to System.currentTimeMillis()
        )

        userRef.set(userData, SetOptions.merge())
    }

    //  눌림 효과 애니메이션 함수
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
}
