# Color Management Pipeline

Status: Draft
Date: 2026-06-13

## Purpose

Define the target color-management contract for the GPU-first renderer.

`12-blend-color-target-state.md` defines `GPUBlendPlan`, `GPUColorPlan`, and
`GPUTargetState` as the bridge between material, layer, filter, blend, and
target writes. This spec defines the complete color-management model those
plans consume: color-space descriptors, ICC/CICP/profile metadata, transfer
functions, gamut, alpha domain, precision, interpolation spaces, color
uniforms, image decode color facts, HDR/gainmap policy, WGSL/CPU transform
parity, diagnostics, and validation gates.

This is a target-complete spec. It is not an implementation slice. The first
rect/rrect plus solid/linear-gradient slice may validate a small SDR subset,
but the target model must already explain how unsupported wide-gamut, HDR, and
profile-dependent behavior is promoted or refused.

The target is Graphite-inspired but Kanvas-owned:

- every color value has an explicit domain before shader, filter, blend,
  layer, or store work consumes it;
- untagged source color behavior is policy, not an accident;
- color-space transforms are structured descriptors shared by CPU reference and
  WGSL implementations;
- `layout(color)` runtime-effect uniforms, image profiles, gradient stops,
  filter outputs, layer targets, and final stores use the same value-spec
  vocabulary;
- unsupported or unvalidated conversions refuse with stable diagnostics instead
  of silently reinterpreting bytes or falling back to CPU-rendered textures.

## Source Specs

This spec depends on:

- `00-architecture-kernel.md` for module, naming, and Graphite evidence policy;
- `01-normalized-draw-commands.md` for captured material, image, layer,
  filter, text, vertices, and target facts;
- `03-material-key-wgsl.md` for `MaterialKey`, material color-space
  requirements, and WGSL module assembly;
- `31-material-source-paint-pipeline.md` for paint colors, solid sources,
  gradient stops, image shader sources, material-source color diagnostics, and
  source payload handoff;
- `04-pipeline-key-cache-resources.md` for cache, resource, and device
  generation policy;
- `05-routing-policy.md` for `GPUNative`, `CPUPreparedGPU`,
  `CPUReferenceOnly`, and `RefuseDiagnostic`;
- `07-validation-conformance.md` for evidence and promotion gates;
- `09-draw-family-support-matrix.md` for color family evidence and refusals;
- `10-gpu-execution-context-submission.md` for target/surface color facts and
  readback evidence;
- `11-wgsl-layout-binding-abi.md` for WGSL color-transform uniform/storage
  layout and reflection;
- `12-blend-color-target-state.md` for `GPUBlendPlan`, `GPUColorPlan`, and
  `GPUTargetState`;
- `13-performance-telemetry-cache-gates.md` for color telemetry, caches, and
  performance gates;
- `16-material-dictionary-and-snippet-registry.md` for color helper snippets;
- `17-payload-gathering-and-slots.md` for color transform payloads and uniform
  values;
- `18-texture-image-ownership.md` for sampled texture/image color ownership
  facts;
- `20-destination-read-strategy.md` for destination-copy and destination-sample
  color interpretation;
- `21-text-glyph-pipeline.md` for text, SDF, bitmap glyph, color glyph, SVG,
  and palette color facts;
- `22-image-bitmap-codec-pipeline.md` for encoded image profiles, CICP/ICC,
  bit depth, premul, HDR/gainmap, and upload color plans;
- `23-filter-effect-pipeline.md` for filter DAG color behavior and color
  filter placement;
- `26-draw-vertices-mesh-pipeline.md` for per-vertex color and primitive-color
  blending;
- `27-registered-runtime-effects-registry.md` for color uniforms,
  `layout(color)`-like behavior, and runtime-effect color facts;
- `28-layer-savelayer-execution.md` for layer source, filter, restore,
  offscreen target, F16, and color-space restoration behavior.

The older `.upstream/target/high-performance-wgsl-pipeline-target.md` color
management section remains product/evidence context. This spec is the
GPU-renderer target contract.

## Skia And Graphite Evidence

Relevant local evidence lives under
`/Users/chaos/workspace/kanvas-forge/skia-main/`.

Useful source landmarks:

- Skia's `SkColorInfo` combines color type, alpha type, and color space as the
  core image/target color descriptor.
- Skia's `SkColorSpace` and `SkColorSpaceXformSteps` model source-to-destination
  transforms, including transfer functions, gamut transforms, and alpha-domain
  transitions.
- `SkConvertPixels` proves that color conversion, swizzle, premul/unpremul,
  bit-depth conversion, and alpha-only conversion are explicit pixel
  operations with refused invalid conversions.
- Graphite `PaintParams::Color4fPrepForDst()` transforms public sRGB color
  values to the destination color info before building paint state.
- Graphite `Image_Graphite` carries `SkColorInfo` with texture views and can
  reinterpret color space without changing underlying texture bytes.
- Graphite context copy/convert paths use source and destination color info,
  YUV color spaces, and `SkColorSpaceXformSteps` as explicit conversion data.
- Skia picture versions record gradient colors with color spaces and working
  color-space shader output control, showing that shader/interpolation color
  domains are semantic data.
- Skia runtime-effect private helpers expose color-uniform transform behavior
  and destination color-space facts.
- Skia image codecs expose ICC, CICP/YUV-like color facts, bit depth,
  gainmaps, HDR metadata, and alpha/premul policy through decode plans.

Kanvas adopts these invariants:

- color type, alpha type, precision, transfer, gamut, and color-space role are
  explicit plan facts;
- transforms are planned and dumpable before CPU or WGSL execution;
- source colors, gradients, images, runtime-effect uniforms, filters, layers,
  blenders, and stores state their input and output value specs;
