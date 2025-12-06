package com.dreamindream.app.ui.settings

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

// --- Theme Colors ---
private val SettingsBg = Color(0xFF0F172A)
private val CardBg = Color(0xFF1E293B)
private val AccentGold = Color(0xFFD4AF37)
private val TextMain = Color(0xFFF8FAFC)
private val TextSub = Color(0xFF94A3B8)
private val InputFieldBg = Color(0xFF334155)

@Composable
fun SettingsScreen(
    vm: SettingsViewModel = viewModel(),
    onNavigateToSubscription: () -> Unit,
    onLogout: () -> Unit
) {
    val uiState by vm.ui.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    // State for Dialogs
    var showEditWarningDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showGuestWarningDialog by remember { mutableStateOf(false) }

    // Google Sign-In logic (Suppress Deprecation Warning)
    @Suppress("DEPRECATION")
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            account?.idToken?.let { token -> vm.linkGoogleWithIdToken(token) { vm.showToast(it) } }
        } catch (e: Exception) {
            vm.handleGoogleError("Google Sign-In Failed: ${e.message}")
        }
    }

    val processGoogleSignIn = {
        if (activity != null) {
            @Suppress("DEPRECATION")
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            @Suppress("DEPRECATION")
            val client = GoogleSignIn.getClient(activity, gso)
            vm.startLinkGoogle()
            client.signOut().addOnCompleteListener { googleLauncher.launch(client.signInIntent) }
        }
    }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.toastShown()
        }
    }

    AdPageScaffold(adUnitRes = R.string.ad_unit_settings_banner) { padding ->
        Box(modifier = Modifier.fillMaxSize().background(SettingsBg)) {
            // Background Image
            Image(
                painter = painterResource(R.drawable.main_ground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().alpha(0.2f),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SETTINGS",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = AccentGold,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(30.dp))

                AnimatedContent(targetState = uiState.isEditMode, label = "Mode") { isEdit ->
                    if (isEdit) {
                        EditProfileView(
                            uiState = uiState,
                            onSave = { n, b, g, m, t, cc, cn, cf ->
                                vm.saveProfile(n, b, g, m, t, cc, cn, cf)
                            },
                            onCancel = vm::cancelEditMode
                        )
                    } else {
                        ViewProfileView(
                            uiState = uiState,
                            onEditClick = {
                                if (uiState.isProfileLocked) showEditWarningDialog = true
                                else vm.enterEditMode()
                            },
                            onPremiumClick = onNavigateToSubscription,
                            onGoogleClick = { if (uiState.isGuest) showGuestWarningDialog = true else processGoogleSignIn() },
                            onLogoutClick = { showLogoutDialog = true },
                            onContactClick = { activity?.startActivity(Intent(context, FeedbackActivity::class.java)) },
                            onTermsClick = { activity?.startActivity(Intent(context, TermsActivity::class.java)) },
                            onTestPremiumToggle = vm::setTestPremium
                        )
                    }
                }
                Spacer(Modifier.height(50.dp))
            }

            // --- Dialogs ---
            if (showLogoutDialog) {
                SettingsAlertDialog(
                    title = stringResource(R.string.logout_dialog_title),
                    text = stringResource(R.string.logout_dialog_message),
                    confirmText = stringResource(R.string.btn_logout),
                    onConfirm = { showLogoutDialog = false; vm.logout(); onLogout() },
                    onDismiss = { showLogoutDialog = false },
                    isDestructive = true
                )
            }
            if (showEditWarningDialog) {
                SettingsAlertDialog(
                    title = stringResource(R.string.alert_edit_profile_title),
                    text = stringResource(R.string.alert_edit_profile_msg),
                    confirmText = stringResource(R.string.btn_yes_edit),
                    onConfirm = { showEditWarningDialog = false; vm.enterEditMode() },
                    onDismiss = { showEditWarningDialog = false }
                )
            }
            if (showGuestWarningDialog) {
                SettingsAlertDialog(
                    title = stringResource(R.string.data_warning_title),
                    text = stringResource(R.string.guest_warning_message),
                    confirmText = stringResource(R.string.btn_confirm),
                    onConfirm = { showGuestWarningDialog = false; processGoogleSignIn() },
                    onDismiss = { showGuestWarningDialog = false }
                )
            }
        }
    }
}

