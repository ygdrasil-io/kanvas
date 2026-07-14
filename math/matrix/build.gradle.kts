plugins {
    id("buildsrc.convention.kotlin-multiplatform")
}

kotlin {
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                api(project(":math:scalar"))
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
