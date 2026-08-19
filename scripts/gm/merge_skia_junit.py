#!/usr/bin/env python3
"""Merge chunked Skia JUnit XML without modifying the source files."""

import argparse
import pathlib
import xml.etree.ElementTree as ET


def _local_name(tag):
    return tag.rsplit("}", 1)[-1]


def _suite_elements(root):
    if _local_name(root.tag) == "testsuite":
        return [root]
    if _local_name(root.tag) == "testsuites":
        return [child for child in root if _local_name(child.tag) == "testsuite"]
    raise ValueError(f"unsupported JUnit root element: {root.tag}")


def _aggregate_counts(suites):
    counts = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    for suite in suites:
        for testcase in suite:
            if _local_name(testcase.tag) != "testcase":
                continue
            counts["tests"] += 1
            outcomes = {_local_name(child.tag) for child in testcase}
            for outcome, count_key in (
                ("failure", "failures"),
                ("error", "errors"),
                ("skipped", "skipped"),
            ):
                if outcome in outcomes:
                    counts[count_key] += 1
    return counts


def merge_junit_files(input_paths, output_path):
    paths = sorted(pathlib.Path(path) for path in input_paths)
    suites = []
    for path in paths:
        if not path.is_file():
            raise FileNotFoundError(path)
        suites.extend(_suite_elements(ET.parse(path).getroot()))

    counts = _aggregate_counts(suites)
    merged_root = ET.Element("testsuites", {key: str(value) for key, value in counts.items()})
    for suite in suites:
        merged_root.append(suite)

    output = pathlib.Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    tree = ET.ElementTree(merged_root)
    ET.indent(tree, space="  ")
    tree.write(output, encoding="utf-8", xml_declaration=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    parser.add_argument("inputs", nargs="+", type=pathlib.Path)
    args = parser.parse_args()
    merge_junit_files(args.inputs, args.output)


if __name__ == "__main__":
    main()
