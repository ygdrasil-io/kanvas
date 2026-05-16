package org.skia.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPath
import org.graphiks.math.SkPoint3
import org.graphiks.math.SkRect

/**
 * R-suivi.33 — verify that [SkShadowUtils.OptimizeForSurface] warms the
 * projection cache, and that subsequent [SkShadowUtils.DrawShadow]
 * calls with the same `(path, lightPos, zPlaneParams)` tuple hit it.
 */
class SkShadowUtilsOptimizeCacheTest {

    private val transparent: Int = 0

    @BeforeEach
    fun resetCache() {
        SkShadowUtils.projectionCacheClearForTest()
    }

    @Test
    fun `OptimizeForSurface populates the cache and DrawShadow hits it`() {
        val path = SkPath.Rect(SkRect.MakeLTRB(10f, 10f, 30f, 30f))
        val light = SkPoint3(20f, 20f, 200f)
        val plane = SkPoint3(0f, 0f, 4f)
        val canvas = SkCanvas(SkBitmap(64, 64).also { it.eraseColor(transparent) })

        // Baseline : no warm-up, no hits.
        val hitsBefore = SkShadowUtils.projectionCacheHits()

        // Warm the cache.
        val ok = SkShadowUtils.OptimizeForSurface(canvas, path, light, 8f, plane)
        assertTrue(ok)
        // Hits unchanged — OptimizeForSurface writes, doesn't read.
        assertEquals(hitsBefore, SkShadowUtils.projectionCacheHits())

        // Now draw with the same key — must hit the cache.
        SkShadowUtils.DrawShadow(
            canvas = canvas,
            path = path,
            zPlaneParams = plane,
            lightPos = light,
            lightRadius = 8f,
            ambientColor = 0,
            spotColor = (0xFF000000).toInt(),
            flags = SkShadowUtils.kTransparentOccluder_ShadowFlag,
        )
        val hitsAfter = SkShadowUtils.projectionCacheHits()
        assertTrue(
            hitsAfter > hitsBefore,
            "expected at least one cache hit after warm-up (before=$hitsBefore, after=$hitsAfter)",
        )
    }

    @Test
    fun `DrawShadow with no warm-up does not hit the cache`() {
        val path = SkPath.Rect(SkRect.MakeLTRB(10f, 10f, 30f, 30f))
        val canvas = SkCanvas(SkBitmap(64, 64).also { it.eraseColor(transparent) })
        val hitsBefore = SkShadowUtils.projectionCacheHits()

        SkShadowUtils.DrawShadow(
            canvas = canvas,
            path = path,
            zPlaneParams = SkPoint3(0f, 0f, 4f),
            lightPos = SkPoint3(20f, 20f, 200f),
            lightRadius = 8f,
            ambientColor = 0,
            spotColor = (0xFF000000).toInt(),
        )
        assertEquals(
            hitsBefore, SkShadowUtils.projectionCacheHits(),
            "DrawShadow must not log a cache hit without a prior OptimizeForSurface call",
        )
    }

    @Test
    fun `cache key changes with lightPos`() {
        val path = SkPath.Rect(SkRect.MakeLTRB(10f, 10f, 30f, 30f))
        val plane = SkPoint3(0f, 0f, 4f)
        val canvas = SkCanvas(SkBitmap(64, 64).also { it.eraseColor(transparent) })

        SkShadowUtils.OptimizeForSurface(canvas, path, SkPoint3(20f, 20f, 200f), 8f, plane)
        val hitsBefore = SkShadowUtils.projectionCacheHits()

        // Different light position — must miss the cache.
        SkShadowUtils.DrawShadow(
            canvas = canvas,
            path = path,
            zPlaneParams = plane,
            lightPos = SkPoint3(40f, 40f, 200f), // shifted light
            lightRadius = 8f,
            ambientColor = 0,
            spotColor = (0xFF000000).toInt(),
        )
        assertEquals(
            hitsBefore, SkShadowUtils.projectionCacheHits(),
            "shifted light must miss the cache (different key)",
        )
    }
}
