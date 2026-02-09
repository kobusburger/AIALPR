package com.example.aialpr.api

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

/**
 * Response from Plate Recognizer API v1/plate-reader
 * https://guides.platerecognizer.com/docs/snapshot/api-reference/
 */
@Parcelize
data class PlateRecognizerResponse(
    @SerializedName("processing_time") val processingTime: Double? = null,
    @SerializedName("results") val results: List<PlateResult>? = null,
    @SerializedName("filename") val filename: String? = null,
    @SerializedName("version") val version: Int? = null,
    @SerializedName("camera_id") val cameraId: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null,
    @SerializedName("detail") val detail: String? = null,
    @SerializedName("status_code") val statusCode: Int? = null
) : Parcelable

@Parcelize
data class PlateResult(
    @SerializedName("box") val box: Box? = null,
    @SerializedName("plate") val plate: String? = null,
    @SerializedName("region") val region: Region? = null,
    @SerializedName("vehicle") val vehicle: Vehicle? = null,
    @SerializedName("score") val score: Double? = null,
    @SerializedName("candidates") val candidates: List<Candidate>? = null,
    @SerializedName("dscore") val dscore: Double? = null
) : Parcelable

@Parcelize
data class Box(
    @SerializedName("xmin") val xmin: Int,
    @SerializedName("ymin") val ymin: Int,
    @SerializedName("xmax") val xmax: Int,
    @SerializedName("ymax") val ymax: Int
) : Parcelable

@Parcelize
data class Region(
    @SerializedName("code") val code: String? = null,
    @SerializedName("score") val score: Double? = null
) : Parcelable

@Parcelize
data class Vehicle(
    @SerializedName("score") val score: Double? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("box") val box: Box? = null
) : Parcelable

@Parcelize
data class Candidate(
    @SerializedName("score") val score: Double? = null,
    @SerializedName("plate") val plate: String? = null
) : Parcelable
