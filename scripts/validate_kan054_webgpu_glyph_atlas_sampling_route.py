#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/webgpu-glyph-atlas-sampling-route"
OUTPUT_JSON = "kan-054-webgpu-glyph-atlas-sampling-route.json"
OUTPUT_MARKDOWN = "kan-054-webgpu-glyph-atlas-sampling-route.md"

SELECTED_ROW_ID = "text.simple-latin.line.v1"
ATLAS_ROUTE = "webgpu.text.glyph-atlas.simple-latin"
LEGACY_ROUTE = "webgpu.text.outline-path.simple-latin"
GLYPH_SOURCE_ROUTE = "font.glyph.outline-path"
ALPHA_MASK_REFUSAL = "coverage.alpha-mask-unsupported"
KAN053_ROOT_CAUSE = "text-atlas-alpha-mask-draw-route-not-materialized"
KAN053_BLOCKER_REASON = "requires-production-glyph-atlas-sampling-route"

KAN053_JSON = "reports/wgsl-pipeline/text-glyph-visual-delta/kan-053-text-glyph-visual-delta.json"
KAN044_JSON = "reports/wgsl-pipeline/glyph-mask-atlas-ownership/kan-044-glyph-mask-atlas-ownership.json"
ROUTE_WEBGPU_JSON = "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/route-webgpu.json"
STATS_JSON = "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/stats.json"
ATLAS_JSON = "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/atlas.json"

REQUIRED_ARTIFACTS = {
    "routeWebGpu": ROUTE_WEBGPU_JSON,
    "stats": STATS_JSON,
    "atlas": ATLAS_JSON,
    "kan053": KAN053_JSON,
    "kan044": KAN044_JSON,
}


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-054 WebGPU glyph atlas sampling route validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def load_json(path: Path) -> Any:
    require(path.is_file(), f"missing JSON file: {path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {path}: {exc}")


def select_ownership_row(rows: Any, row_id: str) -> dict[str, Any]:
    require(isinstance(rows, list), "KAN-044 ownershipRows missing")
    row = next((item for item in rows if isinstance(item, dict) and item.get("rowId") == row_id), None)
    require(isinstance(row, dict), f"KAN-044 row missing: {row_id}")
    return row


def required_artifact_audit(root: Path, route_webgpu: dict[str, Any], stats: dict[str, Any]) -> list[dict[str, Any]]:
    paths = dict(REQUIRED_ARTIFACTS)
    for key in ("referenceArtifact", "renderArtifact", "diffArtifact"):
        value = route_webgpu.get(key)
        if isinstance(value, str) and value:
            paths[key] = value
    for key in ("cpuArtifact", "cpuDiffArtifact", "webGpuArtifact", "webGpuDiffArtifact"):
        value = stats.get(key)
        if isinstance(value, str) and value:
            paths[key] = value
    return [
        {"key": key, "path": path, "exists": (root / path).is_file()}
        for key, path in sorted(paths.items())
    ]


