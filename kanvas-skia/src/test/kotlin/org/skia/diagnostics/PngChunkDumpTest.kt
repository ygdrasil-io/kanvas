package org.skia.diagnostics

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.zip.Inflater

/**
 * Phase 0 diagnostic: dump every chunk in a handful of reference PNGs so we
 * can identify the exact ICC profile (or lack thereof) that produces the
 * observed `0x0000FF -> 0x2B0DF2` shift.
 *
 * This is a one-off test — output goes to stdout; the test always passes.
 */
class PngChunkDumpTest {

    @Test
    fun `dump chunks of representative reference PNGs`() {
        val targets = listOf(
            "bigrect.png",
            "simplerect.png",
            "aarectmodes.png",
            "addarc.png",
            "aaclip.png",
            "all_bitmap_configs.png",
        )
        val out = java.io.File("build/diagnostics/png-chunk-dump.txt")
        out.parentFile.mkdirs()
        out.bufferedWriter().use { w ->
            val saved = System.out
            System.setOut(java.io.PrintStream(java.io.FileOutputStream(out)))
            try {
                for (name in targets) {
                    println("===== $name =====")
                    val bytes = javaClass.classLoader.getResourceAsStream("original-888/$name")
                        ?.readBytes()
                    if (bytes == null) {
                        println("  (missing on classpath)")
                        continue
                    }
                    dumpChunks(bytes)
                    println()
                }
            } finally {
                System.out.flush()
                System.setOut(saved)
            }
        }
        println("dump written to ${out.absolutePath}")
    }

