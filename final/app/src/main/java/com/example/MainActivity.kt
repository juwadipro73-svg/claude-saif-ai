/* Copyright (c) SAIF AI. All rights reserved. */



package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.data.local.AuthManager
import com.example.data.local.ChatRepository
import com.example.data.local.SaifDatabase
import com.example.data.local.ThemeManager
import com.example.data.local.ThemeMode
import com.example.ui.ChatViewModel
import com.example.ui.SaifAiApp
import com.example.ui.components.AuthScreen
import com.example.ui.theme.SaifAiTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels {
        val db = SaifDatabase.getDatabase(applicationContext)
        val repository = ChatRepository(db.chatDao())
        ChatViewModel.Factory(repository, application)
    }

    override fun onResume() {
        super.onResume()
        LiveVoiceManager.isAppInForeground.value = true
        com.example.util.WakeWordManager.isAppInForeground = true
        // The old sign-in screen is never blocked by Linux setup. Reuse the same
        // archive and installer in the background; avoid a second download on updates.
        lifecycleScope.launch {
            try {
                com.example.saif.data.local.LocalEnvironmentManager.ensureInstalledInBackground(applicationContext)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("MainActivity", "Could not schedule background Linux setup", error)
            }
            com.example.saif.data.local.AuthenticatedWorkspace.ensureStarted(applicationContext)
            val activeUid = AuthManager.currentUser.value?.uid
            com.example.providers.TerminalProviderManager.bindUserAccount(applicationContext, activeUid)
            com.example.providers.TerminalProviderManager.resumePendingSetupIfNeeded(applicationContext)
            if (com.example.saif.data.local.LocalEnvironmentManager.hasVerifiedEnvironment(applicationContext) &&
                AuthManager.currentUser.value?.isActive == true && AuthManager.currentUser.value?.isBanned == false) {
                com.saifai.engine.SaifEngineBootstrap.initialize(application)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        LiveVoiceManager.isAppInForeground.value = false
        com.example.util.WakeWordManager.isAppInForeground = false
        com.example.util.WakeWordAudioDetector.stop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthManager.init(applicationContext)
        com.example.saif.data.remote.AntigravityAppBinding.init(applicationContext)
        if (com.saifai.app.BuildConfig.SAIF_CLOUD_API_BASE_URL.isNotBlank()) {
            runCatching {
                com.google.firebase.appcheck.FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory.getInstance()
                )
            }
        }
        com.example.data.local.AdminConfigManager.init(applicationContext)
        com.example.data.local.ProviderSettingsManager.init(applicationContext)
        ThemeManager.init(applicationContext)
        com.example.utils.TTSManager.init(applicationContext)
        com.example.util.WakeWordManager.init(applicationContext)
        com.example.util.SmartConversationMemory.init(applicationContext)
        com.example.util.SaifBroadcastNotificationHelper.initNotificationChannel(applicationContext)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            // Request runtime notification permission on Android 13+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { granted ->
                    android.util.Log.d("MainActivity", "Notification permission: $granted")
                }
                LaunchedEffect(key1 = Unit) {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            android.Manifest.permission.POST_NOTIFICATIONS
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            val themeMode by ThemeManager.themeMode.collectAsState()
            val isSystemDark = isSystemInDarkTheme()
            val isLightMode = when (themeMode) {
                ThemeMode.LIGHT -> true
                ThemeMode.DARK -> false
                ThemeMode.SYSTEM -> !isSystemDark
            }
            
            val currentUser by AuthManager.currentUser.collectAsState()
            // Sign-in and lightweight chat are available while the same Linux archive
            // downloads in the background. Only the local server waits for verification.
            LaunchedEffect(currentUser?.uid, currentUser?.isActive, currentUser?.isBanned) {
                val user = currentUser
                com.example.providers.TerminalProviderManager.bindUserAccount(applicationContext, user?.uid)
                com.example.agents.SaifAgentRegistry.ensureLoaded(applicationContext, user?.uid)
                com.example.providers.TerminalProviderManager.resumePendingSetupIfNeeded(applicationContext)
                if (user != null && user.isActive && !user.isBanned) {
                    com.example.saif.data.remote.SavedAiSkills.bind(applicationContext, user.uid)
                    com.example.saif.data.local.AuthenticatedWorkspace.ensureStarted(applicationContext)
                    com.example.cloud.SaifAiCloudManager.restoreLastJob(applicationContext)
                } else if (user == null || !user.isActive || user.isBanned) {
                    com.example.saif.data.remote.SavedAiSkills.bind(applicationContext, null)
                    com.example.cloud.SaifAiCloudManager.onSignedOut()
                    com.example.saif.data.local.AuthenticatedWorkspace.onSignedOut()
                }
            }
            val globalConfig by com.example.data.local.AdminConfigManager.globalConfig.collectAsState()
            var showWelcomeToast by remember { mutableStateOf(false) }
            var showAdminDashboard by remember(currentUser?.email, currentUser?.role) { mutableStateOf(currentUser?.isAdmin == true) }
            var isAnnouncementDismissed by remember { mutableStateOf(false) }

            // Start Firestore sync for all logged in users so they receive notifications and fleet config
            LaunchedEffect(currentUser?.uid) {
                if (currentUser?.isActive == true && currentUser?.isBanned == false) {
                    com.example.data.local.AdminConfigManager.startFirestoreSync()
                }
            }

            // Automatic Direct Admin Login: If user is admin in Firebase, immediately open Admin Control Panel
            LaunchedEffect(currentUser?.email, currentUser?.role) {
                if (currentUser?.isAdmin == true) {
                    showAdminDashboard = true
                }
            }

            // Reset announcement dismissal whenever a new broadcast is dispatched
            LaunchedEffect(globalConfig.lastBroadcastTime) {
                if (globalConfig.lastBroadcastTime > 0L) {
                    isAnnouncementDismissed = false
                }
            }

            var providerSetupComplete by remember(currentUser?.uid) {
                mutableStateOf(currentUser?.let { user ->
                    !user.isActive || user.isBanned ||
                        com.example.providers.TerminalProviderManager.hasSelectedProviderForUser(applicationContext, user.uid)
                } ?: true)
            }

            var showStartupSplash by androidx.compose.runtime.saveable.rememberSaveable {
                mutableStateOf(!android.os.Build.FINGERPRINT.equals("robolectric", ignoreCase = true))
            }
            LaunchedEffect(showStartupSplash) {
                if (showStartupSplash) {
                    kotlinx.coroutines.delay(1850)
                    showStartupSplash = false
                }
            }

            SaifAiTheme(isLightMode = isLightMode) {
                Box(modifier =
    Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = currentUser != null,
                        transitionSpec = {
                            (fadeIn(tween(400)) + scaleIn(initialScale = 0.98f, animationSpec = tween(400)))
                                .togetherWith(fadeOut(tween(300)) + scaleOut(targetScale = 1.03f, animationSpec = tween(300)))
                        },
                        label = "auth_screen_transition"
                    ) { isLoggedIn ->
                        if (!isLoggedIn) {
                            AuthScreen(
                                onLoginSuccess = { user ->
                                    if (user.isAdmin) {
                                        showAdminDashboard = true
                                    } else {
                                        showWelcomeToast = true
                                    }
                                },
                                modifier =
    Modifier.fillMaxSize()
                            )
                        } else {
                            val user = currentUser
                            if (user != null && (!user.isActive || user.isBanned)) {
                                // Account Suspended / Banned Screen
                                com.example.ui.components.BannedUserOverlay(
                                    onSignOut = { AuthManager.signOut() }
                                )
                            } else if (!providerSetupComplete) {
                                com.example.providers.TerminalProviderSelectionScreen(
                                    userDisplayName = user?.name,
                                    onProviderConfirmed = { selected ->
                                        com.example.providers.TerminalProviderManager.selectAndPrepareProvider(
                                            applicationContext, selected.id
                                        )
                                        providerSetupComplete = true
                                    }
                                )
                            } else {
                                SaifAiApp(
                                    viewModel = viewModel,
                                    isLightMode = isLightMode,
                                    onToggleTheme = { 
                                        val newMode = if (isLightMode) ThemeMode.DARK else ThemeMode.LIGHT
                                        ThemeManager.setTheme(newMode)
                                    },
                                    onOpenAdminDashboard = { showAdminDashboard = true },
                                    modifier =
    Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // Global Admin Broadcast Banner (When active and not dismissed)
                    val activeAnnouncement = globalConfig.globalAnnouncement.trim()
                    if (currentUser != null && globalConfig.isAnnouncementActive && activeAnnouncement.isNotBlank() && !isAnnouncementDismissed && !showAdminDashboard) {
                        Surface(
                            color = Color(0xF02E2046),
                            shape =
    RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF7C5CE0).copy(alpha = 0.7f)),
                            shadowElevation = 6.dp,
                            modifier =
    Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 8.dp, start = 12.dp, end = 12.dp)
                                .fillMaxWidth()
                        ) {
                            Row(
                                modifier =
    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment =
    Alignment.CenterVertically
                            ) {
                                Text(text = "📢", fontSize =
    16.sp)
                                Spacer(modifier =
    Modifier.width(8.dp))
                                Text(
                                    text = activeAnnouncement,
                                    color = Color(0xFFEDE9FE),
                                    fontSize =
    12.sp,
                                    lineHeight = 16.sp,
                                    modifier =
    Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { isAnnouncementDismissed = true },
                                    modifier =
    Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = Color(0xFFD3C2F0),
                                        modifier =
    Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Floating Welcome Toast Banner
                    AnimatedVisibility(
                        visible = showWelcomeToast && currentUser != null && !showAdminDashboard,
                        enter = slideInVertically(
                            initialOffsetY = { -it },
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                        ) + fadeIn(),
                        exit = slideOutVertically(
                            targetOffsetY = { -it },
                            animationSpec = tween(280)
                        ) + fadeOut(),
                        modifier =
    Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 16.dp)
                    ) {
                        LaunchedEffect(key1 = Unit) {
                            delay(3500L)
                            showWelcomeToast = false
                        }
                        val isAdminUser = currentUser?.isAdmin == true
                        Surface(
                            color = if (isAdminUser) Color(0xF02E2046) else Color(0xEB2B2A27),
                            shape =
    RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, if (isAdminUser) Color(0xFF6B31CF) else Color(0x667C5CE0)),
                            shadowElevation = 8.dp,
                            modifier =
    Modifier.clickable { showWelcomeToast = false }
                        ) {
                            Row(
                                modifier =
    Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                verticalAlignment =
    Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isAdminUser) "👑 Logged in as Admin (${currentUser?.name})" else "👋 Welcome back, ${currentUser?.name}!",
                                    color = if (isAdminUser) Color(0xFFD4C9F8) else Color.White,
                                    fontSize =
    13.sp,
                                    fontWeight =
    FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Full-screen Admin Dashboard Screen
                    if (showAdminDashboard && currentUser?.isAdmin == true) {
                        com.example.ui.components.AdminDashboardScreen(
                            onClose = { showAdminDashboard = false },
                            modifier =
    Modifier.fillMaxSize()
                        )
                    }

                    // Smooth Animated Startup Splash Overlay
                    com.example.ui.components.SaifAnimatedSplashOverlay(
                        visible = showStartupSplash
                    )
                }
            }
        }
    }
}
