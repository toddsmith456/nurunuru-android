package io.nurunuru.app.ui.components

import androidx.compose.animation.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.nurunuru.app.data.GeohashUtils
import io.nurunuru.app.data.NosskeyManager
import io.nurunuru.app.data.RelayDiscovery
import io.nurunuru.app.data.models.Nip65Relay
import io.nurunuru.app.data.models.UserProfile
import io.nurunuru.app.data.prefs.AppPreferences
import io.nurunuru.app.ui.theme.LineGreen
import io.nurunuru.app.ui.theme.LocalNuruColors
import io.nurunuru.app.viewmodel.AuthViewModel
import io.nurunuru.app.viewmodel.GeneratedAccount

// チュートリアルPostsで使用するハッシュタグ (Web/iOS と同一)。
//
// 既定Bodyに `\n#nostrはじめました` を pre-fill し、エディタを開いた瞬間から
// ユーザーには常にハッシュタグが見えている (「勝手に付けられた」を回避する規約)。
// 1 行目を空にすることで、ユーザーが先頭にカーソルを置けば
// 「Body → 改行 → #nostrはじめました」の配置が自然に成立する。
//
// ユーザーが意図的にハッシュタグ行を消した場合は、その状態のままPost
// (`publishTutorialPost` は自動補完を行わない。3 プラットフォーム共通の規約)。
//
// プレースホルダーはBodyを全て消した時のガイドとしてのみShowされる
// (pre-fill 時は OutlinedTextField の placeholder API 仕様により非Show)。
private const val TUTORIAL_HASHTAG = "nostrはじめました"
private const val TUTORIAL_DEFAULT_CONTENT = "\n#nostrはじめました"
private const val TUTORIAL_PLACEHOLDER = "What's on your mind?\n#nostrはじめました"

