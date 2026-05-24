plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("java-library")
}

dependencies {
    implementation(kotlin("stdlib"))
    api(project(":codec-core"))
    api(project(":codec-png-imageio"))
    api(project(":codec-jpeg-imageio"))
    api(project(":codec-gif-imageio"))
    api(project(":codec-bmp-imageio"))
    api(project(":codec-wbmp-imageio"))
    api(project(":codec-webp-imageio"))
    api(project(":codec-ico-kotlin"))
    api(project(":codec-extended"))
}
