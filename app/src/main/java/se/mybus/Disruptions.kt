package se.mybus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDateTime

// When alerts may notify, and which notices count. fromHour..toHour is the daily window (0..24 =
// all day; 22..6 crosses midnight); outside it, or at the weekend with weekdaysOnly, nothing is
// announced and nothing is marked as seen, so what's still going on is announced once it opens.
data class AlertOptions(
    val fromHour: Int = 0,
    val toHour: Int = 24,
    val weekdaysOnly: Boolean = false,
    val includeMinor: Boolean = false,
) {
    val allDay: Boolean get() = (toHour - fromHour) % 24 == 0

    fun allows(time: LocalDateTime): Boolean {
        if (weekdaysOnly && time.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) return false
        if (allDay) return true
        val h = time.hour
        return if (fromHour < toHour) h in fromHour until toHour else h >= fromHour || h < toHour
    }
}

// Something worth a notification at one stop: SL's disruption text, or a cancelled departure.
// key identifies it across fetches, so the same notice is only announced once.
data class Notice(val key: String, val text: String)

// Notices among the departures this stop shows (its filters apply: a hidden line shouldn't ping).
// SL repeats a disruption message on every affected departure, so each message counts once.
// SL's own "Inställd" notice is left out (the cancellation notice below says more), and so is
// general information unless includeMinor.
internal fun noticesFor(stop: Stop, departures: List<Departure>, s: Strings, includeMinor: Boolean = false): List<Notice> {
    if (!stop.alerts) return emptyList()
    val shown = stop.visible(departures)
    val messages = shown.flatMap { it.deviations }
        .filter { !it.isCancellation && (includeMinor || !it.isMinor) }
        .map { it.message }.distinct()
        .map { Notice("${stop.siteId}|dev|$it", it) }
    val cancellations = shown.filter { it.cancelled }.map {
        Notice(
            "${stop.siteId}|cx|${it.line}|${it.destination}|${it.expectedAt ?: it.expected}",
            s.lineCancelled(it.line, it.destination, it.expected),
        )
    }
    return messages + cancellations
}

// Which notices are new, and the updated "already announced" record (key -> last seen, millis).
// A notice stays remembered for a while after it's last seen, so one that drops out of the next
// hour's departures and comes back (a disruption on a line that runs every 20 minutes) isn't
// announced again. Remembered ones that haven't been seen for that long are forgotten.
internal fun newNotices(
    current: List<Notice>,
    seen: Map<String, Long>,
    now: Long,
    rememberMs: Long = 12 * 60 * 60_000L,
): Pair<List<Notice>, Map<String, Long>> {
    val remembered = seen.filterValues { now - it < rememberMs }
    val fresh = current.filter { it.key !in remembered }.distinctBy { it.key }
    return fresh to (remembered + current.associate { it.key to now })
}

object DisruptionAlerts {
    private const val CHANNEL = "disruptions"
    private const val PREFS = "disruption_alerts"
    private const val SEEN = "seen"

    // Called after a background fetch with each stop's departures (stops that failed are left out,
    // so a failed fetch neither announces nor forgets anything).
    fun check(context: Context, fetched: Map<Stop, List<Departure>>) {
        if (!Settings.loadAlerts(context)) return
        val options = Settings.loadAlertOptions(context)
        if (!options.allows(LocalDateTime.now())) return
        val s = stringsFor(Settings.loadLanguage(context))
        val now = System.currentTimeMillis()
        var seen = loadSeen(context)
        for ((stop, departures) in fetched) {
            val (fresh, updated) = newNotices(noticesFor(stop, departures, s, options.includeMinor), seen, now)
            seen = updated
            if (fresh.isNotEmpty()) notify(context, s, stop, fresh)
        }
        saveSeen(context, seen)
    }

    // Turning alerts back on announces what's going on right now, rather than what was seen
    // before they were turned off.
    fun forgetSeen(context: Context) = saveSeen(context, emptyMap())

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun notify(context: Context, s: Strings, stop: Stop, notices: List<Notice>) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, s.disruptionAlerts, NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            context,
            stop.siteId,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val body = notices.joinToString("\n") { it.text }
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(s.disruptionTitle(stop.name))
            .setContentText(notices.first().text)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // One notification per stop, replaced by the next one for the same stop.
        runCatching { manager.notify(stop.siteId, notification) }
    }

    private fun loadSeen(context: Context): Map<String, Long> {
        val json = prefs(context).getString(SEEN, null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { o.getLong(it) }
        }.getOrDefault(emptyMap())
    }

    private fun saveSeen(context: Context, seen: Map<String, Long>) {
        val o = JSONObject()
        seen.forEach { (k, v) -> o.put(k, v) }
        prefs(context).edit().putString(SEEN, o.toString()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
