package com.ryan.tdee.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ryan.tdee.core.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.dataStore

    val settings: Flow<Settings> = store.data.map { it.toSettings() }

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    private object Keys {
        val goal = doublePreferencesKey("goal_kg_per_week")
        val days = intPreferencesKey("days")
        val startDate = longPreferencesKey("start_epoch_day")
        val algorithm = stringPreferencesKey("algorithm")
        val defaultCalories = stringPreferencesKey("default_calories")
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val weightUnit = stringPreferencesKey("weight_unit")
        val energyUnit = stringPreferencesKey("energy_unit")
        val weightSmoothing = intPreferencesKey("weight_smoothing")
        val calorieSmoothing = intPreferencesKey("calorie_smoothing")
        val tdeeSmoothing = intPreferencesKey("tdee_smoothing")
        val weightMode = stringPreferencesKey("weight_mode")
        val calorieMode = stringPreferencesKey("calorie_mode")
        val tdeeMode = stringPreferencesKey("tdee_mode")
        val graphRange = stringPreferencesKey("graph_range")
    }

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            goalKgPerWeek = this[Keys.goal] ?: d.goalKgPerWeek,
            days = this[Keys.days] ?: d.days,
            startDate = this[Keys.startDate]?.let(LocalDate::ofEpochDay),
            algorithm = this[Keys.algorithm].toEnum(d.algorithm),
            defaultCalories = this[Keys.defaultCalories].toEnum(d.defaultCalories),
            themeMode = this[Keys.theme].toEnum(d.themeMode),
            dynamicColor = this[Keys.dynamicColor] ?: d.dynamicColor,
            weightUnit = this[Keys.weightUnit].toEnum(d.weightUnit),
            energyUnit = this[Keys.energyUnit].toEnum(d.energyUnit),
            weightSmoothing = this[Keys.weightSmoothing] ?: d.weightSmoothing,
            calorieSmoothing = this[Keys.calorieSmoothing] ?: d.calorieSmoothing,
            tdeeSmoothing = this[Keys.tdeeSmoothing] ?: d.tdeeSmoothing,
            weightMode = this[Keys.weightMode].toEnum(d.weightMode),
            calorieMode = this[Keys.calorieMode].toEnum(d.calorieMode),
            tdeeMode = this[Keys.tdeeMode].toEnum(d.tdeeMode),
            graphRange = this[Keys.graphRange].toEnum(d.graphRange),
        )
    }

    private fun MutablePreferences.write(s: Settings) {
        this[Keys.goal] = s.goalKgPerWeek
        this[Keys.days] = s.days
        if (s.startDate != null) this[Keys.startDate] = s.startDate.toEpochDay() else remove(Keys.startDate)
        this[Keys.algorithm] = s.algorithm.name
        this[Keys.defaultCalories] = s.defaultCalories.name
        this[Keys.theme] = s.themeMode.name
        this[Keys.dynamicColor] = s.dynamicColor
        this[Keys.weightUnit] = s.weightUnit.name
        this[Keys.energyUnit] = s.energyUnit.name
        this[Keys.weightSmoothing] = s.weightSmoothing
        this[Keys.calorieSmoothing] = s.calorieSmoothing
        this[Keys.tdeeSmoothing] = s.tdeeSmoothing
        this[Keys.weightMode] = s.weightMode.name
        this[Keys.calorieMode] = s.calorieMode.name
        this[Keys.tdeeMode] = s.tdeeMode.name
        this[Keys.graphRange] = s.graphRange.name
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
        this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
}
