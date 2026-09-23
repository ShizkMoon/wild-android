package app.wild.android

import android.app.Application
import app.wild.android.data.download.DownloadEngine
import app.wild.android.data.remote.Wenku8Client
import app.wild.android.data.remote.Wenku8DataSource
import app.wild.android.di.appModule
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class WildApp : Application() {

    private val client: Wenku8Client by inject()
    private val source: Wenku8DataSource by inject()
    private val downloadEngine: DownloadEngine by inject()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@WildApp)
            modules(appModule)
        }
        // Coil 全局 ImageLoader：复用 OkHttp（cookie/缓存/UA/Referer 由 imageClient 拦截器补齐）
        SingletonImageLoader.setSafe {
            ImageLoader.Builder(this)
                .crossfade(true)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { client.imageClient() }))
                }
                .build()
        }
        // 会话预热必须在 Application 层：深链直达内页会跳过 InitScreen，
        // 否则 UA/cookie 恢复与 session 种植都缺位（表现为 index.php 403 + 空 cookie）。
        appScope.launch {
            client.init()
            runCatching { source.initSession() }
        }
        // 下载引擎常驻：扫 pending 续传（断点续传）
        downloadEngine.start()
    }
}
