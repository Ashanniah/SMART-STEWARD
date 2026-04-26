package com.example.smart_steward.api.services

interface ApiService {
    fun call(
        route: String,
        params: Map<String, Any?> = emptyMap(),
        onSuccess: (Map<String, Any?>) -> Unit,
        onError: (String) -> Unit
    )
}
