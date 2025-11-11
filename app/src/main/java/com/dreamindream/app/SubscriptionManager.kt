package com.dreamindream.app.billing

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn

object SubscriptionManager {

    private const val TAG = "SubscriptionManager"
    private const val PREFS = "premium_cache"

    enum class ServerState { ENTITLED, GRACE, HOLD, PAUSED, CANCELED, EXPIRED, REVOKED, UNKNOWN }
    data class Entitlement(
        val serverState: ServerState = ServerState.UNKNOWN,
        val serverCheckedAt: Long = 0L
    ) {
        val isEntitled get() = serverState == ServerState.ENTITLED || serverState == ServerState.GRACE
    }

    private val premiumState = MutableStateFlow(false)
    private var listener: ListenerRegistration? = null
    private var currentUid: String? = null

    @JvmOverloads
    fun init(context: Context, uidOverride: String? = null) {
        val uid = uidOverride ?: FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Log.w(TAG, "init: no uid; stop listening")
            stop()
            premiumState.value = false
            return
        }
        if (currentUid == uid && listener != null) return

        stop()
        currentUid = uid

        // 1) 캐시 warm start
        val cached = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(cacheKey(uid), false)
        premiumState.value = cached

        // 2) 서버 스냅샷(권위)
        val doc = FirebaseFirestore.getInstance().document("users/$uid/billing/state")
        listener = doc.addSnapshotListener { snap, err ->
            if (err != null) {
                Log.w(TAG, "Firestore listen failed: ${err.message}")
                return@addSnapshotListener
            }
            val serverStateStr = (snap?.getString("serverState") ?: "UNKNOWN").uppercase()
            val entitled = when (runCatching { ServerState.valueOf(serverStateStr) }.getOrElse { ServerState.UNKNOWN }) {
                ServerState.UNKNOWN -> (snap?.getBoolean("isPremium") ?: false) // 레거시 호환
                ServerState.ENTITLED, ServerState.GRACE -> true
                else -> false
            }
            premiumState.value = entitled
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(cacheKey(uid), entitled).apply()
        }
    }

    fun observePremium(owner: LifecycleOwner, block: (Boolean) -> Unit) {
        premiumState.onEach(block).launchIn(owner.lifecycleScope)
    }

    fun isPremium(context: Context): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(cacheKey(uid), false)
    }

    /** 낙관 반영(힌트) — 서버가 최종 갱신 */
    @JvmOverloads
    fun setPremium(context: Context, premium: Boolean, uidOverride: String? = null) {
        val uid = uidOverride ?: FirebaseAuth.getInstance().currentUser?.uid
        premiumState.value = premium
        if (uid != null) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(cacheKey(uid), premium).apply()
            try {
                val db = FirebaseFirestore.getInstance()
                db.document("users/$uid/billing/state").set(
                    mapOf("isPremium" to premium, "lastCheckedAt" to System.currentTimeMillis()),
                    com.google.firebase.firestore.SetOptions.merge()
                )
            } catch (_: Throwable) {}
        }
    }

    fun isAnonymous(): Boolean = FirebaseAuth.getInstance().currentUser?.isAnonymous == true
    fun isEligibleForPurchase(): Boolean = !isAnonymous()

    fun stop() {
        listener?.remove()
        listener = null
    }

    private fun cacheKey(uid: String) = "premium_$uid"
}
