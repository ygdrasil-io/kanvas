# SkShaper — Text Shaping Front-End

`modules/skshaper/` is the shaping layer that sits between Unicode text
and positioned glyph runs. It abstracts over three backends:

- **HarfBuzz** — the canonical shaper, used everywhere production text
  layout matters (Flutter, CanvasKit, Skia tools).
- **CoreText** — a system-shaper backend on macOS / iOS, useful when
  HarfBuzz is unavailable or when matching native rendering.
- **Primitive** — a no-op shaper that maps each code point to a single
  glyph using the typeface's cmap and lays them out by default
  advances. No kerning, no ligatures, no BiDi, no fallback. It is the
  fallback path when neither HarfBuzz nor CoreText is built in.

SkShaper does not break lines. It only produces glyphs and positions
for a given range of text + font(s). Line breaking lives in
[SkParagraph](skparagraph.md) (or in the embedder).

## Pipeline

```
   UTF-8 text + base SkFont
            │
            ▼
   ┌────────────────────────────┐
   │ FontRunIterator            │  splits text where the typeface lacks a
   │  (FontMgrRunIterator,      │  glyph and a fallback exists
   │   TrivialFontRunIterator)  │
   └────────────┬───────────────┘
                │
   ┌────────────▼───────────────┐
   │ BiDiRunIterator            │  splits at BiDi level boundaries
   │  (SkUnicode-backed)        │  even = LTR, odd = RTL
   └────────────┬───────────────┘
                │
   ┌────────────▼───────────────┐
   │ ScriptRunIterator          │  splits at script boundaries (HarfBuzz
   │  (HarfBuzz-backed)         │  hb_unicode_script(); ISO-15924 tag)
   └────────────┬───────────────┘
                │
   ┌────────────▼───────────────┐
   │ LanguageRunIterator        │  splits on locale changes (BCP-47)
   └────────────┬───────────────┘
                │
                ▼
   ┌────────────────────────────┐   For each (font, level, script, lang):
   │ SkShaper::shape(...)       │   - hb_buffer_t with cluster mapping
   │   → HarfBuzz / CoreText /  │   - hb_shape with feature[] from style
   │     primitive              │   - read back glyph IDs, advances, offsets
   └────────────┬───────────────┘   - mark line-break / unsafe-to-break
                │
                ▼
   ┌────────────────────────────┐
   │ RunHandler                 │   beginLine / runInfo / commitRunInfo /
   │  (e.g. SkTextBlobBuilder-  │   runBuffer / commitRunBuffer / commitLine
   │   RunHandler)              │
   └────────────────────────────┘
```

The four run iterators run in lockstep: each call to a shaper splits
text into ranges where *all four* iterators agree. The result is a
sequence of micro-runs that HarfBuzz can shape with a single
`hb_shape` call.

---

## SkShaper — `include/SkShaper.h`

The public abstract type. Construction is via the namespace factories
(`SkShapers::HB::*`, `SkShapers::CT::*`, `SkShapers::Primitive::*`) and
the legacy `SkShaper::Make` helpers.

```cpp
class SKSHAPER_API SkShaper {
public:
#if !defined(SK_DISABLE_LEGACY_SKSHAPER_FUNCTIONS)
    // Picks HarfBuzz (if SK_SHAPER_HARFBUZZ_AVAILABLE && SK_SHAPER_UNICODE_AVAILABLE),
    // else CoreText, else Primitive. fallback is the SkFontMgr used for missing-glyph
    // font fallback.
    static std::unique_ptr<SkShaper> Make(sk_sp<SkFontMgr> fallback = nullptr);
    static void PurgeCaches();

    static std::unique_ptr<SkShaper> MakePrimitive();
#  if defined(SK_SHAPER_HARFBUZZ_AVAILABLE)
    static std::unique_ptr<SkShaper> MakeShaperDrivenWrapper(sk_sp<SkFontMgr>);
    static std::unique_ptr<SkShaper> MakeShapeThenWrap     (sk_sp<SkFontMgr>);
    static void PurgeHarfBuzzCache();
#  endif
#  if defined(SK_SHAPER_CORETEXT_AVAILABLE)
    static std::unique_ptr<SkShaper> MakeCoreText();
#  endif
#endif

    SkShaper();
    virtual ~SkShaper();

    // ... iterators ...

    virtual void shape(const char* utf8, size_t utf8Bytes,
                       FontRunIterator&,
                       BiDiRunIterator&,
                       ScriptRunIterator&,
                       LanguageRunIterator&,
                       const Feature* features, size_t featuresSize,
                       SkScalar width,
                       RunHandler*) const = 0;
};
```

