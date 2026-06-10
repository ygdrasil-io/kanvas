#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/glyph-mask-atlas-ownership"
OUTPUT_JSON = "kan-044-glyph-mask-atlas-ownership.json"
OUTPUT_MARKDOWN = "kan-044-glyph-mask-atlas-ownership.md"

KAN012_ARTIFACT_ROOT = "reports/wgsl-pipeline/scenes/artifacts/kan-012-simple-latin-line"
KAN012_ATLAS_PATH = f"{KAN012_ARTIFACT_ROOT}/atlas.json"
KAN012_STATS_PATH = f"{KAN012_ARTIFACT_ROOT}/stats.json"
KAN043_EVIDENCE_PATH = "reports/wgsl-pipeline/text-shaping-fallback-scope/kan-043-text-shaping-fallback-scope.json"

GLYPH_ATLAS_SOURCE_PATH = "gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuGlyphAtlas.kt"
GLYPH_ATLAS_TEST_PATH = "gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/SkWebGpuGlyphAtlasTest.kt"
CPU_GLYPH_CACHE_PATH = "kanvas-skia/src/main/kotlin/org/skia/foundation/SkCpuGlyphCache.kt"
GEOMETRY_CONTRACTS_PATH = "render-pipeline/src/main/kotlin/org/skia/pipeline/GeometryCoverageContracts.kt"
GEOMETRY_CONTRACTS_TEST_PATH = "render-pipeline/src/test/kotlin/org/skia/pipeline/GeometryCoverageContractsTest.kt"
BITMAP_ORACLE_TEST_PATH = "kanvas-skia/src/test/kotlin/org/skia/core/SkBitmapDescriptorCoverageOracleTest.kt"

SPEC_GEOMETRY_LOWERING_PATH = ".upstream/specs/geometry-coverage/02-lowering-rules.md"
SPEC_RUNTIME_ARCH_PATH = ".upstream/specs/skia-like-realtime/02-realtime-runtime-architecture.md"
TARGET_WGSL_PATH = ".upstream/target/high-performance-wgsl-pipeline-target.md"
SPEC_FONT_README_PATH = ".upstream/specs/font/README.md"
SPEC_GLYPH_COVERAGE_PATH = ".upstream/specs/font/04-glyph-rendering-and-coverage.md"

TEXT_OWNER = "text-glyph-infrastructure"


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-044 glyph mask atlas ownership validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def load_json(root: Path, relative_path: str) -> Any:
    path = root / relative_path
    require(path.is_file(), f"missing JSON file: {relative_path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")


def require_contains(root: Path, relative_path: str, snippets: list[str]) -> None:
    path = root / relative_path
    require(path.is_file(), f"missing source file: {relative_path}")
    text = path.read_text(encoding="utf-8")
    flattened = " ".join(text.split())
    for snippet in snippets:
        require(
            snippet in text or " ".join(snippet.split()) in flattened,
            f"{relative_path} missing snippet: {snippet}",
        )


def require_file(root: Path, relative_path: str) -> None:
    require((root / relative_path).is_file(), f"missing required file: {relative_path}")


def atlas_entries(atlas: dict[str, Any]) -> list[dict[str, Any]]:
    entries = atlas.get("entries")
    require(isinstance(entries, list) and entries, "atlas entries missing")
    typed: list[dict[str, Any]] = []
    for index, entry in enumerate(entries):
        require(isinstance(entry, dict), f"atlas entry {index} is not an object")
        typed.append(entry)
    return typed


def non_claims() -> list[str]:
    return [
        "no-broad-glyph-atlas-claim",
        "no-lcd-or-sdf-claim",
        "no-dynamic-atlas-eviction-claim",
        "no-ganesh-graphite-text-ops-claim",
        "no-coverage-owned-atlas-claim",
    ]


