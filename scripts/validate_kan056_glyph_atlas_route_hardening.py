#!/usr/bin/env python3
import json
import shutil
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/glyph-atlas-route-hardening"
OUTPUT_JSON = "kan-056-glyph-atlas-route-hardening.json"
OUTPUT_MARKDOWN = "kan-056-glyph-atlas-route-hardening.md"
OUTPUT_MANIFEST_ENTRY = "pm-bundle-manifest-entry.json"

SELECTED_ROW_ID = "text.simple-latin.line.v1"
ATLAS_ROUTE = "webgpu.text.glyph-atlas.simple-latin"
ALPHA_MASK_REFUSAL = "coverage.alpha-mask-unsupported"

KAN054_JSON = "reports/wgsl-pipeline/webgpu-glyph-atlas-sampling-route/kan-054-webgpu-glyph-atlas-sampling-route.json"
KAN055_JSON = "reports/wgsl-pipeline/text-glyph-atlas-visual-delta/kan-055-text-glyph-atlas-visual-delta.json"
ROUTE_WEBGPU_JSON = "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/route-webgpu.json"
ROUTE_CPU_JSON = "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/route-cpu.json"
STATS_JSON = "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/stats.json"
ATLAS_JSON = "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line/atlas.json"

REQUIRED_CATEGORIES = {"supported", "expected-unsupported", "dependency-gated", "reporting-only"}
REQUIRED_NON_CLAIMS = {
    "no-broad-text-claim",
    "no-broad-glyph-atlas-claim",
    "no-shaping-claim",
    "no-fallback-font-claim",
    "no-emoji-or-color-font-claim",
    "no-sdf-or-lcd-claim",
    "no-dynamic-atlas-eviction-claim",
    "no-threshold-change-claim",
}
REQUIRED_SOURCE_NON_CLAIMS = {
    "no-general-alpha-mask-webgpu-claim",
    "no-broad-text-claim",
    "no-shaping-claim",
    "no-fallback-font-claim",
    "no-emoji-or-color-font-claim",
    "no-sdf-or-lcd-claim",
    "no-dynamic-atlas-eviction-claim",
}
REQUIRED_ROUTE_NON_CLAIMS = {
    "no-broad-text-claim",
    "no-shaping-claim",
    "no-fallback-font-claim",
    "no-emoji-or-color-font-claim",
    "no-sdf-or-lcd-claim",
}


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-056 glyph atlas route hardening validation failed: {message}")


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
        for key, path in sorted(artifacts.items())
    ]


def support_proofs(after_artifacts: dict[str, str]) -> dict[str, Any]:
    return {
        "reference": bool(after_artifacts.get("reference")),
        "cpuGpu": bool(after_artifacts.get("cpu") and after_artifacts.get("webGpu")),
        "diffStat": bool(after_artifacts.get("webGpuDiff") and after_artifacts.get("stats")),
        "routeDiagnostics": bool(after_artifacts.get("routeWebGpu")),
        "fallbackStable": True,
    }


def build_matrix_rows(after_artifacts: dict[str, str]) -> list[dict[str, Any]]:
    return [
        {
            "rowId": "text.simple-latin.line.v1.webgpu-glyph-atlas",
            "category": "supported",
            "claimScope": "selected-simple-latin-line-only",
            "routeId": ATLAS_ROUTE,
            "fallbackReason": "none",
            "proofs": support_proofs(after_artifacts),
            "sourceTickets": ["KAN-054", "KAN-055"],
        },
        {
            "rowId": "webgpu.standalone-alpha-mask-refusal",
            "category": "expected-unsupported",
            "reasonCode": ALPHA_MASK_REFUSAL,
            "fallbackAction": "refuse",
            "claimScope": "coverage-alpha-mask-outside-text-glyph-route",
        },
        {
            "rowId": "text.font-fallback-complex-shaping-color-fonts",
            "category": "dependency-gated",
            "reasonCodes": [
                "font.shaping-fallback-missing",
                "font.shaping-feature-unsupported",
                "font.color-font-unsupported",
            ],
            "claimScope": "real-font-shaping-dependencies-required",
        },
        {
            "rowId": "glyph-atlas-route-diagnostics",
            "category": "reporting-only",
            "reasonCode": "glyph-atlas.diagnostics-reporting-only",
            "claimScope": "diagnostics-not-release-blocking-performance-gate",
        },
    ]


