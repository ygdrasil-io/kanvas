# M65 Kadre / poc-koreos Audit

Status: FOR-33 concrete audit, no live-host support claim.

## Inputs

- Target/specs: `.upstream/target/skia-like-realtime-renderer-target.md`,
  `.upstream/specs/skia-like-realtime/02-realtime-runtime-architecture.md`,
  `.upstream/specs/skia-like-realtime/05-pm-demo-and-release-candidate.md`.
- Audited upstream: `https://github.com/ygdrasil-io/poc-koreos.git`
  at `9dae217144fde70bdafddcf60855e2329921b1e5`.
- Local audit command: `rtk git ls-remote https://github.com/ygdrasil-io/poc-koreos.git HEAD 'refs/heads/*'`
  plus a temporary clone under `/tmp/poc-koreos-m65-audit`.

## Findings

Kadre is viable as the selected native/live host, but it is not yet a small
drop-in Kanvas dependency in this branch.

Useful surface:

- `kadre-core` exposes `EventLoop`, `ActiveEventLoop`, `Window`,
  `ApplicationHandler`, `WindowEvent`, `DeviceEvent`, physical sizes, scale
  factor, raw window/display handles, and redraw requests.
- `kadre-appkit` creates macOS `NSWindow` / layer-backed content views with
  `CAMetalLayer`, dispatches keyboard/mouse/window events, and coalesces
  `WindowEvent.RedrawRequested` through a CFRunLoop observer.
- Samples include `samples/hello-metal` and wgpu4k/Metal paths that demonstrate
  the intended host shape.
- The root README now describes a published `org.graphiks.kadre:kadre:1.0.0`
  artifact, but Kanvas still needs an integration decision because the active
  target allowed submodule consumption while Kadre was incubating/unpublished.

Integration blockers for this M65 slice:

- Kanvas has no checked-in Kadre submodule, dependency wiring, or host bridge.
- The Kadre sample path is JVM/macOS/JDK 25 oriented and needs
  `-XstartOnFirstThread` plus `--enable-native-access=ALL-UNNAMED`; adding it
  directly to the existing Kanvas verification lane would make the smoke lane
  host/platform-specific.
- The current M65 acceptance asks for a measurable first smoke lane, not a
  packaged live demo. Forcing a live AppKit shell here would create a
  demo-only host path before Kanvas has a narrow runtime host contract.

## Decision

Do not add a second window shell and do not hide the missing Kadre bridge.
M65 records Kadre as viable but blocked for live presentation with stable
reason `m65.kadre-host-not-wired`, then lands a headless/offscreen runtime
smoke command that produces telemetry JSON and nonblank PNG artifacts.

M67/M68 can promote the host path after one of these happens:

- Kanvas consumes `org.graphiks.kadre:kadre` with pinned version and host
  bridge tests; or
- Kanvas adds `ygdrasil-io/poc-koreos` as a submodule and wires a narrow
  Kadre host adapter around surface creation, resize, present, input, frame
  clock, and diagnostics.

## Non-Claims

- This audit does not claim a Kadre-hosted Kanvas frame loop.
- This audit does not claim WebGPU presentation timing.
- This audit does not change M66 packs or README readiness accounting.
