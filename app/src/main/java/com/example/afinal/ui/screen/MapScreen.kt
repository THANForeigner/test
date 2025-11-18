package com.example.afinal.ui.screen

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.afinal.LocationGPS
import com.example.afinal.LocationViewModel
import com.example.afinal.MainActivity
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun MapScreen(navController: NavController) {
    val viewModel: LocationViewModel = viewModel()
    val context = LocalContext.current
    val myLocationUtils = LocationGPS(context)
    val location = viewModel.location.value
    val requestPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            if (fine || coarse) {
                Toast.makeText(context, "Permissions granted", Toast.LENGTH_SHORT).show()
                myLocationUtils.requestLocationUpdate(viewModel = viewModel)
            } else {
                Toast.makeText(context, "Permissions denied", Toast.LENGTH_SHORT).show()
                val rationaleRequired = ActivityCompat.shouldShowRequestPermissionRationale(
                    context as MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                        ||
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            context as MainActivity,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                if (rationaleRequired) {
                    Toast.makeText(
                        context,
                        "This feature require location permission",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(context, "Please enable location permission", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }
    LaunchedEffect(Unit) {
        if(myLocationUtils.hasLocationPermission(context)){
            myLocationUtils.requestLocationUpdate(viewModel)
        }else{
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(10.7769, 106.7009), 12f)
    }
    LaunchedEffect(location) {
        if(location != null){
            cameraPositionState.animate(
                com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude,location.longitude),15f)
            )
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text("Your Location", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier.fillMaxSize().weight(1f)
        ){
            GoogleMap (
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            ){
                if(location != null){
                    Marker(
                        state = MarkerState(
                            position = LatLng(
                                location.latitude,
                                location.longitude
                            )
                        ),
                        title = "You are here",
                        snippet = "${location.latitude}, ${location.longitude}"
                    )
                }
            }
        }
    }
}

@Composable
fun MakerState(position: LatLng) {
    TODO("Not yet implemented")
}