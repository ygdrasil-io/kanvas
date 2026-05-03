package org.skia.utils

import SkUniqueCFRef
import kotlin.Array
import kotlin.Boolean
import kotlin.Char
import kotlin.CharArray
import kotlin.Double
import kotlin.Float
import kotlin.Int
import kotlin.String
import kotlin.UInt
import kotlin.ULong
import kotlin.UShort
import kotlin.Unit
import org.skia.core.Float4
import org.skia.core.SkCanvas
import org.skia.core.SkDrawShadowRec
import org.skia.core.SkFontMetrics
import org.skia.core.SkPMColor4f
import org.skia.core.SkPathBuilder
import org.skia.core.SkPathEffectBase
import org.skia.core.SkResourceCache
import org.skia.core.SkStrokeRec
import org.skia.core.SkTInternalLList
import org.skia.core.SkVertices
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkData
import org.skia.foundation.SkDeserialProcs
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPath
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.foundation.SkStreamSeekable
import org.skia.foundation.SkUnichar
import org.skia.gpu.ganesh.GrDirectContext
import org.skia.gpu.ganesh.SkAlphaType
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkPoint3
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkVector
import org.skia.memory.SkTDArray
import org.skia.pdf.SkDocumentPage
import org.skia.ports.OpszVariation
import org.skia.sksl.ProgramKind
import undefined.CFMutableDictionaryRef
import undefined.CGBitmapInfo
import undefined.CGColorSpaceRef
import undefined.CGContextRef
import undefined.CGDataProviderRef
import undefined.CGFloat
import undefined.CGImageRef
import undefined.CTFontRef
import undefined.SkCTFontSmoothBehavior

/**
 * C++ original:
 * ```cpp
 * static SkScalar SkScalarDotDiv(int count, const SkScalar a[], int step_a,
 *                                const SkScalar b[], int step_b,
 *                                SkScalar denom) {
 *     SkScalar prod = 0;
 *     for (int i = 0; i < count; i++) {
 *         prod += a[0] * b[0];
 *         a += step_a;
 *         b += step_b;
 *     }
 *     return prod / denom;
 * }
 * ```
 */
public fun skScalarDotDiv(
  count: Int,
  a: Array<SkScalar>,
  stepA: Int,
  b: Array<SkScalar>,
  stepB: Int,
  denom: SkScalar,
): SkScalar {
  TODO("Implement skScalarDotDiv")
}

