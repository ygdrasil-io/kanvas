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
    implementation(project(":codec:core"))
    implementation(project(":codec:common"))
    implementation(project(":codec"))
    implementation(project(":codec:ico"))
    implementation(project(":codec:android"))
    implementation(project(":codec:animated"))
    implementation(project(":codec:extended"))
    implementation(project(":codec:image-generator"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testImplementation(project(":codec:test-fixtures"))
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
        // Skia GM and image fixtures are owned by :skia-integration-tests.
        resources.srcDir("../skia-integration-tests/src/test/resources")
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
    kotlinCodecBackendRuntime(project(":codec"))
}

fun Test.useKotlinCodecBackendRuntime() {
    dependsOn("testClasses")
    shouldRunAfter(tasks.named("test"))

    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath + kotlinCodecBackendRuntime

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

tasks.register<Test>("testCodecSuiteWithKotlinBackend") {
    group = "verification"
    description = "Runs the backend-agnostic cpu-raster codec suite subset with codec-all-kotlin."

    useKotlinCodecBackendRuntime()

    filter {
        includeTestsMatching("org.skia.codec.*")
    }
}

tasks.register<JavaExec>("pipelineRuntimeEffectsV2SupportMatrix") {
    group = "verification"
    description = "Materializes the KAN-027 Runtime Effects V2 support matrix JSON and Markdown artifacts."

    dependsOn("classes")
    mainClass.set("org.skia.effects.runtime.RuntimeEffectsV2SupportMatrixReportKt")
    classpath = sourceSets.main.get().runtimeClasspath
    args(rootProject.layout.projectDirectory.dir("reports/wgsl-pipeline/runtime-effects-v2").asFile.absolutePath)
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
