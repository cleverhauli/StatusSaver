package com.malawianlad.wastatussaver.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * ViewModelProvider.Factory for StatusViewModel.
 *
 * Why this exists:
 * StatusViewModel extends AndroidViewModel and takes an Application constructor
 * argument. The default ViewModelProvider cannot create ViewModels with
 * constructor arguments — it throws IllegalArgumentException at runtime.
 * This factory resolves that by manually constructing the ViewModel.
 */
class StatusViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StatusViewModel::class.java)) {
            return StatusViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

