package com.dreamindream.app

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FeedbackActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 전역 Edge‑to‑Edge + 시스템 바 투명 처리
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            MaterialTheme {
                FeedbackScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

// Pretendard 폰트 – SettingsScreen 과 동일 컨셉
private val PretendardBold = FontFamily(
    Font(R.font.pretendard_bold, FontWeight.Bold)
)
private val PretendardMedium = FontFamily(
    Font(R.font.pretendard_medium, FontWeight.Medium)
)

// 공통 색
private val AccentLavender = Color(0xFFB388FF)
private val FieldContainer = Color(0x22FFFFFF)
private val FieldStroke = Color(0x66FFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    onBack: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var title by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }

    // Dialog 상태
    var dialogState by remember {
        mutableStateOf<FeedbackDialogState?>(null)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.feedbacktitle),
                        color = Color.White,
                        fontFamily = PretendardBold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "close",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // 배경 이미지 (main_ground 그대로)
            Image(
                painter = painterResource(R.drawable.main_ground),
                contentDescription = null,
                modifier = Modifier.matchParentSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 설명 텍스트 (위에 한 줄 안내)
                Text(
                    text = stringResource(R.string.feedback_contents),
                    color = Color(0xCCFFFFFF),
                    fontFamily = PretendardMedium,
                    fontSize = 13.sp
                )

                // 제목
                AnimatedFeedbackTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text(
                            text = stringResource(R.string.title_hint),
                            fontFamily = PretendardMedium
                        )
                    }
                )

                // 내용
                AnimatedFeedbackTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                    label = {
                        Text(
                            text = stringResource(R.string.feedback_contents),
                            fontFamily = PretendardMedium
                        )
                    },
                    singleLine = false
                )

                Spacer(Modifier.height(4.dp))

                // 보내기 버튼 (기존 그라데이션 느낌)
                Button(
                    onClick = {
                        if (title.isBlank()) {
                            dialogState = FeedbackDialogState.Error(
                                ctx.getString(R.string.fb_title_need)
                            )
                            return@Button
                        }
                        if (message.isBlank()) {
                            dialogState = FeedbackDialogState.Error(
                                ctx.getString(R.string.fb_msg_need)
                            )
                            return@Button
                        }

                        scope.launch {
                            isSending = true
                            try {
                                val auth = FirebaseAuth.getInstance()
                                if (auth.currentUser == null) {
                                    auth.signInAnonymously().await()
                                }

                                val now = System.currentTimeMillis()
                                val datePattern =
                                    ctx.getString(R.string.fb_date_format) // "yyyy-MM-dd HH:mm:ss"
                                val dateStr = SimpleDateFormat(
                                    datePattern,
                                    Locale.getDefault()
                                ).format(Date(now))

                                val appName = ctx.getString(R.string.app_name)
                                val userId = auth.currentUser?.uid ?: "guest"
                                val installId = Settings.Secure.getString(
                                    ctx.contentResolver,
                                    Settings.Secure.ANDROID_ID
                                )
                                val device =
                                    "${Build.MANUFACTURER} ${Build.MODEL}"
                                val os =
                                    "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
                                val appVersion = BuildConfig.VERSION_NAME

                                val data = hashMapOf(
                                    "createdAt" to now,
                                    "createdAtStr" to dateStr,
                                    "title" to title,
                                    "message" to message,
                                    "userId" to userId,
                                    "installId" to installId,
                                    "app" to appName,
                                    "status" to "new",
                                    "device" to device,
                                    "os" to os,
                                    "appVersion" to appVersion,
                                    "info" to mapOf(
                                        "appVersion" to appVersion,
                                        "os" to "Android ${Build.VERSION.RELEASE}",
                                        "sdk" to Build.VERSION.SDK_INT,
                                        "device" to device,
                                        "userId" to userId,
                                        "installId" to installId
                                    )
                                )

                                withTimeout(10_000L) {
                                    FirebaseFirestore
                                        .getInstance()
                                        .collection("feedback")
                                        .add(data)
                                        .await()
                                }

                                dialogState = FeedbackDialogState.Success(
                                    title = ctx.getString(R.string.fb_sent_title),
                                    message = ctx.getString(R.string.fb_sent_body)
                                )
                            } catch (e: Exception) {
                                val failTitle =
                                    ctx.getString(R.string.fb_send_fail_title)
                                val detail = e.message ?: e.toString()
                                val fullMsg = ctx.getString(
                                    R.string.fb_send_fail_fmt,
                                    detail
                                )
                                dialogState =
                                    FeedbackDialogState.Error("$failTitle\n\n$fullMsg")
                            } finally {
                                isSending = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = !isSending,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = Color.Black
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 그라데이션 배경 (대기업 감성 버튼)
                        androidx.compose.foundation.Canvas(
                            modifier = Modifier.matchParentSize()
                        ) {
                            drawRoundRect(
                                brush = Brush.linearGradient(
                                    listOf(
                                        Color(0xFFFEDCA6),
                                        Color(0xFF8BAAFF)
                                    )
                                ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                    12.dp.toPx()
                                )
                            )
                        }

                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.send),
                                fontFamily = PretendardBold,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 다이얼로그
            dialogState?.let { state ->
                when (state) {
                    is FeedbackDialogState.Success -> {
                        AlertDialog(
                            onDismissRequest = {
                                dialogState = null
                                onBack()
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    dialogState = null
                                    onBack()
                                }) {
                                    Text(
                                        text = stringResource(R.string.common_ok),
                                        fontFamily = PretendardMedium
                                    )
                                }
                            },
                            title = {
                                Text(
                                    text = state.title,
                                    fontFamily = PretendardBold
                                )
                            },
                            text = {
                                Text(
                                    text = state.message,
                                    fontFamily = PretendardMedium
                                )
                            }
                        )
                    }

                    is FeedbackDialogState.Error -> {
                        AlertDialog(
                            onDismissRequest = { dialogState = null },
                            confirmButton = {
                                TextButton(onClick = { dialogState = null }) {
                                    Text(
                                        text = stringResource(R.string.common_ok),
                                        fontFamily = PretendardMedium
                                    )
                                }
                            },
                            title = {
                                Text(
                                    text = stringResource(R.string.common_notice),
                                    fontFamily = PretendardBold
                                )
                            },
                            text = {
                                Text(
                                    text = state.message,
                                    fontFamily = PretendardMedium
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimatedFeedbackTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Color.White else FieldStroke,
        label = "fb_border_color"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) 1.dp else 2.dp,
        label = "fb_border_width"
    )

    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth(),
            singleLine = singleLine,
            label = label,
            textStyle = LocalTextStyle.current.copy(
                fontFamily = PretendardMedium,
                color = Color.White
            ),
            interactionSource = interactionSource,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                focusedContainerColor = FieldContainer,
                unfocusedContainerColor = FieldContainer,
                disabledContainerColor = FieldContainer,
                cursorColor = AccentLavender,
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color(0xB3FFFFFF),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

private sealed class FeedbackDialogState {
    data class Success(val title: String, val message: String) : FeedbackDialogState()
    data class Error(val message: String) : FeedbackDialogState()
}
