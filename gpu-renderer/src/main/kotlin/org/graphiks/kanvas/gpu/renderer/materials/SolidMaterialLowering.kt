package org.graphiks.kanvas.gpu.renderer.materials

import java.security.MessageDigest

/** Deterministic material-key preimage for solid source plans. */
data class MaterialKeyPreimage(
    val sourceKind: GPUMaterialSourceKind,
    val snippetId: WGSLSnippetID,
    val dictionaryVersion: String,
    val uniformLayoutHash: String,
    val uniformLayoutLabel: String,
    val payloadFields: List<String>,
    val codeShapeFacts: List<String>,
    val featureFlags: List<String>,
) {
    /** Emits a stable text dump for PM and contract fixtures. */
    fun dump(): String =
        buildList {
            add("sourceKind=$sourceKind")
            add("snippet=${snippetId.value}")
            add("dictionaryVersion=$dictionaryVersion")
            add("uniformLayout=$uniformLayoutLabel")
            add("uniformLayoutHash=$uniformLayoutHash")
            payloadFields.forEach { payloadField -> add("payloadField=$payloadField") }
            codeShapeFacts.forEach { fact -> add("codeShape=$fact") }
            featureFlags.forEach { flag -> add("featureFlag=$flag") }
        }.joinToString("\n")
}

/** First-slice solid material dictionary and snippet graph fixture. */
object GPUSolidMaterialDictionary {
    /** Built-in dictionary version for the first solid material slice. */
    const val DictionaryVersion: String = "material-dictionary:solid:v1"

    /** Stable snippet ID for the built-in solid color material source. */
    val SolidColorSnippetID: WGSLSnippetID = WGSLSnippetID("material.solid_color.v1")

    /** Stable uniform layout hash for SolidMaterialBlock. */
    const val SolidMaterialLayoutHash: String = "layout:solid-material-block:v1"

    /** Stable module salt for the first solid material WGSL module. */
    const val SolidMaterialModuleSalt: String = "kanvas-gpu-renderer:solid-material:v1"

    /** Creates the built-in first-slice material dictionary. */
    fun create(): GPUMaterialDictionary =
        GPUMaterialDictionary(
            dictionaryVersion = DictionaryVersion,
            snippets = listOf(solidColorSnippet()),
            rootSets = listOf(solidColorRootSet()),
        )

    /** Expands a solid material key into a deterministic material assembly result. */
    fun expandSolidMaterialOrRefuse(
        materialKey: MaterialKey,
        dictionary: GPUMaterialDictionary,
    ): GPUMaterialAssemblyResult {
        val diagnostic = validateSolidDictionary(dictionary)
        if (diagnostic != null) {
            return GPUMaterialAssemblyResult.Refused(diagnostic)
        }

        return GPUMaterialAssemblyResult.Accepted(
            GPUMaterialAssemblyPlan(
                programId = GPUMaterialProgramID("program:${materialKey.value}"),
                rootSet = dictionary.rootSets.single { SolidColorSnippetID in it.snippetIds },
                snippetGraph = listOf(
                    WGSLSnippetNode(
                        snippetId = SolidColorSnippetID,
                        children = emptyList(),
                        evaluationOrder = 0,
                    ),
                ),
                moduleSalt = SolidMaterialModuleSalt,
            ),
        )
    }

    /** Expands a valid solid material dictionary or fails with the stable refusal code in the exception text. */
    fun expandSolidMaterial(
        materialKey: MaterialKey,
        dictionary: GPUMaterialDictionary,
    ): GPUMaterialAssemblyPlan =
        when (val result = expandSolidMaterialOrRefuse(materialKey, dictionary)) {
            is GPUMaterialAssemblyResult.Accepted -> result.plan
            is GPUMaterialAssemblyResult.Refused -> error("${result.diagnostic.code}: ${result.diagnostic.message}")
        }

    /** Validates the built-in solid dictionary before expansion. */
    fun validateSolidDictionary(dictionary: GPUMaterialDictionary): GPUMaterialSourceDiagnostic? {
        if (dictionary.dictionaryVersion != DictionaryVersion) {
            return GPUMaterialSourceDiagnostic(
                code = "unsupported.material.dictionary_version_mismatch",
                sourceKind = GPUMaterialSourceKind.SolidColor,
                message = "Solid material dictionary version ${dictionary.dictionaryVersion} does not match $DictionaryVersion",
                terminal = true,
            )
        }
        if (dictionary.snippets.none { it.snippetId == SolidColorSnippetID }) {
            return GPUMaterialSourceDiagnostic(
                code = "unsupported.material.dictionary_missing_snippet",
                sourceKind = GPUMaterialSourceKind.SolidColor,
                message = "Solid material dictionary is missing snippet ${SolidColorSnippetID.value}",
                terminal = true,
            )
        }
        if (dictionary.rootSets.none { SolidColorSnippetID in it.snippetIds }) {
            return GPUMaterialSourceDiagnostic(
                code = "unsupported.material.dictionary_missing_root_set",
                sourceKind = GPUMaterialSourceKind.SolidColor,
                message = "Solid material dictionary is missing a root set for ${SolidColorSnippetID.value}",
                terminal = true,
            )
        }

        return null
    }

