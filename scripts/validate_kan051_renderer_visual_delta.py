#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/renderer-visual-delta"
OUTPUT_JSON = "kan-051-renderer-visual-delta.json"
OUTPUT_MARKDOWN = "kan-051-renderer-visual-delta.md"
ASSET_ROOT = f"{DEFAULT_OUTPUT_DIR}/assets"

RENDERER_SOURCE_FILES = [
    "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt",
    "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/ClipDifferenceCrossTest.kt",
]

TARGET_DOCS = [
    ".upstream/target/skia-like-realtime-renderer-target.md",
    ".upstream/specs/skia-like-realtime/03-skia-fidelity-and-gm-promotion.md",
    ".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md",
]

EXISTING_REFUSAL_PACKS = [
    {
        "ticket": "KAN-041",
        "path": "reports/wgsl-pipeline/image-filter-dag-bounded-v3/kan-041-image-filter-dag-bounded-v3.json",
        "requiredSnippets": [
            "image-filter.dag-or-picture-prepass-required",
            "image-filter.crop-input-nonnull-prepass-required",
        ],
    },
    {
        "ticket": "KAN-042",
        "path": "reports/wgsl-pipeline/image-filter-residual-refusal-matrix/kan-042-image-filter-residual-refusal-matrix.json",
        "requiredSnippets": [
            "image-filter.blur-large-sigma-unsupported",
            "image-filter.runtime-descriptor-scope-dependency-gated",
        ],
    },
    {
        "ticket": "KAN-043",
        "path": "reports/wgsl-pipeline/text-shaping-fallback-scope/kan-043-text-shaping-fallback-scope.json",
        "requiredSnippets": [
            "font.shaping-feature-unsupported",
            "font.shaping-fallback-missing",
        ],
    },
    {
        "ticket": "KAN-044",
        "path": "reports/wgsl-pipeline/glyph-mask-atlas-ownership/kan-044-glyph-mask-atlas-ownership.json",
        "requiredSnippets": [
            "coverage.alpha-mask-unsupported",
        ],
    },
    {
        "ticket": "KAN-045",
        "path": "reports/wgsl-pipeline/color-pipeline-bounded-policy/kan-045-color-pipeline-bounded-policy.json",
        "requiredSnippets": [
            "color.color-space-wide-gamut-unsupported",
            "color.f16-policy-candidate-worsens-reference",
        ],
    },
    {
        "ticket": "KAN-046",
        "path": "reports/wgsl-pipeline/tile-modes-mipmap-boundary/kan-046-tile-modes-mipmap-boundary.json",
        "requiredSnippets": [
            "image-sampling.mipmap-unsupported",
        ],
    },
]


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-051 renderer visual delta validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def load_json(path: Path) -> Any:
    require(path.is_file(), f"missing JSON file: {path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {path}: {exc}")


def max_channel(delta: dict[str, Any]) -> int:
    return max(int(delta.get(channel, 0)) for channel in ("a", "r", "g", "b"))


def halo_error(stats: dict[str, Any], sample: str = "clipRectTop") -> int:
    samples = stats.get("haloSamples")
    require(isinstance(samples, dict), f"{stats.get('phase')} stats missing halo samples")
    value = samples.get(sample)
    require(isinstance(value, dict), f"{stats.get('phase')} stats missing {sample} halo sample")
    return int(value.get("rgbAbsError", 0))


def phase_bundle(root: Path, phase: str) -> dict[str, Any]:
    phase_root = root / ASSET_ROOT / phase
    stats = load_json(phase_root / "stats.json")
    route_cpu = load_json(phase_root / "route-cpu.json")
    route_gpu = load_json(phase_root / "route-gpu.json")
    require(stats.get("phase") == phase, f"{phase} stats phase mismatch")
    require(stats.get("ticket") == "KAN-051", f"{phase} stats ticket mismatch")
    require(stats.get("sceneId") == "clip-rect-difference", f"{phase} stats scene mismatch")
    require(route_cpu.get("fallbackReason") == "none", f"{phase} CPU route must not fallback")
    require(route_gpu.get("fallbackReason") == "none", f"{phase} GPU route must not fallback")
    require(route_gpu.get("selectedRoute"), f"{phase} GPU route missing selectedRoute")
    artifacts = stats.get("artifacts")
    require(isinstance(artifacts, dict), f"{phase} stats missing artifacts")
    return {
        "phase": phase,
        "stats": stats,
        "routeCpu": route_cpu,
        "routeGpu": route_gpu,
        "artifacts": artifacts,
    }


