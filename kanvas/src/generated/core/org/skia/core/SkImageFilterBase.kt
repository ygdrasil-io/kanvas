package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.UInt
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkIPoint
import org.skia.math.SkIRect
import org.skia.math.SkRect
import org.skia.memory.AutoSTArray2

/**
 * C++ original:
 * ```cpp
 * class SkImageFilter_Base : public SkImageFilter {
 * public:
 *     /**
 *      *  Request a new filtered image to be created from the src image. The returned skif::Image
 *      *  provides both the pixel data and the origin point that it should be drawn at, relative to
 *      *  the layer space defined by the provided context.
 *      *
 *      *  If the result image cannot be created, or the result would be transparent black, returns
 *      *  a skif::Image that has a null special image, in which its origin should be ignored.
 *      *
 *      *  TODO: Right now the imagefilters sometimes return empty result bitmaps/
 *      *        specialimages. That doesn't seem quite right.
 *      */
 *     skif::FilterResult filterImage(const skif::Context& context) const;
 *
 *     /**
 *      * Create a filtered version of the 'src' image using this filter. This is basically a wrapper
 *      * around filterImage that prepares the skif::Context to filter the 'src' image directly,
 *      * for implementing the SkImages::MakeWithFilter API calls.
 *      */
 *     sk_sp<SkImage> makeImageWithFilter(sk_sp<skif::Backend> backend,
 *                                        sk_sp<SkImage> src,
 *                                        const SkIRect& subset,
 *                                        const SkIRect& clipBounds,
 *                                        SkIRect* outSubset,
 *                                        SkIPoint* offset) const;
 *
 *     /**
 *      *  Calculate the smallest-possible required layer bounds that would provide sufficient
 *      *  information to correctly compute the image filter for every pixel in the desired output
 *      *  bounds. The 'desiredOutput' is intended to represent either the root render target bounds,
 *      *  or the device-space bounds of the current clip. If the bounds of the content that will be
 *      *  drawn into the layer is known, 'knownContentBounds' should be provided, since it can be
 *      *  used to restrict the size of the layer if the image filter DAG does not affect transparent
 *      *  black.
 *      *
 *      *  The returned rect is in the layer space defined by 'mapping', so it directly represents
 *      *  the size and location of the SkDevice created to rasterize the content prior to invoking the
 *      *  image filter (assuming its CTM and basis matrix are configured to match 'mapping').
 *      *
 *      *  While this operation transforms an device-space output bounds to a layer-space input bounds,
 *      *  it is not necessarily the inverse of getOutputBounds(). For instance, a blur needs to have
 *      *  an outset margin when reading pixels at the edge (to satisfy its kernel), thus it expands
 *      *  its required input rect to include every pixel that contributes to the desired output rect.
 *
 *      *  @param mapping       The coordinate space mapping that defines both the transformation
 *      *                       between local and layer, and layer to root device space, that will be
 *      *                       used when the filter is later invoked.
 *      *  @param desiredOutput The desired output boundary that needs to be covered by the filter's
 *      *                       output (assuming that the filter is then invoked with a suitable input)
 *      *  @param knownContentBounds
 *      *                       Optional, the known layer-space bounds of the non-transparent content
 *      *                       that would be rasterized in the source input image. Assumes unbounded
 *      *                       content when not provided.
 *      *
 *      * @return The layer-space bounding box to use for an SkDevice when drawing the source image.
 *      */
 *     skif::LayerSpace<SkIRect> getInputBounds(
 *             const skif::Mapping& mapping,
 *             const skif::DeviceSpace<SkIRect>& desiredOutput,
 *             std::optional<skif::ParameterSpace<SkRect>> knownContentBounds) const;
 *
 *     /**
 *      *  Calculate the device-space bounds of the output of this filter DAG, if it were to process
 *      *  an image layer covering the 'contentBounds'. The 'mapping' defines how the content will be
 *      *  transformed to layer space when it is drawn, and how the output filter image is then
 *      *  transformed to the final device space (i.e. it specifies the mapping between the root device
 *      *  space and the parameter space of the initially provided content).
 *      *
 *      *  While this operation transforms a parameter-space input bounds to an device-space output
 *      *  bounds, it is not necessarily the inverse of getInputBounds(). For instance, a blur needs to
 *      *  have an outset margin when reading pixels at the edge (to satisfy its kernel), so it will
 *      *  generate a result larger than its input (so that the blur is visible) and, thus, expands its
 *      *  output to include every pixel that it will touch.
 *      *
 *      *  If the returned optional does not have a value, the caller should interpret this to mean
 *      *  that the output of the image filter will fill the entirety of whatever clipped device it's
 *      *  drawn into.
 *      *
 *      *  @param mapping       The coordinate space mapping that defines both the transformation
 *      *                       between local and layer, and layer to root device space, that will be
 *      *                       used when the filter is later invoked.
 *      *  @param contentBounds The local-space bounds of the non-transparent content that would be
 *      *                       drawn into the source image prior to filtering with this DAG,  i.e.
 *      *                       the same as 'knownContentBounds' in getInputBounds().
 *      *
 *      *  @return The root device-space bounding box of the filtered image, were it applied to
 *      *          content contained by 'contentBounds' and then drawn with 'mapping' to the root
 *      *          device (w/o any additional clipping).
 *      */
 *     std::optional<skif::DeviceSpace<SkIRect>> getOutputBounds(
 *             const skif::Mapping& mapping,
 *             const skif::ParameterSpace<SkRect>& contentBounds) const;
 *
 *     // Returns true if this image filter graph transforms a source transparent black pixel to a
 *     // color other than transparent black.
 *     bool affectsTransparentBlack() const;
 *
 *     // Returns true if this image filter graph references the Context's source image.
 *     bool usesSource() const { return fUsesSrcInput; }
 *
 *     /**
 *      *  This call returns the maximum "kind" of CTM for a filter and all of its (non-null) inputs.
 *      */
 *     using MatrixCapability = skif::MatrixCapability;
 *     MatrixCapability getCTMCapability() const;
 *
 *     uint32_t uniqueID() const { return fUniqueID; }
 *
 *     static SkFlattenable::Type GetFlattenableType() {
 *         return kSkImageFilter_Type;
 *     }
 *
 *     SkFlattenable::Type getFlattenableType() const override {
 *         return kSkImageFilter_Type;
 *     }
 *
 *     // TODO: CreateProcs for now-removed image filter subclasses need to hook into
 *     // SK_IMAGEFILTER_UNFLATTEN_COMMON, so this temporarily exposes it for the case where there's a
 *     // single input filter, and can be removed when the legacy CreateProcs are deleted.
 *     static std::pair<sk_sp<SkImageFilter>, std::optional<SkRect>>
 *     Unflatten(SkReadBuffer& buffer);
 *
 * protected:
 *     class Common {
 *     public:
 *         /**
 *          *  Attempt to unflatten the expected number of input filters.
 *          *  If any number of input filters is valid, pass -1.
 *          *  If this fails (i.e. corrupt buffer or contents) then return false and common will
 *          *  be left uninitialized.
 *          *  If this returns true, then inputCount() is the number of found input filters, each
 *          *  of which may be NULL or a valid imagefilter.
 *          */
 *         bool unflatten(SkReadBuffer&, int expectedInputs);
 *
 *         std::optional<SkRect> cropRect() const { return fCropRect; }
 *
 *         int inputCount() const { return fInputs.size(); }
 *         sk_sp<SkImageFilter>* inputs() { return fInputs.begin(); }
 *
 *         sk_sp<SkImageFilter> getInput(int index) { return fInputs[index]; }
 *
 *     private:
 *         // Old SKPs (version less than kRemoveDeprecatedCropRect may have this set).
 *         std::optional<SkRect> fCropRect;
 *
 *         // most filters accept at most 2 input-filters
 *         skia_private::STArray<2, sk_sp<SkImageFilter>, true> fInputs;
 *     };
 *
 *     SkImageFilter_Base(sk_sp<SkImageFilter> const* inputs, int inputCount,
 *                        std::optional<bool> usesSrc = {});
 *
 *     ~SkImageFilter_Base() override;
 *
 *     void flatten(SkWriteBuffer&) const override;
 *
 *     // Helper function to calculate the required input/output of a specific child filter,
 *     // automatically handling if the child filter is null.
 *     skif::LayerSpace<SkIRect> getChildInputLayerBounds(
 *             int index,
 *             const skif::Mapping& mapping,
 *             const skif::LayerSpace<SkIRect>& desiredOutput,
 *             std::optional<skif::LayerSpace<SkIRect>> contentBounds) const;
 *     std::optional<skif::LayerSpace<SkIRect>> getChildOutputLayerBounds(
 *             int index,
 *             const skif::Mapping& mapping,
 *             std::optional<skif::LayerSpace<SkIRect>> contentBounds) const;
 *
 *     // Helper function for recursing through the filter DAG. It automatically evaluates the input
 *     // image filter at 'index' using the given context. If the input image filter is null, it
 *     // returns the context's dynamic source image.
 *     //
 *     // When an image filter requires a different output than what is requested in it's own Context
 *     // passed to onFilterImage(), it should explicitly pass in an updated Context via
 *     // `withNewDesiredOutput`.
 *     skif::FilterResult getChildOutput(int index, const skif::Context& ctx) const;
 *
 * private:
 *     friend class SkImageFilter;
 *     // For PurgeCache()
 *     friend class SkGraphics;
 *
 *     static void PurgeCache();
 *
 *     // Configuration points for the filter implementation, marked private since they should not
 *     // need to be invoked by the subclasses. These refer to the node's specific behavior and are
 *     // not responsible for aggregating the behavior of the entire filter DAG.
 *
 *     /**
 *      *  Return true (and returns a ref'd colorfilter) if this node in the DAG is just a colorfilter
 *      *  w/o cropping constraints.
 *      */
 *     virtual bool onIsColorFilterNode(SkColorFilter** /*filterPtr*/) const { return false; }
 *
 *     /**
 *      *  Return the most complex matrix type this filter can support (mapping from its parameter
 *      *  space to a layer space). If this returns anything less than kComplex, the filter only needs
 *      *  to worry about mapping from parameter to layer using a matrix that is constrained in that
 *      *  way (eg, scale+translate).
 *      */
 *     virtual MatrixCapability onGetCTMCapability() const {
 *         return MatrixCapability::kScaleTranslate;
 *     }
 *
 *     /**
 *      *  Return true if this filter would transform transparent black pixels to a color other than
 *      *  transparent black. When false, optimizations can be taken to discard regions known to be
 *      *  transparent black and thus process fewer pixels.
 *      */
 *     virtual bool onAffectsTransparentBlack() const { return false; }
 *
 *     /**
 *      * Return true if `affectsTransparentBlack()` should only be based on
 *      * `onAffectsTransparentBlack()` and ignore the transparency behavior of child input filters.
 *      */
 *     virtual bool ignoreInputsAffectsTransparentBlack() const { return false; }
 *
 *     /**
 *      *  This is the virtual which should be overridden by the derived class to perform image
 *      *  filtering. Subclasses are responsible for recursing to their input filters, although the
 *      *  filterInput() function is provided to handle all necessary details of this.
 *      *
 *      *  If the image cannot be created (either because of an error or if the result would be empty
 *      *  because it was clipped out), this should return a filtered Image with a null SkSpecialImage.
 *      *  In these situations, callers that do not affect transparent black can end early, since the
 *      *  "transparent" implicit image would be unchanged. Callers that affect transparent black need
 *      *  to safely handle these null and empty images and return an image filling the context's clip
 *      *  bounds as if its input filtered image were transparent black.
 *      */
 *     virtual skif::FilterResult onFilterImage(const skif::Context& context) const = 0;
 *
 *     /**
 *      *  Calculates the necessary input layer size in order for the final output of the filter to
 *      *  cover the desired output bounds. The provided 'desiredOutput' represents the requested
 *      *  input bounds for this node's parent filter node, i.e. this function answers "what does this
 *      *  node require for input in order to satisfy (as its own output), the input needs of its
 *      *  parent?".
 *      *
 *      *  'contentBounds' represents the bounds of the non-transparent content that will form the
 *      *  source image when the filter graph is invoked. If it's not instantiated, implementations
 *      *  should treat the content as extending infinitely. However, since the output is known and
 *      *  bounded, implementations should still be able to determine a finite input bounds under these
 *      *  circumstances.
 *      *
 *      *  Unlike the public getInputBounds(), all internal bounds calculations are done in the shared
 *      *  layer space defined by 'mapping'.
 *      */
 *     virtual skif::LayerSpace<SkIRect> onGetInputLayerBounds(
 *             const skif::Mapping& mapping,
 *             const skif::LayerSpace<SkIRect>& desiredOutput,
 *             std::optional<skif::LayerSpace<SkIRect>> contentBounds) const = 0;
 *
 *     /**
 *      *  Calculates the output bounds that this filter node would touch when processing an input
 *      *  sized to 'contentBounds'. This function is responsible for recursing to its child image
 *      *  filters and accounting for what they output. It is up to the filter to determine how to
 *      *  aggregate the outputs of its children.
 *      *
 *      *  'contentBounds' represents the bounds of the non-transparent content that will form the
 *      *  source image when the filter graph is invoked. If it's not instantiated, implementations
 *      *  should treat the content as extending infinitely. However, since the output is known and
 *      *  bounded, implementations should still be able to determine a finite input bounds under these
 *      *  circumstances.
 *      *
 *      *  If the non-transparent output extends infinitely, subclasses should return an uninstantiated
 *      *  optional. Implementations must also be able to handle when their children return such
 *      *  unbounded "outputs" and react accordingly.
 *      *
 *      *  Unlike the public getOutputBounds(), all internal bounds calculations are done in the
 *      *  shared layer space defined by 'mapping'.
 *      */
 *     // TODO (michaelludwig) - When layerMatrix = I, this function could be used to implement
 *     // onComputeFastBounds() instead of making filters implement the essentially the same calcs x2
 *     virtual std::optional<skif::LayerSpace<SkIRect>> onGetOutputLayerBounds(
 *             const skif::Mapping& mapping,
 *             std::optional<skif::LayerSpace<SkIRect>> contentBounds) const = 0;
 *
 *     skia_private::AutoSTArray<2, sk_sp<SkImageFilter>> fInputs;
 *
 *     bool fUsesSrcInput;
 *     uint32_t fUniqueID; // Globally unique
 *
 *     using INHERITED = SkImageFilter;
 * }
 * ```
 */
public abstract class SkImageFilterBase public constructor(
  inputs: SkSp<SkImageFilter>?,
  inputCount: Int,
  usesSrc: Int,
) : SkImageFilter() {
  /**
   * C++ original:
   * ```cpp
   * skia_private::AutoSTArray<2, sk_sp<SkImageFilter>> fInputs
   * ```
   */
  private var fInputs: AutoSTArray2<SkSp<SkImageFilter>> = TODO("Initialize fInputs")

  /**
   * C++ original:
   * ```cpp
   * bool fUsesSrcInput
   * ```
   */
  private var fUsesSrcInput: Boolean = TODO("Initialize fUsesSrcInput")

  /**
   * C++ original:
   * ```cpp
   * uint32_t fUniqueID
   * ```
   */
  private var fUniqueID: UInt = TODO("Initialize fUniqueID")

  /**
   * C++ original:
   * ```cpp
   * skif::FilterResult SkImageFilter_Base::filterImage(const skif::Context& context) const {
   *     context.markVisitedImageFilter();
   *
   *     skif::FilterResult result;
   *     if (context.desiredOutput().isEmpty() || !context.mapping().layerMatrix().isFinite()) {
   *         return result;
   *     }
   *
   *     // Some image filters that operate on the source image still affect transparent black, so if
   *     // there is clipping, we may have optimized away the source image as an empty input, but still
   *     // need to run the filter on it. This means `fUsesSrcInput` is not equivalent to the source
   *     // being non-null.
   *     const bool srcInKey = fUsesSrcInput && context.source();
   *     uint32_t srcGenID = srcInKey ? context.source().image()->uniqueID() : SK_InvalidUniqueID;
   *     const SkIRect srcSubset = srcInKey ? context.source().image()->subset() : SkIRect::MakeWH(0, 0);
   *
   *     SkImageFilterCacheKey key(fUniqueID,
   *                               context.mapping().layerMatrix().asM33(),
   *                               SkIRect(context.desiredOutput()),
   *                               srcGenID, srcSubset);
   *     if (context.backend()->cache() && context.backend()->cache()->get(key, &result)) {
   *         context.markCacheHit();
   *         return result;
   *     }
   *
   *     result = this->onFilterImage(context);
   *
   *     if (context.backend()->cache()) {
   *         context.backend()->cache()->set(key, this, result);
   *     }
   *
   *     return result;
   * }
   * ```
   */
  public fun filterImage(context: Context): FilterResult {
    TODO("Implement filterImage")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> SkImageFilter_Base::makeImageWithFilter(sk_sp<skif::Backend> backend,
   *                                                        sk_sp<SkImage> src,
   *                                                        const SkIRect& subset,
   *                                                        const SkIRect& clipBounds,
   *                                                        SkIRect* outSubset,
   *                                                        SkIPoint* offset) const {
   *     if (!outSubset || !offset || !src->bounds().contains(subset)) {
   *         return nullptr;
   *     }
   *
   *     auto srcSpecialImage = backend->makeImage(subset, src);
   *     if (!srcSpecialImage) {
   *         return nullptr;
   *     }
   *
   *     skif::Stats stats;
   *     const skif::Context context{std::move(backend),
   *                                 skif::Mapping(SkM44()),
   *                                 skif::LayerSpace<SkIRect>(clipBounds),
   *                                 skif::FilterResult(std::move(srcSpecialImage),
   *                                                    skif::LayerSpace<SkIPoint>(subset.topLeft())),
   *                                 src->imageInfo().colorSpace(),
   *                                 &stats};
   *
   *     sk_sp<SkSpecialImage> result = this->filterImage(context).imageAndOffset(context, offset);
   *     stats.reportStats();
   *
   *     if (!result) {
   *         return nullptr;
   *     }
   *
   *     SkASSERT(clipBounds.contains(SkIRect::MakeXYWH(offset->fX, offset->fY,
   *                                                    result->width(), result->height())));
   *     *outSubset = result->subset();
   *     return result->asImage();
   * }
   * ```
   */
  public fun makeImageWithFilter(
    backend: SkSp<Backend>,
    src: SkSp<SkImage>,
    subset: SkIRect,
    clipBounds: SkIRect,
    outSubset: SkIRect?,
    offset: SkIPoint?,
  ): SkSp<SkImage> {
    TODO("Implement makeImageWithFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> SkImageFilter_Base::getInputBounds(
   *         const skif::Mapping& mapping,
   *         const skif::DeviceSpace<SkIRect>& desiredOutput,
   *         std::optional<skif::ParameterSpace<SkRect>> knownContentBounds) const {
   *     // Map both the device-space desired coverage area and the known content bounds to layer space
   *     skif::LayerSpace<SkIRect> desiredBounds = mapping.deviceToLayer(desiredOutput);
   *
   *     // If we have no known content bounds, leave 'contentBounds' uninstantiated to represent
   *     // infinite possible content.
   *     std::optional<skif::LayerSpace<SkIRect>> contentBounds;
   *     if (knownContentBounds) {
   *         contentBounds = mapping.paramToLayer(*knownContentBounds).roundOut();
   *     }
   *
   *     // Process the layer-space desired output with the filter DAG to determine required input
   *     return this->onGetInputLayerBounds(mapping, desiredBounds, contentBounds);
   * }
   * ```
   */
  public fun getInputBounds(
    mapping: Mapping,
    desiredOutput: DeviceSpace<SkIRect>,
    knownContentBounds: ParameterSpace<SkRect>?,
  ): LayerSpace<SkIRect> {
    TODO("Implement getInputBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<skif::DeviceSpace<SkIRect>> SkImageFilter_Base::getOutputBounds(
   *         const skif::Mapping& mapping,
   *         const skif::ParameterSpace<SkRect>& contentBounds) const {
   *     // Map the input content into the layer space where filtering will occur
   *     skif::LayerSpace<SkRect> layerContent = mapping.paramToLayer(contentBounds);
   *     // Determine the filter DAGs output bounds in layer space
   *     std::optional<skif::LayerSpace<SkIRect>> filterOutput =
   *             this->onGetOutputLayerBounds(mapping, layerContent.roundOut());
   *     if (filterOutput) {
   *         // Map all the way to device space
   *         return mapping.layerToDevice(*filterOutput);
   *     } else {
   *         // Infinite layer output is infinite device-space output too
   *         return {};
   *     }
   * }
   * ```
   */
  public fun getOutputBounds(mapping: Mapping, contentBounds: ParameterSpace<SkRect>): Int {
    TODO("Implement getOutputBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkImageFilter_Base::affectsTransparentBlack() const {
   *     if (this->onAffectsTransparentBlack()) {
   *         return true;
   *     } else if (this->ignoreInputsAffectsTransparentBlack()) {
   *         // TODO(skbug.com/40045513): Automatically infer this from output bounds being finite
   *         return false;
   *     }
   *     for (int i = 0; i < this->countInputs(); i++) {
   *         const SkImageFilter* input = this->getInput(i);
   *         if (input && as_IFB(input)->affectsTransparentBlack()) {
   *             return true;
   *         }
   *     }
   *     return false;
   * }
   * ```
   */
  public fun affectsTransparentBlack(): Boolean {
    TODO("Implement affectsTransparentBlack")
  }

  /**
   * C++ original:
   * ```cpp
   * bool usesSource() const { return fUsesSrcInput; }
   * ```
   */
  public fun usesSource(): Boolean {
    TODO("Implement usesSource")
  }

  /**
   * C++ original:
   * ```cpp
   * SkImageFilter_Base::MatrixCapability SkImageFilter_Base::getCTMCapability() const {
   *     MatrixCapability result = this->onGetCTMCapability();
   *     const int count = this->countInputs();
   *     for (int i = 0; i < count; ++i) {
   *         if (const SkImageFilter_Base* input = as_IFB(this->getInput(i))) {
   *             result = std::min(result, input->getCTMCapability());
   *         }
   *     }
   *     return result;
   * }
   * ```
   */
  public fun getCTMCapability(): SkImageFilterBaseMatrixCapability {
    TODO("Implement getCTMCapability")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t uniqueID() const { return fUniqueID; }
   * ```
   */
  public fun uniqueID(): UInt {
    TODO("Implement uniqueID")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFlattenable::Type getFlattenableType() const override {
   *         return kSkImageFilter_Type;
   *     }
   * ```
   */
  public override fun getFlattenableType(): SkFlattenable.Type {
    TODO("Implement getFlattenableType")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkImageFilter_Base::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeInt(fInputs.count());
   *     for (int i = 0; i < fInputs.count(); i++) {
   *         const SkImageFilter* input = this->getInput(i);
   *         buffer.writeBool(input != nullptr);
   *         if (input != nullptr) {
   *             buffer.writeFlattenable(input);
   *         }
   *     }
   * }
   * ```
   */
  public override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> SkImageFilter_Base::getChildInputLayerBounds(
   *         int index,
   *         const skif::Mapping& mapping,
   *         const skif::LayerSpace<SkIRect>& desiredOutput,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     // The required input for childFilter filter, or 'contentBounds' intersected with
   *     // 'desiredOutput' if the filter is null and the source image is used (i.e. the identity filter)
   *     const SkImageFilter* childFilter = this->getInput(index);
   *     if (childFilter) {
   *         return as_IFB(childFilter)->onGetInputLayerBounds(mapping, desiredOutput, contentBounds);
   *     } else {
   *         // NOTE: We don't calculate the intersection between content and root desired output because
   *         // the desired output can expand or contract as it propagates through the filter graph to
   *         // the leaves that would actually sample from the source content.
   *         skif::LayerSpace<SkIRect> visibleContent = desiredOutput;
   *         if (contentBounds && !visibleContent.intersect(*contentBounds)) {
   *             return skif::LayerSpace<SkIRect>::Empty();
   *         } else {
   *             // This will be equal to 'desiredOutput' if the contentBounds are unknown.
   *             return visibleContent;
   *         }
   *     }
   * }
   * ```
   */
  private fun getChildInputLayerBounds(
    index: Int,
    mapping: Mapping,
    desiredOutput: LayerSpace<SkIRect>,
    contentBounds: LayerSpace<SkIRect>?,
  ): LayerSpace<SkIRect> {
    TODO("Implement getChildInputLayerBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<skif::LayerSpace<SkIRect>> SkImageFilter_Base::getChildOutputLayerBounds(
   *         int index,
   *         const skif::Mapping& mapping,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     // The output for just childFilter filter, or 'contentBounds' if the filter is null and
   *     // the source image is used (i.e. the identity filter applied to the source).
   *     const SkImageFilter* childFilter = this->getInput(index);
   *     return childFilter ? as_IFB(childFilter)->onGetOutputLayerBounds(mapping, contentBounds)
   *                        : contentBounds;
   * }
   * ```
   */
  private fun getChildOutputLayerBounds(
    index: Int,
    mapping: Mapping,
    contentBounds: LayerSpace<SkIRect>?,
  ): Int {
    TODO("Implement getChildOutputLayerBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::FilterResult SkImageFilter_Base::getChildOutput(int index, const skif::Context& ctx) const {
   *     const SkImageFilter* input = this->getInput(index);
   *     return input ? as_IFB(input)->filterImage(ctx) : ctx.source();
   * }
   * ```
   */
  private fun getChildOutput(index: Int, ctx: Context): FilterResult {
    TODO("Implement getChildOutput")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onIsColorFilterNode(SkColorFilter** /*filterPtr*/) const { return false; }
   * ```
   */
  public open fun onIsColorFilterNode(param0: Int): Boolean {
    TODO("Implement onIsColorFilterNode")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual MatrixCapability onGetCTMCapability() const {
   *         return MatrixCapability::kScaleTranslate;
   *     }
   * ```
   */
  public open fun onGetCTMCapability(): SkImageFilterBaseMatrixCapability {
    TODO("Implement onGetCTMCapability")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool onAffectsTransparentBlack() const { return false; }
   * ```
   */
  public open fun onAffectsTransparentBlack(): Boolean {
    TODO("Implement onAffectsTransparentBlack")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool ignoreInputsAffectsTransparentBlack() const { return false; }
   * ```
   */
  public open fun ignoreInputsAffectsTransparentBlack(): Boolean {
    TODO("Implement ignoreInputsAffectsTransparentBlack")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual skif::FilterResult onFilterImage(const skif::Context& context) const = 0
   * ```
   */
  private abstract fun onFilterImage(context: Context): FilterResult

  /**
   * C++ original:
   * ```cpp
   * virtual skif::LayerSpace<SkIRect> onGetInputLayerBounds(
   *             const skif::Mapping& mapping,
   *             const skif::LayerSpace<SkIRect>& desiredOutput,
   *             std::optional<skif::LayerSpace<SkIRect>> contentBounds) const = 0
   * ```
   */
  private abstract fun onGetInputLayerBounds(
    mapping: Mapping,
    desiredOutput: LayerSpace<SkIRect>,
    contentBounds: LayerSpace<SkIRect>?,
  ): LayerSpace<SkIRect>

  /**
   * C++ original:
   * ```cpp
   * virtual std::optional<skif::LayerSpace<SkIRect>> onGetOutputLayerBounds(
   *             const skif::Mapping& mapping,
   *             std::optional<skif::LayerSpace<SkIRect>> contentBounds) const = 0
   * ```
   */
  private abstract fun onGetOutputLayerBounds(mapping: Mapping, contentBounds: LayerSpace<SkIRect>?): Int

  public data class Common public constructor(
    private var fCropRect: Int,
    private var fInputs: STArray2True<SkSp<SkImageFilter>>,
  ) {
    public fun unflatten(buffer: SkReadBuffer, expectedInputs: Int): Boolean {
      TODO("Implement unflatten")
    }

    public fun cropRect(): Int {
      TODO("Implement cropRect")
    }

    public fun inputCount(): Int {
      TODO("Implement inputCount")
    }

    public fun inputs(): SkSp<SkImageFilter> {
      TODO("Implement inputs")
    }

    public fun getInput(index: Int): SkSp<SkImageFilter> {
      TODO("Implement getInput")
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkFlattenable::Type GetFlattenableType() {
     *         return kSkImageFilter_Type;
     *     }
     * ```
     */
    public fun getFlattenableType(): SkFlattenable.Type {
      TODO("Implement getFlattenableType")
    }

    /**
     * C++ original:
     * ```cpp
     * void SkImageFilter_Base::PurgeCache() {
     *     auto cache = SkImageFilterCache::Get(SkImageFilterCache::CreateIfNecessary::kNo);
     *     if (cache) {
     *         cache->purge();
     *     }
     * }
     * ```
     */
    private fun purgeCache() {
      TODO("Implement purgeCache")
    }
  }
}
