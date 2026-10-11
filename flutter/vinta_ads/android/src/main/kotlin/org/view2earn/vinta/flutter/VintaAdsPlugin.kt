package org.view2earn.vinta.flutter

import android.app.Activity
import android.content.Context
import android.view.View
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory
import org.view2earn.vinta.Vinta
import org.view2earn.vinta.VintaAppOpen
import org.view2earn.vinta.VintaBannerView
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
 * Flutter bridge for the VINTA Android SDK (blueprint 3.1 wrappers). Every
 * call answers with result.success (never an error) so a failed ad can't
 * throw into Dart code that forgot a try/catch (blueprint 2.6).
 */
class VintaAdsPlugin : FlutterPlugin, MethodChannel.MethodCallHandler, ActivityAware {
    private lateinit var channel: MethodChannel
    private lateinit var context: Context
    private var activity: Activity? = null
    private val natives = ConcurrentHashMap<String, VintaNativeAd>()

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        channel = MethodChannel(binding.binaryMessenger, "vinta_ads")
        channel.setMethodCallHandler(this)
        binding.platformViewRegistry.registerViewFactory("vinta_ads/banner", BannerFactory())
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        natives.values.forEach { it.unregister() }
        natives.clear()
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) { activity = binding.activity }
    override fun onDetachedFromActivityForConfigChanges() { activity = null }
    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) { activity = binding.activity }
    override fun onDetachedFromActivity() { activity = null }

    private fun failure(e: VintaError?) = mapOf(
        "shown" to false,
        "rewarded" to false,
        "error" to (e?.message ?: "unknown error"),
        "code" to (e?.code ?: VintaError.INTERNAL),
    )

    private fun show(ad: VintaFullScreenAd, result: MethodChannel.Result) {
        var shown = false
        var earned: VintaReward? = null
        ad.show(activity ?: context, object : VintaShowListener {
            override fun onShown() { shown = true }
            override fun onRewarded(reward: VintaReward) { earned = reward }
            override fun onShowFailed(error: VintaError) = result.success(failure(error))
            override fun onClosed() = result.success(
                mapOf(
                    "shown" to shown,
                    "rewarded" to (earned != null),
                    "reward" to earned?.let { mapOf("name" to it.name, "amount" to it.amount) },
                ),
            )
        })
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            val unitId = call.argument<String>("unitId")
            when (call.method) {
                "init" -> {
                    Vinta.childDirected = call.argument<Boolean>("childDirected") == true
                    Vinta.init(context, call.argument<String>("appKey") ?: "", call.argument<Boolean>("test") == true) { c ->
                        result.success(
                            c?.let { cfg ->
                                mapOf(
                                    "name" to cfg.name,
                                    "status" to cfg.status,
                                    "country" to cfg.country,
                                    "units" to cfg.units.map { u ->
                                        mapOf("id" to u.id, "name" to u.name, "format" to u.format, "rewardName" to u.rewardName, "rewardAmount" to u.rewardAmount)
                                    },
                                )
                            },
                        )
                    }
                }
                "deviceId" -> result.success(runCatching { Vinta.deviceId }.getOrNull())
                // User-chosen interests (saved on the device only).
                "getInterests" -> result.success(VintaInterests.get())
                "setInterests" -> result.success(VintaInterests.set(call.argument<List<String>>("ids") ?: emptyList()))
                "showInterestPicker" -> {
                    val a = activity
                    if (a == null) result.success(VintaInterests.get()) else VintaInterests.showPicker(a) { result.success(it) }
                }
                "setChildDirected" -> {
                    Vinta.childDirected = call.argument<Boolean>("value") == true
                    result.success(null)
                }
                "showInterstitial" -> VintaInterstitial().let { ad -> ad.load(unitId!!) { e -> if (e != null) result.success(failure(e)) else show(ad, result) } }
                "showAppOpen" -> VintaAppOpen().let { ad -> ad.load(unitId!!) { e -> if (e != null) result.success(failure(e)) else show(ad, result) } }
                "showRewarded" -> VintaRewarded().let { ad ->
                    ad.load(unitId!!, call.argument("userId"), call.argument("customData")) { e -> if (e != null) result.success(failure(e)) else show(ad, result) }
                }
                "showRewardedInterstitial" -> VintaRewardedInterstitial().let { ad ->
                    ad.load(unitId!!, call.argument("userId"), call.argument("customData")) { e -> if (e != null) result.success(failure(e)) else show(ad, result) }
                }
                "loadNative" -> VintaNativeAd.load(unitId!!) { ad, _ ->
                    if (ad == null) return@load result.success(null) // no fill: Dart renders nothing
                    val token = UUID.randomUUID().toString()
                    natives[token] = ad
                    result.success(
                        mapOf(
                            "token" to token,
                            "title" to ad.title,
                            "body" to ad.body,
                            "cta" to ad.callToAction,
                            "advertiser" to ad.advertiser,
                            "iconUrl" to ad.iconUrl,
                            "imageUrl" to ad.imageUrl,
                            "test" to ad.isTest,
                        ),
                    )
                }
                // Dart measures visibility (50% for 1s) and handles taps; the SDK records them.
                "nativeImpression" -> {
                    natives[call.argument<String>("token")]?.recordImpression()
                    result.success(null)
                }
                "nativeClick" -> {
                    natives[call.argument<String>("token")]?.performClick(activity ?: context)
                    result.success(null)
                }
                "destroyNative" -> {
                    natives.remove(call.argument<String>("token"))?.unregister()
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        } catch (t: Throwable) {
            result.success(failure(VintaError(VintaError.INTERNAL, t.message ?: "error")))
        }
    }
}

/** `VintaBanner` widget → native VintaBannerView (platform view). */
private class BannerFactory : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        val unitId = (args as? Map<*, *>)?.get("unitId") as? String
        val banner = VintaBannerView(context)
        if (!unitId.isNullOrBlank()) banner.load(unitId)
        return object : PlatformView {
            override fun getView(): View = banner
            override fun dispose() = banner.destroy()
        }
    }
}
