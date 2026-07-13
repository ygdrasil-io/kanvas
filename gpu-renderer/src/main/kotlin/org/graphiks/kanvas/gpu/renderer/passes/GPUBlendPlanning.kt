package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.state.GPUAlphaPlan
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import java.security.MessageDigest

/** Coverage topology consumed by the final target blend. */
enum class GPUCoverageConsumption {
    FullOrScissor,
    ScalarCoverage,
    StencilCoverage1x,
    MultisampleAttachmentCoverage,
    LCDCoverage,
}

/** Proof available for source alpha during blend specialization. */
enum class GPUSourceAlphaClassification {
    Translucent,
    ProvenOpaque,
}

/** Fragment-output transformation required by an exact coverage-aware route. */
enum class GPUSourceCoverageEncoding {
    None,
    Coverage,
    ModulateRGBA,
    CoverageTimesOneMinusSourceAlpha,
    CoverageTimesOneMinusSourceRGBA,
    ScalarCoverageInShader,
    LCDCoverageInShader,
}

/** Semantic destination input required by a canonical blend plan. */
enum class GPUBlendDestinationReadRequirement {
    None,
    DestinationTextureRequired,
    Refused,
}

/** Target facts that affect exact attachment blend representability. */
data class GPUTargetBlendFacts(
    val formatClass: String,
    val clampsNormalizedColorWrites: Boolean,
    val premultipliedAlpha: Boolean,
) {
    init {
        require(formatClass.isNotBlank()) { "GPUTargetBlendFacts.formatClass must not be blank" }
    }
}

/** Scope that must be refused when exact blend execution is unavailable. */
enum class GPURefusalScope {
    RefusedLeafDrawStep,
    RefusedCompositeCommand,
    AtomicFrameFailure,
}

/** Stable canonical blend diagnostic. */
data class GPUBlendDiagnostic(
    val code: String,
    val mode: GPUBlendMode,
    val message: String,
    val terminal: Boolean = true,
)

/** Exhaustive blend specialization product. */
sealed interface GPUBlendPlan {
    val mode: GPUBlendMode
    val sourceCoverageEncoding: GPUSourceCoverageEncoding
        get() = GPUSourceCoverageEncoding.None
    val destinationReadRequirement: GPUBlendDestinationReadRequirement
        get() = GPUBlendDestinationReadRequirement.None

    data class FixedFunctionBlend(
        override val mode: GPUBlendMode,
        val state: GPUFixedFunctionBlendState,
        override val sourceCoverageEncoding: GPUSourceCoverageEncoding,
    ) : GPUBlendPlan

    data class ShaderBlendNoDstRead(
        override val mode: GPUBlendMode,
        val formulaId: String,
        override val sourceCoverageEncoding: GPUSourceCoverageEncoding,
    ) : GPUBlendPlan

    data class ShaderBlendWithDstRead(
        override val mode: GPUBlendMode,
        val formulaId: String,
        override val sourceCoverageEncoding: GPUSourceCoverageEncoding,
    ) : GPUBlendPlan {
        override val destinationReadRequirement =
            GPUBlendDestinationReadRequirement.DestinationTextureRequired
    }

    data class LayerCompositeBlend(
        val child: GPUBlendPlan,
        val layerOrderingToken: String,
    ) : GPUBlendPlan {
        override val mode: GPUBlendMode
            get() = child.mode
        override val sourceCoverageEncoding: GPUSourceCoverageEncoding
            get() = child.sourceCoverageEncoding
        override val destinationReadRequirement: GPUBlendDestinationReadRequirement
            get() = child.destinationReadRequirement
    }

    data class NoOp(override val mode: GPUBlendMode, val reason: String) : GPUBlendPlan

    data class UnsupportedBlend(
        override val mode: GPUBlendMode,
        val diagnostic: GPUBlendDiagnostic,
        val refusalScope: GPURefusalScope,
    ) : GPUBlendPlan {
        override val destinationReadRequirement = GPUBlendDestinationReadRequirement.Refused
    }
}

