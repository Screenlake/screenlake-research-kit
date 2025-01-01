package com.screenlake

import android.content.Context
import androidx.room.Database
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.screenlake.data.DataHelper
import com.screenlake.data.database.entity.ScreenshotEntity
import com.screenlake.di.DatabaseModule
import com.screenlake.recorder.ocr.Recognize
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.File

/**
 * Instrumented test class for testing the Recognize OCR functionality.
 *
 * This class provides tests for initializing Tesseract, processing images for OCR,
 * and stopping Tesseract using the `Recognize` class.
 */
@RunWith(AndroidJUnit4::class)
class RecognizeInstrumentedTest {

    private lateinit var recognize: Recognize
    private lateinit var appContext: Context
    private lateinit var tessDataDir: String

    /**
     * Sets up the test environment.
     *
     * Initializes the Timber logger for debug output, copies necessary assets such as
     * Tesseract trained data and a test image to the internal storage, and initializes the `Recognize` class.
     */
    @Before
    fun setUp() {
        // Initialize the Timber logger for debug output
        Timber.plant(Timber.DebugTree())

        // Get the application context
        appContext = InstrumentationRegistry.getInstrumentation().targetContext

        // Copy Tesseract trained data and test image to internal storage
        tessDataDir = DataHelper.copyAssetToInternalStorage(appContext, "eng.traineddata")
        DataHelper.copyAssetToInternalStorage(appContext, "img.png")

        // Initialize the Recognize class
        recognize = Recognize(DatabaseModule.provideDatabase(appContext).getUserDao())
    }

    /**
     * Tests the initialization of the Tesseract OCR engine.
     *
     * Verifies that the Tesseract engine is properly initialized by calling the `initTesseract` method,
     * and checks that the `isInitialized` flag is set to true.
     */
    @Test
    fun testInitTesseract() {
        // Initialize Tesseract with the given trained data directory and language
        recognize.initTesseract(tessDataDir, "eng", com.googlecode.tesseract.android.TessBaseAPI.OEM_LSTM_ONLY)

        // Assert that Tesseract is initialized
        assert(recognize.isInitialized) { "Tesseract should be initialized" }
    }

    /**
     * Tests the processing of an image using the Tesseract OCR engine.
     *
     * Verifies that the image processing using the `processImage` method returns true, indicating successful OCR.
     * The test also ensures that the `isOcrComplete` flag in the `Screenshot` object is set to true.
     */
    @Test
    fun testProcessImage() = runBlocking {
        // Create a temporary image file for testing
        val tempImageFile = File(appContext.filesDir, "img.png")

        // Create a screenshot object with the path to the temporary image
        val screenshot = ScreenshotEntity(filePath = tempImageFile.absolutePath)

        // Process the image using Tesseract OCR
        val result = recognize.processImage(screenshot)

        // Assert that the image processing was successful
        assert(result) { "Image processing should return true" }

        // Assert that the OCR process is marked as complete in the screenshot object
        assert(screenshot.isOcrComplete) { "OCR should be marked complete" }

        // Clean up the temporary file
        tempImageFile.delete()
    }

    /**
     * Tests the stopping of the Tesseract OCR engine.
     *
     * Verifies that Tesseract processing is properly stopped by calling the `stop` method
     * and checking that the `stopped` flag is set to true.
     */
    @Test
    fun testStopTesseract() {
        // Stop the Tesseract OCR engine
        recognize.stop()

        // Assert that Tesseract has stopped processing
        assert(recognize.stopped) { "Tesseract processing should be stopped" }
    }

    /**
     * Cleans up the test environment after each test.
     *
     * Deletes the Tesseract trained data and test image copied to the internal storage to ensure
     * there is no leftover test data.
     */
    @After
    fun tearDown() {
        // Clean up the test environment
        val tessFile = File(tessDataDir)
        if (tessFile.exists()) {
            tessFile.deleteRecursively()
        }
    }
}
