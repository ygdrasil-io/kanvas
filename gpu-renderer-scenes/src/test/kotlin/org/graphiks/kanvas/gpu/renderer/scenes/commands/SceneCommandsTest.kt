package org.graphiks.kanvas.gpu.renderer.scenes.commands

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand

class SceneCommandsTest {
    @Test
    fun `fill rect command converts to gpu renderer normalized command`() {
        val target = SceneTarget(width = 320, height = 200, colorFormat = "bgra8unorm")
        val command = SceneCommand.FillRect(
            label = "front-card",
            rect = SceneRect(24f, 32f, 180f, 112f),
            color = SceneColor(0.1f, 0.35f, 0.9f, 0.88f),
            paintOrder = 2,
        )

        val normalized = command.toNormalizedFillRect(commandIndex = 7, target = target)

        assertIs<NormalizedDrawCommand.FillRect>(normalized)
        assertEquals(7, normalized.commandId.value)
        assertEquals(GPURect(24f, 32f, 180f, 112f), normalized.rect)
        assertEquals(320, normalized.layer.target.width)
        assertEquals(200, normalized.layer.target.height)
        assertEquals("bgra8unorm", normalized.layer.target.colorFormat)
        assertEquals("gpu-renderer-scenes", normalized.source.adapter)
        assertEquals("front-card", normalized.source.operation)
        assertEquals(2, normalized.ordering.paintOrder)
        val material = assertIs<GPUMaterialDescriptor.SolidColor>(normalized.material)
        assertEquals(0.1f, material.r)
        assertEquals(0.35f, material.g)
        assertEquals(0.9f, material.b)
        assertEquals(0.88f, material.a)
    }

    @Test
    fun `scene command family exposes business friendly names`() {
        assertEquals("fill-rect", SceneCommand.FillRect("card", SceneRect(0f, 0f, 8f, 8f), SceneColor.red()).family)
        assertEquals(
            "linear-gradient-rect",
            SceneCommand.LinearGradientRect(
                "gradient",
                SceneRect(0f, 0f, 8f, 8f),
                SceneColor.red(),
                SceneColor.blue(),
            ).family,
        )
        assertEquals("clip", SceneCommand.Clip("clip", SceneRect(0f, 0f, 8f, 8f)).family)
        assertEquals(
            "bitmap-rect",
            SceneCommand.BitmapRect(
                label = "bitmap",
                rect = SceneRect(0f, 0f, 8f, 8f),
                source = SceneBitmapSource(
                    topLeft = SceneColor.red(),
                    topRight = SceneColor.blue(),
                    bottomLeft = SceneColor.green(),
                    bottomRight = SceneColor.amber(),
                ),
            ).family,
        )
        assertEquals(
            "filter-node",
            SceneCommand.FilterNode(
                label = "luma-filter",
                inputLabel = "bitmap",
                kind = SceneFilterKind.LumaTint,
                strength = 0.65f,
            ).family,
        )
        assertEquals(
            "save-layer",
            SceneCommand.SaveLayer(
                label = "shadow-card-layer",
                bounds = SceneRect(32f, 28f, 288f, 172f),
                contentRect = SceneRect(48f, 44f, 270f, 154f),
                radius = 20f,
                contentColor = SceneColor(0.98f, 0.98f, 0.94f, 1f),
                shadowColor = SceneColor(0.02f, 0.04f, 0.07f, 0.44f),
            ).family,
        )
        assertEquals("runtime-effect", SceneCommand.RuntimeEffectTile("simple-rt").family)
        assertEquals("vertices", SceneCommand.MeshRibbon("mesh").family)
    }

