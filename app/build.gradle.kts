plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.androidx.navigation.safeargs.kotlin)
}

val signingStorePath = (project.findProperty("signingStoreFile") as String?) ?: "platform.jks"
val signingStorePassword = (project.findProperty("signingStorePassword") as String?) ?: "android"
val signingKeyAlias = (project.findProperty("signingKeyAlias") as String?) ?: "android"
val signingKeyPassword = (project.findProperty("signingKeyPassword") as String?) ?: signingStorePassword

android {
    namespace = "com.mahesh.tvproviderbrowser"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(signingStorePath)
            storePassword = signingStorePassword
            keyAlias = signingKeyAlias
            keyPassword = signingKeyPassword
        }
    }

    defaultConfig {
        applicationId = "com.mahesh.tvproviderbrowser"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    applicationVariants.all {
        val variant = this
        val capitalizedName = variant.name.replaceFirstChar { it.uppercase() }
        tasks.named("assemble$capitalizedName") {
            doLast {
                val outputDir = rootProject.file("apk")
                outputDir.mkdirs()
                variant.outputs.all {
                    val srcFile = (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFile
                    if (srcFile.exists()) {
                        srcFile.copyTo(File(outputDir, "TvProviderBrowser.apk"), overwrite = true)
                        println("✅ Copied APK → ${outputDir.absolutePath}/TvProviderBrowser.apk")
                    }
                }
            }
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // TV / Lists
    implementation(libs.androidx.tvprovider)
    implementation(libs.androidx.leanback)
    implementation(libs.androidx.recyclerview)

    // Lifecycle + ViewModel
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // Navigation
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Test
}