### Three HarfBuzz strategies

The HarfBuzz backend offers three line-breaking strategies, in
order of complexity:

- **`ShapeDontWrapOrReorder`** — shape the entire text as one line.
  No line breaking; no visual reorder. The caller (e.g. SkParagraph)
  will reorder/break itself.
- **`ShapeThenWrap`** — shape once, then break the resulting glyphs
  into lines using `SkBreakIterator` (line-break opportunities) and
  measured widths. Re-emits the runs with line origins.
- **`ShaperDrivenWrapper`** — break first (using ICU line-break
  information) and reshape each line independently. Used when
  per-line shaping decisions matter (e.g. Arabic justification).

`SkShaper::Make` returns `ShapeThenWrap` when both HarfBuzz and
SkUnicode are available — the best balance of speed and correctness.
SkParagraph constructs the `ShapeDontWrapOrReorder` form because it
manages its own line breaking and BiDi reorder.

---

## RunIterator hierarchy

`RunIterator` is the base of four "iterators-by-type":

```cpp
class RunIterator {
public:
    virtual void   consume()           = 0;   // advance to end of current run
    virtual size_t endOfCurrentRun()   const = 0;
    virtual bool   atEnd()             const = 0;
};

class FontRunIterator     : public RunIterator { virtual const SkFont&       currentFont    () const = 0; };
class BiDiRunIterator     : public RunIterator { virtual uint8_t             currentLevel   () const = 0; };
class ScriptRunIterator   : public RunIterator { virtual SkFourByteTag       currentScript  () const = 0; };
class LanguageRunIterator : public RunIterator { virtual const char*         currentLanguage() const = 0; };
```

Each iterator advances independently; the shaper takes
`min(endOfCurrentRun)` across all four to find the next sub-run boundary,
calls `consume()` on whichever iterator(s) just ended, and shapes the
sub-run with the current `(font, level, script, language)` tuple.

### TrivialRunIterator

A templated helper that emits *one* run covering the entire text:

```cpp
template <typename RunIteratorSubclass>
class TrivialRunIterator : public RunIteratorSubclass { /* ... */ };
class TrivialFontRunIterator     : public TrivialRunIterator<FontRunIterator>     { ... };
class TrivialBiDiRunIterator     : public TrivialRunIterator<BiDiRunIterator>     { ... };
class TrivialScriptRunIterator   : public TrivialRunIterator<ScriptRunIterator>   { ... };
class TrivialLanguageRunIterator : public TrivialRunIterator<LanguageRunIterator> { ... };
```

These let you shape simple text with a fixed font/level/script/language
without hooking into ICU or HarfBuzz iterators.

### FontRunIterator factories

```cpp
static std::unique_ptr<FontRunIterator>
MakeFontMgrRunIterator(const char* utf8, size_t utf8Bytes,
                       const SkFont& font, sk_sp<SkFontMgr> fallback);
static std::unique_ptr<FontRunIterator>
MakeFontMgrRunIterator(const char* utf8, size_t utf8Bytes, const SkFont& font,
                       sk_sp<SkFontMgr> fallback,
                       const char* requestName, SkFontStyle requestStyle,
                       const SkShaper::LanguageRunIterator*);
```

The implementation is `FontMgrRunIterator` (`src/SkShaper.cpp`):

1. Take the next code point.
2. If the base font's typeface contains a glyph for it (`unicharToGlyph`
   is non-zero), keep using the base font.
3. Else if the *current* fallback typeface still has the glyph, keep
   using it.
4. Else call `fontMgr->matchFamilyStyleCharacter(name, style, language,
   1, codePoint)` for a new fallback typeface. End the current run if a
   different typeface is found.

This is exactly how SkParagraph triggers font fallback for an emoji or
CJK glyph in the middle of a Latin string — without ever asking the
host for an explicit fallback chain.

### BiDiRunIterator factories

```cpp
static std::unique_ptr<BiDiRunIterator>
MakeBiDiRunIterator   (const char* utf8, size_t utf8Bytes, uint8_t bidiLevel);
static std::unique_ptr<BiDiRunIterator>
MakeIcuBiDiRunIterator(const char* utf8, size_t utf8Bytes, uint8_t bidiLevel);

namespace SkShapers::unicode {
SKSHAPER_API std::unique_ptr<SkShaper::BiDiRunIterator>
BidiRunIterator(sk_sp<SkUnicode>, const char* utf8, size_t utf8Bytes,
                uint8_t bidiLevel);
}
```

