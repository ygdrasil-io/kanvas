package org.graphiks.kanvas.gpu.renderer.execution

/**
 * Kinds of prepared-text invariants that the preflight must validate.
 *
 * Each kind maps to exactly one refusal boundary in the production preflight.
 * The production implementation owns the canonical refusal codes; this test-only
 * matrix does not invent any.
 */
enum class GPUPreparedTextViolationKind {
    /** Inconsistent R8-atlas generation across artifact, payload, resource, encoder. */
    STALE_ATLAS_GENERATION,

    /** Modified R8 page bytes or content hash. */
    MODIFIED_PAGE_BYTES,

    /** Modified page width or height. */
    MODIFIED_PAGE_DIMENSIONS,

    /**
     * Modified page row stride. The artifact source must satisfy
     * `rowBytes >= width` and the bytes/hash must match. The
     * WebGPU 256-byte upload alignment is separately covered by
     * [COPY_ALIGNMENT_UNMET] on the upload layout, not here.
     */
    MODIFIED_PAGE_ROW_BYTES,

    /** R8Unorm support absent from the frame capabilities. */
    R8UNORM_UNSUPPORTED,

    /** One or more UV coordinates outside [0, 1] or inconsistent with placement. */
    INSTANCE_UV_INVALID,

    /** Instance stride does not match the canonical A8 encoding size. */
    INSTANCE_STRIDE_INCORRECT,

    /** Instance ranges overlap; each byte must belong to exactly one sub-run. */
    INSTANCE_RANGES_OVERLAPPING,

    /** firstInstance or instanceCount outside the frame instance buffer. */
    INSTANCE_COUNT_OUT_OF_BUFFER,

    /** Material ABI hash differs between semantic payload and compiled program. */
    MATERIAL_ABI_MISMATCH,

    /** WGSL entry point differs between task plan and compiled program. */
    WGSL_ENTRY_POINT_INCORRECT,

    /** Binding layout identity differs between semantic payload and compiled program. */
    BINDING_LAYOUT_INCORRECT,

    /** Uniform bytes altered between compilation and preflight. */
    MATERIAL_UNIFORMS_MODIFIED,

    /** Sampled resource bindings altered between compilation and preflight. */
    MATERIAL_RESOURCES_MODIFIED,

    /** An upload step expected by a text consumer is absent. */
    UPLOAD_MISSING,

    /** Two upload steps reference the same page identity (key + generation). */
    UPLOAD_DUPLICATED,

    /** An upload appears after the first consuming draw. */
    UPLOAD_AFTER_FIRST_CONSUMER,

    /** Target bounds differ between semantic payload and task plan. */
    TARGET_MODIFIED,

    /** Scissor bounds differ between semantic payload and task plan. */
    SCISSOR_MODIFIED,

    /** Clip authority identity changed between gathering and preflight. */
    CLIP_MODIFIED,

    /** Blend plan identity changed between gathering and preflight. */
    BLEND_MODIFIED,

    /** The logical resource plan for a text page does not use FrameLocal. */
    RESOURCE_LIFETIME_NOT_FRAME_LOCAL,

    /** A dependency key references a non-existent producer. */
    DEPENDENCY_KEY_INCORRECT,

    /** An operand key does not match the expected topology (role/kind/binding/ownership). */
    OPERAND_KEY_INCORRECT,

    /** The expected ownership in the operand partition is incorrect. */
    OPERAND_OWNERSHIP_INCORRECT,

    /** Texture dimension exceeds the device limit. */
    TEXTURE_LIMIT_EXCEEDED,

    /** Instance buffer size exceeds the device limit. */
    INSTANCE_BUFFER_LIMIT_EXCEEDED,

    /** Copy alignment (bytes-per-row) does not meet the device minimum. */
    COPY_ALIGNMENT_UNMET,
}

/**
 * Category grouping for violation kinds, ordered by expected preflight
 * check priority (earliest detection first).
 */
