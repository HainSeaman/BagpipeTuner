@file:OptIn(ExperimentalMaterial3Api::class)

package com.eh.bagpipetuner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AutomaticGainControl
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import androidx.core.net.toUri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.filters.HighPass
import be.tarsos.dsp.filters.LowPassSP
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.io.TarsosDSPAudioInputStream
import be.tarsos.dsp.pitch.PitchDetectionHandler
import be.tarsos.dsp.pitch.PitchProcessor
import com.eh.bagpipetuner.ui.theme.BagpipeTunerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log
import kotlin.math.roundToInt


// Enums
enum class TunerMode { CHANTER, DRONES }
enum class SoundType { NEWBAND, WARM, BRILLANT, OLDBAND, CUSTOM }

// Frequenzen Tabellen
val soundFrequencies: Map<SoundType, Map<String, Double>> = mapOf(
    SoundType.OLDBAND to mapOf(
        "Low G" to 405.0, "Low A" to 480.0, "B" to 542.0, "C#" to 571.0,
        "D" to 600.0, "E" to 675.0, "F#" to 718.0, "High G" to 806.0, "High A" to 960.0
    ),
    SoundType.WARM to mapOf(
        "Low G" to 403.0, "Low A" to 480.0, "B" to 538.0, "C#" to 569.0,
        "D" to 600.0, "E" to 673.0, "F#" to 716.0, "High G" to 805.0, "High A" to 960.0
    ),
    SoundType.BRILLANT to mapOf(
        "Low G" to 407.0, "Low A" to 480.0, "B" to 547.0, "C#" to 574.0,
        "D" to 600.0, "E" to 679.0, "F#" to 724.0, "High G" to 810.0, "High A" to 960.0
    ),
    SoundType.NEWBAND to mapOf(
        "Low G" to 420.0, "Low A" to 480.0, "B" to 539.0, "C#" to 600.0,
        "D" to 641.0, "E" to 718.0, "F#" to 799.0, "High G" to 840.0, "High A" to 960.0
    )
)

class MainActivity : AppCompatActivity() {
    @SuppressLint("AutoboxingStateCreation")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                111
            )
        }

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val savedTarget = prefs.getFloat("targetA", 472f).toDouble()
        val savedAnimSpeed = prefs.getInt("animationSpeedMs", 200)
        val savedSoundString = prefs.getString("soundType", SoundType.NEWBAND.name)
        val savedSound = SoundType.entries.find { it.name == savedSoundString } ?: SoundType.NEWBAND
        val savedDuration = prefs.getInt("recordingDuration", 3)
        val loadedCustomProfile = loadCustomSoundProfile(this)

        setContent {
            BagpipeTunerTheme {
                val navController = rememberNavController()
                @Suppress("UnrememberedMutableState")
                var target by rememberSaveable { mutableDoubleStateOf(savedTarget) }
                var animationSpeed by rememberSaveable { mutableIntStateOf(savedAnimSpeed) }
                var soundType by rememberSaveable { mutableStateOf(savedSound) }        // bleibt – kein primitiver Typ
                var recordingDuration by rememberSaveable { mutableStateOf(savedDuration) }
                var customProfileState by remember { mutableStateOf(loadedCustomProfile) }

                NavHost(navController, startDestination = "tuner") {
                    composable("tuner") {
                        MainScreenWithAppBar(
                            navController,
                            target,
                            animationSpeed,
                            soundType,
                            customProfile = customProfileState
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            navController = navController,
                            target = target,
                            animationSpeed = animationSpeed,
                            soundType = soundType,
                            recordingDuration = recordingDuration,
                            onTargetAChange = { newTarget ->
                                target = newTarget
                                prefs.edit { putFloat("targetA", newTarget.toFloat()) }
                            },
                            onAnimationSpeedChange = { newSpeed ->
                                animationSpeed = newSpeed
                                prefs.edit { putInt("animationSpeedMs", newSpeed) }
                            },
                            onSoundTypeChange = { newSound ->
                                soundType = newSound
                                prefs.edit { putString("soundType", newSound.name) }
                            },
                            onRecordingDurationChange = { newDuration ->
                                recordingDuration = newDuration
                                prefs.edit { putInt("recordingDuration", newDuration) }
                            },
                            customProfile = customProfileState,
                            onCustomProfileChange = { newProfile ->
                                customProfileState = newProfile
                                saveCustomSoundProfile(this@MainActivity, newProfile)
                            }
                        )
                    }

                    composable("info") {
                        InfoScreen(navController)
                    }

                    composable("language") {
                        LanguageScreen(navController)
                    }

                    composable("help") {
                        HelpScreen(navController)
                    }
                                    }
            }
        }
    }
}

