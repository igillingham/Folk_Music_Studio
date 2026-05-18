/*
 * Copyright (c) 2026 Ian Gillingham
 * Licensed under the GNU General Public License v3.0
 */
package net.iangillingham.music_abc.logic

import android.content.Context
import android.net.Uri
import net.iangillingham.music_abc.model.AbcTune
import java.io.BufferedReader
import java.io.InputStreamReader

object AbcHandler {
    fun parseAbcContent(content: String, sourceUri: Uri): List<AbcTune> {
        val tunes = mutableListOf<AbcTune>()
        val parts = content.split(Regex("(?m)^X:"))
        parts.forEach { part ->
            if (part.trim().isNotEmpty()) {
                val fullTuneContent = "X:$part"
                val titleMatch = Regex("(?m)^T:(.*)").find(fullTuneContent)
                val title = titleMatch?.groupValues?.get(1)?.trim() ?: "Untitled"
                tunes.add(AbcTune(title, fullTuneContent, sourceUri, fullTuneContent))
            }
        }
        return tunes
    }

    fun saveTune(context: Context, tune: AbcTune, newContent: String): AbcTune? {
        return try {
            val sourceUri = tune.sourceUri
            val currentFileContent = context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readText()
            } ?: return null

            val updatedFileContent = currentFileContent.replace(tune.originalContent, newContent)

            context.contentResolver.openOutputStream(sourceUri, "wt")?.use { outputStream ->
                outputStream.write(updatedFileContent.toByteArray())
            }

            tune.copy(content = newContent, originalContent = newContent)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteTune(context: Context, tune: AbcTune): Boolean {
        return try {
            val sourceUri = tune.sourceUri
            val currentFileContent = context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readText()
            } ?: return false

            val updatedFileContent = currentFileContent.replace(tune.originalContent, "").trim()

            context.contentResolver.openOutputStream(sourceUri, "wt")?.use { outputStream ->
                outputStream.write(updatedFileContent.toByteArray())
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
