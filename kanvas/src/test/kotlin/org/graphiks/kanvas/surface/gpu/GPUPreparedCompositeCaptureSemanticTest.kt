package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.SaveLayerRec
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedClipSnapshot
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScopeKind
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedMaskFilterKind
import org.graphiks.kanvas.gpu.renderer.filters.GPUPreparedMaskFilterPlan
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.picture.Picture
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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

    @Test
    fun `saveLayer with backdrop filter is captured not refused`() {
        val backdrop = org.graphiks.kanvas.paint.ImageFilter.Blur(
            sigmaX = 4f, sigmaY = 4f,
        )
        val result = capture(
            listOf(
                DisplayOp.BeginLayer(SaveLayerRec(backdrop = backdrop)),
                DisplayOp.DrawRect(
                    Rect.fromLTRB(0f, 0f, 10f, 10f),
                    black,
                    identity,
                    open,
                ),
                DisplayOp.EndLayer,
            ),
        )

        val ready = assertIs<GPUPreparedCompositeCaptureResult.Ready>(result)
        val layerScope = ready.capture.scopes.values.find { it.sourceKind == GPUPreparedCompositeScopeKind.SaveLayer }
            ?: throw AssertionError("Expected a SaveLayer scope")
        val state = layerScope.state ?: throw AssertionError("Expected scope state")
        assertEquals(true, state.backdropRequired)
    }

    @Test
    fun `saveLayer with backdrop and without backdrop produce different identities`() {
        val body = DisplayOp.DrawRect(Rect.fromLTRB(0f, 0f, 10f, 10f), black, identity, open)
        val backdrop = org.graphiks.kanvas.paint.ImageFilter.Blur(
            sigmaX = 4f, sigmaY = 4f,
        )

        val withBackdrop = capture(
            listOf(DisplayOp.BeginLayer(SaveLayerRec(backdrop = backdrop)), body, DisplayOp.EndLayer),
        )
        val withoutBackdrop = capture(
            listOf(DisplayOp.BeginLayer(SaveLayerRec()), body, DisplayOp.EndLayer),
        )

        assertNotEquals(
            assertIs<GPUPreparedCompositeCaptureResult.Ready>(withBackdrop).capture.identity,
            assertIs<GPUPreparedCompositeCaptureResult.Ready>(withoutBackdrop).capture.identity,
        )
    }

    @Test
    fun `paint with mask blur is captured not refused`() {
        val maskBlurPaint = black.copy(
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, sigma = 4f),
        )
        val rect = Rect.fromLTRB(0f, 0f, 10f, 10f)
        val result = capture(
            listOf(
                DisplayOp.DrawRect(rect, maskBlurPaint, identity, open),
            ),
        )

        val ready = assertIs<GPUPreparedCompositeCaptureResult.Ready>(result)
        val draw = assertIs<GPUPreparedOperationSnapshot.Draw>(
            ready.capture.expandedOperations.single().snapshot,
        )
        val plan = assertNotNull(draw.maskFilterPlan)
        assertEquals(GPUPreparedMaskFilterKind.Blur, plan.kind)
    }

    @Test
    fun `paint with unsupported mask filter is refused`() {
        val tableMaskPaint = black.copy(
            maskFilter = MaskFilter.Table(table = ubyteArrayOf(0u, 128u, 255u)),
        )
        val rect = Rect.fromLTRB(0f, 0f, 10f, 10f)
        val result = capture(
            listOf(
                DisplayOp.DrawRect(rect, tableMaskPaint, identity, open),
            ),
        )

        val refused = assertIs<GPUPreparedCompositeCaptureResult.Refused>(result)
        assertEquals("unsupported.composite.paint", refused.code)
    }

    @Test
    fun `draw picture with image filter creates FilterPictureSource scope`() {
        val picture = Picture(
            Rect.fromLTRB(0f, 0f, 10f, 10f),
            listOf(DisplayOp.DrawRect(Rect.fromLTRB(0f, 0f, 5f, 5f), black, identity, open)),
        )
        val paintWithFilter = black.copy(imageFilter = ImageFilter.Blur(sigmaX = 4f, sigmaY = 4f))

        val result = capture(
            listOf(DisplayOp.DrawPicture(picture, paintWithFilter, identity, open)),
        )

        val ready = assertIs<GPUPreparedCompositeCaptureResult.Ready>(result)
        val filterScopes = ready.capture.scopes.values.filter {
            it.sourceKind == GPUPreparedCompositeScopeKind.FilterPictureSource
        }
        assertEquals(1, filterScopes.size, "Expected exactly one FilterPictureSource scope")
    }

    @Test
    fun `painted picture with image filter and without produce different identities`() {
        val picture = Picture(
            Rect.fromLTRB(0f, 0f, 10f, 10f),
            listOf(DisplayOp.DrawRect(Rect.fromLTRB(0f, 0f, 5f, 5f), black, identity, open)),
        )

        val noFilter = capture(
            listOf(DisplayOp.DrawPicture(picture, black, identity, open)),
        )
        val withFilter = capture(
            listOf(
                DisplayOp.DrawPicture(
                    picture,
                    black.copy(imageFilter = ImageFilter.Blur(sigmaX = 4f, sigmaY = 4f)),
                    identity,
                    open,
                ),
            ),
        )

        assertNotEquals(
            assertIs<GPUPreparedCompositeCaptureResult.Ready>(noFilter).capture.identity,
            assertIs<GPUPreparedCompositeCaptureResult.Ready>(withFilter).capture.identity,
        )
    }

    // Task 17 follow-up: an unpainted DrawPicture inside a saveLayer scope refuses at the
    // capture boundary (unsupported.composite.operation, like every other non-core child).
    // Its expanded children cannot ride the composite commands: the flat mapper never maps
    // them (commandIdsByOperationIndex only records top-level mapped ops), so the old
    // elision silently dropped the picture content. The refusal must stay loud.
    @Test
    fun `unpainted picture inside a saveLayer scope refuses instead of expanding`() {
        val picture = Picture(
            Rect.fromLTRB(0f, 0f, 10f, 10f),
            listOf(DisplayOp.DrawRect(Rect.fromLTRB(0f, 0f, 5f, 5f), black, identity, open)),
        )
        val result = capture(
            listOf(
                DisplayOp.BeginLayer(SaveLayerRec(bounds = Rect.fromLTRB(0f, 0f, 10f, 10f))),
                DisplayOp.DrawPicture(picture, null, identity, open),
                DisplayOp.EndLayer,
            ),
        )

        val refused = assertIs<GPUPreparedCompositeCaptureResult.Refused>(result)
        assertEquals("unsupported.composite.operation", refused.code)
        assertEquals(0, refused.operationIndex)
    }

    @Test
    fun `saveLayer with mixed rect and unpainted picture children refuses`() {
        val picture = Picture(
            Rect.fromLTRB(0f, 0f, 10f, 10f),
            listOf(DisplayOp.DrawRect(Rect.fromLTRB(0f, 0f, 5f, 5f), black, identity, open)),
        )
        val result = capture(
            listOf(
                DisplayOp.BeginLayer(SaveLayerRec(bounds = Rect.fromLTRB(0f, 0f, 10f, 10f))),
                DisplayOp.DrawRect(Rect.fromLTRB(0f, 0f, 4f, 4f), black, identity, open),
                DisplayOp.DrawPicture(picture, null, identity, open),
                DisplayOp.EndLayer,
            ),
        )

        val refused = assertIs<GPUPreparedCompositeCaptureResult.Refused>(result)
        assertEquals("unsupported.composite.operation", refused.code)
        assertEquals(1, refused.operationIndex)
    }

    // FP-06 boundary: vertices inside composite scopes (layer or picture) are not promoted to
    // prepared vertices — the capturer refuses them via the generic OPERATION code until a
    // dedicated vertices-in-composite scope lands. Do not relax without that scope.
    @Test
    fun `vertices inside layer and picture composites stay refused`() {
        val triangle = Vertices(
            mode = VertexMode.TRIANGLES,
            positions = listOf(Point(0f, 0f), Point(10f, 0f), Point(10f, 10f)),
        )
        val verticesOp = DisplayOp.DrawVertices(triangle, black, identity, open)

        val layerScope = capture(
            listOf(DisplayOp.BeginLayer(SaveLayerRec()), verticesOp, DisplayOp.EndLayer),
        )
        val layerRefused = assertIs<GPUPreparedCompositeCaptureResult.Refused>(layerScope)
        assertEquals("unsupported.composite.operation", layerRefused.code)
        assertEquals(0, layerRefused.operationIndex)

        val pictureScope = capture(
            listOf(
                DisplayOp.DrawPicture(
                    picture = Picture(
                        Rect.fromLTRB(0f, 0f, 10f, 10f),
                        listOf(verticesOp),
                    ),
                    paint = null,
                    transform = identity,
                    clip = open,
                ),
            ),
        )
        val pictureRefused = assertIs<GPUPreparedCompositeCaptureResult.Refused>(pictureScope)
        assertEquals("unsupported.composite.operation", pictureRefused.code)
        assertEquals(0, pictureRefused.operationIndex)
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
