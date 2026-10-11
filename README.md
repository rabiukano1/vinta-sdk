# VINTA — the halal ad network

VINTA is the Sharia-compliant ad system of **View to Earn**, a project on
[Sidra Start](https://www.sidrastart.com). It shows people only approved,
halal ads, matched to interests **they choose themselves**, and pays
publishers for real views by real users.

This repository holds VINTA's **public deliverables**: the SDKs that apps and
websites integrate, the AdMob mediation adapter, example apps and the roadmap.
The ad server, dashboards and payment systems are run by VINTA and are not
part of this repository.

## What's here

| Folder | What | Status |
|---|---|---|
| `android/vinta` | Android SDK (Kotlin, works from Java), minSdk 21, no third-party dependencies | 0.5.0 |
| `android/vinta-admob` | AdMob mediation adapter (custom event) | 0.5.0.0 |
| `android/sample` | Android test app | |
| `react-native` | React Native wrapper (`react-native-vinta`) + example | 0.2.0 |
| `flutter/vinta_ads` | Flutter plugin (`vinta_ads`) + example | 0.2.0 |
| `web/vinta.js` | Web SDK for websites, PWAs and Telegram mini apps (review copy) | see file header |
| `ROADMAP.md` | What's built and what's next | |

Ad formats: banner, interstitial, app open, rewarded, rewarded interstitial
(intro with Skip), native. Video ads play with sound and a mute button.

## Principles built into the SDKs

- **Halal only.** Every ad is reviewed before it can serve; haram categories
  (alcohol, gambling, interest-based lending, adult, dating, astrology…) are
  banned network-wide.
- **Ads people choose.** Users pick their interests in a built-in picker.
  Interests stay **on the device** and are sent with each ad request; VINTA's
  servers never store them. No advertising ID, no tracking, no profiles.
- **Fixed rewards.** A rewarded ad's reward is fixed and shown before
  watching (ju'alah). No random rewards.
- **Real traffic.** Each install signs its requests with its own key;
  emulator and data-center traffic is never billed.
- **Privacy.** IAB TCF 2.2 and GPP signals are honoured, child-directed apps
  get family-safe ads only, and users can ask for their data to be deleted.

## Quick start

**Android** (`settings.gradle.kts` → repositories, then app dependencies):
```kotlin
maven { url = uri("https://vinta-console.pages.dev/maven") }
implementation("org.view2earn.vinta:vinta:0.5.0")
```
```kotlin
Vinta.init(context, "va_your_app_key", test = BuildConfig.DEBUG)
VintaInterests.showPicker(activity)                 // let users choose interests
VintaBannerView(context).also { layout.addView(it) }.load("<banner unit>")
```

**Web / Telegram mini app:**
```html
<script src="https://site.view2earn.org/vinta/sdk.js"></script>
<script>
  VINTA.init({ appKey: "va_your_app_key", test: true });
  VINTA.showInterestPicker();
  VINTA.banner(document.getElementById("slot"), "<banner unit>");
</script>
```

**React Native / Flutter:** see `react-native/README.md` and
`flutter/vinta_ads/README.md`.

App keys and ad units come from the VINTA publisher console. Each folder's
README has the full API.

## License

Source available for review and verification; see `LICENSE`.
