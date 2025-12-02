package com.dreamindream.app.ui.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

object CommunityDestinations {
    const val FEED = "feed"
    const val POST_DETAIL = "post"
    const val WRITE = "write"
    const val MY_PAGE = "mypage"
}

/**
 * 커뮤니티 전용 NavHost
 * - 시작: 피드
 * - 피드 → 상세까지 Compose 안에서 네비게이션
 */
@Composable
fun CommunityNavHost(
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = CommunityDestinations.FEED,
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF050712))
    ) {
        // 1) 메인 피드
        composable(route = CommunityDestinations.FEED) {
            // 여기서 바로 ViewModel + uiState 붙인다
            val vm: CommunityViewModel = viewModel()
            val uiState by vm.uiState.collectAsState()

            CommunityFeedScreen(
                uiState = uiState,
                onPostClick = { postId ->
                    navController.navigate("${CommunityDestinations.POST_DETAIL}/$postId")
                },
                onWriteClick = {
                    navController.navigate(CommunityDestinations.WRITE)
                },
                onMyPageClick = {
                    navController.navigate(CommunityDestinations.MY_PAGE)
                },
                onCategoryClick = vm::onCategorySelected,
                onRefresh = vm::refresh
            )
        }

        // 2) 게시글 상세
        composable(
            route = "${CommunityDestinations.POST_DETAIL}/{postId}",
            arguments = listOf(
                navArgument("postId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: return@composable

            PostDetailRoute(
                postId = postId,
                onBackClick = { navController.popBackStack() }
            )
        }

        // 3) 글쓰기 (임시 화면)
        // 3) 글쓰기
        composable(route = CommunityDestinations.WRITE) {
            val vm: WritePostViewModel = viewModel()
            val uiState by vm.uiState.collectAsState()

            WritePostScreen(
                uiState = uiState,
                onBackClick = { navController.popBackStack() },
                onContentChange = vm::onContentChange,
                onCategoryClick = vm::onCategoryClick,
                onAddImageClick = vm::onAddImageClick,
                onRemoveImageClick = vm::onRemoveImageClick,
                onSubmitClick = {
                    vm.onSubmitClick(
                        onSuccess = {
                            // 등록 성공 시 피드로 돌아가기
                            navController.popBackStack()
                        }
                    )
                }
            )
        }

        // 4) 마이페이지
        composable(route = CommunityDestinations.MY_PAGE) {
            MyPageScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

    }
}

/**
 * 나중에 Firestore + ViewModel 붙일 때를 대비한 상세 Route
 */
@Composable
private fun PostDetailRoute(
    postId: String,
    onBackClick: () -> Unit
) {
    // 지금은 postId만 찍어주는 더미 상태
    val dummyPost = CommunityPostUi(
        id = postId,
        authorName = "Dreamer",
        authorFlagEmoji = "🇰🇷",
        authorCountryCode = "KR",
        createdAtText = "5분 전",
        editedCount = 0,
        content = "postId = $postId 에 대한 더미 게시글입니다.\n" +
                "나중에 Firestore에서 실제 게시글을 불러올 예정입니다.",
        imageUrls = emptyList(),
        commentCount = 0,
        reactionSummary = ReactionSummaryUi(totalCount = 0),
        category = PostCategory.Dream
    )

    val uiState = PostDetailUiState(
        isLoading = false,
        isMine = false,
        post = dummyPost,
        comments = emptyList()
    )

    PostDetailScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onEditClick = { /* TODO */ },
        onDeleteClick = { /* TODO */ },
        onSendComment = { /* TODO */ }
    )
}

/**
 * 아직 안 만든 화면용 임시 컴포저블
 */
@Composable
private fun PlaceholderScreen(
    text: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050712)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF9BA1C5)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF050712)
@Composable
private fun CommunityNavHostPreview() {
    MaterialTheme {
        CommunityNavHost()
    }
}