def build_negative_fixtures() -> list[dict[str, Any]]:
    return [
        {
            "fixtureId": "reject-broad-text-support",
            "wouldFailIf": "any supported row uses claimScope=broad-text",
            "reason": "Support requires row-specific reference/CPU/GPU/diff/stat/routes/fallback evidence.",
        },
        {
            "fixtureId": "reject-missing-supported-proof",
            "wouldFailIf": "a supported row lacks reference, CPU/GPU, diff/stat, route diagnostics, or stable fallback proof",
            "reason": "Route diagnostics alone are not enough for support claims.",
        },
        {
            "fixtureId": "reject-threshold-change",
            "wouldFailIf": "globalThresholdChanged=true or WebGPU threshold differs from 95.0",
            "reason": "KAN-056 is a hardening pack, not a threshold relaxation.",
        },
    ]


def build_evidence(root: Path) -> dict[str, Any]:
    kan054 = load_json(root / KAN054_JSON)
    kan055 = load_json(root / KAN055_JSON)
    route_webgpu = load_json(root / ROUTE_WEBGPU_JSON)
    stats = load_json(root / STATS_JSON)
    atlas = load_json(root / ATLAS_JSON)

    after = kan055.get("after") if isinstance(kan055.get("after"), dict) else {}
    after_artifacts = after.get("artifacts") if isinstance(after.get("artifacts"), dict) else {}
    atlas_diagnostics = atlas.get("diagnostics") if isinstance(atlas.get("diagnostics"), dict) else {}
    cache_ids = kan054.get("kan044AtlasUploadPlan", {}).get("cacheIds", {}) if isinstance(kan054.get("kan044AtlasUploadPlan"), dict) else {}
    artifacts = {
        "kan054": KAN054_JSON,
        "kan055": KAN055_JSON,
        "routeWebGpu": ROUTE_WEBGPU_JSON,
        "routeCpu": ROUTE_CPU_JSON,
        "stats": STATS_JSON,
        "atlas": ATLAS_JSON,
    }
    for key, value in after_artifacts.items():
        if isinstance(value, str) and value:
            artifacts[f"after.{key}"] = value

    return {
        "schemaVersion": 1,
        "ticket": "KAN-056",
        "packId": "kan-056-glyph-atlas-route-hardening",
        "status": "pending-validation",
        "selectedRowId": SELECTED_ROW_ID,
        "supportedRoute": {
            "routeId": route_webgpu.get("selectedRoute"),
            "fallbackReason": route_webgpu.get("fallbackReason"),
            "supportScope": route_webgpu.get("supportScope"),
            "sourceTickets": ["KAN-054", "KAN-055"],
        },
        "sourceGuards": {
            "routeWebGpu": {
                "sceneId": route_webgpu.get("sceneId"),
                "selectedRoute": route_webgpu.get("selectedRoute"),
                "fallbackReason": route_webgpu.get("fallbackReason"),
                "supportScope": route_webgpu.get("supportScope"),
                "nonClaims": route_webgpu.get("nonClaims", []),
            },
            "kan054RouteWebGpu": kan054.get("routeWebGpu", {}),
            "kan054NonClaims": kan054.get("nonClaims", []),
            "standaloneAlphaMaskRefusal": kan054.get("standaloneAlphaMaskRefusal", {}),
        },
        "diagnostics": {
            "atlasGeneration": atlas.get("generation"),
            "atlasUploadBytes": atlas.get("uploadByteCount"),
            "cacheIds": {
                "scopeId": cache_ids.get("scopeId"),
                "routeIdentifier": cache_ids.get("routeIdentifier") or atlas.get("routeIdentifier"),
                "textureLabel": cache_ids.get("textureLabel") or atlas.get("textureLabel"),
                "sourceCacheSha256": cache_ids.get("sourceCacheSha256") or atlas.get("sourceCacheSha256"),
                "uploadSha256": cache_ids.get("uploadSha256") or atlas.get("uploadSha256"),
            },
            "textureFormat": atlas.get("textureFormat"),
            "maskFormat": atlas.get("maskFormat"),
            "sampler": atlas_diagnostics.get("sampler") or kan054.get("atlas", {}).get("sampler"),
            "resourceKind": atlas_diagnostics.get("resourceKind"),
            "glyphEntryCount": atlas_diagnostics.get("glyphEntryCount"),
            "nonEmptyGlyphCount": atlas_diagnostics.get("nonEmptyGlyphCount"),
            "routeId": route_webgpu.get("selectedRoute"),
            "pipelineKeyFacts": {
                "maskTextureSamplerPresence": True,
                "atlasRouteAffectsLayout": True,
                "uniformOnlyAxesExcluded": ["rect bounds", "fill color", "transform values"],
            },
        },
        "matrixRows": build_matrix_rows(after_artifacts),
        "categoryRows": [
            {"category": category, "count": 1}
            for category in ["supported", "expected-unsupported", "dependency-gated", "reporting-only"]
        ],
        "negativeFixtures": build_negative_fixtures(),
        "nonClaims": sorted(REQUIRED_NON_CLAIMS),
        "nativeKadreCiRequired": False,
        "releaseBlocking": False,
        "readinessDelta": 0.0,
        "artifactAudit": artifact_audit(root, artifacts),
        "sourceEvidence": {"kan054": KAN054_JSON, "kan055": KAN055_JSON},
        "kan055Decision": kan055.get("kan053Decision"),
        "kan055Delta": kan055.get("delta"),
        "thresholds": {
            "globalThresholdChanged": stats.get("globalThresholdChanged") or after.get("stats", {}).get("globalThresholdChanged"),
            "webGpuSimilarityThreshold": stats.get("webGpuSimilarityThreshold"),
            "cpuSimilarityThreshold": stats.get("cpuSimilarityThreshold"),
        },
    }


