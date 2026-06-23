#!/usr/bin/env python3
import json
import sys
from pathlib import Path
from typing import Any


ARTIFACT_PATH = "reports/pure-kotlin-text/facade-adapter-inventory.json"
REPORT_PATH = "reports/pure-kotlin-text/2026-06-19-kfont-m13-001-facade-adapter-inventory.md"
DASHBOARD_PATH = "reports/pure-kotlin-text/font-claim-dashboard.json"
TAXONOMY_PATH = "reports/pure-kotlin-text/font-diagnostic-taxonomy.json"
BUILD_GRADLE_PATH = "build.gradle.kts"
TASK_NAME = "validateKfontM13001FacadeInventory"
SURFACE_ID = "skia-facade-adapter-inventory"
REQUIRED_ROUTE_IDS = [
    "paragraph-compatible-apis",
    "skcanvas-drawstring-simple-text",
    "skfont-metrics-glyph-query",
    "skfontmgr-catalog",
    "skshaper-explicit-shaping",
    "sktextblob-glyph-runs",
    "sktypeface-opentype-facts",
]
REQUIRED_LEGACY_GATES = [
    "coloremoji_blendmodes",
    "dftext",
    "fontations",
    "fontations_ft_compare",
    "pdf_never_embed",
    "scaledemoji",
    "scaledemoji_rendering",
]
ALLOWED_MIGRATION_CATEGORIES = [
    "reuse-as-is",
    "promote-with-contract",
    "replace",
    "keep-current-gate",
    "expected-unsupported",
]
ALLOWED_CLASSIFICATIONS = [
    "target-supported",
    "current-supported",
    "tracked-gap",
    "DependencyGated",
    "fixture-gated",
    "GPU-gated",
    "expected-unsupported",
    "drift-only",
]
ALLOWED_DIAGNOSTIC_FAMILIES = [
    "font.catalog",
    "font.source",
    "font.sfnt",
    "font.scaler",
    "text.shaping",
    "text.paragraph",
    "text.glyph",
    "glyph.artifact",
    "text.gpu",
    "expected-unsupported",
]


class ValidationError(RuntimeError):
    pass


def fail(message: str) -> None:
    raise ValidationError(f"KFONT-M13-001 facade inventory validation failed: {message}")


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def project_root() -> Path:
    if len(sys.argv) > 1:
        return Path(sys.argv[1]).resolve()
    return Path(__file__).resolve().parents[1]


def load_json(root: Path, relative_path: str) -> Any:
    path = root / relative_path
    require(path.is_file(), f"missing JSON file: {relative_path}")
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail(f"invalid JSON in {relative_path}: {exc}")


def load_text(root: Path, relative_path: str) -> str:
    path = root / relative_path
    require(path.is_file(), f"missing text file: {relative_path}")
    return path.read_text(encoding="utf-8")


def require_string(value: Any, label: str) -> str:
    require(isinstance(value, str) and value.strip() == value and value, f"{label} must be a non-empty trimmed string")
    return value


def require_string_list(value: Any, label: str, *, allow_empty: bool = False) -> list[str]:
    require(isinstance(value, list), f"{label} must be a list")
    if not allow_empty:
        require(value, f"{label} must be non-empty")
    for index, item in enumerate(value):
        require_string(item, f"{label}[{index}]")
    return value


def validate_route(route: Any, route_ids: set[str]) -> dict[str, Any]:
    require(isinstance(route, dict), "each route must be an object")
    route_id = require_string(route.get("routeId"), "route.routeId")
    route_ids.add(route_id)
    require(route_id in REQUIRED_ROUTE_IDS, f"unknown routeId `{route_id}`")
    require_string(route.get("facadeApi"), f"{route_id}.facadeApi")
    facade_surface = require_string(route.get("facadeSurface"), f"{route_id}.facadeSurface")
    require_string(route.get("targetContract"), f"{route_id}.targetContract")
    require_string(route.get("targetOwner"), f"{route_id}.targetOwner")
    migration_category = require_string(route.get("migrationCategory"), f"{route_id}.migrationCategory")
    require(migration_category in ALLOWED_MIGRATION_CATEGORIES, f"{route_id} has unknown migrationCategory `{migration_category}`")
    classification = require_string(route.get("classification"), f"{route_id}.classification")
    require(classification in ALLOWED_CLASSIFICATIONS, f"{route_id} has unknown classification `{classification}`")
    legacy_gates = require_string_list(route.get("legacyGates"), f"{route_id}.legacyGates", allow_empty=True)
    for gate in legacy_gates:
        require(gate in REQUIRED_LEGACY_GATES, f"{route_id} references unknown legacy gate `{gate}`")
    target_families = require_string_list(route.get("targetDiagnosticFamilies"), f"{route_id}.targetDiagnosticFamilies")
    for family in target_families:
        require(family in ALLOWED_DIAGNOSTIC_FAMILIES, f"{route_id} references unknown diagnostic family `{family}`")
    require_string_list(route.get("blockingTickets"), f"{route_id}.blockingTickets", allow_empty=True)
    require_string_list(route.get("requiredEvidence"), f"{route_id}.requiredEvidence")
    notes = require_string(route.get("notes"), f"{route_id}.notes")

    if route_id == "skcanvas-drawstring-simple-text":
        require("simple" in facade_surface.lower(), "drawString facadeSurface must keep the simple-route wording visible")
        require("simple" in notes.lower() and "deterministic" in notes.lower(), "drawString notes must mention the simple deterministic boundary")
    if route_id == "paragraph-compatible-apis":
        require("org.skia.paragraph" in facade_surface, "paragraph route must record the missing public org.skia.paragraph facade")
    if route_id == "sktypeface-opentype-facts":
        require("fontations" in notes.lower(), "SkTypeface route must keep Fontations drift-only wording visible")

    return route


