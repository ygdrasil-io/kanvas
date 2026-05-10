# Platform Ports

`src/ports/` is the OS-glue layer of Skia. Almost every header under
`include/core/` whose implementation depends on the host operating
system — `SkOSFile`, the `SkFontMgr` factories, `SkDiscardableMemory`,
`SkImageGenerator::MakeFromEncoded`, `SkDebugf`, `SkMalloc` — has one
file under `include/core` or `include/ports` that declares the
interface and one file per OS under `src/ports/` that implements it.
Each Skia build picks exactly one implementation per category, so the
linker sees a single definition.

This document describes those per-platform implementations. For the
shaping/layout side of text see [Text & Fonts](text-and-fonts.md); the
codec side of image decoding lives in [Image Decoders](image-decoders.md);
and the Android-only surface helpers and `AHardwareBuffer` glue have
their own page in [Android Integration](android-integration.md).

## Source map

| Category | Public header(s) | Implementations under `src/ports/` |
| --- | --- | --- |
| File I/O | `src/core/SkOSFile.h` | `SkOSFile_posix.cpp`, `SkOSFile_win.cpp`, `SkOSFile_stdio.cpp`, `SkOSFile_ios.h` |
| Memory | `include/private/base/SkMalloc.h` | `SkMemory_malloc.cpp`, `SkMemory_mozalloc.cpp` |
| Logging | `include/core/SkTypes.h` (`SkDebugf`) | `SkLog_stdio.cpp`, `SkLog_android.cpp`, `SkLog_win.cpp` |
| Discardable memory | `src/core/SkDiscardableMemory.h` | `SkDiscardableMemory_none.cpp` (Android pins via the framework) |
| Default registries | — | `SkGlobalInitialization_default.cpp` |
| Font host: FreeType | `include/ports/SkFontScanner_FreeType.h`, `src/ports/SkTypeface_FreeType.h` | `SkFontHost_FreeType.cpp`, `SkFontHost_FreeType_common.cpp` |
| Font host: CoreText | `include/ports/SkFontMgr_mac_ct.h`, `SkTypeface_mac.h` | `SkFontMgr_mac_ct.cpp`, `SkTypeface_mac_ct.cpp`, `SkScalerContext_mac_ct.cpp` |
| Font host: DirectWrite | `include/ports/SkTypeface_win.h` | `SkFontMgr_win_dw.cpp`, `SkFontHost_win.cpp`, `SkTypeface_win_dw.cpp`, `SkScalerContext_win_dw.cpp` |
| Font host: Fontations | `include/ports/SkTypeface_fontations.h`, `SkFontScanner_Fontations.h` | `SkTypeface_fontations.cpp`, `SkFontScanner_fontations.cpp`, `fontations/` (Rust FFI) |
| Font manager: fontconfig | `include/ports/SkFontMgr_fontconfig.h`, `SkFontConfigInterface.h` | `SkFontMgr_fontconfig.cpp`, `SkFontConfigInterface*.cpp`, `SkFontMgr_FontConfigInterface.cpp` |
| Font manager: Android XML | `include/ports/SkFontMgr_android.h` | `SkFontMgr_android.cpp` + `SkFontMgr_android_parser.cpp` |
| Font manager: Android NDK | `include/ports/SkFontMgr_android_ndk.h` | `SkFontMgr_android_ndk.cpp` |
| Font manager: custom | `include/ports/SkFontMgr_directory.h`, `SkFontMgr_data.h`, `SkFontMgr_empty.h` | `SkFontMgr_custom*.cpp` |
| Font manager: Fontations / Fuchsia | `SkFontMgr_Fontations.h`, `SkFontMgr_fuchsia.h` | `SkFontMgr_fontations_empty.cpp`, `SkFontMgr_fuchsia.cpp` |
| Image generator | `include/ports/SkImageGeneratorCG.h`, `SkImageGeneratorNDK.h`, `SkImageGeneratorWIC.h` | `SkImageGeneratorCG.cpp`, `SkImageGeneratorNDK.cpp`, `SkImageGeneratorWIC.cpp` |
| Encoder (Android NDK) | `include/encode/SkPngEncoder.h` (and friends) | `SkImageEncoder_NDK.cpp`, `SkNDKConversions.cpp` |

