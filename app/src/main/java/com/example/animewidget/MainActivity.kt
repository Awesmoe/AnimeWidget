package com.example.animewidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.util.Log
import androidx.compose.ui.Alignment
import androidx.glance.appwidget.updateAll
import getUseEnglishTitle
import getUsername
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import saveUseEnglishTitle
import saveUsername

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            UsernameScreen()
        }
    }
}

@Composable
fun UsernameScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var useEnglishTitle by remember { mutableStateOf(true) }

    // load saved username once
    LaunchedEffect(Unit) {
        try {
            val existing = getUsername(context).firstOrNull()
            if (!existing.isNullOrBlank()) username = existing
        } catch (e: Exception) { }

        try {
            val existingToggle = getUseEnglishTitle(context).firstOrNull()
            if (existingToggle != null) useEnglishTitle = existingToggle
        } catch (e: Exception) { }

        loaded = true
    }

    if (!loaded) {
        // show a simple loading text while fetching username
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading...")
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // inner column for toggle + username block
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // toggle for title preference
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show English titles?")
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = useEnglishTitle,
                    onCheckedChange = { useEnglishTitle = it }
                )
            }

            Spacer(modifier = Modifier.height(24.dp)) // space before username

            // username input
            Text("Enter your MAL username:")
            Spacer(Modifier.height(8.dp))
            TextField(
                value = username,
                onValueChange = { username = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        saveUsername(context, username)
                        saveUseEnglishTitle(context, useEnglishTitle)

                        // Force widget update with new data
                        try {
                            AnimeWidget().updateAll(context)
                            Log.d("MainActivity", "Widget update triggered")
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Failed to update widget", e)
                        }

                        // close activity
                        (context as? Activity)?.finish()
                    }
                },
                enabled = username.isNotBlank()
            ) {
                Text("Save")
            }

        }
    }
}