package com.dreamindream.app.ui.settings

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dreamindream.app.AdPageScaffold
import com.dreamindream.app.FeedbackActivity
import com.dreamindream.app.R
import com.dreamindream.app.TermsActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import java.text.SimpleDateFormat
import java.util.*

// --- Premium Colors ---
private val DeepNavy = Color(0xFF121626)
private val GlassWhite = Color(0x1AFFFFFF)
private val TextWhite = Color(0xFFEEEEEE)
private val TextGray = Color(0xFFB0BEC5)
private val AccentGold = Color(0xFFFFD54F)
private val AccentPurple = Color(0xFFB39DDB)
private val InputBg = Color(0x22FFFFFF)

@Composable
fun SettingsScreen(
    vm: SettingsViewModel = viewModel(),
    onNavigateToSubscription: () -> Unit,
    onLogout: () -> Unit
) {
    val uiState by vm.ui.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    // --- State: 게스트 경고 다이얼로그 ---
    var showGuestWarningDialog by remember { mutableStateOf(false) }
    // --- State: 로그아웃 확인 다이얼로그 (NEW) ---
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Toast Handler
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.toastShown()
        }
    }

    // Google Login Launcher
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            account?.idToken?.let { token ->
                vm.linkGoogleWithIdToken(token) { msg -> vm.handleGoogleError(msg) }
            }
        } catch (e: Exception) {
            vm.handleGoogleError("Google Sign-In Failed: ${e.message}")
        }
    }

    // 실제 구글 로그인 프로세스 시작 함수
    val processGoogleSignIn = {
        if (activity != null) {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            val client = GoogleSignIn.getClient(activity, gso)
            vm.startLinkGoogle()
            client.signOut().addOnCompleteListener {
                googleLauncher.launch(client.signInIntent)
            }
        }
    }

    // 버튼 클릭 시 게스트 여부 확인 로직
    val onGoogleConnectClick = {
        if (uiState.isGuest) {
            showGuestWarningDialog = true
        } else {
            processGoogleSignIn()
        }
    }

    AdPageScaffold(adUnitRes = R.string.ad_unit_settings_banner) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepNavy)
        ) {
            // Background Image
            Image(
                painter = painterResource(R.drawable.main_ground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.5f
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Animated Content (View vs Edit Mode)
                AnimatedContent(
                    targetState = uiState.isEditMode,
                    label = "ProfileMode"
                ) { isEdit ->
                    if (isEdit) {
                        EditProfileView(
                            uiState = uiState,
                            onSave = vm::saveProfile,
                            onCancel = vm::toggleEditMode
                        )
                    } else {
                        ViewProfileView(
                            uiState = uiState,
                            onEditClick = vm::toggleEditMode,
                            onPremiumClick = onNavigateToSubscription,
                            onGoogleClick = onGoogleConnectClick,
                            onLogoutClick = {
                                // 바로 로그아웃 하지 않고 다이얼로그 띄움
                                showLogoutDialog = true
                            },
                            onTestPremiumToggle = vm::setTestPremium,
                            onContactClick = {
                                activity?.startActivity(Intent(context, FeedbackActivity::class.java))
                            },
                            onTermsClick = {
                                activity?.startActivity(Intent(context, TermsActivity::class.java))
                            }
                        )
                    }
                }

                Spacer(Modifier.height(50.dp))
            }

            // ⚠️ 1. 로그아웃 확인 다이얼로그 추가
            if (showLogoutDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoutDialog = false },
                    title = {
                        Text(
                            text = stringResource(R.string.logout_dialog_title),
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.logout_dialog_message),
                            color = TextGray
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showLogoutDialog = false
                            vm.logout()
                            onLogout()
                        }) {
                            Text(stringResource(R.string.btn_logout), color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLogoutDialog = false }) {
                            Text(stringResource(R.string.btn_cancel), color = TextGray)
                        }
                    },
                    containerColor = Color(0xFF1E212B)
                )
            }

            // ⚠️ 2. 게스트 데이터 경고 다이얼로그
            if (showGuestWarningDialog) {
                AlertDialog(
                    onDismissRequest = { showGuestWarningDialog = false },
                    title = {
                        Text(
                            text = stringResource(id = R.string.data_warning_title),
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                    },

                    text = {
                        Text(
                            text = stringResource(R.string.guest_warning_message),
                            color = TextGray
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showGuestWarningDialog = false
                            processGoogleSignIn()
                        }) {
                            Text(stringResource(R.string.btn_confirm), color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showGuestWarningDialog = false }) {
                            Text(stringResource(R.string.btn_cancel), color = TextGray)
                        }
                    },
                    containerColor = Color(0xFF1E212B)
                )
            }

            // Loading Overlay
            if (uiState.isLoading || uiState.saving || uiState.linkInProgress) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.5f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentGold)
                }
            }
        }
    }
}

