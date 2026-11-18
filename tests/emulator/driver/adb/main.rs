use std::{ffi::OsStr, path::PathBuf};

fn main() {
    let arguments = std::env::args_os().skip(1).collect::<Vec<_>>();
    if arguments.len() != 6
        || arguments[0] != OsStr::new("--emulator-check")
        || arguments[2] != OsStr::new("--device")
        || arguments[4] != OsStr::new("--candidate")
    {
        voboost_installer_lib::emulator_driver_error(
            "Usage: emulator-driver --emulator-check <json> --device <emulator> --candidate <installer>",
        );
    }
    let Some(device) = arguments[3].to_str() else {
        voboost_installer_lib::emulator_driver_error("the emulator endpoint must be UTF-8");
    };
    voboost_installer_lib::run_emulator_driver(
        PathBuf::from(&arguments[5]),
        PathBuf::from(&arguments[1]),
        device.into(),
    );
}

