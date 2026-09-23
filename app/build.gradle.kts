import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.firebase.appdistribution)
    alias(libs.plugins.google.services)
}

room {
    schemaDirectory("$projectDir/schemas")
}

/**
 * Release signing credentials, kept out of the repository.
 *
 * Absent on a machine that only builds debug, so this stays null rather than failing the
 * build — the release task is the only thing that needs it, and it says so when it is missing.
 */
val signing = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}.takeIf { it.isNotEmpty() }

/**
 * Runs `git` in the repo root and returns trimmed stdout, or null off a machine without git or
 * outside a checkout — a build must still succeed there, just without a commit-accurate version.
 */
fun git(vararg args: String): String? = runCatching {
    providers.exec {
        commandLine("git", *args)
        workingDir(rootDir)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrNull()?.takeIf { it.isNotBlank() }

// One version per commit rather than a hand-bumped counter, because the hand-bumped one sat at
// 1 for the life of the project: versionCode always climbs, so an older APK is never offered as
// an update over a newer one, and versionName's hash is what "Settings → About" shows to tell
// which commit a phone is actually running.
// Where a call goes when the user has not set a key of their own. Overridable so a debug
// build can be pointed at a locally running proxy.
val proxyUrl = providers.gradleProperty("tracker.proxyUrl").getOrElse("https://PROXY-URL-NOT-SET.invalid")

val gitCommitCount = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
val gitShortSha = git("rev-parse", "--short=7", "HEAD") ?: "nogit"
val gitDirty = git("status", "--porcelain")?.isNotEmpty() == true

android {
    namespace = "com.yash.tracker"

    compileSdk = libs.versions.compileSdk.get().toInt()
    compileSdkMinor = libs.versions.compileSdkMinor.get().toInt()

    defaultConfig {
        applicationId = "com.yash.tracker"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = gitCommitCount
        versionName = "0.1.$gitCommitCount-$gitShortSha" + if (gitDirty) "-dirty" else ""

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Not a secret: it is the address of a service that refuses anyone without a token
        // minted by this Firebase project.
        buildConfigField("String", "PROXY_URL", "\"$proxyUrl\"")
    }

    signingConfigs {
        signing?.let { props ->
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
                // v1 is long dead; v2/v3 are what Android 8+ verifies, and v4 enables
                // incremental installs over adb.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")

            // Hands the signed APK to the handful of people testing it. The testers live
            // in a console-managed group, so adding or dropping one is not a commit.
            firebaseAppDistribution {
                appId = "1:186319507324:android:5ee821fcbec3541784def9"
                artifactType = "APK"
                groups = "friends"
                releaseNotes = git("log", "-1", "--pretty=%s") ?: "Build $gitCommitCount"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric reaches into JDK internals that are closed off from JDK 17 onward.
        unitTests.all {
            it.jvmArgs(
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.androidx.exifinterface)

    // Auth only. The app borrows one shared Gemini key from our proxy, and an anonymous
    // sign-in is what tells the proxy the caller is a real install rather than someone who
    // unzipped the APK. No other Firebase product is linked in.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    // The system camera cannot be told which lens to open — Samsung's ignores every documented
    // extra — so capture happens in-app, where the back camera can simply be selected.
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)

    // ExerciseDB's animations, fetched live. coil-gif because Android before P has no
    // platform decoder that animates a gif.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.room.testing)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
