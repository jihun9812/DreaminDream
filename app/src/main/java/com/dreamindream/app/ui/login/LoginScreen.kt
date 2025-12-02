package com.dreamindream.app.ui.login

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.airbnb.lottie.compose.*
import com.dreamindream.app.R
import com.dreamindream.app.LocaleKit
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Locale
import androidx.compose.ui.unit.DpOffset

private enum class LoginPanel { LOGIN, SIGNUP, FINDPW }

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {

    val ctx = LocalContext.current
    val inPreview = LocalInspectionMode.current

    // Firebase, DB
    val auth = if (!inPreview) FirebaseAuth.getInstance() else null
    val db = if (!inPreview) FirebaseFirestore.getInstance() else null

    val activity = ctx as? Activity
    val prefs = remember { ctx.getSharedPreferences("app", Context.MODE_PRIVATE) }

    // 상태들
    var panel by remember { mutableStateOf(LoginPanel.LOGIN) }

    var email by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }

    var signupEmail by remember { mutableStateOf("") }
    var signupPw by remember { mutableStateOf("") }
    var signupPwConfirm by remember { mutableStateOf("") }

    var findPwEmail by remember { mutableStateOf("") }

    // ─────────────────────────── GOOGLE LOGIN ───────────────────────────
    val googleLauncher =
        if (!inPreview && activity != null && auth != null && db != null) {
            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
                val task = GoogleSignIn.getSignedInAccountFromIntent(res.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                    auth.signInWithCredential(credential)
                        .addOnSuccessListener {
                            saveUserInfo(db, auth)
                            onLoginSuccess()
                        }
                        .addOnFailureListener {
                            toast(ctx, ctx.getString(R.string.toast_google_login_failed_fmt, it.message ?: "-"))
                        }
                } catch (e: ApiException) {
                    toast(ctx, ctx.getString(R.string.toast_google_login_error_fmt, e.message ?: "-"))
                }
            }
        } else null

    val googleClient =
        if (!inPreview && activity != null)
            GoogleSignIn.getClient(
                activity,
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(stringResource(R.string.default_web_client_id))
                    .requestEmail()
                    .build()
            )
        else null

    // ─────────────────────────── UI ───────────────────────────
    Box(Modifier.fillMaxSize()) {

        LoginBackground()  // Lottie + main_ground

        // 카드 컨테이너
        Card(
            modifier = Modifier
                .padding(32.dp)
                .align(Alignment.Center),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xB3282828))
        ) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                LangMenuButton { tag ->
                    setLanguage(ctx, prefs, tag, showToast = true)
                }

                when (panel) {

                    LoginPanel.LOGIN -> LoginPanelView(
                        email = email,
                        pw = pw,
                        onEmail = { email = it },
                        onPw = { pw = it },
                        onLogin = {
                            if (inPreview || auth == null || db == null) return@LoginPanelView

                            if (email.isBlank() || pw.isBlank()) {
                                toast(ctx, ctx.getString(R.string.toast_fill_email_password))
                                return@LoginPanelView

                            }

                            auth.signInWithEmailAndPassword(email.trim(), pw.trim())
                                .addOnSuccessListener {
                                    if (auth.currentUser?.isEmailVerified == true) {
                                        saveUserInfo(db, auth)
                                        onLoginSuccess()
                                    } else {
                                        toast(ctx, ctx.getString(R.string.toast_email_verify_first))
                                        auth.signOut()
                                    }
                                }
                                .addOnFailureListener {
                                    toast(ctx, ctx.getString(R.string.toast_login_failed_fmt, it.message ?: "-"))
                                }
                        },
                        onFindPw = { panel = LoginPanel.FINDPW },
                        onSignup = { panel = LoginPanel.SIGNUP },
                        onGoogle = {
                            if (googleLauncher != null && googleClient != null) {
                                googleLauncher.launch(googleClient.signInIntent)
                            }
                        },
                        onGuest = {
                            if (inPreview || auth == null || db == null) return@LoginPanelView
                            auth.signInAnonymously()
                                .addOnSuccessListener {
                                    saveUserInfo(db, auth)
                                    onLoginSuccess()
                                }
                                .addOnFailureListener {
                                    toast(ctx, ctx.getString(R.string.toast_anonymous_failed_fmt, it.message ?: "-"))
                                }
                        }
                    )

                    LoginPanel.SIGNUP -> SignupPanelView(
                        email = signupEmail,
                        pw = signupPw,
                        pwConfirm = signupPwConfirm,
                        onEmail = { signupEmail = it },
                        onPw = { signupPw = it },
                        onPwConfirm = { signupPwConfirm = it },
                        onSignup = {
                            if (inPreview || auth == null) return@SignupPanelView
                            if (signupEmail.isBlank() || signupPw.isBlank() || signupPwConfirm.isBlank()) {
                                toast(ctx, ctx.getString(R.string.toast_fill_email_password))
                                return@SignupPanelView
                            }
                            if (signupPw != signupPwConfirm) {
                                toast(ctx, ctx.getString(R.string.toast_password_mismatch)) // 리소스는 없지만 임시 메시지
                                return@SignupPanelView
                            }

                            auth.createUserWithEmailAndPassword(signupEmail.trim(), signupPw.trim())
                                .addOnSuccessListener {
                                    auth.currentUser?.sendEmailVerification()
                                    toast(ctx, ctx.getString(R.string.toast_email_verify_first))
                                    panel = LoginPanel.LOGIN
                                }
                                .addOnFailureListener {
                                    toast(ctx, it.message ?: "-")
                                }
                        },
                        onCancel = { panel = LoginPanel.LOGIN }
                    )

                    LoginPanel.FINDPW -> FindPwPanelView(
                        email = findPwEmail,
                        onEmail = { findPwEmail = it },
                        onSend = {
                            if (inPreview || auth == null) return@FindPwPanelView
                            if (findPwEmail.isBlank()) {
                                toast(ctx, ctx.getString(R.string.toast_enter_email))
                                return@FindPwPanelView
                            }
                            auth.sendPasswordResetEmail(findPwEmail.trim())
                                .addOnSuccessListener {
                                    toast(ctx, ctx.getString(R.string.toast_reset_sent))
                                    panel = LoginPanel.LOGIN
                                }
                                .addOnFailureListener {
                                    toast(ctx, ctx.getString(R.string.toast_reset_failed_fmt, it.message ?: "-"))
                                }
                        },
                        onCancel = { panel = LoginPanel.LOGIN }
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.privacy_and_version),
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily(Font(R.font.pretendard_bold)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}


@Composable
private fun LoginBackground() {
    Image(
        painter = painterResource(R.drawable.main_ground),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )

    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.just_flow_teal)
    )

    LottieAnimation(
        composition = composition,
        iterations = Int.MAX_VALUE,
        modifier = Modifier.fillMaxSize()
    )
}