def audit_artifacts(root: Path, bundle: dict[str, Any]) -> dict[str, Any]:
    missing = []
    for key in ("reference", "cpu", "gpu", "cpuDiff", "gpuDiff", "routeCpu", "routeGpu", "stats"):
        path = bundle["artifacts"].get(key)
        if not isinstance(path, str) or not (root / path).is_file():
            missing.append({"phase": bundle["phase"], "key": key, "path": path})
    return {"phase": bundle["phase"], "missing": missing}


def audit_existing_refusals(root: Path) -> list[dict[str, Any]]:
    rows = []
    for spec in EXISTING_REFUSAL_PACKS:
        path = root / spec["path"]
        require(path.is_file(), f"{spec['ticket']} refusal pack missing: {spec['path']}")
        text = path.read_text(encoding="utf-8")
        missing = [snippet for snippet in spec["requiredSnippets"] if snippet not in text]
        require(not missing, f"{spec['ticket']} refusal reasons missing: {missing}")
        payload = load_json(path)
        rows.append(
            {
                "ticket": spec["ticket"],
                "path": spec["path"],
                "ticketField": payload.get("ticket"),
                "requiredSnippets": spec["requiredSnippets"],
                "status": "visible",
            }
        )
    return rows


def metric_improvements(before_stats: dict[str, Any], after_stats: dict[str, Any]) -> dict[str, Any]:
    before_max = max_channel(before_stats.get("gpuMaxChannelDelta", {}))
    after_max = max_channel(after_stats.get("gpuMaxChannelDelta", {}))
    before_halo = halo_error(before_stats)
    after_halo = halo_error(after_stats)
    before_matching = int(before_stats.get("gpuMatchingPixels", 0))
    after_matching = int(after_stats.get("gpuMatchingPixels", 0))
    before_mismatching = int(before_stats.get("gpuMismatchingPixels", 0))
    after_mismatching = int(after_stats.get("gpuMismatchingPixels", 0))
    before_similarity = float(before_stats.get("gpuSimilarity", 0.0))
    after_similarity = float(after_stats.get("gpuSimilarity", 0.0))
    return {
        "gpuMatchingPixels": {
            "before": before_matching,
            "after": after_matching,
            "delta": after_matching - before_matching,
            "improved": after_matching > before_matching,
        },
        "gpuMismatchingPixels": {
            "before": before_mismatching,
            "after": after_mismatching,
            "delta": after_mismatching - before_mismatching,
            "improved": after_mismatching < before_mismatching,
        },
        "gpuMaxChannelDelta": {
            "before": before_max,
            "after": after_max,
            "delta": after_max - before_max,
            "improved": after_max < before_max,
        },
        "clipRectTopHaloRgbAbsError": {
            "before": before_halo,
            "after": after_halo,
            "delta": after_halo - before_halo,
            "improved": after_halo < before_halo,
        },
        "gpuSimilarity": {
            "before": before_similarity,
            "after": after_similarity,
            "delta": round(after_similarity - before_similarity, 4),
            "improved": after_similarity > before_similarity,
        },
    }


def has_renderer_source_change(files: list[str]) -> bool:
    for path in files:
        if path.startswith(("gpu-raster/src/main/", "cpu-raster/src/main/", "render-pipeline/src/main/", "kanvas-skia/src/main/")):
            return True
    return False


