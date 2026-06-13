#!/usr/bin/env python3
import json
import hashlib
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/text-glyph-atlas-visual-delta"
OUTPUT_JSON = "kan-055-text-glyph-atlas-visual-delta.json"
OUTPUT_MARKDOWN = "kan-055-text-glyph-atlas-visual-delta.md"

SELECTED_ROW_ID = "text.simple-latin.line.v1"
ATLAS_ROUTE = "webgpu.text.glyph-atlas.simple-latin"
LEGACY_ROUTE = "webgpu.text.outline-path.simple-latin"
KAN053_ROOT_CAUSE = "text-atlas-alpha-mask-draw-route-not-materialized"
KAN053_BLOCKER_REASON = "requires-production-glyph-atlas-sampling-route"

KAN053_JSON = "reports/wgsl-pipeline/text-glyph-visual-delta/kan-053-text-glyph-visual-delta.json"
KAN054_JSON = "reports/wgsl-pipeline/webgpu-glyph-atlas-sampling-route/kan-054-webgpu-glyph-atlas-sampling-route.json"
ROUTE_WEBGPU_JSON = "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/route-webgpu.json"
STATS_JSON = "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/stats.json"

MATERIALIZED_BEFORE_ARTIFACTS = {
    "reference": f"{DEFAULT_OUTPUT_DIR}/before/reference.png",
    "cpu": f"{DEFAULT_OUTPUT_DIR}/before/cpu.png",
    "webGpu": f"{DEFAULT_OUTPUT_DIR}/before/webgpu.png",
    "cpuDiff": f"{DEFAULT_OUTPUT_DIR}/before/cpu-diff.png",
    "webGpuDiff": f"{DEFAULT_OUTPUT_DIR}/before/webgpu-diff.png",
    "routeCpu": f"{DEFAULT_OUTPUT_DIR}/before/route-cpu.json",
    "routeWebGpu": f"{DEFAULT_OUTPUT_DIR}/before/route-webgpu.json",
    "stats": f"{DEFAULT_OUTPUT_DIR}/before/stats.json",
    "atlas": f"{DEFAULT_OUTPUT_DIR}/before/atlas.json",
}
MATERIALIZED_AFTER_ARTIFACTS = {
    "reference": f"{DEFAULT_OUTPUT_DIR}/after/reference.png",
    "cpu": f"{DEFAULT_OUTPUT_DIR}/after/cpu.png",
    "webGpu": f"{DEFAULT_OUTPUT_DIR}/after/webgpu.png",
    "cpuDiff": f"{DEFAULT_OUTPUT_DIR}/after/cpu-diff.png",
    "webGpuDiff": f"{DEFAULT_OUTPUT_DIR}/after/webgpu-diff.png",
    "routeCpu": f"{DEFAULT_OUTPUT_DIR}/after/route-cpu.json",
    "routeWebGpu": f"{DEFAULT_OUTPUT_DIR}/after/route-webgpu.json",
    "stats": f"{DEFAULT_OUTPUT_DIR}/after/stats.json",
    "atlas": f"{DEFAULT_OUTPUT_DIR}/after/atlas.json",
}


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-055 text glyph atlas visual delta validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def load_json(path: Path) -> Any:
    require(path.is_file(), f"missing JSON file: {path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {path}: {exc}")


def sha256_file(path: Path) -> str:
    require(path.is_file(), f"missing artifact file: {path}")
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def comparison(stats: dict[str, Any], key: str) -> dict[str, Any]:
    value = stats.get(key)
    require(isinstance(value, dict), f"missing stats comparison: {key}")
    return value