@Composable
private fun LoginPanelView(
    email: String,
    pw: String,
    onEmail: (String) -> Unit,
    onPw: (String) -> Unit,
    onLogin: () -> Unit,
    onFindPw: () -> Unit,
    onSignup: () -> Unit,
    onGoogle: () -> Unit,
    onGuest: () -> Unit
) {
    GradientTitle(text = stringResource(R.string.home_title))

    Text(
        text = stringResource(R.string.login_slogan),
        fontSize = 14.sp,
        fontFamily = FontFamily(Font(R.font.pretendard_medium)),
        color = Color(0xFFF8F7F7),
        modifier = Modifier.padding(bottom = 20.dp),
        textAlign = TextAlign.Center
    )

    CustomEditText(
        value = email,
        hint = stringResource(R.string.login_email_hint),
        onValueChange = onEmail
    )
    Spacer(Modifier.height(10.dp))

    CustomEditText(
        value = pw,
        hint = stringResource(R.string.login_password_hint),
        onValueChange = onPw,
        isPassword = true
    )
    Spacer(Modifier.height(12.dp))

    LoginButton(
        text = stringResource(R.string.login_btn_login),
        width = 100.dp,
        height = 39.dp,
        bgColor = Color(0x00A8A7A7),
        textColor = Color(0xFFFDC450),
        onClick = onLogin
    )

    Spacer(Modifier.height(16.dp)) // 간격 넓힘 (수정)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically // 중앙 정렬
    ) {
        Text(
            text = stringResource(R.string.forgot_password),
            color = Color(0xFFEDEDED),
            fontSize = 14.sp, // 폰트 크기 조정 (수정)
            modifier = Modifier
                .clickable { onFindPw() }
                .padding(end = 6.dp) // 간격 추가 (수정)
        )
        Text(
            " / ",
            color = Color(0xFFC7C7C7),
            fontSize = 15.sp, // 폰트 크기 조정 (수정)
            modifier = Modifier.padding(horizontal = 4.dp) // 좌우 간격 추가 (수정)
        )
        Text(
            text = stringResource(R.string.sign_up),
            color = Color(0xFFEDEDED),
            fontSize = 14.sp, // 폰트 크기 조정 (수정)
            modifier = Modifier
                .clickable { onSignup() }
                .padding(start = 6.dp) // 간격 추가 (수정)
        )
    }

    Spacer(Modifier.height(24.dp)) // 간격 넓힘 (수정)

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Divider(Modifier.weight(1f), color = Color(0xFFEDEDED))
        Text(
            "or",
            color = Color(0xFFEDEDED),
            fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
        Divider(Modifier.weight(1f), color = Color(0xFFEDEDED))
    }

    Spacer(Modifier.height(10.dp))

    Image(
        painterResource(R.drawable.btn_google),
        contentDescription = stringResource(R.string.login_btn_google),
        modifier = Modifier
            .width(189.dp)
            .height(50.dp)
            .clickable { onGoogle() }
    )

    Spacer(Modifier.height(10.dp))

    LoginButton(
        text = stringResource(R.string.login_btn_guest),
        width = 80.dp,
        height = 40.dp,
        bgColor = Color(0x00424141),
        textColor = Color(0xFFFDC450),
        onClick = onGuest
    )
}

