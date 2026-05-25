package com.example.smart_steward

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageReference
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.util.Date

object ReportFirestore {
    private const val TAG = "ReportFirestore"

    private val firestore = FirebaseFirestore.getInstance()

    /** Uses `storage_bucket` from google-services.json (`gs://…`). */
    private val storage: FirebaseStorage by lazy {
        val bucket = FirebaseApp.getInstance().options.storageBucket?.trim().orEmpty()
        if (bucket.isEmpty()) {
            Log.w(TAG, "google-services.json has no storage_bucket; using default instance.")
            FirebaseStorage.getInstance()
        } else {
            val gsUrl = if (bucket.startsWith("gs://")) bucket else "gs://$bucket"
            Log.d(TAG, "FirebaseStorage bucket: $gsUrl")
            FirebaseStorage.getInstance(gsUrl)
        }
    }

    /**
     * @param photo Optional still image, or a **thumbnail frame** when [videoUri] is set.
     * @param videoUri When set, full video is uploaded and [UserReport.videoUrl] is filled.
     * @param onSuccess (documentId, anyMediaStoredInCloud)
     */
    fun submitReport(
        userId: String?,
        incidentType: String,
        assignedAgency: String,
        description: String,
        locationLine: String,
        photo: Bitmap?,
        videoUri: Uri? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        /** Gemini assessment: Low | Medium | High | Critical */
        severity: String? = null,
        /** Free-text justification from the AI for the chosen [severity]. */
        severityReason: String? = null,
        /** AI classification confidence 0–100 (stored for backend review rules; not shown to citizens). */
        aiConfidence: Int? = null,
        /** Raw `category` from the AI response (e.g. "Traffic Accident"). */
        aiCategory: String? = null,
        /** AI-generated summary (1–2 sentences) of the incident. */
        aiSummary: String? = null,
        /** AI synthesis paragraph that combines per-frame evidence. */
        aiSynthesis: String? = null,
        /** AI `reportable` flag — kept for backend auditing. */
        aiReportable: Boolean? = null,
        /** Source media kind reported by the AI ("video" | "image"). */
        aiFile: String? = null,
        /** Raw `frame_analysis` JSON array string from the AI response. */
        aiFrameAnalysisJson: String? = null,
        onSuccess: (String, Boolean) -> Unit,
        onError: (String) -> Unit,
        onWarning: ((String) -> Unit)? = null
    ) {
        val docRef = firestore.collection("reports").document()
        val docId = docRef.id
        val hasVideoUri = videoUri != null && videoUri != Uri.EMPTY

        fun writeDocument(photoUrl: String, videoUrl: String) {
            val hasPhoto = photoUrl.isNotEmpty()
            val hasVideo = videoUrl.isNotEmpty()
            val publicReportId = ReportRef.format(docId, Date())
            val data = hashMapOf<String, Any>(
                "publicReportId" to publicReportId,
                "userId" to (userId ?: ""),
                "incidentType" to incidentType,
                "assignedAgency" to assignedAgency,
                "description" to description,
                "locationLine" to locationLine,
                "hasPhoto" to hasPhoto,
                "hasVideo" to hasVideo,
                "photoUrl" to photoUrl,
                "videoUrl" to videoUrl,
                "status" to "pending",
                "submittedAt" to FieldValue.serverTimestamp()
            )
            val lat = latitude?.takeIf { it in -90.0..90.0 }
            val lng = longitude?.takeIf { it in -180.0..180.0 }
            if (lat != null && lng != null) {
                data["latitude"] = lat
                data["longitude"] = lng
            }

            normalizeSeverityForFirestore(severity)?.let { data["severity"] = it }
            severityReason?.trim()?.takeIf { it.isNotBlank() }?.let { data["severityReason"] = it }
            aiConfidence?.takeIf { it in 0..100 }?.let { data["aiConfidence"] = it }
            aiCategory?.trim()?.takeIf { it.isNotBlank() }?.let { data["aiCategory"] = it }
            aiSummary?.trim()?.takeIf { it.isNotBlank() }?.let { data["aiSummary"] = it }
            aiSynthesis?.trim()?.takeIf { it.isNotBlank() }?.let { data["aiSynthesis"] = it }
            aiReportable?.let { data["aiReportable"] = it }
            aiFile?.trim()?.takeIf { it.isNotBlank() }?.let { data["aiFile"] = it }
            parseFrameAnalysisToList(aiFrameAnalysisJson)?.let { data["aiFrameAnalysis"] = it }

            docRef.set(data)
                .addOnSuccessListener {
                    try {
                        AgencyNotificationsFirestore.notifyNewReportForAgency(
                            docId,
                            incidentType,
                            locationLine,
                            assignedAgency
                        )
                    } catch (_: Exception) {
                        /* non-fatal */
                    }
                    try {
                        CitizenNotificationsRepository.append(
                            userId = userId,
                            kind = CitizenNotificationKind.LIFECYCLE_SUBMITTED,
                            agency = assignedAgency,
                            reportId = docId,
                            incidentType = incidentType,
                            publicReportId = publicReportId
                        )
                    } catch (_: Exception) {
                        /* non-fatal */
                    }
                    onSuccess(docId, hasPhoto || hasVideo)
                }
                .addOnFailureListener { e -> onError(e.message ?: "Failed to save report.") }
        }

        fun patchMedia(photoUrl: String, videoUrl: String) {
            val hasPhoto = photoUrl.isNotEmpty()
            val hasVideo = videoUrl.isNotEmpty()
            docRef.update(
                mapOf(
                    "photoUrl" to photoUrl,
                    "videoUrl" to videoUrl,
                    "hasPhoto" to hasPhoto,
                    "hasVideo" to hasVideo,
                )
            )
        }

        fun uploadJpeg(
            ref: StorageReference,
            bitmap: Bitmap,
            onUrl: (String) -> Unit,
            onFail: (Exception?) -> Unit
        ) {
            val bytes = ByteArrayOutputStream().apply {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, this)
            }.toByteArray()
            if (bytes.isEmpty()) {
                onUrl("")
                return
            }
            ref.putBytes(bytes)
                .continueWithTask { uploadTask ->
                    if (!uploadTask.isSuccessful) {
                        throw uploadTask.exception ?: Exception("Upload failed.")
                    }
                    ref.downloadUrl
                }
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        onUrl(task.result?.toString().orEmpty())
                    } else {
                        onFail(task.exception)
                    }
                }
        }

        when {
            hasVideoUri -> {
                writeDocument("", "")
                val vRef = storage.reference.child("reports/$docId/report.mp4")
                vRef.putFile(videoUri!!)
                    .continueWithTask { t ->
                        if (!t.isSuccessful) throw t.exception ?: Exception("Video upload failed.")
                        vRef.downloadUrl
                    }
                    .addOnCompleteListener { vTask ->
                        if (!vTask.isSuccessful) {
                            val hint = storageFailureHint(vTask.exception)
                            Log.e(TAG, "Video upload failed; report saved without video.", vTask.exception)
                            val bmp = photo?.takeIf { !it.isRecycled }
                            if (bmp != null) {
                                val pRef = storage.reference.child("reports/$docId/capture.jpg")
                                uploadJpeg(
                                    pRef,
                                    bmp,
                                    onUrl = { pUrl -> patchMedia(pUrl, "") },
                                    onFail = { ex ->
                                        onWarning?.invoke(hint)
                                    }
                                )
                            } else {
                                onWarning?.invoke(hint)
                            }
                            return@addOnCompleteListener
                        }
                        val videoUrlStr = vTask.result?.toString().orEmpty()
                        val bmp = photo?.takeIf { !it.isRecycled }
                        if (bmp != null) {
                            val pRef = storage.reference.child("reports/$docId/capture.jpg")
                            uploadJpeg(
                                pRef,
                                bmp,
                                onUrl = { pUrl -> patchMedia(pUrl, videoUrlStr) },
                                onFail = { ex ->
                                    val hint = storageFailureHint(ex)
                                    Log.e(TAG, "Thumbnail upload failed after video ok.", ex)
                                    patchMedia("", videoUrlStr)
                                    onWarning?.invoke(hint)
                                }
                            )
                        } else {
                            patchMedia("", videoUrlStr)
                        }
                    }
            }
            photo != null && !photo.isRecycled -> {
                writeDocument("", "")
                val ref = storage.reference.child("reports/$docId/capture.jpg")
                uploadJpeg(
                    ref,
                    photo,
                    onUrl = { pUrl -> patchMedia(pUrl, "") },
                    onFail = { ex ->
                        val hint = storageFailureHint(ex)
                        Log.e(TAG, "Image upload failed; report saved without cloud photo.", ex)
                        onWarning?.invoke(hint)
                    }
                )
            }
            else -> writeDocument("", "")
        }
    }

    /** Maps API labels to lowercase Firestore values expected by the agency web app. */
    private fun normalizeSeverityForFirestore(raw: String?): String? {
        val s = raw?.trim()?.lowercase().orEmpty()
        if (s.isBlank() || s == "—" || s == "-") return null
        return when (s) {
            "low", "medium", "high", "critical" -> s
            else -> null
        }
    }

    /**
     * Converts the raw `frame_analysis` JSON string into a list of plain maps
     * so Firestore stores it as a queryable array of objects rather than as text.
     */
    private fun parseFrameAnalysisToList(raw: String?): List<Map<String, Any>>? {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) return null
        return try {
            val arr = JSONArray(text)
            val out = ArrayList<Map<String, Any>>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val entry = mutableMapOf<String, Any>()
                val frameNumber = obj.opt("frame_number")
                if (frameNumber is Number) {
                    entry["frame_number"] = frameNumber.toInt()
                } else {
                    val n = frameNumber?.toString()?.toIntOrNull()
                    if (n != null) entry["frame_number"] = n
                }
                obj.optString("physical_description").trim()
                    .takeIf { it.isNotBlank() }
                    ?.let { entry["physical_description"] = it }
                if (entry.isNotEmpty()) out.add(entry)
            }
            out.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse aiFrameAnalysisJson: ${e.message}")
            null
        }
    }

    private fun storageFailureHint(ex: Throwable?): String {
        val base = "Media was not saved to Cloud Storage. Your report was still saved."
        val storage = ex as? StorageException
        val code = storage?.errorCode
        val isNotFound =
            code == StorageException.ERROR_OBJECT_NOT_FOUND ||
                code == StorageException.ERROR_BUCKET_NOT_FOUND ||
                ex?.message?.contains("404", ignoreCase = true) == true ||
                ex?.message?.contains("Not Found", ignoreCase = true) == true
        return if (isNotFound) {
            "$base In Firebase Console: Build → Storage → Get started (create the default bucket), then submit again."
        } else {
            "$base (${ex?.message ?: "unknown error"})"
        }
    }
}