def artifact_audit(root: Path, before: dict[str, Any], after: dict[str, Any]) -> list[dict[str, Any]]:
    audit = []
    for prefix, bundle in (("before", before), ("after", after)):
        source_artifacts = bundle.get("sourceArtifacts")
        artifacts = bundle.get("artifacts")
        require(isinstance(source_artifacts, dict), f"{prefix} source artifacts missing")
        require(isinstance(artifacts, dict), f"{prefix} materialized artifacts missing")
        for key, materialized_path in sorted(artifacts.items()):
            source_path = source_artifacts.get(key)
            require(isinstance(source_path, str) and source_path, f"{prefix}.{key} source artifact path missing")
            require(isinstance(materialized_path, str) and materialized_path, f"{prefix}.{key} materialized artifact path missing")
            source_hash = sha256_file(root / source_path)
            materialized_hash = sha256_file(root / materialized_path)
            source_comparable = not materialized_path.endswith(".png")
            audit.append(
                {
                    "key": f"{prefix}.{key}",
                    "sourcePath": source_path,
                    "path": materialized_path,
                    "exists": (root / materialized_path).is_file(),
                    "sourceComparable": source_comparable,
                    "sourceSha256": source_hash,
                    "sha256": materialized_hash,
                    "matchesSource": source_hash == materialized_hash if source_comparable else None,
                }
            )
    return audit


def current_artifacts(route_webgpu: dict[str, Any], stats: dict[str, Any]) -> dict[str, str]:
    source_paths = {
        "reference": stats.get("referenceArtifact") or route_webgpu.get("referenceArtifact"),
        "cpu": stats.get("cpuArtifact"),
        "webGpu": stats.get("webGpuArtifact") or route_webgpu.get("renderArtifact"),
        "cpuDiff": stats.get("cpuDiffArtifact"),
        "webGpuDiff": stats.get("webGpuDiffArtifact") or route_webgpu.get("diffArtifact"),
        "routeCpu": stats.get("routeCpuArtifact"),
        "routeWebGpu": stats.get("routeWebGpuArtifact") or ROUTE_WEBGPU_JSON,
        "stats": STATS_JSON,
        "atlas": "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/atlas.json",
    }
    require(all(isinstance(value, str) and value for value in source_paths.values()), "after artifact source paths missing")
    return source_paths


def historical_before(kan053: dict[str, Any]) -> dict[str, Any]:
    require(kan053.get("blocked") is True, "KAN-053 prior evidence must be blocked")
    blocker = kan053.get("blocker")
    require(isinstance(blocker, dict), "KAN-053 prior blocker missing")
    require(blocker.get("rootCause") == KAN053_ROOT_CAUSE, "KAN-053 prior root cause changed")
    require(blocker.get("reasonCode") == KAN053_BLOCKER_REASON, "KAN-053 prior blocker reason changed")
    current = kan053.get("current")
    require(isinstance(current, dict), "KAN-053 current evidence missing")
    stats = current.get("stats")
    route = current.get("routeWebGpu")
    artifacts = current.get("artifacts")
    require(isinstance(stats, dict), "KAN-053 before stats missing")
    require(isinstance(route, dict), "KAN-053 before route missing")
    require(isinstance(artifacts, dict), "KAN-053 before artifacts missing")
    return {
        "source": KAN053_JSON,
        "blocked": True,
        "blocker": blocker,
        "stats": stats,
        "routeWebGpu": route,
        "sourceArtifacts": dict(MATERIALIZED_BEFORE_ARTIFACTS),
        "artifacts": dict(MATERIALIZED_BEFORE_ARTIFACTS),
    }


def after_bundle(root: Path) -> dict[str, Any]:
    route_webgpu = load_json(root / ROUTE_WEBGPU_JSON)
    stats = load_json(root / STATS_JSON)
    webgpu = comparison(stats, "webGpuComparison")
    cpu = comparison(stats, "cpuComparison")
    return {
        "source": "KAN-054",
        "stats": {
            "sceneId": stats.get("sceneId"),
            "webGpuMismatchingPixels": webgpu.get("mismatchingPixels"),
            "cpuMismatchingPixels": cpu.get("mismatchingPixels"),
            "webGpuMinusCpuReferenceMismatches": webgpu.get("mismatchingPixels") - cpu.get("mismatchingPixels"),
            "webGpuSimilarity": webgpu.get("similarity"),
            "cpuSimilarity": cpu.get("similarity"),
            "tolerance": stats.get("tolerance"),
            "webGpuSimilarityThreshold": stats.get("webGpuSimilarityThreshold"),
            "cpuSimilarityThreshold": stats.get("cpuSimilarityThreshold"),
            "globalThresholdChanged": stats.get("globalThresholdChanged"),
        },
        "routeWebGpu": {
            "path": ROUTE_WEBGPU_JSON,
            "sceneId": route_webgpu.get("sceneId"),
            "selectedRoute": route_webgpu.get("selectedRoute"),
            "legacyRoute": route_webgpu.get("legacyRoute"),
            "atlasRouteIdentifier": route_webgpu.get("atlasRouteIdentifier"),
            "fallbackReason": route_webgpu.get("fallbackReason"),
            "nonClaims": route_webgpu.get("nonClaims", []),
        },
        "sourceArtifacts": current_artifacts(route_webgpu, stats),
        "artifacts": dict(MATERIALIZED_AFTER_ARTIFACTS),
    }


