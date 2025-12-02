package com.dreamindream.app.ui.settings

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dreamindream.app.AdPageScaffold
import com.dreamindream.app.FeedbackActivity
import com.dreamindream.app.R
import com.dreamindream.app.TermsActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import java.text.SimpleDateFormat
import java.util.*

private val PretendardBold =
    FontFamily(Font(R.font.pretendard_bold, FontWeight.Bold))

private val PretendardMedium =
    FontFamily(Font(R.font.pretendard_medium, FontWeight.Medium))

// 공통 컬러들
private val CardBackground = Color(0xE611141D)
private val CardStroke = Color(0x33FFFFFF)
private val FieldContainer = Color(0x22FFFFFF)
private val FieldBorder = Color(0x66FFFFFF)          // 기본 회색 라인
private val FieldBorderFocused = Color(0xFFFFFFFF)   // 포커스 시 흰색
private val FieldLabelUnfocused = Color(0xB3FFFFFF)  // 라벨 기본
private val FieldLabelFocused = Color(0xFFFFFFFF)    // 포커스 라벨
private val AccentLavender = Color(0xFFB388FF)

// DatePicker용 컬러
private val DatePickerBackground = Color(0xFFEDE7F6)   // 연보라
private val DatePickerTextColor = Color(0xFF000000)    // 검정 글씨

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val focusManager = LocalFocusManager.current

    // Google Sign-In launcher
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        vm.endLinkGoogle()
        val task = GoogleSignIn.getSignedInAccountFromIntent(res.data)
        runCatching {
            task.getResult(com.google.android.gms.common.api.ApiException::class.java)
        }.onSuccess { acc ->
            acc?.idToken?.let {
                vm.linkGoogleWithIdToken(it) { msg ->
                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }.onFailure {
            Toast.makeText(
                ctx,
                ctx.getString(R.string.toast_error_with_reason, it.localizedMessage ?: "-"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 토스트 처리
    ui.toast?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }

    var showPremiumDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    AdPageScaffold(
        adUnitRes = R.string.ad_unit_settings_banner) { pad ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                // 아무 데나 탭하면 포커스 해제 → 키보드 내려감
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusManager.clearFocus()
                }
        ) {
            // 메인 배경 (main_ground 그대로 사용)
            Image(
                painter = painterResource(R.drawable.main_ground),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 상단 타이틀
                Text(
                    text = stringResource(R.string.settings_title),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = PretendardBold,
                    fontWeight = FontWeight.Bold
                )

                // 고정 카드 영역 (안쪽만 스크롤)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    GlassCard(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val scrollState = rememberScrollState()

                        Crossfade(
                            targetState = ui.isEditMode,
                            label = "settings_mode_crossfade"
                        ) { isEdit ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                            ) {
                                if (!isEdit) {
                                    // ===== APP 모드 =====
                                    ProfileHeader(ui)
                                    Spacer(Modifier.height(10.dp))
                                    QuickSummary(ui)
                                    Spacer(Modifier.height(10.dp))
                                    QuickStats(ui)
                                    Spacer(Modifier.height(16.dp))

                                    Text(
                                        text = stringResource(R.string.settings_section),
                                        color = Color(0xFFEDDF90),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = PretendardMedium
                                    )

                                    Spacer(Modifier.height(4.dp))

                                    SettingsButtons(
                                        onEdit = {
                                            vm.loadFromPrefs()
                                            vm.setEditMode(true)
                                        },
                                        onPremium = { showPremiumDialog = true },
                                        onContact = {
                                            activity?.startActivity(
                                                Intent(ctx, FeedbackActivity::class.java)
                                            )
                                        },
                                        onTerms = {
                                            activity?.startActivity(
                                                Intent(ctx, TermsActivity::class.java)
                                            )
                                        }
                                    )

                                    Spacer(Modifier.height(12.dp))
                                    HorizontalDivider(color = Color(0x334A4B4B))
                                    Spacer(Modifier.height(8.dp))

                                    // 구글 계정 연동 섹션
                                    AccountLinkSection(
                                        ui = ui,
                                        onLinkGoogle = {
                                            if (activity == null) return@AccountLinkSection

                                            val webClientId =
                                                ctx.getString(R.string.default_web_client_id)
                                            val gso =
                                                GoogleSignInOptions.Builder(
                                                    GoogleSignInOptions.DEFAULT_SIGN_IN
                                                )
                                                    .requestIdToken(webClientId)
                                                    .requestEmail()
                                                    .build()
                                            val client =
                                                GoogleSignIn.getClient(activity, gso)
                                            vm.startLinkGoogle()
                                            client.signOut().addOnCompleteListener {
                                                googleLauncher.launch(client.signInIntent)
                                            }
                                        },
                                        onDelete = {
                                            vm.softDeleteAccount {
                                                Toast.makeText(
                                                    ctx,
                                                    it,
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    )
                                } else {
                                    // ===== EDIT 모드 =====
                                    EditProfileForm(
                                        ui = ui,
                                        onCancel = { vm.setEditMode(false) },
                                        onSave = { nn, bd, gd, mb, bt ->
                                            vm.save(nn, bd, gd, mb, bt)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 로그아웃 버튼 (카드 아래, 광고 위에 고정)
                Button(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x00FDCA60),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = stringResource(R.string.btn_logout),
                        fontFamily = PretendardMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 프리미엄 준비중 다이얼로그
            if (showPremiumDialog) {
                AlertDialog(
                    onDismissRequest = { showPremiumDialog = false },
                    confirmButton = {
                        TextButton(onClick = { showPremiumDialog = false }) {
                            Text(
                                text = stringResource(R.string.ok),
                                fontFamily = PretendardMedium
                            )
                        }
                    },
                    title = {
                        Text(
                            text = stringResource(R.string.dlg_premium_title),
                            fontFamily = PretendardBold
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.dlg_premium_msg),
                            fontFamily = PretendardMedium
                        )
                    }
                )
            }

            // 로그아웃 확인 다이얼로그
            if (showLogoutDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoutDialog = false },
                    confirmButton = {
                        TextButton(onClick = {
                            showLogoutDialog = false
                            vm.logout()
                        }) {
                            Text(
                                text = stringResource(R.string.logout_confirm_yes),
                                fontFamily = PretendardMedium
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLogoutDialog = false }) {
                            Text(
                                text = stringResource(R.string.logout_confirm_no),
                                fontFamily = PretendardMedium
                            )
                        }
                    },
                    title = {
                        Text(
                            text = stringResource(R.string.logout_confirm_title),
                            fontFamily = PretendardBold
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.logout_confirm_msg),
                            fontFamily = PretendardMedium
                        )
                    }
                )
            }

            // 저장/연동 중 오버레이
            if (ui.saving || ui.linkInProgress) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, CardStroke),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun ProfileHeader(ui: SettingsUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0x22000000)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = ui.chineseZodiacIcon,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = PretendardBold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = if (ui.nickname.isBlank())
                    stringResource(R.string.value_placeholder_dash)
                else ui.nickname,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = PretendardBold,
                fontWeight = FontWeight.Bold
            )

            val subs = buildList {
                if (ui.age >= 0) add(stringResource(R.string.summary_age_value, ui.age))
                if (ui.gender.isNotBlank()) add(ui.gender)
                if (ui.mbti.isNotBlank()) add(ui.mbti)
                if (ui.birthTimeLabel.isNotBlank() && ui.birthTimeCode != "none") add(
                    ui.birthTimeLabel
                )
            }.joinToString(" · ")

            Text(
                text = subs,
                color = Color(0xB3FFFFFF),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = PretendardMedium
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color(0xFFEDDF90),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = PretendardMedium
        )
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = PretendardBold
        )
    }
}

@Composable
private fun QuickSummary(ui: SettingsUiState) {
    val dash = stringResource(R.string.value_placeholder_dash)
    val birth = if (ui.birthIso.isBlank()) dash else ui.birthIso
    val gender = if (ui.gender.isBlank()) dash else ui.gender
    val mbti = if (ui.mbti.isBlank()) dash else ui.mbti
    val ageText =
        if (ui.age >= 0) stringResource(R.string.summary_age_value, ui.age) else dash
    val birthTime = if (ui.birthTimeLabel.isBlank()) dash else ui.birthTimeLabel

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SummaryRow(
            label = stringResource(R.string.summary_birth_prefix),
            value = birth
        )
        SummaryRow(
            label = stringResource(R.string.summary_gender_prefix),
            value = gender
        )
        SummaryRow(
            label = stringResource(R.string.summary_mbti_prefix),
            value = mbti
        )
        SummaryRow(
            label = stringResource(R.string.summary_age_prefix),
            value = ageText
        )
        SummaryRow(
            label = stringResource(R.string.summary_wz_prefix, ""),
            value = ui.westernZodiacText
        )
        SummaryRow(
            label = stringResource(R.string.summary_birthtime_prefix),
            value = birthTime
        )
    }
}

@Composable
private fun QuickStats(ui: SettingsUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x872C2B2B)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.gpt_usage_today),
                    color = Color(0xFFA6E9E8),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = PretendardMedium
                )
                Text(
                    text = stringResource(
                        R.string.unit_times_value,
                        ui.gptUsedToday
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = PretendardBold,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.dream_count),
                    color = Color(0xFFA6E9E8),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = PretendardMedium
                )
                Text(
                    text = stringResource(
                        R.string.unit_entries_value,
                        ui.dreamTotalLocal
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = PretendardBold,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SettingsButtons(
    onEdit: () -> Unit,
    onPremium: () -> Unit,
    onContact: () -> Unit,
    onTerms: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {

        TextButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onEdit,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 10.dp)
        ) {
            Text(
                text = stringResource(R.string.btn_profile_edit),
                color = Color(0xFF76E4E0), // 잘못된 HEX 수정 + 약간의 포인트 컬러
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = PretendardMedium
            )
        }

        TextButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onPremium,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 10.dp)
        ) {
            Text(
                text = stringResource(R.string.btn_premium),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = PretendardMedium
            )
        }

        TextButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onContact,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 10.dp)
        ) {
            Text(
                text = stringResource(R.string.btn_contact),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = PretendardMedium
            )
        }

        TextButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onTerms,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 10.dp)
        ) {
            Text(
                text = stringResource(R.string.btn_terms),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = PretendardMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileForm(
    ui: SettingsUiState,
    onCancel: () -> Unit,
    onSave: (
        nickname: String,
        birthIso: String,
        gender: String,
        mbti: String,
        birthTimeCode: String
    ) -> Unit
) {
    var nickname by remember(ui.nickname) { mutableStateOf(ui.nickname) }
    var birth by remember(ui.birthIso) { mutableStateOf(ui.birthIso) }
    var gender by remember(ui.gender) { mutableStateOf(ui.gender) }
    var mbti by remember(ui.mbti) { mutableStateOf(ui.mbti) }
    var birthCode by remember(ui.birthTimeCode) { mutableStateOf(ui.birthTimeCode) }
    var birthLabel by remember(ui.birthTimeLabel) { mutableStateOf(ui.birthTimeLabel) }
    var showBirthPicker by remember { mutableStateOf(false) }




    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,

        focusedContainerColor = FieldContainer,
        unfocusedContainerColor = FieldContainer,
        disabledContainerColor = FieldContainer.copy(alpha = 0.7f),

        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,

        focusedLabelColor = FieldLabelFocused,
        unfocusedLabelColor = FieldLabelUnfocused,

        focusedPlaceholderColor = Color(0x66FFFFFF),
        unfocusedPlaceholderColor = Color(0x66FFFFFF),

        cursorColor = AccentLavender
    )

    Text(
        text = stringResource(R.string.btn_profile_edit),
        color = Color(0xFFFDCA60),
        style = MaterialTheme.typography.titleMedium,
        fontFamily = PretendardBold,
        fontWeight = FontWeight.Bold
    )
    HorizontalDivider(color = Color(0x44494949))
    Spacer(Modifier.height(12.dp))

    // 이름
    AnimatedOutlinedTextField(
        value = nickname,
        onValueChange = { nickname = it },

        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = {
            Text(
                text = stringResource(R.string.label_name),
                fontFamily = PretendardMedium
            )
        },
        placeholder = {
            Text(
                text = stringResource(R.string.hint_name_example),
                fontFamily = PretendardMedium
            )
        },
        colors = fieldColors
    )

    Spacer(Modifier.height(10.dp))

    // 생일 (DatePicker 팝업 – 읽기 전용 필드)
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedOutlinedTextField(
            value = birth,
            onValueChange = { },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            readOnly = true,
            label = {
                Text(
                    text = stringResource(R.string.label_birthdate),
                    fontFamily = PretendardMedium
                )
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.hint_birthdate_format),
                    fontFamily = PretendardMedium
                )
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Outlined.CalendarMonth,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f)
                )
            },
            colors = fieldColors
        )

        // 전체를 투명 클릭 영역으로 사용 (Ripple 제거)
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showBirthPicker = true
                }
        )
    }

    if (showBirthPicker) {
        val datePickerState = rememberDatePickerState()

        DatePickerDialog(
            onDismissRequest = { showBirthPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            val date = Date(millis)
                            val format =
                                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            birth = format.format(date)
                        }
                        showBirthPicker = false
                    }
                ) {
                    Text(
                        text = stringResource(R.string.ok),
                        fontFamily = PretendardMedium,
                        color = AccentLavender
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showBirthPicker = false }) {
                    Text(
                        text = stringResource(R.string.btn_cancel),
                        fontFamily = PretendardMedium,
                        color = Color(0xFFB0B0B0)
                    )
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                modifier = Modifier
                    .scale(0.9f), // 달력 전체 사이즈 살짝 축소
                title = {
                    Text(
                        text = stringResource(R.string.label_birthdate),
                        fontFamily = PretendardBold,
                        color = DatePickerTextColor,
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp)
                    )
                },
                colors = DatePickerDefaults.colors(
                    containerColor = DatePickerBackground,          // 연보라 배경
                    titleContentColor = DatePickerTextColor,
                    headlineContentColor = DatePickerTextColor,
                    weekdayContentColor = DatePickerTextColor,      // 요일 글씨 검정
                    subheadContentColor = DatePickerTextColor,
                    dayContentColor = DatePickerTextColor,          // 날짜 숫자 검정
                    disabledDayContentColor = DatePickerTextColor.copy(alpha = 0.3f),
                    selectedDayContainerColor = AccentLavender,
                    selectedDayContentColor = Color.White,
                    todayContentColor = AccentLavender,
                    todayDateBorderColor = AccentLavender
                )
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    // 성별
    Text(
        text = stringResource(R.string.label_gender),
        color = Color.White,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = PretendardMedium
    )

    val maleLabel = stringResource(R.string.gender_male)
    val femaleLabel = stringResource(R.string.gender_female)

    Spacer(Modifier.height(6.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GenderChip(
            text = maleLabel,
            selected = gender == maleLabel,
            onClick = { gender = maleLabel }
        )
        GenderChip(
            text = femaleLabel,
            selected = gender == femaleLabel,
            onClick = { gender = femaleLabel }
        )
    }

    Spacer(Modifier.height(12.dp))

    // MBTI
    Text(
        text = stringResource(R.string.label_mbti),
        color = Color(0xFFFDD071),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = PretendardMedium
    )
    Spacer(Modifier.height(4.dp))
    MBTIDropdown(
        selected = mbti,
        onSelect = { mbti = it },
        fieldColors = fieldColors
    )

    Spacer(Modifier.height(12.dp))

    // 출생시간
    Text(
        text = stringResource(R.string.label_birthtime),
        color = Color(0xFFFDD071),
        style = MaterialTheme.typography.bodySmall,
        fontFamily = PretendardMedium
    )
    Spacer(Modifier.height(4.dp))
    BirthTimeDropdown(
        selectedLabel = birthLabel,
        slots = birthSlotsUi(),
        onSelect = { (code, label) ->
            birthCode = code
            birthLabel = label
        },
        fieldColors = fieldColors
    )

    Spacer(Modifier.height(16.dp))

    // 취소 / 저장 버튼 → 배경 없는 텍스트 버튼
    Row(
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth()
    ) {
        TextButton(onClick = onCancel) {
            Text(
                text = stringResource(R.string.btn_cancel),
                color = Color(0xFFCBCBCB),
                fontFamily = PretendardMedium
            )
        }
        Spacer(Modifier.width(4.dp))
        TextButton(onClick = {
            onSave(nickname, birth, gender, mbti, birthCode)
        }) {
            Text(
                text = stringResource(R.string.btn_save),
                color = AccentLavender,
                fontFamily = PretendardBold,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GenderChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) Color(0x33B388FF) else Color(0x22000000),
        label = "gender_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) AccentLavender else FieldBorder,
        label = "gender_border"
    )

    Surface(
        modifier = Modifier.height(34.dp),
        shape = RoundedCornerShape(999.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(color = AccentLavender.copy(alpha = 0.25f)),
                    onClick = onClick

                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontFamily = PretendardMedium,
                color = if (selected) Color.White else Color(0xCCFFFFFF),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MBTIDropdown(
    selected: String,
    onSelect: (String) -> Unit,
    fieldColors: TextFieldColors
) {
    val noneLabel = stringResource(R.string.select_none)
    val list = listOf(
        noneLabel,
        "INTJ", "INTP", "ENTJ", "ENTP",
        "INFJ", "INFP", "ENFJ", "ENFP",
        "ISTJ", "ISFJ", "ESTJ", "ESFJ",
        "ISTP", "ISFP", "ESTP", "ESFP"
    )
    var expanded by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = if (expanded || isFocused) FieldBorderFocused else FieldBorder,
        label = "mbti_border"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (expanded || isFocused) 1.dp else 2.dp,
        label = "mbti_width"
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        Box(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            OutlinedTextField(
                value = if (selected.isBlank()) list.first() else selected,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                interactionSource = interactionSource,
                colors = fieldColors,
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = PretendardMedium,
                    color = Color.White
                )
            )
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            list.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item, fontFamily = PretendardMedium) },
                    onClick = {
                        onSelect(if (item == noneLabel) "" else item)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthTimeDropdown(
    selectedLabel: String,
    slots: List<Pair<String, String>>,
    onSelect: (Pair<String, String>) -> Unit,
    fieldColors: TextFieldColors
) {
    var expanded by remember { mutableStateOf(false) }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = if (expanded || isFocused) FieldBorderFocused else FieldBorder,
        label = "birthtime_border"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (expanded || isFocused) 1.dp else 2.dp,
        label = "birthtime_width"
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        Box(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            OutlinedTextField(
                value = if (selectedLabel.isBlank())
                    stringResource(R.string.birthtime_none)
                else selectedLabel,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                interactionSource = interactionSource,
                colors = fieldColors,
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = PretendardMedium,
                    color = Color.White
                )
            )
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            slots.forEach { slot ->
                DropdownMenuItem(
                    text = { Text(slot.second, fontFamily = PretendardMedium) },
                    onClick = {
                        onSelect(slot)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun birthSlotsUi(): List<Pair<String, String>> {
    val ctx = LocalContext.current
    val isKo = ctx.resources.configuration.locales[0].language.startsWith("ko")

    return remember(isKo) {
        listOf(
            "none" to (if (isKo) ctx.getString(R.string.birthtime_none) else "None"),
            "23_01" to (if (isKo) "자시 (23:00~01:00)" else "Zi (23:00–01:00)"),
            "01_03" to (if (isKo) "축시 (01:00~03:00)" else "Chou (01:00–03:00)"),
            "03_05" to (if (isKo) "인시 (03:00~05:00)" else "Yin (03:00–05:00)"),
            "05_07" to (if (isKo) "묘시 (05:00~07:00)" else "Mao (05:00–07:00)"),
            "07_09" to (if (isKo) "진시 (07:00~09:00)" else "Chen (07:00–09:00)"),
            "09_11" to (if (isKo) "사시 (09:00~11:00)" else "Si (09:00–11:00)"),
            "11_13" to (if (isKo) "오시 (11:00~13:00)" else "Wu (11:00–13:00)"),
            "13_15" to (if (isKo) "미시 (13:00~15:00)" else "Wei (13:00–15:00)"),
            "15_17" to (if (isKo) "신시 (15:00~17:00)" else "Shen (15:00–17:00)"),
            "17_19" to (if (isKo) "유시 (17:00~19:00)" else "You (17:00–19:00)"),
            "19_21" to (if (isKo) "술시 (19:00~21:00)" else "Xu (19:00–21:00)"),
            "21_23" to (if (isKo) "해시 (21:00~23:00)" else "Hai (21:00–23:00)")
        )
    }
}

@Composable
private fun AccountLinkSection(
    ui: SettingsUiState,
    onLinkGoogle: () -> Unit,
    onDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.label_account_link),
            color = Color(0xFFFDD071),
            style = MaterialTheme.typography.titleSmall,
            fontFamily = PretendardMedium,
            fontWeight = FontWeight.Bold
        )

        if (ui.accountStatusLabel.isNotBlank()) {
            Text(
                text = ui.accountStatusLabel,
                color = Color(0xFFB3FFFFFF),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = PretendardMedium
            )
        }

        val enabled = ui.googleButtonEnabled && !ui.linkInProgress
        val label = when {
            ui.googleButtonLabel.isNotBlank() -> ui.googleButtonLabel
            else -> stringResource(R.string.btn_google_connect)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .alpha(if (enabled) 1f else 0.6f)
                .clip(RoundedCornerShape(20.dp))
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(color = Color.Black.copy(alpha = 0.1f))
                ) { onLinkGoogle() },
            contentAlignment = Alignment.Center
        ) {
            // btn_google.xml 을 그대로 배경으로 사용 (텍스트는 이미지에만)
            Image(
                painter = painterResource(R.drawable.btn_google),
                contentDescription = label,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(4.5f, matchHeightConstraintsFirst = true),
                contentScale = ContentScale.FillBounds
            )
        }

        if (ui.canDeleteAccount) {
            TextButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onDelete,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(R.string.btn_delete_account),
                    color = Color(0xFFFF8A80),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = PretendardMedium
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.account_link_note),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0x80FFFFFF),
            fontFamily = PretendardMedium
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimatedOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
    colors: TextFieldColors
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) FieldBorderFocused else FieldBorder,
        label = "textfield_border"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) 1.dp else 2.dp,
        label = "textfield_width"
    )

    Box(
        modifier = modifier
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
       TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxSize()
                .heightIn(min = 50.dp),  // 이 부분 추가!
            singleLine = singleLine,
            readOnly = readOnly,
            label = label,
            placeholder = placeholder,
            trailingIcon = trailingIcon,
            interactionSource = interactionSource,
            textStyle = LocalTextStyle.current.copy(
                fontFamily = PretendardMedium,
                color = Color.White
            ),
            colors = colors,
            shape = RoundedCornerShape(12.dp)
        )
    }
}