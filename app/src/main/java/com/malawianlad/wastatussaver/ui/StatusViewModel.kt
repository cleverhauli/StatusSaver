package com.malawianlad.wastatussaver.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// UI State
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Sealed class representing every possible state the status list UI can be in.
 * The View (MainActivity) observes this and renders accordingly — it never
 * contains any logic of its own.
 */
sealed class StatusUiState {
    /** Initial state: no folder has been granted yet. Shows the setup screen. */
    data object Idle : StatusUiState()

    /** A folder scan is running on the IO thread. Shows a spinner. */
    data object Loading : StatusUiState()

    /**
     * Scan completed but the folder contained zero recognised files.
     * This usually means the user has not viewed any statuses in WhatsApp yet.
     */
    data object Empty : StatusUiState()

    /**
     * Scan succeeded. Files are pre-split into images and videos so the
     * TabRow can switch between them without re-filtering.
     */
    data class Success(
        val images: List<StatusFile>,
        val videos: List<StatusFile>
    ) : StatusUiState()

    /** An exception was thrown during scanning. Shows the error message. */
    data class Error(val message: String) : StatusUiState()
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class StatusViewModel(application: Application) : AndroidViewModel(application) {

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        private const val TAG        = "StatusViewModel"
        private const val PREFS      = "wa_status_saver_prefs"
        private const val KEY_WA     = "uri_whatsapp"           // SharedPrefs key for WA uri
        private const val KEY_WA_BIZ = "uri_whatsapp_business"  // SharedPrefs key for WA Biz uri
        private const val OUT_DIR    = "WA StatusSaver"         // gallery sub-folder name
        private const val KEY_RECENTLY_SAVED = "recently_saved_uris" // persisted recently saved URIs
    }

    // ── SharedPreferences ─────────────────────────────────────────────────────

    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ── Public state ──────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow<StatusUiState>(StatusUiState.Idle)
    /**
     * The UI observes this StateFlow via collectAsStateWithLifecycle().
     * It emits a new value whenever the scan result changes — no manual
     * "refresh" trigger is needed from the View side.
     */
    val uiState: StateFlow<StatusUiState> = _uiState.asStateFlow()

    private val _saveResult = MutableStateFlow<String?>(null)
    /**
     * One-shot feedback string shown as a Toast after a save attempt.
     * The View calls clearSaveResult() after consuming it.
     */
    val saveResult: StateFlow<String?> = _saveResult.asStateFlow()

    // New: selection state for multi-select
    private val _selectedFiles = MutableStateFlow<Set<Uri>>(emptySet())
    val selectedFiles: StateFlow<Set<Uri>> = _selectedFiles.asStateFlow()

    // New: recently saved list (most recent first)
    private val _recentlySaved = MutableStateFlow<List<StatusFile>>(emptyList())
    val recentlySaved: StateFlow<List<StatusFile>> = _recentlySaved.asStateFlow()

    // ── URI persistence helpers ───────────────────────────────────────────────

    /** Returns the persisted WhatsApp folder URI, or null if not yet granted. */
    fun getWaUri(): Uri? = prefs.getString(KEY_WA, null)?.let { Uri.parse(it) }

    /** Returns the persisted WhatsApp Business folder URI, or null if not yet granted. */
    fun getWaBizUri(): Uri? = prefs.getString(KEY_WA_BIZ, null)?.let { Uri.parse(it) }

    /** Persists the WhatsApp folder URI after the user has granted access. */
    fun saveWaUri(uri: Uri) { prefs.edit().putString(KEY_WA, uri.toString()).apply() }

    /** Persists the WhatsApp Business folder URI after the user has granted access. */
    fun saveWaBizUri(uri: Uri) { prefs.edit().putString(KEY_WA_BIZ, uri.toString()).apply() }

    /** Removes the WhatsApp URI — triggers the setup screen on next launch. */
    fun clearWaUri() { prefs.edit().remove(KEY_WA).apply() }

