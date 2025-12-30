// This is the full and complete code for MainActivity.kt
package com.malawianlad.statussaver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.malawianlad.statussaver.ui.StatusViewModel
import com.malawianlad.statussaver.ui.StatusViewModelFactory
import com.malawianlad.statussaver.ui.theme.StatusSaverTheme

class MainActivity : ComponentActivity() {

    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            Log.d("MainActivity", "Folder selected: $uri")
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)

            // Save the URI and restart the activity to apply the change
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            prefs.edit().putString("status_folder_uri", uri.toString()).apply()
            recreate()
        } else {
            Log.d("MainActivity", "Folder selection cancelled.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val savedUriString = prefs.getString("status_folder_uri", null)

        if (savedUriString == null) {
            // SCENARIO 1: No folder has been chosen yet. Show the permission screen.
            setContent {
                StatusSaverTheme {
                    PermissionScreen(
                        onRequestPermission = { folderPickerLauncher.launch(null) }
                    )
                }
            }
        } else {
            // SCENARIO 2: We have the folder. Show the statuses.
            val folderUri = Uri.parse(savedUriString)
            setContent {
                StatusSaverTheme {
                    // Create the ViewModel using our factory
                    val viewModel: StatusViewModel = viewModel(
                        factory = StatusViewModelFactory(application, folderUri)
                    )
                    // Show the main screen for displaying statuses
                    StatusListScreen(viewModel = viewModel)
                }
            }
        }
    }
}

/**
 * The screen that shows the "Choose Folder" button.
 */
@Composable
fun PermissionScreen(onRequestPermission: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Welcome to Status Saver!")
            Button(onClick = onRequestPermission) {
                Text("Choose Status Folder")
            }
        }
    }
}

/**
 * The screen that displays the list of statuses found by the ViewModel.
 */
@Composable
fun StatusListScreen(viewModel: StatusViewModel) {
    // Watch the list of files from the ViewModel.
    // The 'by' keyword makes it automatically update when the list changes.
    val statusFiles by viewModel.statusFiles.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (statusFiles.isEmpty()) {
            // Show a message if the list is empty.
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No statuses found.")
                Text("View some statuses in WhatsApp first!")
            }
        } else {
            // Display the statuses in a grid with 3 columns.
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize()
            ) {
                items(statusFiles) { file ->
                    // For now, we just show the name of the file.
                    // Later, we will show the actual image here.
                    Text(text = file.name)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PermissionScreenPreview() {
    StatusSaverTheme {
        PermissionScreen(onRequestPermission = {})
    }
}