def task_block(build_gradle: str, marker: str) -> str:
    start = build_gradle.find(marker)
    require(start >= 0, f"missing Gradle marker: {marker}")
    block_chars: list[str] = []
    depth = 0
    seen_open = False
    for char in build_gradle[start:]:
        block_chars.append(char)
        if char == "{":
            depth += 1
            seen_open = True
        elif char == "}":
            depth -= 1
            if seen_open and depth == 0:
                return "".join(block_chars)
    fail(f"unterminated Gradle block: {marker}")


def validate_inventory(
    root: Path,
    artifact: dict[str, Any],
    dashboard: dict[str, Any],
    taxonomy: dict[str, Any],
    report: str,
    build_gradle: str,
) -> None:
    require(isinstance(artifact, dict), "facade inventory root must be an object")
    require(artifact.get("schema") == "org.graphiks.kanvas.font.FacadeAdapterInventory.v1", "schema changed")
    require(artifact.get("schemaVersion") == 1, "schemaVersion must stay 1")
    require(artifact.get("artifactId") == "facade-adapter-inventory", "artifactId changed")
    require(artifact.get("ticketId") == "KFONT-M13-001", "ticketId changed")
    require(artifact.get("dashboardSurfaceId") == SURFACE_ID, "dashboardSurfaceId changed")
    require(artifact.get("pmBundleTask") == "pipelinePmBundle", "pmBundleTask changed")
    require(artifact.get("migrationCategories") == ALLOWED_MIGRATION_CATEGORIES, "migrationCategories changed")
    require_string_list(artifact.get("nonClaims"), "nonClaims")
    require("no-legacy-gate-retirement" in artifact["nonClaims"], "nonClaims must keep the no-legacy-gate-retirement guard")
    require_string_list(artifact.get("validationCommands"), "validationCommands")
    require("rtk ./gradlew --no-daemon validateKfontM13001FacadeInventory" in artifact["validationCommands"], "validationCommands must mention validateKfontM13001FacadeInventory")

    source_specs = artifact.get("sourceSpecs")
    require(isinstance(source_specs, list) and source_specs, "sourceSpecs must be a non-empty list")
    for index, row in enumerate(source_specs):
        require(isinstance(row, dict), f"sourceSpecs[{index}] must be an object")
        path = require_string(row.get("path"), f"sourceSpecs[{index}].path")
        section = require_string(row.get("section"), f"sourceSpecs[{index}].section")
        require((root / path).is_file(), f"missing source spec path: {path}")
        require(section, f"sourceSpecs[{index}] must carry a non-empty section")

    routes = artifact.get("routes")
    require(isinstance(routes, list) and routes, "routes must be a non-empty list")
    route_ids: set[str] = set()
    route_order = []
    classification_counts: dict[str, int] = {}
    migration_counts: dict[str, int] = {}
    for route in routes:
        validated = validate_route(route, route_ids)
        route_order.append(validated["routeId"])
        classification_counts[validated["classification"]] = classification_counts.get(validated["classification"], 0) + 1
        migration_counts[validated["migrationCategory"]] = migration_counts.get(validated["migrationCategory"], 0) + 1
    require(route_order == sorted(route_order), "routes must be sorted by routeId")
    require(route_order == REQUIRED_ROUTE_IDS, "route coverage/order changed")

    summary = artifact.get("summary")
    require(isinstance(summary, dict), "summary must be an object")
    require(summary.get("routeCount") == len(REQUIRED_ROUTE_IDS), "summary.routeCount changed")
    require(summary.get("classificationCounts") == classification_counts, "summary.classificationCounts mismatch")
    require(summary.get("migrationCategoryCounts") == migration_counts, "summary.migrationCategoryCounts mismatch")

    gate_rows = artifact.get("legacyGateCoverage")
    require(isinstance(gate_rows, list) and gate_rows, "legacyGateCoverage must be a non-empty list")
    gate_order = []
    for row in gate_rows:
        require(isinstance(row, dict), "legacyGateCoverage rows must be objects")
        gate = require_string(row.get("gate"), "legacyGateCoverage.gate")
        gate_order.append(gate)
        require(gate in REQUIRED_LEGACY_GATES, f"unknown legacy gate `{gate}`")
        coverage_kind = require_string(row.get("coverageKind"), f"{gate}.coverageKind")
        require(coverage_kind in {"route-owned", "adjacent-out-of-scope"}, f"{gate} has unknown coverageKind `{coverage_kind}`")
        route_refs = require_string_list(row.get("routeIds"), f"{gate}.routeIds", allow_empty=True)
        for route_id in route_refs:
            require(route_id in route_ids, f"{gate} references unknown routeId `{route_id}`")
        if coverage_kind == "adjacent-out-of-scope":
            require(not route_refs, f"{gate} adjacent-out-of-scope rows must not claim a facade route owner")
        require_string(row.get("rationale"), f"{gate}.rationale")
    require(gate_order == REQUIRED_LEGACY_GATES, "legacyGateCoverage must be sorted and complete")

    remaining_gates = require_string_list(artifact.get("remainingGates"), "remainingGates")
    require(any("KFONT-M13-003" in gate and "KFONT-M6-010" in gate for gate in remaining_gates), "remainingGates must keep the KFONT-M13-003 / KFONT-M6-010 shaping gate")
    require(any("pdf_never_embed" in gate for gate in remaining_gates), "remainingGates must keep pdf_never_embed visible")

    require(isinstance(dashboard, dict), "dashboard root must be an object")
    surface_rows = dashboard.get("surfaceRows")
    require(isinstance(surface_rows, list), "dashboard surfaceRows must be a list")
    row = next((item for item in surface_rows if isinstance(item, dict) and item.get("surfaceId") == SURFACE_ID), None)
    require(isinstance(row, dict), f"dashboard must contain `{SURFACE_ID}`")
    require(row.get("classification") == "tracked-gap", f"{SURFACE_ID} must stay tracked-gap")
    require(row.get("claimPromotionAllowed") is False, f"{SURFACE_ID} must keep claimPromotionAllowed=false")
    deterministic_dumps = row.get("evidence", {}).get("deterministicDumps", [])
    refusal_diagnostics = row.get("evidence", {}).get("refusalDiagnostics", [])
    require(ARTIFACT_PATH in deterministic_dumps, f"{SURFACE_ID} must reference facade-adapter-inventory.json")
    require(REPORT_PATH in deterministic_dumps, f"{SURFACE_ID} must reference the markdown inventory report")
    require(TAXONOMY_PATH in refusal_diagnostics, f"{SURFACE_ID} must reference font-diagnostic-taxonomy.json")

    require(isinstance(taxonomy, dict), "taxonomy root must be an object")
    namespaces = taxonomy.get("acceptedNamespaces")
    require(isinstance(namespaces, list), "taxonomy acceptedNamespaces must be a list")
    require("text.paragraph" in namespaces and "text.shaping" in namespaces and "glyph.artifact" in namespaces, "taxonomy must keep paragraph, shaping, and glyph artifact namespaces")

    require("tracked-gap" in report, "markdown report must mention tracked-gap")
    require("pipelinePmBundle" in report, "markdown report must mention pipelinePmBundle")
    require("org.skia.paragraph" in report, "markdown report must mention the missing public org.skia.paragraph facade")
    require("SkCanvas.drawString" in report, "markdown report must mention SkCanvas.drawString")
    require("pdf_never_embed" in report, "markdown report must mention pdf_never_embed")

    require(f'tasks.register<Exec>("{TASK_NAME}")' in build_gradle, "build.gradle.kts must register validateKfontM13001FacadeInventory")
    scene_block = task_block(build_gradle, 'tasks.register("pipelineSceneDashboardGate")')
    pm_bundle_block = task_block(build_gradle, 'tasks.register("pipelinePmBundle")')
    require(f'"{TASK_NAME}"' in scene_block, "pipelineSceneDashboardGate must depend on validateKfontM13001FacadeInventory")
    require(f'"{TASK_NAME}"' in pm_bundle_block, "pipelinePmBundle must depend on validateKfontM13001FacadeInventory")
    require(ARTIFACT_PATH in pm_bundle_block, "pipelinePmBundle must include facade-adapter-inventory.json")
    require(REPORT_PATH in pm_bundle_block, "pipelinePmBundle must include the markdown inventory report")
    require(TAXONOMY_PATH in pm_bundle_block, "pipelinePmBundle must include font-diagnostic-taxonomy.json")


def main() -> int:
    root = project_root()
    artifact = load_json(root, ARTIFACT_PATH)
    dashboard = load_json(root, DASHBOARD_PATH)
    taxonomy = load_json(root, TAXONOMY_PATH)
    report = load_text(root, REPORT_PATH)
    build_gradle = load_text(root, BUILD_GRADLE_PATH)
    validate_inventory(root, artifact, dashboard, taxonomy, report, build_gradle)

    print("KFONT-M13-001 facade inventory validated")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ValidationError as exc:
        print(exc, file=sys.stderr)
        raise SystemExit(1)