The `BUILD.bazel` in `src/ports/` selects the right subset for each
target operating system; with the GN build `gn/skia.gni` performs the
same role.

---

## File I/O — `src/core/SkOSFile.h`

`SkOSFile` is a thin C wrapper around `FILE*` plus a handful of helpers
that Skia's own resource loading and `SkData::MakeFromFileName` rely on.
The interface is small:

- `sk_fopen` / `sk_fclose` — open/close, with `kRead`/`kWrite` flag bits.
- `sk_exists`, `sk_isdir` — existence/type tests.
- `sk_fmmap`, `sk_fmunmap` — mmap a file into memory (returns `nullptr`
  if mapping is unsupported).
- `sk_fidentical` — true if two `FILE*` map to the same inode.
- `SkOSFile::Iter` — directory iteration.
- `sk_fsync` — flush buffers to disk.

`SkOSFile_posix.cpp` is the POSIX implementation used by Linux, macOS,
iOS, and Android. It uses `access`, `fstat`, `mmap`, and `dirent` from
the C library, and includes `src/ports/SkOSFile_ios.h` to fall back to
the iOS bundle when an absolute path miss is read-only
(`skia-main/src/ports/SkOSFile_posix.cpp:42`). `sk_fsync` is conditional
because Android, uClibc, and newlib either do not ship `fsync` or treat
it as a no-op.

`SkOSFile_win.cpp` reimplements the same surface on top of Win32
(`CreateFileMapping`/`MapViewOfFile`, `_stat64i32`, `FindFirstFile`).
`SkOSFile_stdio.cpp` carries the platform-independent helpers
(`sk_fwrite`, `sk_fseek`, `sk_fgetsize`) so they need not be duplicated.

## Memory — `include/private/base/SkMalloc.h`

Skia routes every allocation through `sk_malloc_*` / `sk_free`. The
default `SkMemory_malloc.cpp` calls the system `malloc` family and
maps `kThrow_SkMallocFlag` to `SK_ABORT` on OOM (so an aborted allocation
crashes loudly rather than returning `nullptr`). `SkMemory_mozalloc.cpp`
forwards to Firefox's `mozalloc` for embeddings inside Gecko. New
embeddings can drop in their own `SkMemory_*.cpp` and exclude the
default; nothing else in `src/` uses bare `malloc`.

## Logging — `SkDebugf`

`SkDebugf` is the printf-style log used everywhere in Skia. The three
backends are:

- **`SkLog_stdio.cpp`** — `vfprintf(stderr, …)`. Used on Linux, macOS,
  Fuchsia, and most embedded builds.
- **`SkLog_android.cpp`** — `__android_log_vprint(ANDROID_LOG_DEBUG, "skia", …)`.
- **`SkLog_win.cpp`** — `OutputDebugStringA` plus `vfprintf(stderr, …)`,
  so messages reach both the Visual Studio debugger and the console.

Builds replace the implementation by linking only one file. There is no
runtime registry — log routing is a build-time choice.

## Discardable memory — `src/core/SkDiscardableMemory.h`

`SkDiscardableMemory` is an OS-managed buffer that can be paged out
under memory pressure and re-paged via `lock()`. The CPU and GPU glyph
caches use it to keep large strikes resident only when possible.

Skia ships a single open-source stub, `SkDiscardableMemory_none.cpp`,
that always returns `nullptr` (so callers fall back to plain `malloc`).
Embedders supply real implementations: Chromium uses `ashmem`/Mach VM,
the Android framework uses `android::DiscardableMemory`. The stub keeps
the link clean for projects that do not care.

## Default registries — `SkGlobalInitialization_default.cpp`

Pulled in by every Skia link, this file constructs the static
`SkFlattenable::Register` calls for the built-in shaders, color
filters, image filters, mask filters, and path effects. Without it
`SkPicture` deserialisation would not find any of them. Embeddings that
strip features can replace it with a leaner version that only registers
what they need.

---

## Font hosts

