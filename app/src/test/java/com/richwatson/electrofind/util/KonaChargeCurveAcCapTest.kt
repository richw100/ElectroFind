package com.richwatson.electrofind.util

import com.richwatson.electrofind.model.CarProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KonaChargeCurveAcCapTest {

    private val kona = CarProfile.KONA_LR
    private val noAcCap = kona.copy(id = "no_ac_cap", maxAcKw = null)

    @Test
    fun isAcConnector_classifiesCommonTypes() {
        assertTrue(KonaChargeCurve.isAcConnector("Type 2", 22.0))
        assertTrue(KonaChargeCurve.isAcConnector("TYPE_2", 43.0))
        assertTrue(KonaChargeCurve.isAcConnector("Type 1", 7.4))
        assertFalse(KonaChargeCurve.isAcConnector("CCS", 50.0))
        assertFalse(KonaChargeCurve.isAcConnector("CHAdeMO", 50.0))
        assertFalse(KonaChargeCurve.isAcConnector("CCS Type 2", 150.0))
        // Unknown type falls back to the power rating.
        assertTrue(KonaChargeCurve.isAcConnector(null, 11.0))
        assertFalse(KonaChargeCurve.isAcConnector(null, 60.0))
    }

    @Test
    fun acConnector_isCappedAtOnBoardChargerLimit() {
        // A 22 kW AC connector: the Kona's 11 kW OBC should roughly halve the rate, so the
        // session takes about twice as long as an unconstrained 22 kW simulation would.
        val capped = KonaChargeCurve.simulate(20f, 80f, 22.0, connectorType = "Type 2", profile = kona)
        val uncapped = KonaChargeCurve.simulate(20f, 80f, 22.0, connectorType = "Type 2", profile = noAcCap)

        assertEquals(capped.energyKwh, uncapped.energyKwh, 0.5)
        assertTrue(
            "expected capped time (${capped.chargeMinutes}) ~2x uncapped (${uncapped.chargeMinutes})",
            capped.chargeMinutes > uncapped.chargeMinutes * 1.7
        )
    }

    @Test
    fun dcConnector_ignoresAcCap() {
        val withCap = KonaChargeCurve.simulate(20f, 80f, 50.0, connectorType = "CCS", profile = kona)
        val withoutCap = KonaChargeCurve.simulate(20f, 80f, 50.0, connectorType = "CCS", profile = noAcCap)
        assertEquals(withoutCap.chargeMinutes, withCap.chargeMinutes, 0.001)
    }

    @Test
    fun acConnector_usesAcEfficiency_evenAt22kW() {
        // Same 22 kW rating, no AC cap on the profile: the Type 2 (AC) run should still be
        // slower than the CCS (DC) run because AC charging is modelled as less efficient.
        val ac = KonaChargeCurve.simulate(20f, 60f, 22.0, connectorType = "Type 2", profile = noAcCap)
        val dc = KonaChargeCurve.simulate(20f, 60f, 22.0, connectorType = "CCS", profile = noAcCap)
        assertTrue(
            "expected AC (${ac.chargeMinutes}) slower than DC (${dc.chargeMinutes})",
            ac.chargeMinutes > dc.chargeMinutes
        )
        assertTrue(ac.billedEnergyKwh > dc.billedEnergyKwh)
    }

    @Test
    fun acCap_neverSpeedsChargingUp() {
        // Cap above the connector rating must not increase power.
        val slowAc = KonaChargeCurve.simulate(20f, 80f, 7.4, connectorType = "Type 2", profile = kona)
        val slowAcNoCap = KonaChargeCurve.simulate(20f, 80f, 7.4, connectorType = "Type 2", profile = noAcCap)
        assertEquals(slowAcNoCap.chargeMinutes, slowAc.chargeMinutes, 0.001)
    }
}
