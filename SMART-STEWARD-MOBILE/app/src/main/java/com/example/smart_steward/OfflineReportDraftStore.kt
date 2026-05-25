package com.example.smart_steward

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

data class OfflineReportDraft(
    val id: String,
    val userId: String,
    val incidentType: String,
    val assignedAgency: String,
    val description: String,
    val locationLine: String,
    val photoPath: String,
    val videoPath: String,
    val latitude: Double?,
    val longitude: Double?,
    val createdAtMs: Long,
    val severity: String? = null,
    val severityReason: String? = null,
    val aiConfidence: Int? = null,
    val aiCategory: String? = null,
    val aiSummary: String? = null,
    val aiSynthesis: String? = null,
    val aiReportable: Boolean? = null,
    val aiFile: String? = null,
    val aiFrameAnalysisJson: String? = null,
)

object OfflineReportDraftStore {
    private const val PREFS = "offline_report_drafts"
    private const val KEY_ARRAY = "drafts"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun addDraft(
        context: Context,
        userId: String,
        incidentType: String,
        assignedAgency: String,
        description: String,
        locationLine: String,
        photoBitmap: Bitmap?,
        videoUri: Uri?,
        latitude: Double?,
        longitude: Double?,
        severity: String? = null,
        severityReason: String? = null,
        aiConfidence: Int? = null,
        aiCategory: String? = null,
        aiSummary: String? = null,
        aiSynthesis: String? = null,
        aiReportable: Boolean? = null,
        aiFile: String? = null,
        aiFrameAnalysisJson: String? = null,
    ): OfflineReportDraft {
        val id = "draft_" + UUID.randomUUID().toString().replace("-", "").take(16)
        val draftsDir = File(context.filesDir, "report_drafts").apply { mkdirs() }
        val photoPath = savePhoto(draftsDir, id, photoBitmap)
        val videoPath = saveVideo(context, draftsDir, id, videoUri)
        val draft = OfflineReportDraft(
            id = id,
            userId = userId,
            incidentType = incidentType,
            assignedAgency = assignedAgency,
            description = description,
            locationLine = locationLine,
            photoPath = photoPath,
            videoPath = videoPath,
            latitude = latitude,
            longitude = longitude,
            createdAtMs = System.currentTimeMillis(),
            severity = severity,
            severityReason = severityReason,
            aiConfidence = aiConfidence,
            aiCategory = aiCategory,
            aiSummary = aiSummary,
            aiSynthesis = aiSynthesis,
            aiReportable = aiReportable,
            aiFile = aiFile,
            aiFrameAnalysisJson = aiFrameAnalysisJson,
        )
        val arr = loadArray(context)
        arr.put(toJson(draft))
        prefs(context).edit().putString(KEY_ARRAY, arr.toString()).apply()
        return draft
    }

