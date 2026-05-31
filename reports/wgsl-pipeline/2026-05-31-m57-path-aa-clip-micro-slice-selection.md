# M57 Path AA / Clip Micro-Slice Selection

Linear: `GRA-340`

Result: select one bounded support slice and keep broad Path AA / clip rows
unsupported.

## Selected Slice

| Row | Inventory | Source artifacts | Decision |
|---|---|---|---|
| `m57-aaclip-bounded-grid` | `skia-gm-aaclip` | `AaclipSceneCaptureTest` row-specific artifacts | Add as `pass` |

The selected row is a bounded AA clip grid from `AaclipGM` with intersect AA
rect clips, no inverse clip, no complex clip, and no dash/stroke breadth claim.
It uses row-specific CPU/GPU/reference/diff/stats artifacts emitted by
`org.skia.gpu.webgpu.AaclipSceneCaptureTest` with `fallbackReason=none`,
`edgeBudgetReason=not coverage.edge-count-exceeded`, and an explicit
`webgpu.coverage.aaclip-bounded-grid` route. The M57 route diagnostics carry the
row-specific AA clip contract: edge budget, clip op, inverse/complex flags, and
dash/stroke non-applicability.

## Why This Is Defensible

- The row claims only one bounded `aaclip` grid slice, not all `AaclipGM`.
- The GPU route is below the current WebGPU edge budget.
- The source artifacts include CPU image, GPU image, reference, CPU diff, GPU
  diff, route diagnostics, and stats.
- The row keeps `fallbackReason=none` only for the bounded subset.

## Rejected Alternatives

| Candidate | Decision | Reason |
|---|---|---|
| `m54-dash-circle-boundary` | Keep `expected-unsupported` | Dash/cap/join breadth still trips edge-budget policy. |
| `m53-complexclip-boundary-refusal` | Keep `expected-unsupported` | Complex clip needs its own clip-stack implementation proof. |
| `m52-closed-capped-hairlines-edge-budget` | Keep `expected-unsupported` | Hairline/cap breadth has no row-specific adapter-backed pass artifacts. |
| `path-aa-stroke-outline-fallback` | Keep static sentinel | Stroke-outline fallback remains a deliberate policy boundary. |

## Non-Claims

- No edge-budget increase.
- No broad Path AA parity.
- No dash, cap, join, or stroke-outline support.
- No complex clip support.
- No conversion of existing expected-unsupported rows without row-specific
  artifacts.
