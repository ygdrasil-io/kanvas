# Performance, Telemetry, And Cache Gates

Status: Draft
Date: 2026-06-13

## Purpose

Define the performance, telemetry, cache, and promotion-gate evidence required
for the new GPU renderer.

Correct rendering support and performance readiness are separate claims. A GPU
route can be functionally promoted with correctness evidence while remaining
unpromoted for performance-sensitive realtime use until measured gates exist.

## Telemetry Ledger

`GPUTelemetryLedger` is the deterministic reporting product for one recording,
frame, test scene, or PM run.

It records:

- scene or test identity;
- context and capability facts;
- command counts by family;
- route counts by `GPUNative`, `CPUPreparedGPU`, `CPUReferenceOnly`, and
  `RefuseDiagnostic`;
- draw-pass, compute-pass, copy, upload, and readback counts;
- material key, material program, material assembly plan, render pipeline,
  compute pipeline, and WGSL module counts;
- paint pipeline, material source descriptor, material source plan, gradient
  stop store, image shader source, local matrix source, and material-source
  refusal counters when touched;
- payload slot counts, payload fingerprints, bind group churn, and upload byte
  counts;
- cache hits, misses, evictions, and creations;
- CPU-prepared artifact counts, bytes, uploads, and refusals;
- texture descriptor, texture view, sampler, ownership plan, sampled binding,
  imported texture, surface lease, and stale-generation counters when touched;
- image codec registry, metadata scan, decode request, decoded pixel,
  animated frame selection, composed frame, color conversion, orientation,
  mip preparation, image upload artifact, and image refusal counters when
  touched;
- path/coverage atlas policy, entry, resident byte, upload/compute byte,
  generation, stale-entry, retry/split, eviction, and budget-pressure counters
  when touched;
- clip descriptor, route, effective-element, scissor, analytic, stencil,
  mask, shader, pass-split, mask-byte, budget-pressure, and refusal counters
  when touched;
- layer/saveLayer execution class, elision, cull, offscreen target, target
  byte, live byte, init-with-previous, backdrop, source filter, restore
  composite, nesting, pass split, budget-pressure, and refusal counters when
  touched;
- destination-read requirement, strategy, copied byte, pass-split, binding,
  target-generation, stale-generation, active-attachment refusal, and
  budget-pressure counters when touched;
- filter graph, node, render-node, compute-node, intermediate, bounds, crop,
  tile, runtime-effect, folded color-filter, copied/intermediate byte, pass
  count, and refusal counters when touched;
- runtime-effect registry descriptor, registry generation, lookup,
  compatibility-key, descriptor kind, WGSL validation/reflection, CPU oracle,
  uniform byte, child slot, live-parameter, cache, budget, and dynamic SkSL
  refusal counters when touched;
- color-management plan, value-spec, profile, ICC/CICP, transform,
  gradient-interpolation, image-profile, runtime color uniform, layer
  restoration, F16, HDR, gainmap, store/readback, cache, budget, and refusal
  counters when touched;
- coordinate-space, transform class, transform chain, inverse, pixel-grid,
  bounds kind, expansion, rounding, clip-reduction proof, full-target
  widening, coordinate payload, cache, budget, and refusal counters when
  touched;
- submitted bytes for uniforms, vertices, indices, storage, textures, and
  readbacks when available;
- timing samples when enabled;
- stable skip and risk states.

Telemetry must be deterministic for the same inputs unless the field is
explicitly marked as measured runtime data.

## Cache Domains

Cache reporting is grouped by domain:

