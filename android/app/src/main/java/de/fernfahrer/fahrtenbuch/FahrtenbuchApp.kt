package de.fernfahrer.fahrtenbuch

import android.app.Application
import de.fernfahrer.fahrtenbuch.data.AppDatabase
import de.fernfahrer.fahrtenbuch.data.Repository
import de.fernfahrer.fahrtenbuch.data.Settings

class FahrtenbuchApp : Application() {
    lateinit var repository: Repository
        private set
    lateinit var settings: Settings
        private set

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        repository = Repository(AppDatabase.create(this), settings.rules)
    }
}
