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
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoverageElementKind
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipMaskCombine
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipStencilOperation
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName.BOUNDED_CLIP_NATIVE
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUFirstSliceCapabilityName.PATH_FILL_STENCIL_COVER
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.commands.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.commands.GPUCommandSource
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveFillRule
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectRouteAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveStrokeLoweringProof
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUCorePrimitivePreparedFrameResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_AFFINE_FILL_RECT_CAPABILITY
import org.graphiks.kanvas.gpu.renderer.payloads.CORE_PRIMITIVE_AFFINE_FILL_RECT_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

class GPUFramePathApiInventoryTest {
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
                    Rect.fromLTRB(2f, 3f, 12f, 11f),
                    Paint.fill(Color.RED).copy(antiAlias = false),
                    Matrix33.skew(0.25f, 0.125f),
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
        val semantic = gatheredSemantic(inventory)
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
                    Rect.fromLTRB(2f, 3f, 12f, 11f),
                    Paint.fill(Color.RED).copy(antiAlias = false),
                    Matrix33.skew(0.25f, 0.125f),
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

        val semantic = gatheredSemantic(localBoundsInventory)
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
            Matrix33.rotate(45f) to GPUCorePrimitiveRectRouteAuthority.RectAffineDirectTrianglesV1,
            Matrix33.scale(-1f, 1f) to GPUCorePrimitiveRectRouteAuthority.RectAxisAligned,
            Matrix33.skew(0.25f, 0.125f) to
                GPUCorePrimitiveRectRouteAuthority.RectAffineDirectTrianglesV1,
        )

        cases.forEach { (transform, expectedAuthority) ->
            val inventory = GPUFramePathApiInventory.plan(
                operations = listOf(
                    DisplayOp.DrawRect(
                        Rect.fromLTRB(2f, 3f, 12f, 11f),
                        Paint.fill(Color.RED).copy(antiAlias = false),
                        transform,
                        org.graphiks.kanvas.canvas.ClipStack.WideOpen,
                    ),
                ),
                target = target(),
                config = RenderConfig.DEFAULT,
                capabilities = capabilities,
            )
            val semantic = gatheredSemantic(inventory)
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
                    Rect.fromLTRB(2f, 3f, 12f, 11f),
                    Paint.fill(Color.RED).copy(antiAlias = false),
                    Matrix33.skew(0.25f, 0.125f),
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

        val semantic = gatheredSemantic(forged)

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
                    Rect.fromLTRB(2f, 3f, 12f, 11f),
                    Paint.fill(Color.RED).copy(antiAlias = false),
                    Matrix33.identity(),
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
                RRect(Rect.fromLTRB(2f, 3f, 12f, 11f), radius = 2f),
                Paint.fill(Color.RED),
                Matrix33.identity(),
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
                    Rect.fromLTRB(2f, 3f, 12f, 11f),
                    Paint.fill(Color.RED).copy(antiAlias = false),
                    Matrix33.identity(),
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
            RRect(
                rect = Rect.fromLTRB(2f, 3f, 14f, 13f),
                topLeft = CornerRadii(8f, 2f),
                topRight = CornerRadii(8f, 6f),
                bottomRight = CornerRadii(4f, 6f),
                bottomLeft = CornerRadii(2f, 2f),
            ),
            Paint.fill(Color.RED),
            Matrix33.identity(),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))
        val analysisRecord = inventory.recording.analysis.records.single()
        val semantic = gatheredSemantic(inventory)
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
            RRect(Rect.fromLTRB(2f, 3f, 14f, 13f), radius = 2f),
            Paint.fill(Color.RED),
            Matrix33.identity(),
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
            RRect(Rect.fromLTRB(4f, 5f, 18f, 17f), radius = 3f),
            Paint.fill(Color.RED),
            Matrix33.identity(),
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
            drawColor(Color.RED)
            clear(Color.BLUE)
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
                Paint.stroke(Color.RED, 6f).copy(strokeCap = StrokeCap.ROUND, antiAlias = true),
            )
            drawPoints(
                PointMode.LINES,
                listOf(Point(10f, 10f), Point(30f, 10f)),
                Paint.stroke(Color.BLUE, 4f).copy(strokeCap = StrokeCap.SQUARE, antiAlias = true),
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
            Paint.stroke(Color.RED, 4f).copy(strokeCap = StrokeCap.SQUARE, antiAlias = false),
            Matrix33.identity(),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        )
        val inventory = inventoryFor(operation)
        val command = assertIs<NormalizedDrawCommand.FillPath>(inventory.normalizedCommands.single())
        val semantic = gatheredSemantic(inventory)
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
            listOf(Point(5f, 5f), Point(15f, 10f)),
            Paint.stroke(Color.BLUE, 6f).copy(strokeCap = StrokeCap.BUTT, antiAlias = false),
            Matrix33.identity(),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        )
        val inventory = inventoryFor(operation)
        val command = assertIs<NormalizedDrawCommand.FillPath>(inventory.normalizedCommands.single())
        val semantic = gatheredSemantic(inventory)
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)

        assertEquals(2f, command.tessellatedVertices.filterIndexed { index, _ -> index % 2 == 0 }.min())
        assertEquals(18f, command.tessellatedVertices.filterIndexed { index, _ -> index % 2 == 0 }.max())
        assertEquals(2f, command.tessellatedVertices.filterIndexed { index, _ -> index % 2 == 1 }.min())
        assertEquals(13f, command.tessellatedVertices.filterIndexed { index, _ -> index % 2 == 1 }.max())
        assertEquals(10, geometry.sourceVertexCount)
        assertEquals(2, geometry.sourceContourStarts.size)
    }

    @Test
    fun `drawPoint hairline refuses with a stable geometry diagnostic`() {
        val inventory = inventoryFor(DisplayOp.DrawPoint(
            10f,
            12f,
            Paint.fill(Color.RED).copy(strokeWidth = 0f, strokeCap = StrokeCap.SQUARE),
            Matrix33.identity(),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))

        val refused = gatherRefusal(inventory)

        assertEquals("unsupported.core_primitive.point.hairline_exact_lowering", refused.code)
        assertEquals("drawPoint", refused.facts["source"])
    }

    @Test
    fun `drawPoints round points refuse with a stable geometry diagnostic`() {
        val inventory = inventoryFor(DisplayOp.DrawPoints(
            PointMode.POINTS,
            listOf(Point(5f, 5f), Point(15f, 10f)),
            Paint.stroke(Color.BLUE, 6f).copy(strokeCap = StrokeCap.ROUND),
            Matrix33.identity(),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))

        val refused = gatherRefusal(inventory)

        assertEquals("unsupported.core_primitive.point.round_cap_exact_lowering", refused.code)
        assertEquals("drawPoints.points", refused.facts["source"])
    }

    @Test
    fun `stroked rrect lowers to path and preserves stroke facts`() {
        val paint = Paint.stroke(Color.GREEN, 4f).copy(
            strokeCap = StrokeCap.ROUND,
            strokeJoin = StrokeJoin.ROUND,
            pathEffect = PathEffect.Dash(floatArrayOf(3f, 2f), phase = 1f),
            antiAlias = true,
        )
        val operation = DisplayOp.DrawRRect(
            RRect(Rect.fromLTRB(10f, 10f, 30f, 24f), radius = 4f),
            paint,
            Matrix33.identity(),
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
            Paint.fill(Color.RED).copy(antiAlias = true),
            Matrix33.identity(),
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
            addRect(Rect.fromLTRB(2f, 2f, 28f, 28f))
            reverseAddPath(Path().apply { addRect(Rect.fromLTRB(9f, 9f, 21f, 21f)) })
        }
        val semantic = semanticFor(DisplayOp.DrawPath(
            path,
            Paint.fill(Color.GREEN).copy(antiAlias = false),
            Matrix33.identity(),
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
            addRect(Rect.fromLTRB(6f, 6f, 20f, 20f))
        }
        val semantic = semanticFor(DisplayOp.DrawPath(
            path,
            Paint.fill(Color.BLUE),
            Matrix33.identity(),
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
            clipRect(Rect.fromLTRB(4f, 5f, 24f, 26f), ClipOp.INTERSECT, antiAlias = false)
            drawPath(
                Path().apply {
                    fillType = FillType.INVERSE_WINDING
                    addRect(Rect.fromLTRB(10f, 11f, 15f, 16f))
                },
                Paint.fill(Color.RED),
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
                Paint.fill(Color.RED),
                Matrix33.identity(),
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
    fun `drrect preserves outer and inner contours instead of filling two fans`() {
        val semantic = semanticFor(DisplayOp.DrawDRRect(
            RRect(Rect.fromLTRB(2f, 2f, 30f, 30f), radius = 4f),
            RRect(Rect.fromLTRB(9f, 9f, 23f, 23f), radius = 2f),
            Paint.fill(Color.WHITE),
            Matrix33.identity(),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)

        assertEquals(GPUCorePrimitiveSourceFamily.DRRect, semantic.sourceFamily)
        assertEquals(GPUCorePrimitiveGeometryMode.StencilEdgeFan, geometry.geometryMode)
        assertEquals(2, geometry.sourceContourStarts.size)
        assertEquals(GPUCorePrimitiveFillRule.Winding, geometry.fillRule)
        assertFalse(geometry.inverseFill)
    }

    @Test
    fun `invalid drrect inner outside outer becomes a stable geometry refusal`() {
        val inventory = inventoryFor(DisplayOp.DrawDRRect(
            RRect(Rect.fromLTRB(4f, 4f, 20f, 20f), radius = 2f),
            RRect(Rect.fromLTRB(2f, 8f, 12f, 16f), radius = 1f),
            Paint.fill(Color.WHITE),
            Matrix33.identity(),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))

        val refused = gatherRefusal(inventory)

        assertEquals("unsupported.core_primitive.drrect.inner_outside_outer", refused.code)
        assertEquals("drawDRRect", refused.facts["source"])
    }

    @Test
    fun `direct rrect normalizes oversized radii before semantic gathering`() {
        val semantic = semanticFor(DisplayOp.DrawRRect(
            RRect(
                Rect.fromLTRB(2f, 3f, 12f, 11f),
                topLeft = CornerRadii(8f, 6f),
                topRight = CornerRadii(8f, 6f),
                bottomRight = CornerRadii(8f, 6f),
                bottomLeft = CornerRadii(8f, 6f),
            ),
            Paint.fill(Color.RED),
            Matrix33.identity(),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))
        val geometry = assertIs<GPUCorePrimitiveGeometry.RRect>(semantic.geometry)

        assertEquals(listOf(5f, 3.75f, 5f, 3.75f, 5f, 3.75f, 5f, 3.75f), geometry.radii)
    }

    @Test
    fun `draw rrect preserves raw square corner input until shared normalization`() {
        val inventory = inventoryFor(DisplayOp.DrawRRect(
            RRect(
                Rect.fromLTRB(2f, 3f, 12f, 13f),
                topLeft = CornerRadii(0f, 100f),
                topRight = CornerRadii(2f, 2f),
                bottomRight = CornerRadii(2f, 2f),
                bottomLeft = CornerRadii(2f, 2f),
            ),
            Paint.fill(Color.RED),
            Matrix33.identity(),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))

        val command = assertIs<NormalizedDrawCommand.FillRRect>(inventory.normalizedCommands.single())
        assertEquals(0f, command.rrect.topLeft.x)
        assertEquals(100f, command.rrect.topLeft.y)
        assertEquals(2f, command.rrect.topRight.x)
        assertEquals(2f, command.rrect.bottomRight.x)
        assertEquals(2f, command.rrect.bottomLeft.x)

        val geometry = assertIs<GPUCorePrimitiveGeometry.RRect>(gatheredSemantic(inventory).geometry)
        assertEquals(listOf(0f, 0f, 2f, 2f, 2f, 2f, 2f, 2f), geometry.radii)
    }

    @Test
    fun `draw rrect keeps negative radius raw until the shared typed refusal`() {
        val inventory = inventoryFor(DisplayOp.DrawRRect(
            RRect(
                Rect.fromLTRB(2f, 3f, 12f, 13f),
                topLeft = CornerRadii(-1f, 2f),
                topRight = CornerRadii(2f, 2f),
                bottomRight = CornerRadii(2f, 2f),
                bottomLeft = CornerRadii(2f, 2f),
            ),
            Paint.fill(Color.RED),
            Matrix33.identity(),
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
            RRect(
                Rect.fromLTRB(2f, 3f, 12f, 13f),
                topLeft = CornerRadii(Float.MAX_VALUE, Float.MAX_VALUE),
                topRight = CornerRadii(Float.MAX_VALUE, Float.MAX_VALUE),
                bottomRight = CornerRadii(Float.MAX_VALUE, Float.MAX_VALUE),
                bottomLeft = CornerRadii(Float.MAX_VALUE, Float.MAX_VALUE),
            ),
            Paint.fill(Color.RED),
            Matrix33.identity(),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))

        val command = assertIs<NormalizedDrawCommand.FillRRect>(inventory.normalizedCommands.single())
        assertTrue(command.rrect.topLeft.x.isFinite())
        assertEquals(Float.MAX_VALUE, command.rrect.topLeft.x)

        val geometry = assertIs<GPUCorePrimitiveGeometry.RRect>(gatheredSemantic(inventory).geometry)
        assertEquals(List(8) { 5f }, geometry.radii)
    }

    @Test
    fun `semantic gathering normalizes the same raw asymmetric rrect analyzed from display ops`() {
        val inventory = inventoryFor(DisplayOp.DrawRRect(
            RRect(
                rect = Rect.fromLTRB(2f, 3f, 14f, 13f),
                topLeft = CornerRadii(8f, 2f),
                topRight = CornerRadii(8f, 6f),
                bottomRight = CornerRadii(4f, 6f),
                bottomLeft = CornerRadii(2f, 2f),
            ),
            Paint.fill(Color.RED),
            Matrix33.identity(),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))

        val geometry = assertIs<GPUCorePrimitiveGeometry.RRect>(gatheredSemantic(inventory).geometry)

        assertEquals(listOf(6f, 1.5f, 6f, 4.5f, 3f, 4.5f, 1.5f, 1.5f), geometry.radii)
    }

    @Test
    fun `direct rrect reflection permutes normalized corners into device order`() {
        val semantic = semanticFor(DisplayOp.DrawRRect(
            RRect(
                Rect.fromLTRB(2f, 4f, 12f, 14f),
                topLeft = CornerRadii(1f, 1f),
                topRight = CornerRadii(2f, 1f),
                bottomRight = CornerRadii(3f, 1f),
                bottomLeft = CornerRadii(4f, 1f),
            ),
            Paint.fill(Color.RED),
            Matrix33.makeAll(-1f, 0f, 32f, 0f, -1f, 32f),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))
        val geometry = assertIs<GPUCorePrimitiveGeometry.RRect>(semantic.geometry)

        assertEquals(listOf(3f, 1f, 4f, 1f, 1f, 1f, 2f, 1f), geometry.radii)
    }

    @Test
    fun `direct rrect horizontal reflection permutes normalized corners into device order`() {
        val semantic = semanticFor(DisplayOp.DrawRRect(
            RRect(
                Rect.fromLTRB(2f, 4f, 12f, 14f),
                topLeft = CornerRadii(1f, 1f),
                topRight = CornerRadii(2f, 1f),
                bottomRight = CornerRadii(3f, 1f),
                bottomLeft = CornerRadii(4f, 1f),
            ),
            Paint.fill(Color.RED),
            Matrix33.makeAll(-1f, 0f, 32f, 0f, 1f, 0f),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))
        val geometry = assertIs<GPUCorePrimitiveGeometry.RRect>(semantic.geometry)

        assertEquals(listOf(2f, 1f, 1f, 1f, 4f, 1f, 3f, 1f), geometry.radii)
    }

    @Test
    fun `direct rrect vertical reflection permutes normalized corners into device order`() {
        val semantic = semanticFor(DisplayOp.DrawRRect(
            RRect(
                Rect.fromLTRB(2f, 4f, 12f, 14f),
                topLeft = CornerRadii(1f, 1f),
                topRight = CornerRadii(2f, 1f),
                bottomRight = CornerRadii(3f, 1f),
                bottomLeft = CornerRadii(4f, 1f),
            ),
            Paint.fill(Color.RED),
            Matrix33.makeAll(1f, 0f, 0f, 0f, -1f, 32f),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))
        val geometry = assertIs<GPUCorePrimitiveGeometry.RRect>(semantic.geometry)

        assertEquals(listOf(4f, 1f, 3f, 1f, 2f, 1f, 1f, 1f), geometry.radii)
    }

    @Test
    fun `skewed direct rrect becomes a stable geometry refusal`() {
        val inventory = inventoryFor(DisplayOp.DrawRRect(
            RRect(Rect.fromLTRB(2f, 4f, 12f, 14f), radius = 2f),
            Paint.fill(Color.RED),
            Matrix33.skew(0.25f, 0f),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))

        val refused = gatherRefusal(inventory)

        assertEquals("unsupported.core_primitive.rrect.non_axis_aligned_transform", refused.code)
    }

    @Test
    fun `partially outside rect retains exact geometry with target bounded coverage`() {
        val semantic = semanticFor(DisplayOp.DrawRect(
            Rect.fromLTRB(-4f, 3f, 12f, 15f),
            Paint.fill(Color.RED),
            Matrix33.identity(),
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
            RRect(Rect.fromLTRB(-4f, 3f, 12f, 15f), radius = 3f),
            Paint.fill(Color.RED),
            Matrix33.identity(),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))
        val geometry = assertIs<GPUCorePrimitiveGeometry.RRect>(semantic.geometry)

        assertEquals(-4f, geometry.left)
        assertEquals(12f, geometry.right)
        assertEquals(GPUPixelBounds(0, 0, 32, 32), semantic.scissorBounds)
    }

    @Test
    fun `non solid core material becomes a stable refusal instead of an exception`() {
        val gradient = Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(16f, 0f),
            stops = listOf(GradientStop(0f, Color.RED), GradientStop(1f, Color.BLUE)),
        )
        val inventory = inventoryFor(DisplayOp.DrawRect(
            Rect.fromLTRB(2f, 3f, 18f, 20f),
            Paint(shader = gradient),
            Matrix33.identity(),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))

        val refused = gatherRefusal(inventory)

        assertEquals("unsupported.core_primitive.material.non_solid", refused.code)
        assertEquals("LinearGradient", refused.facts["materialKind"])
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
                Paint.fill(Color.RED),
                Matrix33.identity(),
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
                Paint.fill(Color.RED),
                Matrix33.identity(),
                org.graphiks.kanvas.canvas.ClipStack.WideOpen,
            )),
            target(),
            RenderConfig(maxPathVertices = 512u),
        )

        val refused = gatherRefusal(inventory)

        assertEquals("unsupported.core_primitive.stencil_edge_fan_budget", refused.code)
    }

    @Test
    fun `path AA mode changes semantic identity without changing fill authority`() {
        val path = triangle()
        val aa = semanticFor(DisplayOp.DrawPath(
            path,
            Paint.fill(Color.RED).copy(antiAlias = true),
            Matrix33.identity(),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))
        val binary = semanticFor(DisplayOp.DrawPath(
            path,
            Paint.fill(Color.RED).copy(antiAlias = false),
            Matrix33.identity(),
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
            listOf(Point(4f, 8f), Point(24f, 8f)),
            Paint.stroke(Color.RED, 4f).copy(
                strokeCap = StrokeCap.SQUARE,
                strokeJoin = StrokeJoin.BEVEL,
                strokeMiter = 3f,
                antiAlias = true,
            ),
            Matrix33.identity(),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        ))
        val geometry = assertIs<GPUCorePrimitiveGeometry.TriangulatedPath>(semantic.geometry)
        val stroke = assertNotNull(geometry.strokeStyle)

        assertEquals(GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan, geometry.geometryMode)
        assertEquals(4f, stroke.width)
        assertEquals("square", stroke.cap)
        assertEquals("bevel", stroke.join)
        assertEquals(3f, stroke.miterLimit)
        assertEquals(emptyList(), stroke.dashIntervals)
        assertEquals(0f, stroke.dashPhase)
        assertEquals(GPUCorePrimitiveStrokeLoweringProof.SingleSegmentSquareV1, stroke.loweringProof)
        assertEquals(GPUCorePrimitiveCoverageMode.StencilAA, semantic.coverageMode)
    }

    @Test
    fun `complex dashed round stroke refuses with stable exact lowering code`() {
        val inventory = GPUFramePathApiInventory.plan(
            listOf(DisplayOp.DrawPath(
                triangle(),
                Paint.stroke(Color.RED, 4f).copy(
                    strokeCap = StrokeCap.ROUND,
                    strokeJoin = StrokeJoin.BEVEL,
                    pathEffect = PathEffect.Dash(floatArrayOf(5f, 2f), phase = 1f),
                ),
                Matrix33.identity(),
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
            drawColor(Color.fromRGBA(0.05f, 0.06f, 0.07f, 1f))
            translate(1f, 2f)
            clipRect(Rect.fromLTRB(0f, 0f, 46f, 38f), ClipOp.INTERSECT, antiAlias = false)
            drawPoint(
                2f,
                3f,
                Paint.stroke(Color.WHITE, 1f).copy(strokeCap = StrokeCap.SQUARE, antiAlias = false),
            )
            drawPoints(
                PointMode.LINES,
                listOf(Point(3f, 4f), Point(14f, 9f)),
                Paint.stroke(Color.RED, 2f),
            )
            drawRect(Rect.fromLTRB(4f, 11f, 14f, 20f), Paint.fill(Color.GREEN))
            clipRRect(
                RRect(Rect.fromLTRB(1f, 1f, 43f, 35f), radius = 3f),
                ClipOp.INTERSECT,
                antiAlias = true,
            )
            drawRRect(RRect(Rect.fromLTRB(16f, 11f, 28f, 21f), radius = 2f), Paint.fill(Color.BLUE))
            clipPath(
                Path().apply { addRect(Rect.fromLTRB(2f, 2f, 42f, 34f)) },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawDRRect(
                RRect(Rect.fromLTRB(29f, 10f, 43f, 25f), radius = 3f),
                RRect(Rect.fromLTRB(33f, 14f, 39f, 21f), radius = 1f),
                Paint.fill(Color.WHITE),
            )
            clipRect(Rect.fromLTRB(20f, 16f, 24f, 20f), ClipOp.DIFFERENCE, antiAlias = true)
            drawPath(
                Path().apply {
                    moveTo(5f, 27f)
                    lineTo(22f, 27f)
                    lineTo(13f, 36f)
                    close()
                },
                Paint.fill(Color.RED),
            )
            flushAndSnapshot(Rect.fromLTRB(0f, 0f, 48f, 40f))
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
            drawAnnotation(Rect.EMPTY, GPU_FRAME_PROVENANCE_ANNOTATION_KEY, "harness-background")
            drawRRect(RRect(Rect.fromLTRB(1f, 2f, 5f, 7f), radius = 1f), Paint.fill(Color.RED))
            drawAnnotation(Rect.EMPTY, GPU_FRAME_PROVENANCE_ANNOTATION_KEY, "gm-content")
            drawDRRect(
                RRect(Rect.fromLTRB(8f, 3f, 16f, 13f), radius = 2f),
                RRect(Rect.fromLTRB(10f, 5f, 14f, 11f), radius = 1f),
                Paint.fill(Color.GREEN),
            )
            drawAnnotation(Rect.EMPTY, GPU_FRAME_PROVENANCE_ANNOTATION_KEY, "none")
            drawPath(
                Path().apply {
                    moveTo(18f, 2f)
                    lineTo(29f, 2f)
                    lineTo(24f, 14f)
                    close()
                },
                Paint.fill(Color.BLUE),
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
        assertEquals(0, plan.legacyDump.invocationCount)
    }

    @Test
    fun `unknown provenance annotation is inert and cannot activate a reserved value`() {
        val surface = Surface(16, 16)
        surface.canvas {
            drawAnnotation(Rect.EMPTY, GPU_FRAME_PROVENANCE_ANNOTATION_KEY, "gm-content")
            drawRect(Rect.fromLTRB(1f, 1f, 4f, 4f), Paint.fill(Color.RED))
            drawAnnotation(Rect.EMPTY, GPU_FRAME_PROVENANCE_ANNOTATION_KEY, "GM-CONTENT")
            drawRect(Rect.fromLTRB(5f, 1f, 8f, 4f), Paint.fill(Color.GREEN))
            drawAnnotation(Rect.EMPTY, "unrelated.annotation", "harness-background")
            drawRect(Rect.fromLTRB(9f, 1f, 12f, 4f), Paint.fill(Color.BLUE))
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
            clipRect(Rect.fromLTRB(0f, 0f, 32f, 24f), ClipOp.INTERSECT, antiAlias = false)
            drawColor(Color.RED, BlendMode.SRC_OVER)
            clear(Color.TRANSPARENT)
            drawPoint(1f, 1f, Paint.fill(Color.GREEN).copy(antiAlias = false))
            drawPoints(
                PointMode.LINES,
                listOf(Point(2f, 2f), Point(8f, 8f)),
                Paint.stroke(Color.BLUE, 2f),
            )
            drawRect(Rect.fromLTRB(3f, 4f, 10f, 12f), Paint.fill(Color.RED))
            drawRRect(RRect(Rect.fromLTRB(11f, 4f, 18f, 12f), radius = 2f), Paint.fill(Color.GREEN))
            drawDRRect(
                RRect(Rect.fromLTRB(19f, 3f, 30f, 15f), radius = 2f),
                RRect(Rect.fromLTRB(22f, 6f, 27f, 12f), radius = 1f),
                Paint.fill(Color.BLUE),
            )
            drawPath(
                Path().apply {
                    moveTo(2f, 16f)
                    lineTo(12f, 16f)
                    lineTo(7f, 25f)
                    close()
                },
                Paint.fill(Color.WHITE),
            )
            drawAnnotation(Rect.EMPTY, GPU_FRAME_PROVENANCE_ANNOTATION_KEY, "none")
            flushAndSnapshot(Rect.fromLTRB(0f, 0f, 40f, 30f))
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
            clipRect(Rect.fromLTRB(1f, 1f, 31f, 31f), ClipOp.INTERSECT, antiAlias = true)
            clipRRect(
                RRect(
                    rect = Rect.fromLTRB(4f, 4f, 28f, 28f),
                    topLeft = CornerRadii(2f, 2f),
                    topRight = CornerRadii(2f, 2f),
                    bottomRight = CornerRadii(2f, 2f),
                    bottomLeft = CornerRadii(2f, 2f),
                ),
                ClipOp.DIFFERENCE,
                antiAlias = true,
            )
            clipPath(
                Path().apply { addRect(Rect.fromLTRB(10f, 10f, 20f, 20f)) },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawRRect(RRect(Rect.fromLTRB(0f, 0f, 32f, 32f), radius = 2f), Paint.fill(Color.RED))
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
                Rect.fromLTRB(2f, 3f, 12f, 14f),
                Paint.fill(Color.RED),
                Matrix33.identity(),
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
            clipRect(Rect.fromLTRB(3f, 4f, 24f, 27f), ClipOp.INTERSECT, antiAlias = false)
            drawRect(Rect.fromLTRB(0f, 0f, 30f, 30f), Paint.fill(Color.RED))
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
                RRect(Rect.fromLTRB(3f, 4f, 24f, 27f), radius = 3f),
                ClipOp.INTERSECT,
                antiAlias = true,
            )
            drawRect(Rect.fromLTRB(0f, 0f, 30f, 30f), Paint.fill(Color.RED))
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
    fun `mapper preserves depth one coverage and execution identity while bypassing frame mask budget`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipRRect(
                RRect(Rect.fromLTRB(3f, 4f, 24f, 27f), radius = 3f),
                ClipOp.INTERSECT,
                antiAlias = true,
            )
            drawRect(Rect.fromLTRB(0f, 0f, 30f, 30f), Paint.fill(Color.RED))
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
                drawRect(Rect.fromLTRB(0f, 0f, 30f, 30f), Paint.fill(Color.RED))
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
            clipRect(Rect.fromLTRB(2.25f, 3.5f, 24.75f, 27.25f), ClipOp.INTERSECT, antiAlias = true)
        })
        assertIs<GPUClipExecutionPlan.AnalyticCoverage>(executionFor {
            clipRRect(
                RRect(Rect.fromLTRB(2f, 3f, 25f, 28f), radius = 3f),
                ClipOp.INTERSECT,
                antiAlias = false,
            )
        })
        assertIs<GPUClipExecutionPlan.AnalyticIntersection>(executionFor {
            clipRRect(
                RRect(Rect.fromLTRB(2f, 2f, 29f, 29f), radius = 3f),
                ClipOp.INTERSECT,
                antiAlias = true,
            )
            clipRect(Rect.fromLTRB(12f, 10f, 20f, 22f), ClipOp.INTERSECT, antiAlias = false)
        })
        assertIs<GPUClipExecutionPlan.CoverageMask>(executionFor {
            clipRRect(
                RRect(Rect.fromLTRB(2f, 2f, 29f, 29f), radius = 3f),
                ClipOp.INTERSECT,
                antiAlias = true,
            )
            clipRect(Rect.fromLTRB(12f, 10f, 20f, 22f), ClipOp.DIFFERENCE, antiAlias = false)
        })
        assertIs<GPUClipExecutionPlan.StencilCoverage>(executionFor {
            clipPath(
                Path().apply {
                    addRect(Rect.fromLTRB(3f, 3f, 26f, 27f))
                    fillType = FillType.INVERSE_WINDING
                },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
        })
    }

    @Test
    fun `mapper uses the analytic intersection frame route before mask byte budget`() {
        val surface = Surface(32, 32)
        surface.canvas {
            clipRect(Rect.fromLTRB(2.25f, 2.5f, 29.25f, 29.5f), antiAlias = true)
            clipRRect(RRect(Rect.fromLTRB(4f, 4f, 27f, 27f), radius = 3f), antiAlias = false)
            drawRect(Rect.fromLTRB(0f, 0f, 32f, 32f), Paint.fill(Color.RED))
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
            setMatrix(Matrix33.makeAll(1f, 0f, 0f, 0f, 1f, 0f, 0.1f, 0f, 1f))
            clipRect(Rect.fromLTRB(2f, 3f, 24f, 27f), ClipOp.INTERSECT, antiAlias = true)
            resetMatrix()
            drawRect(Rect.fromLTRB(0f, 0f, 30f, 30f), Paint.fill(Color.RED))
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
                    RRect(Rect.fromLTRB(2f, 3f, 28f, 29f), radius = 3f),
                    ClipOp.INTERSECT,
                    antiAlias = true,
                )
                drawRect(Rect.fromLTRB(0f, 0f, 30f, 30f), Paint.fill(Color.RED))
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
                drawPath(triangle(), Paint.fill(Color.BLUE).copy(antiAlias = false))
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

        val missingProductGateFacts = org.graphiks.kanvas.gpu.renderer.product.GPUProductFlagConfig(
            pathFillEnabled = false,
        ).buildCapabilities().withCapabilities(FILL_RECT_CAPABILITY, PATH_FILL_STENCIL_COVER)
        val missingProductGate = GPUCapabilities(
            implementation = missingProductGateFacts.implementation,
            facts = missingProductGateFacts.facts,
            knownUnsupportedFacts = missingProductGateFacts.knownUnsupportedFacts,
            snapshotId = "${missingProductGateFacts.snapshotId}:observed-limits",
            limits = stencilCapabilities.limits,
        )
        val refusedPathPlan = GPUFramePathApiInventory.plan(
            pathSurface.snapshotOps(),
            target(),
            RenderConfig.DEFAULT,
            missingProductGate,
        )
        val refusal = assertIs<GPUCorePrimitivePreparedFrameResult.Refused>(
            GPUFramePathApiInventory.prepareNativeTaskList(
                refusedPathPlan,
                missingProductGate,
                GPUPixelBounds(0, 0, 32, 32),
            ),
        )
        assertEquals("unsupported.pipeline.capability_missing", refusal.diagnostic.code.value)
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
            drawRect(Rect.fromLTRB(0f, 0f, 30f, 30f), Paint.fill(Color.RED))
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
            drawRect(Rect.fromLTRB(0f, 0f, 30f, 30f), Paint.fill(Color.RED))
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
            drawRect(Rect.fromLTRB(0f, 0f, 30f, 30f), Paint.fill(Color.RED))
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
                    addRect(Rect.fromLTRB(2f, 2f, 30f, 30f))
                    reverseAddPath(Path().apply { addRect(Rect.fromLTRB(9f, 9f, 23f, 23f)) })
                    fillType = FillType.WINDING
                },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawRect(Rect.fromLTRB(0f, 0f, 32f, 32f), Paint.fill(Color.RED))
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
                    addRect(Rect.fromLTRB(2f, 2f, 30f, 30f))
                    addRect(Rect.fromLTRB(9f, 9f, 23f, 23f))
                    fillType = FillType.EVEN_ODD
                },
                ClipOp.INTERSECT,
                antiAlias = false,
            )
            drawRect(Rect.fromLTRB(0f, 0f, 32f, 32f), Paint.fill(Color.RED))
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
                RRect(Rect.fromLTRB(2f, 2f, 29f, 29f), radius = 3f),
                ClipOp.INTERSECT,
                antiAlias = true,
            )
            clipRect(Rect.fromLTRB(12f, 10f, 20f, 22f), ClipOp.DIFFERENCE, antiAlias = false)
            drawRect(Rect.fromLTRB(0f, 0f, 32f, 32f), Paint.fill(Color.RED))
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
                RRect(Rect.fromLTRB(2f, 2f, 29f, 29f), radius = 3f),
                ClipOp.INTERSECT,
                antiAlias = true,
            )
            clipRect(Rect.fromLTRB(12f, 10f, 20f, 22f), ClipOp.DIFFERENCE, antiAlias = false)
            drawRect(Rect.fromLTRB(0f, 0f, 32f, 32f), Paint.fill(Color.RED))
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
            { mode -> DisplayOp.DrawColor(Color.RED, mode, Matrix33.identity(), org.graphiks.kanvas.canvas.ClipStack.WideOpen) },
            { mode -> DisplayOp.DrawPoint(2f, 2f, paint(mode), Matrix33.identity(), org.graphiks.kanvas.canvas.ClipStack.WideOpen) },
            { mode -> DisplayOp.DrawPoints(PointMode.LINES, listOf(Point(1f, 1f), Point(5f, 5f)), paint(mode), Matrix33.identity(), org.graphiks.kanvas.canvas.ClipStack.WideOpen) },
            { mode -> DisplayOp.DrawRect(Rect.fromLTRB(1f, 1f, 7f, 7f), paint(mode), Matrix33.identity(), org.graphiks.kanvas.canvas.ClipStack.WideOpen) },
            { mode -> DisplayOp.DrawRRect(RRect(Rect.fromLTRB(1f, 1f, 7f, 7f), radius = 1f), paint(mode), Matrix33.identity(), org.graphiks.kanvas.canvas.ClipStack.WideOpen) },
            { mode -> DisplayOp.DrawDRRect(RRect(Rect.fromLTRB(1f, 1f, 8f, 8f), 1f), RRect(Rect.fromLTRB(3f, 3f, 6f, 6f), 1f), paint(mode), Matrix33.identity(), org.graphiks.kanvas.canvas.ClipStack.WideOpen) },
            { mode -> DisplayOp.DrawPath(triangle(), paint(mode), Matrix33.identity(), org.graphiks.kanvas.canvas.ClipStack.WideOpen) },
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

    @Test
    fun `slice 12A families are absent from the closed legacy allowlist`() {
        assertEquals(
            setOf(
                LegacyDisplayOpFamily.Images,
                LegacyDisplayOpFamily.Text,
                LegacyDisplayOpFamily.Vertices,
                LegacyDisplayOpFamily.Composites,
            ),
            GPULegacyImmediatePathAdapter.allowedFamilies,
        )

        val adapter = GPULegacyImmediatePathAdapter()
        assertFalse(adapter.accepts(DisplayOp.DrawRect(
            Rect.fromLTRB(0f, 0f, 1f, 1f),
            Paint.fill(Color.RED),
            Matrix33.identity(),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        )))
        assertTrue(adapter.accepts(legacyImageOp()))
        adapter.recordInvocation(legacyImageOp())
        assertEquals(1, adapter.dump().invocationCount)
        assertEquals(mapOf(LegacyDisplayOpFamily.Images to 1), adapter.dump().invocationsByFamily)
    }

    private fun target(width: Int = 32, height: Int = 32) =
        org.graphiks.kanvas.gpu.renderer.commands.GPUTargetFacts(width, height, "rgba8unorm")

    private fun semanticFor(operation: DisplayOp): GPUDrawSemanticPayload.CorePrimitive {
        val inventory = inventoryFor(operation)
        return gatheredSemantic(inventory)
    }

    private fun gatheredSemantic(
        inventory: GPUFramePathInventoryPlan,
    ): GPUDrawSemanticPayload.CorePrimitive {
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

    private fun paint(mode: BlendMode) = Paint.fill(Color.RED).copy(blendMode = mode)

    private fun triangle() = Path().apply {
        moveTo(1f, 1f)
        lineTo(8f, 1f)
        lineTo(4f, 8f)
        close()
    }

    private fun legacyImageOp(): DisplayOp.DrawImage {
        val image = org.graphiks.kanvas.image.Image.fromPixels(
            1,
            1,
            byteArrayOf(0, 0, 0, 0),
            sourceId = "legacy-boundary",
        )
        return DisplayOp.DrawImage(
            image,
            Rect.fromLTRB(0f, 0f, 1f, 1f),
            Rect.fromLTRB(0f, 0f, 1f, 1f),
            null,
            Matrix33.identity(),
            org.graphiks.kanvas.canvas.ClipStack.WideOpen,
        )
    }
}
