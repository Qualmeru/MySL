package se.mybus

// One cool-down for every SL request the app makes: the departures screen, the widget job and
// the disruption alerts. Without it each kept retrying on its own after SL said "Quota has been
// exceeded", which only kept the quota used up. They all run in the app's one process, so a
// shared object is enough; if the process is restarted, the cool-down starts over, which is fine.
//
// The pause starts at a minute and doubles while SL keeps refusing, up to 15 minutes, or longer
// if SL asks for that with Retry-After. The first good answer ends it.
internal object SlCooldown {
    const val FIRST_MS = 60_000L
    const val MAX_MS = 15 * 60_000L

    // Replaced in tests.
    internal var clock: () -> Long = System::currentTimeMillis

    private var until = 0L
    private var lengthMs = 0L

    // Throws without touching the network while a cool-down is on.
    @Synchronized
    fun check() {
        if (clock() < until) throw SlApiException("Waiting before asking SL again", quota = true)
    }

    @Synchronized
    fun refused(retryAfterMs: Long? = null) {
        lengthMs = if (lengthMs == 0L) FIRST_MS else minOf(lengthMs * 2, MAX_MS)
        until = clock() + maxOf(lengthMs, retryAfterMs ?: 0L)
    }

    @Synchronized
    fun succeeded() {
        lengthMs = 0L
        until = 0L
    }

    @Synchronized
    fun remainingMs(): Long = (until - clock()).coerceAtLeast(0L)

    @Synchronized
    internal fun reset() = succeeded()
}