- profile-aware behavior has reference fixtures or stable refusal;
- destination/surface encoding is not inferred from final pixels alone.

Kanvas intentionally does not copy:

- Skia class ownership, `SkColorSpace` object identity, or C++ conversion
  pipeline internals;
- Ganesh or Graphite backend abstractions;
- SkSL color transform helpers;
- accepting platform codec color conversion as conformance without a descriptor;
- silent fallback to CPU-rendered full draws, layers, filters, images, or
  scenes when color conversion is unsupported.

## Ownership Boundary

This spec owns:

- `GPUColorManagementPlan`;
- `GPUColorSpaceDescriptor`;
- `GPUColorSpaceID`;
- `GPUColorProfileDescriptor`;
- `GPUICCProfileDescriptor`;
- `GPUCICPDescriptor`;
- `GPUTransferFunctionDescriptor`;
- `GPUGamutDescriptor`;
- `GPUColorValueSpec`;
- `GPUAlphaDomain`;
- `GPUColorSpaceRole`;
- `GPUPrecisionDomain`;
- `GPUColorEncoding`;
- `GPUColorConversionPlan`;
- `GPUColorTransformPlan`;
- `GPUWorkingColorSpacePlan`;
- `GPUGradientColorPlan`;
- `GPUImageColorManagementPlan`;
- `GPUColorUniformPlan`;
- `GPUHDRColorPlan`;
- `GPUGainmapPlan`;
- `GPUColorStorePlan`;
- `GPUColorCachePlan`;
- `GPUColorBudgetPolicy`;
- `GPUColorDiagnostic`.

Owned by other specs:

- blend selection and target attachment state: `12-blend-color-target-state.md`;
- image decode and upload artifact creation: `22-image-bitmap-codec-pipeline.md`;
- texture/view/sampler ownership: `18-texture-image-ownership.md`;
- material key construction and WGSL snippets: `03-material-key-wgsl.md` and
  `16-material-dictionary-and-snippet-registry.md`;
- filter DAG execution and color-filter placement:
  `23-filter-effect-pipeline.md`;
- layer offscreen target and restore/composite execution:
  `28-layer-savelayer-execution.md`;
- runtime-effect descriptor registration:
  `27-registered-runtime-effects-registry.md`;
- payload writing and resource binding: `17-payload-gathering-and-slots.md`.

`GPUColorPlan` from `12-blend-color-target-state.md` consumes the objects in
this spec. It remains the per-draw/layer/filter bridge used by route selection
and target-state planning. This spec owns the detailed color-management facts
and transform contracts that keep `GPUColorPlan` unambiguous.

## Core Objects

| Object | Purpose |
|---|---|
| `GPUColorManagementPlan` | Top-level color plan for one recording, target, or route family. |
| `GPUColorSpaceID` | Stable ID for a built-in or registered color space descriptor. |
| `GPUColorSpaceDescriptor` | Dumpable color-space descriptor: role, profile, transfer, gamut, white point, range, and version. |
| `GPUColorProfileDescriptor` | Common descriptor for ICC, CICP, named, or raw/untagged profile facts. |
| `GPUICCProfileDescriptor` | ICC profile identity, hash, parsed facts, supported transform status, and diagnostic provenance. |
| `GPUCICPDescriptor` | CICP or codec color metadata: color primaries, transfer characteristics, matrix coefficients, range, and bit-depth expectations. |
| `GPUTransferFunctionDescriptor` | Transfer function identity and parameters: sRGB, linear, gamma, PQ, HLG, or refused/custom. |
| `GPUGamutDescriptor` | Gamut/primaries/white-point descriptor and matrix-to-XYZ or equivalent transform facts. |
| `GPUColorValueSpec` | Value domain for a color at a pipeline boundary: alpha domain, color-space role, precision, encoding, and range. |
| `GPUAlphaDomain` | Alpha interpretation: opaque, premul, unpremul, raw, coverage, or destination. |
| `GPUColorSpaceRole` | Role: sRGB, destination, working, explicit descriptor, image source, layer, raw bytes, or untagged policy. |
| `GPUPrecisionDomain` | Precision domain: U8/unorm, U16/unorm, F16, F32, packed 10-bit, or descriptor-specific. |
| `GPUColorEncoding` | Numeric encoding and channel order: linear float, nonlinear unorm, packed, alpha-only, luminance, YUV-derived, or raw. |
| `GPUColorConversionPlan` | Accepted/refused source-to-destination conversion with alpha, transfer, gamut, precision, clamp, and tolerance facts. |
| `GPUColorTransformPlan` | CPU/WGSL executable transform descriptor and validation evidence. |
| `GPUWorkingColorSpacePlan` | Active working-space selection for shaders, filters, runtime effects, gradients, and layer restore. |
| `GPUGradientColorPlan` | Gradient stop color-space, interpolation, premul policy, hue policy when accepted, and transform requirements. |
| `GPUImageColorManagementPlan` | Image-source color/profile plan consumed by `GPUImageColorDecodePlan` and `GPUColorPlan`. |
| `GPUColorUniformPlan` | Color-uniform role, transform behavior, `layout(color)`-like policy, defaults, and packing facts. |
| `GPUHDRColorPlan` | HDR transfer, range, luminance metadata, tone-map/refusal policy, and target capability requirements. |
| `GPUGainmapPlan` | Gainmap pairing, metadata, reconstruction/tone-map policy, source image dependencies, and refusal behavior. |
| `GPUColorStorePlan` | Final store or intermediate-store conversion, clamp, dither, quantization, and target format facts. |
| `GPUColorCachePlan` | Transform, profile parse, LUT, matrix, shader helper, and evidence cache policy. |
| `GPUColorBudgetPolicy` | Profile size, LUT size, transform count, shader helper size, precision, and telemetry budgets. |
| `GPUColorDiagnostic` | Structured accepted/refused diagnostic product for color behavior. |

