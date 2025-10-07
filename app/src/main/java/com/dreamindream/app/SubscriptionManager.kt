package com.dreamindream.app.billing

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn

object SubscriptionManager {
    private const val PREFS = "billing_prefs"
    private const val KEY_PREMIUM = "is_premium"

    private val premiumState = MutableStateFlow(false)

    fun init(context: Context) {
        premiumState.value = isPremium(context)
    }

    fun isPremium(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREMIUM, false)
    }

    fun setPremium(context: Context, premium: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PREMIUM, premium).apply()
        premiumState.value = premium
    }

    fun observePremium(owner: LifecycleOwner, onChanged: (Boolean) -> Unit) {
        premiumState.onEach(onChanged).launchIn(owner.lifecycleScope)
    }
}