/** Immutable inputs to exact blend specialization. */
data class GPUBlendSpecializationRequest(
    val mode: GPUBlendMode,
    val coverage: GPUCoverageConsumption,
    val sourceAlpha: GPUSourceAlphaClassification,
    val target: GPUTargetBlendFacts,
    val samplePlan: GPUSamplePlan,
    val layerOrderingToken: String? = null,
    /** True when the draw would sample the same attachment it is actively writing. */
    val activeAttachmentSampled: Boolean = false,
)

/** Pure, exhaustive 29-mode blend-and-coverage specializer. */
class GPUBlendPlanner {
    fun plan(request: GPUBlendSpecializationRequest): GPUBlendPlan {
        val child = planChild(request)
        val layerOrderingToken = request.layerOrderingToken ?: return child
        return GPUBlendPlan.LayerCompositeBlend(
            child = child,
            layerOrderingToken = layerOrderingToken,
        )
    }

    private fun planChild(request: GPUBlendSpecializationRequest): GPUBlendPlan {
        if (request.activeAttachmentSampled) {
            return refused(request.mode, "unsupported.destination_read.active_attachment_sampled")
        }
        if (request.target.formatClass !in acceptedBlendTargetFormats) {
            return refused(request.mode, "unsupported.target.format_blend_incompatible")
        }
        if (!request.target.premultipliedAlpha) {
            return refused(request.mode, "unsupported.color.premul_conversion_unvalidated")
        }
        if (request.mode == GPUBlendMode.DST) {
            return GPUBlendPlan.NoOp(request.mode, "destination is unchanged")
        }
        if (request.coverage == GPUCoverageConsumption.LCDCoverage) {
            return if (request.samplePlan.sampleCount == 1) {
                shaderWithDestination(
                    mode = request.mode,
                    formulaId = "lcd.${request.mode.gpuLabel}@v1",
                    coverageEncoding = GPUSourceCoverageEncoding.LCDCoverageInShader,
                )
            } else {
                refused(request.mode, "unsupported.blend.lcd_msaa_exactness")
            }
        }

        val plan = when (request.coverage) {
            GPUCoverageConsumption.FullOrScissor,
            GPUCoverageConsumption.StencilCoverage1x,
            GPUCoverageConsumption.MultisampleAttachmentCoverage,
            -> fullCoveragePlan(request)
            GPUCoverageConsumption.ScalarCoverage -> scalarCoveragePlan(request)
            GPUCoverageConsumption.LCDCoverage -> error("handled above")
        }
        return if (
            request.samplePlan.sampleCount > 1 &&
            plan.destinationReadRequirement == GPUBlendDestinationReadRequirement.DestinationTextureRequired
        ) {
            refused(request.mode, "unsupported.blend.msaa_destination_read_exactness")
        } else {
            plan
        }
    }

    private fun fullCoveragePlan(request: GPUBlendSpecializationRequest): GPUBlendPlan =
        when (request.mode) {
            GPUBlendMode.CLEAR -> fixed(request.mode, "zero_zero")
            GPUBlendMode.SRC -> fixed(request.mode, "one_zero")
            GPUBlendMode.DST -> GPUBlendPlan.NoOp(request.mode, "destination is unchanged")
            GPUBlendMode.SRC_OVER -> fixed(request.mode, "one_isa")
            GPUBlendMode.DST_OVER -> fixed(request.mode, "ida_one")
            GPUBlendMode.SRC_IN -> fixed(request.mode, "da_zero")
            GPUBlendMode.DST_IN -> fixed(request.mode, "zero_sa")
            GPUBlendMode.SRC_OUT -> fixed(request.mode, "ida_zero")
            GPUBlendMode.DST_OUT -> fixed(request.mode, "zero_isa")
            GPUBlendMode.SRC_ATOP -> fixed(request.mode, "da_isa")
            GPUBlendMode.DST_ATOP -> fixed(request.mode, "ida_sa")
            GPUBlendMode.XOR -> fixed(request.mode, "ida_isa")
            GPUBlendMode.PLUS -> if (request.target.clampsNormalizedColorWrites) {
                fixed(request.mode, "one_one_clamped")
            } else {
                shaderWithDestination(request.mode, "plus_exact@v1")
            }
            GPUBlendMode.MODULATE -> fixed(request.mode, "zero_sc")
            GPUBlendMode.SCREEN -> fixed(request.mode, "one_isc")
            else -> shaderWithDestination(request.mode, "${request.mode.gpuLabel}@v1")
        }