These objects live under `org.graphiks.kanvas.gpu.renderer` package
responsibilities. Public names keep `GPU`, `CPU`, and `WGSL` uppercase.

## Value Spec Model

`GPUColorValueSpec` is required at every color pipeline boundary where color,
alpha, precision, or space can affect output.

It records:

- alpha domain;
- color-space role;
- explicit color-space descriptor ID when role is explicit;
- precision domain;
- numeric encoding;
- channel order or swizzle when relevant;
- value range: normalized, extended, scene-referred, display-referred, raw, or
  descriptor-specific;
- clamp, preserve-out-of-range, or refuse policy;
- premul/unpremul conversion policy;
- tolerance class for CPU/GPU comparisons;
- diagnostic label.

Required boundaries:

- public/source paint color;
- gradient stop input;
- gradient interpolation output;
- image/bitmap sample before and after decode conversion;
- text/glyph color, palette color, bitmap glyph sample, SVG glyph color, and
  color glyph composite inputs;
- vertex color attribute and primitive-color output;
- shader/material output;
- color-filter input/output;
- runtime-effect color uniforms and child outputs;
- filter node input/output;
- layer source, layer filter output, and restore composite input;
- blend source and destination input;
- coverage multiplication boundary;
- destination-read sample;
- intermediate target store/load;
- final target store and readback.

Rules:

- A route may not consume a color value whose spec is unknown.
- Raw bytes must be converted, tagged, or refused before blend or target store.
- Premul and unpremul values must not be silently reinterpreted.
- Nonlinear encoded values must not be used in linear math unless the plan
  explicitly accepts that behavior.
- Extended-range values must declare whether they are preserved, clamped,
  tone-mapped, or refused.

## Supported Color Space Surface

The target color-space surface is:

| Color space / metadata | Target status | Required behavior |
|---|---|---|
| sRGB | `TargetRequired` | Named descriptor, transfer, gamut, untagged-source default lane when policy accepts it, CPU/WGSL transforms, final store evidence. |
| Linear sRGB | `TargetRequired` | Linear working/intermediate descriptor and conversion to/from sRGB. |
| Display P3 / DCI-P3 family | `TargetRequired` | Wide-gamut descriptor, profile/CICP mapping, gamut transform, target capability/refusal. |
| Rec. 709 | `TargetRequired` | Video/image metadata mapping, transfer/range policy, conversion to working/target space. |
| Rec. 2020 SDR | `DependencyGated` | Descriptor and conversion accepted only with evidence for gamut and transfer behavior. |
| ICC v2/v4 profiles | `DependencyGated` | Parse/profile hash, supported profile-class subset, matrix/TRC or LUT policy, cache, and refusal for unrepresented profiles. |
| CICP metadata | `TargetRequired` for metadata capture, `DependencyGated` for all transforms | Primaries, transfer, matrix, range, and bit-depth facts captured even when conversion refuses. |
| Planar YUV/YUVA | `DependencyGated` | Plane descriptors, CICP/matrix/range, chroma siting, subsampling, per-plane texture ownership, WGSL sampling route or prepared interleaved conversion, and refusal. |
| PQ HDR | `DependencyGated` | HDR descriptor, luminance metadata, tone-map or preserve route, target capability, and refusal. |
| HLG HDR | `DependencyGated` | HLG descriptor, scene/display policy, target capability, and refusal. |
| Gainmap HDR | `DependencyGated` | Base image, gainmap image, metadata, reconstruction/tone-map route, and refusal. |
| Raw/untagged | `PolicyGated` | Explicit untagged policy: assume sRGB, inherit destination, raw bytes, or refuse. |
| Custom transfer/gamut | `RefuseDiagnostic` until registered | No ad hoc transform; descriptor registration and evidence required. |

The renderer may support a smaller subset in early implementation slices, but
the target diagnostic surface must distinguish metadata capture, transform
support, target capability, and promotion evidence.

## Profile, Pixel, And Transform Detail

Color descriptors must be precise enough to explain Graphite-like
`SkColorInfo` and `SkColorSpaceXformSteps` behavior without importing Skia
classes.

`GPUColorValueSpec` records the color type equivalent:

- channel count and channel roles;
- component type: unorm, snorm, uint, sint, float, or packed;
- bit depth per component;
- channel order and swizzle;
- alpha type and alpha-domain transition;
- normalized, extended, display-referred, scene-referred, or raw range;
- texture/sample format when the value is read from a `GPU` resource.

`GPUColorSpaceDescriptor` records:

- named color-space ID when built in;
- profile descriptor reference when profile-backed;
- primaries, white point, and matrix-to-XYZ or equivalent PCS facts;
- transfer function descriptor;
- range and matrix coefficients for video-derived sources;
- default rendering intent when a profile exposes one;
- white-point adaptation method when source and destination white points
  differ;
- descriptor version and hash.

`GPUICCProfileDescriptor` records:

- ICC version;
- device/profile class;
- color model;
- PCS;
- rendering intents exposed by the profile;
- matrix/TRC tags when accepted;
- A2B/B2A LUT presence, dimensions, interpolation policy, and precision;
- unsupported tags, malformed data, or policy refusals;
- parser version and profile hash.

Target ICC policy:

- matrix/TRC RGB display profiles are the first normative dependency-gated ICC
  class;
