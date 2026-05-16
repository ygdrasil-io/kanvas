package org.skia.skcms

import kotlin.Any
import kotlin.Boolean
import kotlin.Float
import kotlin.FloatArray
import kotlin.Int
import kotlin.IntArray
import kotlin.String
import kotlin.UByte
import kotlin.UInt
import kotlin.ULong
import kotlin.UShort
import kotlin.Unit
import org.skia.core.U32
import org.skia.core.U8
import undefined.I32
import undefined.U16
import undefined.U64

/**
 * C++ original:
 * ```cpp
 * void skcms_DisableRuntimeCPUDetection() {
 *     sAllowRuntimeCPUDetection = false;
 * }
 * ```
 */
public fun skcmsDisableRuntimeCPUDetection() {
  TODO("Implement skcmsDisableRuntimeCPUDetection")
}

/**
 * C++ original:
 * ```cpp
 * static float log2f_(float x) {
 *     // The first approximation of log2(x) is its exponent 'e', minus 127.
 *     int32_t bits;
 *     memcpy(&bits, &x, sizeof(bits));
 *
 *     float e = (float)bits * (1.0f / (1<<23));
 *
 *     // If we use the mantissa too we can refine the error signficantly.
 *     int32_t m_bits = (bits & 0x007fffff) | 0x3f000000;
 *     float m;
 *     memcpy(&m, &m_bits, sizeof(m));
 *
 *     return (e - 124.225514990f
 *               -   1.498030302f*m
 *               -   1.725879990f/(0.3520887068f + m));
 * }
 * ```
 */
public fun log2f(x: Float): Float {
  TODO("Implement log2f")
}

/**
 * C++ original:
 * ```cpp
 * static float logf_(float x) {
 *     const float ln2 = 0.69314718f;
 *     return ln2*log2f_(x);
 * }
 * ```
 */
public fun logf(x: Float): Float {
  TODO("Implement logf")
}

/**
 * C++ original:
 * ```cpp
 * static float exp2f_(float x) {
 *     if (x > 128.0f) {
 *         return INFINITY_;
 *     } else if (x < -127.0f) {
 *         return 0.0f;
 *     }
 *     float fract = x - floorf_(x);
 *
 *     float fbits = (1.0f * (1<<23)) * (x + 121.274057500f
 *                                         -   1.490129070f*fract
 *                                         +  27.728023300f/(4.84252568f - fract));
 *
 *     // Before we cast fbits to int32_t, check for out of range values to pacify UBSAN.
 *     // INT_MAX is not exactly representable as a float, so exclude it as effectively infinite.
 *     // Negative values are effectively underflow - we'll end up returning a (different) negative
 *     // value, which makes no sense. So clamp to zero.
 *     if (fbits >= (float)INT_MAX) {
 *         return INFINITY_;
 *     } else if (fbits < 0) {
 *         return 0;
 *     }
 *
 *     int32_t bits = (int32_t)fbits;
 *     memcpy(&x, &bits, sizeof(x));
 *     return x;
 * }
 * ```
 */
public fun exp2f(x: Float): Float {
  TODO("Implement exp2f")
}

/**
 * C++ original:
 * ```cpp
 * float powf_(float x, float y) {
 *     if (x <= 0.f) {
 *         return 0.f;
 *     }
 *     if (x == 1.f) {
 *         return 1.f;
 *     }
 *     return exp2f_(log2f_(x) * y);
 * }
 * ```
 */
public fun powf(x: Float, y: Float): Float {
  TODO("Implement powf")
}

/**
 * C++ original:
 * ```cpp
 * static float expf_(float x) {
 *     const float log2_e = 1.4426950408889634074f;
 *     return exp2f_(log2_e * x);
 * }
 * ```
 */
public fun expf(x: Float): Float {
  TODO("Implement expf")
}

/**
 * C++ original:
 * ```cpp
 * static float fmaxf_(float x, float y) { return x > y ? x : y; }
 * ```
 */
public fun fmaxf(x: Float, y: Float): Float {
  TODO("Implement fmaxf")
}

/**
 * C++ original:
 * ```cpp
 * static float fminf_(float x, float y) { return x < y ? x : y; }
 * ```
 */
public fun fminf(x: Float, y: Float): Float {
  TODO("Implement fminf")
}

/**
 * C++ original:
 * ```cpp
 * static bool isfinitef_(float x) { return 0 == x*0; }
 * ```
 */
public fun isfinitef(x: Float): Boolean {
  TODO("Implement isfinitef")
}

/**
 * C++ original:
 * ```cpp
 * static float TFKind_marker(skcms_TFType kind) {
 *     // We'd use different NaNs, but those aren't guaranteed to be preserved by WASM.
 *     return -(float)kind;
 * }
 * ```
 */
public fun tFKindMarker(kind: SkcmsTFType): Float {
  TODO("Implement tFKindMarker")
}

/**
 * C++ original:
 * ```cpp
 * static skcms_TFType classify(const skcms_TransferFunction& tf, TF_PQish*   pq = nullptr
 *                                                              , TF_HLGish* hlg = nullptr) {
 *     if (tf.g < 0) {
 *         // Negative "g" is mapped to enum values; large negative are for sure invalid.
 *         if (tf.g < -128) {
 *             return skcms_TFType_Invalid;
 *         }
 *         int enum_g = -static_cast<int>(tf.g);
 *         // Non-whole "g" values are invalid as well.
 *         if (static_cast<float>(-enum_g) != tf.g) {
 *             return skcms_TFType_Invalid;
 *         }
 *         // TODO: soundness checks for PQ/HLG like we do for sRGBish?
 *         switch (enum_g) {
 *             case skcms_TFType_PQish:
 *                 if (pq) {
 *                     memcpy(pq , &tf.a, sizeof(*pq ));
 *                 }
 *                 return skcms_TFType_PQish;
 *             case skcms_TFType_HLGish:
 *                 if (hlg) {
 *                     memcpy(hlg, &tf.a, sizeof(*hlg));
 *                 }
 *                 return skcms_TFType_HLGish;
 *             case skcms_TFType_HLGinvish:
 *                 if (hlg) {
 *                     memcpy(hlg, &tf.a, sizeof(*hlg));
 *                 }
 *                 return skcms_TFType_HLGinvish;
 *             case skcms_TFType_PQ:
 *                 if (tf.b != 0.f || tf.c != 0.f || tf.d != 0.f || tf.e != 0.f || tf.f != 0.f) {
 *                     return skcms_TFType_Invalid;
 *                 }
 *                 return skcms_TFType_PQ;
 *             case skcms_TFType_HLG:
 *                 if (tf.d != 0.f || tf.e != 0.f || tf.f != 0.f) {
 *                     return skcms_TFType_Invalid;
 *                 }
 *                 return skcms_TFType_HLG;
 *         }
 *         return skcms_TFType_Invalid;
 *     }
 *
 *     // Basic soundness checks for sRGBish transfer functions.
 *     if (isfinitef_(tf.a + tf.b + tf.c + tf.d + tf.e + tf.f + tf.g)
 *             // a,c,d,g should be non-negative to make any sense.
 *             && tf.a >= 0
 *             && tf.c >= 0
 *             && tf.d >= 0
 *             && tf.g >= 0
 *             // Raising a negative value to a fractional tf->g produces complex numbers.
 *             && tf.a * tf.d + tf.b >= 0) {
 *         return skcms_TFType_sRGBish;
 *     }
 *
 *     return skcms_TFType_Invalid;
 * }
 * ```
 */
public fun classify(
  tf: SkcmsTransferFunction,
  pq: TFPQish? = TODO(),
  hlg: TFHLGish? = TODO(),
): SkcmsTFType {
  TODO("Implement classify")
}

/**
 * C++ original:
 * ```cpp
 * skcms_TFType skcms_TransferFunction_getType(const skcms_TransferFunction* tf) {
 *     return classify(*tf);
 * }
 * ```
 */
public fun skcmsTransferFunctionGetType(tf: SkcmsTransferFunction?): SkcmsTFType {
  TODO("Implement skcmsTransferFunctionGetType")
}

/**
 * C++ original:
 * ```cpp
 * bool skcms_TransferFunction_isSRGBish(const skcms_TransferFunction* tf) {
 *     return classify(*tf) == skcms_TFType_sRGBish;
 * }
 * ```
 */
public fun skcmsTransferFunctionIsSRGBish(tf: SkcmsTransferFunction?): Boolean {
  TODO("Implement skcmsTransferFunctionIsSRGBish")
}

/**
 * C++ original:
 * ```cpp
 * bool skcms_TransferFunction_isPQish(const skcms_TransferFunction* tf) {
 *     return classify(*tf) == skcms_TFType_PQish;
 * }
 * ```
 */
public fun skcmsTransferFunctionIsPQish(tf: SkcmsTransferFunction?): Boolean {
  TODO("Implement skcmsTransferFunctionIsPQish")
}

/**
 * C++ original:
 * ```cpp
 * bool skcms_TransferFunction_isHLGish(const skcms_TransferFunction* tf) {
 *     return classify(*tf) == skcms_TFType_HLGish;
 * }
 * ```
 */
public fun skcmsTransferFunctionIsHLGish(tf: SkcmsTransferFunction?): Boolean {
  TODO("Implement skcmsTransferFunctionIsHLGish")
}

/**
 * C++ original:
 * ```cpp
 * bool skcms_TransferFunction_isPQ(const skcms_TransferFunction* tf) {
 *     return classify(*tf) == skcms_TFType_PQ;
 * }
 * ```
 */
public fun skcmsTransferFunctionIsPQ(tf: SkcmsTransferFunction?): Boolean {
  TODO("Implement skcmsTransferFunctionIsPQ")
}

/**
 * C++ original:
 * ```cpp
 * bool skcms_TransferFunction_isHLG(const skcms_TransferFunction* tf) {
 *     return classify(*tf) == skcms_TFType_HLG;
 * }
 * ```
 */
public fun skcmsTransferFunctionIsHLG(tf: SkcmsTransferFunction?): Boolean {
  TODO("Implement skcmsTransferFunctionIsHLG")
}

/**
 * C++ original:
 * ```cpp
 * bool skcms_TransferFunction_makePQish(skcms_TransferFunction* tf,
 *                                       float A, float B, float C,
 *                                       float D, float E, float F) {
 *     *tf = { TFKind_marker(skcms_TFType_PQish), A,B,C,D,E,F };
 *     assert(skcms_TransferFunction_isPQish(tf));
 *     return true;
 * }
 * ```
 */
public fun skcmsTransferFunctionMakePQish(
  tf: SkcmsTransferFunction?,
  a: Float,
  b: Float,
  c: Float,
  d: Float,
  e: Float,
  f: Float,
): Boolean {
  TODO("Implement skcmsTransferFunctionMakePQish")
}

/**
 * C++ original:
 * ```cpp
 * bool skcms_TransferFunction_makeScaledHLGish(skcms_TransferFunction* tf,
 *                                              float K, float R, float G,
 *                                              float a, float b, float c) {
 *     *tf = { TFKind_marker(skcms_TFType_HLGish), R,G, a,b,c, K-1.0f };
 *     assert(skcms_TransferFunction_isHLGish(tf));
 *     return true;
 * }
 * ```
 */
public fun skcmsTransferFunctionMakeScaledHLGish(
  tf: SkcmsTransferFunction?,
  k: Float,
  r: Float,
  g: Float,
  a: Float,
  b: Float,
  c: Float,
): Boolean {
  TODO("Implement skcmsTransferFunctionMakeScaledHLGish")
}

/**
 * C++ original:
 * ```cpp
 * void skcms_TransferFunction_makePQ(
 *     skcms_TransferFunction* tf,
 *     float hdr_reference_white_luminance) {
 *     *tf = { TFKind_marker(skcms_TFType_PQ),
 *             hdr_reference_white_luminance,
 *             0.f,0.f,0.f,0.f,0.f };
 *     assert(skcms_TransferFunction_isPQ(tf));
 * }
 * ```
 */
public fun skcmsTransferFunctionMakePQ(tf: SkcmsTransferFunction?, hdrReferenceWhiteLuminance: Float) {
  TODO("Implement skcmsTransferFunctionMakePQ")
}

/**
 * C++ original:
 * ```cpp
 * void skcms_TransferFunction_makeHLG(
 *     skcms_TransferFunction* tf,
 *     float hdr_reference_white_luminance,
 *     float peak_luminance,
 *     float system_gamma) {
 *     *tf = { TFKind_marker(skcms_TFType_HLG),
 *             hdr_reference_white_luminance,
 *             peak_luminance,
 *             system_gamma,
 *             0.f, 0.f, 0.f };
 *     assert(skcms_TransferFunction_isHLG(tf));
 * }
 * ```
 */
public fun skcmsTransferFunctionMakeHLG(
  tf: SkcmsTransferFunction?,
  hdrReferenceWhiteLuminance: Float,
  peakLuminance: Float,
  systemGamma: Float,
) {
  TODO("Implement skcmsTransferFunctionMakeHLG")
}

/**
 * C++ original:
 * ```cpp
 * float skcms_TransferFunction_eval(const skcms_TransferFunction* tf, float x) {
 *     float sign = x < 0 ? -1.0f : 1.0f;
 *     x *= sign;
 *
 *     TF_PQish  pq;
 *     TF_HLGish hlg;
 *     switch (classify(*tf, &pq, &hlg)) {
 *         case skcms_TFType_Invalid: break;
 *
 *         case skcms_TFType_HLG: {
 *             const float a = 0.17883277f;
 *             const float b = 0.28466892f;
 *             const float c = 0.55991073f;
 *             return sign * (x <= 0.5f ? x*x/3.f : (expf_((x-c)/a) + b) / 12.f);
 *         }
 *
 *         case skcms_TFType_HLGish: {
 *             const float K = hlg.K_minus_1 + 1.0f;
 *             return K * sign * (x*hlg.R <= 1 ? powf_(x*hlg.R, hlg.G)
 *                                             : expf_((x-hlg.c)*hlg.a) + hlg.b);
 *         }
 *
 *         // skcms_TransferFunction_invert() inverts R, G, and a for HLGinvish so this math is fast.
 *         case skcms_TFType_HLGinvish: {
 *             const float K = hlg.K_minus_1 + 1.0f;
 *             x /= K;
 *             return sign * (x <= 1 ? hlg.R * powf_(x, hlg.G)
 *                                   : hlg.a * logf_(x - hlg.b) + hlg.c);
 *         }
 *
 *         case skcms_TFType_sRGBish:
 *             return sign * (x < tf->d ?       tf->c * x + tf->f
 *                                      : powf_(tf->a * x + tf->b, tf->g) + tf->e);
 *
 *         case skcms_TFType_PQ: {
 *             const float c1 =  107 / 128.f;
 *             const float c2 = 2413 / 128.f;
 *             const float c3 = 2392 / 128.f;
 *             const float m1 = 1305 / 8192.f;
 *             const float m2 = 2523 / 32.f;
 *             const float p = powf_(x, 1.f / m2);
 *             return powf_((p - c1) / (c2 - c3 * p), 1.f / m1);
 *         }
 *
 *         case skcms_TFType_PQish:
 *             return sign *
 *                    powf_((pq.A + pq.B * powf_(x, pq.C)) / (pq.D + pq.E * powf_(x, pq.C)), pq.F);
 *     }
 *     return 0;
 * }
 * ```
 */
public fun skcmsTransferFunctionEval(tf: SkcmsTransferFunction?, x: Float): Float {
  TODO("Implement skcmsTransferFunctionEval")
}

/**
 * C++ original:
 * ```cpp
 * static float eval_curve(const skcms_Curve* curve, float x) {
 *     if (curve->table_entries == 0) {
 *         return skcms_TransferFunction_eval(&curve->parametric, x);
 *     }
 *
 *     float ix = fmaxf_(0, fminf_(x, 1)) * static_cast<float>(curve->table_entries - 1);
 *     int   lo = (int)                   ix        ,
 *           hi = (int)(float)minus_1_ulp(ix + 1.0f);
 *     float t = ix - (float)lo;
 *
 *     float l, h;
 *     if (curve->table_8) {
 *         l = curve->table_8[lo] * (1/255.0f);
 *         h = curve->table_8[hi] * (1/255.0f);
 *     } else {
 *         uint16_t be_l, be_h;
 *         memcpy(&be_l, curve->table_16 + 2*lo, 2);
 *         memcpy(&be_h, curve->table_16 + 2*hi, 2);
 *         uint16_t le_l = ((be_l << 8) | (be_l >> 8)) & 0xffff;
 *         uint16_t le_h = ((be_h << 8) | (be_h >> 8)) & 0xffff;
 *         l = le_l * (1/65535.0f);
 *         h = le_h * (1/65535.0f);
 *     }
 *     return l + (h-l)*t;
 * }
 * ```
 */
public fun evalCurve(curve: SkcmsCurve?, x: Float): Float {
  TODO("Implement evalCurve")
}

/**
 * C++ original:
 * ```cpp
 * float skcms_MaxRoundtripError(const skcms_Curve* curve, const skcms_TransferFunction* inv_tf) {
 *     uint32_t N = curve->table_entries > 256 ? curve->table_entries : 256;
 *     const float dx = 1.0f / static_cast<float>(N - 1);
 *     float err = 0;
 *     for (uint32_t i = 0; i < N; i++) {
 *         float x = static_cast<float>(i) * dx,
 *               y = eval_curve(curve, x);
 *         err = fmaxf_(err, fabsf_(x - skcms_TransferFunction_eval(inv_tf, y)));
 *     }
 *     return err;
 * }
 * ```
 */
public fun skcmsMaxRoundtripError(curve: SkcmsCurve?, invTf: SkcmsTransferFunction?): Float {
  TODO("Implement skcmsMaxRoundtripError")
}

/**
 * C++ original:
 * ```cpp
 * bool skcms_AreApproximateInverses(const skcms_Curve* curve, const skcms_TransferFunction* inv_tf) {
 *     return skcms_MaxRoundtripError(curve, inv_tf) < (1/512.0f);
 * }
 * ```
 */
public fun skcmsAreApproximateInverses(curve: SkcmsCurve?, invTf: SkcmsTransferFunction?): Boolean {
  TODO("Implement skcmsAreApproximateInverses")
}

/**
 * C++ original:
 * ```cpp
 * static uint16_t read_big_u16(const uint8_t* ptr) {
 *     uint16_t be;
 *     memcpy(&be, ptr, sizeof(be));
 * #if defined(_MSC_VER)
 *     return _byteswap_ushort(be);
 * #else
 *     return __builtin_bswap16(be);
 * #endif
 * }
 * ```
 */
public fun readBigU16(ptr: UByte?): UShort {
  TODO("Implement readBigU16")
}

/**
 * C++ original:
 * ```cpp
 * static uint32_t read_big_u32(const uint8_t* ptr) {
 *     uint32_t be;
 *     memcpy(&be, ptr, sizeof(be));
 * #if defined(_MSC_VER)
 *     return _byteswap_ulong(be);
 * #else
 *     return __builtin_bswap32(be);
 * #endif
 * }
 * ```
 */
public fun readBigU32(ptr: UByte?): UInt {
  TODO("Implement readBigU32")
}

/**
 * C++ original:
 * ```cpp
 * static int32_t read_big_i32(const uint8_t* ptr) {
 *     return (int32_t)read_big_u32(ptr);
 * }
 * ```
 */
public fun readBigI32(ptr: UByte?): Int {
  TODO("Implement readBigI32")
}

/**
 * C++ original:
 * ```cpp
 * static float read_big_fixed(const uint8_t* ptr) {
 *     return static_cast<float>(read_big_i32(ptr)) * (1.0f / 65536.0f);
 * }
 * ```
 */
public fun readBigFixed(ptr: UByte?): Float {
  TODO("Implement readBigFixed")
}

/**
 * C++ original:
 * ```cpp
 * static const tag_Layout* get_tag_table(const skcms_ICCProfile* profile) {
 *     return (const tag_Layout*)(profile->buffer + SAFE_SIZEOF(header_Layout));
 * }
 * ```
 */
public fun getTagTable(profile: SkcmsICCProfile?): TagLayout {
  TODO("Implement getTagTable")
}

/**
 * C++ original:
 * ```cpp
 * bool skcms_GetCHAD(const skcms_ICCProfile* profile, skcms_Matrix3x3* m) {
 *     skcms_ICCTag tag;
 *     if (!skcms_GetTagBySignature(profile, skcms_Signature_CHAD, &tag)) {
 *         return false;
 *     }
 *
 *     if (tag.type != skcms_Signature_sf32 || tag.size < SAFE_SIZEOF(sf32_Layout)) {
 *         return false;
 *     }
 *
 *     const sf32_Layout* sf32Tag = (const sf32_Layout*)tag.buf;
 *     const uint8_t* values = sf32Tag->values;
 *     for (int r = 0; r < 3; ++r)
 *     for (int c = 0; c < 3; ++c, values += 4) {
 *         m->vals[r][c] = read_big_fixed(values);
 *     }
 *     return true;
 * }
 * ```
 */
public fun skcmsGetCHAD(profile: SkcmsICCProfile?, m: SkcmsMatrix3x3?): Boolean {
  TODO("Implement skcmsGetCHAD")
}

/**
 * C++ original:
 * ```cpp
 * static bool read_tag_xyz(const skcms_ICCTag* tag, float* x, float* y, float* z) {
 *     if (tag->type != skcms_Signature_XYZ || tag->size < SAFE_SIZEOF(XYZ_Layout)) {
 *         return false;
 *     }
 *
 *     const XYZ_Layout* xyzTag = (const XYZ_Layout*)tag->buf;
 *
 *     *x = read_big_fixed(xyzTag->X);
 *     *y = read_big_fixed(xyzTag->Y);
 *     *z = read_big_fixed(xyzTag->Z);
 *     return true;
 * }
 * ```
 */
public fun readTagXyz(
  tag: SkcmsICCTag?,
  x: Float?,
  y: Float?,
  z: Float?,
): Boolean {
  TODO("Implement readTagXyz")
}

/**
 * C++ original:
 * ```cpp
 * bool skcms_GetWTPT(const skcms_ICCProfile* profile, float xyz[3]) {
 *     skcms_ICCTag tag;
 *     return skcms_GetTagBySignature(profile, skcms_Signature_WTPT, &tag) &&
 *            read_tag_xyz(&tag, &xyz[0], &xyz[1], &xyz[2]);
 * }
 * ```
 */
public fun skcmsGetWTPT(profile: SkcmsICCProfile?, xyz: FloatArray): Boolean {
  TODO("Implement skcmsGetWTPT")
}

