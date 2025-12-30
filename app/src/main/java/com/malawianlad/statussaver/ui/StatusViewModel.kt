// This is the full and complete code for StatusViewModel.kt
package com.malawianlad.statussaver.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.size
import androidx.compose.ui.test.filter
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.io.path.exists

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
