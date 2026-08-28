package org.graphiks.kanvas.gpu.evidence.oracle

/** Independent 64x64 oracle for one bounded opaque-child saveLayer restored with group opacity and SrcOver. */
class SurfaceSrgbSaveLayerSrcOverOpacityCpuOracle : CpuOracle {
    override fun render(width: Int, height: Int): ByteArray {
        require(width == WIDTH && height == HEIGHT) { "fixture requires 64x64 target" }
        return ByteArray(width * height * 4).also { output ->
            for (y in 0 until height) for (x in 0 until width) {
                var layer = transparent
                if (blueBounds.contains(x, y)) layer = SurfaceSrgbOracleMath.srcOver(layer, blue)
                if (orangeBounds.contains(x, y)) layer = SurfaceSrgbOracleMath.srcOver(layer, orange)
                val restored = SurfaceSrgbOracleMath.srcOver(background, layer.withOpacity(GROUP_ALPHA))
                val offset = (y * width + x) * 4
                SurfaceSrgbOracleMath.storeSrgb(restored).forEachIndexed { channel, value ->
                    output[offset + channel] = value.toByte()
                }
            }
        }
    }

    private fun SurfaceSrgbOracleMath.LinearPremul.withOpacity(alpha: Double) =
        SurfaceSrgbOracleMath.LinearPremul(red * alpha, green * alpha, blue * alpha, this.alpha * alpha)

    private companion object {
        const val WIDTH = 64
        const val HEIGHT = 64
        const val GROUP_ALPHA = 128.0 / 255.0

        val transparent = SurfaceSrgbOracleMath.decodeStraight(intArrayOf(0, 0, 0, 0))
        val background = SurfaceSrgbOracleMath.decodeStraight(intArrayOf(13, 20, 33, 255))
        val blue = SurfaceSrgbOracleMath.decodeStraight(intArrayOf(31, 115, 209, 255))
        val orange = SurfaceSrgbOracleMath.decodeStraight(intArrayOf(242, 135, 46, 255))
        val blueBounds = SurfaceSrgbOracleMath.PixelRect(12, 12, 44, 42)
        val orangeBounds = SurfaceSrgbOracleMath.PixelRect(24, 22, 52, 54)
    }
}
