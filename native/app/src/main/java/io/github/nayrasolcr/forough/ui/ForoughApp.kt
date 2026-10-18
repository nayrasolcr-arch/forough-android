package io.github.nayrasolcr.forough.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import io.github.nayrasolcr.forough.BuildConfig
import io.github.nayrasolcr.forough.CrashLog
import io.github.nayrasolcr.forough.EngineBridge
import io.github.nayrasolcr.forough.ForoughBus
import io.github.nayrasolcr.forough.R
import io.github.nayrasolcr.forough.tunnel.SettingsStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private enum class Screen(val depth: Int) {
    MAIN(0), SCANNER(1), SETTINGS(1), LOGS(1), ABOUT(1), APPS(2)
}

/**
 * ForoughApp — the Android shell of the desktop window: header bar with
 * the same tool buttons (scanner, speed test, diagnostics, settings, logs,
 * about) over the connection panel + stats panel.
 *
 * Motion: screens cross-fade with a directional slide + subtle zoom
 * (forward = slide left, back = slide right), everything spring-driven.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForoughApp(
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onLanguageChanged: () -> Unit,
    onBatteryHelper: () -> Unit
) {
    var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val store = remember { SettingsStore(context.applicationContext) }

    // System back gesture/button returns from any sub-screen to the main
    // panel (and exits only from MAIN).
    BackHandler(enabled = screen != Screen.MAIN) {
        screen = Screen.MAIN
    }

    val stats by ForoughBus.stats.collectAsState()
    val running by ForoughBus.running.collectAsState()

    // Bind the global haptics controller to this window and keep it in sync
    // with the user's haptic-feedback setting (power features group).
    val view = LocalView.current
    DisposableEffect(view, store.hapticsEnabled) {
        Haptics.bind(view)
        Haptics.setEnabled(store.hapticsEnabled)
        onDispose { }
    }

    // Toasts from the engine — the desktop Adw.Toast parity. Also: when the
    // tunnel first reaches "connected", greet the user (visible confirmation
    // that the session is live even if they're on another screen).
    var wasConnected by remember { mutableStateOf(false) }
    val connectedToastText = stringResource(R.string.connected_toast)
    val disconnectedToastText = stringResource(R.string.disconnected_toast)
    LaunchedEffect(Unit) {
        ForoughBus.toast.collectLatest { message ->
            if (message != null) {
                snackbar.showSnackbar(message)
                ForoughBus.consumeToast()
            }
        }
    }
    LaunchedEffect(running) {
        if (!running && wasConnected) {
            wasConnected = false
            snackbar.showSnackbar(disconnectedToastText)
        }
    }
    LaunchedEffect(stats.status) {
        if (stats.status == "connected" && running && !wasConnected) {
            wasConnected = true
            Haptics.confirm()
            snackbar.showSnackbar(connectedToastText)
        }
    }

    val diagCopiedText = stringResource(R.string.diagnostics_copied)
    val speedStartedText = stringResource(R.string.speed_test_running)
    val speedUnavailableText = stringResource(R.string.speed_test_unavailable)
    val backText = stringResource(R.string.back)

    // Runs a speed test from any entry point; the engine reports the result
    // as a toast when the numbers are in.
    val runSpeedTest: () -> Unit = {
        try {
            val manager = EngineBridge.manager
            if (manager != null && manager.isRunning) {
                manager.runSpeedTest(manual = true)
                scope.launch { snackbar.showSnackbar(speedStartedText) }
            } else {
                scope.launch { snackbar.showSnackbar(speedUnavailableText) }
            }
        } catch (e: Exception) {
            ForoughBus.appendLog("speed test error: ${e.message}")
        }
    }

    // Copies the diagnostics report to the clipboard from any entry point.
    val copyDiagnostics: () -> Unit = {
        try {
            val report = EngineBridge.manager?.buildDiagnosticsReport("manual-copy")
                ?: buildString {
                    appendLine("Forough Android ${BuildConfig.VERSION_NAME} — tunnel not running")
                    CrashLog.read(context)?.let { crash ->
                        appendLine()
                        appendLine("last crash:")
                        append(crash)
                    }
                }
            clipboard.setText(AnnotatedString(report))
            scope.launch { snackbar.showSnackbar(diagCopiedText) }
        } catch (e: Exception) {
            ForoughBus.appendLog("diagnostics error: ${e.message}")
        }
    }

    val screenTitle = when (screen) {
        Screen.MAIN -> stringResource(R.string.app_name)
        Screen.SCANNER -> stringResource(R.string.scanner_title)
        Screen.SETTINGS -> stringResource(R.string.settings)
        Screen.LOGS -> stringResource(R.string.logs_title)
        Screen.ABOUT -> stringResource(R.string.about)
        Screen.APPS -> stringResource(R.string.apps_title)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = screenTitle,
                        transitionSpec = {
                            (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 6 })
                                .togetherWith(fadeOut(tween(120)))
                        },
                        label = "title"
                    ) { title -> Text(title) }
                },
                navigationIcon = {
                    if (screen == Screen.MAIN) {
                        Image(
                            painter = painterResource(R.drawable.forough_logo_circle),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        IconButton(onClick = {
                            Haptics.tap()
                            screen = Screen.MAIN
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = backText
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        Haptics.tap()
                        screen = Screen.SCANNER
                    }) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.scanner_title))
                    }
                    IconButton(onClick = {
                        Haptics.tap()
                        runSpeedTest()
                    }) {
                        Icon(Icons.Outlined.Speed, contentDescription = stringResource(R.string.speed_test))
                    }
                    IconButton(onClick = {
                        Haptics.tap()
                        copyDiagnostics()
                    }) {
                        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.diagnostics))
                    }
                    IconButton(onClick = {
                        Haptics.tap()
                        screen = Screen.SETTINGS
                    }) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings))
                    }
                    Box {
                        IconButton(onClick = {
                            Haptics.tap()
                            menuOpen = true
                        }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.menu_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.logs_title)) },
                                leadingIcon = { Icon(Icons.Outlined.Article, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    screen = Screen.LOGS
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.about)) },
                                onClick = {
                                    menuOpen = false
                                    screen = Screen.ABOUT
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        // The shell is a NON-scrolling column: each screen owns its own scroll
        // container (lazy lists inside an unbounded shell crash on measure).
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val forward = targetState.depth >= initialState.depth
                val sign = if (forward) 1 else -1
                val fade = tween<Float>(240)
                val slide = tween<IntOffset>(300)
                (fadeIn(fade) + slideInHorizontally(slide) { sign * it / 3 })
                    .togetherWith(fadeOut(fade) + slideOutHorizontally(slide) { -sign * it / 3 })
            },
            label = "screen",
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) { current ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                when (current) {
                    Screen.MAIN -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            StaggeredItem(index = 0) {
                                ConnectionPanel(
                                    stats = stats,
                                    running = running,
                                    onConnect = onConnect,
                                    onDisconnect = onDisconnect,
                                    onSpeedTest = runSpeedTest,
                                    onDiagnostics = copyDiagnostics,
                                    onLogs = { screen = Screen.LOGS },
                                    onAbout = { screen = Screen.ABOUT }
                                )
                            }
                            StaggeredItem(index = 1) {
                                StatsPanel(stats = stats, running = running)
                            }
                        }
                    }
                    Screen.SCANNER -> ScannerScreen(
                        store = store,
                        onToast = { message -> ForoughBus.setToast(message) }
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        store = store,
                        onSave = { message -> ForoughBus.setToast(message) },
                        onLanguageChanged = onLanguageChanged,
                        onBatteryHelper = onBatteryHelper,
                        onOpenApps = { screen = Screen.APPS }
                    )
                    Screen.APPS -> AppsScreen(
                        store = store,
                        onToast = { message -> ForoughBus.setToast(message) },
                        onBack = { screen = Screen.SETTINGS }
                    )
                    Screen.LOGS -> LogsScreen()
                    Screen.ABOUT -> AboutScreen()
                }
            }
        }
    }
}
