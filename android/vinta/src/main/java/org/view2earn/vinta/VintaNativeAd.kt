package org.view2earn.vinta

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

interface VintaNativeListener {
    fun onImpression() {}
    fun onClicked() {}
}

fun interface VintaNativeLoadListener {
    fun onLoaded(ad: VintaNativeAd?, error: VintaError?)
}

/**
 * Native ad (blueprint 3.2): you get the assets and lay them out in your own
 * design, then call [registerView] on the view that shows them. VINTA counts
 * the impression when that view is at least 50% on screen for 1 second, and
 * handles taps. If the view is a ViewGroup the required "Ad · VINTA" label is
 * added for you; otherwise show an "Ad" label yourself.
 */
class VintaNativeAd private constructor(
    private val ad: VintaAd,
    val icon: Bitmap?,
    val image: Bitmap?,
) {
    val title: String get() = ad.headline.orEmpty()
    val body: String get() = ad.body.orEmpty()
    val callToAction: String get() = ad.cta ?: "Learn more"
    val advertiser: String get() = ad.advertiserName.orEmpty()
    val isTest: Boolean get() = ad.test
    /** Image URLs, for UI toolkits that load images themselves (React Native, Flutter). */
    val iconUrl: String? get() = ad.iconUrl
    val imageUrl: String get() = ad.mediaUrl

    private var view: View? = null
    private var listener: VintaNativeListener? = null
    private var visibleSince = 0L
    private var impressed = false
    private val check = object : Runnable {
        override fun run() {
            val v = view ?: return
            if (impressed) return
            val r = Rect()
            val area = v.width.toLong() * v.height
            val shown = v.isShown && area > 0 && v.getGlobalVisibleRect(r) && r.width().toLong() * r.height() * 2 >= area
            val now = System.currentTimeMillis()
            if (!shown) visibleSince = 0L
            else if (visibleSince == 0L) visibleSince = now
            else if (now - visibleSince >= 1000) {
                impressed = true
                Net.track(ad, "impression")
                listener?.onImpression()
                return
            }
            v.postDelayed(this, 250)
        }
    }

    /**
     * Start impression tracking and click handling on the view showing this ad.
     * [addLabel] = false when your UI toolkit draws the required "Ad" label itself
     * (React Native / Flutter manage their own child views).
     */
    @JvmOverloads
    fun registerView(view: View, listener: VintaNativeListener? = null, addLabel: Boolean = true) {
        unregister()
        this.view = view
        this.listener = listener
        view.setOnClickListener {
            Net.track(ad, "click")
            this.listener?.onClicked()
            runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ad.clickUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        if (addLabel && view is ViewGroup && view.findViewWithTag<View>(LABEL_TAG) == null) {
            val label = TextView(view.context).apply {
                tag = LABEL_TAG
                text = if (ad.test) "Test ad · VINTA" else "Ad · VINTA"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.argb(140, 0, 0, 0))
                setPadding(6, 2, 6, 2)
            }
            if (view is FrameLayout) view.addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
            else view.addView(label, 0)
        }
        view.post(check)
    }

    /** Stop tracking (call when the view is recycled or destroyed). */
    fun unregister() {
        view?.removeCallbacks(check)
        view?.setOnClickListener(null)
        view = null
        listener = null
    }

    /** For adapters that handle the click themselves (e.g. AdMob's native mapper). */
    fun performClick(context: android.content.Context) {
        Net.track(ad, "click")
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ad.clickUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** For adapters whose host decides when the impression happened. */
    fun recordImpression() {
        if (impressed) return
        impressed = true
        Net.track(ad, "impression")
    }

    companion object {
        private const val LABEL_TAG = "vinta_ad_label"

        @JvmStatic
        fun load(unitId: String, listener: VintaNativeLoadListener) {
            Net.requestAd(unitId, null, null) { loaded, error ->
                if (loaded == null) return@requestAd listener.onLoaded(null, error)
                if (loaded.format != "native") return@requestAd listener.onLoaded(null, VintaError(VintaError.INVALID_REQUEST, "Unit is ${loaded.format}, not native"))
                Net.background {
                    val icon = loaded.iconUrl?.let(Net::bitmap)
                    val image = Net.bitmap(loaded.mediaUrl)
                    Net.main {
                        if (image == null) listener.onLoaded(null, VintaError(VintaError.NETWORK, "Could not load the ad image"))
                        else listener.onLoaded(VintaNativeAd(loaded, icon, image), null)
                    }
                }
            }
        }
    }
}
