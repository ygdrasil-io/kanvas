package org.graphiks.kanvas.gpu.renderer.materials

import org.graphiks.kanvas.gpu.renderer.wgsl.BitmapShaderDecalClampEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.BitmapShaderMirrorClampEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.BitmapShaderRepeatClampEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.BitmapShaderSourceEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.BitmapShaderWgsl
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
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
    fun `bitmap shader material lowering accepts repeat tile mode as a distinct source plan`() {
        val source = imageSource(
            imageSourceKey = "repeat-image-key",
            tileModeX = GPUMaterialTileMode.Repeat,
            tileModeY = GPUMaterialTileMode.Clamp,
            filterMode = "linear",
        )
        val context = GPUMaterialLoweringContext(
            capabilityClass = "test-capability",
            targetFormatClass = "rgba8unorm",
            dictionaryVersion = GPUBitmapShaderMaterialDictionary.DictionaryVersion,
        )

        val sourcePlan = GPUBitmapShaderMaterialLowering.planSource(source, context)
        val accepted = assertIs<GPUMaterialSourcePlan.Accepted>(sourcePlan)

        assertEquals(GPUBitmapShaderMaterialDictionary.BitmapShaderSnippetID, accepted.snippetId)
        assertEquals(BitmapShaderRepeatClampEntryPoint, accepted.entryPoint)
        assertTrue(accepted.diagnostics.any { it.code == "accepted.material_source.bitmap_shader" })
    }

    @Test
    fun `bitmap shader material lowering selects mirror and decal entry points`() {
        val context = GPUMaterialLoweringContext(
            capabilityClass = "test-capability",
            targetFormatClass = "rgba8unorm",
            dictionaryVersion = GPUBitmapShaderMaterialDictionary.DictionaryVersion,
        )
        val mirror = imageSource(
            imageSourceKey = "mirror-image-key",
            tileModeX = GPUMaterialTileMode.Mirror,
            tileModeY = GPUMaterialTileMode.Clamp,
            filterMode = "linear",
        )
        val decal = imageSource(
            imageSourceKey = "decal-image-key",
            tileModeX = GPUMaterialTileMode.Decal,
            tileModeY = GPUMaterialTileMode.Clamp,
            filterMode = "linear",
        )

        val mirrorPlan = assertIs<GPUMaterialSourcePlan.Accepted>(
            GPUBitmapShaderMaterialLowering.planSource(mirror, context),
        )
        val decalPlan = assertIs<GPUMaterialSourcePlan.Accepted>(
            GPUBitmapShaderMaterialLowering.planSource(decal, context),
        )

        assertEquals(BitmapShaderMirrorClampEntryPoint, mirrorPlan.entryPoint)
        assertEquals(BitmapShaderDecalClampEntryPoint, decalPlan.entryPoint)
    }

    @Test
    fun `bitmap shader material keys include tile and filter facts`() {
        val context = GPUMaterialLoweringContext(
            capabilityClass = "test-capability",
            targetFormatClass = "rgba8unorm",
            dictionaryVersion = GPUBitmapShaderMaterialDictionary.DictionaryVersion,
        )
        val clamp = imageSource(
            imageSourceKey = "image-key",
            tileModeX = GPUMaterialTileMode.Clamp,
            tileModeY = GPUMaterialTileMode.Clamp,
            filterMode = "linear",
        )
        val repeat = imageSource(
            imageSourceKey = "image-key",
            tileModeX = GPUMaterialTileMode.Repeat,
            tileModeY = GPUMaterialTileMode.Clamp,
            filterMode = "linear",
        )
        val nearest = imageSource(
            imageSourceKey = "image-key",
            tileModeX = GPUMaterialTileMode.Clamp,
            tileModeY = GPUMaterialTileMode.Clamp,
            filterMode = "nearest",
        )

        val clampPlan = assertIs<GPUMaterialSourcePlan.Accepted>(
            GPUBitmapShaderMaterialLowering.planSource(clamp, context),
        )
        val repeatPlan = assertIs<GPUMaterialSourcePlan.Accepted>(
            GPUBitmapShaderMaterialLowering.planSource(repeat, context),
        )
        val nearestPlan = assertIs<GPUMaterialSourcePlan.Accepted>(
            GPUBitmapShaderMaterialLowering.planSource(nearest, context),
        )

        val clampKey = GPUBitmapShaderMaterialLowering.deriveMaterialKey(clampPlan, context)
        val repeatKey = GPUBitmapShaderMaterialLowering.deriveMaterialKey(repeatPlan, context)
        val nearestKey = GPUBitmapShaderMaterialLowering.deriveMaterialKey(nearestPlan, context)

        assertNotEquals(clampKey, repeatKey)
        assertNotEquals(clampKey, nearestKey)
    }

    @Test
    fun `bitmap shader material lowering accepts all bounded tile pairs with declared WGSL entry points`() {
        val context = GPUMaterialLoweringContext(
            capabilityClass = "test-capability",
            targetFormatClass = "rgba8unorm",
            dictionaryVersion = GPUBitmapShaderMaterialDictionary.DictionaryVersion,
        )
        val materialKeys = mutableSetOf<MaterialKey>()

        for (tileModeX in GPUMaterialTileMode.values()) {
            for (tileModeY in GPUMaterialTileMode.values()) {
                val source = imageSource(
                    imageSourceKey = "image-key",
                    tileModeX = tileModeX,
                    tileModeY = tileModeY,
                    filterMode = "linear",
                )

                val accepted = assertIs<GPUMaterialSourcePlan.Accepted>(
                    GPUBitmapShaderMaterialLowering.planSource(source, context),
                )

                assertTrue(
                    BitmapShaderWgsl.contains("fn ${accepted.entryPoint}("),
                    "Missing WGSL function for ${tileModeX}/${tileModeY}: ${accepted.entryPoint}",
                )
                materialKeys += GPUBitmapShaderMaterialLowering.deriveMaterialKey(accepted, context)
            }
        }

        assertEquals(16, materialKeys.size)
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
        assertEquals(BitmapShaderSourceEntryPoint, dictionary.snippets.single().entryPoint)
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
    fun `bitmap shader material expansion wires accepted repeat mirror and decal entry points into consumed WGSL source`() {
        val dictionary = GPUBitmapShaderMaterialDictionary.create()
        val context = GPUMaterialLoweringContext(
            capabilityClass = "test-capability",
            targetFormatClass = "rgba8unorm",
            dictionaryVersion = GPUBitmapShaderMaterialDictionary.DictionaryVersion,
        )
        val cases = listOf(
            GPUMaterialTileMode.Repeat to BitmapShaderRepeatClampEntryPoint,
            GPUMaterialTileMode.Mirror to BitmapShaderMirrorClampEntryPoint,
            GPUMaterialTileMode.Decal to BitmapShaderDecalClampEntryPoint,
        )

        for ((tileModeX, expectedEntryPoint) in cases) {
            val source = imageSource(
                imageSourceKey = "image-key",
                tileModeX = tileModeX,
                tileModeY = GPUMaterialTileMode.Clamp,
                filterMode = "linear",
            )
            val sourcePlan = assertIs<GPUMaterialSourcePlan.Accepted>(
                GPUBitmapShaderMaterialLowering.planSource(source, context),
            )
            val materialKey = GPUBitmapShaderMaterialLowering.deriveMaterialKey(sourcePlan, context)

            val result = GPUBitmapShaderMaterialDictionary.expandBitmapShaderMaterialOrRefuse(
                materialKey = materialKey,
                dictionary = dictionary,
                sourcePlan = sourcePlan,
            )

            val accepted = assertIs<GPUMaterialAssemblyResult.Accepted>(result)
            assertEquals(BitmapShaderSourceEntryPoint, accepted.plan.sourceEntryPoint)
            assertContains(accepted.plan.sourceWgsl, "fn bitmap_shader_source(uv: vec2<f32>) -> vec4<f32>")
            assertContains(accepted.plan.sourceWgsl, "return $expectedEntryPoint(uv);")
            assertFalse(
                accepted.plan.sourceWgsl.contains("return bitmap_shader_clamp(uv);"),
                "Consumed bitmap shader source must not silently fall back to clamp for $tileModeX",
            )
        }
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

    private fun imageSource(
        imageSourceKey: String,
        tileModeX: GPUMaterialTileMode,
        tileModeY: GPUMaterialTileMode,
        filterMode: String,
        mipmapMode: String = "none",
    ): GPUMaterialSourceDescriptor.Image =
        GPUMaterialSourceDescriptor.Image(
            plan = GPUImageShaderPlan(
                imageSourceKey = imageSourceKey,
                sampling = GPUMaterialSamplingPlan(
                    tileModeX = tileModeX,
                    tileModeY = tileModeY,
                    filterMode = filterMode,
                    mipmapMode = mipmapMode,
                ),
                colorTreatment = "sampled-unpremul-srgb-to-target",
            ),
        )
}
