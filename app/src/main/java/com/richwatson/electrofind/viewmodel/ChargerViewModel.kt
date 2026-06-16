package com.richwatson.electrofind.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.api.models.LocationSuggestion
import com.richwatson.electrofind.repository.ChargerRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortOrder { PRICE_ASC, PRICE_DESC, SPEED_DESC }
enum class SpeedFilter { ALL, FAST, RAPID, ULTRA }

data class SearchState(
    val isLoading: Boolean = false,
    val chargers: List<ChargingLocation> = emptyList(),
    val error: String? = null,
    val searchQuery: String = "",
    val searchLat: Double = 0.0,
    val searchLng: Double = 0.0,
    val sortOrder: SortOrder = SortOrder.PRICE_ASC,
    val speedFilter: SpeedFilter = SpeedFilter.ALL,
    val connectorFilter: String = "ALL",
    val loadingStatus: String = "",
    val fetchProgress: Float = 0f
)

class ChargerViewModel(private val repository: ChargerRepository) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val _navigateToResults = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToResults: SharedFlow<Unit> = _navigateToResults.asSharedFlow()

    private val _suggestions = MutableStateFlow<List<LocationSuggestion>>(emptyList())
    val suggestions: StateFlow<List<LocationSuggestion>> = _suggestions.asStateFlow()

    private var searchJob: Job? = null
    private var suggestionsJob: Job? = null

    val filteredSortedChargers: List<ChargingLocation>
        get() {
            val s = _state.value
            var list = s.chargers
            Log.d("ChargerViewModel", "filteredSortedChargers: total=${list.size} speed=${s.speedFilter} connector=${s.connectorFilter} sort=${s.sortOrder}")

            list = when (s.speedFilter) {
                SpeedFilter.ALL -> list
                SpeedFilter.FAST -> list.filter { it.maxKilowatts?.let { kw -> kw >= 7 } == true }
                SpeedFilter.RAPID -> list.filter { it.maxKilowatts?.let { kw -> kw >= 22 } == true }
                SpeedFilter.ULTRA -> list.filter { it.maxKilowatts?.let { kw -> kw >= 100 } == true }
            }
            Log.d("ChargerViewModel", "filteredSortedChargers: after speed filter=${list.size}")

            if (s.connectorFilter != "ALL") {
                list = list.filter { charger ->
                    charger.connectorTypes.any { it.contains(s.connectorFilter, ignoreCase = true) }
                }
                Log.d("ChargerViewModel", "filteredSortedChargers: after connector filter=${list.size}")
                if (list.isEmpty()) {
                    Log.w("ChargerViewModel", "connector filter '${s.connectorFilter}' removed all results. Sample types: ${s.chargers.take(3).map { it.connectorTypes }}")
                }
            }

            list = when (s.sortOrder) {
                SortOrder.PRICE_ASC -> list.sortedBy { it.pricePerKwh ?: Double.MAX_VALUE }
                SortOrder.PRICE_DESC -> list.sortedByDescending { it.pricePerKwh ?: -1.0 }
                SortOrder.SPEED_DESC -> list.sortedByDescending { it.maxKilowatts ?: 0.0 }
            }
            Log.d("ChargerViewModel", "filteredSortedChargers: returning ${list.size}")
            return list
        }

    fun searchByPlaceName(name: String, socketGroups: List<String> = listOf("CCS", "TYPE_2")) {
        if (name.isBlank()) return
        searchJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null, searchQuery = name, chargers = emptyList()) }
        searchJob = viewModelScope.launch {
            val coords = repository.geocode(name)
            if (coords == null) {
                _state.update { it.copy(isLoading = false, error = "Location not found: $name") }
                return@launch
            }
            _state.update { it.copy(searchLat = coords.first, searchLng = coords.second) }
            _navigateToResults.tryEmit(Unit)
            doSearch(coords.first, coords.second, socketGroups)
        }
    }

    fun fetchSuggestions(query: String) {
        suggestionsJob?.cancel()
        suggestionsJob = viewModelScope.launch {
            _suggestions.value = repository.fetchLocationSuggestions(query)
        }
    }

    fun clearSuggestions() {
        suggestionsJob?.cancel()
        _suggestions.value = emptyList()
    }

    fun searchByCoordinates(lat: Double, lng: Double, label: String? = null, socketGroups: List<String> = listOf("CCS", "TYPE_2")) {
        searchJob?.cancel()
        _state.update { it.copy(
            isLoading = true, error = null,
            searchQuery = label ?: "%.4f, %.4f".format(lat, lng),
            searchLat = lat, searchLng = lng,
            chargers = emptyList()
        ) }
        _navigateToResults.tryEmit(Unit)
        searchJob = viewModelScope.launch {
            doSearch(lat, lng, socketGroups)
        }
    }

    private suspend fun doSearch(lat: Double, lng: Double, socketGroups: List<String>) {
        var lastStatus = ""
        repository.searchChargers(
            lat, lng, socketGroups = socketGroups,
            onStatus = { status, progress ->
                Log.d("ChargerViewModel", "onStatus: progress=$progress status=$status")
                lastStatus = status
                _state.update { it.copy(loadingStatus = status, fetchProgress = progress) }
            }
        )
            .catch { e ->
                Log.e("ChargerViewModel", "catch: ${e.message}")
                _state.update { it.copy(isLoading = false, error = e.message ?: "Search failed") }
            }
            .onCompletion {
                Log.d("ChargerViewModel", "onCompletion: chargers=${_state.value.chargers.size} lastStatus=$lastStatus")
                _state.update { s ->
                    s.copy(
                        isLoading = false,
                        fetchProgress = 0f,
                        loadingStatus = if (s.chargers.isNotEmpty()) "" else lastStatus,
                        error = if (s.chargers.isEmpty() && s.error == null) "No chargers found within 3 miles" else s.error
                    )
                }
                Log.d("ChargerViewModel", "onCompletion: state updated, loadingStatus=${_state.value.loadingStatus}")
            }
            .collect { charger ->
                Log.d("ChargerViewModel", "collect: ${charger.name}")
                _state.update { it.copy(chargers = it.chargers + charger) }
            }
    }

    fun setSortOrder(order: SortOrder) {
        _state.update { it.copy(sortOrder = order) }
    }

    fun setSpeedFilter(filter: SpeedFilter) {
        _state.update { it.copy(speedFilter = filter) }
    }

    fun setConnectorFilter(connector: String) {
        _state.update { it.copy(connectorFilter = connector) }
    }

    fun clearResults() {
        searchJob?.cancel()
        _state.update { it.copy(chargers = emptyList(), error = null, isLoading = false) }
    }
}
