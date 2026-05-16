package org.skia.utils

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorGetA
import org.skia.foundation.SkPath
import org.skia.math.SkPoint3
import org.skia.math.SkRect

/**
 * R-suivi.32 — verify that [SkShadowUtils.kTransparentOccluder_ShadowFlag]
 * actually toggles occluder culling.
 *
 * - Flag unset (opaque occluder) : the spot shadow under the occluder
 *   is clipped out, so the centre of the occluder bbox stays
 *   transparent.
 * - Flag set (transparent occluder) : the spot shadow extends through
 *   the occluder, so the centre is painted.
 */
class SkShadowUtilsTransparentOccluderTest {

    private val transparent: SkColor = 0

    @Test
    fun `opaque occluder culls the spot shadow under the path`() {
        val rect = SkRect.MakeLTRB(24f, 24f, 40f, 40f)
        val path = SkPath.Rect(rect)
        val canvas = SkCanvas(SkBitmap(64, 64).also { it.eraseColor(transparent) })

        // Use a small spot offset so the shadow stays near the occluder
        // and the cull is visible at the path's centre.
        SkShadowUtils.DrawShadow(
            canvas = canvas,
            path = path,
            zPlaneParams = SkPoint3(0f, 0f, 4f),
            lightPos = SkPoint3(32f, 32f, 800f),
            lightRadius = 8f,
            ambientColor = 0,                       // disable ambient halo
            spotColor = (0xFF000000).toInt(),       // opaque black spot
            flags = SkShadowUtils.kNone_ShadowFlag, // opaque occluder
        )

        // The exact centre of the rect (32, 32) is well inside the
        // occluder bounds — must be culled.
        val centre = canvas.bitmap.getPixel(32, 32)
        assertTrue(
            SkColorGetA(centre) == 0,
            "opaque-occluder spot must be culled at the path centre (got alpha=${SkColorGetA(centre)})",
        )
    }

    @Test
    fun `transparent occluder leaks the spot shadow through the path`() {
        val rect = SkRect.MakeLTRB(24f, 24f, 40f, 40f)
        val path = SkPath.Rect(rect)
        val canvas = SkCanvas(SkBitmap(64, 64).also { it.eraseColor(transparent) })

        SkShadowUtils.DrawShadow(
            canvas = canvas,
            path = path,
            zPlaneParams = SkPoint3(0f, 0f, 4f),
            lightPos = SkPoint3(32f, 32f, 800f),
            lightRadius = 8f,
            ambientColor = 0,
            spotColor = (0xFF000000).toInt(),
            flags = SkShadowUtils.kTransparentOccluder_ShadowFlag,
        )

        // With the transparent flag set, the spot must paint through
        // the occluder — the centre pixel is non-transparent.
        val centre = canvas.bitmap.getPixel(32, 32)
        assertTrue(
            SkColorGetA(centre) > 0,
            "transparent-occluder spot must paint at the path centre (got alpha=${SkColorGetA(centre)})",
        )
    }
}