def build_evidence(root: Path) -> dict[str, Any]:
    route_webgpu = load_json(root / ROUTE_WEBGPU_JSON)
    stats = load_json(root / STATS_JSON)
    atlas = load_json(root / ATLAS_JSON)
    kan053 = load_json(root / KAN053_JSON)
    kan044 = load_json(root / KAN044_JSON)

    alpha_refusal = select_ownership_row(
        kan044.get("ownershipRows"),
        "webgpu.standalone-alpha-mask-refusal",
    )
    atlas_row = select_ownership_row(
        kan044.get("ownershipRows"),
        "text.simple-latin.glyph-atlas.upload-plan",
    )
    atlas_diagnostics = atlas.get("diagnostics") if isinstance(atlas.get("diagnostics"), dict) else {}

    require("glyphRoute" not in route_webgpu, "ambiguous glyphRoute in WebGPU route evidence; use glyphSourceRoute")
    require("glyphRoute" not in stats, "ambiguous glyphRoute in stats evidence; use glyphSourceRoute")

    return {
        "schemaVersion": 1,
        "ticket": "KAN-054",
        "packId": "kan-054-webgpu-glyph-atlas-sampling-route",
        "status": "pending-validation",
        "rendererChanged": True,
        "blocked": False,
        "selectedRowId": route_webgpu.get("sceneId"),
        "selectedRoute": route_webgpu.get("selectedRoute"),
        "fallbackReason": route_webgpu.get("fallbackReason"),
        "routeWebGpu": {
            "path": ROUTE_WEBGPU_JSON,
            "sceneId": route_webgpu.get("sceneId"),
            "backend": route_webgpu.get("backend"),
            "selectedRoute": route_webgpu.get("selectedRoute"),
            "fallbackReason": route_webgpu.get("fallbackReason"),
            "glyphSourceRoute": route_webgpu.get("glyphSourceRoute"),
            "atlasRouteIdentifier": route_webgpu.get("atlasRouteIdentifier"),
            "supportScope": route_webgpu.get("supportScope"),
            "nonClaims": route_webgpu.get("nonClaims", []),
        },
        "atlas": {
            "path": ATLAS_JSON,
            "routeIdentifier": atlas.get("routeIdentifier"),
            "textureLabel": atlas.get("textureLabel"),
            "textureFormat": atlas.get("textureFormat"),
            "textureUsage": atlas.get("textureUsage"),
            "maskFormat": atlas.get("maskFormat"),
            "generation": atlas.get("generation"),
            "width": atlas.get("width"),
            "height": atlas.get("height"),
            "uploadByteCount": atlas.get("uploadByteCount"),
            "uploadSha256": atlas.get("uploadSha256"),
            "sourceCacheSha256": atlas.get("sourceCacheSha256"),
            "sampler": atlas_diagnostics.get("sampler"),
            "resourceKind": atlas_diagnostics.get("resourceKind"),
            "glyphEntryCount": atlas_diagnostics.get("glyphEntryCount"),
            "nonEmptyGlyphCount": atlas_diagnostics.get("nonEmptyGlyphCount"),
            "emptyGlyphCount": atlas_diagnostics.get("emptyGlyphCount"),
        },
        "stats": {
            "path": STATS_JSON,
            "sceneId": stats.get("sceneId"),
            "webGpuRouteIdentifier": stats.get("webGpuRouteIdentifier"),
            "atlasRouteIdentifier": stats.get("atlasRouteIdentifier"),
            "glyphSourceRoute": stats.get("glyphSourceRoute"),
            "atlasUploadByteCount": stats.get("atlasUploadByteCount"),
            "atlasUploadSha256": stats.get("atlasUploadSha256"),
            "glyphInventoryCount": stats.get("glyphInventoryCount"),
            "dedupedGlyphCount": stats.get("dedupedGlyphCount"),
            "tolerance": stats.get("tolerance"),
            "cpuSimilarityThreshold": stats.get("cpuSimilarityThreshold"),
            "webGpuSimilarityThreshold": stats.get("webGpuSimilarityThreshold"),
            "globalThresholdChanged": stats.get("globalThresholdChanged"),
            "fallbackPolicy": stats.get("fallbackPolicy"),
        },
        "kan044AtlasUploadPlan": atlas_row,
        "standaloneAlphaMaskRefusal": alpha_refusal,
        "kan053PriorEvidence": {
            "path": KAN053_JSON,
            "ticket": kan053.get("ticket"),
            "blocked": kan053.get("blocked"),
            "blocker": kan053.get("blocker"),
        },
        "nonClaims": [
            "no-general-alpha-mask-webgpu-claim",
            "no-broad-text-claim",
            "no-shaping-claim",
            "no-fallback-font-claim",
            "no-emoji-or-color-font-claim",
            "no-sdf-or-lcd-claim",
            "no-dynamic-atlas-eviction-claim",
            "no-ganesh-graphite-text-ops-claim",
        ],
        "unblocks": ["KAN-055"],
        "doesNotClose": ["KAN-053"],
        "artifactAudit": required_artifact_audit(root, route_webgpu, stats),
    }


