# Device test plan (Phases 5, 7, 8, 9) — NOT EXECUTED

Nothing in this file has been run. It exists so that the device/VPN/download/WebView verification can be done quickly and
**observed from outside the app**. Record every result in the table at the end; until that table is filled, every
network/privacy claim stays **[DEVICE-VERIFICATION-REQUIRED]**.

## 0. Setup
1. Server: `python3 tools/test_server/server.py --port 8080 [--alt-host second.host.name] [--cert fullchain.pem --key key.pem]`.
   The app trusts only **system** CAs and refuses cleartext, so the server must be reachable as `https://` with a *publicly trusted*
   certificate (Let's Encrypt, or an HTTPS tunnel/reverse proxy in front of the plain-HTTP server). `--alt-host` must be a second
   name for the same server (cross-origin tests). Optional private-LAN probe target: run a second copy with a self-signed cert on
   `192.168.x.y:8443`; **a TLS error in its log means the app connected; silence means the request was refused before the network**.
2. Observation point for network behaviour (pick at least one; do not infer from source):
   * **Server log** (`CLIENT DISCONNECTED after N/M bytes at HH:MM:SS` is printed when the app stops reading).
   * **Packet capture off the phone**: make a Linux PC the Wi-Fi access point (or use a switch mirror port) and run
     `tcpdump -ni <wlan-if> host <phone-ip>`. With the VPN up, only packets to the VPN server endpoint should appear; **any direct
     DNS/TLS packet to a website or resolver while the VPN is down is a leak**. VPN-provider-side logs are a second source.
3. Phone: install the debug APK, grant nothing in advance, start with **no VPN**. Note Android version, WebView version
   (`adb shell dumpsys webviewupdate`), device model, VPN app + protocol.

## 1. Instrumented tests (Phase 5) — `./gradlew connectedDebugAndroidTest`
Run on a device/emulator **without an active VPN**. Classes present: `PrivacyDeviceTest` (gate closed without VPN, cookie wipe),
`WindowAndManifestDeviceTest` (FLAG_SECURE incl. after `recreate()`, installed-package exported components + backup flag),
`ExampleInstrumentedTest`. Not automatable here (need a real VPN consent dialog, publicly-trusted HTTPS, or crash injection):
VPN present/disconnect, popup, permissions, renderer lifecycle, Service Worker, download behaviour → manual protocol below.
After any build run: `python3 tools/verify_merged_manifest.py $(find app/build -path '*merged_manifest*' -name AndroidManifest.xml)`.

## 2. VPN network tests (Phase 7)
| # | Action | Expected (design intent, unverified) | Observe |
|---|---|---|---|
| A | Open app, tap a site with **no VPN** | Overlay "Secure VPN connection required"; no WebView; **no packets** from the app | capture/server log empty |
| B | Connect VPN, retry | Browser becomes usable | page loads; VPN carries traffic |
| C | Load `https://<server>/` | Page loads; results POSTed to `/report` | server log shows source = VPN exit IP |
| D | Disconnect VPN **while** `/slow?mb=64` (download) and a page are loading | New requests refused; WebView destroyed; download stops | server prints `CLIENT DISCONNECTED`; no packets on physical iface after drop |
| E1 | After drop: reload / navigate / tap links | Nothing sent; overlay shown | capture |
| E2 | After drop: popup, download, media (`<video>` from server), WebSocket, WebRTC (`RTCPeerConnection` is removed by the page script — also test from a *different* WebRTC page), Service Worker `/sw-ping` | **No request reaches the physical network** | capture/server log (**WebSocket, WebRTC and media may not pass the interceptor — that is exactly what this test is for**) |
| F | Reconnect VPN | Gate reopens, browser can reload safely | page reloads; no stale permission/popup |
Record also: time between VPN drop and last observed physical-network packet.

## 3. Download tests (Phase 8) — all via `https://<server>/…`
| Case | Endpoint | Expected | Observe |
|---|---|---|---|
| normal | `/file` | confirmation dialog; 1 MiB saved to Downloads | file size 1048576 |
| redirect | `/redirect-https` | follows, saves | server log |
| downgrade | `/redirect-http` | **fails** ("Download failed"); no http request sent | no `GET /file` over http in server log |
| cross-origin | `/cookie-set` then `/redirect-cross` | `/echo` body shows **no cookie, no referer** | downloaded `echo.json` |
| same-origin cookie | `/cookie-set` then `/echo` | cookie + referer present | `echo.json` |
| private/local targets | `/redirect-private`, `/redirect-localhost`, `/redirect-ipv6`, `/redirect-metadata` | all fail; **no connection attempt** | LAN probe server silent; capture |
| redirect loop | `/redirect-loop` | fails after 5 hops | server log count |
| VPN drop | `/slow?mb=64` + drop VPN | stops, **partial file deleted** | `CLIENT DISCONNECTED`; Downloads folder |
| retry after drop | tap download again while VPN down | refused ("VPN required") | no packets |
| cancel | dialog Cancel / leave screen mid-download | stops; no partial file | server log |
| duplicate name | `/dupe` twice | second saved as `report (1).pdf` (Q+: MediaStore renames) | Downloads |
| malicious name | `/evil-name` | saved with sanitised name, no `/`, no `..`, no bidi char | Downloads |
| mime params | `/mime` | saved as `note.txt`/`text/plain` | Downloads |
| large file | `/big?mb=512` | completes or fails cleanly; memory stable | `adb shell dumpsys meminfo` |
| failed transfer | `/fail` | "Download failed"; partial deleted | Downloads |
| http link | tap `http://` download link | blocked: "Only HTTPS downloads" | — |

## 4. WebView security tests (Phase 9)
Open `https://<server>/` in the app and work through the page. **Expected policy decisions per the Java cross-check port
(`docs/audit/AlgorithmCrossCheck.java --table`) — these are predictions, NOT observations of the Kotlin code or the device:**

| Input | Home normalizeUrl | initial load (toLoadableUrl) | main-frame nav, gesture | main-frame nav, no gesture | download hop | permission origin |
|---|---|---|---|---|---|---|
| `https://example.com` | `https://example.com` | `https://example.com` | ALLOW | ALLOW | allowed | PROMPT |
| `http://example.com` | `http://example.com` | `https://example.com` | BLOCK | BLOCK | rejected (scheme) | DENY (insecure/invalid origin) |
| `https://münchen.de` | `https://münchen.de` | `https://münchen.de` | ALLOW | ALLOW | allowed | PROMPT |
| `https://xn--mnchen-3ya.de` | `https://xn--mnchen-3ya.de` | `https://xn--mnchen-3ya.de` | ALLOW | ALLOW | allowed | PROMPT |
| `https://localhost` | `https://localhost` | `https://localhost` | ALLOW | ALLOW | rejected (private) | PROMPT |
| `https://localhost:3000` | `https://localhost:3000` | `https://localhost:3000` | ALLOW | ALLOW | rejected (private) | PROMPT |
| `https://192.168.1.1` | `https://192.168.1.1` | `https://192.168.1.1` | ALLOW | ALLOW | rejected (private) | PROMPT |
| `https://10.0.0.1` | `https://10.0.0.1` | `https://10.0.0.1` | ALLOW | ALLOW | rejected (private) | PROMPT |
| `https://169.254.169.254` | `https://169.254.169.254` | `https://169.254.169.254` | ALLOW | ALLOW | rejected (private) | PROMPT |
| `https://[::1]` | `https://[::1]` | `https://[::1]` | ALLOW | ALLOW | rejected (private) | PROMPT |
| `https://[fe80::1]` | `https://[fe80::1]` | `https://[fe80::1]` | ALLOW | ALLOW | rejected (private) | PROMPT |
| `https://[2001:db8::1]:8443` | `https://[2001:db8::1]:8443` | `https://[2001:db8::1]:8443` | ALLOW | ALLOW | allowed | PROMPT |
| `https://example.com:99999` | `https://example.com:99999` | rejected | BLOCK | BLOCK | rejected (malformed) | DENY (insecure/invalid origin) |
| `https://example.com:8443` | `https://example.com:8443` | `https://example.com:8443` | ALLOW | ALLOW | allowed | PROMPT |
| `https://user@example.com` | `https://user@example.com` | rejected | BLOCK | BLOCK | rejected (userinfo) | DENY (insecure/invalid origin) |
| `https://2130706433` | `https://2130706433` | `https://2130706433` | ALLOW | ALLOW | rejected (private) | PROMPT |
| `javascript:alert(1)` | `https://www.google.com/search?q=javascript%3Aalert%281%29` | rejected | BLOCK | BLOCK | rejected (malformed) | DENY (insecure/invalid origin) |
| `data:text/html,hi` | `https://www.google.com/search?q=data%3Atext%2Fhtml%2Chi` | rejected | BLOCK | BLOCK | rejected (malformed) | DENY (insecure/invalid origin) |
| `blob:https://example.com/id` | `https://www.google.com/search?q=blob%3Ahttps%3A%2F%2Fexam...` | rejected | ALLOW | ALLOW | rejected (malformed) | DENY (insecure/invalid origin) |
| `file:///sdcard/a` | `https://www.google.com/search?q=file%3A%2F%2F%2Fsdcard%2Fa` | rejected | BLOCK | BLOCK | rejected (malformed) | DENY (insecure/invalid origin) |
| `content://com.x/y` | `https://www.google.com/search?q=content%3A%2F%2Fcom.x%2Fy` | rejected | BLOCK | BLOCK | rejected (scheme) | DENY (insecure/invalid origin) |
| `intent://x#Intent;S.browser_fallback_url=https%3A%2F%2Fexample.com;end` | `https://www.google.com/search?q=intent%3A%2F%2Fx%23Intent...` | rejected | LOAD:https://example.com | BLOCK | rejected (scheme) | DENY (insecure/invalid origin) |
| `intent://x#Intent;package=com.evil;end` | `https://www.google.com/search?q=intent%3A%2F%2Fx%23Intent...` | rejected | BLOCK | BLOCK | rejected (scheme) | DENY (insecure/invalid origin) |
| `mailto:a@b.com` | `https://www.google.com/search?q=mailto%3Aa%40b.com` | rejected | EXT:mailto:a@b.com | BLOCK | rejected (malformed) | DENY (insecure/invalid origin) |
| `tel:+123` | `https://www.google.com/search?q=tel%3A%2B123` | rejected | EXT:tel:+123 | BLOCK | rejected (malformed) | DENY (insecure/invalid origin) |
| `sms:+123` | `https://www.google.com/search?q=sms%3A%2B123` | rejected | EXT:sms:+123 | BLOCK | rejected (malformed) | DENY (insecure/invalid origin) |
| `market://details?id=x` | `https://www.google.com/search?q=market%3A%2F%2Fdetails%3F...` | rejected | BLOCK | BLOCK | rejected (scheme) | DENY (insecure/invalid origin) |

Notes: "main-frame nav" is `NavigationPolicy.decideMainFrame`; additionally a *script/redirect-initiated* (no gesture) top-level
navigation from a public page to a loopback/private host is refused in `FullscreenWebScreen.shouldOverrideUrlLoading`
(`/redirect-page-private` must NOT reach the private host; `/redirect-page-http` must show "Insecure HTTP navigation blocked").
Manual checks on the page: camera/microphone prompts (consent dialog first, OS prompt second, shows *requesting origin* and
*page you are viewing*, with an "embedded frame of a DIFFERENT site" line for the `__ALT__` iframe); geolocation → denied;
`window.open` on tap opens one popup with a host header, 2nd/timer popups refused; `typeof RTCPeerConnection` → `undefined`;
`hardwareConcurrency`/`deviceMemory` → 4; pushState → bookmark "add current" stores `/spa/step2`; storage markers → gone after
Home → Clear browsing data (IndexedDB / CacheStorage / Service Worker coverage is **UNKNOWN** until this is run).

## 5. Results (fill in; leave blank = NOT EXECUTED)
| Test | Date | Device / Android / WebView / VPN | Result | Evidence (log/pcap file) |
|---|---|---|---|---|
| A | | | | |
| B | | | | |
| C | | | | |
| D | | | | |
| E1 | | | | |
| E2 | | | | |
| F | | | | |
| downloads (each row) | | | | |
| WebView matrix | | | | |
