package com.example.smart_steward.net

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import com.example.smart_steward.AiAnalysisActivity
import com.example.smart_steward.CapturedMediaStore
import com.example.smart_steward.R
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Calls the Express LLM route: POST {baseUrl}/ai
 * (multipart: optional "message", optional file field "media").
 */
object SmartStewardAiClient {

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
    fun analyzeToResultIntent(
        context: Context,
        baseUrl: String,
        userMessage: String?,
        reanalyze: Boolean,
        deviceLocationShort: String?
    ): Intent {
        val root = baseUrl.trim().trimEnd('/')
        val url = "$root/ai"

        val bitmap = CapturedMediaStore.capturedBitmap
        val videoUri = CapturedMediaStore.capturedVideoUri

        val mediaFile = when {
            bitmap != null -> writeBitmapToCache(context, bitmap)
            videoUri != null -> copyUriToCacheFile(context, videoUri)
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
                val partBody = mediaFile.asRequestBody(mime.toMediaTypeOrNull())
                bodyBuilder.addFormDataPart("media", mediaFile.name, partBody)
            } else if (message.isBlank()) {
                throw IllegalStateException("No media or message to send to AI.")
            }

            val request = Request.Builder()
                .url(url)
                .post(bodyBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val err = runCatching { JSONObject(raw).optString("error") }.getOrNull()
                    throw IllegalStateException(err?.takeIf { it.isNotBlank() } ?: "HTTP ${response.code}")
                }
                val json = JSONObject(raw)
                val payload = json.optJSONObject("response")
                    ?: throw IllegalStateException("Invalid AI response (missing response object).")
                return mapPayloadToResultIntent(context, payload, deviceLocationShort)
            }
        } finally {
            mediaFile?.delete()
        }
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
        val category = payload.optString("category").ifBlank { "Incident" }
        val type = payload.optString("type").ifBlank { "incident" }
        val agency = payload.optString("assignedAgency").ifBlank {
            context.getString(R.string.review_detected_agency_title_default)
        }
        val summary = payload.optString("summary").ifBlank {
            context.getString(R.string.review_ai_description_default)
        }
        val severity = payload.optString("severity").ifBlank { "—" }

        val incidentTitle = category
        val incidentSubtitle = listOf(
            type.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
            },
            severity
        ).joinToString(" · ")

        val agencyShort = agencyShortFromLine(agency)
        val loc = deviceLocationShort?.trim()?.takeIf { it.isNotEmpty() }
            ?: context.getString(R.string.review_location_short_default)

        return Intent().apply {
            putExtra(AiAnalysisActivity.EXTRA_INCIDENT_TITLE, incidentTitle)
            putExtra(AiAnalysisActivity.EXTRA_INCIDENT_SUBTITLE, incidentSubtitle)
            putExtra(AiAnalysisActivity.EXTRA_AGENCY_TITLE, agency)
            putExtra(AiAnalysisActivity.EXTRA_AGENCY_SUBLINE, agencyShort)
            putExtra(AiAnalysisActivity.EXTRA_DESCRIPTION, summary)
            putExtra(AiAnalysisActivity.EXTRA_LOCATION_SHORT, loc)
        }
    }

    private fun agencyShortFromLine(assignedAgency: String): String {
        val inParens = Regex("\\(([^)]+)\\)").find(assignedAgency)?.groupValues?.getOrNull(1)?.trim()
        if (!inParens.isNullOrBlank()) return inParens
        return assignedAgency.split(",").firstOrNull()?.trim().orEmpty().ifBlank { assignedAgency }
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
