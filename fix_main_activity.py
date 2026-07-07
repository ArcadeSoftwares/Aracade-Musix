import re

with open("app/src/main/java/com/arcadesoftware/musix/MainActivity.kt", "r") as f:
    content = f.read()

# Chunk 1
c1_target = """            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Account",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp),
                    fontWeight = FontWeight.Bold
                )"""

c1_replacement = """            var settingsScreen by remember { mutableStateOf("Main") }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    .padding(16.dp)
            ) {
                if (settingsScreen == "Main") {
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Text(
                            text = "Account",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }"""

content = content.replace(c1_target, c1_replacement)

# Chunk 2
c2_target = """                    Spacer(modifier = Modifier.height(16.dp))

                    // Cloud Sync Features Card
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(cardBg)
                                .then(if (currentUser == null) Modifier.graphicsLayer { alpha = 0.5f } else Modifier)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {"""

c2_replacement = """                    Spacer(modifier = Modifier.height(16.dp))

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
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { triggerGoogleSignIn() },
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
                            text = "Cloud Sync Features",
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
                    ) {"""

content = content.replace(c2_target, c2_replacement)

# Chunk 3
c3_target = """                                backdrop = mainBackdrop
                            )
                        }
                    }
                    
                    if (currentUser == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    triggerGoogleSignIn()
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // App Preferences Card
                    Column("""

c3_replacement = """                                backdrop = mainBackdrop
                            )
                        }
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

                    // App Preferences Card
                    Column("""

content = content.replace(c3_target, c3_replacement)

# Chunk 4
c4_target = """                                backdrop = mainBackdrop
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            showAccountSheet = false
                            // Push final data snapshot before signing out, then sign out
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
                        Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Sign Out")
                    }"""

c4_replacement = """                                backdrop = mainBackdrop
                            )
                        }
                    }
                }"""

content = content.replace(c4_target, c4_replacement)

with open("app/src/main/java/com/arcadesoftware/musix/MainActivity.kt", "w") as f:
    f.write(content)

print("Done")
