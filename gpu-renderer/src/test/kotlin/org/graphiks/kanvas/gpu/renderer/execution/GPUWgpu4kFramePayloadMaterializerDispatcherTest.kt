package org.graphiks.kanvas.gpu.renderer.execution

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload

class GPUWgpu4kFramePayloadMaterializerDispatcherTest {
    @Test
    fun `dispatcher selects every typed prepared route from semantic classes`() {
        assertEquals(
            GPUWgpu4kPreparedFramePayloadRoute.SolidRect,
            selectWgpu4kPreparedFramePayloadRoute(listOf(GPUDrawSemanticPayload.SolidRect::class)),
        )
        assertEquals(
            GPUWgpu4kPreparedFramePayloadRoute.ColorGlyph,
            selectWgpu4kPreparedFramePayloadRoute(listOf(GPUDrawSemanticPayload.ColorGlyph::class)),
        )
        assertEquals(
            GPUWgpu4kPreparedFramePayloadRoute.RegisteredUniformRect,
            selectWgpu4kPreparedFramePayloadRoute(
                listOf(GPUDrawSemanticPayload.RegisteredUniformRect::class),
            ),
        )
    }

    @Test
    fun `dispatcher refuses mixed solid and color shapes before invoking a native delegate`() {
        val route = selectWgpu4kPreparedFramePayloadRoute(
            listOf(
                GPUDrawSemanticPayload.SolidRect::class,
                GPUDrawSemanticPayload.ColorGlyph::class,
            ),
        )

        val refused = assertIs<GPUWgpu4kPreparedFramePayloadRoute.Refused>(route)
        assertEquals("unsupported.native-frame-payload.mixed-semantic-shape", refused.code)
    }
}
