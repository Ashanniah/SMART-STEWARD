package com.example.smart_steward

/**
 * Maps [UserReport.assignedAgency] free text to the same canonical keys the agency web app uses
 * (DENR, PNP, BFP, Barangay) for Firestore `targetAgency` and UI labels.
 */
object AgencyCanonical {

    private fun canonicalKey(segment: String): String? {
        val s = segment.lowercase().trim()
        if (s.isEmpty() || s == "n/a") return null
        if (s.contains("barangay") || s == "brgy") return "Barangay"
        if (s.contains("bfp") || s.contains("bureau of fire") || s.contains("fire protection")) {
            return "BFP"
        }
        if (s.contains("pnp") || s.contains("national police") || s == "police") return "PNP"
        if (s.contains("denr") || s.contains("environment and natural") || s.contains("environmental")) {
            return "DENR"
        }
        return when (s.uppercase()) {
            "DENR", "PNP", "BFP", "BARANGAY" -> when (s.uppercase()) {
                "BARANGAY" -> "Barangay"
                else -> s.uppercase()
            }
            else -> null
        }
    }

    /** Parses comma/semicolon-separated assignments from AI or Firestore. */
    fun parseAssignedAgencies(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.equals("N/A", ignoreCase = true)) return emptyList()
        val parts = trimmed.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return emptyList()
        val mapped = parts.mapNotNull { canonicalKey(it) }
        if (mapped.isNotEmpty()) return mapped.distinct()
        return canonicalKey(trimmed)?.let { listOf(it) } ?: emptyList()
    }

    fun targetKey(raw: String): String =
        parseAssignedAgencies(raw).firstOrNull() ?: "DENR"

    fun shortName(raw: String): String {
        val list = parseAssignedAgencies(raw)
        if (list.isEmpty()) return targetKey(raw)
        return list.joinToString(", ")
    }
}
