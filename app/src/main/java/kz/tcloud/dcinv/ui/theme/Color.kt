package kz.tcloud.dcinv.ui.theme

import androidx.compose.ui.graphics.Color

// Brand: "Datacenter Blue". Single source of truth; AuthManager also reuses
// BrandPrimaryArgb for the Custom Tab toolbar so login matches the app.
const val BrandPrimaryArgb: Int = 0xFF1A57C7.toInt()

val Primary = Color(0xFF1A57C7)
val PrimaryDim = Color(0xFF1647A6)
val OnPrimary = Color(0xFFFFFFFF)

// TTC corporate green — muted/professional, not neon. Used as the secondary
// accent across the app (status, section icons, success, login ring).
val Accent = Color(0xFF1E9E57)
val OnAccent = Color(0xFFFFFFFF)
val AccentContainerLight = Color(0xFFD9F2E3)
val OnAccentContainerLight = Color(0xFF06311A)
val AccentContainerDark = Color(0xFF12492B)
val OnAccentContainerDark = Color(0xFFB6F0CC)

// Light scheme
val BackgroundLight = Color(0xFFF8F9FA)
val SurfaceLight = Color(0xFFFFFFFF) // cards
val OnSurfaceLight = Color(0xFF111111) // headings: bold + dark
val SecondaryTextLight = Color(0xFF6B7280) // gray-500
val BorderLight = Color(0xFFE2E5EA) // gray-200-ish: visible hairline on the off-white bg
val PrimaryContainerLight = Color(0xFFE8EFFC)
val OnPrimaryContainerLight = Color(0xFF0C2E66)

// Dark scheme
val BackgroundDark = Color(0xFF09090B)
val SurfaceDark = Color(0xFF111111) // cards
val OnSurfaceDark = Color(0xFFFAFAFA)
val SecondaryTextDark = Color(0xFF9CA3AF)
val BorderDark = Color(0x14FFFFFF) // border-white/5 (~8% alpha)
val PrimaryContainerDark = Color(0xFF12275A)
val OnPrimaryContainerDark = Color(0xFFD6E2FB)

val ErrorLight = Color(0xFFDC2626)
val ErrorDark = Color(0xFFF87171)
