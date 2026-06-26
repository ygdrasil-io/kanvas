package org.skia.gpu.webgpu

/** Legacy gpu-raster route family that must be gated independently before migration. */
internal enum class GpuRendererLegacyRouteFamily(
    val familyId: String,
    val displayName: String,
    val defaultReplacementTicket: String,
) {
    MaterialSourcePaintPipeline(
        familyId = "material-paint",
        displayName = "Material source / paint pipeline",
        defaultReplacementTicket = "KGPU-M11-009",
    ),
    SolidRectAndDrawPaintFill(
        familyId = "solid-rect-drawpaint",
        displayName = "Solid rect and drawPaint rect fill",
        defaultReplacementTicket = "KGPU-M1-004",
    ),
    RoundedRectAndGradients(
        familyId = "rounded-rect-gradients",
        displayName = "Rounded rect and simple gradients",
        defaultReplacementTicket = "KGPU-M2-002",
    ),
    RectRrectStroke(
        familyId = "rect-rrect-stroke",
        displayName = "Rect/rrect stroke",
        defaultReplacementTicket = "KGPU-M3-003",
    ),
    DeviceScissorAndSimpleClips(
        familyId = "device-scissor-simple-clips",
        displayName = "Device scissor and simple clips",
        defaultReplacementTicket = "KGPU-M2-003",
    ),
    PathFillAndStroke(
        familyId = "path-fill-stroke",
        displayName = "Path fill and path stroke",
        defaultReplacementTicket = "KGPU-M11-007",
    ),
    ImagesBitmapCodecsUploads(
        familyId = "images-bitmap-codecs-uploads",
        displayName = "Images, bitmap shaders, codecs, and uploads",
        defaultReplacementTicket = "KGPU-M11-004",
    ),
    SaveLayerDestinationReadFilters(
        familyId = "savelayer-destination-read-filters",
        displayName = "saveLayer, destination read, and filter DAGs",
        defaultReplacementTicket = "KGPU-M11-006",
    ),
    TextAndGlyphs(
        familyId = "text-glyphs",
        displayName = "Text and glyphs",
        defaultReplacementTicket = "KGPU-M6-002",
    ),
    RuntimeEffectsColorFiltersBlendsColor(
        familyId = "runtime-effects-color-blends",
        displayName = "Runtime effects, color filters, blends, and color management",
        defaultReplacementTicket = "KGPU-M11-008",
    ),
    VerticesPointsMeshes(
        familyId = "vertices-points-meshes",
        displayName = "Vertices, points, and mesh-like draws",
        defaultReplacementTicket = "KGPU-M8-003",
    ),
    ClearDiscardTargetBackground(
        familyId = "clear-discard-target-background",
        displayName = "Clear/discard and target background",
        defaultReplacementTicket = "KGPU-M32-022",
    ),
}

/** Evidence supplied for one family-specific shadow parity migration gate. */
internal data class GpuRendererShadowParityEvidence(
    val family: GpuRendererLegacyRouteFamily,
    val shadowRouteTestId: String,
    val beforeDumpHash: String,
    val afterDumpHash: String,
    val pmRowId: String,
    val rollbackLabel: String,
    val acceptedReplacementTicket: String,
    val adapterBacked: Boolean,
    val productRouteActivated: Boolean,
    val releaseBlocking: Boolean,
    val readinessDelta: Double,
) {
    init {
        require(shadowRouteTestId.isNotBlank()) {
            "GpuRendererShadowParityEvidence.shadowRouteTestId must not be blank"
        }
        require(beforeDumpHash.isNotBlank()) {
            "GpuRendererShadowParityEvidence.beforeDumpHash must not be blank"
        }
        require(afterDumpHash.isNotBlank()) {
            "GpuRendererShadowParityEvidence.afterDumpHash must not be blank"
        }
        require(pmRowId.isNotBlank()) { "GpuRendererShadowParityEvidence.pmRowId must not be blank" }
        require(rollbackLabel.isNotBlank()) {
            "GpuRendererShadowParityEvidence.rollbackLabel must not be blank"
        }
        require(acceptedReplacementTicket.isNotBlank()) {
            "GpuRendererShadowParityEvidence.acceptedReplacementTicket must not be blank"
        }
    }
}

/** Per-family gate status before any default route migration. */
internal enum class GpuRendererShadowParityGateStatus {
    Accepted,
    MissingEvidence,
    Refused,
}