    /** Validates a material-owned WGSL snippet graph before module assembly. */
    fun validateSnippetGraph(nodes: List<WGSLSnippetNode>): GPUMaterialSourceDiagnostic? {
        val nodeIds = nodes.map { it.snippetId }.toSet()
        val missingChild = nodes
            .flatMap { node -> node.children.map { child -> node.snippetId to child } }
            .firstOrNull { (_, child) -> child !in nodeIds }

        if (missingChild != null) {
            return GPUMaterialSourceDiagnostic(
                code = "unsupported.material.snippet_unknown",
                sourceKind = GPUMaterialSourceKind.SolidColor,
                message = "Snippet ${missingChild.first.value} references unknown child ${missingChild.second.value}",
                terminal = true,
            )
        }

        val graph = nodes.associate { it.snippetId to it.children }
        val cyclicNode = nodes.firstOrNull { node ->
            node.snippetId.reachesItself(graph, visited = mutableSetOf(), stack = mutableSetOf())
        }

        return if (cyclicNode == null) {
            null
        } else {
            GPUMaterialSourceDiagnostic(
                code = "unsupported.material.snippet_cycle",
                sourceKind = GPUMaterialSourceKind.SolidColor,
                message = "Snippet graph contains a cycle at ${cyclicNode.snippetId.value}",
                terminal = true,
            )
        }
    }

    /** Creates the built-in solid color snippet metadata. */
    private fun solidColorSnippet(): WGSLSnippet =
        WGSLSnippet(
            snippetId = SolidColorSnippetID,
            sourceHash = "fragment:material.solid_color:v1",
            entryPoint = "solid_source",
            requiredBindings = listOf("group1.binding0.SolidMaterialBlock"),
            category = "material-source",
            version = "v1",
            uniformLayoutHashes = listOf(SolidMaterialLayoutHash),
            requiredFeatures = emptyList(),
        )

    /** Creates the built-in solid color source root. */
    private fun solidColorRootSet(): GPUMaterialRootSet =
        GPUMaterialRootSet(
            rootSetId = "sourceRoot:solid-color",
            snippetIds = listOf(SolidColorSnippetID),
            payloadShapeHash = "payload:SolidMaterialBlock.color.vec4f32@group1.binding0",
        )
}

/** First-slice solid material lowering and MaterialKey derivation. */
object GPUSolidMaterialLowering {
    /** Lowers a complete paint descriptor with a solid source into a paint pipeline plan. */
    fun planPaint(
        descriptor: GPUPaintDescriptor,
        context: GPUMaterialLoweringContext,
    ): GPUPaintPipelinePlan {
        val sourcePlan = planSource(descriptor.source, context)
        val materialKey = when (sourcePlan) {
            is GPUMaterialSourcePlan.Accepted -> deriveMaterialKey(sourcePlan, context)
            is GPUMaterialSourcePlan.Refused -> MaterialKey("refused:${sourcePlan.diagnostic.code}")
        }

        return GPUPaintPipelinePlan(
            paint = descriptor,
            evaluationOrder = GPUPaintEvaluationOrder.SourceThenCoverage,
            stages = listOf(GPUPaintStagePlan.Material(sourcePlan)),
            materialKey = materialKey,
            diagnostics = emptyList(),
        )
    }

