package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTarget
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTexture
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRectDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilMode
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendTriangleData
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendVertexColorData
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendVertexPositionUVData
import org.graphiks.kanvas.gpu.renderer.execution.GPUClearColor
import org.graphiks.kanvas.gpu.renderer.execution.GPUDeviceGeneration
import org.graphiks.kanvas.gpu.renderer.execution.GPUSurfaceTarget
import org.graphiks.kanvas.gpu.renderer.execution.GPUSurfaceTargetDescriptor
import org.graphiks.kanvas.gpu.renderer.geometry.ConvexFanExecutor
import org.graphiks.kanvas.gpu.renderer.geometry.PathData
import org.graphiks.kanvas.gpu.renderer.geometry.PathTessellator
import org.graphiks.kanvas.gpu.renderer.geometry.PathVerb
import org.graphiks.kanvas.gpu.renderer.geometry.Point
import org.graphiks.kanvas.gpu.renderer.geometry.StencilCoverExecutor
import org.graphiks.kanvas.gpu.renderer.geometry.isPathConvex
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlan
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlanStep
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePurpose
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.intermediates.dumpLines
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTEntryPoint
import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTWgsl

/**
 * KGPU-M25-001..006: proves the offscreen renderer routes each family through the real delivered
 * executors / module snippets for diagnostic evidence. These assertions cover the wiring only;
 * real textures and atlases landed in M26, and secondary targets + vertex/index buffers landed in
 * M28, and no family is promoted to product support here (ImplementationCandidate).
 */
class M25ExecutorWiringTest {
    @Test
    fun `KGPU-M25-001 bitmap shader routes through the real snippet identity`() {
        // KGPU-M26-002: M26 replaces the procedural wrapper with real texture upload.
        // The M25 snippet+entry+pipeline wiring evidence stays; the deferral line is
        // replaced by real-texture-uploaded evidence.
        val lines = bitmapShaderWiringDiagnostics()
        assertTrue(lines.any { it.contains("snippetSourceHash=fragment:bitmap_shader:v2") }, lines.toString())
        assertTrue(lines.any { it.contains("entryPoint=bitmap_shader_source") }, lines.toString())
        assertTrue(lines.any { it.contains("uniformPacker=UniformPacker.bitmapTextureBytes") }, lines.toString())
        assertTrue(lines.any { it.contains("realTextureUploaded=true") }, lines.toString())
    }

    @Test
    fun `KGPU-M25-002 text routes through TextA8AtlasExecutor and SDFGenerator`() {
        val lines = textAtlasWiringDiagnostics(width = 320, height = 200)
        assertTrue(lines.any { it.startsWith("textA8Atlas:executor accepted=") }, lines.toString())
        assertTrue(lines.any { it.contains("nonclaim:no-gpu-atlas-pages") }, lines.toString())
        assertTrue(lines.any { it.startsWith("textSdf:generator accepted=true") }, lines.toString())
        assertTrue(lines.any { it.contains("nonclaim:no-hybrid-sdf") }, lines.toString())
        assertTrue(lines.any { it.contains("realAtlasUploaded=true") }, lines.toString())
    }

    @Test
    fun `KGPU-M25-003 runtime effect routes through the registered SimpleRT snippet`() {
        val lines = runtimeEffectWiringDiagnostics()
        assertTrue(lines.any { it.contains("wgslSnippetSourceHash=fragment:simple_rt:v1") }, lines.toString())
        assertTrue(lines.any { it.contains("entryPoint=simple_rt_source") }, lines.toString())
        assertTrue(lines.any { it.contains("uniformPacker=UniformPacker.simpleRtBytes") }, lines.toString())
        assertTrue(lines.any { it.contains("realGpuOutput=true") }, lines.toString())
    }

    @Test
    fun `KGPU-M25-003 runtime effect WGSL is imported from the real SimpleRTWgsl module not an inline copy`() {
        val wgsl = composeRuntimeEffectWgsl()
        // The runtime-effect pass must embed the registered SimpleRTWgsl module source verbatim;
        // only the bind group is rebound from @group(1) to @group(0) so the offscreen
        // fullscreen-uniform backend binds the gColor uniform at group 0. No inline copy.
        assertTrue(wgsl.contains(SimpleRTWgsl.replace("@group(1)", "@group(0)")), wgsl)
        assertTrue(wgsl.contains("struct SimpleRTUniform"), wgsl)
        assertTrue(wgsl.contains("uSimpleRT.gColor"), wgsl)
        assertTrue(wgsl.contains("@group(0) @binding(0)"), wgsl)
        assertFalse(wgsl.contains("@group(1)"), wgsl)
        assertTrue(wgsl.contains("$SimpleRTEntryPoint(pos.xy)"), wgsl)
    }

