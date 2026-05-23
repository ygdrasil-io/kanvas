import java.net.URL
import kotlinx.benchmark.gradle.JsBenchmarkTarget
import kotlinx.benchmark.gradle.JvmBenchmarkTarget

plugins {
    id("buildsrc.convention.kotlin-multiplatform")
    alias(libs.plugins.kotlinPluginAllOpen)
    alias(libs.plugins.kotlinxBenchmark)
    id("org.jetbrains.dokka") version "2.2.0"
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

dependencies {
    // GFM (GitHub-Flavored Markdown) renderer — scopé sur dokkaGfm uniquement
    // (vs `dokkaPlugin(...)` qui l'aurait appliqué à tous les formats et écrasé HTML).
    dokkaGfmPlugin("org.jetbrains.dokka:gfm-plugin:2.2.0")
}

// On ne configure que dokkaGfm — le rendu HTML final est fait par MkDocs Material
// à partir de la sortie GFM (voir mkdocs.yml + .github/workflows/docs.yml).
tasks.dokkaGfm {
    moduleName.set("math")
    dokkaSourceSets.named("commonMain") {
        includes.from("module.md")
        sourceLink {
            localDirectory.set(file("src/commonMain/kotlin"))
            remoteUrl.set(URL("https://github.com/ygdrasil-io/kanvas/blob/master/math/src/commonMain/kotlin"))
            remoteLineSuffix.set("#L")
        }
    }
}
