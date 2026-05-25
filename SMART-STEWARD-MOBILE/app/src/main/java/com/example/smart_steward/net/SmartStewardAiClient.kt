package com.example.smart_steward.net

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.example.smart_steward.AiAnalysisActivity
import com.example.smart_steward.CapturedMediaStore
import com.example.smart_steward.R
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Calls the Express LLM route: POST {baseUrl}/ai
 * (multipart: optional "message", optional file field "media").
 */
object SmartStewardAiClient {

    private const val TAG = "SmartStewardAiClient"

    /** Upload target band (5–8 MB warning). We aim for the best quality still ≤ this. */
    const val TARGET_VIDEO_BYTES: Long = 8L * 1024L * 1024L

    /** Soft floor of the target band — stop early once we reach a good 5–8 MB encode. */
    private const val TARGET_VIDEO_BYTES_MIN: Long = 5L * 1024L * 1024L

    /** Hard upload cap — reject only if the file cannot be brought under this. */
    const val MAX_VIDEO_BYTES: Long = 20L * 1024L * 1024L

    /** Already within the 5–8 MB band (used for UI hints only). */
    const val COMPRESS_ABOVE_BYTES: Long = TARGET_VIDEO_BYTES

    /** Max height for all upload encodes (low resolution). */
    private const val UPLOAD_MAX_HEIGHT = 480

    /** @deprecated Use [MAX_VIDEO_BYTES]. */
    @Deprecated("Use MAX_VIDEO_BYTES")
    const val MAX_SOURCE_VIDEO_BYTES: Long = MAX_VIDEO_BYTES

    private data class VideoCompressProfile(val maxHeight: Int, val videoBitrate: Int)

    /** Low-resolution profiles only — every video is transcoded before upload. */
    private val COMPRESS_PROFILES = listOf(
        VideoCompressProfile(UPLOAD_MAX_HEIGHT, 700_000),
        VideoCompressProfile(UPLOAD_MAX_HEIGHT, 500_000),
        VideoCompressProfile(UPLOAD_MAX_HEIGHT, 400_000),
        VideoCompressProfile(360, 350_000),
        VideoCompressProfile(360, 250_000),
        VideoCompressProfile(360, 180_000),
        VideoCompressProfile(360, 120_000),
        VideoCompressProfile(360, 80_000),
    )

    /** OkHttp requires an explicit http/https scheme (host:port alone throws). */
    private fun normalizeApiBaseUrl(raw: String): String {
        val t = raw.trim().trimEnd('/')
        return when {
            t.isEmpty() -> "http://54.66.101.26:3000"
            t.startsWith("http://", ignoreCase = true) ||
                t.startsWith("https://", ignoreCase = true) -> t
            else -> "http://$t"
        }
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Returns an [Intent] with the same extras as [AiAnalysisActivity] uses for the review screen,
     * populated from the API JSON (`type`, `category`, `assignedAgency`, `summary`, `severity`).
     */
    /** Resolves size; may copy to cache only when length is unknown. */
    fun resolveVideoSizeBytes(context: Context, uri: Uri): Long {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            val len = afd.length
            if (len >= 0L) return len
        }
        val path = uri.path
        if (path != null) {
            val f = File(path)
            if (f.isFile) return f.length()
        }
        val temp = copyUriToCacheFile(context, uri)
        val size = temp.length()
        temp.delete()
        return size
    }

    /** Every video is transcoded to low resolution before upload. */
    fun shouldAttemptVideoCompression(@Suppress("UNUSED_PARAMETER") sizeBytes: Long): Boolean = true

    /** True when the source exceeds 20 MB and must be reduced before upload. */
    fun isVideoCompressionRequired(sizeBytes: Long): Boolean =
        sizeBytes > MAX_VIDEO_BYTES

