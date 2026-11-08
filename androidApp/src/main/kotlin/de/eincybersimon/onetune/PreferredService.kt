package de.eincybersimon.onetune

import android.content.Context
import androidx.core.content.edit

class PreferredService(context: Context) {
    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var service: StreamingService?
        get() = StreamingService.fromId(preferences.getString(KEY, null))
        set(value) = preferences.edit { putString(KEY, value?.id) }

    private companion object {
        const val KEY = "preferred_service"
    }
}
