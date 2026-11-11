import contextlib
import io
import tempfile
import unittest
from pathlib import Path

import check_codecov_ignore as guard

REPO_ROOT = Path(__file__).resolve().parents[2]
TRACKED = ["app/src/Present.kt", "app/src/main/kotlin/de/Foo.kt", "docs/readme.md"]


def run_main(codecov, tracked):
    with tempfile.TemporaryDirectory() as root:
        (Path(root) / "codecov.yml").write_text(codecov, encoding="utf-8")
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            code = guard.main([root], tracked_files=lambda _: tracked)
        return code, stderr.getvalue()


class IgnoredPathsTest(unittest.TestCase):
    def test_reads_quoted_and_plain_block_entries(self):
        text = 'ignore:\n  - "app/src/Present.kt"\n  - app/src/Gone.kt\n'
        self.assertEqual(["app/src/Present.kt", "app/src/Gone.kt"], guard.ignored_paths(text))

    def test_entries_after_another_key_are_not_ignore_paths(self):
        text = "ignore:\n  - a/B.kt\ncoverage:\n  - c/D.kt\n"
        self.assertEqual(["a/B.kt"], guard.ignored_paths(text))

    def test_no_ignore_key_lists_nothing(self):
        self.assertEqual([], guard.ignored_paths("coverage:\n  - c/D.kt\n"))

    def test_reads_flow_list(self):
        self.assertEqual(["a/B.kt", "c/D.kt"], guard.ignored_paths("ignore: [\"a/B.kt\", 'c/D.kt']\n"))

    def test_ignore_key_without_parsable_entry_fails(self):
        for text in ("ignore:\n", "ignore: []\n", "ignore: a/B.kt\n", "ignore:\n  key: a/B.kt\n"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                guard.ignored_paths(text)


class MissingPathsTest(unittest.TestCase):
    def missing(self, *entries):
        text = "ignore:\n" + "".join(f"  - {entry}\n" for entry in entries)
        return guard.missing_paths(text, TRACKED)

    def test_tracked_file_is_found(self):
        self.assertEqual([], self.missing("app/src/Present.kt"))

    def test_untracked_file_is_reported(self):
        self.assertEqual(["app/src/Untracked.kt"], self.missing("app/src/Untracked.kt"))

    def test_tracked_directory_prefix_is_found(self):
        self.assertEqual([], self.missing("app/src/main", "docs/"))

    def test_partial_directory_name_is_reported(self):
        self.assertEqual(["app/sr"], self.missing("app/sr"))

    def test_double_star_crosses_directories(self):
        self.assertEqual([], self.missing("app/**/Foo.kt", "**/Foo.kt", "app/**"))

    def test_single_star_stays_within_a_directory(self):
        self.assertEqual([], self.missing("app/src/*.kt"))
        self.assertEqual(["app/*/Foo.kt"], self.missing("app/*/Foo.kt"))

    def test_question_mark_and_class_match_one_character(self):
        self.assertEqual([], self.missing("docs/read?e.md", "docs/[a-z]eadme.md", "docs/[!x]eadme.md"))
        self.assertEqual(
            ["docs/[x-z]eadme.md", "docs/??readme.md"], self.missing("docs/[x-z]eadme.md", "docs/??readme.md")
        )

    def test_glob_without_match_is_reported(self):
        self.assertEqual(["app/**/Bar.kt"], self.missing("app/**/Bar.kt"))


class MainTest(unittest.TestCase):
    def test_passes_when_every_entry_is_tracked(self):
        self.assertEqual((0, ""), run_main("ignore:\n  - app/**/Foo.kt\n", TRACKED))

    def test_fails_and_names_an_untracked_entry(self):
        code, stderr = run_main("ignore:\n  - app/src/Untracked.kt\n", TRACKED)
        self.assertEqual(1, code)
        self.assertEqual("codecov.yml ignores a path that matches no tracked file: app/src/Untracked.kt\n", stderr)

    def test_fails_on_unparsed_ignore_shape(self):
        code, stderr = run_main("ignore: app/src/Present.kt\n", TRACKED)
        self.assertEqual(1, code)
        self.assertEqual("codecov.yml has an ignore key but no entry could be parsed\n", stderr)

    def test_default_root_is_the_repository_root(self):
        self.assertEqual(REPO_ROOT, guard.REPO_ROOT)
        self.assertTrue((guard.REPO_ROOT / "codecov.yml").is_file())


class RealCodecovTest(unittest.TestCase):
    def test_real_codecov_yml_ignores_only_tracked_paths(self):
        text = (REPO_ROOT / "codecov.yml").read_text(encoding="utf-8")
        self.assertEqual([], guard.missing_paths(text, guard.git_tracked_files(REPO_ROOT)))


if __name__ == "__main__":
    unittest.main()
