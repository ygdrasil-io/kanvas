// The settings file is the entry point of every Gradle build.
// Its primary purpose is to define the subprojects.
// It is also used for some aspects of project-wide configuration, like managing plugins, dependencies, etc.
// https://docs.gradle.org/current/userguide/settings_file_basics.html
rootProject.name = "kanvas-root"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://central.sonatype.com/repository/maven-snapshots/")
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
include(":cpu-raster")
// :gpu-raster — GPU-backed device implementation built on wgpu4k.
// Depends on :kanvas-skia (consumes SkDevice / SkBitmap / SkPaint).
// MIGRATION_PLAN_GPU_WEBGPU.md G0/G1. The module was introduced in
// PR #458 (G1.0) but the `include` line was dropped from that merge ;
// restored here so :gpu-raster:test (ClearRedTest) actually runs.
include(":gpu-raster")
include(":skia-integration-tests")
