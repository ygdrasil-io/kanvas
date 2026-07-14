package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadUploadPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlanner
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendSpecializationRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceAlphaClassification
import org.graphiks.kanvas.gpu.renderer.passes.GPUTargetBlendFacts
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState
import org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadMaterializationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.resources.ValidatingPayloadResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.dumpLines
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheTelemetry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private fun canonicalFixedState(mode: GPUBlendMode): GPUFixedFunctionBlendState {
    val plan = GPUBlendPlanner().plan(
        GPUBlendSpecializationRequest(
            mode = mode,
            coverage = GPUCoverageConsumption.FullOrScissor,
            sourceAlpha = GPUSourceAlphaClassification.Translucent,
            target = GPUTargetBlendFacts("rgba8unorm", true, true),
            samplePlan = GPUSamplePlan.SingleSampleFrame,
        ),
    )
    return (plan as GPUBlendPlan.FixedFunctionBlend).state
}

class GPUBackendRuntimeNativeSmokeTest {
    @AfterEach
    fun disposeRuntime() {
        GPUBackendRuntimeFactory.dispose()
        resetFullscreenUniformSlabTestingHooks()
    }

    @Test
    fun `align copy bytes per row rounds up to 256-byte blocks`() {
        assertEquals(256, alignCopyBytesPerRow(4))
        assertEquals(256, alignCopyBytesPerRow(128))
        assertEquals(512, alignCopyBytesPerRow(300))
    }

    @Test
    fun `strip row padding compacts padded rgba rows`() {
        val padded = byteArrayOf(
            1, 2, 3, 4, 5, 6, 7, 8,
            9, 9, 9, 9, 9, 9, 9, 9,
            10, 11, 12, 13, 14, 15, 16, 17,
            8, 8, 8, 8, 8, 8, 8, 8,
        )

        val stripped = stripRowPadding(
            bytes = padded,
            width = 2,
            height = 2,
            bytesPerPixel = 4,
            paddedBytesPerRow = 16,
        )

        assertContentEquals(
            byteArrayOf(
                1, 2, 3, 4, 5, 6, 7, 8,
                10, 11, 12, 13, 14, 15, 16, 17,
            ),
            stripped,
        )
    }

    @Test
    fun `swizzle bgra to rgba rewrites channel order per pixel`() {
        val bgra = byteArrayOf(
            30, 20, 10, 40,
            70, 60, 50, 80,
        )

        val rgba = swizzleBgraToRgba(bgra)

        assertContentEquals(
            byteArrayOf(
                10, 20, 30, 40,
                50, 60, 70, 80,
            ),
            rgba,
        )
    }

    @Test
    fun `readback cleanup completes and releases pending submission when map fails`() {
        val manager = GPUQueueManager()
        val submission = manager.submit(
            label = "offscreen-pass:frame-1",
            retainedResources = listOf(GPUQueuedResourceRef("readback:frame-1")),
        )
        var unmapCalls = 0

        val failure = assertFailsWith<IllegalStateException> {
            gpuRuntimeWithReadbackCleanup(
                mapAction = { error("map failed") },
                readAction = { byteArrayOf(1, 2, 3, 4) },
                unmapAction = { unmapCalls += 1 },
                completeAction = { completion ->
                    manager.markCompleted(submission.id, completion)
                    manager.releaseCompleted()
                },
            )
        }

        val dump = manager.telemetry.dumpLines().joinToString("\n")
        assertEquals("map failed", failure.message)
        assertEquals(0, unmapCalls)
        assertTrue(dump.contains("submitted=1 completed=1 released=1 pending=0 waits=0 unknownCompletions=0"))
        assertTrue(dump.contains("completion=readback-failed"))
    }

    @Test
    fun `failed readback keeps released transient pending until target close`() {
        val manager = GPUQueueManager()
        val submission = manager.submit(
            label = "offscreen-pass:released-transient",
            retainedResources = listOf(GPUQueuedResourceRef("target:released-transient")),
        )
        var transientDestroyed = false

        assertFailsWith<IllegalStateException> {
            gpuRuntimeWithReadbackCleanup(
                mapAction = { error("map failed") },
                readAction = { byteArrayOf() },
                unmapAction = {},
                completeAction = { completion ->
                    if (gpuRuntimeHasCompletedReadback(completion)) {
                        manager.markCompleted(submission.id, completion)
                        manager.releaseCompleted()
                        transientDestroyed = true
                    }
                },
            )
        }

        assertFalse(transientDestroyed)
        assertEquals(1L, manager.telemetry.pending)
        manager.markCompleted(submission.id, GPU_QUEUE_COMPLETION_TARGET_CLOSE)
        manager.releaseCompleted()
        transientDestroyed = true
        assertTrue(transientDestroyed)
        assertEquals(0L, manager.telemetry.pending)
    }

    @Test
    fun `resolved offscreen texture records sampled label`() {
        val sampledLabels = mutableListOf<String>()

        assertEquals(
            "texture-a",
            gpuRuntimeResolveAndRecordOffscreenTexture(
                label = "texture-a",
                resolve = { label -> label.takeIf { it == "texture-a" } },
                recordUse = sampledLabels::add,
            ),
        )
        assertEquals(listOf("texture-a"), sampledLabels)
    }

    @Test
    fun `window present cleanup completes and releases submitted frame on success`() {
        // Full native window coverage requires platform surface handles; this helper keeps the lifecycle contract covered in CI.
        val manager = GPUQueueManager()
        val submission = manager.submit(
            label = "window-frame:frame-1",
            retainedResources = listOf(GPUQueuedResourceRef("target:window-frame-1")),
        )

        gpuRuntimeCompleteWindowPresentSubmission(
            queueManager = manager,
            submission = submission,
            presentAction = {},
        )

        val dump = manager.telemetry.dumpLines().joinToString("\n")
        assertTrue(dump.contains("submitted=1 completed=1 released=1 pending=0 waits=0 unknownCompletions=0"))
        assertTrue(dump.contains("completion=presented"))
    }

    @Test
    fun `window present cleanup marks failure and rethrows after release`() {
        // Full native window coverage requires platform surface handles; this helper keeps the lifecycle contract covered in CI.
        val manager = GPUQueueManager()
        val submission = manager.submit(
            label = "window-frame:frame-2",
            retainedResources = listOf(GPUQueuedResourceRef("target:window-frame-2")),
        )

        val failure = assertFailsWith<IllegalStateException> {
            gpuRuntimeCompleteWindowPresentSubmission(
                queueManager = manager,
                submission = submission,
                presentAction = { error("present failed") },
            )
        }

        val dump = manager.telemetry.dumpLines().joinToString("\n")
        assertEquals("present failed", failure.message)
        assertTrue(dump.contains("submitted=1 completed=1 released=1 pending=0 waits=0 unknownCompletions=0"))
        assertTrue(dump.contains("completion=$GPU_QUEUE_COMPLETION_PRESENT_FAILED"))
    }

    @Test
    fun `fullscreen uniform slab test hook restores and resets thread local override`() {
        resetFullscreenUniformSlabTestingHooks()
        assertEquals("fullscreen-uniform-pass", currentFullscreenUniformSlabSourceLabelForTesting())

        withFullscreenUniformSlabRefusedForTesting {
            assertEquals("fullscreen-uniform-pass@refused", currentFullscreenUniformSlabSourceLabelForTesting())
            withFullscreenUniformSlabRefusedForTesting {
                assertEquals("fullscreen-uniform-pass@refused", currentFullscreenUniformSlabSourceLabelForTesting())
            }
            assertEquals("fullscreen-uniform-pass@refused", currentFullscreenUniformSlabSourceLabelForTesting())
        }

        assertEquals("fullscreen-uniform-pass", currentFullscreenUniformSlabSourceLabelForTesting())
        resetFullscreenUniformSlabTestingHooks()
        assertEquals("fullscreen-uniform-pass", currentFullscreenUniformSlabSourceLabelForTesting())
    }

    @Test
    fun `window surface helpers derive deterministic device generation and target id`() {
        val binding = GPUNativeSurfaceBinding(
            platform = GPUNativePlatform.AppKitMetalLayer,
            width = 640,
            height = 480,
            pointerLabels = mapOf("layerHandle" to 42L),
        )

        assertEquals(GPUDeviceGenerationID(7L), windowSurfaceDeviceGeneration(windowRuntimeOrdinal = 7L))
        assertEquals(
            "gpu-window-surface-7-appkitmetallayer-640x480",
            windowSurfaceTargetId(windowRuntimeOrdinal = 7L, binding = binding),
        )
    }

    @Test
    fun `runtime retained resource refs include target extras and leases`() {
        val refs = gpuRuntimeRetainedResourceRefs(
            targetRef = GPUQueuedResourceRef("target:window-frame-1"),
            leases = listOf(
                GPUResourceLease(
                    leaseId = "uniform-slab:frame-1",
                    resourceKind = GPUResourceLeaseKind.UniformSlab,
                    deviceGeneration = 11,
                    descriptorHash = "sha256:uniform-slab-frame-1",
                    ownerScope = "frame-1",
                    usageLabels = listOf("copy_dst", "uniform"),
                    releasePolicy = "submission-complete",
                    cacheResult = GPUResourceLeaseCacheResult.Create,
                ),
            ),
            extraRefs = listOf(GPUQueuedResourceRef("readback:frame-1")),
        )

        assertEquals(
            listOf(
                GPUQueuedResourceRef("target:window-frame-1"),
                GPUQueuedResourceRef("readback:frame-1"),
                GPUQueuedResourceRef("lease:uniform-slab:frame-1"),
            ),
            refs,
        )
    }

    @Test
    fun `offscreen target helper derives deterministic unique target id per session and target`() {
        val request = GPUOffscreenTargetRequest(
            width = 320,
            height = 180,
            colorFormat = "rgba8unorm",
        )

        assertEquals(GPUDeviceGenerationID(3L), sessionDeviceGeneration(sessionOrdinal = 3L))
        assertEquals(
            "gpu-offscreen-3-5-320x180-rgba8unorm",
            offscreenTargetId(
                sessionOrdinal = 3L,
                offscreenTargetOrdinal = 5L,
                request = request,
            ),
        )
        assertEquals(
            "gpu-offscreen-3-6-320x180-rgba8unorm",
            offscreenTargetId(
                sessionOrdinal = 3L,
                offscreenTargetOrdinal = 6L,
                request = request.copy(colorFormat = "RGBA8Unorm"),
            ),
        )
    }

