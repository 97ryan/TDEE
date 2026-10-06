package com.ryan.tdee.data

import com.ryan.tdee.core.Entry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class EntryRepository(private val dao: EntryDao) {

    val entries: Flow<List<Entry>> = dao.observeAll().map { rows -> rows.map { it.toEntry() } }

    /** Saves a day's log; a day with neither weight nor calories is removed. */
    suspend fun save(date: LocalDate, weightKg: Double?, calories: Double?) {
        if (weightKg == null && calories == null) {
            dao.delete(date.toEpochDay())
        } else {
            dao.upsert(EntryEntity(date.toEpochDay(), weightKg, calories))
        }
    }

    suspend fun delete(date: LocalDate) = dao.delete(date.toEpochDay())

    /** Adds imported entries, replacing any existing entries on the same dates. */
    suspend fun import(entries: List<Entry>) =
        dao.upsertAll(entries.map { EntryEntity(it.date.toEpochDay(), it.weightKg, it.calories) })

    suspend fun clear() = dao.clear()

    private fun EntryEntity.toEntry() = Entry(LocalDate.ofEpochDay(epochDay), weightKg, calories)
}
