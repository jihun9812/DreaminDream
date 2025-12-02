package com.dreamindream.app.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Immutable
data class WritePostUiState(
    val content: String = "",
    val selectedCategory: PostCategory = PostCategory.Dream,
    val imagePreviews: List<String> = emptyList(), // 나중에 URI/URL로 사용
    val isPosting: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritePostScreen(
    uiState: WritePostUiState,
    onBackClick: () -> Unit,
    onContentChange: (String) -> Unit,
    onCategoryClick: (PostCategory) -> Unit,
    onAddImageClick: () -> Unit,
    onRemoveImageClick: (String) -> Unit,
    onSubmitClick: () -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(WriteColors.background),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WriteColors.background,
                    titleContentColor = WriteColors.onBackground,
                    navigationIconContentColor = WriteColors.onBackground,
                    actionIconContentColor = WriteColors.onBackground
                ),
                title = {
                    Text(
                        text = "새 글 작성",
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
                }
            )
        },
        containerColor = WriteColors.background,
        bottomBar = {
            WriteBottomBar(
                enabled = uiState.content.isNotBlank() && !uiState.isPosting,
                isPosting = uiState.isPosting,
                onSubmitClick = onSubmitClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(WriteColors.background)
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // 카테고리 선택
            CategorySelectorRow(
                selected = uiState.selectedCategory,
                onCategoryClick = onCategoryClick
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 내용 입력
            TextField(
                value = uiState.content,
                onValueChange = onContentChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                placeholder = {
                    Text(
                        text = "꿈 내용을 자세하게 적어주세요.\n예) 하늘을 날다가 갑자기 떨어지는 느낌이 났어요…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WriteColors.subText
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = WriteColors.inputBackground,
                    unfocusedContainerColor = WriteColors.inputBackground,
                    disabledContainerColor = WriteColors.inputBackground,
                    cursorColor = WriteColors.onBackground,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                maxLines = Int.MAX_VALUE
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 이미지 섹션
            ImageSelectorRow(
                imagePreviews = uiState.imagePreviews,
                onAddImageClick = onAddImageClick,
                onRemoveImageClick = onRemoveImageClick
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CategorySelectorRow(
    selected: PostCategory,
    onCategoryClick: (PostCategory) -> Unit
) {
    Column {
        Text(
            text = "카테고리",
            style = MaterialTheme.typography.labelMedium,
            color = WriteColors.subText
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            PostCategory.values().forEach { category ->
                if (category == PostCategory.All) return@forEach // 전체는 글쓰기에서 제외
                val isSelected = category == selected

                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (isSelected) WriteColors.primaryChip
                            else WriteColors.chip
                        )
                        .clickable { onCategoryClick(category) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = category.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) WriteColors.onPrimary else WriteColors.subText
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageSelectorRow(
    imagePreviews: List<String>,
    onAddImageClick: () -> Unit,
    onRemoveImageClick: (String) -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "이미지",
                style = MaterialTheme.typography.labelMedium,
                color = WriteColors.subText
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${imagePreviews.size} / 4",
                style = MaterialTheme.typography.labelSmall,
                color = WriteColors.subText
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // + 이미지 추가 카드
            item {
                AddImageCard(onClick = onAddImageClick)
            }

            // 선택된 이미지들
            items(imagePreviews, key = { it }) { item ->
                SelectedImageCard(
                    label = item,
                    onRemoveClick = { onRemoveImageClick(item) }
                )
            }
        }
    }
}

@Composable
private fun AddImageCard(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .size(width = 90.dp, height = 90.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = WriteColors.card
        ),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(WriteColors.chip),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = "이미지 추가",
                    tint = WriteColors.subText
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "이미지 추가",
                style = MaterialTheme.typography.labelSmall,
                color = WriteColors.subText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SelectedImageCard(
    label: String,
    onRemoveClick: () -> Unit
) {
    Card(
        modifier = Modifier.size(width = 90.dp, height = 90.dp),
        colors = CardDefaults.cardColors(
            containerColor = WriteColors.card
        ),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // 지금은 썸네일 대신 라벨만; 나중에 Coil로 이미지 썸네일 교체
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = WriteColors.onBackground,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 6.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            IconButton(
                onClick = onRemoveClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(WriteColors.chip)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "삭제",
                    tint = WriteColors.subText
                )
            }
        }
    }
}

@Composable
private fun WriteBottomBar(
    enabled: Boolean,
    isPosting: Boolean,
    onSubmitClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(WriteColors.background)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Button(
            onClick = onSubmitClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = WriteColors.primary,
                contentColor = WriteColors.onPrimary,
                disabledContainerColor = WriteColors.disabled,
                disabledContentColor = WriteColors.onBackground.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                text = if (isPosting) "등록 중…" else "등록하기",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private object WriteColors {
    val background = Color(0xFF050712)
    val onBackground = Color(0xFFF5F6FF)

    val card = Color(0xFF111322)
    val subText = Color(0xFF9BA1C5)

    val inputBackground = Color(0xFF111322)

    val chip = Color(0xFF141726)
    val primaryChip = Color(0xFF8BAAFF)

    val primary = Color(0xFF8BAAFF)
    val onPrimary = Color(0xFF050712)
    val disabled = Color(0xFF303345)
}

@Preview(
    name = "Write Post",
    showBackground = true,
    backgroundColor = 0xFF050712
)
@Composable
private fun WritePostPreview() {
    val uiState = WritePostUiState(
        content = "",
        selectedCategory = PostCategory.Dream,
        imagePreviews = listOf("첫 번째 이미지", "두 번째 이미지")
    )

    MaterialTheme {
        WritePostScreen(
            uiState = uiState,
            onBackClick = {},
            onContentChange = {},
            onCategoryClick = {},
            onAddImageClick = {},
            onRemoveImageClick = {},
            onSubmitClick = {}
        )
    }
}