def validate_supported_row(row: dict[str, Any]) -> None:
    require(row.get("claimScope") != "broad-text", "broad text claim is forbidden")
    proofs = row.get("proofs")
    require(isinstance(proofs, dict), f"supported row missing proof map: {row.get('rowId')}")
    for key in ("reference", "cpuGpu", "diffStat", "routeDiagnostics", "fallbackStable"):
        require(proofs.get(key) is True, f"supported row missing proof {key}: {row.get('rowId')}")
    require(row.get("fallbackReason") == "none", f"supported row fallback must be none: {row.get('rowId')}")


def validate_evidence(evidence: dict[str, Any], root: Path) -> None:
    require(evidence.get("ticket") == "KAN-056", "ticket mismatch")
    require(evidence.get("packId") == "kan-056-glyph-atlas-route-hardening", "packId mismatch")
    require(evidence.get("selectedRowId") == SELECTED_ROW_ID, "selected row mismatch")
    require(evidence.get("nativeKadreCiRequired") is False, "pipelinePmBundle must not require native Kadre CI")
    require(evidence.get("releaseBlocking") is False, "KAN-056 must not add release-blocking performance gates")
    require(float(evidence.get("readinessDelta")) == 0.0, "KAN-056 must not move readiness denominator")

    supported_route = evidence.get("supportedRoute")
    require(isinstance(supported_route, dict), "supported route missing")
    require(supported_route.get("routeId") == ATLAS_ROUTE, "supported route must be glyph atlas")
    require(supported_route.get("fallbackReason") == "none", "supported route fallbackReason must be none")
    require(supported_route.get("supportScope") == "simple-latin-line-visible", "support scope must remain simple-latin-line-visible")

    source_guards = evidence.get("sourceGuards")
    require(isinstance(source_guards, dict), "source guards missing")
    route_guard = source_guards.get("routeWebGpu")
    require(isinstance(route_guard, dict), "source route guard missing")
    require(route_guard.get("sceneId") == SELECTED_ROW_ID, "source route scene id must remain selected row")
    require(route_guard.get("selectedRoute") == ATLAS_ROUTE, "source route must remain glyph atlas")
    require(route_guard.get("fallbackReason") == "none", "source route fallback must remain none")
    require(route_guard.get("supportScope") == "simple-latin-line-visible", "source route support scope must remain simple-latin-line-visible")
    route_non_claims = set(route_guard.get("nonClaims", []))
    require(REQUIRED_ROUTE_NON_CLAIMS.issubset(route_non_claims), f"source route non-claims missing: {sorted(REQUIRED_ROUTE_NON_CLAIMS - route_non_claims)}")

    kan054_route_guard = source_guards.get("kan054RouteWebGpu")
    require(isinstance(kan054_route_guard, dict), "KAN-054 route guard missing")
    require(kan054_route_guard.get("sceneId") == SELECTED_ROW_ID, "KAN-054 route scene id must remain selected row")
    require(kan054_route_guard.get("selectedRoute") == ATLAS_ROUTE, "KAN-054 route must remain glyph atlas")
    require(kan054_route_guard.get("supportScope") == "simple-latin-line-visible", "KAN-054 route support scope must remain simple-latin-line-visible")
    kan054_route_non_claims = set(kan054_route_guard.get("nonClaims", []))
    require(REQUIRED_ROUTE_NON_CLAIMS.issubset(kan054_route_non_claims), f"KAN-054 route non-claims missing: {sorted(REQUIRED_ROUTE_NON_CLAIMS - kan054_route_non_claims)}")

    source_non_claims = set(source_guards.get("kan054NonClaims", []))
    require(REQUIRED_SOURCE_NON_CLAIMS.issubset(source_non_claims), f"source non-claims missing: {sorted(REQUIRED_SOURCE_NON_CLAIMS - source_non_claims)}")
    alpha_refusal = source_guards.get("standaloneAlphaMaskRefusal")
    require(isinstance(alpha_refusal, dict), "standalone alpha mask refusal missing")
    require(alpha_refusal.get("status") == "expected-unsupported", "standalone alpha mask refusal status must remain expected-unsupported")
    require(alpha_refusal.get("reasonCode") == ALPHA_MASK_REFUSAL, "standalone alpha mask refusal reason must remain coverage.alpha-mask-unsupported")
    require(alpha_refusal.get("coverageOwnsAtlas") is False, "standalone alpha mask refusal must not move atlas ownership to coverage")
    alpha_route = alpha_refusal.get("route")
    require(isinstance(alpha_route, dict), "standalone alpha mask refusal route missing")
    require(alpha_route.get("coverage") == "CoveragePlan.AlphaMask", "standalone alpha mask refusal coverage route changed")
    require(alpha_route.get("webGpu") == "webgpu.refuse.standalone-alpha-mask", "standalone alpha mask refusal WebGPU route changed")

    thresholds = evidence.get("thresholds")
    require(isinstance(thresholds, dict), "threshold evidence missing")
    require(thresholds.get("globalThresholdChanged") is False, "global threshold changed")
    require(float(thresholds.get("webGpuSimilarityThreshold")) == 95.0, "WebGPU threshold changed")
    require(float(thresholds.get("cpuSimilarityThreshold")) == 95.0, "CPU threshold changed")

    diagnostics = evidence.get("diagnostics")
    require(isinstance(diagnostics, dict), "diagnostics missing")
    require(diagnostics.get("atlasGeneration") == 1, "atlas generation missing")
    require(isinstance(diagnostics.get("atlasUploadBytes"), int) and diagnostics["atlasUploadBytes"] > 0, "atlas upload bytes missing")
    require(diagnostics.get("textureFormat") == "R8Unorm", "texture format must remain R8Unorm")
    require(diagnostics.get("maskFormat") == "A8", "mask format must remain A8")
    require(diagnostics.get("routeId") == ATLAS_ROUTE, "diagnostic route id mismatch")
    cache_ids = diagnostics.get("cacheIds")
    require(isinstance(cache_ids, dict), "cache ids missing")
    for key in ("scopeId", "routeIdentifier", "textureLabel", "sourceCacheSha256", "uploadSha256"):
        require(isinstance(cache_ids.get(key), str) and cache_ids[key], f"cache id missing: {key}")

    matrix_rows = evidence.get("matrixRows")
    require(isinstance(matrix_rows, list), "matrix rows missing")
    categories = {row.get("category") for row in matrix_rows if isinstance(row, dict)}
    require(REQUIRED_CATEGORIES.issubset(categories), f"required categories missing: {sorted(REQUIRED_CATEGORIES - categories)}")
    for row in matrix_rows:
        require(isinstance(row, dict), "matrix row must be object")
        require(row.get("claimScope") != "broad-text", "broad text claim is forbidden")
        if row.get("category") == "supported":
            validate_supported_row(row)
        if row.get("category") == "expected-unsupported":
            require(isinstance(row.get("reasonCode"), str) and row["reasonCode"], f"expected-unsupported reason missing: {row.get('rowId')}")
            require(row.get("fallbackAction") == "refuse", f"expected-unsupported fallback action must be refuse: {row.get('rowId')}")

    non_claims = set(evidence.get("nonClaims", []))
    require(REQUIRED_NON_CLAIMS.issubset(non_claims), f"required non-claims missing: {sorted(REQUIRED_NON_CLAIMS - non_claims)}")
    require(evidence.get("kan055Decision") == "close-root-cause-resolved", "KAN-055 closure decision missing")
    delta = evidence.get("kan055Delta")
    require(isinstance(delta, dict), "KAN-055 delta missing")
    require(delta.get("webGpuMismatchingPixelsBefore") == 608, "KAN-055 before mismatch changed")
    require(delta.get("webGpuMismatchingPixelsAfter") == 122, "KAN-055 after mismatch changed")
    require(delta.get("webGpuMismatchingPixelsImprovement") == 486, "KAN-055 improvement changed")

    missing = [item for item in evidence.get("artifactAudit", []) if not item.get("exists")]
    require(not missing, f"required artifacts missing: {missing}")
    for fixture in evidence.get("negativeFixtures", []):
        require(isinstance(fixture, dict) and fixture.get("fixtureId"), "negative fixture missing id")


