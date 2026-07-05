import kotlinx.benchmark.gradle.JsBenchmarkTarget
import kotlinx.benchmark.gradle.JvmBenchmarkTarget

plugins {
    id("buildsrc.convention.kotlin-multiplatform")
    alias(libs.plugins.kotlinPluginAllOpen)
    alias(libs.plugins.kotlinxBenchmark)
}

kotlin {
    jvm {
        val mainCompilation = compilations.getByName("main")
        compilations.create("benchmark") {
            associateWith(mainCompilation)
        }
    }

    js(IR) {
        nodejs()
        val mainCompilation = compilations.getByName("main")
        compilations.create("benchmark") {
            associateWith(mainCompilation)
        }
    }

    sourceSets {
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val jvmBenchmark by getting {
            kotlin.srcDir("src/commonBenchmark/kotlin")
            dependencies {
                implementation(libs.kotlinxBenchmarkRuntime)
            }
        }

        val jsBenchmark by getting {
            kotlin.srcDir("src/commonBenchmark/kotlin")
            dependencies {
                implementation(libs.kotlinxBenchmarkRuntime)
            }
        }
    }
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

benchmark {
    configurations {
        named("main") {
            warmups = 5
            iterations = 5
            iterationTime = 500
            iterationTimeUnit = "ms"
        }
    }
    targets {
        register("jvmBenchmark") {
            this as JvmBenchmarkTarget
            jmhVersion = "1.37"
        }
        register("jsBenchmark") {
            this as JsBenchmarkTarget
        }
    }
}
