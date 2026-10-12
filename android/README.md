# VINTA Android SDK

Halal ad network SDK for Android (Kotlin, works from Java). Modules:

| Module | What | Size |
|---|---|---|
| `vinta` | SDK: banner, interstitial, rewarded, app open, rewarded interstitial, native. No third-party dependencies. minSdk 21. | ~75 KB AAR |
| `vinta-admob` | AdMob mediation custom-event adapter. minSdk 23. | ~15 KB AAR |
| `vinta-applovin` | AppLovin MAX custom SDK network adapter. minSdk 21. | ~26 KB AAR |
| `vinta-levelplay` | Unity LevelPlay custom adapter (interstitial, rewarded, banner). minSdk 21. | ~17 KB AAR |
| `sample` | Test app: paste an app key, try every ad unit. | |

Build: `./gradlew :vinta:assembleRelease :vinta-admob:assembleRelease :vinta-applovin:assembleRelease :vinta-levelplay:assembleRelease :sample:assembleDebug`
(AARs in `*/build/outputs/aar/`, APK in `sample/build/outputs/apk/debug/`).

## Install

```kotlin
// settings.gradle.kts → dependencyResolutionManagement { repositories { ... } }
maven { url = uri("https://vinta-console.pages.dev/maven") }
// app/build.gradle.kts
implementation("org.view2earn.vinta:vinta:0.5.0")
implementation("org.view2earn.vinta:vinta-admob:0.5.0.0")    // AdMob mediation only
implementation("org.view2earn.vinta:vinta-applovin:0.5.0.0") // AppLovin MAX mediation only
implementation("org.view2earn.vinta:vinta-levelplay:0.5.0.0") // Unity LevelPlay mediation only
```

Publish a new version: bump `gradle.properties`, run `gradlew publishToConsole`, deploy the console.

## Direct integration

```kotlin
// Application.onCreate
Vinta.init(this, "va_your_app_key", test = BuildConfig.DEBUG)

// Banner
val banner = VintaBannerView(context)
layout.addView(banner)
banner.load("<banner unit id>")
// onDestroy: banner.destroy()

// Interstitial
val interstitial = VintaInterstitial()
interstitial.load("<interstitial unit id>") { error -> if (error == null) interstitial.show(activity, null) }

// Rewarded: userId/customData come back to your server in the signed SSV callback
val rewarded = VintaRewarded()
rewarded.load("<rewarded unit id>", userId = "user-123", customData = null) { error ->
    if (error == null) rewarded.show(activity, object : VintaShowListener {
        override fun onRewarded(reward: VintaReward) { /* UI only; grant from SSV */ }
        override fun onClosed() { /* load the next one */ }
    })
}
```

```kotlin
// App open / rewarded interstitial: same load/show pattern
VintaAppOpen().also { a -> a.load("<unit>") { e -> if (e == null) a.show(activity, null) } }
VintaRewardedInterstitial().also { a -> a.load("<unit>", userId = "u1", customData = null) { e -> if (e == null) a.show(activity, listener) } }

// Native: lay out the assets yourself, then register the view
VintaNativeAd.load("<unit>") { ad, e ->
    ad ?: return@load
    title.text = ad.title; body.text = ad.body; cta.text = ad.callToAction
    icon.setImageBitmap(ad.icon); image.setImageBitmap(ad.image)
    ad.registerView(cardView) // label, impression (50% for 1s), clicks
}
```

Error codes match AdMob: 0 internal, 1 invalid request, 2 network, 3 no fill, 4 not ready.

## AdMob mediation (waterfall custom event)

1. Add `vinta` + `vinta-admob` to the app (it already has Google Mobile Ads).
2. AdMob → Mediation → your group → **Add custom event**:
   - Class name: `org.view2earn.vinta.admob.VintaMediationAdapter`
   - Parameter: `{"appKey":"va_...","unitId":"<VINTA unit id of the same format>"}`
   - Manual eCPM: your expected VINTA eCPM.
