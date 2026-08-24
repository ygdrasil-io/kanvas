# M84 Historical Native Frame Timing Candidate Evidence

Status: `candidate-reporting-only`

M84 preserves historical native Kadre timing as a candidate/reporting payload.
The native source artifact was retired from the working tree; recover it from
Git history only. This evidence is not a current runtime requirement.

## PM Outcome

- Lane: `frame.kadre-windowed`
- Scene contract: `m83-display-list-pm-scene-v1`
- Gate phase: `candidate-reporting-only`
- Release blocking: `false`
- Counted as measured gate: `false`
- Warmup frames: `60`
- Measured samples: `120`
- p50/p95/worst: `16.6495 ms` / `18.1140 ms` / `18.3875 ms`
- Adapter: `AdapterInfo(architecture=, description=, device=Apple M2 Max, subgroupMaxSize=0, subgroupMinSize=0, vendor=, isFallbackAdapter=false)`

## Quarantine / Reporting Reasons

- `m84.reporting-only-until-owner-accepts-variance`

## Negative Fixture

- Status: `expected-fail`
- Reason: `m84.negative-fixture-p95-threshold-exceeded`
- Mutates baseline: `false`

## Validation

```bash
python3 -m json.tool reports/wgsl-pipeline/m84-native-frame-timing/evidence.json >/dev/null
```
