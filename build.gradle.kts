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
        // Compile target: PyCharm Professional 2026.2 (installed via Toolbox, build 262.*).
        pycharmProfessional("2026.2.3")
    }
}

// JVM target is driven by the IntelliJ Platform plugin (matches the platform's JBR;
// PyCharm 2026.2 runs JBR 25). Do not pin source/targetCompatibility manually here —
// that causes the "Inconsistent JVM Target Compatibility" failure.

intellijPlatform {
    pluginConfiguration {
        id = "com.starmanmartin.sdc.intellij"
        name = "SimpleDomControl"
        version = project.version.toString()
        ideaVersion {
            // 2026.2+ only: compiled with JVM 25 bytecode, matching the platform's JBR 25.
            sinceBuild = "262"
            // Open-ended: stays installable on future IDE versions (e.g. 2026.3+).
            untilBuild = provider { null }
        }
    }
}

// Searchable-options building needs to launch the IDE; not needed for this plugin.
tasks.matching { it.name == "buildSearchableOptions" }.configureEach {
    enabled = false
}
