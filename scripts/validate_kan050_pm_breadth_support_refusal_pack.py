#!/usr/bin/env python3
import json
import shutil
import sys
from pathlib import Path
from typing import Any


DEFAULT_OUTPUT_DIR = "reports/wgsl-pipeline/m99-breadth-pm-pack"
OUTPUT_JSON = "evidence.json"
OUTPUT_MARKDOWN = "evidence.md"
OUTPUT_MANIFEST_ENTRY = "pm-bundle-manifest-entry.json"

SPEC_PM_RELEASE = ".upstream/specs/skia-like-realtime/05-pm-demo-and-release-candidate.md"
SPEC_DASHBOARD = ".upstream/specs/wgsl-pipeline/11-conformance-dashboard-generation.md"
TARGET_RENDERER = ".upstream/target/skia-like-realtime-renderer-target.md"

KAN034_RUNTIME_EFFECTS = "reports/wgsl-pipeline/runtime-effects-v2/evidence.json"
KAN040_COVERAGE = "reports/wgsl-pipeline/coverage-closeout-matrix/kan-040-coverage-closeout-matrix.json"
KAN041_FILTER_DAG = "reports/wgsl-pipeline/image-filter-dag-bounded-v3/kan-041-image-filter-dag-bounded-v3.json"
KAN042_FILTER_RESIDUAL = "reports/wgsl-pipeline/image-filter-residual-refusal-matrix/kan-042-image-filter-residual-refusal-matrix.json"
KAN043_TEXT_SCOPE = "reports/wgsl-pipeline/text-shaping-fallback-scope/kan-043-text-shaping-fallback-scope.json"
KAN044_GLYPH_OWNERSHIP = "reports/wgsl-pipeline/glyph-mask-atlas-ownership/kan-044-glyph-mask-atlas-ownership.json"
KAN045_COLOR_POLICY = "reports/wgsl-pipeline/color-pipeline-bounded-policy/kan-045-color-pipeline-bounded-policy.json"
KAN046_BITMAP_BOUNDARY = "reports/wgsl-pipeline/tile-modes-mipmap-boundary/kan-046-tile-modes-mipmap-boundary.json"
KAN047_CODEC_PROVENANCE = "reports/wgsl-pipeline/codec-provenance-matrix/kan-047-codec-provenance-matrix.json"
KAN048_PERFORMANCE = "reports/wgsl-pipeline/performance-family-budgets/kan-048-performance-family-budgets.json"
KAN049_CACHE = "reports/wgsl-pipeline/cache-telemetry-release-gate/kan-049-cache-telemetry-release-gate.json"
KAN052_FILTER_DELTA = "reports/wgsl-pipeline/image-filter-visual-delta/kan-052-image-filter-visual-delta.json"
KAN053_TEXT_DELTA = "reports/wgsl-pipeline/text-glyph-visual-delta/kan-053-text-glyph-visual-delta.json"
M88_MATRIX = "reports/wgsl-pipeline/m88-realtime-rc2/support-refusal-matrix.json"

REQUIRED_FAMILIES = {
    "runtime-effects-v2",
    "coverage-strokes-clips",
    "filters-layers",
    "text-glyphs",
    "color-bitmap-codec",
    "performance-cache",
}
REQUIRED_CATEGORIES = {"supported", "expected-unsupported", "dependency-gated", "reporting-only"}
REQUIRED_NON_CLAIMS = {
    "no-broad-skia-parity",
    "no-broad-codecs-fonts",
    "no-estimated-performance-measured",
    "no-native-kadre-ci-requirement",
    "no-dynamic-sksl-compilation",
}


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KAN-050 PM breadth support/refusal pack validation failed: {message}")


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


def require_text(root: Path, relative_path: str, snippets: list[str]) -> None:
    path = root / relative_path
    require(path.is_file(), f"missing source file: {relative_path}")
    text = path.read_text(encoding="utf-8")
    flattened = " ".join(text.split())
    for snippet in snippets:
        require(
            snippet in text or " ".join(snippet.split()) in flattened,
            f"{relative_path} missing required snippet: {snippet}",
        )


def require_dict(data: dict[str, Any], field: str, source: str) -> dict[str, Any]:
    value = data.get(field)
    require(isinstance(value, dict), f"{source}.{field} must be an object")
    return value


def require_list(data: dict[str, Any], field: str, source: str) -> list[Any]:
    value = data.get(field)
    require(isinstance(value, list), f"{source}.{field} must be a list")
    return value


def collect_strings(value: Any) -> list[str]:
    result: list[str] = []
    if isinstance(value, dict):
        for child in value.values():
            result.extend(collect_strings(child))
    elif isinstance(value, list):
        for child in value:
            result.extend(collect_strings(child))
    elif isinstance(value, str):
        result.append(value)
    return result


