import java.net.URL

plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("org.jetbrains.dokka") version "2.2.0"
}

dependencies {
    implementation(kotlin("stdlib"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")

    // GFM (GitHub-Flavored Markdown) renderer — scopé sur dokkaGfm uniquement
    // (vs `dokkaPlugin(...)` qui l'aurait appliqué à tous les formats et écrasé HTML).
    dokkaGfmPlugin("org.jetbrains.dokka:gfm-plugin:2.2.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// On ne configure que dokkaGfm — le rendu HTML final est fait par MkDocs Material
// à partir de la sortie GFM (voir mkdocs.yml + .github/workflows/docs.yml).
tasks.dokkaGfm {
    moduleName.set("math")
    dokkaSourceSets.named("main") {
        includes.from("module.md")
        sourceLink {
            localDirectory.set(file("src/main/kotlin"))
            remoteUrl.set(URL("https://github.com/ygdrasil-io/kanvas/blob/master/math/src/main/kotlin"))
            remoteLineSuffix.set("#L")
        }
    }
}
