package se.mybus

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val MAX_RESULTS = 50

// Names starting with the query first, then a later word starting with it, then any match.
internal fun searchSites(sites: List<Site>, query: String, max: Int = MAX_RESULTS): List<Site> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return sites
        .mapNotNull { site ->
            val name = site.name.lowercase()
            val rank = when {
                name.startsWith(q) -> 0
                name.split(' ', '-', '/').any { it.startsWith(q) } -> 1
                q in name -> 2
                else -> return@mapNotNull null
            }
            rank to site
        }
        .sortedWith(compareBy({ it.first }, { it.second.name }))
        .take(max)
        .map { it.second }
}

data class NearbySite(val site: Site, val metres: Int)

// The closest sites to a position, nearest first. Sites without coordinates are skipped, and so
// is anything further than maxMetres: past that it's not really "near me".
internal fun nearestSites(
    sites: List<Site>,
    lat: Double,
    lon: Double,
    count: Int = 15,
    maxMetres: Int = 3_000,
): List<NearbySite> =
    sites.mapNotNull { site ->
        val sLat = site.lat ?: return@mapNotNull null
        val sLon = site.lon ?: return@mapNotNull null
        NearbySite(site, distanceMetres(lat, lon, sLat, sLon).toInt())
    }
        .filter { it.metres <= maxMetres }
        .sortedWith(compareBy({ it.metres }, { it.site.name }))
        .take(count)

// Great-circle distance; plenty accurate at city scale.
internal fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}
