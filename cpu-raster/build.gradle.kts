import java.net.URL
import org.gradle.api.tasks.testing.Test

plugins {
    id("buildsrc.convention.kotlin-jvm")
    // G7.2 — Dokka GFM doc generation for :cpu-raster (CPU
    // rasterization core). See :math/build.gradle.kts for the
    // reference setup.
    id("org.jetbrains.dokka") version "2.2.0"
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":math"))
    implementation(project(":kanvas-skia"))
    implementation(project(":codec-core"))
    implementation(project(":codec-common"))
    implementation(project(":codec-all-kotlin"))
    implementation(project(":codec-ico-kotlin"))
    implementation(project(":codec-android"))
    implementation(project(":codec-animated"))
    implementation(project(":codec-extended"))
    implementation(project(":codec-image-generator"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testImplementation(project(":codec-test-fixtures"))
    testImplementation(project(":codec-all-awt"))
    testImplementation(project(":codec-png-imageio"))
    testImplementation(project(":codec-jpeg-imageio"))
    testImplementation(project(":codec-gif-imageio"))
    testImplementation(project(":codec-bmp-imageio"))
    testImplementation(project(":codec-wbmp-imageio"))
    testImplementation(project(":codec-webp-imageio"))
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    // D1.4 — PathOps regression harness loads upstream Skia
    // fixtures from a JSON resource. jackson-databind is the
    // standard mature JSON parser ; only the harness imports it.
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")

    // G7.2 — GFM (GitHub-Flavored Markdown) renderer scoped to
    // `:cpu-raster:dokkaGfm` only.
    dokkaGfmPlugin("org.jetbrains.dokka:gfm-plugin:2.2.0")
}

sourceSets {
    test {
        // kanvas-legacy is excluded from the build (see settings.gradle.kts)
        // but its src/test/resources/{images,original-888} are still required
        // at runtime for cpu-raster's GMs (during transit through this module
        // before iter 4-5 splits them into :skia-integration-tests /
        // :integration-tests) and DM harness.
        resources.srcDir("../kanvas-legacy/src/test/resources")
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

val kotlinCodecBackendRuntime by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    kotlinCodecBackendRuntime(project(":codec-all-kotlin"))
}

val awtCodecBackendArtifacts = setOf(
    "codec-all-awt",
    "codec-png-imageio",
    "codec-jpeg-imageio",
    "codec-gif-imageio",
    "codec-bmp-imageio",
    "codec-wbmp-imageio",
    "codec-webp-imageio",
)

fun File.isAwtCodecBackendArtifact(): Boolean =
    awtCodecBackendArtifacts.any { artifact ->
        name == "$artifact.jar" || name.startsWith("$artifact-")
    }

fun Test.useKotlinCodecBackendRuntime() {
    dependsOn("testClasses")
    shouldRunAfter(tasks.named("test"))

    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath.filter { file ->
        !file.isAwtCodecBackendArtifact()
    } + kotlinCodecBackendRuntime

    systemProperty("kanvas.codec.expectedBackend", "kotlin")
}

tasks.register<Test>("testCodecWithKotlinBackend") {
    group = "verification"
    description = "Runs the cpu-raster codec backend smoke test with codec-all-kotlin instead of the temporary AWT/ImageIO codec bundle."

    useKotlinCodecBackendRuntime()

    filter {
        includeTestsMatching("org.skia.codec.CpuRasterKotlinCodecBackendTest")
    }
}

val legacyCodecSuiteKotlinBackendBlockers = listOf(
    "org.skia.codec.webp.SkWebpCodecTest",
)

tasks.register<Test>("testCodecSuiteWithKotlinBackend") {
    group = "verification"
    description = "Runs the backend-agnostic cpu-raster codec suite subset with codec-all-kotlin; excludes documented legacy ImageIO/VP8 lossy blockers."

    useKotlinCodecBackendRuntime()

    filter {
        includeTestsMatching("org.skia.codec.*")
        legacyCodecSuiteKotlinBackendBlockers.forEach { testClass ->
            excludeTestsMatching(testClass)
        }
    }
}

// G7.2 — Dokka GFM config. See :math/build.gradle.kts for the
// reference setup.
tasks.dokkaGfm {
    moduleName.set("cpu-raster")
    dokkaSourceSets.named("main") {
        includes.from("module.md")
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl.set(URL("https://github.com/ygdrasil-io/kanvas/blob/master/cpu-raster/src/main/kotlin"))
            remoteLineSuffix.set("#L")
        }
    }
}
