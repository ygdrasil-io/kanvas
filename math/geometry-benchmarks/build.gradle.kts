plugins {
    id("buildsrc.convention.kotlin-multiplatform")
    alias(libs.plugins.kotlinPluginAllOpen)
    alias(libs.plugins.kotlinxBenchmark)
}

allOpen {
    annotation("kotlinx.benchmark.State")
    annotation("org.openjdk.jmh.annotations.State")
}

kotlin {
    jvm()
    js {
        nodejs()
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":math:vector"))
                implementation(project(":math:geometry"))
                implementation(project(":math:matrix"))
                implementation(libs.kotlinxBenchmarkRuntime)
            }
        }
    }
}

benchmark {
    targets {
        register("jvm")
        register("js")
    }
    configurations {
        named("main") {
            warmups = 5
            iterations = 10
            iterationTime = 300
            iterationTimeUnit = "ms"
        }
    }
}

val jvmMainCompilation = kotlin.targets.getByName("jvm").compilations.getByName("main")
val allocationReport = layout.buildDirectory.file("reports/allocations.json")

tasks.register<JavaExec>("measureJvmGeometryAllocations") {
    group = "benchmark"
    description = "Measures observed JVM allocations for a semantic point transform (opt-in)."
    dependsOn(jvmMainCompilation.compileTaskProvider)
    classpath(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    mainClass.set("org.graphiks.math.benchmarks.JvmAllocationProbe")
    args(allocationReport.get().asFile.absolutePath)
    outputs.file(allocationReport)
    outputs.upToDateWhen { false }
}
