package com.example.aialpr.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface PlateInfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(info: List<PlateInfo>)

    @Query("SELECT * FROM plate_info WHERE plate = :plate")
    suspend fun getInfoForPlate(plate: String): PlateInfo?

    @Query("DELETE FROM plate_info")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(info: List<PlateInfo>) {
        deleteAll()
        insertAll(info)
    }
}
