package com.example.smart_steward

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object IncidentAnalysisClient {
    data class AnalysisResult(
        val incidentType: String,
        val assignedAgency: String,
        val summary: String,
        val severity: String
    )

    fun analyze(
        context: Context,
        message: String,
        bitmap: Bitmap?,
        videoUri: Uri?,
        onSuccess: (AnalysisResult) -> Unit,
        onError: (String) -> Unit
    ) {
        val mainHandler = Handler(Looper.getMainLooper())

        Thread {
            try {
                val result = postAnalysis(context, message, bitmap, videoUri)
                mainHandler.post { onSuccess(result) }
            } catch (error: Exception) {
                mainHandler.post { onError(error.message ?: "AI analysis failed.") }
            }
        }.start()
    }

    private fun postAnalysis(
        context: Context,
        message: String,
        bitmap: Bitmap?,
        videoUri: Uri?
    ): AnalysisResult {
        val boundary = "SmartStewardBoundary${System.currentTimeMillis()}"
        val endpoint = "${BuildConfig.AI_API_BASE_URL.trimEnd('/')}/ai"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 60_000
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        connection.outputStream.use { output ->
            val writer = BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8))

            writeTextPart(writer, boundary, "message", message)

            if (bitmap != null) {
                val imageBytes = ByteArrayOutputStream().use { imageStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, imageStream)
                    imageStream.toByteArray()
                }
                writeFilePart(writer, output, boundary, imageBytes)
            }

            if (videoUri != null) {
                val mimeType = context.contentResolver.getType(videoUri) ?: "video/mp4"
                val videoBytes = context.contentResolver.openInputStream(videoUri)?.use { input ->
                    input.readBytes()
                } ?: throw IllegalStateException("Unable to read captured video.")
                writeFilePart(
                    writer = writer,
                    output = output,
                    boundary = boundary,
                    fileBytes = videoBytes,
                    fileName = "incident.mp4",
                    contentType = mimeType
                )
            }

            writer.append("--").append(boundary).append("--").append("\r\n")
            writer.flush()
        }

        val responseBody = if (connection.responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }

        if (connection.responseCode !in 200..299) {
            val errorMessage = runCatching { JSONObject(responseBody).optString("error") }.getOrNull()
            throw IllegalStateException(errorMessage?.takeIf { it.isNotBlank() } ?: "AI server error.")
        }

        val responseJson = JSONObject(responseBody)
        val payload = responseJson.optJSONObject("response") ?: responseJson

        return AnalysisResult(
            incidentType = payload.optString("incidentType", "Unclassified"),
            assignedAgency = payload.optString("assignedAgency", "Barangay"),
            summary = payload.optString("summary", "No incident summary was returned."),
            severity = payload.optString("severity", "Medium")
        )
    }

    private fun writeTextPart(
        writer: BufferedWriter,
        boundary: String,
        name: String,
        value: String
    ) {
        writer.append("--").append(boundary).append("\r\n")
        writer.append("Content-Disposition: form-data; name=\"").append(name).append("\"")
            .append("\r\n\r\n")
        writer.append(value).append("\r\n")
        writer.flush()
    }

    private fun writeFilePart(
        writer: BufferedWriter,
        output: java.io.OutputStream,
        boundary: String,
        fileBytes: ByteArray,
        fileName: String = "incident.jpg",
        contentType: String = "image/jpeg"
    ) {
        writer.append("--").append(boundary).append("\r\n")
        writer.append("Content-Disposition: form-data; name=\"media\"; filename=\"")
            .append(fileName)
            .append("\"")
            .append("\r\n")
        writer.append("Content-Type: ").append(contentType).append("\r\n\r\n")
        writer.flush()

        output.write(fileBytes)
        output.flush()

        writer.append("\r\n")
        writer.flush()
    }
}
