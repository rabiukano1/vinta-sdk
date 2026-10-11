package org.view2earn.vinta

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView

/**
 * Full-screen interstitial / rewarded ad. Built in code (no XML resources) so
 * the AAR stays tiny. Same behaviour as the Web SDK:
 * - impression when the image is on screen / the video starts
 * - interstitial: close button after 5 s
 * - rewarded: 15 s countdown for images, end of video for videos; closing
 *   early means no reward
 */
class VintaAdActivity : Activity() {
    companion object {
        internal const val EXTRA_TOKEN = "vinta_token"
        private const val INTERSTITIAL_CLOSE_SECONDS = 5
        private const val APP_OPEN_CLOSE_SECONDS = 2
        private const val REWARDED_INTRO_SECONDS = 3
        private const val REWARD_IMAGE_SECONDS = 15
    }

    private var token: String? = null
    private var session: Session? = null
    private var closable = false
    private var closedReported = false
    private val status by lazy { text(13f) }
    private val close by lazy { Button(this) }
    private val ticker = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token = intent.getStringExtra(EXTRA_TOKEN)
        val s = Sessions.get(token)
        if (s == null) {
            finish()
            return
        }
        session = s
        try {
            setContentView(build(s))
        } catch (t: Throwable) {
            s.listener?.onShowFailed(VintaError(VintaError.INTERNAL, t.message ?: "render failed"))
            finish()
        }
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    private fun text(sp: Float) = TextView(this).apply { setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp) }

    private fun build(s: Session): View {
        val ad = s.ad
        val rewarded = isRewarded(ad)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val cta = Button(this)

        if (ad.format == "rewarded_interstitial") {
            // Shown without an opt-in button (blueprint 3.2), so a short intro names
            // the reward and lets the user skip before the ad starts.
            val intro = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            val line = text(17f).apply { gravity = Gravity.CENTER; setPadding(dp(24), 0, dp(24), 0) }
            val skip = Button(this).apply {
                text = "Skip"
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply { setStroke(dp(1), Color.WHITE); cornerRadius = dp(10).toFloat() }
                setOnClickListener { finish() } // skipped before the ad: no impression, no reward
            }
            intro.addView(line)
            intro.addView(skip, android.widget.LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(16) })
            root.addView(intro, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER))
            cta.visibility = View.GONE
            var n = REWARDED_INTRO_SECONDS
            val say = { line.text = (ad.rewardAmount?.let { "Watch a short ad to get $it ${ad.rewardName}. " } ?: "") + "Ad starting in $n…" }
            say()
            ticker.postDelayed(object : Runnable {
                override fun run() {
                    n--
                    if (n > 0) {
                        say()
                        ticker.postDelayed(this, 1000)
                    } else {
                        root.removeView(intro)
                        cta.visibility = View.VISIBLE
                        addMedia(root, s, rewarded)
                    }
                }
            }, 1000)
        } else {
            addMedia(root, s, rewarded)
        }
        decorate(root, s, rewarded, cta)
        return root
    }

    private fun isRewarded(ad: VintaAd) = ad.format == "rewarded" || ad.format == "rewarded_interstitial"

    private fun addMedia(root: FrameLayout, s: Session, rewarded: Boolean) {
        val ad = s.ad
        if (s.video != null) {
            val video = VideoView(this)
            video.setVideoURI(Uri.fromFile(s.video))
            // Plays with sound (apps aren't subject to browser autoplay rules);
            // the speaker button mutes/unmutes. Volume follows the media volume keys.
            var player: android.media.MediaPlayer? = null
            var muted = false
            val sound = Button(this).apply {
                text = "🔊"
                contentDescription = "Mute ad"
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(70, 255, 255, 255)) }
            }
            val applyVolume = {
                val v = if (muted) 0f else 1f
                runCatching { player?.setVolume(v, v) }
                sound.text = if (muted) "🔇" else "🔊"
                sound.contentDescription = if (muted) "Unmute ad" else "Mute ad"
            }
            sound.setOnClickListener {
                muted = !muted
                applyVolume()
            }
            video.setOnPreparedListener { mp ->
                player = mp
                applyVolume()
                video.start()
                onShown()
            }
            video.setOnCompletionListener { if (rewarded) complete() }
            video.setOnErrorListener { _, _, _ -> failAndFinish("Video could not play"); true }
            volumeControlStream = android.media.AudioManager.STREAM_MUSIC
            root.addView(video, 0, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.CENTER))
            root.addView(sound, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(16), 0, 0, dp(88)) })
        } else {
            val image = ImageView(this).apply {
                setImageBitmap(s.bitmap)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = ad.headline ?: "Sponsored"
            }
            root.addView(image, 0, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, Gravity.CENTER))
            image.post { onShown() }
        }
    }

    private fun decorate(root: FrameLayout, s: Session, rewarded: Boolean, cta: Button) {
        val ad = s.ad

        val label = text(10f).apply {
            text = if (ad.test) "Test ad · VINTA" else "Ad · VINTA"
            background = GradientDrawable().apply { setColor(Color.argb(60, 255, 255, 255)); cornerRadius = dp(4).toFloat() }
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        root.addView(label, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply { setMargins(dp(12), dp(14), 0, 0) })
        root.addView(status, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(18), dp(60), 0) })

        close.apply {
            text = "✕"
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(70, 255, 255, 255)) }
            contentDescription = "Close ad"
            setOnClickListener { finish() }
            visibility = if (rewarded) View.VISIBLE else View.GONE
        }
        closable = rewarded // rewarded can always be closed (= no reward)
        root.addView(close, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(8), dp(12), 0) })

        cta.apply {
            text = ad.headline ?: "Learn more"
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = GradientDrawable().apply { setColor(Color.rgb(16, 185, 129)); cornerRadius = dp(12).toFloat() }
            setOnClickListener {
                Net.track(ad, "click")
                session?.listener?.onClicked()
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ad.clickUrl))) }
            }
        }
        root.addView(cta, FrameLayout.LayoutParams(MATCH_PARENT, dp(52), Gravity.BOTTOM).apply { setMargins(dp(16), 0, dp(16), dp(24)) })
    }

    private var shown = false
    private fun onShown() {
        val s = session ?: return
        if (shown) return
        shown = true
        Net.track(s.ad, "impression")
        s.listener?.onShown()
        when {
            !isRewarded(s.ad) -> countdown(if (s.ad.format == "app_open") APP_OPEN_CLOSE_SECONDS else INTERSTITIAL_CLOSE_SECONDS, { "Close in $it" }) {
                closable = true
                close.visibility = View.VISIBLE
            }
            s.video == null -> countdown(REWARD_IMAGE_SECONDS, { rewardText(s.ad, it) }) { complete() }
            else -> status.text = s.ad.rewardAmount?.let { "Watch to earn $it ${s.ad.rewardName}" } ?: "Watch to the end"
        }
    }

    private fun rewardText(ad: VintaAd, s: Int) = ad.rewardAmount?.let { "$it ${ad.rewardName} in ${s}s" } ?: "Reward in ${s}s"

    private fun countdown(seconds: Int, label: (Int) -> String, done: () -> Unit) {
        var left = seconds
        status.text = label(left)
        ticker.postDelayed(object : Runnable {
            override fun run() {
                left--
                if (left > 0) {
                    status.text = label(left)
                    ticker.postDelayed(this, 1000)
                } else {
                    status.text = ""
                    done()
                }
            }
        }, 1000)
    }

    private fun complete() {
        val s = session ?: return
        Net.track(s.ad, "complete") { res ->
            if (res?.optBoolean("completed") == true) {
                status.text = "Reward earned ✓"
                s.listener?.onRewarded(VintaReward(s.ad.rewardName ?: "", s.ad.rewardAmount ?: 0))
            }
        }
    }

    private fun failAndFinish(msg: String) {
        session?.listener?.onShowFailed(VintaError(VintaError.INTERNAL, msg))
        closedReported = true // a failed show doesn't also report closed
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (closable) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        ticker.removeCallbacksAndMessages(null)
        if (isFinishing) {
            if (!closedReported) session?.listener?.onClosed()
            closedReported = true
            Sessions.remove(token)
        }
        super.onDestroy()
    }
}
