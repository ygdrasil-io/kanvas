# Text & Fonts

This document covers Skia's core text and font stack — everything from
opening a font file to producing a positioned `SkTextBlob` that the
canvas can render. Higher-level layout (line breaking, BiDi reordering,
shaping with HarfBuzz) lives in the modules `skparagraph`, `skshaper`,
and `skunicode` and is documented separately:

- [SkShaper](skshaper.md) — Unicode → glyph + positioning (HarfBuzz / CoreText)
- [SkUnicode](skunicode.md) — script, BiDi, line/word/grapheme break detection
- [SkParagraph](skparagraph.md) — multi-style paragraph layout

Platform-specific font hosts (FreeType, CoreText, DirectWrite,
Fontations, Android NDK) are described in
[Platform Ports](platform-ports.md). The PDF backend's font subsetting
and PostScript metrics live in [PDF Backend](pdf-backend.md).

## Pipeline at a glance

```
                 ┌────────────────┐
  text bytes ─►  │  SkTextBlob    │  built by SkTextBlobBuilder, or returned by
  + SkFont       │  (immutable)   │  the shaper / paragraph layout
                 └─────┬──────────┘
                       │ blobToGlyphRunList
                       ▼
                 ┌────────────────┐
                 │ sktext::       │  one GlyphRun per typeface+font
                 │ GlyphRunList   │  (positions, glyph IDs, optional UTF-8 + clusters)
                 └─────┬──────────┘
                       │
        ┌──────────────┴───────────────┐
        ▼                              ▼
  ┌────────────┐                ┌────────────────┐
  │ skcpu::    │                │ sktext::gpu::  │
  │ GlyphRun   │                │ SubRun /       │
  │ ListPainter│                │ SubRunContainer│
  └─────┬──────┘                └────────┬───────┘
        │  scaler context cache         │  GPU strikes / atlas
        ▼                                ▼
  ┌────────────┐                ┌────────────────┐
  │ SkScaler   │  ───── glyph ──►   atlas (GPU)  │
  │ Context    │                │  +  raster path /
  │ (FreeType, │                │  SDF / mask    │
  │  CoreText, │                └────────────────┘
  │  DWrite,   │
  │  Fontations│
  │  …)        │
  └─────┬──────┘
        │ outline / bitmap / colr / svg / drawable
        ▼
  ┌────────────┐
  │  raster    │
  │  blitter   │
  └────────────┘
```

A single `SkFont` carries a typeface plus rendering knobs (size, scale,
skew, hinting, edging). The font is resolvable into glyph IDs via
`textToGlyphs` / `unicharToGlyph`, and into per-glyph metrics or paths
via `getWidths` / `getBounds` / `getPos` / `getPath`. Once you have
glyph IDs and positions you can assemble them into a run inside a
`SkTextBlob`, which is the form `SkCanvas::drawTextBlob` consumes.

---

## SkFont — `include/core/SkFont.h`

`SkFont` is a value type (with `sk_is_trivially_relocatable = std::true_type`)
holding a `sk_sp<SkTypeface>` and rendering knobs. It is the input to
every text-drawing primitive on `SkCanvas`.

### Construction

```cpp
SkFont();                                                 // empty typeface, default size
explicit SkFont(sk_sp<SkTypeface> typeface);              // size 12, scale 1, skew 0
SkFont(sk_sp<SkTypeface> typeface, SkScalar size);
SkFont(sk_sp<SkTypeface> typeface, SkScalar size, SkScalar scaleX, SkScalar skewX);
```

`scaleX` simulates condensed/expanded fonts, `skewX` simulates oblique.
The `SkFont` becomes the equivalent of CSS `font-stretch` (via scale)
and `font-style: oblique` (via skew). `setSize(<=0)` is a no-op.

`makeWithSize(SkScalar)` returns a copy with a new size; convenient for
deriving fonts from a configured base.

### Knobs

The font carries a small bitmask (`fFlags`) and three uint8 enums:

| Flag                        | Setter / getter                         |
|-----------------------------|-----------------------------------------|
| `kForceAutoHinting_PrivFlag`| `setForceAutoHinting`/`isForceAutoHinting` |
| `kEmbeddedBitmaps_PrivFlag` | `setEmbeddedBitmaps`/`isEmbeddedBitmaps`   |
| `kSubpixel_PrivFlag`        | `setSubpixel`/`isSubpixel`                 |
| `kLinearMetrics_PrivFlag`   | `setLinearMetrics`/`isLinearMetrics`       |
| `kEmbolden_PrivFlag`        | `setEmbolden`/`isEmbolden`                 |
| `kBaselineSnap_PrivFlag`    | `setBaselineSnap`/`isBaselineSnap`         |

`Edging` (`getEdging()`/`setEdging()`):

```cpp
enum class Edging {
    kAlias,             // no transparent pixels on glyph edges
    kAntiAlias,         // may have transparent pixels on glyph edges
    kSubpixelAntiAlias, // glyph positioned in pixel using transparency (LCD)
};
```

`SkFontHinting` (from `SkFontTypes.h`):

```cpp
enum class SkFontHinting { kNone, kSlight, kNormal, kFull };
```

`getMetrics()` returns the recommended line spacing
(`descent − ascent + leading`) and optionally fills an
`SkFontMetrics` struct (see below). `getSpacing()` is a shortcut.

### Text → glyph

```cpp
size_t textToGlyphs(const void* text, size_t byteLength,
                    SkTextEncoding, SkSpan<SkGlyphID> glyphs) const;
size_t countText  (const void* text, size_t byteLength, SkTextEncoding) const;
SkGlyphID unicharToGlyph(SkUnichar uni) const;
void      unicharsToGlyphs(SkSpan<const SkUnichar> src, SkSpan<SkGlyphID> dst) const;
```

