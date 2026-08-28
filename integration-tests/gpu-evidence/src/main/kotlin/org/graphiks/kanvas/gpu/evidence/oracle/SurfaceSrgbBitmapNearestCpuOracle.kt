package org.graphiks.kanvas.gpu.evidence.oracle

/** Literal CPU nearest-neighbour oracle for the bounded immutable RGBA8 bitmap evidence. */
class SurfaceSrgbBitmapNearestCpuOracle : CpuOracle {
    override fun render(width: Int, height: Int): ByteArray {
        val output = ByteArray(width * height * 4)
        val background = intArrayOf(13, 20, 33, 255)
        for (y in 0 until height) {
            for (x in 0 until width) write(output, width, x, y, background)
        }
        val source = arrayOf(
            arrayOf(intArrayOf(17, 34, 51, 255), intArrayOf(221, 204, 187, 255), intArrayOf(119, 136, 153, 255)),
            arrayOf(intArrayOf(68, 85, 102, 255), intArrayOf(16, 32, 48, 255), intArrayOf(170, 187, 204, 255)),
        )
        source.forEachIndexed { sourceY, row ->
            row.forEachIndexed { sourceX, rgba -> write(output, width, 12 + sourceX, 16 + sourceY, rgba) }
        }
        return output
    }

    private fun write(target: ByteArray, width: Int, x: Int, y: Int, rgba: IntArray) {
        val offset = (y * width + x) * 4
        rgba.forEachIndexed { channel, value -> target[offset + channel] = value.toByte() }
    }
}