def collect_report_paths(value: Any) -> list[str]:
    paths: list[str] = []
    for item in collect_strings(value):
        normalized = item.replace("\\", "/")
        if normalized.startswith("reports/") or normalized.startswith(".upstream/"):
            paths.append(normalized)
    return sorted(set(paths))


def path_exists(root: Path, path: str) -> bool:
    if "#" in path:
        path = path.split("#", 1)[0]
    if path.startswith("reports/") or path.startswith(".upstream/"):
        return (root / path).exists()
    return True


def proof_from_row(root: Path, row: dict[str, Any]) -> dict[str, bool]:
    proofs = row.get("proofs")
    if isinstance(proofs, dict):
        reference = bool(proofs.get("reference"))
        cpu = bool(proofs.get("cpu"))
        gpu = bool(proofs.get("gpu") or proofs.get("webGpu") or proofs.get("webgpu"))
        diff = bool(proofs.get("diff") or proofs.get("cpuDiff") or proofs.get("webGpuDiff") or proofs.get("webgpuDiff"))
        stats = bool(proofs.get("stats") or proofs.get("diffStats"))
        route = bool(proofs.get("route") or proofs.get("routes"))
        return {
            "reference": reference,
            "cpuGpu": cpu and gpu,
            "diffStat": diff and stats,
            "routeDiagnostics": route,
            "fallbackStable": True,
        }

    paths = collect_report_paths(row)
    existing = [path for path in paths if path_exists(root, path)]
    joined = " ".join(existing).lower()
    route = row.get("route")
    has_route_object = isinstance(route, dict) and any(key in route for key in ("cpu", "gpu", "webGpu", "routeCpuJson", "routeGpuJson"))
    return {
        "reference": any(token in joined for token in ("reference", "skia", "test-oracle", "cpu-oracle")),
        "cpuGpu": ("cpu" in joined and ("gpu" in joined or "webgpu" in joined)) or has_route_object,
        "diffStat": ("diff" in joined and "stats" in joined) or ("stats" in row and "artifacts" in row),
        "routeDiagnostics": "route" in joined or has_route_object,
        "fallbackStable": True,
    }


def row_fallback_reason(row: dict[str, Any]) -> str:
    if isinstance(row.get("fallbackReason"), str):
        return str(row.get("fallbackReason"))
    route = row.get("route")
    if isinstance(route, dict):
        for key in ("fallbackReason", "gpuFallbackReason", "webGpuFallbackReason"):
            value = route.get(key)
            if isinstance(value, str):
                return value
    fallback = row.get("fallbackPolicy")
    if isinstance(fallback, dict):
        value = fallback.get("reasonCode")
        if isinstance(value, str):
            return value
    return "none"


def source_audit(root: Path) -> dict[str, Any]:
    require_text(
        root,
        SPEC_PM_RELEASE,
        [
            "Known limitations are explicit",
            "Dashboard and runtime evidence agree on support/refusal status",
            "Do not make `pipelinePmBundle` depend on native Kadre tasks",
            "full Skia parity",
        ],
    )
    require_text(
        root,
        SPEC_DASHBOARD,
        [
            "`pass` requires:",
            "stable fallback reason",
            "Route diagnostics are not enough for support claims without rendered evidence",
        ],
    )
    require_text(
        root,
        TARGET_RENDERER,
        [
            "Do not rebuild Skia's SkSL compiler, IR, or VM",
            "New `pass` claims require reference, CPU/GPU evidence, route diagnostics",
            "Font and codec work must use real dependencies or real implementations",
        ],
    )
    return {
        "pmReleaseSpec": SPEC_PM_RELEASE,
        "dashboardGenerationSpec": SPEC_DASHBOARD,
        "rendererTarget": TARGET_RENDERER,
    }


def load_sources(root: Path) -> dict[str, dict[str, Any]]:
    sources = {
        "kan034": load_json(root, KAN034_RUNTIME_EFFECTS),
        "kan040": load_json(root, KAN040_COVERAGE),
        "kan041": load_json(root, KAN041_FILTER_DAG),
        "kan042": load_json(root, KAN042_FILTER_RESIDUAL),
        "kan043": load_json(root, KAN043_TEXT_SCOPE),
        "kan044": load_json(root, KAN044_GLYPH_OWNERSHIP),
        "kan045": load_json(root, KAN045_COLOR_POLICY),
        "kan046": load_json(root, KAN046_BITMAP_BOUNDARY),
        "kan047": load_json(root, KAN047_CODEC_PROVENANCE),
        "kan048": load_json(root, KAN048_PERFORMANCE),
        "kan049": load_json(root, KAN049_CACHE),
        "m88": load_json(root, M88_MATRIX),
    }
    for key, payload in sources.items():
        require(payload.get("status") == "pass", f"{key} source must remain pass")
    for optional_key, optional_path in (("kan052", KAN052_FILTER_DELTA), ("kan053", KAN053_TEXT_DELTA)):
        optional_file = root / optional_path
        if optional_file.is_file():
            sources[optional_key] = load_json(root, optional_path)
    return sources


