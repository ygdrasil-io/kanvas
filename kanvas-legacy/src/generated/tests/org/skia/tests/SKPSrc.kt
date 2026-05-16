package org.skia.tests

import kotlin.Int
import org.skia.core.SkCanvas
import org.skia.tools.GraphiteTestContext

/**
 * C++ original:
 * ```cpp
 * class SKPSrc : public Src {
 * public:
 *     explicit SKPSrc(Path path);
 *
 *     Result draw(SkCanvas*, GraphiteTestContext*) const override;
 *     SkISize size() const override;
 *     Name name() const override;
 * private:
 *     Path fPath;
 * }
 * ```
 */
public open class SKPSrc public constructor(
  path: Path,
) : Src() {
  /**
   * C++ original:
   * ```cpp
   * Path fPath
   * ```
   */
  private var fPath: Path = TODO("Initialize fPath")

  /**
   * C++ original:
   * ```cpp
   * Result SKPSrc::draw(SkCanvas* canvas, GraphiteTestContext*) const {
   *     struct DeserializationContext {
   *         GrDirectContext*           fDirectContext = nullptr;
   * #if defined(SK_GRAPHITE)
   *         skgpu::graphite::Recorder* fRecorder = nullptr;
   * #endif
   *     } ctx {
   *         GrAsDirectContext(canvas->recordingContext()),
   * #if defined(SK_GRAPHITE)
   *         canvas->recorder()
   * #endif
   *     };
   *
   *     SkDeserialProcs procs;
   *     procs.fImageProc = [](const void* data, size_t size, void* ctx) -> sk_sp<SkImage> {
   *         sk_sp<SkData> tmpData = SkData::MakeWithoutCopy(data, size);
   *         sk_sp<SkImage> image = SkImages::DeferredFromEncodedData(std::move(tmpData));
   *         image = image->makeRasterImage(nullptr); // force decoding
   *
   * #if defined(SK_GANESH)
   *         if (image) {
   *             DeserializationContext* context = reinterpret_cast<DeserializationContext*>(ctx);
   *             if (context->fDirectContext) {
   *                 return SkImages::TextureFromImage(context->fDirectContext, image);
   *             }
   *         }
   * #endif
   *         return image;
   *     };
   *     procs.fImageCtx = &ctx;
   *
   *     // SKPs may have typefaces encoded in them (e.g. with FreeType). We can try falling back
   *     // to the Test FontMgr (possibly a native one) if we have do not have FreeType built-in.
   *     procs.fTypefaceProc = [](const void* data, size_t size, void*) -> sk_sp<SkTypeface> {
   *         SkStream** stream = reinterpret_cast<SkStream**>(const_cast<void*>(data));
   *         return SkTypeface::MakeDeserialize(*stream, ToolUtils::TestFontMgr());
   *     };
   *
   *
   *     std::unique_ptr<SkStream> stream = SkStream::MakeFromFile(fPath.c_str());
   *     if (!stream) {
   *         return Result::Fatal("Couldn't read %s.", fPath.c_str());
   *     }
   *     sk_sp<SkPicture> pic(SkPicture::MakeFromStream(stream.get(), &procs));
   *     if (!pic) {
   *         return Result::Fatal("Couldn't parse file %s.", fPath.c_str());
   *     }
   *     stream = nullptr;  // Might as well drop this when we're done with it.
   *     canvas->clipRect(SkRect::MakeWH(FLAGS_skpViewportSize, FLAGS_skpViewportSize));
   *     canvas->drawPicture(pic);
   *     return Result::Ok();
   * }
   * ```
   */
  public override fun draw(canvas: SkCanvas?, param1: GraphiteTestContext?): Result {
    TODO("Implement draw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize SKPSrc::size() const {
   *     SkRect viewport = get_cull_rect_for_skp(fPath.c_str());
   *     if (!viewport.intersect((SkRect::MakeWH(FLAGS_skpViewportSize, FLAGS_skpViewportSize)))) {
   *         return {0, 0};
   *     }
   *     return viewport.roundOut().size();
   * }
   * ```
   */
  public override fun size(): Int {
    TODO("Implement size")
  }

  /**
   * C++ original:
   * ```cpp
   * Name SKPSrc::name() const { return SkOSPath::Basename(fPath.c_str()); }
   * ```
   */
  public override fun name(): Name {
    TODO("Implement name")
  }
}

public typealias BisectSrcINHERITED = SKPSrc