// ==========================================
// View Mode Components
// ==========================================

@Composable
fun ViewProfileView(
    uiState: SettingsUiState,
    onEditClick: () -> Unit,
    onPremiumClick: () -> Unit,
    onGoogleClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onTestPremiumToggle: (Boolean) -> Unit,
    onContactClick: () -> Unit,
    onTermsClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // 1. Profile Card
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(AccentPurple, Color(0xFF7E57C2)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = uiState.zodiacAnimal.ifBlank { "👤" }, fontSize = 28.sp)
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = uiState.nickname.ifBlank { "Guest" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextWhite
                        )
                        if (uiState.isPremium) {
                            Spacer(Modifier.width(8.dp))
                            Box(contentAlignment = Alignment.Center) {
                                AnimatedCrescentIcon(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                    Text(
                        text = uiState.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                }

                IconButton(onClick = onEditClick) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = AccentGold)
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E212B), RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = stringResource(R.string.dream_count), color = TextGray, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.dream_logs_count, uiState.dreamTotalCount),
                        color = Color.White
                    )
                }
                Box(modifier = Modifier.width(1.dp).height(30.dp).background(Color.White.copy(alpha = 0.1f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = stringResource(R.string.gpt_usage_today), color = TextGray, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.interpretations_today, uiState.gptUsedToday),
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(Modifier.height(16.dp))

            // ⚠️ 한글화 적용 (나이, 별자리, MBTI)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                StatItem(stringResource(R.string.profile_age), if(uiState.age > 0) "${uiState.age}" else "-")
                StatItem(stringResource(R.string.profile_zodiac), uiState.zodiacSign.ifBlank { "-" })
                StatItem(stringResource(R.string.profile_mbti), uiState.mbti.ifBlank { "-" }) // MBTI는 영어 유지
            }
        }

        // 2. Settings Menu
        Text("General", color = AccentGold, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))

        GlassCard {
            SettingsTile(
                title = stringResource(R.string.btn_premium),
                subtitle = "Remove ads & Unlock unlimited AI",
                customIcon = { Icon(Icons.Default.Star, null, tint = AccentGold) },
                onClick = onPremiumClick,
                color = AccentGold
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 4.dp))
            SettingsTile(
                title = uiState.googleButtonLabel,
                subtitle = uiState.accountProviderLabel,
                icon = painterResource(R.drawable.google_logo),
                onClick = onGoogleClick,
                enabled = uiState.googleButtonEnabled
            )
        }

        // 3. Support & Legal
        Text("Support", color = AccentGold, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))

        GlassCard {
            SettingsTile(
                title = stringResource(R.string.btn_contact),
                iconVector = Icons.Default.Email,
                onClick = onContactClick
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 4.dp))
            SettingsTile(
                title = stringResource(R.string.btn_terms),
                iconVector = Icons.Default.Info,
                onClick = onTermsClick
            )
        }

        // 4. Logout & Dev
        GlassCard {
            SettingsTile(
                title = stringResource(R.string.btn_logout),
                iconVector = Icons.Default.Logout,
                onClick = onLogoutClick,
                color = Color(0xFFFF8A80)
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Developer: Force Premium", color = TextGray, fontSize = 12.sp)
                Switch(
                    checked = uiState.isPremium,
                    onCheckedChange = onTestPremiumToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentGold,
                        uncheckedThumbColor = TextGray,
                        uncheckedTrackColor = Color.Black
                    ),
                    modifier = Modifier.scale(0.8f)
                )
            }
        }
    }
}

