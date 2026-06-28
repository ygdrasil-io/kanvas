package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic

/** Stable handle for a render pass in the recording task graph. */
@JvmInline
value class GPURenderPassHandle(val value: String) {
    init {
        require(value.isNotBlank()) { "GPURenderPassHandle.value must not be blank" }
    }
}

/** Adapter capability evidence required for subpass merge analysis. */
data class GPUSubpassMergeAdapterCapability(
    val supportsInputAttachment: Boolean,
    val maxColorAttachments: Int,
) {
    init {
        require(maxColorAttachments > 0) {
            "GPUSubpassMergeAdapterCapability.maxColorAttachments must be positive"
        }
    }
}

/**
 * Per-pass properties inspected during subpass merge analysis.
 *
 * Each candidate records the pass handle, scope, format, sample count, and
 * whether any intervening GPU operation (barrier, copy, upload, readback,
 * dispatch) is required between it and the preceding pass.
 */
data class GPUSubpassMergeCandidate(
    val pass: GPURenderPassHandle,
    val scopeId: String,
    val colorAttachmentFormat: String,
    val sampleCount: Int,
    val hasInterveningBarrier: Boolean = false,
    val hasInterveningCopy: Boolean = false,
    val hasInterveningUpload: Boolean = false,
    val hasInterveningReadback: Boolean = false,
    val hasInterveningDispatch: Boolean = false,
) {
    val hasAnyInterveningOperation: Boolean
        get() = hasInterveningBarrier || hasInterveningCopy || hasInterveningUpload ||
            hasInterveningReadback || hasInterveningDispatch

    init {
        require(scopeId.isNotBlank()) { "GPUSubpassMergeCandidate.scopeId must not be blank" }
        require(colorAttachmentFormat.isNotBlank()) {
            "GPUSubpassMergeCandidate.colorAttachmentFormat must not be blank"
        }
        require(sampleCount > 0) { "GPUSubpassMergeCandidate.sampleCount must be positive" }
    }
}

/** Accepted subpass merge plan describing a compatible producer-consumer pair. */
data class GPUSubpassMergePlan(
    val producerPass: GPURenderPassHandle,
    val consumerPass: GPURenderPassHandle,
    val inputAttachmentIndex: Int,
    val colorAttachmentIndex: Int,
) {
    init {
        require(inputAttachmentIndex >= 0) { "inputAttachmentIndex must be non-negative" }
        require(colorAttachmentIndex >= 0) { "colorAttachmentIndex must be non-negative" }
    }
}

/** Refused producer-consumer pair with a terminal diagnostic. */
data class GPUSubpassMergeRefusal(
    val producerPass: GPURenderPassHandle,
    val consumerPass: GPURenderPassHandle,
    val diagnostic: RefuseDiagnostic,
)

/** Result of subpass merge analysis over an ordered pass sequence. */
data class GPUSubpassMergeAnalysis(
    val eligiblePairs: List<GPUSubpassMergePlan>,
    val refusedPairs: List<GPUSubpassMergeRefusal>,
) {
    fun dumpLines(): List<String> =
        listOf(
            "passes.subpass-merge eligible=${eligiblePairs.size} refused=${refusedPairs.size}",
        ) + eligiblePairs.map { plan -> plan.dumpLine() } +
            refusedPairs.map { refusal -> refusal.dumpLine() }
}

private fun GPUSubpassMergePlan.dumpLine(): String =
    "passes.subpass-merge.mergable producer=${producerPass.value} consumer=${consumerPass.value} " +
        "inputAttachmentIndex=$inputAttachmentIndex colorAttachmentIndex=$colorAttachmentIndex"

private fun GPUSubpassMergeRefusal.dumpLine(): String =
    "passes.subpass-merge.refused producer=${producerPass.value} consumer=${consumerPass.value} " +
        "code=${diagnostic.code} message=${diagnostic.message} " +
        "stage=${diagnostic.stage} terminal=${diagnostic.terminal}"

object GpuSubpassMergeReason {
    const val NO_INPUT_ATTACHMENT = "unsupported.recording.subpass_merge_no_input_attachment"
    const val INCOMPATIBLE = "unsupported.recording.subpass_merge_incompatible"
}

private const val STAGE = "subpass-merge"

/**
 * Analyses an ordered sequence of render passes for subpass merge eligibility.
 *
 * Each adjacent pair is inspected for:
 * 1. Adapter support for [inputAttachment] reads
 * 2. Same render pass scope
 * 3. No intervening GPU operations (barrier, copy, upload, readback, dispatch)
 * 4. Compatible color attachment formats
 * 5. Same sample count
 */
