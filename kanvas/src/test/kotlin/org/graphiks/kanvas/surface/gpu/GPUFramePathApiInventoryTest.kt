package org.graphiks.kanvas.surface.gpu

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.canvas.DisplayOp
import org.graphiks.kanvas.canvas.ClipStack
import org.graphiks.kanvas.canvas.ClipStackOp
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElementKind
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskCombine
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipFillRule
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilCompare
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName.BOUNDED_CLIP_NATIVE
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName.PATH_FILL_STENCIL_COVER
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.geometry.GPUPathEdgeFanPayloadContract
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveFillRule
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveMaterialPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectRouteAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveStrokeLoweringProof
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_AFFINE_FILL_RECT_CAPABILITY
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_AFFINE_FILL_RECT_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.kanvas.types.Lattice
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.kanvas.types.PointMode
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.types.Vertices

class GPUFramePathApiInventoryTest {
    @Test
    fun `public path defaults derive from the stencil edge fan payload contract`() {
        assertEquals(
            GPUPathEdgeFanPayloadContract.MAX_TRIANGLES,
            RenderConfig.DEFAULT.maxPathFanTriangles,
        )
        assertEquals(
            GPUPathEdgeFanPayloadContract.MAX_GEOMETRY_BYTES,
            RenderConfig.DEFAULT.maxPathGeometryBytes,
        )
        assertEquals(
            GPUPathEdgeFanPayloadContract.BYTES_PER_TRIANGLE,
            GPUPathEdgeFanPayloadContract.MAX_GEOMETRY_BYTES /
                GPUPathEdgeFanPayloadContract.MAX_TRIANGLES,
        )
    }

    @Test
    fun `identical draw paths retain one content key across command ids`() {
        val path = Path().apply {
            moveTo(2f, 2f)
            quadTo(12f, 24f, 24f, 2f)
            lineTo(2f, 2f)
            close()
        }
        val operations = listOf(
            DisplayOp.DrawPath(path, Paint.fill(ColorARGB.Red).copy(antiAlias = false), Matrix3x3F32.Identity, ClipStack.WideOpen),
            DisplayOp.DrawRect(RectF32.ofLTRB(1f, 1f, 3f, 3f), Paint.fill(ColorARGB.Blue), Matrix3x3F32.Identity, ClipStack.WideOpen),
            DisplayOp.DrawPath(path, Paint.fill(ColorARGB.Red).copy(antiAlias = false), Matrix3x3F32.Identity, ClipStack.WideOpen),
        )

        val keys = GPUOpMapper.mapOperations(
            operations, target(), RenderConfig.DEFAULT, capabilitiesWith(PATH_FILL_STENCIL_COVER),
        ).visualCommands.mapNotNull { command ->
            (command.normalized as? NormalizedDrawCommand.FillPath)?.pathKey
        }

        assertEquals(2, keys.size)
        assertEquals(keys.first(), keys.last())
        assertTrue(keys.first().startsWith("path:"))
    }

    @Test
    fun `distinct cubic draw paths retain distinct canonical keys`() {
        fun cubic(controlY: Float) = Path().apply {
            moveTo(2f, 2f)
            cubicTo(8f, controlY, 16f, controlY, 24f, 2f)
            lineTo(2f, 2f)
            close()
        }

        val keys = GPUOpMapper.mapOperations(
            listOf(
                DisplayOp.DrawPath(cubic(18f), Paint.fill(ColorARGB.Red).copy(antiAlias = false), Matrix3x3F32.Identity, ClipStack.WideOpen),
                DisplayOp.DrawPath(cubic(22f), Paint.fill(ColorARGB.Red).copy(antiAlias = false), Matrix3x3F32.Identity, ClipStack.WideOpen),
            ),
            target(), RenderConfig.DEFAULT, capabilitiesWith(PATH_FILL_STENCIL_COVER),
        ).visualCommands.map { command ->
            assertIs<NormalizedDrawCommand.FillPath>(command.normalized).pathKey
        }

        assertNotEquals(keys.first(), keys.last())
        assertTrue(keys.all { key ->
            key.startsWith("path:") &&
                !key.contains("handle", ignoreCase = true) &&
                !key.contains("pointer", ignoreCase = true) &&
                !key.contains("0x", ignoreCase = true)
        })
    }

    @Test
    fun `bounded cubic path exposes immutable finite facts to stencil cover`() {
        val path = Path().apply {
            moveTo(2f, 2f)
            cubicTo(8f, 18f, 16f, 18f, 24f, 2f)
            lineTo(2f, 2f)
            close()
        }

        val command = assertIs<NormalizedDrawCommand.FillPath>(
            GPUOpMapper.mapOperations(
                listOf(DisplayOp.DrawPath(path, Paint.fill(ColorARGB.Red).copy(antiAlias = false), Matrix3x3F32.Identity, ClipStack.WideOpen)),
                target(), RenderConfig.DEFAULT, capabilitiesWith(PATH_FILL_STENCIL_COVER),
            ).visualCommands.single().normalized,
        )

        assertEquals("finite", command.pathDescriptor.finiteProof)
        assertEquals("immutable", command.pathDescriptor.volatility)
    }

    @Test
    fun `bounded cubic draw path reaches the native stencil cover route`() {
        val path = Path().apply {
            moveTo(2f, 2f)
            cubicTo(8f, 18f, 16f, 18f, 24f, 2f)
            lineTo(2f, 2f)
            close()
        }
        val plan = GPUFramePathApiInventory.plan(
            listOf(DisplayOp.DrawPath(path, Paint.fill(ColorARGB.Red).copy(antiAlias = false), Matrix3x3F32.Identity, ClipStack.WideOpen)),
            target(), RenderConfig.DEFAULT, capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals(null, plan.preparedRefusal)
        assertEquals("native.path_fill.stencil_cover", plan.recording.analysis.records.single().routeDecisionLabel)
        assertEquals(listOf("route:native.path_fill.stencil_cover"), plan.recording.routeDiagnostics)
        assertTrue(plan.recording.taskList.tasks.none { it is GPUTask.Refused })
        val command = assertIs<NormalizedDrawCommand.FillPath>(plan.visualCommands.single().normalized)
        assertTrue(command.pathKey.startsWith("path:"))
        assertEquals("finite", command.pathDescriptor.finiteProof)
        assertEquals("immutable", command.pathDescriptor.volatility)
        val semantic = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(
            GPUFramePathApiInventory.gatherCorePrimitiveSemantics(plan, GPUPixelBounds(0, 0, 32, 32)),
        ).semantics.values.single() as GPUDrawSemanticPayload.CorePrimitive
        assertEquals(GPUCorePrimitiveCoverageMode.Stencil1x, semantic.coverageMode)
        assertEquals(
            GPUCorePrimitiveGeometryMode.StencilEdgeFan,
            assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry).geometryMode,
        )
    }

    @Test
    fun `oval and circle cubic paths reach the bounded native stencil route`() {
        val paths = listOf(
            Path().addOval(RectF32.ofLTRB(2f, 4f, 26f, 20f)),
            Path().addCircle(16f, 16f, 12f),
        )

        paths.forEach { path ->
            val plan = GPUFramePathApiInventory.plan(
                listOf(
                    DisplayOp.DrawPath(
                        path,
                        Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                        Matrix3x3F32.Identity,
                        ClipStack.WideOpen,
                    ),
                ),
                target(),
                RenderConfig.DEFAULT,
                capabilitiesWith(PATH_FILL_STENCIL_COVER),
            )

            assertEquals(null, plan.preparedRefusal)
            assertEquals("native.path_fill.stencil_cover", plan.recording.analysis.records.single().routeDecisionLabel)
            assertTrue(plan.recording.taskList.tasks.none { it is GPUTask.Refused })
        }
    }

    @Test
    fun `identical flattened geometry with distinct fill types retains distinct keys`() {
        fun path(fillType: FillType) = Path().apply {
            this.fillType = fillType
            moveTo(2f, 2f)
            quadTo(12f, 24f, 24f, 2f)
            lineTo(2f, 2f)
            close()
        }

        val vertices = listOf(2f, 2f, 12f, 8f, 24f, 2f)
        val keys = listOf(FillType.WINDING, FillType.EVEN_ODD).map { fillType ->
            DisplayOp.DrawPath(
                path(fillType), Paint.fill(ColorARGB.Red).copy(antiAlias = false), Matrix3x3F32.Identity, ClipStack.WideOpen,
            ).toNormalizedCommand(
                org.graphiks.kanvas.gpu.renderer.commands.GPUDrawCommandID(7),
                target(), vertices, listOf(0), edgeCount = 3,
            ).pathKey
        }

        assertNotEquals(keys.first(), keys.last())
    }

    @Test
    fun `path budget refusal remains stable with a canonical path key`() {
        val path = Path().apply {
            moveTo(1f, 1f)
            lineTo(5f, 1f)
            lineTo(6f, 3f)
            lineTo(5f, 6f)
            lineTo(1f, 6f)
            close()
        }

        val refused = gatherRefusal(
            GPUFramePathApiInventory.plan(
                listOf(DisplayOp.DrawPath(path, Paint.fill(ColorARGB.Red), Matrix3x3F32.Identity, ClipStack.WideOpen)),
                target(), RenderConfig(maxPathVertices = 4u), capabilitiesWith(PATH_FILL_STENCIL_COVER),
            ),
        )

        assertEquals("unsupported.core_primitive.path_vertex_budget", refused.code)
        assertEquals("4", refused.facts["maxPathVertices"])
    }

