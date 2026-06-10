package org.skia.effects.runtime

import org.skia.foundation.SkBlender
import org.graphiks.math.SkColor4f
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Concrete [SkBlender] backing a registered [SkRuntimeImpl] of
 * blender kind. Phase D2.2 binding — wraps the impl so it slots
 * into `paint.blender` like the existing
 * [org.skia.foundation.SkBlendModeBlender] (D2.0) or
 * [org.skia.foundation.SkArithmeticBlender] (D2.0 via
 * [org.skia.foundation.SkBlenders.Arithmetic]).
 *
 * **Per-pixel evaluation** : [blend] forwards `(src, dst)` to
 * [SkRuntimeImpl.shade] with `coords = null`, `srcColor = src`,
 * `dstColor = dst`. The impl's children (rare but allowed by the
 * SkSL spec for blender effects) are resolved via the pre-built
 * [ChildResolver] array.
 *
 * Constructed exclusively by [SkRuntimeEffect.makeBlender] ; the
 * constructor is `internal`.
 */
public class SkRuntimeBlender internal constructor(
    private val impl: SkRuntimeImpl,
    public val runtimeEffectDescriptor: SkRuntimeEffectDescriptor?,
    private val uniformsBuffer: ByteBuffer,
    private val childResolvers: Array<ChildResolver>,
) : SkBlender() {

    override fun blend(src: SkColor4f, dst: SkColor4f): SkColor4f {
        val u = uniformsBuffer.duplicate().order(ByteOrder.nativeOrder())
        return impl.shade(
            coords = null,
            srcColor = src,
            dstColor = dst,
            uniforms = u,
            children = childResolvers,
        )
    }
}
