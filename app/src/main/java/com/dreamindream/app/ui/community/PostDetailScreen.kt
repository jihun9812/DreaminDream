package com.dreamindream.app.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * 게시글 상세 + 댓글 화면
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    uiState: PostDetailUiState,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSendComment: (String) -> Unit,
) {
    var commentText by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(DetailColors.background),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DetailColors.background,
                    titleContentColor = DetailColors.onBackground,
                    navigationIconContentColor = DetailColors.onBackground,
                    actionIconContentColor = DetailColors.onBackground
                ),
                title = {
                    Text(
                        text = "게시글 상세",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                },
                actions = {
                    if (uiState.isMine) {
                        IconButton(onClick = onEditClick) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = "수정"
                            )
                        }
                        IconButton(onClick = onDeleteClick) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "삭제"
                            )
                        }
                    } else {
                        IconButton(onClick = { /* 신고 / 차단 등 나중에 */ }) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = "더보기"
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            CommentInputBar(
                text = commentText,
                onTextChange = { commentText = it },
                onSendClick = {
                    val trimmed = commentText.trim()
                    if (trimmed.isNotEmpty()) {
                        onSendComment(trimmed)
                        commentText = ""
                    }
                }
            )
        },
        containerColor = DetailColors.background
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = DetailColors.background
        ) {
            if (uiState.isLoading || uiState.post == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "게시글을 불러오는 중…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DetailColors.subText
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 12.dp,
                        bottom = 96.dp // 하단 입력창 공간
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        PostDetailCard(post = uiState.post)
                    }

                    item {
                        ReactionInfoRow(
                            reactionSummary = uiState.post.reactionSummary,
                            commentCount = uiState.comments.size
                        )
                    }

                    item {
                        Divider(
                            color = DetailColors.divider,
                            thickness = 1.dp
                        )
                    }

                    item {
                        Text(
                            text = "댓글 ${uiState.comments.size}",
                            style = MaterialTheme.typography.titleSmall,
                            color = DetailColors.onBackground
                        )
                    }

                    items(uiState.comments, key = { it.id }) { comment ->
                        CommentItem(comment = comment)
                    }
                }
            }
        }
    }
}

@Composable
private fun PostDetailCard(
    post: CommunityPostUi
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DetailColors.card
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(DetailColors.avatarBackground),
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
                            style = MaterialTheme.typography.bodyMedium,
                            color = DetailColors.onBackground
                        )
                        if (post.authorCountryCode.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = post.authorCountryCode,
                                style = MaterialTheme.typography.labelSmall,
                                color = DetailColors.subText
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
                        color = DetailColors.subText
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyMedium,
                color = DetailColors.onCard
            )

            if (post.imageUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "이미지 ${post.imageUrls.size}장",
                    style = MaterialTheme.typography.labelMedium,
                    color = DetailColors.subText
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                DetailCategoryChip(text = post.category.displayName)
            }
        }
    }
}

@Composable
private fun ReactionInfoRow(
    reactionSummary: ReactionSummaryUi,
    commentCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "반응 ${reactionSummary.totalCount}",
            style = MaterialTheme.typography.labelMedium,
            color = DetailColors.subText
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "댓글 $commentCount",
            style = MaterialTheme.typography.labelMedium,
            color = DetailColors.subText
        )
    }
}

@Composable
private fun CommentItem(
    comment: CommentUi
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DetailColors.commentBackground)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(DetailColors.avatarBackground),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = comment.authorFlagEmoji.ifEmpty { "⭐" },
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = comment.authorName,
                        style = MaterialTheme.typography.bodySmall,
                        color = DetailColors.onBackground
                    )
                    if (comment.authorCountryCode.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = comment.authorCountryCode,
                            style = MaterialTheme.typography.labelSmall,
                            color = DetailColors.subText
                        )
                    }
                }

                val editedSuffix = if (comment.editedCount > 0) {
                    " · 수정 ${comment.editedCount}회"
                } else {
                    ""
                }

                Text(
                    text = comment.createdAtText + editedSuffix,
                    style = MaterialTheme.typography.labelSmall,
                    color = DetailColors.subText
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = comment.content,
            style = MaterialTheme.typography.bodyMedium,
            color = DetailColors.onCard
        )
    }
}

