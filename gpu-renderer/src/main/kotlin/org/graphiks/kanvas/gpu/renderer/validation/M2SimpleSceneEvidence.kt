package org.graphiks.kanvas.gpu.renderer.validation

import java.util.Locale
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipDiagnostic
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipElementPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipOrderingToken
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipScissorPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStackDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectCornerRadii
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.materials.GPUGradientGeometryPlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUGradientKind
import org.graphiks.kanvas.gpu.renderer.materials.GPUGradientPlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUGradientStopPlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUGradientStopStorePlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourceDescriptor
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourceDiagnostic
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourceKind
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialSourcePlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialTileMode
import org.graphiks.kanvas.gpu.renderer.materials.GPUPaintDescriptor
import org.graphiks.kanvas.gpu.renderer.materials.GPUPaintEvaluationOrder
import org.graphiks.kanvas.gpu.renderer.materials.GPUPaintPipelinePlan
import org.graphiks.kanvas.gpu.renderer.materials.GPUPaintStagePlan
import org.graphiks.kanvas.gpu.renderer.materials.MaterialKey
import org.graphiks.kanvas.gpu.renderer.materials.WGSLSnippetID
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLBindingLayout
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLFacadeCapabilities
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLFragment
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLModule
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLModuleAssembler
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLModuleAssemblyInput
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLModuleAssemblyResult
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLPackingPlan
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLParserState
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLReflectionResult
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLUniformFieldLayout
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLUniformLayout

/**
 * Deterministic M2 closeout scene evidence.
 *
 * This is a validation fixture, not a product route. It ties M2 rounded-rect,
 * linear-gradient material, device-scissor clip, and batching contracts into a
 * single dump while keeping the adapter-backed GPU lane explicitly skipped.
 */
internal class M2SimpleSceneEvidence private constructor(
    private val rrect: GPURRect,
    private val paintPlan: GPUPaintPipelinePlan,
    private val wgslModule: WGSLModule,
    private val clipPlan: GPUClipPlan,
    private val refusedGradient: GPUMaterialSourcePlan.Refused,
    private val refusedClip: GPUClipElementPlan.Refused,
    private val batch: M2BatchEvidence,
) {
    /** Emits stable evidence lines consumed by tickets and reports. */
    fun dumpLines(): List<String> {
        val gradientPlan = (paintPlan.paint.source as GPUMaterialSourceDescriptor.Gradient).plan
        val materialStage = paintPlan.stages.single() as GPUPaintStagePlan.Material
        val materialSource = materialStage.sourcePlan as GPUMaterialSourcePlan.Accepted
        val scissor = (clipPlan.elements.single() as GPUClipElementPlan.Scissor).plan
        val reflectionSource = when (val reflection = wgslModule.reflection) {
            is WGSLReflectionResult.Accepted -> reflection.reflectionSource
            is WGSLReflectionResult.Rejected -> "rejected"
        }

        return buildList {
            add("scene:m2.simple.rrect-gradient-scissor-batch mode=contract-fixture")
            add(
                "rrect:accepted routeCandidate=native.fill_rrect.solid " +
                    "geometry=${rrect.geometryDump()}",
            )
            add(
                "gradient:accepted materialKey=${paintPlan.materialKey.value} " +
                    "payload=${materialSource.payloadPlanHash} " +
                    "stops=${gradientPlan.stops.size} " +
                    "tileMode=${gradientPlan.tileMode.dumpName()}",
            )
            add(
                "wgsl:accepted module=${wgslModule.moduleLabel} " +
                    "parser=${wgslModule.parserState.status}:${wgslModule.parserState.toolName} " +
                    "reflection=$reflectionSource",
            )
            add(
                "clip:accepted stack=${clipPlan.stack.stackId} element=scissor " +
                    "rect=${scissor.x.dumpFloat()},${scissor.y.dumpFloat()}," +
                    "${scissor.width.dumpFloat()},${scissor.height.dumpFloat()} mode=intersect",
            )
            add(
                "batch:accepted key=${batch.acceptedKey} draws=${batch.acceptedDrawCount} " +
                    "boundaries=${batch.boundaries.joinToString(",")}",
            )
            batch.splits.forEach { split ->
                add("batch:split reason=${split.reason} before=${split.beforeDraw} after=${split.afterDraw}")
            }
            add(
                "gradient:refused reason=${refusedGradient.diagnostic.code} source=mirror " +
                    "requiredGate=tile-mode-wgsl-reference-evidence",
            )
            add(
                "clip:refused reason=${refusedClip.diagnostic.code} " +
                    "requiredGate=clip-stencil-mask-evidence",
            )
            add("nonclaim:paths-images-text-filters-saveLayer-complex-clips-not-routed")
            add(
                "gpu-lane:explicit-skipped reason=adapter-backed-webgpu-evidence-not-run " +
                    "productRouteActivated=false releaseBlocking=false readinessDelta=0.0",
            )
        }
    }

    /** Factory for the canonical M2 simple closeout scene. */
    companion object {
        /** Builds the deterministic scene evidence. */
        fun build(): M2SimpleSceneEvidence =
            M2SimpleSceneEvidence(
                rrect = buildRRect(),
                paintPlan = buildGradientPaintPlan(),
                wgslModule = buildGradientModule(),
                clipPlan = buildClipPlan(),
                refusedGradient = buildRefusedGradient(),
                refusedClip = buildRefusedClip(),
                batch = M2BatchEvidence(
                    acceptedKey = "batch:rrect.linear-gradient.scissor",
                    acceptedDrawCount = 2,
                    boundaries = listOf("materialKey", "clipStack", "layer", "ordering"),
                    splits = listOf(
                        M2BatchSplit(
                            reason = "material-key-mismatch",
                            beforeDraw = "draw-2",
                            afterDraw = "draw-3",
                        ),
                        M2BatchSplit(
                            reason = "clip-stack-mismatch",
                            beforeDraw = "draw-3",
                            afterDraw = "draw-4",
                        ),
                        M2BatchSplit(
                            reason = "layer-or-ordering-boundary",
                            beforeDraw = "draw-4",
                            afterDraw = "draw-5",
                        ),
                    ),
                ),
            )
    }
}

