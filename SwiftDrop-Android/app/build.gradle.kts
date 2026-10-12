import com.android.build.api.artifact.SingleArtifact

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// SwiftDrop @bugfix Codex 12/10/2026 Validate permissions in the packaged manifest.
androidComponents.onVariants(androidComponents.selector().withBuildType("debug")) { variant ->
    val manifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
    tasks.withType<Test>().configureEach {
        if (name == "testDebugUnitTest") {
            inputs.file(manifest)
            dependsOn(manifest)
            doFirst { systemProperty("swiftdrop.mergedManifest", manifest.get().asFile.absolutePath) }
        }
    }
}

android {
    namespace = "app.swiftdrop"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.swiftdrop"
        minSdk = 26
        targetSdk = 35
        versionCode = 22
        versionName = "0.4.11-alpha"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    bundle { language { enableSplit = false } }
    // SwiftDrop @build Codex 12/10/2026 CI supplies a retained development-only key.
    System.getenv("SWIFTDROP_DEBUG_KEYSTORE")?.let { retainedKey ->
        signingConfigs.getByName("debug") {
            storeFile = file(retainedKey)
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("com.google.android.gms:play-services-nearby:19.5.1")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