fun analyzeSubpassMerge(
    passes: List<GPUSubpassMergeCandidate>,
    adapter: GPUSubpassMergeAdapterCapability,
): GPUSubpassMergeAnalysis {
    if (passes.size < 2) {
        return GPUSubpassMergeAnalysis(eligiblePairs = emptyList(), refusedPairs = emptyList())
    }

    val eligiblePairs = mutableListOf<GPUSubpassMergePlan>()
    val refusedPairs = mutableListOf<GPUSubpassMergeRefusal>()

    if (!adapter.supportsInputAttachment) {
        for (i in 0 until passes.lastIndex) {
            val producer = passes[i]
            val consumer = passes[i + 1]
            refusedPairs.add(
                GPUSubpassMergeRefusal(
                    producerPass = producer.pass,
                    consumerPass = consumer.pass,
                    diagnostic = RefuseDiagnostic(
                        code = GpuSubpassMergeReason.NO_INPUT_ATTACHMENT,
                        message = "Adapter does not support inputAttachment reads",
                        stage = STAGE,
                        terminal = true,
                    ),
                ),
            )
        }
        return GPUSubpassMergeAnalysis(eligiblePairs = emptyList(), refusedPairs = refusedPairs)
    }

    for (i in 0 until passes.lastIndex) {
        val producer = passes[i]
        val consumer = passes[i + 1]

        val refusal = checkMergeEligibility(producer, consumer)
        if (refusal != null) {
            refusedPairs.add(refusal)
        } else {
            eligiblePairs.add(
                GPUSubpassMergePlan(
                    producerPass = producer.pass,
                    consumerPass = consumer.pass,
                    inputAttachmentIndex = 0,
                    colorAttachmentIndex = 0,
                ),
            )
        }
    }

    return GPUSubpassMergeAnalysis(eligiblePairs = eligiblePairs, refusedPairs = refusedPairs)
}

private fun checkMergeEligibility(
    producer: GPUSubpassMergeCandidate,
    consumer: GPUSubpassMergeCandidate,
): GPUSubpassMergeRefusal? {
    if (producer.scopeId != consumer.scopeId) {
        return GPUSubpassMergeRefusal(
            producerPass = producer.pass,
            consumerPass = consumer.pass,
            diagnostic = RefuseDiagnostic(
                code = GpuSubpassMergeReason.INCOMPATIBLE,
                message = "Different render pass scope: producer=${producer.scopeId} consumer=${consumer.scopeId}",
                stage = STAGE,
                terminal = true,
            ),
        )
    }

    if (consumer.hasInterveningBarrier) {
        return GPUSubpassMergeRefusal(
            producerPass = producer.pass,
            consumerPass = consumer.pass,
            diagnostic = RefuseDiagnostic(
                code = GpuSubpassMergeReason.INCOMPATIBLE,
                message = "Intervening barrier prevents subpass merge",
                stage = STAGE,
                terminal = true,
            ),
        )
    }

    if (consumer.hasInterveningCopy) {
        return GPUSubpassMergeRefusal(
            producerPass = producer.pass,
            consumerPass = consumer.pass,
            diagnostic = RefuseDiagnostic(
                code = GpuSubpassMergeReason.INCOMPATIBLE,
                message = "Intervening copy prevents subpass merge",
                stage = STAGE,
                terminal = true,
            ),
        )
    }

    if (consumer.hasInterveningUpload) {
        return GPUSubpassMergeRefusal(
            producerPass = producer.pass,
            consumerPass = consumer.pass,
            diagnostic = RefuseDiagnostic(
                code = GpuSubpassMergeReason.INCOMPATIBLE,
                message = "Intervening upload prevents subpass merge",
                stage = STAGE,
                terminal = true,
            ),
        )
    }

    if (consumer.hasInterveningReadback) {
        return GPUSubpassMergeRefusal(
            producerPass = producer.pass,
            consumerPass = consumer.pass,
            diagnostic = RefuseDiagnostic(
                code = GpuSubpassMergeReason.INCOMPATIBLE,
                message = "Intervening readback prevents subpass merge",
                stage = STAGE,
                terminal = true,
            ),
        )
    }

    if (consumer.hasInterveningDispatch) {
        return GPUSubpassMergeRefusal(
            producerPass = producer.pass,
            consumerPass = consumer.pass,
            diagnostic = RefuseDiagnostic(
                code = GpuSubpassMergeReason.INCOMPATIBLE,
                message = "Intervening dispatch prevents subpass merge",
                stage = STAGE,
                terminal = true,
            ),
        )
    }

    if (producer.colorAttachmentFormat != consumer.colorAttachmentFormat) {
        return GPUSubpassMergeRefusal(
            producerPass = producer.pass,
            consumerPass = consumer.pass,
            diagnostic = RefuseDiagnostic(
                code = GpuSubpassMergeReason.INCOMPATIBLE,
                message = "Incompatible color attachment format: producer=${producer.colorAttachmentFormat} consumer=${consumer.colorAttachmentFormat}",
                stage = STAGE,
                terminal = true,
            ),
        )
    }

    if (producer.sampleCount != consumer.sampleCount) {
        return GPUSubpassMergeRefusal(
            producerPass = producer.pass,
            consumerPass = consumer.pass,
            diagnostic = RefuseDiagnostic(
                code = GpuSubpassMergeReason.INCOMPATIBLE,
                message = "Mismatched sample count: producer=${producer.sampleCount} consumer=${consumer.sampleCount}",
                stage = STAGE,
                terminal = true,
            ),
        )
    }

    return null
}