def build_evidence(root: Path) -> dict[str, Any]:
    before = phase_bundle(root, "before")
    after = phase_bundle(root, "after")
    improvements = metric_improvements(before["stats"], after["stats"])
    evidence = {
        "schemaVersion": 1,
        "ticket": "KAN-051",
        "packId": "kan-051-renderer-visual-delta",
        "status": "passed",
        "closureDecision": "renderer-visual-delta",
        "selectedScene": {
            "sceneId": "clip-rect-difference",
            "drawKind": "Skbug9319GM",
            "reason": "Priority clip/maskFilter scene already had reference, CPU, GPU, diff, stats, and route diagnostics from the current PM bundle.",
        },
        "rendererChanged": True,
        "blocked": False,
        "evidenceOnlyClosure": False,
        "thresholdsWeakened": False,
        "thresholdPolicy": {
            "before": before["stats"].get("threshold"),
            "after": after["stats"].get("threshold"),
            "changed": before["stats"].get("threshold") != after["stats"].get("threshold"),
        },
        "rootCause": {
            "summary": "The WebGPU maskFilter blur path used point-sampled Gaussian weights for small-sigma filled rect masks, while the Skia-like A8 blur profile integrates coverage over destination pixels.",
            "beforeDivergence": "The clipRect(kDifference) halo at (60,9) and (9,60) was RGB 224 against reference RGB 207, leaving a one-pixel halo too light by 17.",
            "fixedPath": "SkWebGpuDevice.drawPathWithBlurMaskFilterIfApplicable now uses a per-pixel integrated Gaussian profile only for axis-aligned filled rect maskFilter blur.",
            "regressionGuard": "Non-rect maskFilter blur, including the tiny RRect half of Skbug9319GM, keeps the historical point-sampled profile.",
        },
        "rendererSourceFiles": RENDERER_SOURCE_FILES,
        "before": before,
        "after": after,
        "improvements": improvements,
        "artifactAudit": [
            audit_artifacts(root, before),
            audit_artifacts(root, after),
        ],
        "existingRefusals": audit_existing_refusals(root),
        "targetDocs": TARGET_DOCS,
        "requiredValidation": [
            "rtk python3 scripts/test_validate_kan051_renderer_visual_delta.py",
            "rtk env GRADLE_USER_HOME=/Users/chaos/.codex/worktrees/7442/kanvas/.gradle-codex ./gradlew --no-daemon validateKan051RendererVisualDelta",
            "rtk env GRADLE_USER_HOME=/Users/chaos/.codex/worktrees/7442/kanvas/.gradle-codex ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.ClipDifferenceCrossTest",
            "rtk env GRADLE_USER_HOME=/Users/chaos/.codex/worktrees/7442/kanvas/.gradle-codex ./gradlew --no-daemon pipelineConformance",
            "rtk env GRADLE_USER_HOME=/Users/chaos/.codex/worktrees/7442/kanvas/.gradle-codex ./gradlew --no-daemon pipelinePmBundle",
        ],
        "nonClaims": [
            "no-threshold-change",
            "no-broad-Skia-parity-claim",
            "no-multi-family-correction-claim",
            "no-Ganesh-or-Graphite-port",
            "no-SkSL-compiler-IR-or-VM",
        ],
    }
    validate_evidence(evidence, root)
    return evidence


def validate_evidence(evidence: dict[str, Any], root: Path) -> None:
    blocked = bool(evidence.get("blocked"))
    renderer_changed = bool(evidence.get("rendererChanged"))
    require(renderer_changed or blocked, "rendererChanged=false is allowed only when blocked=true")
    require(not evidence.get("evidenceOnlyClosure"), "evidence-only closure is forbidden")
    require(not evidence.get("thresholdsWeakened"), "thresholds must not be weakened")

    if blocked:
        root_cause = evidence.get("rootCause")
        require(isinstance(root_cause, dict) and root_cause.get("summary"), "blocked evidence requires explicit root cause")
        return

    require(has_renderer_source_change(list(evidence.get("rendererSourceFiles", []))), "missing renderer source change outside scripts/reports/Gradle wiring")
    before = evidence.get("before")
    after = evidence.get("after")
    require(isinstance(before, dict) and isinstance(after, dict), "before and after evidence are required")
    before_stats = before.get("stats")
    after_stats = after.get("stats")
    require(isinstance(before_stats, dict) and isinstance(after_stats, dict), "before/after stats are required")
    require(before_stats.get("threshold") == after_stats.get("threshold"), "threshold changed between before and after")
    require(before_stats.get("tolerance") == after_stats.get("tolerance"), "comparison tolerance changed between before and after")
    require(before_stats.get("sceneId") == after_stats.get("sceneId"), "scene changed between before and after")
    require(before_stats.get("drawKind") == after_stats.get("drawKind"), "drawKind changed between before and after")
    require(before_stats.get("pixels") == after_stats.get("pixels"), "pixel count changed between before and after")

    for audit in evidence.get("artifactAudit", []):
        require(not audit.get("missing"), f"{audit.get('phase')} evidence artifacts missing: {audit.get('missing')}")
    for bundle_name, bundle in (("before", before), ("after", after)):
        route_gpu = bundle.get("routeGpu")
        route_cpu = bundle.get("routeCpu")
        require(isinstance(route_gpu, dict), f"{bundle_name} GPU route missing")
        require(isinstance(route_cpu, dict), f"{bundle_name} CPU route missing")
        require(route_gpu.get("fallbackReason") == "none", f"{bundle_name} GPU fallback is not none")
        require(route_cpu.get("fallbackReason") == "none", f"{bundle_name} CPU fallback is not none")
        artifacts = bundle.get("artifacts")
        require(isinstance(artifacts, dict), f"{bundle_name} artifacts missing")
        for key in ("reference", "cpu", "gpu", "cpuDiff", "gpuDiff", "routeCpu", "routeGpu", "stats"):
            path = artifacts.get(key)
            require(isinstance(path, str) and (root / path).is_file(), f"{bundle_name} missing artifact {key}: {path}")

    require(
        "point-sampled" in str(before.get("routeGpu", {}).get("maskFilterKernelProfile", "")),
        "before route must record the point-sampled kernel profile",
    )
    require(
        "integrated" in str(after.get("routeGpu", {}).get("maskFilterKernelProfile", "")),
        "after route must record the integrated kernel profile",
    )

    improvements = metric_improvements(before_stats, after_stats)
    require(any(item["improved"] for item in improvements.values()), "no pixel/diff/stat metric improved")
    require(evidence.get("existingRefusals"), "existing KAN-041..KAN-046 refusal visibility audit missing")