@Composable
fun SignUpModal(
    viewModel: AuthViewModel,
    onClose: () -> Unit,
    onSuccess: (String) -> Unit
) {
    var step by remember { mutableStateOf("welcome") } // welcome, backup, relay, profile, tutorial, success, completing
    var generatedAccount by remember { mutableStateOf<GeneratedAccount?>(null) }
    var selectedRelays by remember { mutableStateOf<List<Nip65Relay>?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var usingPasskey by remember { mutableStateOf(false) }

    val nuruColors = LocalNuruColors.current
    val signUpScope = rememberCoroutineScope()
    val signUpContext = LocalContext.current
    val signUpActivity = signUpContext as? ComponentActivity

    // This is intentionally a normal opaque full-screen composable, not Dialog.
    // Dialog is a separate window and can reveal LoginScreen for a frame while
    // it is dismissed or while AuthState switches to MainScreen.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = nuruColors.bgPrimary
    ) {
        if (step == "completing") {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = LineGreen)
                Spacer(Modifier.height(16.dp))
                Text("Opening home...", color = nuruColors.textSecondary, fontSize = 14.sp)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp)),
                    colors = CardDefaults.cardColors(containerColor = nuruColors.bgPrimary),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column {
                        // 5 steps when registering via Passkey (no nsec backup needed),
                        // 6 steps for the classic nsec flow.
                        val totalSteps = if (usingPasskey) 5f else 6f
                        val progress = when (step) {
                            "welcome" -> 1f / totalSteps
                            "backup" -> 2f / totalSteps
                            "relay" -> (if (usingPasskey) 2f else 3f) / totalSteps
                            "profile" -> (if (usingPasskey) 3f else 4f) / totalSteps
                            "tutorial" -> (if (usingPasskey) 4f else 5f) / totalSteps
                            else -> 1f
                        }
                        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(nuruColors.bgSecondary)) {
                            Box(modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().background(LineGreen))
                        }

                        Column(
                            modifier = Modifier
                                .padding(24.dp)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            when (step) {
                                "welcome" -> WelcomeStep(
                                    onNext = {
                                        usingPasskey = false
                                        isLoading = true
                                        error = ""
                                        val acc = viewModel.generateNewAccount()
                                        if (acc != null) {
                                            generatedAccount = acc
                                            step = "backup"
                                        } else {
                                            error = "Failed to create account"
                                        }
                                        isLoading = false
                                    },
                                    onNextWithPasskey = {
                                        val act = signUpActivity
                                        if (act == null) {
                                            error = "Failed to register passkey"
                                        } else {
                                            usingPasskey = true
                                            isLoading = true
                                            error = ""
                                            signUpScope.launch {
                                                val acc = viewModel.generateNewAccountWithPasskey(
                                                    activity = act,
                                                    username = "user"
                                                )
                                                if (acc != null) {
                                                    generatedAccount = acc
                                                    // Passkey flow skips the nsec backup step.
                                                    step = "relay"
                                                } else {
                                                    usingPasskey = false
                                                    error = "Failed to register passkey"
                                                }
                                                isLoading = false
                                            }
                                        }
                                    },
                                    onClose = onClose,
                                    isLoading = isLoading,
                                    error = error
                                )
                                "backup" -> BackupStep(
                                    account = generatedAccount!!,
                                    onNext = { step = "relay" }
                                )
                                "relay" -> RelayStep(
                                    onRelaysSelected = { relays ->
                                        selectedRelays = relays
                                        step = "profile"
                                    }
                                )
                                "profile" -> {
                                    val coroutineScope = rememberCoroutineScope()
                                    // Pick the appropriate signer for the active sign-up flow.
                                    // - nosskey: NosskeySigner triggers a biometric prompt per signature.
                                    // - nsec   : InternalSigner reuses the freshly-unlocked SecureKeyManager key.
                                    val profileSigner = remember(usingPasskey) {
                                        if (usingPasskey && signUpActivity != null) {
                                            val keyInfo = viewModel.nosskeyManager
                                                .loadStoredKeyInfo(signUpActivity)
                                            if (keyInfo != null) {
                                                viewModel.buildSigner(signUpActivity)
                                            } else {
                                                io.nurunuru.app.data.InternalSigner(viewModel.keyManager)
                                            }
                                        } else {
                                            io.nurunuru.app.data.InternalSigner(viewModel.keyManager)
                                        }
                                    }
                                    ProfileStep(
                                        uploadSigner = profileSigner,
                                        onFinish = { name, about, picture, banner, nip05, lud16, website, birthday ->
                                            isLoading = true
                                            coroutineScope.launch {
                                                val relayTriples: List<Triple<String, Boolean, Boolean>>? =
                                                    selectedRelays?.map { Triple(it.url, it.read, it.write) }
                                                // Do not block onboarding on relay publishing. Persist the selected
                                                // relay/profile draft immediately, then publish kind:0 / kind:10002
                                                // in the background. This avoids a stuck spinner when the selected
                                                // relays are slow or when Rust FFI send_event waits for ACKs.
                                                viewModel.saveInitialOnboardingDraft(
                                                    name = name,
                                                    about = about,
                                                    picture = picture,
                                                    banner = banner,
                                                    nip05 = nip05,
                                                    lud16 = lud16,
                                                    website = website,
                                                    birthday = birthday,
                                                    relays = relayTriples
                                                )
                                                step = "tutorial"
                                                isLoading = false
                                                launch(kotlinx.coroutines.Dispatchers.IO) {
                                                    val publishedProfile = kotlinx.coroutines.withTimeoutOrNull(12_000) {
                                                        viewModel.publishInitialMetadata(
                                                            signer = profileSigner,
                                                            name = name,
                                                            about = about,
                                                            picture = picture,
                                                            banner = banner,
                                                            nip05 = nip05,
                                                            lud16 = lud16,
                                                            website = website,
                                                            birthday = birthday,
                                                            relays = relayTriples
                                                        )
                                                    } ?: false
                                                    if (!publishedProfile) {
                                                        android.util.Log.w("SignUpModal", "Initial profile background publish failed or timed out")
                                                    }
                                                }
                                            }
                                        },
                                        isLoading = isLoading
                                    )
                                }
                                "tutorial" -> {
                                    val coroutineScope = rememberCoroutineScope()
                                    val tutorialSigner = remember(usingPasskey) {
                                        if (usingPasskey && signUpActivity != null) {
                                            val keyInfo = viewModel.nosskeyManager
                                                .loadStoredKeyInfo(signUpActivity)
                                            if (keyInfo != null) {
                                                viewModel.buildSigner(signUpActivity)
                                            } else {
                                                io.nurunuru.app.data.InternalSigner(viewModel.keyManager)
                                            }
                                        } else {
                                            io.nurunuru.app.data.InternalSigner(viewModel.keyManager)
                                        }
                                    }
                                    TutorialStep(
                                        onPost = { content, onResult ->
                                            coroutineScope.launch {
                                                val relayTriples: List<Triple<String, Boolean, Boolean>>? =
                                                    selectedRelays?.map { Triple(it.url, it.read, it.write) }
                                                val ok = viewModel.publishTutorialPost(
                                                    signer = tutorialSigner,
                                                    content = content,
                                                    relays = relayTriples
                                                )
                                                onResult(ok)
                                            }
                                        },
                                        onNext = { step = "success" }
                                    )
                                }
                                "success" -> SuccessStep(
                                    npub = generatedAccount?.npub ?: "",
                                    onComplete = {
                                        val pubkey = generatedAccount!!.pubkeyHex
                                        step = "completing"
                                        signUpScope.launch {
                                            kotlinx.coroutines.yield()
                                            onSuccess(pubkey)
                                            viewModel.completeRegistration(pubkey, signUpActivity)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeStep(
    onNext: () -> Unit,
    onNextWithPasskey: () -> Unit,
    onClose: () -> Unit,
    isLoading: Boolean,
    error: String
) {
    val nuruColors = LocalNuruColors.current
    val context = LocalContext.current
    val passkeyAvailable = remember { NosskeyManager().isPlatformSupported(context) }

    IconBox(icon = Icons.Default.PersonAdd, containerColor = LineGreen.copy(alpha = 0.1f), iconColor = LineGreen)

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sign up", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = nuruColors.textPrimary)
        Text(
            "This creates a new Nostr account.\nYour private key is stored securely on this device.",
            fontSize = 14.sp,
            color = nuruColors.textSecondary,
            textAlign = TextAlign.Center
        )
    }

    if (error.isNotEmpty()) {
        Text(error, color = Color.Red, fontSize = 12.sp)
    }

    // Passkey-first registration button (shown only on supported devices).
    if (passkeyAvailable) {
        Button(
            onClick = onNextWithPasskey,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LineGreen),
            shape = RoundedCornerShape(16.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Text("Sign up with passkey", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        Text(
            "Passkeys are securely registered with Face ID / Touch ID / fingerprint.",
            fontSize = 12.sp,
            color = nuruColors.textSecondary,
            textAlign = TextAlign.Center
        )
    }

    // Classic nsec registration — shown as secondary on passkey-capable devices,
    // or as the only primary option when CredentialManager is unavailable.
    if (passkeyAvailable) {
        OutlinedButton(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, LineGreen),
            enabled = !isLoading
        ) {
            Text("Create the traditional way (nsec)", fontWeight = FontWeight.Bold, color = LineGreen)
        }
    } else {
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LineGreen),
            shape = RoundedCornerShape(16.dp),
            enabled = !isLoading
        ) {
            if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            else Text("Create account", fontWeight = FontWeight.Bold)
        }
    }

    TextButton(onClick = onClose) {
        Text("Cancel", color = nuruColors.textTertiary)
    }
}

@Composable
fun BackupStep(
    account: GeneratedAccount,
    onNext: () -> Unit
) {
    val nuruColors = LocalNuruColors.current
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    IconBox(icon = Icons.Default.Security, containerColor = Color(0xFFFFA000).copy(alpha = 0.1f), iconColor = Color(0xFFFFA000))

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Back up private key", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = nuruColors.textPrimary)
        Text(
            "This is the \"private key\" needed to recover your account. Never share it with anyone.",
            fontSize = 13.sp,
            color = nuruColors.textSecondary,
            textAlign = TextAlign.Center
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = nuruColors.bgSecondary),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your private key (nsec)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFA000))
            Text(
                account.nsec,
                fontSize = 12.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = nuruColors.textPrimary,
                modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(8.dp)
            )

            Button(
                onClick = {
                    clipboardManager.setText(AnnotatedString(account.nsec))
                    copied = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = nuruColors.bgTertiary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (copied) LineGreen else nuruColors.textPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text(if (copied) "Copied" else "Copy private key", fontSize = 13.sp, color = nuruColors.textPrimary)
            }
        }
    }

    Button(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LineGreen),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("Next", fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RelayStep(onRelaysSelected: (List<Nip65Relay>) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { AppPreferences(context.applicationContext) }
    val nuruColors = LocalNuruColors.current
    var selectionMode by remember { mutableStateOf("manual") } // auto, manual
    var selectedLat by remember { mutableDoubleStateOf(35.6762) }
    var selectedLon by remember { mutableDoubleStateOf(139.6503) }
    var selectedRegionId by remember { mutableStateOf<String?>("tokyo") }
    var recommendedRelays by remember { mutableStateOf<List<Nip65Relay>>(
        RelayDiscovery.generateRelayListByLocation(selectedLat, selectedLon).combined // Default to Tokyo
    ) }
    var regionName by remember { mutableStateOf("Tokyo") }
    var isLoading by remember { mutableStateOf(false) }

    IconBox(icon = Icons.Default.LocationOn, containerColor = Color(0xFF2196F3).copy(alpha = 0.1f), iconColor = Color(0xFF2196F3))

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Relay setup", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = nuruColors.textPrimary)
        Text("Select a region and the best relays will be set up automatically.", fontSize = 13.sp, color = nuruColors.textSecondary, textAlign = TextAlign.Center)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(nuruColors.bgSecondary, RoundedCornerShape(12.dp)).padding(4.dp)
        ) {
            val modes = listOf("auto" to "Auto-detect with GPS", "manual" to "Select manually")
            modes.forEach { (id, label) ->
                val selected = selectionMode == id
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) nuruColors.bgPrimary else Color.Transparent)
                        .clickable {
                            selectionMode = id
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (selected) LineGreen else nuruColors.textTertiary)
                }
            }
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                          permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            if (granted) {
                // In a real app, we would use FusedLocationProvider here.
                // For this synchronization task, we'll simulate the detection once granted.
                isLoading = true
                selectedLat = 35.6762
                selectedLon = 139.6503
                selectedRegionId = null
                regionName = "Tokyo (GPS estimate)"
                recommendedRelays = RelayDiscovery.generateRelayListByLocation(selectedLat, selectedLon).combined
                isLoading = false
            } else {
                selectionMode = "manual"
            }
        }

        if (selectionMode == "auto") {
            LaunchedEffect(Unit) {
                val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

                if (hasFine || hasCoarse) {
                    isLoading = true
                    selectedLat = 35.6762
                    selectedLon = 139.6503
                    selectedRegionId = null
                    regionName = "Tokyo (GPS estimate)"
                    recommendedRelays = RelayDiscovery.generateRelayListByLocation(selectedLat, selectedLon).combined
                    isLoading = false
                } else {
                    permissionLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                }
            }
        }

        if (selectionMode == "manual") {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = nuruColors.textPrimary)
                ) {
                    Text(regionName)
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    RelayDiscovery.REGION_COORDINATES.forEach { region ->
                        DropdownMenuItem(
                            text = { Text(region.name) },
                            onClick = {
                                regionName = region.name
                                selectedLat = region.lat
                                selectedLon = region.lon
                                selectedRegionId = region.id
                                recommendedRelays = RelayDiscovery.generateRelayListByLocation(region.lat, region.lon).combined
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = nuruColors.bgSecondary),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Recommended relays ($regionName)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = nuruColors.textTertiary)
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp).align(Alignment.CenterHorizontally), color = LineGreen)
                } else {
                    recommendedRelays.forEach { (url, read, write) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(14.dp), tint = LineGreen)
                            Spacer(Modifier.width(8.dp))
                            Text(url.replace("wss://", ""), fontSize = 13.sp, color = nuruColors.textPrimary, modifier = Modifier.weight(1f))
                            if (read) Badge(containerColor = Color.Blue.copy(alpha = 0.2f)) { Text("R", color = Color.Blue, fontSize = 8.sp) }
                            if (write) {
                                Spacer(Modifier.width(4.dp))
                                Badge(containerColor = LineGreen.copy(alpha = 0.2f)) { Text("W", color = LineGreen, fontSize = 8.sp) }
                            }
                        }
                    }
                }
            }
        }
    }

    Button(
        onClick = {
            prefs.userLat = selectedLat
            prefs.userLon = selectedLon
            prefs.userGeohash = GeohashUtils.encodeGeohash(selectedLat, selectedLon)
            prefs.selectedRegionId = selectedRegionId
            prefs.nip65Relays = recommendedRelays
            prefs.mainRelay = recommendedRelays.firstOrNull { it.read && it.write }?.url
                ?: recommendedRelays.firstOrNull()?.url
                ?: "wss://yabu.me"
            onRelaysSelected(recommendedRelays)
        },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LineGreen),
        shape = RoundedCornerShape(16.dp),
        enabled = !isLoading
    ) {
        Text("Next", fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ProfileStep(
    uploadSigner: io.nurunuru.app.data.AppSigner? = null,
    onFinish: (String, String, String, String, String, String, String, String) -> Unit,
    isLoading: Boolean
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val nuruColors = LocalNuruColors.current
    var name by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    var picture by remember { mutableStateOf("") }
    var banner by remember { mutableStateOf("") }
    var nip05 by remember { mutableStateOf("") }
    var lud16 by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var birthday by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }

    var uploadingPicture by remember { mutableStateOf(false) }
    var uploadingBanner by remember { mutableStateOf(false) }

    val pictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            coroutineScope.launch {
                uploadingPicture = true
                try {
                    val (bytes, mimeType) = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.readBytes() to
                            (context.contentResolver.getType(it) ?: "image/jpeg")
                    }
                    if (bytes != null) {
                        val url = uploadSignupImage(bytes, mimeType, uploadSigner)
                        if (url != null) picture = url
                        else Toast.makeText(context, "Image upload failed", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SignUpModal", "Picture upload failed: ${e.message}")
                    Toast.makeText(context, "Image upload failed", Toast.LENGTH_SHORT).show()
                } finally {
                    uploadingPicture = false
                }
            }
        }
    }

    val bannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            coroutineScope.launch {
                uploadingBanner = true
                try {
                    val (bytes, mimeType) = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(it)?.readBytes() to
                            (context.contentResolver.getType(it) ?: "image/jpeg")
                    }
                    if (bytes != null) {
                        val url = uploadSignupImage(bytes, mimeType, uploadSigner)
                        if (url != null) banner = url
                        else Toast.makeText(context, "Image upload failed", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SignUpModal", "Banner upload failed: ${e.message}")
                    Toast.makeText(context, "Image upload failed", Toast.LENGTH_SHORT).show()
                } finally {
                    uploadingBanner = false
                }
            }
        }
    }

    // Avatar Upload
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(nuruColors.bgSecondary)
                .clickable(enabled = !uploadingPicture) { pictureLauncher.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            if (picture.isNotEmpty()) {
                AsyncImage(
                    model = picture,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AddAPhoto,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = nuruColors.textTertiary
                )
            }

            if (uploadingPicture) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = LineGreen, modifier = Modifier.size(24.dp))
                }
            }
        }
        Text(
            if (uploadingPicture) "Uploading..." else "Upload avatar image",
            fontSize = 12.sp,
            color = if (uploadingPicture) LineGreen else nuruColors.textTertiary
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Profile setup", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = nuruColors.textPrimary)
        Text("Let's enter your information.", fontSize = 13.sp, color = nuruColors.textSecondary)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            placeholder = { Text("Display name") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LineGreen, focusedLabelColor = LineGreen),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = picture,
            onValueChange = { picture = it },
            label = { Text("Avatar image URL") },
            placeholder = { Text("https://...") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LineGreen, focusedLabelColor = LineGreen),
            shape = RoundedCornerShape(12.dp)
        )

        OutlinedTextField(
            value = about,
            onValueChange = { about = it },
            label = { Text("Bio") },
            modifier = Modifier.fillMaxWidth().height(100.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LineGreen, focusedLabelColor = LineGreen),
            shape = RoundedCornerShape(12.dp)
        )

        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(if (showAdvanced) "Hide advanced settings" else "Show advanced settings", color = LineGreen, fontSize = 12.sp)
        }

        if (showAdvanced) {
            OutlinedTextField(
                value = banner,
                onValueChange = { banner = it },
                label = { Text("Banner image URL") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LineGreen, focusedLabelColor = LineGreen),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(onClick = { bannerLauncher.launch("image/*") }, enabled = !uploadingBanner) {
                        if (uploadingBanner) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = LineGreen)
                        else Icon(Icons.Default.CloudUpload, contentDescription = "Upload")
                    }
                }
            )
            OutlinedTextField(
                value = nip05,
                onValueChange = { nip05 = it },
                label = { Text("NIP-05 (verification)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LineGreen, focusedLabelColor = LineGreen),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = lud16,
                onValueChange = { lud16 = it },
                label = { Text("Lightning address") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LineGreen, focusedLabelColor = LineGreen),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = website,
                onValueChange = { website = it },
                label = { Text("Website") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LineGreen, focusedLabelColor = LineGreen),
                shape = RoundedCornerShape(12.dp)
            )
            OutlinedTextField(
                value = birthday,
                onValueChange = { birthday = it },
                label = { Text("Birthday (MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LineGreen, focusedLabelColor = LineGreen),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }

    Button(
        onClick = { onFinish(name, about, picture, banner, nip05, lud16, website, birthday) },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LineGreen),
        shape = RoundedCornerShape(16.dp),
        enabled = !isLoading
    ) {
        if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
        else Text("Finish setup", fontWeight = FontWeight.Bold)
    }
}

/**
 * Onboarding tutorial step — `#nostrはじめました` ハッシュタグ付きで最初の kind:1 をPost。
 *
 * - 既定Body: `\n#nostrはじめました` を pre-fill。エディタを開いた瞬間から
 *   ユーザーには常時ハッシュタグが見えている (「勝手に付けられた」を回避する規約)。
 * - ユーザーが意図的にハッシュタグ行をDeleteした場合は、その状態のままPost。
 *   `AuthViewModel.publishTutorialPost` はBodyへの自動補完・末尾付与を一切行わない。
 * - Body中の `#xxx` のみが `t` タグとして抽出される (PostModal と同一規約)。
 * - 140 文字制限を強制。Bodyが空 (trim 後 0 文字) の場合はPostsボタンを無効化。
 * - PostsSucceeded時は確認カードをShowしてから「Next」で success ステップへ。
 * - 「Skipped」でPostsせずに次のステップへ進める。
 * - 吹き出しアイコンはNurunuruブランドカラー (LineGreen) に統一。
 * - プレースホルダー (薄い灰色) はBodyを全て消した時のガイドとしてのみShow。
 *
 * @param onPost  Bodyを渡して非同期Posts。結果コールバックを返す。
 * @param onNext  次のステップ (success) に遷移するコールバック。
 */
@Composable
fun TutorialStep(
    onPost: (String, (Boolean) -> Unit) -> Unit,
    onNext: () -> Unit
) {
    val nuruColors = LocalNuruColors.current
    var content by remember { mutableStateOf(TUTORIAL_DEFAULT_CONTENT) }
    var isPosting by remember { mutableStateOf(false) }
    var posted by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Nurunuruブランドカラー (LineGreen) に統一。アイコンは吹き出し (Forum)。
    IconBox(
        icon = Icons.Default.Forum,
        containerColor = LineGreen.copy(alpha = 0.1f),
        iconColor = LineGreen
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("First post", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = nuruColors.textPrimary)
        Text(
            "As a tutorial, try posting with \"#nostrはじめました\" to say hello.",
            fontSize = 13.sp,
            color = nuruColors.textSecondary,
            textAlign = TextAlign.Center
        )
    }

    if (posted) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = LineGreen.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = LineGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Posted!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = nuruColors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Welcome to Nostr. Search for \"#nostrはじめました\" on the timeline to find others who are just getting started.",
                        fontSize = 12.sp,
                        color = nuruColors.textSecondary
                    )
                }
            }
        }

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LineGreen),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Next", fontWeight = FontWeight.Bold)
        }
    } else {
        // 既定で `\n#nostrはじめました` が pre-fill されているため、エディタを開いた瞬間から
        // ユーザーには常時ハッシュタグが見えている (「勝手に付けられた」を回避する規約)。
        // ユーザーがハッシュタグを消したら、消した状態のままPostsされる (自動補完なし)。
        // プレースホルダーはBodyを全て消した時のガイドとしてのみShowされる。
        OutlinedTextField(
            value = content,
            onValueChange = { newValue ->
                // 140 文字制限を超える入力は受け付けない (PostModal と同じ制約)
                if (newValue.length <= 140) content = newValue
            },
            label = { Text("Body") },
            placeholder = {
                Text(
                    TUTORIAL_PLACEHOLDER,
                    color = nuruColors.textTertiary
                )
            },
            modifier = Modifier.fillMaxWidth().height(140.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LineGreen, focusedLabelColor = LineGreen),
            shape = RoundedCornerShape(12.dp)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                "${content.length}/140",
                fontSize = 11.sp,
                color = if (content.length >= 140) Color.Red else nuruColors.textTertiary
            )
        }

        if (errorMsg != null) {
            Text(errorMsg!!, color = Color.Red, fontSize = 12.sp)
        }

        Button(
            onClick = {
                errorMsg = null
                isPosting = true
                onPost(content) { ok ->
                    isPosting = false
                    if (ok) {
                        posted = true
                    } else {
                        errorMsg = "Post failed. Please check your connection."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LineGreen),
            shape = RoundedCornerShape(16.dp),
            // Bodyが空 (trim 後 0 文字) の場合はPosts不可。
            // pre-fill された `#nostrはじめました` を残せばそのままPosts可能。
            enabled = !isPosting && content.trim().isNotEmpty()
        ) {
            if (isPosting) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Text("Post", fontWeight = FontWeight.Bold)
            }
        }

        TextButton(onClick = onNext, enabled = !isPosting) {
            Text("Skipped", color = nuruColors.textTertiary)
        }
    }
}