    @Test
    fun `public stroke rect inventory reports analytic four band fills without a path key`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(8f, 8f, 24f, 20f),
                    Paint.stroke(ColorARGB.Red, 4f).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(FILL_RECT_CAPABILITY),
        )

        assertEquals(null, inventory.preparedRefusal)
        assertEquals(listOf(0, 1, 2, 3), inventory.visualCommands.map { it.normalized.commandId.value })
        assertTrue(inventory.visualCommands.all { visual ->
            visual.normalized is NormalizedDrawCommand.FillRect &&
                visual.normalized.source.operation == "drawRect.stroke.analytic-four-band"
        })
        assertTrue(inventory.visualCommands.none { visual ->
            (visual.normalized as? NormalizedDrawCommand.FillPath)?.pathKey?.contains("rect-stroke") == true
        })
    }

    @Test
    fun `public frame preparation retains one clamp gradient descriptor across four stroke bands`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(8f, 16f, 56f, 48f),
                    Paint.stroke(ColorARGB.Transparent, 4f).copy(
                        shader = Shader.LinearGradient(
                            Point2F32(8.5f, 32.5f), Point2F32(55.5f, 32.5f),
                            listOf(GradientStop(0f, ColorARGB.of(255, 255, 56, 56)), GradientStop(1f, ColorARGB.of(255, 56, 112, 255))),
                        ),
                        antiAlias = false,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = GPUTargetFacts(64, 64, "rgba8unorm-srgb"),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(FILL_RECT_CAPABILITY),
        )

        assertEquals(null, inventory.preparedRefusal)
        val materials = inventory.visualCommands.map {
            assertIs<NormalizedDrawCommand.FillRect>(it.normalized).material
        }
        assertTrue(materials.all { it is org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor.LinearGradient })
        assertTrue(materials.drop(1).all { it === materials.first() })
        assertEquals(
            listOf(
                org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(6, 14, 58, 18),
                org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(6, 46, 58, 50),
                org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(6, 18, 10, 46),
                org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(54, 18, 58, 46),
            ),
            inventory.visualCommands.map { visual ->
                val fill = assertIs<NormalizedDrawCommand.FillRect>(visual.normalized)
                org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds(
                    fill.rect.left.toInt(), fill.rect.top.toInt(), fill.rect.right.toInt(), fill.rect.bottom.toInt(),
                )
            },
        )
    }

    @Test
    fun `public inventory path prepares vertices with text and maps the snapshot once`() {
        val operations = listOf(
            DisplayOp.DrawRect(
                RectF32.ofLTRB(1f, 1f, 4f, 4f), Paint.fill(ColorARGB.Red),
                Matrix3x3F32.Identity, ClipStack.WideOpen,
            ),
            DisplayOp.DrawText(
                blob = TextBlob(
                    glyphRuns = listOf(
                        KanvasGlyphRun(
                            glyphs = listOf(36u), positions = listOf(Point2F32(0f, 0f)),
                            fontSize = 12f,
                        ),
                    ),
                    typeface = liberationTypeface(), fontSize = 12f,
                ),
                x = 4f, y = 16f, paint = Paint.fill(ColorARGB.White),
                transform = Matrix3x3F32.Identity, clip = ClipStack.WideOpen,
            ),
            DisplayOp.DrawVertices(
                Vertices(
                    VertexMode.TRIANGLES,
                    listOf(Point2F32(0f, 0f), Point2F32(2f, 0f), Point2F32(0f, 2f)),
                ),
                Paint.fill(ColorARGB.Red), Matrix3x3F32.Identity, ClipStack.WideOpen,
            ),
            DisplayOp.DrawVertices(
                Vertices(
                    VertexMode.TRIANGLES,
                    listOf(Point2F32(0f, 0f), Point2F32(3f, 0f), Point2F32(0f, 3f)),
                ),
                Paint.fill(ColorARGB.Blue), Matrix3x3F32.Identity, ClipStack.WideOpen,
            ),
            DisplayOp.DrawRect(
                RectF32.ofLTRB(5f, 5f, 8f, 8f), Paint.fill(ColorARGB.Green),
                Matrix3x3F32.Identity, ClipStack.WideOpen,
            ),
        )

        val inventory = GPUFramePathApiInventory.plan(
            operations, target(), RenderConfig.DEFAULT, capabilitiesWith(FILL_RECT_CAPABILITY),
        )

        val vertices = assertNotNull(inventory.preparedVerticesInventory)
        assertEquals(listOf(2, 3), vertices.commands.map { it.operationIndex })
        assertEquals(setOf(2, 3), vertices.artifactKeyByCommandId.keys)
        assertEquals(listOf(0, 1, 4), inventory.visualCommands.map { it.normalized.commandId.value })
        assertNotNull(inventory.preparedTextInventory)
    }

    @Test
    fun `every native preparation seam refuses authenticated semantic-only vertices`() {
        val capabilities = capabilitiesWith(FILL_RECT_CAPABILITY)
        val vertices = DisplayOp.DrawVertices(
            Vertices(
                VertexMode.TRIANGLES,
                listOf(Point2F32(0f, 0f), Point2F32(2f, 0f), Point2F32(0f, 2f)),
            ),
            Paint.fill(ColorARGB.Red),
            Matrix3x3F32.Identity,
            ClipStack.WideOpen,
        )
        val firstRect = DisplayOp.DrawRect(
                    RectF32.ofLTRB(1f, 1f, 4f, 4f),
                    Paint.fill(ColorARGB.Green).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
        )
        val secondRect = DisplayOp.DrawRect(
            RectF32.ofLTRB(5f, 5f, 8f, 8f),
            Paint.fill(ColorARGB.Blue).copy(antiAlias = false),
            Matrix3x3F32.Identity,
            ClipStack.WideOpen,
        )
        listOf(
            listOf(vertices),
            listOf(firstRect, vertices),
            listOf(firstRect, vertices, secondRect),
        ).forEach { operations ->
            val inventory = GPUFramePathApiInventory.plan(
                operations, target(), RenderConfig.DEFAULT, capabilities,
            )
            assertEquals(1, inventory.recording.semanticOnlyDraws.size)

            val coreRefusal = assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(
                GPUFramePathApiInventory.prepareNativeTaskList(
                    inventory, capabilities, GPUPixelBounds(0, 0, 32, 32),
                ),
            )
            val heterogeneousRefusal = assertIs<
                org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameResult.Refused
                >(
                GPUFramePathApiInventory.preparePreparedNativeTaskList(
                    inventory, capabilities, GPUPixelBounds(0, 0, 32, 32),
                ),
            )
            assertEquals(
                "unsupported.preflight.prepared_vertices_unmaterialized",
                coreRefusal.diagnostic.code.value,
            )
            assertEquals(coreRefusal.diagnostic.code, heterogeneousRefusal.diagnostic.code)
        }
    }

    @Test
    fun `global command slots authenticate expansions vertices and explicit elisions without holes`() {
        val image = org.graphiks.kanvas.image.Image.fromPixels(
            4, 4, ByteArray(4 * 4 * 4) { 0xff.toByte() },
            sourceId = "fp06-command-slot-expansion",
            alphaType = org.graphiks.kanvas.image.AlphaType.PREMUL,
        )
        fun nine(dstLeft: Float) = DisplayOp.DrawImageNine(
            image = image,
            center = RectF32.ofLTRB(1f, 1f, 3f, 3f),
            dst = RectF32.ofLTRB(dstLeft, 0f, dstLeft + 12f, 12f),
            paint = null,
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.WideOpen,
        )
        fun vertices(clip: ClipStack) = DisplayOp.DrawVertices(
            Vertices(
                VertexMode.TRIANGLES,
                listOf(Point2F32(0f, 0f), Point2F32(2f, 0f), Point2F32(0f, 2f)),
            ),
            Paint.fill(ColorARGB.Red), Matrix3x3F32.Identity, clip,
        )
        val culledText = DisplayOp.DrawText(
            blob = TextBlob(
                glyphRuns = listOf(
                    KanvasGlyphRun(
                        glyphs = listOf(36u), positions = listOf(Point2F32(0f, 0f)),
                        fontSize = 12f,
                    ),
                ),
                typeface = liberationTypeface(), fontSize = 12f,
            ),
            x = 4f, y = 16f, paint = Paint.fill(ColorARGB.White),
            transform = Matrix3x3F32.Identity,
            clip = ClipStack.DeviceRect(RectF32.ofLTRB(80f, 80f, 96f, 96f), false),
        )
        val operations = listOf(
            DisplayOp.Annotation(RectF32.ofLTRB(0f, 0f, 1f, 1f), "state", "kept"),
            nine(0f),
            vertices(ClipStack.WideOpen),
            culledText,
            vertices(ClipStack.DeviceRect(RectF32.ofLTRB(80f, 80f, 96f, 96f), false)),
            DisplayOp.SetTransform(Matrix3x3F32.Identity),
            nine(16f),
        )

        val plan = GPUFramePathApiInventory.plan(
            operations, target(), RenderConfig.DEFAULT, capabilitiesWith(FILL_RECT_CAPABILITY),
        )

        val preparedVertices = assertNotNull(
            plan.preparedVerticesInventory,
            plan.preparedRefusal?.let { "${it.code}: ${it.facts}" },
        )
        assertEquals((0..18).toSet(), plan.allocatedCommandIds)
        assertEquals(
            (0..8).toList() + (10..18).toList(),
            plan.visualCommands.map { it.normalized.commandId.value },
        )
        assertEquals(mapOf(9 to preparedVertices.commandsByOperationIndex.getValue(2).artifactKey),
            preparedVertices.artifactKeyByCommandId)
        assertEquals(setOf(4), preparedVertices.elidedVerticesOperationIndices)
        assertEquals(setOf(3), assertNotNull(plan.preparedTextInventory).acceptedTextOperationIndices)
        assertTrue(plan.visualCommands.none { it.preparedText != null })
        assertEquals(listOf(0, 5), plan.stateEvents.map { it.operationIndex })
    }

    @Test
    fun `vertices mapper hot path is indexed by immutable inventory structures`() {
        val mapperSource = File(
            "src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt",
        ).readText()
        val verticesBranch = mapperSource.substringAfter(
            "is DisplayOp.DrawVertices, is DisplayOp.DrawMesh -> {",
        ).substringBefore("\n                else -> {")

        assertTrue("elidedVerticesOperationIndices" in verticesBranch)
        assertTrue("commandsByOperationIndex[operationIndex]" in verticesBranch)
        assertTrue("commands.singleOrNull" !in verticesBranch)
        assertTrue("commands.firstOrNull" !in verticesBranch)
    }

    @Test
    fun `affine fill rect is publicly analyzed as rect family direct triangles`() {
        val baseCapabilities = capabilitiesWith(
            FILL_RECT_CAPABILITY,
            CORE_PRIMITIVE_AFFINE_FILL_RECT_CAPABILITY,
        )
        val capabilities = GPUCapabilities(
            implementation = baseCapabilities.implementation,
            facts = baseCapabilities.facts,
            knownUnsupportedFacts = baseCapabilities.knownUnsupportedFacts,
            snapshotId = "${baseCapabilities.snapshotId}:observed-limits",
            limits = GPULimits(
                8192,
                256,
                256,
                maxBufferSize = 1L shl 30,
                maxDynamicUniformBuffersPerPipelineLayout = 1,
            ),
        )
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(2f, 3f, 12f, 11f),
                    Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                    Matrix3x3F32.skewing(0.25f, 0.125f),
                    org.graphiks.kanvas.canvas.ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
        )

        val packet = inventory.recording.taskList.tasks
            .filterIsInstance<GPUTask.Render>()
            .single()
            .drawPackets
            .single()
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        val analysisRecord = inventory.recording.analysis.records.single()

        assertEquals(CORE_PRIMITIVE_AFFINE_FILL_RECT_STEP_IDENTITY, packet.renderStepId.value)
        assertEquals(analysisRecord.recordId, packet.analysisRecordId)
        assertEquals(analysisRecord.recordId, semantic.analysisRecordId)
        assertEquals(analysisRecord.commandFamily, semantic.analysisCommandFamily)
        assertEquals(
            analysisRecord.corePrimitiveRectRouteAuthority,
            semantic.rectRouteAuthority,
        )
        assertEquals(
            analysisRecord.corePrimitiveRectGeometryAuthority,
            semantic.rectGeometryAuthority,
        )
        assertSame(analysisRecord.corePrimitiveRectGeometryAuthority, semantic.rectGeometryAuthority)
        assertEquals(
            GPUCorePrimitiveRectRouteAuthority.RectAffineDirectTrianglesV1,
            semantic.rectRouteAuthority,
        )
        assertEquals(GPUCorePrimitiveSourceFamily.Rect, semantic.sourceFamily)
        assertEquals(GPUCorePrimitiveGeometryMode.DirectTriangles, geometry.geometryMode)
        assertEquals(4, geometry.sourceVertexCount)
        assertEquals(GPUPixelBounds(2, 3, 15, 13), geometry.coverBounds)
        assertTrue(inventory.recording.routeDiagnostics.none { it.contains("path_fill") })
        val prepared = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory,
            capabilities,
            GPUPixelBounds(0, 0, 32, 32),
        )
        assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            prepared,
            (prepared as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        )
    }

    @Test
    fun `affine fill rect derives cover bounds from its four device corners`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(2f, 3f, 12f, 11f),
                    Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                    Matrix3x3F32.skewing(0.25f, 0.125f),
                    org.graphiks.kanvas.canvas.ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(
                FILL_RECT_CAPABILITY,
                CORE_PRIMITIVE_AFFINE_FILL_RECT_CAPABILITY,
            ),
        )
        val visual = inventory.visualCommands.single()
        val command = assertIs<NormalizedDrawCommand.FillRect>(visual.normalized)
        val localBoundsCommand = command.copy(
            bounds = org.graphiks.kanvas.gpu.renderer.commands.GPUBounds(2f, 3f, 12f, 11f),
        )
        val localBoundsInventory = inventory.copy(
            visualCommands = listOf(visual.copy(normalized = localBoundsCommand)),
            normalizedCommands = listOf(localBoundsCommand),
        )

        val semantic = gatheredSemantic(localBoundsInventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)

        assertEquals(GPUPixelBounds(2, 3, 15, 13), geometry.coverBounds)
    }

    @Test
    fun `rotation mirror and skew derive geometry only from the real fill rect command`() {
        val capabilities = capabilitiesWith(
            FILL_RECT_CAPABILITY,
            CORE_PRIMITIVE_AFFINE_FILL_RECT_CAPABILITY,
        )
        val cases = listOf(
            Matrix3x3F32.rotation(45f) to GPUCorePrimitiveRectRouteAuthority.RectAffineDirectTrianglesV1,
            Matrix3x3F32.translation(16f, 0f) * Matrix3x3F32.scaling(-1f, 1f) to
                GPUCorePrimitiveRectRouteAuthority.RectAxisAligned,
            Matrix3x3F32.skewing(0.25f, 0.125f) to
                GPUCorePrimitiveRectRouteAuthority.RectAffineDirectTrianglesV1,
        )

        cases.forEach { (transform, expectedAuthority) ->
            val inventory = GPUFramePathApiInventory.plan(
                operations = listOf(
                    DisplayOp.DrawRect(
                        RectF32.ofLTRB(2f, 3f, 12f, 11f),
                        Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                        transform,
                        org.graphiks.kanvas.canvas.ClipStack.WideOpen,
                    ),
                ),
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities,
            )
            val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
            assertEquals(expectedAuthority, semantic.rectRouteAuthority)
            when (expectedAuthority) {
                GPUCorePrimitiveRectRouteAuthority.RectAxisAligned ->
                    assertIs<GPUCorePrimitiveGeometry.Rect>(semantic.geometry)
                GPUCorePrimitiveRectRouteAuthority.RectAffineDirectTrianglesV1 ->
                    assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
            }
        }
    }

    @Test
    fun `forged source operation cannot change a fill rect semantic family`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(2f, 3f, 12f, 11f),
                    Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                    Matrix3x3F32.skewing(0.25f, 0.125f),
                    org.graphiks.kanvas.canvas.ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(
                FILL_RECT_CAPABILITY,
                CORE_PRIMITIVE_AFFINE_FILL_RECT_CAPABILITY,
            ),
        )
        val visual = inventory.visualCommands.single()
        val command = assertIs<NormalizedDrawCommand.FillRect>(visual.normalized)
        val forgedCommand = command.copy(
            source = GPUCommandSource("forged-adapter", "drawPath"),
        )
        val forged = inventory.copy(
            visualCommands = listOf(visual.copy(normalized = forgedCommand)),
            normalizedCommands = listOf(forgedCommand),
        )

        val semantic = gatheredSemantic(forged) as GPUDrawSemanticPayload.CorePrimitive

        assertEquals(GPUCorePrimitiveSourceFamily.Rect, semantic.sourceFamily)
        assertEquals("FillRect", semantic.analysisCommandFamily)
        assertEquals(
            GPUCorePrimitiveRectRouteAuthority.RectAffineDirectTrianglesV1,
            semantic.rectRouteAuthority,
        )
    }

    @Test
    fun `semantic gathering refuses missing duplicated or forged analysis authority`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(2f, 3f, 12f, 11f),
                    Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    org.graphiks.kanvas.canvas.ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(FILL_RECT_CAPABILITY),
        )
        val record = inventory.recording.analysis.records.single()
        fun withRecords(records: List<org.graphiks.kanvas.gpu.renderer.analysis.GPUDrawAnalysisRecord>) =
            inventory.copy(
                recording = inventory.recording.copy(
                    analysis = inventory.recording.analysis.copy(records = records),
                ),
            )
        val rrectAuthority = inventoryFor(
            DisplayOp.DrawRRect(
                RRectF32.of(RectF32.ofLTRB(2f, 3f, 12f, 11f), radius = 2f),
                Paint.fill(ColorARGB.Red),
                Matrix3x3F32.Identity,
                org.graphiks.kanvas.canvas.ClipStack.WideOpen,
            ),
        ).recording.analysis.records.single().corePrimitiveRRectGeometryAuthority
        val cases = listOf(
            withRecords(listOf(record.copy(corePrimitiveRectRouteAuthority = null))) to
                "unsupported.core_primitive.rect.analysis_authority_missing",
            withRecords(listOf(record.copy(corePrimitiveRectGeometryAuthority = null))) to
                "unsupported.core_primitive.rect.geometry_authority_mismatch",
            withRecords(listOf(record, record)) to
                "unsupported.core_primitive.analysis_record_bijection",
            withRecords(listOf(record.copy(commandFamily = "FillPath"))) to
                "unsupported.core_primitive.analysis_command_family_mismatch",
            withRecords(listOf(record.copy(recordId = "analysis.fill_rect.forged"))) to
                "unsupported.core_primitive.analysis_record_id_mismatch",
            withRecords(
                listOf(record.copy(corePrimitiveRRectGeometryAuthority = rrectAuthority)),
            ) to "unsupported.core_primitive.rrect.analysis_authority_forbidden",
        )

        cases.forEach { (forged, expectedCode) ->
            assertEquals(expectedCode, gatherRefusal(forged).code)
        }
    }

    @Test
    fun `semantic gathering refuses rect or transform mutation after analysis`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(2f, 3f, 12f, 11f),
                    Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    org.graphiks.kanvas.canvas.ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(FILL_RECT_CAPABILITY),
        )
        val visual = inventory.visualCommands.single()
        val command = assertIs<NormalizedDrawCommand.FillRect>(visual.normalized)
        fun withCommand(mutated: NormalizedDrawCommand.FillRect) = inventory.copy(
            visualCommands = listOf(visual.copy(normalized = mutated)),
            normalizedCommands = listOf(mutated),
        )
        val mutations = listOf(
            withCommand(command.copy(rect = command.rect.copy(right = command.rect.right + 1f))),
            withCommand(command.copy(transform = command.transform.copy(scaleX = 2f))),
        )

        mutations.forEach { mutated ->
            assertEquals(
                "unsupported.core_primitive.rect.geometry_authority_mismatch",
                gatherRefusal(mutated).code,
            )
        }
    }

    @Test
    fun `rrect semantic consumes the analysis sealed geometry authority`() {
        val inventory = inventoryFor(DisplayOp.DrawRRect(
            RRectF32.of(
                rect = RectF32.ofLTRB(2f, 3f, 14f, 13f),
                topLeft = CornerRadiiF32.of(8f, 2f),
                topRight = CornerRadiiF32.of(8f, 6f),
                bottomRight = CornerRadiiF32.of(4f, 6f),
                bottomLeft = CornerRadiiF32.of(2f, 2f),
            ),
            Paint.fill(ColorARGB.Red),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))
        val analysisRecord = inventory.recording.analysis.records.single()
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.RRect>(semantic.geometry)

        assertNotNull(analysisRecord.corePrimitiveRRectGeometryAuthority)
        assertSame(analysisRecord.corePrimitiveRRectGeometryAuthority, semantic.rrectGeometryAuthority)
        assertEquals("analysis.fill_rrect.0", semantic.analysisRecordId)
        assertEquals("FillRRect", semantic.analysisCommandFamily)
        assertEquals(listOf(6f, 1.5f, 6f, 4.5f, 3f, 4.5f, 1.5f, 1.5f), geometry.radii)
    }

    @Test
    fun `rrect semantic gathering refuses missing transplanted or mutated analysis authority`() {
        val operation = DisplayOp.DrawRRect(
            RRectF32.of(RectF32.ofLTRB(2f, 3f, 14f, 13f), radius = 2f),
            Paint.fill(ColorARGB.Red),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        )
        val inventory = inventoryFor(operation)
        val visual = inventory.visualCommands.single()
        val command = assertIs<NormalizedDrawCommand.FillRRect>(visual.normalized)
        val record = inventory.recording.analysis.records.single()
        fun withRecord(mutated: org.graphiks.kanvas.gpu.renderer.analysis.GPUDrawAnalysisRecord) =
            inventory.copy(
                recording = inventory.recording.copy(
                    analysis = inventory.recording.analysis.copy(records = listOf(mutated)),
                ),
            )
        fun withCommand(mutated: NormalizedDrawCommand.FillRRect) = inventory.copy(
            visualCommands = listOf(visual.copy(normalized = mutated)),
            normalizedCommands = listOf(mutated),
        )
        val donor = inventoryFor(DisplayOp.DrawRRect(
            RRectF32.of(RectF32.ofLTRB(4f, 5f, 18f, 17f), radius = 3f),
            Paint.fill(ColorARGB.Red),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        )).recording.analysis.records.single()

        val cases = listOf(
            withRecord(record.copy(corePrimitiveRRectGeometryAuthority = null)) to
                "unsupported.core_primitive.rrect.analysis_authority_missing",
            withRecord(
                record.copy(
                    corePrimitiveRRectGeometryAuthority = donor.corePrimitiveRRectGeometryAuthority,
                ),
            ) to "unsupported.core_primitive.rrect.geometry_authority_mismatch",
            withCommand(
                command.copy(rrect = command.rrect.copy(
                    topLeft = command.rrect.topLeft.copy(x = command.rrect.topLeft.x + 1f),
                )),
            ) to "unsupported.core_primitive.rrect.geometry_authority_mismatch",
            withCommand(command.copy(transform = command.transform.copy(translateX = 1f))) to
                "unsupported.core_primitive.rrect.geometry_authority_mismatch",
        )

        cases.forEach { (forged, expectedCode) ->
            assertEquals(expectedCode, gatherRefusal(forged).code)
        }
    }

    @Test
    fun `rrect normalization is owned only by first route analysis`() {
        val mapperSource = File(
            "src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUOpMapper.kt",
        ).readText()
        val inventorySource = File(
            "src/main/kotlin/org/graphiks/kanvas/surface/gpu/GPUFramePathApiInventory.kt",
        ).readText()
        val plannerSource = File(
            "../gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/analysis/AnalysisContracts.kt",
        ).readText()

        assertEquals(0, "GPURRectNormalizer.normalize".toRegex().findAll(mapperSource).count())
        assertEquals(0, "GPURRectNormalizer.normalize".toRegex().findAll(inventorySource).count())
        assertEquals(1, "GPURRectNormalizer.normalize".toRegex().findAll(plannerSource).count())
    }

    @Test
    fun `drawColor and clear cover the exact target independently of the current transform`() {
        val surface = Surface(40, 30)
        surface.canvas {
            translate(13f, 17f)
            drawColor(ColorARGB.Red)
            clear(ColorARGB.Blue)
        }

        val plan = GPUFramePathApiInventory.plan(surface.snapshotOps(), target(40, 30), RenderConfig.DEFAULT)

        assertEquals(2, plan.visualCommands.size)
        plan.visualCommands.forEach { visual ->
            val command = assertIs<NormalizedDrawCommand.FillRect>(visual.normalized)
            assertEquals(0f, command.rect.left)
            assertEquals(0f, command.rect.top)
            assertEquals(40f, command.rect.right)
            assertEquals(30f, command.rect.bottom)
            assertEquals(0f, visual.targetSpaceBounds.left)
            assertEquals(0f, visual.targetSpaceBounds.top)
            assertEquals(40f, visual.targetSpaceBounds.right)
            assertEquals(30f, visual.targetSpaceBounds.bottom)
            assertEquals(0f, command.transform.translateX)
            assertEquals(0f, command.transform.translateY)
        }
    }

    @Test
    fun `point and line bounds include stroke width square cap and antialiasing`() {
        val surface = Surface(48, 40)
        surface.canvas {
            drawPoint(
                20f,
                20f,
                Paint.stroke(ColorARGB.Red, 6f).copy(strokeCap = StrokeCap.ROUND, antiAlias = true),
            )
            drawPoints(
                PointMode.LINES,
                listOf(Point2F32(10f, 10f), Point2F32(30f, 10f)),
                Paint.stroke(ColorARGB.Blue, 4f).copy(strokeCap = StrokeCap.SQUARE, antiAlias = true),
            )
        }

        val plan = GPUFramePathApiInventory.plan(surface.snapshotOps(), target(48, 40), RenderConfig.DEFAULT)

        assertEquals(2, plan.visualCommands.size)
        assertEquals(
            org.graphiks.kanvas.gpu.renderer.commands.GPUBounds(16f, 16f, 24f, 24f),
            plan.visualCommands[0].targetSpaceBounds,
        )
        assertEquals(
            org.graphiks.kanvas.gpu.renderer.commands.GPUBounds(7f, 7f, 33f, 13f),
            plan.visualCommands[1].targetSpaceBounds,
        )
    }

    @Test
    fun `drawPoint lowers positive square stroke width around the point`() {
        val operation = DisplayOp.DrawPoint(
            10f,
            12f,
            Paint.stroke(ColorARGB.Red, 4f).copy(strokeCap = StrokeCap.SQUARE, antiAlias = false),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        )
        val inventory = inventoryFor(operation)
        val command = assertIs<NormalizedDrawCommand.FillPath>(inventory.normalizedCommands.single())
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)

        assertEquals(8f, command.tessellatedVertices.filterIndexed { index, _ -> index % 2 == 0 }.min())
        assertEquals(12f, command.tessellatedVertices.filterIndexed { index, _ -> index % 2 == 0 }.max())
        assertEquals(10f, command.tessellatedVertices.filterIndexed { index, _ -> index % 2 == 1 }.min())
        assertEquals(14f, command.tessellatedVertices.filterIndexed { index, _ -> index % 2 == 1 }.max())
        assertEquals(5, geometry.sourceVertexCount)
        assertEquals(GPUCorePrimitiveSourceFamily.PointLine, semantic.sourceFamily)
    }

    @Test
    fun `drawPoints points mode lowers every positive butt point as a width sized square`() {
        val operation = DisplayOp.DrawPoints(
            PointMode.POINTS,
            listOf(Point2F32(5f, 5f), Point2F32(15f, 10f)),
            Paint.stroke(ColorARGB.Blue, 6f).copy(strokeCap = StrokeCap.BUTT, antiAlias = false),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        )
        val inventory = inventoryFor(operation)
        val command = assertIs<NormalizedDrawCommand.FillPath>(inventory.normalizedCommands.single())
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)

        assertEquals(2f, command.tessellatedVertices.filterIndexed { index, _ -> index % 2 == 0 }.min())
        assertEquals(18f, command.tessellatedVertices.filterIndexed { index, _ -> index % 2 == 0 }.max())
        assertEquals(2f, command.tessellatedVertices.filterIndexed { index, _ -> index % 2 == 1 }.min())
        assertEquals(13f, command.tessellatedVertices.filterIndexed { index, _ -> index % 2 == 1 }.max())
        assertEquals(10, geometry.sourceVertexCount)
        assertEquals(2, geometry.sourceContourStarts.size)
    }

    @Test
    fun `drawPoint hairline lowers to a one device pixel square geometry`() {
        val semantic = semanticFor(DisplayOp.DrawPoint(
            10f,
            12f,
            Paint.fill(ColorARGB.Red).copy(strokeWidth = 0f, strokeCap = StrokeCap.SQUARE),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        // The hairline point is one device pixel: cover bounds span 1 px in each axis.
        assertEquals(1, geometry.coverBounds.right - geometry.coverBounds.left, "hairline point spans one device pixel in x")
        assertEquals(1, geometry.coverBounds.bottom - geometry.coverBounds.top, "hairline point spans one device pixel in y")
    }

    @Test
    fun `drawPoints round points refuse with a stable geometry diagnostic`() {
        val inventory = inventoryFor(DisplayOp.DrawPoints(
            PointMode.POINTS,
            listOf(Point2F32(5f, 5f), Point2F32(15f, 10f)),
            Paint.stroke(ColorARGB.Blue, 6f).copy(strokeCap = StrokeCap.ROUND),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))

        val refused = gatherRefusal(inventory)

        assertEquals("unsupported.core_primitive.point.round_cap_exact_lowering", refused.code)
        assertEquals("drawPoints.points", refused.facts["source"])
    }

    @Test
    fun `stroked rrect lowers to path and preserves stroke facts`() {
        val paint = Paint.stroke(ColorARGB.Green, 4f).copy(
            strokeCap = StrokeCap.ROUND,
            strokeJoin = StrokeJoin.ROUND,
            pathEffect = PathEffect.Dash(floatArrayOf(3f, 2f), phase = 1f),
            antiAlias = true,
        )
        val operation = DisplayOp.DrawRRect(
            RRectF32.of(RectF32.ofLTRB(10f, 10f, 30f, 24f), radius = 4f),
            paint,
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        )

        val visual = GPUFramePathApiInventory.plan(
            listOf(operation),
            target(48, 40),
            RenderConfig.DEFAULT,
        ).visualCommands.single()
        val command = assertIs<NormalizedDrawCommand.FillPath>(visual.normalized)

        assertTrue(command.stroke)
        assertEquals(4f, command.strokeWidth)
        assertEquals("round", command.strokeCap)
        assertEquals("round", command.strokeJoin)
        assertTrue(command.dashIntervals!!.contentEquals(floatArrayOf(3f, 2f)))
        assertEquals(1f, command.dashPhase)
        assertEquals("drawRRect.stroke", command.source.operation)
        assertEquals(
            org.graphiks.kanvas.gpu.renderer.commands.GPUBounds(7f, 7f, 33f, 27f),
            visual.targetSpaceBounds,
        )
    }

    @Test
    fun `concave path uses stencil edge fan and retains even odd fill`() {
        val path = Path().apply {
            fillType = FillType.EVEN_ODD
            moveTo(2f, 2f)
            lineTo(20f, 2f)
            lineTo(8f, 9f)
            lineTo(20f, 20f)
            lineTo(2f, 20f)
            close()
        }

        val semantic = semanticFor(DisplayOp.DrawPath(
            path,
            Paint.fill(ColorARGB.Red).copy(antiAlias = true),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)

        assertEquals(GPUCorePrimitiveGeometryMode.StencilEdgeFan, geometry.geometryMode)
        assertEquals(GPUCorePrimitiveFillRule.EvenOdd, geometry.fillRule)
        assertFalse(geometry.inverseFill)
        assertEquals(GPUCorePrimitiveCoverageMode.StencilAA, semantic.coverageMode)
        assertEquals(geometry.vertices.size / 2, geometry.indices.size)
        assertEquals(geometry.indices.indices.toList(), geometry.indices)
    }

    @Test
    fun `multi contour hole preserves contour starts for stencil lowering`() {
        val path = Path().apply {
            addRect(RectF32.ofLTRB(2f, 2f, 28f, 28f))
            reverseAddPath(Path().apply { addRect(RectF32.ofLTRB(9f, 9f, 21f, 21f)) })
        }
        val semantic = semanticFor(DisplayOp.DrawPath(
            path,
            Paint.fill(ColorARGB.Green).copy(antiAlias = false),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)

        assertEquals(GPUCorePrimitiveGeometryMode.StencilEdgeFan, geometry.geometryMode)
        assertEquals(GPUCorePrimitiveFillRule.Winding, geometry.fillRule)
        assertFalse(geometry.inverseFill)
        assertEquals(2, geometry.sourceContourStarts.size)
        assertTrue(geometry.sourceContourStarts[1] > geometry.sourceContourStarts[0])
        assertEquals(GPUCorePrimitiveCoverageMode.Stencil1x, semantic.coverageMode)
    }

    @Test
    fun `inverse path preserves inverse even odd stencil facts`() {
        val path = Path().apply {
            fillType = FillType.INVERSE_EVEN_ODD
            addRect(RectF32.ofLTRB(6f, 6f, 20f, 20f))
        }
        val semantic = semanticFor(DisplayOp.DrawPath(
            path,
            Paint.fill(ColorARGB.Blue),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)

        assertEquals(GPUCorePrimitiveFillRule.EvenOdd, geometry.fillRule)
        assertTrue(geometry.inverseFill)
        assertEquals(GPUCorePrimitiveGeometryMode.StencilEdgeFan, geometry.geometryMode)
        assertEquals(GPUPixelBounds(0, 0, 32, 32), geometry.coverBounds)
    }

    @Test
    fun `inverse path normalized bounds cover its device clip before recorder`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipRect(RectF32.ofLTRB(4f, 5f, 24f, 26f), ClipOp.INTERSECT, antiAlias = false)
            drawPath(
                Path().apply {
                    fillType = FillType.INVERSE_WINDING
                    addRect(RectF32.ofLTRB(10f, 11f, 15f, 16f))
                },
                Paint.fill(ColorARGB.Red),
            )
        }

        val command = assertIs<NormalizedDrawCommand.FillPath>(
            GPUFramePathApiInventory.plan(
                surface.snapshotOps(),
                target(),
                RenderConfig.DEFAULT,
            ).normalizedCommands.single(),
        )

        assertEquals(org.graphiks.kanvas.gpu.renderer.commands.GPUBounds(4f, 5f, 24f, 26f), command.bounds)
    }

    @Test
    fun `empty inverse path refuses instead of presenting a degenerate edge fan`() {
        val path = Path().apply { fillType = FillType.INVERSE_WINDING }
        val inventory = GPUFramePathApiInventory.plan(
            listOf(DisplayOp.DrawPath(
                path,
                Paint.fill(ColorARGB.Red),
                Matrix3x3F32.Identity,
                org.graphiks.kanvas.canvas.ClipStack.WideOpen,
            )),
            target(),
            RenderConfig.DEFAULT,
        )

        val refused = assertIs<GPUCorePrimitiveSemanticGatherResult.Refused>(
            GPUFramePathApiInventory.gatherCorePrimitiveSemantics(
                inventory,
                GPUPixelBounds(0, 0, 32, 32),
            ),
        )
        assertEquals("unsupported.core_primitive.inverse_empty_path", refused.code)
    }

    @Test
    fun `identity solid drrect retains exact typed outer and inner rrect geometry`() {
        val semantic = semanticFor(DisplayOp.DrawDRRect(
            RRectF32.of(RectF32.ofLTRB(8f, 8f, 56f, 56f), radius = 8f),
            RRectF32.of(RectF32.ofLTRB(20f, 20f, 44f, 44f), radius = 4f),
            Paint.fill(ColorARGB.Blue).copy(antiAlias = false),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))

        assertEquals(GPUCorePrimitiveSourceFamily.DRRect, semantic.sourceFamily)
        val geometry = assertIs<GPUCorePrimitiveGeometry.DRRect>(semantic.geometry)
        assertEquals(listOf(8f, 8f, 56f, 56f), geometry.outerBounds)
        assertEquals(List(8) { 8f }, geometry.outerRadii)
        assertEquals(listOf(20f, 20f, 44f, 44f), geometry.innerBounds)
        assertEquals(List(8) { 4f }, geometry.innerRadii)
        assertEquals(GPUCorePrimitiveCoverageMode.FullOrScissor, semantic.coverageMode)
    }

    @Test
    fun `valid rrect and drrect fully outside the target are stable no ops before native routing`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawRRect(
                    RRectF32.of(RectF32.ofLTRB(-32f, -32f, -8f, -8f), radius = 4f),
                    Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
                DisplayOp.DrawDRRect(
                    RRectF32.of(RectF32.ofLTRB(72f, 72f, 104f, 104f), radius = 6f),
                    RRectF32.of(RectF32.ofLTRB(80f, 80f, 96f, 96f), radius = 3f),
                    Paint.fill(ColorARGB.Blue).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals(emptyList(), inventory.visualCommands)
        assertEquals(null, inventory.preparedRefusal)
        assertTrue(inventory.recording.taskList.tasks.none { it is GPUTask.Refused })
    }

    @Test
    fun `drrect analytic route remains closed to aa non solid filters transforms clips and invalid containment`() {
        val outer = RRectF32.of(RectF32.ofLTRB(8f, 8f, 56f, 56f), radius = 8f)
        val inner = RRectF32.of(RectF32.ofLTRB(20f, 20f, 44f, 44f), radius = 4f)
        val blurOperation = DisplayOp.DrawDRRect(
            outer,
            inner,
            Paint.fill(ColorARGB.Blue).copy(
                antiAlias = false,
                maskFilter = org.graphiks.kanvas.paint.MaskFilter.Blur(
                    org.graphiks.kanvas.pipeline.BlurStyle.NORMAL,
                    1f,
                ),
            ),
            Matrix3x3F32.Identity,
            ClipStack.WideOpen,
        )
        val operations = listOf(
            DisplayOp.DrawDRRect(outer, inner, Paint.fill(ColorARGB.Blue), Matrix3x3F32.Identity, ClipStack.WideOpen),
            DisplayOp.DrawDRRect(
                outer,
                inner,
                Paint.fill(ColorARGB.of(alpha = 160, red = 31, green = 115, blue = 209)).copy(antiAlias = false),
                Matrix3x3F32.Identity,
                ClipStack.WideOpen,
            ),
            DisplayOp.DrawDRRect(outer, inner, Paint.stroke(ColorARGB.Blue, 2f).copy(antiAlias = false), Matrix3x3F32.Identity, ClipStack.WideOpen),
            DisplayOp.DrawDRRect(
                outer,
                inner,
                Paint(shader = Shader.LinearGradient(
                    Point2F32(0f, 0f),
                    Point2F32(64f, 0f),
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                )).copy(antiAlias = false),
                Matrix3x3F32.Identity,
                ClipStack.WideOpen,
            ),
            DisplayOp.DrawDRRect(outer, inner, Paint.fill(ColorARGB.Blue).copy(antiAlias = false), Matrix3x3F32.translation(1f, 0f), ClipStack.WideOpen),
            DisplayOp.DrawDRRect(
                outer,
                inner,
                Paint.fill(ColorARGB.Blue).copy(antiAlias = false),
                Matrix3x3F32.Identity,
                ClipStack.Complex(listOf(org.graphiks.kanvas.canvas.ClipStackOp.RectOp(RectF32.ofLTRB(0f, 0f, 60f, 60f), ClipOp.INTERSECT, false))),
            ),
        )

        operations.forEach { operation ->
            when (val result = GPUFramePathApiInventory.gatherCorePrimitiveSemantics(
                inventoryFor(operation),
                GPUPixelBounds(0, 0, 32, 32),
            )) {
                is GPUCorePrimitiveSemanticGatherResult.Gathered -> assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(
                    assertIs<GPUDrawSemanticPayload.CorePrimitive>(
                        result.semantics.values.single(),
                    ).geometry,
                )
                is GPUCorePrimitiveSemanticGatherResult.Refused -> assertTrue(
                    result.code.startsWith("unsupported.core_primitive."),
                    "Neighbour refusal must remain a stable core-primitive boundary: ${result.code}",
                )
            }
        }
        assertIs<NormalizedDrawCommand.FillPath>(
            inventoryFor(blurOperation).normalizedCommands.single(),
        )
        val invalid = inventoryFor(DisplayOp.DrawDRRect(
            outer,
            RRectF32.of(RectF32.ofLTRB(4f, 20f, 44f, 44f), radius = 4f),
            Paint.fill(ColorARGB.Blue).copy(antiAlias = false),
            Matrix3x3F32.Identity,
            ClipStack.WideOpen,
        ))
        assertEquals("unsupported.core_primitive.drrect.inner_outside_outer", gatherRefusal(invalid).code)
    }

    @Test
    fun `nonidentity DRRect under one hard winding path clip enters analytic native preparation`() {
        val hardClip = ClipStack.Complex(
            listOf(
                ClipStackOp.PathOp(
                    Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }
                        .apply { fillType = FillType.WINDING },
                    ClipOp.INTERSECT,
                    antiAlias = false,
                ),
            ),
        )
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawDRRect(
                    RRectF32.of(RectF32.ofLTRB(8f, 8f, 56f, 56f), radius = 8f),
                    RRectF32.of(RectF32.ofLTRB(20f, 20f, 44f, 44f), radius = 4f),
                    Paint.fill(ColorARGB.Blue).copy(antiAlias = false),
                    Matrix3x3F32.translation(1f, 0f),
                    hardClip,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertIs<NormalizedDrawCommand.FillDRRect>(inventory.normalizedCommands.single())
        val gathered = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(
            GPUFramePathApiInventory.gatherCorePrimitiveSemantics(
                inventory,
                GPUPixelBounds(0, 0, 32, 32),
            ),
        )
        assertIs<GPUCorePrimitiveGeometry.DRRect>(
            assertIs<GPUDrawSemanticPayload.CorePrimitive>(gathered.semantics.values.single()).geometry,
        )
    }

    @Test
    fun `analytic DRRect mapper admits identity or every finite non-zero translated hard winding clip`() {
        val outer = RRectF32.of(RectF32.ofLTRB(8f, 8f, 56f, 56f), radius = 8f)
        val inner = RRectF32.of(RectF32.ofLTRB(20f, 20f, 44f, 44f), radius = 4f)
        val paint = Paint.fill(ColorARGB.Blue).copy(antiAlias = false)
        fun hardClip(
            fillType: FillType = FillType.WINDING,
            transformClass: String = "identity",
        ): ClipStack = ClipStack.Complex(
            listOf(
                ClipStackOp.PathOp(
                    Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }
                        .apply { this.fillType = fillType },
                    ClipOp.INTERSECT,
                    antiAlias = false,
                    transformClass = transformClass,
                ),
            ),
        )
        fun operation(transform: Matrix3x3F32, clip: ClipStack): DisplayOp.DrawDRRect = DisplayOp.DrawDRRect(
            outer,
            inner,
            paint,
            transform,
            clip,
        )

        listOf(4f to 0f, 0f to 5f, -4f to 5f, 4f to -5f).forEach { (x, y) ->
            assertIs<NormalizedDrawCommand.FillDRRect>(
                inventoryFor(operation(Matrix3x3F32.translation(x, y), hardClip())).normalizedCommands.single(),
                "translation ($x,$y)",
            )
        }

        val rejected = listOf(
            "wide-open positive translation" to operation(Matrix3x3F32.translation(4f, 5f), ClipStack.WideOpen),
            "non-finite translation" to operation(Matrix3x3F32.translation(Float.NaN, 5f), hardClip()),
            "scale" to operation(Matrix3x3F32.scaling(2f, 2f), hardClip()),
            "affine" to operation(Matrix3x3F32.of(1f, 0.25f, 0f, 0f, 1f, 0f, 0f, 0f, 1f), hardClip()),
            "transformed clip" to operation(Matrix3x3F32.translation(4f, 5f), hardClip(transformClass = "translate")),
            "inverse translated" to operation(
                Matrix3x3F32.translation(4f, 5f),
                hardClip(fillType = FillType.INVERSE_WINDING),
            ),
        )
        rejected.forEach { (label, draw) ->
            assertIs<NormalizedDrawCommand.FillPath>(inventoryFor(draw).normalizedCommands.single(), label)
        }

        assertIs<NormalizedDrawCommand.FillDRRect>(
            inventoryFor(operation(Matrix3x3F32.translation(0f, 0f), hardClip())).normalizedCommands.single(),
            "zero matrix is indistinguishable from identity at the public mapper boundary",
        )

        assertIs<NormalizedDrawCommand.FillDRRect>(
            inventoryFor(operation(Matrix3x3F32.Identity, hardClip(fillType = FillType.INVERSE_WINDING)))
                .normalizedCommands
                .single(),
        )
    }

    @Test
    fun `rrect mapper preserves analytic geometry for inverse winding hard clips at exact translations`() {
        val rrect = RRectF32.of(RectF32.ofLTRB(8f, 8f, 52f, 48f), radius = 10f)
        val paint = Paint.fill(ColorARGB.Blue).copy(antiAlias = false)
        val inverseClip = ClipStack.Complex(
            listOf(
                ClipStackOp.PathOp(
                    Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }
                        .apply { fillType = FillType.INVERSE_WINDING },
                    ClipOp.INTERSECT,
                    antiAlias = false,
                ),
            ),
        )
        listOf(
            Matrix3x3F32.translation(4f, 0f),
            Matrix3x3F32.translation(0f, 5f),
            Matrix3x3F32.translation(-4f, 5f),
            Matrix3x3F32.translation(4f, -5f),
            Matrix3x3F32.translation(0f, 0f),
        ).forEach { matrix ->
            val inventory = inventoryFor(DisplayOp.DrawRRect(rrect, paint, matrix, inverseClip))
            assertIs<NormalizedDrawCommand.FillRRect>(inventory.normalizedCommands.single(), matrix.toString())
            val gathered = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(
                GPUFramePathApiInventory.gatherCorePrimitiveSemantics(inventory, GPUPixelBounds(0, 0, 64, 64)),
            )
            assertIs<GPUCorePrimitiveGeometry.RRect>(
                assertIs<GPUDrawSemanticPayload.CorePrimitive>(gathered.semantics.values.single()).geometry,
            )
        }
    }

    @Test
    fun `drrect paint effects become stable exact semantic refusals`() {
        val outer = RRectF32.of(RectF32.ofLTRB(8f, 8f, 56f, 56f), radius = 8f)
        val inner = RRectF32.of(RectF32.ofLTRB(20f, 20f, 44f, 44f), radius = 4f)
        val opaque = Paint.fill(ColorARGB.Blue).copy(antiAlias = false)
        val alphaMatrix = ColorMatrixF32.ofIdentity().apply {
            setScale(1f, 1f, 1f, 0.5f)
        }
        data class PaintEffectCase(
            val label: String,
            val paint: Paint,
            val refusalCode: String,
            val refusalFact: String,
        )
        val cases = listOf(
            PaintEffectCase(
                "color-filter-alpha",
                opaque.copy(colorFilter = ColorFilter.Matrix(alphaMatrix)),
                "unsupported.core_primitive.drrect.paint_effect.color_filter",
                "color_filter",
            ),
            PaintEffectCase(
                "color-filter-unevaluated",
                opaque.copy(colorFilter = ColorFilter.HighContrast),
                "unsupported.core_primitive.drrect.paint_effect.color_filter",
                "color_filter",
            ),
            PaintEffectCase(
                "image-filter",
                opaque.copy(imageFilter = ImageFilter.Blur(1f, 1f)),
                "unsupported.core_primitive.drrect.paint_effect.image_filter",
                "image_filter",
            ),
            PaintEffectCase(
                "path-effect",
                opaque.copy(pathEffect = PathEffect.Dash(floatArrayOf(2f, 2f))),
                "unsupported.core_primitive.drrect.paint_effect.path_effect",
                "path_effect",
            ),
            PaintEffectCase(
                "blender",
                opaque.copy(blender = Blender.Mode(BlendMode.SRC)),
                "unsupported.core_primitive.drrect.paint_effect.blender",
                "blender",
            ),
        )

        cases.forEach { (label, paint, expectedRefusalCode, expectedRefusalFact) ->
            val inventory = inventoryFor(
                DisplayOp.DrawDRRect(outer, inner, paint, Matrix3x3F32.Identity, ClipStack.WideOpen),
            )

            assertIs<NormalizedDrawCommand.FillPath>(
                inventory.normalizedCommands.single(),
                "$label must not retain a direct FillDRRect command",
            )
            val refusal = assertIs<GPUCorePrimitiveSemanticGatherResult.Refused>(
                GPUFramePathApiInventory.gatherCorePrimitiveSemantics(
                    inventory,
                    GPUPixelBounds(0, 0, 32, 32),
                ),
                "$label must not gather a FillPath that drops its Paint effect",
            )
            assertEquals(expectedRefusalCode, refusal.code)
            assertEquals("drawDRRect", refusal.facts["source"])
            assertEquals(expectedRefusalFact, refusal.facts["paintEffect"])
        }
    }

    @Test
    fun `invalid drrect inner outside outer becomes a stable geometry refusal`() {
        val inventory = inventoryFor(DisplayOp.DrawDRRect(
            RRectF32.of(RectF32.ofLTRB(4f, 4f, 20f, 20f), radius = 2f),
            RRectF32.of(RectF32.ofLTRB(2f, 8f, 12f, 16f), radius = 1f),
            Paint.fill(ColorARGB.White),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))

        val refused = gatherRefusal(inventory)

        assertEquals("unsupported.core_primitive.drrect.inner_outside_outer", refused.code)
        assertEquals("drawDRRect", refused.facts["source"])
    }

    @Test
    fun `direct rrect normalizes oversized radii before semantic gathering`() {
        val semantic = semanticFor(DisplayOp.DrawRRect(
            RRectF32.of(
                RectF32.ofLTRB(2f, 3f, 12f, 11f),
                topLeft = CornerRadiiF32.of(8f, 6f),
                topRight = CornerRadiiF32.of(8f, 6f),
                bottomRight = CornerRadiiF32.of(8f, 6f),
                bottomLeft = CornerRadiiF32.of(8f, 6f),
            ),
            Paint.fill(ColorARGB.Red),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))
        val geometry = assertIs<GPUCorePrimitiveGeometry.RRect>(semantic.geometry)

        assertEquals(listOf(5f, 3.75f, 5f, 3.75f, 5f, 3.75f, 5f, 3.75f), geometry.radii)
    }

    @Test
    fun `draw rrect preserves raw square corner input until shared normalization`() {
        val inventory = inventoryFor(DisplayOp.DrawRRect(
            RRectF32.of(
                RectF32.ofLTRB(2f, 3f, 12f, 13f),
                topLeft = CornerRadiiF32.of(0f, 100f),
                topRight = CornerRadiiF32.of(2f, 2f),
                bottomRight = CornerRadiiF32.of(2f, 2f),
                bottomLeft = CornerRadiiF32.of(2f, 2f),
            ),
            Paint.fill(ColorARGB.Red),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))

        val command = assertIs<NormalizedDrawCommand.FillRRect>(inventory.normalizedCommands.single())
        assertEquals(0f, command.rrect.topLeft.x)
        assertEquals(100f, command.rrect.topLeft.y)
        assertEquals(2f, command.rrect.topRight.x)
        assertEquals(2f, command.rrect.bottomRight.x)
        assertEquals(2f, command.rrect.bottomLeft.x)

        val geometry = assertIs<GPUCorePrimitiveGeometry.RRect>(
            (gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive).geometry,
        )
        assertEquals(listOf(0f, 0f, 2f, 2f, 2f, 2f, 2f, 2f), geometry.radii)
    }

    @Test
    fun `draw rrect keeps negative radius raw until the shared typed refusal`() {
        val inventory = inventoryFor(DisplayOp.DrawRRect(
            RRectF32.of(
                RectF32.ofLTRB(2f, 3f, 12f, 13f),
                topLeft = CornerRadiiF32.of(-1f, 2f),
                topRight = CornerRadiiF32.of(2f, 2f),
                bottomRight = CornerRadiiF32.of(2f, 2f),
                bottomLeft = CornerRadiiF32.of(2f, 2f),
            ),
            Paint.fill(ColorARGB.Red),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))

        val command = assertIs<NormalizedDrawCommand.FillRRect>(inventory.normalizedCommands.single())
        assertEquals(-1f, command.rrect.topLeft.x)
        val refused = assertIs<GPUTask.Refused>(inventory.recording.taskList.tasks.single())
        assertEquals("unsupported.geometry.rrect_radii_negative", refused.diagnostic.code.value)
    }

    @Test
    fun `draw rrect normalizes very large finite radii without float overflow`() {
        val inventory = inventoryFor(DisplayOp.DrawRRect(
            RRectF32.of(
                RectF32.ofLTRB(2f, 3f, 12f, 13f),
                topLeft = CornerRadiiF32.of(Float.MAX_VALUE, Float.MAX_VALUE),
                topRight = CornerRadiiF32.of(Float.MAX_VALUE, Float.MAX_VALUE),
                bottomRight = CornerRadiiF32.of(Float.MAX_VALUE, Float.MAX_VALUE),
                bottomLeft = CornerRadiiF32.of(Float.MAX_VALUE, Float.MAX_VALUE),
            ),
            Paint.fill(ColorARGB.Red),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))

        val command = assertIs<NormalizedDrawCommand.FillRRect>(inventory.normalizedCommands.single())
        assertTrue(command.rrect.topLeft.x.isFinite())
        assertEquals(Float.MAX_VALUE, command.rrect.topLeft.x)

        val geometry = assertIs<GPUCorePrimitiveGeometry.RRect>(
            (gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive).geometry,
        )
        assertEquals(List(8) { 5f }, geometry.radii)
    }

    @Test
    fun `semantic gathering normalizes the same raw asymmetric rrect analyzed from display ops`() {
        val inventory = inventoryFor(DisplayOp.DrawRRect(
            RRectF32.of(
                rect = RectF32.ofLTRB(2f, 3f, 14f, 13f),
                topLeft = CornerRadiiF32.of(8f, 2f),
                topRight = CornerRadiiF32.of(8f, 6f),
                bottomRight = CornerRadiiF32.of(4f, 6f),
                bottomLeft = CornerRadiiF32.of(2f, 2f),
            ),
            Paint.fill(ColorARGB.Red),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))

        val geometry = assertIs<GPUCorePrimitiveGeometry.RRect>(
            (gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive).geometry,
        )

        assertEquals(listOf(6f, 1.5f, 6f, 4.5f, 3f, 4.5f, 1.5f, 1.5f), geometry.radii)
    }

    @Test
    fun `direct rrect reflection permutes normalized corners into device order`() {
        val semantic = semanticFor(DisplayOp.DrawRRect(
            RRectF32.of(
                RectF32.ofLTRB(2f, 4f, 12f, 14f),
                topLeft = CornerRadiiF32.of(1f, 1f),
                topRight = CornerRadiiF32.of(2f, 1f),
                bottomRight = CornerRadiiF32.of(3f, 1f),
                bottomLeft = CornerRadiiF32.of(4f, 1f),
            ),
            Paint.fill(ColorARGB.Red),
            Matrix3x3F32.of(-1f, 0f, 32f, 0f, -1f, 32f),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))
        val geometry = assertIs<GPUCorePrimitiveGeometry.RRect>(semantic.geometry)

        assertEquals(listOf(3f, 1f, 4f, 1f, 1f, 1f, 2f, 1f), geometry.radii)
    }

    @Test
    fun `direct rrect horizontal reflection permutes normalized corners into device order`() {
        val semantic = semanticFor(DisplayOp.DrawRRect(
            RRectF32.of(
                RectF32.ofLTRB(2f, 4f, 12f, 14f),
                topLeft = CornerRadiiF32.of(1f, 1f),
                topRight = CornerRadiiF32.of(2f, 1f),
                bottomRight = CornerRadiiF32.of(3f, 1f),
                bottomLeft = CornerRadiiF32.of(4f, 1f),
            ),
            Paint.fill(ColorARGB.Red),
            Matrix3x3F32.of(-1f, 0f, 32f, 0f, 1f, 0f),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))
        val geometry = assertIs<GPUCorePrimitiveGeometry.RRect>(semantic.geometry)

        assertEquals(listOf(2f, 1f, 1f, 1f, 4f, 1f, 3f, 1f), geometry.radii)
    }

    @Test
    fun `direct rrect vertical reflection permutes normalized corners into device order`() {
        val semantic = semanticFor(DisplayOp.DrawRRect(
            RRectF32.of(
                RectF32.ofLTRB(2f, 4f, 12f, 14f),
                topLeft = CornerRadiiF32.of(1f, 1f),
                topRight = CornerRadiiF32.of(2f, 1f),
                bottomRight = CornerRadiiF32.of(3f, 1f),
                bottomLeft = CornerRadiiF32.of(4f, 1f),
            ),
            Paint.fill(ColorARGB.Red),
            Matrix3x3F32.of(1f, 0f, 0f, 0f, -1f, 32f),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))
        val geometry = assertIs<GPUCorePrimitiveGeometry.RRect>(semantic.geometry)

        assertEquals(listOf(4f, 1f, 3f, 1f, 2f, 1f, 1f, 1f), geometry.radii)
    }

    @Test
    fun `skewed direct rrect becomes a stable geometry refusal`() {
        val inventory = inventoryFor(DisplayOp.DrawRRect(
            RRectF32.of(RectF32.ofLTRB(2f, 4f, 12f, 14f), radius = 2f),
            Paint.fill(ColorARGB.Red),
            Matrix3x3F32.skewing(0.25f, 0f),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))

        val refused = gatherRefusal(inventory)

        assertEquals("unsupported.core_primitive.rrect.non_axis_aligned_transform", refused.code)
    }

    @Test
    fun `partially outside rect retains exact geometry with target bounded coverage`() {
        val semantic = semanticFor(DisplayOp.DrawRect(
            RectF32.ofLTRB(-4f, 3f, 12f, 15f),
            Paint.fill(ColorARGB.Red),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))
        val geometry = assertIs<GPUCorePrimitiveGeometry.Rect>(semantic.geometry)

        assertEquals(-4f, geometry.left)
        assertEquals(12f, geometry.right)
        assertEquals(GPUPixelBounds(0, 0, 32, 32), semantic.scissorBounds)
    }

    @Test
    fun `partially outside rrect retains exact geometry with target bounded coverage`() {
        val semantic = semanticFor(DisplayOp.DrawRRect(
            RRectF32.of(RectF32.ofLTRB(-4f, 3f, 12f, 15f), radius = 3f),
            Paint.fill(ColorARGB.Red),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))
        val geometry = assertIs<GPUCorePrimitiveGeometry.RRect>(semantic.geometry)

        assertEquals(-4f, geometry.left)
        assertEquals(12f, geometry.right)
        assertEquals(GPUPixelBounds(0, 0, 32, 32), semantic.scissorBounds)
    }

    @Test
    fun `linear core material without its capability stays fail closed during semantic gathering`() {
        val gradient = Shader.LinearGradient(
            start = Point2F32(0f, 0f),
            end = Point2F32(16f, 0f),
            stops = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
        )
        val inventory = inventoryFor(DisplayOp.DrawRect(
            RectF32.ofLTRB(2f, 3f, 18f, 20f),
            Paint(shader = gradient),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))

        val refused = gatherRefusal(inventory)

        assertEquals("unsupported.core_primitive.rect.analysis_authority_missing", refused.code)
        assertEquals("analysis.fill_rect.0", refused.facts["analysisRecordId"])
    }

    @Test
    fun `bounded linear radial and sweep public materials reach core primitive semantics when facts are injected`() {
        val stops = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue))
        val shaders = listOf(
            Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(32f, 0f), stops),
            Shader.RadialGradient(Point2F32(16f, 16f), 16f, stops),
            Shader.SweepGradient(Point2F32(16f, 16f), stops = stops),
        )

        shaders.forEach { shader ->
            val inventory = GPUFramePathApiInventory.plan(
                listOf(
                    DisplayOp.DrawRect(
                        RectF32.ofLTRB(2f, 2f, 30f, 30f),
                        Paint(shader = shader).copy(antiAlias = false),
                        Matrix3x3F32.Identity,
                        ClipStack.WideOpen,
                    ),
                ),
                target(),
                RenderConfig.DEFAULT,
                capabilitiesWith(
                    FILL_RECT_CAPABILITY,
                    "first_slice.linear_gradient.native",
                    "first_slice.radial_gradient.native",
                    "first_slice.sweep_gradient.native",
                ),
            )

            assertTrue(inventory.recording.routeDiagnostics.none { it.startsWith("refused:") })
            val gatheredResult = GPUFramePathApiInventory.gatherCorePrimitiveSemantics(
                inventory,
                GPUPixelBounds(0, 0, 32, 32),
            )
            val gathered = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(
                gatheredResult,
                gatheredResult.toString(),
            )
            val material = assertIs<GPUDrawSemanticPayload.CorePrimitive>(
                gathered.semantics.values.single(),
            ).material
            when (shader) {
                is Shader.LinearGradient -> assertIs<GPUCorePrimitiveMaterialPayload.LinearGradient>(material)
                is Shader.RadialGradient -> assertIs<GPUCorePrimitiveMaterialPayload.RadialGradient>(material)
                is Shader.SweepGradient -> assertIs<GPUCorePrimitiveMaterialPayload.SweepGradient>(material)
                else -> error("unexpected shader")
            }
        }
    }

    @Test
    fun `antialiased radial and sweep public materials refuse before analytic recording`() {
        val stops = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue))
        val shaders = listOf(
            Shader.RadialGradient(Point2F32(16f, 16f), 16f, stops),
            Shader.SweepGradient(Point2F32(16f, 16f), stops = stops),
        )

        shaders.forEach { shader ->
            val inventory = GPUFramePathApiInventory.plan(
                listOf(
                    DisplayOp.DrawRect(
                        RectF32.ofLTRB(2f, 2f, 30f, 30f),
                        Paint(shader = shader).copy(antiAlias = true),
                        Matrix3x3F32.Identity,
                        ClipStack.WideOpen,
                    ),
                ),
                target(),
                RenderConfig.DEFAULT,
                capabilitiesWith(
                    FILL_RECT_CAPABILITY,
                    "first_slice.radial_gradient.native",
                    "first_slice.sweep_gradient.native",
                ),
            )

            assertEquals(
                listOf("refused:unsupported.material.gradient_antialias"),
                inventory.recording.routeDiagnostics,
            )
            assertTrue(
                inventory.recording.taskList.tasks
                    .filterIsInstance<GPUTask.Render>()
                    .flatMap(GPUTask.Render::drawPackets)
                    .isEmpty(),
            )
        }
    }

    @Test
    fun `three stop radial FillRect keeps the bounded public refusal policy`() {
        val threeStops = listOf(
            GradientStop(0f, ColorARGB.Red),
            GradientStop(0.5f, ColorARGB.Green),
            GradientStop(1f, ColorARGB.Blue),
        )
        val cases = listOf(
            Shader.RadialGradient(
                Point2F32(16f, 16f), 16f,
                threeStops + GradientStop(1f, ColorARGB.White),
                TileMode.CLAMP,
            ).let { Triple(it, false, "unsupported.material.radial_gradient_stop_count") },
            Triple(
                Shader.RadialGradient(Point2F32(16f, 16f), 16f, threeStops, TileMode.REPEAT),
                false,
                "unsupported.material.radial_gradient_stop_count",
            ),
            Triple(
                Shader.RadialGradient(Point2F32(16f, 16f), 16f, threeStops, TileMode.CLAMP),
                true,
                "unsupported.material.radial_gradient_stop_count",
            ),
            Triple(
                Shader.WithLocalMatrix(
                Shader.RadialGradient(Point2F32(16f, 16f), 16f, threeStops, TileMode.CLAMP),
                Matrix3x3F32.translation(1f, 0f),
                ),
                false,
                "unsupported.material.radial_gradient_stop_count",
            ),
        )

        cases.forEach { (shader, antiAlias, expectedCode) ->
            val inventory = GPUFramePathApiInventory.plan(
                listOf(
                    DisplayOp.DrawRect(
                        RectF32.ofLTRB(2f, 2f, 30f, 30f),
                        Paint(shader = shader).copy(antiAlias = antiAlias),
                        Matrix3x3F32.Identity,
                        ClipStack.WideOpen,
                    ),
                ),
                target(),
                RenderConfig.DEFAULT,
                capabilitiesWith(FILL_RECT_CAPABILITY, "first_slice.radial_gradient.native"),
            )

            assertEquals(listOf("refused:$expectedCode"), inventory.recording.routeDiagnostics)
            assertTrue(inventory.recording.taskList.tasks.filterIsInstance<GPUTask.Render>()
                .flatMap(GPUTask.Render::drawPackets).isEmpty())
        }

        val translated = GPUFramePathApiInventory.plan(
            listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(2f, 2f, 30f, 30f),
                    Paint(shader = Shader.RadialGradient(
                        Point2F32(16f, 16f), 16f, threeStops, TileMode.CLAMP,
                    )).copy(antiAlias = false),
                    Matrix3x3F32.translation(1f, 0f),
                    ClipStack.WideOpen,
                ),
            ),
            target(),
            RenderConfig.DEFAULT,
            capabilitiesWith(FILL_RECT_CAPABILITY, "first_slice.radial_gradient.native"),
        )
        assertEquals(
            listOf("refused:unsupported.material.radial_gradient_stop_count"),
            translated.recording.routeDiagnostics,
        )
    }

    @Test
    fun `three stop sweep FillRect keeps the bounded public refusal policy`() {
        val threeStops = listOf(
            GradientStop(0f, ColorARGB.Red),
            GradientStop(0.5f, ColorARGB.Green),
            GradientStop(1f, ColorARGB.Blue),
        )
        val accepted = GPUFramePathApiInventory.plan(
            listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(2f, 2f, 30f, 30f),
                    Paint(shader = Shader.SweepGradient(
                        Point2F32(16f, 16f), stops = threeStops, tileMode = TileMode.CLAMP,
                    )).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target(colorFormat = "rgba8unorm-srgb"),
            RenderConfig.DEFAULT,
            capabilitiesWith(FILL_RECT_CAPABILITY, "first_slice.sweep_gradient.native"),
        )
        assertTrue(accepted.recording.routeDiagnostics.none { it.startsWith("refused:") })

        val cases = listOf(
            Triple(
                Shader.SweepGradient(
                    Point2F32(16f, 16f),
                    stops = threeStops + GradientStop(1f, ColorARGB.White), tileMode = TileMode.CLAMP,
                ),
                false,
                "unsupported.material.sweep_gradient_stop_count",
            ),
            Triple(
                Shader.SweepGradient(Point2F32(16f, 16f), stops = threeStops, tileMode = TileMode.REPEAT),
                false,
                "unsupported.material.sweep_gradient_stop_count",
            ),
            Triple(
                Shader.SweepGradient(Point2F32(16f, 16f), stops = threeStops, tileMode = TileMode.CLAMP),
                true,
                "unsupported.material.sweep_gradient_stop_count",
            ),
            Triple(
                Shader.WithLocalMatrix(
                    Shader.SweepGradient(Point2F32(16f, 16f), stops = threeStops, tileMode = TileMode.CLAMP),
                    Matrix3x3F32.translation(1f, 0f),
                ),
                false,
                "unsupported.material.sweep_gradient_stop_count",
            ),
        )
        cases.forEach { (shader, antiAlias, expectedCode) ->
            val inventory = GPUFramePathApiInventory.plan(
                listOf(
                    DisplayOp.DrawRect(
                        RectF32.ofLTRB(2f, 2f, 30f, 30f),
                        Paint(shader = shader).copy(antiAlias = antiAlias),
                        Matrix3x3F32.Identity,
                        ClipStack.WideOpen,
                    ),
                ),
                target(),
                RenderConfig.DEFAULT,
                capabilitiesWith(FILL_RECT_CAPABILITY, "first_slice.sweep_gradient.native"),
            )
            assertEquals(listOf("refused:$expectedCode"), inventory.recording.routeDiagnostics)
            assertTrue(inventory.recording.taskList.tasks.filterIsInstance<GPUTask.Render>()
                .flatMap(GPUTask.Render::drawPackets).isEmpty())
        }

        val translated = GPUFramePathApiInventory.plan(
            listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(2f, 2f, 30f, 30f),
                    Paint(shader = Shader.SweepGradient(
                        Point2F32(16f, 16f), stops = threeStops, tileMode = TileMode.CLAMP,
                    )).copy(antiAlias = false),
                    Matrix3x3F32.translation(1f, 0f),
                    ClipStack.WideOpen,
                ),
            ),
            target(),
            RenderConfig.DEFAULT,
            capabilitiesWith(FILL_RECT_CAPABILITY, "first_slice.sweep_gradient.native"),
        )
        assertEquals(
            listOf("refused:unsupported.material.sweep_gradient_stop_count"),
            translated.recording.routeDiagnostics,
        )
    }

    @Test
    fun `three stop sweep FillRect requires the proven target and wide open clip`() {
        val stops = listOf(
            GradientStop(0f, ColorARGB.Red),
            GradientStop(0.5f, ColorARGB.Green),
            GradientStop(1f, ColorARGB.Blue),
        )
        val shader = Shader.SweepGradient(Point2F32(16f, 16f), stops = stops, tileMode = TileMode.CLAMP)
        val cases = listOf(
            Triple("rgba8unorm", ClipStack.WideOpen, "unsupported.material.sweep_gradient_stop_count"),
            Triple("bgra8unorm", ClipStack.WideOpen, "unsupported.material.sweep_gradient_stop_count"),
            Triple("rgba8unorm-srgb", ClipStack.DeviceRect(RectF32.ofLTRB(4f, 4f, 28f, 28f), false), "unsupported.material.sweep_gradient_stop_count"),
            Triple(
                "rgba8unorm-srgb",
                ClipStack.Complex(listOf(org.graphiks.kanvas.canvas.ClipStackOp.RectOp(
                    RectF32.ofLTRB(4f, 4f, 28f, 28f), ClipOp.INTERSECT, false,
                ))),
                "unsupported.material.sweep_gradient_stop_count",
            ),
        )

        cases.forEach { (colorFormat, clip, expectedCode) ->
            val inventory = GPUFramePathApiInventory.plan(
                listOf(DisplayOp.DrawRect(
                    RectF32.ofLTRB(2f, 2f, 30f, 30f),
                    Paint(shader = shader).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    clip,
                )),
                org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts(32, 32, colorFormat),
                RenderConfig.DEFAULT,
                capabilitiesWith(
                    FILL_RECT_CAPABILITY,
                    "first_slice.sweep_gradient.native",
                    "first_slice.scissor.native",
                ),
            )
            assertEquals(listOf("refused:$expectedCode"), inventory.recording.routeDiagnostics)
            assertTrue(inventory.recording.taskList.tasks.filterIsInstance<GPUTask.Render>()
                .flatMap(GPUTask.Render::drawPackets).isEmpty())
        }
    }

    @Test
    fun `antialiased bounded linear public material reaches analytic core primitive semantics with injected fact`() {
        val inventory = GPUFramePathApiInventory.plan(
            listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(2f, 2f, 30f, 30f),
                    Paint(
                        shader = Shader.LinearGradient(
                            Point2F32(0f, 0f),
                            Point2F32(32f, 0f),
                            listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                        ),
                    ).copy(antiAlias = true),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target(),
            RenderConfig.DEFAULT,
            capabilitiesWith(FILL_RECT_CAPABILITY, "first_slice.linear_gradient.native"),
        )

        assertTrue(inventory.recording.routeDiagnostics.none { it.startsWith("refused:") })
        val gathered = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(
            GPUFramePathApiInventory.gatherCorePrimitiveSemantics(
                inventory,
                GPUPixelBounds(0, 0, 32, 32),
            ),
        )
        assertEquals(1, gathered.semantics.size)
    }

    @Test
    fun `exact quarter turn clamp linear FillRect enters the native hard path clip route`() {
        val capabilityFacts = capabilitiesWith(
            FILL_RECT_CAPABILITY,
            PATH_FILL_STENCIL_COVER,
            "first_slice.linear_gradient.native",
        )
        val capabilities = GPUCapabilities(
            implementation = capabilityFacts.implementation,
            facts = capabilityFacts.facts,
            knownUnsupportedFacts = capabilityFacts.knownUnsupportedFacts,
            snapshotId = "${capabilityFacts.snapshotId}:observed-limits",
            limits = GPULimits(
                maxTextureDimension2D = 8192,
                copyBytesPerRowAlignment = 256,
                minUniformBufferOffsetAlignment = 256,
                maxBufferSize = 1L shl 30,
                maxDynamicUniformBuffersPerPipelineLayout = 1,
            ),
        )
        val surface = Surface(64, 64)
        surface.canvas {
            scale(0.75f, 0.75f)
            clipPath(
                Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }
                    .apply { fillType = FillType.WINDING },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            resetMatrix()
            rotate(90f, 16f, 16f)
            drawRect(
                RectF32.ofLTRB(8f, 8f, 32f, 24f),
                Paint(
                    shader = Shader.LinearGradient(
                        Point2F32(8f, 8f),
                        Point2F32(32f, 8f),
                        listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                    ),
                ).copy(antiAlias = false),
            )
        }
        val plan = GPUFramePathApiInventory.plan(
            operations = surface.snapshotOps(),
            target = target(64, 64),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
        )

        assertEquals(null, plan.preparedRefusal)
        assertEquals("native.fill_rect.linear_gradient", plan.recording.analysis.records.single().routeDecisionLabel)
        val semantic = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(
            GPUFramePathApiInventory.gatherCorePrimitiveSemantics(plan, GPUPixelBounds(0, 0, 64, 64)),
        ).semantics.values.single() as GPUDrawSemanticPayload.CorePrimitive
        assertEquals(GPUCorePrimitiveRectRouteAuthority.RectAffineDirectTrianglesV1, semantic.rectRouteAuthority)
        assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        val prepared = GPUFramePathApiInventory.prepareNativeTaskList(
            plan,
            capabilities,
            GPUPixelBounds(0, 0, 64, 64),
        )
        assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(prepared, prepared.toString())
    }

    @Test
    fun `non right angle clamp linear FillRect remains refused inside a hard path clip`() {
        val surface = Surface(64, 64)
        surface.canvas {
            scale(0.75f, 0.75f)
            clipPath(
                Path { moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close() }
                    .apply { fillType = FillType.WINDING },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            resetMatrix()
            rotate(45f, 16f, 16f)
            drawRect(
                RectF32.ofLTRB(8f, 8f, 32f, 24f),
                Paint(
                    shader = Shader.LinearGradient(
                        Point2F32(8f, 8f),
                        Point2F32(32f, 8f),
                        listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                    ),
                ).copy(antiAlias = false),
            )
        }
        val plan = GPUFramePathApiInventory.plan(
            operations = surface.snapshotOps(),
            target = target(64, 64),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(FILL_RECT_CAPABILITY, PATH_FILL_STENCIL_COVER, "first_slice.linear_gradient.native"),
        )

        assertEquals(
            listOf("refused:unsupported.transform.affine_material"),
            plan.recording.routeDiagnostics,
        )
    }

    @Test
    fun `removing either gradient fact preserves exact planner refusal and no render packets`() {
        val fixtures = listOf(
            "first_slice.linear_gradient.native" to
                Shader.LinearGradient(
                    Point2F32(0f, 0f), Point2F32(32f, 0f),
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
            "first_slice.radial_gradient.native" to
                Shader.RadialGradient(
                    Point2F32(16f, 16f), 16f,
                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
            "first_slice.sweep_gradient.native" to
                Shader.SweepGradient(
                    Point2F32(16f, 16f),
                    stops = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                ),
        )

        fixtures.forEach { (missing, shader) ->
            val allCapabilities = capabilitiesWith(
                FILL_RECT_CAPABILITY,
                "first_slice.linear_gradient.native",
                "first_slice.radial_gradient.native",
                "first_slice.sweep_gradient.native",
            )
            val capabilities = GPUCapabilities(
                implementation = allCapabilities.implementation,
                facts = allCapabilities.facts.filterNot { it.name == missing },
                knownUnsupportedFacts = allCapabilities.knownUnsupportedFacts,
                snapshotId = "${allCapabilities.snapshotId}:without-$missing",
                limits = allCapabilities.limits,
            )
            val inventory = GPUFramePathApiInventory.plan(
                listOf(
                    DisplayOp.DrawRect(
                        RectF32.ofLTRB(2f, 2f, 30f, 30f),
                        Paint(shader = shader).copy(antiAlias = false),
                        Matrix3x3F32.Identity,
                        ClipStack.WideOpen,
                    ),
                ),
                target(),
                RenderConfig.DEFAULT,
                capabilities,
            )

            val expected = when {
                missing.contains("linear") -> "unsupported.material.linear_gradient_capability_missing"
                missing.contains("radial") -> "unsupported.material.radial_gradient_capability_missing"
                else -> "unsupported.material.sweep_gradient_capability_missing"
            }
            assertEquals(listOf("refused:$expected"), inventory.recording.routeDiagnostics)
            assertTrue(
                inventory.recording.taskList.tasks
                    .filterIsInstance<GPUTask.Render>()
                    .flatMap { it.drawPackets }
                    .isEmpty(),
            )
        }
    }

    @Test
    fun `path over RenderConfig vertex budget becomes a stable refusal during mapping`() {
        val path = Path().apply {
            moveTo(1f, 1f)
            lineTo(5f, 1f)
            lineTo(6f, 3f)
            lineTo(5f, 6f)
            lineTo(1f, 6f)
            close()
        }
        val inventory = GPUFramePathApiInventory.plan(
            listOf(DisplayOp.DrawPath(
                path,
                Paint.fill(ColorARGB.Red),
                Matrix3x3F32.Identity,
                org.graphiks.kanvas.canvas.ClipStack.WideOpen,
            )),
            target(),
            RenderConfig(maxPathVertices = 4u),
        )

        val refused = gatherRefusal(inventory)

        assertEquals("unsupported.core_primitive.path_vertex_budget", refused.code)
        assertEquals("4", refused.facts["maxPathVertices"])
    }

    @Test
    fun `public path fan budget refuses before recording any submission`() {
        val inventory = pathBudgetInventory(
            RenderConfig(maxPathVertices = 16u, maxPathFanTriangles = 3u),
        )

        val refused = assertNotNull(inventory.preparedRefusal)

        assertEquals("geometry.path.fan_budget_exceeded", refused.code)
        assertTrue(
            inventory.recording.taskList.tasks
                .filterIsInstance<GPUTask.Render>()
                .flatMap { it.drawPackets }
                .isEmpty(),
        )
    }

    @Test
    fun `public path geometry memory budget refuses before recording any submission`() {
        val inventory = pathBudgetInventory(
            RenderConfig(
                maxPathVertices = 16u,
                maxPathFanTriangles = 4u,
                maxPathGeometryBytes = 143u,
            ),
        )

        val refused = assertNotNull(inventory.preparedRefusal)

        assertEquals("geometry.path.memory_budget_exceeded", refused.code)
        assertTrue(
            inventory.recording.taskList.tasks
                .filterIsInstance<GPUTask.Render>()
                .flatMap { it.drawPackets }
                .isEmpty(),
        )
    }

    @Test
    fun `public path rejects static and UInt-overflow fan memory configuration before submission`() {
        val cases = listOf(
            RenderConfig(maxPathVertices = 16u, maxPathFanTriangles = 1_025u) to
                "geometry.path.fan_budget_config_exceeded",
            RenderConfig(maxPathVertices = 16u, maxPathGeometryBytes = 36_865u) to
                "geometry.path.memory_budget_config_exceeded",
            RenderConfig(maxPathVertices = 16u, maxPathFanTriangles = UInt.MAX_VALUE) to
                "geometry.path.fan_budget_config_out_of_int_range",
            RenderConfig(maxPathVertices = 16u, maxPathGeometryBytes = UInt.MAX_VALUE) to
                "geometry.path.memory_budget_config_out_of_int_range",
            RenderConfig(maxPathVertices = UInt.MAX_VALUE) to
                "unsupported.core_primitive.path_vertex_budget_config_out_of_int_range",
        )

        cases.forEach { (config, expectedCode) ->
            val inventory = pathBudgetInventory(config)

            assertEquals(expectedCode, assertNotNull(inventory.preparedRefusal).code)
            assertTrue(
                inventory.recording.taskList.tasks
                    .filterIsInstance<GPUTask.Render>()
                    .flatMap { it.drawPackets }
                    .isEmpty(),
            )
        }
    }

    @Test
    fun `stencil edge fan over 256 source vertices preserves its budget diagnostic`() {
        val path = Path().apply {
            moveTo(1f, 1f)
            repeat(256) { index ->
                lineTo(
                    1f + (index % 28),
                    2f + ((index * 7) % 27),
                )
            }
            close()
        }
        val inventory = GPUFramePathApiInventory.plan(
            listOf(DisplayOp.DrawPath(
                path,
                Paint.fill(ColorARGB.Red),
                Matrix3x3F32.Identity,
                org.graphiks.kanvas.canvas.ClipStack.WideOpen,
            )),
            target(),
            RenderConfig(maxPathVertices = 512u, maxPathFanTriangles = 256u),
        )

        val refused = assertNotNull(inventory.preparedRefusal)

        assertEquals("geometry.path.fan_budget_exceeded", refused.code)
    }

    @Test
    fun `path AA mode changes semantic identity without changing fill authority`() {
        val path = triangle()
        val aa = semanticFor(DisplayOp.DrawPath(
            path,
            Paint.fill(ColorARGB.Red).copy(antiAlias = true),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))
        val binary = semanticFor(DisplayOp.DrawPath(
            path,
            Paint.fill(ColorARGB.Red).copy(antiAlias = false),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))

        assertEquals(GPUCorePrimitiveCoverageMode.StencilAA, aa.coverageMode)
        assertEquals(GPUCorePrimitiveCoverageMode.Stencil1x, binary.coverageMode)
        assertNotEquals(aa.canonicalHash, binary.canonicalHash)
    }

    @Test
    fun `stroked path uses canonical stroke outline and retains all stroke facts`() {
        val semantic = semanticFor(DisplayOp.DrawPoints(
            PointMode.LINES,
            listOf(Point2F32(4f, 8f), Point2F32(24f, 8f)),
            Paint.stroke(ColorARGB.Red, 4f).copy(
                strokeCap = StrokeCap.SQUARE,
                strokeJoin = StrokeJoin.MITER,
                strokeMiter = 3f,
                antiAlias = true,
            ),
            Matrix3x3F32.Identity,
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        val stroke = assertNotNull(geometry.strokeStyle)

        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(4f, stroke.width)
        assertEquals("square", stroke.cap)
        assertEquals("miter", stroke.join)
        assertEquals(3f, stroke.miterLimit)
        assertEquals(emptyList(), stroke.dashIntervals)
        assertEquals(0f, stroke.dashPhase)
        assertEquals(GPUCorePrimitiveStrokeLoweringProof.SingleSegmentSquareV1, stroke.loweringProof)
        assertEquals(GPUCorePrimitiveCoverageMode.StencilAA, semantic.coverageMode)
    }

    @Test
    fun `bounded solid square miter stroke crosses mapper planner and native stencil recording`() {
        val path = Path().apply {
            moveTo(4f, 8f)
            lineTo(24f, 8f)
        }
        val baseCapabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER)
        val capabilities = GPUCapabilities(
            implementation = baseCapabilities.implementation,
            facts = baseCapabilities.facts,
            knownUnsupportedFacts = baseCapabilities.knownUnsupportedFacts,
            snapshotId = "${baseCapabilities.snapshotId}:observed-limits",
            limits = GPULimits(
                maxTextureDimension2D = 8192,
                copyBytesPerRowAlignment = 256,
                minUniformBufferOffsetAlignment = 256,
                maxBufferSize = 1L shl 30,
                maxDynamicUniformBuffersPerPipelineLayout = 1,
            ),
            textureFormatSampleSupport = baseCapabilities.textureFormatSampleSupport,
            rendererFeatures = baseCapabilities.rendererFeatures,
            copyAsDrawCapability = baseCapabilities.copyAsDrawCapability,
        )
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    path,
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.SQUARE,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
        )

        val command = assertIs<NormalizedDrawCommand.FillPath>(inventory.visualCommands.single().normalized)
        assertEquals("square", command.strokeCap)
        assertEquals("miter", command.strokeJoin)
        assertEquals(
            "native.path_stroke.stencil_cover",
            inventory.recording.analysis.records.single().routeDecisionLabel,
        )
        assertEquals(
            listOf("route:native.path_stroke.stencil_cover"),
            inventory.recording.routeDiagnostics,
        )

        val prepared = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory,
            capabilities,
            GPUPixelBounds(0, 0, 32, 32),
        )
        assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            prepared,
            (prepared as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let { diagnostic ->
                "${diagnostic.code.value}: ${diagnostic.message}; facts=${diagnostic.facts}"
            },
        )
    }

    @Test
    fun `bounded anti aliased horizontal stroke crosses native preparation with MSAA`() {
        val capabilities = completeMsaaCapabilities()
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply { moveTo(4f, 8f); lineTo(28f, 8f) },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = true,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
        )
        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory, capabilities, GPUPixelBounds(0, 0, 32, 32),
        )
        val prepared = assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        ).taskList
        val render = prepared.tasks.filterIsInstance<GPUTask.Render>().single()
        assertEquals(GPUSamplePlan.MultisampleFrame(4), render.samplePlan)
        assertTrue(render.drawPackets.any { packet ->
            (packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive)?.coverageMode ==
                GPUCorePrimitiveCoverageMode.StencilAA
        })
    }

    @Test
    fun `translated triangle radial gradient remains explicitly refused outside hard stroke lane`() {
        val clip = Path().apply {
            moveTo(4f, 4f)
            lineTo(28f, 4f)
            lineTo(4f, 28f)
            close()
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(6f, 6f)
                        lineTo(26f, 6f)
                        lineTo(6f, 26f)
                        close()
                    },
                    Paint.fill(ColorARGB.Transparent).copy(
                        shader = Shader.RadialGradient(
                            Point2F32(16f, 16f), 16f,
                            listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                            TileMode.CLAMP,
                        ),
                        antiAlias = false,
                    ),
                    Matrix3x3F32.translation(2f, 1f),
                    ClipStack.Complex(listOf(ClipStackOp.PathOp(clip, ClipOp.INTERSECT, antiAlias = false))),
                ),
            ),
            target = target(), config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )
        assertTrue(inventory.recording.analysis.records.single().routeDecisionLabel != "native.path_stroke.stencil_cover")
    }

    @Test
    fun `translated triangle radial FillRect reaches native hard clip route`() {
        val clip = Path().apply {
            moveTo(8f, 8f); lineTo(56f, 8f); lineTo(8f, 55f); close()
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawRect(
                    RectF32.ofLTRB(0f, 0f, 64f, 64f),
                    Paint(shader = Shader.RadialGradient(
                        Point2F32(24.5f, 24.5f), 24f,
                        listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                        TileMode.CLAMP,
                    )).copy(antiAlias = false),
                    Matrix3x3F32.translation(2f, 0f),
                    ClipStack.Complex(listOf(ClipStackOp.PathOp(clip, ClipOp.INTERSECT, antiAlias = false))),
                ),
            ),
            target = target(), config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(FILL_RECT_CAPABILITY, PATH_FILL_STENCIL_COVER, "first_slice.radial_gradient.native"),
        )
        assertEquals("native.fill_rect.radial_gradient", inventory.recording.analysis.records.single().routeDecisionLabel)
        assertEquals(listOf("route:native.fill_rect.radial_gradient"), inventory.recording.routeDiagnostics)
    }

    @Test
    fun `translated local sweep matrix reaches the hard path clip stroke stencil route`() {
        val capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER)
        val clipPath = Path().apply {
            moveTo(3f, 3f)
            lineTo(29f, 3f)
            lineTo(3f, 29f)
            close()
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(5.25f, 8.25f)
                        lineTo(21.25f, 20.25f)
                    },
                    Paint.stroke(ColorARGB.Transparent, 4f).copy(
                        shader = Shader.WithLocalMatrix(
                            Shader.SweepGradient(
                                Point2F32(16f, 16f),
                                0f,
                                360f,
                                listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                                TileMode.CLAMP,
                            ),
                            Matrix3x3F32.translation(1.25f, -0.75f),
                        ),
                        antiAlias = false,
                        strokeCap = StrokeCap.SQUARE,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.Complex(listOf(ClipStackOp.PathOp(clipPath, ClipOp.INTERSECT, antiAlias = false))),
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
    }

    @Test
    fun `translated local radial matrix reaches the hard path clip stroke stencil route`() {
        val capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER)
        val clipPath = Path().apply {
            moveTo(3f, 3f)
            lineTo(29f, 3f)
            lineTo(3f, 29f)
            close()
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(5.25f, 8.25f)
                        lineTo(21.25f, 20.25f)
                    },
                    Paint.stroke(ColorARGB.Transparent, 4f).copy(
                        shader = Shader.WithLocalMatrix(
                            Shader.RadialGradient(
                                Point2F32(16f, 16f),
                                16f,
                                listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                                TileMode.CLAMP,
                            ),
                            Matrix3x3F32.translation(1.25f, -0.75f),
                        ),
                        antiAlias = false,
                        strokeCap = StrokeCap.SQUARE,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.Complex(listOf(ClipStackOp.PathOp(clipPath, ClipOp.INTERSECT, antiAlias = false))),
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
    }

    @Test
    fun `translated radial draw reaches the hard path clip stroke stencil route`() {
        val clipPath = Path().apply {
            moveTo(7.25f, 6.25f)
            lineTo(30.25f, 6.25f)
            lineTo(7.25f, 29.25f)
            close()
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(5.25f, 8.25f)
                        lineTo(21.25f, 20.25f)
                    },
                    Paint.stroke(ColorARGB.Transparent, 4f).copy(
                        shader = Shader.RadialGradient(
                            Point2F32(16f, 16f),
                            16f,
                            listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                            TileMode.CLAMP,
                        ),
                        antiAlias = false,
                        strokeCap = StrokeCap.SQUARE,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.of(
                        1.25f, 0f, 2f,
                        0f, 1.25f, -1f,
                        0f, 0f, 1f,
                    ),
                    ClipStack.Complex(listOf(ClipStackOp.PathOp(clipPath, ClipOp.INTERSECT, antiAlias = false))),
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
    }

    @Test
    fun `exact right angle radial draw with square miter stroke under winding clip reaches the hard path clip route`() {
        val capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER)
        val clipPath = Path().apply {
            moveTo(27.75f, 4.25f)
            lineTo(27.75f, 27.25f)
            lineTo(4.75f, 4.25f)
            close()
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(8.25f, 8.25f)
                        lineTo(20.25f, 14.25f)
                    },
                    Paint.stroke(ColorARGB.Transparent, 4f).copy(
                        shader = Shader.RadialGradient(
                            Point2F32(16f, 16f),
                            16f,
                            listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                            TileMode.CLAMP,
                        ),
                        antiAlias = false,
                        strokeCap = StrokeCap.SQUARE,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.rotation(90f, pivotX = 16f, pivotY = 16f),
                    ClipStack.Complex(
                        listOf(
                            ClipStackOp.PathOp(
                                clipPath,
                                ClipOp.INTERSECT,
                                antiAlias = false,
                                transformClass = "right-angle-rotation",
                            ),
                        ),
                    ),
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
        )

        val visual = inventory.visualCommands.single()
        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        assertEquals(listOf("route:native.path_stroke.stencil_cover"), inventory.recording.routeDiagnostics)
        val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(visual.clipExecutionPlan)
        assertEquals("right-angle-rotation", execution.pathTransformClass)
        assertEquals(GPUClipStencilOperation.IncrementWrap, execution.producer.frontPassOperation)
        assertEquals(GPUClipStencilOperation.DecrementWrap, execution.producer.backPassOperation)
        assertEquals(GPUClipStencilCompare.NotEqual, execution.consumer.compare)
        val clipGeometry = assertIs<GPUClipExecutionGeometry.Path>(execution.producer.geometry)
        assertEquals(GPUClipFillRule.Winding, clipGeometry.fillRule)
        assertEquals(false, clipGeometry.inverseFill)
    }

    @Test
    fun `exact right angle sweep draw with square miter stroke under winding clip reaches the hard path clip route`() {
        val capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER)
        val clipPath = Path().apply {
            moveTo(27.75f, 4.25f)
            lineTo(27.75f, 27.25f)
            lineTo(4.75f, 4.25f)
            close()
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(8.25f, 8.25f)
                        lineTo(20.25f, 14.25f)
                    },
                    Paint.stroke(ColorARGB.Transparent, 4f).copy(
                        shader = Shader.SweepGradient(
                            Point2F32(16f, 16f),
                            0f,
                            360f,
                            listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                        ),
                        antiAlias = false,
                        strokeCap = StrokeCap.SQUARE,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.rotation(90f, pivotX = 16f, pivotY = 16f),
                    ClipStack.Complex(
                        listOf(
                            ClipStackOp.PathOp(
                                clipPath,
                                ClipOp.INTERSECT,
                                antiAlias = false,
                                transformClass = "right-angle-rotation",
                            ),
                        ),
                    ),
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilities,
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        assertEquals(
            listOf("route:native.path_stroke.stencil_cover"),
            inventory.recording.routeDiagnostics,
        )
    }

    @Test
    fun `sweep draw with non-right-angle rotation remains refused before hard path clip recording`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(8.25f, 8.25f)
                        lineTo(20.25f, 14.25f)
                    },
                    Paint.stroke(ColorARGB.Transparent, 4f).copy(
                        shader = Shader.SweepGradient(
                            Point2F32(16f, 16f),
                            0f,
                            360f,
                            listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                        ),
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.rotation(15f),
                    ClipStack.Complex(listOf(ClipStackOp.PathOp(
                        Path().apply {
                            moveTo(27.75f, 4.25f)
                            lineTo(27.75f, 27.25f)
                            lineTo(4.75f, 4.25f)
                            close()
                        },
                        ClipOp.INTERSECT,
                        antiAlias = false,
                        transformClass = "non-right-angle-rotation",
                    ))),
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals(
            "refused.unsupported.geometry.perspective_path",
            inventory.recording.analysis.records.single().routeDecisionLabel,
        )
    }

    @Test
    fun `general radial draw rotation remains refused before native preparation`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(5.25f, 8.25f)
                        lineTo(21.25f, 20.25f)
                    },
                    Paint.stroke(ColorARGB.Transparent, 4f).copy(
                        shader = Shader.RadialGradient(
                            Point2F32(16f, 16f),
                            16f,
                            listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                            TileMode.CLAMP,
                        ),
                        antiAlias = false,
                        strokeCap = StrokeCap.SQUARE,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.rotation(15f),
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertTrue(inventory.recording.analysis.records.single().routeDecisionLabel.startsWith("refused."))
    }

    @Test
    fun `rotated and nonuniform local radial matrices remain refused before hard path clip recording`() {
        listOf(
            Matrix3x3F32.rotation(90f),
            Matrix3x3F32.scaling(2f, 1f),
        ).forEach { localMatrix ->
            val inventory = GPUFramePathApiInventory.plan(
                operations = listOf(
                    DisplayOp.DrawPath(
                        Path().apply {
                            moveTo(5.25f, 8.25f)
                            lineTo(21.25f, 20.25f)
                        },
                        Paint.stroke(ColorARGB.Transparent, 4f).copy(
                            shader = Shader.WithLocalMatrix(
                                Shader.RadialGradient(
                                    Point2F32(16f, 16f),
                                    16f,
                                    listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                                    TileMode.CLAMP,
                                ),
                                localMatrix,
                            ),
                            antiAlias = false,
                            strokeCap = StrokeCap.SQUARE,
                            strokeJoin = StrokeJoin.MITER,
                        ),
                        Matrix3x3F32.Identity,
                        ClipStack.WideOpen,
                    ),
                ),
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
            )

            assertEquals(
                "refused.unsupported.material.mapping.local_matrix",
                inventory.recording.analysis.records.single().routeDecisionLabel,
            )
        }
    }

    @Test
    fun `rotated and nonuniform local sweep matrices remain refused before hard path clip recording`() {
        listOf(
            Matrix3x3F32.rotation(90f),
            Matrix3x3F32.scaling(2f, 1f),
        ).forEach { localMatrix ->
            val inventory = GPUFramePathApiInventory.plan(
                operations = listOf(
                    DisplayOp.DrawPath(
                        Path().apply {
                            moveTo(5.25f, 8.25f)
                            lineTo(21.25f, 20.25f)
                        },
                        Paint.stroke(ColorARGB.Transparent, 4f).copy(
                            shader = Shader.WithLocalMatrix(
                                Shader.SweepGradient(
                                    Point2F32(16f, 16f),
                                    stops = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
                                ),
                                localMatrix,
                            ),
                            antiAlias = false,
                            strokeCap = StrokeCap.SQUARE,
                            strokeJoin = StrokeJoin.MITER,
                        ),
                        Matrix3x3F32.Identity,
                        ClipStack.WideOpen,
                    ),
                ),
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
            )

            assertEquals(
                "refused.unsupported.material.mapping.local_matrix",
                inventory.recording.analysis.records.single().routeDecisionLabel,
            )
        }
    }

    @Test
    fun `single segment stroke refuses non miter joins before native preparation`() {
        val path = Path().apply {
            moveTo(4f, 8f)
            lineTo(24f, 8f)
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    path,
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.SQUARE,
                        strokeJoin = StrokeJoin.BEVEL,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
        )

        val refused = gatherRefusal(inventory)

        assertEquals("unsupported.core_primitive.stroke.join_exact_lowering", refused.code)
        assertEquals("bevel", refused.facts["join"])
    }

    @Test
    fun `bounded open polyline butt miter stroke reaches native stencil cover`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(4f, 8f)
                        lineTo(24f, 8f)
                        lineTo(24f, 20f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(3, geometry.sourceVertexCount)
        assertEquals(GPUCorePrimitiveStrokeLoweringProof.MultiSegmentButtMiterV1, geometry.strokeStyle?.loweringProof)
    }

    @Test
    fun `bounded horizontal dashed butt stroke reaches native stencil cover`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(4f, 8f)
                        lineTo(36f, 8f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                        pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f)),
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(listOf(8f, 4f), geometry.strokeStyle?.dashIntervals)
        assertEquals(GPUCorePrimitiveStrokeLoweringProof.HorizontalDashedButtMiterV1, geometry.strokeStyle?.loweringProof)
    }

    @Test
    fun `bounded horizontal dashed butt stroke admits the second proven phase`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(4f, 8f)
                        lineTo(36f, 8f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                        pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 4f),
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveStrokeLoweringProof.HorizontalDashedButtMiterV1, geometry.strokeStyle?.loweringProof)
        assertEquals(4f, geometry.strokeStyle?.dashPhase)
    }

    @Test
    fun `bounded vertical dashed butt stroke reaches native stencil cover`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(16f, 4f)
                        lineTo(16f, 28f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                        pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(listOf(8f, 4f), geometry.strokeStyle?.dashIntervals)
        assertEquals(GPUCorePrimitiveStrokeLoweringProof.VerticalDashedButtMiterV1, geometry.strokeStyle?.loweringProof)
    }

    @Test
    fun `bounded reverse vertical dashed butt stroke reaches native stencil cover`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(16f, 28f)
                        lineTo(16f, 4f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                        pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(listOf(8f, 4f), geometry.strokeStyle?.dashIntervals)
        assertEquals(GPUCorePrimitiveStrokeLoweringProof.VerticalDashedButtMiterV1, geometry.strokeStyle?.loweringProof)
    }

    @Test
    fun `bounded vertical dashed butt stroke admits the second proven phase`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(16f, 4f)
                        lineTo(16f, 28f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                        pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 4f),
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveStrokeLoweringProof.VerticalDashedButtMiterV1, geometry.strokeStyle?.loweringProof)
        assertEquals(4f, geometry.strokeStyle?.dashPhase)
    }

    @Test
    fun `bounded vertical dashed butt stroke reaches native stencil cover under device scissor`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(16f, 4f)
                        lineTo(16f, 28f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                        pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.DeviceRect(RectF32.ofLTRB(14f, 8f, 19f, 20f), false),
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(GPUCorePrimitiveStrokeLoweringProof.VerticalDashedButtMiterV1, geometry.strokeStyle?.loweringProof)
    }

    @Test
    fun `bounded translated vertical dashed butt stroke reaches native stencil cover`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(16f, 4f)
                        lineTo(16f, 28f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                        pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                    ),
                    Matrix3x3F32.translation(3f, 2f),
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(GPUCorePrimitiveStrokeLoweringProof.VerticalDashedButtMiterV1, geometry.strokeStyle?.loweringProof)
    }

    @Test
    fun `bounded uniformly scaled horizontal dashed butt stroke reaches native stencil cover`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(4f, 8f)
                        lineTo(14f, 8f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                        pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                    ),
                    Matrix3x3F32.scaling(2f, 2f),
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(GPUCorePrimitiveStrokeLoweringProof.HorizontalDashedButtMiterV1, geometry.strokeStyle?.loweringProof)
    }

    @Test
    fun `bounded uniformly scaled vertical dashed butt stroke reaches native stencil cover`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(8f, 4f)
                        lineTo(8f, 14f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                        pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                    ),
                    Matrix3x3F32.scaling(2f, 2f),
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(GPUCorePrimitiveStrokeLoweringProof.VerticalDashedButtMiterV1, geometry.strokeStyle?.loweringProof)
    }

    @Test
    fun `bounded uniformly scaled translated horizontal dashed butt stroke reaches native stencil cover`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(4f, 4f)
                        lineTo(14f, 4f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                        pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                    ),
                    Matrix3x3F32.translation(2f, 4f) * Matrix3x3F32.scaling(2f, 2f),
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(GPUCorePrimitiveStrokeLoweringProof.HorizontalDashedButtMiterV1, geometry.strokeStyle?.loweringProof)
    }

    @Test
    fun `bounded uniformly scaled translated vertical dashed butt stroke reaches native stencil cover`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(8f, 3f)
                        lineTo(8f, 13f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                        pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                    ),
                    Matrix3x3F32.translation(2f, 4f) * Matrix3x3F32.scaling(2f, 2f),
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(GPUCorePrimitiveStrokeLoweringProof.VerticalDashedButtMiterV1, geometry.strokeStyle?.loweringProof)
    }

    @Test
    fun `bounded quarter turn horizontal dashed butt stroke reaches native stencil cover`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(4f, 8f)
                        lineTo(16f, 8f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                        pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                    ),
                    Matrix3x3F32.translation(20f, 4f) * Matrix3x3F32.rotation(90f),
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(GPUCorePrimitiveStrokeLoweringProof.VerticalDashedButtMiterV1, geometry.strokeStyle?.loweringProof)
    }

    @Test
    fun `bounded half turn vertical dashed butt stroke reaches native stencil cover`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(8f, 4f)
                        lineTo(8f, 28f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                        pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 0f),
                    ),
                    Matrix3x3F32.translation(32f, 32f) * Matrix3x3F32.rotation(180f),
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(GPUCorePrimitiveStrokeLoweringProof.VerticalDashedButtMiterV1, geometry.strokeStyle?.loweringProof)
    }

    @Test
    fun `nearby dashed stroke remains refused outside the fixed proof`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(4f, 8f)
                        lineTo(36f, 8f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                        pathEffect = PathEffect.Dash(floatArrayOf(4f, 2f)),
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("prepared.path_stroke.tessellated", inventory.recording.analysis.records.single().routeDecisionLabel)
        val refused = GPUFramePathApiInventory.gatherCorePrimitiveSemantics(
            inventory,
            GPUPixelBounds(0, 0, target().width, target().height),
        ) as GPUCorePrimitiveSemanticGatherResult.Refused
        assertEquals("unsupported.core_primitive.stroke.dash_exact_lowering", refused.code)
    }

    @Test
    fun `dash phase remains refused outside the bounded proof`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(4f, 8f)
                        lineTo(36f, 8f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                        pathEffect = PathEffect.Dash(floatArrayOf(8f, 4f), phase = 2f),
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("prepared.path_stroke.tessellated", inventory.recording.analysis.records.single().routeDecisionLabel)
        val refused = GPUFramePathApiInventory.gatherCorePrimitiveSemantics(
            inventory,
            GPUPixelBounds(0, 0, target().width, target().height),
        ) as GPUCorePrimitiveSemanticGatherResult.Refused
        assertEquals("unsupported.core_primitive.stroke.dash_exact_lowering", refused.code)
    }

    @Test
    fun `round cap remains bounded to one open segment before native preparation`() {
        val path = Path().apply {
            moveTo(4f, 8f)
            lineTo(16f, 8f)
            lineTo(16f, 20f)
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    path,
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.ROUND,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
        )

        val refused = gatherRefusal(inventory)

        assertEquals("unsupported.core_primitive.stroke.complex_exact_lowering", refused.code)
        assertEquals("3", refused.facts["pointCount"])
        assertEquals("round", refused.facts["cap"])
    }

    @Test
    fun `round cap width outside the pixel-exact contract refuses before native preparation`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(6f, 16f)
                        lineTo(26f, 16f)
                    },
                    Paint.stroke(ColorARGB.Red, 6f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.ROUND,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
        )

        val refused = gatherRefusal(inventory)

        assertEquals("unsupported.core_primitive.stroke.round_cap_pixel_exact_lowering", refused.code)
        assertEquals("6.0", refused.facts["width"])
        assertEquals("round", refused.facts["cap"])
    }

    @Test
    fun `vertical round cap reaches native preparation`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(16f, 6f)
                        lineTo(16f, 26f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.ROUND,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(
            GPUCorePrimitiveStrokeLoweringProof.SingleSegmentRoundPixelExactR2VerticalV1,
            geometry.strokeStyle?.loweringProof,
        )
    }

    @Test
    fun `reverse vertical round cap reaches native preparation`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(16f, 26f)
                        lineTo(16f, 6f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.ROUND,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(
            GPUCorePrimitiveStrokeLoweringProof.SingleSegmentRoundPixelExactR2ReverseVerticalV1,
            geometry.strokeStyle?.loweringProof,
        )
    }

    @Test
    fun `reverse horizontal round cap reaches native preparation`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(26f, 16f)
                        lineTo(6f, 16f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.ROUND,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(
            GPUCorePrimitiveStrokeLoweringProof.SingleSegmentRoundPixelExactR2ReverseHorizontalV1,
            geometry.strokeStyle?.loweringProof,
        )
    }

    @Test
    fun `translated reverse horizontal round cap reaches native preparation`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(26f, 16f)
                        lineTo(6f, 16f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.ROUND,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.translation(3f, 2f),
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(
            GPUCorePrimitiveStrokeLoweringProof.SingleSegmentRoundPixelExactR2ReverseHorizontalV1,
            geometry.strokeStyle?.loweringProof,
        )
    }

    @Test
    fun `scissored reverse horizontal round cap reaches native preparation`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(26f, 16f)
                        lineTo(6f, 16f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.ROUND,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.DeviceRect(RectF32.ofLTRB(24f, 15f, 27f, 18f), false),
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(
            GPUCorePrimitiveStrokeLoweringProof.SingleSegmentRoundPixelExactR2ReverseHorizontalV1,
            geometry.strokeStyle?.loweringProof,
        )
        assertEquals(GPUPixelBounds(24, 15, 27, 18), semantic.scissorBounds)
    }

    @Test
    fun `translated scissored reverse horizontal round cap reaches native preparation`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(26f, 16f)
                        lineTo(6f, 16f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.ROUND,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.translation(3f, 2f),
                    ClipStack.DeviceRect(RectF32.ofLTRB(28f, 17f, 30f, 20f), false),
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(
            GPUCorePrimitiveStrokeLoweringProof.SingleSegmentRoundPixelExactR2ReverseHorizontalV1,
            geometry.strokeStyle?.loweringProof,
        )
        assertEquals(GPUPixelBounds(28, 17, 30, 20), semantic.scissorBounds)
    }

    @Test
    fun `translated reverse vertical round cap reaches native preparation`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(16f, 26f)
                        lineTo(16f, 6f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.ROUND,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.translation(2f, 3f),
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(
            GPUCorePrimitiveStrokeLoweringProof.SingleSegmentRoundPixelExactR2ReverseVerticalV1,
            geometry.strokeStyle?.loweringProof,
        )
    }

    @Test
    fun `translated scissored reverse vertical round cap reaches native preparation`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(16f, 26f)
                        lineTo(16f, 6f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.ROUND,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.translation(2f, 3f),
                    ClipStack.DeviceRect(RectF32.ofLTRB(17f, 28f, 20f, 30f), false),
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(
            GPUCorePrimitiveStrokeLoweringProof.SingleSegmentRoundPixelExactR2ReverseVerticalV1,
            geometry.strokeStyle?.loweringProof,
        )
        assertEquals(GPUPixelBounds(17, 28, 20, 30), semantic.scissorBounds)
    }

    @Test
    fun `quarter turn round cap reaches native preparation`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(4f, 8f)
                        lineTo(16f, 8f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.ROUND,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32(sx = 0f, kx = -1f, tx = 20f, sy = 0f, ky = 1f, ty = 4f),
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(
            GPUCorePrimitiveStrokeLoweringProof.SingleSegmentRoundPixelExactR2QuarterTurnV1,
            geometry.strokeStyle?.loweringProof,
        )
    }

    @Test
    fun `half turn round cap reaches native preparation`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(8f, 4f)
                        lineTo(8f, 16f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.ROUND,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.translation(28f, 24f) * Matrix3x3F32.rotation(180f),
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(
            GPUCorePrimitiveStrokeLoweringProof.SingleSegmentRoundPixelExactR2HalfTurnV1,
            geometry.strokeStyle?.loweringProof,
        )
    }

    @Test
    fun `negative quarter turn round cap reaches native preparation`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(4f, 8f)
                        lineTo(16f, 8f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.ROUND,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.translation(12f, 24f) * Matrix3x3F32.rotation(-90f),
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals("native.path_stroke.stencil_cover", inventory.recording.analysis.records.single().routeDecisionLabel)
        val semantic = gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(
            GPUCorePrimitiveStrokeLoweringProof.SingleSegmentRoundPixelExactR2NegativeQuarterTurnV1,
            geometry.strokeStyle?.loweringProof,
        )
    }

    @Test
    fun `round cap requires an integral left-to-right segment of at least one width`() {
        listOf(
            6.5f to 26.5f,
            6f to 8f,
            26f to 6f,
        ).forEach { (startX, endX) ->
            val inventory = GPUFramePathApiInventory.plan(
                operations = listOf(
                    DisplayOp.DrawPath(
                        Path().apply {
                            moveTo(startX, 16f)
                            lineTo(endX, 16f)
                        },
                        Paint.stroke(ColorARGB.Red, 4f).copy(
                            antiAlias = false,
                            strokeCap = StrokeCap.ROUND,
                            strokeJoin = StrokeJoin.MITER,
                        ),
                        Matrix3x3F32.Identity,
                        ClipStack.WideOpen,
                    ),
                ),
                target = target(),
                config = RenderConfig.DEFAULT,
            )

            assertEquals(
                "unsupported.core_primitive.stroke.round_cap_pixel_exact_lowering",
                gatherRefusal(inventory).code,
                "startX=$startX endX=$endX",
            )
        }
    }

    @Test
    fun `single segment stroke refuses an unregistered path effect before native preparation`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(4f, 8f)
                        lineTo(24f, 8f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(pathEffect = PathEffect.Corner(2f)),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
        )

        val refused = gatherRefusal(inventory)

        assertEquals("unsupported.core_primitive.stroke.path_effect_exact_lowering", refused.code)
        assertEquals("Corner", refused.facts["pathEffect"])
    }

    @Test
    fun `public Surface empty dash preserves its identity and refuses before native preparation`() {
        val surface = Surface(32, 32)
        surface.canvas {
            drawPath(
                Path().apply {
                    moveTo(4f, 8f)
                    lineTo(24f, 8f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    pathEffect = PathEffect.Dash(floatArrayOf()),
                ),
            )
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = surface.snapshotOps(),
            target = target(),
            config = RenderConfig.DEFAULT,
        )

        val command = assertIs<NormalizedDrawCommand.FillPath>(inventory.visualCommands.single().normalized)
        assertEquals("Dash", command.pathEffectKind)
        assertTrue(
            inventory.recording.analysis.diagnostics.any { it.code == "unsupported.stroke.dash_empty" },
        )
        val refused = gatherRefusal(inventory)

        assertEquals("unsupported.core_primitive.stroke.dash_exact_lowering", refused.code)
        assertEquals("Dash", refused.facts["pathEffect"])
        assertEquals("", refused.facts["dashIntervals"])
    }

    @Test
    fun `single axis aligned hairline lowers to direct device quad`() {
        val path = Path().apply {
            moveTo(4f, 8f)
            lineTo(14f, 8f)
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    path,
                    Paint.stroke(ColorARGB.Red, 0f).copy(antiAlias = false),
                    Matrix3x3F32.Identity,
                    org.graphiks.kanvas.canvas.ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        val semantic = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(
            GPUFramePathApiInventory.gatherCorePrimitiveSemantics(inventory, GPUPixelBounds(0, 0, 32, 32)),
        ).semantics.values.single() as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)

        assertEquals(GPUCorePrimitiveGeometryMode.DirectTriangles, geometry.geometryMode)
        assertEquals(listOf(0, 1, 2, 0, 2, 3), geometry.indices)
        assertEquals(2, geometry.sourceVertexCount)
        assertEquals(GPUPixelBounds(4, 7, 14, 9), geometry.coverBounds)
    }

    @Test
    fun `single segment hairline with uniform scale lowers to direct device quad`() {
        val path = Path().apply {
            moveTo(4f, 8f)
            lineTo(14f, 8f)
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
            DisplayOp.DrawPath(
                path,
                Paint.stroke(ColorARGB.Red, 0f).copy(antiAlias = false),
                Matrix3x3F32.scaling(2f, 2f),
                org.graphiks.kanvas.canvas.ClipStack.WideOpen,
            ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
        )

        val semantic = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(
            GPUFramePathApiInventory.gatherCorePrimitiveSemantics(inventory, GPUPixelBounds(0, 0, 32, 32)),
        ).semantics.values.single() as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)

        assertEquals(GPUCorePrimitiveGeometryMode.DirectTriangles, geometry.geometryMode)
        assertEquals(listOf(0, 1, 2, 0, 2, 3), geometry.indices)
        assertEquals(GPUPixelBounds(8, 15, 28, 17), geometry.coverBounds)
    }

    @Test
    fun `single segment hairline with uniform scale and translation lowers to direct device quad`() {
        val path = Path().apply {
            moveTo(4f, 8f)
            lineTo(14f, 8f)
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    path,
                    Paint.stroke(ColorARGB.Red, 0f).copy(antiAlias = false),
                    Matrix3x3F32.translation(2f, 3f) * Matrix3x3F32.scaling(2f, 2f),
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
        )

        val semantic = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(
            GPUFramePathApiInventory.gatherCorePrimitiveSemantics(inventory, GPUPixelBounds(0, 0, 32, 32)),
        ).semantics.values.single() as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)

        assertEquals(GPUCorePrimitiveGeometryMode.DirectTriangles, geometry.geometryMode)
        assertEquals(listOf(0, 1, 2, 0, 2, 3), geometry.indices)
        assertEquals(GPUPixelBounds(10, 18, 30, 20), geometry.coverBounds)
    }

    @Test
    fun `single vertical butt miter stroke lowers to native stencil cover`() {
        val path = Path().apply {
            moveTo(16f, 4f)
            lineTo(16f, 28f)
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    path,
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals(null, inventory.preparedRefusal)
        assertEquals(
            "native.path_stroke.stencil_cover",
            inventory.recording.analysis.records.single().routeDecisionLabel,
        )
        val semantic = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(
            GPUFramePathApiInventory.gatherCorePrimitiveSemantics(
                inventory,
                GPUPixelBounds(0, 0, 32, 32),
            ),
        ).semantics.values.single() as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(GPUPixelBounds(14, 4, 18, 28), geometry.coverBounds)
    }

    @Test
    fun `single horizontal square miter stroke lowers to native stencil cover`() {
        val path = Path().apply {
            moveTo(8f, 16f)
            lineTo(24f, 16f)
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    path,
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.SQUARE,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals(null, inventory.preparedRefusal)
        assertEquals(
            "native.path_stroke.stencil_cover",
            inventory.recording.analysis.records.single().routeDecisionLabel,
        )
        val semantic = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(
            GPUFramePathApiInventory.gatherCorePrimitiveSemantics(
                inventory,
                GPUPixelBounds(0, 0, 32, 32),
            ),
        ).semantics.values.single() as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(GPUPixelBounds(6, 14, 26, 18), geometry.coverBounds)
    }

    @Test
    fun `single diagonal butt miter stroke lowers to native stencil cover`() {
        val path = Path().apply {
            moveTo(5.25f, 8.25f)
            lineTo(21.25f, 20.25f)
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    path,
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals(null, inventory.preparedRefusal)
        assertEquals(
            "native.path_stroke.stencil_cover",
            inventory.recording.analysis.records.single().routeDecisionLabel,
        )
        val semantic = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(
            GPUFramePathApiInventory.gatherCorePrimitiveSemantics(
                inventory,
                GPUPixelBounds(0, 0, 32, 32),
            ),
        ).semantics.values.single() as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(GPUPixelBounds(4, 6, 23, 22), geometry.coverBounds)
    }

    @Test
    fun `single diagonal butt miter stroke retains an integral device scissor`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipRect(
                RectF32.ofLTRB(8f, 10f, 20f, 19f),
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawPath(
                Path().apply {
                    moveTo(5.25f, 8.25f)
                    lineTo(21.25f, 20.25f)
                },
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    antiAlias = false,
                    strokeCap = StrokeCap.BUTT,
                    strokeJoin = StrokeJoin.MITER,
                ),
            )
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = surface.snapshotOps(),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        val visual = inventory.visualCommands.single()
        assertEquals(
            GPUClipExecutionPlan.ScissorOnly(GPUPixelBounds(8, 10, 20, 19)),
            visual.clipExecutionPlan,
        )
        assertEquals(
            "native.path_stroke.stencil_cover",
            inventory.recording.analysis.records.single().routeDecisionLabel,
        )
    }

    @Test
    fun `single diagonal square miter stroke lowers to native stencil cover`() {
        val path = Path().apply {
            moveTo(5.25f, 8.25f)
            lineTo(21.25f, 20.25f)
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    path,
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.SQUARE,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals(null, inventory.preparedRefusal)
        assertEquals(
            "native.path_stroke.stencil_cover",
            inventory.recording.analysis.records.single().routeDecisionLabel,
        )
        val semantic = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(
            GPUFramePathApiInventory.gatherCorePrimitiveSemantics(
                inventory,
                GPUPixelBounds(0, 0, 32, 32),
            ),
        ).semantics.values.single() as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(GPUPixelBounds(2, 5, 25, 24), geometry.coverBounds)
    }

    @Test
    fun `single diagonal butt miter stroke with uniform scale and translation lowers to native stencil cover`() {
        val path = Path().apply {
            moveTo(4.125f, 4.125f)
            lineTo(12.125f, 8.625f)
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    path,
                    Paint.stroke(ColorARGB.Red, 2f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.translation(2f, 3f) * Matrix3x3F32.scaling(2f, 2f),
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals(null, inventory.preparedRefusal)
        assertEquals(
            "native.path_stroke.stencil_cover",
            inventory.recording.analysis.records.single().routeDecisionLabel,
        )
        val semantic = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(
            GPUFramePathApiInventory.gatherCorePrimitiveSemantics(
                inventory,
                GPUPixelBounds(0, 0, 32, 32),
            ),
        ).semantics.values.single() as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(GPUPixelBounds(9, 9, 28, 22), geometry.coverBounds)
    }

    @Test
    fun `single diagonal square miter stroke with uniform scale and translation lowers to native stencil cover`() {
        val path = Path().apply {
            moveTo(4.125f, 4.125f)
            lineTo(12.125f, 8.625f)
        }
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    path,
                    Paint.stroke(ColorARGB.Red, 2f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.SQUARE,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.translation(2f, 3f) * Matrix3x3F32.scaling(2f, 2f),
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals(null, inventory.preparedRefusal)
        assertEquals(
            "native.path_stroke.stencil_cover",
            inventory.recording.analysis.records.single().routeDecisionLabel,
        )
        val semantic = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(
            GPUFramePathApiInventory.gatherCorePrimitiveSemantics(
                inventory,
                GPUPixelBounds(0, 0, 32, 32),
            ),
        ).semantics.values.single() as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(GPUPixelBounds(7, 8, 29, 23), geometry.coverBounds)
    }

    @Test
    fun `diagonal round cap refuses before native preparation`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(5.25f, 8.25f)
                        lineTo(21.25f, 20.25f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.ROUND,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
        )

        val refused = gatherRefusal(inventory)

        assertEquals("unsupported.core_primitive.stroke.round_cap_pixel_exact_lowering", refused.code)
        assertEquals("4.0", refused.facts["width"])
        assertEquals("round", refused.facts["cap"])
        assertTrue(
            inventory.recording.analysis.records.single().routeDecisionLabel !=
                "native.path_stroke.stencil_cover",
        )
    }

    @Test
    fun `simple stroke with nonuniform scale refuses before native preparation`() {
        val inventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(5.25f, 8.25f)
                        lineTo(21.25f, 20.25f)
                    },
                    Paint.stroke(ColorARGB.Red, 4f).copy(
                        antiAlias = false,
                        strokeCap = StrokeCap.BUTT,
                        strokeJoin = StrokeJoin.MITER,
                    ),
                    Matrix3x3F32.scaling(2f, 1f),
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
        )

        assertEquals(
            "refused.unsupported.geometry.perspective_path",
            inventory.recording.analysis.records.single().routeDecisionLabel,
        )
        assertTrue(inventory.recording.taskList.tasks.none { it is GPUTask.Render })
        val prepared = GPUFramePathApiInventory.prepareNativeTaskList(
            inventory = inventory,
            capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER),
            targetBounds = GPUPixelBounds(0, 0, 32, 32),
        )
        val refusal = assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(prepared)
        assertEquals("unsupported.geometry.perspective_path", refusal.diagnostic.code.value)
    }

    @Test
    fun `zero length butt and square strokes refuse before native preparation`() {
        listOf(StrokeCap.BUTT, StrokeCap.SQUARE).forEach { cap ->
            val inventory = GPUFramePathApiInventory.plan(
                operations = listOf(
                    DisplayOp.DrawPath(
                        Path().apply {
                            moveTo(10f, 10f)
                            lineTo(10f, 10f)
                        },
                        Paint.stroke(ColorARGB.Red, 4f).copy(
                            antiAlias = false,
                            strokeCap = cap,
                            strokeJoin = StrokeJoin.MITER,
                        ),
                        Matrix3x3F32.Identity,
                        ClipStack.WideOpen,
                    ),
                ),
                target = target(),
                config = RenderConfig.DEFAULT,
            )

            val refused = gatherRefusal(inventory)

            assertEquals("unsupported.core_primitive.stroke.complex_exact_lowering", refused.code, "cap=$cap")
            assertEquals("1", refused.facts["pointCount"], "cap=$cap")
            assertEquals(cap.name.lowercase(), refused.facts["cap"], "cap=$cap")
            assertTrue(
                inventory.recording.analysis.records.single().routeDecisionLabel !=
                    "native.path_stroke.stencil_cover",
                "cap=$cap",
            )
        }
    }

    @Test
    fun `single segment round joins and widths outside the fixed budget refuse`() {
        val path = Path().apply {
            moveTo(4f, 8f)
            lineTo(24f, 8f)
        }
        val roundJoinInventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    path,
                    Paint.stroke(ColorARGB.Red, 4f).copy(strokeJoin = StrokeJoin.ROUND),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
        )
        val overBudgetInventory = GPUFramePathApiInventory.plan(
            operations = listOf(
                DisplayOp.DrawPath(
                    path,
                    Paint.stroke(ColorARGB.Red, 65f),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target = target(),
            config = RenderConfig.DEFAULT,
        )

        assertEquals(
            "unsupported.core_primitive.stroke.join_exact_lowering",
            gatherRefusal(roundJoinInventory).code,
        )
        assertEquals(
            "unsupported.core_primitive.stroke.width_budget",
            gatherRefusal(overBudgetInventory).code,
        )
    }

    @Test
    fun `single segment invalid miter limits refuse before native preparation`() {
        val path = Path().apply {
            moveTo(4f, 8f)
            lineTo(24f, 8f)
        }
        listOf(0f, Float.NaN, Float.POSITIVE_INFINITY).forEach { miterLimit ->
            val inventory = GPUFramePathApiInventory.plan(
                operations = listOf(
                    DisplayOp.DrawPath(
                        path,
                        Paint.stroke(ColorARGB.Red, 4f).copy(
                            antiAlias = false,
                            strokeMiter = miterLimit,
                        ),
                        Matrix3x3F32.Identity,
                        ClipStack.WideOpen,
                    ),
                ),
                target = target(),
                config = RenderConfig.DEFAULT,
            )

            assertEquals(
                "unsupported.core_primitive.stroke.miter_exact_lowering",
                gatherRefusal(inventory).code,
                "miterLimit=$miterLimit",
            )
        }
    }

    @Test
    fun `complex dashed round stroke refuses with stable exact lowering code`() {
        val inventory = GPUFramePathApiInventory.plan(
            listOf(DisplayOp.DrawPath(
                triangle(),
                Paint.stroke(ColorARGB.Red, 4f).copy(
                    strokeCap = StrokeCap.ROUND,
                    strokeJoin = StrokeJoin.BEVEL,
                    pathEffect = PathEffect.Dash(floatArrayOf(5f, 2f), phase = 1f),
                ),
                Matrix3x3F32.Identity,
                org.graphiks.kanvas.canvas.ClipStack.WideOpen,
            )),
            target(),
            RenderConfig.DEFAULT,
        )

        val refused = assertIs<GPUCorePrimitiveSemanticGatherResult.Refused>(
            GPUFramePathApiInventory.gatherCorePrimitiveSemantics(
                inventory,
                GPUPixelBounds(0, 0, 32, 32),
            ),
        )
        assertEquals("unsupported.core_primitive.stroke.dash_exact_lowering", refused.code)
        assertEquals("round", refused.facts["cap"])
        assertEquals("5.0,2.0", refused.facts["dashIntervals"])
    }

    @Test
    fun `mixed core frame remains fail closed until native core capability exists`() {
        val surface = Surface(48, 40)
        surface.canvas {
            drawColor(ColorARGB.fromRGBA(0.05f, 0.06f, 0.07f, 1f))
            translate(1f, 2f)
            clipRect(RectF32.ofLTRB(0f, 0f, 46f, 38f), ClipOp.INTERSECT, antiAlias = false)
            drawPoint(
                2f,
                3f,
                Paint.stroke(ColorARGB.White, 1f).copy(strokeCap = StrokeCap.SQUARE, antiAlias = false),
            )
            drawPoints(
                PointMode.LINES,
                listOf(Point2F32(3f, 4f), Point2F32(14f, 9f)),
                Paint.stroke(ColorARGB.Red, 2f),
            )
            drawRect(RectF32.ofLTRB(4f, 11f, 14f, 20f), Paint.fill(ColorARGB.Green))
            clipRRect(
                RRectF32.of(RectF32.ofLTRB(1f, 1f, 43f, 35f), radius = 3f),
                ClipOp.INTERSECT,
                antiAlias = true,
            )
            drawRRect(RRectF32.of(RectF32.ofLTRB(16f, 11f, 28f, 21f), radius = 2f), Paint.fill(ColorARGB.Blue))
            clipPath(
                Path().apply { addRect(RectF32.ofLTRB(2f, 2f, 42f, 34f)) },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawDRRect(
                RRectF32.of(RectF32.ofLTRB(29f, 10f, 43f, 25f), radius = 3f),
                RRectF32.of(RectF32.ofLTRB(33f, 14f, 39f, 21f), radius = 1f),
                Paint.fill(ColorARGB.White),
            )
            clipRect(RectF32.ofLTRB(20f, 16f, 24f, 20f), ClipOp.DIFFERENCE, antiAlias = true)
            drawPath(
                Path().apply {
                    moveTo(5f, 27f)
                    lineTo(22f, 27f)
                    lineTo(13f, 36f)
                    close()
                },
                Paint.fill(ColorARGB.Red),
            )
            flushAndSnapshot(RectF32.ofLTRB(0f, 0f, 48f, 40f))
        }

        val frame = GPUFramePathApiInventory.plan(
            surface.snapshotOps(),
            target(48, 40),
            RenderConfig.DEFAULT,
        )
        val readbackId = GPUReadbackRequestID("readback.kanvas.slice-12a.core")
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            frame,
            org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig().buildCapabilities(),
            GPUPixelBounds(0, 0, 48, 40),
            readbackId,
        )
        val refused = assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(
            preparation,
            (preparation as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}"
            },
        )
        assertEquals(
            "unsupported.pipeline.capability_missing",
            refused.diagnostic.code.value,
        )
    }

    @Test
    fun `reserved provenance partitions three draws through commands tasks steps and telemetry`() {
        val surface = Surface(32, 32)
        surface.canvas {
            drawAnnotation(RectF32.Empty, GPU_FRAME_PROVENANCE_ANNOTATION_KEY, "harness-background")
            drawRRect(RRectF32.of(RectF32.ofLTRB(1f, 2f, 5f, 7f), radius = 1f), Paint.fill(ColorARGB.Red))
            drawAnnotation(RectF32.Empty, GPU_FRAME_PROVENANCE_ANNOTATION_KEY, "gm-content")
            drawDRRect(
                RRectF32.of(RectF32.ofLTRB(8f, 3f, 16f, 13f), radius = 2f),
                RRectF32.of(RectF32.ofLTRB(10f, 5f, 14f, 11f), radius = 1f),
                Paint.fill(ColorARGB.Green),
            )
            drawAnnotation(RectF32.Empty, GPU_FRAME_PROVENANCE_ANNOTATION_KEY, "none")
            drawPath(
                Path().apply {
                    moveTo(18f, 2f)
                    lineTo(29f, 2f)
                    lineTo(24f, 14f)
                    close()
                },
                Paint.fill(ColorARGB.Blue),
            )
        }

        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(),
            target(),
            RenderConfig.DEFAULT,
        )

        assertEquals(
            listOf(
                GPUFrameProvenance.HarnessBackground,
                GPUFrameProvenance.GmContent,
                GPUFrameProvenance.None,
            ),
            plan.visualCommands.map { it.provenance },
        )
        assertEquals(3, plan.visualCommands.size)
        assertEquals(3, plan.normalizedCommands.size)
        assertEquals(3, plan.recording.recordedCommands.size)
        assertEquals(3, plan.telemetryInputs.size)
        assertEquals(
            plan.visualCommands.map { it.provenance },
            plan.telemetryInputs.map { it.provenance },
        )

        val taskProvenance = plan.recording.taskList.tasks
            .filterIsInstance<GPUTask.Render>()
            .flatMap { task -> task.frameProvenanceByPacketId.values }
        assertEquals(
            plan.visualCommands.map { it.provenance },
            taskProvenance,
            plan.recording.routeDiagnostics.joinToString("\n"),
        )

        val stepProvenance = plan.framePlan.steps
            .filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap { step -> step.frameProvenanceByPacketId.values }
        assertEquals(plan.visualCommands.map { it.provenance }, stepProvenance)

        assertEquals(3, plan.framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .sumOf { it.drawPackets.size })
    }

    @Test
    fun `direct inventory preserves solid image solid order with prepared image facts`() {
        val image = org.graphiks.kanvas.image.Image(
            width = GPUPreparedImageTestFixtures.rgbaPremul2x2Width,
            height = GPUPreparedImageTestFixtures.rgbaPremul2x2Height,
            colorType = GPUPreparedImageTestFixtures.rgbaPremul2x2ColorType,
            sourceId = "inventory-prepared-image",
            pixels = GPUPreparedImageTestFixtures.rgbaPremul2x2Bytes,
            alphaType = org.graphiks.kanvas.image.AlphaType.PREMUL,
        )
        val operations = listOf(
            DisplayOp.DrawRect(
                RectF32.ofLTRB(0f, 0f, 2f, 2f),
                Paint.fill(ColorARGB.Red).copy(antiAlias = false),
                Matrix3x3F32.Identity,
                org.graphiks.kanvas.canvas.ClipStack.WideOpen,
            ),
            DisplayOp.DrawImage(
                image = image,
                src = RectF32.ofLTRB(0f, 0f, 2f, 2f),
                dst = RectF32.ofLTRB(2f, 0f, 4f, 2f),
                paint = null,
                transform = Matrix3x3F32.Identity,
                clip = org.graphiks.kanvas.canvas.ClipStack.WideOpen,
            ),
            DisplayOp.DrawRect(
                RectF32.ofLTRB(4f, 0f, 6f, 2f),
                Paint.fill(ColorARGB.Blue).copy(antiAlias = false),
                Matrix3x3F32.Identity,
                org.graphiks.kanvas.canvas.ClipStack.WideOpen,
            ),
        )

        val baseCapabilities = capabilitiesWith(FILL_RECT_CAPABILITY)
        val capabilities = GPUCapabilities(
            implementation = baseCapabilities.implementation,
            facts = baseCapabilities.facts,
            knownUnsupportedFacts = baseCapabilities.knownUnsupportedFacts,
            snapshotId = "${baseCapabilities.snapshotId}:prepared-image",
            limits = GPULimits(
                maxTextureDimension2D = 8192,
                copyBytesPerRowAlignment = 256,
                minUniformBufferOffsetAlignment = 256,
                maxBufferSize = 1L shl 30,
                maxDynamicUniformBuffersPerPipelineLayout = 1,
            ),
            textureFormatSampleSupport = baseCapabilities.textureFormatSampleSupport,
            rendererFeatures = baseCapabilities.rendererFeatures,
            copyAsDrawCapability = baseCapabilities.copyAsDrawCapability,
        )
        val plan = GPUFramePathApiInventory.plan(
            operations,
            org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts(8, 4, "rgba8unorm-srgb"),
            RenderConfig.DEFAULT,
            capabilities,
        )

        assertEquals(
            listOf(
                NormalizedDrawCommand.FillRect::class,
                NormalizedDrawCommand.DrawImageRect::class,
                NormalizedDrawCommand.FillRect::class,
            ),
            plan.visualCommands.map { it.normalized::class },
        )
        assertEquals(listOf(0, 1, 2), plan.normalizedCommands.map { it.commandId.value })
        assertNotNull(plan.visualCommands[1].preparedImage)
        val preparation = GPUFramePathApiInventory.preparePreparedNativeTaskList(
            inventory = plan,
            capabilities = capabilities,
            targetBounds = GPUPixelBounds(0, 0, 8, 4),
            readbackRequestId = GPUReadbackRequestID("inventory-prepared-image"),
        )
        val prepared = assertIs<org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedSurfaceFrameResult.Recorded>(
            preparation,
            preparation.toString(),
        )
        val preparedPlan = org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlanner.plan(
            prepared.taskList,
        )
        assertEquals(
            3,
            preparedPlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
                .sumOf { it.drawPackets.size },
        )
    }

    @Test
    fun `unknown provenance annotation is inert and cannot activate a reserved value`() {
        val surface = Surface(16, 16)
        surface.canvas {
            drawAnnotation(RectF32.Empty, GPU_FRAME_PROVENANCE_ANNOTATION_KEY, "gm-content")
            drawRect(RectF32.ofLTRB(1f, 1f, 4f, 4f), Paint.fill(ColorARGB.Red))
            drawAnnotation(RectF32.Empty, GPU_FRAME_PROVENANCE_ANNOTATION_KEY, "GM-CONTENT")
            drawRect(RectF32.ofLTRB(5f, 1f, 8f, 4f), Paint.fill(ColorARGB.Green))
            drawAnnotation(RectF32.Empty, "unrelated.annotation", "harness-background")
            drawRect(RectF32.ofLTRB(9f, 1f, 12f, 4f), Paint.fill(ColorARGB.Blue))
        }

        val plan = GPUFramePathApiInventory.plan(surface.snapshotOps(), target(16, 16), RenderConfig.DEFAULT)

        assertEquals(
            listOf(
                GPUFrameProvenance.GmContent,
                GPUFrameProvenance.GmContent,
                GPUFrameProvenance.GmContent,
            ),
            plan.visualCommands.map { it.provenance },
        )
        assertEquals(3, plan.stateEvents.count { it.kind == GPUFramePathStateKind.Annotation })
        assertEquals(3, plan.visualCommands.size)
    }

    @Test
    fun `core inventory captures target bounds geometry clip blend and state only markers`() {
        val surface = Surface(40, 30)
        surface.canvas {
            translate(2f, 3f)
            clipRect(RectF32.ofLTRB(0f, 0f, 32f, 24f), ClipOp.INTERSECT, antiAlias = false)
            drawColor(ColorARGB.Red, BlendMode.SRC_OVER)
            clear(ColorARGB.Transparent)
            drawPoint(1f, 1f, Paint.fill(ColorARGB.Green).copy(antiAlias = false))
            drawPoints(
                PointMode.LINES,
                listOf(Point2F32(2f, 2f), Point2F32(8f, 8f)),
                Paint.stroke(ColorARGB.Blue, 2f),
            )
            drawRect(RectF32.ofLTRB(3f, 4f, 10f, 12f), Paint.fill(ColorARGB.Red))
            drawRRect(RRectF32.of(RectF32.ofLTRB(11f, 4f, 18f, 12f), radius = 2f), Paint.fill(ColorARGB.Green))
            drawDRRect(
                RRectF32.of(RectF32.ofLTRB(19f, 3f, 30f, 15f), radius = 2f),
                RRectF32.of(RectF32.ofLTRB(22f, 6f, 27f, 12f), radius = 1f),
                Paint.fill(ColorARGB.Blue),
            )
            drawPath(
                Path().apply {
                    moveTo(2f, 16f)
                    lineTo(12f, 16f)
                    lineTo(7f, 25f)
                    close()
                },
                Paint.fill(ColorARGB.White),
            )
            drawAnnotation(RectF32.Empty, GPU_FRAME_PROVENANCE_ANNOTATION_KEY, "none")
            flushAndSnapshot(RectF32.ofLTRB(0f, 0f, 40f, 30f))
        }

        val plan = GPUFramePathApiInventory.plan(surface.snapshotOps(), target(40, 30), RenderConfig.DEFAULT)

        assertEquals(8, plan.visualCommands.size)
        plan.visualCommands.forEach { visual ->
            assertTrue(visual.targetSpaceBounds.left >= 0f)
            assertTrue(visual.targetSpaceBounds.top >= 0f)
            assertTrue(visual.targetSpaceBounds.right <= 40f)
            assertTrue(visual.targetSpaceBounds.bottom <= 30f)
            assertNotNull(visual.geometryCoverage)
            assertNotNull(visual.clipCoverage)
            assertEquals(visual.normalized.blend.mode, visual.blendPlan.mode)
            assertEquals(visual.provenance, visual.normalized.source.frameProvenance)
        }
        assertTrue(plan.visualCommands.any { it.geometryCoverage == GPUCoverageConsumption.StencilCoverage1x })
        assertTrue(plan.visualCommands.filterNot { it.normalized.source.operation == "clear" }
            .all { it.clipCoverage is GPUClipCoveragePlan.Scissor })
        assertEquals(1, plan.stateEvents.count { it.kind == GPUFramePathStateKind.Transform })
        assertEquals(1, plan.stateEvents.count { it.kind == GPUFramePathStateKind.Clip })
        assertEquals(1, plan.stateEvents.count { it.kind == GPUFramePathStateKind.Annotation })
        assertEquals(1, plan.stateEvents.count { it.kind == GPUFramePathStateKind.FlushSnapshot })
        assertEquals(8, plan.normalizedCommands.size)
    }

    @Test
    fun `complex clip captures source elements and remains fail closed before B2 topology`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipRect(RectF32.ofLTRB(1f, 1f, 31f, 31f), ClipOp.INTERSECT, antiAlias = true)
            clipRRect(
                RRectF32.of(
                    rect = RectF32.ofLTRB(4f, 4f, 28f, 28f),
                    topLeft = CornerRadiiF32.of(2f, 2f),
                    topRight = CornerRadiiF32.of(2f, 2f),
                    bottomRight = CornerRadiiF32.of(2f, 2f),
                    bottomLeft = CornerRadiiF32.of(2f, 2f),
                ),
                ClipOp.DIFFERENCE,
                antiAlias = true,
            )
            clipPath(
                Path().apply { addRect(RectF32.ofLTRB(10f, 10f, 20f, 20f)) },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawRRect(RRectF32.of(RectF32.ofLTRB(0f, 0f, 32f, 32f), radius = 2f), Paint.fill(ColorARGB.Red))
        }

        val plan = GPUFramePathApiInventory.plan(surface.snapshotOps(), target(), RenderConfig.DEFAULT)
        val visual = plan.visualCommands.single()
        val clip = assertIs<GPUClipCoveragePlan.Mask>(visual.clipCoverage)

        assertEquals(
            setOf(
                GPUClipCoverageElementKind.Rect,
                GPUClipCoverageElementKind.RRect,
                GPUClipCoverageElementKind.Path,
            ),
            clip.elements.map { it.kind }.toSet(),
        )
        assertEquals(3, plan.stateEvents.count { it.kind == GPUFramePathStateKind.Clip })
        val preparation = GPUFramePathApiInventory.prepareNativeTaskList(
            plan,
            org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig().buildCapabilities(),
            GPUPixelBounds(0, 0, 32, 32),
        )
        assertEquals(
            "unsupported.clip.complex_stack",
            assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(preparation).diagnostic.code.value,
        )
    }

    @Test
    fun `mapper selects no clip once and propagates it unchanged to the draw packet`() {
        val plan = GPUFramePathApiInventory.plan(
            operations = listOf(DisplayOp.DrawRect(
                RectF32.ofLTRB(2f, 3f, 12f, 14f),
                Paint.fill(ColorARGB.Red),
                Matrix3x3F32.Identity,
                org.graphiks.kanvas.canvas.ClipStack.WideOpen,
            )),
            target = target(),
            config = RenderConfig.DEFAULT,
            capabilities = capabilitiesWith(FILL_RECT_CAPABILITY),
        )

        assertClipExecutionPropagation(plan, GPUClipExecutionPlan.NoClip)
    }

    @Test
    fun `mapper selects exact integral scissor once and propagates it unchanged`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipRect(RectF32.ofLTRB(3f, 4f, 24f, 27f), ClipOp.INTERSECT, antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 30f, 30f), Paint.fill(ColorARGB.Red))
        }

        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(),
            target(),
            RenderConfig.DEFAULT,
            capabilitiesWith(FILL_RECT_CAPABILITY),
        )
        val expected = GPUClipExecutionPlan.ScissorOnly(GPUPixelBounds(3, 4, 24, 27))

        assertClipExecutionPropagation(plan, expected)
    }

    @Test
    fun `mapper selects a single intersect rrect as analytic coverage`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipRRect(
                RRectF32.of(RectF32.ofLTRB(3f, 4f, 24f, 27f), radius = 3f),
                ClipOp.INTERSECT,
                antiAlias = true,
            )
            drawRect(RectF32.ofLTRB(0f, 0f, 30f, 30f), Paint.fill(ColorARGB.Red))
        }

        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(),
            target(),
            RenderConfig.DEFAULT,
            capabilitiesWith(FILL_RECT_CAPABILITY),
        )
        val execution = assertIs<GPUClipExecutionPlan.AnalyticCoverage>(
            plan.visualCommands.single().clipExecutionPlan,
        )

        assertIs<GPUClipExecutionGeometry.RRect>(execution.geometry)
        assertTrue(execution.antiAlias)
        assertClipExecutionPropagation(plan, execution)
    }

    @Test
    fun `transformed rrect clip keeps device-space provenance for rect rrect and path consumers`() {
        val surface = Surface(64, 64)
        surface.canvas {
            translate(4f, 6f)
            scale(1.5f, .75f)
            clipRRect(
                RRectF32.of(RectF32.ofLTRB(4f, 8f, 36f, 56f), radius = 4f),
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            resetMatrix()
            drawRect(RectF32.ofLTRB(0f, 0f, 64f, 64f), Paint.fill(ColorARGB.Red).copy(antiAlias = false))
            drawRRect(RRectF32.of(RectF32.ofLTRB(8f, 8f, 56f, 56f), radius = 3f), Paint.fill(ColorARGB.Green).copy(antiAlias = false))
            drawPath(triangle(), Paint.fill(ColorARGB.Blue).copy(antiAlias = false))
        }

        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(),
            target(64, 64),
            RenderConfig.DEFAULT,
            capabilitiesWith(FILL_RECT_CAPABILITY),
        )

        assertEquals(3, plan.visualCommands.size)
        plan.visualCommands.forEach { visual ->
            val analytic = assertIs<GPUClipExecutionPlan.AnalyticCoverage>(visual.clipExecutionPlan)
            assertIs<GPUClipExecutionGeometry.RRect>(analytic.geometry)
            assertEquals("scale-translate", visual.normalized.clip.coverageRequest!!.elements.single().transformClass)
        }
    }

    @Test
    fun `mapper preserves depth one coverage and execution identity while bypassing frame mask budget`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipRRect(
                RRectF32.of(RectF32.ofLTRB(3f, 4f, 24f, 27f), radius = 3f),
                ClipOp.INTERSECT,
                antiAlias = true,
            )
            drawRect(RectF32.ofLTRB(0f, 0f, 30f, 30f), Paint.fill(ColorARGB.Red))
        }
        val operations = surface.snapshotOps()
        val capabilities = capabilitiesWith(FILL_RECT_CAPABILITY)
        val canonical = GPUFramePathApiInventory.plan(
            operations,
            target(),
            RenderConfig.DEFAULT,
            capabilities,
        )
        val budgetBypass = GPUFramePathApiInventory.plan(
            operations,
            target(),
            RenderConfig(maxClipIntermediateBytes = 1u),
            capabilities,
        )
        val canonicalVisual = canonical.visualCommands.single()
        val bypassVisual = budgetBypass.visualCommands.single()
        val canonicalCoverage = assertIs<GPUClipCoveragePlan.Mask>(canonicalVisual.clipCoverage)
        val bypassCoverage = assertIs<GPUClipCoveragePlan.Mask>(bypassVisual.clipCoverage)
        val canonicalExecution = assertIs<GPUClipExecutionPlan.AnalyticCoverage>(
            canonicalVisual.clipExecutionPlan,
        )
        val bypassExecution = assertIs<GPUClipExecutionPlan.AnalyticCoverage>(
            bypassVisual.clipExecutionPlan,
        )

        assertEquals(canonicalCoverage, bypassCoverage)
        assertEquals(canonicalCoverage.hashCode(), bypassCoverage.hashCode())
        assertEquals(canonicalExecution.canonicalIdentity(), bypassExecution.canonicalIdentity())
        assertClipExecutionPropagation(budgetBypass, bypassExecution)
    }

    @Test
    fun `mapper promotes two to four simple intersections and preserves other clip routes`() {
        fun executionFor(buildClip: org.graphiks.kanvas.canvas.Canvas.() -> Unit): GPUClipExecutionPlan {
            val surface = Surface(32, 32)
            surface.canvas {
                buildClip()
                drawRect(RectF32.ofLTRB(0f, 0f, 30f, 30f), Paint.fill(ColorARGB.Red))
            }
            return requireNotNull(
                GPUFramePathApiInventory.plan(
                    surface.snapshotOps(),
                    target(),
                    RenderConfig.DEFAULT,
                    capabilitiesWith(
                        FILL_RECT_CAPABILITY,
                        PATH_FILL_STENCIL_COVER,
                    ),
                ).visualCommands.single().clipExecutionPlan,
            )
        }

        assertIs<GPUClipExecutionPlan.AnalyticCoverage>(executionFor {
            clipRect(RectF32.ofLTRB(2.25f, 3.5f, 24.75f, 27.25f), ClipOp.INTERSECT, antiAlias = true)
        })
        assertIs<GPUClipExecutionPlan.AnalyticCoverage>(executionFor {
            clipRRect(
                RRectF32.of(RectF32.ofLTRB(2f, 3f, 25f, 28f), radius = 3f),
                ClipOp.INTERSECT,
                antiAlias = false,
            )
        })
        assertIs<GPUClipExecutionPlan.AnalyticIntersection>(executionFor {
            clipRRect(
                RRectF32.of(RectF32.ofLTRB(2f, 2f, 29f, 29f), radius = 3f),
                ClipOp.INTERSECT,
                antiAlias = true,
            )
            clipRect(RectF32.ofLTRB(12f, 10f, 20f, 22f), ClipOp.INTERSECT, antiAlias = false)
        })
        assertIs<GPUClipExecutionPlan.CoverageMask>(executionFor {
            clipRRect(
                RRectF32.of(RectF32.ofLTRB(2f, 2f, 29f, 29f), radius = 3f),
                ClipOp.INTERSECT,
                antiAlias = true,
            )
            clipRect(RectF32.ofLTRB(12f, 10f, 20f, 22f), ClipOp.DIFFERENCE, antiAlias = false)
        })
        assertIs<GPUClipExecutionPlan.StencilCoverage>(executionFor {
            clipPath(
                Path().apply {
                    addRect(RectF32.ofLTRB(3f, 3f, 26f, 27f))
                    fillType = FillType.INVERSE_WINDING
                },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
        })
    }

    @Test
    fun `mapper turns an over-depth public clip stack into a stable pre-draw refusal`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipRect(RectF32.ofLTRB(1f, 1f, 31f, 31f), ClipOp.INTERSECT, antiAlias = true)
            clipRect(RectF32.ofLTRB(8f, 8f, 24f, 24f), ClipOp.DIFFERENCE, antiAlias = true)
            clipRect(RectF32.ofLTRB(4f, 4f, 28f, 28f), ClipOp.INTERSECT, antiAlias = true)
            drawRect(RectF32.ofLTRB(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.Red))
        }

        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(),
            target(),
            RenderConfig(maxClipStackDepth = 2u),
            capabilitiesWith(FILL_RECT_CAPABILITY),
        )

        val visual = plan.visualCommands.single()
        assertEquals(
            "unsupported.clip.depth_budget",
            assertIs<GPUClipCoveragePlan.Refused>(visual.clipCoverage).code,
        )
        assertEquals(
            "unsupported.clip.depth_budget",
            assertIs<GPUClipExecutionPlan.Refused>(visual.clipExecutionPlan).code,
        )
    }

    @Test
    fun `mapper uses the analytic intersection frame route before mask byte budget`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipRect(RectF32.ofLTRB(2.25f, 2.5f, 29.25f, 29.5f), antiAlias = true)
            clipRRect(RRectF32.of(RectF32.ofLTRB(4f, 4f, 27f, 27f), radius = 3f), antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.Red))
        }

        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(),
            target(),
            RenderConfig(maxClipIntermediateBytes = 1u),
            capabilitiesWith(FILL_RECT_CAPABILITY),
        )

        val execution = assertIs<GPUClipExecutionPlan.AnalyticIntersection>(
            plan.visualCommands.single().clipExecutionPlan,
        )
        assertEquals(2, execution.elements.size)
        assertClipExecutionPropagation(plan, execution)
    }

    @Test
    fun `perspective clip capture cannot reach analytic execution authority`() {
        val surface = Surface(32, 32)
        surface.canvas {
            setMatrix(Matrix3x3F32.of(1f, 0f, 0f, 0f, 1f, 0f, 0.1f, 0f, 1f))
            clipRect(RectF32.ofLTRB(2f, 3f, 24f, 27f), ClipOp.INTERSECT, antiAlias = true)
            resetMatrix()
            drawRect(RectF32.ofLTRB(0f, 0f, 30f, 30f), Paint.fill(ColorARGB.Red))
        }

        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(),
            target(),
            RenderConfig.DEFAULT,
            capabilitiesWith(FILL_RECT_CAPABILITY),
        )

        assertTrue(plan.visualCommands.none { it.clipExecutionPlan is GPUClipExecutionPlan.AnalyticCoverage })
    }

    @Test
    fun `canonical bounded clip and stencil cover facts cross only their exact capability gates`() {
        val boundedClipCapabilities = org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig(
            boundedClipEnabled = false,
        ).buildCapabilities().withCapabilities(
            FILL_RECT_CAPABILITY,
            BOUNDED_CLIP_NATIVE,
        )
        val clippedRect = Surface(32, 32).also { surface ->
            surface.canvas {
                clipRRect(
                    RRectF32.of(RectF32.ofLTRB(2f, 3f, 28f, 29f), radius = 3f),
                    ClipOp.INTERSECT,
                    antiAlias = true,
                )
                drawRect(RectF32.ofLTRB(0f, 0f, 30f, 30f), Paint.fill(ColorARGB.Red))
            }
        }
        val boundedPlan = GPUFramePathApiInventory.plan(
            clippedRect.snapshotOps(),
            target(),
            RenderConfig.DEFAULT,
            boundedClipCapabilities,
        )
        assertIs<GPUClipExecutionPlan.AnalyticCoverage>(
            boundedPlan.visualCommands.single().clipExecutionPlan,
        )

        val pathSurface = Surface(32, 32).also { surface ->
            surface.canvas {
                drawPath(triangle(), Paint.fill(ColorARGB.Blue).copy(antiAlias = false))
            }
        }
        val stencilCapabilityFacts = org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig()
            .buildCapabilities()
            .withCapabilities(FILL_RECT_CAPABILITY, PATH_FILL_STENCIL_COVER)
        val stencilCapabilities = GPUCapabilities(
            implementation = stencilCapabilityFacts.implementation,
            facts = stencilCapabilityFacts.facts,
            knownUnsupportedFacts = stencilCapabilityFacts.knownUnsupportedFacts,
            snapshotId = "${stencilCapabilityFacts.snapshotId}:observed-limits",
            limits = GPULimits(
                maxTextureDimension2D = 8192,
                copyBytesPerRowAlignment = 256,
                minUniformBufferOffsetAlignment = 256,
                maxBufferSize = 1L shl 30,
                maxDynamicUniformBuffersPerPipelineLayout = 1,
            ),
        )
        val pathPlan = GPUFramePathApiInventory.plan(
            pathSurface.snapshotOps(),
            target(),
            RenderConfig.DEFAULT,
            stencilCapabilities,
        )
        assertEquals(
            "native.path_fill.stencil_cover",
            pathPlan.recording.analysis.records.single().routeDecisionLabel,
        )
        assertEquals(
            listOf("route:native.path_fill.stencil_cover"),
            pathPlan.recording.routeDiagnostics,
        )
        val preparedPath = GPUFramePathApiInventory.prepareNativeTaskList(
            pathPlan,
            stencilCapabilities,
            GPUPixelBounds(0, 0, 32, 32),
        )
        assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            preparedPath,
            (preparedPath as? GPUCorePrimitivePreparedFrameResult.Refused)?.diagnostic?.let {
                "${it.code.value}: ${it.message}; facts=${it.facts}"
            },
        )

        val withoutStencilCover = GPUCapabilities(
            implementation = stencilCapabilities.implementation,
            facts = stencilCapabilities.facts.filterNot { it.name == PATH_FILL_STENCIL_COVER },
            knownUnsupportedFacts = stencilCapabilities.knownUnsupportedFacts,
            snapshotId = "${stencilCapabilities.snapshotId}:without-stencil-cover",
            limits = stencilCapabilities.limits,
        )
        val preparedRoutePlan = GPUFramePathApiInventory.plan(
            pathSurface.snapshotOps(),
            target(),
            RenderConfig.DEFAULT,
            withoutStencilCover,
        )
        assertEquals(
            "prepared.path_fill.tessellated",
            preparedRoutePlan.recording.analysis.records.single().routeDecisionLabel,
        )
        assertEquals(
            listOf("route:coverage-mask.sample.path-fill"),
            preparedRoutePlan.recording.routeDiagnostics,
        )

        val runtimeStencilOnlyFacts = org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig(
            pathFillEnabled = false,
        ).buildCapabilities().withCapabilities(FILL_RECT_CAPABILITY, PATH_FILL_STENCIL_COVER)
        assertFalse(runtimeStencilOnlyFacts.facts.any { it.name == "first_slice.path_fill.native" })
        val runtimeStencilOnly = GPUCapabilities(
            implementation = runtimeStencilOnlyFacts.implementation,
            facts = runtimeStencilOnlyFacts.facts,
            knownUnsupportedFacts = runtimeStencilOnlyFacts.knownUnsupportedFacts,
            snapshotId = "${runtimeStencilOnlyFacts.snapshotId}:observed-limits",
            limits = stencilCapabilities.limits,
        )
        val runtimeStencilPlan = GPUFramePathApiInventory.plan(
            pathSurface.snapshotOps(),
            target(),
            RenderConfig.DEFAULT,
            runtimeStencilOnly,
        )
        assertEquals(
            "native.path_fill.stencil_cover",
            runtimeStencilPlan.recording.analysis.records.single().routeDecisionLabel,
        )
        assertIs<GPUCorePrimitivePreparedFrameResult.Recorded>(
            GPUFramePathApiInventory.prepareNativeTaskList(
                runtimeStencilPlan,
                runtimeStencilOnly,
                GPUPixelBounds(0, 0, 32, 32),
            ),
        )
    }

    @Test
    fun `public translated FillPath remains a candidate for the native stencil cover route`() {
        val surface = Surface(32, 32)
        surface.canvas {
            translate(4f, 5f)
            drawPath(triangle(), Paint.fill(ColorARGB.Blue).copy(antiAlias = false))
        }
        val capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER)

        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(),
            target(),
            RenderConfig.DEFAULT,
            capabilities,
        )

        assertEquals(null, plan.preparedRefusal)
        assertEquals("native.path_fill.stencil_cover", plan.recording.analysis.records.single().routeDecisionLabel)
        assertTrue(plan.recording.taskList.tasks.none { it is GPUTask.Refused })
        assertEquals(GPUTransformType.Translate, plan.visualCommands.single().normalized.transform.type)
    }

    @Test
    fun `public translated FillPath exposes transformed device cover and vertices`() {
        val surface = Surface(64, 64)
        surface.canvas {
            translate(4f, 5f)
            drawPath(
                Path().apply {
                    moveTo(8f, 8f)
                    lineTo(56f, 8f)
                    lineTo(8f, 55f)
                    close()
                },
                Paint.fill(ColorARGB.Blue).copy(antiAlias = false),
            )
        }
        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(),
            target(64, 64),
            RenderConfig.DEFAULT,
            capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals(null, plan.preparedRefusal)
        assertEquals("native.path_fill.stencil_cover", plan.recording.analysis.records.single().routeDecisionLabel)
        val semantic = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(
            GPUFramePathApiInventory.gatherCorePrimitiveSemantics(
                plan,
                GPUPixelBounds(0, 0, 64, 64),
            ),
        ).semantics.values.single() as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveCoverageMode.Stencil1x, semantic.coverageMode)
        assertEquals(GPUPixelBounds(12, 13, 60, 60), geometry.coverBounds)
        val deviceVertices = geometry.vertices.chunked(2).map { it[0] to it[1] }
        assertTrue(listOf(12f to 13f, 60f to 13f, 12f to 60f).all { it in deviceVertices })
    }

    @Test
    fun `public uniformly scaled FillPath keeps native stencil cover with device geometry`() {
        val surface = Surface(64, 64)
        surface.canvas {
            scale(1.5f, 1.5f)
            drawPath(
                Path().apply {
                    moveTo(8f, 8f)
                    lineTo(40f, 8f)
                    lineTo(8f, 40f)
                    close()
                },
                Paint.fill(ColorARGB.Blue).copy(antiAlias = false),
            )
        }
        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(),
            target(64, 64),
            RenderConfig.DEFAULT,
            capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals(null, plan.preparedRefusal)
        assertEquals("native.path_fill.stencil_cover", plan.recording.analysis.records.single().routeDecisionLabel)
        val command = assertIs<NormalizedDrawCommand.FillPath>(plan.visualCommands.single().normalized)
        assertEquals(GPUTransformType.Scale, command.transform.type)
        assertEquals("scale", command.pathDescriptor.transformClass)
        val semantic = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(
            GPUFramePathApiInventory.gatherCorePrimitiveSemantics(
                plan,
                GPUPixelBounds(0, 0, 64, 64),
            ),
        ).semantics.values.single() as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveCoverageMode.Stencil1x, semantic.coverageMode)
        assertEquals(GPUPixelBounds(12, 12, 60, 60), geometry.coverBounds)
        val deviceVertices = geometry.vertices.chunked(2).map { it[0] to it[1] }
        assertTrue(listOf(12f to 12f, 60f to 12f, 12f to 60f).all { it in deviceVertices })
    }

    @Test
    fun `public exact quarter turn FillPath keeps native stencil cover with device geometry`() {
        val plan = GPUFramePathApiInventory.plan(
            listOf(
                DisplayOp.DrawPath(
                    path = triangle(),
                    paint = Paint.fill(ColorARGB.Blue).copy(antiAlias = false),
                    transform = Matrix3x3F32.rotation(90f, pivotX = 16f, pivotY = 16f),
                    clip = ClipStack.WideOpen,
                ),
            ),
            target(32, 32),
            RenderConfig.DEFAULT,
            capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals(null, plan.preparedRefusal)
        assertEquals("native.path_fill.stencil_cover", plan.recording.analysis.records.single().routeDecisionLabel)
        val command = assertIs<NormalizedDrawCommand.FillPath>(plan.visualCommands.single().normalized)
        assertEquals("right-angle-rotation", command.pathDescriptor.transformClass)
        val semantic = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(
            GPUFramePathApiInventory.gatherCorePrimitiveSemantics(
                plan,
                GPUPixelBounds(0, 0, 32, 32),
            ),
        ).semantics.values.single() as GPUDrawSemanticPayload.CorePrimitive
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        assertEquals(GPUCorePrimitiveCoverageMode.Stencil1x, semantic.coverageMode)
        assertTrue(listOf(31f to 1f, 31f to 8f, 24f to 4f).all { it in geometry.vertices.chunked(2).map { it[0] to it[1] } })
    }

    @Test
    fun `public exact half turn FillPath keeps native stencil cover`() {
        val plan = GPUFramePathApiInventory.plan(
            listOf(
                DisplayOp.DrawPath(
                    path = triangle(),
                    paint = Paint.fill(ColorARGB.Blue).copy(antiAlias = false),
                    transform = Matrix3x3F32.rotation(180f, pivotX = 16f, pivotY = 16f),
                    clip = ClipStack.WideOpen,
                ),
            ),
            target(32, 32),
            RenderConfig.DEFAULT,
            capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )

        assertEquals(null, plan.preparedRefusal)
        assertEquals("native.path_fill.stencil_cover", plan.recording.analysis.records.single().routeDecisionLabel)
        val command = assertIs<NormalizedDrawCommand.FillPath>(plan.visualCommands.single().normalized)
        assertEquals("right-angle-rotation", command.pathDescriptor.transformClass)
    }

    @Test
    fun `public non right angle affine and perspective FillPaths retain stable route refusals`() {
        val variants = listOf(
            Triple("skew", Matrix3x3F32(kx = 0.25f), "refused.unsupported.transform.class_downgrade"),
            Triple("perspective", Matrix3x3F32(persp0 = 0.25f), "refused.unsupported.transform.perspective"),
        )

        variants.forEach { (label, transform, expectedRoute) ->
            val plan = GPUFramePathApiInventory.plan(
                listOf(
                    DisplayOp.DrawPath(
                        path = triangle(),
                        paint = Paint.fill(ColorARGB.Blue).copy(antiAlias = false),
                        transform = transform,
                        clip = ClipStack.WideOpen,
                    ),
                ),
                target(32, 32),
                RenderConfig.DEFAULT,
                capabilitiesWith(PATH_FILL_STENCIL_COVER),
            )

            assertEquals(expectedRoute, plan.recording.analysis.records.single().routeDecisionLabel, label)
        }
    }

    @Test
    fun `public scale FillPath is limited to solid non inverse winding without AA`() {
        val gradient = Paint(
            shader = Shader.LinearGradient(
                Point2F32(0f, 0f),
                Point2F32(64f, 64f),
                listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
            ),
        ).copy(antiAlias = false)
        val variants = listOf(
            "linear-gradient" to DisplayOp.DrawPath(scaledTriangle(), gradient, Matrix3x3F32(sx = 1.5f, sy = 1.5f), ClipStack.WideOpen),
            "even-odd" to DisplayOp.DrawPath(scaledTriangle(FillType.EVEN_ODD), Paint.fill(ColorARGB.Blue).copy(antiAlias = false), Matrix3x3F32(sx = 1.5f, sy = 1.5f), ClipStack.WideOpen),
            "inverse-winding" to DisplayOp.DrawPath(scaledTriangle(FillType.INVERSE_WINDING), Paint.fill(ColorARGB.Blue).copy(antiAlias = false), Matrix3x3F32(sx = 1.5f, sy = 1.5f), ClipStack.WideOpen),
        )
        val capabilities = capabilitiesWith(PATH_FILL_STENCIL_COVER, "first_slice.linear_gradient.native")

        variants.forEach { (label, operation) ->
            val plan = GPUFramePathApiInventory.plan(
                listOf(operation),
                target(64, 64),
                RenderConfig.DEFAULT,
                capabilities,
            )
            assertEquals(
                "refused.unsupported.transform.class_downgrade",
                plan.recording.analysis.records.single().routeDecisionLabel,
                label,
            )
        }

        listOf(Matrix3x3F32.Identity, Matrix3x3F32.translation(4f, 5f)).forEach { transform ->
            val plan = GPUFramePathApiInventory.plan(
                listOf(DisplayOp.DrawPath(scaledTriangle(), gradient, transform, ClipStack.WideOpen)),
                target(64, 64),
                RenderConfig.DEFAULT,
                capabilities,
            )
            assertEquals(null, plan.preparedRefusal, transform.toString())
            assertEquals("native.path_fill.stencil_cover", plan.recording.analysis.records.single().routeDecisionLabel, transform.toString())
        }
    }

    @Test
    fun `public scaled FillPath retains stable refusals for unsupported transforms and AA`() {
        val variants = listOf(
            "non-uniform" to Matrix3x3F32(sx = 1.5f, sy = 1.25f),
            "scale-translation" to Matrix3x3F32(sx = 1.5f, sy = 1.5f, tx = 2f),
            "negative-scale" to Matrix3x3F32(sx = -1.5f, sy = -1.5f),
            "zero-scale" to Matrix3x3F32(sx = 0f, sy = 0f),
        )
        variants.forEach { (label, transform) ->
            val plan = GPUFramePathApiInventory.plan(
                listOf(DisplayOp.DrawPath(path = scaledTriangle(), paint = Paint.fill(ColorARGB.Blue).copy(antiAlias = false), transform = transform, clip = ClipStack.WideOpen)),
                target(64, 64),
                RenderConfig.DEFAULT,
                capabilitiesWith(PATH_FILL_STENCIL_COVER),
            )
            assertEquals(
                "refused.unsupported.transform.class_downgrade",
                plan.recording.analysis.records.single().routeDecisionLabel,
                label,
            )
        }
        val aaPlan = GPUFramePathApiInventory.plan(
            listOf(DisplayOp.DrawPath(scaledTriangle(), Paint.fill(ColorARGB.Blue).copy(antiAlias = true), Matrix3x3F32(sx = 1.5f, sy = 1.5f), ClipStack.WideOpen)),
            target(64, 64),
            RenderConfig.DEFAULT,
            capabilitiesWith(PATH_FILL_STENCIL_COVER),
        )
        val aaRefusal = assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(
            GPUFramePathApiInventory.prepareNativeTaskList(
                aaPlan,
                capabilitiesWith(PATH_FILL_STENCIL_COVER),
                GPUPixelBounds(0, 0, 64, 64),
            ),
        )
        assertEquals("unsupported.core_primitive.coverage_sample.color_capability", aaRefusal.diagnostic.code.value)
    }

    @Test
    fun `public scaled FillPath AA refuses before native candidate regardless of MSAA capabilities`() {
        listOf(
            "incomplete" to capabilitiesWith(PATH_FILL_STENCIL_COVER),
            "complete" to completeMsaaCapabilities(),
        ).forEach { (label, capabilities) ->
            val plan = GPUFramePathApiInventory.plan(
                listOf(
                    DisplayOp.DrawPath(
                        scaledTriangle(),
                        Paint.fill(ColorARGB.Blue).copy(antiAlias = true),
                        Matrix3x3F32(sx = 1.5f, sy = 1.5f),
                        ClipStack.WideOpen,
                    ),
                ),
                target(64, 64),
                RenderConfig.DEFAULT,
                capabilities,
            )

            assertEquals(
                "refused.unsupported.core_primitive.coverage_sample.color_capability",
                plan.recording.analysis.records.single().routeDecisionLabel,
                "$label route=${plan.recording.analysis.records.singleOrNull()?.routeDecisionLabel}",
            )
            assertTrue(plan.recording.taskList.tasks.none { it is GPUTask.Render }, label)
        }
    }

    @Test
    fun `mapper selects one path clip as stencil only when stencil capability exists`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipPath(
                Path().apply {
                    moveTo(3f, 3f)
                    lineTo(26f, 4f)
                    lineTo(14f, 27f)
                    close()
                },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawRect(RectF32.ofLTRB(0f, 0f, 30f, 30f), Paint.fill(ColorARGB.Red))
        }
        val capabilities = capabilitiesWith(
            FILL_RECT_CAPABILITY,
            PATH_FILL_STENCIL_COVER,
        )

        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(),
            target(),
            RenderConfig.DEFAULT,
            capabilities,
        )
        val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
            plan.visualCommands.single().clipExecutionPlan,
        )

        assertIs<GPUClipExecutionGeometry.Path>(execution.producer.geometry)
        assertEquals(execution.atomicGroup.value, "clip-atomic:${execution.contentKey}")
        assertClipExecutionPropagation(plan, execution)
    }

    @Test
    fun `mapper lowers one hard winding difference path clip to inverted stencil consumer`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipPath(
                Path().apply {
                    moveTo(3f, 3f)
                    lineTo(26f, 4f)
                    lineTo(14f, 27f)
                    close()
                    fillType = FillType.WINDING
                },
                ClipOp.DIFFERENCE,
                antiAlias = false,
            )
            drawRect(RectF32.ofLTRB(0f, 0f, 30f, 30f), Paint.fill(ColorARGB.Red))
        }

        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(),
            target(),
            RenderConfig.DEFAULT,
            capabilitiesWith(FILL_RECT_CAPABILITY, PATH_FILL_STENCIL_COVER),
        )
        val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
            plan.visualCommands.single().clipExecutionPlan,
        )

        assertTrue(execution.consumerInverseFill)
        assertEquals(GPUClipStencilCompare.Equal, execution.consumer.compare)
        assertEquals(GPUClipStencilOperation.IncrementWrap, execution.producer.frontPassOperation)
        assertEquals(GPUClipStencilOperation.DecrementWrap, execution.producer.backPassOperation)
        assertClipExecutionPropagation(plan, execution)
    }

    @Test
    fun `mapper keeps inverse intersect path clip inversion native under translation`() {
        val surface = Surface(32, 32)
        surface.canvas {
            translate(4f, 3f)
            clipPath(
                triangle().apply { fillType = FillType.INVERSE_WINDING },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawRect(RectF32.ofLTRB(0f, 0f, 30f, 30f), Paint.fill(ColorARGB.Red))
        }

        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(), target(), RenderConfig.DEFAULT,
            capabilitiesWith(FILL_RECT_CAPABILITY, PATH_FILL_STENCIL_COVER),
        )
        val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
            plan.visualCommands.single().clipExecutionPlan,
        )

        assertFalse(execution.consumerInverseFill)
        assertEquals(GPUClipStencilCompare.Equal, execution.consumer.compare)
        assertClipExecutionPropagation(plan, execution)
    }

    @Test
    fun `mapper admits bounded even odd difference path clip to the single stencil route`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipPath(triangle().apply { fillType = FillType.EVEN_ODD }, ClipOp.DIFFERENCE, antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 30f, 30f), Paint.fill(ColorARGB.Red))
        }

        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(), target(), RenderConfig.DEFAULT,
            capabilitiesWith(FILL_RECT_CAPABILITY, PATH_FILL_STENCIL_COVER),
        )

        val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
            plan.visualCommands.single().clipExecutionPlan,
        )
        assertEquals(GPUClipStencilOperation.Invert, execution.producer.frontPassOperation)
        assertEquals(GPUClipStencilOperation.Invert, execution.producer.backPassOperation)
        assertEquals(GPUClipStencilCompare.Equal, execution.consumer.compare)
    }

    @Test
    fun `bounded cubic hole selects exact stencil state for every fill rule and operation`() {
        val variants = listOf(
            CubicClipStencilExpectation(
                FillType.WINDING,
                ClipOp.INTERSECT,
                GPUClipStencilOperation.IncrementWrap,
                GPUClipStencilOperation.DecrementWrap,
                GPUClipStencilCompare.NotEqual,
            ),
            CubicClipStencilExpectation(
                FillType.EVEN_ODD,
                ClipOp.INTERSECT,
                GPUClipStencilOperation.Invert,
                GPUClipStencilOperation.Invert,
                GPUClipStencilCompare.NotEqual,
            ),
            CubicClipStencilExpectation(
                FillType.WINDING,
                ClipOp.DIFFERENCE,
                GPUClipStencilOperation.IncrementWrap,
                GPUClipStencilOperation.DecrementWrap,
                GPUClipStencilCompare.Equal,
            ),
            CubicClipStencilExpectation(
                FillType.EVEN_ODD,
                ClipOp.DIFFERENCE,
                GPUClipStencilOperation.Invert,
                GPUClipStencilOperation.Invert,
                GPUClipStencilCompare.Equal,
            ),
        )

        var observedVertexCount: Int? = null
        variants.forEach { expected ->
            val surface = Surface(32, 32)
            surface.canvas {
                clipPath(cubicHolePath(expected.fillType), expected.operation, antiAlias = false)
                drawRect(RectF32.ofLTRB(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.Red).copy(antiAlias = false))
            }
            val plan = GPUFramePathApiInventory.plan(
                surface.snapshotOps(),
                target(),
                RenderConfig(maxPathVertices = 256u),
                capabilitiesWith(FILL_RECT_CAPABILITY, PATH_FILL_STENCIL_COVER),
            )
            val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
                plan.visualCommands.single().clipExecutionPlan,
                "${expected.fillType}/${expected.operation}",
            )
            val geometry = assertIs<GPUClipExecutionGeometry.Path>(execution.producer.geometry)

            val vertexCount = geometry.vertices.size / 2
            assertTrue(vertexCount in 3..256, "vertexCount=$vertexCount")
            assertTrue(vertexCount > 8, "expected cubic flattening to retain curve detail")
            if (observedVertexCount == null) {
                observedVertexCount = vertexCount
            } else {
                assertEquals(observedVertexCount, vertexCount)
            }
            assertEquals(expected.front, execution.producer.frontPassOperation)
            assertEquals(expected.back, execution.producer.backPassOperation)
            assertEquals(expected.consumer, execution.consumer.compare)
        }
    }

    @Test
    fun `mapper admits inverse winding difference path clip to the single stencil route`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipPath(triangle().apply { fillType = FillType.INVERSE_WINDING }, ClipOp.DIFFERENCE, antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 30f, 30f), Paint.fill(ColorARGB.Red))
        }

        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(), target(), RenderConfig.DEFAULT,
            capabilitiesWith(FILL_RECT_CAPABILITY, PATH_FILL_STENCIL_COVER),
        )

        val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
            plan.visualCommands.single().clipExecutionPlan,
        )
        val geometry = assertIs<GPUClipExecutionGeometry.Path>(execution.producer.geometry)
        assertTrue(geometry.inverseFill)
        assertTrue(execution.consumerInverseFill)
        assertEquals(GPUClipStencilCompare.NotEqual, execution.consumer.compare)
        assertEquals(GPUClipStencilOperation.IncrementWrap, execution.producer.frontPassOperation)
        assertEquals(GPUClipStencilOperation.DecrementWrap, execution.producer.backPassOperation)
        assertClipExecutionPropagation(plan, execution)
    }

    @Test
    fun `mapper does not admit antialiased difference path clip to the single stencil route`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipPath(triangle(), ClipOp.DIFFERENCE, antiAlias = true)
            drawRect(RectF32.ofLTRB(0f, 0f, 30f, 30f), Paint.fill(ColorARGB.Red))
        }

        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(), target(), RenderConfig.DEFAULT,
            capabilitiesWith(FILL_RECT_CAPABILITY, PATH_FILL_STENCIL_COVER),
        )

        assertFalse(plan.visualCommands.single().clipExecutionPlan is GPUClipExecutionPlan.StencilCoverage)
    }

    @Test
    fun `mapper does not admit a multiple path clip stack to the single stencil route`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipPath(triangle(), ClipOp.INTERSECT, antiAlias = false)
            clipPath(
                Path().apply {
                    moveTo(6f, 6f)
                    lineTo(28f, 6f)
                    lineTo(18f, 28f)
                    close()
                    fillType = FillType.WINDING
                },
                ClipOp.DIFFERENCE,
                antiAlias = false,
            )
            drawRect(RectF32.ofLTRB(0f, 0f, 30f, 30f), Paint.fill(ColorARGB.Red))
        }

        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(), target(), RenderConfig.DEFAULT,
            capabilitiesWith(FILL_RECT_CAPABILITY, PATH_FILL_STENCIL_COVER),
        )

        assertFalse(plan.visualCommands.single().clipExecutionPlan is GPUClipExecutionPlan.StencilCoverage)
    }

    @Test
    fun `mapper routes an antialiased path clip through a coverage mask even when stencil is available`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipPath(
                Path().apply {
                    moveTo(3f, 3f)
                    lineTo(26f, 4f)
                    lineTo(14f, 27f)
                    close()
                },
                ClipOp.INTERSECT,
                antiAlias = true,
            )
            drawRect(RectF32.ofLTRB(0f, 0f, 30f, 30f), Paint.fill(ColorARGB.Red))
        }

        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(),
            target(),
            RenderConfig.DEFAULT,
            capabilitiesWith(
                FILL_RECT_CAPABILITY,
                PATH_FILL_STENCIL_COVER,
            ),
        )
        val execution = assertIs<GPUClipExecutionPlan.CoverageMask>(
            plan.visualCommands.single().clipExecutionPlan,
        )

        assertEquals(1, execution.producers.size)
        assertTrue(execution.producers.single().antiAlias)
        assertIs<GPUClipExecutionGeometry.Path>(execution.producers.single().geometry)
        assertClipExecutionPropagation(plan, execution)
    }

    @Test
    fun `mapper refuses an antialiased path clip with a stable code when mask support is absent`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipPath(
                Path().apply {
                    moveTo(3f, 3f)
                    lineTo(26f, 4f)
                    lineTo(14f, 27f)
                    close()
                },
                ClipOp.INTERSECT,
                antiAlias = true,
            )
            drawRect(RectF32.ofLTRB(0f, 0f, 30f, 30f), Paint.fill(ColorARGB.Red))
        }
        val capabilities = org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig(
            boundedClipEnabled = false,
        ).buildCapabilities().withCapabilities(
            FILL_RECT_CAPABILITY,
            PATH_FILL_STENCIL_COVER,
        )

        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(),
            target(),
            RenderConfig.DEFAULT,
            capabilities,
        )
        val refusal = assertIs<GPUClipExecutionPlan.Refused>(
            plan.visualCommands.single().clipExecutionPlan,
        )

        assertEquals("unsupported.clip.mask_unavailable", refusal.code)
        assertClipExecutionPropagation(plan, refusal)
    }

    @Test
    fun `mapper preserves multi contour winding orientation in explicit front and back stencil operations`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipPath(
                Path().apply {
                    addRect(RectF32.ofLTRB(2f, 2f, 30f, 30f))
                    reverseAddPath(Path().apply { addRect(RectF32.ofLTRB(9f, 9f, 23f, 23f)) })
                    fillType = FillType.WINDING
                },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawRect(RectF32.ofLTRB(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.Red))
        }

        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(),
            target(),
            RenderConfig.DEFAULT,
            capabilitiesWith(
                FILL_RECT_CAPABILITY,
                PATH_FILL_STENCIL_COVER,
            ),
        )
        val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
            plan.visualCommands.single().clipExecutionPlan,
        )
        val path = assertIs<GPUClipExecutionGeometry.Path>(execution.producer.geometry)

        assertEquals(2, path.contourStarts.size)
        assertEquals(GPUClipStencilOperation.IncrementWrap, execution.producer.frontPassOperation)
        assertEquals(GPUClipStencilOperation.DecrementWrap, execution.producer.backPassOperation)
    }

    @Test
    fun `mapper uses invert on both stencil faces for even odd multi contour paths`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipPath(
                Path().apply {
                    addRect(RectF32.ofLTRB(2f, 2f, 30f, 30f))
                    addRect(RectF32.ofLTRB(9f, 9f, 23f, 23f))
                    fillType = FillType.EVEN_ODD
                },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawRect(RectF32.ofLTRB(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.Red))
        }

        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(),
            target(),
            RenderConfig.DEFAULT,
            capabilitiesWith(
                FILL_RECT_CAPABILITY,
                PATH_FILL_STENCIL_COVER,
            ),
        )
        val execution = assertIs<GPUClipExecutionPlan.StencilCoverage>(
            plan.visualCommands.single().clipExecutionPlan,
        )

        assertEquals(GPUClipStencilOperation.Invert, execution.producer.frontPassOperation)
        assertEquals(GPUClipStencilOperation.Invert, execution.producer.backPassOperation)
    }

    @Test
    fun `mapper retains exact source order for mixed intersect difference mask producers`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipRRect(
                RRectF32.of(RectF32.ofLTRB(2f, 2f, 29f, 29f), radius = 3f),
                ClipOp.INTERSECT,
                antiAlias = true,
            )
            clipRect(RectF32.ofLTRB(12f, 10f, 20f, 22f), ClipOp.DIFFERENCE, antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.Red))
        }

        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(),
            target(),
            RenderConfig.DEFAULT,
            capabilitiesWith(FILL_RECT_CAPABILITY),
        )
        val execution = assertIs<GPUClipExecutionPlan.CoverageMask>(
            plan.visualCommands.single().clipExecutionPlan,
        )

        assertEquals(listOf(0, 1), execution.producers.map { it.sourceOrder })
        assertEquals(
            listOf(GPUClipMaskCombine.Intersect, GPUClipMaskCombine.Difference),
            execution.producers.map { it.combine },
        )
        assertIs<GPUClipExecutionGeometry.RRect>(execution.producers[0].geometry)
        assertIs<GPUClipExecutionGeometry.Rect>(execution.producers[1].geometry)
        assertClipExecutionPropagation(plan, execution)
    }

    @Test
    fun `mapper refuses mask execution when bounded clip capability is absent`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipRRect(
                RRectF32.of(RectF32.ofLTRB(2f, 2f, 29f, 29f), radius = 3f),
                ClipOp.INTERSECT,
                antiAlias = true,
            )
            clipRect(RectF32.ofLTRB(12f, 10f, 20f, 22f), ClipOp.DIFFERENCE, antiAlias = false)
            drawRect(RectF32.ofLTRB(0f, 0f, 32f, 32f), Paint.fill(ColorARGB.Red))
        }
        val capabilities = org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig(
            boundedClipEnabled = false,
        ).buildCapabilities().withCapabilities(FILL_RECT_CAPABILITY)

        val plan = GPUFramePathApiInventory.plan(
            surface.snapshotOps(),
            target(),
            RenderConfig.DEFAULT,
            capabilities,
        )
        val refusal = assertIs<GPUClipExecutionPlan.Refused>(
            plan.visualCommands.single().clipExecutionPlan,
        )

        assertEquals("unsupported.clip.mask_unavailable", refusal.code)
        assertClipExecutionPropagation(plan, refusal)
    }

    @Test
    fun `all 29 blend identities use the canonical shared plan on every core family`() {
        val families = listOf<(BlendMode) -> DisplayOp>(
            { mode -> DisplayOp.DrawColor(ColorARGB.Red, mode, Matrix3x3F32.Identity, org.graphiks.kanvas.canvas.ClipStack.WideOpen) },
            { mode -> DisplayOp.DrawPoint(2f, 2f, paint(mode), Matrix3x3F32.Identity, org.graphiks.kanvas.canvas.ClipStack.WideOpen) },
            { mode -> DisplayOp.DrawPoints(PointMode.LINES, listOf(Point2F32(1f, 1f), Point2F32(5f, 5f)), paint(mode), Matrix3x3F32.Identity, org.graphiks.kanvas.canvas.ClipStack.WideOpen) },
            { mode -> DisplayOp.DrawRect(RectF32.ofLTRB(1f, 1f, 7f, 7f), paint(mode), Matrix3x3F32.Identity, org.graphiks.kanvas.canvas.ClipStack.WideOpen) },
            { mode -> DisplayOp.DrawRRect(RRectF32.of(RectF32.ofLTRB(1f, 1f, 7f, 7f), radius = 1f), paint(mode), Matrix3x3F32.Identity, org.graphiks.kanvas.canvas.ClipStack.WideOpen) },
            { mode -> DisplayOp.DrawDRRect(RRectF32.of(RectF32.ofLTRB(1f, 1f, 8f, 8f), 1f), RRectF32.of(RectF32.ofLTRB(3f, 3f, 6f, 6f), 1f), paint(mode), Matrix3x3F32.Identity, org.graphiks.kanvas.canvas.ClipStack.WideOpen) },
            { mode -> DisplayOp.DrawPath(triangle(), paint(mode), Matrix3x3F32.Identity, org.graphiks.kanvas.canvas.ClipStack.WideOpen) },
        )

        assertEquals(29, BlendMode.entries.size)
        families.forEach { family ->
            BlendMode.entries.forEach { mode ->
                val visual = GPUFramePathApiInventory.plan(
                    listOf(family(mode)),
                    target(16, 16),
                    RenderConfig.DEFAULT,
                ).visualCommands.single()
                assertEquals(mode.toGpuBlendFacts().mode, visual.blendPlan.mode)
                assertFalse(visual.blendPlan is GPUBlendPlan.UnsupportedBlend)
            }
        }
    }

    private fun target(width: Int = 32, height: Int = 32, colorFormat: String = "rgba8unorm") =
        org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts(width, height, colorFormat)

    private fun semanticFor(operation: DisplayOp): GPUDrawSemanticPayload.CorePrimitive {
        val inventory = inventoryFor(operation)
        return gatheredSemantic(inventory) as GPUDrawSemanticPayload.CorePrimitive
    }

    private fun gatheredSemantic(
        inventory: GPUFramePathInventoryPlan,
    ): GPUDrawSemanticPayload {
        val target = target()
        val result = GPUFramePathApiInventory.gatherCorePrimitiveSemantics(
            inventory,
            GPUPixelBounds(0, 0, target.width, target.height),
        )
        val gathered = assertIs<GPUCorePrimitiveSemanticGatherResult.Gathered>(
            result,
            (result as? GPUCorePrimitiveSemanticGatherResult.Refused)?.let { refused ->
                "${refused.code}: ${refused.facts}"
            },
        )
        return gathered.semantics.values.single()
    }

    private fun inventoryFor(operation: DisplayOp): GPUFramePathInventoryPlan =
        GPUFramePathApiInventory.plan(listOf(operation), target(), RenderConfig.DEFAULT)

    private fun pathBudgetInventory(config: RenderConfig): GPUFramePathInventoryPlan =
        GPUFramePathApiInventory.plan(
            listOf(
                DisplayOp.DrawPath(
                    Path().apply {
                        moveTo(1f, 1f)
                        lineTo(9f, 1f)
                        lineTo(1f, 9f)
                        close()
                    },
                    Paint.fill(ColorARGB.Red),
                    Matrix3x3F32.Identity,
                    ClipStack.WideOpen,
                ),
            ),
            target(),
            config,
        )

    private fun gatherRefusal(
        inventory: GPUFramePathInventoryPlan,
    ): GPUCorePrimitiveSemanticGatherResult.Refused = assertIs(
        GPUFramePathApiInventory.gatherCorePrimitiveSemantics(
            inventory,
            GPUPixelBounds(0, 0, 32, 32),
        ),
    )

    private fun assertClipExecutionPropagation(
        plan: GPUFramePathInventoryPlan,
        expected: GPUClipExecutionPlan,
    ) {
        val visual = plan.visualCommands.single()
        assertEquals(expected.canonicalIdentity(), visual.clipExecutionPlan.canonicalIdentity())
        assertEquals(
            expected.canonicalIdentity(),
            visual.normalized.clip.executionPlan?.canonicalIdentity(),
        )
        assertSame(visual.clipExecutionPlan, visual.normalized.clip.executionPlan)
        val packets = plan.recording.taskList.tasks
            .filterIsInstance<GPUTask.Render>()
            .flatMap { it.drawPackets }
        assertEquals(
            1,
            packets.size,
            "tasks=${plan.recording.taskList.tasks.map { it::class.simpleName }} " +
                "diagnostics=${plan.recording.routeDiagnostics}",
        )
        val packet = packets.single()
        assertEquals(expected.canonicalIdentity(), packet.clipExecutionPlan?.canonicalIdentity())
        assertSame(visual.clipExecutionPlan, packet.clipExecutionPlan)
    }

    private fun capabilitiesWith(vararg names: String): GPUCapabilities =
        org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig()
            .buildCapabilities()
            .withCapabilities(*names)

    private fun completeMsaaCapabilities(): GPUCapabilities {
        val base = capabilitiesWith(PATH_FILL_STENCIL_COVER)
        val formatClass = Class.forName("io.ygdrasil.webgpu.GPUTextureFormat")
        fun format(name: String): Any =
            formatClass.enumConstants.first { (it as Enum<*>).name == name }

        val sampleSupportClass = Class.forName(
            "org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureSampleCountSupport",
        )
        fun sampleSupport(resolve: Set<Int>): Any =
            sampleSupportClass
                .getConstructor(Set::class.java, Set::class.java)
                .newInstance(setOf(1, 4), resolve)

        val formatSupport = Class.forName(
            "org.graphiks.kanvas.gpu.renderer.capabilities.GPUTextureFormatSampleSupport",
        ).getConstructor(Map::class.java).newInstance(
            mapOf(
                format("RGBA8Unorm") to sampleSupport(setOf(4)),
                format("Depth24PlusStencil8") to sampleSupport(emptySet()),
            ),
        )
        val copy = GPUCapabilities::class.java.methods.first {
            it.name.startsWith("copy") && it.parameterCount == 10
        }
        return copy.invoke(
            base,
            base.implementation,
            base.facts,
            base.knownUnsupportedFacts,
            "complete-msaa",
            GPULimits(
                maxTextureDimension2D = 8192,
                copyBytesPerRowAlignment = 256,
                minUniformBufferOffsetAlignment = 256,
                maxBufferSize = 1L shl 30,
                maxDynamicUniformBuffersPerPipelineLayout = 1,
            ),
            emptySet<Any>(),
            null,
            formatSupport,
            base.rendererFeatures,
            base.copyAsDrawCapability,
        ) as GPUCapabilities
    }

    private fun GPUCapabilities.withCapabilities(vararg names: String): GPUCapabilities {
        return GPUCapabilities(
            implementation = implementation,
            facts = facts + names.map { name ->
                GPUCapabilityFact(
                    name = name,
                    source = "test",
                    value = "supported",
                    affectsValidity = true,
                    evidenceLabel = "test:$name",
                )
            },
            knownUnsupportedFacts = knownUnsupportedFacts,
            snapshotId = "$snapshotId:${names.joinToString(":")}",
        )
    }

    private companion object {
        const val FILL_RECT_CAPABILITY = "first_slice.fill_rect.native"
    }

    private fun paint(mode: BlendMode) = Paint.fill(ColorARGB.Red).copy(blendMode = mode)

    private fun triangle() = Path().apply {
        moveTo(1f, 1f)
        lineTo(8f, 1f)
        lineTo(4f, 8f)
        close()
    }

    private fun scaledTriangle(fillType: FillType = FillType.WINDING) = Path().apply {
        this.fillType = fillType
        moveTo(8f, 8f)
        lineTo(40f, 8f)
        lineTo(8f, 40f)
        close()
    }

    private fun cubicHolePath(fillType: FillType): Path = Path().apply {
        appendCubicRing(this, radius = 5f)
        appendCubicRing(this, radius = 2f)
        this.fillType = fillType
    }

    private fun appendCubicRing(path: Path, radius: Float) {
        val center = 16f
        val kappa = 0.55228475f * radius
        path.moveTo(center + radius, center)
        path.cubicTo(center + radius, center + kappa, center + kappa, center + radius, center, center + radius)
        path.cubicTo(center - kappa, center + radius, center - radius, center + kappa, center - radius, center)
        path.cubicTo(center - radius, center - kappa, center - kappa, center - radius, center, center - radius)
        path.cubicTo(center + kappa, center - radius, center + radius, center - kappa, center + radius, center)
        path.close()
    }

    private data class CubicClipStencilExpectation(
        val fillType: FillType,
        val operation: ClipOp,
        val front: GPUClipStencilOperation,
        val back: GPUClipStencilOperation,
        val consumer: GPUClipStencilCompare,
    )
}