    private fun scalarCoveragePlan(request: GPUBlendSpecializationRequest): GPUBlendPlan {
        if (request.sourceAlpha == GPUSourceAlphaClassification.ProvenOpaque) {
            when (request.mode) {
                GPUBlendMode.SRC -> return fixed(request.mode, "modulate_one_isa")
                GPUBlendMode.SRC_IN -> return fixed(request.mode, "modulate_da_isa")
                GPUBlendMode.DST_IN -> return GPUBlendPlan.NoOp(request.mode, "opaque source preserves destination")
                GPUBlendMode.SRC_OUT -> return fixed(request.mode, "modulate_ida_isa")
                GPUBlendMode.DST_ATOP -> return fixed(request.mode, "modulate_ida_one")
                else -> Unit
            }
        }
        return when (request.mode) {
            GPUBlendMode.CLEAR -> fixed(request.mode, "cov_reverse_subtract")
            GPUBlendMode.SRC -> scalarShaderWithDestination(request.mode, "src@v1")
            GPUBlendMode.DST -> GPUBlendPlan.NoOp(request.mode, "destination is unchanged")
            GPUBlendMode.SRC_OVER -> fixed(request.mode, "modulate_one_isa")
            GPUBlendMode.DST_OVER -> fixed(request.mode, "modulate_ida_one")
            GPUBlendMode.SRC_IN -> scalarShaderWithDestination(request.mode, "src_in@v1")
            GPUBlendMode.DST_IN -> fixed(request.mode, "cov_reverse_subtract_isa")
            GPUBlendMode.SRC_OUT -> scalarShaderWithDestination(request.mode, "src_out@v1")
            GPUBlendMode.DST_OUT -> fixed(request.mode, "modulate_zero_isa")
            GPUBlendMode.SRC_ATOP -> fixed(request.mode, "modulate_da_isa")
            GPUBlendMode.DST_ATOP -> scalarShaderWithDestination(request.mode, "dst_atop@v1")
            GPUBlendMode.XOR -> fixed(request.mode, "modulate_ida_isa")
            GPUBlendMode.PLUS -> scalarShaderWithDestination(request.mode, "plus_exact@v1")
            GPUBlendMode.MODULATE -> fixed(request.mode, "cov_reverse_subtract_isc")
            GPUBlendMode.SCREEN -> fixed(request.mode, "modulate_one_isc")
            else -> scalarShaderWithDestination(request.mode, "${request.mode.gpuLabel}@v1")
        }
    }
}

