// VINTA Web SDK 0.6.0. Served by VINTA at https://site.view2earn.org/vinta/sdk.js
// Load it from there (it uses its own origin as the API base); this copy is for review.
(function () {
  "use strict";
  if (window.VINTA) return;
  var script = document.currentScript;
  var API = script ? new URL(script.src).origin : "";
  var cfg = { appKey: "", test: false, childDirected: false };
  var config = null; // remote config from /vinta/v1/init
  var TIMEOUT_MS = 4000; // well under a mediation's ~5s timeout
  var REWARD_IMAGE_SECONDS = 15; // server needs >= 10s on screen
  var INTERSTITIAL_CLOSE_SECONDS = 5;
  // Safe areas: Telegram full-screen mini apps draw Close / v / ... over the top
  // of the page and report it as --tg-* CSS variables; phones report env().
  // Full-screen ad controls sit below/above the larger of the two.
  var SAFE_TOP = "max(env(safe-area-inset-top, 0px), calc(var(--tg-safe-area-inset-top, 0px) + var(--tg-content-safe-area-inset-top, 0px)))";
  var SAFE_BOTTOM = "max(env(safe-area-inset-bottom, 0px), calc(var(--tg-safe-area-inset-bottom, 0px) + var(--tg-content-safe-area-inset-bottom, 0px)))";
  var below = function (px) { return "calc(" + px + "px + " + SAFE_TOP + ")"; };
  var above = function (px) { return "calc(" + px + "px + " + SAFE_BOTTOM + ")"; };
  var APP_OPEN_CLOSE_SECONDS = 2; // app open should get out of the way fast
  var REWARDED_INTRO_SECONDS = 3; // rewarded interstitial: "Ad starting in 3… Skip"

  // Per-install signing key (blueprint 10.1). ECDSA P-256, private key
  // non-extractable, kept in IndexedDB. deviceId = first 32 hex of
  // SHA-256(public key), so the server can tie every request to this install.
  // Without WebCrypto/IndexedDB requests go unsigned (served, never billed).
  var identity = null;
  function idb(mode, fn) {
    return new Promise(function (res, rej) {
      var open = indexedDB.open("vinta", 1);
      open.onupgradeneeded = function () { open.result.createObjectStore("keys"); };
      open.onerror = function () { rej(open.error); };
      open.onsuccess = function () {
        var tx = open.result.transaction("keys", mode), req = fn(tx.objectStore("keys"));
        tx.oncomplete = function () { res(req.result); };
        tx.onerror = function () { rej(tx.error); };
      };
    });
  }
  function b64(buf) { var s = "", a = new Uint8Array(buf); for (var i = 0; i < a.length; i++) s += String.fromCharCode(a[i]); return btoa(s); }
  function hex(buf) { return Array.prototype.map.call(new Uint8Array(buf), function (b) { return ("0" + b.toString(16)).slice(-2); }).join(""); }
  function getIdentity() {
    if (identity) return identity;
    identity = (function () {
      if (!(window.crypto && crypto.subtle && window.indexedDB)) return Promise.reject(new Error("no webcrypto"));
      return idb("readonly", function (st) { return st.get("device"); }).then(function (saved) {
        if (saved) return saved;
        return crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, false, ["sign"]).then(function (pair) {
          return crypto.subtle.exportKey("spki", pair.publicKey).then(function (spki) {
            return crypto.subtle.digest("SHA-256", spki).then(function (h) {
              var rec = { privateKey: pair.privateKey, spki: b64(spki), deviceId: hex(h).slice(0, 32) };
              return idb("readwrite", function (st) { return st.put(rec, "device"); }).then(function () { return rec; });
            });
          });
        });
      });
    })().catch(function () {
      var id;
      try { id = localStorage.getItem("vinta_device_id"); } catch (e) {}
      if (!id) { id = "u" + String(Math.random()).slice(2) + Date.now(); try { localStorage.setItem("vinta_device_id", id); } catch (e) {} }
      return { deviceId: id, privateKey: null, spki: null };
    });
    return identity;
  }
  function signedHeaders(id, path, text) {
    var h = { "Content-Type": "application/json" };
    if (!id.privateKey) return Promise.resolve(h);
    var ts = String(Date.now());
    var nonce = hex(crypto.getRandomValues(new Uint8Array(16)));
    var NL = String.fromCharCode(10); // newline separator of the signed string (no escapes inside String.raw)
    return crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, id.privateKey, new TextEncoder().encode(ts + NL + nonce + NL + path + NL + text))
      .then(function (sig) { h["X-Vinta-Key"] = id.spki; h["X-Vinta-Ts"] = ts; h["X-Vinta-Nonce"] = nonce; h["X-Vinta-Sig"] = b64(sig); return h; });
  }
  function context() {
    var ua = navigator.userAgent;
    var os = /android/i.test(ua) ? "android" : /iphone|ipad|ipod/i.test(ua) ? "ios" : "other";
    var device = /ipad|tablet/i.test(ua) || (os === "android" && !/mobile/i.test(ua)) ? "tablet" : /mobi|iphone|android/i.test(ua) ? "phone" : "desktop";
    return { os: os, device: device, language: (navigator.language || "").slice(0, 2).toLowerCase(), interests: getInterests() };
  }

  // Interests (blueprint 0 + 24.9): chosen by the user, saved only on this
  // device, sent with ad requests so ads match what they asked for. VINTA's
  // server never stores them. Keep in sync with INTERESTS in vinta/money.ts.
  var INTEREST_LABELS = {
    islamic_education: "Islamic learning", islamic_finance_takaful: "Islamic finance & Takaful", halal_food: "Halal food",
    modest_fashion: "Modest fashion", hajj_umrah: "Hajj & Umrah", apps_utilities: "Apps & tools", education: "Education",
    ecommerce: "Shopping", telecom: "Phones & data", music: "Music", entertainment_media: "Films & shows",
    fashion_beauty: "Fashion & beauty", crypto: "Crypto & blockchain"
  };
  var INTEREST_KEY = "vinta_interests";
  function getInterests() {
    try { var v = JSON.parse(localStorage.getItem(INTEREST_KEY) || "[]"); return Array.isArray(v) ? v.filter(function (i) { return INTEREST_LABELS[i]; }) : []; }
    catch (e) { return []; }
  }
  function setInterests(list) {
    var clean = (list || []).filter(function (i, n, a) { return INTEREST_LABELS[i] && a.indexOf(i) === n; });
    try { localStorage.setItem(INTEREST_KEY, JSON.stringify(clean)); } catch (e) {}
    return clean;
  }
  // Ready-made "Choose your interests" screen. Resolves with the saved list
  // (unchanged if the user cancels).
  function showInterestPicker() {
    return new Promise(function (resolve) {
      var chosen = getInterests();
      var overlay = el("div", "position:fixed;inset:0;z-index:2147483647;background:rgba(0,0,0,.55);display:flex;align-items:flex-end;justify-content:center");
      var sheet = el("div", "background:#fff;color:#111;width:100%;max-width:480px;border-radius:18px 18px 0 0;padding:20px 16px " + above(16) + ";font:15px system-ui;max-height:85vh;overflow:auto");
      sheet.appendChild(el("div", "font-weight:700;font-size:18px;margin-bottom:4px", "Choose your interests"));
      sheet.appendChild(el("div", "color:#555;font-size:13px;margin-bottom:12px", "You'll see ads about these. Saved only on this device; you can change them any time."));
      var grid = el("div", "display:flex;flex-wrap:wrap;gap:8px");
      Object.keys(INTEREST_LABELS).forEach(function (k) {
        var b = el("button", "", INTEREST_LABELS[k]);
        var paint = function () {
          var on = chosen.indexOf(k) >= 0;
          b.style.cssText = "padding:8px 12px;border-radius:999px;font:600 13px system-ui;cursor:pointer;border:1px solid " + (on ? "#10B981" : "#ccc") + ";background:" + (on ? "#10B981" : "#fff") + ";color:" + (on ? "#fff" : "#111");
          b.setAttribute("aria-pressed", on ? "true" : "false");
        };
        b.onclick = function () { var i = chosen.indexOf(k); if (i >= 0) chosen.splice(i, 1); else chosen.push(k); paint(); };
        paint();
        grid.appendChild(b);
      });
      sheet.appendChild(grid);
      var row = el("div", "display:flex;gap:8px;margin-top:16px");
      var cancel = el("button", "flex:1;padding:12px;border-radius:10px;border:1px solid #ccc;background:#fff;font:600 14px system-ui;cursor:pointer", "Cancel");
      var save = el("button", "flex:2;padding:12px;border-radius:10px;border:0;background:#10B981;color:#fff;font:700 14px system-ui;cursor:pointer", "Save");
      cancel.onclick = function () { overlay.remove(); resolve(getInterests()); };
      save.onclick = function () { overlay.remove(); resolve(setInterests(chosen)); };
      row.appendChild(cancel); row.appendChild(save);
      sheet.appendChild(row);
      overlay.appendChild(sheet);
      document.body.appendChild(overlay);
    });
  }
  // body is built with the install's deviceId, then signed exactly as sent.
  function post(path, makeBody) {
    return getIdentity().then(function (id) {
      var text = JSON.stringify(makeBody(id.deviceId));
      return signedHeaders(id, path, text).then(function (headers) {
        var ctl = typeof AbortController !== "undefined" ? new AbortController() : null;
        var t = ctl ? setTimeout(function () { ctl.abort(); }, TIMEOUT_MS) : 0;
        return fetch(API + path, { method: "POST", headers: headers, body: text, signal: ctl ? ctl.signal : undefined })
          .then(function (r) { clearTimeout(t); if (r.status === 204) return null; return r.json().then(function (j) { if (!r.ok) throw new Error(j.error || ("HTTP " + r.status)); return j; }); });
      });
    });
  }
  // Consent signals from the page's CMP (blueprint 13): IAB TCF 2.2 and IAB
  // GPP, read with a short timeout so a slow CMP never delays the ad much.
  function privacy() {
    var out = { coppa: cfg.childDirected || undefined };
    var wait = function (fn) { return new Promise(function (res) { var t = setTimeout(res, 300); try { fn(function () { clearTimeout(t); res(); }); } catch (e) { clearTimeout(t); res(); } }); };
    var tcf = typeof window.__tcfapi === "function" ? wait(function (done) {
      window.__tcfapi("getTCData", 2, function (tc, ok) {
        if (ok && tc) { if (typeof tc.gdprApplies === "boolean") out.gdpr = tc.gdprApplies ? 1 : 0; if (tc.tcString) out.tcf = tc.tcString; }
        done();
      });
    }) : null;
    var gpp = typeof window.__gpp === "function" ? wait(function (done) {
      window.__gpp("ping", function (d, ok) {
        if (ok !== false && d) { if (d.gppString) out.gpp = d.gppString; if (d.applicableSections) out.gppSid = [].concat(d.applicableSections).join("_"); }
        done();
      });
    }) : null;
    return Promise.all([tcf, gpp]).then(function () { return out; });
  }
  function load(unitId, opts) {
    if (!cfg.appKey) return Promise.reject(new Error("VINTA.init({ appKey }) first"));
    opts = opts || {};
    return privacy().then(function (pv) { return post("/vinta/v1/ad", function (deviceId) {
      return {
        privacy: pv,
        appKey: cfg.appKey, adUnitId: unitId, deviceId: deviceId, userId: opts.userId, customData: opts.customData,
        context: context(), test: !!cfg.test,
        signals: { automation: navigator.webdriver === true }, // headless / automated browser (blueprint 10.1)
      };
    }); });
  }
  function track(ad, event) {
    return post("/vinta/v1/events", function (deviceId) { return { serveId: ad.serveId, deviceId: deviceId, event: event }; }).catch(function () { return null; });
  }
  function el(tag, css, text) { var n = document.createElement(tag); n.style.cssText = css; if (text) n.textContent = text; return n; }
  function badge(ad) { return el("span", "position:absolute;top:6px;left:6px;font:600 10px system-ui;color:#fff;background:rgba(0,0,0,.55);padding:2px 6px;border-radius:4px;z-index:1", ad.test ? "Test ad · VINTA" : "Ad · VINTA"); }
  function openClick(ad) {
    track(ad, "click");
    var tg = window.Telegram && window.Telegram.WebApp;
    if (tg && tg.openLink) tg.openLink(ad.clickUrl); else window.open(ad.clickUrl, "_blank", "noopener,noreferrer");
  }

  // Banner: impression after >= 50% visible for 1s (MRC). Auto-refresh when the unit sets refreshSec.
  function banner(container, unitId, opts) {
    try {
      return load(unitId, opts).then(function (ad) {
        if (!ad || !ad.mediaUrl) return false;
        var box = el("div", "position:relative;cursor:pointer;display:block;width:100%");
        var img = el("img", "display:block;width:100%;height:auto");
        img.src = ad.mediaUrl; img.alt = ad.headline || "Sponsored";
        box.appendChild(img); box.appendChild(badge(ad));
        box.onclick = function () { openClick(ad); };
        container.innerHTML = ""; container.appendChild(box);
        img.onload = function () {
          var timer = 0;
          var io = new IntersectionObserver(function (e) {
            clearTimeout(timer);
            if (!e[0].isIntersecting) return;
            timer = setTimeout(function () { io.disconnect(); track(ad, "impression"); }, 1000);
          }, { threshold: 0.5 });
          io.observe(box);
        };
        if (ad.refreshSec) setTimeout(function () { if (document.body.contains(box)) banner(container, unitId, opts); }, Math.min(120, Math.max(30, ad.refreshSec)) * 1000);
        return true;
      }).catch(function (e) { console.warn("[VINTA]", e.message); return false; });
    } catch (e) { return Promise.resolve(false); }
  }

  // Full screen (interstitial / app_open / rewarded / rewarded_interstitial).
  // Resolves when closed; never rejects.
  function fullscreen(unitId, mode, opts) {
    var rewarded = mode === "rewarded" || mode === "rewarded_interstitial";
    return load(unitId, opts).then(function (ad) {
      if (!ad || !ad.mediaUrl) return { shown: false, rewarded: false, reward: null };
      return new Promise(function (resolve) {
        var done = false, timer = 0, impressed = false;
        var overlay = el("div", "position:fixed;inset:0;z-index:2147483647;background:#000;display:flex;align-items:center;justify-content:center");
        var status = el("span", "position:absolute;top:" + below(12) + ";right:56px;font:600 13px system-ui;color:#fff");
        var close = el("button", "position:absolute;top:" + below(8) + ";right:12px;width:36px;height:36px;border-radius:50%;border:0;background:rgba(255,255,255,.2);color:#fff;font:700 18px system-ui;cursor:pointer", "✕");
        close.setAttribute("aria-label", "Close ad");
        var cta = el("button", "position:absolute;bottom:" + above(24) + ";left:16px;right:16px;padding:14px;border:0;border-radius:12px;background:#10B981;color:#fff;font:700 15px system-ui;cursor:pointer", ad.headline || "Learn more");
        var reward = ad.rewardName ? { name: ad.rewardName, amount: ad.rewardAmount } : null;
        function finish() { clearInterval(timer); overlay.remove(); resolve({ shown: impressed, rewarded: done, reward: done ? reward : null }); }
        function complete() {
          track(ad, "complete").then(function (r) { done = !!(r && r.completed); status.textContent = done ? "Reward earned ✓" : ""; });
        }
        function countdown(n, onDone, label) {
          status.textContent = label(n);
          timer = setInterval(function () { n--; if (n > 0) { status.textContent = label(n); return; } clearInterval(timer); status.textContent = ""; onDone(); }, 1000);
        }
        function shown() {
          if (impressed) return; impressed = true; track(ad, "impression");
          if (!rewarded) { close.style.display = "none"; countdown(mode === "app_open" ? APP_OPEN_CLOSE_SECONDS : INTERSTITIAL_CLOSE_SECONDS, function () { close.style.display = ""; }, function (s) { return "Close in " + s; }); }
          else if (ad.mediaType === "image") countdown(REWARD_IMAGE_SECONDS, complete, function (s) { return (reward ? reward.amount + " " + reward.name + " in " : "Reward in ") + s + "s"; });
          else status.textContent = reward ? "Watch to earn " + reward.amount + " " + reward.name : "Watch to the end";
        }
        var media, startPlay = function () {};
        if (ad.mediaType === "video") {
          media = el("video", "max-width:100%;max-height:80vh");
          media.src = ad.mediaUrl; media.playsInline = true; media.muted = false; media.preload = "auto";
          // Speaker button: video plays with sound; tap to mute / unmute.
          var sound = el("button", "position:absolute;left:16px;bottom:" + above(88) + ";width:44px;height:44px;border-radius:50%;border:0;background:rgba(255,255,255,.2);color:#fff;font:20px system-ui;cursor:pointer", "");
          var syncSound = function () { sound.textContent = media.muted ? "🔇" : "🔊"; sound.setAttribute("aria-label", media.muted ? "Unmute ad" : "Mute ad"); };
          sound.onclick = function () { media.muted = !media.muted; syncSound(); if (media.paused) media.play().catch(function () {}); };
          syncSound();
          overlay.appendChild(sound);
          // Try with sound first. Browsers may refuse sound without a recent tap;
          // then play muted and the button shows "tap for sound".
          startPlay = function () {
            media.play().catch(function () { media.muted = true; syncSound(); media.play().catch(function () {}); });
          };
          media.onplaying = shown; media.onended = function () { if (rewarded) complete(); };
        } else {
          media = el("img", "max-width:100%;max-height:80vh;object-fit:contain");
          media.src = ad.mediaUrl; media.alt = ad.headline || "Sponsored"; media.onload = shown;
        }
        media.onerror = finish;
        close.onclick = finish;
        cta.onclick = function () { openClick(ad); };
        var label = badge(ad); label.style.top = below(12); label.style.left = "12px";
        overlay.appendChild(label); overlay.appendChild(status); overlay.appendChild(close); overlay.appendChild(cta);
        document.body.appendChild(overlay);
        if (mode !== "rewarded_interstitial") { overlay.insertBefore(media, overlay.firstChild); startPlay(); return; }
        // Rewarded interstitial (blueprint 3.2): shown without an opt-in button,
        // so the user gets a short intro naming the reward and can skip it.
        var intro = el("div", "color:#fff;font:600 17px system-ui;text-align:center;padding:24px");
        var skip = el("button", "margin-top:16px;padding:10px 18px;border:1px solid #fff;border-radius:10px;background:transparent;color:#fff;font:600 14px system-ui;cursor:pointer", "Skip");
        var line = el("div", "", "");
        intro.appendChild(line); intro.appendChild(skip);
        overlay.insertBefore(intro, overlay.firstChild);
        cta.style.display = "none";
        skip.onclick = finish; // skipped before the ad: no impression, no reward
        var n = REWARDED_INTRO_SECONDS;
        var say = function () { line.textContent = (reward ? "Watch a short ad to get " + reward.amount + " " + reward.name + ". " : "") + "Ad starting in " + n + "…"; };
        say();
        var introTimer = setInterval(function () {
          if (!overlay.isConnected) return clearInterval(introTimer);
          n--; if (n > 0) return say();
          clearInterval(introTimer); intro.remove(); cta.style.display = ""; overlay.insertBefore(media, overlay.firstChild); startPlay();
        }, 1000);
      });
    }).catch(function (e) { console.warn("[VINTA]", e.message); return { shown: false, rewarded: false, reward: null }; });
  }

  // Native (blueprint 3.2): returns the ad's assets for the publisher to lay
  // out, or null on no fill. Call ad.attach(element) on the element that shows
  // it: VINTA adds the required "Ad · VINTA" label, counts the impression at
  // 50% visible for 1s, and handles taps.
  function native(unitId, opts) {
    return load(unitId, opts).then(function (ad) {
      if (!ad || ad.format !== "native") return null;
      return {
        title: ad.headline, body: ad.body, cta: ad.cta, iconUrl: ad.iconUrl, imageUrl: ad.mediaUrl,
        advertiser: ad.advertiserName, test: !!ad.test,
        attach: function (elm) {
          if (getComputedStyle(elm).position === "static") elm.style.position = "relative";
          elm.appendChild(badge(ad));
          elm.style.cursor = "pointer";
          elm.addEventListener("click", function () { openClick(ad); });
          var timer = 0;
          var io = new IntersectionObserver(function (e) {
            clearTimeout(timer);
            if (!e[0].isIntersecting) return;
            timer = setTimeout(function () { io.disconnect(); track(ad, "impression"); }, 1000);
          }, { threshold: 0.5 });
          io.observe(elm);
        },
      };
    }).catch(function (e) { console.warn("[VINTA]", e.message); return null; });
  }

  window.VINTA = {
    version: "0.6.0",
    // Returns a promise of the app config (blueprint 3.4); never rejects (null on failure).
    init: function (o) {
      cfg.appKey = (o && o.appKey) || ""; cfg.test = !!(o && o.test);
      // COPPA: set when this page/app is for children → family-safe ads only.
      cfg.childDirected = !!(o && o.childDirected);
      return fetch(API + "/vinta/v1/init?appKey=" + encodeURIComponent(cfg.appKey))
        .then(function (r) { return r.ok ? r.json() : null; })
        .then(function (c) { config = c; if (!c) console.warn("[VINTA] unknown appKey"); return c; })
        .catch(function () { return null; });
    },
    config: function () { return config; },
    // This install's VINTA id, for privacy deletion requests (blueprint 13).
    deviceId: function () { return getIdentity().then(function (id) { return id.deviceId; }); },
    // User-chosen interests (on device only). Use your own settings UI with
    // setInterests/getInterests, or VINTA's ready-made picker.
    interests: function () { return getInterests(); },
    setInterests: setInterests,
    showInterestPicker: showInterestPicker,
    interestOptions: function () { return Object.keys(INTEREST_LABELS).map(function (k) { return { id: k, label: INTEREST_LABELS[k] }; }); },
    banner: banner,
    showInterstitial: function (unitId) { return fullscreen(unitId, "interstitial").then(function (r) { return { shown: r.shown }; }); },
    showAppOpen: function (unitId) { return fullscreen(unitId, "app_open").then(function (r) { return { shown: r.shown }; }); },
    showRewarded: function (unitId, opts) { return fullscreen(unitId, "rewarded", opts); },
    showRewardedInterstitial: function (unitId, opts) { return fullscreen(unitId, "rewarded_interstitial", opts); },
    native: native,
  };
})();
