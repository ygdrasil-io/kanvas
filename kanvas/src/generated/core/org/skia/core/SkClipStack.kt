package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.skia.foundation.SkSp
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkClipStack {
 * public:
 *     enum BoundsType {
 *         // The bounding box contains all the pixels that can be written to
 *         kNormal_BoundsType,
 *         // The bounding box contains all the pixels that cannot be written to.
 *         // The real bound extends out to infinity and all the pixels outside
 *         // of the bound can be written to. Note that some of the pixels inside
 *         // the bound may also be writeable but all pixels that cannot be
 *         // written to are guaranteed to be inside.
 *         kInsideOut_BoundsType
 *     };
 *
 *     /**
 *      * An element of the clip stack. It represents a shape combined with the prevoius clip using a
 *      * set operator. Each element can be antialiased or not.
 *      */
 *     class Element {
 *     public:
 *         /** This indicates the shape type of the clip element in device space. */
 *         enum class DeviceSpaceType {
 *             //!< This element makes the clip empty (regardless of previous elements).
 *             kEmpty,
 *             //!< This element combines a device space rect with the current clip.
 *             kRect,
 *             //!< This element combines a device space round-rect with the current clip.
 *             kRRect,
 *             //!< This element combines a device space path with the current clip.
 *             kPath,
 *             //!< This element does not have geometry, but applies a shader to the clip
 *             kShader,
 *
 *             kLastType = kShader
 *         };
 *         static const int kTypeCnt = (int)DeviceSpaceType::kLastType + 1;
 *
 *         Element() {
 *             this->initCommon(0, SkClipOp::kIntersect, false);
 *             this->setEmpty();
 *         }
 *
 *         Element(const Element&);
 *
 *         Element(const SkRect& rect, const SkMatrix& m, SkClipOp op, bool doAA) {
 *             this->initRect(0, rect, m, op, doAA);
 *         }
 *
 *         Element(const SkRRect& rrect, const SkMatrix& m, SkClipOp op, bool doAA) {
 *             this->initRRect(0, rrect, m, op, doAA);
 *         }
 *
 *         Element(const SkPath& path, const SkMatrix& m, SkClipOp op, bool doAA) {
 *             this->initPath(0, path, m, op, doAA);
 *         }
 *
 *         explicit Element(sk_sp<SkShader> shader) { this->initShader(0, std::move(shader)); }
 *
 *         Element(const SkRect& rect, bool doAA) {
 *             this->initReplaceRect(0, rect, doAA);
 *         }
 *
 *         ~Element();
 *
 *         bool operator== (const Element& element) const;
 *         bool operator!= (const Element& element) const { return !(*this == element); }
 *
 *         //!< Call to get the type of the clip element.
 *         DeviceSpaceType getDeviceSpaceType() const { return fDeviceSpaceType; }
 *
 *         //!< Call to get the save count associated with this clip element.
 *         int getSaveCount() const { return fSaveCount; }
 *
 *         //!< Call if getDeviceSpaceType() is kPath to get the path.
 *         const SkPath& getDeviceSpacePath() const {
 *             SkASSERT(DeviceSpaceType::kPath == fDeviceSpaceType);
 *             return *fDeviceSpacePath;
 *         }
 *
 *         //!< Call if getDeviceSpaceType() is kRRect to get the round-rect.
 *         const SkRRect& getDeviceSpaceRRect() const {
 *             SkASSERT(DeviceSpaceType::kRRect == fDeviceSpaceType);
 *             return fDeviceSpaceRRect;
 *         }
 *
 *         //!< Call if getDeviceSpaceType() is kRect to get the rect.
 *         const SkRect& getDeviceSpaceRect() const {
 *             SkASSERT(DeviceSpaceType::kRect == fDeviceSpaceType &&
 *                      (fDeviceSpaceRRect.isRect() || fDeviceSpaceRRect.isEmpty()));
 *             return fDeviceSpaceRRect.getBounds();
 *         }
 *
 *         //!<Call if getDeviceSpaceType() is kShader to get a reference to the clip shader.
 *         sk_sp<SkShader> refShader() const {
 *             return fShader;
 *         }
 *         const SkShader* getShader() const {
 *             return fShader.get();
 *         }
 *
 *         //!< Call if getDeviceSpaceType() is not kEmpty to get the set operation used to combine
 *         //!< this element.
 *         SkClipOp getOp() const { return fOp; }
 *         // Augments getOps()'s behavior by requiring a clip reset before the op is applied.
 *         bool isReplaceOp() const { return fIsReplace; }
 *
 *         //!< Call to get the element as a path, regardless of its type.
 *         SkPath asDeviceSpacePath() const;
 *
 *         //!< Call if getType() is not kPath to get the element as a round rect.
 *         const SkRRect& asDeviceSpaceRRect() const {
 *             SkASSERT(DeviceSpaceType::kPath != fDeviceSpaceType);
 *             return fDeviceSpaceRRect;
 *         }
 *
 *         /** If getType() is not kEmpty this indicates whether the clip shape should be anti-aliased
 *             when it is rasterized. */
 *         bool isAA() const { return fDoAA; }
 *
 *         /** The GenID can be used by clip stack clients to cache representations of the clip. The
 *             ID corresponds to the set of clip elements up to and including this element within the
 *             stack not to the element itself. That is the same clip path in different stacks will
 *             have a different ID since the elements produce different clip result in the context of
 *             their stacks. */
 *         uint32_t getGenID() const { SkASSERT(kInvalidGenID != fGenID); return fGenID; }
 *
 *         /**
 *          * Gets the bounds of the clip element, either the rect or path bounds. (Whether the shape
 *          * is inverse filled is not considered.)
 *          */
 *         const SkRect& getBounds() const;
 *
 *         /**
 *          * Conservatively checks whether the clip shape contains the rect/rrect. (Whether the shape
 *          * is inverse filled is not considered.)
 *          */
 *         bool contains(const SkRect& rect) const;
 *         bool contains(const SkRRect& rrect) const;
 *
 *         /**
 *          * Is the clip shape inverse filled.
 *          */
 *         bool isInverseFilled() const {
 *             return DeviceSpaceType::kPath == fDeviceSpaceType &&
 *                    fDeviceSpacePath->isInverseFillType();
 *         }
 *
 * #ifdef SK_DEBUG
 *         /**
 *          * Dumps the element to SkDebugf. This is intended for Skia development debugging
 *          * Don't rely on the existence of this function or the formatting of its output.
 *          */
 *         void dump() const;
 * #endif
 *
 *     private:
 *         friend class SkClipStack;
 *
 *         std::optional<SkPath> fDeviceSpacePath;
 *         SkRRect fDeviceSpaceRRect;
 *         sk_sp<SkShader> fShader;
 *         int fSaveCount;  // save count of stack when this element was added.
 *         SkClipOp fOp;
 *         DeviceSpaceType fDeviceSpaceType;
 *         bool fDoAA;
 *         bool fIsReplace;
 *
 *         /* fFiniteBoundType and fFiniteBound are used to incrementally update the clip stack's
 *            bound. When fFiniteBoundType is kNormal_BoundsType, fFiniteBound represents the
 *            conservative bounding box of the pixels that aren't clipped (i.e., any pixels that can be
 *            drawn to are inside the bound). When fFiniteBoundType is kInsideOut_BoundsType (which
 *            occurs when a clip is inverse filled), fFiniteBound represents the conservative bounding
 *            box of the pixels that _are_ clipped (i.e., any pixels that cannot be drawn to are inside
 *            the bound). When fFiniteBoundType is kInsideOut_BoundsType the actual bound is the
 *            infinite plane. This behavior of fFiniteBoundType and fFiniteBound is required so that we
 *            can capture the cancelling out of the extensions to infinity when two inverse filled
 *            clips are Booleaned together. */
 *         SkClipStack::BoundsType fFiniteBoundType;
 *         SkRect fFiniteBound;
 *
 *         // When element is applied to the previous elements in the stack is the result known to be
 *         // equivalent to a single rect intersection? IIOW, is the clip effectively a rectangle.
 *         bool fIsIntersectionOfRects;
 *
 *         uint32_t fGenID;
 *         explicit Element(int saveCount) {
 *             this->initCommon(saveCount, SkClipOp::kIntersect, false);
 *             this->setEmpty();
 *         }
 *
 *         Element(int saveCount, const SkRRect& rrect, const SkMatrix& m, SkClipOp op, bool doAA) {
 *             this->initRRect(saveCount, rrect, m, op, doAA);
 *         }
 *
 *         Element(int saveCount, const SkRect& rect, const SkMatrix& m, SkClipOp op, bool doAA) {
 *             this->initRect(saveCount, rect, m, op, doAA);
 *         }
 *
 *         Element(int saveCount, const SkPath& path, const SkMatrix& m, SkClipOp op, bool doAA) {
 *             this->initPath(saveCount, path, m, op, doAA);
 *         }
 *
 *         Element(int saveCount, sk_sp<SkShader> shader) {
 *             this->initShader(saveCount, std::move(shader));
 *         }
 *
 *         Element(int saveCount, const SkRect& rect, bool doAA) {
 *             this->initReplaceRect(saveCount, rect, doAA);
 *         }
 *
 *         void initCommon(int saveCount, SkClipOp op, bool doAA);
 *         void initRect(int saveCount, const SkRect&, const SkMatrix&, SkClipOp, bool doAA);
 *         void initRRect(int saveCount, const SkRRect&, const SkMatrix&, SkClipOp, bool doAA);
 *         void initPath(int saveCount, const SkPath&, const SkMatrix&, SkClipOp, bool doAA);
 *         void initAsPath(int saveCount, const SkPath&, const SkMatrix&, SkClipOp, bool doAA);
 *         void initShader(int saveCount, sk_sp<SkShader>);
 *         void initReplaceRect(int saveCount, const SkRect&, bool doAA);
 *
 *         void setEmpty();
 *
 *         // All Element methods below are only used within SkClipStack.cpp
 *         inline void checkEmpty() const;
 *         inline bool canBeIntersectedInPlace(int saveCount, SkClipOp op) const;
 *         /* This method checks to see if two rect clips can be safely merged into one. The issue here
 *           is that to be strictly correct all the edges of the resulting rect must have the same
 *           anti-aliasing. */
 *         bool rectRectIntersectAllowed(const SkRect& newR, bool newAA) const;
 *         /** Determines possible finite bounds for the Element given the previous element of the
 *             stack */
 *         void updateBoundAndGenID(const Element* prior);
 *         // The different combination of fill & inverse fill when combining bounding boxes
 *         enum FillCombo {
 *             kPrev_Cur_FillCombo,
 *             kPrev_InvCur_FillCombo,
 *             kInvPrev_Cur_FillCombo,
 *             kInvPrev_InvCur_FillCombo
 *         };
 *         // per-set operation functions used by updateBoundAndGenID().
 *         inline void combineBoundsDiff(FillCombo combination, const SkRect& prevFinite);
 *         inline void combineBoundsIntersection(int combination, const SkRect& prevFinite);
 *     };
 *
 *     SkClipStack();
 *     SkClipStack(void* storage, size_t size);
 *     SkClipStack(const SkClipStack& b);
 *     ~SkClipStack();
 *
 *     SkClipStack& operator=(const SkClipStack& b);
 *     bool operator==(const SkClipStack& b) const;
 *     bool operator!=(const SkClipStack& b) const { return !(*this == b); }
 *
 *     void reset();
 *
 *     int getSaveCount() const { return fSaveCount; }
 *     void save();
 *     void restore();
 *
 *     class AutoRestore {
 *     public:
 *         AutoRestore(SkClipStack* cs, bool doSave)
 *             : fCS(cs), fSaveCount(cs->getSaveCount())
 *         {
 *             if (doSave) {
 *                 fCS->save();
 *             }
 *         }
 *         ~AutoRestore() {
 *             SkASSERT(fCS->getSaveCount() >= fSaveCount);  // no underflow
 *             while (fCS->getSaveCount() > fSaveCount) {
 *                 fCS->restore();
 *             }
 *         }
 *
 *     private:
 *         SkClipStack* fCS;
 *         const int    fSaveCount;
 *     };
 *
 *     /**
 *      * getBounds places the current finite bound in its first parameter. In its
 *      * second, it indicates which kind of bound is being returned. If
 *      * 'canvFiniteBound' is a normal bounding box then it encloses all writeable
 *      * pixels. If 'canvFiniteBound' is an inside out bounding box then it
 *      * encloses all the un-writeable pixels and the true/normal bound is the
 *      * infinite plane. isIntersectionOfRects is an optional parameter
 *      * that is true if 'canvFiniteBound' resulted from an intersection of rects.
 *      */
 *     void getBounds(SkRect* canvFiniteBound,
 *                    BoundsType* boundType,
 *                    bool* isIntersectionOfRects = nullptr) const;
 *
 *     SkRect bounds(const SkIRect& deviceBounds) const;
 *     bool isEmpty(const SkIRect& deviceBounds) const;
 *
 *     /**
 *      * Returns true if the input (r)rect in device space is entirely contained
 *      * by the clip. A return value of false does not guarantee that the (r)rect
 *      * is not contained by the clip.
 *      */
 *     bool quickContains(const SkRect& devRect) const {
 *         return this->isWideOpen() || this->internalQuickContains(devRect);
 *     }
 *
 *     bool quickContains(const SkRRect& devRRect) const {
 *         return this->isWideOpen() || this->internalQuickContains(devRRect);
 *     }
 *
 *     void clipDevRect(const SkIRect& ir, SkClipOp op) {
 *         SkRect r;
 *         r.set(ir);
 *         this->clipRect(r, SkMatrix::I(), op, false);
 *     }
 *     void clipRect(const SkRect&, const SkMatrix& matrix, SkClipOp, bool doAA);
 *     void clipRRect(const SkRRect&, const SkMatrix& matrix, SkClipOp, bool doAA);
 *     void clipPath(const SkPath&, const SkMatrix& matrix, SkClipOp, bool doAA);
 *     void clipShader(sk_sp<SkShader>);
 *     // An optimized version of clipDevRect(emptyRect, kIntersect, ...)
 *     void clipEmpty();
 *
 *     void replaceClip(const SkRect& devRect, bool doAA);
 *
 *     /**
 *      * isWideOpen returns true if the clip state corresponds to the infinite
 *      * plane (i.e., draws are not limited at all)
 *      */
 *     bool isWideOpen() const { return this->getTopmostGenID() == kWideOpenGenID; }
 *
 *     /**
 *      * This method quickly and conservatively determines whether the entire stack is equivalent to
 *      * intersection with a rrect given a bounds, where the rrect must not contain the entire bounds.
 *      *
 *      * @param bounds   A bounds on what will be drawn through the clip. The clip only need be
 *      *                 equivalent to a intersection with a rrect for draws within the bounds. The
 *      *                 returned rrect must intersect the bounds but need not be contained by the
 *      *                 bounds.
 *      * @param rrect    If return is true rrect will contain the rrect equivalent to the stack.
 *      * @param aa       If return is true aa will indicate whether the equivalent rrect clip is
 *      *                 antialiased.
 *      * @return true if the stack is equivalent to a single rrect intersect clip, false otherwise.
 *      */
 *     bool isRRect(const SkRect& bounds, SkRRect* rrect, bool* aa) const;
 *
 *     /**
 *      * The generation ID has three reserved values to indicate special
 *      * (potentially ignorable) cases
 *      */
 *     static const uint32_t kInvalidGenID  = 0;    //!< Invalid id that is never returned by
 *                                                  //!< SkClipStack. Useful when caching clips
 *                                                  //!< based on GenID.
 *     static const uint32_t kEmptyGenID    = 1;    // no pixels writeable
 *     static const uint32_t kWideOpenGenID = 2;    // all pixels writeable
 *
 *     uint32_t getTopmostGenID() const;
 *
 * #ifdef SK_DEBUG
 *     /**
 *      * Dumps the contents of the clip stack to SkDebugf. This is intended for Skia development
 *      * debugging. Don't rely on the existence of this function or the formatting of its output.
 *      */
 *     void dump() const;
 * #endif
 *
 * public:
 *     class Iter {
 *     public:
 *         enum IterStart {
 *             kBottom_IterStart = SkDeque::Iter::kFront_IterStart,
 *             kTop_IterStart = SkDeque::Iter::kBack_IterStart
 *         };
 *
 *         /**
 *          * Creates an uninitialized iterator. Must be reset()
 *          */
 *         Iter();
 *
 *         Iter(const SkClipStack& stack, IterStart startLoc);
 *
 *         /**
 *          *  Return the clip element for this iterator. If next()/prev() returns NULL, then the
 *          *  iterator is done.
 *          */
 *         const Element* next();
 *         const Element* prev();
 *
 *         /**
 *          * Moves the iterator to the topmost element with the specified RegionOp and returns that
 *          * element. If no clip element with that op is found, the first element is returned.
 *          */
 *         const Element* skipToTopmost(SkClipOp op);
 *
 *         /**
 *          * Restarts the iterator on a clip stack.
 *          */
 *         void reset(const SkClipStack& stack, IterStart startLoc);
 *
 *     private:
 *         const SkClipStack* fStack;
 *         SkDeque::Iter      fIter;
 *     };
 *
 *     /**
 *      * The B2TIter iterates from the bottom of the stack to the top.
 *      * It inherits privately from Iter to prevent access to reverse iteration.
 *      */
 *     class B2TIter : private Iter {
 *     public:
 *         B2TIter() {}
 *
 *         /**
 *          * Wrap Iter's 2 parameter ctor to force initialization to the
 *          * beginning of the deque/bottom of the stack
 *          */
 *         B2TIter(const SkClipStack& stack)
 *         : INHERITED(stack, kBottom_IterStart) {
 *         }
 *
 *         using Iter::next;
 *
 *         /**
 *          * Wrap Iter::reset to force initialization to the
 *          * beginning of the deque/bottom of the stack
 *          */
 *         void reset(const SkClipStack& stack) {
 *             this->INHERITED::reset(stack, kBottom_IterStart);
 *         }
 *
 *     private:
 *
 *         using INHERITED = Iter;
 *     };
 *
 *     /**
 *      * GetConservativeBounds returns a conservative bound of the current clip.
 *      * Since this could be the infinite plane (if inverse fills were involved) the
 *      * maxWidth and maxHeight parameters can be used to limit the returned bound
 *      * to the expected drawing area. Similarly, the offsetX and offsetY parameters
 *      * allow the caller to offset the returned bound to account for translated
 *      * drawing areas (i.e., those resulting from a saveLayer). For finite bounds,
 *      * the translation (+offsetX, +offsetY) is applied before the clamp to the
 *      * maximum rectangle: [0,maxWidth) x [0,maxHeight).
 *      * isIntersectionOfRects is an optional parameter that is true when
 *      * 'devBounds' is the result of an intersection of rects. In this case
 *      * 'devBounds' is the exact answer/clip.
 *      */
 *     void getConservativeBounds(int offsetX,
 *                                int offsetY,
 *                                int maxWidth,
 *                                int maxHeight,
 *                                SkRect* devBounds,
 *                                bool* isIntersectionOfRects = nullptr) const;
 *
 * private:
 *     friend class Iter;
 *
 *     SkDeque fDeque;
 *     int     fSaveCount;
 *
 *     bool internalQuickContains(const SkRect& devRect) const;
 *     bool internalQuickContains(const SkRRect& devRRect) const;
 *
 *     /**
 *      * Helper for clipDevPath, etc.
 *      */
 *     void pushElement(const Element& element);
 *
 *     /**
 *      * Restore the stack back to the specified save count.
 *      */
 *     void restoreTo(int saveCount);
 *
 *     /**
 *      * Return the next unique generation ID.
 *      */
 *     static uint32_t GetNextGenID();
 * }
 * ```
 */
public data class SkClipStack public constructor(
  /**
   * C++ original:
   * ```cpp
   * static const uint32_t kInvalidGenID  = 0
   * ```
   */
  private var fDeque: SkDeque,
  /**
   * C++ original:
   * ```cpp
   * static const uint32_t kEmptyGenID    = 1
   * ```
   */
  private var fSaveCount: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkClipStack& SkClipStack::operator=(const SkClipStack& b) {
   *     if (this == &b) {
   *         return *this;
   *     }
   *     reset();
   *
   *     fSaveCount = b.fSaveCount;
   *     SkDeque::F2BIter recIter(b.fDeque);
   *     for (const Element* element = (const Element*)recIter.next();
   *          element != nullptr;
   *          element = (const Element*)recIter.next()) {
   *         new (fDeque.push_back()) Element(*element);
   *     }
   *
   *     return *this;
   * }
   * ```
   */
  private fun assign(b: SkClipStack) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkClipStack::operator==(const SkClipStack& b) const {
   *     if (this->getTopmostGenID() == b.getTopmostGenID()) {
   *         return true;
   *     }
   *     if (fSaveCount != b.fSaveCount ||
   *         fDeque.count() != b.fDeque.count()) {
   *         return false;
   *     }
   *     SkDeque::F2BIter myIter(fDeque);
   *     SkDeque::F2BIter bIter(b.fDeque);
   *     const Element* myElement = (const Element*)myIter.next();
   *     const Element* bElement = (const Element*)bIter.next();
   *
   *     while (myElement != nullptr && bElement != nullptr) {
   *         if (*myElement != *bElement) {
   *             return false;
   *         }
   *         myElement = (const Element*)myIter.next();
   *         bElement = (const Element*)bIter.next();
   *     }
   *     return myElement == nullptr && bElement == nullptr;
   * }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkClipStack& b) const { return !(*this == b); }
   * ```
   */
  private fun reset() {
    TODO("Implement reset")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkClipStack::reset() {
   *     // We used a placement new for each object in fDeque, so we're responsible
   *     // for calling the destructor on each of them as well.
   *     while (!fDeque.empty()) {
   *         Element* element = (Element*)fDeque.back();
   *         element->~Element();
   *         fDeque.pop_back();
   *     }
   *
   *     fSaveCount = 0;
   * }
   * ```
   */
  public fun getSaveCount(): Int {
    TODO("Implement getSaveCount")
  }

  /**
   * C++ original:
   * ```cpp
   * int getSaveCount() const { return fSaveCount; }
   * ```
   */
  private fun save() {
    TODO("Implement save")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkClipStack::save() {
   *     fSaveCount += 1;
   * }
   * ```
   */
  private fun restore() {
    TODO("Implement restore")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkClipStack::restore() {
   *     fSaveCount -= 1;
   *     restoreTo(fSaveCount);
   * }
   * ```
   */
  private fun getBounds(
    canvFiniteBound: SkRect?,
    boundType: BoundsType?,
    isIntersectionOfRects: Boolean? = null,
  ) {
    TODO("Implement getBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkClipStack::getBounds(SkRect* canvFiniteBound,
   *                             BoundsType* boundType,
   *                             bool* isIntersectionOfRects) const {
   *     SkASSERT(canvFiniteBound && boundType);
   *
   *     const Element* element = (const Element*)fDeque.back();
   *
   *     if (nullptr == element) {
   *         // the clip is wide open - the infinite plane w/ no pixels un-writeable
   *         canvFiniteBound->setEmpty();
   *         *boundType = kInsideOut_BoundsType;
   *         if (isIntersectionOfRects) {
   *             *isIntersectionOfRects = false;
   *         }
   *         return;
   *     }
   *
   *     *canvFiniteBound = element->fFiniteBound;
   *     *boundType = element->fFiniteBoundType;
   *     if (isIntersectionOfRects) {
   *         *isIntersectionOfRects = element->fIsIntersectionOfRects;
   *     }
   * }
   * ```
   */
  private fun bounds(deviceBounds: SkIRect): SkRect {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkClipStack::bounds(const SkIRect& deviceBounds) const {
   *     // TODO: optimize this.
   *     SkRect r;
   *     SkClipStack::BoundsType bounds;
   *     this->getBounds(&r, &bounds);
   *     if (bounds == SkClipStack::kInsideOut_BoundsType) {
   *         return SkRect::Make(deviceBounds);
   *     }
   *     return r.intersect(SkRect::Make(deviceBounds)) ? r : SkRect::MakeEmpty();
   * }
   * ```
   */
  private fun isEmpty(deviceBounds: SkIRect): Boolean {
    TODO("Implement isEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkClipStack::isEmpty(const SkIRect& r) const { return this->bounds(r).isEmpty(); }
   * ```
   */
  private fun quickContains(devRect: SkRect): Boolean {
    TODO("Implement quickContains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool quickContains(const SkRect& devRect) const {
   *         return this->isWideOpen() || this->internalQuickContains(devRect);
   *     }
   * ```
   */
  private fun quickContains(devRRect: SkRRect): Boolean {
    TODO("Implement quickContains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool quickContains(const SkRRect& devRRect) const {
   *         return this->isWideOpen() || this->internalQuickContains(devRRect);
   *     }
   * ```
   */
  private fun clipDevRect(ir: SkIRect, op: SkClipOp) {
    TODO("Implement clipDevRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void clipDevRect(const SkIRect& ir, SkClipOp op) {
   *         SkRect r;
   *         r.set(ir);
   *         this->clipRect(r, SkMatrix::I(), op, false);
   *     }
   * ```
   */
  private fun clipRect(
    rect: SkRect,
    matrix: SkMatrix,
    op: SkClipOp,
    doAA: Boolean,
  ) {
    TODO("Implement clipRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkClipStack::clipRect(const SkRect& rect, const SkMatrix& matrix, SkClipOp op, bool doAA) {
   *     Element element(fSaveCount, rect, matrix, op, doAA);
   *     this->pushElement(element);
   * }
   * ```
   */
  private fun clipRRect(
    rrect: SkRRect,
    matrix: SkMatrix,
    op: SkClipOp,
    doAA: Boolean,
  ) {
    TODO("Implement clipRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkClipStack::clipRRect(const SkRRect& rrect, const SkMatrix& matrix, SkClipOp op, bool doAA) {
   *     Element element(fSaveCount, rrect, matrix, op, doAA);
   *     this->pushElement(element);
   * }
   * ```
   */
  private fun clipPath(
    path: SkPath,
    matrix: SkMatrix,
    op: SkClipOp,
    doAA: Boolean,
  ) {
    TODO("Implement clipPath")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkClipStack::clipPath(const SkPath& path, const SkMatrix& matrix, SkClipOp op,
   *                            bool doAA) {
   *     Element element(fSaveCount, path, matrix, op, doAA);
   *     this->pushElement(element);
   * }
   * ```
   */
  private fun clipShader(shader: SkSp<SkShader>) {
    TODO("Implement clipShader")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkClipStack::clipShader(sk_sp<SkShader> shader) {
   *     Element element(fSaveCount, std::move(shader));
   *     this->pushElement(element);
   * }
   * ```
   */
  private fun clipEmpty() {
    TODO("Implement clipEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkClipStack::clipEmpty() {
   *     Element* element = (Element*) fDeque.back();
   *
   *     if (element && element->canBeIntersectedInPlace(fSaveCount, SkClipOp::kIntersect)) {
   *         element->setEmpty();
   *     }
   *     new (fDeque.push_back()) Element(fSaveCount);
   *
   *     ((Element*)fDeque.back())->fGenID = kEmptyGenID;
   * }
   * ```
   */
  private fun replaceClip(devRect: SkRect, doAA: Boolean) {
    TODO("Implement replaceClip")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkClipStack::replaceClip(const SkRect& rect, bool doAA) {
   *     Element element(fSaveCount, rect, doAA);
   *     this->pushElement(element);
   * }
   * ```
   */
  private fun isWideOpen(): Boolean {
    TODO("Implement isWideOpen")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isWideOpen() const { return this->getTopmostGenID() == kWideOpenGenID; }
   * ```
   */
  private fun isRRect(
    bounds: SkRect,
    rrect: SkRRect?,
    aa: Boolean?,
  ): Boolean {
    TODO("Implement isRRect")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkClipStack::isRRect(const SkRect& bounds, SkRRect* rrect, bool* aa) const {
   *     const Element* back = static_cast<const Element*>(fDeque.back());
   *     if (!back) {
   *         // TODO: return bounds?
   *         return false;
   *     }
   *     // First check if the entire stack is known to be a rect by the top element.
   *     if (back->fIsIntersectionOfRects && back->fFiniteBoundType == BoundsType::kNormal_BoundsType) {
   *         rrect->setRect(back->fFiniteBound);
   *         *aa = back->isAA();
   *         return true;
   *     }
   *
   *     if (back->getDeviceSpaceType() != SkClipStack::Element::DeviceSpaceType::kRect &&
   *         back->getDeviceSpaceType() != SkClipStack::Element::DeviceSpaceType::kRRect) {
   *         return false;
   *     }
   *     if (back->isReplaceOp()) {
   *         *rrect = back->asDeviceSpaceRRect();
   *         *aa = back->isAA();
   *         return true;
   *     }
   *
   *     if (back->getOp() == SkClipOp::kIntersect) {
   *         SkRect backBounds;
   *         if (!backBounds.intersect(bounds, back->asDeviceSpaceRRect().rect())) {
   *             return false;
   *         }
   *         // We limit to 17 elements. This means the back element will be bounds checked at most 16
   *         // times if it is an rrect.
   *         int cnt = fDeque.count();
   *         if (cnt > 17) {
   *             return false;
   *         }
   *         if (cnt > 1) {
   *             SkDeque::Iter iter(fDeque, SkDeque::Iter::kBack_IterStart);
   *             SkAssertResult(static_cast<const Element*>(iter.prev()) == back);
   *             while (const Element* prior = (const Element*)iter.prev()) {
   *                 // TODO: Once expanding clip ops are removed, this is equiv. to op == kDifference
   *                 if ((prior->getOp() != SkClipOp::kIntersect && !prior->isReplaceOp()) ||
   *                     !prior->contains(backBounds)) {
   *                     return false;
   *                 }
   *                 if (prior->isReplaceOp()) {
   *                     break;
   *                 }
   *             }
   *         }
   *         *rrect = back->asDeviceSpaceRRect();
   *         *aa = back->isAA();
   *         return true;
   *     }
   *     return false;
   * }
   * ```
   */
  private fun getTopmostGenID(): UInt {
    TODO("Implement getTopmostGenID")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t SkClipStack::getTopmostGenID() const {
   *     if (fDeque.empty()) {
   *         return kWideOpenGenID;
   *     }
   *
   *     const Element* back = static_cast<const Element*>(fDeque.back());
   *     if (kInsideOut_BoundsType == back->fFiniteBoundType && back->fFiniteBound.isEmpty() &&
   *         Element::DeviceSpaceType::kShader != back->fDeviceSpaceType) {
   *         return kWideOpenGenID;
   *     }
   *
   *     return back->getGenID();
   * }
   * ```
   */
  public fun dump() {
    TODO("Implement dump")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkClipStack::dump() const {
   *     B2TIter iter(*this);
   *     const Element* e;
   *     while ((e = iter.next())) {
   *         e->dump();
   *         SkDebugf("\n");
   *     }
   * }
   * ```
   */
  private fun getConservativeBounds(
    offsetX: Int,
    offsetY: Int,
    maxWidth: Int,
    maxHeight: Int,
    devBounds: SkRect?,
    isIntersectionOfRects: Boolean? = null,
  ) {
    TODO("Implement getConservativeBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkClipStack::getConservativeBounds(int offsetX,
   *                                         int offsetY,
   *                                         int maxWidth,
   *                                         int maxHeight,
   *                                         SkRect* devBounds,
   *                                         bool* isIntersectionOfRects) const {
   *     SkASSERT(devBounds);
   *
   *     devBounds->setLTRB(0, 0,
   *                        SkIntToScalar(maxWidth), SkIntToScalar(maxHeight));
   *
   *     SkRect temp;
   *     SkClipStack::BoundsType boundType;
   *
   *     // temp starts off in canvas space here
   *     this->getBounds(&temp, &boundType, isIntersectionOfRects);
   *     if (SkClipStack::kInsideOut_BoundsType == boundType) {
   *         return;
   *     }
   *
   *     // but is converted to device space here
   *     temp.offset(SkIntToScalar(offsetX), SkIntToScalar(offsetY));
   *
   *     if (!devBounds->intersect(temp)) {
   *         devBounds->setEmpty();
   *     }
   * }
   * ```
   */
  private fun internalQuickContains(devRect: SkRect): Boolean {
    TODO("Implement internalQuickContains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkClipStack::internalQuickContains(const SkRect& rect) const {
   *     Iter iter(*this, Iter::kTop_IterStart);
   *     const Element* element = iter.prev();
   *     while (element != nullptr) {
   *         // TODO: Once expanding ops are removed, this condition is equiv. to op == kDifference.
   *         if (SkClipOp::kIntersect != element->getOp() && !element->isReplaceOp()) {
   *             return false;
   *         }
   *         if (element->isInverseFilled()) {
   *             // Part of 'rect' could be trimmed off by the inverse-filled clip element
   *             if (SkRect::Intersects(element->getBounds(), rect)) {
   *                 return false;
   *             }
   *         } else {
   *             if (!element->contains(rect)) {
   *                 return false;
   *             }
   *         }
   *         if (element->isReplaceOp()) {
   *             break;
   *         }
   *         element = iter.prev();
   *     }
   *     return true;
   * }
   * ```
   */
  private fun internalQuickContains(devRRect: SkRRect): Boolean {
    TODO("Implement internalQuickContains")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkClipStack::internalQuickContains(const SkRRect& rrect) const {
   *     Iter iter(*this, Iter::kTop_IterStart);
   *     const Element* element = iter.prev();
   *     while (element != nullptr) {
   *         // TODO: Once expanding ops are removed, this condition is equiv. to op == kDifference.
   *         if (SkClipOp::kIntersect != element->getOp() && !element->isReplaceOp()) {
   *             return false;
   *         }
   *         if (element->isInverseFilled()) {
   *             // Part of 'rrect' could be trimmed off by the inverse-filled clip element
   *             if (SkRect::Intersects(element->getBounds(), rrect.getBounds())) {
   *                 return false;
   *             }
   *         } else {
   *             if (!element->contains(rrect)) {
   *                 return false;
   *             }
   *         }
   *         if (element->isReplaceOp()) {
   *             break;
   *         }
   *         element = iter.prev();
   *     }
   *     return true;
   * }
   * ```
   */
  private fun pushElement(element: Element) {
    TODO("Implement pushElement")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkClipStack::pushElement(const Element& element) {
   *     // Use reverse iterator instead of back because Rect path may need previous
   *     SkDeque::Iter iter(fDeque, SkDeque::Iter::kBack_IterStart);
   *     Element* prior = (Element*) iter.prev();
   *
   *     if (prior) {
   *         if (element.isReplaceOp()) {
   *             this->restoreTo(fSaveCount - 1);
   *             prior = (Element*) fDeque.back();
   *         } else if (prior->canBeIntersectedInPlace(fSaveCount, element.getOp())) {
   *             switch (prior->fDeviceSpaceType) {
   *                 case Element::DeviceSpaceType::kEmpty:
   *                     SkDEBUGCODE(prior->checkEmpty();)
   *                     return;
   *                 case Element::DeviceSpaceType::kShader:
   *                     if (Element::DeviceSpaceType::kShader == element.getDeviceSpaceType()) {
   *                         prior->fShader = SkShaders::Blend(SkBlendMode::kSrcIn,
   *                                                           element.fShader, prior->fShader);
   *                         Element* priorPrior = (Element*) iter.prev();
   *                         prior->updateBoundAndGenID(priorPrior);
   *                         return;
   *                     }
   *                     break;
   *                 case Element::DeviceSpaceType::kRect:
   *                     if (Element::DeviceSpaceType::kRect == element.getDeviceSpaceType()) {
   *                         if (prior->rectRectIntersectAllowed(element.getDeviceSpaceRect(),
   *                                                             element.isAA())) {
   *                             SkRect isectRect;
   *                             if (!isectRect.intersect(prior->getDeviceSpaceRect(),
   *                                                      element.getDeviceSpaceRect())) {
   *                                 prior->setEmpty();
   *                                 return;
   *                             }
   *
   *                             prior->fDeviceSpaceRRect.setRect(isectRect);
   *                             prior->fDoAA = element.isAA();
   *                             Element* priorPrior = (Element*) iter.prev();
   *                             prior->updateBoundAndGenID(priorPrior);
   *                             return;
   *                         }
   *                         break;
   *                     }
   *                     [[fallthrough]];
   *                 default:
   *                     if (!SkRect::Intersects(prior->getBounds(), element.getBounds())) {
   *                         prior->setEmpty();
   *                         return;
   *                     }
   *                     break;
   *             }
   *         }
   *     }
   *     Element* newElement = new (fDeque.push_back()) Element(element);
   *     newElement->updateBoundAndGenID(prior);
   * }
   * ```
   */
  private fun restoreTo(saveCount: Int) {
    TODO("Implement restoreTo")
  }

  public open class Element public constructor() {
    private var fDeviceSpacePath: Int = TODO("Initialize fDeviceSpacePath")

    private var fDeviceSpaceRRect: SkRRect = TODO("Initialize fDeviceSpaceRRect")

    private var fShader: SkSp<SkShader> = TODO("Initialize fShader")

    private var fSaveCount: Int = TODO("Initialize fSaveCount")

    private var fOp: SkClipOp = TODO("Initialize fOp")

    private var fDeviceSpaceType: Element.DeviceSpaceType = TODO("Initialize fDeviceSpaceType")

    private var fDoAA: Boolean = TODO("Initialize fDoAA")

    private var fIsReplace: Boolean = TODO("Initialize fIsReplace")

    private var fFiniteBoundType: BoundsType = TODO("Initialize fFiniteBoundType")

    private var fFiniteBound: SkRect = TODO("Initialize fFiniteBound")

    private var fIsIntersectionOfRects: Boolean = TODO("Initialize fIsIntersectionOfRects")

    private var fGenID: UInt = TODO("Initialize fGenID")

    public constructor(that: undefined.Element) : this() {
      TODO("Implement constructor")
    }

    public constructor(
      rect: SkRect,
      m: SkMatrix,
      op: SkClipOp,
      doAA: Boolean,
    ) : this() {
      TODO("Implement constructor")
    }

    public constructor(
      rrect: SkRRect,
      m: SkMatrix,
      op: SkClipOp,
      doAA: Boolean,
    ) : this() {
      TODO("Implement constructor")
    }

    public constructor(
      path: SkPath,
      m: SkMatrix,
      op: SkClipOp,
      doAA: Boolean,
    ) : this() {
      TODO("Implement constructor")
    }

    public constructor(shader: SkSp<SkShader>) : this() {
      TODO("Implement constructor")
    }

    public constructor(rect: SkRect, doAA: Boolean) : this() {
      TODO("Implement constructor")
    }

    public constructor(saveCount: Int) : this() {
      TODO("Implement constructor")
    }

    public constructor(
      saveCount: Int,
      rrect: SkRRect,
      m: SkMatrix,
      op: SkClipOp,
      doAA: Boolean,
    ) : this() {
      TODO("Implement constructor")
    }

    public constructor(
      saveCount: Int,
      rect: SkRect,
      m: SkMatrix,
      op: SkClipOp,
      doAA: Boolean,
    ) : this() {
      TODO("Implement constructor")
    }

    public constructor(
      saveCount: Int,
      path: SkPath,
      m: SkMatrix,
      op: SkClipOp,
      doAA: Boolean,
    ) : this() {
      TODO("Implement constructor")
    }

    public constructor(saveCount: Int, shader: SkSp<SkShader>) : this() {
      TODO("Implement constructor")
    }

    public constructor(
      saveCount: Int,
      rect: SkRect,
      doAA: Boolean,
    ) : this() {
      TODO("Implement constructor")
    }

    public override operator fun equals(other: Any?): Boolean {
      TODO("Implement equals")
    }

    public fun getDeviceSpaceType(): Element.DeviceSpaceType {
      TODO("Implement getDeviceSpaceType")
    }

    public fun getSaveCount(): Int {
      TODO("Implement getSaveCount")
    }

    public fun getDeviceSpacePath(): SkPath {
      TODO("Implement getDeviceSpacePath")
    }

    public fun getDeviceSpaceRRect(): SkRRect {
      TODO("Implement getDeviceSpaceRRect")
    }

    public fun getDeviceSpaceRect(): SkRect {
      TODO("Implement getDeviceSpaceRect")
    }

    public fun refShader(): SkSp<SkShader> {
      TODO("Implement refShader")
    }

    public fun getShader(): SkShader {
      TODO("Implement getShader")
    }

    public fun getOp(): SkClipOp {
      TODO("Implement getOp")
    }

    public fun isReplaceOp(): Boolean {
      TODO("Implement isReplaceOp")
    }

    public fun asDeviceSpacePath(): SkPath {
      TODO("Implement asDeviceSpacePath")
    }

    public fun asDeviceSpaceRRect(): SkRRect {
      TODO("Implement asDeviceSpaceRRect")
    }

    public fun isAA(): Boolean {
      TODO("Implement isAA")
    }

    public fun getGenID(): UInt {
      TODO("Implement getGenID")
    }

    public fun getBounds(): SkRect {
      TODO("Implement getBounds")
    }

    public fun contains(rect: SkRect): Boolean {
      TODO("Implement contains")
    }

    public fun contains(rrect: SkRRect): Boolean {
      TODO("Implement contains")
    }

    public fun isInverseFilled(): Boolean {
      TODO("Implement isInverseFilled")
    }

    public fun dump() {
      TODO("Implement dump")
    }

    private fun initCommon(
      saveCount: Int,
      op: SkClipOp,
      doAA: Boolean,
    ) {
      TODO("Implement initCommon")
    }

    private fun initRect(
      saveCount: Int,
      rect: SkRect,
      m: SkMatrix,
      op: SkClipOp,
      doAA: Boolean,
    ) {
      TODO("Implement initRect")
    }

    private fun initRRect(
      saveCount: Int,
      rrect: SkRRect,
      m: SkMatrix,
      op: SkClipOp,
      doAA: Boolean,
    ) {
      TODO("Implement initRRect")
    }

    private fun initPath(
      saveCount: Int,
      path: SkPath,
      m: SkMatrix,
      op: SkClipOp,
      doAA: Boolean,
    ) {
      TODO("Implement initPath")
    }

    private fun initAsPath(
      saveCount: Int,
      path: SkPath,
      m: SkMatrix,
      op: SkClipOp,
      doAA: Boolean,
    ) {
      TODO("Implement initAsPath")
    }

    private fun initShader(saveCount: Int, shader: SkSp<SkShader>) {
      TODO("Implement initShader")
    }

    private fun initReplaceRect(
      saveCount: Int,
      rect: SkRect,
      doAA: Boolean,
    ) {
      TODO("Implement initReplaceRect")
    }

    private fun setEmpty() {
      TODO("Implement setEmpty")
    }

    private fun checkEmpty() {
      TODO("Implement checkEmpty")
    }

    private fun canBeIntersectedInPlace(saveCount: Int, op: SkClipOp): Boolean {
      TODO("Implement canBeIntersectedInPlace")
    }

    private fun rectRectIntersectAllowed(newR: SkRect, newAA: Boolean): Boolean {
      TODO("Implement rectRectIntersectAllowed")
    }

    private fun updateBoundAndGenID(prior: undefined.Element?) {
      TODO("Implement updateBoundAndGenID")
    }

    private fun combineBoundsDiff(combination: Element.FillCombo, prevFinite: SkRect) {
      TODO("Implement combineBoundsDiff")
    }

    private fun combineBoundsIntersection(combination: Int, prevFinite: SkRect) {
      TODO("Implement combineBoundsIntersection")
    }

    public enum class DeviceSpaceType {
      kEmpty,
      kRect,
      kRRect,
      kPath,
      kShader,
      kLastType,
    }

    public enum class FillCombo {
      kPrev_Cur_FillCombo,
      kPrev_InvCur_FillCombo,
      kInvPrev_Cur_FillCombo,
      kInvPrev_InvCur_FillCombo,
    }

    public companion object {
      public val kTypeCnt: Int = TODO("Initialize kTypeCnt")
    }
  }

  public data class AutoRestore public constructor(
    private var fCS: SkClipStack?,
    private val fSaveCount: Int,
  )

  public open class Iter public constructor() {
    private val fStack: SkClipStack? = TODO("Initialize fStack")

    private var fIter: SkDeque.Iter = TODO("Initialize fIter")

    public constructor(stack: SkClipStack, startLoc: org.skia.core.Iter.IterStart) : this() {
      TODO("Implement constructor")
    }

    public fun next(): undefined.Element {
      TODO("Implement next")
    }

    public fun prev(): undefined.Element {
      TODO("Implement prev")
    }

    public fun skipToTopmost(op: SkClipOp): undefined.Element {
      TODO("Implement skipToTopmost")
    }

    public fun reset(stack: SkClipStack, startLoc: org.skia.core.Iter.IterStart) {
      TODO("Implement reset")
    }

    public enum class IterStart {
      kBottom_IterStart,
      kTop_IterStart,
    }
  }

  public open class B2TIter public constructor() : org.skia.core.Iter() {
    public constructor(stack: SkClipStack) : super(TODO(), TODO()) {
      TODO("Implement constructor")
    }

    public fun reset(stack: SkClipStack) {
      TODO("Implement reset")
    }
  }

  public enum class BoundsType {
    kNormal_BoundsType,
    kInsideOut_BoundsType,
  }

  public companion object {
    private val kInvalidGenID: UInt = TODO("Initialize kInvalidGenID")

    private val kEmptyGenID: UInt = TODO("Initialize kEmptyGenID")

    private val kWideOpenGenID: UInt = TODO("Initialize kWideOpenGenID")

    /**
     * C++ original:
     * ```cpp
     * uint32_t SkClipStack::GetNextGenID() {
     *     // 0-2 are reserved for invalid, empty & wide-open
     *     static const uint32_t kFirstUnreservedGenID = 3;
     *     static std::atomic<uint32_t> nextID{kFirstUnreservedGenID};
     *
     *     uint32_t id;
     *     do {
     *         id = nextID.fetch_add(1, std::memory_order_relaxed);
     *     } while (id < kFirstUnreservedGenID);
     *     return id;
     * }
     * ```
     */
    private fun getNextGenID(): UInt {
      TODO("Implement getNextGenID")
    }
  }
}
