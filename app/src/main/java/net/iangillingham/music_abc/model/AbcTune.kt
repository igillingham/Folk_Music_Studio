/*
 * Copyright (c) 2026 Ian Gillingham
 * Licensed under the GNU General Public License v3.0
 */
package net.iangillingham.music_abc.model

import android.net.Uri

data class AbcTune(
    val title: String,
    val content: String,
    val sourceUri: Uri,
    val originalContent: String // Used to identify the tune in the file for replacement
)
