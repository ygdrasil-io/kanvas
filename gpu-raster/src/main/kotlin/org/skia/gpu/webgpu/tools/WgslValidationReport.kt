package org.skia.gpu.webgpu.tools

import org.graphiks.wgsl.ast.FunctionDecl
import org.graphiks.wgsl.ast.IntLiteral
import org.graphiks.wgsl.ast.NamedType
import org.graphiks.wgsl.ast.StructDecl
import org.graphiks.wgsl.ast.StructType
import org.graphiks.wgsl.ast.VariableDecl
import org.graphiks.wgsl.ir.GlobalVariable
import org.graphiks.wgsl.ir.StorageClass
import org.graphiks.wgsl.ir.Type
import org.graphiks.wgsl.ir.TypeInner
import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult
import org.graphiks.wgsl.proc.Layouter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence

data class WgslValidationFileReport(
    val path: String,
    val success: Boolean,
    val diagnostics: List<String>,
    val entryPoints: List<String>,
    val bindings: List<String>,
    val uniformStructs: List<UniformStructReport>,
)

data class UniformStructReport(
    val variable: String,
    val members: List<UniformMemberOffset>,
    val source: UniformReflectionSource = UniformReflectionSource.AstDeclaration,
)

enum class UniformReflectionSource {
    AstDeclaration,
    LoweredLayout,
}

data class UniformMemberOffset(
    val name: String,
    val offset: Int,
    val size: Int? = null,
    val alignment: Int? = null,
)

data class WgslValidationSummary(
    val files: List<WgslValidationFileReport>,
) {
    val fileCount: Int get() = files.size
    val parsedCount: Int get() = files.count { it.success }
    val diagnosticsCount: Int get() = files.sumOf { it.diagnostics.size }
    val reflectionCoverageCount: Int get() = files.count { it.uniformStructs.isNotEmpty() }
}

object WgslValidationReport {
    fun run(shaderRoot: Path): WgslValidationSummary {
        require(Files.isDirectory(shaderRoot)) { "shaderRoot must be a directory: $shaderRoot" }
        val files = Files.walk(shaderRoot).use { stream ->
            stream.asSequence()
                .filter { it.isRegularFile() && it.extension == "wgsl" }
                .sortedBy { it.toString() }
                .map { validateFile(it) }
                .toList()
        }
        return WgslValidationSummary(files)
    }

    fun validateSource(pathLabel: String, source: String): WgslValidationFileReport {
        return validateSource(Path.of(pathLabel), source)
    }

    private fun validateFile(path: Path): WgslValidationFileReport {
        val source = Files.readString(path)
        return validateSource(path, source)
    }

