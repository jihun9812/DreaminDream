package com.dreamindream.app

import android.content.Context
import com.dreamindream.app.ui.billing.BillingManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SubscriptionManager {

    private const val PREF_NAME = "subscription"
    private const val KEY_SUBSCRIBED = "is_subscribed"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed

    fun init(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // 1. 로컬 캐시 먼저 로드 (빠른 UI 반영)
        val localState = prefs.getBoolean(KEY_SUBSCRIBED, false)
        updateGlobalState(localState)

        // 2. Billing 라이브러리 감시
        BillingManager.init(app)
        scope.launch {
            BillingManager.isSubscribed.collect { billingState ->
                if (billingState != _isSubscribed.value) {
                    updateGlobalState(billingState)
                    prefs.edit().putBoolean(KEY_SUBSCRIBED, billingState).apply()
                    syncToFirestore(billingState)
                }
            }
        }

        // 3. Firestore 역동기화 (기기 변경 대응)
        syncFromFirestore(prefs)
    }

    // ★ 상태 업데이트 핵심 허브
    private fun updateGlobalState(subscribed: Boolean) {
        _isSubscribed.value = subscribed
        // AdManager에 즉시 전파하여 배너 광고 제거
        AdManager.isPremium = subscribed
    }

    private fun syncFromFirestore(prefs: android.content.SharedPreferences) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null && !user.isAnonymous) {
            FirebaseFirestore.getInstance().collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { doc ->
                    val remote = doc.getBoolean("subscribed") ?: false
                    // Firestore가 true면 로컬도 true로 강제 (Billing이 아직 로드 안 됐을 수 있음)
                    if (remote && !BillingManager.isSubscribed.value) {
                        updateGlobalState(true)
                        prefs.edit().putBoolean(KEY_SUBSCRIBED, true).apply()
                    }
                }
        }
    }

    fun isSubscribedNow(): Boolean = _isSubscribed.value

    // 테스트용 또는 복구용 수동 설정
    fun markSubscribed(context: Context, subscribed: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SUBSCRIBED, subscribed).apply()
        updateGlobalState(subscribed)
        syncToFirestore(subscribed)
    }

    private fun syncToFirestore(subscribed: Boolean) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        if (user.isAnonymous) return
        val data = mapOf("subscribed" to subscribed)
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .set(data, SetOptions.merge())
    }
}