A *font host* in Skia is the pair of (a) `SkScalerContext` subclass
that produces glyph metrics and rasterised masks/paths and (b) a
`SkTypeface` subclass that owns the font handle and creates scaler
contexts. Each platform contributes one — sometimes two — and a
platform-specific `SkFontMgr` factory exposes them as the system
`SkFontMgr`.

### FreeType — `src/ports/SkFontHost_FreeType.cpp`

The Linux, Android, and ChromeOS port uses FreeType. `SkTypeface_FreeType`
holds an `FT_Face`; `SkScalerContext_FreeType` performs glyph rendering
with `FT_Load_Glyph`, optional CFF/Type 1 hinting, LCD subpixel
filtering (`FT_Library_SetLcdFilter`), and a runtime version check
(`SK_FREETYPE_MINIMUM_RUNTIME_VERSION`,
`skia-main/src/ports/SkFontHost_FreeType.cpp:78`). COLRv1, SVG, and
SBIX colour glyphs go through `SkFontHost_FreeType_common.cpp`, which
is shared with the Fontations path. `SkFontScanner_FreeType` parses
font files for face/index discovery (used by directory `SkFontMgr`s).

### CoreText (macOS / iOS) — `src/ports/SkFontMgr_mac_ct.cpp`

The Apple port wraps `CTFont`/`CGFont`. `SkTypeface_Mac` stores a
`CTFontRef`; `SkScalerContext_Mac` rasterises with `CGContext`
into a 32-bit ARGB bitmap, then converts to A8 / LCD16 via
`SkScalerContext_mac_ct.cpp`. `SkFontMgr_New_CoreText()` enumerates
families through `CTFontManagerCreateFontDescriptorsForType`, and
`SkTypeface_GetCoreTextRef` lets callers escape back to CoreText for
interop. `SkCFObject.h` is the smart pointer for `CFTypeRef` used
throughout these files.

### DirectWrite (Windows) — `src/ports/SkFontMgr_win_dw.cpp`

The Windows port uses DirectWrite for font enumeration/metrics and
GDI for legacy bitmap fonts. `SkTypeface_DirectWrite` stores an
`IDWriteFontFace`; `SkScalerContext_DW` renders via
`IDWriteGlyphRunAnalysis` (or `IDWriteColorGlyphRunEnumerator` for
COLR). `SkFontHost_win.cpp` carries the small amount of GDI code still
needed for Windows bitmap fonts. The factory in `SkTypeface_win.h`
returns a font manager that wraps the system `IDWriteFactory`.

### Fontations (Rust) — `src/ports/SkTypeface_fontations.cpp`

Fontations is a pure-Rust font stack (skrifa + fontc) that Skia can use
in place of FreeType for OpenType reading. The C++ side here is a thin
wrapper over a generated FFI; the Rust crate lives under
`src/ports/fontations/`. Colour glyphs reuse `SkFontHost_FreeType_common`'s
COLRv1 painter via a small adapter, so visual output matches the
FreeType path. The matching font manager
(`SkFontMgr_fontations_empty.cpp`) returns no system fonts — embedders
typically combine Fontations with `SkFontMgr_New_Custom_Data` to ship
their own font set.

---

## Font managers

`SkFontMgr` is the registry for system fonts: family enumeration, style
matching, and `SkTypeface` creation from `SkData`/files. Each platform
chooses one factory.

