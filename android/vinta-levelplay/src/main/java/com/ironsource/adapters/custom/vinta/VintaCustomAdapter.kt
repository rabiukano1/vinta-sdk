package com.ironsource.adapters.custom.vinta

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import com.ironsource.mediationsdk.ISBannerSize
import com.ironsource.mediationsdk.adunit.adapter.BaseAdapter
import com.ironsource.mediationsdk.adunit.adapter.BaseBanner
import com.ironsource.mediationsdk.adunit.adapter.BaseInterstitial
import com.ironsource.mediationsdk.adunit.adapter.BaseRewardedVideo
import com.ironsource.mediationsdk.adunit.adapter.internal.listener.AdapterAdInteractionListener
import com.ironsource.mediationsdk.adunit.adapter.internal.listener.AdapterAdListener
import com.ironsource.mediationsdk.adunit.adapter.listener.BannerAdListener
import com.ironsource.mediationsdk.adunit.adapter.listener.InterstitialAdListener
import com.ironsource.mediationsdk.adunit.adapter.listener.NetworkInitializationListener
import com.ironsource.mediationsdk.adunit.adapter.listener.RewardedVideoAdListener
import com.ironsource.mediationsdk.adunit.adapter.utility.AdData
import com.ironsource.mediationsdk.adunit.adapter.utility.AdapterErrorType
import com.ironsource.mediationsdk.adunit.adapter.utility.AdapterErrors
import com.ironsource.mediationsdk.model.NetworkSettings
import org.view2earn.vinta.Vinta
import org.view2earn.vinta.VintaBannerListener
import org.view2earn.vinta.VintaBannerView
import org.view2earn.vinta.VintaError
import org.view2earn.vinta.VintaFullScreenAd
import org.view2earn.vinta.VintaInterstitial
import org.view2earn.vinta.VintaReward
import org.view2earn.vinta.VintaRewarded
import org.view2earn.vinta.VintaShowListener
import org.view2earn.vinta.levelplay.BuildConfig

// Unity LevelPlay custom adapter for VINTA. LevelPlay finds these classes by
// package + name, as registered with Unity (custom adapter form):
//   package:   com.ironsource.adapters.custom.vinta
//   classes:   VintaCustomAdapter, VintaCustomInterstitial,
//              VintaCustomRewardedVideo, VintaCustomBanner
//   app key:   "appKey"  (VINTA app key, va_...)
//   instance:  "unitId"  (VINTA ad unit id of the same format), optional "test" = "true"
// LevelPlay custom adapters support interstitial, rewarded video and banner.
// Nothing here may throw into the host app.

private const val KEY_APP = "appKey"
private const val KEY_UNIT = "unitId"

private fun AdData.str(key: String): String? = runCatching { getString(key) }.getOrNull()?.takeIf { it.isNotBlank() }
private fun AdData.test(): Boolean = runCatching { getBoolean("test") == true || getString("test") == "true" }.getOrDefault(false)

private fun errorType(e: VintaError) =
    if (e.code == VintaError.NO_FILL) AdapterErrorType.ADAPTER_ERROR_TYPE_NO_FILL else AdapterErrorType.ADAPTER_ERROR_TYPE_INTERNAL

private fun AdapterAdListener.missingParams() =
    onAdLoadFailed(AdapterErrorType.ADAPTER_ERROR_TYPE_INTERNAL, AdapterErrors.ADAPTER_ERROR_MISSING_PARAMS, "VINTA: set appKey and unitId for this instance")

/** Starts VINTA lazily (LevelPlay may load before init finished). */
private fun ensureInit(adData: AdData, context: Context): Boolean {
    val key = adData.str(KEY_APP) ?: return Vinta.isInitialized
    if (!Vinta.isInitialized) Vinta.init(context, key, adData.test())
    return true
}

class VintaCustomAdapter : BaseAdapter() {
    override fun init(adData: AdData, context: Context, listener: NetworkInitializationListener?) {
        try {
            val key = adData.str(KEY_APP)
            if (key == null) {
                listener?.onInitFailed(AdapterErrors.ADAPTER_ERROR_MISSING_PARAMS, "VINTA: missing appKey (your VINTA app key)")
                return
            }
            // May be called several times: VINTA's init is safe to repeat.
            Vinta.init(context, key, adData.test()) { listener?.onInitSuccess() }
        } catch (t: Throwable) {
            listener?.onInitFailed(AdapterErrors.ADAPTER_ERROR_INTERNAL, "VINTA: ${t.message}")
        }
    }

    override fun getNetworkSDKVersion(): String = Vinta.version
    override fun getAdapterVersion(): String = BuildConfig.ADAPTER_VERSION
}

