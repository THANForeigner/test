package com.example.afinal.utils

import android.os.Handler
import android.os.Looper
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class IndoorDetector(private val context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val INDOOR_SNR_THRESHOLD = 25.0f
    private val MIN_SATELLITE_COUNT = 4
    val handler = Handler(Looper.getMainLooper())

    @RequiresApi(Build.VERSION_CODES.N)
    @SuppressLint("MissingPermission")
    fun observeIndoorStatus(): Flow<Boolean> = callbackFlow {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            close()
            return@callbackFlow
        }
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                super.onSatelliteStatusChanged(status)
                var totalSnr = 0.0f
                var visibleSatellites = 0
                val satelliteCount = status.satelliteCount
                if (satelliteCount == 0) {
                    trySend(true)
                    return
                }
                for (i in 0 until satelliteCount) {
                    if (status.usedInFix(i)) {
                        totalSnr += status.getCn0DbHz(i)
                        visibleSatellites++
                    }
                }
                if (visibleSatellites < MIN_SATELLITE_COUNT) {
                    trySend(true)
                } else {
                    val avgSnr = totalSnr / visibleSatellites
                    trySend(avgSnr < INDOOR_SNR_THRESHOLD)
                }
            }
        }

        locationManager.registerGnssStatusCallback(callback,handler)

        awaitClose {
            locationManager.unregisterGnssStatusCallback(callback)
        }
    }
}