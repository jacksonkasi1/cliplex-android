package com.learnthis.ui.navigation

sealed interface LearnThisDestination {
 val route: String
 val title: String

 data object Onboarding : LearnThisDestination {
 override val route = "onboarding"
 override val title = "Onboarding"
 }

 data object Home : LearnThisDestination {
 override val route = "home"
 override val title = "Home"
 }
}
