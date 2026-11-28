package com.memora.app.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.memora.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SubscriptionManager(
    context: Context,
    private val scope: CoroutineScope
) : PurchasesUpdatedListener {
    private val appContext = context.applicationContext
    private val cache = EntitlementCache(appContext)
    private val verifier = BillingVerificationClient(appContext)
    private val offerByPlan = mutableMapOf<String, ProductDetails.SubscriptionOfferDetails>()
    private var productDetails: ProductDetails? = null
    private var expiryRefreshJob: Job? = null
    private var connecting = false

    private val _entitlement = MutableStateFlow(initialEntitlement())
    val entitlement: StateFlow<EntitlementState> = _entitlement.asStateFlow()

    private val _plans = MutableStateFlow<List<SubscriptionPlan>>(emptyList())
    val plans: StateFlow<List<SubscriptionPlan>> = _plans.asStateFlow()

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun onAppForeground() {
        if (BuildConfig.DEBUG_ENTITLEMENT) {
            _entitlement.value = EntitlementState(EntitlementMode.DEBUG, canWrite = true)
            return
        }
        if (billingClient.isReady) refresh() else connect()
    }

    fun refresh() {
        if (BuildConfig.DEBUG_ENTITLEMENT) return
        if (!billingClient.isReady) {
            connect()
            return
        }
        queryPlans()
        queryPurchases()
    }

    fun canWriteNow(): Boolean {
        if (BuildConfig.DEBUG_ENTITLEMENT) return true
        return EntitlementPolicy.fromCache(cache.read(), System.currentTimeMillis()).canWrite
    }

    fun launchPurchase(activity: Activity, basePlanId: String): Boolean {
        if (!billingClient.isReady) {
            connect()
            return false
        }
        val details = productDetails ?: return false
        val offer = synchronized(offerByPlan) { offerByPlan[basePlanId] } ?: return false
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offer.offerToken)
            .build()
        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()
        )
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchases(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> applyOfflineCache()
        }
    }

    private fun connect() {
        if (connecting || billingClient.isReady) return
        connecting = true
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) refresh()
                else applyOfflineCache()
            }

            override fun onBillingServiceDisconnected() {
                connecting = false
                applyOfflineCache()
            }
        })
    }

    private fun queryPlans() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(SubscriptionCatalog.PRODUCT_ID)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()
        billingClient.queryProductDetailsAsync(params) { result, detailsResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            val details = detailsResult.productDetailsList
                .firstOrNull { it.productId == SubscriptionCatalog.PRODUCT_ID }
                ?: return@queryProductDetailsAsync
            productDetails = details

            val grouped = details.subscriptionOfferDetails.orEmpty()
                .groupBy { it.basePlanId }
            val selected = SubscriptionCatalog.basePlanOrder.mapNotNull { basePlanId ->
                val candidates = grouped[basePlanId].orEmpty()
                candidates.firstOrNull(::hasOneMonthTrial)
                    ?: candidates.firstOrNull { it.offerId == null }
                    ?: candidates.firstOrNull()
            }
            synchronized(offerByPlan) {
                offerByPlan.clear()
                selected.forEach { offerByPlan[it.basePlanId] = it }
            }
            _plans.value = selected.mapNotNull(::toPlan)
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            } else {
                applyOfflineCache()
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        val relevant = purchases.filter { SubscriptionCatalog.PRODUCT_ID in it.products }
        val purchased = relevant.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (purchased != null) {
            scope.launch { verifyAndGrant(purchased) }
            return
        }
        if (relevant.any { it.purchaseState == Purchase.PurchaseState.PENDING }) {
            _entitlement.value = EntitlementState(EntitlementMode.PENDING, canWrite = false)
            return
        }
        cache.clear()
        expiryRefreshJob?.cancel()
        _entitlement.value = EntitlementState.readOnly()
    }

    private suspend fun verifyAndGrant(purchase: Purchase) {
        when (val result = verifier.verify(purchase.purchaseToken)) {
            is VerificationResult.Verified -> {
                if (!result.active || result.expiryTimeMillis <= result.serverTimeMillis) {
                    cache.clear()
                    _entitlement.value = EntitlementState.readOnly()
                    return
                }
                val now = System.currentTimeMillis()
                val cached = CachedEntitlement(
                    verifiedAtDeviceMillis = now,
                    serverTimeMillis = result.serverTimeMillis,
                    entitlementExpiryMillis = result.expiryTimeMillis,
                    basePlanId = result.basePlanId
                )
                cache.write(cached)
                _entitlement.value = EntitlementState(
                    mode = EntitlementMode.ACTIVE,
                    canWrite = true,
                    validUntilMillis = result.expiryTimeMillis,
                    basePlanId = result.basePlanId
                )
                scheduleRefresh(cached)
                acknowledgeIfNeeded(purchase)
            }
            VerificationResult.Unavailable -> applyOfflineCache()
            VerificationResult.Misconfigured -> {
                _entitlement.value = EntitlementState(
                    EntitlementMode.CONFIGURATION_ERROR,
                    canWrite = false
                )
            }
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged || !billingClient.isReady) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { /* Rechecked on next foreground if needed. */ }
    }

    private fun applyOfflineCache() {
        val cached = cache.read()
        _entitlement.value = EntitlementPolicy.fromCache(cached, System.currentTimeMillis())
        if (cached != null && _entitlement.value.canWrite) scheduleRefresh(cached)
    }

    private fun scheduleRefresh(cached: CachedEntitlement) {
        expiryRefreshJob?.cancel()
        val deviceExpiry = cached.verifiedAtDeviceMillis +
            (cached.entitlementExpiryMillis - cached.serverTimeMillis).coerceAtLeast(0L)
        val refreshAt = minOf(
            cached.verifiedAtDeviceMillis + EntitlementPolicy.OFFLINE_WINDOW_MILLIS,
            deviceExpiry
        )
        expiryRefreshJob = scope.launch {
            delay((refreshAt - System.currentTimeMillis()).coerceAtLeast(1_000L))
            refresh()
        }
    }

    private fun initialEntitlement(): EntitlementState =
        if (BuildConfig.DEBUG_ENTITLEMENT) {
            EntitlementState(EntitlementMode.DEBUG, canWrite = true)
        } else {
            EntitlementPolicy.fromCache(cache.read(), System.currentTimeMillis())
        }

    private fun hasOneMonthTrial(offer: ProductDetails.SubscriptionOfferDetails): Boolean =
        SubscriptionCatalog.FREE_TRIAL_TAG in offer.offerTags ||
            offer.pricingPhases.pricingPhaseList.any {
                it.priceAmountMicros == 0L && it.billingPeriod == "P1M"
            }

    private fun toPlan(offer: ProductDetails.SubscriptionOfferDetails): SubscriptionPlan? {
        val paidPhase = offer.pricingPhases.pricingPhaseList
            .lastOrNull { it.priceAmountMicros > 0L }
            ?: return null
        return SubscriptionPlan(
            basePlanId = offer.basePlanId,
            formattedPrice = paidPhase.formattedPrice,
            billingPeriod = paidPhase.billingPeriod,
            hasOneMonthFreeTrial = hasOneMonthTrial(offer)
        )
    }
}
