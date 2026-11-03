package se.mybus

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK }

// SYSTEM picks Swedish when the phone is in Swedish, otherwise English.
enum class Language(val label: String?) {
    SYSTEM(null),
    SWEDISH("SWE"),
    ENGLISH("ENG"),
}

// Theme and language choices, kept in SharedPreferences.
object Settings {
    private const val PREFS = "settings"
    private const val THEME = "theme"
    private const val LANGUAGE = "language"

    fun loadTheme(context: Context): ThemeMode = load(context, THEME, ThemeMode.SYSTEM)
    fun saveTheme(context: Context, mode: ThemeMode) = save(context, THEME, mode)

    fun loadLanguage(context: Context): Language = load(context, LANGUAGE, Language.SYSTEM)
    fun saveLanguage(context: Context, language: Language) = save(context, LANGUAGE, language)

    private inline fun <reified T : Enum<T>> load(context: Context, key: String, default: T): T =
        prefs(context).getString(key, null)
            ?.let { name -> enumValues<T>().firstOrNull { it.name == name } }
            ?: default

    private fun save(context: Context, key: String, value: Enum<*>) {
        prefs(context).edit().putString(key, value.name).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