@Composable
private fun SignupPanelView(
    email: String,
    pw: String,
    pwConfirm: String,
    onEmail: (String) -> Unit,
    onPw: (String) -> Unit,
    onPwConfirm: (String) -> Unit,
    onSignup: () -> Unit,
    onCancel: () -> Unit
) {
    Text(
        stringResource(R.string.sign_up_title),
        color = Color(0xFFFABE41),
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 18.dp)
    )

    CustomEditText(
        value = email,
        hint = stringResource(R.string.email_hint),
        onValueChange = onEmail,
        height = 43.dp
    )
    Spacer(Modifier.height(10.dp))

    CustomEditText(
        value = pw,
        hint = stringResource(R.string.password_hint),
        onValueChange = onPw,
        isPassword = true,
        height = 43.dp
    )
    Spacer(Modifier.height(10.dp))

    CustomEditText(
        value = pwConfirm,
        hint = stringResource(R.string.password_confirm_hint),
        onValueChange = onPwConfirm,
        isPassword = true,
        height = 43.dp
    )
    Spacer(Modifier.height(16.dp))

    FullWidthButton(
        text = stringResource(R.string.sign_up_button),
        bg = Color(0xFFFABE41),
        color = Color.White,
        onClick = onSignup
    )
    Spacer(Modifier.height(6.dp))

    FullWidthButton(
        text = stringResource(R.string.cancel),
        bg = Color(0xFFEEEEEE),
        color = Color(0xFF431D56),
        onClick = onCancel
    )
}

@Composable
private fun FindPwPanelView(
    email: String,
    onEmail: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    Text(
        stringResource(R.string.password_reset_title),
        color = Color(0xFFFABE41),
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 18.dp)
    )

    CustomEditText(
        value = email,
        hint = stringResource(R.string.email_hint),
        onValueChange = onEmail,
        height = 43.dp
    )
    Spacer(Modifier.height(14.dp))

    FullWidthButton(
        text = stringResource(R.string.send_reset_email),
        bg = Color(0xFFFABE41),
        color = Color.White,
        onClick = onSend
    )
    Spacer(Modifier.height(6.dp))

    FullWidthButton(
        text = stringResource(R.string.cancel),
        bg = Color(0xFFEEEEEE),
        color = Color(0xFF431D56),
        onClick = onCancel
    )
}