    @Test
    fun `KGPU-M25-004 save layer routes through SaveLayerExecutor`() {
        val scene = GPURendererSceneRegistry.scenes.single { it.sceneId.value == "savelayer-isolated" }
        val drawPlan = prepareRectOnlyDrawPlan(
            sceneId = scene.sceneId.value,
            commands = scene.commands,
            width = scene.dimensions.width,
            height = scene.dimensions.height,
        )
        val plan = SceneIntermediatePlanAdapter().plan(
            sceneId = scene.sceneId.value,
            drawPlan = drawPlan,
            width = scene.dimensions.width,
            height = scene.dimensions.height,
        )
        val lines = plan.dumpLines() + saveLayerWiringDiagnostics(
            fills = drawPlan.fills,
            sceneId = scene.sceneId.value,
            width = scene.dimensions.width,
            height = scene.dimensions.height,
        )
        assertTrue(lines.any { it.startsWith("intermediate.plan id=scene-intermediate:savelayer-isolated") }, lines.toString())
        assertTrue(lines.any { it.contains("intermediate.layer-children scope=layer:translucent-group") }, lines.toString())
        assertTrue(lines.any { it.contains("savelayer:executor targetAllocated=true") }, lines.toString())
        assertTrue(lines.any { it.contains("compositeSnippetSourceHash=fragment:layer_composite:v1") }, lines.toString())
        assertTrue(lines.any { it.contains("secondaryTargetAllocated=true") }, lines.toString())
    }

    @Test
    fun `KGPU-M25-004 save layer refusal aborts before main pass encode`() {
        val scene = GPURendererSceneRegistry.scenes.single { it.sceneId.value == "savelayer-isolated" }
        val drawPlan = prepareRectOnlyDrawPlan(
            sceneId = scene.sceneId.value,
            commands = scene.commands,
            width = scene.dimensions.width,
            height = scene.dimensions.height,
        )
        val refusalPlan = GPUIntermediatePlan(
            planId = "scene-intermediate:${scene.sceneId.value}",
            targetId = "target:${scene.sceneId.value}",
            steps = listOf(
                GPUIntermediatePlanStep.Refuse(
                    scopeLabel = "layer:translucent-group",
                    reasonCode = "unsupported.layer.save_layer_refused",
                ),
            ),
        )
        val renderer = RectOnlyOffscreenRenderer(
            intermediatePlanAdapter = SceneIntermediatePlanAdapter { refusalPlan },
            intermediatePlanExecutor = SceneIntermediatePlanExecutor(),
        )
        val target = RecordingOffscreenTarget(scene.dimensions.width, scene.dimensions.height)

        val failure = assertFailsWith<SceneIntermediateExecutionRefused> {
            renderer.renderToPixels(target, drawPlan)
        }

        assertEquals("unsupported.layer.save_layer_refused", failure.reasonCode)
        assertTrue(
            failure.diagnostics.any {
                it == "intermediate.scene.refused scope=layer:translucent-group reason=unsupported.layer.save_layer_refused stage=save-layer-preparation"
            },
            failure.diagnostics.toString(),
        )
        assertEquals(0, target.mainEncodeCount)
        assertEquals(0, target.offscreenEncodeCount)
    }

