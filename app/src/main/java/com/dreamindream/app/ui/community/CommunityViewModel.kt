package com.dreamindream.app.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CommunityViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        CommunityFeedUiState(
            isLoading = true,
            userDisplayName = "",
            selectedCategory = PostCategory.All,
            topPosts = emptyList(),
            posts = emptyList()
        )
    )
    val uiState: StateFlow<CommunityFeedUiState> = _uiState

    init {
        loadInitialFeed()
    }

    /** 카테고리 선택 */
    fun onCategorySelected(category: PostCategory) {
        _uiState.update { current ->
            current.copy(selectedCategory = category)
        }
        // TODO: 나중에 Firestore 필터링 붙이기
    }

    /** 새로고침 */
    fun refresh() {
        loadInitialFeed()
    }

    /** 초기 피드 로딩 – 지금은 UI 확인용 더미 데이터 */
    private fun loadInitialFeed() {
        viewModelScope.launch {
            // TODO: FirestoreManager 이용해서 실제 피드 불러오기
            val dummyPost = CommunityPostUi(
                id = "1",
                authorName = "Dreamer",
                authorFlagEmoji = "🇰🇷",
                authorCountryCode = "KR",
                createdAtText = "3분 전",
                editedCount = 0,
                content = "오늘 꿈에서 거대한 달이 바로 앞에 떠 있었어요. 그 아래를 천천히 걸었어요.",
                imageUrls = emptyList(),
                commentCount = 3,
                reactionSummary = ReactionSummaryUi(totalCount = 12),
                category = PostCategory.Dream
            )

            _uiState.value = CommunityFeedUiState(
                isLoading = false,
                userDisplayName = "지훈", // 로그인 이름으로 나중에 교체
                selectedCategory = PostCategory.All,
                topPosts = listOf(
                    dummyPost,
                    dummyPost.copy(id = "2"),
                    dummyPost.copy(id = "3")
                ),
                posts = List(10) { index ->
                    dummyPost.copy(id = (index + 10).toString())
                }
            )
        }
    }
}
