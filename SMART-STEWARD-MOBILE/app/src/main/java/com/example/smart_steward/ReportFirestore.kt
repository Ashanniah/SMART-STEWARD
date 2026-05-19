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
                            userId,
                            CitizenNotificationKind.LIFECYCLE_SUBMITTED,
                            assignedAgency,
                            docId
                        )
                    } catch (_: Exception) {
                        /* non-fatal */
                    }
                    onSuccess(docId, hasPhoto || hasVideo)
                }
                .addOnFailureListener { e -> onError(e.message ?: "Failed to save report.") }
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
                val vRef = storage.reference.child("reports/$docId/report.mp4")
                vRef.putFile(videoUri!!)
                    .continueWithTask { t ->
                        if (!t.isSuccessful) throw t.exception ?: Exception("Video upload failed.")
                        vRef.downloadUrl
                    }
                    .addOnCompleteListener { vTask ->
                        if (!vTask.isSuccessful) {
                            val hint = storageFailureHint(vTask.exception)
                            Log.e(TAG, "Video upload failed; saving text-only if possible.", vTask.exception)
                            val bmp = photo?.takeIf { !it.isRecycled }
                            if (bmp != null) {
                                val pRef = storage.reference.child("reports/$docId/capture.jpg")
                                uploadJpeg(
                                    pRef,
                                    bmp,
                                    onUrl = { pUrl -> writeDocument(pUrl, "") },
                                    onFail = { ex ->
                                        onWarning?.invoke(hint)
                                        writeDocument("", "")
                                    }
                                )
                            } else {
                                onWarning?.invoke(hint)
                                writeDocument("", "")
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
                                onUrl = { pUrl -> writeDocument(pUrl, videoUrlStr) },
                                onFail = { ex ->
                                    val hint = storageFailureHint(ex)
                                    Log.e(TAG, "Thumbnail upload failed after video ok.", ex)
                                    onWarning?.invoke(hint)
                                    writeDocument("", videoUrlStr)
                                }
                            )
                        } else {
                            writeDocument("", videoUrlStr)
                        }
                    }
            }
            photo != null && !photo.isRecycled -> {
                val ref = storage.reference.child("reports/$docId/capture.jpg")
                uploadJpeg(
                    ref,
                    photo,
                    onUrl = { pUrl -> writeDocument(pUrl, "") },
                    onFail = { ex ->
                        val hint = storageFailureHint(ex)
                        Log.e(TAG, "Image upload failed; saving report without cloud photo.", ex)
                        onWarning?.invoke(hint)
                        writeDocument("", "")
                    }
                )
            }
            else -> writeDocument("", "")
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
