package com.cleanzip.app.billing

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Compras dentro de la app con Google Play Billing:
 * - Una compra única (no consumible) para quitar los anuncios.
 * - Donaciones (consumibles, se pueden repetir).
 *
 * Los ID deben coincidir con los productos creados en Play Console
 * (Monetizar → Productos → Productos de compra única).
 */
object BillingManager {

    // ─── ID de los productos (cámbialos aquí si usas otros) ────────────
    const val REMOVE_ADS = "remove_ads"
    val DONATIONS = listOf("donation_small", "donation_medium", "donation_large")
    // ───────────────────────────────────────────────────────────────────

    private const val PREFS = "billing"
    private const val KEY_ADS_REMOVED = "ads_removed"

    /** true si el usuario ha comprado «Quitar anuncios». */
    var adsRemoved by mutableStateOf(false)
        private set

    /** Productos disponibles en Play, por ID. Vacío si Play Billing no está disponible. */
    val products = mutableStateMapOf<String, ProductDetails>()

    /** Mensaje breve tras una compra (por ejemplo, agradecimiento). */
    var message by mutableStateOf<String?>(null)

    private lateinit var appContext: Context
    private var client: BillingClient? = null

    fun init(context: Context) {
        if (client != null) return
        appContext = context.applicationContext
        adsRemoved = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ADS_REMOVED, false)

        client = BillingClient.newBuilder(appContext)
            .setListener { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases?.forEach(::handlePurchase)
                } else if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                    restorePurchases()
                }
            }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        connect()
    }

    private fun connect() {
        val c = client ?: return
        if (c.isReady) return
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    loadProducts()
                    restorePurchases()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Se reconecta la próxima vez que haga falta.
            }
        })
    }

    private fun loadProducts() {
        val list = (listOf(REMOVE_ADS) + DONATIONS).map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(ProductType.INAPP)
                .build()
        }
        client?.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(list).build()
        ) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                details.forEach { products[it.productId] = it }
            }
        }
    }

    /** Comprueba compras anteriores (por ejemplo, tras reinstalar la app). */
    private fun restorePurchases() {
        client?.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync
            val owned = purchases.any {
                REMOVE_ADS in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            saveAdsRemoved(owned)
            purchases.forEach { handlePurchase(it, silent = true) }
        }
    }

    fun buy(activity: Activity, productId: String) {
        val details = products[productId]
        if (client?.isReady != true || details == null) {
            connect()
            message = "Las compras no están disponibles ahora. Inténtalo de nuevo más tarde."
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build())
            )
            .build()
        client?.launchBillingFlow(activity, params)
    }

    private fun handlePurchase(purchase: Purchase, silent: Boolean = false) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        val c = client ?: return

        if (REMOVE_ADS in purchase.products) {
            saveAdsRemoved(true)
            if (!purchase.isAcknowledged) {
                c.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                ) {}
                if (!silent) message = "¡Gracias! Los anuncios se han eliminado."
            }
        }
        if (purchase.products.any { it in DONATIONS }) {
            // Las donaciones se consumen para poder repetirse.
            c.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()) { _, _ -> }
            if (!silent) message = "¡Muchas gracias por tu apoyo! ❤"
        }
    }

    private fun saveAdsRemoved(value: Boolean) {
        adsRemoved = value
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ADS_REMOVED, value).apply()
    }
}
