# Skia-Like Breadth And Real-Time Renderer Specs

Status: Draft
Target: `.upstream/target/skia-like-realtime-renderer-target.md`
Previous evidence target: removed from the working tree; recover from Git
history only if needed.

This spec pack defines the next Kanvas ambition after the MEP evidence target:
add missing rendering features, move closer to Skia CPU output, and build a
measured real-time WebGPU lane. Most early milestones are breadth/fidelity
work; real-time is exploratory until M67/M68 provide measured frame gates and
a packaged Kadre demo.

## Scope

The pack covers M60-M88 and continues to be extended by later runtime/display-list
sprints:

| Spec | Scope |
|---|---|
| `00-current-state-and-gap-analysis.md` | Starting point after M59, retained strengths, missing features, and new readiness model. |
| `01-rendering-feature-expansion.md` | Path AA, image filters, text/glyphs, color/blend/color filters, gradients, and runtime effects. |
| `02-realtime-runtime-architecture.md` | Frame loop, display-list replay boundary, invalidation diagnostics, resource lifetime, cache policy, and telemetry. |
| `03-skia-fidelity-and-gm-promotion.md` | GM selection, reference policy, diff burn-down, support/refusal semantics, and promotion waves. |
| `04-performance-tiering-and-release-gates.md` | Family budgets, frame budgets, measured lanes, cache pressure, quarantine, and rebaseline rules. |
| `05-pm-demo-and-release-candidate.md` | PM demos, native/live demo packaging, release-candidate criteria, and non-claims. |

## Hard Constraints

- Do not port Ganesh or Graphite.
- Do not rebuild Skia's SkSL compiler, IR, or VM.
- Keep WebGPU as the GPU backend.
- Keep CPU as the Skia-like reference path.
- Keep `SkRuntimeEffect` as a compatibility facade backed by registered
  Kotlin/WGSL implementations.
- Treat WGSL as the shader implementation target. SkSL wording may appear only
  for Skia API compatibility context; dynamic SkSL compilation is not in scope.
- Use Kadre from `ygdrasil-io/poc-koreos` for live/native windowing. It is
  incubating and unpublished, so M65/M68 work may include it as a git
  submodule rather than waiting for publication.
- Treat `ygdrasil-io/wgsl4k` as an active dependency, not a finished black box.
  Ambiguous or surprising WGSL parser/IR/generator behavior must stop the
  Kanvas assumption and become a `wgsl4k` ticket.
- Do not mark support as complete from route diagnostics alone.
- Do not hide unsupported rows from inventory or dashboard evidence.
- Do not count estimated or missing performance metrics as measured evidence.

## Milestone Map

| Milestone | Primary specs | PM-visible result |
|---|---|---|
| M60 Coverage & Path AA Expansion | `01`, `03`, `04` | Complex shapes/strokes/clips render or refuse with visible reasons. |
| M61 Image Filter DAG V2 | `01`, `02`, `03` | Bounded filter graphs render with graph diagnostics and intermediate ownership. |
| M62 Text & Glyph Rendering V1 | `01`, `02`, `03` | Simple text renders through glyph masks and WebGPU glyph atlas. |
| M63 Color, Blend & ColorFilter Parity | `01`, `03`, `04` | Common paint/color/blend families compare against Skia references. |
| M64 Registered Runtime Effects | `01`, `02`, `03` | Known effects render with reflected uniforms and CPU/GPU parity evidence. |
| M65 Real-Time Scene Runtime | `02`, `04`, `05` | Interactive scene loop with FPS/frame telemetry and cache diagnostics. |
| M66 Skia GM Promotion Wave | `03`, `05` | 50-100 prioritized GM rows become support/refusal evidence. |
| M67 Performance Tiering | `04` | Family-level correctness/performance gates become release-owned. |
| M68 Native Real-Time Demo | `02`, `05` | Runnable live demo with animation, controls, and PM report export. |
| M69 Fidelity Hardening | `03`, `04` | Visual diff burn-down across promoted families. |
| M70 Release Candidate Renderer | all | Renderer RC with API, demos, CI gates, and known limitations. |
| M71-M83 Runtime/display-list replay evidence | `02`, `04`, `05` | Kadre frame clock, bounded replay scenes, native artifacts, input/resize telemetry, and one bounded display-list route become PM evidence without broad SkCanvas replay claims. |
| M84 Native Frame Timing Candidate Gate | `04`, `05` | `frame.kadre-windowed` timing is serialized as candidate/reporting-only evidence with quarantine and a negative fixture, not a release-blocking FPS gate. |
| M85 Runtime Resource Lifetime And Cache Hardening | `02`, `04`, `05` | Selected realtime route resource/cache ledger, bounded key spaces, resize invalidation, and device-loss unsupported diagnostics become PM evidence without claiming observed runtime cache telemetry. |
| M86 Fidelity Burn-Down Wave 2 | `03`, `05` | Selected GM/reference rows are ranked and classified by root cause with PM-visible remediation targets, without claiming visual fixes or readiness movement unless before/after artifacts exist. |
| M87 Registered Runtime Effect Live Editing V2 | `01`, `02`, `05` | Selected SimpleRT live parameter editing, reflected WGSL layout, edited-state CPU/GPU/diff artifacts, and stable arbitrary Skia/SkSL runtime-input or missing-descriptor refusals become PM evidence without claiming broad runtime-effect compatibility or dynamic SkSL compilation. |
| M88 Realtime Renderer Release Candidate 2 | `04`, `05`, `06` | API/demo surface, gate set, support/refusal matrix, PM script, and release notes are frozen into a reproducible RC2 package without claiming new broad support or readiness movement. |

## Definition Of Done For Any Future Milestone

- The milestone updates this spec pack if behavior or scope changes.
- New support claims have reference, CPU, GPU, diff/stat, route, and fallback
  artifacts.
- New refusals use stable reason codes and remain visible to PM.
- Performance-sensitive work has measured payloads or an explicit non-gating
  rationale.
- PM demo output is reproducible from commands or checked-in artifacts.
- Planned implementation work cites the target and exact spec sections it
  implements.

## Open Decisions

Use these as planning prompts, not blockers:

- first native demo shell: Kadre desktop windowing from
  `ygdrasil-io/poc-koreos`, consumed as a git submodule while unpublished;
- frame target for curated PM scenes: 60 FPS strict or 60 FPS target with
  30 FPS warning;
- first text language/script scope;
- flagship PM scene theme for M68/M70.
