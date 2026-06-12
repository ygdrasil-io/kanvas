#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/text-glyph-visual-delta"
OUTPUT_JSON = "kan-053-text-glyph-visual-delta.json"
OUTPUT_MARKDOWN = "kan-053-text-glyph-visual-delta.md"

SELECTED_ROW_ID = "text.simple-latin.line.v1"
KAN043_JSON = "reports/wgsl-pipeline/text-shaping-fallback-scope/kan-043-text-shaping-fallback-scope.json"
KAN044_JSON = "reports/wgsl-pipeline/glyph-mask-atlas-ownership/kan-044-glyph-mask-atlas-ownership.json"
WEBGPU_ROUTE = "webgpu.text.outline-path.simple-latin"
ATLAS_ROUTE = "webgpu.text.glyph-atlas.simple-latin"
REFERENCE_KIND = "cpu-atlas-alpha-mask-oracle"
ROOT_CAUSE = "text-atlas-alpha-mask-draw-route-not-materialized"
BLOCKER_REASON = "requires-production-glyph-atlas-sampling-route"

CURRENT_ARTIFACTS = {
    "reference": "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/reference.png",
    "cpu": "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/cpu.png",
    "webGpu": "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/webgpu.png",
    "cpuDiff": "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/cpu-diff.png",
    "webGpuDiff": "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/webgpu-diff.png",
    "routeCpu": "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/route-cpu.json",
    "routeWebGpu": "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/route-webgpu.json",
    "stats": "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/stats.json",
    "atlas": "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/atlas.json",
}


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-053 text glyph visual delta validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def load_json(path: Path) -> Any:
    require(path.is_file(), f"missing JSON file: {path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {path}: {exc}")


def artifact_audit(root: Path, artifacts: dict[str, str]) -> list[dict[str, Any]]:
    return [
        {"key": key, "path": path, "exists": (root / path).is_file()}
        for key, path in artifacts.items()
    ]


def audit_artifacts(root: Path, current: dict[str, Any]) -> list[dict[str, Any]]:
    artifacts = current.get("artifacts")
    require(isinstance(artifacts, dict), "current artifacts missing")
    return artifact_audit(root, artifacts)


def select_row(rows: list[Any], key: str, value: str, source: str) -> dict[str, Any]:
    row = next((item for item in rows if isinstance(item, dict) and item.get(key) == value), None)
    require(isinstance(row, dict), f"{source} row missing: {value}")
    return row


def selected_row_from_kan043(root: Path) -> dict[str, Any]:
    payload = load_json(root / KAN043_JSON)
    rows = payload.get("textScopeRows")
    require(isinstance(rows, list), "KAN-043 textScopeRows missing")
    row = select_row(rows, "rowId", SELECTED_ROW_ID, "KAN-043")
    route = row.get("route")
    fallback = row.get("fallbackPolicy")
    font = row.get("font")
    require(isinstance(route, dict), "KAN-043 selected row route missing")
    require(isinstance(fallback, dict), "KAN-043 selected row fallback policy missing")
    require(isinstance(font, dict), "KAN-043 selected row font missing")
    return {
        "rowId": row.get("rowId"),
        "sourceTicket": "KAN-043",
        "pmCategory": row.get("pmCategory"),
        "status": row.get("status"),
        "textInput": row.get("textInput"),
        "font": {
            "face": font.get("face"),
            "source": font.get("source"),
            "sha256": font.get("sha256"),
            "sourceId": font.get("sourceId"),
        },
        "shapingRoute": row.get("shapingRoute"),
        "glyphIds": row.get("glyphIds"),
        "clusters": row.get("clusters"),
        "cpuRoute": route.get("cpu"),
        "webGpuRoute": route.get("gpu"),
        "glyphRoute": route.get("glyph"),
        "atlasRoute": ATLAS_ROUTE,
        "referenceKind": route.get("referenceKind"),
        "fallbackReason": fallback.get("reasonCode"),
        "artifacts": dict(CURRENT_ARTIFACTS),
        "nonClaims": row.get("nonClaims", []),
    }


