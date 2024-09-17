import java.util.Properties

/**
 * Note: You have to create a "github.properties" file inside your project folder containing the following:
 *
 * <pre>
 *   githubUsername="YOUR_GITHUB_USERNAME"
 *   githubPassword="YOUR_GITHUB_ACCESS_TOKEN"
 * </pre>
 */
val githubProperties = Properties().apply {
    rootProject.file("github.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
/** Or add githubUsername and githubPassword to Global Gradle Properties. **/
val githubUsername: String? by rootProject
val githubPassword: String? by rootProject
val ghUsername: String = githubUsername
    ?: githubProperties.getProperty("githubUsername")
    ?: System.getenv("GPR_USER")
    ?: error("GitHub username not found")
val ghPassword: String = githubPassword
    ?: githubProperties.getProperty("githubPassword")
    ?: System.getenv("GPR_API_KEY")
    ?: error("GitHub password not found")

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.20")
        classpath("com.android.tools.build:gradle:8.6.0")
        classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.0.20-1.0.25")
        classpath("com.google.android.libraries.mapsplatform.secrets-gradle-plugin:secrets-gradle-plugin:2.0.1")
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
                password = githubPassword
            }
        }
        maven("https://maven.pkg.github.com/tribalfs/sesl-material-components-android") {
            credentials {
                username = githubUsername
                password = githubPassword
            }
        }
    }
}

plugins {
    id("org.jetbrains.kotlin.android") version "1.9.25" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
}