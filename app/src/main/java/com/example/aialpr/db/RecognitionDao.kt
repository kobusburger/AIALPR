package com.example.aialpr.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RecognitionDao {
    @Insert
    suspend fun insert(result: RecognitionResult)

    @Query("SELECT * FROM recognition_results ORDER BY id DESC")
    suspend fun getAll(): List<RecognitionResult>
}