@Composable
fun LanguageScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.language)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LanguageSwitcherRow()
        }
    }
}
@Composable
fun MainScreenWithAppBar(
    navController: NavHostController,
    target: Double,
    animationSpeed: Int,
    soundType: SoundType,
    customProfile: CustomSoundProfile?
) {
    Scaffold(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        topBar = {
            TopAppBar(
                title = { },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                    IconButton(onClick = { navController.navigate("info") }) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.information))
                    }
                    IconButton(onClick = { navController.navigate("language") }) {
                        Icon(Icons.Default.Public, contentDescription = stringResource(R.string.language))
                    }
                    IconButton(onClick = { navController.navigate("help") }) {
                        Icon(Icons.AutoMirrored.Filled.Help, contentDescription = stringResource(R.string.help))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            BagpipeTunerUI(
                startTargetA = target,
                animationSpeedMs = animationSpeed,
                context = LocalContext.current,
                soundType = soundType,
                customProfile = customProfile
            )
        }
    }
}

@Composable
fun LanguageSwitcherRow() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    // Hole die aktuell aktive App-Sprachliste (aus AppCompat)
    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val currentDisplay = getDisplayName(currentLocales)

    fun setAppLanguage(languageTag: String) {
        val locales = if (languageTag.isNotBlank()) {
            LocaleListCompat.forLanguageTags(languageTag)
        } else {
            LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(locales)
        activity?.recreate()
    }

    HorizontalDivider(Modifier.padding(vertical = 16.dp))
    Text(
        text = stringResource(R.string.language_title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary)
    HorizontalDivider(Modifier.padding(vertical = 8.dp))

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = { setAppLanguage("de") },
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .height(40.dp)
        ) { Text("Deutsch", fontSize = 16.sp) }

        Button(
            onClick = { setAppLanguage("en") },
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .height(40.dp)
        ) { Text("English", fontSize = 16.sp) }

        // Scots-Button
        Button(
            onClick = { setAppLanguage("sco") },
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .height(40.dp)
        ) { Text("Scots", fontSize = 16.sp) }

        Button(
            onClick = { setAppLanguage("") },
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .height(40.dp)
        ) { Text("System", fontSize = 16.sp) }
    }

    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.current_language, currentDisplay),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.bodyMedium
    )
}

fun getDisplayName(list: LocaleListCompat?): String {
    val locale = list?.get(0) ?: return "System"
    return when (locale.language.lowercase(Locale.ROOT)) {
        "de" -> "Deutsch"
        "en" -> "English"
        "sco" -> "Scots"
        else -> "System"
    }
}

