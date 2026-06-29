plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":math"))
    implementation(project(":kanvas-skia"))
    implementation(project(":cpu-raster"))
    implementation(project(":codec:core"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

sourceSets {
    test {
        // GMs need font + image/reference PNG fixtures from :skia-integration-tests.
        resources.srcDir("../skia-integration-tests/src/test/resources")
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