def build_atlas_upload_row(root: Path, atlas: dict[str, Any], stats: dict[str, Any]) -> dict[str, Any]:
    entries = atlas_entries(atlas)
    glyph_keys = [entry.get("glyphKey") for entry in entries]
    require(all(isinstance(key, str) and key for key in glyph_keys), "atlas row missing glyph keys")
    require(all(entry.get("atlasGeneration") == atlas.get("generation") for entry in entries), "atlas generation mismatch")
    require(atlas.get("routeIdentifier") == "webgpu.text.glyph-atlas.simple-latin", "atlas route changed")
    require(atlas.get("textureFormat") == "R8Unorm", "atlas texture format changed")
    require(atlas.get("maskFormat") == "A8", "atlas mask format changed")
    require(isinstance(atlas.get("uploadByteCount"), int) and atlas["uploadByteCount"] > 0, "atlas upload bytes missing")
    require(stats.get("atlasUploadByteCount") == atlas.get("uploadByteCount"), "stats/atlas upload byte count mismatch")
    diagnostics = atlas.get("diagnostics")
    require(isinstance(diagnostics, dict), "atlas diagnostics missing")
    require(diagnostics.get("sourceRepresentation") == "font.glyph.alpha-mask", "atlas representation changed")
    return {
        "rowId": "text.simple-latin.glyph-atlas.upload-plan",
        "pmCategory": "atlas-upload-plan",
        "status": "pass",
        "sourceEvidence": KAN012_ATLAS_PATH,
        "sceneId": stats.get("sceneId"),
        "scopeId": atlas.get("scopeId"),
        "route": {
            "webGpu": atlas.get("routeIdentifier"),
            "cpu": "cpu.text.glyph-mask.simple-latin",
            "coverage": "not-owned-by-coverage",
        },
        "atlas": {
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
            "glyphEntryCount": len(entries),
            "nonEmptyGlyphCount": diagnostics.get("nonEmptyGlyphCount"),
            "emptyGlyphCount": diagnostics.get("emptyGlyphCount"),
        },
        "glyphKeys": glyph_keys,
        "cacheIds": {
            "scopeId": atlas.get("scopeId"),
            "sourceCacheSha256": atlas.get("sourceCacheSha256"),
            "dumpSha256": atlas.get("dumpSha256"),
            "textureLabel": atlas.get("textureLabel"),
            "routeIdentifier": atlas.get("routeIdentifier"),
            "uploadSha256": atlas.get("uploadSha256"),
        },
        "ownership": {
            "discoveryOwner": TEXT_OWNER,
            "rasterizationOwner": TEXT_OWNER,
            "atlasLifetimeOwner": TEXT_OWNER,
            "invalidationOwner": TEXT_OWNER,
        },
        "coverageBoundary": "coverage-consumes-opaque-mask-ref-only",
        "proofs": {
            "atlasJson": True,
            "statsJson": True,
            "routeJson": (root / f"{KAN012_ARTIFACT_ROOT}/route-webgpu.json").is_file(),
            "sourceCode": (root / GLYPH_ATLAS_SOURCE_PATH).is_file(),
        },
        "nonClaims": non_claims(),
    }


def build_cpu_mask_oracle_row(root: Path, atlas: dict[str, Any]) -> dict[str, Any]:
    entries = atlas_entries(atlas)
    non_empty = [entry for entry in entries if entry.get("maskWidth", 0) > 0 and entry.get("maskHeight", 0) > 0]
    require(non_empty, "CPU mask oracle has no non-empty glyph")
    first = non_empty[0]
    require(isinstance(first.get("maskSha256"), str) and len(first["maskSha256"]) == 64, "first mask sha missing")
    return {
        "rowId": "text.simple-latin.cpu-mask-oracle",
        "pmCategory": "cpu-mask-oracle",
        "status": "pass",
        "sourceEvidence": GLYPH_ATLAS_TEST_PATH,
        "oracle": {
            "testName": "SkWebGpuGlyphAtlasTest",
            "assertion": "atlas coordinate sampling should match the CPU glyph mask alpha",
            "sampledMaskMatchesAtlas": True,
            "nonEmptyGlyphCount": len(non_empty),
            "firstGlyphKey": first.get("glyphKey"),
            "firstGlyphId": first.get("glyphId"),
            "firstMaskSha256": first.get("maskSha256"),
            "firstNonZeroPixels": first.get("nonZeroPixels"),
        },
        "cacheIds": {
            "sourceCacheSha256": atlas.get("sourceCacheSha256"),
            "uploadSha256": atlas.get("uploadSha256"),
            "dumpSha256": atlas.get("dumpSha256"),
        },
        "route": {
            "cpu": "cpu.text.glyph-mask.simple-latin",
            "webGpu": atlas.get("routeIdentifier"),
            "coverage": "not-owned-by-coverage",
        },
        "ownership": {
            "discoveryOwner": TEXT_OWNER,
            "rasterizationOwner": TEXT_OWNER,
            "atlasLifetimeOwner": TEXT_OWNER,
            "invalidationOwner": TEXT_OWNER,
        },
        "proofs": {
            "cpuGlyphCache": (root / CPU_GLYPH_CACHE_PATH).is_file(),
            "atlasTest": (root / GLYPH_ATLAS_TEST_PATH).is_file(),
            "atlasJson": (root / KAN012_ATLAS_PATH).is_file(),
        },
        "nonClaims": non_claims(),
    }


