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

import com.android.build.api.dsl.CommonExtension
import java.util.Properties

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.dependency.analysis)
}

spotless {
    kotlinGradle {
        target(
            fileTree(rootDir) {
                include("**/*.gradle.kts")
                exclude("**/build/**", "**/.gradle/**")
            },
        )
        licenseHeaderFile(rootProject.file("config/spotless/apache-2.0.kt"), "(^(?![\\/ ]\\*).*$)")
        ktlint(libs.versions.ktlint.get())
    }
}

/**
 * Converts a camelCase or mixedCase string to ENV_VAR_STYLE (uppercase with underscores).
 * Example: githubAccessToken -> GITHUB_ACCESS_TOKEN
 */
fun String.toEnvVarStyle(): String = replace(Regex("([a-z])([A-Z])"), "$1_$2").uppercase()

/**
 * Note: To configure GitHub credentials, you have to generate an access token with at least `read:packages` scope at
 * https://github.com/settings/tokens/new and then add it to any of the following:
 *
 * - Add `ghUsername` and `ghAccessToken` to Global Gradle Properties
 * - Set `GH_USERNAME` and `GH_ACCESS_TOKEN` in your environment variables or
 * - Create a `github.properties` file in your project folder with the following content:
 *      ghUsername=&lt;YOUR_GITHUB_USERNAME&gt;
 *      ghAccessToken=&lt;YOUR_GITHUB_ACCESS_TOKEN&gt;
 */
fun getProperty(key: String): String =
    Properties()
        .apply {
            rootProject
                .file("github.properties")
                .takeIf { it.exists() }
                ?.inputStream()
                ?.use { load(it) }
        }.getProperty(key)
        ?: rootProject.findProperty(key)?.toString()
        ?: System.getenv(key.toEnvVarStyle())
        ?: throw GradleException("Property $key not found")

val githubUsername = getProperty("ghUsername")
val githubAccessToken = getProperty("ghAccessToken")

val checkDependencyUpdates = providers.gradleProperty("lint.checkDependencyUpdates").getOrElse("true").toBoolean()

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://maven.pkg.github.com/tribalfs/oneui-design") {
            credentials {
                username = githubUsername
                password = githubAccessToken
            }
        }
        maven("https://maven.pkg.github.com/lemkinator/common-utils") {
            credentials {
                username = githubUsername
                password = githubAccessToken
            }
        }
    }
}

subprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-metadata-jvm") {
                useVersion("2.4.0")
                because(
                    "Hilt 2.59.x does not support Kotlin 2.4 — its bundled kotlin-metadata-jvm only reads metadata format ≤ 2.3.0. Remove once Hilt ships native Kotlin 2.4 support (track: github.com/google/dagger/issues/5001)",
                )
            }
        }
    }
    plugins.withId("com.android.base") {
        project.extensions.findByType(CommonExtension::class.java)?.apply {
            compileOptions.apply {
                sourceCompatibility = JavaVersion.VERSION_21
                targetCompatibility = JavaVersion.VERSION_21
            }

            // Renovate owns dependency freshness on its own PRs; enforcing there would fail every
            // in-flight bump against every other still-pending one.
            if (!checkDependencyUpdates) {
                lint.informational += setOf("GradleDependency", "NewerVersionAvailable")
            }

            @Suppress("UnstableApiUsage")
            testOptions.managedDevices.localDevices {
                register("pixel9Api35") {
                    device = "Pixel 9"
                    apiLevel = 35
                    systemImageSource = "aosp"
                    testedAbi = "x86_64"
                }
            }

            // oneui-design replaces these AOSP AndroidX modules with Samsung's SESL forks, which
            // keep the original package names — exclude the AOSP originals everywhere to prevent
            // shadowing. androidTest specifically needs SESL: instrumented tests launch SESL
            // activities calling SESL-only APIs (e.g. MenuItemCompat.setSeslNaviMenuItemType).
            plugins.withId("com.android.application") {
                configurations.configureEach {
                    exclude(group = "androidx.core", module = "core")
                    exclude(group = "androidx.core", module = "core-ktx")
                    exclude(group = "androidx.customview", module = "customview")
                    exclude(group = "androidx.coordinatorlayout", module = "coordinatorlayout")
                    exclude(group = "androidx.drawerlayout", module = "drawerlayout")
                    exclude(group = "androidx.viewpager2", module = "viewpager2")
                    exclude(group = "androidx.viewpager", module = "viewpager")
                    exclude(group = "androidx.appcompat", module = "appcompat")
                    exclude(group = "androidx.fragment", module = "fragment")
                    exclude(group = "androidx.fragment", module = "fragment-ktx")
                    exclude(group = "androidx.preference", module = "preference")
                    exclude(group = "androidx.recyclerview", module = "recyclerview")
                    exclude(group = "androidx.slidingpanelayout", module = "slidingpanelayout")
                    exclude(group = "androidx.swiperefreshlayout", module = "swiperefreshlayout")
                    exclude(group = "com.google.android.material", module = "material")
                }

                // exclude() alone is unreliable for androidx.core/core-ktx: several real,
                // non-SESL-forked libraries (activity, compose.ui, emoji2, autofill, window,
                // graphics, savedstate) each pull a different real core version, and once enough
                // conflicting real versions are in one graph, Gradle stops honoring the exclude
                // rule for this pair. Declaring the SESL fork as an alternate provider of the real
                // capability, then selecting it, closes that gap.
                dependencies {
                    components {
                        withModule("sesl.androidx.core:core") {
                            allVariants { withCapabilities { addCapability("androidx.core", "core", id.version) } }
                        }
                        withModule("sesl.androidx.core:core-ktx") {
                            allVariants { withCapabilities { addCapability("androidx.core", "core-ktx", id.version) } }
                        }
                    }
                }
                configurations.configureEach {
                    resolutionStrategy.capabilitiesResolution {
                        withCapability("androidx.core:core") { select(candidates.first { it.id.toString().startsWith("sesl.") }) }
                        withCapability("androidx.core:core-ktx") { select(candidates.first { it.id.toString().startsWith("sesl.") }) }
                    }
                }
            }
        }
    }
}
