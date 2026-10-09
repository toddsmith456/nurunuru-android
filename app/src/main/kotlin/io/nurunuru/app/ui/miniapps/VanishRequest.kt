package io.nurunuru.app.ui.miniapps

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.nurunuru.app.data.NostrRepository
import io.nurunuru.app.data.*
import io.nurunuru.app.ui.theme.LineGreen
import io.nurunuru.app.ui.theme.LocalNuruColors
import kotlinx.coroutines.launch

@Composable
fun VanishRequest(
    pubkey: String,
    repository: NostrRepository
) {
    val nuruColors = LocalNuruColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var vanishMode by remember { mutableStateOf("relay") } // "relay" or "global"
    var vanishRelay by remember { mutableStateOf("") }
    var vanishReason by remember { mutableStateOf("") }
    var vanishConfirm by remember { mutableStateOf("") }
    var vanishLoading by remember { mutableStateOf(false) }
    var vanishResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        val confirmMessage = if (vanishMode == "global") {
            "Are you sure you want to send a deletion request to all relays? This cannot be undone."
        } else {
            "Send a deletion request to $vanishRelay?"
        }

        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirm deletion") },
            text = { Text(confirmMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        vanishLoading = true
                        vanishResult = null
                        coroutineScope.launch {
                            try {
                                val relays = if (vanishMode == "global") null else listOf(vanishRelay)
                                val success = repository.requestVanish(relays, vanishReason)

                                if (success) {
                                    vanishResult = true to "Deletion request sent (${if (vanishMode == "global") "All relays" else vanishRelay})"
                                    vanishConfirm = ""
                                    vanishReason = ""
                                } else {
                                    vanishResult = false to "Failed to send"
                                }
                            } catch (e: Exception) {
                                vanishResult = false to "Error: ${e.message}"
                            } finally {
                                vanishLoading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            color = nuruColors.bgSecondary,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Deletion request",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )

                Surface(
                    color = Color.Red.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "⚠️ This action cannot be undone",
                            fontSize = 14.sp,
                            color = Color.Red,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            "Sending a deletion request (kind 62) will cause the target relay to delete all your events.",
                            fontSize = 12.sp,
                            color = nuruColors.textTertiary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clickable { vanishMode = "relay" },
                        color = if (vanishMode == "relay") LineGreen else nuruColors.bgTertiary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text(
                                "Specific relay",
                                fontSize = 14.sp,
                                color = if (vanishMode == "relay") Color.White else nuruColors.textSecondary
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clickable { vanishMode = "global" },
                        color = if (vanishMode == "global") Color.Red else nuruColors.bgTertiary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text(
                                "All relays",
                                fontSize = 14.sp,
                                color = if (vanishMode == "global") Color.White else nuruColors.textSecondary
                            )
                        }
                    }
                }

                if (vanishMode == "relay") {
                    OutlinedTextField(
                        value = vanishRelay,
                        onValueChange = { vanishRelay = it },
                        placeholder = { Text("wss://...", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LineGreen,
                            unfocusedBorderColor = nuruColors.border
                        )
                    )
                }

                OutlinedTextField(
                    value = vanishReason,
                    onValueChange = { vanishReason = it },
                    placeholder = { Text("Reason (optional)", fontSize = 14.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LineGreen,
                        unfocusedBorderColor = nuruColors.border
                    )
                )

                OutlinedTextField(
                    value = vanishConfirm,
                    onValueChange = { vanishConfirm = it },
                    placeholder = { Text("Type "Delete" to confirm", fontSize = 14.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LineGreen,
                        unfocusedBorderColor = nuruColors.border
                    )
                )

                Button(
                    onClick = {
                        if (vanishMode == "relay" && vanishRelay.isBlank()) {
                            Toast.makeText(context, "Please enter a relay URL", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        showConfirmDialog = true
                    },
                    enabled = !vanishLoading && vanishConfirm == "Delete",
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (vanishMode == "global") Color.Red else Color(0xFFFF9800)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (vanishLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Send deletion request", fontWeight = FontWeight.Bold)
                    }
                }

                vanishResult?.let { (success, message) ->
                    Surface(
                        color = if (success) Color.Green.copy(alpha = 0.1f) else Color.Red.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            message,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 14.sp,
                            color = if (success) Color(0xFF4CAF50) else Color.Red
                        )
                    }
                }
            }
        }
    }
}