Walks ICU `ubidi`-derived levels (or the SkUnicode wrapper). `bidiLevel`
is the paragraph's base level (0 for LTR, 1 for RTL). A run's level is
even for left-to-right embedding and odd for right-to-left.

### ScriptRunIterator factories

```cpp
namespace SkShapers::HB {
SKSHAPER_API std::unique_ptr<SkShaper::ScriptRunIterator>
ScriptRunIterator(const char* utf8, size_t utf8Bytes);

SKSHAPER_API std::unique_ptr<SkShaper::ScriptRunIterator>
ScriptRunIterator(const char* utf8, size_t utf8Bytes, SkFourByteTag scriptHint);
}
```

The HarfBuzz iterator (`SkUnicodeHbScriptRunIterator` in
`src/SkShaper_harfbuzz.cpp`) walks the text code-point by code-point,
calling `hb_unicode_script(hb_unicode_funcs_get_default(), unichar)` to
get an ISO-15924 tag (`Latn`, `Arab`, `Hebr`, `Hani`, …). Common /
inherited scripts inherit the surrounding script.

### LanguageRunIterator factories

```cpp
static std::unique_ptr<LanguageRunIterator>
MakeStdLanguageRunIterator(const char* utf8, size_t utf8Bytes);
```

The standard implementation returns one run for the entire text using
`std::locale().name()` as the language. SkParagraph uses
`LangIterator` (`src/Iterators.h`), which derives the language from the
current `TextStyle::getLocale()` and starts a new run at locale
boundaries.

### Feature

```cpp
struct Feature {
    SkFourByteTag tag;          // OpenType feature tag, e.g. 'liga', 'smcp', 'cv01'
    uint32_t      value;        // 0 = off, 1+ = on (some features are valued)
    size_t        start;        // UTF-8 byte offset
    size_t        end;          // exclusive
};
```

A flat list of features is passed to `shape()`. HarfBuzz applies a
feature to each glyph whose cluster falls in `[start, end)`. SkParagraph
flattens its `TextStyle::getFontFeatures()` into this list.

---

## RunHandler — the output sink

The shaper streams its output through a `RunHandler` callback object.
The handler receives line + run callbacks in this order:

```
beginLine
  runInfo(run0)
  runInfo(run1)
  ...
commitRunInfo
  buffer = runBuffer(run0)         // handler allocates glyph + position storage
  // shaper writes glyphs / positions into buffer
  commitRunBuffer(run0)
  buffer = runBuffer(run1)
  commitRunBuffer(run1)
commitLine
```

The handler interface:

```cpp
class RunHandler {
public:
    struct Range  { size_t fBegin, fSize; size_t begin() const, end() const, size() const; };
    struct RunInfo {
        const SkFont&  fFont;
        uint8_t        fBidiLevel;     // even LTR, odd RTL
        SkFourByteTag  fScript;
        const char*    fLanguage;
        SkVector       fAdvance;       // total run advance
        size_t         glyphCount;
        Range          utf8Range;      // byte range in source text
    };
    struct Buffer {
        SkGlyphID* glyphs;             // [glyphCount]
        SkPoint*   positions;          // [glyphCount]
        SkPoint*   offsets;            // optional
        uint32_t*  clusters;           // optional, source-text byte index per glyph
        SkPoint    point;              // origin to add to all positions
    };

    virtual void    beginLine()                                = 0;
    virtual void    runInfo       (const RunInfo&)             = 0;  // called per run
    virtual void    commitRunInfo ()                           = 0;
    virtual Buffer  runBuffer     (const RunInfo&)             = 0;  // allocate storage
    virtual void    commitRunBuffer(const RunInfo&)            = 0;
    virtual void    commitLine    ()                           = 0;
};
```

### Buffer semantics

- If `offsets == nullptr`, glyph `i` is positioned at `positions[i]`
  (cumulative advances) — single positioning.
- If `offsets != nullptr`, glyph `i` is at `positions[i] + offsets[i]`.
  The cumulative advance is in `positions`; per-glyph mark/anchor
  offsets (e.g. combining marks above a base) are in `offsets`. This
  lets the consumer fold the offset into either a `kHorizontal_Positioning`
  or `kFull_Positioning` `SkTextBlob` run.
