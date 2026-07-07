import re

with open("app/src/main/java/com/arcadesoftware/musix/MainActivity.kt", "r") as f:
    text = f.read()

start_pattern = '                if (settingsScreen == "Main") {\n'
end_pattern = '                }\n                Spacer(modifier = Modifier.height(48.dp))'

start_idx = text.find(start_pattern)
end_idx = text.find(end_pattern)

new_content = """                if (settingsScreen == "Main") {
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Text(
                            text = "Account",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    if (currentUser == null) {
                        OutlinedButton(
                            onClick = {
                                if (isSigningIn) return@OutlinedButton
                                isSigningIn = true
                                scope.launch {
                                    try {
                                        val credentialManager = androidx.credentials.CredentialManager.create(context)
                                        val request = androidx.credentials.GetCredentialRequest.Builder()
                                            .addCredentialOption(
                                                com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
                                                    .setFilterByAuthorizedAccounts(false)
                                                    .setServerClientId("983178184530-c0grj95ua7kb862qnr0f9nnhr2g3t5qt.apps.googleusercontent.com")
                                                    .setAutoSelectEnabled(false)
                                                    .build()
                                            )
                                            .build()
                                        val result = credentialManager.getCredential(context, request)
                                        val credential = result.credential
                                        if (credential is androidx.credentials.CustomCredential &&
                                            credential.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                        ) {
                                            val googleIdTokenCredential = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data)
                                            val idToken = googleIdTokenCredential.idToken
                                            val authCredential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                                            com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(authCredential)
                                                .addOnSuccessListener {
                                                    isSigningIn = false
                                                    com.arcadesoftware.musix.db.FirestoreSyncManager.syncUserDetails(context)
                                                    showWelcomePopup = true
                                                    scope.launch {
                                                        kotlinx.coroutines.delay(2500)
                                                        showWelcomePopup = false
                                                    }
                                                }
                                                .addOnFailureListener {
                                                    isSigningIn = false
                                                    android.widget.Toast.makeText(context, "Sign in failed: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
                                                }
                                        } else {
                                            isSigningIn = false
                                        }
                                    } catch (e: Exception) {
                                        isSigningIn = false
                                        android.widget.Toast.makeText(context, "Google Sign-In failed", android.widget.Toast.LENGTH_SHORT).show()
                                        e.printStackTrace()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                    color = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color.Black
                                )
                            } else {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_google),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = Color.Unspecified
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Sign in with Google",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(cardBg)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
                            val rotation by infiniteTransition.animateFloat(
                                initialValue = 0f, targetValue = 360f,
                                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                    animation = androidx.compose.animation.core.tween(3000, easing = androidx.compose.animation.core.LinearEasing),
                                    repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                                )
                            )
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .graphicsLayer { rotationZ = rotation }
                                        .border(
                                            2.dp,
                                            androidx.compose.ui.graphics.Brush.sweepGradient(listOf(Color.Cyan, Color.Magenta, Color.Yellow, Color.Cyan)),
                                            androidx.compose.foundation.shape.CircleShape
                                        )
                                )
                                AsyncImage(
                                    model = currentUser?.photoUrl,
                                    contentDescription = "Profile Picture",
                                    modifier = Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(currentUser?.displayName ?: "User", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(currentUser?.email ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Cloud Sync Features Menu Button
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(cardBg)
                                .then(if (currentUser == null) Modifier.graphicsLayer { alpha = 0.5f } else Modifier)
                                .clickable(enabled = currentUser != null) { settingsScreen = "Cloud" }
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Cloud, contentDescription = null)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text("Cloud Sync Features", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                                }
                                Icon(Icons.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Gray)
                            }
                        }

                        if (currentUser == null) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        if (isSigningIn) return@clickable
                                        isSigningIn = true
                                        scope.launch {
                                            try {
                                                val credentialManager = androidx.credentials.CredentialManager.create(context)
                                                val request = androidx.credentials.GetCredentialRequest.Builder()
                                                    .addCredentialOption(
                                                        com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
                                                            .setFilterByAuthorizedAccounts(false)
                                                            .setServerClientId("983178184530-c0grj95ua7kb862qnr0f9nnhr2g3t5qt.apps.googleusercontent.com")
                                                            .setAutoSelectEnabled(false)
                                                            .build()
                                                    )
                                                    .build()
                                                val result = credentialManager.getCredential(context, request)
                                                val credential = result.credential
                                                if (credential is androidx.credentials.CustomCredential &&
                                                    credential.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                                                ) {
                                                    val googleIdTokenCredential = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data)
                                                    val idToken = googleIdTokenCredential.idToken
                                                    val authCredential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
                                                    com.google.firebase.auth.FirebaseAuth.getInstance().signInWithCredential(authCredential)
                                                        .addOnSuccessListener {
                                                            isSigningIn = false
                                                            com.arcadesoftware.musix.db.FirestoreSyncManager.syncUserDetails(context)
                                                            showWelcomePopup = true
                                                            scope.launch {
                                                                kotlinx.coroutines.delay(2500)
                                                                showWelcomePopup = false
                                                            }
                                                        }
                                                        .addOnFailureListener {
                                                            isSigningIn = false
                                                            android.widget.Toast.makeText(context, "Sign in failed: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
                                                        }
                                                } else {
                                                    isSigningIn = false
                                                }
                                            } catch (e: Exception) {
                                                isSigningIn = false
                                                android.widget.Toast.makeText(context, "Google Sign-In failed", android.widget.Toast.LENGTH_SHORT).show()
                                                e.printStackTrace()
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    shape = RoundedCornerShape(20.dp),
                                    shadowElevation = 4.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sign In Required", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // App Preferences Menu Button
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg)
                            .clickable { settingsScreen = "App" }
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Settings, contentDescription = null)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("App Preferences", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                            }
                            Icon(Icons.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Gray)
                        }
                    }

                    if (currentUser != null) {
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = {
                                showAccountSheet = false
                                com.arcadesoftware.musix.db.FirestoreSyncManager.pushAllLocalDataToFirestore(context)
                                com.arcadesoftware.musix.db.FirestoreSyncManager.clearAllLocalData(context)
                                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                                com.arcadesoftware.musix.components.ByeAnimManager.trigger()
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFFFF453A) else Color(0xFFFF3B30),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text("Sign Out")
                        }
                    }
                } else if (settingsScreen == "Cloud") {
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        IconButton(onClick = { settingsScreen = "Main" }, modifier = Modifier.align(Alignment.CenterStart)) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                        }
                        Text(
                            text = "Cloud Sync",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "CLOUD SYNC FEATURES",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLightMode) Color.Gray else Color.LightGray,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sync Playlists", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text("Sync custom playlists with Firebase cloud storage", fontSize = 12.sp, color = Color.Gray)
                            }
                            com.arcadesoftware.musix.components.LiquidToggle(
                                selected = { syncPlaylists },
                                onSelect = { enabled ->
                                    syncPlaylists = enabled
                                    sharedPrefs.edit().putBoolean("sync_playlists", enabled).apply()
                                    com.arcadesoftware.musix.db.FirestoreSyncManager.schedulePushAllLocalDataToFirestore(context)
                                },
                                backdrop = mainBackdrop
                            )
                        }

                        androidx.compose.material3.HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), thickness = 0.5.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sync Library", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text("Sync liked songs, albums, and artists", fontSize = 12.sp, color = Color.Gray)
                            }
                            com.arcadesoftware.musix.components.LiquidToggle(
                                selected = { syncLibrary },
                                onSelect = { enabled ->
                                    syncLibrary = enabled
                                    sharedPrefs.edit().putBoolean("sync_library", enabled).apply()
                                    com.arcadesoftware.musix.db.FirestoreSyncManager.schedulePushAllLocalDataToFirestore(context)
                                },
                                backdrop = mainBackdrop
                            )
                        }

                        androidx.compose.material3.HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), thickness = 0.5.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sync History & Recommendation", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text("Sync history details and homepage recommendations", fontSize = 12.sp, color = Color.Gray)
                            }
                            com.arcadesoftware.musix.components.LiquidToggle(
                                selected = { syncHistory },
                                onSelect = { enabled ->
                                    syncHistory = enabled
                                    sharedPrefs.edit().putBoolean("sync_history", enabled).apply()
                                    com.arcadesoftware.musix.db.FirestoreSyncManager.schedulePushAllLocalDataToFirestore(context)
                                },
                                backdrop = mainBackdrop
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { showDeleteConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete Account", color = Color.White)
                    }

                    if (showDeleteConfirmDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirmDialog = false },
                            title = { Text("Delete Account") },
                            text = {
                                Column {
                                    Text("Type 'Confirm' to delete your account. This action cannot be undone.")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = deleteConfirmText,
                                        onValueChange = { deleteConfirmText = it },
                                        label = { Text("Type Confirm") },
                                        singleLine = true
                                    )
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        if (deleteConfirmText == "Confirm") {
                                            currentUser?.delete()?.addOnCompleteListener { task ->
                                                if (task.isSuccessful) {
                                                    android.widget.Toast.makeText(context, "Account deleted", android.widget.Toast.LENGTH_SHORT).show()
                                                    currentUser = null
                                                    settingsScreen = "Main"
                                                    showDeleteConfirmDialog = false
                                                    deleteConfirmText = ""
                                                } else {
                                                    android.widget.Toast.makeText(context, "Failed: ${task.exception?.message}", android.widget.Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        } else {
                                            android.widget.Toast.makeText(context, "Please type 'Confirm'", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text("Delete", color = Color.Red)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                } else if (settingsScreen == "App") {
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

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "APP PREFERENCES",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLightMode) Color.Gray else Color.LightGray,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Remember playback position", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text("Resume playing song from where you left", fontSize = 12.sp, color = Color.Gray)
                            }
                            com.arcadesoftware.musix.components.LiquidToggle(
                                selected = { resumePlayback },
                                onSelect = { enabled ->
                                    resumePlayback = enabled
                                    sharedPrefs.edit().putBoolean("resume_playback", enabled).apply()
                                    com.arcadesoftware.musix.db.FirestoreSyncManager.schedulePushAllLocalDataToFirestore(context)
                                },
                                backdrop = mainBackdrop
                            )
                        }

                        androidx.compose.material3.HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), thickness = 0.5.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Always Shuffle", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text("Always shuffle playlists when clicking play", fontSize = 12.sp, color = Color.Gray)
                            }
                            com.arcadesoftware.musix.components.LiquidToggle(
                                selected = { alwaysShuffle },
                                onSelect = { enabled ->
                                    alwaysShuffle = enabled
                                    sharedPrefs.edit().putBoolean("always_shuffle", enabled).apply()
                                    com.arcadesoftware.musix.db.FirestoreSyncManager.schedulePushAllLocalDataToFirestore(context)
                                },
                                backdrop = mainBackdrop
                            )
                        }

                        androidx.compose.material3.HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), thickness = 0.5.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto Download Playlists", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text("Automatically download all songs in your custom playlists", fontSize = 12.sp, color = Color.Gray)
                            }
                            com.arcadesoftware.musix.components.LiquidToggle(
                                selected = { autoDownloadPlaylists },
                                onSelect = { enabled ->
                                    autoDownloadPlaylists = enabled
                                    sharedPrefs.edit().putBoolean("auto_download_playlists", enabled).apply()
                                    com.arcadesoftware.musix.db.FirestoreSyncManager.schedulePushAllLocalDataToFirestore(context)
                                },
                                backdrop = mainBackdrop
                            )
                        }

                        androidx.compose.material3.HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f), thickness = 0.5.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Download on Wi-Fi only", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Text("Restrict downloads to Wi-Fi connections only", fontSize = 12.sp, color = Color.Gray)
                            }
                            com.arcadesoftware.musix.components.LiquidToggle(
                                selected = { wifiOnlyDownload },
                                onSelect = { enabled ->
                                    wifiOnlyDownload = enabled
                                    sharedPrefs.edit().putBoolean("wifi_only_download", enabled).apply()
                                    com.arcadesoftware.musix.db.FirestoreSyncManager.schedulePushAllLocalDataToFirestore(context)
                                },
                                backdrop = mainBackdrop
                            )
                        }
                    }
""" # I removed the final `\n                }` from `new_content`!

text = text[:start_idx] + new_content + "\n" + text[end_idx:]

with open("app/src/main/java/com/arcadesoftware/musix/MainActivity.kt", "w") as f:
    f.write(text)

# Don't forget to add the dialog variables
lines = text.split('\n')
target_state = '            var settingsScreen by remember { mutableStateOf("Main") }'
for i, line in enumerate(lines):
    if target_state in line:
        if 'showDeleteConfirmDialog' not in lines[i+1]:
            lines.insert(i + 1, '            var showDeleteConfirmDialog by remember { mutableStateOf(false) }')
            lines.insert(i + 2, '            var deleteConfirmText by remember { mutableStateOf("") }')
        break

with open("app/src/main/java/com/arcadesoftware/musix/MainActivity.kt", "w") as f:
    f.write('\n'.join(lines))

