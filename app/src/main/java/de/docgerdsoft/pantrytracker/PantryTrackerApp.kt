package de.docgerdsoft.pantrytracker

import android.app.Application
import de.docgerdsoft.pantrytracker.di.AppContainer

class PantryTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