`SkTextEncoding` (from `SkFontTypes.h`): `kUTF8`, `kUTF16`, `kUTF32`,
`kGlyphID`. UTF code points are mapped one-to-one with glyphs using
the typeface's default cmap — there is *no* shaping, kerning, or
fallback. Unmapped code points become glyph 0. UTF-8 with an invalid
sequence yields zero glyphs.

If `glyphs.size() < required`, no glyphs are written; the function
returns the count needed so the caller can resize.

### Per-glyph measurement

```cpp
SkScalar measureText(const void* text, size_t byteLength,
                     SkTextEncoding, SkRect* bounds = nullptr,
                     const SkPaint* = nullptr) const;

void getWidthsBounds(SkSpan<const SkGlyphID> glyphs,
                     SkSpan<SkScalar>        widths,
                     SkSpan<SkRect>          bounds,
                     const SkPaint*) const;
void getWidths      (SkSpan<const SkGlyphID>, SkSpan<SkScalar>)        const;
SkScalar getWidth   (SkGlyphID) const;
void getBounds      (SkSpan<const SkGlyphID>, SkSpan<SkRect>,
                     const SkPaint*) const;
SkRect getBounds    (SkGlyphID, const SkPaint*) const;

void getPos (SkSpan<const SkGlyphID>, SkSpan<SkPoint>, SkPoint origin = {0,0}) const;
void getXPos(SkSpan<const SkGlyphID>, SkSpan<SkScalar>, SkScalar origin = 0)   const;

std::vector<SkScalar> getIntercepts(SkSpan<const SkGlyphID>,
                                    SkSpan<const SkPoint>,
                                    SkScalar top, SkScalar bottom,
                                    const SkPaint* = nullptr) const;

std::optional<SkPath> getPath(SkGlyphID glyphID) const;
void getPaths(SkSpan<const SkGlyphID> glyphIDs,
              void (*proc)(const SkPath* pathOrNull, const SkMatrix& mx, void* ctx),
              void* ctx) const;

SkScalar getMetrics(SkFontMetrics* metrics) const;
SkScalar getSpacing() const;
```

The `SkPaint*` argument is consulted for stroke width, `SkPathEffect`,
and `SkMaskFilter` — these alter glyph bounds (`measureText` ignores
mask filters and clip but *does* honour stroke + path effect). For
positioning queries (`getPos`/`getXPos`) glyphs are placed using their
default advances starting at `origin`.

`getIntercepts` returns alternating `[start, end]` x-pairs where a
horizontal slab (`top..bottom`, in font local units relative to the
baseline) crosses the glyphs — used to compute
underline-and-overstrike gaps, see
`SkParagraph`'s `Decorations`.

`getPath`/`getPaths` extract glyph outlines as `SkPath`. A glyph
without an outline (e.g. a bitmap-only emoji) returns `std::nullopt`
or invokes `proc` with `pathOrNull == nullptr`.

The `SK_SUPPORT_UNSPANNED_APIS` block at the bottom mirrors every
span-based API as a `(ptr, count)` overload for legacy callers.

### Comparison and dump

```cpp
bool operator==(const SkFont& other) const;   // compares all knobs + typeface ptr
bool operator!=(const SkFont& other) const;
void dump() const;                            // SkDebugf
```

Two fonts with logically identical typefaces but different `SkTypeface*`
pointers compare unequal. This matters for caching: glyph caches key on
the typeface ID, not the pointer.

---

## SkFontMetrics — `include/core/SkFontMetrics.h`

Returned by `SkFont::getMetrics`. All metrics are scaled by `SkFont`
size and follow Skia's y-down convention (so `fAscent` is typically
negative, `fDescent` positive).

```cpp
struct SK_API SkFontMetrics {
    enum FontMetricsFlags {
        kUnderlineThicknessIsValid_Flag = 1 << 0,
        kUnderlinePositionIsValid_Flag  = 1 << 1,
        kStrikeoutThicknessIsValid_Flag = 1 << 2,
        kStrikeoutPositionIsValid_Flag  = 1 << 3,
        kBoundsInvalid_Flag             = 1 << 4,
    };
    uint32_t fFlags;
    SkScalar fTop;                // greatest extent above origin (≤ ascent), ≤ 0
    SkScalar fAscent;             // distance to reserve above baseline, ≤ 0
    SkScalar fDescent;            // distance to reserve below baseline, ≥ 0
    SkScalar fBottom;             // greatest extent below origin (≥ descent)
    SkScalar fLeading;            // recommended gap between lines, ≥ 0
    SkScalar fAvgCharWidth;       // 0 if unknown
    SkScalar fMaxCharWidth;       // 0 if unknown
    SkScalar fXMin, fXMax;        // greatest extents to left/right of origin
    SkScalar fXHeight, fCapHeight;
    SkScalar fUnderlineThickness, fUnderlinePosition;
    SkScalar fStrikeoutThickness, fStrikeoutPosition;

    bool hasUnderlineThickness(SkScalar*) const;
    bool hasUnderlinePosition (SkScalar*) const;
    bool hasStrikeoutThickness(SkScalar*) const;
    bool hasStrikeoutPosition (SkScalar*) const;
    bool hasBounds()                    const;  // ! kBoundsInvalid_Flag
};
```

`fTop`/`fBottom`/`fXMin`/`fXMax` reflect the actual maximal extents of
*all* glyphs in the typeface and are deprecated for variable fonts —
they cannot adapt to instance-specific axes.

The recommended line height is `descent − ascent + leading`; this is
exactly what `SkFont::getSpacing()` returns.

---

## SkTypeface — `include/core/SkTypeface.h`

`SkTypeface` is the immutable, ref-counted handle to a font resource
(file, in-memory blob, or system font ID). It extends `SkWeakRefCnt`
because the strike cache holds *weak* references — the typeface can be
purged from the system but the cache won't keep it alive.

A typeface is opaque: the same family can be backed by FreeType,
CoreText, DirectWrite, Fontations, or a custom factory, all of which
implement the virtual interface in this header. Concrete subclasses
live under `src/ports/` (see [Platform Ports](platform-ports.md)).