@Composable
private fun CommentInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit
) {
    Surface(
        color = DetailColors.background,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp)),
                placeholder = {
                    Text(
                        text = "댓글을 입력해 주세요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DetailColors.subText
                    )
                },
                maxLines = 3,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = DetailColors.inputBackground,
                    unfocusedContainerColor = DetailColors.inputBackground,
                    disabledContainerColor = DetailColors.inputBackground,
                    cursorColor = DetailColors.onCard,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (text.isBlank()) DetailColors.sendDisabled
                        else DetailColors.primary
                    )
                    .clickable(enabled = text.isNotBlank()) {
                        onSendClick()
                    }
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Send,
                    contentDescription = "댓글 전송",
                    tint = DetailColors.onPrimary
                )
            }
        }
    }
}

@Composable
private fun DetailCategoryChip(
    text: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(DetailColors.chip),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = DetailColors.subText,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// 색 팔레트
private object DetailColors {
    val background = Color(0xFF050712)
    val onBackground = Color(0xFFF5F6FF)

    val card = Color(0xFF111322)
    val onCard = Color(0xFFE7E9FF)

    val subText = Color(0xFF9BA1C5)
    val divider = Color(0xFF1D2033)

    val avatarBackground = Color(0xFF1A1D30)
    val commentBackground = Color(0xFF0D0F1C)

    val chip = Color(0xFF141726)
    val inputBackground = Color(0xFF141726)

    val primary = Color(0xFF8BAAFF)
    val onPrimary = Color(0xFF050712)
    val sendDisabled = Color(0xFF303345)
}

// UI 상태 모델
@Immutable
data class PostDetailUiState(
    val isLoading: Boolean = false,
    val isMine: Boolean = false,
    val post: CommunityPostUi? = null,
    val comments: List<CommentUi> = emptyList()
)

@Immutable
data class CommentUi(
    val id: String,
    val authorName: String,
    val authorFlagEmoji: String = "",
    val authorCountryCode: String = "",
    val createdAtText: String,
    val editedCount: Int = 0,
    val content: String
)

@Preview(
    name = "Post Detail",
    showBackground = true,
    backgroundColor = 0xFF050712
)
@Composable
private fun PostDetailPreview() {
    val dummyPost = CommunityPostUi(
        id = "post_1",
        authorName = "Dreamer",
        authorFlagEmoji = "🇰🇷",
        authorCountryCode = "KR",
        createdAtText = "5분 전",
        editedCount = 1,
        content = "오늘 꿈에서 거대한 달이 눈앞에 떠 있었어요.\n" +
                "그 아래를 천천히 걷고 있는데, 갑자기 발밑이 사라지는 느낌이 들었어요.",
        imageUrls = emptyList(),
        commentCount = 3,
        reactionSummary = ReactionSummaryUi(totalCount = 12),
        category = PostCategory.Dream
    )

    val dummyComments = listOf(
        CommentUi(
            id = "c1",
            authorName = "Luna",
            authorFlagEmoji = "🇸🇬",
            authorCountryCode = "SG",
            createdAtText = "3분 전",
            content = "달 관련 꿈은 보통 감성, 직관, 잠재적인 감정을 의미해요.",
            editedCount = 0
        ),
        CommentUi(
            id = "c2",
            authorName = "Nightwalker",
            authorFlagEmoji = "🇮🇳",
            authorCountryCode = "IN",
            createdAtText = "1분 전",
            content = "요즘 고민 많으신가요? 감정 정리가 필요할 때 이런 꿈이 자주 오더라구요.",
            editedCount = 1
        )
    )

    val previewState = PostDetailUiState(
        isLoading = false,
        isMine = true,
        post = dummyPost,
        comments = dummyComments
    )

    PostDetailScreen(
        uiState = previewState,
        onBackClick = {},
        onEditClick = {},
        onDeleteClick = {},
        onSendComment = {}
    )
}
