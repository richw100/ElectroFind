package com.richwatson.electrofind.viewmodel

import android.app.Application
import android.location.Geocoder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.api.models.DataSource
import com.richwatson.electrofind.api.models.LocationSuggestion
import com.richwatson.electrofind.preferences.AppPreferences
import com.richwatson.electrofind.repository.ChargerRepository
import com.richwatson.electrofind.repository.OcmRepository
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
enum class ThemeMode { LIGHT, DARK, SYSTEM }

data class SearchState(
    val isLoadingEv: Boolean = false,
    val isLoadingOcm: Boolean = false,
    val chargers: List<ChargingLocation> = emptyList(),
    val ocmChargers: List<ChargingLocation> = emptyList(),
    val error: String? = null,
    val ocmError: String? = null,
    val searchQuery: String = "",
    val searchLat: Double = 0.0,
    val searchLng: Double = 0.0,
    val sortOrder: SortOrder = SortOrder.PRICE_ASC,
    val speedFilter: SpeedFilter = SpeedFilter.ALL,
    val connectorFilter: String = "ALL",
    val minPriceKwh: Double? = null,
    val maxPriceKwh: Double? = null,
    val currencySymbol: String = "€",
    val loadingStatus: String = "",
    val fetchProgress: Float = 0f,
    val dataSource: DataSource = DataSource.ELECTROVERSE,
    val searchRadiusMiles: Int = 3,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val savedMapZoom: Double = 14.0,
    val savedMapCenterLat: Double = 0.0,
    val savedMapCenterLng: Double = 0.0
) {
    val isLoading: Boolean get() = isLoadingEv || isLoadingOcm
}