def ownership_rows_from_kan044(root: Path) -> dict[str, Any]:
    payload = load_json(root / KAN044_JSON)
    rows = payload.get("ownershipRows")
    require(isinstance(rows, list), "KAN-044 ownershipRows missing")
    atlas_row = select_row(rows, "rowId", "text.simple-latin.glyph-atlas.upload-plan", "KAN-044")
    cpu_oracle = select_row(rows, "rowId", "text.simple-latin.cpu-mask-oracle", "KAN-044")
    handoff = select_row(rows, "rowId", "geometry.glyph-mask.alpha-mask-handoff", "KAN-044")
    alpha_refusal = select_row(rows, "rowId", "webgpu.standalone-alpha-mask-refusal", "KAN-044")
    return {
        "path": KAN044_JSON,
        "atlasUploadPlan": slim_ownership_row(atlas_row),
        "cpuMaskOracle": slim_ownership_row(cpu_oracle),
        "coverageHandoff": slim_ownership_row(handoff),
        "standaloneAlphaMaskRefusal": slim_ownership_row(alpha_refusal),
        "claimGuard": payload.get("claimGuard"),
    }


def slim_ownership_row(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "rowId": row.get("rowId"),
        "pmCategory": row.get("pmCategory"),
        "status": row.get("status"),
        "sourceEvidence": row.get("sourceEvidence"),
        "reasonCode": row.get("reasonCode"),
        "route": row.get("route"),
        "atlas": row.get("atlas"),
        "cacheIds": row.get("cacheIds"),
        "ownership": row.get("ownership"),
        "coverageOwnsAtlas": row.get("coverageOwnsAtlas"),
        "coveragePlan": row.get("coveragePlan"),
        "coverageModel": row.get("coverageModel"),
        "nonClaims": row.get("nonClaims", []),
    }


def current_bundle(root: Path) -> dict[str, Any]:
    stats = load_json(root / CURRENT_ARTIFACTS["stats"])
    route_cpu = load_json(root / CURRENT_ARTIFACTS["routeCpu"])
    route_webgpu = load_json(root / CURRENT_ARTIFACTS["routeWebGpu"])
    atlas = load_json(root / CURRENT_ARTIFACTS["atlas"])
    cpu_mismatches = stats.get("cpuComparison", {}).get("mismatchingPixels")
    webgpu_mismatches = stats.get("webGpuComparison", {}).get("mismatchingPixels")
    require(isinstance(cpu_mismatches, int), "CPU mismatch count missing")
    require(isinstance(webgpu_mismatches, int), "WebGPU mismatch count missing")
    return {
        "phase": "current",
        "artifacts": dict(CURRENT_ARTIFACTS),
        "stats": {
            "sceneId": stats.get("sceneId"),
            "scopeId": stats.get("scopeId"),
            "text": stats.get("text"),
            "fontSourceId": stats.get("fontSourceId"),
            "glyphInventoryCount": stats.get("glyphInventoryCount"),
            "dedupedGlyphCount": stats.get("dedupedGlyphCount"),
            "cpuNonWhitePixels": stats.get("cpuNonWhitePixels"),
            "webGpuNonWhitePixels": stats.get("webGpuNonWhitePixels"),
            "tolerance": stats.get("tolerance"),
            "cpuSimilarityThreshold": stats.get("cpuSimilarityThreshold"),
            "webGpuSimilarityThreshold": stats.get("webGpuSimilarityThreshold"),
            "cpuSimilarity": stats.get("cpuComparison", {}).get("similarity"),
            "webGpuSimilarity": stats.get("webGpuComparison", {}).get("similarity"),
            "cpuMatchingPixels": stats.get("cpuComparison", {}).get("matchingPixels"),
            "webGpuMatchingPixels": stats.get("webGpuComparison", {}).get("matchingPixels"),
            "cpuMismatchingPixels": cpu_mismatches,
            "webGpuMismatchingPixels": webgpu_mismatches,
            "webGpuMinusCpuReferenceMismatches": webgpu_mismatches - cpu_mismatches,
            "cpuMaxChannelDiff": stats.get("cpuComparison", {}).get("maxChannelDiff"),
            "webGpuMaxChannelDiff": stats.get("webGpuComparison", {}).get("maxChannelDiff"),
            "globalThresholdChanged": stats.get("globalThresholdChanged"),
            "fallbackPolicy": stats.get("fallbackPolicy"),
            "atlasUploadByteCount": stats.get("atlasUploadByteCount"),
            "atlasUploadSha256": stats.get("atlasUploadSha256"),
            "sourceCacheSha256": stats.get("sourceCacheSha256"),
            "webGpuAdapter": stats.get("webGpuAdapter"),
        },
        "routeCpu": route_cpu,
        "routeWebGpu": route_webgpu,
        "atlas": {
            "routeIdentifier": atlas.get("routeIdentifier"),
            "textureLabel": atlas.get("textureLabel"),
            "textureFormat": atlas.get("textureFormat"),
            "textureUsage": atlas.get("textureUsage"),
            "maskFormat": atlas.get("maskFormat"),
            "generation": atlas.get("generation"),
            "width": atlas.get("width"),
            "height": atlas.get("height"),
            "rowStrideBytes": atlas.get("rowStrideBytes"),
            "uploadByteCount": atlas.get("uploadByteCount"),
            "uploadSha256": atlas.get("uploadSha256"),
            "sourceCacheSha256": atlas.get("sourceCacheSha256"),
            "glyphEntryCount": atlas.get("diagnostics", {}).get("glyphEntryCount"),
            "nonEmptyGlyphCount": atlas.get("diagnostics", {}).get("nonEmptyGlyphCount"),
            "emptyGlyphCount": atlas.get("diagnostics", {}).get("emptyGlyphCount"),
            "resourceKind": atlas.get("diagnostics", {}).get("resourceKind"),
            "sampler": atlas.get("diagnostics", {}).get("sampler"),
        },
    }


