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
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.jupiter.api.Test

class ArchitectureTest {
    private val scope = Konsist.scopeFromProduction()

    @Test
    fun `data layer does not depend on ui`() {
        scope.files
            .withPackage("de.lemke.oneurl.data..")
            .assertFalse { it.hasImport { import -> import.name.startsWith("de.lemke.oneurl.ui.") } }
    }

    @Test
    fun `data layer does not depend on domain business logic`() {
        // data may depend on domain.model (shared value types, e.g. URL, ShortURLProvider) to
        // map DB entities to domain models, but never on use cases or other domain logic.
        scope.files
            .withPackage("de.lemke.oneurl.data..")
            .assertFalse {
                it.hasImport { import ->
                    import.name.startsWith("de.lemke.oneurl.domain.") && !import.name.startsWith("de.lemke.oneurl.domain.model.")
                }
            }
    }

    @Test
    fun `domain layer does not depend on ui`() {
        scope.files
            .withPackage("de.lemke.oneurl.domain..")
            .assertFalse { it.hasImport { import -> import.name.startsWith("de.lemke.oneurl.ui.") } }
    }

    @Test
    fun `use case classes declare operator fun invoke`() {
        scope
            .classes()
            .filter { it.name.endsWith("UseCase") }
            .assertTrue { koClass ->
                koClass
                    .functions(includeNested = false, includeLocal = false)
                    .any { it.name == "invoke" && it.hasModifier(KoModifier.OPERATOR) }
            }
    }

    @Test
    fun `classes named ViewModel extend ViewModel`() {
        scope
            .classes()
            .filter { it.name.endsWith("ViewModel") }
            .assertTrue { it.hasParentWithName("ViewModel", "AndroidViewModel", indirectParents = true) }
    }

    @Test
    fun `HiltViewModel classes use Inject constructor`() {
        scope
            .classes()
            .filter { it.hasAnnotation { ann -> ann.name == "HiltViewModel" } }
            .assertTrue { it.primaryConstructor?.hasAnnotation { ann -> ann.name == "Inject" } == true }
    }
}