/** Evidence-only request built around the canonical exhaustive planner. */
data class GPUBlendAllowlistRequest(
    val commandId: String,
    val mode: GPUBlendMode,
    val targetFormatClass: String,
    val materialKeyHash: String,
    val renderStepIdentity: String,
    val alphaPlan: GPUAlphaPlan = GPUAlphaPlan(
        inputAlpha = "premultiplied",
        outputAlpha = "premultiplied",
        premultiply = false,
        clamp = true,
    ),
    val coverage: GPUCoverageConsumption = GPUCoverageConsumption.FullOrScissor,
    val sourceAlpha: GPUSourceAlphaClassification = GPUSourceAlphaClassification.Translucent,
    val samplePlan: GPUSamplePlan = GPUSamplePlan.SingleSampleFrame,
    val activeAttachmentSampled: Boolean = false,
) {
    init {
        require(commandId.isNotBlank()) { "GPUBlendAllowlistRequest.commandId must not be blank" }
        require(targetFormatClass.isNotBlank()) { "GPUBlendAllowlistRequest.targetFormatClass must not be blank" }
        require(materialKeyHash.isNotBlank()) { "GPUBlendAllowlistRequest.materialKeyHash must not be blank" }
        require(renderStepIdentity.isNotBlank()) { "GPUBlendAllowlistRequest.renderStepIdentity must not be blank" }
    }
}

/** Stable plan classification used only by evidence and boundary dumps. */
enum class GPUBlendPlanKind {
    FixedFunctionBlend,
    ShaderBlendNoDstRead,
    ShaderBlendWithDstRead,
    LayerCompositeBlend,
    NoOp,
    UnsupportedBlend,
}

/** Evidence adapter output. Blend semantics remain entirely in [plan]. */
data class GPUBlendAllowlistGatePlan(
    val commandId: String,
    val evidenceRow: String,
    val routeKind: String,
    val classification: String,
    val promoted: Boolean,
    val productActivation: Boolean,
    val materialized: Boolean,
    val targetFormatClass: String,
    val materialKeyHash: String,
    val renderStepIdentity: String,
    val alphaPlan: GPUAlphaPlan,
    val planKind: GPUBlendPlanKind,
    val plan: GPUBlendPlan,
    val fixedFunctionState: GPUFixedFunctionBlendState?,
    val destinationReadRequirement: GPUBlendDestinationReadRequirement,
    val activeAttachmentSampled: Boolean,
    val blendStateHash: String,
    val pipelineKeyHash: String,
    val diagnostics: List<GPUBlendDiagnostic>,
) {
    val pipelineBlendStateKey: String
        get() = blendStateHash

    /** Returns deterministic evidence from the canonical plan and key inputs. */
    fun dumpLines(): List<String> = listOf(
        "blend:allowlist row=$evidenceRow routeKind=$routeKind classification=$classification " +
            "promoted=$promoted productActivation=$productActivation materialized=$materialized " +
            "command=$commandId mode=${plan.mode} plan=$planKind target=$targetFormatClass " +
            "state=$blendStateHash pipeline=$pipelineKeyHash",
        "blend:alpha input=${alphaPlan.inputAlpha} output=${alphaPlan.outputAlpha} " +
            "premultiply=${alphaPlan.premultiply} clamp=${alphaPlan.clamp}",
        fixedFunctionState?.dumpLine()
            ?: "blend:formula identity=${plan.formulaIdentity()} coverage=${plan.sourceCoverageEncoding} " +
                "destinationRead=$destinationReadRequirement",
        "blend:pipeline-key material=$materialKeyHash renderStep=$renderStepIdentity " +
            "blend=$blendStateHash coverage=${plan.sourceCoverageEncoding} pipelineKey=$pipelineKeyHash",
        "blend:diagnostic code=${diagnostics.single().code} terminal=${diagnostics.single().terminal} " +
            "mode=${diagnostics.single().mode}",
        "blend:nonclaim framebufferFetch=false inputAttachment=false cpuRenderedFallback=false productActivation=true",
    )
}