    /** Lowers a material source descriptor into the first-slice solid source plan. */
    fun planSource(
        source: GPUMaterialSourceDescriptor,
        context: GPUMaterialLoweringContext,
    ): GPUMaterialSourcePlan =
        when (source) {
            is GPUMaterialSourceDescriptor.Solid -> source.planSolid(context)
            else -> GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material_source.unknown",
                    sourceKind = source.kind,
                    message = "Only finite solid material sources are accepted by P0-C",
                    terminal = true,
                ),
            )
        }

    /** Derives a deterministic MaterialKey from accepted solid source layout and code-shape facts. */
    fun deriveMaterialKey(
        accepted: GPUMaterialSourcePlan.Accepted,
        context: GPUMaterialLoweringContext,
    ): MaterialKey {
        val source = accepted.source as? GPUMaterialSourceDescriptor.Solid
            ?: error("P0-C MaterialKey derivation only accepts solid source plans")
        val preimage = solidMaterialKeyPreimage(
            context = context,
            colorSpecLabel = source.plan.colorSpecLabel,
        )

        return MaterialKey("material:${source.plan.colorSpecLabel}:${preimage.dump().stableHash()}")
    }

    /** Reconstructs the deterministic solid MaterialKey preimage used by first-slice fixtures. */
    fun materialKeyPreimage(
        materialKey: MaterialKey,
        context: GPUMaterialLoweringContext,
    ): MaterialKeyPreimage {
        require(materialKey.value.startsWith("material:")) { "Expected a material key, got ${materialKey.value}" }

        val colorSpecLabel = materialKey.value
            .removePrefix("material:")
            .substringBeforeLast(":", missingDelimiterValue = "")
        require(colorSpecLabel.isNotBlank()) { "Material key must encode colorSpecLabel: ${materialKey.value}" }

        return solidMaterialKeyPreimage(
            context = context,
            colorSpecLabel = colorSpecLabel,
        )
    }

    /** Accepts a finite solid color source without including concrete RGBA values in key facts. */
    private fun GPUMaterialSourceDescriptor.Solid.planSolid(
        context: GPUMaterialLoweringContext,
    ): GPUMaterialSourcePlan {
        val channels = listOf(plan.r, plan.g, plan.b, plan.a)
        if (channels.any { !it.isFinite() }) {
            return GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.solid.non_finite",
                    sourceKind = GPUMaterialSourceKind.SolidColor,
                    message = "Solid color channels must be finite",
                    terminal = true,
                ),
            )
        }

        if (context.dictionaryVersion != GPUSolidMaterialDictionary.DictionaryVersion) {
            return GPUMaterialSourcePlan.Refused(
                GPUMaterialSourceDiagnostic(
                    code = "unsupported.material.dictionary_version_mismatch",
                    sourceKind = GPUMaterialSourceKind.SolidColor,
                    message = "Solid material requires ${GPUSolidMaterialDictionary.DictionaryVersion}",
                    terminal = true,
                ),
            )
        }

        return GPUMaterialSourcePlan.Accepted(
            source = this,
            snippetId = GPUSolidMaterialDictionary.SolidColorSnippetID,
            payloadPlanHash = "payload:SolidMaterialBlock.color.vec4f32@group1.binding0",
            diagnostics = listOf(
                GPUMaterialSourceDiagnostic(
                    code = "accepted.material_source.solid_color",
                    sourceKind = GPUMaterialSourceKind.SolidColor,
                    message = "Solid color source accepted as uniform payload",
                    terminal = false,
                ),
            ),
        )
    }
}

/** Creates the solid material key preimage from layout and code-shape facts. */
private fun solidMaterialKeyPreimage(
    context: GPUMaterialLoweringContext,
    colorSpecLabel: String,
): MaterialKeyPreimage =
    MaterialKeyPreimage(
        sourceKind = GPUMaterialSourceKind.SolidColor,
        snippetId = GPUSolidMaterialDictionary.SolidColorSnippetID,
        dictionaryVersion = context.dictionaryVersion,
        uniformLayoutHash = GPUSolidMaterialDictionary.SolidMaterialLayoutHash,
        uniformLayoutLabel = "SolidMaterialBlock(color:vec4<f32>)",
        payloadFields = listOf("color@group1.binding0.offset0.vec4<f32>"),
        codeShapeFacts = listOf(
            "sourceFunction=solid_source",
            "colorSpec=$colorSpecLabel",
            "payloadBlock=SolidMaterialBlock",
        ),
        featureFlags = listOf("solid-material-abi-v1"),
    )

/** Returns true when this snippet can reach itself through child edges. */
private fun WGSLSnippetID.reachesItself(
    graph: Map<WGSLSnippetID, List<WGSLSnippetID>>,
    visited: MutableSet<WGSLSnippetID>,
    stack: MutableSet<WGSLSnippetID>,
): Boolean {
    if (this in stack) return true
    if (!visited.add(this)) return false

    stack += this
    val cyclic = graph[this].orEmpty().any { child ->
        child.reachesItself(graph, visited, stack)
    }
    stack -= this

    return cyclic
}

/** Creates a short stable hash for material key fixtures. */
private fun String.stableHash(): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    return digest.take(16)
}
