package org.graphiks.kanvas.gpu.renderer.materials

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BitmapShaderMaterialLoweringTest {

    @Test
    fun `bitmap shader material lowering accepts valid image source`() {
        val source = GPUMaterialSourceDescriptor.Image(
            plan = GPUImageShaderPlan(
                imageSourceKey = "test-image-key",
                sampling = GPUMaterialSamplingPlan(
                    tileModeX = GPUMaterialTileMode.Clamp,
                    tileModeY = GPUMaterialTileMode.Clamp,
                    filterMode = "linear",
                    mipmapMode = "none",
                ),
                colorTreatment = "sampled-unpremul-srgb-to-target",
            ),
        )
        val context = GPUMaterialLoweringContext(
            capabilityClass = "test-capability",
            targetFormatClass = "rgba8unorm",
            dictionaryVersion = GPUBitmapShaderMaterialDictionary.DictionaryVersion,
        )

        val plan = GPUBitmapShaderMaterialLowering.planPaint(
            descriptor = GPUPaintDescriptor(
                paintId = "paint-1",
                source = source,
                blendModeLabel = "src_over",
                alpha = 1f,
                colorSpaceLabel = "srgb",
            ),
            context = context,
        )

        assertTrue(plan.materialKey.value.startsWith("material:bitmap_shader:"))
        assertEquals(GPUPaintEvaluationOrder.SourceThenCoverage, plan.evaluationOrder)
    }

    @Test
    fun `bitmap shader material source plan accepts valid image`() {
        val source = GPUMaterialSourceDescriptor.Image(
            plan = GPUImageShaderPlan(
                imageSourceKey = "test-image-key",
                sampling = GPUMaterialSamplingPlan(
                    tileModeX = GPUMaterialTileMode.Clamp,
                    tileModeY = GPUMaterialTileMode.Clamp,
                    filterMode = "nearest",
                    mipmapMode = "none",
                ),
                colorTreatment = "sampled-unpremul-srgb-to-target",
            ),
        )
        val context = GPUMaterialLoweringContext(
            capabilityClass = "test-capability",
            targetFormatClass = "rgba8unorm",
            dictionaryVersion = GPUBitmapShaderMaterialDictionary.DictionaryVersion,
        )

        val sourcePlan = GPUBitmapShaderMaterialLowering.planSource(source, context)
        val accepted = assertIs<GPUMaterialSourcePlan.Accepted>(sourcePlan)

        assertEquals(
            GPUBitmapShaderMaterialDictionary.BitmapShaderSnippetID,
            accepted.snippetId,
        )
        assertTrue(
            accepted.diagnostics.any { it.code == "accepted.material_source.bitmap_shader" },
        )
    }

    @Test
    fun `bitmap shader material lowering refuses unsupported source kind`() {
        val source = GPUMaterialSourceDescriptor.Solid(
            plan = GPUSolidColorPlan(r = 1f, g = 0f, b = 0f, a = 1f, colorSpecLabel = "srgb"),
        )
        val context = GPUMaterialLoweringContext(
            capabilityClass = "test-capability",
            targetFormatClass = "rgba8unorm",
            dictionaryVersion = GPUBitmapShaderMaterialDictionary.DictionaryVersion,
        )

        val sourcePlan = GPUBitmapShaderMaterialLowering.planSource(source, context)
        assertIs<GPUMaterialSourcePlan.Refused>(sourcePlan)
    }

    @Test
    fun `bitmap shader material lowering refuses unsupported tile mode`() {
        val source = GPUMaterialSourceDescriptor.Image(
            plan = GPUImageShaderPlan(
                imageSourceKey = "test-image-key",
                sampling = GPUMaterialSamplingPlan(
                    tileModeX = GPUMaterialTileMode.Repeat,
                    tileModeY = GPUMaterialTileMode.Clamp,
                    filterMode = "linear",
                    mipmapMode = "none",
                ),
                colorTreatment = "sampled-unpremul-srgb-to-target",
            ),
        )
        val context = GPUMaterialLoweringContext(
            capabilityClass = "test-capability",
            targetFormatClass = "rgba8unorm",
            dictionaryVersion = GPUBitmapShaderMaterialDictionary.DictionaryVersion,
        )

        val sourcePlan = GPUBitmapShaderMaterialLowering.planSource(source, context)
        val refused = assertIs<GPUMaterialSourcePlan.Refused>(sourcePlan)
        assertEquals("unsupported.material.bitmap_tile_mode_unimplemented", refused.diagnostic.code)
    }

    @Test
    fun `bitmap shader material dictionary creates valid entries`() {
        val dictionary = GPUBitmapShaderMaterialDictionary.create()

        assertEquals(GPUBitmapShaderMaterialDictionary.DictionaryVersion, dictionary.dictionaryVersion)
        assertEquals(1, dictionary.snippets.size)
        assertEquals(
            GPUBitmapShaderMaterialDictionary.BitmapShaderSnippetID,
            dictionary.snippets.single().snippetId,
        )
        assertEquals(1, dictionary.rootSets.size)
        assertTrue(
            GPUBitmapShaderMaterialDictionary.BitmapShaderSnippetID in dictionary.rootSets.single().snippetIds,
        )
    }

    @Test
    fun `bitmap shader material dictionary expansion accepts valid key`() {
        val dictionary = GPUBitmapShaderMaterialDictionary.create()
        val materialKey = MaterialKey("material:bitmap_shader:test")

        val result = GPUBitmapShaderMaterialDictionary.expandBitmapShaderMaterialOrRefuse(
            materialKey = materialKey,
            dictionary = dictionary,
        )

        val accepted = assertIs<GPUMaterialAssemblyResult.Accepted>(result)
        assertEquals("program:material:bitmap_shader:test", accepted.plan.programId.value)
        assertEquals(
            GPUBitmapShaderMaterialDictionary.BitmapShaderMaterialModuleSalt,
            accepted.plan.moduleSalt,
        )
    }

    @Test
    fun `bitmap shader material dictionary refuses unknown version`() {
        val dictionary = GPUBitmapShaderMaterialDictionary.create().copy(
            dictionaryVersion = "unknown-version",
        )
        val materialKey = MaterialKey("material:bitmap_shader:test")

        val result = GPUBitmapShaderMaterialDictionary.expandBitmapShaderMaterialOrRefuse(
            materialKey = materialKey,
            dictionary = dictionary,
        )

        val refused = assertIs<GPUMaterialAssemblyResult.Refused>(result)
        assertEquals("unsupported.material.dictionary_version_mismatch", refused.diagnostic.code)
    }

    @Test
    fun `bitmap shader material lowering refuses blank image source key`() {
        val source = GPUMaterialSourceDescriptor.Image(
            plan = GPUImageShaderPlan(
                imageSourceKey = "",
                sampling = GPUMaterialSamplingPlan(
                    tileModeX = GPUMaterialTileMode.Clamp,
                    tileModeY = GPUMaterialTileMode.Clamp,
                    filterMode = "linear",
                    mipmapMode = "none",
                ),
                colorTreatment = "sampled-unpremul-srgb-to-target",
            ),
        )
        val context = GPUMaterialLoweringContext(
            capabilityClass = "test-capability",
            targetFormatClass = "rgba8unorm",
            dictionaryVersion = GPUBitmapShaderMaterialDictionary.DictionaryVersion,
        )

        val sourcePlan = GPUBitmapShaderMaterialLowering.planSource(source, context)
        val refused = assertIs<GPUMaterialSourcePlan.Refused>(sourcePlan)
        assertEquals("unsupported.material.bitmap_source_key_missing", refused.diagnostic.code)
    }
}
