plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    api(project(":gpu-renderer"))
    api(project(":font:gpu-api"))
    api(project(":color-management"))
    implementation(kotlin("stdlib"))
    implementation(project(":math"))
    implementation(project(":font"))
    implementation(project(":font:colr"))
    api(libs.wgslCoreJvm)
    api(libs.wgslParserJvm)
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.10.2")
    testImplementation(libs.wgslCoreJvm)
    testImplementation(libs.wgslParserJvm)
}

sourceSets {
    test {
        resources.srcDir("../color-management/src/test/resources")
    }
}

tasks.withType<Test> {
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--enable-native-access=ALL-UNNAMED",
    )
    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.register<JavaExec>("kanvasTextGpuEvidence") {
    group = "verification"
    description = "Native WebGPU evidence: renders A8 text via Surface and checks GPU/CPU coverage parity (opt-in)."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.graphiks.kanvas.TextGpuEvidenceMainKt")
    dependsOn(tasks.named("testClasses"))
    outputs.upToDateWhen { false }
    jvmArgs(
        buildList {
            add("--add-opens=java.base/java.lang=ALL-UNNAMED")
            add("--enable-native-access=ALL-UNNAMED")
            if (org.gradle.internal.os.OperatingSystem.current().isMacOsX) {
                add("-XstartOnFirstThread")
            }
        },
    )
}
