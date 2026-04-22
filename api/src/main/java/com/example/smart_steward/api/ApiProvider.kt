package com.example.smart_steward.api

import android.content.Context
import com.example.smart_steward.api.services.ApiService
import com.example.smart_steward.api.services.FirebaseAuthApiService
import com.google.firebase.FirebaseApp

object ApiProvider {
    lateinit var auth: ApiService
        private set

    fun init(context: Context) {
        FirebaseApp.initializeApp(context)
        auth = FirebaseAuthApiService()
    }
}
