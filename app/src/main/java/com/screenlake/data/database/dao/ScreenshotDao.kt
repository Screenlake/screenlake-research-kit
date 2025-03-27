package com.screenlake.data.database.dao

import androidx.room.*
import com.screenlake.data.database.entity.ScreenshotEntity

/**
 * Data Access Object (DAO) for interacting with the screenshot_table in the database.
 */
@Dao
interface ScreenshotDao {

    /**
     * Inserts or updates a Screenshot.
     *
     * @param screenshot The Screenshot to be saved.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScreenshot(screenshot: ScreenshotEntity)

    /**
     * Deletes a Screenshot.
     *
     * @param screenshot The Screenshot to be deleted.
     */
    @Delete
    suspend fun deleteScreenshot(screenshot: ScreenshotEntity)

    /**
     * Deletes Screenshots with the specified IDs.
     *
     * @param idList The list of IDs of the Screenshots to be deleted.
     */
    @Query("delete from screenshot_table where id in (:idList)")
    fun deleteScreenshots(idList: List<Int>)

    /**
     * Retrieves a list of Screenshots ordered by timestamp in descending order, limited by the specified number and offset.
     *
     * @param limit The maximum number of Screenshots to retrieve.
     * @param offset The offset from which to start retrieving Screenshots.
     * @return A list of Screenshots.
     */
    @Query("SELECT * FROM screenshot_table ORDER BY timestamp DESC LIMIT :limit OFFSET :offset ")
    suspend fun getAllScreenshotsSortedByDate(limit: Int, offset: Int): List<ScreenshotEntity>

    /**
     * Retrieves a list of Screenshots where OCR is complete and appSegmentId is not null, ordered by timestamp in descending order, limited by the specified number and offset.
     *
     * @param limit The maximum number of Screenshots to retrieve.
     * @param offset The offset from which to start retrieving Screenshots.
     * @return A list of Screenshots.
     */
    @Query("SELECT * FROM screenshot_table where isOcrComplete is 1 and appSegmentId is not NULL ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getAllScreenshotsSortedByDateWhereOcrIsComplete(limit: Int, offset: Int): List<ScreenshotEntity>

    @Query("SELECT DISTINCT sessionId FROM screenshot_table WHERE appSegmentId IS NULL ORDER BY timestamp DESC")
    suspend fun getAllSessionsWithoutAppSegments(): List<String>

    /**
     * Retrieves a list of Screenshots where OCR is not complete, type is 'SCREENSHOT', and isAppRestricted is false, ordered by timestamp in descending order, limited by the specified number and offset.
     *
     * @param limit The maximum number of Screenshots to retrieve.
     * @param offset The offset from which to start retrieving Screenshots.
     * @return A list of Screenshots.
     */
    @Query("SELECT * FROM screenshot_table where isOcrComplete is 0 and type is 'SCREENSHOT' and isAppRestricted is 0 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAllScreenshotsSortedByDateWhereOcrIsNotComplete(limit: Int, offset: Int): List<ScreenshotEntity>

    /**
     * Retrieves a list of Screenshots by their file paths.
     *
     * @param files The list of file paths to filter the Screenshots.
     * @return A list of Screenshots.
     */
    @Query("Select * from screenshot_table where filePath in (:files)")
    suspend fun getScreenshotsByFileName(files: List<String>): List<ScreenshotEntity>

    /**
     * Inserts or updates a Screenshot as a restricted app.
     *
     * @param screenshot The Screenshot to be saved.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRestrictedApp(screenshot: ScreenshotEntity)

    /**
     * Deletes a restricted app Screenshot.
     *
     * @param screenshot The Screenshot to be deleted.
     */
    @Delete
    suspend fun deleteRestrictedApp(screenshot: ScreenshotEntity)

    /**
     * Retrieves a list of restricted app Screenshots ordered by timestamp.
     *
     * @return A list of Screenshots.
     */
    @Query("SELECT * FROM screenshot_table ORDER BY timestamp")
    suspend fun getAllRestrictedAppsSortedByDate(): List<ScreenshotEntity>

    /**
     * Updates the OCR completion status and text of a Screenshot.
     *
     * @param id The ID of the Screenshot to be updated.
     * @param isOcrComplete The new OCR completion status.
     * @param text The new text extracted from the Screenshot.
     */
    @Query("UPDATE screenshot_table SET isOcrComplete=:isOcrComplete, text=:text WHERE id = :id")
    suspend fun setOcrComplete(id: Int, isOcrComplete: Boolean, text: String)

    /**
     * Deletes all Screenshots from the screenshot_table.
     */
    @Query("DELETE FROM screenshot_table")
    suspend fun nukeTable()

    /**
     * Retrieves a Screenshot taken between the specified start and end epoch timestamps, with a non-null sessionId.
     *
     * @param start The start epoch timestamp.
     * @param end The end epoch timestamp.
     * @return The Screenshot taken between the specified interval.
     */
    @Query("SELECT * FROM screenshot_table WHERE epochTimeStamp >= :start AND epochTimeStamp <= :end and sessionId not Null limit 1")
    suspend fun getScreenshotBetweenInterval(start:Long, end:Long): ScreenshotEntity?

    /**
     * Retrieves the count of Screenshots where OCR is complete.
     *
     * @return The count of Screenshots where OCR is complete.
     */
    @Query("SELECT COUNT(id) FROM screenshot_table where isOcrComplete is 1")
    suspend fun getCountWhereOcrIsComplete(): Int

    /**
     * Retrieves the total count of Screenshots.
     *
     * @return The total count of Screenshots.
     */
    @Query("SELECT COUNT(id) FROM screenshot_table")
    suspend fun getCount(): Int

    /**
     * Retrieves a list of Screenshots by their session ID.
     *
     * @param sessionId The session ID to filter the Screenshots.
     * @return A list of Screenshots.
     */
    @Query("SELECT * FROM screenshot_table where sessionId is :sessionId")
    suspend fun getScreenshotsBySessionId(sessionId: String) : List<ScreenshotEntity>
}