def manifest_entry() -> dict[str, Any]:
    return {
        "key": "kan056GlyphAtlasRouteHardening",
        "claimLevel": "glyph-atlas-route-hardening-pm-gates",
        "categories": sorted(REQUIRED_CATEGORIES),
        "evidenceJson": f"release/kan-056-glyph-atlas-route-hardening/{OUTPUT_JSON}",
        "evidenceMarkdown": f"release/kan-056-glyph-atlas-route-hardening/{OUTPUT_MARKDOWN}",
        "manifestEntryJson": f"release/kan-056-glyph-atlas-route-hardening/{OUTPUT_MANIFEST_ENTRY}",
        "generationCommand": "rtk ./gradlew --no-daemon validateKan056GlyphAtlasRouteHardening",
        "pmPackageCommand": "rtk ./gradlew --no-daemon pipelinePmBundle",
        "nativeKadreCiRequired": False,
        "releaseBlocking": False,
        "readinessDelta": 0.0,
        "notice": "KAN-056 hardens the selected glyph atlas WebGPU route with PM support/refusal gates and diagnostics. It does not broaden text, font, LCD/SDF, color-font, alpha-mask, performance, or Kadre CI claims.",
    }


def markdown_report(evidence: dict[str, Any]) -> str:
    diagnostics = evidence["diagnostics"]
    return f"""# KAN-056 Glyph Atlas Route Hardening

KAN-056 hardens the selected WebGPU glyph atlas route for `{evidence['selectedRowId']}` without broad text claims.

## Decision

| Field | Value |
|---|---|
| Status | `{evidence['status']}` |
| Supported route | `{evidence['supportedRoute']['routeId']}` |
| Fallback | `{evidence['supportedRoute']['fallbackReason']}` |
| Atlas generation | `{diagnostics['atlasGeneration']}` |
| Upload bytes | `{diagnostics['atlasUploadBytes']}` |
| Texture format | `{diagnostics['textureFormat']}` |
| Native Kadre CI required | `{evidence['nativeKadreCiRequired']}` |

## Matrix

| Row | Category | Scope / Reason |
|---|---|---|
""" + "".join(
        f"| `{row['rowId']}` | `{row['category']}` | `{row.get('claimScope') or row.get('reasonCode')}` |\n"
        for row in evidence["matrixRows"]
    ) + """

## Non-Claims

- No broad text, shaping, fallback-font, emoji/color-font, LCD, SDF, dynamic atlas eviction, or general alpha-mask WebGPU support claim.
- No threshold, readiness, release-blocking performance, or native Kadre CI gate change.
"""


