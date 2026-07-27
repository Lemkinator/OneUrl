/*
 * Copyright 2023-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.gradle.secrets)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

fun String.toEnvVarStyle(): String = replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()

fun getProperty(key: String): String? = rootProject.findProperty(key)?.toString() ?: System.getenv(key.toEnvVarStyle())

fun com.android.build.api.dsl.ApplicationBuildType.addConstant(
    name: String,
    value: String,
) {
    manifestPlaceholders += mapOf(name to value)
    buildConfigField("String", name, "\"$value\"")
}

android {
    namespace = "de.lemke.oneurl"
    compileSdk = 37
    defaultConfig {
        applicationId = "de.lemke.oneurl"
        minSdk = 26
        targetSdk = 37
        versionCode = 45
        versionName = "1.7.6"
        buildConfigField("boolean", "FIRST_RUN_SKIPPABLE", "false")
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
        all { signingConfig = signingConfigs.getByName(if (getProperty("releaseStoreFile").isNullOrEmpty()) "debug" else "release") }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            addConstant("APP_NAME", "OneURL")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk { debugSymbolLevel = "FULL" }
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            //noinspection NotShrinkingResources
            isShrinkResources = false
            applicationIdSuffix = ".debug"
            addConstant("APP_NAME", "OneURL (Debug)")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "META-INF/AL2.0"
            excludes += "META-INF/LGPL2.1"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/licenses/**"
        }
    }
    lint {
        warningsAsErrors = true
        // checkDependencies = false: private AAR deps (oneui-design, common-utils) surface
        // hundreds of unactionable warnings; flip to true once in-project surface is clean
        checkDependencies = false
        checkReleaseBuilds = true
        abortOnError = true
        baseline = file("lint-baseline.xml")
    }
    testOptions {
        unitTests {
            all { test -> test.useJUnitPlatform() }
        }
    }
}
dependencies {
    debugImplementation(libs.leakcanary)
    implementation(libs.oneui.design)
    implementation(libs.oneui.icons)
    implementation(libs.common.utils)
    implementation(libs.bundler)
    implementation(libs.volley)
    implementation(libs.bundles.room)
    implementation(libs.hilt.android)
    ksp(libs.room.compiler)
    ksp(libs.hilt.compiler)

    testImplementation(libs.konsist)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
secrets {
    propertiesFileName = "secrets.properties"
    defaultPropertiesFileName = "secrets.defaults.properties"
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

spotless {
    kotlin {
        target("src/**/*.kt")
        targetExclude("**/build/**", "**/generated/**")
        ktlint(libs.versions.ktlint.get())
        licenseHeaderFile(rootProject.file("config/spotless/apache-2.0.kt"))
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("xml") {
        target("src/**/*.xml")
        targetExclude("**/build/**")
        licenseHeaderFile(rootProject.file("config/spotless/apache-2.0.xml"), "(<[^!?])")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
    autoCorrect = false
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget = libs.versions.jvmTarget.get()
    reports {
        html.required.set(true)
        sarif.required.set(true)
    }
}
