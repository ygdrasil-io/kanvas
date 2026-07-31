package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialLoweringContext
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramCompiler
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramResult
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.recording.canonicalSnapshotHash
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasPreparedRuntimeEffectResolver
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesArtifactInput
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesPacker
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesPackingLimits
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesPackingResult
import org.graphiks.kanvas.gpu.renderer.vertices.GPUPreparedVerticesRefusalCodes
import org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexMode
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.types.a
import org.graphiks.kanvas.types.alphaByte
import org.graphiks.kanvas.types.blueByte
import org.graphiks.kanvas.types.greenByte
import org.graphiks.kanvas.types.isAffine
import org.graphiks.kanvas.types.redByte

private const val PREPARED_VERTICES_MATERIAL_DICTIONARY_VERSION =
    "material-dictionary:prepared-vertices:v1"

/** Pure FP-06 lowering for a single recorded DrawVertices or DrawMesh operation. */
object GPUPreparedVerticesLowerer {
    fun lower(
        operation: DisplayOp,
        operationIndex: Int,
        target: GPUTargetFacts,
        capabilities: GPUCapabilities,
    ): GPUPreparedVerticesLowering = when (operation) {
        is DisplayOp.DrawVertices -> lowerVertices(
            vertices = operation.vertices,
            paint = operation.paint.snapshotForPreparedText(),
            transform = operation.transform,
            clip = operation.clip,
            operationKind = GPUPreparedVerticesOperationKind.DrawVertices,
            operationIndex = operationIndex,
            target = target,
            capabilities = capabilities,
            provenance = "drawVertices",
            meshBounds = null,
            finalBlend = operation.paint.blendMode.toGpuBlendFacts(),
        )
        is DisplayOp.DrawMesh -> lowerMesh(operation, operationIndex, target, capabilities)
        else -> refused(
            GPUPreparedVerticesRefusalCodes.Material,
            operationIndex,
            "operation",
            "unsupported_operation",
            mapOf("operation" to operation::class.simpleName.orEmpty()),
        )
    }

