package org.skia.gpu.webgpu.tools

import io.ygdrasil.wgsl.ast.FunctionDecl
import io.ygdrasil.wgsl.parser.parseWgslResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val shaderPath = args.firstOrNull()?.let { Path.of(it) } ?: Path.of("src/main/resources/shaders/solid_color.wgsl")
    val expectFailure = args.contains("--expect-failure")

    if (!shaderPath.isRegularFile()) {
        System.err.println("wgsl-parser-smoke sourcePath=${shaderPath.toAbsolutePath()} success=false diagnostics=1 entrypoints=0")
        System.err.println("wgsl-parser-smoke error=missing file. Publish parser deps via: rtk ./gradlew --no-daemon publishToMavenLocal (webgpu-ktypes repo)")
        exitProcess(2)
    }

    val source = Files.readString(shaderPath)
    val result = parseWgslResult(source)
    val entrypoints = result.translationUnit.declarations
        .asSequence()
        .filterIsInstance<FunctionDecl>()
        .flatMap { decl -> decl.attributes.asSequence().map { it.name } }
        .count { name -> name == "vertex" || name == "fragment" || name == "compute" }

    println("wgsl-parser-smoke sourcePath=${shaderPath.toAbsolutePath()} success=${result.isSuccess} diagnostics=${result.errors.size} entrypoints=$entrypoints")
    result.errors.forEachIndexed { index, error ->
        println("diag[$index] ${error.message} span=${error.span}")
    }

    if (expectFailure) {
        if (result.isSuccess) {
            System.err.println("wgsl-parser-smoke expected failure but parse succeeded")
            exitProcess(3)
        }
        return
    }

    if (!result.isSuccess) {
        exitProcess(1)
    }
}