    private fun dumpChunks(png: ByteArray) {
        val sig = png.copyOfRange(0, 8)
        val expectedSig = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )
        require(sig.contentEquals(expectedSig)) { "not a PNG (bad signature)" }
        val dis = DataInputStream(ByteArrayInputStream(png, 8, png.size - 8))
        while (dis.available() > 0) {
            val length = dis.readInt()
            val typeBytes = ByteArray(4).also { dis.readFully(it) }
            val type = String(typeBytes, Charsets.US_ASCII)
            val data = ByteArray(length).also { dis.readFully(it) }
            dis.readInt()
            println("  $type len=$length")
            when (type) {
                "IHDR" -> dumpIhdr(data)
                "sRGB" -> dumpSrgb(data)
                "gAMA" -> dumpGama(data)
                "cHRM" -> dumpChrm(data)
                "iCCP" -> dumpIccp(data)
                "cICP" -> dumpCicp(data)
                "pHYs", "tIME", "tEXt", "iTXt", "zTXt", "bKGD" -> {
                    if (length <= 64) hexdump(data, "    ")
                }
                "IEND" -> return
            }
        }
    }

    private fun dumpIhdr(data: ByteArray) {
        val w = ((data[0].toInt() and 0xFF) shl 24) or ((data[1].toInt() and 0xFF) shl 16) or
                ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        val h = ((data[4].toInt() and 0xFF) shl 24) or ((data[5].toInt() and 0xFF) shl 16) or
                ((data[6].toInt() and 0xFF) shl 8) or (data[7].toInt() and 0xFF)
        val bitDepth = data[8].toInt() and 0xFF
        val colorType = data[9].toInt() and 0xFF
        println("    ${w}x${h} bitDepth=$bitDepth colorType=$colorType (0=gray,2=RGB,3=palette,4=grayA,6=RGBA)")
    }

    private fun dumpSrgb(data: ByteArray) {
        val intent = data[0].toInt() and 0xFF
        val intentName = when (intent) {
            0 -> "perceptual"
            1 -> "relative-colorimetric"
            2 -> "saturation"
            3 -> "absolute-colorimetric"
            else -> "?"
        }
        println("    rendering intent = $intent ($intentName)")
    }

    private fun dumpGama(data: ByteArray) {
        val v = ((data[0].toInt() and 0xFF) shl 24) or ((data[1].toInt() and 0xFF) shl 16) or
                ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)
        println("    gamma = ${v / 100000.0}")
    }

    private fun dumpChrm(data: ByteArray) {
        val ints = IntArray(8) {
            ((data[it * 4].toInt() and 0xFF) shl 24) or
                ((data[it * 4 + 1].toInt() and 0xFF) shl 16) or
                ((data[it * 4 + 2].toInt() and 0xFF) shl 8) or
                (data[it * 4 + 3].toInt() and 0xFF)
        }
        val labels = listOf("wpx", "wpy", "rx", "ry", "gx", "gy", "bx", "by")
        for (i in 0 until 8) {
            println("    ${labels[i]} = ${ints[i] / 100000.0}")
        }
    }

    private fun dumpCicp(data: ByteArray) {
        println("    color_primaries=${data[0].toInt() and 0xFF}")
        println("    transfer_characteristics=${data[1].toInt() and 0xFF}")
        println("    matrix_coefficients=${data[2].toInt() and 0xFF}")
        println("    full_range=${data[3].toInt() and 0xFF}")
    }

    private fun dumpIccp(data: ByteArray) {
        var nameEnd = 0
        while (nameEnd < data.size && data[nameEnd] != 0.toByte()) nameEnd++
        val name = String(data, 0, nameEnd, Charsets.US_ASCII)
        val method = if (nameEnd + 1 < data.size) data[nameEnd + 1].toInt() and 0xFF else -1
        println("    profileName=\"$name\" compressionMethod=$method")
        val compressed = data.copyOfRange(nameEnd + 2, data.size)
        val inflater = Inflater()
        inflater.setInput(compressed)
        val out = ByteArray(64 * 1024)
        val len = inflater.inflate(out)
        inflater.end()
        val profile = out.copyOfRange(0, len)
        println("    profileSize=$len bytes")
        dumpIccProfile(profile)
    }

    private fun dumpIccProfile(p: ByteArray) {
        if (p.size < 128) {
            println("    (profile shorter than ICC header)")
            return
        }
        val cs = String(p, 16, 4, Charsets.US_ASCII)
        val pcs = String(p, 20, 4, Charsets.US_ASCII)
        val deviceClass = String(p, 12, 4, Charsets.US_ASCII)
        val cmm = String(p, 4, 4, Charsets.US_ASCII)
        val version = "${p[8].toInt() and 0xFF}.${(p[9].toInt() and 0xF0) shr 4}.${p[9].toInt() and 0x0F}"
        val platform = String(p, 40, 4, Charsets.US_ASCII)
        val ccreator = String(p, 80, 4, Charsets.US_ASCII)
        println("    ICC: cmm='$cmm' version=$version deviceClass='$deviceClass' colorSpace='$cs' pcs='$pcs' platform='$platform' creator='$ccreator'")

        val wpX = readS15Fixed16(p, 68)
        val wpY = readS15Fixed16(p, 72)
        val wpZ = readS15Fixed16(p, 76)
        println("    illuminant XYZ = ($wpX, $wpY, $wpZ)")

        val tagCount = readU32BE(p, 128)
        println("    tagCount=$tagCount")
        var off = 132
        repeat(tagCount.toInt().coerceAtMost(40)) {
            if (off + 12 > p.size) return@repeat
            val tag = String(p, off, 4, Charsets.US_ASCII)
            val tagOff = readU32BE(p, off + 4).toInt()
            val tagLen = readU32BE(p, off + 8).toInt()
            print("      tag='$tag' off=$tagOff len=$tagLen")
            if (tagOff in 0 until p.size && tagOff + 8 <= p.size) {
                val tagSig = String(p, tagOff, 4, Charsets.US_ASCII)
                print(" type='$tagSig'")
                when (tag) {
                    "rXYZ", "gXYZ", "bXYZ", "wtpt", "bkpt" -> {
                        if (tagLen >= 20) {
                            val x = readS15Fixed16(p, tagOff + 8)
                            val y = readS15Fixed16(p, tagOff + 12)
                            val z = readS15Fixed16(p, tagOff + 16)
                            print(" XYZ=($x, $y, $z)")
                        }
                    }
                    "rTRC", "gTRC", "bTRC", "kTRC" -> {
                        when (tagSig) {
                            "para" -> {
                                if (tagLen >= 12) {
                                    val funcType = readU16BE(p, tagOff + 8)
                                    print(" para type=$funcType")
                                    val nParams = when (funcType) {
                                        0 -> 1; 1 -> 3; 2 -> 4; 3 -> 5; 4 -> 7
                                        else -> 0
                                    }
                                    val params = (0 until nParams).map {
                                        readS15Fixed16(p, tagOff + 12 + 4 * it)
                                    }
                                    print(" params=$params")
                                }
                            }
                            "curv" -> {
                                if (tagLen >= 12) {
                                    val n = readU32BE(p, tagOff + 8).toInt()
                                    print(" curv n=$n")
                                    when (n) {
                                        0 -> print(" (linear)")
                                        1 -> {
                                            val g = readU16BE(p, tagOff + 12)
                                            print(" gamma=${g / 256.0}")
                                        }
                                        else -> {
                                            val first = readU16BE(p, tagOff + 12)
                                            val mid = readU16BE(p, tagOff + 12 + (n / 2) * 2)
                                            val last = readU16BE(p, tagOff + 12 + (n - 1) * 2)
                                            print(" first=$first mid=$mid last=$last")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            println()
            off += 12
        }
    }

    private fun readU32BE(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or
            ((b[off + 1].toLong() and 0xFF) shl 16) or
            ((b[off + 2].toLong() and 0xFF) shl 8) or
            (b[off + 3].toLong() and 0xFF)

    private fun readU16BE(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    private fun readS15Fixed16(b: ByteArray, off: Int): Double {
        val raw = ((b[off].toInt() and 0xFF) shl 24) or
            ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or
            (b[off + 3].toInt() and 0xFF)
        return raw / 65536.0
    }

    private fun hexdump(data: ByteArray, prefix: String) {
        val sb = StringBuilder(prefix)
        for ((i, b) in data.withIndex()) {
            sb.append("%02x ".format(b.toInt() and 0xFF))
            if (i % 16 == 15) sb.append("\n").append(prefix)
        }
        println(sb.toString().trimEnd())
    }
}