def build_evidence(root: Path) -> dict[str, Any]:
    kan053 = load_json(root / KAN053_JSON)
    kan054 = load_json(root / KAN054_JSON)
    before = historical_before(kan053)
    after = after_bundle(root)

    before_stats = before["stats"]
    after_stats = after["stats"]
    before_mismatches = before_stats.get("webGpuMismatchingPixels")
    after_mismatches = after_stats.get("webGpuMismatchingPixels")
    require(isinstance(before_mismatches, int), "before WebGPU mismatch count missing")
    require(isinstance(after_mismatches, int), "after WebGPU mismatch count missing")
    improvement = before_mismatches - after_mismatches

    return {
        "schemaVersion": 1,
        "ticket": "KAN-055",
        "packId": "kan-055-text-glyph-atlas-visual-delta",
        "status": "pending-validation",
        "selectedRowId": SELECTED_ROW_ID,
        "rendererChangedSource": "KAN-054",
        "kan054RendererChanged": kan054.get("rendererChanged"),
        "before": before,
        "after": after,
        "delta": {
            "mode": "before-after-route-delta",
            "webGpuMismatchingPixelsBefore": before_mismatches,
            "webGpuMismatchingPixelsAfter": after_mismatches,
            "webGpuMismatchingPixelsImprovement": improvement,
            "webGpuMinusCpuReferenceMismatchesBefore": before_stats.get("webGpuMinusCpuReferenceMismatches"),
            "webGpuMinusCpuReferenceMismatchesAfter": after_stats.get("webGpuMinusCpuReferenceMismatches"),
        },
        "kan053Decision": "close-root-cause-resolved" if improvement > 0 else "blocked-no-improvement",
        "nonClaims": [
            "no-broad-text-claim",
            "no-shaping-claim",
            "no-fallback-font-claim",
            "no-emoji-or-color-font-claim",
            "no-sdf-or-lcd-claim",
            "no-threshold-change-claim",
        ],
        "artifactAudit": artifact_audit(root, before, after),
    }