- LUT-backed RGB display profiles require explicit LUT resource and
  interpolation evidence before promotion;
- CMYK, Lab-only, abstract, device-link, named-color, malformed, or
  multi-processing profiles refuse until a registered descriptor and evidence
  exist;
- rendering intent is explicit: perceptual, relative colorimetric,
  saturation, absolute colorimetric, or refused;
- black-point compensation is disabled unless a later descriptor makes it
  deterministic and testable.

`GPUColorTransformPlan` must define:

- operation order: alpha conversion, transfer linearization, white-point
  adaptation, gamut transform, transfer encoding, clamp/quantize;
- matrix precision and rounding policy;
- LUT dimension, address mode, interpolation mode, and resource layout when
  LUT-backed;
- CPU tolerance and GPU tolerance separately when needed;
- exact refusal if CPU and WGSL cannot share the same descriptor.

Tolerance classes are named and dumpable. A promoted transform cannot rely on
"close enough" without recording channel tolerances, alpha tolerance,
out-of-range policy, and fixture ID.

## Retag, Raw Preserve, And Conversion Semantics

Color-space reinterpretation is explicit. The planner chooses exactly one
semantic action:

| Action | Meaning | Required proof |
|---|---|---|
| `ConvertColorValues` | Numeric values are transformed from source value spec to destination value spec. | `GPUColorConversionPlan` plus CPU/WGSL transform evidence. |
| `RetagColorSpace` | Underlying bytes or sampled values are preserved, but future consumers interpret them with a different descriptor. | Source and destination encodings are byte-compatible, no math boundary observes the old meaning, and diagnostics state the retag reason. |
| `PreserveRawBytes` | Values remain raw/opaque until a later registered consumer interprets them. | No blend, filter, material math, store conversion, or comparison consumes them as color. |
| `RejectUnknownColor` | The route refuses. | Stable `unsupported.color.*` diagnostic. |

Rules:

- Retagging is forbidden across transfer, gamut, alpha, precision, or range
  differences that affect a later math boundary.
- Retagging may be used for metadata-only changes to imported/provider-owned
  textures only when texture ownership, target usage, and downstream consumers
  all accept the new descriptor.
- A final store cannot retag; it either stores raw target bytes by policy or
  uses an accepted conversion.
- Readback evidence states whether it reports raw target bytes, retagged
  interpretation, or converted comparison pixels.

## Working Space Policy

`GPUWorkingColorSpacePlan` selects the active working color space for a
material/filter/layer route.

Inputs:

- target color space and format;
- source color-space descriptors;
- gradient interpolation requirements;
- image source profiles;
- runtime-effect route contracts;
- filter DAG color requirements;
- layer color-space request;
- destination-read color interpretation;
- capability and performance constraints.

Default target policy:

- public paint colors enter as finite unpremul RGBA floats tagged sRGB unless
  the API supplies another accepted descriptor;
- material shader logical output is unpremul RGBA float in the active working
  space unless the source declares raw or destination-space behavior;
- color filters consume and produce unpremul RGBA in their declared input and
  output specs;
- blend source and destination are premultiplied in the declared blend
  encoding;
- coverage multiplies premultiplied source color/alpha at the final
  source-to-destination composition boundary;
- final store uses `GPUColorStorePlan`.

Rules:

- Working space selection must be deterministic and dumpable.
- A shader or filter cannot silently switch working spaces.
- `SkWorkingColorSpaceShader`-like compatibility must lower to an explicit
  `GPUWorkingColorSpacePlan` or refuse.
- Layer color-space requests from `saveLayer` are honored only when the layer
  source/filter/composite plans prove the conversion path.
- If multiple sources have incompatible color spaces and no transform is
  accepted, the route refuses.

## Color Conversion And Transform Plans

`GPUColorConversionPlan` records:

- source `GPUColorValueSpec`;
- destination `GPUColorValueSpec`;
- conversion reason;
- source and destination color-space descriptors;
- alpha conversion: none, premul, unpremul, force opaque, preserve raw, or
  refused;
- transfer conversion;
- gamut conversion;
- precision conversion;
- clamp/out-of-range policy;
- dither/quantization policy when accepted;
- matrix/TRC/LUT/helper route;
- CPU implementation descriptor;
- WGSL implementation descriptor;
- tolerance class;
- selected route or stable refusal.

`GPUColorTransformPlan` records executable facts:

- transform plan ID and version;
- CPU oracle function or descriptor;
- WGSL helper/snippet ID;
- uniform or storage payload layout;
- parsed ICC/CICP/profile facts used;
- matrix and curve parameters or LUT descriptors;
- required precision and feature facts;
- reflection and `wgsl4k` validation status;
- fixture set;
- diagnostics.

Rules:

- CPU and WGSL must use the same transform descriptor, not parallel ad hoc
  constants.
- The complete WGSL module using a transform must validate and reflect.
- LUT-backed transforms require explicit texture/buffer ownership and cache
  budgets.
- If ICC parsing or transform generation is lossy, unsupported, or
  nondeterministic, the route refuses or stays non-normative.
- A successful platform conversion is not conformance evidence unless its
  descriptor and tolerance policy are recorded.

## Planar YUV/YUVA Target Model

Planar image sources use `GPUImageColorManagementPlan` plus texture ownership
facts from `18-texture-image-ownership.md`. The target supports two accepted
route families once validated:

- `CPUPreparedGPU` interleaved conversion: CPU decode/convert produces a typed
  `UploadedTextureArtifact` with a recorded `GPUColorConversionPlan`;
- `GPUNative` multi-plane sampling: each plane has a texture descriptor, plane
  format, view, sampler, ownership generation, chroma siting, and WGSL
  conversion helper.

