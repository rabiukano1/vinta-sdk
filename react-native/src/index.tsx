// VINTA for React Native (blueprint 3.1 wrappers). Android today; on iOS every
// call resolves as "no fill" until the iOS SDK ships, so the same app code runs.
import React, { useEffect, useState } from "react";
import { NativeModules, Platform, Pressable, StyleSheet, Text, View, requireNativeComponent, type StyleProp, type ViewStyle } from "react-native";

export type VintaUnit = {
  id: string;
  name: string;
  format: "banner" | "interstitial" | "rewarded" | "app_open" | "rewarded_interstitial" | "native";
  rewardName: string | null;
  rewardAmount: number | null;
};
export type VintaConfig = { name: string; status: string; country: string | null; units: VintaUnit[] };
export type VintaShowResult = { shown: boolean; error?: string; code?: number };
export type VintaRewardResult = VintaShowResult & { rewarded: boolean; reward: { name: string; amount: number } | null };
export type VintaNativeAd = {
  token: string;
  title: string;
  body: string;
  cta: string;
  advertiser: string;
  iconUrl: string | null;
  imageUrl: string;
  test: boolean;
};

const native = Platform.OS === "android" ? NativeModules.VintaAds : null;
const NO_FILL: VintaRewardResult = { shown: false, rewarded: false, reward: null, error: "not supported on this platform yet" };

export const Vinta = {
  /** Start the SDK. Returns the app config, or null (unknown key / offline / iOS). */
  init(appKey: string, opts: { test?: boolean; childDirected?: boolean } = {}): Promise<VintaConfig | null> {
    return native ? native.init(appKey, !!opts.test, !!opts.childDirected) : Promise.resolve(null);
  },
  /** COPPA: true for child-directed users/apps → family-safe ads only. */
  setChildDirected(value: boolean) {
    native?.setChildDirected(value);
  },
  /** User-chosen interests (saved on the device only, never on VINTA's server). */
  getInterests(): Promise<string[]> {
    return native ? native.getInterests() : Promise.resolve([]);
  },
  /** Save interests from your own settings screen; unknown ids are dropped. */
  setInterests(ids: string[]): Promise<string[]> {
    return native ? native.setInterests(ids) : Promise.resolve([]);
  },
  /** VINTA's ready-made "Choose your interests" dialog. Resolves with the saved list. */
  showInterestPicker(): Promise<string[]> {
    return native ? native.showInterestPicker() : Promise.resolve([]);
  },
  /** This install's VINTA id, for privacy deletion requests. */
  deviceId(): Promise<string | null> {
    return native ? native.deviceId() : Promise.resolve(null);
  },
  showInterstitial(unitId: string): Promise<VintaShowResult> {
    return native ? native.showInterstitial(unitId) : Promise.resolve(NO_FILL);
  },
  /** On launch / return to the app. Keep it rare (e.g. once per session). */
  showAppOpen(unitId: string): Promise<VintaShowResult> {
    return native ? native.showAppOpen(unitId) : Promise.resolve(NO_FILL);
  },
  /** `rewarded` is a UI hint: grant the reward from your SSV callback. */
  showRewarded(unitId: string, opts: { userId?: string; customData?: string } = {}): Promise<VintaRewardResult> {
    return native ? native.showRewarded(unitId, opts.userId ?? null, opts.customData ?? null) : Promise.resolve(NO_FILL);
  },
  /** Rewarded at a natural break: VINTA shows a 3-second intro with Skip first. */
  showRewardedInterstitial(unitId: string, opts: { userId?: string; customData?: string } = {}): Promise<VintaRewardResult> {
    return native ? native.showRewardedInterstitial(unitId, opts.userId ?? null, opts.customData ?? null) : Promise.resolve(NO_FILL);
  },
  /** Native ad assets, or null on no fill. Render them inside <VintaNativeAdView>. */
  loadNative(unitId: string): Promise<VintaNativeAd | null> {
    return native ? native.loadNative(unitId) : Promise.resolve(null);
  },
};

const BannerNative = Platform.OS === "android" ? requireNativeComponent<{ unitId: string; style?: StyleProp<ViewStyle> }>("VintaBannerView") : null;

/** Banner. Give it a height (e.g. 50 for 320x50, 250 for 300x250). */
export function VintaBanner({ unitId, style }: { unitId: string; style?: StyleProp<ViewStyle> }) {
  if (!BannerNative) return null;
  return <BannerNative unitId={unitId} style={[{ width: "100%", height: 50 }, style]} />;
}

const NativeContainer =
  Platform.OS === "android" ? requireNativeComponent<{ adToken: string; style?: StyleProp<ViewStyle>; children?: React.ReactNode }>("VintaNativeAdView") : null;

/**
 * Wrap your own native-ad layout in this. It shows the required "Ad · VINTA"
 * label, counts the impression (50% visible for 1s) and handles taps.
 */
export function VintaNativeAdView({ ad, style, children }: { ad: VintaNativeAd; style?: StyleProp<ViewStyle>; children: React.ReactNode }) {
  useEffect(() => () => native?.destroyNative(ad.token), [ad.token]);
  if (!NativeContainer) return null;
  return (
    <NativeContainer adToken={ad.token} style={style}>
      <Pressable onPress={() => native?.nativeClick(ad.token)}>
        {children}
        <View style={styles.label} pointerEvents="none">
          <Text style={styles.labelText}>{ad.test ? "Test ad · VINTA" : "Ad · VINTA"}</Text>
        </View>
      </Pressable>
    </NativeContainer>
  );
}

/** Convenience hook: load a native ad once for a unit. */
export function useVintaNative(unitId: string) {
  const [ad, setAd] = useState<VintaNativeAd | null>(null);
  useEffect(() => {
    let alive = true;
    Vinta.loadNative(unitId).then((a) => alive && setAd(a));
    return () => {
      alive = false;
    };
  }, [unitId]);
  return ad;
}

const styles = StyleSheet.create({
  label: { position: "absolute", top: 4, right: 4, backgroundColor: "rgba(0,0,0,0.55)", borderRadius: 4, paddingHorizontal: 5, paddingVertical: 1 },
  labelText: { color: "#fff", fontSize: 9, fontWeight: "600" },
});