    private fun lowerMesh(
        operation: DisplayOp.DrawMesh,
        operationIndex: Int,
        target: GPUTargetFacts,
        capabilities: GPUCapabilities,
    ): GPUPreparedVerticesLowering {
        val finalBlend = (operation.blendMode ?: operation.paint.blendMode).toGpuBlendFacts()
        val program = operation.mesh.program
        if (program == null) {
            // This is the exact public Canvas.drawMesh normalization, kept as one route.
            return lowerVertices(
                vertices = operation.mesh.vertices,
                paint = operation.paint.copy(blendMode = operation.blendMode ?: operation.paint.blendMode)
                    .snapshotForPreparedText(),
                transform = operation.transform,
                clip = operation.clip,
                operationKind = GPUPreparedVerticesOperationKind.DrawVertices,
                operationIndex = operationIndex,
                target = target,
                capabilities = capabilities,
                provenance = "drawMesh:no-program",
                meshBounds = null,
                finalBlend = finalBlend,
            )
        }
        val bounds = operation.mesh.bounds
        if (listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).any { !it.isFinite() } ||
            bounds.right < bounds.left || bounds.bottom < bounds.top
        ) {
            return refused(
                GPUPreparedVerticesRefusalCodes.MeshBounds,
                operationIndex,
                "mesh-bounds",
                "invalid_mesh_bounds",
            )
        }
        val mapping = when (val mapped = program.toPreparedMeshProgramMappingResult(
            paintAlpha = operation.paint.color.a,
            finalTargetBlendMode = finalBlend.mode,
        )) {
            is GPUPreparedMeshProgramMappingResult.Ready -> mapped.mapping
            is GPUPreparedMeshProgramMappingResult.Refused -> return refused(
                mapped.code, operationIndex, "mesh-program", mapped.facts["reason"] ?: "mapping_refused", mapped.facts,
            )
        }
        val material = when (val compiled = GPUPreparedMaterialProgramCompiler.compile(
            descriptor = mapping.descriptor,
            paintAlpha = mapping.paintAlpha,
            context = materialContext(target, capabilities),
        )) {
            is GPUPreparedMaterialProgramResult.Ready -> compiled.program
            is GPUPreparedMaterialProgramResult.Refused -> return refused(
                meshMaterialCode(compiled.code), operationIndex, "mesh-program", "compiler_refused",
                mapOf("compilerCode" to compiled.code, "sourceKind" to compiled.sourceKind.name),
            )
        }
        return lowerVertices(
            vertices = operation.mesh.vertices,
            paint = operation.paint.snapshotForPreparedText(),
            transform = operation.transform,
            clip = operation.clip,
            operationKind = GPUPreparedVerticesOperationKind.DrawMesh,
            operationIndex = operationIndex,
            target = target,
            capabilities = capabilities,
            provenance = "drawMesh:program",
            meshBounds = bounds,
            finalBlend = finalBlend,
            material = material,
        )
    }

    private fun lowerVertices(
        vertices: Vertices,
        paint: org.graphiks.kanvas.paint.Paint,
        transform: Matrix33,
        clip: org.graphiks.kanvas.canvas.ClipStack,
        operationKind: GPUPreparedVerticesOperationKind,
        operationIndex: Int,
        target: GPUTargetFacts,
        capabilities: GPUCapabilities,
        provenance: String,
        meshBounds: org.graphiks.kanvas.types.Rect?,
        finalBlend: GPUBlendFacts,
        material: GPUPreparedMaterialProgram? = null,
    ): GPUPreparedVerticesLowering {
        val transformFailure = transformFailure(transform)
        if (transformFailure != null) return refused(
            GPUPreparedVerticesRefusalCodes.Transform, operationIndex, "transform", transformFailure,
        )
        // Invoke the shared clip authority before publishing a ready draw.
        val clipFailure = runCatching { clip.toGPUClipFacts(target) }.exceptionOrNull()
        if (clipFailure != null) return refused(
            GPUPreparedVerticesRefusalCodes.Material, operationIndex, "clip", "clip_authority_refused",
        )
        val packed = when (val result = GPUPreparedVerticesPacker.pack(
            input = vertices.toArtifactInput(provenance),
            limits = packingLimits(capabilities),
            supportsUint32Index = capabilities.facts.any { it.name == "vertices.uint32_index" && it.value == "supported" },
        )) {
            is GPUPreparedVerticesPackingResult.Ready -> result
            is GPUPreparedVerticesPackingResult.Refused -> return refused(
                result.code, operationIndex, "geometry", result.facts["reason"] ?: "packer_refused", result.facts,
            )
        }
        val resolvedMaterial = material ?: when (val compiled = compilePaint(paint, target, capabilities)) {
            is MaterialResult.Ready -> compiled.material
            is MaterialResult.Refused -> return refused(
                GPUPreparedVerticesRefusalCodes.Material, operationIndex, "material", compiled.reason, compiled.facts,
            )
        }
        val blendPlan = finalBlend.copy(sourceAlpha = resolvedMaterial.preCoverageSourceAlpha)
            .canonicalBlendPlan(
                coverage = GPUCoverageConsumption.FullOrScissor,
                targetFormatClass = target.colorFormat,
            )
        if (blendPlan is GPUBlendPlan.UnsupportedBlend) return refused(
            GPUPreparedVerticesRefusalCodes.Material, operationIndex, "blend", "blend_unsupported",
            mapOf("commonDiagnosticCode" to blendPlan.diagnostic.code),
        )
        return GPUPreparedVerticesLowering.Ready(
            GPUPreparedVerticesDraw.create(
                artifact = packed.artifact,
                operationKind = operationKind,
                material = resolvedMaterial,
                transform = transform,
                clip = clip,
                finalBlend = finalBlend.copy(sourceAlpha = resolvedMaterial.preCoverageSourceAlpha),
                blendPlan = blendPlan,
                sourceBounds = packed.sourceBounds,
                meshBounds = meshBounds,
                operationIndex = operationIndex,
                provenance = provenance,
                paintAlphaApplicationCount = 1,
                primitiveColorPresent = vertices.colors != null,
            ),
        )
    }
}

private sealed interface MaterialResult {
    data class Ready(val material: GPUPreparedMaterialProgram) : MaterialResult
    data class Refused(val reason: String, val facts: Map<String, String>) : MaterialResult
}

private fun compilePaint(
    paint: org.graphiks.kanvas.paint.Paint,
    target: GPUTargetFacts,
    capabilities: GPUCapabilities,
): MaterialResult = try {
    val mapped = paint.toPreparedMaterialMapping()
    when (val compiled = GPUPreparedMaterialProgramCompiler.compile(
        mapped.descriptor, mapped.paintAlpha, materialContext(target, capabilities),
    )) {
        is GPUPreparedMaterialProgramResult.Ready -> MaterialResult.Ready(compiled.program)
        is GPUPreparedMaterialProgramResult.Refused -> MaterialResult.Refused(
            "compiler_refused", mapOf("compilerCode" to compiled.code, "sourceKind" to compiled.sourceKind.name),
        )
    }
} catch (_: Exception) {
    MaterialResult.Refused("mapper_exception", emptyMap())
}

