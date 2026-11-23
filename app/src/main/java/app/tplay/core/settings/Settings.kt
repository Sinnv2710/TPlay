package app.tplay.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.tplay.domain.CleanupPolicy
import app.tplay.domain.CleanupPreset
import app.tplay.domain.SettingsRepository
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

internal const val MILLIS_PER_DAY = 86_400_000L

internal fun cleanupCutoff(nowMillis: Long, days: Int): Long {
    require(days >= 0) { "days must not be negative" }
    return nowMillis - days * MILLIS_PER_DAY
}

internal fun shouldClean(
    lastModifiedMillis: Long,
    nowMillis: Long,
    days: Int,
    keepHistory: Boolean,
    isHistory: Boolean,
): Boolean {
    if (keepHistory && isHistory) {
        return false
    }
    return lastModifiedMillis <= cleanupCutoff(nowMillis, days)
}

/** Deletes downloaded title files created before the cutoff. Returns how many titles were cleaned. */
fun interface TitleCleaner {
    suspend fun clean(cutoffMillis: Long, keepHistory: Boolean): Int
}

const val DEFAULT_DISCOVER_SITE = "https://en.yts-official.com"

class SettingsRepositoryImpl(
    private val context: Context,
    private val titleCleaner: TitleCleaner? = null,
) : SettingsRepository {
    private val store = context.settingsStore
    private val presetKey = stringPreferencesKey("preset")
    private val daysKey = intPreferencesKey("days")
    private val keepHistoryKey = booleanPreferencesKey("keep_history")
    private val folderKey = stringPreferencesKey("folder")
    private val wifiOnlyKey = booleanPreferencesKey("wifi_only")
    private val playerKey = stringPreferencesKey("player")
    private val browseKey = stringPreferencesKey("browse_start")
    private val discoverKey = stringPreferencesKey("discover_site")
    private val adultKey = booleanPreferencesKey("show_adult")

    override fun cleanupPolicy(): Flow<CleanupPolicy> = store.data.map { preferences ->
        val preset = preferences[presetKey]
            ?.let { value -> runCatching { CleanupPreset.valueOf(value) }.getOrNull() }
            ?: CleanupPreset.NEVER
        CleanupPolicy(
            preset = preset,
            customDays = preferences[daysKey] ?: 7,
            alsoRemoveFromRd = false,
            keepHistory = preferences[keepHistoryKey] ?: true,
        )
    }

    override suspend fun setCleanupPolicy(policy: CleanupPolicy) {
        store.edit { preferences ->
            preferences[presetKey] = policy.preset.name
            preferences[daysKey] = policy.customDays
            preferences[keepHistoryKey] = policy.keepHistory
        }
    }

    override fun parentFolder(): Flow<String> = store.data.map { preferences ->
        preferences[folderKey]
                ?: (context.getExternalFilesDir("Movies") ?: context.filesDir).absolutePath
    }

    override suspend fun setParentFolder(path: String) {
        store.edit { preferences -> preferences[folderKey] = path }
    }

    override fun wifiOnly(): Flow<Boolean> = store.data.map { preferences ->
        preferences[wifiOnlyKey] ?: false
    }

    override suspend fun setWifiOnly(value: Boolean) {
        store.edit { preferences -> preferences[wifiOnlyKey] = value }
    }

    override fun browseStartPage(): Flow<String> = store.data.map { preferences ->
        preferences[browseKey] ?: "https://en.yts-official.com/"
    }

    override suspend fun setBrowseStartPage(value: String) {
        store.edit { preferences -> preferences[browseKey] = value.trim() }
    }

    override fun discoverSite(): Flow<String> = store.data.map { it[discoverKey] ?: DEFAULT_DISCOVER_SITE }

    override suspend fun setDiscoverSite(value: String) {
        store.edit { it[discoverKey] = value.trim() }
    }

    override fun showAdult(): Flow<Boolean> = store.data.map { it[adultKey] ?: false }

    override suspend fun setShowAdult(value: Boolean) {
        store.edit { it[adultKey] = value }
    }

    override fun preferredPlayer(): Flow<String> = store.data.map { preferences ->
        preferences[playerKey] ?: "vlc"
    }

    override suspend fun setPreferredPlayer(value: String) {
        store.edit { preferences -> preferences[playerKey] = value }
    }

    override suspend fun cacheBytes(): Long {
        return context.cacheDir.walkTopDown()
            .filter(File::isFile)
            .sumOf(File::length)
    }

    override suspend fun clearCache() {
        context.cacheDir.deleteRecursively()
        context.cacheDir.mkdirs()
    }

    override suspend fun cleanOlderThan(days: Int): Int {
        val cleaner = titleCleaner ?: return 0
        val keepHistory = cleanupPolicy().first().keepHistory
        return cleaner.clean(cleanupCutoff(System.currentTimeMillis(), days), keepHistory)
    }
}
