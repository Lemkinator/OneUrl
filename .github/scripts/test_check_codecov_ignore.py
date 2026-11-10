import tempfile
import unittest
from pathlib import Path

import check_codecov_ignore

REPO_ROOT = Path(__file__).resolve().parents[2]
CODECOV = "ignore:\n  - \"app/src/Present.kt\"\n  - app/src/Gone.kt\n"


class CheckCodecovIgnoreTest(unittest.TestCase):
    def test_reads_quoted_and_plain_entries(self):
        self.assertEqual(["app/src/Present.kt", "app/src/Gone.kt"], check_codecov_ignore.ignored_paths(CODECOV))

    def test_entries_after_another_key_are_not_ignore_paths(self):
        text = "ignore:\n  - a/B.kt\ncoverage:\n  - c/D.kt\n"
        self.assertEqual(["a/B.kt"], check_codecov_ignore.ignored_paths(text))

    def test_no_ignore_key_lists_nothing(self):
        self.assertEqual([], check_codecov_ignore.ignored_paths("coverage:\n  - c/D.kt\n"))

    def test_missing_path_is_reported(self):
        with tempfile.TemporaryDirectory() as root:
            present = Path(root) / "app/src/Present.kt"
            present.parent.mkdir(parents=True)
            present.write_text("")
            self.assertEqual(["app/src/Gone.kt"], check_codecov_ignore.missing_paths(root, CODECOV))

    def test_existing_paths_report_nothing(self):
        with tempfile.TemporaryDirectory() as root:
            for name in ("Present.kt", "Gone.kt"):
                file = Path(root) / "app/src" / name
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text("")
            self.assertEqual([], check_codecov_ignore.missing_paths(root, CODECOV))


if __name__ == "__main__":
    unittest.main()
