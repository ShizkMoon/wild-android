package app.wild.android.di

import app.wild.android.data.local.WildDatabase
import app.wild.android.data.prefs.SettingsStore
import app.wild.android.data.repository.LibraryRepository
import app.wild.android.data.repository.SessionRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single { WildDatabase.build(androidContext()) }
    single { get<WildDatabase>().readingHistoryDao() }
    single { get<WildDatabase>().searchHistoryDao() }
    single { get<WildDatabase>().cookieDao() }
    single { get<WildDatabase>().webCacheDao() }
    single { get<WildDatabase>().chapterCacheDao() }
    single { get<WildDatabase>().signLogDao() }
    single { get<WildDatabase>().downloadDao() }

    single { SettingsStore(androidContext()) }
    single { LibraryRepository(get(), get()) }
    single { SessionRepository(get()) }
}
