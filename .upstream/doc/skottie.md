# Skottie — Lottie / Bodymovin Player

`modules/skottie/` is Skia's player for Adobe After Effects animations
exported as **Lottie / Bodymovin** JSON. It parses the JSON once into a
retained [SkSG Scene Graph](sksg-scene-graph.md), drives the per-frame
animator stack on `seek()`, and renders the resulting tree into any
`SkCanvas` on `render()`. External assets (images, fonts, expression
evaluators) are hosted through pluggable interfaces so the same
animation can play under very different runtime environments
(desktop, mobile, [CanvasKit](canvaskit.md) in the browser).

## Module layout

| File | Role |
|------|------|
| `include/Skottie.h` | Public API: `Animation`, `Animation::Builder`, `Logger`, `MarkerObserver`, `ExpressionManager` |
| `include/SkottieProperty.h` | `PropertyObserver`, typed property handles for runtime tweaking |
| `include/SlotManager.h` | Lottie 5.11+ "slot" system — re-parameterise an animation post-build |
| `include/ExternalLayer.h` | `PrecompInterceptor` — substitute precomp layers with custom drawing |
| `include/TextShaper.h` | Shaping callback for `<text>` layers |
| `src/Skottie.cpp` | `Animation::Builder` driver, JSON DOM walk, scene assembly |
| `src/SkottiePriv.h`, `src/SkottieJson.{h,cpp}` | Parsing helpers |
| `src/Composition.{h,cpp}`, `src/Layer.{h,cpp}`, `src/Camera.{h,cpp}`, `src/Transform.cpp` | Compositions, layer types, transforms |
| `src/Adapter.h` | Bridge between animated property values and `sksg::Node` setters |
| `src/animator/` | Per-property animators (numeric, colour, path, gradient, text, expressions) |
| `src/effects/` | Effect implementations (Drop Shadow, Tint, Levels, Tritone, Glow, Motion Tile, ...) |
| `src/layers/`, `src/text/` | Layer-type implementations: shape, image, solid, null, precomp, text |
| `utils/SkottieUtils.{h,cpp}` | `CustomPropertyManager`, file-system resource provider |

## Animation lifecycle

```
   JSON bytes -> Animation::Builder -> Animation
                       |                    |  seek(t)   --> drives all internal::Animator
                       |                    |  render(c) --> sksg::RenderNode::render(c)
                       |
                       +- ResourceProvider  (images, fonts, sub-animations)
                       +- SkFontMgr         (system typeface lookup)
                       +- SkShapers::Factory (HarfBuzz / CoreText for text layers)
                       +- PropertyObserver   (notified of every named property)
                       +- MarkerObserver     (composition markers)
                       +- ExpressionManager  (After Effects expressions -> numeric/string/array)
                       +- PrecompInterceptor (replace precomp layers with native renderers)
                       +- Logger             (warnings & errors during parsing)
```

`Animation::Builder` is the configuration entry point
(`skia-main/modules/skottie/include/Skottie.h:103`). Key knobs:

- `kDeferImageLoading` — skip the eager `ImageAsset::getFrame(0)`
  resolve and only fetch frames on first `seek()`.
- `kPreferEmbeddedFonts` — use the JSON-embedded glyph paths instead
  of a host typeface match.
- `setResourceProvider`, `setFontManager`, `setTextShapingFactory`
  — same trio used by the [SVG Module](svg-module.md).
- `setPropertyObserver` — notified of each named property during
  parsing so a host can build a manipulation map keyed by Lottie
  property paths (e.g. `"layer.transform.scale"`).
- `setExpressionManager` — without this, AE expressions in the JSON
  are silently ignored.

`Builder::make` returns an `sk_sp<Animation>`. The animation is
stateless except for its current frame: `seek(t)` (normalised to
`[0, duration]`) walks all registered `internal::Animator`s, each of
which writes a per-frame value into the corresponding `sksg` node;
`render(SkCanvas*)` then walks the scene graph and emits draws.
`renderFlags` toggles top-level isolation and clipping for callers
that already know the destination is transparent or want to allow
overdraw.

