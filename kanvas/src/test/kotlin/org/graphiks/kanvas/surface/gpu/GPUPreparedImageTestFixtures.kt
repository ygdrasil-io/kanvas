package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.image.ColorType

/**
 * Deterministic byte-array image fixtures for prepared-image tests.
 *
 * All colour images use premultiplied alpha: each R, G, B component is already
 * multiplied by the per-pixel alpha so that R ≤ A, G ≤ A, B ≤ A everywhere.
 *
 * These fixtures are pure Kotlin [ByteArray] values — no external PNG files
 * are required.
 */
object GPUPreparedImageTestFixtures {

    // ---- RGBA premultiplied 2×2 -----------------------------------------------

    val rgbaPremul2x2Width = 2
    val rgbaPremul2x2Height = 2
    val rgbaPremul2x2ColorType = ColorType.RGBA_8888

    /**
     * RGBA premul 2×2: row-major (top-left, top-right, bottom-left, bottom-right).
     *
     * | pixel         | R   | G   | B   | A   |
     * |---------------|-----|-----|-----|-----|
     * | (0,0) red     | 128 |   0 |   0 | 128 |
     * | (1,0) green   |   0 | 128 |   0 | 128 |
     * | (0,1) blue    |   0 |   0 | 128 | 128 |
     * | (1,1) white   | 128 | 128 | 128 | 128 |
     */
    val rgbaPremul2x2Bytes: ByteArray = byteArrayOf(
        // row 0
        128.toByte(), 0, 0, 128.toByte(),     // red premul
        0, 128.toByte(), 0, 128.toByte(),     // green premul
        // row 1
        0, 0, 128.toByte(), 128.toByte(),     // blue premul
        128.toByte(), 128.toByte(), 128.toByte(), 128.toByte(), // white premul
    )

    // ---- BGRA opaque 2×2 ------------------------------------------------------

    val bgraOpaque2x2Width = 2
    val bgraOpaque2x2Height = 2
    val bgraOpaque2x2ColorType = ColorType.BGRA_8888

    /**
     * BGRA opaque 2×2: same logical colours as the RGBA fixture, but with
     * BGRA byte order and fully opaque alpha.
     */
    val bgraOpaque2x2Bytes: ByteArray = byteArrayOf(
        // row 0
        0, 0, 255.toByte(), 255.toByte(),     // red
        0, 255.toByte(), 0, 255.toByte(),     // green
        // row 1
        255.toByte(), 0, 0, 255.toByte(),     // blue
        255.toByte(), 255.toByte(), 255.toByte(), 255.toByte(), // white
    )

    // ---- A8 3×1 ---------------------------------------------------------------

    val a8_3x1Width = 3
    val a8_3x1Height = 1
    val a8_3x1ColorType = ColorType.ALPHA_8

    /** A8 3×1: 0 (transparent), 128 (half), 255 (fully opaque). */
    val a8_3x1Bytes: ByteArray = byteArrayOf(0, 128.toByte(), 255.toByte())

    // ---- Image nine / lattice 6×6 ---------------------------------------------

    val imageNine6x6Width = 6
    val imageNine6x6Height = 6
    val imageNine6x6ColorType = ColorType.RGBA_8888

    /**
     * 6×6 image divided into nine 2×2 regions, each filled with a single
     * visually distinct premultiplied colour:
     *
     * | region          | R   | G   | B   | A   |
     * |-----------------|-----|-----|-----|-----|
     * | corners (white) | 255 | 255 | 255 | 255 |
     * | edges top/bot   | 255 |   0 |   0 | 255 |
     * | edges left/right|   0 | 255 |   0 | 255 |
     * | centre          |   0 |   0 | 255 | 255 |
     */
    val imageNine6x6Bytes: ByteArray = buildImageNineBytes()

    private fun buildImageNineBytes(): ByteArray {
        val w = 6
        val h = 6
        val bytes = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val offset = (y * w + x) * 4
                val regionX = x / 2
                val regionY = y / 2
                when {
                    regionX == 1 && regionY == 1 -> {
                        bytes[offset] = 0; bytes[offset + 1] = 0
                        bytes[offset + 2] = 255.toByte(); bytes[offset + 3] = 255.toByte()
                    }
                    regionY == 1 -> {
                        bytes[offset] = 0; bytes[offset + 1] = 255.toByte()
                        bytes[offset + 2] = 0; bytes[offset + 3] = 255.toByte()
                    }
                    regionX == 1 -> {
                        bytes[offset] = 255.toByte(); bytes[offset + 1] = 0
                        bytes[offset + 2] = 0; bytes[offset + 3] = 255.toByte()
                    }
                    else -> {
                        bytes[offset] = 255.toByte(); bytes[offset + 1] = 255.toByte()
                        bytes[offset + 2] = 255.toByte(); bytes[offset + 3] = 255.toByte()
                    }
                }
            }
        }
        return bytes
    }

    // ---- Atlas 4×4 ------------------------------------------------------------

    val atlas4x4Width = 4
    val atlas4x4Height = 4
    val atlas4x4ColorType = ColorType.RGBA_8888

    /**
     * 4×4 atlas with four 2×2 quadrants, each a distinct premultiplied RGBA
     * colour:
     *
     * | quadrant   | R   | G   | B   | A   |
     * |------------|-----|-----|-----|-----|
     * | (0,0) red  | 255 |   0 |   0 | 255 |
     * | (1,0) green|   0 | 128 |   0 | 128 |
     * | (0,1) blue |   0 |   0 | 255 | 255 |
     * | (1,1) white| 128 | 128 | 128 | 128 |
     */
    val atlas4x4Bytes: ByteArray = buildAtlasBytes()

    private fun buildAtlasBytes(): ByteArray {
        val w = 4
        val h = 4
        val bytes = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val offset = (y * w + x) * 4
                val qx = x / 2
                val qy = y / 2
                when {
                    qx == 0 && qy == 0 -> {
                        bytes[offset] = 255.toByte(); bytes[offset + 1] = 0
                        bytes[offset + 2] = 0; bytes[offset + 3] = 255.toByte()
                    }
                    qx == 1 && qy == 0 -> {
                        bytes[offset] = 0; bytes[offset + 1] = 128.toByte()
                        bytes[offset + 2] = 0; bytes[offset + 3] = 128.toByte()
                    }
                    qx == 0 && qy == 1 -> {
                        bytes[offset] = 0; bytes[offset + 1] = 0
                        bytes[offset + 2] = 255.toByte(); bytes[offset + 3] = 255.toByte()
                    }
                    else -> {
                        bytes[offset] = 128.toByte(); bytes[offset + 1] = 128.toByte()
                        bytes[offset + 2] = 128.toByte(); bytes[offset + 3] = 128.toByte()
                    }
                }
            }
        }
        return bytes
    }
}
