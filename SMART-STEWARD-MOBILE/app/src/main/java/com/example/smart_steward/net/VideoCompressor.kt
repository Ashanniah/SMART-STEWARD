package com.example.smart_steward.net

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Transcodes video to H.264/AAC MP4 for upload.
 * Media3 [Transformer] must be created and started on the main thread.
 */
@OptIn(UnstableApi::class)
object VideoCompressor {

  private const val MAX_EXPORT_WAIT_MINUTES = 15L
  private val mainHandler = Handler(Looper.getMainLooper())

  fun compress(
      context: Context,
      inputUri: Uri,
      outputFile: File,
      videoBitrate: Int,
      maxHeight: Int = 720,
      onProgress: ((Int) -> Unit)?,
  ): Boolean {
    outputFile.parentFile?.mkdirs()
    if (outputFile.exists()) outputFile.delete()

    val appContext = context.applicationContext
    val mediaItem = MediaItem.fromUri(inputUri)
    val editedMediaItem =
        EditedMediaItem.Builder(mediaItem)
            .setEffects(
                Effects(
                    /* audioProcessors= */ emptyList(),
                    /* videoEffects= */ listOf(Presentation.createForHeight(maxHeight)),
                ),
            )
            .build()
    val sequence = EditedMediaItemSequence.Builder(listOf(editedMediaItem)).build()
    val composition = Composition.Builder(sequence).build()

    val encoderFactory =
        DefaultEncoderFactory.Builder(appContext)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder().setBitrate(videoBitrate).build(),
            )
            .build()

    val request =
        TransformationRequest.Builder()
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .build()

    val exportLatch = CountDownLatch(1)
    val exportError = AtomicReference<ExportException?>(null)
    val completed = AtomicBoolean(false)
    val startError = AtomicReference<Throwable?>(null)
    val startLatch = CountDownLatch(1)

    mainHandler.post {
      try {
        val transformer =
            Transformer.Builder(appContext)
                .setTransformationRequest(request)
                .setEncoderFactory(encoderFactory)
                .addListener(
                    object : Transformer.Listener {
                      override fun onCompleted(
                          composition: Composition,
                          exportResult: ExportResult,
                      ) {
                        completed.set(true)
                        exportLatch.countDown()
                      }

                      override fun onError(
                          composition: Composition,
                          exportResult: ExportResult,
                          exportException: ExportException,
                      ) {
                        exportError.set(exportException)
                        exportLatch.countDown()
                      }
                    },
                )
                .build()
        transformer.start(composition, outputFile.absolutePath)
      } catch (t: Throwable) {
        startError.set(t)
        exportLatch.countDown()
      } finally {
        startLatch.countDown()
      }
    }

    if (!startLatch.await(30, TimeUnit.SECONDS)) {
      outputFile.delete()
      return false
    }
    startError.get()?.let { throw it }

    onProgress?.invoke(0)
    val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(MAX_EXPORT_WAIT_MINUTES)
    var uiProgress = 0
    while (exportLatch.count > 0 && System.currentTimeMillis() < deadline) {
      uiProgress = (uiProgress + 1).coerceAtMost(95)
      onProgress?.invoke(uiProgress)
      if (exportLatch.await(250, TimeUnit.MILLISECONDS)) {
        break
      }
    }

    if (exportLatch.count > 0) {
      exportLatch.await(MAX_EXPORT_WAIT_MINUTES, TimeUnit.MINUTES)
    }

    if (!completed.get()) {
      exportError.get()?.let { /* logged by caller if needed */ }
      outputFile.delete()
      return false
    }

    onProgress?.invoke(100)
    return outputFile.isFile && outputFile.length() > 0L
  }
}
