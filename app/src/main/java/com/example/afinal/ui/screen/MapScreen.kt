package com.example.afinal.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.afinal.LocationGPS
import com.example.afinal.LocationViewModel
import com.example.afinal.StoryViewModel
import com.example.afinal.navigation.Routes
import com.example.afinal.utils.GeofenceHelper
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@Composable
fun MapScreen(navController: NavController) {
    val context = LocalContext.current
    val locationViewModel: LocationViewModel = viewModel()
    val storyViewModel: StoryViewModel = viewModel()

    val locations by storyViewModel.locations
    val myLocation = locationViewModel.location.value

    val geofenceHelper = remember { GeofenceHelper(context) }
    val myLocationUtils = remember { LocationGPS(context) }

    // School Center (VNUHCM)
    val schoolCenter = LatLng(10.762867, 106.682496)

    // Passive Permission Check
    val hasForegroundPermission = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    val hasBackgroundPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    // GPS Updates (only if allowed)
    LaunchedEffect(hasForegroundPermission) {
        if (hasForegroundPermission) {
            myLocationUtils.requestLocationUpdate(locationViewModel)
        }
    }

    // Geofencing (only if allowed & near school)
    LaunchedEffect(myLocation, locations, hasBackgroundPermission) {
        if (locations.isNotEmpty() && myLocation != null && hasBackgroundPermission) {
            val results = FloatArray(1)
            Location.distanceBetween(
                myLocation.latitude, myLocation.longitude,
                schoolCenter.latitude, schoolCenter.longitude,
                results
            )
            if (results[0] < 500) {
                geofenceHelper.addGeofences(locations)
            } else {
                geofenceHelper.removeGeofences()
            }
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(schoolCenter, 17f)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasForegroundPermission)
        ) {
            locations.forEach { loc ->
                // Debug Circle
                Circle(
                    center = LatLng(loc.latitude, loc.longitude),
                    radius = GeofenceHelper.GEOFENCE_RADIUS.toDouble(),
                    fillColor = Color(0x44FF0000),
                    strokeColor = Color.Red,
                    strokeWidth = 2f
                )
                Marker(
                    state = MarkerState(position = LatLng(loc.latitude, loc.longitude)),
                    title = loc.locationName,
                    snippet = "Tap to view posts",
                    onClick = {
                        navController.navigate("${Routes.AUDIO_PLAYER}/${loc.id}")
                        false
                    }
                )
            }
        }
    }
}