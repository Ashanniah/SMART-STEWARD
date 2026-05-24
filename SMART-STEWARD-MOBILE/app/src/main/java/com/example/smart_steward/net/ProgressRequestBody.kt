package com.example.smart_steward.net

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer

/**
 * Wraps a [RequestBody] and reports upload progress as bytes are written.
 */
class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (bytesWritten: Long, contentLength: Long) -> Unit,
    private val onComplete: (() -> Unit)? = null
) : RequestBody() {

    private var completed = false

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        val forwarding = object : ForwardingSink(sink) {
            private var written = 0L

            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                written += byteCount
                val length = if (total > 0L) total else written
                onProgress(written, length)
                if (!completed && total > 0L && written >= total) {
                    completed = true
                    onComplete?.invoke()
                }
            }
        }
        val buffered = forwarding.buffer()
        delegate.writeTo(buffered)
        buffered.flush()
    }
}
