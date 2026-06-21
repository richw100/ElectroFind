package com.richwatson.electrofind.viewmodel

import android.app.Application
import android.location.Geocoder
import com.richwatson.electrofind.model.CarProfile
import com.richwatson.electrofind.repository.CarProfileRepository
import com.richwatson.electrofind.util.KonaChargeCurve
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import com.richwatson.electrofind.api.models.ChargingLocation
import com.richwatson.electrofind.api.models.LocationSuggestion
import com.richwatson.electrofind.preferences.AppPreferences
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

enum class SortOrder { PRICE_ASC, PRICE_DESC, SPEED_DESC, OPTIMAL_COST_ASC, STAY_COST_ASC }
enum class SpeedFilter { ALL, FAST, RAPID, ULTRA }
enum class ThemeMode { LIGHT, DARK, SYSTEM }

data class SearchHistoryEntry(val label: String, val lat: Double, val lng: Double)

data class SearchState(
    val isLoadingEv: Boolean = false,
    val chargers: List<ChargingLocation> = emptyList(),
    val error: String? = null,
    val searchQuery: String = "",
    val searchLat: Double = 0.0,
    val searchLng: Double = 0.0,
    val sortOrder: SortOrder = SortOrder.PRICE_ASC,
    val speedFilter: SpeedFilter = SpeedFilter.ALL,
    val connectorFilters: Set<String> = emptySet(),
    val maxSpeedKw: Double? = null,
    val minPriceKwh: Double? = null,
    val maxPriceKwh: Double? = null,
    val minOptimalCost: Double? = null,
    val maxOptimalCost: Double? = null,
    val minStayCost: Double? = null,
    val maxStayCost: Double? = null,
    val currencySymbol: String = "€",
    val loadingStatus: String = "",
    val fetchProgress: Float = 0f,
    val searchRadiusMiles: Int = 3,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val savedMapZoom: Double = 14.0,
    val savedMapCenterLat: Double = 0.0,
    val savedMapCenterLng: Double = 0.0,
    val startSocPercent: Int = 20,
    val targetSocPercent: Int = 80,
    val stayMinutes: Int = 30,
    val searchHistory: List<SearchHistoryEntry> = emptyList(),
    val selectedChargerPk: Long? = null,
    val activeProfile: CarProfile = CarProfile.KONA_LR,
    val profiles: List<CarProfile> = listOf(CarProfile.KONA_LR),
    val favouritePks: Set<Long> = emptySet(),
    val excludedPks: Set<Long> = emptySet(),
    val showOnlyFavourites: Boolean = false,
    val hideExcluded: Boolean = false,
    val favouriteChargers: List<ChargingLocation> = emptyList()
) {
    val isLoading: Boolean get() = isLoadingEv
}

