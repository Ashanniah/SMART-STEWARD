package com.example.smart_steward

/**
 * Maps [UserReport.assignedAgency] free text to the same canonical keys the agency web app uses
 * (DENR, PNP, BFP, Barangay) for Firestore `targetAgency` and UI labels.
 */
object AgencyCanonical {

    fun targetKey(raw: String): String {
        val s = raw.lowercase().trim()
        if (s.isEmpty()) return "DENR"
        if (s.contains("barangay") || s.contains("brgy")) return "Barangay"
        if (s.contains("bfp") || s.contains("bureau of fire") || s.contains("fire protection")) return "BFP"
        if (s.contains("pnp") || s.contains("national police") || s.contains("police")) return "PNP"
        if (s.contains("denr") || s.contains("environment and natural") || s.contains("environmental")) {
            return "DENR"
        }
        return "DENR"
    }

    fun shortName(raw: String): String = when (targetKey(raw)) {
        "DENR" -> "DENR"
        "PNP" -> "PNP"
        "BFP" -> "BFP"
        "Barangay" -> "Barangay"
        else -> "DENR"
    }
}
