# Fullscreen Web — Privacy Hardened (v7.1 audit build)

Android fullscreen WebView browser focused on VPN-gated browsing, conservative WebView hardening and a
lightweight ad/tracker shield.

> **Status: NOT BUILT, NOT RUN.** The audit environment had no Gradle wrapper (this archive ships **no `gradlew`/`gradle-wrapper.jar`**),
> no network access to Gradle/Maven/Google, no Android SDK, no Kotlin compiler and no device. No claim below has been verified by a
> compiler, a test run or a device unless it says so. Start with `docs/BUILD_AND_TEST_RUNBOOK.md`; results so far are in
> `docs/audit/V7_1_VERIFICATION_REPORT.md`; the device/VPN/download/WebView protocol is `docs/DEVICE_TEST_PLAN.md`.

## What the source implements (verify on a device before relying on it)

**VPN gating (app-level, not a system kill switch)**
- Browser traffic is allowed only while a VPN-transport network with INTERNET capability exists and the process
  is bound to it (`PrivacyNetworkController`, `VpnGate`).
- On VPN loss the gate closes **synchronously on the callback thread**; `shouldInterceptRequest` (page and
  Service Worker) then refuses every request, the browser WebViews are destroyed on the main thread, and a
  loopback black-hole proxy is installed when `PROXY_OVERRIDE` is supported.
- The process binding is **deliberately kept** after VPN loss (Android makes sockets/DNS bound to a dead
  network fail instead of falling back to the physical network). The previous code unbound the process, which
  re-opened the physical network.
- It does **not** stop other apps, system services (e.g. Safe Browsing lookups by Google Play services) or
  traffic if the VPN provider excludes this app (split tunnelling). For a real kill switch use Android's
  *Always-on VPN* + *Block connections without VPN*.

**Navigation**
- Main-frame navigations: https allowed; `http://` blocked (typed/bookmarked http is upgraded to https);
  URLs with embedded credentials blocked; `data:`, `file:`, `content:`, `javascript:`, custom schemes blocked.
- External apps: only `mailto:` (without `attach`/`attachment` parameters), `tel:`, `sms:`, `smsto:` and only with a user gesture. `intent://` never
  launches an app; only an https `browser_fallback_url` is loaded in the WebView.
- Sub-resource/sub-frame requests, and script- or redirect-initiated (no user gesture) top-level navigations, from a public page to
  loopback/private/link-local literals are refused (LAN/localhost probing); typed addresses and user-gesture navigations are allowed. DNS-rebinding to private addresses is **not** detected.
- Popups: need a user gesture, an open VPN gate and no other popup; the popup dialog always shows its host.
  Popup state no longer overwrites the main page's URL/title/progress.

**Permissions**: camera/microphone/protected-media requests need an `https` origin, are asked **in-app first**,
then (if needed) via the Android runtime prompt, and `grant()` is called only for that exact request. The dialog shows the requesting
origin, the page being viewed, and a warning when the request comes from an embedded frame of a different site. A second
request while one is pending is denied. Pending requests are denied on navigation cancel, renderer crash and
VPN loss. Geolocation is always denied (no location permission is declared).

**Downloads** (in-process `HttpURLConnection`, never `DownloadManager`): user confirmation dialog; https only;
no credentials in URL; private/loopback targets refused (also on redirects); max 5 redirects, no downgrade;
cookies and Referer only for the original origin; VPN gate checked before every connection and every 16 KiB
chunk; partial files are deleted on failure; file names are sanitised. `blob:`/`data:` downloads are not supported.

**Storage**: cookies/DOM storage persist across sessions by design (logins). **Home → "Clear browsing data"**
wipes cookies, WebStorage, cache and history (bookmarks and saved downloads are kept). Exiting the browser only
clears history and the HTTP cache. Android backup and device-transfer are disabled/excluded.

**Fingerprinting**: only partial. `hardwareConcurrency`/`deviceMemory` are pinned to common stable values (4),
`RTCPeerConnection` is removed via document-start JavaScript when the WebView supports it (a toast warns if not).
Canvas, WebGL, AudioContext, fonts, User-Agent/Client Hints, screen and timezone are **not** protected.

**Ad/tracker shield**: exact-host/true-subdomain list; no WebSocket visibility, no CNAME-cloaking detection, no
cosmetic filtering. It is not an EasyList/uBlock equivalent.

**Lifecycle**: the page is `onPause()`d when the app is not visible unless background media is enabled *and*
playing; renderer-crash loops (3 in 60 s) return to Home; the notification never shows page titles or URLs.

## Known limitations / product decisions left unchanged
- Default search engine is Google. `TRANSPORT_VPN` proves a VPN exists, not that it is trustworthy.
- Release build: R8/minify is off (enable only after a full build + device pass). A release build is **unsigned** unless
  `KEYSTORE_PATH`/`STORE_PASSWORD`/`KEY_PASSWORD` are set; `-PallowDebugSignedRelease=true` opts in to debug-key signing for local tests.
- Bookmarks are plain JSON in app-private SharedPreferences.
- The unreferenced `DiagnosticsBottomSheet`/`WebViewDiagnostics` files were removed (no references remained, **[STATIC]**).

## Verify before shipping
See `docs/BUILD_AND_TEST_RUNBOOK.md` (wrapper generation, SDK, the three Gradle commands, compile-risk register) and
`docs/DEVICE_TEST_PLAN.md` (VPN A–F, downloads, WebView matrix, with `tools/test_server/` as the observation harness).
Toolchain-free checks that can be run anywhere: `python3 tools/syntax_balance.py`, `tools/static_checks.py`, `tools/symbol_check.py`,
`tools/verify_merged_manifest.py <merged manifest>`.
