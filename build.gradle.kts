buildscript {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }

    dependencies {
        //java.lang.NoSuchFieldError: on > 8.2.2
        // No static field sesl_color_picker_opacity_seekbar of type I in class Landroidx/picker/R$id;
        // or its superclasses (declaration of 'androidx.picker.R$id' appears in /data/app/~~LPtmGRN0Qtb33YZcCPU9VQ==/de.lemke.oneurl-JdPm3x6R0Y3pyZvPcRFckQ==/base.apk!classes2.dex)
        //         at androidx.picker3.widget.SeslColorPicker.initOpacitySeekBar(SeslColorPicker.java:587)
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("com.google.android.libraries.mapsplatform.secrets-gradle-plugin:secrets-gradle-plugin:2.0.1")
        //classpath("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:1.9.0-1.0.11")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

plugins {
    id("com.android.application") version "8.2.2" apply false
    id("com.android.library") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.7.20" apply false
    id("com.google.dagger.hilt.android") version "2.42" apply false
}