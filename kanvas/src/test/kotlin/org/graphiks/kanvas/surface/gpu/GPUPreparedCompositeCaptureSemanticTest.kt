package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedClipSnapshot
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScopeKind
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class GPUPreparedCompositeCaptureSemanticTest {

    private val identity = Matrix33.identity()
    private val open = ClipStack.WideOpen
    private val black = Paint(color = Color.BLACK)
    private val red = Paint(color = Color.RED)

    @Test
    fun `different paths cannot share one placeholder identity`() {
        val first = Path().moveTo(0f, 0f).lineTo(10f, 0f)
        val second = Path().moveTo(0f, 0f).lineTo(20f, 0f)

        assertNotEquals(
            readyIdentity(DisplayOp.DrawPath(first, black, identity, open)),
            readyIdentity(DisplayOp.DrawPath(second, black, identity, open)),
        )
    }

    @Test
    fun `different transforms produce different operation identities`() {
        val rect = Rect.fromLTRB(0f, 0f, 10f, 10f)

        assertNotEquals(
            readyIdentity(DisplayOp.DrawRect(rect, black, Matrix33.translate(1f, 0f), open)),
            readyIdentity(DisplayOp.DrawRect(rect, black, Matrix33.translate(2f, 0f), open)),
        )
    }

    @Test
    fun `different device clips produce different operation identities`() {
        val rect = Rect.fromLTRB(0f, 0f, 10f, 10f)

        assertNotEquals(
            readyIdentity(
                DisplayOp.DrawRect(
                    rect,
                    black,
                    identity,
                    ClipStack.DeviceRect(Rect.fromLTRB(0f, 0f, 5f, 5f)),
                ),
            ),
            readyIdentity(
                DisplayOp.DrawRect(
                    rect,
                    black,
                    identity,
                    ClipStack.DeviceRect(Rect.fromLTRB(1f, 1f, 5f, 5f)),
                ),
            ),
        )
    }

    @Test
    fun `paint effects are refused instead of published as incomplete facts`() {
        val paint = black.copy(shader = Shader.SolidColor(Color.WHITE))
        val result = capture(
            listOf(
                DisplayOp.DrawRect(
                    Rect.fromLTRB(0f, 0f, 10f, 10f),
                    paint,
                    identity,
                    open,
                ),
            ),
        )

        val refused = assertIs<GPUPreparedCompositeCaptureResult.Refused>(result)
        assertEquals("unsupported.composite.paint", refused.code)
    }

    @Test
    fun `save layer paint contributes to the final capture identity`() {
        val body = DisplayOp.DrawRect(Rect.fromLTRB(0f, 0f, 10f, 10f), black, identity, open)

        val blackLayer = capture(
            listOf(DisplayOp.BeginLayer(SaveLayerRec(paint = black)), body, DisplayOp.EndLayer),
        )
        val redLayer = capture(
            listOf(DisplayOp.BeginLayer(SaveLayerRec(paint = red)), body, DisplayOp.EndLayer),
        )

        assertNotEquals(
            assertIs<GPUPreparedCompositeCaptureResult.Ready>(blackLayer).capture.identity,
            assertIs<GPUPreparedCompositeCaptureResult.Ready>(redLayer).capture.identity,
        )
    }

    @Test
    fun `painted picture paint contributes to the final capture identity`() {
        val picture = Picture(
            Rect.fromLTRB(0f, 0f, 10f, 10f),
            listOf(DisplayOp.DrawRect(Rect.fromLTRB(0f, 0f, 5f, 5f), black, identity, open)),
        )

        val blackPicture = capture(
            listOf(DisplayOp.DrawPicture(picture, black, identity, open)),
        )
        val redPicture = capture(
            listOf(DisplayOp.DrawPicture(picture, red, identity, open)),
        )

        assertNotEquals(
            assertIs<GPUPreparedCompositeCaptureResult.Ready>(blackPicture).capture.identity,
            assertIs<GPUPreparedCompositeCaptureResult.Ready>(redPicture).capture.identity,
        )
    }

    @Test
    fun `unpainted picture expands inline with composed transform and clip`() {
        val picture = Picture(
            Rect.fromLTRB(0f, 0f, 10f, 10f),
            listOf(
                DisplayOp.DrawRect(
                    rect = Rect.fromLTRB(0f, 0f, 5f, 5f),
                    paint = black,
                    transform = Matrix33.translate(2f, 0f),
                    clip = ClipStack.DeviceRect(Rect.fromLTRB(2f, 0f, 10f, 10f)),
                ),
            ),
        )
        val ready = assertIs<GPUPreparedCompositeCaptureResult.Ready>(
            capture(
                listOf(
                    DisplayOp.DrawPicture(
                        picture = picture,
                        paint = null,
                        transform = Matrix33.translate(3f, 0f),
                        clip = ClipStack.DeviceRect(Rect.fromLTRB(0f, 0f, 8f, 8f)),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(GPUPreparedCompositeScopeKind.Root),
            ready.capture.scopes.values.map { it.sourceKind },
        )
        val draw = assertIs<GPUPreparedOperationSnapshot.Draw>(
            ready.capture.expandedOperations.single().snapshot,
        )
        assertEquals(5f.toRawBits(), draw.transform.transXBits)
        val clip = assertIs<GPUPreparedClipSnapshot.DeviceRect>(draw.clip)
        assertEquals(5f.toRawBits(), clip.rect.leftBits)
        assertEquals(8f.toRawBits(), clip.rect.rightBits)
    }

    @Test
    fun `self-referential picture is refused without overflowing the stack`() {
        val operations = mutableListOf<DisplayOp>()
        val picture = Picture(Rect.fromLTRB(0f, 0f, 10f, 10f), operations)
        operations += DisplayOp.DrawPicture(picture, null, identity, open)

        val refused = assertIs<GPUPreparedCompositeCaptureResult.Refused>(
            capture(listOf(DisplayOp.DrawPicture(picture, null, identity, open))),
        )
        assertEquals("unsupported.composite.picture.cycle", refused.code)
    }

    @Test
    fun `orphan end layer inside a picture is refused`() {
        val picture = Picture(
            Rect.fromLTRB(0f, 0f, 10f, 10f),
            listOf(DisplayOp.EndLayer),
        )

        val refused = assertIs<GPUPreparedCompositeCaptureResult.Refused>(
            capture(listOf(DisplayOp.DrawPicture(picture, null, identity, open))),
        )

        assertEquals("unsupported.composite.layer.unbalanced", refused.code)
    }

    @Test
    fun `capture rejects an identity that does not match its contents`() {
        val ready = assertIs<GPUPreparedCompositeCaptureResult.Ready>(
            capture(
                listOf(
                    DisplayOp.DrawRect(
                        Rect.fromLTRB(0f, 0f, 10f, 10f),
                        black,
                        identity,
                        open,
                    ),
                ),
            ),
        )

        assertFailsWith<IllegalStateException> {
            GPUPreparedCompositeCapture(
                rootScopeId = ready.capture.rootScopeId,
                scopes = ready.capture.scopes,
                expandedOperations = ready.capture.expandedOperations,
                identity = "injected-identity",
            )
        }
    }

    private fun readyIdentity(op: DisplayOp): String =
        assertIs<GPUPreparedCompositeCaptureResult.Ready>(capture(listOf(op)))
            .capture
            .expandedOperations
            .single()
            .identity

    private fun capture(ops: List<DisplayOp>): GPUPreparedCompositeCaptureResult =
        GPUPreparedCompositeCapturer.capture(
            ops,
            GPUPreparedCompositeCaptureLimits(
                maxRecursionDepth = 8,
                maxNestingDepth = 8,
                maxExpandedOps = 64,
            ),
        )
}
