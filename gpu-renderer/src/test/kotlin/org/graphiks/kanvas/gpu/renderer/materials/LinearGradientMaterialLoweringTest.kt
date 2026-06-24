package org.graphiks.kanvas.gpu.renderer.materials

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LinearGradientMaterialLoweringTest {

    @Test
    fun `linear gradient material lowering accepts valid gradient source`() {
        val source = GPUMaterialSourceDescriptor.Gradient(
            plan = GPUGradientPlan(
                geometry = GPUGradientGeometryPlan(
                    kind = GPUGradientKind.Linear,
                    controlPoints = listOf(0f, 0f, 100f, 100f),
                ),
                stops = listOf(
                    GPUGradientStopPlan(offset = 0f, colorLabel = "red"),
                    GPUGradientStopPlan(offset = 1f, colorLabel = "blue"),
                ),
                stopStore = GPUGradientStopStorePlan(
                    stopCount = 2,
                    storageKind = "uniform",
                    payloadHash = "gradient-stop-payload-v1",
                ),
                tileMode = GPUMaterialTileMode.Clamp,
            ),
        )
        val context = GPUMaterialLoweringContext(
            capabilityClass = "test-capability",
            targetFormatClass = "rgba8unorm",
            dictionaryVersion = GPULinearGradientMaterialDictionary.DictionaryVersion,
        )

        val plan = GPULinearGradientMaterialLowering.planPaint(
            descriptor = GPUPaintDescriptor(
                paintId = "paint-1",
                source = source,
                blendModeLabel = "src_over",
                alpha = 1f,
                colorSpaceLabel = "srgb",
            ),
            context = context,
        )

        assertTrue(plan.materialKey.value.startsWith("material:linear_gradient:"))
        assertEquals(GPUPaintEvaluationOrder.SourceThenCoverage, plan.evaluationOrder)
    }

    @Test
    fun `linear gradient material source plan accepts valid gradient`() {
        val source = GPUMaterialSourceDescriptor.Gradient(
            plan = GPUGradientPlan(
                geometry = GPUGradientGeometryPlan(
                    kind = GPUGradientKind.Linear,
                    controlPoints = listOf(0f, 0f, 100f, 100f),
                ),
                stops = listOf(
                    GPUGradientStopPlan(offset = 0f, colorLabel = "red"),
                    GPUGradientStopPlan(offset = 1f, colorLabel = "blue"),
                ),
                stopStore = GPUGradientStopStorePlan(
                    stopCount = 2,
                    storageKind = "uniform",
                    payloadHash = "gradient-stop-payload-v1",
                ),
                tileMode = GPUMaterialTileMode.Clamp,
            ),
        )
        val context = GPUMaterialLoweringContext(
            capabilityClass = "test-capability",
            targetFormatClass = "rgba8unorm",
            dictionaryVersion = GPULinearGradientMaterialDictionary.DictionaryVersion,
        )

        val sourcePlan = GPULinearGradientMaterialLowering.planSource(source, context)
        val accepted = assertIs<GPUMaterialSourcePlan.Accepted>(sourcePlan)

        assertEquals(
            GPULinearGradientMaterialDictionary.LinearGradientSnippetID,
            accepted.snippetId,
        )
        assertTrue(
            accepted.diagnostics.any { it.code == "accepted.material_source.linear_gradient" },
        )
    }

    @Test
    fun `linear gradient material lowering refuses unsupported source kind`() {
        val source = GPUMaterialSourceDescriptor.Solid(
            plan = GPUSolidColorPlan(r = 1f, g = 0f, b = 0f, a = 1f, colorSpecLabel = "srgb"),
        )
        val context = GPUMaterialLoweringContext(
            capabilityClass = "test-capability",
            targetFormatClass = "rgba8unorm",
            dictionaryVersion = GPULinearGradientMaterialDictionary.DictionaryVersion,
        )

        val sourcePlan = GPULinearGradientMaterialLowering.planSource(source, context)
        assertIs<GPUMaterialSourcePlan.Refused>(sourcePlan)
    }

    @Test
    fun `linear gradient material dictionary creates valid entries`() {
        val dictionary = GPULinearGradientMaterialDictionary.create()

        assertEquals(GPULinearGradientMaterialDictionary.DictionaryVersion, dictionary.dictionaryVersion)
        assertEquals(1, dictionary.snippets.size)
        assertEquals(
            GPULinearGradientMaterialDictionary.LinearGradientSnippetID,
            dictionary.snippets.single().snippetId,
        )
        assertEquals(1, dictionary.rootSets.size)
        assertTrue(
            GPULinearGradientMaterialDictionary.LinearGradientSnippetID in dictionary.rootSets.single().snippetIds,
        )
    }

    @Test
    fun `linear gradient material dictionary expansion accepts valid key`() {
        val dictionary = GPULinearGradientMaterialDictionary.create()
        val materialKey = MaterialKey("material:linear_gradient:test")

        val result = GPULinearGradientMaterialDictionary.expandLinearGradientMaterialOrRefuse(
            materialKey = materialKey,
            dictionary = dictionary,
        )

        val accepted = assertIs<GPUMaterialAssemblyResult.Accepted>(result)
        assertEquals("program:material:linear_gradient:test", accepted.plan.programId.value)
        assertEquals(
            GPULinearGradientMaterialDictionary.LinearGradientMaterialModuleSalt,
            accepted.plan.moduleSalt,
        )
    }

    @Test
    fun `linear gradient material dictionary refuses unknown version`() {
        val dictionary = GPULinearGradientMaterialDictionary.create().copy(
            dictionaryVersion = "unknown-version",
        )
        val materialKey = MaterialKey("material:linear_gradient:test")

        val result = GPULinearGradientMaterialDictionary.expandLinearGradientMaterialOrRefuse(
            materialKey = materialKey,
            dictionary = dictionary,
        )

        val refused = assertIs<GPUMaterialAssemblyResult.Refused>(result)
        assertEquals("unsupported.material.dictionary_version_mismatch", refused.diagnostic.code)
    }
}