Multi-plane routes record:

- plane count and role: Y, U, V, A, interleaved UV, or format-specific;
- subsampling: 4:4:4, 4:2:2, 4:2:0, or refused;
- chroma siting and sample reconstruction policy;
- full or limited range;
- matrix coefficients and transfer from CICP/container metadata;
- bit depth and normalization;
- alpha composition for YUVA;
- target sample value spec;
- budget and binding limits.

Until those facts, WGSL helpers, CPU oracle fixtures, and facade capabilities
are promoted, planar YUV/YUVA routes refuse. The refusal must distinguish
metadata capture success from missing conversion or sampling support.

## Source Color Families

Material-facing color sources are planned by
`31-material-source-paint-pipeline.md`. This spec remains authoritative for the
color value specs, conversions, interpolation spaces, precision, HDR policy,
and diagnostics consumed by those source plans.

### Paint Colors

Paint colors record:

- input color values;
- API-provided or default color space;
- alpha domain;
- finite proof;
- conversion to working or destination space;
- premul policy;
- diagnostic provenance.

Non-finite values refuse. Out-of-range values require an extended-range policy
or refusal.

### Gradients

`GPUGradientColorPlan` records:

- stop colors and source color spaces;
- interpolation color space;
- premul or unpremul interpolation policy;
- stop normalization and clamp/repeat/mirror interaction;
- precision domain;
- hue interpolation policy when future color spaces require it;
- transform payloads;
- CPU/WGSL fixture evidence.

Rules:

- Gradient interpolation space is not inferred from target format.
- Mixed-space gradient stops require accepted conversion into the interpolation
  space.
- Premul/unpremul interpolation must be explicit.
- Unsupported interpolation spaces refuse with stable diagnostics.

### Images And Bitmaps

`GPUImageColorManagementPlan` bridges this spec to
`GPUImageColorDecodePlan` from `22-image-bitmap-codec-pipeline.md`.

It records:

- encoded profile metadata: ICC, CICP, EXIF-adjacent color facts, or untagged;
- decoded pixel color value spec;
- alpha/premul policy;
- bit depth and numeric encoding;
- YUV/YUVA matrix/range facts when present;
- HDR/gainmap metadata;
- conversion before upload, conversion in shader, preserve-raw, tone-map, or
  refusal;
- upload texture format and target sample spec;
- diagnostic provenance.

Rules:

- Encoded image metadata must be captured even when conversion support refuses.
- Unvalidated profile conversion cannot be hidden in codec output.
- Planar YUV/YUVA conversion follows the target model above and refuses until
  the selected CPU-prepared or multi-plane WGSL route is validated.
- Uploaded texture artifacts must name the color conversion that produced their
  bytes.
- Image sample WGSL must know whether sampled values are raw, encoded, linear,
  premul, or unpremul.

### Vertex And Mesh Colors

Per-vertex colors use `GPUVertexColorPlan` and `GPUPrimitiveColorPlan` from
`26-draw-vertices-mesh-pipeline.md`.

Rules:

- vertex color attribute encoding, alpha domain, and source color space are
  explicit;
- primitive color combines with material output only after both have compatible
  `GPUColorValueSpec` facts;
- source arrays with unknown color encoding refuse or normalize through a
  validated conversion plan;
- primitive blender runtime effects must declare color specs through
  `27-registered-runtime-effects-registry.md`.

### Text And Glyph Colors

Text routes use this color model for:

- paint fill/stroke colors;
- SDF coverage reconstruction output;
- A8/bitmap glyph mask modulation;
- COLR/CPAL palette colors;
- bitmap glyph embedded color profiles;
- SVG glyph colors and gradients when accepted;
- emoji/color glyph composite outputs.

Color font and SVG glyph routes remain dependency-gated until the pure Kotlin
text specs provide stable color artifacts and this spec provides conversion
evidence.

### Runtime Effects

`GPUColorUniformPlan` records color-uniform behavior:

- descriptor ID and version;
- uniform name and role;
- source color-space role;
- `layout(color)`-like transform requirement when exposed through
  compatibility APIs;
- raw-color policy for uniforms that must not transform;
- packing layout;
- CPU oracle interpretation;
- WGSL helper route;
- diagnostics.

Runtime effects must declare input/output `GPUColorValueSpec` facts for
material, filter, blender, primitive blender, and future clip shader routes.
Unknown color-uniform semantics refuse.

## HDR And Gainmap Policy

`GPUHDRColorPlan` records:

- source HDR kind: none, PQ, HLG, extended SDR, gainmap, or custom;
- mastering display metadata when present;
- content light metadata when present;
- luminance units and range: nits, relative scene-linear, display-linear, or
  descriptor-specific;
- scene-referred or display-referred interpretation;
- target HDR capability;
- tone-map, preserve, explicit SDR down-convert, or refuse policy;
- tone-map operator descriptor and parameters when accepted;
- HDR blend/composite domain and premul policy;
- CPU/WGSL route and evidence;
- diagnostics.

`GPUGainmapPlan` records:

- base image descriptor;
- gainmap image descriptor;
- metadata and version;
- color-space and transfer facts for base and gainmap;
- reconstruction math descriptor;
- target tone-map or preserve route;
- required texture bindings or prepared artifacts;
- budget and cache policy;
- selected route or refusal.

Initial target stance:

- HDR and gainmap metadata must be captured when the codec exposes it.
- Product promotion requires CPU oracle, WGSL route, target capability, and
  PM-visible evidence.