    /** Removes the WhatsApp Business URI. */
    fun clearWaBizUri() { prefs.edit().remove(KEY_WA_BIZ).apply() }

    /**
     * Returns true if at least one folder has been granted.
     * The View uses this to decide between the setup screen and the main screen.
     */
    fun hasAnyUri(): Boolean = getWaUri() != null || getWaBizUri() != null

    // ── File scanning ─────────────────────────────────────────────────────────

    /**
     * Entry point called by the View to trigger a folder scan.
     *
     * All file IO runs on [Dispatchers.IO] inside viewModelScope — the main
     * (UI) thread is never blocked. The coroutine is automatically cancelled
     * if the ViewModel is cleared (e.g. Activity destroyed).
     */
    fun loadStatuses() {
        viewModelScope.launch {
            _uiState.value = StatusUiState.Loading

            val files = withContext(Dispatchers.IO) {
                val merged = mutableListOf<StatusFile>()

                // Scan every granted URI (WA and/or WA Business)
                listOfNotNull(getWaUri(), getWaBizUri()).forEach { treeUri ->
                    merged += scanFolder(treeUri)
                }

                // Remove duplicates — the same status can appear in both apps
                // Sort descending — WA filenames are timestamp-based so newest comes first
                merged
                    .distinctBy { it.name }
                    .sortedByDescending { it.name }
            }

            _uiState.value = if (files.isEmpty()) {
                StatusUiState.Empty
            } else {
                StatusUiState.Success(
                    images = files.filter { it.type == StatusType.IMAGE },
                    videos = files.filter { it.type == StatusType.VIDEO }
                )
            }
        }
    }

    /**
     * Lists all valid status files inside one SAF tree URI.
     *
     * Uses [DocumentFile.fromTreeUri] which is the only compliant way to read
     * inside Android/media/ on API 30+. The .nomedia file and any unrecognised
     * extensions are silently skipped.
     */
    private fun scanFolder(treeUri: Uri): List<StatusFile> {
        return try {
            val ctx  = getApplication<Application>()
            val tree = DocumentFile.fromTreeUri(ctx, treeUri) ?: return emptyList()

            tree.listFiles()
                .filter { file ->
                    file.isFile &&
                    file.name != null &&
                    file.name != ".nomedia" &&
                    !file.name!!.startsWith(".")
                }
                .mapNotNull { file ->
                    val name = file.name ?: return@mapNotNull null
                    val type = when {
                        name.endsWith(".jpg",  ignoreCase = true) ||
                        name.endsWith(".jpeg", ignoreCase = true) ||
                        name.endsWith(".png",  ignoreCase = true) -> StatusType.IMAGE

                        name.endsWith(".mp4",  ignoreCase = true) ||
                        name.endsWith(".3gp",  ignoreCase = true) -> StatusType.VIDEO

                        else -> return@mapNotNull null   // skip unknown file types
                    }
                    StatusFile(uri = file.uri, name = name, type = type)
                }
        } catch (e: Exception) {
            Log.e(TAG, "scanFolder failed for $treeUri", e)
            emptyList()
        }
    }

    // ── Save to gallery ───────────────────────────────────────────────────────

