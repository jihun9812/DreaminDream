package com.dreamindream.app.ui.subscription

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.billingclient.api.ProductDetails
import com.dreamindream.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    viewModel: SubscriptionViewModel = viewModel(),
    onClose: () -> Unit = {}
) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(10.dp))

            // Premium Title Badge
            Surface(
                color = Color(0xFF2A2A2A),
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = "DREAM PREMIUM",
                    color = Color(0xFFFFD54F),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Text(
                text = "꿈 해석의 모든 것을\n무제한으로 경험하세요",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    lineHeight = 40.sp
                ),
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))

            // Benefits List
            BenefitItem("광고 없는 쾌적한 환경")
            BenefitItem("AI 주간 리포트 ")
            BenefitItem("모든 운세 및 심화 분석 잠금 해제")
            BenefitItem("프리미엄 전용 커뮤니티 뱃지")

            Spacer(Modifier.height(40.dp))

            // Pricing Cards
            if (ui.loading) {
                CircularProgressIndicator(color = Color(0xFFFFD54F))
            } else if (ui.products.isEmpty()) {
                Text("현재 구매 가능한 상품이 없습니다.", color = Color.Gray)
            } else {
                ui.products.sortedBy { it.productId }.forEach { pd ->
                    ProductCard(pd) {
                        if (activity != null) viewModel.buy(activity, pd)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            Spacer(Modifier.height(20.dp))

            TextButton(onClick = { viewModel.restore(context) }) {
                Text("구매 복원하기", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun BenefitItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = Color(0xFFE0E0E0), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ProductCard(pd: ProductDetails, onClick: () -> Unit) {
    val offer = pd.subscriptionOfferDetails?.firstOrNull()
    val price = offer?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice ?: "가격 정보 없음"

    // 단순화: 월간/연간 구분 (실제 productId에 따라 텍스트 변경 가능)
    val period = if (pd.productId.contains("year")) "연간 멤버십" else "월간 멤버십"
    val subtext = if (pd.productId.contains("year")) "1년마다 결제" else "매월 결제"

    val brush = Brush.horizontalGradient(listOf(Color(0xFFFEDCA6), Color(0xFFF5C066)))

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(60.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(0.dp) // 그라데이션을 위해
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(period, color = Color.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text(subtext, color = Color(0xFF424242), style = MaterialTheme.typography.labelSmall)
                }
                Text(price, color = Color.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}