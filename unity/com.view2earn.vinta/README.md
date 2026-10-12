# VINTA Ads for Unity

VINTA (halal ad network) for Unity games. Android now; in the Editor and on
other platforms every call completes as "no fill", so the same code runs everywhere.

## Install

1. Install the **External Dependency Manager for Unity** (EDM4U), the tool most
   ad SDKs use: https://github.com/googlesamples/unity-jar-resolver
2. Window → Package Manager → **+** → *Add package from git URL*:
   ```
   https://github.com/rabiukano1/vinta-sdk.git?path=/unity/com.view2earn.vinta
   ```
3. Android build: EDM4U adds VINTA's Maven repo and the Android SDK
   (`org.view2earn.vinta:vinta`) automatically (Assets → External Dependency
   Manager → Android Resolver → Force Resolve if needed).

## Use

```csharp
using View2Earn.Vinta;

// once, from the main thread (e.g. in your first scene's Start)
Vinta.Init("va_your_app_key", test: Debug.isDebugBuild /*, childDirected: true for kids' games */);

Vinta.ShowBanner("<banner unit>", VintaBannerPosition.Bottom);
Vinta.HideBanner();

Vinta.ShowInterstitial("<unit>", r => Debug.Log("shown: " + r.Shown));
Vinta.ShowAppOpen("<unit>");
Vinta.ShowRewarded("<unit>", playerId, r => {
    // UI only: grant the reward from your server-side verification (SSV) callback
    if (r.Rewarded) ShowThanks(r.RewardAmount, r.RewardName);
});
Vinta.ShowRewardedInterstitial("<unit>", playerId, r => { });

Vinta.ShowInterestPicker(ids => Debug.Log(string.Join(",", ids)));
string id = Vinta.DeviceId; // for privacy deletion requests
```

Every callback runs on Unity's main thread. Nothing throws: failures come back
as `Shown == false` with an `Error`.
