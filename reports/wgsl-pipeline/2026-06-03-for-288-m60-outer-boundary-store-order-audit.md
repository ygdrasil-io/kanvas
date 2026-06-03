# FOR-288 M60 Outer Boundary Store Order Audit

Linear: `FOR-288`

Scene: `m60-bounded-nested-rrect-clip`

Decision: `NEXT_FIX_INSTRUMENT_CPU_ACTIVE_AA_DIFFERENCE_STORE_CHRONOLOGY_FOR_OUTER_BOUNDARY`

Primary classification: `OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE`

Next action: `TRACE_ACTUAL_RUNTIME_WRITES_FOR_DRAW_OVAL_OUTER_BOUNDARY`

## Short Result

FOR-288 isolates why `draw_oval_outer_boundary` stayed white/layer after the
canceled FOR-287 attempt. The survivor is not a zero mask, missing blur support,
or AA clip coverage miss. It is the full-clip outer blur band: the source is
partial-alpha red, FOR-286 reconstructs a red-tinted store for 59/59 pixels, and
the final readback is still 59/59 white/layer.

FOR-287 useful signal, kept as canceled evidence only:

| Measure | Before | After |
|---|---:|---:|
| Readback red-dominant pixels | 9 | 50 |
| Readback white/layer pixels | 78 | 59 |
| CPU/reference similarity | 97.31% | 97.13% |
| CPU/reference >32 pixels | 15726 | 16785 |

FOR-287 was not merged because the global score regressed. This report does not
promote M60 and does not change production rendering.

## Zone Comparison

| Zone | Targets | Zero mask | Blur alpha buckets | Clip coverage buckets | Alpha after blur+clip | Dispatch red | Reconstructed red store | Opaque writes | Partial writes | FOR-286 readback white | FOR-287 after white | Anchor mask/clip |
|---|---:|---:|---|---|---|---:|---:|---:|---:|---:|---:|---|
| `draw_oval_outer_boundary` | 59 | 0 | zero=0 partial=59 full=0 | zero=0 partial=0 full=59 | zero=0 partial=59 full=0 | 59 | 59 | 0 | 59 | 59 | 59 | `250/255` |
| `difference_oval_inner_boundary` | 18 | 0 | zero=0 partial=0 full=18 | zero=0 partial=10 full=8 | zero=0 partial=10 full=8 | 18 | 18 | 8 | 10 | 14 | 0 | `255/255` |
| `halo_interior` | 12 | 0 | zero=0 partial=2 full=10 | zero=0 partial=0 full=12 | zero=0 partial=2 full=10 | 12 | 12 | 10 | 2 | 5 | 0 | `255/255` |

`draw_oval_outer_boundary` differs from the two improved zones because every
target pixel is full clip coverage and every target pixel has partial blur
alpha. The canceled FOR-287 pre-clip/double-modulation change improved the
inner/halo pixels but left exactly the 59 outer-boundary white/layer pixels.

## Cause Classification

| Candidate cause | Selected | Confidence | Evidence |
|---|---|---|---|
| `sourceMaskOrBlurSupport` | `False` | `high` | All three zones have non-zero mask support on CPU/reference >32 targets. The outer boundary has 59/59 partial blur-alpha pixels, so the source is present; it is not a zero-source miss. |
| `preClipTooEarlyOrTooLate` | `False` | `medium` | The canceled FOR-287 pre-clip attempt fixed the zones with inner/halo white readback, but outer remained 59 white/layer pixels. Outer has full active-AA clip coverage on 59/59 targets, so changing the difference pre-clip timing is effectively not the discriminating variable there. |
| `aaCoverageDifference` | `False` | `high` | Outer has full clip coverage on 59/59 targets, while inner has partial clip coverage on 10/18 targets. The failure persists where clip AA is not partial. |
| `whiteDrawRedDrawOrder` | `False` | `medium` | The GM source draws the white kSrc background before the difference-clipped red oval, and the source has no authored white draw after c.drawRRect(rr, paint). The symptom is therefore not a simple GM command order inversion. |
| `otherLaterWrite` | `True` | `high` | FOR-286 reconstructs red-tinted written values for 89/89 targets, including 59/59 outer pixels. The committed outer readback is still 59/59 white/layer after FOR-286 and remains 59 after the canceled FOR-287 attempt, which points to a later overwrite or commit/order path after the reconstructed red store rather than payload, clip coverage, or SrcOver math. |
| `proofInsufficient` | `False` | `medium` | The existing artifacts are sufficient to choose the next audit target. They are not a production fix: the next correction should first instrument actual runtime write chronology for the outer boundary pixels. |

FOR-287 contrast: FOR-287 moved global readback red-dominant pixels 9 -> 50 and white/layer 78 -> 59; inner and halo reached 0 white/layer, while outer stayed exactly 59 white/layer. The surviving 59 pixels match the FOR-286 outer window cardinality.

Decision: `OTHER_LATER_WRITE_AFTER_RECONSTRUCTED_RED_STORE`. The most probable next fix target
is a later CPU store/commit/order write after the reconstructed red store. It is
not a global `SrcOver`, `blend`, `setPixel`, GPU/WebGPU, threshold, Ganesh,
Graphite, or SkSL issue.

## Full Scene And Preservation

| Measure | Value |
|---|---:|
| CPU/reference similarity | 97.31% |
| CPU matching pixels | 908439 |
| CPU max channel delta | 237 |
| CPU/reference >32 | 15726 |
| GPU/reference similarity | 98.48% |
| GPU/reference >32 | 2869 |

M60 stays `expected-unsupported`: `coverage.nested-clip-visual-parity-below-threshold`.
The crop diagnostic stays `image-filter.crop-input-nonnull-prepass-required`.

Machine artifact:
`reports/wgsl-pipeline/scenes/artifacts/m60-bounded-nested-rrect-clip/m60-outer-boundary-store-order-audit-for288.json`
