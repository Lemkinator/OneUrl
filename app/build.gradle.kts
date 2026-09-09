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

@file:OptIn(ExperimentalRoborazziApi::class)

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.gradle.secrets)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    alias(libs.plugins.kover)
    alias(libs.plugins.android.junit)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.roborazzi)
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
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }
    defaultConfig {
        applicationId = "de.lemke.oneurl"
        minSdk = 26
        targetSdk = 37
        versionCode = 45
        versionName = "1.7.6"
        testInstrumentationRunner = "de.lemke.oneurl.HiltTestRunner"
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
        // checkDependencies = false: private AAR deps surface
        // hundreds of unactionable warnings; flip to true once in-project surface is clean
        checkDependencies = false
        // Explicit: pins intent against future AGP default changes.
        checkReleaseBuilds = true
        abortOnError = true
        baseline = file("lint-baseline.xml")
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true

            all { test ->
                test.useJUnitPlatform()
                // MockK ≥ 1.14 on JDK 21 needs this:
                test.jvmArgs("-XX:+EnableDynamicAgentLoading")
                test.systemProperty("robolectric.graphicsMode", "NATIVE")
                test.systemProperty("roborazzi.test.record", project.findProperty("roborazzi.record") ?: "false")
                test.systemProperty("roborazzi.test.verify", project.findProperty("roborazzi.verify") ?: "true")
            }
        }
        animationsDisabled = true
    }
}

androidComponents {
    listOf("nonMinifiedRelease", "benchmarkRelease").forEach { buildType ->
        onVariants(selector().withBuildType(buildType)) { variant ->
            variant.buildConfigFields!!.put(
                "FIRST_RUN_SKIPPABLE",
                com.android.build.api.variant
                    .BuildConfigField("boolean", "true", "Allow benchmarks to skip the first-run chain"),
            )
        }
    }
}

dependencies {
    implementation(libs.oneui.design)
    implementation(libs.oneui.icons)
    implementation(libs.common.utils)
    implementation(libs.bundler)
    // Pins kotlinx-coroutines-core for main/androidTest classpath parity - do not remove without
    // re-checking dependencyInsight on both debugRuntimeClasspath and debugAndroidTestRuntimeClasspath
    // (see commit 7e1ebd6).
    implementation(libs.coroutines.android)
    implementation(libs.volley)
    implementation(libs.bundles.room)
    implementation(libs.hilt.android)
    ksp(libs.room.compiler)
    ksp(libs.hilt.compiler)

    implementation(libs.profileinstaller)
    baselineProfile(project(":benchmarks"))
    debugImplementation(libs.leakcanary)

    testImplementation(testFixtures(libs.common.utils))

    testImplementation(libs.arch.core.testing)
    testImplementation(libs.bundles.unit.test)
    testImplementation(libs.bundles.robolectric.test)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.konsist)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.vintage.engine)
    kspTest(libs.hilt.compiler)

    androidTestImplementation(testFixtures(libs.common.utils))
    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.kotest.assertions.core)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
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
        licenseHeaderFile(rootProject.file("config/spotless/apache-2.0.kt"))
        ktlint(libs.versions.ktlint.get())
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

baselineProfile {
    dexLayoutOptimization = true
}

roborazzi {
    outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
    compare {
        outputDir.set(layout.buildDirectory.dir("reports/roborazzi"))
    }
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "*.databinding.*",
                    "*.BuildConfig",
                    "*.di.*",
                    "*Hilt_*",
                    "*_HiltModules*",
                    "*_Factory",
                    "*_Provide*",
                    "*_MembersInjector",
                    "dagger.hilt.*",
                    "hilt_aggregated_deps.*",
                )
            }
        }
        variant("debug") {
            verify {
                rule {
                    minBound(19, coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.INSTRUCTION)
                    minBound(7, coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH)
                }
            }
        }
    }
}