- `clusters[i]` is the UTF-8 byte index of the source code point that
  produced the cluster glyph `i` belongs to. Multiple glyphs in the
  same cluster (e.g. the base + marks of a Devanagari syllable) share
  a cluster index. These are the values that drive PDF tagging,
  selection, and cursor placement.

---

## SkTextBlobBuilderRunHandler — convenience adapter

`include/SkShaper.h` ships a built-in handler that streams shaper
output straight into a `SkTextBlobBuilder`:

```cpp
class SKSHAPER_API SkTextBlobBuilderRunHandler final : public SkShaper::RunHandler {
public:
    SkTextBlobBuilderRunHandler(const char* utf8Text, SkPoint offset);
    sk_sp<SkTextBlob> makeBlob();   // call after shaping is complete
    SkPoint           endPoint();   // post-shape pen position

    // RunHandler overrides
    void   beginLine() override;
    void   runInfo       (const RunInfo&) override;
    void   commitRunInfo () override;
    Buffer runBuffer     (const RunInfo&) override;
    void   commitRunBuffer(const RunInfo&) override;
    void   commitLine    () override;
};
```

Implementation (`src/SkShaper.cpp`):

- `beginLine` resets the per-line accumulators.
- `runInfo` updates the line's max ascent/descent/leading from each
  run's font metrics.
- `commitRunInfo` adjusts the current y by `−maxAscent` so the baseline
  lands at the line origin.
- `runBuffer` calls `SkTextBlobBuilder::allocRunTextPos` (the
  full-positioning + UTF-8 + cluster variant), copies the source UTF-8
  into the run, and returns a `RunHandler::Buffer` pointing at the
  blob's storage.
- `commitRunBuffer` rebases cluster indices to be 0-based within the
  run's UTF-8 range, then advances the pen by the run's advance.
- `commitLine` advances y by `maxDescent + maxLeading − maxAscent`
  (one full line height).

Use it for one-off shaping where you just want a blob:

```cpp
SkTextBlobBuilderRunHandler handler(utf8.data(), {0, 0});
shaper->shape(utf8.data(), utf8.size(),
              fontIter, bidiIter, scriptIter, langIter,
              features.data(), features.size(),
              FLT_MAX, &handler);
sk_sp<SkTextBlob> blob = handler.makeBlob();
canvas->drawTextBlob(blob, 0, 0, paint);
```

---

## Backend: SkShaper_primitive — `src/SkShaper_primitive.cpp`

A pure-C++ shaper with no third-party dependencies. Mostly used as a
bring-up backend or when HarfBuzz is unavailable.

- Maps each Unicode code point to a single glyph using
  `SkFont::unicharToGlyph`. No fallback, no shaping, no kerning, no
  ligatures, no combining marks above bases.
- Its line-break `linebreak()` helper recognises the ASCII space plus a
  curated list of Unicode breaking whitespace (EM SPACE, IDEOGRAPHIC
  SPACE, ZERO WIDTH SPACE, …) and breaks at the last whitespace that
  fit. Hard breaks (`\n`) are also honoured.
- The per-line callback emits a single LTR run covering the line's
  glyphs with default advances.

```cpp
namespace SkShapers::Primitive {
SKSHAPER_API std::unique_ptr<SkShaper>                PrimitiveText();
SKSHAPER_API std::unique_ptr<SkShaper::BiDiRunIterator>   TrivialBiDiRunIterator(size_t utf8Bytes,  uint8_t bidiLevel);
SKSHAPER_API std::unique_ptr<SkShaper::ScriptRunIterator> TrivialScriptRunIterator(size_t utf8Bytes, SkFourByteTag scriptTag);
}
```

Trivial / primitive iterators are also used as fallbacks when the
SkUnicode-backed BiDi/script iterators cannot be constructed.

---

## Backend: SkShaper_harfbuzz — `src/SkShaper_harfbuzz.cpp`

The full shaping backend (~1.5 kLOC). Wraps libharfbuzz with C++
RAII and Skia-friendly types.

### RAII wrappers

```cpp
using HBBlob   = std::unique_ptr<hb_blob_t,   SkFunctionObject<hb_blob_destroy>>;
using HBFace   = std::unique_ptr<hb_face_t,   SkFunctionObject<hb_face_destroy>>;
using HBFont   = std::unique_ptr<hb_font_t,   SkFunctionObject<hb_font_destroy>>;
using HBBuffer = std::unique_ptr<hb_buffer_t, SkFunctionObject<hb_buffer_destroy>>;
```

