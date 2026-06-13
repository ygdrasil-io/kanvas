package org.graphiks.kanvas.gpu.renderer

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Verifies that the initial GPU renderer scaffold matches the package layout target. */
class GPURendererLayoutSurfaceTest {
    /** Ensures every documented scaffold type can be loaded from the JVM test classpath. */
    @Test
    fun `target package layout exposes the documented scaffold types`() {
        val missingTypes = expectedTypes.filterNot(::classExists)

        assertTrue(
            actual = missingTypes.isEmpty(),
            message = "Missing gpu-renderer scaffold types: ${missingTypes.joinToString()}",
        )
    }

    /** Ensures scaffold declarations keep KDoc comments while behavior is still absent. */
    @Test
    fun `main scaffold declarations are documented`() {
        val undocumentedDeclarations = sourceFiles()
            .flatMap(::undocumentedDeclarations)

        assertTrue(
            actual = undocumentedDeclarations.isEmpty(),
            message = "Missing KDoc on declarations: ${undocumentedDeclarations.joinToString()}",
        )
    }

    /** Ensures the target scaffold exposes typed contracts instead of empty marker placeholders. */
    @Test
    fun `main scaffold declarations are typed contracts`() {
        val placeholderDeclarations = sourceFiles()
            .flatMap(::placeholderDeclarations)

        assertTrue(
            actual = placeholderDeclarations.isEmpty(),
            message = "Empty placeholder declarations remain: ${placeholderDeclarations.joinToString()}",
        )
    }

    /** Ensures public fake methods can later return normally when implemented. */
    @Test
    fun `main scaffold methods do not expose nothing return type`() {
        val nothingReturns = sourceFiles()
            .flatMap(::nothingReturnDeclarations)

        assertTrue(
            actual = nothingReturns.isEmpty(),
            message = "Public contract methods must not return Nothing: ${nothingReturns.joinToString()}",
        )
    }

    /** Returns true when a class name resolves from the compiled test classpath. */
    private fun classExists(fqcn: String): Boolean =
        runCatching { Class.forName(fqcn) }.isSuccess

    /** Finds Kotlin source files in the gpu-renderer main source set. */
    private fun sourceFiles(): List<File> =
        File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

    /** Returns declarations whose nearest preceding non-annotation line is not KDoc. */
    private fun undocumentedDeclarations(file: File): List<String> {
        val lines = file.readLines()
        return lines.mapIndexedNotNull { index, line ->
            if (!line.trimStart().isDocumentedDeclaration()) return@mapIndexedNotNull null

            val previous = lines
                .subList(0, index)
                .asReversed()
                .firstOrNull { previousLine ->
                    val trimmed = previousLine.trim()
                    trimmed.isNotEmpty() && !trimmed.startsWith("@")
                }

            if (previous?.trim()?.endsWith("*/") == true) {
                null
            } else {
                "${file.relativeTo(File(".")).path}:${index + 1}:${line.trim()}"
            }
        }
    }

    /** Returns declarations that are still empty placeholder shells. */
    private fun placeholderDeclarations(file: File): List<String> =
        file.readLines().mapIndexedNotNull { index, line ->
            val trimmed = line.trimStart()
            if (trimmed.isPlaceholderDeclaration()) {
                "${file.relativeTo(File(".")).path}:${index + 1}:$trimmed"
            } else {
                null
            }
        }

