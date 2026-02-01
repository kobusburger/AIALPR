package com.example.aialpr.api

import com.google.gson.annotations.SerializedName

/**
 * Response from Plate Recognizer API v1/plate-reader
 * https://guides.platerecognizer.com/docs/snapshot/api-reference/
 */
data class PlateRecognizerResponse(
    @SerializedName("processing_time") val processingTime: Double? = null,
    @SerializedName("results") val results: List<PlateResult>? = null,
    @SerializedName("filename") val filename: String? = null,
    @SerializedName("version") val version: Int? = null,
    @SerializedName("camera_id") val cameraId: String? = null,
    @SerializedName("timestamp") val timestamp: String? = null,
    @SerializedName("detail") val detail: String? = null,
    @SerializedName("status_code") val statusCode: Int? = null
)

data class PlateResult(
    @SerializedName("box") val box: Box? = null,
    @SerializedName("plate") val plate: String? = null,
    @SerializedName("region") val region: Region? = null,
    @SerializedName("vehicle") val vehicle: Vehicle? = null,
    @SerializedName("score") val score: Double? = null,
    @SerializedName("candidates") val candidates: List<Candidate>? = null,
    @SerializedName("dscore") val dscore: Double? = null
)

data class Box(
    @SerializedName("xmin") val xmin: Int,
    @SerializedName("ymin") val ymin: Int,
    @SerializedName("xmax") val xmax: Int,
    @SerializedName("ymax") val ymax: Int
)

data class Region(
    @SerializedName("code") val code: String? = null,
    @SerializedName("score") val score: Double? = null
)

data class Vehicle(
    @SerializedName("score") val score: Double? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("box") val box: Box? = null
)

data class Candidate(
    @SerializedName("score") val score: Double? = null,
    @SerializedName("plate") val plate: String? = null
)