### Skia ↔ HarfBuzz glue

A custom set of `hb_font_funcs_t` callbacks lets HarfBuzz query
SkFonts directly (no SFNT round-trip):

| HarfBuzz callback              | Skia implementation                      |
|--------------------------------|------------------------------------------|
| `hb_font_get_nominal_glyph`    | `SkFont::unicharToGlyph`                 |
| `hb_font_get_nominal_glyphs`   | `SkFont::textToGlyphs` (UTF-32 batched)  |
| `hb_font_get_variation_glyph`  | n/a — Skia uses the nominal glyph        |
| `hb_font_get_glyph_h_advance(s)` | `SkFont::getWidths`                    |
| `hb_font_get_glyph_extents`    | `SkFont::getBounds`                      |

Positions are converted with `skhb_position`, treating
`hb_position_t` as 16.16 fixed-point.

### `hb_face_t` is built from the typeface's `head`/`hmtx`/`cmap` tables

Each `SkTypeface` is wrapped in an `hb_face_t` via a
`hb_face_create_for_tables` callback that reads tables on demand
(`SkTypeface::copyTableData`). The face is cached in a thread-safe
LRU (`HBLockedFaceCache`) keyed by `SkTypeface::uniqueID()` so the
same face is reused across shapings.

Variation axes (`fvar`) are forwarded to HarfBuzz via
`hb_font_set_variations` so OpenType `GSUB`/`GPOS` lookups operate at
the right design position.

### Shaping core

The inner `ShaperHarfBuzz::shape(utf8, ...)` builds a single
`hb_buffer_t` per micro-run:

1. `hb_buffer_set_direction` — `HB_DIRECTION_LTR` if the run's BiDi
   level is even, else `HB_DIRECTION_RTL`.
2. `hb_buffer_set_script` — the ISO-15924 tag from the script
   iterator.
3. `hb_buffer_set_language` — `hb_language_from_string(lang)`.
4. `hb_buffer_add_utf8` — appends the run text and assigns each code
   point a cluster index equal to its UTF-8 offset.
5. `hb_shape(font, buffer, features, num_features)`.
6. Read back `hb_buffer_get_glyph_infos` and
   `hb_buffer_get_glyph_positions`. For each glyph, store
   `(glyphID, cluster, x_offset, y_offset, x_advance)` in a
   `ShapedGlyph`.
7. Mark glyph properties:
   - `fHasVisual` from `extents.width != 0 || extents.height != 0`.
   - `fUnsafeToBreak` from `hb_glyph_info_get_glyph_flags`.
   - `fGraphemeBreakBefore` from a precomputed `SkBreakIterator` over
     the run's UTF-8.
   - `fMustLineBreakBefore` / `fMayLineBreakBefore` similarly.

The intermediate types are:

```cpp
struct ShapedGlyph {
    SkGlyphID fID;
    uint32_t  fCluster;
    SkPoint   fOffset;
    SkVector  fAdvance;
    bool fMayLineBreakBefore, fMustLineBreakBefore;
    bool fHasVisual;
    bool fGraphemeBreakBefore;
    bool fUnsafeToBreak;
};
struct ShapedRun {
    SkShaper::RunHandler::Range  fUtf8Range;
    SkFont                       fFont;
    SkBidiIterator::Level        fLevel;
    SkFourByteTag                fScript;
    const char*                  fLanguage;
    std::unique_ptr<ShapedGlyph[]> fGlyphs;
    size_t                       fNumGlyphs;
    SkVector                     fAdvance;
};
struct ShapedLine { TArray<ShapedRun> runs; SkVector fAdvance; };
```

### Visual reorder per line

When emitting a line, the backend builds a level array
`runLevels[i] = lineRuns[i].fLevel`, calls
`SkUnicode::reorderVisual(runLevels, count, logicalFromVisual[])`, and
emits the runs in *visual* order. RTL glyphs are also reversed inside
each run so the consumer (e.g. `SkTextBlobBuilder`) sees glyphs in
left-to-right visual order — this keeps PDF readers and the GPU subrun
system happy.

### `ShaperDrivenWrapper` vs `ShapeThenWrap` vs `ShapeDontWrapOrReorder`