    private fun validateSource(path: Path, source: String): WgslValidationFileReport {
        val parsed = parseWgslResult(source)
        val diagnostics = parsed.errors.map { "${it.message} span=${it.span}" }
        val entryPoints = parsed.translationUnit.declarations.asSequence()
            .filterIsInstance<FunctionDecl>()
            .flatMap { fn ->
                fn.attributes.asSequence()
                    .map { attr -> attr.name }
                    .filter { it == "vertex" || it == "fragment" || it == "compute" }
                    .map { stage -> "$stage:${fn.name}" }
            }
            .toList()

        if (!parsed.isSuccess) {
            return WgslValidationFileReport(
                path = path.toString(),
                success = false,
                diagnostics = diagnostics,
                entryPoints = entryPoints,
                bindings = emptyList(),
                uniformStructs = emptyList(),
            )
        }

        val bindings = mutableListOf<String>()
        val uniformStructs = mutableListOf<UniformStructReport>()
        val structDeclByName = parsed.translationUnit.declarations
            .filterIsInstance<StructDecl>()
            .associateBy { it.name }
        parsed.translationUnit.declarations
            .filterIsInstance<VariableDecl>()
            .forEach { decl ->
                val group = decl.attributes.firstOrNull { it.name == "group" }
                    ?.args?.firstOrNull()
                    ?.let { it as? IntLiteral }
                    ?.value
                val binding = decl.attributes.firstOrNull { it.name == "binding" }
                    ?.args?.firstOrNull()
                    ?.let { it as? IntLiteral }
                    ?.value
                if (group != null && binding != null) {
                    bindings += "${decl.name}@group=$group,binding=$binding"
                }
                if (decl.storageClass == "uniform") {
                    val typeName = when (val t = decl.type) {
                        is StructType -> t.name
                        is NamedType -> t.name
                        else -> null
                    }
                    val structDecl = typeName?.let { structDeclByName[it] }
                    if (structDecl != null) {
                        val members = structDecl.members.mapIndexed { index, member ->
                            val annotatedOffset = member.attributes.firstOrNull { it.name == "offset" }
                                ?.args?.firstOrNull()
                                ?.let { it as? IntLiteral }
                                ?.value
                                ?.toInt()
                            UniformMemberOffset(name = member.name, offset = annotatedOffset ?: index * 16)
                        }
                        uniformStructs += UniformStructReport(
                            variable = decl.name,
                            members = members,
                            source = UniformReflectionSource.AstDeclaration,
                        )
                    }
                }
            }
        val reflectionDiagnostics = mutableListOf<String>()
        try {
            val module = Lowerer().lower(parsed.translationUnit)
            val layouter = Layouter().also { it.update(module) }
            module.globalVariables.forEach { global ->
                global.binding?.let { binding ->
                    bindings += "${global.name}@group=${binding.group},binding=${binding.index}"
                }
                if (global.storageClass == StorageClass.Uniform) {
                    val report = reflectUniformStruct(global, module.types, layouter)
                    if (report.members.isNotEmpty()) {
                        uniformStructs.removeAll { it.variable == report.variable }
                        uniformStructs += report
                    }
                }
            }
        } catch (t: Throwable) {
            reflectionDiagnostics += "reflection-skip ${t::class.simpleName}: ${t.message}"
        }

        return WgslValidationFileReport(
            path = path.toString(),
            success = true,
            diagnostics = diagnostics + reflectionDiagnostics,
            entryPoints = entryPoints,
            bindings = bindings.sorted(),
            uniformStructs = uniformStructs.sortedBy { it.variable },
        )
    }

    private fun reflectUniformStruct(
        global: GlobalVariable,
        types: org.graphiks.wgsl.arena.UniqueArena<Type>,
        layouter: Layouter,
    ): UniformStructReport {
        val members = when (val inner = types[global.type].inner) {
            is TypeInner.Struct -> {
                var cursor = 0
                inner.members.map { member ->
                    val layout = layouter[member.type]
                    cursor = layout.alignment.roundUp(cursor)
                    val reflected = UniformMemberOffset(
                        name = member.name,
                        offset = cursor,
                        size = layout.size,
                        alignment = layout.alignment.roundUp(1),
                    )
                    cursor += layout.size
                    reflected
                }
            }
            else -> emptyList()
        }
        return UniformStructReport(
            variable = global.name,
            members = members,
            source = UniformReflectionSource.LoweredLayout,
        )
    }
}

fun main(args: Array<String>) {
    val shaderRoot = args.firstOrNull()?.let(Path::of)
        ?: Path.of("src/main/resources/shaders")
    val summary = WgslValidationReport.run(shaderRoot)
    println(
        "wgsl-validate-all root=$shaderRoot files=${summary.fileCount} parsed=${summary.parsedCount} " +
            "diagnostics=${summary.diagnosticsCount} reflectionCoverage=${summary.reflectionCoverageCount}",
    )
    summary.files.forEach { report ->
        val filename = Path.of(report.path).name
        println(
            "file=$filename success=${report.success} diagnostics=${report.diagnostics.size} " +
                "entrypoints=${report.entryPoints.size} bindings=${report.bindings.size} uniforms=${report.uniformStructs.size}",
        )
        report.diagnostics.forEach { diag -> println("diag $diag") }
    }
}
