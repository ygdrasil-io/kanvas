plugins {
    id("buildsrc.convention.kotlin-multiplatform")
}

kotlin {
    jvm()
    js {
        nodejs()
    }

    sourceSets {
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
