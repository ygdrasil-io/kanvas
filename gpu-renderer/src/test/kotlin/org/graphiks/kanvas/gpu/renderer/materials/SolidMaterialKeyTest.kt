package org.graphiks.kanvas.gpu.renderer.materials

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.fail

/** Verifies first-slice solid material lowering, keys, dictionary, and snippet graph diagnostics. */
class SolidMaterialKeyTest {
    /** Solid material lowering accepts finite RGBA values while making color a payload fact. */
    @Test
    fun `solid material lowering records material source and payload ABI`() {
        val plan = GPUSolidMaterialLowering.planPaint(
            descriptor = solidPaint(id = "paint-solid-a", r = 1f, g = 0.25f, b = 0.5f, a = 1f),
            context = loweringContext(),
        )

        assertEquals(GPUPaintEvaluationOrder.SourceThenCoverage, plan.evaluationOrder)
        val materialStage = plan.stages.single() as? GPUPaintStagePlan.Material
            ?: fail("Expected a material paint stage")
        val accepted = materialStage.sourcePlan as? GPUMaterialSourcePlan.Accepted
            ?: fail("Expected solid material source to be accepted")

        assertEquals(GPUMaterialSourceKind.SolidColor, accepted.source.kind)
        assertEquals(GPUSolidMaterialDictionary.SolidColorSnippetID, accepted.snippetId)
        assertEquals("payload:SolidMaterialBlock.color.vec4f32@group1.binding0", accepted.payloadPlanHash)
        assertContains(accepted.diagnostics.map { it.code }, "accepted.material_source.solid_color")
        assertEquals(
            GPUSolidMaterialLowering.deriveMaterialKey(accepted, loweringContext()),
            plan.materialKey,
        )
    }

    /** Solid material keys include layout/code shape facts but exclude concrete RGBA payload values. */
    @Test
    fun `material key is equivalent when only solid color payload values change`() {
        val red = GPUSolidMaterialLowering.planPaint(
            descriptor = solidPaint(id = "paint-red", r = 1f, g = 0f, b = 0f, a = 1f),
            context = loweringContext(),
        )
        val transparentBlue = GPUSolidMaterialLowering.planPaint(
            descriptor = solidPaint(id = "paint-blue", r = 0f, g = 0f, b = 1f, a = 0.125f),
            context = loweringContext(),
        )

        assertEquals(red.materialKey, transparentBlue.materialKey)

        val preimageDump = GPUSolidMaterialLowering
            .materialKeyPreimage(red.materialKey, loweringContext())
            .dump()

        assertContains(preimageDump, "sourceKind=SolidColor")
        assertContains(preimageDump, "snippet=material.solid_color.v1")
        assertContains(preimageDump, "uniformLayout=SolidMaterialBlock(color:vec4<f32>)")
        assertContains(preimageDump, "payloadField=color@group1.binding0.offset0.vec4<f32>")
        assertFalse(preimageDump.contains("1.0"), "RGBA value 1.0 must not enter the key preimage")
        assertFalse(preimageDump.contains("0.125"), "RGBA alpha payload must not enter the key preimage")
    }

    /** Material key dumps must reflect the same color-spec code-shape fact used by key derivation. */
    @Test
    fun `material key preimage reflects non default color spec`() {
        val wideGamut = GPUSolidMaterialLowering.planPaint(
            descriptor = solidPaint(
                id = "paint-display-p3",
                r = 0.4f,
                g = 0.5f,
                b = 0.6f,
                a = 1f,
                colorSpecLabel = "display-p3-f32",
            ),
            context = loweringContext(),
        )

        val preimageDump = GPUSolidMaterialLowering
            .materialKeyPreimage(wideGamut.materialKey, loweringContext())
            .dump()

        assertContains(preimageDump, "codeShape=colorSpec=display-p3-f32")
    }

