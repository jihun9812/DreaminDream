package com.dreamindream.app.ui.community

import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityFeedScreen(
    modifier: Modifier = Modifier,
    uiState: CommunityFeedUiState,
    onPostClick: (String) -> Unit,
    onWriteClick: () -> Unit,
    onMyPageClick: () -> Unit,
    onCategoryClick: (PostCategory) -> Unit,
    onRefresh: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(CommunityColors.background),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CommunityColors.background,
                    titleContentColor = CommunityColors.onBackground,
                    navigationIconContentColor = CommunityColors.onBackground,
                    actionIconContentColor = CommunityColors.onBackground
                ),
                title = {
                    Column {
                        Text(
                            text = "Dreammunity",
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (uiState.userDisplayName.isNotEmpty()) {
                            Text(
                                text = "${uiState.userDisplayName}님, 오늘도 좋은 꿈 나눠볼까요?",
                                style = MaterialTheme.typography.bodySmall,
                                color = CommunityColors.subText
                            )
                        }
                    }
                },
                navigationIcon = {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsNone,
                        contentDescription = "알림",
                        modifier = Modifier
                            .padding(start = 8.dp)
                    )
                },
                actions = {
                    IconButton(onClick = { /* TODO 검색 화면 연결 */ }) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = "검색"
                        )
                    }
                    IconButton(onClick = onMyPageClick) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = "마이페이지"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onWriteClick,
                containerColor = CommunityColors.primary,
                contentColor = CommunityColors.onPrimary,
                modifier = Modifier
                    .shadow(16.dp, CircleShape, clip = false)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "글 쓰기"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CommunityColors.background)
                .padding(innerPadding)
        ) {
            // 카테고리 필터
            CategoryFilterBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                selected = uiState.selectedCategory,
                onCategoryClick = onCategoryClick
            )

            // TODO: 당분간 당겨서 새로고침 대신 클릭으로 새로고침
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "피드를 불러오는 중…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CommunityColors.subText
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = 20.dp,
                    vertical = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 이번 주 인기글 섹션
                if (uiState.topPosts.isNotEmpty()) {
                    item {
                        TopPostsSection(
                            topPosts = uiState.topPosts,
                            onPostClick = onPostClick
                        )
                    }
                }

                // 전체 피드
                items(uiState.posts, key = { it.id }) { post ->
                    PostCard(
                        post = post,
                        onClick = { onPostClick(post.id) }
                    )
                }

                // 맨 아래 여백 약간
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TopPostsSection(
    topPosts: List<CommunityPostUi>,
    onPostClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Whatshot,
                contentDescription = null,
                tint = CommunityColors.primary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "이번 주 인기글 Top 3",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            topPosts.take(3).forEachIndexed { index, post ->
                PostCard(
                    post = post,
                    onClick = { onPostClick(post.id) },
                    isHighlighted = true,
                    rank = index + 1
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 게시글 카드 UI – 대기업 서비스 느낌으로 정갈하게 배치
 */
@Composable
private fun PostCard(
    post: CommunityPostUi,
    onClick: () -> Unit,
    isHighlighted: Boolean = false,
    rank: Int? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) {
                CommunityColors.highlightCard
            } else {
                CommunityColors.card
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isHighlighted) 6.dp else 2.dp
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            // 상단: 작성자 정보 + 랭크 뱃지
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 아바타 (국기 + 이니셜)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CommunityColors.avatarBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = post.authorFlagEmoji.ifEmpty { "🌙" },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.authorName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (post.authorCountryCode.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = post.authorCountryCode,
                                style = MaterialTheme.typography.labelSmall,
                                color = CommunityColors.subText
                            )
                        }
                    }

                    val editedSuffix = if (post.editedCount > 0) {
                        " · 수정 ${post.editedCount}회"
                    } else {
                        ""
                    }

                    Text(
                        text = post.createdAtText + editedSuffix,
                        style = MaterialTheme.typography.labelSmall,
                        color = CommunityColors.subText
                    )
                }

                if (rank != null) {
                    RankBadge(rank = rank)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 본문 텍스트
            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyMedium,
                color = CommunityColors.onCard,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            // 이미지 섹션 (있을 때만)
            if (post.imageUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                PostImagePreview(imageUrls = post.imageUrls)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 하단: 카테고리 + 리액션 + 댓글 수
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryChip(
                    text = post.category.displayName
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "댓글 ${post.commentCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = CommunityColors.subText
                )

                Spacer(modifier = Modifier.weight(1f))

                ReactionSummaryView(
                    summary = post.reactionSummary
                )
            }
        }
    }
}

@Composable
private fun RankBadge(rank: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(CommunityColors.rankBadgeBackground)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "#$rank",
            style = MaterialTheme.typography.labelSmall,
            color = CommunityColors.rankBadgeText
        )
    }
}

