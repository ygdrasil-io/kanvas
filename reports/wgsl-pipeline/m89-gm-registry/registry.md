# M89 GM Support/Refusal Registry

Status: generated evidence

This registry normalizes current generated dashboard rows and policy-only GM visibility rows. It does not promote support, weaken thresholds, or change render paths.

## Counters

- Total rows: `47`
- Support claims: `22`
- Policy-only rows: `20`
- Row-specific refusal links: `7`
- Dependency gate links: `4`
- Grouped policy refusal links: `9`
- Edge-budget gate links: `2`
- Image-filter prepass gate links: `1`
- Text/glyph dependency gate links: `2`
- Unlinked unsupported rows: `0`
- Expected unsupported with fallback: `25`
- Linked M66 rows: `18`
- Linked M86 rows: `18`
- Linked M90 rows: `9`

### Status

- `expected-unsupported`: `25`
- `pass`: `22`

### Family

- `bitmap-image`: `7`
- `blend-color`: `2`
- `gradient`: `4`
- `image-filter`: `5`
- `path-aa`: `18`
- `runtime-effect`: `3`
- `text-glyph`: `7`
- `transform-layer`: `1`

## Non-Claims

- Policy-only visibility rows do not count as support.
- Expected-unsupported rows remain visible until row-specific evidence proves support.
- Rows that only miss strict similarity/tolerance thresholds belong in fidelity burn-down, not production missing-feature accounting.
- WGSL remains the WebGPU shader target; SkSL is compatibility/refusal wording only.

## Follow-Up Focus

- Convert policy-only rows into row-specific evidence without changing claims.
- Keep dependency-gated text/font rows visible until real dependencies land.
- Keep tolerance-only rows in fidelity burn-down rather than production missing-feature counts.
- Keep `unlinkedUnsupportedRows=0` so every non-pass row has PM-visible support/refusal context.
