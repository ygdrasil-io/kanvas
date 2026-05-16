package org.skia.effects.runtime.effects

import org.skia.effects.runtime.ChildResolver
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectDispatch
import org.skia.effects.runtime.SkRuntimeImpl
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkPoint
import java.nio.ByteBuffer
import kotlin.math.sin

/**
 * Hand-ported runtime shader effects from the
 * `gm/runtimeimagefilter.cpp` GM file — both `rtif_distort` and
 * `rtif_unsharp` shader programs.
 *
 * **GMs unblocked** :
 *  - [`RtifDistortGM`](../../../../tests/RtifDistortGM.kt)
 *  - [`RtifUnsharpGM`](../../../../tests/RtifUnsharpGM.kt)
 *
 * **Why a separate cluster** : both effects target the
 * `SkImageFilters::RuntimeShader(...)` factory (i.e. an image-filter
 * DAG node) rather than the shader-of-a-paint factory. They share the
 * runtime-effect impl/dispatch machinery but the consuming surface
 * differs : the [org.skia.effects.runtime.SkRuntimeImageFilter] resolves
 * children as filtered images, not local-coord shaders.
 */
public object SkBuiltinShaderEffectsRtifImageFilters {

    init { registerAll() }

    public fun registerAll() {
        SkRuntimeEffectDispatch.register(RTIF_DISTORT_SKSL) { RtifDistortImpl }
        SkRuntimeEffectDispatch.register(RTIF_UNSHARP_SKSL) { RtifUnsharpImpl }
    }

    // ─── SkSL sources (verbatim from upstream) ───────────────────────

    /** From `gm/runtimeimagefilter.cpp:30-36`. Single-shader child
     *  named `child` ; warps `coord.x` by a sine wave keyed off
     *  `coord.y`. */
    public const val RTIF_DISTORT_SKSL: String = """
        uniform shader child;
        half4 main(float2 coord) {
            coord.x += sin(coord.y / 3) * 4;
            return child.eval(coord);
        }
    """

    /** From `gm/runtimeimagefilter.cpp:84-92`. Two-shader children :
     *  `content` (raw input) and `blurred` (blurred input). Computes
     *  the canonical unsharp-mask formula
     *  `out = c + (c - b) * 4`. */
    public const val RTIF_UNSHARP_SKSL: String = """
        uniform shader content;
        uniform shader blurred;
        vec4 main(vec2 coord) {
            vec4 c = content.eval(coord);
            vec4 b = blurred.eval(coord);
            return c + (c - b) * 4;
        }
    """

    // ─── Concrete impls ──────────────────────────────────────────────

    /**
     * `rtif_distort` — single-child shader, no uniforms. Re-implements
     * the SkSL `coord.x += sin(coord.y / 3) * 4 ; return child.eval(coord)`
     * verbatim.
     *
     * The `child` slot is fed by the layer's source image (since the
     * upstream [`SkImageFilters::RuntimeShader`] is invoked with
     * `input = nullptr` → bind directly to the source).
     */
    public object RtifDistortImpl : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
        override val children: List<SkRuntimeEffect.Child> = listOf(
            SkRuntimeEffect.Child("child", SkRuntimeEffect.ChildType.kShader, 0),
        )
        override val flags: Int = SkRuntimeEffect.kAllowShader_Flag or
            SkRuntimeEffect.kUsesSampleCoords_Flag

        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f {
            val p = coords ?: return SkColor4f.kBlack
            val cx = p.fX + sin(p.fY / 3f) * 4f
            val cy = p.fY
            return (children[0] as ChildResolver.Shader).sample(SkPoint(cx, cy))
        }
    }

    /**
     * `rtif_unsharp` — two-child shader, no uniforms. Reads `content`
     * and `blurred` at the same coord, returns `c + (c - b) * 4`.
     */
    public object RtifUnsharpImpl : SkRuntimeImpl {
        override val uniforms: List<SkRuntimeEffect.Uniform> = emptyList()
        override val children: List<SkRuntimeEffect.Child> = listOf(
            SkRuntimeEffect.Child("content", SkRuntimeEffect.ChildType.kShader, 0),
            SkRuntimeEffect.Child("blurred", SkRuntimeEffect.ChildType.kShader, 1),
        )
        override val flags: Int = SkRuntimeEffect.kAllowShader_Flag or
            SkRuntimeEffect.kUsesSampleCoords_Flag

        override fun shade(
            coords: SkPoint?,
            srcColor: SkColor4f?,
            dstColor: SkColor4f?,
            uniforms: ByteBuffer,
            children: Array<ChildResolver>,
        ): SkColor4f {
            val xy = coords ?: SkPoint(0f, 0f)
            val c = (children[0] as ChildResolver.Shader).sample(xy)
            val b = (children[1] as ChildResolver.Shader).sample(xy)
            // out = c + (c - b) * 4   per channel.
            return SkColor4f(
                fR = c.fR + (c.fR - b.fR) * 4f,
                fG = c.fG + (c.fG - b.fG) * 4f,
                fB = c.fB + (c.fB - b.fB) * 4f,
                fA = c.fA + (c.fA - b.fA) * 4f,
            )
        }
    }
}
