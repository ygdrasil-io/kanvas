# Scene Tag Taxonomy

Status: Draft
Target: `.upstream/target/rendering-conformance-performance-target.md`
Milestone: M41 -- Generated Conformance Dashboard

## Purpose

Scene tags provide stable grouping dimensions for the WGSL scene dashboard.
They support exact filtering, search, and aggregate summaries without replacing
support status, rendered artifacts, route diagnostics, diffs, stats, thresholds,
or fallback policy.

Tags are review metadata. A tag must never be used as the only evidence that a
route is supported.

## Format

Each scene row carries a top-level `tags` array:

```json
"tags": [
  "source.generated",
  "feature.image.bitmap",
  "route.gpu.webgpu",
  "reference.skia-upstream",
  "maturity.generated-evidence",
  "risk.none"
]
```

Rules:

- tags are lowercase ASCII strings;
- tags use dot-separated namespaces;
- tags may contain lowercase letters, digits, dots, and hyphens;
- empty tags, uppercase tags, whitespace, slashes, and duplicate tags are invalid;
- generated and mixed rows must include at least one tag in each required
  namespace: `source.*`, `feature.*`, `route.*`, `reference.*`, and
  `maturity.*`.

## Namespaces

Required generated-row namespaces:

| Namespace | Meaning | Examples |
|---|---|---|
| `source.*` | Provenance of the dashboard row. | `source.static`, `source.generated` |
| `feature.*` | Rendering family, paint family, or route family exercised by the scene. | `feature.image-filter`, `feature.path-aa`, `feature.gradient.linear` |
| `route.*` | CPU/GPU route class or refusal class. | `route.cpu.oracle`, `route.gpu.webgpu`, `route.gpu.expected-unsupported` |
| `reference.*` | Reference origin. | `reference.skia-upstream`, `reference.cpu-oracle`, `reference.test-oracle` |
| `maturity.*` | Evidence maturity. | `maturity.static-evidence`, `maturity.generated-evidence`, `maturity.adapter-backed` |

Optional but recommended namespace:

| Namespace | Meaning | Examples |
|---|---|---|
| `risk.*` | Review risk or blocker family used for aggregates. | `risk.none`, `risk.tracked-gap`, `risk.expected-unsupported`, `risk.edge-budget` |

## Semantic Constraints

- `maturity.adapter-backed` requires concrete GPU adapter metadata in
  `gpu.stats.adapter`.
- `route.gpu.expected-unsupported` requires a stable non-`none`
  `gpu.route.fallbackReason`.
- `gpu.status=expected-unsupported` rows must carry
  `route.gpu.expected-unsupported`.
- `maturity.static-evidence` identifies hand-authored or checked-in evidence;
  it is not weaker or stronger than `maturity.generated-evidence` without the
  row's artifacts and diagnostics.
- `risk.none` means no known dashboard review risk for that row; it does not
  imply full Skia compatibility outside the selected scene.

## Dashboard Behavior

The dashboard must:

- expose exact-tag filtering;
- include tags in text search;
- keep tags visually compact so cards remain readable;
- show aggregate counts for the visible scene set by `feature.*`,
  `maturity.*`, and `risk.*`;
- export the same aggregate families in `data/scenes.json` for report tooling.

## Non-Goals

- Do not add free-form prose tags or one-off test-name tags.
- Do not encode support status as tags instead of `status` and `gpu.status`.
- Do not hide expected unsupported scenes by filtering them out by default.
- Do not change `priority`, `referenceKind`, artifact paths, stats, or route
  diagnostics as part of tag maintenance.