### Identity & style

```cpp
SkFontStyle  fontStyle()    const;     // weight/width/slant
bool         isBold()       const;
bool         isItalic()     const;
bool         isFixedPitch() const;
SkTypefaceID uniqueID()     const;     // never 0; 32-bit
static bool  Equal(const SkTypeface*, const SkTypeface*); // null-safe

static sk_sp<SkTypeface> MakeEmpty();   // a typeface with no glyphs
```

`uniqueID` is the cache key for strikes; equality of two typefaces is
*pointer* equality. For value equality use `Equal`.

### Variations / palettes

```cpp
int getVariationDesignPosition (SkSpan<SkFontArguments::VariationPosition::Coordinate>) const;
int getVariationDesignParameters(SkSpan<SkFontParameters::Variation::Axis>)             const;

sk_sp<SkTypeface> makeClone(const SkFontArguments&) const;
```

`getVariationDesignPosition` reports the typeface's location in
variation design space (one `(tag, value)` per axis). `getVariation
DesignParameters` reports each axis' min/def/max plus a hidden flag
(see `SkFontParameters.h`). `makeClone` returns a new typeface with a
new variation position and/or palette overrides — see
`SkFontArguments` below. The default implementation may return `this`
if the requested arguments match.

### Cmap / glyph counts

```cpp
void unicharsToGlyphs(SkSpan<const SkUnichar>, SkSpan<SkGlyphID>) const;
size_t textToGlyphs(const void* text, size_t byteLength, SkTextEncoding,
                    SkSpan<SkGlyphID>) const;
SkGlyphID unicharToGlyph(SkUnichar) const;
int countGlyphs() const;
```

These mirror the corresponding `SkFont` methods but at the typeface
level (no size/scale/skew) — they reflect the typeface's cmap directly.

### SFNT / TrueType tables

```cpp
int    countTables () const;
int    readTableTags(SkSpan<SkFontTableTag>) const;
size_t getTableSize(SkFontTableTag) const;
size_t getTableData(SkFontTableTag, size_t offset, size_t length, void* data) const;
sk_sp<SkData> copyTableData(SkFontTableTag) const;
int    getUnitsPerEm() const;
```

The four-byte `SkFontTableTag` is machine-endian. Table contents are
returned in their native (big) endian. `MAX_REASONABLE_TABLE_COUNT`
is `(1 << 16) - 1`. Table introspection is used by the PDF backend
(font subsetting) and by external libraries like HarfBuzz that take
table blobs through Skia.

### Kerning

```cpp
bool getKerningPairAdjustments(SkSpan<const SkGlyphID> glyphs,
                               SkSpan<int32_t> adjustments) const;
```

Adjustments are in the typeface's design units. Calling with empty
spans returns whether the typeface supports kerning at all — many
fonts return false unconditionally.

### Names

```cpp
struct LocalizedString { SkString fString, fLanguage; };
class  LocalizedStrings {
    virtual bool next(LocalizedString*) = 0;
    void unref();              // delete this
};
LocalizedStrings* createFamilyNameIterator() const;
void  getFamilyName    (SkString* name) const;
bool  getPostScriptName(SkString* name) const;   // may vary with variation axes
int   getResourceName  (SkString* name) const;   // file path / URL, if available
```

`createFamilyNameIterator` walks every name table entry; the caller
must `unref()` the iterator. `getFamilyName` returns the typeface's
canonical UTF-8 family name. `getPostScriptName` returns the
PostScript name used by the PDF backend.

### Streaming & serialization

```cpp
std::unique_ptr<SkStreamAsset> openStream        (int* ttcIndex) const;
std::unique_ptr<SkStreamAsset> openExistingStream(int* ttcIndex) const; // no work if not cached

enum class SerializeBehavior {
    kDoIncludeData,
    kDontIncludeData,
    kIncludeDataIfLocal,
};
void               serialize(SkWStream*, SerializeBehavior = kIncludeDataIfLocal) const;
sk_sp<SkData>      serialize(SerializeBehavior = kIncludeDataIfLocal)            const;
static sk_sp<SkTypeface> MakeDeserialize(SkStream*, sk_sp<SkFontMgr> lastResortMgr);

using FactoryId = SkFourByteTag;
static void Register(FactoryId, sk_sp<SkTypeface>(*)(std::unique_ptr<SkStreamAsset>,
                                                     const SkFontArguments&));
```

`openStream` returns the raw font bytes; `ttcIndex` is set to the
typeface's index inside a TrueType collection (or 0 for a standalone
file). `openExistingStream` is a non-blocking variant: if producing a
stream would require I/O (e.g. re-reading a system font), it returns
nullptr.

`serialize` writes a *signature* (family name, style, factory id,
optional embedded data) sufficient for `MakeDeserialize` to reconstruct
the typeface. `kIncludeDataIfLocal` is the default — it embeds the
font bytes only when the typeface owns its data, so a serialized
`SkPicture` can replay portably without bloating when the font is a
system font. `Register` lets ports plug in custom factories indexed
by a four-byte tag.

### Bounds & scaler context

```cpp
SkRect getBounds() const;     // union of glyph bounds at 1pt
std::unique_ptr<SkScalerContext>
       createScalerContext(const SkScalerContextEffects&, const SkDescriptor*) const;
```

`getBounds` is computed lazily (`SkOnce fBoundsOnce`) by
`onComputeBounds`. The scaler context is the per-strike object that
talks to the underlying font engine — `SkFontPriv`/`SkStrikeSpec` use
it to hydrate `SkGlyph` records (advance, mask, path).

### Subclass interface (for ports)

