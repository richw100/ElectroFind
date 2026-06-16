package com.richwatson.electrofind.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.richwatson.electrofind.viewmodel.ChargerViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    chargerViewModel: ChargerViewModel,
    onResultsReady: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val state by chargerViewModel.state.collectAsState()
    val suggestions by chargerViewModel.suggestions.collectAsState()
    var searchText by remember { mutableStateOf("") }
    var locationError by remember { mutableStateOf<String?>(null) }
    var suggestionsExpanded by remember { mutableStateOf(false) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            getCurrentLocation(fusedLocationClient,
                onResult = { lat, lng -> chargerViewModel.searchByCoordinates(lat, lng) },
                onError = { locationError = it }
            )
        } else {
            locationError = "Location permission denied"
        }
    }

    LaunchedEffect(Unit) {
        chargerViewModel.navigateToResults.collect {
            onResultsReady()
        }
    }

    // Debounced autocomplete: fire after 400ms of no typing; cancel if text changes again
    LaunchedEffect(searchText) {
        if (searchText.length >= 2) {
            delay(400)
            chargerViewModel.fetchSuggestions(searchText)
            suggestionsExpanded = true
        } else {
            chargerViewModel.clearSuggestions()
            suggestionsExpanded = false
        }
    }

    // Keep expanded state in sync with whether we have suggestions
    LaunchedEffect(suggestions) {
        if (suggestions.isEmpty()) suggestionsExpanded = false
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("ElectroFind") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Find EV chargers sorted by price", style = MaterialTheme.typography.titleMedium)

            // Text field + autocomplete dropdown
            Box {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("Town, city or postcode") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboardController?.hide()
                        chargerViewModel.clearSuggestions()
                        chargerViewModel.searchByPlaceName(searchText)
                    }),
                    trailingIcon = {
                        IconButton(onClick = {
                            keyboardController?.hide()
                            chargerViewModel.clearSuggestions()
                            chargerViewModel.searchByPlaceName(searchText)
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                )
                // Dropdown anchored to the bottom of the Box (below the text field)
                DropdownMenu(
                    expanded = suggestionsExpanded && suggestions.isNotEmpty(),
                    onDismissRequest = { suggestionsExpanded = false },
                    properties = PopupProperties(focusable = false),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    suggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        suggestion.primaryName,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (suggestion.secondaryName.isNotEmpty()) {
                                        Text(
                                            suggestion.secondaryName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            onClick = {
                                searchText = suggestion.primaryName
                                suggestionsExpanded = false
                                chargerViewModel.clearSuggestions()
                                keyboardController?.hide()
                                chargerViewModel.searchByCoordinates(
                                    suggestion.lat, suggestion.lng,
                                    label = suggestion.primaryName
                                )
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        keyboardController?.hide()
                        chargerViewModel.clearSuggestions()
                        chargerViewModel.searchByPlaceName(searchText)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = searchText.isNotBlank() && !state.isLoading
                ) {
                    Text("Search")
                }
                OutlinedButton(
                    onClick = {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            locationError = null
                            getCurrentLocation(fusedLocationClient,
                                onResult = { lat, lng ->
                                    chargerViewModel.searchByCoordinates(lat, lng)
                                },
                                onError = { locationError = it }
                            )
                        } else {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isLoading
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Near me")
                }
            }

            if (state.isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Searching for chargers…")
                }
            }

            state.error?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error)
            }
            locationError?.let { err ->
                Text(err, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Suppress("MissingPermission")
private fun getCurrentLocation(
    client: com.google.android.gms.location.FusedLocationProviderClient,
    onResult: (Double, Double) -> Unit,
    onError: (String) -> Unit
) {
    client.lastLocation.addOnSuccessListener { loc ->
        if (loc != null) {
            onResult(loc.latitude, loc.longitude)
        } else {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { freshLoc ->
                    if (freshLoc != null) {
                        onResult(freshLoc.latitude, freshLoc.longitude)
                    } else {
                        onError("Could not get location — ensure GPS is enabled")
                    }
                }
                .addOnFailureListener { onError("Location error: ${it.message}") }
        }
    }.addOnFailureListener { onError("Location error: ${it.message}") }
}