/** Evidence adapter over [GPUBlendPlanner]; it owns no blend-mode table. */
class GPUBlendAllowlistPlanner(
    private val planner: GPUBlendPlanner = GPUBlendPlanner(),
) {
    fun plan(request: GPUBlendAllowlistRequest): GPUBlendAllowlistGatePlan {
        val canonicalRequest = GPUBlendSpecializationRequest(
            mode = request.mode,
            coverage = request.coverage,
            sourceAlpha = request.sourceAlpha,
            target = GPUTargetBlendFacts(
                formatClass = request.targetFormatClass,
                clampsNormalizedColorWrites = request.targetFormatClass.endsWith("unorm"),
                premultipliedAlpha = request.alphaPlan.isAcceptedPremultiplied(),
            ),
            samplePlan = request.samplePlan,
            activeAttachmentSampled = request.activeAttachmentSampled,
        )
        val plan = planner.plan(canonicalRequest)

        val planKind = plan.kind()
        val blendStateHash = plan.blendStateHash()
        val pipelineKeyHash = stableBlendHash(
            listOf(
                "blend-pipeline-key-v2",
                request.materialKeyHash,
                request.renderStepIdentity,
                request.targetFormatClass,
                request.samplePlan.sampleCount.toString(),
                request.sourceAlpha.name,
                request.coverage.name,
                blendStateHash,
            ),
        )
        val diagnostic = when (plan) {
            is GPUBlendPlan.UnsupportedBlend -> plan.diagnostic
            else -> GPUBlendDiagnostic(
                code = "accepted.blend.${planKind.name.toStableLabel()}",
                mode = plan.mode,
                message = "canonical blend planner accepted ${plan.mode.gpuLabel} as $planKind",
                terminal = false,
            )
        }
        return GPUBlendAllowlistGatePlan(
            commandId = request.commandId,
            evidenceRow = "gpu-renderer.blend-allowlist",
            routeKind = if (plan is GPUBlendPlan.UnsupportedBlend) "RefuseDiagnostic" else "GPUNative",
            classification = "TargetNative",
            promoted = false,
            productActivation = true,
            materialized = false,
            targetFormatClass = request.targetFormatClass,
            materialKeyHash = request.materialKeyHash,
            renderStepIdentity = request.renderStepIdentity,
            alphaPlan = request.alphaPlan,
            planKind = planKind,
            plan = plan,
            fixedFunctionState = (plan as? GPUBlendPlan.FixedFunctionBlend)?.state,
            destinationReadRequirement = plan.destinationReadRequirement,
            activeAttachmentSampled = request.activeAttachmentSampled,
            blendStateHash = blendStateHash,
            pipelineKeyHash = if (plan is GPUBlendPlan.UnsupportedBlend) "none" else pipelineKeyHash,
            diagnostics = listOf(diagnostic),
        )
    }
}

private val acceptedBlendTargetFormats = setOf("rgba8unorm", "bgra8unorm", "rgba16float")

private fun GPUAlphaPlan.isAcceptedPremultiplied(): Boolean =
    inputAlpha == "premultiplied" &&
        outputAlpha == "premultiplied" &&
        !premultiply

private fun GPUBlendPlan.kind(): GPUBlendPlanKind = when (this) {
    is GPUBlendPlan.FixedFunctionBlend -> GPUBlendPlanKind.FixedFunctionBlend
    is GPUBlendPlan.ShaderBlendNoDstRead -> GPUBlendPlanKind.ShaderBlendNoDstRead
    is GPUBlendPlan.ShaderBlendWithDstRead -> GPUBlendPlanKind.ShaderBlendWithDstRead
    is GPUBlendPlan.LayerCompositeBlend -> GPUBlendPlanKind.LayerCompositeBlend
    is GPUBlendPlan.NoOp -> GPUBlendPlanKind.NoOp
    is GPUBlendPlan.UnsupportedBlend -> GPUBlendPlanKind.UnsupportedBlend
}

private fun GPUBlendPlan.formulaIdentity(): String = when (this) {
    is GPUBlendPlan.FixedFunctionBlend -> state.stateId
    is GPUBlendPlan.ShaderBlendNoDstRead -> formulaId
    is GPUBlendPlan.ShaderBlendWithDstRead -> formulaId
    is GPUBlendPlan.LayerCompositeBlend -> child.formulaIdentity()
    is GPUBlendPlan.NoOp -> "no-op"
    is GPUBlendPlan.UnsupportedBlend -> diagnostic.code
}