| Domain | Required counters |
|---|---|
| Material source planning | paint descriptor plans, source descriptor plans, source-kind histogram, stage-order refusals, gradient stop store plans, image shader plans, local matrix plans, shader-blend plans, source cache hits/misses, and source budget refusals. |
| Material dictionary cache | lookups, hits, misses, assigned program IDs, version mismatches, refusals. |
| Material assembly plan cache | lookups, hits, misses, created plans, unsupported requirements. |
| Material module cache | lookups, hits, misses, created modules, validation failures. |
| Render pipeline cache | lookups, hits, misses, created pipelines, creation failures. |
| Compute pipeline cache | lookups, hits, misses, created pipelines, creation failures. |
| Layout cache | bind group layouts, uniform layouts, packing plans, mismatches. |
| Payload cache | uniform payload slots, resource binding slots, scoped fingerprints, hits, misses, bytes uploaded, materialization failures. |
| Resource cache | textures, buffers, samplers, bind groups, live bytes, evictions. |
| Texture ownership cache | texture descriptors, view descriptors, sampler descriptors, ownership plans, sampled bindings, imports, uploads, surface leases, stale generations, rebuilds, and refusals. |
| Image/codec cache | codec registry lookups, metadata scans, decode requests, decoded pixel bytes, animated frame selections, composed frame bytes, color/profile conversions, orientation applications, mip preparations, upload artifact lookups, codec refusals, and budget refusals. |
| Artifact registry | artifact lookups, hits, misses, uploads, evictions, budget refusals. |
| Atlas cache | atlas descriptor count, policy version, page count, resident entries, resident bytes, lookup hits/misses, entry creations, page activations, evictions, compactions/resets, upload bytes, compute write bytes, stale entries, retry/split counts, hard capability refusals, and budget refusals. |
| Clip pipeline | descriptor lookups, effective element analysis hits/misses, route counts, scissor counts, analytic plan counts, stencil producer/consumer counts, atomic group counts, mask artifact lookups, mask bytes, clip shader descriptor counts, clip-induced pass splits, hard capability refusals, and budget refusals. |
| Layer/saveLayer execution | saveLayer count, execution-class counts, elision/cull counts, offscreen target allocations, target bytes, maximum live bytes, reuse hits/misses, init-with-previous copied bytes, backdrop/filter intermediate bytes, restore composite counts, nesting depth, pass splits, hard capability refusals, and budget refusals. |
| Destination-read resources | requirements, strategies, target-copy descriptors, existing-intermediate bindings, copied bytes, pass splits, generation checks, active-attachment refusals, and budget refusals. |
| Filter/effect pipeline | graph count, node count, render/compute/copy route counts, intermediate count and bytes, bounds/crop/tile refusals, runtime-effect descriptor counts, folded color-filter counts, destination/backdrop read counts, pass splits, artifact use, and budget refusals. |
| Text/glyph pipeline | `DrawTextRun` count, text run/subrun count, representation counts, route counts, glyph instances, atlas page count, atlas bytes, upload bytes, instance buffer bytes, stale generation refusals, SDF/color/bitmap/SVG route refusals, text-induced pass splits, and budget refusals. |
| Runtime-effect registry | registry version/generation, descriptor count, descriptor-kind histogram, lookup hits/misses/refusals, compatibility-key hits/misses/refusals, descriptor-version invalidations, WGSL validation/reflection successes/failures, CPU oracle availability, uniform bytes, child slot counts, live-parameter updates/refusals, dynamic SkSL refusals, and budget refusals. |
| Color management | color management plans, value-spec histograms, color-space descriptors, ICC profile parses/refusals, CICP metadata/refusals, transform cache hits/misses, CPU/WGSL transform counts, gradient interpolation spaces, image profile conversions, runtime color uniforms, layer color restoration, F16/HDR/gainmap counts, LUT bytes, store/readback conversions, and budget refusals. |
| Coordinate/transform/bounds | coordinate-space descriptors, transform descriptors/classes/chains, inverse plans, precision plans, pixel-grid descriptors, bounds descriptors/kinds, expansion plans, rounded bounds, clip-reduction proofs, full-target widening, coordinate payload bytes, cache hits/misses, and budget refusals. |

A cache hit is performance evidence, not correctness evidence. A cache miss
must never change rendering output.

## Budget Policy

Each bounded cache or artifact family must declare:

- key-space expectation;
- memory budget;
- upload budget when relevant;
- eviction policy;
- stale-entry policy;
- frame-local, recording-local, cache-resident, or atlas-resident lifetime;
- refusal behavior when rebuilding would exceed budget;
- diagnostic counters.

Unbounded caches are allowed only when a spec proves the key space is finite and
small. Otherwise, the route remains unpromoted for realtime use.

## Warmup And Measurement

Performance measurements must distinguish:

- cold start;
- warmup;
- stable frame;
- cache pressure run;
- resized or device-generation-changed run;
- skipped or unavailable measurement.

Warmup policy must name:

- number of warmup frames or iterations;
- what resources may be created during warmup;
- expected cache creation count after warmup;
- whether timestamp queries, wall-clock time, or deterministic counters are
  used.

When timestamp queries are unavailable, the report may use wall-clock or
counter-only evidence, but it must label the measurement lane honestly.

## Performance Gates

`GPUPerformanceGate` defines a measurable readiness gate.

Gate fields:

- gate ID and version;
- scene or fixture set;
- route families covered;
- required capability lane;
- warmup policy;
- metric source;
- threshold;
- quarantine and rebaseline rule;
- failure classification;
- PM artifact path.