- Without that evidence, HDR/gainmap content refuses or uses an explicitly
  accepted SDR down-convert policy only if a future product decision records
  the loss, fixtures, and diagnostics. This is never a CPU-rendered fallback
  for an unsupported draw, layer, filter, image, or scene. The default target
  is refusal, not silent SDR flattening.

## Store And Readback

`GPUColorStorePlan` records:

- source value spec;
- target format and color-space descriptor;
- present/swapchain color-space descriptor when different from the attachment
  descriptor;
- target alpha type;
- surface alpha mode and premul convention;
- transfer/gamut conversion;
- premul policy;
- clamp, quantization, dither, and rounding behavior;
- write mask and channel order;
- surface/present capability facts;
- readback conversion for evidence;
- diagnostics.

Rules:

- Final store conversion is part of target behavior, not material identity.
- Intermediate stores for layers, filters, destination copies, and readbacks
  must name their color spec.
- Readback evidence must state whether bytes are raw target bytes or converted
  comparison pixels.
- Present/swapchain behavior is capability-gated. If the selected `GPU` facade
  cannot expose enough target color, alpha, or format facts, presentation may
  proceed only through an explicit raw-target policy or refuse; it must not
  invent a surface color space from platform output.
- Dithering is disabled unless explicitly accepted and deterministic enough for
  tests.

## Integration Points

### Material Dictionary And WGSL

`GPUMaterialDictionary` consumes color helper metadata:

- conversion helper snippets;
- color-filter helper snippets;
- gradient interpolation snippets;
- runtime-effect color uniform snippets;
- store/composite helpers when shader-owned.

Material keys include only behavior-affecting color facts such as source
family, gradient interpolation mode, color filter identity, runtime-effect
descriptor, and required helper variants. They must not include concrete
profile object identity, decoded pixels, or payload values.

### Blend, Layers, And Filters

`GPUColorPlan` records the per-route bridge to `GPUBlendPlan`,
`GPULayerCompositePlan`, and `GPUFilterNodePlan`.

Rules:

- fixed-function blend eligibility depends on color and alpha specs;
- shader blend routes must declare source and destination value specs;
- layer source, layer filter output, and restore composite specs must be
  compatible or transformed;
- filter DAG nodes declare input/output specs and conversion nodes when needed;
- direct-to-parent layer elision must prove color equivalence.

### Payload And ABI

Color transforms may contribute:

- uniform matrices;
- transfer function parameters;
- LUT buffers or textures when accepted;
- profile IDs and version salts;
- dynamic target facts when they affect shader behavior.

These payloads use `WGSLPackingPlan`, `WGSLBindingLayout`, and
`GPUPayloadGatherer`. Concrete transform values are payload facts unless they
affect WGSL code shape, layout, or pipeline validity.

LUT-backed transforms allocate LUT data through `GPUResourceProvider`.
Buffer-backed LUTs use storage or uniform-buffer bindings from
`11-wgsl-layout-binding-abi.md`. Texture-backed LUTs use texture ownership,
view, and sampler descriptors from `18-texture-image-ownership.md`. The plan
records descriptor, dimensions, interpolation, binding layout, and cache key;
it never keys behavior on a concrete GPU handle.

## Cache, Versioning, And Budgets

`GPUColorCachePlan` may cache:

- parsed ICC/profile descriptors;
- CICP mapping results;
- transfer/gamut transform descriptors;
- CPU transform descriptors;
- WGSL helper module variants;
- LUT resource descriptors and GPU resources;
- gradient converted stop payloads;
- image color conversion plans;
- runtime-effect color uniform plans;
- store plans.

Cache keys include:

- descriptor versions;
- profile hashes;
- source and destination value specs;
- conversion reason;
- transform route;
- target capability facts;
- WGSL helper version;
- LUT descriptor hash;
- tolerance class.

Cache keys must not include:

- raw profile object addresses;
- decoded pixel contents except through artifact keys owned by spec 22;
- concrete GPU handles;
- transient command IDs;
- cache residency state.

`GPUColorBudgetPolicy` records:

- maximum profile byte size;
- maximum parsed profile count;
- maximum LUT byte count;
- maximum transform count per material/module/recording;
- maximum color helper WGSL size;
- maximum F16/HDR intermediate byte budget;
- maximum gainmap texture count and bytes;
- profile parse time class;
- stable refusal behavior.

Budget pressure refuses. The renderer must not silently substitute sRGB,
clamp HDR, ignore profiles, or drop gainmaps to fit budget.

## Diagnostics

Every accepted or refused color route emits `GPUColorDiagnostic`.

Fields:

- color management plan ID;
- source value spec;
- destination value spec;
- color-space descriptor IDs;
- profile descriptor hash and type when present;
- transfer, gamut, white-point, range, alpha, precision, and encoding facts;
- conversion reason and selected route;
- CPU transform descriptor;
- WGSL helper/module ID and validation status;
- material/filter/layer/runtime/image/text/vertex integration point;
- target format and surface capability facts;
- HDR/gainmap metadata when present;
- budget policy ID and budget state;
- tolerance class and fixture ID when validated;
- stable reason code.

Stable reason-code examples:

- `unsupported.color.source_space_unknown`
- `unsupported.color.untagged_policy`
- `unsupported.color.profile_parse`
- `unsupported.color.profile_class`
- `unsupported.color.icc_v4`
- `unsupported.color.cicp`
- `unsupported.color.rendering_intent`
- `unsupported.color.lut_profile`
- `unsupported.color.transfer_function`
- `unsupported.color.gamut_transform`
- `unsupported.color.white_point`
- `unsupported.color.retag_illegal`
- `unsupported.color.alpha_domain`
- `unsupported.color.premul_conversion_unvalidated`
- `unsupported.color.unpremul_zero_alpha`
- `unsupported.color.precision_conversion`
- `unsupported.color.extended_range`
- `unsupported.color.gradient_interpolation_space`
- `unsupported.color.runtime_layout_color`
- `unsupported.color.image_profile_conversion`
- `unsupported.color.yuv_conversion`
- `unsupported.color.yuv_multiplane_route`
- `unsupported.color.hdr_transfer`
- `unsupported.color.gainmap`
- `unsupported.color.tone_map`
- `unsupported.color.f16_target`
- `unsupported.color.present_color_space`
- `unsupported.color.surface_alpha`
- `unsupported.color.target_capability`
- `unsupported.color.wgsl_validation`
- `unsupported.color.cpu_oracle_missing`
- `unsupported.color.budget_exceeded`
- `unsupported.color.platform_conversion_nonnormative`

Existing reason codes such as `unsupported.image.color.conversion_unvalidated`
may remain in older reports, but new GPU renderer color diagnostics should use
the `unsupported.color.*` family where this spec owns the refusal.

## Telemetry

`GPUTelemetryLedger` records color counters:

- color management plan count;
- source/destination value spec histogram;
- color-space descriptor count;
- ICC profile parse count and refusal count;
- CICP metadata count and refusal count;
- ICC LUT profile count and refusal count;
- transfer/gamut transform count;
- CPU transform count;
- WGSL transform/helper count;
- transform cache hit/miss count;
- gradient interpolation color-space count;
- image profile conversion count;
- planar YUV/YUVA metadata and route count;
- runtime color uniform transform count;
- layer color-space restoration count;
- F16 target/intermediate count;
- HDR metadata count;
- gainmap count;
- tone-map count;
- color refusal count by reason;
- LUT resource bytes;
- color transform uniform bytes;
- present/swapchain color route count;
- readback conversion count.

Performance reports must distinguish:

- color metadata capture;
- CPU reference conversion;
- WGSL conversion;
- target capability acceptance;
- realtime readiness;
- non-normative platform conversion lanes.

## Validation Requirements

Promoted color behavior requires:

- canonical dumps for `GPUColorManagementPlan`,
  `GPUColorSpaceDescriptor`, `GPUColorProfileDescriptor`,
  `GPUICCProfileDescriptor`, `GPUCICPDescriptor`,
  `GPUTransferFunctionDescriptor`, `GPUGamutDescriptor`,
  `GPUColorValueSpec`, `GPUColorConversionPlan`,
  `GPUColorTransformPlan`, `GPUWorkingColorSpacePlan`,
  `GPUGradientColorPlan`, `GPUImageColorManagementPlan`,
  `GPUColorUniformPlan`, `GPUHDRColorPlan`, `GPUGainmapPlan`,
  `GPUColorStorePlan`, `GPUColorCachePlan`, `GPUColorBudgetPolicy`, and
  `GPUColorDiagnostic`;
- deterministic descriptor hashing tests;
- ICC class, rendering-intent, white-point adaptation, LUT dimension,
  interpolation, and tolerance tests for promoted profile routes;
- source paint color conversion tests;
- gradient interpolation space tests;
- premul/unpremul tests including zero-alpha behavior;
- image ICC/CICP metadata capture tests;
- planar YUV/YUVA metadata capture tests and conversion/sampling refusal or
  promotion tests;
- image profile conversion or refusal tests;
- vertex color conversion tests when vertex colors are promoted;
- text/color glyph color tests when those routes are promoted;
- runtime-effect color uniform and `layout(color)` tests;
- filter DAG color input/output tests;
- layer color-space restoration and F16 refusal/acceptance tests;
- final store and readback interpretation tests;
- present/swapchain color-space and surface alpha evidence or refusal tests;
- WGSL validation/reflection tests for transform helper modules;
- CPU/GPU parity tests for promoted transforms;
- budget pressure tests for profile/LUT/HDR/gainmap resources;
- stable refusal tests for unsupported profiles, HDR, gainmaps, custom color
  spaces, target capabilities, and nondeterministic platform conversions;
- PM evidence showing metadata, route, transform, CPU/GPU/diff artifacts,
  target format, and stable refusals.

## First Slice Policy

The first rect/rrect plus solid/linear-gradient slice may validate only a small
SDR lane.

It may promote:

- finite sRGB paint colors;
- sRGB solid color to target store;
- sRGB linear-gradient stops with a declared interpolation policy;
- premul source for fixed-function `SrcOver`;
- explicit refusal for unknown color spaces, ICC/CICP transforms, HDR,
  gainmap, F16 target requests, runtime `layout(color)`, and wide-gamut target
  behavior.

It must not claim support for:

- arbitrary ICC profiles;
- CICP transforms beyond metadata capture;
- Display P3 output unless fixtures and target capability evidence exist;
- HDR PQ/HLG;
- gainmaps;
- YUV/YUVA conversion;
- layer color-space restoration;
- runtime-effect color uniforms;
- color glyph profile conversion;
- platform codec color conversion as normative evidence.

Those routes require later evidence against this spec.

## Non-Goals

- Do not port Skia's color-management implementation.
- Do not use `SkColorSpace` object identity as a renderer core contract.
- Do not infer color-space conversion from codec or platform success alone.
- Do not silently assume every untagged source is sRGB without policy.
- Do not reinterpret raw, premul, unpremul, HDR, or encoded values as another
  domain without a conversion plan.
- Do not hide color conversion inside `MaterialKey`.
- Do not sample or store HDR/gainmap content through implicit SDR flattening.
- Do not use CPU-rendered full draws, layers, filters, images, or scenes as
  product fallback for unsupported color behavior.

## HDR Transfer Functions

