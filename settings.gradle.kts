// The settings file is the entry point of every Gradle build.
// Its primary purpose is to define the subprojects.
// It is also used for some aspects of project-wide configuration, like managing plugins, dependencies, etc.
// https://docs.gradle.org/current/userguide/settings_file_basics.html
rootProject.name = "kanvas-root"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenLocal()
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
include(":font")
include(":font:core")
include(":font:sfnt")
include(":font:scaler")
include(":font:text")
include(":font:glyph")
include(":font:gpu-api")
include(":kanvas-skia")
include(":codec-api")
include(":codec-core")
include(":codec-common")
include(":codec-test-fixtures")
include(":codec-real-image-tests")
include(":codec-all-kotlin")
include(":codec-png-api")
include(":codec-png-kotlin")
include(":codec-jpeg-api")
include(":codec-jpeg-kotlin")
include(":codec-gif-kotlin")
include(":codec-bmp-kotlin")
include(":codec-wbmp-kotlin")
include(":codec-webp-kotlin")
include(":codec-ico-kotlin")
include(":codec-android")
include(":codec-animated")
include(":codec-extended")
include(":codec-image-generator")
include(":cpu-raster")
// :gpu-raster — GPU-backed device implementation built on wgpu4k.
// Depends on :kanvas-skia (consumes SkDevice / SkBitmap / SkPaint).
// MIGRATION_PLAN_GPU_WEBGPU.md G0/G1. The module was introduced in
// PR #458 (G1.0) but the `include` line was dropped from that merge ;
// restored here so :gpu-raster:test (ClearRedTest) actually runs.
include(":gpu-raster")
include(":gpu-renderer")
include(":gpu-renderer-scenes")
include(":render-pipeline")
include(":kadre-runtime")
include(":skia-integration-tests")
include(":integration-tests")
