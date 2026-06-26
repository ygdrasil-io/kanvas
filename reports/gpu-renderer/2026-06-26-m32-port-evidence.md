# M32 per-family PORT evidence consolidation (KGPU-M32-010..021)

- Date: 2026-06-26
- Phase: Legacy gpu-raster Decommission — Phase 2.C (port-evidence consolidation)
- Tickets (port sub-cases): KGPU-M32-010, -011, -012, -014, -015, -018, -019, -021
- Status of tickets: kept `proposed` (independent review still owed)
- Scope: **documentation-only**. This report consolidates ALREADY-COMMITTED port
  parity evidence into one per-family view with HONEST scoping. No code changes;
  no new feature. Refusal sub-cases are covered separately
  (`reports/gpu-renderer/2026-06-26-m32-refusal-coverage.md`,
  `reports/gpu-renderer/2026-06-26-m32-013-stroke-refusal.md`).

## Source evidence (cited verbatim below)

- **m32-002** — `reports/gpu-renderer/2026-06-26-m32-002-bridge-vs-legacy-parity.md`
  — real bridge↔legacy `SkWebGpuDevice` GPU pixel parity.
- **M31-005** — `reports/gpu-renderer/2026-06-26-M31-005-fillrrect-evidence.md`
  — GPU↔independent-CPU parity (+ §9 bridge↔Skia software raster).
- **TextGpuEvidenceMain** —
  `kanvas/src/test/kotlin/org/graphiks/kanvas/TextGpuEvidenceMain.kt`
  — real GPU A8 `DrawTextRun` pixels with CPU-oracle parity (opt-in JavaExec).
- **refusal-coverage** — `reports/gpu-renderer/2026-06-26-m32-refusal-coverage.md`.
- **stroke-refusal** — `reports/gpu-renderer/2026-06-26-m32-013-stroke-refusal.md`.

### Fresh re-confirmation of the bridge↔legacy GPU compare (this Phase 2.C run)

Re-ran the same GPU task on Apple M2 Max (Dawn/Metal, `libWGPU-v27.0.4.0.dylib`)
to re-confirm the m32-002 numbers. Real output (verbatim; locale prints comma
decimal separators, values identical to the committed report):

```
rtk ./gradlew --no-daemon :kanvas-skia-bridge:compareBridgeVsLegacyGpuRaster

=== Summary ===
PASS | Rect solid fill | similarity=100,00% matching=40000/40000 maxDiff=0
PASS | RRect solid fill | similarity=99,77% matching=39908/40000 maxDiff=123
PASS | Path solid triangle fill | similarity=100,00% matching=40000/40000 maxDiff=0
All comparisons passed (threshold >= 99.0%)

BUILD SUCCESSFUL in 11s
```

This is a re-confirmation only; the canonical committed numbers remain those in
m32-002.

## Per-family PORT evidence

### KGPU-M32-011 — solid-rect-drawpaint (port) — FULLY PROVEN

- **Bridge↔legacy GPU pixel parity** (m32-002): Rect solid fill
  **100.00%, matching 40000/40000, maxDiff 0** (byte-identical non-AA rect).
- **GPU↔independent CPU + bridge↔Skia raster** (M31-005 §Family Coverage /
  §9): FillRect **100%** vs independent geometric reference and **100%** vs Skia
  software raster (tol=0).
- **Scope:** SolidColor material, Identity transform, Root layer,
  WideOpen/DeviceRect clip, SRC_OVER. Stroke refused (stroke-refusal). This is
  the strongest-evidenced family — proven against BOTH an independent CPU oracle
  and the legacy `SkWebGpuDevice` at the pixel level.

### KGPU-M32-010 — material-paint (port sub-case = SolidColor) — PROVEN for SolidColor only

- The **SolidColor** material sub-case is exactly what every rect/rrect/path
  parity scene above exercises (all use a SolidColor paint). So SolidColor
  material is covered transitively by m32-002 (Rect 100%, RRect 99.77%, Path
  100%) and M31-005 (FillRect 100%, FillRRect 99.84%, FillPath 100%).
- **Refused remainder (not proven here):** gradients
  (`unsupported_material:{Linear,Radial,Sweep}Gradient`), image shaders
  (`unsupported_material:ImageDraw`), and runtime effects
  (`unsupported_material:RuntimeEffect`) — see refusal-coverage
  §KGPU-M32-010. Real gradient/shader GPU port remains dependency-gated.

### KGPU-M32-012 — rounded-rect-gradients (port sub-case = solid uniform rrect) — PROVEN for solid uniform rrect only

