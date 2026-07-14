package org.graphiks.kanvas.gpu.renderer.validation

import java.io.File
import java.security.MessageDigest
import org.graphiks.kanvas.gpu.renderer.execution.GPUCommandSubmission
import org.graphiks.kanvas.gpu.renderer.execution.GPUReadbackResult
import org.graphiks.kanvas.gpu.renderer.execution.dumpLines
import org.graphiks.kanvas.gpu.renderer.recording.GPURecording
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingReplayResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.recording.checkReplayCompatibility
import org.graphiks.kanvas.gpu.renderer.recording.dump
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.dumpLines
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUTelemetryLedger
import org.graphiks.kanvas.gpu.renderer.wgsl.WGSLReflectionResult

/** Fixture descriptor used by GPU renderer validation tests. */
class GPUValidationFixture {
    /** Builds the deterministic ownership dump for first-slice command contracts. */
    fun firstSliceConceptOwnershipDump(): GPUContractDump =
        GPUContractDump(
            name = "gpu-renderer-first-slice-ownership",
            entries = listOf(
                GPUContractDump.Entry(
                    ownerPackage = "commands",
                    concept = "GPUDrawCommandID",
                    detail = "canonical command identifier",
                ),
                GPUContractDump.Entry(
                    ownerPackage = "commands",
                    concept = "NormalizedDrawCommand.FillRect",
                    detail = "first-slice draw command",
                ),
                GPUContractDump.Entry(
                    ownerPackage = "commands",
                    concept = "NormalizedDrawCommand.FillRRect",
                    detail = "first-expansion rounded-rect command",
                ),
                GPUContractDump.Entry(
                    ownerPackage = "commands",
                    concept = "GPUMaterialDescriptor.SolidColor",
                    detail = "first-slice material descriptor",
                ),
                GPUContractDump.Entry(
                    ownerPackage = "analysis",
                    concept = "GPUDrawAnalysisRecord",
                    detail = "first-route analysis dump schema",
                ),
                GPUContractDump.Entry(
                    ownerPackage = "routing",
                    concept = "GPURouteDecision.Refused",
                    detail = "first-route route dump schema without product promotion",
                ),
                GPUContractDump.Entry(
                    ownerPackage = "materials",
                    concept = "GPUPaintPipelinePlan",
                    detail = "first-route solid material dump schema",
                ),
                GPUContractDump.Entry(
                    ownerPackage = "wgsl",
                    concept = "WGSLReflectionResult",
                    detail = "first-route WGSL reflection dump schema",
                ),
                GPUContractDump.Entry(
                    ownerPackage = "payloads",
                    concept = "GPUPayloadGatherPlan",
                    detail = "first-route payload dump schema",
                ),
                GPUContractDump.Entry(
                    ownerPackage = "pipelines",
                    concept = "GPUPipelineKeyPreimage.Render",
                    detail = "first-route pipeline-key preimage dump schema",
                ),
                GPUContractDump.Entry(
                    ownerPackage = "resources",
                    concept = "GPUResourceMaterializationDecision.Refused",
                    detail = "first-route resource decision dump schema",
                ),
                GPUContractDump.Entry(
                    ownerPackage = "execution",
                    concept = "GPUCommandSubmission.Refused",
                    detail = "first-route submission dump schema refuses before backend work",
                ),
                GPUContractDump.Entry(
                    ownerPackage = "telemetry",
                    concept = "GPUTelemetryLedger",
                    detail = "first-route telemetry dump schema",
                ),
                GPUContractDump.Entry(
                    ownerPackage = "routing",
                    concept = "NegativeCPUFallbackRefusal",
                    detail = "forbidden CPU-rendered texture fallback remains refused",
                ),
                GPUContractDump.Entry(
                    ownerPackage = "routing",
                    concept = "UnsupportedRouteFamilyRefusal",
                    detail = "first-route ${firstRouteUnsupportedRouteFamilyRefusalDetail()}",
                ),
            ),
        )

    /** Builds deterministic evidence for compatibility aliases kept after concept renames. */
    fun aliasEvidenceDump(): GPUContractDump =
        GPUContractDump(
            name = "gpu-renderer-alias-evidence",
            entries = listOf(
                GPUContractDump.Entry(
                    ownerPackage = "commands",
                    concept = "GPUCommandId",
                    detail = "alias of GPUDrawCommandID",
                ),
            ),
        )

    /**
     * Assembles the R6 first-route PM evidence bundle from validation-owned contract entries.
     *
     * The validation package owns this helper because PM bundles are evidence artifacts, not product
     * route activation. The helper preserves the supplied entry order within each deterministic
     * category dump, emits one artifact name per required first-route category, and marks the report
     * `Incomplete` unless the positive promotion evidence categories are all present. Refusal-first
     * entries such as `GPURouteDecision.Refused`, `GPUResourceMaterializationDecision.Refused`, and
     * `GPUCommandSubmission.Refused` remain dumpable schema/refusal evidence but do not satisfy the
     * promotion gate. Unsupported-route-family refusals are tracked separately from route refusals,
     * because R6 requires nearby unsupported families to remain explicit while an accepted native
     * first route is present. Synthetic callers must label exposed test evidence with a
     * `synthetic-test` report name and diagnostic runtime evidence must use a `diagnostic` report
     * name so neither can be mistaken for default product support evidence.
     */
    fun firstRoutePMEvidenceBundle(
        name: String = "gpu-renderer-first-route-pm-evidence",
        entries: List<GPUContractDump.Entry> = firstSliceConceptOwnershipDump().entries,
    ): GPUValidationReport =
        firstRoutePMEvidenceReport(
            name = name,
            entries = entries,
            requirements = firstRoutePMEvidenceRequirements,
        )

    /**
     * Assembles R6 first-route PM evidence from a closed recording and telemetry ledger.
     *
     * Validation owns this helper because it formats evidence for tests and PM bundles; production
     * routes must not depend on it. The helper preserves recording-owned invariants: analysis,
     * task-list, compatibility, and replay facts are dump-only evidence, pre-materialization
     * recordings remain unable to claim resource materialization or command submission, and route
     * refusals stay negative evidence. Telemetry and pipeline-cache entries are emitted only from
     * facts already present in [telemetryLedger]. Missing, refused, or absent facts make the report
     * `Incomplete`; the helper never synthesizes backend success or hidden CPU fallback.
     */
    fun firstRouteRecordingPMEvidenceBundle(
        recording: GPURecording,
        telemetryLedger: GPUTelemetryLedger,
        name: String = "gpu-renderer-first-route-recording-pm-evidence",
    ): GPUValidationReport {
        val entries = firstRouteRecordingPMEvidenceEntries(
            recording = recording,
            telemetryLedger = telemetryLedger,
        )
        val requirements = firstRoutePMEvidenceRequirements + firstRouteRecordingEvidenceRequirements

        return firstRoutePMEvidenceReport(
            name = name,
            entries = entries,
            requirements = requirements,
        )
    }

    /**
     * Assembles R6 first-route PM evidence after resource materialization and backend execution.
     *
     * The helper accepts only Kanvas-owned contract objects, so backend-specific resources stay in
     * `:gpu-renderer` or another backend package. It projects materialization, submission, readback,
     * recording, explicit parser-backed WGSL reflection, and telemetry facts into validation dumps
     * without constructing success evidence. A report passes only when the supplied objects include
     * a native route recording, parser-backed WGSL reflection supplied for the execution evidence,
     * materialized resources, a submitted command, completed readback evidence, telemetry,
     * pipeline-cache facts, explicit hidden-CPU-fallback and unsupported-route-family refusal
     * evidence, plus late-stage task ids and submitted readback request ids that match the supplied
     * recording and submission. The current consistency checks validate parser backing and execution
     * correlation, but they do not derive a module identity link from backend submission state.
     */
    fun firstRouteExecutedPMEvidenceBundle(
        recording: GPURecording,
        wgslReflection: WGSLReflectionResult.Accepted? = null,
        materialization: GPUResourceMaterializationDecision,
        submission: GPUCommandSubmission,
        readbacks: List<GPUReadbackResult>,
        telemetryLedger: GPUTelemetryLedger,
        name: String = "gpu-renderer-first-route-executed-pm-evidence",
    ): GPUValidationReport {
        val entries = firstRouteRecordingPMEvidenceEntries(
            recording = recording,
            telemetryLedger = telemetryLedger,
            includePreExecutionRefusals = false,
            includePreMaterializationWGSLReflection = false,
        ) +
            listOfNotNull(wgslReflection?.firstRoutePMEvidenceEntry(evidenceLabel = name)) +
            materialization.firstRoutePMEvidenceEntry(evidenceLabel = name) +
            submission.firstRoutePMEvidenceEntry(evidenceLabel = name) +
            readbacks.map { readback -> readback.firstRoutePMEvidenceEntry(evidenceLabel = name) }
        val requirements = firstRoutePMEvidenceRequirements + firstRouteRecordingEvidenceRequirements

        val report = firstRoutePMEvidenceReport(
            name = name,
            entries = entries,
            requirements = requirements,
        )
        val consistencyDiagnostics = firstRouteExecutedPMEvidenceConsistencyDiagnostics(
            recording = recording,
            wgslReflection = wgslReflection,
            materialization = materialization,
            submission = submission,
            readbacks = readbacks,
            telemetryLedger = telemetryLedger,
        )

        return if (consistencyDiagnostics.isEmpty()) {
            report
        } else {
            report.copy(
                status = GPUValidationStatus.Failed,
                diagnostics = report.diagnostics + consistencyDiagnostics,
            )
        }
    }
}

