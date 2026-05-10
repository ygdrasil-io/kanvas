# SkResources

`modules/skresources/` defines the **resource-provider abstraction**
shared by [Skottie](skottie.md) and the [SVG Module](svg-module.md).
Animations and SVG documents reference external assets — bitmap
images, fonts, sub-animations, audio tracks — by name or URL. Rather
than baking in any specific I/O strategy, both modules consult an
abstract `skresources::ResourceProvider` that the host implements
(or composes from the supplied building blocks).

The module is tiny: a single public header (`SkResources.h`), a
single internal helper (`SkAnimCodecPlayer.h`) and their `.cpp`
companions.

## Layout

| File | Role |
|------|------|
| `include/SkResources.h` | `ImageAsset`, `ResourceProvider`, `MultiFrameImageAsset`, `FileResourceProvider`, `CachingResourceProvider`, `DataURIResourceProviderProxy`, `ExternalTrackAsset` |
| `src/SkResources.cpp` | Default implementations and proxy plumbing |
| `src/SkAnimCodecPlayer.{h,cpp}` | Drives multi-frame `SkCodec`s (animated GIF / WebP / APNG) by time, returning the active frame as an `SkImage` |

## ResourceProvider — `include/SkResources.h`

`ResourceProvider` is a small virtual interface, all methods optional
(default: return null). Hosts override only what they need:

```cpp
sk_sp<SkData>             load(path, name)               // generic blob — used for nested animations
sk_sp<ImageAsset>         loadImageAsset(path, name, id) // image (single or multi-frame)
sk_sp<ExternalTrackAsset> loadAudioAsset(path, name, id) // audio playback hook
sk_sp<SkTypeface>         loadTypeface(name, url)        // font by reference
sk_sp<SkData>             loadFont(name, url)            // DEPRECATED — raw font bytes
```

The same instance is wired through the relevant builder
(`Skottie::Animation::Builder::setResourceProvider`,
`SkSVGDOM::Builder::setResourceProvider`).

## ImageAsset — proxying a moving picture

An `ImageAsset` is a per-image proxy that the animation re-queries
on every `seek()`. The interface separates what changes per frame
(the `SkImage` payload) from what stays constant (sampling, transform,
fit policy):

```cpp
struct FrameData {
    sk_sp<SkImage>    image;
    SkSamplingOptions sampling;        // nearest / linear / cubic / mipmap
    SkMatrix          matrix;          // pre-AE transform
    SizeFit           scaling;         // kFill / kStart / kCenter / kEnd / kNone
};

virtual FrameData getFrameData(float t);
virtual sk_sp<SkImage> getFrame(float t);   // legacy convenience
virtual bool isMultiFrame();
```

`isMultiFrame() == false` lets Skottie cache `getFrame(0)` once at
build time. For animated sources, `t` is in seconds relative to the
layer's in-point. Embedders are expected to cache and re-serve the
same `SkImage` across calls.

## Built-in implementations

The module ships several `ResourceProvider`s that compose well:

| Class | Purpose |
|-------|---------|
| `FileResourceProvider` | Reads from a base directory on the local filesystem; `loadImageAsset` pairs the bytes with `MultiFrameImageAsset` |
| `CachingResourceProvider` | Proxy that memoises `loadImageAsset` results in a `THashMap` keyed by `(path, name, id)` |
| `DataURIResourceProviderProxy` | Proxy that intercepts `data:` URIs (base64 image / font payloads inlined in the JSON) and delegates everything else to its inner provider |
| `MultiFrameImageAsset` | `ImageAsset` backed by an [SkCodec](image-decoders.md); for animated GIF / WebP / APNG it drives an internal `SkAnimCodecPlayer` |

Composition is canonical:

```cpp
auto fs   = FileResourceProvider::Make(SkString("/assets"));
auto data = DataURIResourceProviderProxy::Make(std::move(fs), strategy, fontMgr);
auto rp   = CachingResourceProvider::Make(std::move(data));
builder.setResourceProvider(std::move(rp));
```

`ResourceProviderProxyBase` is the helper base class that forwards
unimplemented hooks to the inner provider so proxies only need to
override what they specialise.

### ImageDecodeStrategy

`MultiFrameImageAsset::Make` and `FileResourceProvider::Make` accept
an `ImageDecodeStrategy`:

- **`kLazyDecode`** — the default; bytes are held until rasterisation
  and decoded on demand. Cheap to build, but can cause jank on the
  first paint of a large bitmap.
- **`kPreDecode`** — decodes every static image upfront. Higher
  build-time RAM, smoother playback.

Animated formats (`MultiFrameImageAsset` over an `SkCodec` with
multiple frames) always decode on demand via `SkAnimCodecPlayer`,
which performs frame-disposal accounting and returns the composited
RGBA frame for a given time `t`.

> Note: clients must call `SkCodecs::Register(SkPngDecoder::Decoder())`
> (and friends) before `Make` so the codec lookup can resolve. See
> [Image Decoders](image-decoders.md) for the registry.

## ExternalTrackAsset

A minimal hook for audio. The runtime calls `seek(t)` for each
animation `seek()`; negative `t` signals "out of range, stop". The
host implements the actual playback against any platform audio API.

## SkAnimCodecPlayer — `src/SkAnimCodecPlayer.h`

A thin time-driven wrapper over `SkCodec::FrameInfo` and
`SkCodec::getPixels`. Given a multi-frame codec, it tracks elapsed
time, resolves the current frame index, decodes the frame
incrementally (honouring required-prior-frame disposal), and returns
the result as an `SkImage`. `MultiFrameImageAsset` uses it to satisfy
`getFrame(t)` without bringing animated-codec knowledge into Skottie
or the SVG renderer.

## Source map

| File | Role |
|------|------|
| `skia-main/modules/skresources/src/SkResources.cpp` | All built-in providers and the `ResourceProviderProxyBase` plumbing |
| `skia-main/modules/skresources/src/SkAnimCodecPlayer.cpp` | Frame-time driver for animated `SkCodec`s |

## Cross-references

- [Skottie](skottie.md) — primary consumer; resolves Lottie image,
  font, and nested-animation references via `ResourceProvider`.
- [SVG Module](svg-module.md) — uses the same provider for
  `<image>` element references.
- [Image Decoders](image-decoders.md) — `MultiFrameImageAsset` is
  built on top of `SkCodec`, and the registry must be populated
  before construction.
- [Animated Images](animated-images.md) — broader background on
  `SkAnimCodecPlayer` and frame disposal.
