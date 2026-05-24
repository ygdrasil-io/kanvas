// The settings file is the entry point of every Gradle build.
// Its primary purpose is to define the subprojects.
// It is also used for some aspects of project-wide configuration, like managing plugins, dependencies, etc.
// https://docs.gradle.org/current/userguide/settings_file_basics.html
rootProject.name = "kanvas-root"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
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


// :kanvas-legacy — hand-written legacy implementation under `kanvas-legacy/`.
// Excluded from the build (frozen as a read-only reference). Test fixtures
// under kanvas-legacy/src/test/resources/{images,original-888} are still
// consumed by :kanvas-skia via a srcDir reference in kanvas-skia/build.gradle.kts.
include(":math")
include(":kanvas-skia")
include(":codec-api")
include(":codec-core")
include(":codec-common")
include(":codec-all-awt")
include(":codec-all-kotlin")
include(":codec-png-imageio")
include(":codec-png-api")
include(":codec-png-kotlin")
include(":codec-jpeg-imageio")
include(":codec-jpeg-api")
include(":codec-jpeg-kotlin")
include(":codec-gif-imageio")
include(":codec-gif-kotlin")
include(":codec-bmp-imageio")
include(":codec-bmp-kotlin")
include(":codec-wbmp-imageio")
include(":codec-wbmp-kotlin")
include(":codec-webp-imageio")
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
include(":skia-integration-tests")
include(":integration-tests")
