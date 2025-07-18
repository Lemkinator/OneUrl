import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
    id("com.google.android.gms.oss-licenses-plugin")
}

fun String.toEnvVarStyle(): String = replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()
fun getProperty(key: String): String? = rootProject.findProperty(key)?.toString() ?: System.getenv(key.toEnvVarStyle())

android {
    namespace = "de.lemke.oneurl"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.lemke.oneurl"
        minSdk = 26
        targetSdk = 36
        versionCode = 40
        versionName = "1.7.1"
    }

    @Suppress("UnstableApiUsage")
    androidResources.localeFilters += listOf("en", "de")

    signingConfigs {
        create("release") {
            getProperty("releaseStoreFile").apply {
                if (isNullOrEmpty()) {
                    logger.warn("Release signing configuration not found. Using debug signing config.")
                } else {
                    logger.lifecycle("Using release signing configuration from: $this")
                    storeFile = rootProject.file(this)
                    storePassword = getProperty("releaseStorePassword")
                    keyAlias = getProperty("releaseKeyAlias")
                    keyPassword = getProperty("releaseKeyPassword")
                }
            }
        }
    }

    buildTypes {
        all {
            signingConfig = signingConfigs.getByName(if (getProperty("releaseStoreFile").isNullOrEmpty()) "debug" else "release")
        }

        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk { debugSymbolLevel = "FULL" }
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            applicationIdSuffix = ".debug"
            resValue("string", "app_name", "OneURL (Debug)")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_21) } }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

secrets {
    propertiesFileName = "secrets.properties"
    defaultPropertiesFileName = "secrets.defaults.properties"
}

dependencies {
    //SESL Android Jetpack
    implementation("sesl.androidx.core:core:1.16.0+1.0.16-sesl7+rev1")
    implementation("sesl.androidx.core:core-ktx:1.16.0+1.0.0-sesl7+rev0")
    implementation("sesl.androidx.appcompat:appcompat:1.7.1+1.0.47000-sesl7+rev0")
    implementation("sesl.androidx.preference:preference:1.2.1+1.0.12-sesl7+rev0")
    implementation("sesl.androidx.picker:picker-color:1.0.19+1.0.19-sesl7+rev0")
    //SESL Material Components + Design Lib + Icons
    implementation("sesl.com.google.android.material:material:1.12.0+1.0.39-sesl7+rev5")
    implementation("io.github.tribalfs:oneui-design:0.7.4+oneui7")
    implementation("io.github.oneuiproject:icons:1.1.0")

    implementation("io.github.lemkinator:common-utils:0.8.39")

    implementation("com.airbnb.android:lottie:6.6.7")
    implementation("com.google.android.play:review-ktx:2.0.2")
    implementation("com.android.volley:volley:1.2.1")
    implementation("com.github.skydoves:bundler:1.0.4")

    implementation("androidx.core:core-splashscreen:1.2.0-rc01")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    val roomVersion = "2.7.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("com.google.dagger:hilt-android:2.57")
    ksp("com.google.dagger:hilt-compiler:2.57")
}

configurations.implementation {
    //Exclude official android jetpack modules
    exclude("androidx.core", "core")
    exclude("androidx.core", "core-ktx")
    exclude("androidx.customview", "customview")
    exclude("androidx.coordinatorlayout", "coordinatorlayout")
    exclude("androidx.drawerlayout", "drawerlayout")
    exclude("androidx.viewpager2", "viewpager2")
    exclude("androidx.viewpager", "viewpager")
    exclude("androidx.appcompat", "appcompat")
    exclude("androidx.fragment", "fragment")
    exclude("androidx.preference", "preference")
    exclude("androidx.recyclerview", "recyclerview")
    exclude("androidx.slidingpanelayout", "slidingpanelayout")
    exclude("androidx.swiperefreshlayout", "swiperefreshlayout")

    //Exclude official material components lib
    exclude("com.google.android.material", "material")
}