enum class GPUPreparedTextPreflightCategory(
    val priority: Int,
    val label: String,
) {
    GENERATION_IDENTITY(1, "Generation identity"),
    ATLAS_INTEGRITY(2, "Atlas integrity"),
    R8UNORM_CAPABILITY(3, "R8Unorm capability"),
    INSTANCE_LAYOUT(4, "Instance layout"),
    MATERIAL_ABI(5, "Material ABI"),
    UPLOAD_TOPOLOGY(6, "Upload topology"),
    FRAME_STATE(7, "Frame state"),
    OWNERSHIP(8, "Ownership"),
    DEPENDENCY_OPERAND(9, "Dependency / operand"),
    DEVICE_LIMITS(10, "Device limits"),
}

/**
 * One prepared-text preflight invariant to be verified against the
 * production preflight once Task 9 implements it.
 *
 * @property name human-readable label
 * @property violationKind which invariant is violated
 * @property description what the production preflight must reject
 * @property category priority grouping
 */
data class GPUPreparedTextPreflightMutation(
    val name: String,
    val violationKind: GPUPreparedTextViolationKind,
    val description: String,
    val category: GPUPreparedTextPreflightCategory,
)

/**
 * Ordered matrix of every invariant the production text preflight must
 * validate, grouped by priority category.
 *
 * Contains 28 entries, one per [GPUPreparedTextViolationKind].
 * The entire matrix is pending until the production preflight is
 * implemented; this branch only proves the baseline is valid and
 * each invariant has a unique descriptive entry.
 */
object GPUPreparedTextPreflightMutationMatrix {

