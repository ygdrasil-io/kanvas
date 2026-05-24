plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(project(":codec-test-fixtures"))
    testImplementation(project(":codec-png-imageio"))
    testImplementation(project(":codec-png-kotlin"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}
