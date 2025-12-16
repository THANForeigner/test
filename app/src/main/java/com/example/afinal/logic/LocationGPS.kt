package com.example.afinal.logic

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.afinal.data.LocationData
import com.example.afinal.models.LocationViewModel
import com.example.afinal.ultis.IndoorDetector
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LocationGPS(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val pdrSystem: PDRSystem
    private val indoorDetector = IndoorDetector(context)

    private var locationCallback: LocationCallback? = null
    private var locationJob: Job? = null

    // Track the last known location to initialize PDR
    private var lastKnownLocation: LocationData? = null
    private var isTracking = false

    init {
        // Initialize PDR System with a callback that updates the ViewModel (via a bridge we set later)
        pdrSystem = PDRSystem(context) { newLocation ->
            lastKnownLocation = newLocation
            // We need a way to pass this to the ViewModel.
            // Since we don't have the VM reference in init, we handle it in startTracking.
            updateListener?.invoke(newLocation)
        }
    }

    private var updateListener: ((LocationData) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startTracking(viewModel: LocationViewModel, scope: CoroutineScope) {
        if (isTracking) return
        isTracking = true

        updateListener = { loc ->
            viewModel.updateLocation(loc)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locationJob = scope.launch(Dispatchers.Main) {
                indoorDetector.observeIndoorStatus().collectLatest { isIndoor ->
                    Log.d("LocationGPS", "Indoor Status Changed: $isIndoor")
                    if (isIndoor) {
                        switchMode(indoor = true)
                    } else {
                        switchMode(indoor = false)
                    }
                }
            }
        } else {
            // Fallback for older Android: Always use GPS
            startGpsUpdates()
        }
    }

    fun stopTracking() {
        isTracking = false
        locationJob?.cancel()
        stopGpsUpdates()
        pdrSystem.stop()
        updateListener = null
    }

    private fun switchMode(indoor: Boolean) {
        if (indoor) {
            Log.d("LocationGPS", "Switching to PDR Mode")
            stopGpsUpdates()
            if (lastKnownLocation != null) {
                pdrSystem.start(lastKnownLocation!!)
            } else {
                // If we don't have a start location yet, try to get one last GPS fix or wait
                Log.w("LocationGPS", "Cannot start PDR: No start location. Waiting for GPS...")
                startGpsUpdates(singleUpdate = true)
            }
        } else {
            Log.d("LocationGPS", "Switching to GPS Mode")
            pdrSystem.stop()
            startGpsUpdates()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startGpsUpdates(singleUpdate: Boolean = false) {
        if (!hasLocationPermission(context)) return

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let {
                    val locData = LocationData(it.latitude, it.longitude)
                    lastKnownLocation = locData
                    updateListener?.invoke(locData)

                    if (singleUpdate) {
                        // We got our fix for PDR, now switch back
                        stopGpsUpdates()
                        pdrSystem.start(locData)
                    }
                }
            }
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateDistanceMeters(2f) // Update frequently for smooth outdoor transitions
            .build()

        fusedLocationClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
    }

    private fun stopGpsUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    private fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}