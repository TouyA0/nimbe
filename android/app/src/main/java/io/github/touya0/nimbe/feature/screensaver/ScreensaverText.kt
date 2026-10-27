package io.github.touya0.nimbe.feature.screensaver

import io.github.touya0.nimbe.data.homeassistant.HaEntity
import io.github.touya0.nimbe.data.homeassistant.SensorCatalog
import io.github.touya0.nimbe.data.homeassistant.SensorKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Textes et calculs de l'écran de veille, sans Android : testés sur le PC. */
object ScreensaverText {

    /** États météo de Home Assistant (entités weather.*), en français. */
    private val CONDITIONS = mapOf(
        "clear-night" to "Nuit claire",
        "cloudy" to "Nuageux",
        "exceptional" to "Temps exceptionnel",
        "fog" to "Brouillard",
        "hail" to "Grêle",
        "lightning" to "Orage",
        "lightning-rainy" to "Orage et pluie",
        "partlycloudy" to "Éclaircies",
        "pouring" to "Averses",
        "rainy" to "Pluie",
        "snowy" to "Neige",
        "snowy-rainy" to "Neige fondue",
        "sunny" to "Ensoleillé",
        "windy" to "Venteux",
        "windy-variant" to "Venteux et nuageux",
    )

    /** « 12 °C · Éclaircies », ou null si la météo n'est pas connue (jamais une météo inventée). */
    fun weather(entity: HaEntity?): String? {
        if (entity == null || entity.unknown) return null
        val condition = CONDITIONS[entity.state]
        val temperature = entity.temperature?.let { "${it.roundToInt()} °C" }
        return listOfNotNull(temperature, condition).joinToString(" · ").ifEmpty { null }
    }

    /** « Processeur 6 % · 57 °C · Disque 50 % » : les mesures du Pi en une ligne discrète. */
    fun stats(measures: List<HaEntity>): String =
        measures.filterNot { it.unknown }.mapNotNull { entity ->
            when (SensorCatalog.kindOf(entity)) {
                SensorKind.Processor -> "Processeur ${entity.displayValue}"
                SensorKind.Temperature -> entity.displayValue
                SensorKind.Memory -> "Mémoire ${entity.displayValue}"
                SensorKind.Disk -> "Disque ${entity.displayValue}"
                else -> null
            }
        }.joinToString(" · ")

    /**
     * Décalage anti-marquage pour la minute [minute] : tout le contenu fait lentement le tour d'un cercle
     * de rayon [radius] (un tour par heure), pour qu'aucun pixel ne reste allumé au même endroit.
     */
    fun burnInOffset(minute: Long, radius: Float): Pair<Float, Float> {
        val angle = (minute % 60) / 60.0 * 2 * PI
        return (cos(angle) * radius).toFloat() to (sin(angle) * radius).toFloat()
    }
}
