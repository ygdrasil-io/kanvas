plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    api(project(":gpu-renderer"))
    api(project(":font:gpu-api"))
    implementation(kotlin("stdlib"))
    implementation(project(":math"))
    implementation(project(":font"))
    implementation(project(":font:colr"))
    api("org.graphiks:wgsl-core-jvm:1.0.0-SNAPSHOT")
    api("org.graphiks:wgsl-parser-jvm:1.0.0-SNAPSHOT")
    testImplementation(kotlin("test"))
    testImplementation("org.graphiks:wgsl-core-jvm:1.0.0-SNAPSHOT")
    testImplementation("org.graphiks:wgsl-parser-jvm:1.0.0-SNAPSHOT")
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
