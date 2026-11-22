package app.tplay.core.vault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.tplay.domain.ServiceToken
import app.tplay.domain.VaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.vaultStore by preferencesDataStore("vault")

private const val IV_BYTES = 12

interface VaultCipher {
    fun encrypt(value: String): String
    fun decrypt(value: String): String
}

class KeystoreCipher : VaultCipher {
    private val alias = "tplay-vault"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(spec)
        return generator.generateKey()
    }

    override fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(
            cipher.iv + cipher.doFinal(value.toByteArray()),
            Base64.NO_WRAP,
        )
    }

    override fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, bytes.copyOfRange(0, IV_BYTES)),
        )
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
    }
}

class VaultRepositoryImpl(
    private val context: Context,
    private val cipher: VaultCipher = KeystoreCipher(),
) : VaultRepository {
    private val values = stringPreferencesKey("values")

    override fun services(): Flow<List<ServiceToken>> = context.vaultStore.data.map { preferences ->
        preferences[values].orEmpty().split("|")
            .filter(String::isNotBlank)
            .map { entry ->
                val id = entry.substringBefore('=')
                ServiceToken(id, id, true)
            }
    }

    override suspend fun put(serviceId: String, label: String, value: String) {
        context.vaultStore.edit { preferences ->
            val retained = preferences[values].orEmpty().split("|")
                .filter { it.isNotBlank() && !it.startsWith("$serviceId=") }
            preferences[values] = (retained + "$serviceId=${cipher.encrypt(value)}").joinToString("|")
        }
    }

    override suspend fun get(serviceId: String): String? = context.vaultStore.data.first()[values]
        ?.split("|")
        ?.firstOrNull { it.startsWith("$serviceId=") }
        ?.substringAfter('=')
        ?.let(cipher::decrypt)

    override suspend fun remove(serviceId: String) {
        context.vaultStore.edit { preferences ->
            preferences[values] = preferences[values].orEmpty().split("|")
                .filterNot { it.startsWith("$serviceId=") }
                .joinToString("|")
        }
    }
}
