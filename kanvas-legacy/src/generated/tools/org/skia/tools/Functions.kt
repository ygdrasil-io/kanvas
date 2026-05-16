package org.skia.tools

import SkCommandLineConfigArray
import kotlin.Array
import kotlin.Boolean
import kotlin.Char
import kotlin.CharArray
import kotlin.Double
import kotlin.Float
import kotlin.FloatArray
import kotlin.Function
import kotlin.Int
import kotlin.Long
import kotlin.Pair
import kotlin.String
import kotlin.UByte
import kotlin.ULong
import kotlin.Unit
import kotlin.collections.List
import org.skia.core.SkCanvas
import org.skia.core.SkClipOp
import org.skia.core.SkDrawShadowRec
import org.skia.core.SkFontScanner
import org.skia.core.SkSurface
import org.skia.core.SkTextBlobBuilder
import org.skia.core.TArray
import org.skia.effects.SkRuntimeEffect
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkBlender
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorChannel
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkData
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPMColor
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkRRect
import org.skia.foundation.SkRegion
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.foundation.SkStream
import org.skia.foundation.SkStreamAsset
import org.skia.foundation.SkSurfaceProps
import org.skia.foundation.SkTileMode
import org.skia.foundation.SkTypeface
import org.skia.foundation.SkWStream
import org.skia.gpu.GraphicsPipelineDesc
import org.skia.gpu.PrecompileBlender
import org.skia.gpu.PrecompileColorFilter
import org.skia.gpu.PrecompileContext
import org.skia.gpu.PrecompileShader
import org.skia.gpu.RecorderOptions
import org.skia.gpu.RenderPassDesc
import org.skia.gpu.UniqueKey
import org.skia.gpu.ganesh.Mipmapped
import org.skia.gpu.ganesh.SkAlphaType
import org.skia.json.SkJSONWriter
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.math.SkVector3
import org.skia.sksl.DebugTracePriv
import skia_private.TArraytrue
import undefined.A8CompareProc
import undefined.MSec
import undefined.SkColor4f
import undefined.SkSerialReturnType
import undefined.Strings

/**
 * C++ original:
 * ```cpp
 * void* SkLoadDynamicLibrary(const char* libraryName) {
 *     return dlopen(libraryName, RTLD_LAZY);
 * }
 * ```
 */
public fun skLoadDynamicLibrary(libraryName: String?) {
  TODO("Implement skLoadDynamicLibrary")
}

/**
 * C++ original:
 * ```cpp
 * void* SkGetProcedureAddress(void* library, const char* functionName) {
 *     return dlsym(library, functionName);
 * }
 * ```
 */
public fun skGetProcedureAddress(library: Unit?, functionName: String?) {
  TODO("Implement skGetProcedureAddress")
}

/**
 * C++ original:
 * ```cpp
 * bool SkFreeDynamicLibrary(void* library) {
 *     return dlclose(library) == 0;
 * }
 * ```
 */
public fun skFreeDynamicLibrary(library: Unit?): Boolean {
  TODO("Implement skFreeDynamicLibrary")
}

/**
 * C++ original:
 * ```cpp
 * static void ignore_result(const T&) {}
 * ```
 */
public fun <T> ignoreResult(param0: T) {
  TODO("Implement ignoreResult")
}

/**
 * C++ original:
 * ```cpp
 * static bool string_is_in(const char* target, const char* set[], size_t len) {
 *     for (size_t i = 0; i < len; i++) {
 *         if (0 == strcmp(target, set[i])) {
 *             return true;
 *         }
 *     }
 *     return false;
 * }
 * ```
 */
public fun stringIsIn(
  target: String?,
  `set`: Int,
  len: ULong,
): Boolean {
  TODO("Implement stringIsIn")
}

/**
 * C++ original:
 * ```cpp
 * static bool parse_bool_arg(const char* string, bool* result) {
 *     static const char* trueValues[] = {"1", "TRUE", "true"};
 *     if (string_is_in(string, trueValues, std::size(trueValues))) {
 *         *result = true;
 *         return true;
 *     }
 *     static const char* falseValues[] = {"0", "FALSE", "false"};
 *     if (string_is_in(string, falseValues, std::size(falseValues))) {
 *         *result = false;
 *         return true;
 *     }
 *     SkDebugf("Parameter \"%s\" not supported.\n", string);
 *     return false;
 * }
 * ```
 */
public fun parseBoolArg(string: String?, result: Boolean?): Boolean {
  TODO("Implement parseBoolArg")
}

/**
 * C++ original:
 * ```cpp
 * static void print_indented(const SkString& text) {
 *     size_t      length   = text.size();
 *     const char* currLine = text.c_str();
 *     const char* stop     = currLine + length;
 *     while (currLine < stop) {
 *         int lineBreak = SkStrFind(currLine, "\n");
 *         if (lineBreak < 0) {
 *             lineBreak = static_cast<int>(strlen(currLine));
 *         }
 *         if (lineBreak > LINE_LENGTH) {
 *             // No line break within line length. Will need to insert one.
 *             // Find a space before the line break.
 *             int spaceIndex = LINE_LENGTH - 1;
 *             while (spaceIndex > 0 && currLine[spaceIndex] != ' ') {
 *                 spaceIndex--;
 *             }
 *             int gap;
 *             if (0 == spaceIndex) {
 *                 // No spaces on the entire line. Go ahead and break mid word.
 *                 spaceIndex = LINE_LENGTH;
 *                 gap        = 0;
 *             } else {
 *                 // Skip the space on the next line
 *                 gap = 1;
 *             }
 *             SkDebugf("        %.*s\n", spaceIndex, currLine);
 *             currLine += spaceIndex + gap;
 *         } else {
 *             // the line break is within the limit. Break there.
 *             lineBreak++;
 *             SkDebugf("        %.*s", lineBreak, currLine);
 *             currLine += lineBreak;
 *         }
 *     }
 * }
 * ```
 */
public fun printIndented(text: String) {
  TODO("Implement printIndented")
}

/**
 * C++ original:
 * ```cpp
 * static void print_help_for_flag(const SkFlagInfo* flag) {
 *     SkDebugf("    --%s", flag->name().c_str());
 *     const SkString& shortName = flag->shortName();
 *     if (shortName.size() > 0) {
 *         SkDebugf(" or -%s", shortName.c_str());
 *     }
 *     SkDebugf(":\ttype: %s", flag->typeAsString().c_str());
 *     if (flag->defaultValue().size() > 0) {
 *         SkDebugf("\tdefault: %s", flag->defaultValue().c_str());
 *     }
 *     SkDebugf("\n");
 *     const SkString& help = flag->help();
 *     print_indented(help);
 *     SkDebugf("\n");
 * }
 * ```
 */
public fun printHelpForFlag(flag: SkFlagInfo?) {
  TODO("Implement printHelpForFlag")
}

/**
 * C++ original:
 * ```cpp
 * static void print_extended_help_for_flag(const SkFlagInfo* flag) {
 *     print_help_for_flag(flag);
 *     print_indented(flag->extendedHelp());
 *     SkDebugf("\n");
 * }
 * ```
 */
public fun printExtendedHelpForFlag(flag: SkFlagInfo?) {
  TODO("Implement printExtendedHelpForFlag")
}

/**
 * C++ original:
 * ```cpp
 * bool ShouldSkipImpl(const Strings& strings, const char* name) {
 *     int    count      = strings.size();
 *     size_t testLen    = strlen(name);
 *     bool   anyExclude = count == 0;
 *     for (int i = 0; i < strings.size(); ++i) {
 *         const char* matchName = strings[i];
 *         size_t      matchLen  = strlen(matchName);
 *         bool        matchExclude, matchStart, matchEnd;
 *         if ((matchExclude = matchName[0] == '~')) {
 *             anyExclude = true;
 *             matchName++;
 *             matchLen--;
 *         }
 *         if ((matchStart = matchName[0] == '^')) {
 *             matchName++;
 *             matchLen--;
 *         }
 *         if ((matchEnd = matchName[matchLen - 1] == '$')) {
 *             matchLen--;
 *         }
 *         if (matchStart
 *                     ? (!matchEnd || matchLen == testLen) && strncmp(name, matchName, matchLen) == 0
 *                     : matchEnd
 *                               ? matchLen <= testLen &&
 *                                         strncmp(name + testLen - matchLen, matchName, matchLen) == 0
 *                               : strstr(name, matchName) != nullptr) {
 *             return matchExclude;
 *         }
 *     }
 *     return !anyExclude;
 * }
 * ```
 */
