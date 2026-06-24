package org.graphiks.kanvas.gpu.renderer.clips

/** Clip ordering token. */
@JvmInline
value class GPUClipOrderingToken(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUClipOrderingToken.value must not be blank" }
    }
}

/** Captured clip stack descriptor. */
data class GPUClipStackDescriptor(
    val stackId: String,
    val stateLabel: String,
    val boundsLabel: String,
    val activeElementCount: Int,
    val generation: Long,
    val provenance: String,
)

/** Captured clip element descriptor used before route selection. */
data class GPUClipElementDescriptor(
    val elementId: String,
    val sourceOrder: Int,
    val shapeKind: String,
    val operation: String,
    val shapeKey: String,
    val boundsLabel: String,
    val transformClass: String,
    val antiAliasMode: String,
    val fillRule: String,
    val inverseFill: Boolean,
    val coveragePixelEstimate: Int,
)

/** Clip element plan. */
sealed interface GPUClipElementPlan {
    /** Clip element can be ignored. */
    data class Ignore(val reasonCode: String) : GPUClipElementPlan

    /** Clip element culls the draw. */
    data class Cull(val reasonCode: String) : GPUClipElementPlan

    /** Clip element is represented as geometry. */
    data class Geometric(val geometryLabel: String) : GPUClipElementPlan

    /** Clip element is represented as a scissor. */
    data class Scissor(val plan: GPUClipScissorPlan) : GPUClipElementPlan

    /** Clip element is represented analytically. */
    data class Analytic(val plan: GPUClipAnalyticPlan) : GPUClipElementPlan

    /** Clip element is represented with stencil. */
    data class Stencil(val plan: GPUClipStencilPlan) : GPUClipElementPlan

    /** Clip element is represented with a mask artifact. */
    data class Mask(val plan: GPUClipMaskPlan) : GPUClipElementPlan

    /** Clip element is represented in shader code. */
    data class Shader(val plan: GPUClipShaderPlan) : GPUClipElementPlan

    /** Clip element is refused. */
    data class Refused(val diagnostic: GPUClipDiagnostic) : GPUClipElementPlan
}

/** Clip plan for one draw. */
data class GPUClipPlan(
    val stack: GPUClipStackDescriptor,
    val elements: List<GPUClipElementPlan>,
    val orderingToken: GPUClipOrderingToken,
    val routeKind: String = "Unclassified",
    val elementDescriptors: List<GPUClipElementDescriptor> = emptyList(),
    val diagnostics: List<GPUClipDiagnostic> = emptyList(),
)

/** Clip bounds plan. */
data class GPUClipBoundsPlan(
    val inputBoundsLabel: String,
    val reducedBoundsLabel: String,
    val conservative: Boolean,
)

