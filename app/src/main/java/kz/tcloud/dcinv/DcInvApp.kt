package kz.tcloud.dcinv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hosts the Hilt dependency graph for the whole app.
 */
@HiltAndroidApp
class DcInvApp : Application()