/** Conservative batching fixture for the M2 scene. */
private data class M2BatchEvidence(
    val acceptedKey: String,
    val acceptedDrawCount: Int,
    val boundaries: List<String>,
    val splits: List<M2BatchSplit>,
)

/** Split evidence for a conservative batching boundary. */
private data class M2BatchSplit(
    val reason: String,
    val beforeDraw: String,
    val afterDraw: String,
)

private fun buildRRect(): GPURRect =
    GPURRect(
        rect = GPURect(left = 4f, top = 8f, right = 92f, bottom = 56f),
        topLeft = GPURRectCornerRadii(x = 6f, y = 6f),
        topRight = GPURRectCornerRadii(x = 10f, y = 10f),
        bottomRight = GPURRectCornerRadii(x = 14f, y = 14f),
        bottomLeft = GPURRectCornerRadii(x = 4f, y = 4f),
    )

private fun buildGradientPaintPlan(): GPUPaintPipelinePlan {
    val gradient = GPUGradientPlan(
        geometry = GPUGradientGeometryPlan(
            kind = GPUGradientKind.Linear,
            controlPoints = listOf(4f, 8f, 92f, 56f),
            localMatrixHash = null,
        ),
        stops = listOf(
            GPUGradientStopPlan(offset = 0f, colorLabel = "rgba(0.12,0.36,0.90,1.00)"),
            GPUGradientStopPlan(offset = 1f, colorLabel = "rgba(0.95,0.44,0.20,1.00)"),
        ),
        stopStore = GPUGradientStopStorePlan(
            stopCount = 2,
            storageKind = "inline-uniform",
            payloadHash = "gradient.inline2",
        ),
        tileMode = GPUMaterialTileMode.Clamp,
    )
    val source = GPUMaterialSourceDescriptor.Gradient(gradient)
    val acceptedSource = GPUMaterialSourcePlan.Accepted(
        source = source,
        snippetId = WGSLSnippetID("snippet:linear-gradient.clamp.inline2"),
        payloadPlanHash = "gradient.inline2",
    )

    return GPUPaintPipelinePlan(
        paint = GPUPaintDescriptor(
            paintId = "m2-simple-linear-gradient",
            source = source,
            blendModeLabel = "src_over",
            alpha = 1f,
            colorSpaceLabel = "srgb-fixture",
        ),
        evaluationOrder = GPUPaintEvaluationOrder.SourceThenCoverage,
        stages = listOf(GPUPaintStagePlan.Material(acceptedSource)),
        materialKey = MaterialKey("material:linear-gradient.clamp.inline2"),
    )
}

