// This is the full and complete code for MainActivity.kt
package com.malawianlad.statussaver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import com.malawianlad.statussaver.ui.StatusViewModel
import com.malawianlad.statussaver.ui.StatusViewModelFactory
import com.malawianlad.statussaver.ui.theme.StatusSaverTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.foundation.layout.padding
import coil.compose.AsyncImage

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
            // Create the ViewModel using our factory in Activity scope
            val vm: StatusViewModel = ViewModelProvider(this, StatusViewModelFactory(application, folderUri)).get(StatusViewModel::class.java)
            setContent {
                StatusSaverTheme {
                    // Pass the Activity-scoped ViewModel into the composable
                    StatusListScreen(viewModel = vm)
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
    val context = LocalContext.current

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
                    Card(modifier = Modifier.padding(8.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(8.dp)) {
                            // Thumbnail using Coil; Coil can load content:// URIs
                            AsyncImage(
                                model = file.uri,
                                contentDescription = file.name,
                                modifier = Modifier.size(100.dp)
                            )
                            Text(text = file.name)
                            Button(onClick = {
                                viewModel.saveStatus(file) { success, message ->
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Text("Save")
                            }
                        }
                    }
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