/**
 * C++ original:
 * ```cpp
 * static int data_color_space_channel_count(uint32_t data_color_space) {
 *     switch (data_color_space) {
 *         case skcms_Signature_CMYK:   return 4;
 *         case skcms_Signature_Gray:   return 1;
 *         case skcms_Signature_RGB:    return 3;
 *         case skcms_Signature_Lab:    return 3;
 *         case skcms_Signature_XYZ:    return 3;
 *         case skcms_Signature_CIELUV: return 3;
 *         case skcms_Signature_YCbCr:  return 3;
 *         case skcms_Signature_CIEYxy: return 3;
 *         case skcms_Signature_HSV:    return 3;
 *         case skcms_Signature_HLS:    return 3;
 *         case skcms_Signature_CMY:    return 3;
 *         case skcms_Signature_2CLR:   return 2;
 *         case skcms_Signature_3CLR:   return 3;
 *         case skcms_Signature_4CLR:   return 4;
 *         case skcms_Signature_5CLR:   return 5;
 *         case skcms_Signature_6CLR:   return 6;
 *         case skcms_Signature_7CLR:   return 7;
 *         case skcms_Signature_8CLR:   return 8;
 *         case skcms_Signature_9CLR:   return 9;
 *         case skcms_Signature_10CLR:  return 10;
 *         case skcms_Signature_11CLR:  return 11;
 *         case skcms_Signature_12CLR:  return 12;
 *         case skcms_Signature_13CLR:  return 13;
 *         case skcms_Signature_14CLR:  return 14;
 *         case skcms_Signature_15CLR:  return 15;
 *         default:                     return -1;
 *     }
 * }
 * ```
 */
public fun dataColorSpaceChannelCount(dataColorSpace: UInt): Int {
  TODO("Implement dataColorSpaceChannelCount")
}

/**
 * C++ original:
 * ```cpp
 * int skcms_GetInputChannelCount(const skcms_ICCProfile* profile) {
 *     int a2b_count = 0;
 *     if (profile->has_A2B) {
 *         a2b_count = profile->A2B.input_channels != 0
 *                         ? static_cast<int>(profile->A2B.input_channels)
 *                         : 3;
 *     }
 *
 *     skcms_ICCTag tag;
 *     int trc_count = 0;
 *     if (skcms_GetTagBySignature(profile, skcms_Signature_kTRC, &tag)) {
 *         trc_count = 1;
 *     } else if (profile->has_trc) {
 *         trc_count = 3;
 *     }
 *
 *     int dcs_count = data_color_space_channel_count(profile->data_color_space);
 *
 *     if (dcs_count < 0) {
 *         return -1;
 *     }
 *
 *     if (a2b_count > 0 && a2b_count != dcs_count) {
 *         return -1;
 *     }
 *     if (trc_count > 0 && trc_count != dcs_count) {
 *         return -1;
 *     }
 *
 *     return dcs_count;
 * }
 * ```
 */
public fun skcmsGetInputChannelCount(profile: SkcmsICCProfile?): Int {
  TODO("Implement skcmsGetInputChannelCount")
}

/**
 * C++ original:
 * ```cpp
 * static bool read_to_XYZD50(const skcms_ICCTag* rXYZ, const skcms_ICCTag* gXYZ,
 *                            const skcms_ICCTag* bXYZ, skcms_Matrix3x3* toXYZ) {
 *     return read_tag_xyz(rXYZ, &toXYZ->vals[0][0], &toXYZ->vals[1][0], &toXYZ->vals[2][0]) &&
 *            read_tag_xyz(gXYZ, &toXYZ->vals[0][1], &toXYZ->vals[1][1], &toXYZ->vals[2][1]) &&
 *            read_tag_xyz(bXYZ, &toXYZ->vals[0][2], &toXYZ->vals[1][2], &toXYZ->vals[2][2]);
 * }
 * ```
 */
public fun readToXYZD50(
  rXYZ: SkcmsICCTag?,
  gXYZ: SkcmsICCTag?,
  bXYZ: SkcmsICCTag?,
  toXYZ: SkcmsMatrix3x3?,
): Boolean {
  TODO("Implement readToXYZD50")
}

/**
 * C++ original:
 * ```cpp
 * static bool read_curve_para(const uint8_t* buf, uint32_t size,
 *                             skcms_Curve* curve, uint32_t* curve_size) {
 *     if (size < SAFE_FIXED_SIZE(para_Layout)) {
 *         return false;
 *     }
 *
 *     const para_Layout* paraTag = (const para_Layout*)buf;
 *
 *     enum { kG = 0, kGAB = 1, kGABC = 2, kGABCD = 3, kGABCDEF = 4 };
 *     uint16_t function_type = read_big_u16(paraTag->function_type);
 *     if (function_type > kGABCDEF) {
 *         return false;
 *     }
 *
 *     static const uint32_t curve_bytes[] = { 4, 12, 16, 20, 28 };
 *     if (size < SAFE_FIXED_SIZE(para_Layout) + curve_bytes[function_type]) {
 *         return false;
 *     }
 *
 *     if (curve_size) {
 *         *curve_size = SAFE_FIXED_SIZE(para_Layout) + curve_bytes[function_type];
 *     }
 *
 *     curve->table_entries = 0;
 *     curve->parametric.a  = 1.0f;
 *     curve->parametric.b  = 0.0f;
 *     curve->parametric.c  = 0.0f;
 *     curve->parametric.d  = 0.0f;
 *     curve->parametric.e  = 0.0f;
 *     curve->parametric.f  = 0.0f;
 *     curve->parametric.g  = read_big_fixed(paraTag->variable);
 *
 *     switch (function_type) {
 *         case kGAB:
 *             curve->parametric.a = read_big_fixed(paraTag->variable + 4);
 *             curve->parametric.b = read_big_fixed(paraTag->variable + 8);
 *             if (curve->parametric.a == 0) {
 *                 return false;
 *             }
 *             curve->parametric.d = -curve->parametric.b / curve->parametric.a;
 *             break;
 *         case kGABC:
 *             curve->parametric.a = read_big_fixed(paraTag->variable + 4);
 *             curve->parametric.b = read_big_fixed(paraTag->variable + 8);
 *             curve->parametric.e = read_big_fixed(paraTag->variable + 12);
 *             if (curve->parametric.a == 0) {
 *                 return false;
 *             }
 *             curve->parametric.d = -curve->parametric.b / curve->parametric.a;
 *             curve->parametric.f = curve->parametric.e;
 *             break;
 *         case kGABCD:
 *             curve->parametric.a = read_big_fixed(paraTag->variable + 4);
 *             curve->parametric.b = read_big_fixed(paraTag->variable + 8);
 *             curve->parametric.c = read_big_fixed(paraTag->variable + 12);
 *             curve->parametric.d = read_big_fixed(paraTag->variable + 16);
 *             break;
 *         case kGABCDEF:
 *             curve->parametric.a = read_big_fixed(paraTag->variable + 4);
 *             curve->parametric.b = read_big_fixed(paraTag->variable + 8);
 *             curve->parametric.c = read_big_fixed(paraTag->variable + 12);
 *             curve->parametric.d = read_big_fixed(paraTag->variable + 16);
 *             curve->parametric.e = read_big_fixed(paraTag->variable + 20);
 *             curve->parametric.f = read_big_fixed(paraTag->variable + 24);
 *             break;
 *     }
 *     return skcms_TransferFunction_isSRGBish(&curve->parametric);
 * }
 * ```
 */
public fun readCurvePara(
  buf: UByte?,
  size: UInt,
  curve: SkcmsCurve?,
  curveSize: UInt?,
): Boolean {
  TODO("Implement readCurvePara")
}

/**
 * C++ original:
 * ```cpp
 * static bool read_curve_curv(const uint8_t* buf, uint32_t size,
 *                             skcms_Curve* curve, uint32_t* curve_size) {
 *     if (size < SAFE_FIXED_SIZE(curv_Layout)) {
 *         return false;
 *     }
 *
 *     const curv_Layout* curvTag = (const curv_Layout*)buf;
 *
 *     uint32_t value_count = read_big_u32(curvTag->value_count);
 *     if (size < SAFE_FIXED_SIZE(curv_Layout) + value_count * SAFE_SIZEOF(uint16_t)) {
 *         return false;
 *     }
 *
 *     if (curve_size) {
 *         *curve_size = SAFE_FIXED_SIZE(curv_Layout) + value_count * SAFE_SIZEOF(uint16_t);
 *     }
 *
 *     if (value_count < 2) {
 *         curve->table_entries = 0;
 *         curve->parametric.a  = 1.0f;
 *         curve->parametric.b  = 0.0f;
 *         curve->parametric.c  = 0.0f;
 *         curve->parametric.d  = 0.0f;
 *         curve->parametric.e  = 0.0f;
 *         curve->parametric.f  = 0.0f;
 *         if (value_count == 0) {
 *             // Empty tables are a shorthand for an identity curve
 *             curve->parametric.g = 1.0f;
 *         } else {
 *             // Single entry tables are a shorthand for simple gamma
 *             curve->parametric.g = read_big_u16(curvTag->variable) * (1.0f / 256.0f);
 *         }
 *     } else {
 *         curve->table_8       = nullptr;
 *         curve->table_16      = curvTag->variable;
 *         curve->table_entries = value_count;
 *     }
 *
 *     return true;
 * }
 * ```
 */
public fun readCurveCurv(
  buf: UByte?,
  size: UInt,
  curve: SkcmsCurve?,
  curveSize: UInt?,
): Boolean {
  TODO("Implement readCurveCurv")
}

/**
 * C++ original:
 * ```cpp
 * static bool read_curve(const uint8_t* buf, uint32_t size,
 *                        skcms_Curve* curve, uint32_t* curve_size) {
 *     if (!buf || size < 4 || !curve) {
 *         return false;
 *     }
 *
 *     uint32_t type = read_big_u32(buf);
 *     if (type == skcms_Signature_para) {
 *         return read_curve_para(buf, size, curve, curve_size);
 *     } else if (type == skcms_Signature_curv) {
 *         return read_curve_curv(buf, size, curve, curve_size);
 *     }
 *
 *     return false;
 * }
 * ```
 */
public fun readCurve(
  buf: UByte?,
  size: UInt,
  curve: SkcmsCurve?,
  curveSize: UInt?,
): Boolean {
  TODO("Implement readCurve")
}

/**
 * C++ original:
 * ```cpp
 * static bool read_mft_common(const mft_CommonLayout* mftTag, skcms_B2A* b2a) {
 *     // Same as A2B.
 *     b2a->matrix_channels = 0;
 *     b2a-> input_channels = mftTag-> input_channels[0];
 *     b2a->output_channels = mftTag->output_channels[0];
 *
 *
 *     // For B2A, exactly 3 input channels (XYZ) and 3 (RGB) or 4 (CMYK) output channels.
 *     if (b2a->input_channels != ARRAY_COUNT(b2a->input_curves)) {
 *         return false;
 *     }
 *     if (b2a->output_channels < 3 || b2a->output_channels > ARRAY_COUNT(b2a->output_curves)) {
 *         return false;
 *     }
 *
 *     // Same as A2B.
 *     for (uint32_t i = 0; i < b2a->input_channels; ++i) {
 *         b2a->grid_points[i] = mftTag->grid_points[0];
 *     }
 *     if (b2a->grid_points[0] < 2) {
 *         return false;
 *     }
 *     return true;
 * }
 * ```
 */
public fun readMftCommon(mftTag: MftCommonLayout?, b2a: SkcmsB2A?): Boolean {
  TODO("Implement readMftCommon")
}

/**
 * C++ original:
 * ```cpp
 * template <typename A2B_or_B2A>
 * static bool init_tables(const uint8_t* table_base, uint64_t max_tables_len, uint32_t byte_width,
 *                         uint32_t input_table_entries, uint32_t output_table_entries,
 *                         A2B_or_B2A* out) {
 *     // byte_width is 1 or 2, [input|output]_table_entries are in [2, 4096], so no overflow
 *     uint32_t byte_len_per_input_table  = input_table_entries * byte_width;
 *     uint32_t byte_len_per_output_table = output_table_entries * byte_width;
 *
 *     // [input|output]_channels are <= 4, so still no overflow
 *     uint32_t byte_len_all_input_tables  = out->input_channels * byte_len_per_input_table;
 *     uint32_t byte_len_all_output_tables = out->output_channels * byte_len_per_output_table;
 *
 *     uint64_t grid_size = out->output_channels * byte_width;
 *     for (uint32_t axis = 0; axis < out->input_channels; ++axis) {
 *         grid_size *= out->grid_points[axis];
 *     }
 *
 *     if (max_tables_len < byte_len_all_input_tables + grid_size + byte_len_all_output_tables) {
 *         return false;
 *     }
 *
 *     for (uint32_t i = 0; i < out->input_channels; ++i) {
 *         out->input_curves[i].table_entries = input_table_entries;
 *         if (byte_width == 1) {
 *             out->input_curves[i].table_8  = table_base + i * byte_len_per_input_table;
 *             out->input_curves[i].table_16 = nullptr;
 *         } else {
 *             out->input_curves[i].table_8  = nullptr;
 *             out->input_curves[i].table_16 = table_base + i * byte_len_per_input_table;
 *         }
 *     }
 *
 *     if (byte_width == 1) {
 *         out->grid_8  = table_base + byte_len_all_input_tables;
 *         out->grid_16 = nullptr;
 *     } else {
 *         out->grid_8  = nullptr;
 *         out->grid_16 = table_base + byte_len_all_input_tables;
 *     }
 *
 *     const uint8_t* output_table_base = table_base + byte_len_all_input_tables + grid_size;
 *     for (uint32_t i = 0; i < out->output_channels; ++i) {
 *         out->output_curves[i].table_entries = output_table_entries;
 *         if (byte_width == 1) {
 *             out->output_curves[i].table_8  = output_table_base + i * byte_len_per_output_table;
 *             out->output_curves[i].table_16 = nullptr;
 *         } else {
 *             out->output_curves[i].table_8  = nullptr;
 *             out->output_curves[i].table_16 = output_table_base + i * byte_len_per_output_table;
 *         }
 *     }
 *
 *     return true;
 * }
 * ```
 */
public fun <A2B_or_B2A> initTables(
  tableBase: UByte?,
  maxTablesLen: ULong,
  byteWidth: UInt,
  inputTableEntries: UInt,
  outputTableEntries: UInt,
  `out`: A2B_or_B2A,
): Boolean {
  TODO("Implement initTables")
}

/**
 * C++ original:
 * ```cpp
 * template <typename A2B_or_B2A>
 * static bool read_tag_mft1(const skcms_ICCTag* tag, A2B_or_B2A* out) {
 *     if (tag->size < SAFE_FIXED_SIZE(mft1_Layout)) {
 *         return false;
 *     }
 *
 *     const mft1_Layout* mftTag = (const mft1_Layout*)tag->buf;
 *     if (!read_mft_common(mftTag->common, out)) {
 *         return false;
 *     }
 *
 *     uint32_t input_table_entries  = 256;
 *     uint32_t output_table_entries = 256;
 *
 *     return init_tables(mftTag->variable, tag->size - SAFE_FIXED_SIZE(mft1_Layout), 1,
 *                        input_table_entries, output_table_entries, out);
 * }
 * ```
 */
public fun <A2B_or_B2A> readTagMft1(tag: SkcmsICCTag?, `out`: A2B_or_B2A): Boolean {
  TODO("Implement readTagMft1")
}

/**
 * C++ original:
 * ```cpp
 * template <typename A2B_or_B2A>
 * static bool read_tag_mft2(const skcms_ICCTag* tag, A2B_or_B2A* out) {
 *     if (tag->size < SAFE_FIXED_SIZE(mft2_Layout)) {
 *         return false;
 *     }
 *
 *     const mft2_Layout* mftTag = (const mft2_Layout*)tag->buf;
 *     if (!read_mft_common(mftTag->common, out)) {
 *         return false;
 *     }
 *
 *     uint32_t input_table_entries = read_big_u16(mftTag->input_table_entries);
 *     uint32_t output_table_entries = read_big_u16(mftTag->output_table_entries);
 *
 *     // ICC spec mandates that 2 <= table_entries <= 4096
 *     if (input_table_entries < 2 || input_table_entries > 4096 ||
 *         output_table_entries < 2 || output_table_entries > 4096) {
 *         return false;
 *     }
 *
 *     return init_tables(mftTag->variable, tag->size - SAFE_FIXED_SIZE(mft2_Layout), 2,
 *                        input_table_entries, output_table_entries, out);
 * }
 * ```
 */
public fun <A2B_or_B2A> readTagMft2(tag: SkcmsICCTag?, `out`: A2B_or_B2A): Boolean {
  TODO("Implement readTagMft2")
}

/**
 * C++ original:
 * ```cpp
 * static bool read_curves(const uint8_t* buf, uint32_t size, uint32_t curve_offset,
 *                         uint32_t num_curves, skcms_Curve* curves) {
 *     for (uint32_t i = 0; i < num_curves; ++i) {
 *         if (curve_offset > size) {
 *             return false;
 *         }
 *
 *         uint32_t curve_bytes;
 *         if (!read_curve(buf + curve_offset, size - curve_offset, &curves[i], &curve_bytes)) {
 *             return false;
 *         }
 *
 *         if (curve_bytes > UINT32_MAX - 3) {
 *             return false;
 *         }
 *         curve_bytes = (curve_bytes + 3) & ~3U;
 *
 *         uint64_t new_offset_64 = (uint64_t)curve_offset + curve_bytes;
 *         curve_offset = (uint32_t)new_offset_64;
 *         if (new_offset_64 != curve_offset) {
 *             return false;
 *         }
 *     }
 *
 *     return true;
 * }
 * ```
 */
public fun readCurves(
  buf: UByte?,
  size: UInt,
  curveOffset: UInt,
  numCurves: UInt,
  curves: SkcmsCurve?,
): Boolean {
  TODO("Implement readCurves")
}

/**
 * C++ original:
 * ```cpp
 * static bool read_tag_mab(const skcms_ICCTag* tag, skcms_A2B* a2b, bool pcs_is_xyz) {
 *     if (tag->size < SAFE_SIZEOF(mAB_or_mBA_Layout)) {
 *         return false;
 *     }
 *
 *     const mAB_or_mBA_Layout* mABTag = (const mAB_or_mBA_Layout*)tag->buf;
 *
 *     a2b->input_channels  = mABTag->input_channels[0];
 *     a2b->output_channels = mABTag->output_channels[0];
 *
 *     // We require exactly three (ie XYZ/Lab/RGB) output channels
 *     if (a2b->output_channels != ARRAY_COUNT(a2b->output_curves)) {
 *         return false;
 *     }
 *     // We require no more than four (ie CMYK) input channels
 *     if (a2b->input_channels > ARRAY_COUNT(a2b->input_curves)) {
 *         return false;
 *     }
 *
 *     uint32_t b_curve_offset = read_big_u32(mABTag->b_curve_offset);
 *     uint32_t matrix_offset  = read_big_u32(mABTag->matrix_offset);
 *     uint32_t m_curve_offset = read_big_u32(mABTag->m_curve_offset);
 *     uint32_t clut_offset    = read_big_u32(mABTag->clut_offset);
 *     uint32_t a_curve_offset = read_big_u32(mABTag->a_curve_offset);
 *
 *     // "B" curves must be present
 *     if (0 == b_curve_offset) {
 *         return false;
 *     }
 *
 *     if (!read_curves(tag->buf, tag->size, b_curve_offset, a2b->output_channels,
 *                      a2b->output_curves)) {
 *         return false;
 *     }
 *
 *     // "M" curves and Matrix must be used together
 *     if (0 != m_curve_offset) {
 *         if (0 == matrix_offset) {
 *             return false;
 *         }
 *         a2b->matrix_channels = a2b->output_channels;
 *         if (!read_curves(tag->buf, tag->size, m_curve_offset, a2b->matrix_channels,
 *                          a2b->matrix_curves)) {
 *             return false;
 *         }
 *
 *         // Read matrix, which is stored as a row-major 3x3, followed by the fourth column
 *         if (tag->size < matrix_offset + 12 * SAFE_SIZEOF(uint32_t)) {
 *             return false;
 *         }
 *         float encoding_factor = pcs_is_xyz ? (65535 / 32768.0f) : 1.0f;
 *         const uint8_t* mtx_buf = tag->buf + matrix_offset;
 *         a2b->matrix.vals[0][0] = encoding_factor * read_big_fixed(mtx_buf +  0);
 *         a2b->matrix.vals[0][1] = encoding_factor * read_big_fixed(mtx_buf +  4);
 *         a2b->matrix.vals[0][2] = encoding_factor * read_big_fixed(mtx_buf +  8);
 *         a2b->matrix.vals[1][0] = encoding_factor * read_big_fixed(mtx_buf + 12);
 *         a2b->matrix.vals[1][1] = encoding_factor * read_big_fixed(mtx_buf + 16);
 *         a2b->matrix.vals[1][2] = encoding_factor * read_big_fixed(mtx_buf + 20);
 *         a2b->matrix.vals[2][0] = encoding_factor * read_big_fixed(mtx_buf + 24);
 *         a2b->matrix.vals[2][1] = encoding_factor * read_big_fixed(mtx_buf + 28);
 *         a2b->matrix.vals[2][2] = encoding_factor * read_big_fixed(mtx_buf + 32);
 *         a2b->matrix.vals[0][3] = encoding_factor * read_big_fixed(mtx_buf + 36);
 *         a2b->matrix.vals[1][3] = encoding_factor * read_big_fixed(mtx_buf + 40);
 *         a2b->matrix.vals[2][3] = encoding_factor * read_big_fixed(mtx_buf + 44);
 *     } else {
 *         if (0 != matrix_offset) {
 *             return false;
 *         }
 *         a2b->matrix_channels = 0;
 *     }
 *
 *     // "A" curves and CLUT must be used together
 *     if (0 != a_curve_offset) {
 *         if (0 == clut_offset) {
 *             return false;
 *         }
 *         if (!read_curves(tag->buf, tag->size, a_curve_offset, a2b->input_channels,
 *                          a2b->input_curves)) {
 *             return false;
 *         }
 *
 *         if (tag->size < clut_offset + SAFE_FIXED_SIZE(CLUT_Layout)) {
 *             return false;
 *         }
 *         const CLUT_Layout* clut = (const CLUT_Layout*)(tag->buf + clut_offset);
 *
 *         if (clut->grid_byte_width[0] == 1) {
 *             a2b->grid_8  = clut->variable;
 *             a2b->grid_16 = nullptr;
 *         } else if (clut->grid_byte_width[0] == 2) {
 *             a2b->grid_8  = nullptr;
 *             a2b->grid_16 = clut->variable;
 *         } else {
 *             return false;
 *         }
 *
 *         uint64_t grid_size = a2b->output_channels * clut->grid_byte_width[0];  // the payload
 *         for (uint32_t i = 0; i < a2b->input_channels; ++i) {
 *             a2b->grid_points[i] = clut->grid_points[i];
 *             // The grid only makes sense with at least two points along each axis
 *             if (a2b->grid_points[i] < 2) {
 *                 return false;
 *             }
 *             grid_size *= a2b->grid_points[i];
 *         }
 *         if (tag->size < clut_offset + SAFE_FIXED_SIZE(CLUT_Layout) + grid_size) {
 *             return false;
 *         }
 *     } else {
 *         if (0 != clut_offset) {
 *             return false;
 *         }
 *
 *         // If there is no CLUT, the number of input and output channels must match
 *         if (a2b->input_channels != a2b->output_channels) {
 *             return false;
 *         }
 *
 *         // Zero out the number of input channels to signal that we're skipping this stage
 *         a2b->input_channels = 0;
 *     }
 *
 *     return true;
 * }
 * ```
 */
public fun readTagMab(
  tag: SkcmsICCTag?,
  a2b: SkcmsA2B?,
  pcsIsXyz: Boolean,
): Boolean {
  TODO("Implement readTagMab")
}

