import tempfile
import unittest
from pathlib import Path
from xml.etree import ElementTree

from scripts.gm.merge_skia_junit import merge_junit_files


class MergeSkiaJunitTest(unittest.TestCase):
    def test_merges_suites_in_sorted_order_and_recomputes_counts(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            first = root / "b.xml"
            second = root / "a.xml"
            output = root / "merged.xml"
            first.write_text(
                """<?xml version=\"1.0\"?>
<testsuite name=\"suite-b\" tests=\"99\" failures=\"99\">
  <testcase name=\"failed\" classname=\"gm\"><failure message=\"bad\">details</failure></testcase>
  <testcase name=\"errored\" classname=\"gm\"><error message=\"oops\">trace</error></testcase>
</testsuite>
""",
                encoding="utf-8",
            )
            second.write_text(
                """<?xml version=\"1.0\"?>
<testsuites tests=\"0\"><testsuite name=\"suite-a\" tests=\"0\">
  <testcase name=\"skipped\" classname=\"gm\"><skipped message=\"blocked\" /></testcase>
</testsuite></testsuites>
""",
                encoding="utf-8",
            )

            merge_junit_files([first, second], output)

            merged = ElementTree.parse(output).getroot()
            self.assertEqual("testsuites", merged.tag)
            self.assertEqual(["suite-a", "suite-b"], [suite.get("name") for suite in merged])
            self.assertEqual("3", merged.get("tests"))
            self.assertEqual("1", merged.get("failures"))
            self.assertEqual("1", merged.get("errors"))
            self.assertEqual("1", merged.get("skipped"))
            self.assertEqual("failed", merged[1][0].get("name"))
            self.assertEqual("details", merged[1][0][0].text)

    def test_rejects_missing_input_without_creating_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "merged.xml"

            with self.assertRaises(FileNotFoundError):
                merge_junit_files([root / "missing.xml"], output)

            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