// ... (StatItem, SettingsTile)

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextGray, fontSize = 12.sp)
        Text(value, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun SettingsTile(
    title: String,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.painter.Painter? = null,
    iconVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    customIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    color: Color = TextWhite,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (customIcon != null) {
                customIcon()
            } else if (icon != null) {
                Image(painter = icon, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else if (iconVector != null) {
                Icon(imageVector = iconVector, contentDescription = null, tint = color)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = if(enabled) color else TextGray, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, color = TextGray, fontSize = 12.sp)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextGray.copy(alpha = 0.5f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileView(
    uiState: SettingsUiState,
    onSave: (String, String, String, String, String) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(uiState.nickname) }
    var birth by remember { mutableStateOf(uiState.birthIso) }
    var gender by remember { mutableStateOf(uiState.gender) }
    var mbti by remember { mutableStateOf(uiState.mbti) }
    var timeCode by remember { mutableStateOf(uiState.birthTimeCode) }
    var showDatePicker by remember { mutableStateOf(false) }

    GlassCard {
        // ⚠️ 한글화 적용
        Text(stringResource(R.string.edit_profile_title), color = AccentGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(20.dp))
        PremiumTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.label_nickname))
        Spacer(Modifier.height(12.dp))
        Box {
            PremiumTextField(value = birth, onValueChange = {}, label = stringResource(R.string.label_birthdate), readOnly = true, trailingIcon = Icons.Default.CalendarMonth)
            Box(modifier = Modifier.matchParentSize().clickable { showDatePicker = true })
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.label_gender), color = TextGray, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // 성별 값 자체는 서버에 "Male"/"Female"로 보낼 수 있으므로 저장 값은 영어로, 표시는 한글로
            PremiumChip(stringResource(R.string.gender_male), gender == "Male") { gender = "Male" }
            PremiumChip(stringResource(R.string.gender_female), gender == "Female") { gender = "Female" }
        }
        Spacer(Modifier.height(16.dp))
        PremiumTextField(value = mbti, onValueChange = { mbti = it.uppercase() }, label = stringResource(R.string.label_mbti)) // MBTI 유지
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.btn_cancel), color = TextGray) }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onSave(name, birth, gender, mbti, timeCode) }, colors = ButtonDefaults.buttonColors(containerColor = AccentPurple)) {
                Text(stringResource(R.string.btn_save), color = DeepNavy, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        birth = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it))
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.btn_confirm), color = AccentGold) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.btn_cancel), color = TextGray) }
            },
            colors = DatePickerDefaults.colors(containerColor = Color(0xFF1E1E1E))
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = AccentGold,
                    headlineContentColor = TextWhite,
                    dayContentColor = TextWhite,
                    selectedDayContainerColor = AccentGold,
                    selectedDayContentColor = DeepNavy,
                    todayContentColor = AccentGold,
                    todayDateBorderColor = AccentGold
                )
            )
        }
    }
}

@Composable
fun PremiumChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if(selected) AccentPurple else InputBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(text, color = if(selected) DeepNavy else TextGray, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumTextField(value: String, onValueChange: (String) -> Unit, label: String, readOnly: Boolean = false, trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label, color = TextGray) },
        readOnly = readOnly, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextWhite, unfocusedTextColor = TextWhite,
            focusedContainerColor = InputBg, unfocusedContainerColor = InputBg,
            focusedBorderColor = AccentGold, unfocusedBorderColor = Color.Transparent
        ),
        trailingIcon = if (trailingIcon != null) { { Icon(trailingIcon, contentDescription = null, tint = TextGray) } } else null
    )
}

@Composable
fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GlassWhite)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Column(content = content)
    }
}