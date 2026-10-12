package com.seagull.nivod

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.seagull.nivod.data.Site
import com.shuyu.gsyvideoplayer.player.PlayerFactory
import tv.danmaku.ijk.media.exo2.Exo2PlayerManager

class App : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        // ★ 必须显式指定播放内核。
        // GSY 的 PlayerFactory 默认是 IjkPlayerManager，而其静态字段会触发
        // System.loadLibrary("ijkffmpeg")；我们为瘦身剔掉了 libijk*.so（约 40MB），
        // 默认内核必然 UnsatisfiedLinkError 闪退。指定 Exo2 后走 media3，不碰 IJK。
        PlayerFactory.setPlayManager(Exo2PlayerManager::class.java)

        CrashHandler.install(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient { Site.client }
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder(this).maxSizePercent(0.25).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("img_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .build()
}
