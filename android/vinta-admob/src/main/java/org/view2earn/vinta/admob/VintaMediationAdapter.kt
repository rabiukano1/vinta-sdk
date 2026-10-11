package org.view2earn.vinta.admob

import android.content.Context
import android.os.Bundle
import android.view.View
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.VersionInfo
import com.google.android.gms.ads.mediation.Adapter
import com.google.android.gms.ads.mediation.InitializationCompleteCallback
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationBannerAd
import com.google.android.gms.ads.mediation.MediationBannerAdCallback
import com.google.android.gms.ads.mediation.MediationBannerAdConfiguration
import com.google.android.gms.ads.mediation.MediationConfiguration
import com.google.android.gms.ads.mediation.MediationInterstitialAd
import com.google.android.gms.ads.mediation.MediationInterstitialAdCallback
import com.google.android.gms.ads.mediation.MediationInterstitialAdConfiguration
import com.google.android.gms.ads.mediation.MediationRewardedAd
import com.google.android.gms.ads.mediation.MediationRewardedAdCallback
import com.google.android.gms.ads.mediation.MediationRewardedAdConfiguration
import org.json.JSONObject
import org.view2earn.vinta.Vinta
import org.view2earn.vinta.VintaBannerListener
import org.view2earn.vinta.VintaBannerView
import org.view2earn.vinta.VintaError
import org.view2earn.vinta.VintaInterstitial
import org.view2earn.vinta.VintaReward
import org.view2earn.vinta.VintaRewarded
import org.view2earn.vinta.VintaShowListener
import org.view2earn.vinta.VintaAppOpen
import org.view2earn.vinta.VintaFullScreenAd
import org.view2earn.vinta.VintaNativeAd
import org.view2earn.vinta.VintaRewardedInterstitial
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.mediation.MediationAppOpenAd
import com.google.android.gms.ads.mediation.MediationAppOpenAdCallback
import com.google.android.gms.ads.mediation.MediationAppOpenAdConfiguration
import com.google.android.gms.ads.mediation.MediationNativeAdCallback
import com.google.android.gms.ads.mediation.MediationNativeAdConfiguration
import com.google.android.gms.ads.mediation.NativeAdMapper

/**
 * AdMob mediation custom event for VINTA (blueprint 2.2, waterfall).
 *
 * In the AdMob UI: Mediation group → Add custom event →
 *   Class name: org.view2earn.vinta.admob.VintaMediationAdapter
 *   Parameter:  {"appKey":"va_...","unitId":"<VINTA ad unit id>"}   (optional "test":true)
 * and a manual eCPM.
 *
 * Every callback is forwarded (loaded, failed, shown, clicked, impression,
 * closed, reward); nothing here may throw into the host app (blueprint 2.6).
 */
class VintaMediationAdapter : Adapter() {
    companion object {
        const val ERROR_DOMAIN = "org.view2earn.vinta"
    }

    private class Params(val appKey: String, val unitId: String, val test: Boolean)

    private fun params(server: Bundle?): Params? = runCatching {
        val j = JSONObject(server?.getString(MediationConfiguration.CUSTOM_EVENT_SERVER_PARAMETER_FIELD) ?: return null)
        Params(j.getString("appKey"), j.optString("unitId"), j.optBoolean("test", false))
    }.getOrNull()

    private fun error(code: Int, msg: String) = AdError(code, msg, ERROR_DOMAIN)
    private fun error(e: VintaError) = error(e.code, e.message)

    private fun ensureInit(context: Context, p: Params) {
        if (!Vinta.isInitialized) Vinta.init(context, p.appKey, p.test)
    }

    // COPPA: pass AdMob's child-directed tag through to VINTA (family-safe ads only).
    private fun applyTags(config: com.google.android.gms.ads.mediation.MediationAdConfiguration) {
        Vinta.childDirected = config.taggedForChildDirectedTreatment() ==
            com.google.android.gms.ads.RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE
    }

