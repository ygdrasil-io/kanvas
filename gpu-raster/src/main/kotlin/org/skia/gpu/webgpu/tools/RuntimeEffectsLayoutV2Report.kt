package org.skia.gpu.webgpu.tools

import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectDescriptorRegistry
import org.skia.effects.runtime.registerRuntimeEffectsV2SupportMatrixBuiltins
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

data class RuntimeEffectsLayoutV2Summary(
    val rows: List<RuntimeEffectLayoutV2Row>,
) {
    val schemaVersion: String = "kanvas.runtime-effects.v2.layout"
    val total: Int get() = rows.size
    val layoutMatched: Int get() = rows.count { it.status == "layout-matched" }
    val layoutMismatched: Int get() = rows.count { it.status == "layout-mismatched" }
}

data class RuntimeEffectLayoutV2Row(
    val stableId: String,
    val wgslImplementationId: String,
    val shaderPath: String,
    val status: String,
    val uniformBlockSize: Int,
    val uniforms: List<RuntimeEffectLayoutV2Uniform>,
    val diagnostics: List<String>,
    val pipelineCacheKeyPolicy: String,
    val pipelineCacheKeyAxes: List<String>,
)

data class RuntimeEffectLayoutV2Uniform(
    val name: String,
    val descriptorType: String,
    val descriptorOffset: Int,
    val descriptorSize: Int,
    val wgslOffset: Int?,
    val wgslSize: Int?,
    val wgslAlignment: Int?,
    val status: String,
    val diagnostic: String,
)

object RuntimeEffectsLayoutV2Report {
    fun run(shaderRoot: Path = Path.of("src/main/resources/shaders")): RuntimeEffectsLayoutV2Summary {
        registerRuntimeEffectsV2SupportMatrixBuiltins()
        val validationByFileName = WgslValidationReport.run(shaderRoot).files
            .associateBy { Path.of(it.path).name }
        val rows = SkRuntimeEffectDescriptorRegistry.supportMatrixEntries()
            .asSequence()
            .filter { it.descriptorStatus == "descriptor-backed" }
            .filter { it.gpuSupport.startsWith("supported:") }
            .map { entry ->
                val wgslImplementationId = requireNotNull(entry.wgslImplementationId) {
                    "descriptor-backed GPU row missing WGSL implementation id: ${entry.stableId}"
                }
                val shaderName = wgslImplementationId.removePrefix("wgsl/") + ".wgsl"
                buildRow(
                    stableId = entry.stableId,
                    wgslImplementationId = wgslImplementationId,
                    shaderName = shaderName,
                    descriptorUniforms = entry.uniforms,
                    validation = validationByFileName[shaderName],
                )
            }
            .sortedBy { it.stableId }
            .toList()
        return RuntimeEffectsLayoutV2Summary(rows)
    }

    fun exportMarkdown(summary: RuntimeEffectsLayoutV2Summary): String = buildString {
        appendLine("# Runtime Effects Layout V2")
        appendLine()
        appendLine("Derived evidence. `SkRuntimeEffectDescriptorRegistry` and WGSL lowered reflection are the sources.")
        appendLine(
            "Status counts: total=${summary.total}; layout-matched=${summary.layoutMatched}; " +
                "layout-mismatched=${summary.layoutMismatched}.",
        )
        appendLine()
        appendLine(
            "Mismatch diagnostic: `runtime-effect.layout-reflection-mismatch`; " +
                "missing reflection diagnostic: `wgsl.reflection.uniform-member-missing`.",
        )
        appendLine("No uniform values enter runtime-effect pipeline cache keys.")
        appendLine()
        appendLine("| Stable id | WGSL implementation | Status | Uniform block bytes | Diagnostics |")
        appendLine("|---|---|---|---:|---|")
        summary.rows.forEach { row ->
            append("| ")
            append(markdownCell(row.stableId))
            append(" | ")
            append(markdownCell(row.wgslImplementationId))
            append(" | ")
            append(markdownCell(row.status))
            append(" | ")
            append(row.uniformBlockSize)
            append(" | ")
            append(markdownCell(row.diagnostics.joinToString("; ")))
            appendLine(" |")
        }
        appendLine()
        appendLine("## Uniforms")
        summary.rows.forEach { row ->
            appendLine()
            appendLine("### ${row.stableId}")
            appendLine()
            appendLine("| Name | Descriptor type | Descriptor offset | Descriptor size | WGSL offset | WGSL size | WGSL alignment | Status | Diagnostic |")
            appendLine("|---|---|---:|---:|---:|---:|---:|---|---|")
            row.uniforms.forEach { uniform ->
                append("| ")
                append(markdownCell(uniform.name))
                append(" | ")
                append(markdownCell(uniform.descriptorType))
                append(" | ")
                append(uniform.descriptorOffset)
                append(" | ")
                append(uniform.descriptorSize)
                append(" | ")
                append(uniform.wgslOffset ?: "-")
                append(" | ")
                append(uniform.wgslSize ?: "-")
                append(" | ")
                append(uniform.wgslAlignment ?: "-")
                append(" | ")
                append(markdownCell(uniform.status))
                append(" | ")
                append(markdownCell(uniform.diagnostic))
                appendLine(" |")
            }
        }
    }

