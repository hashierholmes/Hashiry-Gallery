import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePropsFile = rootProject.file("release.properties")
val keystoreProps = Properties()
if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { keystoreProps.load(it) }
}
val hasValidSigningProps = keystorePropsFile.exists() &&
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all { keystoreProps[it] != null }

android {
    namespace = "com.hashiry.gallery"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hashiry.gallery"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "80"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasValidSigningProps) {
            create("release") {
                storeFile = rootProject.file(keystoreProps["storeFile"] as String)
                storePassword = keystoreProps["storePassword"] as String
                keyAlias = keystoreProps["keyAlias"] as String
                keyPassword = keystoreProps["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            if (hasValidSigningProps) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        viewBinding = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*"
            )
            // Keep the conflict workarounds used by Code On The Go's local Maven setup.
            pickFirsts.add("nonJvmMain/default/linkdata/package_androidx/0_androidx.knm")
            pickFirsts.add("nonJvmMain/default/linkdata/root_package/0_.knm")
            pickFirsts.add("nonJvmMain/default/linkdata/module")
            pickFirsts.add("nativeMain/default/linkdata/root_package/0_.knm")
            pickFirsts.add("nativeMain/default/linkdata/module")
            pickFirsts.add("commonMain/default/linkdata/root_package/0_.knm")
            pickFirsts.add("commonMain/default/linkdata/module")
            pickFirsts.add("commonMain/default/linkdata/package_androidx/0_androidx.knm")
            pickFirsts.add("META-INF/kotlin-project-structure-metadata.json")
            merges.add("commonMain/default/manifest")
            merges.add("nonJvmMain/default/manifest")
            merges.add("nativeMain/default/manifest")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("17"))
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi"
        )
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-video:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0") // Animated WebP decoding on Android 9+
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-effect:1.4.1")

    // Existing Hashiry view-system dependencies remain available for future integration.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.android.material)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.interpolator)
}
