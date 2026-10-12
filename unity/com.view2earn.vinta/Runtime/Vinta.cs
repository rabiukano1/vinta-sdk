// VINTA (halal ad network) for Unity. Android calls the VINTA Android SDK
// (org.view2earn.vinta, pulled in by Editor/VintaDependencies.xml through the
// External Dependency Manager). In the Editor and on iOS every call completes
// as "no fill", so the same game code runs everywhere.
//
// Every callback runs on Unity's main thread, and nothing here throws into
// the game: failures come back as Shown = false with an Error.
// Written in C# 5 on purpose (works with every Unity version VINTA supports).

using System;
using System.Collections.Generic;
using UnityEngine;

namespace View2Earn.Vinta
{
    public enum VintaBannerPosition { Top, Bottom }

    /// <summary>Result of a full-screen ad. Grant rewards from your server callback (SSV), not from this.</summary>
    public sealed class VintaResult
    {
        public bool Shown;
        public bool Rewarded;
        public string RewardName;
        public int RewardAmount;
        public string Error;

        internal static VintaResult Fail(string error)
        {
            var r = new VintaResult();
            r.Error = error;
            return r;
        }
    }

    public static class Vinta
    {
        public const string PluginVersion = "0.1.0";

        /// <summary>Starts VINTA. Call once at startup. onDone(true) when the app key is known to VINTA.</summary>
        public static void Init(string appKey, bool test = false, bool childDirected = false, Action<bool> onDone = null)
        {
            MainThread.Ensure();
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                var vinta = new AndroidJavaClass("org.view2earn.vinta.Vinta");
                vinta.CallStatic("setChildDirected", childDirected);
                vinta.CallStatic("init", Android.Activity, appKey, test, vinta.GetStatic<string>("DEFAULT_ENDPOINT"), new InitCallback(onDone));
                return;
            }
            catch (Exception e)
            {
                Debug.LogWarning("[VINTA] init failed: " + e.Message);
            }
#endif
            if (onDone != null) MainThread.Post(delegate { onDone(false); });
        }

        public static void ShowInterstitial(string unitId, Action<VintaResult> onDone = null)
        {
            ShowFullScreen("org.view2earn.vinta.VintaInterstitial", unitId, null, onDone);
        }

        public static void ShowAppOpen(string unitId, Action<VintaResult> onDone = null)
        {
            ShowFullScreen("org.view2earn.vinta.VintaAppOpen", unitId, null, onDone);
        }

        /// <param name="userId">Your player id: sent to your server-side verification callback.</param>
        public static void ShowRewarded(string unitId, string userId, Action<VintaResult> onDone)
        {
            ShowFullScreen("org.view2earn.vinta.VintaRewarded", unitId, userId ?? "", onDone);
        }

        public static void ShowRewardedInterstitial(string unitId, string userId, Action<VintaResult> onDone)
        {
            ShowFullScreen("org.view2earn.vinta.VintaRewardedInterstitial", unitId, userId ?? "", onDone);
        }

        /// <summary>Shows a banner across the top or bottom of the screen until HideBanner.</summary>
        public static void ShowBanner(string unitId, VintaBannerPosition position = VintaBannerPosition.Bottom)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            Android.ShowBanner(unitId, position);
#endif
        }

        public static void HideBanner()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            Android.HideBanner();
#endif
        }

        /// <summary>This install's VINTA id, for privacy deletion requests. Empty outside Android.</summary>
        public static string DeviceId
        {
            get
            {
#if UNITY_ANDROID && !UNITY_EDITOR
                try { return new AndroidJavaClass("org.view2earn.vinta.Vinta").CallStatic<string>("getDeviceId"); }
                catch (Exception) { }
#endif
                return "";
            }
        }

        /// <summary>Interests the player chose (kept on the device only).</summary>
        public static string[] GetInterests()
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try { return Android.ToArray(new AndroidJavaClass("org.view2earn.vinta.VintaInterests").CallStatic<AndroidJavaObject>("get")); }
            catch (Exception) { }