    /**
     * Prepares a video for AI upload.
     * Always transcodes to low resolution (480p/360p) and compresses toward the 5–8 MB band.
     * Rejects only if no encode can be produced under 20 MB.
     */
    fun prepareVideoUploadFile(
        context: Context,
        uri: Uri,
        onPrepareProgress: ((Int) -> Unit)? = null,
    ): PrepareVideoResult {
        onPrepareProgress?.invoke(0)
        val sourceFile = copyUriToCacheFile(context, uri)
        val originalSize = sourceFile.length().coerceAtLeast(0L)
        val compressionRequired = isVideoCompressionRequired(originalSize)

        Log.d(
            TAG,
            "Video ${formatMegabytes(originalSize)} — mandatory low-res transcode toward 5–8 MB" +
                if (compressionRequired) " (must be ≤ 20 MB)" else "",
        )

        val sourceUri = Uri.fromFile(sourceFile)
        var bestInBand: File? = null
        var bestInBandSize = 0L
        var smallestUnderMax: File? = null
        var smallestUnderMaxSize = Long.MAX_VALUE

        for ((index, profile) in COMPRESS_PROFILES.withIndex()) {
            val candidate =
                File(context.cacheDir, "ai_upload_opt_${System.currentTimeMillis()}_$index.mp4")
            val progressScale: (Int) -> Unit = { pct ->
                val slice = 100 / COMPRESS_PROFILES.size.coerceAtLeast(1)
                val base = index * slice
                onPrepareProgress?.invoke((base + (pct * slice / 100f)).toInt().coerceIn(0, 99))
            }
            val ok =
                VideoCompressor.compress(
                    context,
                    sourceUri,
                    candidate,
                    profile.videoBitrate,
                    profile.maxHeight,
                    progressScale,
                )
            if (!ok || !candidate.isFile || candidate.length() <= 0L) {
                candidate.delete()
                continue
            }
            val len = candidate.length()
            when {
                len <= TARGET_VIDEO_BYTES && len > bestInBandSize -> {
                    smallestUnderMax?.delete()
                    smallestUnderMax = null
                    smallestUnderMaxSize = Long.MAX_VALUE
                    bestInBand?.delete()
                    bestInBand = candidate
                    bestInBandSize = len
                }
                len <= MAX_VIDEO_BYTES && len < smallestUnderMaxSize -> {
                    smallestUnderMax?.delete()
                    smallestUnderMax = candidate
                    smallestUnderMaxSize = len
                }
                else -> candidate.delete()
            }
            if (bestInBandSize in TARGET_VIDEO_BYTES_MIN..TARGET_VIDEO_BYTES) {
                break
            }
        }

        onPrepareProgress?.invoke(100)

        fun finishWithCompressed(file: File, finalSize: Long): PrepareVideoResult {
            sourceFile.delete()
            return PrepareVideoResult(
                file = file,
                originalSizeBytes = originalSize,
                finalSizeBytes = finalSize,
                wasCompressed = true,
            )
        }

        bestInBand?.let { return finishWithCompressed(it, bestInBandSize) }

        smallestUnderMax?.let { compressed ->
            val finalSize = compressed.length()
            Log.d(
                TAG,
                "Using ${formatMegabytes(finalSize)} low-res encode (5–8 MB band not reached)",
            )
            return finishWithCompressed(compressed, finalSize)
        }

        sourceFile.delete()
        throw VideoTooLargeException(originalSize)
    }

