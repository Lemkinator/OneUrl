import re
import sys
from pathlib import Path

IGNORE_ENTRY = re.compile(r"^\s+-\s+[\"']?([^\"'#]+?)[\"']?\s*(#.*)?$")


def ignored_paths(text):
    paths = []
    in_ignore = False
    for line in text.splitlines():
        if re.match(r"^ignore:\s*$", line):
            in_ignore = True
        elif in_ignore and (match := IGNORE_ENTRY.match(line)):
            paths.append(match.group(1))
        elif in_ignore and line.strip() and not line.lstrip().startswith("#"):
            in_ignore = False
    return paths


def missing_paths(root, text):
    return [path for path in ignored_paths(text) if not (Path(root) / path).exists()]


def main():
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".")
    text = (root / "codecov.yml").read_text(encoding="utf-8")
    missing = missing_paths(root, text)
    for path in missing:
        print(f"codecov.yml ignores a path that does not exist: {path}", file=sys.stderr)
    sys.exit(1 if missing else 0)


if __name__ == "__main__":
    main()