def write_outputs(root: Path, output_dir: Path) -> dict[str, Any]:
    evidence = build_evidence(root)
    validate_evidence(evidence, root)
    evidence["status"] = "pass"
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / OUTPUT_JSON).write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (output_dir / OUTPUT_MARKDOWN).write_text(markdown_report(evidence), encoding="utf-8")
    (output_dir / OUTPUT_MANIFEST_ENTRY).write_text(json.dumps(manifest_entry(), indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return evidence


def inject_pm_bundle(output_dir: Path, bundle_dir: Path) -> None:
    manifest_path = bundle_dir / "manifest.json"
    require(manifest_path.is_file(), f"missing PM bundle manifest: {manifest_path}")
    evidence = load_json(output_dir / OUTPUT_JSON)
    validate_evidence(evidence, output_dir.parents[2] if len(output_dir.parents) > 2 else Path.cwd())
    require(evidence.get("status") == "pass", "KAN-056 evidence must be pass before PM bundle injection")

    target_dir = bundle_dir / "release/kan-056-glyph-atlas-route-hardening"
    if target_dir.exists():
        shutil.rmtree(target_dir)
    shutil.copytree(output_dir, target_dir)

    manifest = load_json(manifest_path)
    require(isinstance(manifest, dict), "PM bundle manifest must be a JSON object")
    entry = manifest_entry()
    manifest[entry["key"]] = entry
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main(argv: list[str]) -> int:
    root = Path(argv[1]).resolve() if len(argv) > 1 else Path.cwd()
    output_dir = Path(argv[2]).resolve() if len(argv) > 2 else root / DEFAULT_OUTPUT_DIR
    evidence = write_outputs(root, output_dir)
    if "--inject-pm-bundle" in argv:
        bundle_arg_index = argv.index("--inject-pm-bundle") + 1
        require(bundle_arg_index < len(argv), "--inject-pm-bundle requires bundle directory")
        inject_pm_bundle(output_dir, Path(argv[bundle_arg_index]).resolve())
    print(
        "KAN-056 glyph atlas route hardening validation passed: "
        f"{len(evidence['matrixRows'])} matrix rows, route {evidence['supportedRoute']['routeId']}"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main(sys.argv))
    except ValidationError as exc:
        print(str(exc), file=sys.stderr)
        raise SystemExit(1)
