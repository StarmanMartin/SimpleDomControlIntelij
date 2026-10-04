plugins {
    id("java")
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.starmanmartin"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Compile target: IntelliJ IDEA 2024.2 (JBR 21). Gson comes from the platform.
        intellijIdeaCommunity("2024.2.4")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "com.starmanmartin.sdc.intellij"
        name = "SimpleDomControl"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { "261.*" }
        }
    }
}

// Searchable-options building needs to launch the IDE; not needed for this plugin.
tasks.matching { it.name == "buildSearchableOptions" }.configureEach {
    enabled = false
}
