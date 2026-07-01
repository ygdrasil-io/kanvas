package org.graphiks.kanvas.paint

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.types.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class EffectsExpansionTest {

    // Shader subtypes
    @Test fun `PerlinNoise constructs`() {
        val s = Shader.PerlinNoise(0f, 0f, 3, 42, null)
        assertTrue(s is Shader)
    }
    @Test fun `FractalNoise constructs`() {
        val s = Shader.FractalNoise(0f, 0f, 3, 42, null)
        assertTrue(s is Shader)
    }
    @Test fun `WithWorkingColorSpace constructs`() {
        val s = Shader.WithWorkingColorSpace(Shader.SolidColor(Color.RED), ColorSpaceInterpolation.LINEAR)
        assertTrue(s is Shader)
    }
    @Test fun `CoordClamp constructs`() {
        val s = Shader.CoordClamp(Shader.SolidColor(Color.RED), Rect(0f, 0f, 100f, 100f))
        assertTrue(s is Shader)
    }

    // Gradients with interpolation parameter
    @Test fun `LinearGradient has interpolation default`() {
        val s = Shader.LinearGradient(
            start = Point(0f, 0f), end = Point(100f, 0f),
            stops = listOf(GradientStop(0f, Color.WHITE), GradientStop(1f, Color.BLACK)),
        )
        assertEquals(ColorSpaceInterpolation.SRGB, s.interpolation)
    }
    @Test fun `RadialGradient has interpolation default`() {
        val s = Shader.RadialGradient(
            center = Point(50f, 50f), radius = 80f,
            stops = listOf(GradientStop(0f, Color.GREEN), GradientStop(1f, Color.TRANSPARENT)),
        )
        assertEquals(ColorSpaceInterpolation.SRGB, s.interpolation)
    }
    @Test fun `SweepGradient has interpolation default`() {
        val s = Shader.SweepGradient(
            center = Point(50f, 50f),
            stops = listOf(GradientStop(0f, Color.WHITE), GradientStop(1f, Color.BLACK)),
        )
        assertEquals(ColorSpaceInterpolation.SRGB, s.interpolation)
    }
    @Test fun `ConicalGradient has interpolation default`() {
        val s = Shader.ConicalGradient(
            start = Point(0f, 0f), startRadius = 0f,
            end = Point(100f, 0f), endRadius = 50f,
            stops = listOf(GradientStop(0f, Color.WHITE), GradientStop(1f, Color.BLACK)),
        )
        assertEquals(ColorSpaceInterpolation.SRGB, s.interpolation)
    }

    // ColorFilter subtypes
    @Test fun `HSLAMatrix constructs`() {
        val cf = ColorFilter.HSLAMatrix(FloatArray(20))
        assertTrue(cf is ColorFilter)
    }
    @Test fun `Lerp constructs`() {
        val cf = ColorFilter.Lerp(0.5f, ColorFilter.SRGBToLinear, ColorFilter.LinearToSRGB)
        assertTrue(cf is ColorFilter)
    }
    @Test fun `HighContrast constructs`() {
        assertTrue(ColorFilter.HighContrast is ColorFilter)
    }
    @Test fun `Luma constructs`() {
        assertTrue(ColorFilter.Luma is ColorFilter)
    }
    @Test fun `Overdraw constructs`() {
        assertTrue(ColorFilter.Overdraw is ColorFilter)
    }

    // MaskFilter subtypes
    @Test fun `MaskFilterShader constructs`() {
        val mf = MaskFilter.Shader(Shader.SolidColor(Color.RED))
        assertTrue(mf is MaskFilter)
    }
    @Test fun `MaskFilterTable constructs`() {
        val mf = MaskFilter.Table(UByteArray(256))
        assertTrue(mf is MaskFilter)
    }

    // PathEffect subtypes
    @Test fun `Path1D constructs`() {
        val pe = PathEffect.Path1D(Path(), 10f, 0f, Path1DStyle.TRANSLATE)
        assertTrue(pe is PathEffect)
    }
    @Test fun `Path2D constructs`() {
        val pe = PathEffect.Path2D(Matrix33.identity(), Path())
        assertTrue(pe is PathEffect)
    }
    @Test fun `Trim constructs`() {
        val pe = PathEffect.Trim(0f, 1f)
        assertTrue(pe is PathEffect)
    }

    // ImageFilter subtypes
    @Test fun `Dilate constructs`() {
        val f = ImageFilter.Dilate(1f, 1f, null)
        assertTrue(f is ImageFilter)
    }
    @Test fun `Erode constructs`() {
        val f = ImageFilter.Erode(1f, 1f, null)
        assertTrue(f is ImageFilter)
    }
    @Test fun `DistantLitDiffuse constructs`() {
        val f = ImageFilter.DistantLitDiffuse(Point(0f, 0f), Color.WHITE, 1f, 1f, null)
        assertTrue(f is ImageFilter)
    }
    @Test fun `PointLitDiffuse constructs`() {
        val f = ImageFilter.PointLitDiffuse(Point(0f, 0f), Color.WHITE, 1f, 1f, null)
        assertTrue(f is ImageFilter)
    }
    @Test fun `SpotLitDiffuse constructs`() {
        val f = ImageFilter.SpotLitDiffuse(Point(0f, 0f), Point(1f, 1f), 10f, 30f, Color.WHITE, 1f, 1f, null)
        assertTrue(f is ImageFilter)
    }
    @Test fun `DistantLitSpecular constructs`() {
        val f = ImageFilter.DistantLitSpecular(Point(0f, 0f), Color.WHITE, 1f, 1f, 10f, null)
        assertTrue(f is ImageFilter)
    }
    @Test fun `PointLitSpecular constructs`() {
        val f = ImageFilter.PointLitSpecular(Point(0f, 0f), Color.WHITE, 1f, 1f, 10f, null)
        assertTrue(f is ImageFilter)
    }
    @Test fun `SpotLitSpecular constructs`() {
        val f = ImageFilter.SpotLitSpecular(Point(0f, 0f), Point(1f, 1f), 10f, 30f, Color.WHITE, 1f, 1f, 10f, null)
        assertTrue(f is ImageFilter)
    }
    @Test fun `Offset constructs`() {
        val f = ImageFilter.Offset(10f, 20f, null)
        assertTrue(f is ImageFilter)
    }
    @Test fun `Tile constructs`() {
        val f = ImageFilter.Tile(Rect(0f, 0f, 10f, 10f), Rect(0f, 0f, 100f, 100f), null)
        assertTrue(f is ImageFilter)
    }
    @Test fun `Merge constructs`() {
        val f = ImageFilter.Merge(emptyList())
        assertTrue(f is ImageFilter)
    }
    @Test fun `DisplacementMap constructs`() {
        val f = ImageFilter.DisplacementMap(ColorChannel.R, ColorChannel.G, 10f, ImageFilter.Offset(1f, 1f, null), null)
        assertTrue(f is ImageFilter)
    }
    @Test fun `Magnifier constructs`() {
        val f = ImageFilter.Magnifier(Rect(0f, 0f, 50f, 50f), 2f, 0.1f, null)
        assertTrue(f is ImageFilter)
    }
    @Test fun `MatrixConvolution constructs`() {
        val f = ImageFilter.MatrixConvolution(Size(3f, 3f), FloatArray(9), 1f, 0f, Point(1f, 1f), TileMode.CLAMP, true, null)
        assertTrue(f is ImageFilter)
    }

    // Enum value counts
    @Test fun `ColorSpaceInterpolation has 5 values`() {
        assertEquals(5, ColorSpaceInterpolation.entries.size)
    }
    @Test fun `Path1DStyle has 3 values`() {
        assertEquals(3, Path1DStyle.entries.size)
    }
    @Test fun `ColorChannel has 4 values`() {
        assertEquals(4, ColorChannel.entries.size)
    }
}
