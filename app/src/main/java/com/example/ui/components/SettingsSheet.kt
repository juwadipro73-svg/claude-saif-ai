package com.example.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.PhotoCamera
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material.icons.outlined.ChevronRight
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.data.local.ThemeManager
import com.example.data.local.ThemeMode
import com.example.gemini.ui.settings.ENGINE_SETTINGS_SECTIONS
import com.example.gemini.ui.settings.SettingsSection
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PrimaryAccent
import com.example.ui.theme.SaifTheme
import com.example.ui.theme.StatusError
import com.example.ui.theme.SubtextMuted

data class VoiceProfile(
    val id: String, 
    val name: String, 
    val description: String, 
    val emotion: String = "Warm & Natural",
    val gender: String, 
    val pitch: Float, 
    val speechRate: Float, 
    val previewHinglish: String = "Bilkul! Mai SAIF AI hoon, aapse smoothly aur natural emotion ke saath baat karne ke liye taiyaar.",
    val previewEnglish: String = "Hello, I am SAIF AI, speaking with natural clarity and emotion.",
    val targetEngineNameMatches: List<String> = emptyList()
)

val VOICE_PROFILES = listOf(
    // Round 9: the voice of SAIF AI AUTOMATION, brought into the main app as the DEFAULT voice everywhere
    // (chat replies, live baatcheet and video narration all read the same setting - see utils/SaifVoice.kt).
    VoiceProfile(
        id = "SaifAdvance",
        name = "SAIF ADVANCE VOICE",
        description = "SAIF AI ki apni advance awaaz — warm, smart aur natural (Hindi & English). Live baatcheet, chat aur video narration sab isi me.",
        emotion = "\u2728 Warm & Intelligent",
        gender = "Male",
        pitch = 1.00f,
        speechRate = 1.00f,
        previewHinglish = "Namaste! Mai SAIF Advance Voice hoon. Aapke saath naturally baat karunga, screen bhi dekh sakta hoon, aur video bhi bana sakta hoon.",
        previewEnglish = "Hello, I am the SAIF Advance Voice. Natural, warm and ready to talk, watch your screen or build your video.",
        // Best natural Indian voices first; the live engine asks a Gemini-Live provider for "Puck" instead.
        targetEngineNameMatches = listOf(
            "en-in-x-end-network", "en-in-x-ene-local", "hi-in-x-hia-network", "hi-in-x-hia-local",
            "en-in-x-cxx-network", "en_in", "hi_in"
        )
    ),
    VoiceProfile(
        id = "Kabir",
        name = "Kabir",
        description = "Deep, confident, podcast baritone (Hindi & English)",
        emotion = "Deep & Calm",
        gender = "Male",
        pitch = 0.90f,
        speechRate = 0.95f,
        previewHinglish = "Namaste! Mai Kabir hoon. Har baat ko deeply, smoothly aur bina kisi rukawat ke explain karna mera style hai.",
        previewEnglish = "Greetings. I am Kabir. Here to provide thoughtful, authoritative guidance.",
        targetEngineNameMatches = listOf("en-in-x-end-network", "en-in-x-ene-local", "hi-in-x-hia-network", "hi-in-x-hia-local", "en_in", "hi_in")
    ),
    VoiceProfile(
        id = "Priya",
        name = "Priya",
        description = "Cheerful, sweet, bubbly & joyful (Hindi & English)",
        emotion = "Cheerful & Sweet",
        gender = "Female",
        pitch = 1.12f,
        speechRate = 0.98f,
        previewHinglish = "Hi there! Mai Priya hoon! Aaiye milkar kuch naya, super interesting aur mazedaar banate hain!",
        previewEnglish = "Hi there! I am Priya, ready to bring creativity and joy to every task!",
        targetEngineNameMatches = listOf("en-in-x-cxx-network", "en-in-x-cxx-local", "hi-in-x-hic-local", "hi-in-x-hid-network", "en_in", "hi_in")
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    temperature: Float,
    customSystemPrompt: String,
    onSaveSettings: (Float, String) -> Unit,
    onClearCurrentChat: () -> Unit,
    onDismiss: () -> Unit,
    onOpenAdminDashboard: () -> Unit = {},
    /** Engine categories open as details and return to this original SAIF AI Settings panel. */
    onOpenEngineSettings: (String) -> Unit,
    autoApprove: Boolean,
    onAutoApproveChange: (Boolean) -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
    /** The engine's separate OAuth account (shown in the profile card). */
    engineAuthInfo: com.example.gemini.data.remote.AgyHubClient.AgyAuthInfo? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = androidx.compose.ui.platform.LocalContext.current
    val themeMode by ThemeManager.themeMode.collectAsState()
    val generalPrefs = remember { context.getSharedPreferences("SettingsPrefs", android.content.Context.MODE_PRIVATE) }
    var autoSpeak by remember { mutableStateOf(generalPrefs.getBoolean("autoSpeak", false)) }
    

    val authUser by com.example.data.local.AuthManager.currentUser.collectAsState()
    val isPinProtected by com.example.util.SmartConversationMemory.isPinProtected.collectAsState()
    var showCropDialog by remember { mutableStateOf(false) }
    var selectedCropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showMemoryVaultDialog by remember { mutableStateOf(false) }
    var showCustomApiExtDialog by remember { mutableStateOf(false) }
    var showAgentsManager by remember { mutableStateOf(false) }
    var showSkillsManager by remember { mutableStateOf(false) }
    var showTerminalProviders by remember { mutableStateOf(false) }
    var showSignOutConfirmDialog by remember { mutableStateOf(false) }
    var showLegalNotices by remember { mutableStateOf(false) }
    val displayName = authUser?.name?.ifBlank { authUser?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } } ?: "User"

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(stream)
                stream?.close()
                if (bmp != null) {
                    selectedCropBitmap = bmp
                    showCropDialog = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Could not load selected photo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showCropDialog && selectedCropBitmap != null) {
        ImageCropEditPanel(
            bitmap = selectedCropBitmap!!,
            onCropConfirmed = { cropped ->
                showCropDialog = false
                selectedCropBitmap = null
                val userEmail = authUser?.email ?: "user"
                com.example.data.local.AuthManager.saveUserAvatar(context, userEmail, cropped)
                Toast.makeText(context, "Profile photo updated successfully!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showCropDialog = false
                selectedCropBitmap = null
            }
        )
    }

    val glassBorderBrush = Brush.linearGradient(
        listOf(
            SaifTheme.colors.textPrimary.copy(alpha = 0.35f),
            Color.Transparent,
            SaifTheme.colors.textPrimary.copy(alpha = 0.1f)
        )
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SaifTheme.colors.primaryBackground,
        contentColor = SaifTheme.colors.textPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(SaifTheme.colors.cardBorder)
            )
        },
        modifier = Modifier.fillMaxSize() // Make it full screen
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Settings",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = SaifTheme.colors.textPrimary
                    )
                    Text(
                        text = "Manage your SAIF AI experience",
                        fontSize = 13.sp,
                        color = SubtextMuted
                    )
                }
                
                // Liquid glass close button
                LiquidButton(
                    onClick = onDismiss,
                    color = SaifTheme.colors.surfaceCard,
                    glassBorderBrush = glassBorderBrush,
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = SaifTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Profile Section
            val user = authUser
            val displayName = user?.name?.ifBlank { user.email.substringBefore("@").replaceFirstChar { it.uppercase() } } ?: "User"
            val engineEmail = engineAuthInfo?.takeIf { it.isLoggedIn && it.email.isNotBlank() }?.email
            val engineAvatarModel = remember(engineAuthInfo?.profilePictureUrl) {
                com.example.gemini.ui.drawer.parseProfileAvatarModel(engineAuthInfo?.profilePictureUrl)
            }
            val displayEmail = user?.email?.ifBlank { "guest@saif.ai" } ?: "guest@saif.ai"
            val initialLetter = user?.initialLetter ?: displayName.take(1).uppercase().ifBlank { "U" }
            val avatarPath = user?.profilePicturePath
            val avatarFile = avatarPath?.let { java.io.File(it) }
            val avatarBitmap = remember(avatarPath) {
                if (avatarFile != null && avatarFile.exists()) {
                    try {
                        BitmapFactory.decodeFile(avatarFile.absolutePath)
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }

            LiquidSurface(
                color = SaifTheme.colors.surfaceCard,
                glassBorderBrush = glassBorderBrush,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clickable {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF7C5CE0), Color(0xFF5E43BD), Color(0xFF4C1D95))
                                    )
                                )
                                .border(2.dp, Color(0xFF9B82EC).copy(alpha = 0.6f), CircleShape)
                        ) {
                            if (avatarBitmap != null) {
                                Image(
                                    bitmap = avatarBitmap.asImageBitmap(),
                                    contentDescription = "Profile Photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (engineEmail != null && engineAvatarModel != null) {
                                coil.compose.AsyncImage(
                                    model = engineAvatarModel,
                                    contentDescription = "Google profile photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = initialLetter,
                                    color = Color.White,
                                    fontSize = 30.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Camera Edit Badge Icon on bottom-end
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF5E43BD),
                            border = BorderStroke(1.5.dp, Color(0xFF1F1E1D)),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .size(26.dp)
                                .align(Alignment.BottomEnd)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Change Profile Photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Display Name with Edit Name Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = displayName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SaifTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            onClick = { showEditNameDialog = true },
                            shape = CircleShape,
                            color = SaifTheme.colors.primaryBackground.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, SaifTheme.colors.cardBorder)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Name",
                                tint = com.example.ui.theme.PrimaryAccent,
                                modifier = Modifier.padding(5.dp).size(14.dp)
                            )
                        }
                    }

                    Text(
                        text = displayEmail,
                        fontSize = 13.sp,
                        color = SaifTheme.colors.textSecondary
                    )
                    if (engineEmail != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(Color(0xFF22C55E), CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Antigravity signed in: $engineEmail",
                                fontSize = 11.sp,
                                color = SaifTheme.colors.textSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (user?.isAdmin == true) {
                            Surface(
                                color = Color(0xFF2E2046),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFF6B31CF).copy(alpha = 0.7f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Icon(imageVector = Icons.Outlined.AdminPanelSettings, contentDescription = null, tint = Color(0xFFD4C9F8), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Admin",
                                        color = Color(0xFFD4C9F8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Surface(
                                color = Color(0xFF4C1D95).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "Free User",
                                    color = Color(0xFF9B82EC),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // Sign Out button in Profile Card
                        Surface(
                            onClick = { showSignOutConfirmDialog = true },
                            shape = RoundedCornerShape(16.dp),
                            color = StatusError.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, StatusError.copy(alpha = 0.35f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                                    contentDescription = "Sign Out",
                                    tint = StatusError,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Sign Out",
                                    color = StatusError,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // SAIF AI's original Appearance control comes first.
            SectionHeader("APPEARANCE")
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().height(64.dp)
                    .background(SaifTheme.colors.surfaceCard, RoundedCornerShape(16.dp))
                    .padding(4.dp)
            ) {
                val segmentWidth = maxWidth / 3
                val segmentWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { segmentWidth.toPx() }
                val targetOffsetX = when (themeMode) {
                    ThemeMode.LIGHT -> 0
                    ThemeMode.DARK -> segmentWidthPx.roundToInt()
                    ThemeMode.SYSTEM -> (segmentWidthPx * 2).roundToInt()
                }
                val offset by animateIntOffsetAsState(
                    targetValue = IntOffset(targetOffsetX, 0),
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                    label = "AppearanceSegmentOffset"
                )
                Box(
                    modifier = Modifier.width(segmentWidth).fillMaxHeight()
                        .offset { offset }
                        .background(PrimaryAccent, RoundedCornerShape(12.dp))
                )
                Row(Modifier.fillMaxSize()) {
                    listOf(
                        Triple("Light", Icons.Default.LightMode, ThemeMode.LIGHT),
                        Triple("Dark", Icons.Default.DarkMode, ThemeMode.DARK),
                        Triple("System", Icons.Default.Computer, ThemeMode.SYSTEM)
                    ).forEach { (title, icon, mode) ->
                        val selected = themeMode == mode
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { ThemeManager.setTheme(mode) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(icon, contentDescription = title,
                                    tint = if (selected) Color.White else SaifTheme.colors.textSecondary,
                                    modifier = Modifier.size(20.dp))
                                Spacer(Modifier.height(2.dp))
                                Text(title, color = if (selected) Color.White else SaifTheme.colors.textSecondary,
                                    fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            SectionHeader("AI ACCOUNT CONNECTION")
            LiquidSurface(
                color = SaifTheme.colors.surfaceCard,
                glassBorderBrush = glassBorderBrush,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsActionRow("Antigravity Connection", "Manage sign-in and workspace server status", onClick = { onOpenEngineSettings("CONNECTION") })
                    HorizontalDivider(color = SaifTheme.colors.cardBorder)
                    com.example.agyext.AntigravityCustomApiCompactRow(onOpenDialog = { showCustomApiExtDialog = true })
                }
            }
            Spacer(Modifier.height(24.dp))

            SectionHeader("VOICE RESPONSES")
            LiquidSurface(
                color = SaifTheme.colors.surfaceCard,
                glassBorderBrush = glassBorderBrush,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null,
                        tint = SaifTheme.colors.textSecondary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Voice Responses", color = SaifTheme.colors.textPrimary,
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Read SAIF AI replies aloud", color = SaifTheme.colors.textSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = autoSpeak,
                        onCheckedChange = {
                            autoSpeak = it
                            generalPrefs.edit().putBoolean("autoSpeak", it).apply()
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // AI Memory
            SectionHeader("AI MEMORY")
            LiquidSurface(
                color = SaifTheme.colors.surfaceCard,
                glassBorderBrush = glassBorderBrush,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                onClick = { showMemoryVaultDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Psychology,
                        contentDescription = null,
                        tint = com.example.ui.theme.PrimaryAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SAIF AI Memories",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SaifTheme.colors.textPrimary
                            )
                            if (isPinProtected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "PIN Protected",
                                    tint = com.example.ui.theme.PrimaryAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                        Text(
                            text = if (isPinProtected) "Private & PIN Protected" else "Things AI learned about you",
                            fontSize = 13.sp,
                            color = SaifTheme.colors.textSecondary
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = SaifTheme.colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Spacer(Modifier.height(24.dp))

            SectionHeader("AI AGENTS & SKILLS")
            LiquidSurface(
                color = SaifTheme.colors.surfaceCard, glassBorderBrush = glassBorderBrush,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsActionRow("Specialist AI Agents", "Coding, research, automation, analysis & content", onClick = { showAgentsManager = true })
                    HorizontalDivider(color = SaifTheme.colors.cardBorder)
                    SettingsActionRow("AI Skills (/commands)", "Import and manage saved skills", onClick = { showSkillsManager = true })
                    HorizontalDivider(color = SaifTheme.colors.cardBorder)
                    SettingsActionRow("Custom Slash Commands", "Create and edit your own /commands", onClick = { onOpenEngineSettings("SLASH_COMMANDS") })
                    HorizontalDivider(color = SaifTheme.colors.cardBorder)
                    SettingsActionRow("Custom SAIF AI Plugins", "Install and manage extensions", onClick = { onOpenEngineSettings("WEBVIEW_EXTENSIONS") })
                    HorizontalDivider(color = SaifTheme.colors.cardBorder)
                    SettingsActionRow("MCP Servers", "Configure connected tools and integrations", onClick = { onOpenEngineSettings("MCP") })
                    HorizontalDivider(color = SaifTheme.colors.cardBorder)
                    SettingsActionRow("GitHub Sync", "Connect repositories and sync skills", onClick = { onOpenEngineSettings("GITHUB") })
                    HorizontalDivider(color = SaifTheme.colors.cardBorder)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto-approve commands", color = SaifTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(if (autoApprove) "Commands may run without confirmation" else "Ask before running commands", color = SaifTheme.colors.textSecondary, fontSize = 12.sp)
                        }
                        Switch(checked = autoApprove, onCheckedChange = onAutoApproveChange)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader("TERMINAL & AI PROVIDERS")
            LiquidSurface(
                color = SaifTheme.colors.surfaceCard, glassBorderBrush = glassBorderBrush,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsActionRow("Manage Terminal Providers", "Antigravity, OpenCode, Claude Code, local Ubuntu & custom providers", onClick = { showTerminalProviders = true })
                    HorizontalDivider(color = SaifTheme.colors.cardBorder)
                    com.example.agyext.AntigravityCustomApiCompactRow(onOpenDialog = { showCustomApiExtDialog = true })
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionHeader("WORKSPACE ENGINE SETTINGS")
            LiquidSurface(
                color = SaifTheme.colors.surfaceCard, glassBorderBrush = glassBorderBrush,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    ENGINE_SETTINGS_SECTIONS.filterNot { section ->
                        section in setOf(
                            SettingsSection.WEBVIEW_EXTENSIONS,
                            SettingsSection.SLASH_COMMANDS,
                            SettingsSection.AI_SKILLS,
                            SettingsSection.MCP
                        )
                    }.forEachIndexed { index, section ->
                        if (index > 0) HorizontalDivider(color = SaifTheme.colors.cardBorder)
                        SettingsActionRow(section.title, section.subtitle) {
                            onOpenEngineSettings(section.name)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            // Work in the Linux workspace downloads gigabytes of modules. They are removed again once a project
            // has been idle; the project itself is never removed.
            SectionHeader("STORAGE")
            val cleanupStatus by com.example.storage.ModuleAutoCleaner.status.collectAsState()
            LaunchedEffect(Unit) { com.example.storage.ModuleAutoCleaner.load(context) }
            LiquidSurface(
                color = SaifTheme.colors.surfaceCard, glassBorderBrush = glassBorderBrush,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsToggleRow("Auto-clean heavy modules", cleanupStatus.enabled) { value ->
                        com.example.storage.ModuleAutoCleaner.setEnabled(context, value)
                    }
                    Text(
                        "After a project is finished and idle, downloaded modules (node_modules, Python environments, " +
                            "Gradle, npm, pip and apt caches) are deleted to free storage. Your project files are never " +
                            "deleted. When you return to a project, its modules are downloaded again.",
                        color = SaifTheme.colors.textSecondary, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                    )
                    HorizontalDivider(color = SaifTheme.colors.cardBorder)
                    SettingsChoiceRow(
                        "Clean when a project is idle for",
                        com.example.storage.ModuleCleanupRules.idleLabel(cleanupStatus.idleMinutes),
                        com.example.storage.ModuleCleanupRules.IDLE_CHOICES_MINUTES.map { com.example.storage.ModuleCleanupRules.idleLabel(it) }
                    ) { label ->
                        com.example.storage.ModuleAutoCleaner.setIdleMinutes(
                            context, com.example.storage.ModuleCleanupRules.idleMinutesFromLabel(label)
                        )
                    }
                    HorizontalDivider(color = SaifTheme.colors.cardBorder)
                    SettingsActionRow(
                        if (cleanupStatus.running) "Cleaning…" else "Clean heavy modules now",
                        com.example.storage.ModuleAutoCleaner.describe(cleanupStatus),
                        onClick = { com.example.storage.ModuleAutoCleaner.startManualClean(context) }
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Permission Manager Section
            PermissionManagerSection(glassBorderBrush = glassBorderBrush)
            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader("ABOUT & LICENCES")
            LiquidSurface(
                color = SaifTheme.colors.surfaceCard, glassBorderBrush = glassBorderBrush,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    SettingsActionRow(
                        "Open-source licences & credits",
                        "Everyone whose code SAIF AI uses, and the licences",
                        onClick = { showLegalNotices = true }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Footer: version, and the copyright / no-warranty / licence notice the GPL asks for.
            Text(
                text = "SAIF AI ${com.example.gemini.BuildConfig.VERSION_NAME}\n${com.example.gemini.BuildConfig.BUILD_STAMP}\n\n" +
                    SaifLegalNotices.SHORT_NOTICE,
                fontSize = 11.sp,
                color = SubtextMuted,
                modifier = Modifier.fillMaxWidth().clickable { showLegalNotices = true },
                textAlign = TextAlign.Center
            )
        }
    }

    if (showEditNameDialog) {
        EditNameDialog(
            currentName = displayName,
            onDismiss = { showEditNameDialog = false },
            onNameUpdated = { }
        )
    }

    if (showMemoryVaultDialog) {
        SaifAiMemoryDialog(
            onDismiss = { showMemoryVaultDialog = false }
        )
    }

    if (showAgentsManager) {
        com.example.agents.SaifAgentsManagerDialog(onDismiss = { showAgentsManager = false })
    }
    if (showSkillsManager) {
        SaifSkillsManagerDialog(isLightMode = SaifTheme.colors.isLight, onDismiss = { showSkillsManager = false })
    }
    if (showTerminalProviders) {
        com.example.providers.TerminalProvidersSettingsDialog(onDismiss = { showTerminalProviders = false })
    }

    if (showCustomApiExtDialog) {
        com.example.agyext.AntigravityCustomApiDialog(
            onDismiss = { showCustomApiExtDialog = false }
        )
    }

    if (showLegalNotices) {
        SaifLegalNoticesDialog(onDismiss = { showLegalNotices = false })
    }

    if (showSignOutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmDialog = false },
            title = {
                Text("Sign Out", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Are you sure you want to sign out of SAIF AI?", fontSize = 14.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirmDialog = false
                        onDismiss()
                        com.example.data.local.AuthManager.signOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) {
                    Text("Sign Out", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = SaifTheme.colors.surfaceCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}


@Composable
private fun SettingsActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = SaifTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = SaifTheme.colors.textSecondary, fontSize = 12.sp)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = SaifTheme.colors.textSecondary)
    }
}

@Composable
private fun SettingsChoiceRow(title: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { expanded = true }.padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = SaifTheme.colors.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Box {
            Text(selected, color = PrimaryAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = {
                    expanded = false
                    onSelect(option)
                }) }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = SaifTheme.colors.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier.padding(bottom = 8.dp)) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = SubtextMuted,
        letterSpacing = 1.sp,
        modifier = modifier
    )
}

@Composable
fun AppearanceOption(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glassBorderBrush: Brush
) {
    val backgroundColor = if (isSelected) com.example.ui.theme.PrimaryAccent else SaifTheme.colors.surfaceCard
    val contentColor = if (isSelected) Color.White else SaifTheme.colors.textPrimary
    
    LiquidSurface(
        onClick = onClick,
        color = backgroundColor,
        glassBorderBrush = if (isSelected) null else glassBorderBrush,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                color = contentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun LiquidSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color,
    glassBorderBrush: Brush?,
    shape: androidx.compose.ui.graphics.Shape,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "LiquidScale"
    )

    Surface(
        shape = shape,
        color = color,
        border = if (glassBorderBrush != null) androidx.compose.foundation.BorderStroke(1.5.dp, glassBorderBrush) else null,
        modifier = modifier
            .scale(scale)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource, 
                        indication = androidx.compose.foundation.LocalIndication.current,
                        onClick = onClick
                    )
                } else Modifier
            )
    ) {
        Box(modifier = Modifier.animateContentSize()) {
            content()
        }
    }
}

@Composable
fun LiquidButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    color: Color,
    glassBorderBrush: Brush?,
    shape: androidx.compose.ui.graphics.Shape,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "ButtonScale"
    )

    Surface(
        onClick = onClick,
        shape = shape,
        color = color,
        border = if (glassBorderBrush != null) androidx.compose.foundation.BorderStroke(1.5.dp, glassBorderBrush) else null,
        interactionSource = interactionSource,
        modifier = modifier.scale(scale)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