## Scene assembly

Internally, `Builder::make` walks the Lottie JSON and turns each
composition into an `sksg::Group`, each layer into a chain of matrix
/ opacity / blend / mask / effect nodes wrapping the layer's content
node. Layer types and their backings:

| Lottie type | Backing |
|-------------|---------|
| Shape (`ty=4`) | `sksg::Path`, `sksg::Color`, `sksg::Gradient`, `sksg::Stroke`, `sksg::TrimEffect`, `sksg::RoundEffect` |
| Solid (`ty=1`) | `sksg::Rect` + colour fill |
| Image (`ty=2`) | `skresources::ImageAsset::getFrame(t)` -> `sksg::Image` |
| Precomp (`ty=0`) | Sub-`Composition` (or whatever `PrecompInterceptor` returns) |
| Text (`ty=5`) | `TextAdapter` shapes via the `SkShapers::Factory` and emits `sksg::TextBlob` |
| Null (`ty=3`) | Pure transform / parent target |

Effects wrap the layer's render node in image-filter or colour-filter
nodes. Masks become `sksg::ClipEffect` or `sksg::MaskEffect`. Track
mattes (the per-layer alpha / luma matte from the layer above) are
implemented by chaining a `MaskEffect` between siblings.

## Property control & slots

Two complementary tweak surfaces:

- **`PropertyObserver`** (`include/SkottieProperty.h`) is invoked
  during parsing and receives typed handles
  (`ColorPropertyHandle`, `OpacityPropertyHandle`,
  `TransformPropertyHandle`, `TextPropertyHandle`) keyed by node and
  property name. The host stores them and then calls `set()` between
  frames to mutate any non-expression-driven property at runtime.
- **`SlotManager`** (`include/SlotManager.h`) implements Lottie's
  newer named-slot mechanism — colour / image / scalar / text slots
  declared in the JSON can be rebound after build, which is how
  Lottie editors expose "themeable" animations.

`utils/SkottieUtils::CustomPropertyManager` wraps the observer
pattern in a string-keyed map for the common use case.

## Image & font assets — `skresources::ResourceProvider`

Animations rarely contain bitmaps inline; they reference external
assets by `id`. Skottie looks them up through a
[SkResources](skresources.md) `ResourceProvider`, which the host
supplies. The same provider is consulted for embedded font files
(`fontFamily` / `fontStyle`).

## External layer interception

`PrecompInterceptor::onLoadPrecomp` (in `include/ExternalLayer.h`)
lets a host swap a Lottie precomp for a custom drawing — e.g. inject
a [SkParagraph](skparagraph.md) text block, a video texture, or a
nested SVG. The replacement implements `ExternalLayer::render` and is
stitched into the scene graph alongside the native layers.

## Source map

| File | Role |
|------|------|
| `skia-main/modules/skottie/src/Skottie.cpp` | `Animation::Builder` orchestration |
| `skia-main/modules/skottie/src/Composition.cpp` | Composition walk; layer ordering & parenting |
| `skia-main/modules/skottie/src/Layer.cpp` | Layer-type dispatch, mattes, effects chain |
| `skia-main/modules/skottie/src/animator/Animator.cpp` | Frame interpolation, keyframe evaluation |
| `skia-main/modules/skottie/src/text/TextAdapter.cpp` | Text layer shaping & per-glyph animators |
| `skia-main/modules/skottie/src/effects/*.cpp` | Drop Shadow, Glow, Tint, Levels, Motion Tile, ... |

## Cross-references

- [SkSG Scene Graph](sksg-scene-graph.md) — the retained tree Skottie
  produces and re-evaluates each frame.
- [SkResources](skresources.md) — image and font resource providers.
- [Text & Fonts](text-and-fonts.md) — the shaping pipeline used for
  `<text>` layers.
- [SVG Module](svg-module.md) — same author, same `sksg` substrate, a
  related but distinct front end.
- [CanvasKit](canvaskit.md) — exposes Skottie to JavaScript as
  `CanvasKit.MakeAnimation`.
