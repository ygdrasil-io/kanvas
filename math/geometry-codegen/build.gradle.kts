plugins {
    id("buildsrc.convention.kotlin-jvm")
    application
}

dependencies {
    implementation(libs.kotlinPoet)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinCompilerEmbeddable)
    testImplementation(project(":math:vector"))
    testImplementation(project(":math:geometry"))
    testImplementation(project(":math:matrix"))
}

application {
    mainClass.set("org.graphiks.math.codegen.MainKt")
}

tasks.register<JavaExec>("generateMathPrimitives") {
    group = "code generation"
    description = "Generates the versioned semantic math primitive sources."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    args("generate", rootProject.layout.projectDirectory.asFile.absolutePath)
}

tasks.register<JavaExec>("verifyMathPrimitivesGenerated") {
    group = "verification"
    description = "Verifies deterministic semantic math primitive generation and checked-in sources."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    args("verify", rootProject.layout.projectDirectory.asFile.absolutePath)
}
