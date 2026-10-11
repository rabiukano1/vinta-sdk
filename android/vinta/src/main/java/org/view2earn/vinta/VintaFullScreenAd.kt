package org.view2earn.vinta

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import java.io.File
import java.util.concurrent.ConcurrentHashMap

fun interface VintaLoadListener {
    /** Exactly one of [error] or success (error == null) per load. */
    fun onLoadResult(error: VintaError?)
}

interface VintaShowListener {
    fun onShown() {}
    fun onShowFailed(error: VintaError) {}
    fun onClicked() {}
    /** Rewarded only: the user finished the ad. Grant the reward from your SSV callback for security. */
    fun onRewarded(reward: VintaReward) {}
    fun onClosed() {}
}

/** Everything the full-screen activity needs, handed over by token. */
internal class Session(val ad: VintaAd, val bitmap: Bitmap?, val video: File?, val listener: VintaShowListener?)

internal object Sessions {
    private val map = ConcurrentHashMap<String, Session>()
    fun put(s: Session): String = s.ad.serveId.also { map[it] = s }
    fun get(token: String?) = token?.let { map[it] }
    fun remove(token: String?) = token?.let { map.remove(it) }
}

/**
 * Load / show split (blueprint 3.4): load() fetches the ad and pre-caches its
 * image or video; show() displays it. One ad per load; load again after close.
 */
abstract class VintaFullScreenAd internal constructor(private val format: String) {
    private var ad: VintaAd? = null
    private var bitmap: Bitmap? = null
    private var video: File? = null
    private var loading = false

    /** Loaded, cached and not expired. */
    fun isReady(): Boolean = ad?.let { !it.expired } ?: false

    internal fun loadInternal(unitId: String, userId: String?, customData: String?, listener: VintaLoadListener) {
        if (loading) return listener.onLoadResult(VintaError(VintaError.INVALID_REQUEST, "Already loading"))
        loading = true
        ad = null
        Net.requestAd(unitId, userId, customData) { loaded, error ->
            if (loaded == null) {
                loading = false
                return@requestAd listener.onLoadResult(error ?: VintaError(VintaError.INTERNAL, "unknown"))
            }
            if (loaded.format != format) {
                loading = false
                return@requestAd listener.onLoadResult(VintaError(VintaError.INVALID_REQUEST, "Unit is ${loaded.format}, not $format"))
            }
            Net.background {
                val bmp = if (loaded.mediaType == "image") Net.bitmap(loaded.mediaUrl) else null
                val file = if (loaded.mediaType == "video") Net.videoFile(loaded.mediaUrl) else null
                Net.main {
                    loading = false
                    if (bmp == null && file == null) return@main listener.onLoadResult(VintaError(VintaError.NETWORK, "Could not download the ad"))
                    ad = loaded
                    bitmap = bmp
                    video = file
                    listener.onLoadResult(null)
                }
            }
        }
    }

    /** Show the loaded ad. Works from an Activity or any Context. */
    fun show(context: Context, listener: VintaShowListener?) {
        val current = ad
        if (current == null || current.expired) {
            listener?.onShowFailed(VintaError(VintaError.NOT_READY, "No ad loaded (or it expired); call load first"))
            return
        }
        ad = null // one show per load
        try {
            val token = Sessions.put(Session(current, bitmap, video, listener))
            bitmap = null
            video = null
            val intent = Intent(context, VintaAdActivity::class.java).putExtra(VintaAdActivity.EXTRA_TOKEN, token)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (t: Throwable) {
            listener?.onShowFailed(VintaError(VintaError.INTERNAL, t.message ?: "Could not open the ad"))
        }
    }
}

/** Full-screen ad at a natural break. Closable after 5 seconds. */
class VintaInterstitial : VintaFullScreenAd("interstitial") {
    fun load(unitId: String, listener: VintaLoadListener) = loadInternal(unitId, null, null, listener)
}

/**
 * Rewarded ad. The reward is fixed in the ad unit and shown before watching
 * (ju'alah, blueprint 11). [userId] and [customData] are sent back to your
 * server in the signed SSV callback.
 */
class VintaRewarded : VintaFullScreenAd("rewarded") {
    @JvmOverloads
    fun load(unitId: String, userId: String? = null, customData: String? = null, listener: VintaLoadListener) =
        loadInternal(unitId, userId, customData, listener)
}

/** Full-screen ad on app launch / resume. Closable after 2 seconds. Keep it rare (e.g. once per session). */
class VintaAppOpen : VintaFullScreenAd("app_open") {
    fun load(unitId: String, listener: VintaLoadListener) = loadInternal(unitId, null, null, listener)
}

/**
 * Rewarded ad shown at a natural break without an opt-in button: a 3-second
 * intro names the fixed reward and offers Skip, then the ad plays.
 */
class VintaRewardedInterstitial : VintaFullScreenAd("rewarded_interstitial") {
    @JvmOverloads
    fun load(unitId: String, userId: String? = null, customData: String? = null, listener: VintaLoadListener) =
        loadInternal(unitId, userId, customData, listener)
}