#endif
            return new string[0];
        }

        /// <summary>Saves interests from your own settings screen; unknown ids are dropped. Returns the saved list.</summary>
        public static string[] SetInterests(string[] ids)
        {
#if UNITY_ANDROID && !UNITY_EDITOR
            try { return Android.ToArray(new AndroidJavaClass("org.view2earn.vinta.VintaInterests").CallStatic<AndroidJavaObject>("set", Android.ToList(ids))); }
            catch (Exception) { }
#endif
            return new string[0];
        }

        /// <summary>VINTA's ready-made "Choose your interests" dialog. onDone gets the saved list.</summary>
        public static void ShowInterestPicker(Action<string[]> onDone = null)
        {
            MainThread.Ensure();
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                new AndroidJavaClass("org.view2earn.vinta.VintaInterests").CallStatic("showPicker", Android.Activity, new PickerCallback(onDone));
                return;
            }
            catch (Exception e)
            {
                Debug.LogWarning("[VINTA] interest picker failed: " + e.Message);
            }
#endif
            if (onDone != null) MainThread.Post(delegate { onDone(GetInterests()); });
        }

        static void ShowFullScreen(string javaClass, string unitId, string userId, Action<VintaResult> onDone)
        {
            MainThread.Ensure();
#if UNITY_ANDROID && !UNITY_EDITOR
            try
            {
                var ad = new AndroidJavaObject(javaClass);
                var load = new LoadListener(ad, onDone);
                // Never pass null through the JNI bridge: pick the overload instead.
                if (string.IsNullOrEmpty(userId)) ad.Call("load", unitId, load);
                else ad.Call("load", unitId, userId, load);
                return;
            }
            catch (Exception e)
            {
                Finish(onDone, VintaResult.Fail(e.Message));
                return;
            }
#else
            Finish(onDone, VintaResult.Fail("VINTA ads run on Android only for now"));
#endif
        }

        internal static void Finish(Action<VintaResult> onDone, VintaResult result)
        {
            if (onDone != null) MainThread.Post(delegate { onDone(result); });
        }

