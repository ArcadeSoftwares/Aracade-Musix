import re

# 1. Update Theme.kt to animate colors
with open("app/src/main/java/com/arcadesoftware/musix/ui/theme/Theme.kt", "r") as f:
    theme_text = f.read()

animated_theme = """
    val primary by androidx.compose.animation.animateColorAsState(targetColorScheme.primary, androidx.compose.animation.core.tween(500))
    val secondary by androidx.compose.animation.animateColorAsState(targetColorScheme.secondary, androidx.compose.animation.core.tween(500))
    val tertiary by androidx.compose.animation.animateColorAsState(targetColorScheme.tertiary, androidx.compose.animation.core.tween(500))
    val background by androidx.compose.animation.animateColorAsState(targetColorScheme.background, androidx.compose.animation.core.tween(500))
    val surface by androidx.compose.animation.animateColorAsState(targetColorScheme.surface, androidx.compose.animation.core.tween(500))
    val onPrimary by androidx.compose.animation.animateColorAsState(targetColorScheme.onPrimary, androidx.compose.animation.core.tween(500))
    val onSecondary by androidx.compose.animation.animateColorAsState(targetColorScheme.onSecondary, androidx.compose.animation.core.tween(500))
    val onTertiary by androidx.compose.animation.animateColorAsState(targetColorScheme.onTertiary, androidx.compose.animation.core.tween(500))
    val onBackground by androidx.compose.animation.animateColorAsState(targetColorScheme.onBackground, androidx.compose.animation.core.tween(500))
    val onSurface by androidx.compose.animation.animateColorAsState(targetColorScheme.onSurface, androidx.compose.animation.core.tween(500))
    val primaryContainer by androidx.compose.animation.animateColorAsState(targetColorScheme.primaryContainer, androidx.compose.animation.core.tween(500))
    val onPrimaryContainer by androidx.compose.animation.animateColorAsState(targetColorScheme.onPrimaryContainer, androidx.compose.animation.core.tween(500))
    val secondaryContainer by androidx.compose.animation.animateColorAsState(targetColorScheme.secondaryContainer, androidx.compose.animation.core.tween(500))
    val onSecondaryContainer by androidx.compose.animation.animateColorAsState(targetColorScheme.onSecondaryContainer, androidx.compose.animation.core.tween(500))
    val surfaceVariant by androidx.compose.animation.animateColorAsState(targetColorScheme.surfaceVariant, androidx.compose.animation.core.tween(500))
    val onSurfaceVariant by androidx.compose.animation.animateColorAsState(targetColorScheme.onSurfaceVariant, androidx.compose.animation.core.tween(500))
    val outline by androidx.compose.animation.animateColorAsState(targetColorScheme.outline, androidx.compose.animation.core.tween(500))
    val error by androidx.compose.animation.animateColorAsState(targetColorScheme.error, androidx.compose.animation.core.tween(500))
    val onError by androidx.compose.animation.animateColorAsState(targetColorScheme.onError, androidx.compose.animation.core.tween(500))
    val errorContainer by androidx.compose.animation.animateColorAsState(targetColorScheme.errorContainer, androidx.compose.animation.core.tween(500))
    val onErrorContainer by androidx.compose.animation.animateColorAsState(targetColorScheme.onErrorContainer, androidx.compose.animation.core.tween(500))
    val inversePrimary by androidx.compose.animation.animateColorAsState(targetColorScheme.inversePrimary, androidx.compose.animation.core.tween(500))
    val inverseSurface by androidx.compose.animation.animateColorAsState(targetColorScheme.inverseSurface, androidx.compose.animation.core.tween(500))
    val inverseOnSurface by androidx.compose.animation.animateColorAsState(targetColorScheme.inverseOnSurface, androidx.compose.animation.core.tween(500))
    val surfaceTint by androidx.compose.animation.animateColorAsState(targetColorScheme.surfaceTint, androidx.compose.animation.core.tween(500))
    val outlineVariant by androidx.compose.animation.animateColorAsState(targetColorScheme.outlineVariant, androidx.compose.animation.core.tween(500))
    val scrim by androidx.compose.animation.animateColorAsState(targetColorScheme.scrim, androidx.compose.animation.core.tween(500))

    val colorScheme = androidx.compose.material3.ColorScheme(
        primary = primary,
        secondary = secondary,
        tertiary = tertiary,
        background = background,
        surface = surface,
        onPrimary = onPrimary,
        onSecondary = onSecondary,
        onTertiary = onTertiary,
        onBackground = onBackground,
        onSurface = onSurface,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        inversePrimary = inversePrimary,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        surfaceTint = surfaceTint,
        outlineVariant = outlineVariant,
        scrim = scrim
    )
"""

