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
    fun parseAbcContent(content: String, sourceUri: Uri, sourceFileName: String): List<AbcTune> {
        val tunes = mutableListOf<AbcTune>()
        val parts = content.split(Regex("(?m)^X:"))
        parts.forEach { part ->
            if (part.trim().isNotEmpty()) {
                val fullTuneContent = "X:$part"
                val titleMatch = Regex("(?m)^T:(.*)").find(fullTuneContent)
                val title = titleMatch?.groupValues?.get(1)?.trim() ?: "Untitled"
                tunes.add(AbcTune(title, fullTuneContent, sourceUri, sourceFileName, fullTuneContent))
            }
        }
        return tunes.sortedBy { it.title.lowercase() }
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
        return deleteTunes(context, listOf(tune))
    }

    fun deleteTunes(context: Context, tunes: List<AbcTune>): Boolean {
        var success = true
        // Group tunes by source URI to minimize file operations
        val tunesByUri = tunes.groupBy { it.sourceUri }
        
        tunesByUri.forEach { (uri, fileTunes) ->
            try {
                var fileContent = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).readText()
                } ?: return@forEach

                fileTunes.forEach { tune ->
                    fileContent = fileContent.replace(tune.originalContent, "").trim()
                }

                context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                    outputStream.write(fileContent.toByteArray())
                }
            } catch (e: Exception) {
                e.printStackTrace()
                success = false
            }
        }
        return success
    }

    /**
     * Copies tunes to a target file.
     * Returns a list of titles that were skipped because they already existed.
     */
    suspend fun copyTunesToFile(
        context: Context,
        tunes: List<AbcTune>,
        targetUri: Uri,
        onDuplicateFound: suspend (AbcTune) -> Boolean? // Return true to overwrite, false to skip, null to cancel all
    ): List<String> {
        val skippedTitles = mutableListOf<String>()
        
        try {
            var targetContent = context.contentResolver.openInputStream(targetUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).readText()
            } ?: ""

            // Find current max Z: value if any
            val zValues = Regex("(?m)^Z:\\s*(\\d+)").findAll(targetContent)
                .map { it.groupValues[1].toInt() }
                .toList()
            var nextZ = (zValues.maxOrNull() ?: 0) + 1

            // Find current max X: value if any
            val xValues = Regex("(?m)^X:\\s*(\\d+)").findAll(targetContent)
                .map { it.groupValues[1].toInt() }
                .toList()
            var nextX = (xValues.maxOrNull() ?: 0) + 1

            val tunesToAppend = mutableListOf<String>()

            for (tune in tunes) {
                // Check if tune with same title already exists in target
                val titleRegex = Regex("(?m)^T:\\s*${Regex.escape(tune.title)}\\s*$")
                val existingMatch = titleRegex.find(targetContent)

                if (existingMatch != null) {
                    val shouldOverwrite = onDuplicateFound(tune)
                    if (shouldOverwrite == true) {
                        // Find the whole tune block to replace. 
                        val tuneStart = targetContent.lastIndexOf("X:", existingMatch.range.first)
                        if (tuneStart != -1) {
                            var tuneEnd = targetContent.indexOf("\nX:", tuneStart + 1)
                            if (tuneEnd == -1) tuneEnd = targetContent.length
                            
                            val oldTuneBlock = targetContent.substring(tuneStart, tuneEnd)
                            val newTuneContent = prepareTuneForFile(tune, nextX++, nextZ++)
                            targetContent = targetContent.replace(oldTuneBlock, newTuneContent)
                        }
                    } else if (shouldOverwrite == false) {
                        skippedTitles.add(tune.title)
                    } else {
                        // Canceled
                        return skippedTitles
                    }
                } else {
                    tunesToAppend.add(prepareTuneForFile(tune, nextX++, nextZ++))
                }
            }

            if (tunesToAppend.isNotEmpty()) {
                val separator = if (targetContent.isEmpty() || targetContent.endsWith("\n\n")) "" else if (targetContent.endsWith("\n")) "\n" else "\n\n"
                targetContent += separator + tunesToAppend.joinToString("\n\n")
            }

            context.contentResolver.openOutputStream(targetUri, "wt")?.use { outputStream ->
                outputStream.write(targetContent.toByteArray())
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return skippedTitles
    }

    private fun prepareTuneForFile(tune: AbcTune, xIndex: Int, zIndex: Int): String {
        var content = tune.content
        // Update X: field
        content = if (content.contains(Regex("(?m)^X:"))) {
            content.replaceFirst(Regex("(?m)^X:.*"), "X:$xIndex")
        } else {
            "X:$xIndex\n$content"
        }
        
        // Update or add Z: field
        content = if (content.contains(Regex("(?m)^Z:"))) {
            content.replaceFirst(Regex("(?m)^Z:.*"), "Z:$zIndex")
        } else {
            // Insert Z: after T: or at the beginning if no T:
            val tMatch = Regex("(?m)^T:.*").find(content)
            if (tMatch != null) {
                val insertPos = content.indexOf("\n", tMatch.range.first)
                if (insertPos != -1) {
                    content.substring(0, insertPos + 1) + "Z:$zIndex\n" + content.substring(insertPos + 1)
                } else {
                    content + "\nZ:$zIndex"
                }
            } else {
                content + "\nZ:$zIndex"
            }
        }
        return content
    }
}
