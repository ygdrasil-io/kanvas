package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.SizeI32

/** Checked physical capacity reserved by one pooled W4e clip-mask realization. */
public object ClipPlanBudget {
    /** Two 1x accumulators, one resolved scratch, one 4x scratch and one 4x D24S8 attachment. */
    public fun aaPathClipBytes(extentI32: SizeI32): Long =
        checkedAaPathClipBytesI64(extentI32.width, extentI32.height)

    /**
     * Two 1x accumulators, one resolved scratch and, for a path producer, its 1x D24S8
     * attachment.  Analytic rect-only pools pass [requiresDepthStencil] as false.
     */
    public fun hardClipBytes(
        extentI32: SizeI32,
        requiresDepthStencil: Boolean = true,
    ): Long {
        val oneSampleI64 = checkedMaskTextureBytesI64(extentI32.width, extentI32.height, 1)
        return Math.multiplyExact(oneSampleI64, if (requiresDepthStencil) 4L else 3L)
    }

    internal fun checkedMaskTextureBytesI64(widthI32: Int, heightI32: Int, sampleCountI32: Int): Long =
        Math.multiplyExact(
            4L,
            Math.multiplyExact(
                Math.multiplyExact(widthI32.toLong(), heightI32.toLong()),
                sampleCountI32.toLong(),
            ),
        )

    internal fun checkedAaPathClipBytesI64(widthI32: Int, heightI32: Int): Long {
        val oneSampleI64 = checkedMaskTextureBytesI64(widthI32, heightI32, 1)
        val fourSamplesI64 = checkedMaskTextureBytesI64(widthI32, heightI32, 4)
        return Math.addExact(Math.multiplyExact(oneSampleI64, 3L), Math.multiplyExact(fourSamplesI64, 2L))
    }
}
