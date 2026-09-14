@file:OptIn(ExperimentalUnsignedTypes::class)
package org.graphiks.kanvas.picture

import org.graphiks.kanvas.paint.*
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.surface.*
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

class W5gComposedMaterialPictureTest {
    @Test fun twoImagePixelsAndFilterStayCapturedThroughOriginalAndDecodedPictures() {
        val first=byteArrayOf(-1,0,0,-1,0,0,-1,-128)
        val second=byteArrayOf(0,0,-1,-128,-1,0,0,-1)
        val matrix=ColorMatrixF32.ofIdentity().apply { setScale(.5f,1f,1f,1f) }
        fun tree(a: ByteArray,b: ByteArray,filter: ColorFilter,reversed: Boolean=false): Shader {
            val dst=Shader.WithColorFilter(Shader.Image(Image.fromPixels(2,1,a)),filter)
            val src=Shader.Image(Image.fromPixels(2,1,b))
            return if(reversed) Shader.Blend(BlendMode.SRC_OVER,src,dst) else Shader.Blend(BlendMode.SRC_OVER,dst,src)
        }
        val filter=ColorFilter.Matrix(matrix)
        val shader=tree(first,second,filter)
        val expected=W5fColorCpuOracle.expectedShaderTree(shader)
        val changed=byteArrayOf(0,-1,0,-1,0,-1,0,-1)
        val changedFilter=ColorFilter.Matrix(ColorMatrixF32.ofIdentity().apply { setScale(.125f,1f,1f,1f) })
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(tree(first,second,filter,true)))
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(tree(changed,changed,filter)))
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(tree(first,second,changedFilter)))
        val paint=Paint(shader=shader,blendMode=BlendMode.SRC,antiAlias=false)
        val surface=Surface(1,1)
        surface.canvas { drawRect(rect(),paint) }
        val recorder=PictureRecorder()
        recorder.beginRecording(rect()).drawRect(rect(),paint)
        val picture=recorder.finishRecordingAsPicture()
        changed.copyInto(first); changed.copyInto(second); matrix.setScale(.125f,1f,1f,1f)
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
        val decoded=assertNotNull(Picture.fromByteArray(picture.toByteArray()))
        for(replay in listOf(picture,decoded)) {
            val target=Surface(1,1)
            target.canvas { replay.playback(this) }
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(target.render(),listOf(expected)) }
        }
    }

    @Test fun sharedGradientStopsLocalMatrixAndFilterRemainCapturedOnBothPicturePaths() {
        val stops=mutableListOf(GradientStop(0f,ColorARGB.Black.withAlpha(128)),GradientStop(1f,ColorARGB.White.withAlpha(128)))
        val local=Matrix3x3F32(tx=-.25f)
        val matrix=ColorMatrixF32.ofIdentity().apply { setScale(.75f,1f,1f,1f) }
        val filter=ColorFilter.Matrix(matrix)
        fun tree(stops: List<GradientStop>,local: Matrix3x3F32,filter: ColorFilter,reversed: Boolean=false): Shader {
            val leaf=Shader.LinearGradient(Point2F32(0f,0f),Point2F32(1f,0f),stops)
            val shared=Shader.WithColorFilter(Shader.WithLocalMatrix(leaf,local),filter)
            val dst=Shader.Opacity(shared,.5f)
            val src=Shader.WithLocalMatrix(shared,Matrix3x3F32(tx=.5f))
            return if(reversed) Shader.Blend(BlendMode.SRC_OVER,src,dst) else Shader.Blend(BlendMode.SRC_OVER,dst,src)
        }
        val shader=tree(stops,local,filter)
        val expected=W5fColorCpuOracle.expectedShaderTree(shader)
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(tree(stops,local,filter,true)))
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(tree(
            listOf(GradientStop(0f,ColorARGB.Blue),GradientStop(1f,ColorARGB.Blue)),local,filter)))
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(tree(stops,local,
            ColorFilter.Matrix(ColorMatrixF32.ofIdentity().apply { setScale(.25f,1f,1f,1f) }))))
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(tree(
            listOf(GradientStop(0f,ColorARGB.Blue),GradientStop(1f,ColorARGB.Blue)),local,
            ColorFilter.Matrix(ColorMatrixF32.ofIdentity().apply { setScale(.25f,1f,1f,1f) }))))
        val paint=Paint(shader=shader,blendMode=BlendMode.SRC,antiAlias=false)
        val surface=Surface(1,1)
        surface.canvas { drawRect(rect(),paint) }
        val recorder=PictureRecorder()
        recorder.beginRecording(rect()).drawRect(rect(),paint)
        val picture=recorder.finishRecordingAsPicture()
        stops[0]=GradientStop(0f,ColorARGB.Blue); stops[1]=GradientStop(1f,ColorARGB.Blue)
        // Shader-local Matrix3x3F32 is immutable; the shared ColorMatrixF32
        // filter payload and original stop list are the mutable public inputs.
        matrix.setScale(.25f,1f,1f,1f)
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
        val decoded=assertNotNull(Picture.fromByteArray(picture.toByteArray()))
        for(replay in listOf(picture,decoded)) {
            val target=Surface(1,1)
            target.canvas { replay.playback(this) }
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(target.render(),listOf(expected)) }
        }
    }

    @Test fun largestSharedOccurrenceTreeRetainsThePublicLimit() {
        val leaf = Shader.SolidColor(ColorARGB.Red)
        fun tree(depth: Int): Shader = if (depth == 0) leaf
            else Shader.Blend(BlendMode.SRC,tree(depth-1),tree(depth-1))
        // 4095 original occurrences are admitted; synthesized paint-alpha and
        // external-filter entries must not consume the public shader limit.
        val shader = tree(11)
        val expected = W5fColorCpuOracle.expectedShaderTree(leaf)
        W5fSurfacePixelFixtures.requireBounded(expected)
        val surface = Surface(1,1)
        surface.canvas { drawRect(rect(),Paint(shader=shader,blendMode=BlendMode.SRC,antiAlias=false)) }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
    }

    private fun rect() = RectF32.ofLTRB(0f,0f,1f,1f)
    private fun disjoint(a: WgslFloatEnvelopeV1Oracle.DrawResult,b: WgslFloatEnvelopeV1Oracle.DrawResult) {
        W5fSurfacePixelFixtures.requireBounded(a); W5fSurfacePixelFixtures.requireBounded(b)
        a as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded; b as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
        assertTrue(a.channels.indices.any { a.channels[it].intersect(b.channels[it]).isEmpty() },
            "Counterfactual must be distinct: ${a.channels}/${b.channels}")
    }

    @Test fun occurrenceLimitsRejectBeforeCaptureAndRecoverOnSameOwners() {
        val shared = Shader.SolidColor(ColorARGB.Red)
        var deep: Shader = Shader.Blend(BlendMode.SRC_OVER,shared,shared)
        repeat(65) { deep = Shader.Opacity(deep,.5f) }
        fun tree(leaves: Int,sharedLeaf: Boolean): Shader = if (leaves == 1)
            if (sharedLeaf) shared else Shader.SolidColor(ColorARGB.Red)
            else Shader.Blend(BlendMode.SRC_OVER,tree(leaves/2,sharedLeaf),tree(leaves-leaves/2,sharedLeaf))
        // A full binary tree has 2*2049-1 = 4097 counted occurrences, whether
        // leaves are the same object or distinct captured objects.
        val invalid = listOf(deep to "graph-depth-limit",tree(2049,false) to "graph-node-limit",
            tree(2049,true) to "graph-node-limit")
        val valid = Shader.Blend(BlendMode.SRC_OVER,Shader.Opacity(shared,.5f),Shader.Opacity(shared,.25f))
        val expected = W5fColorCpuOracle.expectedShaderTree(valid)
        W5fSurfacePixelFixtures.requireBounded(expected)
        for ((shader,code) in invalid) {
            val recorder = PictureRecorder()
            val canvas = recorder.beginRecording(rect())
            val failure = assertFailsWith<org.graphiks.kanvas.canvas.SceneRecordingValidationException> {
                canvas.drawRect(rect(),Paint(shader=shader,antiAlias=false))
            }
            assertEquals(code,failure.diagnostic.code.value)
            canvas.drawRect(rect(),Paint(shader=valid,blendMode=BlendMode.SRC,antiAlias=false))
            val picture = recorder.finishRecordingAsPicture()
            val surface = Surface(1,1)
            surface.canvas {
                val surfaceFailure = assertFailsWith<org.graphiks.kanvas.canvas.SceneRecordingValidationException> {
                    drawRect(rect(),Paint(shader=shader,antiAlias=false))
                }
                assertEquals(code,surfaceFailure.diagnostic.code.value)
                picture.playback(this)
            }
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
        }
    }

    @Test fun sharedMutableMatrixAndTableRemainCapturedAcrossSurfaceAndPictureReplay() {
        for (tableKind in listOf(false,true)) {
            val matrix = ColorMatrixF32.ofIdentity().apply { setScale(.5f,1f,1f,1f) }
            val bytes = UByteArray(256) { it.toUByte() }
            val filter = if (tableKind) ColorFilter.Table(bytes) else ColorFilter.Matrix(matrix)
            fun tree(f: ColorFilter,reversed: Boolean = false): Shader {
                val shared = Shader.WithColorFilter(Shader.SolidColor(ColorARGB.Red),f)
                val dst = Shader.Opacity(shared,.5f)
                val src = Shader.WithColorFilter(Shader.Opacity(shared,.25f),ColorFilter.Matrix(
                    ColorMatrixF32.ofIdentity().apply { postTranslate(0f,.25f,0f,0f) }))
                return if (reversed) Shader.Blend(BlendMode.SRC_OVER,src,dst)
                    else Shader.Blend(BlendMode.SRC_OVER,dst,src)
            }
            val shader = tree(filter)
            val expected = W5fColorCpuOracle.expectedShaderTree(shader)
            disjoint(expected,W5fColorCpuOracle.expectedShaderTree(tree(filter,true)))
            val changed = if (tableKind) ColorFilter.Table(UByteArray(256) { 64u })
                else ColorFilter.Matrix(ColorMatrixF32.ofIdentity().apply { setScale(0f,1f,1f,1f) })
            disjoint(expected,W5fColorCpuOracle.expectedShaderTree(tree(changed)))
            val paint = Paint(shader=shader,blendMode=BlendMode.SRC,antiAlias=false)
            val surface = Surface(1,1)
            surface.canvas { drawRect(rect(),paint) }
            val recorder = PictureRecorder()
            recorder.beginRecording(rect()).drawRect(rect(),paint)
            val picture = recorder.finishRecordingAsPicture()
            matrix.setScale(0f,1f,1f,1f); bytes.fill(64u)
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
            val decoded = assertNotNull(Picture.fromByteArray(picture.toByteArray()))
            for (replay in listOf(picture,decoded)) {
                val target = Surface(1,1)
                target.canvas { replay.playback(this) }
                repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(target.render(),listOf(expected)) }
            }
        }
    }
}
