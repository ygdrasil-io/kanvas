package org.graphiks.kanvas.gpu.renderer.validation

import java.io.File

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
                    concept = "GPUMaterialDescriptor.SolidColor",
                    detail = "first-slice material descriptor",
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
}

/** Validation report emitted by tests or PM evidence tooling. */
data class GPUValidationReport(
    val name: String,
    val status: GPUValidationStatus,
    val dumps: List<GPUContractDump>,
    val diagnostics: List<String> = emptyList(),
)

/** Validation status for PM evidence and contract gates. */
enum class GPUValidationStatus {
    /** Evidence satisfies the target gate. */
    Passed,
    /** Evidence fails the target gate. */
    Failed,
    /** Evidence is incomplete for the target gate. */
    Incomplete,
}

/** Deterministic dump of renderer concept ownership evidence. */
data class GPUContractDump(
    val name: String,
    val entries: List<Entry>,
) {
    /** One deterministic concept-ownership row in a contract dump. */
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

/** Promotion-gate result produced from validation evidence. */
data class GPUValidationGateResult(
    val gateName: String,
    val passed: Boolean,
    val missingEvidence: List<String> = emptyList(),
    val diagnostics: List<String> = emptyList(),
)

/** Validation check for promotion-gate evidence. */
class GPUPromotionGateCheck {
    /** Evaluates promotion evidence without inventing fake acceptance. */
    fun evaluate(report: GPUValidationReport): GPUValidationGateResult =
        TODO("Wire GPUPromotionGateCheck to concrete PM evidence gates for ${report.name}")
}

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

private val packageRegex = Regex("""^\s*package\s+([A-Za-z0-9_.]+)""")

private val importRegex = Regex("""^\s*import\s+([^\s]+)""")
