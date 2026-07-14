package org.graphiks.math.color

public data class ColorTransferFunction(
    public val g: Float,
    public val a: Float,
    public val b: Float,
    public val c: Float,
    public val d: Float,
    public val e: Float,
    public val f: Float
) {
    public companion object {
        public val sRgb: ColorTransferFunction = ColorTransferFunction(
            g = 2.4f, a = 1f / 1.055f, b = 0.055f / 1.055f,
            c = 1f / 12.92f, d = 0.04045f, e = 0f, f = 0f
        )
        public val linear: ColorTransferFunction = ColorTransferFunction(
            g = 1f, a = 1f, b = 0f, c = 0f, d = 0f, e = 0f, f = 0f
        )
        public val rec2020: ColorTransferFunction = ColorTransferFunction(
            g = 2.2222222f, a = 0.9096724f, b = 0.0903276f,
            c = 0.2222222f / 0.45f, d = 0.0812429f, e = 0f, f = 0f
        )
        public val pq: ColorTransferFunction = ColorTransferFunction(
            g = 0.8359375f, a = 0.1593018f, b = 0.0f,
            c = 1.0f, d = 0.0f, e = 0.0f, f = 0.0f
        )
        public val hlg: ColorTransferFunction = ColorTransferFunction(
            g = 1.2f, a = 0.7746413f, b = 0.0042930f,
            c = 0.5555556f, d = 0.0f, e = 0.0f, f = 0.0f
        )
    }
}