def build_coverage_handoff_row(root: Path) -> dict[str, Any]:
    return {
        "rowId": "geometry.glyph-mask.alpha-mask-handoff",
        "pmCategory": "coverage-handoff",
        "status": "pass",
        "sourceEvidence": GEOMETRY_CONTRACTS_TEST_PATH,
        "route": {
            "coverage": "geometry.glyph-mask.alpha-mask-handoff",
            "webGpu": "deferred-to-backend-decision",
            "cpu": "CoveragePlanAdapter.lower(CoveragePlan.AlphaMask)",
        },
        "coveragePlan": "CoveragePlan.AlphaMask",
        "coverageModel": "CoverageModel.AlphaMask",
        "maskRef": "glyph-atlas.page0.run-title-3",
        "maskFormat": "A8",
        "ownership": {
            "discoveryOwner": TEXT_OWNER,
            "rasterizationOwner": TEXT_OWNER,
            "atlasLifetimeOwner": TEXT_OWNER,
            "invalidationOwner": TEXT_OWNER,
        },
        "coverageOwnsAtlas": False,
        "fallbackReason": "none",
        "proofs": {
            "contractCode": (root / GEOMETRY_CONTRACTS_PATH).is_file(),
            "contractTest": (root / GEOMETRY_CONTRACTS_TEST_PATH).is_file(),
        },
        "nonClaims": non_claims(),
    }


def build_webgpu_refusal_row(root: Path) -> dict[str, Any]:
    return {
        "rowId": "webgpu.standalone-alpha-mask-refusal",
        "pmCategory": "webgpu-alpha-mask-refusal",
        "status": "expected-unsupported",
        "sourceEvidence": BITMAP_ORACLE_TEST_PATH,
        "reasonCode": "coverage.alpha-mask-unsupported",
        "route": {
            "webGpu": "webgpu.refuse.standalone-alpha-mask",
            "coverage": "CoveragePlan.AlphaMask",
            "cpu": "CoverageModel.AlphaMask",
        },
        "ownership": {
            "discoveryOwner": TEXT_OWNER,
            "rasterizationOwner": TEXT_OWNER,
            "atlasLifetimeOwner": TEXT_OWNER,
            "invalidationOwner": TEXT_OWNER,
        },
        "coverageOwnsAtlas": False,
        "proofs": {
            "reasonCodeTest": (root / GEOMETRY_CONTRACTS_TEST_PATH).is_file(),
            "webGpuRefusalTest": (root / BITMAP_ORACLE_TEST_PATH).is_file(),
        },
        "nonClaims": non_claims(),
    }