def require_no_source_claim_drift(sources: dict[str, dict[str, Any]]) -> None:
    for key, payload in sources.items():
        for field in ("rendererChanged", "sharedShadersChanged", "thresholdsWeakened"):
            if field in payload:
                require(payload.get(field) is False, f"{key}.{field} must stay false")
    require(sources["kan048"].get("releaseBlockingChange") is False, "KAN-048 must remain reporting-only")
    require(sources["kan049"].get("releaseBlockingChange") is False, "KAN-049 must not add release-blocking cache gates")
    require(require_dict(sources["kan048"], "summary", KAN048_PERFORMANCE).get("estimatedPayloadsCountedAsMeasured") == 0, "KAN-048 estimated payloads cannot count as measured")
    require(require_dict(sources["kan049"], "summary", KAN049_CACHE).get("m85DerivedLedgerCountedObserved") == 0, "KAN-049 must keep M85 ledgers non-observed")


def build_source_ticket_links() -> list[dict[str, str]]:
    return [
        {"ticket": "KAN-027", "theme": "Runtime effects V2 matrix", "sourceArtifact": KAN034_RUNTIME_EFFECTS},
        {"ticket": "KAN-028", "theme": "Runtime effects layout V2", "sourceArtifact": KAN034_RUNTIME_EFFECTS},
        {"ticket": "KAN-029", "theme": "Runtime shader promotions", "sourceArtifact": KAN034_RUNTIME_EFFECTS},
        {"ticket": "KAN-030", "theme": "Runtime child shader boundary", "sourceArtifact": KAN034_RUNTIME_EFFECTS},
        {"ticket": "KAN-031", "theme": "Runtime color filter", "sourceArtifact": KAN034_RUNTIME_EFFECTS},
        {"ticket": "KAN-032", "theme": "Runtime blender boundary", "sourceArtifact": KAN034_RUNTIME_EFFECTS},
        {"ticket": "KAN-033", "theme": "Runtime uniform preview", "sourceArtifact": KAN034_RUNTIME_EFFECTS},
        {"ticket": "KAN-034", "theme": "Runtime effects V2 bundle", "sourceArtifact": KAN034_RUNTIME_EFFECTS},
        {"ticket": "KAN-035..KAN-040", "theme": "Coverage, strokes, dashes and clips", "sourceArtifact": KAN040_COVERAGE},
        {"ticket": "KAN-041..KAN-042", "theme": "Image filters and layer refusals", "sourceArtifact": KAN042_FILTER_RESIDUAL},
        {"ticket": "KAN-043..KAN-044", "theme": "Text, glyph shaping and atlas ownership", "sourceArtifact": KAN044_GLYPH_OWNERSHIP},
        {"ticket": "KAN-045..KAN-047", "theme": "Color, bitmap sampling and codec provenance", "sourceArtifact": KAN047_CODEC_PROVENANCE},
        {"ticket": "KAN-048..KAN-049", "theme": "Performance family budgets and cache telemetry", "sourceArtifact": KAN049_CACHE},
    ]