#if UNITY_ANDROID && !UNITY_EDITOR
        // ── Android bridge ───────────────────────────────────────────────────

        static class Android
        {
            static AndroidJavaObject banner;

            internal static AndroidJavaObject Activity
            {
                get { return new AndroidJavaClass("com.unity3d.player.UnityPlayer").GetStatic<AndroidJavaObject>("currentActivity"); }
            }

            internal static void OnUi(AndroidJavaRunnable run)
            {
                Activity.Call("runOnUiThread", run);
            }

            internal static void ShowBanner(string unitId, VintaBannerPosition position)
            {
                var activity = Activity;
                OnUi(delegate
                {
                    try
                    {
                        RemoveBanner();
                        const int MATCH_PARENT = -1, WRAP_CONTENT = -2, TOP = 48, BOTTOM = 80, CENTER_HORIZONTAL = 1;
                        int gravity = (position == VintaBannerPosition.Top ? TOP : BOTTOM) | CENTER_HORIZONTAL;
                        banner = new AndroidJavaObject("org.view2earn.vinta.VintaBannerView", activity);
                        var lp = new AndroidJavaObject("android.widget.FrameLayout$LayoutParams", MATCH_PARENT, WRAP_CONTENT, gravity);
                        var content = activity.Call<AndroidJavaObject>("findViewById", 0x01020002); // android.R.id.content
                        content.Call("addView", banner, lp);
                        banner.Call("load", unitId);
                    }
                    catch (Exception e)
                    {
                        Debug.LogWarning("[VINTA] banner failed: " + e.Message);
                    }
                });
            }

            internal static void HideBanner()
            {
                OnUi(delegate { RemoveBanner(); });
            }

            static void RemoveBanner()
            {
                if (banner == null) return;
                try
                {
                    banner.Call("destroy");
                    var parent = banner.Call<AndroidJavaObject>("getParent");
                    if (parent != null) parent.Call("removeView", banner);
                }
                catch (Exception) { }
                banner = null;
            }

            internal static string[] ToArray(AndroidJavaObject list)
            {
                if (list == null) return new string[0];
                int n = list.Call<int>("size");
                var a = new string[n];
                for (int i = 0; i < n; i++) a[i] = list.Call<string>("get", i);
                return a;
            }

            internal static AndroidJavaObject ToList(string[] ids)
            {
                var list = new AndroidJavaObject("java.util.ArrayList");
                if (ids != null) foreach (var id in ids) list.Call<bool>("add", id);
                return list;
            }
        }

        sealed class InitCallback : AndroidJavaProxy
        {
            readonly Action<bool> onDone;
            public InitCallback(Action<bool> onDone) : base("org.view2earn.vinta.VintaInitCallback") { this.onDone = onDone; }
            public void onComplete(AndroidJavaObject config)
            {
                bool ok = config != null;
                if (onDone != null) MainThread.Post(delegate { onDone(ok); });
            }
        }

        sealed class LoadListener : AndroidJavaProxy
        {
            readonly AndroidJavaObject ad;
            readonly Action<VintaResult> onDone;
            public LoadListener(AndroidJavaObject ad, Action<VintaResult> onDone) : base("org.view2earn.vinta.VintaLoadListener")
            {
                this.ad = ad;
                this.onDone = onDone;
            }
            public void onLoadResult(AndroidJavaObject error)
            {
                if (error != null)
                {
                    Finish(onDone, VintaResult.Fail(error.Call<string>("getMessage")));
                    return;
                }
                var show = new ShowListener(onDone);
                var activity = Android.Activity;
                Android.OnUi(delegate
                {
                    try { ad.Call("show", activity, show); }
                    catch (Exception e) { Finish(onDone, VintaResult.Fail(e.Message)); }
                });
            }
        }

        sealed class ShowListener : AndroidJavaProxy
        {
            readonly Action<VintaResult> onDone;
            readonly VintaResult result = new VintaResult();
            bool finished;
            public ShowListener(Action<VintaResult> onDone) : base("org.view2earn.vinta.VintaShowListener") { this.onDone = onDone; }
            public void onShown() { result.Shown = true; }
            public void onClicked() { }
            public void onRewarded(AndroidJavaObject reward)
            {
                result.Rewarded = true;
                result.RewardName = reward.Call<string>("getName");
                result.RewardAmount = reward.Call<int>("getAmount");
            }
            public void onShowFailed(AndroidJavaObject error)
            {
                result.Error = error.Call<string>("getMessage");
                Done();
            }
            public void onClosed() { Done(); }
            void Done()
            {
                if (finished) return;
                finished = true;
                Finish(onDone, result);
            }
        }

        sealed class PickerCallback : AndroidJavaProxy
        {
            readonly Action<string[]> onDone;
            public PickerCallback(Action<string[]> onDone) : base("kotlin.jvm.functions.Function1") { this.onDone = onDone; }
            public AndroidJavaObject invoke(AndroidJavaObject list)
            {
                var ids = Android.ToArray(list);
                if (onDone != null) MainThread.Post(delegate { onDone(ids); });
                return null; // Kotlin Unit
            }
        }
#endif
    }

    /// <summary>Runs VINTA callbacks on Unity's main thread (Android calls back on its own UI thread).</summary>
    sealed class MainThread : MonoBehaviour
    {
        static MainThread instance;
        static readonly Queue<Action> queue = new Queue<Action>();

        internal static void Ensure()
        {
            if (instance != null) return;
            var go = new GameObject("VINTA");
            DontDestroyOnLoad(go);
            instance = go.AddComponent<MainThread>();
        }

        internal static void Post(Action a)
        {
            lock (queue) queue.Enqueue(a);
        }

        void Update()
        {
            while (true)
            {
                Action a;
                lock (queue)
                {
                    if (queue.Count == 0) return;
                    a = queue.Dequeue();
                }
                try { a(); }
                catch (Exception e) { Debug.LogException(e); }
            }
        }
    }
}
