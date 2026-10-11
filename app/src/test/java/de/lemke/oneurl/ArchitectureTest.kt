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
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe

class ArchitectureTest : ShouldSpec() {
    private val codeScope = Konsist.scopeFromProduction()

    init {
        should("data layer does not depend on ui") {
            codeScope.files
                .withPackage("de.lemke.oneurl.data..")
                .assertFalse(testName = this.testCase.name.toString()) {
                    it.hasImport { import -> import.name.startsWith("de.lemke.oneurl.ui.") }
                }
        }
        // data may depend on domain.model (shared value types, e.g. URL, ShortURLProvider) to
        // map DB entities to domain models, but never on use cases or other domain logic.
        should("data layer does not depend on domain business logic") {
            codeScope.files
                .withPackage("de.lemke.oneurl.data..")
                .assertFalse(testName = this.testCase.name.toString()) {
                    it.hasImport { import ->
                        import.name.startsWith("de.lemke.oneurl.domain.") && !import.name.startsWith("de.lemke.oneurl.domain.model.")
                    }
                }
        }
        should("domain layer does not depend on ui") {
            codeScope.files
                .withPackage("de.lemke.oneurl.domain..")
                .assertFalse(testName = this.testCase.name.toString()) {
                    it.hasImport { import -> import.name.startsWith("de.lemke.oneurl.ui.") }
                }
        }
        should("use case classes declare operator fun invoke") {
            codeScope
                .classes()
                .filter { it.name.endsWith("UseCase") }
                .assertTrue(testName = this.testCase.name.toString()) { koClass ->
                    koClass
                        .functions(includeNested = false, includeLocal = false)
                        .any { it.name == "invoke" && it.hasModifier(KoModifier.OPERATOR) }
                }
        }
        should("classes named ViewModel extend ViewModel") {
            codeScope
                .classes()
                .filter { it.name.endsWith("ViewModel") }
                .assertTrue(testName = this.testCase.name.toString()) {
                    it.hasParentWithName("ViewModel", "AndroidViewModel", indirectParents = true)
                }
        }
        should("ViewModel files expose state only, without Channel or SharedFlow events") {
            codeScope.files
                .filter { it.declaresViewModel() }
                .assertFalse(testName = this.testCase.name.toString()) { it.usesEventStreams() }
        }
        should("event stream rule catches fully qualified, typed and inferred Channel and SharedFlow use") {
            fun usesEventStreams(source: String): Boolean {
                val dir = tempdir()
                dir.resolve("ProbeViewModel.kt").writeText(source)
                return Konsist
                    .scopeFromExternalDirectory(dir.absolutePath)
                    .files
                    .single()
                    .usesEventStreams()
            }
            usesEventStreams(
                """
                import kotlinx.coroutines.channels.BufferOverflow
                import kotlinx.coroutines.channels.ProducerScope
                import kotlinx.coroutines.channels.awaitClose
                import kotlinx.coroutines.flow.MutableStateFlow
                import kotlinx.coroutines.flow.callbackFlow
                class ProbeViewModel : ViewModel() {
                    /** Mirrors no Channel( or MutableSharedFlow( */
                    val state = MutableStateFlow(0)
                    val ticks = callbackFlow<Int> { awaitClose { } }
                    val label = "Channel( and MutableSharedFlow<Int>( and .shareIn("
                    val overflow = BufferOverflow.DROP_OLDEST
                    fun produce(scope: ProducerScope<Int>) = scope.trySend(0)
                    fun forward(source: Channel<Int>) {
                        val mirror = MutableStateFlow<Int>(0)
                    }
                    init {
                        val note = "receiveAsFlow( // Channel("
                    }
                }
                """.trimIndent(),
            ) shouldBe false
            listOf(
                "val events = kotlinx.coroutines.channels.Channel<Int>()",
                "val events = kotlinx.coroutines.flow.MutableSharedFlow<List<Int>>()",
                "val events = kotlinx.coroutines.channels.Channel<\n        Int,\n    >()",
                "val events = state.shareIn(viewModelScope, SharingStarted.Eagerly)",
                "val events = kotlinx.coroutines.channels.Channel<Int>().receiveAsFlow()",
                "val events: SharedFlow<Int>? = null",
                "val events: kotlinx.coroutines.channels.SendChannel<Int> = TODO()",
                "val events: ReceiveChannel<Int> = TODO()",
                "val events: Channel<Int> = TODO()",
                "val events: StateFlow<Int>\n        field = kotlinx.coroutines.flow.MutableSharedFlow<Int>()",
                "val events by lazy { kotlinx.coroutines.channels.Channel<Int>() }",
                "val label = \"\" + Channel<Int>()",
                "fun events() = Channel<Int>()",
                "fun events() = state.shareIn(viewModelScope, SharingStarted.Eagerly)",
                "fun events(): SharedFlow<Int> = TODO()",
                "fun events(): kotlinx.coroutines.channels.ReceiveChannel<Int>? = null",
                "fun send() {\n        val events: SendChannel<Int> = TODO()\n    }",
                "init {\n        events = kotlinx.coroutines.flow.MutableSharedFlow<Int>()\n    }",
                "init {\n        val events: Channel<Int> = TODO()\n    }",
            ).forEach { member ->
                withClue(member) { usesEventStreams("class ProbeViewModel : ViewModel() {\n    $member\n}\n") shouldBe true }
            }
            BANNED_EVENT_IMPORTS.forEach { banned ->
                withClue(banned) { usesEventStreams("import $banned\nclass ProbeViewModel : ViewModel()\n") shouldBe true }
            }
        }
        should("ui collects no event flows") {
            codeScope.files
                .withPackage("de.lemke.oneurl.ui..")
                .assertFalse(testName = this.testCase.name.toString()) {
                    it.hasImport { import -> import.name == "de.lemke.commonutils.ui.utils.collectEvents" }
                }
        }
        should("HiltViewModel classes use Inject constructor") {
            codeScope
                .classes()
                .filter { it.hasAnnotation { ann -> ann.name == "HiltViewModel" } }
                .assertTrue(testName = this.testCase.name.toString()) {
                    it.primaryConstructor?.hasAnnotation { ann -> ann.name == "Inject" } == true
                }
        }
    }
}