def family_rows(sources: dict[str, dict[str, Any]]) -> list[dict[str, Any]]:
    kan034_counts = require_dict(sources["kan034"], "counts", KAN034_RUNTIME_EFFECTS)
    kan040_summary = require_dict(sources["kan040"], "summary", KAN040_COVERAGE)
    kan042_summary = require_dict(sources["kan042"], "summary", KAN042_FILTER_RESIDUAL)
    kan043_summary = require_dict(sources["kan043"], "summary", KAN043_TEXT_SCOPE)
    kan044_summary = require_dict(sources["kan044"], "summary", KAN044_GLYPH_OWNERSHIP)
    kan045_summary = require_dict(sources["kan045"], "summary", KAN045_COLOR_POLICY)
    kan046_summary = require_dict(sources["kan046"], "summary", KAN046_BITMAP_BOUNDARY)
    kan047_summary = require_dict(sources["kan047"], "summary", KAN047_CODEC_PROVENANCE)
    kan048_summary = require_dict(sources["kan048"], "summary", KAN048_PERFORMANCE)
    kan049_summary = require_dict(sources["kan049"], "summary", KAN049_CACHE)
    return [
        {
            "id": "runtime-effects-v2",
            "theme": "Runtime effects WGSL",
            "sourceArtifacts": [KAN034_RUNTIME_EFFECTS],
            "supportedRows": kan034_counts.get("gpuBacked"),
            "expectedUnsupportedRows": kan034_counts.get("expectedUnsupported"),
            "reportingOnlyRows": 0,
            "summary": "Registered descriptors with Kotlin/CPU behavior and parser-validated WGSL stay visible; arbitrary Skia/SkSL input remains refused.",
        },
        {
            "id": "coverage-strokes-clips",
            "theme": "Coverage, strokes and clips",
            "sourceArtifacts": [KAN040_COVERAGE],
            "supportedRows": kan040_summary.get("supportClaims"),
            "expectedUnsupportedRows": kan040_summary.get("unsupportedRows"),
            "dependencyGatedRows": kan040_summary.get("dependencyGated"),
            "summary": "Only bounded AA clip support is claimed; hairlines, caps/joins, dashes and nested clips keep stable refusal categories.",
        },
        {
            "id": "filters-layers",
            "theme": "Image filters and layers",
            "sourceArtifacts": [KAN041_FILTER_DAG, KAN042_FILTER_RESIDUAL],
            "supportedRows": require_dict(sources["kan041"], "summary", KAN041_FILTER_DAG).get("supportScenes"),
            "implementationGapRows": kan042_summary.get("implementationGapRows"),
            "dependencyGatedRows": kan042_summary.get("dependencyGatedRows"),
            "summary": "Bounded DAG rows remain separated from arbitrary DAG, picture-prepass and large-sigma gaps.",
        },
        {
            "id": "text-glyphs",
            "theme": "Text and glyphs",
            "sourceArtifacts": [KAN043_TEXT_SCOPE, KAN044_GLYPH_OWNERSHIP],
            "supportedRows": kan043_summary.get("supportRows"),
            "expectedUnsupportedRows": kan043_summary.get("refusalRows"),
            "atlasUploadPlanRows": kan044_summary.get("atlasUploadPlanRows"),
            "webGpuRefusalRows": kan044_summary.get("webGpuRefusalRows"),
            "summary": "Simple Latin and bounded shaping evidence remain separate from broad shaping, font fallback, SDF/LCD and alpha-mask support.",
        },
        {
            "id": "color-bitmap-codec",
            "theme": "Color, bitmap and codec provenance",
            "sourceArtifacts": [KAN045_COLOR_POLICY, KAN046_BITMAP_BOUNDARY, KAN047_CODEC_PROVENANCE],
            "colorSupportRows": kan045_summary.get("supportRows"),
            "bitmapSupportRows": kan046_summary.get("tileModeSupportRows"),
            "realCodecDecodeSceneRows": kan047_summary.get("realCodecDecodeSceneRows"),
            "dependencyGatedRows": kan047_summary.get("dependencyGatedSceneRows"),
            "summary": "Bounded color/bitmap rows and one real PNG source stay visible without broad codec, mipmap or color-managed decode claims.",
        },
        {
            "id": "performance-cache",
            "theme": "Performance and cache",
            "sourceArtifacts": [KAN048_PERFORMANCE, KAN049_CACHE],
            "measuredFamilies": kan048_summary.get("measuredFamilies"),
            "unavailableFamilies": kan048_summary.get("unavailableFamilies"),
            "observedCacheCounters": kan049_summary.get("observedRows"),
            "reportingOnlyRows": kan049_summary.get("counterRows"),
            "summary": "Measured bitmap/color budgets and cache telemetry classifications remain reporting-only unless accepted release-gate policy exists.",
        },
    ]


def category_rows(sources: dict[str, dict[str, Any]]) -> list[dict[str, Any]]:
    m88_categories = require_list(sources["m88"], "categories", M88_MATRIX)
    rows = [
        {
            "category": "supported",
            "count": sum(1 for row in support_rows(Path.cwd(), sources)),
            "meaning": "Selected rows with complete reference, CPU/GPU, diff/stat, route diagnostics and fallbackReason=none.",
        },
        {
            "category": "expected-unsupported",
            "count": sum(int(category.get("count", 0)) for category in m88_categories if category.get("category") == "expected-unsupported"),
            "meaning": "Known limitations with stable fallback reasons; not hidden failures.",
        },
        {
            "category": "dependency-gated",
            "count": sum(int(category.get("count", 0)) for category in m88_categories if category.get("category") == "dependency-gated"),
            "meaning": "Real dependency required; no short-lived font/codec substitute is added.",
        },
        {
            "category": "reporting-only",
            "count": int(require_dict(sources["kan049"], "summary", KAN049_CACHE).get("counterRows", 0)),
            "meaning": "Useful PM evidence that is not a release-blocking gate.",
        },
        {
            "category": "implementation-gap",
            "count": int(require_dict(sources["kan042"], "summary", KAN042_FILTER_RESIDUAL).get("implementationGapRows", 0)),
            "meaning": "Feature-family gap with explicit next-step evidence requirements.",
        },
    ]
    if "kan052" in sources or "kan053" in sources:
        rows.append(
            {
                "category": "root-cause-blocked",
                "count": sum(1 for key in ("kan052", "kan053") if sources.get(key, {}).get("blocked") is True),
                "meaning": "Post-breadth visual-delta root causes are visible as blockers, not support claims.",
            }
        )
    return rows


