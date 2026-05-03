package org.skia.foundation

import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.core.SkPathBuilder
import org.skia.math.SkIRect
import RunHead as RunHead_
import undefined.RunHead as UndefinedRunHead

/**
 * C++ original:
 * ```cpp
 * class SK_API SkRegion {
 *     typedef int32_t RunType;
 * public:
 *
 *     /** Constructs an empty SkRegion. SkRegion is set to empty bounds
 *         at (0, 0) with zero width and height.
 *
 *         @return  empty SkRegion
 *
 *         example: https://fiddle.skia.org/c/@Region_empty_constructor
 *     */
 *     SkRegion();
 *
 *     /** Constructs a copy of an existing region.
 *         Copy constructor makes two regions identical by value. Internally, region and
 *         the returned result share pointer values. The underlying SkRect array is
 *         copied when modified.
 *
 *         Creating a SkRegion copy is very efficient and never allocates memory.
 *         SkRegion are always copied by value from the interface; the underlying shared
 *         pointers are not exposed.
 *
 *         @param region  SkRegion to copy by value
 *         @return        copy of SkRegion
 *
 *         example: https://fiddle.skia.org/c/@Region_copy_const_SkRegion
 *     */
 *     SkRegion(const SkRegion& region);
 *
 *     /** Constructs a rectangular SkRegion matching the bounds of rect.
 *
 *         @param rect  bounds of constructed SkRegion
 *         @return      rectangular SkRegion
 *
 *         example: https://fiddle.skia.org/c/@Region_copy_const_SkIRect
 *     */
 *     explicit SkRegion(const SkIRect& rect);
 *
 *     /** Releases ownership of any shared data and deletes data if SkRegion is sole owner.
 *
 *         example: https://fiddle.skia.org/c/@Region_destructor
 *     */
 *     ~SkRegion();
 *
 *     /** Constructs a copy of an existing region.
 *         Makes two regions identical by value. Internally, region and
 *         the returned result share pointer values. The underlying SkRect array is
 *         copied when modified.
 *
 *         Creating a SkRegion copy is very efficient and never allocates memory.
 *         SkRegion are always copied by value from the interface; the underlying shared
 *         pointers are not exposed.
 *
 *         @param region  SkRegion to copy by value
 *         @return        SkRegion to copy by value
 *
 *         example: https://fiddle.skia.org/c/@Region_copy_operator
 *     */
 *     SkRegion& operator=(const SkRegion& region);
 *
 *     /** Compares SkRegion and other; returns true if they enclose exactly
 *         the same area.
 *
 *         @param other  SkRegion to compare
 *         @return       true if SkRegion pair are equivalent
 *
 *         example: https://fiddle.skia.org/c/@Region_equal1_operator
 *     */
 *     bool operator==(const SkRegion& other) const;
 *
 *     /** Compares SkRegion and other; returns true if they do not enclose the same area.
 *
 *         @param other  SkRegion to compare
 *         @return       true if SkRegion pair are not equivalent
 *     */
 *     bool operator!=(const SkRegion& other) const {
 *         return !(*this == other);
 *     }
 *
 *     /** Sets SkRegion to src, and returns true if src bounds is not empty.
 *         This makes SkRegion and src identical by value. Internally,
 *         SkRegion and src share pointer values. The underlying SkRect array is
 *         copied when modified.
 *
 *         Creating a SkRegion copy is very efficient and never allocates memory.
 *         SkRegion are always copied by value from the interface; the underlying shared
 *         pointers are not exposed.
 *
 *         @param src  SkRegion to copy
 *         @return     copy of src
 *     */
 *     bool set(const SkRegion& src) {
 *         *this = src;
 *         return !this->isEmpty();
 *     }
 *
 *     /** Exchanges SkIRect array of SkRegion and other. swap() internally exchanges pointers,
 *         so it is lightweight and does not allocate memory.
 *
 *         swap() usage has largely been replaced by operator=(const SkRegion& region).
 *         SkPath do not copy their content on assignment until they are written to,
 *         making assignment as efficient as swap().
 *
 *         @param other  operator=(const SkRegion& region) set
 *
 *         example: https://fiddle.skia.org/c/@Region_swap
 *     */
 *     void swap(SkRegion& other);
 *
 *     /** Returns true if SkRegion is empty.
 *         Empty SkRegion has bounds width or height less than or equal to zero.
 *         SkRegion() constructs empty SkRegion; setEmpty()
 *         and setRect() with dimensionless data make SkRegion empty.
 *
 *         @return  true if bounds has no width or height
 *     */
 *     bool isEmpty() const { return fRunHead == emptyRunHeadPtr(); }
 *
 *     /** Returns true if SkRegion is one SkIRect with positive dimensions.
 *
 *         @return  true if SkRegion contains one SkIRect
 *     */
 *     bool isRect() const { return fRunHead == kRectRunHeadPtr; }
 *
 *     /** Returns true if SkRegion is described by more than one rectangle.
 *
 *         @return  true if SkRegion contains more than one SkIRect
 *     */
 *     bool isComplex() const { return !this->isEmpty() && !this->isRect(); }
 *
 *     /** Returns minimum and maximum axes values of SkIRect array.
 *         Returns (0, 0, 0, 0) if SkRegion is empty.
 *
 *         @return  combined bounds of all SkIRect elements
 *     */
 *     const SkIRect& getBounds() const { return fBounds; }
 *
 *     /** Returns a value that increases with the number of
 *         elements in SkRegion. Returns zero if SkRegion is empty.
 *         Returns one if SkRegion equals SkIRect; otherwise, returns
 *         value greater than one indicating that SkRegion is complex.
 *
 *         Call to compare SkRegion for relative complexity.
 *
 *         @return  relative complexity
 *
 *         example: https://fiddle.skia.org/c/@Region_computeRegionComplexity
 *     */
 *     int computeRegionComplexity() const;
 *
 *     /** Appends outline of SkRegion to path builder.
 *         Returns true if SkRegion is not empty; otherwise, returns false, and leaves path
 *         unmodified.
 *
 *         @param path  SkPath to append to
 *         @return      true if path changed
 *
 *         example: https://fiddle.skia.org/c/@Region_getBoundaryPath
 *     */
 *     bool addBoundaryPath(SkPathBuilder*) const;
 *
 *     /**
 *      * Return the boundary of the region as a path.
 *      */
 *     SkPath getBoundaryPath() const;
 *
 *     /** Constructs an empty SkRegion. SkRegion is set to empty bounds
 *         at (0, 0) with zero width and height. Always returns false.
 *
 *         @return  false
 *
 *         example: https://fiddle.skia.org/c/@Region_setEmpty
 *     */
 *     bool setEmpty();
 *
 *     /** Constructs a rectangular SkRegion matching the bounds of rect.
 *         If rect is empty, constructs empty and returns false.
 *
 *         @param rect  bounds of constructed SkRegion
 *         @return      true if rect is not empty
 *
 *         example: https://fiddle.skia.org/c/@Region_setRect
 *     */
 *     bool setRect(const SkIRect& rect);
 *
 *     /** Constructs SkRegion as the union of SkIRect in rects array. If count is
 *         zero, constructs empty SkRegion. Returns false if constructed SkRegion is empty.
 *
 *         May be faster than repeated calls to op().
 *
 *         @param rects  array of SkIRect
 *         @param count  array size
 *         @return       true if constructed SkRegion is not empty
 *
 *         example: https://fiddle.skia.org/c/@Region_setRects
 *     */
 *     bool setRects(const SkIRect rects[], int count);
 *
 *     /** Constructs a copy of an existing region.
 *         Makes two regions identical by value. Internally, region and
 *         the returned result share pointer values. The underlying SkRect array is
 *         copied when modified.
 *
 *         Creating a SkRegion copy is very efficient and never allocates memory.
 *         SkRegion are always copied by value from the interface; the underlying shared
 *         pointers are not exposed.
 *
 *         @param region  SkRegion to copy by value
 *         @return        SkRegion to copy by value
 *
 *         example: https://fiddle.skia.org/c/@Region_setRegion
 *     */
 *     bool setRegion(const SkRegion& region);
 *
 *     /** Constructs SkRegion to match outline of path within clip.
 *         Returns false if constructed SkRegion is empty.
 *
 *         Constructed SkRegion draws the same pixels as path through clip when
 *         anti-aliasing is disabled.
 *
 *         @param path  SkPath providing outline
 *         @param clip  SkRegion containing path
 *         @return      true if constructed SkRegion is not empty
 *
 *         example: https://fiddle.skia.org/c/@Region_setPath
 *     */
 *     bool setPath(const SkPath& path, const SkRegion& clip);
 *
 *     /** Returns true if SkRegion intersects rect.
 *         Returns false if either rect or SkRegion is empty, or do not intersect.
 *
 *         @param rect  SkIRect to intersect
 *         @return      true if rect and SkRegion have area in common
 *
 *         example: https://fiddle.skia.org/c/@Region_intersects
 *     */
 *     bool intersects(const SkIRect& rect) const;
 *
 *     /** Returns true if SkRegion intersects other.
 *         Returns false if either other or SkRegion is empty, or do not intersect.
 *
 *         @param other  SkRegion to intersect
 *         @return       true if other and SkRegion have area in common
 *
 *         example: https://fiddle.skia.org/c/@Region_intersects_2
 *     */
 *     bool intersects(const SkRegion& other) const;
 *
 *     /** Returns true if SkIPoint (x, y) is inside SkRegion.
 *         Returns false if SkRegion is empty.
 *
 *         @param x  test SkIPoint x-coordinate
 *         @param y  test SkIPoint y-coordinate
 *         @return   true if (x, y) is inside SkRegion
 *
 *         example: https://fiddle.skia.org/c/@Region_contains
 *     */
 *     bool contains(int32_t x, int32_t y) const;
 *
 *     /** Returns true if other is completely inside SkRegion.
 *         Returns false if SkRegion or other is empty.
 *
 *         @param other  SkIRect to contain
 *         @return       true if other is inside SkRegion
 *
 *         example: https://fiddle.skia.org/c/@Region_contains_2
 *     */
 *     bool contains(const SkIRect& other) const;
 *
 *     /** Returns true if other is completely inside SkRegion.
 *         Returns false if SkRegion or other is empty.
 *
 *         @param other  SkRegion to contain
 *         @return       true if other is inside SkRegion
 *
 *         example: https://fiddle.skia.org/c/@Region_contains_3
 *     */
 *     bool contains(const SkRegion& other) const;
 *
 *     /** Returns true if SkRegion is a single rectangle and contains r.
 *         May return false even though SkRegion contains r.
 *
 *         @param r  SkIRect to contain
 *         @return   true quickly if r points are equal or inside
 *     */
 *     bool quickContains(const SkIRect& r) const {
 *         SkASSERT(this->isEmpty() == fBounds.isEmpty()); // valid region
 *
 *         return  r.fLeft < r.fRight && r.fTop < r.fBottom &&
 *                 fRunHead == kRectRunHeadPtr &&  // this->isRect()
 *                 /* fBounds.contains(left, top, right, bottom); */
 *                 fBounds.fLeft <= r.fLeft   && fBounds.fTop <= r.fTop &&
 *                 fBounds.fRight >= r.fRight && fBounds.fBottom >= r.fBottom;
 *     }
 *
 *     /** Returns true if SkRegion does not intersect rect.
 *         Returns true if rect is empty or SkRegion is empty.
 *         May return false even though SkRegion does not intersect rect.
 *
 *         @param rect  SkIRect to intersect
 *         @return      true if rect does not intersect
 *     */
 *     bool quickReject(const SkIRect& rect) const {
 *         return this->isEmpty() || rect.isEmpty() ||
 *                 !SkIRect::Intersects(fBounds, rect);
 *     }
 *
 *     /** Returns true if SkRegion does not intersect rgn.
 *         Returns true if rgn is empty or SkRegion is empty.
 *         May return false even though SkRegion does not intersect rgn.
 *
 *         @param rgn  SkRegion to intersect
 *         @return     true if rgn does not intersect
 *     */
 *     bool quickReject(const SkRegion& rgn) const {
 *         return this->isEmpty() || rgn.isEmpty() ||
 *                !SkIRect::Intersects(fBounds, rgn.fBounds);
 *     }
 *
 *     /** Offsets SkRegion by ivector (dx, dy). Has no effect if SkRegion is empty.
 *
 *         @param dx  x-axis offset
 *         @param dy  y-axis offset
 *     */
 *     void translate(int dx, int dy) { this->translate(dx, dy, this); }
 *
 *     /** Offsets SkRegion by ivector (dx, dy), writing result to dst. SkRegion may be passed
 *         as dst parameter, translating SkRegion in place. Has no effect if dst is nullptr.
 *         If SkRegion is empty, sets dst to empty.
 *
 *         @param dx   x-axis offset
 *         @param dy   y-axis offset
 *         @param dst  translated result
 *
 *         example: https://fiddle.skia.org/c/@Region_translate_2
 *     */
 *     void translate(int dx, int dy, SkRegion* dst) const;
 *
 *     /** \enum SkRegion::Op
 *         The logical operations that can be performed when combining two SkRegion.
 *     */
 *     enum Op {
 *         kDifference_Op,                      //!< target minus operand
 *         kIntersect_Op,                       //!< target intersected with operand
 *         kUnion_Op,                           //!< target unioned with operand
 *         kXOR_Op,                             //!< target exclusive or with operand
 *         kReverseDifference_Op,               //!< operand minus target
 *         kReplace_Op,                         //!< replace target with operand
 *         kLastOp               = kReplace_Op, //!< last operator
 *     };
 *
 *     static const int kOpCnt = kLastOp + 1;
 *
 *     /** Replaces SkRegion with the result of SkRegion op rect.
 *         Returns true if replaced SkRegion is not empty.
 *
 *         @param rect  SkIRect operand
 *         @return      false if result is empty
 *     */
 *     bool op(const SkIRect& rect, Op op) {
 *         if (this->isRect() && kIntersect_Op == op) {
 *             if (!fBounds.intersect(rect)) {
 *                 return this->setEmpty();
 *             }
 *             return true;
 *         }
 *         return this->op(*this, rect, op);
 *     }
 *
 *     /** Replaces SkRegion with the result of SkRegion op rgn.
 *         Returns true if replaced SkRegion is not empty.
 *
 *         @param rgn  SkRegion operand
 *         @return     false if result is empty
 *     */
 *     bool op(const SkRegion& rgn, Op op) { return this->op(*this, rgn, op); }
 *
 *     /** Replaces SkRegion with the result of rect op rgn.
 *         Returns true if replaced SkRegion is not empty.
 *
 *         @param rect  SkIRect operand
 *         @param rgn   SkRegion operand
 *         @return      false if result is empty
 *
 *         example: https://fiddle.skia.org/c/@Region_op_4
 *     */
 *     bool op(const SkIRect& rect, const SkRegion& rgn, Op op);
 *
 *     /** Replaces SkRegion with the result of rgn op rect.
 *         Returns true if replaced SkRegion is not empty.
 *
 *         @param rgn   SkRegion operand
 *         @param rect  SkIRect operand
 *         @return      false if result is empty
 *
 *         example: https://fiddle.skia.org/c/@Region_op_5
 *     */
 *     bool op(const SkRegion& rgn, const SkIRect& rect, Op op);
 *
 *     /** Replaces SkRegion with the result of rgna op rgnb.
 *         Returns true if replaced SkRegion is not empty.
 *
 *         @param rgna  SkRegion operand
 *         @param rgnb  SkRegion operand
 *         @return      false if result is empty
 *
 *         example: https://fiddle.skia.org/c/@Region_op_6
 *     */
 *     bool op(const SkRegion& rgna, const SkRegion& rgnb, Op op);
 *
 * #ifdef SK_BUILD_FOR_ANDROID_FRAMEWORK
 *     /** Private. Android framework only.
 *
 *         @return  string representation of SkRegion
 *     */
 *     char* toString();
 * #endif
 *
 *     /** \class SkRegion::Iterator
 *         Returns sequence of rectangles, sorted along y-axis, then x-axis, that make
 *         up SkRegion.
 *     */
 *     class SK_API Iterator {
 *     public:
 *
 *         /** Initializes SkRegion::Iterator with an empty SkRegion. done() on SkRegion::Iterator
 *             returns true.
 *             Call reset() to initialized SkRegion::Iterator at a later time.
 *
 *             @return  empty SkRegion iterator
 *         */
 *         Iterator() : fRgn(nullptr), fDone(true) {}
 *
 *         /** Sets SkRegion::Iterator to return elements of SkIRect array in region.
 *
 *             @param region  SkRegion to iterate
 *             @return        SkRegion iterator
 *
 *         example: https://fiddle.skia.org/c/@Region_Iterator_copy_const_SkRegion
 *         */
 *         Iterator(const SkRegion& region);
 *
 *         /** SkPoint SkRegion::Iterator to start of SkRegion.
 *             Returns true if SkRegion was set; otherwise, returns false.
 *
 *             @return  true if SkRegion was set
 *
 *         example: https://fiddle.skia.org/c/@Region_Iterator_rewind
 *         */
 *         bool rewind();
 *
 *         /** Resets iterator, using the new SkRegion.
 *
 *             @param region  SkRegion to iterate
 *
 *         example: https://fiddle.skia.org/c/@Region_Iterator_reset
 *         */
 *         void reset(const SkRegion& region);
 *
 *         /** Returns true if SkRegion::Iterator is pointing to final SkIRect in SkRegion.
 *
 *             @return  true if data parsing is complete
 *         */
 *         bool done() const { return fDone; }
 *
 *         /** Advances SkRegion::Iterator to next SkIRect in SkRegion if it is not done.
 *
 *         example: https://fiddle.skia.org/c/@Region_Iterator_next
 *         */
 *         void next();
 *
 *         /** Returns SkIRect element in SkRegion. Does not return predictable results if SkRegion
 *             is empty.
 *
 *             @return  part of SkRegion as SkIRect
 *         */
 *         const SkIRect& rect() const { return fRect; }
 *
 *         /** Returns SkRegion if set; otherwise, returns nullptr.
 *
 *             @return  iterated SkRegion
 *         */
 *         const SkRegion* rgn() const { return fRgn; }
 *
 *     private:
 *         const SkRegion* fRgn;
 *         const SkRegion::RunType*  fRuns;
 *         SkIRect         fRect = {0, 0, 0, 0};
 *         bool            fDone;
 *     };
 *
 *     /** \class SkRegion::Cliperator
 *         Returns the sequence of rectangles, sorted along y-axis, then x-axis, that make
 *         up SkRegion intersected with the specified clip rectangle.
 *     */
 *     class SK_API Cliperator {
 *     public:
 *
 *         /** Sets SkRegion::Cliperator to return elements of SkIRect array in SkRegion within clip.
 *
 *             @param region  SkRegion to iterate
 *             @param clip    bounds of iteration
 *             @return        SkRegion iterator
 *
 *         example: https://fiddle.skia.org/c/@Region_Cliperator_const_SkRegion_const_SkIRect
 *         */
 *         Cliperator(const SkRegion& region, const SkIRect& clip);
 *
 *         /** Returns true if SkRegion::Cliperator is pointing to final SkIRect in SkRegion.
 *
 *             @return  true if data parsing is complete
 *         */
 *         bool done() { return fDone; }
 *
 *         /** Advances iterator to next SkIRect in SkRegion contained by clip.
 *
 *         example: https://fiddle.skia.org/c/@Region_Cliperator_next
 *         */
 *         void  next();
 *
 *         /** Returns SkIRect element in SkRegion, intersected with clip passed to
 *             SkRegion::Cliperator constructor. Does not return predictable results if SkRegion
 *             is empty.
 *
 *             @return  part of SkRegion inside clip as SkIRect
 *         */
 *         const SkIRect& rect() const { return fRect; }
 *
 *     private:
 *         Iterator    fIter;
 *         SkIRect     fClip;
 *         SkIRect     fRect = {0, 0, 0, 0};
 *         bool        fDone;
 *     };
 *
 *     /** \class SkRegion::Spanerator
 *         Returns the line segment ends within SkRegion that intersect a horizontal line.
 *     */
 *     class SK_API Spanerator {
 *     public:
 *
 *         /** Sets SkRegion::Spanerator to return line segments in SkRegion on scan line.
 *
 *             @param region  SkRegion to iterate
 *             @param y       horizontal line to intersect
 *             @param left    bounds of iteration
 *             @param right   bounds of iteration
 *             @return        SkRegion iterator
 *
 *         example: https://fiddle.skia.org/c/@Region_Spanerator_const_SkRegion_int_int_int
 *         */
 *         Spanerator(const SkRegion& region, int y, int left, int right);
 *
 *         /** Advances iterator to next span intersecting SkRegion within line segment provided
 *             in constructor. Returns true if interval was found.
 *
 *             @param left   pointer to span start; may be nullptr
 *             @param right  pointer to span end; may be nullptr
 *             @return       true if interval was found
 *
 *         example: https://fiddle.skia.org/c/@Region_Spanerator_next
 *         */
 *         bool next(int* left, int* right);
 *
 *     private:
 *         const SkRegion::RunType* fRuns;
 *         int     fLeft, fRight;
 *         bool    fDone;
 *     };
 *
 *     /** Writes SkRegion to buffer, and returns number of bytes written.
 *         If buffer is nullptr, returns number number of bytes that would be written.
 *
 *         @param buffer  storage for binary data
 *         @return        size of SkRegion
 *
 *         example: https://fiddle.skia.org/c/@Region_writeToMemory
 *     */
 *     size_t writeToMemory(void* buffer) const;
 *
 *     /** Constructs SkRegion from buffer of size length. Returns bytes read.
 *         Returned value will be multiple of four or zero if length was too small.
 *
 *         @param buffer  storage for binary data
 *         @param length  size of buffer
 *         @return        bytes read
 *
 *         example: https://fiddle.skia.org/c/@Region_readFromMemory
 *     */
 *     size_t readFromMemory(const void* buffer, size_t length);
 *
 *     using sk_is_trivially_relocatable = std::true_type;
 *
 * private:
 *     static constexpr int kOpCount = kReplace_Op + 1;
 *
 *     // T
 *     // [B N L R S]
 *     // S
 *     static constexpr int kRectRegionRuns = 7;
 *
 *     struct RunHead;
 *
 *     static RunHead* emptyRunHeadPtr() { return (SkRegion::RunHead*) -1; }
 *     static constexpr const RunHead* const kRectRunHeadPtr = nullptr;
 *
 *     // allocate space for count runs
 *     void allocateRuns(int count);
 *     void allocateRuns(int count, int ySpanCount, int intervalCount);
 *     void allocateRuns(const RunHead& src);
 *
 *     SkDEBUGCODE(void dump() const;)
 *
 *     SkIRect     fBounds;
 *     RunHead*    fRunHead;
 *
 *     static_assert(::sk_is_trivially_relocatable<decltype(fBounds)>::value);
 *     static_assert(::sk_is_trivially_relocatable<decltype(fRunHead)>::value);
 *
 *     void freeRuns();
 *
 *     /**
 *      *  Return the runs from this region, consing up fake runs if the region
 *      *  is empty or a rect. In those 2 cases, we use tmpStorage to hold the
 *      *  run data.
 *      */
 *     const RunType*  getRuns(RunType tmpStorage[], int* intervals) const;
 *
 *     // This is called with runs[] that do not yet have their interval-count
 *     // field set on each scanline. That is computed as part of this call
 *     // (inside ComputeRunBounds).
 *     bool setRuns(RunType runs[], int count);
 *
 *     int count_runtype_values(int* itop, int* ibot) const;
 *
 *     bool isValid() const;
 *
 *     static void BuildRectRuns(const SkIRect& bounds,
 *                               RunType runs[kRectRegionRuns]);
 *
 *     // If the runs define a simple rect, return true and set bounds to that
 *     // rect. If not, return false and ignore bounds.
 *     static bool RunsAreARect(const SkRegion::RunType runs[], int count,
 *                              SkIRect* bounds);
 *
 *     /**
 *      *  If the last arg is null, just return if the result is non-empty,
 *      *  else store the result in the last arg.
 *      */
 *     static bool Oper(const SkRegion&, const SkRegion&, SkRegion::Op, SkRegion*);
 *
 *     friend struct RunHead;
 *     friend class Iterator;
 *     friend class Spanerator;
 *     friend class SkRegionPriv;
 *     friend class SkRgnBuilder;
 *     friend class SkFlatRegion;
 * }
 * ```
 */
public data class SkRegion public constructor(
  /**
   * C++ original:
   * ```cpp
   * static const int kOpCnt = kLastOp + 1
   * ```
   */
  private var fRunHead: UndefinedRunHead?,
) {
  /**
   * C++ original:
   * ```cpp
   * SkRegion& SkRegion::operator=(const SkRegion& src) {
   *     (void)this->setRegion(src);
   *     return *this;
   * }
   * ```
   */
  public fun assign(region: SkRegion) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRegion::operator==(const SkRegion& b) const {
   *     SkDEBUGCODE(SkRegionPriv::Validate(*this));
   *     SkDEBUGCODE(SkRegionPriv::Validate(b));
   *
   *     if (this == &b) {
   *         return true;
   *     }
   *     if (fBounds != b.fBounds) {
   *         return false;
   *     }
   *
   *     const SkRegion::RunHead* ah = fRunHead;
   *     const SkRegion::RunHead* bh = b.fRunHead;
   *
   *     // this catches empties and rects being equal
   *     if (ah == bh) {
   *         return true;
   *     }
   *     // now we insist that both are complex (but different ptrs)
   *     if (!this->isComplex() || !b.isComplex()) {
   *         return false;
   *     }
   *     return  ah->fRunCount == bh->fRunCount &&
   *             !memcmp(ah->readonly_runs(), bh->readonly_runs(),
   *                     ah->fRunCount * sizeof(SkRegion::RunType));
   * }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkRegion& other) const {
   *         return !(*this == other);
   *     }
   * ```
   */
  public fun `set`(src: SkRegion): Boolean {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * bool set(const SkRegion& src) {
   *         *this = src;
   *         return !this->isEmpty();
   *     }
   * ```
   */
  public fun swap(other: SkRegion) {
    TODO("Implement swap")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRegion::swap(SkRegion& other) {
   *     using std::swap;
   *     swap(fBounds, other.fBounds);
   *     swap(fRunHead, other.fRunHead);
   * }
   * ```
   */
  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmpty() const { return fRunHead == emptyRunHeadPtr(); }
   * ```
   */
  public fun isRect(): Boolean {
    TODO("Implement isRect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isRect() const { return fRunHead == kRectRunHeadPtr; }
   * ```
   */
  public fun isComplex(): Boolean {
    TODO("Implement isComplex")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isComplex() const { return !this->isEmpty() && !this->isRect(); }
   * ```
   */
  public fun getBounds(): Int {
    TODO("Implement getBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkIRect& getBounds() const { return fBounds; }
   * ```
   */
  public fun computeRegionComplexity(): Int {
    TODO("Implement computeRegionComplexity")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkRegion::computeRegionComplexity() const {
   *   if (this->isEmpty()) {
   *     return 0;
   *   } else if (this->isRect()) {
   *     return 1;
   *   }
   *   return fRunHead->getIntervalCount();
   * }
   * ```
   */
  public fun addBoundaryPath(builder: SkPathBuilder?): Boolean {
    TODO("Implement addBoundaryPath")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRegion::addBoundaryPath(SkPathBuilder* builder) const {
   *     // path could safely be nullptr if we're empty, but the caller shouldn't
   *     // *know* that
   *     SkASSERT(builder);
   *
   *     if (this->isEmpty()) {
   *         return false;
   *     }
   *
   *     const SkIRect& bounds = this->getBounds();
   *
   *     if (this->isRect()) {
   *         SkRect  r;
   *         r.set(bounds);      // this converts the ints to scalars
   *         builder->addRect(r);
   *         return true;
   *     }
   *
   *     SkRegion::Iterator  iter(*this);
   *     SkTDArray<Edge>     edges;
   *
   *     for (const SkIRect& r = iter.rect(); !iter.done(); iter.next()) {
   *         Edge* edge = edges.append(2);
   *         edge[0].set(r.fLeft, r.fBottom, r.fTop);
   *         edge[1].set(r.fRight, r.fTop, r.fBottom);
   *     }
   *
   *     int count = edges.size();
   *     Edge* start = edges.begin();
   *     Edge* stop = start + count;
   *     SkTQSort<Edge>(start, stop, EdgeLT());
   *
   *     Edge* e;
   *     for (e = start; e != stop; e++) {
   *         find_link(e, stop);
   *     }
   *
   * #ifdef SK_DEBUG
   *     for (e = start; e != stop; e++) {
   *         SkASSERT(e->fNext != nullptr);
   *         SkASSERT(e->fFlags == Edge::kCompleteLink);
   *     }
   * #endif
   *
   *     builder->incReserve(count << 1);
   *     do {
   *         SkASSERT(count > 1);
   *         count -= extract_path(start, stop, builder);
   *     } while (count > 0);
   *
   *     return true;
   * }
   * ```
   */
  public fun getBoundaryPath(): Int {
    TODO("Implement getBoundaryPath")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath SkRegion::getBoundaryPath() const {
   *     SkPathBuilder builder;
   *     (void)this->addBoundaryPath(&builder);
   *     return builder.detach();
   * }
   * ```
   */
  public fun setEmpty(): Boolean {
    TODO("Implement setEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRegion::setEmpty() {
   *     this->freeRuns();
   *     fBounds.setEmpty();
   *     fRunHead = SkRegion_gEmptyRunHeadPtr;
   *     return false;
   * }
   * ```
   */
  public fun setRect(rect: SkIRect): Boolean {
    TODO("Implement setRect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRegion::setRect(const SkIRect& r) {
   *     if (r.isEmpty() ||
   *         SkRegion_kRunTypeSentinel == r.right() ||
   *         SkRegion_kRunTypeSentinel == r.bottom()) {
   *         return this->setEmpty();
   *     }
   *     this->freeRuns();
   *     fBounds = r;
   *     fRunHead = SkRegion_gRectRunHeadPtr;
   *     return true;
   * }
   * ```
   */
  public fun setRects(rects: Array<SkIRect>, count: Int): Boolean {
    TODO("Implement setRects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRegion::setRects(const SkIRect rects[], int count) {
   *     if (0 == count) {
   *         this->setEmpty();
   *     } else {
   *         this->setRect(rects[0]);
   *         for (int i = 1; i < count; i++) {
   *             this->op(rects[i], kUnion_Op);
   *         }
   *     }
   *     return !this->isEmpty();
   * }
   * ```
   */
  public fun setRegion(region: SkRegion): Boolean {
    TODO("Implement setRegion")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRegion::setRegion(const SkRegion& src) {
   *     if (this != &src) {
   *         this->freeRuns();
   *
   *         fBounds = src.fBounds;
   *         fRunHead = src.fRunHead;
   *         if (this->isComplex()) {
   *             fRunHead->fRefCnt++;
   *         }
   *     }
   *     return fRunHead != SkRegion_gEmptyRunHeadPtr;
   * }
   * ```
   */
  public fun setPath(path: SkPath, clip: SkRegion): Boolean {
    TODO("Implement setPath")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRegion::setPath(const SkPath& path, const SkRegion& clip) {
   *     SkDEBUGCODE(SkRegionPriv::Validate(*this));
   *
   *     const auto raw = SkPathPriv::Raw(path, SkResolveConvexity::kYes);
   *
   *     if (clip.isEmpty() || !raw.has_value() || path.isEmpty()) {
   *         // This treats non-finite paths (no raw) as empty as well, so this returns empty or 'clip'
   *         // if it's inverse-filled. If clip is also empty, path's fill type doesn't really matter
   *         // and this region ends up empty.
   *         return check_inverse_on_empty_return(this, path, clip);
   *     }
   *
   *     // Our builder is very fragile, and can't be called with spans/rects out of Y->X order.
   *     // To ensure this, we only "fill" clipped to a rect (the clip's bounds), and if the
   *     // clip is more complex than that, we just post-intersect the result with the clip.
   *     const SkIRect clipBounds = clip.getBounds();
   *     if (clip.isComplex()) {
   *         if (!this->setPath(path, SkRegion(clipBounds))) {
   *             return false;
   *         }
   *         return this->op(clip, kIntersect_Op);
   *     }
   *
   *     // SkScan::FillPath has limits on the coordinate range of the clipping SkRegion. If it's too
   *     // big, tile the clip bounds and union the pieces back together.
   *     if (SkScan::PathRequiresTiling(clipBounds)) {
   *         static constexpr int kTileSize = 32767 >> 1; // Limit so coords can fit into SkFixed (16.16)
   *         const SkIRect pathBounds = path.getBounds().roundOut();
   *
   *         this->setEmpty();
   *
   *         // Note: With large integers some intermediate calculations can overflow, but the
   *         // end results will still be in integer range. Using int64_t for the intermediate
   *         // values will handle this situation.
   *         for (int64_t top = clipBounds.fTop; top < clipBounds.fBottom; top += kTileSize) {
   *             int64_t bot = std::min(top + kTileSize, (int64_t)clipBounds.fBottom);
   *             for (int64_t left = clipBounds.fLeft; left < clipBounds.fRight; left += kTileSize) {
   *                 int64_t right = std::min(left + kTileSize, (int64_t)clipBounds.fRight);
   *
   *                 SkIRect tileClipBounds = {(int)left, (int)top, (int)right, (int)bot};
   *                 if (!SkIRect::Intersects(pathBounds, tileClipBounds)) {
   *                     continue;
   *                 }
   *
   *                 // Shift coordinates so the top left is (0,0) during scan conversion and then
   *                 // translate the SkRegion afterwards.
   *                 tileClipBounds.offset(-left, -top);
   *                 SkASSERT(!SkScan::PathRequiresTiling(tileClipBounds));
   *                 SkRegion tile;
   *                 if (auto newpath = path.tryMakeOffset(-left, -top)) {
   *                     tile.setPath(*newpath, SkRegion(tileClipBounds));
   *                     tile.translate(left, top);
   *                     this->op(tile, kUnion_Op);
   *                 } else {
   *                     return false;
   *                 }
   *             }
   *         }
   *         // During tiling we only applied the bounds of the tile, now that we have a full SkRegion,
   *         // apply the original clip.
   *         return this->op(clip, kIntersect_Op);
   *     }
   *
   *     //  compute worst-case rgn-size for the path
   *     int pathTop, pathBot;
   *     int pathTransitions = count_path_runtype_values(path, &pathTop, &pathBot);
   *     if (0 == pathTransitions) {
   *         return check_inverse_on_empty_return(this, path, clip);
   *     }
   *
   *     int clipTop, clipBot;
   *     int clipTransitions = clip.count_runtype_values(&clipTop, &clipBot);
   *
   *     int top = std::max(pathTop, clipTop);
   *     int bot = std::min(pathBot, clipBot);
   *     if (top >= bot) {
   *         return check_inverse_on_empty_return(this, path, clip);
   *     }
   *
   *     SkRgnBuilder builder;
   *
   *     if (!builder.init(bot - top,
   *                       std::max(pathTransitions, clipTransitions),
   *                       path.isInverseFillType())) {
   *         // can't allocate working space, so return false
   *         return this->setEmpty();
   *     }
   *
   *     SkScan::FillPath(*raw, clip, &builder);
   *     builder.done();
   *
   *     int count = builder.computeRunCount();
   *     if (count == 0) {
   *         return this->setEmpty();
   *     } else if (count == kRectRegionRuns) {
   *         builder.copyToRect(&fBounds);
   *         this->setRect(fBounds);
   *     } else {
   *         SkRegion tmp;
   *
   *         tmp.fRunHead = RunHead::Alloc(count);
   *         builder.copyToRgn(tmp.fRunHead->writable_runs());
   *         tmp.fRunHead->computeRunBounds(&tmp.fBounds);
   *         this->swap(tmp);
   *     }
   *     SkDEBUGCODE(SkRegionPriv::Validate(*this));
   *     return true;
   * }
   * ```
   */
  public fun intersects(rect: SkIRect): Boolean {
    TODO("Implement intersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool intersects(const SkIRect& rect) const
   * ```
   */
  public fun intersects(other: SkRegion): Boolean {
    TODO("Implement intersects")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRegion::intersects(const SkRegion& rgn) const {
   *     if (this->isEmpty() || rgn.isEmpty()) {
   *         return false;
   *     }
   *
   *     if (!SkIRect::Intersects(fBounds, rgn.fBounds)) {
   *         return false;
   *     }
   *
   *     bool weAreARect = this->isRect();
   *     bool theyAreARect = rgn.isRect();
   *
   *     if (weAreARect && theyAreARect) {
   *         return true;
   *     }
   *     if (weAreARect) {
   *         return rgn.intersects(this->getBounds());
   *     }
   *     if (theyAreARect) {
   *         return this->intersects(rgn.getBounds());
   *     }
   *
   *     // both of us are complex
   *     return Oper(*this, rgn, kIntersect_Op, nullptr);
   * }
   * ```
   */
  public fun contains(x: Int, y: Int): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRegion::contains(int32_t x, int32_t y) const {
   *     SkDEBUGCODE(SkRegionPriv::Validate(*this));
   *
   *     if (!fBounds.contains(x, y)) {
   *         return false;
   *     }
   *     if (this->isRect()) {
   *         return true;
   *     }
   *     SkASSERT(this->isComplex());
   *
   *     const RunType* runs = fRunHead->findScanline(y);
   *
   *     // Skip the Bottom and IntervalCount
   *     runs += 2;
   *
   *     // Just walk this scanline, checking each interval. The X-sentinel will
   *     // appear as a left-inteval (runs[0]) and should abort the search.
   *     //
   *     // We could do a bsearch, using interval-count (runs[1]), but need to time
   *     // when that would be worthwhile.
   *     //
   *     for (;;) {
   *         if (x < runs[0]) {
   *             break;
   *         }
   *         if (x < runs[1]) {
   *             return true;
   *         }
   *         runs += 2;
   *     }
   *     return false;
   * }
   * ```
   */
  public fun contains(other: SkIRect): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool contains(const SkIRect& other) const
   * ```
   */
  public fun contains(other: SkRegion): Boolean {
    TODO("Implement contains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRegion::contains(const SkRegion& rgn) const {
   *     SkDEBUGCODE(SkRegionPriv::Validate(*this));
   *     SkDEBUGCODE(SkRegionPriv::Validate(rgn));
   *
   *     if (this->isEmpty() || rgn.isEmpty() || !fBounds.contains(rgn.fBounds)) {
   *         return false;
   *     }
   *     if (this->isRect()) {
   *         return true;
   *     }
   *     if (rgn.isRect()) {
   *         return this->contains(rgn.getBounds());
   *     }
   *
   *     /*
   *      *  A contains B is equivalent to
   *      *  B - A == 0
   *      */
   *     return !Oper(rgn, *this, kDifference_Op, nullptr);
   * }
   * ```
   */
  public fun quickContains(r: SkIRect): Boolean {
    TODO("Implement quickContains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool quickContains(const SkIRect& r) const {
   *         SkASSERT(this->isEmpty() == fBounds.isEmpty()); // valid region
   *
   *         return  r.fLeft < r.fRight && r.fTop < r.fBottom &&
   *                 fRunHead == kRectRunHeadPtr &&  // this->isRect()
   *                 /* fBounds.contains(left, top, right, bottom); */
   *                 fBounds.fLeft <= r.fLeft   && fBounds.fTop <= r.fTop &&
   *                 fBounds.fRight >= r.fRight && fBounds.fBottom >= r.fBottom;
   *     }
   * ```
   */
  public fun quickReject(rect: SkIRect): Boolean {
    TODO("Implement quickReject")
  }

  /**
   * C++ original:
   * ```cpp
   * bool quickReject(const SkIRect& rect) const {
   *         return this->isEmpty() || rect.isEmpty() ||
   *                 !SkIRect::Intersects(fBounds, rect);
   *     }
   * ```
   */
  public fun quickReject(rgn: SkRegion): Boolean {
    TODO("Implement quickReject")
  }

  /**
   * C++ original:
   * ```cpp
   * bool quickReject(const SkRegion& rgn) const {
   *         return this->isEmpty() || rgn.isEmpty() ||
   *                !SkIRect::Intersects(fBounds, rgn.fBounds);
   *     }
   * ```
   */
  public fun translate(dx: Int, dy: Int) {
    TODO("Implement translate")
  }

  /**
   * C++ original:
   * ```cpp
   * void translate(int dx, int dy) { this->translate(dx, dy, this); }
   * ```
   */
  public fun translate(
    dx: Int,
    dy: Int,
    dst: SkRegion?,
  ) {
    TODO("Implement translate")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRegion::translate(int dx, int dy, SkRegion* dst) const {
   *     SkDEBUGCODE(SkRegionPriv::Validate(*this));
   *
   *     if (nullptr == dst) {
   *         return;
   *     }
   *     if (this->isEmpty()) {
   *         dst->setEmpty();
   *         return;
   *     }
   *     // pin dx and dy so we don't overflow our existing bounds
   *     dx = pin_offset_s32(fBounds.fLeft, fBounds.fRight, dx);
   *     dy = pin_offset_s32(fBounds.fTop, fBounds.fBottom, dy);
   *
   *     if (this->isRect()) {
   *         dst->setRect(fBounds.makeOffset(dx, dy));
   *     } else {
   *         if (this == dst) {
   *             dst->fRunHead = dst->fRunHead->ensureWritable();
   *         } else {
   *             SkRegion    tmp;
   *             tmp.allocateRuns(*fRunHead);
   *             SkASSERT(tmp.isComplex());
   *             tmp.fBounds = fBounds;
   *             dst->swap(tmp);
   *         }
   *
   *         dst->fBounds.offset(dx, dy);
   *
   *         const RunType*  sruns = fRunHead->readonly_runs();
   *         RunType*        druns = dst->fRunHead->writable_runs();
   *
   *         *druns++ = (SkRegion::RunType)(*sruns++ + dy);    // top
   *         for (;;) {
   *             int bottom = *sruns++;
   *             if (bottom == SkRegion_kRunTypeSentinel) {
   *                 break;
   *             }
   *             *druns++ = (SkRegion::RunType)(bottom + dy);  // bottom;
   *             *druns++ = *sruns++;    // copy intervalCount;
   *             for (;;) {
   *                 int x = *sruns++;
   *                 if (x == SkRegion_kRunTypeSentinel) {
   *                     break;
   *                 }
   *                 *druns++ = (SkRegion::RunType)(x + dx);
   *                 *druns++ = (SkRegion::RunType)(*sruns++ + dx);
   *             }
   *             *druns++ = SkRegion_kRunTypeSentinel;    // x sentinel
   *         }
   *         *druns++ = SkRegion_kRunTypeSentinel;    // y sentinel
   *
   *         SkASSERT(sruns - fRunHead->readonly_runs() == fRunHead->fRunCount);
   *         SkASSERT(druns - dst->fRunHead->readonly_runs() == dst->fRunHead->fRunCount);
   *     }
   *
   *     SkDEBUGCODE(SkRegionPriv::Validate(*this));
   * }
   * ```
   */
  public fun op(rect: SkIRect, op: Op): Boolean {
    TODO("Implement op")
  }

  /**
   * C++ original:
   * ```cpp
   * bool op(const SkIRect& rect, Op op) {
   *         if (this->isRect() && kIntersect_Op == op) {
   *             if (!fBounds.intersect(rect)) {
   *                 return this->setEmpty();
   *             }
   *             return true;
   *         }
   *         return this->op(*this, rect, op);
   *     }
   * ```
   */
  public fun op(rgn: SkRegion, op: Op): Boolean {
    TODO("Implement op")
  }

  /**
   * C++ original:
   * ```cpp
   * bool op(const SkRegion& rgn, Op op) { return this->op(*this, rgn, op); }
   * ```
   */
  public fun op(
    rect: SkIRect,
    rgn: SkRegion,
    op: Op,
  ): Boolean {
    TODO("Implement op")
  }

  /**
   * C++ original:
   * ```cpp
   * bool op(const SkIRect& rect, const SkRegion& rgn, Op op)
   * ```
   */
  public fun op(
    rgn: SkRegion,
    rect: SkIRect,
    op: Op,
  ): Boolean {
    TODO("Implement op")
  }

  /**
   * C++ original:
   * ```cpp
   * bool op(const SkRegion& rgn, const SkIRect& rect, Op op)
   * ```
   */
  public fun op(
    rgna: SkRegion,
    rgnb: SkRegion,
    op: Op,
  ): Boolean {
    TODO("Implement op")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRegion::op(const SkRegion& rgna, const SkRegion& rgnb, Op op) {
   *     SkDEBUGCODE(SkRegionPriv::Validate(*this));
   *     return SkRegion::Oper(rgna, rgnb, op, this);
   * }
   * ```
   */
  private fun writeToMemory(buffer: Unit?): ULong {
    TODO("Implement writeToMemory")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkRegion::writeToMemory(void* storage) const {
   *     if (nullptr == storage) {
   *         size_t size = sizeof(int32_t); // -1 (empty), 0 (rect), runCount
   *         if (!this->isEmpty()) {
   *             size += sizeof(fBounds);
   *             if (this->isComplex()) {
   *                 size += 2 * sizeof(int32_t);    // ySpanCount + intervalCount
   *                 size += fRunHead->fRunCount * sizeof(RunType);
   *             }
   *         }
   *         return size;
   *     }
   *
   *     SkWBuffer   buffer(storage);
   *
   *     if (this->isEmpty()) {
   *         buffer.write32(-1);
   *     } else {
   *         bool isRect = this->isRect();
   *
   *         buffer.write32(isRect ? 0 : fRunHead->fRunCount);
   *         buffer.write(&fBounds, sizeof(fBounds));
   *
   *         if (!isRect) {
   *             buffer.write32(fRunHead->getYSpanCount());
   *             buffer.write32(fRunHead->getIntervalCount());
   *             buffer.write(fRunHead->readonly_runs(),
   *                          fRunHead->fRunCount * sizeof(RunType));
   *         }
   *     }
   *     return buffer.pos();
   * }
   * ```
   */
  private fun readFromMemory(buffer: Unit?, length: ULong): ULong {
    TODO("Implement readFromMemory")
  }

  /**
   * C++ original:
   * ```cpp
   * size_t SkRegion::readFromMemory(const void* storage, size_t length) {
   *     SkRBuffer   buffer(storage, length);
   *     SkRegion    tmp;
   *     int32_t     count;
   *
   *     // Serialized Region Format:
   *     //    Empty:
   *     //       -1
   *     //    Simple Rect:
   *     //       0  LEFT TOP RIGHT BOTTOM
   *     //    Complex Region:
   *     //       COUNT LEFT TOP RIGHT BOTTOM Y_SPAN_COUNT TOTAL_INTERVAL_COUNT [RUNS....]
   *     if (!buffer.readS32(&count) || count < -1) {
   *         return 0;
   *     }
   *     if (count >= 0) {
   *         if (!buffer.read(&tmp.fBounds, sizeof(tmp.fBounds)) || tmp.fBounds.isEmpty()) {
   *             return 0;  // Short buffer or bad bounds for non-empty region; report failure.
   *         }
   *         if (count == 0) {
   *             tmp.fRunHead = SkRegion_gRectRunHeadPtr;
   *         } else {
   *             int32_t ySpanCount, intervalCount;
   *             if (!buffer.readS32(&ySpanCount) ||
   *                 !buffer.readS32(&intervalCount) ||
   *                 buffer.available() < count * sizeof(int32_t)) {
   *                 return 0;
   *             }
   *             if (!validate_run((const int32_t*)((const char*)storage + buffer.pos()), count,
   *                               tmp.fBounds, ySpanCount, intervalCount)) {
   *                 return 0;  // invalid runs, don't even allocate
   *             }
   *             tmp.allocateRuns(count, ySpanCount, intervalCount);
   *             SkASSERT(tmp.isComplex());
   *             SkAssertResult(buffer.read(tmp.fRunHead->writable_runs(), count * sizeof(int32_t)));
   *         }
   *     }
   *     SkASSERT(tmp.isValid());
   *     SkASSERT(buffer.isValid());
   *     this->swap(tmp);
   *     return buffer.pos();
   * }
   * ```
   */
  private fun allocateRuns(count: Int) {
    TODO("Implement allocateRuns")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRegion::allocateRuns(int count) {
   *     fRunHead = RunHead::Alloc(count);
   * }
   * ```
   */
  private fun allocateRuns(
    count: Int,
    ySpanCount: Int,
    intervalCount: Int,
  ) {
    TODO("Implement allocateRuns")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRegion::allocateRuns(int count, int ySpanCount, int intervalCount) {
   *     fRunHead = RunHead::Alloc(count, ySpanCount, intervalCount);
   * }
   * ```
   */
  private fun allocateRuns(src: UndefinedRunHead) {
    TODO("Implement allocateRuns")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRegion::allocateRuns(const RunHead& head) {
   *     fRunHead = RunHead::Alloc(head.fRunCount,
   *                               head.getYSpanCount(),
   *                               head.getIntervalCount());
   * }
   * ```
   */
  private fun skDEBUGCODE(param0: () -> Unit): Int {
    TODO("Implement skDEBUGCODE")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDEBUGCODE(void dump() const;)
   * ```
   */
  private fun freeRuns() {
    TODO("Implement freeRuns")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkRegion::freeRuns() {
   *     if (this->isComplex()) {
   *         SkASSERT(fRunHead->fRefCnt >= 1);
   *         if (--fRunHead->fRefCnt == 0) {
   *             sk_free(fRunHead);
   *         }
   *     }
   * }
   * ```
   */
  private fun getRuns(tmpStorage: Array<SkRegionRunType>, intervals: Int?): SkRegionRunType {
    TODO("Implement getRuns")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkRegion::RunType* SkRegion::getRuns(RunType tmpStorage[],
   *                                            int* intervals) const {
   *     SkASSERT(tmpStorage && intervals);
   *     const RunType* runs = tmpStorage;
   *
   *     if (this->isEmpty()) {
   *         tmpStorage[0] = SkRegion_kRunTypeSentinel;
   *         *intervals = 0;
   *     } else if (this->isRect()) {
   *         BuildRectRuns(fBounds, tmpStorage);
   *         *intervals = 1;
   *     } else {
   *         runs = fRunHead->readonly_runs();
   *         *intervals = fRunHead->getIntervalCount();
   *     }
   *     return runs;
   * }
   * ```
   */
  private fun setRuns(runs: Array<SkRegionRunType>, count: Int): Boolean {
    TODO("Implement setRuns")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRegion::setRuns(RunType runs[], int count) {
   *     SkDEBUGCODE(SkRegionPriv::Validate(*this));
   *     SkASSERT(count > 0);
   *
   *     if (isRunCountEmpty(count)) {
   *     //  SkDEBUGF("setRuns: empty\n");
   *         assert_sentinel(runs[count-1], true);
   *         return this->setEmpty();
   *     }
   *
   *     // trim off any empty spans from the top and bottom
   *     // weird I should need this, perhaps op() could be smarter...
   *     if (count > kRectRegionRuns) {
   *         RunType* stop = runs + count;
   *         assert_sentinel(runs[0], false);    // top
   *         assert_sentinel(runs[1], false);    // bottom
   *         // runs[2] is uncomputed intervalCount
   *
   *         if (runs[3] == SkRegion_kRunTypeSentinel) {  // should be first left...
   *             runs += 3;  // skip empty initial span
   *             runs[0] = runs[-2]; // set new top to prev bottom
   *             assert_sentinel(runs[1], false);    // bot: a sentinal would mean two in a row
   *             assert_sentinel(runs[2], false);    // intervalcount
   *             assert_sentinel(runs[3], false);    // left
   *             assert_sentinel(runs[4], false);    // right
   *         }
   *
   *         assert_sentinel(stop[-1], true);
   *         assert_sentinel(stop[-2], true);
   *
   *         // now check for a trailing empty span
   *         if (stop[-5] == SkRegion_kRunTypeSentinel) { // eek, stop[-4] was a bottom with no x-runs
   *             stop[-4] = SkRegion_kRunTypeSentinel;    // kill empty last span
   *             stop -= 3;
   *             assert_sentinel(stop[-1], true);    // last y-sentinel
   *             assert_sentinel(stop[-2], true);    // last x-sentinel
   *             assert_sentinel(stop[-3], false);   // last right
   *             assert_sentinel(stop[-4], false);   // last left
   *             assert_sentinel(stop[-5], false);   // last interval-count
   *             assert_sentinel(stop[-6], false);   // last bottom
   *         }
   *         count = (int)(stop - runs);
   *     }
   *
   *     SkASSERT(count >= kRectRegionRuns);
   *
   *     if (SkRegion::RunsAreARect(runs, count, &fBounds)) {
   *         return this->setRect(fBounds);
   *     }
   *
   *     //  if we get here, we need to become a complex region
   *
   *     if (!this->isComplex() || fRunHead->fRunCount != count) {
   *         this->freeRuns();
   *         this->allocateRuns(count);
   *         SkASSERT(this->isComplex());
   *     }
   *
   *     // must call this before we can write directly into runs()
   *     // in case we are sharing the buffer with another region (copy on write)
   *     fRunHead = fRunHead->ensureWritable();
   *     memcpy(fRunHead->writable_runs(), runs, count * sizeof(RunType));
   *     fRunHead->computeRunBounds(&fBounds);
   *
   *     // Our computed bounds might be too large, so we have to check here.
   *     if (fBounds.isEmpty()) {
   *         return this->setEmpty();
   *     }
   *
   *     SkDEBUGCODE(SkRegionPriv::Validate(*this));
   *
   *     return true;
   * }
   * ```
   */
  private fun countRuntypeValues(itop: Int?, ibot: Int?): Int {
    TODO("Implement countRuntypeValues")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkRegion::count_runtype_values(int* itop, int* ibot) const {
   *     int maxT;
   *
   *     if (this->isRect()) {
   *         maxT = 2;
   *     } else {
   *         SkASSERT(this->isComplex());
   *         maxT = fRunHead->getIntervalCount() * 2;
   *     }
   *     *itop = fBounds.fTop;
   *     *ibot = fBounds.fBottom;
   *     return maxT;
   * }
   * ```
   */
  private fun isValid(): Boolean {
    TODO("Implement isValid")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkRegion::isValid() const {
   *     if (this->isEmpty()) {
   *         return fBounds == SkIRect{0, 0, 0, 0};
   *     }
   *     if (fBounds.isEmpty()) {
   *         return false;
   *     }
   *     if (this->isRect()) {
   *         return true;
   *     }
   *     return fRunHead && fRunHead->fRefCnt > 0 &&
   *            validate_run(fRunHead->readonly_runs(), fRunHead->fRunCount, fBounds,
   *                         fRunHead->getYSpanCount(), fRunHead->getIntervalCount());
   * }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  public data class Iterator public constructor(
    private val fRgn: SkRegion?,
    private val fRuns: SkRegionRunType?,
    private var fRect: Int,
    private var fDone: Boolean,
  ) {
    public fun rewind(): Boolean {
      TODO("Implement rewind")
    }

    public fun reset(region: SkRegion) {
      TODO("Implement reset")
    }

    public fun done(): Boolean {
      TODO("Implement done")
    }

    public fun next() {
      TODO("Implement next")
    }

    public fun rect(): Int {
      TODO("Implement rect")
    }

    public fun rgn(): SkRegion {
      TODO("Implement rgn")
    }
  }

  public data class Cliperator public constructor(
    private var fIter: undefined.Iterator,
    private var fClip: Int,
    private var fRect: Int,
    private var fDone: Boolean,
  ) {
    public fun done(): Boolean {
      TODO("Implement done")
    }

    public fun next() {
      TODO("Implement next")
    }

    public fun rect(): Int {
      TODO("Implement rect")
    }
  }

  public data class Spanerator public constructor(
    private val fRuns: SkRegionRunType?,
    private var fLeft: Int,
    private var fRight: Int,
    private var fDone: Boolean,
  ) {
    public fun next(left: Int?, right: Int?): Boolean {
      TODO("Implement next")
    }
  }

  public enum class Op {
    kDifference_Op,
    kIntersect_Op,
    kUnion_Op,
    kXOR_Op,
    kReverseDifference_Op,
    kReplace_Op,
    kLastOp,
  }

  public companion object {
    public val kOpCnt: Int = TODO("Initialize kOpCnt")

    private val kOpCount: Int = TODO("Initialize kOpCount")

    private val kRectRegionRuns: Int = TODO("Initialize kRectRegionRuns")

    private val kRectRunHeadPtr: UndefinedRunHead? = TODO("Initialize kRectRunHeadPtr")

    /**
     * C++ original:
     * ```cpp
     * static RunHead* emptyRunHeadPtr() { return (SkRegion::RunHead*) -1; }
     * ```
     */
    private fun emptyRunHeadPtr(): RunHead_ {
      TODO("Implement emptyRunHeadPtr")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkRegion::BuildRectRuns(const SkIRect& bounds,
     *                              RunType runs[kRectRegionRuns]) {
     *     runs[0] = bounds.fTop;
     *     runs[1] = bounds.fBottom;
     *     runs[2] = 1;    // 1 interval for this scanline
     *     runs[3] = bounds.fLeft;
     *     runs[4] = bounds.fRight;
     *     runs[5] = SkRegion_kRunTypeSentinel;
     *     runs[6] = SkRegion_kRunTypeSentinel;
     * }
     * ```
     */
    private fun buildRectRuns(bounds: SkIRect, runs: Array<SkRegionRunType>) {
      TODO("Implement buildRectRuns")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkRegion::RunsAreARect(const SkRegion::RunType runs[], int count,
     *                             SkIRect* bounds) {
     *     assert_sentinel(runs[0], false);    // top
     *     SkASSERT(count >= kRectRegionRuns);
     *
     *     if (count == kRectRegionRuns) {
     *         assert_sentinel(runs[1], false);    // bottom
     *         SkASSERT(1 == runs[2]);
     *         assert_sentinel(runs[3], false);    // left
     *         assert_sentinel(runs[4], false);    // right
     *         assert_sentinel(runs[5], true);
     *         assert_sentinel(runs[6], true);
     *
     *         SkASSERT(runs[0] < runs[1]);    // valid height
     *         SkASSERT(runs[3] < runs[4]);    // valid width
     *
     *         bounds->setLTRB(runs[3], runs[0], runs[4], runs[1]);
     *         return true;
     *     }
     *     return false;
     * }
     * ```
     */
    private fun runsAreARect(
      runs: Array<SkRegion.SkRegionRunType>,
      count: Int,
      bounds: SkIRect?,
    ): Boolean {
      TODO("Implement runsAreARect")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkRegion::Oper(const SkRegion& rgnaOrig, const SkRegion& rgnbOrig, Op op,
     *                     SkRegion* result) {
     *     SkASSERT((unsigned)op < kOpCount);
     *
     *     if (kReplace_Op == op) {
     *         return setRegionCheck(result, rgnbOrig);
     *     }
     *
     *     // swith to using pointers, so we can swap them as needed
     *     const SkRegion* rgna = &rgnaOrig;
     *     const SkRegion* rgnb = &rgnbOrig;
     *     // after this point, do not refer to rgnaOrig or rgnbOrig!!!
     *
     *     // collaps difference and reverse-difference into just difference
     *     if (kReverseDifference_Op == op) {
     *         using std::swap;
     *         swap(rgna, rgnb);
     *         op = kDifference_Op;
     *     }
     *
     *     SkIRect bounds;
     *     bool    a_empty = rgna->isEmpty();
     *     bool    b_empty = rgnb->isEmpty();
     *     bool    a_rect = rgna->isRect();
     *     bool    b_rect = rgnb->isRect();
     *
     *     switch (op) {
     *     case kDifference_Op:
     *         if (a_empty) {
     *             return setEmptyCheck(result);
     *         }
     *         if (b_empty || !SkIRect::Intersects(rgna->fBounds, rgnb->fBounds)) {
     *             return setRegionCheck(result, *rgna);
     *         }
     *         if (b_rect && rgnb->fBounds.containsNoEmptyCheck(rgna->fBounds)) {
     *             return setEmptyCheck(result);
     *         }
     *         break;
     *
     *     case kIntersect_Op:
     *         if ((a_empty | b_empty)
     *                 || !bounds.intersect(rgna->fBounds, rgnb->fBounds)) {
     *             return setEmptyCheck(result);
     *         }
     *         if (a_rect & b_rect) {
     *             return setRectCheck(result, bounds);
     *         }
     *         if (a_rect && rgna->fBounds.contains(rgnb->fBounds)) {
     *             return setRegionCheck(result, *rgnb);
     *         }
     *         if (b_rect && rgnb->fBounds.contains(rgna->fBounds)) {
     *             return setRegionCheck(result, *rgna);
     *         }
     *         break;
     *
     *     case kUnion_Op:
     *         if (a_empty) {
     *             return setRegionCheck(result, *rgnb);
     *         }
     *         if (b_empty) {
     *             return setRegionCheck(result, *rgna);
     *         }
     *         if (a_rect && rgna->fBounds.contains(rgnb->fBounds)) {
     *             return setRegionCheck(result, *rgna);
     *         }
     *         if (b_rect && rgnb->fBounds.contains(rgna->fBounds)) {
     *             return setRegionCheck(result, *rgnb);
     *         }
     *         break;
     *
     *     case kXOR_Op:
     *         if (a_empty) {
     *             return setRegionCheck(result, *rgnb);
     *         }
     *         if (b_empty) {
     *             return setRegionCheck(result, *rgna);
     *         }
     *         break;
     *     default:
     *         SkDEBUGFAIL("unknown region op");
     *         return false;
     *     }
     *
     *     RunType tmpA[kRectRegionRuns];
     *     RunType tmpB[kRectRegionRuns];
     *
     *     int a_intervals, b_intervals;
     *     const RunType* a_runs = rgna->getRuns(tmpA, &a_intervals);
     *     const RunType* b_runs = rgnb->getRuns(tmpB, &b_intervals);
     *
     *     RunArray array;
     *     int count = operate(a_runs, b_runs, &array, op, nullptr == result);
     *     SkASSERT(count <= array.count());
     *
     *     if (result) {
     *         SkASSERT(count >= 0);
     *         return result->setRuns(&array[0], count);
     *     } else {
     *         return (QUICK_EXIT_TRUE_COUNT == count) || !isRunCountEmpty(count);
     *     }
     * }
     * ```
     */
    private fun oper(
      rgnaOrig: SkRegion,
      rgnbOrig: SkRegion,
      op: Op,
      result: SkRegion?,
    ): Boolean {
      TODO("Implement oper")
    }
  }
}
