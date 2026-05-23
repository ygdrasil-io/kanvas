plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":math"))
    implementation(project(":kanvas-skia"))
    implementation(project(":cpu-raster"))
    implementation(project(":codec-core"))
    implementation(project(":codec-all-awt"))
    implementation(project(":codec-android"))
    implementation(project(":codec-animated"))
    implementation(project(":codec-image-generator"))
    implementation(project(":codec-png-imageio"))
    implementation(project(":codec-jpeg-imageio"))
    implementation(project(":codec-gif-imageio"))
    implementation(project(":codec-webp-imageio"))
    implementation(project(":codec-extended"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
}

sourceSets {
    test {
        // GMs and DM harness consume font + reference PNG fixtures from
        // the legacy kanvas tree. Mirrors the :cpu-raster setup.
        resources.srcDir("../kanvas-legacy/src/test/resources")
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