/**
 * Validation report emitted by tests or PM evidence tooling.
 *
 * The validation package owns this report as a deterministic evidence envelope. A `Passed` status
 * means the report producer has supplied all evidence required by the relevant gate; `Incomplete`
 * means evidence is still missing or only schema/refusal-shaped; `Failed` means evidence was present
 * but violated the contract. Consumers must combine the status with dump categories and diagnostics
 * rather than treating the report name or artifact names as support claims.
 */
data class GPUValidationReport(
    val name: String,
    val status: GPUValidationStatus,
    val dumps: List<GPUContractDump>,
    val diagnostics: List<String> = emptyList(),
)

/**
 * One text artifact exported from a validation report.
 *
 * Validation owns this artifact shape so PM evidence can be packaged without exposing backend
 * handles, raw GPU resources, or filesystem conventions. `artifactName` is the durable relative
 * name inside a PM bundle and must be unique within that bundle. `lines` are snapshotted so caller
 * mutation cannot rewrite hashes or exported PM text after construction.
 */
class GPUValidationArtifact(
    val artifactName: String,
    lines: List<String>,
) {
    /** Deterministic artifact text copied at construction. */
    val lines: List<String> = lines.toList()
}

/**
 * One materialized validation artifact written to disk for PM review.
 *
 * The validation package owns this result so filesystem exports can be audited without re-reading
 * backend state. [relativePath] is the portable bundle path that was written, [file] is the concrete
 * local output location, and the line/hash fields mirror the manifest for review tooling.
 */
data class GPUValidationArtifactWrite(
    val relativePath: String,
    val file: File,
    val lineCount: Int,
    val sha256: String,
)

/**
 * Result of writing a validation artifact bundle to a local directory.
 *
 * The result preserves manifest-first write ordering. It is an export receipt, not a support claim:
 * callers must still inspect the source [GPUValidationArtifactBundle] status and gate fields.
 */
data class GPUValidationArtifactBundleWriteResult(
    val rootDirectory: File,
    val writes: List<GPUValidationArtifactWrite>,
) {
    /** Portable relative paths written by the export, manifest first. */
    val relativePaths: List<String>
        get() = writes.map { write -> write.relativePath }
}

/**
 * Portable text bundle rendered from a validation report for PM evidence.
 *
 * The bundle is evidence-only: it records report status, diagnostics, and dump artifact contents,
 * plus the first-route gate result supplied at render time, but it does not change promotion-gate
 * behavior or imply product route activation. It rejects duplicate or blank artifact names before
 * export so reviewers can materialize the bundle into files without overwriting evidence.
 * `manifestLines` is intentionally textual and deterministic because R6 still treats the final
 * external artifact format as a promotion gate decision.
 */
class GPUValidationArtifactBundle(
    val reportName: String,
    val status: GPUValidationStatus,
    val gateName: String = "first-route-promotion",
    val gatePassed: Boolean = false,
    missingEvidence: List<String> = emptyList(),
    val manifestArtifactName: String,
    artifacts: List<GPUValidationArtifact>,
    diagnostics: List<String> = emptyList(),
    gateDiagnostics: List<String> = emptyList(),
) {
    /** Missing gate categories copied from the supplied gate result. */
    val missingEvidence: List<String> = missingEvidence.toList()

    /** Dump artifacts copied deeply so manifest hashes remain stable after caller mutation. */
    val artifacts: List<GPUValidationArtifact> = artifacts
        .map { artifact ->
            GPUValidationArtifact(
                artifactName = artifact.artifactName,
                lines = artifact.lines,
            )
        }

    /** Report diagnostics copied at bundle construction. */
    val diagnostics: List<String> = diagnostics.toList()

    /** Promotion-gate diagnostics copied at bundle construction. */
    val gateDiagnostics: List<String> = gateDiagnostics.toList()

    init {
        require(reportName.isNotBlank()) {
            "validation artifact bundle report name must not be blank"
        }
        require(manifestArtifactName.isNotBlank()) {
            "validation manifest artifact name must not be blank"
        }
        require(manifestArtifactName.isPortableValidationArtifactPath()) {
            "validation manifest artifact path must be portable: $manifestArtifactName"
        }
        require(this.artifacts.all { artifact -> artifact.artifactName.isNotBlank() }) {
            "validation artifact names must not be blank"
        }
        this.artifacts.forEach { artifact ->
            require(artifact.artifactName.isPortableValidationArtifactPath()) {
                "validation artifact path must be portable: ${artifact.artifactName}"
            }
        }

        val duplicateNames = this.artifacts
            .groupingBy { artifact -> artifact.artifactName }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
            .sorted()
        require(duplicateNames.isEmpty()) {
            "duplicate validation artifact names: ${duplicateNames.joinToString(", ")}"
        }
        require(this.artifacts.none { artifact -> artifact.artifactName == manifestArtifactName }) {
            "validation manifest artifact name conflicts with dump artifact: $manifestArtifactName"
        }

        val artifactPaths = artifactNames
        val caseInsensitiveConflicts = artifactPaths.caseInsensitivePathConflicts()
        require(caseInsensitiveConflicts.isEmpty()) {
            "validation artifact paths conflict case-insensitively: ${caseInsensitiveConflicts.joinToString(", ")}"
        }
        val parentConflicts = artifactPaths.parentPathConflicts()
        require(parentConflicts.isEmpty()) {
            "validation artifact parent conflicts: ${parentConflicts.joinToString(", ")}"
        }
    }

    /** Names every exported artifact in deterministic package order, manifest first. */
    val artifactNames: List<String>
        get() = listOf(manifestArtifactName) + artifacts.map { artifact -> artifact.artifactName }

    /**
     * Renders the deterministic manifest for this PM evidence bundle.
     *
     * The manifest names artifacts and diagnostics only. Dump payload text remains in each artifact
     * so consumers can review or diff evidence without a manifest parser rewriting validation facts.
     */
    fun manifestLines(): List<String> =
        buildList {
            add("validation.report.name=$reportName")
            add("validation.report.status=${status.name}")
            add("validation.gate.name=$gateName")
            add("validation.gate.passed=$gatePassed")
            add("validation.gate.missingEvidence=${missingEvidence.manifestListValue()}")
            add("validation.bundle.scope=pm-evidence-only")
            add("validation.report.artifacts=${artifacts.size}")
            add("validation.report.diagnostics=${diagnostics.size}")
            add("validation.gate.diagnostics=${gateDiagnostics.size}")
            artifacts.forEachIndexed { index, artifact ->
                val ordinal = (index + 1).toString().padStart(2, '0')
                add("artifact.$ordinal.name=${artifact.artifactName}")
                add("artifact.$ordinal.lines=${artifact.lines.size}")
                add("artifact.$ordinal.sha256=${artifact.lines.validationArtifactSha256()}")
            }
            diagnostics.forEachIndexed { index, diagnostic ->
                val ordinal = (index + 1).toString().padStart(2, '0')
                add("diagnostic.$ordinal=$diagnostic")
            }
            gateDiagnostics.forEachIndexed { index, diagnostic ->
                val ordinal = (index + 1).toString().padStart(2, '0')
                add("gateDiagnostic.$ordinal=$diagnostic")
            }
        }

    /** Returns the deterministic text for the named artifact, including the manifest artifact. */
    fun artifactLines(artifactName: String): List<String> =
        when (artifactName) {
            manifestArtifactName -> manifestLines()
            else -> artifacts
                .firstOrNull { artifact -> artifact.artifactName == artifactName }
                ?.lines
                ?: error("unknown validation artifact: $artifactName")
        }

    /**
     * Writes the manifest and every artifact to [outputDirectory].
     *
     * All paths are validated as portable bundle-relative paths before writing. Existing files are
     * refused unless [replaceExisting] is true, so reruns cannot silently overwrite PM evidence.
     * The method writes text artifacts only and appends a final newline to each file for stable
     * command-line diffs.
     */
    fun writeTo(
        outputDirectory: File,
        replaceExisting: Boolean = false,
    ): GPUValidationArtifactBundleWriteResult {
        require(outputDirectory.path.isNotBlank()) {
            "validation artifact output directory must not be blank"
        }
        if (outputDirectory.exists()) {
            require(outputDirectory.isDirectory) {
                "validation artifact output path is not a directory: ${outputDirectory.path}"
            }
        } else {
            require(outputDirectory.mkdirs()) {
                "validation artifact output directory could not be created: ${outputDirectory.path}"
            }
        }

        val exportArtifacts = listOf(
            GPUValidationArtifact(
                artifactName = manifestArtifactName,
                lines = manifestLines(),
            ),
        ) + artifacts
        val targets = exportArtifacts.map { artifact ->
            artifact to outputDirectory.resolvePortableValidationArtifact(artifact.artifactName)
        }
        val parentConflict = targets.firstNotNullOfOrNull { (artifact, _) ->
            outputDirectory.existingParentFileConflict(artifact.artifactName)?.let { artifact.artifactName }
        }
        require(parentConflict == null) {
            "validation artifact parent conflicts: $parentConflict"
        }
        val directoryTarget = targets
            .firstOrNull { (_, target) -> target.exists() && !target.isFile }
        require(directoryTarget == null) {
            "validation artifact target is not a file: ${directoryTarget?.first?.artifactName}"
        }
        if (!replaceExisting) {
            val existing = targets
                .map { (_, target) -> target }
                .firstOrNull { target -> target.exists() }
            if (existing != null) {
                val relative = existing.relativeTo(outputDirectory).path.replace(File.separatorChar, '/')
                require(false) {
                    "validation artifact already exists: $relative"
                }
            }
        }

        val writesByPath = LinkedHashMap<String, GPUValidationArtifactWrite>()
        val writeOrder = targets.drop(1) + targets.take(1)
        writeOrder.forEach { (artifact, target) ->
            target.parentFile?.let { parent ->
                require(parent.mkdirs() || parent.isDirectory) {
                    "validation artifact parent directory could not be created: ${artifact.artifactName}"
                }
            }
            target.writeText(artifact.lines.validationArtifactText())
            writesByPath[artifact.artifactName] = GPUValidationArtifactWrite(
                relativePath = artifact.artifactName,
                file = target,
                lineCount = artifact.lines.size,
                sha256 = artifact.lines.validationArtifactSha256(),
            )
        }

        return GPUValidationArtifactBundleWriteResult(
            rootDirectory = outputDirectory,
            writes = exportArtifacts.map { artifact -> writesByPath.getValue(artifact.artifactName) },
        )
    }
}

