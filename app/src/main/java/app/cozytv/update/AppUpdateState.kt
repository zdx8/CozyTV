package app.cozytv.update

import java.io.File

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class UpToDate(val remoteVersion: String) : AppUpdateState
    data class Available(val info: UpdateInfo) : AppUpdateState
    data class Downloading(val info: UpdateInfo, val percent: Int) : AppUpdateState
    data class Ready(
        val info: UpdateInfo,
        val file: File,
        val permissionRequired: Boolean = false,
    ) : AppUpdateState
    data class Failed(val message: String) : AppUpdateState
}