    @Test
    fun `KGPU-M25-004 save layer composite order follows intermediate plan steps`() {
        val drawPlan = RectOnlyDrawPlan(
            sceneId = "composite-order-scene",
            clearColor = SceneColor(0f, 0f, 0f, 0f),
            clearCount = 1,
            fills = listOf(
                saveLayerFill(label = "layer-a", paintOrder = 1, groupAlpha = 0.25f),
                saveLayerFill(label = "layer-b", paintOrder = 2, groupAlpha = 0.75f),
            ),
        )
        val execution = SceneIntermediateExecutionResult.Prepared(
            childLabels = emptySet(),
            destinationReadDrawLabels = emptySet(),
            destinationReadBlends = emptyList(),
            layerTextureByTargetLabel = linkedMapOf(
                "layer-b-target" to "texture-b",
                "layer-a-target" to "texture-a",
            ),
            fillLabelByTargetLabel = linkedMapOf(
                "layer-b-target" to "layer-b",
                "layer-a-target" to "layer-a",
            ),
            plannedComposites = listOf(
                compositeStep(sourceLabel = "layer-b-target", tokenLabel = "token-b"),
                compositeStep(sourceLabel = "layer-a-target", tokenLabel = "token-a"),
            ),
            diagnostics = emptyList(),
        )
        val recorder = RecordingRenderRecorder()

        SceneIntermediatePlanExecutor().run {
            recorder.compositeSaveLayers(
                drawPlan = drawPlan,
                execution = execution,
                viewportWidth = 320,
                viewportHeight = 200,
            )
        }

        assertEquals(listOf("texture-b", "texture-a"), recorder.compositeTextureLabels)
    }

    @Test
    fun `KGPU-M25-004 same size saveLayer targets keep distinct planned texture labels`() {
        val drawPlan = RectOnlyDrawPlan(
            sceneId = "same-size-layer-scene",
            clearColor = SceneColor(0f, 0f, 0f, 0f),
            clearCount = 1,
            fills = listOf(
                saveLayerFill(label = "layer-a", paintOrder = 1, groupAlpha = 1f),
                saveLayerFill(label = "layer-b", paintOrder = 2, groupAlpha = 1f),
            ),
        )
        val plan = GPUIntermediatePlan(
            planId = "scene-intermediate:same-size-layer-scene",
            targetId = "target:same-size-layer-scene",
            steps = listOf(
                GPUIntermediatePlanStep.RenderLayerChildren(
                    scopeLabel = "layer:layer-a",
                    target = intermediateDescriptor("intermediate:layer:layer-a"),
                    childrenLabel = "none",
                    tokenLabel = "token-a",
                ),
                GPUIntermediatePlanStep.RenderLayerChildren(
                    scopeLabel = "layer:layer-b",
                    target = intermediateDescriptor("intermediate:layer:layer-b"),
                    childrenLabel = "none",
                    tokenLabel = "token-b",
                ),
                compositeStep(sourceLabel = "intermediate:layer:layer-a", tokenLabel = "token-a"),
                compositeStep(sourceLabel = "intermediate:layer:layer-b", tokenLabel = "token-b"),
            ),
        )
        val target = RecordingOffscreenTarget(width = 64, height = 64)

        val execution = SceneIntermediatePlanExecutor().executeSaveLayerPreparation(
            target = target,
            drawPlan = drawPlan,
            plan = plan,
        ) { fills ->
            val solidDraws = SceneIntermediatePlanExecutor.solidRectDraws(fills)
            if (solidDraws.isNotEmpty()) {
                drawFullscreenPass("solid-rect-wgsl", OFFSCREEN_COLOR_FORMAT, solidDraws)
            }
        }

        val prepared = assertIs<SceneIntermediateExecutionResult.Prepared>(execution)
        assertEquals(
            listOf("intermediate:layer:layer-a", "intermediate:layer:layer-b"),
            target.createdTextureLabels,
        )
        assertEquals(
            listOf("intermediate:layer:layer-a", "intermediate:layer:layer-b"),
            prepared.layerTextureByTargetLabel.values.toList(),
        )
    }

