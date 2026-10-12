export declare const VINTA_SDK_URL: string;

export interface VintaInitOptions {
  appKey: string;
  /** Test ads: never billed or paid. Use while developing. */
  test?: boolean;
  /** COPPA: the page is for children, so only family-safe ads. */
  childDirected?: boolean;
}

export interface LoadVintaOptions extends Partial<VintaInitOptions> {
  /** Override the SDK URL (testing only). */
  src?: string;
}

export interface VintaAdOptions {
  /** Your user id, passed to your rewarded server-side verification callback. */
  userId?: string;
  customData?: string;
}

export interface VintaReward {
  name: string;
  amount: number;
}

export interface VintaRewardResult {
  shown: boolean;
  rewarded: boolean;
  reward: VintaReward | null;
}

export interface VintaNativeAd {
  title: string | null;
  body: string | null;
  cta: string | null;
  iconUrl: string | null;
  imageUrl: string | null;
  advertiser: string | null;
  test: boolean;
  /** Call once with the element that shows the ad: adds the label, click and impression tracking. */
  attach(element: HTMLElement): void;
}

export interface Vinta {
  version: string;
  init(options: VintaInitOptions): Promise<unknown | null>;
  config(): unknown | null;
  deviceId(): Promise<string>;
  interests(): string[];
  setInterests(ids: string[]): string[];
  showInterestPicker(): Promise<string[]>;
  interestOptions(): { id: string; label: string }[];
  /** Resolves true when an ad was placed in the container. */
  banner(container: HTMLElement, unitId: string, options?: VintaAdOptions): Promise<boolean>;
  showInterstitial(unitId: string): Promise<{ shown: boolean }>;
  showAppOpen(unitId: string): Promise<{ shown: boolean }>;
  /** Grant the reward from your server callback, not from this result. */
  showRewarded(unitId: string, options?: VintaAdOptions): Promise<VintaRewardResult>;
  showRewardedInterstitial(unitId: string, options?: VintaAdOptions): Promise<VintaRewardResult>;
  native(unitId: string, options?: VintaAdOptions): Promise<VintaNativeAd | null>;
}

export declare function loadVinta(options?: LoadVintaOptions): Promise<Vinta>;

declare global {
  interface Window {
    VINTA?: Vinta;
  }
}
