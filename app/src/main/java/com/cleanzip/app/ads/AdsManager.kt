package com.cleanzip.app.ads

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pide el consentimiento (GDPR) con la plataforma de mensajes de Google (UMP)
 * y solo después inicializa AdMob.
 */
object AdsManager {

    private val initialized = AtomicBoolean(false)

    /** true cuando ya se pueden mostrar anuncios. */
    var adsReady by mutableStateOf(false)
        private set

    /** true si el usuario debe poder cambiar su consentimiento desde la app. */
    var privacyOptionsRequired by mutableStateOf(false)
        private set

    fun start(activity: Activity) {
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()

        consent.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                    updatePrivacyStatus(consent)
                    if (consent.canRequestAds()) initialize(activity)
                }
            },
            { _ -> if (consent.canRequestAds()) initialize(activity) }
        )

        // Si ya había consentimiento de una sesión anterior, empezamos sin esperar.
        if (consent.canRequestAds()) initialize(activity)
    }

    /** Abre el formulario para cambiar las opciones de privacidad de los anuncios. */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ ->
            val consent = UserMessagingPlatform.getConsentInformation(activity)
            updatePrivacyStatus(consent)
            if (consent.canRequestAds()) initialize(activity)
        }
    }

    private fun updatePrivacyStatus(consent: ConsentInformation) {
        privacyOptionsRequired = consent.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun initialize(activity: Activity) {
        if (!initialized.compareAndSet(false, true)) return
        MobileAds.initialize(activity.applicationContext) {
            activity.runOnUiThread { adsReady = true }
        }
    }
}