/**
 * !!! 입력 지연 문제 해결을 위해 AndroidView 대신 순수 Compose를 사용하도록 리팩토링 !!!
 * 배경의 blur 효과는 Compose에서 재현하기 어려워 일단 단색 배경으로 대체했습니다.
 */
@Composable
private fun CustomEditText(
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
    height: Dp = 40.dp
) {
    // 배경을 위한 Box (기존 edittext_bg_blur 대신 임시 배경 사용)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(
                color = Color(0xFFEBEBEB), // 밝은 회색 임시 배경
                shape = RoundedCornerShape(20.dp) // 버튼과 유사한 라운딩
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // 힌트 텍스트 (값 비어있을 때만 표시)
        if (value.isEmpty()) {
            Text(
                text = hint,
                color = Color(0xF7626262),
                fontSize = 16.sp,
                fontFamily = FontFamily(Font(R.font.pretendard_medium))
            )
        }

        // 실제 입력 필드
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 16.sp,
                color = Color.Black,
                fontFamily = FontFamily(Font(R.font.pretendard_medium))
            ),
            visualTransformation =
                if (isPassword) PasswordVisualTransformation()
                else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Email
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}


@Composable
private fun LoginButton(
    text: String,
    width: Dp,
    height: Dp,
    bgColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .width(width)
            .height(height)
            .background(bgColor, RoundedCornerShape(20.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = textColor,
            fontFamily = FontFamily(Font(R.font.pretendard_bold)),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun FullWidthButton(
    text: String,
    bg: Color,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(43.dp)
            .background(bg, RoundedCornerShape(20.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = color, fontSize = 14.sp)
    }
}

@Composable
private fun GradientTitle(text: String) {
    Text(
        text,
        fontSize = 27.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily(Font(R.font.jalnan)),
        style = TextStyle(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFF9B84A),
                    Color(0xFF7B61FF)
                )
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun LangMenuButton(onSelectLang: (String) -> Unit) {

    var expanded by remember { mutableStateOf(false) }

    val langs = listOf(
        "한국어" to "ko",
        "English" to "en",
        "हिन्दी" to "hi",
        "العربية" to "ar",
        "中文" to "zh"
    )

    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Box {  // ✅ Box로 감싸기
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_globe_24),
                    contentDescription = stringResource(R.string.language),
                    tint = Color.Unspecified
                )
            }

            // 드롭다운 메뉴
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                offset = DpOffset(0.dp, 0.dp)  // ✅ offset 추가 (필요시 조정)
            ) {
                langs.forEach { (label, tag) ->
                    DropdownMenuItem(
                        text = { Text(label, color = Color(0xFF222222)) },
                        onClick = {
                            expanded = false
                            onSelectLang(tag)
                        }
                    )
                }
            }
        }
    }
}

private fun setLanguage(ctx: Context, prefs: android.content.SharedPreferences, tag: String, showToast: Boolean) {

    prefs.edit().putString("app_lang_tag", tag).apply()
    LocaleKit.apply(tag)

    if (showToast) {
        val baseConfig = ctx.resources.configuration
        val newConfig = Configuration(baseConfig)
        val locale = Locale.forLanguageTag(tag)
        newConfig.setLocale(locale)

        val localized = ctx.createConfigurationContext(newConfig)
        Toast.makeText(localized, localized.getString(R.string.language_changed), Toast.LENGTH_LONG).show()
    }

    (ctx as? Activity)?.recreate()
}


private fun saveUserInfo(db: FirebaseFirestore, auth: FirebaseAuth) {
    val user = auth.currentUser ?: return
    val data = mapOf(
        "email" to (if (user.isAnonymous) "guest" else user.email ?: "unknown"),
        "name" to (user.displayName ?: "User"),
        "last_login" to System.currentTimeMillis()
    )
    db.collection("users").document(user.uid).set(data, SetOptions.merge())
}


private fun toast(ctx: Context, msg: String) {
    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}

@Preview(showBackground = true)
@Composable
private fun PreviewLogin() {
    LoginScreen(onLoginSuccess = {})
}