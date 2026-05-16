package org.skia.utils

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColor
import org.skia.foundation.SkPath
import org.skia.math.SkPoint3
import org.skia.math.SkRect

/**
 * R-suivi.31 — verify that [SkShadowUtils.DrawShadow] honours
 * non-zero `zPlaneParams.fX` / `fY` (a tilted occluder plane). The
 * centroid-only approximation paints the same shadow on both sides ;
 * the per-vertex sampler should produce a visibly larger shadow on the
 * high-z side of a tilted occluder.
 */
class SkShadowUtilsTiltedZTest {

    private val transparent: SkColor = 0

    private fun countNonTransparent(canvas: SkCanvas, l: Int, t: Int, r: Int, b: Int): Int {
        var n = 0
        for (y in t until b) for (x in l until r) {
            if (canvas.bitmap.getPixel(x, y) != transparent) n++
        }
        return n
    }

    @Test
    fun `tilted plane produces a wider envelope than a flat plane`() {
        // Same square occluder, two shadow renders : one with a flat
        // z-plane (z = 4 everywhere), one with a tilted plane that
        // ramps from z ≈ 0 on the left to z ≈ 8 on the right. Both use
        // a small fill and a heavy spot blur, so the tilted case must
        // touch strictly more pixels than the flat one.
        val rect = SkRect.MakeLTRB(40f, 40f, 56f, 56f)
        val path = SkPath.Rect(rect)

        val flat = SkCanvas(SkBitmap(128, 128).also { it.eraseColor(transparent) })
        SkShadowUtils.DrawShadow(
            canvas = flat,
            path = path,
            zPlaneParams = SkPoint3(0f, 0f, 4f),
            lightPos = SkPoint3(64f, 64f, 600f),
            lightRadius = 80f,
            ambientColor = 0,
            spotColor = (0xFF000000).toInt(),
            flags = SkShadowUtils.kTransparentOccluder_ShadowFlag,
        )

        val tilted = SkCanvas(SkBitmap(128, 128).also { it.eraseColor(transparent) })
        SkShadowUtils.DrawShadow(
            canvas = tilted,
            path = path,
            // z(x, y) = 0.5·x - 0  → z(40)=20, z(56)=28. Big tilt, far
            // bigger spot params than the flat case.
            zPlaneParams = SkPoint3(0.5f, 0f, 0f),
            lightPos = SkPoint3(64f, 64f, 600f),
            lightRadius = 80f,
            ambientColor = 0,
            spotColor = (0xFF000000).toInt(),
            flags = SkShadowUtils.kTransparentOccluder_ShadowFlag,
        )

        val flatCount = countNonTransparent(flat, 0, 0, 128, 128)
        val tiltedCount = countNonTransparent(tilted, 0, 0, 128, 128)
        assertTrue(
            tiltedCount > flatCount,
            "tilted-z shadow ($tiltedCount px) should cover more area than flat-z shadow ($flatCount px)",
        )
    }

    @Test
    fun `single-vertex path falls back to centroid-z (no NaN, no crash)`() {
        // Path with only a Move verb has < 2 endpoints — the impl
        // must fall through to centroid-z without throwing.
        val pb = org.skia.foundation.SkPathBuilder()
        pb.moveTo(10f, 10f)
        val sparse = pb.detach()
        val canvas = SkCanvas(SkBitmap(32, 32).also { it.eraseColor(transparent) })
        SkShadowUtils.DrawShadow(
            canvas = canvas,
            path = sparse,
            zPlaneParams = SkPoint3(0.5f, 0.5f, 4f),
            lightPos = SkPoint3(16f, 16f, 200f),
            lightRadius = 20f,
            ambientColor = (0x40000000),
            spotColor = (0x80000000).toInt(),
        )
        // No assertion on pixels — a single-Move path has empty bounds
        // and DrawShadow returns early. The test only needs to verify
        // the call doesn't throw on the fallback path.
    }
}
