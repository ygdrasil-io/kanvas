package org.skia.core

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.UByte
import kotlin.UInt
import kotlin.ULong
import kotlin.Unit
import org.skia.foundation.SkNVRefCnt
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.math.SkMatrix
import org.skia.math.SkPathDirection
import org.skia.math.SkPathFillType
import org.skia.math.SkPathVerb
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * class SkPathData : public SkNVRefCnt<SkPathData> {
 * public:
 *     ~SkPathData();
 *
 *     /*
 *      *  Returns an empty pathdata.
 *      *
 *      *  Since this is immutable, it may return the same object each time it is called.
 *      */
 *     static sk_sp<SkPathData> Empty();
 *
 *     /*
 *      *  Return SkPathData with a copy of these buffers, or nullptr if they are illegal.
 *      *  Illegal = non-finite, or non-sensical verb sequences
 *      */
 *     static sk_sp<SkPathData> Make(SkSpan<const SkPoint> pts,
 *                                   SkSpan<const SkPathVerb> verbs,
 *                                   SkSpan<const float> conics = {});
 *
 *     /*
 *      *  Attempt to transform src by the matrix. On success, return a new SkPathData
 *      *  with the result, else return {}.
 *      */
 *     static sk_sp<SkPathData> MakeTransform(const SkPathRaw& src, const SkMatrix&);
 *
 *     /*
 *      *  When a factory takes a startIndex, this refers to the position of the first point
 *      *  when constructing one of our simple shapes: rect, oval, rrect.
 *      *  The index is the same as that passed to the equivalent factories in SkPath
 *      *  and in the associated addRect/Oval/RRect methods on SkPathBuilder.
 *      */
 *     static sk_sp<SkPathData> Rect(const SkRect&,
 *                                   SkPathDirection = SkPathDirection::kDefault,
 *                                   unsigned startIndex = 0);
 *     static sk_sp<SkPathData> Oval(const SkRect&,
 *                                   SkPathDirection = SkPathDirection::kDefault,
 *                                   unsigned startIndex = 1);
 *
 *     static sk_sp<SkPathData> RRect(const SkRRect&, SkPathDirection, unsigned startIndex);
 *     static sk_sp<SkPathData> RRect(const SkRRect& rrect,
 *                                    SkPathDirection dir = SkPathDirection::kDefault) {
 *         return RRect(rrect, dir, dir == SkPathDirection::kCW ? 6 : 7);
 *     }
 *     static sk_sp<SkPathData> Polygon(SkSpan<const SkPoint> pts, bool isClosed);
 *     static sk_sp<SkPathData> Line(SkPoint a, SkPoint b) {
 *         return Polygon({{a, b}}, false);
 *     }
 *
 *     friend bool operator==(const SkPathData& a, const SkPathData& b);
 *     friend bool operator!=(const SkPathData& a, const SkPathData& b) {
 *         return !(a == b);
 *     }
 *
 *     SkSpan<const SkPoint> points() const { return fPoints; }
 *     SkSpan<const SkPathVerb> verbs() const { return fVerbs; }
 *     SkSpan<const float> conics() const { return fConics; }
 *     const SkRect& bounds() const { return fBounds; }
 *     uint8_t segmentMask() const { return fSegmentMask; }
 *
 *     // Will never be zero, has the low-2 bits always zero (to store filltype)
 *     uint32_t uniqueID() const { return fUniqueID; }
 *
 *     SkPathRaw raw(SkPathFillType, SkResolveConvexity) const;
 *
 *     /**
 *      * Return true if the path contains no points or verbs
 *      */
 *     bool empty() const { return fVerbs.empty(); }
 *
 *     SkRect computeTightBounds() const;
 *
 *     /**
 *      * Returns true if the pathdata is convex.
 *      * Note: if necessary, it will first compute the convexity (and cache it).
 *      */
 *     bool isConvex() const;
 *
 *     /**
 *      * Returns two points if SkPath contains only one line. If not, return {}.
 *      */
 *     std::optional<std::array<SkPoint, 2>> asLine() const;
 *
 *     /**
 *      * If the pathdata is recognized as a rect, return it and its direction and open/closed.
 *      * If not, return {}
 *      */
 *     std::optional<SkPathRectInfo> asRect() const;
 *
 *     /**
 *      * If the path is recognized as an oval, return its bounds. If not, return {}.
 *      */
 *     std::optional<SkPathOvalInfo> asOval() const;
 *
 *     /**
 *      * If the path is recognized as a round-rect, return it. If not, return {}.
 *      */
 *     std::optional<SkPathRRectInfo> asRRect() const;
 *
 *     /**
 *      *  Attempt to transform the pathdata by the matrix. If this succeeds, return a new
 *      *  pathdata object: note, this may have different verbs / number-of-points, if the
 *      *  matrix contained perspective.
 *      *
 *      *  If the matrix has no effect on the coordinates (e.g. it is identity), then this may
 *      *  return a ref to the same pathdata object.
 *      *
 *      *  If the result of applying the matrix creates any non-finite coordinates, this returns
 *      *  nullptr.
 *      */
 *     sk_sp<SkPathData> makeTransform(const SkMatrix&) const;
 *     sk_sp<SkPathData> makeOffset(SkVector) const;
 *
 *     bool contains(SkPoint, SkPathFillType) const;
 *
 *     void addGenIDChangeListener(sk_sp<SkIDChangeListener>) const;
 *     int genIDChangeListenerCount() const { return fGenIDChangeListeners.count(); }
 *
 * private:
 *     friend class SkNVRefCnt<SkPathData>;
 *     friend class SkPathPriv;
 *     friend class SkPath;
 *     friend class SkPathBuilder;
 *
 *     // notify these in our destructor
 *     mutable SkIDChangeListener::List fGenIDChangeListeners;
 *
 *     SkSpan<SkPoint>    fPoints;
 *     SkSpan<float>      fConics;
 *     SkSpan<SkPathVerb> fVerbs;
 *     SkRect             fBounds;
 *
 *     uint32_t           fUniqueID;   // never 0
 *
 *     /*
 *      *  Convexity can be slow to compute, and (in theory) it can't always survive a matrix
 *      *  transform (due to numeric instability). Therefore we will lazily compute it as
 *      *  requested. Since we are technically always immutable, we have to store this field
 *      *  in an atomic.
 *      */
 *     mutable std::atomic<uint8_t> fConvexity;    // SkPathConvexity
 *     uint8_t                      fSegmentMask;  // SkPathSegmentMask
 *     SkPathIsAType                fType;
 *     SkPathIsAData                fIsA {};
 *
 *     //
 *     // Memory layout after this (assuming we're not empty)
 *     //
 *     //  [point data]
 *     //  [conic data]
 *     //  [verb  data]
 *     //
 *
 *     SkPathData(size_t npts, size_t nvbs, size_t ncns);
 *
 *     // Ensure the unsized delete is called (since we're manually allocating the storage)
 *     void operator delete(void* p);
 *
 *     // internal finisher when building a PathData.
 *     // If the optional value is not present, it will be computed (else checked in debug mode).
 *     //
 *     // In particular, if bounds is not provided, it will be computed, and if it proves
 *     // to be non-finite, false will be returned.
 *     bool finishInit(std::optional<SkRect> bounds, std::optional<uint8_t> segmentMask);
 *
 *     // If we know we're a special shape, call this after the normal initialization
 *     void setupIsA(SkPathIsAType, SkPathDirection dir, unsigned startIndex);
 *
 *     SkPathConvexity getConvexityOrUnknown() const;          // may return kUnknown
 *     SkPathConvexity getResolvedConvexity() const;           // never returns kUnknown
 *     void setConvexity(SkPathConvexity) const;               // const -- but convexity is mutable
 *
 *     static SkPathData* PeekEmptySingleton();
 *
 *     static sk_sp<SkPathData> Alloc(size_t npts, size_t nvbs, size_t ncns);
 *
 *     static sk_sp<SkPathData> MakeNoCheck(SkSpan<const SkPoint> pts,
 *                                          SkSpan<const SkPathVerb> verbs,
 *                                          SkSpan<const float> conics,
 *                                          std::optional<SkRect> bounds,
 *                                          std::optional<unsigned> segmentMask);
 *     static sk_sp<SkPathData> MakeNoCheck(const SkPathRaw&);
 * }
 * ```
 */
public abstract class SkPathData public constructor(
  npts: ULong,
  nvbs: ULong,
  ncns: ULong,
) : SkNVRefCnt(),
    SkPathData {
  /**
   * C++ original:
   * ```cpp
   * mutable SkIDChangeListener::List fGenIDChangeListeners
   * ```
   */
  private var fGenIDChangeListeners: SkIDChangeListener.List =
      TODO("Initialize fGenIDChangeListeners")

  /**
   * C++ original:
   * ```cpp
   * SkSpan<SkPoint>    fPoints
   * ```
   */
  private var fPoints: SkSpan<SkPoint> = TODO("Initialize fPoints")

  /**
   * C++ original:
   * ```cpp
   * SkSpan<float>      fConics
   * ```
   */
  private var fConics: SkSpan<Float> = TODO("Initialize fConics")

  /**
   * C++ original:
   * ```cpp
   * SkSpan<SkPathVerb> fVerbs
   * ```
   */
  private var fVerbs: SkSpan<SkPathVerb> = TODO("Initialize fVerbs")

  /**
   * C++ original:
   * ```cpp
   * SkRect             fBounds
   * ```
   */
  private var fBounds: SkRect = TODO("Initialize fBounds")

  /**
   * C++ original:
   * ```cpp
   * uint32_t           fUniqueID
   * ```
   */
  private var fUniqueID: Int = TODO("Initialize fUniqueID")

  /**
   * C++ original:
   * ```cpp
   * mutable std::atomic<uint8_t> fConvexity
   * ```
   */
  private var fConvexity: Int = TODO("Initialize fConvexity")

  /**
   * C++ original:
   * ```cpp
   * uint8_t                      fSegmentMask
   * ```
   */
  private var fSegmentMask: Int = TODO("Initialize fSegmentMask")

  /**
   * C++ original:
   * ```cpp
   * SkPathIsAType                fType
   * ```
   */
  private var fType: SkPathIsAType = TODO("Initialize fType")

  /**
   * C++ original:
   * ```cpp
   * SkPathIsAData                fIsA
   * ```
   */
  private var fIsA: SkPathIsAData = TODO("Initialize fIsA")

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkPoint> points() const { return fPoints; }
   * ```
   */
  public override fun points(): SkSpan<SkPoint> {
    TODO("Implement points")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkPathVerb> verbs() const { return fVerbs; }
   * ```
   */
  public override fun verbs(): SkSpan<SkPathVerb> {
    TODO("Implement verbs")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSpan<const float> conics() const { return fConics; }
   * ```
   */
  public override fun conics(): SkSpan<Float> {
    TODO("Implement conics")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkRect& bounds() const { return fBounds; }
   * ```
   */
  public override fun bounds(): SkRect {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * uint8_t segmentMask() const { return fSegmentMask; }
   * ```
   */
  public override fun segmentMask(): Int {
    TODO("Implement segmentMask")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t uniqueID() const { return fUniqueID; }
   * ```
   */
  public override fun uniqueID(): Int {
    TODO("Implement uniqueID")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathRaw SkPathData::raw(SkPathFillType ft, SkResolveConvexity rc) const {
   *     return {
   *         fPoints,
   *         fVerbs,
   *         fConics,
   *         fBounds,
   *         ft,
   *         rc == SkResolveConvexity::kYes ? this->getResolvedConvexity()
   *                                        : this->getConvexityOrUnknown(),
   *         fSegmentMask,
   *     };
   * }
   * ```
   */
  public override fun raw(ft: SkPathFillType, rc: SkResolveConvexity): SkPathRaw {
    TODO("Implement raw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool empty() const { return fVerbs.empty(); }
   * ```
   */
  public override fun empty(): Boolean {
    TODO("Implement empty")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkPathData::computeTightBounds() const {
   *     return SkPathPriv::ComputeTightBounds(this->points(), this->verbs(), this->conics());
   * }
   * ```
   */
  public override fun computeTightBounds(): SkRect {
    TODO("Implement computeTightBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPathData::isConvex() const {
   *     return SkPathConvexity_IsConvex(this->getResolvedConvexity());
   * }
   * ```
   */
  public override fun isConvex(): Boolean {
    TODO("Implement isConvex")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPathRectInfo> SkPathData::asRect() const {
   *     if (auto rc = SkPathPriv::IsRectContour(fPoints, fVerbs, fSegmentMask, false)) {
   *         SkASSERT(rc->fRect == fBounds);
   *         return {{
   *             fBounds,
   *             rc->fDirection,
   *             0, // start index???
   *         }};
   *     }
   *     return {};
   * }
   * ```
   */
  public override fun asRect(): Int {
    TODO("Implement asRect")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPathOvalInfo> SkPathData::asOval() const {
   *     if (fType == SkPathIsAType::kOval) {
   *         return {{
   *             fBounds,
   *             fIsA.fDirection,
   *             fIsA.fStartIndex,
   *         }};
   *     }
   *     return {};
   * }
   * ```
   */
  public override fun asOval(): Int {
    TODO("Implement asOval")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkPathRRectInfo> SkPathData::asRRect() const {
   *     if (fType == SkPathIsAType::kRRect) {
   *         return {{
   *             SkPathPriv::DeduceRRectFromContour(fBounds, fPoints, fVerbs),
   *             fIsA.fDirection,
   *             fIsA.fStartIndex,
   *         }};
   *     }
   *     return {};
   * }
   * ```
   */
  public override fun asRRect(): Int {
    TODO("Implement asRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPathData> SkPathData::makeTransform(const SkMatrix& mx) const {
   *     if (mx.isIdentity()) {
   *         return sk_ref_sp(this);
   *     }
   *
   *     // not important for transform, just need a value
   *     const SkPathFillType ft = SkPathFillType::kDefault;
   *
   *     if (auto result = MakeTransform(this->raw(ft, SkResolveConvexity::kNo), mx)) {
   *         // See if we can maintian our IsA status ...
   *         if ((fType == SkPathIsAType::kOval || fType == SkPathIsAType::kRRect) &&
   *             mx.rectStaysRect() && SkPathPriv::IsAxisAligned(fPoints))
   *         {
   *             auto [dir, start] =
   *             SkPathPriv::TransformDirAndStart(mx, fType == SkPathIsAType::kRRect,
   *                                              fIsA.fDirection, fIsA.fStartIndex);
   *             result->setupIsA(fType, dir, start);
   *         }
   *         return result;
   *     }
   *     return nullptr;
   * }
   * ```
   */
  public override fun makeTransform(mx: SkMatrix): SkSp<SkPathData> {
    TODO("Implement makeTransform")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPathData> SkPathData::makeOffset(SkVector v) const {
   *     return this->makeTransform(SkMatrix::Translate(v));
   * }
   * ```
   */
  public override fun makeOffset(v: SkVector): SkSp<SkPathData> {
    TODO("Implement makeOffset")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPathData::contains(SkPoint p, SkPathFillType ft) const {
   *     return SkPathPriv::Contains(this->raw(ft, SkResolveConvexity::kNo), p);
   * }
   * ```
   */
  public override fun contains(p: SkPoint, ft: SkPathFillType): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathData::addGenIDChangeListener(sk_sp<SkIDChangeListener> listener) const {
   *     // our empty singleton is never deleted, so we don't want to add any listeners to it.
   *     if (this != SkPathData::PeekEmptySingleton()) {
   *         // this method on the list is thread-safe
   *         fGenIDChangeListeners.add(std::move(listener));
   *     }
   * }
   * ```
   */
  public override fun addGenIDChangeListener(listener: SkSp<SkIDChangeListener>) {
    TODO("Implement addGenIDChangeListener")
  }

  /**
   * C++ original:
   * ```cpp
   * int genIDChangeListenerCount() const { return fGenIDChangeListeners.count(); }
   * ```
   */
  public override fun genIDChangeListenerCount(): Int {
    TODO("Implement genIDChangeListenerCount")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathData::operator delete(void* p) {
   *     ::operator delete(p);
   * }
   * ```
   */
  public override fun toDelete(p: Unit?) {
    TODO("Implement toDelete")
  }

  /**
   * C++ original:
   * ```cpp
   * bool finishInit(std::optional<SkRect> bounds, std::optional<uint8_t> segmentMask)
   * ```
   */
  public override fun finishInit(bounds: SkRect?, segmentMask: UByte?): Boolean {
    TODO("Implement finishInit")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathData::setupIsA(SkPathIsAType type, SkPathDirection dir, unsigned index) {
   *     this->setConvexity(SkPathDirection_ToConvexity(dir));
   *
   *     SkASSERT(type == SkPathIsAType::kOval || type == SkPathIsAType::kRRect);
   *     fType = type;
   *
   *     SkASSERT((type == SkPathIsAType::kOval && index < 4) ||
   *              (type == SkPathIsAType::kRRect && index < 8));
   *
   *     fIsA.fDirection  = dir;
   *     fIsA.fStartIndex = SkTo<uint8_t>(index);
   * }
   * ```
   */
  public override fun setupIsA(
    type: SkPathIsAType,
    dir: SkPathDirection,
    startIndex: UInt,
  ) {
    TODO("Implement setupIsA")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathConvexity SkPathData::getConvexityOrUnknown() const {
   *     return static_cast<SkPathConvexity>(fConvexity.load(std::memory_order_relaxed));
   * }
   * ```
   */
  public override fun getConvexityOrUnknown(): SkPathConvexity {
    TODO("Implement getConvexityOrUnknown")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPathConvexity SkPathData::getResolvedConvexity() const {
   *     auto convexity = this->getConvexityOrUnknown();
   *     if (convexity == SkPathConvexity::kUnknown) {
   *         convexity = SkPathPriv::ComputeConvexity(fPoints, fVerbs, fConics);
   *         this->setConvexity(convexity);
   *     }
   *     return convexity;
   * }
   * ```
   */
  public override fun getResolvedConvexity(): SkPathConvexity {
    TODO("Implement getResolvedConvexity")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPathData::setConvexity(SkPathConvexity convexity) const {
   *     fConvexity.store((uint8_t)convexity, std::memory_order_relaxed);
   * }
   * ```
   */
  public override fun setConvexity(convexity: SkPathConvexity) {
    TODO("Implement setConvexity")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPathData> SkPathData::Empty() {
     *     return sk_ref_sp(PeekEmptySingleton());
     * }
     * ```
     */
    public override fun empty(): SkSp<SkPathData> {
      TODO("Implement empty")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPathData> SkPathData::Make(SkSpan<const SkPoint> pts,
     *                                    SkSpan<const SkPathVerb> vbs,
     *                                    SkSpan<const float> conics) {
     *     if (!valid_path_data(pts, vbs, conics)) {
     *         report_pathdata_make_failure("invalid path data");
     *         return nullptr;
     *     }
     *
     *     // MakeNoCheck *does* compute/check bounds if we don't pass them in
     *     return MakeNoCheck(pts, vbs, conics, {}, {});
     * }
     * ```
     */
    public override fun make(
      pts: SkSpan<SkPoint>,
      vbs: SkSpan<SkPathVerb>,
      conics: SkSpan<Float>,
    ): SkSp<SkPathData> {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPathData> SkPathData::MakeTransform(const SkPathRaw& src, const SkMatrix& mx) {
     *     if (src.empty()) {
     *         return SkPathData::Empty();
     *     }
     *
     *     if (mx.hasPerspective()) {
     *         SkPathBuilder bu;
     *         bu.addRaw(src);
     *         bu.transform(mx);
     *         return bu.detachData();
     *     }
     *
     *     // Allocate our result, so we can map the new points directly into it
     *     auto result = Alloc(src.points().size(), src.verbs().size(), src.conics().size());
     *     mx.mapPoints(result->fPoints, src.points());
     *     SkSpanPriv::Copy(result->fConics, src.conics());
     *     SkSpanPriv::Copy(result->fVerbs,  src.verbs());
     *
     *     std::optional<SkRect> transformedBounds;
     *     if (mx.rectStaysRect()) {
     *         // safe us from having to compute our transformed bounds in finishInit()
     *         transformedBounds = mx.mapRect(src.bounds());
     *         if (!transformedBounds.value().isFinite()) {
     *             report_pathdata_make_failure("transform created non-finite bounds");
     *             return nullptr;
     *         }
     *     }
     *
     *     if (!result->finishInit(transformedBounds, src.fSegmentMask)) {
     *         return nullptr;
     *     }
     *
     *     result->setConvexity(SkPathPriv::TransformConvexity(mx, src.fPoints, src.fConvexity));
     *
     *     return result;
     * }
     * ```
     */
    public override fun makeTransform(src: SkPathRaw, mx: SkMatrix): SkSp<SkPathData> {
      TODO("Implement makeTransform")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPathData> SkPathData::Rect(const SkRect& r, SkPathDirection dir, unsigned index) {
     *     if (!r.isFinite()) {
     *         return nullptr;
     *     }
     *     SkPathRawShapes::Rect raw(r, dir, index);
     *     return MakeNoCheck(raw.points(), raw.verbs(), raw.conics(), raw.fBounds, raw.fSegmentMask);
     * }
     * ```
     */
    public override fun rect(
      r: SkRect,
      dir: SkPathDirection = TODO(),
      startIndex: UInt = TODO(),
    ): SkSp<SkPathData> {
      TODO("Implement rect")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPathData> SkPathData::Oval(const SkRect& r, SkPathDirection dir, unsigned index) {
     *     if (!r.isFinite()) {
     *         return nullptr;
     *     }
     *     SkPathRawShapes::Oval raw(r, dir, index);
     *     auto path = MakeNoCheck(raw.points(), raw.verbs(), raw.conics(), raw.fBounds, raw.fSegmentMask);
     *
     *     path->setupIsA(SkPathIsAType::kOval, dir, index);
     *     return path;
     * }
     * ```
     */
    public override fun oval(
      r: SkRect,
      dir: SkPathDirection = TODO(),
      startIndex: UInt = TODO(),
    ): SkSp<SkPathData> {
      TODO("Implement oval")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPathData> SkPathData::RRect(const SkRRect& r, SkPathDirection dir, unsigned index) {
     *     if (!r.isValid()) {
     *         return nullptr;
     *     }
     *     SkPathRawShapes::RRect raw(r, dir, index);
     *     // we use Make, not MakeNoCheck, to confirm all points an conics are finite
     *     if (auto path = Make(raw.points(), raw.verbs(), raw.conics())) {
     *         path->setupIsA(SkPathIsAType::kRRect, dir, index);
     *         return path;
     *     }
     *     return nullptr;
     * }
     * ```
     */
    public override fun rRect(
      r: SkRRect,
      dir: SkPathDirection,
      startIndex: UInt,
    ): SkSp<SkPathData> {
      TODO("Implement rRect")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkPathData> RRect(const SkRRect& rrect,
     *                                    SkPathDirection dir = SkPathDirection::kDefault) {
     *         return RRect(rrect, dir, dir == SkPathDirection::kCW ? 6 : 7);
     *     }
     * ```
     */
    public override fun rRect(rrect: SkRRect, dir: SkPathDirection = TODO()): SkSp<SkPathData> {
      TODO("Implement rRect")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPathData> SkPathData::Polygon(SkSpan<const SkPoint> pts, bool isClosed) {
     *     if (pts.size() == 0 || (pts.size() == 1 && !isClosed)) {
     *         return Empty();
     *     }
     *
     *     const size_t nverbs = pts.size() + isClosed;    // +1 for the kClose verb
     *     const size_t nconics = 0;
     *     auto path = Alloc(pts.size(), nverbs, nconics);
     *
     *     SkSpanPriv::Copy(path->fPoints, pts);
     *
     *     path->fVerbs[0] = SkPathVerb::kMove;
     *     for (size_t i = 1; i < pts.size(); ++i) {
     *         path->fVerbs[i] = SkPathVerb::kLine;
     *     }
     *     if (isClosed) {
     *         path->fVerbs.back() = SkPathVerb::kClose;
     *     }
     *
     *     return path->finishInit({}, kLine_SkPathSegmentMask) ? path : nullptr;
     * }
     * ```
     */
    public override fun polygon(pts: SkSpan<SkPoint>, isClosed: Boolean): SkSp<SkPathData> {
      TODO("Implement polygon")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkPathData> Line(SkPoint a, SkPoint b) {
     *         return Polygon({{a, b}}, false);
     *     }
     * ```
     */
    public override fun line(a: SkPoint, b: SkPoint): SkSp<SkPathData> {
      TODO("Implement line")
    }

    /**
     * C++ original:
     * ```cpp
     * SkPathData* SkPathData::PeekEmptySingleton() {
     *     static SkPathData* gEmpty = SkPathData::MakeNoCheck({}, {}, {}, {}, {}).release();
     *     return gEmpty;
     * }
     * ```
     */
    public override fun peekEmptySingleton(): SkPathData {
      TODO("Implement peekEmptySingleton")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPathData> SkPathData::Alloc(size_t npts, size_t nvbs, size_t ncns) {
     *     SkSafeAccumulator accum(sizeof(SkPathData));
     *
     *     accum.addMul(npts, sizeof(SkPoint))
     *          .addMul(ncns, sizeof(SkPoint))
     *          .addMul(nvbs, sizeof(SkPathVerb));
     *
     *     if (auto size = accum.total()) {
     *         // This trick allows us to just make one allocation, for us and our buffer
     *         // rather than allocating us and also allocating the buffer (via malloc or new[])
     *         // We have the corresponding operator delete() specified as well.
     *         void* storage = ::operator new (*size);
     *         sk_sp<SkPathData> path(new (storage) SkPathData(npts, nvbs, ncns));
     *
     *         return path;
     *     }
     *     return nullptr;
     * }
     * ```
     */
    public override fun alloc(
      npts: ULong,
      nvbs: ULong,
      ncns: ULong,
    ): SkSp<SkPathData> {
      TODO("Implement alloc")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPathData> SkPathData::MakeNoCheck(SkSpan<const SkPoint> pts,
     *                                           SkSpan<const SkPathVerb> vbs,
     *                                           SkSpan<const float> conics,
     *                                           std::optional<SkRect> bounds,
     *                                           std::optional<unsigned> segmentMask) {
     *     SkASSERT(valid_path_data(pts, vbs, conics));
     *
     *     auto path = Alloc(pts.size(), vbs.size(), conics.size());
     *
     *     SkSpanPriv::Copy(path->fPoints, pts);
     *     SkSpanPriv::Copy(path->fConics, conics);
     *     SkSpanPriv::Copy(path->fVerbs,  vbs);
     *
     *     return path->finishInit(bounds, segmentMask) ? path : nullptr;
     * }
     * ```
     */
    public override fun makeNoCheck(
      pts: SkSpan<SkPoint>,
      verbs: SkSpan<SkPathVerb>,
      conics: SkSpan<Float>,
      bounds: SkRect?,
      segmentMask: UInt?,
    ): SkSp<SkPathData> {
      TODO("Implement makeNoCheck")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkPathData> SkPathData::MakeNoCheck(const SkPathRaw& raw) {
     *     return MakeNoCheck(raw.points(), raw.verbs(), raw.conics(), raw.fBounds, raw.fSegmentMask);
     * }
     * ```
     */
    public override fun makeNoCheck(raw: SkPathRaw): SkSp<SkPathData> {
      TODO("Implement makeNoCheck")
    }
  }
}