def build_evidence(root: Path) -> dict[str, Any]:
    selected = selected_row_from_kan043(root)
    current = current_bundle(root)
    ownership = ownership_rows_from_kan044(root)
    visual_delta = {
        "mode": "blocked-root-cause-no-renderer-after",
        "before": {
            "phase": "selected-row-existing-evidence",
            "artifacts": dict(current["artifacts"]),
            "stats": current["stats"],
            "routeWebGpu": current["routeWebGpu"],
            "atlas": current["atlas"],
        },
        "after": {
            "phase": "not-materialized",
            "rendererChanged": False,
            "blocked": True,
            "blockedBy": ROOT_CAUSE,
            "reasonCode": BLOCKER_REASON,
            "requiredForAfter": (
                "A production WebGPU glyph atlas alpha-mask sampling draw route must be materialized "
                "with new PNG/stat/route evidence before the selected text row can become a renderer "
                "visual-delta pass."
            ),
        },
    }
    evidence = {
        "schemaVersion": 1,
        "ticket": "KAN-053",
        "packId": "kan-053-text-glyph-visual-delta",
        "status": "blocked",
        "closureDecision": "blocked-root-cause",
        "rendererChanged": False,
        "blocked": True,
        "thresholdsWeakened": False,
        "implicitSystemFontFallback": False,
        "broadShapingClaim": False,
        "lcdSdfColorFontClaim": False,
        "dynamicAtlasEvictionClaim": False,
        "coverageOwnsGlyphAtlasClaim": False,
        "selectedRow": selected,
        "current": current,
        "kan044Ownership": ownership,
        "visualDeltaEvidence": visual_delta,
        "blocker": {
            "rootCause": ROOT_CAUSE,
            "reasonCode": BLOCKER_REASON,
            "summary": (
                "The selected simple Latin text scene has existing reference/CPU/WebGPU/diff/stat/route "
                "artifacts, but its actual CPU and WebGPU draw routes are outline-path text routes. "
                "The glyph atlas route is proven as a text-owned R8/A8 upload plan and CPU mask oracle, "
                "not as a production WebGPU text draw route. Closing the visible delta requires a real "
                "glyph atlas alpha-mask sampling route with after artifacts, not a local outline-path "
                "threshold or AA tweak."
            ),
            "supportingEvidence": ["KAN-043", "KAN-044", "KAN-012"],
            "admissibleNextStep": (
                "Implement the text-owned atlas sampling draw path or explicitly keep standalone "
                "alpha-mask WebGPU unsupported via coverage.alpha-mask-unsupported; do not move glyph "
                "atlas ownership into coverage."
            ),
        },
        "artifactAudit": artifact_audit(root, CURRENT_ARTIFACTS),
        "targetDocs": [
            ".upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md",
            ".upstream/specs/skia-like-realtime/03-skia-fidelity-and-gm-promotion.md",
            ".upstream/target/high-performance-wgsl-pipeline-target.md",
            ".upstream/specs/geometry-coverage/02-lowering-rules.md",
            ".upstream/specs/geometry-coverage/adr/0006-mask-ownership-boundary.md",
        ],
        "rendererSourceAudit": [
            "kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt",
            "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuGlyphAtlas.kt",
            "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SimpleLatinLineSceneEvidence.kt",
        ],
        "validations": [
            "rtk python3 scripts/test_validate_kan053_text_glyph_visual_delta.py",
            "rtk env GRADLE_USER_HOME=/Users/chaos/.codex/worktrees/kan-053-text-glyph-visual-delta/.gradle-codex ./gradlew --no-daemon validateKan053TextGlyphVisualDelta",
        ],
    }
    validate_evidence(evidence, root)
    return evidence