/** Gate result for one legacy family. */
internal data class GpuRendererShadowParityFamilyGate(
    val family: GpuRendererLegacyRouteFamily,
    val status: GpuRendererShadowParityGateStatus,
    val legacyDefaultActive: Boolean,
    val defaultRouteChanged: Boolean,
    val shadowRouteTestId: String?,
    val beforeDumpHash: String?,
    val afterDumpHash: String?,
    val pmRowId: String?,
    val rollbackLabel: String,
    val acceptedReplacementTicket: String,
    val adapterBacked: Boolean,
    val diagnostics: List<String>,
) {
    init {
        require(legacyDefaultActive) {
            "GpuRendererShadowParityFamilyGate must keep legacy defaults active in M10-002"
        }
        require(!defaultRouteChanged) {
            "GpuRendererShadowParityFamilyGate must not change defaults in M10-002"
        }
    }

    /** Stable PM/debug dump for this family gate. */
    fun dumpLine(): String =
        "shadow-parity:family family=${family.familyId} status=${status.dumpLabel()} " +
            "legacyDefaultActive=$legacyDefaultActive defaultRouteChanged=$defaultRouteChanged " +
            "shadowTest=${shadowRouteTestId ?: "missing"} before=${beforeDumpHash ?: "missing"} " +
            "after=${afterDumpHash ?: "missing"} pmRow=${pmRowId ?: expectedPmRow()} " +
            "rollback=$rollbackLabel replacement=$acceptedReplacementTicket adapterBacked=$adapterBacked " +
            "diagnostic=${diagnostics.ifEmpty { listOf("none") }.joinToString(",")}"

    private fun expectedPmRow(): String = "gpu-renderer.shadow-parity.${family.familyId}"
}

/** Full M10-002 shadow parity migration gate report. */
internal data class GpuRendererShadowParityGateReport(
    val familyGates: List<GpuRendererShadowParityFamilyGate>,
) {
    val acceptedFamilyCount: Int =
        familyGates.count { gate -> gate.status == GpuRendererShadowParityGateStatus.Accepted }

    val missingFamilyCount: Int =
        familyGates.count { gate -> gate.status == GpuRendererShadowParityGateStatus.MissingEvidence }

    val refusedFamilyCount: Int =
        familyGates.count { gate -> gate.status == GpuRendererShadowParityGateStatus.Refused }

    val gatePassed: Boolean =
        familyGates.all { gate -> gate.status == GpuRendererShadowParityGateStatus.Accepted }

    val productRouteActivated: Boolean = false

    val releaseBlocking: Boolean = false

    val readinessDelta: Double = 0.0

    /** Stable report dump for PM and review evidence. */
    fun dumpLines(): List<String> =
        listOf(
            "shadow-parity-gates row=gpu-renderer.shadow-parity-gates " +
                "families=${familyGates.size} accepted=$acceptedFamilyCount missing=$missingFamilyCount " +
                "refused=$refusedFamilyCount legacyDefaultActive=true defaultRouteChanged=false " +
                "productRouteActivated=$productRouteActivated releaseBlocking=$releaseBlocking " +
                "readinessDelta=$readinessDelta gatePassed=$gatePassed",
        ) + familyGates.map { gate -> gate.dumpLine() } +
            listOf(
                "shadow-parity:nonclaim defaultsChanged=false broadMigration=false " +
                    "productRouteActivated=false releaseBlocking=false readinessDelta=0.0",
            )
}

/** M10-002 validator for route-family-specific shadow parity evidence. */
internal object GpuRendererShadowParityMigrationGate {
    fun evaluate(
        evidence: List<GpuRendererShadowParityEvidence>,
    ): GpuRendererShadowParityGateReport {
        val byFamily = evidence.groupBy { item -> item.family }
        val sharedEvidenceKeys = evidence
            .groupBy { item -> item.routeEvidenceKey() }
            .filterValues { items -> items.map { item -> item.family }.distinct().size > 1 }
            .keys
        val gates = GpuRendererLegacyRouteFamily.values().map { family ->
            val familyEvidence = byFamily[family].orEmpty()
            when (familyEvidence.size) {
                0 -> missingGate(family)
                1 -> evaluateFamilyEvidence(family, familyEvidence.single(), sharedEvidenceKeys)
                else -> duplicateGate(family, familyEvidence)
            }
        }
        return GpuRendererShadowParityGateReport(familyGates = gates)
    }

    private fun missingGate(family: GpuRendererLegacyRouteFamily): GpuRendererShadowParityFamilyGate =
        GpuRendererShadowParityFamilyGate(
            family = family,
            status = GpuRendererShadowParityGateStatus.MissingEvidence,
            legacyDefaultActive = true,
            defaultRouteChanged = false,
            shadowRouteTestId = null,
            beforeDumpHash = null,
            afterDumpHash = null,
            pmRowId = null,
            rollbackLabel = "legacy.${family.familyId}.rollback",
            acceptedReplacementTicket = family.defaultReplacementTicket,
            adapterBacked = false,
            diagnostics = listOf("shadow.parity.missing_adapter_backed_evidence"),
        )