private fun GPUBlendPlan.blendStateHash(): String = when (this) {
    is GPUBlendPlan.UnsupportedBlend -> "none"
    else -> "sha256:" + stableBlendHash(
        listOf(
            "blend-specialization-v2",
            mode.gpuLabel,
            kind().name,
            formulaIdentity(),
            sourceCoverageEncoding.name,
            destinationReadRequirement.name,
            (this as? GPUBlendPlan.FixedFunctionBlend)?.state?.let { state ->
                listOf(
                    state.color.sourceFactor,
                    state.color.destinationFactor,
                    state.color.operation,
                    state.alpha.sourceFactor,
                    state.alpha.destinationFactor,
                    state.alpha.operation,
                    state.writeMask,
                ).joinToString(":")
            }.orEmpty(),
        ),
    )
}

private fun String.toStableLabel(): String =
    fold(StringBuilder()) { result, character ->
        if (character.isUpperCase() && result.isNotEmpty()) result.append('_')
        result.append(character.lowercaseChar())
    }.toString()

private fun stableBlendHash(parts: List<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = parts.joinToString(separator = "\u001F").toByteArray(Charsets.UTF_8)
    return digest.digest(bytes)
        .take(8)
        .joinToString("") { byte -> "%02x".format(byte) }
}

private data class FixedFunctionStateSpec(
    val colorSource: String,
    val colorDestination: String,
    val colorOperation: String = "add",
    val alphaSource: String = colorSource,
    val alphaDestination: String = colorDestination,
    val alphaOperation: String = colorOperation,
    val coverageEncoding: GPUSourceCoverageEncoding = GPUSourceCoverageEncoding.None,
)

private val fixedFunctionStates = mapOf(
    "zero_zero" to FixedFunctionStateSpec("zero", "zero"),
    "one_zero" to FixedFunctionStateSpec("one", "zero"),
    "one_isa" to FixedFunctionStateSpec("one", "one-minus-src-alpha"),
    "ida_one" to FixedFunctionStateSpec("one-minus-dst-alpha", "one"),
    "da_zero" to FixedFunctionStateSpec("dst-alpha", "zero"),
    "zero_sa" to FixedFunctionStateSpec("zero", "src-alpha"),
    "ida_zero" to FixedFunctionStateSpec("one-minus-dst-alpha", "zero"),
    "zero_isa" to FixedFunctionStateSpec("zero", "one-minus-src-alpha"),
    "da_isa" to FixedFunctionStateSpec("dst-alpha", "one-minus-src-alpha"),
    "ida_sa" to FixedFunctionStateSpec("one-minus-dst-alpha", "src-alpha"),
    "ida_isa" to FixedFunctionStateSpec("one-minus-dst-alpha", "one-minus-src-alpha"),
    "one_one_clamped" to FixedFunctionStateSpec("one", "one"),
    "zero_sc" to FixedFunctionStateSpec("zero", "src", alphaDestination = "src-alpha"),
    "one_isc" to FixedFunctionStateSpec("one", "one-minus-src", alphaDestination = "one-minus-src-alpha"),
    "cov_reverse_subtract" to FixedFunctionStateSpec(
        colorSource = "dst",
        colorDestination = "one",
        colorOperation = "reverse-subtract",
        alphaSource = "dst-alpha",
        alphaDestination = "one",
        alphaOperation = "reverse-subtract",
        coverageEncoding = GPUSourceCoverageEncoding.Coverage,
    ),
    "modulate_one_isa" to FixedFunctionStateSpec(
        "one",
        "one-minus-src-alpha",
        coverageEncoding = GPUSourceCoverageEncoding.ModulateRGBA,
    ),
    "modulate_ida_one" to FixedFunctionStateSpec(
        "one-minus-dst-alpha",
        "one",
        coverageEncoding = GPUSourceCoverageEncoding.ModulateRGBA,
    ),
    "cov_reverse_subtract_isa" to FixedFunctionStateSpec(
        colorSource = "dst",
        colorDestination = "one",
        colorOperation = "reverse-subtract",
        alphaSource = "dst-alpha",
        alphaDestination = "one",
        alphaOperation = "reverse-subtract",
        coverageEncoding = GPUSourceCoverageEncoding.CoverageTimesOneMinusSourceAlpha,
    ),
    "modulate_zero_isa" to FixedFunctionStateSpec(
        "zero",
        "one-minus-src-alpha",
        coverageEncoding = GPUSourceCoverageEncoding.ModulateRGBA,
    ),
    "modulate_da_isa" to FixedFunctionStateSpec(
        "dst-alpha",
        "one-minus-src-alpha",
        coverageEncoding = GPUSourceCoverageEncoding.ModulateRGBA,
    ),
    "modulate_ida_isa" to FixedFunctionStateSpec(
        "one-minus-dst-alpha",
        "one-minus-src-alpha",
        coverageEncoding = GPUSourceCoverageEncoding.ModulateRGBA,
    ),
    "cov_reverse_subtract_isc" to FixedFunctionStateSpec(
        colorSource = "dst",
        colorDestination = "one",
        colorOperation = "reverse-subtract",
        alphaSource = "dst-alpha",
        alphaDestination = "one",
        alphaOperation = "reverse-subtract",
        coverageEncoding = GPUSourceCoverageEncoding.CoverageTimesOneMinusSourceRGBA,
    ),
    "modulate_one_isc" to FixedFunctionStateSpec(
        colorSource = "one",
        colorDestination = "one-minus-src",
        alphaDestination = "one-minus-src-alpha",
        coverageEncoding = GPUSourceCoverageEncoding.ModulateRGBA,
    ),
)