- **Bridge↔legacy GPU pixel parity** (m32-002): RRect solid fill
  **99.77%, matching 39908/40000, maxDiff 123**. The 0.23% (92 px) mismatch is
  concentrated on anti-aliased edge pixels (SDF coverage vs legacy analytical
  AA); position/dimensions/radii are exact. Above the ≥99.0% threshold.
- **GPU↔independent CPU** (M31-005 §3): FillRRect **99.84%, matching
  76680/76800, maxDiff up to 124 at G/B/A on 50%-coverage AA edge pixels** vs an
  independent binary point-in-rounded-rect reference (the ~0.16% delta is the
  expected SDF-vs-binary AA boundary). Bridge↔Skia raster (M31-005 §9) likewise
  99.77%.
- **Scope:** solid, **uniform corner radii** only. Gradients refused
  (`unsupported_material:<gradientKind>`) and **non-uniform radii refused**
  (`non_uniform_radii`) — refusal-coverage §KGPU-M32-012.

### KGPU-M32-015 — path-fill-stroke (port sub-case = fill) — PROVEN for fill only

- **Bridge↔legacy GPU pixel parity** (m32-002): Path solid triangle fill
  **100.00%, matching 40000/40000, maxDiff 0** (non-AA stencil-cover vs legacy
  fan triangulation; interior/exterior pixels match exactly).
- **GPU↔independent CPU** (M31-005 §4–§5): FillPath triangle **100%
  (76800/76800, maxDiff 0)** and star **100% (76800/76800, maxDiff 0)** vs an
  independent winding reference.
- **Scope:** fill only, SolidColor, Identity transform, Root layer. **Path
  stroke refused** (`unsupported_stroke`) — stroke-refusal; real path-stroke
  port stays KGPU-M11-007.

### KGPU-M32-018 — text-glyphs (port sub-case = A8 fill) — PROVEN as GPU↔CPU parity (NOT bridge↔legacy GPU pixel parity)

- **Evidence:** `TextGpuEvidenceMain.kt` renders real Latin text ("ABC", 48px,
  256×96) through `Surface.renderToRgba()` on WebGPU and checks GPU↔CPU coverage
  parity against `TextRunCpuOracle`, printing
  `PASS real GPU A8 text pixels with CPU parity` (asserts `dispatchedCount == 1`,
  `gpuNonTransparent > 0`, `cpuNonTransparent > 0`, count ratio ≥ 0.5, overlap
  parity ≥ 0.5). Skips cleanly when WebGPU is unavailable.
- **HONEST scoping:** this is **GPU↔CPU-oracle parity**, NOT a bridge↔legacy
  `SkWebGpuDevice` pixel-parity comparison like rect/rrect/path. The harness also
  prints the explicit non-claim
  `no-aa no-subpixel no-sdf no-color no-shaping no-bearing no-multipage`.
- **Refused remainder:** color/SDF/emoji text and non-solid text material /
  non-simple text clip — refusal-coverage §KGPU-M32-018. Real port remains
  dependency-gated (KGPU-M6-002).

### KGPU-M32-019 — runtime-effects-color-blends (port sub-case = SRC_OVER) — PROVEN for SRC_OVER only (implicit)

- **Evidence:** SRC_OVER is the blend mode used by **every** parity scene above
  (rect/rrect/path in m32-002 and M31-005, and the A8 text scene). M31-005
  §8 + §Family Coverage explicitly mark `Blend (SRC_OVER)` as proven (implicit in
  all coverage scenes), with a `unsupported_blend` dispatch guard for non-SRC_OVER.
- **HONEST scoping:** only the SRC_OVER blend is proven, transitively. No
  runtime effect, color filter, or non-SRC_OVER blend is rendered.
- **Refused remainder:** non-SRC_OVER blends (`unsupported_blend:<mode>`) and
  runtime effects (`unsupported_material:RuntimeEffect`); color filters
  (`Paint.colorFilter`) are NOT wired into material lowering, so they are neither
  rendered nor refused as a distinct token — refusal-coverage §KGPU-M32-019.
  Real port remains dependency-gated (KGPU-M11-008).

### KGPU-M32-014 — device-scissor-simple-clips (port sub-case = simple clip) — PROVEN only as WideOpen (full-surface); DeviceRect NOT bridge-reachable

- **HONEST scoping (critical):** the "simple clip" port is, in practice,
  **WideOpen (full-surface)**. The public Kanvas/bridge API currently emits only
  WideOpen clips: `Canvas.drawRect/drawRRect/drawPath` default to
  `GPUClipFacts.wideOpen` via the command builder (proven end-to-end by the
  refusal-coverage test `drawRect via public API produces a WideOpen clip`), and
  `Canvas.drawTextBlob` sets `clip = GPUClipFacts.wideOpen(...)` explicitly
  (`kanvas/.../Canvas.kt:221`). The bridge (`KanvasSkiaBridge`) exposes **no**
  clip entrypoint.