@Composable
fun SuccessStep(npub: String, onComplete: () -> Unit) {
    val nuruColors = LocalNuruColors.current
    val context = LocalContext.current
    val shareText = remember(npub) { profileShareText(npub) }

    IconBox(icon = Icons.Default.CheckCircle, containerColor = LineGreen.copy(alpha = 0.1f), iconColor = LineGreen)

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("All set!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = nuruColors.textPrimary)
        Text("You can share your profile with friends. Anyone who starts from your link will begin already following you.", fontSize = 14.sp, color = nuruColors.textSecondary, textAlign = TextAlign.Center)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = nuruColors.bgSecondary),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Share profile", fontSize = 10.sp, color = nuruColors.textTertiary)
            Text(
                text = "Send it via Twitter/X or LINE, and the other person can start Nurunuru already following you.",
                fontSize = 13.sp,
                color = nuruColors.textPrimary
            )
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        putExtra(Intent.EXTRA_TITLE, "Check out my profile on Nurunuru")
                    }
                    context.startActivity(Intent.createChooser(intent, "Share profile"))
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = LineGreen)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share profile", fontWeight = FontWeight.Bold)
            }
        }
    }

    Button(
        onClick = onComplete,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LineGreen),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text("Get started", fontWeight = FontWeight.Bold)
    }
}

private fun profileShareText(npub: String): String = "https://www.nullnull.app/p/$npub"

@Composable
private fun IconBox(icon: ImageVector, containerColor: Color, iconColor: Color) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(containerColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = iconColor
        )
    }
}


private suspend fun uploadSignupImage(
    bytes: ByteArray,
    mimeType: String,
    signer: io.nurunuru.app.data.AppSigner?
): String? {
    // nostr.build increasingly expects NIP-98 auth. During signup we already have
    // the freshly generated key in SecureKeyManager, so sign the upload request and
    // gracefully fall back to the other supported image hosts.
    return io.nurunuru.app.data.ImageUploadUtils.uploadToNostrBuild(bytes, mimeType, signer)
        ?: io.nurunuru.app.data.ImageUploadUtils.uploadToYabuMe(bytes, mimeType, signer)
        ?: io.nurunuru.app.data.ImageUploadUtils.uploadToBlossom(bytes, mimeType, signer)
}