/**
 * Renders this report as a portable PM evidence artifact bundle.
 *
 * The bundle is derived only from validation-owned report data: dump artifact names, dump lines,
 * status, and diagnostics. This mirrors Graphite's late materialization idea at the contract level:
 * Kanvas keeps evidence ordering and failure state in validation-owned text instead of copying
 * renderer task classes or ownership boundaries.
 */
fun GPUValidationReport.artifactBundle(
    manifestArtifactName: String = "$name-00-manifest.txt",
    promotionGateResult: GPUValidationGateResult = GPUPromotionGateCheck().evaluate(this),
): GPUValidationArtifactBundle =
    GPUValidationArtifactBundle(
        reportName = name,
        status = status,
        gateName = promotionGateResult.gateName,
        gatePassed = promotionGateResult.passed,
        missingEvidence = promotionGateResult.missingEvidence.toList(),
        manifestArtifactName = manifestArtifactName,
        artifacts = dumps.map { dump ->
            GPUValidationArtifact(
                artifactName = dump.artifactName,
                lines = dump.lines(),
            )
        },
        diagnostics = diagnostics.toList(),
        gateDiagnostics = promotionGateResult.diagnostics.toList(),
    )

/**
 * Writes the default R6 first-route PM evidence bundle for external review.
 *
 * This exporter intentionally uses the refusal-first validation fixture. It materializes current
 * first-route schema/refusal evidence, gate status, missing evidence, diagnostics, and artifact
 * hashes without requiring WebGPU, native windows, Kadre, or product route activation.
 */
fun writeFirstRoutePMEvidenceArtifactBundle(
    outputDirectory: File,
    replaceExisting: Boolean = false,
): GPUValidationArtifactBundleWriteResult =
    GPUValidationFixture()
        .firstRoutePMEvidenceBundle()
        .artifactBundle()
        .writeTo(
            outputDirectory = outputDirectory,
            replaceExisting = replaceExisting,
        )

/** Formats a manifest list field without whitespace or ambiguous blank values. */
private fun List<String>.manifestListValue(): String =
    if (isEmpty()) "none" else joinToString(",")

/** Renders the exact text bytes written for validation artifacts. */
private fun List<String>.validationArtifactText(): String =
    joinToString(separator = "\n") + "\n"

/** Computes the stable file-byte hash used by validation artifact manifests. */
private fun List<String>.validationArtifactSha256(): String {
    val bytes = validationArtifactText()
        .toByteArray(Charsets.UTF_8)
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)

    return "sha256:" + digest.joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
}

/** Returns true when this bundle path is portable and cannot escape the output root. */
private fun String.isPortableValidationArtifactPath(): Boolean {
    if (isBlank()) return false
    if (startsWith("/") || startsWith("\\") || contains('\\') || contains(':')) return false
    if (!all { char -> char in portableValidationArtifactPathChars }) return false

    val segments = split('/')
    return segments.all { segment ->
        segment.isNotBlank() && segment != "." && segment != ".."
    }
}

/** Returns case-insensitive path collisions that would overwrite on common PM filesystems. */
private fun List<String>.caseInsensitivePathConflicts(): List<String> =
    groupBy { path -> path.lowercase() }
        .values
        .filter { paths -> paths.distinct().size > 1 }
        .map { paths -> paths.distinct().sorted().joinToString("~") }
        .sorted()

/** Returns file/directory prefix collisions within one portable artifact path set. */
private fun List<String>.parentPathConflicts(): List<String> {
    val uniquePaths = distinct().sorted()
    return uniquePaths.flatMapIndexed { index, path ->
        uniquePaths
            .asSequence()
            .drop(index + 1)
            .mapNotNull { candidate ->
                when {
                    candidate.lowercase().startsWith("${path.lowercase()}/") -> "$path -> $candidate"
                    path.lowercase().startsWith("${candidate.lowercase()}/") -> "$candidate -> $path"
                    else -> null
                }
            }
            .toList()
    }
}

/** Returns a conflicting existing parent file for the supplied portable relative path. */
private fun File.existingParentFileConflict(relativePath: String): File? {
    var current = this
    relativePath.split('/').dropLast(1).forEach { segment ->
        current = current.resolve(segment)
        if (current.exists() && !current.isDirectory) {
            return current
        }
    }
    return null
}

/** Resolves a portable validation artifact path below this directory. */
private fun File.resolvePortableValidationArtifact(relativePath: String): File {
    require(relativePath.isPortableValidationArtifactPath()) {
        "validation artifact path must be portable: $relativePath"
    }
    return relativePath
        .split('/')
        .fold(this) { current, segment -> current.resolve(segment) }
}

