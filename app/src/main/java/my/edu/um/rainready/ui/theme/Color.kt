package my.edu.um.rainready.ui.theme

import androidx.compose.ui.graphics.Color

// Colours from the RainReady Figma file (colour variables "m3/..." and "zone/...").
// Text on every container colour below is at least 4.5:1 contrast.

// Material 3 roles
val Primary = Color(0xFF2F5E9E)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFD6E3FF)
val OnPrimaryContainer = Color(0xFF001B3E)
val Secondary = Color(0xFF565E71)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFDAE2F9)
val OnSecondaryContainer = Color(0xFF131C2B)
val Surface = Color(0xFFF9F9FF)
val OnSurface = Color(0xFF191C20)
val SurfaceVariant = Color(0xFFE0E2EC)
val OnSurfaceVariant = Color(0xFF44474E)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF3F3FA)
val SurfaceContainer = Color(0xFFEDEDF4)
val SurfaceContainerHigh = Color(0xFFE7E8EE)
val SurfaceContainerHighest = Color(0xFFE2E2E9)
val InverseSurface = Color(0xFF2E3036)
val InverseOnSurface = Color(0xFFF0F0F7)
val Outline = Color(0xFF74777F)
val OutlineVariant = Color(0xFFC4C6D0)
val Error = Color(0xFFBA1A1A)
val OnError = Color(0xFFFFFFFF)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF410002)

// Zone status colours. A status is always shown with its symbol and word too, never by colour alone.
val ZoneClear = Color(0xFF1E7A34)
val ZoneClearContainer = Color(0xFFD7F3DC)
val OnZoneClearContainer = Color(0xFF0B3D18)

val ZoneCaution = Color(0xFFF2B600)
val ZoneCautionOutline = Color(0xFF5C4300)
val ZoneCautionContainer = Color(0xFFFFEFC2)
val OnZoneCautionContainer = Color(0xFF4A3600)

val ZoneFlooded = Color(0xFFBA1A1A)
val ZoneFloodedContainer = Color(0xFFFFDAD6)
val OnZoneFloodedContainer = Color(0xFF410002)

// Report pins on the map
val PinUnconfirmed = Color(0xFF5F6368)
val PinConfirmed = ZoneFlooded
