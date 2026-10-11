package org.view2earn.vinta

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

interface VintaBannerListener {
    fun onLoaded() {}
    fun onFailed(error: VintaError) {}
    fun onImpression() {}
    fun onClicked() {}
}

/**
 * Banner view (blueprint 3.2): 320x50, 300x250 or any image the advertiser
 * uploaded, scaled to the view width. Impression is counted once the banner is
 * at least 50% on screen for 1 continuous second (MRC). Auto-refreshes when
 * the ad unit sets a refresh interval, only while attached and visible.
 */
class VintaBannerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private val image = ImageView(context).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER }
    private val label = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.argb(140, 0, 0, 0))
        setPadding(6, 2, 6, 2)
        visibility = View.GONE
    }
    private var ad: VintaAd? = null
    private var unitId: String? = null
    private var listener: VintaBannerListener? = null
    private var visibleSince = 0L
    private var impressed = false
    private var destroyed = false
    private val visibilityCheck = object : Runnable {
        override fun run() = checkVisibility()
    }
    private val refresh = Runnable { unitId?.let { load(it, listener) } }

    init {
        addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(label, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
        setOnClickListener { onClick() }
    }

    @JvmOverloads
    fun load(unitId: String, listener: VintaBannerListener? = null) {
        if (destroyed) return
        this.unitId = unitId
        this.listener = listener
        removeCallbacks(refresh)
        Net.requestAd(unitId, null, null) { loaded, error ->
            if (destroyed) return@requestAd
            if (loaded == null) return@requestAd listener?.onFailed(error ?: VintaError(VintaError.INTERNAL, "unknown")) ?: Unit
            Net.background {
                val bmp = Net.bitmap(loaded.mediaUrl)
                Net.main {
                    if (destroyed) return@main
                    if (bmp == null) return@main listener?.onFailed(VintaError(VintaError.NETWORK, "Could not load banner image")) ?: Unit
                    ad = loaded
                    impressed = false
                    visibleSince = 0L
                    image.setImageBitmap(bmp)
                    image.contentDescription = loaded.headline ?: "Sponsored"
                    label.text = if (loaded.test) "Test ad · VINTA" else "Ad · VINTA"
                    label.visibility = View.VISIBLE
                    listener?.onLoaded()
                    removeCallbacks(visibilityCheck)
                    post(visibilityCheck)
                }
            }
        }
    }

    private fun checkVisibility() {
        val current = ad ?: return
        if (impressed || destroyed) return
        val r = Rect()
        val area = width.toLong() * height
        val shown = isShown && windowVisibility == View.VISIBLE && area > 0 && getGlobalVisibleRect(r) &&
            r.width().toLong() * r.height() * 2 >= area
        val now = System.currentTimeMillis()
        if (!shown) visibleSince = 0L
        else if (visibleSince == 0L) visibleSince = now
        else if (now - visibleSince >= 1000) {
            impressed = true
            Net.track(current, "impression")
            listener?.onImpression()
            current.refreshSec?.let { postDelayed(refresh, it.coerceIn(30, 120) * 1000L) }
            return
        }
        postDelayed(visibilityCheck, 250)
    }

    private fun onClick() {
        val current = ad ?: return
        Net.track(current, "click")
        listener?.onClicked()
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(current.clickUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (ad != null && !impressed) post(visibilityCheck)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(visibilityCheck)
        removeCallbacks(refresh) // pause refresh off-screen (blueprint 3.2)
        super.onDetachedFromWindow()
    }

    /** Call from the host's onDestroy. */
    fun destroy() {
        destroyed = true
        removeCallbacks(visibilityCheck)
        removeCallbacks(refresh)
        listener = null
        image.setImageDrawable(null)
    }
}
