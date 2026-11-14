package io.github.offlinemobility.app

import io.github.offlinemobility.app.core.disaster.DisasterType
import io.github.offlinemobility.app.core.light.Lamp
import io.github.offlinemobility.app.core.light.LampSwitch
import io.github.offlinemobility.app.core.light.restoreOrder
import io.github.offlinemobility.app.ui.carNote
import io.github.offlinemobility.app.vehicle.CarBlackout
import io.github.offlinemobility.app.vehicle.SimulatedVehicleGateway
import io.github.offlinemobility.app.vehicle.VehicleGateway
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarStageTest {

    @Test
    fun blackoutSwitchesEveryLampOffAndPutsEachBack() = runBlocking {
        val car = SimulatedVehicleGateway()
        car.setLamp(Lamp.LOW_BEAM, LampSwitch.AUTOMATIC)
        car.setLamp(Lamp.TAIL_LAMP, LampSwitch.ON)
        val blackout = CarBlackout()

        assertEquals(0, blackout.darken(car))
        assertTrue(Lamp.entries.all { car.readLamp(it).getOrThrow() == LampSwitch.OFF })

        assertEquals(0, blackout.restore(car))
        assertEquals(LampSwitch.AUTOMATIC, car.readLamp(Lamp.LOW_BEAM).getOrThrow())
        assertEquals(LampSwitch.ON, car.readLamp(Lamp.TAIL_LAMP).getOrThrow())
        assertEquals(LampSwitch.OFF, car.readLamp(Lamp.HIGH_BEAM).getOrThrow())
        assertTrue(blackout.remembered.isEmpty())
    }

    @Test
    fun aRestartInTheDarkStillRestoresTheOriginalSwitches() = runBlocking {
        val car = SimulatedVehicleGateway()
        car.setLamp(Lamp.LOW_BEAM, LampSwitch.AUTOMATIC)
        val first = CarBlackout()
        first.darken(car)
        val saved = CarBlackout.encode(first.remembered)

        // The app stops and starts again while the lamps are off.
        val second = CarBlackout(CarBlackout.decode(saved))
        second.darken(car)
        second.restore(car)

        assertEquals(LampSwitch.AUTOMATIC, car.readLamp(Lamp.LOW_BEAM).getOrThrow())
    }

    /** Refuses lamp control while [driving], as the vehicle does outside P. */
    private class GearLockedCar(private val car: SimulatedVehicleGateway = SimulatedVehicleGateway()) : VehicleGateway by car {
        var driving = false

        override suspend fun setLamp(lamp: Lamp, value: LampSwitch): Result<Unit> =
            if (driving) Result.failure(IllegalStateException("driving")) else car.setLamp(lamp, value)
    }

    @Test
    fun lampsRefusedWhileDrivingAreRememberedAndPutBackLater() = runBlocking {
        val car = GearLockedCar()
        car.setLamp(Lamp.LOW_BEAM, LampSwitch.ON)
        val blackout = CarBlackout()
        blackout.darken(car)

        car.driving = true
        assertEquals(1, blackout.restore(car))
        assertEquals(mapOf(Lamp.LOW_BEAM to LampSwitch.ON), blackout.remembered)

        // Parked again with the blackout still on: going dark again must not forget the original.
        car.driving = false
        blackout.darken(car)
        assertEquals(0, blackout.restore(car))
        assertEquals(LampSwitch.ON, car.readLamp(Lamp.LOW_BEAM).getOrThrow())
        assertTrue(blackout.remembered.isEmpty())
    }

    @Test
    fun aCarOnLowBeamEndsOnLowBeamNotOnTheTailLampPosition() {
        // Tail lamp and low beam are one switch: whichever is written last wins.
        val order = restoreOrder(mapOf(Lamp.HIGH_BEAM to LampSwitch.OFF, Lamp.LOW_BEAM to LampSwitch.ON, Lamp.TAIL_LAMP to LampSwitch.ON))
        assertEquals(listOf(Lamp.HIGH_BEAM, Lamp.TAIL_LAMP, Lamp.LOW_BEAM), order.map { it.first })
        // Low beam was off and only the tail lamps were on: OFF goes first, so the tail lamps stay on.
        val parked = restoreOrder(mapOf(Lamp.TAIL_LAMP to LampSwitch.ON, Lamp.LOW_BEAM to LampSwitch.OFF))
        assertEquals(listOf(Lamp.LOW_BEAM, Lamp.TAIL_LAMP), parked.map { it.first })
    }

    @Test
    fun rememberedLampsSurviveBeingWrittenDown() {
        val lamps = mapOf(Lamp.LOW_BEAM to LampSwitch.AUTOMATIC, Lamp.TAIL_LAMP to LampSwitch.ON)
        assertEquals(lamps, CarBlackout.decode(CarBlackout.encode(lamps)))
        assertTrue(CarBlackout.decode(null).isEmpty())
        assertTrue(CarBlackout.decode("LOW_BEAM=SIDEWAYS,nonsense").isEmpty())
    }

    @Test
    fun theGuideSaysWhatTheCarDidOnlyForItsOwnStage() {
        assertEquals("차 불을 모두 껐어요", UiState(disaster = DisasterType.AIR_RAID, carDarkRefused = 0).carNote()?.text)
        assertTrue(UiState(disaster = DisasterType.AIR_RAID, carDarkRefused = 2).carNote()!!.failed)
        // Blackout switched on by hand during another stage: that guide does not mention lamps.
        assertNull(UiState(disaster = DisasterType.EVACUATION, carDarkRefused = 0).carNote())
        assertNull(UiState(disaster = DisasterType.AIR_RAID).carNote())

        fun sealed(windows: Int?, air: Int?) = UiState(disaster = DisasterType.CBRN, carSeal = CabinSeal(windows, air)).carNote()
        assertEquals("창문을 닫고 바깥 공기를 막았어요", sealed(0, 0)?.text)
        assertEquals("창문을 닫았어요", sealed(0, null)?.text)
        assertEquals("공조로 들어오는 바깥 공기를 막았어요", sealed(null, 0)?.text)
        assertTrue(sealed(1, 0)!!.failed)
        assertNull(sealed(null, null))
        assertNull(UiState(disaster = DisasterType.ALERT, carSeal = CabinSeal(0, 0)).carNote())
    }

    @Test
    fun sealingIsOnlyReportedWhenTheCarDidSomething() {
        assertTrue(CabinSeal(0, null).done)
        assertTrue(!CabinSeal(null, null).done)
        assertTrue(!CabinSeal(0, 1).done)
    }
}
