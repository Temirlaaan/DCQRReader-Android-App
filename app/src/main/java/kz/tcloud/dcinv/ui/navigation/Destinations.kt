package kz.tcloud.dcinv.ui.navigation

/**
 * Navigation route keys. Kept as plain constants for now; will grow into a typed
 * graph as auth / scan / device verticals land.
 */
object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val RACKS = "racks"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val SCANNER = "scanner"

    /** Top-level destinations that show the bottom-nav island. */
    val TOP_LEVEL = setOf(HOME, RACKS, PROFILE, SETTINGS)

    const val RACK_ARG = "rackId"
    const val RACK_DETAIL = "rack/{$RACK_ARG}"
    fun rackDetail(rackId: Int): String = "rack/$rackId"

    const val QR_ARG = "qrId"
    const val QR_RESULT = "qr/{$QR_ARG}"

    fun qrResult(qrId: String): String = "qr/$qrId"

    const val BIND = "bind/{$QR_ARG}"
    fun bind(qrId: String): String = "bind/$qrId"

    const val REBIND = "rebind/{$QR_ARG}"
    fun rebind(qrId: String): String = "rebind/$qrId"

    const val CREATE_DEVICE = "qr/{$QR_ARG}/create"
    fun createDevice(qrId: String): String = "qr/$qrId/create"

    const val DEVICE_ARG = "deviceId"
    const val EDIT_DEVICE = "device/{$DEVICE_ARG}/edit"
    fun editDevice(deviceId: Int): String = "device/$deviceId/edit"

    const val DEVICE_DETAIL = "device/{$DEVICE_ARG}"
    fun deviceDetail(deviceId: Int): String = "device/$deviceId"

    /** savedStateHandle key: edit screen → QR result screen "please reload". */
    const val RESULT_RELOAD = "result_reload"
}
