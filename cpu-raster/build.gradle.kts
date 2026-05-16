plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":math"))
    implementation(project(":kanvas-skia"))

    // D3.4 — WEBP decoding via the TwelveMonkeys ImageIO plugin.
    // Registers a WEBP `ImageReader` with the JVM's ImageIO SPI on
    // classpath load, so `ImageIO.read` decodes WEBP bytes the same
    // way it does PNG / JPEG / GIF / BMP / WBMP. Used by SkWebpCodec
    // (moved here in iter 3c). Read-only — TwelveMonkeys has no
    // WEBP encoder.
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    // D1.4 — PathOps regression harness loads upstream Skia
    // fixtures from a JSON resource. jackson-databind is the
    // standard mature JSON parser ; only the harness imports it.
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
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
