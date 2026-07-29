package org.graphiks.kanvas.gpu.renderer.wgsl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialLoweringContext
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramCompiler
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramResult
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextCompositeProgramCache
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextCompositeProgramResult

class GPUPreparedTextCompositeProgramCacheTest {
    private val context = GPUMaterialLoweringContext(
        capabilityClass = "webgpu-prepared-text-cache-test",
        targetFormatClass = "rgba8unorm",
        dictionaryVersion = "material-dictionary:prepared-text-cache-test:v1",
    )

    @Test
    fun `one structural key composes parses lowers and reflects once across subruns and warm builds`() {
        val cache = GPUPreparedTextCompositeProgramCache(maximumEntries = 2)
        val firstMaterial = compileSolid(red = 0.25f, paintAlpha = 0.4f)
        val secondMaterial = compileSolid(red = 0.75f, paintAlpha = 0.9f)

        val first = ready(cache.getOrCompose(firstMaterial))
        val secondSubrun = ready(cache.getOrCompose(secondMaterial))
        val warmBuild = ready(cache.getOrCompose(firstMaterial))

        assertSame(first, secondSubrun)
        assertSame(first, warmBuild)
        assertEquals(
            GPUPreparedTextCompositeProgramCache.Snapshot(
                residentEntryCount = 1,
                hitCount = 2,
                missCount = 1,
                evictionCount = 0,
                composeCount = 1,
                parseCount = 1,
                lowerCount = 1,
                reflectCount = 1,
            ),
            cache.snapshot(),
        )
    }

    @Test
    fun `every changed structural key misses and the LRU stays bounded`() {
        val cache = GPUPreparedTextCompositeProgramCache(maximumEntries = 2)
        val material = compileSolid(red = 0.25f, paintAlpha = 1f)

        val baseline = ready(cache.getOrCompose(material))
        ready(cache.getOrCompose(material, targetFormatClass = "rgba8unorm-srgb"))
        ready(cache.getOrCompose(material))
        val otherBlend = ready(
            cache.getOrCompose(
                material = material,
                blendPlanIdentity = "fixed-function:src",
            ),
        )

        assertSame(baseline, ready(cache.getOrCompose(material)))
        assertEquals("fixed-function:src", otherBlend.blendPlanIdentity)
        assertEquals(2, cache.snapshot().residentEntryCount)
        assertEquals(2, cache.snapshot().hitCount)
        assertEquals(3, cache.snapshot().missCount)
        assertEquals(1, cache.snapshot().evictionCount)
        assertEquals(3, cache.snapshot().composeCount)
        assertEquals(cache.snapshot().composeCount, cache.snapshot().parseCount)
        assertEquals(cache.snapshot().composeCount, cache.snapshot().lowerCount)
        assertEquals(cache.snapshot().composeCount, cache.snapshot().reflectCount)
    }

    private fun compileSolid(red: Float, paintAlpha: Float): GPUPreparedMaterialProgram =
        assertIs<GPUPreparedMaterialProgramResult.Ready>(
            GPUPreparedMaterialProgramCompiler.compile(
                descriptor = GPUMaterialDescriptor.SolidColor(
                    r = red,
                    g = 0.25f,
                    b = 0.5f,
                    a = 1f,
                ),
                paintAlpha = paintAlpha,
                context = context,
            ),
        ).program

    private fun ready(
        result: GPUPreparedTextCompositeProgramResult,
    ) = assertIs<GPUPreparedTextCompositeProgramResult.Ready>(result).program
}
