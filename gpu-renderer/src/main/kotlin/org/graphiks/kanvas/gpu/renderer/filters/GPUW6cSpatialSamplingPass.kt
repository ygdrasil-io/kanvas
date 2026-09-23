package org.graphiks.kanvas.gpu.renderer.filters

import org.graphiks.kanvas.gpu.plan.FilterPassOperationV1
import org.graphiks.kanvas.gpu.plan.SpatialSamplingV1
import org.graphiks.kanvas.render.ir.TileMode
import org.graphiks.math.geometry.RectI32

/** Native consumer for an already sealed W6c spatial payload; it owns no bounds calculation. */
internal object GPUW6cSpatialSamplingPass {
    internal fun fragment(operation: FilterPassOperationV1): String = when (operation) {
        is FilterPassOperationV1.Crop -> sampler(operation.sampling, operation.sampling.copySourceInputTargetLocalI32(), operation.tileMode, false)
        is FilterPassOperationV1.Offset -> sampler(operation.sampling, operation.sampling.copySourceInputTargetLocalI32(), TileMode.DECAL, false)
        is FilterPassOperationV1.Tile -> sampler(operation.sampling, operation.copySourceInputTargetLocalI32(), TileMode.REPEAT, true)
        else -> error("W6c spatial sampler received ${operation.kind}.")
    }

    private fun sampler(sampling: SpatialSamplingV1, sampleDomain: RectI32, tileMode: TileMode, periodic: Boolean): String {
        val clip = sampling.copyClipOutputTargetLocalF64()
        val offset = sampling.copyOutputToInputOffsetTargetLocalF64()
        val repeat = periodic || tileMode == TileMode.REPEAT || tileMode == TileMode.MIRROR
        val coordinate = if (repeat) periodicCoordinate(sampleDomain, offset, tileMode) else """
            var source_position = vec2<i32>(floor(position.xy + vec2<f32>(${offset.x}f, ${offset.y}f)));
            ${if (tileMode == TileMode.CLAMP) "source_position = clamp(source_position, vec2<i32>(${sampleDomain.left}, ${sampleDomain.top}), vec2<i32>(${sampleDomain.right - 1}, ${sampleDomain.bottom - 1}));" else ""}
        """
        val outside = if (tileMode == TileMode.DECAL || !repeat && tileMode != TileMode.CLAMP) """
            if (source_position.x < ${sampleDomain.left} || source_position.y < ${sampleDomain.top} ||
                source_position.x >= ${sampleDomain.right} || source_position.y >= ${sampleDomain.bottom}) { return vec4<f32>(0.0); }
        """ else ""
        return """
            @group(0) @binding(0) var w6c_source: texture_2d<f32>;
            @fragment fn fs_main(@builtin(position) position: vec4<f32>) -> @location(0) vec4<f32> {
                if (position.x < ${clip.left}f || position.y < ${clip.top}f || position.x >= ${clip.right}f || position.y >= ${clip.bottom}f) {
                    return vec4<f32>(0.0);
                }
                $coordinate
                $outside
                let source_extent = vec2<i32>(textureDimensions(w6c_source));
                if (source_position.x < 0 || source_position.y < 0 || source_position.x >= source_extent.x || source_position.y >= source_extent.y) {
                    return vec4<f32>(0.0);
                }
                return textureLoad(w6c_source, source_position, 0);
            }
        """
    }

    private fun periodicCoordinate(sampleDomain: RectI32, offset: org.graphiks.math.vector.Vector2F64, tileMode: TileMode): String {
        val raw = "vec2<i32>(floor(position.xy + vec2<f32>(${offset.x}f, ${offset.y}f))) - vec2<i32>(${sampleDomain.left}, ${sampleDomain.top})"
        return if (tileMode == TileMode.MIRROR) """
            let period = vec2<i32>(${sampleDomain.width()}, ${sampleDomain.height()});
            let raw = $raw;
            let mirrored_period = period * 2;
            let wrapped = vec2<i32>(
                ((raw.x % mirrored_period.x) + mirrored_period.x) % mirrored_period.x,
                ((raw.y % mirrored_period.y) + mirrored_period.y) % mirrored_period.y
            );
            let source_position = vec2<i32>(
                select(wrapped.x, mirrored_period.x - 1 - wrapped.x, wrapped.x >= period.x) + ${sampleDomain.left},
                select(wrapped.y, mirrored_period.y - 1 - wrapped.y, wrapped.y >= period.y) + ${sampleDomain.top}
            );
        """ else """
            let period = vec2<i32>(${sampleDomain.width()}, ${sampleDomain.height()});
            let raw = $raw;
            let source_position = vec2<i32>(
                ((raw.x % period.x) + period.x) % period.x + ${sampleDomain.left},
                ((raw.y % period.y) + period.y) % period.y + ${sampleDomain.top}
            );
        """
    }
}