class ChargerViewModel(
    private val repository: ChargerRepository,
    private val ocmRepository: OcmRepository,
    private val appPreferences: AppPreferences,
    private val application: Application
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val _navigateToResults = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToResults: SharedFlow<Unit> = _navigateToResults.asSharedFlow()

    private val _suggestions = MutableStateFlow<List<LocationSuggestion>>(emptyList())
    val suggestions: StateFlow<List<LocationSuggestion>> = _suggestions.asStateFlow()

    private var searchJob: Job? = null
    private var suggestionsJob: Job? = null

    init {
        _state.update {
            it.copy(
                dataSource = appPreferences.dataSource,
                searchRadiusMiles = appPreferences.searchRadiusMiles,
                themeMode = appPreferences.themeMode,
                savedMapZoom = appPreferences.mapZoom,
                savedMapCenterLat = appPreferences.mapCenterLat,
                savedMapCenterLng = appPreferences.mapCenterLng
            )
        }
    }

    val filteredSortedChargers: List<ChargingLocation>
        get() {
            val s = _state.value
            val allChargers = when (s.dataSource) {
                DataSource.ELECTROVERSE -> s.chargers
                DataSource.OCM -> s.ocmChargers
                DataSource.BOTH -> s.chargers + s.ocmChargers
            }
            var list = allChargers
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
                    Log.w("ChargerViewModel", "connector filter '${s.connectorFilter}' removed all results. Sample types: ${allChargers.take(3).map { it.connectorTypes }}")
                }
            }

            s.minPriceKwh?.let { min -> list = list.filter { (it.pricePerKwh ?: 0.0) >= min } }
            s.maxPriceKwh?.let { max -> list = list.filter { (it.pricePerKwh ?: 0.0) <= max } }

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
        _state.update { it.copy(isLoadingEv = true, error = null, ocmError = null, searchQuery = name, chargers = emptyList(), ocmChargers = emptyList()) }
        searchJob = viewModelScope.launch {
            val coords = repository.geocode(name)
            if (coords == null) {
                _state.update { it.copy(isLoadingEv = false, error = "Location not found: $name") }
                return@launch
            }
            _state.update { it.copy(searchLat = coords.first, searchLng = coords.second, savedMapCenterLat = coords.first, savedMapCenterLng = coords.second, savedMapZoom = 12.0) }
            detectCurrency(coords.first, coords.second)
            _navigateToResults.tryEmit(Unit)
            val radius = _state.value.searchRadiusMiles
            doSearch(coords.first, coords.second, socketGroups, radius)
            if (_state.value.dataSource != DataSource.ELECTROVERSE) {
                doOcmSearch(coords.first, coords.second, radius)
            }
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
        _state.update {
            it.copy(
                isLoadingEv = true, error = null, ocmError = null,
                searchQuery = label ?: "%.4f, %.4f".format(lat, lng),
                searchLat = lat, searchLng = lng,
                savedMapCenterLat = lat, savedMapCenterLng = lng, savedMapZoom = 12.0,
                chargers = emptyList(),
                ocmChargers = emptyList()
            )
        }
        detectCurrency(lat, lng)
        _navigateToResults.tryEmit(Unit)
        searchJob = viewModelScope.launch {
            val radius = _state.value.searchRadiusMiles
            doSearch(lat, lng, socketGroups, radius)
            if (_state.value.dataSource != DataSource.ELECTROVERSE) {
                doOcmSearch(lat, lng, radius)
            }
        }
    }

    private suspend fun doSearch(lat: Double, lng: Double, socketGroups: List<String>, radiusMiles: Int) {
        var lastStatus = ""
        repository.searchChargers(
            lat, lng, socketGroups = socketGroups, radiusMiles = radiusMiles,
            onStatus = { status, progress ->
                Log.d("ChargerViewModel", "onStatus: progress=$progress status=$status")
                lastStatus = status
                _state.update { it.copy(loadingStatus = status, fetchProgress = progress) }
            }
        )
            .catch { e ->
                Log.e("ChargerViewModel", "catch: ${e.message}")
                _state.update { it.copy(isLoadingEv = false, error = e.message ?: "Search failed") }
            }
            .onCompletion {
                Log.d("ChargerViewModel", "onCompletion: chargers=${_state.value.chargers.size} lastStatus=$lastStatus")
                _state.update { s ->
                    s.copy(
                        isLoadingEv = false,
                        fetchProgress = 0f,
                        loadingStatus = if (s.chargers.isNotEmpty()) "" else lastStatus,
                        error = if (s.chargers.isEmpty() && s.error == null) "No chargers found within ${s.searchRadiusMiles} miles" else s.error
                    )
                }
                Log.d("ChargerViewModel", "onCompletion: state updated, loadingStatus=${_state.value.loadingStatus}")
            }
            .collect { charger ->
                Log.d("ChargerViewModel", "collect: ${charger.name} stale=${charger.isStale}")
                _state.update { state ->
                    val idx = state.chargers.indexOfFirst { it.pk == charger.pk }
                    if (idx >= 0) {
                        state.copy(chargers = state.chargers.toMutableList().also { it[idx] = charger })
                    } else {
                        state.copy(chargers = state.chargers + charger)
                    }
                }
            }
    }

    private fun doOcmSearch(lat: Double, lng: Double, radiusMiles: Int) {
        viewModelScope.launch {
            _state.update { it.copy(isLoadingOcm = true) }
            try {
                val results = ocmRepository.searchNearby(lat, lng, appPreferences.ocmApiKey, radiusMiles)
                _state.update { it.copy(isLoadingOcm = false, ocmChargers = results) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingOcm = false, ocmError = e.message ?: "OCM search failed") }
            }
        }
    }

    fun setDataSource(source: DataSource) {
        appPreferences.dataSource = source
        _state.update { it.copy(dataSource = source) }
    }

    fun setSearchRadius(miles: Int) {
        appPreferences.searchRadiusMiles = miles
        _state.update { it.copy(searchRadiusMiles = miles) }
    }

    fun setThemeMode(mode: ThemeMode) {
        appPreferences.themeMode = mode
        _state.update { it.copy(themeMode = mode) }
    }

    fun saveMapPosition(zoom: Double, lat: Double, lng: Double) {
        appPreferences.mapZoom = zoom
        appPreferences.mapCenterLat = lat
        appPreferences.mapCenterLng = lng
        _state.update { it.copy(savedMapZoom = zoom, savedMapCenterLat = lat, savedMapCenterLng = lng) }
    }

    fun setSortOrder(order: SortOrder) {
        _state.update { it.copy(sortOrder = order) }
    }

    fun setSpeedFilter(filter: SpeedFilter) {
        _state.update { it.copy(speedFilter = filter) }
    }

    fun setPriceFilter(min: Double?, max: Double?) {
        _state.update { it.copy(minPriceKwh = min, maxPriceKwh = max) }
    }

    fun setConnectorFilter(connector: String) {
        _state.update { it.copy(connectorFilter = connector) }
    }

    fun clearResults() {
        searchJob?.cancel()
        _state.update { it.copy(chargers = emptyList(), ocmChargers = emptyList(), error = null, isLoadingEv = false, isLoadingOcm = false) }
    }

    fun detectCurrency(lat: Double, lng: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION")
                val address = Geocoder(application).getFromLocation(lat, lng, 1)?.firstOrNull()
                    ?: return@launch
                val symbol = currencySymbolForCountry(address.countryCode ?: return@launch)
                _state.update { it.copy(currencySymbol = symbol) }
            } catch (e: Exception) {
                Log.w("ChargerViewModel", "Currency detection failed: ${e.message}")
            }
        }
    }

    private fun currencySymbolForCountry(countryCode: String): String = try {
        when (java.util.Currency.getInstance(java.util.Locale("", countryCode)).currencyCode) {
            "EUR" -> "€"
            "GBP" -> "£"
            "USD" -> "$"
            "CHF" -> "CHF"
            "NOK", "SEK", "DKK" -> "kr"
            "PLN" -> "zł"
            "CZK" -> "Kč"
            "HUF" -> "Ft"
            "RON" -> "lei"
            else -> java.util.Currency.getInstance(java.util.Locale("", countryCode))
                .getSymbol(java.util.Locale.getDefault())
        }
    } catch (e: Exception) { "€" }
}
