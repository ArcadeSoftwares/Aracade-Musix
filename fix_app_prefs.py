import re

with open("app/src/main/java/com/arcadesoftware/musix/MainActivity.kt", "r") as f:
    text = f.read()

start_pattern = '                } else if (settingsScreen == "App") {\n'
end_pattern = '                }\n                Spacer(modifier = Modifier.height(48.dp))'

start_idx = text.find(start_pattern)
end_idx = text.find(end_pattern)

new_content = """                } else if (settingsScreen == "App") {
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        IconButton(onClick = { settingsScreen = "Main" }, modifier = Modifier.align(Alignment.CenterStart)) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                        }
                        Text(
                            text = "App Preferences",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    // Theme Selection
                    val themePref = LocalThemePreference.current
                    val setThemePref = LocalThemePreferenceSetter.current
                    
                    Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp, start = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cardBg).padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val options = listOf("System", "Light", "Dark")
                        options.forEachIndexed { index, title ->
                            val selected = themePref == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { setThemePref(index) }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // App Icon Selection
                    val appIconPref = sharedPrefs.getInt("app_icon_preference", 0)
                    Text("App Icon", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp, start = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cardBg).padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        val icons = listOf(R.mipmap.ic_launcher, R.mipmap.ic_launcher_dark, R.mipmap.ic_launcher_light)
                        val iconNames = listOf("Default", "Dark", "Light")
                        icons.forEachIndexed { index, iconRes ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                                sharedPrefs.edit().putInt("app_icon_preference", index).apply()
                                AppIconManager.changeAppIcon(context, index)
                                android.widget.Toast.makeText(context, "App Icon Updated", android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(2.dp, if (appIconPref == index) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                ) {
                                    AsyncImage(model = iconRes, contentDescription = iconNames[index], modifier = Modifier.fillMaxSize())
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(iconNames[index], style = MaterialTheme.typography.bodyMedium, fontWeight = if (appIconPref == index) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    Text("Playback & Downloads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp, start = 8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        var rememberPos by remember { mutableStateOf(sharedPrefs.getBoolean("remember_playback_pos", true)) }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Remember Playback Position", fontWeight = FontWeight.Medium)
                            com.arcadesoftware.musix.components.LiquidToggle(checked = rememberPos, onCheckedChange = {
                                rememberPos = it
                                sharedPrefs.edit().putBoolean("remember_playback_pos", it).apply()
                            })
                        }
                        androidx.compose.material3.Divider(color = Color.Gray.copy(alpha = 0.2f))
                        
                        var alwaysShuffle by remember { mutableStateOf(sharedPrefs.getBoolean("always_shuffle", false)) }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Always Shuffle", fontWeight = FontWeight.Medium)
                            com.arcadesoftware.musix.components.LiquidToggle(checked = alwaysShuffle, onCheckedChange = {
                                alwaysShuffle = it
                                sharedPrefs.edit().putBoolean("always_shuffle", it).apply()
                            })
                        }
                        androidx.compose.material3.Divider(color = Color.Gray.copy(alpha = 0.2f))
                        
                        var autoDownload by remember { mutableStateOf(sharedPrefs.getBoolean("auto_download_playlists", true)) }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Auto Download Playlists", fontWeight = FontWeight.Medium)
                            com.arcadesoftware.musix.components.LiquidToggle(checked = autoDownload, onCheckedChange = {
                                autoDownload = it
                                sharedPrefs.edit().putBoolean("auto_download_playlists", it).apply()
                            })
                        }
                        androidx.compose.material3.Divider(color = Color.Gray.copy(alpha = 0.2f))
                        
                        var wifiOnly by remember { mutableStateOf(sharedPrefs.getBoolean("download_wifi_only", true)) }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Download on Wi-Fi Only", fontWeight = FontWeight.Medium)
                            com.arcadesoftware.musix.components.LiquidToggle(checked = wifiOnly, onCheckedChange = {
                                wifiOnly = it
                                sharedPrefs.edit().putBoolean("download_wifi_only", it).apply()
                            })
                        }
                    }
"""

text = text[:start_idx] + new_content + text[end_idx:]

with open("app/src/main/java/com/arcadesoftware/musix/MainActivity.kt", "w") as f:
    f.write(text)

