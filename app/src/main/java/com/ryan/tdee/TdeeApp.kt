package com.ryan.tdee

import android.app.Application
import com.ryan.tdee.data.EntryDatabase
import com.ryan.tdee.data.EntryRepository
import com.ryan.tdee.data.SettingsRepository

class TdeeApp : Application() {
    val entryRepository by lazy { EntryRepository(EntryDatabase.build(this).entryDao()) }
    val settingsRepository by lazy { SettingsRepository(this) }
}
