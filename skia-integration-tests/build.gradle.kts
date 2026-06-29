plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":math"))
    implementation(project(":kanvas-skia"))
    implementation(project(":cpu-raster"))
    implementation(project(":codec:jpeg"))
    implementation(project(":codec:png"))
    implementation(project(":codec:webp"))
    implementation(project(":codec:core"))
    implementation(project(":codec:android"))
    implementation(project(":codec:animated"))
    implementation(project(":codec:image-generator"))
    implementation(project(":codec:extended"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
