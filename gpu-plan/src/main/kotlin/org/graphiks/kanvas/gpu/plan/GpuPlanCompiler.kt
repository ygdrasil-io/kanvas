package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneSnapshot

/** Compiles an immutable Scene IR snapshot into a handle-free render graph. */
public interface GpuPlanCompiler {
    /**
     * Classifies scene and target semantics that do not require a physical device.
     *
     * Returning null means the scene is within this compiler's semantic scope and
     * capability-aware planning may continue. A non-null result is final and must
     * be returned before a caller acquires a GPU runtime.
     */
    public fun classify(
        scene: SceneSnapshot,
        target: RenderTargetDescriptor,
    ): RenderPlanResult<Nothing>? = null

    public fun plan(
        scene: SceneSnapshot,
        target: RenderTargetDescriptor,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): RenderPlanResult<RenderGraph>
}