private fun fixed(mode: GPUBlendMode, stateId: String): GPUBlendPlan.FixedFunctionBlend {
    val spec = fixedFunctionStates.getValue(stateId)
    return GPUBlendPlan.FixedFunctionBlend(
        mode = mode,
        state = GPUFixedFunctionBlendState(
            stateId = stateId,
            color = GPUFixedFunctionBlendComponent(
                sourceFactor = spec.colorSource,
                destinationFactor = spec.colorDestination,
                operation = spec.colorOperation,
            ),
            alpha = GPUFixedFunctionBlendComponent(
                sourceFactor = spec.alphaSource,
                destinationFactor = spec.alphaDestination,
                operation = spec.alphaOperation,
            ),
            writeMask = "rgba",
        ),
        sourceCoverageEncoding = spec.coverageEncoding,
    )
}

private fun scalarShaderWithDestination(mode: GPUBlendMode, formulaId: String): GPUBlendPlan =
    shaderWithDestination(mode, formulaId, GPUSourceCoverageEncoding.ScalarCoverageInShader)

private fun shaderWithDestination(
    mode: GPUBlendMode,
    formulaId: String,
    coverageEncoding: GPUSourceCoverageEncoding = GPUSourceCoverageEncoding.None,
): GPUBlendPlan.ShaderBlendWithDstRead = GPUBlendPlan.ShaderBlendWithDstRead(
    mode = mode,
    formulaId = formulaId,
    sourceCoverageEncoding = coverageEncoding,
)

private fun refused(mode: GPUBlendMode, code: String): GPUBlendPlan.UnsupportedBlend =
    GPUBlendPlan.UnsupportedBlend(
        mode = mode,
        diagnostic = GPUBlendDiagnostic(
            code = code,
            mode = mode,
            message = "blend plan refused ${mode.gpuLabel}: $code",
        ),
        refusalScope = GPURefusalScope.RefusedLeafDrawStep,
    )
