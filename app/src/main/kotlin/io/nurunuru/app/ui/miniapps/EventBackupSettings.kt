package io.nurunuru.app.ui.miniapps

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nurunuru.app.data.NostrRepository
import io.nurunuru.app.data.models.ImportResult
import io.nurunuru.app.data.models.NostrEvent
import io.nurunuru.app.data.*
import io.nurunuru.app.ui.icons.NuruIcons
import io.nurunuru.app.ui.theme.LineGreen
import io.nurunuru.app.ui.theme.LocalNuruColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

private val KIND_LABELS = mapOf(
    0 to "Profile",
    1 to "Text posts",
    3 to "Follow list",
    5 to "Delete",
    6 to "Reposts",
    7 to "Reactions",
    10000 to "Mute list",
    10002 to "Relay list",
    30023 to "Long-form articles"
)

private fun getKindLabel(kind: Int): String {
    return KIND_LABELS[kind] ?: "kind: $kind"
}

@Composable
fun EventBackupSettings(
    pubkey: String,
    repository: NostrRepository
) {
    val nuruColors = LocalNuruColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    var activeTab by remember { mutableStateOf("export") }

    // Export state
    var exporting by remember { mutableStateOf(false) }
    var exportProgressFetched by remember { mutableStateOf(0) }
    var exportProgressBatch by remember { mutableStateOf(0) }
    var exportedEvents by remember { mutableStateOf<List<NostrEvent>>(emptyList()) }
    var exportError by remember { mutableStateOf<String?>(null) }

    // Import state
    var importing by remember { mutableStateOf(false) }
    var importProgressCurrent by remember { mutableStateOf(0) }
    var importProgressTotal by remember { mutableStateOf(0) }
    var importProgressSuccess by remember { mutableStateOf(0) }
    var importProgressFailed by remember { mutableStateOf(0) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    var parsedEvents by remember { mutableStateOf<List<NostrEvent>>(emptyList()) }

    // File selection for Export (Saving)
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        OutputStreamWriter(stream).use { writer ->
                            writer.write(json.encodeToString(exportedEvents))
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("EventBackup", "Failed to save file", e)
                }
            }
        }
    }

    // File selection for Import (Opening)
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { it.readText() }
                    if (content != null) {
                        val events = json.decodeFromString<List<NostrEvent>>(content)
                        withContext(Dispatchers.Main) {
                            parsedEvents = events
                            importError = if (events.isEmpty()) "No valid events found" else null
                            importResult = null
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("EventBackup", "Failed to parse file", e)
                    withContext(Dispatchers.Main) {
                        importError = "Failed to read file: ${e.message}"
                        parsedEvents = emptyList()
                    }
                }
            }
        }
    }

    val handleExport = {
        exporting = true
        exportError = null
        exportProgressFetched = 0
        exportProgressBatch = 0
        exportedEvents = emptyList()

        scope.launch {
            try {
                val events = repository.fetchAllUserEvents(pubkey) { fetched, batch ->
                    exportProgressFetched = fetched
                    exportProgressBatch = batch
                }
                exportedEvents = events
                if (events.isEmpty()) {
                    exportError = "No events to export"
                }
            } catch (e: Exception) {
                exportError = "Export failed: ${e.message}"
            } finally {
                exporting = false
            }
        }
    }

    val handleImport = {
        if (parsedEvents.isNotEmpty()) {
            importing = true
            importError = null
            importResult = null
            importProgressCurrent = 0
            importProgressTotal = parsedEvents.size
            importProgressSuccess = 0
            importProgressFailed = 0

            scope.launch {
                try {
                    val result = repository.importEventsToRelays(parsedEvents) { current, total, success, failed ->
                        importProgressCurrent = current
                        importProgressTotal = total
                        importProgressSuccess = success
                        importProgressFailed = failed
                    }
                    importResult = result
                } catch (e: Exception) {
                    importError = "Import failed: ${e.message}"
                } finally {
                    importing = false
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tab Selector
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(nuruColors.bgSecondary, RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                TabButton(
                    label = "Export",
                    isSelected = activeTab == "export",
                    onClick = { activeTab = "export" },
                    modifier = Modifier.weight(1f)
                )
                TabButton(
                    label = "Import",
                    isSelected = activeTab == "import",
                    onClick = { activeTab = "import" },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (activeTab == "export") {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "You can back up all events in JSON format.",
                        fontSize = 14.sp,
                        color = nuruColors.textTertiary
                    )

                    if (exportedEvents.isEmpty() && !exporting) {
                        Button(
                            onClick = { handleExport() },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LineGreen)
                        ) {
                            Text("Fetch events", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (exporting) {
                        Surface(
                            color = nuruColors.bgSecondary,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = LineGreen,
                                    strokeWidth = 2.dp
                                )
                                Column {
                                    Text("Fetching events...", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${exportProgressFetched} fetched (batch ${exportProgressBatch})",
                                        fontSize = 12.sp,
                                        color = nuruColors.textTertiary
                                    )
                                }
                            }
                        }
                    }

                    exportError?.let {
                        Surface(
                            color = Color.Red.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.Red.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                it,
                                modifier = Modifier.padding(12.dp),
                                color = Color.Red,
                                fontSize = 14.sp
                            )
                        }
                    }

                    if (exportedEvents.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(
                                color = nuruColors.bgSecondary,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "${exportedEvents.size} events",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    val stats = getEventStats(exportedEvents)
                                    stats.take(8).forEach { (kind, count) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(getKindLabel(kind), fontSize = 12.sp, color = nuruColors.textTertiary)
                                            Text(count.toString(), fontSize = 12.sp, color = nuruColors.textSecondary)
                                        }
                                    }

                                    if (stats.size > 8) {
                                        Text(
                                            "...and ${stats.size - 8} more types",
                                            fontSize = 12.sp,
                                            color = nuruColors.textTertiary,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }

                                    val protectedCount = exportedEvents.count { it.isProtected() }
                                    if (protectedCount > 0) {
                                        Text(
                                            "Includes ${protectedCount} protected events (NIP-70)",
                                            fontSize = 12.sp,
                                            color = nuruColors.textTertiary,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                    val filename = "nostr-backup-${pubkey.take(8)}-$dateStr.json"
                                    exportLauncher.launch(filename)
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LineGreen)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(NuruIcons.Download, null, modifier = Modifier.size(18.dp))
                                    Text("Save JSON file", fontWeight = FontWeight.Bold)
                                }
                            }

                            TextButton(
                                onClick = { exportedEvents = emptyList() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Fetch again", color = nuruColors.textTertiary, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "You can import events from a JSON file. Only your own events are sent to the relays.",
                        fontSize = 14.sp,
                        color = nuruColors.textTertiary
                    )

                    if (parsedEvents.isEmpty() && !importing) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(Color.Transparent)
                                .border(1.dp, nuruColors.border, RoundedCornerShape(16.dp))
                                .clickable { importLauncher.launch(arrayOf("application/json")) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Outlined.FileUpload,
                                    null,
                                    modifier = Modifier.size(32.dp),
                                    tint = nuruColors.textTertiary
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("Click to select a file", fontSize = 14.sp, color = nuruColors.textSecondary)
                                Text("JSON backup file", fontSize = 12.sp, color = nuruColors.textTertiary)
                            }
                        }
                    }

                    importError?.let {
                        Surface(
                            color = Color.Red.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.Red.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                it,
                                modifier = Modifier.padding(12.dp),
                                color = Color.Red,
                                fontSize = 14.sp
                            )
                        }
                    }

                    if (parsedEvents.isNotEmpty() && importResult == null) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Surface(
                                color = nuruColors.bgSecondary,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "${parsedEvents.size} events",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    val stats = getEventStats(parsedEvents)
                                    stats.take(6).forEach { (kind, count) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(getKindLabel(kind), fontSize = 12.sp, color = nuruColors.textTertiary)
                                            Text(count.toString(), fontSize = 12.sp, color = nuruColors.textSecondary)
                                        }
                                    }

                                    val otherUsersCount = parsedEvents.count { it.pubkey != pubkey }
                                    if (otherUsersCount > 0) {
                                        Surface(
                                            color = Color(0xFFFFC107).copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.padding(top = 12.dp).fillMaxWidth()
                                        ) {
                                            Text(
                                                "${otherUsersCount} are other users' events (they will be skipped on import)",
                                                modifier = Modifier.padding(8.dp),
                                                color = Color(0xFF856404),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    val protectedCount = parsedEvents.count { it.isProtected() }
                                    if (protectedCount > 0) {
                                        Text(
                                            "${protectedCount} protected events (NIP-70)",
                                            fontSize = 12.sp,
                                            color = nuruColors.textTertiary,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                }
                            }

                            if (importing) {
                                Surface(
                                    color = nuruColors.bgSecondary,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = LineGreen,
                                                strokeWidth = 2.dp
                                            )
                                            Text("Importing...", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        }

                                        LinearProgressIndicator(
                                            progress = { importProgressCurrent.toFloat() / importProgressTotal.coerceAtLeast(1) },
                                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                            color = LineGreen,
                                            trackColor = nuruColors.bgTertiary,
                                        )

                                        Text(
                                            "${importProgressCurrent} / ${importProgressTotal} (${importProgressSuccess} succeeded)",
                                            fontSize = 12.sp,
                                            color = nuruColors.textTertiary,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        onClick = { handleImport() },
                                        modifier = Modifier.weight(1f).height(56.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = LineGreen)
                                    ) {
                                        Text("Start import", fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = {
                                            parsedEvents = emptyList()
                                            importError = null
                                        },
                                        modifier = Modifier.width(100.dp).height(56.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = nuruColors.bgSecondary)
                                    ) {
                                        Text("Clear", color = nuruColors.textSecondary)
                                    }
                                }
                            }
                        }
                    }

                    importResult?.let { result ->
                        Surface(
                            color = nuruColors.bgSecondary,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Import complete", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ResultCard(
                                        value = result.success.toString(),
                                        label = "Succeeded",
                                        color = Color(0xFF4CAF50),
                                        modifier = Modifier.weight(1f)
                                    )
                                    ResultCard(
                                        value = result.failed.toString(),
                                        label = "Failed",
                                        color = Color(0xFFF44336),
                                        modifier = Modifier.weight(1f)
                                    )
                                    ResultCard(
                                        value = result.skipped.toString(),
                                        label = "Skipped",
                                        color = Color(0xFF757575),
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                if (result.skipped > 0) {
                                    Text(
                                        "Protected events (NIP-70) and other users' events were skipped",
                                        fontSize = 11.sp,
                                        color = nuruColors.textTertiary,
                                        modifier = Modifier.padding(top = 12.dp)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                parsedEvents = emptyList()
                                importResult = null
                                importError = null
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = nuruColors.bgSecondary)
                        ) {
                            Text("Import another file", color = nuruColors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nuruColors = LocalNuruColors.current
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) LineGreen else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else nuruColors.textTertiary
        )
    }
}

@Composable
private fun ResultCard(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 11.sp, color = color)
        }
    }
}

private fun getEventStats(events: List<NostrEvent>): List<Pair<Int, Int>> {
    val stats = mutableMapOf<Int, Int>()
    for (event in events) {
        stats[event.kind] = (stats[event.kind] ?: 0) + 1
    }
    return stats.entries
        .sortedByDescending { it.value }
        .map { it.key to it.value }
}
