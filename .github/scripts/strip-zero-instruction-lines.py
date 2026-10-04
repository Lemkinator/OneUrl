import sys
import xml.etree.ElementTree as ET

COUNTER_ATTRIBUTES = ("mi", "ci", "mb", "cb")


def is_zero_instruction_line(line):
    return all(line.get(name) == "0" for name in COUNTER_ATTRIBUTES)


def strip_report(path):
    try:
        tree = ET.parse(path)
    except (ET.ParseError, OSError) as error:
        sys.exit(f"{path}: {error}")
    kept = removed = 0
    for sourcefile in tree.iter("sourcefile"):
        for line in sourcefile.findall("line"):
            if is_zero_instruction_line(line):
                sourcefile.remove(line)
                removed += 1
            else:
                kept += 1
    tree.write(path, encoding="UTF-8", xml_declaration=True)
    print(f"{path}: removed {removed} zero-instruction lines, kept {kept}")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit("usage: strip-zero-instruction-lines.py REPORT.xml [REPORT.xml ...]")
    for report in sys.argv[1:]:
        strip_report(report)