The `protected:` block enumerates the methods every backend implements:
`onMakeClone`, `onCreateScalerContext`, `onFilterRec`,
`onGetAdvancedMetrics` (PDF), `getPostScriptGlyphNames`,
`getGlyphToUnicodeMap`, `onOpenStream`, `onGetVariationDesign*`,
`onCharsToGlyphs`, `onGetUPEM`, `onGetFamilyName`, `onGetTableTags`,
`onGetTableData`, `onComputeBounds`, etc. The CT/CG backend additionally
exposes `internal_private_getCTFontRef` so platform code can interop
with raw `CTFontRef`.

---

## SkFontArguments / SkFontParameters

### `SkFontArguments` — `include/core/SkFontArguments.h`

The "what to ask for" struct passed to `SkTypeface::makeClone` and
`SkFontMgr::makeFromStream`. Three pieces:

```cpp
struct SkFontArguments {
    struct VariationPosition {
        struct Coordinate { SkFourByteTag axis; float value; };
        const Coordinate* coordinates;
        int               coordinateCount;
    };
    struct Palette {
        struct Override { uint16_t index; SkColor color; };
        int             index;
        const Override* overrides;
        int             overrideCount;
    };
    SkFontArguments();
    SkFontArguments& setCollectionIndex   (int);                    // .ttc / .dfont index
    SkFontArguments& setVariationDesignPosition(VariationPosition); // not copied
    SkFontArguments& setPalette          (Palette);                 // for COLRv1 etc.

    int               getCollectionIndex()           const;
    VariationPosition getVariationDesignPosition()   const;
    Palette           getPalette()                   const;
};
```

Variation coordinates are `(axisTag, value)` pairs; an unspecified axis
keeps its default. A specified axis tag not present in the font is
silently ignored. Callers must keep the underlying arrays alive for
the lifetime of the `SkFontArguments` (they are *not* copied).

`Palette` selects a CPAL palette index for color fonts and lets clients
override individual entries — useful for theming COLRv1 emoji.

### `SkFontParameters` — `include/core/SkFontParameters.h`

Read-side counterpart. `SkTypeface::getVariationDesignParameters` fills
an array of:

```cpp
struct Axis {
    SkFourByteTag tag;
    float min, def, max;
    bool  isHidden() const;     // axis is recommended hidden in UI
    void  setHidden(bool);
private:
    uint16_t flags;             // bit 0 = HIDDEN
};
```

`Axis` is constexpr-constructible, so it can be embedded in tables of
known fonts.

### `SkFontStyle` — `include/core/SkFontStyle.h`

A 32-bit packed `(weight, width, slant)` triple matching the OpenType
OS/2 conventions:

```cpp
enum Weight {
    kInvisible_Weight   =    0,
    kThin_Weight        =  100,
    kExtraLight_Weight  =  200,
    kLight_Weight       =  300,
    kNormal_Weight      =  400,
    kMedium_Weight      =  500,
    kSemiBold_Weight    =  600,
    kBold_Weight        =  700,
    kExtraBold_Weight   =  800,
    kBlack_Weight       =  900,
    kExtraBlack_Weight  = 1000,
};
enum Width {
    kUltraCondensed_Width = 1, kExtraCondensed_Width = 2, kCondensed_Width = 3,
    kSemiCondensed_Width  = 4, kNormal_Width         = 5, kSemiExpanded_Width = 6,
    kExpanded_Width       = 7, kExtraExpanded_Width  = 8, kUltraExpanded_Width = 9,
};
enum Slant : uint8_t { kUpright_Slant, kItalic_Slant, kOblique_Slant };
```

Constructors clamp out-of-range weight/width to their bounds. There
are static convenience helpers: `Normal()`, `Bold()`, `Italic()`,
`BoldItalic()`. Equality compares the packed `int32_t`. Used as both
input (`SkFontMgr::matchFamilyStyle`) and output (`SkTypeface::fontStyle`).

### `SkFontTypes` — `include/core/SkFontTypes.h`

Shared enums used across the text stack:

```cpp
enum class SkTextEncoding { kUTF8, kUTF16, kUTF32, kGlyphID };
enum class SkFontHinting  { kNone, kSlight, kNormal, kFull };
```

---

## SkFontMgr — `include/core/SkFontMgr.h`

`SkFontMgr` is the system-font catalogue: the source of typefaces by
name, by character, or by raw bytes. It is abstract; concrete
implementations live in `src/ports/SkFontMgr_*` (FreeType-based,
CoreText, DirectWrite, Android NDK, Fontations, etc.).

### Enumeration

```cpp
int  countFamilies()                                   const;
void getFamilyName(int index, SkString* familyName)    const;
sk_sp<SkFontStyleSet> createStyleSet(int index)        const;
```

These iterate top-level families (typically excluding hidden /
auto-activated fonts on macOS).

### Matching

```cpp
sk_sp<SkFontStyleSet> matchFamily      (const char familyName[]) const;
sk_sp<SkTypeface>     matchFamilyStyle (const char familyName[],
                                        const SkFontStyle&)      const;
sk_sp<SkTypeface>     matchFamilyStyleCharacter(
        const char     familyName[],
        const SkFontStyle&,
        const char*    bcp47[],     // [0] least significant, [N-1] most
        int            bcp47Count,
        SkUnichar      character) const;

sk_sp<SkTypeface>     legacyMakeTypeface(const char familyName[], SkFontStyle) const;
```

`matchFamily` always returns a non-null style set (potentially empty).
`matchFamilyStyle` returns `nullptr` if no good match is found; passing
`nullptr` for `familyName` requests the system default font.

`matchFamilyStyleCharacter` is the **font-fallback** entrypoint: given
a family preference, a style, a list of language tags, and a single
code point, it returns a typeface that contains a glyph for the code
point. `bcp47` codes are ordered from least to most significant — a
French Canadian fallback might pass `["en-CA", "fr-CA"]`. If no
fallback exists for the language list, *any* font containing the
character may be returned. This is what `SkShaper`'s
`FontMgrRunIterator` uses to switch fonts on a CJK code point inside
an English string.