    val orderedMutations: List<GPUPreparedTextPreflightMutation> = listOf(
        // ---- 1: Generation identity -----------------------------------------
        GPUPreparedTextPreflightMutation(
            name = "stale atlas generation",
            violationKind = GPUPreparedTextViolationKind.STALE_ATLAS_GENERATION,
            description = "Artifact, payload, resource, and encoder generations diverge.",
            category = GPUPreparedTextPreflightCategory.GENERATION_IDENTITY,
        ),

        // ---- 2: Atlas integrity ---------------------------------------------
        GPUPreparedTextPreflightMutation(
            name = "modified page bytes",
            violationKind = GPUPreparedTextViolationKind.MODIFIED_PAGE_BYTES,
            description = "Page bytes or content hash were altered after artifact finalization.",
            category = GPUPreparedTextPreflightCategory.ATLAS_INTEGRITY,
        ),
        GPUPreparedTextPreflightMutation(
            name = "modified page dimensions",
            violationKind = GPUPreparedTextViolationKind.MODIFIED_PAGE_DIMENSIONS,
            description = "Page width or height changed since artifact finalization.",
            category = GPUPreparedTextPreflightCategory.ATLAS_INTEGRITY,
        ),
        GPUPreparedTextPreflightMutation(
            name = "modified page row bytes",
            violationKind = GPUPreparedTextViolationKind.MODIFIED_PAGE_ROW_BYTES,
            description = "Artifact source rowBytes < width or bytes/hash no longer match. Upload alignment is a separate invariant (COPY_ALIGNMENT_UNMET).",
            category = GPUPreparedTextPreflightCategory.ATLAS_INTEGRITY,
        ),

        // ---- 3: R8Unorm capability ------------------------------------------
        GPUPreparedTextPreflightMutation(
            name = "R8Unorm unsupported",
            violationKind = GPUPreparedTextViolationKind.R8UNORM_UNSUPPORTED,
            description = "The frame capabilities do not include R8Unorm texture support.",
            category = GPUPreparedTextPreflightCategory.R8UNORM_CAPABILITY,
        ),

        // ---- 4: Instance layout ---------------------------------------------
        GPUPreparedTextPreflightMutation(
            name = "instance UV invalid",
            violationKind = GPUPreparedTextViolationKind.INSTANCE_UV_INVALID,
            description = "UV coordinates are outside [0,1] or inconsistent with the placement.",
            category = GPUPreparedTextPreflightCategory.INSTANCE_LAYOUT,
        ),
        GPUPreparedTextPreflightMutation(
            name = "instance stride incorrect",
            violationKind = GPUPreparedTextViolationKind.INSTANCE_STRIDE_INCORRECT,
            description = "Instance byte stride does not match the canonical A8 encoding.",
            category = GPUPreparedTextPreflightCategory.INSTANCE_LAYOUT,
        ),
        GPUPreparedTextPreflightMutation(
            name = "instance ranges overlapping",
            violationKind = GPUPreparedTextViolationKind.INSTANCE_RANGES_OVERLAPPING,
            description = "Two sub-runs claim the same bytes in the frame instance buffer.",
            category = GPUPreparedTextPreflightCategory.INSTANCE_LAYOUT,
        ),
        GPUPreparedTextPreflightMutation(
            name = "instance count out of buffer",
            violationKind = GPUPreparedTextViolationKind.INSTANCE_COUNT_OUT_OF_BUFFER,
            description = "firstInstance or instanceCount exceeds the frame instance buffer.",
            category = GPUPreparedTextPreflightCategory.INSTANCE_LAYOUT,
        ),

        // ---- 5: Material ABI ------------------------------------------------
        GPUPreparedTextPreflightMutation(
            name = "material ABI mismatch",
            violationKind = GPUPreparedTextViolationKind.MATERIAL_ABI_MISMATCH,
            description = "The ABI hash in the semantic payload differs from the compiled program.",
            category = GPUPreparedTextPreflightCategory.MATERIAL_ABI,
        ),
        GPUPreparedTextPreflightMutation(
            name = "WGSL entry point incorrect",
            violationKind = GPUPreparedTextViolationKind.WGSL_ENTRY_POINT_INCORRECT,
            description = "The entry point declared in the task plan differs from the program.",
            category = GPUPreparedTextPreflightCategory.MATERIAL_ABI,
        ),
        GPUPreparedTextPreflightMutation(
            name = "binding layout incorrect",
            violationKind = GPUPreparedTextViolationKind.BINDING_LAYOUT_INCORRECT,
            description = "The binding layout identity differs between payload and program.",
            category = GPUPreparedTextPreflightCategory.MATERIAL_ABI,
        ),
        GPUPreparedTextPreflightMutation(
            name = "material uniforms modified",
            violationKind = GPUPreparedTextViolationKind.MATERIAL_UNIFORMS_MODIFIED,
            description = "Uniform bytes were altered between compilation and preflight.",
            category = GPUPreparedTextPreflightCategory.MATERIAL_ABI,
        ),
        GPUPreparedTextPreflightMutation(
            name = "material resources modified",
            violationKind = GPUPreparedTextViolationKind.MATERIAL_RESOURCES_MODIFIED,
            description = "Sampled resource bindings were altered between compilation and preflight.",
            category = GPUPreparedTextPreflightCategory.MATERIAL_ABI,
        ),

        // ---- 6: Upload topology ---------------------------------------------
        GPUPreparedTextPreflightMutation(
            name = "upload missing",
            violationKind = GPUPreparedTextViolationKind.UPLOAD_MISSING,
            description = "A text consumer references a page with no corresponding upload step.",
            category = GPUPreparedTextPreflightCategory.UPLOAD_TOPOLOGY,
        ),
        GPUPreparedTextPreflightMutation(
            name = "upload duplicated",
            violationKind = GPUPreparedTextViolationKind.UPLOAD_DUPLICATED,
            description = "Two upload steps share the same page identity (key + generation).",
            category = GPUPreparedTextPreflightCategory.UPLOAD_TOPOLOGY,
        ),
        GPUPreparedTextPreflightMutation(
            name = "upload after first consumer",
            violationKind = GPUPreparedTextViolationKind.UPLOAD_AFTER_FIRST_CONSUMER,
            description = "An upload step is ordered after the first consuming draw.",
            category = GPUPreparedTextPreflightCategory.UPLOAD_TOPOLOGY,
        ),

        // ---- 7: Frame state -------------------------------------------------
        GPUPreparedTextPreflightMutation(
            name = "target modified",
            violationKind = GPUPreparedTextViolationKind.TARGET_MODIFIED,
            description = "Target bounds differ between semantic payload and task plan.",
            category = GPUPreparedTextPreflightCategory.FRAME_STATE,
        ),
        GPUPreparedTextPreflightMutation(
            name = "scissor modified",
            violationKind = GPUPreparedTextViolationKind.SCISSOR_MODIFIED,
            description = "Scissor bounds differ between semantic payload and task plan.",
            category = GPUPreparedTextPreflightCategory.FRAME_STATE,
        ),
        GPUPreparedTextPreflightMutation(
            name = "clip modified",
            violationKind = GPUPreparedTextViolationKind.CLIP_MODIFIED,
            description = "Clip authority identity changed between gathering and preflight.",
            category = GPUPreparedTextPreflightCategory.FRAME_STATE,
        ),
        GPUPreparedTextPreflightMutation(
            name = "blend modified",
            violationKind = GPUPreparedTextViolationKind.BLEND_MODIFIED,
            description = "Blend plan identity changed between gathering and preflight.",
            category = GPUPreparedTextPreflightCategory.FRAME_STATE,
        ),

        // ---- 8: Ownership ---------------------------------------------------
        GPUPreparedTextPreflightMutation(
            name = "resource lifetime not FrameLocal",
            violationKind = GPUPreparedTextViolationKind.RESOURCE_LIFETIME_NOT_FRAME_LOCAL,
            description = "The logical resource plan for a text page does not use FrameLocal.",
            category = GPUPreparedTextPreflightCategory.OWNERSHIP,
        ),
        GPUPreparedTextPreflightMutation(
            name = "operand ownership incorrect",
            violationKind = GPUPreparedTextViolationKind.OPERAND_OWNERSHIP_INCORRECT,
            description = "The expected ownership in the operand partition is incorrect.",
            category = GPUPreparedTextPreflightCategory.OWNERSHIP,
        ),

        // ---- 9: Dependency / operand ----------------------------------------
        GPUPreparedTextPreflightMutation(
            name = "dependency key incorrect",
            violationKind = GPUPreparedTextViolationKind.DEPENDENCY_KEY_INCORRECT,
            description = "A dependency key references a producer that does not exist.",
            category = GPUPreparedTextPreflightCategory.DEPENDENCY_OPERAND,
        ),
        GPUPreparedTextPreflightMutation(
            name = "operand key incorrect",
            violationKind = GPUPreparedTextViolationKind.OPERAND_KEY_INCORRECT,
            description = "An operand key does not match the expected role/kind/binding.",
            category = GPUPreparedTextPreflightCategory.DEPENDENCY_OPERAND,
        ),

        // ---- 10: Device limits ----------------------------------------------
        GPUPreparedTextPreflightMutation(
            name = "texture limit exceeded",
            violationKind = GPUPreparedTextViolationKind.TEXTURE_LIMIT_EXCEEDED,
            description = "Page texture dimensions exceed the device limit.",
            category = GPUPreparedTextPreflightCategory.DEVICE_LIMITS,
        ),
        GPUPreparedTextPreflightMutation(
            name = "instance buffer limit exceeded",
            violationKind = GPUPreparedTextViolationKind.INSTANCE_BUFFER_LIMIT_EXCEEDED,
            description = "Instance buffer size exceeds the device limit.",
            category = GPUPreparedTextPreflightCategory.DEVICE_LIMITS,
        ),
        GPUPreparedTextPreflightMutation(
            name = "copy alignment unmet",
            violationKind = GPUPreparedTextViolationKind.COPY_ALIGNMENT_UNMET,
            description = "The bytes-per-row alignment does not meet the device minimum.",
            category = GPUPreparedTextPreflightCategory.DEVICE_LIMITS,
        ),
    )

    /** Total count of all prepared mutations. */
    val totalMutations: Int = orderedMutations.size

    /** Mutations grouped by category, in priority order. */
    val byCategory: Map<GPUPreparedTextPreflightCategory, List<GPUPreparedTextPreflightMutation>> =
        orderedMutations.groupBy { it.category }

    /** Verify the matrix is internally consistent. */
    fun assertInternalConsistency() {
        val names = orderedMutations.map { it.name }
        require(names.size == names.distinct().size) {
            "Mutation names must be unique"
        }
        val kinds = orderedMutations.map { it.violationKind }
        require(kinds.size == kinds.distinct().size) {
            "Violation kinds must be unique"
        }
        orderedMutations.forEachIndexed { index, mutation ->
            if (index > 0) {
                require(
                    mutation.category.priority >= orderedMutations[index - 1].category.priority,
                ) {
                    "Mutations must be ordered by category priority (${mutation.name})"
                }
            }
        }
    }
}
