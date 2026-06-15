package com.richwatson.electrofind.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.repository.ChargerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SortOrder { PRICE_ASC, PRICE_DESC, SPEED_DESC }
enum class SpeedFilter { ALL, FAST, RAPID, ULTRA }

data class SearchState(
    val isLoading: Boolean = false,
    val chargers: List<ChargingLocation> = emptyList(),
    val error: String? = null,
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.PRICE_ASC,
    val speedFilter: SpeedFilter = SpeedFilter.ALL,
    val connectorFilter: String = "ALL"  // "ALL", "CCS", "TYPE_2", "CHADEMO"
)

class ChargerViewModel(private val repository: ChargerRepository) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    val filteredSortedChargers: List<ChargingLocation>
        get() {
            var list = _state.value.chargers

            // Filter by speed
            list = when (_state.value.speedFilter) {
                SpeedFilter.ALL -> list
                SpeedFilter.FAST -> list.filter { it.maxKilowatts?.let { kw -> kw >= 7 } == true }
                SpeedFilter.RAPID -> list.filter { it.maxKilowatts?.let { kw -> kw >= 22 } == true }
                SpeedFilter.ULTRA -> list.filter { it.maxKilowatts?.let { kw -> kw >= 100 } == true }
            }

            // Filter by connector
            if (_state.value.connectorFilter != "ALL") {
                list = list.filter { charger ->
                    charger.connectorTypes.any { it.contains(_state.value.connectorFilter, ignoreCase = true) }
                }
            }

            // Sort (nulls sorted last so chargers without price data go to the end)
            list = when (_state.value.sortOrder) {
                SortOrder.PRICE_ASC -> list.sortedBy { it.pricePerKwh ?: Double.MAX_VALUE }
                SortOrder.PRICE_DESC -> list.sortedByDescending { it.pricePerKwh ?: -1.0 }
                SortOrder.SPEED_DESC -> list.sortedByDescending { it.maxKilowatts ?: 0.0 }
            }

            return list
        }

    fun searchByPlaceName(name: String, socketGroups: List<String> = listOf("CCS", "TYPE_2")) {
        if (name.isBlank()) return
        _state.value = _state.value.copy(isLoading = true, error = null, searchQuery = name)
        viewModelScope.launch {
            val coords = repository.geocode(name)
            if (coords == null) {
                _state.value = _state.value.copy(isLoading = false, error = "Location not found: $name")
                return@launch
            }
            doSearch(coords.first, coords.second, socketGroups)
        }
    }

    fun searchByCoordinates(lat: Double, lng: Double, socketGroups: List<String> = listOf("CCS", "TYPE_2")) {
        _state.value = _state.value.copy(isLoading = true, error = null, searchQuery = "%.4f, %.4f".format(lat, lng))
        viewModelScope.launch {
            doSearch(lat, lng, socketGroups)
        }
    }

    private suspend fun doSearch(lat: Double, lng: Double, socketGroups: List<String>) {
        val result = repository.searchChargers(lat, lng, zoom = 12, gridRadius = 1, socketGroups = socketGroups)
        result.fold(
            onSuccess = { chargers ->
                _state.value = _state.value.copy(isLoading = false, chargers = chargers, error = null)
            },
            onFailure = { error ->
                _state.value = _state.value.copy(isLoading = false, error = error.message ?: "Search failed")
            }
        )
    }

    fun setSortOrder(order: SortOrder) {
        _state.value = _state.value.copy(sortOrder = order)
    }

    fun setSpeedFilter(filter: SpeedFilter) {
        _state.value = _state.value.copy(speedFilter = filter)
    }

    fun setConnectorFilter(connector: String) {
        _state.value = _state.value.copy(connectorFilter = connector)
    }
}