    @Test
    fun `destination read preparation materializes both textures through provider and defers destination readback snapshot`() {
        val drawPlan = RectOnlyDrawPlan(
            sceneId = "destination-copy-scene",
            clearColor = SceneColor(0f, 0f, 0f, 0f),
            clearCount = 1,
            fills = listOf(
                fillRect(label = "background", paintOrder = 1),
                fillRect(label = "foreground", paintOrder = 2),
            ),
        )
        val plan = GPUIntermediatePlan(
            planId = "scene-intermediate:destination-copy-scene",
            targetId = "target:destination-copy-scene",
            steps = listOf(
                GPUIntermediatePlanStep.CreateIntermediate(
                    intermediateDescriptor("dst-copy:foreground").copy(
                        purpose = GPUIntermediatePurpose.DestinationCopy,
                        sourceTargetLabel = "surface:destination-copy-scene",
                        usageLabels = listOf("render_attachment", "texture_binding", "copy_dst"),
                    ),
                ),
                GPUIntermediatePlanStep.CopyDestination(
                    sourceLabel = "surface:destination-copy-scene",
                    destination = intermediateDescriptor("dst-copy:foreground").copy(
                        purpose = GPUIntermediatePurpose.DestinationCopy,
                        sourceTargetLabel = "surface:destination-copy-scene",
                        usageLabels = listOf("render_attachment", "texture_binding", "copy_dst"),
                    ),
                    boundsLabel = "copy:foreground",
                    tokenLabel = "copy-token:foreground",
                    passSplitRequired = true,
                    copyBeforeSample = true,
                ),
                GPUIntermediatePlanStep.BindIntermediate(
                    descriptor = intermediateDescriptor("dst-copy:foreground"),
                    bindingLabel = "dst-read:foreground",
                    layoutHash = "layout:foreground",
                ),
                GPUIntermediatePlanStep.RenderToTarget(
                    commandId = "foreground",
                    targetLabel = "surface:destination-copy-scene",
                    routeLabel = "shader-blend:Screen",
                    orderingToken = "order:foreground",
                ),
            ),
        )
        val target = RecordingOffscreenTarget(width = 64, height = 64)

        val execution = SceneIntermediatePlanExecutor().executeSaveLayerPreparation(
            target = target,
            drawPlan = drawPlan,
            plan = plan,
        ) {
            drawFullscreenPass("solid-rect-wgsl", OFFSCREEN_COLOR_FORMAT, SceneIntermediatePlanExecutor.solidRectDraws(it))
        }

        val prepared = assertIs<SceneIntermediateExecutionResult.Prepared>(execution)
        assertEquals(listOf("dst-copy:foreground", "blend-src:foreground"), target.createdTextureLabels)
        assertEquals(1, target.offscreenEncodeCount, "only the source texture is rendered during preparation")
        assertEquals(0, target.targetReadbackSnapshots, "destination snapshot must happen through the backend snapshot hook later")
        assertEquals(
            2,
            prepared.diagnostics.count {
                it.startsWith("resource-provider.cache lane=intermediate-texture result=create")
            },
            prepared.diagnostics.toString(),
        )
        assertTrue(
            prepared.diagnostics.any {
                it.contains("intermediate.scene.destination-read-readback-snapshot-deferred command=foreground")
            },
            prepared.diagnostics.toString(),
        )
    }

    @Test
    fun `KGPU-M25-004 executor refuses unsupported saveLayer child family instead of solid rect rendering it`() {
        val drawPlan = RectOnlyDrawPlan(
            sceneId = "unsupported-child-scene",
            clearColor = SceneColor(0f, 0f, 0f, 0f),
            clearCount = 1,
            fills = listOf(
                saveLayerFill(label = "layer-a", paintOrder = 1, groupAlpha = 1f),
                saveLayerFill(label = "gradient-child", paintOrder = 2, groupAlpha = 1f).copy(
                    family = "linear-gradient-rect",
                ),
            ),
        )
        val plan = GPUIntermediatePlan(
            planId = "scene-intermediate:unsupported-child-scene",
            targetId = "target:unsupported-child-scene",
            steps = listOf(
                GPUIntermediatePlanStep.RenderLayerChildren(
                    scopeLabel = "layer:layer-a",
                    target = intermediateDescriptor("intermediate:layer:layer-a"),
                    childrenLabel = "gradient-child",
                    tokenLabel = "token-a",
                ),
            ),
        )

        val execution = SceneIntermediatePlanExecutor().executeSaveLayerPreparation(
            target = RecordingOffscreenTarget(width = 64, height = 64),
            drawPlan = drawPlan,
            plan = plan,
        ) {
            drawFullscreenPass("solid-rect-wgsl", OFFSCREEN_COLOR_FORMAT, SceneIntermediatePlanExecutor.solidRectDraws(it))
        }

        val refusal = assertIs<SceneIntermediateExecutionResult.Refused>(execution)
        assertEquals("layer:layer-a", refusal.scopeLabel)
        assertEquals("unsupported.layer.child_family.linear-gradient-rect", refusal.reasonCode)
    }

