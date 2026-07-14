plugins {
    id("buildsrc.convention.kotlin-multiplatform")
}

kotlin {
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":math:scalar"))
                api(project(":math:vector"))
                api(project(":math:geometry"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
