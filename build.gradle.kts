import java.util.Properties

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
    Properties().apply { rootProject.file("github.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }.getProperty(key)
        ?: rootProject.findProperty(key)?.toString()
        ?: System.getenv(key.toEnvVarStyle())
        ?: throw GradleException("Property $key not found")

val githubUsername = getProperty("ghUsername")
val githubAccessToken = getProperty("ghAccessToken")

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
        classpath("com.android.tools.build:gradle:8.13.2")
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.3.3")
        classpath("com.google.android.libraries.mapsplatform.secrets-gradle-plugin:secrets-gradle-plugin:2.0.1")
        classpath("com.google.android.gms:oss-licenses-plugin:0.10.10")
    }
}

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

plugins {
    id("org.jetbrains.kotlin.android") version "2.3.0" apply false
    id("com.google.dagger.hilt.android") version "2.57.2" apply false
}