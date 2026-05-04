package org.skia.foundation

/**
 * Bit-compatible port of `SkChecksum::Hash32` / `Hash64` from
 * [src/core/SkChecksum.cpp](file:///Users/chaos/workspace/kanvas-forge/skia-main/src/core/SkChecksum.cpp).
 *
 * The implementation is wyhash (https://github.com/wangyi-fudan/wyhash)
 * with Skia's choice of secret parameters. Hash32 returns the lower 32
 * bits of the 64-bit wyhash. The reads use little-endian byte order, which
 * matches `memcpy` on x86 and ARM — the only platforms Skia ships on, and
 * the only ones we care about for hash bit-compat.
 *
 * Used by [SkColorSpace] for `transferFnHash` / `toXYZD50Hash`. The
 * previous internal `hashFloats` (FNV-1a) was stable but produced
 * different values from upstream Skia, so any external consumer keying
 * off the colorspace hash would not interop.
 */
internal object SkChecksum {
    // The default secret from wyhash (a0761d64.../e7037ed1.../8ebc6af0.../589965cc...).
    // Kotlin doesn't have unsigned long literals, so we encode via UL→toLong
    // (preserves the bit pattern even when the high bit is set).
    private val WYP0: Long = 0xa0761d6478bd642fUL.toLong()
    private val WYP1: Long = 0xe7037ed1a0b428dbUL.toLong()
    private val WYP2: Long = 0x8ebc6af09c88c6e3UL.toLong()
    private val WYP3: Long = 0x589965cc75374cc3UL.toLong()

    public fun hash32(data: ByteArray, length: Int = data.size, seed: Int = 0): Int {
        // Skia: `Hash32` zero-extends the uint32 seed to uint64 before
        // calling wyhash, then truncates the result to uint32.
        return wyhash(data, length, seed.toLong() and 0xFFFFFFFFL).toInt()
    }

    public fun hash64(data: ByteArray, length: Int = data.size, seed: Long = 0L): Long {
        return wyhash(data, length, seed)
    }

    // Mirror of `wyhash()` in `SkChecksum.cpp:60-98`.
    private fun wyhash(p: ByteArray, len: Int, seedIn: Long): Long {
        var seed = seedIn xor wymix(seedIn xor WYP0, WYP1)
        var a: Long
        var b: Long
        var off = 0
        if (len <= 16) {
            if (len >= 4) {
                val shift = (len ushr 3) shl 2
                a = (wyr4(p, off) shl 32) or wyr4(p, off + shift)
                b = (wyr4(p, off + len - 4) shl 32) or wyr4(p, off + len - 4 - shift)
            } else if (len > 0) {
                a = wyr3(p, off, len)
                b = 0L
            } else {
                a = 0L
                b = 0L
            }
        } else {
            var i = len
            if (i > 48) {
                var see1 = seed
                var see2 = seed
                do {
                    seed = wymix(wyr8(p, off) xor WYP1, wyr8(p, off + 8) xor seed)
                    see1 = wymix(wyr8(p, off + 16) xor WYP2, wyr8(p, off + 24) xor see1)
                    see2 = wymix(wyr8(p, off + 32) xor WYP3, wyr8(p, off + 40) xor see2)
                    off += 48
                    i -= 48
                } while (i > 48)
                seed = seed xor see1 xor see2
            }
            while (i > 16) {
                seed = wymix(wyr8(p, off) xor WYP1, wyr8(p, off + 8) xor seed)
                i -= 16
                off += 16
            }
            a = wyr8(p, off + i - 16)
            b = wyr8(p, off + i - 8)
        }
        a = a xor WYP1
        b = b xor seed
        // Inline _wymum on (a, b): we need both halves of the 128-bit product.
        val lo = a * b
        val hi = Math.unsignedMultiplyHigh(a, b)
        return wymix(lo xor WYP0 xor len.toLong(), hi xor WYP1)
    }

    // 128-bit unsigned multiply, return low XOR high. Mirrors `_wymix`
    // composed with `_wymum`. Uses [Math.unsignedMultiplyHigh] (Java 18+).
    private fun wymix(a: Long, b: Long): Long {
        val lo = a * b
        val hi = Math.unsignedMultiplyHigh(a, b)
        return lo xor hi
    }

    // 8-byte little-endian load.
    private fun wyr8(p: ByteArray, i: Int): Long {
        return (p[i].toLong() and 0xFFL) or
            ((p[i + 1].toLong() and 0xFFL) shl 8) or
            ((p[i + 2].toLong() and 0xFFL) shl 16) or
            ((p[i + 3].toLong() and 0xFFL) shl 24) or
            ((p[i + 4].toLong() and 0xFFL) shl 32) or
            ((p[i + 5].toLong() and 0xFFL) shl 40) or
            ((p[i + 6].toLong() and 0xFFL) shl 48) or
            ((p[i + 7].toLong() and 0xFFL) shl 56)
    }

    // 4-byte little-endian load, zero-extended to 64 bits.
    private fun wyr4(p: ByteArray, i: Int): Long {
        return (p[i].toLong() and 0xFFL) or
            ((p[i + 1].toLong() and 0xFFL) shl 8) or
            ((p[i + 2].toLong() and 0xFFL) shl 16) or
            ((p[i + 3].toLong() and 0xFFL) shl 24)
    }

    // 3-or-less byte load matching wyhash's _wyr3: bytes p[0], p[k>>1],
    // p[k-1] packed as (b0<<16) | (bm<<8) | bl.
    private fun wyr3(p: ByteArray, i: Int, k: Int): Long {
        return ((p[i].toLong() and 0xFFL) shl 16) or
            ((p[i + (k ushr 1)].toLong() and 0xFFL) shl 8) or
            (p[i + k - 1].toLong() and 0xFFL)
    }
}
