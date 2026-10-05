@file:OptIn(ExperimentalUnsignedTypes::class)
package org.graphiks.kanvas.picture

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.*
import org.graphiks.kanvas.surface.*
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.Base64
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class W5fPictureFilterInterpolationTest {
    @Test fun roundTripRetainsOrderedInternalExternalFiltersAndAlpha() {
        val matrix = ColorMatrixF32.ofIdentity().apply { postTranslate(.125f, .25f, 0f, .25f) }
        val internal = ColorFilter.Matrix(matrix)
        val externalMatrix = ColorMatrixF32.ofIdentity().apply { setScale(.5f, 1f, 1f, .5f) }
        val external = ColorFilter.Matrix(externalMatrix)
        val color = ColorARGB.of(255,0,0,0)
        val background = ColorARGB.Blue
        val shader = Shader.WithColorFilter(Shader.Opacity(Shader.SolidColor(color), .5f), internal)
        val paint = Paint(color = ColorARGB.of(127,255,255,255), shader = shader,
            colorFilter = external, blendMode = BlendMode.DIFFERENCE, antiAlias = false)
        val wanted = W5fColorCpuOracle.expectedShaderTree(shader,127f/255f,external,background,BlendMode.DIFFERENCE)
        disjoint(wanted, W5fColorCpuOracle.expectedShaderTree(
            Shader.Opacity(Shader.WithColorFilter(Shader.SolidColor(color),internal),.5f),
            127f/255f,external,background,BlendMode.DIFFERENCE))
        disjoint(wanted, W5fColorCpuOracle.expectedShaderTree(shader,127f/255f,null,background,BlendMode.DIFFERENCE))
        disjoint(wanted, W5fColorCpuOracle.expectedShaderTree(shader,1f,external,background,BlendMode.DIFFERENCE))
        val zeroFilter = ColorFilter.Matrix(ColorMatrixF32.of(FloatArray(20)))
        val changedShader = Shader.WithColorFilter(Shader.Opacity(Shader.SolidColor(color), .5f), zeroFilter)
        disjoint(wanted, W5fColorCpuOracle.expectedShaderTree(changedShader,127f/255f,
            zeroFilter,background,BlendMode.DIFFERENCE))
        val recorder = PictureRecorder()
        recorder.beginRecording(rect()).apply {
            drawRect(rect(), Paint(color = background, blendMode = BlendMode.SRC, antiAlias = false))
            drawRect(rect(), paint)
        }
        val picture = recorder.finishRecordingAsPicture()
        matrix.setRowMajor(FloatArray(20)); externalMatrix.setRowMajor(FloatArray(20))
        val restored = assertNotNull(Picture.fromByteArray(picture.toByteArray()))
        for (replay in listOf(picture, restored)) {
            val surface = Surface(1,1)
            surface.canvas { replay.playback(this) }
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(), listOf(wanted)) }
        }
    }

    @ParameterizedTest(name = "{0} Surface and Picture capture stops, alpha, wrappers and coordinates")
    @EnumSource(ColorSpaceInterpolation::class)
    fun interpolationRoundTripRetainsStopsAndWrapperPrecedence(domain: ColorSpaceInterpolation) {
        val polar = domain == ColorSpaceInterpolation.HSL || domain == ColorSpaceInterpolation.OKLCH
        val black = if (polar) ColorARGB.of(128,160,96,96) else ColorARGB.of(128,0,0,0)
        val white = if (polar) ColorARGB.of(128,96,160,96) else ColorARGB.of(128,255,255,255)
        val stops = mutableListOf(GradientStop(0f,black), GradientStop(1f,white))
        val matrix = ColorMatrixF32.ofIdentity().apply { setScale(1f,1f,1f,.5f) }
        val filter = ColorFilter.Matrix(matrix)
        val wanted = W5fColorCpuOracle.expectedGradientPixel(domain,black,white,.25f,filter,finalBlend = BlendMode.SRC)
        disjoint(wanted, W5fColorCpuOracle.expectedGradientPixel(domain,white,white,.25f,filter,finalBlend = BlendMode.SRC))
        disjoint(wanted, W5fColorCpuOracle.expectedGradientPixel(domain,black,white,0f,filter,finalBlend = BlendMode.SRC))
        disjoint(wanted, W5fColorCpuOracle.expectedGradientPixel(domain,black,white,.25f,
            ColorFilter.Matrix(ColorMatrixF32.ofIdentity()),finalBlend = BlendMode.SRC))
        val zeroFilter = ColorFilter.Matrix(ColorMatrixF32.of(FloatArray(20)))
        disjoint(wanted, W5fColorCpuOracle.expectedGradientPixel(domain,black,white,.25f,
            zeroFilter,finalBlend = BlendMode.SRC))
        disjoint(wanted, W5fColorCpuOracle.expectedGradientPixel(domain,white,white,.25f,
            zeroFilter,finalBlend = BlendMode.SRC))
        val alternate = if (domain == ColorSpaceInterpolation.SRGB) ColorSpaceInterpolation.LINEAR else ColorSpaceInterpolation.SRGB
        disjoint(wanted, W5fColorCpuOracle.expectedGradientPixel(alternate,black,white,.25f,
            filter,finalBlend = BlendMode.SRC))
        // Actual outer working-space and inverse local-coordinate rules survive
        // archive replay; losing either changes the bounded quarter-stop sample.
        val shader = Shader.WithWorkingColorSpace(Shader.WithLocalMatrix(
            Shader.WithWorkingColorSpace(Shader.LinearGradient(Point2F32(.5f,0f), Point2F32(1.5f,0f),stops,
                interpolation = alternate),alternate), Matrix3x3F32.translation(-.25f,0f)),domain)
        val paint = Paint(shader = shader, colorFilter = filter, blendMode = BlendMode.SRC, antiAlias = false)
        val surface = Surface(1,1)
        surface.canvas { drawRect(rect(),paint) }
        val recorder = PictureRecorder()
        recorder.beginRecording(rect()).drawRect(rect(),paint)
        val picture = recorder.finishRecordingAsPicture()
        stops[0] = GradientStop(0f,white); stops[1] = GradientStop(1f,white)
        matrix.setRowMajor(FloatArray(20))
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(wanted)) }
        val restored = assertNotNull(Picture.fromByteArray(picture.toByteArray()))
        for (replay in listOf(picture,restored)) {
            val target = Surface(1,1)
            target.canvas { replay.playback(this) }
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(target.render(),listOf(wanted)) }
        }
    }

    @ParameterizedTest(name = "frozen historical Picture {0} public pixel playback")
    @ValueSource(ints = [8,9])
    fun historicalPicturesRetainPublicNearestImagePixels(version: Int) {
        // Frozen repository fixtures predate W5f. Read their RGBA(1,2,3,4)
        // UNPREMUL image through a one-pixel public viewport, including rearchive.
        val image = Image.fromPixels(1,1,byteArrayOf(1,2,3,4))
        val wanted = W5fColorCpuOracle.expectedImagePixel(image, SamplingOptions.NEAREST,
            Point2F32(.5f,.5f), Paint(), finalBlend = BlendMode.SRC_OVER)
        W5fSurfacePixelFixtures.requireBounded(wanted)
        val bytes = Base64.getDecoder().decode(assertNotNull(javaClass.getResource(
            "/picture/format-$version-image-nearest.base64")).readText().trim())
        val historical = assertNotNull(Picture.fromByteArray(bytes))
        val restored = assertNotNull(Picture.fromByteArray(historical.toByteArray()))
        for (picture in listOf(historical, restored)) {
            val surface = Surface(1,1)
            surface.canvas { picture.playback(this) }
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(wanted)) }
        }
    }

    private fun rect() = RectF32.ofLTRB(0f,0f,1f,1f)
    private fun disjoint(a: WgslFloatEnvelopeV1Oracle.DrawResult, b: WgslFloatEnvelopeV1Oracle.DrawResult) {
        W5fSurfacePixelFixtures.requireBounded(a); W5fSurfacePixelFixtures.requireBounded(b)
        a as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded; b as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
        assertTrue(a.channels.indices.any { a.channels[it].intersect(b.channels[it]).isEmpty() })
    }
}
