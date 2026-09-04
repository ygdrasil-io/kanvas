package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.gpu.renderer.planning.GpuRenderContext
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanSurfaceExecutor
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanSurfacePlanResult
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanSurfaceReadyToken
import org.graphiks.kanvas.gpu.renderer.planning.GpuPlanSurfaceSubmitResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneSnapshot

/** Lazily owns the process-scoped GPU plan context; individual sessions serialize their own frames. */
internal object GPUPlanRenderContextOwner {
    @Volatile private var context: GpuRenderContext? = null

    fun plan(
        scene: SceneSnapshot,
        target: RenderTargetDescriptor,
        frameLocalBudgetBytes: Long,
    ): GpuPlanSurfacePlanResult = executor().plan(scene, target, frameLocalBudgetBytes)

    fun submit(token: GpuPlanSurfaceReadyToken): GpuPlanSurfaceSubmitResult = executor().submit(token)

    private fun context(): GpuRenderContext = context ?: synchronized(this) {
        context ?: GpuRenderContext.createProduction().also { context = it }
    }

    private fun executor(): GpuPlanSurfaceExecutor = context().planSurfaceExecutor()
}