/**
 * C++ original:
 * ```cpp
 * static void setup_MC_state(SkMCState* state, const SkMatrix& matrix, const SkIRect& clip) {
 *     // initialize the struct
 *     state->clipRectCount = 0;
 *
 *     // capture the matrix
 *     for (int i = 0; i < 9; i++) {
 *         state->matrix[i] = matrix.get(i);
 *     }
 *
 *     /*
 *      *  We only support a single clipRect, so we take the clip's bounds. Clients have long made
 *      *  this assumption anyway, so this restriction is fine.
 *      */
 *     SkSWriter32<sizeof(ClipRect)> clipWriter;
 *
 *     if (!clip.isEmpty()) {
 *         state->clipRectCount = 1;
 *         state->clipRects = (ClipRect*)sk_malloc_throw(sizeof(ClipRect));
 *         state->clipRects->left = clip.fLeft;
 *         state->clipRects->top = clip.fTop;
 *         state->clipRects->right = clip.fRight;
 *         state->clipRects->bottom = clip.fBottom;
 *     }
 * }
 * ```
 */
public fun setupMCState(
  state: SkMCState?,
  matrix: SkMatrix,
  clip: SkIRect,
) {
  TODO("Implement setupMCState")
}

/**
 * C++ original:
 * ```cpp
 * static void setup_canvas_from_MC_state(const SkMCState& state, SkCanvas* canvas) {
 *     // reconstruct the matrix
 *     SkMatrix matrix;
 *     for (int i = 0; i < 9; i++) {
 *         matrix.set(i, state.matrix[i]);
 *     }
 *
 *     // only realy support 1 rect, so if the caller (legacy?) sent us more, we just take the bounds
 *     // of what they sent.
 *     SkIRect bounds = SkIRect::MakeEmpty();
 *     if (state.clipRectCount > 0) {
 *         bounds.setLTRB(state.clipRects[0].left,
 *                        state.clipRects[0].top,
 *                        state.clipRects[0].right,
 *                        state.clipRects[0].bottom);
 *         for (int i = 1; i < state.clipRectCount; ++i) {
 *             bounds.join({state.clipRects[i].left,
 *                          state.clipRects[i].top,
 *                          state.clipRects[i].right,
 *                          state.clipRects[i].bottom});
 *         }
 *     }
 *
 *     canvas->clipRect(SkRect::Make(bounds));
 *     canvas->concat(matrix);
 * }
 * ```
 */
public fun setupCanvasFromMCState(state: SkMCState, canvas: SkCanvas?) {
  TODO("Implement setupCanvasFromMCState")
}

/**
 * C++ original:
 * ```cpp
 * static std::unique_ptr<SkCanvas>
 * make_canvas_from_canvas_layer(const SkCanvasLayerState& layerState) {
 *     SkASSERT(kRaster_CanvasBackend == layerState.type);
 *
 *     SkBitmap bitmap;
 *     SkColorType colorType =
 *         layerState.raster.config == kARGB_8888_RasterConfig ? kN32_SkColorType :
 *         layerState.raster.config == kRGB_565_RasterConfig ? kRGB_565_SkColorType :
 *         kUnknown_SkColorType;
 *
 *     if (colorType == kUnknown_SkColorType) {
 *         return nullptr;
 *     }
 *
 *     bitmap.installPixels(SkImageInfo::Make(layerState.width, layerState.height,
 *                                            colorType, kPremul_SkAlphaType),
 *                          layerState.raster.pixels, (size_t) layerState.raster.rowBytes);
 *
 *     SkASSERT(!bitmap.empty());
 *     SkASSERT(!bitmap.isNull());
 *
 *     std::unique_ptr<SkCanvas> canvas(new SkCanvas(bitmap));
 *
 *     // setup the matrix and clip
 *     setup_canvas_from_MC_state(layerState.mcState, canvas.get());
 *
 *     return canvas;
 * }
 * ```
 */
public fun makeCanvasFromCanvasLayer(layerState: SkCanvasLayerState): SkCanvas? {
  TODO("Implement makeCanvasFromCanvasLayer")
}

/**
 * C++ original:
 * ```cpp
 * static int find_simple(const SkUnichar base[], int count, SkUnichar value) {
 *     int index;
 *     for (index = 0;; ++index) {
 *         if (value <= base[index]) {
 *             if (value < base[index]) {
 *                 index = ~index; // not found
 *             }
 *             break;
 *         }
 *     }
 *     return index;
 * }
 * ```
 */
public fun findSimple(
  base: Array<SkUnichar>,
  count: Int,
  `value`: SkUnichar,
): Int {
  TODO("Implement findSimple")
}

/**
 * C++ original:
 * ```cpp
 * static int find_with_slope(const SkUnichar base[], int count, SkUnichar value, double denom) {
 *     SkASSERT(count >= kMinCountForSlope);
 *
 *     int index;
 *     if (value <= base[1]) {
 *         index = 1;
 *         if (value < base[index]) {
 *             index = ~index;
 *         }
 *     } else if (value >= base[count - 2]) {
 *         index = count - 2;
 *         if (value > base[index]) {
 *             index = ~(index + 1);
 *         }
 *     } else {
 *         // make our guess based on the "slope" of the current values
 * //        index = 1 + (int64_t)(count - 2) * (value - base[1]) / (base[count - 2] - base[1]);
 *         index = 1 + (int)(denom * (count - 2) * (value - base[1]));
 *         SkASSERT(index >= 1 && index <= count - 2);
 *
 *         if (value >= base[index]) {
 *             for (;; ++index) {
 *                 if (value <= base[index]) {
 *                     if (value < base[index]) {
 *                         index = ~index; // not found
 *                     }
 *                     break;
 *                 }
 *             }
 *         } else {
 *             for (--index;; --index) {
 *                 SkASSERT(index >= 0);
 *                 if (value >= base[index]) {
 *                     if (value > base[index]) {
 *                         index = ~(index + 1);
 *                     }
 *                     break;
 *                 }
 *             }
 *         }
 *     }
 *     return index;
 * }
 * ```
 */
public fun findWithSlope(
  base: Array<SkUnichar>,
  count: Int,
  `value`: SkUnichar,
  denom: Double,
): Int {
  TODO("Implement findWithSlope")
}

/**
 * C++ original:
 * ```cpp
 * static SkFontMetrics scale_fontmetrics(const SkFontMetrics& src, float sx, float sy) {
 *     SkFontMetrics dst = src;
 *
 *     #define SCALE_X(field)  dst.field *= sx
 *     #define SCALE_Y(field)  dst.field *= sy
 *
 *     SCALE_X(fAvgCharWidth);
 *     SCALE_X(fMaxCharWidth);
 *     SCALE_X(fXMin);
 *     SCALE_X(fXMax);
 *
 *     SCALE_Y(fTop);
 *     SCALE_Y(fAscent);
 *     SCALE_Y(fDescent);
 *     SCALE_Y(fBottom);
 *     SCALE_Y(fLeading);
 *     SCALE_Y(fXHeight);
 *     SCALE_Y(fCapHeight);
 *     SCALE_Y(fUnderlineThickness);
 *     SCALE_Y(fUnderlinePosition);
 *     SCALE_Y(fStrikeoutThickness);
 *     SCALE_Y(fStrikeoutPosition);
 *
 *     #undef SCALE_X
 *     #undef SCALE_Y
 *
 *     return dst;
 * }
 * ```
 */
public fun scaleFontmetrics(
  src: SkFontMetrics,
  sx: Float,
  sy: Float,
): SkFontMetrics {
  TODO("Implement scaleFontmetrics")
}

/**
 * C++ original:
 * ```cpp
 * static inline int is_even(int x) {
 *     return !(x & 1);
 * }
 * ```
 */
public fun isEven(x: Int): Int {
  TODO("Implement isEven")
}

/**
 * C++ original:
 * ```cpp
 * static SkScalar find_first_interval(SkSpan<const SkScalar> intervals, SkScalar phase,
 *                                     size_t* index) {
 *     for (size_t i = 0; i < intervals.size(); ++i) {
 *         SkScalar gap = intervals[i];
 *         if (phase > gap || (phase == gap && gap)) {
 *             phase -= gap;
 *         } else {
 *             *index = i;
 *             return gap - phase;
 *         }
 *     }
 *     // If we get here, phase "appears" to be larger than our length. This
 *     // shouldn't happen with perfect precision, but we can accumulate errors
 *     // during the initial length computation (rounding can make our sum be too
 *     // big or too small. In that event, we just have to eat the error here.
 *     *index = 0;
 *     return intervals[0];
 * }
 * ```
 */
public fun findFirstInterval(
  intervals: SkSpan<SkScalar>,
  phase: SkScalar,
  index: ULong?,
): SkScalar {
  TODO("Implement findFirstInterval")
}

/**
 * C++ original:
 * ```cpp
 * void SkDashPath::CalcDashParameters(SkScalar phase, SkSpan<const SkScalar> intervals,
 *                                     SkScalar* initialDashLength, size_t* initialDashIndex,
 *                                     SkScalar* intervalLength, SkScalar* adjustedPhase) {
 *     SkScalar len = 0;
 *     for (SkScalar interval : intervals) {
 *         len += interval;
 *     }
 *     *intervalLength = len;
 *     // Adjust phase to be between 0 and len, "flipping" phase if negative.
 *     // e.g., if len is 100, then phase of -20 (or -120) is equivalent to 80
 *     if (adjustedPhase) {
 *         if (phase < 0) {
 *             phase = -phase;
 *             if (phase > len) {
 *                 phase = SkScalarMod(phase, len);
 *             }
 *             phase = len - phase;
 *
 *             // Due to finite precision, it's possible that phase == len,
 *             // even after the subtract (if len >>> phase), so fix that here.
 *             // This fixes http://crbug.com/124652 .
 *             SkASSERT(phase <= len);
 *             if (phase == len) {
 *                 phase = 0;
 *             }
 *         } else if (phase >= len) {
 *             phase = SkScalarMod(phase, len);
 *         }
 *         *adjustedPhase = phase;
 *     }
 *     SkASSERT(phase >= 0 && phase < len);
 *
 *     *initialDashLength = find_first_interval(intervals, phase, initialDashIndex);
 *
 *     SkASSERT(*initialDashLength >= 0);
 *     SkASSERT(*initialDashIndex < intervals.size());
 * }
 * ```
 */
public fun calcDashParameters(
  phase: SkScalar,
  intervals: SkSpan<SkScalar>,
  initialDashLength: SkScalar?,
  initialDashIndex: ULong?,
  intervalLength: SkScalar?,
  adjustedPhase: SkScalar?,
) {
  TODO("Implement calcDashParameters")
}

/**
 * C++ original:
 * ```cpp
 * static void adjust_zero_length_line(SkPoint pts[2]) {
 *     SkASSERT(pts[0] == pts[1]);
 *     pts[1].fX += std::max(1.001f, pts[1].fX) * SK_ScalarNearlyZero;
 * }
 * ```
 */
public fun adjustZeroLengthLine(pts: Array<SkPoint>) {
  TODO("Implement adjustZeroLengthLine")
}

/**
 * C++ original:
 * ```cpp
 * static bool clip_line(SkPoint pts[2], const SkRect& bounds, SkScalar intervalLength,
 *                       SkScalar priorPhase) {
 *     SkVector dxy = pts[1] - pts[0];
 *
 *     // only horizontal or vertical lines
 *     if (dxy.fX && dxy.fY) {
 *         return false;
 *     }
 *     int xyOffset = SkToBool(dxy.fY);  // 0 to adjust horizontal, 1 to adjust vertical
 *
 *     SkScalar minXY = (&pts[0].fX)[xyOffset];
 *     SkScalar maxXY = (&pts[1].fX)[xyOffset];
 *     bool swapped = maxXY < minXY;
 *     if (swapped) {
 *         using std::swap;
 *         swap(minXY, maxXY);
 *     }
 *
 *     SkASSERT(minXY <= maxXY);
 *     SkScalar leftTop = (&bounds.fLeft)[xyOffset];
 *     SkScalar rightBottom = (&bounds.fRight)[xyOffset];
 *     if (maxXY < leftTop || minXY > rightBottom) {
 *         return false;
 *     }
 *
 *     // Now we actually perform the chop, removing the excess to the left/top and
 *     // right/bottom of the bounds (keeping our new line "in phase" with the dash,
 *     // hence the (mod intervalLength).
 *
 *     if (minXY < leftTop) {
 *         minXY = leftTop - SkScalarMod(leftTop - minXY, intervalLength);
 *         if (!swapped) {
 *             minXY -= priorPhase;  // for rectangles, adjust by prior phase
 *         }
 *     }
 *     if (maxXY > rightBottom) {
 *         maxXY = rightBottom + SkScalarMod(maxXY - rightBottom, intervalLength);
 *         if (swapped) {
 *             maxXY += priorPhase;  // for rectangles, adjust by prior phase
 *         }
 *     }
 *
 *     SkASSERT(maxXY >= minXY);
 *     if (swapped) {
 *         using std::swap;
 *         swap(minXY, maxXY);
 *     }
 *     (&pts[0].fX)[xyOffset] = minXY;
 *     (&pts[1].fX)[xyOffset] = maxXY;
 *
 *     if (minXY == maxXY) {
 *         adjust_zero_length_line(pts);
 *     }
 *     return true;
 * }
 * ```
 */
public fun clipLine(
  pts: Array<SkPoint>,
  bounds: SkRect,
  intervalLength: SkScalar,
  priorPhase: SkScalar,
): Boolean {
  TODO("Implement clipLine")
}

/**
 * C++ original:
 * ```cpp
 * static bool cull_path(const SkPath& srcPath, const SkStrokeRec& rec,
 *                       const SkRect* cullRect, SkScalar intervalLength, SkPathBuilder* builder) {
 *     if (!cullRect) {
 *         SkPoint pts[2];
 *         if (srcPath.isLine(pts) && pts[0] == pts[1]) {
 *             adjust_zero_length_line(pts);
 *             builder->moveTo(pts[0]);
 *             builder->lineTo(pts[1]);
 *             return true;
 *         }
 *         return false;
 *     }
 *
 *     SkRect bounds;
 *     bounds = *cullRect;
 *     outset_for_stroke(&bounds, rec);
 *
 *     {
 *         SkPoint pts[2];
 *         if (srcPath.isLine(pts)) {
 *             if (clip_line(pts, bounds, intervalLength, 0)) {
 *                 builder->moveTo(pts[0]);
 *                 builder->lineTo(pts[1]);
 *                 return true;
 *             }
 *             return false;
 *         }
 *     }
 *
 *     if (srcPath.isRect(nullptr)) {
 *         // We'll break the rect into four lines, culling each separately.
 *         SkPath::Iter iter(srcPath, false);
 *
 *         std::optional<SkPath::IterRec> it = iter.next();
 *         SkASSERT(it.has_value() && it->fVerb == SkPathVerb::kMove);
 *
 *         double accum = 0;  // Sum of unculled edge lengths to keep the phase correct.
 *                            // Intentionally a double to minimize the risk of overflow and drift.
 *         while ((it = iter.next()) && (it->fVerb == SkPathVerb::kLine)) {
 *             // Notice this vector v and accum work with the original unclipped length.
 *             SkVector v = it->fPoints[1] - it->fPoints[0];
 *
 *             SkPoint pts[2] = {it->fPoints[0], it->fPoints[1]};
 *             if (clip_line(pts, bounds, intervalLength, std::fmod(accum, intervalLength))) {
 *                 // pts[0] may have just been changed by clip_line().
 *                 // If that's not where we ended the previous lineTo(), we need to moveTo() there.
 *                 auto maybeLast = builder->getLastPt();
 *                 if (!maybeLast || *maybeLast != pts[0]) {
 *                     builder->moveTo(pts[0]);
 *                 }
 *                 builder->lineTo(pts[1]);
 *             }
 *
 *             // We either just traveled v.fX horizontally or v.fY vertically.
 *             SkASSERT(v.fX == 0 || v.fY == 0);
 *             accum += SkScalarAbs(v.fX + v.fY);
 *         }
 *         return !builder->isEmpty();
 *     }
 *
 *     return false;
 * }
 * ```
 */
public fun cullPath(
  srcPath: SkPath,
  rec: SkStrokeRec,
  cullRect: SkRect?,
  intervalLength: SkScalar,
  builder: SkPathBuilder?,
): Boolean {
  TODO("Implement cullPath")
}

/**
 * C++ original:
 * ```cpp
 * bool SkDashPath::InternalFilter(SkPathBuilder* dst, const SkPath& src, SkStrokeRec* rec,
 *                                 const SkRect* cullRect, SkSpan<const SkScalar> aIntervals,
 *                                 SkScalar initialDashLength, int32_t initialDashIndex,
 *                                 SkScalar intervalLength, SkScalar startPhase,
 *                                 StrokeRecApplication strokeRecApplication) {
 *     SkSpan<const SkPoint> srcPts = src.points();
 *     if (srcPts.empty()) {
 *         return true;
 *     }
 *     const size_t count = aIntervals.size();
 *     // we must always have an even number of intervals
 *     SkASSERT(is_even(count));
 *
 *     // we do nothing if the src wants to be filled
 *     SkStrokeRec::Style style = rec->getStyle();
 *     if (SkStrokeRec::kFill_Style == style || SkStrokeRec::kStrokeAndFill_Style == style) {
 *         return false;
 *     }
 *
 *     const SkScalar* intervals = aIntervals.data();
 *     SkScalar        dashCount = 0;
 *
 *     SkPathBuilder builder;
 *     SkPath cullPathStorage;
 *     const SkPath* srcPtr = &src;
 *     if (cull_path(src, *rec, cullRect, intervalLength, &builder)) {
 *         // if rect is closed, starts in a dash, and ends in a dash, add the initial join
 *         // potentially a better fix is described here: skbug.com/40038693
 *         if (src.isRect(nullptr) && src.isLastContourClosed() && is_even(initialDashIndex)) {
 *             SkScalar pathLength = SkPathMeasure(src, false, rec->getResScale()).getLength();
 *             SkScalar endPhase = SkScalarMod(pathLength + startPhase, intervalLength);
 *             size_t index = 0;
 *             while (endPhase > intervals[index]) {
 *                 endPhase -= intervals[index++];
 *                 SkASSERT(index <= count);
 *                 if (index == count) {
 *                     // We have run out of intervals. endPhase "should" never get to this point,
 *                     // but it could if the subtracts underflowed. Hence we will pin it as if it
 *                     // perfectly ran through the intervals.
 *                     // See crbug.com/875494 (and skbug.com/40039544)
 *                     endPhase = 0;
 *                     break;
 *                 }
 *             }
 *             // if dash ends inside "on", or ends at beginning of "off"
 *             if (is_even(index) == (endPhase > 0)) {
 *                 SkPoint midPoint = srcPts.front();
 *                 // get vector at end of rect
 *                 int last = src.countPoints() - 1;
 *                 while (midPoint == srcPts[last]) {
 *                     --last;
 *                     SkASSERT(last >= 0);
 *                 }
 *                 // get vector at start of rect
 *                 int next = 1;
 *                 while (midPoint == srcPts[next]) {
 *                     ++next;
 *                     SkASSERT(next < last);
 *                 }
 *                 SkVector v = midPoint - srcPts[last];
 *                 const SkScalar kTinyOffset = SK_ScalarNearlyZero;
 *                 // scale vector to make start of tiny right angle
 *                 v *= kTinyOffset;
 *                 builder.moveTo(midPoint - v);
 *                 builder.lineTo(midPoint);
 *                 v = midPoint - srcPts[next];
 *                 // scale vector to make end of tiny right angle
 *                 v *= kTinyOffset;
 *                 builder.lineTo(midPoint - v);
 *             }
 *         }
 *
 *         // If PathMeasure took a SkPathRaw, we could pass it the raw from src or the builder,
 *         // and not need to first 'detach' a path from the builder.
 *         cullPathStorage = builder.detach();
 *         srcPtr = &cullPathStorage;
 *     }
 *
 *     SpecialLineRec lineRec;
 *     bool specialLine = (StrokeRecApplication::kAllow == strokeRecApplication) &&
 *                        lineRec.init(*srcPtr, dst, rec, count >> 1, intervalLength);
 *
 *     SkPathMeasure   meas(*srcPtr, false, rec->getResScale());
 *
 *     do {
 *         bool        skipFirstSegment = meas.isClosed();
 *         bool        addedSegment = false;
 *         SkScalar    length = meas.getLength();
 *         size_t      index = initialDashIndex;
 *
 *         // Since the path length / dash length ratio may be arbitrarily large, we can exert
 *         // significant memory pressure while attempting to build the filtered path. To avoid this,
 *         // we simply give up dashing beyond a certain threshold.
 *         //
 *         // The original bug report (http://crbug.com/165432) is based on a path yielding more than
 *         // 90 million dash segments and crashing the memory allocator. A limit of 1 million
 *         // segments seems reasonable: at 2 verbs per segment * 9 bytes per verb, this caps the
 *         // maximum dash memory overhead at roughly 17MB per path.
 *         dashCount += length * (count >> 1) / intervalLength;
 *         if (dashCount > kMaxDashCount) {
 *             dst->reset();
 *             return false;
 *         }
 *
 *         // Using double precision to avoid looping indefinitely due to single precision rounding
 *         // (for extreme path_length/dash_length ratios). See test_infinite_dash() unittest.
 *         double  distance = 0;
 *         double  dlen = initialDashLength;
 *
 *         while (distance < length) {
 *             SkASSERT(dlen >= 0);
 *             addedSegment = false;
 *             if (is_even(index) && !skipFirstSegment) {
 *                 addedSegment = true;
 *
 *                 if (specialLine) {
 *                     lineRec.addSegment(SkDoubleToScalar(distance),
 *                                        SkDoubleToScalar(distance + dlen),
 *                                        dst);
 *                 } else {
 *                     meas.getSegment(SkDoubleToScalar(distance),
 *                                     SkDoubleToScalar(distance + dlen),
 *                                     dst, true);
 *                 }
 *             }
 *             distance += dlen;
 *
 *             // clear this so we only respect it the first time around
 *             skipFirstSegment = false;
 *
 *             // wrap around our intervals array if necessary
 *             index += 1;
 *             SkASSERT(index <= count);
 *             if (index == count) {
 *                 index = 0;
 *             }
 *
 *             // fetch our next dlen
 *             dlen = intervals[index];
 *         }
 *
 *         // extend if we ended on a segment and we need to join up with the (skipped) initial segment
 *         if (meas.isClosed() && is_even(initialDashIndex) &&
 *             initialDashLength >= 0) {
 *             meas.getSegment(0, initialDashLength, dst, !addedSegment);
 *         }
 *     } while (meas.nextContour());
 *
 *     return true;
 * }
 * ```
 */
public fun internalFilter(
  dst: SkPathBuilder?,
  src: SkPath,
  rec: SkStrokeRec?,
  cullRect: SkRect?,
  aIntervals: SkSpan<SkScalar>,
  initialDashLength: SkScalar,
  initialDashIndex: Int,
  intervalLength: SkScalar,
  startPhase: SkScalar,
  strokeRecApplication: StrokeRecApplication,
): Boolean {
  TODO("Implement internalFilter")
}

/**
 * C++ original:
 * ```cpp
 * bool SkDashPath::FilterDashPath(SkPathBuilder* dst, const SkPath& src, SkStrokeRec* rec,
 *                                 const SkRect* cullRect, const SkPathEffectBase::DashInfo& info) {
 *     if (!ValidDashPath(info.fPhase, info.fIntervals)) {
 *         return false;
 *     }
 *     SkScalar initialDashLength = 0;
 *     size_t initialDashIndex = 0;
 *     SkScalar intervalLength = 0;
 *     CalcDashParameters(info.fPhase, info.fIntervals, &initialDashLength,
 *                        &initialDashIndex, &intervalLength);
 *     return InternalFilter(dst, src, rec, cullRect, info.fIntervals, initialDashLength,
 *                           initialDashIndex, intervalLength, info.fPhase);
 * }
 * ```
 */
public fun filterDashPath(
  dst: SkPathBuilder?,
  src: SkPath,
  rec: SkStrokeRec?,
  cullRect: SkRect?,
  info: SkPathEffectBase.DashInfo,
): Boolean {
  TODO("Implement filterDashPath")
}

/**
 * C++ original:
 * ```cpp
 * bool SkDashPath::ValidDashPath(SkScalar phase, SkSpan<const SkScalar> intervals) {
 *     if (intervals.size() < 2 || !SkIsAlign2(intervals.size())) {
 *         return false;
 *     }
 *     SkScalar length = 0;
 *     for (SkScalar interval : intervals) {
 *         if (interval < 0) {
 *             return false;
 *         }
 *         length += interval;
 *     }
 *     // watch out for values that might make us go out of bounds
 *     return length > 0 && SkIsFinite(phase, length);
 * }
 * ```
 */
public fun validDashPath(phase: SkScalar, intervals: SkSpan<SkScalar>): Boolean {
  TODO("Implement validDashPath")
}

/**
 * C++ original:
 * ```cpp
 * static double pow_by_squaring(double value, double base, int e) {
 *     // https://en.wikipedia.org/wiki/Exponentiation_by_squaring
 *     SkASSERT(e > 0);
 *     while (true) {
 *         if (e & 1) {
 *             value *= base;
 *         }
 *         e >>= 1;
 *         if (0 == e) {
 *             return value;
 *         }
 *         base *= base;
 *     }
 * }
 * ```
 */
public fun powBySquaring(
  `value`: Double,
  base: Double,
  e: Int,
): Double {
  TODO("Implement powBySquaring")
}

/**
 * C++ original:
 * ```cpp
 * unsigned SkFloatToDecimal(float value, char output[kMaximumSkFloatToDecimalLength]) {
 *     /* The longest result is -FLT_MIN.
 *        We serialize it as "-.0000000000000000000000000000000000000117549435"
 *        which has 48 characters plus a terminating '\0'. */
 *
 *     static_assert(kMaximumSkFloatToDecimalLength == 49, "");
 *     // 3 = '-', '.', and '\0' characters.
 *     // 9 = number of significant digits
 *     // abs(FLT_MIN_10_EXP) = number of zeros in FLT_MIN
 *     static_assert(kMaximumSkFloatToDecimalLength == 3 + 9 - FLT_MIN_10_EXP, "");
 *
 *     /* section C.1 of the PDF1.4 spec (http://goo.gl/0SCswJ) says that
 *        most PDF rasterizers will use fixed-point scalars that lack the
 *        dynamic range of floats.  Even if this is the case, I want to
 *        serialize these (uncommon) very small and very large scalar
 *        values with enough precision to allow a floating-point
 *        rasterizer to read them in with perfect accuracy.
 *        Experimentally, rasterizers such as pdfium do seem to benefit
 *        from this.  Rasterizers that rely on fixed-point scalars should
 *        gracefully ignore these values that they can not parse. */
 *     char* output_ptr = &output[0];
 *     const char* const end = &output[kMaximumSkFloatToDecimalLength - 1];
 *     // subtract one to leave space for '\0'.
 *
 *     /* This function is written to accept any possible input value,
 *        including non-finite values such as INF and NAN.  In that case,
 *        we ignore value-correctness and output a syntacticly-valid
 *        number. */
 *     if (value == INFINITY) {
 *         value = FLT_MAX;  // nearest finite float.
 *     }
 *     if (value == -INFINITY) {
 *         value = -FLT_MAX;  // nearest finite float.
 *     }
 *     if (!std::isfinite(value) || value == 0.0f) {
 *         // NAN is unsupported in PDF.  Always output a valid number.
 *         // Also catch zero here, as a special case.
 *         *output_ptr++ = '0';
 *         *output_ptr = '\0';
 *         return static_cast<unsigned>(output_ptr - output);
 *     }
 *     if (value < 0.0) {
 *         *output_ptr++ = '-';
 *         value = -value;
 *     }
 *     SkASSERT(value >= 0.0f);
 *
 *     int binaryExponent;
 *     (void)std::frexp(value, &binaryExponent);
 *     static const double kLog2 = 0.3010299956639812;  // log10(2.0);
 *     int decimalExponent = static_cast<int>(std::floor(kLog2 * binaryExponent));
 *     int decimalShift = decimalExponent - 8;
 *     double power = pow10(-decimalShift);
 *     SkASSERT(value * power <= (double)INT_MAX);
 *     int d = static_cast<int>(value * power + 0.5);
 *     // SkASSERT(value == (float)(d * pow(10.0, decimalShift)));
 *     SkASSERT(d <= 999999999);
 *     if (d > 167772159) {  // floor(pow(10,1+log10(1<<24)))
 *        // need one fewer decimal digits for 24-bit precision.
 *        decimalShift = decimalExponent - 7;
 *        // SkASSERT(power * 0.1 = pow10(-decimalShift));
 *        // recalculate to get rounding right.
 *        d = static_cast<int>(value * (power * 0.1) + 0.5);
 *        SkASSERT(d <= 99999999);
 *     }
 *     while (d % 10 == 0) {
 *         d /= 10;
 *         ++decimalShift;
 *     }
 *     SkASSERT(d > 0);
 *     // SkASSERT(value == (float)(d * pow(10.0, decimalShift)));
 *     unsigned char buffer[9]; // decimal value buffer.
 *     int bufferIndex = 0;
 *     do {
 *         buffer[bufferIndex++] = d % 10;
 *         d /= 10;
 *     } while (d != 0);
 *     SkASSERT(bufferIndex <= (int)sizeof(buffer) && bufferIndex > 0);
 *     if (decimalShift >= 0) {
 *         do {
 *             --bufferIndex;
 *             *output_ptr++ = '0' + buffer[bufferIndex];
 *         } while (bufferIndex);
 *         for (int i = 0; i < decimalShift; ++i) {
 *             *output_ptr++ = '0';
 *         }
 *     } else {
 *         int placesBeforeDecimal = bufferIndex + decimalShift;
 *         if (placesBeforeDecimal > 0) {
 *             while (placesBeforeDecimal-- > 0) {
 *                 --bufferIndex;
 *                 *output_ptr++ = '0' + buffer[bufferIndex];
 *             }
 *             *output_ptr++ = '.';
 *         } else {
 *             *output_ptr++ = '.';
 *             int placesAfterDecimal = -placesBeforeDecimal;
 *             while (placesAfterDecimal-- > 0) {
 *                 *output_ptr++ = '0';
 *             }
 *         }
 *         while (bufferIndex > 0) {
 *             --bufferIndex;
 *             *output_ptr++ = '0' + buffer[bufferIndex];
 *             if (output_ptr == end) {
 *                 break;  // denormalized: don't need extra precision.
 *                 // Note: denormalized numbers will not have the same number of
 *                 // significantDigits, but do not need them to round-trip.
 *             }
 *         }
 *     }
 *     SkASSERT(output_ptr <= end);
 *     *output_ptr = '\0';
 *     return static_cast<unsigned>(output_ptr - output);
 * }
 * ```
 */
public fun skFloatToDecimal(`value`: Float, output: CharArray): UInt {
  TODO("Implement skFloatToDecimal")
}

/**
 * C++ original:
 * ```cpp
 * void SkComputeGivensRotation(const SkVector& h, SkMatrix* G) {
 *     const SkScalar& a = h.fX;
 *     const SkScalar& b = h.fY;
 *     SkScalar c, s;
 *     if (0 == b) {
 *         c = SkScalarCopySign(SK_Scalar1, a);
 *         s = 0;
 *         //r = SkScalarAbs(a);
 *     } else if (0 == a) {
 *         c = 0;
 *         s = -SkScalarCopySign(SK_Scalar1, b);
 *         //r = SkScalarAbs(b);
 *     } else if (SkScalarAbs(b) > SkScalarAbs(a)) {
 *         SkScalar t = a / b;
 *         SkScalar u = SkScalarCopySign(SkScalarSqrt(SK_Scalar1 + t*t), b);
 *         s = -SK_Scalar1 / u;
 *         c = -s * t;
 *         //r = b * u;
 *     } else {
 *         SkScalar t = b / a;
 *         SkScalar u = SkScalarCopySign(SkScalarSqrt(SK_Scalar1 + t*t), a);
 *         c = SK_Scalar1 / u;
 *         s = -c * t;
 *         //r = a * u;
 *     }
 *
 *     G->setSinCos(s, c);
 * }
 * ```
 */
public fun skComputeGivensRotation(h: SkVector, g: SkMatrix?) {
  TODO("Implement skComputeGivensRotation")
}

/**
 * C++ original:
 * ```cpp
 * int ReadPageCount(SkStreamSeekable* src) {
 *     if (!src) {
 *         return 0;
 *     }
 *     src->seek(0);
 *     const size_t size = sizeof(kMagic) - 1;
 *     char buffer[size];
 *     if (size != src->read(buffer, size) || 0 != memcmp(kMagic, buffer, size)) {
 *         src = nullptr;
 *         return 0;
 *     }
 *     uint32_t versionNumber;
 *     if (!src->readU32(&versionNumber) || versionNumber != kVersion) {
 *         return 0;
 *     }
 *     uint32_t pageCount;
 *     if (!src->readU32(&pageCount) || pageCount > INT_MAX) {
 *         return 0;
 *     }
 *     // leave stream position right here.
 *     return SkTo<int>(pageCount);
 * }
 * ```
 */
public fun readPageCount(src: SkStreamSeekable?): Int {
  TODO("Implement readPageCount")
}

/**
 * C++ original:
 * ```cpp
 * bool ReadPageSizes(SkStreamSeekable* stream,
 *                    SkDocumentPage* dstArray,
 *                    int dstArrayCount) {
 *     if (!dstArray || dstArrayCount < 1) {
 *         return false;
 *     }
 *     int pageCount = ReadPageCount(stream);
 *     if (pageCount < 1 || pageCount != dstArrayCount) {
 *         return false;
 *     }
 *     for (int i = 0; i < pageCount; ++i) {
 *         SkSize& s = dstArray[i].fSize;
 *         if (sizeof(s) != stream->read(&s, sizeof(s))) {
 *             return false;
 *         }
 *     }
 *     // leave stream position right here.
 *     return true;
 * }
 * ```
 */
public fun readPageSizes(
  stream: SkStreamSeekable?,
  dstArray: SkDocumentPage?,
  dstArrayCount: Int,
): Boolean {
  TODO("Implement readPageSizes")
}

/**
 * C++ original:
 * ```cpp
 * bool Read(SkStreamSeekable* src,
 *           SkDocumentPage* dstArray,
 *           int dstArrayCount,
 *           const SkDeserialProcs* procs) {
 *     if (!ReadPageSizes(src, dstArray, dstArrayCount)) {
 *         return false;
 *     }
 *     SkSize joined = {0.0f, 0.0f};
 *     for (int i = 0; i < dstArrayCount; ++i) {
 *         joined = SkSize{std::max(joined.width(), dstArray[i].fSize.width()),
 *                         std::max(joined.height(), dstArray[i].fSize.height())};
 *     }
 *
 *     auto picture = SkPicture::MakeFromStream(src, procs);
 *     if (!picture) {
 *         return false;
 *     }
 *
 *     PagerCanvas canvas(joined.toCeil(), dstArray, dstArrayCount);
 *     // Must call playback(), not drawPicture() to reach
 *     // PagerCanvas::onDrawAnnotation().
 *     picture->playback(&canvas);
 *     if (canvas.fIndex != dstArrayCount) {
 *         SkDEBUGF("Malformed SkMultiPictureDocument: canvas.fIndex=%d dstArrayCount=%d\n",
 *             canvas.fIndex, dstArrayCount);
 *     }
 *     return true;
 * }
 * ```
 */
public fun read(
  src: SkStreamSeekable?,
  dstArray: SkDocumentPage?,
  dstArrayCount: Int,
  procs: SkDeserialProcs?,
): Boolean {
  TODO("Implement read")
}

/**
 * C++ original:
 * ```cpp
 * std::unique_ptr<SkCanvas> SkMakeNullCanvas() {
 *     // An N-Way canvas forwards calls to N canvas's. When N == 0 it's
 *     // effectively a null canvas.
 *     return std::unique_ptr<SkCanvas>(new SkNWayCanvas(0, 0));
 * }
 * ```
 */
public fun skMakeNullCanvas(): SkCanvas? {
  TODO("Implement skMakeNullCanvas")
}

/**
 * C++ original:
 * ```cpp
 * static int to_hex(int c)
 * {
 *     if (is_digit(c))
 *         return c - '0';
 *
 *     c |= 0x20;  // make us lower-case
 *     if (is_between(c, 'a', 'f'))
 *         return c + 10 - 'a';
 *     else
 *         return -1;
 * }
 * ```
 */
public fun toHex(c: Int): Int {
  TODO("Implement toHex")
}

/**
 * C++ original:
 * ```cpp
 * static bool lookup_str(const char str[], const char** table, int count)
 * {
 *     while (--count >= 0)
 *         if (!strcmp(str, table[count]))
 *             return true;
 *     return false;
 * }
 * ```
 */
public fun lookupStr(
  str: CharArray,
  table: Int?,
  count: Int,
): Boolean {
  TODO("Implement lookupStr")
}

/**
 * C++ original:
 * ```cpp
 * static inline bool is_lower(int c) {
 *     return is_between(c, 'a', 'z');
 * }
 * ```
 */
public fun isLower(c: Int): Boolean {
  TODO("Implement isLower")
}

/**
 * C++ original:
 * ```cpp
 * static inline int to_upper(int c) {
 *     return c - 'a' + 'A';
 * }
 * ```
 */
public fun toUpper(c: Int): Int {
  TODO("Implement toUpper")
}

/**
 * C++ original:
 * ```cpp
 * static const char* skip_sep(const char str[]) {
 *     if (!str) {
 *         return nullptr;
 *     }
 *     while (is_sep(*str))
 *         str++;
 *     return str;
 * }
 * ```
 */
public fun skipSep(str: CharArray): Char {
  TODO("Implement skipSep")
}

/**
 * C++ original:
 * ```cpp
 * static const char* find_points(const char str[], SkPoint value[], int count,
 *                                bool isRelative, SkPoint* relative) {
 *     str = SkParse::FindScalars(str, &value[0].fX, count * 2);
 *     if (isRelative) {
 *         for (int index = 0; index < count; index++) {
 *             value[index].fX += relative->fX;
 *             value[index].fY += relative->fY;
 *         }
 *     }
 *     return str;
 * }
 * ```
 */
public fun findPoints(
  str: CharArray,
  `value`: Array<SkPoint>,
  count: Int,
  isRelative: Boolean,
  relative: SkPoint?,
): Char {
  TODO("Implement findPoints")
}

/**
 * C++ original:
 * ```cpp
 * static const char* find_scalar(const char str[], SkScalar* value,
 *                                bool isRelative, SkScalar relative) {
 *     str = SkParse::FindScalar(str, value);
 *     if (!str) {
 *         return nullptr;
 *     }
 *     if (isRelative) {
 *         *value += relative;
 *     }
 *     str = skip_sep(str);
 *     return str;
 * }
 * ```
 */
public fun findScalar(
  str: CharArray,
  `value`: SkScalar?,
  isRelative: Boolean,
  relative: SkScalar,
): Char {
  TODO("Implement findScalar")
}

/**
 * C++ original:
 * ```cpp
 * static const char* find_flag(const char str[], bool* value) {
 *     if (!str) {
 *         return nullptr;
 *     }
 *     if (str[0] != '1' && str[0] != '0') {
 *         return nullptr;
 *     }
 *     *value = str[0] != '0';
 *     str = skip_sep(str + 1);
 *     return str;
 * }
 * ```
 */
public fun findFlag(str: CharArray, `value`: Boolean?): Char {
  TODO("Implement findFlag")
}

/**
 * C++ original:
 * ```cpp
 * static SkScalar approx_arc_length(const SkPoint points[], int count) {
 *     if (count < 2) {
 *         return 0;
 *     }
 *     SkScalar arcLength = 0;
 *     for (int i = 0; i < count - 1; i++) {
 *         arcLength += SkPoint::Distance(points[i], points[i + 1]);
 *     }
 *     return SkIsFinite(arcLength) ? arcLength : -1;
 * }
 * ```
 */
public fun approxArcLength(points: Array<SkPoint>, count: Int): SkScalar {
  TODO("Implement approxArcLength")
}

/**
 * C++ original:
 * ```cpp
 * static skvx::float4 bilerp(SkScalar tx, SkScalar ty,
 *                            const skvx::float4& c00,
 *                            const skvx::float4& c10,
 *                            const skvx::float4& c01,
 *                            const skvx::float4& c11) {
 *     auto a = c00 * (1.f - tx) + c10 * tx;
 *     auto b = c01 * (1.f - tx) + c11 * tx;
 *     return a * (1.f - ty) + b * ty;
 * }
 * ```
 */
public fun bilerp(
  tx: SkScalar,
  ty: SkScalar,
  c00: Float4,
  c10: Float4,
  c01: Float4,
  c11: Float4,
): Float4 {
  TODO("Implement bilerp")
}

/**
 * C++ original:
 * ```cpp
 * static void skcolor_to_float(SkPMColor4f* dst, const SkColor* src, int count, SkColorSpace* dstCS) {
 *     SkImageInfo srcInfo = SkImageInfo::Make(count, 1, kBGRA_8888_SkColorType,
 *                                             kUnpremul_SkAlphaType, SkColorSpace::MakeSRGB());
 *     SkImageInfo dstInfo = SkImageInfo::Make(count, 1, kRGBA_F32_SkColorType,
 *                                             kPremul_SkAlphaType, sk_ref_sp(dstCS));
 *     SkAssertResult(SkConvertPixels(dstInfo, dst, 0, srcInfo, src, 0));
 * }
 * ```
 */
public fun skcolorToFloat(
  dst: SkPMColor4f?,
  src: SkColor?,
  count: Int,
  dstCS: SkColorSpace?,
) {
  TODO("Implement skcolorToFloat")
}

/**
 * C++ original:
 * ```cpp
 * static void float_to_skcolor(SkColor* dst, const SkPMColor4f* src, int count, SkColorSpace* srcCS) {
 *     SkImageInfo srcInfo = SkImageInfo::Make(count, 1, kRGBA_F32_SkColorType,
 *                                             kPremul_SkAlphaType, sk_ref_sp(srcCS));
 *     SkImageInfo dstInfo = SkImageInfo::Make(count, 1, kBGRA_8888_SkColorType,
 *                                             kUnpremul_SkAlphaType, SkColorSpace::MakeSRGB());
 *     SkAssertResult(SkConvertPixels(dstInfo, dst, 0, srcInfo, src, 0));
 * }
 * ```
 */
public fun floatToSkcolor(
  dst: SkColor?,
  src: SkPMColor4f?,
  count: Int,
  srcCS: SkColorSpace?,
) {
  TODO("Implement floatToSkcolor")
}

/**
 * C++ original:
 * ```cpp
 * std::string PrettyPrint(const std::string& string) {
 *     GLSLPrettyPrint pp;
 *     return pp.prettify(string);
 * }
 * ```
 */
public fun prettyPrint(string: String): String {
  TODO("Implement prettyPrint")
}

/**
 * C++ original:
 * ```cpp
 * void VisitLineByLine(const std::string& text,
 *                      const std::function<void(int lineNumber, const char* lineText)>& visitFn) {
 *     TArray<SkString> lines;
 *     SkStrSplit(text.c_str(), "\n", kStrict_SkStrSplitMode, &lines);
 *     for (int i = 0; i < lines.size(); ++i) {
 *         visitFn(i + 1, lines[i].c_str());
 *     }
 * }
 * ```
 */
public fun visitLineByLine(text: String, visitFn: (Int, Int) -> Unit) {
  TODO("Implement visitLineByLine")
}

/**
 * C++ original:
 * ```cpp
 * std::string SpirvAsHexStream(SkSpan<const uint32_t> spirv) {
 *     std::ostringstream result;
 *     result << "Paste the following SPIR-V binary in https://www.khronos.org/spir/visualizer/\n";
 *     result << "      or pass to `spirv-dis` (optionally with `--comment --nested-indent`)\n";
 *
 *     constexpr size_t kIndicesPerRow = 10;
 *     size_t rowOffset = 0;
 *     for (size_t index = 0; index < spirv.size(); ++index, ++rowOffset) {
 *         if (rowOffset == kIndicesPerRow) {
 *             result << "\n";
 *             rowOffset = 0;
 *         }
 *         result << "0x" << std::uppercase << std::setfill('0') << std::setw(8) << std::hex
 *                << spirv[index] << ",";
 *     }
 *
 *     return result.str();
 * }
 * ```
 */
public fun spirvAsHexStream(spirv: SkSpan<UInt>): String {
  TODO("Implement spirvAsHexStream")
}

/**
 * C++ original:
 * ```cpp
 * std::string BuildShaderErrorMessage(const char* shader, const char* errors) {
 *     std::string abortText{"Shader compilation error\n"
 *                           "------------------------\n"};
 *     VisitLineByLine(shader, [&](int lineNumber, const char* lineText) {
 *         SkSL::String::appendf(&abortText, "%4i\t%s\n", lineNumber, lineText);
 *     });
 *     SkSL::String::appendf(&abortText, "Errors:\n%s", errors);
 *     return abortText;
 * }
 * ```
 */
public fun buildShaderErrorMessage(shader: String?, errors: String?): String {
  TODO("Implement buildShaderErrorMessage")
}

/**
 * C++ original:
 * ```cpp
 * void PrintShaderBanner(SkSL::ProgramKind programKind) {
 *     const char* typeName = "Unknown";
 *     if (SkSL::ProgramConfig::IsVertex(programKind)) {
 *         typeName = "Vertex";
 *     } else if (SkSL::ProgramConfig::IsFragment(programKind)) {
 *         typeName = "Fragment";
 *     }
 *     SkDebugf("---- %s shader ----------------------------------------------------\n", typeName);
 * }
 * ```
 */
public fun printShaderBanner(programKind: ProgramKind) {
  TODO("Implement printShaderBanner")
}

/**
 * C++ original:
 * ```cpp
 * static int compute_side(const SkPoint& p0, const SkVector& v, const SkPoint& p) {
 *     SkVector w = p - p0;
 *     SkScalar perpDot = v.cross(w);
 *     if (!SkScalarNearlyZero(perpDot, kCrossTolerance)) {
 *         return ((perpDot > 0) ? 1 : -1);
 *     }
 *
 *     return 0;
 * }
 * ```
 */
public fun computeSide(
  p0: SkPoint,
  v: SkVector,
  p: SkPoint,
): Int {
  TODO("Implement computeSide")
}

/**
 * C++ original:
 * ```cpp
 * int SkGetPolygonWinding(const SkPoint* polygonVerts, int polygonSize) {
 *     if (polygonSize < 3) {
 *         return 0;
 *     }
 *
 *     // compute area and use sign to determine winding
 *     SkScalar quadArea = 0;
 *     SkVector v0 = polygonVerts[1] - polygonVerts[0];
 *     for (int curr = 2; curr < polygonSize; ++curr) {
 *         SkVector v1 = polygonVerts[curr] - polygonVerts[0];
 *         quadArea += v0.cross(v1);
 *         v0 = v1;
 *     }
 *     if (SkScalarNearlyZero(quadArea, kCrossTolerance)) {
 *         return 0;
 *     }
 *     // 1 == ccw, -1 == cw
 *     return (quadArea > 0) ? 1 : -1;
 * }
 * ```
 */
public fun skGetPolygonWinding(polygonVerts: SkPoint?, polygonSize: Int): Int {
  TODO("Implement skGetPolygonWinding")
}

/**
 * C++ original:
 * ```cpp
 * bool compute_offset_vector(const SkPoint& p0, const SkPoint& p1, SkScalar offset, int side,
 *                            SkPoint* vector) {
 *     SkASSERT(side == -1 || side == 1);
 *     // if distances are equal, can just outset by the perpendicular
 *     SkVector perp = SkVector::Make(p0.fY - p1.fY, p1.fX - p0.fX);
 *     if (!perp.setLength(offset*side)) {
 *         return false;
 *     }
 *     *vector = perp;
 *     return true;
 * }
 * ```
 */
public fun computeOffsetVector(
  p0: SkPoint,
  p1: SkPoint,
  offset: SkScalar,
  side: Int,
  vector: SkPoint?,
): Boolean {
  TODO("Implement computeOffsetVector")
}

/**
 * C++ original:
 * ```cpp
 * static inline bool outside_interval(SkScalar numer, SkScalar denom, bool denomPositive) {
 *     return (denomPositive && (numer < 0 || numer > denom)) ||
 *            (!denomPositive && (numer > 0 || numer < denom));
 * }
 * ```
 */
public fun outsideInterval(
  numer: SkScalar,
  denom: SkScalar,
  denomPositive: Boolean,
): Boolean {
  TODO("Implement outsideInterval")
}

/**
 * C++ original:
 * ```cpp
 * static inline bool zero_length(const SkPoint& v, SkScalar vdotv) {
 *     return !(SkIsFinite(v.fX, v.fY) && vdotv);
 * }
 * ```
 */
public fun zeroLength(v: SkPoint, vdotv: SkScalar): Boolean {
  TODO("Implement zeroLength")
}

/**
 * C++ original:
 * ```cpp
 * static bool compute_intersection(const OffsetSegment& s0, const OffsetSegment& s1,
 *                                  SkPoint* p, SkScalar* s, SkScalar* t) {
 *     const SkVector& v0 = s0.fV;
 *     const SkVector& v1 = s1.fV;
 *     SkVector w = s1.fP0 - s0.fP0;
 *     SkScalar denom = v0.cross(v1);
 *     bool denomPositive = (denom > 0);
 *     SkScalar sNumer, tNumer;
 *     if (SkScalarNearlyZero(denom, kCrossTolerance)) {
 *         // segments are parallel, but not collinear
 *         if (!SkScalarNearlyZero(w.cross(v0), kCrossTolerance) ||
 *             !SkScalarNearlyZero(w.cross(v1), kCrossTolerance)) {
 *             return false;
 *         }
 *
 *         // Check for zero-length segments
 *         SkScalar v0dotv0 = v0.dot(v0);
 *         if (zero_length(v0, v0dotv0)) {
 *             // Both are zero-length
 *             SkScalar v1dotv1 = v1.dot(v1);
 *             if (zero_length(v1, v1dotv1)) {
 *                 // Check if they're the same point
 *                 if (!SkPointPriv::CanNormalize(w.fX, w.fY)) {
 *                     *p = s0.fP0;
 *                     *s = 0;
 *                     *t = 0;
 *                     return true;
 *                 } else {
 *                     // Intersection is indeterminate
 *                     return false;
 *                 }
 *             }
 *             // Otherwise project segment0's origin onto segment1
 *             tNumer = v1.dot(-w);
 *             denom = v1dotv1;
 *             if (outside_interval(tNumer, denom, true)) {
 *                 return false;
 *             }
 *             sNumer = 0;
 *         } else {
 *             // Project segment1's endpoints onto segment0
 *             sNumer = v0.dot(w);
 *             denom = v0dotv0;
 *             tNumer = 0;
 *             if (outside_interval(sNumer, denom, true)) {
 *                 // The first endpoint doesn't lie on segment0
 *                 // If segment1 is degenerate, then there's no collision
 *                 SkScalar v1dotv1 = v1.dot(v1);
 *                 if (zero_length(v1, v1dotv1)) {
 *                     return false;
 *                 }
 *
 *                 // Otherwise try the other one
 *                 SkScalar oldSNumer = sNumer;
 *                 sNumer = v0.dot(w + v1);
 *                 tNumer = denom;
 *                 if (outside_interval(sNumer, denom, true)) {
 *                     // it's possible that segment1's interval surrounds segment0
 *                     // this is false if params have the same signs, and in that case no collision
 *                     if (sNumer*oldSNumer > 0) {
 *                         return false;
 *                     }
 *                     // otherwise project segment0's endpoint onto segment1 instead
 *                     sNumer = 0;
 *                     tNumer = v1.dot(-w);
 *                     denom = v1dotv1;
 *                 }
 *             }
 *         }
 *     } else {
 *         sNumer = w.cross(v1);
 *         if (outside_interval(sNumer, denom, denomPositive)) {
 *             return false;
 *         }
 *         tNumer = w.cross(v0);
 *         if (outside_interval(tNumer, denom, denomPositive)) {
 *             return false;
 *         }
 *     }
 *
 *     SkScalar localS = sNumer/denom;
 *     SkScalar localT = tNumer/denom;
 *
 *     *p = s0.fP0 + v0*localS;
 *     *s = localS;
 *     *t = localT;
 *
 *     return true;
 * }
 * ```
 */
public fun computeIntersection(
  s0: OffsetSegment,
  s1: OffsetSegment,
  p: SkPoint?,
  s: SkScalar?,
  t: SkScalar?,
): Boolean {
  TODO("Implement computeIntersection")
}

/**
 * C++ original:
 * ```cpp
 * bool SkIsConvexPolygon(const SkPoint* polygonVerts, int polygonSize) {
 *     if (polygonSize < 3) {
 *         return false;
 *     }
 *
 *     SkScalar lastPerpDot = 0;
 *     int xSignChangeCount = 0;
 *     int ySignChangeCount = 0;
 *
 *     int prevIndex = polygonSize - 1;
 *     int currIndex = 0;
 *     int nextIndex = 1;
 *     SkVector v0 = polygonVerts[currIndex] - polygonVerts[prevIndex];
 *     SkScalar lastVx = v0.fX;
 *     SkScalar lastVy = v0.fY;
 *     SkVector v1 = polygonVerts[nextIndex] - polygonVerts[currIndex];
 *     for (int i = 0; i < polygonSize; ++i) {
 *         if (!polygonVerts[i].isFinite()) {
 *             return false;
 *         }
 *
 *         // Check that winding direction is always the same (otherwise we have a reflex vertex)
 *         SkScalar perpDot = v0.cross(v1);
 *         if (lastPerpDot*perpDot < 0) {
 *             return false;
 *         }
 *         if (0 != perpDot) {
 *             lastPerpDot = perpDot;
 *         }
 *
 *         // Check that the signs of the edge vectors don't change more than twice per coordinate
 *         if (lastVx*v1.fX < 0) {
 *             xSignChangeCount++;
 *         }
 *         if (lastVy*v1.fY < 0) {
 *             ySignChangeCount++;
 *         }
 *         if (xSignChangeCount > 2 || ySignChangeCount > 2) {
 *             return false;
 *         }
 *         prevIndex = currIndex;
 *         currIndex = nextIndex;
 *         nextIndex = (currIndex + 1) % polygonSize;
 *         if (v1.fX != 0) {
 *             lastVx = v1.fX;
 *         }
 *         if (v1.fY != 0) {
 *             lastVy = v1.fY;
 *         }
 *         v0 = v1;
 *         v1 = polygonVerts[nextIndex] - polygonVerts[currIndex];
 *     }
 *
 *     return true;
 * }
 * ```
 */
public fun skIsConvexPolygon(polygonVerts: SkPoint?, polygonSize: Int): Boolean {
  TODO("Implement skIsConvexPolygon")
}

/**
 * C++ original:
 * ```cpp
 * static void remove_node(const OffsetEdge* node, OffsetEdge** head) {
 *     // remove from linked list
 *     node->fPrev->fNext = node->fNext;
 *     node->fNext->fPrev = node->fPrev;
 *     if (node == *head) {
 *         *head = (node->fNext == node) ? nullptr : node->fNext;
 *     }
 * }
 * ```
 */
public fun removeNode(node: OffsetEdge?, head: Int?) {
  TODO("Implement removeNode")
}

/**
 * C++ original:
 * ```cpp
 * bool SkInsetConvexPolygon(const SkPoint* inputPolygonVerts, int inputPolygonSize,
 *                           SkScalar inset, SkTDArray<SkPoint>* insetPolygon) {
 *     if (inputPolygonSize < 3) {
 *         return false;
 *     }
 *
 *     // restrict this to match other routines
 *     // practically we don't want anything bigger than this anyway
 *     if (inputPolygonSize > std::numeric_limits<uint16_t>::max()) {
 *         return false;
 *     }
 *
 *     // can't inset by a negative or non-finite amount
 *     if (inset < -SK_ScalarNearlyZero || !SkIsFinite(inset)) {
 *         return false;
 *     }
 *
 *     // insetting close to zero just returns the original poly
 *     if (inset <= SK_ScalarNearlyZero) {
 *         for (int i = 0; i < inputPolygonSize; ++i) {
 *             *insetPolygon->append() = inputPolygonVerts[i];
 *         }
 *         return true;
 *     }
 *
 *     // get winding direction
 *     int winding = SkGetPolygonWinding(inputPolygonVerts, inputPolygonSize);
 *     if (0 == winding) {
 *         return false;
 *     }
 *
 *     // set up
 *     AutoSTMalloc<64, OffsetEdge> edgeData(inputPolygonSize);
 *     int prev = inputPolygonSize - 1;
 *     for (int curr = 0; curr < inputPolygonSize; prev = curr, ++curr) {
 *         int next = (curr + 1) % inputPolygonSize;
 *         if (!inputPolygonVerts[curr].isFinite()) {
 *             return false;
 *         }
 *         // check for convexity just to be sure
 *         if (compute_side(inputPolygonVerts[prev], inputPolygonVerts[curr] - inputPolygonVerts[prev],
 *                          inputPolygonVerts[next])*winding < 0) {
 *             return false;
 *         }
 *         SkVector v = inputPolygonVerts[next] - inputPolygonVerts[curr];
 *         SkVector perp = SkVector::Make(-v.fY, v.fX);
 *         perp.setLength(inset*winding);
 *         edgeData[curr].fPrev = &edgeData[prev];
 *         edgeData[curr].fNext = &edgeData[next];
 *         edgeData[curr].fOffset.fP0 = inputPolygonVerts[curr] + perp;
 *         edgeData[curr].fOffset.fV = v;
 *         edgeData[curr].init();
 *     }
 *
 *     OffsetEdge* head = &edgeData[0];
 *     OffsetEdge* currEdge = head;
 *     OffsetEdge* prevEdge = currEdge->fPrev;
 *     int insetVertexCount = inputPolygonSize;
 *     unsigned int iterations = 0;
 *     unsigned int maxIterations = inputPolygonSize * inputPolygonSize;
 *     while (head && prevEdge != currEdge) {
 *         ++iterations;
 *         // we should check each edge against each other edge at most once
 *         if (iterations > maxIterations) {
 *             return false;
 *         }
 *
 *         SkScalar s, t;
 *         SkPoint intersection;
 *         if (compute_intersection(prevEdge->fOffset, currEdge->fOffset,
 *                                  &intersection, &s, &t)) {
 *             // if new intersection is further back on previous inset from the prior intersection
 *             if (s < prevEdge->fTValue) {
 *                 // no point in considering this one again
 *                 remove_node(prevEdge, &head);
 *                 --insetVertexCount;
 *                 // go back one segment
 *                 prevEdge = prevEdge->fPrev;
 *             // we've already considered this intersection, we're done
 *             } else if (currEdge->fTValue > SK_ScalarMin &&
 *                        SkPointPriv::EqualsWithinTolerance(intersection,
 *                                                           currEdge->fIntersection,
 *                                                           1.0e-6f)) {
 *                 break;
 *             } else {
 *                 // add intersection
 *                 currEdge->fIntersection = intersection;
 *                 currEdge->fTValue = t;
 *
 *                 // go to next segment
 *                 prevEdge = currEdge;
 *                 currEdge = currEdge->fNext;
 *             }
 *         } else {
 *             // if prev to right side of curr
 *             int side = winding*compute_side(currEdge->fOffset.fP0,
 *                                             currEdge->fOffset.fV,
 *                                             prevEdge->fOffset.fP0);
 *             if (side < 0 &&
 *                 side == winding*compute_side(currEdge->fOffset.fP0,
 *                                              currEdge->fOffset.fV,
 *                                              prevEdge->fOffset.fP0 + prevEdge->fOffset.fV)) {
 *                 // no point in considering this one again
 *                 remove_node(prevEdge, &head);
 *                 --insetVertexCount;
 *                 // go back one segment
 *                 prevEdge = prevEdge->fPrev;
 *             } else {
 *                 // move to next segment
 *                 remove_node(currEdge, &head);
 *                 --insetVertexCount;
 *                 currEdge = currEdge->fNext;
 *             }
 *         }
 *     }
 *
 *     // store all the valid intersections that aren't nearly coincident
 *     // TODO: look at the main algorithm and see if we can detect these better
 *     insetPolygon->reset();
 *     if (!head) {
 *         return false;
 *     }
 *
 *     static constexpr SkScalar kCleanupTolerance = 0.01f;
 *     if (insetVertexCount >= 0) {
 *         insetPolygon->reserve(insetVertexCount);
 *     }
 *     int currIndex = 0;
 *     *insetPolygon->append() = head->fIntersection;
 *     currEdge = head->fNext;
 *     while (currEdge != head) {
 *         if (!SkPointPriv::EqualsWithinTolerance(currEdge->fIntersection,
 *                                                 (*insetPolygon)[currIndex],
 *                                                 kCleanupTolerance)) {
 *             *insetPolygon->append() = currEdge->fIntersection;
 *             currIndex++;
 *         }
 *         currEdge = currEdge->fNext;
 *     }
 *     // make sure the first and last points aren't coincident
 *     if (currIndex >= 1 &&
 *         SkPointPriv::EqualsWithinTolerance((*insetPolygon)[0], (*insetPolygon)[currIndex],
 *                                             kCleanupTolerance)) {
 *         insetPolygon->pop_back();
 *     }
 *
 *     return SkIsConvexPolygon(insetPolygon->begin(), insetPolygon->size());
 * }
 * ```
 */
public fun skInsetConvexPolygon(
  inputPolygonVerts: SkPoint?,
  inputPolygonSize: Int,
  inset: SkScalar,
  insetPolygon: SkTDArray<SkPoint>?,
): Boolean {
  TODO("Implement skInsetConvexPolygon")
}

/**
 * C++ original:
 * ```cpp
 * bool SkComputeRadialSteps(const SkVector& v1, const SkVector& v2, SkScalar offset,
 *                           SkScalar* rotSin, SkScalar* rotCos, int* n) {
 *     const SkScalar kRecipPixelsPerArcSegment = 0.25f;
 *
 *     SkScalar rCos = v1.dot(v2);
 *     if (!SkIsFinite(rCos)) {
 *         return false;
 *     }
 *     SkScalar rSin = v1.cross(v2);
 *     if (!SkIsFinite(rSin)) {
 *         return false;
 *     }
 *     SkScalar theta = SkScalarATan2(rSin, rCos);
 *
 *     SkScalar floatSteps = SkScalarAbs(offset*theta*kRecipPixelsPerArcSegment);
 *     // limit the number of steps to at most max uint16_t (that's all we can index)
 *     // knock one value off the top to account for rounding
 *     if (floatSteps >= std::numeric_limits<uint16_t>::max()) {
 *         return false;
 *     }
 *     int steps = SkScalarRoundToInt(floatSteps);
 *
 *     SkScalar dTheta = steps > 0 ? theta / steps : 0;
 *     *rotSin = SkScalarSin(dTheta);
 *     *rotCos = SkScalarCos(dTheta);
 *     // Our offset may be so large that we end up with a tiny dTheta, in which case we
 *     // lose precision when computing rotSin and rotCos.
 *     if (steps > 0 && (*rotSin == 0 || *rotCos == 1)) {
 *         return false;
 *     }
 *     *n = steps;
 *     return true;
 * }
 * ```
 */
public fun skComputeRadialSteps(
  v1: SkVector,
  v2: SkVector,
  offset: SkScalar,
  rotSin: SkScalar?,
  rotCos: SkScalar?,
  n: Int?,
): Boolean {
  TODO("Implement skComputeRadialSteps")
}

/**
 * C++ original:
 * ```cpp
 * static bool left(const SkPoint& p0, const SkPoint& p1) {
 *     return p0.fX < p1.fX || (!(p0.fX > p1.fX) && p0.fY > p1.fY);
 * }
 * ```
 */
public fun left(p0: SkPoint, p1: SkPoint): Boolean {
  TODO("Implement left")
}

/**
 * C++ original:
 * ```cpp
 * static bool right(const SkPoint& p0, const SkPoint& p1) {
 *     return p0.fX > p1.fX || (!(p0.fX < p1.fX) && p0.fY < p1.fY);
 * }
 * ```
 */
public fun right(p0: SkPoint, p1: SkPoint): Boolean {
  TODO("Implement right")
}

/**
 * C++ original:
 * ```cpp
 * bool SkIsSimplePolygon(const SkPoint* polygon, int polygonSize) {
 *     if (polygonSize < 3) {
 *         return false;
 *     }
 *
 *     // If it's convex, it's simple
 *     if (SkIsConvexPolygon(polygon, polygonSize)) {
 *         return true;
 *     }
 *
 *     // practically speaking, it takes too long to process large polygons
 *     if (polygonSize > 2048) {
 *         return false;
 *     }
 *
 *     SkTDPQueue <Vertex, Vertex::Left> vertexQueue(polygonSize);
 *     for (int i = 0; i < polygonSize; ++i) {
 *         Vertex newVertex;
 *         if (!polygon[i].isFinite()) {
 *             return false;
 *         }
 *         newVertex.fPosition = polygon[i];
 *         newVertex.fIndex = i;
 *         newVertex.fPrevIndex = (i - 1 + polygonSize) % polygonSize;
 *         newVertex.fNextIndex = (i + 1) % polygonSize;
 *         newVertex.fFlags = 0;
 *         // The two edges adjacent to this vertex are the same, so polygon is not simple
 *         if (polygon[newVertex.fPrevIndex] == polygon[newVertex.fNextIndex]) {
 *             return false;
 *         }
 *         if (left(polygon[newVertex.fPrevIndex], polygon[i])) {
 *             newVertex.fFlags |= kPrevLeft_VertexFlag;
 *         }
 *         if (left(polygon[newVertex.fNextIndex], polygon[i])) {
 *             newVertex.fFlags |= kNextLeft_VertexFlag;
 *         }
 *         vertexQueue.insert(newVertex);
 *     }
 *
 *     // pop each vertex from the queue and generate events depending on
 *     // where it lies relative to its neighboring edges
 *     ActiveEdgeList sweepLine(polygonSize);
 *     while (vertexQueue.count() > 0) {
 *         const Vertex& v = vertexQueue.peek();
 *
 *         // both to the right -- insert both
 *         if (v.fFlags == 0) {
 *             if (!sweepLine.insert(v.fPosition, polygon[v.fPrevIndex], v.fIndex, v.fPrevIndex)) {
 *                 break;
 *             }
 *             if (!sweepLine.insert(v.fPosition, polygon[v.fNextIndex], v.fIndex, v.fNextIndex)) {
 *                 break;
 *             }
 *         // both to the left -- remove both
 *         } else if (v.fFlags == (kPrevLeft_VertexFlag | kNextLeft_VertexFlag)) {
 *             if (!sweepLine.remove(polygon[v.fPrevIndex], v.fPosition, v.fPrevIndex, v.fIndex)) {
 *                 break;
 *             }
 *             if (!sweepLine.remove(polygon[v.fNextIndex], v.fPosition, v.fNextIndex, v.fIndex)) {
 *                 break;
 *             }
 *         // one to left and right -- replace one with another
 *         } else {
 *             if (v.fFlags & kPrevLeft_VertexFlag) {
 *                 if (!sweepLine.replace(polygon[v.fPrevIndex], v.fPosition, polygon[v.fNextIndex],
 *                                        v.fPrevIndex, v.fIndex, v.fNextIndex)) {
 *                     break;
 *                 }
 *             } else {
 *                 SkASSERT(v.fFlags & kNextLeft_VertexFlag);
 *                 if (!sweepLine.replace(polygon[v.fNextIndex], v.fPosition, polygon[v.fPrevIndex],
 *                                        v.fNextIndex, v.fIndex, v.fPrevIndex)) {
 *                     break;
 *                 }
 *             }
 *         }
 *
 *         vertexQueue.pop();
 *     }
 *
 *     return (vertexQueue.count() == 0);
 * }
 * ```
 */
public fun skIsSimplePolygon(polygon: SkPoint?, polygonSize: Int): Boolean {
  TODO("Implement skIsSimplePolygon")
}

/**
 * C++ original:
 * ```cpp
 * static void setup_offset_edge(OffsetEdge* currEdge,
 *                               const SkPoint& endpoint0, const SkPoint& endpoint1,
 *                               uint16_t startIndex, uint16_t endIndex) {
 *     currEdge->fOffset.fP0 = endpoint0;
 *     currEdge->fOffset.fV = endpoint1 - endpoint0;
 *     currEdge->init(startIndex, endIndex);
 * }
 * ```
 */
public fun setupOffsetEdge(
  currEdge: OffsetEdge?,
  endpoint0: SkPoint,
  endpoint1: SkPoint,
  startIndex: UShort,
  endIndex: UShort,
) {
  TODO("Implement setupOffsetEdge")
}

/**
 * C++ original:
 * ```cpp
 * static bool is_reflex_vertex(const SkPoint* inputPolygonVerts, int winding, SkScalar offset,
 *                              uint16_t prevIndex, uint16_t currIndex, uint16_t nextIndex) {
 *     int side = compute_side(inputPolygonVerts[prevIndex],
 *                             inputPolygonVerts[currIndex] - inputPolygonVerts[prevIndex],
 *                             inputPolygonVerts[nextIndex]);
 *     // if reflex point, we need to add extra edges
 *     return (side*winding*offset < 0);
 * }
 * ```
 */
public fun isReflexVertex(
  inputPolygonVerts: SkPoint?,
  winding: Int,
  offset: SkScalar,
  prevIndex: UShort,
  currIndex: UShort,
  nextIndex: UShort,
): Boolean {
  TODO("Implement isReflexVertex")
}

/**
 * C++ original:
 * ```cpp
 * bool SkOffsetSimplePolygon(const SkPoint* inputPolygonVerts, int inputPolygonSize,
 *                            const SkRect& bounds, SkScalar offset,
 *                            SkTDArray<SkPoint>* offsetPolygon, SkTDArray<int>* polygonIndices) {
 *     if (inputPolygonSize < 3) {
 *         return false;
 *     }
 *
 *     // need to be able to represent all the vertices in the 16-bit indices
 *     if (inputPolygonSize >= std::numeric_limits<uint16_t>::max()) {
 *         return false;
 *     }
 *
 *     if (!SkIsFinite(offset)) {
 *         return false;
 *     }
 *
 *     // can't inset more than the half bounds of the polygon
 *     if (offset > std::min(SkTAbs(SkRectPriv::HalfWidth(bounds)),
 *                           SkTAbs(SkRectPriv::HalfHeight(bounds)))) {
 *         return false;
 *     }
 *
 *     // offsetting close to zero just returns the original poly
 *     if (SkScalarNearlyZero(offset)) {
 *         for (int i = 0; i < inputPolygonSize; ++i) {
 *             *offsetPolygon->append() = inputPolygonVerts[i];
 *             if (polygonIndices) {
 *                 *polygonIndices->append() = i;
 *             }
 *         }
 *         return true;
 *     }
 *
 *     // get winding direction
 *     int winding = SkGetPolygonWinding(inputPolygonVerts, inputPolygonSize);
 *     if (0 == winding) {
 *         return false;
 *     }
 *
 *     // build normals
 *     AutoSTMalloc<64, SkVector> normals(inputPolygonSize);
 *     unsigned int numEdges = 0;
 *     for (int currIndex = 0, prevIndex = inputPolygonSize - 1;
 *          currIndex < inputPolygonSize;
 *          prevIndex = currIndex, ++currIndex) {
 *         if (!inputPolygonVerts[currIndex].isFinite()) {
 *             return false;
 *         }
 *         int nextIndex = (currIndex + 1) % inputPolygonSize;
 *         if (!compute_offset_vector(inputPolygonVerts[currIndex], inputPolygonVerts[nextIndex],
 *                                    offset, winding, &normals[currIndex])) {
 *             return false;
 *         }
 *         if (currIndex > 0) {
 *             // if reflex point, we need to add extra edges
 *             if (is_reflex_vertex(inputPolygonVerts, winding, offset,
 *                                  prevIndex, currIndex, nextIndex)) {
 *                 SkScalar rotSin, rotCos;
 *                 int numSteps;
 *                 if (!SkComputeRadialSteps(normals[prevIndex], normals[currIndex], offset,
 *                                           &rotSin, &rotCos, &numSteps)) {
 *                     return false;
 *                 }
 *                 numEdges += std::max(numSteps, 1);
 *             }
 *         }
 *         numEdges++;
 *     }
 *     // finish up the edge counting
 *     if (is_reflex_vertex(inputPolygonVerts, winding, offset, inputPolygonSize-1, 0, 1)) {
 *         SkScalar rotSin, rotCos;
 *         int numSteps;
 *         if (!SkComputeRadialSteps(normals[inputPolygonSize-1], normals[0], offset,
 *                                   &rotSin, &rotCos, &numSteps)) {
 *             return false;
 *         }
 *         numEdges += std::max(numSteps, 1);
 *     }
 *
 *     // Make sure we don't overflow the max array count.
 *     // We shouldn't overflow numEdges, as SkComputeRadialSteps returns a max of 2^16-1,
 *     // and we have a max of 2^16-1 original vertices.
 *     if (numEdges > (unsigned int)std::numeric_limits<int32_t>::max()) {
 *         return false;
 *     }
 *
 *     // build initial offset edge list
 *     STArray<64, OffsetEdge> edgeData(numEdges);
 *     OffsetEdge* prevEdge = nullptr;
 *     for (int currIndex = 0, prevIndex = inputPolygonSize - 1;
 *          currIndex < inputPolygonSize;
 *          prevIndex = currIndex, ++currIndex) {
 *         int nextIndex = (currIndex + 1) % inputPolygonSize;
 *         // if reflex point, fill in curve
 *         if (is_reflex_vertex(inputPolygonVerts, winding, offset,
 *                              prevIndex, currIndex, nextIndex)) {
 *             SkScalar rotSin, rotCos;
 *             int numSteps;
 *             SkVector prevNormal = normals[prevIndex];
 *             if (!SkComputeRadialSteps(prevNormal, normals[currIndex], offset,
 *                                       &rotSin, &rotCos, &numSteps)) {
 *                 return false;
 *             }
 *             auto currEdge = edgeData.push_back_n(std::max(numSteps, 1));
 *             for (int i = 0; i < numSteps - 1; ++i) {
 *                 SkVector currNormal = SkVector::Make(prevNormal.fX*rotCos - prevNormal.fY*rotSin,
 *                                                      prevNormal.fY*rotCos + prevNormal.fX*rotSin);
 *                 setup_offset_edge(currEdge,
 *                                   inputPolygonVerts[currIndex] + prevNormal,
 *                                   inputPolygonVerts[currIndex] + currNormal,
 *                                   currIndex, currIndex);
 *                 prevNormal = currNormal;
 *                 currEdge->fPrev = prevEdge;
 *                 if (prevEdge) {
 *                     prevEdge->fNext = currEdge;
 *                 }
 *                 prevEdge = currEdge;
 *                 ++currEdge;
 *             }
 *             setup_offset_edge(currEdge,
 *                               inputPolygonVerts[currIndex] + prevNormal,
 *                               inputPolygonVerts[currIndex] + normals[currIndex],
 *                               currIndex, currIndex);
 *             currEdge->fPrev = prevEdge;
 *             if (prevEdge) {
 *                 prevEdge->fNext = currEdge;
 *             }
 *             prevEdge = currEdge;
 *         }
 *
 *         // Add the edge
 *         auto currEdge = edgeData.push_back_n(1);
 *         setup_offset_edge(currEdge,
 *                           inputPolygonVerts[currIndex] + normals[currIndex],
 *                           inputPolygonVerts[nextIndex] + normals[currIndex],
 *                           currIndex, nextIndex);
 *         currEdge->fPrev = prevEdge;
 *         if (prevEdge) {
 *             prevEdge->fNext = currEdge;
 *         }
 *         prevEdge = currEdge;
 *     }
 *     // close up the linked list
 *     SkASSERT(prevEdge);
 *     prevEdge->fNext = &edgeData[0];
 *     edgeData[0].fPrev = prevEdge;
 *
 *     // now clip edges
 *     SkASSERT(edgeData.size() == (int)numEdges);
 *     auto head = &edgeData[0];
 *     auto currEdge = head;
 *     unsigned int offsetVertexCount = numEdges;
 *     unsigned long long iterations = 0;
 *     unsigned long long maxIterations = (unsigned long long)(numEdges) * numEdges;
 *     while (head && prevEdge != currEdge && offsetVertexCount > 0) {
 *         ++iterations;
 *         // we should check each edge against each other edge at most once
 *         if (iterations > maxIterations) {
 *             return false;
 *         }
 *
 *         SkScalar s, t;
 *         SkPoint intersection;
 *         if (prevEdge->checkIntersection(currEdge, &intersection, &s, &t)) {
 *             // if new intersection is further back on previous inset from the prior intersection
 *             if (s < prevEdge->fTValue) {
 *                 // no point in considering this one again
 *                 remove_node(prevEdge, &head);
 *                 --offsetVertexCount;
 *                 // go back one segment
 *                 prevEdge = prevEdge->fPrev;
 *                 // we've already considered this intersection, we're done
 *             } else if (currEdge->fTValue > SK_ScalarMin &&
 *                        SkPointPriv::EqualsWithinTolerance(intersection,
 *                                                           currEdge->fIntersection,
 *                                                           1.0e-6f)) {
 *                 break;
 *             } else {
 *                 // add intersection
 *                 currEdge->fIntersection = intersection;
 *                 currEdge->fTValue = t;
 *                 currEdge->fIndex = prevEdge->fEnd;
 *
 *                 // go to next segment
 *                 prevEdge = currEdge;
 *                 currEdge = currEdge->fNext;
 *             }
 *         } else {
 *             // If there is no intersection, we want to minimize the distance between
 *             // the point where the segment lines cross and the segments themselves.
 *             OffsetEdge* prevPrevEdge = prevEdge->fPrev;
 *             OffsetEdge* currNextEdge = currEdge->fNext;
 *             SkScalar dist0 = currEdge->computeCrossingDistance(prevPrevEdge);
 *             SkScalar dist1 = prevEdge->computeCrossingDistance(currNextEdge);
 *             // if both lead to direct collision
 *             if (dist0 < 0 && dist1 < 0) {
 *                 // check first to see if either represent parts of one contour
 *                 SkPoint p1 = prevPrevEdge->fOffset.fP0 + prevPrevEdge->fOffset.fV;
 *                 bool prevSameContour = SkPointPriv::EqualsWithinTolerance(p1,
 *                                                                           prevEdge->fOffset.fP0);
 *                 p1 = currEdge->fOffset.fP0 + currEdge->fOffset.fV;
 *                 bool currSameContour = SkPointPriv::EqualsWithinTolerance(p1,
 *                                                                          currNextEdge->fOffset.fP0);
 *
 *                 // want to step along contour to find intersections rather than jump to new one
 *                 if (currSameContour && !prevSameContour) {
 *                     remove_node(currEdge, &head);
 *                     currEdge = currNextEdge;
 *                     --offsetVertexCount;
 *                     continue;
 *                 } else if (prevSameContour && !currSameContour) {
 *                     remove_node(prevEdge, &head);
 *                     prevEdge = prevPrevEdge;
 *                     --offsetVertexCount;
 *                     continue;
 *                 }
 *             }
 *
 *             // otherwise minimize collision distance along segment
 *             if (dist0 < dist1) {
 *                 remove_node(prevEdge, &head);
 *                 prevEdge = prevPrevEdge;
 *             } else {
 *                 remove_node(currEdge, &head);
 *                 currEdge = currNextEdge;
 *             }
 *             --offsetVertexCount;
 *         }
 *     }
 *
 *     // store all the valid intersections that aren't nearly coincident
 *     // TODO: look at the main algorithm and see if we can detect these better
 *     offsetPolygon->reset();
 *     if (!head || offsetVertexCount == 0 ||
 *         offsetVertexCount >= std::numeric_limits<uint16_t>::max()) {
 *         return false;
 *     }
 *
 *     static constexpr SkScalar kCleanupTolerance = 0.01f;
 *     offsetPolygon->reserve(offsetVertexCount);
 *     int currIndex = 0;
 *     *offsetPolygon->append() = head->fIntersection;
 *     if (polygonIndices) {
 *         *polygonIndices->append() = head->fIndex;
 *     }
 *     currEdge = head->fNext;
 *     while (currEdge != head) {
 *         if (!SkPointPriv::EqualsWithinTolerance(currEdge->fIntersection,
 *                                                 (*offsetPolygon)[currIndex],
 *                                                 kCleanupTolerance)) {
 *             *offsetPolygon->append() = currEdge->fIntersection;
 *             if (polygonIndices) {
 *                 *polygonIndices->append() = currEdge->fIndex;
 *             }
 *             currIndex++;
 *         }
 *         currEdge = currEdge->fNext;
 *     }
 *     // make sure the first and last points aren't coincident
 *     if (currIndex >= 1 &&
 *         SkPointPriv::EqualsWithinTolerance((*offsetPolygon)[0], (*offsetPolygon)[currIndex],
 *                                             kCleanupTolerance)) {
 *         offsetPolygon->pop_back();
 *         if (polygonIndices) {
 *             polygonIndices->pop_back();
 *         }
 *     }
 *
 *     // check winding of offset polygon (it should be same as the original polygon)
 *     SkScalar offsetWinding = SkGetPolygonWinding(offsetPolygon->begin(), offsetPolygon->size());
 *
 *     return (winding*offsetWinding > 0 &&
 *             SkIsSimplePolygon(offsetPolygon->begin(), offsetPolygon->size()));
 * }
 * ```
 */
public fun skOffsetSimplePolygon(
  inputPolygonVerts: SkPoint?,
  inputPolygonSize: Int,
  bounds: SkRect,
  offset: SkScalar,
  offsetPolygon: SkTDArray<SkPoint>?,
  polygonIndices: SkTDArray<Int>?,
): Boolean {
  TODO("Implement skOffsetSimplePolygon")
}

/**
 * C++ original:
 * ```cpp
 * static void compute_triangle_bounds(const SkPoint& p0, const SkPoint& p1, const SkPoint& p2,
 *                                     SkRect* bounds) {
 *     skvx::float4 min, max;
 *     min = max = skvx::float4(p0.fX, p0.fY, p0.fX, p0.fY);
 *     skvx::float4 xy(p1.fX, p1.fY, p2.fX, p2.fY);
 *     min = skvx::min(min, xy);
 *     max = skvx::max(max, xy);
 *     bounds->setLTRB(std::min(min[0], min[2]), std::min(min[1], min[3]),
 *                     std::max(max[0], max[2]), std::max(max[1], max[3]));
 * }
 * ```
 */
public fun computeTriangleBounds(
  p0: SkPoint,
  p1: SkPoint,
  p2: SkPoint,
  bounds: SkRect?,
) {
  TODO("Implement computeTriangleBounds")
}

/**
 * C++ original:
 * ```cpp
 * static void reclassify_vertex(TriangulationVertex* p, const SkPoint* polygonVerts,
 *                               int winding, ReflexHash* reflexHash,
 *                               SkTInternalLList<TriangulationVertex>* convexList) {
 *     if (TriangulationVertex::VertexType::kReflex == p->fVertexType) {
 *         SkVector v0 = p->fPosition - polygonVerts[p->fPrevIndex];
 *         SkVector v1 = polygonVerts[p->fNextIndex] - p->fPosition;
 *         if (winding*v0.cross(v1) > SK_ScalarNearlyZero*SK_ScalarNearlyZero) {
 *             p->fVertexType = TriangulationVertex::VertexType::kConvex;
 *             reflexHash->remove(p);
 *             p->fPrev = p->fNext = nullptr;
 *             convexList->addToTail(p);
 *         }
 *     }
 * }
 * ```
 */
public fun reclassifyVertex(
  p: TriangulationVertex?,
  polygonVerts: SkPoint?,
  winding: Int,
  reflexHash: ReflexHash?,
  convexList: SkTInternalLList<TriangulationVertex>?,
) {
  TODO("Implement reclassifyVertex")
}

/**
 * C++ original:
 * ```cpp
 * bool SkTriangulateSimplePolygon(const SkPoint* polygonVerts, uint16_t* indexMap, int polygonSize,
 *                                 SkTDArray<uint16_t>* triangleIndices) {
 *     if (polygonSize < 3) {
 *         return false;
 *     }
 *     // need to be able to represent all the vertices in the 16-bit indices
 *     if (polygonSize >= std::numeric_limits<uint16_t>::max()) {
 *         return false;
 *     }
 *
 *     // get bounds
 *     SkRect bounds;
 *     if (!bounds.setBoundsCheck({polygonVerts, (size_t)polygonSize})) {
 *         return false;
 *     }
 *     // get winding direction
 *     // TODO: we do this for all the polygon routines -- might be better to have the client
 *     // compute it and pass it in
 *     int winding = SkGetPolygonWinding(polygonVerts, polygonSize);
 *     if (0 == winding) {
 *         return false;
 *     }
 *
 *     // Set up vertices
 *     AutoSTArray<64, TriangulationVertex> triangulationVertices(polygonSize);
 *     int prevIndex = polygonSize - 1;
 *     SkVector v0 = polygonVerts[0] - polygonVerts[prevIndex];
 *     for (int currIndex = 0; currIndex < polygonSize; ++currIndex) {
 *         int nextIndex = (currIndex + 1) % polygonSize;
 *
 *         triangulationVertices[currIndex] = TriangulationVertex{};
 *         triangulationVertices[currIndex].fPosition = polygonVerts[currIndex];
 *         triangulationVertices[currIndex].fIndex = currIndex;
 *         triangulationVertices[currIndex].fPrevIndex = prevIndex;
 *         triangulationVertices[currIndex].fNextIndex = nextIndex;
 *         SkVector v1 = polygonVerts[nextIndex] - polygonVerts[currIndex];
 *         if (winding*v0.cross(v1) > SK_ScalarNearlyZero*SK_ScalarNearlyZero) {
 *             triangulationVertices[currIndex].fVertexType = TriangulationVertex::VertexType::kConvex;
 *         } else {
 *             triangulationVertices[currIndex].fVertexType = TriangulationVertex::VertexType::kReflex;
 *         }
 *
 *         prevIndex = currIndex;
 *         v0 = v1;
 *     }
 *
 *     // Classify initial vertices into a list of convex vertices and a hash of reflex vertices
 *     // TODO: possibly sort the convexList in some way to get better triangles
 *     SkTInternalLList<TriangulationVertex> convexList;
 *     ReflexHash reflexHash;
 *     if (!reflexHash.init(bounds, polygonSize)) {
 *         return false;
 *     }
 *     prevIndex = polygonSize - 1;
 *     for (int currIndex = 0; currIndex < polygonSize; prevIndex = currIndex, ++currIndex) {
 *         TriangulationVertex::VertexType currType = triangulationVertices[currIndex].fVertexType;
 *         if (TriangulationVertex::VertexType::kConvex == currType) {
 *             int nextIndex = (currIndex + 1) % polygonSize;
 *             TriangulationVertex::VertexType prevType = triangulationVertices[prevIndex].fVertexType;
 *             TriangulationVertex::VertexType nextType = triangulationVertices[nextIndex].fVertexType;
 *             // We prioritize clipping vertices with neighboring reflex vertices.
 *             // The intent here is that it will cull reflex vertices more quickly.
 *             if (TriangulationVertex::VertexType::kReflex == prevType ||
 *                 TriangulationVertex::VertexType::kReflex == nextType) {
 *                 convexList.addToHead(&triangulationVertices[currIndex]);
 *             } else {
 *                 convexList.addToTail(&triangulationVertices[currIndex]);
 *             }
 *         } else {
 *             // We treat near collinear vertices as reflex
 *             reflexHash.add(&triangulationVertices[currIndex]);
 *         }
 *     }
 *
 *     // The general concept: We are trying to find three neighboring vertices where
 *     // no other vertex lies inside the triangle (an "ear"). If we find one, we clip
 *     // that ear off, and then repeat on the new polygon. Once we get down to three vertices
 *     // we have triangulated the entire polygon.
 *     // In the worst case this is an n^2 algorithm. We can cut down the search space somewhat by
 *     // noting that only convex vertices can be potential ears, and we only need to check whether
 *     // any reflex vertices lie inside the ear.
 *     triangleIndices->reserve(triangleIndices->size() + 3 * (polygonSize - 2));
 *     int vertexCount = polygonSize;
 *     while (vertexCount > 3) {
 *         bool success = false;
 *         TriangulationVertex* earVertex = nullptr;
 *         TriangulationVertex* p0 = nullptr;
 *         TriangulationVertex* p2 = nullptr;
 *         // find a convex vertex to clip
 *         for (SkTInternalLList<TriangulationVertex>::Iter convexIter = convexList.begin();
 *              convexIter != convexList.end(); ++convexIter) {
 *             earVertex = *convexIter;
 *             SkASSERT(TriangulationVertex::VertexType::kReflex != earVertex->fVertexType);
 *
 *             p0 = &triangulationVertices[earVertex->fPrevIndex];
 *             p2 = &triangulationVertices[earVertex->fNextIndex];
 *
 *             // see if any reflex vertices are inside the ear
 *             bool failed = reflexHash.checkTriangle(p0->fPosition, earVertex->fPosition,
 *                                                    p2->fPosition, p0->fIndex, p2->fIndex);
 *             if (failed) {
 *                 continue;
 *             }
 *
 *             // found one we can clip
 *             success = true;
 *             break;
 *         }
 *         // If we can't find any ears to clip, this probably isn't a simple polygon
 *         if (!success) {
 *             return false;
 *         }
 *
 *         // add indices
 *         auto indices = triangleIndices->append(3);
 *         indices[0] = indexMap[p0->fIndex];
 *         indices[1] = indexMap[earVertex->fIndex];
 *         indices[2] = indexMap[p2->fIndex];
 *
 *         // clip the ear
 *         convexList.remove(earVertex);
 *         --vertexCount;
 *
 *         // reclassify reflex verts
 *         p0->fNextIndex = earVertex->fNextIndex;
 *         reclassify_vertex(p0, polygonVerts, winding, &reflexHash, &convexList);
 *
 *         p2->fPrevIndex = earVertex->fPrevIndex;
 *         reclassify_vertex(p2, polygonVerts, winding, &reflexHash, &convexList);
 *     }
 *
 *     // output indices
 *     for (SkTInternalLList<TriangulationVertex>::Iter vertexIter = convexList.begin();
 *          vertexIter != convexList.end(); ++vertexIter) {
 *         TriangulationVertex* vertex = *vertexIter;
 *         *triangleIndices->append() = indexMap[vertex->fIndex];
 *     }
 *
 *     return true;
 * }
 * ```
 */
public fun skTriangulateSimplePolygon(
  polygonVerts: SkPoint?,
  indexMap: UShort?,
  polygonSize: Int,
  triangleIndices: SkTDArray<UShort>?,
): Boolean {
  TODO("Implement skTriangulateSimplePolygon")
}

/**
 * C++ original:
 * ```cpp
 * static inline unsigned nib2byte(unsigned n)
 * {
 *     SkASSERT((n & ~0xF) == 0);
 *     return (n << 4) | n;
 * }
 * ```
 */
public fun nib2byte(n: UInt): UInt {
  TODO("Implement nib2byte")
}

/**
 * C++ original:
 * ```cpp
 * static bool compute_normal(const SkPoint& p0, const SkPoint& p1, SkScalar dir,
 *                            SkVector* newNormal) {
 *     SkVector normal;
 *     // compute perpendicular
 *     normal.fX = p0.fY - p1.fY;
 *     normal.fY = p1.fX - p0.fX;
 *     normal *= dir;
 *     if (!normal.normalize()) {
 *         return false;
 *     }
 *     *newNormal = normal;
 *     return true;
 * }
 * ```
 */
public fun computeNormal(
  p0: SkPoint,
  p1: SkPoint,
  dir: SkScalar,
  newNormal: SkVector?,
): Boolean {
  TODO("Implement computeNormal")
}

/**
 * C++ original:
 * ```cpp
 * static bool duplicate_pt(const SkPoint& p0, const SkPoint& p1) {
 *     static constexpr SkScalar kClose = (SK_Scalar1 / 16);
 *     static constexpr SkScalar kCloseSqd = kClose * kClose;
 *
 *     SkScalar distSq = SkPointPriv::DistanceToSqd(p0, p1);
 *     return distSq < kCloseSqd;
 * }
 * ```
 */
public fun duplicatePt(p0: SkPoint, p1: SkPoint): Boolean {
  TODO("Implement duplicatePt")
}

/**
 * C++ original:
 * ```cpp
 * static SkScalar perp_dot(const SkPoint& p0, const SkPoint& p1, const SkPoint& p2) {
 *     SkVector v0 = p1 - p0;
 *     SkVector v1 = p2 - p1;
 *     return v0.cross(v1);
 * }
 * ```
 */
public fun perpDot(
  p0: SkPoint,
  p1: SkPoint,
  p2: SkPoint,
): SkScalar {
  TODO("Implement perpDot")
}

/**
 * C++ original:
 * ```cpp
 * static void sanitize_point(const SkPoint& in, SkPoint* out) {
 *     out->fX = SkScalarRoundToScalar(16.f*in.fX)*0.0625f;
 *     out->fY = SkScalarRoundToScalar(16.f*in.fY)*0.0625f;
 * }
 * ```
 */
public fun sanitizePoint(`in`: SkPoint, `out`: SkPoint?) {
  TODO("Implement sanitizePoint")
}

/**
 * C++ original:
 * ```cpp
 * static inline void spancpy(SkSpan<SkPoint> dst, SkSpan<const SkPoint> src) {
 *     SkASSERT(dst.size() >= src.size());
 *     for (size_t i = 0; i < src.size(); ++i) {
 *         dst[i] = src[i];
 *     }
 * }
 * ```
 */
public fun spancpy(dst: SkSpan<SkPoint>, src: SkSpan<SkPoint>) {
  TODO("Implement spancpy")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkVertices> SkShadowTessellator::MakeAmbient(const SkPath& path, const SkMatrix& ctm,
 *                                                    const SkPoint3& zPlane, bool transparent) {
 *     if (!ctm.mapRect(path.getBounds()).isFinite() || !zPlane.isFinite()) {
 *         return nullptr;
 *     }
 *     SkAmbientShadowTessellator ambientTess(path, ctm, zPlane, transparent);
 *     return ambientTess.releaseVertices();
 * }
 * ```
 */