@Composable
fun SettingsScreen(
    navController: NavHostController,
    target: Double,
    animationSpeed: Int,
    soundType: SoundType,
    recordingDuration: Int,
    onTargetAChange: (Double) -> Unit,
    onAnimationSpeedChange: (Int) -> Unit,
    onSoundTypeChange: (SoundType) -> Unit,
    onRecordingDurationChange: (Int) -> Unit,
    customProfile: CustomSoundProfile?,
    onCustomProfileChange: (CustomSoundProfile) -> Unit
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var measuredPitch by remember { mutableStateOf<Double?>(null) }
    val analyzer: AudioAnalyzer = remember { AudioAnalyzer(context, { pitch -> measuredPitch = pitch }, TunerMode.CHANTER) }
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.background(MaterialTheme.colorScheme.background),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.settings), color = MaterialTheme.colorScheme.primary, fontSize = 28.sp)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))
            Text(stringResource(R.string.base_tone_label), color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
            Slider(
                value = target.toFloat(),
                onValueChange = { onTargetAChange(it.roundToInt().toDouble()) },
                valueRange = 440f..490f
            )
            Text("${target.roundToInt()} Hz", fontSize = 24.sp)
            Spacer(Modifier.height(28.dp))

            if (isRecording && measuredPitch != null) {
                Text(
                    "Erkannter Pitch: ${measuredPitch!!.roundToInt()} Hz",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Button(onClick = {
                measuredPitch = null
                isRecording = true
                analyzer.start()
            }) {
                Text(
                    if (isRecording)
                        stringResource(R.string.recording_in_progress)
                    else
                        stringResource(R.string.record_base_tone),
                    fontSize = 20.sp
                )
            }

            LaunchedEffect(isRecording) {
                if (isRecording) {
                    delay(recordingDuration * 1000L)
                    analyzer.stop()
                    isRecording = false
                    measuredPitch?.let { pitch -> onTargetAChange(pitch) }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                stringResource(R.string.recording_time, recordingDuration),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 22.sp
            )
            Slider(
                value = recordingDuration.toFloat(),
                onValueChange = {
                    val sec = it.roundToInt().coerceIn(1, 15)
                    onRecordingDurationChange(sec)
                },
                valueRange = 1f..15f,
                steps = 13
            )

            Spacer(Modifier.height(30.dp))
            Text(stringResource(R.string.animation_speed), color = MaterialTheme.colorScheme.primary, fontSize = 22.sp)
            Slider(
                value = animationSpeed.toFloat(),
                onValueChange = { onAnimationSpeedChange(it.roundToInt()) },
                valueRange = 100f..600f
            )
            Text("$animationSpeed ms", fontSize = 22.sp)

            Spacer(Modifier.height(30.dp))

            // ==========================================
            // Bereich: Soundtyp auswählen
            // ==========================================
            Text(stringResource(R.string.select_sound_type),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 22.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

// Soundtypen-Liste
            val soundOptions = listOf(
                stringResource(R.string.new_band_sound) to SoundType.NEWBAND,
                stringResource(R.string.warm_sound) to SoundType.WARM,
                stringResource(R.string.brilliant_sound) to SoundType.BRILLANT,
                stringResource(R.string.old_band_sound) to SoundType.OLDBAND,
                stringResource(R.string.custom_sound_type) to SoundType.CUSTOM
            )

// << äußere Column, zentriert >>
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                // << innere Column, für linke Ausrichtung der Zeilen >>
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.wrapContentWidth()
                ) {
                    soundOptions.forEach { (label, type) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .selectable(
                                    selected = soundType == type,
                                    onClick = { onSoundTypeChange(type) }
                                )
                                .padding(vertical = 4.dp, horizontal = 16.dp)
                        ) {
                            RadioButton(
                                selected = soundType == type,
                                onClick = { onSoundTypeChange(type) },
                                modifier = Modifier.size(28.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // ===== Prüflogik für den "Eigener Soundtyp" =====
                            val isCustomSelected = (soundType == SoundType.CUSTOM && type == SoundType.CUSTOM)

                            val hasMissingValues =
                                isCustomSelected && (
                                        customProfile?.frequencies?.isEmpty() == true ||
                                                customProfile?.frequencies?.values?.any { it <= 0.0 } == true
                                        )

                            val textColor = when {
                                hasMissingValues -> Color.Red
                                type == soundType -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                            }

                            Text(
                                text = label,
                                fontSize = 21.sp,
                                color = textColor
                            )
                        }
                    }
                }
            }

            // ==========================================================
            // Neuer Abschnitt: Eigener Soundtyp aufnehmen
            // ==========================================================
            Spacer(Modifier.height(40.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
            CustomSoundSection(
                customProfile = customProfile ?: CustomSoundProfile(),
                onUpdateNote = { note, freq ->
                    val map = customProfile?.frequencies ?: mutableMapOf()
                    map[note] = freq
                    val updated = CustomSoundProfile(map)
                    onCustomProfileChange(updated)
                    // Wenn alle neun Töne fertig → automatisch SoundType „CUSTOM“
                    if (updated.frequencies.size == 9) onSoundTypeChange(SoundType.CUSTOM)
                },
                onClearAll = {
                    clearCustomSoundProfile(context)
                    onCustomProfileChange(CustomSoundProfile())
                },
                recordingDuration = recordingDuration,
                context = context
            )
        }
    }
}

@Composable
fun InfoScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.information), fontSize = 28.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            factory = { context ->
                @SuppressLint("SetJavaScriptEnabled")
                WebView(context).apply {

                    // ✅ Externer Link → Browser öffnen
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            val url = request.url.toString()
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    url.toUri()  // ✅ sauberer
                                )
                                context.startActivity(intent)
                                return true
                            }
                            return false
                        }
                    }

                    settings.javaScriptEnabled = true
                    isVerticalScrollBarEnabled = true
                    isHorizontalScrollBarEnabled = false
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.defaultTextEncodingName = "utf-8"

                    val langTag = getCurrentLanguageTag()
                    val infoFile = when (langTag) {
                        "de" -> "info.html"
                        "en", "sco" -> "info-en.html"
                        else -> "info.html"
                    }
                    loadUrl("file:///android_asset/$infoFile")
                }
            },
            update = { webView ->
                webView.isVerticalScrollBarEnabled = true
                webView.isHorizontalScrollBarEnabled = false
            }
        )
    }
}

@Composable
fun HelpScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help), fontSize = 28.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            factory = { context ->
                @SuppressLint("SetJavaScriptEnabled")
                WebView(context).apply {

                    // ✅ Externer Link → Browser öffnen
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            val url = request.url.toString()
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    url.toUri()  // ✅ sauberer
                                )
                                context.startActivity(intent)
                                return true
                            }
                            return false
                        }
                    }

                    settings.javaScriptEnabled = true
                    isVerticalScrollBarEnabled = true
                    isHorizontalScrollBarEnabled = false
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.defaultTextEncodingName = "utf-8"

                    val langTag = getCurrentLanguageTag()
                    val helpFile = when (langTag) {
                        "de" -> "help.html"
                        "en", "sco" -> "help-en.html"
                        else -> "help.html"
                    }
                    loadUrl("file:///android_asset/$helpFile")
                }
            }
        )
    }
}
@Composable
fun BagpipeTunerUI(
    startTargetA: Double,
    animationSpeedMs: Int,
    context: Context,
    soundType: SoundType,
    customProfile: CustomSoundProfile? = null
) {
    var cents by remember { mutableDoubleStateOf(0.0) }
    var noteName by remember { mutableStateOf("Ton") }           // String – bleibt
    var freq by remember { mutableDoubleStateOf(0.0) }
    var tunerMode by remember { mutableStateOf(TunerMode.CHANTER) }  // Enum – bleibt
    var isTuningActive by remember { mutableStateOf(false) }         // Boolean – bleibt
    var lastSignalTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastStableFreq by remember { mutableDoubleStateOf(0.0) }
    var pendingCount by remember { mutableIntStateOf(0) }

    val kalman: KalmanFilter = remember { KalmanFilter(processNoise = 0.02, measurementNoise = 0.12) }

    var lastDroneNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tunerMode) {
        when (tunerMode) {
            TunerMode.DRONES -> {
                kalman.reset()
                kalman.setParams(processNoise = 0.08, measurementNoise = 0.20) // schneller für Drones
            }
            TunerMode.CHANTER -> {
                kalman.reset()
                kalman.setParams(processNoise = 0.02, measurementNoise = 0.12) // weicher für Chanter
            }
        }
    }

