# M7 - Runtime Effects, Color, And Blend

## Goal

Add registered runtime-effect, color-management, color-filter, and blend routes
without accepting arbitrary SkSL, arbitrary WGSL strings, or unsupported
destination reads.

## Dependencies

Depends on M2 material/WGSL foundations and M5 destination-read strategy for
blend modes that require destination access. Runtime effects must follow
`27-registered-runtime-effects-registry.md`.

## Exit Criteria

- [ ] Runtime effects route only through registered descriptors with Kotlin/CPU
      oracle and parser-validated WGSL evidence.
- [ ] Blend/color plans distinguish fixed-function, shader, destination-read,
      and refusal paths.
- [ ] SDR color behavior is explicit and broad color/HDR/profile routes are
      refused or dependency-gated.
- [ ] No SkSL compiler, SkSL IR/VM, or arbitrary source support is implied.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M7-001 - Add registered runtime-effect descriptor route](KGPU-M7-001-add-registered-runtime-effect-descriptor-route.md) | `review` | `P0` | `DependencyGated` | `GPUNative` | `false` | `true` | `runtime-effects` | `KGPU-M2-002` | `runtime-effect legacy` |
| [KGPU-M7-002 - Add runtime-effect child and source refusal gates](KGPU-M7-002-add-runtime-effect-child-and-source-refusal-gates.md) | `blocked` | `P0` | `RefuseRequired` | `RefuseDiagnostic` | `false` | `false` | `runtime-effects-validation` | `KGPU-M7-001` | - |
| [KGPU-M7-003 - Add blend mode allowlist and destination-read refusals](KGPU-M7-003-add-blend-mode-allowlist-and-destination-read-refusals.md) | `blocked` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `blend-destination-read` | `KGPU-M5-002` | `blend legacy` |
| [KGPU-M7-004 - Add SDR color plan and HDR profile refusal gates](KGPU-M7-004-add-sdr-color-plan-and-hdr-profile-refusal-gates.md) | `done` | `P1` | `DependencyGated` | `GPUNative` | `false` | `false` | `color` | `KGPU-M2-002` | `color legacy` |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*Runtime*' --tests '*Blend*' --tests '*Color*'
```

## Non-Claims

- No arbitrary SkSL or dynamic shader compilation.
- No broad color-management, HDR, ICC/CICP, gainmap, or all-blend-mode support.
- Runtime descriptor acceptance is not product support without route evidence.

## Current Evidence

- KGPU-M7-001 is in `review` with contract-gate evidence for the registered
  `runtime.simple.color` material descriptor, descriptor ID/version, uniform
  schema/packing, canonical 64-hex `sha256:` CPU oracle hash,
  parser-validated wgsl4k reflection linkage, route/material-key dumps, and
  stable refusals for unregistered descriptors, descriptor collisions, dynamic
  SkSL source, wrong placement, missing explicit placement opt-in, WGSL
  reflection/schema/descriptor mismatch, and missing or non-canonical CPU oracle
  evidence. It remains non-promoted: no adapter-backed runtime-effect
  execution, readback, product activation, arbitrary SkSL/WGSL input, children,
  blenders, filters, or live editing is claimed.
  Evidence report:
  `reports/gpu-renderer/2026-06-17-m7-001-runtime-effect-descriptor-gate.md`.
- KGPU-M7-002 is `blocked` on independent acceptance of KGPU-M7-001 so
  child/source refusal rows can be anchored to an accepted descriptor route
  boundary.
- KGPU-M7-003 is `blocked` on KGPU-M5-002 and native destination-read strategy
  evidence; no framebuffer-fetch, active-attachment sampling, or CPU-rendered
  blend fallback is implied.
- KGPU-M7-004 is `done` with bounded SDR color boundary evidence:
  deterministic finite-sRGB value/store dumps, behavior key facts that exclude
  source/profile identity, and terminal refusals for HDR, gainmap, ICC/CICP,
  untagged, and extended-range cases. Independent review
  `019ec850-9390-7240-9313-1f9af4b9a77d` accepted the evidence with no
  findings. This is contract/refusal evidence only.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
