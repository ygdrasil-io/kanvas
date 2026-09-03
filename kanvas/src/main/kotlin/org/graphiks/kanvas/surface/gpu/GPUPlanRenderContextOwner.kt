package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.renderer.planning.GpuRenderContext
import org.graphiks.kanvas.gpu.renderer.planning.GpuW3SurfaceExecutor
import org.graphiks.kanvas.gpu.renderer.planning.GpuW3SurfacePlanResult
import org.graphiks.kanvas.gpu.renderer.planning.GpuW3SurfaceReadyToken
import org.graphiks.kanvas.gpu.renderer.planning.GpuW3SurfaceSubmitResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneSnapshot

/** Lazily owns the process-scoped W3 context; individual sessions serialize their own frames. */
internal object GPUPlanRenderContextOwner {
    @Volatile private var context: GpuRenderContext? = null

    fun plan(
        scene: SceneSnapshot,
        target: RenderTargetDescriptor,
        frameLocalBudgetBytes: Long,
    ): GpuW3SurfacePlanResult = executor().plan(scene, target, frameLocalBudgetBytes)

    fun submit(token: GpuW3SurfaceReadyToken): GpuW3SurfaceSubmitResult = executor().submit(token)

    private fun context(): GpuRenderContext = context ?: synchronized(this) {
        context ?: GpuRenderContext.createProduction().also { context = it }
    }

    private fun executor(): GpuW3SurfaceExecutor = context().w3SurfaceExecutor()
}
