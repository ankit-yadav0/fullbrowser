# Build & test runbook (nothing below has been executed by the auditor)

## Why no build was possible in the audit environment  **[BUILD NOT EXECUTED]**
1. The project ships **no `gradlew`, `gradlew.bat` or `gradle/wrapper/gradle-wrapper.jar`** — only `gradle-wrapper.properties`
   (`ls` result; the original README only hinted at this). `./gradlew` therefore cannot start. It is not repairable offline: the
   wrapper jar is a binary from a Gradle distribution.
2. The sandbox egress proxy denies every build host (`x-deny-reason: host_not_allowed`): services.gradle.org, downloads.gradle.org,
   plugins.gradle.org, repo.maven.apache.org, dl.google.com (Google Maven + Android SDK), github.com, pypi.org, registry.npmjs.org,
   archive.ubuntu.com.
3. No Gradle, no `kotlinc`, no Android SDK/`android.jar`, no Maven/Gradle cache, no `/dev/kvm` (so no emulator), no `adb`.
   The only Ubuntu candidates (kotlin 1.3.31, gradle 4.4.1) cannot build a Kotlin 2.2 / Gradle 9.3 / AGP 9 project anyway.

## Unblock (on your machine, or here after allow-listing the hosts above plus objects.githubusercontent.com / maven.google.com)
```bash
# 1. wrapper (needs any Gradle >= 9.3.1 once, e.g. the one bundled with Android Studio)
gradle wrapper --gradle-version 9.3.1
# 2. SDK (compileSdk 36.1 per app/build.gradle.kts) and licences
sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools" && yes | sdkmanager --licenses
echo "sdk.dir=$ANDROID_HOME" > local.properties
# 3. the three commands requested
./gradlew assembleDebug
./gradlew testDebugUnitTest          # whole suite, not selected tests
./gradlew lint
./gradlew connectedDebugAndroidTest  # device/emulator WITHOUT an active VPN
# 4. after a build: merged-manifest check (libraries can add components/permissions)
python3 tools/verify_merged_manifest.py $(find app/build -path '*merged_manifest*' -name AndroidManifest.xml)
```
JDK 17+ is expected for AGP 9 **[UNKNOWN: not verified]**. Release builds are now **unsigned** unless `KEYSTORE_PATH`,
`STORE_PASSWORD`, `KEY_PASSWORD` are set; for a local test: `./gradlew assembleRelease -PallowDebugSignedRelease=true`.

## Static checks that DID run (no toolchain needed) — `tools/`
`python3 tools/syntax_balance.py` (brackets), `python3 tools/static_checks.py` (resource refs, catalog aliases, package/path,
manifest + backup invariants), `python3 tools/symbol_check.py` (project-internal imports, member references, named arguments;
mutation-tested). These are heuristics and **do not replace compilation**.

## Compile-risk register (first places to look if `assembleDebug` fails) — all **[INFERENCE]**
| Where | What to check |
|---|---|
| `app/src/main/java/com/example/ui/FullscreenWebScreen.kt:79` | `androidx.lifecycle.compose.LocalLifecycleOwner` needs lifecycle-runtime-compose >= 2.8 (catalog says 2.8.7) |
| `app/src/main/java/com/example/ui/FullscreenWebScreen.kt:599` | property syntax on `ServiceWorkerWebSettings` (`allowContentAccess`, `allowFileAccess`) |
| `app/src/main/java/com/example/ui/FullscreenWebScreen.kt:440` | `WebViewCompat.addDocumentStartJavaScript(webView, script, Set<String>)` with webkit 1.11.0 |
| `app/src/main/java/com/example/ui/FullscreenWebScreen.kt:145` | assigning `null` to `WebView.webChromeClient` (platform nullability) |
| `app/src/main/java/com/example/ui/FullscreenWebScreen.kt:830` | `AlertDialog(properties = DialogProperties(securePolicy = ...))` with the Material3 version in the BOM |
| `app/src/main/java/com/example/ui/FullscreenWebScreen.kt:479` | `onRenderProcessGone` override (API 26) with minSdk 24 — lint `NewApi`/`Override` expectations |
| `app/src/main/java/com/example/ui/FullscreenWebScreen.kt:515` | `is NavDecision.Allow` on a `data object` inside an exhaustive `when` expression |
| `app/src/main/java/com/example/util/PrivacyNetworkController.kt:142`, `:156` | androidx `ProxyController` argument order/nullability; `Runnable { }` SAM |
| `app/src/main/java/com/example/util/PrivacyNetworkController.kt:76`, `:56` | `removeCapability(NOT_VPN)`; `Network.networkHandle` (API 23) |
| `app/src/main/java/com/example/util/PrivateDownloader.kt:65`, `:78` | non-local `return@runCatching` out of `while(true)`/`try`/`finally`; type inference with trailing `error()` |
| `app/build.gradle.kts:8` | `compileSdk { version = release(36) { minorApiLevel = 1 } }` is AGP-9 DSL (pre-existing) |
| `app/build.gradle.kts:26`, `:58` | `providers` inside `android {}`; `signingConfig = when { ... else -> null }` |
| tests | `@Config(sdk = [36])` needs a Robolectric build that supports API 36 (pre-existing tests use the same) |
