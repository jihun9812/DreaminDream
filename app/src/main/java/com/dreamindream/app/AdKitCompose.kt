package com.dreamindream.app

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*

@Composable
fun AdPageScaffold(
    @StringRes adUnitRes: Int?,              // null이면 배너 없음
    topBar: @Composable (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val showGate by AdManager.showGate.collectAsState()

    Scaffold(
        topBar = { topBar?.invoke() },
        bottomBar = {
            if (!AdManager.isPremium && adUnitRes != null) {
                Box(Modifier.fillMaxWidth()) {
                    AdBanner(
                        adUnitRes,
                        Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    )
                }
            }
        }
    ) { pad ->
        // 실제 화면 내용
        content(pad)

        // 보상형 광고 게이트 (전역 AdManager 상태 기반)
        if (showGate) {
            RewardGateSheet(
                onDismiss = { AdManager.hideGate() },
                onReward = { AdManager.onRewardFinished() }
            )
        }
    }
}

/** 배너 컴포저블: strings.xml 의 광고 단위 ID 사용 */
@Composable
fun AdBanner(@StringRes adUnitRes: Int, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MobileAds.initialize(ctx)
            AdView(ctx).apply {
                adUnitId = ctx.getString(adUnitRes)
                setAdSize(AdSize.BANNER)
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

/** 보상형 광고 바텀시트 UI */
@OptIn(ExperimentalMaterial3Api::class)   // ← 실험적 API 경고 해결
@Composable
fun RewardGateSheet(
    onDismiss: () -> Unit,
    onReward: () -> Unit,
    watchLabel: String = stringResource(R.string.ad_prompt_watch_ad),
    cancelLabel: String = stringResource(R.string.btn_cancel),
    preparingLabel: String = stringResource(R.string.ad_preparing)
) {
    val ctx = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp)) {
            Text(preparingLabel, color = Color.White)
            Text(
                text = stringResource(R.string.ad_prompt_status),
                color = Color(0xFFBFD0DC)
            )
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(cancelLabel)
                }

                Button(
                    onClick = {
                        val act = ctx as? Activity ?: return@Button
                        AdManager.showRewarded(
                            activity = act,
                            onRewardEarned = onReward,
                            onClosed = { AdManager.loadRewarded(ctx) },
                            onFailed = { AdManager.loadRewarded(ctx) }
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8BAAFF),
                        contentColor = Color(0xFF17212B)
                    )
                ) {
                    Text(watchLabel)
                }
            }
        }
    }
}