def validate_evidence(evidence: dict[str, Any], root: Path) -> None:
    require(evidence.get("ticket") == "KAN-053", "ticket mismatch")
    require(evidence.get("packId") == "kan-053-text-glyph-visual-delta", "packId mismatch")
    if not evidence.get("rendererChanged"):
        require(evidence.get("blocked") is True, "rendererChanged=false requires blocked=true")
    require(evidence.get("closureDecision") == "blocked-root-cause", "closure decision mismatch")
    require(evidence.get("thresholdsWeakened") is False, "thresholds weakened")
    require(evidence.get("implicitSystemFontFallback") is False, "implicit system font fallback")
    require(evidence.get("broadShapingClaim") is False, "broad shaping claim")
    require(evidence.get("lcdSdfColorFontClaim") is False, "LCD/SDF/color-font claim")
    require(evidence.get("dynamicAtlasEvictionClaim") is False, "dynamic atlas eviction claim")
    require(evidence.get("coverageOwnsGlyphAtlasClaim") is False, "coverage-owned glyph atlas claim")

    selected = evidence.get("selectedRow")
    require(isinstance(selected, dict), "selected row missing")
    require(selected.get("rowId") == SELECTED_ROW_ID, "selected row mismatch")
    require(selected.get("sourceTicket") == "KAN-043", "selected row source mismatch")
    require(selected.get("pmCategory") == "simple-latin-support", "selected row PM category mismatch")
    require(selected.get("status") == "pass", "selected row status mismatch")
    require(selected.get("webGpuRoute") == WEBGPU_ROUTE, "selected row WebGPU route mismatch")
    require(selected.get("atlasRoute") == ATLAS_ROUTE, "selected row atlas route mismatch")
    require(selected.get("referenceKind") == REFERENCE_KIND, "selected row reference kind mismatch")
    require(selected.get("fallbackReason") == "none", "selected row fallback changed")
    font = selected.get("font")
    require(isinstance(font, dict), "font identity missing")
    require(
        bool(font.get("face")) and bool(font.get("source")) and bool(font.get("sha256")),
        "font identity missing",
    )
    require(
        font.get("sha256") == "76d04c18ea243f426b7de1f3ad208e927008f961dc5945e5aad352d0dfde8ee8",
        "font identity changed",
    )
    glyph_ids = selected.get("glyphIds")
    require(isinstance(glyph_ids, list) and len(glyph_ids) == 32, "glyph ids missing")

    current = evidence.get("current")
    require(isinstance(current, dict), "current bundle missing")
    stats = current.get("stats")
    require(isinstance(stats, dict), "current stats missing")
    require(stats.get("sceneId") == SELECTED_ROW_ID, "current stats scene mismatch")
    require(int(stats.get("tolerance")) == 8, "tolerance changed")
    require(float(stats.get("cpuSimilarityThreshold")) == 95.0, "threshold changed")
    require(float(stats.get("webGpuSimilarityThreshold")) == 95.0, "threshold changed")
    require(stats.get("globalThresholdChanged") is False, "global threshold changed")
    require(stats.get("cpuMismatchingPixels") == 581, "CPU mismatch count changed")
    require(stats.get("webGpuMismatchingPixels") == 608, "WebGPU mismatch count changed")
    require(
        stats.get("webGpuMinusCpuReferenceMismatches") == 27,
        "WebGPU minus CPU reference mismatch delta changed",
    )

    route_webgpu = current.get("routeWebGpu")
    require(isinstance(route_webgpu, dict), "current WebGPU route missing")
    require(route_webgpu.get("selectedRoute") == WEBGPU_ROUTE, "current WebGPU route changed")
    require(route_webgpu.get("atlasRouteIdentifier") == ATLAS_ROUTE, "current atlas route changed")
    require(route_webgpu.get("referenceKind") == REFERENCE_KIND, "current reference kind changed")
    require(route_webgpu.get("fallbackReason") == "none", "current fallback changed")

    atlas = current.get("atlas")
    require(isinstance(atlas, dict), "current atlas facts missing")
    require(atlas.get("routeIdentifier") == ATLAS_ROUTE, "atlas route mismatch")
    require(atlas.get("textureFormat") == "R8Unorm", "atlas texture format changed")
    require(atlas.get("maskFormat") == "A8", "atlas mask format changed")
    require(atlas.get("generation") == 1, "atlas generation changed")
    require(atlas.get("uploadByteCount") == 12928, "atlas upload byte count changed")
    require(atlas.get("glyphEntryCount") == 26, "atlas glyph entry count changed")
    require(atlas.get("nonEmptyGlyphCount") == 25, "atlas non-empty glyph count changed")

    missing = [item for item in evidence.get("artifactAudit", []) if not item.get("exists")]
    require(not missing, f"current artifacts missing: {missing}")

    ownership = evidence.get("kan044Ownership")
    require(isinstance(ownership, dict), "KAN-044 ownership evidence missing")
    atlas_row = ownership.get("atlasUploadPlan")
    require(isinstance(atlas_row, dict), "KAN-044 atlas upload row missing")
    require(atlas_row.get("status") == "pass", "KAN-044 atlas upload row status changed")
    atlas_route = atlas_row.get("route")
    require(isinstance(atlas_route, dict), "KAN-044 atlas upload route missing")
    require(atlas_route.get("webGpu") == ATLAS_ROUTE, "KAN-044 atlas WebGPU route changed")
    require(atlas_route.get("coverage") == "not-owned-by-coverage", "atlas ownership moved to coverage")
    alpha_refusal = ownership.get("standaloneAlphaMaskRefusal")
    require(isinstance(alpha_refusal, dict), "KAN-044 standalone alpha-mask refusal missing")
    require(alpha_refusal.get("status") == "expected-unsupported", "standalone alpha-mask support changed")
    require(
        alpha_refusal.get("reasonCode") == "coverage.alpha-mask-unsupported",
        "standalone alpha-mask refusal reason changed",
    )
    require(alpha_refusal.get("coverageOwnsAtlas") is False, "coverage atlas ownership changed")

    visual_delta = evidence.get("visualDeltaEvidence")
    require(isinstance(visual_delta, dict), "visual delta evidence missing")
    before = visual_delta.get("before")
    after = visual_delta.get("after")
    require(isinstance(before, dict), "visual delta before evidence missing")
    require(isinstance(after, dict), "visual delta after evidence missing")
    require(before.get("phase") == "selected-row-existing-evidence", "visual delta before phase mismatch")
    before_artifacts = before.get("artifacts")
    require(isinstance(before_artifacts, dict), "visual delta before artifacts missing")
    before_missing = [item for item in artifact_audit(root, before_artifacts) if not item.get("exists")]
    require(not before_missing, f"visual delta before artifacts missing: {before_missing}")
    if not evidence.get("rendererChanged"):
        require(after.get("phase") == "not-materialized", "blocked renderer after artifacts must not be materialized")
        require(after.get("blocked") is True, "blocked renderer after evidence must stay blocked")
        require(after.get("blockedBy") == ROOT_CAUSE, "blocked renderer after root cause mismatch")

    blocker = evidence.get("blocker")
    require(isinstance(blocker, dict), "blocker missing")
    require(bool(blocker.get("rootCause")), "missing root cause")
    require(blocker.get("rootCause") == ROOT_CAUSE, "root cause mismatch")
    require(
        blocker.get("reasonCode") == BLOCKER_REASON,
        "atlas sampling route requirement must stay visible",
    )
    support = blocker.get("supportingEvidence")
    require(isinstance(support, list), "blocker supporting evidence missing")
    for required in ("KAN-043", "KAN-044", "KAN-012"):
        require(required in support, f"missing blocker supporting evidence: {required}")


