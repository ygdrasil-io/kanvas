package org.skia.effects.runtime

import org.graphiks.math.SkColor4f
import org.skia.foundation.SkColorFilter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Concrete [SkColorFilter] backing a registered [SkRuntimeImpl] of
 * color-filter kind. Phase D2.2 binding — wraps the impl so it
 * slots into `paint.colorFilter` like any other filter subtype.
 *
 * **Per-call evaluation** : [filterColor4f] forwards the input
 * colour to [SkRuntimeImpl.shade] with `coords = null`, `srcColor =
 * input`, `dstColor = null`. The impl's children (typically other
 * color filters or shader sources for compose-style filters) are
 * resolved via the pre-built [ChildResolver] array.
 *
 * Constructed exclusively by [SkRuntimeEffect.makeColorFilter] ;
 * the constructor is `internal`.
 */
public class SkRuntimeColorFilter internal constructor(
    private val impl: SkRuntimeImpl,
    public val runtimeEffectDescriptor: SkRuntimeEffectDescriptor?,
    private val uniformsBuffer: ByteBuffer,
    private val childResolvers: Array<ChildResolver>,
) : SkColorFilter() {
    public fun runtimeEffectUniformBytes(): ByteArray {
        val dup = uniformsBuffer.duplicate()
        dup.position(0)
        val out = ByteArray(dup.capacity())
        dup.get(out)
        return out
    }

    override fun filterColor4f(src: SkColor4f): SkColor4f {
        val u = uniformsBuffer.duplicate().order(ByteOrder.nativeOrder())
        return impl.shade(
            coords = null,
            srcColor = src,
            dstColor = null,
            uniforms = u,
            children = childResolvers,
        )
    }

    override fun isAlphaUnchanged(): Boolean {
        // The runtime impl can declare via flags whether it
        // preserves alpha. SkRuntimeEffect.kAlphaUnchanged_Flag
        // mirrors upstream's `SkRuntimeEffect::Flags::kAlphaUnchanged_Flag`.
        return (impl.flags and SkRuntimeEffect.kAlphaUnchanged_Flag) != 0
    }
}