`legacyMakeTypeface` is a convenience that combines lookup + fallback
to a system default — used in older code paths where `SkPaint::setTypeface`
took a family name.

### Loading from bytes / files

```cpp
sk_sp<SkTypeface> makeFromData  (sk_sp<SkData>,                   int ttcIndex = 0) const;
sk_sp<SkTypeface> makeFromStream(std::unique_ptr<SkStreamAsset>, int ttcIndex = 0) const;
sk_sp<SkTypeface> makeFromStream(std::unique_ptr<SkStreamAsset>, const SkFontArguments&) const;
sk_sp<SkTypeface> makeFromFile  (const char path[],              int ttcIndex = 0) const;
```

The two-arg `makeFromStream` overload accepts a full `SkFontArguments`
and can therefore preselect a variation position or palette.

### Empty manager

```cpp
static sk_sp<SkFontMgr> RefEmpty();    // satisfies the API; matches nothing
```

The empty manager is useful for headless tests and for embedders that
want to avoid pulling in a system font enumerator.

### Subclass surface (`onXxx`)

Every public method delegates to a virtual `onXxx`. Ports implement
the on-methods; the base `SkFontMgr` provides argument validation.

---

## SkFontStyleSet — also in `SkFontMgr.h`

```cpp
class SK_API SkFontStyleSet : public SkRefCnt {
public:
    virtual int               count() = 0;
    virtual void              getStyle(int index, SkFontStyle*, SkString* style) = 0;
    virtual sk_sp<SkTypeface> createTypeface(int index)                           = 0;
    virtual sk_sp<SkTypeface> matchStyle    (const SkFontStyle& pattern)          = 0;
    static  sk_sp<SkFontStyleSet> CreateEmpty();
protected:
    sk_sp<SkTypeface> matchStyleCSS3(const SkFontStyle& pattern);
};
```

A style set represents all faces of one family. `matchStyleCSS3`
implements the CSS 3 font-matching algorithm (closest weight under the
"4-step" rule) and is the common helper used by ports that want
predictable matching.

---

## SkFontScanner — `include/core/SkFontScanner.h`

Used by the asset font managers (e.g. `TypefaceFontProvider` from
SkParagraph) and by `SkFontMgr` ports to inspect the *contents* of a
font stream without fully constructing typefaces. The factory id lets
the host pick a backend (`SkTypeface::Register`).

```cpp
class SkFontScanner : public SkNoncopyable {
public:
    virtual bool scanFile  (SkStreamAsset*, int* numFaces) const = 0;
    virtual bool scanFace  (SkStreamAsset*, int faceIndex,
                            int* numInstances) const = 0;
    virtual bool scanInstance(
        SkStreamAsset*, int faceIndex, int instanceIndex,
        SkString* name, SkFontStyle*, bool* isFixedPitch,
        AxisDefinitions* axes, VariationPosition* position) const = 0;
    virtual sk_sp<SkTypeface> MakeFromStream(
        std::unique_ptr<SkStreamAsset>, const SkFontArguments&) const = 0;
    virtual SkFourByteTag getFactoryId() const = 0;
};
```

`numInstances` enumerates a font's *named instances* (fvar named
instances) so a font picker can list "Roboto Condensed Bold" and
"Roboto Condensed Light" as separate styles even when they share an
underlying variation axis.

---

## SkTextBlob — `include/core/SkTextBlob.h`

`SkTextBlob` is the immutable, ref-counted (`SkNVRefCnt`) container of
prebaked glyph runs that `SkCanvas::drawTextBlob` consumes. A blob has
a conservative bounding box, a unique 32-bit ID, and a payload of
runs allocated as a single contiguous tail using a custom
`operator new`. The implementation lives in
`src/core/SkTextBlob.cpp` (~1.1k lines).

### Run positioning

A run is a sequence of glyphs sharing one `SkFont`. The internal
`GlyphPositioning` enum (private) selects how glyph positions are
encoded; user code chooses by picking the matching builder method:

| `allocRun*` method      | Storage                                       | Use                                |
|-------------------------|-----------------------------------------------|------------------------------------|
| `allocRun`              | one `(x, y)`, glyph advances                  | fastest path; one-line text        |
| `allocRunPosH`          | one `y`, per-glyph `x`                        | rendered LTR runs                  |
| `allocRunPos`           | per-glyph `(x, y)`                            | shaper output                      |
| `allocRunRSXform`       | per-glyph `SkRSXform` (rot+scale+translate)   | text on a path / 2-D placed text   |
| `allocRunText*`         | as above + UTF-8 bytes + cluster indices      | needed for accessibility / a11y    |

Each `RunBuffer` exposes:

```cpp
struct RunBuffer {
    SkGlyphID* glyphs;
    SkScalar*  pos;       // count, 2*count, or 4*count scalars depending on positioning
    char*      utf8text;
    uint32_t*  clusters;  // monotonic UTF-8 byte indices
    SkPoint*   points()  const { return reinterpret_cast<SkPoint*>(pos); }
    SkRSXform* xforms()  const { return reinterpret_cast<SkRSXform*>(pos); }
};
```

The caller fills `glyphs` (always) and `pos` (depending on positioning)
before calling another `allocRun*` or `make()`. `utf8text` and
`clusters` carry the original text and the cluster-of-each-glyph
indices needed by accessibility, PDF tagging, and the GPU
`Slug`/`SubRun` system.

### Construction shortcuts

`SkTextBlob` exposes static helpers that build a single-run blob
without the intermediate `SkTextBlobBuilder`:

```cpp
static sk_sp<SkTextBlob> MakeFromText     (const void* text, size_t byteLength,
                                           const SkFont&, SkTextEncoding = kUTF8);
static sk_sp<SkTextBlob> MakeFromString   (const char*, const SkFont&,
                                           SkTextEncoding = kUTF8);
static sk_sp<SkTextBlob> MakeFromPosTextH (const void* text, size_t byteLength,
                                           SkSpan<const SkScalar> xpos,
                                           SkScalar constY, const SkFont&,
                                           SkTextEncoding = kUTF8);
static sk_sp<SkTextBlob> MakeFromPosText  (const void*, size_t,
                                           SkSpan<const SkPoint> pos,
                                           const SkFont&, SkTextEncoding = kUTF8);
static sk_sp<SkTextBlob> MakeFromRSXform  (const void*, size_t,
                                           SkSpan<const SkRSXform>,
                                           const SkFont&, SkTextEncoding = kUTF8);

// Convenience for already-shaped glyph IDs:
static sk_sp<SkTextBlob> MakeFromPosHGlyphs   (SkSpan<const SkGlyphID>,
                                               SkSpan<const SkScalar>, SkScalar,
                                               const SkFont&);
static sk_sp<SkTextBlob> MakeFromPosGlyphs    (SkSpan<const SkGlyphID>,
                                               SkSpan<const SkPoint>, const SkFont&);
static sk_sp<SkTextBlob> MakeFromRSXformGlyphs(SkSpan<const SkGlyphID>,
                                               SkSpan<const SkRSXform>, const SkFont&);
```

These do *no* shaping — Unicode code points map one-to-one with glyphs
through the typeface's default cmap. Use [SkShaper](skshaper.md) or
[SkParagraph](skparagraph.md) for proper shaping.

### Bounds, intercepts, identity

```cpp
const SkRect& bounds()  const;   // conservative bounding box
uint32_t      uniqueID() const;  // never zero; stable for the blob's lifetime

int getIntercepts(const SkScalar bounds[2], SkScalar intervals[],
                  const SkPaint* = nullptr) const;
```

`getIntercepts` returns alternating `[start, end]` x-pairs where the
horizontal slab `[bounds[0], bounds[1]]` (in text-baseline-relative
coordinates) crosses the rendered glyphs in any non-RSXform run. RSXform
runs are skipped. Pass `intervals == nullptr` to query the count first.

### Iteration

```cpp
class SK_API Iter {
public:
    struct Run { SkTypeface* fTypeface; int fGlyphCount; const SkGlyphID* fGlyphIndices; };
    Iter(const SkTextBlob&);
    bool next(Run*);          // public, stable
    bool experimentalNext(ExperimentalRun*);  // EXPERIMENTAL: also yields SkFont + SkPoint*
};
```

`Iter` walks runs in the order they were allocated. Internal callers
prefer `SkTextBlobRunIterator` (`src/core/SkTextBlobPriv.h`) which
also exposes positioning, clusters, UTF-8 text, and the `SkRSXform`
storage — see the conversion to `GlyphRunList` below.

### Serialization

```cpp
size_t        serialize  (const SkSerialProcs&, void* memory, size_t memory_size) const;
sk_sp<SkData> serialize  (const SkSerialProcs&)                                   const;
static sk_sp<SkTextBlob> Deserialize(const void*, size_t, const SkDeserialProcs&);
```

`SkSerialProcs::fTypefaceProc` (and the matching deserializer) lets
embedders override how typefaces are encoded — for example to embed a
cross-process font registry ID rather than full font data.

### Cache callbacks

```cpp
void notifyAddedToCache(uint32_t cacheID, PurgeDelegate) const;
```

The blob can be a key into one or more downstream caches (e.g. the
GPU strike). When the blob is destroyed it invokes the registered
`PurgeDelegate` so the cache evicts associated entries deterministically.

### Storage layout

The blob's `RunRecord` payload is allocated in a single
`sk_malloc`'d block following the `SkTextBlob` header — see the
custom `operator new(size_t, void*)` overrides. This minimises
allocations for short text and lets the entire blob live in one
contiguous chunk that the GPU recorder can hash efficiently.

---

## sktext::GlyphRun and GlyphRunList — `src/text/GlyphRun.h`

A `GlyphRun` is a *non-owning view* of one positioned run: glyph IDs,
positions, optional UTF-8 + cluster indices, and optional RSXform
"scaled-rotation" components (`fSCos`, `fSSin`). It always has a
`SkFont` (with encoding rewritten to `kGlyphID` and left-aligned).

```cpp
class GlyphRun {
public:
    GlyphRun(const SkFont&,
             SkSpan<const SkPoint>     positions,
             SkSpan<const SkGlyphID>   glyphIDs,
             SkSpan<const char>        text,
             SkSpan<const uint32_t>    clusters,
             SkSpan<const SkVector>    scaledRotations);

    size_t                  runSize()         const;
    SkSpan<const SkPoint>   positions()       const;
    SkSpan<const SkGlyphID> glyphsIDs()       const;
    SkZip<const SkGlyphID, const SkPoint> source() const;
    const SkFont&           font()            const;
    SkSpan<const uint32_t>  clusters()        const;
    SkSpan<const char>      text()            const;
    SkSpan<const SkVector>  scaledRotations() const;
};
```

A `GlyphRunList` is a slice of runs that share an origin and conservative
bounds, optionally backed by a source `SkTextBlob`:

```cpp
class GlyphRunList {
public:
    GlyphRunList(const SkTextBlob*, SkRect bounds, SkPoint origin,
                 SkSpan<const GlyphRun>, GlyphRunBuilder*);
    GlyphRunList(const GlyphRun&,    const SkRect& bounds,
                 SkPoint origin,    GlyphRunBuilder*);

    uint64_t   uniqueID()              const;  // SkTextBlob::uniqueID() or SK_InvalidUniqueID
    bool       anyRunsLCD()            const;  // any run uses kSubpixelAntiAlias
    bool       canCache()              const;  // we have an SkTextBlob
    bool       hasRSXForm()            const;
    size_t     runCount()              const;
    size_t     totalGlyphCount()       const;
    size_t     maxGlyphRunSize()       const;
    sk_sp<SkTextBlob> makeBlob()       const;
    SkPoint    origin()                const;
    SkRect     sourceBounds()          const;
    SkRect     sourceBoundsWithOrigin()const;
    const SkTextBlob* blob()           const;
};
```

