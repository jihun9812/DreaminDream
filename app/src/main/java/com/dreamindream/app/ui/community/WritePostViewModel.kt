package com.dreamindream.app.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WritePostViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(WritePostUiState())
    val uiState: StateFlow<WritePostUiState> = _uiState

    fun onContentChange(text: String) {
        _uiState.value = _uiState.value.copy(content = text)
    }

    fun onCategoryClick(category: PostCategory) {
        _uiState.value = _uiState.value.copy(selectedCategory = category)
    }

    fun onAddImageClick() {
        // TODO: 나중에 이미지 픽커 붙이고 URI 추가
        // 지금은 구조만, 아무 것도 안 함
    }

    fun onRemoveImageClick(label: String) {
        _uiState.value = _uiState.value.copy(
            imagePreviews = _uiState.value.imagePreviews.filterNot { it == label }
        )
    }

    fun onSubmitClick(
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit = {}
    ) {
        val current = _uiState.value
        if (current.content.isBlank() || current.isPosting) return

        viewModelScope.launch {
            _uiState.value = current.copy(isPosting = true)
            try {
                // TODO: 나중에 여기서 FirestoreManager 써서 실제 업로드
                // ex) firestore.createCommunityPost(...)
                onSuccess()
                // 성공 후 입력 값 초기화
                _uiState.value = WritePostUiState()
            } catch (t: Throwable) {
                onError(t)
                _uiState.value = _uiState.value.copy(isPosting = false)
            }
        }
    }
}