/** Shared full-screen show logic: LevelPlay's interstitial and rewarded listeners share these callbacks. */
private fun show(ad: VintaFullScreenAd?, activity: Activity, listener: AdapterAdInteractionListener, onRewarded: () -> Unit = {}) {
    try {
        if (ad == null || !ad.isReady()) return listener.onAdShowFailed(AdapterErrors.ADAPTER_ERROR_AD_EXPIRED, "VINTA ad not ready or expired")
        ad.show(activity, object : VintaShowListener {
            override fun onShown() {
                listener.onAdOpened()
                listener.onAdVisible()
                listener.onAdStarted()
            }
            override fun onShowFailed(error: VintaError) = listener.onAdShowFailed(error.code, error.message)
            override fun onClicked() = listener.onAdClicked()
            override fun onRewarded(reward: VintaReward) = onRewarded()
            override fun onClosed() {
                listener.onAdEnded()
                listener.onAdClosed()
            }
        })
    } catch (t: Throwable) {
        listener.onAdShowFailed(AdapterErrors.ADAPTER_ERROR_INTERNAL, t.message ?: "show error")
    }
}

class VintaCustomInterstitial(settings: NetworkSettings) : BaseInterstitial<VintaCustomAdapter>(settings) {
    private var ad: VintaInterstitial? = null

    override fun loadAd(adData: AdData, context: Context, listener: InterstitialAdListener) {
        try {
            val unitId = adData.str(KEY_UNIT)
            if (unitId == null || !ensureInit(adData, context)) return listener.missingParams()
            val vinta = VintaInterstitial().also { ad = it }
            vinta.load(unitId) { e -> if (e == null) listener.onAdLoadSuccess() else listener.onAdLoadFailed(errorType(e), e.code, e.message) }
        } catch (t: Throwable) {
            listener.onAdLoadFailed(AdapterErrorType.ADAPTER_ERROR_TYPE_INTERNAL, AdapterErrors.ADAPTER_ERROR_INTERNAL, t.message ?: "load error")
        }
    }

    override fun showAd(adData: AdData, activity: Activity, listener: InterstitialAdListener) = show(ad, activity, listener)
    override fun isAdAvailable(adData: AdData): Boolean = ad?.isReady() == true
    override fun destroyAd(adData: AdData) { ad = null }
}

// LevelPlay does its own server-side reward callbacks for mediated networks,
// so the reward name/amount come from the LevelPlay placement.
class VintaCustomRewardedVideo(settings: NetworkSettings) : BaseRewardedVideo<VintaCustomAdapter>(settings) {
    private var ad: VintaRewarded? = null

    override fun loadAd(adData: AdData, context: Context, listener: RewardedVideoAdListener) {
        try {
            val unitId = adData.str(KEY_UNIT)
            if (unitId == null || !ensureInit(adData, context)) return listener.missingParams()
            val vinta = VintaRewarded().also { ad = it }
            vinta.load(unitId, null, null) { e -> if (e == null) listener.onAdLoadSuccess() else listener.onAdLoadFailed(errorType(e), e.code, e.message) }
        } catch (t: Throwable) {
            listener.onAdLoadFailed(AdapterErrorType.ADAPTER_ERROR_TYPE_INTERNAL, AdapterErrors.ADAPTER_ERROR_INTERNAL, t.message ?: "load error")
        }
    }

    override fun showAd(adData: AdData, activity: Activity, listener: RewardedVideoAdListener) =
        show(ad, activity, listener) { listener.onAdRewarded() }

    override fun isAdAvailable(adData: AdData): Boolean = ad?.isReady() == true
    override fun destroyAd(adData: AdData) { ad = null }
}

class VintaCustomBanner(settings: NetworkSettings) : BaseBanner<VintaCustomAdapter>(settings) {
    private var view: VintaBannerView? = null

    override fun loadAd(adData: AdData, activity: Activity, bannerSize: ISBannerSize, listener: BannerAdListener) {
        try {
            val unitId = adData.str(KEY_UNIT)
            if (unitId == null || !ensureInit(adData, activity)) return listener.missingParams()
            // LevelPlay owns banner refresh in mediation: leave refresh off on this VINTA unit.
            val banner = VintaBannerView(activity).also { view = it }
            banner.load(unitId, object : VintaBannerListener {
                override fun onLoaded() = listener.onAdLoadSuccess(
                    banner,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                )
                override fun onFailed(error: VintaError) = listener.onAdLoadFailed(errorType(error), error.code, error.message)
                override fun onImpression() = listener.onAdOpened()
                override fun onClicked() {
                    listener.onAdClicked()
                    listener.onAdLeftApplication()
                }
            })
        } catch (t: Throwable) {
            listener.onAdLoadFailed(AdapterErrorType.ADAPTER_ERROR_TYPE_INTERNAL, AdapterErrors.ADAPTER_ERROR_INTERNAL, t.message ?: "banner error")
        }
    }

    override fun destroyAd(adData: AdData) {
        view?.destroy()
        view = null
    }
}
