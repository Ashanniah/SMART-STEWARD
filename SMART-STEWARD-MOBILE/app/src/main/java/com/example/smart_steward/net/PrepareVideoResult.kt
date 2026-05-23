package com.example.smart_steward.net

import java.io.File

data class PrepareVideoResult(
    val file: File,
    val originalSizeBytes: Long,
    val finalSizeBytes: Long,
    val wasCompressed: Boolean,
)
