package org.graphiks.kanvas.codec.ico

import org.graphiks.kanvas.codec.png.PngEncoder
import org.skia.foundation.SkBitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream

public object IcoEncoder {

    private val defaultOptions: PngEncoder.Options = PngEncoder.Options()

    public fun encode(bitmap: SkBitmap): ByteArray? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null

        // Encode bitmap as PNG first
        val png = PngEncoder.encode(bitmap, defaultOptions) ?: return null

        // ICO header: reserved(2) + type(2, 1=ICO) + count(2)
        val count = 1
        val headerSize = 6
        val dirEntrySize = 16
        val dataOffset = headerSize + count * dirEntrySize
        val dirW = if (w >= 256) 0 else w
        val dirH = if (h >= 256) 0 else h

        val fileSize = dataOffset + png.size
        val out = ByteArrayOutputStream(fileSize)

        // ICO header
        writeU16LE(out, 0) // reserved
        writeU16LE(out, 1) // type = ICO
        writeU16LE(out, count)

        // Directory entry
        out.write(dirW)          // width
        out.write(dirH)          // height
        out.write(0)             // color count (0 for true color)
        out.write(0)             // reserved
        writeU16LE(out, 1)       // planes (for ICO)
        writeU16LE(out, 32)      // bits per pixel
        writeU32LE(out, png.size) // image size
        writeU32LE(out, dataOffset) // image data offset

        // PNG payload
        out.write(png)

        return out.toByteArray()
    }

    public fun encode(dst: OutputStream, bitmap: SkBitmap): Boolean {
        val bytes = encode(bitmap) ?: return false
        return try {
            dst.write(bytes)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun writeU16LE(out: OutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
    }

    private fun writeU32LE(out: OutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v ushr 8) and 0xFF)
        out.write((v ushr 16) and 0xFF)
        out.write((v ushr 24) and 0xFF)
    }
}
