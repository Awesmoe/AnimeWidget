package com.example.animewidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    UsernameScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsernameScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var useEnglishTitle by remember { mutableStateOf(true) }
    var includePlanToWatch by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // Load saved preferences
    LaunchedEffect(Unit) {
        try {
            val existing = getUsername(context).firstOrNull()
            if (!existing.isNullOrBlank()) username = existing
        } catch (_: Exception) { }
        try {
            val existingToggle = getUseEnglishTitle(context).firstOrNull()
            if (existingToggle != null) useEnglishTitle = existingToggle
        } catch (_: Exception) { }
        try {
            val existingPlanToWatch = getIncludePlanToWatch(context).firstOrNull()
            if (existingPlanToWatch != null) includePlanToWatch = existingPlanToWatch
        } catch (_: Exception) { }
        loaded = true
    }

    if (!loaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Anime Widget Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Settings Cards
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // MAL Username Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "MyAnimeList Username",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            placeholder = { Text("Enter your MAL username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
 /*                           supportingText = {
                                Text("This is used to fetch your anime list")
                            }
   */                     )
                    }
                }

                // Display Options Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Display Options",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        // English titles toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Use English Titles",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Show English names when available",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = useEnglishTitle,
                                onCheckedChange = { useEnglishTitle = it }
                            )
                        }
                    }
                }

                // List Selection Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Lists to Include",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Watching checkbox (always checked, disabled)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = true,
                                onCheckedChange = null,
                                enabled = false
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Watching",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Always included",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Plan to Watch checkbox
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = includePlanToWatch,
                                onCheckedChange = { includePlanToWatch = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Plan to Watch",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Include upcoming shows",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save Button
            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        Log.d("MainActivity", "Starting save...")

                        saveUsername(context, username)
                        saveUseEnglishTitle(context, useEnglishTitle)
                        saveIncludePlanToWatch(context, includePlanToWatch)

                        Log.d("MainActivity", "Settings saved, updating widget...")

                        try {
                            // Send broadcast to trigger widget update
                            val intent = Intent(context, AnimeWidgetReceiver::class.java).apply {
                                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                            }
                            val appWidgetManager = AppWidgetManager.getInstance(context)
                            val componentName = ComponentName(context, AnimeWidgetReceiver::class.java)
                            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

                            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                            context.sendBroadcast(intent)

                            Log.d("MainActivity", "Widget update broadcast sent for ${appWidgetIds.size} widgets")
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Failed to update widget", e)
                        }

                        isSaving = false
                        (context as? Activity)?.finish()
                    }
                },
                enabled = username.isNotBlank() && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Save Settings", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}