`makeBlob()` reconstructs an `SkTextBlob` from the runs, choosing
`allocRunPos` or `allocRunRSXform` based on whether any run has
scaled-rotations. `temporaryShuntBlobNotifyAddedToCache` plumbs the
blob's purge delegate from the GPU strike layer.

### `GlyphRunBuilder`

The reusable factory that:

- materializes a list from a `SkTextBlob` (`blobToGlyphRunList`),
- materializes a list directly from raw text + font (`textToGlyphRunList`),
- converts an `SkRSXform[]` array to parallel `(position, scaledRotation)`
  arrays (`convertRSXForm`).

Internally it caches scratch buffers so the `(SkPoint*, SkVector*)`
storage is reused across draws. `blobToGlyphRunList` translates each
`SkTextBlobRunIterator::positioning()` value to the right cursor
strategy:

- `kDefault_Positioning` — synthesize positions from glyph advances,
- `kHorizontal_Positioning` — pair each `x` with the run's `offset.y`,
- `kFull_Positioning` — point directly into the blob's memory,
- `kRSXform_Positioning` — split each `SkRSXform` into a
  `(translate, scaleCos+sin)` pair.

The conservative bounds are computed by `glyphrun_source_bounds`,
which prefers the typeface's overall bounds (`SkFontPriv::GetFontBounds`)
and falls back to per-glyph rects when the typeface reports an empty
bounding box (a common font bug).

---

## Glyph caches — `src/core/SkGlyph.h`, `SkScalerContext.h`

Once you have positioned glyph IDs you need rasterized images. That
work happens inside the **strike cache**:

- **`SkPackedGlyphID`** — 32 bits packing `glyphID` (16 bits) plus two
  sub-pixel positions (2 bits each). Sub-pixel keys keep the strike
  cache compact while supporting `Edging::kSubpixelAntiAlias`.
- **`SkGlyph`** — the hydrated glyph record: advance, bounds, mask
  format (A8 / LCD / ARGB / SDF / path), an optional `SkPath` for
  outline draws, and an optional `SkDrawable` (color / SVG / COLRv1).
- **`SkScalerContext`** — the per-strike object created by
  `SkTypeface::createScalerContext`. Backends (FreeType, CoreText,
  DirectWrite, Fontations, custom) implement `generateAdvance`,
  `generateMetrics`, `generateImage`, `generatePath`, `generateDrawable`.
  Configured by `SkScalerContextRec` (size, matrix, hinting, edging…).
- **`SkStrike`** — caches `SkGlyph`s keyed by `SkPackedGlyphID` for one
  scaler-context configuration.
- **`SkStrikeCache`** — global LRU of strikes. Purged on memory
  pressure; `SkTypeface` weak-refs let it survive font deletion.
- **`SkBulkGlyphMetrics`** — cheap front for "give me metrics for
  these N glyph IDs" (used by `glyphrun_source_bounds`).

The CPU painter consumes glyphs directly via
`skcpu::GlyphRunListPainter`. The GPU paths sit behind a `StrikeForGPU`
abstraction:

```cpp
namespace sktext {
class StrikeForGPU : public SkRefCnt {
    virtual void lock(); virtual void unlock();
    virtual SkGlyphDigest digestFor(skglyph::ActionType, SkPackedGlyphID) = 0;
    virtual bool prepareForImage   (SkGlyph*) = 0;
    virtual bool prepareForPath    (SkGlyph*) = 0;
    virtual bool prepareForDrawable(SkGlyph*) = 0;
    virtual const SkDescriptor& getDescriptor() const = 0;
    virtual const SkGlyphPositionRoundingSpec& roundingSpec() const = 0;
    virtual SkStrikePromise strikePromise() = 0;
};

class SkStrikePromise {           // sk_sp<SkStrike> | unique_ptr<SkStrikeSpec>
    explicit SkStrikePromise(sk_sp<SkStrike>&&);
    explicit SkStrikePromise(const SkStrikeSpec&);
    static std::optional<SkStrikePromise> MakeFromBuffer(
            SkReadBuffer&, const SkStrikeClient*, SkStrikeCache*);
    void  flatten(SkWriteBuffer&) const;
    SkStrike* strike();           // resolve lazily
    void resetStrike();
    const SkDescriptor& descriptor() const;
};
```

`StrikeForGPU` is the surface the GPU `SubRunContainer`
(`src/text/gpu/SubRunContainer.h`) talks to. `SkStrikePromise` lets a
glyph reference travel across processes (SkStrikeServer / SkStrikeClient)
or between recording and replay: a recorded
`Slug` carries promises that resolve to actual strikes when the recording
is replayed.

The full GPU subrun pipeline (mask atlas, SDF, transformed path, drawable)
is documented under [GPU Overview](gpu-overview.md); here we only note
that everything flows through `GlyphRunList → StrikeForGPU → SubRunContainer`.

---

## SFNT helpers — `src/sfnt/`

The `src/sfnt/` directory contains *reading-only* C++ structs that
mirror the binary layout of OpenType / TrueType tables. They are not a
font parser; they are a typed view over big-endian raw bytes. Backends
that load a font from a stream cast the table data to these structs to
read fields directly.

All types live behind two macros:

- `SK_OT_*` — OpenType primitive types, big-endian:
  `SK_OT_BYTE`/`SK_OT_CHAR`/`SK_OT_SHORT`/`SK_OT_USHORT`/`SK_OT_LONG`/
  `SK_OT_ULONG`/`SK_OT_Fixed` (16.16)/`SK_OT_F2DOT14` (2.14)/
  `SK_OT_FWORD`/`SK_OT_UFWORD`/`SK_OT_LONGDATETIME` (Mac epoch).