    fun exportJson(summary: RuntimeEffectsLayoutV2Summary): String = buildString {
        append("{")
        append("\"schemaVersion\":")
        appendJsonString(summary.schemaVersion)
        append(",\"generatedBy\":")
        appendJsonString("RuntimeEffectsLayoutV2Report.exportJson")
        append(",\"sourceOfTruth\":")
        appendJsonString("SkRuntimeEffectDescriptorRegistry + WgslValidationReport lowered layout")
        append(",\"counts\":{")
        append("\"total\":${summary.total}")
        append(",\"layoutMatched\":${summary.layoutMatched}")
        append(",\"layoutMismatched\":${summary.layoutMismatched}")
        append("}")
        append(",\"nonClaims\":")
        appendJsonStringArray(
            listOf(
                "No dynamic SkSL compilation.",
                "No arbitrary WGSL input.",
                "No uniform values in runtime-effect pipeline cache keys.",
            ),
        )
        append(",\"rows\":[")
        summary.rows.forEachIndexed { index, row ->
            if (index > 0) append(",")
            append("{")
            append("\"stableId\":")
            appendJsonString(row.stableId)
            append(",\"wgslImplementationId\":")
            appendJsonString(row.wgslImplementationId)
            append(",\"shaderPath\":")
            appendJsonString(row.shaderPath)
            append(",\"status\":")
            appendJsonString(row.status)
            append(",\"uniformBlockSize\":${row.uniformBlockSize}")
            append(",\"pipelineCacheKeyPolicy\":")
            appendJsonString(row.pipelineCacheKeyPolicy)
            append(",\"pipelineCacheKeyAxes\":")
            appendJsonStringArray(row.pipelineCacheKeyAxes)
            append(",\"diagnostics\":")
            appendJsonStringArray(row.diagnostics)
            append(",\"uniforms\":[")
            row.uniforms.forEachIndexed { uniformIndex, uniform ->
                if (uniformIndex > 0) append(",")
                append("{")
                append("\"name\":")
                appendJsonString(uniform.name)
                append(",\"descriptorType\":")
                appendJsonString(uniform.descriptorType)
                append(",\"descriptorOffset\":${uniform.descriptorOffset}")
                append(",\"descriptorSize\":${uniform.descriptorSize}")
                append(",\"wgslOffset\":")
                appendJsonNullableInt(uniform.wgslOffset)
                append(",\"wgslSize\":")
                appendJsonNullableInt(uniform.wgslSize)
                append(",\"wgslAlignment\":")
                appendJsonNullableInt(uniform.wgslAlignment)
                append(",\"status\":")
                appendJsonString(uniform.status)
                append(",\"diagnostic\":")
                appendJsonString(uniform.diagnostic)
                append("}")
            }
            append("]}")
        }
        append("]}")
    }

