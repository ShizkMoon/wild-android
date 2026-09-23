package app.wild.android.di

import app.wild.android.data.download.DownloadEngine
import app.wild.android.data.local.WildDatabase
import app.wild.android.data.prefs.SettingsStore
import app.wild.android.data.remote.CfBypass
import app.wild.android.data.remote.Wenku8Client
import app.wild.android.data.remote.Wenku8DataSource
import app.wild.android.data.remote.Wenku8HtmlSource
import app.wild.android.data.repository.LibraryRepository
import app.wild.android.data.repository.ReaderContentSource
import app.wild.android.data.repository.SessionRepository
import app.wild.android.ui.vm.AccountViewModel
import app.wild.android.ui.vm.BookshelfViewModel
import app.wild.android.ui.vm.DownloadDetailViewModel
import app.wild.android.ui.vm.DownloadSelectViewModel
import app.wild.android.ui.vm.DownloadsViewModel
import app.wild.android.ui.vm.HistoryViewModel
import app.wild.android.ui.vm.HomeViewModel
import app.wild.android.ui.vm.NovelInfoViewModel
import app.wild.android.ui.vm.ReaderViewModel
import app.wild.android.ui.vm.ReviewsViewModel
import app.wild.android.ui.vm.SearchViewModel
import app.wild.android.ui.vm.SessionViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { WildDatabase.build(androidContext()) }
    single { get<WildDatabase>().readingHistoryDao() }
    single { get<WildDatabase>().searchHistoryDao() }
    single { get<WildDatabase>().cookieDao() }
    single { get<WildDatabase>().webCacheDao() }
    single { get<WildDatabase>().chapterCacheDao() }
    single { get<WildDatabase>().imageCacheDao() }
    single { get<WildDatabase>().signLogDao() }
    single { get<WildDatabase>().downloadDao() }
    single { get<WildDatabase>().bookshelfLocalDao() }

    single { SettingsStore(androidContext()) }

    // ---- 数据通道（spec §3） ----
    single { Wenku8Client(androidContext(), get(), get(), get()) }
    single { CfBypass(get()) }
    single<Wenku8DataSource> { Wenku8HtmlSource(get(), get()) }
    single { DownloadEngine(androidContext(), get(), get(), get()) }
    single { ReaderContentSource(androidContext(), get(), get()) }

    // ---- Repository ----
    single { LibraryRepository(get(), get(), get(), get(), get(), get()) }
    single { SessionRepository(get(), get(), get(), get()) }

    // ---- ViewModels ----
    viewModel { HomeViewModel(get()) }
    viewModel { (aid: Int) -> NovelInfoViewModel(aid, get(), get()) }
    viewModel { SearchViewModel(get(), get()) }
    viewModel { BookshelfViewModel(get(), get()) }
    viewModel { HistoryViewModel(get()) }
    viewModel { (aid: Int) -> ReviewsViewModel(aid, get()) }
    viewModel { (aid: Int) -> DownloadSelectViewModel(aid, get(), get()) }
    viewModel { DownloadsViewModel(get()) }
    viewModel { (aid: Int) -> DownloadDetailViewModel(aid, get(), get()) }
    viewModel { (aid: Int, cid: Int) -> ReaderViewModel(aid, cid, get(), get(), get(), get()) }
    viewModel { SessionViewModel(get(), get(), get()) }
    viewModel { AccountViewModel(get(), get()) }
}
