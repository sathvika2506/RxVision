package com.example.rxvision

// ─────────────────────────────────────────────────────────────────────────────
// Interaction Engine — pure rule-based, no ML needed
// ─────────────────────────────────────────────────────────────────────────────

data class InteractionRule(
    val drugA: String,
    val drugB: String,
    val status: InteractionStatus,
    val explanation: String,
    val recommendation: String
)

val interactionRules = listOf(
    // DANGER pairs
    InteractionRule("Aspirin", "Warfarin", InteractionStatus.DANGER,
        "Aspirin + Warfarin greatly increases bleeding risk.",
        "Avoid this combination. Seek immediate medical advice."),
    InteractionRule("Metformin", "Alcohol", InteractionStatus.DANGER,
        "Metformin + Alcohol may cause severe lactic acidosis.",
        "Do not consume alcohol while on Metformin."),
    InteractionRule("Simvastatin", "Amiodarone", InteractionStatus.DANGER,
        "Simvastatin + Amiodarone raises risk of serious muscle damage (rhabdomyolysis).",
        "Consult your cardiologist immediately."),
    InteractionRule("Lisinopril", "Potassium", InteractionStatus.DANGER,
        "Lisinopril + Potassium supplements may cause dangerous hyperkalemia.",
        "Avoid potassium supplements without medical supervision."),
    InteractionRule("SSRIs", "MAOIs", InteractionStatus.DANGER,
        "SSRIs + MAOIs can trigger life-threatening serotonin syndrome.",
        "Never combine these. Seek emergency care if symptoms appear."),
    InteractionRule("Tramadol", "SSRIs", InteractionStatus.DANGER,
        "Tramadol + SSRIs significantly raises risk of serotonin syndrome.",
        "Consult your doctor before combining these medications."),
    InteractionRule("Clopidogrel", "Omeprazole", InteractionStatus.DANGER,
        "Omeprazole reduces the effectiveness of Clopidogrel, increasing clot risk.",
        "Ask your doctor about alternative acid-reducing medications."),

    // MODERATE pairs
    InteractionRule("Ibuprofen", "Paracetamol", InteractionStatus.MODERATE,
        "Ibuprofen + Paracetamol together may increase strain on the liver and kidneys.",
        "Use at recommended doses and avoid long-term combination."),
    InteractionRule("Aspirin", "Ibuprofen", InteractionStatus.MODERATE,
        "Ibuprofen may reduce the antiplatelet effect of low-dose Aspirin.",
        "Take Aspirin 2 hours before Ibuprofen if both are needed."),
    InteractionRule("Metoprolol", "Verapamil", InteractionStatus.MODERATE,
        "Metoprolol + Verapamil may excessively slow your heart rate.",
        "Heart rate monitoring is recommended. Consult your cardiologist."),
    InteractionRule("Ciprofloxacin", "Antacids", InteractionStatus.MODERATE,
        "Antacids reduce absorption of Ciprofloxacin, lowering its effectiveness.",
        "Take Ciprofloxacin at least 2 hours before or 6 hours after antacids."),
    InteractionRule("Sertraline", "Tramadol", InteractionStatus.MODERATE,
        "Sertraline + Tramadol may lower the seizure threshold.",
        "Use with caution and report any unusual symptoms to your doctor."),
    InteractionRule("Atorvastatin", "Clarithromycin", InteractionStatus.MODERATE,
        "Clarithromycin raises Atorvastatin levels, increasing side-effect risk.",
        "Your doctor may temporarily stop your statin during antibiotic therapy."),
    InteractionRule("Digoxin", "Amiodarone", InteractionStatus.MODERATE,
        "Amiodarone increases Digoxin levels, risking toxicity.",
        "Monitor Digoxin levels closely if both are prescribed."),
    InteractionRule("Sildenafil", "Nitrates", InteractionStatus.MODERATE,
        "Sildenafil + Nitrates can cause severe blood pressure drops.",
        "Do not take these together. Contact your doctor immediately.")
)

fun analyzeInteraction(meds: List<String>): Triple<InteractionStatus, String, String> {
    if (meds.size < 2) {
        return Triple(
            InteractionStatus.SAFE,
            "Enter at least two medicines to check interactions.",
            "No action needed."
        )
    }

    var worstStatus = InteractionStatus.SAFE
    val explanations = mutableListOf<String>()
    val recommendations = mutableListOf<String>()

    for (i in meds.indices) {
        for (j in i + 1 until meds.size) {
            val a = meds[i].trim()
            val b = meds[j].trim()

            val match = interactionRules.firstOrNull { rule ->
                (rule.drugA.equals(a, ignoreCase = true) && rule.drugB.equals(b, ignoreCase = true)) ||
                (rule.drugA.equals(b, ignoreCase = true) && rule.drugB.equals(a, ignoreCase = true))
            }

            if (match != null) {
                if (match.status.ordinal > worstStatus.ordinal) {
                    worstStatus = match.status
                }
                explanations.add(match.explanation)
                recommendations.add(match.recommendation)
            }
        }
    }

    return if (explanations.isEmpty()) {
        Triple(
            InteractionStatus.SAFE,
            "No known interactions found between the listed medicines.",
            "Always verify with your pharmacist or physician."
        )
    } else {
        Triple(
            worstStatus,
            explanations.joinToString("\n\n"),
            recommendations.first()
        )
    }
}
