package com.dreamindream.app.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPageScreen(
    userName: String = "지훈",
    onBackClick: () -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MyPageColors.background),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MyPageColors.background,
                    titleContentColor = MyPageColors.onBackground,
                    navigationIconContentColor = MyPageColors.onBackground,
                    actionIconContentColor = MyPageColors.onBackground
                ),
                title = {
                    Text(
                        text = "My Dreammunity",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO 프로필 편집 */ }) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "프로필 편집"
                        )
                    }
                }
            )
        },
        containerColor = MyPageColors.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MyPageColors.background)
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProfileCard(userName = userName)

            ActivitySummaryCard(
                postCount = 12,
                likeCount = 87,
                commentCount = 34
            )

            GuideCard()
        }
    }
}

@Composable
private fun ProfileCard(
    userName: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MyPageColors.card
        ),
        elevation = CardDefaults.cardElevation(3.dp),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MyPageColors.avatarBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Person,
                    contentDescription = null,
                    tint = MyPageColors.onBackground
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = userName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MyPageColors.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "꿈·운세를 기록하는 DreaminDream 사용자",
                    style = MaterialTheme.typography.bodySmall,
                    color = MyPageColors.subText
                )
            }
        }
    }
}

@Composable
private fun ActivitySummaryCard(
    postCount: Int,
    likeCount: Int,
    commentCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MyPageColors.card
        ),
        elevation = CardDefaults.cardElevation(3.dp),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(
                text = "이번 달 활동",
                style = MaterialTheme.typography.titleSmall,
                color = MyPageColors.onBackground
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SummaryChip(
                    label = "작성한 글",
                    value = postCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                SummaryChip(
                    label = "받은 반응",
                    value = likeCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                SummaryChip(
                    label = "댓글",
                    value = commentCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MyPageColors.chip)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MyPageColors.subText
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MyPageColors.onBackground
        )
    }
}

@Composable
private fun GuideCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MyPageColors.card
        ),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Dreammunity 사용 TIP",
                style = MaterialTheme.typography.titleSmall,
                color = MyPageColors.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "• 꿈을 자세히 적을수록 AI 분석 정확도가 올라가요.\n" +
                        "• 공감·조언 태그를 활용해 다른 사람의 경험도 참고해 보세요.\n" +
                        "• 중요한 꿈은 북마크 기능(추후 제공 예정)을 사용해 따로 모아둘 수 있어요.",
                style = MaterialTheme.typography.bodySmall,
                color = MyPageColors.subText
            )
        }
    }
}

private object MyPageColors {
    val background = Color(0xFF050712)
    val onBackground = Color(0xFFF5F6FF)

    val card = Color(0xFF111322)
    val subText = Color(0xFF9BA1C5)

    val avatarBackground = Color(0xFF1A1D30)
    val chip = Color(0xFF141726)
}

@Preview(
    name = "My Page",
    showBackground = true,
    backgroundColor = 0xFF050712
)
@Composable
private fun MyPagePreview() {
    MaterialTheme {
        MyPageScreen(onBackClick = {})
    }
}
