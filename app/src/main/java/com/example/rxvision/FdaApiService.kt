package com.example.rxvision

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// ─────────────────────────────────────────────────────────────────────────────
// Data Classes
// ─────────────────────────────────────────────────────────────────────────────

data class DrugResponse(
    val results: List<DrugResult>?
)

data class DrugResult(
    val purpose: List<String>?,
    val warnings: List<String>?,
    @SerializedName("indications_and_usage")
    val indicationsAndUsage: List<String>?,
    @SerializedName("drug_interactions")
    val drugInteractions: List<String>?,
    @SerializedName("adverse_reactions")
    val adverseReactions: List<String>?
)

data class FdaInteractionResult(
    val riskLevel: InteractionStatus,
    val explanation: String,
    val recommendation: String,
    val sourceLabel: String = "FDA Drug Label"
)

// ─────────────────────────────────────────────────────────────────────────────
// Retrofit Interface
// ─────────────────────────────────────────────────────────────────────────────

interface DrugApiService {
    @GET("drug/label.json")
    suspend fun getDrugInfo(
        @Query("search") query: String,
        @Query("limit") limit: Int = 1
    ): DrugResponse
}

// ─────────────────────────────────────────────────────────────────────────────
// Singleton Retrofit client
// ─────────────────────────────────────────────────────────────────────────────

object DrugApiClient {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.fda.gov/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: DrugApiService = retrofit.create(DrugApiService::class.java)
}

// ─────────────────────────────────────────────────────────────────────────────
// Fetch function
// ─────────────────────────────────────────────────────────────────────────────

suspend fun fetchDrugInfo(drugName: String): DrugResult? {
    return try {
        val query = "openfda.generic_name:\"$drugName\""
        val response = DrugApiClient.api.getDrugInfo(query)
        response.results?.firstOrNull()
    } catch (e: Exception) {
        // Try brand name as fallback
        try {
            val query = "openfda.brand_name:\"$drugName\""
            val response = DrugApiClient.api.getDrugInfo(query)
            response.results?.firstOrNull()
        } catch (e2: Exception) {
            null
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FDA-based Interaction Analysis
// ─────────────────────────────────────────────────────────────────────────────

private val dangerKeywords = listOf(
    "bleeding", "hemorrhage", "fatal", "death", "seizure",
    "cardiac arrest", "anaphylaxis", "serotonin syndrome",
    "respiratory depression", "stroke", "thrombosis"
)

private val moderateKeywords = listOf(
    "liver", "hepatic", "kidney", "renal", "caution",
    "monitor", "increased risk", "reduced efficacy",
    "interactions", "enzyme", "metabolism"
)

fun analyzeFdaInteraction(d1: DrugResult?, d2: DrugResult?): FdaInteractionResult {
    // Combine all warning text from both drugs
    val combinedText = buildString {
        d1?.warnings?.forEach { append(it.lowercase()); append(" ") }
        d1?.drugInteractions?.forEach { append(it.lowercase()); append(" ") }
        d1?.adverseReactions?.forEach { append(it.lowercase()); append(" ") }
        d2?.warnings?.forEach { append(it.lowercase()); append(" ") }
        d2?.drugInteractions?.forEach { append(it.lowercase()); append(" ") }
        d2?.adverseReactions?.forEach { append(it.lowercase()); append(" ") }
    }

    val foundDanger = dangerKeywords.filter { combinedText.contains(it) }
    val foundModerate = moderateKeywords.filter { combinedText.contains(it) }

    return when {
        d1 == null && d2 == null -> FdaInteractionResult(
            riskLevel = InteractionStatus.SAFE,
            explanation = "No FDA data found for either medicine. Using offline rule base only.",
            recommendation = "Consult your pharmacist for accurate interaction information."
        )

        foundDanger.isNotEmpty() -> FdaInteractionResult(
            riskLevel = InteractionStatus.DANGER,
            explanation = buildString {
                append("⚠️ FDA warnings flag high-risk signals between these medicines.\n\n")
                append("Detected risk factors: ${foundDanger.joinToString(", ")}.\n\n")
                d1?.warnings?.firstOrNull()?.take(300)?.let { append("Drug 1 warning: $it...") }
            },
            recommendation = "Do NOT combine without direct physician supervision. Seek medical advice immediately."
        )

        foundModerate.isNotEmpty() -> FdaInteractionResult(
            riskLevel = InteractionStatus.MODERATE,
            explanation = buildString {
                append("⚠️ FDA data indicates moderate caution is advised.\n\n")
                append("Key factors: ${foundModerate.joinToString(", ")}.\n\n")
                d1?.warnings?.firstOrNull()?.take(200)?.let { append("Note: $it...") }
            },
            recommendation = "Consult your doctor or pharmacist before combining these medications."
        )

        else -> FdaInteractionResult(
            riskLevel = InteractionStatus.SAFE,
            explanation = buildString {
                append("✅ No high-risk interaction signals found in FDA label data.\n\n")
                d1?.indicationsAndUsage?.firstOrNull()?.take(180)?.let {
                    append("Drug 1 usage: $it...")
                }
            },
            recommendation = "Always verify with your pharmacist. FDA data covers labeled uses only."
        )
    }
}
