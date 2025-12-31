// This is the full and complete code for StatusViewModel.kt
package com.malawianlad.statussaver.ui

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.ContentValues
import java.io.BufferedInputStream
import java.io.BufferedOutputStream

/**
 * A simple data class to hold information about each status file we find.
 */
data class StatusFile(
    val uri: Uri,
    val name: String,
    val isVideo: Boolean
)

/**
 * The ViewModel (the "brain") that fetches and holds the list of statuses.
 */
class StatusViewModel(
    private val application: Application,
    private val statusFolderUri: Uri
) : ViewModel() {

    // This is the private list of files that our ViewModel manages.
    private val _statusFiles = MutableStateFlow<List<StatusFile>>(emptyList())
    // This is the public, read-only list that the UI will watch for changes.
    val statusFiles = _statusFiles.asStateFlow()

    init {
        // When the ViewModel is created, immediately start fetching the statuses.
        fetchStatuses()
    }

    private fun fetchStatuses() {
        // We do this work in the background so the app doesn't freeze.
        viewModelScope.launch {
            try {
                // Use the URI to get a representation of the folder
                val folder = DocumentFile.fromTreeUri(application, statusFolderUri)

                if (folder != null && folder.exists() && folder.isDirectory) {
                    val files = folder.listFiles()
                        // Filter out things we don't want (like sub-folders or the .nomedia file)
                        .filterNotNull()
                        .filter { it.isFile && it.name != ".nomedia" }
                        // Convert the files into our StatusFile data class
                        .map {
                            StatusFile(
                                uri = it.uri,
                                name = it.name ?: "unknown",
                                isVideo = it.name?.endsWith(".mp4") == true
                            )
                        }
                    // Update our list with the files we found. The UI will update automatically.
                    _statusFiles.value = files
                    Log.d("StatusViewModel", "Success: Found ${files.size} status files.")
                } else {
                    Log.e("StatusViewModel", "Error: Status folder not found or is not a directory.")
                    _statusFiles.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e("StatusViewModel", "Error fetching statuses", e)
                _statusFiles.value = emptyList()
            }
        }
    }

    /**
     * Save a single status file (image or video) into the system Downloads folder.
     * Calls onResult(success, message) on completion.
     */
    fun saveStatus(status: StatusFile, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = application.contentResolver
                val mime = resolver.getType(status.uri) ?: if (status.isVideo) "video/mp4" else "image/jpeg"
                val displayName = "WhatsAppStatus_${System.currentTimeMillis()}_${status.name}".replace("\\s+".toRegex(), "_")

                val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Save to Downloads collection on Android 10+
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    // For older devices, fall back to general external content URI
                    MediaStore.Files.getContentUri("external")
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/StatusSaver")
                    }
                }

                val outUri = resolver.insert(collectionUri, values)
                if (outUri == null) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "Failed to create destination file")
                    }
                    return@launch
                }

                resolver.openInputStream(status.uri).use { inputStream ->
                    if (inputStream == null) {
                        withContext(Dispatchers.Main) {
                            onResult(false, "Unable to open source file")
                        }
                        return@launch
                    }
                    resolver.openOutputStream(outUri).use { outputStream ->
                        if (outputStream == null) {
                            withContext(Dispatchers.Main) {
                                onResult(false, "Unable to open destination file")
                            }
                            return@launch
                        }

                        // Copy bytes
                        val bis = BufferedInputStream(inputStream)
                        val bos = BufferedOutputStream(outputStream)
                        val buffer = ByteArray(8 * 1024)
                        var bytes = bis.read(buffer)
                        while (bytes >= 0) {
                            bos.write(buffer, 0, bytes)
                            bytes = bis.read(buffer)
                        }
                        bos.flush()
                        bis.close()
                        bos.close()

                        withContext(Dispatchers.Main) {
                            onResult(true, "Saved to Downloads/$displayName")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("StatusViewModel", "Error saving status", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "Error: ${e.message}")
                }
            }
        }
    }
}

/**
 * This is a factory that tells Android how to create our StatusViewModel,
 * since it needs extra information (the application and the folder URI).
 */
class StatusViewModelFactory(
    private val application: Application,
    private val statusFolderUri: Uri
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StatusViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StatusViewModel(application, statusFolderUri) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