public fun shouldSkipImpl(strings: Strings, name: String?): Boolean {
  TODO("Implement shouldSkipImpl")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkSurface> MakeBackendTextureSurface(skgpu::graphite::Recorder* recorder,
 *                                            const SkImageInfo& ii,
 *                                            skgpu::Mipmapped mipmapped,
 *                                            skgpu::Protected isProtected,
 *                                            const SkSurfaceProps* props) {
 *     if (ii.alphaType() == kUnpremul_SkAlphaType) {
 *         return nullptr;
 *     }
 *     sk_sp<ManagedGraphiteTexture> mbet = ManagedGraphiteTexture::MakeUnInit(recorder,
 *                                                                             ii,
 *                                                                             mipmapped,
 *                                                                             skgpu::Renderable::kYes,
 *                                                                             isProtected);
 *     if (!mbet) {
 *         return nullptr;
 *     }
 *     return SkSurfaces::WrapBackendTexture(recorder,
 *                                           mbet->texture(),
 *                                           ii.colorType(),
 *                                           ii.refColorSpace(),
 *                                           props,
 *                                           ManagedGraphiteTexture::ReleaseProc,
 *                                           mbet->releaseContext());
 * }
 * ```
 */
public fun makeBackendTextureSurface(
  recorder: org.skia.gpu.Recorder?,
  ii: SkImageInfo,
  mipmapped: Mipmapped,
  isProtected: org.skia.gpu.ganesh.Protected,
  props: SkSurfaceProps?,
): SkSp<SkSurface> {
  TODO("Implement makeBackendTextureSurface")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkImage> MakeBackendTextureImage(Recorder* recorder,
 *                                        const SkImageInfo& ii,
 *                                        SkColor4f color,
 *                                        skgpu::Mipmapped isMipmapped,
 *                                        Renderable isRenderable,
 *                                        Origin origin,
 *                                        Protected isProtected) {
 *     if (ii.alphaType() == kOpaque_SkAlphaType) {
 *         color = color.makeOpaque();
 *     }
 *
 *     SkBitmap bitmap;
 *     bitmap.allocPixels(ii);
 *
 *     bitmap.eraseColor(color);
 *
 *     return MakeBackendTextureImage(recorder, bitmap.pixmap(), isMipmapped, isRenderable,
 *                                    origin, isProtected);
 * }
 * ```
 */
public fun makeBackendTextureImage(
  recorder: Recorder?,
  ii: SkImageInfo,
  color: SkColor4f,
  isMipmapped: Mipmapped,
  isRenderable: Renderable,
  origin: Origin,
  isProtected: Protected,
): SkSp<SkImage> {
  TODO("Implement makeBackendTextureImage")
}

/**
 * C++ original:
 * ```cpp
 * static int num_4x4_blocks(int size) { return ((size + 3) & ~3) >> 2; }
 * ```
 */
public fun num4x4Blocks(size: Int): Int {
  TODO("Implement num4x4Blocks")
}

/**
 * C++ original:
 * ```cpp
 * void TwoColorBC1Compress(const SkPixmap& pixmap, SkColor otherColor, char* dstPixels) {
 *     BC1Block* dstBlocks = reinterpret_cast<BC1Block*>(dstPixels);
 *     SkASSERT(pixmap.colorType() == SkColorType::kRGBA_8888_SkColorType);
 *
 *     BC1Block block;
 *
 *     // black -> fColor0, otherColor -> fColor1
 *     create_BC1_block(SK_ColorBLACK, otherColor, &block);
 *
 *     int numXBlocks = num_4x4_blocks(pixmap.width());
 *     int numYBlocks = num_4x4_blocks(pixmap.height());
 *
 *     for (int y = 0; y < numYBlocks; ++y) {
 *         for (int x = 0; x < numXBlocks; ++x) {
 *             int shift = 0;
 *             int offsetX = 4 * x, offsetY = 4 * y;
 *             block.fIndices = 0;  // init all the pixels to color0 (i.e., opaque black)
 *             for (int i = 0; i < 4; ++i) {
 *                 for (int j = 0; j < 4; ++j, shift += 2) {
 *                     if (offsetX + j >= pixmap.width() || offsetY + i >= pixmap.height()) {
 *                         // This can happen for the topmost levels of a mipmap and for
 *                         // non-multiple of 4 textures
 *                         continue;
 *                     }
 *
 *                     SkColor tmp = pixmap.getColor(offsetX + j, offsetY + i);
 *                     if (tmp == SK_ColorTRANSPARENT) {
 *                         // For RGBA BC1 images color3 is set to transparent black
 *                         block.fIndices |= 3 << shift;
 *                     } else if (tmp != SK_ColorBLACK) {
 *                         block.fIndices |= 1 << shift;  // color1
 *                     }
 *                 }
 *             }
 *
 *             dstBlocks[y * numXBlocks + x] = block;
 *         }
 *     }
 * }
 * ```
 */
public fun twoColorBC1Compress(
  pixmap: SkPixmap,
  otherColor: SkColor,
  dstPixels: String?,
) {
  TODO("Implement twoColorBC1Compress")
}

/**
 * C++ original:
 * ```cpp
 * const char* skgpu::ContextTypeName(skgpu::ContextType type) {
 *     switch (type) {
 *         case skgpu::ContextType::kGL:
 *             return "OpenGL";
 *         case skgpu::ContextType::kGLES:
 *             return "OpenGLES";
 *         case skgpu::ContextType::kANGLE_D3D9_ES2:
 *             return "ANGLE D3D9 ES2";
 *         case skgpu::ContextType::kANGLE_D3D11_ES2:
 *             return "ANGLE D3D11 ES2";
 *         case skgpu::ContextType::kANGLE_D3D11_ES3:
 *             return "ANGLE D3D11 ES3";
 *         case skgpu::ContextType::kANGLE_GL_ES2:
 *             return "ANGLE GL ES2";
 *         case skgpu::ContextType::kANGLE_GL_ES3:
 *             return "ANGLE GL ES3";
 *         case skgpu::ContextType::kANGLE_Metal_ES2:
 *             return "ANGLE Metal ES2";
 *         case skgpu::ContextType::kANGLE_Metal_ES3:
 *             return "ANGLE Metal ES3";
 *         case skgpu::ContextType::kVulkan:
 *             return "Vulkan";
 *         case skgpu::ContextType::kMetal:
 *             return "Metal";
 *         case skgpu::ContextType::kDirect3D:
 *             return "Direct3D";
 *         case skgpu::ContextType::kDawn_D3D11:
 *             return "Dawn D3D11";
 *         case skgpu::ContextType::kDawn_D3D12:
 *             return "Dawn D3D12";
 *         case skgpu::ContextType::kDawn_Metal:
 *             return "Dawn Metal";
 *         case skgpu::ContextType::kDawn_Vulkan:
 *             return "Dawn Vulkan";
 *         case skgpu::ContextType::kDawn_OpenGL:
 *             return "Dawn OpenGL";
 *         case skgpu::ContextType::kDawn_OpenGLES:
 *             return "Dawn OpenGLES";
 *         case skgpu::ContextType::kMock:
 *             return "Mock";
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun contextTypeName(type: ContextType): Char {
  TODO("Implement contextTypeName")
}

/**
 * C++ original:
 * ```cpp
 * bool skgpu::IsNativeBackend(skgpu::ContextType type) {
 *     switch (type) {
 *         case ContextType::kDirect3D:
 *         case ContextType::kGL:
 *         case ContextType::kGLES:
 *         case ContextType::kMetal:
 *         case ContextType::kVulkan:
 *             return true;
 *
 *         default:
 *             // Mock doesn't use the GPU, and Dawn and ANGLE add a layer between Skia and the native
 *             // GPU backend.
 *             return false;
 *     }
 * }
 * ```
 */
public fun isNativeBackend(type: ContextType): Boolean {
  TODO("Implement isNativeBackend")
}

/**
 * C++ original:
 * ```cpp
 * bool skgpu::IsDawnBackend(skgpu::ContextType type) {
 *     switch (type) {
 *         case ContextType::kDawn_D3D11:
 *         case ContextType::kDawn_D3D12:
 *         case ContextType::kDawn_Metal:
 *         case ContextType::kDawn_OpenGL:
 *         case ContextType::kDawn_OpenGLES:
 *         case ContextType::kDawn_Vulkan:
 *             return true;
 *
 *         default:
 *             return false;
 *     }
 * }
 * ```
 */
public fun isDawnBackend(type: ContextType): Boolean {
  TODO("Implement isDawnBackend")
}

/**
 * C++ original:
 * ```cpp
 * bool skgpu::IsRenderingContext(ContextType type) {
 *     return type != ContextType::kMock;
 * }
 * ```
 */
public fun isRenderingContext(type: ContextType): Boolean {
  TODO("Implement isRenderingContext")
}

/**
 * C++ original:
 * ```cpp
 * skgpu::BackendApi skgpu::graphite::ContextTypeBackend(ContextType type) {
 *     switch (type) {
 *         case skgpu::ContextType::kGL:
 *         case skgpu::ContextType::kGLES:
 *         case skgpu::ContextType::kANGLE_D3D9_ES2:
 *         case skgpu::ContextType::kANGLE_D3D11_ES2:
 *         case skgpu::ContextType::kANGLE_D3D11_ES3:
 *         case skgpu::ContextType::kANGLE_GL_ES2:
 *         case skgpu::ContextType::kANGLE_GL_ES3:
 *         case skgpu::ContextType::kANGLE_Metal_ES2:
 *         case skgpu::ContextType::kANGLE_Metal_ES3:
 *         case skgpu::ContextType::kDirect3D:
 *             return BackendApi::kUnsupported;
 *
 *         case ContextType::kVulkan:
 *             return BackendApi::kVulkan;
 *
 *         case ContextType::kMetal:
 *             return BackendApi::kMetal;
 *
 *         case ContextType::kDawn_D3D11:
 *         case ContextType::kDawn_D3D12:
 *         case ContextType::kDawn_Metal:
 *         case ContextType::kDawn_Vulkan:
 *         case ContextType::kDawn_OpenGL:
 *         case ContextType::kDawn_OpenGLES:
 *             return BackendApi::kDawn;
 *
 *         case ContextType::kMock:
 *             return BackendApi::kMock;
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun contextTypeBackend(type: ContextType): BackendApi {
  TODO("Implement contextTypeBackend")
}

/**
 * C++ original:
 * ```cpp
 * static SkPMColor convert_yuva_to_rgba(const float mtx[20], uint8_t yuva[4]) {
 *     uint8_t y = yuva[0];
 *     uint8_t u = yuva[1];
 *     uint8_t v = yuva[2];
 *     uint8_t a = yuva[3];
 *
 *     uint8_t r = SkTPin(SkScalarRoundToInt(mtx[ 0]*y + mtx[ 1]*u + mtx[ 2]*v + mtx[ 4]*255), 0, 255);
 *     uint8_t g = SkTPin(SkScalarRoundToInt(mtx[ 5]*y + mtx[ 6]*u + mtx[ 7]*v + mtx[ 9]*255), 0, 255);
 *     uint8_t b = SkTPin(SkScalarRoundToInt(mtx[10]*y + mtx[11]*u + mtx[12]*v + mtx[14]*255), 0, 255);
 *
 *     return SkPremultiplyARGBInline(a, r, g, b);
 * }
 * ```
 */
public fun convertYuvaToRgba(mtx: FloatArray, yuva: Array<UByte>): SkPMColor {
  TODO("Implement convertYuvaToRgba")
}

/**
 * C++ original:
 * ```cpp
 * static uint8_t look_up(SkPoint normPt, const SkPixmap& pmap, SkColorChannel channel) {
 *     SkASSERT(normPt.x() > 0 && normPt.x() < 1.0f);
 *     SkASSERT(normPt.y() > 0 && normPt.y() < 1.0f);
 *     int x = SkScalarFloorToInt(normPt.x() * pmap.width());
 *     int y = SkScalarFloorToInt(normPt.y() * pmap.height());
 *
 *     auto ii = pmap.info().makeColorType(kRGBA_8888_SkColorType).makeWH(1, 1);
 *     uint32_t pixel;
 *     SkAssertResult(pmap.readPixels(ii, &pixel, sizeof(pixel), x, y));
 *     int shift = static_cast<int>(channel) * 8;
 *     return static_cast<uint8_t>((pixel >> shift) & 0xff);
 * }
 * ```
 */
public fun lookUp(
  normPt: SkPoint,
  pmap: SkPixmap,
  channel: SkColorChannel,
): UByte {
  TODO("Implement lookUp")
}

/**
 * C++ original:
 * ```cpp
 * skgpu::graphite::RecorderOptions CreateTestingRecorderOptions() {
 *     skgpu::graphite::RecorderOptions options;
 *
 *     options.fImageProvider.reset(new TestingImageProvider);
 *
 *     return options;
 * }
 * ```
 */
public fun createTestingRecorderOptions(): RecorderOptions {
  TODO("Implement createTestingRecorderOptions")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkSurface> CreateProtectedSkSurface(Recorder* recorder,
 *                                           SkISize size,
 *                                           Protected isProtected) {
 *     SkImageInfo ii = SkImageInfo::Make(size, kRGBA_8888_SkColorType, kPremul_SkAlphaType);
 *
 *     sk_sp<SkSurface> surface = sk_gpu_test::MakeBackendTextureSurface(recorder,
 *                                                                       ii,
 *                                                                       Mipmapped::kNo,
 *                                                                       isProtected,
 *                                                                       /* surfaceProps= */ nullptr);
 *     if (!surface) {
 *         SK_ABORT("Could not create %s surface.",
 *                  isProtected == Protected::kYes ? "protected" : "unprotected");
 *         return nullptr;
 *     }
 *
 *     SkCanvas* canvas = surface->getCanvas();
 *
 *     canvas->clear(SkColors::kBlue);
 *
 *     return surface;
 * }
 * ```
 */
public fun createProtectedSkSurface(
  recorder: Recorder?,
  size: SkISize,
  isProtected: Protected,
): SkSp<SkSurface> {
  TODO("Implement createProtectedSkSurface")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkImage> CreateProtectedSkImage(Recorder* recorder,
 *                                       SkISize size,
 *                                       SkColor4f color,
 *                                       Protected isProtected) {
 *     SkImageInfo ii = SkImageInfo::Make(size, kRGBA_8888_SkColorType, kPremul_SkAlphaType);
 *
 *     sk_sp<SkImage> image = sk_gpu_test::MakeBackendTextureImage(recorder,
 *                                                                 ii,
 *                                                                 color,
 *                                                                 Mipmapped::kNo,
 *                                                                 Renderable::kNo,
 *                                                                 Origin::kTopLeft,
 *                                                                 isProtected);
 *     if (!image) {
 *         SK_ABORT("Could not create %s image.",
 *                  isProtected == Protected::kYes ? "protected" : "unprotected");
 *         return nullptr;
 *     }
 *
 *     return image;
 * }
 * ```
 */
public fun createProtectedSkImage(
  recorder: Recorder?,
  size: SkISize,
  color: SkColor4f,
  isProtected: Protected,
): SkSp<SkImage> {
  TODO("Implement createProtectedSkImage")
}

/**
 * C++ original:
 * ```cpp
 * void FetchUniqueKeys(PrecompileContext* precompileContext,
 *                      std::vector<UniqueKey>* keys) {
 *     GlobalCache* globalCache = precompileContext->priv().globalCache();
 *
 *     keys->reserve(globalCache->numGraphicsPipelines());
 *     globalCache->forEachGraphicsPipeline([keys](const UniqueKey& key,
 *                                                 const GraphicsPipeline* pipeline) {
 *                                                     keys->push_back(key);
 *                                          });
 * }
 * ```
 */
public fun fetchUniqueKeys(precompileContext: PrecompileContext?, keys: List<UniqueKey>?) {
  TODO("Implement fetchUniqueKeys")
}

/**
 * C++ original:
 * ```cpp
 * void DumpDescs(PrecompileContext* precompileContext,
 *                const GraphicsPipelineDesc& pipelineDesc,
 *                const RenderPassDesc& rpd) {
 *     const RendererProvider* rendererProvider = precompileContext->priv().rendererProvider();
 *     const ShaderCodeDictionary* dict = precompileContext->priv().shaderCodeDictionary();
 *
 *     const RenderStep* rs = rendererProvider->lookup(pipelineDesc.renderStepID());
 *     SkDebugf("GraphicsPipelineDesc: %u %s\n", pipelineDesc.paintParamsID().asUInt(), rs->name());
 *
 *     dict->dump(precompileContext->priv().caps(), pipelineDesc.paintParamsID());
 *
 *     SkDebugf("RenderPassDesc:\n");
 *     SkDebugf("   colorAttach: %s\n", rpd.fColorAttachment.toString().c_str());
 *     SkDebugf("   colorResolveAttach: %s\n", rpd.fColorResolveAttachment.toString().c_str());
 *     SkDebugf("   depthStencilAttach: %s\n", rpd.fDepthStencilAttachment.toString().c_str());
 *     SkDebugf("   clearColor: %.2f %.2f %.2f %.2f\n"
 *              "   clearDepth: %.2f\n"
 *              "   stencilClear: %u\n"
 *              "   writeSwizzle: %s\n"
 *              "   sampleCount: %u\n",
 *              rpd.fClearColor[0], rpd.fClearColor[1], rpd.fClearColor[2], rpd.fClearColor[3],
 *              rpd.fClearDepth,
 *              rpd.fClearStencil,
 *              rpd.fWriteSwizzle.asString().c_str(),
 *              (unsigned) rpd.fSampleCount);
 *
 * }
 * ```
 */
public fun dumpDescs(
  precompileContext: PrecompileContext?,
  pipelineDesc: GraphicsPipelineDesc,
  rpd: RenderPassDesc,
) {
  TODO("Implement dumpDescs")
}

/**
 * C++ original:
 * ```cpp
 * bool ExtractKeyDescs(PrecompileContext* precompileContext,
 *                      const UniqueKey& origKey,
 *                      GraphicsPipelineDesc* pipelineDesc,
 *                      RenderPassDesc* renderPassDesc) {
 *     const skgpu::graphite::Caps* caps = precompileContext->priv().caps();
 *     const RendererProvider* rendererProvider = precompileContext->priv().rendererProvider();
 *
 *     bool extracted = caps->extractGraphicsDescs(origKey, pipelineDesc, renderPassDesc,
 *                                                 rendererProvider);
 *     if (!extracted) {
 *         SkASSERT(0);
 *         return false;
 *     }
 *
 * #ifdef SK_DEBUG
 *     UniqueKey newKey = caps->makeGraphicsPipelineKey(*pipelineDesc, *renderPassDesc);
 *     if (origKey != newKey) {
 *         SkDebugf("------- The UniqueKey didn't round trip!\n");
 *         origKey.dump("original key:");
 *         newKey.dump("reassembled key:");
 *         DumpDescs(precompileContext, *pipelineDesc, *renderPassDesc);
 *         SkDebugf("------------------------\n");
 *     }
 *     SkASSERT(origKey == newKey);
 * #endif
 *
 *     return true;
 * }
 * ```
 */
public fun extractKeyDescs(
  precompileContext: PrecompileContext?,
  origKey: UniqueKey,
  pipelineDesc: GraphicsPipelineDesc?,
  renderPassDesc: RenderPassDesc?,
): Boolean {
  TODO("Implement extractKeyDescs")
}

/**
 * C++ original:
 * ```cpp
 * const char* GetAnnulusShaderCode() {
 *     static const char* sCode =
 *         // draw a annulus centered at "center" w/ inner and outer radii in "radii"
 *         "uniform float2 center;"
 *         "uniform float2 radii;"
 *         "half4 main(float2 xy) {"
 *             "float len = length(xy - center);"
 *             "half value = len < radii.x ? 0.0 : (len > radii.y ? 0.0 : 1.0);"
 *             "return half4(value);"
 *         "}";
 *
 *     return sCode;
 * }
 * ```
 */
public fun getAnnulusShaderCode(): Char {
  TODO("Implement getAnnulusShaderCode")
}

/**
 * C++ original:
 * ```cpp
 * SkRuntimeEffect* GetAnnulusShaderEffect() {
 *     SkRuntimeEffect::Options options;
 *     options.fName = "AnnulusShader";
 *
 *     static SkRuntimeEffect* sEffect = SkMakeRuntimeEffect(SkRuntimeEffect::MakeForShader,
 *                                                           GetAnnulusShaderCode(),
 *                                                           options);
 *
 *     return sEffect;
 * }
 * ```
 */
public fun getAnnulusShaderEffect(): SkRuntimeEffect {
  TODO("Implement getAnnulusShaderEffect")
}

/**
 * C++ original:
 * ```cpp
 * std::pair<sk_sp<SkShader>, sk_sp<PrecompileShader>> CreateAnnulusRuntimeShader() {
 *     SkRuntimeEffect* effect = GetAnnulusShaderEffect();
 *
 *     static const float kUniforms[4] = { 50.0f, 50.0f, 40.0f, 50.0f };
 *
 *     sk_sp<SkData> uniforms = SkData::MakeWithCopy(kUniforms, sizeof(kUniforms));
 *
 *     sk_sp<SkShader> s = effect->makeShader(std::move(uniforms), /* children= */ {});
 *     sk_sp<PrecompileShader> o = PrecompileRuntimeEffects::MakePrecompileShader(sk_ref_sp(effect));
 *     return { std::move(s), std::move(o) };
 * }
 * ```
 */
public fun createAnnulusRuntimeShader(): Pair<SkSp<SkShader>, SkSp<PrecompileShader>> {
  TODO("Implement createAnnulusRuntimeShader")
}

/**
 * C++ original:
 * ```cpp
 * SkRuntimeEffect* GetSrcBlenderEffect() {
 *     SkRuntimeEffect::Options options;
 *     options.fName = "SrcBlender";
 *
 *     static SkRuntimeEffect* sEffect = SkMakeRuntimeEffect(
 *         SkRuntimeEffect::MakeForBlender,
 *         "half4 main(half4 src, half4 dst) {"
 *             "return src;"
 *         "}",
 *         options);
 *
 *     return sEffect;
 * }
 * ```
 */
public fun getSrcBlenderEffect(): SkRuntimeEffect {
  TODO("Implement getSrcBlenderEffect")
}

/**
 * C++ original:
 * ```cpp
 * std::pair<sk_sp<SkBlender>, sk_sp<PrecompileBlender>> CreateSrcRuntimeBlender() {
 *     SkRuntimeEffect* effect = GetSrcBlenderEffect();
 *
 *     sk_sp<SkBlender> b = effect->makeBlender(/* uniforms= */ nullptr);
 *     sk_sp<PrecompileBlender> o =
 *             PrecompileRuntimeEffects::MakePrecompileBlender(sk_ref_sp(effect));
 *     return { std::move(b) , std::move(o) };
 * }
 * ```
 */
public fun createSrcRuntimeBlender(): Pair<SkSp<SkBlender>, SkSp<PrecompileBlender>> {
  TODO("Implement createSrcRuntimeBlender")
}

/**
 * C++ original:
 * ```cpp
 * SkRuntimeEffect* GetDstBlenderEffect() {
 *     SkRuntimeEffect::Options options;
 *     options.fName = "DstBlender";
 *
 *     static SkRuntimeEffect* sEffect = SkMakeRuntimeEffect(
 *         SkRuntimeEffect::MakeForBlender,
 *         "half4 main(half4 src, half4 dst) {"
 *             "return dst;"
 *         "}",
 *         options);
 *
 *     return sEffect;
 * }
 * ```
 */
public fun getDstBlenderEffect(): SkRuntimeEffect {
  TODO("Implement getDstBlenderEffect")
}

/**
 * C++ original:
 * ```cpp
 * std::pair<sk_sp<SkBlender>, sk_sp<PrecompileBlender>> CreateDstRuntimeBlender() {
 *     SkRuntimeEffect* effect = GetDstBlenderEffect();
 *
 *     sk_sp<SkBlender> b = effect->makeBlender(/* uniforms= */ nullptr);
 *     sk_sp<PrecompileBlender> o =
 *             PrecompileRuntimeEffects::MakePrecompileBlender(sk_ref_sp(effect));
 *     return { std::move(b) , std::move(o) };
 * }
 * ```
 */
public fun createDstRuntimeBlender(): Pair<SkSp<SkBlender>, SkSp<PrecompileBlender>> {
  TODO("Implement createDstRuntimeBlender")
}

/**
 * C++ original:
 * ```cpp
 * SkRuntimeEffect* GetComboBlenderEffect() {
 *     SkRuntimeEffect::Options options;
 *     options.fName = "ComboBlender";
 *
 *     static SkRuntimeEffect* sEffect = SkMakeRuntimeEffect(
 *         SkRuntimeEffect::MakeForBlender,
 *         "uniform float blendFrac;"
 *         "uniform blender a;"
 *         "uniform blender b;"
 *         "half4 main(half4 src, half4 dst) {"
 *             "return (blendFrac * a.eval(src, dst)) + ((1 - blendFrac) * b.eval(src, dst));"
 *         "}",
 *         options);
 *
 *     return sEffect;
 * }
 * ```
 */
public fun getComboBlenderEffect(): SkRuntimeEffect {
  TODO("Implement getComboBlenderEffect")
}

/**
 * C++ original:
 * ```cpp
 * std::pair<sk_sp<SkBlender>, sk_sp<PrecompileBlender>> CreateComboRuntimeBlender() {
 *     SkRuntimeEffect* effect = GetComboBlenderEffect();
 *
 *     auto [src, srcO] = CreateSrcRuntimeBlender();
 *     auto [dst, dstO] = CreateDstRuntimeBlender();
 *
 *     SkRuntimeEffect::ChildPtr children[] = { src, dst };
 *
 *     const float kUniforms[] = { 1.0f };
 *
 *     sk_sp<SkData> uniforms = SkData::MakeWithCopy(kUniforms, sizeof(kUniforms));
 *     sk_sp<SkBlender> b = effect->makeBlender(std::move(uniforms), children);
 *     sk_sp<PrecompileBlender> o = PrecompileRuntimeEffects::MakePrecompileBlender(
 *             sk_ref_sp(effect),
 *             {{ {{ srcO }}, {{ dstO }} }});
 *     return { std::move(b) , std::move(o) };
 * }
 * ```
 */
public fun createComboRuntimeBlender(): Pair<SkSp<SkBlender>, SkSp<PrecompileBlender>> {
  TODO("Implement createComboRuntimeBlender")
}

/**
 * C++ original:
 * ```cpp
 * SkRuntimeEffect* GetDoubleColorFilterEffect() {
 *     SkRuntimeEffect::Options options;
 *     options.fName = "DoubleColorFilter";
 *
 *     static SkRuntimeEffect* sEffect = SkMakeRuntimeEffect(
 *         SkRuntimeEffect::MakeForColorFilter,
 *         "half4 main(half4 c) {"
 *             "return 2*c;"
 *         "}",
 *         options);
 *
 *     return sEffect;
 * }
 * ```
 */
public fun getDoubleColorFilterEffect(): SkRuntimeEffect {
  TODO("Implement getDoubleColorFilterEffect")
}

/**
 * C++ original:
 * ```cpp
 * std::pair<sk_sp<SkColorFilter>, sk_sp<PrecompileColorFilter>> CreateDoubleRuntimeColorFilter() {
 *     SkRuntimeEffect* effect = GetDoubleColorFilterEffect();
 *
 *     return { effect->makeColorFilter(/* uniforms= */ nullptr),
 *              PrecompileRuntimeEffects::MakePrecompileColorFilter(sk_ref_sp(effect)) };
 * }
 * ```
 */
public fun createDoubleRuntimeColorFilter(): Pair<SkSp<SkColorFilter>, SkSp<PrecompileColorFilter>> {
  TODO("Implement createDoubleRuntimeColorFilter")
}

/**
 * C++ original:
 * ```cpp
 * SkRuntimeEffect* GetHalfColorFilterEffect() {
 *     SkRuntimeEffect::Options options;
 *     // We withhold this name to test out the default name case
 *     //options.fName = "HalfColorFilter";
 *
 *     static SkRuntimeEffect* sEffect = SkMakeRuntimeEffect(
 *         SkRuntimeEffect::MakeForColorFilter,
 *         "half4 main(half4 c) {"
 *             "return 0.5*c;"
 *         "}",
 *         options);
 *
 *     return sEffect;
 * }
 * ```
 */
public fun getHalfColorFilterEffect(): SkRuntimeEffect {
  TODO("Implement getHalfColorFilterEffect")
}

/**
 * C++ original:
 * ```cpp
 * std::pair<sk_sp<SkColorFilter>, sk_sp<PrecompileColorFilter>> CreateHalfRuntimeColorFilter() {
 *     SkRuntimeEffect* effect = GetHalfColorFilterEffect();
 *
 *     return { effect->makeColorFilter(/* uniforms= */ nullptr),
 *              PrecompileRuntimeEffects::MakePrecompileColorFilter(sk_ref_sp(effect)) };
 * }
 * ```
 */
public fun createHalfRuntimeColorFilter(): Pair<SkSp<SkColorFilter>, SkSp<PrecompileColorFilter>> {
  TODO("Implement createHalfRuntimeColorFilter")
}

/**
 * C++ original:
 * ```cpp
 * SkRuntimeEffect* GetComboColorFilterEffect() {
 *     SkRuntimeEffect::Options options;
 *     options.fName = "ComboColorFilter";
 *
 *     static SkRuntimeEffect* sEffect = SkMakeRuntimeEffect(
 *         SkRuntimeEffect::MakeForColorFilter,
 *         "uniform float blendFrac;"
 *         "uniform colorFilter a;"
 *         "uniform colorFilter b;"
 *         "half4 main(half4 c) {"
 *             "return (blendFrac * a.eval(c)) + ((1 - blendFrac) * b.eval(c));"
 *         "}",
 *         options);
 *
 *     return sEffect;
 * }
 * ```
 */
public fun getComboColorFilterEffect(): SkRuntimeEffect {
  TODO("Implement getComboColorFilterEffect")
}

/**
 * C++ original:
 * ```cpp
 * std::pair<sk_sp<SkColorFilter>, sk_sp<PrecompileColorFilter>> CreateComboRuntimeColorFilter() {
 *     SkRuntimeEffect* effect = GetComboColorFilterEffect();
 *
 *     auto [src, srcO] = CreateDoubleRuntimeColorFilter();
 *     auto [dst, dstO] = CreateHalfRuntimeColorFilter();
 *
 *     SkRuntimeEffect::ChildPtr children[] = { src, dst };
 *
 *     const float kUniforms[] = { 0.5f };
 *
 *     sk_sp<SkData> uniforms = SkData::MakeWithCopy(kUniforms, sizeof(kUniforms));
 *     sk_sp<SkColorFilter> cf = effect->makeColorFilter(std::move(uniforms), children);
 *     sk_sp<PrecompileColorFilter> o =
 *             PrecompileRuntimeEffects::MakePrecompileColorFilter(sk_ref_sp(effect),
 *                                                                 {{ {{ srcO }}, {{ dstO }} }});
 *     return { std::move(cf) , std::move(o) };
 * }
 * ```
 */
public fun createComboRuntimeColorFilter(): Pair<SkSp<SkColorFilter>, SkSp<PrecompileColorFilter>> {
  TODO("Implement createComboRuntimeColorFilter")
}

/**
 * C++ original:
 * ```cpp
 * bool DecodeDataToBitmap(sk_sp<SkData> data, SkBitmap* dst) {
 *     std::unique_ptr<SkImageGenerator> gen(SkImageGenerators::MakeFromEncoded(std::move(data)));
 *     return gen && dst->tryAllocPixels(gen->getInfo()) &&
 *            gen->getPixels(
 *                    gen->getInfo().makeColorSpace(nullptr), dst->getPixels(), dst->rowBytes());
 * }
 * ```
 */
public fun decodeDataToBitmap(`data`: SkSp<SkData>, dst: SkBitmap?): Boolean {
  TODO("Implement decodeDataToBitmap")
}

/**
 * C++ original:
 * ```cpp
 * bool DecodeDataToBitmapWithColorType(sk_sp<SkData> data, SkBitmap* dst, SkColorType dstCT) {
 *   std::unique_ptr<SkImageGenerator> gen(SkImageGenerators::MakeFromEncoded(std::move(data)));
 *   return gen && dst->tryAllocPixels(gen->getInfo().makeColorType(dstCT)) &&
 *          gen->getPixels(
 *                  gen->getInfo().makeColorSpace(nullptr).makeColorType(dstCT),
 *                  dst->getPixels(), dst->rowBytes());
 * }
 * ```
 */
public fun decodeDataToBitmapWithColorType(
  `data`: SkSp<SkData>,
  dst: SkBitmap?,
  dstCT: SkColorType,
): Boolean {
  TODO("Implement decodeDataToBitmapWithColorType")
}

/**
 * C++ original:
 * ```cpp
 * bool BitmapToBase64DataURI(const SkBitmap& bitmap, SkString* dst) {
 *     SkPixmap pm;
 *     if (!bitmap.peekPixels(&pm)) {
 *         dst->set("peekPixels failed");
 *         return false;
 *     }
 *
 *     // We're going to embed this PNG in a data URI, so make it as small as possible
 *     SkPngEncoder::Options options;
 *     options.fFilterFlags = SkPngEncoder::FilterFlag::kAll;
 *     options.fZLibLevel = 9;
 *
 *     sk_sp<SkData> pngData = SkPngEncoder::Encode(pm, options);
 *     if (!pngData) {
 *         dst->set("SkPngEncoder::Encode failed");
 *         return false;
 *     }
 *
 *     size_t len = SkBase64::EncodedSize(pngData->size());
 *
 *     // The PNG can be almost arbitrarily large. We don't want to fill our logs with enormous URLs.
 *     // Infra says these can be pretty big, as long as we're only outputting them on failure.
 *     static const size_t kMaxBase64Length = 1024 * 1024;
 *     if (len > kMaxBase64Length) {
 *         dst->printf("Encoded image too large (%u bytes)", static_cast<uint32_t>(len));
 *         return false;
 *     }
 *
 *     dst->resize(len);
 *     SkBase64::Encode(pngData->data(), pngData->size(), dst->data());
 *     dst->prepend("data:image/png;base64,");
 *     return true;
 * }
 * ```
 */
public fun bitmapToBase64DataURI(bitmap: SkBitmap, dst: String?): Boolean {
  TODO("Implement bitmapToBase64DataURI")
}

/**
 * C++ original:
 * ```cpp
 * bool EncodeImageToPngFile(const char* path, const SkPixmap& src) {
 *     SkFILEWStream file(path);
 *     return file.isValid() && SkPngEncoder::Encode(&file, src, {});
 * }
 * ```
 */
public fun encodeImageToPngFile(path: String?, src: SkPixmap): Boolean {
  TODO("Implement encodeImageToPngFile")
}

/**
 * C++ original:
 * ```cpp
 * int64_t sk_tools::getMaxResidentSetSizeBytes() {
 *         struct rusage ru;
 *         getrusage(RUSAGE_SELF, &ru);
 *     #if defined(SK_BUILD_FOR_MAC) || defined(SK_BUILD_FOR_IOS)
 *         return ru.ru_maxrss;         // Darwin reports bytes.
 *     #else
 *         return ru.ru_maxrss * 1024;  // Linux reports kilobytes.
 *     #endif
 *     }
 * ```
 */
public fun getMaxResidentSetSizeBytes(): Long {
  TODO("Implement getMaxResidentSetSizeBytes")
}

/**
 * C++ original:
 * ```cpp
 * int64_t sk_tools::getCurrResidentSetSizeBytes() {
 *         mach_task_basic_info info;
 *         mach_msg_type_number_t count = MACH_TASK_BASIC_INFO_COUNT;
 *         if (KERN_SUCCESS !=
 *                 task_info(mach_task_self(), MACH_TASK_BASIC_INFO, (task_info_t)&info, &count)) {
 *             return -1;
 *         }
 *         return info.resident_size;
 *     }
 * ```
 */
public fun getCurrResidentSetSizeBytes(): Long {
  TODO("Implement getCurrResidentSetSizeBytes")
}

/**
 * C++ original:
 * ```cpp
 * int sk_tools::getMaxResidentSetSizeMB() {
 *     int64_t bytes = sk_tools::getMaxResidentSetSizeBytes();
 *     return bytes < 0 ? -1 : static_cast<int>(bytes / 1024 / 1024);
 * }
 * ```
 */
public fun getMaxResidentSetSizeMB(): Int {
  TODO("Implement getMaxResidentSetSizeMB")
}

/**
 * C++ original:
 * ```cpp
 * int sk_tools::getCurrResidentSetSizeMB() {
 *     int64_t bytes = sk_tools::getCurrResidentSetSizeBytes();
 *     return bytes < 0 ? -1 : static_cast<int>(bytes / 1024 / 1024);
 * }
 * ```
 */
public fun getCurrResidentSetSizeMB(): Int {
  TODO("Implement getCurrResidentSetSizeMB")
}

/**
 * C++ original:
 * ```cpp
 * SkString GetResourcePath(const char* resource) {
 *     return SkOSPath::Join(FLAGS_resourcePath[0], resource);
 * }
 * ```
 */
public fun getResourcePath(resource: String?): String {
  TODO("Implement getResourcePath")
}

/**
 * C++ original:
 * ```cpp
 * void SetResourcePath(const char* resource) {
 *     FLAGS_resourcePath.set(0, resource);
 * }
 * ```
 */
public fun setResourcePath(resource: String?) {
  TODO("Implement setResourcePath")
}

/**
 * C++ original:
 * ```cpp
 * std::unique_ptr<SkStreamAsset> GetResourceAsStream(const char* resource, bool useFileStream) {
 *     if (useFileStream) {
 *         auto path = GetResourcePath(resource);
 *         return SkFILEStream::Make(path.c_str());
 *     } else {
 *         auto data = GetResourceAsData(resource);
 *         return data ? std::unique_ptr<SkStreamAsset>(new SkMemoryStream(std::move(data)))
 *                     : nullptr;
 *     }
 * }
 * ```
 */
public fun getResourceAsStream(resource: String?, useFileStream: Boolean): SkStreamAsset? {
  TODO("Implement getResourceAsStream")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkBlender> GetRuntimeBlendForBlendMode(SkBlendMode mode) {
 *     static auto result = SkRuntimeEffect::MakeForBlender(SkString(
 *         "uniform blender b;"
 *         "half4 main(half4 src, half4 dst) {"
 *             "return b.eval(src, dst);"
 *         "}"
 *     ));
 *
 *     SkASSERTF(result.effect, "%s", result.errorText.c_str());
 *
 *     SkRuntimeBlendBuilder builder(result.effect);
 *     builder.child("b") = SkBlender::Mode(mode);
 *     return builder.makeBlender();
 * }
 * ```
 */