def validate_evidence(evidence: dict[str, Any], root: Path) -> None:
    require(evidence.get("ticket") == "KAN-054", "ticket mismatch")
    require(evidence.get("packId") == "kan-054-webgpu-glyph-atlas-sampling-route", "packId mismatch")
    require(evidence.get("rendererChanged") is True, "rendererChanged must be true")
    require(evidence.get("blocked") is False, "blocked must be false")
    require(evidence.get("selectedRowId") == SELECTED_ROW_ID, "selected row mismatch")
    require(evidence.get("selectedRoute") == ATLAS_ROUTE, "selected route must be glyph atlas")
    require(evidence.get("fallbackReason") == "none", "fallbackReason must be none")

    route_webgpu = evidence.get("routeWebGpu")
    require(isinstance(route_webgpu, dict), "WebGPU route evidence missing")
    require(route_webgpu.get("selectedRoute") == ATLAS_ROUTE, "route WebGPU selected route must be glyph atlas")
    require(route_webgpu.get("fallbackReason") == "none", "route WebGPU fallbackReason must be none")
    require(route_webgpu.get("glyphSourceRoute") == GLYPH_SOURCE_ROUTE, "route WebGPU glyph source route must be scoped")
    require(route_webgpu.get("atlasRouteIdentifier") == ATLAS_ROUTE, "route WebGPU atlas route mismatch")

    atlas = evidence.get("atlas")
    require(isinstance(atlas, dict), "atlas facts missing")
    require(atlas.get("routeIdentifier") == ATLAS_ROUTE, "atlas route mismatch")
    require(atlas.get("textureFormat") == "R8Unorm", "atlas texture format must be R8Unorm")
    require(atlas.get("maskFormat") == "A8", "atlas mask format must be A8")
    require(isinstance(atlas.get("uploadByteCount"), int) and atlas["uploadByteCount"] > 0, "atlas upload bytes missing")
    require(atlas.get("sampler") == "nearest-clamp-to-edge", "atlas sampler must be nearest-clamp-to-edge")

    stats = evidence.get("stats")
    require(isinstance(stats, dict), "stats facts missing")
    require(stats.get("sceneId") == SELECTED_ROW_ID, "stats scene mismatch")
    require(stats.get("webGpuRouteIdentifier") == ATLAS_ROUTE, "stats route must be glyph atlas")
    require(stats.get("atlasRouteIdentifier") == ATLAS_ROUTE, "stats atlas route mismatch")
    require(stats.get("glyphSourceRoute") == GLYPH_SOURCE_ROUTE, "stats glyph source route must be scoped")
    require(stats.get("globalThresholdChanged") is False, "global threshold changed")
    require(float(stats.get("cpuSimilarityThreshold")) == 95.0, "threshold changed")
    require(float(stats.get("webGpuSimilarityThreshold")) == 95.0, "threshold changed")

    kan053_prior = evidence.get("kan053PriorEvidence")
    require(isinstance(kan053_prior, dict), "KAN-053 prior evidence missing")
    require(kan053_prior.get("blocked") is True, "KAN-053 prior evidence must remain blocked")
    kan053_blocker = kan053_prior.get("blocker")
    require(isinstance(kan053_blocker, dict), "KAN-053 prior blocker missing")
    require(
        kan053_blocker.get("reasonCode") == KAN053_BLOCKER_REASON,
        "KAN-053 prior blocker reason changed",
    )
    require(kan053_blocker.get("rootCause") == KAN053_ROOT_CAUSE, "KAN-053 prior root cause changed")

    atlas_row = evidence.get("kan044AtlasUploadPlan")
    require(isinstance(atlas_row, dict), "KAN-044 atlas upload plan missing")
    route = atlas_row.get("route")
    require(isinstance(route, dict), "KAN-044 atlas route missing")
    require(route.get("webGpu") == ATLAS_ROUTE, "KAN-044 atlas route mismatch")
    require(route.get("coverage") == "not-owned-by-coverage", "atlas ownership moved to coverage")

    alpha_refusal = evidence.get("standaloneAlphaMaskRefusal")
    require(isinstance(alpha_refusal, dict), "standalone alpha-mask refusal missing")
    require(alpha_refusal.get("status") == "expected-unsupported", "standalone alpha-mask support changed")
    require(alpha_refusal.get("reasonCode") == ALPHA_MASK_REFUSAL, "standalone alpha-mask refusal reason changed")

    non_claims = evidence.get("nonClaims")
    require(isinstance(non_claims, list), "non-claims missing")
    require("no-general-alpha-mask-webgpu-claim" in non_claims, "missing non-claim: no-general-alpha-mask-webgpu-claim")
    require("KAN-055" in evidence.get("unblocks", []), "KAN-055 unblock marker missing")
    require("KAN-053" in evidence.get("doesNotClose", []), "KAN-053 non-closure marker missing")

    missing = [item for item in evidence.get("artifactAudit", []) if not item.get("exists")]
    require(not missing, f"required artifacts missing: {missing}")
    for key, path in REQUIRED_ARTIFACTS.items():
        require((root / path).is_file(), f"missing required artifact {key}: {path}")


def markdown_report(evidence: dict[str, Any]) -> str:
    atlas = evidence["atlas"]
    stats = evidence["stats"]
    alpha_refusal = evidence["standaloneAlphaMaskRefusal"]
    return f"""# KAN-054 WebGPU Glyph Atlas Sampling Route

KAN-054 validates the selected simple Latin WebGPU text route as `{evidence["selectedRoute"]}` with `fallbackReason={evidence["fallbackReason"]}`.

## Decision

| Field | Value |
|---|---|
| rendererChanged | `{evidence["rendererChanged"]}` |
| blocked | `{evidence["blocked"]}` |
| selected row | `{evidence["selectedRowId"]}` |
| selected route | `{evidence["selectedRoute"]}` |
| unblocks | `unblocks KAN-055` |
| does not close | `KAN-053` |

## Atlas

| Field | Value |
|---|---|
| texture format | `{atlas["textureFormat"]}` |
| mask format | `{atlas["maskFormat"]}` |
| sampler | `{atlas["sampler"]}` |
| upload bytes | `{atlas["uploadByteCount"]}` |
| stats route | `{stats["webGpuRouteIdentifier"]}` |

## Refusal Boundary

Standalone WebGPU alpha-mask coverage remains `{alpha_refusal["status"]}` via `{alpha_refusal["reasonCode"]}`. This report makes no general alpha-mask WebGPU claim.
"""


def write_outputs(root: Path, output_dir: Path) -> dict[str, Any]:
    evidence = build_evidence(root)
    validate_evidence(evidence, root)
    evidence["status"] = "pass"
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / OUTPUT_JSON).write_text(
        json.dumps(evidence, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    (output_dir / OUTPUT_MARKDOWN).write_text(markdown_report(evidence), encoding="utf-8")
    return evidence


def main(argv: list[str]) -> int:
    root = Path(argv[1]).resolve() if len(argv) > 1 else Path(__file__).resolve().parents[1]
    output_dir = Path(argv[2]).resolve() if len(argv) > 2 else root / DEFAULT_OUTPUT_DIR
    try:
        write_outputs(root, output_dir)
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    print(f"KAN-054 WebGPU glyph atlas sampling route evidence written to {output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
