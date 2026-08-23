plugins {
    id("buildsrc.convention.kotlin-multiplatform")
}

kotlin {
    jvm()
    js {
        nodejs()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir("src/generated/kotlin")
            dependencies {
                implementation(project(":math:scalar"))
                api(project(":math:vector"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