    /** Solid material dictionary expands the key into a deterministic source root and snippet graph. */
    @Test
    fun `solid material dictionary expands key into source root set`() {
        val dictionary = GPUSolidMaterialDictionary.create()
        val key = GPUSolidMaterialLowering
            .planPaint(solidPaint(id = "paint-solid", r = 0.1f, g = 0.2f, b = 0.3f, a = 0.4f), loweringContext())
            .materialKey

        val assembly = GPUSolidMaterialDictionary.expandSolidMaterial(
            materialKey = key,
            dictionary = dictionary,
        )

        assertEquals(GPUSolidMaterialDictionary.DictionaryVersion, dictionary.dictionaryVersion)
        assertEquals(listOf(GPUSolidMaterialDictionary.SolidColorSnippetID), dictionary.rootSets.single().snippetIds)
        assertEquals("sourceRoot:solid-color", assembly.rootSet.rootSetId)
        assertEquals("kanvas-gpu-renderer:solid-material:v1", assembly.moduleSalt)
        assertEquals("program:${key.value}", assembly.programId.value)
        assertEquals(listOf(GPUSolidMaterialDictionary.SolidColorSnippetID), assembly.snippetGraph.map { it.snippetId })
        assertEquals("material-source", dictionary.snippets.single().category)
    }

    /** Malformed material dictionaries must refuse instead of synthesizing fallback root sets. */
    @Test
    fun `solid material dictionary refuses missing root set and snippets`() {
        val key = GPUSolidMaterialLowering
            .planPaint(solidPaint(id = "paint-solid", r = 0.1f, g = 0.2f, b = 0.3f, a = 0.4f), loweringContext())
            .materialKey
        val malformed = GPUMaterialDictionary(
            dictionaryVersion = GPUSolidMaterialDictionary.DictionaryVersion,
            snippets = emptyList(),
            rootSets = emptyList(),
        )

        val result = GPUSolidMaterialDictionary.expandSolidMaterialOrRefuse(
            materialKey = key,
            dictionary = malformed,
        )

        val refused = assertIs<GPUMaterialAssemblyResult.Refused>(result)
        assertEquals("unsupported.material.dictionary_missing_snippet", refused.diagnostic.code)
    }

    /** Solid source lowering refuses non-finite color payloads with a stable source diagnostic. */
    @Test
    fun `solid material lowering rejects non finite color channels`() {
        val result = GPUSolidMaterialLowering.planSource(
            source = GPUMaterialSourceDescriptor.Solid(
                GPUSolidColorPlan(
                    r = Float.NaN,
                    g = 0f,
                    b = 0f,
                    a = 1f,
                    colorSpecLabel = "unpremul-srgb-f32",
                ),
            ),
            context = loweringContext(),
        )

        val refused = result as? GPUMaterialSourcePlan.Refused
            ?: fail("Expected non-finite solid color to be refused")

        assertEquals("unsupported.solid.non_finite", refused.diagnostic.code)
        assertEquals(GPUMaterialSourceKind.SolidColor, refused.diagnostic.sourceKind)
        assertEquals(true, refused.diagnostic.terminal)
    }

    /** Snippet graph validation rejects recursive material graphs before WGSL assembly. */
    @Test
    fun `snippet graph cycle is rejected with stable diagnostic`() {
        val diagnostic = GPUSolidMaterialDictionary.validateSnippetGraph(
            listOf(
                WGSLSnippetNode(
                    snippetId = WGSLSnippetID("material.test.a"),
                    children = listOf(WGSLSnippetID("material.test.b")),
                    evaluationOrder = 0,
                ),
                WGSLSnippetNode(
                    snippetId = WGSLSnippetID("material.test.b"),
                    children = listOf(WGSLSnippetID("material.test.a")),
                    evaluationOrder = 1,
                ),
            ),
        )

        assertNotNull(diagnostic)
        assertEquals("unsupported.material.snippet_cycle", diagnostic.code)
        assertEquals(true, diagnostic.terminal)
    }

    /** Creates a solid paint descriptor for material-owned tests. */
    private fun solidPaint(
        id: String,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
        colorSpecLabel: String = "unpremul-srgb-f32",
    ): GPUPaintDescriptor =
        GPUPaintDescriptor(
            paintId = id,
            source = GPUMaterialSourceDescriptor.Solid(
                GPUSolidColorPlan(
                    r = r,
                    g = g,
                    b = b,
                    a = a,
                    colorSpecLabel = colorSpecLabel,
                ),
            ),
            blendModeLabel = "SrcOver",
            alpha = a,
            colorSpaceLabel = "srgb",
        )

    /** Creates the first-slice solid material lowering context. */
    private fun loweringContext(): GPUMaterialLoweringContext =
        GPUMaterialLoweringContext(
            capabilityClass = "first-route-solid",
            targetFormatClass = "rgba8unorm",
            dictionaryVersion = GPUSolidMaterialDictionary.DictionaryVersion,
        )
}