    @Test
    fun `text run fixture payload names real font and explicit unpromoted GPU routes`() {
        val command = SceneCommand.TextRun(
            label = "receipt-line",
            text = "TOTAL 42.00",
            baselineX = 42f,
            baselineY = 118f,
            fontSourceId = "kanvas-skia/src/main/resources/fonts/liberation/LiberationSans-Regular.ttf",
            fontFamily = "Liberation Sans",
            fontSize = 28f,
            color = SceneColor(0.08f, 0.09f, 0.10f, 1f),
        )

        assertTrue(command.hasFixturePayload)
        assertEquals("simple-latin", command.shapingMode)
        assertEquals("font.glyph.outline-path", command.glyphRoute)
        assertEquals("webgpu.text.glyph-atlas.simple-latin", command.webGpuCandidateRoute)
        assertEquals("unsupported.text.draw_run_route_unavailable", command.fallbackReason)
        assertEquals(0, command.paintOrder)
    }

    @Test
    fun `save layer fixture payload names the bounded shadow card contract`() {
        val command = SceneCommand.SaveLayer(
            label = "shadow-card-layer",
            bounds = SceneRect(32f, 28f, 288f, 172f),
            contentRect = SceneRect(48f, 44f, 270f, 154f),
            radius = 20f,
            contentColor = SceneColor(0.98f, 0.98f, 0.94f, 1f),
            shadowColor = SceneColor(0.02f, 0.04f, 0.07f, 0.44f),
            shadowOffsetX = 10f,
            shadowOffsetY = 12f,
            paintOrder = 2,
        )

        assertTrue(command.hasFixturePayload)
        assertEquals("bounded-shadow-card", command.layerKind)
        assertEquals(SceneRect(32f, 28f, 288f, 172f), command.bounds)
        assertEquals(SceneRect(48f, 44f, 270f, 154f), command.contentRect)
        assertEquals(SceneRect(58f, 56f, 280f, 166f), command.shadowRect)
        assertEquals(20f, command.radius)
        assertEquals(2, command.paintOrder)
    }

    @Test
    fun `runtime effect tile fixture payload names the registered SimpleRT descriptor contract`() {
        val command = SceneCommand.RuntimeEffectTile(
            label = "simple-rt-color",
            rect = SceneRect(48f, 36f, 272f, 164f),
            stableId = "runtime.simple_rt",
            wgslImplementationId = "wgsl/runtime_simple_rt",
            uniformColor = SceneColor(0.18f, 0.42f, 0.72f, 1f),
        )

        assertTrue(command.hasFixturePayload)
        assertEquals("runtime.simple_rt", command.stableId)
        assertEquals("wgsl/runtime_simple_rt", command.wgslImplementationId)
        assertEquals("kotlin/simple_rt", command.cpuImplementationId)
        assertEquals("gColor", command.uniformName)
        assertEquals("kFloat4", command.uniformType)
        assertEquals(0, command.uniformOffset)
        assertEquals(16, command.uniformSize)
        assertEquals("runtimeEffect=SimpleRT descriptor=runtime_simple_rt.wgsl state=[blendMode=kSrcOver]", command.pipelineKey)
    }

    @Test
    fun `mesh ribbon fixture payload names the bounded ribbon strip contract`() {
        val command = SceneCommand.MeshRibbon(
            label = "ribbon",
            bounds = SceneRect(36f, 42f, 284f, 158f),
            startColor = SceneColor(0.10f, 0.52f, 0.86f, 1f),
            endColor = SceneColor(0.98f, 0.62f, 0.18f, 1f),
            thickness = 28f,
            paintOrder = 3,
        )

        assertTrue(command.hasFixturePayload)
        assertEquals("bounded-ribbon-strip", command.meshKind)
        assertEquals(SceneRect(36f, 42f, 284f, 158f), command.bounds)
        assertEquals(SceneColor(0.10f, 0.52f, 0.86f, 1f), command.startColor)
        assertEquals(SceneColor(0.98f, 0.62f, 0.18f, 1f), command.endColor)
        assertEquals(28f, command.thickness)
        assertEquals(3, command.paintOrder)
    }