@Composable
private fun PostImagePreview(
    imageUrls: List<String>
) {
    // 대기업 스타일: 이미지도 정갈하게, 과한 장식 없이
    when (imageUrls.size) {
        1 -> {
            SingleImage(
                url = imageUrls[0],
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .clip(RoundedCornerShape(18.dp))
            )
        }

        2 -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SingleImage(
                    url = imageUrls[0],
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                )
                SingleImage(
                    url = imageUrls[1],
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                )
            }
        }

        else -> {
            // 3장 이상 -> 첫 장 크게 + 오른쪽 2장 (또는 2x2)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SingleImage(
                    url = imageUrls[0],
                    modifier = Modifier
                        .weight(1.4f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SingleImage(
                        url = imageUrls.getOrNull(1),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        SingleImage(
                            url = imageUrls.getOrNull(2),
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(18.dp))
                        )

                        if (imageUrls.size > 3) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(CommunityColors.imageOverlay),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+${imageUrls.size - 3}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CommunityColors.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleImage(
    url: String?,
    modifier: Modifier = Modifier
) {
    if (url == null) {
        Box(
            modifier = modifier
                .background(CommunityColors.imagePlaceholder)
        )
        return
    }

    Image(
        painter = rememberAsyncImagePainter(url),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}

// ---------------------
// 카테고리 필터
// ---------------------

@Composable
private fun CategoryFilterBar(
    modifier: Modifier = Modifier,
    selected: PostCategory,
    onCategoryClick: (PostCategory) -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PostCategory.values().forEach { category ->
            val isSelected = category == selected
            CategoryChip(
                text = category.displayName,
                selected = isSelected,
                onClick = { onCategoryClick(category) }
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        Icon(
            imageVector = Icons.Outlined.ThumbUp,
            contentDescription = null,
            tint = CommunityColors.subText
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "최신순",
            style = MaterialTheme.typography.labelMedium,
            color = CommunityColors.subText
        )
    }
}

@Composable
private fun CategoryChip(
    text: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val background = if (selected) CommunityColors.primaryChip else CommunityColors.chip
    val contentColor = if (selected) CommunityColors.onPrimary else CommunityColors.subText

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .let { base ->
                if (onClick != null) base.clickable(onClick = onClick) else base
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
    }
}

// ---------------------
// 리액션 집계
// ---------------------

@Composable
private fun ReactionSummaryView(
    summary: ReactionSummaryUi
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (summary.totalCount > 0) {
            Text(
                text = "반응 ${summary.totalCount}",
                style = MaterialTheme.typography.labelMedium,
                color = CommunityColors.subText
            )
        }
    }
}

// ---------------------
// 색 & 모델
// ---------------------

private object CommunityColors {
    val background = androidx.compose.ui.graphics.Color(0xFF050712)
    val onBackground = androidx.compose.ui.graphics.Color(0xFFF5F6FF)

    val card = androidx.compose.ui.graphics.Color(0xFF111322)
    val highlightCard = androidx.compose.ui.graphics.Color(0xFF171A2C)
    val onCard = androidx.compose.ui.graphics.Color(0xFFE7E9FF)

    val primary = androidx.compose.ui.graphics.Color(0xFF8BAAFF)
    val onPrimary = androidx.compose.ui.graphics.Color(0xFF050712)

    val subText = androidx.compose.ui.graphics.Color(0xFF9BA1C5)

    val avatarBackground = androidx.compose.ui.graphics.Color(0xFF1A1D30)

    val chip = androidx.compose.ui.graphics.Color(0xFF141726)
    val primaryChip = androidx.compose.ui.graphics.Color(0xFF8BAAFF)

    val rankBadgeBackground = androidx.compose.ui.graphics.Color(0x338BAAFF)
    val rankBadgeText = androidx.compose.ui.graphics.Color(0xFFB8C8FF)

    val imagePlaceholder = androidx.compose.ui.graphics.Color(0xFF1A1D30)
    val imageOverlay = androidx.compose.ui.graphics.Color(0x88000000)
}

@Immutable
data class CommunityFeedUiState(
    val isLoading: Boolean = false,
    val userDisplayName: String = "",
    val selectedCategory: PostCategory = PostCategory.All,
    val topPosts: List<CommunityPostUi> = emptyList(),
    val posts: List<CommunityPostUi> = emptyList()
)

@Immutable
data class CommunityPostUi(
    val id: String,
    val authorName: String,
    val authorFlagEmoji: String = "",
    val authorCountryCode: String = "",
    val createdAtText: String,
    val editedCount: Int = 0,
    val content: String,
    val imageUrls: List<String> = emptyList(),
    val category: PostCategory = PostCategory.General,
    val commentCount: Int = 0,
    val reactionSummary: ReactionSummaryUi = ReactionSummaryUi()
)

@Immutable
data class ReactionSummaryUi(
    val totalCount: Int = 0
)


@Preview(
    showBackground = true,
    backgroundColor = 0xFF050712,
    name = "Community Feed – 기본"
)
@Composable
fun CommunityFeedScreenPreview() {
    val dummyPost = CommunityPostUi(
        id = "1",
        authorName = "Dreamer",
        authorFlagEmoji = "🇰🇷",
        authorCountryCode = "KR",
        createdAtText = "3분 전",
        content = "오늘 꿈에서 거대한 달이 바로 앞에 떠 있었어요. 그 아래를 천천히 걸었어요.",
        imageUrls = emptyList(),
        commentCount = 3,
        reactionSummary = ReactionSummaryUi(totalCount = 12),
        category = PostCategory.Dream
    )

    val uiState = CommunityFeedUiState(
        isLoading = false,
        userDisplayName = "지훈",
        selectedCategory = PostCategory.All,
        topPosts = listOf(dummyPost, dummyPost, dummyPost),
        posts = List(6) { dummyPost }
    )

    CommunityFeedScreen(
        uiState = uiState,
        onPostClick = {},
        onWriteClick = {},
        onMyPageClick = {},
        onCategoryClick = {}
    )
}

enum class PostCategory(val displayName: String) {
    All("전체"),
    General("일상"),
    Dream("꿈 이야기"),
    Advice("조언·공감"),
    Question("질문")
}
