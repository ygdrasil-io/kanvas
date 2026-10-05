@file:OptIn(ExperimentalUnsignedTypes::class)
package org.graphiks.kanvas.picture

import java.util.Base64
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.surface.W5fSurfacePixelFixtures
import org.graphiks.kanvas.surface.W5gNoiseCpuOracle
import org.graphiks.kanvas.surface.WgslFloatEnvelopeV1Oracle
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.SizeF32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertFailsWith
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class W5gNoisePictureCompatibilityTest {
    @Test fun currentNoisePictureRoundtripsSchema5ComplexClipAndPreservesBoundedRefusal() {
        val noise=Shader.PerlinNoise(.125f,.25f,2,7,null)
        val samples=listOf(.5f,1.5f).map { x ->
            W5gNoiseCpuOracle.expected(noise,Point2F32(x,.5f)).also(W5fSurfacePixelFixtures::requireBounded)
        }
        val transparent=W5gNoiseCpuOracle.expected(Shader.PerlinNoise(.125f,.25f,0,7,null),Point2F32(.5f,.5f))
        // The translated triangle has upper edge y=-2*x+2: left center is inside,
        // right center is outside. Without its captured translation both are outside.
        // Without the clip both centers contain nonzero Noise. Bound both mutants first.
        disjoint(samples[0],transparent)
        disjoint(samples[1],transparent)
        val expected=listOf(samples[0],transparent)
        val recorder=PictureRecorder()
        val canvas=recorder.beginRecording(RectF32.ofLTRB(0f,0f,2f,1f))
        canvas.concat(Matrix3x3F32(tx=1f))
        canvas.clipPath(Path().apply {
            moveTo(-2f,-2f); lineTo(1f,-2f); lineTo(-2f,4f); close()
        },antiAlias=false)
        canvas.resetMatrix()
        canvas.drawPath(Path().apply {
            moveTo(-10f,-10f); lineTo(20f,-10f); lineTo(-10f,20f); close()
        },Paint(shader=noise,blendMode=BlendMode.SRC,antiAlias=false))
        val picture=recorder.finishRecordingAsPicture()
        val encoded=picture.toByteArray()
        val decoded=assertNotNull(Picture.fromByteArray(encoded))
        val reencoded=decoded.toByteArray()
        assertContentEquals(encoded,reencoded)
        val decodedAgain=assertNotNull(Picture.fromByteArray(reencoded))
        assertContentEquals(reencoded,decodedAgain.toByteArray())
        // Schema 5 roundtrips the captured clip. Public composite replay retains its
        // documented refusal; the analytical clip discriminants are not rendered pixels.
        for (value in listOf(picture,decoded,decodedAgain)) {
            val surface=Surface(expected.size,1)
            surface.canvas { drawPicture(value) }
            repeat(2) {
                val failure=assertFailsWith<IllegalStateException> { surface.render() }
                assertTrue(failure.message.orEmpty().startsWith("unsupported.composite.clip:"),
                    "Schema 5 complex clip replay must preserve its bounded refusal: ${failure.message}")
            }
        }
    }

    @Test fun capturedNoiseFilterMutationCannotChangeSurfaceOrPicture() {
        val leaf=Shader.Blend(BlendMode.SRC_OVER,Shader.PerlinNoise(.125f,.25f,2,7,SizeI32(8,4)),
            Shader.FractalNoise(.125f,.25f,2,1,null))
        val matrix=ColorMatrixF32.ofIdentity().apply { setScale(.5f,1f,1f,1f) }
        val filter=ColorFilter.Matrix(matrix)
        val expected=W5gNoiseCpuOracle.expected(leaf,Point2F32(.5f,.5f),external=filter)
        val changed=W5gNoiseCpuOracle.expected(leaf,Point2F32(.5f,.5f),external=ColorFilter.Matrix(
            ColorMatrixF32.ofIdentity().apply { setScale(0f,.5f,1f,1f) }))
        disjoint(expected,changed)
        val paint=Paint(shader=leaf,colorFilter=filter,blendMode=BlendMode.SRC,antiAlias=false)
        val surface=Surface(1,1)
        surface.canvas { drawRect(RectF32.ofLTRB(0f,0f,1f,1f),paint) }
        val recorder=PictureRecorder()
        recorder.beginRecording(RectF32.ofLTRB(0f,0f,1f,1f)).drawRect(RectF32.ofLTRB(0f,0f,1f,1f),paint)
        val picture=recorder.finishRecordingAsPicture()
        matrix.setScale(0f,.5f,1f,1f)
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
        replay(picture,listOf(expected))
        replay(assertNotNull(Picture.fromByteArray(picture.toByteArray())),listOf(expected))
    }

    @ParameterizedTest(name="genuine historical Noise writer version {0}")
    @ValueSource(ints=[8,9,10])
    fun genuineHistoricalAbsentZeroIntegralTilesReplay(version: Int) {
        val cases=listOf("absent" to null,"zero" to SizeI32(0,4),"integral" to SizeI32(8,4)).map { (name,tile) ->
            val shaders=listOf(Shader.PerlinNoise(.125f,.25f,1,7,tile),Shader.FractalNoise(.125f,.25f,1,7,tile))
            val expected=shaders.mapIndexed { index,shader -> W5gNoiseCpuOracle.expected(shader,Point2F32(index+.5f,.5f)) }
            expected.forEach(W5fSurfacePixelFixtures::requireBounded)
            val zero=listOf(Shader.PerlinNoise(.125f,.25f,0,7,null),Shader.FractalNoise(.125f,.25f,0,7,null))
                .mapIndexed { index,shader -> W5gNoiseCpuOracle.expected(shader,Point2F32(index+.5f,.5f)) }
            expected.zip(zero).forEach { (a,b) -> disjoint(a,b) }
            println("Historical Noise version=$version tile=$name independent=${expected.map(::channels)} zero=${zero.map(::channels)}")
            Triple(name,shaders,expected)
        }
        for((name,_,expected) in cases) {
            val picture=assertNotNull(Picture.fromByteArray(fixture(version,name)))
            replay(picture,expected)
            // Current writer/public reader roundtrip follows the genuine historical decode.
            replay(assertNotNull(Picture.fromByteArray(picture.toByteArray())),expected)
        }
    }

    @ParameterizedTest(name="malformed historical Noise tile boundary version {0}")
    @ValueSource(ints=[8,9,10])
    fun malformedTilesRejectAndValidPictureRecovers(version: Int) {
        val expected=listOf(Shader.PerlinNoise(.125f,.25f,1,7,null),Shader.FractalNoise(.125f,.25f,1,7,null))
            .mapIndexed { index,shader -> W5gNoiseCpuOracle.expected(shader,Point2F32(index+.5f,.5f)) }
        expected.forEach(W5fSurfacePixelFixtures::requireBounded)
        // Fractional/out-of-range bytes are genuine old writer output. NaN/Infinity are
        // deliberately malformed documented old-wire payloads: old writers rejected them.
        for(name in listOf("fractional","out-of-range","nan","infinity"))
            assertNull(Picture.fromByteArray(fixture(version,name)),"version=$version tile=$name")
        replay(assertNotNull(Picture.fromByteArray(fixture(version,"absent"))),expected)
    }

    @Test fun sourceCompatibleTileConstructorsRenderAndRoundtrip() {
        val floatNull: SizeF32?=null
        val floatTile: SizeF32?=SizeF32(8f,4f)
        val integralTile: SizeI32?=SizeI32(8,4)
        @Suppress("DEPRECATION")
        val leaves=listOf(
            Shader.PerlinNoise(.125f,.25f,1,7,null),
            Shader.PerlinNoise(baseX=.125f,baseY=.25f,numOctaves=1,seed=7,tileSize=null),
            Shader.PerlinNoise(.125f,.25f,1,7,floatNull),Shader.PerlinNoise(.125f,.25f,1,7,floatTile),
            Shader.PerlinNoise(.125f,.25f,1,7,integralTile),
            Shader.FractalNoise(.125f,.25f,1,7,null),
            Shader.FractalNoise(baseX=.125f,baseY=.25f,numOctaves=1,seed=7,tileSize=null),
            Shader.FractalNoise(.125f,.25f,1,7,floatNull),Shader.FractalNoise(.125f,.25f,1,7,floatTile),
            Shader.FractalNoise(.125f,.25f,1,7,integralTile))
        val cases=leaves.map { shader -> shader to W5gNoiseCpuOracle.expected(shader,Point2F32(.5f,.5f)).also {
            W5fSurfacePixelFixtures.requireBounded(it)
        } }
        cases.forEach { (shader,expected) ->
            val paint=Paint(shader=shader,blendMode=BlendMode.SRC,antiAlias=false)
            val surface=Surface(1,1)
            surface.canvas { drawRect(RectF32(0f,0f,1f,1f),paint) }
            W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected))
            val recorder=PictureRecorder()
            recorder.beginRecording(RectF32(0f,0f,1f,1f)).drawRect(RectF32(0f,0f,1f,1f),paint)
            val picture=recorder.finishRecordingAsPicture()
            replay(picture,listOf(expected))
            replay(assertNotNull(Picture.fromByteArray(picture.toByteArray())),listOf(expected))
        }
    }

    @Test fun legacyFloatTileConversionRejectsBeforeSaturating() {
        for(tile in listOf(SizeF32(.5f,4f),SizeF32(-1f,4f),SizeF32(Float.NaN,4f),
            SizeF32(Float.POSITIVE_INFINITY,4f),SizeF32(2147483648f,4f),SizeF32(4f,.5f))) {
            @Suppress("DEPRECATION")
            assertFailsWith<IllegalArgumentException> { Shader.PerlinNoise(.125f,.25f,1,7,tile) }
            @Suppress("DEPRECATION")
            assertFailsWith<IllegalArgumentException> { Shader.FractalNoise(.125f,.25f,1,7,tile) }
        }
    }

    private fun fixture(version: Int,name: String): ByteArray = Base64.getDecoder().decode(
        requireNotNull(javaClass.getResourceAsStream("/picture/format-$version-noise-$name.base64"))
            .bufferedReader().use { it.readText().trim() })
    private fun replay(picture: Picture,expected: List<WgslFloatEnvelopeV1Oracle.DrawResult>) {
        val surface=Surface(expected.size,1)
        surface.canvas { picture.playback(this) }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),expected) }
    }
    private fun channels(value: WgslFloatEnvelopeV1Oracle.DrawResult) =
        (value as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded).channels
    private fun disjoint(a: WgslFloatEnvelopeV1Oracle.DrawResult,b: WgslFloatEnvelopeV1Oracle.DrawResult) {
        W5fSurfacePixelFixtures.requireBounded(a); W5fSurfacePixelFixtures.requireBounded(b)
        assertTrue(channels(a).zip(channels(b)).any { (left,right) -> left.intersect(right).isEmpty() })
    }
}
