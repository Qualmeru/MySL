// AGP 9 has Kotlin built in, so there's no separate Kotlin Android plugin. This picks the Kotlin
// version it compiles with; the Compose compiler plugin below must be the same version.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.5.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