- `SkOTTableTAG<T>::value` — a constexpr big-endian `SK_OT_ULONG`
  derived from the table's `TAG0..TAG3` characters. So
  `SkOTTableHead::TAG == 'head'` in big endian.

`SkSFNTHeader` (12 bytes) is the file header — `numTables` plus a
binary-search index — followed by a directory of
`TableDirectoryEntry { tag, checksum, offset, logicalLength }`.
`fontType` distinguishes the four sfnt flavours: Windows TrueType
(0x00010000), Mac TrueType (`'true'`), PostScript (`'typ1'`), and
OpenType-CFF (`'OTTO'`).

The headers cover the standard set of tables Skia needs to read:

| Header                    | Table | Purpose                                                       |
|---------------------------|-------|---------------------------------------------------------------|
| `SkOTTable_head.h`        | head  | font-wide metrics, units-per-em, bounding box, mac-style bits |
| `SkOTTable_hhea.h`        | hhea  | horizontal header (ascent/descent/lineGap, advance count)     |
| `SkOTTable_hmtx.h`        | hmtx  | per-glyph horizontal metrics (advance, lsb)                   |
| `SkOTTable_maxp.h` + `_TT.h`/`_CFF.h` | maxp | maximum profile (number of glyphs, etc.)         |
| `SkOTTable_OS_2.h` + `_V0..V4.h`, `_VA.h` | OS/2 | Windows-side metrics, weight/width, fsSelection, panose |
| `SkOTTable_name.h`/`.cpp` | name  | localized name table (family, subfamily, postscript, ...)     |
| `SkOTTable_post.h`        | post  | PostScript metrics + glyph names                              |
| `SkOTTable_loca.h`        | loca  | glyph offsets for `glyf`                                      |
| `SkOTTable_glyf.h`        | glyf  | TrueType outline glyphs                                       |
| `SkOTTable_fvar.h`        | fvar  | variation axes + named instances                              |
| `SkOTTable_gasp.h`        | gasp  | grid-fitting / smoothing PPEM thresholds                      |
| `SkOTTable_EBDT.h`/`EBLC.h`/`EBSC.h` | embedded bitmap data / location / scaling           |
| `SkOTTable_sbix.h`        | sbix  | Apple PNG bitmap strikes                                      |
| `SkOTTable_OS_2_VA.h`     | OS/2 v0-2 *variant A* layouts (some early TrueType files)     |
| `SkOTTable_OS_2_V4.h`     | OS/2 v4 — adds fsSelection bits 7-9 (typo metrics, oblique, …)|

Every `_OS_2_V*.h` header defines the union arm for one OS/2 table
revision (V0, V1, V2, V3, V4, plus the legacy V*A* variant).
`SkOTTableOS2::Version` is a discriminated union that lets backends
read whichever revision the font ships.

### Companion utilities

- **`SkOTTableTypes.h`** — the `SK_OT_*` typedefs, `SkOTTableTAG`,
  `SkOTSetUSHORTBit`/`SkOTSetULONGBit` (constexpr endian-aware bit
  setters), and `SK_OT_BYTE_BITFIELD` (a portable bit-field macro that
  preserves declaration order across compilers).
- **`SkOTTable_name.cpp`** — the only sfnt source with significant
  logic. Implements `SkOTTableName::Iterator`, the Mac-roman/Mac-script
  → UTF-8 conversion tables, and language-tag mapping. It is invoked
  by ports that don't have a native localized-name iterator.
- **`SkOTUtils.h`/`.cpp`** — helpers used by ports:
  `SkOTUtils::CalcTableChecksum`, `SkOTUtils::RemovePaintTables` (PDF
  subsetting workaround), and the fallback
  `LocalizedStrings_NameTable` / `LocalizedStrings_SingleName`
  implementations of `SkTypeface::LocalizedStrings`.
- **`SkSFNTHeader.h`** — the file-level entry point.
- **`SkTTCFHeader.h`** — TrueType collection header (`'ttcf'`).
- **`SkPanose.h`** — the panose-1 classification (`bFamilyType`,
  `bSerifStyle`, …) used by some matchers.
- **`SkIBMFamilyClass.h`** — the OS/2 IBM family-class enumeration
  used for matching and PDF tagging.

These structs are used pervasively by `src/ports/` typeface
implementations (FreeType, CoreText, DirectWrite, Fontations) and by
the PDF backend, which reads `head`, `OS/2`, `name`, `post`, and
`fvar` to emit advanced font metrics.

### What is *not* here

There is no parser for `cmap`, `GSUB`, `GPOS`, `GDEF`, `BASE`, `MATH`,
`COLR`, `CPAL`, `CBDT`, `CBLC`, `STAT`, `MVAR`, `HVAR`, `VVAR`, or any
of the OpenType layout tables — those are parsed by HarfBuzz inside
`SkShaper_harfbuzz` (see [SkShaper](skshaper.md)), or by FreeType /
CoreText / DirectWrite for the rasterizer. Skia itself touches only the
metadata it needs to subset, match, and serialize.

---

## Cross-references

- **Drawing** — `SkCanvas::drawTextBlob`/`drawGlyphs`/`drawString`
  produce a `GlyphRunList` and route it to `skcpu::GlyphRunListPainter`
  (CPU) or the GPU subrun system. See [Canvas & Recording API](canvas-and-recording.md).
- **PDF** — `SkPDFFont` (`src/pdf/`) drives font subsetting using the
  SFNT helpers and `SkAdvancedTypefaceMetrics` (returned by
  `SkTypeface::onGetAdvancedMetrics`).
- **GPU** — `src/text/gpu/SubRunContainer.cpp` consumes
  `GlyphRunList`s and produces atlas / path / drawable subruns; see
  [GPU Overview](gpu-overview.md).
- **CanvasKit** — exposes the same `SkFont`, `SkTypeface`,
  `SkTextBlob`, `SkParagraph`, and `SkUnicode` APIs to JavaScript;
  see [CanvasKit](canvaskit.md).