def markdown_report(evidence: dict[str, Any]) -> str:
    stats = evidence["current"]["stats"]
    atlas = evidence["current"]["atlas"]
    blocker = evidence["blocker"]
    row = evidence["selectedRow"]
    alpha_refusal = evidence["kan044Ownership"]["standaloneAlphaMaskRefusal"]
    return f"""# KAN-053 Text Glyph Visual Delta

KAN-053 relit `{row["rowId"]}` after KAN-043/KAN-044 and closes this slice as `blocked=true`, not as a renderer visual fix.

## Decision

| Field | Value |
|---|---|
| rendererChanged | `{evidence["rendererChanged"]}` |
| blocked | `{evidence["blocked"]}` |
| closureDecision | `{evidence["closureDecision"]}` |
| rootCause | `{blocker["rootCause"]}` |
| reasonCode | `{blocker["reasonCode"]}` |

## Selected Row

| Field | Value |
|---|---|
| row | `{row["rowId"]}` |
| source | `{row["sourceTicket"]}` |
| font | `{row["font"]["face"]}` |
| font sha256 | `{row["font"]["sha256"]}` |
| WebGPU route | `{row["webGpuRoute"]}` |
| atlas route | `{row["atlasRoute"]}` |
| referenceKind | `{row["referenceKind"]}` |
| fallbackReason | `{row["fallbackReason"]}` |

## Current Evidence

| Metric | Value |
|---|---:|
| CPU mismatching pixels vs atlas reference | `{stats["cpuMismatchingPixels"]}` |
| WebGPU mismatching pixels vs atlas reference | `{stats["webGpuMismatchingPixels"]}` |
| WebGPU minus CPU reference mismatches | `{stats["webGpuMinusCpuReferenceMismatches"]}` |
| tolerance | `{stats["tolerance"]}` |
| similarity threshold | `{stats["webGpuSimilarityThreshold"]}` |
| atlas upload bytes | `{atlas["uploadByteCount"]}` |
| atlas glyph entries | `{atlas["glyphEntryCount"]}` |

Current PNG/stat/route artifacts remain under `reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/`.

## Before/After Status

- before: existing KAN-012 reference/CPU/WebGPU/diff/stat/route artifacts are reused as the selected-row evidence.
- after: renderer artifacts are not materialized because `rendererChanged=false` and the slice is blocked by `{blocker["rootCause"]}`.

## Blocker Evidence

- KAN-043 proves the text row and font identity, but the draw route is `{row["webGpuRoute"]}`.
- KAN-044 proves the text-owned `{row["atlasRoute"]}` upload plan and CPU mask oracle, not a production atlas sampling draw path.
- KAN-044 keeps standalone WebGPU alpha mask `{alpha_refusal["status"]}` via `{alpha_refusal["reasonCode"]}`.

No implicit system font fallback, broad shaping, LCD/SDF/color-font, dynamic atlas eviction, threshold weakening, or coverage-owned atlas claim is made.
"""


def write_outputs(root: Path, output_dir: Path) -> dict[str, Any]:
    evidence = build_evidence(root)
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
    print(f"KAN-053 text glyph visual delta evidence written to {output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
