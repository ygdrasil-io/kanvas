package org.skia.effects

import kotlin.Boolean
import kotlin.Float
import kotlin.FloatArray
import kotlin.Int
import kotlin.Pair
import kotlin.ULong
import kotlin.Unit
import org.skia.core.Context
import org.skia.core.FilterResult
import org.skia.core.LayerSpace
import org.skia.core.SkPathBuilder
import org.skia.core.SkPathMeasure
import org.skia.core.SkStrokeRec
import org.skia.core.StableKey
import org.skia.core.Vector
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlender
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorChannel
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkPath
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.foundation.U8CPU
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkSize
import org.skia.math.SkVector
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * static void outset_for_stroke(SkRect* rect, const SkStrokeRec& rec) {
 *     SkScalar radius = SkScalarHalf(rec.getWidth());
 *     if (0 == radius) {
 *         radius = SK_Scalar1;    // hairlines
 *     }
 *     if (SkPaint::kMiter_Join == rec.getJoin()) {
 *         radius *= rec.getMiter();
 *     }
 *     rect->outset(radius, radius);
 * }
 * ```
 */
public fun outsetForStroke(rect: SkRect?, rec: SkStrokeRec) {
  TODO("Implement outsetForStroke")
}

/**
 * C++ original:
 * ```cpp
 * static bool morphpoints(SkSpan<SkPoint> dst, SkSpan<const SkPoint> src,
 *                         SkPathMeasure& meas, SkScalar dist) {
 *     SkASSERT(dst.size() >= src.size());
 *     for (size_t i = 0; i < src.size(); i++) {
 *         SkPoint pos;
 *         SkVector tangent;
 *
 *         SkScalar sx = src[i].fX;
 *         SkScalar sy = src[i].fY;
 *
 *         if (!meas.getPosTan(dist + sx, &pos, &tangent)) {
 *             return false;
 *         }
 *
 *         SkMatrix    matrix;
 *         SkPoint     pt;
 *
 *         pt.set(sx, sy);
 *         matrix.setSinCos(tangent.fY, tangent.fX, 0, 0);
 *         matrix.preTranslate(-sx, 0);
 *         matrix.postTranslate(pos.fX, pos.fY);
 *         dst[i] = matrix.mapPoint(pt);
 *     }
 *     return true;
 * }
 * ```
 */
public fun morphpoints(
  dst: SkSpan<SkPoint>,
  src: SkSpan<SkPoint>,
  meas: SkPathMeasure,
  dist: SkScalar,
): Boolean {
  TODO("Implement morphpoints")
}

/**
 * C++ original:
 * ```cpp
 * static void morphpath(SkPathBuilder* dst, const SkPath& src, SkPathMeasure& meas,
 *                       SkScalar dist) {
 *     SkPath::Iter    iter(src, false);
 *     SkPoint         dstP[3], scratch[3];
 *
 *     while (auto rec = iter.next()) {
 *         SkSpan<const SkPoint> srcP = rec->fPoints;
 *         switch (rec->fVerb) {
 *             case SkPathVerb::kMove:
 *                 if (morphpoints(dstP, srcP, meas, dist)) {
 *                     dst->moveTo(dstP[0]);
 *                 }
 *                 break;
 *             case SkPathVerb::kLine:
 *                 scratch[0] = srcP[0];
 *                 scratch[1].set(sk_float_midpoint(srcP[0].fX, srcP[1].fX),
 *                                sk_float_midpoint(srcP[0].fY, srcP[1].fY));
 *                 scratch[2] = srcP[1];
 *                 srcP = scratch; // now we look like a quad
 *                 [[fallthrough]];
 *             case SkPathVerb::kQuad:
 *                 if (morphpoints(dstP, srcP.subspan(1), meas, dist)) {
 *                     dst->quadTo(dstP[0], dstP[1]);
 *                 }
 *                 break;
 *             case SkPathVerb::kConic:
 *                 if (morphpoints(dstP, srcP.subspan(1), meas, dist)) {
 *                     dst->conicTo(dstP[0], dstP[1], rec->conicWeight());
 *                 }
 *                 break;
 *             case SkPathVerb::kCubic:
 *                 if (morphpoints(dstP, srcP.subspan(1), meas, dist)) {
 *                     dst->cubicTo(dstP[0], dstP[1], dstP[2]);
 *                 }
 *                 break;
 *             case SkPathVerb::kClose:
 *                 dst->close();
 *                 break;
 *         }
 *     }
 * }
 * ```
 */
public fun morphpath(
  dst: SkPathBuilder?,
  src: SkPath,
  meas: SkPathMeasure,
  dist: SkScalar,
) {
  TODO("Implement morphpath")
}

/**
 * C++ original:
 * ```cpp
 * static void set_concat(float result[20], const float outer[20], const float inner[20]) {
 *     float    tmp[20];
 *     float*   target;
 *
 *     if (outer == result || inner == result) {
 *         target = tmp;   // will memcpy answer when we're done into result
 *     } else {
 *         target = result;
 *     }
 *
 *     int index = 0;
 *     for (int j = 0; j < 20; j += 5) {
 *         for (int i = 0; i < 4; i++) {
 *             target[index++] =   outer[j + 0] * inner[i + 0] +
 *                                 outer[j + 1] * inner[i + 5] +
 *                                 outer[j + 2] * inner[i + 10] +
 *                                 outer[j + 3] * inner[i + 15];
 *         }
 *         target[index++] =   outer[j + 0] * inner[4] +
 *                             outer[j + 1] * inner[9] +
 *                             outer[j + 2] * inner[14] +
 *                             outer[j + 3] * inner[19] +
 *                             outer[j + 4];
 *     }
 *
 *     if (target != result) {
 *         std::copy_n(target, 20, result);
 *     }
 * }
 * ```
 */
public fun setConcat(
  result: FloatArray,
  outer: FloatArray,
  `inner`: FloatArray,
) {
  TODO("Implement setConcat")
}

/**
 * C++ original:
 * ```cpp
 * static void setrow(float row[], float r, float g, float b) {
 *     row[0] = r;
 *     row[1] = g;
 *     row[2] = b;
 * }
 * ```
 */
public fun setrow(
  row: FloatArray,
  r: Float,
  g: Float,
  b: Float,
) {
  TODO("Implement setrow")
}

/**
 * C++ original:
 * ```cpp
 * static SkScalar byte_to_unit_float(U8CPU byte) {
 *     if (0xFF == byte) {
 *         // want to get this exact
 *         return 1;
 *     } else {
 *         return byte * 0.00392156862745f;
 *     }
 * }
 * ```
 */
public fun byteToUnitFloat(byte: U8CPU): SkScalar {
  TODO("Implement byteToUnitFloat")
}

/**
 * C++ original:
 * ```cpp
 * static bool ComputeStep(const SkPoint& a, const SkPoint& b, SkScalar radius,
 *                         SkPoint* step) {
 *     SkScalar dist = SkPoint::Distance(a, b);
 *
 *     *step = b - a;
 *     if (dist <= radius * 2) {
 *         *step *= SK_ScalarHalf;
 *         return false;
 *     } else {
 *         *step *= radius / dist;
 *         return true;
 *     }
 * }
 * ```
 */
public fun computeStep(
  a: SkPoint,
  b: SkPoint,
  radius: SkScalar,
  step: SkPoint?,
): Boolean {
  TODO("Implement computeStep")
}

/**
 * C++ original:
 * ```cpp
 * static bool cull_line(SkPoint* pts, const SkStrokeRec& rec,
 *                       const SkMatrix& ctm, const SkRect* cullRect,
 *                       const SkScalar intervalLength) {
 *     if (nullptr == cullRect) {
 *         SkASSERT(false); // Shouldn't ever occur in practice
 *         return false;
 *     }
 *
 *     SkScalar dx = pts[1].x() - pts[0].x();
 *     SkScalar dy = pts[1].y() - pts[0].y();
 *
 *     if ((dx && dy) || (!dx && !dy)) {
 *         return false;
 *     }
 *
 *     SkRect bounds = *cullRect;
 *     outset_for_stroke(&bounds, rec);
 *
 *     // cullRect is in device space while pts are in the local coordinate system
 *     // defined by the ctm. We want our answer in the local coordinate system.
 *
 *     SkASSERT(ctm.rectStaysRect());
 *     SkMatrix inv;
 *     if (!ctm.invert(&inv)) {
 *         return false;
 *     }
 *
 *     inv.mapRect(&bounds);
 *
 *     if (dx) {
 *         SkASSERT(dx && !dy);
 *         SkScalar minX = pts[0].fX;
 *         SkScalar maxX = pts[1].fX;
 *
 *         if (dx < 0) {
 *             using std::swap;
 *             swap(minX, maxX);
 *         }
 *
 *         SkASSERT(minX < maxX);
 *         if (maxX <= bounds.fLeft || minX >= bounds.fRight) {
 *             return false;
 *         }
 *
 *         // Now we actually perform the chop, removing the excess to the left and
 *         // right of the bounds (keeping our new line "in phase" with the dash,
 *         // hence the (mod intervalLength).
 *
 *         if (minX < bounds.fLeft) {
 *             minX = bounds.fLeft - SkScalarMod(bounds.fLeft - minX, intervalLength);
 *         }
 *         if (maxX > bounds.fRight) {
 *             maxX = bounds.fRight + SkScalarMod(maxX - bounds.fRight, intervalLength);
 *         }
 *
 *         SkASSERT(maxX > minX);
 *         if (dx < 0) {
 *             using std::swap;
 *             swap(minX, maxX);
 *         }
 *         pts[0].fX = minX;
 *         pts[1].fX = maxX;
 *     } else {
 *         SkASSERT(dy && !dx);
 *         SkScalar minY = pts[0].fY;
 *         SkScalar maxY = pts[1].fY;
 *
 *         if (dy < 0) {
 *             using std::swap;
 *             swap(minY, maxY);
 *         }
 *
 *         SkASSERT(minY < maxY);
 *         if (maxY <= bounds.fTop || minY >= bounds.fBottom) {
 *             return false;
 *         }
 *
 *         // Now we actually perform the chop, removing the excess to the top and
 *         // bottom of the bounds (keeping our new line "in phase" with the dash,
 *         // hence the (mod intervalLength).
 *
 *         if (minY < bounds.fTop) {
 *             minY = bounds.fTop - SkScalarMod(bounds.fTop - minY, intervalLength);
 *         }
 *         if (maxY > bounds.fBottom) {
 *             maxY = bounds.fBottom + SkScalarMod(maxY - bounds.fBottom, intervalLength);
 *         }
 *
 *         SkASSERT(maxY > minY);
 *         if (dy < 0) {
 *             using std::swap;
 *             swap(minY, maxY);
 *         }
 *         pts[0].fY = minY;
 *         pts[1].fY = maxY;
 *     }
 *
 *     return true;
 * }
 * ```
 */
public fun cullLine(
  pts: SkPoint?,
  rec: SkStrokeRec,
  ctm: SkMatrix,
  cullRect: SkRect?,
  intervalLength: SkScalar,
): Boolean {
  TODO("Implement cullLine")
}

/**
 * C++ original:
 * ```cpp
 * static void Perterb(SkPoint* p, const SkVector& tangent, SkScalar scale) {
 *     SkVector normal = tangent;
 *     SkPointPriv::RotateCCW(&normal);
 *     normal.setLength(scale);
 *     *p += normal;
 * }
 * ```
 */
public fun perterb(
  p: SkPoint?,
  tangent: SkVector,
  scale: SkScalar,
) {
  TODO("Implement perterb")
}

/**
 * C++ original:
 * ```cpp
 * static inline int nonzero_to_one(int x) {
 * #if 0
 *     return x != 0;
 * #else
 *     return ((unsigned)(x | -x)) >> 31;
 * #endif
 * }
 * ```
 */
public fun nonzeroToOne(x: Int): Int {
  TODO("Implement nonzeroToOne")
}

/**
 * C++ original:
 * ```cpp
 * static inline int neq_to_one(int x, int max) {
 * #if 0
 *     return x != max;
 * #else
 *     SkASSERT(x >= 0 && x <= max);
 *     return ((unsigned)(x - max)) >> 31;
 * #endif
 * }
 * ```
 */
public fun neqToOne(x: Int, max: Int): Int {
  TODO("Implement neqToOne")
}

/**
 * C++ original:
 * ```cpp
 * static inline int neq_to_mask(int x, int max) {
 * #if 0
 *     return -(x != max);
 * #else
 *     SkASSERT(x >= 0 && x <= max);
 *     return (x - max) >> 31;
 * #endif
 * }
 * ```
 */
public fun neqToMask(x: Int, max: Int): Int {
  TODO("Implement neqToMask")
}

/**
 * C++ original:
 * ```cpp
 * static void rect_memcpy(void* dst, size_t dstRB, const void* src, size_t srcRB,
 *                         size_t copyBytes, int rows) {
 *     for (int i = 0; i < rows; ++i) {
 *         memcpy(dst, src, copyBytes);
 *         dst = (char*)dst + dstRB;
 *         src = (const char*)src + srcRB;
 *     }
 * }
 * ```
 */
public fun rectMemcpy(
  dst: Unit?,
  dstRB: ULong,
  src: Unit?,
  srcRB: ULong,
  copyBytes: ULong,
  rows: Int,
) {
  TODO("Implement rectMemcpy")
}

/**
 * C++ original:
 * ```cpp
 * static size_t add_segments(const SkPath& src, SkScalar start, SkScalar stop, SkPathBuilder* dst,
 *                            bool requires_moveto = true) {
 *     SkASSERT(start < stop);
 *
 *     SkPathMeasure measure(src, false);
 *
 *     SkScalar current_segment_offset = 0;
 *     size_t            contour_count = 1;
 *
 *     do {
 *         const auto next_offset = current_segment_offset + measure.getLength();
 *
 *         if (start < next_offset) {
 *             measure.getSegment(start - current_segment_offset,
 *                                stop  - current_segment_offset,
 *                                dst, requires_moveto);
 *
 *             if (stop <= next_offset)
 *                 break;
 *         }
 *
 *         contour_count++;
 *         current_segment_offset = next_offset;
 *     } while (measure.nextContour());
 *
 *     return contour_count;
 * }
 * ```
 */
public fun addSegments(
  src: SkPath,
  start: SkScalar,
  stop: SkScalar,
  dst: SkPathBuilder?,
  requiresMoveto: Boolean = TODO(),
): ULong {
  TODO("Implement addSegments")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterModeColorFilterFlattenable() {
 *     SK_REGISTER_FLATTENABLE(SkBlendModeColorFilter);
 *     // Previous name
 *     SkFlattenable::Register("SkModeColorFilter", SkBlendModeColorFilter::CreateProc);
 * }
 * ```
 */