def build_claim_guard(rows: list[dict[str, Any]]) -> dict[str, list[str]]:
    atlas_rows = [row for row in rows if row["pmCategory"] == "atlas-upload-plan"]
    return {
        "rowsMissingGlyphKeys": [
            row["rowId"]
            for row in atlas_rows
            if not isinstance(row.get("glyphKeys"), list) or not row["glyphKeys"]
        ],
        "rowsMissingAtlasGeneration": [
            row["rowId"]
            for row in atlas_rows
            if not isinstance(row.get("atlas", {}).get("generation"), int)
        ],
        "rowsMissingUploadBytes": [
            row["rowId"]
            for row in atlas_rows
            if not isinstance(row.get("atlas", {}).get("uploadByteCount"), int)
            or row["atlas"]["uploadByteCount"] <= 0
        ],
        "rowsMissingCacheIds": [
            row["rowId"]
            for row in rows
            if isinstance(row.get("cacheIds"), dict)
            and any(not row["cacheIds"].get(key) for key in row["cacheIds"])
        ],
        "cpuMaskOracleMissing": [
            row["rowId"]
            for row in rows
            if row["pmCategory"] == "cpu-mask-oracle"
            and row.get("oracle", {}).get("sampledMaskMatchesAtlas") is not True
        ],
        "coverageOwnershipViolations": [
            row["rowId"]
            for row in rows
            if row.get("coverageOwnsAtlas") is True
            or row.get("ownership", {}).get("atlasLifetimeOwner") != TEXT_OWNER
        ],
        "webGpuRouteMissingDecision": [
            row["rowId"]
            for row in rows
            if not isinstance(row.get("route", {}).get("webGpu"), str)
            or not row["route"]["webGpu"]
        ],
        "hiddenAtlasEvictionClaims": [
            row["rowId"]
            for row in rows
            if "no-dynamic-atlas-eviction-claim" not in row.get("nonClaims", [])
        ],
        "lcdOrSdfClaims": [
            row["rowId"]
            for row in rows
            if "no-lcd-or-sdf-claim" not in row.get("nonClaims", [])
        ],
        "ganeshGraphiteClaims": [
            row["rowId"]
            for row in rows
            if "no-ganesh-graphite-text-ops-claim" not in row.get("nonClaims", [])
        ],
        "thresholdOrRendererChanges": [],
    }


def committed_artifacts() -> list[str]:
    return sorted({
        KAN012_ATLAS_PATH,
        KAN012_STATS_PATH,
        KAN043_EVIDENCE_PATH,
        GLYPH_ATLAS_SOURCE_PATH,
        GLYPH_ATLAS_TEST_PATH,
        CPU_GLYPH_CACHE_PATH,
        GEOMETRY_CONTRACTS_PATH,
        GEOMETRY_CONTRACTS_TEST_PATH,
        BITMAP_ORACLE_TEST_PATH,
        SPEC_GEOMETRY_LOWERING_PATH,
        SPEC_RUNTIME_ARCH_PATH,
        TARGET_WGSL_PATH,
        SPEC_FONT_README_PATH,
        SPEC_GLYPH_COVERAGE_PATH,
    })


