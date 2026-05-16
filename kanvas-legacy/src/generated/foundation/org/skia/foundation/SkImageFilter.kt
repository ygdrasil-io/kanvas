package org.skia.foundation

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import kotlin.Unit
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SK_API SkImageFilter : public SkFlattenable {
 * public:
 *     enum MapDirection {
 *         kForward_MapDirection,
 *         kReverse_MapDirection,
 *     };
 *     /**
 *      * Map a device-space rect recursively forward or backward through the filter DAG.
 *      * kForward_MapDirection is used to determine which pixels of the destination canvas a source
 *      * image rect would touch after filtering. kReverse_MapDirection is used to determine which rect
 *      * of the source image would be required to fill the given rect (typically, clip bounds). Used
 *      * for clipping and temp-buffer allocations, so the result need not be exact, but should never
 *      * be smaller than the real answer. The default implementation recursively unions all input
 *      * bounds, or returns the source rect if no inputs.
 *      *
 *      * In kReverse mode, 'inputRect' is the device-space bounds of the input pixels. In kForward
 *      * mode it should always be null. If 'inputRect' is null in kReverse mode the resulting answer
 *      * may be incorrect.
 *      */
 *     SkIRect filterBounds(const SkIRect& src, const SkMatrix& ctm,
 *                          MapDirection, const SkIRect* inputRect = nullptr) const;
 *
 *     /**
 *      *  Returns whether this image filter is a color filter and puts the color filter into the
 *      *  "filterPtr" parameter if it can. Does nothing otherwise.
 *      *  If this returns false, then the filterPtr is unchanged.
 *      *  If this returns true, then if filterPtr is not null, it must be set to a ref'd colorfitler
 *      *  (i.e. it may not be set to NULL).
 *      */
 *     bool isColorFilterNode(SkColorFilter** filterPtr) const;
 *
 *     // DEPRECATED : use isColorFilterNode() instead
 *     bool asColorFilter(SkColorFilter** filterPtr) const {
 *         return this->isColorFilterNode(filterPtr);
 *     }
 *
 *     /**
 *      *  Returns true (and optionally returns a ref'd filter) if this imagefilter can be completely
 *      *  replaced by the returned colorfilter. i.e. the two effects will affect drawing in the same
 *      *  way.
 *      */
 *     bool asAColorFilter(SkColorFilter** filterPtr) const;
 *
 *     /**
 *      *  Returns the number of inputs this filter will accept (some inputs can be NULL).
 *      */
 *     int countInputs() const;
 *
 *     /**
 *      *  Returns the input filter at a given index, or NULL if no input is connected.  The indices
 *      *  used are filter-specific.
 *      */
 *     const SkImageFilter* getInput(int i) const;
 *
 *     // Default impl returns union of all input bounds.
 *     virtual SkRect computeFastBounds(const SkRect& bounds) const;
 *
 *     // Can this filter DAG compute the resulting bounds of an object-space rectangle?
 *     bool canComputeFastBounds() const;
 *
 *     /**
 *      *  If this filter can be represented by another filter + a localMatrix, return that filter,
 *      *  else return null.
 *      */
 *     sk_sp<SkImageFilter> makeWithLocalMatrix(const SkMatrix& matrix) const;
 *
 *     static sk_sp<SkImageFilter> Deserialize(const void* data, size_t size,
 *                                           const SkDeserialProcs* procs = nullptr) {
 *         return sk_sp<SkImageFilter>(static_cast<SkImageFilter*>(
 *                 SkFlattenable::Deserialize(kSkImageFilter_Type, data, size, procs).release()));
 *     }
 *
 * protected:
 *
 *     sk_sp<SkImageFilter> refMe() const {
 *         return sk_ref_sp(const_cast<SkImageFilter*>(this));
 *     }
 *
 * private:
 *     friend class SkImageFilter_Base;
 *
 *     using INHERITED = SkFlattenable;
 * }
 * ```
 */
public open class SkImageFilter : SkFlattenable() {
  /**
   * C++ original:
   * ```cpp
   * SkIRect SkImageFilter::filterBounds(const SkIRect& src, const SkMatrix& ctm,
   *                                     MapDirection direction, const SkIRect* inputRect) const {
   *     // The old filterBounds() function uses SkIRects that are defined in layer space so, while
   *     // we still are supporting it, bypass SkIF_B's new public filter bounds functions and go right
   *     // to the internal layer-space calculations.
   *     skif::Mapping mapping{SkM44(ctm)};
   *     if (kReverse_MapDirection == direction) {
   *         skif::LayerSpace<SkIRect> targetOutput(src);
   *         std::optional<skif::LayerSpace<SkIRect>> content;
   *         if (inputRect) {
   *             content = skif::LayerSpace<SkIRect>(*inputRect);
   *         }
   *         return SkIRect(as_IFB(this)->onGetInputLayerBounds(mapping, targetOutput, content));
   *     } else {
   *         SkASSERT(!inputRect);
   *         skif::LayerSpace<SkIRect> content(src);
   *         auto output = as_IFB(this)->onGetOutputLayerBounds(mapping, content);
   *         return output ? SkIRect(*output) : SkRectPriv::MakeILarge();
   *     }
   * }
   * ```
   */
  public fun filterBounds(
    src: SkIRect,
    ctm: SkMatrix,
    direction: MapDirection,
    inputRect: SkIRect? = TODO(),
  ): Int {
    TODO("Implement filterBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImageFilter::isColorFilterNode(SkColorFilter** filterPtr) const {
   *     return as_IFB(this)->onIsColorFilterNode(filterPtr);
   * }
   * ```
   */
  public fun isColorFilterNode(filterPtr: Int?): Boolean {
    TODO("Implement isColorFilterNode")
  }

  /**
   * C++ original:
   * ```cpp
   * bool asColorFilter(SkColorFilter** filterPtr) const {
   *         return this->isColorFilterNode(filterPtr);
   *     }
   * ```
   */
  public fun asColorFilter(filterPtr: Int?): Boolean {
    TODO("Implement asColorFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImageFilter::asAColorFilter(SkColorFilter** filterPtr) const {
   *     SkASSERT(nullptr != filterPtr);
   *     if (!this->isColorFilterNode(filterPtr)) {
   *         return false;
   *     }
   *     if (nullptr != this->getInput(0) || as_CFB(*filterPtr)->affectsTransparentBlack()) {
   *         (*filterPtr)->unref();
   *         return false;
   *     }
   *     return true;
   * }
   * ```
   */
  public fun asAColorFilter(filterPtr: Int?): Boolean {
    TODO("Implement asAColorFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * int SkImageFilter::countInputs() const { return as_IFB(this)->fInputs.count(); }
   * ```
   */
  public fun countInputs(): Int {
    TODO("Implement countInputs")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkImageFilter* SkImageFilter::getInput(int i) const {
   *     SkASSERT(i < this->countInputs());
   *     return as_IFB(this)->fInputs[i].get();
   * }
   * ```
   */
  public fun getInput(i: Int): SkImageFilter {
    TODO("Implement getInput")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkImageFilter::computeFastBounds(const SkRect& src) const {
   *     if (0 == this->countInputs()) {
   *         return src;
   *     }
   *     SkRect combinedBounds = this->getInput(0) ? this->getInput(0)->computeFastBounds(src) : src;
   *     for (int i = 1; i < this->countInputs(); i++) {
   *         const SkImageFilter* input = this->getInput(i);
   *         if (input) {
   *             combinedBounds.join(input->computeFastBounds(src));
   *         } else {
   *             combinedBounds.join(src);
   *         }
   *     }
   *     return combinedBounds;
   * }
   * ```
   */
  public open fun computeFastBounds(bounds: SkRect): Int {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImageFilter::canComputeFastBounds() const {
   *     return !as_IFB(this)->affectsTransparentBlack();
   * }
   * ```
   */
  public fun canComputeFastBounds(): Boolean {
    TODO("Implement canComputeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkImageFilter::makeWithLocalMatrix(const SkMatrix& matrix) const {
   *     return SkLocalMatrixImageFilter::Make(matrix, this->refMe());
   * }
   * ```
   */
  public fun makeWithLocalMatrix(matrix: SkMatrix): Int {
    TODO("Implement makeWithLocalMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> refMe() const {
   *         return sk_ref_sp(const_cast<SkImageFilter*>(this));
   *     }
   * ```
   */
  protected fun refMe(): Int {
    TODO("Implement refMe")
  }

  public enum class MapDirection {
    kForward_MapDirection,
    kReverse_MapDirection,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkImageFilter> Deserialize(const void* data, size_t size,
     *                                           const SkDeserialProcs* procs = nullptr) {
     *         return sk_sp<SkImageFilter>(static_cast<SkImageFilter*>(
     *                 SkFlattenable::Deserialize(kSkImageFilter_Type, data, size, procs).release()));
     *     }
     * ```
     */
    public fun deserialize(
      `data`: Unit?,
      size: ULong,
      procs: SkDeserialProcs? = TODO(),
    ): Int {
      TODO("Implement deserialize")
    }
  }
}
