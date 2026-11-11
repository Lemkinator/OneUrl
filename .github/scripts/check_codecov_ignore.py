import re
import subprocess
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    sys.exit("check_codecov_ignore.py needs PyYAML: install python3-yaml")

REPO_ROOT = Path(__file__).resolve().parents[2]
GLOB_CHARS = frozenset("*?[")


def ignored_paths(text):
    try:
        config = yaml.safe_load(text)
    except yaml.YAMLError as error:
        raise ValueError(f"codecov.yml is not valid YAML: {error}") from error
    if not isinstance(config, dict) or "ignore" not in config:
        return []
    entries = config["ignore"]
    if not isinstance(entries, list) or not entries:
        raise ValueError("codecov.yml has an ignore key but no entry could be parsed")
    if not all(isinstance(entry, str) and entry.strip() for entry in entries):
        raise ValueError("codecov.yml ignore entries must be non-empty strings")
    return [entry.strip() for entry in entries]


def glob_regex(pattern):
    out = []
    i = 0
    while i < len(pattern):
        char = pattern[i]
        if char == "*" and pattern.startswith("**", i):
            i += 2
            if pattern.startswith("/", i):
                i += 1
                out.append("(?:.*/)?")
            else:
                out.append(".*")
            continue
        if char == "*":
            out.append("[^/]*")
        elif char == "?":
            out.append("[^/]")
        elif char == "[" and (end := pattern.find("]", i + 2)) != -1:
            body = pattern[i + 1 : end]
            negate = body.startswith("!")
            body = body[1:] if negate else body
            out.append("[" + ("^" if negate else "") + body.replace("\\", "\\\\") + "]")
            i = end
        else:
            out.append(re.escape(char))
        i += 1
    return re.compile("".join(out) + "(?:/.*)?")


def matches_tracked(entry, tracked):
    if GLOB_CHARS & set(entry):
        regex = glob_regex(entry)
        return any(regex.fullmatch(path) for path in tracked)
    prefix = entry.rstrip("/") + "/"
    return any(path == entry or path.startswith(prefix) for path in tracked)


def git_tracked_files(root):
    result = subprocess.run(
        ["git", "ls-files", "-z"], cwd=root, check=True, capture_output=True, text=True, encoding="utf-8"
    )
    return [path for path in result.stdout.split("\0") if path]


def missing_paths(text, tracked):
    return [entry for entry in ignored_paths(text) if not matches_tracked(entry, tracked)]


def main(argv=None, tracked_files=git_tracked_files):
    args = sys.argv[1:] if argv is None else argv
    root = Path(args[0]) if args else REPO_ROOT
    text = (root / "codecov.yml").read_text(encoding="utf-8")
    try:
        missing = missing_paths(text, tracked_files(root))
    except ValueError as error:
        print(error, file=sys.stderr)
        return 1
    for path in missing:
        print(f"codecov.yml ignores a path that matches no tracked file: {path}", file=sys.stderr)
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
