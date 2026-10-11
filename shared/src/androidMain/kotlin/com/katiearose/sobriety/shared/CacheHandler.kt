package com.katiearose.sobriety.shared

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.widget.Toast
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Reads and writes all addictions as one JSON file.
 *
 * There are two kinds of storage:
 *  - the **cache** file `Sobriety.cache` in the private app storage. This is where the app keeps
 *    its data between starts ([loadFromDisk] / [writeCache]).
 *  - **export / import** files chosen by the user through the system file picker
 *    ([exportData] / [importData]). They use exactly the same JSON format.
 */
class CacheHandler(private val context: Context) {

    companion object {
        /** File name inside `Context.filesDir`. Do not change it, existing users have this file. */
        const val CACHE_FILE_NAME = "Sobriety.cache"

        /** A damaged cache file is renamed to this so that it is never overwritten by new data. */
        private const val CORRUPT_FILE_NAME = "Sobriety.cache.corrupt"

        private val JsonSerializer = Json { ignoreUnknownKeys = true }
        private val listSerializer = ListSerializer(AddictionSerializer)
    }

    private val cacheFile: File
        get() = File(context.filesDir, CACHE_FILE_NAME)

    /**
     * Reads the list of addictions from a stream.
     *
     * @throws Exception if the content is empty or is not valid data. Callers must handle this,
     *   a broken file must never crash the app.
     */
    fun readCache(input: InputStream): List<Addiction> {
        val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return JsonSerializer.decodeFromString(listSerializer, text)
    }

    /**
     * Loads the saved data when the app starts.
     *
     * - No file yet (first start): returns an empty list.
     * - File can not be read (damaged, for example because the app was killed in the middle of
     *   writing with an older version): the file is kept as `Sobriety.cache.corrupt`, a message is
     *   shown, and an empty list is returned. Before this fix the app crashed on every start in
     *   this situation and the user could never open it again.
     */
    fun loadFromDisk(): List<Addiction> {
        val atomicFile = AtomicFile(cacheFile)
        return try {
            // openRead() also restores the file from the backup copy that AtomicFile keeps while
            // writing, if the app was killed at an unlucky moment.
            atomicFile.openRead().use { readCache(it) }
        } catch (_: FileNotFoundException) {
            emptyList()
        } catch (e: Exception) {
            keepCorruptFile()
            Toast.makeText(context, R.string.cache_corrupt, Toast.LENGTH_LONG).show()
            emptyList()
        }
    }

    /** Copies the unreadable cache file aside so a later save can not destroy the only copy. */
    private fun keepCorruptFile() {
        try {
            cacheFile.copyTo(File(context.filesDir, CORRUPT_FILE_NAME), overwrite = true)
        } catch (_: Exception) {
            // Nothing more we can do. The important thing is that the app still starts.
        }
    }

    /**
     * Saves everything to the cache file.
     *
     * [AtomicFile] writes to a temporary file first and swaps it in only when writing has
     * completely finished. If the app is killed in the middle (empty battery, system kills the
     * process) the previous complete version is still there. The old implementation truncated the
     * real file first, which could leave it empty or cut off.
     */
    fun writeCache(addictions: List<Addiction>) {
        val json = JsonSerializer.encodeToString(listSerializer, addictions)
        val atomicFile = AtomicFile(cacheFile)
        val out = atomicFile.startWrite()
        try {
            out.write(json.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(out)
        } catch (t: Throwable) {
            atomicFile.failWrite(out)
            throw t
        }
    }

    /**
     * Writes all data to a file the user picked.
     *
     * @return true on success. On failure a message is shown and false is returned.
     */
    fun exportData(addictions: List<Addiction>, output: Uri): Boolean {
        return try {
            val json = JsonSerializer.encodeToString(listSerializer, addictions)
            // Mode "wt" = write and truncate. Without it, overwriting a longer existing file can
            // leave old text at the end, which makes the exported JSON invalid.
            val stream = context.contentResolver.openOutputStream(output, "wt")
                ?: throw FileNotFoundException("No output stream for $output")
            stream.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
            true
        } catch (e: Exception) {
            Toast.makeText(context, R.string.cant_export_data, Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Reads data from a file the user picked and hands the result to [resultConsumer].
     * A file with invalid content is rejected with a message instead of crashing the app.
     * Merging the result into the existing list is up to the caller.
     *
     * @return true if the file was read successfully and [resultConsumer] was called
     */
    fun importData(input: Uri, resultConsumer: (List<Addiction>) -> Unit): Boolean {
        val stream = context.contentResolver.openInputStream(input)
        if (stream == null) {
            Toast.makeText(context, R.string.cant_import_data, Toast.LENGTH_SHORT).show()
            return false
        }
        val imported = try {
            readCache(stream)
        } catch (e: Exception) {
            Toast.makeText(context, R.string.cant_import_data, Toast.LENGTH_SHORT).show()
            return false
        }
        resultConsumer(imported)
        return true
    }
}
