// The settings file is the entry point of every Gradle build.
// Its primary purpose is to define the subprojects.
// It is also used for some aspects of project-wide configuration, like managing plugins, dependencies, etc.
// https://docs.gradle.org/current/userguide/settings_file_basics.html
rootProject.name = "kanvas-root"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenLocal {
            content {
                excludeGroup("io.ygdrasil")
            }
        }
        google()
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
        ivy("https://nodejs.org/dist/") {
            name = "Node.js"
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("org.nodejs", "node")
            }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download/") {
            name = "Yarn"
            patternLayout {
                artifact("v[revision]/[artifact]-v[revision].[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("com.yarnpkg", "yarn")
            }
        }
    }
}

val wgpu4kPublishedVersion = "0.2.0-20260716.235022-2"
val webgpuKtypesPublishedVersion = "0.0.10-20260716.185724-3"
val wgpu4kNativePublishedVersion = "v29.0.0-20260716.085936-2"

gradle.beforeProject {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "io.ygdrasil" && requested.version.orEmpty().endsWith("-SNAPSHOT")) {
                val publishedVersion =
                    when (requested.name) {
                        "wgpu4k",
                        "wgpu4k-jvm",
                        "wgpu4k-toolkit",
                        "wgpu4k-toolkit-jvm",
                        -> wgpu4kPublishedVersion

                        "webgpu-ktypes",
                        "webgpu-ktypes-jvm",
                        "webgpu-ktypes-descriptors",
                        "webgpu-ktypes-descriptors-jvm",
                        -> webgpuKtypesPublishedVersion

                        "wgpu4k-native",
                        "wgpu4k-native-jvm",
                        "kffi",
                        "kffi-jvm",
                        -> wgpu4kNativePublishedVersion

                        else -> null
                    }
                if (publishedVersion != null) {
                    useVersion(publishedVersion)
                    because("Task 9 requires one immutable remote WebGPU runtime graph")
                }
            }
        }
    }
}

plugins {
    // Use the Foojay Toolchains plugin to automatically download JDKs required by subprojects.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Kadre is not assumed to be available from Maven Central during Kanvas integration.
// Keep it as an independent source build so Kanvas modules can depend on
// org.graphiks.kadre:* coordinates without vendoring Kadre modules into this build.
val kadreSourceBuildHasPublishedModules =
    file("external/poc-koreos/settings.gradle.kts").takeIf { it.isFile }
        ?.readText()
        ?.contains("include(\":kadre\")")
        ?: false

includeBuild("external/poc-koreos") {
    if (kadreSourceBuildHasPublishedModules) {
        dependencySubstitution {
            substitute(module("org.graphiks.kadre:kadre")).using(project(":kadre"))
            substitute(module("org.graphiks.kadre:kadre-win32")).using(project(":kadre-win32"))
            substitute(module("org.graphiks.kadre:kadre-x11")).using(project(":kadre-x11"))
            substitute(module("org.graphiks.kadre:kadre-wayland")).using(project(":kadre-wayland"))
        }
    }
}

include(":math")
include(":color-management")
include(":font")
include(":font:colr")
include(":font:core")
include(":font:sfnt")
include(":font:scaler")
include(":font:text")
include(":font:glyph")
include(":font:gpu-api")
include(":codec")
include(":codec:api")
include(":codec:core")
include(":codec:common")
include(":codec:test-fixtures")
include(":codec:png")
include(":codec:jpeg")
include(":codec:gif")
include(":codec:bmp")
include(":codec:wbmp")
include(":codec:webp")
include(":codec:ico")
include(":codec:extended")
include(":gpu-renderer")
include(":gpu-renderer-scenes")
include(":kanvas")
include(":kanvas:svg")
include(":integration-tests:svg")
include(":integration-tests:test-utils")
include(":integration-tests:diagnostic")
include(":integration-tests:skia")
include(":integration-tests:skia-evidence")