    private fun version(v: String): VersionInfo {
        // "1.4.0.0" → VersionInfo(1, 4, 0 * 100 + 0), AdMob's adapter convention.
        val n = v.split(".").map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0, 0)
        return VersionInfo(n[0], n[1], n[2] * 100 + n[3])
    }

    override fun getVersionInfo(): VersionInfo = version(BuildConfig.ADAPTER_VERSION)
    override fun getSDKVersionInfo(): VersionInfo = version(Vinta.version)

    override fun initialize(context: Context, callback: InitializationCompleteCallback, configurations: MutableList<MediationConfiguration>) {
        try {
            val p = configurations.firstNotNullOfOrNull { params(it.serverParameters) }
            if (p == null) return callback.onInitializationFailed("VINTA: missing or invalid custom event parameter {\"appKey\":...,\"unitId\":...}")
            Vinta.init(context, p.appKey, p.test) { callback.onInitializationSucceeded() }
        } catch (t: Throwable) {
            callback.onInitializationFailed("VINTA: ${t.message}")
        }
    }

    // ── Banner ───────────────────────────────────────────────────────────────

    override fun loadBannerAd(
        config: MediationBannerAdConfiguration,
        callback: MediationAdLoadCallback<MediationBannerAd, MediationBannerAdCallback>,
    ) {
        try {
            val p = params(config.serverParameters) ?: return callback.onFailure(error(VintaError.INVALID_REQUEST, "Invalid VINTA parameter"))
            ensureInit(config.context, p)
            applyTags(config)
            // AdMob owns banner refresh in mediation: leave refresh off on this VINTA unit.
            val view = VintaBannerView(config.context)
            var adCallback: MediationBannerAdCallback? = null
            val ad = object : MediationBannerAd {
                override fun getView(): View = view
            }
            view.load(p.unitId, object : VintaBannerListener {
                override fun onLoaded() { adCallback = callback.onSuccess(ad) }
                override fun onFailed(error: VintaError) { callback.onFailure(error(error)) }
                override fun onImpression() { adCallback?.reportAdImpression() }
                override fun onClicked() {
                    adCallback?.reportAdClicked()
                    adCallback?.onAdLeftApplication()
                }
            })
        } catch (t: Throwable) {
            callback.onFailure(error(VintaError.INTERNAL, t.message ?: "banner error"))
        }
    }

    // ── Interstitial ─────────────────────────────────────────────────────────

    override fun loadInterstitialAd(
        config: MediationInterstitialAdConfiguration,
        callback: MediationAdLoadCallback<MediationInterstitialAd, MediationInterstitialAdCallback>,
    ) {
        try {
            val p = params(config.serverParameters) ?: return callback.onFailure(error(VintaError.INVALID_REQUEST, "Invalid VINTA parameter"))
            ensureInit(config.context, p)
            applyTags(config)
            val vinta = VintaInterstitial()
            var adCallback: MediationInterstitialAdCallback? = null
            val ad = object : MediationInterstitialAd {
                override fun showAd(context: Context) {
                    vinta.show(context, object : VintaShowListener {
                        override fun onShown() {
                            adCallback?.onAdOpened()
                            adCallback?.reportAdImpression()
                        }
                        override fun onShowFailed(error: VintaError) { adCallback?.onAdFailedToShow(error(error)) }
                        override fun onClicked() { adCallback?.reportAdClicked() }
                        override fun onClosed() { adCallback?.onAdClosed() }
                    })
                }
            }
            vinta.load(p.unitId) { err -> if (err == null) adCallback = callback.onSuccess(ad) else callback.onFailure(error(err)) }
        } catch (t: Throwable) {
            callback.onFailure(error(VintaError.INTERNAL, t.message ?: "interstitial error"))
        }
    }

    // ── Rewarded ─────────────────────────────────────────────────────────────

    override fun loadRewardedAd(
        config: MediationRewardedAdConfiguration,
        callback: MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback>,
    ) = loadRewardedLike(config, callback, interstitial = false)

    // Rewarded interstitial: same callbacks; VINTA shows its intro + skip first.
    override fun loadRewardedInterstitialAd(
        config: MediationRewardedAdConfiguration,
        callback: MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback>,
    ) = loadRewardedLike(config, callback, interstitial = true)

    private fun loadRewardedLike(
        config: MediationRewardedAdConfiguration,
        callback: MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback>,
        interstitial: Boolean,
    ) {
        try {
            val p = params(config.serverParameters) ?: return callback.onFailure(error(VintaError.INVALID_REQUEST, "Invalid VINTA parameter"))
            ensureInit(config.context, p)
            applyTags(config)
            val ri = if (interstitial) VintaRewardedInterstitial() else null
            val rw = if (interstitial) null else VintaRewarded()
            val vinta: VintaFullScreenAd = ri ?: rw!!
            var adCallback: MediationRewardedAdCallback? = null
            val ad = object : MediationRewardedAd {
                override fun showAd(context: Context) {
                    vinta.show(context, object : VintaShowListener {
                        override fun onShown() {
                            adCallback?.onAdOpened()
                            adCallback?.onVideoStart()
                            adCallback?.reportAdImpression()
                        }
                        override fun onShowFailed(error: VintaError) { adCallback?.onAdFailedToShow(error(error)) }
                        override fun onClicked() { adCallback?.reportAdClicked() }
                        override fun onRewarded(reward: VintaReward) {
                            adCallback?.onVideoComplete()
                            // GMA 24: the reward amount/type come from the AdMob ad unit, so
                            // set them there to match this VINTA unit (blueprint 2.6).
                            adCallback?.onUserEarnedReward()
                        }
                        override fun onClosed() { adCallback?.onAdClosed() }
                    })
                }
            }
            // AdMob does its own SSV for mediated rewards, so no VINTA user id here.
            val onLoad = org.view2earn.vinta.VintaLoadListener { err -> if (err == null) adCallback = callback.onSuccess(ad) else callback.onFailure(error(err)) }
            if (ri != null) ri.load(p.unitId, null, null, onLoad) else rw!!.load(p.unitId, null, null, onLoad)
        } catch (t: Throwable) {
            callback.onFailure(error(VintaError.INTERNAL, t.message ?: "rewarded error"))
        }
    }

    // ── App open ─────────────────────────────────────────────────────────────

    override fun loadAppOpenAd(
        config: MediationAppOpenAdConfiguration,
        callback: MediationAdLoadCallback<MediationAppOpenAd, MediationAppOpenAdCallback>,
    ) {
        try {
            val p = params(config.serverParameters) ?: return callback.onFailure(error(VintaError.INVALID_REQUEST, "Invalid VINTA parameter"))
            ensureInit(config.context, p)
            applyTags(config)
            val vinta = VintaAppOpen()
            var adCallback: MediationAppOpenAdCallback? = null
            val ad = object : MediationAppOpenAd {
                override fun showAd(context: Context) {
                    vinta.show(context, object : VintaShowListener {
                        override fun onShown() {
                            adCallback?.onAdOpened()
                            adCallback?.reportAdImpression()
                        }
                        override fun onShowFailed(error: VintaError) { adCallback?.onAdFailedToShow(error(error)) }
                        override fun onClicked() { adCallback?.reportAdClicked() }
                        override fun onClosed() { adCallback?.onAdClosed() }
                    })
                }
            }
            vinta.load(p.unitId) { err -> if (err == null) adCallback = callback.onSuccess(ad) else callback.onFailure(error(err)) }
        } catch (t: Throwable) {
            callback.onFailure(error(VintaError.INTERNAL, t.message ?: "app open error"))
        }
    }

    // ── Native ───────────────────────────────────────────────────────────────

    private class Image(private val d: Drawable?) : NativeAd.Image() {
        override fun getDrawable(): Drawable? = d
        override fun getUri(): Uri = Uri.EMPTY
        override fun getScale(): Double = 1.0
    }

    override fun loadNativeAdMapper(
        config: MediationNativeAdConfiguration,
        callback: MediationAdLoadCallback<NativeAdMapper, MediationNativeAdCallback>,
    ) {
        try {
            val p = params(config.serverParameters) ?: return callback.onFailure(error(VintaError.INVALID_REQUEST, "Invalid VINTA parameter"))
            ensureInit(config.context, p)
            applyTags(config)
            VintaNativeAd.load(p.unitId) { native, err ->
                if (native == null) return@load callback.onFailure(error(err ?: VintaError(VintaError.INTERNAL, "native error")))
                val res = config.context.resources
                var adCallback: MediationNativeAdCallback? = null
                val mapper = object : NativeAdMapper() {
                    init {
                        headline = native.title
                        body = native.body
                        callToAction = native.callToAction
                        advertiser = native.advertiser
                        native.icon?.let { icon = Image(BitmapDrawable(res, it)) }
                        native.image?.let { images = listOf<NativeAd.Image>(Image(BitmapDrawable(res, it))) }
                        // VINTA tracks clicks/impressions itself and tells AdMob.
                        overrideClickHandling = true
                        overrideImpressionRecording = true
                    }
                    override fun handleClick(view: View) {
                        native.performClick(view.context)
                        adCallback?.reportAdClicked()
                        adCallback?.onAdOpened()
                        adCallback?.onAdLeftApplication()
                    }
                    override fun recordImpression() {
                        native.recordImpression()
                        adCallback?.reportAdImpression()
                    }
                }
                adCallback = callback.onSuccess(mapper)
            }
        } catch (t: Throwable) {
            callback.onFailure(error(VintaError.INTERNAL, t.message ?: "native error"))
        }
    }
}
