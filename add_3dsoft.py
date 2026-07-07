import os

manifest_path = "app/src/main/AndroidManifest.xml"
with open(manifest_path, "r") as f:
    manifest = f.read()

soft_alias = """
        <activity-alias
            android:name=".MainActivitySketch"
            android:enabled="false"
            android:exported="true"
            android:icon="@mipmap/ic_launcher_sketch"
            android:roundIcon="@mipmap/ic_launcher_sketch"
            android:targetActivity=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>

        <activity-alias
            android:name=".MainActivity3dsoft"
            android:enabled="false"
            android:exported="true"
            android:icon="@mipmap/ic_launcher_3dsoft"
            android:roundIcon="@mipmap/ic_launcher_3dsoft"
            android:targetActivity=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>"""

manifest = manifest.replace("""
        <activity-alias
            android:name=".MainActivitySketch"
            android:enabled="false"
            android:exported="true"
            android:icon="@mipmap/ic_launcher_sketch"
            android:roundIcon="@mipmap/ic_launcher_sketch"
            android:targetActivity=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>""", soft_alias)

with open(manifest_path, "w") as f:
    f.write(manifest)


main_path = "app/src/main/java/com/arcadesoftware/musix/MainActivity.kt"
with open(main_path, "r") as f:
    main_text = f.read()

# Update AppIconManager
old_manager = """        val specialAlias = android.content.ComponentName(context, "$packageName.MainActivitySpecial1")
        val sketchAlias = android.content.ComponentName(context, "$packageName.MainActivitySketch")

        val components = listOf(defaultAlias, blueAlias, comicAlias, grad2Alias, miniAlias, orangeAlias, specialAlias, sketchAlias)"""

new_manager = """        val specialAlias = android.content.ComponentName(context, "$packageName.MainActivitySpecial1")
        val sketchAlias = android.content.ComponentName(context, "$packageName.MainActivitySketch")
        val softAlias = android.content.ComponentName(context, "$packageName.MainActivity3dsoft")

        val components = listOf(defaultAlias, blueAlias, comicAlias, grad2Alias, miniAlias, orangeAlias, specialAlias, sketchAlias, softAlias)"""
main_text = main_text.replace(old_manager, new_manager)


# Update UI
old_icons = """                        val icons = listOf(
                            R.mipmap.ic_launcher,
                            R.mipmap.ic_launcher_bluegradient,
                            R.mipmap.ic_launcher_comic1,
                            R.mipmap.ic_launcher_gradient2,
                            R.mipmap.ic_launcher_mini1,
                            R.mipmap.ic_launcher_orange,
                            R.mipmap.ic_launcher_special1,
                            R.mipmap.ic_launcher_sketch
                        )
                        val iconNames = listOf("Default", "Blue", "Comic", "Grad 2", "Mini", "Orange", "Special", "Sketch")"""

new_icons = """                        val icons = listOf(
                            R.mipmap.ic_launcher,
                            R.mipmap.ic_launcher_bluegradient,
                            R.mipmap.ic_launcher_comic1,
                            R.mipmap.ic_launcher_gradient2,
                            R.mipmap.ic_launcher_mini1,
                            R.mipmap.ic_launcher_orange,
                            R.mipmap.ic_launcher_special1,
                            R.mipmap.ic_launcher_sketch,
                            R.mipmap.ic_launcher_3dsoft
                        )
                        val iconNames = listOf("Default", "Blue", "Comic", "Grad 2", "Mini", "Orange", "Special", "Sketch", "3D Soft")"""

main_text = main_text.replace(old_icons, new_icons)

with open(main_path, "w") as f:
    f.write(main_text)

