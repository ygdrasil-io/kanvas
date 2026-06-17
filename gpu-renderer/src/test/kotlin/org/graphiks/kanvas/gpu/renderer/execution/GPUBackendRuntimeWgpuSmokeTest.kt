package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheTelemetry
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GPUBackendRuntimeWgpuSmokeTest {
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
}
