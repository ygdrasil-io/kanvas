plugins {
    id("buildsrc.convention.kotlin-multiplatform")
}

kotlin {
    jvm()

    sourceSets {
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
