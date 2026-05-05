package com.example.rxvision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.rxvision.ui.theme.*
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// Data
// ─────────────────────────────────────────────────────────────────────────────

enum class InteractionStatus {
    SAFE, MODERATE, DANGER;

    val label: String get() = when (this) {
        SAFE     -> "All Clear"
        MODERATE -> "Moderate Risk"
        DANGER   -> "High Risk"
    }
    val color: Color get() = when (this) {
        SAFE     -> SafeGreen
        MODERATE -> ModerateAmber
        DANGER   -> DangerRed
    }
    val bgColor: Color get() = when (this) {
        SAFE     -> SafeGreenBg
        MODERATE -> ModerateAmberBg
        DANGER   -> DangerRedBg
    }
    val icon: androidx.compose.ui.graphics.vector.ImageVector get() = when (this) {
        SAFE     -> Icons.Filled.CheckCircle
        MODERATE -> Icons.Filled.Warning
        DANGER   -> Icons.Filled.Dangerous
    }
}

data class ScanRecord(
    val meds: List<String>,
    val status: InteractionStatus,
    val summary: String
)

// ─────────────────────────────────────────────────────────────────────────────
// Root Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RxVisionScreen() {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    // Camera
    val capturedBitmap = remember { mutableStateOf<Bitmap?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp -> capturedBitmap.value = bmp }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) cameraLauncher.launch(null) }

    fun launchCamera() {
        val check = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (check == PackageManager.PERMISSION_GRANTED) cameraLauncher.launch(null)
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Input & result state
    var userInput by remember { mutableStateOf("") }
    var resultStatus by remember { mutableStateOf(InteractionStatus.SAFE) }
    var resultText by remember { mutableStateOf("Enter medicines above to check interactions.") }
    var resultRec by remember { mutableStateOf("") }
    var resultSource by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scanHistory = remember { mutableStateListOf<ScanRecord>() }

    fun analyze() {
        focusManager.clearFocus()
        val meds = userInput.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (meds.isEmpty()) return

        // Step 1: Run offline rule engine immediately for instant feedback
        val (offlineStatus, offlineExplanation, offlineRec) = analyzeInteraction(meds)
        resultStatus = offlineStatus
        resultText = offlineExplanation
        resultRec = offlineRec
        resultSource = "Offline Rules"

        // Step 2: Fetch FDA data asynchronously, then merge results
        scope.launch {
            isLoading = true
            try {
                val d1 = if (meds.isNotEmpty()) fetchDrugInfo(meds[0]) else null
                val d2 = if (meds.size >= 2) fetchDrugInfo(meds[1]) else null

                val fdaResult = analyzeFdaInteraction(d1, d2)

                // Pick worst-case between offline rules and FDA result
                val mergedStatus = if (fdaResult.riskLevel.ordinal > offlineStatus.ordinal)
                    fdaResult.riskLevel else offlineStatus

                val mergedExplanation = buildString {
                    if (offlineExplanation != "No known interactions found between the listed medicines." &&
                        offlineExplanation != "Enter at least two medicines to check interactions.") {
                        append("📋 Rule-Based:\n$offlineExplanation\n\n")
                    }
                    append("🔬 FDA Label Data:\n${fdaResult.explanation}")
                }

                resultStatus = mergedStatus
                resultText = mergedExplanation
                resultRec = fdaResult.recommendation
                resultSource = "FDA OpenData + Rules"

                scanHistory.add(0, ScanRecord(
                    meds = meds,
                    status = mergedStatus,
                    summary = fdaResult.explanation.lines().first().take(60)
                ))
            } catch (e: Exception) {
                // FDA fetch failed — keep offline result, just note it
                resultSource = "Offline Rules (FDA unavailable)"
                if (scanHistory.none { it.meds == meds }) {
                    scanHistory.add(0, ScanRecord(
                        meds = meds,
                        status = offlineStatus,
                        summary = offlineExplanation.lines().first().take(60)
                    ))
                }
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(containerColor = DarkBackground, contentColor = TextPrimary) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            // Header
            item { HeaderSection() }

            // Camera card
            item {
                CameraSection(
                    bitmap = capturedBitmap.value,
                    onCameraClick = ::launchCamera
                )
            }

            // Input section
            item {
                InputSection(
                    value = userInput,
                    onValueChange = { userInput = it },
                    onAnalyze = ::analyze
                )
            }

            // Result card
            item {
                ResultSection(
                    status = resultStatus,
                    explanation = resultText,
                    recommendation = resultRec,
                    meds = userInput.split(",").map { it.trim() }.filter { it.isNotBlank() },
                    isLoading = isLoading,
                    sourceLabel = resultSource
                )
            }

            // History
            if (scanHistory.isNotEmpty()) {
                item { HistoryHeader() }
                items(scanHistory) { record ->
                    HistoryItem(record)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HeaderSection() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        IndigoAccent.copy(alpha = 0.18f),
                        DarkBackground
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 36.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            // Pill badge
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ElevatedGrey)
                    .padding(horizontal = 14.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(6.dp).clip(CircleShape).background(SafeGreen)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "AI Safety Assistant",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary, fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(
                        brush = Brush.horizontalGradient(listOf(IndigoAccent, PurpleAccent)),
                        fontWeight = FontWeight.Black, fontSize = 46.sp, letterSpacing = (-1.5).sp
                    )) { append("Rx") }
                    withStyle(SpanStyle(
                        color = TextPrimary, fontWeight = FontWeight.Black,
                        fontSize = 46.sp, letterSpacing = (-1.5).sp
                    )) { append("Vision") }
                },
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Scan medications · Check interactions · Stay safe",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextSecondary, textAlign = TextAlign.Center
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Camera Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CameraSection(bitmap: Bitmap?, onCameraClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {

        // Camera preview / placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(SurfaceGrey)
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                IndigoAccent.copy(alpha = glowAlpha),
                                PurpleAccent.copy(alpha = glowAlpha)
                            )
                        )
                    ),
                    RoundedCornerShape(24.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Captured medicine",
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))
                )
                // overlay badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(CircleShape)
                        .background(SafeGreen)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Photo Captured", style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White, fontWeight = FontWeight.Bold
                    ))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(ElevatedGrey),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.CameraAlt, "Camera",
                            tint = IndigoAccent, modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("No photo yet", style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
                    Text("Tap the button below to scan", style = MaterialTheme.typography.bodySmall.copy(color = TextMuted))
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Camera button
        Button(
            onClick = onCameraClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(
                    Brush.horizontalGradient(listOf(IndigoAccent, PurpleAccent)),
                    CircleShape
                ),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        ) {
            Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Scan Medicine Label", fontWeight = FontWeight.SemiBold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Input Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun InputSection(
    value: String,
    onValueChange: (String) -> Unit,
    onAnalyze: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
        Text(
            "Enter Medicines Manually",
            style = MaterialTheme.typography.titleSmall.copy(
                color = TextPrimary, fontWeight = FontWeight.Bold
            )
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Comma-separated  e.g.  Aspirin, Warfarin",
            style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "Paracetamol, Ibuprofen, ...",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
                )
            },
            leadingIcon = {
                Icon(Icons.Outlined.MedicalServices, null, tint = IndigoAccent)
            },
            trailingIcon = {
                if (value.isNotBlank()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Filled.Clear, null, tint = TextMuted)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAnalyze() }),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = IndigoAccent,
                unfocusedBorderColor = DividerGrey,
                focusedContainerColor = SurfaceGrey,
                unfocusedContainerColor = SurfaceGrey,
                cursorColor = IndigoAccent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onAnalyze,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent),
            enabled = value.isNotBlank()
        ) {
            Icon(Icons.Filled.Search, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Analyze Interactions", fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Result Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ResultSection(
    status: InteractionStatus,
    explanation: String,
    recommendation: String,
    meds: List<String>,
    isLoading: Boolean = false,
    sourceLabel: String = ""
) {
    val animBg by animateColorAsState(status.bgColor, tween(500), label = "bg")
    val animColor by animateColorAsState(status.color, tween(500), label = "col")

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {

        // Status banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(animBg)
                .border(BorderStroke(1.dp, animColor.copy(alpha = 0.35f)), RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(animColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(status.icon, null, tint = animColor, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    status.label,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = animColor, fontWeight = FontWeight.ExtraBold
                    )
                )
                Text(
                    when (status) {
                        InteractionStatus.SAFE -> "No known interactions found"
                        InteractionStatus.MODERATE -> "Use with caution"
                        InteractionStatus.DANGER -> "Seek medical advice"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(color = animColor.copy(alpha = 0.75f))
                )
            }
            // badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(animColor.copy(alpha = 0.20f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    status.name,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = animColor, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp
                    )
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // FDA source + loading indicator row
        if (isLoading || sourceLabel.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ElevatedGrey)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = IndigoAccent
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Fetching FDA data...",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                } else {
                    Icon(
                        Icons.Outlined.VerifiedUser, null,
                        tint = SafeGreen, modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Source: $sourceLabel",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextMuted)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (meds.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceGrey),
                border = BorderStroke(1.dp, DividerGrey)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Medication, null, tint = IndigoAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Detected Medicines (${meds.size})",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = TextSecondary, fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(meds) { med ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ChipBackground)
                                    .border(BorderStroke(1.dp, ChipBorder), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    med,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = TextPrimary, fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        // Explanation card
        if (explanation.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceGrey),
                border = BorderStroke(1.dp, DividerGrey)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Info, null, tint = IndigoAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Interaction Details",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = TextSecondary, fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        explanation,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary, lineHeight = 22.sp
                        )
                    )

                    if (recommendation.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ElevatedGrey)
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Outlined.Lightbulb, null,
                                tint = ModerateAmber, modifier = Modifier.size(16.dp).padding(top = 2.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                recommendation,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary, lineHeight = 18.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Scan History
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HistoryHeader() {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.History, null, tint = IndigoAccent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Recent Scans",
            style = MaterialTheme.typography.titleSmall.copy(
                color = TextPrimary, fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
fun HistoryItem(record: ScanRecord) {
    val animColor by animateColorAsState(record.status.color, tween(400), label = "hcolor")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceGrey)
            .border(BorderStroke(1.dp, DividerGrey), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(record.status.bgColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(record.status.icon, null, tint = animColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                record.meds.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = TextPrimary, fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1
            )
            Text(
                record.summary,
                style = MaterialTheme.typography.bodySmall.copy(color = TextMuted),
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(record.status.bgColor)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                record.status.name,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = animColor, fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preview
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0C0C0C)
@Composable
fun RxVisionPreview() {
    RxVisionTheme { RxVisionScreen() }
}