public fun skRegisterModeColorFilterFlattenable() {
  TODO("Implement skRegisterModeColorFilterFlattenable")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterComposeColorFilterFlattenable() { SK_REGISTER_FLATTENABLE(SkComposeColorFilter); }
 * ```
 */
public fun skRegisterComposeColorFilterFlattenable() {
  TODO("Implement skRegisterComposeColorFilterFlattenable")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterSkColorSpaceXformColorFilterFlattenable() {
 *     SK_REGISTER_FLATTENABLE(SkColorSpaceXformColorFilter);
 *     // Previous name
 *     SkFlattenable::Register("ColorSpaceXformColorFilter", SkColorSpaceXformColorFilter::CreateProc);
 *     // TODO(ccameron): Remove after grace period for SKPs to stop using old serialization.
 *     SkFlattenable::Register("SkSRGBGammaColorFilter",
 *                             SkColorSpaceXformColorFilter::LegacyGammaOnlyCreateProc);
 * }
 * ```
 */
public fun skRegisterSkColorSpaceXformColorFilterFlattenable() {
  TODO("Implement skRegisterSkColorSpaceXformColorFilterFlattenable")
}

/**
 * C++ original:
 * ```cpp
 * static bool is_alpha_unchanged(const float matrix[20]) {
 *     const float* srcA = matrix + 15;
 *
 *     return SkScalarNearlyZero(srcA[0]) && SkScalarNearlyZero(srcA[1]) &&
 *            SkScalarNearlyZero(srcA[2]) && SkScalarNearlyEqual(srcA[3], 1) &&
 *            SkScalarNearlyZero(srcA[4]);
 * }
 * ```
 */
public fun isAlphaUnchanged(matrix: FloatArray): Boolean {
  TODO("Implement isAlphaUnchanged")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkColorFilter> MakeMatrix(const float array[20],
 *                                        SkMatrixColorFilter::Domain domain,
 *                                        SkColorFilters::Clamp clamp) {
 *     if (!SkIsFinite(array, 20)) {
 *         return nullptr;
 *     }
 *     return sk_make_sp<SkMatrixColorFilter>(array, domain, clamp);
 * }
 * ```
 */
public fun makeMatrix(
  array: FloatArray,
  domain: SkMatrixColorFilter.Domain,
  clamp: SkColorFilters.Clamp,
): SkSp<SkColorFilter> {
  TODO("Implement makeMatrix")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterMatrixColorFilterFlattenable() {
 *     SK_REGISTER_FLATTENABLE(SkMatrixColorFilter);
 *     // Previous name
 *     SkFlattenable::Register("SkColorFilter_Matrix", SkMatrixColorFilter::CreateProc);
 * }
 * ```
 */
public fun skRegisterMatrixColorFilterFlattenable() {
  TODO("Implement skRegisterMatrixColorFilterFlattenable")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterWorkingFormatColorFilterFlattenable() {
 *     SK_REGISTER_FLATTENABLE(SkWorkingFormatColorFilter);
 * }
 * ```
 */
public fun skRegisterWorkingFormatColorFilterFlattenable() {
  TODO("Implement skRegisterWorkingFormatColorFilterFlattenable")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterTableColorFilterFlattenable() {
 *     SK_REGISTER_FLATTENABLE(SkTableColorFilter);
 *     // Previous name
 *     SkFlattenable::Register("SkTable_ColorFilter", SkTableColorFilter::CreateProc);
 * }
 * ```
 */
public fun skRegisterTableColorFilterFlattenable() {
  TODO("Implement skRegisterTableColorFilterFlattenable")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkImageFilter> make_blend(sk_sp<SkBlender> blender,
 *                                 sk_sp<SkImageFilter> background,
 *                                 sk_sp<SkImageFilter> foreground,
 *                                 const SkImageFilters::CropRect& cropRect,
 *                                 std::optional<SkV4> coefficients = {},
 *                                 bool enforcePremul = false) {
 *     if (!blender) {
 *         blender = SkBlender::Mode(SkBlendMode::kSrcOver);
 *     }
 *
 *     auto cropped = [cropRect](sk_sp<SkImageFilter> filter) {
 *         if (cropRect) {
 *             filter = SkImageFilters::Crop(*cropRect, std::move(filter));
 *         }
 *         return filter;
 *     };
 *
 *     if (auto bm = as_BB(blender)->asBlendMode()) {
 *         if (bm == SkBlendMode::kSrc) {
 *             return cropped(std::move(foreground));
 *         } else if (bm == SkBlendMode::kDst) {
 *             return cropped(std::move(background));
 *         } else if (bm == SkBlendMode::kClear) {
 *             return SkImageFilters::Empty();
 *         }
 *     }
 *
 *     sk_sp<SkImageFilter> inputs[2] = { std::move(background), std::move(foreground) };
 *     sk_sp<SkImageFilter> filter{new SkBlendImageFilter(blender, coefficients,
 *                                                        enforcePremul, inputs)};
 *     return cropped(std::move(filter));
 * }
 * ```
 */
public fun makeBlend(
  param0: SkSp<SkBlender>,
  param1: SkSp<SkImageFilter>,
  param2: SkSp<SkImageFilter>,
  param3: SkImageFilters.CropRect,
  param4: Int,
  param5: Boolean,
): SkSp<SkImageFilter> {
  TODO("Implement makeBlend")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterBlendImageFilterFlattenable() {
 *     SK_REGISTER_FLATTENABLE(SkBlendImageFilter);
 *     // TODO (michaelludwig) - Remove after grace period for SKPs to stop using old name
 *     SkFlattenable::Register("SkXfermodeImageFilter_Base", SkBlendImageFilter::CreateProc);
 *     SkFlattenable::Register("SkXfermodeImageFilterImpl", SkBlendImageFilter::CreateProc);
 *     SkFlattenable::Register("ArithmeticImageFilterImpl",
 *                             SkBlendImageFilter::LegacyArithmeticCreateProc);
 *     SkFlattenable::Register("SkArithmeticImageFilter",
 *                             SkBlendImageFilter::LegacyArithmeticCreateProc);
 * }
 * ```
 */
public fun skRegisterBlendImageFilterFlattenable() {
  TODO("Implement skRegisterBlendImageFilterFlattenable")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterBlurImageFilterFlattenable() {
 *     SK_REGISTER_FLATTENABLE(SkBlurImageFilter);
 *     SkFlattenable::Register("SkBlurImageFilterImpl", SkBlurImageFilter::CreateProc);
 * }
 * ```
 */
public fun skRegisterBlurImageFilterFlattenable() {
  TODO("Implement skRegisterBlurImageFilterFlattenable")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterColorFilterImageFilterFlattenable() {
 *     SK_REGISTER_FLATTENABLE(SkColorFilterImageFilter);
 *     // TODO (michaelludwig) - Remove after grace period for SKPs to stop using old name
 *     SkFlattenable::Register("SkColorFilterImageFilterImpl", SkColorFilterImageFilter::CreateProc);
 * }
 * ```
 */
public fun skRegisterColorFilterImageFilterFlattenable() {
  TODO("Implement skRegisterColorFilterImageFilterFlattenable")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterComposeImageFilterFlattenable() {
 *     SK_REGISTER_FLATTENABLE(SkComposeImageFilter);
 *     // TODO (michaelludwig) - Remove after grace period for SKPs to stop using old name
 *     SkFlattenable::Register("SkComposeImageFilterImpl", SkComposeImageFilter::CreateProc);
 * }
 * ```
 */
public fun skRegisterComposeImageFilterFlattenable() {
  TODO("Implement skRegisterComposeImageFilterFlattenable")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterCropImageFilterFlattenable() {
 *     SK_REGISTER_FLATTENABLE(SkCropImageFilter);
 *     // TODO (michaelludwig) - Remove after grace period for SKPs to stop using old name
 *     SkFlattenable::Register("SkTileImageFilter", SkCropImageFilter::LegacyTileCreateProc);
 *     SkFlattenable::Register("SkTileImageFilterImpl", SkCropImageFilter::LegacyTileCreateProc);
 * }
 * ```
 */
public fun skRegisterCropImageFilterFlattenable() {
  TODO("Implement skRegisterCropImageFilterFlattenable")
}

/**
 * C++ original:
 * ```cpp
 * bool channel_selector_type_is_valid(SkColorChannel cst) {
 *     switch (cst) {
 *         case SkColorChannel::kR:
 *         case SkColorChannel::kG:
 *         case SkColorChannel::kB:
 *         case SkColorChannel::kA:
 *             return true;
 *         default:
 *             break;
 *     }
 *     return false;
 * }
 * ```
 */
public fun channelSelectorTypeIsValid(cst: SkColorChannel): Boolean {
  TODO("Implement channelSelectorTypeIsValid")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkShader> make_displacement_shader(
 *         sk_sp<SkShader> displacement,
 *         sk_sp<SkShader> color,
 *         skif::LayerSpace<skif::Vector> scale,
 *         SkColorChannel xChannel,
 *         SkColorChannel yChannel) {
 *     if (!color) {
 *         // Color is fully transparent, so no point in displacing it
 *         return nullptr;
 *     }
 *     if (!displacement) {
 *         // Somehow we had a valid displacement image but failed to produce a shader
 *         // (e.g. an internal resolve to a new image failed). Treat the displacement as
 *         // transparent, but it's too late to switch to the applyTransform() optimization.
 *         displacement = SkShaders::Color(SK_ColorTRANSPARENT);
 *     }
 *
 *     const SkRuntimeEffect* displacementEffect =
 *             GetKnownRuntimeEffect(SkKnownRuntimeEffects::StableKey::kDisplacement);
 *
 *     auto channelSelector = [](SkColorChannel c) {
 *         return SkV4{c == SkColorChannel::kR ? 1.f : 0.f,
 *                     c == SkColorChannel::kG ? 1.f : 0.f,
 *                     c == SkColorChannel::kB ? 1.f : 0.f,
 *                     c == SkColorChannel::kA ? 1.f : 0.f};
 *     };
 *
 *     SkRuntimeShaderBuilder builder(sk_ref_sp(displacementEffect));
 *     builder.child("displMap") = std::move(displacement);
 *     builder.child("colorMap") = std::move(color);
 *     builder.uniform("scale") = SkV2{scale.x(), scale.y()};
 *     builder.uniform("xSelect") = channelSelector(xChannel);
 *     builder.uniform("ySelect") = channelSelector(yChannel);
 *
 *     return builder.makeShader();
 * }
 * ```
 */
public fun makeDisplacementShader(
  displacement: SkSp<SkShader>,
  color: SkSp<SkShader>,
  scale: LayerSpace<Vector>,
  xChannel: SkColorChannel,
  yChannel: SkColorChannel,
): SkSp<SkShader> {
  TODO("Implement makeDisplacementShader")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterDisplacementMapImageFilterFlattenable() {
 *     SK_REGISTER_FLATTENABLE(SkDisplacementMapImageFilter);
 *     // TODO (michaelludwig) - Remove after grace period for SKPs to stop using old name
 *     SkFlattenable::Register("SkDisplacementMapEffect", SkDisplacementMapImageFilter::CreateProc);
 *     SkFlattenable::Register("SkDisplacementMapEffectImpl",
 *                             SkDisplacementMapImageFilter::CreateProc);
 * }
 * ```
 */
public fun skRegisterDisplacementMapImageFilterFlattenable() {
  TODO("Implement skRegisterDisplacementMapImageFilterFlattenable")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkImageFilter> make_drop_shadow_graph(SkVector offset,
 *                                                    SkSize sigma,
 *                                                    SkColor4f color,
 *                                                    sk_sp<SkColorSpace> colorSpace,
 *                                                    bool shadowOnly,
 *                                                    sk_sp<SkImageFilter> input,
 *                                                    const std::optional<SkRect>& crop) {
 *     // A drop shadow blurs the input, filters it to be the solid color + blurred
 *     // alpha, and then offsets it. If it's not shadow-only, the input is then
 *     // src-over blended on top. Finally it's cropped to the optional 'crop'.
 *     sk_sp<SkImageFilter> filter = input;
 *     filter = SkImageFilters::Blur(sigma.fWidth, sigma.fHeight, std::move(filter));
 *     filter = SkImageFilters::ColorFilter(
 *             SkColorFilters::Blend(color, std::move(colorSpace), SkBlendMode::kSrcIn),
 *             std::move(filter));
 *     // TODO: Offset should take SkSamplingOptions too, but kLinear filtering is needed to hide
 *     // nearest-neighbor sampling artifacts from fractional offsets applied post-blur.
 *     filter = SkImageFilters::MatrixTransform(SkMatrix::Translate(offset.fX, offset.fY),
 *                                              SkFilterMode::kLinear,
 *                                              std::move(filter));
 *     if (!shadowOnly) {
 * #if defined(SK_LEGACY_BLEND_FOR_DROP_SHADOWS)
 *         filter = SkImageFilters::Blend(
 *                 SkBlendMode::kSrcOver, std::move(filter), std::move(input));
 * #else
 *         // Merge is visually equivalent to Blend(kSrcOver) but draws each child independently,
 *         // whereas Blend() fills the union of the child bounds with a single shader evaluation.
 *         // Since we know the original and the offset blur will have somewhat disjoint bounds, a
 *         // Blend() shader would force evaluating tile edge conditions for each, while merge lets us
 *         // avoid that.
 *         filter = SkImageFilters::Merge(std::move(filter), std::move(input));
 * #endif
 *     }
 *     if (crop) {
 *         filter = SkImageFilters::Crop(*crop, std::move(filter));
 *     }
 *     return filter;
 * }
 * ```
 */
public fun makeDropShadowGraph(
  offset: SkVector,
  sigma: SkSize,
  color: SkColor4f,
  colorSpace: SkSp<SkColorSpace>,
  shadowOnly: Boolean,
  input: SkSp<SkImageFilter>,
  crop: SkRect?,
): SkSp<SkImageFilter> {
  TODO("Implement makeDropShadowGraph")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkFlattenable> legacy_drop_shadow_create_proc(SkReadBuffer& buffer) {
 *     if (!buffer.isVersionLT(SkPicturePriv::Version::kDropShadowImageFilterComposition)) {
 *         // SKPs created with this version or newer just serialize the image filter composition that
 *         // is equivalent to a drop-shadow, instead of a single dedicated flattenable for the effect.
 *         return nullptr;
 *     }
 *
 *     auto [child, cropRect] = SkImageFilter_Base::Unflatten(buffer);
 *
 *     SkScalar dx = buffer.readScalar();
 *     SkScalar dy = buffer.readScalar();
 *     SkScalar sigmaX = buffer.readScalar();
 *     SkScalar sigmaY = buffer.readScalar();
 *     SkColor4f color = SkColor4f::FromColor(buffer.readColor());
 *
 *     // For backwards compatibility, the shadow mode had been saved as an enum cast to a 32LE int,
 *     // where shadow-and-foreground was 0 and shadow-only was 1. Other than the number of bits, this
 *     // is equivalent to the bool that SkDropShadowImageFilter now uses.
 *     bool shadowOnly = SkToBool(buffer.read32LE(1));
 *     return make_drop_shadow_graph({dx, dy}, {sigmaX, sigmaY}, color, /*colorSpace=*/nullptr,
 *                                   shadowOnly, std::move(child), cropRect);
 * }
 * ```
 */
public fun legacyDropShadowCreateProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
  TODO("Implement legacyDropShadowCreateProc")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterLegacyDropShadowImageFilterFlattenable() {
 *     SkFlattenable::Register("SkDropShadowImageFilter", legacy_drop_shadow_create_proc);
 *     SkFlattenable::Register("SkDropShadowImageFilterImpl", legacy_drop_shadow_create_proc);
 * }
 * ```
 */
public fun skRegisterLegacyDropShadowImageFilterFlattenable() {
  TODO("Implement skRegisterLegacyDropShadowImageFilterFlattenable")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterImageImageFilterFlattenable() {
 *     SK_REGISTER_FLATTENABLE(SkImageImageFilter);
 *     // TODO (michaelludwig) - Remove after grace period for SKPs to stop using old name
 *     SkFlattenable::Register("SkImageSourceImpl", SkImageImageFilter::CreateProc);
 * }
 * ```
 */
public fun skRegisterImageImageFilterFlattenable() {
  TODO("Implement skRegisterImageImageFilterFlattenable")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkShader> make_normal_shader(sk_sp<SkShader> alphaMap,
 *                                    const skif::LayerSpace<SkIRect>& edgeBounds,
 *                                    skif::LayerSpace<ZValue> surfaceDepth) {
 *     const SkRuntimeEffect* normalEffect =
 *             GetKnownRuntimeEffect(SkKnownRuntimeEffects::StableKey::kNormal);
 *
 *     SkRuntimeShaderBuilder builder(sk_ref_sp(normalEffect));
 *     builder.child("alphaMap") = std::move(alphaMap);
 *     builder.uniform("edgeBounds") = SkRect::Make(SkIRect(edgeBounds)).makeInset(0.5f, 0.5f);
 *     builder.uniform("negSurfaceDepth") = -surfaceDepth.val();
 *
 *     return builder.makeShader();
 * }
 * ```
 */
public fun makeNormalShader(
  alphaMap: SkSp<SkShader>,
  edgeBounds: LayerSpace<SkIRect>,
  surfaceDepth: LayerSpace<ZValue>,
): SkSp<SkShader> {
  TODO("Implement makeNormalShader")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkShader> make_lighting_shader(sk_sp<SkShader> normalMap,
 *                                      Light::Type lightType,
 *                                      SkColor lightColor,
 *                                      skif::LayerSpace<SkPoint> locationXY,
 *                                      skif::LayerSpace<ZValue> locationZ,
 *                                      skif::LayerSpace<skif::Vector> directionXY,
 *                                      skif::LayerSpace<ZValue> directionZ,
 *                                      float falloffExponent,
 *                                      float cosCutoffAngle,
 *                                      Material::Type matType,
 *                                      skif::LayerSpace<ZValue> surfaceDepth,
 *                                      float k,
 *                                      float shininess) {
 *
 *     const SkRuntimeEffect* lightingEffect =
 *             GetKnownRuntimeEffect(SkKnownRuntimeEffects::StableKey::kLighting);
 *
 *     SkRuntimeShaderBuilder builder(sk_ref_sp(lightingEffect));
 *     builder.child("normalMap") = std::move(normalMap);
 *
 *     builder.uniform("materialAndLightType") =
 *             SkV4{surfaceDepth.val(),
 *                  shininess,
 *                  static_cast<float>(matType),
 *                  lightType == Light::Type::kPoint ?
 *                          0.f : (lightType == Light::Type::kDistant ? -1.f : 1.f)};
 *     builder.uniform("lightPosAndSpotFalloff") =
 *             SkV4{locationXY.x(), locationXY.y(), locationZ.val(), falloffExponent};
 *
 *     // Pre-normalize the light direction, but this can be (0,0,0) for point lights, which won't use
 *     // the uniform anyways. Avoid a division by 0 to keep ASAN happy or in the event that a spot/dir
 *     // light have bad user input.
 *     SkV3 dir{directionXY.x(), directionXY.y(), directionZ.val()};
 *     float invDirLen = dir.length();
 *     invDirLen = invDirLen ? 1.0f / invDirLen : 0.f;
 *     builder.uniform("lightDirAndSpotCutoff") =
 *             SkV4{invDirLen*dir.x, invDirLen*dir.y, invDirLen*dir.z, cosCutoffAngle};
 *
 *     // Historically, the Skia lighting image filter did not apply any color space transformation to
 *     // the light's color. The SVG spec for the lighting effects does not stipulate how to interpret
 *     // the color for a light. Overall, it does not have a principled physically based approach, but
 *     // the closest way to interpret it, is:
 *     //  - the material's K is a uniformly distributed reflectance coefficient
 *     //  - lighting *should* be calculated in a linear color space, which is the default for SVG
 *     //    filters. Chromium manages these color transformations using SkImageFilters::ColorFilter
 *     //    so it's not necessarily reflected in the Context's color space.
 *     //  - it's unspecified in the SVG spec if the light color should be transformed to linear or
 *     //    interpreted as linear already. Regardless, if there was any transformation that needed to
 *     //    occur, Blink took care of it in the past so adding color space management to the light
 *     //    color would be a breaking change.
 *     //  - so for now, leave the color un-modified and apply K up front since no color space
 *     //    transforms need to be performed on the original light color.
 *     const float colorScale = k / 255.f;
 *     builder.uniform("lightColor") = SkV3{SkColorGetR(lightColor) * colorScale,
 *                                          SkColorGetG(lightColor) * colorScale,
 *                                          SkColorGetB(lightColor) * colorScale};
 *
 *     return builder.makeShader();
 * }
 * ```
 */
public fun makeLightingShader(
  normalMap: SkSp<SkShader>,
  lightType: Light.Type,
  lightColor: SkColor,
  locationXY: LayerSpace<SkPoint>,
  locationZ: LayerSpace<ZValue>,
  directionXY: LayerSpace<Vector>,
  directionZ: LayerSpace<ZValue>,
  falloffExponent: Float,
  cosCutoffAngle: Float,
  matType: Material.Type,
  surfaceDepth: LayerSpace<ZValue>,
  k: Float,
  shininess: Float,
): SkSp<SkShader> {
  TODO("Implement makeLightingShader")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkImageFilter> make_lighting(const Light& light,
 *                                    const Material& material,
 *                                    sk_sp<SkImageFilter> input,
 *                                    const SkImageFilters::CropRect& cropRect) {
 *     // According to the spec, ks and kd can be any non-negative number:
 *     // http://www.w3.org/TR/SVG/filters.html#feSpecularLightingElement
 *     if (!SkIsFinite(material.fK, material.fShininess, ZValue(material.fSurfaceDepth)) ||
 *         material.fK < 0.f) {
 *         return nullptr;
 *     }
 *
 *     // Ensure light values are finite, and the cosine should be between -1 and 1
 *     if (!SkPoint(light.fLocationXY).isFinite() || !skif::Vector(light.fDirectionXY).isFinite() ||
 *         !SkIsFinite(light.fFalloffExponent, light.fCosCutoffAngle,
 *                     ZValue(light.fLocationZ), ZValue(light.fDirectionZ)) ||
 *         light.fCosCutoffAngle < -1.f || light.fCosCutoffAngle > 1.f) {
 *         return nullptr;
 *     }
 *
 *     // If a crop rect is provided, it clamps both the input (to better match the SVG's normal
 *     // boundary condition spec) and the output (because otherwise it has infinite bounds).
 *     sk_sp<SkImageFilter> filter = std::move(input);
 *     if (cropRect) {
 *         filter = SkImageFilters::Crop(*cropRect, std::move(filter));
 *     }
 *     filter = sk_sp<SkImageFilter>(
 *             new SkLightingImageFilter(light, material, std::move(filter)));
 *     if (cropRect) {
 *         filter = SkImageFilters::Crop(*cropRect, std::move(filter));
 *     }
 *     return filter;
 * }
 * ```
 */
public fun makeLighting(
  light: Light,
  material: Material,
  input: SkSp<SkImageFilter>,
  cropRect: SkImageFilters.CropRect,
): SkSp<SkImageFilter> {
  TODO("Implement makeLighting")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterLightingImageFilterFlattenables() {
 *     SK_REGISTER_FLATTENABLE(SkLightingImageFilter);
 *     // TODO (michaelludwig): Remove after grace period for SKPs to stop using old name
 *     SkFlattenable::Register("SkDiffuseLightingImageFilter",
 *                             SkLightingImageFilter::LegacyDiffuseCreateProc);
 *     SkFlattenable::Register("SkSpecularLightingImageFilter",
 *                             SkLightingImageFilter::LegacySpecularCreateProc);
 * }
 * ```
 */
public fun skRegisterLightingImageFilterFlattenables() {
  TODO("Implement skRegisterLightingImageFilterFlattenables")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterMagnifierImageFilterFlattenable() {
 *     SK_REGISTER_FLATTENABLE(SkMagnifierImageFilter);
 * }
 * ```
 */
public fun skRegisterMagnifierImageFilterFlattenable() {
  TODO("Implement skRegisterMagnifierImageFilterFlattenable")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkShader> make_magnifier_shader(
 *         sk_sp<SkShader> input,
 *         const skif::LayerSpace<SkRect>& lensBounds,
 *         const skif::LayerSpace<SkMatrix>& zoomXform,
 *         const skif::LayerSpace<SkSize>& inset) {
 *     const SkRuntimeEffect* magEffect =
 *             GetKnownRuntimeEffect(SkKnownRuntimeEffects::StableKey::kMagnifier);
 *
 *     SkRuntimeShaderBuilder builder(sk_ref_sp(magEffect));
 *     builder.child("src") = std::move(input);
 *
 *     SkASSERT(inset.width() > 0.f && inset.height() > 0.f);
 *     builder.uniform("lensBounds") = SkRect(lensBounds);
 *     builder.uniform("zoomXform") = SkV4{/*Tx*/zoomXform.rc(0, 2), /*Ty*/zoomXform.rc(1, 2),
 *                                         /*Sx*/zoomXform.rc(0, 0), /*Sy*/zoomXform.rc(1, 1)};
 *     builder.uniform("invInset") = SkV2{1.f / inset.width(),
 *                                        1.f / inset.height()};
 *
 *     return builder.makeShader();
 * }
 * ```
 */
public fun makeMagnifierShader(
  input: SkSp<SkShader>,
  lensBounds: LayerSpace<SkRect>,
  zoomXform: LayerSpace<SkMatrix>,
  inset: LayerSpace<SkSize>,
): SkSp<SkShader> {
  TODO("Implement makeMagnifierShader")
}

/**
 * C++ original:
 * ```cpp
 * skif::LayerSpace<SkIRect> adjust(const skif::LayerSpace<SkIRect>& rect,
 *                                  int dl, int dt, int dr, int db) {
 *     SkIRect adjusted = SkIRect(rect);
 *     adjusted.adjust(dl, dt, dr, db);
 *     return skif::LayerSpace<SkIRect>(adjusted);
 * }
 * ```
 */
public fun adjust(
  rect: LayerSpace<SkIRect>,
  dl: Int,
  dt: Int,
  dr: Int,
  db: Int,
): LayerSpace<SkIRect> {
  TODO("Implement adjust")
}

/**
 * C++ original:
 * ```cpp
 * std::pair<int, SkKnownRuntimeEffects::StableKey> quantize_by_kernel_size(int kernelSize) {
 *     if (kernelSize < kMaxUniformKernelSize) {
 *         return { kMaxUniformKernelSize, SkKnownRuntimeEffects::StableKey::kMatrixConvUniforms };
 *     } else if (kernelSize <= kSmallKernelSize) {
 *         return { kSmallKernelSize, SkKnownRuntimeEffects::StableKey::kMatrixConvTexSm };
 *     }
 *
 *     return { kLargeKernelSize, SkKnownRuntimeEffects::StableKey::kMatrixConvTexLg };
 * }
 * ```
 */
public fun quantizeByKernelSize(kernelSize: Int): Pair<Int, StableKey> {
  TODO("Implement quantizeByKernelSize")
}

/**
 * C++ original:
 * ```cpp
 * SkBitmap create_kernel_bitmap(const SkISize& kernelSize, const float* kernel,
 *                               float* innerGain, float* innerBias) {
 *     int length = kernelSize.fWidth * kernelSize.fHeight;
 *     auto [quantizedKernelSize, key] = quantize_by_kernel_size(length);
 *     if (key == SkKnownRuntimeEffects::StableKey::kMatrixConvUniforms) {
 *         // No bitmap is needed to store the kernel on the GPU
 *         *innerGain = 1.f;
 *         *innerBias = 0.f;
 *         return {};
 *     }
 *
 *
 *     // The convolution kernel is "big". The SVG spec has no upper limit on what's supported so
 *     // store the kernel in a SkBitmap that will be uploaded to a data texture. We could
 *     // implement a more straight forward evaluation loop for the CPU backend, but kernels of
 *     // this size are already going to be very slow so we accept the extra indirection to
 *     // keep the code paths consolidated.
 *     //
 *     // We store the data in A8 for universal support, but this requires normalizing the values
 *     // and adding an extra inner bias operation to the shader. We could store values in A16 or
 *     // A32 for improved accuracy but that would require querying GPU capabilities, which
 *     // prevents creating the bitmap once during initialization. Even on the GPU, kernels larger
 *     // than 5x5 quickly exceed realtime capabilities, so the loss of precision isn't a great
 *     // concern either.
 *     float min = kernel[0];
 *     float max = kernel[0];
 *     for (int i = 1; i < length; ++i) {
 *         if (kernel[i] < min) {
 *             min = kernel[i];
 *         }
 *         if (kernel[i] > max) {
 *             max = kernel[i];
 *         }
 *     }
 *
 *     *innerGain = max - min;
 *     *innerBias = min;
 *     // Treat a near-0 gain (i.e. box blur) as 1 and let innerBias move everything to final value.
 *     if (SkScalarNearlyZero(*innerGain)) {
 *         *innerGain = 1.f;
 *     }
 *
 *     SkBitmap kernelBM;
 *     if (!kernelBM.tryAllocPixels(SkImageInfo::Make({ quantizedKernelSize, 1 },
 *                                                    kAlpha_8_SkColorType,
 *                                                    kPremul_SkAlphaType))) {
 *         // OOM so return an empty bitmap, which will be detected later on in onFilterImage().
 *         return {};
 *     }
 *
 *     for (int i = 0; i < length; ++i) {
 *         *kernelBM.getAddr8(i, 0) = SkScalarRoundToInt(255 * (kernel[i] - min) / *innerGain);
 *     }
 *     for (int i = length; i < quantizedKernelSize; ++i) {
 *         *kernelBM.getAddr8(i, 0) = 0;
 *     }
 *
 *     kernelBM.setImmutable();
 *     return kernelBM;
 * }
 * ```
 */
public fun createKernelBitmap(
  kernelSize: SkISize,
  kernel: Float?,
  innerGain: Float?,
  innerBias: Float?,
): SkBitmap {
  TODO("Implement createKernelBitmap")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterMatrixConvolutionImageFilterFlattenable() {
 *     SK_REGISTER_FLATTENABLE(SkMatrixConvolutionImageFilter);
 *     // TODO (michaelludwig) - Remove after grace period for SKPs to stop using old name
 *     SkFlattenable::Register("SkMatrixConvolutionImageFilterImpl",
 *                             SkMatrixConvolutionImageFilter::CreateProc);
 * }
 * ```
 */
public fun skRegisterMatrixConvolutionImageFilterFlattenable() {
  TODO("Implement skRegisterMatrixConvolutionImageFilterFlattenable")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterMatrixTransformImageFilterFlattenable() {
 *     SK_REGISTER_FLATTENABLE(SkMatrixTransformImageFilter);
 *     // TODO(michaelludwig): Remove after grace period for SKPs to stop using old name
 *     SkFlattenable::Register("SkMatrixImageFilter", SkMatrixTransformImageFilter::CreateProc);
 *     // TODO(michaelludwig): Remove after grace period for SKPs to stop using old serialization
 *     SkFlattenable::Register("SkOffsetImageFilter",
 *                             SkMatrixTransformImageFilter::LegacyOffsetCreateProc);
 *     SkFlattenable::Register("SkOffsetImageFilterImpl",
 *                             SkMatrixTransformImageFilter::LegacyOffsetCreateProc);
 * }
 * ```
 */
public fun skRegisterMatrixTransformImageFilterFlattenable() {
  TODO("Implement skRegisterMatrixTransformImageFilterFlattenable")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterMergeImageFilterFlattenable() {
 *     SK_REGISTER_FLATTENABLE(SkMergeImageFilter);
 *     // TODO (michaelludwig) - Remove after grace period for SKPs to stop using old name
 *     SkFlattenable::Register("SkMergeImageFilterImpl", SkMergeImageFilter::CreateProc);
 * }
 * ```
 */
public fun skRegisterMergeImageFilterFlattenable() {
  TODO("Implement skRegisterMergeImageFilterFlattenable")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkImageFilter> make_morphology(MorphType type,
 *                                      SkSize radii,
 *                                      sk_sp<SkImageFilter> input,
 *                                      const SkImageFilters::CropRect& cropRect) {
 *     if (radii.width() < 0.f || radii.height() < 0.f) {
 *         return nullptr; // invalid
 *     }
 *     sk_sp<SkImageFilter> filter = std::move(input);
 *     if (radii.width() > 0.f || radii.height() > 0.f) {
 *         filter = sk_sp<SkImageFilter>(new SkMorphologyImageFilter(type, radii, std::move(filter)));
 *     }
 *     // otherwise both radii are 0, so the kernel is always the identity function, in which case
 *     // we just need to apply the 'cropRect' to the 'input'.
 *
 *     if (cropRect) {
 *         filter = SkImageFilters::Crop(*cropRect, std::move(filter));
 *     }
 *     return filter;
 * }
 * ```
 */
public fun makeMorphology(
  type: MorphType,
  radii: SkSize,
  input: SkSp<SkImageFilter>,
  cropRect: SkImageFilters.CropRect,
): SkSp<SkImageFilter> {
  TODO("Implement makeMorphology")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkShader> make_linear_morphology(sk_sp<SkShader> input,
 *                                        MorphType type,
 *                                        MorphDirection direction,
 *                                        int radius) {
 *     SkASSERT(radius <= kMaxLinearRadius);
 *
 *     const SkRuntimeEffect* linearMorphologyEffect =
 *             GetKnownRuntimeEffect(SkKnownRuntimeEffects::StableKey::kLinearMorphology);
 *
 *     SkRuntimeShaderBuilder builder(sk_ref_sp(linearMorphologyEffect));
 *     builder.child("child") = std::move(input);
 *     builder.uniform("offset") = direction == MorphDirection::kX ? SkV2{1.f, 0.f} : SkV2{0.f, 1.f};
 *     builder.uniform("flip") = (type == MorphType::kDilate) ? 1.f : -1.f;
 *     builder.uniform("radius") = (int32_t)radius;
 *
 *     return builder.makeShader();
 * }
 * ```
 */
public fun makeLinearMorphology(
  input: SkSp<SkShader>,
  type: MorphType,
  direction: MorphDirection,
  radius: Int,
): SkSp<SkShader> {
  TODO("Implement makeLinearMorphology")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkShader> make_sparse_morphology(sk_sp<SkShader> input,
 *                                        MorphType type,
 *                                        MorphDirection direction,
 *                                        int radius) {
 *
 *     const SkRuntimeEffect* sparseMorphologyEffect =
 *             GetKnownRuntimeEffect(SkKnownRuntimeEffects::StableKey::kSparseMorphology);
 *
 *     SkRuntimeShaderBuilder builder(sk_ref_sp(sparseMorphologyEffect));
 *     builder.child("child") = std::move(input);
 *     builder.uniform("offset") = direction == MorphDirection::kX ? SkV2{(float)radius, 0.f}
 *                                                                 : SkV2{0.f, (float)radius};
 *     builder.uniform("flip") = (type == MorphType::kDilate) ? 1.f : -1.f;
 *
 *     return builder.makeShader();
 * }
 * ```
 */
public fun makeSparseMorphology(
  input: SkSp<SkShader>,
  type: MorphType,
  direction: MorphDirection,
  radius: Int,
): SkSp<SkShader> {
  TODO("Implement makeSparseMorphology")
}

/**
 * C++ original:
 * ```cpp
 * skif::FilterResult morphology_pass(const skif::Context& ctx, const skif::FilterResult& input,
 *                                    MorphType type, MorphDirection dir, int radius) {
 *     using ShaderFlags = skif::FilterResult::ShaderFlags;
 *
 *     auto axisDelta = [dir](int step) {
 *         return skif::LayerSpace<SkISize>({
 *                 dir == MorphDirection::kX ? step : 0,
 *                 dir == MorphDirection::kY ? step : 0});
 *     };
 *
 *     // The first iteration will sample a full kernel outset from the final output.
 *     skif::LayerSpace<SkIRect> sampleBounds = ctx.desiredOutput();
 *     sampleBounds.outset(axisDelta(radius));
 *
 *     skif::FilterResult childOutput = input;
 *     int appliedRadius = 0;
 *     while (radius > appliedRadius) {
 *         if (!childOutput) {
 *             return {}; // Eroded or dilated transparent black is still transparent black
 *         }
 *
 *         // The first iteration uses up to kMaxLinearRadius with a linear accumulation pass.
 *         // After that we double the radius each step until we can finish with the target radius.
 *         int stepRadius =
 *                 appliedRadius == 0 ? std::min(kMaxLinearRadius, radius)
 *                                    : std::min(radius - appliedRadius, appliedRadius);
 *
 *         skif::Context stepCtx = ctx;
 *         if (appliedRadius + stepRadius < radius) {
 *             // Intermediate steps need to output what will be sampled on the next iteration
 *             auto outputBounds = sampleBounds;
 *             outputBounds.inset(axisDelta(stepRadius));
 *             stepCtx = ctx.withNewDesiredOutput(outputBounds);
 *         } // else the last iteration should output what was originally requested
 *
 *         skif::FilterResult::Builder builder{stepCtx};
 *         builder.add(childOutput, sampleBounds, ShaderFlags::kSampledRepeatedly);
 *         childOutput = builder.eval(
 *                 [&](SkSpan<sk_sp<SkShader>> inputs) {
 *                     if (appliedRadius == 0) {
 *                         return make_linear_morphology(inputs[0], type, dir, stepRadius);
 *                     } else {
 *                         return make_sparse_morphology(inputs[0], type, dir, stepRadius);
 *                     }
 *                 });
 *
 *         sampleBounds = stepCtx.desiredOutput();
 *         appliedRadius += stepRadius;
 *         SkASSERT(appliedRadius <= radius); // Our last iteration should hit 'radius' exactly.
 *     }
 *
 *     return childOutput;
 * }
 * ```
 */
public fun morphologyPass(
  ctx: Context,
  input: FilterResult,
  type: MorphType,
  dir: MorphDirection,
  radius: Int,
): FilterResult {
  TODO("Implement morphologyPass")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterMorphologyImageFilterFlattenables() {
 *     SK_REGISTER_FLATTENABLE(SkMorphologyImageFilter);
 *     // TODO (michaelludwig): Remove after grace period for SKPs to stop using old name
 *     SkFlattenable::Register("SkMorphologyImageFilterImpl", SkMorphologyImageFilter::CreateProc);
 * }
 * ```
 */
public fun skRegisterMorphologyImageFilterFlattenables() {
  TODO("Implement skRegisterMorphologyImageFilterFlattenables")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterPictureImageFilterFlattenable() {
 *     SK_REGISTER_FLATTENABLE(SkPictureImageFilter);
 *     // TODO (michaelludwig) - Remove after grace period for SKPs to stop using old name
 *     SkFlattenable::Register("SkPictureImageFilterImpl", SkPictureImageFilter::CreateProc);
 * }
 * ```
 */
public fun skRegisterPictureImageFilterFlattenable() {
  TODO("Implement skRegisterPictureImageFilterFlattenable")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterShaderImageFilterFlattenable() {
 *     SK_REGISTER_FLATTENABLE(SkShaderImageFilter);
 *     // TODO (michaelludwig) - Remove after grace period for SKPs to stop using old name
 *     SkFlattenable::Register("SkPaintImageFilter", SkShaderImageFilter::CreateProc);
 *     SkFlattenable::Register("SkPaintImageFilterImpl", SkShaderImageFilter::CreateProc);
 * }
 * ```
 */
public fun skRegisterShaderImageFilterFlattenable() {
  TODO("Implement skRegisterShaderImageFilterFlattenable")
}

/**
 * C++ original:
 * ```cpp
 * void SkRegisterRuntimeImageFilterFlattenable() {
 *     SK_REGISTER_FLATTENABLE(SkRuntimeImageFilter);
 * }
 * ```
 */
public fun skRegisterRuntimeImageFilterFlattenable() {
  TODO("Implement skRegisterRuntimeImageFilterFlattenable")
}

/**
 * C++ original:
 * ```cpp
 * static inline const SkColorFilterBase* as_CFB(const sk_sp<SkColorFilter>& filter) {
 *     return static_cast<SkColorFilterBase*>(filter.get());
 * }
 * ```
 */
public fun asCFB(filter: SkSp<SkColorFilter>): SkColorFilterBase {
  TODO("Implement asCFB")
}

/**
 * C++ original:
 * ```cpp
 * static inline sk_sp<SkColorFilterBase> as_CFB_sp(sk_sp<SkColorFilter> filter) {
 *     return sk_sp<SkColorFilterBase>(static_cast<SkColorFilterBase*>(filter.release()));
 * }
 * ```
 */
public fun asCFBSp(filter: SkSp<SkColorFilter>): SkSp<SkColorFilterBase> {
  TODO("Implement asCFBSp")
}

/**
 * C++ original:
 * ```cpp
 * static inline sk_sp<SkShader> SweepGradient(SkPoint center,
 *                                      const SkGradient& grad,
 *                                      const SkMatrix* lm = nullptr) {
 *     return SweepGradient(center, 0, 360, grad, lm);
 * }
 * ```
 */
public fun sweepGradient(
  center: SkPoint,
  grad: SkGradient,
  lm: SkMatrix? = TODO(),
): SkSp<SkShader> {
  TODO("Implement sweepGradient")
}
