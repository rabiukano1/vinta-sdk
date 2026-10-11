package org.view2earn.vinta

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * VINTA Android SDK entry point (ad-network-blueprint.md section 3).
 *
 * ```
 * Vinta.init(context, "va_...", test = true) { config -> ... }
 * VintaBannerView(context).load("<unitId>")
 * VintaInterstitial().load("<unitId>", listener); interstitial.show(activity, listener)
 * VintaRewarded().load("<unitId>", "user-123", null, listener); rewarded.show(activity, listener)
 * ```
 * Rules (blueprint 2.6, 3.4): never throw into the host app, callbacks on the
 * main thread, report no-fill fast, impression once and only when visible.
 */
object Vinta {
    /** VINTA's API origin (the Convex HTTP-actions site). Overridable in init for staging. */
    const val DEFAULT_ENDPOINT = "https://site.view2earn.org"

    @JvmStatic
    val version: String get() = BuildConfig.VINTA_VERSION

    internal var appContext: Context? = null
    internal var endpoint: String = DEFAULT_ENDPOINT
    internal var appKey: String = ""
    internal var test: Boolean = false

    /** Remote config from the last successful init, or null. */
    @JvmStatic
    @Volatile
    var config: VintaConfig? = null
        private set

    @JvmStatic
    val isInitialized: Boolean get() = appContext != null && appKey.isNotEmpty()

    /**
     * Start the SDK. Safe to call more than once. [callback] gets the app
     * config (status + active ad units), or null if the app key is unknown or
     * the network failed; ads can still be requested either way.
     */
    @JvmStatic
    @JvmOverloads
    fun init(context: Context, appKey: String, test: Boolean = false, endpoint: String = DEFAULT_ENDPOINT, callback: VintaInitCallback? = null) {
        try {
            appContext = context.applicationContext
            this.appKey = appKey.trim()
            this.test = test
            this.endpoint = endpoint.trimEnd('/')
            Net.fetchConfig { c ->
                config = c
                callback?.onComplete(c)
            }
        } catch (t: Throwable) {
            Net.main { callback?.onComplete(null) }
        }
    }

    /**
     * This install's VINTA id, derived from its signing key (Identity.kt). No
     * advertising id, no personal data. Show it in your privacy settings so
     * users can ask for deletion (blueprint 13).
     */
    @JvmStatic
    val deviceId: String get() = Identity.deviceId

    /**
     * COPPA: set true when the current user/app is child-directed (like AdMob's
     * tagForChildDirectedTreatment). Only family-safe ads are served.
     */
    @JvmStatic
    @Volatile
    var childDirected: Boolean = false

    /**
     * Consent signals written by the app's CMP to the default SharedPreferences
     * (IAB TCF 2.2 and GPP standard keys).
     */
    internal fun privacy(): Map<String, Any> {
        val ctx = appContext ?: return emptyMap()
        val p = ctx.getSharedPreferences(ctx.packageName + "_preferences", Context.MODE_PRIVATE)
        val out = HashMap<String, Any>()
        runCatching { if (p.contains("IABTCF_gdprApplies")) out["gdpr"] = p.getInt("IABTCF_gdprApplies", 0) }
        runCatching { p.getString("IABTCF_TCString", null)?.let { out["tcf"] = it } }
        runCatching { p.getString("IABGPP_HDR_GppString", null)?.let { out["gpp"] = it } }
        runCatching { p.getString("IABGPP_GppSID", null)?.let { out["gppSid"] = it } }
        if (childDirected) out["coppa"] = true
        return out
    }

    /** Targeting context (blueprint 4.2), same values as the web SDK. */
    internal fun deviceContext(): Map<String, String> {
        val ctx = appContext
        val tablet = ctx != null && ctx.resources.configuration.smallestScreenWidthDp >= 600 &&
            (ctx.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
        return mapOf(
            "os" to "android",
            "device" to if (tablet) "tablet" else "phone",
            "language" to Locale.getDefault().language.take(2).lowercase(Locale.ROOT),
        )
    }
}

fun interface VintaInitCallback {
    fun onComplete(config: VintaConfig?)
}

/** [country]: ISO code VINTA detected for this device from its IP (null if unknown); used for country targeting. */
data class VintaConfig(val name: String, val status: String, val units: List<VintaUnit>, val country: String? = null) {
    /** Halal review passed: real, paid ads. Otherwise the app only gets test ads. */
    val approved: Boolean get() = status == "approved"
}

data class VintaUnit(val id: String, val name: String, val format: String, val rewardName: String?, val rewardAmount: Int?)

data class VintaReward(val name: String, val amount: Int)

/** Error codes mirror AdMob's so adapters can pass them straight through (blueprint 2.6). */
data class VintaError(val code: Int, val message: String) {
    companion object {
        const val INTERNAL = 0
        const val INVALID_REQUEST = 1
        const val NETWORK = 2
        const val NO_FILL = 3
        const val NOT_READY = 4
    }
}