    fun formatMegabytes(bytes: Long): String =
        String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))

    fun analyzeToResultIntent(
        context: Context,
        baseUrl: String,
        userMessage: String?,
        reanalyze: Boolean,
        deviceLocationShort: String?,
        callbacks: AiAnalyzeCallbacks? = null,
        preparedVideoFile: File? = null,
    ): Intent {
        val root = normalizeApiBaseUrl(baseUrl)
        val url = "$root/ai"
        Log.i(TAG, "AI request starting -> POST $url (baseUrl=$baseUrl)")

        val bitmap = CapturedMediaStore.capturedBitmap
        val videoUri = CapturedMediaStore.capturedVideoUri

        val mediaFile = when {
            bitmap != null -> writeBitmapToCache(context, bitmap)
            preparedVideoFile != null -> preparedVideoFile
            videoUri != null -> prepareVideoUploadFile(context, videoUri).file
            else -> null
        }

        try {
            val bodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)

            val message = buildMessage(userMessage, reanalyze, bitmap != null, videoUri != null)
            if (message.isNotBlank()) {
                bodyBuilder.addFormDataPart("message", message)
            }

            if (mediaFile != null) {
                val mime = guessMime(mediaFile)
                val rawBody = mediaFile.asRequestBody(mime.toMediaTypeOrNull())
                val total = rawBody.contentLength().coerceAtLeast(0L)
                callbacks?.onUploadStarted(total)
                val partBody = if (callbacks != null) {
                    ProgressRequestBody(
                        delegate = rawBody,
                        onProgress = { written, length ->
                            val pct = if (length > 0L) {
                                ((written * 100f) / length).roundToInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            callbacks.onUploadProgress(pct, written, length)
                        },
                        onComplete = {
                            callbacks.onUploadComplete()
                            callbacks.onWaitingForServer()
                        }
                    )
                } else {
                    rawBody
                }
                bodyBuilder.addFormDataPart("media", mediaFile.name, partBody)
            } else if (message.isBlank()) {
                throw IllegalStateException("No media or message to send to AI.")
            }

            val request = Request.Builder()
                .url(url)
                .post(bodyBuilder.build())
                .build()

            Log.i(
                TAG,
                "AI upload -> media=${mediaFile?.name ?: "none"} " +
                    "size=${mediaFile?.length()?.let { formatMegabytes(it) } ?: "0 B"}",
            )

            try {
                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    Log.i(TAG, "AI raw response (${response.code}): $raw")
                    if (!response.isSuccessful) {
                        val err = runCatching { JSONObject(raw).optString("error") }.getOrNull()
                        val message = when {
                            response.code == 413 -> "Video file is too large (max 20 MB after compression)."
                            !err.isNullOrBlank() -> err
                            else -> "HTTP ${response.code}"
                        }
                        throw IllegalStateException(message)
                    }
                    val json = JSONObject(raw)
                    val payload = parseClassificationPayload(json)
                    Log.i(TAG, "AI classification payload: $payload")
                    return mapPayloadToResultIntent(context, payload, deviceLocationShort)
                }
            } catch (e: Exception) {
                Log.e(TAG, "AI request failed: ${e.javaClass.simpleName}: ${e.message}", e)
                throw e
            }
        } finally {
            mediaFile?.delete()
        }
    }

    /** Accepts `{ "response": { ... } }` or a legacy flat classification object. */
    private fun parseClassificationPayload(json: JSONObject): JSONObject {
        json.optJSONObject("response")?.let { return it }
        if (json.has("category") || json.has("type") || json.has("summary")) {
            return json
        }
        throw IllegalStateException("Invalid AI response (missing classification fields).")
    }

    private fun buildMessage(
        userMessage: String?,
        reanalyze: Boolean,
        hasPhoto: Boolean,
        hasVideo: Boolean
    ): String {
        val trimmed = userMessage?.trim().orEmpty()
        if (trimmed.isNotEmpty()) return trimmed
        if (reanalyze) {
            return "Re-analyze this report using the user's corrected description. " +
                "Media context: ${if (hasPhoto) "photo" else if (hasVideo) "video" else "no media"}."
        }
        return ""
    }

    private fun mapPayloadToResultIntent(
        context: Context,
        payload: JSONObject,
        deviceLocationShort: String?
    ): Intent {
        val type = payload.optString("type").trim()
        val category = payload.optString("category").ifBlank { "Incident" }
        val agency = parseAssignedAgency(payload).ifBlank {
            context.getString(R.string.review_detected_agency_title_default)
        }
        val summary = payload.optString("summary").ifBlank {
            context.getString(R.string.review_ai_description_default)
        }
        val severity = payload.optString("severity").ifBlank { "—" }
        val severityReason = payload.optString("severity_reason").trim()
        val confidenceScore = payload.optDouble("confidence_score", Double.NaN)
        val reportable = isReportablePayload(payload, category, agency)
        val synthesis = payload.optString("synthesis").trim()
        val fileKind = payload.optString("file").trim()
        val frameAnalysisJson = payload.optJSONArray("frame_analysis")?.toString().orEmpty()

        Log.i(
            TAG,
            "AI fields -> type=$type category=$category agency=$agency severity=$severity " +
                "severity_reason=${if (severityReason.isBlank()) "(none)" else severityReason} " +
                "confidence_score=$confidenceScore reportable=$reportable file=$fileKind",
        )

        val incidentTitle = category
        val incidentSubtitle = listOf(
            type.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
            }.ifBlank { "incident" },
            severity,
        ).joinToString(" · ")

        val loc = deviceLocationShort?.trim()?.takeIf { it.isNotEmpty() }
            ?: context.getString(R.string.review_location_short_default)

        return Intent().apply {
            putExtra(AiAnalysisActivity.EXTRA_INCIDENT_TITLE, incidentTitle)
            putExtra(AiAnalysisActivity.EXTRA_INCIDENT_SUBTITLE, incidentSubtitle)
            putExtra(AiAnalysisActivity.EXTRA_AGENCY_TITLE, agency)
            putExtra(AiAnalysisActivity.EXTRA_AGENCY_SUBLINE, agency)
            putExtra(AiAnalysisActivity.EXTRA_DESCRIPTION, summary)
            putExtra(AiAnalysisActivity.EXTRA_LOCATION_SHORT, loc)
            putExtra(AiAnalysisActivity.EXTRA_REPORTABLE, reportable)
            if (severity.isNotBlank() && severity != "—") {
                putExtra(AiAnalysisActivity.EXTRA_SEVERITY, severity)
            }
            if (severityReason.isNotBlank()) {
                putExtra(AiAnalysisActivity.EXTRA_AI_SEVERITY_REASON, severityReason)
            }
            if (!confidenceScore.isNaN()) {
                putExtra(AiAnalysisActivity.EXTRA_CONFIDENCE_SCORE, confidenceScore.toFloat())
            }
            if (category.isNotBlank()) {
                putExtra(AiAnalysisActivity.EXTRA_AI_CATEGORY, category)
            }
            if (summary.isNotBlank()) {
                putExtra(AiAnalysisActivity.EXTRA_AI_SUMMARY, summary)
            }
            if (synthesis.isNotBlank()) {
                putExtra(AiAnalysisActivity.EXTRA_AI_SYNTHESIS, synthesis)
            }
            if (fileKind.isNotBlank()) {
                putExtra(AiAnalysisActivity.EXTRA_AI_FILE, fileKind)
            }
            if (frameAnalysisJson.isNotBlank()) {
                putExtra(AiAnalysisActivity.EXTRA_AI_FRAME_ANALYSIS_JSON, frameAnalysisJson)
            }
        }
    }

    /**
     * Server may return one agency string or multiple (e.g. ["BFP","DENR","Barangay"]).
     * Stored and displayed as a comma-separated string for Firestore/UI.
     */
    private fun parseAssignedAgency(payload: JSONObject): String {
        if (!payload.has("assignedAgency")) return ""
        return when (val raw = payload.get("assignedAgency")) {
            is String -> raw.trim()
            is JSONArray -> {
                buildList {
                    for (i in 0 until raw.length()) {
                        raw.optString(i).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                    }
                }.joinToString(", ")
            }
            else -> raw?.toString()?.trim().orEmpty()
        }
    }

    private fun isReportablePayload(
        payload: JSONObject,
        category: String,
        agency: String
    ): Boolean {
        if (payload.has("reportable")) {
            return payload.optBoolean("reportable", true)
        }
        if (category.equals("Not a valid incident", ignoreCase = true)) return false
        if (agency.equals("N/A", ignoreCase = true)) return false
        if (agency.split(",").all { it.trim().equals("N/A", ignoreCase = true) }) return false
        return true
    }

    private fun writeBitmapToCache(context: Context, bitmap: Bitmap): File {
        val out = File(context.cacheDir, "ai_upload_${System.currentTimeMillis()}.jpg")
        FileOutputStream(out).use { fos ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, fos)) {
                throw IllegalStateException("Failed to compress image for upload.")
            }
        }
        return out
    }

    private fun copyUriToCacheFile(context: Context, uri: Uri): File {
        val mime = context.contentResolver.getType(uri) ?: "video/mp4"
        val ext = when {
            mime.contains("mp4", ignoreCase = true) -> "mp4"
            mime.contains("webm", ignoreCase = true) -> "webm"
            mime.contains("quicktime", ignoreCase = true) || mime.contains("mov", ignoreCase = true) -> "mov"
            else -> "bin"
        }
        val out = File(context.cacheDir, "ai_upload_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Could not read video for upload.")
        return out
    }

    private fun guessMime(file: File): String {
        return when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            else -> "application/octet-stream"
        }
    }
}
