package org.skia.foundation

import org.graphiks.math.SkMatrix
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SkTrimPathEffectTest {
    @Test
    fun `normal mode trims line range`() {
        val path = SkPathBuilder().moveTo(0f, 0f).lineTo(100f, 0f).detach()
        val effect = SkTrimPathEffect.Make(0.25f, 0.75f)!!

        val trimmed = effect.filterPath(path, SkMatrix.I())!!

        assertArrayEquals(
            arrayOf(SkPath.Verb.kMove, SkPath.Verb.kLine),
            trimmed.verbs,
        )
        assertArrayEquals(floatArrayOf(25f, 0f, 75f, 0f), trimmed.coords, 1e-4f)
    }

    @Test
    fun `inverted mode emits complement ranges`() {
        val path = SkPathBuilder().moveTo(0f, 0f).lineTo(100f, 0f).detach()
        val effect = SkTrimPathEffect.Make(0.25f, 0.75f, SkTrimPathEffect.Mode.kInverted)!!

        val trimmed = effect.filterPath(path, SkMatrix.I())!!

        assertArrayEquals(
            arrayOf(SkPath.Verb.kMove, SkPath.Verb.kLine, SkPath.Verb.kMove, SkPath.Verb.kLine),
            trimmed.verbs,
        )
        assertArrayEquals(floatArrayOf(75f, 0f, 100f, 0f, 0f, 0f, 25f, 0f), trimmed.coords, 1e-4f)
    }

    @Test
    fun `factory mirrors upstream no-op cases`() {
        assertNull(SkTrimPathEffect.Make(Float.NaN, 1f))
        assertNull(SkTrimPathEffect.Make(0f, 1f))
        assertNull(SkTrimPathEffect.Make(1f, 0.75f, SkTrimPathEffect.Mode.kInverted))
        assertNotNull(SkTrimPathEffect.Make(1f, 0.75f, SkTrimPathEffect.Mode.kNormal))
    }
}
