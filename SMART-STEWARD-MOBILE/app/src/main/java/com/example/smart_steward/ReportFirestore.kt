package com.example.smart_steward

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import java.io.ByteArrayOutputStream

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
     * @param onSuccess (documentId, photoStoredInCloud)
     */
    fun submitReport(
        userId: String?,
        incidentType: String,
        assignedAgency: String,
        description: String,
        locationLine: String,
        photo: Bitmap?,
        onSuccess: (String, Boolean) -> Unit,
        onError: (String) -> Unit,
        onWarning: ((String) -> Unit)? = null
    ) {
        val docRef = firestore.collection("reports").document()
        val docId = docRef.id

        fun writeDocument(
            photoUrl: String,
            hasPhoto: Boolean,
            onWritten: () -> Unit = { onSuccess(docId, hasPhoto && photoUrl.isNotEmpty()) }
        ) {
            val data = hashMapOf<String, Any>(
                "userId" to (userId ?: ""),
                "incidentType" to incidentType,
                "assignedAgency" to assignedAgency,
                "description" to description,
                "locationLine" to locationLine,
                "hasPhoto" to hasPhoto,
                "photoUrl" to photoUrl,
                "status" to "pending",
                "submittedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(data)
                .addOnSuccessListener { onWritten() }
                .addOnFailureListener { e -> onError(e.message ?: "Failed to save report.") }
        }

        val bitmap = photo
        if (bitmap != null && !bitmap.isRecycled) {
            val bytes = ByteArrayOutputStream().apply {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, this)
            }.toByteArray()
            if (bytes.isEmpty()) {
                writeDocument("", false)
                return
            }
            val ref = storage.reference.child("reports/$docId/capture.jpg")
            ref.putBytes(bytes)
                .continueWithTask { uploadTask ->
                    if (!uploadTask.isSuccessful) {
                        throw uploadTask.exception ?: Exception("Upload failed.")
                    }
                    ref.downloadUrl
                }
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uri = task.result ?: return@addOnCompleteListener
                        writeDocument(uri.toString(), true)
                    } else {
                        val hint = storageFailureHint(task.exception)
                        Log.e(TAG, "Image upload failed; saving report without cloud photo.", task.exception)
                        writeDocument("", false) {
                            onWarning?.invoke(hint)
                            onSuccess(docId, false)
                        }
                    }
                }
        } else {
            writeDocument("", false)
        }
    }

    private fun storageFailureHint(ex: Throwable?): String {
        val base = "Photo was not saved to Cloud Storage. Your report was still saved."
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