public fun getRuntimeBlendForBlendMode(mode: SkBlendMode): SkSp<SkBlender> {
  TODO("Implement getRuntimeBlendForBlendMode")
}

/**
 * C++ original:
 * ```cpp
 * void SetFontTestDataDirectory() {
 *     if (FLAGS_fontTestDataPath.isEmpty()) {
 *         return;
 *     }
 *     if (strlen(FLAGS_fontTestDataPath[0])) {
 *         gFontTestDataBasePath = FLAGS_fontTestDataPath[0];
 *     }
 * }
 * ```
 */
public fun setFontTestDataDirectory() {
  TODO("Implement setFontTestDataDirectory")
}

/**
 * C++ original:
 * ```cpp
 * SkString prefixWithTestDataPath(SkString suffix) {
 *     return SkOSPath::Join(gFontTestDataBasePath, suffix.c_str());
 * }
 * ```
 */
public fun prefixWithTestDataPath(suffix: String): String {
  TODO("Implement prefixWithTestDataPath")
}

/**
 * C++ original:
 * ```cpp
 * SkString prefixWithFontsPath(SkString suffix) {
 *     SkString fontsPath = prefixWithTestDataPath(SkString("fonts"));
 *     return SkOSPath::Join(fontsPath.c_str(), suffix.c_str());
 * }
 * ```
 */
public fun prefixWithFontsPath(suffix: String): String {
  TODO("Implement prefixWithFontsPath")
}

/**
 * C++ original:
 * ```cpp
 * const char* alphatype_name(SkAlphaType at) {
 *     switch (at) {
 *         case kUnknown_SkAlphaType:  return "Unknown";
 *         case kOpaque_SkAlphaType:   return "Opaque";
 *         case kPremul_SkAlphaType:   return "Premul";
 *         case kUnpremul_SkAlphaType: return "Unpremul";
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun alphatypeName(at: SkAlphaType): Char {
  TODO("Implement alphatypeName")
}

/**
 * C++ original:
 * ```cpp
 * const char* colortype_name(SkColorType ct) {
 *     switch (ct) {
 *         case kUnknown_SkColorType:            return "Unknown";
 *         case kAlpha_8_SkColorType:            return "Alpha_8";
 *         case kA16_unorm_SkColorType:          return "Alpha_16";
 *         case kA16_float_SkColorType:          return "A16_float";
 *         case kRGB_565_SkColorType:            return "RGB_565";
 *         case kARGB_4444_SkColorType:          return "ARGB_4444";
 *         case kRGBA_8888_SkColorType:          return "RGBA_8888";
 *         case kSRGBA_8888_SkColorType:         return "SRGBA_8888";
 *         case kRGB_888x_SkColorType:           return "RGB_888x";
 *         case kBGRA_8888_SkColorType:          return "BGRA_8888";
 *         case kRGBA_1010102_SkColorType:       return "RGBA_1010102";
 *         case kBGRA_1010102_SkColorType:       return "BGRA_1010102";
 *         case kRGB_101010x_SkColorType:        return "RGB_101010x";
 *         case kBGR_101010x_SkColorType:        return "BGR_101010x";
 *         case kBGR_101010x_XR_SkColorType:     return "BGR_101010x_XR";
 *         case kRGBA_10x6_SkColorType:          return "RGBA_10x6";
 *         case kGray_8_SkColorType:             return "Gray_8";
 *         case kRGBA_F16Norm_SkColorType:       return "RGBA_F16Norm";
 *         case kRGB_F16F16F16x_SkColorType:     return "RGB_F16F16F16x";
 *         case kRGBA_F16_SkColorType:           return "RGBA_F16";
 *         case kRGBA_F32_SkColorType:           return "RGBA_F32";
 *         case kR8G8_unorm_SkColorType:         return "R8G8_unorm";
 *         case kR16_unorm_SkColorType:          return "R16_unorm";
 *         case kR16G16_unorm_SkColorType:       return "R16G16_unorm";
 *         case kR16G16_float_SkColorType:       return "R16G16_float";
 *         case kR16G16B16A16_unorm_SkColorType: return "R16G16B16A16_unorm";
 *         case kR8_unorm_SkColorType:           return "R8_unorm";
 *         case kBGRA_10101010_XR_SkColorType:   return "BGRA_10101010_XR";
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun colortypeName(ct: SkColorType): Char {
  TODO("Implement colortypeName")
}

/**
 * C++ original:
 * ```cpp
 * const char* colortype_depth(SkColorType ct) {
 *     switch (ct) {
 *         case kUnknown_SkColorType:            return "Unknown";
 *         case kAlpha_8_SkColorType:            return "A8";
 *         case kA16_unorm_SkColorType:          return "A16";
 *         case kA16_float_SkColorType:          return "AF16";
 *         case kRGB_565_SkColorType:            return "565";
 *         case kARGB_4444_SkColorType:          return "4444";
 *         case kRGBA_8888_SkColorType:          return "8888";
 *         case kSRGBA_8888_SkColorType:         return "8888";
 *         case kRGB_888x_SkColorType:           return "888";
 *         case kBGRA_8888_SkColorType:          return "8888";
 *         case kRGBA_1010102_SkColorType:       return "1010102";
 *         case kBGRA_1010102_SkColorType:       return "1010102";
 *         case kRGB_101010x_SkColorType:        return "101010";
 *         case kBGR_101010x_SkColorType:        return "101010";
 *         case kBGR_101010x_XR_SkColorType:     return "101010";
 *         case kBGRA_10101010_XR_SkColorType:   return "10101010";
 *         case kRGBA_10x6_SkColorType:          return "10101010";
 *         case kGray_8_SkColorType:             return "G8";
 *         case kRGBA_F16Norm_SkColorType:       return "F16Norm";
 *         case kRGB_F16F16F16x_SkColorType:     return "F16F16F16x";
 *         case kRGBA_F16_SkColorType:           return "F16";
 *         case kRGBA_F32_SkColorType:           return "F32";
 *         case kR8G8_unorm_SkColorType:         return "88";
 *         case kR16_unorm_SkColorType:          return "R16";
 *         case kR16G16_unorm_SkColorType:       return "1616";
 *         case kR16G16_float_SkColorType:       return "F16F16";
 *         case kR16G16B16A16_unorm_SkColorType: return "16161616";
 *         case kR8_unorm_SkColorType:           return "R8";
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun colortypeDepth(ct: SkColorType): Char {
  TODO("Implement colortypeDepth")
}

/**
 * C++ original:
 * ```cpp
 * const char* tilemode_name(SkTileMode mode) {
 *     switch (mode) {
 *         case SkTileMode::kClamp:  return "clamp";
 *         case SkTileMode::kRepeat: return "repeat";
 *         case SkTileMode::kMirror: return "mirror";
 *         case SkTileMode::kDecal:  return "decal";
 *     }
 *     SkUNREACHABLE;
 * }
 * ```
 */
public fun tilemodeName(mode: SkTileMode): Char {
  TODO("Implement tilemodeName")
}

/**
 * C++ original:
 * ```cpp
 * SkColor color_to_565(SkColor color) {
 *     // Not a good idea to use this function for greyscale colors...
 *     // it will add an obvious purple or green tint.
 *     SkASSERT(SkColorGetR(color) != SkColorGetG(color) || SkColorGetR(color) != SkColorGetB(color) ||
 *              SkColorGetG(color) != SkColorGetB(color));
 *
 *     SkPMColor pmColor = SkPreMultiplyColor(color);
 *     U16CPU    color16 = SkPixel32ToPixel16(pmColor);
 *     return SkPixel16ToColor(color16);
 * }
 * ```
 */
public fun colorTo565(color: SkColor): SkColor {
  TODO("Implement colorTo565")
}

/**
 * C++ original:
 * ```cpp
 * SkBitmap create_checkerboard_bitmap(int w, int h, SkColor c1, SkColor c2, int checkSize) {
 *     SkBitmap bitmap;
 *     bitmap.allocPixels(SkImageInfo::MakeS32(w, h, kPremul_SkAlphaType));
 *     SkCanvas canvas(bitmap);
 *
 *     ToolUtils::draw_checkerboard(&canvas, c1, c2, checkSize);
 *     return bitmap;
 * }
 * ```
 */
