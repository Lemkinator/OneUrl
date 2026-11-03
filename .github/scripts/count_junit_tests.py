import re
import sys

ACC_ABSTRACT = 0x0400

CLASS_ANNOTATIONS = re.compile(r"^Class #\d+ annotations:")
METHOD_ANNOTATIONS = re.compile(r"^Annotations on method #\d+ '(.*)'$")
ANNOTATIONS_OTHER = re.compile(r"^Annotations on (class|field|parameter)")
TEST_ANNOTATION = "  VISIBILITY_RUNTIME Lorg/junit/Test;"
UNRUN_PACKAGES = ("Lorg/junit/", "Lorg/hamcrest/", "Lorg/mockito/")
CLASS_DESCRIPTOR = re.compile(r"^  Class descriptor\s*: '(.*)'")
ACCESS_FLAGS = re.compile(r"^  Access flags\s*: 0x([0-9a-fA-F]+)")
SUPERCLASS = re.compile(r"^  Superclass\s*: '(.*)'")


class DexClass:
    def __init__(self, descriptor, test_methods):
        self.descriptor = descriptor
        self.test_methods = test_methods
        self.flags = 0
        self.superclass = None


def parse(lines):
    classes = {}
    pending = set()
    method = None
    current = None
    for line in lines:
        line = line.rstrip("\n")
        if CLASS_ANNOTATIONS.match(line):
            pending = set()
            method = None
        elif (match := METHOD_ANNOTATIONS.match(line)):
            method = match.group(1)
        elif ANNOTATIONS_OTHER.match(line):
            method = None
        elif (line == TEST_ANNOTATION or line.startswith(TEST_ANNOTATION + " ")) and method is not None:
            pending.add(method)
        elif (match := CLASS_DESCRIPTOR.match(line)):
            current = DexClass(match.group(1), pending)
            classes[current.descriptor] = current
            pending = set()
            method = None
        elif current is not None and (match := ACCESS_FLAGS.match(line)):
            current.flags = int(match.group(1), 16)
        elif current is not None and (match := SUPERCLASS.match(line)):
            current.superclass = match.group(1)
    return classes


def is_run(cls):
    return (
        not cls.flags & ACC_ABSTRACT
        and "$" not in cls.descriptor
        and not cls.descriptor.startswith(UNRUN_PACKAGES)
    )


def runnable_tests(cls, classes):
    names = set()
    seen = set()
    while cls is not None and cls.descriptor not in seen:
        seen.add(cls.descriptor)
        names |= cls.test_methods
        cls = classes.get(cls.superclass)
    return len(names)


def main():
    classes = parse(sys.stdin)
    print(sum(runnable_tests(cls, classes) for cls in classes.values() if is_run(cls)))


if __name__ == "__main__":
    main()
