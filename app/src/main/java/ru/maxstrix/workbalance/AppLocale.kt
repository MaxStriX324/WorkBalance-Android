package ru.maxstrix.workbalance

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

enum class AppLanguage(val languageTag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    RUSSIAN("ru")
}

object AppLocale {
    private const val PREFERENCES = "app-locale"
    private const val LANGUAGE_TAG = "language-tag"

    fun wrap(base: Context): ContextWrapper {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextWrapper(base)
        }
        val tag = savedLanguageTag(base)
        if (tag.isBlank()) return ContextWrapper(base)
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply {
            setLocales(LocaleList(locale))
        }
        return ContextWrapper(base.createConfigurationContext(configuration))
    }

    fun current(context: Context): AppLanguage {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)
                .applicationLocales
                .toLanguageTags()
                .substringBefore(',')
        } else {
            savedLanguageTag(context)
        }
        return AppLanguage.entries.firstOrNull {
            it.languageTag.isNotBlank() && tag.startsWith(it.languageTag, ignoreCase = true)
        } ?: AppLanguage.SYSTEM
    }

    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(LANGUAGE_TAG, language.languageTag)
            .apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(language.languageTag)
        }
        (context as? Activity)?.recreate()
    }

    private fun savedLanguageTag(context: Context): String =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(LANGUAGE_TAG, "")
            .orEmpty()
}