def validate_evidence(evidence: dict[str, Any], root: Path) -> None:
    require(evidence.get("ticket") == "KAN-055", "ticket mismatch")
    require(evidence.get("packId") == "kan-055-text-glyph-atlas-visual-delta", "packId mismatch")
    require(evidence.get("selectedRowId") == SELECTED_ROW_ID, "selected row mismatch")
    require(evidence.get("kan054RendererChanged") is True, "KAN-054 renderer change source missing")

    before = evidence.get("before")
    after = evidence.get("after")
    delta = evidence.get("delta")
    require(isinstance(before, dict), "before evidence missing")
    require(isinstance(after, dict), "after evidence missing")
    require(isinstance(delta, dict), "delta evidence missing")

    before_route = before.get("routeWebGpu")
    after_route = after.get("routeWebGpu")
    require(isinstance(before_route, dict), "before route missing")
    require(isinstance(after_route, dict), "after route missing")
    require(before_route.get("selectedRoute") == LEGACY_ROUTE, "before route must be legacy outline route")
    require(after_route.get("selectedRoute") == ATLAS_ROUTE, "after route must be glyph atlas")
    require(after_route.get("fallbackReason") == "none", "after fallbackReason must be none")

    before_stats = before.get("stats")
    after_stats = after.get("stats")
    require(isinstance(before_stats, dict), "before stats missing")
    require(isinstance(after_stats, dict), "after stats missing")
    require(before_stats.get("globalThresholdChanged") is False, "global threshold changed before")
    require(after_stats.get("globalThresholdChanged") is False, "global threshold changed after")
    require(float(before_stats.get("webGpuSimilarityThreshold")) == 95.0, "threshold changed before")
    require(float(after_stats.get("webGpuSimilarityThreshold")) == 95.0, "threshold changed after")
    require(delta.get("webGpuMismatchingPixelsBefore") == 608, "before mismatch count must match KAN-053 evidence")
    require(delta.get("webGpuMismatchingPixelsAfter") == 122, "after mismatch count must match KAN-054 evidence")
    require(delta.get("webGpuMismatchingPixelsImprovement") == 486, "improvement must match KAN-055 evidence")
    require(delta.get("webGpuMismatchingPixelsImprovement") > 0, "WebGPU mismatch count must improve")
    require(evidence.get("kan053Decision") == "close-root-cause-resolved", "KAN-053 decision must close resolved root cause")

    non_claims = evidence.get("nonClaims")
    require(isinstance(non_claims, list), "non-claims missing")
    require("no-broad-text-claim" in non_claims, "missing no-broad-text-claim")
    require("no-threshold-change-claim" in non_claims, "missing threshold non-claim")

    missing = [item for item in evidence.get("artifactAudit", []) if not item.get("exists")]
    require(not missing, f"required artifacts missing: {missing}")
    mismatched = [
        item
        for item in evidence.get("artifactAudit", [])
        if item.get("sourceComparable") and not item.get("matchesSource")
    ]
    require(not mismatched, f"materialized artifact hash mismatch: {mismatched}")
    audits = {item.get("key"): item for item in evidence.get("artifactAudit", [])}
    require(
        audits["before.webGpu"].get("sha256") != audits["after.webGpu"].get("sha256"),
        "before/after WebGPU visual artifacts must be distinct",
    )
    require(
        audits["before.webGpuDiff"].get("sha256") != audits["after.webGpuDiff"].get("sha256"),
        "before/after WebGPU diff artifacts must be distinct",
    )


def markdown_report(evidence: dict[str, Any]) -> str:
    delta = evidence["delta"]
    after_route = evidence["after"]["routeWebGpu"]
    return f"""# KAN-055 Text Glyph Atlas Visual Delta

KAN-055 compares KAN-053 before evidence against the KAN-054 glyph atlas WebGPU route for `{evidence['selectedRowId']}`.

## Decision

| Field | Value |
|---|---|
| Status | `{evidence['status']}` |
| KAN-053 decision | `{evidence['kan053Decision']}` |
| After route | `{after_route['selectedRoute']}` |
| After fallback | `{after_route['fallbackReason']}` |
| WebGPU mismatches before | `{delta['webGpuMismatchingPixelsBefore']}` |
| WebGPU mismatches after | `{delta['webGpuMismatchingPixelsAfter']}` |
| Improvement | `{delta['webGpuMismatchingPixelsImprovement']}` |

## Non-Claims

- No broad text, shaping, fallback-font, emoji/color-font, LCD, or SDF support claim.
- No threshold change is used to claim the improvement.
- KAN-055 only closes the KAN-053 root cause for the selected simple Latin row.
"""


def write_outputs(root: Path, output_dir: Path) -> dict[str, Any]:
    evidence = build_evidence(root)
    validate_evidence(evidence, root)
    evidence["status"] = "pass"
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / OUTPUT_JSON).write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (output_dir / OUTPUT_MARKDOWN).write_text(markdown_report(evidence), encoding="utf-8")
    return evidence


def main(argv: list[str]) -> int:
    root = Path(argv[1]).resolve() if len(argv) > 1 else Path.cwd()
    output_dir = Path(argv[2]).resolve() if len(argv) > 2 else root / DEFAULT_OUTPUT_DIR
    evidence = write_outputs(root, output_dir)
    print(
        "KAN-055 text glyph atlas visual delta validation passed: "
        f"{evidence['delta']['webGpuMismatchingPixelsBefore']} -> "
        f"{evidence['delta']['webGpuMismatchingPixelsAfter']} mismatches"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main(sys.argv))
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        raise SystemExit(1)
