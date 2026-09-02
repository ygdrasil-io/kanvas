package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneSnapshot

/** Compiles an immutable Scene IR snapshot into a handle-free render graph. */
public interface GpuPlanCompiler {
    public fun plan(
        scene: SceneSnapshot,
        target: RenderTargetDescriptor,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): RenderPlanResult<RenderGraph>
}
