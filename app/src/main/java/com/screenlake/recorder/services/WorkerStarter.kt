package com.screenlake.recorder.services

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkerStarter is a singleton class responsible for initializing and managing periodic workers
 * using Android's WorkManager API. It handles tasks like file uploads, file zipping, and OCR operations.
 *
 */
@Singleton
class WorkerStarter @Inject constructor(
    context: Context
) {

    // Instance of WorkManager
    private val workManager = WorkManager.getInstance(context)

    /**
     * Invokes the initialization of all periodic workers.
     * This method sets up workers for:
     * 1. File uploads
     * 2. File zipping
     * 3. OCR processing
     */
    operator fun invoke() {
        initUploadWorker()
        initZipFileWorker()
        initOCRWorker()
    }

    private fun initUploadWorker() {
        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName = WORK_MANAGER_UPLOAD_WORKER,
            existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
            request = PeriodicWorkRequestBuilder<UploadWorker>(3, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                        .build()
                ).build()
        )
    }

    private fun initZipFileWorker() {
        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName = WORK_MANAGER_ZIP_FILE_WORKER,
            existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
            request = PeriodicWorkRequestBuilder<ZipFileWorker>(3, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                        .build()
                ).build()
        )
    }

    private fun initOCRWorker() {
        workManager.enqueueUniquePeriodicWork(
            uniqueWorkName = WORK_MANAGER_OCR_WORKER,
            existingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
            request = PeriodicWorkRequestBuilder<OcrWorker>(3, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                        .build()
                ).build()
        )
    }

    /**
     * Companion object to store unique names for the workers.
     */
    companion object {
        const val WORK_MANAGER_UPLOAD_WORKER = "WORK_MANAGER_UPLOAD_WORKER"
        const val WORK_MANAGER_ZIP_FILE_WORKER = "WORK_MANAGER_ZIP_FILE_WORKER"
        const val WORK_MANAGER_OCR_WORKER = "WORK_MANAGER_OCR_WORKER"
    }
}