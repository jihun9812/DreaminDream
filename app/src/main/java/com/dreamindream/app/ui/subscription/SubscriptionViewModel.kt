package com.dreamindream.app.ui.subscription

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dreamindream.app.SubscriptionManager
import com.dreamindream.app.ui.billing.BillingManager
import com.android.billingclient.api.ProductDetails
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SubscriptionUiState(
    val loading: Boolean = true,
    val products: List<ProductDetails> = emptyList(),
    val isSubscribed: Boolean = false,
    val error: String? = null
)

class SubscriptionViewModel : ViewModel() {

    private val _ui = MutableStateFlow(SubscriptionUiState())
    val ui: StateFlow<SubscriptionUiState> = _ui

    init {
        viewModelScope.launch {
            BillingManager.productDetails.collect { list ->
                _ui.update { it.copy(products = list, loading = false) }
            }
        }
        viewModelScope.launch {
            BillingManager.isSubscribed.collect { s ->
                _ui.update { it.copy(isSubscribed = s) }
            }
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            BillingManager.queryProducts()
            BillingManager.refreshPurchases()
            _ui.update { it.copy(loading = false) }
        }
    }

    fun buy(activity: Activity, product: ProductDetails) {
        BillingManager.purchase(activity, product)
    }

    fun restore(context: Context) {
        viewModelScope.launch {
            BillingManager.restore()
        }
    }

    fun markSubForTest(context: Context, value: Boolean) {
        SubscriptionManager.markSubscribed(context, value)
    }
}