The `GPUHDRColorPlan` stub is promoted to a complete pipeline plan covering HDR
transfer functions, mastering metadata, and tone-mapping policy.

### Contracts

| Contract | Purpose |
|---|---|
| `GPUHDRTransferFunctionPlan` | Explicit transfer function descriptor: PQ (ST 2084), HLG, scRGB (linear), or custom parametric curve. |
| `GPUHDRMasteringMetadata` | SMPTE 2086 mastering display color volume: primaries, white point, min/max luminance. |
| `GPUHDRContentLightLevel` | MaxCLL and MaxFALL metadata from HDR content. |
| `GPUHDREOTFPlan` | Electro-optical transfer function for GPU-side display mapping: PQ EOTF, HLG OETF^-1, or identity for scRGB. |
| `GPUHDRToneMapPlan` | Tone-mapping strategy when HDR content exceeds target display capabilities: Reinhard, ACES, Hable, or registered custom tone mapper. |
| `GPUHDRColorDiagnostic` | Refusal for unsupported transfer function, missing metadata, HDR content on SDR target, or unvalidated tone-map WGSL. |

### Route Selection

```
HDR Image / Surface
  -> GPUHDRTransferFunctionPlan when codec/surface reports HDR metadata
  -> GPUHDREOTFPlan for GPU-side display mapping
  -> GPUHDRToneMapPlan when target luminance < content luminance
  -> GPUColorStorePlan with HDR-capable target format (rgba16float, rgba32float)
  -> RefuseDiagnostic when HDR target format unavailable
```

### Acceptance Gates

- At least one PQ-encoded image decoded with correct EOTF application and GPU evidence.
- HLG scene-referred image mapped to display with GPU evidence.
- scRGB linear floating-point rendered and stored without quantization artifacts.
- HDR-to-SDR tone mapping produces visually acceptable results (CPU oracle parity).
- Stable refusal for HDR content on SDR-only targets.
- WGSL color transform helpers validated through `wgsl4k`.

## Wide-Gamut Working Spaces

The renderer moves beyond sRGB to accept wide-gamut working spaces for color
computation and intermediate storage.

### Contracts

| Contract | Purpose |
|---|---|
| `GPUWideGamutWorkingSpacePlan` | Accepted working space: Display P3, Adobe RGB, or Rec.2020 primaries with known white point and gamma/transfer. |
| `GPUWideGamutConversionPlan` | Source-to-working-space and working-to-destination conversion using explicit matrix + transfer function pairs. |
| `GPUWideGamutIntermediateFormat` | Intermediate texture format for wide-gamut layer/filter storage: rgba16float or rgba32float. |
| `GPUWideGamutDiagnostic` | Refusal for unsupported gamut, out-of-gamut clipping policy not defined, or incompatible intermediate format. |

### Acceptance Gates

- Display P3 tagged image decoded and rendered with GPU evidence, compared to sRGB-clamped fallback.
- Layer/saveLayer with wide-gamut working space preserves color fidelity (CPU oracle with DeltaE threshold).
- Gradient interpolation in wide-gamut space uses correct transfer-function-aware interpolation.
- Stable refusal for unregistered wide-gamut profile or incompatible destination format.

## Gain Map Pipeline

The `GPUGainmapPlan` stub is promoted to a complete pipeline for Ultra HDR /
Android gain-map images and adaptive display rendering.

### Contracts

| Contract | Purpose |
|---|---|
| `GPUGainmapDecodePlan` | Gain map image decode: base image (SDR), gain map image (1- or 2-channel), and metadata (min/max content boost, gamma, offset, scaling). |
| `GPUGainmapApplyPlan` | Per-pixel gain map application in WGSL: recover HDR from base + gain map, tone map to display range. |
| `GPUGainmapDisplayAdaptationPlan` | Adapt gain-map rendering to current display headroom: use HDR when available, tone-map to SDR otherwise. |
| `GPUGainmapDiagnostic` | Refusal for missing gain map metadata, incompatible gain map format, or unvalidated WGSL apply shader. |

### Acceptance Gates

- Ultra HDR JPEG decoded with gain map metadata preserved.
- GPU-side gain map application produces HDR output (CPU oracle parity on known test images).
- Adaptive display rendering: HDR on HDR target, tone-mapped SDR on SDR target.
- Stable refusal for multi-image gain map variants until proven.

## ICC Profile Parsing Pipeline

ICC profile parsing moves from `RefuseDiagnostic` to an accepted pipeline when
the profile is mathematically well-defined and the transform can be validated.

### Contracts

| Contract | Purpose |
|---|---|
| `GPUICCProfileParsePlan` | ICC profile header, tag table, and supported tag parsing: rXYZ/gXYZ/bXYZ, rTRC/gTRC/bTRC, A2B0/B2A0 (refused initially), and profile ID. |
| `GPUICCProfileTransformPlan` | Matrix + TRC transform extracted from profile tags, equivalent to `GPUColorConversionPlan`, validated against reference profile implementation. |
| `GPUICCProfileCachePlan` | Parsed profile cache keyed by profile bytes hash. Cached transforms are immutable after parsing. |
| `GPUICCProfileDiagnostic` | Refusal for unsupported profile version (ICC v5), unsupported tag types, LUT-based profiles, or profile parse failure. |

### Acceptance Gates

- v2 and v4 ICC profiles with matrix/TRC tags parsed and transformed correctly.
- CPU oracle for at least one ICC profile: GPU transform output matches reference ICC implementation within DeltaE tolerance.
- Profile cache hit/miss telemetry.
- Stable refusal for ICC v5, LUT profiles, named color profiles, and unparseable profiles.
