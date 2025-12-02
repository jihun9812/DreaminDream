package com.dreamindream.app.ui.dream

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airbnb.lottie.LottieAnimationView
import com.dreamindream.app.AdManager
import com.dreamindream.app.AdPageScaffold
import com.dreamindream.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DreamScreen(vm: DreamViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    val ctx = LocalContext.current
    val scroll = rememberScrollState()

    // 토스트
    ui.toast?.let {
        LaunchedEffect(it) {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
        }
    }

    // 결과 도착 시 결과 카드로 스크롤
    LaunchedEffect(ui.resultRaw) {
        if (ui.resultRaw.isNotBlank()) {
            kotlinx.coroutines.delay(100)
            scroll.animateScrollTo(scroll.maxValue)
        }
    }

    AdPageScaffold(adUnitRes = R.string.ad_unit_dream_banner) { pad ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0B1220), Color(0xFF17212B))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.dream_input_label),
                    color = Color(0xFFFDE995),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // 남은 해석 수
                Text(
                    text = stringResource(R.string.dream_today_left, ui.remaining),
                    color = Color(0xFFB8D5F6),
                    style = MaterialTheme.typography.bodySmall
                )

                // 입력 카드
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF)),
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.cardElevation(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BasicTextField(
                            value = ui.input,
                            onValueChange = { vm.onInputChange(it) },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 16.sp,
                                lineHeight = 20.sp
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 140.dp)
                        )
                        // 해석 버튼 (그라디언트 pill)
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFFEDCA6), Color(0xFF8BAAFF))
                                    )
                                )
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                                .clickable(enabled = !ui.isLoading) {
                                    // 🔥 광고 게이트 열기 → 보상 후 onClickInterpret 실행
                                    AdManager.openGate {
                                        vm.onClickInterpret()
                                    }
                                }
                        ) {
                            Text(
                                text = stringResource(R.string.button_interpret),
                                color = Color(0xFF17212B),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // 로딩 애니메이션 (Lottie)
                AnimatedVisibility(visible = ui.isLoading) {
                    AndroidView(
                        modifier = Modifier
                            .size(100.dp)
                            .align(Alignment.CenterHorizontally),
                        factory = { context ->
                            LottieAnimationView(context).apply {
                                setAnimation(R.raw.just_flow_teal)
                                repeatCount = com.airbnb.lottie.LottieDrawable.INFINITE
                                playAnimation()
                            }
                        },
                        update = {
                            if (ui.isLoading) it.playAnimation() else it.cancelAnimation()
                        }
                    )
                }

                // 결과 카드
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF)),
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.cardElevation(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        val placeholder = if (ui.resultRaw.isBlank())
                            stringResource(R.string.dream_result_placeholder)
                        else null

                        if (placeholder != null) {
                            Text(
                                placeholder,
                                color = Color(0xFFB8D5F6),
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 18.sp
                            )
                        } else {
                            Text(
                                ui.resultStyled,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }

    // ⛔️ 보상형 광고 바텀시트는 이제 AdPageScaffold + AdKitCompose 쪽에서 전역 관리하므로
    // 여기에서 RewardGateSheet를 직접 호출할 필요가 없음.

    // 쿼터 초과 안내
    if (ui.showLimitDialog) {
        AlertDialog(
            onDismissRequest = { vm.onLimitDialogDismiss() },
            confirmButton = {
                TextButton(onClick = { vm.onLimitDialogDismiss() }) {
                    Text(stringResource(R.string.ok))
                }
            },
            title = { Text(stringResource(R.string.dream_quota_title)) },
            text = { Text(stringResource(R.string.dream_quota_message)) }
        )
    }
}
