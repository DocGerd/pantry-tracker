package de.docgerdsoft.pantrytracker

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import de.docgerdsoft.pantrytracker.di.AppContainer

open class PantryTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.real(this)
        // OkHttpNetworkFetcherFactory is auto-registered by the coil-network-okhttp
        // artifact — explicit .components { add(...) } is redundant and can be dropped.
        SingletonImageLoader.setSafe { ctx ->
            ImageLoader.Builder(ctx).build()
        }
    }
}
