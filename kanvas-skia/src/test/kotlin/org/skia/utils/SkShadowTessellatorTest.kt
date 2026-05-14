package org.skia.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorTRANSPARENT
import org.skia.foundation.SkColorGetA
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkPoint3
import org.skia.math.SkRect
import kotlin.math.abs

/**
 * R-suivi.30 — unit tests for [SkShadowTessellator].
 *
 * - `geometricOnlyHonored` : when `kGeometricOnly_ShadowFlag` is set,
 *   the emitted mesh has no penumbra ring — every vertex carries the
 *   umbra colour (full alpha).
 * - `ambientMeshIsSoft` : the default ambient mesh has soft alpha
 *   gradient — inner-ring vertices at full alpha, outer-ring vertices
 *   at zero alpha.
 * - `spotMeshTracksLight` : the spot mesh centroid matches the light
 *   projection (centroid + translate / scale).
 */
class SkShadowTessellatorTest {

    private fun rectPath(l: Float, t: Float, r: Float, b: Float): SkPath =
        SkPath.Rect(SkRect.MakeLTRB(l, t, r, b))

    @Test
    fun `tessellatePath returns null for an empty path`() {
        val poly = SkShadowTessellator.tessellatePath(SkPathBuilder().detach())
        assertNull(poly)
    }

    @Test
    fun `tessellatePath produces a convex rect polygon`() {
        val poly = SkShadowTessellator.tessellatePath(rectPath(10f, 10f, 30f, 30f))
        assertNotNull(poly)
        // sanitize_point snaps to 1/16 px ; 10/30 are exact at that quantum.
        assertEquals(4, poly!!.points.size)
        assertTrue(poly.convex, "rect must be convex")
    }

    @Test
    fun `ambientMeshIsSoft has soft alpha gradient inner-1 outer-0`() {
        val path = rectPath(10f, 10f, 30f, 30f)
        val mesh = SkShadowTessellator.MakeAmbient(
            path = path,
            zPlaneParams = SkPoint3(0f, 0f, 8f), // z = 8 ⇒ ambient outset > 0
            transparent = false,
            geometricOnly = false,
        )
        assertNotNull(mesh, "ambient mesh must be non-null for a convex rect")

        // Half the vertices come from the inner ring (umbra colour = ARGB FF000000)
        // and half from the outer ring (penumbra colour = ARGB 00000000).
        val colors = mesh!!.colors!!
        val innerCount = colors.count { it == SK_ColorBLACK }
        val outerCount = colors.count { it == SK_ColorTRANSPARENT }
        assertTrue(innerCount > 0, "must have at least one inner-ring (umbra) vertex")
        assertTrue(outerCount > 0, "must have at least one outer-ring (penumbra) vertex")
        assertEquals(
            innerCount, outerCount,
            "inner / outer rings must have the same vertex count for a soft halo",
        )

        // Soft alpha gradient : umbra alpha = 255, penumbra alpha = 0.
        for (c in colors) {
            val a = SkColorGetA(c)
            assertTrue(
                a == 0 || a == 255,
                "ambient mesh vertices must carry full or zero alpha (got $a)",
            )
        }
    }

    @Test
    fun `geometricOnlyHonored — no penumbra ring`() {
        val path = rectPath(10f, 10f, 30f, 30f)
        val mesh = SkShadowTessellator.MakeAmbient(
            path = path,
            zPlaneParams = SkPoint3(0f, 0f, 8f),
            transparent = false,
            geometricOnly = true,
        )
        assertNotNull(mesh)
        // Geometric only ⇒ every vertex is the umbra colour, no
        // penumbra (zero-alpha) ring.
        val colors = mesh!!.colors!!
        for (c in colors) {
            assertEquals(
                SK_ColorBLACK, c,
                "geometric-only mesh must only carry umbra-colour vertices",
            )
        }
    }

    @Test
    fun `spotMeshTracksLight — projected centroid matches scale + translate`() {
        val path = rectPath(20f, 20f, 40f, 40f)
        // light directly above the path centre, mid-height occluder.
        val mesh = SkShadowTessellator.MakeSpot(
            path = path,
            zPlaneParams = SkPoint3(0f, 0f, 8f),
            lightPos = SkPoint3(30f, 30f, 600f),
            lightRadius = 8f,
            scale = 1.5f,
            translateX = -2f,
            translateY = -3f,
            blurRadius = 4f,
            transparent = false,
            geometricOnly = false,
        )
        assertNotNull(mesh, "spot mesh must be non-null for a convex rect")

        // Inner-ring vertices are the projected silhouette. Their
        // centroid should be at (origCentroid * scale + translate)
        // i.e. (30, 30) + (-2, -3) = (28, 27) for our scale-around-centroid
        // transform.
        val n = mesh!!.positions.size / 2 // (inner + outer) — equal halves.
        var sx = 0f
        var sy = 0f
        for (i in 0 until n) {
            sx += mesh.positions[i].fX
            sy += mesh.positions[i].fY
        }
        sx /= n
        sy /= n
        // Allow a small tolerance for the sanitize-to-1/16-px snap.
        assertTrue(
            abs(sx - 28f) < 0.5f,
            "projected centroid X must track light projection (got $sx, want 28)",
        )
        assertTrue(
            abs(sy - 27f) < 0.5f,
            "projected centroid Y must track light projection (got $sy, want 27)",
        )
    }

    @Test
    fun `MakeAmbient returns null on a degenerate single-point path`() {
        val degenerate = SkPathBuilder().moveTo(5f, 5f).detach()
        val mesh = SkShadowTessellator.MakeAmbient(
            degenerate,
            SkPoint3(0f, 0f, 4f),
            transparent = false,
            geometricOnly = false,
        )
        assertNull(mesh, "degenerate path must yield null (fall back to legacy blur)")
    }
}
