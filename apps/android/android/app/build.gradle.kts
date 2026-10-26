import java.security.MessageDigest

plugins {
    id("com.android.application")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

// Flutter's target-platform selects its engine, but its default JNI filter still
// admits all supported ABIs. Apply the same selection to every dependency's .so.
val targetAbis = providers.gradleProperty("target-platform")
    .orElse("android-arm64").get().split(",").map { platform ->
        when (platform) {
            "android-arm" -> "armeabi-v7a"
            "android-arm64" -> "arm64-v8a"
            "android-x64" -> "x86_64"
            else -> throw GradleException("Unsupported Android target platform: $platform")
        }
    }

android {
    namespace = "com.xjicloud.xjicloud_mobile"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = "28.2.13676358"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.xjicloud.mobile"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = 29
        targetSdk = flutter.targetSdkVersion
        // Uses the version code from pubspec.yaml. When using split APKs, 1000 * ABI_VERSION
        // is added automatically by Flutter. (https://developer.android.com/studio/build/configure-apk-splits#configure-APK-versions)
        // You can force using the value of versionCode by specifying the `-P force-version-code-ignoring-abi=true`
        // flag during build.
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        ndk {
            // AGP merges this set with build types by union, not replacement.
            abiFilters.clear()
        }
    }

    buildTypes {
        configureEach {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk {
                abiFilters.clear()
                abiFilters.addAll(if (name == "debug") targetAbis else listOf("arm64-v8a"))
            }
        }
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

// Check the actual executing Flutter compile task, so debug x64 remains valid.
// Filtering an x64-only release/profile to arm64 would omit its Flutter engine.
tasks.withType<com.flutter.gradle.tasks.FlutterTask>().configureEach {
    doFirst {
        if (this@configureEach.buildMode != "debug" && "arm64-v8a" !in targetAbis) {
            throw GradleException("Release/profile require --target-platform android-arm64 (or a list including it).")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

flutter {
    source = "../.."
}

dependencies {
    implementation(files("../../vendor/native3dgs/0.2.2/aar/native3dgs-sdk-release.aar"))
    implementation(files("../../vendor/aliyun-fusion/1.2.16/fusionauth-1.2.16-online-release.aar"))
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}

val fusionAar = layout.projectDirectory.file("../../vendor/aliyun-fusion/1.2.16/fusionauth-1.2.16-online-release.aar")
val verifyFusionSdk by tasks.registering {
    inputs.file(fusionAar)
    doLast {
        val digest = MessageDigest.getInstance("SHA-256")
        fusionAar.asFile.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual == "286efa126634c92da7fa0b6c22808ed8e27b7a485418ebdc2c4d64534ad33fc4") {
            "Aliyun fusion SDK checksum mismatch; restore the pinned original AAR."
        }
    }
}
tasks.named("preBuild").configure { dependsOn(verifyFusionSdk) }
