import io
import unittest
from unittest import mock

import count_junit_tests

OBJECT = "Ljava/lang/Object;"


def dex_class(descriptor, flags="0x0001", superclass=OBJECT, tests=(), test_suffix=""):
    text = "Class #0 annotations:\nAnnotations on class\n  VISIBILITY_RUNTIME Lkotlin/Metadata; d1={ \"x\" }\n"
    for index, name in enumerate(tests):
        text += f"Annotations on method #{index + 3} '{name}'\n"
        text += f"  VISIBILITY_RUNTIME Lorg/junit/Test;{test_suffix}\n"
    text += "Annotations on field #1 'rule'\n  VISIBILITY_RUNTIME Lorg/junit/Rule; order=0\n"
    text += f"\nClass #0            -\n  Class descriptor  : '{descriptor}'\n"
    text += f"  Access flags      : {flags}\n  Superclass        : '{superclass}'\n"
    return text


def dex(*classes):
    return "Processing 'classes.dex'...\nOpened 'classes.dex', DEX version '038'\n" + "".join(classes)


def count(text):
    with mock.patch("sys.stdin", io.StringIO(text)), mock.patch("sys.stdout", io.StringIO()) as out:
        count_junit_tests.main()
    return int(out.getvalue())


class CountJunitTestsTest(unittest.TestCase):
    NINE = [f"t{i}" for i in range(9)]

    def test_empty_input_counts_zero(self):
        self.assertEqual(0, count(""))

    def test_abstract_contract_without_subclass_counts_zero(self):
        self.assertEqual(0, count(dex(dex_class("Lx/Contract;", "0x0421", tests=self.NINE))))

    def test_inherited_only_subclass_in_another_dex_counts_contract_tests(self):
        contract = dex(dex_class("Lx/Contract;", "0x0421", tests=self.NINE))
        subclass = dex(dex_class("Lx/Sub;", superclass="Lx/Contract;"))
        self.assertEqual(9, count(contract + subclass))

    def test_own_tests_add_to_inherited_tests(self):
        text = dex(
            dex_class("Lx/Contract;", "0x0421", tests=self.NINE),
            dex_class("Lx/Sub;", superclass="Lx/Contract;", tests=["own"]),
        )
        self.assertEqual(10, count(text))

    def test_override_of_inherited_test_counts_once(self):
        text = dex(
            dex_class("Lx/Contract;", "0x0421", tests=self.NINE),
            dex_class("Lx/Sub;", superclass="Lx/Contract;", tests=["t0"]),
        )
        self.assertEqual(9, count(text))

    def test_test_with_element_values_counts(self):
        text = dex(dex_class("Lx/T;", tests=["a", "b"], test_suffix=" timeout=1000"))
        self.assertEqual(2, count(text))

    def test_nested_class_counts_zero(self):
        self.assertEqual(0, count(dex(dex_class("Lx/T$Inner;", tests=["a"]))))

    def test_junit_package_class_counts_zero(self):
        self.assertEqual(0, count(dex(dex_class("Lorg/junit/Foo;", tests=["a"]))))

    def test_interface_counts_zero(self):
        self.assertEqual(0, count(dex(dex_class("Lx/I;", "0x0601", tests=["a"]))))

    def test_missing_superclass_stops_chain(self):
        text = dex(dex_class("Lx/T;", superclass="Landroid/Missing;", tests=["a"]))
        self.assertEqual(1, count(text))


if __name__ == "__main__":
    unittest.main()