/**
 * C++ original:
 * ```cpp
 * static bool read_tag_mba(const skcms_ICCTag* tag, skcms_B2A* b2a, bool pcs_is_xyz) {
 *     if (tag->size < SAFE_SIZEOF(mAB_or_mBA_Layout)) {
 *         return false;
 *     }
 *
 *     const mAB_or_mBA_Layout* mBATag = (const mAB_or_mBA_Layout*)tag->buf;
 *
 *     b2a->input_channels  = mBATag->input_channels[0];
 *     b2a->output_channels = mBATag->output_channels[0];
 *
 *     // Require exactly 3 inputs (XYZ) and 3 (RGB) or 4 (CMYK) outputs.
 *     if (b2a->input_channels != ARRAY_COUNT(b2a->input_curves)) {
 *         return false;
 *     }
 *     if (b2a->output_channels < 3 || b2a->output_channels > ARRAY_COUNT(b2a->output_curves)) {
 *         return false;
 *     }
 *
 *     uint32_t b_curve_offset = read_big_u32(mBATag->b_curve_offset);
 *     uint32_t matrix_offset  = read_big_u32(mBATag->matrix_offset);
 *     uint32_t m_curve_offset = read_big_u32(mBATag->m_curve_offset);
 *     uint32_t clut_offset    = read_big_u32(mBATag->clut_offset);
 *     uint32_t a_curve_offset = read_big_u32(mBATag->a_curve_offset);
 *
 *     if (0 == b_curve_offset) {
 *         return false;
 *     }
 *
 *     // "B" curves are our inputs, not outputs.
 *     if (!read_curves(tag->buf, tag->size, b_curve_offset, b2a->input_channels,
 *                      b2a->input_curves)) {
 *         return false;
 *     }
 *
 *     if (0 != m_curve_offset) {
 *         if (0 == matrix_offset) {
 *             return false;
 *         }
 *         // Matrix channels is tied to input_channels (3), not output_channels.
 *         b2a->matrix_channels = b2a->input_channels;
 *
 *         if (!read_curves(tag->buf, tag->size, m_curve_offset, b2a->matrix_channels,
 *                          b2a->matrix_curves)) {
 *             return false;
 *         }
 *
 *         if (tag->size < matrix_offset + 12 * SAFE_SIZEOF(uint32_t)) {
 *             return false;
 *         }
 *         float encoding_factor = pcs_is_xyz ? (32768 / 65535.0f) : 1.0f;  // TODO: understand
 *         const uint8_t* mtx_buf = tag->buf + matrix_offset;
 *         b2a->matrix.vals[0][0] = encoding_factor * read_big_fixed(mtx_buf +  0);
 *         b2a->matrix.vals[0][1] = encoding_factor * read_big_fixed(mtx_buf +  4);
 *         b2a->matrix.vals[0][2] = encoding_factor * read_big_fixed(mtx_buf +  8);
 *         b2a->matrix.vals[1][0] = encoding_factor * read_big_fixed(mtx_buf + 12);
 *         b2a->matrix.vals[1][1] = encoding_factor * read_big_fixed(mtx_buf + 16);
 *         b2a->matrix.vals[1][2] = encoding_factor * read_big_fixed(mtx_buf + 20);
 *         b2a->matrix.vals[2][0] = encoding_factor * read_big_fixed(mtx_buf + 24);
 *         b2a->matrix.vals[2][1] = encoding_factor * read_big_fixed(mtx_buf + 28);
 *         b2a->matrix.vals[2][2] = encoding_factor * read_big_fixed(mtx_buf + 32);
 *         b2a->matrix.vals[0][3] = encoding_factor * read_big_fixed(mtx_buf + 36);
 *         b2a->matrix.vals[1][3] = encoding_factor * read_big_fixed(mtx_buf + 40);
 *         b2a->matrix.vals[2][3] = encoding_factor * read_big_fixed(mtx_buf + 44);
 *     } else {
 *         if (0 != matrix_offset) {
 *             return false;
 *         }
 *         b2a->matrix_channels = 0;
 *     }
 *
 *     if (0 != a_curve_offset) {
 *         if (0 == clut_offset) {
 *             return false;
 *         }
 *
 *         // "A" curves are our output, not input.
 *         if (!read_curves(tag->buf, tag->size, a_curve_offset, b2a->output_channels,
 *                          b2a->output_curves)) {
 *             return false;
 *         }
 *
 *         if (tag->size < clut_offset + SAFE_FIXED_SIZE(CLUT_Layout)) {
 *             return false;
 *         }
 *         const CLUT_Layout* clut = (const CLUT_Layout*)(tag->buf + clut_offset);
 *
 *         if (clut->grid_byte_width[0] == 1) {
 *             b2a->grid_8  = clut->variable;
 *             b2a->grid_16 = nullptr;
 *         } else if (clut->grid_byte_width[0] == 2) {
 *             b2a->grid_8  = nullptr;
 *             b2a->grid_16 = clut->variable;
 *         } else {
 *             return false;
 *         }
 *
 *         uint64_t grid_size = b2a->output_channels * clut->grid_byte_width[0];
 *         for (uint32_t i = 0; i < b2a->input_channels; ++i) {
 *             b2a->grid_points[i] = clut->grid_points[i];
 *             if (b2a->grid_points[i] < 2) {
 *                 return false;
 *             }
 *             grid_size *= b2a->grid_points[i];
 *         }
 *         if (tag->size < clut_offset + SAFE_FIXED_SIZE(CLUT_Layout) + grid_size) {
 *             return false;
 *         }
 *     } else {
 *         if (0 != clut_offset) {
 *             return false;
 *         }
 *
 *         if (b2a->input_channels != b2a->output_channels) {
 *             return false;
 *         }
 *
 *         // Zero out *output* channels to skip this stage.
 *         b2a->output_channels = 0;
 *     }
 *     return true;
 * }
 * ```
 */
public fun readTagMba(
  tag: SkcmsICCTag?,
  b2a: SkcmsB2A?,
  pcsIsXyz: Boolean,
): Boolean {
  TODO("Implement readTagMba")
}

/**
 * C++ original:
 * ```cpp
 * static int fit_linear(const skcms_Curve* curve, int N, float tol,
 *                       float* c, float* d, float* f = nullptr) {
 *     assert(N > 1);
 *     // We iteratively fit the first points to the TF's linear piece.
 *     // We want the cx + f line to pass through the first and last points we fit exactly.
 *     //
 *     // As we walk along the points we find the minimum and maximum slope of the line before the
 *     // error would exceed our tolerance.  We stop when the range [slope_min, slope_max] becomes
 *     // emtpy, when we definitely can't add any more points.
 *     //
 *     // Some points' error intervals may intersect the running interval but not lie fully
 *     // within it.  So we keep track of the last point we saw that is a valid end point candidate,
 *     // and once the search is done, back up to build the line through *that* point.
 *     const float dx = 1.0f / static_cast<float>(N - 1);
 *
 *     int lin_points = 1;
 *
 *     float f_zero = 0.0f;
 *     if (f) {
 *         *f = eval_curve(curve, 0);
 *     } else {
 *         f = &f_zero;
 *     }
 *
 *
 *     float slope_min = -INFINITY_;
 *     float slope_max = +INFINITY_;
 *     for (int i = 1; i < N; ++i) {
 *         float x = static_cast<float>(i) * dx;
 *         float y = eval_curve(curve, x);
 *
 *         float slope_max_i = (y + tol - *f) / x,
 *               slope_min_i = (y - tol - *f) / x;
 *         if (slope_max_i < slope_min || slope_max < slope_min_i) {
 *             // Slope intervals would no longer overlap.
 *             break;
 *         }
 *         slope_max = fminf_(slope_max, slope_max_i);
 *         slope_min = fmaxf_(slope_min, slope_min_i);
 *
 *         float cur_slope = (y - *f) / x;
 *         if (slope_min <= cur_slope && cur_slope <= slope_max) {
 *             lin_points = i + 1;
 *             *c = cur_slope;
 *         }
 *     }
 *
 *     // Set D to the last point that met our tolerance.
 *     *d = static_cast<float>(lin_points - 1) * dx;
 *     return lin_points;
 * }
 * ```
 */
public fun fitLinear(
  curve: SkcmsCurve?,
  n: Int,
  tol: Float,
  c: Float?,
  d: Float?,
  f: Float? = TODO(),
): Int {
  TODO("Implement fitLinear")
}

/**
 * C++ original:
 * ```cpp
 * static void canonicalize_identity(skcms_Curve* curve) {
 *     if (curve->table_entries && curve->table_entries <= (uint32_t)INT_MAX) {
 *         int N = (int)curve->table_entries;
 *
 *         float c = 0.0f, d = 0.0f, f = 0.0f;
 *         if (N == fit_linear(curve, N, 1.0f/static_cast<float>(2*N), &c,&d,&f)
 *             && c == 1.0f
 *             && f == 0.0f) {
 *             curve->table_entries = 0;
 *             curve->table_8       = nullptr;
 *             curve->table_16      = nullptr;
 *             curve->parametric    = skcms_TransferFunction{1,1,0,0,0,0,0};
 *         }
 *     }
 * }
 * ```
 */
public fun canonicalizeIdentity(curve: SkcmsCurve?) {
  TODO("Implement canonicalizeIdentity")
}

/**
 * C++ original:
 * ```cpp
 * static bool read_a2b(const skcms_ICCTag* tag, skcms_A2B* a2b, bool pcs_is_xyz) {
 *     bool ok = false;
 *     if (tag->type == skcms_Signature_mft1) { ok = read_tag_mft1(tag, a2b); }
 *     if (tag->type == skcms_Signature_mft2) { ok = read_tag_mft2(tag, a2b); }
 *     if (tag->type == skcms_Signature_mAB ) { ok = read_tag_mab(tag, a2b, pcs_is_xyz); }
 *     if (!ok) {
 *         return false;
 *     }
 *
 *     if (a2b->input_channels > 0) { canonicalize_identity(a2b->input_curves + 0); }
 *     if (a2b->input_channels > 1) { canonicalize_identity(a2b->input_curves + 1); }
 *     if (a2b->input_channels > 2) { canonicalize_identity(a2b->input_curves + 2); }
 *     if (a2b->input_channels > 3) { canonicalize_identity(a2b->input_curves + 3); }
 *
 *     if (a2b->matrix_channels > 0) { canonicalize_identity(a2b->matrix_curves + 0); }
 *     if (a2b->matrix_channels > 1) { canonicalize_identity(a2b->matrix_curves + 1); }
 *     if (a2b->matrix_channels > 2) { canonicalize_identity(a2b->matrix_curves + 2); }
 *
 *     if (a2b->output_channels > 0) { canonicalize_identity(a2b->output_curves + 0); }
 *     if (a2b->output_channels > 1) { canonicalize_identity(a2b->output_curves + 1); }
 *     if (a2b->output_channels > 2) { canonicalize_identity(a2b->output_curves + 2); }
 *
 *     return true;
 * }
 * ```
 */
public fun readA2b(
  tag: SkcmsICCTag?,
  a2b: SkcmsA2B?,
  pcsIsXyz: Boolean,
): Boolean {
  TODO("Implement readA2b")
}

/**
 * C++ original:
 * ```cpp
 * static bool read_b2a(const skcms_ICCTag* tag, skcms_B2A* b2a, bool pcs_is_xyz) {
 *     bool ok = false;
 *     if (tag->type == skcms_Signature_mft1) { ok = read_tag_mft1(tag, b2a); }
 *     if (tag->type == skcms_Signature_mft2) { ok = read_tag_mft2(tag, b2a); }
 *     if (tag->type == skcms_Signature_mBA ) { ok = read_tag_mba(tag, b2a, pcs_is_xyz); }
 *     if (!ok) {
 *         return false;
 *     }
 *
 *     if (b2a->input_channels > 0) { canonicalize_identity(b2a->input_curves + 0); }
 *     if (b2a->input_channels > 1) { canonicalize_identity(b2a->input_curves + 1); }
 *     if (b2a->input_channels > 2) { canonicalize_identity(b2a->input_curves + 2); }
 *
 *     if (b2a->matrix_channels > 0) { canonicalize_identity(b2a->matrix_curves + 0); }
 *     if (b2a->matrix_channels > 1) { canonicalize_identity(b2a->matrix_curves + 1); }
 *     if (b2a->matrix_channels > 2) { canonicalize_identity(b2a->matrix_curves + 2); }
 *
 *     if (b2a->output_channels > 0) { canonicalize_identity(b2a->output_curves + 0); }
 *     if (b2a->output_channels > 1) { canonicalize_identity(b2a->output_curves + 1); }
 *     if (b2a->output_channels > 2) { canonicalize_identity(b2a->output_curves + 2); }
 *     if (b2a->output_channels > 3) { canonicalize_identity(b2a->output_curves + 3); }
 *
 *     return true;
 * }
 * ```
 */
public fun readB2a(
  tag: SkcmsICCTag?,
  b2a: SkcmsB2A?,
  pcsIsXyz: Boolean,
): Boolean {
  TODO("Implement readB2a")
}

/**
 * C++ original:
 * ```cpp
 * static bool read_cicp(const skcms_ICCTag* tag, skcms_CICP* cicp) {
 *     if (tag->type != skcms_Signature_CICP || tag->size < SAFE_SIZEOF(CICP_Layout)) {
 *         return false;
 *     }
 *
 *     const CICP_Layout* cicpTag = (const CICP_Layout*)tag->buf;
 *
 *     cicp->color_primaries          = cicpTag->color_primaries[0];
 *     cicp->transfer_characteristics = cicpTag->transfer_characteristics[0];
 *     cicp->matrix_coefficients      = cicpTag->matrix_coefficients[0];
 *     cicp->video_full_range_flag    = cicpTag->video_full_range_flag[0];
 *     return true;
 * }
 * ```
 */
public fun readCicp(tag: SkcmsICCTag?, cicp: SkcmsCICP?): Boolean {
  TODO("Implement readCicp")
}

/**
 * C++ original:
 * ```cpp
 * void skcms_GetTagByIndex(const skcms_ICCProfile* profile, uint32_t idx, skcms_ICCTag* tag) {
 *     if (!profile || !profile->buffer || !tag) { return; }
 *     if (idx > profile->tag_count) { return; }
 *     const tag_Layout* tags = get_tag_table(profile);
 *     tag->signature = read_big_u32(tags[idx].signature);
 *     tag->size      = read_big_u32(tags[idx].size);
 *     tag->buf       = read_big_u32(tags[idx].offset) + profile->buffer;
 *     tag->type      = read_big_u32(tag->buf);
 * }
 * ```
 */
public fun skcmsGetTagByIndex(
  profile: SkcmsICCProfile?,
  idx: UInt,
  tag: SkcmsICCTag?,
) {
  TODO("Implement skcmsGetTagByIndex")
}

/**
 * C++ original:
 * ```cpp
 * bool skcms_GetTagBySignature(const skcms_ICCProfile* profile, uint32_t sig, skcms_ICCTag* tag) {
 *     if (!profile || !profile->buffer || !tag) { return false; }
 *     const tag_Layout* tags = get_tag_table(profile);
 *     for (uint32_t i = 0; i < profile->tag_count; ++i) {
 *         if (read_big_u32(tags[i].signature) == sig) {
 *             tag->signature = sig;
 *             tag->size      = read_big_u32(tags[i].size);
 *             tag->buf       = read_big_u32(tags[i].offset) + profile->buffer;
 *             tag->type      = read_big_u32(tag->buf);
 *             return true;
 *         }
 *     }
 *     return false;
 * }
 * ```
 */
public fun skcmsGetTagBySignature(
  profile: SkcmsICCProfile?,
  sig: UInt,
  tag: SkcmsICCTag?,
): Boolean {
  TODO("Implement skcmsGetTagBySignature")
}

/**
 * C++ original:
 * ```cpp
 * static bool usable_as_src(const skcms_ICCProfile* profile) {
 *     return profile->has_A2B
 *        || (profile->has_trc && profile->has_toXYZD50);
 * }
 * ```
 */
public fun usableAsSrc(profile: SkcmsICCProfile?): Boolean {
  TODO("Implement usableAsSrc")
}

/**
 * C++ original:
 * ```cpp
 * bool skcms_ParseWithA2BPriority(const void* buf, size_t len,
 *                                 const int priority[], const int priorities,
 *                                 skcms_ICCProfile* profile) {
 *     static_assert(SAFE_SIZEOF(header_Layout) == 132, "need to update header code");
 *
 *     if (!profile) {
 *         return false;
 *     }
 *     memset(profile, 0, SAFE_SIZEOF(*profile));
 *
 *     if (len < SAFE_SIZEOF(header_Layout)) {
 *         return false;
 *     }
 *
 *     // Byte-swap all header fields
 *     const header_Layout* header  = (const header_Layout*)buf;
 *     profile->buffer              = (const uint8_t*)buf;
 *     profile->size                = read_big_u32(header->size);
 *     uint32_t version             = read_big_u32(header->version);
 *     profile->data_color_space    = read_big_u32(header->data_color_space);
 *     profile->pcs                 = read_big_u32(header->pcs);
 *     uint32_t signature           = read_big_u32(header->signature);
 *     float illuminant_X           = read_big_fixed(header->illuminant_X);
 *     float illuminant_Y           = read_big_fixed(header->illuminant_Y);
 *     float illuminant_Z           = read_big_fixed(header->illuminant_Z);
 *     profile->tag_count           = read_big_u32(header->tag_count);
 *
 *     // Validate signature, size (smaller than buffer, large enough to hold tag table),
 *     // and major version
 *     uint64_t tag_table_size = profile->tag_count * SAFE_SIZEOF(tag_Layout);
 *     if (signature != skcms_Signature_acsp ||
 *         profile->size > len ||
 *         profile->size < SAFE_SIZEOF(header_Layout) + tag_table_size ||
 *         (version >> 24) > 4) {
 *         return false;
 *     }
 *
 *     // Validate that illuminant is D50 white
 *     if (fabsf_(illuminant_X - 0.9642f) > 0.0100f ||
 *         fabsf_(illuminant_Y - 1.0000f) > 0.0100f ||
 *         fabsf_(illuminant_Z - 0.8249f) > 0.0100f) {
 *         return false;
 *     }
 *
 *     // Validate that all tag entries have sane offset + size
 *     const tag_Layout* tags = get_tag_table(profile);
 *     for (uint32_t i = 0; i < profile->tag_count; ++i) {
 *         uint32_t tag_offset = read_big_u32(tags[i].offset);
 *         uint32_t tag_size   = read_big_u32(tags[i].size);
 *         uint64_t tag_end    = (uint64_t)tag_offset + (uint64_t)tag_size;
 *         if (tag_size < 4 || tag_end > profile->size) {
 *             return false;
 *         }
 *     }
 *
 *     if (profile->pcs != skcms_Signature_XYZ && profile->pcs != skcms_Signature_Lab) {
 *         return false;
 *     }
 *
 *     bool pcs_is_xyz = profile->pcs == skcms_Signature_XYZ;
 *
 *     // Pre-parse commonly used tags.
 *     skcms_ICCTag kTRC;
 *     if (profile->data_color_space == skcms_Signature_Gray &&
 *         skcms_GetTagBySignature(profile, skcms_Signature_kTRC, &kTRC)) {
 *         if (!read_curve(kTRC.buf, kTRC.size, &profile->trc[0], nullptr)) {
 *             // Malformed tag
 *             return false;
 *         }
 *         profile->trc[1] = profile->trc[0];
 *         profile->trc[2] = profile->trc[0];
 *         profile->has_trc = true;
 *
 *         if (pcs_is_xyz) {
 *             profile->toXYZD50.vals[0][0] = illuminant_X;
 *             profile->toXYZD50.vals[1][1] = illuminant_Y;
 *             profile->toXYZD50.vals[2][2] = illuminant_Z;
 *             profile->has_toXYZD50 = true;
 *         }
 *     } else {
 *         skcms_ICCTag rTRC, gTRC, bTRC;
 *         if (skcms_GetTagBySignature(profile, skcms_Signature_rTRC, &rTRC) &&
 *             skcms_GetTagBySignature(profile, skcms_Signature_gTRC, &gTRC) &&
 *             skcms_GetTagBySignature(profile, skcms_Signature_bTRC, &bTRC)) {
 *             if (!read_curve(rTRC.buf, rTRC.size, &profile->trc[0], nullptr) ||
 *                 !read_curve(gTRC.buf, gTRC.size, &profile->trc[1], nullptr) ||
 *                 !read_curve(bTRC.buf, bTRC.size, &profile->trc[2], nullptr)) {
 *                 // Malformed TRC tags
 *                 return false;
 *             }
 *             profile->has_trc = true;
 *         }
 *
 *         skcms_ICCTag rXYZ, gXYZ, bXYZ;
 *         if (skcms_GetTagBySignature(profile, skcms_Signature_rXYZ, &rXYZ) &&
 *             skcms_GetTagBySignature(profile, skcms_Signature_gXYZ, &gXYZ) &&
 *             skcms_GetTagBySignature(profile, skcms_Signature_bXYZ, &bXYZ)) {
 *             if (!read_to_XYZD50(&rXYZ, &gXYZ, &bXYZ, &profile->toXYZD50)) {
 *                 // Malformed XYZ tags
 *                 return false;
 *             }
 *             profile->has_toXYZD50 = true;
 *         }
 *     }
 *
 *     for (int i = 0; i < priorities; i++) {
 *         // enum { perceptual, relative_colormetric, saturation }
 *         if (priority[i] < 0 || priority[i] > 2) {
 *             return false;
 *         }
 *         uint32_t sig = skcms_Signature_A2B0 + static_cast<uint32_t>(priority[i]);
 *         skcms_ICCTag tag;
 *         if (skcms_GetTagBySignature(profile, sig, &tag)) {
 *             if (!read_a2b(&tag, &profile->A2B, pcs_is_xyz)) {
 *                 // Malformed A2B tag
 *                 return false;
 *             }
 *             profile->has_A2B = true;
 *             break;
 *         }
 *     }
 *
 *     for (int i = 0; i < priorities; i++) {
 *         // enum { perceptual, relative_colormetric, saturation }
 *         if (priority[i] < 0 || priority[i] > 2) {
 *             return false;
 *         }
 *         uint32_t sig = skcms_Signature_B2A0 + static_cast<uint32_t>(priority[i]);
 *         skcms_ICCTag tag;
 *         if (skcms_GetTagBySignature(profile, sig, &tag)) {
 *             if (!read_b2a(&tag, &profile->B2A, pcs_is_xyz)) {
 *                 // Malformed B2A tag
 *                 return false;
 *             }
 *             profile->has_B2A = true;
 *             break;
 *         }
 *     }
 *
 *     skcms_ICCTag cicp_tag;
 *     if (skcms_GetTagBySignature(profile, skcms_Signature_CICP, &cicp_tag)) {
 *         if (!read_cicp(&cicp_tag, &profile->CICP)) {
 *             // Malformed CICP tag
 *             return false;
 *         }
 *         profile->has_CICP = true;
 *     }
 *
 *     return usable_as_src(profile);
 * }
 * ```
 */
public fun skcmsParseWithA2BPriority(
  buf: Unit?,
  len: ULong,
  priority: IntArray,
  priorities: Int,
  profile: SkcmsICCProfile?,
): Boolean {
  TODO("Implement skcmsParseWithA2BPriority")
}

