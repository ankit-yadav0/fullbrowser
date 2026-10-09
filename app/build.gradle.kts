plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.fullscreenweb.qvxz"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Release signing: a real upload keystore is used only when KEYSTORE_PATH (plus STORE_PASSWORD and
  // KEY_PASSWORD) are supplied, e.g. by CI secrets. Without it the release APK is left UNSIGNED instead
  // of silently using the debug key (a shared, predictable key that must never sign a distributable
  // build). To test a release build on a local device, opt in explicitly:
  //   ./gradlew assembleRelease -PallowDebugSignedRelease=true
  val hasReleaseKeystore = System.getenv("KEYSTORE_PATH") != null
  val allowDebugSignedRelease = providers.gradleProperty("allowDebugSignedRelease").orNull == "true"

  signingConfigs {
    if (hasReleaseKeystore) {
      create("release") {
        storeFile = file(System.getenv("KEYSTORE_PATH")!!)
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
  }

  if (!hasReleaseKeystore) {
    logger.warn(
      if (allowDebugSignedRelease) {
        "WARNING: KEYSTORE_PATH is not set — 'release' is signed with the DEBUG key because " +
          "-PallowDebugSignedRelease=true was passed. Never distribute this build."
      } else {
        "WARNING: KEYSTORE_PATH is not set — the 'release' build type will be UNSIGNED. Set " +
          "KEYSTORE_PATH, STORE_PASSWORD and KEY_PASSWORD, or pass -PallowDebugSignedRelease=true " +
          "for a local-only test build."
      }
    )
  }

  buildTypes {
    debug {}
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = when {
        hasReleaseKeystore -> signingConfigs.getByName("release")
        allowDebugSignedRelease -> signingConfigs.getByName("debug")
        else -> null
      }
    }
    }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  // Room was in the AI Studio scaffold but bookmarks are stored via BookmarkRepository
  // (SharedPreferences + JSON) — Room is unused, so it's commented out to keep build/APK lean.
  // implementation(libs.androidx.room.ktx)
  // implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.play.services.location)
  implementation("androidx.webkit:webkit:1.11.0")
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  // "ksp"(libs.androidx.room.compiler) // unused — see Room note above
}
