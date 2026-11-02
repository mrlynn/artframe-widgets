package com.mrlynn.artframe.widget

/**
 * WMO weather codes (as Open-Meteo reports them) in words and as an emoji. Here rather than
 * in the weather source so the widget parser stays plain Kotlin, which the widget
 * marketplace's checker runs on a desktop JVM.
 */
object Wmo {
    fun describe(code: Int, isDay: Boolean = true): Pair<String, String> = when (code) {
        0 -> "Clear" to if (isDay) "☀️" else "🌙"
        1 -> "Mostly clear" to if (isDay) "🌤️" else "🌙"
        2 -> "Partly cloudy" to "⛅"
        3 -> "Overcast" to "☁️"
        45, 48 -> "Fog" to "🌫️"
        in 51..57 -> "Drizzle" to "🌦️"
        in 61..67 -> "Rain" to "🌧️"
        in 71..77 -> "Snow" to "🌨️"
        in 80..82 -> "Showers" to "🌦️"
        85, 86 -> "Snow showers" to "🌨️"
        in 95..99 -> "Thunderstorms" to "⛈️"
        else -> "—" to "🌡️"
    }
}
