package com.stralarp.app.data.model

import kotlin.math.abs
import kotlin.math.max

data class RouteStats(
    val distanceKm: Double,
    val elevationGain: Double,
    val elevationLoss: Double,
    val maxElevation: Double?,
    val minElevation: Double?,
    val maxGradePercent: Double,
    val avgGradePercent: Double,
    val climbingDistanceM: Double
)

fun Route.computeStats(): RouteStats {
    if (points.size < 2) {
        return RouteStats(0.0, 0.0, 0.0, null, null, 0.0, 0.0, 0.0)
    }
    var gain = 0.0
    var loss = 0.0
    var maxEle: Double? = null
    var minEle: Double? = null
    var maxGrade = 0.0
    var gradeSum = 0.0
    var gradeCount = 0
    var climbDist = 0.0

    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val dist = a.distanceTo(b)
        val ea = a.elevation
        val eb = b.elevation
        if (ea != null) {
            maxEle = maxEle?.let { max(it, ea) } ?: ea
            minEle = minEle?.let { kotlin.math.min(it, ea) } ?: ea
        }
        if (eb != null) {
            maxEle = maxEle?.let { max(it, eb) } ?: eb
            minEle = minEle?.let { kotlin.math.min(it, eb) } ?: eb
        }
        if (ea != null && eb != null && dist > 1.0) {
            val dEle = eb - ea
            if (dEle > 0) {
                gain += dEle
                climbDist += dist
            } else {
                loss += -dEle
            }
            val grade = (dEle / dist) * 100.0
            maxGrade = max(maxGrade, abs(grade))
            gradeSum += abs(grade)
            gradeCount++
        }
    }
    return RouteStats(
        distanceKm = totalDistanceKm,
        elevationGain = gain,
        elevationLoss = loss,
        maxElevation = maxEle,
        minElevation = minEle,
        maxGradePercent = maxGrade,
        avgGradePercent = if (gradeCount > 0) gradeSum / gradeCount else 0.0,
        climbingDistanceM = climbDist
    )
}

/** Indices of points closest to each full kilometre along the route. */
fun Route.kmMarkerIndices(): List<Pair<Int, Int>> {
    if (points.size < 2) return emptyList()
    val result = mutableListOf<Pair<Int, Int>>()
    var cum = 0.0
    var nextKm = 1
    for (i in 1 until points.size) {
        cum += points[i - 1].distanceTo(points[i])
        while (cum >= nextKm * 1000.0) {
            result.add(i to nextKm)
            nextKm++
        }
    }
    return result
}