/**
 * C++ original:
 * ```cpp
 * const skcms_ICCProfile* skcms_sRGB_profile() {
 *     static const skcms_ICCProfile sRGB_profile = {
 *         nullptr,               // buffer, moot here
 *
 *         0,                     // size, moot here
 *         skcms_Signature_RGB,   // data_color_space
 *         skcms_Signature_XYZ,   // pcs
 *         0,                     // tag count, moot here
 *
 *         // We choose to represent sRGB with its canonical transfer function,
 *         // and with its canonical XYZD50 gamut matrix.
 *         {   // the 3 trc curves
 *             {{0, {2.4f, (float)(1/1.055), (float)(0.055/1.055), (float)(1/12.92), 0.04045f, 0, 0}}},
 *             {{0, {2.4f, (float)(1/1.055), (float)(0.055/1.055), (float)(1/12.92), 0.04045f, 0, 0}}},
 *             {{0, {2.4f, (float)(1/1.055), (float)(0.055/1.055), (float)(1/12.92), 0.04045f, 0, 0}}},
 *         },
 *
 *         {{  // 3x3 toXYZD50 matrix
 *             { 0.436065674f, 0.385147095f, 0.143066406f },
 *             { 0.222488403f, 0.716873169f, 0.060607910f },
 *             { 0.013916016f, 0.097076416f, 0.714096069f },
 *         }},
 *
 *         {   // an empty A2B
 *             {   // input_curves
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *             },
 *             nullptr,   // grid_8
 *             nullptr,   // grid_16
 *             0,         // input_channels
 *             {0,0,0,0}, // grid_points
 *
 *             {   // matrix_curves
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *             },
 *             {{  // matrix (3x4)
 *                 { 0,0,0,0 },
 *                 { 0,0,0,0 },
 *                 { 0,0,0,0 },
 *             }},
 *             0,  // matrix_channels
 *
 *             0,  // output_channels
 *             {   // output_curves
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *             },
 *         },
 *
 *         {   // an empty B2A
 *             {   // input_curves
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *             },
 *             0,  // input_channels
 *
 *             0,  // matrix_channels
 *             {   // matrix_curves
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *             },
 *             {{  // matrix (3x4)
 *                 { 0,0,0,0 },
 *                 { 0,0,0,0 },
 *                 { 0,0,0,0 },
 *             }},
 *
 *             {   // output_curves
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *             },
 *             nullptr,    // grid_8
 *             nullptr,    // grid_16
 *             {0,0,0,0},  // grid_points
 *             0,          // output_channels
 *         },
 *
 *         { 0, 0, 0, 0 },  // an empty CICP
 *
 *         true,  // has_trc
 *         true,  // has_toXYZD50
 *         false, // has_A2B
 *         false, // has B2A
 *         false, // has_CICP
 *     };
 *     return &sRGB_profile;
 * }
 * ```
 */
public fun skcmsSRGBProfile(): SkcmsICCProfile {
  TODO("Implement skcmsSRGBProfile")
}

/**
 * C++ original:
 * ```cpp
 * const skcms_ICCProfile* skcms_XYZD50_profile() {
 *     // Just like sRGB above, but with identity transfer functions and toXYZD50 matrix.
 *     static const skcms_ICCProfile XYZD50_profile = {
 *         nullptr,               // buffer, moot here
 *
 *         0,                     // size, moot here
 *         skcms_Signature_RGB,   // data_color_space
 *         skcms_Signature_XYZ,   // pcs
 *         0,                     // tag count, moot here
 *
 *         {   // the 3 trc curves
 *             {{0, {1,1, 0,0,0,0,0}}},
 *             {{0, {1,1, 0,0,0,0,0}}},
 *             {{0, {1,1, 0,0,0,0,0}}},
 *         },
 *
 *         {{  // 3x3 toXYZD50 matrix
 *             { 1,0,0 },
 *             { 0,1,0 },
 *             { 0,0,1 },
 *         }},
 *
 *         {   // an empty A2B
 *             {   // input_curves
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *             },
 *             nullptr,   // grid_8
 *             nullptr,   // grid_16
 *             0,         // input_channels
 *             {0,0,0,0}, // grid_points
 *
 *             {   // matrix_curves
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *             },
 *             {{  // matrix (3x4)
 *                 { 0,0,0,0 },
 *                 { 0,0,0,0 },
 *                 { 0,0,0,0 },
 *             }},
 *             0,  // matrix_channels
 *
 *             0,  // output_channels
 *             {   // output_curves
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *             },
 *         },
 *
 *         {   // an empty B2A
 *             {   // input_curves
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *             },
 *             0,  // input_channels
 *
 *             0,  // matrix_channels
 *             {   // matrix_curves
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *             },
 *             {{  // matrix (3x4)
 *                 { 0,0,0,0 },
 *                 { 0,0,0,0 },
 *                 { 0,0,0,0 },
 *             }},
 *
 *             {   // output_curves
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *                 {{0, {0,0, 0,0,0,0,0}}},
 *             },
 *             nullptr,    // grid_8
 *             nullptr,    // grid_16
 *             {0,0,0,0},  // grid_points
 *             0,          // output_channels
 *         },
 *
 *         { 0, 0, 0, 0 },  // an empty CICP
 *
 *         true,  // has_trc
 *         true,  // has_toXYZD50
 *         false, // has_A2B
 *         false, // has B2A
 *         false, // has_CICP
 *     };
 *
 *     return &XYZD50_profile;
 * }
 * ```
 */
public fun skcmsXYZD50Profile(): SkcmsICCProfile {
  TODO("Implement skcmsXYZD50Profile")
}

/**
 * C++ original:
 * ```cpp
 * const skcms_TransferFunction* skcms_sRGB_TransferFunction() {
 *     return &skcms_sRGB_profile()->trc[0].parametric;
 * }
 * ```
 */
public fun skcmsSRGBTransferFunction(): SkcmsTransferFunction {
  TODO("Implement skcmsSRGBTransferFunction")
}

/**
 * C++ original:
 * ```cpp
 * const skcms_TransferFunction* skcms_sRGB_Inverse_TransferFunction() {
 *     static const skcms_TransferFunction sRGB_inv =
 *         {0.416666657f, 1.137283325f, -0.0f, 12.920000076f, 0.003130805f, -0.054969788f, -0.0f};
 *     return &sRGB_inv;
 * }
 * ```
 */
public fun skcmsSRGBInverseTransferFunction(): SkcmsTransferFunction {
  TODO("Implement skcmsSRGBInverseTransferFunction")
}

/**
 * C++ original:
 * ```cpp
 * const skcms_TransferFunction* skcms_Identity_TransferFunction() {
 *     static const skcms_TransferFunction identity = {1,1,0,0,0,0,0};
 *     return &identity;
 * }
 * ```
 */
public fun skcmsIdentityTransferFunction(): SkcmsTransferFunction {
  TODO("Implement skcmsIdentityTransferFunction")
}

/**
 * C++ original:
 * ```cpp
 * bool skcms_ApproximatelyEqualProfiles(const skcms_ICCProfile* A, const skcms_ICCProfile* B) {
 *     // Test for exactly equal profiles first.
 *     if (A == B || 0 == memcmp(A,B, sizeof(skcms_ICCProfile))) {
 *         return true;
 *     }
 *
 *     // For now this is the essentially the same strategy we use in test_only.c
 *     // for our skcms_Transform() smoke tests:
 *     //    1) transform A to XYZD50
 *     //    2) transform B to XYZD50
 *     //    3) return true if they're similar enough
 *     // Our current criterion in 3) is maximum 1 bit error per XYZD50 byte.
 *
 *     // skcms_252_random_bytes are 252 of a random shuffle of all possible bytes.
 *     // 252 is evenly divisible by 3 and 4.  Only 192, 10, 241, and 43 are missing.
 *
 *     // We want to allow otherwise equivalent profiles tagged as grayscale and RGB
 *     // to be treated as equal.  But CMYK profiles are a totally different ballgame.
 *     const auto CMYK = skcms_Signature_CMYK;
 *     if ((A->data_color_space == CMYK) != (B->data_color_space == CMYK)) {
 *         return false;
 *     }
 *
 *     // Interpret as RGB_888 if data color space is RGB or GRAY, RGBA_8888 if CMYK.
 *     // TODO: working with RGBA_8888 either way is probably fastest.
 *     skcms_PixelFormat fmt = skcms_PixelFormat_RGB_888;
 *     size_t npixels = 84;
 *     if (A->data_color_space == skcms_Signature_CMYK) {
 *         fmt = skcms_PixelFormat_RGBA_8888;
 *         npixels = 63;
 *     }
 *
 *     // TODO: if A or B is a known profile (skcms_sRGB_profile, skcms_XYZD50_profile),
 *     // use pre-canned results and skip that skcms_Transform() call?
 *     uint8_t dstA[252],
 *             dstB[252];
 *     if (!skcms_Transform(
 *                 skcms_252_random_bytes,     fmt, skcms_AlphaFormat_Unpremul, A,
 *                 dstA, skcms_PixelFormat_RGB_888, skcms_AlphaFormat_Unpremul, skcms_XYZD50_profile(),
 *                 npixels)) {
 *         return false;
 *     }
 *     if (!skcms_Transform(
 *                 skcms_252_random_bytes,     fmt, skcms_AlphaFormat_Unpremul, B,
 *                 dstB, skcms_PixelFormat_RGB_888, skcms_AlphaFormat_Unpremul, skcms_XYZD50_profile(),
 *                 npixels)) {
 *         return false;
 *     }
 *
 *     // TODO: make sure this final check has reasonable codegen.
 *     for (size_t i = 0; i < 252; i++) {
 *         if (abs((int)dstA[i] - (int)dstB[i]) > 1) {
 *             return false;
 *         }
 *     }
 *     return true;
 * }
 * ```
 */
public fun skcmsApproximatelyEqualProfiles(a: SkcmsICCProfile?, b: SkcmsICCProfile?): Boolean {
  TODO("Implement skcmsApproximatelyEqualProfiles")
}

/**
 * C++ original:
 * ```cpp
 * bool skcms_TRCs_AreApproximateInverse(const skcms_ICCProfile* profile,
 *                                       const skcms_TransferFunction* inv_tf) {
 *     if (!profile || !profile->has_trc) {
 *         return false;
 *     }
 *
 *     return skcms_AreApproximateInverses(&profile->trc[0], inv_tf) &&
 *            skcms_AreApproximateInverses(&profile->trc[1], inv_tf) &&
 *            skcms_AreApproximateInverses(&profile->trc[2], inv_tf);
 * }
 * ```
 */
public fun skcmsTRCsAreApproximateInverse(profile: SkcmsICCProfile?, invTf: SkcmsTransferFunction?): Boolean {
  TODO("Implement skcmsTRCsAreApproximateInverse")
}

/**
 * C++ original:
 * ```cpp
 * static bool is_zero_to_one(float x) {
 *     return 0 <= x && x <= 1;
 * }
 * ```
 */
public fun isZeroToOne(x: Float): Boolean {
  TODO("Implement isZeroToOne")
}

/**
 * C++ original:
 * ```cpp
 * static skcms_Vector3 mv_mul(const skcms_Matrix3x3* m, const skcms_Vector3* v) {
 *     skcms_Vector3 dst = {{0,0,0}};
 *     for (int row = 0; row < 3; ++row) {
 *         dst.vals[row] = m->vals[row][0] * v->vals[0]
 *                       + m->vals[row][1] * v->vals[1]
 *                       + m->vals[row][2] * v->vals[2];
 *     }
 *     return dst;
 * }
 * ```
 */
public fun mvMul(m: SkcmsMatrix3x3?, v: SkcmsVector3?): SkcmsVector3 {
  TODO("Implement mvMul")
}

/**
 * C++ original:
 * ```cpp
 * bool skcms_AdaptToXYZD50(float wx, float wy,
 *                          skcms_Matrix3x3* toXYZD50) {
 *     if (!is_zero_to_one(wx) || !is_zero_to_one(wy) ||
 *         !toXYZD50) {
 *         return false;
 *     }
 *
 *     // Assumes that Y is 1.0f.
 *     skcms_Vector3 wXYZ = { { wx / wy, 1, (1 - wx - wy) / wy } };
 *
 *     // Now convert toXYZ matrix to toXYZD50.
 *     skcms_Vector3 wXYZD50 = { { 0.96422f, 1.0f, 0.82521f } };
 *
 *     // Calculate the chromatic adaptation matrix.  We will use the Bradford method, thus
 *     // the matrices below.  The Bradford method is used by Adobe and is widely considered
 *     // to be the best.
 *     skcms_Matrix3x3 xyz_to_lms = {{
 *         {  0.8951f,  0.2664f, -0.1614f },
 *         { -0.7502f,  1.7135f,  0.0367f },
 *         {  0.0389f, -0.0685f,  1.0296f },
 *     }};
 *     skcms_Matrix3x3 lms_to_xyz = {{
 *         {  0.9869929f, -0.1470543f, 0.1599627f },
 *         {  0.4323053f,  0.5183603f, 0.0492912f },
 *         { -0.0085287f,  0.0400428f, 0.9684867f },
 *     }};
 *
 *     skcms_Vector3 srcCone = mv_mul(&xyz_to_lms, &wXYZ);
 *     skcms_Vector3 dstCone = mv_mul(&xyz_to_lms, &wXYZD50);
 *
 *     *toXYZD50 = {{
 *         { dstCone.vals[0] / srcCone.vals[0], 0, 0 },
 *         { 0, dstCone.vals[1] / srcCone.vals[1], 0 },
 *         { 0, 0, dstCone.vals[2] / srcCone.vals[2] },
 *     }};
 *     *toXYZD50 = skcms_Matrix3x3_concat(toXYZD50, &xyz_to_lms);
 *     *toXYZD50 = skcms_Matrix3x3_concat(&lms_to_xyz, toXYZD50);
 *
 *     return true;
 * }
 * ```
 */
public fun skcmsAdaptToXYZD50(
  wx: Float,
  wy: Float,
  toXYZD50: SkcmsMatrix3x3?,
): Boolean {
  TODO("Implement skcmsAdaptToXYZD50")
}

/**
 * C++ original:
 * ```cpp
 * bool skcms_PrimariesToXYZD50(float rx, float ry,
 *                              float gx, float gy,
 *                              float bx, float by,
 *                              float wx, float wy,
 *                              skcms_Matrix3x3* toXYZD50) {
 *     if (!is_zero_to_one(rx) || !is_zero_to_one(ry) ||
 *         !is_zero_to_one(gx) || !is_zero_to_one(gy) ||
 *         !is_zero_to_one(bx) || !is_zero_to_one(by) ||
 *         !is_zero_to_one(wx) || !is_zero_to_one(wy) ||
 *         !toXYZD50) {
 *         return false;
 *     }
 *
 *     // First, we need to convert xy values (primaries) to XYZ.
 *     skcms_Matrix3x3 primaries = {{
 *         { rx, gx, bx },
 *         { ry, gy, by },
 *         { 1 - rx - ry, 1 - gx - gy, 1 - bx - by },
 *     }};
 *     skcms_Matrix3x3 primaries_inv;
 *     if (!skcms_Matrix3x3_invert(&primaries, &primaries_inv)) {
 *         return false;
 *     }
 *
 *     // Assumes that Y is 1.0f.
 *     skcms_Vector3 wXYZ = { { wx / wy, 1, (1 - wx - wy) / wy } };
 *     skcms_Vector3 XYZ = mv_mul(&primaries_inv, &wXYZ);
 *
 *     skcms_Matrix3x3 toXYZ = {{
 *         { XYZ.vals[0],           0,           0 },
 *         {           0, XYZ.vals[1],           0 },
 *         {           0,           0, XYZ.vals[2] },
 *     }};
 *     toXYZ = skcms_Matrix3x3_concat(&primaries, &toXYZ);
 *
 *     skcms_Matrix3x3 DXtoD50;
 *     if (!skcms_AdaptToXYZD50(wx, wy, &DXtoD50)) {
 *         return false;
 *     }
 *
 *     *toXYZD50 = skcms_Matrix3x3_concat(&DXtoD50, &toXYZ);
 *     return true;
 * }
 * ```
 */
public fun skcmsPrimariesToXYZD50(
  rx: Float,
  ry: Float,
  gx: Float,
  gy: Float,
  bx: Float,
  `by`: Float,
  wx: Float,
  wy: Float,
  toXYZD50: SkcmsMatrix3x3?,
): Boolean {
  TODO("Implement skcmsPrimariesToXYZD50")
}

/**
 * C++ original:
 * ```cpp
 * bool skcms_Matrix3x3_invert(const skcms_Matrix3x3* src, skcms_Matrix3x3* dst) {
 *     double a00 = src->vals[0][0],
 *            a01 = src->vals[1][0],
 *            a02 = src->vals[2][0],
 *            a10 = src->vals[0][1],
 *            a11 = src->vals[1][1],
 *            a12 = src->vals[2][1],
 *            a20 = src->vals[0][2],
 *            a21 = src->vals[1][2],
 *            a22 = src->vals[2][2];
 *
 *     double b0 = a00*a11 - a01*a10,
 *            b1 = a00*a12 - a02*a10,
 *            b2 = a01*a12 - a02*a11,
 *            b3 = a20,
 *            b4 = a21,
 *            b5 = a22;
 *
 *     double determinant = b0*b5
 *                        - b1*b4
 *                        + b2*b3;
 *
 *     if (determinant == 0) {
 *         return false;
 *     }
 *
 *     double invdet = 1.0 / determinant;
 *     if (invdet > +FLT_MAX || invdet < -FLT_MAX || !isfinitef_((float)invdet)) {
 *         return false;
 *     }
 *
 *     b0 *= invdet;
 *     b1 *= invdet;
 *     b2 *= invdet;
 *     b3 *= invdet;
 *     b4 *= invdet;
 *     b5 *= invdet;
 *
 *     dst->vals[0][0] = (float)( a11*b5 - a12*b4 );
 *     dst->vals[1][0] = (float)( a02*b4 - a01*b5 );
 *     dst->vals[2][0] = (float)(        +     b2 );
 *     dst->vals[0][1] = (float)( a12*b3 - a10*b5 );
 *     dst->vals[1][1] = (float)( a00*b5 - a02*b3 );
 *     dst->vals[2][1] = (float)(        -     b1 );
 *     dst->vals[0][2] = (float)( a10*b4 - a11*b3 );
 *     dst->vals[1][2] = (float)( a01*b3 - a00*b4 );
 *     dst->vals[2][2] = (float)(        +     b0 );
 *
 *     for (int r = 0; r < 3; ++r)
 *     for (int c = 0; c < 3; ++c) {
 *         if (!isfinitef_(dst->vals[r][c])) {
 *             return false;
 *         }
 *     }
 *     return true;
 * }
 * ```
 */
public fun skcmsMatrix3x3Invert(src: SkcmsMatrix3x3?, dst: SkcmsMatrix3x3?): Boolean {
  TODO("Implement skcmsMatrix3x3Invert")
}

/**
 * C++ original:
 * ```cpp
 * skcms_Matrix3x3 skcms_Matrix3x3_concat(const skcms_Matrix3x3* A, const skcms_Matrix3x3* B) {
 *     skcms_Matrix3x3 m = { { { 0,0,0 },{ 0,0,0 },{ 0,0,0 } } };
 *     for (int r = 0; r < 3; r++)
 *         for (int c = 0; c < 3; c++) {
 *             m.vals[r][c] = A->vals[r][0] * B->vals[0][c]
 *                          + A->vals[r][1] * B->vals[1][c]
 *                          + A->vals[r][2] * B->vals[2][c];
 *         }
 *     return m;
 * }
 * ```
 */
public fun skcmsMatrix3x3Concat(a: SkcmsMatrix3x3?, b: SkcmsMatrix3x3?): SkcmsMatrix3x3 {
  TODO("Implement skcmsMatrix3x3Concat")
}

/**
 * C++ original:
 * ```cpp
 * bool skcms_TransferFunction_invert(const skcms_TransferFunction* src, skcms_TransferFunction* dst) {
 *     TF_PQish  pq;
 *     TF_HLGish hlg;
 *     switch (classify(*src, &pq, &hlg)) {
 *         case skcms_TFType_Invalid: return false;
 *         case skcms_TFType_PQ:      return false;
 *         case skcms_TFType_HLG:     return false;
 *         case skcms_TFType_sRGBish: break;  // handled below
 *
 *         case skcms_TFType_PQish:
 *             *dst = { TFKind_marker(skcms_TFType_PQish), -pq.A,  pq.D, 1.0f/pq.F
 *                                                       ,  pq.B, -pq.E, 1.0f/pq.C};
 *             return true;
 *
 *         case skcms_TFType_HLGish:
 *             *dst = { TFKind_marker(skcms_TFType_HLGinvish), 1.0f/hlg.R, 1.0f/hlg.G
 *                                                           , 1.0f/hlg.a, hlg.b, hlg.c
 *                                                           , hlg.K_minus_1 };
 *             return true;
 *
 *         case skcms_TFType_HLGinvish:
 *             *dst = { TFKind_marker(skcms_TFType_HLGish), 1.0f/hlg.R, 1.0f/hlg.G
 *                                                        , 1.0f/hlg.a, hlg.b, hlg.c
 *                                                        , hlg.K_minus_1 };
 *             return true;
 *     }
 *
 *     assert (classify(*src) == skcms_TFType_sRGBish);
 *
 *     // We're inverting this function, solving for x in terms of y.
 *     //   y = (cx + f)         x < d
 *     //       (ax + b)^g + e   x ≥ d
 *     // The inverse of this function can be expressed in the same piecewise form.
 *     skcms_TransferFunction inv = {0,0,0,0,0,0,0};
 *
 *     // We'll start by finding the new threshold inv.d.
 *     // In principle we should be able to find that by solving for y at x=d from either side.
 *     // (If those two d values aren't the same, it's a discontinuous transfer function.)
 *     float d_l =       src->c * src->d + src->f,
 *           d_r = powf_(src->a * src->d + src->b, src->g) + src->e;
 *     if (fabsf_(d_l - d_r) > 1/512.0f) {
 *         return false;
 *     }
 *     inv.d = d_l;  // TODO(mtklein): better in practice to choose d_r?
 *
 *     // When d=0, the linear section collapses to a point.  We leave c,d,f all zero in that case.
 *     if (inv.d > 0) {
 *         // Inverting the linear section is pretty straightfoward:
 *         //        y       = cx + f
 *         //        y - f   = cx
 *         //   (1/c)y - f/c = x
 *         inv.c =    1.0f/src->c;
 *         inv.f = -src->f/src->c;
 *     }
 *
 *     // The interesting part is inverting the nonlinear section:
 *     //         y                = (ax + b)^g + e.
 *     //         y - e            = (ax + b)^g
 *     //        (y - e)^1/g       =  ax + b
 *     //        (y - e)^1/g - b   =  ax
 *     //   (1/a)(y - e)^1/g - b/a =   x
 *     //
 *     // To make that fit our form, we need to move the (1/a) term inside the exponentiation:
 *     //   let k = (1/a)^g
 *     //   (1/a)( y -  e)^1/g - b/a = x
 *     //        (ky - ke)^1/g - b/a = x
 *
 *     float k = powf_(src->a, -src->g);  // (1/a)^g == a^-g
 *     inv.g = 1.0f / src->g;
 *     inv.a = k;
 *     inv.b = -k * src->e;
 *     inv.e = -src->b / src->a;
 *
 *     // We need to enforce the same constraints here that we do when fitting a curve,
 *     // a >= 0 and ad+b >= 0.  These constraints are checked by classify(), so they're true
 *     // of the source function if we're here.
 *
 *     // Just like when fitting the curve, there's really no way to rescue a < 0.
 *     if (inv.a < 0) {
 *         return false;
 *     }
 *     // On the other hand we can rescue an ad+b that's gone slightly negative here.
 *     if (inv.a * inv.d + inv.b < 0) {
 *         inv.b = -inv.a * inv.d;
 *     }
 *
 *     // That should usually make classify(inv) == sRGBish true, but there are a couple situations
 *     // where we might still fail here, like non-finite parameter values.
 *     if (classify(inv) != skcms_TFType_sRGBish) {
 *         return false;
 *     }
 *
 *     assert (inv.a >= 0);
 *     assert (inv.a * inv.d + inv.b >= 0);
 *
 *     // Now in principle we're done.
 *     // But to preserve the valuable invariant inv(src(1.0f)) == 1.0f, we'll tweak
 *     // e or f of the inverse, depending on which segment contains src(1.0f).
 *     float s = skcms_TransferFunction_eval(src, 1.0f);
 *     if (!isfinitef_(s)) {
 *         return false;
 *     }
 *
 *     float sign = s < 0 ? -1.0f : 1.0f;
 *     s *= sign;
 *     if (s < inv.d) {
 *         inv.f = 1.0f - sign * inv.c * s;
 *     } else {
 *         inv.e = 1.0f - sign * powf_(inv.a * s + inv.b, inv.g);
 *     }
 *
 *     *dst = inv;
 *     return classify(*dst) == skcms_TFType_sRGBish;
 * }
 * ```
 */
