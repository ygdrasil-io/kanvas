package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * struct SkCpu {
 *     enum {
 *         SSE1       = 1 << 0,
 *         SSE2       = 1 << 1,
 *         SSE3       = 1 << 2,
 *         SSSE3      = 1 << 3,
 *         SSE41      = 1 << 4,
 *         SSE42      = 1 << 5,
 *         AVX        = 1 << 6,
 *         F16C       = 1 << 7,
 *         FMA        = 1 << 8,
 *         AVX2       = 1 << 9,
 *         BMI1       = 1 << 10,
 *         BMI2       = 1 << 11,
 *         // Handy alias for all the cool Haswell+ instructions.
 *         HSW = AVX2 | BMI1 | BMI2 | F16C | FMA,
 *
 *         AVX512F    = 1 << 12,
 *         AVX512DQ   = 1 << 13,
 *         AVX512IFMA = 1 << 14,
 *         AVX512PF   = 1 << 15,
 *         AVX512ER   = 1 << 16,
 *         AVX512CD   = 1 << 17,
 *         AVX512BW   = 1 << 18,
 *         AVX512VL   = 1 << 19,
 *
 *         // Handy alias for all the cool Skylake Xeon+ instructions.
 *         SKX = AVX512F  | AVX512DQ | AVX512CD | AVX512BW | AVX512VL,
 *
 *         ERMS       = 1 << 20,
 *     };
 *
 *     enum {
 *         LOONGARCH_SX = 1 << 0,
 *         LOONGARCH_ASX = 1 << 1,
 *     };
 *
 *     static void CacheRuntimeFeatures();
 *     static bool Supports(uint32_t);
 * private:
 *     static uint32_t gCachedFeatures;
 * }
 * ```
 */
public open class SkCpu {
  public companion object {
    public val sse1: Int = TODO("Initialize sse1")

    public val sse2: Int = TODO("Initialize sse2")

    public val sse3: Int = TODO("Initialize sse3")

    public val ssse3: Int = TODO("Initialize ssse3")

    public val sse41: Int = TODO("Initialize sse41")

    public val sse42: Int = TODO("Initialize sse42")

    public val avx: Int = TODO("Initialize avx")

    public val f16c: Int = TODO("Initialize f16c")

    public val fma: Int = TODO("Initialize fma")

    public val avx2: Int = TODO("Initialize avx2")

    public val bmi1: Int = TODO("Initialize bmi1")

    public val bmi2: Int = TODO("Initialize bmi2")

    public val hsw: Int = TODO("Initialize hsw")

    public val avx512f: Int = TODO("Initialize avx512f")

    public val avx512dq: Int = TODO("Initialize avx512dq")

    public val avx512ifma: Int = TODO("Initialize avx512ifma")

    public val avx512pf: Int = TODO("Initialize avx512pf")

    public val avx512er: Int = TODO("Initialize avx512er")

    public val avx512cd: Int = TODO("Initialize avx512cd")

    public val avx512bw: Int = TODO("Initialize avx512bw")

    public val avx512vl: Int = TODO("Initialize avx512vl")

    public val skx: Int = TODO("Initialize skx")

    public val erms: Int = TODO("Initialize erms")

    public val loongarchSX: Int = TODO("Initialize loongarchSX")

    public val loongarchASX: Int = TODO("Initialize loongarchASX")

    private var gCachedFeatures: Int = TODO("Initialize gCachedFeatures")

    /**
     * C++ original:
     * ```cpp
     * void SkCpu::CacheRuntimeFeatures() {
     *     static SkOnce once;
     *     once([] { gCachedFeatures = read_cpu_features(); });
     * }
     * ```
     */
    public fun cacheRuntimeFeatures() {
      TODO("Implement cacheRuntimeFeatures")
    }

    /**
     * C++ original:
     * ```cpp
     * inline bool SkCpu::Supports(uint32_t mask) {
     *     uint32_t features = gCachedFeatures;
     *
     *     // If we mask in compile-time known lower limits, the compiler can
     *     // often compile away this entire function.
     * #if SK_CPU_X86
     *     #if SK_CPU_SSE_LEVEL >= SK_CPU_SSE_LEVEL_SSE1
     *     features |= SSE1;
     *     #endif
     *     #if SK_CPU_SSE_LEVEL >= SK_CPU_SSE_LEVEL_SSE2
     *     features |= SSE2;
     *     #endif
     *     #if SK_CPU_SSE_LEVEL >= SK_CPU_SSE_LEVEL_SSE3
     *     features |= SSE3;
     *     #endif
     *     #if SK_CPU_SSE_LEVEL >= SK_CPU_SSE_LEVEL_SSSE3
     *     features |= SSSE3;
     *     #endif
     *     #if SK_CPU_SSE_LEVEL >= SK_CPU_SSE_LEVEL_SSE41
     *     features |= SSE41;
     *     #endif
     *     #if SK_CPU_SSE_LEVEL >= SK_CPU_SSE_LEVEL_SSE42
     *     features |= SSE42;
     *     #endif
     *     #if SK_CPU_SSE_LEVEL >= SK_CPU_SSE_LEVEL_AVX
     *     features |= AVX;
     *     #endif
     *     // F16C goes here if we add SK_CPU_SSE_LEVEL_F16C
     *     #if SK_CPU_SSE_LEVEL >= SK_CPU_SSE_LEVEL_AVX2
     *     features |= AVX2;
     *     #endif
     *     #if SK_CPU_SSE_LEVEL >= SK_CPU_SSE_LEVEL_SKX
     *     features |= (AVX512F | AVX512DQ | AVX512CD | AVX512BW | AVX512VL);
     *     #endif
     *     // FMA doesn't fit neatly into this total ordering.
     *     // It's available on Haswell+ just like AVX2, but it's technically a different bit.
     *     // TODO: circle back on this if we find ourselves limited by lack of compile-time FMA
     *
     *     #if defined(SK_CPU_LIMIT_AVX)
     *     features &= (SSE1 | SSE2 | SSE3 | SSSE3 | SSE41 | SSE42 | AVX);
     *     #elif defined(SK_CPU_LIMIT_SSE41)
     *     features &= (SSE1 | SSE2 | SSE3 | SSSE3 | SSE41);
     *     #elif defined(SK_CPU_LIMIT_SSE2)
     *     features &= (SSE1 | SSE2);
     *     #endif
     *
     * #elif SK_CPU_LOONGARCH
     *     #if SK_CPU_LSX_LEVEL >= SK_CPU_LSX_LEVEL_LSX
     *     features |= LOONGARCH_SX;
     *     #endif
     *     #if SK_CPU_LSX_LEVEL >= SK_CPU_LSX_LEVEL_LASX
     *     features |= LOONGARCH_ASX;
     *     #endif
     *
     * #endif
     *     return (features & mask) == mask;
     * }
     * ```
     */
    public fun supports(mask: UInt): Boolean {
      TODO("Implement supports")
    }
  }
}