    private fun buildRow(
        stableId: String,
        wgslImplementationId: String,
        shaderName: String,
        descriptorUniforms: List<SkRuntimeEffect.Uniform>,
        validation: WgslValidationFileReport?,
    ): RuntimeEffectLayoutV2Row {
        val reflectedByName = validation
            ?.uniformStructs
            ?.firstOrNull { it.variable == "uniforms" && it.source == UniformReflectionSource.LoweredLayout }
            ?.members
            ?.associateBy { it.name }
            .orEmpty()
        val rowDiagnostics = mutableListOf<String>()
        if (validation == null) {
            rowDiagnostics += "wgsl.reflection.source-missing stableId=$stableId shader=$shaderName"
        } else {
            if (!validation.success) {
                rowDiagnostics += "wgsl.reflection.parse-failed stableId=$stableId shader=$shaderName"
            }
            validation.diagnostics.forEach { diagnostic ->
                rowDiagnostics += "wgsl.reflection.diagnostic stableId=$stableId shader=$shaderName diagnostic=$diagnostic"
            }
            if (reflectedByName.isEmpty()) {
                rowDiagnostics += "wgsl.reflection.uniform-struct-missing stableId=$stableId shader=$shaderName variable=uniforms"
            }
        }
        val uniforms = descriptorUniforms.map { uniform ->
            val reflected = reflectedByName[uniform.name]
            val descriptorSize = uniform.sizeInBytes()
            val diagnostic = when {
                reflected == null ->
                    "wgsl.reflection.uniform-member-missing stableId=$stableId uniform=${uniform.name}"
                reflected.offset != uniform.offset || reflected.size != descriptorSize ->
                    "runtime-effect.layout-reflection-mismatch stableId=$stableId uniform=${uniform.name} " +
                        "expectedOffset=${uniform.offset} actualOffset=${reflected.offset} " +
                        "expectedSize=$descriptorSize actualSize=${reflected.size}"
                else -> "none"
            }
            if (diagnostic != "none") {
                rowDiagnostics += diagnostic
            }
            RuntimeEffectLayoutV2Uniform(
                name = uniform.name,
                descriptorType = uniform.type.toString(),
                descriptorOffset = uniform.offset,
                descriptorSize = descriptorSize,
                wgslOffset = reflected?.offset,
                wgslSize = reflected?.size,
                wgslAlignment = reflected?.alignment,
                status = if (diagnostic == "none") "matched" else "mismatched",
                diagnostic = diagnostic,
            )
        }
        val diagnostics = rowDiagnostics.ifEmpty { listOf("none") }
        return RuntimeEffectLayoutV2Row(
            stableId = stableId,
            wgslImplementationId = wgslImplementationId,
            shaderPath = "gpu-raster/src/main/resources/shaders/$shaderName",
            status = if (diagnostics.all { it == "none" }) "layout-matched" else "layout-mismatched",
            uniformBlockSize = descriptorUniforms.maxOfOrNull { it.offset + it.sizeInBytes() } ?: 0,
            uniforms = uniforms,
            diagnostics = diagnostics,
            pipelineCacheKeyPolicy = "uniform-values-excluded",
            pipelineCacheKeyAxes = listOf("wgslImplementationId", "blendMode"),
        )
    }

    private fun markdownCell(value: String): String =
        value.replace("|", "\\|").replace("\n", " ")

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        append("\\u")
                        append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        append(ch)
                    }
                }
            }
        }
        append('"')
    }

    private fun StringBuilder.appendJsonStringArray(values: List<String>) {
        append("[")
        values.forEachIndexed { index, value ->
            if (index > 0) append(",")
            appendJsonString(value)
        }
        append("]")
    }

    private fun StringBuilder.appendJsonNullableInt(value: Int?) {
        if (value == null) {
            append("null")
        } else {
            append(value)
        }
    }
}

fun writeRuntimeEffectsLayoutV2Report(
    outputRoot: Path,
    shaderRoot: Path = Path.of("src/main/resources/shaders"),
) {
    val summary = RuntimeEffectsLayoutV2Report.run(shaderRoot)
    Files.createDirectories(outputRoot)
    Files.writeString(
        outputRoot.resolve("runtime-effects-layout-v2.json"),
        RuntimeEffectsLayoutV2Report.exportJson(summary) + "\n",
    )
    Files.writeString(
        outputRoot.resolve("runtime-effects-layout-v2.md"),
        RuntimeEffectsLayoutV2Report.exportMarkdown(summary),
    )
}

fun main(args: Array<String>) {
    val outputRoot = args.firstOrNull()
        ?.let(Path::of)
        ?: Path.of("reports/wgsl-pipeline/runtime-effects-layout-v2")
    val shaderRoot = args.getOrNull(1)
        ?.let(Path::of)
        ?: Path.of("gpu-raster/src/main/resources/shaders")
    writeRuntimeEffectsLayoutV2Report(outputRoot, shaderRoot)
}