public fun skcmsTransferFunctionInvert(src: SkcmsTransferFunction?, dst: SkcmsTransferFunction?): Boolean {
  TODO("Implement skcmsTransferFunctionInvert")
}

/**
 * C++ original:
 * ```cpp
 * static float rg_nonlinear(float x,
 *                           const skcms_Curve* curve,
 *                           const skcms_TransferFunction* tf,
 *                           float dfdP[3]) {
 *     const float y = eval_curve(curve, x);
 *
 *     const float g = tf->g, a = tf->a, b = tf->b,
 *                 c = tf->c, d = tf->d, f = tf->f;
 *
 *     const float Y = fmaxf_(a*y + b, 0.0f),
 *                 D =        a*d + b;
 *     assert (D >= 0);
 *
 *     // The gradient.
 *     dfdP[0] = logf_(Y)*powf_(Y, g)
 *             - logf_(D)*powf_(D, g);
 *     dfdP[1] = y*g*powf_(Y, g-1)
 *             - d*g*powf_(D, g-1);
 *     dfdP[2] =   g*powf_(Y, g-1)
 *             -   g*powf_(D, g-1);
 *
 *     // The residual.
 *     const float f_inv = powf_(Y, g)
 *                       - powf_(D, g)
 *                       + c*d + f;
 *     return x - f_inv;
 * }
 * ```
 */
public fun rgNonlinear(
  x: Float,
  curve: SkcmsCurve?,
  tf: SkcmsTransferFunction?,
  dfdP: FloatArray,
): Float {
  TODO("Implement rgNonlinear")
}

/**
 * C++ original:
 * ```cpp
 * static bool gauss_newton_step(const skcms_Curve* curve,
 *                                     skcms_TransferFunction* tf,
 *                               float x0, float dx, int N) {
 *     // We'll sample x from the range [x0,x1] (both inclusive) N times with even spacing.
 *     //
 *     // Let P = [ tf->g, tf->a, tf->b ] (the three terms that we're adjusting).
 *     //
 *     // We want to do P' = P + (Jf^T Jf)^-1 Jf^T r(P),
 *     //   where r(P) is the residual vector
 *     //   and Jf is the Jacobian matrix of f(), ∂r/∂P.
 *     //
 *     // Let's review the shape of each of these expressions:
 *     //   r(P)   is [N x 1], a column vector with one entry per value of x tested
 *     //   Jf     is [N x 3], a matrix with an entry for each (x,P) pair
 *     //   Jf^T   is [3 x N], the transpose of Jf
 *     //
 *     //   Jf^T Jf   is [3 x N] * [N x 3] == [3 x 3], a 3x3 matrix,
 *     //                                              and so is its inverse (Jf^T Jf)^-1
 *     //   Jf^T r(P) is [3 x N] * [N x 1] == [3 x 1], a column vector with the same shape as P
 *     //
 *     // Our implementation strategy to get to the final ∆P is
 *     //   1) evaluate Jf^T Jf,   call that lhs
 *     //   2) evaluate Jf^T r(P), call that rhs
 *     //   3) invert lhs
 *     //   4) multiply inverse lhs by rhs
 *     //
 *     // This is a friendly implementation strategy because we don't have to have any
 *     // buffers that scale with N, and equally nice don't have to perform any matrix
 *     // operations that are variable size.
 *     //
 *     // Other implementation strategies could trade this off, e.g. evaluating the
 *     // pseudoinverse of Jf ( (Jf^T Jf)^-1 Jf^T ) directly, then multiplying that by
 *     // the residuals.  That would probably require implementing singular value
 *     // decomposition, and would create a [3 x N] matrix to be multiplied by the
 *     // [N x 1] residual vector, but on the upside I think that'd eliminate the
 *     // possibility of this gauss_newton_step() function ever failing.
 *
 *     // 0) start off with lhs and rhs safely zeroed.
 *     skcms_Matrix3x3 lhs = {{ {0,0,0}, {0,0,0}, {0,0,0} }};
 *     skcms_Vector3   rhs = {  {0,0,0} };
 *
 *     // 1,2) evaluate lhs and evaluate rhs
 *     //   We want to evaluate Jf only once, but both lhs and rhs involve Jf^T,
 *     //   so we'll have to update lhs and rhs at the same time.
 *     for (int i = 0; i < N; i++) {
 *         float x = x0 + static_cast<float>(i)*dx;
 *
 *         float dfdP[3] = {0,0,0};
 *         float resid = rg_nonlinear(x,curve,tf, dfdP);
 *
 *         for (int r = 0; r < 3; r++) {
 *             for (int c = 0; c < 3; c++) {
 *                 lhs.vals[r][c] += dfdP[r] * dfdP[c];
 *             }
 *             rhs.vals[r] += dfdP[r] * resid;
 *         }
 *     }
 *
 *     // If any of the 3 P parameters are unused, this matrix will be singular.
 *     // Detect those cases and fix them up to indentity instead, so we can invert.
 *     for (int k = 0; k < 3; k++) {
 *         if (lhs.vals[0][k]==0 && lhs.vals[1][k]==0 && lhs.vals[2][k]==0 &&
 *             lhs.vals[k][0]==0 && lhs.vals[k][1]==0 && lhs.vals[k][2]==0) {
 *             lhs.vals[k][k] = 1;
 *         }
 *     }
 *
 *     // 3) invert lhs
 *     skcms_Matrix3x3 lhs_inv;
 *     if (!skcms_Matrix3x3_invert(&lhs, &lhs_inv)) {
 *         return false;
 *     }
 *
 *     // 4) multiply inverse lhs by rhs
 *     skcms_Vector3 dP = mv_mul(&lhs_inv, &rhs);
 *     tf->g += dP.vals[0];
 *     tf->a += dP.vals[1];
 *     tf->b += dP.vals[2];
 *     return isfinitef_(tf->g) && isfinitef_(tf->a) && isfinitef_(tf->b);
 * }
 * ```
 */
public fun gaussNewtonStep(
  curve: SkcmsCurve?,
  tf: SkcmsTransferFunction?,
  x0: Float,
  dx: Float,
  n: Int,
): Boolean {
  TODO("Implement gaussNewtonStep")
}

/**
 * C++ original:
 * ```cpp
 * static float max_roundtrip_error_checked(const skcms_Curve* curve,
 *                                          const skcms_TransferFunction* tf_inv) {
 *     skcms_TransferFunction tf;
 *     if (!skcms_TransferFunction_invert(tf_inv, &tf) || skcms_TFType_sRGBish != classify(tf)) {
 *         return INFINITY_;
 *     }
 *
 *     skcms_TransferFunction tf_inv_again;
 *     if (!skcms_TransferFunction_invert(&tf, &tf_inv_again)) {
 *         return INFINITY_;
 *     }
 *
 *     return skcms_MaxRoundtripError(curve, &tf_inv_again);
 * }
 * ```
 */
public fun maxRoundtripErrorChecked(curve: SkcmsCurve?, tfInv: SkcmsTransferFunction?): Float {
  TODO("Implement maxRoundtripErrorChecked")
}

/**
 * C++ original:
 * ```cpp
 * static bool fit_nonlinear(const skcms_Curve* curve, int L, int N, skcms_TransferFunction* tf) {
 *     // This enforces a few constraints that are not modeled in gauss_newton_step()'s optimization.
 *     auto fixup_tf = [tf]() {
 *         // a must be non-negative. That ensures the function is monotonically increasing.
 *         // We don't really know how to fix up a if it goes negative.
 *         if (tf->a < 0) {
 *             return false;
 *         }
 *         // ad+b must be non-negative. That ensures we don't end up with complex numbers in powf.
 *         // We feel just barely not uneasy enough to tweak b so ad+b is zero in this case.
 *         if (tf->a * tf->d + tf->b < 0) {
 *             tf->b = -tf->a * tf->d;
 *         }
 *         assert (tf->a >= 0 &&
 *                 tf->a * tf->d + tf->b >= 0);
 *
 *         // cd+f must be ~= (ad+b)^g+e. That ensures the function is continuous. We keep e as a free
 *         // parameter so we can guarantee this.
 *         tf->e =   tf->c*tf->d + tf->f
 *           - powf_(tf->a*tf->d + tf->b, tf->g);
 *
 *         return isfinitef_(tf->e);
 *     };
 *
 *     if (!fixup_tf()) {
 *         return false;
 *     }
 *
 *     // No matter where we start, dx should always represent N even steps from 0 to 1.
 *     const float dx = 1.0f / static_cast<float>(N-1);
 *
 *     skcms_TransferFunction best_tf = *tf;
 *     float best_max_error = INFINITY_;
 *
 *     // Need this or several curves get worse... *sigh*
 *     float init_error = max_roundtrip_error_checked(curve, tf);
 *     if (init_error < best_max_error) {
 *         best_max_error = init_error;
 *         best_tf = *tf;
 *     }
 *
 *     // As far as we can tell, 1 Gauss-Newton step won't converge, and 3 steps is no better than 2.
 *     for (int j = 0; j < 8; j++) {
 *         if (!gauss_newton_step(curve, tf, static_cast<float>(L)*dx, dx, N-L) || !fixup_tf()) {
 *             *tf = best_tf;
 *             return isfinitef_(best_max_error);
 *         }
 *
 *         float max_error = max_roundtrip_error_checked(curve, tf);
 *         if (max_error < best_max_error) {
 *             best_max_error = max_error;
 *             best_tf = *tf;
 *         }
 *     }
 *
 *     *tf = best_tf;
 *     return isfinitef_(best_max_error);
 * }
 * ```
 */
public fun fitNonlinear(
  curve: SkcmsCurve?,
  l: Int,
  n: Int,
  tf: SkcmsTransferFunction?,
): Boolean {
  TODO("Implement fitNonlinear")
}

/**
 * C++ original:
 * ```cpp
 * bool skcms_ApproximateCurve(const skcms_Curve* curve,
 *                             skcms_TransferFunction* approx,
 *                             float* max_error) {
 *     if (!curve || !approx || !max_error) {
 *         return false;
 *     }
 *
 *     if (curve->table_entries == 0) {
 *         // No point approximating an skcms_TransferFunction with an skcms_TransferFunction!
 *         return false;
 *     }
 *
 *     if (curve->table_entries == 1 || curve->table_entries > (uint32_t)INT_MAX) {
 *         // We need at least two points, and must put some reasonable cap on the maximum number.
 *         return false;
 *     }
 *
 *     int N = (int)curve->table_entries;
 *     const float dx = 1.0f / static_cast<float>(N - 1);
 *
 *     *max_error = INFINITY_;
 *     const float kTolerances[] = { 1.5f / 65535.0f, 1.0f / 512.0f };
 *     for (int t = 0; t < ARRAY_COUNT(kTolerances); t++) {
 *         skcms_TransferFunction tf,
 *                                tf_inv;
 *
 *         // It's problematic to fit curves with non-zero f, so always force it to zero explicitly.
 *         tf.f = 0.0f;
 *         int L = fit_linear(curve, N, kTolerances[t], &tf.c, &tf.d);
 *
 *         if (L == N) {
 *             // If the entire data set was linear, move the coefficients to the nonlinear portion
 *             // with G == 1.  This lets use a canonical representation with d == 0.
 *             tf.g = 1;
 *             tf.a = tf.c;
 *             tf.b = tf.f;
 *             tf.c = tf.d = tf.e = tf.f = 0;
 *         } else if (L == N - 1) {
 *             // Degenerate case with only two points in the nonlinear segment. Solve directly.
 *             tf.g = 1;
 *             tf.a = (eval_curve(curve, static_cast<float>(N-1)*dx) -
 *                     eval_curve(curve, static_cast<float>(N-2)*dx))
 *                  / dx;
 *             tf.b = eval_curve(curve, static_cast<float>(N-2)*dx)
 *                  - tf.a * static_cast<float>(N-2)*dx;
 *             tf.e = 0;
 *         } else {
 *             // Start by guessing a gamma-only curve through the midpoint.
 *             int mid = (L + N) / 2;
 *             float mid_x = static_cast<float>(mid) / static_cast<float>(N - 1);
 *             float mid_y = eval_curve(curve, mid_x);
 *             tf.g = log2f_(mid_y) / log2f_(mid_x);
 *             tf.a = 1;
 *             tf.b = 0;
 *             tf.e =    tf.c*tf.d + tf.f
 *               - powf_(tf.a*tf.d + tf.b, tf.g);
 *
 *
 *             if (!skcms_TransferFunction_invert(&tf, &tf_inv) ||
 *                 !fit_nonlinear(curve, L,N, &tf_inv)) {
 *                 continue;
 *             }
 *
 *             // We fit tf_inv, so calculate tf to keep in sync.
 *             // fit_nonlinear() should guarantee invertibility.
 *             if (!skcms_TransferFunction_invert(&tf_inv, &tf)) {
 *                 assert(false);
 *                 continue;
 *             }
 *         }
 *
 *         // We'd better have a sane, sRGB-ish TF by now.
 *         // Other non-Bad TFs would be fine, but we know we've only ever tried to fit sRGBish;
 *         // anything else is just some accident of math and the way we pun tf.g as a type flag.
 *         // fit_nonlinear() should guarantee this, but the special cases may fail this test.
 *         if (skcms_TFType_sRGBish != classify(tf)) {
 *             continue;
 *         }
 *
 *         // We find our error by roundtripping the table through tf_inv.
 *         //
 *         // (The most likely use case for this approximation is to be inverted and
 *         // used as the transfer function for a destination color space.)
 *         //
 *         // We've kept tf and tf_inv in sync above, but we can't guarantee that tf is
 *         // invertible, so re-verify that here (and use the new inverse for testing).
 *         // fit_nonlinear() should guarantee this, but the special cases that don't use
 *         // it may fail this test.
 *         if (!skcms_TransferFunction_invert(&tf, &tf_inv)) {
 *             continue;
 *         }
 *
 *         float err = skcms_MaxRoundtripError(curve, &tf_inv);
 *         if (*max_error > err) {
 *             *max_error = err;
 *             *approx    = tf;
 *         }
 *     }
 *     return isfinitef_(*max_error);
 * }
 * ```
 */
public fun skcmsApproximateCurve(
  curve: SkcmsCurve?,
  approx: SkcmsTransferFunction?,
  maxError: Float?,
): Boolean {
  TODO("Implement skcmsApproximateCurve")
}

/**
 * C++ original:
 * ```cpp
 * static CpuType cpu_type() {
 *     #if defined(SKCMS_PORTABLE) || !defined(__x86_64__) || defined(SKCMS_FORCE_BASELINE)
 *         return CpuType::Baseline;
 *     #elif defined(SKCMS_FORCE_HSW)
 *         return CpuType::HSW;
 *     #elif defined(SKCMS_FORCE_SKX)
 *         return CpuType::SKX;
 *     #else
 *         static const CpuType type = []{
 *             if (!sAllowRuntimeCPUDetection) {
 *                 return CpuType::Baseline;
 *             }
 *             // See http://www.sandpile.org/x86/cpuid.htm
 *
 *             // First, a basic cpuid(1) lets us check prerequisites for HSW, SKX.
 *             uint32_t eax, ebx, ecx, edx;
 *             __asm__ __volatile__("cpuid" : "=a"(eax), "=b"(ebx), "=c"(ecx), "=d"(edx)
 *                                          : "0"(1), "2"(0));
 *             if ((edx & (1u<<25)) &&  // SSE
 *                 (edx & (1u<<26)) &&  // SSE2
 *                 (ecx & (1u<< 0)) &&  // SSE3
 *                 (ecx & (1u<< 9)) &&  // SSSE3
 *                 (ecx & (1u<<12)) &&  // FMA (N.B. not used, avoided even)
 *                 (ecx & (1u<<19)) &&  // SSE4.1
 *                 (ecx & (1u<<20)) &&  // SSE4.2
 *                 (ecx & (1u<<26)) &&  // XSAVE
 *                 (ecx & (1u<<27)) &&  // OSXSAVE
 *                 (ecx & (1u<<28)) &&  // AVX
 *                 (ecx & (1u<<29))) {  // F16C
 *
 *                 // Call cpuid(7) to check for AVX2 and AVX-512 bits.
 *                 __asm__ __volatile__("cpuid" : "=a"(eax), "=b"(ebx), "=c"(ecx), "=d"(edx)
 *                                              : "0"(7), "2"(0));
 *                 // eax from xgetbv(0) will tell us whether XMM, YMM, and ZMM state is saved.
 *                 uint32_t xcr0, dont_need_edx;
 *                 __asm__ __volatile__("xgetbv" : "=a"(xcr0), "=d"(dont_need_edx) : "c"(0));
 *
 *                 if ((xcr0 & (1u<<1)) &&  // XMM register state saved?
 *                     (xcr0 & (1u<<2)) &&  // YMM register state saved?
 *                     (ebx  & (1u<<5))) {  // AVX2
 *                     // At this point we're at least HSW.  Continue checking for SKX.
 *                     if ((xcr0 & (1u<< 5)) && // Opmasks state saved?
 *                         (xcr0 & (1u<< 6)) && // First 16 ZMM registers saved?
 *                         (xcr0 & (1u<< 7)) && // High 16 ZMM registers saved?
 *                         (ebx  & (1u<<16)) && // AVX512F
 *                         (ebx  & (1u<<17)) && // AVX512DQ
 *                         (ebx  & (1u<<28)) && // AVX512CD
 *                         (ebx  & (1u<<30)) && // AVX512BW
 *                         (ebx  & (1u<<31))) { // AVX512VL
 *                         return CpuType::SKX;
 *                     }
 *                     return CpuType::HSW;
 *                 }
 *             }
 *             return CpuType::Baseline;
 *         }();
 *         return type;
 *     #endif
 * }
 * ```
 */
public fun cpuType(): CpuType {
  TODO("Implement cpuType")
}

/**
 * C++ original:
 * ```cpp
 * static bool tf_is_gamma(const skcms_TransferFunction& tf) {
 *     return tf.g > 0 && tf.a == 1 &&
 *            tf.b == 0 && tf.c == 0 && tf.d == 0 && tf.e == 0 && tf.f == 0;
 * }
 * ```
 */
public fun tfIsGamma(tf: SkcmsTransferFunction): Boolean {
  TODO("Implement tfIsGamma")
}

