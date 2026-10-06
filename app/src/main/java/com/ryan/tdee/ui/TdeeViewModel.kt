package com.ryan.tdee.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ryan.tdee.TdeeApp
import com.ryan.tdee.core.Analysis
import com.ryan.tdee.core.CsvFormat
import com.ryan.tdee.core.ImportResult
import com.ryan.tdee.core.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class TdeeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TdeeApp
    private val entries = app.entryRepository
    private val settingsStore = app.settingsRepository

    val settings: StateFlow<Settings?> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val analysis: StateFlow<Analysis?> =
        combine(entries.entries, settingsStore.settings) { list, s -> Analysis(list, s) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val today = MutableStateFlow(LocalDate.now())

    /** Null means "follow today", so the home screen rolls over at midnight. */
    private val pickedDate = MutableStateFlow<LocalDate?>(null)

    val selectedDate: StateFlow<LocalDate> =
        combine(today, pickedDate) { t, picked -> picked?.takeIf { it < t } ?: t }
            .stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())

    fun refreshToday() {
        today.value = LocalDate.now()
    }

    fun selectDate(date: LocalDate) {
        pickedDate.value = if (date >= today.value) null else date
    }

    fun shiftDate(days: Long) = selectDate(selectedDate.value.plusDays(days))

    fun save(date: LocalDate, weightKg: Double?, calories: Double?) {
        viewModelScope.launch { entries.save(date, weightKg, calories) }
    }

    fun delete(date: LocalDate) {
        viewModelScope.launch { entries.delete(date) }
    }

    fun updateSettings(transform: (Settings) -> Settings) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    suspend fun exportCsv(): String {
        val a = analysis.filterNotNull().first()
        return withContext(Dispatchers.Default) {
            CsvFormat.export(a.entries, a.tdeeByDate, a.settings.weightUnit, a.settings.energyUnit)
        }
    }

    suspend fun importCsv(text: String): ImportResult {
        val result = withContext(Dispatchers.Default) { CsvFormat.parse(text) }
        entries.import(result.entries)
        return result
    }

    fun clearData() {
        viewModelScope.launch { entries.clear() }
    }
}