public fun makeAmbient(
  path: SkPath,
  ctm: SkMatrix,
  zPlane: SkPoint3,
  transparent: Boolean,
): SkSp<SkVertices> {
  TODO("Implement makeAmbient")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkVertices> SkShadowTessellator::MakeSpot(const SkPath& path, const SkMatrix& ctm,
 *                                                 const SkPoint3& zPlane, const SkPoint3& lightPos,
 *                                                 SkScalar lightRadius,  bool transparent,
 *                                                 bool directional) {
 *     if (!ctm.mapRect(path.getBounds()).isFinite() || !zPlane.isFinite() ||
 *         !lightPos.isFinite() || !(lightPos.fZ >= SK_ScalarNearlyZero) ||
 *         !SkIsFinite(lightRadius) || !(lightRadius >= SK_ScalarNearlyZero)) {
 *         return nullptr;
 *     }
 *     SkSpotShadowTessellator spotTess(path, ctm, zPlane, lightPos, lightRadius, transparent,
 *                                      directional);
 *     return spotTess.releaseVertices();
 * }
 * ```
 */
public fun makeSpot(
  path: SkPath,
  ctm: SkMatrix,
  zPlane: SkPoint3,
  lightPos: SkPoint3,
  lightRadius: SkScalar,
  transparent: Boolean,
  directional: Boolean,
): SkSp<SkVertices> {
  TODO("Implement makeSpot")
}

/**
 * C++ original:
 * ```cpp
 * uint64_t resource_cache_shared_id() {
 *     return 0x2020776f64616873llu;  // 'shadow  '
 * }
 * ```
 */
public fun resourceCacheSharedId(): ULong {
  TODO("Implement resourceCacheSharedId")
}

/**
 * C++ original:
 * ```cpp
 * template <typename FACTORY>
 * bool FindVisitor(const SkResourceCache::Rec& baseRec, void* ctx) {
 *     FindContext<FACTORY>* findContext = (FindContext<FACTORY>*)ctx;
 *     const CachedTessellationsRec& rec = static_cast<const CachedTessellationsRec&>(baseRec);
 *     findContext->fVertices =
 *             rec.find(*findContext->fFactory, *findContext->fViewMatrix, &findContext->fTranslate);
 *     if (findContext->fVertices) {
 *         return true;
 *     }
 *     // We ref the tessellations and let the cache destroy the Rec. Once the tessellations have been
 *     // manipulated we will add a new Rec.
 *     findContext->fTessellationsOnFailure = rec.refTessellations();
 *     return false;
 * }
 * ```
 */
public fun <FACTORY> findVisitor(baseRec: SkResourceCache.Rec, ctx: Unit?): Boolean {
  TODO("Implement findVisitor")
}

/**
 * C++ original:
 * ```cpp
 * static bool tilted(const SkPoint3& zPlaneParams) {
 *     return !SkScalarNearlyZero(zPlaneParams.fX) || !SkScalarNearlyZero(zPlaneParams.fY);
 * }
 * ```
 */
public fun tilted(zPlaneParams: SkPoint3): Boolean {
  TODO("Implement tilted")
}

/**
 * C++ original:
 * ```cpp
 * static bool fill_shadow_rec(const SkPath& path, const SkPoint3& zPlaneParams,
 *                             const SkPoint3& lightPos, SkScalar lightRadius,
 *                             SkColor ambientColor, SkColor spotColor,
 *                             uint32_t flags, const SkMatrix& ctm, SkDrawShadowRec* rec) {
 *     SkPoint pt = { lightPos.fX, lightPos.fY };
 *     if (!SkToBool(flags & kDirectionalLight_ShadowFlag)) {
 *         // If light position is in device space, need to transform to local space
 *         // before applying to SkCanvas.
 *         SkMatrix inverse;
 *         if (!ctm.invert(&inverse)) {
 *             return false;
 *         }
 *         pt = inverse.mapPoint(pt);
 *     }
 *
 *     rec->fZPlaneParams   = zPlaneParams;
 *     rec->fLightPos       = { pt.fX, pt.fY, lightPos.fZ };
 *     rec->fLightRadius    = lightRadius;
 *     rec->fAmbientColor   = ambientColor;
 *     rec->fSpotColor      = spotColor;
 *     rec->fFlags          = flags;
 *
 *     return true;
 * }
 * ```
 */
public fun fillShadowRec(
  path: SkPath,
  zPlaneParams: SkPoint3,
  lightPos: SkPoint3,
  lightRadius: SkScalar,
  ambientColor: SkColor,
  spotColor: SkColor,
  flags: UInt,
  ctm: SkMatrix,
  rec: SkDrawShadowRec?,
): Boolean {
  TODO("Implement fillShadowRec")
}

/**
 * C++ original:
 * ```cpp
 * static bool validate_rec(const SkDrawShadowRec& rec) {
 *     return rec.fLightPos.isFinite() && rec.fZPlaneParams.isFinite() &&
 *            SkIsFinite(rec.fLightRadius);
 * }
 * ```
 */
public fun validateRec(rec: SkDrawShadowRec): Boolean {
  TODO("Implement validateRec")
}

/**
 * C++ original:
 * ```cpp
 * SkCTFontSmoothBehavior SkCTFontGetSmoothBehavior() {
 *     static SkCTFontSmoothBehavior gSmoothBehavior = []{
 *         uint32_t noSmoothBitmap[16][16] = {};
 *         uint32_t smoothBitmap[16][16] = {};
 *
 *         SkUniqueCFRef<CGColorSpaceRef> colorspace(CGColorSpaceCreateDeviceRGB());
 *         SkUniqueCFRef<CGContextRef> noSmoothContext(
 *                 CGBitmapContextCreate(&noSmoothBitmap, 16, 16, 8, 16*4,
 *                                       colorspace.get(), kBitmapInfoRGB));
 *         SkUniqueCFRef<CGContextRef> smoothContext(
 *                 CGBitmapContextCreate(&smoothBitmap, 16, 16, 8, 16*4,
 *                                       colorspace.get(), kBitmapInfoRGB));
 *
 *         SkUniqueCFRef<CFDataRef> data(CFDataCreateWithBytesNoCopy(
 *                 kCFAllocatorDefault, kSpiderSymbol_ttf, std::size(kSpiderSymbol_ttf),
 *                 kCFAllocatorNull));
 *         SkUniqueCFRef<CTFontDescriptorRef> desc(
 *                 CTFontManagerCreateFontDescriptorFromData(data.get()));
 *         SkUniqueCFRef<CTFontRef> ctFont(CTFontCreateWithFontDescriptor(desc.get(), 16, nullptr));
 *         SkASSERT(ctFont);
 *
 *         CGContextSetShouldSmoothFonts(noSmoothContext.get(), false);
 *         CGContextSetShouldAntialias(noSmoothContext.get(), true);
 *         CGContextSetTextDrawingMode(noSmoothContext.get(), kCGTextFill);
 *         CGContextSetGrayFillColor(noSmoothContext.get(), 1, 1);
 *
 *         CGContextSetShouldSmoothFonts(smoothContext.get(), true);
 *         CGContextSetShouldAntialias(smoothContext.get(), true);
 *         CGContextSetTextDrawingMode(smoothContext.get(), kCGTextFill);
 *         CGContextSetGrayFillColor(smoothContext.get(), 1, 1);
 *
 *         CGPoint point = CGPointMake(0, 3);
 *         CGGlyph spiderGlyph = 3;
 *         CTFontDrawGlyphs(ctFont.get(), &spiderGlyph, &point, 1, noSmoothContext.get());
 *         CTFontDrawGlyphs(ctFont.get(), &spiderGlyph, &point, 1, smoothContext.get());
 *
 *         // For debugging.
 *         //SkUniqueCFRef<CGImageRef> image(CGBitmapContextCreateImage(noSmoothContext()));
 *         //SkUniqueCFRef<CGImageRef> image(CGBitmapContextCreateImage(smoothContext()));
 *
 *         SkCTFontSmoothBehavior smoothBehavior = SkCTFontSmoothBehavior::none;
 *         for (int x = 0; x < 16; ++x) {
 *             for (int y = 0; y < 16; ++y) {
 *                 uint32_t smoothPixel = smoothBitmap[x][y];
 *                 uint32_t r = (smoothPixel >> 16) & 0xFF;
 *                 uint32_t g = (smoothPixel >>  8) & 0xFF;
 *                 uint32_t b = (smoothPixel >>  0) & 0xFF;
 *                 if (r != g || r != b) {
 *                     return SkCTFontSmoothBehavior::subpixel;
 *                 }
 *                 if (noSmoothBitmap[x][y] != smoothPixel) {
 *                     smoothBehavior = SkCTFontSmoothBehavior::some;
 *                 }
 *             }
 *         }
 *         return smoothBehavior;
 *     }();
 *     return gSmoothBehavior;
 * }
 * ```
 */
public fun skCTFontGetSmoothBehavior(): SkCTFontSmoothBehavior {
  TODO("Implement skCTFontGetSmoothBehavior")
}

/**
 * C++ original:
 * ```cpp
 * SkCTFontWeightMapping& SkCTFontGetNSFontWeightMapping() {
 *     // In the event something goes wrong finding the real values, use this mapping.
 *     static constexpr CGFloat defaultNSFontWeights[] =
 *         { -1.00, -0.80, -0.60, -0.40, 0.00, 0.23, 0.30, 0.40, 0.56, 0.62, 1.00 };
 *
 *     // Declarations in <AppKit/AppKit.h> on macOS, <UIKit/UIKit.h> on iOS
 * #ifdef SK_BUILD_FOR_MAC
 * #  define SK_KIT_FONT_WEIGHT_PREFIX "NS"
 * #endif
 * #ifdef SK_BUILD_FOR_IOS
 * #  define SK_KIT_FONT_WEIGHT_PREFIX "UI"
 * #endif
 *     static constexpr const char* nsFontWeightNames[] = {
 *         SK_KIT_FONT_WEIGHT_PREFIX "FontWeightUltraLight",
 *         SK_KIT_FONT_WEIGHT_PREFIX "FontWeightThin",
 *         SK_KIT_FONT_WEIGHT_PREFIX "FontWeightLight",
 *         SK_KIT_FONT_WEIGHT_PREFIX "FontWeightRegular",
 *         SK_KIT_FONT_WEIGHT_PREFIX "FontWeightMedium",
 *         SK_KIT_FONT_WEIGHT_PREFIX "FontWeightSemibold",
 *         SK_KIT_FONT_WEIGHT_PREFIX "FontWeightBold",
 *         SK_KIT_FONT_WEIGHT_PREFIX "FontWeightHeavy",
 *         SK_KIT_FONT_WEIGHT_PREFIX "FontWeightBlack",
 *     };
 *     static_assert(std::size(nsFontWeightNames) == 9, "");
 *
 *     static CGFloat nsFontWeights[11];
 *     static const CGFloat (*selectedNSFontWeights)[11] = &defaultNSFontWeights;
 *     static SkOnce once;
 *     once([&] {
 *         size_t i = 0;
 *         nsFontWeights[i++] = -1.00;
 *         for (const char* nsFontWeightName : nsFontWeightNames) {
 *             void* nsFontWeightValuePtr = dlsym(RTLD_DEFAULT, nsFontWeightName);
 *             if (nsFontWeightValuePtr) {
 *                 nsFontWeights[i++] = *(static_cast<CGFloat*>(nsFontWeightValuePtr));
 *             } else {
 *                 return;
 *             }
 *         }
 *         nsFontWeights[i++] = 1.00;
 *         selectedNSFontWeights = &nsFontWeights;
 *     });
 *     return *selectedNSFontWeights;
 * }
 * ```
 */
public fun skCTFontGetNSFontWeightMapping(): Int {
  TODO("Implement skCTFontGetNSFontWeightMapping")
}

/**
 * C++ original:
 * ```cpp
 * SkCTFontWeightMapping& SkCTFontGetDataFontWeightMapping() {
 *     // In the event something goes wrong finding the real values, use this mapping.
 *     // These were the values from macOS 10.13 to 10.15.
 *     static constexpr CGFloat defaultDataFontWeights[] =
 *         { -1.00, -0.70, -0.50, -0.23, 0.00, 0.20, 0.30, 0.40, 0.60, 0.80, 1.00 };
 *
 *     static const CGFloat (*selectedDataFontWeights)[11] = &defaultDataFontWeights;
 *     static CGFloat dataFontWeights[11];
 *     static SkOnce once;
 *     once([&] {
 *         constexpr size_t dataSize = std::size(kSpiderSymbol_ttf);
 *         sk_sp<SkData> data = SkData::MakeWithCopy(kSpiderSymbol_ttf, dataSize);
 *         const SkSFNTHeader* sfntHeader = reinterpret_cast<const SkSFNTHeader*>(data->data());
 *         const SkSFNTHeader::TableDirectoryEntry* tableEntry =
 *             SkTAfter<const SkSFNTHeader::TableDirectoryEntry>(sfntHeader);
 *         const SkSFNTHeader::TableDirectoryEntry* os2TableEntry = nullptr;
 *         int numTables = SkEndian_SwapBE16(sfntHeader->numTables);
 *         for (int tableEntryIndex = 0; tableEntryIndex < numTables; ++tableEntryIndex) {
 *             if (SkOTTableOS2::TAG == tableEntry[tableEntryIndex].tag) {
 *                 os2TableEntry = tableEntry + tableEntryIndex;
 *                 break;
 *             }
 *         }
 *         if (!os2TableEntry) {
 *             return;
 *         }
 *         size_t os2TableOffset = SkEndian_SwapBE32(os2TableEntry->offset);
 *         SkOTTableOS2_V0* os2Table = SkTAddOffset<SkOTTableOS2_V0>(data->writable_data(),
 *                                                                   os2TableOffset);
 *
 *         // On macOS 15.0 and later, CoreText will pin a usWeightClass of 0 to 1.
 *         // Instead, get the value at 11 and then when finished project back to 0.
 *         // Cannot use 1-10 as CoreText considers these as 100-1000 as in OS/2 version A.
 *         constexpr int kLowestUsefulWeightClassValue = 11;
 *         CGFloat previousWeight = -CGFLOAT_MAX;
 *         for (int i = 0; i < 11; ++i) {
 *             if (i == 0) {
 *                 os2Table->usWeightClass.value = SkEndian_SwapBE16(kLowestUsefulWeightClassValue);
 *             } else {
 *                 os2Table->usWeightClass.value = SkEndian_SwapBE16(i * 100);
 *             }
 *
 *             // On macOS 10.14 and earlier it appears that the CFDataGetBytePtr is used somehow in
 *             // font caching. Creating a slightly modified font with data at the same address seems
 *             // to in some ways act like a font previously created at that address. As a result,
 *             // always make a copy of the data.
 *             SkUniqueCFRef<CFDataRef> cfData(
 *                     CFDataCreate(kCFAllocatorDefault, (const UInt8 *)data->data(), data->size()));
 *             if (!cfData) {
 *                 return;
 *             }
 *             SkUniqueCFRef<CTFontDescriptorRef> desc(
 *                     CTFontManagerCreateFontDescriptorFromData(cfData.get()));
 *             if (!desc) {
 *                 return;
 *             }
 *
 *             // On macOS 10.14 and earlier, the CTFontDescriptorRef returned from
 *             // CTFontManagerCreateFontDescriptorFromData is incomplete and does not have the
 *             // correct traits. It is necessary to create the CTFont and then get the descriptor
 *             // off of it.
 *             SkUniqueCFRef<CTFontRef> ctFont(CTFontCreateWithFontDescriptor(desc.get(), 9, nullptr));
 *             if (!ctFont) {
 *                 return;
 *             }
 *             SkUniqueCFRef<CTFontDescriptorRef> desc2(CTFontCopyFontDescriptor(ctFont.get()));
 *             if (!desc2) {
 *                 return;
 *             }
 *
 *             SkUniqueCFRef<CFTypeRef> traitsRef(
 *                     CTFontDescriptorCopyAttribute(desc2.get(), kCTFontTraitsAttribute));
 *             if (!traitsRef || CFGetTypeID(traitsRef.get()) != CFDictionaryGetTypeID()) {
 *                 return;
 *             }
 *             CFDictionaryRef fontTraitsDict = static_cast<CFDictionaryRef>(traitsRef.get());
 *
 *             CFTypeRef weightRef;
 *             if (!CFDictionaryGetValueIfPresent(fontTraitsDict, kCTFontWeightTrait, &weightRef) ||
 *                 !weightRef)
 *             {
 *                 return;
 *             }
 *
 *             // It is possible there is a kCTFontWeightTrait entry, but it is not a CFNumberRef.
 *             // This is usually due to a bug with the handling of 0, so set the default to 0.
 *             // See https://crbug.com/1372420
 *             CGFloat weight = 0;
 *             if (CFGetTypeID(weightRef) == CFNumberGetTypeID()) {
 *                 CFNumberRef weightNumber = static_cast<CFNumberRef>(weightRef);
 *                 if (!CFNumberIsFloatType(weightNumber) ||
 *                     !CFNumberGetValue(weightNumber, kCFNumberCGFloatType, &weight))
 *                 {
 *                     // CFNumberGetValue may modify `weight` even when returning `false`.
 *                     weight = 0;
 *                 }
 *             }
 *
 *             // It is expected that the weights will be strictly monotonically increasing.
 *             if (weight <= previousWeight) {
 *                 return;
 *             }
 *             previousWeight = weight;
 *             dataFontWeights[i] = weight;
 *         }
 *         CGFloat slope = (dataFontWeights[1] - dataFontWeights[0]) /
 *                         (100 - kLowestUsefulWeightClassValue);
 *         dataFontWeights[0] = dataFontWeights[1] - (slope * (100 - 0));
 *         selectedDataFontWeights = &dataFontWeights;
 *     });
 *     return *selectedDataFontWeights;
 * }
 * ```
 */
public fun skCTFontGetDataFontWeightMapping(): Int {
  TODO("Implement skCTFontGetDataFontWeightMapping")
}

/**
 * C++ original:
 * ```cpp
 * static void add_opsz_attr(CFMutableDictionaryRef attr, double opsz) {
 *     SkUniqueCFRef<CFNumberRef> opszValueNumber(
 *         CFNumberCreate(kCFAllocatorDefault, kCFNumberDoubleType, &opsz));
 *     // Avoid using kCTFontOpticalSizeAttribute directly
 *     CFStringRef SkCTFontOpticalSizeAttribute = CFSTR("NSCTFontOpticalSizeAttribute");
 *     CFDictionarySetValue(attr, SkCTFontOpticalSizeAttribute, opszValueNumber.get());
 * }
 * ```
 */
public fun addOpszAttr(attr: CFMutableDictionaryRef, opsz: Double) {
  TODO("Implement addOpszAttr")
}

/**
 * C++ original:
 * ```cpp
 * static void add_notrak_attr(CFMutableDictionaryRef attr) {
 *     int zero = 0;
 *     SkUniqueCFRef<CFNumberRef> unscaledTrackingNumber(
 *         CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, &zero));
 *     CFStringRef SkCTFontUnscaledTrackingAttribute = CFSTR("NSCTFontUnscaledTrackingAttribute");
 *     CFDictionarySetValue(attr, SkCTFontUnscaledTrackingAttribute, unscaledTrackingNumber.get());
 * }
 * ```
 */
public fun addNotrakAttr(attr: CFMutableDictionaryRef) {
  TODO("Implement addNotrakAttr")
}

/**
 * C++ original:
 * ```cpp
 * SkUniqueCFRef<CTFontRef> SkCTFontCreateExactCopy(CTFontRef baseFont, CGFloat textSize,
 *                                                  OpszVariation opszVariation)
 * {
 *     SkUniqueCFRef<CFMutableDictionaryRef> attr(
 *     CFDictionaryCreateMutable(kCFAllocatorDefault, 0,
 *                               &kCFTypeDictionaryKeyCallBacks,
 *                               &kCFTypeDictionaryValueCallBacks));
 *
 *     if (opszVariation.isSet) {
 *         add_opsz_attr(attr.get(), opszVariation.value);
 *     } else {
 *         // On (at least) 10.10 though 10.14 the default system font was SFNSText/SFNSDisplay.
 *         // The CTFont is backed by both; optical size < 20 means SFNSText else SFNSDisplay.
 *         // On at least 10.11 the glyph ids in these fonts became non-interchangable.
 *         // To keep glyph ids stable over size changes, preserve the optical size.
 *         // In 10.15 this was replaced with use of variable fonts with an opsz axis.
 *         // A CTFont backed by multiple fonts picked by opsz where the multiple backing fonts are
 *         // variable fonts with opsz axis and non-interchangeable glyph ids would break the
 *         // opsz.isSet branch above, but hopefully that never happens.
 *         // See https://crbug.com/524646 .
 *         CFStringRef SkCTFontOpticalSizeAttribute = CFSTR("NSCTFontOpticalSizeAttribute");
 *         SkUniqueCFRef<CFTypeRef> opsz(CTFontCopyAttribute(baseFont, SkCTFontOpticalSizeAttribute));
 *         double opsz_val;
 *         if (!opsz ||
 *             CFGetTypeID(opsz.get()) != CFNumberGetTypeID() ||
 *             !CFNumberGetValue(static_cast<CFNumberRef>(opsz.get()),kCFNumberDoubleType,&opsz_val) ||
 *             opsz_val <= 0)
 *         {
 *             opsz_val = CTFontGetSize(baseFont);
 *         }
 *         add_opsz_attr(attr.get(), opsz_val);
 *     }
 *     add_notrak_attr(attr.get());
 *
 *     SkUniqueCFRef<CTFontDescriptorRef> desc(CTFontDescriptorCreateWithAttributes(attr.get()));
 *
 *     return SkUniqueCFRef<CTFontRef>(
 *             CTFontCreateCopyWithAttributes(baseFont, textSize, nullptr, desc.get()));
 * }
 * ```
 */
public fun skCTFontCreateExactCopy(
  baseFont: CTFontRef,
  textSize: CGFloat,
  opszVariation: OpszVariation,
): SkUniqueCFRef<CTFontRef> {
  TODO("Implement skCTFontCreateExactCopy")
}

/**
 * C++ original:
 * ```cpp
 * static CGBitmapInfo compute_cgalpha_info_rgba(SkAlphaType at) {
 *     CGBitmapInfo info = kCGBitmapByteOrder32Big;
 *     switch (at) {
 *         case kUnknown_SkAlphaType:                                          break;
 *         case kOpaque_SkAlphaType:   info |= kCGImageAlphaNoneSkipLast;      break;
 *         case kPremul_SkAlphaType:   info |= kCGImageAlphaPremultipliedLast; break;
 *         case kUnpremul_SkAlphaType: info |= kCGImageAlphaLast;              break;
 *     }
 *     return info;
 * }
 * ```
 */
public fun computeCgalphaInfoRgba(at: SkAlphaType): CGBitmapInfo {
  TODO("Implement computeCgalphaInfoRgba")
}

/**
 * C++ original:
 * ```cpp
 * static CGBitmapInfo compute_cgalpha_info_bgra(SkAlphaType at) {
 *     CGBitmapInfo info = kCGBitmapByteOrder32Little;
 *     switch (at) {
 *         case kUnknown_SkAlphaType:                                           break;
 *         case kOpaque_SkAlphaType:   info |= kCGImageAlphaNoneSkipFirst;      break;
 *         case kPremul_SkAlphaType:   info |= kCGImageAlphaPremultipliedFirst; break;
 *         case kUnpremul_SkAlphaType: info |= kCGImageAlphaFirst;              break;
 *     }
 *     return info;
 * }
 * ```
 */
public fun computeCgalphaInfoBgra(at: SkAlphaType): CGBitmapInfo {
  TODO("Implement computeCgalphaInfoBgra")
}

/**
 * C++ original:
 * ```cpp
 * static CGBitmapInfo compute_cgalpha_info_4444(SkAlphaType at) {
 *     CGBitmapInfo info = kCGBitmapByteOrder16Little;
 *     switch (at) {
 *         case kOpaque_SkAlphaType: info |= kCGImageAlphaNoneSkipLast;      break;
 *         default:                  info |= kCGImageAlphaPremultipliedLast; break;
 *     }
 *     return info;
 * }
 * ```
 */
public fun computeCgalphaInfo4444(at: SkAlphaType): CGBitmapInfo {
  TODO("Implement computeCgalphaInfo4444")
}

/**
 * C++ original:
 * ```cpp
 * static bool get_bitmap_info(SkColorType skColorType,
 *                             SkAlphaType skAlphaType,
 *                             size_t* bitsPerComponent,
 *                             CGBitmapInfo* info,
 *                             bool* upscaleTo32) {
 *     if (upscaleTo32) {
 *         *upscaleTo32 = false;
 *     }
 *     switch (skColorType) {
 *         case kRGB_565_SkColorType:
 *             if (upscaleTo32) {
 *                 *upscaleTo32 = true;
 *             }
 *             // now treat like RGBA
 *             *bitsPerComponent = 8;
 *             *info = compute_cgalpha_info_rgba(kOpaque_SkAlphaType);
 *             break;
 *         case kRGBA_8888_SkColorType:
 *             *bitsPerComponent = 8;
 *             *info = compute_cgalpha_info_rgba(skAlphaType);
 *             break;
 *         case kBGRA_8888_SkColorType:
 *             *bitsPerComponent = 8;
 *             *info = compute_cgalpha_info_bgra(skAlphaType);
 *             break;
 *         case kARGB_4444_SkColorType:
 *             *bitsPerComponent = 4;
 *             *info = compute_cgalpha_info_4444(skAlphaType);
 *             break;
 *         default:
 *             return false;
 *     }
 *     return true;
 * }
 * ```
 */
public fun getBitmapInfo(
  skColorType: SkColorType,
  skAlphaType: SkAlphaType,
  bitsPerComponent: ULong?,
  info: CGBitmapInfo?,
  upscaleTo32: Boolean?,
): Boolean {
  TODO("Implement getBitmapInfo")
}

/**
 * C++ original:
 * ```cpp
 * static std::unique_ptr<SkBitmap> prepare_for_image_ref(const SkBitmap& bm,
 *                                                        size_t* bitsPerComponent,
 *                                                        CGBitmapInfo* info) {
 *     bool upscaleTo32;
 *     if (!get_bitmap_info(bm.colorType(), bm.alphaType(), bitsPerComponent, info, &upscaleTo32)) {
 *         return nullptr;
 *     }
 *     if (upscaleTo32) {
 *         std::unique_ptr<SkBitmap> copy(new SkBitmap);
 *         // here we make a deep copy of the pixels, since CG won't take our
 *         // 565 directly, so we always go to RGBA
 *         copy->allocPixels(bm.info().makeColorType(kRGBA_8888_SkColorType));
 *         bm.readPixels(copy->info(), copy->getPixels(), copy->rowBytes(), 0, 0);
 *         return copy;
 *     }
 *     return std::make_unique<SkBitmap>(bm);
 * }
 * ```
 */
public fun prepareForImageRef(
  bm: SkBitmap,
  bitsPerComponent: ULong?,
  info: CGBitmapInfo?,
): SkBitmap? {
  TODO("Implement prepareForImageRef")
}

/**
 * C++ original:
 * ```cpp
 * CGImageRef SkCreateCGImageRefWithColorspace(const SkBitmap& bm,
 *                                             CGColorSpaceRef colorSpace) {
 *     return SkCreateCGImageRef(bm);
 * }
 * ```
 */
public fun skCreateCGImageRefWithColorspace(bm: SkBitmap, colorSpace: CGColorSpaceRef): CGImageRef {
  TODO("Implement skCreateCGImageRefWithColorspace")
}

/**
 * C++ original:
 * ```cpp
 * CGImageRef SkCreateCGImageRef(const SkBitmap& bm) {
 *     if (bm.drawsNothing()) {
 *         return nullptr;
 *     }
 *     size_t bitsPerComponent SK_INIT_TO_AVOID_WARNING;
 *     CGBitmapInfo info       SK_INIT_TO_AVOID_WARNING;
 *
 *     std::unique_ptr<SkBitmap> bitmap = prepare_for_image_ref(bm, &bitsPerComponent, &info);
 *     if (nullptr == bitmap) {
 *         return nullptr;
 *     }
 *
 *     SkPixmap pm = bitmap->pixmap();  // Copy bitmap info before releasing it.
 *     const size_t s = bitmap->computeByteSize();
 *     void* pixels = bitmap->getPixels();
 *
 *     // our provider "owns" the bitmap*, and will take care of deleting it
 *     SkUniqueCFRef<CGDataProviderRef> dataRef(CGDataProviderCreateWithData(
 *             bitmap.release(), pixels, s,
 *             [](void* p, const void*, size_t) { delete reinterpret_cast<SkBitmap*>(p); }));
 *
 *     SkUniqueCFRef<CGColorSpaceRef> colorSpace(SkCreateCGColorSpace(bm.colorSpace()));
 *     return CGImageCreate(pm.width(),
 *                          pm.height(),
 *                          bitsPerComponent,
 *                          pm.info().bytesPerPixel() * CHAR_BIT,
 *                          pm.rowBytes(),
 *                          colorSpace.get(),
 *                          info,
 *                          dataRef.get(),
 *                          nullptr,
 *                          false,
 *                          kCGRenderingIntentDefault);
 * }
 * ```
 */
public fun skCreateCGImageRef(bm: SkBitmap): CGImageRef {
  TODO("Implement skCreateCGImageRef")
}

/**
 * C++ original:
 * ```cpp
 * void SkCGDrawBitmap(CGContextRef cg, const SkBitmap& bm, float x, float y) {
 *     SkUniqueCFRef<CGImageRef> img(SkCreateCGImageRef(bm));
 *
 *     if (img) {
 *         CGRect r = CGRectMake(0, 0, bm.width(), bm.height());
 *
 *         CGContextSaveGState(cg);
 *         CGContextTranslateCTM(cg, x, r.size.height + y);
 *         CGContextScaleCTM(cg, 1, -1);
 *
 *         CGContextDrawImage(cg, r, img.get());
 *
 *         CGContextRestoreGState(cg);
 *     }
 * }
 * ```
 */
public fun skCGDrawBitmap(
  cg: CGContextRef,
  bm: SkBitmap,
  x: Float,
  y: Float,
) {
  TODO("Implement skCGDrawBitmap")
}

/**
 * C++ original:
 * ```cpp
 * CGContextRef SkCreateCGContext(const SkPixmap& pmap) {
 *     CGBitmapInfo cg_bitmap_info = 0;
 *     size_t bitsPerComponent = 0;
 *     switch (pmap.colorType()) {
 *         case kRGBA_8888_SkColorType:
 *             bitsPerComponent = 8;
 *             cg_bitmap_info = compute_cgalpha_info_rgba(pmap.alphaType());
 *             break;
 *         case kBGRA_8888_SkColorType:
 *             bitsPerComponent = 8;
 *             cg_bitmap_info = compute_cgalpha_info_bgra(pmap.alphaType());
 *             break;
 *         default:
 *             return nullptr;   // no other colortypes are supported (for now)
 *     }
 *
 *     size_t rb = pmap.addr() ? pmap.rowBytes() : 0;
 *     SkUniqueCFRef<CGColorSpaceRef> cs(SkCreateCGColorSpace(pmap.colorSpace()));
 *     CGContextRef cg = CGBitmapContextCreate(pmap.writable_addr(), pmap.width(), pmap.height(),
 *                                             bitsPerComponent, rb, cs.get(), cg_bitmap_info);
 *     return cg;
 * }
 * ```
 */
public fun skCreateCGContext(pmap: SkPixmap): CGContextRef {
  TODO("Implement skCreateCGContext")
}

/**
 * C++ original:
 * ```cpp
 * bool SkCopyPixelsFromCGImage(const SkImageInfo& info, size_t rowBytes, void* pixels,
 *                              CGImageRef image) {
 *     CGBitmapInfo cg_bitmap_info = 0;
 *     size_t bitsPerComponent = 0;
 *     switch (info.colorType()) {
 *         case kRGBA_8888_SkColorType:
 *             bitsPerComponent = 8;
 *             cg_bitmap_info = compute_cgalpha_info_rgba(info.alphaType());
 *             break;
 *         case kBGRA_8888_SkColorType:
 *             bitsPerComponent = 8;
 *             cg_bitmap_info = compute_cgalpha_info_bgra(info.alphaType());
 *             break;
 *         default:
 *             return false;   // no other colortypes are supported (for now)
 *     }
 *
 *     SkUniqueCFRef<CGColorSpaceRef> cs(SkCreateCGColorSpace(info.colorSpace()));
 *     SkUniqueCFRef<CGContextRef> cg(CGBitmapContextCreate(
 *                 pixels, info.width(), info.height(), bitsPerComponent,
 *                 rowBytes, cs.get(), cg_bitmap_info));
 *     if (!cg) {
 *         return false;
 *     }
 *
 *     // use this blend mode, to avoid having to erase the pixels first, and to avoid CG performing
 *     // any blending (which could introduce errors and be slower).
 *     CGContextSetBlendMode(cg.get(), kCGBlendModeCopy);
 *
 *     CGContextDrawImage(cg.get(), CGRectMake(0, 0, info.width(), info.height()), image);
 *     return true;
 * }
 * ```
 */
public fun skCopyPixelsFromCGImage(
  info: SkImageInfo,
  rowBytes: ULong,
  pixels: Unit?,
  image: CGImageRef,
): Boolean {
  TODO("Implement skCopyPixelsFromCGImage")
}

/**
 * C++ original:
 * ```cpp
 * bool SkCreateBitmapFromCGImage(SkBitmap* dst, CGImageRef image) {
 *     const int width = SkToInt(CGImageGetWidth(image));
 *     const int height = SkToInt(CGImageGetHeight(image));
 *     sk_sp<SkColorSpace> colorSpace(SkMakeColorSpaceFromCGColorSpace(CGImageGetColorSpace(image)));
 *     SkImageInfo info = SkImageInfo::MakeN32Premul(width, height, colorSpace);
 *
 *     SkBitmap tmp;
 *     if (!tmp.tryAllocPixels(info)) {
 *         return false;
 *     }
 *
 *     if (!SkCopyPixelsFromCGImage(tmp.info(), tmp.rowBytes(), tmp.getPixels(), image)) {
 *         return false;
 *     }
 *
 *     CGImageAlphaInfo cgInfo = CGImageGetAlphaInfo(image);
 *     switch (cgInfo) {
 *         case kCGImageAlphaNone:
 *         case kCGImageAlphaNoneSkipLast:
 *         case kCGImageAlphaNoneSkipFirst:
 *             SkASSERT(SkBitmap::ComputeIsOpaque(tmp));
 *             tmp.setAlphaType(kOpaque_SkAlphaType);
 *             break;
 *         default:
 *             // we don't know if we're opaque or not, so compute it.
 *             if (SkBitmap::ComputeIsOpaque(tmp)) {
 *                 tmp.setAlphaType(kOpaque_SkAlphaType);
 *             }
 *     }
 *
 *     *dst = tmp;
 *     return true;
 * }
 * ```
 */
public fun skCreateBitmapFromCGImage(dst: SkBitmap?, image: CGImageRef): Boolean {
  TODO("Implement skCreateBitmapFromCGImage")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkImage> SkMakeImageFromCGImage(CGImageRef src) {
 *     SkBitmap bm;
 *     if (!SkCreateBitmapFromCGImage(&bm, src)) {
 *         return nullptr;
 *     }
 *
 *     bm.setImmutable();
 *     return bm.asImage();
 * }
 * ```
 */
