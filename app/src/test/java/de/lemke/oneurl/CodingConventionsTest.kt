/*
 * Copyright 2023-2026 Leonard Lemke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.lemke.oneurl

import com.lemonappdev.konsist.api.KoModifier
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoClassDeclaration
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoInitBlockDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.ext.list.withAnnotationOf
import com.lemonappdev.konsist.api.verify.assertTrue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import de.lemke.commonutils.assertLaunchLatchConventions
import io.kotest.core.spec.style.ShouldSpec
import org.junit.Rule
import org.junit.rules.RuleChain

class CodingConventionsTest : ShouldSpec() {
    private val codeScope = Konsist.scopeFromProduction()

    init {
        should("launch activities and show dialogs only through the launch latch") {
            codeScope.assertLaunchLatchConventions(extraShowReceivers = setOf("AddFab"))
        }
        should("hilt tests declare exactly one outermost HiltTestRule and no raw HiltAndroidRule") {
            val ruleName = HiltTestRule::class.simpleName!!
            val rulePackage = HiltTestRule::class.java.packageName
            val ruleQualifiedName = "$rulePackage.$ruleName"
            val directConstruction =
                Regex("""^(${Regex.escape("$rulePackage.")})?${Regex.escape(ruleName)}\((testInstance\s*=\s*)?this\)$""")
            val forbiddenRuleTypes = setOf(HiltAndroidRule::class.qualifiedName!!, RuleChain::class.qualifiedName!!)
            Konsist
                .scopeFromTest()
                .classes()
                .withAnnotationOf(HiltAndroidTest::class)
                .assertTrue(
                    additionalMessage =
                        "Declare `@get:Rule(order = 0) val hiltRule = $ruleName(this)` ($ruleQualifiedName) as the only " +
                            "Hilt rule, ordered before every other rule. A RuleChain-wrapped, aliased or raw HiltAndroidRule " +
                            "and a @Rule function are not allowed.",
                    testName = this.testCase.name.toString(),
                ) { koClass ->
                    val file = koClass.containingFile
                    val resolvesToFixture =
                        file.packagee?.name == rulePackage ||
                            file.hasImport { it.name == ruleQualifiedName || (it.isWildcard && it.name.removeSuffix(".*") == rulePackage) }
                    val properties = koClass.properties(includeNested = false)
                    val constructsFixture = { property: KoPropertyDeclaration ->
                        val initializer = property.value?.trim()
                        initializer != null &&
                            directConstruction.matches(initializer) &&
                            (initializer.startsWith(ruleQualifiedName) || resolvesToFixture)
                    }
                    val hiltRule =
                        properties
                            .filter { property ->
                                val typeText = property.type?.text
                                typeText == ruleQualifiedName || (typeText == ruleName && resolvesToFixture) || constructsFixture(property)
                            }.singleOrNull()
                            ?.takeIf(constructsFixture)
                    val hiltOrder = hiltRule?.explicitRuleOrder()
                    val otherRuleOrders = properties.filter { it != hiltRule }.mapNotNull { it.ruleOrder() }
                    val outermost =
                        when {
                            hiltOrder == null -> false
                            koClass.fullyQualifiedName == HiltTestRuleTest::class.qualifiedName -> true
                            otherRuleOrders.isEmpty() -> hiltOrder == 0
                            else -> otherRuleOrders.all { hiltOrder < it }
                        }
                    outermost &&
                        koClass.functions(includeNested = false).none { function -> function.hasAnnotation { it.name == "Rule" } } &&
                        !file.hasImport { it.name == HiltAndroidRule::class.qualifiedName } &&
                        properties.none { property ->
                            listOfNotNull(property.type?.text, property.value).any { text ->
                                file.resolveReferences(text).any(forbiddenRuleTypes::contains)
                            }
                        }
                }
        }
        should("properties declared before functions in class body") {
            codeScope
                .classes()
                .assertTrue(testName = this.testCase.name.toString()) { koClass ->
                    val declarations = koClass.declarations(includeNested = false, includeLocal = false)
                    val lastPropertyIndex = declarations.indexOfLast { it is KoPropertyDeclaration }
                    val firstFunctionIndex = declarations.indexOfFirst { it is KoFunctionDeclaration }
                    lastPropertyIndex == -1 || firstFunctionIndex == -1 || lastPropertyIndex < firstFunctionIndex
                }
            codeScope
                .interfaces()
                .assertTrue(testName = this.testCase.name.toString()) { koInterface ->
                    val declarations = koInterface.declarations(includeNested = false, includeLocal = false)
                    val lastPropertyIndex = declarations.indexOfLast { it is KoPropertyDeclaration }
                    val firstFunctionIndex = declarations.indexOfFirst { it is KoFunctionDeclaration }
                    lastPropertyIndex == -1 || firstFunctionIndex == -1 || lastPropertyIndex < firstFunctionIndex
                }
        }
        should("init blocks declared before functions in class body") {
            codeScope
                .classes()
                .assertTrue(testName = this.testCase.name.toString()) { koClass ->
                    val declarations = koClass.declarations(includeNested = false, includeLocal = false)
                    val lastInitIndex = declarations.indexOfLast { it is KoInitBlockDeclaration }
                    val firstFunctionIndex = declarations.indexOfFirst { it is KoFunctionDeclaration }
                    lastInitIndex == -1 || firstFunctionIndex == -1 || lastInitIndex < firstFunctionIndex
                }
        }
        should("override functions declared before non-override functions in class body") {
            codeScope
                .classes()
                .assertTrue(testName = this.testCase.name.toString()) { koClass ->
                    val functions = koClass.functions(includeNested = false, includeLocal = false)
                    val lastOverrideIndex = functions.indexOfLast { it.hasModifier(KoModifier.OVERRIDE) }
                    val firstNonOverrideIndex = functions.indexOfFirst { !it.hasModifier(KoModifier.OVERRIDE) }
                    lastOverrideIndex == -1 || firstNonOverrideIndex == -1 || firstNonOverrideIndex > lastOverrideIndex
                }
            codeScope
                .interfaces()
                .assertTrue(testName = this.testCase.name.toString()) { koInterface ->
                    val functions = koInterface.functions(includeNested = false, includeLocal = false)
                    val lastOverrideIndex = functions.indexOfLast { it.hasModifier(KoModifier.OVERRIDE) }
                    val firstNonOverrideIndex = functions.indexOfFirst { !it.hasModifier(KoModifier.OVERRIDE) }
                    lastOverrideIndex == -1 || firstNonOverrideIndex == -1 || firstNonOverrideIndex > lastOverrideIndex
                }
        }
        should("companion object is the last non-class member in class body") {
            codeScope
                .classes()
                .assertTrue(testName = this.testCase.name.toString()) {
                    val declarations = it.declarations(includeNested = false, includeLocal = false)
                    val companion = it.objects(includeNested = false).lastOrNull { obj -> obj.hasModifier(KoModifier.COMPANION) }
                    companion == null ||
                        declarations.drop(declarations.indexOf(companion) + 1).none { decl ->
                            decl is KoPropertyDeclaration || decl is KoFunctionDeclaration || decl is KoInitBlockDeclaration
                        }
                }
            codeScope
                .interfaces()
                .assertTrue(testName = this.testCase.name.toString()) {
                    val declarations = it.declarations(includeNested = false, includeLocal = false)
                    val companion = it.objects(includeNested = false).lastOrNull { obj -> obj.hasModifier(KoModifier.COMPANION) }
                    companion == null ||
                        declarations.drop(declarations.indexOf(companion) + 1).none { decl ->
                            decl is KoPropertyDeclaration || decl is KoFunctionDeclaration || decl is KoInitBlockDeclaration
                        }
                }
        }
        should("non-private nested class declarations are last in class body") {
            codeScope
                .classes()
                .assertTrue(testName = this.testCase.name.toString()) {
                    val declarations = it.declarations(includeNested = false, includeLocal = false)
                    val firstNonPrivateClassTypeIndex =
                        declarations.indexOfFirst { decl ->
                            when (decl) {
                                is KoClassDeclaration -> !decl.hasModifier(KoModifier.PRIVATE)
                                is KoInterfaceDeclaration -> !decl.hasModifier(KoModifier.PRIVATE)
                                is KoObjectDeclaration -> !decl.hasModifier(KoModifier.COMPANION) && !decl.hasModifier(KoModifier.PRIVATE)
                                else -> false
                            }
                        }
                    val lastNonClassTypeIndex =
                        declarations.indexOfLast { decl ->
                            decl is KoPropertyDeclaration || decl is KoFunctionDeclaration ||
                                decl is KoInitBlockDeclaration ||
                                (decl is KoObjectDeclaration && decl.hasModifier(KoModifier.COMPANION))
                        }
                    firstNonPrivateClassTypeIndex == -1 || lastNonClassTypeIndex == -1 ||
                        firstNonPrivateClassTypeIndex > lastNonClassTypeIndex
                }
            codeScope
                .interfaces()
                .assertTrue(testName = this.testCase.name.toString()) {
                    val declarations = it.declarations(includeNested = false, includeLocal = false)
                    val firstNonPrivateClassTypeIndex =
                        declarations.indexOfFirst { decl ->
                            when (decl) {
                                is KoClassDeclaration -> !decl.hasModifier(KoModifier.PRIVATE)
                                is KoInterfaceDeclaration -> !decl.hasModifier(KoModifier.PRIVATE)
                                is KoObjectDeclaration -> !decl.hasModifier(KoModifier.COMPANION) && !decl.hasModifier(KoModifier.PRIVATE)
                                else -> false
                            }
                        }
                    val lastNonClassTypeIndex =
                        declarations.indexOfLast { decl ->
                            decl is KoPropertyDeclaration || decl is KoFunctionDeclaration ||
                                decl is KoInitBlockDeclaration ||
                                (decl is KoObjectDeclaration && decl.hasModifier(KoModifier.COMPANION))
                        }
                    firstNonPrivateClassTypeIndex == -1 || lastNonClassTypeIndex == -1 ||
                        firstNonPrivateClassTypeIndex > lastNonClassTypeIndex
                }
        }
    }
}

private val identifierChain = Regex("""[A-Za-z_]\w*(\.[A-Za-z_]\w*)*""")

private fun KoPropertyDeclaration.explicitRuleOrder(): Int? =
    annotations
        .firstOrNull { it.name == "Rule" }
        ?.arguments
        ?.firstOrNull { it.name == "order" || it.name.isEmpty() }
        ?.value
        ?.trim()
        ?.toIntOrNull()

private fun KoPropertyDeclaration.ruleOrder(): Int? =
    if (hasAnnotation {
            it.name == "Rule"
        }
    ) {
        explicitRuleOrder() ?: Rule.DEFAULT_ORDER
    } else {
        null
    }

private fun KoFileDeclaration.resolveReferences(text: String): Set<String> =
    identifierChain
        .findAll(text)
        .flatMap { match ->
            val segments = match.value.split('.')
            val head = segments.first()
            val heads =
                imports.filter { !it.isWildcard && (it.alias?.name ?: it.name.substringAfterLast('.')) == head }.map { it.name } +
                    imports.filter { it.isWildcard }.map { "${it.name}.$head" } +
                    listOfNotNull(packagee?.name?.let { "$it.$head" }) +
                    head
            heads.flatMap { resolvedHead ->
                segments.indices.map { last -> (listOf(resolvedHead) + segments.subList(1, last + 1)).joinToString(".") }
            }
        }.toSet()