Initial gate families:

- pipeline creation after warmup;
- pipeline cache hit rate;
- material dictionary hit rate;
- material assembly plan creation after warmup;
- WGSL module creation after warmup;
- uniform upload bytes;
- uniform payload slot reuse;
- bind group churn;
- vertex/index/storage upload bytes;
- texture upload bytes;
- image decode bytes;
- image composed-frame bytes;
- image upload artifact cache hit rate;
- image codec refusal count;
- surface lease churn;
- imported texture refusal count;
- stale texture generation rebuild or refusal count;
- artifact cache hit rate;
- path/coverage atlas hit rate;
- path/coverage atlas resident bytes;
- path/coverage atlas upload or compute bytes;
- path/coverage atlas retry/split and eviction count;
- clip effective element analysis hit rate;
- clip stencil atomic group count;
- clip mask byte count;
- clip-induced pass split count;
- clip shader refusal count;
- layer offscreen target allocation count;
- layer maximum live bytes;
- layer target reuse hit rate;
- layer-induced pass split count;
- layer elision proof rate;
- destination-read copy bytes;
- destination-read pass split count;
- destination-read target snapshot live bytes;
- filter intermediate live bytes;
- filter render/compute pass count;
- filter artifact cache hit rate;
- filter runtime-effect refusal count;
- runtime-effect registry lookup hit rate;
- runtime-effect descriptor-version invalidation count;
- runtime-effect WGSL validation/reflection failure count;
- runtime-effect live-parameter key stability;
- runtime-effect dynamic SkSL refusal count;
- color transform cache hit rate;
- color WGSL transform creation after warmup;
- color profile parse refusal count;
- HDR/gainmap refusal count;
- transform chain cache hit rate;
- inverse transform creation after warmup;
- rounded-bounds cache hit rate;
- full-target widening count and area;
- coordinate payload bytes;
- pass count and draw count stability;
- readback availability for evidence lanes;
- frame time or GPU time when accepted by a target milestone.

## Rebaseline And Quarantine

Gate thresholds must be versioned outside ad hoc test code. A regression cannot
be hidden by changing a threshold without a rebaseline artifact.

Quarantine is allowed only when:

- the gate is marked non-release-blocking or candidate;
- the failure is linked to a known environment or adapter issue;
- the PM report marks the lane as quarantined;
- follow-up ownership is recorded.

Skipped, quarantined, and missing measurements do not count as passing
performance evidence.

## PM Evidence

PM evidence must include:

- route counts;
- refusal counts;
- capability facts;
- cache counters;
- artifact memory and upload counters;
- timing metrics when claimed;
- warmup policy;
- skipped and quarantined lanes;
- known limitations.

Reports must distinguish:

- correctness support;
- GPU execution evidence;
- realtime performance readiness;
- candidate gates;
- reporting-only telemetry.

## Diagnostics

Stable reason-code examples:

- `perf.gate.pipeline_creation_after_warmup`
- `perf.gate.pipeline_cache_hit_rate`
- `perf.gate.uniform_upload_bytes`
- `perf.gate.texture_upload_bytes`
- `perf.gate.artifact_budget_pressure`
- `perf.gate.runtime_effect_registry_lookup_hit_rate`
- `perf.gate.runtime_effect_live_parameter_key_stability`
- `perf.gate.frame_time_candidate`
- `perf.skip.timestamp_query_unavailable`
- `perf.skip.adapter_unavailable`
- `perf.quarantine.environment_variance`
- `perf.rebaseline.required`

Performance reason codes are report classifications. They must not replace
route refusal codes.

## Validation Requirements

Promoted telemetry/cache behavior requires:

- deterministic ledger dumps for fixed scenes;
- cache counter tests for hit, miss, eviction, and stale generation;
- runtime-effect registry counter tests for lookup, compatibility-key refusal,
  descriptor-version invalidation, WGSL validation/reflection, CPU oracle
  availability, live-parameter update, and dynamic SkSL refusal;
- artifact budget pressure tests when artifacts are used;
- warmup policy tests for promoted performance gates;
- skipped-lane tests for unavailable GPU timing or readback;
- PM report fixtures that expose correctness, performance, skipped, and
  quarantined states separately.

## Non-Goals

- Do not claim realtime readiness from correctness tests alone.
- Do not count missing metrics as measured evidence.
- Do not make cache residency a correctness dependency.
- Do not hide cache misses or evictions from reports.
- Do not silently rebaseline performance thresholds.
