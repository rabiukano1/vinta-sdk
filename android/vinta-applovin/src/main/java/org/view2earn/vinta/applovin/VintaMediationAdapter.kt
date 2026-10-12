package org.view2earn.vinta.applovin

import android.app.Activity
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.view.ViewGroup
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.adapter.MaxAdViewAdapter
import com.applovin.mediation.adapter.MaxAdapter
import com.applovin.mediation.adapter.MaxAdapterError
import com.applovin.mediation.adapter.MaxInterstitialAdapter
import com.applovin.mediation.adapter.MaxRewardedAdapter
import com.applovin.mediation.adapter.MaxRewardedInterstitialAdapter
import com.applovin.mediation.adapter.listeners.MaxAdViewAdapterListener
import com.applovin.mediation.adapter.listeners.MaxAppOpenAdapterListener
import com.applovin.mediation.adapter.listeners.MaxInterstitialAdapterListener
import com.applovin.mediation.adapter.listeners.MaxNativeAdAdapterListener
import com.applovin.mediation.adapter.listeners.MaxRewardedAdapterListener
import com.applovin.mediation.adapter.listeners.MaxRewardedInterstitialAdapterListener
import com.applovin.mediation.adapter.parameters.MaxAdapterInitializationParameters
import com.applovin.mediation.adapter.parameters.MaxAdapterParameters
import com.applovin.mediation.adapter.parameters.MaxAdapterResponseParameters
import com.applovin.mediation.adapters.MediationAdapterBase
import com.applovin.mediation.nativeAds.MaxNativeAd
import com.applovin.sdk.AppLovinSdk
import org.view2earn.vinta.Vinta
import org.view2earn.vinta.VintaAppOpen
import org.view2earn.vinta.VintaBannerListener
import org.view2earn.vinta.VintaBannerView
import org.view2earn.vinta.VintaError
import org.view2earn.vinta.VintaFullScreenAd
import org.view2earn.vinta.VintaInterstitial
import org.view2earn.vinta.VintaLoadListener
import org.view2earn.vinta.VintaNativeAd
import org.view2earn.vinta.VintaNativeListener
import org.view2earn.vinta.VintaReward
import org.view2earn.vinta.VintaRewarded
import org.view2earn.vinta.VintaRewardedInterstitial
import org.view2earn.vinta.VintaShowListener

/**
 * AppLovin MAX custom SDK network for VINTA.
 *
 * In the MAX dashboard: Mediation → Manage → Networks → "Click here to add a
 * Custom Network" → Network type: SDK →
 *   Android adapter class name: org.view2earn.vinta.applovin.VintaMediationAdapter
 * Then on each MAX ad unit enable the custom network with
 *   App ID:        your VINTA app key (va_...)
 *   Placement ID:  the VINTA ad unit id for that format
 *   Custom Parameters (optional): {"test":true}
 * and a CPM price.
 *
 * Every callback is forwarded; nothing here may throw into the host app.
 */
