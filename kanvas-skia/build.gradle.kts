plugins {
    id("buildsrc.convention.kotlin-jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))

    // D3.4 — WEBP decoding via the TwelveMonkeys ImageIO plugin.
    // Registers a WEBP `ImageReader` with the JVM's ImageIO SPI on
    // classpath load, so `ImageIO.read` decodes WEBP bytes the same
    // way it does PNG / JPEG / GIF / BMP / WBMP. Plan
    // (MIGRATION_PLAN_RASTER_COMPLETION.md § D3.4) recommended this
    // "Option B" external-dep approach over a ~3000 LOC pure-Kotlin
    // VP8L port. Read-only — TwelveMonkeys has no WEBP encoder.
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
        resources.srcDir("../kanvas/src/test/resources")
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
