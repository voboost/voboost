package ru.voboost.ui.panels

import ru.voboost.config.models.DriveMode
import ru.voboost.config.models.FuelMode
import ru.voboost.config.models.Tab
import ru.voboost.ui.ConfigState
import ru.voboost.ui.components.PanelDefinition
import ru.voboost.ui.components.RadioButton
import ru.voboost.ui.components.panel

fun createVehiclePanel(
    @Suppress("UNUSED_PARAMETER") configState: ConfigState,
): PanelDefinition {
    return panel(Tab.vehicle, "tab_vehicle") {
        section("fuel_section", "vehicle_fuel_mode") {
            radio(
                id = "fuel_mode",
                fieldPath = "vehicleFuelMode",
                options = FuelMode.values().map { RadioButton("fuel_mode_${it.name}", it.name) },
                defaultValue = FuelMode.electric.name,
            )
        }

        section("drive_section", "vehicle_drive_mode") {
            radio(
                id = "drive_mode",
                fieldPath = "vehicleDriveMode",
                options = DriveMode.values().map { RadioButton("drive_mode_${it.name}", it.name) },
                defaultValue = DriveMode.comfort.name,
            )
        }
    }
}
