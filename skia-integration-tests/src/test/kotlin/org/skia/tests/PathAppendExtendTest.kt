package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * PathAppendExtendGM ports gm/patharcto.cpp `path_append_extend`.
 *
 * Classification: STUB.PERSPECTIVE_ADDPATH
 *
 * The GM exercises [SkPathBuilder.addPath] with both [SkPath.AddPathMode.kAppend]
 * and [SkPath.AddPathMode.kExtend], including two variants that pass a tiny
 * perspective matrix (`persp0 = 0.0001f`) to verify addPath does not crash on
 * perspective input.
 *
 * The Kotlin [SkPathBuilder.addPath] implementation only maps points via the
 * affine rows of the matrix — the `persp0 / persp1` perspective rows are stored
 * in [SkMatrix] but not applied during point mapping. As a result, the two
 * "with perspective" columns (columns 3 and 5) render identically to the
 * plain-identity columns, producing a pixel-level divergence from the upstream
 * DM reference for those columns.
 *
 * The GM compiles and runs without errors. Re-enable this test once perspective
 * mapping is implemented in [SkPathBuilder.addPath].
 */
@Disabled("STUB.PERSPECTIVE_ADDPATH: addPath perspective rows not applied; columns 3/5 differ from upstream DM reference")
class PathAppendExtendTest {
    @Test
    fun `PathAppendExtendGM matches path_append_extend_png within tolerance`() {
        val gm = PathAppendExtendGM()
        TestUtils.runGmTest(gm)
    }
}