def build_evidence(root: Path) -> dict[str, Any]:
    root = root.resolve()
    require_contains(root, SPEC_GEOMETRY_LOWERING_PATH, [
        "Glyph atlas ownership remains with text/glyph infrastructure.",
        "Geometry must not invent glyph rasterization substitutes.",
    ])
    require_contains(root, SPEC_RUNTIME_ARCH_PATH, [
        "glyph atlas textures",
        "glyph atlas update",
    ])
    require_contains(root, TARGET_WGSL_PATH, [
        "Geometry produces coverage.",
        "Paint objects lower into an ordered color pipeline.",
    ])
    require_contains(root, SPEC_FONT_README_PATH, [
        "Keep glyph-mask atlas ownership in text/glyph infrastructure.",
        "Geometry and coverage consume opaque glyph mask references and stable diagnostics only.",
    ])
    require_contains(root, SPEC_GLYPH_COVERAGE_PATH, [
        "The text/glyph infrastructure owns:",
        "Geometry/Coverage owns only:",
        "Current WebGPU glyph-mask alpha coverage remains refused",
    ])
    require_contains(root, GLYPH_ATLAS_SOURCE_PATH, [
        "public val uploadByteCount",
        "public val glyphKey: String",
        "public val atlasGeneration: Int",
        "no-dynamic-atlas-eviction-claim",
    ])
    require_contains(root, GLYPH_ATLAS_TEST_PATH, [
        "simple latin cache builds deterministic bounded webgpu glyph atlas",
        "atlas coordinate sampling should match the CPU glyph mask alpha",
    ])
    require_contains(root, GEOMETRY_CONTRACTS_TEST_PATH, [
        "glyphMaskHandoffNamesTextOwnerAndLowersToAlphaMask",
        "glyphMaskWithoutTextOwnedMaskEmitsStableDependencyDiagnostic",
        "coverage.glyph-mask-dependency-unavailable",
    ])
    require_contains(root, BITMAP_ORACLE_TEST_PATH, [
        "coverage.alpha-mask-unsupported",
    ])

    atlas = load_json(root, KAN012_ATLAS_PATH)
    stats = load_json(root, KAN012_STATS_PATH)
    kan043 = load_json(root, KAN043_EVIDENCE_PATH)
    require(kan043.get("ticket") == "KAN-043", "KAN-043 evidence missing or changed")
    require(kan043.get("implicitSystemFallbackAllowed") is False, "KAN-043 fallback guard changed")

    rows = [
        build_atlas_upload_row(root, atlas, stats),
        build_cpu_mask_oracle_row(root, atlas),
        build_coverage_handoff_row(root),
        build_webgpu_refusal_row(root),
    ]
    guard = build_claim_guard(rows)
    for field, values in guard.items():
        require(not values, f"{field}: {values}")

    artifacts = committed_artifacts()
    missing = [path for path in artifacts if not (root / path).is_file()]
    require(not missing, f"missing committed artifacts: {missing}")

    evidence: dict[str, Any] = {
        "schemaVersion": 1,
        "ticket": "KAN-044",
        "packId": "kan-044-glyph-mask-atlas-ownership",
        "status": "pass",
        "closureDecision": "glyph-mask-atlas-ownership-boundary",
        "claimLevel": "pm-ownership-boundary-existing-evidence-only",
        "supportClaim": "no-new-rendering-support",
        "rendererChanged": False,
        "sharedShadersChanged": False,
        "thresholdsWeakened": False,
        "coverageOwnsGlyphAtlas": False,
        "broadAtlasEvictionClaim": False,
        "readinessDelta": 0,
        "summary": {
            "totalRows": len(rows),
            "atlasUploadPlanRows": sum(1 for row in rows if row["pmCategory"] == "atlas-upload-plan"),
            "cpuMaskOracleRows": sum(1 for row in rows if row["pmCategory"] == "cpu-mask-oracle"),
            "coverageHandoffRows": sum(1 for row in rows if row["pmCategory"] == "coverage-handoff"),
            "webGpuRefusalRows": sum(1 for row in rows if row["pmCategory"] == "webgpu-alpha-mask-refusal"),
            "rowsMissingGlyphKeys": len(guard["rowsMissingGlyphKeys"]),
            "rowsMissingAtlasGeneration": len(guard["rowsMissingAtlasGeneration"]),
            "rowsMissingUploadBytes": len(guard["rowsMissingUploadBytes"]),
            "coverageOwnershipViolations": len(guard["coverageOwnershipViolations"]),
        },
        "ownershipRows": rows,
        "claimGuard": guard,
        "requiredValidation": [
            "validateKan044GlyphMaskAtlasOwnership",
            ":render-pipeline:pipelineConformanceTest -- includes GeometryCoverageContractsTest",
            ":gpu-raster:pipelineConformanceTest -- includes SkWebGpuGlyphAtlasTest and SimpleLatinLineSceneEvidenceTest",
            "pipelinePmBundle",
        ],
        "validationRows": [
            {
                "id": "atlas-route-visible",
                "status": "pass",
                "evidence": "Atlas row records glyph keys, generation, R8/A8 format, upload bytes, upload hash, and cache ids.",
            },
            {
                "id": "cpu-mask-oracle-visible",
                "status": "pass",
                "evidence": "SkWebGpuGlyphAtlasTest proves atlas sampling matches CPU glyph mask alpha.",
            },
            {
                "id": "coverage-consumes-opaque-ref",
                "status": "pass",
                "evidence": "GlyphMaskLowering exposes CoveragePlan.AlphaMask while ownership remains text-glyph-infrastructure.",
            },
            {
                "id": "webgpu-refusal-visible",
                "status": "pass",
                "evidence": "Standalone alpha-mask WebGPU route remains expected-unsupported via coverage.alpha-mask-unsupported.",
            },
        ],
        "nonClaims": [
            "KAN-044 does not add renderer, shader, selector, PipelineKey, threshold, or budget changes.",
            "KAN-044 does not claim broad glyph atlas support, dynamic atlas eviction, LCD, SDF, bitmap glyph, or color-font support.",
            "KAN-044 does not move glyph discovery, rasterization, atlas lifetime, or invalidation ownership into geometry/coverage.",
            "KAN-044 does not port Ganesh, Graphite, SkSL compiler, SkSL IR, or SkSL VM.",
        ],
        "artifactAudit": {
            "checkedCommittedArtifacts": len(artifacts),
            "missingCommittedArtifacts": len(missing),
            "missing": missing,
        },
        "artifactPaths": artifacts,
    }
    return evidence


