package org.skia.gpu

import RawElement.Stack
import kotlin.Boolean
import kotlin.Int
import org.skia.core.SkClipOp
import org.skia.core.SkStrokeRec
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import undefined.ElementIter
import undefined.ElementList

/**
 * C++ original:
 * ```cpp
 * class ClipStack {
 * public:
 *     // TODO: Some of these states reflect what SkDevice requires. Others are based on what Ganesh
 *     // could handle analytically. They will likely change as graphite's clips are sorted out
 *     enum class ClipState : uint8_t {
 *         kEmpty, kWideOpen, kDeviceRect, kDeviceRRect, kComplex
 *     };
 *
 *     // All data describing a geometric modification to the clip
 *     struct Element {
 *         Shape     fShape;
 *         Transform fLocalToDevice; // TODO: reference a cached Transform like DrawList?
 *         SkClipOp  fOp;
 *     };
 *
 *     // 'owningDevice' must outlive the clip stack.
 *     ClipStack(Device* owningDevice);
 *
 *     ~ClipStack();
 *
 *     ClipStack(const ClipStack&) = delete;
 *     ClipStack& operator=(const ClipStack&) = delete;
 *
 *     ClipState clipState() const { return this->currentSaveRecord().state(); }
 *     int maxDeferredClipDraws() const { return fElements.count(); }
 *     Rect conservativeBounds() const;
 *
 *     class ElementIter;
 *     // Provides for-range over active, valid clip elements from most recent to oldest.
 *     // The iterator provides items as "const Element&".
 *     inline ElementIter begin() const;
 *     inline ElementIter end() const;
 *
 *     // Clip stack manipulation
 *     void save();
 *     void restore();
 *
 *     // The clip stack does not have a notion of AA vs. non-AA. However, if PixelSnapping::kYes is
 *     // used and the right conditions are met, it can adjust the clip geometry to align with the
 *     // pixel grid and emulate some aspects of non-AA behavior.
 *     enum class PixelSnapping : bool {
 *         kNo = false,
 *         kYes = true
 *     };
 *     void clipShape(const Transform& localToDevice, const Shape& shape, SkClipOp op,
 *                    PixelSnapping = PixelSnapping::kNo);
 *     void clipShader(sk_sp<SkShader> shader);
 *
 *     // Compute the bounds and the effective elements of the clip stack when applied to the draw
 *     // described by the provided transform, shape, and stroke.
 *     //
 *     // Applying clips to a draw is a mostly lazy operation except for what is returned:
 *     //  - The Clip's scissor is set to 'conservativeBounds()'.
 *     //  - The Clip stores the draw's clipped bounds, taking into account its transform, styling, and
 *     //    the above scissor.
 *     //  - The Clip also stores the draw's fill-style invariant clipped bounds which is used in atlas
 *     //    draws and may differ from the draw bounds.
 *     //  - The Clip may contain an analytic clip (geometry or texture mask) that must be included in
 *     //    the draw's PaintParams.
 *     //  - The draw's Geometry may be intersected geometrically with clip elements, potentially
 *     //    impacting the final choice of Renderer.
 *     //
 *     // All remaining clip elements that affect the draw will be returned in `outEffectiveElements`.
 *     // The per-clip element state has to be explicitly updated by calling `updateClipStateForDraw()`
 *     // which prepares the clip stack for later rendering.
 *     using ElementList = skia_private::STArray<4, const Element*>;
 *     Clip visitClipStackForDraw(const Transform&,
 *                                Geometry*,
 *                                const SkStrokeRec&,
 *                                ElementList* outEffectiveElements) const;
 *
 *     // Update the per-clip element state for later rendering using pre-computed clip state data for
 *     // a particular draw. The provided 'z' value is the depth value that the draw will use if it's
 *     // not clipped out entirely.
 *     //
 *     // The returned CompressedPaintersOrder is the largest order that will be used by any of the
 *     // clip elements that affect the draw.
 *     //
 *     // If the provided `clipState` indicates that the draw will be clipped out, then this method has
 *     // no effect and returns DrawOrder::kNoIntersection.
 *     CompressedPaintersOrder updateClipStateForDraw(const Clip& clip,
 *                                                    const ElementList& effectiveElements,
 *                                                    const BoundsManager*,
 *                                                    PaintersDepth z);
 *
 *     void recordDeferredClipDraws();
 *
 * private:
 *     // SaveRecords and Elements are stored in two parallel stacks. The top-most SaveRecord is the
 *     // active record, older records represent earlier save points and aren't modified until they
 *     // become active again. Elements may be owned by the active SaveRecord, in which case they are
 *     // fully mutable, or they may be owned by a prior SaveRecord. However, Elements from both the
 *     // active SaveRecord and older records can be valid and affect draw operations. Elements are
 *     // marked inactive when new elements are determined to supersede their effect completely.
 *     // Inactive elements of the active SaveRecord can be deleted immediately; inactive elements of
 *     // older SaveRecords may become active again as the save stack is popped back.
 *     //
 *     // See go/grclipstack-2.0 for additional details and visualization of the data structures.
 *     class SaveRecord;
 *
 *     // Internally, a lot of clip reasoning is based on an op, outer bounds, and whether a shape
 *     // contains another (possibly just conservatively based on inner/outer device-space bounds).
 *     // Element and SaveRecord store this information directly. A regular draw is equivalent to a
 *     // clip element with the intersection op; an inverse-filled draw is the difference op.
 *     //
 *     // TransformedShape is a lightweight wrapper that can convert these different types into a
 *     // common type that Simplify() can reason about.
 *     struct TransformedShape;
 *     class DrawShape;
 *
 *     // This captures which of the two elements in (A op B) would be required when they are combined,
 *     // where op is intersect or difference.
 *     enum class SimplifyResult {
 *         kEmpty,
 *         kAOnly,
 *         kBOnly,
 *         kBoth
 *     };
 *     static SimplifyResult Simplify(const TransformedShape& a, const TransformedShape& b);
 *
 *     // Returns how this element affects the draw after more detailed analysis.
 *     enum class DrawInfluence {
 *         kClipsOutDraw,       // The element causes the draw shape to be entirely clipped out
 *         kReplacesDraw,       // The element is fully covered, so the draw's shape can be ignored
 *         kNone,               // The element does not affect the draw
 *         kComplexInteraction, // The element affects the draw shape in a complex way
 *     };
 *     static DrawInfluence SimplifyForDraw(const TransformedShape& clip,
 *                                          const TransformedShape& draw);
 *
 *     // Wraps the geometric Element data with logic for containment and bounds testing.
 *     class RawElement : public Element {
 *     public:
 *         using Stack = SkTBlockList<RawElement, 1>;
 *
 *         RawElement(const Rect& deviceBounds,
 *                    const Transform& localToDevice,
 *                    const Shape& shape,
 *                    SkClipOp op,
 *                    PixelSnapping);
 *
 *         ~RawElement() {
 *             // A pending draw means the element affects something already recorded, so its own
 *             // shape needs to be recorded as a draw. Since recording requires the Device (and
 *             // DrawContext), it must happen before we destroy the element itself.
 *             SkASSERT(!this->hasPendingDraw());
 *         }
 *
 *         // Silence warnings about implicit copy ctor/assignment because we're declaring a dtor
 *         RawElement(const RawElement&) = default;
 *         RawElement& operator=(const RawElement&) = default;
 *
 *         operator TransformedShape() const;
 *
 *         bool             hasPendingDraw() const { return fOrder != DrawOrder::kNoIntersection; }
 *         const Shape&     shape()          const { return fShape;         }
 *         const Transform& localToDevice()  const { return fLocalToDevice; }
 *         const Rect&      outerBounds()    const { return fOuterBounds;   }
 *         const Rect&      innerBounds()    const { return fInnerBounds;   }
 *         SkClipOp         op()             const { return fOp;            }
 *         ClipState        clipType()       const;
 *
 *         // As new elements are pushed on to the stack, they may make older elements redundant.
 *         // The old elements are marked invalid so they are skipped during clip application, but may
 *         // become active again when a save record is restored.
 *         bool isInvalid() const { return fInvalidatedByIndex >= 0; }
 *         void markInvalid(const SaveRecord& current);
 *         void restoreValid(const SaveRecord& current);
 *
 *         // 'added' represents a new op added to the element stack. Its combination with this element
 *         // can result in a number of possibilities:
 *         //  1. The entire clip is empty (signaled by both this and 'added' being invalidated).
 *         //  2. The 'added' op supercedes this element (this element is invalidated).
 *         //  3. This op supercedes the 'added' element (the added element is marked invalidated).
 *         //  4. Their combination can be represented by a single new op (in which case this
 *         //     element should be invalidated, and the combined shape stored in 'added').
 *         //  5. Or both elements remain needed to describe the clip (both are valid and unchanged).
 *         //
 *         // The calling element will only modify its invalidation index since it could belong
 *         // to part of the inactive stack (that might be restored later). All merged state/geometry
 *         // is handled by modifying 'added'.
 *         void updateForElement(RawElement* added, const SaveRecord& current);
 *
 *         DrawInfluence testForDraw(const TransformedShape& draw) const;
 *
 *         // Updates usage tracking to incorporate the bounds and Z value for the new draw call.
 *         // If this element hasn't affected any prior draws, it will use the bounds manager to
 *         // assign itself a compressed painters order for later rendering.
 *         //
 *         // This method assumes that this element affects the draw in a complex way, such that
 *         // calling `testForDraw()` on the same draw would return `DrawInfluence::kIntersect`. It is
 *         // assumed that `testForDraw()` was called beforehand to ensure that this is the case.
 *         //
 *         // Assuming that this element does not clip out the draw, returns the painters order the
 *         // draw must sort after.
 *         CompressedPaintersOrder updateForDraw(const BoundsManager* boundsManager,
 *                                               const Rect& deviceBounds,
 *                                               const Rect& drawBounds,
 *                                               PaintersDepth drawZ);
 *
 *         // Record a depth-only draw to the given device, restricted to the portion of the clip that
 *         // is actually required based on prior recorded draws. Resets usage tracking for subsequent
 *         // passes.
 *         void drawClip(Device*);
 *
 *         void validate() const;
 *
 *     private:
 *         // TODO: Should only combine elements within the same save record, that don't have pending
 *         // draws already. Otherwise, we're changing the geometry that will be rasterized and it
 *         // could lead to gaps even if in a perfect the world the analytically intersected shape was
 *         // equivalent. Can't combine with other save records, since they *might* become pending
 *         // later on.
 *         bool combine(const RawElement& other, const SaveRecord& current);
 *
 *         // Device space bounds. These bounds are not snapped to pixels with the assumption that if
 *         // a relation (intersects, contains, etc.) is true for the bounds it will be true for the
 *         // rasterization of the coordinates that produced those bounds.
 *         Rect fInnerBounds;
 *         Rect fOuterBounds;
 *         // TODO: Convert fOuterBounds to a ComplementRect to make intersection tests faster?
 *         // Would need to store both original and complement, since the intersection test is
 *         // Rect + ComplementRect and Element/SaveRecord could be on either side of operation.
 *
 *         // State tracking how this clip element needs to be recorded into the draw context. As the
 *         // clip stack is applied to additional draws, the clip's Z and usage bounds grow to account
 *         // for it; its compressed painter's order is selected the first time a draw is affected.
 *         Rect fUsageBounds;
 *         CompressedPaintersOrder fOrder;
 *         PaintersDepth fMaxZ;
 *
 *         // Elements are invalidated by SaveRecords as the record is updated with new elements that
 *         // override old geometry. An invalidated element stores the index of the first element of
 *         // the save record that invalidated it. This makes it easy to undo when the save record is
 *         // popped from the stack, and is stable as the current save record is modified.
 *         int fInvalidatedByIndex;
 *     };
 *
 *     // Represents a saved point in the clip stack, and manages the life time of elements added to
 *     // stack within the record's life time. Also provides the logic for determining active elements
 *     // given a draw query.
 *     class SaveRecord {
 *     public:
 *         using Stack = SkTBlockList<SaveRecord, 2>;
 *
 *         explicit SaveRecord(const Rect& deviceBounds);
 *
 *         SaveRecord(const SaveRecord& prior, int startingElementIndex);
 *
 *         const SkShader* shader()      const { return fShader.get(); }
 *         const Rect&     outerBounds() const { return fOuterBounds;  }
 *         const Rect&     innerBounds() const { return fInnerBounds;  }
 *         SkClipOp        op()          const { return fStackOp;      }
 *         ClipState       state()       const;
 *         uint32_t        genID()       const;
 *
 *         int  firstActiveElementIndex() const { return fStartingElementIndex;     }
 *         int  oldestElementIndex()      const { return fOldestValidIndex;         }
 *         bool canBeUpdated()            const { return (fDeferredSaveCount == 0); }
 *
 *         DrawInfluence testForDraw(const TransformedShape& draw) const;
 *
 *         // Deferred save manipulation
 *         void pushSave() {
 *             SkASSERT(fDeferredSaveCount >= 0);
 *             fDeferredSaveCount++;
 *         }
 *         // Returns true if the record should stay alive. False means the ClipStack must delete it
 *         bool popSave() {
 *             fDeferredSaveCount--;
 *             SkASSERT(fDeferredSaveCount >= -1);
 *             return fDeferredSaveCount >= 0;
 *         }
 *
 *         // Return true if the element was added to 'elements', or otherwise affected the save record
 *         // (e.g. turned it empty).
 *         bool addElement(RawElement&& toAdd, RawElement::Stack* elements, Device*);
 *
 *         void addShader(sk_sp<SkShader> shader);
 *
 *         // Remove the elements owned by this save record, which must happen before the save record
 *         // itself is removed from the clip stack. Records draws for any removed elements that have
 *         // draw usages.
 *         void removeElements(RawElement::Stack* elements, Device*);
 *
 *         // Restore element validity now that this record is the new top of the stack.
 *         void restoreElements(RawElement::Stack* elements);
 *
 *     private:
 *         // These functions modify 'elements' and element-dependent state of the record
 *         // (such as valid index and fState). Records draws for any clips that have deferred usages
 *         // that are inactivated and cannot be restored (i.e. part of the active save record).
 *         bool appendElement(RawElement&& toAdd, RawElement::Stack* elements, Device*);
 *         void replaceWithElement(RawElement&& toAdd, RawElement::Stack* elements, Device*);
 *
 *         // Inner bounds is always contained in outer bounds, or it is empty. All bounds will be
 *         // contained in the device bounds.
 *         Rect fInnerBounds; // Inside is full coverage (stack op == intersect) or 0 cov (diff)
 *         Rect fOuterBounds; // Outside is 0 coverage (op == intersect) or full cov (diff)
 *
 *         // A save record can have up to one shader, multiple shaders are automatically blended
 *         sk_sp<SkShader> fShader;
 *
 *         const int fStartingElementIndex; // First element owned by this save record
 *         int       fOldestValidIndex;     // Index of oldest element that's valid for this record
 *         int       fDeferredSaveCount;    // Number of save() calls without modifications (yet)
 *
 *         // Will be kIntersect unless every valid element is kDifference, which is significant
 *         // because if kDifference then there is an implicit extra outer bounds at the device edges.
 *         SkClipOp  fStackOp;
 *         ClipState fState;
 *         uint32_t  fGenID;
 *     };
 *
 *     Rect deviceBounds() const;
 *
 *     const SaveRecord& currentSaveRecord() const {
 *         SkASSERT(!fSaves.empty());
 *         return fSaves.back();
 *     }
 *
 *     // Will return the current save record, properly updating deferred saves
 *     // and initializing a first record if it were empty.
 *     SaveRecord& writableSaveRecord(bool* wasDeferred);
 *
 *     RawElement::Stack fElements;
 *     SaveRecord::Stack fSaves; // always has one wide open record at the top
 *
 *     Device* fDevice; // the device this clip stack is coupled with
 * }
 * ```
 */
