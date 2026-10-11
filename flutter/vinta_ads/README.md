# vinta_ads

VINTA (halal ad network) for Flutter. Android now; on other platforms every
call returns "no fill", so the same app code runs everywhere.

## Install

```yaml
# pubspec.yaml
dependencies:
  vinta_ads:
    path: ../View2Earn/vinta-sdk/flutter/vinta_ads   # or the published package later
```

The Android side pulls `org.view2earn.vinta:vinta` from VINTA's Maven repo
(`https://vinta-console.pages.dev/maven`). To test against the local copy, add
to your app's `android/gradle.properties`:

```
vintaMavenUrl=file:///C:/path/to/vinta/apps/vinta/public/maven
```

## Use

```dart
import 'package:vinta_ads/vinta_ads.dart';

await Vinta.init('va_your_app_key', test: kDebugMode /*, childDirected: true for kids' apps */);

const VintaBanner(unitId: '<banner unit>', height: 50);

await Vinta.showInterstitial('<unit>');
await Vinta.showAppOpen('<unit>');
final r = await Vinta.showRewarded('<unit>', userId: user.id); // grant from your SSV callback
await Vinta.showRewardedInterstitial('<unit>', userId: user.id);

final ad = await Vinta.loadNative('<native unit>');
if (ad != null) {
  VintaNativeAdView(ad: ad, child: MyCard(title: ad.title, body: ad.body, cta: ad.cta,
      iconUrl: ad.iconUrl, imageUrl: ad.imageUrl, advertiser: ad.advertiser));
}
```

Nothing throws: failures come back as `shown: false` with an `error`.
`VintaNativeAdView` adds the "Ad · VINTA" label, reports the impression at
50% visible for 1 second, and handles taps. `Vinta.deviceId()` returns the
install id for privacy deletion requests.

Ads people choose: `Vinta.showInterestPicker()` opens VINTA's "Choose your
interests" dialog (or use `setInterests([...])` / `getInterests()` from your own
settings screen). Choices stay on the device and only rank ads; there is no profiling.

Built on VINTA Android SDK 0.5.0. Try it with `example/`.
