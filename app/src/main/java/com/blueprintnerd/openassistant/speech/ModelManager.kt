package com.blueprintnerd.openassistant.speech

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

enum class ModelKind(
    val id: String,
    val title: String,
    val subtitle: String,
    val sizeLabel: String,
    val archiveUrl: String,
    val extractedDirName: String,
) {
    KWS(
        id = "kws",
        title = "Wake Word (Sherpa-ONNX KWS)",
        subtitle = "On-device keyword spotting for Hey Google, OK Google, and custom wake phrases",
        sizeLabel = "15 MB",
        archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01-mobile.tar.bz2",
        extractedDirName = "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01-mobile",
    ),
    ASR(
        id = "asr",
        title = "Speech-to-Text (Zipformer EN)",
        subtitle = "Streaming on-device English speech recognition with endpoint detection",
        sizeLabel = "103 MB",
        archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17-mobile.tar.bz2",
        extractedDirName = "sherpa-onnx-streaming-zipformer-en-20M-2023-02-17-mobile",
    ),
    TTS(
        id = "tts",
        title = "Neural Voice (Piper Lessac EN)",
        subtitle = "Offline neural text-to-speech voice (optional; falls back to local system TTS)",
        sizeLabel = "20 MB",
        archiveUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium-int8.tar.bz2",
        extractedDirName = "vits-piper-en_US-lessac-medium-int8",
    ),
}

data class ModelDownloadState(
    val installed: Boolean = false,
    val downloading: Boolean = false,
    val extracting: Boolean = false,
    val progress: Float = 0f,
    val error: String? = null,
)

class ModelManager(context: Context) {
    private val modelsRoot = File(context.filesDir, "models").apply { mkdirs() }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _states = MutableStateFlow(buildInitialStates())
    val states: StateFlow<Map<ModelKind, ModelDownloadState>> = _states.asStateFlow()

    private fun buildInitialStates(): Map<ModelKind, ModelDownloadState> =
        ModelKind.entries.associateWith { kind ->
            ModelDownloadState(installed = isInstalled(kind))
        }

    fun refreshStates() {
        _states.value = ModelKind.entries.associateWith { kind ->
            val current = _states.value[kind] ?: ModelDownloadState()
            current.copy(installed = isInstalled(kind))
        }
    }

    fun getModelDir(kind: ModelKind): File = File(modelsRoot, kind.extractedDirName)

    fun isInstalled(kind: ModelKind): Boolean {
        val dir = getModelDir(kind)
        if (!dir.exists() || !dir.isDirectory) return false
        return when (kind) {
            ModelKind.KWS -> findFile(dir, "encoder", ".onnx") != null &&
                findFile(dir, "decoder", ".onnx") != null &&
                findFile(dir, "joiner", ".onnx") != null &&
                File(dir, "tokens.txt").exists()
            ModelKind.ASR -> findFile(dir, "encoder", ".onnx") != null &&
                findFile(dir, "decoder", ".onnx") != null &&
                findFile(dir, "joiner", ".onnx") != null &&
                File(dir, "tokens.txt").exists()
            ModelKind.TTS -> dir.listFiles()?.any { it.name.endsWith(".onnx") } == true &&
                File(dir, "tokens.txt").exists()
        }
    }

    fun findFile(dir: File, prefix: String, suffix: String): File? {
        val files = dir.listFiles()?.filter {
            it.isFile && it.name.startsWith(prefix) && it.name.endsWith(suffix)
        }.orEmpty()
        return files.firstOrNull { it.name.contains("int8") } ?: files.firstOrNull()
    }

    fun deleteModel(kind: ModelKind) {
        getModelDir(kind).deleteRecursively()
        updateState(kind) { ModelDownloadState(installed = false) }
    }

    suspend fun downloadModel(kind: ModelKind): Result<File> = withContext(Dispatchers.IO) {
        updateState(kind) {
            ModelDownloadState(installed = false, downloading = true, progress = 0f, error = null)
        }
        val tmpArchive = File(modelsRoot, "${kind.id}.tar.bz2.tmp")
        try {
            val request = Request.Builder().url(kind.archiveUrl).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("HTTP ${response.code} downloading ${kind.title}")
                }
                val body = response.body ?: throw IllegalStateException("Empty download response")
                val totalBytes = body.contentLength()
                var downloaded = 0L
                body.byteStream().use { input ->
                    FileOutputStream(tmpArchive).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (totalBytes > 0) {
                                val pct = (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                updateState(kind) { it.copy(progress = pct * 0.85f) }
                            }
                        }
                    }
                }
            }

            updateState(kind) { it.copy(downloading = false, extracting = true, progress = 0.9f) }
            getModelDir(kind).deleteRecursively()
            extractTarBz2(tmpArchive, modelsRoot)
            tmpArchive.delete()

            val ok = isInstalled(kind)
            if (!ok) {
                throw IllegalStateException("Extracted archive missing required ONNX files")
            }
            updateState(kind) { ModelDownloadState(installed = true, progress = 1f) }
            Result.success(getModelDir(kind))
        } catch (e: Exception) {
            tmpArchive.delete()
            updateState(kind) {
                ModelDownloadState(
                    installed = isInstalled(kind),
                    downloading = false,
                    extracting = false,
                    progress = 0f,
                    error = e.message ?: "Download failed",
                )
            }
            Result.failure(e)
        }
    }

    private fun extractTarBz2(archive: File, destinationRoot: File) {
        val canonicalRoot = destinationRoot.canonicalPath
        FileInputStream(archive).use { fis ->
            BufferedInputStream(fis).use { bis ->
                BZip2CompressorInputStream(bis).use { bzis ->
                    TarArchiveInputStream(bzis).use { tis ->
                        var entry = tis.nextEntry
                        while (entry != null) {
                            val outFile = File(destinationRoot, entry.name)
                            if (!outFile.canonicalPath.startsWith(canonicalRoot)) {
                                throw SecurityException("Unsafe archive path: ${entry.name}")
                            }
                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { fos ->
                                    tis.copyTo(fos)
                                }
                            }
                            entry = tis.nextEntry
                        }
                    }
                }
            }
        }
    }

    private fun updateState(kind: ModelKind, transform: (ModelDownloadState) -> ModelDownloadState) {
        val map = _states.value.toMutableMap()
        map[kind] = transform(map[kind] ?: ModelDownloadState())
        _states.value = map
    }
}