    @Test
    fun `KGPU-M25-005 path fill routes through PathTessellator StencilCoverExecutor and ConvexFanExecutor`() {
        val tessellator = PathTessellator()

        // Concave star contour -> StencilCoverExecutor (two-pass stencil + cover).
        val starFlat = tessellator.flatten(loopPath(starVertices()))
        val starTri = tessellator.triangulate(starFlat)
        assertTrue(starTri.triangleCount > 0, "PathTessellator produced no triangles for star")
        assertFalse(isPathConvex(starFlat), "star contour must be classified concave")
        val stencilStats = StencilCoverExecutor().execute(starTri)
        assertEquals(1, stencilStats.stencilPassCount, "stencil pass")
        assertEquals(1, stencilStats.coverPassCount, "cover pass")
        assertEquals(2, stencilStats.totalDrawCalls, "stencil-cover totalDraws")
        val stencilDiag = StencilCoverExecutor().stencilStateDiagnostics()
        assertTrue(stencilDiag.any { it.contains("twoPass=true") }, stencilDiag.toString())

        // Convex octagon contour -> ConvexFanExecutor (single-pass fan).
        val octFlat = tessellator.flatten(loopPath(octagonVertices()))
        val octTri = tessellator.triangulate(octFlat)
        assertTrue(octTri.triangleCount > 0, "PathTessellator produced no triangles for octagon")
        assertTrue(isPathConvex(octFlat), "octagon contour must be classified convex")
        val convexStats = ConvexFanExecutor().execute(octTri)
        assertTrue(convexStats.singlePass, "convex fan must be single-pass")
        assertEquals(1, convexStats.drawCallCount, "convex fan draw calls")
        val perf = ConvexFanExecutor().performanceDiagnostics(convexStats, stencilStats)
        assertTrue(perf.any { it.contains("singlePass=true") }, perf.toString())
    }

    @Test
    fun `KGPU-M25-006 vertices route through executor uploader and batcher`() {
        val lines = verticesWiringDiagnostics()
        assertTrue(lines.any { it.startsWith("vertices:executor executed=true") }, lines.toString())
        assertTrue(lines.any { it.startsWith("vertices:uploader uploaded=true") }, lines.toString())
        assertTrue(lines.any { it.startsWith("vertices:batcher inputDraws=2") }, lines.toString())
        assertTrue(lines.any { it.contains("realMesh=true") }, lines.toString())
    }

    @Test
    fun `KGPU phase 4 pass batching diagnostics stay explicit for simple rect route`() {
        val lines = passBatchingWiringDiagnostics()
        assertTrue(lines.any { it.startsWith("passes.batching.wiring-fixture passes.batch-plan ") }, lines.toString())
        assertTrue(lines.any { it.contains("accepted=") }, lines.toString())
        assertTrue(lines.any { it.contains("nonclaim:no-destination-read-batching") }, lines.toString())
        assertTrue(lines.any { it.contains("nonclaim:no-save-layer-batching") }, lines.toString())
        assertTrue(lines.any { it.contains("nonclaim:no-text-complex-batching") }, lines.toString())
    }

    private fun loopPath(vertices: List<Point>): PathData =
        PathData(
            verbs = vertices.map { PathVerb.LineTo(it) } + listOf(PathVerb.Close),
            points = emptyList(),
        )

    private fun starVertices(): List<Point> {
        val centerX = 160f
        val centerY = 100f
        val outerRadius = 80f
        val innerRadius = 35f
        val points = 5
        return (0 until points * 2).map { i ->
            val angle = Math.PI * i / points - Math.PI / 2
            val r = if (i % 2 == 0) outerRadius else innerRadius
            Point((centerX + r * Math.cos(angle)).toFloat(), (centerY + r * Math.sin(angle)).toFloat())
        }
    }

    private fun octagonVertices(): List<Point> {
        val centerX = 160f
        val centerY = 100f
        val radius = 80f
        val sides = 8
        return (0 until sides).map { i ->
            val angle = 2.0 * Math.PI * i / sides - Math.PI / 2
            Point((centerX + radius * Math.cos(angle)).toFloat(), (centerY + radius * Math.sin(angle)).toFloat())
        }
    }

    private fun saveLayerFill(label: String, paintOrder: Int, groupAlpha: Float): RectOnlyFillDraw =
        RectOnlyFillDraw(
            label = label,
            family = "save-layer",
            startColor = SceneColor(1f, 1f, 1f, 1f),
            endColor = SceneColor(1f, 1f, 1f, 1f),
            bottomLeftColor = SceneColor(1f, 1f, 1f, 1f),
            bottomRightColor = SceneColor(1f, 1f, 1f, 1f),
            left = 0f,
            top = 0f,
            right = 64f,
            bottom = 64f,
            radius = 0f,
            paintKind = 0f,
            filterKind = 0f,
            filterStrength = 0f,
            scissorX = 0,
            scissorY = 0,
            scissorWidth = 64,
            scissorHeight = 64,
            paintOrder = paintOrder,
            groupAlpha = groupAlpha,
        )

