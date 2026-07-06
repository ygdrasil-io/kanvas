package org.graphiks.kanvas.gpu.renderer.execution

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadUploadPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.resources.GPUPayloadMaterializationRequest
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
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GPUBackendRuntimeWgpuSmokeTest {
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

        assertEquals(GPUDeviceGeneration(7L), windowSurfaceDeviceGeneration(windowRuntimeOrdinal = 7L))
        assertEquals(
            "wgpu-window-surface-7-appkitmetallayer-640x480",
            windowSurfaceTargetId(windowRuntimeOrdinal = 7L, binding = binding),
        )
    }

    @Test
    fun `offscreen target helper derives deterministic unique target id per session and target`() {
        val request = GPUOffscreenTargetRequest(
            width = 320,
            height = 180,
            colorFormat = "rgba8unorm",
        )

        assertEquals(GPUDeviceGeneration(3L), sessionDeviceGeneration(sessionOrdinal = 3L))
        assertEquals(
            "wgpu-offscreen-3-5-320x180-rgba8unorm",
            offscreenTargetId(
                sessionOrdinal = 3L,
                offscreenTargetOrdinal = 5L,
                request = request,
            ),
        )
    }

    @Test
    fun `backend runtime exposes conservative GPU capabilities when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        runtime!!.use { session ->
            val capabilities = session.capabilities
                ?: error("GPU backend session should expose capabilities")
            val limits = capabilities.limits
                ?: error("GPU backend session should expose limits")
            val facts = limits.capabilityFacts(evidenceLabel = "runtime")

            assertEquals("GPU", capabilities.implementation.facadeName)
            assertEquals("wgpu4k", capabilities.implementation.implementationName)
            assertEquals(8192L, limits.maxTextureDimension2D)
            assertEquals(256L, limits.copyBytesPerRowAlignment)
            assertEquals(256L, limits.minUniformBufferOffsetAlignment)
            assertEquals("runtime.conservative", limits.source)
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

            assertTrue(after.renderPasses - before.renderPasses >= 2L)
            assertTrue(after.offscreenPasses - before.offscreenPasses >= 2L)
            assertEquals(0L, after.windowPasses - before.windowPasses)
            assertTrue(after.submissions - before.submissions >= 2L)
            assertTrue(after.buffersCreated - before.buffersCreated >= 3L)
            assertTrue(after.texturesCreated - before.texturesCreated >= 4L)
            assertTrue(after.bindGroupsCreated - before.bindGroupsCreated >= 2L)
            assertTrue(after.queueWrites - before.queueWrites >= 2L)
            assertTrue(dump.contains("gpu-runtime.telemetry"))
            assertTrue(!dump.contains("@"))
        }
    }

    @Test
    fun `backend runtime offscreen encode and read rgba when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "WGPU backend unavailable in current environment")

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
                assertTrue(
                    Regex("""payload-slab\.batch\.plan .* frame=offscreen-\d+-\d+-frame-\d+ """).containsMatchIn(dump),
                    "payload slab plan dump should include per-encode offscreen frame ordinal",
                )
                assertTrue(dump.contains("payload-slab.batch.plan source=fullscreen-uniform-pass target=payload-target-"))
                assertTrue(dump.contains("payload-slab.batch.slot source=fullscreen-uniform-pass slot=fullscreen-packet-0:fullscreen-pass:uniform:0:fullscreen-pass:resource:0"))
                assertTrue(!dump.contains("@"))
                assertTrue(!dump.contains("WGPU"))
                assertTrue(!dump.contains("wgpu"))
                assertTrue(!dump.contains("0x"))
            }
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
    fun `backend runtime records WGPU execution cache hit miss and create telemetry when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "WGPU backend unavailable in current environment")

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
    fun `backend runtime uploads uniform payload bytes and binds them when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "WGPU backend unavailable in current environment")

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
    fun `backend runtime records WGPU execution cache failure telemetry when backend is available`() {
        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "WGPU backend unavailable in current environment")

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

    private fun payloadMaterializationRequest(uniformBlock: GPUUniformPayloadBlock): GPUPayloadMaterializationRequest =
        GPUPayloadMaterializationRequest(
            targetId = "root-target",
            packetId = "packet-1",
            taskIds = listOf("task-payload-upload"),
            resourcePlanLabels = listOf("payload-materialization:solid-fill"),
            uniformBlock = uniformBlock,
            uniformSlot = GPUUniformPayloadSlot(
                slotId = GPUPayloadSlotID("pass-a:uniform:0"),
                fingerprint = uniformBlock.fingerprint,
                byteOffset = 0L,
            ),
            resourceBlock = GPUResourceBindingBlock(
                fingerprint = GPUPayloadFingerprint("resource-fingerprint-smoke"),
                bindingPlanHash = "layout-solid-v1",
                bindingCount = 1,
                resourceDescriptorLabels = listOf("uniform:solid-payload"),
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
            reflectedBindingLayoutHash = "layout-solid-v1",
            deviceGeneration = 1,
            payloadGeneration = 1L,
            alignmentBytes = 256L,
            uploadBudgetBytes = 256L,
            uploadCapabilityAvailable = true,
            maxDynamicOffsets = 1,
            requiredUniformUsageLabels = setOf("copy_dst", "uniform"),
            availableUniformUsageLabels = setOf("copy_dst", "uniform"),
        )
}
