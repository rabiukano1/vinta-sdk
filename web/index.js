// Loader for the VINTA Web SDK. The SDK itself is always served by VINTA
// (like Google's ad scripts): it signs requests per install and fixes reach
// every site at once, so this package only adds the script tag and types.
export const VINTA_SDK_URL = "https://site.view2earn.org/vinta/sdk.js";

let loading = null;

/**
 * Loads the SDK once and resolves with `window.VINTA`.
 * Pass `appKey` to also call `VINTA.init` (awaited).
 */
export function loadVinta(options = {}) {
  if (typeof window === "undefined") return Promise.reject(new Error("VINTA runs in the browser only"));
  if (!window.VINTA && !loading) {
    loading = new Promise((resolve, reject) => {
      const s = document.createElement("script");
      s.src = options.src || VINTA_SDK_URL;
      s.async = true;
      s.onload = () => (window.VINTA ? resolve(window.VINTA) : reject(new Error("VINTA SDK did not start")));
      s.onerror = () => {
        loading = null; // allow a retry, e.g. after the network comes back
        s.remove();
        reject(new Error("Couldn't load the VINTA SDK"));
      };
      document.head.appendChild(s);
    });
  }
  const ready = window.VINTA ? Promise.resolve(window.VINTA) : loading;
  return ready.then(async (vinta) => {
    if (options.appKey) await vinta.init({ appKey: options.appKey, test: options.test, childDirected: options.childDirected });
    return vinta;
  });
}
