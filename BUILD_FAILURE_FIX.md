# V7.1 build-failure fix

## Exact root cause in the supplied GitHub Actions log

The existing `.github/workflows/main.yml` pinned Gradle **8.7**, while Android Gradle Plugin **9.1.1** explicitly requires **Gradle 9.3.1**. The workflow bypasses the project's `gradle-wrapper.properties` and executes the separate Gradle installation, so changing the wrapper properties alone could not fix that run.

The same workflow also called `sdkmanager` before installing Android SDK command-line tools, which caused `sdkmanager: command not found`. It attempted to install API 34/build-tools 34.0.0 even though this project uses compile SDK **36.1**.

## Changes in this fixed archive

- Replaced `.github/workflows/main.yml` in-place, keeping the exact workflow path/name so this is a replacement, not a second parallel workflow.
- Pins Gradle 9.3.1, the minimum required by AGP 9.1.1 according to the supplied build error.
- Installs Android command-line tools before invoking `sdkmanager`.
- Installs SDK Platform 36.1 and Build Tools 36.0.0/36.1.0, matching the project's compile SDK setup without forcing a build-tools version in the Android DSL.
- Runs `assembleDebug`, `testDebugUnitTest`, and `lint` and uploads the APK after a successful assemble plus available reports.

No Kotlin source was changed for this failure: the supplied log fails while applying the Android Gradle Plugin, before Kotlin compilation begins.

## Verification limit

The project source checks and workflow YAML validation pass. This environment has no installed Gradle or Android SDK, and outbound network access for Gradle/Google dependencies is blocked, so a real APK build cannot honestly be claimed from here. The next network-enabled GitHub Actions run must confirm compilation; if more compiler/test errors appear after the toolchain mismatch is removed, those will need a separate fix.

The source archive still does not include `gradlew`, `gradlew.bat`, or `gradle-wrapper.jar`. The corrected CI workflow intentionally uses the explicit Gradle 9.3.1 installation and therefore does not need the wrapper. For local command-line builds, generate the wrapper once from the project root with `gradle wrapper --gradle-version 9.3.1 --distribution-type bin` after installing compatible Gradle.
