buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 自带 Kotlin 支持；这里只指定 Kotlin 版本
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false
}