/** Scissor clip plan. */
data class GPUClipScissorPlan(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/** Analytic clip plan. */
data class GPUClipAnalyticPlan(
    val shapeLabel: String,
    val coverageMode: String,
)

/** Stencil clip plan. */
data class GPUClipStencilPlan(
    val stencilLabel: String,
    val fillRule: String,
    val preserveStencil: Boolean,
)

/** Clip mask plan. */
data class GPUClipMaskPlan(
    val maskArtifactKey: String,
    val boundsLabel: String,
    val samplingPolicy: String,
    val artifactType: String = "CoverageMaskArtifact",
    val lifetimeClass: String = "recording-local",
    val budgetClass: String = "clip-bounded",
    val strategyLabel: String = "coverage-mask.standalone",
    val consumerKind: String = "clip-mask.sample",
)

/** Shader clip plan. */
data class GPUClipShaderPlan(
    val snippetHash: String,
    val payloadHash: String,
)

/** Clip diagnostic. */
data class GPUClipDiagnostic(
    val code: String,
    val stackId: String? = null,
    val message: String,
    val terminal: Boolean,
)

/** Builds bounded rrect/path clip mask contract evidence without route activation. */
class GPUBoundedClipPreparedPlanner(
    private val maxElements: Int = 2,
    private val maxMaskPixels: Int = 4096,
) {
    /**
     * Plans one bounded rrect-only intersect element as a typed CPU-prepared mask.
     *
     * Complex clips (non-intersect operations, inverse fills) remain refused.
     * Accepts single rrect elements with identity or translate transforms.
     * This is contract evidence only: it does not allocate an atlas, upload a
     * texture, submit adapter work, or make arbitrary clip-stack support available.
     */
    fun planBoundedRRectClip(
        stack: GPUClipStackDescriptor,
        element: GPUClipElementDescriptor,
    ): GPUClipPlan {
        val stackRefusal = stack.boundedRRectRefusalCode()
        val elementRefusal = element.boundedRRectRefusalCode()
        val refusalCode = stackRefusal ?: elementRefusal
        if (refusalCode != null) {
            val diagnostic = GPUClipDiagnostic(
                code = refusalCode,
                stackId = stack.stackId,
                message = "Bounded rrect clip refused: $refusalCode",
                terminal = true,
            )
            return GPUClipPlan(
                stack = stack,
                elements = listOf(GPUClipElementPlan.Refused(diagnostic)),
                orderingToken = GPUClipOrderingToken(stack.boundedRRectOrderingLabel(element)),
                routeKind = "RefuseDiagnostic",
                elementDescriptors = listOf(element),
                diagnostics = listOf(diagnostic),
            )
        }

        val maskPlan = GPUClipMaskPlan(
            maskArtifactKey = stack.boundedRRectMaskArtifactKey(element),
            boundsLabel = stack.boundsLabel,
            samplingPolicy = "nearest",
        )
        return GPUClipPlan(
            stack = stack,
            elements = listOf(GPUClipElementPlan.Mask(maskPlan)),
            orderingToken = GPUClipOrderingToken(stack.boundedRRectOrderingLabel(element)),
            routeKind = "CPUPreparedGPU",
            elementDescriptors = listOf(element),
            diagnostics = listOf(
                GPUClipDiagnostic(
                    code = "clip:rrect.prepared",
                    stackId = stack.stackId,
                    message = "Prepared bounded rrect clip mask is available for ${maskPlan.consumerKind}",
                    terminal = false,
                ),
            ),
        )
    }

    private fun GPUClipStackDescriptor.boundedRRectRefusalCode(): String? =
        when {
            stackId.isBlank() || generation < 0 -> "unsupported.clip.descriptor_invalid"
            boundsLabel.isBlank() || boundsLabel == "unbounded" -> "unsupported.clip.stack_unbounded"
            stateLabel != "Complex" -> "unsupported.clip.descriptor_invalid"
            activeElementCount != 1 -> "unsupported.clip.descriptor_invalid"
            else -> null
        }

    private fun GPUClipElementDescriptor.boundedRRectRefusalCode(): String? =
        when {
            shapeKind != "rrect" -> "unsupported.clip.shape_unsupported"
            operation != "Intersect" -> "unsupported.clip.operation"
            inverseFill -> "unsupported.clip.inverse_unaccepted"
            !shapeKey.isCanonicalClipShapeKey() -> "unsupported.clip.element_key_nondeterministic"
            !shapeKey.matchesClipShapeKind("rrect") -> "unsupported.clip.element_key_mismatch"
            boundsLabel.isBlank() || boundsLabel == "unbounded" -> "unsupported.clip.mask_bounds_invalid"
            transformClass !in setOf("identity", "translate") -> "unsupported.clip.geometric_intersection_unproven"
            antiAliasMode !in setOf("coverage-aa", "none") -> "unsupported.clip.analytic_unsupported"
            fillRule !in setOf("NonZero", "EvenOdd") -> "unsupported.clip.descriptor_invalid"
            coveragePixelEstimate <= 0 -> "unsupported.clip.mask_bounds_invalid"
            coveragePixelEstimate > maxMaskPixels -> "unsupported.clip.mask_budget_exceeded"
            else -> null
        }

    private fun GPUClipStackDescriptor.boundedRRectOrderingLabel(element: GPUClipElementDescriptor): String =
        "clip-order.${stackId.sanitizeForClipKey()}.gen$generation.rrect"

    private fun GPUClipStackDescriptor.boundedRRectMaskArtifactKey(element: GPUClipElementDescriptor): String =
        "coverage.clip.${stackId.sanitizeForClipKey()}.gen$generation.rrect." +
            "${element.contentKeySegment()}"

    /**
     * Plans one bounded rrect/path intersect stack as a typed CPU-prepared mask.
     *
     * This is contract evidence only: it names a future GPU mask consumer and
     * ordering token, but it does not allocate an atlas, upload a texture,
     * submit adapter work, or make arbitrary clip-stack support available.
     */
    fun plan(
        stack: GPUClipStackDescriptor,
        elements: List<GPUClipElementDescriptor>,
    ): GPUClipPlan {
        val refusalCode = stack.refusalCode(elements) ?: elements.refusalCode()
        if (refusalCode != null) {
            val diagnostic = GPUClipDiagnostic(
                code = refusalCode,
                stackId = stack.stackId,
                message = "Bounded prepared clip refused: $refusalCode",
                terminal = true,
            )
            return GPUClipPlan(
                stack = stack,
                elements = listOf(GPUClipElementPlan.Refused(diagnostic)),
                orderingToken = GPUClipOrderingToken(stack.orderingTokenLabel(elements)),
                routeKind = "RefuseDiagnostic",
                elementDescriptors = elements.sortedBy { element -> element.sourceOrder },
                diagnostics = listOf(diagnostic),
            )
        }

        val orderedElements = elements.sortedBy { element -> element.sourceOrder }
        val maskPlan = GPUClipMaskPlan(
            maskArtifactKey = stack.maskArtifactKey(orderedElements),
            boundsLabel = stack.boundsLabel,
            samplingPolicy = "nearest",
        )
        return GPUClipPlan(
            stack = stack,
            elements = listOf(GPUClipElementPlan.Mask(maskPlan)),
            orderingToken = GPUClipOrderingToken(stack.orderingTokenLabel(orderedElements)),
            routeKind = "CPUPreparedGPU",
            elementDescriptors = orderedElements,
            diagnostics = listOf(
                GPUClipDiagnostic(
                    code = "clip:rrect-path.prepared",
                    stackId = stack.stackId,
                    message = "Prepared bounded clip mask is available for ${maskPlan.consumerKind}",
                    terminal = false,
                ),
            ),
        )
    }

    private fun GPUClipStackDescriptor.refusalCode(elements: List<GPUClipElementDescriptor>): String? =
        when {
            stackId.isBlank() || generation < 0 -> "unsupported.clip.descriptor_invalid"
            boundsLabel.isBlank() || boundsLabel == "unbounded" -> "unsupported.clip.stack_unbounded"
            stateLabel != "Complex" -> "unsupported.clip.descriptor_invalid"
            activeElementCount != elements.size -> "unsupported.clip.descriptor_invalid"
            elements.size != maxElements -> "unsupported.clip.stack_too_deep"
            else -> null
        }

    private fun List<GPUClipElementDescriptor>.refusalCode(): String? =
        when {
            any { element -> element.operation != "Intersect" } -> "unsupported.clip.operation"
            any { element -> element.inverseFill } -> "unsupported.clip.inverse_unaccepted"
            any { element -> element.shapeKind == "shader" } -> "unsupported.clip.shader_unregistered"
            sumOf { element -> element.coveragePixelEstimate } > maxMaskPixels -> "unsupported.clip.mask_budget_exceeded"
            any { element -> !element.shapeKey.isCanonicalClipShapeKey() } ->
                "unsupported.clip.element_key_nondeterministic"
            any { element -> !element.shapeKey.matchesClipShapeKind(element.shapeKind) } ->
                "unsupported.clip.element_key_mismatch"
            map { element -> element.shapeKind }.toSet() != setOf("rrect", "path") -> "unsupported.clip.shape_unsupported"
            any { element -> element.boundsLabel.isBlank() || element.boundsLabel == "unbounded" } ->
                "unsupported.clip.mask_bounds_invalid"
            any { element -> element.transformClass !in setOf("identity", "translate") } ->
                "unsupported.clip.geometric_intersection_unproven"
            any { element -> element.antiAliasMode !in setOf("coverage-aa", "none") } ->
                "unsupported.clip.analytic_unsupported"
            any { element -> element.fillRule !in setOf("NonZero", "EvenOdd") } ->
                "unsupported.clip.descriptor_invalid"
            any { element -> element.coveragePixelEstimate <= 0 } -> "unsupported.clip.mask_bounds_invalid"
            else -> null
        }

    private fun GPUClipStackDescriptor.orderingTokenLabel(elements: List<GPUClipElementDescriptor>): String =
        "clip-order.${stackId.sanitizeForClipKey()}.gen$generation.elements${elements.size}"

    private fun GPUClipStackDescriptor.maskArtifactKey(elements: List<GPUClipElementDescriptor>): String =
        "coverage.clip.${stackId.sanitizeForClipKey()}.gen$generation." +
            "${contentKeySegment()}_${elements.shapeKeySegment()}.elements${elements.size}"
}

/** Emits stable M3 bounded clip evidence lines for reports and tests. */
fun GPUClipPlan.dumpLines(): List<String> {
    val nonClaim = clipNonClaimLine
    if (routeKind == "RefuseDiagnostic") {
        val diagnostic = diagnostics.firstOrNull()
        return listOf(
            "clip:refused reason=${diagnostic?.code ?: "unknown"} stack=${stack.stackId} routeKind=RefuseDiagnostic",
            nonClaim,
        )
    }

    val maskPlan = elements.mapNotNull { element ->
        (element as? GPUClipElementPlan.Mask)?.plan
    }.singleOrNull()

    return if (routeKind == "CPUPreparedGPU" && maskPlan != null) {
        buildList {
            add(
                "clip:prepared routeKind=CPUPreparedGPU strategy=${maskPlan.strategyLabel} " +
                    "consumer=${maskPlan.consumerKind} ordering=${orderingToken.value}",
            )
            add(
                "clip:stack id=${stack.stackId} state=${stack.stateLabel} bounds=${stack.boundsLabel} " +
                    "generation=${stack.generation} elements=${stack.activeElementCount} provenance=${stack.provenance}",
            )
            elementDescriptors.forEach { element ->
                add(element.dumpLine())
            }
            add(
                "clip:mask artifact=${maskPlan.maskArtifactKey} type=${maskPlan.artifactType} " +
                    "lifetime=${maskPlan.lifetimeClass} budget=${maskPlan.budgetClass} " +
                    "bounds=${maskPlan.boundsLabel} sampling=${maskPlan.samplingPolicy} atlasPolicy=NoAtlas",
            )
            add(nonClaim)
        }
    } else {
        listOf(
            "clip:unsupported-dump routeKind=$routeKind stack=${stack.stackId}",
            nonClaim,
        )
    }
}

private fun GPUClipElementDescriptor.dumpLine(): String =
    "clip:element order=$sourceOrder id=$elementId shape=$shapeKind operation=$operation " +
        "key=$shapeKey transform=$transformClass aa=$antiAliasMode bounds=$boundsLabel " +
        "inverse=$inverseFill fillRule=$fillRule"

private fun List<GPUClipElementDescriptor>.shapeKeySegment(): String =
    sortedBy { element -> element.sourceOrder }
        .joinToString("_") { element -> element.contentKeySegment() }

private fun GPUClipStackDescriptor.contentKeySegment(): String =
    listOf(
        "stack",
        stackId,
        stateLabel,
        boundsLabel,
        activeElementCount.toString(),
        generation.toString(),
    ).joinToString(".") { value -> value.encodeForClipKey() }

private fun GPUClipElementDescriptor.contentKeySegment(): String =
    listOf(
        "element",
        sourceOrder.toString(),
        shapeKind,
        shapeKey,
        operation,
        boundsLabel,
        transformClass,
        antiAliasMode,
        fillRule,
        inverseFill.toString(),
    ).joinToString(".") { value -> value.encodeForClipKey() }

private fun String.isCanonicalClipShapeKey(): Boolean =
    (startsWith("rrect:") || startsWith("path:")) &&
        isNotBlank() &&
        !contains("handle", ignoreCase = true) &&
        !contains("pointer", ignoreCase = true) &&
        !contains("0x", ignoreCase = true)

private fun String.matchesClipShapeKind(shapeKind: String): Boolean =
    when (shapeKind) {
        "rrect" -> startsWith("rrect:")
        "path" -> startsWith("path:")
        else -> false
    }

private fun String.sanitizeForClipKey(): String =
    map { char ->
        when {
            char.isLetterOrDigit() -> char
            else -> '_'
        }
    }.joinToString("")
        .replace(Regex("_+"), "_")
        .trim('_')

private fun String.encodeForClipKey(): String =
    encodeToByteArray()
        .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

private const val clipNonClaimLine =
    "nonclaim:no-product-activation no-adapter-backed-execution no-arbitrary-clip-stack " +
        "no-stencil-coverage no-atlas-generation no-clip-shader no-cpu-rendered-clipped-layer"