public data class ClipStack public constructor(
  /**
   * C++ original:
   * ```cpp
   * RawElement::Stack fElements
   * ```
   */
  private var fElements: Int,
  /**
   * C++ original:
   * ```cpp
   * SaveRecord::Stack fSaves
   * ```
   */
  private var fSaves: Int,
  /**
   * C++ original:
   * ```cpp
   * Device* fDevice
   * ```
   */
  private var fDevice: Device?,
) {
  /**
   * C++ original:
   * ```cpp
   * ClipStack& operator=(const ClipStack&) = delete
   * ```
   */
  public fun assign(param0: ClipStack) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * ClipState clipState() const { return this->currentSaveRecord().state(); }
   * ```
   */
  public fun clipState(): ClipState {
    TODO("Implement clipState")
  }

  /**
   * C++ original:
   * ```cpp
   * int maxDeferredClipDraws() const { return fElements.count(); }
   * ```
   */
  public fun maxDeferredClipDraws(): Int {
    TODO("Implement maxDeferredClipDraws")
  }

  /**
   * C++ original:
   * ```cpp
   * Rect ClipStack::conservativeBounds() const {
   *     const SaveRecord& current = this->currentSaveRecord();
   *     if (current.state() == ClipState::kEmpty) {
   *         return Rect::InfiniteInverted();
   *     } else if (current.state() == ClipState::kWideOpen) {
   *         return this->deviceBounds();
   *     } else {
   *         if (current.op() == SkClipOp::kDifference) {
   *             // The outer/inner bounds represent what's cut out, so full bounds remains the device
   *             // bounds, minus any fully clipped content that spans the device edge.
   *             return subtract(this->deviceBounds(), current.innerBounds(), /* exact */ true);
   *         } else {
   *             SkASSERT(this->deviceBounds().contains(current.outerBounds()));
   *             return current.outerBounds();
   *         }
   *     }
   * }
   * ```
   */
  public fun conservativeBounds(): Int {
    TODO("Implement conservativeBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * inline ElementIter begin() const
   * ```
   */
  public fun begin(): ElementIter {
    TODO("Implement begin")
  }

  /**
   * C++ original:
   * ```cpp
   * inline ElementIter end() const
   * ```
   */
  public fun end(): ElementIter {
    TODO("Implement end")
  }

  /**
   * C++ original:
   * ```cpp
   * void ClipStack::save() {
   *     SkASSERT(!fSaves.empty());
   *     fSaves.back().pushSave();
   * }
   * ```
   */
  public fun save() {
    TODO("Implement save")
  }

  /**
   * C++ original:
   * ```cpp
   * void ClipStack::restore() {
   *     SkASSERT(!fSaves.empty());
   *     SaveRecord& current = fSaves.back();
   *     if (current.popSave()) {
   *         // This was just a deferred save being undone, so the record doesn't need to be removed yet
   *         return;
   *     }
   *
   *     // When we remove a save record, we delete all elements >= its starting index and any masks
   *     // that were rasterized for it.
   *     current.removeElements(&fElements, fDevice);
   *
   *     fSaves.pop_back();
   *     // Restore any remaining elements that were only invalidated by the now-removed save record.
   *     fSaves.back().restoreElements(&fElements);
   * }
   * ```
   */
  public fun restore() {
    TODO("Implement restore")
  }

  /**
   * C++ original:
   * ```cpp
   * void ClipStack::clipShape(const Transform& localToDevice,
   *                           const Shape& shape,
   *                           SkClipOp op,
   *                           PixelSnapping snapping) {
   *     if (this->currentSaveRecord().state() == ClipState::kEmpty) {
   *         return;
   *     }
   *
   *     // This will apply the transform if it's shape-type preserving, and clip the element's bounds
   *     // to the device bounds (NOT the conservative clip bounds, since those are based on the net
   *     // effect of all elements while device bounds clipping happens implicitly. During addElement,
   *     // we may still be able to invalidate some older elements).
   *     // NOTE: Does not try to simplify the shape type by inspecting the SkPath.
   *     RawElement element{this->deviceBounds(), localToDevice, shape, op, snapping};
   *
   *     // An empty op means do nothing (for difference), or close the save record, so we try and detect
   *     // that early before doing additional unnecessary save record allocation.
   *     if (element.shape().isEmpty()) {
   *         if (element.op() == SkClipOp::kDifference) {
   *             // If the shape is empty and we're subtracting, this has no effect on the clip
   *             return;
   *         }
   *         // else we will make the clip empty, but we need a new save record to record that change
   *         // in the clip state; fall through to below and updateForElement() will handle it.
   *     }
   *
   *     bool wasDeferred;
   *     SaveRecord& save = this->writableSaveRecord(&wasDeferred);
   *     SkDEBUGCODE(int elementCount = fElements.count();)
   *     if (!save.addElement(std::move(element), &fElements, fDevice)) {
   *         if (wasDeferred) {
   *             // We made a new save record, but ended up not adding an element to the stack.
   *             // So instead of keeping an empty save record around, pop it off and restore the counter
   *             SkASSERT(elementCount == fElements.count());
   *             fSaves.pop_back();
   *             fSaves.back().pushSave();
   *         }
   *     }
   * }
   * ```
   */
  public fun clipShape(
    localToDevice: Transform,
    shape: Shape,
    op: SkClipOp,
    snapping: PixelSnapping = TODO(),
  ) {
    TODO("Implement clipShape")
  }

  /**
   * C++ original:
   * ```cpp
   * void ClipStack::clipShader(sk_sp<SkShader> shader) {
   *     // Shaders can't bring additional coverage
   *     if (this->currentSaveRecord().state() == ClipState::kEmpty) {
   *         return;
   *     }
   *
   *     bool wasDeferred;
   *     this->writableSaveRecord(&wasDeferred).addShader(std::move(shader));
   *     // Geometry elements are not invalidated by updating the clip shader
   *     // TODO(b/238763003): Integrating clipShader into graphite needs more thought, particularly how
   *     // to handle the shader explosion and where to put the effects in the GraphicsPipelineDesc.
   *     // One idea is to use sample locations and draw the clipShader into the depth buffer.
   *     // Another is resolve the clip shader into an alpha mask image that is sampled by the draw.
   * }
   * ```
   */
  public fun clipShader(shader: SkSp<SkShader>) {
    TODO("Implement clipShader")
  }

  /**
   * C++ original:
   * ```cpp
   * Clip ClipStack::visitClipStackForDraw(const Transform& localToDevice,
   *                                       Geometry* geometry,
   *                                       const SkStrokeRec& style,
   *                                       ClipStack::ElementList* outEffectiveElements) const {
   *     static const Clip kClippedOut = {
   *             Rect::InfiniteInverted(), Rect::InfiniteInverted(), SkIRect::MakeEmpty(),
   *             /* nonMSAAClip= */ {}, /* shader= */ nullptr};
   *
   *     const SaveRecord& cs = this->currentSaveRecord();
   *     if (cs.state() == ClipState::kEmpty) {
   *         // We know the draw is clipped out so don't bother computing the base draw bounds.
   *         return kClippedOut;
   *     }
   *     // Compute draw bounds, clipped only to our device bounds since we need to return that even if
   *     // the clip stack is known to be wide-open.
   *     const Rect deviceBounds = this->deviceBounds();
   *
   *     DrawShape draw{localToDevice, *geometry};
   *     if (!draw.applyStyle(style, deviceBounds)) {
   *         return kClippedOut;
   *     }
   *
   *     // For intersect clips, the scissor rectangle is snapped outer bounds (to loosely restrict
   *     // rasterization if absolutely necessary). Cases where the draw is fully inside the scissor are
   *     // automatically handled during GPU command generation.
   *     //
   *     // For difference clips, a tight scissor could be `subtract(drawBounds, cs.innerBounds())`
   *     // but this is only useful when the clip spans across an axis of the draw and can otherwise
   *     // lead to scissor state thrashing since it's connected to the draw's bounds as well. So just
   *     // use the device bounds for simplicity.
   *     draw.applyScissor(cs.op() == SkClipOp::kIntersect ? snap_scissor(cs.outerBounds(), deviceBounds)
   *                                                       : deviceBounds);
   *
   *     switch (cs.testForDraw(draw)) {
   *         case DrawInfluence::kClipsOutDraw:
   *             // The draw is offscreen or clipped out, so there is no need to visit the clip elements.
   *             return kClippedOut;
   *
   *         case DrawInfluence::kNone:
   *             // The draw is unaffected by the clip stack (except possibly `scissor`), and there's no
   *             // need to visit each clip element.
   *             return draw.toClip(geometry, {}, cs.shader());
   *
   *         case DrawInfluence::kReplacesDraw:
   *             // The draw covers the clip entirely. Replace the shape with a flood fill, which can
   *             // intersect with shapes efficiently.
   *             draw.resetToFloodFill();
   *             [[fallthrough]];
   *
   *         case DrawInfluence::kComplexInteraction:
   *             // Check each element's influence on the draw below
   *             break;
   *     }
   *
   *     SkASSERT(outEffectiveElements);
   *     SkASSERT(outEffectiveElements->empty());
   *     int i = fElements.count();
   *     NonMSAAClip nonMSAAClip;
   *     for (const RawElement& e : fElements.ritems()) {
   *         --i;
   *         if (i < cs.oldestElementIndex()) {
   *             // All earlier elements have been invalidated by elements already processed so the draw
   *             // can't be affected by them and cannot contribute to their usage bounds.
   *             break;
   *         }
   *
   *         switch (e.testForDraw(draw)) {
   *             case DrawInfluence::kClipsOutDraw:
   *                 // Per-element check was able to completely reject the draw.
   *                 outEffectiveElements->clear();
   *                 return kClippedOut;
   *
   *             case DrawInfluence::kNone:
   *                 // This element does not interact, so continue to the next
   *                 continue;
   *
   *             case DrawInfluence::kReplacesDraw:
   *                 // This element is covered entirely by the draw, so the draw's geometry can be
   *                 // replaced assuming the coordinate spaces are compatible. To facilitate this, we
   *                 // switch the drawn geometry to a flood fill and then fall through to intersection.
   *                 // Even if the coordinate spaces aren't in alignment, this eliminates the draw's
   *                 // source of analytic coverage.
   *                 draw.resetToFloodFill();
   *
   *                 [[fallthrough]];
   *
   *             case DrawInfluence::kComplexInteraction:
   *                 // First try to handle the clip geometrically
   *                 if (e.op() == SkClipOp::kIntersect && draw.intersectClipElement(e)) {
   *                     continue;
   *                 }
   *                 // Second try to tighten the scissor, which is lighter weight than adding an
   *                 // analytic clip pipeline variation or triggering MSAA.
   *                 if (e.clipType() == ClipState::kDeviceRect) {
   *                     Rect scissor = e.shape().rect().makeRound();
   *                     if (e.shape().rect().nearlyEquals(scissor, Shape::kDefaultPixelTolerance)) {
   *                         // Pass in `scissor` since these need to be integral values while
   *                         // nearlyEquals allows the original rect coordinates to be slightly
   *                         // different (causing problems later with asSkIRect()).
   *                         draw.applyScissor(scissor);
   *                         continue;
   *                     }
   *                 }
   *                 // // Third try to handle the clip analytically in the shader
   *                 if (nonMSAAClip.fAnalyticClip.isEmpty()) {
   *                     nonMSAAClip.fAnalyticClip = can_apply_analytic_clip(e.shape(),
   *                                                                         e.localToDevice());
   *                     if (!nonMSAAClip.fAnalyticClip.isEmpty()) {
   *                         continue;
   *                     }
   *                 }
   *                 // Fourth, remember the element for later, either to be a depth-only draw or to be
   *                 // flattened into a clip mask.
   *                 // Otherwise, accumulate it for later. Depending on how many elements are collected
   *                 // we may use the scissor, analytic clip, or MSAA/atlas.
   *                 outEffectiveElements->push_back(&e);
   *                 break;
   *         }
   *     }
   *
   *     // If there is no MSAA supported, rasterize any remaining elements by flattening them
   *     // into a single mask and storing in an atlas. Otherwise these will be handled by
   *     // Device::drawClip().
   *     ClipAtlasManager* clipAtlas =
   *             fDevice->recorder()->priv().atlasProvider()->getClipAtlasManager();
   *     if (clipAtlas && !outEffectiveElements->empty()) {
   *         AtlasClip* atlasClip = &nonMSAAClip.fAtlasClip;
   *
   *         SkIRect iMaskBounds = cs.outerBounds().makeRoundOut().asSkIRect();
   *         sk_sp<TextureProxy> proxy = clipAtlas->findOrCreateEntry(cs.genID(),
   *                                                                  outEffectiveElements,
   *                                                                  iMaskBounds,
   *                                                                  &atlasClip->fOutPos);
   *         if (proxy) {
   *             // Add to Clip
   *             atlasClip->fMaskBounds = iMaskBounds;
   *             atlasClip->fAtlasTexture = std::move(proxy);
   *
   *             // Elements are represented in the clip atlas, discard.
   *             outEffectiveElements->clear();
   *         }
   *     }
   *
   *     return draw.toClip(geometry, nonMSAAClip, cs.shader());
   * }
   * ```
   */
  public fun visitClipStackForDraw(
    localToDevice: Transform,
    geometry: Geometry?,
    style: SkStrokeRec,
    outEffectiveElements: ElementList?,
  ): Int {
    TODO("Implement visitClipStackForDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * CompressedPaintersOrder ClipStack::updateClipStateForDraw(const Clip& clip,
   *                                                           const ElementList& effectiveElements,
   *                                                           const BoundsManager* boundsManager,
   *                                                           PaintersDepth z) {
   *     if (clip.isClippedOut()) {
   *         return DrawOrder::kNoIntersection;
   *     }
   *
   *     SkDEBUGCODE(const SaveRecord& cs = this->currentSaveRecord();)
   *     SkASSERT(cs.state() != ClipState::kEmpty);
   *
   *     Rect deviceBounds = this->deviceBounds();
   *     CompressedPaintersOrder maxClipOrder = DrawOrder::kNoIntersection;
   *     for (int i = 0; i < effectiveElements.size(); ++i) {
   *         // ClipStack owns the elements in the `clipState` so it's OK to downcast and cast away
   *         // const.
   *         // TODO: Enforce the ownership? In debug builds we could invalidate a `ClipStateForDraw` if
   *         // its element pointers become dangling and assert validity here.
   *         const RawElement* e = static_cast<const RawElement*>(effectiveElements[i]);
   *         CompressedPaintersOrder order =  const_cast<RawElement*>(e)->updateForDraw(
   *                 boundsManager, deviceBounds, clip.drawBounds(), z);
   *         maxClipOrder = std::max(order, maxClipOrder);
   *     }
   *
   *     return maxClipOrder;
   * }
   * ```
   */
  public fun updateClipStateForDraw(
    clip: Clip,
    effectiveElements: ElementList,
    boundsManager: BoundsManager?,
    z: PaintersDepth,
  ): Int {
    TODO("Implement updateClipStateForDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void ClipStack::recordDeferredClipDraws() {
   *     for (auto& e : fElements.items()) {
   *         // When a Device requires all clip elements to be recorded, we have to iterate all elements,
   *         // and will draw clip shapes for elements that are still marked as invalid from the clip
   *         // stack, including those that are older than the current save record's oldest valid index,
   *         // because they could have accumulated draw usage prior to being invalidated, but weren't
   *         // flushed when they were invalidated because of an intervening save.
   *         e.drawClip(fDevice);
   *     }
   * }
   * ```
   */
  public fun recordDeferredClipDraws() {
    TODO("Implement recordDeferredClipDraws")
  }

  /**
   * C++ original:
   * ```cpp
   * Rect ClipStack::deviceBounds() const {
   *     return Rect::WH(fDevice->width(), fDevice->height());
   * }
   * ```
   */
  private fun deviceBounds(): Int {
    TODO("Implement deviceBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * const SaveRecord& currentSaveRecord() const {
   *         SkASSERT(!fSaves.empty());
   *         return fSaves.back();
   *     }
   * ```
   */
  private fun currentSaveRecord(): SaveRecord {
    TODO("Implement currentSaveRecord")
  }

  /**
   * C++ original:
   * ```cpp
   * ClipStack::SaveRecord& ClipStack::writableSaveRecord(bool* wasDeferred) {
   *     SaveRecord& current = fSaves.back();
   *     if (current.canBeUpdated()) {
   *         // Current record is still open, so it can be modified directly
   *         *wasDeferred = false;
   *         return current;
   *     } else {
   *         // Must undefer the save to get a new record.
   *         SkAssertResult(current.popSave());
   *         *wasDeferred = true;
   *         return fSaves.emplace_back(current, fElements.count());
   *     }
   * }
   * ```
   */
  private fun writableSaveRecord(wasDeferred: Boolean?): SaveRecord {
    TODO("Implement writableSaveRecord")
  }

  public open class Element public constructor(
    public var fShape: Int,
    public var fLocalToDevice: Int,
    public var fOp: SkClipOp,
  )

  public open class RawElement public constructor(
    deviceBounds: Rect,
    localToDevice: Transform,
    shape: Shape,
    op: SkClipOp,
    snapping: undefined.PixelSnapping,
  ) : undefined.Element(TODO(), TODO(), TODO()) {
    private var fInnerBounds: Int = TODO("Initialize fInnerBounds")

    private var fOuterBounds: Int = TODO("Initialize fOuterBounds")

    private var fUsageBounds: Int = TODO("Initialize fUsageBounds")

    private var fOrder: Int = TODO("Initialize fOrder")

    private var fMaxZ: Int = TODO("Initialize fMaxZ")

    private var fInvalidatedByIndex: Int = TODO("Initialize fInvalidatedByIndex")

    public constructor(param0: undefined.RawElement) : this() {
      TODO("Implement constructor")
    }

    public fun assign(param0: undefined.RawElement) {
      TODO("Implement assign")
    }

    public fun hasPendingDraw(): Boolean {
      TODO("Implement hasPendingDraw")
    }

    public fun shape(): Int {
      TODO("Implement shape")
    }

    public fun localToDevice(): Int {
      TODO("Implement localToDevice")
    }

    public fun outerBounds(): Int {
      TODO("Implement outerBounds")
    }

    public fun innerBounds(): Int {
      TODO("Implement innerBounds")
    }

    public fun op(): SkClipOp {
      TODO("Implement op")
    }

    public fun clipType(): undefined.ClipState {
      TODO("Implement clipType")
    }

    public fun isInvalid(): Boolean {
      TODO("Implement isInvalid")
    }

    public fun markInvalid(current: undefined.SaveRecord) {
      TODO("Implement markInvalid")
    }

    public fun restoreValid(current: undefined.SaveRecord) {
      TODO("Implement restoreValid")
    }

    public fun updateForElement(added: undefined.RawElement?, current: undefined.SaveRecord) {
      TODO("Implement updateForElement")
    }

    public fun testForDraw(draw: undefined.TransformedShape): undefined.DrawInfluence {
      TODO("Implement testForDraw")
    }

    public fun updateForDraw(
      boundsManager: BoundsManager?,
      deviceBounds: Rect,
      drawBounds: Rect,
      drawZ: PaintersDepth,
    ): Int {
      TODO("Implement updateForDraw")
    }

    public fun drawClip(device: Device?) {
      TODO("Implement drawClip")
    }

    public fun validate() {
      TODO("Implement validate")
    }

    private fun combine(other: undefined.RawElement, current: undefined.SaveRecord): Boolean {
      TODO("Implement combine")
    }

    public fun toTransformedShape(): TransformedShape {
      TODO("Implement toTransformedShape")
    }
  }

  public data class SaveRecord public constructor(
    private var fInnerBounds: Int,
    private var fOuterBounds: Int,
    private var fShader: Int,
    private val fStartingElementIndex: Int,
    private var fOldestValidIndex: Int,
    private var fDeferredSaveCount: Int,
    private var fStackOp: SkClipOp,
    private var fState: undefined.ClipState,
    private var fGenID: Int,
  ) {
    public fun shader(): Int {
      TODO("Implement shader")
    }

    public fun outerBounds(): Int {
      TODO("Implement outerBounds")
    }

    public fun innerBounds(): Int {
      TODO("Implement innerBounds")
    }

    public fun op(): SkClipOp {
      TODO("Implement op")
    }

    public fun state(): undefined.ClipState {
      TODO("Implement state")
    }

    public fun genID(): Int {
      TODO("Implement genID")
    }

    public fun firstActiveElementIndex(): Int {
      TODO("Implement firstActiveElementIndex")
    }

    public fun oldestElementIndex(): Int {
      TODO("Implement oldestElementIndex")
    }

    public fun canBeUpdated(): Boolean {
      TODO("Implement canBeUpdated")
    }

    public fun testForDraw(draw: undefined.TransformedShape): undefined.DrawInfluence {
      TODO("Implement testForDraw")
    }

    public fun pushSave() {
      TODO("Implement pushSave")
    }

    public fun popSave(): Boolean {
      TODO("Implement popSave")
    }

    public fun addElement(
      toAdd: undefined.RawElement,
      elements: Stack?,
      device: Device?,
    ): Boolean {
      TODO("Implement addElement")
    }

    public fun addShader(shader: SkSp<SkShader>) {
      TODO("Implement addShader")
    }

    public fun removeElements(elements: Stack?, device: Device?) {
      TODO("Implement removeElements")
    }

    public fun restoreElements(elements: Stack?) {
      TODO("Implement restoreElements")
    }

    private fun appendElement(
      toAdd: undefined.RawElement,
      elements: Stack?,
      device: Device?,
    ): Boolean {
      TODO("Implement appendElement")
    }

    private fun replaceWithElement(
      toAdd: undefined.RawElement,
      elements: Stack?,
      device: Device?,
    ) {
      TODO("Implement replaceWithElement")
    }
  }

  public enum class ClipState {
    kEmpty,
    kWideOpen,
    kDeviceRect,
    kDeviceRRect,
    kComplex,
  }

  public enum class PixelSnapping {
    kNo,
    kYes,
  }

  public enum class SimplifyResult {
    kEmpty,
    kAOnly,
    kBOnly,
    kBoth,
  }

  public enum class DrawInfluence {
    kClipsOutDraw,
    kReplacesDraw,
    kNone,
    kComplexInteraction,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * ClipStack::SimplifyResult ClipStack::Simplify(const TransformedShape& a,
     *                                               const TransformedShape& b) {
     *     enum class ClipCombo {
     *         kDD = 0b00,
     *         kDI = 0b01,
     *         kID = 0b10,
     *         kII = 0b11
     *     };
     *
     *     switch(static_cast<ClipCombo>(((int) a.fOp << 1) | (int) b.fOp)) {
     *         case ClipCombo::kII:
     *             // Intersect (A) + Intersect (B)
     *             if (!a.intersects(b)) {
     *                 // Regions with non-zero coverage are disjoint, so intersection = empty
     *                 return SimplifyResult::kEmpty;
     *             } else if (b.contains(a)) {
     *                 // B's full coverage region contains entirety of A, so intersection = A
     *                 return SimplifyResult::kAOnly;
     *             } else if (a.contains(b)) {
     *                 // A's full coverage region contains entirety of B, so intersection = B
     *                 return SimplifyResult::kBOnly;
     *             } else {
     *                 // The shapes intersect in some non-trivial manner
     *                 return SimplifyResult::kBoth;
     *             }
     *         case ClipCombo::kID:
     *             // Intersect (A) + Difference (B)
     *             if (!a.intersects(b)) {
     *                 // A only intersects B's full coverage region, so intersection = A
     *                 return SimplifyResult::kAOnly;
     *             } else if (b.contains(a)) {
     *                 // B's zero coverage region completely contains A, so intersection = empty
     *                 return SimplifyResult::kEmpty;
     *             } else {
     *                 // Intersection cannot be simplified. Note that the combination of a intersect
     *                 // and difference op in this order cannot produce kBOnly
     *                 return SimplifyResult::kBoth;
     *             }
     *         case ClipCombo::kDI:
     *             // Difference (A) + Intersect (B) - the mirror of Intersect(A) + Difference(B),
     *             // but combining is commutative so this is equivalent barring naming.
     *             if (!b.intersects(a)) {
     *                 // B only intersects A's full coverage region, so intersection = B
     *                 return SimplifyResult::kBOnly;
     *             } else if (a.contains(b)) {
     *                 // A's zero coverage region completely contains B, so intersection = empty
     *                 return SimplifyResult::kEmpty;
     *             } else {
     *                 // Cannot be simplified
     *                 return SimplifyResult::kBoth;
     *             }
     *         case ClipCombo::kDD:
     *             // Difference (A) + Difference (B)
     *             if (a.contains(b)) {
     *                 // A's zero coverage region contains B, so B doesn't remove any extra
     *                 // coverage from their intersection.
     *                 return SimplifyResult::kAOnly;
     *             } else if (b.contains(a)) {
     *                 // Mirror of the above case, intersection = B instead
     *                 return SimplifyResult::kBOnly;
     *             } else {
     *                 // Intersection of the two differences cannot be simplified. Note that for
     *                 // this op combination it is not possible to produce kEmpty.
     *                 return SimplifyResult::kBoth;
     *             }
     *     }
     *     SkUNREACHABLE;
     * }
     * ```
     */
    private fun simplify(a: TransformedShape, b: TransformedShape): SimplifyResult {
      TODO("Implement simplify")
    }

    /**
     * C++ original:
     * ```cpp
     * ClipStack::DrawInfluence ClipStack::SimplifyForDraw(const TransformedShape& clip,
     *                                                     const TransformedShape& draw) {
     *     // Given the asserts below, we can just recast the SimplifyResult returned from
     *     // Simplify(A=clip, B=draw):
     *     //
     *     // If the result is kEmpty, the draw is clipped out.
     *     static_assert((int) SimplifyResult::kEmpty == (int) DrawInfluence::kClipsOutDraw);
     *     // If the result is kAOnly, only the clip's shape provides coverage and the draw could be
     *     // replaced with something that just covers the clip bounds.
     *     static_assert((int) SimplifyResult::kAOnly == (int) DrawInfluence::kReplacesDraw);
     *     // If the result is kBOnly, the clip's shape doesn't impact the draw's coverage at all.
     *     static_assert((int) SimplifyResult::kBOnly == (int) DrawInfluence::kNone);
     *     // If the result is kBoth, the clip and the draw combine in a complex manner
     *     static_assert((int) SimplifyResult::kBoth == (int) DrawInfluence::kComplexInteraction);
     *
     *     SimplifyResult result = Simplify(clip, draw);
     *     return static_cast<DrawInfluence>(result);
     * }
     * ```
     */
    private fun simplifyForDraw(clip: TransformedShape, draw: TransformedShape): DrawInfluence {
      TODO("Implement simplifyForDraw")
    }
  }
}
