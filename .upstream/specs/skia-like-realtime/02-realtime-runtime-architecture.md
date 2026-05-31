# 02 Real-Time Runtime Architecture

Status: Draft
Target: `.upstream/target/skia-like-realtime-renderer-target.md`

## Purpose

This spec defines the runtime architecture needed to move from static evidence
generation to interactive rendering. The runtime must reuse the same semantic
pipeline as conformance tests, not create a separate demo-only renderer.

Live windowing is expected to use Kadre from `ygdrasil-io/poc-koreos`. Kadre is
incubating and not published, but it is mature enough to host presentation for
wgpu4 and Skiko experiments. Until it is published, Kanvas may consume it as a
git submodule for M65/M68 runtime work.

Kanvas rendering remains the proof path. Skiko may be used only as a
presentation/comparison host when explicitly labeled; PM support evidence must
distinguish Kanvas-rendered pixels from Skia/Skiko-rendered pixels.

## Runtime Model

```text
Application state
  -> Skia-like display list / optional runtime node view
  -> Dirty/invalidation analysis
  -> Render frame plan
  -> Pipeline/resource cache lookup
  -> CPU or WebGPU execution
  -> presentation
  -> telemetry snapshot
```

## M65 Real-Time Scene Runtime

M65 should start from immediate-style command recording and display-list replay,
matching Skia's `SkCanvas`/`SkPicture` mental model. A retained node view may
exist internally for invalidation diagnostics, but it is not a public API
decision and must not become the only representation.

### Required Components

- frame clock;
- Kadre window host;
- display-list container and optional diagnostic node view;
- input/update phase;
- render planning phase;
- resource upload queue;
- WebGPU command submission;
- Kadre presentation bridge for wgpu4 and, where useful, Skiko comparison;
- CPU fallback execution path;
- telemetry overlay;
- PM demo export hook.

## Kadre Host Contract

Kanvas needs only a narrow host contract from Kadre:

| Capability | Required behavior |
|---|---|
| Surface creation | Create a WebGPU/wgpu4-compatible drawable surface with adapter/device metadata. |
| Resize | Report physical size, scale factor, and resize events before the next frame plan. |
| Present | Present a completed Kanvas frame and expose present timing proxy where available. |
| Input | Forward pointer, keyboard, and close events to the runtime update phase. |
| Frame clock | Provide vsync-like callback or monotonic timer suitable for 120-frame smoke tests. |
| Diagnostics | Expose host, backend, adapter, surface format, and failure reason in telemetry. |

This contract keeps Kadre as the selected implementation while preserving the
ability to change hosts later if the same minimum contract is implemented.

### Display List / Runtime View Minimum

Recorded commands or derived runtime nodes should cover:

- transform;
- clip;
- shape/path;
- image/bitmap;
- text/glyph run;
- image filter layer;
- runtime effect;
- group/layer.

Each recorded element or derived node must expose:

- stable id;
- bounds;
- local transform;
- invalidation version when retained for diagnostics;
- feature tags;
- render route diagnostics.

### Invalidation

Initial implementation may redraw the whole frame, but the runtime must record
why:

- full redraw;
- transform-only update;
- paint update;
- geometry update;
- filter graph update;
- glyph atlas update;
- texture upload update.

Dirty-region optimization can land later, but diagnostics must be present from
the first live runtime milestone.

## WebGPU Resource Lifetime

Track:

- shader modules;
- render pipelines;
- bind groups;
- uniform buffers;
- textures;
- intermediate filter textures;
- glyph atlas textures;
- samplers.

Resource telemetry per frame:

- cache hits/misses;
- new allocations;
- bytes uploaded;
- intermediate texture count;
- bind group count;
- command encoder/pass count.

## Frame Telemetry

Every PM live demo frame should be able to emit:

- frame number;
- CPU update time;
- CPU render planning time;
- GPU submit time proxy where available;
- frame time;
- FPS rolling average;
- pipeline cache hits/misses;
- texture/cache bytes;
- fallback/refusal count;
- route summary.

## Runtime Gates

M65 does not claim release-grade real-time performance, but it must define
telemetry schema and reporting-only frame metrics:

- scene loads;
- first frame nonblank;
- 120 frames render without exception;
- frame telemetry JSON exists;
- fallback reasons are stable;
- screenshots or canvas pixels prove rendering;
- median and p95 frame time are reported as non-blocking metrics.

Release-blocking frame/FPS gates belong to M67 after the execution model below
has produced stable baselines.

## CI Frame Execution Model

Frame gates use two lanes:

| Lane | Purpose | Requirement |
|---|---|---|
| Headless/offscreen WebGPU | CI-friendly frame planning, command submission, cache telemetry, and pixel proof. | Required for M65 smoke and M67 candidate gates. |
| Kadre windowed adapter | PM-visible presentation timing and host integration. | Required for M68 demo packaging; optional/non-blocking before then. |

Initial curated scenes:

- `p0-live-basic-transform`: transform + bitmap/gradient + path;
- `p0-live-filter-small`: one bounded filter DAG;
- `p0-live-text-atlas`: bundled Liberation text when M62 is ready;
- `p1-live-runtime-effect`: registered effect when M64 is ready.

Initial environment:

- Apple M-series adapter as the first measured lane;
- JDK 25;
- 120 warm frames plus 300 measured frames for candidate gates;
- 60 FPS target, 30 FPS warning until M67 promotes a lane.

## PM Demo Requirements

The first real-time demo should include:

- animated transform;
- one image or bitmap shader;
- one path/coverage element;
- one text or placeholder text lane if M62 is not complete;
- one image-filter or runtime-effect lane if available;
- live telemetry overlay;
- pause/reset controls;
- export current frame to dashboard-style artifact.

## Non-Goals

- game engine ECS;
- editor UX;
- full retained-mode API stability;
- public platform abstraction beyond the minimal host contract;
- native packaging before M68.

## Open Decisions

- first host: Kadre desktop windowing is the default; browser-hosted WebGPU is
  optional evidence only if a milestone explicitly asks for it;
- primary refresh target: strict 60 FPS or 60 FPS target with 30 FPS warning;
- telemetry format compatibility with existing PM bundle manifests.
