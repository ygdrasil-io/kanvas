# KAN-054 WebGPU Glyph Atlas Sampling Route

KAN-054 validates the selected simple Latin WebGPU text route as `webgpu.text.glyph-atlas.simple-latin` with `fallbackReason=none`.

## Decision

| Field | Value |
|---|---|
| rendererChanged | `True` |
| blocked | `False` |
| selected row | `text.simple-latin.line.v1` |
| selected route | `webgpu.text.glyph-atlas.simple-latin` |
| unblocks | `unblocks KAN-055` |
| does not close | `KAN-053` |

## Atlas

| Field | Value |
|---|---|
| texture format | `R8Unorm` |
| mask format | `A8` |
| sampler | `nearest-clamp-to-edge` |
| upload bytes | `12928` |
| stats route | `webgpu.text.glyph-atlas.simple-latin` |

## Refusal Boundary

Standalone WebGPU alpha-mask coverage remains `expected-unsupported` via `coverage.alpha-mask-unsupported`. This report makes no general alpha-mask WebGPU claim.
