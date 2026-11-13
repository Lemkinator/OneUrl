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

package de.lemke.oneurl.data

import android.security.keystore.KeyGenParameterSpec
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.SecureRandom
import java.security.Security
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.util.Collections
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.KeyGeneratorSpi
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.junit.rules.ExternalResource

/**
 * Registers a JCA provider named `AndroidKeyStore` for one test, so code that asks for the Android Keystore runs on the JVM.
 * It holds AES keys in memory and ignores key purposes, so it proves no Keystore security property.
 */
class FakeAndroidKeyStoreRule : ExternalResource() {
    override fun before() {
        Security.addProvider(FakeAndroidKeyStoreProvider())
    }

    override fun after() {
        Security.removeProvider(PROVIDER_NAME)
    }

    private companion object {
        const val PROVIDER_NAME = "AndroidKeyStore"
    }
}

private class FakeAndroidKeyStoreProvider : Provider("AndroidKeyStore", 1.0, "In-memory AndroidKeyStore for JVM tests") {
    private val secrets = ConcurrentHashMap<String, SecretKey>()

    init {
        putService(SpiService(this, "KeyStore", "AndroidKeyStore") { InMemoryKeyStoreSpi(secrets) })
        putService(SpiService(this, "KeyGenerator", "AES") { AesKeyGeneratorSpi(secrets) })
    }

    private class SpiService(
        provider: Provider,
        type: String,
        algorithm: String,
        private val create: () -> Any,
    ) : Service(provider, type, algorithm, SpiService::class.java.name, null, null) {
        override fun newInstance(constructorParameter: Any?): Any = create()
    }
}

private class InMemoryKeyStoreSpi(
    private val keys: MutableMap<String, SecretKey>,
) : KeyStoreSpi() {
    override fun engineGetKey(
        alias: String,
        password: CharArray?,
    ): Key? = keys[alias]

    override fun engineContainsAlias(alias: String): Boolean = alias in keys

    override fun engineIsKeyEntry(alias: String): Boolean = alias in keys

    override fun engineDeleteEntry(alias: String) {
        keys.remove(alias)
    }

    override fun engineAliases() = Collections.enumeration(keys.keys.toList())

    override fun engineSize(): Int = keys.size

    override fun engineGetCreationDate(alias: String): Date? = if (alias in keys) Date() else null

    override fun engineGetCertificateChain(alias: String): Array<Certificate>? = null

    override fun engineGetCertificate(alias: String): Certificate? = null

    override fun engineGetCertificateAlias(cert: Certificate): String? = null

    override fun engineIsCertificateEntry(alias: String): Boolean = false

    override fun engineSetKeyEntry(
        alias: String,
        key: Key,
        password: CharArray?,
        chain: Array<Certificate>?,
    ) {
        keys[alias] = SecretKeySpec(key.encoded, key.algorithm)
    }

    override fun engineSetKeyEntry(
        alias: String,
        key: ByteArray,
        chain: Array<Certificate>?,
    ) = throw UnsupportedOperationException()

    override fun engineSetCertificateEntry(
        alias: String,
        cert: Certificate,
    ) = throw UnsupportedOperationException()

    override fun engineStore(
        stream: OutputStream?,
        password: CharArray?,
    ) = Unit

    override fun engineLoad(
        stream: InputStream?,
        password: CharArray?,
    ) = Unit
}

private class AesKeyGeneratorSpi(
    private val keys: MutableMap<String, SecretKey>,
) : KeyGeneratorSpi() {
    private var spec: KeyGenParameterSpec? = null

    override fun engineInit(random: SecureRandom?) = throw UnsupportedOperationException()

    override fun engineInit(
        keysize: Int,
        random: SecureRandom?,
    ) = throw UnsupportedOperationException()

    override fun engineInit(
        params: AlgorithmParameterSpec,
        random: SecureRandom?,
    ) {
        spec = params as KeyGenParameterSpec
    }

    override fun engineGenerateKey(): SecretKey {
        val spec = checkNotNull(spec) { "KeyGenerator is not initialized" }
        val bytes = ByteArray(spec.keySize / Byte.SIZE_BITS).also { SecureRandom().nextBytes(it) }
        return SecretKeySpec(bytes, "AES").also { keys[spec.keystoreAlias] = it }
    }
}