    /** Returns public function declarations that expose Nothing as their declared return type. */
    private fun nothingReturnDeclarations(file: File): List<String> =
        file.readLines().mapIndexedNotNull { index, line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("fun ") && ": Nothing" in trimmed) {
                "${file.relativeTo(File(".")).path}:${index + 1}:$trimmed"
            } else {
                null
            }
        }

    /** Returns true when this line starts a declaration that should have KDoc. */
    private fun String.isDocumentedDeclaration(): Boolean =
        declarationPrefixes.any { startsWith(it) }

    /** Returns true when this line declares a named type without a body or typed constructor. */
    private fun String.isPlaceholderDeclaration(): Boolean =
        placeholderDeclarationPrefixes.any { startsWith(it) } &&
            !contains("(") &&
            !contains("{") &&
            !contains("=")

    /** Constants used by the layout surface tests. */
    private companion object {
        /** Declaration starts that the scaffold KDoc check enforces. */
        val declarationPrefixes = listOf(
            "class ",
            "data class ",
            "enum class ",
            "sealed interface ",
            "interface ",
            "value class ",
            "fun ",
            "typealias ",
        )

        /** Declaration starts that cannot remain as empty placeholder shells. */
        val placeholderDeclarationPrefixes = listOf(
            "class ",
            "data class ",
            "sealed interface ",
            "interface ",
        )

        /** Fully qualified class names required by the package layout target. */
        val expectedTypes = listOf(
            "org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode",
            "org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain",
            "org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity",
            "org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSink",
            "org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDump",
            "org.graphiks.kanvas.gpu.renderer.telemetry.GPUTelemetryLedger",
            "org.graphiks.kanvas.gpu.renderer.telemetry.GPUTelemetryCounter",
            "org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheTelemetry",
            "org.graphiks.kanvas.gpu.renderer.telemetry.GPUBudgetTelemetry",
            "org.graphiks.kanvas.gpu.renderer.telemetry.GPUPromotionEvidence",
            "org.graphiks.kanvas.gpu.renderer.telemetry.GPUPerformanceGate",
            "org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities",
            "org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact",
            "org.graphiks.kanvas.gpu.renderer.capabilities.GPUFeatureRequirement",
            "org.graphiks.kanvas.gpu.renderer.capabilities.GPULimitRequirement",
            "org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity",
            "org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.state.GPUTargetState",
            "org.graphiks.kanvas.gpu.renderer.state.GPUTargetTextureDescriptor",
            "org.graphiks.kanvas.gpu.renderer.state.GPUBlendPlan",
            "org.graphiks.kanvas.gpu.renderer.state.GPUBlendMode",
            "org.graphiks.kanvas.gpu.renderer.state.GPUAlphaPlan",
            "org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan",
            "org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan",
            "org.graphiks.kanvas.gpu.renderer.state.GPUSampleState",
            "org.graphiks.kanvas.gpu.renderer.color.GPUColorManagementPlan",
            "org.graphiks.kanvas.gpu.renderer.color.GPUColorValueSpec",
            "org.graphiks.kanvas.gpu.renderer.color.GPUColorSpaceDescriptor",
            "org.graphiks.kanvas.gpu.renderer.color.GPUColorProfileDescriptor",
            "org.graphiks.kanvas.gpu.renderer.color.GPUColorConversionPlan",
            "org.graphiks.kanvas.gpu.renderer.color.GPUColorTransformPlan",
            "org.graphiks.kanvas.gpu.renderer.color.GPUWorkingColorSpacePlan",
            "org.graphiks.kanvas.gpu.renderer.color.GPUGradientColorPlan",
            "org.graphiks.kanvas.gpu.renderer.color.GPUColorUniformPlan",
            "org.graphiks.kanvas.gpu.renderer.color.GPUHDRColorPlan",
            "org.graphiks.kanvas.gpu.renderer.color.GPUGainmapPlan",
            "org.graphiks.kanvas.gpu.renderer.color.GPUColorStorePlan",
            "org.graphiks.kanvas.gpu.renderer.color.GPUColorCachePlan",
            "org.graphiks.kanvas.gpu.renderer.color.GPUColorDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.coordinates.GPUCoordinateSpace",
            "org.graphiks.kanvas.gpu.renderer.coordinates.GPUTransformPlan",
            "org.graphiks.kanvas.gpu.renderer.coordinates.GPUInverseTransformPlan",
            "org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelGridPlan",
            "org.graphiks.kanvas.gpu.renderer.coordinates.GPUBoundsPlan",
            "org.graphiks.kanvas.gpu.renderer.coordinates.GPUBoundsProof",
            "org.graphiks.kanvas.gpu.renderer.coordinates.GPURoundingPlan",
            "org.graphiks.kanvas.gpu.renderer.coordinates.GPUClipReductionProof",
            "org.graphiks.kanvas.gpu.renderer.coordinates.GPUCoordinatePayloadPlan",
            "org.graphiks.kanvas.gpu.renderer.coordinates.GPUTransformDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand",
            "org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID",
            "org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandFamily",
            "org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandProvenance",
            "org.graphiks.kanvas.gpu.renderer.commands.GPUDrawOrderingToken",
            "org.graphiks.kanvas.gpu.renderer.commands.GPUCommandBounds",
            "org.graphiks.kanvas.gpu.renderer.commands.GPUCommandCapture",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUPaintDescriptor",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUPaintPipelinePlan",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUPaintStagePlan",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUPaintEvaluationOrder",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourceDescriptor",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourceKind",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourcePlan",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUSolidColorPlan",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUGradientPlan",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUGradientKind",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUGradientGeometryPlan",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUGradientStopPlan",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUGradientStopStorePlan",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialTileMode",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSamplingPlan",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUImageShaderPlan",
            "org.graphiks.kanvas.gpu.renderer.materials.GPULocalMatrixShaderPlan",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUShaderBlendSourcePlan",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUPaintColorPlan",
            "org.graphiks.kanvas.gpu.renderer.materials.MaterialKey",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialDictionary",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialProgramID",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialLoweringContext",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialRootSet",
            "org.graphiks.kanvas.gpu.renderer.materials.WGSLSnippet",
            "org.graphiks.kanvas.gpu.renderer.materials.WGSLSnippetID",
            "org.graphiks.kanvas.gpu.renderer.materials.WGSLSnippetNode",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialAssemblyPlan",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourcePayloadPlan",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourceDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.materials.GPUPaintPipelineDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPURuntimeEffectRegistry",
            "org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPURuntimeEffectDescriptor",
            "org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPURuntimeEffectID",
            "org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPURuntimeEffectDescriptorVersion",
            "org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPURuntimeEffectUniformSchema",
            "org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPURuntimeEffectUniformBlockPlan",
            "org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPURuntimeEffectChildSlotPlan",
            "org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPURuntimeEffectResourcePlan",
            "org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPURuntimeEffectWGSLPlan",
            "org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPURuntimeEffectCPUOracle",
            "org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPURuntimeEffectRouteContract",
            "org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPURuntimeEffectLiveEditPlan",
            "org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPURuntimeEffectUsageSet",
            "org.graphiks.kanvas.gpu.renderer.runtimeeffects.GPURuntimeEffectDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.geometry.GPUShapeDescriptor",
            "org.graphiks.kanvas.gpu.renderer.geometry.GPUPathDescriptor",
            "org.graphiks.kanvas.gpu.renderer.geometry.GPUStrokeDescriptor",
            "org.graphiks.kanvas.gpu.renderer.geometry.GPUGeometryPlan",
            "org.graphiks.kanvas.gpu.renderer.geometry.GPUGeometryRoute",
            "org.graphiks.kanvas.gpu.renderer.geometry.GPUPathBoundsPlan",
            "org.graphiks.kanvas.gpu.renderer.geometry.GPUStrokeExpansionPlan",
            "org.graphiks.kanvas.gpu.renderer.geometry.GPUStencilCoverPlan",
            "org.graphiks.kanvas.gpu.renderer.geometry.GPUPreparedGeometryPlan",
            "org.graphiks.kanvas.gpu.renderer.geometry.GPUGeometryRenderStepPlan",
            "org.graphiks.kanvas.gpu.renderer.geometry.GPUPathAtlasPlan",
            "org.graphiks.kanvas.gpu.renderer.geometry.GPUCoverageAtlasPlan",
            "org.graphiks.kanvas.gpu.renderer.geometry.GPUAtlasPolicy",
            "org.graphiks.kanvas.gpu.renderer.geometry.GPUAtlasBudgetPolicy",
            "org.graphiks.kanvas.gpu.renderer.geometry.GPUAtlasEntryRef",
            "org.graphiks.kanvas.gpu.renderer.geometry.GPUAtlasMutationPlan",
            "org.graphiks.kanvas.gpu.renderer.geometry.PathAtlasArtifact",
            "org.graphiks.kanvas.gpu.renderer.geometry.CoverageMaskArtifact",
            "org.graphiks.kanvas.gpu.renderer.geometry.PrecomputedGeometryArtifact",
            "org.graphiks.kanvas.gpu.renderer.geometry.GPUGeometryDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.vertices.GPUVerticesDescriptor",
            "org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexLayoutPlan",
            "org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexColorPlan",
            "org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexTexCoordPlan",
            "org.graphiks.kanvas.gpu.renderer.vertices.GPUPrimitiveBlendPlan",
            "org.graphiks.kanvas.gpu.renderer.vertices.GPUIndexBufferPlan",
            "org.graphiks.kanvas.gpu.renderer.vertices.GPUVertexBufferPlan",
            "org.graphiks.kanvas.gpu.renderer.vertices.GPUVerticesRoute",
            "org.graphiks.kanvas.gpu.renderer.vertices.GPUVerticesRenderStepPlan",
            "org.graphiks.kanvas.gpu.renderer.vertices.GPUMeshDescriptor",
            "org.graphiks.kanvas.gpu.renderer.vertices.GPUVerticesDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.clips.GPUClipStackDescriptor",
            "org.graphiks.kanvas.gpu.renderer.clips.GPUClipElementPlan",
            "org.graphiks.kanvas.gpu.renderer.clips.GPUClipPlan",
            "org.graphiks.kanvas.gpu.renderer.clips.GPUClipBoundsPlan",
            "org.graphiks.kanvas.gpu.renderer.clips.GPUClipScissorPlan",
            "org.graphiks.kanvas.gpu.renderer.clips.GPUClipAnalyticPlan",
            "org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilPlan",
            "org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskPlan",
            "org.graphiks.kanvas.gpu.renderer.clips.GPUClipShaderPlan",
            "org.graphiks.kanvas.gpu.renderer.clips.GPUClipOrderingToken",
            "org.graphiks.kanvas.gpu.renderer.clips.GPUClipDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadRequirement",
            "org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadPlan",
            "org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategy",
            "org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadBounds",
            "org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadBinding",
            "org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadToken",
            "org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.layers.GPULayerPlan",
            "org.graphiks.kanvas.gpu.renderer.layers.GPULayerExecutionPlan",
            "org.graphiks.kanvas.gpu.renderer.layers.GPULayerScopeID",
            "org.graphiks.kanvas.gpu.renderer.layers.GPULayerSaveRecord",
            "org.graphiks.kanvas.gpu.renderer.layers.GPULayerRestorePlan",
            "org.graphiks.kanvas.gpu.renderer.layers.GPULayerBoundsPlan",
            "org.graphiks.kanvas.gpu.renderer.layers.GPULayerTargetPlan",
            "org.graphiks.kanvas.gpu.renderer.layers.GPULayerInitializationPlan",
            "org.graphiks.kanvas.gpu.renderer.layers.GPULayerBackdropPlan",
            "org.graphiks.kanvas.gpu.renderer.layers.GPULayerSourcePlan",
            "org.graphiks.kanvas.gpu.renderer.layers.GPULayerFilterChainPlan",
            "org.graphiks.kanvas.gpu.renderer.layers.GPULayerCompositePlan",
            "org.graphiks.kanvas.gpu.renderer.layers.GPULayerElisionPlan",
            "org.graphiks.kanvas.gpu.renderer.layers.GPULayerTaskPlan",
            "org.graphiks.kanvas.gpu.renderer.layers.GPULayerResourcePlan",
            "org.graphiks.kanvas.gpu.renderer.layers.GPULayerOrderingToken",
            "org.graphiks.kanvas.gpu.renderer.layers.GPULayerCachePlan",
            "org.graphiks.kanvas.gpu.renderer.layers.GPULayerBudgetPolicy",
            "org.graphiks.kanvas.gpu.renderer.layers.GPUDrawLayer",
            "org.graphiks.kanvas.gpu.renderer.layers.GPUDrawLayerPlanner",
            "org.graphiks.kanvas.gpu.renderer.layers.GPULayerDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterPlan",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterGraphDescriptor",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterNodeID",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterNodeDescriptor",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterNodePlan",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterNodeRoute",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterInputPlan",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterSourcePlan",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterBackdropPlan",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterBoundsPlan",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterCropPlan",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterTilePlan",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterSamplingPlan",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterIntermediatePlan",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterRenderNodePlan",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterComputeNodePlan",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterKernelPlan",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterRuntimeEffectPlan",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterColorPlan",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterOrderingToken",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterCachePlan",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterBudgetPolicy",
            "org.graphiks.kanvas.gpu.renderer.filters.FilterIntermediateArtifact",
            "org.graphiks.kanvas.gpu.renderer.filters.GPUFilterDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.images.GPUImageSourceDescriptor",
            "org.graphiks.kanvas.gpu.renderer.images.GPUImagePipelinePlan",
            "org.graphiks.kanvas.gpu.renderer.images.GPUEncodedImageSource",
            "org.graphiks.kanvas.gpu.renderer.images.GPUImageCodecRegistry",
            "org.graphiks.kanvas.gpu.renderer.images.GPUImageCodecDescriptor",
            "org.graphiks.kanvas.gpu.renderer.images.GPUImageDecodeRequest",
            "org.graphiks.kanvas.gpu.renderer.images.GPUImageDecodePlan",
            "org.graphiks.kanvas.gpu.renderer.images.GPUImageDecodeResult",
            "org.graphiks.kanvas.gpu.renderer.images.GPUAnimatedImagePlan",
            "org.graphiks.kanvas.gpu.renderer.images.GPUImageFrameInfo",
            "org.graphiks.kanvas.gpu.renderer.images.GPUImageFrameSelection",
            "org.graphiks.kanvas.gpu.renderer.images.GPUImageColorDecodePlan",
            "org.graphiks.kanvas.gpu.renderer.images.GPUImageOrientationPlan",
            "org.graphiks.kanvas.gpu.renderer.images.GPUImagePixelPlan",
            "org.graphiks.kanvas.gpu.renderer.images.GPUImageMipmapPlan",
            "org.graphiks.kanvas.gpu.renderer.images.GPUImageUploadPlan",
            "org.graphiks.kanvas.gpu.renderer.images.GPUImageUploadArtifactKey",
            "org.graphiks.kanvas.gpu.renderer.images.UploadedTextureArtifact",
            "org.graphiks.kanvas.gpu.renderer.images.GPUImageDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.text.GPUTextRunPlan",
            "org.graphiks.kanvas.gpu.renderer.text.GPUTextSubRunPlan",
            "org.graphiks.kanvas.gpu.renderer.text.GPUTextRoute",
            "org.graphiks.kanvas.gpu.renderer.text.GPUTextRenderStep",
            "org.graphiks.kanvas.gpu.renderer.text.GPUTextAtlasPlan",
            "org.graphiks.kanvas.gpu.renderer.text.GPUTextBinding",
            "org.graphiks.kanvas.gpu.renderer.text.GPUTextInstancePlan",
            "org.graphiks.kanvas.gpu.renderer.text.GPUTextSDFParams",
            "org.graphiks.kanvas.gpu.renderer.text.GPUTextOrderingToken",
            "org.graphiks.kanvas.gpu.renderer.text.GlyphAtlasArtifact",
            "org.graphiks.kanvas.gpu.renderer.text.SDFGlyphAtlasArtifact",
            "org.graphiks.kanvas.gpu.renderer.text.GlyphUploadPlan",
            "org.graphiks.kanvas.gpu.renderer.text.OutlineGlyphPlan",
            "org.graphiks.kanvas.gpu.renderer.text.ColorGlyphPlan",
            "org.graphiks.kanvas.gpu.renderer.text.BitmapGlyphPlan",
            "org.graphiks.kanvas.gpu.renderer.text.SVGGlyphPlan",
            "org.graphiks.kanvas.gpu.renderer.text.GPUTextDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.wgsl.WGSLFragment",
            "org.graphiks.kanvas.gpu.renderer.wgsl.WGSLModule",
            "org.graphiks.kanvas.gpu.renderer.wgsl.WGSLComputeModule",
            "org.graphiks.kanvas.gpu.renderer.wgsl.WGSLModuleHash",
            "org.graphiks.kanvas.gpu.renderer.wgsl.WGSLReflectionResult",
            "org.graphiks.kanvas.gpu.renderer.wgsl.WGSLBindingLayout",
            "org.graphiks.kanvas.gpu.renderer.wgsl.WGSLUniformLayout",
            "org.graphiks.kanvas.gpu.renderer.wgsl.WGSLStorageLayout",
            "org.graphiks.kanvas.gpu.renderer.wgsl.WGSLResourceBindingPlan",
            "org.graphiks.kanvas.gpu.renderer.wgsl.WGSLPackingPlan",
            "org.graphiks.kanvas.gpu.renderer.wgsl.WGSLValidationDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadGatherer",
            "org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadGatherPlan",
            "org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadWritePlan",
            "org.graphiks.kanvas.gpu.renderer.payloads.GPUMaterialPayload",
            "org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID",
            "org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadBlock",
            "org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot",
            "org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingBlock",
            "org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot",
            "org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadBindingPlan",
            "org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadUploadPlan",
            "org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint",
            "org.graphiks.kanvas.gpu.renderer.payloads.GPUGradientPayloadStore",
            "org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawPayloadRef",
            "org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey",
            "org.graphiks.kanvas.gpu.renderer.pipelines.GPUComputeProgramKey",
            "org.graphiks.kanvas.gpu.renderer.pipelines.GPUComputePipelineKey",
            "org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineKeyPreimage",
            "org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineCacheKey",
            "org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineCreationPlan",
            "org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.routing.GPURouteKind",
            "org.graphiks.kanvas.gpu.renderer.routing.GPUNativeRoute",
            "org.graphiks.kanvas.gpu.renderer.routing.CPUPreparedGPURoute",
            "org.graphiks.kanvas.gpu.renderer.routing.CPUReferenceOnlyRoute",
            "org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.routing.GPURouteDecision",
            "org.graphiks.kanvas.gpu.renderer.routing.GPURoutePreimage",
            "org.graphiks.kanvas.gpu.renderer.routing.CPUPreparedGPUArtifactRegistry",
            "org.graphiks.kanvas.gpu.renderer.routing.CPUPreparedGPUArtifactDescriptor",
            "org.graphiks.kanvas.gpu.renderer.routing.CPUPreparedGPUArtifactKey",
            "org.graphiks.kanvas.gpu.renderer.routing.GPURouteDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.analysis.GPUDrawAnalysis",
            "org.graphiks.kanvas.gpu.renderer.analysis.GPUDrawAnalysisRecord",
            "org.graphiks.kanvas.gpu.renderer.analysis.GPUDrawAnalysisDecision",
            "org.graphiks.kanvas.gpu.renderer.analysis.GPUOcclusionTracker",
            "org.graphiks.kanvas.gpu.renderer.analysis.GPUOcclusionProof",
            "org.graphiks.kanvas.gpu.renderer.analysis.SortKey",
            "org.graphiks.kanvas.gpu.renderer.analysis.GPUAnalysisDependency",
            "org.graphiks.kanvas.gpu.renderer.analysis.GPUAnalysisDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.recording.GPURecorder",
            "org.graphiks.kanvas.gpu.renderer.recording.GPURecording",
            "org.graphiks.kanvas.gpu.renderer.recording.GPURecordingCompatibilityKey",
            "org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID",
            "org.graphiks.kanvas.gpu.renderer.recording.GPUOrderedRecording",
            "org.graphiks.kanvas.gpu.renderer.recording.GPUSharedScope",
            "org.graphiks.kanvas.gpu.renderer.recording.GPURecorderScope",
            "org.graphiks.kanvas.gpu.renderer.recording.GPUFrameScope",
            "org.graphiks.kanvas.gpu.renderer.recording.GPUAtlasScope",
            "org.graphiks.kanvas.gpu.renderer.recording.GPUTask",
            "org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList",
            "org.graphiks.kanvas.gpu.renderer.recording.GPUTaskDependency",
            "org.graphiks.kanvas.gpu.renderer.recording.GPURecordingDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.passes.GPUDrawInvocation",
            "org.graphiks.kanvas.gpu.renderer.passes.GPUDrawInsertion",
            "org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPass",
            "org.graphiks.kanvas.gpu.renderer.passes.GPURenderStep",
            "org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID",
            "org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepPlan",
            "org.graphiks.kanvas.gpu.renderer.passes.GPUComputeTask",
            "org.graphiks.kanvas.gpu.renderer.passes.GPUCopyTask",
            "org.graphiks.kanvas.gpu.renderer.passes.GPUUploadTask",
            "org.graphiks.kanvas.gpu.renderer.passes.GPUSortWindow",
            "org.graphiks.kanvas.gpu.renderer.passes.GPUPassDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.resources.GPUResourceProvider",
            "org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision",
            "org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext",
            "org.graphiks.kanvas.gpu.renderer.resources.GPUTextureDescriptor",
            "org.graphiks.kanvas.gpu.renderer.resources.GPUTextureViewDescriptor",
            "org.graphiks.kanvas.gpu.renderer.resources.GPUSamplerDescriptor",
            "org.graphiks.kanvas.gpu.renderer.resources.GPUTextureOwnershipPlan",
            "org.graphiks.kanvas.gpu.renderer.resources.GPUTextureAllocationPlan",
            "org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef",
            "org.graphiks.kanvas.gpu.renderer.resources.GPUImportedTextureDescriptor",
            "org.graphiks.kanvas.gpu.renderer.resources.GPUSurfaceTextureLease",
            "org.graphiks.kanvas.gpu.renderer.resources.GPUSampledTextureBinding",
            "org.graphiks.kanvas.gpu.renderer.resources.GPULazyResourcePlan",
            "org.graphiks.kanvas.gpu.renderer.resources.GPUPromiseResourcePlan",
            "org.graphiks.kanvas.gpu.renderer.resources.GPUImportedResourcePlan",
            "org.graphiks.kanvas.gpu.renderer.resources.GPUVolatileResourcePlan",
            "org.graphiks.kanvas.gpu.renderer.resources.GPUUseToken",
            "org.graphiks.kanvas.gpu.renderer.resources.GPUPendingReadToken",
            "org.graphiks.kanvas.gpu.renderer.resources.GPUPendingWriteToken",
            "org.graphiks.kanvas.gpu.renderer.resources.GPUScratchResourceToken",
            "org.graphiks.kanvas.gpu.renderer.resources.GPUIntermediateResourceToken",
            "org.graphiks.kanvas.gpu.renderer.resources.GPUTextureDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.resources.GPUResourceDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionContext",
            "org.graphiks.kanvas.gpu.renderer.execution.GPUCommandScope",
            "org.graphiks.kanvas.gpu.renderer.execution.GPUCommandSubmission",
            "org.graphiks.kanvas.gpu.renderer.execution.GPUSurfaceTarget",
            "org.graphiks.kanvas.gpu.renderer.execution.GPUSurfaceTargetDescriptor",
            "org.graphiks.kanvas.gpu.renderer.execution.GPUFrameSubmission",
            "org.graphiks.kanvas.gpu.renderer.execution.GPUReadbackRequest",
            "org.graphiks.kanvas.gpu.renderer.execution.GPUReadbackResult",
            "org.graphiks.kanvas.gpu.renderer.execution.GPUDeviceGeneration",
            "org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionDiagnostic",
            "org.graphiks.kanvas.gpu.renderer.validation.GPUValidationFixture",
            "org.graphiks.kanvas.gpu.renderer.validation.GPUValidationReport",
            "org.graphiks.kanvas.gpu.renderer.validation.GPUContractDump",
            "org.graphiks.kanvas.gpu.renderer.validation.GPUKeyPreimageDump",
            "org.graphiks.kanvas.gpu.renderer.validation.GPUWGSLReflectionDump",
            "org.graphiks.kanvas.gpu.renderer.validation.GPUPackageBoundaryCheck",
            "org.graphiks.kanvas.gpu.renderer.validation.GPUForbiddenImportCheck",
            "org.graphiks.kanvas.gpu.renderer.validation.GPUPromotionGateCheck",
        )
    }
}
