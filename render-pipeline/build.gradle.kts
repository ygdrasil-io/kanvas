plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--add-modules", "jdk.incubator.vector"))
}

tasks.withType<Test>().configureEach {
    jvmArgs("--add-modules", "jdk.incubator.vector")
}

tasks.register<JavaExec>("cpuVectorPilotBenchmark") {
    group = "verification"
    description = "Runs the GRA-28 scalar vs Java 25 Vector API solid-rect pilot benchmark."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.skia.pipeline.CpuVectorPilotBenchmarkKt")
    jvmArgs("--add-modules", "jdk.incubator.vector")
    listOf(
        "kanvas.cpu.vector.benchmark.width",
        "kanvas.cpu.vector.benchmark.height",
        "kanvas.cpu.vector.benchmark.warmups",
        "kanvas.cpu.vector.benchmark.iterations",
    ).forEach { key ->
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
}
