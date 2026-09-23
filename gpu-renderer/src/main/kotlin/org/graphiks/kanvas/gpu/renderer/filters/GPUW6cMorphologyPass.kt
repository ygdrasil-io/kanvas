package org.graphiks.kanvas.gpu.renderer.filters

import org.graphiks.kanvas.gpu.plan.FilterAxisV1
import org.graphiks.kanvas.gpu.plan.FilterPassOperationV1

/** Native consumer for one fully frozen W6c morphology axis; it creates no geometry or targets. */
internal object GPUW6cMorphologyPass {
    internal fun fragment(operation: FilterPassOperationV1.Morphology): String {
        val radiusI32 = if (operation.axis == FilterAxisV1.X) operation.radiusXTexelsI32 else operation.radiusYTexelsI32
        val axis = if (operation.axis == FilterAxisV1.X) "vec2<i32>(tap, 0)" else "vec2<i32>(0, tap)"
        val offset = operation.sampling.copyOutputToInputOffsetTargetLocalI32()
        val known = operation.sampling.copyKnownContentInputTargetLocalI32()
        val initial = if (operation.morphologyKind == FilterPassOperationV1.Morphology.Kind.DILATE) "vec4<f32>(0.0)" else "vec4<f32>(1.0)"
        val combine = if (operation.morphologyKind == FilterPassOperationV1.Morphology.Kind.DILATE) "max(value, sample)" else "min(value, sample)"
        return """
            @group(0) @binding(0) var w6c_morphology_source: texture_2d<f32>;
            fn w6c_morphology_sample(coordinate: vec2<i32>) -> vec4<f32> {
                if (coordinate.x < ${known.left} || coordinate.y < ${known.top} ||
                    coordinate.x >= ${known.right} || coordinate.y >= ${known.bottom}) {
                    return vec4<f32>(0.0);
                }
                return textureLoad(w6c_morphology_source, coordinate, 0);
            }
            @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                let center = vec2<i32>(position.xy) + vec2<i32>(${offset.x}, ${offset.y});
                var value = $initial;
                for (var tap: i32 = -$radiusI32; tap <= $radiusI32; tap = tap + 1) {
                    let sample = w6c_morphology_sample(center + $axis);
                    value = $combine;
                }
                return value;
            }
        """
    }
}
