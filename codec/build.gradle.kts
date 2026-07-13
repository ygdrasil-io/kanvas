plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    implementation(kotlin("stdlib"))
    api(project(":codec:core"))
    api(project(":codec:png"))
    api(project(":codec:jpeg"))
    api(project(":codec:jpeg-ls"))
    api(project(":codec:jpeg2000"))
    api(project(":codec:jpegxl"))
    api(project(":codec:gif"))
    api(project(":codec:wbmp"))
    api(project(":codec:bmp"))
    api(project(":codec:webp"))
    api(project(":codec:ico"))
    api(project(":codec:extended"))

    testImplementation(project(":codec:test-fixtures"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}