class VintaMediationAdapter(sdk: AppLovinSdk) :
    MediationAdapterBase(sdk),
    MaxAdViewAdapter,
    MaxInterstitialAdapter,
    MaxRewardedAdapter,
    MaxRewardedInterstitialAdapter {

    private var banner: VintaBannerView? = null
    private var interstitial: VintaInterstitial? = null
    private var rewarded: VintaRewarded? = null
    private var rewardedInterstitial: VintaRewardedInterstitial? = null
    private var appOpen: VintaAppOpen? = null
    private var native: VintaNativeAd? = null

    override fun getSdkVersion(): String = Vinta.version
    override fun getAdapterVersion(): String = BuildConfig.ADAPTER_VERSION

    private fun appKey(p: MaxAdapterParameters): String? =
        p.serverParameters?.getString("app_id")?.takeIf { it.isNotBlank() }

    private fun test(p: MaxAdapterParameters): Boolean =
        p.isTesting || p.customParameters?.getBoolean("test", false) == true ||
            p.customParameters?.getString("test") == "true"

    private fun context(activity: Activity?): Context = activity ?: applicationContext

    /** Lazily starts VINTA (MAX may load before initialize finished) and applies the COPPA flag. */
    private fun prepare(p: MaxAdapterParameters, activity: Activity?): Boolean {
        val key = appKey(p) ?: return false
        if (!Vinta.isInitialized) Vinta.init(context(activity), key, test(p))
        // COPPA: MAX's age-restricted user flag → family-safe ads only.
        Vinta.childDirected = p.isAgeRestrictedUser == true
        return true
    }

    private fun unit(p: MaxAdapterResponseParameters): String? = p.thirdPartyAdPlacementId?.takeIf { it.isNotBlank() }

    private fun maxError(e: VintaError): MaxAdapterError {
        val base = when (e.code) {
            VintaError.NO_FILL -> MaxAdapterError.NO_FILL
            VintaError.NETWORK -> MaxAdapterError.NO_CONNECTION
            VintaError.INVALID_REQUEST -> MaxAdapterError.INVALID_CONFIGURATION
            VintaError.NOT_READY -> MaxAdapterError.AD_NOT_READY
            else -> MaxAdapterError.UNSPECIFIED
        }
        return MaxAdapterError(base, e.code, e.message)
    }

    private val badConfig = MaxAdapterError(MaxAdapterError.INVALID_CONFIGURATION, 0, "VINTA: set App ID (app key) and Placement ID (ad unit id) in MAX")

    // ── Init ─────────────────────────────────────────────────────────────────

    override fun initialize(params: MaxAdapterInitializationParameters, activity: Activity?, onCompletion: MaxAdapter.OnCompletionListener) {
        try {
            val key = appKey(params)
            if (key == null) {
                onCompletion.onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_FAILURE, "VINTA: missing App ID (your VINTA app key)")
                return
            }
            Vinta.init(context(activity), key, test(params)) {
                onCompletion.onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_SUCCESS, null)
            }
        } catch (t: Throwable) {
            onCompletion.onCompletion(MaxAdapter.InitializationStatus.INITIALIZED_FAILURE, "VINTA: ${t.message}")
        }
    }

    override fun onDestroy() {
        banner?.destroy()
        native?.unregister()
        banner = null
        interstitial = null
        rewarded = null
        rewardedInterstitial = null
        appOpen = null
        native = null
    }

    // ── Banner / MREC ────────────────────────────────────────────────────────

    override fun loadAdViewAd(params: MaxAdapterResponseParameters, format: MaxAdFormat, activity: Activity?, listener: MaxAdViewAdapterListener) {
        try {
            val unitId = unit(params)
            if (unitId == null || !prepare(params, activity)) return listener.onAdViewAdLoadFailed(badConfig)
            // MAX owns banner refresh in mediation: leave refresh off on this VINTA unit.
            val view = VintaBannerView(context(activity))
            banner = view
            view.load(unitId, object : VintaBannerListener {
                override fun onLoaded() = listener.onAdViewAdLoaded(view)
                override fun onFailed(error: VintaError) = listener.onAdViewAdLoadFailed(maxError(error))
                override fun onImpression() = listener.onAdViewAdDisplayed()
                override fun onClicked() = listener.onAdViewAdClicked()
            })
        } catch (t: Throwable) {
            listener.onAdViewAdLoadFailed(MaxAdapterError(MaxAdapterError.INTERNAL_ERROR, 0, t.message ?: "banner error"))
        }
    }

    // ── Full screen (shared) ─────────────────────────────────────────────────

    private fun loadFullScreen(params: MaxAdapterResponseParameters, activity: Activity?, onLoaded: () -> Unit, onFailed: (MaxAdapterError) -> Unit, load: (String, VintaLoadListener) -> Unit) {
        try {
            val unitId = unit(params)
            if (unitId == null || !prepare(params, activity)) return onFailed(badConfig)
            load(unitId) { err -> if (err == null) onLoaded() else onFailed(maxError(err)) }
        } catch (t: Throwable) {
            onFailed(MaxAdapterError(MaxAdapterError.INTERNAL_ERROR, 0, t.message ?: "load error"))
        }
    }

    private fun showFullScreen(ad: VintaFullScreenAd?, activity: Activity?, listener: VintaShowListener, onFailed: (MaxAdapterError) -> Unit) {
        try {
            if (ad == null || !ad.isReady()) return onFailed(MaxAdapterError(MaxAdapterError.AD_NOT_READY, 0, "VINTA ad not ready or expired"))
            ad.show(context(activity), listener)
        } catch (t: Throwable) {
            onFailed(MaxAdapterError(MaxAdapterError.AD_DISPLAY_FAILED, 0, t.message ?: "show error"))
        }
    }

    // ── Interstitial ─────────────────────────────────────────────────────────

    override fun loadInterstitialAd(params: MaxAdapterResponseParameters, activity: Activity?, listener: MaxInterstitialAdapterListener) {
        val ad = VintaInterstitial().also { interstitial = it }
        loadFullScreen(params, activity, { listener.onInterstitialAdLoaded() }, listener::onInterstitialAdLoadFailed) { unitId, l -> ad.load(unitId, l) }
    }

    override fun showInterstitialAd(params: MaxAdapterResponseParameters, activity: Activity?, listener: MaxInterstitialAdapterListener) =
        showFullScreen(interstitial, activity, object : VintaShowListener {
            override fun onShown() = listener.onInterstitialAdDisplayed()
            override fun onShowFailed(error: VintaError) = listener.onInterstitialAdDisplayFailed(MaxAdapterError(MaxAdapterError.AD_DISPLAY_FAILED, error.code, error.message))
            override fun onClicked() = listener.onInterstitialAdClicked()
            override fun onClosed() = listener.onInterstitialAdHidden()
        }, listener::onInterstitialAdDisplayFailed)

    // ── App open ─────────────────────────────────────────────────────────────

    override fun loadAppOpenAd(params: MaxAdapterResponseParameters, activity: Activity?, listener: MaxAppOpenAdapterListener) {
        val ad = VintaAppOpen().also { appOpen = it }
        loadFullScreen(params, activity, { listener.onAppOpenAdLoaded() }, listener::onAppOpenAdLoadFailed) { unitId, l -> ad.load(unitId, l) }
    }

    override fun showAppOpenAd(params: MaxAdapterResponseParameters, activity: Activity?, listener: MaxAppOpenAdapterListener) =
        showFullScreen(appOpen, activity, object : VintaShowListener {
            override fun onShown() = listener.onAppOpenAdDisplayed()
            override fun onShowFailed(error: VintaError) = listener.onAppOpenAdDisplayFailed(MaxAdapterError(MaxAdapterError.AD_DISPLAY_FAILED, error.code, error.message))
            override fun onClicked() = listener.onAppOpenAdClicked()
            override fun onClosed() = listener.onAppOpenAdHidden()
        }, listener::onAppOpenAdDisplayFailed)

    // ── Rewarded ─────────────────────────────────────────────────────────────
    // MAX does its own server-side reward callbacks for mediated networks, so
    // the reward amount/label come from the MAX ad unit (configureReward).

    override fun loadRewardedAd(params: MaxAdapterResponseParameters, activity: Activity?, listener: MaxRewardedAdapterListener) {
        configureReward(params)
        val ad = VintaRewarded().also { rewarded = it }
        loadFullScreen(params, activity, { listener.onRewardedAdLoaded() }, listener::onRewardedAdLoadFailed) { unitId, l -> ad.load(unitId, null, null, l) }
    }

    override fun showRewardedAd(params: MaxAdapterResponseParameters, activity: Activity?, listener: MaxRewardedAdapterListener) {
        var earned = false
        showFullScreen(rewarded, activity, object : VintaShowListener {
            override fun onShown() = listener.onRewardedAdDisplayed()
            override fun onShowFailed(error: VintaError) = listener.onRewardedAdDisplayFailed(MaxAdapterError(MaxAdapterError.AD_DISPLAY_FAILED, error.code, error.message))
            override fun onClicked() = listener.onRewardedAdClicked()
            override fun onRewarded(reward: VintaReward) { earned = true }
            override fun onClosed() {
                if (earned || shouldAlwaysRewardUser()) listener.onUserRewarded(reward)
                listener.onRewardedAdHidden()
            }
        }, listener::onRewardedAdDisplayFailed)
    }

    // ── Rewarded interstitial ────────────────────────────────────────────────

    override fun loadRewardedInterstitialAd(params: MaxAdapterResponseParameters, activity: Activity?, listener: MaxRewardedInterstitialAdapterListener) {
        configureReward(params)
        val ad = VintaRewardedInterstitial().also { rewardedInterstitial = it }
        loadFullScreen(params, activity, { listener.onRewardedInterstitialAdLoaded() }, listener::onRewardedInterstitialAdLoadFailed) { unitId, l -> ad.load(unitId, null, null, l) }
    }

    override fun showRewardedInterstitialAd(params: MaxAdapterResponseParameters, activity: Activity?, listener: MaxRewardedInterstitialAdapterListener) {
        var earned = false
        showFullScreen(rewardedInterstitial, activity, object : VintaShowListener {
            override fun onShown() = listener.onRewardedInterstitialAdDisplayed()
            override fun onShowFailed(error: VintaError) = listener.onRewardedInterstitialAdDisplayFailed(MaxAdapterError(MaxAdapterError.AD_DISPLAY_FAILED, error.code, error.message))
            override fun onClicked() = listener.onRewardedInterstitialAdClicked()
            override fun onRewarded(reward: VintaReward) { earned = true }
            override fun onClosed() {
                if (earned || shouldAlwaysRewardUser()) listener.onUserRewarded(reward)
                listener.onRewardedInterstitialAdHidden()
            }
        }, listener::onRewardedInterstitialAdDisplayFailed)
    }

    // ── Native ───────────────────────────────────────────────────────────────

    override fun loadNativeAd(params: MaxAdapterResponseParameters, activity: Activity?, listener: MaxNativeAdAdapterListener) {
        try {
            val unitId = unit(params)
            if (unitId == null || !prepare(params, activity)) return listener.onNativeAdLoadFailed(badConfig)
            val res = context(activity).resources
            VintaNativeAd.load(unitId) { loaded, err ->
                if (loaded == null) return@load listener.onNativeAdLoadFailed(maxError(err ?: VintaError(VintaError.INTERNAL, "native error")))
                native = loaded
                val builder = MaxNativeAd.Builder()
                    .setAdFormat(MaxAdFormat.NATIVE)
                    .setTitle(loaded.title)
                    .setBody(loaded.body)
                    .setCallToAction(loaded.callToAction)
                    .setAdvertiser(loaded.advertiser)
                loaded.icon?.let { builder.setIcon(MaxNativeAd.MaxNativeAdImage(BitmapDrawable(res, it))) }
                loaded.image?.let { builder.setMainImage(MaxNativeAd.MaxNativeAdImage(BitmapDrawable(res, it))) }
                listener.onNativeAdLoaded(VintaMaxNativeAd(builder, loaded, listener), null)
            }
        } catch (t: Throwable) {
            listener.onNativeAdLoadFailed(MaxAdapterError(MaxAdapterError.INTERNAL_ERROR, 0, t.message ?: "native error"))
        }
    }

    /** VINTA counts the impression (50% visible for 1 s) and handles taps, then tells MAX. */
    private class VintaMaxNativeAd(
        builder: Builder,
        private val vinta: VintaNativeAd,
        private val listener: MaxNativeAdAdapterListener,
    ) : MaxNativeAd(builder) {
        override fun prepareForInteraction(clickableViews: MutableList<View>, container: ViewGroup): Boolean {
            vinta.registerView(container, object : VintaNativeListener {
                override fun onImpression() = listener.onNativeAdDisplayed(null)
                override fun onClicked() = listener.onNativeAdClicked()
            })
            for (v in clickableViews) v.setOnClickListener {
                vinta.performClick(it.context)
                listener.onNativeAdClicked()
            }
            return true
        }
    }
}