private fun materialContext(target: GPUTargetFacts, capabilities: GPUCapabilities): GPUMaterialLoweringContext =
    GPUMaterialLoweringContext(
        capabilityClass = capabilities.canonicalSnapshotHash(),
        targetFormatClass = target.colorFormat,
        dictionaryVersion = PREPARED_VERTICES_MATERIAL_DICTIONARY_VERSION,
        runtimeEffectResolver = KanvasPreparedRuntimeEffectResolver(),
    )

private fun Vertices.toArtifactInput(provenance: String): GPUPreparedVerticesArtifactInput =
    GPUPreparedVerticesArtifactInput(
        topology = when (mode) {
            VertexMode.TRIANGLES -> GPUVertexMode.Triangles
            VertexMode.TRIANGLE_STRIP -> GPUVertexMode.TriangleStrip
            VertexMode.TRIANGLE_FAN -> GPUVertexMode.TriangleFan
        },
        positions = FloatArray(positions.size * 2).also { output ->
            positions.forEachIndexed { index, point -> output[index * 2] = point.x; output[index * 2 + 1] = point.y }
        },
        colorsRgba8 = colors?.toRgba8(),
        texCoords = texCoords?.let { coordinates -> FloatArray(coordinates.size * 2).also { output ->
            coordinates.forEachIndexed { index, point -> output[index * 2] = point.x; output[index * 2 + 1] = point.y }
        } },
        indices = indices?.toIntArray(),
        provenance = provenance,
    )

private fun List<Color>.toRgba8(): ByteArray = ByteArray(size * 4).also { output ->
    forEachIndexed { index, color ->
        output[index * 4] = color.redByte.toByte()
        output[index * 4 + 1] = color.greenByte.toByte()
        output[index * 4 + 2] = color.blueByte.toByte()
        output[index * 4 + 3] = color.alphaByte.toByte()
    }
}

private fun packingLimits(capabilities: GPUCapabilities): GPUPreparedVerticesPackingLimits {
    val maxBytes = capabilities.limits?.maxBufferSize ?: 64L * 1024L * 1024L
    return GPUPreparedVerticesPackingLimits(
        maxVertices = 1_000_000,
        maxIndices = 3_000_000,
        maxVertexBytes = maxBytes,
        maxIndexBytes = maxBytes,
        maxFanExpandedIndices = 3_000_000,
    )
}

private fun transformFailure(transform: Matrix33): String? = when {
    listOf(transform.scaleX, transform.skewX, transform.transX, transform.skewY, transform.scaleY,
        transform.transY, transform.persp0, transform.persp1, transform.persp2).any { !it.isFinite() } -> "non_finite"
    !transform.isAffine() -> "perspective"
    else -> null
}

private fun meshMaterialCode(compilerCode: String): String = when {
    "runtime_effect.descriptor" in compilerCode -> GPUPreparedVerticesRefusalCodes.MeshProgramUnregistered
    "unregistered" in compilerCode -> GPUPreparedVerticesRefusalCodes.MeshProgramUnregistered
    "child" in compilerCode -> GPUPreparedVerticesRefusalCodes.MeshProgramChild
    "uniform" in compilerCode -> GPUPreparedVerticesRefusalCodes.MeshProgramAbi
    "cpu" in compilerCode -> GPUPreparedVerticesRefusalCodes.MeshProgramCpuUnavailable
    "wgsl_validation" in compilerCode -> GPUPreparedVerticesRefusalCodes.MeshProgramWgslValidation
    "wgsl" in compilerCode -> GPUPreparedVerticesRefusalCodes.MeshProgramWgslUnavailable
    "abi" in compilerCode -> GPUPreparedVerticesRefusalCodes.MeshProgramAbi
    "resource" in compilerCode -> GPUPreparedVerticesRefusalCodes.MeshProgramResource
    else -> GPUPreparedVerticesRefusalCodes.Material
}

private fun refused(
    code: String,
    operationIndex: Int,
    stage: String,
    reason: String,
    facts: Map<String, String> = emptyMap(),
): GPUPreparedVerticesLowering.Refused = GPUPreparedVerticesLowering.Refused(
    code = code,
    operationIndex = operationIndex,
    facts = linkedMapOf(
        "stage" to stage,
        "reason" to reason,
        "authority" to when (stage) {
            "geometry" -> "GPUPreparedVerticesPacker"
            "mesh-program" -> "GPUMaterialMapper"
            "material" -> "GPUPreparedMaterialProgramCompiler"
            "transform" -> "GPUPreparedVerticesLowerer"
            "clip" -> "GPUClipMapper"
            else -> "GPUPreparedVerticesLowerer"
        },
    ).apply { putAll(facts) },
)
