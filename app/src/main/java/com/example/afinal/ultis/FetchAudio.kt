package com.example.afinal.ultis

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.afinal.models.StoryViewModel
import com.example.afinal.models.LocationModel
import com.example.afinal.data.LocationData
import com.example.afinal.ultis.DistanceCalculator

/**
 * Handles the logic for detecting nearby locations and triggering audio fetches.
 * This separates the "Side Effects" from the UI code.
 */
@Composable
fun FetchAudio(
    userLocation: LocationData?,
    allLocations: List<LocationModel>,
    isUserIndoor: Boolean,
    currentLocationId: String?,
    storyViewModel: StoryViewModel
) {
    // MAIN LOGIC: Pinning & Tracking
    LaunchedEffect(userLocation, allLocations, isUserIndoor) {
        val userLoc = userLocation

        // Safety check: ensure we have data
        if (userLoc != null && allLocations.isNotEmpty()) {

            // Check if we are currently pinned to a valid indoor location
            val isCurrentlyInsideBuilding = currentLocationId != null &&
                    allLocations.find { it.id == currentLocationId }?.type == "indoor"

            // If we are indoors and pinned, stop looking for new places (stable state)
            if (isUserIndoor && isCurrentlyInsideBuilding) {
                return@LaunchedEffect
            }

            // LIVE TRACKING (Outdoor OR Searching for Building) ---

            // Define what we are looking for
            val candidates = if (isUserIndoor) {
                // If Indoor but NOT yet selected: Look for closest Building
                allLocations.filter { it.type == "indoor" }
            } else {
                // If Outdoor: Look for Everything (Regain full tracking)
                allLocations
            }

            // Use the separated DistanceCalculator to find the nearest match
            val foundLocation = DistanceCalculator.findNearestLocation(
                userLoc.latitude,
                userLoc.longitude,
                candidates
            )

            if (foundLocation != null) {
                // Enter the location if it's new
                if (foundLocation.id != currentLocationId) {
                    storyViewModel.fetchStoriesForLocation(foundLocation.id)
                }
            } else {
                // Exit condition: Only clear if we are NOT pinned
                // (We already handled the Pinned case at the top, so if we reach here, we are free to clear)
                if (currentLocationId != null) {
                    storyViewModel.clearLocation()
                }
            }
        }
    }
}