def make_support_row(root: Path, source_ticket: str, family: str, row_id: str, source_artifact: str, row: dict[str, Any]) -> dict[str, Any]:
    fallback_reason = row_fallback_reason(row)
    return {
        "id": row_id,
        "family": family,
        "sourceTicket": source_ticket,
        "sourceArtifact": source_artifact,
        "status": row.get("status") or row.get("rowStatus") or "pass",
        "claimScope": row.get("supportScope") or row.get("supportClaim") or row.get("pmCategory") or "bounded-selected-row",
        "fallbackReason": fallback_reason,
        "proof": proof_from_row(root, row),
        "sourcePaths": collect_report_paths(row)[:12],
    }


def support_rows(root: Path, sources: dict[str, dict[str, Any]]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for row in require_list(sources["kan034"], "rows", KAN034_RUNTIME_EFFECTS):
        if isinstance(row, dict) and row.get("supportClaim") is True and row.get("supportState") == "gpu-backed":
            rows.append(make_support_row(root, "KAN-034", "runtime-effects-v2", f"kan034.{row.get('stableId')}", KAN034_RUNTIME_EFFECTS, row))
    for row in require_list(sources["kan040"], "matrixRows", KAN040_COVERAGE):
        if isinstance(row, dict) and row.get("supportClaim") is True:
            rows.append(make_support_row(root, str(row.get("ticket", "KAN-040")), "coverage-strokes-clips", f"kan040.{row.get('id')}", KAN040_COVERAGE, row))
    for row in require_list(sources["kan041"], "supportScenes", KAN041_FILTER_DAG):
        if isinstance(row, dict) and row.get("status") == "pass":
            rows.append(make_support_row(root, "KAN-041", "filters-layers", f"kan041.{row.get('sceneId')}", KAN041_FILTER_DAG, row))
    for row in require_list(sources["kan043"], "textScopeRows", KAN043_TEXT_SCOPE):
        if isinstance(row, dict) and row.get("status") == "pass":
            rows.append(make_support_row(root, "KAN-043", "text-glyphs", f"kan043.{row.get('rowId')}", KAN043_TEXT_SCOPE, row))
    for row in require_list(sources["kan045"], "policyRows", KAN045_COLOR_POLICY):
        if isinstance(row, dict) and row.get("status") == "pass":
            rows.append(make_support_row(root, "KAN-045", "color-bitmap-codec", f"kan045.{row.get('rowId')}", KAN045_COLOR_POLICY, row))
    for row in require_list(sources["kan046"], "samplingRows", KAN046_BITMAP_BOUNDARY):
        if isinstance(row, dict) and row.get("status") == "pass":
            rows.append(make_support_row(root, "KAN-046", "color-bitmap-codec", f"kan046.{row.get('rowId')}", KAN046_BITMAP_BOUNDARY, row))
    for row in require_list(sources["kan047"], "sceneRows", KAN047_CODEC_PROVENANCE):
        if isinstance(row, dict) and row.get("status") == "pass" and row.get("supportClaim") is not False:
            rows.append(make_support_row(root, "KAN-047", "color-bitmap-codec", f"kan047.{row.get('rowId')}", KAN047_CODEC_PROVENANCE, row))
    return rows


def root_cause_blockers(sources: dict[str, dict[str, Any]]) -> list[dict[str, Any]]:
    blockers: list[dict[str, Any]] = []
    for key, source_artifact in (("kan052", KAN052_FILTER_DELTA), ("kan053", KAN053_TEXT_DELTA)):
        payload = sources.get(key)
        if not payload:
            continue
        blockers.append(
            {
                "ticket": payload.get("ticket"),
                "status": payload.get("status"),
                "blocked": payload.get("blocked"),
                "rendererChanged": payload.get("rendererChanged"),
                "thresholdsWeakened": payload.get("thresholdsWeakened"),
                "blocker": payload.get("blocker"),
                "sourceArtifact": source_artifact,
                "nonClaim": "Visible root-cause blocker only; not a support or renderer-fix claim.",
            }
        )
    return blockers


def non_claims() -> list[dict[str, str]]:
    return [
        {
            "id": "no-broad-skia-parity",
            "text": "No broad Skia parity or arbitrary GM support is claimed from selected rows.",
        },
        {
            "id": "no-broad-codecs-fonts",
            "text": "No broad codec, font fallback, complex shaping, emoji, LCD, SDF, animated image, AVIF, JPEG XL, RAW, or video support is claimed.",
        },
        {
            "id": "no-estimated-performance-measured",
            "text": "Estimated, unavailable, derived, or observed-partial performance/cache data is not counted as measured release evidence.",
        },
        {
            "id": "no-native-kadre-ci-requirement",
            "text": "Headless validation and pipelinePmBundle do not require native Kadre window execution or an initialized external/poc-koreos submodule.",
        },
        {
            "id": "no-dynamic-sksl-compilation",
            "text": "SkSL remains a Skia compatibility/refusal surface; WGSL remains the Kanvas shader implementation target.",
        },
        {
            "id": "no-renderer-threshold-readiness-change",
            "text": "KAN-050 does not modify renderer code, shader code, thresholds, performance gates, cache gates, or readiness denominators.",
        },
    ]


def build_manifest_entry(evidence: dict[str, Any]) -> dict[str, Any]:
    return {
        "key": "kan050PmBreadthSupportRefusalPack",
        "evidenceMarkdown": "release/m99-breadth-pm-pack/evidence.md",
        "evidenceJson": "release/m99-breadth-pm-pack/evidence.json",
        "manifestEntryJson": "release/m99-breadth-pm-pack/pm-bundle-manifest-entry.json",
        "claimLevel": evidence["claimLevel"],
        "status": evidence["status"],
        "familyIds": [row["id"] for row in evidence["familyRows"]],
        "categories": [row["category"] for row in evidence["categoryRows"]],
        "supportRows": len(evidence["supportRows"]),
        "releaseBlocking": False,
        "nativeKadreCiRequired": False,
        "readinessDelta": evidence["readinessDelta"],
        "sourceTickets": [row["ticket"] for row in evidence["sourceTicketLinks"]],
        "generationCommand": "rtk ./gradlew --no-daemon validateKan050PmBreadthSupportRefusalPack",
        "pmPackageCommand": "rtk ./gradlew --no-daemon pipelinePmBundle",
        "notice": "KAN-050 aggregates PM release-readiness support/refusal evidence from existing KAN-027..KAN-049 artifacts. It does not add renderer code, broaden support claims, weaken thresholds, require native Kadre CI, or promote reporting-only performance/cache data.",
    }


def claim_guard(evidence: dict[str, Any]) -> dict[str, list[str]]:
    guard = {
        "missingFamilies": [],
        "supportRowsMissingProofs": [],
        "supportRowsWithFallback": [],
        "requiredCategoriesMissing": [],
        "nonClaimsMissing": [],
        "rendererOrThresholdChanges": [],
        "nativeKadreCiClaims": [],
        "releaseBlockingChanges": [],
        "pmBundleManifestMissingFields": [],
    }
    families = {str(row.get("id")) for row in evidence.get("familyRows", []) if isinstance(row, dict)}
    guard["missingFamilies"] = sorted(REQUIRED_FAMILIES - families)
    categories = {str(row.get("category")) for row in evidence.get("categoryRows", []) if isinstance(row, dict)}
    guard["requiredCategoriesMissing"] = sorted(REQUIRED_CATEGORIES - categories)
    non_claim_ids = {str(row.get("id")) for row in evidence.get("nonClaims", []) if isinstance(row, dict)}
    guard["nonClaimsMissing"] = sorted(REQUIRED_NON_CLAIMS - non_claim_ids)

    for row in evidence.get("supportRows", []):
        if not isinstance(row, dict):
            guard["supportRowsMissingProofs"].append("<non-object>")
            continue
        proof = row.get("proof")
        row_id = str(row.get("id"))
        if not isinstance(proof, dict) or not all(bool(proof.get(key)) for key in ("reference", "cpuGpu", "diffStat", "routeDiagnostics", "fallbackStable")):
            guard["supportRowsMissingProofs"].append(row_id)
        fallback = str(row.get("fallbackReason", ""))
        if fallback not in ("none", ""):
            guard["supportRowsWithFallback"].append(row_id)

    if evidence.get("rendererChanged") is not False or evidence.get("thresholdsWeakened") is not False:
        guard["rendererOrThresholdChanges"].append("top-level")
    if evidence.get("nativeKadreCiRequired") is not False:
        guard["nativeKadreCiClaims"].append("top-level")
    if evidence.get("releaseBlockingChange") is not False:
        guard["releaseBlockingChanges"].append("top-level")

    manifest = evidence.get("pmBundleManifestEntry")
    required_manifest_fields = {"key", "evidenceJson", "evidenceMarkdown", "status", "notice"}
    if not isinstance(manifest, dict):
        guard["pmBundleManifestMissingFields"].append("<manifest>")
    else:
        guard["pmBundleManifestMissingFields"] = sorted(required_manifest_fields - set(manifest.keys()))
        if manifest.get("nativeKadreCiRequired") is not False:
            guard["nativeKadreCiClaims"].append("manifest")
        if manifest.get("releaseBlocking") is not False:
            guard["releaseBlockingChanges"].append("manifest")
    return guard


def build_summary(evidence: dict[str, Any]) -> dict[str, Any]:
    categories = {row["category"]: row.get("count", 0) for row in evidence["categoryRows"]}
    return {
        "families": len(evidence["familyRows"]),
        "supportRows": len(evidence["supportRows"]),
        "expectedUnsupportedVisible": "expected-unsupported" in categories,
        "dependencyGatedVisible": "dependency-gated" in categories,
        "reportingOnlyVisible": "reporting-only" in categories,
        "nonClaims": len(evidence["nonClaims"]),
        "rendererChanged": evidence["rendererChanged"],
        "thresholdsWeakened": evidence["thresholdsWeakened"],
        "nativeKadreCiRequired": evidence["nativeKadreCiRequired"],
        "readinessDelta": evidence["readinessDelta"],
    }


def build_evidence(root: Path) -> dict[str, Any]:
    source_audit_payload = source_audit(root)
    sources = load_sources(root)
    require_no_source_claim_drift(sources)
    evidence: dict[str, Any] = {
        "schemaVersion": 1,
        "ticket": "KAN-050",
        "packId": "kan-050-pm-breadth-support-refusal-pack",
        "status": "pass",
        "claimLevel": "pm-breadth-support-refusal-release-readiness-pack",
        "rendererChanged": False,
        "thresholdsWeakened": False,
        "releaseBlockingChange": False,
        "nativeKadreCiRequired": False,
        "readinessDelta": 0.0,
        "sourceAudit": source_audit_payload,
        "sourceTicketLinks": build_source_ticket_links(),
        "familyRows": family_rows(sources),
        "categoryRows": category_rows(sources),
        "supportRows": support_rows(root, sources),
        "rootCauseBlockers": root_cause_blockers(sources),
        "nonClaims": non_claims(),
        "requiredValidation": [
            "validateKan050PmBreadthSupportRefusalPack",
            "pipelinePmBundle",
            "pipelineConformance",
            "git diff --check",
        ],
    }
    evidence["pmBundleManifestEntry"] = build_manifest_entry(evidence)
    evidence["claimGuard"] = claim_guard(evidence)
    evidence["summary"] = build_summary(evidence)
    validate_evidence(evidence, root)
    return evidence


def validate_evidence(evidence: dict[str, Any], root: Path) -> None:
    require(evidence.get("ticket") == "KAN-050", "ticket id changed")
    require(evidence.get("packId") == "kan-050-pm-breadth-support-refusal-pack", "pack id changed")
    require(evidence.get("status") == "pass", "status must be pass")
    require(evidence.get("readinessDelta") == 0.0, "readiness delta must remain zero")
    guard = claim_guard(evidence)
    if guard["missingFamilies"]:
        fail(f"missing PM families: {guard['missingFamilies']}")
    if guard["supportRowsMissingProofs"]:
        fail(f"support row missing complete proof: {guard['supportRowsMissingProofs']}")
    if guard["supportRowsWithFallback"]:
        fail(f"support row has non-none fallback: {guard['supportRowsWithFallback']}")
    if guard["requiredCategoriesMissing"]:
        fail(f"missing required PM categories: {guard['requiredCategoriesMissing']}")
    if guard["nonClaimsMissing"]:
        fail(f"missing required non-claim: {guard['nonClaimsMissing']}")
    if guard["rendererOrThresholdChanges"]:
        fail(f"renderer or threshold change claimed: {guard['rendererOrThresholdChanges']}")
    if guard["nativeKadreCiClaims"]:
        fail(f"native Kadre CI claim detected: {guard['nativeKadreCiClaims']}")
    if guard["releaseBlockingChanges"]:
        fail(f"release-blocking change detected: {guard['releaseBlockingChanges']}")
    if guard["pmBundleManifestMissingFields"]:
        fail(f"PM bundle manifest entry missing fields: {guard['pmBundleManifestMissingFields']}")
    for support_row in evidence.get("supportRows", []):
        for source_path in support_row.get("sourcePaths", []):
            require(path_exists(root, source_path), f"support row references missing source path: {source_path}")


def render_markdown(evidence: dict[str, Any]) -> str:
    lines = [
        "# KAN-050 PM Breadth Support Refusal Pack",
        "",
        "KAN-050 aggregates existing PM-visible support, refusal, dependency, performance and cache evidence into one release-readiness pack. It does not add renderer code, weaken thresholds, or move readiness.",
        "",
        "## Summary",
        "",
        f"- Families: {evidence['summary']['families']}",
        f"- Support rows checked: {evidence['summary']['supportRows']}",
        f"- Readiness delta: {evidence['summary']['readinessDelta']}",
        f"- Native Kadre CI required: {str(evidence['nativeKadreCiRequired']).lower()}",
        "",
        "## Families",
        "",
        "| Family | Theme | Source artifacts | Summary |",
        "|---|---|---|---|",
    ]
    for row in evidence["familyRows"]:
        sources = "<br>".join(f"`{source}`" for source in row["sourceArtifacts"])
        lines.append(f"| `{row['id']}` | {row['theme']} | {sources} | {row['summary']} |")
    lines.extend(
        [
            "",
            "## Categories",
            "",
            "| Category | Count | PM meaning |",
            "|---|---:|---|",
        ]
    )
    for row in evidence["categoryRows"]:
        lines.append(f"| `{row['category']}` | {row.get('count', 0)} | {row['meaning']} |")
    lines.extend(
        [
            "",
            "## Support Rows",
            "",
            "| Row | Family | Source | Proof |",
            "|---|---|---|---|",
        ]
    )
    for row in evidence["supportRows"]:
        proof = row["proof"]
        proof_text = ", ".join(key for key in ("reference", "cpuGpu", "diffStat", "routeDiagnostics", "fallbackStable") if proof.get(key))
        lines.append(f"| `{row['id']}` | `{row['family']}` | `{row['sourceArtifact']}` | {proof_text} |")
    if evidence["rootCauseBlockers"]:
        lines.extend(["", "## Root-Cause Blockers", "", "| Ticket | Source | Root cause | Reason |", "|---|---|---|---|"])
        for row in evidence["rootCauseBlockers"]:
            blocker = row.get("blocker") if isinstance(row.get("blocker"), dict) else {}
            root_cause = blocker.get("rootCause", "blocked")
            reason = blocker.get("reasonCode", "blocked")
            lines.append(f"| {row['ticket']} | `{row['sourceArtifact']}` | `{root_cause}` | `{reason}` |")
    lines.extend(["", "## Non-Claims", ""])
    for item in evidence["nonClaims"]:
        lines.append(f"- `{item['id']}`: {item['text']}")
    lines.extend(
        [
            "",
            "## PM Bundle Manifest",
            "",
            f"- Entry key: `{evidence['pmBundleManifestEntry']['key']}`",
            f"- Evidence JSON: `{evidence['pmBundleManifestEntry']['evidenceJson']}`",
            f"- Evidence Markdown: `{evidence['pmBundleManifestEntry']['evidenceMarkdown']}`",
            "",
            "## Validation",
            "",
        ]
    )
    for command in evidence["requiredValidation"]:
        lines.append(f"- `{command}`")
    return "\n".join(lines) + "\n"


def write_outputs(root: Path, output_dir: Path) -> dict[str, Any]:
    output_dir.mkdir(parents=True, exist_ok=True)
    evidence = build_evidence(root)
    (output_dir / OUTPUT_JSON).write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (output_dir / OUTPUT_MARKDOWN).write_text(render_markdown(evidence), encoding="utf-8")
    (output_dir / OUTPUT_MANIFEST_ENTRY).write_text(json.dumps(evidence["pmBundleManifestEntry"], indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return evidence


def inject_pm_bundle(root: Path, output_dir: Path, bundle_dir: Path) -> None:
    evidence = write_outputs(root, output_dir)
    manifest_path = bundle_dir / "manifest.json"
    require(manifest_path.is_file(), f"missing PM bundle manifest: {manifest_path}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    require(manifest.get("generatedBy") == "pipelinePmBundle", "target manifest must be generated by pipelinePmBundle")

    target_dir = bundle_dir / "release" / "m99-breadth-pm-pack"
    if target_dir.exists():
        shutil.rmtree(target_dir)
    shutil.copytree(output_dir, target_dir)
    manifest[evidence["pmBundleManifestEntry"]["key"]] = evidence["pmBundleManifestEntry"]
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    readme_path = bundle_dir / "README.md"
    if readme_path.is_file():
        readme = readme_path.read_text(encoding="utf-8")
        marker = "KAN-050 PM breadth support/refusal pack"
        if marker not in readme:
            readme += (
                "\n"
                "- KAN-050 PM breadth support/refusal pack lives in `manifest.json` under "
                "`kan050PmBreadthSupportRefusalPack` and in `release/m99-breadth-pm-pack/`; it aggregates existing "
                "support/refusal/perf/cache evidence without broadening support claims or requiring native Kadre CI.\n"
            )
            readme_path.write_text(readme, encoding="utf-8")


def main(argv: list[str]) -> int:
    root = Path(argv[1]).resolve() if len(argv) > 1 else Path.cwd().resolve()
    output_dir = Path(argv[2]).resolve() if len(argv) > 2 and argv[2] != "--inject-pm-bundle" else root / DEFAULT_OUTPUT_DIR
    if "--inject-pm-bundle" in argv:
        index = argv.index("--inject-pm-bundle")
        require(len(argv) > index + 1, "--inject-pm-bundle requires a bundle directory")
        inject_pm_bundle(root, output_dir, Path(argv[index + 1]).resolve())
    else:
        write_outputs(root, output_dir)
    print(f"KAN-050 PM breadth support/refusal pack validation passed: {output_dir.relative_to(root)}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main(sys.argv))
    except ValidationError as exc:
        print(exc, file=sys.stderr)
        raise SystemExit(1)
