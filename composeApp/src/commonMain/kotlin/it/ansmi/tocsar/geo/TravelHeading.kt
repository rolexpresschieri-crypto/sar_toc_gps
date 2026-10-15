package it.ansmi.tocsar.geo

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Sotto questa velocità la rotta GPS è rumorosa: si usa la bussola. ~4 km/h. */
const val CourseMinSpeedMps = 1.1f

/** Dopo l'ultima rotta valida non si torna subito alla bussola (semaforo, coda). */
val GpsCourseHold: Duration = 20.seconds

/** Ultima rotta GPS accettata, con l'istante in cui è arrivata. */
data class TravelCourse(
    val degrees: Double,
    val mark: TimeMark,
)

fun TravelCourse?.acceptFix(fix: GeoFix): TravelCourse? {
    if (!fix.isGpsProvider) return this
    val course = fix.courseDeg ?: return this
    val speed = fix.speedMps ?: 0f
    if (speed < CourseMinSpeedMps) return this
    return TravelCourse(
        degrees = smoothHeading(this?.degrees, course.toDouble(), 0.55),
        mark = TimeSource.Monotonic.markNow(),
    )
}

fun TravelCourse?.isFresh(): Boolean {
    if (this == null) return false
    return mark.elapsedNow() < GpsCourseHold
}

fun smoothHeading(previous: Double?, sample: Double, alpha: Double): Double {
    if (previous == null) return (sample % 360.0 + 360.0) % 360.0
    var delta = (sample - previous) % 360.0
    if (delta > 180.0) delta -= 360.0
    if (delta < -180.0) delta += 360.0
    return (previous + delta * alpha).mod(360.0)
}