// tunerMode als Referenz, damit der Analyzer immer den aktuellen Modus kennt
    val tunerModeRef = remember { mutableStateOf(tunerMode) }
    tunerModeRef.value = tunerMode

    val analyzer: AudioAnalyzer = remember {
        AudioAnalyzer(
            context = context,
            onFrequencyDetected = { measuredFreq ->
                if (measuredFreq <= 0.0) return@AudioAnalyzer
                lastSignalTime = System.currentTimeMillis()

                // Einschwing‑Stabilität zur Unterdrückung kurzer Peaks
                val diff = abs(measuredFreq - lastStableFreq)
                if (lastStableFreq == 0.0 || diff / lastStableFreq > 0.05) {
                    pendingCount++
                    if (pendingCount < 3) return@AudioAnalyzer
                }
                pendingCount = 0
                lastStableFreq = measuredFreq

                // geglättete Frequenz
                val smoothFreq = kalman.update(measuredFreq)

                if (tunerModeRef.value == TunerMode.CHANTER) {
                    val minFreq = 300.0
                    val maxFreq = 1500.0
                    if (smoothFreq in minFreq..maxFreq && isChanterNote(smoothFreq, startTargetA, soundType, customProfile)) {
                        noteName = getBagpipeNoteName(smoothFreq, startTargetA, soundType, customProfile)
                        cents = calculateCents(smoothFreq, noteName, startTargetA, soundType, customProfile)
                        freq = smoothFreq
                    }
                } else {
                    val minFreq = 80.0
                    val maxFreq = 300.0

                    if (smoothFreq in minFreq..maxFreq && isDroneFreq(smoothFreq, startTargetA)) {
                        val isBass = smoothFreq < startTargetA / 3.0
                        val droneNote = if (isBass) "Bass" else "Tenor"
                        noteName = droneNote
                        lastDroneNote = droneNote
                        val droneExpectedFreq = if (isBass) startTargetA / 4 else startTargetA / 2
                        cents = 1200 * log(smoothFreq / droneExpectedFreq, 2.0)
                        freq = smoothFreq
                    }else {
                        if (lastDroneNote != null) {
                            noteName = lastDroneNote!!
                        }
                    }
                }
            },
            mode = TunerMode.CHANTER  // Startwert; Filter/Algorithmus werden beim Moduswechsel neu gesetzt
        )
    }

    // Rücksetzen, wenn länger kein Signal erkannt wurde
    LaunchedEffect(isTuningActive) {
        while (isTuningActive) {
            delay(500L)
            val now = System.currentTimeMillis()
            if (now - lastSignalTime > 1000L) {
                noteName = "Ton"
                freq = 0.0
                cents = 0.0
            }
        }
    }

    // Animationen für Zeiger
    val animatedCents by animateFloatAsState(cents.toFloat(), tween(animationSpeedMs))
    val normalized = cents.coerceIn(-50.0, 50.0) / 50.0
    val animatedNormalized by animateFloatAsState(normalized.toFloat(), tween(animationSpeedMs))

    // Farben für Balken
    val inTune = Color(0xFF00A658)
    val outTune = Color(0xFFFAE013)
    val noTune = Color(0xFFDD2A1B)
    val inactive = Color.DarkGray

    val barColor = when {
        !isTuningActive -> inactive
        animatedCents in -5.0..5.0 -> inTune
        animatedCents in -15.0..-6.0 || animatedCents in 6.0..15.0 -> outTune
        animatedCents !in -16.0..16.0 -> noTune
        else -> inTune
    }

    // ==========================================
    // UI – Anzeige
    // ==========================================
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Kopfbereich
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = painterResource(id = R.drawable.eh_bagpipe_tuner_round_playstore),
                contentDescription = "Logo",
                modifier = Modifier.size(100.dp).clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(32.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Bagpipe Tuner",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.version),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(60.dp))

        // Skala
        Canvas(Modifier.fillMaxWidth().height(80.dp)) {
            val centerY = size.height / 2
            val zeroX = size.width / 2
            drawLine(Color.Black, Offset(0f, centerY), Offset(size.width, centerY), strokeWidth = 4f)
            for (i in -50..50 step 10) {
                val x = zeroX + (i / 50f) * (size.width / 2)
                drawLine(Color.Black, Offset(x, centerY - 15f), Offset(x, centerY + 15f), strokeWidth = 3f)
                drawContext.canvas.nativeCanvas.apply {
                    val p = android.graphics.Paint().apply {
                        color = android.graphics.Color.BLACK
                        textAlign = android.graphics.Paint.Align.CENTER
                        textSize = 50f
                    }
                    drawText(if (i > 0) "+$i" else "$i", x, centerY - 60f, p)
                }
            }
            val markerX = zeroX + animatedNormalized * (size.width / 2)
            drawLine(Color.Blue, Offset(markerX, centerY - 37.5f), Offset(markerX, centerY + 37.5f), strokeWidth = 12f)
        }

        Spacer(Modifier.height(12.dp))

        // Balken zur Tuninganzeige
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(20.dp)
                .clip(CircleShape)
                .background(barColor)
        )

        Spacer(Modifier.height(20.dp))

        // Textanzeige mit Cents
        Text(String.format(Locale.US, "%.1f Cent", animatedCents), color = Color.Black, fontSize = 24.sp)

        // Anzeige: Note - Frequenz
        Text(
            text = when {
                noteName == "Ton" -> stringResource(R.string.no_signal)
                else -> {
                    val measuredFreq = freq
                    val reference = if (soundType == SoundType.CUSTOM)
                        customProfile?.frequencies ?: emptyMap()
                    else
                        soundFrequencies[soundType] ?: emptyMap()
                    val measuredLowA = reference["Low A"] ?: measuredFreq
                    val toneRatio = (reference[noteName] ?: measuredLowA) / measuredLowA
                    val relativeFreq = measuredFreq / toneRatio
                    val relDisplay = relativeFreq.roundToInt()
                    val measuredDisplay = measuredFreq.roundToInt()
                    "$noteName – ${relDisplay} Hz (${measuredDisplay} Hz)"
                }
            },
            fontSize = 24.sp, color = Color.Black
        )

        Spacer(Modifier.height(16.dp))

        // Start/Stop‑Button
        Button(
            onClick = {
                isTuningActive = !isTuningActive
                if (isTuningActive) {
                    analyzer.start()
                    lastSignalTime = System.currentTimeMillis()
                } else {
                    analyzer.stop()
                    noteName = "Ton"
                    freq = 0.0
                    lastStableFreq = 0.0
                    pendingCount = 0
                }
            }
        ) {
            Text(
                if (isTuningActive)
                    stringResource(R.string.stop_tuning)
                else
                    stringResource(R.string.start_tuning),
                fontSize = 22.sp
            )
        }

        Spacer(Modifier.height(30.dp))
        Text(stringResource(R.string.base_tone_hz, startTargetA.roundToInt()), fontSize = 22.sp)

        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.select_tuner_mode), fontSize = 25.sp)
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.selectable(selected = tunerMode == TunerMode.CHANTER, onClick = {
                    tunerMode = TunerMode.CHANTER
                })
            ) {
                RadioButton(selected = tunerMode == TunerMode.CHANTER, onClick = { tunerMode = TunerMode.CHANTER })
                Text("Chanter", fontSize = 22.sp)
            }
            Spacer(Modifier.width(32.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.selectable(selected = tunerMode == TunerMode.DRONES, onClick = {
                    tunerMode = TunerMode.DRONES
                })
            ) {
                RadioButton(selected = tunerMode == TunerMode.DRONES, onClick = { tunerMode = TunerMode.DRONES })
                Text("Drones", fontSize = 22.sp)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.sound_type, soundTypeLabel(soundType)), fontSize = 22.sp)

        // Diagnoseanzeige für Custom-Profil
        if (soundType == SoundType.CUSTOM) {
            val count = customProfile?.frequencies?.count { it.value > 0.0 } ?: 0
            Text(
                text = if (count > 0)
                    stringResource(R.string.custom_type_loaded, count)
                else
                    stringResource(R.string.custom_type_invalid),
                fontSize = 18.sp,
                color = if (count > 0)
                    MaterialTheme.colorScheme.primary
                else
                    Color.Red,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

fun getCurrentLanguageTag(): String {
    val locales = AppCompatDelegate.getApplicationLocales()
    val locale = locales[0]
    return locale?.language?.lowercase(Locale.ROOT) ?: ""
}

fun soundTypeLabel(soundType: SoundType): String = when (soundType) {
    SoundType.NEWBAND -> "new Band Sound"
    SoundType.WARM -> "Warmer Sound"
    SoundType.BRILLANT -> "Brillanter Sound"
    SoundType.OLDBAND -> "old Band Sound"
    SoundType.CUSTOM -> "Eigener Soundtyp"
}

fun isDroneFreq(freq: Double, targetA: Double): Boolean {
    val bass = targetA / 4
    val tenor = targetA / 2
    val toleranceHz = 5.0
    return abs(freq - bass) < toleranceHz || abs(freq - tenor) < toleranceHz
}

fun isChanterNote(
    freq: Double,
    targetA: Double,
    soundType: SoundType,
    customProfile: CustomSoundProfile? = null
): Boolean {
    val reference = when (soundType) {
        SoundType.CUSTOM -> customProfile?.frequencies ?: emptyMap()
        else -> soundFrequencies[soundType] ?: emptyMap()
    }
    if (reference.isEmpty()) return false

    val lowA = reference["Low A"] ?: 480.0

    return reference.values.any { hz ->
        val noteFreq = targetA * hz / lowA
        val toleranceHz = 10.0
        abs(freq - noteFreq) < toleranceHz
    }
}

fun getBagpipeNoteName(freq: Double, targetA: Double, soundType: SoundType, customProfile: CustomSoundProfile? = null): String {
    val reference = when (soundType) {
        SoundType.CUSTOM -> customProfile?.frequencies ?: emptyMap()
        else -> soundFrequencies[soundType] ?: emptyMap()
    }
    if (reference.isEmpty()) return "Ton"

    val measuredLowA = reference["Low A"] ?: reference.values.first()
    var closestNote = "Ton"
    var minDiff = Double.MAX_VALUE
    for ((name, hz) in reference) {
        val noteFreq = targetA * hz / measuredLowA
        val diff = abs(freq - noteFreq)
        if (diff < minDiff) {
            minDiff = diff
            closestNote = name
        }
    }
    return closestNote
}

fun calculateCents(freq: Double, noteName: String, targetA: Double, soundType: SoundType, customProfile: CustomSoundProfile? = null): Double {
    val reference = when (soundType) {
        SoundType.CUSTOM -> customProfile?.frequencies ?: emptyMap()
        else -> soundFrequencies[soundType] ?: emptyMap()
    }
    if (!reference.containsKey(noteName)) return 0.0

    val lowA = reference["Low A"] ?: reference.values.first()
    val ratio = (reference[noteName] ?: lowA) / lowA
    val expectedFreq = targetA * ratio
    return 1200 * log(freq / expectedFreq, 2.0)
}

// ==========================================
// Custom Soundtype – Speicherung & Laden
// ==========================================
data class CustomSoundProfile(
    val frequencies: MutableMap<String, Double> = mutableMapOf()
)

fun saveCustomSoundProfile(context: Context, profile: CustomSoundProfile) {
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val json = JSONObject()
    for ((note, freq) in profile.frequencies) json.put(note, freq)
    prefs.edit { putString("customSoundProfile", json.toString()) }
}

fun loadCustomSoundProfile(context: Context): CustomSoundProfile? {
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val jsonString = prefs.getString("customSoundProfile", null) ?: return null
    return try {
        val json = JSONObject(jsonString)
        val map = mutableMapOf<String, Double>()
        json.keys().forEach { key ->
            map[key] = json.getDouble(key)
        }
        CustomSoundProfile(map)
    } catch (e: JSONException) {
        e.printStackTrace()
        null
    }
}

fun clearCustomSoundProfile(context: Context) {
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    prefs.edit { remove("customSoundProfile") }
}

// ==========================================
// KalmanFilter‑Klasse (mit Parameteranpassung für Modi)
// ==========================================
class KalmanFilter(
    private var processNoise: Double = 0.05,     // Prozessrauschen (Reaktionsgeschwindigkeit)
    private var measurementNoise: Double = 0.08, // Messrauschen (Glättung)
    private var estimate: Double = 0.0
) {
    private var errorEstimate = 1.0

    /**
     * Berechnet den neuen geglätteten Wert auf Basis der Messung.
     */
    fun update(measurement: Double): Double {
        val kalmanGain = errorEstimate / (errorEstimate + measurementNoise)
        estimate += kalmanGain * (measurement - estimate)
        errorEstimate = (1.0 - kalmanGain) * errorEstimate + processNoise
        return estimate
    }

    /**
     * Setzt den internen Status zurück – beispielsweise beim Tonwechsel.
     */
    fun reset(toValue: Double = estimate) {
        estimate = toValue
        errorEstimate = 1.0
    }

    /**
     * Ermöglicht die dynamische Anpassung der Filter‑Parameter.
     * - processNoise groß → schneller, aber weniger stabil
     * - measurementNoise klein → reagiert empfindlicher
     */
    fun setParams(processNoise: Double, measurementNoise: Double) {
        this.processNoise = processNoise
        this.measurementNoise = measurementNoise
    }
}

class AudioAnalyzer(
    private val context: Context,
    private val onFrequencyDetected: (Double) -> Unit,
    private val mode: TunerMode = TunerMode.CHANTER
) {
    private var dispatcher: AudioDispatcher? = null
    private var job: Job? = null
    private var isRunning = false

    // interne Zwischenspeicher
    private var lastFreq = 0.0
    private var smoothFactor = 0.12

    fun start() {
        if (isRunning) return
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) return

        isRunning = true
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val sampleRate = 44100
                val bufferSize = 8192
                val overlapSize = 6144

                val audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize * 2
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) return@launch

                if (AutomaticGainControl.isAvailable()) {
                    try { AutomaticGainControl.create(audioRecord.audioSessionId) } catch (_: Exception) {}
                }

                val audioFormat = TarsosDSPAudioFormat(sampleRate.toFloat(), 16, 1, true, false)
                val audioStream = object : TarsosDSPAudioInputStream {
                    override fun read(b: ByteArray?, offset: Int, length: Int): Int {
                        if (b == null) return 0
                        val shortBuffer = ShortArray(length / 2)
                        val readCount = audioRecord.read(shortBuffer, 0, shortBuffer.size)
                        var idx = offset
                        for (i in 0 until readCount) {
                            val s = shortBuffer[i]
                            b[idx++] = (s.toInt() and 0xFF).toByte()
                            b[idx++] = ((s.toInt() shr 8) and 0xFF).toByte()
                        }
                        return readCount * 2
                    }
                    override fun skip(n: Long): Long = 0L
                    override fun close() {
                        try { audioRecord.stop() } catch (_: Exception) {}
                        audioRecord.release()
                    }
                    override fun getFormat(): TarsosDSPAudioFormat = audioFormat
                    override fun getFrameLength(): Long = -1L
                }

                dispatcher = AudioDispatcher(audioStream, bufferSize, overlapSize)

                dispatcher!!.addAudioProcessor(HighPass(70f, sampleRate.toFloat()))
                dispatcher!!.addAudioProcessor(LowPassSP(1800f, sampleRate.toFloat()))

                val handler = PitchDetectionHandler { result, _ ->
                    val pitch = if (result.isPitched) result.pitch.toDouble() else 0.0
                    if (pitch in 100.0..2000.0) {
                        if (lastFreq == 0.0) {
                            lastFreq = pitch
                            onFrequencyDetected(pitch)
                        } else {
                            lastFreq = lastFreq * (1 - smoothFactor) + pitch * smoothFactor
                            onFrequencyDetected(lastFreq)
                        }
                    }
                }

                dispatcher!!.addAudioProcessor(
                    PitchProcessor(PitchProcessor.PitchEstimationAlgorithm.YIN, sampleRate.toFloat(), bufferSize, handler)
                )

                audioRecord.startRecording()
                try { dispatcher?.run() } catch (e: Exception) { e.printStackTrace() }

                if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                    try { audioRecord.stop() } catch (_: Exception) {}
                    audioRecord.release()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        isRunning = false
        try { dispatcher?.stop() } catch (_: Exception) {}
        job?.cancel()
        dispatcher = null
        lastFreq = 0.0
    }
}