    private fun fillRect(label: String, paintOrder: Int): RectOnlyFillDraw =
        RectOnlyFillDraw(
            label = label,
            family = "fill-rect",
            startColor = SceneColor(1f, 1f, 1f, 1f),
            endColor = SceneColor(1f, 1f, 1f, 1f),
            bottomLeftColor = SceneColor(1f, 1f, 1f, 1f),
            bottomRightColor = SceneColor(1f, 1f, 1f, 1f),
            left = 0f,
            top = 0f,
            right = 64f,
            bottom = 64f,
            radius = 0f,
            paintKind = 0f,
            filterKind = 0f,
            filterStrength = 0f,
            scissorX = 0,
            scissorY = 0,
            scissorWidth = 64,
            scissorHeight = 64,
            paintOrder = paintOrder,
        )

    private fun compositeStep(sourceLabel: String, tokenLabel: String): GPUIntermediatePlanStep.CompositeIntermediate =
        GPUIntermediatePlanStep.CompositeIntermediate(
            source = intermediateDescriptor(sourceLabel),
            parentTargetLabel = "surface:composite-order-scene",
            blendModeLabel = "srcOver",
            routeLabel = "layer-composite",
            tokenLabel = tokenLabel,
        )

    private fun intermediateDescriptor(label: String): GPUIntermediateTextureDescriptor =
        GPUIntermediateTextureDescriptor(
            label = label,
            purpose = GPUIntermediatePurpose.LayerTarget,
            descriptorHash = "descriptor:$label",
            sourceTargetLabel = "surface:composite-order-scene",
            boundsLabel = "bounds:$label",
            width = 64,
            height = 64,
            formatClass = OFFSCREEN_COLOR_FORMAT,
            usageLabels = listOf("render_attachment", "texture_binding"),
            sampleCount = 1,
            generation = 1L,
            lifetimeClass = "pass",
            ownerScope = "layer:$label",
            byteEstimate = 64L * 64L * 4L,
        )

    private class RecordingOffscreenTarget(width: Int, height: Int) : GPUBackendOffscreenTarget {
        override val target: GPUSurfaceTarget = GPUSurfaceTarget(
            targetId = "recording-target",
            descriptor = GPUSurfaceTargetDescriptor(
                width = width,
                height = height,
                colorFormat = OFFSCREEN_COLOR_FORMAT,
                surfaceBacked = false,
                targetGeneration = 1L,
                usageLabels = setOf("render_attachment", "copy_src", "copy_dst", "texture_binding"),
                readbackAvailable = true,
            ),
            deviceGeneration = GPUDeviceGeneration(1L),
        )

        var mainEncodeCount: Int = 0
            private set
        var offscreenEncodeCount: Int = 0
            private set
        val createdTextureLabels: MutableList<String> = mutableListOf()
        var targetReadbackSnapshots: Int = 0
            private set

        override fun encode(clearColor: GPUClearColor, block: GPUBackendRenderRecorder.() -> Unit) {
            mainEncodeCount += 1
            RecordingRenderRecorder().block()
        }

        override fun readRgba(): ByteArray =
            ByteArray(target.descriptor.width * target.descriptor.height * 4)

        override fun snapshotTargetToOffscreenTexture(textureLabel: String) {
            targetReadbackSnapshots += 1
        }

        override fun createOffscreenTexture(texture: GPUBackendOffscreenTexture): String {
            createdTextureLabels += texture.label
            return texture.label
        }

        override fun encodeOffscreenTexture(
            textureLabel: String,
            clearColor: GPUClearColor?,
            block: GPUBackendRenderRecorder.() -> Unit,
        ) {
            offscreenEncodeCount += 1
            RecordingRenderRecorder().block()
        }

        override fun close() = Unit
    }

    private class RecordingRenderRecorder : GPUBackendRenderRecorder {
        val compositeTextureLabels = mutableListOf<String>()

        override fun drawFullscreenPass(
            wgsl: String,
            colorFormat: String,
            draws: List<GPUBackendRectDraw>,
            blendMode: GPUBlendMode?,
            passBatchKind: org.graphiks.kanvas.gpu.renderer.execution.GPUBackendSimplePassBatchKind?,
        ) = Unit