3. Rewarded: set the reward amount/type on the **AdMob** ad unit to match the VINTA unit
   (GMA 24 reports the AdMob-configured reward). AdMob's own SSV covers mediated rewards.
4. Banners: leave auto-refresh **off** on the VINTA unit; AdMob refreshes the mediated banner.
5. Test with AdMob Ad Inspector.

## AppLovin MAX mediation (custom SDK network)

1. Add `vinta` + `vinta-applovin` to the app (it already has the AppLovin SDK).
2. MAX dashboard → Mediation → Manage → Networks → **Click here to add a Custom Network**:
   - Network type: **SDK**, name: `VINTA`
   - Android adapter class name: `org.view2earn.vinta.applovin.VintaMediationAdapter`
3. On each MAX ad unit, enable the VINTA network:
   - **App ID**: your VINTA app key (`va_...`)
   - **Placement ID**: the VINTA ad unit id of the same format
   - **Custom Parameters** (optional): `{"test":true}` while testing (MAX test mode also works)
   - CPM price: your expected VINTA eCPM.
4. Rewarded: the reward amount/label come from the **MAX** ad unit; MAX's server-side callbacks cover mediated rewards.
5. Banners and MRECs: leave auto-refresh **off** on the VINTA unit; MAX refreshes the mediated banner.
6. Test with the MAX Mediation Debugger (`AppLovinSdk.getInstance(context).showMediationDebugger()`).

## Unity LevelPlay mediation (custom adapter)

Once per network, VINTA registers the adapter with Unity (LevelPlay custom adapter form) with:
package `com.ironsource.adapters.custom.vinta`, base adapter `VintaCustomAdapter`
(ad units `VintaCustomInterstitial`, `VintaCustomRewardedVideo`, `VintaCustomBanner`),
app-level key `appKey`, instance-level keys `unitId` and `test`. Then each publisher:

1. Adds `vinta` + `vinta-levelplay` to the app (it already has the LevelPlay SDK 7.3+).
2. LevelPlay dashboard → SDK Networks → **VINTA**: App key = their VINTA app key (`va_...`).
3. Per instance: `unitId` = the VINTA ad unit id of the same format (`test` = `true` while testing), and a rate.
4. Rewarded: the reward comes from the LevelPlay placement; LevelPlay's server-to-server callbacks cover it.
5. Banners: leave auto-refresh **off** on the VINTA unit; LevelPlay refreshes the mediated banner.
6. Test with the LevelPlay Integration Test Suite.

LevelPlay custom adapters support interstitial, rewarded video and banner (no native).

## Compatibility (blueprint 2.6)

| Adapter | VINTA SDK | Google Mobile Ads (compiled against) | Tested |
|---|---|---|---|
| 0.5.0.0 (LevelPlay) | 0.5.0 | LevelPlay SDK 9.6.1 (compileOnly) | builds against the real SDK; interstitial, rewarded, banner; awaiting Unity registration |
| 0.5.0.0 (MAX) | 0.5.0 | AppLovin SDK 13.6.4 (compileOnly) | builds against the real SDK; all 6 formats; not yet in a live MAX unit |
| 0.3.0.0 | 0.3.0 | 24.9.0 (compileOnly) | builds; adds app open, rewarded interstitial, native mapper |
| 0.2.0.0 | 0.2.0 | 24.9.0 (compileOnly: uses the app's version) | builds; not yet in a live mediation group |
| 0.1.0.0 | 0.1.0 | 24.9.0 | superseded (unsigned requests are no longer billed) |

## Not in 0.1 yet

- VAST/MRAID, OM SDK (blueprint phases 2–4)
- Play Integrity: not available to third-party SDKs (only the app's own developer can decode its tokens); VINTA uses signed per-install keys + emulator signals instead
- Persisted offline event queue (events retry in memory)
- Maven Central (VINTA's own Maven repo works now: `gradlew publishToConsole` → `<console>/maven`)
- AppLovin MAX / Unity LevelPlay adapters