/**
 * C++ original:
 * ```cpp
 * static OpAndArg select_curve_op(const skcms_Curve* curve, int channel) {
 *     struct OpType {
 *         Op sGamma, sRGBish, PQish, HLGish, HLGinvish, table;
 *     };
 *     static constexpr OpType kOps[] = {
 *         { Op::gamma_r, Op::tf_r, Op::pq_r, Op::hlg_r, Op::hlginv_r, Op::table_r },
 *         { Op::gamma_g, Op::tf_g, Op::pq_g, Op::hlg_g, Op::hlginv_g, Op::table_g },
 *         { Op::gamma_b, Op::tf_b, Op::pq_b, Op::hlg_b, Op::hlginv_b, Op::table_b },
 *         { Op::gamma_a, Op::tf_a, Op::pq_a, Op::hlg_a, Op::hlginv_a, Op::table_a },
 *     };
 *     const auto& op = kOps[channel];
 *
 *     if (curve->table_entries == 0) {
 *         const OpAndArg noop = { Op::load_a8/*doesn't matter*/, nullptr };
 *
 *         const skcms_TransferFunction& tf = curve->parametric;
 *
 *         if (tf_is_gamma(tf)) {
 *             return tf.g != 1 ? OpAndArg{op.sGamma, &tf}
 *                              : noop;
 *         }
 *
 *         switch (classify(tf)) {
 *             case skcms_TFType_Invalid:    return noop;
 *             // TODO(https://issues.skia.org/issues/420956739): Consider adding
 *             // support for PQ and HLG. Generally any code that goes through this
 *             // path would also want tone mapping too.
 *             case skcms_TFType_PQ:         return noop;
 *             case skcms_TFType_HLG:        return noop;
 *             case skcms_TFType_sRGBish:    return OpAndArg{op.sRGBish,   &tf};
 *             case skcms_TFType_PQish:      return OpAndArg{op.PQish,     &tf};
 *             case skcms_TFType_HLGish:     return OpAndArg{op.HLGish,    &tf};
 *             case skcms_TFType_HLGinvish:  return OpAndArg{op.HLGinvish, &tf};
 *         }
 *     }
 *     return OpAndArg{op.table, curve};
 * }
 * ```
 */
public fun selectCurveOp(curve: SkcmsCurve?, channel: Int): OpAndArg {
  TODO("Implement selectCurveOp")
}

/**
 * C++ original:
 * ```cpp
 * static int select_curve_ops(const skcms_Curve* curves, int numChannels, OpAndArg* ops) {
 *     // We process the channels in reverse order, yielding ops in ABGR order.
 *     // (Working backwards allows us to fuse trailing B+G+R ops into a single RGB op.)
 *     int cursor = 0;
 *     for (int index = numChannels; index-- > 0; ) {
 *         ops[cursor] = select_curve_op(&curves[index], index);
 *         if (ops[cursor].arg) {
 *             ++cursor;
 *         }
 *     }
 *
 *     // Identify separate B+G+R ops and fuse them into a single RGB op.
 *     if (cursor >= 3) {
 *         struct FusableOps {
 *             Op r, g, b, rgb;
 *         };
 *         static constexpr FusableOps kFusableOps[] = {
 *             {Op::gamma_r,  Op::gamma_g,  Op::gamma_b,  Op::gamma_rgb},
 *             {Op::tf_r,     Op::tf_g,     Op::tf_b,     Op::tf_rgb},
 *             {Op::pq_r,     Op::pq_g,     Op::pq_b,     Op::pq_rgb},
 *             {Op::hlg_r,    Op::hlg_g,    Op::hlg_b,    Op::hlg_rgb},
 *             {Op::hlginv_r, Op::hlginv_g, Op::hlginv_b, Op::hlginv_rgb},
 *         };
 *
 *         int posR = cursor - 1;
 *         int posG = cursor - 2;
 *         int posB = cursor - 3;
 *         for (const FusableOps& fusableOp : kFusableOps) {
 *             if (ops[posR].op == fusableOp.r &&
 *                 ops[posG].op == fusableOp.g &&
 *                 ops[posB].op == fusableOp.b &&
 *                 (0 == memcmp(ops[posR].arg, ops[posG].arg, sizeof(skcms_TransferFunction))) &&
 *                 (0 == memcmp(ops[posR].arg, ops[posB].arg, sizeof(skcms_TransferFunction)))) {
 *                 // Fuse the three matching ops into one.
 *                 ops[posB].op = fusableOp.rgb;
 *                 cursor -= 2;
 *                 break;
 *             }
 *         }
 *     }
 *
 *     return cursor;
 * }
 * ```
 */
public fun selectCurveOps(
  curves: SkcmsCurve?,
  numChannels: Int,
  ops: OpAndArg?,
): Int {
  TODO("Implement selectCurveOps")
}

/**
 * C++ original:
 * ```cpp
 * static size_t bytes_per_pixel(skcms_PixelFormat fmt) {
 *     switch (fmt >> 1) {   // ignore rgb/bgr
 *         case skcms_PixelFormat_A_8              >> 1: return  1;
 *         case skcms_PixelFormat_G_8              >> 1: return  1;
 *         case skcms_PixelFormat_GA_88            >> 1: return  2;
 *         case skcms_PixelFormat_ABGR_4444        >> 1: return  2;
 *         case skcms_PixelFormat_RGB_565          >> 1: return  2;
 *         case skcms_PixelFormat_RGB_888          >> 1: return  3;
 *         case skcms_PixelFormat_RGBA_8888        >> 1: return  4;
 *         case skcms_PixelFormat_RGBA_8888_sRGB   >> 1: return  4;
 *         case skcms_PixelFormat_RGBA_1010102     >> 1: return  4;
 *         case skcms_PixelFormat_RGB_101010x_XR   >> 1: return  4;
 *         case skcms_PixelFormat_RGB_161616LE     >> 1: return  6;
 *         case skcms_PixelFormat_RGBA_10101010_XR >> 1: return  8;
 *         case skcms_PixelFormat_RGBA_16161616LE  >> 1: return  8;
 *         case skcms_PixelFormat_RGB_161616BE     >> 1: return  6;
 *         case skcms_PixelFormat_RGBA_16161616BE  >> 1: return  8;
 *         case skcms_PixelFormat_RGB_hhh_Norm     >> 1: return  6;
 *         case skcms_PixelFormat_RGBA_hhhh_Norm   >> 1: return  8;
 *         case skcms_PixelFormat_RGB_hhh          >> 1: return  6;
 *         case skcms_PixelFormat_RGBA_hhhh        >> 1: return  8;
 *         case skcms_PixelFormat_RGB_fff          >> 1: return 12;
 *         case skcms_PixelFormat_RGBA_ffff        >> 1: return 16;
 *     }
 *     assert(false);
 *     return 0;
 * }
 * ```
 */
public fun bytesPerPixel(fmt: SkcmsPixelFormat): ULong {
  TODO("Implement bytesPerPixel")
}

/**
 * C++ original:
 * ```cpp
 * static bool has_cicp_pq_trc(const skcms_ICCProfile* profile) {
 *     return profile->has_CICP
 *         && profile->CICP.transfer_characteristics == kTransferCicpIdPQ;
 * }
 * ```
 */
public fun hasCicpPqTrc(profile: SkcmsICCProfile?): Boolean {
  TODO("Implement hasCicpPqTrc")
}

/**
 * C++ original:
 * ```cpp
 * static bool has_cicp_hlg_trc(const skcms_ICCProfile* profile) {
 *     return profile->has_CICP
 *         && profile->CICP.transfer_characteristics == kTransferCicpIdHLG;
 * }
 * ```
 */
public fun hasCicpHlgTrc(profile: SkcmsICCProfile?): Boolean {
  TODO("Implement hasCicpHlgTrc")
}

/**
 * C++ original:
 * ```cpp
 * static void set_reference_pq_ish_trc(skcms_TransferFunction* tf) {
 *     // Initialize such that 1.0 maps to 1.0.
 *     skcms_TransferFunction_makePQish(tf,
 *         -107/128.0f, 1.0f, 32/2523.0f, 2413/128.0f, -2392/128.0f, 8192/1305.0f);
 *
 *     // Distribute scaling factor W by scaling A and B with X ^ (1/F):
 *     // ((A + Bx^C) / (D + Ex^C))^F * W = ((A + Bx^C) / (D + Ex^C) * W^(1/F))^F
 *     // See https://crbug.com/1058580#c32 for discussion.
 *     const float w = 10000.0f / 203.0f;
 *     const float ws = powf_(w, 1.0f / tf->f);
 *     tf->a = ws * tf->a;
 *     tf->b = ws * tf->b;
 * }
 * ```
 */
public fun setReferencePqIshTrc(tf: SkcmsTransferFunction?) {
  TODO("Implement setReferencePqIshTrc")
}

/**
 * C++ original:
 * ```cpp
 * static void set_sdr_hlg_ish_trc(skcms_TransferFunction* tf) {
 *     skcms_TransferFunction_makeHLGish(tf,
 *         2.0f, 2.0f, 1/0.17883277f, 0.28466892f, 0.55991073f);
 *     tf->f = 1.0f / 12.0f - 1.0f;
 * }
 * ```
 */
public fun setSdrHlgIshTrc(tf: SkcmsTransferFunction?) {
  TODO("Implement setSdrHlgIshTrc")
}

/**
 * C++ original:
 * ```cpp
 * static bool prep_for_destination(const skcms_ICCProfile* profile,
 *                                  skcms_Matrix3x3* fromXYZD50,
 *                                  skcms_TransferFunction* invR,
 *                                  skcms_TransferFunction* invG,
 *                                  skcms_TransferFunction* invB,
 *                                  bool* dst_using_B2A,
 *                                  bool* dst_using_hlg_ootf) {
 *     const bool has_xyzd50 =
 *         profile->has_toXYZD50 &&
 *         skcms_Matrix3x3_invert(&profile->toXYZD50, fromXYZD50);
 *     *dst_using_B2A = false;
 *     *dst_using_hlg_ootf = false;
 *
 *     // CICP-specified PQ or HLG transfer functions take precedence.
 *     // TODO: Add the ability to parse CICP primaries to not require
 *     // the XYZD50 matrix.
 *     if (has_cicp_pq_trc(profile) && has_xyzd50) {
 *         skcms_TransferFunction trc_pq;
 *         set_reference_pq_ish_trc(&trc_pq);
 *         skcms_TransferFunction_invert(&trc_pq, invR);
 *         skcms_TransferFunction_invert(&trc_pq, invG);
 *         skcms_TransferFunction_invert(&trc_pq, invB);
 *         return true;
 *     }
 *     if (has_cicp_hlg_trc(profile) && has_xyzd50) {
 *         skcms_TransferFunction trc_hlg;
 *         set_sdr_hlg_ish_trc(&trc_hlg);
 *         skcms_TransferFunction_invert(&trc_hlg, invR);
 *         skcms_TransferFunction_invert(&trc_hlg, invG);
 *         skcms_TransferFunction_invert(&trc_hlg, invB);
 *         *dst_using_hlg_ootf = true;
 *         return true;
 *     }
 *
 *     // Then prefer the B2A transformation.
 *     // skcms_Transform() supports B2A destinations.
 *     if (profile->has_B2A) {
 *         *dst_using_B2A = true;
 *         return true;
 *     }
 *
 *     // Finally use parametric transfer functions.
 *     // TODO: Reject non sRGB-ish transfer functions here.
 *     return has_xyzd50
 *         && profile->has_trc
 *         && profile->trc[0].table_entries == 0
 *         && profile->trc[1].table_entries == 0
 *         && profile->trc[2].table_entries == 0
 *         && skcms_TransferFunction_invert(&profile->trc[0].parametric, invR)
 *         && skcms_TransferFunction_invert(&profile->trc[1].parametric, invG)
 *         && skcms_TransferFunction_invert(&profile->trc[2].parametric, invB);
 * }
 * ```
 */
public fun prepForDestination(
  profile: SkcmsICCProfile?,
  fromXYZD50: SkcmsMatrix3x3?,
  invR: SkcmsTransferFunction?,
  invG: SkcmsTransferFunction?,
  invB: SkcmsTransferFunction?,
  dstUsingB2A: Boolean?,
  dstUsingHlgOotf: Boolean?,
): Boolean {
  TODO("Implement prepForDestination")
}

/**
 * C++ original:
 * ```cpp
 * bool skcms_Transform(const void*             src,
 *                      skcms_PixelFormat       srcFmt,
 *                      skcms_AlphaFormat       srcAlpha,
 *                      const skcms_ICCProfile* srcProfile,
 *                      void*                   dst,
 *                      skcms_PixelFormat       dstFmt,
 *                      skcms_AlphaFormat       dstAlpha,
 *                      const skcms_ICCProfile* dstProfile,
 *                      size_t                  nz) {
 *     const size_t dst_bpp = bytes_per_pixel(dstFmt),
 *                  src_bpp = bytes_per_pixel(srcFmt);
 *     // Let's just refuse if the request is absurdly big.
 *     if (nz * dst_bpp > INT_MAX || nz * src_bpp > INT_MAX) {
 *         return false;
 *     }
 *     int n = (int)nz;
 *
 *     // Null profiles default to sRGB. Passing null for both is handy when doing format conversion.
 *     if (!srcProfile) {
 *         srcProfile = skcms_sRGB_profile();
 *     }
 *     if (!dstProfile) {
 *         dstProfile = skcms_sRGB_profile();
 *     }
 *
 *     // We can't transform in place unless the PixelFormats are the same size.
 *     if (dst == src && dst_bpp != src_bpp) {
 *         return false;
 *     }
 *     // TODO: more careful alias rejection (like, dst == src + 1)?
 *
 *     Op          program[32];
 *     const void* context[32];
 *
 *     Op*          ops      = program;
 *     const void** contexts = context;
 *
 *     auto add_op = [&](Op o) {
 *         *ops++ = o;
 *         *contexts++ = nullptr;
 *     };
 *
 *     auto add_op_ctx = [&](Op o, const void* c) {
 *         *ops++ = o;
 *         *contexts++ = c;
 *     };
 *
 *     auto add_curve_ops = [&](const skcms_Curve* curves, int numChannels) {
 *         OpAndArg oa[4];
 *         assert(numChannels <= ARRAY_COUNT(oa));
 *
 *         int numOps = select_curve_ops(curves, numChannels, oa);
 *
 *         for (int i = 0; i < numOps; ++i) {
 *             add_op_ctx(oa[i].op, oa[i].arg);
 *         }
 *     };
 *
 *     // If the source has a TRC that is specified by CICP and not the TRC
 *     // entries, then store it here for future use.
 *     skcms_TransferFunction src_cicp_trc;
 *
 *     // These are always parametric curves of some sort.
 *     skcms_Curve dst_curves[3];
 *     dst_curves[0].table_entries =
 *     dst_curves[1].table_entries =
 *     dst_curves[2].table_entries = 0;
 *
 *     // This will store the XYZD50 to destination gamut conversion matrix, if it is needed.
 *     skcms_Matrix3x3        dst_from_xyz;
 *
 *     // This will store the full source to destination gamut conversion matrix, if it is needed.
 *     skcms_Matrix3x3        dst_from_src;
 *
 *     switch (srcFmt >> 1) {
 *         default: return false;
 *         case skcms_PixelFormat_A_8              >> 1: add_op(Op::load_a8);          break;
 *         case skcms_PixelFormat_G_8              >> 1: add_op(Op::load_g8);          break;
 *         case skcms_PixelFormat_GA_88            >> 1: add_op(Op::load_ga88);        break;
 *         case skcms_PixelFormat_ABGR_4444        >> 1: add_op(Op::load_4444);        break;
 *         case skcms_PixelFormat_RGB_565          >> 1: add_op(Op::load_565);         break;
 *         case skcms_PixelFormat_RGB_888          >> 1: add_op(Op::load_888);         break;
 *         case skcms_PixelFormat_RGBA_8888        >> 1: add_op(Op::load_8888);        break;
 *         case skcms_PixelFormat_RGBA_1010102     >> 1: add_op(Op::load_1010102);     break;
 *         case skcms_PixelFormat_RGB_101010x_XR   >> 1: add_op(Op::load_101010x_XR);  break;
 *         case skcms_PixelFormat_RGBA_10101010_XR >> 1: add_op(Op::load_10101010_XR); break;
 *         case skcms_PixelFormat_RGB_161616LE     >> 1: add_op(Op::load_161616LE);    break;
 *         case skcms_PixelFormat_RGBA_16161616LE  >> 1: add_op(Op::load_16161616LE);  break;
 *         case skcms_PixelFormat_RGB_161616BE     >> 1: add_op(Op::load_161616BE);    break;
 *         case skcms_PixelFormat_RGBA_16161616BE  >> 1: add_op(Op::load_16161616BE);  break;
 *         case skcms_PixelFormat_RGB_hhh_Norm     >> 1: add_op(Op::load_hhh);         break;
 *         case skcms_PixelFormat_RGBA_hhhh_Norm   >> 1: add_op(Op::load_hhhh);        break;
 *         case skcms_PixelFormat_RGB_hhh          >> 1: add_op(Op::load_hhh);         break;
 *         case skcms_PixelFormat_RGBA_hhhh        >> 1: add_op(Op::load_hhhh);        break;
 *         case skcms_PixelFormat_RGB_fff          >> 1: add_op(Op::load_fff);         break;
 *         case skcms_PixelFormat_RGBA_ffff        >> 1: add_op(Op::load_ffff);        break;
 *
 *         case skcms_PixelFormat_RGBA_8888_sRGB >> 1:
 *             add_op(Op::load_8888);
 *             add_op_ctx(Op::tf_rgb, skcms_sRGB_TransferFunction());
 *             break;
 *     }
 *     if (srcFmt == skcms_PixelFormat_RGB_hhh_Norm ||
 *         srcFmt == skcms_PixelFormat_RGBA_hhhh_Norm) {
 *         add_op(Op::clamp);
 *     }
 *     if (srcFmt & 1) {
 *         add_op(Op::swap_rb);
 *     }
 *     skcms_ICCProfile gray_dst_profile;
 *     switch (dstFmt >> 1) {
 *         case skcms_PixelFormat_G_8:
 *         case skcms_PixelFormat_GA_88:
 *             // When transforming to gray, stop at XYZ (by setting toXYZ to identity), then transform
 *             // luminance (Y) by the destination transfer function.
 *             gray_dst_profile = *dstProfile;
 *             skcms_SetXYZD50(&gray_dst_profile, &skcms_XYZD50_profile()->toXYZD50);
 *             dstProfile = &gray_dst_profile;
 *             break;
 *         default:
 *             break;
 *     }
 *
 *     if (srcProfile->data_color_space == skcms_Signature_CMYK) {
 *         // Photoshop creates CMYK images as inverse CMYK.
 *         // These happen to be the only ones we've _ever_ seen.
 *         add_op(Op::invert);
 *         // With CMYK, ignore the alpha type, to avoid changing K or conflating CMY with K.
 *         srcAlpha = skcms_AlphaFormat_Unpremul;
 *     }
 *
 *     if (srcAlpha == skcms_AlphaFormat_Opaque) {
 *         add_op(Op::force_opaque);
 *     } else if (srcAlpha == skcms_AlphaFormat_PremulAsEncoded) {
 *         add_op(Op::unpremul);
 *     }
 *
 *     if (dstProfile != srcProfile) {
 *
 *         // Track whether or not the A2B or B2A transforms are used. the CICP
 *         // values take precedence over A2B and B2A.
 *         bool src_using_A2B = false;
 *         bool src_using_hlg_ootf = false;
 *         bool dst_using_B2A = false;
 *         bool dst_using_hlg_ootf = false;
 *
 *         if (!prep_for_destination(dstProfile,
 *                                   &dst_from_xyz,
 *                                   &dst_curves[0].parametric,
 *                                   &dst_curves[1].parametric,
 *                                   &dst_curves[2].parametric,
 *                                   &dst_using_B2A,
 *                                   &dst_using_hlg_ootf)) {
 *             return false;
 *         }
 *
 *         if (has_cicp_pq_trc(srcProfile) && srcProfile->has_toXYZD50) {
 *             set_reference_pq_ish_trc(&src_cicp_trc);
 *             add_op_ctx(Op::pq_rgb, &src_cicp_trc);
 *         } else if (has_cicp_hlg_trc(srcProfile) && srcProfile->has_toXYZD50) {
 *             src_using_hlg_ootf = true;
 *             set_sdr_hlg_ish_trc(&src_cicp_trc);
 *             add_op_ctx(Op::hlg_rgb, &src_cicp_trc);
 *         } else if (srcProfile->has_A2B) {
 *             src_using_A2B = true;
 *             if (srcProfile->A2B.input_channels) {
 *                 add_curve_ops(srcProfile->A2B.input_curves,
 *                               (int)srcProfile->A2B.input_channels);
 *                 add_op(Op::clamp);
 *                 add_op_ctx(Op::clut_A2B, &srcProfile->A2B);
 *             }
 *
 *             if (srcProfile->A2B.matrix_channels == 3) {
 *                 add_curve_ops(srcProfile->A2B.matrix_curves, /*numChannels=*/3);
 *
 *                 static const skcms_Matrix3x4 I = {{
 *                     {1,0,0,0},
 *                     {0,1,0,0},
 *                     {0,0,1,0},
 *                 }};
 *                 if (0 != memcmp(&I, &srcProfile->A2B.matrix, sizeof(I))) {
 *                     add_op_ctx(Op::matrix_3x4, &srcProfile->A2B.matrix);
 *                 }
 *             }
 *
 *             if (srcProfile->A2B.output_channels == 3) {
 *                 add_curve_ops(srcProfile->A2B.output_curves, /*numChannels=*/3);
 *             }
 *
 *             if (srcProfile->pcs == skcms_Signature_Lab) {
 *                 add_op(Op::lab_to_xyz);
 *             }
 *
 *         } else if (srcProfile->has_trc && srcProfile->has_toXYZD50) {
 *             add_curve_ops(srcProfile->trc, /*numChannels=*/3);
 *         } else {
 *             return false;
 *         }
 *
 *         // A2B sources are in XYZD50 by now, but TRC sources are still in their original gamut.
 *         assert (srcProfile->has_A2B || srcProfile->has_toXYZD50);
 *
 *         if (dst_using_B2A) {
 *             // B2A needs its input in XYZD50, so transform TRC sources now.
 *             if (!src_using_A2B) {
 *                 add_op_ctx(Op::matrix_3x3, &srcProfile->toXYZD50);
 *                 // Apply the HLG OOTF in XYZD50 space, if needed.
 *                 if (src_using_hlg_ootf) {
 *                     add_op(Op::hlg_ootf_scale);
 *                 }
 *             }
 *
 *             if (dstProfile->pcs == skcms_Signature_Lab) {
 *                 add_op(Op::xyz_to_lab);
 *             }
 *
 *             if (dstProfile->B2A.input_channels == 3) {
 *                 add_curve_ops(dstProfile->B2A.input_curves, /*numChannels=*/3);
 *             }
 *
 *             if (dstProfile->B2A.matrix_channels == 3) {
 *                 static const skcms_Matrix3x4 I = {{
 *                     {1,0,0,0},
 *                     {0,1,0,0},
 *                     {0,0,1,0},
 *                 }};
 *                 if (0 != memcmp(&I, &dstProfile->B2A.matrix, sizeof(I))) {
 *                     add_op_ctx(Op::matrix_3x4, &dstProfile->B2A.matrix);
 *                 }
 *
 *                 add_curve_ops(dstProfile->B2A.matrix_curves, /*numChannels=*/3);
 *             }
 *
 *             if (dstProfile->B2A.output_channels) {
 *                 add_op(Op::clamp);
 *                 add_op_ctx(Op::clut_B2A, &dstProfile->B2A);
 *
 *                 add_curve_ops(dstProfile->B2A.output_curves,
 *                               (int)dstProfile->B2A.output_channels);
 *             }
 *         } else {
 *             // This is a TRC destination.
 *
 *             // Transform to the destination gamut.
 *             if (src_using_hlg_ootf != dst_using_hlg_ootf) {
 *                 // If just the src or the dst has an HLG OOTF then we will apply the OOTF in XYZD50
 *                 // space. If both the src and dst has an HLG OOTF then they will cancel.
 *                 if (!src_using_A2B) {
 *                     add_op_ctx(Op::matrix_3x3, &srcProfile->toXYZD50);
 *                 }
 *                 if (src_using_hlg_ootf) {
 *                     add_op(Op::hlg_ootf_scale);
 *                 }
 *                 if (dst_using_hlg_ootf) {
 *                     add_op(Op::hlginv_ootf_scale);
 *                 }
 *                 add_op_ctx(Op::matrix_3x3, &dst_from_xyz);
 *             } else if (src_using_A2B) {
 *                 // If the source is A2B then we are already in XYZD50. Just apply the xyz->dst
 *                 // matrix.
 *                 add_op_ctx(Op::matrix_3x3, &dst_from_xyz);
 *             } else {
 *                 const skcms_Matrix3x3* to_xyz = &srcProfile->toXYZD50;
 *                 // There's a chance the source and destination gamuts are identical,
 *                 // in which case we can skip the gamut transform.
 *                 if (0 != memcmp(&dstProfile->toXYZD50, to_xyz, sizeof(skcms_Matrix3x3))) {
 *                     // Concat the entire gamut transform into dst_from_src.
 *                     dst_from_src = skcms_Matrix3x3_concat(&dst_from_xyz, to_xyz);
 *                     add_op_ctx(Op::matrix_3x3, &dst_from_src);
 *                 }
 *             }
 *
 *             // Encode back to dst RGB using its parametric transfer functions.
 *             OpAndArg oa[3];
 *             int numOps = select_curve_ops(dst_curves, /*numChannels=*/3, oa);
 *             for (int index = 0; index < numOps; ++index) {
 *                 assert(oa[index].op != Op::table_r &&
 *                        oa[index].op != Op::table_g &&
 *                        oa[index].op != Op::table_b &&
 *                        oa[index].op != Op::table_a);
 *                 add_op_ctx(oa[index].op, oa[index].arg);
 *             }
 *         }
 *     }
 *
 *     // Clamp here before premul to make sure we're clamping to normalized values _and_ gamut,
 *     // not just to values that fit in [0,1].
 *     //
 *     // E.g. r = 1.1, a = 0.5 would fit fine in fixed point after premul (ra=0.55,a=0.5),
 *     // but would be carrying r > 1, which is really unexpected for downstream consumers.
 *     if (dstFmt < skcms_PixelFormat_RGB_hhh) {
 *         add_op(Op::clamp);
 *     }
 *
 *     if (dstProfile->data_color_space == skcms_Signature_CMYK) {
 *         // Photoshop creates CMYK images as inverse CMYK.
 *         // These happen to be the only ones we've _ever_ seen.
 *         add_op(Op::invert);
 *
 *         // CMYK has no alpha channel, so make sure dstAlpha is a no-op.
 *         dstAlpha = skcms_AlphaFormat_Unpremul;
 *     }
 *
 *     if (dstAlpha == skcms_AlphaFormat_Opaque) {
 *         add_op(Op::force_opaque);
 *     } else if (dstAlpha == skcms_AlphaFormat_PremulAsEncoded) {
 *         add_op(Op::premul);
 *     }
 *     if (dstFmt & 1) {
 *         add_op(Op::swap_rb);
 *     }
 *     switch (dstFmt >> 1) {
 *         default: return false;
 *         case skcms_PixelFormat_A_8              >> 1: add_op(Op::store_a8);          break;
 *         case skcms_PixelFormat_G_8              >> 1: add_op(Op::store_g8);          break;
 *         case skcms_PixelFormat_GA_88            >> 1: add_op(Op::store_ga88);        break;
 *         case skcms_PixelFormat_ABGR_4444        >> 1: add_op(Op::store_4444);        break;
 *         case skcms_PixelFormat_RGB_565          >> 1: add_op(Op::store_565);         break;
 *         case skcms_PixelFormat_RGB_888          >> 1: add_op(Op::store_888);         break;
 *         case skcms_PixelFormat_RGBA_8888        >> 1: add_op(Op::store_8888);        break;
 *         case skcms_PixelFormat_RGBA_1010102     >> 1: add_op(Op::store_1010102);     break;
 *         case skcms_PixelFormat_RGB_161616LE     >> 1: add_op(Op::store_161616LE);    break;
 *         case skcms_PixelFormat_RGBA_16161616LE  >> 1: add_op(Op::store_16161616LE);  break;
 *         case skcms_PixelFormat_RGB_161616BE     >> 1: add_op(Op::store_161616BE);    break;
 *         case skcms_PixelFormat_RGBA_16161616BE  >> 1: add_op(Op::store_16161616BE);  break;
 *         case skcms_PixelFormat_RGB_hhh_Norm     >> 1: add_op(Op::store_hhh);         break;
 *         case skcms_PixelFormat_RGBA_hhhh_Norm   >> 1: add_op(Op::store_hhhh);        break;
 *         case skcms_PixelFormat_RGB_101010x_XR   >> 1: add_op(Op::store_101010x_XR);  break;
 *         case skcms_PixelFormat_RGBA_10101010_XR >> 1: add_op(Op::store_10101010_XR); break;
 *         case skcms_PixelFormat_RGB_hhh          >> 1: add_op(Op::store_hhh);         break;
 *         case skcms_PixelFormat_RGBA_hhhh        >> 1: add_op(Op::store_hhhh);        break;
 *         case skcms_PixelFormat_RGB_fff          >> 1: add_op(Op::store_fff);         break;
 *         case skcms_PixelFormat_RGBA_ffff        >> 1: add_op(Op::store_ffff);        break;
 *
 *         case skcms_PixelFormat_RGBA_8888_sRGB >> 1:
 *             add_op_ctx(Op::tf_rgb, skcms_sRGB_Inverse_TransferFunction());
 *             add_op(Op::store_8888);
 *             break;
 *     }
 *
 *     assert(ops      <= program + ARRAY_COUNT(program));
 *     assert(contexts <= context + ARRAY_COUNT(context));
 *
 *     auto run = baseline::run_program;
 *     switch (cpu_type()) {
 *         case CpuType::SKX:
 *             #if !defined(SKCMS_DISABLE_SKX)
 *                 run = skx::run_program;
 *                 break;
 *             #endif
 *
 *         case CpuType::HSW:
 *             #if !defined(SKCMS_DISABLE_HSW)
 *                 run = hsw::run_program;
 *                 break;
 *             #endif
 *
 *         case CpuType::Baseline:
 *             break;
 *     }
 *
 *     run(program, context, ops - program, (const char*)src, (char*)dst, n, src_bpp,dst_bpp);
 *     return true;
 * }
 * ```
 */
public fun skcmsTransform(
  src: Unit?,
  srcFmt: SkcmsPixelFormat,
  srcAlpha: SkcmsAlphaFormat,
  srcProfile: SkcmsICCProfile?,
  dst: Unit?,
  dstFmt: SkcmsPixelFormat,
  dstAlpha: SkcmsAlphaFormat,
  dstProfile: SkcmsICCProfile?,
  nz: ULong,
): Boolean {
  TODO("Implement skcmsTransform")
}

/**
 * C++ original:
 * ```cpp
 * static void assert_usable_as_destination(const skcms_ICCProfile* profile) {
 * #if defined(NDEBUG)
 *     (void)profile;
 * #else
 *     skcms_Matrix3x3 fromXYZD50;
 *     skcms_TransferFunction invR, invG, invB;
 *     bool useB2A = false;
 *     bool useHlgOotf = false;
 *     assert(prep_for_destination(profile, &fromXYZD50, &invR, &invG, &invB, &useB2A, &useHlgOotf));
 * #endif
 * }
 * ```
 */
public fun assertUsableAsDestination(profile: SkcmsICCProfile?) {
  TODO("Implement assertUsableAsDestination")
}

/**
 * C++ original:
 * ```cpp
 * bool skcms_MakeUsableAsDestination(skcms_ICCProfile* profile) {
 *     if (!profile->has_B2A) {
 *         skcms_Matrix3x3 fromXYZD50;
 *         if (!profile->has_trc || !profile->has_toXYZD50
 *             || !skcms_Matrix3x3_invert(&profile->toXYZD50, &fromXYZD50)) {
 *             return false;
 *         }
 *
 *         skcms_TransferFunction tf[3];
 *         for (int i = 0; i < 3; i++) {
 *             skcms_TransferFunction inv;
 *             if (profile->trc[i].table_entries == 0
 *                 && skcms_TransferFunction_invert(&profile->trc[i].parametric, &inv)) {
 *                 tf[i] = profile->trc[i].parametric;
 *                 continue;
 *             }
 *
 *             float max_error;
 *             // Parametric curves from skcms_ApproximateCurve() are guaranteed to be invertible.
 *             if (!skcms_ApproximateCurve(&profile->trc[i], &tf[i], &max_error)) {
 *                 return false;
 *             }
 *         }
 *
 *         for (int i = 0; i < 3; ++i) {
 *             profile->trc[i].table_entries = 0;
 *             profile->trc[i].parametric = tf[i];
 *         }
 *     }
 *     assert_usable_as_destination(profile);
 *     return true;
 * }
 * ```
 */
public fun skcmsMakeUsableAsDestination(profile: SkcmsICCProfile?): Boolean {
  TODO("Implement skcmsMakeUsableAsDestination")
}

/**
 * C++ original:
 * ```cpp
 * bool skcms_MakeUsableAsDestinationWithSingleCurve(skcms_ICCProfile* profile) {
 *     // Call skcms_MakeUsableAsDestination() with B2A disabled;
 *     // on success that'll return a TRC/XYZ profile with three skcms_TransferFunctions.
 *     skcms_ICCProfile result = *profile;
 *     result.has_B2A = false;
 *     if (!skcms_MakeUsableAsDestination(&result)) {
 *         return false;
 *     }
 *
 *     // Of the three, pick the transfer function that best fits the other two.
 *     int best_tf = 0;
 *     float min_max_error = INFINITY_;
 *     for (int i = 0; i < 3; i++) {
 *         skcms_TransferFunction inv;
 *         if (!skcms_TransferFunction_invert(&result.trc[i].parametric, &inv)) {
 *             return false;
 *         }
 *
 *         float err = 0;
 *         for (int j = 0; j < 3; ++j) {
 *             err = fmaxf_(err, skcms_MaxRoundtripError(&profile->trc[j], &inv));
 *         }
 *         if (min_max_error > err) {
 *             min_max_error = err;
 *             best_tf = i;
 *         }
 *     }
 *
 *     for (int i = 0; i < 3; i++) {
 *         result.trc[i].parametric = result.trc[best_tf].parametric;
 *     }
 *
 *     *profile = result;
 *     assert_usable_as_destination(profile);
 *     return true;
 * }
 * ```
 */
public fun skcmsMakeUsableAsDestinationWithSingleCurve(profile: SkcmsICCProfile?): Boolean {
  TODO("Implement skcmsMakeUsableAsDestinationWithSingleCurve")
}

/**
 * C++ original:
 * ```cpp
 * SI F approx_log(F x) {
 *     const float ln2 = 0.69314718f;
 *     return ln2 * approx_log2(x);
 * }
 * ```
 */
public fun <F> approxLog(x: F): Any {
  TODO("Implement approxLog")
}

/**
 * C++ original:
 * ```cpp
 * static inline bool skcms_TransferFunction_makeHLGish(skcms_TransferFunction* fn,
 *                                                      float R, float G,
 *                                                      float a, float b, float c) {
 *     return skcms_TransferFunction_makeScaledHLGish(fn, 1.0f, R,G, a,b,c);
 * }
 * ```
 */
public fun skcmsTransferFunctionMakeHLGish(
  fn: SkcmsTransferFunction?,
  r: Float,
  g: Float,
  a: Float,
  b: Float,
  c: Float,
): Boolean {
  TODO("Implement skcmsTransferFunctionMakeHLGish")
}

/**
 * C++ original:
 * ```cpp
 * static inline bool skcms_Parse(const void* buf, size_t len, skcms_ICCProfile* profile) {
 *     // For continuity of existing user expectations,
 *     // prefer A2B0 (perceptual) over A2B1 (relative colormetric), and ignore A2B2 (saturation).
 *     const int priority[] = {0,1};
 *     return skcms_ParseWithA2BPriority(buf, len,
 *                                       priority, sizeof(priority)/sizeof(*priority),
 *                                       profile);
 * }
 * ```
 */
public fun skcmsParse(
  buf: Unit?,
  len: ULong,
  profile: SkcmsICCProfile?,
): Boolean {
  TODO("Implement skcmsParse")
}

/**
 * C++ original:
 * ```cpp
 * static inline void skcms_Init(skcms_ICCProfile* p) {
 *     memset(p, 0, sizeof(*p));
 *     p->data_color_space = skcms_Signature_RGB;
 *     p->pcs = skcms_Signature_XYZ;
 * }
 * ```
 */
public fun skcmsInit(p: SkcmsICCProfile?) {
  TODO("Implement skcmsInit")
}

/**
 * C++ original:
 * ```cpp
 * static inline void skcms_SetTransferFunction(skcms_ICCProfile* p,
 *                                              const skcms_TransferFunction* tf) {
 *     p->has_trc = true;
 *     for (int i = 0; i < 3; ++i) {
 *         p->trc[i].table_entries = 0;
 *         p->trc[i].parametric = *tf;
 *     }
 * }
 * ```
 */
public fun skcmsSetTransferFunction(p: SkcmsICCProfile?, tf: SkcmsTransferFunction?) {
  TODO("Implement skcmsSetTransferFunction")
}

/**
 * C++ original:
 * ```cpp
 * static inline void skcms_SetXYZD50(skcms_ICCProfile* p, const skcms_Matrix3x3* m) {
 *     p->has_toXYZD50 = true;
 *     p->toXYZD50 = *m;
 * }
 * ```
 */
public fun skcmsSetXYZD50(p: SkcmsICCProfile?, m: SkcmsMatrix3x3?) {
  TODO("Implement skcmsSetXYZD50")
}

/**
 * C++ original:
 * ```cpp
 * static inline float floorf_(float x) {
 *     float roundtrip = (float)((int)x);
 *     return roundtrip > x ? roundtrip - 1 : roundtrip;
 * }
 * ```
 */
public fun floorf(x: Float): Float {
  TODO("Implement floorf")
}

/**
 * C++ original:
 * ```cpp
 * static inline float fabsf_(float x) { return x < 0 ? -x : x; }
 * ```
 */
public fun fabsf(x: Float): Float {
  TODO("Implement fabsf")
}

/**
 * C++ original:
 * ```cpp
 * template <typename T, typename P>
 * SI T load(const P* ptr) {
 *     T val;
 *     memcpy(&val, ptr, sizeof(val));
 *     return val;
 * }
 * ```
 */
public fun <T, P> load(ptr: P): T {
  TODO("Implement load")
}

/**
 * C++ original:
 * ```cpp
 * template <typename T, typename P>
 * SI void store(P* ptr, const T& val) {
 *     memcpy(ptr, &val, sizeof(val));
 * }
 * ```
 */
public fun <T, P> store(ptr: P, `val`: T) {
  TODO("Implement store")
}

/**
 * C++ original:
 * ```cpp
 * template <typename D, typename S>
 * SI D cast(const S& v) {
 * #if N == 1
 *     return (D)v;
 * #else
 *     return __builtin_convertvector(v, D);
 *
 * #endif
 * }
 * ```
 */
public fun <D, S> cast(v: S): D {
  TODO("Implement cast")
}

/**
 * C++ original:
 * ```cpp
 * template <typename D, typename S>
 * SI D bit_pun(const S& v) {
 *     static_assert(sizeof(D) == sizeof(v), "");
 *     return load<D>(&v);
 * }
 * ```
 */
public fun <D, S> bitPun(v: S): D {
  TODO("Implement bitPun")
}

/**
 * C++ original:
 * ```cpp
 * SI U32 to_fixed(F f) {  return (U32)cast<I32>(f + 0.5f); }
 * ```
 */
public fun <F> toFixed(f: F): Any {
  TODO("Implement toFixed")
}

/**
 * C++ original:
 * ```cpp
 *     template <typename C, typename T>
 *     SI T if_then_else(C cond, T t, T e) {
 *         return bit_pun<T>( ( cond & bit_pun<C>(t)) |
 *                            (~cond & bit_pun<C>(e)) );
 *     }
 * ```
 */
public fun <C, T> ifThenElse(
  cond: C,
  t: T,
  e: T,
): T {
  TODO("Implement ifThenElse")
}

/**
 * C++ original:
 * ```cpp
 * SI F F_from_Half(U16 half) {
 * #if defined(USING_NEON_F16C)
 *     return vcvt_f32_f16((float16x4_t)half);
 * #elif defined(USING_AVX512F)
 *     return (F)_mm512_cvtph_ps((__m256i)half);
 * #elif defined(USING_AVX_F16C)
 * #if defined(__clang__) && __clang_major__ >= 15 // for _Float16 support
 *     typedef _Float16 __attribute__((vector_size(16))) F16;
 *     return __builtin_convertvector((F16)half, F);
 * #else
 *     typedef int16_t __attribute__((vector_size(16))) I16;
 *     return __builtin_ia32_vcvtph2ps256((I16)half);
 * #endif // defined(__clang))
 * #else
 *     U32 wide = cast<U32>(half);
 *     // A half is 1-5-10 sign-exponent-mantissa, with 15 exponent bias.
 *     U32 s  = wide & 0x8000,
 *         em = wide ^ s;
 *
 *     // Constructing the float is easy if the half is not denormalized.
 *     F norm = bit_pun<F>( (s<<16) + (em<<13) + ((127-15)<<23) );
 *
 *     // Simply flush all denorm half floats to zero.
 *     return if_then_else(em < 0x0400, F0, norm);
 * #endif
 * }
 * ```
 */
public fun fFromHalf(half: U16): Any {
  TODO("Implement fFromHalf")
}

/**
 * C++ original:
 * ```cpp
 * __attribute__((no_sanitize("unsigned-integer-overflow")))
 * #endif
 * SI U16 Half_from_F(F f) {
 * #if defined(USING_NEON_F16C)
 *     return (U16)vcvt_f16_f32(f);
 * #elif defined(USING_AVX512F)
 *     return (U16)_mm512_cvtps_ph((__m512 )f, _MM_FROUND_CUR_DIRECTION );
 * #elif defined(USING_AVX_F16C)
 *     return (U16)__builtin_ia32_vcvtps2ph256(f, 0x04/*_MM_FROUND_CUR_DIRECTION*/);
 * #else
 *     // A float is 1-8-23 sign-exponent-mantissa, with 127 exponent bias.
 *     U32 sem = bit_pun<U32>(f),
 *         s   = sem & 0x80000000,
 *          em = sem ^ s;
 *
 *     // For simplicity we flush denorm half floats (including all denorm floats) to zero.
 *     return cast<U16>(if_then_else(em < 0x38800000, (U32)F0
 *                                                  , (s>>16) + (em>>13) - ((127-15)<<10)));
 * #endif
 * }
 * ```
 */
public fun halfFromF(param0: Any) {
  TODO("Implement halfFromF")
}

/**
 * C++ original:
 * ```cpp
 * SI U64 swap_endian_16x4(const U64& rgba) {
 *     return (rgba & 0x00ff00ff00ff00ff) << 8
 *          | (rgba & 0xff00ff00ff00ff00) >> 8;
 * }
 * ```
 */
public fun swapEndian16x4(rgba: U64): Any {
  TODO("Implement swapEndian16x4")
}

/**
 * C++ original:
 * ```cpp
 * SI F min_(F x, F y) { return if_then_else(x > y, y, x); }
 * ```
 */
public fun <F> min(x: F, y: F): Any {
  TODO("Implement min")
}

/**
 * C++ original:
 * ```cpp
 * SI F max_(F x, F y) { return if_then_else(x < y, y, x); }
 * ```
 */
public fun <F> max(x: F, y: F): Any {
  TODO("Implement max")
}

/**
 * C++ original:
 * ```cpp
 * SI F floor_(F x) {
 * #if N == 1
 *     return floorf_(x);
 * #elif defined(__aarch64__)
 *     return vrndmq_f32(x);
 * #elif defined(USING_AVX512F)
 *     // Clang's _mm512_floor_ps() passes its mask as -1, not (__mmask16)-1,
 *     // and integer santizer catches that this implicit cast changes the
 *     // value from -1 to 65535.  We'll cast manually to work around it.
 *     // Read this as `return _mm512_floor_ps(x)`.
 *     return _mm512_mask_floor_ps(x, (__mmask16)-1, x);
 * #elif defined(USING_AVX)
 *     return __builtin_ia32_roundps256(x, 0x01/*_MM_FROUND_FLOOR*/);
 * #elif defined(__SSE4_1__)
 *     return _mm_floor_ps(x);
 * #elif defined(__loongarch_sx)
 *     return __lsx_vfrintrm_s((__m128)x);
 * #else
 *     // Round trip through integers with a truncating cast.
 *     F roundtrip = cast<F>(cast<I32>(x));
 *     // If x is negative, truncating gives the ceiling instead of the floor.
 *     return roundtrip - if_then_else(roundtrip > x, F1, F0);
 *
 *     // This implementation fails for values of x that are outside
 *     // the range an integer can represent.  We expect most x to be small.
 * #endif
 * }
 * ```
 */
public fun <F> floor(x: F): Any {
  TODO("Implement floor")
}

/**
 * C++ original:
 * ```cpp
 * SI F approx_log2(F x) {
 *     // The first approximation of log2(x) is its exponent 'e', minus 127.
 *     I32 bits = bit_pun<I32>(x);
 *
 *     F e = cast<F>(bits) * (1.0f / (1<<23));
 *
 *     // If we use the mantissa too we can refine the error signficantly.
 *     F m = bit_pun<F>( (bits & 0x007fffff) | 0x3f000000 );
 *
 *     return e - 124.225514990f
 *              -   1.498030302f*m
 *              -   1.725879990f/(0.3520887068f + m);
 * }
 * ```
 */
public fun <F> approxLog2(x: F): Any {
  TODO("Implement approxLog2")
}

/**
 * C++ original:
 * ```cpp
 * SI F approx_exp2(F x) {
 *     F fract = x - floor_(x);
 *
 *     F fbits = (1.0f * (1<<23)) * (x + 121.274057500f
 *                                     -   1.490129070f*fract
 *                                     +  27.728023300f/(4.84252568f - fract));
 *     I32 bits = cast<I32>(min_(max_(fbits, F0), FInfBits));
 *
 *     return bit_pun<F>(bits);
 * }
 * ```
 */
public fun <F> approxExp2(x: F): Any {
  TODO("Implement approxExp2")
}

/**
 * C++ original:
 * ```cpp
 * SI F approx_pow(F x, float y) {
 *     return if_then_else((x == F0) | (x == F1), x
 *                                              , approx_exp2(approx_log2(x) * y));
 * }
 * ```
 */
public fun <F> approxPow(x: F, y: Float): Any {
  TODO("Implement approxPow")
}

/**
 * C++ original:
 * ```cpp
 * SI F approx_exp(F x) {
 *     const float log2_e = 1.4426950408889634074f;
 *     return approx_exp2(log2_e * x);
 * }
 * ```
 */
public fun <F> approxExp(x: F): Any {
  TODO("Implement approxExp")
}

/**
 * C++ original:
 * ```cpp
 * SI F strip_sign(F x, U32* sign) {
 *     U32 bits = bit_pun<U32>(x);
 *     *sign = bits & 0x80000000;
 *     return bit_pun<F>(bits ^ *sign);
 * }
 * ```
 */
public fun <F> stripSign(x: F, sign: U32?): Any {
  TODO("Implement stripSign")
}

/**
 * C++ original:
 * ```cpp
 * SI F apply_sign(F x, U32 sign) {
 *     return bit_pun<F>(sign | bit_pun<U32>(x));
 * }
 * ```
 */
public fun <F> applySign(x: F, sign: U32): Any {
  TODO("Implement applySign")
}

/**
 * C++ original:
 * ```cpp
 * SI F apply_tf(const skcms_TransferFunction* tf, F x) {
 *     // Peel off the sign bit and set x = |x|.
 *     U32 sign;
 *     x = strip_sign(x, &sign);
 *
 *     // The transfer function has a linear part up to d, exponential at d and after.
 *     F v = if_then_else(x < tf->d,            tf->c*x + tf->f
 *                                 , approx_pow(tf->a*x + tf->b, tf->g) + tf->e);
 *
 *     // Tack the sign bit back on.
 *     return apply_sign(v, sign);
 * }
 * ```
 */
public fun <F> applyTf(tf: SkcmsTransferFunction?, x: F): Any {
  TODO("Implement applyTf")
}

/**
 * C++ original:
 * ```cpp
 * SI F apply_gamma(const skcms_TransferFunction* tf, F x) {
 *     U32 sign;
 *     x = strip_sign(x, &sign);
 *     return apply_sign(approx_pow(x, tf->g), sign);
 * }
 * ```
 */
public fun <F> applyGamma(tf: SkcmsTransferFunction?, x: F): Any {
  TODO("Implement applyGamma")
}

/**
 * C++ original:
 * ```cpp
 * SI F apply_pq(const skcms_TransferFunction* tf, F x) {
 *     U32 bits = bit_pun<U32>(x),
 *         sign = bits & 0x80000000;
 *     x = bit_pun<F>(bits ^ sign);
 *
 *     F v = approx_pow(max_(tf->a + tf->b * approx_pow(x, tf->c), F0)
 *                        / (tf->d + tf->e * approx_pow(x, tf->c)),
 *                      tf->f);
 *
 *     return bit_pun<F>(sign | bit_pun<U32>(v));
 * }
 * ```
 */
public fun <F> applyPq(tf: SkcmsTransferFunction?, x: F): Any {
  TODO("Implement applyPq")
}

/**
 * C++ original:
 * ```cpp
 * SI F apply_hlg(const skcms_TransferFunction* tf, F x) {
 *     const float R = tf->a, G = tf->b,
 *                 a = tf->c, b = tf->d, c = tf->e,
 *                 K = tf->f + 1;
 *     U32 bits = bit_pun<U32>(x),
 *         sign = bits & 0x80000000;
 *     x = bit_pun<F>(bits ^ sign);
 *
 *     F v = if_then_else(x*R <= 1, approx_pow(x*R, G)
 *                                , approx_exp((x-c)*a) + b);
 *
 *     return K*bit_pun<F>(sign | bit_pun<U32>(v));
 * }
 * ```
 */
public fun <F> applyHlg(tf: SkcmsTransferFunction?, x: F): Any {
  TODO("Implement applyHlg")
}

/**
 * C++ original:
 * ```cpp
 * SI F apply_hlginv(const skcms_TransferFunction* tf, F x) {
 *     const float R = tf->a, G = tf->b,
 *                 a = tf->c, b = tf->d, c = tf->e,
 *                 K = tf->f + 1;
 *     U32 bits = bit_pun<U32>(x),
 *         sign = bits & 0x80000000;
 *     x = bit_pun<F>(bits ^ sign);
 *     x /= K;
 *
 *     F v = if_then_else(x <= 1, R * approx_pow(x, G)
 *                              , a * approx_log(x - b) + c);
 *
 *     return bit_pun<F>(sign | bit_pun<U32>(v));
 * }
 * ```
 */
public fun <F> applyHlginv(tf: SkcmsTransferFunction?, x: F): Any {
  TODO("Implement applyHlginv")
}

/**
 * C++ original:
 * ```cpp
 * SI F compute_Y_in_xyzd50(F x, F y, F z) {
 *   return -0.02831655f * x +
 *           1.00995452f * y +
 *           0.02102382f * z;
 * }
 * ```
 */
public fun <F> computeYInXyzd50(
  x: F,
  y: F,
  z: F,
): Any {
  TODO("Implement computeYInXyzd50")
}

/**
 * C++ original:
 * ```cpp
 * template <typename T, typename P>
 * SI T load_3(const P* p) {
 * #if N == 1
 *     return (T)p[0];
 * #elif N == 4
 *     return T{p[ 0],p[ 3],p[ 6],p[ 9]};
 * #elif N == 8
 *     return T{p[ 0],p[ 3],p[ 6],p[ 9], p[12],p[15],p[18],p[21]};
 * #elif N == 16
 *     return T{p[ 0],p[ 3],p[ 6],p[ 9], p[12],p[15],p[18],p[21],
 *              p[24],p[27],p[30],p[33], p[36],p[39],p[42],p[45]};
 * #endif
 * }
 * ```
 */
public fun <T, P> load3(p: P): T {
  TODO("Implement load3")
}

/**
 * C++ original:
 * ```cpp
 * template <typename T, typename P>
 * SI T load_4(const P* p) {
 * #if N == 1
 *     return (T)p[0];
 * #elif N == 4
 *     return T{p[ 0],p[ 4],p[ 8],p[12]};
 * #elif N == 8
 *     return T{p[ 0],p[ 4],p[ 8],p[12], p[16],p[20],p[24],p[28]};
 * #elif N == 16
 *     return T{p[ 0],p[ 4],p[ 8],p[12], p[16],p[20],p[24],p[28],
 *              p[32],p[36],p[40],p[44], p[48],p[52],p[56],p[60]};
 * #endif
 * }
 * ```
 */
public fun <T, P> load4(p: P): T {
  TODO("Implement load4")
}

/**
 * C++ original:
 * ```cpp
 * template <typename T, typename P>
 * SI void store_3(P* p, const T& v) {
 * #if N == 1
 *     p[0] = v;
 * #elif N == 4
 *     p[ 0] = v[ 0]; p[ 3] = v[ 1]; p[ 6] = v[ 2]; p[ 9] = v[ 3];
 * #elif N == 8
 *     p[ 0] = v[ 0]; p[ 3] = v[ 1]; p[ 6] = v[ 2]; p[ 9] = v[ 3];
 *     p[12] = v[ 4]; p[15] = v[ 5]; p[18] = v[ 6]; p[21] = v[ 7];
 * #elif N == 16
 *     p[ 0] = v[ 0]; p[ 3] = v[ 1]; p[ 6] = v[ 2]; p[ 9] = v[ 3];
 *     p[12] = v[ 4]; p[15] = v[ 5]; p[18] = v[ 6]; p[21] = v[ 7];
 *     p[24] = v[ 8]; p[27] = v[ 9]; p[30] = v[10]; p[33] = v[11];
 *     p[36] = v[12]; p[39] = v[13]; p[42] = v[14]; p[45] = v[15];
 * #endif
 * }
 * ```
 */
public fun <T, P> store3(p: P, v: T) {
  TODO("Implement store3")
}

/**
 * C++ original:
 * ```cpp
 * template <typename T, typename P>
 * SI void store_4(P* p, const T& v) {
 * #if N == 1
 *     p[0] = v;
 * #elif N == 4
 *     p[ 0] = v[ 0]; p[ 4] = v[ 1]; p[ 8] = v[ 2]; p[12] = v[ 3];
 * #elif N == 8
 *     p[ 0] = v[ 0]; p[ 4] = v[ 1]; p[ 8] = v[ 2]; p[12] = v[ 3];
 *     p[16] = v[ 4]; p[20] = v[ 5]; p[24] = v[ 6]; p[28] = v[ 7];
 * #elif N == 16
 *     p[ 0] = v[ 0]; p[ 4] = v[ 1]; p[ 8] = v[ 2]; p[12] = v[ 3];
 *     p[16] = v[ 4]; p[20] = v[ 5]; p[24] = v[ 6]; p[28] = v[ 7];
 *     p[32] = v[ 8]; p[36] = v[ 9]; p[40] = v[10]; p[44] = v[11];
 *     p[48] = v[12]; p[52] = v[13]; p[56] = v[14]; p[60] = v[15];
 * #endif
 * }
 * ```
 */
public fun <T, P> store4(p: P, v: T) {
  TODO("Implement store4")
}

/**
 * C++ original:
 * ```cpp
 * SI U8 gather_8(const uint8_t* p, I32 ix) {
 * #if N == 1
 *     U8 v = p[ix];
 * #elif N == 4
 *     U8 v = { p[ix[0]], p[ix[1]], p[ix[2]], p[ix[3]] };
 * #elif N == 8
 *     U8 v = { p[ix[0]], p[ix[1]], p[ix[2]], p[ix[3]],
 *              p[ix[4]], p[ix[5]], p[ix[6]], p[ix[7]] };
 * #elif N == 16
 *     U8 v = { p[ix[ 0]], p[ix[ 1]], p[ix[ 2]], p[ix[ 3]],
 *              p[ix[ 4]], p[ix[ 5]], p[ix[ 6]], p[ix[ 7]],
 *              p[ix[ 8]], p[ix[ 9]], p[ix[10]], p[ix[11]],
 *              p[ix[12]], p[ix[13]], p[ix[14]], p[ix[15]] };
 * #endif
 *     return v;
 * }
 * ```
 */
public fun gather8(p: UByte?, ix: I32): Any {
  TODO("Implement gather8")
}

/**
 * C++ original:
 * ```cpp
 * SI U16 gather_16(const uint8_t* p, I32 ix) {
 *     // Load the i'th 16-bit value from p.
 *     auto load_16 = [p](int i) {
 *         return load<uint16_t>(p + 2*i);
 *     };
 * #if N == 1
 *     U16 v = load_16(ix);
 * #elif N == 4
 *     U16 v = { load_16(ix[0]), load_16(ix[1]), load_16(ix[2]), load_16(ix[3]) };
 * #elif N == 8
 *     U16 v = { load_16(ix[0]), load_16(ix[1]), load_16(ix[2]), load_16(ix[3]),
 *               load_16(ix[4]), load_16(ix[5]), load_16(ix[6]), load_16(ix[7]) };
 * #elif N == 16
 *     U16 v = { load_16(ix[ 0]), load_16(ix[ 1]), load_16(ix[ 2]), load_16(ix[ 3]),
 *               load_16(ix[ 4]), load_16(ix[ 5]), load_16(ix[ 6]), load_16(ix[ 7]),
 *               load_16(ix[ 8]), load_16(ix[ 9]), load_16(ix[10]), load_16(ix[11]),
 *               load_16(ix[12]), load_16(ix[13]), load_16(ix[14]), load_16(ix[15]) };
 * #endif
 *     return v;
 * }
 * ```
 */
public fun gather16(p: UByte?, ix: I32): Any {
  TODO("Implement gather16")
}

/**
 * C++ original:
 * ```cpp
 * SI U32 gather_32(const uint8_t* p, I32 ix) {
 *     // Load the i'th 32-bit value from p.
 *     auto load_32 = [p](int i) {
 *         return load<uint32_t>(p + 4*i);
 *     };
 * #if N == 1
 *     U32 v = load_32(ix);
 * #elif N == 4
 *     U32 v = { load_32(ix[0]), load_32(ix[1]), load_32(ix[2]), load_32(ix[3]) };
 * #elif N == 8
 *     U32 v = { load_32(ix[0]), load_32(ix[1]), load_32(ix[2]), load_32(ix[3]),
 *               load_32(ix[4]), load_32(ix[5]), load_32(ix[6]), load_32(ix[7]) };
 * #elif N == 16
 *     U32 v = { load_32(ix[ 0]), load_32(ix[ 1]), load_32(ix[ 2]), load_32(ix[ 3]),
 *               load_32(ix[ 4]), load_32(ix[ 5]), load_32(ix[ 6]), load_32(ix[ 7]),
 *               load_32(ix[ 8]), load_32(ix[ 9]), load_32(ix[10]), load_32(ix[11]),
 *               load_32(ix[12]), load_32(ix[13]), load_32(ix[14]), load_32(ix[15]) };
 * #endif
 *     // TODO: AVX2 and AVX-512 gathers (c.f. gather_24).
 *     return v;
 * }
 * ```
 */
public fun gather32(p: UByte?, ix: I32): Any {
  TODO("Implement gather32")
}

/**
 * C++ original:
 * ```cpp
 * SI U32 gather_24(const uint8_t* p, I32 ix) {
 *     // First, back up a byte.  Any place we're gathering from has a safe junk byte to read
 *     // in front of it, either a previous table value, or some tag metadata.
 *     p -= 1;
 *
 *     // Load the i'th 24-bit value from p, and 1 extra byte.
 *     auto load_24_32 = [p](int i) {
 *         return load<uint32_t>(p + 3*i);
 *     };
 *
 *     // Now load multiples of 4 bytes (a junk byte, then r,g,b).
 * #if N == 1
 *     U32 v = load_24_32(ix);
 * #elif N == 4
 *     U32 v = { load_24_32(ix[0]), load_24_32(ix[1]), load_24_32(ix[2]), load_24_32(ix[3]) };
 * #elif N == 8 && !defined(USING_AVX2)
 *     U32 v = { load_24_32(ix[0]), load_24_32(ix[1]), load_24_32(ix[2]), load_24_32(ix[3]),
 *               load_24_32(ix[4]), load_24_32(ix[5]), load_24_32(ix[6]), load_24_32(ix[7]) };
 * #elif N == 8
 *     (void)load_24_32;
 *     // The gather instruction here doesn't need any particular alignment,
 *     // but the intrinsic takes a const int*.
 *     const int* p4 = bit_pun<const int*>(p);
 *     I32 zero = { 0, 0, 0, 0,  0, 0, 0, 0},
 *         mask = {-1,-1,-1,-1, -1,-1,-1,-1};
 *     #if defined(__clang__)
 *         U32 v = (U32)__builtin_ia32_gatherd_d256(zero, p4, 3*ix, mask, 1);
 *     #elif defined(__GNUC__)
 *         U32 v = (U32)__builtin_ia32_gathersiv8si(zero, p4, 3*ix, mask, 1);
 *     #endif
 * #elif N == 16
 *     (void)load_24_32;
 *     // The intrinsic is supposed to take const void* now, but it takes const int*, just like AVX2.
 *     // And AVX-512 swapped the order of arguments.  :/
 *     const int* p4 = bit_pun<const int*>(p);
 *     U32 v = (U32)_mm512_i32gather_epi32((__m512i)(3*ix), p4, 1);
 * #endif
 *
 *     // Shift off the junk byte, leaving r,g,b in low 24 bits (and zero in the top 8).
 *     return v >> 8;
 * }
 * ```
 */
public fun gather24(p: UByte?, ix: I32): Any {
  TODO("Implement gather24")
}

/**
 * C++ original:
 * ```cpp
 * SI void gather_48(const uint8_t* p, I32 ix, U64* v) {
 *         // As in gather_24(), with everything doubled.
 *         p -= 2;
 *
 *         // Load the i'th 48-bit value from p, and 2 extra bytes.
 *         auto load_48_64 = [p](int i) {
 *             return load<uint64_t>(p + 6*i);
 *         };
 *
 *     #if N == 1
 *         *v = load_48_64(ix);
 *     #elif N == 4
 *         *v = U64{
 *             load_48_64(ix[0]), load_48_64(ix[1]), load_48_64(ix[2]), load_48_64(ix[3]),
 *         };
 *     #elif N == 8 && !defined(USING_AVX2)
 *         *v = U64{
 *             load_48_64(ix[0]), load_48_64(ix[1]), load_48_64(ix[2]), load_48_64(ix[3]),
 *             load_48_64(ix[4]), load_48_64(ix[5]), load_48_64(ix[6]), load_48_64(ix[7]),
 *         };
 *     #elif N == 8
 *         (void)load_48_64;
 *         typedef int32_t   __attribute__((vector_size(16))) Half_I32;
 *         typedef long long __attribute__((vector_size(32))) Half_I64;
 *
 *         // The gather instruction here doesn't need any particular alignment,
 *         // but the intrinsic takes a const long long*.
 *         const long long int* p8 = bit_pun<const long long int*>(p);
 *
 *         Half_I64 zero = { 0, 0, 0, 0},
 *                  mask = {-1,-1,-1,-1};
 *
 *         ix *= 6;
 *         Half_I32 ix_lo = { ix[0], ix[1], ix[2], ix[3] },
 *                  ix_hi = { ix[4], ix[5], ix[6], ix[7] };
 *
 *         #if defined(__clang__)
 *             Half_I64 lo = (Half_I64)__builtin_ia32_gatherd_q256(zero, p8, ix_lo, mask, 1),
 *                      hi = (Half_I64)__builtin_ia32_gatherd_q256(zero, p8, ix_hi, mask, 1);
 *         #elif defined(__GNUC__)
 *             Half_I64 lo = (Half_I64)__builtin_ia32_gathersiv4di(zero, p8, ix_lo, mask, 1),
 *                      hi = (Half_I64)__builtin_ia32_gathersiv4di(zero, p8, ix_hi, mask, 1);
 *         #endif
 *         store((char*)v +  0, lo);
 *         store((char*)v + 32, hi);
 *     #elif N == 16
 *         (void)load_48_64;
 *         const long long int* p8 = bit_pun<const long long int*>(p);
 *         __m512i lo = _mm512_i32gather_epi64(_mm512_extracti32x8_epi32((__m512i)(6*ix), 0), p8, 1),
 *                 hi = _mm512_i32gather_epi64(_mm512_extracti32x8_epi32((__m512i)(6*ix), 1), p8, 1);
 *         store((char*)v +  0, lo);
 *         store((char*)v + 64, hi);
 *     #endif
 *
 *         *v >>= 16;
 *     }
 * ```
 */
public fun gather48(
  p: UByte?,
  ix: I32,
  v: U64?,
): Any {
  TODO("Implement gather48")
}

/**
 * C++ original:
 * ```cpp
 * SI F F_from_U8(U8 v) {
 *     return cast<F>(v) * (1/255.0f);
 * }
 * ```
 */
public fun fFromU8(v: U8): Any {
  TODO("Implement fFromU8")
}

/**
 * C++ original:
 * ```cpp
 * SI F F_from_U16_BE(U16 v) {
 *     // All 16-bit ICC values are big-endian, so we byte swap before converting to float.
 *     // MSVC catches the "loss" of data here in the portable path, so we also make sure to mask.
 *     U16 lo = (v >> 8),
 *         hi = (v << 8) & 0xffff;
 *     return cast<F>(lo|hi) * (1/65535.0f);
 * }
 * ```
 */
public fun fFromU16BE(v: U16): Any {
  TODO("Implement fFromU16BE")
}

/**
 * C++ original:
 * ```cpp
 * SI U16 U16_from_F(F v) {
 *     // 65535 == inf in FP16, so promote to FP32 before converting.
 *     return cast<U16>(cast<V<float>>(v) * 65535 + 0.5f);
 * }
 * ```
 */
public fun <F> u16FromF(v: F): Any {
  TODO("Implement u16FromF")
}

/**
 * C++ original:
 * ```cpp
 * SI F minus_1_ulp(F v) {
 *     return bit_pun<F>( bit_pun<U32>(v) - 1 );
 * }
 * ```
 */
public fun <F> minus1Ulp(v: F): Any {
  TODO("Implement minus1Ulp")
}

/**
 * C++ original:
 * ```cpp
 * SI F table(const skcms_Curve* curve, F v) {
 *     // Clamp the input to [0,1], then scale to a table index.
 *     F ix = max_(F0, min_(v, F1)) * (float)(curve->table_entries - 1);
 *
 *     // We'll look up (equal or adjacent) entries at lo and hi, then lerp by t between the two.
 *     I32 lo = cast<I32>(            ix      ),
 *         hi = cast<I32>(minus_1_ulp(ix+1.0f));
 *     F t = ix - cast<F>(lo);  // i.e. the fractional part of ix.
 *
 *     // TODO: can we load l and h simultaneously?  Each entry in 'h' is either
 *     // the same as in 'l' or adjacent.  We have a rough idea that's it'd always be safe
 *     // to read adjacent entries and perhaps underflow the table by a byte or two
 *     // (it'd be junk, but always safe to read).  Not sure how to lerp yet.
 *     F l,h;
 *     if (curve->table_8) {
 *         l = F_from_U8(gather_8(curve->table_8, lo));
 *         h = F_from_U8(gather_8(curve->table_8, hi));
 *     } else {
 *         l = F_from_U16_BE(gather_16(curve->table_16, lo));
 *         h = F_from_U16_BE(gather_16(curve->table_16, hi));
 *     }
 *     return l + (h-l)*t;
 * }
 * ```
 */
public fun <F> table(curve: SkcmsCurve?, v: F): Any {
  TODO("Implement table")
}

/**
 * C++ original:
 * ```cpp
 * SI void sample_clut_8(const uint8_t* grid_8, I32 ix, F* r, F* g, F* b, F* a) {
 *     // TODO: don't forget to optimize gather_32().
 *     U32 rgba = gather_32(grid_8, ix);
 *
 *     *r = cast<F>((rgba >>  0) & 0xff) * (1/255.0f);
 *     *g = cast<F>((rgba >>  8) & 0xff) * (1/255.0f);
 *     *b = cast<F>((rgba >> 16) & 0xff) * (1/255.0f);
 *     *a = cast<F>((rgba >> 24) & 0xff) * (1/255.0f);
 * }
 * ```
 */
public fun <F> sampleClut8(
  grid8: UByte?,
  ix: I32,
  r: F?,
  g: F?,
  b: F?,
  a: F?,
): Any {
  TODO("Implement sampleClut8")
}

/**
 * C++ original:
 * ```cpp
 * SI void sample_clut_16(const uint8_t* grid_16, I32 ix, F* r, F* g, F* b, F* a) {
 *     // TODO: gather_64()-based fast path?
 *     *r = F_from_U16_BE(gather_16(grid_16, 4*ix+0));
 *     *g = F_from_U16_BE(gather_16(grid_16, 4*ix+1));
 *     *b = F_from_U16_BE(gather_16(grid_16, 4*ix+2));
 *     *a = F_from_U16_BE(gather_16(grid_16, 4*ix+3));
 * }
 * ```
 */
public fun <F> sampleClut16(
  grid16: UByte?,
  ix: I32,
  r: F?,
  g: F?,
  b: F?,
  a: F?,
): Any {
  TODO("Implement sampleClut16")
}

/**
 * C++ original:
 * ```cpp
 * static void clut(const skcms_B2A* b2a, F* r, F* g, F* b, F* a) {
 *     clut(b2a->input_channels, b2a->output_channels,
 *          b2a->grid_points, b2a->grid_8, b2a->grid_16,
 *          r,g,b,a);
 * }
 * ```
 */
public fun <F> clut(
  b2a: SkcmsB2A?,
  r: F?,
  g: F?,
  b: F?,
  a: F?,
) {
  TODO("Implement clut")
}

/**
 * C++ original:
 * ```cpp
 * static void exec_stages(const Op* ops, const void** contexts,
 *                             const char* src, char* dst, int i) {
 *         F r = F0, g = F0, b = F0, a = F1;
 *         while (true) {
 *             switch (*ops++) {
 * #define M(name) case Op::name: Exec_##name(*contexts++, src, dst, r, g, b, a, i); break;
 *                 SKCMS_WORK_OPS(M)
 * #undef M
 * #define M(name) case Op::name: Exec_##name(*contexts++, src, dst, r, g, b, a, i); return;
 *                 SKCMS_STORE_OPS(M)
 * #undef M
 *             }
 *         }
 *     }
 * ```
 */
public fun execStages(
  ops: Op?,
  contexts: Int?,
  src: String?,
  dst: String?,
  i: Int,
) {
  TODO("Implement execStages")
}

/**
 * C++ original:
 * ```cpp
 * void run_program(const Op* program, const void** contexts, SKCMS_MAYBE_UNUSED ptrdiff_t programSize,
 *                  const char* src, char* dst, int n,
 *                 size_t src_bpp, size_t dst_bpp) {
 * #if SKCMS_HAS_MUSTTAIL
 *     // Convert the program into an array of tailcall stages.
 *     StageFn stages[32];
 *     assert(programSize <= ARRAY_COUNT(stages));
 *
 *     static constexpr StageFn kStageFns[] = {
 * #define M(name) &Exec_##name,
 *         SKCMS_WORK_OPS(M)
 *         SKCMS_STORE_OPS(M)
 * #undef M
 *     };
 *
 *     for (ptrdiff_t index = 0; index < programSize; ++index) {
 *         stages[index] = kStageFns[(int)program[index]];
 *     }
 * #else
 *     // Use the op array as-is.
 *     const Op* stages = program;
 * #endif
 *
 *     int i = 0;
 *     while (n >= N) {
 *         exec_stages(stages, contexts, src, dst, i);
 *         i += N;
 *         n -= N;
 *     }
 *     if (n > 0) {
 *         char tmp[4*4*N] = {0};
 *
 *         memcpy(tmp, (const char*)src + (size_t)i*src_bpp, (size_t)n*src_bpp);
 *         exec_stages(stages, contexts, tmp, tmp, 0);
 *         memcpy((char*)dst + (size_t)i*dst_bpp, tmp, (size_t)n*dst_bpp);
 *     }
 * }
 * ```
 */
public fun runProgram(
  param0: Int?,
  param1: Int?,
  param2: Int,
) {
  TODO("Implement runProgram")
}