private fun buildGradientModule(): WGSLModule {
    val layout = WGSLUniformLayout(
        layoutHash = "layout:linear-gradient-inline2:v1",
        fields = listOf("start", "end", "color0", "color1"),
        fieldLayouts = listOf(
            WGSLUniformFieldLayout(name = "start", type = "vec2<f32>", offset = 0, sizeBytes = 8, alignment = 8),
            WGSLUniformFieldLayout(name = "end", type = "vec2<f32>", offset = 8, sizeBytes = 8, alignment = 8),
            WGSLUniformFieldLayout(name = "color0", type = "vec4<f32>", offset = 16, sizeBytes = 16, alignment = 16),
            WGSLUniformFieldLayout(name = "color1", type = "vec4<f32>", offset = 32, sizeBytes = 16, alignment = 16),
        ),
        sizeBytes = 48,
        alignment = 16,
        numericRepresentation = "f32",
    )
    val binding = WGSLBindingLayout(
        group = 1,
        binding = 0,
        visibility = setOf("fragment"),
        resourceKind = "uniform-buffer",
        access = "read",
        minBindingSize = 48,
        dynamicOffset = false,
        layoutRole = "material-gradient",
        diagnosticLabel = "m2.simple.linear-gradient.material",
    )
    val packing = WGSLPackingPlan(
        planHash = "packing:linear-gradient-inline2:v1",
        layoutHash = layout.layoutHash,
        fieldOrder = layout.fields,
        offsets = mapOf(
            "start" to 0,
            "end" to 8,
            "color0" to 16,
            "color1" to 32,
        ),
        paddingBytes = 0,
        dynamicOffsetAlignment = 256,
    )
    val fragment = WGSLFragment(
        fragmentId = "fragment:m2-linear-gradient-rrect",
        stage = "fragment",
        sourceHash = "source:m2-linear-gradient-rrect:v1",
        entryPoints = listOf("vs_main", "fs_main"),
        bindingLayouts = emptyList(),
        uniformLayouts = listOf(layout),
        storageLayouts = emptyList(),
        requiredFeatures = emptyList(),
        diagnosticLabel = "m2.simple.linear-gradient.rrect",
    )

    return when (
        val result = WGSLModuleAssembler.assembleRenderModule(
            WGSLModuleAssemblyInput(
                moduleLabel = "m2-simple-rrect-linear-gradient",
                moduleSalt = "m2-simple-scene-v1",
                vertexEntryPoint = "vs_main",
                fragmentEntryPoint = "fs_main",
                fragments = listOf(fragment),
                bindings = listOf(binding),
                uniformLayouts = listOf(layout),
                packingPlans = listOf(packing),
                parserState = WGSLParserState.unavailable(
                    toolName = "wgsl4k",
                    message = "M2 simple scene uses fixture-declared reflection; parser-backed evidence remains a gate.",
                ),
                capabilities = WGSLFacadeCapabilities(supportedFeatures = emptySet()),
            ),
        )
    ) {
        is WGSLModuleAssemblyResult.Accepted -> result.module
        is WGSLModuleAssemblyResult.Rejected -> error(
            "M2 simple scene WGSL fixture rejected: " +
                result.diagnostics.joinToString { diagnostic -> diagnostic.code },
        )
    }
}

private fun buildClipPlan(): GPUClipPlan =
    GPUClipPlan(
        stack = GPUClipStackDescriptor(
            stackId = "m2-simple-device-scissor",
            stateLabel = "single-device-rect",
            boundsLabel = "device[0,0,96,64]",
            activeElementCount = 1,
            generation = 1,
            provenance = "m2-simple-scene",
        ),
        elements = listOf(
            GPUClipElementPlan.Scissor(
                GPUClipScissorPlan(x = 0, y = 0, width = 96, height = 64),
            ),
        ),
        orderingToken = GPUClipOrderingToken("clip-order:m2-simple-device-scissor"),
    )

private fun buildRefusedGradient(): GPUMaterialSourcePlan.Refused =
    GPUMaterialSourcePlan.Refused(
        GPUMaterialSourceDiagnostic(
            code = "unsupported.gradient.tile_mode",
            sourceKind = GPUMaterialSourceKind.Gradient,
            message = "Mirror tile mode needs WGSL reference evidence before M2 can claim support.",
            terminal = true,
        ),
    )

private fun buildRefusedClip(): GPUClipElementPlan.Refused =
    GPUClipElementPlan.Refused(
        GPUClipDiagnostic(
            code = "unsupported.clip.non_device_rect",
            stackId = "m2-complex-clip",
            message = "Non-device-rect clip needs clip/stencil/mask evidence before routing.",
            terminal = true,
        ),
    )

private fun GPURRect.geometryDump(): String =
    "rrect.corner_radii=tl(${topLeft.x.dumpFloat()},${topLeft.y.dumpFloat()});" +
        "tr(${topRight.x.dumpFloat()},${topRight.y.dumpFloat()});" +
        "br(${bottomRight.x.dumpFloat()},${bottomRight.y.dumpFloat()});" +
        "bl(${bottomLeft.x.dumpFloat()},${bottomLeft.y.dumpFloat()})"

private fun GPUMaterialTileMode.dumpName(): String = name.lowercase()

private fun Int.dumpFloat(): String = toFloat().dumpFloat()

private fun Float.dumpFloat(): String = String.format(Locale.ROOT, "%.1f", this)
