package com.malawianlad.wastatussaver.ui

import android.net.Uri

/**
 * Represents a single WhatsApp status file discovered inside the .Statuses folder.
 *
 * @param uri   The SAF content URI for this file. Used by:
 *              - Coil (AsyncImage) to load the thumbnail / full image
 *              - ContentResolver.openInputStream() when saving to the gallery
 * @param name  The raw filename, e.g. "IMG-20240515-WA0003.jpg"
 * @param type  Whether this file is an IMAGE or a VIDEO
 */
data class StatusFile(
    val uri: Uri,
    val name: String,
    val type: StatusType
)

/**
 * Simple enum that drives which composable is used for display and
 * which MediaStore collection is used when saving.
 */
enum class StatusType {
    IMAGE,
    VIDEO
}

