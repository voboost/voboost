package ru.voboost.ui.panels

import ru.voboost.config.models.DriveMode
import ru.voboost.config.models.FuelMode
import ru.voboost.config.models.Tab
import ru.voboost.ui.components.Panel
import ru.voboost.ui.components.SelectOption
import ru.voboost.ui.components.panel
import ru.voboost.ui.ConfigViewModel

fun createVehiclePanel(
    @Suppress("UNUSED_PARAMETER") configViewModel: ConfigViewModel
): Panel {
    return panel(Tab.vehicle, "tab_vehicle") {
        section("fuel_section", "vehicle_fuel_mode") {
            select(
                id = "fuel_mode",
                label = "vehicle_fuel_mode",
                fieldPath = "vehicleFuelMode",
                options = FuelMode.values().map { SelectOption(it.name, "fuel_mode_${it.name}") },
                defaultValue = FuelMode.electric.name
            )
        }

        section("drive_section", "vehicle_drive_mode") {
            select(
                id = "drive_mode",
                label = "vehicle_drive_mode",
                fieldPath = "vehicleDriveMode",
                options = DriveMode.values().map { SelectOption(it.name, "drive_mode_${it.name}") },
                defaultValue = DriveMode.comfort.name
            )
        }
    }
}