    /**
     * Saves a single StatusFile into MediaStore. Returns true on success.
     */
    private fun saveStatusToMediaStoreInternal(file: StatusFile): Boolean {
        try {
            val ctx      = getApplication<Application>()
            val resolver = ctx.contentResolver

            val (mimeType, collection, relativePath) = when (file.type) {
                StatusType.IMAGE -> Triple(
                    "image/jpeg",
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    "${Environment.DIRECTORY_PICTURES}/$OUT_DIR"
                )
                StatusType.VIDEO -> Triple(
                    "video/mp4",
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    "${Environment.DIRECTORY_MOVIES}/$OUT_DIR"
                )
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME,  file.name)
                put(MediaStore.MediaColumns.MIME_TYPE,      mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH,  relativePath)
                put(MediaStore.MediaColumns.IS_PENDING,     1)
            }
            val destUri = resolver.insert(collection, values) ?: return false

            resolver.openInputStream(file.uri)?.use { input ->
                resolver.openOutputStream(destUri)?.use { output ->
                    input.copyTo(output)
                }
            }

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(destUri, values, null, null)

            return true
        } catch (e: Exception) {
            Log.e(TAG, "saveStatusToMediaStoreInternal failed", e)
            return false
        }
    }

    // ── ViewModel init: load persisted recently saved URIs ─────────────────────
    init {
        viewModelScope.launch {
            loadRecentlySavedFromPrefs()
        }
    }

    // ── Selection helpers ────────────────────────────────────────────────────

    fun toggleSelection(uri: Uri) {
        _selectedFiles.value = _selectedFiles.value.toMutableSet().also { set ->
            if (!set.add(uri)) set.remove(uri)
        }
    }

    fun clearSelection() {
        _selectedFiles.value = emptySet()
    }

    // ── Recently saved persistence helpers ───────────────────────────────────

    private fun persistRecentlySavedUris() {
        try {
            val arr = org.json.JSONArray()
            _recentlySaved.value.forEach { arr.put(it.uri.toString()) }
            prefs.edit().putString(KEY_RECENTLY_SAVED, arr.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist recently saved URIs", e)
        }
    }

    private suspend fun loadRecentlySavedFromPrefs() {
        withContext(Dispatchers.IO) {
            try {
                val json = prefs.getString(KEY_RECENTLY_SAVED, null) ?: return@withContext
                val arr = org.json.JSONArray(json)
                val list = mutableListOf<StatusFile>()
                for (i in 0 until arr.length()) {
                    val uriStr = arr.optString(i, null) ?: continue
                    val uri = Uri.parse(uriStr)
                    val name = uri.lastPathSegment ?: uri.toString()
                    val type = when {
                        name.endsWith(".mp4", true) || name.endsWith(".3gp", true) -> StatusType.VIDEO
                        else -> StatusType.IMAGE
                    }
                    list += StatusFile(uri = uri, name = name, type = type)
                }
                _recentlySaved.value = list
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load recently saved URIs", e)
            }
        }
    }

    private fun prependRecentlySaved(file: StatusFile) {
        _recentlySaved.value = listOf(file) + _recentlySaved.value.filter { it.uri != file.uri }
        if (_recentlySaved.value.size > 10) {
            _recentlySaved.value = _recentlySaved.value.take(10)
        }
        persistRecentlySavedUris()
    }

    // ── Public saveStatus (single) — reworked to reuse helper and update recentlySaved

    fun saveStatus(file: StatusFile) {
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                val success = saveStatusToMediaStoreInternal(file)
                if (success) {
                    // Update recently saved list
                    prependRecentlySaved(file)
                    "✅ Saved to gallery!"
                } else {
                    "❌ Save failed"
                }
            }
            _saveResult.value = message
        }
    }

    /**
     * Saves a list of files sequentially on Dispatchers.IO and emits a combined result.
     */
    fun batchSave(files: List<StatusFile>) {
        viewModelScope.launch {
            val resultMessage = withContext(Dispatchers.IO) {
                var savedCount = 0
                files.forEach { file ->
                    val ok = saveStatusToMediaStoreInternal(file)
                    if (ok) {
                        savedCount++
                        prependRecentlySaved(file)
                    }
                }
                if (savedCount > 0) "✅ Saved $savedCount files" else "❌ No files were saved"
            }
            _saveResult.value = resultMessage
            // Clear selection on main thread
            clearSelection()
        }
    }

    /** Called by the View after it has consumed the save result Toast message. */
    fun clearSaveResult() {
        _saveResult.value = null
    }
}