The three subclasses of `ShaperHarfBuzz` differ only in their
`wrap(...)` implementation:

| Subclass                    | Strategy                                                                                  |
|-----------------------------|-------------------------------------------------------------------------------------------|
| `ShaperDrivenWrapper`       | Use `SkBreakIterator(line)` to find break points *first*, then re-shape each line.        |
| `ShapeThenWrap`             | Shape once, then walk shaped glyphs and break at `fMayLineBreakBefore` opportunities.     |
| `ShapeDontWrapOrReorder`    | Emit one line containing all runs in *logical* order; caller (SkParagraph) handles wrap.  |

The first two enforce `width` and produce multiple `commitLine()`
callbacks; the third sets `width = ∞` and emits one line.

### Cache lifecycle

`PurgeCaches()` clears the typeface → `hb_face_t` LRU. SkParagraph
calls this when its `FontCollection::clearCaches()` is invoked.

---

## Backend: SkShaper_coretext — `src/SkShaper_coretext.cpp`

A minimal CoreText backend used on Apple platforms. Construct via
`SkShapers::CT::CoreText()`. It uses `CTLineCreateWithAttributedString`
and walks the resulting `CTRun`s, copying glyphs / positions into the
`RunHandler::Buffer`. Single line; no font fallback (CoreText handles
that internally); no BiDi reordering (also internal). Used by Skia
tests on macOS / iOS to validate parity with native text rendering.

---

## SkShaper_skunicode helper — `src/SkShaper_skunicode.cpp`

`include/SkShaper_skunicode.h` exposes a single factory that builds an
SkUnicode-backed BiDi iterator:

```cpp
namespace SkShapers::unicode {
std::unique_ptr<SkShaper::BiDiRunIterator>
BidiRunIterator(sk_sp<SkUnicode>, const char* utf8, size_t utf8Bytes,
                uint8_t bidiLevel);
}
```

Its purpose is to keep the HarfBuzz backend's compile-time dependency
on ICU optional — when SkUnicode is built with the ICU4X backend (no
ICU), the same `SkUnicode::makeBidiIterator` interface is used to
populate per-position levels.

---

## Factory abstraction — `include/SkShaper_factory.h`

The `Factory` class lets a host pre-bind an SkUnicode + SkFontMgr pair
into a reusable shaper-builder:

```cpp
namespace SkShapers {

class SKSHAPER_API Factory : public SkRefCnt {
public:
    virtual std::unique_ptr<SkShaper>                 makeShaper          (sk_sp<SkFontMgr> fallback) = 0;
    virtual std::unique_ptr<SkShaper::BiDiRunIterator>  makeBidiRunIterator (const char* utf8, size_t utf8Bytes, uint8_t bidiLevel) = 0;
    virtual std::unique_ptr<SkShaper::ScriptRunIterator> makeScriptRunIterator(const char* utf8, size_t utf8Bytes, SkFourByteTag) = 0;
    virtual SkUnicode*                                 getUnicode()       = 0;
};

namespace Primitive {
SKSHAPER_API sk_sp<Factory> Factory();   // builds a SkUnicode-less, no-shape factory
}

}
```

CanvasKit and Skia tools use a `Factory` so they can pick the
shaping/Unicode backend at build time and pass a single object around
instead of three disconnected `Make*` calls.

---

## SkShaper::PurgeCaches

A global flush of HarfBuzz caches, exposed through the legacy
`SkShaper::PurgeCaches` and the namespace-qualified
`SkShapers::HB::PurgeCaches`. Embedders call this on memory pressure
or font-collection reset.

---

## Cross-references

- **Unicode** — every BiDi level, script tag, line break, and grapheme
  boundary used here comes from [SkUnicode](skunicode.md).
- **Fonts** — `FontRunIterator` walks `SkFontMgr::matchFamilyStyleCharacter`;
  `SkShaper` calls `SkFont::unicharToGlyph`/`textToGlyphs`/
  `getWidths`/`getBounds` to feed HarfBuzz. See
  [Text & Fonts](text-and-fonts.md).
- **Paragraph layout** — [SkParagraph](skparagraph.md) builds custom
  iterator implementations (`LangIterator`) and consumes the shaper
  output through its own `RunHandler` (`OneLineShaper`).
- **Text blob output** — `SkTextBlobBuilderRunHandler` produces a
  drawable `SkTextBlob`; see [Text & Fonts](text-and-fonts.md) for
  the blob format.
