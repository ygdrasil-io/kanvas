plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    implementation(kotlin("stdlib"))
    api(project(":codec-core"))
    api(project(":codec-png-kotlin"))
    api(project(":codec-jpeg-kotlin"))
    api(project(":codec-gif-kotlin"))
    api(project(":codec-wbmp-kotlin"))
    api(project(":codec-bmp-kotlin"))
    api(project(":codec-ico-kotlin"))
    api(project(":codec-extended"))
}
