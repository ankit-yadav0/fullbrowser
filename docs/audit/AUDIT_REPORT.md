> **Superseded in part by `V7_1_VERIFICATION_REPORT.md`** (dead diagnostics code removed, release signing changed, top-level private-navigation
> rule, `mailto:` attachment rule, embedded-frame prompt). Everything in this V7 report that says *not executed* is still true.

# Audit report — fullscreen-web-privacy-shield (v6 → v7)

Evidence labels: **[CODE]** read in source · **[STATIC]** grep/scan result (proves only that pattern) ·
**[TEST]** test executed and passed · **[BUILD]** Gradle build succeeded · **[DEVICE]** verified on device ·
**[DOC]** external documentation (does not prove this project's behaviour) · **[INFERENCE]** reasoning, not verified ·
**[UNKNOWN]**. Confidence: HIGH / MEDIUM / LOW / UNKNOWN.

## A. PROJECT STATUS

| Item | Status |
|---|---|
| Gradle build (`assembleDebug`, release, lint) | **[NOT EXECUTED]** no Gradle, Android SDK, Kotlin compiler or network in the audit environment. Compilation of the modified tree **cannot be claimed**. |
| Unit tests (14 files, `app/src/test`) | **[NOT EXECUTED]** the tests exist but were not run. |
| Instrumented tests (`PrivacyDeviceTest`, `ExampleInstrumentedTest`) | **[NOT EXECUTED]** **[DEVICE-EVIDENCE MISSING]** |
| Bracket/paren balance of every `.kt` file | **[STATIC]** 0 imbalances (single-pass scanner). Proves syntax *shape* only, not compilation. |
| Java transliteration of the pure algorithms (`docs/audit/AlgorithmCrossCheck.java`) | **[INFERENCE]** 223/223 assertions pass under JDK 21. It is a *port*, not the Kotlin; it only shows my algorithms and test expectations are mutually consistent. |
| Original tree compiles? | **[CODE]** No: ≥3 definite compile blockers (C-1) plus 1 probable. The README of v6 claimed otherwise. |

## B. FILES AUDITED

Original archive: 57 files; modified tree: 73 files (+16 added, 0 removed, 17 changed incl. README; plus `docs/audit/*`). Main Kotlin LOC 3 377 → 4 263.

Read line by line: `settings.gradle.kts`, root/app `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`,
`gradle-wrapper.properties`, `AndroidManifest.xml`, `proguard-rules.pro`, `backup_rules.xml`, `data_extraction_rules.xml`,
`strings/themes/colors`, `README.md`, `metadata.json`, `.env.example`, and Kotlin: `MainActivity`, `BackgroundAudioService`,
`BookmarkRepository`, `Bookmark`, `UrlHelper`, `AdBlockList`, `AdTrackerStats`, `PrivacyNetworkController`, `PrivateDownloader`,
`DrmDiagnostics`, `WebViewDiagnostics`, `HomeScreen`, `FullscreenWebScreen`, `BookmarksBottomSheet`, `QuickControlFab`,
all original tests.
**Scanned, not line-audited:** `DiagnosticsBottomSheet.kt` (unreferenced UI; checked by grep for side effects only), theme/`Color`/`Type`
Kotlin, launcher drawables/mipmaps (listed, binary/vector content not inspected).
No PIN/crypto/Keystore code exists **[STATIC]** (grep `cipher|keystore|messagedigest|securerandom|pin|password`: only a log-redaction
comment) → Phase 11 is *not applicable*, not "passed".

## C. CRITICAL FINDINGS

**C-1 Original does not compile (≥3 definite blockers).** Fixed in source; **[BUILD NOT EXECUTED]**.
1. `ui/FullscreenWebScreen.kt:297` `privacyNetwork::isReady` — `PrivacyNetworkController` had no such member (only private `ready`, lines 16/46/85). **[CODE]** + **[STATIC]** (single reference, no definition).
2. `ui/FullscreenWebScreen.kt:61` `import androidx.compose.ui.testTag`; the other four files import `androidx.compose.ui.platform.testTag` **[STATIC]**. That the former does not exist: **[INFERENCE, high]**.
3. `service/BackgroundAudioService.onStartCommand`: `intent.getStringExtra(...)` on `intent: Intent?` (no smart cast from `intent?.action`) **[CODE]**; error **[INFERENCE, high]**.
4. (probable) `PrivacyNetworkController` lines 37/41/66/82 pass `null` executor/listener to androidx `ProxyController` (non-null parameters) **[CODE]**; compile or runtime failure **[INFERENCE]**.

**C-2 "Fail-closed on VPN loss" was false: the process was unbound on loss.**
Evidence chain: CLAIM README "Fail closed on VPN loss" → `util/PrivacyNetworkController.start()/apply()` line 34 calls `cm.bindProcessToNetwork(null)` when no VPN is found, and `stop()` line 77 does the same → **[DOC]** Android: sockets/DNS bound via `bindProcessToNetwork` stop working when that network disconnects, *unbinding restores the default (physical) network* → conclusion: after loss, new sockets of the app process can use the physical network unless another layer blocks them **[INFERENCE, MEDIUM]**; the only other layer was a WebView-only proxy installed with null args (C-1.4). Not exercised on a device **[DEVICE-EVIDENCE MISSING]**.
Fix: `PrivacyNetworkController` never unbinds; binding to the dead VPN network is kept, black-hole proxy installed first. Confidence the *source* does this: HIGH **[CODE]**; that Android then blocks traffic: MEDIUM **[DOC]**, unverified on device.

**C-3 Gap between VPN loss and teardown; state written from a foreign thread.**
Chain: `apply()` invoked from `ConnectivityManager` callbacks (lines 50-52, callback thread) → `onStateChanged` writes Compose state off the main thread → WebView destroyed later by `LaunchedEffect(networkReady)` (orig `FullscreenWebScreen.kt:496-505`) → requests issued in between are not gated (`shouldInterceptRequest` had no VPN check) **[CODE]**. Also `findVpn()` re-queries `allNetworks` inside `onLost`, which may still list the dying network **[INFERENCE]**.
Fix: `VpnGate` closes synchronously in the callback; `shouldInterceptRequest` (page + Service Worker) returns a blocked response when closed; teardown runs on the main thread inside the state callback. **[CODE]**; runtime **[DEVICE-EVIDENCE MISSING]**. Not covered: WebSocket/WebRTC/media-pipeline traffic (rely on process binding + proxy only).

## D. HIGH FINDINGS

| ID | Location | Problem | Fixed |
|---|---|---|---|
| H-1 | `PrivacyNetworkController` (orig 37-43) | `clearProxyOverride`/`setProxyOverride` are asynchronous; `ready=true` was published before completion; no ordering/epoch handling; capability-change callbacks re-ran bind+clear each time | Yes — `clearBlackhole{}` completion + epoch guard before `markReady` **[CODE]** |
| H-2 | `util/PrivateDownloader` | No VPN check during the copy (`copyTo`, orig 71/83); blocking IO not cancellable so a download outlived the screen after `stop()` unbound the process; cookies attached to **every** redirect hop (orig 36) incl. cross-origin; no confirmation (drive-by downloads via `DownloadListener`); private/loopback targets allowed; `Content-Type` params passed to MediaStore; connection not disconnected on error paths | Yes — per-chunk gate + `ensureActive`, origin-only cookies/Referer, `DownloadPolicy.validateHop` (https, no userinfo, no private targets), confirmation dialog, `finally{disconnect}` **[CODE]**; runtime **[DEVICE-EVIDENCE MISSING]** |
| H-3 | `FullscreenWebScreen` popup client (orig 365/370/377/422) | Popup callbacks overwrote main `currentUrl`/title/progress → wrong bookmark, wrong reload target after crash/process restore; popup had no host display (phishing) | Yes — popup state isolated, popup header shows host **[CODE]** |
| H-4 | `FullscreenWebScreen` (orig 326/502/504/563/601) | `pauseTimers()` is process-global: closing a popup or restarting the renderer froze JS timers of other WebViews; no lifecycle pause → page JS/network/media kept running when the app was backgrounded **[CODE]** (grep: no lifecycle observer) | Yes — never `pauseTimers`; `ON_STOP→onPause()`/`ON_START→onResume()` unless background media is enabled *and* playing |
| H-5 | `launchExternal` (orig 303-313), `shouldOverrideUrlLoading` (orig 398-401) | Any scheme launched via `ACTION_VIEW` on a gesture; `intent://` launched with page-chosen action/package/extras; `data:`/`blob:` main-frame navigation allowed; `http://` relied only on the manifest cleartext flag | Yes — `NavigationPolicy.decideMainFrame`: https only, allow-list `mailto/tel/sms/smsto` + gesture, `intent://` → https fallback only **[CODE, TEST NOT EXECUTED]** |
| H-6 | permission flow (orig 235-283, 676) | Android runtime prompt shown *before* the user consented for the origin; pending request not denied on VPN loss/renderer crash → stale `grant()` on a destroyed WebView | Yes — consent first, runtime prompt second, `finishPermission()` on every teardown path |

## E. MEDIUM FINDINGS
- **M-1 Fingerprint script** (orig 91-96): `hardwareConcurrency`/`deviceMemory` set to `undefined` *as instance properties* (rare, detectable, can break sites; contradicts "stable common values"); geolocation monkey-patch redundant with native denial and itself fingerprintable. Fixed: prototype getters returning 4/4; geolocation patch removed (native: `geolocationEnabled=false`, deny callback, no location permission **[CODE]**).
- **M-2** Document-start script unsupported ⇒ WebRTC hardening silently off. Fixed: one-time toast.
- **M-3** Notification title = page title (visible to notification listeners). Fixed: constant text; extras ignored.
- **M-4** `AdTrackerStats` classified with `contains("analytics"/"segment"/…)` (orig 22-25). Fixed: list category via exact/subdomain match.
- **M-5** Blocked responses carried header `X-Obsidian-Block` (app-identifying, page-readable). Removed; 204 status is still distinguishable (limitation).
- **M-6** `UrlHelper.normalizeUrl` passed any `scheme://…` through and sent Unicode (IDN) hosts to Google search. Fixed with the single strict parser `UrlSafety` (IDN→punycode, port range, userinfo, IPv6).
- **M-7** Bookmarks: case-insensitive de-dupe merged distinct paths (orig 80), no scheme validation (orig 42), unbounded titles. Fixed.
- **M-8** `currentUrl` accepted error-page/`data:` URLs (saved to bookmarks/state). Fixed (`isWebUrl`).
- **M-9** Renderer crash → reload → crash loop possible. Fixed with `CrashLoopGuard` (3 crashes / 60 s → Home).
- **M-10** Backup rules excluded `file` but not `root` (WebView data lives under `app_webview`, domain `root` **[INFERENCE]**). Fixed in both XMLs; `allowBackup=false` already set **[CODE]**.
- **M-11** Service-Worker settings left at defaults (file/content access on). Set false.
- **M-12** Compose `Dialog`/`AlertDialog` windows relied on `SecureFlagPolicy.Inherit` for FLAG_SECURE. Now `SecureOn` explicitly.
- **M-13** `androidx.savedstate:savedstate-compose:1.2.1` declared, never imported **[STATIC]**; existence of that version **[UNKNOWN]**. Removed.
- **M-14** No explicit privacy wipe. Added `BrowsingDataWiper` + Home action (cookies, WebStorage, cache, history; keeps bookmarks/downloads).
- **M-15** Camera/microphone permissions without `uses-feature required="false"` would filter devices on Play. Added.

## F. LOW / REMAINING FINDINGS (not fixed — see L)
Default search is Google; no R8/minify; release signed with debug key when no keystore; `material-icons-extended` bloat; dead
`DiagnosticsBottomSheet`/`WebViewDiagnostics`; `metadata.json`/`.env.example` Gemini scaffolding (no code uses it **[STATIC]**);
permission dialog shows the *requesting frame's* origin, not the top-level page; no same-site/registrable-domain logic exists
anywhere (no PSL) — only exact-origin comparison is used, so none is claimed.

## G. FIXES APPLIED (all **[CODE]**; none compiled)
New pure-JVM units: `UrlSafety` (strict URL/host parser, IDN, IPv4/IPv6/private classification), `NavigationPolicy`, `PermissionPolicy`/
`PopupPolicy`/`CrashLoopGuard`, `VpnGate`, `DownloadPolicy`, `BlockedResponses`, `BrowsingDataWiper`.
Rewritten: `PrivacyNetworkController`, `PrivateDownloader`, `AdBlockList` (+`categoryOf`), `AdTrackerStats`, `UrlHelper.normalizeUrl`,
`BookmarkRepository`, `BackgroundAudioService`, `FullscreenWebScreen`; small edits: `HomeScreen`, manifest, backup XMLs, `build.gradle.kts`, `proguard-rules.pro`, README.

## H. TESTS ADDED
JVM (plain JUnit): `UrlSafetyTest`, `NavigationPolicyTest`, `PermissionAndPopupPolicyTest` (incl. popup gesture, concurrent-request verdict, crash guard),
`VpnGateTest`, `DownloadPolicyTest`, `AdBlockListTest` (extended), `AdTrackerStatsTest`, `UrlHelperTest` (extended),
`ManifestAndBackupConfigTest` (manifest + both backup XMLs). Robolectric: `BookmarkRepositorySecurityTest`. Instrumented: `PrivacyDeviceTest`
(gate closed without VPN; cookie wipe).
**Requested categories with NO real test:** iframe policy (sub-frame path returns "allow" untested), Service-Worker interception (logic lives in a lambda),
download-VPN behaviour of `PrivateDownloader` (needs HttpURLConnection fake), SPA/`pushState` tracking, WebView renderer-crash handling, popup *lifecycle*.
These are device/manual items (K).

## I. TESTS ACTUALLY EXECUTED
**None of the Kotlin tests.** Only: (1) bracket-balance scan **[STATIC]**, (2) `AlgorithmCrossCheck.java` — 223/223 **[INFERENCE]** (Java port).

## J. TESTS THAT COULD NOT BE EXECUTED
All 14 unit-test files and both instrumented classes: **[NOT EXECUTED] This test exists but was not run.** Reason: no Kotlin compiler/Gradle/SDK/network.
Robolectric additionally needs to download Android jars.

## K. DEVICE-ONLY VERIFICATION REQUIRED
1. Process binding to a VPN `Network` actually carries WebView, Service-Worker, and `HttpURLConnection` traffic; kept binding to a dead VPN network makes new connections fail (varies by OS/OEM/VPN app).
2. `NetworkRequest` with `TRANSPORT_VPN` + `removeCapability(NOT_VPN)` matches your VPN app's network; split-tunnel/excluded-app VPNs.
3. `PROXY_OVERRIDE` set/clear ordering and completion callbacks; black-hole proxy effect on WebSocket/media/WebRTC.
4. WebRTC: whether `RTCPeerConnection` removal reaches about:blank/srcdoc/sandboxed iframes and Workers; actual STUN/ICE behaviour.
5. `isUserGesture`/`hasGesture()` accuracy on your WebView version; popup blocking with `javaScriptCanOpenWindowsAutomatically=false`.
6. `WebStorage.deleteAllData()` coverage of IndexedDB / Cache Storage / Service-Worker registrations.
7. Whether media element requests pass through `shouldInterceptRequest` (VPN-gate refusal coverage).
8. FLAG_SECURE on Compose dialogs; foreground-service start/stop from background (`BackgroundAudioService`).
9. Safe Browsing lookups: made outside this process (Google Play services) — see L.

## L. REMAINING LIMITATIONS
| # | File / function | Problem | Why it matters | Fixed? | Device? | Next |
|---|---|---|---|---|---|---|
| 1 | `PrivacyNetworkController` | App-level only; other apps/system components unaffected; provider can exclude the app | "Kill switch" cannot be claimed | No (impossible in-app) | Yes | Use Always-on VPN + "Block connections without VPN" |
| 2 | `FullscreenWebScreen` `shouldInterceptRequest` | WebSocket, WebRTC and possibly some media-pipeline traffic are not expected to reach the interceptor (documented WebView limitation **[DOC]**; not verified here **[UNKNOWN]**) | Ad/tracker + VPN-gate refusal incomplete there | No | Yes | Rely on binding/proxy; test with packet capture |
| 3 | `AdBlockList` | Host list only; no CNAME-cloak detection (no DNS access), no cosmetic filters, no WebSocket | Not a uBlock equivalent; do not market as such | No | – | Integrate a maintained list engine if needed |
| 4 | document-start JS | Canvas/WebGL/Audio/fonts/UA/Client Hints/screen/timezone unprotected; WebView UA exposes model/`wv` | Strong fingerprinting remains | No | Yes | Decide product scope; consider UA reduction |
| 5 | `UrlSafety.isPrivateOrLocalHost` | DNS-rebinding (public name → private IP) undetectable | LAN probing partially open | No | – | Needs resolver-level control |
| 6 | `HomeScreen`/`UrlHelper` | Search goes to Google | Query leakage to a third party | No (product decision) | – | Make engine configurable (e.g. DuckDuckGo) |
| 7 | WebView data | Logins/cookies/localStorage persist by design; saved-state keeps last URL across process death | "No trace" cannot be claimed | Partly (manual wipe) | Yes | Optional wipe-on-exit setting |
| 8 | Safe Browsing | Hash lookups go to Google outside the process | Contradicts strict "all traffic via VPN" | No (security trade-off) | Yes | Decide; document |
| 9 | Release config | R8 off; debug-key fallback; icons-extended bloat | Size/hardening/signing hygiene | No (can't test R8 here) | – | Enable R8 after a full device pass; fail release without keystore |
| 10 | `PermissionPolicy` / dialog | Shows requesting frame origin only | Cross-origin iframe requests are user-approvable | No | – | Show top-level host too |
| 11 | Dead code | `DiagnosticsBottomSheet`, `WebViewDiagnostics` | Maintenance/attack surface | No | – | Delete or wire up |

## M/N/O. SCORES (judgements, not measurements; confidence LOW because nothing was built/run)
- **Security 6/10** — source-level design is now coherent (central policies, gated interception, explicit permission/popup/external-scheme rules), but compile status, runtime behaviour and the kept-binding assumption are unverified.
- **Privacy 5/10** — VPN-gating (if it works on-device) is strong for browser traffic; fingerprinting, Google search, Safe Browsing, persistent storage and WebSocket/WebRTC paths limit it.
- **Performance 6/10** — **[UNKNOWN]**, not measured. Positives: page paused in background, no polling unless background audio is enabled, crash-loop guard. Negatives: no R8, icon pack, one 2 s JS poll while background audio is on.

## P. FINAL VERDICT
**Not release-ready, and not claimable as "verified".** The source was materially hardened and the original's false fail-closed behaviour and compile
blockers were addressed in code, but nothing has been compiled, unit-tested or run on a device. Next steps in order: (1) `./gradlew assembleDebug testDebugUnitTest lint`
and fix any compile errors in the new/rewritten files (most likely spots: `FullscreenWebScreen.kt`, `PrivacyNetworkController.kt`, `LocalLifecycleOwner`
import version); (2) run `PrivacyDeviceTest` and the manual checklist in the README on a real device with a real VPN and a packet capture; (3) only then revisit the scores.
