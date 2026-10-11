package org.view2earn.vinta.sample

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.view2earn.vinta.Vinta
import org.view2earn.vinta.VintaBannerListener
import org.view2earn.vinta.VintaBannerView
import org.view2earn.vinta.VintaConfig
import org.view2earn.vinta.VintaError
import org.view2earn.vinta.VintaInterstitial
import org.view2earn.vinta.VintaReward
import org.view2earn.vinta.VintaRewarded
import org.view2earn.vinta.VintaShowListener
import org.view2earn.vinta.VintaAppOpen
import org.view2earn.vinta.VintaNativeAd
import org.view2earn.vinta.VintaNativeListener
import org.view2earn.vinta.VintaRewardedInterstitial
import android.widget.FrameLayout
import android.widget.ImageView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One-screen test app: init with an app key, then try each of the app's ad units. */
class MainActivity : Activity() {
    private lateinit var log: TextView
    private lateinit var units: LinearLayout
    private lateinit var banner: VintaBannerView
    private val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

    private fun log(msg: String) {
        log.text = "${time.format(Date())}  $msg\n${log.text}"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("sample", Context.MODE_PRIVATE)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        col.addView(TextView(this).apply { text = "VINTA SDK ${Vinta.version}"; textSize = 20f })
        val key = EditText(this).apply { hint = "App key (va_...)"; setText(prefs.getString("appKey", "")) }
        val test = CheckBox(this).apply { text = "Test mode (never billed)"; isChecked = true }
        val start = Button(this).apply { text = "Start SDK" }
        val interests = Button(this).apply {
            text = "Choose interests"
            setOnClickListener { org.view2earn.vinta.VintaInterests.showPicker(this@MainActivity) { log("interests: ${it.joinToString().ifEmpty { "none" }}") } }
        }
        units = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        banner = VintaBannerView(this)
        log = TextView(this).apply { textSize = 12f; typeface = android.graphics.Typeface.MONOSPACE }
        listOf(key, test, start, interests, units, banner, log).forEach(col::addView)
        setContentView(ScrollView(this).apply { addView(col) })

        start.setOnClickListener {
            prefs.edit().putString("appKey", key.text.toString().trim()).apply()
            log("init…")
            Vinta.init(this, key.text.toString(), test.isChecked) { config -> onConfig(config) }
        }
    }

    private fun onConfig(config: VintaConfig?) {
        units.removeAllViews()
        if (config == null) return log("init failed: unknown app key or no network")
        log("VINTA device id (for deletion requests): ${Vinta.deviceId}")
        log("\"${config.name}\": ${if (config.approved) "approved (real ads)" else "${config.status} (test ads only)"}, ${config.units.size} unit(s), your country: ${config.country ?: "unknown"}")
        for (u in config.units) {
            when (u.format) {
                "banner" -> banner.load(u.id, object : VintaBannerListener {
                    override fun onLoaded() = log("banner \"${u.name}\": loaded")
                    override fun onFailed(error: VintaError) = log("banner \"${u.name}\": ${error.message}")
                    override fun onImpression() = log("banner: impression")
                    override fun onClicked() = log("banner: clicked")
                })
                "interstitial" -> units.addView(Button(this).apply {
                    text = "Show ${u.name}"
                    setOnClickListener {
                        val ad = VintaInterstitial()
                        ad.load(u.id) { err ->
                            if (err != null) log("interstitial: ${err.message}") else ad.show(this@MainActivity, listener("interstitial"))
                        }
                    }
                })
                "app_open" -> units.addView(Button(this).apply {
                    text = "Show ${u.name} (app open)"
                    setOnClickListener {
                        val ad = VintaAppOpen()
                        ad.load(u.id) { err -> if (err != null) log("app open: ${err.message}") else ad.show(this@MainActivity, listener("app open")) }
                    }
                })
                "rewarded_interstitial" -> units.addView(Button(this).apply {
                    text = "▶ ${u.name} (+${u.rewardAmount} ${u.rewardName}, with intro)"
                    setOnClickListener {
                        val ad = VintaRewardedInterstitial()
                        ad.load(u.id, "sample-user", "android-sample") { err ->
                            if (err != null) log("rewarded interstitial: ${err.message}") else ad.show(this@MainActivity, listener("rewarded interstitial"))
                        }
                    }
                })
                "native" -> VintaNativeAd.load(u.id) { ad, err ->
                    if (ad == null) return@load log("native \"${u.name}\": ${err?.message}")
                    log("native \"${u.name}\": loaded")
                    // Laid out in the app's own style: that's the point of native.
                    val card = FrameLayout(this)
                    val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
                    col.addView(ImageView(this).apply { setImageBitmap(ad.icon) }, LinearLayout.LayoutParams(96, 96))
                    col.addView(TextView(this).apply { text = ad.title; textSize = 16f })
                    col.addView(TextView(this).apply { text = ad.advertiser; textSize = 12f })
                    col.addView(ImageView(this).apply { setImageBitmap(ad.image); adjustViewBounds = true })
                    col.addView(TextView(this).apply { text = ad.body })
                    col.addView(Button(this).apply { text = ad.callToAction; isClickable = false })
                    card.addView(col)
                    units.addView(card)
                    ad.registerView(card, object : VintaNativeListener {
                        override fun onImpression() = log("native: impression")
                        override fun onClicked() = log("native: clicked")
                    })
                }
                "rewarded" -> units.addView(Button(this).apply {
                    text = "▶ ${u.name} (+${u.rewardAmount} ${u.rewardName})"
                    setOnClickListener {
                        val ad = VintaRewarded()
                        ad.load(u.id, "sample-user", "android-sample") { err ->
                            if (err != null) log("rewarded: ${err.message}") else ad.show(this@MainActivity, listener("rewarded"))
                        }
                    }
                })
            }
        }
    }

    private fun listener(name: String) = object : VintaShowListener {
        override fun onShown() = log("$name: shown")
        override fun onShowFailed(error: VintaError) = log("$name: show failed: ${error.message}")
        override fun onClicked() = log("$name: clicked")
        override fun onRewarded(reward: VintaReward) = log("$name: REWARD ${reward.amount} ${reward.name} (check SSV log)")
        override fun onClosed() = log("$name: closed")
    }

    override fun onDestroy() {
        banner.destroy()
        super.onDestroy()
    }
}