@Composable
fun ViewProfileView(
    uiState: SettingsUiState,
    onEditClick: () -> Unit,
    onPremiumClick: () -> Unit,
    onGoogleClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onContactClick: () -> Unit,
    onTermsClick: () -> Unit,
    onTestPremiumToggle: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // 1. Profile Card
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(70.dp).clip(CircleShape).background(AccentGold),
                    contentAlignment = Alignment.Center
                ) {
                    Text(uiState.zodiacAnimal.ifBlank { "👤" }, fontSize = 32.sp)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            uiState.nickname.ifBlank { "Guest User" },
                            color = TextMain,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        if (uiState.isPremium) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Rounded.Star, null, tint = AccentGold, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(uiState.email, color = TextSub, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(uiState.countryFlag, fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(uiState.countryName, color = TextSub, fontSize = 14.sp)
                    }
                }
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Rounded.Edit, null, tint = AccentGold)
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBadge("Age", "${uiState.age}")
                StatBadge("Zodiac", uiState.zodiacSign)
                StatBadge("MBTI", uiState.mbti)
            }
        }

        // 2. Account & Subscription
        SectionHeader("Account")
        SettingsCard {
            SettingsItem("Premium Membership", "Unlock unlimited insights", Icons.Rounded.Star, AccentGold, onPremiumClick)
            Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
            SettingsItem(uiState.googleButtonLabel, uiState.accountProviderLabel, null, TextMain, onGoogleClick, iconRes = R.drawable.google_logo)
        }

        // 3. Support
        SectionHeader("Support")
        SettingsCard {
            SettingsItem("Contact Us", null, Icons.Rounded.Email, TextMain, onContactClick)
            Divider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 12.dp))
            SettingsItem("Terms & Policy", null, Icons.Rounded.Policy, TextMain, onTermsClick)
        }

        // 4. Logout / Dev
        SettingsCard {
            SettingsItem("Logout", null, Icons.Rounded.Logout, Color(0xFFEF5350), onLogoutClick)
            Divider(color = Color.White.copy(alpha = 0.1f))
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Developer Mode", color = TextSub, fontSize = 12.sp)
                Switch(
                    checked = uiState.isPremium,
                    onCheckedChange = onTestPremiumToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = AccentGold, checkedTrackColor = Color.Black)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileView(
    uiState: SettingsUiState,
    onSave: (String, String, String, String, String, String, String, String) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(uiState.nickname) }
    var birth by remember { mutableStateOf(uiState.birthIso) }
    var gender by remember { mutableStateOf(uiState.gender) }
    var mbti by remember { mutableStateOf(uiState.mbti) }
    var selectedCountry by remember { mutableStateOf(Country(uiState.countryCode, uiState.countryName, uiState.countryFlag)) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showCountryDialog by remember { mutableStateOf(false) }

    val allCountries = remember { CountryUtils.getAllCountries() }
    var searchQuery by remember { mutableStateOf("") }
    val filteredCountries = remember(searchQuery) {
        if (searchQuery.isBlank()) allCountries else allCountries.filter { it.name.contains(searchQuery, true) }
    }

    SettingsCard {
        Text("Edit Profile", color = AccentGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        Label("Country")
        Box(Modifier.fillMaxWidth().clickable { showCountryDialog = true }) {
            OutlinedTextField(
                value = "${selectedCountry.flag} ${selectedCountry.name}",
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, tint = TextSub) },
                colors = defaultInputColors()
            )
            Box(Modifier.matchParentSize().clickable { showCountryDialog = true })
        }
        Spacer(Modifier.height(16.dp))

        Label("Nickname")
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            colors = defaultInputColors()
        )
        Spacer(Modifier.height(16.dp))

        Label("Birthdate")
        Box {
            OutlinedTextField(
                value = birth,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = { Icon(Icons.Rounded.CalendarToday, null, tint = TextSub) },
                colors = defaultInputColors()
            )
            Box(Modifier.matchParentSize().clickable { showDatePicker = true })
        }
        Spacer(Modifier.height(16.dp))

        Label("Gender")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GenderChip("Male", gender == "Male") { gender = "Male" }
            GenderChip("Female", gender == "Female") { gender = "Female" }
            GenderChip("Others", gender == "Others") { gender = "Others" }
        }
        Spacer(Modifier.height(16.dp))

        Label("MBTI")
        OutlinedTextField(
            value = mbti,
            onValueChange = { mbti = it.uppercase() },
            modifier = Modifier.fillMaxWidth(),
            colors = defaultInputColors()
        )
        Spacer(Modifier.height(32.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel", color = TextSub) }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onSave(name, birth, gender, mbti, "none", selectedCountry.code, selectedCountry.name, selectedCountry.flag) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentGold)
            ) {
                Text("Save Changes", color = SettingsBg, fontWeight = FontWeight.Bold)
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
                }) { Text("OK", color = AccentGold) }
            },
            colors = DatePickerDefaults.colors(containerColor = CardBg)
        ) {
            DatePicker(state = datePickerState, colors = DatePickerDefaults.colors(titleContentColor = AccentGold, headlineContentColor = TextMain, dayContentColor = TextMain, selectedDayContainerColor = AccentGold))
        }
    }

    if (showCountryDialog) {
        AlertDialog(
            onDismissRequest = { showCountryDialog = false },
            title = { Text("Select Country", color = TextMain) },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search...", color = TextSub) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = defaultInputColors()
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(Modifier.height(300.dp)) {
                        items(filteredCountries) { c ->
                            Row(
                                Modifier.fillMaxWidth().clickable { selectedCountry = c; showCountryDialog = false }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(c.flag, fontSize = 24.sp)
                                Spacer(Modifier.width(16.dp))
                                Text(c.name, color = TextMain)
                            }
                            Divider(color = Color.White.copy(alpha = 0.1f))
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = SettingsBg
        )
    }
}

// --- Components ---

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun SettingsItem(title: String, subtitle: String?, icon: ImageVector?, color: Color, onClick: () -> Unit, iconRes: Int? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(InputFieldBg), contentAlignment = Alignment.Center) {
            if (iconRes != null) Image(painterResource(iconRes), null, Modifier.size(20.dp))
            else if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = color, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = TextSub, fontSize = 12.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = TextSub)
    }
}

@Composable
fun StatBadge(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.ifBlank { "-" }, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, color = TextSub, fontSize = 12.sp)
    }
}

@Composable
fun GenderChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AccentGold else InputFieldBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (selected) SettingsBg else TextSub, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(text, color = TextSub, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
}

@Composable
fun Label(text: String) {
    Text(text, color = TextSub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
fun defaultInputColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = InputFieldBg,
    unfocusedContainerColor = InputFieldBg,
    focusedTextColor = TextMain,
    unfocusedTextColor = TextMain,
    focusedBorderColor = AccentGold,
    unfocusedBorderColor = Color.Transparent
)

@Composable
fun SettingsAlertDialog(title: String, text: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit, isDestructive: Boolean = false) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = TextMain) },
        text = { Text(text, color = TextSub) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText, color = if(isDestructive) Color(0xFFEF5350) else AccentGold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSub) } },
        containerColor = SettingsBg
    )
}