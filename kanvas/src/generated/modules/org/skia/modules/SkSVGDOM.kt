package org.skia.modules

import SkShapers.Factory
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import org.skia.foundation.SkStream
import org.skia.math.SkSize
import undefined.SkSVGIDMapper

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGDOM : public SkRefCnt {
 * public:
 *     class Builder final {
 *     public:
 *         /**
 *          * Specify a font manager for loading fonts (e.g. from the system) to render <text>
 *          * SVG nodes.
 *          * If this is not set, but a font is required as part of rendering, the text will
 *          * not be displayed.
 *          */
 *         Builder& setFontManager(sk_sp<SkFontMgr>);
 *
 *         /**
 *          * Specify a resource provider for loading images etc.
 *          */
 *         Builder& setResourceProvider(sk_sp<skresources::ResourceProvider>);
 *
 *         /**
 *          * Specify the callbacks for dealing with shaping text. See also
 *          * modules/skshaper/utils/FactoryHelpers.h
 *          */
 *         Builder& setTextShapingFactory(sk_sp<SkShapers::Factory>);
 *
 *         sk_sp<SkSVGDOM> make(SkStream&) const;
 *
 *     private:
 *         sk_sp<SkFontMgr>                             fFontMgr;
 *         sk_sp<skresources::ResourceProvider>         fResourceProvider;
 *         sk_sp<SkShapers::Factory>                    fTextShapingFactory;
 *     };
 *
 *     static sk_sp<SkSVGDOM> MakeFromStream(SkStream& str);
 *
 *     /**
 *      * Returns the root (outermost) SVG element.
 *      */
 *     SkSVGSVG* getRoot() const { return fRoot.get(); }
 *
 *     /**
 *      * Specify a "container size" for the SVG dom.
 *      *
 *      * This is used to resolve the initial viewport when the root SVG width/height are specified
 *      * in relative units.
 *      *
 *      * If the root dimensions are in absolute units, then the container size has no effect since
 *      * the initial viewport is fixed.
 *      */
 *     void setContainerSize(const SkSize&);
 *
 *     /**
 *      * DEPRECATED: use getRoot()->intrinsicSize() to query the root element intrinsic size.
 *      *
 *      * Returns the SVG dom container size.
 *      *
 *      * If the client specified a container size via setContainerSize(), then the same size is
 *      * returned.
 *      *
 *      * When unspecified by clients, this returns the intrinsic size of the root element, as defined
 *      * by its width/height attributes.  If either width or height is specified in relative units
 *      * (e.g. "100%"), then the corresponding intrinsic size dimension is zero.
 *      */
 *     const SkSize& containerSize() const;
 *
 *     // Returns the node with the given id, or nullptr if not found.
 *     sk_sp<SkSVGNode>* findNodeById(const char* id);
 *
 *     void render(SkCanvas*) const;
 *
 *     /** Render the node with the given id as if it were the only child of the root. */
 *     void renderNode(SkCanvas*, SkSVGPresentationContext&, const char* id) const;
 *
 * private:
 *     SkSVGDOM(sk_sp<SkSVGSVG>,
 *              sk_sp<SkFontMgr>,
 *              sk_sp<skresources::ResourceProvider>,
 *              SkSVGIDMapper&&,
 *              sk_sp<SkShapers::Factory>);
 *
 *     const sk_sp<SkSVGSVG>                       fRoot;
 *     const sk_sp<SkFontMgr>                      fFontMgr;
 *     const sk_sp<SkShapers::Factory>             fTextShapingFactory;
 *     const sk_sp<skresources::ResourceProvider>  fResourceProvider;
 *     const SkSVGIDMapper                         fIDMapper;
 *     SkSize                                      fContainerSize;
 * }
 * ```
 */
public open class SkSVGDOM public constructor(
  root: SkSp<SkSVGSVG>,
  fmgr: SkSp<SkFontMgr>,
  rp: SkSp<ResourceProvider>,
  mapper: SkSVGIDMapper,
  fact: SkSp<Factory>,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * const sk_sp<SkSVGSVG>                       fRoot
   * ```
   */
  private val fRoot: Int = TODO("Initialize fRoot")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<SkFontMgr>                      fFontMgr
   * ```
   */
  private val fFontMgr: Int = TODO("Initialize fFontMgr")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<SkShapers::Factory>             fTextShapingFactory
   * ```
   */
  private val fTextShapingFactory: Int = TODO("Initialize fTextShapingFactory")

  /**
   * C++ original:
   * ```cpp
   * const sk_sp<skresources::ResourceProvider>  fResourceProvider
   * ```
   */
  private val fResourceProvider: Int = TODO("Initialize fResourceProvider")

  /**
   * C++ original:
   * ```cpp
   * const SkSVGIDMapper                         fIDMapper
   * ```
   */
  private val fIDMapper: Int = TODO("Initialize fIDMapper")

  /**
   * C++ original:
   * ```cpp
   * SkSize                                      fContainerSize
   * ```
   */
  private var fContainerSize: Int = TODO("Initialize fContainerSize")

  /**
   * C++ original:
   * ```cpp
   * SkSVGSVG* getRoot() const { return fRoot.get(); }
   * ```
   */
  private fun getRoot(): Int {
    TODO("Implement getRoot")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGDOM::setContainerSize(const SkSize& containerSize) {
   *     // TODO: inval
   *     fContainerSize = containerSize;
   * }
   * ```
   */
  private fun setContainerSize(containerSize: SkSize) {
    TODO("Implement setContainerSize")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkSize& SkSVGDOM::containerSize() const {
   *     return fContainerSize;
   * }
   * ```
   */
  private fun containerSize(): Int {
    TODO("Implement containerSize")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSVGNode>* SkSVGDOM::findNodeById(const char* id) {
   *     SkString idStr(id);
   *     return this->fIDMapper.find(idStr);
   * }
   * ```
   */
  private fun findNodeById(id: String?): Int {
    TODO("Implement findNodeById")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGDOM::render(SkCanvas* canvas) const {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *     if (fRoot) {
   *         SkSVGLengthContext       lctx(fContainerSize);
   *         SkSVGPresentationContext pctx;
   *         fRoot->render(SkSVGRenderContext(canvas,
   *                                          fFontMgr,
   *                                          fResourceProvider,
   *                                          fIDMapper,
   *                                          lctx,
   *                                          pctx,
   *                                          {nullptr, nullptr},
   *                                          fTextShapingFactory));
   *     }
   * }
   * ```
   */
  private fun render(canvas: SkCanvas?) {
    TODO("Implement render")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGDOM::renderNode(SkCanvas* canvas, SkSVGPresentationContext& pctx, const char* id) const {
   *     TRACE_EVENT0("skia", TRACE_FUNC);
   *
   *     if (fRoot) {
   *         SkSVGLengthContext lctx(fContainerSize);
   *         fRoot->renderNode(SkSVGRenderContext(canvas,
   *                                              fFontMgr,
   *                                              fResourceProvider,
   *                                              fIDMapper,
   *                                              lctx,
   *                                              pctx,
   *                                              {nullptr, nullptr},
   *                                              fTextShapingFactory),
   *                           SkSVGIRI(SkSVGIRI::Type::kLocal, SkSVGStringType(id)));
   *     }
   * }
   * ```
   */
  private fun renderNode(
    canvas: SkCanvas?,
    pctx: SkSVGPresentationContext,
    id: String?,
  ) {
    TODO("Implement renderNode")
  }

  public class Builder {
    private var fFontMgr: Int = TODO("Initialize fFontMgr")

    private var fResourceProvider: Int = TODO("Initialize fResourceProvider")

    private var fTextShapingFactory: Int = TODO("Initialize fTextShapingFactory")

    public fun setFontManager(fmgr: SkSp<SkFontMgr>): org.skia.tests.Builder {
      TODO("Implement setFontManager")
    }

    public fun setResourceProvider(rp: SkSp<ResourceProvider>): org.skia.tests.Builder {
      TODO("Implement setResourceProvider")
    }

    public fun setTextShapingFactory(f: SkSp<Factory>): org.skia.tests.Builder {
      TODO("Implement setTextShapingFactory")
    }

    public fun make(str: SkStream): Int {
      TODO("Implement make")
    }
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkSVGDOM> SkSVGDOM::MakeFromStream(SkStream& str) { return Builder().make(str); }
     * ```
     */
    private fun makeFromStream(str: SkStream): Int {
      TODO("Implement makeFromStream")
    }
  }
}