    @Test
    fun `label bearing scene commands reject blank labels`() {
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.FillRect(" ", SceneRect(0f, 0f, 8f, 8f), SceneColor.red())
        }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.LinearGradientRect(
                " ",
                SceneRect(0f, 0f, 8f, 8f),
                SceneColor.red(),
                SceneColor.blue(),
            )
        }
        assertFailsWith<IllegalArgumentException> { SceneCommand.Clip("", SceneRect(0f, 0f, 8f, 8f)) }
        assertFailsWith<IllegalArgumentException> { SceneCommand.BitmapRect(" ") }
        assertFailsWith<IllegalArgumentException> { SceneCommand.SaveLayer(" ") }
        assertFailsWith<IllegalArgumentException> { SceneCommand.TextRun(" ") }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.FilterNode(
                label = "filter",
                inputLabel = " ",
                kind = SceneFilterKind.DropShadow,
            )
        }
        assertFailsWith<IllegalArgumentException> { SceneCommand.RuntimeEffectTile("") }
        assertFailsWith<IllegalArgumentException> { SceneCommand.MeshRibbon("\t") }
    }

    @Test
    fun `save layer fixture payload requires bounds content and shadow together`() {
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.SaveLayer(
                label = "shadow-card-layer",
                contentRect = SceneRect(48f, 44f, 270f, 154f),
                contentColor = SceneColor(0.98f, 0.98f, 0.94f, 1f),
                shadowColor = SceneColor(0.02f, 0.04f, 0.07f, 0.44f),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.SaveLayer(
                label = "shadow-card-layer",
                bounds = SceneRect(32f, 28f, 288f, 172f),
                contentColor = SceneColor(0.98f, 0.98f, 0.94f, 1f),
                shadowColor = SceneColor(0.02f, 0.04f, 0.07f, 0.44f),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.SaveLayer(
                label = "shadow-card-layer",
                bounds = SceneRect(32f, 28f, 288f, 172f),
                contentRect = SceneRect(48f, 44f, 270f, 154f),
                shadowColor = SceneColor(0.02f, 0.04f, 0.07f, 0.44f),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.SaveLayer(
                label = "shadow-card-layer",
                bounds = SceneRect(32f, 28f, 288f, 172f),
                contentRect = SceneRect(48f, 44f, 270f, 154f),
                contentColor = SceneColor(0.98f, 0.98f, 0.94f, 1f),
            )
        }
    }

    @Test
    fun `text run fixture payload requires text position font and color together`() {
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.TextRun(
                label = "receipt-line",
                baselineX = 42f,
                baselineY = 118f,
                fontSourceId = "kanvas-skia/src/main/resources/fonts/liberation/LiberationSans-Regular.ttf",
                fontFamily = "Liberation Sans",
                fontSize = 28f,
                color = SceneColor(0.08f, 0.09f, 0.10f, 1f),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.TextRun(
                label = "receipt-line",
                text = "TOTAL 42.00",
                baselineY = 118f,
                fontSourceId = "kanvas-skia/src/main/resources/fonts/liberation/LiberationSans-Regular.ttf",
                fontFamily = "Liberation Sans",
                fontSize = 28f,
                color = SceneColor(0.08f, 0.09f, 0.10f, 1f),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.TextRun(
                label = "receipt-line",
                text = "TOTAL 42.00",
                baselineX = 42f,
                baselineY = 118f,
                fontSourceId = " ",
                fontFamily = "Liberation Sans",
                fontSize = 28f,
                color = SceneColor(0.08f, 0.09f, 0.10f, 1f),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.TextRun(
                label = "receipt-line",
                text = "TOTAL 42.00",
                baselineX = 42f,
                baselineY = 118f,
                fontSourceId = "kanvas-skia/src/main/resources/fonts/liberation/LiberationSans-Regular.ttf",
                fontFamily = "Liberation Sans",
                fontSize = 0f,
                color = SceneColor(0.08f, 0.09f, 0.10f, 1f),
            )
        }
    }

    @Test
    fun `filter node fixture payload requires an input kind and normalized strength`() {
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.FilterNode(label = "filter", inputLabel = "photo")
        }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.FilterNode(label = "filter", kind = SceneFilterKind.LumaTint)
        }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.FilterNode(
                label = "filter",
                inputLabel = "photo",
                kind = SceneFilterKind.LumaTint,
                strength = -0.01f,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.FilterNode(
                label = "filter",
                inputLabel = "photo",
                kind = SceneFilterKind.LumaTint,
                strength = 1.01f,
            )
        }
    }

    @Test
    fun `runtime effect tile fixture payload requires descriptor rect and uniform color together`() {
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.RuntimeEffectTile(
                label = "simple-rt-color",
                stableId = "runtime.simple_rt",
                wgslImplementationId = "wgsl/runtime_simple_rt",
                uniformColor = SceneColor.blue(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.RuntimeEffectTile(
                label = "simple-rt-color",
                rect = SceneRect(0f, 0f, 8f, 8f),
                wgslImplementationId = "wgsl/runtime_simple_rt",
                uniformColor = SceneColor.blue(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.RuntimeEffectTile(
                label = "simple-rt-color",
                rect = SceneRect(0f, 0f, 8f, 8f),
                stableId = "runtime.simple_rt",
                uniformColor = SceneColor.blue(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.RuntimeEffectTile(
                label = "simple-rt-color",
                rect = SceneRect(0f, 0f, 8f, 8f),
                stableId = "runtime.simple_rt",
                wgslImplementationId = "wgsl/runtime_simple_rt",
            )
        }
    }

    @Test
    fun `mesh ribbon fixture payload requires bounds and endpoint colors together`() {
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.MeshRibbon(
                label = "ribbon",
                startColor = SceneColor.blue(),
                endColor = SceneColor.amber(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.MeshRibbon(
                label = "ribbon",
                bounds = SceneRect(0f, 0f, 8f, 8f),
                endColor = SceneColor.amber(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.MeshRibbon(
                label = "ribbon",
                bounds = SceneRect(0f, 0f, 8f, 8f),
                startColor = SceneColor.blue(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.MeshRibbon(
                label = "ribbon",
                bounds = SceneRect(0f, 0f, 8f, 8f),
                startColor = SceneColor.blue(),
                endColor = SceneColor.amber(),
                thickness = 0f,
            )
        }
    }

    @Test
    fun `fill rect command rejects negative paint order`() {
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.FillRect("card", SceneRect(0f, 0f, 8f, 8f), SceneColor.red(), paintOrder = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.LinearGradientRect(
                "gradient",
                SceneRect(0f, 0f, 8f, 8f),
                SceneColor.red(),
                SceneColor.blue(),
                paintOrder = -1,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.BitmapRect(
                label = "bitmap",
                rect = SceneRect(0f, 0f, 8f, 8f),
                source = SceneBitmapSource(
                    topLeft = SceneColor.red(),
                    topRight = SceneColor.blue(),
                    bottomLeft = SceneColor.green(),
                    bottomRight = SceneColor.amber(),
                ),
                paintOrder = -1,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.SaveLayer(
                label = "shadow-card-layer",
                bounds = SceneRect(0f, 0f, 16f, 16f),
                contentRect = SceneRect(2f, 2f, 12f, 12f),
                contentColor = SceneColor.green(),
                shadowColor = SceneColor(0f, 0f, 0f, 0.25f),
                paintOrder = -1,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            SceneCommand.MeshRibbon(
                label = "ribbon",
                bounds = SceneRect(0f, 0f, 8f, 8f),
                startColor = SceneColor.blue(),
                endColor = SceneColor.amber(),
                paintOrder = -1,
            )
        }
    }

    @Test
    fun `scene rect rejects non finite coordinates`() {
        val infinite = assertFailsWith<IllegalArgumentException> {
            SceneRect(0f, 0f, Float.POSITIVE_INFINITY, 8f)
        }
        val nan = assertFailsWith<IllegalArgumentException> {
            SceneRect(0f, Float.NaN, 8f, 8f)
        }

        assertTrue(infinite.message.orEmpty().contains("finite"))
        assertTrue(nan.message.orEmpty().contains("finite"))
    }

    @Test
    fun `scene color rejects channels outside normalized range`() {
        assertFailsWith<IllegalArgumentException> { SceneColor(1.1f, 0f, 0f) }
    }
}
