# react-native-vinta

VINTA (halal ad network) for React Native. Android now; on iOS every call
resolves as "no fill" until the iOS SDK ships, so the same code runs on both.

## Install

```sh
npm install ./vinta-sdk/react-native   # or the published package when available
```

The Android module pulls `org.view2earn.vinta:vinta` from VINTA's Maven repo.
If your `android/settings.gradle` sets `repositoriesMode` to
`FAIL_ON_PROJECT_REPOS`, add the repo there too:

```gradle
maven { url "https://vinta-console.pages.dev/maven" }
```

## Use

```tsx
import { Vinta, VintaBanner, VintaNativeAdView, useVintaNative } from "react-native-vinta";

await Vinta.init("va_your_app_key", { test: __DEV__ /*, childDirected: true for kids' apps */ });

<VintaBanner unitId="<banner unit>" style={{ height: 50 }} />

await Vinta.showInterstitial("<unit>");
await Vinta.showAppOpen("<unit>");
const r = await Vinta.showRewarded("<unit>", { userId: user.id }); // grant from your SSV callback
await Vinta.showRewardedInterstitial("<unit>", { userId: user.id });

function FeedAd() {
  const ad = useVintaNative("<native unit>");
  if (!ad) return null;
  return (
    <VintaNativeAdView ad={ad} style={{ margin: 12 }}>
      <Image source={{ uri: ad.iconUrl! }} style={{ width: 32, height: 32 }} />
      <Text>{ad.title}</Text>
      <Text>{ad.advertiser}</Text>
      <Image source={{ uri: ad.imageUrl }} style={{ width: "100%", aspectRatio: 1.9 }} />
      <Text>{ad.body}</Text>
      <Text>{ad.cta}</Text>
    </VintaNativeAdView>
  );
}
```

Calls never reject: failures resolve with `shown: false` and an `error`.
`Vinta.deviceId()` returns the install id for privacy deletion requests.

Built on VINTA Android SDK 0.4.0; checked against React Native 0.86 (legacy
module API, runs on the New Architecture via interop).
