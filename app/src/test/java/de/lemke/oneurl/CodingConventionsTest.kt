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
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import com.lemonappdev.konsist.api.declaration.KoInitBlockDeclaration
import com.lemonappdev.konsist.api.declaration.KoInterfaceDeclaration
import com.lemonappdev.konsist.api.declaration.KoObjectDeclaration
import com.lemonappdev.konsist.api.declaration.KoPropertyDeclaration
import com.lemonappdev.konsist.api.verify.assertTrue
import io.kotest.core.spec.style.ShouldSpec

class CodingConventionsTest : ShouldSpec() {
    private val codeScope = Konsist.scopeFromProduction()

    init {
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
