package com.example.smart_steward

import android.content.Context
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth
import java.util.Locale

object ProfileInitials {

    fun fromDisplayName(displayName: String): String {
        val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return "?"
        if (parts.size == 1) return parts[0].take(2).uppercase(Locale.getDefault())
        return (parts[0].first().toString() + parts[1].first().toString()).uppercase(Locale.getDefault())
    }

    fun displayName(context: Context): String {
        val user = FirebaseAuth.getInstance().currentUser
        return user?.displayName?.trim().takeUnless { it.isNullOrEmpty() }
            ?: context.getString(R.string.profile_name_placeholder)
    }

    fun bind(textView: TextView) {
        textView.text = fromDisplayName(displayName(textView.context))
    }
}