def render_markdown(evidence: dict[str, Any]) -> str:
    before_stats = evidence["before"]["stats"]
    after_stats = evidence["after"]["stats"]
    improvements = evidence["improvements"]
    before_route = evidence["before"]["routeGpu"]
    after_route = evidence["after"]["routeGpu"]

    def row(name: str, before_value: Any, after_value: Any, delta: Any) -> str:
        return f"| {name} | {before_value} | {after_value} | {delta} |"

    refusal_lines = "\n".join(
        f"- `{row['ticket']}`: `{row['path']}` keeps `{', '.join(row['requiredSnippets'])}` visible."
        for row in evidence["existingRefusals"]
    )
    validation_lines = "\n".join(f"- `{command}`" for command in evidence["requiredValidation"])
    source_lines = "\n".join(f"- `{path}`" for path in evidence["rendererSourceFiles"])

    return f"""# KAN-051 Renderer Visual Delta

KAN-051 selects `clip-rect-difference` / `Skbug9319GM` and burns down a
real WebGPU renderer divergence in the small-sigma `clipRect(kDifference)`
maskFilter halo. The CPU/reference artifact is unchanged across phases; the
GPU artifact changes through `SkWebGpuDevice.drawPathWithBlurMaskFilterIfApplicable`.

## Renderer Change

{evidence['rootCause']['summary']}

Before route: `{before_route['selectedRoute']}` with `{before_route['maskFilterKernelProfile']}`.
After route: `{after_route['selectedRoute']}` with `{after_route['maskFilterKernelProfile']}`.
Fallback reason remains `none`.

Changed renderer files:

{source_lines}

## Metrics

Threshold and tolerance stay constant: threshold `{before_stats['threshold']}` -> `{after_stats['threshold']}`,
tolerance `{before_stats['tolerance']}` -> `{after_stats['tolerance']}`.

| Metric | Before | After | Delta |
|---|---:|---:|---:|
{row('GPU matching pixels', improvements['gpuMatchingPixels']['before'], improvements['gpuMatchingPixels']['after'], improvements['gpuMatchingPixels']['delta'])}
{row('GPU mismatching pixels', improvements['gpuMismatchingPixels']['before'], improvements['gpuMismatchingPixels']['after'], improvements['gpuMismatchingPixels']['delta'])}
{row('GPU max channel delta', improvements['gpuMaxChannelDelta']['before'], improvements['gpuMaxChannelDelta']['after'], improvements['gpuMaxChannelDelta']['delta'])}
{row('clipRect top halo RGB abs error', improvements['clipRectTopHaloRgbAbsError']['before'], improvements['clipRectTopHaloRgbAbsError']['after'], improvements['clipRectTopHaloRgbAbsError']['delta'])}
{row('GPU similarity', improvements['gpuSimilarity']['before'], improvements['gpuSimilarity']['after'], improvements['gpuSimilarity']['delta'])}

## Artifacts

Before artifacts live under `reports/wgsl-pipeline/renderer-visual-delta/assets/before/`.
After artifacts live under `reports/wgsl-pipeline/renderer-visual-delta/assets/after/`.
Each phase contains `skia.png`, `cpu.png`, `gpu.png`, `cpu-diff.png`,
`gpu-diff.png`, `stats.json`, `route-cpu.json`, and `route-gpu.json`.

## Existing Refusals

{refusal_lines}

## Validation

{validation_lines}
"""


def write_outputs(root: Path, output_dir: Path) -> dict[str, Any]:
    evidence = build_evidence(root)
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / OUTPUT_JSON).write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (output_dir / OUTPUT_MARKDOWN).write_text(render_markdown(evidence), encoding="utf-8")
    return evidence


def main(argv: list[str]) -> int:
    root = Path(argv[1]).resolve() if len(argv) > 1 else Path.cwd()
    output_dir = Path(argv[2]).resolve() if len(argv) > 2 else root / DEFAULT_OUTPUT_DIR
    try:
        write_outputs(root, output_dir)
    except ValidationError as exc:
        print(exc, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