    fun getAll(context: Context): List<OfflineReportDraft> {
        val arr = loadArray(context)
        val list = ArrayList<OfflineReportDraft>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            fromJson(obj)?.let { list.add(it) }
        }
        return list.sortedBy { it.createdAtMs }
    }

    fun remove(context: Context, draftId: String) {
        val arr = loadArray(context)
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optString("id") != draftId) {
                out.put(obj)
            } else {
                obj.optString("photoPath").takeIf { it.isNotBlank() }?.let { File(it).delete() }
                obj.optString("videoPath").takeIf { it.isNotBlank() }?.let { File(it).delete() }
            }
        }
        prefs(context).edit().putString(KEY_ARRAY, out.toString()).apply()
    }

    fun loadPhotoBitmap(path: String): Bitmap? =
        path.takeIf { it.isNotBlank() }?.let { BitmapFactory.decodeFile(it) }

    fun loadVideoUri(path: String): Uri? =
        path.takeIf { it.isNotBlank() && File(it).exists() }?.let { Uri.fromFile(File(it)) }

    private fun savePhoto(dir: File, draftId: String, bitmap: Bitmap?): String {
        val bmp = bitmap?.takeIf { !it.isRecycled } ?: return ""
        val file = File(dir, "$draftId.jpg")
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return file.absolutePath
    }

    private fun saveVideo(context: Context, dir: File, draftId: String, uri: Uri?): String {
        val source = uri ?: return ""
        return try {
            val file = File(dir, "$draftId.mp4")
            val input: InputStream? = when (source.scheme?.lowercase()) {
                "content" -> context.contentResolver.openInputStream(source)
                else -> File(source.path ?: "").takeIf { it.exists() }?.inputStream()
            }
            if (input == null) return ""
            input.use { inp ->
                file.outputStream().use { out -> inp.copyTo(out) }
            }
            file.absolutePath
        } catch (_: Exception) {
            ""
        }
    }

    private fun loadArray(context: Context): JSONArray {
        val raw = prefs(context).getString(KEY_ARRAY, null).orEmpty()
        return try {
            if (raw.isBlank()) JSONArray() else JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun toJson(d: OfflineReportDraft): JSONObject = JSONObject().apply {
        put("id", d.id)
        put("userId", d.userId)
        put("incidentType", d.incidentType)
        put("assignedAgency", d.assignedAgency)
        put("description", d.description)
        put("locationLine", d.locationLine)
        put("photoPath", d.photoPath)
        put("videoPath", d.videoPath)
        put("latitude", d.latitude ?: JSONObject.NULL)
        put("longitude", d.longitude ?: JSONObject.NULL)
        put("createdAtMs", d.createdAtMs)
        d.severity?.takeIf { it.isNotBlank() }?.let { put("severity", it) }
        d.severityReason?.takeIf { it.isNotBlank() }?.let { put("severityReason", it) }
        d.aiConfidence?.takeIf { it in 0..100 }?.let { put("aiConfidence", it) }
        d.aiCategory?.takeIf { it.isNotBlank() }?.let { put("aiCategory", it) }
        d.aiSummary?.takeIf { it.isNotBlank() }?.let { put("aiSummary", it) }
        d.aiSynthesis?.takeIf { it.isNotBlank() }?.let { put("aiSynthesis", it) }
        d.aiReportable?.let { put("aiReportable", it) }
        d.aiFile?.takeIf { it.isNotBlank() }?.let { put("aiFile", it) }
        d.aiFrameAnalysisJson?.takeIf { it.isNotBlank() }?.let { put("aiFrameAnalysisJson", it) }
    }

    private fun fromJson(o: JSONObject): OfflineReportDraft? {
        val id = o.optString("id")
        val userId = o.optString("userId")
        if (id.isBlank() || userId.isBlank()) return null
        val lat = if (o.isNull("latitude")) null else o.optDouble("latitude")
        val lng = if (o.isNull("longitude")) null else o.optDouble("longitude")
        val aiConf = if (o.has("aiConfidence") && !o.isNull("aiConfidence")) {
            o.optInt("aiConfidence").takeIf { it in 0..100 }
        } else {
            null
        }
        val aiReportable = if (o.has("aiReportable") && !o.isNull("aiReportable")) {
            o.optBoolean("aiReportable")
        } else {
            null
        }
        return OfflineReportDraft(
            id = id,
            userId = userId,
            incidentType = o.optString("incidentType"),
            assignedAgency = o.optString("assignedAgency"),
            description = o.optString("description"),
            locationLine = o.optString("locationLine"),
            photoPath = o.optString("photoPath"),
            videoPath = o.optString("videoPath"),
            latitude = lat,
            longitude = lng,
            createdAtMs = o.optLong("createdAtMs"),
            severity = o.optString("severity").takeIf { it.isNotBlank() },
            severityReason = o.optString("severityReason").takeIf { it.isNotBlank() },
            aiConfidence = aiConf,
            aiCategory = o.optString("aiCategory").takeIf { it.isNotBlank() },
            aiSummary = o.optString("aiSummary").takeIf { it.isNotBlank() },
            aiSynthesis = o.optString("aiSynthesis").takeIf { it.isNotBlank() },
            aiReportable = aiReportable,
            aiFile = o.optString("aiFile").takeIf { it.isNotBlank() },
            aiFrameAnalysisJson = o.optString("aiFrameAnalysisJson").takeIf { it.isNotBlank() },
        )
    }
}
