package com.denser.june

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.denser.june.core.domain.preferences.JournalPreferences
import com.denser.june.core.domain.reminder.ReminderScheduler
import com.denser.june.core.domain.repository.JournalRepository
import com.denser.june.core.media.storage.AudioStorageManager
import com.denser.june.core.media.storage.MediaStorageManager
import com.denser.june.core.media.storage.SongMediaStorage
import com.denser.june.billing.initializePurchases
import com.denser.june.di.flavorModule
import com.denser.june.di.juneModules
import com.denser.june.notification.NotificationsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import kotlin.time.Duration.Companion.milliseconds

class JuneApplication : Application(), ImageLoaderFactory {
    private val journalRepo: JournalRepository by inject()
    private val songRepo: com.denser.june.core.domain.repository.SongRepository by inject()
    private val imageLoader: ImageLoader by inject()
    private val journalPreferences: JournalPreferences by inject()
    private val reminderScheduler: ReminderScheduler by inject()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun newImageLoader(): ImageLoader = imageLoader

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@JuneApplication)
            modules(juneModules, flavorModule)
        }

        initializePurchases(this)

        NotificationsHelper(this).createNotificationChannel()
        rescheduleReminders()
        cleanupStorage()
    }

    /** Alarms are not part of a backup, so after a restore they only come back once June sets them again. */
    private fun rescheduleReminders() {
        appScope.launch {
            try {
                reminderScheduler.sync(
                    masterEnabled = journalPreferences.isReminderEnabled().first(),
                    slots = journalPreferences.reminderSlots().first()
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun cleanupStorage() {
        appScope.launch {
            try {
                MediaStorageManager.migrateLegacyMediaStorage(applicationContext)
                SongMediaStorage.migrateLegacySongStorage(applicationContext)
                delay(3000L.milliseconds)
                val allJournals = journalRepo.getAllJournalsIncludeDeletedSync()
                val librarySongs = songRepo.getLibrarySongs().first()
                val activePaths = allJournals.flatMap { it.images } +
                    allJournals.mapNotNull { it.songDetails?.localThumbnailPath } +
                    librarySongs.mapNotNull { it.localThumbnailPath }
                MediaStorageManager.cleanOrphanedFiles(applicationContext, activePaths)
                val activeAudioPaths = allJournals.flatMap { journal -> journal.audios.map { it.path } }.filter { it.isNotBlank() }
                AudioStorageManager.cleanOrphanedAudioFiles(applicationContext, activeAudioPaths)
                songRepo.cleanupUnreferencedSongMedia(allJournals)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
