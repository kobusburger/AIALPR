package com.example.aialpr.db

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "recognition_results")
data class RecognitionResult(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: String,
    val plate: String,
    val region: String,
    val score: Double,
    val photoBytes: ByteArray
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RecognitionResult

        if (id != other.id) return false
        if (timestamp != other.timestamp) return false
        if (plate != other.plate) return false
        if (region != other.region) return false
        if (score != other.score) return false
        if (!photoBytes.contentEquals(other.photoBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + plate.hashCode()
        result = 31 * result + region.hashCode()
        result = 31 * result + score.hashCode()
        result = 31 * result + photoBytes.contentHashCode()
        return result
    }
}