    private fun duplicateGate(
        family: GpuRendererLegacyRouteFamily,
        evidence: List<GpuRendererShadowParityEvidence>,
    ): GpuRendererShadowParityFamilyGate =
        GpuRendererShadowParityFamilyGate(
            family = family,
            status = GpuRendererShadowParityGateStatus.Refused,
            legacyDefaultActive = true,
            defaultRouteChanged = false,
            shadowRouteTestId = evidence.joinToString("|") { item -> item.shadowRouteTestId },
            beforeDumpHash = evidence.joinToString("|") { item -> item.beforeDumpHash },
            afterDumpHash = evidence.joinToString("|") { item -> item.afterDumpHash },
            pmRowId = evidence.joinToString("|") { item -> item.pmRowId },
            rollbackLabel = "legacy.${family.familyId}.rollback",
            acceptedReplacementTicket = family.defaultReplacementTicket,
            adapterBacked = evidence.all { item -> item.adapterBacked },
            diagnostics = listOf("shadow.parity.duplicate_family_evidence"),
        )

    private fun evaluateFamilyEvidence(
        family: GpuRendererLegacyRouteFamily,
        evidence: GpuRendererShadowParityEvidence,
        sharedEvidenceKeys: Set<String>,
    ): GpuRendererShadowParityFamilyGate {
        val diagnostics = evidence.diagnosticsFor(family, sharedEvidenceKeys)
        return GpuRendererShadowParityFamilyGate(
            family = family,
            status = if (diagnostics.isEmpty()) {
                GpuRendererShadowParityGateStatus.Accepted
            } else {
                GpuRendererShadowParityGateStatus.Refused
            },
            legacyDefaultActive = true,
            defaultRouteChanged = false,
            shadowRouteTestId = evidence.shadowRouteTestId,
            beforeDumpHash = evidence.beforeDumpHash,
            afterDumpHash = evidence.afterDumpHash,
            pmRowId = evidence.pmRowId,
            rollbackLabel = evidence.rollbackLabel,
            acceptedReplacementTicket = evidence.acceptedReplacementTicket,
            adapterBacked = evidence.adapterBacked,
            diagnostics = diagnostics,
        )
    }

    private fun GpuRendererShadowParityEvidence.diagnosticsFor(
        family: GpuRendererLegacyRouteFamily,
        sharedEvidenceKeys: Set<String>,
    ): List<String> = buildList {
        if (!adapterBacked) {
            add("shadow.parity.adapter_backed_required")
        }
        if (!shadowRouteTestId.contains(":${family.familyId}:")) {
            add("shadow.parity.shadow_test_not_family_scoped")
        }
        if (routeEvidenceKey() in sharedEvidenceKeys) {
            add("shadow.parity.shared_evidence_across_families")
        }
        if (!beforeDumpHash.startsWith("sha256:")) {
            add("shadow.parity.before_dump_hash_required")
        }
        if (!afterDumpHash.startsWith("sha256:")) {
            add("shadow.parity.after_dump_hash_required")
        }
        if (pmRowId != "gpu-renderer.shadow-parity.${family.familyId}") {
            add("shadow.parity.pm_row_not_family_scoped")
        }
        if (rollbackLabel != "legacy.${family.familyId}.rollback") {
            add("shadow.parity.rollback_label_not_family_scoped")
        }
        if (acceptedReplacementTicket != family.defaultReplacementTicket) {
            add("shadow.parity.replacement_ticket_mismatch")
        }
        if (productRouteActivated) {
            add("shadow.parity.product_route_activation_forbidden")
        }
        if (releaseBlocking) {
            add("shadow.parity.release_blocking_forbidden")
        }
        if (readinessDelta != 0.0) {
            add("shadow.parity.readiness_delta_forbidden")
        }
    }

    private fun GpuRendererShadowParityEvidence.routeEvidenceKey(): String =
        "$shadowRouteTestId|$beforeDumpHash|$afterDumpHash"
}

private fun GpuRendererShadowParityGateStatus.dumpLabel(): String =
    when (this) {
        GpuRendererShadowParityGateStatus.Accepted -> "accepted"
        GpuRendererShadowParityGateStatus.MissingEvidence -> "missing"
        GpuRendererShadowParityGateStatus.Refused -> "refused"
    }
