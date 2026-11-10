package app.flare.auto

internal class SolarState(minute: Int, rise: Int, set: Int, tomorrowRise: Int) {
    @JvmField val day = rise >= 0 && set > rise && minute >= rise && minute < set
    @JvmField val next = if (day) set else if (set >= 0 && minute >= set) tomorrowRise else rise
    @JvmField val progress = if (day) (minute - rise).toFloat() / (set - rise) else 0f
}