public fun createCheckerboardBitmap(
  w: Int,
  h: Int,
  c1: SkColor,
  c2: SkColor,
  checkSize: Int,
): SkBitmap {
  TODO("Implement createCheckerboardBitmap")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkImage> create_checkerboard_image(int w, int h, SkColor c1, SkColor c2, int checkSize) {
 *     auto surf = SkSurfaces::Raster(SkImageInfo::MakeN32Premul(w, h));
 *     ToolUtils::draw_checkerboard(surf->getCanvas(), c1, c2, checkSize);
 *     return surf->makeImageSnapshot();
 * }
 * ```
 */
public fun createCheckerboardImage(
  w: Int,
  h: Int,
  c1: SkColor,
  c2: SkColor,
  checkSize: Int,
): SkSp<SkImage> {
  TODO("Implement createCheckerboardImage")
}

/**
 * C++ original:
 * ```cpp
 * int make_pixmaps(SkColorType ct,
 *                  SkAlphaType at,
 *                  bool withMips,
 *                  const SkColor4f colors[6],
 *                  SkPixmap pixmaps[6],
 *                  std::unique_ptr<char[]>* mem) {
 *
 *     int levelSize = 32;
 *     int numMipLevels = withMips ? 6 : 1;
 *     size_t size = 0;
 *     SkImageInfo ii[6];
 *     size_t rowBytes[6];
 *     for (int level = 0; level < numMipLevels; ++level) {
 *         ii[level] = SkImageInfo::Make(levelSize, levelSize, ct, at);
 *         rowBytes[level] = ii[level].minRowBytes();
 *         // Make sure we test row bytes that aren't tight.
 *         if (!(level % 2)) {
 *             rowBytes[level] += (level + 1)*SkColorTypeBytesPerPixel(ii[level].colorType());
 *         }
 *         size += rowBytes[level]*ii[level].height();
 *         levelSize /= 2;
 *     }
 *     mem->reset(new char[size]);
 *     char* addr = mem->get();
 *     for (int level = 0; level < numMipLevels; ++level) {
 *         pixmaps[level].reset(ii[level], addr, rowBytes[level]);
 *         addr += rowBytes[level]*ii[level].height();
 *         pixmaps[level].erase(colors[level]);
 *     }
 *     return numMipLevels;
 * }
 * ```
 */
public fun makePixmaps(
  ct: SkColorType,
  at: SkAlphaType,
  withMips: Boolean,
  colors: Array<SkColor4f>,
  pixmaps: Array<SkPixmap>,
  mem: CharArray?,
): Int {
  TODO("Implement makePixmaps")
}

/**
 * C++ original:
 * ```cpp
 * void add_to_text_blob_w_len(SkTextBlobBuilder* builder,
 *                             const char*        text,
 *                             size_t             len,
 *                             SkTextEncoding     encoding,
 *                             const SkFont&      font,
 *                             SkScalar           x,
 *                             SkScalar           y) {
 *     size_t  count = font.countText(text, len, encoding);
 *     if (count < 1) {
 *         return;
 *     }
 *     auto run   = builder->allocRun(font, count, x, y);
 *     font.textToGlyphs(text, len, encoding, {run.glyphs, count});
 * }
 * ```
 */
public fun addToTextBlobWLen(
  builder: SkTextBlobBuilder?,
  text: String?,
  len: ULong,
  encoding: SkTextEncoding,
  font: SkFont,
  x: SkScalar,
  y: SkScalar,
) {
  TODO("Implement addToTextBlobWLen")
}

/**
 * C++ original:
 * ```cpp
 * void add_to_text_blob(SkTextBlobBuilder* builder,
 *                       const char*        text,
 *                       const SkFont&      font,
 *                       SkScalar           x,
 *                       SkScalar           y) {
 *     add_to_text_blob_w_len(builder, text, strlen(text), SkTextEncoding::kUTF8, font, x, y);
 * }
 * ```
 */
public fun addToTextBlob(
  builder: SkTextBlobBuilder?,
  text: String?,
  font: SkFont,
  x: SkScalar,
  y: SkScalar,
) {
  TODO("Implement addToTextBlob")
}

/**
 * C++ original:
 * ```cpp
 * SkPath get_text_path(const SkFont&  font,
 *                    const void*    text,
 *                    size_t         length,
 *                    SkTextEncoding encoding,
 *                    const SkPoint  pos[]) {
 *     SkAutoToGlyphs      atg(font, text, length, encoding);
 *     const size_t        count = atg.count();
 *     AutoTArray<SkPoint> computedPos;
 *     if (pos == nullptr) {
 *         computedPos.reset(count);
 *         font.getPos(atg.glyphs(), computedPos);
 *         pos = computedPos.get();
 *     }
 *
 *     struct Rec {
 *         SkPathBuilder  fBuilder;
 *         const SkPoint* fPos;
 *     } rec = {{}, pos};
 *     font.getPaths(atg.glyphs(),
 *                   [](const SkPath* src, const SkMatrix& mx, void* ctx) {
 *                       Rec* rec = (Rec*)ctx;
 *                       if (src) {
 *                           SkMatrix tmp(mx);
 *                           tmp.postTranslate(rec->fPos->fX, rec->fPos->fY);
 *                           rec->fBuilder.addPath(*src, tmp);
 *                       }
 *                       rec->fPos += 1;
 *                   },
 *                   &rec);
 *     return rec.fBuilder.detach();
 * }
 * ```
 */
public fun getTextPath(
  font: SkFont,
  text: Unit?,
  length: ULong,
  encoding: SkTextEncoding,
  pos: Array<SkPoint>,
): SkPath {
  TODO("Implement getTextPath")
}

/**
 * C++ original:
 * ```cpp
 * static inline void norm_to_rgb(SkBitmap* bm, int x, int y, const SkVector3& norm) {
 *     SkASSERT(SkScalarNearlyEqual(norm.length(), 1.0f));
 *     unsigned char r      = static_cast<unsigned char>((0.5f * norm.fX + 0.5f) * 255);
 *     unsigned char g      = static_cast<unsigned char>((-0.5f * norm.fY + 0.5f) * 255);
 *     unsigned char b      = static_cast<unsigned char>((0.5f * norm.fZ + 0.5f) * 255);
 *     *bm->getAddr32(x, y) = SkPackARGB32(0xFF, r, g, b);
 * }
 * ```
 */
public fun normToRgb(
  bm: SkBitmap?,
  x: Int,
  y: Int,
  norm: SkVector3,
) {
  TODO("Implement normToRgb")
}

/**
 * C++ original:
 * ```cpp
 * void create_hemi_normal_map(SkBitmap* bm, const SkIRect& dst) {
 *     const SkPoint center =
 *             SkPoint::Make(dst.fLeft + (dst.width() / 2.0f), dst.fTop + (dst.height() / 2.0f));
 *     const SkPoint halfSize = SkPoint::Make(dst.width() / 2.0f, dst.height() / 2.0f);
 *
 *     SkVector3 norm;
 *
 *     for (int y = dst.fTop; y < dst.fBottom; ++y) {
 *         for (int x = dst.fLeft; x < dst.fRight; ++x) {
 *             norm.fX = (x + 0.5f - center.fX) / halfSize.fX;
 *             norm.fY = (y + 0.5f - center.fY) / halfSize.fY;
 *
 *             SkScalar tmp = norm.fX * norm.fX + norm.fY * norm.fY;
 *             if (tmp >= 1.0f) {
 *                 norm.set(0.0f, 0.0f, 1.0f);
 *             } else {
 *                 norm.fZ = sqrtf(1.0f - tmp);
 *             }
 *
 *             norm_to_rgb(bm, x, y, norm);
 *         }
 *     }
 * }
 * ```
 */
public fun createHemiNormalMap(bm: SkBitmap?, dst: SkIRect) {
  TODO("Implement createHemiNormalMap")
}

/**
 * C++ original:
 * ```cpp
 * void create_frustum_normal_map(SkBitmap* bm, const SkIRect& dst) {
 *     const SkPoint center =
 *             SkPoint::Make(dst.fLeft + (dst.width() / 2.0f), dst.fTop + (dst.height() / 2.0f));
 *
 *     SkIRect inner = dst;
 *     inner.inset(dst.width() / 4, dst.height() / 4);
 *
 *     SkPoint3       norm;
 *     const SkPoint3 left  = SkPoint3::Make(-SK_ScalarRoot2Over2, 0.0f, SK_ScalarRoot2Over2);
 *     const SkPoint3 up    = SkPoint3::Make(0.0f, -SK_ScalarRoot2Over2, SK_ScalarRoot2Over2);
 *     const SkPoint3 right = SkPoint3::Make(SK_ScalarRoot2Over2, 0.0f, SK_ScalarRoot2Over2);
 *     const SkPoint3 down  = SkPoint3::Make(0.0f, SK_ScalarRoot2Over2, SK_ScalarRoot2Over2);
 *
 *     for (int y = dst.fTop; y < dst.fBottom; ++y) {
 *         for (int x = dst.fLeft; x < dst.fRight; ++x) {
 *             if (inner.contains(x, y)) {
 *                 norm.set(0.0f, 0.0f, 1.0f);
 *             } else {
 *                 SkScalar locX = x + 0.5f - center.fX;
 *                 SkScalar locY = y + 0.5f - center.fY;
 *
 *                 if (locX >= 0.0f) {
 *                     if (locY > 0.0f) {
 *                         norm = locX >= locY ? right : down;  // LR corner
 *                     } else {
 *                         norm = locX > -locY ? right : up;  // UR corner
 *                     }
 *                 } else {
 *                     if (locY > 0.0f) {
 *                         norm = -locX > locY ? left : down;  // LL corner
 *                     } else {
 *                         norm = locX > locY ? up : left;  // UL corner
 *                     }
 *                 }
 *             }
 *
 *             norm_to_rgb(bm, x, y, norm);
 *         }
 *     }
 * }
 * ```
 */
public fun createFrustumNormalMap(bm: SkBitmap?, dst: SkIRect) {
  TODO("Implement createFrustumNormalMap")
}

/**
 * C++ original:
 * ```cpp
 * void create_tetra_normal_map(SkBitmap* bm, const SkIRect& dst) {
 *     const SkPoint center =
 *             SkPoint::Make(dst.fLeft + (dst.width() / 2.0f), dst.fTop + (dst.height() / 2.0f));
 *
 *     static const SkScalar k1OverRoot3 = 0.5773502692f;
 *
 *     SkPoint3       norm;
 *     const SkPoint3 leftUp  = SkPoint3::Make(-k1OverRoot3, -k1OverRoot3, k1OverRoot3);
 *     const SkPoint3 rightUp = SkPoint3::Make(k1OverRoot3, -k1OverRoot3, k1OverRoot3);
 *     const SkPoint3 down    = SkPoint3::Make(0.0f, SK_ScalarRoot2Over2, SK_ScalarRoot2Over2);
 *
 *     for (int y = dst.fTop; y < dst.fBottom; ++y) {
 *         for (int x = dst.fLeft; x < dst.fRight; ++x) {
 *             SkScalar locX = x + 0.5f - center.fX;
 *             SkScalar locY = y + 0.5f - center.fY;
 *
 *             if (locX >= 0.0f) {
 *                 if (locY > 0.0f) {
 *                     norm = locX >= locY ? rightUp : down;  // LR corner
 *                 } else {
 *                     norm = rightUp;
 *                 }
 *             } else {
 *                 if (locY > 0.0f) {
 *                     norm = -locX > locY ? leftUp : down;  // LL corner
 *                 } else {
 *                     norm = leftUp;
 *                 }
 *             }
 *
 *             norm_to_rgb(bm, x, y, norm);
 *         }
 *     }
 * }
 * ```
 */
public fun createTetraNormalMap(bm: SkBitmap?, dst: SkIRect) {
  TODO("Implement createTetraNormalMap")
}

/**
 * C++ original:
 * ```cpp
 * bool copy_to(SkBitmap* dst, SkColorType dstColorType, const SkBitmap& src) {
 *     SkPixmap srcPM;
 *     if (!src.peekPixels(&srcPM)) {
 *         return false;
 *     }
 *
 *     SkBitmap    tmpDst;
 *     SkImageInfo dstInfo = srcPM.info().makeColorType(dstColorType);
 *     if (!tmpDst.setInfo(dstInfo)) {
 *         return false;
 *     }
 *
 *     if (!tmpDst.tryAllocPixels()) {
 *         return false;
 *     }
 *
 *     SkPixmap dstPM;
 *     if (!tmpDst.peekPixels(&dstPM)) {
 *         return false;
 *     }
 *
 *     if (!srcPM.readPixels(dstPM)) {
 *         return false;
 *     }
 *
 *     dst->swap(tmpDst);
 *     return true;
 * }
 * ```
 */
public fun copyTo(
  dst: SkBitmap?,
  dstColorType: SkColorType,
  src: SkBitmap,
): Boolean {
  TODO("Implement copyTo")
}

/**
 * C++ original:
 * ```cpp
 * void copy_to_g8(SkBitmap* dst, const SkBitmap& src) {
 *     SkASSERT(kBGRA_8888_SkColorType == src.colorType() ||
 *              kRGBA_8888_SkColorType == src.colorType());
 *
 *     SkImageInfo grayInfo = src.info().makeColorType(kGray_8_SkColorType);
 *     dst->allocPixels(grayInfo);
 *     uint8_t*        dst8  = (uint8_t*)dst->getPixels();
 *     const uint32_t* src32 = (const uint32_t*)src.getPixels();
 *
 *     const int  w      = src.width();
 *     const int  h      = src.height();
 *     const bool isBGRA = (kBGRA_8888_SkColorType == src.colorType());
 *     for (int y = 0; y < h; ++y) {
 *         if (isBGRA) {
 *             // BGRA
 *             for (int x = 0; x < w; ++x) {
 *                 uint32_t s = src32[x];
 *                 dst8[x]    = SkComputeLuminance((s >> 16) & 0xFF, (s >> 8) & 0xFF, s & 0xFF);
 *             }
 *         } else {
 *             // RGBA
 *             for (int x = 0; x < w; ++x) {
 *                 uint32_t s = src32[x];
 *                 dst8[x]    = SkComputeLuminance(s & 0xFF, (s >> 8) & 0xFF, (s >> 16) & 0xFF);
 *             }
 *         }
 *         src32 = (const uint32_t*)((const char*)src32 + src.rowBytes());
 *         dst8 += dst->rowBytes();
 *     }
 * }
 * ```
 */
public fun copyToG8(dst: SkBitmap?, src: SkBitmap) {
  TODO("Implement copyToG8")
}

/**
 * C++ original:
 * ```cpp
 * bool equal_pixels(const SkImage* a, const SkImage* b) {
 *     SkASSERT_RELEASE(a);
 *     SkASSERT_RELEASE(b);
 *     // ensure that peekPixels will succeed
 *     auto imga = a->makeRasterImage(nullptr);
 *     auto imgb = b->makeRasterImage(nullptr);
 *
 *     SkPixmap pm0, pm1;
 *     if (!imga->peekPixels(&pm0)) {
 *         return false;
 *     }
 *     if (!imgb->peekPixels(&pm1)) {
 *         return false;
 *     }
 *     return equal_pixels(pm0, pm1);
 * }
 * ```
 */
public fun equalPixels(a: SkImage?, b: SkImage?): Boolean {
  TODO("Implement equalPixels")
}

/**
 * C++ original:
 * ```cpp
 * void ExtractPathsFromSKP(const char filepath[], std::function<PathSniffCallback> callback) {
 *     SkFILEStream stream(filepath);
 *     if (!stream.isValid()) {
 *         SkDebugf("ExtractPaths: invalid input file at \"%s\"\n", filepath);
 *         return;
 *     }
 *
 *     class PathSniffer : public SkCanvas {
 *     public:
 *         PathSniffer(std::function<PathSniffCallback> callback)
 *                 : SkCanvas(4096, 4096, nullptr)
 *                 , fPathSniffCallback(callback) {}
 *     private:
 *         void onDrawPath(const SkPath& path, const SkPaint& paint) override {
 *             fPathSniffCallback(this->getTotalMatrix(), path, paint);
 *         }
 *         std::function<PathSniffCallback> fPathSniffCallback;
 *     };
 *
 *     // We don't need to decode images etc, so we can pass nullptr for the deserial procs.
 *     sk_sp<SkPicture> skp = SkPicture::MakeFromStream(&stream, nullptr);
 *     if (!skp) {
 *         SkDebugf("ExtractPaths: couldn't load skp at \"%s\"\n", filepath);
 *         return;
 *     }
 *     PathSniffer pathSniffer(callback);
 *     skp->playback(&pathSniffer);
 * }
 * ```
 */
public fun extractPathsFromSKP(filepath: CharArray, callback: Function) {
  TODO("Implement extractPathsFromSKP")
}

/**
 * C++ original:
 * ```cpp
 * bool A8ComparePaths(const SkPath& a, const SkPath& b, A8CompareProc cmp) {
 *     const auto ra = a.computeTightBounds(),
 *                rb = b.computeTightBounds();
 *     if (ra.isEmpty() && rb.isEmpty()) {
 *         return true;
 *     }
 *
 *     const auto r = ra.makeOutset(1, 1);
 *     if (!r.contains(rb)) {
 *         return false;
 *     }
 *
 *     const auto ir = r.roundOut();
 *     const auto info = SkImageInfo::MakeA8(ir.width(), ir.height());
 *
 *     auto make_img = [&](const SkPath& path) {
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         auto surf = SkSurfaces::Raster(info);
 *         auto canvas = surf->getCanvas();
 *         canvas->translate(1 - ir.fLeft, 1 - ir.fTop);   // keep ~1 pixel margin
 *         canvas->drawPath(a, paint);
 *         return surf->makeImageSnapshot();
 *     };
 *     auto imga = make_img(a),
 *          imgb = make_img(b);
 *
 *     SkPixmap pma, pmb;
 *     SkAssertResult(imga->peekPixels(&pma));
 *     SkAssertResult(imgb->peekPixels(&pmb));
 *
 *     for (int y = 0; y < pma.height(); ++y) {
 *         for (int x = 0; x < pma.width(); ++x) {
 *             uint8_t pa = *pma.addr8(x, y),
 *                     pb = *pmb.addr8(x, y);
 *             if (pa != pb) {
 *                 if (!cmp(x, y, pa, pb)) {
 *                     return false;
 *                 }
 *             }
 *         }
 *     }
 *     return true;
 * }
 * ```
 */
public fun a8ComparePaths(
  a: SkPath,
  b: SkPath,
  cmp: A8CompareProc,
): Boolean {
  TODO("Implement a8ComparePaths")
}

/**
 * C++ original:
 * ```cpp
 * void drawArrow(SkCanvas* canvas, const SkPoint& a, const SkPoint& b, const SkPaint& paint) {
 *         canvas->translate(0.5, 0.5);
 *         canvas->drawLine(a, b, paint);
 *         canvas->save();
 *         canvas->translate(b.fX, b.fY);
 *         SkScalar angle = SkScalarATan2((b.fY - a.fY), b.fX - a.fX);
 *         canvas->rotate(angle * 180 / SK_ScalarPI - 90);
 *         // arrow head
 *         canvas->drawPath(arrowHead, paint);
 *         canvas->restore();
 *         canvas->restore();
 *     }
 * ```
 */
public fun drawArrow(
  canvas: SkCanvas?,
  a: SkPoint,
  b: SkPoint,
  paint: SkPaint,
) {
  TODO("Implement drawArrow")
}

/**
 * C++ original:
 * ```cpp
 * static SkString* str_append(SkString* str, const SkRect& r) {
 *     str->appendf(" [%g %g %g %g]", r.left(), r.top(), r.right(), r.bottom());
 *     return str;
 * }
 * ```
 */
public fun strAppend(str: String?, r: SkRect): String {
  TODO("Implement strAppend")
}

/**
 * C++ original:
 * ```cpp
 * void xlate_and_scale_to_bounds(SkCanvas* canvas, const SkRect& bounds) {
 *     const SkISize& size = canvas->getBaseLayerSize();
 *
 *     static const SkScalar kInsetFrac = 0.9f;  // Leave a border around object
 *
 *     canvas->translate(size.fWidth / 2.0f, size.fHeight / 2.0f);
 *     if (bounds.width() > bounds.height()) {
 *         canvas->scale(SkDoubleToScalar((kInsetFrac * size.fWidth) / bounds.width()),
 *                       SkDoubleToScalar((kInsetFrac * size.fHeight) / bounds.width()));
 *     } else {
 *         canvas->scale(SkDoubleToScalar((kInsetFrac * size.fWidth) / bounds.height()),
 *                       SkDoubleToScalar((kInsetFrac * size.fHeight) / bounds.height()));
 *     }
 *     canvas->translate(-bounds.centerX(), -bounds.centerY());
 * }
 * ```
 */
public fun xlateAndScaleToBounds(canvas: SkCanvas?, bounds: SkRect) {
  TODO("Implement xlateAndScaleToBounds")
}

/**
 * C++ original:
 * ```cpp
 * void render_path(SkCanvas* canvas, const SkPath& path) {
 *     canvas->clear(0xFFFFFFFF);
 *
 *     const SkRect& bounds = path.getBounds();
 *     if (bounds.isEmpty()) {
 *         return;
 *     }
 *
 *     SkAutoCanvasRestore acr(canvas, true);
 *     xlate_and_scale_to_bounds(canvas, bounds);
 *
 *     SkPaint p;
 *     p.setColor(SK_ColorBLACK);
 *     p.setStyle(SkPaint::kStroke_Style);
 *
 *     canvas->drawPath(path, p);
 * }
 * ```
 */
public fun renderPath(canvas: SkCanvas?, path: SkPath) {
  TODO("Implement renderPath")
}

/**
 * C++ original:
 * ```cpp
 * void render_region(SkCanvas* canvas, const SkRegion& region) {
 *     canvas->clear(0xFFFFFFFF);
 *
 *     const SkIRect& bounds = region.getBounds();
 *     if (bounds.isEmpty()) {
 *         return;
 *     }
 *
 *     SkAutoCanvasRestore acr(canvas, true);
 *     xlate_and_scale_to_bounds(canvas, SkRect::Make(bounds));
 *
 *     SkPaint p;
 *     p.setColor(SK_ColorBLACK);
 *     p.setStyle(SkPaint::kStroke_Style);
 *
 *     canvas->drawRegion(region, p);
 * }
 * ```
 */
public fun renderRegion(canvas: SkCanvas?, region: SkRegion) {
  TODO("Implement renderRegion")
}

/**
 * C++ original:
 * ```cpp
 * void render_rrect(SkCanvas* canvas, const SkRRect& rrect) {
 *     canvas->clear(0xFFFFFFFF);
 *     canvas->save();
 *
 *     const SkRect& bounds = rrect.getBounds();
 *
 *     xlate_and_scale_to_bounds(canvas, bounds);
 *
 *     SkPaint p;
 *     p.setColor(SK_ColorBLACK);
 *     p.setStyle(SkPaint::kStroke_Style);
 *
 *     canvas->drawRRect(rrect, p);
 *     canvas->restore();
 * }
 * ```
 */
public fun renderRrect(canvas: SkCanvas?, rrect: SkRRect) {
  TODO("Implement renderRrect")
}

/**
 * C++ original:
 * ```cpp
 * void render_drrect(SkCanvas* canvas, const SkRRect& outer, const SkRRect& inner) {
 *     canvas->clear(0xFFFFFFFF);
 *     canvas->save();
 *
 *     const SkRect& bounds = outer.getBounds();
 *
 *     xlate_and_scale_to_bounds(canvas, bounds);
 *
 *     SkPaint p;
 *     p.setColor(SK_ColorBLACK);
 *     p.setStyle(SkPaint::kStroke_Style);
 *
 *     canvas->drawDRRect(outer, inner, p);
 *     canvas->restore();
 * }
 * ```
 */
public fun renderDrrect(
  canvas: SkCanvas?,
  outer: SkRRect,
  `inner`: SkRRect,
) {
  TODO("Implement renderDrrect")
}

/**
 * C++ original:
 * ```cpp
 * void render_shadow(SkCanvas* canvas, const SkPath& path, SkDrawShadowRec rec) {
 *     canvas->clear(0xFFFFFFFF);
 *
 *     const SkRect& bounds = path.getBounds();
 *     if (bounds.isEmpty()) {
 *         return;
 *     }
 *
 *     SkAutoCanvasRestore acr(canvas, true);
 *     xlate_and_scale_to_bounds(canvas, bounds);
 *
 *     rec.fAmbientColor = SK_ColorBLACK;
 *     rec.fSpotColor    = SK_ColorBLACK;
 *     canvas->private_draw_shadow_rec(path, rec);
 * }
 * ```
 */
public fun renderShadow(
  canvas: SkCanvas?,
  path: SkPath,
  rec: SkDrawShadowRec,
) {
  TODO("Implement renderShadow")
}

/**
 * C++ original:
 * ```cpp
 * void apply_paint_blend_mode(const SkPaint& paint, SkJSONWriter& writer) {
 *     const auto mode = paint.getBlendMode_or(SkBlendMode::kSrcOver);
 *     if (mode != SkBlendMode::kSrcOver) {
 *         writer.appendCString(DEBUGCANVAS_ATTRIBUTE_BLENDMODE, SkBlendMode_Name(mode));
 *     }
 * }
 * ```
 */
public fun applyPaintBlendMode(paint: SkPaint, writer: SkJSONWriter) {
  TODO("Implement applyPaintBlendMode")
}

/**
 * C++ original:
 * ```cpp
 * static void make_json_rrect(SkJSONWriter& writer, const SkRRect& rrect) {
 *     writer.beginArray(nullptr, false);
 *     DrawCommand::MakeJsonRect(writer, rrect.rect());
 *     DrawCommand::MakeJsonPoint(writer, rrect.radii(SkRRect::kUpperLeft_Corner));
 *     DrawCommand::MakeJsonPoint(writer, rrect.radii(SkRRect::kUpperRight_Corner));
 *     DrawCommand::MakeJsonPoint(writer, rrect.radii(SkRRect::kLowerRight_Corner));
 *     DrawCommand::MakeJsonPoint(writer, rrect.radii(SkRRect::kLowerLeft_Corner));
 *     writer.endArray();
 * }
 * ```
 */
public fun makeJsonRrect(writer: SkJSONWriter, rrect: SkRRect) {
  TODO("Implement makeJsonRrect")
}

/**
 * C++ original:
 * ```cpp
 * static const char* clipop_name(SkClipOp op) {
 *     switch (op) {
 *         case SkClipOp::kDifference: return DEBUGCANVAS_CLIPOP_DIFFERENCE;
 *         case SkClipOp::kIntersect: return DEBUGCANVAS_CLIPOP_INTERSECT;
 *         default: SkASSERT(false); return "<invalid region op>";
 *     }
 * }
 * ```
 */
public fun clipopName(op: SkClipOp): Char {
  TODO("Implement clipopName")
}

/**
 * C++ original:
 * ```cpp
 * static const char* pointmode_name(SkCanvas::PointMode mode) {
 *     switch (mode) {
 *         case SkCanvas::kPoints_PointMode: return DEBUGCANVAS_POINTMODE_POINTS;
 *         case SkCanvas::kLines_PointMode: return DEBUGCANVAS_POINTMODE_LINES;
 *         case SkCanvas::kPolygon_PointMode: return DEBUGCANVAS_POINTMODE_POLYGON;
 *         default: SkASSERT(false); return "<invalid point mode>";
 *     }
 * }
 * ```
 */
public fun pointmodeName(mode: SkCanvas.PointMode): Char {
  TODO("Implement pointmodeName")
}

/**
 * C++ original:
 * ```cpp
 * static void store_scalar(SkJSONWriter& writer,
 *                          const char*   key,
 *                          SkScalar      value,
 *                          SkScalar      defaultValue) {
 *     if (value != defaultValue) {
 *         writer.appendFloat(key, value);
 *     }
 * }
 * ```
 */
public fun storeScalar(
  writer: SkJSONWriter,
  key: String?,
  `value`: SkScalar,
  defaultValue: SkScalar,
) {
  TODO("Implement storeScalar")
}

/**
 * C++ original:
 * ```cpp
 * static void store_bool(SkJSONWriter& writer, const char* key, bool value, bool defaultValue) {
 *     if (value != defaultValue) {
 *         writer.appendBool(key, value);
 *     }
 * }
 * ```
 */
public fun storeBool(
  writer: SkJSONWriter,
  key: String?,
  `value`: Boolean,
  defaultValue: Boolean,
) {
  TODO("Implement storeBool")
}

/**
 * C++ original:
 * ```cpp
 * static const char* alpha_type_name(SkAlphaType alphaType) {
 *     switch (alphaType) {
 *         case kOpaque_SkAlphaType: return DEBUGCANVAS_ALPHATYPE_OPAQUE;
 *         case kPremul_SkAlphaType: return DEBUGCANVAS_ALPHATYPE_PREMUL;
 *         case kUnpremul_SkAlphaType: return DEBUGCANVAS_ALPHATYPE_UNPREMUL;
 *         default: SkASSERT(false); return DEBUGCANVAS_ALPHATYPE_OPAQUE;
 *     }
 * }
 * ```
 */
public fun alphaTypeName(alphaType: SkAlphaType): Char {
  TODO("Implement alphaTypeName")
}

/**
 * C++ original:
 * ```cpp
 * static void apply_font_hinting(const SkFont& font, SkJSONWriter& writer) {
 *     SkFontHinting hinting = font.getHinting();
 *     if (hinting != SkPaintDefaults_Hinting) {
 *         switch (hinting) {
 *             case SkFontHinting::kNone:
 *                 writer.appendNString(DEBUGCANVAS_ATTRIBUTE_HINTING, DEBUGCANVAS_HINTING_NONE);
 *                 break;
 *             case SkFontHinting::kSlight:
 *                 writer.appendNString(DEBUGCANVAS_ATTRIBUTE_HINTING, DEBUGCANVAS_HINTING_SLIGHT);
 *                 break;
 *             case SkFontHinting::kNormal:
 *                 writer.appendNString(DEBUGCANVAS_ATTRIBUTE_HINTING, DEBUGCANVAS_HINTING_NORMAL);
 *                 break;
 *             case SkFontHinting::kFull:
 *                 writer.appendNString(DEBUGCANVAS_ATTRIBUTE_HINTING, DEBUGCANVAS_HINTING_FULL);
 *                 break;
 *         }
 *     }
 * }
 * ```
 */
public fun applyFontHinting(font: SkFont, writer: SkJSONWriter) {
  TODO("Implement applyFontHinting")
}

/**
 * C++ original:
 * ```cpp
 * static void apply_font_edging(const SkFont& font, SkJSONWriter& writer) {
 *     switch (font.getEdging()) {
 *         case SkFont::Edging::kAlias:
 *             writer.appendNString(DEBUGCANVAS_ATTRIBUTE_EDGING, DEBUGCANVAS_EDGING_ALIAS);
 *             break;
 *         case SkFont::Edging::kAntiAlias:
 *             writer.appendNString(DEBUGCANVAS_ATTRIBUTE_EDGING, DEBUGCANVAS_EDGING_ANTIALIAS);
 *             break;
 *         case SkFont::Edging::kSubpixelAntiAlias:
 *             writer.appendNString(DEBUGCANVAS_ATTRIBUTE_EDGING,
 *                                  DEBUGCANVAS_EDGING_SUBPIXELANTIALIAS);
 *             break;
 *     }
 * }
 * ```
 */
public fun applyFontEdging(font: SkFont, writer: SkJSONWriter) {
  TODO("Implement applyFontEdging")
}

/**
 * C++ original:
 * ```cpp
 * static void apply_paint_color(const SkPaint& paint, SkJSONWriter& writer) {
 *     SkColor color = paint.getColor();
 *     if (color != SK_ColorBLACK) {
 *         writer.appendName(DEBUGCANVAS_ATTRIBUTE_COLOR);
 *         DrawCommand::MakeJsonColor(writer, color);
 *     }
 * }
 * ```
 */
public fun applyPaintColor(paint: SkPaint, writer: SkJSONWriter) {
  TODO("Implement applyPaintColor")
}

/**
 * C++ original:
 * ```cpp
 * static void apply_paint_style(const SkPaint& paint, SkJSONWriter& writer) {
 *     SkPaint::Style style = paint.getStyle();
 *     if (style != SkPaint::kFill_Style) {
 *         switch (style) {
 *             case SkPaint::kStroke_Style: {
 *                 writer.appendNString(DEBUGCANVAS_ATTRIBUTE_STYLE, DEBUGCANVAS_STYLE_STROKE);
 *                 break;
 *             }
 *             case SkPaint::kStrokeAndFill_Style: {
 *                 writer.appendNString(DEBUGCANVAS_ATTRIBUTE_STYLE, DEBUGCANVAS_STYLE_STROKEANDFILL);
 *                 break;
 *             }
 *             default: SkASSERT(false);
 *         }
 *     }
 * }
 * ```
 */
public fun applyPaintStyle(paint: SkPaint, writer: SkJSONWriter) {
  TODO("Implement applyPaintStyle")
}

/**
 * C++ original:
 * ```cpp
 * static void apply_paint_cap(const SkPaint& paint, SkJSONWriter& writer) {
 *     SkPaint::Cap cap = paint.getStrokeCap();
 *     if (cap != SkPaint::kDefault_Cap) {
 *         switch (cap) {
 *             case SkPaint::kButt_Cap:
 *                 writer.appendNString(DEBUGCANVAS_ATTRIBUTE_CAP, DEBUGCANVAS_CAP_BUTT);
 *                 break;
 *             case SkPaint::kRound_Cap:
 *                 writer.appendNString(DEBUGCANVAS_ATTRIBUTE_CAP, DEBUGCANVAS_CAP_ROUND);
 *                 break;
 *             case SkPaint::kSquare_Cap:
 *                 writer.appendNString(DEBUGCANVAS_ATTRIBUTE_CAP, DEBUGCANVAS_CAP_SQUARE);
 *                 break;
 *             default: SkASSERT(false);
 *         }
 *     }
 * }
 * ```
 */
public fun applyPaintCap(paint: SkPaint, writer: SkJSONWriter) {
  TODO("Implement applyPaintCap")
}

/**
 * C++ original:
 * ```cpp
 * static void apply_paint_join(const SkPaint& paint, SkJSONWriter& writer) {
 *     SkPaint::Join join = paint.getStrokeJoin();
 *     if (join != SkPaint::kDefault_Join) {
 *         switch (join) {
 *             case SkPaint::kMiter_Join:
 *                 writer.appendNString(DEBUGCANVAS_ATTRIBUTE_STROKEJOIN, DEBUGCANVAS_MITER_JOIN);
 *                 break;
 *             case SkPaint::kRound_Join:
 *                 writer.appendNString(DEBUGCANVAS_ATTRIBUTE_STROKEJOIN, DEBUGCANVAS_ROUND_JOIN);
 *                 break;
 *             case SkPaint::kBevel_Join:
 *                 writer.appendNString(DEBUGCANVAS_ATTRIBUTE_STROKEJOIN, DEBUGCANVAS_BEVEL_JOIN);
 *                 break;
 *             default: SkASSERT(false);
 *         }
 *     }
 * }
 * ```
 */
public fun applyPaintJoin(paint: SkPaint, writer: SkJSONWriter) {
  TODO("Implement applyPaintJoin")
}

/**
 * C++ original:
 * ```cpp
 * static void apply_paint_maskfilter(const SkPaint&  paint,
 *                                    SkJSONWriter&   writer,
 *                                    UrlDataManager& urlDataManager) {
 *     SkMaskFilter* maskFilter = paint.getMaskFilter();
 *     if (maskFilter != nullptr) {
 *         SkMaskFilterBase::BlurRec blurRec;
 *         if (as_MFB(maskFilter)->asABlur(&blurRec)) {
 *             writer.beginObject(DEBUGCANVAS_ATTRIBUTE_BLUR);
 *             writer.appendFloat(DEBUGCANVAS_ATTRIBUTE_SIGMA, blurRec.fSigma);
 *             switch (blurRec.fStyle) {
 *                 case SkBlurStyle::kNormal_SkBlurStyle:
 *                     writer.appendNString(DEBUGCANVAS_ATTRIBUTE_STYLE, DEBUGCANVAS_BLURSTYLE_NORMAL);
 *                     break;
 *                 case SkBlurStyle::kSolid_SkBlurStyle:
 *                     writer.appendNString(DEBUGCANVAS_ATTRIBUTE_STYLE, DEBUGCANVAS_BLURSTYLE_SOLID);
 *                     break;
 *                 case SkBlurStyle::kOuter_SkBlurStyle:
 *                     writer.appendNString(DEBUGCANVAS_ATTRIBUTE_STYLE, DEBUGCANVAS_BLURSTYLE_OUTER);
 *                     break;
 *                 case SkBlurStyle::kInner_SkBlurStyle:
 *                     writer.appendNString(DEBUGCANVAS_ATTRIBUTE_STYLE, DEBUGCANVAS_BLURSTYLE_INNER);
 *                     break;
 *                 default: SkASSERT(false);
 *             }
 *             writer.endObject();  // blur
 *         } else {
 *             writer.beginObject(DEBUGCANVAS_ATTRIBUTE_MASKFILTER);
 *             DrawCommand::flatten(maskFilter, writer, urlDataManager);
 *             writer.endObject();  // maskFilter
 *         }
 *     }
 * }
 * ```
 */
public fun applyPaintMaskfilter(
  paint: SkPaint,
  writer: SkJSONWriter,
  urlDataManager: UrlDataManager,
) {
  TODO("Implement applyPaintMaskfilter")
}

/**
 * C++ original:
 * ```cpp
 * static void apply_paint_patheffect(const SkPaint&  paint,
 *                                    SkJSONWriter&   writer,
 *                                    UrlDataManager& urlDataManager) {
 *     SkPathEffect* pathEffect = paint.getPathEffect();
 *     if (pathEffect != nullptr) {
 *         if (const auto dashInfo = as_PEB(pathEffect)->asADash()) {
 *             writer.beginObject(DEBUGCANVAS_ATTRIBUTE_DASHING);
 *             writer.beginArray(DEBUGCANVAS_ATTRIBUTE_INTERVALS, false);
 *             for (SkScalar value : dashInfo->fIntervals) {
 *                 writer.appendFloat(value);
 *             }
 *             writer.endArray();  // intervals
 *             writer.appendFloat(DEBUGCANVAS_ATTRIBUTE_PHASE, dashInfo->fPhase);
 *             writer.endObject();  // dashing
 *         } else {
 *             writer.beginObject(DEBUGCANVAS_ATTRIBUTE_PATHEFFECT);
 *             DrawCommand::flatten(pathEffect, writer, urlDataManager);
 *             writer.endObject();  // pathEffect
 *         }
 *     }
 * }
 * ```
 */
public fun applyPaintPatheffect(
  paint: SkPaint,
  writer: SkJSONWriter,
  urlDataManager: UrlDataManager,
) {
  TODO("Implement applyPaintPatheffect")
}

/**
 * C++ original:
 * ```cpp
 * static void apply_font_typeface(const SkFont&   font,
 *                                 SkJSONWriter&   writer,
 *                                 UrlDataManager& urlDataManager) {
 *     SkTypeface* typeface = font.getTypeface();
 *     if (typeface != nullptr) {
 *         writer.beginObject(DEBUGCANVAS_ATTRIBUTE_TYPEFACE);
 *         SkDynamicMemoryWStream buffer;
 *         typeface->serialize(&buffer);
 *         void* data = sk_malloc_throw(buffer.bytesWritten());
 *         buffer.copyTo(data);
 *         SkString url = encode_data(
 *                 data, buffer.bytesWritten(), "application/octet-stream", urlDataManager);
 *         writer.appendString(DEBUGCANVAS_ATTRIBUTE_DATA, url);
 *         sk_free(data);
 *         writer.endObject();
 *     }
 * }
 * ```
 */
public fun applyFontTypeface(
  font: SkFont,
  writer: SkJSONWriter,
  urlDataManager: UrlDataManager,
) {
  TODO("Implement applyFontTypeface")
}

/**
 * C++ original:
 * ```cpp
 * static void apply_flattenable(const char*     key,
 *                               SkFlattenable*  flattenable,
 *                               SkJSONWriter&   writer,
 *                               UrlDataManager& urlDataManager) {
 *     if (flattenable != nullptr) {
 *         writer.beginObject(key);
 *         DrawCommand::flatten(flattenable, writer, urlDataManager);
 *         writer.endObject();
 *     }
 * }
 * ```
 */
public fun applyFlattenable(
  key: String?,
  flattenable: SkFlattenable?,
  writer: SkJSONWriter,
  urlDataManager: UrlDataManager,
) {
  TODO("Implement applyFlattenable")
}

/**
 * C++ original:
 * ```cpp
 * static void MakeJsonFont(const SkFont& font, SkJSONWriter& writer, UrlDataManager& urlDataManager) {
 *     writer.beginObject();
 *     store_bool(writer, DEBUGCANVAS_ATTRIBUTE_FAKEBOLDTEXT, font.isEmbolden(), false);
 *     store_bool(writer, DEBUGCANVAS_ATTRIBUTE_LINEARTEXT, font.isLinearMetrics(), false);
 *     store_bool(writer, DEBUGCANVAS_ATTRIBUTE_SUBPIXELTEXT, font.isSubpixel(), false);
 *     store_bool(writer, DEBUGCANVAS_ATTRIBUTE_EMBEDDEDBITMAPTEXT, font.isEmbeddedBitmaps(), false);
 *     store_bool(writer, DEBUGCANVAS_ATTRIBUTE_AUTOHINTING, font.isForceAutoHinting(), false);
 *
 *     store_scalar(writer, DEBUGCANVAS_ATTRIBUTE_TEXTSIZE, font.getSize(), SkPaintDefaults_TextSize);
 *     store_scalar(writer, DEBUGCANVAS_ATTRIBUTE_TEXTSCALEX, font.getScaleX(), SK_Scalar1);
 *     store_scalar(writer, DEBUGCANVAS_ATTRIBUTE_TEXTSCALEX, font.getSkewX(), 0.0f);
 *     apply_font_edging(font, writer);
 *     apply_font_hinting(font, writer);
 *     apply_font_typeface(font, writer, urlDataManager);
 *     writer.endObject();  // font
 * }
 * ```
 */
public fun makeJsonFont(
  font: SkFont,
  writer: SkJSONWriter,
  urlDataManager: UrlDataManager,
) {
  TODO("Implement makeJsonFont")
}

/**
 * C++ original:
 * ```cpp
 * void writeMatrixType(SkJSONWriter& writer, const SkMatrix& m) {
 *         switch (m.getType()) {
 *             case SkMatrix::kTranslate_Mask:
 *                 writer.appendNString(DEBUGCANVAS_ATTRIBUTE_SHORTDESC, " (translate)");
 *                 break;
 *             case SkMatrix::kScale_Mask:
 *                 writer.appendNString(DEBUGCANVAS_ATTRIBUTE_SHORTDESC, " (scale)");
 *                 break;
 *             case SkMatrix::kAffine_Mask:
 *                 writer.appendNString(DEBUGCANVAS_ATTRIBUTE_SHORTDESC, " (rotation or skew)");
 *                 break;
 *             case SkMatrix::kPerspective_Mask:
 *                 writer.appendNString(DEBUGCANVAS_ATTRIBUTE_SHORTDESC, " (perspective)");
 *                 break;
 *             default:
 *                 break;
 *         }
 *     }
 * ```
 */
public fun writeMatrixType(writer: SkJSONWriter, m: SkMatrix) {
  TODO("Implement writeMatrixType")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkTypeface> PlanetTypeface() {
 *     static const sk_sp<SkTypeface> planetTypeface = []() {
 *         const char* filename;
 * #if defined(SK_BUILD_FOR_WIN)
 *         filename = "fonts/planetcolr.ttf";
 * #elif defined(SK_BUILD_FOR_MAC) || defined(SK_BUILD_FOR_IOS)
 *         filename = "fonts/planetsbix.ttf";
 * #else
 *         filename = "fonts/planetcbdt.ttf";
 * #endif
 *         sk_sp<SkTypeface> typeface = CreateTypefaceFromResource(filename);
 *         if (typeface) {
 *             return typeface;
 *         }
 *         return CreateTestTypeface("Planet", SkFontStyle());
 *     }();
 *     return planetTypeface;
 * }
 * ```
 */
public fun planetTypeface(): SkSp<SkTypeface> {
  TODO("Implement planetTypeface")
}

/**
 * C++ original:
 * ```cpp
 * EmojiTestSample EmojiSample(EmojiFontFormat format) {
 *     EmojiTestSample sample;
 *     sample.sampleText = "\U0001F600 \u2662";  // 😀  
 *     switch (format) {
 *         case EmojiFontFormat::Cbdt:
 *             sample.typeface = CreateTypefaceFromResource("fonts/cbdt.ttf");
 *             break;
 *         case EmojiFontFormat::Sbix:
 *             sample.typeface = CreateTypefaceFromResource("fonts/sbix.ttf");
 *             break;
 *         case EmojiFontFormat::ColrV0:
 *             sample.typeface = CreateTypefaceFromResource("fonts/colr.ttf");
 *             break;
 *         case EmojiFontFormat::Svg:
 *             sample.typeface = CreateTypefaceFromResource("fonts/SampleSVG.ttf");
 *             sample.sampleText = "abcdefghij";
 *             break;
 *         case EmojiFontFormat::Test:
 *             sample.typeface = CreatePortableTypeface("Emoji", SkFontStyle());
 *     }
 *     return sample;
 * }
 * ```
 */
public fun emojiSample(format: EmojiFontFormat): EmojiTestSample {
  TODO("Implement emojiSample")
}

/**
 * C++ original:
 * ```cpp
 * SkString NameForFontFormat(EmojiFontFormat format) {
 *     switch (format) {
 *         case EmojiFontFormat::Cbdt:
 *             return SkString("cbdt");
 *         case EmojiFontFormat::Sbix:
 *             return SkString("sbix");
 *         case EmojiFontFormat::ColrV0:
 *             return SkString("colrv0");
 *         case EmojiFontFormat::Test:
 *             return SkString("test");
 *         case EmojiFontFormat::Svg:
 *             return SkString("svg");
 *     }
 *     return SkString();
 * }
 * ```
 */
public fun nameForFontFormat(format: EmojiFontFormat): String {
  TODO("Implement nameForFontFormat")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkTypeface> SampleUserTypeface() {
 *     SkCustomTypefaceBuilder builder;
 *     SkFont font;
 *     const float upem = 200;
 *
 *     {
 *         SkFontMetrics metrics;
 *         metrics.fFlags = 0;
 *         metrics.fTop = -200;
 *         metrics.fAscent = -150;
 *         metrics.fDescent = 50;
 *         metrics.fBottom = -75;
 *         metrics.fLeading = 10;
 *         metrics.fAvgCharWidth = 150;
 *         metrics.fMaxCharWidth = 300;
 *         metrics.fXMin = -20;
 *         metrics.fXMax = 290;
 *         metrics.fXHeight = -100;
 *         metrics.fCapHeight = 0;
 *         metrics.fUnderlineThickness = 5;
 *         metrics.fUnderlinePosition = 2;
 *         metrics.fStrikeoutThickness = 5;
 *         metrics.fStrikeoutPosition = -50;
 *         builder.setMetrics(metrics, 1.0f/upem);
 *     }
 *     builder.setFontStyle(SkFontStyle(367, 3, SkFontStyle::kOblique_Slant));
 *
 *     const SkMatrix scale = SkMatrix::Scale(1.0f/upem, 1.0f/upem);
 *     for (SkGlyphID index = 0; index <= 67; ++index) {
 *         SkScalar width;
 *         width = 100;
 *
 *         builder.setGlyph(index, width/upem, SkPath::Circle(50, -50, 75).makeTransform(scale));
 *     }
 *
 *     return builder.detach();
 * }
 * ```
 */
public fun sampleUserTypeface(): SkSp<SkTypeface> {
  TODO("Implement sampleUserTypeface")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkTypeface> CreatePortableTypeface(const char* name, SkFontStyle style) {
 *     static sk_sp<SkFontMgr> portableFontMgr = MakePortableFontMgr();
 *     SkASSERT_RELEASE(portableFontMgr);
 *     sk_sp<SkTypeface> face = portableFontMgr->legacyMakeTypeface(name, style);
 *     SkASSERT_RELEASE(face);
 *     return face;
 * }
 * ```
 */
public fun createPortableTypeface(name: String?, style: SkFontStyle): SkSp<SkTypeface> {
  TODO("Implement createPortableTypeface")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkTypeface> DefaultPortableTypeface() {
 *     // At last check, the default typeface is a serif font.
 *     sk_sp<SkTypeface> face = CreatePortableTypeface(nullptr, SkFontStyle());
 *     SkASSERT_RELEASE(face);
 *     return face;
 * }
 * ```
 */
public fun defaultPortableTypeface(): SkSp<SkTypeface> {
  TODO("Implement defaultPortableTypeface")
}

/**
 * C++ original:
 * ```cpp
 * SkFont DefaultPortableFont() {
 *     return SkFont(DefaultPortableTypeface(), 12);
 * }
 * ```
 */
public fun defaultPortableFont(): SkFont {
  TODO("Implement defaultPortableFont")
}

/**
 * C++ original:
 * ```cpp
 * SkBitmap CreateStringBitmap(int w, int h, SkColor c, int x, int y, int textSize,
 *                             const char* str) {
 *     SkBitmap bitmap;
 *     bitmap.allocN32Pixels(w, h);
 *     SkCanvas canvas(bitmap);
 *
 *     SkPaint paint;
 *     paint.setColor(c);
 *
 *     SkFont font(DefaultPortableTypeface(), textSize);
 *
 *     canvas.clear(0x00000000);
 *     canvas.drawSimpleText(str,
 *                           strlen(str),
 *                           SkTextEncoding::kUTF8,
 *                           SkIntToScalar(x),
 *                           SkIntToScalar(y),
 *                           font,
 *                           paint);
 *
 *     // Tag data as sRGB (without doing any color space conversion). Color-space aware configs
 *     // will process this correctly but legacy configs will render as if this returned N32.
 *     SkBitmap result;
 *     result.setInfo(SkImageInfo::MakeS32(w, h, kPremul_SkAlphaType));
 *     result.setPixelRef(sk_ref_sp(bitmap.pixelRef()), 0, 0);
 *     return result;
 * }
 * ```
 */
public fun createStringBitmap(
  w: Int,
  h: Int,
  c: SkColor,
  x: Int,
  y: Int,
  textSize: Int,
  str: String?,
): SkBitmap {
  TODO("Implement createStringBitmap")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkImage> CreateStringImage(int w, int h, SkColor c, int x, int y, int textSize,
 *                                  const char* str) {
 *     return CreateStringBitmap(w, h, c, x, y, textSize, str).asImage();
 * }
 * ```
 */
public fun createStringImage(
  w: Int,
  h: Int,
  c: SkColor,
  x: Int,
  y: Int,
  textSize: Int,
  str: String?,
): SkSp<SkImage> {
  TODO("Implement createStringImage")
}

/**
 * C++ original:
 * ```cpp
 * std::unique_ptr<SkFontScanner> TestFontScanner() {
 * #if defined(SK_TYPEFACE_FACTORY_FONTATIONS)
 *     if (FLAGS_fontations) {
 *         auto result = SkFontScanner_Make_Fontations();
 *         if (result) {
 *             return result;
 *         }
 *     }
 * #endif
 * #if defined(SK_TYPEFACE_FACTORY_FREETYPE)
 *     return SkFontScanner_Make_FreeType();
 * #else
 *     return nullptr;
 * #endif
 * }
 * ```
 */
public fun testFontScanner(): SkFontScanner? {
  TODO("Implement testFontScanner")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkFontMgr> TestFontMgr() {
 *     static sk_sp<SkFontMgr> mgr;
 *     static SkOnce once;
 *     once([] {
 *         if (!FLAGS_nativeFonts) {
 *             mgr = MakePortableFontMgr();
 *         }
 * #if defined(SK_BUILD_FOR_WIN) && defined(SK_FONTMGR_GDI_AVAILABLE)
 *         else if (FLAGS_gdi) {
 *             mgr = SkFontMgr_New_GDI();
 *         }
 * #endif
 * #if defined(SK_BUILD_FOR_ANDROID) && defined(SK_FONTMGR_ANDROID_NDK_AVAILABLE) && defined(SK_TYPEFACE_SCANNER_AVAILABLE)
 *         else if (FLAGS_androidndkfonts) {
 *             mgr = SkFontMgr_New_AndroidNDK(false, TestFontScanner());
 *         }
 * #endif
 *         else {
 * #if defined(SK_BUILD_FOR_ANDROID) && defined(SK_FONTMGR_ANDROID_AVAILABLE) && defined(SK_TYPEFACE_SCANNER_AVAILABLE)
 *             mgr = SkFontMgr_New_Android(nullptr, TestFontScanner());
 * #elif defined(SK_BUILD_FOR_WIN) && defined(SK_FONTMGR_DIRECTWRITE_AVAILABLE)
 *             mgr = SkFontMgr_New_DirectWrite();
 * #elif defined(SK_FONTMGR_CORETEXT_AVAILABLE) && (defined(SK_BUILD_FOR_IOS) || \
 *                                                 defined(SK_BUILD_FOR_MAC))
 *             mgr = SkFontMgr_New_CoreText(nullptr);
 * #elif defined(SK_FONTMGR_FONTCONFIG_AVAILABLE) && defined(SK_TYPEFACE_SCANNER_AVAILABLE)
 *             mgr = SkFontMgr_New_FontConfig(nullptr, TestFontScanner());
 * #elif defined(SK_FONTMGR_FREETYPE_DIRECTORY_AVAILABLE)
 *             // In particular, this is used on ChromeOS, which is Linux-like but doesn't have
 *             // FontConfig.
 *             mgr = SkFontMgr_New_Custom_Directory(SK_FONT_FILE_PREFIX);
 * #elif defined(SK_FONTMGR_FREETYPE_EMPTY_AVAILABLE)
 *             mgr = SkFontMgr_New_Custom_Empty();
 * #else
 *             mgr = SkFontMgr::RefEmpty();
 * #endif
 *         }
 *         SkASSERT_RELEASE(mgr);
 *     });
 *     return mgr;
 * }
 * ```
 */
public fun testFontMgr(): SkSp<SkFontMgr> {
  TODO("Implement testFontMgr")
}

/**
 * C++ original:
 * ```cpp
 * bool FontMgrIsGDI() {
 *     if (!FLAGS_nativeFonts) {
 *         return false;
 *     }
 * #if defined(SK_BUILD_FOR_WIN)
 *     if (FLAGS_gdi) {
 *         return true;
 *     }
 * #endif
 *     return false;
 * }
 * ```
 */
public fun fontMgrIsGDI(): Boolean {
  TODO("Implement fontMgrIsGDI")
}

/**
 * C++ original:
 * ```cpp
 * void UsePortableFontMgr() { FLAGS_nativeFonts = false; }
 * ```
 */
public fun usePortableFontMgr() {
  TODO("Implement usePortableFontMgr")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkTypeface> DefaultTypeface() {
 *     return CreateTestTypeface(nullptr, SkFontStyle());
 * }
 * ```
 */
public fun defaultTypeface(): SkSp<SkTypeface> {
  TODO("Implement defaultTypeface")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkTypeface> CreateTestTypeface(const char* name, SkFontStyle style) {
 *     sk_sp<SkFontMgr> fm = TestFontMgr();
 *     SkASSERT_RELEASE(fm);
 *     sk_sp<SkTypeface> face = fm->legacyMakeTypeface(name, style);
 *     if (face) {
 *         return face;
 *     }
 *     return CreatePortableTypeface(name, style);
 * }
 * ```
 */
public fun createTestTypeface(name: String?, style: SkFontStyle): SkSp<SkTypeface> {
  TODO("Implement createTestTypeface")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkTypeface> CreateTypefaceFromResource(const char* resource, int ttcIndex) {
 *     sk_sp<SkFontMgr> fm = TestFontMgr();
 *     SkASSERT_RELEASE(fm);
 *     return fm->makeFromStream(GetResourceAsStream(resource), ttcIndex);
 * }
 * ```
 */
public fun createTypefaceFromResource(resource: String?, ttcIndex: Int): SkSp<SkTypeface> {
  TODO("Implement createTypefaceFromResource")
}

/**
 * C++ original:
 * ```cpp
 * SkFont DefaultFont() {
 *     return SkFont(DefaultTypeface(), 12);
 * }
 * ```
 */
public fun defaultFont(): SkFont {
  TODO("Implement defaultFont")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkFontMgr> MakePortableFontMgr() { return sk_make_sp<FontMgr>(); }
 * ```
 */
public fun makePortableFontMgr(): SkSp<SkFontMgr> {
  TODO("Implement makePortableFontMgr")
}

/**
 * C++ original:
 * ```cpp
 * void convert_noninflect_cubic_to_quads(const SkPoint            p[4],
 *                                        SkScalar                 toleranceSqd,
 *                                        TArray<SkPoint, true>* quads,
 *                                        int                      sublevel = 0) {
 *     // Notation: Point a is always p[0]. Point b is p[1] unless p[1] == p[0], in which case it is
 *     // p[2]. Point d is always p[3]. Point c is p[2] unless p[2] == p[3], in which case it is p[1].
 *
 *     SkVector ab = p[1] - p[0];
 *     SkVector dc = p[2] - p[3];
 *
 *     if (SkPointPriv::LengthSqd(ab) < SK_ScalarNearlyZero) {
 *         if (SkPointPriv::LengthSqd(dc) < SK_ScalarNearlyZero) {
 *             SkPoint* degQuad = quads->push_back_n(3);
 *             degQuad[0]       = p[0];
 *             degQuad[1]       = p[0];
 *             degQuad[2]       = p[3];
 *             return;
 *         }
 *         ab = p[2] - p[0];
 *     }
 *     if (SkPointPriv::LengthSqd(dc) < SK_ScalarNearlyZero) {
 *         dc = p[1] - p[3];
 *     }
 *
 *     static const SkScalar kLengthScale = 3 * SK_Scalar1 / 2;
 *     static const int      kMaxSubdivs  = 10;
 *
 *     ab.scale(kLengthScale);
 *     dc.scale(kLengthScale);
 *
 *     // e0 and e1 are extrapolations along vectors ab and dc.
 *     SkVector c0 = p[0];
 *     c0 += ab;
 *     SkVector c1 = p[3];
 *     c1 += dc;
 *
 *     SkScalar dSqd = sublevel > kMaxSubdivs ? 0 : SkPointPriv::DistanceToSqd(c0, c1);
 *     if (dSqd < toleranceSqd) {
 *         SkPoint cAvg = c0;
 *         cAvg += c1;
 *         cAvg.scale(SK_ScalarHalf);
 *
 *         SkPoint* pts = quads->push_back_n(3);
 *         pts[0]       = p[0];
 *         pts[1]       = cAvg;
 *         pts[2]       = p[3];
 *         return;
 *     }
 *     SkPoint choppedPts[7];
 *     SkChopCubicAtHalf(p, choppedPts);
 *     convert_noninflect_cubic_to_quads(choppedPts + 0, toleranceSqd, quads, sublevel + 1);
 *     convert_noninflect_cubic_to_quads(choppedPts + 3, toleranceSqd, quads, sublevel + 1);
 * }
 * ```
 */
public fun convertNoninflectCubicToQuads(
  p: Array<SkPoint>,
  toleranceSqd: SkScalar,
  quads: TArraytrue<SkPoint>,
  sublevel: Int = TODO(),
) {
  TODO("Implement convertNoninflectCubicToQuads")
}

/**
 * C++ original:
 * ```cpp
 * void convertCubicToQuads(const SkPoint p[4], SkScalar tolScale, TArray<SkPoint, true>* quads) {
 *     if (!p[0].isFinite() || !p[1].isFinite() || !p[2].isFinite() || !p[3].isFinite()) {
 *         return;
 *     }
 *     SkPoint chopped[10];
 *     int     count = SkChopCubicAtInflections(p, chopped);
 *
 *     const SkScalar tolSqd = SkScalarSquare(tolScale);
 *
 *     for (int i = 0; i < count; ++i) {
 *         SkPoint* cubic = chopped + 3 * i;
 *         convert_noninflect_cubic_to_quads(cubic, tolSqd, quads);
 *     }
 * }
 * ```
 */
public fun convertCubicToQuads(
  p: Array<SkPoint>,
  tolScale: SkScalar,
  quads: TArraytrue<SkPoint>,
) {
  TODO("Implement convertCubicToQuads")
}

/**
 * C++ original:
 * ```cpp
 * SkPath path_to_quads(const SkPath& path) {
 *     SkPathBuilder quadPath;
 *     TArray<SkPoint, true> qPts;
 *     SkAutoConicToQuads      converter;
 *     const SkPoint*          quadPts;
 *     for (auto [verb, pts, w] : SkPathPriv::Iterate(path)) {
 *         switch (verb) {
 *             case SkPathVerb::kMove: quadPath.moveTo(pts[0]); break;
 *             case SkPathVerb::kLine: quadPath.lineTo(pts[1]); break;
 *             case SkPathVerb::kQuad:
 *                 quadPath.quadTo(pts[1].fX, pts[1].fY, pts[2].fX, pts[2].fY);
 *                 break;
 *             case SkPathVerb::kCubic:
 *                 qPts.clear();
 *                 convertCubicToQuads(pts, SK_Scalar1, &qPts);
 *                 for (int i = 0; i < qPts.size(); i += 3) {
 *                     quadPath.quadTo(
 *                             qPts[i + 1].fX, qPts[i + 1].fY, qPts[i + 2].fX, qPts[i + 2].fY);
 *                 }
 *                 break;
 *             case SkPathVerb::kConic:
 *                 quadPts = converter.computeQuads(pts, *w, SK_Scalar1);
 *                 for (int i = 0; i < converter.countQuads(); ++i) {
 *                     quadPath.quadTo(quadPts[i * 2 + 1], quadPts[i * 2 + 2]);
 *                 }
 *                 break;
 *             case SkPathVerb::kClose: quadPath.close(); break;
 *         }
 *     }
 *     return quadPath.detach();
 * }
 * ```
 */
public fun pathToQuads(path: SkPath): SkPath {
  TODO("Implement pathToQuads")
}

/**
 * C++ original:
 * ```cpp
 * void WriteTrace(const SkSL::DebugTracePriv& src, SkWStream* w) {
 *     SkJSONWriter json(w);
 *
 *     json.beginObject();  // root
 *     json.appendNString("version", kTraceVersion);
 *     json.beginArray("source");
 *
 *     for (const std::string& line : src.fSource) {
 *         json.appendString(line);
 *     }
 *
 *     json.endArray();  // code
 *     json.beginArray("slots");
 *
 *     for (size_t index = 0; index < src.fSlotInfo.size(); ++index) {
 *         const SkSL::SlotDebugInfo& info = src.fSlotInfo[index];
 *
 *         json.beginObject();
 *         json.appendString("name", info.name.data(), info.name.size());
 *         json.appendS32("columns", info.columns);
 *         json.appendS32("rows", info.rows);
 *         json.appendS32("index", info.componentIndex);
 *         if (info.groupIndex != info.componentIndex) {
 *             json.appendS32("groupIdx", info.groupIndex);
 *         }
 *         json.appendS32("kind", (int)info.numberKind);
 *         json.appendS32("line", info.line);
 *         if (info.fnReturnValue >= 0) {
 *             json.appendS32("retval", info.fnReturnValue);
 *         }
 *         json.endObject();
 *     }
 *
 *     json.endArray();  // slots
 *     json.beginArray("functions");
 *
 *     for (size_t index = 0; index < src.fFuncInfo.size(); ++index) {
 *         const SkSL::FunctionDebugInfo& info = src.fFuncInfo[index];
 *
 *         json.beginObject();
 *         json.appendString("name", info.name);
 *         json.endObject();
 *     }
 *
 *     json.endArray();  // functions
 *     json.beginArray("trace");
 *
 *     for (size_t index = 0; index < src.fTraceInfo.size(); ++index) {
 *         const SkSL::TraceInfo& trace = src.fTraceInfo[index];
 *         json.beginArray();
 *         json.appendS32((int)trace.op);
 *
 *         // Skip trailing zeros in the data (since most ops only use one value).
 *         int lastDataIdx = std::size(trace.data) - 1;
 *         while (lastDataIdx >= 0 && !trace.data[lastDataIdx]) {
 *             --lastDataIdx;
 *         }
 *         for (int dataIdx = 0; dataIdx <= lastDataIdx; ++dataIdx) {
 *             json.appendS32(trace.data[dataIdx]);
 *         }
 *         json.endArray();
 *     }
 *
 *     json.endArray();   // trace
 *     json.endObject();  // root
 *     json.flush();
 * }
 * ```
 */
public fun writeTrace(src: DebugTracePriv, w: SkWStream?) {
  TODO("Implement writeTrace")
}

/**
 * C++ original:
 * ```cpp
 * sk_sp<SkSL::DebugTracePriv> ReadTrace(SkStream* r) {
 *     sk_sp<SkData> data = SkStreamPriv::CopyStreamToData(r);
 *     skjson::DOM json(reinterpret_cast<const char*>(data->bytes()), data->size());
 *     const skjson::ObjectValue* root = json.root();
 *     if (!root) {
 *         return nullptr;
 *     }
 *
 *     const skjson::StringValue* version = (*root)["version"];
 *     if (!version || version->str() != kTraceVersion) {
 *         return nullptr;
 *     }
 *
 *     const skjson::ArrayValue* source = (*root)["source"];
 *     if (!source) {
 *         return nullptr;
 *     }
 *
 *     auto dst = sk_make_sp<SkSL::DebugTracePriv>();
 *     for (const skjson::StringValue* line : *source) {
 *         if (!line) {
 *             return nullptr;
 *         }
 *         dst->fSource.push_back(line->begin());
 *     }
 *
 *     const skjson::ArrayValue* slots = (*root)["slots"];
 *     if (!slots) {
 *         return nullptr;
 *     }
 *     for (const skjson::ObjectValue* element : *slots) {
 *         if (!element) {
 *             return nullptr;
 *         }
 *
 *         // Grow the slot array to hold this element.
 *         dst->fSlotInfo.push_back({});
 *         SkSL::SlotDebugInfo& info = dst->fSlotInfo.back();
 *
 *         // Populate the SlotInfo with our JSON data.
 *         const skjson::StringValue* name     = (*element)["name"];
 *         const skjson::NumberValue* columns  = (*element)["columns"];
 *         const skjson::NumberValue* rows     = (*element)["rows"];
 *         const skjson::NumberValue* index    = (*element)["index"];
 *         const skjson::NumberValue* groupIdx = (*element)["groupIdx"];
 *         const skjson::NumberValue* kind     = (*element)["kind"];
 *         const skjson::NumberValue* line     = (*element)["line"];
 *         const skjson::NumberValue* retval   = (*element)["retval"];
 *         if (!name || !columns || !rows || !index || !kind || !line) {
 *             return nullptr;
 *         }
 *
 *         info.name = name->begin();
 *         info.columns = **columns;
 *         info.rows = **rows;
 *         info.componentIndex = **index;
 *         info.groupIndex = groupIdx ? **groupIdx : info.componentIndex;
 *         info.numberKind = (SkSL::Type::NumberKind)(int)**kind;
 *         info.line = **line;
 *         info.fnReturnValue = retval ? **retval : -1;
 *     }
 *
 *     const skjson::ArrayValue* functions = (*root)["functions"];
 *     if (!functions) {
 *         return nullptr;
 *     }
 *
 *     for (const skjson::ObjectValue* element : *functions) {
 *         if (!element) {
 *             return nullptr;
 *         }
 *
 *         // Grow the function array to hold this element.
 *         dst->fFuncInfo.push_back({});
 *         SkSL::FunctionDebugInfo& info = dst->fFuncInfo.back();
 *
 *         // Populate the FunctionInfo with our JSON data.
 *         const skjson::StringValue* name = (*element)["name"];
 *         if (!name) {
 *             return nullptr;
 *         }
 *
 *         info.name = name->begin();
 *     }
 *
 *     const skjson::ArrayValue* trace = (*root)["trace"];
 *     if (!trace) {
 *         return nullptr;
 *     }
 *
 *     dst->fTraceInfo.reserve(trace->size());
 *     for (const skjson::ArrayValue* element : *trace) {
 *         dst->fTraceInfo.push_back(SkSL::TraceInfo{});
 *         SkSL::TraceInfo& info = dst->fTraceInfo.back();
 *
 *         if (!element || element->size() < 1 || element->size() > (1 + std::size(info.data))) {
 *             return nullptr;
 *         }
 *         const skjson::NumberValue* opVal = (*element)[0];
 *         if (!opVal) {
 *             return nullptr;
 *         }
 *         info.op = (SkSL::TraceInfo::Op)(int)**opVal;
 *         for (size_t elemIdx = 1; elemIdx < element->size(); ++elemIdx) {
 *             const skjson::NumberValue* dataVal = (*element)[elemIdx];
 *             if (!dataVal) {
 *                 return nullptr;
 *             }
 *             info.data[elemIdx - 1] = **dataVal;
 *         }
 *     }
 *
 *     return dst;
 * }
 * ```
 */
public fun readTrace(r: SkStream?): SkSp<DebugTracePriv> {
  TODO("Implement readTrace")
}

/**
 * C++ original:
 * ```cpp
 * std::vector<SkTextBlobTrace::Record> SkTextBlobTrace::CreateBlobTrace(
 *         SkStream* stream, sk_sp<SkFontMgr> lastResortMgr) {
 *     std::vector<SkTextBlobTrace::Record> trace;
 *
 *     uint32_t typefaceCount;
 *     if (!stream->readU32(&typefaceCount)) {
 *         return trace;
 *     }
 *
 *     std::vector<sk_sp<SkTypeface>> typefaceArray;
 *     for (uint32_t i = 0; i < typefaceCount; i++) {
 *         typefaceArray.push_back(SkTypeface::MakeDeserialize(stream, lastResortMgr));
 *     }
 *
 *     uint32_t restOfFile;
 *     if (!stream->readU32(&restOfFile)) {
 *         return trace;
 *     }
 *     sk_sp<SkData> data = SkData::MakeFromStream(stream, restOfFile);
 *     SkReadBuffer readBuffer{data->data(), data->size()};
 *     readBuffer.setTypefaceArray(typefaceArray.data(), typefaceArray.size());
 *
 *     while (!readBuffer.eof()) {
 *         SkTextBlobTrace::Record record;
 *         record.origUniqueID = readBuffer.readUInt();
 *         record.paint = readBuffer.readPaint();
 *         readBuffer.readPoint(&record.offset);
 *         record.blob = SkTextBlobPriv::MakeFromBuffer(readBuffer);
 *         trace.push_back(std::move(record));
 *     }
 *     return trace;
 * }
 * ```
 */
public fun createBlobTrace(stream: SkStream?, lastResortMgr: SkSp<SkFontMgr>): List<Record> {
  TODO("Implement createBlobTrace")
}

/**
 * C++ original:
 * ```cpp
 * void SkTextBlobTrace::DumpTrace(const std::vector<SkTextBlobTrace::Record>& trace) {
 *     for (const SkTextBlobTrace::Record& record : trace) {
 *         const SkTextBlob* blob = record.blob.get();
 *         const SkPaint& p = record.paint;
 *         bool weirdPaint = p.getStyle() != SkPaint::kFill_Style
 *         || p.getMaskFilter() != nullptr
 *         || p.getPathEffect() != nullptr;
 *
 *         SkDebugf("Blob %u ( %g %g ) %d\n  ",
 *                 blob->uniqueID(), record.offset.x(), record.offset.y(), weirdPaint);
 *         SkTextBlobRunIterator iter(blob);
 *         int runNumber = 0;
 *         while (!iter.done()) {
 *             SkDebugf("Run %d\n    ", runNumber);
 *             SkFont font = iter.font();
 *             SkDebugf("Font %u %g %g %g %d %d %d\n    ",
 *                     font.getTypeface()->uniqueID(),
 *                     font.getSize(),
 *                     font.getScaleX(),
 *                     font.getSkewX(),
 *                     SkFontPriv::Flags(font),
 *                     (int)font.getEdging(),
 *                     (int)font.getHinting());
 *             uint32_t glyphCount = iter.glyphCount();
 *             const SkGlyphID* glyphs = iter.glyphs();
 *             for (uint32_t i = 0; i < glyphCount; i++) {
 *                 SkDebugf("%02X ", glyphs[i]);
 *             }
 *             SkDebugf("\n");
 *             runNumber += 1;
 *             iter.next();
 *         }
 *     }
 * }
 * ```
 */
public fun dumpTrace(trace: List<Record>) {
  TODO("Implement dumpTrace")
}

/**
 * C++ original:
 * ```cpp
 * SkString HumanizeMs(double ms) {
 *     if (ms > 60e+3)  return SkStringPrintf("%.3gm", ms/60e+3);
 *     if (ms >  1e+3)  return SkStringPrintf("%.3gs",  ms/1e+3);
 *     if (ms <  1e-3)  return SkStringPrintf("%.3gns", ms*1e+6);
 * #ifdef SK_BUILD_FOR_WIN
 *     if (ms < 1)      return SkStringPrintf("%.3gus", ms*1e+3);
 * #else
 *     if (ms < 1)      return SkStringPrintf("%.3gµs", ms*1e+3);
 * #endif
 *     return SkStringPrintf("%.3gms", ms);
 * }
 * ```
 */
public fun humanizeMs(ms: Double): String {
  TODO("Implement humanizeMs")
}

/**
 * C++ original:
 * ```cpp
 * void ExtractPaths(const char filepath[], std::function<PathSniffCallback> callback) {
 *     SkFILEStream stream(filepath);
 *     if (!stream.isValid()) {
 *         SkDebugf("ExtractPaths: invalid input file at \"%s\"\n", filepath);
 *         return;
 *     }
 *
 *     class PathSniffer : public SkCanvas {
 *     public:
 *         PathSniffer(std::function<PathSniffCallback> callback)
 *                 : SkCanvas(4096, 4096, nullptr)
 *                 , fPathSniffCallback(callback) {}
 *     private:
 *         void onDrawPath(const SkPath& path, const SkPaint& paint) override {
 *             fPathSniffCallback(this->getTotalMatrix(), path, paint);
 *         }
 *         std::function<PathSniffCallback> fPathSniffCallback;
 *     };
 *
 *     sk_sp<SkSVGDOM> svg = SkSVGDOM::MakeFromStream(stream);
 *     if (!svg) {
 *         SkDebugf("ExtractPaths: couldn't load svg at \"%s\"\n", filepath);
 *         return;
 *     }
 *     PathSniffer pathSniffer(callback);
 *     svg->setContainerSize(SkSize::Make(pathSniffer.getBaseLayerSize()));
 *     svg->render(&pathSniffer);
 * }
 * ```
 */
public fun extractPaths(filepath: CharArray, callback: Function) {
  TODO("Implement extractPaths")
}

/**
 * C++ original:
 * ```cpp
 * SkSerialReturnType collectNonTextureImagesProc(SkImage* img, void* ctx) {
 *     SkSharingSerialContext* context = reinterpret_cast<SkSharingSerialContext*>(ctx);
 *     uint32_t originalId = img->uniqueID();
 *     sk_sp<SkImage>* imageInMap = context->fNonTexMap.find(originalId);
 *     if (!imageInMap) {
 *         context->fNonTexMap[originalId] = img->makeRasterImage(context->fDirectContext);
 *     }
 *
 *     // This function implements a proc that is more generally for serialization, but
 *     // we really only want to build our map. The output of this function is ignored.
 *     return SkData::MakeEmpty();
 * }
 * ```
 */
public fun collectNonTextureImagesProc(img: SkImage?, ctx: Unit?): SkSerialReturnType {
  TODO("Implement collectNonTextureImagesProc")
}

/**
 * C++ original:
 * ```cpp
 * static void handler(int sig) {
 *         unw_context_t context;
 *         unw_getcontext(&context);
 *
 *         unw_cursor_t cursor;
 *         unw_init_local(&cursor, &context);
 *
 *         SkDebugf("\nSignal %d:\n", sig);
 *         while (unw_step(&cursor) > 0) {
 *             static const size_t kMax = 256;
 *             char mangled[kMax], demangled[kMax];
 *             unw_word_t offset;
 *             unw_get_proc_name(&cursor, mangled, kMax, &offset);
 *
 *             int ok;
 *             size_t len = kMax;
 *             abi::__cxa_demangle(mangled, demangled, &len, &ok);
 *
 *             SkDebugf("%s (+0x%zx)\n", ok == 0 ? demangled : mangled, (size_t)offset);
 *         }
 *         SkDebugf("\n");
 *
 *         // Exit NOW.  Don't notify other threads, don't call anything registered with atexit().
 *         _Exit(sig);
 *     }
 * ```
 */
public fun handler(sig: Int) {
  TODO("Implement handler")
}

/**
 * C++ original:
 * ```cpp
 * static const char* config_help_fn() {
 *     static SkString helpString;
 *     helpString.set(configHelp);
 *     for (const auto& config : gPredefinedConfigs) {
 *         helpString.appendf(" %s", config.predefinedConfig);
 *     }
 *     helpString.append(" or use extended form 'backend[option=value,...]'.\n");
 *     return helpString.c_str();
 * }
 * ```
 */
public fun configHelpFn(): Char {
  TODO("Implement configHelpFn")
}

/**
 * C++ original:
 * ```cpp
 * static const char* config_extended_help_fn() {
 *     static SkString helpString;
 *     helpString.set(configExtendedHelp);
 *     for (const auto& config : gPredefinedConfigs) {
 *         helpString.appendf("\t%-10s\t= gpu(%s)\n", config.predefinedConfig, config.options);
 *     }
 *     return helpString.c_str();
 * }
 * ```
 */
public fun configExtendedHelpFn(): Char {
  TODO("Implement configExtendedHelpFn")
}

/**
 * C++ original:
 * ```cpp
 * static bool parse_option_int(const SkString& value, int* outInt) {
 *     if (value.isEmpty()) {
 *         return false;
 *     }
 *     char* endptr   = nullptr;
 *     long  intValue = strtol(value.c_str(), &endptr, 10);
 *     if (*endptr != '\0') {
 *         return false;
 *     }
 *     *outInt = static_cast<int>(intValue);
 *     return true;
 * }
 * ```
 */
public fun parseOptionInt(`value`: String, outInt: Int?): Boolean {
  TODO("Implement parseOptionInt")
}

/**
 * C++ original:
 * ```cpp
 * static bool parse_option_bool(const SkString& value, bool* outBool) {
 *     if (value.equals("true")) {
 *         *outBool = true;
 *         return true;
 *     }
 *     if (value.equals("false")) {
 *         *outBool = false;
 *         return true;
 *     }
 *     return false;
 * }
 * ```
 */
public fun parseOptionBool(`value`: String, outBool: Boolean?): Boolean {
  TODO("Implement parseOptionBool")
}

/**
 * C++ original:
 * ```cpp
 * static bool parse_option_gpu_color(const SkString& value,
 *                                    SkColorType*    outColorType,
 *                                    SkAlphaType*    alphaType) {
 *     // We always use premul unless the color type is 565.
 *     *alphaType = kPremul_SkAlphaType;
 *
 *     if (value.equals("8888")) {
 *         *outColorType  = kRGBA_8888_SkColorType;
 *     } else if (value.equals("888x")) {
 *         *outColorType  = kRGB_888x_SkColorType;
 *     } else if (value.equals("bgra8")) {
 *         *outColorType  = kBGRA_8888_SkColorType;
 *     } else if (value.equals("4444")) {
 *         *outColorType  = kARGB_4444_SkColorType;
 *     } else if (value.equals("565")) {
 *         *outColorType  = kRGB_565_SkColorType;
 *         *alphaType     = kOpaque_SkAlphaType;
 *     } else if (value.equals("1010102")) {
 *         *outColorType  = kRGBA_1010102_SkColorType;
 *     } else if (value.equals("f16")) {
 *         *outColorType  = kRGBA_F16_SkColorType;
 *     } else if (value.equals("f16norm")) {
 *         *outColorType  = kRGBA_F16Norm_SkColorType;
 *     } else if (value.equals("srgba")) {
 *         *outColorType = kSRGBA_8888_SkColorType;
 *     } else if (value.equals("r8")) {
 *         *outColorType = kR8_unorm_SkColorType;
 *         *alphaType = kOpaque_SkAlphaType;
 *     } else {
 *         return false;
 *     }
 *     return true;
 * }
 * ```
 */
public fun parseOptionGpuColor(
  `value`: String,
  outColorType: SkColorType?,
  alphaType: SkAlphaType?,
): Boolean {
  TODO("Implement parseOptionGpuColor")
}

/**
 * C++ original:
 * ```cpp
 * SkCommandLineConfigGraphite* parse_command_line_config_graphite(const SkString& tag,
 *                                                                 const TArray<SkString>& vias,
 *                                                                 const SkString& options) {
 *     using ContextType = skgpu::ContextType;
 *
 *     ContextType contextType            = skgpu::ContextType::kMetal;
 *     SkColorType colorType              = kRGBA_8888_SkColorType;
 *     SkAlphaType alphaType              = kPremul_SkAlphaType;
 *     bool        testPersistentStorage  = false;
 *     bool        testPrecompileGraphite = false;
 *     bool        testPipelineTracking   = false;
 *
 *     bool parseSucceeded = false;
 *     ExtendedOptions extendedOptions(options, &parseSucceeded);
 *     if (!parseSucceeded) {
 *         return nullptr;
 *     }
 *
 *     bool validOptions =
 *         extendedOptions.get_option_graphite_api("api", &contextType) &&
 *         extendedOptions.get_option_gpu_color("color", &colorType, &alphaType) &&
 *         extendedOptions.get_option_bool("testPersistentStorage", &testPersistentStorage) &&
 *         extendedOptions.get_option_bool("testPrecompileGraphite", &testPrecompileGraphite) &&
 *         extendedOptions.get_option_bool("testPipelineTracking", &testPipelineTracking);
 *     if (!validOptions) {
 *         return nullptr;
 *     }
 *
 *     return new SkCommandLineConfigGraphite(tag,
 *                                            vias,
 *                                            contextType,
 *                                            colorType,
 *                                            alphaType,
 *                                            testPersistentStorage,
 *                                            testPrecompileGraphite,
 *                                            testPipelineTracking);
 * }
 * ```
 */
public fun parseCommandLineConfigGraphite(
  tag: String,
  vias: TArray<String>,
  options: String,
): SkCommandLineConfigGraphite {
  TODO("Implement parseCommandLineConfigGraphite")
}

/**
 * C++ original:
 * ```cpp
 * SkCommandLineConfigSvg* parse_command_line_config_svg(const SkString& tag,
 *                                                       const TArray<SkString>& vias,
 *                                                       const SkString& options) {
 *     // Defaults for SVG backend.
 *     int pageIndex = 0;
 *
 *     bool            parseSucceeded = false;
 *     ExtendedOptions extendedOptions(options, &parseSucceeded);
 *     if (!parseSucceeded) {
 *         return nullptr;
 *     }
 *
 *     bool validOptions = extendedOptions.get_option_int("page", &pageIndex);
 *
 *     if (!validOptions) {
 *         return nullptr;
 *     }
 *
 *     return new SkCommandLineConfigSvg(tag, vias, pageIndex);
 * }
 * ```
 */
public fun parseCommandLineConfigSvg(
  tag: String,
  vias: TArray<String>,
  options: String,
): SkCommandLineConfigSvg {
  TODO("Implement parseCommandLineConfigSvg")
}

/**
 * C++ original:
 * ```cpp
 * void ParseConfigs(const CommandLineFlags::StringArray& configs,
 *                   SkCommandLineConfigArray*            outResult) {
 *     outResult->clear();
 *     for (int i = 0; i < configs.size(); ++i) {
 *         SkString         extendedBackend;
 *         SkString         extendedOptions;
 *         SkString         simpleBackend;
 *         TArray<SkString> vias;
 *
 *         SkString         tag(configs[i]);
 *         TArray<SkString> parts;
 *         SkStrSplit(tag.c_str(), "[", kStrict_SkStrSplitMode, &parts);
 *         if (parts.size() == 2) {
 *             TArray<SkString> parts2;
 *             SkStrSplit(parts[1].c_str(), "]", kStrict_SkStrSplitMode, &parts2);
 *             if (parts2.size() == 2 && parts2[1].isEmpty()) {
 *                 SkStrSplit(parts[0].c_str(), "-", kStrict_SkStrSplitMode, &vias);
 *                 if (vias.size()) {
 *                     extendedBackend = vias[vias.size() - 1];
 *                     vias.pop_back();
 *                 } else {
 *                     extendedBackend = parts[0];
 *                 }
 *                 extendedOptions = parts2[0];
 *                 simpleBackend.printf("%s[%s]", extendedBackend.c_str(), extendedOptions.c_str());
 *             }
 *         }
 *
 *         if (extendedBackend.isEmpty()) {
 *             simpleBackend = tag;
 *             SkStrSplit(tag.c_str(), "-", kStrict_SkStrSplitMode, &vias);
 *             if (vias.size()) {
 *                 simpleBackend = vias[vias.size() - 1];
 *                 vias.pop_back();
 *             }
 *             for (auto& predefinedConfig : gPredefinedConfigs) {
 *                 if (simpleBackend.equals(predefinedConfig.predefinedConfig)) {
 *                     extendedBackend = predefinedConfig.backend;
 *                     extendedOptions = predefinedConfig.options;
 *                     break;
 *                 }
 *             }
 *         }
 *         SkCommandLineConfig* parsedConfig = nullptr;
 * #if defined(SK_GANESH)
 *         if (extendedBackend.equals("gpu")) {
 *             parsedConfig = parse_command_line_config_gpu(tag, vias, extendedOptions);
 *         }
 * #endif
 * #if defined(SK_GRAPHITE)
 *         if (extendedBackend.equals("graphite")) {
 *             parsedConfig = parse_command_line_config_graphite(tag, vias, extendedOptions);
 *         }
 * #endif
 *         if (extendedBackend.equals("svg")) {
 *             parsedConfig = parse_command_line_config_svg(tag, vias, extendedOptions);
 *         }
 *         if (!parsedConfig) {
 *             parsedConfig = new SkCommandLineConfig(tag, simpleBackend, vias);
 *         }
 *         outResult->emplace_back(parsedConfig);
 *     }
 * }
 * ```
 */
public fun parseConfigs(configs: CommandLineFlags.StringArray, outResult: SkCommandLineConfigArray?) {
  TODO("Implement parseConfigs")
}

/**
 * C++ original:
 * ```cpp
 * void SetTestOptions(skiatest::graphite::TestOptions* testOptions) {
 *     static std::unique_ptr<SkExecutor> gGpuExecutor =
 *             (0 != FLAGS_gpuThreads) ? SkExecutor::MakeFIFOThreadPool(FLAGS_gpuThreads)
 *                                     : nullptr;
 *
 *     testOptions->fContextOptions.fExecutor = gGpuExecutor.get();
 *
 *     if (FLAGS_internalSamples >= 0) {
 *         testOptions->fContextOptions.fInternalMultisampleCount =
 *                 skgpu::graphite::ToSampleCount(FLAGS_internalSamples);
 *     }
 *     if (FLAGS_internalMSAATileSize > 0) {
 *         testOptions->fContextOptions.fInternalMSAATileSize = {FLAGS_internalMSAATileSize,
 *                                                               FLAGS_internalMSAATileSize};
 *     }
 *     if (FLAGS_minMSAAPathSize >= 0) {
 *         testOptions->fContextOptions.fMinimumPathSizeForMSAA = FLAGS_minMSAAPathSize;
 *     }
 *
 * #if defined(SK_DAWN)
 *     testOptions->fDisableTintSymbolRenaming = FLAGS_disable_tint_symbol_renaming;
 *     testOptions->fNeverYieldToWebGPU = FLAGS_neverYieldToWebGPU;
 *     testOptions->fUseWGPUTextureView = FLAGS_useWGPUTextureView;
 * #endif // SK_DAWN
 * }
 * ```
 */
public fun setTestOptions(testOptions: TestOptions?) {
  TODO("Implement setTestOptions")
}

/**
 * C++ original:
 * ```cpp
 * bool CollectImages(const CommandLineFlags::StringArray& images, TArray<SkString>* output) {
 *     SkASSERT(output);
 *
 *     static const char* const exts[] = {
 *         "bmp",
 *         "gif",
 *         "jpg",
 *         "jpeg",
 *         "png",
 *         "webp",
 *         "ktx",
 *         "astc",
 *         "wbmp",
 *         "ico",
 * #if !defined(SK_BUILD_FOR_WIN)
 *         "BMP",
 *         "GIF",
 *         "JPG",
 *         "JPEG",
 *         "PNG",
 *         "WEBP",
 *         "KTX",
 *         "ASTC",
 *         "WBMP",
 *         "ICO",
 * #endif
 * #ifdef SK_CODEC_DECODES_RAW
 *         "arw",
 *         "cr2",
 *         "dng",
 *         "nef",
 *         "nrw",
 *         "orf",
 *         "raf",
 *         "rw2",
 *         "pef",
 *         "srw",
 * #if !defined(SK_BUILD_FOR_WIN)
 *         "ARW",
 *         "CR2",
 *         "DNG",
 *         "NEF",
 *         "NRW",
 *         "ORF",
 *         "RAF",
 *         "RW2",
 *         "PEF",
 *         "SRW",
 * #endif
 * #endif
 *     };
 *
 *     for (int i = 0; i < images.size(); ++i) {
 *         const char* flag = images[i];
 *         if (!sk_exists(flag)) {
 *             SkDebugf("%s does not exist!\n", flag);
 *             return false;
 *         }
 *
 *         if (sk_isdir(flag)) {
 *             // If the value passed in is a directory, add all the images
 *             bool foundAnImage = false;
 *             for (const char* ext : exts) {
 *                 SkOSFile::Iter it(flag, ext);
 *                 SkString file;
 *                 while (it.next(&file)) {
 *                     foundAnImage = true;
 *                     output->push_back() = SkOSPath::Join(flag, file.c_str());
 *                 }
 *             }
 *             if (!foundAnImage) {
 *                 SkDebugf("No supported images found in %s!\n", flag);
 *                 return false;
 *             }
 *         } else {
 *             // Also add the value if it is a single image
 *             output->push_back() = flag;
 *         }
 *     }
 *     return true;
 * }
 * ```
 */
public fun collectImages(images: CommandLineFlags.StringArray, output: TArray<String>?): Boolean {
  TODO("Implement collectImages")
}

/**
 * C++ original:
 * ```cpp
 * static sk_sp<SkColorSpace> rec2020() {
 *     return SkColorSpace::MakeRGB(SkNamedTransferFn::kRec2020, SkNamedGamut::kRec2020);
 * }
 * ```
 */
public fun rec2020(): SkSp<SkColorSpace> {
  TODO("Implement rec2020")
}

/**
 * C++ original:
 * ```cpp
 * void initializeEventTracingForTools(const char* traceFlag) {
 *     if (!traceFlag) {
 *         if (FLAGS_trace.isEmpty()) {
 *             return;
 *         }
 *         traceFlag = FLAGS_trace[0];
 *     }
 *
 *     SkEventTracer* eventTracer = nullptr;
 *     if (0 == strcmp(traceFlag, "atrace")) {
 *         eventTracer = new SkATrace();
 *     } else if (0 == strcmp(traceFlag, "debugf")) {
 *         eventTracer = new SkDebugfTracer();
 *     } else if (0 == strcmp(traceFlag, "perfetto")) {
 *       #if defined(SK_USE_PERFETTO)
 *           eventTracer = new SkPerfettoTrace();
 *       #else
 *           // TODO(b/259248961): update this explanation (and associated docs) as the Perfetto
 *           // transition progresses.
 *           SkDebugf("Perfetto is not enabled (SK_USE_PERFETTO is false). Perfetto tracing will not "
 *                    "be performed.\nTracing tools with Perfetto is only enabled for Linux, Android, "
 *                    "and Mac.\n");
 *           return;
 *       #endif
 *     }
 *     else {
 *         eventTracer = new ChromeTracingTracer(traceFlag);
 *     }
 *
 *     SkAssertResult(SkEventTracer::SetInstance(eventTracer));
 * }
 * ```
 */
public fun initializeEventTracingForTools(traceFlag: String?) {
  TODO("Implement initializeEventTracingForTools")
}

/**
 * C++ original:
 * ```cpp
 * template <typename T>
 * void begin_event_with_second_arg(const char * categoryName, const char* eventName,
 *                                  const char* arg1Name, T arg1Val, const char* arg2Name,
 *                                  const uint8_t& arg2Type, const uint64_t& arg2Val) {
 *       perfetto::DynamicCategory category{categoryName};
 *
 *       switch (arg2Type) {
 *           case TRACE_VALUE_TYPE_BOOL: {
 *               TRACE_EVENT_BEGIN(category, nullptr, arg1Name, arg1Val, arg2Name, SkToBool(arg2Val),
 *                                 [&](perfetto::EventContext ctx) {
 *                                 ctx.event()->set_name(eventName); });
 *               break;
 *           }
 *           case TRACE_VALUE_TYPE_UINT: {
 *               TRACE_EVENT_BEGIN(category, nullptr, arg1Name, arg1Val, arg2Name, arg2Val,
 *                                 [&](perfetto::EventContext ctx) {
 *                                 ctx.event()->set_name(eventName); });
 *               break;
 *           }
 *           case TRACE_VALUE_TYPE_INT: {
 *               TRACE_EVENT_BEGIN(category, nullptr, arg1Name, arg1Val,
 *                                 arg2Name, static_cast<int64_t>(arg2Val),
 *                                 [&](perfetto::EventContext ctx) {
 *                                 ctx.event()->set_name(eventName); });
 *               break;
 *           }
 *           case TRACE_VALUE_TYPE_DOUBLE: {
 *               TRACE_EVENT_BEGIN(category, nullptr, arg1Name, arg1Val,
 *                                 arg2Name, sk_bit_cast<double>(arg2Val),
 *                                 [&](perfetto::EventContext ctx) {
 *                                 ctx.event()->set_name(eventName); });
 *               break;
 *           }
 *           case TRACE_VALUE_TYPE_POINTER: {
 *               TRACE_EVENT_BEGIN(category, nullptr, arg1Name, arg1Val,
 *                                 arg2Name, skia_private::TraceValueAsPointer(arg2Val),
 *                                 [&](perfetto::EventContext ctx) {
 *                                 ctx.event()->set_name(eventName); });
 *               break;
 *           }
 *           case TRACE_VALUE_TYPE_COPY_STRING: [[fallthrough]];
 *           case TRACE_VALUE_TYPE_STRING: {
 *               TRACE_EVENT_BEGIN(category, nullptr, arg1Name, arg1Val,
 *                                 arg2Name, skia_private::TraceValueAsString(arg2Val),
 *                                 [&](perfetto::EventContext ctx) {
 *                                 ctx.event()->set_name(eventName); });
 *               break;
 *           }
 *           default: {
 *               SkUNREACHABLE;
 *               break;
 *           }
 *       }
 * }
 * ```
 */
public fun <T> beginEventWithSecondArg(
  categoryName: String?,
  eventName: String?,
  arg1Name: String?,
  arg1Val: T,
  arg2Name: String?,
  arg2Type: UByte,
  arg2Val: ULong,
) {
  TODO("Implement beginEventWithSecondArg")
}

/**
 * C++ original:
 * ```cpp
 * inline bool GetResourceAsBitmap(const char* resource, SkBitmap* dst) {
 *     return DecodeDataToBitmap(GetResourceAsData(resource), dst);
 * }
 * ```
 */
public fun getResourceAsBitmap(resource: String?, dst: SkBitmap?): Boolean {
  TODO("Implement getResourceAsBitmap")
}

/**
 * C++ original:
 * ```cpp
 * inline bool GetResourceAsBitmapWithColortype(const char* resource, SkBitmap* dst, SkColorType dstCT) {
 *     return DecodeDataToBitmapWithColorType(GetResourceAsData(resource), dst, dstCT);
 * }
 * ```
 */
public fun getResourceAsBitmapWithColortype(
  resource: String?,
  dst: SkBitmap?,
  dstCT: SkColorType,
): Boolean {
  TODO("Implement getResourceAsBitmapWithColortype")
}

/**
 * C++ original:
 * ```cpp
 * inline sk_sp<SkImage> GetResourceAsImage(const char* resource) {
 *     return SkImages::DeferredFromEncodedData(GetResourceAsData(resource));
 * }
 * ```
 */
public fun getResourceAsImage(resource: String?): SkSp<SkImage> {
  TODO("Implement getResourceAsImage")
}

/**
 * C++ original:
 * ```cpp
 * inline sk_sp<SkData> GetResourceAsData(const std::string& resource) {
 *     return GetResourceAsData(resource.c_str());
 * }
 * ```
 */
public fun getResourceAsData(resource: String): SkSp<SkData> {
  TODO("Implement getResourceAsData")
}

/**
 * C++ original:
 * ```cpp
 * inline void draw_checkerboard(SkCanvas* canvas) {
 *     ToolUtils::draw_checkerboard(canvas, 0xFF999999, 0xFF666666, 8);
 * }
 * ```
 */
public fun drawCheckerboard(canvas: SkCanvas?) {
  TODO("Implement drawCheckerboard")
}

/**
 * C++ original:
 * ```cpp
 * static inline MSec NanosToMSec(double nanos) {
 *     const double msec = nanos * 1e-6;
 *     SkASSERT(MSecMax >= msec);
 *     return static_cast<MSec>(msec);
 * }
 * ```
 */
public fun nanosToMSec(nanos: Double): MSec {
  TODO("Implement nanosToMSec")
}

/**
 * C++ original:
 * ```cpp
 * static inline double NanosToSeconds(double nanos) {
 *     return nanos * 1e-9;
 * }
 * ```
 */
public fun nanosToSeconds(nanos: Double): Double {
  TODO("Implement nanosToSeconds")
}

/**
 * C++ original:
 * ```cpp
 * static inline float Scaled(float time, float speed, float period = 0) {
 *     double value = time * speed;
 *     if (period) {
 *         value = ::fmod(value, (double)(period));
 *     }
 *     return (float)value;
 * }
 * ```
 */
public fun scaled(
  time: Float,
  speed: Float,
  period: Float = TODO(),
): Float {
  TODO("Implement scaled")
}

/**
 * C++ original:
 * ```cpp
 * static inline float PingPong(double time,
 *                              float period,
 *                              float phase,
 *                              float ends,
 *                              float mid) {
 *     double value = ::fmod(time + phase, period);
 *     double half  = period / 2.0;
 *     double diff  = ::fabs(value - half);
 *     return (float)(ends + (1.0 - diff / half) * (mid - ends));
 * }
 * ```
 */
public fun pingPong(
  time: Double,
  period: Float,
  phase: Float,
  ends: Float,
  mid: Float,
): Float {
  TODO("Implement pingPong")
}

/**
 * C++ original:
 * ```cpp
 * static inline float SineWave(double time,
 *                              float periodInSecs,
 *                              float phaseInSecs,
 *                              float min,
 *                              float max) {
 *     if (periodInSecs < 0.f) {
 *         return (min + max) / 2.f;
 *     }
 *     double t = NanosToSeconds(time) + phaseInSecs;
 *     t *= 2 * SK_FloatPI / periodInSecs;
 *     float halfAmplitude = (max - min) / 2.f;
 *     return halfAmplitude * std::sin(t) + halfAmplitude + min;
 * }
 * ```
 */
public fun sineWave(
  time: Double,
  periodInSecs: Float,
  phaseInSecs: Float,
  min: Float,
  max: Float,
): Float {
  TODO("Implement sineWave")
}

/**
 * C++ original:
 * ```cpp
 * inline sk_sp<SkImage> MakeTextureImage(SkCanvas* canvas, sk_sp<SkImage> orig) {
 *     if (!orig) {
 *         return nullptr;
 *     }
 *
 * #if defined(SK_GANESH)
 *     if (canvas->recordingContext() && canvas->recordingContext()->asDirectContext()) {
 *         GrDirectContext* dContext = canvas->recordingContext()->asDirectContext();
 *         const GrCaps* caps = dContext->priv().caps();
 *
 *         if (orig->width() >= caps->maxTextureSize() || orig->height() >= caps->maxTextureSize()) {
 *             // Ganesh is able to tile large SkImage draws. Always forcing SkImages to be uploaded
 *             // prevents this feature from being tested by our tools. For now, leave excessively
 *             // large SkImages as bitmaps.
 *             return orig;
 *         }
 *
 *         return SkImages::TextureFromImage(dContext, orig);
 *     }
 * #endif
 * #if defined(SK_GRAPHITE)
 *     if (canvas->recorder()) {
 *         return SkImages::TextureFromImage(canvas->recorder(), orig, {/*fMipmapped=*/false});
 *     }
 * #endif
 *     return orig;
 * }
 * ```
 */
public fun makeTextureImage(canvas: SkCanvas?, orig: SkSp<SkImage>): SkSp<SkImage> {
  TODO("Implement makeTextureImage")
}

/**
 * C++ original:
 * ```cpp
 * inline void RegisterAllAvailable() {
 * #if defined(SK_CODEC_DECODES_AVIF)
 *     SkCodecs::Register(SkAvifDecoder::Decoder());
 * #endif
 * #if defined(SK_CODEC_DECODES_BMP_WITH_RUST)
 *     SkCodecs::Register(SkBmpRustDecoder::Decoder());
 * #elif defined(SK_CODEC_DECODES_BMP)
 *     SkCodecs::Register(SkBmpDecoder::Decoder());
 * #endif
 * #if defined(SK_CODEC_DECODES_GIF)
 *     SkCodecs::Register(SkGifDecoder::Decoder());
 * #endif
 * #if defined(SK_CODEC_DECODES_ICO)
 *     SkCodecs::Register(SkIcoDecoder::Decoder());
 * #endif
 * #if defined(SK_CODEC_DECODES_JPEG)
 *     SkCodecs::Register(SkJpegDecoder::Decoder());
 * #endif
 * #if defined(SK_CODEC_DECODES_JPEGXL)
 *     SkCodecs::Register(SkJpegxlDecoder::Decoder());
 * #endif
 * #if defined(SK_CODEC_DECODES_PNG_WITH_LIBPNG)
 *     SkCodecs::Register(SkPngDecoder::Decoder());
 * #endif
 * #if defined(SK_CODEC_DECODES_PNG_WITH_RUST)
 *     SkCodecs::Register(SkPngRustDecoder::Decoder());
 * #endif
 * #if defined(SK_CODEC_DECODES_RAW)
 *     SkCodecs::Register(SkRawDecoder::Decoder());
 * #endif
 * #if defined(SK_CODEC_DECODES_WBMP)
 *     SkCodecs::Register(SkWbmpDecoder::Decoder());
 * #endif
 * #if defined(SK_CODEC_DECODES_WEBP)
 *     SkCodecs::Register(SkWebpDecoder::Decoder());
 * #endif
 * }
 * ```
 */
public fun registerAllAvailable() {
  TODO("Implement registerAllAvailable")
}