    @Test
    fun `backend runtime exposes adapter backed GPU capabilities when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            val capabilities = session.capabilities
                ?: error("GPU backend session should expose capabilities")
            val limits = capabilities.limits
                ?: error("GPU backend session should expose limits")
            val facts = limits.capabilityFacts(evidenceLabel = "runtime")

            assertEquals("GPU", capabilities.implementation.facadeName)
            assertEquals("native", capabilities.implementation.implementationName)
            assertEquals(8192L, limits.maxTextureDimension2D)
            assertEquals(256L, limits.copyBytesPerRowAlignment)
            assertEquals(256L, limits.minUniformBufferOffsetAlignment)
            assertEquals("adapter.limits", limits.source)
            assertEquals(
                listOf("maxTextureDimension2D", "copyBytesPerRowAlignment", "minUniformBufferOffsetAlignment"),
                facts.map { it.name },
            )
            assertTrue(!facts.joinToString("\n").contains("@"))
        }
    }

    @Test
    fun `backend runtime records GPU runtime telemetry when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            val before = session.runtimeTelemetry

            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(
                    width = 4,
                    height = 4,
                    colorFormat = "rgba8unorm",
                ),
            ).use { target ->
                val secondary = target.createOffscreenTexture(
                    GPUBackendOffscreenTexture(
                        label = "telemetry-secondary",
                        width = 4,
                        height = 4,
                        format = "rgba8unorm",
                    ),
                )
                target.encodeOffscreenTexture(
                    textureLabel = secondary,
                    clearColor = GPUClearColor(red = 0.0, green = 0.0, blue = 0.0, alpha = 1.0),
                ) {
                    drawFullscreenPass(
                        wgsl = solidColorFullscreenWgsl(),
                        colorFormat = "rgba8unorm",
                        draws = listOf(
                            GPUBackendRectDraw(
                                rgbaPremul = floatArrayOf(0f, 1f, 0f, 1f),
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = 4,
                                scissorHeight = 4,
                            ),
                        ),
                    )
                }
                target.encode(
                    clearColor = GPUClearColor(red = 0.0, green = 0.0, blue = 0.0, alpha = 1.0),
                ) {
                    drawFullscreenPass(
                        wgsl = solidColorFullscreenWgsl(),
                        colorFormat = "rgba8unorm",
                        draws = listOf(
                            GPUBackendRectDraw(
                                rgbaPremul = floatArrayOf(1f, 0f, 0f, 1f),
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = 4,
                                scissorHeight = 4,
                            ),
                        ),
                    )
                }
                target.readRgba()
            }

            val after = session.runtimeTelemetry
            val dump = session.runtimeTelemetryDumpLines.joinToString("\n")
            val baselineDump = session.phase0BaselineDumpLines.joinToString("\n")
            val submissionDelta = after.submissions - before.submissions
            val commandBufferDelta = after.commandBuffers - before.commandBuffers

            assertTrue(after.renderPasses - before.renderPasses >= 2L)
            assertTrue(after.offscreenPasses - before.offscreenPasses >= 2L)
            assertEquals(0L, after.windowPasses - before.windowPasses)
            assertTrue(after.submissions - before.submissions >= 2L)
            assertTrue(commandBufferDelta >= 2L)
            assertTrue(commandBufferDelta >= submissionDelta)
            assertTrue(after.buffersCreated - before.buffersCreated >= 3L)
            assertTrue(after.texturesCreated - before.texturesCreated >= 4L)
            assertTrue(after.bindGroupsCreated - before.bindGroupsCreated >= 2L)
            assertTrue(after.queueWrites - before.queueWrites >= 2L)
            assertTrue(dump.contains("gpu-runtime.telemetry"))
            assertTrue(dump.contains("commandBuffers="))
            assertTrue(baselineDump.contains("gpu-phase0.baseline"))
            assertTrue(baselineDump.contains("uniformSlabsCreated="))
            assertTrue(!dump.contains("@"))
        }
    }

    @Test
    fun `released offscreen texture is no longer bindable after composite submission`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = 4, height = 4, colorFormat = "rgba8unorm"),
            ).use { target ->
                val source = target.createOffscreenTexture(
                    GPUBackendOffscreenTexture("released-source", 4, 4, "rgba8unorm"),
                )
                target.encodeOffscreenTexture(
                    textureLabel = source,
                    clearColor = GPUClearColor(0.0, 0.0, 0.0, 1.0),
                ) {
                    drawFullscreenPass(
                        wgsl = solidColorFullscreenWgsl(),
                        colorFormat = "rgba8unorm",
                        draws = listOf(
                            GPUBackendRectDraw(floatArrayOf(1f, 0f, 0f, 1f), 0, 0, 4, 4),
                        ),
                    )
                }
                target.encode(GPUClearColor(0.0, 0.0, 0.0, 1.0)) {
                    drawCompositePass(
                        wgsl = singleTextureWgsl(),
                        colorFormat = "rgba8unorm",
                        textureLabel = source,
                        draws = listOf(
                            GPUBackendRawUniformDraw(
                                uniformBytes = solidColorUniformBytes(0f, 0f, 0f, 0f),
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = 4,
                                scissorHeight = 4,
                            ),
                        ),
                        blendMode = canonicalFixedState(GPUBlendMode.SRC),
                    )
                }

                target.releaseOffscreenTexture("unknown-offscreen-texture")
                target.releaseOffscreenTexture(source)

                assertFailsWith<IllegalStateException> {
                    target.encodeOffscreenTexture(source, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {}
                }
                assertContentEquals(
                    byteArrayOf(0xFF.toByte(), 0, 0, 0xFF.toByte()),
                    target.readRgba().copyOfRange(0, 4),
                )
            }
        }
    }

    @Test
    fun `released multi texture sources remain valid through their final sampled submission`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = 4, height = 4, colorFormat = "rgba8unorm"),
            ).use { target ->
                val first = target.createOffscreenTexture(
                    GPUBackendOffscreenTexture("released-multi-first", 4, 4, "rgba8unorm"),
                )
                val second = target.createOffscreenTexture(
                    GPUBackendOffscreenTexture("released-multi-second", 4, 4, "rgba8unorm"),
                )
                listOf(first, second).forEach { source ->
                    target.encodeOffscreenTexture(source, GPUClearColor(0.0, 0.0, 0.0, 1.0)) {
                        drawFullscreenPass(
                            wgsl = solidColorFullscreenWgsl(),
                            colorFormat = "rgba8unorm",
                            draws = listOf(GPUBackendRectDraw(floatArrayOf(1f, 0f, 0f, 1f), 0, 0, 4, 4)),
                        )
                    }
                }
                target.encode(GPUClearColor(0.0, 0.0, 0.0, 1.0)) {
                    drawTwoTexturePass(
                        wgsl = multiTextureWgsl(textureCount = 2),
                        colorFormat = "rgba8unorm",
                        firstTextureLabel = first,
                        secondTextureLabel = second,
                        draws = listOf(
                            GPUBackendRawUniformDraw(
                                uniformBytes = solidColorUniformBytes(0f, 0f, 0f, 0f),
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = 4,
                                scissorHeight = 4,
                            ),
                        ),
                        blendMode = canonicalFixedState(GPUBlendMode.SRC),
                    )
                }

                target.releaseOffscreenTexture(first)
                target.releaseOffscreenTexture(second)

                assertFailsWith<IllegalStateException> {
                    target.encodeOffscreenTexture(first, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {}
                }
                assertContentEquals(
                    byteArrayOf(0xFF.toByte(), 0, 0, 0xFF.toByte()),
                    target.readRgba().copyOfRange(0, 4),
                )
            }
        }
    }

    @Test
    fun `coverage mask resolves four samples and copy stays on GPU`() {
        GPUBackendRuntimeFactory.createOrNull().use { session ->
            assumeTrue(session != null, "GPU backend unavailable in current environment")
            val target = session!!.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = 16, height = 16, colorFormat = "rgba8unorm"),
            )
            target.use {
                val mask = it.createCoverageMask(
                    GPUBackendCoverageMaskRequest("clip", 16, 16, sampleCount = 4),
                )
                val source = it.createOffscreenTexture(
                    GPUBackendOffscreenTexture("source", 16, 16, "rgba8unorm"),
                )
                val snapshot = it.createOffscreenTexture(
                    GPUBackendOffscreenTexture("snapshot", 16, 16, "rgba8unorm"),
                )

                it.encodeCoverageMask(mask, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {}
                it.copyOffscreenTexture(source, snapshot)
                it.releaseCoverageMask(mask)

                assertEquals(4, mask.sampleCount)
            }
            assertEquals(0L, session.runtimeTelemetry.destinationReadbackSnapshots)
            assertTrue(session.runtimeTelemetry.destinationCopies >= 1L)
            assertTrue(session.runtimeTelemetry.msaaResolves >= 1L)
        }
    }

    @Test
    fun `released sampled coverage mask remains physically allocated until final readback`() {
        GPUBackendRuntimeFactory.createOrNull().use { session ->
            assumeTrue(session != null, "GPU backend unavailable in current environment")
            val runtimeSession = session!!
            val destroyedBefore = runtimeSession.runtimeTelemetry.coverageMasksDestroyed

            runtimeSession.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = 4, height = 4, colorFormat = "rgba8unorm"),
            ).use { target ->
                val mask = target.createCoverageMask(
                    GPUBackendCoverageMaskRequest("released-sampled-mask", 4, 4, sampleCount = 1),
                )
                target.encodeCoverageMask(mask, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                    drawFullscreenPass(
                        wgsl = solidColorFullscreenWgsl(),
                        colorFormat = "rgba8unorm",
                        draws = listOf(
                            GPUBackendRectDraw(floatArrayOf(1f, 0f, 0f, 1f), 0, 0, 4, 4),
                        ),
                    )
                }
                target.encode(GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                    drawCompositePass(
                        wgsl = singleTextureWgsl(),
                        colorFormat = "rgba8unorm",
                        textureLabel = mask.sampleLabel,
                        draws = listOf(
                            GPUBackendRawUniformDraw(
                                uniformBytes = solidColorUniformBytes(0f, 0f, 0f, 0f),
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = 4,
                                scissorHeight = 4,
                            ),
                        ),
                        blendMode = canonicalFixedState(GPUBlendMode.SRC),
                    )
                }

                target.releaseCoverageMask(mask)

                assertEquals(destroyedBefore, runtimeSession.runtimeTelemetry.coverageMasksDestroyed)
                assertFailsWith<IllegalStateException> {
                    target.encodeOffscreenTexture(mask.sampleLabel, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {}
                }
                assertContentEquals(
                    byteArrayOf(0xFF.toByte(), 0, 0, 0xFF.toByte()),
                    target.readRgba().copyOfRange(0, 4),
                )
                assertEquals(destroyedBefore + 1L, runtimeSession.runtimeTelemetry.coverageMasksDestroyed)
            }
        }
    }

    @Test
    fun `released coverage mask is physically destroyed when its target closes`() {
        GPUBackendRuntimeFactory.createOrNull().use { session ->
            assumeTrue(session != null, "GPU backend unavailable in current environment")
            val runtimeSession = session!!
            val destroyedBefore = runtimeSession.runtimeTelemetry.coverageMasksDestroyed

            runtimeSession.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = 4, height = 4, colorFormat = "rgba8unorm"),
            ).use { target ->
                val mask = target.createCoverageMask(
                    GPUBackendCoverageMaskRequest("released-mask-target-close", 4, 4, sampleCount = 1),
                )
                target.encodeCoverageMask(mask, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {}
                target.releaseCoverageMask(mask)

                assertEquals(destroyedBefore, runtimeSession.runtimeTelemetry.coverageMasksDestroyed)
                assertFailsWith<IllegalStateException> {
                    target.encodeOffscreenTexture(mask.sampleLabel, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {}
                }
            }

            assertEquals(destroyedBefore + 1L, runtimeSession.runtimeTelemetry.coverageMasksDestroyed)
        }
    }

    @Test
    fun `primary target copy stays on GPU`() {
        GPUBackendRuntimeFactory.createOrNull().use { session ->
            assumeTrue(session != null, "GPU backend unavailable in current environment")
            val runtimeSession = session!!
            runtimeSession.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = 16, height = 16, colorFormat = "rgba8unorm"),
            ).use { target ->
                val destination = target.createOffscreenTexture(
                    GPUBackendOffscreenTexture("primary-copy", 16, 16, "rgba8unorm"),
                )
                val copiesBefore = runtimeSession.runtimeTelemetry.destinationCopies
                val readbacksBefore = runtimeSession.runtimeTelemetry.destinationReadbackSnapshots

                target.copyTargetToOffscreenTexture(destination)

                val telemetry = runtimeSession.runtimeTelemetry
                assertTrue(telemetry.destinationCopies > copiesBefore)
                assertEquals(readbacksBefore, telemetry.destinationReadbackSnapshots)
            }
        }
    }

    @Test
    fun `coverage masks sample actual x1 and x4 stencil cover output before release`() {
        GPUBackendRuntimeFactory.createOrNull().use { session ->
            assumeTrue(session != null, "GPU backend unavailable in current environment")
            val runtimeSession = session!!
            val telemetryBefore = runtimeSession.runtimeTelemetry
            runtimeSession.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = 16, height = 16, colorFormat = "rgba8unorm"),
            ).use { target ->
                val halfPixelEdgeRect = GPUBackendTriangleData(
                    vertices = floatArrayOf(
                        -1f, -1f,
                        0.0625f, -1f,
                        -1f, 1f,
                        0.0625f, 1f,
                    ),
                    indices = intArrayOf(0, 1, 2, 2, 1, 3),
                )
                val coverDraw = GPUBackendRawUniformDraw(
                    uniformBytes = solidColorUniformBytes(0f, 1f, 0f, 1f),
                    scissorX = 0,
                    scissorY = 0,
                    scissorWidth = 16,
                    scissorHeight = 16,
                )
                val msaaMask = target.createCoverageMask(
                    GPUBackendCoverageMaskRequest("stencil-x4", 16, 16, sampleCount = 4),
                )

                target.encodeCoverageMask(msaaMask, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                    drawFullscreenStencilPass(
                        wgsl = stencilWriteWgsl(),
                        colorFormat = "rgba8unorm",
                        stencilMode = GPUBackendStencilMode.Write,
                        triangleData = halfPixelEdgeRect,
                        draws = emptyList(),
                    )
                    drawFullscreenStencilPass(
                        wgsl = stencilTestWgsl(),
                        colorFormat = "rgba8unorm",
                        stencilMode = GPUBackendStencilMode.Test,
                        triangleData = null,
                        draws = listOf(coverDraw),
                    )
                }
                target.encode(GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                    drawCompositePass(
                        wgsl = singleTextureWgsl(),
                        colorFormat = "rgba8unorm",
                        textureLabel = msaaMask.sampleLabel,
                        draws = listOf(
                            GPUBackendRawUniformDraw(
                                uniformBytes = solidColorUniformBytes(0f, 0f, 0f, 0f),
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = 16,
                                scissorHeight = 16,
                            ),
                        ),
                        blendMode = canonicalFixedState(GPUBlendMode.SRC),
                    )
                }
                val x4Pixels = target.readRgba()
                assertContentEquals(
                    byteArrayOf(0, 0xff.toByte(), 0, 0xff.toByte()),
                    pixelAt(x4Pixels, width = 16, x = 4, y = 8),
                )
                assertContentEquals(
                    byteArrayOf(0, 0, 0, 0),
                    pixelAt(x4Pixels, width = 16, x = 12, y = 8),
                )
                val x4Edge = pixelAt(x4Pixels, width = 16, x = 8, y = 8)
                val x4EdgeAlpha = x4Edge[3].toInt() and 0xff
                assertTrue(x4EdgeAlpha in 1..254, "expected partially covered x4 edge, actual=${x4Edge.toList()}")
                assertTrue(kotlin.math.abs(x4EdgeAlpha - 128) <= 1, "expected half-covered x4 edge, actual=${x4Edge.toList()}")
                assertEquals(0, x4Edge[0].toInt() and 0xff)
                assertTrue(kotlin.math.abs((x4Edge[1].toInt() and 0xff) - x4EdgeAlpha) <= 1)
                assertEquals(0, x4Edge[2].toInt() and 0xff)
                target.releaseCoverageMask(msaaMask)
                assertFailsWith<IllegalStateException> {
                    target.encodeOffscreenTexture(msaaMask.sampleLabel, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {}
                }

                val singleSampleMask = target.createCoverageMask(
                    GPUBackendCoverageMaskRequest("stencil-x1", 16, 16, sampleCount = 1),
                )
                target.encodeCoverageMask(singleSampleMask, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                    drawFullscreenStencilPass(
                        wgsl = stencilWriteWgsl(),
                        colorFormat = "rgba8unorm",
                        stencilMode = GPUBackendStencilMode.Write,
                        triangleData = halfPixelEdgeRect,
                        draws = emptyList(),
                    )
                    drawFullscreenStencilPass(
                        wgsl = stencilTestWgsl(),
                        colorFormat = "rgba8unorm",
                        stencilMode = GPUBackendStencilMode.Test,
                        triangleData = null,
                        draws = listOf(coverDraw),
                    )
                }
                target.encode(GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                    drawCompositePass(
                        wgsl = singleTextureWgsl(),
                        colorFormat = "rgba8unorm",
                        textureLabel = singleSampleMask.sampleLabel,
                        draws = listOf(
                            GPUBackendRawUniformDraw(
                                uniformBytes = solidColorUniformBytes(0f, 0f, 0f, 0f),
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = 16,
                                scissorHeight = 16,
                            ),
                        ),
                        blendMode = canonicalFixedState(GPUBlendMode.SRC),
                    )
                }
                val x1Pixels = target.readRgba()
                assertContentEquals(
                    byteArrayOf(0, 0xff.toByte(), 0, 0xff.toByte()),
                    pixelAt(x1Pixels, width = 16, x = 4, y = 8),
                )
                assertContentEquals(
                    byteArrayOf(0, 0, 0, 0),
                    pixelAt(x1Pixels, width = 16, x = 12, y = 8),
                )
                target.releaseCoverageMask(singleSampleMask)
                val unknownMask = GPUBackendCoverageMask(
                    renderLabel = "unknown-mask:render",
                    sampleLabel = "unknown-mask:sample",
                    width = 16,
                    height = 16,
                    sampleCount = 1,
                )
                target.releaseCoverageMask(unknownMask)
                target.releaseCoverageMask(unknownMask)
            }
            val telemetryAfter = runtimeSession.runtimeTelemetry
            assertEquals(telemetryBefore.msaaTargets + 1L, telemetryAfter.msaaTargets)
            assertEquals(telemetryBefore.msaaResolves + 1L, telemetryAfter.msaaResolves)
        }
    }

    @Test
    fun `coverage mask runs textured vertex and dual UV pipelines at four samples`() {
        GPUBackendRuntimeFactory.createOrNull().use { session ->
            assumeTrue(session != null, "GPU backend unavailable in current environment")
            session!!.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = 4, height = 4, colorFormat = "rgba8unorm"),
            ).use { target ->
                val mask = target.createCoverageMask(
                    GPUBackendCoverageMaskRequest("vertex-msaa", 4, 4, sampleCount = 4),
                )
                val uniform = GPUBackendRawUniformDraw(
                    uniformBytes = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).apply {
                        putFloat(1f)
                        putInt(0)
                    }.array(),
                    scissorX = 0,
                    scissorY = 0,
                    scissorWidth = 4,
                    scissorHeight = 4,
                )
                val rgba = byteArrayOf(
                    0xff.toByte(), 0, 0, 0xff.toByte(),
                    0, 0xff.toByte(), 0, 0xff.toByte(),
                    0, 0, 0xff.toByte(), 0xff.toByte(),
                    0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0xff.toByte(),
                )

                target.encodeCoverageMask(mask, GPUClearColor(0.0, 0.0, 0.0, 0.0)) {
                    val texturedVertexBuffer = createVertexPositionUVBuffer(
                        GPUBackendVertexPositionUVData(
                            vertexData = floatArrayOf(
                                -1f, -1f, 0f, 1f,
                                1f, -1f, 1f, 1f,
                                -1f, 1f, 0f, 0f,
                                1f, 1f, 1f, 0f,
                            ),
                            indices = intArrayOf(0, 1, 2, 2, 1, 3),
                        ),
                    )
                    drawVertexPositionUVIndexed(
                        vertexBufferLabel = texturedVertexBuffer,
                        indexCount = 6,
                        uniformDraw = uniform,
                        textureRgba = rgba,
                        textureWidth = 2,
                        textureHeight = 2,
                        textureFormat = "rgba8unorm",
                    )

                    val dualVertexBuffer = createVertexPositionUVBuffer(
                        GPUBackendVertexPositionUVData(
                            vertexData = floatArrayOf(
                                -1f, -1f, 0f, 1f, 0f, 1f,
                                1f, -1f, 1f, 1f, 1f, 1f,
                                -1f, 1f, 0f, 0f, 0f, 0f,
                                1f, 1f, 1f, 0f, 1f, 0f,
                            ),
                            indices = intArrayOf(0, 1, 2, 2, 1, 3),
                        ),
                    )
                    drawVertexPositionDualUVIndexed(
                        vertexBufferLabel = dualVertexBuffer,
                        indexCount = 6,
                        uniformDraw = uniform,
                        texture1Rgba = rgba,
                        texture1Width = 2,
                        texture1Height = 2,
                        texture2Rgba = rgba,
                        texture2Width = 2,
                        texture2Height = 2,
                        textureFormat = "rgba8unorm",
                    )
                }
                target.releaseCoverageMask(mask)
            }
        }
    }

    @Test
    fun `multi texture passes cache layout topology without texture labels`() {
        GPUBackendRuntimeFactory.createOrNull().use { session ->
            assumeTrue(session != null, "GPU backend unavailable in current environment")
            session!!.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = 4, height = 4, colorFormat = "rgba8unorm"),
            ).use { target ->
                val first = target.createOffscreenTexture(GPUBackendOffscreenTexture("first", 4, 4, "rgba8unorm"))
                val second = target.createOffscreenTexture(GPUBackendOffscreenTexture("second", 4, 4, "rgba8unorm"))
                val third = target.createOffscreenTexture(GPUBackendOffscreenTexture("third", 4, 4, "rgba8unorm"))
                val draw = GPUBackendRawUniformDraw(
                    uniformBytes = solidColorUniformBytes(0f, 0f, 0f, 0f),
                    scissorX = 0,
                    scissorY = 0,
                    scissorWidth = 4,
                    scissorHeight = 4,
                )

                target.encode(GPUClearColor(0.0, 0.0, 0.0, 1.0)) {
                    drawTwoTexturePass(
                        wgsl = multiTextureWgsl(textureCount = 2),
                        colorFormat = "rgba8unorm",
                        firstTextureLabel = first,
                        secondTextureLabel = second,
                        draws = listOf(draw),
                        blendMode = canonicalFixedState(GPUBlendMode.SRC),
                    )
                }
                target.encode(GPUClearColor(0.0, 0.0, 0.0, 1.0)) {
                    drawThreeTexturePass(
                        wgsl = multiTextureWgsl(textureCount = 3),
                        colorFormat = "rgba8unorm",
                        firstTextureLabel = first,
                        secondTextureLabel = second,
                        thirdTextureLabel = third,
                        draws = listOf(draw),
                        blendMode = canonicalFixedState(GPUBlendMode.SRC),
                    )
                }
            }

            val preimages = session.executionCacheDumpLines.joinToString("\n")
            assertTrue(preimages.contains("topology=2-texture-sampler-pairs"), preimages)
            assertTrue(preimages.contains("topology=3-texture-sampler-pairs"), preimages)
            assertTrue(!preimages.contains("offscreenTex:first"), preimages)
            assertTrue(!preimages.contains("offscreenTex:second"), preimages)
            assertTrue(!preimages.contains("offscreenTex:third"), preimages)
        }
    }

    @Test
    fun `runtime telemetry dump includes pass batch counters`() {
        val telemetry = GPUBackendRuntimeTelemetry(
            renderPasses = 1,
            submissions = 1,
            commandBuffers = 1,
            passBatchPlans = 1,
            passBatchesAccepted = 1,
            passBatchCuts = 0,
            passBatchPackets = 3,
        )

        val dump = telemetry.dumpLines().joinToString("\n")

        assertTrue(dump.contains("passBatchPlans=1"), dump)
        assertTrue(dump.contains("passBatchesAccepted=1"), dump)
        assertTrue(dump.contains("passBatchCuts=0"), dump)
        assertTrue(dump.contains("passBatchPackets=3"), dump)
    }

    @Test
    fun `backend runtime offscreen encode and read rgba when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(
                    width = 4,
                    height = 4,
                    colorFormat = "rgba8unorm",
                ),
            ).use { target ->
                target.encode(
                    clearColor = GPUClearColor(red = 0.0, green = 0.0, blue = 0.0, alpha = 1.0),
                ) {
                    drawFullscreenPass(
                        wgsl = """
                            struct Uniforms {
                                color: vec4f,
                            };

                            @group(0) @binding(0) var<uniform> uniforms: Uniforms;

                            @vertex
                            fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
                                let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
                                let y = f32(idx & 2u) * 2.0 - 1.0;
                                return vec4f(x, y, 0.0, 1.0);
                            }

                            @fragment
                            fn fs_main() -> @location(0) vec4f {
                                return uniforms.color;
                            }
                        """.trimIndent(),
                        colorFormat = "rgba8unorm",
                        draws = listOf(
                            GPUBackendRectDraw(
                                rgbaPremul = floatArrayOf(1f, 0f, 0f, 1f),
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = 4,
                                scissorHeight = 4,
                            ),
                        ),
                    )
                }

                val rgba = target.readRgba()

                assertEquals(4 * 4 * 4, rgba.size)
                assertContentEquals(byteArrayOf(0xFF.toByte(), 0, 0, 0xFF.toByte()), rgba.copyOfRange(0, 4))
                assertTrue(rgba.asList().chunked(4).all { pixel -> pixel[3] == 0xFF.toByte() })
            }
        }
    }

    @Test
    fun `backend runtime records pass batch plan for fullscreen rect draws when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = 4, height = 4, colorFormat = "rgba8unorm"),
            ).use { target ->
                target.encode(GPUClearColor(0.0, 0.0, 0.0, 1.0)) {
                    drawFullscreenPass(
                        wgsl = solidColorFullscreenWgsl(),
                        colorFormat = "rgba8unorm",
                        draws = listOf(
                            GPUBackendRectDraw(floatArrayOf(1f, 0f, 0f, 1f), 0, 0, 2, 4),
                            GPUBackendRectDraw(floatArrayOf(0f, 1f, 0f, 1f), 2, 0, 2, 4),
                        ),
                        passBatchKind = GPUBackendSimplePassBatchKind.SolidFill,
                    )
                }
                target.readRgba()

                val dump = session.runtimeTelemetryDumpLines.joinToString("\n")
                assertTrue(dump.contains("passBatchPlans=1"), dump)
                assertTrue(dump.contains("passBatchesAccepted=1"), dump)
                assertTrue(dump.contains("passes.batch-plan stream=fullscreen-uniform-pass"), dump)
                assertTrue(dump.contains("passes.batch id=batch-1 kind=solid-fill"), dump)
                assertTrue(!dump.contains("@"))
                assertTrue(!dump.contains("0x"))
                assertTrue(!dump.contains(forbiddenImplementationTokenUpperForAudit()))
                assertTrue(!dump.contains(forbiddenImplementationTokenLowerForAudit()))
            }
        }
    }

    @Test
    fun `backend runtime does not record pass batch plan for unmarked generic fullscreen pass draws when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            val before = session.runtimeTelemetry

            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = 4, height = 4, colorFormat = "rgba8unorm"),
            ).use { target ->
                target.encode(GPUClearColor(0.0, 0.0, 0.0, 1.0)) {
                    drawFullscreenPass(
                        wgsl = nonSimpleFullscreenWgsl(),
                        colorFormat = "rgba8unorm",
                        draws = listOf(
                            GPUBackendRectDraw(floatArrayOf(1f, 0f, 0f, 1f), 0, 0, 4, 4),
                            GPUBackendRectDraw(floatArrayOf(0f, 1f, 0f, 1f), 0, 0, 2, 4),
                        ),
                    )
                }

                val rgba = target.readRgba()
                val after = session.runtimeTelemetry
                val dump = session.runtimeTelemetryDumpLines.joinToString("\n")

                assertEquals(4 * 4 * 4, rgba.size)
                assertEquals(0L, after.passBatchPlans - before.passBatchPlans, dump)
                assertEquals(0L, after.passBatchesAccepted - before.passBatchesAccepted, dump)
                assertEquals(0L, after.passBatchPackets - before.passBatchPackets, dump)
                assertTrue(!dump.contains("passes.batch-plan stream=fullscreen-uniform-pass"), dump)
                assertTrue(!dump.contains("kind=solid-fill"), dump)
            }
        }
    }

    @Test
    fun `backend runtime does not record pass batch plan for unmarked raw uniform fullscreen passes when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            val before = session.runtimeTelemetry

            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = 4, height = 4, colorFormat = "rgba8unorm"),
            ).use { target ->
                target.encode(GPUClearColor(0.0, 0.0, 0.0, 1.0)) {
                    drawFullscreenRawUniformPass(
                        wgsl = solidColorFullscreenWgsl(),
                        colorFormat = "rgba8unorm",
                        draws = listOf(
                            GPUBackendRawUniformDraw(
                                uniformBytes = solidColorUniformBytes(1f, 0f, 0f, 1f),
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = 4,
                                scissorHeight = 4,
                            ),
                        ),
                    )
                }

                val rgba = target.readRgba()
                val after = session.runtimeTelemetry
                val dump = session.runtimeTelemetryDumpLines.joinToString("\n")

                assertContentEquals(byteArrayOf(0xFF.toByte(), 0, 0, 0xFF.toByte()), rgba.copyOfRange(0, 4))
                assertEquals(0L, after.passBatchPlans - before.passBatchPlans, dump)
                assertEquals(0L, after.passBatchesAccepted - before.passBatchesAccepted, dump)
                assertEquals(0L, after.passBatchPackets - before.passBatchPackets, dump)
                assertTrue(!dump.contains("passes.batch-plan stream=fullscreen-uniform-pass"), dump)
                assertTrue(!dump.contains("kind=simple-gradient"), dump)
            }
        }
    }

    @Test
    fun `backend runtime records pass batch plan for explicitly marked simple gradient raw uniform fullscreen passes when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            val before = session.runtimeTelemetry

            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = 4, height = 4, colorFormat = "rgba8unorm"),
            ).use { target ->
                target.encode(GPUClearColor(0.0, 0.0, 0.0, 1.0)) {
                    drawFullscreenRawUniformPass(
                        wgsl = solidColorFullscreenWgsl(),
                        colorFormat = "rgba8unorm",
                        draws = listOf(
                            GPUBackendRawUniformDraw(
                                uniformBytes = solidColorUniformBytes(0f, 1f, 0f, 1f),
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = 4,
                                scissorHeight = 4,
                            ),
                        ),
                        passBatchKind = GPUBackendSimplePassBatchKind.SimpleGradient,
                    )
                }

                val rgba = target.readRgba()
                val after = session.runtimeTelemetry
                val dump = session.runtimeTelemetryDumpLines.joinToString("\n")

                assertContentEquals(byteArrayOf(0, 0xFF.toByte(), 0, 0xFF.toByte()), rgba.copyOfRange(0, 4))
                assertEquals(1L, after.passBatchPlans - before.passBatchPlans, dump)
                assertEquals(0L, after.passBatchesAccepted - before.passBatchesAccepted, dump)
                assertEquals(1L, after.passBatchPackets - before.passBatchPackets, dump)
                assertTrue(dump.contains("passes.batch-plan stream=fullscreen-uniform-pass"), dump)
                assertTrue(dump.contains("passes.batch id=batch-1 kind=simple-gradient"), dump)
            }
        }
    }

    @Test
    fun `batched rectangle scene uses fewer submissions than explicit unbatched baseline when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            val beforeBaseline = session.runtimeTelemetry
            session.createOffscreenTarget(GPUOffscreenTargetRequest(width = 4, height = 4, colorFormat = "rgba8unorm")).use { target ->
                repeat(4) { index ->
                    target.encode(GPUClearColor(0.0, 0.0, 0.0, 1.0)) {
                        drawFullscreenPass(
                            wgsl = solidColorFullscreenWgsl(),
                            colorFormat = "rgba8unorm",
                            draws = listOf(
                                GPUBackendRectDraw(
                                    rgbaPremul = floatArrayOf(1f, 0f, 0f, 1f),
                                    scissorX = index,
                                    scissorY = 0,
                                    scissorWidth = 1,
                                    scissorHeight = 4,
                                ),
                            ),
                            passBatchKind = GPUBackendSimplePassBatchKind.SolidFill,
                        )
                    }
                    target.readRgba()
                }
            }
            val afterBaseline = session.runtimeTelemetry
            val baselineSubmissions = afterBaseline.submissions - beforeBaseline.submissions
            val baselinePassBatchPlans = afterBaseline.passBatchPlans - beforeBaseline.passBatchPlans
            val baselinePassBatchesAccepted = afterBaseline.passBatchesAccepted - beforeBaseline.passBatchesAccepted
            val baselinePassBatchPackets = afterBaseline.passBatchPackets - beforeBaseline.passBatchPackets

            val beforeBatched = session.runtimeTelemetry
            session.createOffscreenTarget(GPUOffscreenTargetRequest(width = 4, height = 4, colorFormat = "rgba8unorm")).use { target ->
                target.encode(GPUClearColor(0.0, 0.0, 0.0, 1.0)) {
                    drawFullscreenPass(
                        wgsl = solidColorFullscreenWgsl(),
                        colorFormat = "rgba8unorm",
                        draws = (0 until 4).map { index ->
                            GPUBackendRectDraw(
                                rgbaPremul = floatArrayOf(1f, 0f, 0f, 1f),
                                scissorX = index,
                                scissorY = 0,
                                scissorWidth = 1,
                                scissorHeight = 4,
                            )
                        },
                        passBatchKind = GPUBackendSimplePassBatchKind.SolidFill,
                    )
                }
                target.readRgba()
            }
            val afterBatched = session.runtimeTelemetry
            val batchedSubmissions = afterBatched.submissions - beforeBatched.submissions
            val batchedPassBatchPlans = afterBatched.passBatchPlans - beforeBatched.passBatchPlans
            val batchedPassBatchesAccepted = afterBatched.passBatchesAccepted - beforeBatched.passBatchesAccepted
            val batchedPassBatchPackets = afterBatched.passBatchPackets - beforeBatched.passBatchPackets
            val dump = session.runtimeTelemetryDumpLines.joinToString("\n")

            assertEquals(4L, baselineSubmissions)
            assertEquals(1L, batchedSubmissions)
            assertTrue(batchedSubmissions < baselineSubmissions)
            assertEquals(4L, baselinePassBatchPlans)
            assertEquals(0L, baselinePassBatchesAccepted)
            assertEquals(4L, baselinePassBatchPackets)
            assertEquals(1L, batchedPassBatchPlans)
            assertEquals(1L, batchedPassBatchesAccepted)
            assertEquals(4L, batchedPassBatchPackets)
            assertTrue(dump.contains("passes.batch-plan stream=fullscreen-uniform-pass"), dump)
            assertTrue(dump.contains("passes.batch id=batch-1 kind=solid-fill"), dump)
        }
    }

    @Test
    fun `offscreen submission stays pending until readback completes when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = 4, height = 4, colorFormat = "rgba8unorm"),
            ).use { target ->
                target.encode(
                    clearColor = GPUClearColor(red = 0.0, green = 0.0, blue = 0.0, alpha = 1.0),
                ) {
                    drawFullscreenPass(
                        wgsl = solidColorFullscreenWgsl(),
                        colorFormat = "rgba8unorm",
                        draws = listOf(
                            GPUBackendRectDraw(
                                rgbaPremul = floatArrayOf(1f, 0f, 0f, 1f),
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = 4,
                                scissorHeight = 4,
                            ),
                        ),
                    )
                }

                val pendingDump = session.phase0EvidenceDumpLines.joinToString("\n")
                assertTrue(
                    pendingDump.contains(
                        "gpu-queue.telemetry submitted=1 completed=0 released=0 pending=1 waits=0 unknownCompletions=0",
                    ),
                )
                assertTrue(pendingDump.contains("completion=pending"))

                val rgba = target.readRgba()
                assertContentEquals(byteArrayOf(0xFF.toByte(), 0, 0, 0xFF.toByte()), rgba.copyOfRange(0, 4))

                val completedDump = session.phase0EvidenceDumpLines.joinToString("\n")
                assertTrue(
                    completedDump.contains(
                        "gpu-queue.telemetry submitted=1 completed=1 released=1 pending=0 waits=1 unknownCompletions=0",
                    ),
                )
                assertTrue(completedDump.contains("completion=readback-complete"))
            }
        }
    }

    @Test
    fun `backend runtime batches fullscreen uniform draws into one slab when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            val before = session.runtimeTelemetry

            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(
                    width = 6,
                    height = 2,
                    colorFormat = "rgba8unorm",
                ),
            ).use { target ->
                target.encode(
                    clearColor = GPUClearColor(red = 0.0, green = 0.0, blue = 0.0, alpha = 1.0),
                ) {
                    drawFullscreenPass(
                        wgsl = solidColorFullscreenWgsl(),
                        colorFormat = "rgba8unorm",
                        draws = listOf(
                            GPUBackendRectDraw(
                                rgbaPremul = floatArrayOf(1f, 0f, 0f, 1f),
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = 2,
                                scissorHeight = 2,
                            ),
                            GPUBackendRectDraw(
                                rgbaPremul = floatArrayOf(0f, 1f, 0f, 1f),
                                scissorX = 2,
                                scissorY = 0,
                                scissorWidth = 2,
                                scissorHeight = 2,
                            ),
                            GPUBackendRectDraw(
                                rgbaPremul = floatArrayOf(0f, 0f, 1f, 1f),
                                scissorX = 4,
                                scissorY = 0,
                                scissorWidth = 2,
                                scissorHeight = 2,
                            ),
                        ),
                    )
                }

                val rgba = target.readRgba()
                val after = session.runtimeTelemetry
                val dump = session.runtimeTelemetryDumpLines.joinToString("\n")

                assertContentEquals(
                    byteArrayOf(0xFF.toByte(), 0, 0, 0xFF.toByte()),
                    pixelAt(rgba = rgba, width = 6, x = 0, y = 0),
                )
                assertContentEquals(
                    byteArrayOf(0, 0xFF.toByte(), 0, 0xFF.toByte()),
                    pixelAt(rgba = rgba, width = 6, x = 2, y = 0),
                )
                assertContentEquals(
                    byteArrayOf(0, 0, 0xFF.toByte(), 0xFF.toByte()),
                    pixelAt(rgba = rgba, width = 6, x = 4, y = 0),
                )
                assertEquals(1L, after.uniformSlabsCreated - before.uniformSlabsCreated)
                assertEquals(768L, after.uniformSlabBytesAllocated - before.uniformSlabBytesAllocated)
                assertEquals(0L, after.uniformSlabFallbacks - before.uniformSlabFallbacks)
                assertEquals(2L, after.buffersCreated - before.buffersCreated)
                assertTrue(dump.contains("uniformSlabsCreated="))
                assertTrue(dump.contains("uniformSlabBytesAllocated="))
                assertTrue(dump.contains("uniformSlabFallbacks="))
                assertTrue(dump.contains("payload-slab.batch.plan source=fullscreen-uniform-pass"))
                assertTrue(dump.contains("payload-slab.resource.planned source=fullscreen-uniform-pass"))
                assertTrue(dump.contains("payload-slab.resource.accepted source=fullscreen-uniform-pass"))
                assertTrue(
                    Regex("""payload-slab\.batch\.plan .* frame=offscreen-\d+-\d+-frame-\d+ """).containsMatchIn(dump),
                    "payload slab plan dump should include per-encode offscreen frame ordinal",
                )
                assertTrue(dump.contains("payload-slab.batch.plan source=fullscreen-uniform-pass target=payload-target-"))
                assertTrue(dump.contains("payload-slab.batch.slot source=fullscreen-uniform-pass slot=fullscreen-packet-0:fullscreen-pass:uniform:0:fullscreen-pass:resource:0"))
                assertTrue(!dump.contains("@"))
                assertTrue(!dump.contains(forbiddenImplementationTokenUpperForAudit()))
                assertTrue(!dump.contains(forbiddenImplementationTokenLowerForAudit()))
                assertTrue(!dump.contains("0x"))
            }
        }
    }

    @Test
    fun `fullscreen uniform path exposes provider cache evidence when runtime is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU runtime unavailable in current environment")

        runtime!!.use { session ->
            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(
                    width = 4,
                    height = 4,
                    colorFormat = "rgba8unorm",
                ),
            ).use { target ->
                target.encode(
                    clearColor = GPUClearColor(red = 0.0, green = 0.0, blue = 0.0, alpha = 1.0),
                ) {
                    drawFullscreenPass(
                        wgsl = solidColorFullscreenWgsl(),
                        colorFormat = "rgba8unorm",
                        draws = listOf(
                            GPUBackendRectDraw(
                                rgbaPremul = floatArrayOf(1f, 0f, 0f, 1f),
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = 4,
                                scissorHeight = 4,
                            ),
                        ),
                    )
                }
                target.readRgba()
            }

            val evidenceDump = session.phase0EvidenceDumpLines.joinToString("\n")

            assertTrue(evidenceDump.contains("gpu-phase0.baseline"))
            assertTrue(
                evidenceDump.contains(
                    "gpu-queue.telemetry submitted=1 completed=1 released=1 pending=0 waits=1 unknownCompletions=0",
                ),
            )
            assertTrue(evidenceDump.contains("gpu-queue.submission id=1 label=offscreen-pass:"))
            assertTrue(evidenceDump.contains("retained=4"))
            assertTrue(evidenceDump.contains("completion=readback-complete"))
            assertTrue(evidenceDump.contains("resource-provider.cache"))
            assertTrue(evidenceDump.contains("resource-provider.lease"))
            assertTrue(evidenceDump.contains("kind=uniform-slab"))
            assertTrue(evidenceDump.contains("kind=bind-group"))
            assertTrue(evidenceDump.contains("result=create") || evidenceDump.contains("result=reuse"))
            assertTrue(!evidenceDump.contains("W" + "GPU"))
            assertTrue(!evidenceDump.contains("@"))
        }
    }

    @Test
    fun `fullscreen uniform path reuses provider lease evidence on repeated frames when runtime is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = 4, height = 4, colorFormat = "rgba8unorm"),
            ).use { target ->
                repeat(2) {
                    target.encode(GPUClearColor(0.0, 0.0, 0.0, 1.0)) {
                        drawFullscreenPass(
                            wgsl = solidColorFullscreenWgsl(),
                            colorFormat = "rgba8unorm",
                            draws = listOf(
                                GPUBackendRectDraw(
                                    rgbaPremul = floatArrayOf(1f, 0f, 0f, 1f),
                                    scissorX = 0,
                                    scissorY = 0,
                                    scissorWidth = 4,
                                    scissorHeight = 4,
                                ),
                            ),
                        )
                    }
                }
            }

            val dumpLines = session.phase0EvidenceDumpLines
            assertEquals(
                1,
                dumpLines.count { line -> line.contains("resource-provider.cache lane=uniform-slab result=create") },
            )
            assertEquals(
                1,
                dumpLines.count { line -> line.contains("resource-provider.cache lane=uniform-slab result=reuse") },
            )
            assertTrue(dumpLines.any { line -> line.contains("gpu-queue.submission") })
            assertTrue(dumpLines.none { line -> line.contains("@") })
        }
    }

    @Test
    fun `fullscreen uniform reuse keeps native resource counts stable on repeated frames when runtime is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = 4, height = 4, colorFormat = "rgba8unorm"),
            ).use { target ->
                fun encodeRedFrame() {
                    target.encode(GPUClearColor(0.0, 0.0, 0.0, 1.0)) {
                        drawFullscreenPass(
                            wgsl = solidColorFullscreenWgsl(),
                            colorFormat = "rgba8unorm",
                            draws = listOf(
                                GPUBackendRectDraw(
                                    rgbaPremul = floatArrayOf(1f, 0f, 0f, 1f),
                                    scissorX = 0,
                                    scissorY = 0,
                                    scissorWidth = 4,
                                    scissorHeight = 4,
                                ),
                            ),
                        )
                    }
                }

                val before = session.runtimeTelemetry
                encodeRedFrame()
                val afterFirst = session.runtimeTelemetry
                encodeRedFrame()
                val afterSecond = session.runtimeTelemetry

                assertEquals(1L, afterFirst.uniformSlabsCreated - before.uniformSlabsCreated)
                assertEquals(1L, afterFirst.buffersCreated - before.buffersCreated)
                assertEquals(1L, afterFirst.bindGroupsCreated - before.bindGroupsCreated)
                assertEquals(0L, afterSecond.uniformSlabsCreated - afterFirst.uniformSlabsCreated)
                assertEquals(0L, afterSecond.uniformSlabBytesAllocated - afterFirst.uniformSlabBytesAllocated)
                assertEquals(0L, afterSecond.buffersCreated - afterFirst.buffersCreated)
                assertEquals(0L, afterSecond.bindGroupsCreated - afterFirst.bindGroupsCreated)
                assertTrue(afterSecond.queueWrites - afterFirst.queueWrites >= 1L)
            }

            val dumpLines = session.phase0EvidenceDumpLines
            val bindGroupCreateLines =
                dumpLines.filter { line ->
                    line.contains("resource-provider.cache lane=bind-group result=create") &&
                        line.contains("key=lease=bind-group:fullscreen:")
                }
            val bindGroupReuseLines =
                dumpLines.filter { line ->
                    line.contains("resource-provider.cache lane=bind-group result=reuse") &&
                        line.contains("key=lease=bind-group:fullscreen:")
                }
            assertEquals(
                1,
                dumpLines.count { line -> line.contains("resource-provider.cache lane=uniform-slab result=create") },
            )
            assertEquals(
                1,
                dumpLines.count { line -> line.contains("resource-provider.cache lane=uniform-slab result=reuse") },
            )
            assertTrue(
                bindGroupCreateLines.size == 1,
                "Expected one bind-group create line, got ${bindGroupCreateLines.size}:\n" +
                    bindGroupCreateLines.joinToString("\n"),
            )
            assertTrue(
                bindGroupReuseLines.size == 1,
                "Expected one bind-group reuse line, got ${bindGroupReuseLines.size}:\n" +
                    bindGroupReuseLines.joinToString("\n"),
            )
            assertTrue(dumpLines.none { line -> line.contains("@") })
            assertTrue(dumpLines.none { line -> line.contains(forbiddenImplementationTokenUpperForAudit()) })
            assertTrue(dumpLines.none { line -> line.contains(forbiddenImplementationTokenLowerForAudit()) })
        }
    }

    @Test
    fun `fullscreen uniform cache identity stays target scoped when runtime is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            fun GPUBackendOffscreenTarget.encodeRedFrame() {
                encode(GPUClearColor(0.0, 0.0, 0.0, 1.0)) {
                    drawFullscreenPass(
                        wgsl = solidColorFullscreenWgsl(),
                        colorFormat = "rgba8unorm",
                        draws = listOf(
                            GPUBackendRectDraw(
                                rgbaPremul = floatArrayOf(1f, 0f, 0f, 1f),
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = 4,
                                scissorHeight = 4,
                            ),
                        ),
                    )
                }
            }

            val firstTarget = session.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = 4, height = 4, colorFormat = "rgba8unorm"),
            )
            val secondTarget = session.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = 4, height = 4, colorFormat = "rgba8unorm"),
            )
            firstTarget.use { target ->
                val beforeFirst = session.runtimeTelemetry
                target.encodeRedFrame()
                val afterFirst = session.runtimeTelemetry
                assertEquals(1L, afterFirst.uniformSlabsCreated - beforeFirst.uniformSlabsCreated)
                assertEquals(1L, afterFirst.bindGroupsCreated - beforeFirst.bindGroupsCreated)
            }
            secondTarget.use { target ->
                val beforeSecond = session.runtimeTelemetry
                target.encodeRedFrame()
                val afterSecond = session.runtimeTelemetry
                assertEquals(1L, afterSecond.uniformSlabsCreated - beforeSecond.uniformSlabsCreated)
                assertEquals(1L, afterSecond.bindGroupsCreated - beforeSecond.bindGroupsCreated)
            }

            val dumpLines = session.phase0EvidenceDumpLines
            assertEquals(
                2,
                dumpLines.count { line -> line.contains("resource-provider.cache lane=uniform-slab result=create") },
            )
            assertEquals(
                2,
                dumpLines.count { line ->
                    line.contains("resource-provider.cache lane=bind-group result=create") &&
                        line.contains("key=lease=bind-group:fullscreen:")
                },
            )
        }
    }

    @Test
    fun `offscreen texture fullscreen pass records resource leases when runtime is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = 4, height = 4, colorFormat = "rgba8unorm"),
            ).use { target ->
                val textureLabel = target.createOffscreenTexture(
                    GPUBackendOffscreenTexture(label = "lease-secondary", width = 4, height = 4, format = "rgba8unorm"),
                )
                target.encodeOffscreenTexture(
                    textureLabel = textureLabel,
                    clearColor = GPUClearColor(0.0, 0.0, 0.0, 1.0),
                ) {
                    drawFullscreenPass(
                        wgsl = solidColorFullscreenWgsl(),
                        colorFormat = "rgba8unorm",
                        draws = listOf(
                            GPUBackendRectDraw(
                                rgbaPremul = floatArrayOf(1f, 0f, 0f, 1f),
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = 4,
                                scissorHeight = 4,
                            ),
                        ),
                    )
                }
            }

            val dumpLines = session.phase0EvidenceDumpLines
            val textureSubmissionLine = dumpLines.singleOrNull { line ->
                line.contains("gpu-queue.submission") && line.contains("offscreen-texture-pass:")
            } ?: error("Expected one offscreen texture submission")
            assertTrue(
                dumpLines.any { line ->
                    line.contains(
                        "gpu-queue.telemetry submitted=1 completed=1 released=1 pending=0 waits=0 unknownCompletions=0",
                    )
                },
            )
            assertTrue(textureSubmissionLine.contains("completed=true"))
            assertTrue(textureSubmissionLine.contains("released=true"))
            assertTrue(textureSubmissionLine.contains("completion=target-close"))
            assertTrue(dumpLines.any { line -> line.contains("retained=3") })
            assertTrue(dumpLines.any { line -> line.contains("kind=uniform-slab") && line.contains("result=create") })
            assertTrue(dumpLines.any { line -> line.contains("kind=bind-group") && line.contains("result=create") })
        }
    }

    @Test
    fun `offscreen texture pass is not completed by unrelated readback when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = 4, height = 4, colorFormat = "rgba8unorm"),
            ).use { target ->
                val textureLabel = target.createOffscreenTexture(
                    GPUBackendOffscreenTexture(label = "pending-secondary", width = 4, height = 4, format = "rgba8unorm"),
                )
                target.encodeOffscreenTexture(
                    textureLabel = textureLabel,
                    clearColor = GPUClearColor(0.0, 0.0, 0.0, 1.0),
                ) {
                    drawFullscreenPass(
                        wgsl = solidColorFullscreenWgsl(),
                        colorFormat = "rgba8unorm",
                        draws = listOf(
                            GPUBackendRectDraw(
                                rgbaPremul = floatArrayOf(1f, 0f, 0f, 1f),
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = 4,
                                scissorHeight = 4,
                            ),
                        ),
                    )
                }

                val pendingDump = session.phase0EvidenceDumpLines.joinToString("\n")
                assertTrue(
                    pendingDump.contains(
                        "gpu-queue.telemetry submitted=1 completed=0 released=0 pending=1 waits=0 unknownCompletions=0",
                    ),
                )

                target.readRgba()

                val afterReadbackDump = session.phase0EvidenceDumpLines.joinToString("\n")
                val textureSubmissionLine = session.phase0EvidenceDumpLines.singleOrNull { line ->
                    line.contains("gpu-queue.submission") && line.contains("offscreen-texture-pass:")
                } ?: error("Expected one offscreen texture submission")
                assertTrue(
                    afterReadbackDump.contains(
                        "gpu-queue.telemetry submitted=1 completed=0 released=0 pending=1 waits=1 unknownCompletions=0",
                    ),
                )
                assertTrue(textureSubmissionLine.contains("completed=false"))
                assertTrue(textureSubmissionLine.contains("released=false"))
                assertTrue(textureSubmissionLine.contains("completion=pending"))
            }

            val closedDump = session.phase0EvidenceDumpLines.joinToString("\n")
            assertTrue(
                closedDump.contains(
                    "gpu-queue.telemetry submitted=1 completed=1 released=1 pending=0 waits=1 unknownCompletions=0",
                ),
            )
            assertTrue(closedDump.contains("completion=target-close"))
        }
    }

    @Test
    fun `backend runtime falls back when fullscreen uniform slab planner refuses and backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        withFullscreenUniformSlabRefusedForTesting {
            runtime!!.use { session ->
                val before = session.runtimeTelemetry

                session.createOffscreenTarget(
                    GPUOffscreenTargetRequest(
                        width = 6,
                        height = 2,
                        colorFormat = "rgba8unorm",
                    ),
                ).use { target ->
                    target.encode(
                        clearColor = GPUClearColor(red = 0.0, green = 0.0, blue = 0.0, alpha = 1.0),
                    ) {
                        drawFullscreenPass(
                            wgsl = solidColorFullscreenWgsl(),
                            colorFormat = "rgba8unorm",
                            draws = listOf(
                                GPUBackendRectDraw(
                                    rgbaPremul = floatArrayOf(1f, 0f, 0f, 1f),
                                    scissorX = 0,
                                    scissorY = 0,
                                    scissorWidth = 2,
                                    scissorHeight = 2,
                                ),
                                GPUBackendRectDraw(
                                    rgbaPremul = floatArrayOf(0f, 1f, 0f, 1f),
                                    scissorX = 2,
                                    scissorY = 0,
                                    scissorWidth = 2,
                                    scissorHeight = 2,
                                ),
                                GPUBackendRectDraw(
                                    rgbaPremul = floatArrayOf(0f, 0f, 1f, 1f),
                                    scissorX = 4,
                                    scissorY = 0,
                                    scissorWidth = 2,
                                    scissorHeight = 2,
                                ),
                            ),
                        )
                    }

                    val rgba = target.readRgba()
                    val after = session.runtimeTelemetry
                    val dump = session.runtimeTelemetryDumpLines.joinToString("\n")

                    assertTrue(dump.contains("payload-slab.resource.planned source=fullscreen-uniform-pass"))
                    assertTrue(dump.contains("payload-slab.resource.fallback source=fullscreen-uniform-pass"))
                    assertTrue(dump.contains("reason=unsupported.payload_slab_dump_unsafe"))

                    assertContentEquals(
                        byteArrayOf(0xFF.toByte(), 0, 0, 0xFF.toByte()),
                        pixelAt(rgba = rgba, width = 6, x = 0, y = 0),
                    )
                    assertContentEquals(
                        byteArrayOf(0, 0xFF.toByte(), 0, 0xFF.toByte()),
                        pixelAt(rgba = rgba, width = 6, x = 2, y = 0),
                    )
                    assertContentEquals(
                        byteArrayOf(0, 0, 0xFF.toByte(), 0xFF.toByte()),
                        pixelAt(rgba = rgba, width = 6, x = 4, y = 0),
                    )
                    assertEquals(1L, after.uniformSlabFallbacks - before.uniformSlabFallbacks)
                    assertEquals(0L, after.uniformSlabsCreated - before.uniformSlabsCreated)
                    assertEquals(0L, after.uniformSlabBytesAllocated - before.uniformSlabBytesAllocated)
                    assertTrue(dump.contains("uniformSlabsCreated="))
                    assertTrue(dump.contains("uniformSlabBytesAllocated="))
                    assertTrue(dump.contains("uniformSlabFallbacks="))
                    assertTrue(!dump.contains("@"))
                }
            }
        }
    }

    @Test
    fun `backend runtime does not record accepted pass batch plan when fullscreen slab fallback handles marked solid fills`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        withFullscreenUniformSlabRefusedForTesting {
            runtime!!.use { session ->
                val before = session.runtimeTelemetry

                session.createOffscreenTarget(
                    GPUOffscreenTargetRequest(
                        width = 6,
                        height = 2,
                        colorFormat = "rgba8unorm",
                    ),
                ).use { target ->
                    target.encode(
                        clearColor = GPUClearColor(red = 0.0, green = 0.0, blue = 0.0, alpha = 1.0),
                    ) {
                        drawFullscreenPass(
                            wgsl = solidColorFullscreenWgsl(),
                            colorFormat = "rgba8unorm",
                            draws = listOf(
                                GPUBackendRectDraw(
                                    rgbaPremul = floatArrayOf(1f, 0f, 0f, 1f),
                                    scissorX = 0,
                                    scissorY = 0,
                                    scissorWidth = 2,
                                    scissorHeight = 2,
                                ),
                                GPUBackendRectDraw(
                                    rgbaPremul = floatArrayOf(0f, 1f, 0f, 1f),
                                    scissorX = 2,
                                    scissorY = 0,
                                    scissorWidth = 2,
                                    scissorHeight = 2,
                                ),
                            ),
                            passBatchKind = GPUBackendSimplePassBatchKind.SolidFill,
                        )
                    }

                    val rgba = target.readRgba()
                    val after = session.runtimeTelemetry
                    val dump = session.runtimeTelemetryDumpLines.joinToString("\n")

                    assertContentEquals(
                        byteArrayOf(0xFF.toByte(), 0, 0, 0xFF.toByte()),
                        pixelAt(rgba = rgba, width = 6, x = 0, y = 0),
                    )
                    assertContentEquals(
                        byteArrayOf(0, 0xFF.toByte(), 0, 0xFF.toByte()),
                        pixelAt(rgba = rgba, width = 6, x = 2, y = 0),
                    )
                    assertEquals(1L, after.uniformSlabFallbacks - before.uniformSlabFallbacks)
                    assertEquals(0L, after.passBatchPlans - before.passBatchPlans, dump)
                    assertEquals(0L, after.passBatchesAccepted - before.passBatchesAccepted, dump)
                    assertEquals(0L, after.passBatchPackets - before.passBatchPackets, dump)
                    assertTrue(dump.contains("payload-slab.resource.fallback source=fullscreen-uniform-pass"))
                    assertTrue(!dump.contains("passes.batch-plan stream=fullscreen-uniform-pass"), dump)
                    assertTrue(!dump.contains("passes.batch id=batch-1 kind=solid-fill"), dump)
                }
            }
        }
    }

    @Test
    fun `backend runtime records GPU execution cache hit miss and create telemetry when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(
                    width = 4,
                    height = 4,
                    colorFormat = "rgba8unorm",
                ),
            ).use { target ->
                repeat(2) {
                    target.encode(
                        clearColor = GPUClearColor(red = 0.0, green = 0.0, blue = 0.0, alpha = 1.0),
                    ) {
                        drawFullscreenPass(
                            wgsl = solidColorFullscreenWgsl(),
                            colorFormat = "rgba8unorm",
                            draws = listOf(
                                GPUBackendRectDraw(
                                    rgbaPremul = floatArrayOf(1f, 0f, 0f, 1f),
                                    scissorX = 0,
                                    scissorY = 0,
                                    scissorWidth = 4,
                                    scissorHeight = 4,
                                ),
                            ),
                        )
                    }
                }

                val telemetry = session.executionCacheTelemetry.associateBy(GPUCacheTelemetry::cacheName)

                listOf("module", "bind-group-layout", "pipeline-layout", "pipeline").forEach { cacheName ->
                    val cache = telemetry.getValue(cacheName)
                    assertEquals(1L, cache.misses, "$cacheName should miss once on the first encode")
                    assertEquals(1L, cache.creations, "$cacheName should create once on the first encode")
                    assertEquals(1L, cache.hits, "$cacheName should hit once on the second encode")
                }

                val dump = session.executionCacheDumpLines.joinToString("\n")
                listOf("module", "bind-group-layout", "pipeline-layout", "pipeline").forEach { cacheName ->
                    assertTrue(dump.contains("domain=$cacheName"), "$cacheName should be present in cache dumps")
                }
                listOf("module", "bind-group-layout", "pipeline-layout", "pipeline").forEach { cacheName ->
                    assertTrue(
                        dump.contains("execution.cache.preimage domain=$cacheName"),
                        "$cacheName should expose a deterministic cache-key preimage dump",
                    )
                }
                assertTrue(dump.contains("kind=wgsl-module"))
                assertTrue(dump.contains("kind=bind-group-layout"))
                assertTrue(dump.contains("kind=pipeline-layout"))
                assertTrue(dump.contains("kind=render"))
                assertTrue(dump.contains("renderStepIdentity=gpu-backend.fullscreen-pass"))
                assertTrue(dump.contains("owner=GPUResourceProvider"))
                assertTrue(dump.contains("productRouteActivated=false"))
                assertTrue(dump.contains("releaseBlocking=false"))
                assertTrue(!dump.contains("@"), "cache dumps must not expose backend object identities")
            }
        }
    }

    @Test
    fun `backend runtime records gradient material payload slabs when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            val before = session.runtimeTelemetry

            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(
                    width = 6,
                    height = 2,
                    colorFormat = "rgba8unorm",
                ),
            ).use { target ->
                target.encode(
                    clearColor = GPUClearColor(red = 0.0, green = 0.0, blue = 0.0, alpha = 1.0),
                ) {
                    drawFullscreenUniformPayloadPass(
                        wgsl = solidColorPayloadWgsl(),
                        colorFormat = "rgba8unorm",
                        draws = listOf(
                            GPUBackendUniformPayloadDraw(
                                uniformBytes = uniformPayloadBlock().bytes.map { byte -> byte.toByte() }.toByteArray(),
                                materialization = gradientMaterialization("gradient-red"),
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = 3,
                                scissorHeight = 2,
                            ),
                            GPUBackendUniformPayloadDraw(
                                uniformBytes = uniformPayloadBlock().bytes.map { byte -> byte.toByte() }.toByteArray(),
                                materialization = gradientMaterialization("gradient-green"),
                                scissorX = 3,
                                scissorY = 0,
                                scissorWidth = 3,
                                scissorHeight = 2,
                            ),
                        ),
                        sourceLabel = "gradient-material-pass",
                    )
                }

                val rgba = target.readRgba()
                val after = session.runtimeTelemetry
                val dump = session.runtimeTelemetryDumpLines.joinToString("\n")

                assertContentEquals(
                    byteArrayOf(0xFF.toByte(), 0, 0, 0xFF.toByte()),
                    pixelAt(rgba = rgba, width = 6, x = 0, y = 0),
                )
                assertContentEquals(
                    byteArrayOf(0xFF.toByte(), 0, 0, 0xFF.toByte()),
                    pixelAt(rgba = rgba, width = 6, x = 3, y = 0),
                )
                assertEquals(1L, after.uniformSlabsCreated - before.uniformSlabsCreated)
                assertTrue(dump.contains("payload-slab.batch.plan source=gradient-material-pass"))
                assertTrue(dump.contains("payload-slab.resource.planned source=gradient-material-pass"))
                assertTrue(dump.contains("payload-slab.resource.accepted source=gradient-material-pass"))
                assertTrue(!dump.contains("@"))
                assertTrue(!dump.contains(forbiddenImplementationTokenUpperForAudit()))
                assertTrue(!dump.contains(forbiddenImplementationTokenLowerForAudit()))
                assertTrue(!dump.contains("0x"))
            }
        }
    }

    @Test
    fun `backend runtime does not auto record pass batch evidence for unmarked payload fullscreen passes when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        val uniformBlock = uniformPayloadBlock()
        val materialized = assertIs<GPUResourceMaterializationDecision.Materialized>(
            ValidatingPayloadResourceProvider().materializePayloadBindings(
                request = payloadMaterializationRequest(uniformBlock),
                context = GPUTargetPreparationContext(
                    targetId = "root-target",
                    frameId = "frame-1",
                    deviceGeneration = 1,
                    budgetClass = "smoke-test",
                ),
            ),
        )

        runtime!!.use { session ->
            val before = session.runtimeTelemetry

            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(
                    width = 6,
                    height = 2,
                    colorFormat = "rgba8unorm",
                ),
            ).use { target ->
                target.encode(
                    clearColor = GPUClearColor(red = 0.0, green = 0.0, blue = 0.0, alpha = 1.0),
                ) {
                    drawFullscreenUniformPayloadPass(
                        wgsl = solidColorPayloadWgsl(),
                        colorFormat = "rgba8unorm",
                        draws = listOf(
                            GPUBackendUniformPayloadDraw(
                                uniformBytes = uniformBlock.bytes.map { byte -> byte.toByte() }.toByteArray(),
                                materialization = materialized,
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = 3,
                                scissorHeight = 2,
                            ),
                            GPUBackendUniformPayloadDraw(
                                uniformBytes = uniformBlock.bytes.map { byte -> byte.toByte() }.toByteArray(),
                                materialization = materialized,
                                scissorX = 3,
                                scissorY = 0,
                                scissorWidth = 3,
                                scissorHeight = 2,
                            ),
                        ),
                    )
                }

                val rgba = target.readRgba()
                val after = session.runtimeTelemetry
                val dump = session.runtimeTelemetryDumpLines.joinToString("\n")

                assertContentEquals(
                    byteArrayOf(0xFF.toByte(), 0, 0, 0xFF.toByte()),
                    pixelAt(rgba = rgba, width = 6, x = 0, y = 0),
                )
                assertContentEquals(
                    byteArrayOf(0xFF.toByte(), 0, 0, 0xFF.toByte()),
                    pixelAt(rgba = rgba, width = 6, x = 3, y = 0),
                )
                assertEquals(0L, after.passBatchPlans - before.passBatchPlans, dump)
                assertEquals(0L, after.passBatchesAccepted - before.passBatchesAccepted, dump)
                assertEquals(0L, after.passBatchPackets - before.passBatchPackets, dump)
                assertTrue(!dump.contains("passes.batch-plan"), dump)
                assertTrue(!dump.contains("passes.batch id="), dump)
                assertTrue(!dump.contains("kind=solid-fill"), dump)
                assertTrue(!dump.contains("kind=simple-gradient"), dump)
            }
        }
    }

    @Test
    fun `backend runtime falls back safely for unsafe public payload source labels when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        val uniformBlock = uniformPayloadBlock()
        val materialized = assertIs<GPUResourceMaterializationDecision.Materialized>(
            ValidatingPayloadResourceProvider().materializePayloadBindings(
                request = payloadMaterializationRequest(uniformBlock),
                context = GPUTargetPreparationContext(
                    targetId = "root-target",
                    frameId = "frame-1",
                    deviceGeneration = 1,
                    budgetClass = "smoke-test",
                ),
            ),
        )

        runtime!!.use { session ->
            val before = session.runtimeTelemetry

            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(
                    width = 4,
                    height = 4,
                    colorFormat = "rgba8unorm",
                ),
            ).use { target ->
                target.encode(
                    clearColor = GPUClearColor(red = 0.0, green = 0.0, blue = 0.0, alpha = 1.0),
                ) {
                    drawFullscreenUniformPayloadPass(
                        wgsl = solidColorPayloadWgsl(),
                        colorFormat = "rgba8unorm",
                        draws = listOf(
                            GPUBackendUniformPayloadDraw(
                                uniformBytes = uniformBlock.bytes.map { byte -> byte.toByte() }.toByteArray(),
                                materialization = materialized,
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = 4,
                                scissorHeight = 4,
                            ),
                        ),
                        sourceLabel = "gradient-material-pass@unsafe",
                    )
                }

                val rgba = target.readRgba()
                val after = session.runtimeTelemetry
                val dump = session.runtimeTelemetryDumpLines.joinToString("\n")

                assertContentEquals(
                    byteArrayOf(0xFF.toByte(), 0, 0, 0xFF.toByte()),
                    pixelAt(rgba = rgba, width = 4, x = 0, y = 0),
                )
                assertEquals(1L, after.uniformSlabFallbacks - before.uniformSlabFallbacks)
                assertTrue(dump.contains("payload-slab.resource.planned source=fullscreen-uniform-pass"))
                assertTrue(dump.contains("payload-slab.resource.fallback source=fullscreen-uniform-pass"))
                assertTrue(dump.contains("reason=unsupported.payload_slab_dump_unsafe"))
                assertTrue(!dump.contains("@"))
                assertTrue(!dump.contains(forbiddenImplementationTokenUpperForAudit()))
                assertTrue(!dump.contains(forbiddenImplementationTokenLowerForAudit()))
                assertTrue(!dump.contains("0x"))
            }
        }
    }

    @Test
    fun `backend runtime uploads uniform payload bytes and binds them when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        val uniformBlock = uniformPayloadBlock()
        val materialization = ValidatingPayloadResourceProvider().materializePayloadBindings(
            request = payloadMaterializationRequest(uniformBlock),
            context = GPUTargetPreparationContext(
                targetId = "root-target",
                frameId = "frame-1",
                deviceGeneration = 1,
                budgetClass = "smoke-test",
            ),
        )
        val materialized = assertIs<GPUResourceMaterializationDecision.Materialized>(materialization)

        runtime!!.use { session ->
            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(
                    width = 4,
                    height = 4,
                    colorFormat = "rgba8unorm",
                ),
            ).use { target ->
                target.encode(
                    clearColor = GPUClearColor(red = 0.0, green = 0.0, blue = 0.0, alpha = 1.0),
                ) {
                    drawFullscreenUniformPayloadPass(
                        wgsl = solidColorPayloadWgsl(),
                        colorFormat = "rgba8unorm",
                        draws = listOf(
                            GPUBackendUniformPayloadDraw(
                                uniformBytes = uniformBlock.bytes.map { byte -> byte.toByte() }.toByteArray(),
                                materialization = materialized,
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = 4,
                                scissorHeight = 4,
                            ),
                        ),
                    )
                }

                val rgba = target.readRgba()
                val materializationDump = materialized.dumpLines()

                assertContentEquals(byteArrayOf(0xFF.toByte(), 0, 0, 0xFF.toByte()), rgba.copyOfRange(0, 4))
                assertContains(
                    materializationDump,
                    "resource.materialization:operand operand=payload-upload:pass-a:uniform:0 kind=uniform-buffer " +
                        "deviceGeneration=1 owner=payload-scope:pass-a usage=copy_dst,uniform " +
                        "invalidation=pass-end descriptor=uniform-fingerprint-smoke " +
                        "facts=alignment=256;bindingLayout=layout-solid-v1;byteSize=64;generation=1;" +
                        "scope=pass-a;uploadPlan=upload-solid-v1;uploadScope=pass-a-staging;zeroedPadding=true",
                )
                assertTrue(session.executionCacheDumpLines.joinToString("\n").contains("kind=bind-group-layout"))
                assertTrue(materializationDump.joinToString("\n").contains("productRouteActivated=false"))
            }
        }
    }

    @Test
    fun `backend runtime records GPU execution cache failure telemetry when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(
                    width = 4,
                    height = 4,
                    colorFormat = "rgba8unorm",
                ),
            ).use { target ->
                val failure = assertFailsWith<IllegalStateException> {
                    target.encode(
                        clearColor = GPUClearColor(red = 0.0, green = 0.0, blue = 0.0, alpha = 1.0),
                    ) {
                        drawFullscreenPass(
                            wgsl = fullscreenWgslWithoutFragmentEntry(),
                            colorFormat = "rgba8unorm",
                            draws = listOf(
                                GPUBackendRectDraw(
                                    rgbaPremul = floatArrayOf(1f, 0f, 0f, 1f),
                                    scissorX = 0,
                                    scissorY = 0,
                                    scissorWidth = 4,
                                    scissorHeight = 4,
                                ),
                            ),
                        )
                    }
                }

                assertTrue(
                    failure.message?.contains("unsupported.execution.cache_create_failed") == true,
                    "failure should expose a stable execution-cache diagnostic",
                )
                val telemetry = session.executionCacheTelemetry.associateBy(GPUCacheTelemetry::cacheName)
                assertEquals(1L, telemetry.getValue("pipeline").failures)
                val dump = session.executionCacheDumpLines.joinToString("\n")
                assertTrue(dump.contains("domain=pipeline"))
                assertTrue(dump.contains("result=failure"))
                assertTrue(dump.contains("productRouteActivated=false"))
            }
        }
    }

    private fun solidColorFullscreenWgsl(): String =
        """
            struct Uniforms {
                color: vec4f,
            };

            @group(0) @binding(0) var<uniform> uniforms: Uniforms;

            @vertex
            fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
                let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
                let y = f32(idx & 2u) * 2.0 - 1.0;
                return vec4f(x, y, 0.0, 1.0);
            }

            @fragment
            fn fs_main() -> @location(0) vec4f {
                return uniforms.color;
            }
        """.trimIndent()

    private fun solidColorPayloadWgsl(): String =
        """
            struct Payload {
                color: vec4f,
                padding0: vec4f,
                padding1: vec4f,
                padding2: vec4f,
            };

            @group(0) @binding(0) var<uniform> payload: Payload;

            @vertex
            fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
                let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
                let y = f32(idx & 2u) * 2.0 - 1.0;
                return vec4f(x, y, 0.0, 1.0);
            }

            @fragment
            fn fs_main() -> @location(0) vec4f {
                return payload.color;
            }
        """.trimIndent()

    private fun nonSimpleFullscreenWgsl(): String =
        """
            struct Uniforms {
                color: vec4f,
            };

            @group(0) @binding(0) var<uniform> uniforms: Uniforms;

            struct VertexOut {
                @builtin(position) position: vec4f,
            };

            @vertex
            fn vs_main(@builtin(vertex_index) idx: u32) -> VertexOut {
                let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
                let y = f32(idx & 2u) * 2.0 - 1.0;
                return VertexOut(vec4f(x, y, 0.0, 1.0));
            }

            @fragment
            fn fs_main(in: VertexOut) -> @location(0) vec4f {
                let tint = select(0.35, 1.0, in.position.x >= 0.0);
                return vec4f(uniforms.color.rgb * tint, uniforms.color.a);
            }
        """.trimIndent()

    private fun singleTextureWgsl(): String =
        """
            struct Uniforms {
                color: vec4f,
            };

            @group(0) @binding(0) var<uniform> uniforms: Uniforms;
            @group(1) @binding(1) var source: texture_2d<f32>;
            @group(1) @binding(2) var sourceSampler: sampler;

            @vertex
            fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
                let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
                let y = f32(idx & 2u) * 2.0 - 1.0;
                return vec4f(x, y, 0.0, 1.0);
            }

            @fragment
            fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
                let dimensions = textureDimensions(source);
                let uv = vec2f(coord.x / f32(dimensions.x), coord.y / f32(dimensions.y));
                return textureSample(source, sourceSampler, uv) + uniforms.color * 0.0;
            }
        """.trimIndent()

    private fun multiTextureWgsl(textureCount: Int): String {
        require(textureCount in 2..3)
        val textureDeclarations = (1..textureCount).joinToString("\n") { index ->
            "@group(1) @binding(${index * 2 - 1}) var texture$index: texture_2d<f32>;\n" +
                "@group(1) @binding(${index * 2}) var sampler$index: sampler;"
        }
        val samples = (1..textureCount).joinToString(" + ") { index ->
            "textureSample(texture$index, sampler$index, uv)"
        }
        return """
            struct Uniforms {
                color: vec4f,
            };

            @group(0) @binding(0) var<uniform> uniforms: Uniforms;
            $textureDeclarations

            @vertex
            fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
                let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
                let y = f32(idx & 2u) * 2.0 - 1.0;
                return vec4f(x, y, 0.0, 1.0);
            }

            @fragment
            fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
                let dims = textureDimensions(texture1);
                let uv = vec2f(coord.x / f32(dims.x), coord.y / f32(dims.y));
                return ($samples) / ${textureCount.toFloat()} + uniforms.color * 0.0;
            }
        """.trimIndent()
    }

    private fun stencilWriteWgsl(): String =
        """
            @vertex
            fn vs_main(@location(0) position: vec2f) -> @builtin(position) vec4f {
                return vec4f(position, 0.0, 1.0);
            }

            @fragment
            fn fs_main() -> @location(0) vec4f {
                return vec4f(0.0);
            }
        """.trimIndent()

    private fun stencilTestWgsl(): String =
        """
            struct Uniforms {
                color: vec4f,
            };

            @group(0) @binding(0) var<uniform> uniforms: Uniforms;

            @vertex
            fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
                let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
                let y = f32(idx & 2u) * 2.0 - 1.0;
                return vec4f(x, y, 0.0, 1.0);
            }

            @fragment
            fn fs_main() -> @location(0) vec4f {
                return uniforms.color;
            }
        """.trimIndent()

    private fun fullscreenWgslWithoutFragmentEntry(): String =
        """
            struct Uniforms {
                color: vec4f,
            };

            @group(0) @binding(0) var<uniform> uniforms: Uniforms;

            @vertex
            fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
                let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
                let y = f32(idx & 2u) * 2.0 - 1.0;
                return vec4f(x, y, 0.0, 1.0);
            }

            @fragment
            fn missing_fragment_entry() -> @location(0) vec4f {
                return uniforms.color;
            }
        """.trimIndent()

    private fun pixelAt(rgba: ByteArray, width: Int, x: Int, y: Int): ByteArray {
        val offset = ((y * width) + x) * 4
        return rgba.copyOfRange(offset, offset + 4)
    }

    private fun uniformPayloadBlock(): GPUUniformPayloadBlock {
        val buffer = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putFloat(1f)
        buffer.putFloat(0f)
        buffer.putFloat(0f)
        buffer.putFloat(1f)
        return GPUUniformPayloadBlock(
            fingerprint = GPUPayloadFingerprint("uniform-fingerprint-smoke"),
            packingPlanHash = "solid-rect-layout-v1",
            byteSize = 64L,
            zeroedPadding = true,
            scope = "pass-a",
            bytes = buffer.array().map { byte -> byte.toInt() and 0xff },
        )
    }

    private fun solidColorUniformBytes(red: Float, green: Float, blue: Float, alpha: Float): ByteArray {
        val buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putFloat(red)
        buffer.putFloat(green)
        buffer.putFloat(blue)
        buffer.putFloat(alpha)
        return buffer.array()
    }

    private fun gradientMaterialization(label: String): GPUResourceMaterializationDecision.Materialized {
        val uniformBlock = uniformPayloadBlock()
        val request = payloadMaterializationRequest(
            uniformBlock = uniformBlock,
            resourceDescriptorLabel = "uniform:gradient-material-payload",
            layoutHash = "layout:linear-gradient-material-block:v1",
            resourceLabel = label,
        )
        return assertIs<GPUResourceMaterializationDecision.Materialized>(
            ValidatingPayloadResourceProvider().materializePayloadBindings(
                request = request,
                context = GPUTargetPreparationContext(
                    targetId = "root-target",
                    frameId = "frame-1",
                    deviceGeneration = 1,
                    budgetClass = "smoke-test",
                ),
            ),
        )
    }

    private fun payloadMaterializationRequest(
        uniformBlock: GPUUniformPayloadBlock,
        resourceDescriptorLabel: String = "uniform:solid-payload",
        layoutHash: String = "layout-solid-v1",
        resourceLabel: String = "solid-fill",
    ): GPUPayloadMaterializationRequest =
        GPUPayloadMaterializationRequest(
            targetId = "root-target",
            packetId = "packet-1",
            taskIds = listOf("task-payload-upload"),
            resourcePlanLabels = listOf("payload-materialization:$resourceLabel"),
            uniformBlock = uniformBlock,
            uniformSlot = GPUUniformPayloadSlot(
                slotId = GPUPayloadSlotID("pass-a:uniform:0"),
                fingerprint = uniformBlock.fingerprint,
                byteOffset = 0L,
            ),
            resourceBlock = GPUResourceBindingBlock(
                fingerprint = GPUPayloadFingerprint("resource-fingerprint-smoke"),
                bindingPlanHash = layoutHash,
                bindingCount = 1,
                resourceDescriptorLabels = listOf(resourceDescriptorLabel),
                dynamicOffsets = listOf(0L),
            ),
            resourceSlot = GPUResourceBindingSlot(
                slotId = GPUPayloadSlotID("pass-a:resource:0"),
                fingerprint = GPUPayloadFingerprint("resource-fingerprint-smoke"),
                bindingIndex = 0,
            ),
            uploadPlan = GPUPayloadUploadPlan(
                planHash = "upload-solid-v1",
                byteRanges = listOf(0L..63L),
                stagingScope = "pass-a-staging",
                budgetClass = "smoke-test",
                beforeUseToken = "before-draw-1",
            ),
            reflectedBindingLayoutHash = layoutHash,
            deviceGeneration = 1,
            payloadGeneration = 1L,
            alignmentBytes = 256L,
            uploadBudgetBytes = 256L,
            uploadCapabilityAvailable = true,
            maxDynamicOffsets = 1,
            requiredUniformUsageLabels = setOf("copy_dst", "uniform"),
            availableUniformUsageLabels = setOf("copy_dst", "uniform"),
        )

    /** Builds implementation-specific audit tokens without exposing them in test names. */
    private fun forbiddenImplementationTokenUpperForAudit(): String = "W" + "GPU"

    private fun forbiddenImplementationTokenLowerForAudit(): String = "w" + "gpu"
}
