plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    implementation(libs.kotlinPoet)
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("org.graphiks.math.codegen.MainKt")
}