private val VIEW_MODEL_BASE_CLASSES = setOf("ViewModel", "AndroidViewModel")

private val BANNED_EVENT_IMPORTS =
    setOf(
        "kotlinx.coroutines.channels.*",
        "kotlinx.coroutines.channels.Channel",
        "kotlinx.coroutines.channels.ReceiveChannel",
        "kotlinx.coroutines.channels.SendChannel",
        "kotlinx.coroutines.flow.MutableSharedFlow",
        "kotlinx.coroutines.flow.SharedFlow",
        "kotlinx.coroutines.flow.asSharedFlow",
        "kotlinx.coroutines.flow.consumeAsFlow",
        "kotlinx.coroutines.flow.receiveAsFlow",
        "kotlinx.coroutines.flow.shareIn",
    )

private val BANNED_CHANNEL_TYPES = setOf("Channel", "SendChannel", "ReceiveChannel")

private val EVENT_STREAM_CALL =
    Regex("""\b(Channel|MutableSharedFlow)\s*(<[^(){};=]*?>)?\s*\(|\.(shareIn|receiveAsFlow|consumeAsFlow)\s*\(""")

private val COMMENT_OR_STRING = Regex(""""{3}.*?"{3}|"(?:\\.|[^"\\\n])*"|/\*.*?\*/|//[^\n]*""", RegexOption.DOT_MATCHES_ALL)

private fun KoFileDeclaration.declaresViewModel(): Boolean =
    classes().any { koClass -> koClass.hasParent { it.name in VIEW_MODEL_BASE_CLASSES } }

private fun KoFileDeclaration.usesEventStreams(): Boolean =
    hasImport { import ->
        val path =
            import.text
                .removePrefix("import")
                .substringBefore(" as ")
                .trim()
        path in BANNED_EVENT_IMPORTS || path.endsWith("SharedFlow")
    } ||
        declaredTypes().any { type ->
            val name =
                type
                    .substringBefore('<')
                    .removeSuffix("?")
                    .trim()
                    .substringAfterLast('.')
            name in BANNED_CHANNEL_TYPES || name.endsWith("SharedFlow")
        } ||
        EVENT_STREAM_CALL.containsMatchIn(text.replace(COMMENT_OR_STRING, ""))

private fun KoFileDeclaration.declaredTypes(): List<String> {
    val functions = functions()
    val variables = functions.flatMap { it.variables } + classes().flatMap { it.initBlocks }.flatMap { it.variables }
    return properties().mapNotNull { it.type?.text } +
        functions.mapNotNull { it.returnType?.text } +
        variables.mapNotNull { it.type?.text }
}