private val portableValidationArtifactPathChars: Set<Char> =
    (('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf('.', '_', '-', '/')).toSet()

/** Validation status for PM evidence and contract gates. */
enum class GPUValidationStatus {
    /** Evidence satisfies the target gate. */
    Passed,
    /** Evidence fails the target gate. */
    Failed,
    /** Evidence is incomplete for the target gate. */
    Incomplete,
}

/**
 * Deterministic dump of renderer validation evidence.
 *
 * Validation owns these dump records for tests and PM bundles. `name` and `artifactName` must be
 * stable, human-readable identifiers; entries must remain ordered by the helper that produced the
 * dump. A dump is not a support claim by itself, because schema and refusal entries are intentionally
 * preserved for diagnostics and must be interpreted through `GPUPromotionGateCheck`.
 */
data class GPUContractDump(
    val name: String,
    val artifactName: String = "$name.txt",
    val entries: List<Entry>,
) {
    /** One deterministic concept-ownership or evidence row in a contract dump. */
    data class Entry(
        val ownerPackage: String,
        val concept: String,
        val detail: String,
    )

    /** Returns stable lines for snapshot-like validation and PM evidence. */
    fun lines(): List<String> =
        entries.map { entry ->
            "${entry.ownerPackage}:${entry.concept}:${entry.detail}"
        }
}

/** Deterministic dump of a key preimage. */
data class GPUKeyPreimageDump(
    val kind: String,
    val entries: List<GPUContractDump.Entry>,
)

/** Deterministic dump of WGSL reflection facts. */
data class GPUWGSLReflectionDump(
    val moduleHash: String,
    val bindings: List<GPUContractDump.Entry>,
)

/** Validation check for package ownership and dependency boundaries. */
class GPUPackageBoundaryCheck {
    /** Finds package-root, reserved-package, validation-import, and package-cycle violations. */
    fun findViolations(sourceRoot: File): List<String> {
        val sources = sourceRoot.kotlinSources()
        val packageViolations = sources.flatMap { source ->
            source.packageBoundaryViolations()
        }
        val dependencyViolations = sources.flatMap { source ->
            source.dependencyRuleViolations()
        }
        val cycleViolations = sources.packageCycleViolations()

        return (packageViolations + dependencyViolations + cycleViolations).sorted()
    }
}

/** Validation check for forbidden imports. */
class GPUForbiddenImportCheck {
    /** Finds imports from Skia-like public APIs and Graphite or Ganesh source trees. */
    fun findViolations(sourceRoot: File): List<String> =
        sourceRoot.kotlinSources()
            .flatMap { source ->
                source.imports.flatMap { imported ->
                    buildList {
                        if (imported.isSkiaLikeImport()) {
                            add("${source.relativePath}: Skia-like public API import is forbidden: $imported")
                        }
                        if (imported.isGraphiteOrGaneshImport()) {
                            add("${source.relativePath}: Graphite/Ganesh source import is forbidden: $imported")
                        }
                    }
                }
            }
            .sorted()
}

/**
 * Promotion-gate result produced from validation evidence.
 *
 * [passed] is a PM evidence verdict only; it never activates a product route by itself. When
 * [passed] is false, [missingEvidence] and [diagnostics] are the failure contract that explains
 * which R6 categories or conflicts still block promotion.
 */
data class GPUValidationGateResult(
    val gateName: String,
    val passed: Boolean,
    val missingEvidence: List<String> = emptyList(),
    val diagnostics: List<String> = emptyList(),
)

/**
 * Validation check for promotion-gate evidence.
 *
 * The gate is validation-owned and never creates backend success evidence. It passes only when the
 * report status is `Passed` and every first-route R6 category is represented by accepted evidence,
 * including native routing, materialized resources, submitted commands, completed readback,
 * telemetry, pipeline-cache facts, negative CPU fallback refusal evidence, and unsupported
 * first-route-family refusal evidence that covers every canonical unsupported family. Missing,
 * incomplete, or refusal-shaped entries are returned as stable diagnostics so PM bundles can show
 * why a route is not promoted.
 */
class GPUPromotionGateCheck {
    /** Evaluates promotion evidence without inventing fake acceptance or hiding missing categories. */
    fun evaluate(report: GPUValidationReport): GPUValidationGateResult {
        val entries = report.dumps
            .flatMap { dump -> dump.entries }
        val presentCategories = entries
            .mapNotNull { entry -> entry.firstRouteEvidenceCategory() }
            .toSet()
        val missingEvidence = firstRoutePromotionEvidenceCategories
            .filterNot { category -> category in presentCategories }
        val missingDiagnostics = if (report.diagnostics.isEmpty()) {
            firstRoutePMEvidenceDiagnostics(entries = entries, missingEvidence = missingEvidence)
        } else {
            emptyList()
        }
        val conflictingEvidence = entries.firstRouteConflictingEvidenceDiagnostics()
        val diagnostics = buildList {
            if (report.status != GPUValidationStatus.Passed) {
                add("validation report status is ${report.status}")
            }
            addAll(report.diagnostics)
            addAll(missingDiagnostics)
            addAll(conflictingEvidence)
        }

        return GPUValidationGateResult(
            gateName = "first-route-promotion",
            passed = report.status == GPUValidationStatus.Passed &&
                missingEvidence.isEmpty() &&
                conflictingEvidence.isEmpty(),
            missingEvidence = missingEvidence,
            diagnostics = diagnostics,
        )
    }
}

/** Builds a deterministic first-route PM report for the supplied requirement list. */
private fun firstRoutePMEvidenceReport(
    name: String,
    entries: List<GPUContractDump.Entry>,
    requirements: List<FirstRoutePMEvidenceRequirement>,
): GPUValidationReport {
    requireSyntheticEvidenceIsLabeled(name = name, entries = entries)

    val dumps = requirements.mapIndexed { index, requirement ->
        val dumpName = "$name-${(index + 1).toString().padStart(2, '0')}-${requirement.category}"
        GPUContractDump(
            name = dumpName,
            artifactName = "$dumpName.txt",
            entries = entries.filter { entry -> requirement.includesInDump(entry) },
        )
    }
    val presentCategories = dumps
        .flatMap { dump -> dump.entries }
        .mapNotNull { entry -> entry.firstRouteEvidenceCategory() }
        .toSet()
    val missingEvidence = firstRoutePromotionEvidenceCategories
        .filterNot { category -> category in presentCategories }
    val conflictingEvidence = entries.firstRouteConflictingEvidenceDiagnostics()

    return GPUValidationReport(
        name = name,
        status = when {
            conflictingEvidence.isNotEmpty() -> GPUValidationStatus.Failed
            missingEvidence.isEmpty() -> GPUValidationStatus.Passed
            else -> GPUValidationStatus.Incomplete
        },
        dumps = dumps,
        diagnostics = firstRoutePMEvidenceDiagnostics(entries = entries, missingEvidence = missingEvidence) +
            conflictingEvidence,
    )
}

/** Verifies positive execution evidence is connected to the recording it promotes. */
private fun firstRouteExecutedPMEvidenceConsistencyDiagnostics(
    recording: GPURecording,
    wgslReflection: WGSLReflectionResult.Accepted?,
    materialization: GPUResourceMaterializationDecision,
    submission: GPUCommandSubmission,
    readbacks: List<GPUReadbackResult>,
    telemetryLedger: GPUTelemetryLedger,
): List<String> {
    val renderTasks = recording.taskList.tasks.filterIsInstance<GPUTask.Render>()
    val recordingTaskIds = renderTasks.map { task -> task.taskId.value }.distinct()
    val recordingPassIds = renderTasks.map { task -> task.passId }.distinct()

    return buildList {
        if (wgslReflection != null) {
            addPositiveExecutedEvidenceDiagnostics(
                label = "WGSL reflection",
                diagnosticCodes = wgslReflection.diagnostics.map { diagnostic -> diagnostic.code },
            )
        }
        if (wgslReflection != null && !wgslReflection.parserState.parserBacked) {
            add(
                "executed PM evidence WGSL reflection must be parser-backed: " +
                    "module=${wgslReflection.moduleHash.value} parserState=${wgslReflection.parserState.status}",
            )
        }

        if (recordingTaskIds.isEmpty()) {
            add("executed PM evidence mismatch: recording ${recording.recordingId.value} has no render tasks")
        }

        if (materialization is GPUResourceMaterializationDecision.Materialized) {
            addPositiveExecutedEvidenceDiagnostics(
                label = "materialization",
                diagnosticCodes = materialization.diagnostics.map { diagnostic -> diagnostic.code },
            )
            addMembershipDiagnostics(
                label = "materialization taskIds",
                actual = materialization.taskIds,
                expected = recordingTaskIds,
                expectedNoun = "recording tasks",
            )
        }

        if (submission is GPUCommandSubmission.Submitted) {
            addPositiveExecutedEvidenceDiagnostics(
                label = "submission",
                diagnosticCodes = submission.diagnostics.map { diagnostic -> diagnostic.code },
            )
            addMembershipDiagnostics(
                label = "submission taskIds",
                actual = submission.taskIds,
                expected = recordingTaskIds,
                expectedNoun = "recording tasks",
            )
            addMembershipDiagnostics(
                label = "submission passIds",
                actual = submission.passIds,
                expected = recordingPassIds,
                expectedNoun = "recording passes",
            )

            val submittedReadbackIds = submission.readbackRequests
                .map { request -> request.requestId }
                .distinct()
            val submittedReadbackIdSet = submittedReadbackIds.toSet()
            val readbackResultIds = readbacks
                .map { readback ->
                    when (readback) {
                        is GPUReadbackResult.Completed -> readback.request.requestId
                        is GPUReadbackResult.Skipped -> readback.request.requestId
                        is GPUReadbackResult.Refused -> readback.request.requestId
                    }
                }
            val duplicateReadbackResultIds = readbackResultIds
                .groupingBy { requestId -> requestId }
                .eachCount()
                .let { counts ->
                    readbackResultIds
                        .distinct()
                        .filter { requestId -> counts.getValue(requestId) > 1 }
                }
            if (duplicateReadbackResultIds.isNotEmpty()) {
                add(
                    "executed PM evidence mismatch: readback requestIds with duplicate results " +
                        duplicateReadbackResultIds.joinToString(","),
                )
            }
            val readbackResultIdSet = readbackResultIds
                .distinct()
                .toSet()
            val submittedReadbackIdsMissingResults = submittedReadbackIds
                .filterNot { requestId -> requestId in readbackResultIdSet }
            if (submittedReadbackIdsMissingResults.isNotEmpty()) {
                add(
                    "executed PM evidence mismatch: submitted readback requestIds missing results " +
                        submittedReadbackIdsMissingResults.joinToString(","),
                )
            }
            val unsubmittedCompletedReadbackIds = readbacks
                .filterIsInstance<GPUReadbackResult.Completed>()
                .map { readback -> readback.request.requestId }
                .distinct()
                .filterNot { requestId -> requestId in submittedReadbackIdSet }
            if (unsubmittedCompletedReadbackIds.isNotEmpty()) {
                add(
                    "executed PM evidence mismatch: completed readback requestIds not submitted " +
                        unsubmittedCompletedReadbackIds.joinToString(","),
                )
            }
        }

        addPositiveExecutedEvidenceDiagnostics(
            label = "completed readback",
            diagnosticCodes = readbacks
                .filterIsInstance<GPUReadbackResult.Completed>()
                .flatMap { readback -> readback.diagnostics.map { diagnostic -> diagnostic.code } },
        )
        addExecutedPMTelemetryCounterDiagnostics(
            recording = recording,
            wgslReflection = wgslReflection,
            materialization = materialization,
            submission = submission,
            telemetryLedger = telemetryLedger,
        )
    }
}

/** Adds stable diagnostics when late positive evidence lacks matching telemetry counters. */
private fun MutableList<String>.addExecutedPMTelemetryCounterDiagnostics(
    recording: GPURecording,
    wgslReflection: WGSLReflectionResult.Accepted?,
    materialization: GPUResourceMaterializationDecision,
    submission: GPUCommandSubmission,
    telemetryLedger: GPUTelemetryLedger,
) {
    if (recording.routeDiagnostics.any { diagnostic -> diagnostic.isNativeRouteDiagnostic() }) {
        addMissingExecutedPMTelemetryCounterDiagnostic(
            telemetryLedger = telemetryLedger,
            name = "first_route.command.count",
            scope = "family=Rect",
        )
        addMissingExecutedPMTelemetryCounterDiagnostic(
            telemetryLedger = telemetryLedger,
            name = "first_route.route.count",
            scope = "kind=GPUNative",
        )
        addMissingExecutedPMTelemetryCounterDiagnostic(
            telemetryLedger = telemetryLedger,
            name = "first_route.negative_cpu_fallback.refusal.count",
            scope = "policy=forbidden",
        )
    }
    if (wgslReflection != null) {
        addMissingExecutedPMTelemetryCounterDiagnostic(
            telemetryLedger = telemetryLedger,
            name = "first_route.wgsl_module_validation.count",
            scope = "outcome=Success",
        )
    }
    if (
        materialization is GPUResourceMaterializationDecision.Materialized &&
        !telemetryLedger.hasPositiveFirstRouteCounter(
            name = "first_route.resource_materialization.count",
            scope = "outcome=Materialized",
        )
    ) {
        add(
            "executed PM evidence mismatch: telemetry counter missing " +
                "first_route.resource_materialization.count outcome=Materialized unit=count positive count",
        )
    }
    if (
        submission is GPUCommandSubmission.Submitted &&
        !telemetryLedger.hasPositiveFirstRouteCounter(
            name = "first_route.command_submission.count",
            scope = "outcome=Submitted",
        )
    ) {
        add(
            "executed PM evidence mismatch: telemetry counter missing " +
                "first_route.command_submission.count outcome=Submitted unit=count positive count",
        )
    }
}

private fun MutableList<String>.addMissingExecutedPMTelemetryCounterDiagnostic(
    telemetryLedger: GPUTelemetryLedger,
    name: String,
    scope: String,
) {
    if (!telemetryLedger.hasPositiveFirstRouteCounter(name = name, scope = scope)) {
        add(
            "executed PM evidence mismatch: telemetry counter missing " +
                "$name $scope unit=count positive count",
        )
    }
}

private fun GPUTelemetryLedger.hasPositiveFirstRouteCounter(name: String, scope: String): Boolean =
    counters.any { counter ->
        counter.name == name &&
            counter.scope == scope &&
            counter.value > 0L &&
            counter.unit == "count"
    }

/** Adds stable diagnostics for actual execution ids that do not exactly match recording ids. */
private fun MutableList<String>.addMembershipDiagnostics(
    label: String,
    actual: List<String>,
    expected: List<String>,
    expectedNoun: String,
) {
    val actualDistinct = actual.distinct()
    val actualSet = actualDistinct.toSet()
    val expectedSet = expected.toSet()
    val missing = expected.filterNot { value -> value in actualSet }
    val unexpected = actualDistinct.filterNot { value -> value in expectedSet }

    if (missing.isNotEmpty()) {
        add("executed PM evidence mismatch: $label missing $expectedNoun ${missing.joinToString(",")}")
    }
    if (unexpected.isNotEmpty()) {
        add("executed PM evidence mismatch: $label outside recording ${unexpected.joinToString(",")}")
    }
}

/** Adds stable diagnostics when positive executed PM evidence is still diagnostic-bearing. */
private fun MutableList<String>.addPositiveExecutedEvidenceDiagnostics(
    label: String,
    diagnosticCodes: List<String>,
) {
    val distinctCodes = diagnosticCodes.distinct()
    if (distinctCodes.isNotEmpty()) {
        add("positive executed evidence diagnostics: $label carries diagnostics ${distinctCodes.joinToString(",")}")
    }
}

/** Returns the first-route promotion evidence category represented by this dump entry. */
private fun GPUContractDump.Entry.firstRouteEvidenceCategory(): String? =
    when {
        concept == "NegativeCPUFallbackRefusal" -> "negative-cpu-fallback"
        concept == "UnsupportedRouteFamilyRefusal" && coversFirstRouteUnsupportedFamilies() ->
            "unsupported-route-refusals"
        ownerPackage == "commands" && concept == "NormalizedDrawCommand.FillRect" -> "command"
        ownerPackage == "analysis" && concept == "GPUDrawAnalysisRecord" -> "analysis"
        ownerPackage == "routing" && concept == "GPURouteDecision.Native" -> "route"
        ownerPackage == "materials" && concept == "GPUPaintPipelinePlan" -> "material"
        ownerPackage == "wgsl" && concept == "WGSLReflectionResult" -> "wgsl"
        ownerPackage == "payloads" && concept == "GPUPayloadGatherPlan" -> "payload"
        ownerPackage == "pipelines" && concept == "GPUPipelineKeyPreimage.Render" -> "pipeline-key"
        ownerPackage == "resources" && concept == "GPUResourceMaterializationDecision.Materialized" -> "resource-decision"
        ownerPackage == "execution" && concept == "GPUCommandSubmission.Submitted" -> "submission"
        ownerPackage == "execution" && concept == "GPUReadbackResult.Completed" -> "readback"
        ownerPackage == "telemetry" && concept == "GPUCacheTelemetry.pipeline" -> "pipeline-cache"
        ownerPackage == "telemetry" && concept == "GPUTelemetryLedger" -> "telemetry"
        else -> null
    }

/** Finds contradictory accepted/refused facts that cannot coexist in one promotion bundle. */
private fun List<GPUContractDump.Entry>.firstRouteConflictingEvidenceDiagnostics(): List<String> =
    firstRouteConflictRules.mapNotNull { rule ->
        val concepts = asSequence()
            .map { entry -> entry.concept }
            .filter { concept -> concept in rule.positiveConcepts || concept in rule.nonPositiveConcepts }
            .distinct()
            .sorted()
            .toList()
        val hasPositive = concepts.any { concept -> concept in rule.positiveConcepts }
        val hasNonPositive = concepts.any { concept -> concept in rule.nonPositiveConcepts }

        if (hasPositive && hasNonPositive) {
            "${rule.category} has conflicting positive and non-positive evidence: ${concepts.joinToString(", ")}"
        } else {
            null
        }
    }

/** Verifies synthetic evidence is visible as test-only evidence when exposed through PM helpers. */
private fun requireSyntheticEvidenceIsLabeled(
    name: String,
    entries: List<GPUContractDump.Entry>,
) {
    val containsCustomPositiveEvidence = entries.any { entry ->
        entry.firstRouteEvidenceCategory() in firstRoutePositiveEvidenceCategories
    }
    if (containsCustomPositiveEvidence) {
        require(
            name == "gpu-renderer-first-route-executed-pm-evidence" ||
                name.startsWith("synthetic-test-") ||
                name.startsWith("diagnostic-"),
        ) {
            "Custom first-route PM evidence entries must use a synthetic-test or diagnostic report name: $name"
        }
    }

    val containsSyntheticEvidence = entries.any { entry ->
        entry.detail.contains("synthetic", ignoreCase = true) ||
            entry.concept.contains("synthetic", ignoreCase = true)
    }
    if (containsSyntheticEvidence) {
        require(name.startsWith("synthetic-test-")) {
            "Synthetic/test PM evidence must use a synthetic-test report name: $name"
        }
    }
}

/** Builds stable incompleteness diagnostics for first-route PM evidence bundles. */
private fun firstRoutePMEvidenceDiagnostics(
    entries: List<GPUContractDump.Entry>,
    missingEvidence: List<String>,
): List<String> =
    buildList {
        if (missingEvidence.isEmpty()) return@buildList

        add("first-route PM evidence incomplete: ${missingEvidence.joinToString(", ")}")
        for (category in missingEvidence) {
            if (category == "unsupported-route-refusals") {
                addUnsupportedRouteFamilyRefusalDiagnostic(entries)
                continue
            }

            val requirement = firstRoutePMEvidenceRequirements.first { it.category == category }
            val foundConcepts = entries
                .filter { entry -> requirement.includesInDump(entry) }
                .map { entry -> entry.concept }
                .distinct()
            val expectedConcept = requirement.requiredConcept
            when {
                expectedConcept == null ->
                    add("$category evidence is missing")

                foundConcepts.isEmpty() ->
                    add("$category requires $expectedConcept")

                else ->
                    add("$category requires $expectedConcept but found ${foundConcepts.joinToString(", ")}")
            }
        }
    }

/** Lists the roadmap-required unsupported first-route families as evidence, not route activation. */
private fun firstRouteUnsupportedRouteFamilyRefusalDetail(): String =
    "unsupportedFamilies=${firstRouteUnsupportedFamilies.joinToString(",")} diagnostics=none"

/** Returns true when this refusal entry names every canonical unsupported first-route family. */
private fun GPUContractDump.Entry.coversFirstRouteUnsupportedFamilies(): Boolean =
    firstRouteUnsupportedFamilies.all { family -> family in unsupportedRouteFamiliesInDetail() }

/** Extracts the unsupported-family list from a structured refusal detail, if present. */
private fun GPUContractDump.Entry.unsupportedRouteFamiliesInDetail(): Set<String> =
    unsupportedFamiliesDetailRegex
        .find(detail)
        ?.groupValues
        ?.get(1)
        ?.split(',')
        ?.map { family -> family.trim() }
        ?.filter { family -> family.isNotEmpty() }
        ?.toSet()
        ?: emptySet()

/** Adds a stable diagnostic for present but incomplete unsupported-family refusal evidence. */
private fun MutableList<String>.addUnsupportedRouteFamilyRefusalDiagnostic(entries: List<GPUContractDump.Entry>) {
    val refusalEntries = entries.filter { entry -> entry.concept == "UnsupportedRouteFamilyRefusal" }
    if (refusalEntries.isEmpty()) {
        add("unsupported-route-refusals requires UnsupportedRouteFamilyRefusal")
        return
    }

    val presentFamilies = firstRouteUnsupportedFamilies
        .filter { family ->
            refusalEntries.any { entry -> family in entry.unsupportedRouteFamiliesInDetail() }
        }
    val missingFamilies = firstRouteUnsupportedFamilies.filterNot { family -> family in presentFamilies }
    val presentValue = if (presentFamilies.isEmpty()) {
        "none"
    } else {
        presentFamilies.joinToString(",")
    }
    add(
        "unsupported-route-refusals requires UnsupportedRouteFamilyRefusal covering canonical " +
            "first-route unsupported families; missing=${missingFamilies.joinToString(",")} present=$presentValue",
    )
}

/** Projects immutable recording and telemetry facts into validation-owned PM evidence entries. */
private fun firstRouteRecordingPMEvidenceEntries(
    recording: GPURecording,
    telemetryLedger: GPUTelemetryLedger,
    includePreExecutionRefusals: Boolean = true,
    includePreMaterializationWGSLReflection: Boolean = true,
): List<GPUContractDump.Entry> =
    buildList {
        val recordingId = recording.recordingId.value
        val records = recording.analysis.records

        records
            .groupBy { record -> record.commandFamily }
            .forEach { (family, familyRecords) ->
                add(
                    GPUContractDump.Entry(
                        ownerPackage = "commands",
                        concept = "NormalizedDrawCommand.$family",
                        detail = "recording $recordingId commandIds=${familyRecords.commandIds()} families=$family",
                    ),
                )
            }

        if (records.isNotEmpty()) {
            add(
                GPUContractDump.Entry(
                    ownerPackage = "analysis",
                    concept = "GPUDrawAnalysisRecord",
                    detail = "recording $recordingId records=${records.recordIds()} " +
                        "decisionHash=${recording.analysisDecisionDump.decisionHash}",
                ),
            )
        }

        recording.routeDiagnostics.forEach { diagnostic ->
            val concept = when {
                diagnostic.isNativeRouteDiagnostic() -> "GPURouteDecision.Native"
                diagnostic.startsWith("route:") -> "GPURouteDecision.Prepared"
                diagnostic.startsWith("refused:") -> "GPURouteDecision.Refused"
                diagnostic.startsWith("reference:") -> "GPURouteDecision.ReferenceOnly"
                else -> "GPURouteDecision.Refused"
            }
            add(
                GPUContractDump.Entry(
                    ownerPackage = "routing",
                    concept = concept,
                    detail = "recording $recordingId $diagnostic",
                ),
            )
        }

        if (recording.routeDiagnostics.any { diagnostic -> diagnostic.isNativeRouteDiagnostic() }) {
            add(
                GPUContractDump.Entry(
                    ownerPackage = "routing",
                    concept = "UnsupportedRouteFamilyRefusal",
                    detail = "recording $recordingId ${firstRouteUnsupportedRouteFamilyRefusalDetail()}",
                ),
            )
        }

        val renderTasks = recording.taskList.tasks.filterIsInstance<GPUTask.Render>()
        val renderAnalysisRecordIds = renderTasks.map { task -> task.analysisRecordId }.toSet()
        val materialKeyHashes = records
            .filter { record -> record.recordId in renderAnalysisRecordIds }
            .map { record -> record.materialKeyHash }
            .filterNot { hash -> hash == "none" }
            .distinct()
        val pipelineKeyHashes = renderTasks
            .flatMap { task -> task.pipelineKeyHashes }
            .distinct()

        if (materialKeyHashes.isNotEmpty()) {
            add(
                GPUContractDump.Entry(
                    ownerPackage = "materials",
                    concept = "GPUPaintPipelinePlan",
                    detail = "recording $recordingId materialKeyHashes=${materialKeyHashes.joinToString(",")}",
                ),
            )
        }
        if (pipelineKeyHashes.isNotEmpty()) {
            if (includePreMaterializationWGSLReflection) {
                add(
                    GPUContractDump.Entry(
                        ownerPackage = "wgsl",
                        concept = "WGSLReflectionResult",
                        detail = "recording $recordingId pre-materialization WGSL evidence for " +
                            "pipelineKeys=${pipelineKeyHashes.joinToString(",")}",
                    ),
                )
            }
            add(
                GPUContractDump.Entry(
                    ownerPackage = "payloads",
                    concept = "GPUPayloadGatherPlan",
                    detail = "recording $recordingId payload evidence for " +
                        "renderTasks=${renderTasks.joinToString(",") { task -> task.taskId.value }}",
                ),
            )
            add(
                GPUContractDump.Entry(
                    ownerPackage = "pipelines",
                    concept = "GPUPipelineKeyPreimage.Render",
                    detail = "recording $recordingId pipelineKeyHashes=${pipelineKeyHashes.joinToString(",")}",
                ),
            )
        }

        if (includePreExecutionRefusals) {
            add(
                GPUContractDump.Entry(
                    ownerPackage = "resources",
                    concept = "GPUResourceMaterializationDecision.Refused",
                    detail = "recording $recordingId pre-materialization resources are not materialized",
                ),
            )
            add(
                GPUContractDump.Entry(
                    ownerPackage = "execution",
                    concept = "GPUCommandSubmission.Refused",
                    detail = "recording $recordingId command submission is absent before execution",
                ),
            )
        }

        telemetryLedger.firstRouteCounterDumpLines().forEach { line ->
            add(
                GPUContractDump.Entry(
                    ownerPackage = "telemetry",
                    concept = "GPUTelemetryLedger",
                    detail = line,
                ),
            )
        }
        telemetryLedger.pipelineCacheEvidenceLines().forEach { line ->
            add(
                GPUContractDump.Entry(
                    ownerPackage = "telemetry",
                    concept = "GPUCacheTelemetry.pipeline",
                    detail = line,
                ),
            )
        }

        add(
            GPUContractDump.Entry(
                ownerPackage = "routing",
                concept = "NegativeCPUFallbackRefusal",
                detail = "recording $recordingId has no CPU-rendered texture fallback tasks",
            ),
        )

        recording.analysisDecisionDump.lines.forEach { line ->
            add(
                GPUContractDump.Entry(
                    ownerPackage = "recording",
                    concept = "GPUAnalysisDecisionDump",
                    detail = "recording $recordingId $line",
                ),
            )
        }
        recording.taskList.dumpLines().forEach { line ->
            add(
                GPUContractDump.Entry(
                    ownerPackage = "recording",
                    concept = "GPUTaskList",
                    detail = "recording $recordingId $line",
                ),
            )
        }
        recording.compatibilityKey.dump().lines.forEach { line ->
            add(
                GPUContractDump.Entry(
                    ownerPackage = "recording",
                    concept = "GPURecordingCompatibilityKey",
                    detail = "recording $recordingId $line",
                ),
            )
        }

        val replay = recording.checkReplayCompatibility(recording.compatibilityKey)
        add(
            when (replay) {
                is GPURecordingReplayResult.Replayable ->
                    GPUContractDump.Entry(
                        ownerPackage = "recording",
                        concept = "GPURecordingReplayResult.Replayable",
                        detail = "recording $recordingId replayable",
                    )
                is GPURecordingReplayResult.Refused ->
                    GPUContractDump.Entry(
                        ownerPackage = "recording",
                        concept = "GPURecordingReplayResult.Refused",
                        detail = "recording $recordingId ${replay.diagnostic.code}",
                    )
            },
        )
    }

/** Projects a materialization decision into one PM evidence entry without exposing resource handles. */
private fun GPUResourceMaterializationDecision.firstRoutePMEvidenceEntry(evidenceLabel: String): GPUContractDump.Entry =
    when (this) {
        is GPUResourceMaterializationDecision.Materialized ->
            GPUContractDump.Entry(
                ownerPackage = "resources",
                concept = "GPUResourceMaterializationDecision.Materialized",
                detail = "$evidenceLabel ${dumpLines().joinToString(" | ")}",
            )
        is GPUResourceMaterializationDecision.Deferred ->
            GPUContractDump.Entry(
                ownerPackage = "resources",
                concept = "GPUResourceMaterializationDecision.Deferred",
                detail = "$evidenceLabel ${dumpLines().joinToString(" | ")}",
            )
        is GPUResourceMaterializationDecision.Refused ->
            GPUContractDump.Entry(
                ownerPackage = "resources",
                concept = "GPUResourceMaterializationDecision.Refused",
                detail = "$evidenceLabel ${dumpLines().joinToString(" | ")}",
            )
    }

/** Projects a command-submission decision into PM evidence without changing execution status. */
private fun GPUCommandSubmission.firstRoutePMEvidenceEntry(evidenceLabel: String): GPUContractDump.Entry =
    when (this) {
        is GPUCommandSubmission.Submitted ->
            GPUContractDump.Entry(
                ownerPackage = "execution",
                concept = "GPUCommandSubmission.Submitted",
                detail = "$evidenceLabel ${dumpLines().joinToString(" | ")}",
            )
        is GPUCommandSubmission.Refused ->
            GPUContractDump.Entry(
                ownerPackage = "execution",
                concept = "GPUCommandSubmission.Refused",
                detail = "$evidenceLabel ${dumpLines().joinToString(" | ")}",
            )
        is GPUCommandSubmission.Failed ->
            GPUContractDump.Entry(
                ownerPackage = "execution",
                concept = "GPUCommandSubmission.Failed",
                detail = "$evidenceLabel ${dumpLines().joinToString(" | ")}",
            )
    }

/** Projects readback evidence; only `Completed` satisfies the R6 readback category. */
private fun GPUReadbackResult.firstRoutePMEvidenceEntry(evidenceLabel: String): GPUContractDump.Entry =
    when (this) {
        is GPUReadbackResult.Completed ->
            GPUContractDump.Entry(
                ownerPackage = "execution",
                concept = "GPUReadbackResult.Completed",
                detail = "$evidenceLabel ${dumpLines().joinToString(" | ")}",
            )
        is GPUReadbackResult.Skipped ->
            GPUContractDump.Entry(
                ownerPackage = "execution",
                concept = "GPUReadbackResult.Skipped",
                detail = "$evidenceLabel ${dumpLines().joinToString(" | ")}",
            )
        is GPUReadbackResult.Refused ->
            GPUContractDump.Entry(
                ownerPackage = "execution",
                concept = "GPUReadbackResult.Refused",
                detail = "$evidenceLabel ${dumpLines().joinToString(" | ")}",
            )
    }

/** Projects parser-backed WGSL reflection into explicit executed PM evidence. */
private fun WGSLReflectionResult.Accepted.firstRoutePMEvidenceEntry(
    evidenceLabel: String,
): GPUContractDump.Entry {
    val bindingFacts = bindings
        .sortedWith(compareBy({ it.group }, { it.binding }, { it.layoutRole }))
        .joinToString(",") { binding ->
            "group=${binding.group}/binding=${binding.binding}/role=${binding.layoutRole}/kind=${binding.resourceKind}"
        }
        .ifBlank { "none" }
    val uniformFacts = uniforms
        .sortedBy { uniform -> uniform.layoutHash }
        .joinToString(",") { uniform ->
            "${uniform.layoutHash}:${uniform.sizeBytes}B:${uniform.fields.joinToString("+")}"
        }
        .ifBlank { "none" }
    val diagnostics = diagnostics
        .joinToString(",") { diagnostic -> diagnostic.code }
        .ifBlank { "none" }

    return GPUContractDump.Entry(
        ownerPackage = "wgsl",
        concept = "WGSLReflectionResult",
        detail = "$evidenceLabel wgsl.reflection:accepted module=${moduleHash.value} " +
            "parserState=${parserState.status} tool=${parserState.toolName} source=$reflectionSource " +
            "bindings=$bindingFacts uniforms=$uniformFacts storageCount=${storage.size} diagnostics=$diagnostics",
    )
}

/** Returns deterministic command IDs for one command-family group. */
private fun List<org.graphiks.kanvas.gpu.renderer.analysis.GPUDrawAnalysisRecord>.commandIds(): String =
    joinToString(",") { record -> record.commandIdValue.toString() }

/** Returns deterministic analysis record IDs for diagnostics and PM details. */
private fun List<org.graphiks.kanvas.gpu.renderer.analysis.GPUDrawAnalysisRecord>.recordIds(): String =
    joinToString(",") { record -> record.recordId }

/** Emits pipeline-cache evidence only when the supplied telemetry ledger contains pipeline facts. */
private fun GPUTelemetryLedger.pipelineCacheEvidenceLines(): List<String> =
    buildList {
        cacheTelemetry
            .filter { telemetry -> telemetry.cacheName == "pipeline" && telemetry.hasObservedPipelineCacheFact() }
            .forEach { telemetry ->
                add(
                    "cache:pipeline:hits=${telemetry.hits}:misses=${telemetry.misses}:" +
                        "evictions=${telemetry.evictions}:residentBytes=${telemetry.residentBytes}:" +
                        "pressureBytes=${telemetry.pressureBytes}",
                )
            }
        cacheEvents
            .filter { event -> event.domain == "pipeline" }
            .forEach { event ->
                add("event:pipeline:${event.result.name}:key=${event.keyHash}:subject=${event.subjectHash}")
            }
    }

/** Returns true only for the explicit native route marker emitted by recording today. */
private fun String.isNativeRouteDiagnostic(): Boolean =
    this == "route:native" || startsWith("route:native.")

/** Returns true when aggregate pipeline telemetry carries at least one observed fact. */
private fun org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheTelemetry.hasObservedPipelineCacheFact(): Boolean =
    hits > 0L ||
        misses > 0L ||
        evictions > 0L ||
        residentBytes > 0L ||
        pressureBytes > 0L

/** Parsed facts needed by lightweight package-boundary validation. */
private data class GPUKotlinSource(
    val root: File,
    val file: File,
    val packageName: String?,
    val imports: List<String>,
) {
    val relativePath: String = file.relativeTo(root).path.replace(File.separatorChar, '/')
}

/** Returns all Kotlin source facts below this root. */
private fun File.kotlinSources(): List<GPUKotlinSource> {
    if (!exists()) return emptyList()

    return walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .map { source ->
            val lines = source.readLines()
            GPUKotlinSource(
                root = this,
                file = source,
                packageName = lines.firstNotNullOfOrNull { line -> packageRegex.find(line)?.groupValues?.get(1) },
                imports = lines.mapNotNull { line ->
                    importRegex.find(line)?.groupValues?.get(1)?.substringBefore(" as ")
                },
            )
        }
        .toList()
}

/** Finds ownership violations for one parsed Kotlin source file. */
private fun GPUKotlinSource.packageBoundaryViolations(): List<String> {
    val packageName = packageName
    val violations = mutableListOf<String>()

    if (packageName == null || !packageName.isCanonicalRendererPackage()) {
        violations += "$relativePath: canonical package root violation: ${packageName ?: "<missing>"}"
    }

    if (packageName?.hasReservedPackageSegment() == true) {
        violations += "$relativePath: reserved package segment violation: $packageName"
    }

    if (packageName != null && !packageName.startsWith("$canonicalRendererPackage.validation")) {
        imports
            .filter { it.startsWith("$canonicalRendererPackage.validation.") }
            .forEach { imported ->
                violations += "$relativePath: validation helper import violation: $imported"
            }
    }

    return violations
}

/** Finds dependency-rule violations for one parsed Kotlin source file. */
private fun GPUKotlinSource.dependencyRuleViolations(): List<String> {
    val sourcePackage = packageName ?: return emptyList()
    val sourceSegment = sourcePackage.rendererTopSegment() ?: return emptyList()

    return imports.mapNotNull { imported ->
        val targetSegment = imported.rendererTopSegment() ?: return@mapNotNull null
        when {
            sourceSegment in foundationPackageSegments && targetSegment in latePlanningPackageSegments ->
                "$relativePath: foundation package dependency violation: $sourceSegment imports $targetSegment"

            sourceSegment in domainPackageSegments && targetSegment == "execution" ->
                "$relativePath: domain package dependency violation: $sourceSegment imports execution"

            sourceSegment == "materials" &&
                targetSegment == "resources" &&
                imported.substringAfterLast('.') in concreteResourceTypes ->
                "$relativePath: materials concrete resource import violation: $imported"

            sourceSegment == "wgsl" && targetSegment in domainPackageSegments ->
                "$relativePath: wgsl domain semantics import violation: $imported"

            sourceSegment == "execution" && targetSegment in semanticPackageSegments ->
                "$relativePath: execution semantic package import violation: $imported"

            else -> null
        }
    }
}

/** Finds package-level import cycles among canonical renderer packages. */
private fun List<GPUKotlinSource>.packageCycleViolations(): List<String> {
    val packages = mapNotNull { it.packageName }
        .filter { it.isCanonicalRendererPackage() }
        .toSet()
    val edges = mutableMapOf<String, MutableSet<String>>()

    for (source in this) {
        val sourcePackage = source.packageName?.takeIf { it.isCanonicalRendererPackage() } ?: continue
        for (imported in source.imports) {
            val targetPackage = imported.targetRendererPackage(packages) ?: continue
            if (targetPackage != sourcePackage) {
                edges.getOrPut(sourcePackage) { mutableSetOf() } += targetPackage
            }
        }
    }

    return edges.keys
        .flatMap { source ->
            edges.getValue(source).mapNotNull { target ->
                if (target.reachesPackage(source, edges, mutableSetOf())) {
                    "package cycle violation: $source -> $target -> $source"
                } else {
                    null
                }
            }
        }
        .distinct()
        .sorted()
}

/** Returns true when this package can reach the requested target package. */
private fun String.reachesPackage(
    targetPackage: String,
    edges: Map<String, Set<String>>,
    visited: MutableSet<String>,
): Boolean {
    if (!visited.add(this)) return false

    return edges[this].orEmpty().any { next ->
        next == targetPackage || next.reachesPackage(targetPackage, edges, visited)
    }
}

/** Resolves an import to the most specific renderer package it targets. */
private fun String.targetRendererPackage(packages: Set<String>): String? =
    packages
        .filter { packageName -> this == packageName || startsWith("$packageName.") }
        .maxByOrNull { it.length }

/** Returns true when this package belongs to the canonical renderer root. */
private fun String.isCanonicalRendererPackage(): Boolean =
    this == canonicalRendererPackage || startsWith("$canonicalRendererPackage.")

/** Returns the first segment owned by the canonical renderer root. */
private fun String.rendererTopSegment(): String? {
    if (!isCanonicalRendererPackage()) return null
    if (this == canonicalRendererPackage) return null

    return removePrefix("$canonicalRendererPackage.")
        .substringBefore('.')
}

/** Returns true when this package uses a reserved implementation-oriented segment. */
private fun String.hasReservedPackageSegment(): Boolean =
    split('.').any { it in reservedPackageSegments }

/** Returns true when this import references Skia-like public API objects. */
private fun String.isSkiaLikeImport(): Boolean =
    startsWith("org.jetbrains.skia.") ||
        startsWith("org.graphiks.kanvas.skia.") ||
        substringAfterLast('.') in skiaLikePublicTypes

/** Returns true when this import references Graphite or Ganesh source namespaces. */
private fun String.isGraphiteOrGaneshImport(): Boolean =
    startsWith("skgpu.graphite.") ||
        startsWith("skgpu.ganesh.") ||
        contains(".graphite.") ||
        contains(".ganesh.")

private const val canonicalRendererPackage = "org.graphiks.kanvas.gpu.renderer"

private val reservedPackageSegments = setOf("webgpu", "graphite", "ganesh")

private val skiaLikePublicTypes = setOf("SkCanvas", "SkPaint", "SkShader", "SkPath")

private val foundationPackageSegments = setOf(
    "diagnostics",
    "telemetry",
    "capabilities",
    "state",
    "color",
    "coordinates",
)

private val domainPackageSegments = setOf(
    "commands",
    "materials",
    "runtimeeffects",
    "geometry",
    "vertices",
    "clips",
    "destination",
    "layers",
    "filters",
    "images",
    "text",
)

private val latePlanningPackageSegments = setOf("recording", "passes", "resources", "execution")

private val semanticPackageSegments = domainPackageSegments - "commands"

private val concreteResourceTypes = setOf(
    "GPUTextureResourceRef",
    "GPUSurfaceTextureLease",
    "GPUSampledTextureBinding",
    "GPUScratchResourceToken",
    "GPUIntermediateResourceToken",
)

private data class FirstRoutePMEvidenceRequirement(
    val category: String,
    val requiredConcept: String?,
    val includesInDump: (GPUContractDump.Entry) -> Boolean,
)

private data class FirstRouteConflictRule(
    val category: String,
    val positiveConcepts: Set<String>,
    val nonPositiveConcepts: Set<String>,
)

private val firstRoutePositiveEvidenceCategories = setOf(
    "resource-decision",
    "submission",
    "readback",
)

private val firstRouteUnsupportedFamilies = listOf(
    "perspective-transform",
    "singular-transform",
    "rrect-scale-transform",
    "rrect-affine-transform",
    "unsupported-target-format",
    "unsupported-blend",
    "non-simple-clip",
    "layer-filter-destination-read",
    "missing-capability",
    "wgsl-validation-or-abi-mismatch",
)

private val unsupportedFamiliesDetailRegex = Regex("""(?:^|\s)unsupportedFamilies=([^\s]+)""")

private val firstRouteConflictRules = listOf(
    FirstRouteConflictRule(
        category = "route",
        positiveConcepts = setOf("GPURouteDecision.Native"),
        nonPositiveConcepts = setOf(
            "GPURouteDecision.Refused",
            "GPURouteDecision.Prepared",
            "GPURouteDecision.ReferenceOnly",
        ),
    ),
    FirstRouteConflictRule(
        category = "resource-decision",
        positiveConcepts = setOf("GPUResourceMaterializationDecision.Materialized"),
        nonPositiveConcepts = setOf(
            "GPUResourceMaterializationDecision.Deferred",
            "GPUResourceMaterializationDecision.Refused",
        ),
    ),
    FirstRouteConflictRule(
        category = "submission",
        positiveConcepts = setOf("GPUCommandSubmission.Submitted"),
        nonPositiveConcepts = setOf("GPUCommandSubmission.Failed", "GPUCommandSubmission.Refused"),
    ),
    FirstRouteConflictRule(
        category = "readback",
        positiveConcepts = setOf("GPUReadbackResult.Completed"),
        nonPositiveConcepts = setOf("GPUReadbackResult.Refused", "GPUReadbackResult.Skipped"),
    ),
)

private val firstRoutePMEvidenceRequirements = listOf(
    FirstRoutePMEvidenceRequirement(
        category = "command",
        requiredConcept = "NormalizedDrawCommand.FillRect",
        includesInDump = { entry -> entry.ownerPackage == "commands" },
    ),
    FirstRoutePMEvidenceRequirement(
        category = "analysis",
        requiredConcept = "GPUDrawAnalysisRecord",
        includesInDump = { entry -> entry.ownerPackage == "analysis" },
    ),
    FirstRoutePMEvidenceRequirement(
        category = "route",
        requiredConcept = "GPURouteDecision.Native",
        includesInDump = { entry ->
            entry.ownerPackage == "routing" &&
                entry.concept != "NegativeCPUFallbackRefusal" &&
                entry.concept != "UnsupportedRouteFamilyRefusal"
        },
    ),
    FirstRoutePMEvidenceRequirement(
        category = "material",
        requiredConcept = "GPUPaintPipelinePlan",
        includesInDump = { entry -> entry.ownerPackage == "materials" },
    ),
    FirstRoutePMEvidenceRequirement(
        category = "wgsl",
        requiredConcept = "WGSLReflectionResult",
        includesInDump = { entry -> entry.ownerPackage == "wgsl" },
    ),
    FirstRoutePMEvidenceRequirement(
        category = "payload",
        requiredConcept = "GPUPayloadGatherPlan",
        includesInDump = { entry -> entry.ownerPackage == "payloads" },
    ),
    FirstRoutePMEvidenceRequirement(
        category = "pipeline-key",
        requiredConcept = "GPUPipelineKeyPreimage.Render",
        includesInDump = { entry -> entry.ownerPackage == "pipelines" },
    ),
    FirstRoutePMEvidenceRequirement(
        category = "resource-decision",
        requiredConcept = "GPUResourceMaterializationDecision.Materialized",
        includesInDump = { entry -> entry.ownerPackage == "resources" },
    ),
    FirstRoutePMEvidenceRequirement(
        category = "submission",
        requiredConcept = "GPUCommandSubmission.Submitted",
        includesInDump = { entry ->
            entry.ownerPackage == "execution" && entry.concept.startsWith("GPUCommandSubmission.")
        },
    ),
    FirstRoutePMEvidenceRequirement(
        category = "readback",
        requiredConcept = "GPUReadbackResult.Completed",
        includesInDump = { entry ->
            entry.ownerPackage == "execution" && entry.concept.startsWith("GPUReadbackResult.")
        },
    ),
    FirstRoutePMEvidenceRequirement(
        category = "telemetry",
        requiredConcept = "GPUTelemetryLedger",
        includesInDump = { entry -> entry.ownerPackage == "telemetry" },
    ),
    FirstRoutePMEvidenceRequirement(
        category = "pipeline-cache",
        requiredConcept = "GPUCacheTelemetry.pipeline",
        includesInDump = { entry -> entry.ownerPackage == "telemetry" && entry.concept == "GPUCacheTelemetry.pipeline" },
    ),
    FirstRoutePMEvidenceRequirement(
        category = "negative-cpu-fallback",
        requiredConcept = "NegativeCPUFallbackRefusal",
        includesInDump = { entry -> entry.concept == "NegativeCPUFallbackRefusal" },
    ),
    FirstRoutePMEvidenceRequirement(
        category = "unsupported-route-refusals",
        requiredConcept = "UnsupportedRouteFamilyRefusal",
        includesInDump = { entry -> entry.concept == "UnsupportedRouteFamilyRefusal" },
    ),
)

private val firstRouteRecordingEvidenceRequirements = listOf(
    FirstRoutePMEvidenceRequirement(
        category = "recording-analysis",
        requiredConcept = "GPUAnalysisDecisionDump",
        includesInDump = { entry ->
            entry.ownerPackage == "recording" && entry.concept == "GPUAnalysisDecisionDump"
        },
    ),
    FirstRoutePMEvidenceRequirement(
        category = "recording-task-list",
        requiredConcept = "GPUTaskList",
        includesInDump = { entry ->
            entry.ownerPackage == "recording" && entry.concept == "GPUTaskList"
        },
    ),
    FirstRoutePMEvidenceRequirement(
        category = "recording-compatibility",
        requiredConcept = "GPURecordingCompatibilityKey",
        includesInDump = { entry ->
            entry.ownerPackage == "recording" && entry.concept == "GPURecordingCompatibilityKey"
        },
    ),
    FirstRoutePMEvidenceRequirement(
        category = "recording-replay",
        requiredConcept = null,
        includesInDump = { entry ->
            entry.ownerPackage == "recording" && entry.concept.startsWith("GPURecordingReplayResult.")
        },
    ),
)

private val firstRoutePromotionEvidenceCategories =
    firstRoutePMEvidenceRequirements.map { requirement -> requirement.category }

private val packageRegex = Regex("""^\s*package\s+([A-Za-z0-9_.]+)""")

private val importRegex = Regex("""^\s*import\s+([^\s]+)""")