        override fun drawFullscreenUniformPayloadPass(
            wgsl: String,
            colorFormat: String,
            draws: List<org.graphiks.kanvas.gpu.renderer.execution.GPUBackendUniformPayloadDraw>,
            blendMode: GPUBlendMode?,
            sourceLabel: String,
            passBatchKind: org.graphiks.kanvas.gpu.renderer.execution.GPUBackendSimplePassBatchKind?,
        ) = Unit

        override fun drawFullscreenRawUniformPass(
            wgsl: String,
            colorFormat: String,
            draws: List<GPUBackendRawUniformDraw>,
            blendMode: GPUBlendMode?,
            passBatchKind: org.graphiks.kanvas.gpu.renderer.execution.GPUBackendSimplePassBatchKind?,
        ) = Unit

        override fun drawFullscreenTextureUniformPass(
            wgsl: String,
            colorFormat: String,
            textureRgba: ByteArray,
            textureWidth: Int,
            textureHeight: Int,
            textureFormat: String,
            draws: List<GPUBackendRawUniformDraw>,
            blendMode: GPUBlendMode?,
            stencilMode: GPUBackendStencilMode?,
        ) = Unit

        override fun drawFullscreenStencilPass(
            wgsl: String,
            colorFormat: String,
            stencilMode: GPUBackendStencilMode,
            triangleData: GPUBackendTriangleData?,
            draws: List<GPUBackendRawUniformDraw>,
            blendMode: GPUBlendMode?,
        ) = Unit

        override fun createVertexColorBuffer(data: GPUBackendVertexColorData): String = "vertex-color"

        override fun drawVertexColorIndexed(
            vertexBufferLabel: String,
            indexCount: Int,
            uniformDraw: GPUBackendRawUniformDraw,
            blendMode: GPUBlendMode?,
        ) = Unit

        override fun createVertexPositionUVBuffer(data: GPUBackendVertexPositionUVData): String = "vertex-uv"

        override fun drawVertexPositionUVIndexed(
            vertexBufferLabel: String,
            indexCount: Int,
            uniformDraw: GPUBackendRawUniformDraw,
            textureRgba: ByteArray,
            textureWidth: Int,
            textureHeight: Int,
            textureFormat: String,
            blendMode: GPUBlendMode?,
        ) = Unit

        override fun drawVertexPositionDualUVIndexed(
            vertexBufferLabel: String,
            indexCount: Int,
            uniformDraw: GPUBackendRawUniformDraw,
            texture1Rgba: ByteArray,
            texture1Width: Int,
            texture1Height: Int,
            texture2Rgba: ByteArray,
            texture2Width: Int,
            texture2Height: Int,
            textureFormat: String,
            blendMode: GPUBlendMode?,
        ) = Unit

        override fun createOffscreenTexture(texture: GPUBackendOffscreenTexture): String = "texture"

        override fun encodeOffscreenTexture(
            textureLabel: String,
            clearColor: GPUClearColor?,
            block: GPUBackendRenderRecorder.() -> Unit,
        ) = Unit

        override fun drawCompositePass(
            wgsl: String,
            colorFormat: String,
            textureLabel: String,
            draws: List<GPUBackendRawUniformDraw>,
            blendMode: GPUBlendMode?,
        ) {
            compositeTextureLabels += textureLabel
        }

        override fun drawBlendPass(
            wgsl: String,
            colorFormat: String,
            srcTextureLabel: String,
            dstTextureLabel: String,
            draws: List<GPUBackendRawUniformDraw>,
        ) = Unit

        override fun drawTextAtlasPass(
            atlasRgba: ByteArray,
            atlasWidth: Int,
            atlasHeight: Int,
            atlasFormat: String,
            vertexData: FloatArray,
            indexData: IntArray,
            draws: List<GPUBackendRawUniformDraw>,
            blendMode: GPUBlendMode?,
        ) = Unit

        override fun drawColorGlyphPass(
            atlasRgba: ByteArray,
            atlasWidth: Int,
            atlasHeight: Int,
            atlasFormat: String,
            vertexData: FloatArray,
            indexData: IntArray,
            draws: List<GPUBackendRawUniformDraw>,
            blendMode: GPUBlendMode?,
        ) = Unit
    }
}
