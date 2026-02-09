package com.example.aialpr.db

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "plate_info")
data class PlateInfo(
    @PrimaryKey val plate: String,
    val extraData: String // Store as a formatted string or JSON
) : Parcelable
