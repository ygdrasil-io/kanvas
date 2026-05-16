package org.skia.gpu.webgpu

import ffi.LibraryLoader
import io.ygdrasil.webgpu.GLFWContext
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlinx.coroutines.runBlocking

/**
 * G0 bootstrap — "headless" WebGPU context for tests.
 *
 * wgpu4k 0.2.0 requires a `NativeSurface` for
 * [io.ygdrasil.webgpu.WGPU.requestAdapter] (confirmed by reading
 * wgpu4k commonNativeMain `WGPU.kt` — `surface.handler` is
 * dereferenced unconditionally). The toolkit's
 * [glfwContextRenderer] with `deferredRendering = true` is the
 * closest match: it creates a `GLFW_VISIBLE = GLFW_FALSE` window
 * (never mapped to the display), derives a `NativeSurface` from it,
 * and wires up the adapter + device.
 *
 * On macOS, GLFW *requires* the AppKit main thread (thread 0). The
 * Gradle test JVM is therefore configured with `-XstartOnFirstThread`
 * (see [kanvas-skia/build.gradle.kts]) so that the JVM's main thread
 * is the OS' thread 0. JUnit 5's default executor runs tests on the
 * calling thread, so the test ends up on thread 0 too.
 *
 * Phase G2+ may need a non-macOS path. R2 in the master plan flagged
 * this; revisit when Linux CI is wired up (likely Wayland surface
 * via xvfb or a similar offscreen wrapper).
 */
public class WebGpuContext private constructor(
    private val glfw: GLFWContext,
) : AutoCloseable {

    public val device: GPUDevice get() = glfw.wgpuContext.device
    public val queue: GPUQueue get() = device.queue

    override fun close() {
        glfw.close()
    }

    public companion object {
        /**
         * Try to bring up a headless context. Returns `null` on any
         * failure (test code should pair this with
         * [org.junit.jupiter.api.Assumptions.assumeTrue] to skip
         * rather than fail). [Throwable] is caught on purpose:
         * missing platform binaries surface as `UnsatisfiedLinkError`,
         * and GLFW's main-thread misconfiguration aborts the JVM with
         * an `IllegalStateException`.
         */
        public fun createOrNull(): WebGpuContext? = try {
            // wgpu4k's `io.ygdrasil.wgpu.Functions` static-initializes
            // method handles by looking up symbols in the loaded
            // wgpu-native library. Without an explicit
            // `LibraryLoader.load()` call first, the lookup fails with
            // `UnsatisfiedLinkError: unresolved symbol wgpuCreateInstance`.
            // The `glfwContextRenderer` path does NOT trigger this
            // loader transitively — we must call it explicitly.
            LibraryLoader.load()
            val glfw = runBlocking {
                glfwContextRenderer(
                    width = 1,
                    height = 1,
                    title = "kanvas-skia-gpu-headless",
                    deferredRendering = true,
                )
            }
            val info = glfw.wgpuContext.adapter.info
            println(
                "[WebGpuContext] adapter=${info.vendor}/${info.device} " +
                    "arch=${info.architecture} desc=${info.description}"
            )
            WebGpuContext(glfw)
        } catch (t: Throwable) {
            println("[WebGpuContext] init failed — ${t::class.simpleName}: ${t.message}")
            null
        }
    }
}
