import re

# 1. Update AndroidManifest.xml
with open("app/src/main/AndroidManifest.xml", "r") as f:
    manifest = f.read()

aliases = """        <activity-alias
            android:name=".MainActivityDefault"
            android:enabled="true"
            android:exported="true"
            android:icon="@mipmap/ic_launcher"
            android:roundIcon="@mipmap/ic_launcher_round"
            android:targetActivity=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>

        <activity-alias
            android:name=".MainActivityBlueGradient"
            android:enabled="false"
            android:exported="true"
            android:icon="@mipmap/ic_launcher_bluegradient"
            android:roundIcon="@mipmap/ic_launcher_bluegradient"
            android:targetActivity=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>

        <activity-alias
            android:name=".MainActivityComic1"
            android:enabled="false"
            android:exported="true"
            android:icon="@mipmap/ic_launcher_comic1"
            android:roundIcon="@mipmap/ic_launcher_comic1"
            android:targetActivity=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>

        <activity-alias
            android:name=".MainActivityGradient2"
            android:enabled="false"
            android:exported="true"
            android:icon="@mipmap/ic_launcher_gradient2"
            android:roundIcon="@mipmap/ic_launcher_gradient2"
            android:targetActivity=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>

        <activity-alias
            android:name=".MainActivityMini1"
            android:enabled="false"
            android:exported="true"
            android:icon="@mipmap/ic_launcher_mini1"
            android:roundIcon="@mipmap/ic_launcher_mini1"
            android:targetActivity=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>

        <activity-alias
            android:name=".MainActivityOrange"
            android:enabled="false"
            android:exported="true"
            android:icon="@mipmap/ic_launcher_orange"
            android:roundIcon="@mipmap/ic_launcher_orange"
            android:targetActivity=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>

        <activity-alias
            android:name=".MainActivitySpecial1"
            android:enabled="false"
            android:exported="true"
            android:icon="@mipmap/ic_launcher_special1"
            android:roundIcon="@mipmap/ic_launcher_special1"
            android:targetActivity=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>"""

# Replace existing aliases
start_idx = manifest.find('<activity-alias\n            android:name=".MainActivityDefault"')
end_idx = manifest.find('<activity\n            android:name=".SearchActivity"')
manifest = manifest[:start_idx] + aliases + "\n\n        " + manifest[end_idx:]

with open("app/src/main/AndroidManifest.xml", "w") as f:
    f.write(manifest)


# 2. Update MainActivity.kt (AppIconManager and UI)
with open("app/src/main/java/com/arcadesoftware/musix/MainActivity.kt", "r") as f:
    main_text = f.read()

# Update AppIconManager
old_manager = """        val defaultAlias = android.content.ComponentName(context, "$packageName.MainActivityDefault")
        val darkAlias = android.content.ComponentName(context, "$packageName.MainActivityDark")
        val lightAlias = android.content.ComponentName(context, "$packageName.MainActivityLight")

        val components = listOf(defaultAlias, darkAlias, lightAlias)"""

new_manager = """        val defaultAlias = android.content.ComponentName(context, "$packageName.MainActivityDefault")
        val blueAlias = android.content.ComponentName(context, "$packageName.MainActivityBlueGradient")
        val comicAlias = android.content.ComponentName(context, "$packageName.MainActivityComic1")
        val grad2Alias = android.content.ComponentName(context, "$packageName.MainActivityGradient2")
        val miniAlias = android.content.ComponentName(context, "$packageName.MainActivityMini1")
        val orangeAlias = android.content.ComponentName(context, "$packageName.MainActivityOrange")
        val specialAlias = android.content.ComponentName(context, "$packageName.MainActivitySpecial1")

        val components = listOf(defaultAlias, blueAlias, comicAlias, grad2Alias, miniAlias, orangeAlias, specialAlias)"""

main_text = main_text.replace(old_manager, new_manager)


# Update UI
old_ui = """                    // App Icon Selection
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
                    }"""

new_ui = """                    // App Icon Selection
                    val appIconPref = sharedPrefs.getInt("app_icon_preference", 0)
                    Text("App Icon", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp, start = 8.dp))
                    
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cardBg).padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val icons = listOf(
                            R.mipmap.ic_launcher,
                            R.mipmap.ic_launcher_bluegradient,
                            R.mipmap.ic_launcher_comic1,
                            R.mipmap.ic_launcher_gradient2,
                            R.mipmap.ic_launcher_mini1,
                            R.mipmap.ic_launcher_orange,
                            R.mipmap.ic_launcher_special1
                        )
                        val iconNames = listOf("Default", "Blue", "Comic", "Grad 2", "Mini", "Orange", "Special")
                        
                        items(icons.size) { index ->
                            val iconRes = icons[index]
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
                    }"""

main_text = main_text.replace(old_ui, new_ui)

with open("app/src/main/java/com/arcadesoftware/musix/MainActivity.kt", "w") as f:
    f.write(main_text)

