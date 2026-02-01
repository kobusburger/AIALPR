package com.example.aialpr.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recognition_results")
data class RecognitionResult(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: String,
    val plates: String,
    val regions: String,
    val scores: String,
    val photoBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RecognitionResult

        if (id != other.id) return false
        if (timestamp != other.timestamp) return false
        if (plates != other.plates) return false
        if (regions != other.regions) return false
        if (scores != other.scores) return false
        if (!photoBytes.contentEquals(other.photoBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + plates.hashCode()
        result = 31 * result + regions.hashCode()
        result = 31 * result + scores.hashCode()
        result = 31 * result + photoBytes.contentHashCode()
        return result
    }
}
