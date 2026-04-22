package com.example.smart_steward

import android.app.Application
import com.example.smart_steward.api.ApiProvider

class SmartStewardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiProvider.init(this)
    }
}
