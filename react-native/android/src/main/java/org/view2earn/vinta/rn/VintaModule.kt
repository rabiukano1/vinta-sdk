package org.view2earn.vinta.rn

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import org.view2earn.vinta.Vinta
import org.view2earn.vinta.VintaAppOpen
import org.view2earn.vinta.VintaError
import org.view2earn.vinta.VintaFullScreenAd
import org.view2earn.vinta.VintaInterests
import org.view2earn.vinta.VintaInterstitial
import org.view2earn.vinta.VintaNativeAd
import org.view2earn.vinta.VintaReward
import org.view2earn.vinta.VintaRewarded
import org.view2earn.vinta.VintaRewardedInterstitial
import org.view2earn.vinta.VintaShowListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * React Native bridge for the VINTA Android SDK (blueprint 3.1 wrappers).
 * Every method resolves (never rejects) so a failed ad can't crash JS code
 * that forgot a catch (blueprint 2.6).
 * ponytail: legacy NativeModule; works on the New Architecture through RN's
 * interop layer. Move to a TurboModule spec when the iOS side lands.
 */
class VintaModule(private val ctx: ReactApplicationContext) : ReactContextBaseJavaModule(ctx) {
    override fun getName() = NAME

    companion object {
        const val NAME = "VintaAds"
        /** Loaded native ads, looked up by VintaNativeAdView's adToken prop. */
        internal val natives = ConcurrentHashMap<String, VintaNativeAd>()
    }

    private fun err(e: VintaError?): WritableMap = Arguments.createMap().apply {
        putBoolean("shown", false)
        putBoolean("rewarded", false)
        putString("error", e?.message ?: "unknown error")
        putInt("code", e?.code ?: VintaError.INTERNAL)
    }

    @ReactMethod
    fun init(appKey: String, test: Boolean, childDirected: Boolean, promise: Promise) {
        Vinta.childDirected = childDirected
        Vinta.init(ctx, appKey, test) { c ->
            promise.resolve(c?.let { cfg ->
                Arguments.createMap().apply {
                    putString("name", cfg.name)
                    putString("status", cfg.status)
                    putString("country", cfg.country)
                    putArray("units", Arguments.createArray().apply {
                        cfg.units.forEach { u ->
                            pushMap(Arguments.createMap().apply {
                                putString("id", u.id)
                                putString("name", u.name)
                                putString("format", u.format)
                                putString("rewardName", u.rewardName)
                                u.rewardAmount?.let { putInt("rewardAmount", it) } ?: putNull("rewardAmount")
                            })
                        }
                    })
                }
            })
        }
    }

    @ReactMethod
    fun deviceId(promise: Promise) = promise.resolve(runCatching { Vinta.deviceId }.getOrNull())

    // User-chosen interests (saved on the device only).
    @ReactMethod
    fun getInterests(promise: Promise) = promise.resolve(Arguments.fromList(VintaInterests.get()))

    @ReactMethod
    fun setInterests(ids: com.facebook.react.bridge.ReadableArray, promise: Promise) =
        promise.resolve(Arguments.fromList(VintaInterests.set(ids.toArrayList().filterIsInstance<String>())))

    @ReactMethod
    fun showInterestPicker(promise: Promise) {
        val activity = ctx.currentActivity ?: return promise.resolve(Arguments.fromList(VintaInterests.get()))
        VintaInterests.showPicker(activity) { promise.resolve(Arguments.fromList(it)) }
    }

    @ReactMethod
    fun setChildDirected(value: Boolean) {
        Vinta.childDirected = value
    }

    // ── Full screen ───────────────────────────────────────────────────────────

    private fun show(ad: VintaFullScreenAd, promise: Promise) {
        var shown = false
        var reward: VintaReward? = null
        ad.show(ctx.currentActivity ?: ctx, object : VintaShowListener {
            override fun onShown() { shown = true }
            override fun onRewarded(reward0: VintaReward) { reward = reward0 }
            override fun onShowFailed(error: VintaError) = promise.resolve(err(error))
            override fun onClosed() = promise.resolve(Arguments.createMap().apply {
                putBoolean("shown", shown)
                putBoolean("rewarded", reward != null)
                reward?.let { r -> putMap("reward", Arguments.createMap().apply { putString("name", r.name); putInt("amount", r.amount) }) }
                    ?: putNull("reward")
            })
        })
    }

    @ReactMethod
    fun showInterstitial(unitId: String, promise: Promise) {
        val ad = VintaInterstitial()
        ad.load(unitId) { e -> if (e != null) promise.resolve(err(e)) else show(ad, promise) }
    }

    @ReactMethod
    fun showAppOpen(unitId: String, promise: Promise) {
        val ad = VintaAppOpen()
        ad.load(unitId) { e -> if (e != null) promise.resolve(err(e)) else show(ad, promise) }
    }

    @ReactMethod
    fun showRewarded(unitId: String, userId: String?, customData: String?, promise: Promise) {
        val ad = VintaRewarded()
        ad.load(unitId, userId, customData) { e -> if (e != null) promise.resolve(err(e)) else show(ad, promise) }
    }

    @ReactMethod
    fun showRewardedInterstitial(unitId: String, userId: String?, customData: String?, promise: Promise) {
        val ad = VintaRewardedInterstitial()
        ad.load(unitId, userId, customData) { e -> if (e != null) promise.resolve(err(e)) else show(ad, promise) }
    }

    // ── Native ────────────────────────────────────────────────────────────────

    @ReactMethod
    fun loadNative(unitId: String, promise: Promise) {
        VintaNativeAd.load(unitId) { ad, _ ->
            if (ad == null) return@load promise.resolve(null) // no fill or error: JS renders nothing
            val token = UUID.randomUUID().toString()
            natives[token] = ad
            promise.resolve(Arguments.createMap().apply {
                putString("token", token)
                putString("title", ad.title)
                putString("body", ad.body)
                putString("cta", ad.callToAction)
                putString("advertiser", ad.advertiser)
                putString("iconUrl", ad.iconUrl)
                putString("imageUrl", ad.imageUrl)
                putBoolean("test", ad.isTest)
            })
        }
    }

    /** JS handles taps (RN's touch system); the SDK records the click and opens the link. */
    @ReactMethod
    fun nativeClick(token: String) {
        natives[token]?.performClick(ctx)
    }

    @ReactMethod
    fun destroyNative(token: String) {
        natives.remove(token)?.unregister()
    }
}
