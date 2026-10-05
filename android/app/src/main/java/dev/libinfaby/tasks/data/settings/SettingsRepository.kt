package dev.libinfaby.tasks.data.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.libinfaby.tasks.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val token: String? = null,
    val apiUrl: String = BuildConfig.API_BASE_URL,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val wallpaperColors: Boolean = false,
    val lastSyncAt: Long = 0,
) {
    val signedIn get() = token != null
}

private val Context.dataStore by preferencesDataStore("settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val token = stringPreferencesKey("token_enc")
        val apiUrl = stringPreferencesKey("api_url")
        val theme = stringPreferencesKey("theme")
        val wallpaperColors = booleanPreferencesKey("wallpaper_colors")
        val lastSync = longPreferencesKey("last_sync_at")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            token = p[Keys.token]?.let { TokenCipher.decrypt(it) },
            apiUrl = p[Keys.apiUrl] ?: BuildConfig.API_BASE_URL,
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            wallpaperColors = p[Keys.wallpaperColors] ?: false,
            lastSyncAt = p[Keys.lastSync] ?: 0,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setToken(token: String) = context.dataStore.edit { it[Keys.token] = TokenCipher.encrypt(token) }

    suspend fun setApiUrl(url: String) = context.dataStore.edit {
        it[Keys.apiUrl] = url.trim().let { u -> if (u.endsWith("/")) u else "$u/" }
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }

    suspend fun setWallpaperColors(on: Boolean) = context.dataStore.edit { it[Keys.wallpaperColors] = on }

    suspend fun setLastSync(at: Long) = context.dataStore.edit { it[Keys.lastSync] = at }

    suspend fun signOut() = context.dataStore.edit {
        it.remove(Keys.token)
        it.remove(Keys.lastSync)
    }
}

/** Encrypts the session token with an AES key that never leaves the Android Keystore. */
private object TokenCipher {
    private const val ALIAS = "tasks_token_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
        }.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    // A token that can't be decrypted (e.g. the key was wiped) reads as signed out.
    fun decrypt(encoded: String): String? = runCatching {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
        }
        String(cipher.doFinal(bytes, 12, bytes.size - 12))
    }.getOrNull()
}
