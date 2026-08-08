package com.jacksonkasi.cliplex.ui.navigation

sealed interface ClipLexDestination {
 val route: String
 val title: String

 data object Onboarding : ClipLexDestination {
 override val route = "onboarding"
 override val title = "Onboarding"
 }

 data object Home : ClipLexDestination {
 override val route = "home"
 override val title = "Home"
 }
}
