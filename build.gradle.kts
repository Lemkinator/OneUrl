import java.util.Properties

/**
 * Note: To configure GitHub credentials, you have to do one of the following:
 * <ul>
 *      <li>Add `githubUsername` and `githubAccessToken` to Global Gradle Properties</li>
 *      <li>Set `GITHUB_USERNAME` and `GITHUB_ACCESS_TOKEN` in your environment variables</li>
 *      <li>Create a `github.properties` file in your project folder with the following content:</li>
 * </ul>
 *
 * <pre>
 *   githubUsername="YOUR_GITHUB_USERNAME"
 *   githubAccessToken="YOUR_GITHUB_ACCESS_TOKEN"
 * </pre>
 */
val githubProperties = Properties().apply {
    rootProject.file("github.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

val githubUsername: String = rootProject.findProperty("githubUsername") as String? // Global Gradle Properties
    ?: githubProperties.getProperty("githubUsername") // github.properties file
    ?: System.getenv("GITHUB_USERNAME") // Environment Variables
    ?: error("GitHub username not found")

val githubAccessToken: String = rootProject.findProperty("githubAccessToken") as String? // Global Gradle Properties
    ?: githubProperties.getProperty("githubAccessToken") // github.properties file
    ?: System.getenv("GITHUB_ACCESS_TOKEN") // Environment Variables
    ?: error("GitHub Access Token not found")

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
        classpath("com.android.tools.build:gradle:8.8.1")
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.1.0-1.0.29")
        classpath("com.google.android.libraries.mapsplatform.secrets-gradle-plugin:secrets-gradle-plugin:2.0.1")
        classpath("com.google.android.gms:oss-licenses-plugin:0.10.6")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://maven.pkg.github.com/tribalfs/sesl-androidx") {
            credentials {
                username = githubUsername
                password = githubAccessToken
            }
        }
        maven("https://maven.pkg.github.com/tribalfs/sesl-material-components-android") {
            credentials {
                username = githubUsername
                password = githubAccessToken
            }
        }
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
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("com.google.dagger.hilt.android") version "2.55" apply false
}