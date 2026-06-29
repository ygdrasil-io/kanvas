package org.graphiks.kanvas.codec.ico

import org.graphiks.kanvas.codec.png.PngEncoder
import org.skia.foundation.SkBitmap
import java.io.ByteArrayOutputStream
import java.io.OutputStream

public object IcoEncoder {

    public enum class PayloadFormat { PNG, BMP }

    public data class Entry(
        val bitmap: SkBitmap,
        val format: PayloadFormat = PayloadFormat.PNG,
    )

    public fun encode(entries: List<Entry>): ByteArray? {
        if (entries.isEmpty()) return null
        val payloads = ArrayList<ByteArray>(entries.size)
        for (entry in entries) {
            val w = entry.bitmap.width
            val h = entry.bitmap.height
            if (w <= 0 || h <= 0) return null
            val payload = when (entry.format) {
                PayloadFormat.PNG -> PngEncoder.encode(entry.bitmap) ?: return null
                PayloadFormat.BMP -> {
                    val fullBmp = org.graphiks.kanvas.codec.bmp.BmpEncoder.encode(entry.bitmap) ?: return null
                    if (fullBmp.size <= 14) return null
                    fullBmp.copyOfRange(14, fullBmp.size)
                }
            }
            payloads.add(payload)
        }
        return buildIco(entries, payloads)
    }

    public fun encode(bitmap: SkBitmap): ByteArray? {
        return encode(listOf(Entry(bitmap)))
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

    private fun buildIco(entries: List<Entry>, payloads: List<ByteArray>): ByteArray {
        val count = entries.size
        val headerSize = 6
        val dirEntrySize = 16
        val dirEnd = headerSize + count * dirEntrySize

        val offsets = ArrayList<Int>(count)
        var nextOffset = dirEnd
        for (i in 0 until count) {
            offsets.add(nextOffset)
            nextOffset += payloads[i].size
        }

        val out = ByteArrayOutputStream(nextOffset)
        writeU16LE(out, 0)
        writeU16LE(out, 1)
        writeU16LE(out, count)

        for (i in 0 until count) {
            val w = entries[i].bitmap.width
            val h = entries[i].bitmap.height
            val dirW = if (w >= 256) 0 else w
            val dirH = if (h >= 256) 0 else h
            out.write(dirW)
            out.write(dirH)
            out.write(0)
            out.write(0)
            writeU16LE(out, 1)
            writeU16LE(out, 32)
            writeU32LE(out, payloads[i].size)
            writeU32LE(out, offsets[i])
        }

        for (payload in payloads) {
            out.write(payload)
        }

        return out.toByteArray()
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