class ChargerViewModel(
    private val repository: ChargerRepository,
    private val appPreferences: AppPreferences,
    private val application: Application,
    private val carProfileRepository: CarProfileRepository
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
        val savedProfiles = carProfileRepository.loadAll()
        val allProfiles = listOf(CarProfile.KONA_LR) + savedProfiles
        val activeId = appPreferences.activeProfileId
        val activeProfile = allProfiles.find { it.id == activeId } ?: CarProfile.KONA_LR
        _state.update {
            it.copy(
                searchRadiusMiles = appPreferences.searchRadiusMiles,
                themeMode = appPreferences.themeMode,
                savedMapZoom = appPreferences.mapZoom,
                savedMapCenterLat = appPreferences.mapCenterLat,
                savedMapCenterLng = appPreferences.mapCenterLng,
                startSocPercent = appPreferences.startSocPercent,
                targetSocPercent = appPreferences.targetSocPercent,
                stayMinutes = appPreferences.stayMinutes,
                searchHistory = loadSearchHistory(),
                profiles = allProfiles,
                activeProfile = activeProfile,
                favouritePks = appPreferences.favouritePks,
                excludedPks = appPreferences.excludedPks
            )
        }
        loadFavouriteChargers(appPreferences.favouritePks)
    }

    val filteredSortedChargers: List<ChargingLocation>
        get() {
            val s = _state.value
            var list = s.chargers
            if (s.showOnlyFavourites) list = list.filter { it.pk in s.favouritePks }
            if (s.hideExcluded) list = list.filter { it.pk !in s.excludedPks }
            list = when (s.speedFilter) {
                SpeedFilter.ALL -> list
                SpeedFilter.FAST -> list.filter { it.maxKilowatts?.let { kw -> kw >= 7 } == true }
                SpeedFilter.RAPID -> list.filter { it.maxKilowatts?.let { kw -> kw >= 22 } == true }
                SpeedFilter.ULTRA -> list.filter { it.maxKilowatts?.let { kw -> kw >= 100 } == true }
            }
            s.maxSpeedKw?.let { max -> list = list.filter { (it.maxKilowatts ?: 0.0) <= max } }
            if (s.connectorFilters.isNotEmpty()) {
                list = list.filter { charger ->
                    charger.connectorTypes.any { ct ->
                        s.connectorFilters.any { f -> ct.contains(f, ignoreCase = true) }
                    }
                }
            }

            s.minPriceKwh?.let { min -> list = list.filter { (it.pricePerKwh ?: 0.0) >= min } }
            s.maxPriceKwh?.let { max -> list = list.filter { (it.pricePerKwh ?: 0.0) <= max } }
            s.minOptimalCost?.let { min -> list = list.filter { charger -> (charger.simCost(s, null) ?: 0.0) >= min } }
            s.maxOptimalCost?.let { max -> list = list.filter { charger -> (charger.simCost(s, null) ?: Double.MAX_VALUE) <= max } }
            s.minStayCost?.let { min -> list = list.filter { charger -> (charger.simCost(s, s.stayMinutes.toDouble()) ?: 0.0) >= min } }
            s.maxStayCost?.let { max -> list = list.filter { charger -> (charger.simCost(s, s.stayMinutes.toDouble()) ?: Double.MAX_VALUE) <= max } }

            list = when (s.sortOrder) {
                SortOrder.PRICE_ASC -> list.sortedBy { it.pricePerKwh ?: Double.MAX_VALUE }
                SortOrder.PRICE_DESC -> list.sortedByDescending { it.pricePerKwh ?: -1.0 }
                SortOrder.SPEED_DESC -> list.sortedByDescending { it.maxKilowatts ?: 0.0 }
                SortOrder.OPTIMAL_COST_ASC -> list.sortedBy { charger ->
                    charger.simCost(s, stayMinutes = null) ?: Double.MAX_VALUE
                }
                SortOrder.STAY_COST_ASC -> list.sortedBy { charger ->
                    charger.simCost(s, stayMinutes = s.stayMinutes.toDouble()) ?: Double.MAX_VALUE
                }
            }
            return list
        }

    fun searchByPlaceName(name: String, socketGroups: List<String> = listOf("CCS", "TYPE_2")) {
        if (name.isBlank()) return
        searchJob?.cancel()
        _state.update { it.copy(isLoadingEv = true, error = null, searchQuery = name, chargers = emptyList()) }
        searchJob = viewModelScope.launch {
            val coords = repository.geocode(name)
            if (coords == null) {
                _state.update { it.copy(isLoadingEv = false, error = "Location not found: $name") }
                return@launch
            }
            _state.update { it.copy(searchLat = coords.first, searchLng = coords.second, savedMapCenterLat = coords.first, savedMapCenterLng = coords.second, savedMapZoom = 12.0) }
            addToHistory(name, coords.first, coords.second)
            detectCurrency(coords.first, coords.second)
            _navigateToResults.tryEmit(Unit)
            val radius = _state.value.searchRadiusMiles
            doSearch(coords.first, coords.second, socketGroups, radius)
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

    fun searchByCoordinates(lat: Double, lng: Double, label: String? = null, socketGroups: List<String> = listOf("CCS", "TYPE_2"), isNearMe: Boolean = false, reverseGeocodePrefix: String? = null) {
        val prefix = if (isNearMe) "Near me" else reverseGeocodePrefix
        val immediateLabel = label ?: prefix ?: "%.4f, %.4f".format(lat, lng)
        searchJob?.cancel()
        _state.update {
            it.copy(
                isLoadingEv = true, error = null,
                searchQuery = immediateLabel,
                searchLat = lat, searchLng = lng,
                savedMapCenterLat = lat, savedMapCenterLng = lng, savedMapZoom = 12.0,
                chargers = emptyList()
            )
        }
        addToHistory(immediateLabel, lat, lng)
        if (prefix != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    val address = Geocoder(application).getFromLocation(lat, lng, 1)?.firstOrNull()
                    val town = address?.locality ?: address?.subLocality ?: address?.subAdminArea ?: address?.adminArea
                    if (town != null) updateHistoryLabel(immediateLabel, "$prefix · $town", lat, lng)
                } catch (_: Exception) { }
            }
        }
        detectCurrency(lat, lng)
        _navigateToResults.tryEmit(Unit)
        searchJob = viewModelScope.launch {
            val radius = _state.value.searchRadiusMiles
            doSearch(lat, lng, socketGroups, radius)
        }
    }

    private suspend fun doSearch(lat: Double, lng: Double, socketGroups: List<String>, radiusMiles: Int) {
        var lastStatus = ""
        repository.searchChargers(
            lat, lng, socketGroups = socketGroups, radiusMiles = radiusMiles,
            onStatus = { status, progress ->
                lastStatus = status
                _state.update { it.copy(loadingStatus = status, fetchProgress = progress) }
            }
        )
            .catch { e ->
                _state.update { it.copy(isLoadingEv = false, error = e.message ?: "Search failed") }
            }
            .onCompletion {
                _state.update { s ->
                    s.copy(
                        isLoadingEv = false,
                        fetchProgress = 0f,
                        loadingStatus = if (s.chargers.isNotEmpty()) "" else lastStatus,
                        error = if (s.chargers.isEmpty() && s.error == null) "No chargers found within ${s.searchRadiusMiles} miles" else s.error
                    )
                }
            }
            .collect { charger ->
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

    fun setOptimalCostFilter(min: Double?, max: Double?) {
        _state.update { it.copy(minOptimalCost = min, maxOptimalCost = max) }
    }

    fun setStayCostFilter(min: Double?, max: Double?) {
        _state.update { it.copy(minStayCost = min, maxStayCost = max) }
    }

    fun optimalCostFor(charger: ChargingLocation): Double? = charger.simCost(_state.value, null)
    fun stayCostFor(charger: ChargingLocation): Double? = charger.simCost(_state.value, _state.value.stayMinutes.toDouble())

    fun setMaxSpeedFilter(kw: Double?) {
        _state.update { it.copy(maxSpeedKw = kw) }
    }

    fun toggleFavourite(pk: Long) {
        val current = _state.value.favouritePks.toMutableSet()
        if (pk in current) current.remove(pk) else current.add(pk)
        appPreferences.favouritePks = current
        _state.update { it.copy(favouritePks = current) }
        loadFavouriteChargers(current)
    }

    fun toggleExcluded(pk: Long) {
        val currentExcluded = _state.value.excludedPks.toMutableSet()
        if (pk in currentExcluded) {
            currentExcluded.remove(pk)
        } else {
            currentExcluded.add(pk)
            val currentFavs = _state.value.favouritePks.toMutableSet().also { it.remove(pk) }
            appPreferences.favouritePks = currentFavs
            _state.update { it.copy(favouritePks = currentFavs) }
            loadFavouriteChargers(currentFavs)
        }
        appPreferences.excludedPks = currentExcluded
        _state.update { it.copy(excludedPks = currentExcluded) }
    }

    private fun loadFavouriteChargers(pks: Set<Long>) {
        viewModelScope.launch {
            if (pks.isEmpty()) {
                _state.update { it.copy(favouriteChargers = emptyList()) }
                return@launch
            }
            val chargers = repository.getChargersByPks(pks)
            _state.update { it.copy(favouriteChargers = chargers) }
        }
    }

    fun setShowOnlyFavourites(on: Boolean) {
        _state.update { it.copy(showOnlyFavourites = on) }
    }

    fun setHideExcluded(on: Boolean) {
        _state.update { it.copy(hideExcluded = on) }
    }

    fun toggleConnectorFilter(connector: String) {
        _state.update { s ->
            val updated = s.connectorFilters.toMutableSet()
            if (connector in updated) updated.remove(connector) else updated.add(connector)
            s.copy(connectorFilters = updated)
        }
    }

    fun selectCharger(pk: Long?) {
        _state.update { it.copy(selectedChargerPk = pk) }
    }

    fun setChargeSession(startSoc: Int, targetSoc: Int, stayMinutes: Int) {
        appPreferences.startSocPercent = startSoc
        appPreferences.targetSocPercent = targetSoc
        appPreferences.stayMinutes = stayMinutes
        _state.update { it.copy(startSocPercent = startSoc, targetSocPercent = targetSoc, stayMinutes = stayMinutes) }
    }

    fun clearResults() {
        searchJob?.cancel()
        _state.update { it.copy(chargers = emptyList(), error = null, isLoadingEv = false) }
    }

    fun loadProfiles() {
        val saved = carProfileRepository.loadAll()
        val all = listOf(CarProfile.KONA_LR) + saved
        val activeId = appPreferences.activeProfileId
        val active = all.find { it.id == activeId } ?: CarProfile.KONA_LR
        _state.update { it.copy(profiles = all, activeProfile = active) }
    }

    fun setActiveProfile(id: String) {
        val profile = _state.value.profiles.find { it.id == id } ?: return
        appPreferences.activeProfileId = id
        _state.update { it.copy(activeProfile = profile) }
    }

    fun saveProfile(profile: CarProfile) {
        carProfileRepository.save(profile)
        loadProfiles()
        setActiveProfile(profile.id)
    }

    fun deleteProfile(id: String) {
        if (id == CarProfile.KONA_LR_ID) return
        carProfileRepository.delete(id)
        loadProfiles()
        if (appPreferences.activeProfileId == id) setActiveProfile(CarProfile.KONA_LR_ID)
    }

    private fun loadSearchHistory(): List<SearchHistoryEntry> {
        val raw = appPreferences.rawSearchHistory
        if (raw.isEmpty()) return emptyList()
        return raw.split("\n").mapNotNull { line ->
            val parts = line.split("\t")
            if (parts.size == 3) try {
                SearchHistoryEntry(parts[0], parts[1].toDouble(), parts[2].toDouble())
            } catch (e: Exception) { null } else null
        }
    }

    private fun addToHistory(label: String, lat: Double, lng: Double) {
        val current = loadSearchHistory().toMutableList()
        current.removeAll { it.label.equals(label, ignoreCase = true) }
        current.add(0, SearchHistoryEntry(label, lat, lng))
        val trimmed = current.take(10)
        appPreferences.rawSearchHistory = trimmed.joinToString("\n") { "${it.label}\t${it.lat}\t${it.lng}" }
        _state.update { it.copy(searchHistory = trimmed) }
    }

    private fun updateHistoryLabel(oldLabel: String, newLabel: String, lat: Double, lng: Double) {
        val current = loadSearchHistory().toMutableList()
        val idx = current.indexOfFirst { it.label == oldLabel && it.lat == lat && it.lng == lng }
        if (idx >= 0) {
            current[idx] = SearchHistoryEntry(newLabel, lat, lng)
            appPreferences.rawSearchHistory = current.joinToString("\n") { "${it.label}\t${it.lat}\t${it.lng}" }
            _state.update { it.copy(searchHistory = current) }
        }
    }

    fun removeFromHistory(label: String) {
        val current = loadSearchHistory().toMutableList()
        current.removeAll { it.label == label }
        appPreferences.rawSearchHistory = current.joinToString("\n") { "${it.label}\t${it.lat}\t${it.lng}" }
        _state.update { it.copy(searchHistory = current) }
    }

    fun detectCurrency(lat: Double, lng: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION")
                val address = Geocoder(application).getFromLocation(lat, lng, 1)?.firstOrNull()
                    ?: return@launch
                val symbol = currencySymbolForCountry(address.countryCode ?: return@launch)
                _state.update { it.copy(currencySymbol = symbol) }
            } catch (_: Exception) {
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

private fun ChargingLocation.simCost(state: SearchState, stayMinutes: Double?): Double? {
    val kw = maxKilowatts ?: return null
    val price = pricePerKwh ?: return null
    val result = KonaChargeCurve.simulate(
        state.startSocPercent.toFloat(),
        state.targetSocPercent.toFloat(),
        kw,
        stayMinutes,
        profile = state.activeProfile
    )
    return KonaChargeCurve.totalCost(
        result = result,
        pricePerKwh = price,
        connectionFee = connectionFeeMajor ?: 0.0,
        chargingRatePerMin = chargingTimeRateMajor ?: 0.0,
        parkingRatePerMin = parkingTimeRateMajor ?: 0.0,
        stayMinutes = stayMinutes ?: result.chargeMinutes
    )
}
