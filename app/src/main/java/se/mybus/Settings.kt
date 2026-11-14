package se.mybus

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK }

// SYSTEM picks Swedish when the phone is in Swedish, otherwise English.
enum class Language(val label: String?) {
    SYSTEM(null),
    SWEDISH("SWE"),
    ENGLISH("ENG"),
}

// Theme, language and alert choices, kept in SharedPreferences.
object Settings {
    private const val PREFS = "settings"
    private const val THEME = "theme"
    private const val LANGUAGE = "language"
    private const val ALERTS = "disruption_alerts"
    private const val ALERT_FROM = "alerts_from_hour"
    private const val ALERT_TO = "alerts_to_hour"
    private const val ALERT_WEEKDAYS = "alerts_weekdays_only"
    private const val ALERT_MINOR = "alerts_include_minor"

    fun loadTheme(context: Context): ThemeMode = load(context, THEME, ThemeMode.SYSTEM)
    fun saveTheme(context: Context, mode: ThemeMode) = save(context, THEME, mode)

    fun loadLanguage(context: Context): Language = load(context, LANGUAGE, Language.SYSTEM)
    fun saveLanguage(context: Context, language: Language) = save(context, LANGUAGE, language)

    // Off until turned on in the menu: notifications are opt-in.
    fun loadAlerts(context: Context): Boolean = prefs(context).getBoolean(ALERTS, false)
    fun saveAlerts(context: Context, on: Boolean) = prefs(context).edit().putBoolean(ALERTS, on).apply()

    // When alerts may notify and whether minor notices count; all day, every day, important only
    // until changed.
    fun loadAlertOptions(context: Context): AlertOptions {
        val p = prefs(context)
        return AlertOptions(
            fromHour = p.getInt(ALERT_FROM, 0),
            toHour = p.getInt(ALERT_TO, 24),
            weekdaysOnly = p.getBoolean(ALERT_WEEKDAYS, false),
            includeMinor = p.getBoolean(ALERT_MINOR, false),
        )
    }

    fun saveAlertOptions(context: Context, options: AlertOptions) {
        prefs(context).edit()
            .putInt(ALERT_FROM, options.fromHour)
            .putInt(ALERT_TO, options.toHour)
            .putBoolean(ALERT_WEEKDAYS, options.weekdaysOnly)
            .putBoolean(ALERT_MINOR, options.includeMinor)
            .apply()
    }

    private inline fun <reified T : Enum<T>> load(context: Context, key: String, default: T): T =
        prefs(context).getString(key, null)
            ?.let { name -> enumValues<T>().firstOrNull { it.name == name } }
            ?: default

    private fun save(context: Context, key: String, value: Enum<*>) {
        prefs(context).edit().putString(key, value.name).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
