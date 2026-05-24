package com.example.smart_steward.net

/**
 * Optional progress hooks while POSTing media to the AI server.
 */
interface AiAnalyzeCallbacks {
    /** Called after local file is ready and before HTTP body is sent. */
    fun onUploadStarted(totalBytes: Long) {}

    /** [percent] is 0–100 when [totalBytes] is known. */
    fun onUploadProgress(percent: Int, bytesWritten: Long, totalBytes: Long) {}

    /** All request body bytes have been sent. */
    fun onUploadComplete() {}

    /** Waiting for server analysis response. */
    fun onWaitingForServer() {}
}

class VideoTooLargeException(val sizeBytes: Long) : Exception("Video exceeds size limit")