theme_text = theme_text.replace("val colorScheme = when", "val targetColorScheme = when")
theme_text = theme_text.replace("MaterialTheme(\n        colorScheme = colorScheme,", animated_theme + "\n    MaterialTheme(\n        colorScheme = colorScheme,")
with open("app/src/main/java/com/arcadesoftware/musix/ui/theme/Theme.kt", "w") as f:
    f.write(theme_text)

# 2. Add AppIconManager to MainActivity
with open("app/src/main/java/com/arcadesoftware/musix/MainActivity.kt", "r") as f:
    main_text = f.read()

icon_manager = """
object AppIconManager {
    fun changeAppIcon(context: android.content.Context, iconIndex: Int) {
        val pm = context.packageManager
        val packageName = context.packageName

        val defaultAlias = android.content.ComponentName(context, "$packageName.MainActivityDefault")
        val darkAlias = android.content.ComponentName(context, "$packageName.MainActivityDark")
        val lightAlias = android.content.ComponentName(context, "$packageName.MainActivityLight")

        val components = listOf(defaultAlias, darkAlias, lightAlias)
        val enableComponent = components[iconIndex]
        
        components.forEach {
            pm.setComponentEnabledSetting(
                it,
                if (it == enableComponent) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        }
    }
}
"""
# Insert before MainActivity class
main_text = main_text.replace("class MainActivity : ComponentActivity() {", icon_manager + "\nclass MainActivity : ComponentActivity() {")

# 3. Hoist Theme state in MainActivity
set_content_pattern = "        setContent {\n            MusixTheme {\n                MainScreen()\n            }\n        }"
set_content_replacement = """        val sharedPrefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        setContent {
            var themePref by remember { mutableStateOf(sharedPrefs.getInt("theme_preference", 0)) }
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (themePref) {
                1 -> false
                2 -> true
                else -> isSystemDark
            }
            
            // Allow MainScreen to update themePref
            androidx.compose.runtime.CompositionLocalProvider(
                LocalThemePreference provides themePref,
                LocalThemePreferenceSetter provides { newPref: Int ->
                    themePref = newPref
                    sharedPrefs.edit().putInt("theme_preference", newPref).apply()
                }
            ) {
                MusixTheme(darkTheme = darkTheme) {
                    MainScreen()
                }
            }
        }"""
main_text = main_text.replace(set_content_pattern, set_content_replacement)

# Add CompositionLocals
composition_locals = """
val LocalThemePreference = androidx.compose.runtime.compositionLocalOf<Int> { 0 }
val LocalThemePreferenceSetter = androidx.compose.runtime.compositionLocalOf<(Int) -> Unit> { {} }
"""
main_text = main_text.replace("class MainActivity : ComponentActivity() {", composition_locals + "\nclass MainActivity : ComponentActivity() {")

# 4. Update MainScreen to use LocalThemePreference
is_light_pattern = "            val isLightMode = !androidx.compose.foundation.isSystemInDarkTheme()\n            val cardBg = if (isLightMode) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)"
is_light_replacement = """            val themePref = LocalThemePreference.current
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val isLightMode = when (themePref) {
                1 -> true
                2 -> false
                else -> !isSystemDark
            }
            val targetCardBg = if (isLightMode) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
            val cardBg by androidx.compose.animation.animateColorAsState(targetCardBg, androidx.compose.animation.core.tween(500))"""
main_text = main_text.replace(is_light_pattern, is_light_replacement)

with open("app/src/main/java/com/arcadesoftware/musix/MainActivity.kt", "w") as f:
    f.write(main_text)