- The **DeviceRect scissor path IS dispatch-capable** — the fill/text dispatch
  guards accept `WideOpen` OR `DeviceRect`
  (`kanvas/.../Surface.kt:70`,
  `kanvas/.../TextRunDispatch.kt:100`) and the dispatch carries
  scissor coordinates — **but it is NOT bridge-reachable end-to-end today** (no
  public API constructs a DeviceRect clip).
- **What is proven:** the WideOpen (full-surface) clip is implicitly covered by
  every parity scene above (all run under WideOpen).
  **NOT claimed:** DeviceRect scissor end-to-end pixel parity. Complex clips
  refuse `unsupported_clip:<ClipKind>` at the dispatch guard (refusal-coverage
  §KGPU-M32-014). Real device-scissor port remains dependency-gated.

### KGPU-M32-021 — clear-discard-target-background (port, trivial) — IMPLICIT / TRIVIAL

- **Evidence:** surface background initialization / clear is part of every
  rendered scene — e.g. the m32-002 scenes render against an initialized surface
  (the legacy device uses transparent-black background `setBackground(0)`; the
  bridge renders onto a raster-backed SkSurface), so the clear/background-init
  step is exercised by every parity comparison above.
- **HONEST scoping:** this is a **trivial / implicit** port; no dedicated
  empty-surface-readback dump has been produced as standalone evidence.
- **Open item (matrix concern #4):** the family's `defaultReplacementTicket` is
  the placeholder `route-specific-clear-discard-ticket-required`, which is NOT a
  real ticket id. A real replacement id must be assigned before Phase 3
  retirement authorization (KGPU-M32-003).

## Summary table

| Ticket | Port sub-case | Strongest cited evidence | Scope honesty |
|---|---|---|---|
| M32-011 solid-rect-drawpaint | FillRect | m32-002 Rect 100% (40000/40000, maxDiff 0); M31-005 FillRect 100% indep + 100% Skia raster | Fully proven (bridge↔legacy + indep CPU) |
| M32-010 material-paint | SolidColor | covered by all solid rect/rrect/path parity | Only SolidColor; gradients/shaders/RE refused |
| M32-012 rounded-rect-gradients | solid uniform rrect | m32-002 RRect 99.77% (39908/40000, maxDiff 123); M31-005 FillRRect 99.84% (76680/76800) | Only solid uniform rrect; gradients + non-uniform radii refused |
| M32-015 path-fill-stroke | path fill | m32-002 Path 100% (40000/40000, maxDiff 0); M31-005 triangle+star 100% (76800/76800) | Only fill; stroke refused |
| M32-018 text-glyphs | A8 fill | TextGpuEvidenceMain GPU+CPU parity PASS | GPU↔CPU parity (NOT bridge↔legacy GPU pixel parity); color/SDF/emoji refused |
| M32-019 runtime-effects-color-blends | SRC_OVER | implicit SRC_OVER in all parity scenes (M31-005 §8) | Only SRC_OVER; other blends/effects/filters refused |
| M32-014 device-scissor-simple-clips | simple clip | WideOpen implicit in all parity scenes | WideOpen-only end-to-end; DeviceRect dispatch-capable but NOT bridge-reachable |
| M32-021 clear-discard-target-background | surface init | implicit in every rendered scene (e.g. m32-002 backgrounds) | Trivial/implicit; placeholder replacement-ticket id still owed |

## Honesty caveats (do not over-read this report)

- This is a **consolidation** of existing committed evidence, not new proof. Each
  number above is quoted verbatim from m32-002 / M31-005 /
  TextGpuEvidenceMain.kt; the Phase 2.C re-run only re-confirms m32-002.
- **A8 text** (M32-018) is CPU-oracle parity, **not** bridge↔legacy GPU pixel
  parity — strictly weaker than the rect/rrect/path evidence.
- **DeviceRect scissor** (M32-014) is dispatch-capable but **not bridge-reachable
  end-to-end**; only WideOpen (full-surface) is proven via the bridge. No
  DeviceRect end-to-end parity is claimed.
- **Clear/background** (M32-021) is trivial/implicit; the placeholder
  replacement-ticket id must be assigned before Phase 3.
- All eight tickets remain `status: proposed` — **independent review is still
  owed** before any retirement-gate promotion.