| Factory | Header | Source |
| --- | --- | --- |
| `SkFontMgr_New_FCI(…)` | `include/ports/SkFontMgr_FontConfigInterface.h` | `SkFontMgr_FontConfigInterface.cpp` (Chromium IPC fontconfig) |
| `SkFontMgr_New_FontConfig(…)` | `include/ports/SkFontMgr_fontconfig.h` | `SkFontMgr_fontconfig.cpp` (in-process libfontconfig) |
| `SkFontMgr_New_Android(…)` | `include/ports/SkFontMgr_android.h` | `SkFontMgr_android.cpp` + `SkFontMgr_android_parser.cpp` (XML config) |
| `SkFontMgr_New_AndroidNDK(…)` | `include/ports/SkFontMgr_android_ndk.h` | `SkFontMgr_android_ndk.cpp` (`ASystemFontIterator`) |
| `SkFontMgr_New_Custom_Directory(…)` | `include/ports/SkFontMgr_directory.h` | `SkFontMgr_custom_directory.cpp` |
| `SkFontMgr_New_Custom_Embedded(…)` | `include/ports/SkFontMgr_data.h` | `SkFontMgr_custom_embedded.cpp` |
| `SkFontMgr_New_Custom_Empty()` | `include/ports/SkFontMgr_empty.h` | `SkFontMgr_custom_empty.cpp` |
| `SkFontMgr_New_CoreText(…)` | `include/ports/SkFontMgr_mac_ct.h` | `SkFontMgr_mac_ct.cpp` |
| `SkFontMgr_New_DirectWrite(…)` | `include/ports/SkTypeface_win.h` | `SkFontMgr_win_dw.cpp` |
| `SkFontMgr_New_Fontations_Empty(…)` | `include/ports/SkFontMgr_Fontations.h` | `SkFontMgr_fontations_empty.cpp` |
| `SkFontMgr_New_Fuchsia(…)` | `include/ports/SkFontMgr_fuchsia.h` | `SkFontMgr_fuchsia.cpp` |

The fontconfig managers come in two flavours: the *direct* path links
libfontconfig in-process, while the *interface* path delegates to a
Chromium-style sandboxed font service via an `SkFontConfigInterface`
IPC abstraction (`SkFontConfigInterface_direct.cpp` is the in-process
fallback for tests). The Android XML parser reads
`/system/etc/fonts.xml` to honour family fallback chains and
locale-specific substitutions; the NDK manager replaces it on API 29+
with the framework's own font enumerator and is a forward-port of the
same fallback semantics. The custom managers have no system source —
they read TTF/OTF files from a directory, an in-memory blob set, or
nothing at all (an empty manager is useful for tests and minimal apps
that supply typefaces explicitly).

The default `SkFontMgr::RefDefault()` returned by
`include/core/SkFontMgr.h` is wired up by Skia's build files to call
the platform's factory automatically.

---

## Image generators

`SkImageGenerator` is the lazy decoder protocol. The platform image
generators wrap the OS codec stack instead of Skia's own
`SkCodec` family:

- **`SkImageGeneratorCG.cpp`** — uses ImageIO (`CGImageSourceRef`).
  Returned by `SkImageGeneratorCG::MakeFromEncodedCG`. Useful on Apple
  platforms when you want HEIC/AVIF support without bundling
  `libheif`.
- **`SkImageGeneratorNDK.cpp`** — uses `AImageDecoder` from the Android
  NDK (API 30+). Returned by `SkImageGeneratorNDK::MakeFromEncodedNDK`.
  See [Android Integration](android-integration.md) for the matching
  surface APIs.
- **`SkImageGeneratorWIC.cpp`** — uses Windows Imaging Component
  (`IWICImagingFactory`). Returned by
  `SkImageGeneratorWIC::MakeFromEncodedWIC`.

These coexist with Skia's own decoders described in
[Image Decoders](image-decoders.md). An app typically picks one
strategy: bundle Skia's codecs everywhere for byte-identical output, or
defer to the OS for smaller binaries and access to HW-accelerated
decoders.

`SkImageEncoder_NDK.cpp` plus `SkNDKConversions.cpp` provide the inverse
on Android: routing `SkPngEncoder::Encode` (and friends) through
`AImageEncoder` when `SK_ENABLE_NDK_IMAGES` is set. The conversion
table maps `SkColorType`/`SkAlphaType` to the matching
`ANDROID_BITMAP_FORMAT` constants.

---

## Cross-references

- [Text & Fonts](text-and-fonts.md) — what `SkScalerContext` and
  `SkTypeface` are and how they feed `SkFont` / `SkTextBlob`.
- [SkShaper](skshaper.md) — uses CoreText on Apple and HarfBuzz
  elsewhere, both layered on top of the font hosts above.
- [Image Decoders](image-decoders.md) — Skia's own codec stack,
  alternative to the platform image generators here.
- [Android Integration](android-integration.md) — `AHardwareBuffer`,
  animated images, and other Android-only public APIs that build on
  the NDK ports.
