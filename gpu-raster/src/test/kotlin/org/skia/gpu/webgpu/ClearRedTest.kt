package org.skia.gpu.webgpu

import io.ygdrasil.webgpu.Color
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.VertexState
import io.ygdrasil.webgpu.beginRenderPass
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test

/**
 * Phase G0 acceptance test — proves the wgpu4k native stack loads,
 * a WGSL shader compiles, an offscreen render pass runs, and the
 * resulting pixels can be read back to the JVM heap. If this test
 * passes on a fresh checkout, the WebGPU bootstrap is good and we
 * can move on to G1 (`SkDevice` extraction + `BigRectGM` port).
 */
class ClearRedTest {

    @Test
    fun `headless WebGPU clears a 64x64 texture to opaque red`() {
        val context = WebGpuContext.createOrNull()
        Assumptions.assumeTrue(
            context != null,
            "No WebGPU adapter available — skipping GPU bootstrap test",
        )
        context!!.use { ctx ->
            HeadlessTarget(ctx, WIDTH, HEIGHT, GPUTextureFormat.RGBA8Unorm).use { target ->
                val pixels = runBlocking {
                    renderClearRed(ctx, target)
                    target.readPixels()
                }
                assertAllPixelsOpaqueRed(pixels)
            }
        }
    }

    private fun renderClearRed(ctx: WebGpuContext, target: HeadlessTarget) {
        val wgsl = ClearRedTest::class.java.classLoader
            .getResource("shaders/clear_red.wgsl")
            ?.readText()
            ?: error("shaders/clear_red.wgsl not on classpath")

        val shader = ctx.device.createShaderModule(ShaderModuleDescriptor(code = wgsl))
        val pipeline = ctx.device.createRenderPipeline(
            RenderPipelineDescriptor(
                vertex = VertexState(module = shader, entryPoint = "vs_main"),
                fragment = FragmentState(
                    module = shader,
                    entryPoint = "fs_main",
                    targets = listOf(ColorTargetState(format = target.format)),
                ),
            ),
        )

        val view = target.colorTexture.createView()
        val encoder = ctx.device.createCommandEncoder()

        // The clearValue is set to *black* on purpose: that way, if the
        // shader silently never ran, the readback would come back black,
        // not red, and the assertion would catch it. With Clear=red and
        // a no-op shader, we'd get red for the wrong reason.
        encoder.beginRenderPass(
            RenderPassDescriptor(
                colorAttachments = listOf(
                    RenderPassColorAttachment(
                        view = view,
                        loadOp = GPULoadOp.Clear,
                        clearValue = Color(0.0, 0.0, 0.0, 1.0),
                        storeOp = GPUStoreOp.Store,
                    ),
                ),
            ),
        ) {
            setPipeline(pipeline)
            draw(VERTEX_COUNT)
            end()
        }

        target.encodeCopyToStaging(encoder)
        ctx.queue.submit(listOf(encoder.finish()))
    }

    private fun assertAllPixelsOpaqueRed(pixels: ByteArray) {
        assertEquals(WIDTH * HEIGHT * 4, pixels.size, "pixel byte count")
        for (i in 0 until WIDTH * HEIGHT) {
            val base = i * 4
            val r = pixels[base].toInt() and 0xFF
            val g = pixels[base + 1].toInt() and 0xFF
            val b = pixels[base + 2].toInt() and 0xFF
            val a = pixels[base + 3].toInt() and 0xFF
            if (r != 255 || g != 0 || b != 0 || a != 255) {
                val x = i % WIDTH
                val y = i / WIDTH
                error("pixel ($x,$y) expected RGBA=(255,0,0,255) but got ($r,$g,$b,$a)")
            }
        }
    }

    private companion object {
        const val WIDTH: Int = 64
        const val HEIGHT: Int = 64
        const val VERTEX_COUNT: UInt = 3u
    }
}