public fun skMakeImageFromCGImage(src: CGImageRef): SkSp<SkImage> {
  TODO("Implement skMakeImageFromCGImage")
}

/**
 * C++ original:
 * ```cpp
 * CGDataProviderRef SkCreateCGDataProvider(sk_sp<SkData> data) {
 *     if (!data) {
 *         return nullptr;
 *     }
 *
 *     CGDataProviderRef result = CGDataProviderCreateWithData(
 *             data.get(), data->data(), data->size(), [](void* info, const void*, size_t) {
 *                 reinterpret_cast<SkData*>(info)->unref();
 *             });
 *     if (!result) {
 *         return nullptr;
 *     }
 *
 *     // Retain `data` for the release that will come when `result` is freed.
 *     data->ref();
 *     return result;
 * }
 * ```
 */
public fun skCreateCGDataProvider(`data`: SkSp<SkData>): CGDataProviderRef {
  TODO("Implement skCreateCGDataProvider")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkColorSpace> SkMakeColorSpaceFromCGColorSpace(CGColorSpaceRef cgColorSpace) {
 *     if (!cgColorSpace) {
 *         return nullptr;
 *     }
 *
 *     // Attempt to convert by name.
 *     SkUniqueCFRef<CFStringRef> name(CGColorSpaceCopyName(cgColorSpace));
 *     if (name && CFStringCompare(name.get(), kCGColorSpaceSRGB, 0) == kCFCompareEqualTo) {
 *         return SkColorSpace::MakeSRGB();
 *     }
 *
 *     // Attempt to convert by parsing the ICC profile.
 *     SkUniqueCFRef<CFDataRef> iccData(CGColorSpaceCopyICCData(cgColorSpace));
 *     if (!iccData) {
 *         return nullptr;
 *     }
 *     skcms_ICCProfile iccProfile;
 *     if (!skcms_Parse(
 *                 CFDataGetBytePtr(iccData.get()), CFDataGetLength(iccData.get()), &iccProfile)) {
 *         return nullptr;
 *     }
 *     return SkColorSpace::Make(iccProfile);
 * }
 * ```
 */
public fun skMakeColorSpaceFromCGColorSpace(cgColorSpace: CGColorSpaceRef): SkSp<SkColorSpace> {
  TODO("Implement skMakeColorSpaceFromCGColorSpace")
}

/**
 * C++ original:
 * ```cpp
 * CGColorSpaceRef SkCreateCGColorSpace(const SkColorSpace* space) {
 *     // Initialize result to sRGB. We will use this as the fallback on failure.
 *     CGColorSpaceRef cgSRGB = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
 *
 *     // Early-out of this is sRGB (or nullptr defaulting to sRGB).
 *     if (!space || space->isSRGB()) {
 *         return cgSRGB;
 *     }
 *
 *     // Create an SkData with the ICC profile.
 *     skcms_TransferFunction fn;
 *     skcms_Matrix3x3 to_xyzd50;
 *     space->transferFn(&fn);
 *     space->toXYZD50(&to_xyzd50);
 *     sk_sp<SkData> iccData = SkWriteICCProfile(fn, to_xyzd50);
 *     if (!iccData) {
 *         return cgSRGB;
 *     }
 *
 *     // Create a CGColorSpaceRef from that ICC data.
 *     const size_t kNumComponents = 3;
 *     const CGFloat kComponentRanges[6] = {0, 1, 0, 1, 0, 1};
 *     SkUniqueCFRef<CGDataProviderRef> iccDataProvider(SkCreateCGDataProvider(iccData));
 *     CGColorSpaceRef result = CGColorSpaceCreateICCBased(
 *             kNumComponents, kComponentRanges, iccDataProvider.get(), cgSRGB);
 *     if (!result) {
 *         return cgSRGB;
 *     }
 *
 *     // We will not be returning |cgSRGB|, so free it now.
 *     CFRelease(cgSRGB);
 *     cgSRGB = nullptr;
 *
 *     return result;
 * }
 * ```
 */
public fun skCreateCGColorSpace(space: SkColorSpace?): CGColorSpaceRef {
  TODO("Implement skCreateCGColorSpace")
}

/**
 * C++ original:
 * ```cpp
 * inline void PrintLineByLine(const std::string& text) {
 *     VisitLineByLine(text, [](int lineNumber, const char* lineText) {
 *         SkDebugf("%4i\t%s\n", lineNumber, lineText);
 *     });
 * }
 * ```
 */
public fun printLineByLine(text: String) {
  TODO("Implement printLineByLine")
}

/**
 * C++ original:
 * ```cpp
 * inline GrDirectContext* GetContext(const sk_sp<const SkImage>& src) {
 *     return GetContext(src.get());
 * }
 * ```
 */
public fun getContext(src: SkSp<SkImage>): GrDirectContext {
  TODO("Implement getContext")
}