@Composable
fun CustomSoundSection(
    customProfile: CustomSoundProfile,
    onUpdateNote: (String, Double) -> Unit,
    onClearAll: () -> Unit,
    recordingDuration: Int,
    context: Context
) {
    var currentNote by remember { mutableStateOf<String?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var measuredPitch by remember { mutableStateOf<Double?>(null) }
    var isAutoDurchlaufRunning by remember { mutableStateOf(false) }
    var autoPitch by remember { mutableStateOf<Double?>(null) }
    var autoDurchlaufJob by remember { mutableStateOf<Job?>(null) }
    var countdown by remember { mutableIntStateOf(0) }

    // gemeinsamer Analyzer – identische Logik wie beim Grundton
    val analyzer = remember {
        AudioAnalyzer(
            context = context,
            onFrequencyDetected = { pitch ->
                if (pitch > 0.0 && currentNote != null) {
                    measuredPitch = pitch
                    autoPitch = pitch
                }
            },
            mode = TunerMode.CHANTER
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(stringResource(R.string.record_custom_sound_section),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))

        val notes = listOf("Low G","Low A","B","C#","D","E","F#","High G","High A")

        notes.forEach { note ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 12.dp)
            ) {
                // Tonname links – feste proportionale Breite
                Text(
                    text = note,
                    modifier = Modifier.weight(0.8f),
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Start
                )

                // Frequenz mittig – leicht höheres Gewicht
                Text(
                    text = when {
                        // Einzelaufnahme: gemessener Wert
                        isRecording && currentNote == note && measuredPitch != null ->
                            "${measuredPitch!!.roundToInt()} Hz"

                        // Auto‑Durchlauf: Live‑Pitch während laufender Note
                        isAutoDurchlaufRunning && currentNote == note && autoPitch != null ->
                            "${autoPitch!!.roundToInt()} Hz"

                        // Gespeicherte Werte nach Abschluss
                        customProfile.frequencies[note] != null ->
                            "${customProfile.frequencies[note]!!.roundToInt()} Hz"

                        else -> "–"
                    },
                    modifier = Modifier.weight(1.8f),
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp,
                    color = if (isRecording && currentNote == note)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )

                // Button rechts
                val isActive = currentNote == note && (isRecording || isAutoDurchlaufRunning)
                Button(
                    onClick = {
                        measuredPitch = null
                        currentNote = note
                        isRecording = true
                        analyzer.start()

                        CoroutineScope(Dispatchers.Main).launch {
                            delay(recordingDuration * 1000L)
                            analyzer.stop()
                            measuredPitch?.let { pitch ->
                                onUpdateNote(note, pitch)
                            }
                            currentNote = null
                            isRecording = false
                        }
                    },
                    enabled = !isActive,
                    modifier = Modifier
                        .weight(1.0f)
                        .padding(start = 8.dp)
                        .height(42.dp),
                    colors = if (isActive)
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    else
                        ButtonDefaults.buttonColors()
                ) {
                    Text(
                        text = if (isActive)
                            stringResource(R.string.recording_running)
                        else
                            stringResource(R.string.start_recording_note),
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

// Auto‑Durchlauf‑Button
        Button(
            onClick = {
                // Wenn Durchlauf läuft → abbrechen
                if (isAutoDurchlaufRunning) {
                    autoDurchlaufJob?.cancel()
                    analyzer.stop()
                    isAutoDurchlaufRunning = false
                    autoDurchlaufJob = null
                    countdown = 0
                } else {
                    // Countdown starten
                    countdown = 10
                    CoroutineScope(Dispatchers.Main).launch {
                        // visueller Countdown – jede Sekunde runterzählen
                        while (countdown > 0) {
                            delay(1000L)
                            countdown--
                        }

                        // nach Ablauf Countdown → Durchlauf starten
                        isAutoDurchlaufRunning = true
                        autoDurchlaufJob = CoroutineScope(Dispatchers.IO).launch {
                            try {
                                startAutoDurchlauf(
                                    notes = listOf("Low G","Low A","B","C#","D","E","F#","High G","High A"),
                                    recordingDuration = recordingDuration,
                                    analyzerContext = context,
                                    onUpdateNote = onUpdateNote,
                                    onCurrentNoteChange = { currentNote = it },
                                    onResetAutoPitch = { autoPitch = null },
                                    onLivePitch = { autoPitch = it }
                                )
                            } finally {
                                withContext(Dispatchers.Main) {
                                    isAutoDurchlaufRunning = false
                                    autoPitch = null
                                    currentNote = null
                                    countdown = 0
                                }
                            }
                        }
                    }
                }
            },
            enabled = !isRecording && countdown == 0, // während Countdown Button deaktiviert
            colors = if (isAutoDurchlaufRunning)
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            else
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(48.dp)
        ) {
            Text(
                text = when {
                    countdown > 0 -> "Start in $countdown s …"
                    isAutoDurchlaufRunning -> stringResource(R.string.cancel_runthrough)
                    else -> stringResource(R.string.start_runthrough)
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        Spacer(Modifier.height(10.dp))

        // Alle löschen
        Button(
            onClick = { onClearAll() },
            enabled = !isRecording,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Text(stringResource(R.string.delete_all),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

/**
 * Führt eine automatische Aufnahme aller Bagpipe‑Töne nacheinander aus.
 * Zeigt den gerade aktiven Ton im UI an und aktualisiert nach jedem Schritt die Frequenz.
 */
suspend fun startAutoDurchlauf(
    notes: List<String>,
    recordingDuration: Int,
    analyzerContext: Context,
    onUpdateNote: (String, Double) -> Unit,
    onCurrentNoteChange: (String?) -> Unit,
    onResetAutoPitch: () -> Unit,
    onLivePitch: (Double) -> Unit
) = coroutineScope {

    var analyzer: AudioAnalyzer? = null   // aktuell laufender Analyzer

    try {
        for (note in notes) {
            ensureActive()                 // prüft, ob der Job noch aktiv ist

            withContext(Dispatchers.Main) {
                onCurrentNoteChange(note)
            }

            var pitchDetected: Double? = null
            analyzer = AudioAnalyzer(
                context = analyzerContext,
                onFrequencyDetected = { pitch ->
                    if (isActive && pitch > 0.0) {
                        pitchDetected = pitch
                        CoroutineScope(Dispatchers.Main).launch {
                            onLivePitch(pitch)
                        }
                    }
                },
                mode = TunerMode.CHANTER
            )

            analyzer.start()

            try {
                // Aufnahmezeit – in kleinen Schritten, damit abbrechbar
                repeat(recordingDuration * 10) {
                    ensureActive()
                    delay(100L)
                }
            } catch (e: CancellationException) {
                analyzer.stop()
                throw e                     // Job-Ende sauber weiterreichen
            }

            analyzer.stop()

            withContext(Dispatchers.Main) {
                pitchDetected?.let { freq ->
                    onUpdateNote(note, freq)
                }
                onResetAutoPitch()
                onCurrentNoteChange(null)
            }

            // kurze Pause zwischen den Tönen (ebenfalls abbrechbar)
            repeat(10) {
                ensureActive()
                delay(50L)
            }
        }
    } catch (e: CancellationException) {
        // Durchlauf wurde abgebrochen → Analyzer sofort stoppen
        analyzer?.stop()
        throw e
    }
}