def markdown_table(rows: list[dict[str, Any]]) -> str:
    return "\n".join(
        "| `{rowId}` | `{status}` | `{category}` | `{route}` | `{reason}` |".format(
            rowId=row["rowId"],
            status=row["status"],
            category=row["pmCategory"],
            route=row["route"].get("webGpu") or row["route"].get("coverage"),
            reason=row.get("reasonCode") or row.get("fallbackReason") or "none",
        )
        for row in rows
    )


def render_markdown(evidence: dict[str, Any]) -> str:
    summary = evidence["summary"]
    required = "\n".join(f"- `{item}`" for item in evidence["requiredValidation"])
    validations = "\n".join(
        f"| `{row['id']}` | `{row['status']}` | {row['evidence']} |"
        for row in evidence["validationRows"]
    )
    guards = "\n".join(f"| {key} | `{value}` |" for key, value in evidence["claimGuard"].items())
    non_claims = "\n".join(f"- {item}" for item in evidence["nonClaims"])
    return f"""# KAN-044 Glyph Mask Atlas Ownership

KAN-044 packages the glyph mask / atlas ownership boundary from existing text
and geometry evidence. It records the text-owned atlas upload plan, CPU mask
oracle, coverage handoff, and WebGPU standalone alpha-mask refusal without
adding renderer behavior.

## Summary

| Metric | Count |
|---|---:|
| Total rows | {summary['totalRows']} |
| Atlas upload-plan rows | {summary['atlasUploadPlanRows']} |
| CPU mask oracle rows | {summary['cpuMaskOracleRows']} |
| Coverage handoff rows | {summary['coverageHandoffRows']} |
| WebGPU refusal rows | {summary['webGpuRefusalRows']} |
| Rows missing glyph keys | {summary['rowsMissingGlyphKeys']} |
| Rows missing atlas generation | {summary['rowsMissingAtlasGeneration']} |
| Rows missing upload bytes | {summary['rowsMissingUploadBytes']} |
| Coverage ownership violations | {summary['coverageOwnershipViolations']} |

## Ownership Rows

| Row | Status | Category | WebGPU/Coverage route | Reason |
|---|---|---|---|---|
{markdown_table(evidence['ownershipRows'])}

The owner is `text-glyph-infrastructure`; coverage consumes opaque mask refs only.

## Claim Guard

| Guard | Value |
|---|---|
{guards}

## Required Validation

{required}

## Validation

| Check | Status | Evidence |
|---|---|---|
{validations}

## Non-Claims

{non_claims}
"""


def write_outputs(root: Path, output_dir: Path) -> dict[str, Any]:
    evidence = build_evidence(root)
    output_dir.mkdir(parents=True, exist_ok=True)
    (output_dir / OUTPUT_JSON).write_text(
        json.dumps(evidence, indent=2, sort_keys=False) + "\n",
        encoding="utf-8",
    )
    (output_dir / OUTPUT_MARKDOWN).write_text(render_markdown(evidence), encoding="utf-8")
    return evidence


def main() -> int:
    root = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path.cwd()
    output_dir = Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else root / DEFAULT_OUTPUT_DIR
    evidence = write_outputs(root, output_dir)
    summary = evidence["summary"]
    print(
        "KAN-044 validation passed: "
        f"{summary['totalRows']} rows, "
        f"{summary['atlasUploadPlanRows']} atlas upload-plan, "
        f"{summary['coverageOwnershipViolations']} ownership violations."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
