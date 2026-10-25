package com.skofqq.shieldy.data

/** What the app was able to learn about the module, in order of severity. */
sealed interface ModuleState {

    data object Loading : ModuleState

    /** Root was refused, or no root manager answered. */
    data object NoRoot : ModuleState

    /** `dnscrypt-ctl` is nowhere to be found and the module directory is absent. */
    data object ModuleMissing : ModuleState

    /** The module is installed but switched off in Magisk, so nothing is mounted. */
    data object ModuleDisabled : ModuleState

    /** Installed and enabled, but too old to answer `dnscrypt-ctl json`. */
    data class Outdated(val version: String?) : ModuleState

    data class Ready(
        val status: ModuleStatus,
        val uptimeSeconds: Long?,
    ) : ModuleState
}
