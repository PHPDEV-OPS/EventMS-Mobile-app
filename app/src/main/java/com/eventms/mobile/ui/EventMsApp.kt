package com.eventms.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eventms.mobile.data.prefs.Session
import com.eventms.mobile.data.repository.AuthRepository
import com.eventms.mobile.ui.navigation.Routes
import com.eventms.mobile.ui.screens.SplashScreen
import com.eventms.mobile.ui.screens.account.AccountScreen
import com.eventms.mobile.ui.screens.attendee.CheckoutScreen
import com.eventms.mobile.ui.screens.attendee.EventDetailScreen
import com.eventms.mobile.ui.screens.attendee.ExploreScreen
import com.eventms.mobile.ui.screens.attendee.MyTicketsScreen
import com.eventms.mobile.ui.screens.attendee.TicketDetailScreen
import com.eventms.mobile.ui.screens.auth.SignInScreen
import com.eventms.mobile.ui.screens.organizer.AttendeesScreen
import com.eventms.mobile.ui.screens.organizer.EventEditorScreen
import com.eventms.mobile.ui.screens.organizer.OrganizerDashboardScreen
import com.eventms.mobile.ui.screens.organizer.OrganizerEventsScreen
import com.eventms.mobile.ui.screens.organizer.ScanPickerScreen
import com.eventms.mobile.ui.screens.organizer.ScannerScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    /**
     * Null means the stored session has not been read yet — distinct from
     * "read, and nobody is signed in". Without that distinction the app would
     * flash the sign-in screen at someone who is already signed in.
     */
    val session: StateFlow<Session?> = authRepository.session.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    init {
        // Pick up a role granted elsewhere (or a session that has since been
        // revoked) rather than trusting what was cached on this device.
        viewModelScope.launch { authRepository.refreshProfile() }
    }
}

/** A tab in the bottom bar. */
private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val attendeeTabs = listOf(
    Tab(Routes.EXPLORE, "Explore", Icons.Outlined.Explore),
    Tab(Routes.MY_TICKETS, "Tickets", Icons.Outlined.ConfirmationNumber),
    Tab(Routes.ACCOUNT, "Account", Icons.Outlined.AccountCircle)
)

private val organizerTabs = listOf(
    Tab(Routes.ORG_DASHBOARD, "Dashboard", Icons.Outlined.Dashboard),
    Tab(Routes.ORG_EVENTS, "Events", Icons.Outlined.Event),
    Tab(Routes.ORG_SCAN_PICKER, "Scan", Icons.Outlined.QrCodeScanner),
    Tab(Routes.EXPLORE, "Explore", Icons.Outlined.Explore),
    Tab(Routes.ACCOUNT, "Account", Icons.Outlined.AccountCircle)
)

@Composable
fun EventMsApp(
    viewModel: AppViewModel = hiltViewModel()
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // Still reading the stored session.
        val current = session ?: run {
            SplashScreen()
            return@Surface
        }

        // Every call needs a signed-in token, so there is nothing to show first.
        if (!current.signedIn) {
            SignInScreen(
                onSignedIn = { /* the session flow re-renders this into the app */ },
                onSkip = null
            )
            return@Surface
        }

        val tabs = if (current.isOrganizer) organizerTabs else attendeeTabs
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

        Scaffold(
            bottomBar = {
                // Detail screens own the full window; the bar is for the tabs.
                AnimatedVisibility(
                    visible = tabs.any { it.route == currentRoute },
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }
                ) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = backStackEntry?.destination?.hierarchy
                                ?.any { it.route == tab.route } == true

                            NavigationBarItem(
                                selected = selected,
                                onClick = { navController.switchTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = if (current.isOrganizer) {
                    Routes.ORG_DASHBOARD
                } else {
                    Routes.EXPLORE
                },
                modifier = Modifier.padding(padding)
            ) {
                // ----------------------------------------------- attendee

                composable(Routes.EXPLORE) {
                    ExploreScreen(
                        onEventClick = { navController.navigate(Routes.eventDetail(it)) }
                    )
                }

                composable(Routes.MY_TICKETS) {
                    MyTicketsScreen(
                        onTicketClick = { navController.navigate(Routes.ticketDetail(it)) },
                        onBrowseEvents = { navController.switchTab(Routes.EXPLORE) }
                    )
                }

                composable(
                    route = Routes.EVENT_DETAIL,
                    arguments = listOf(navArgument(Routes.ARG_EVENT_ID) {
                        type = NavType.LongType
                    })
                ) {
                    EventDetailScreen(
                        onBack = { navController.popBackStack() },
                        onBookTickets = { navController.navigate(Routes.checkout(it)) },
                        onEventClick = { navController.navigate(Routes.eventDetail(it)) }
                    )
                }

                composable(
                    route = Routes.CHECKOUT,
                    arguments = listOf(navArgument(Routes.ARG_EVENT_ID) {
                        type = NavType.LongType
                    })
                ) {
                    CheckoutScreen(
                        onBack = { navController.popBackStack() },
                        onViewTicket = { bookingId ->
                            navController.navigate(Routes.ticketDetail(bookingId)) {
                                // Coming back from a ticket should land on the
                                // tickets list, not back inside checkout.
                                popUpTo(Routes.EXPLORE)
                            }
                        }
                    )
                }

                composable(
                    route = Routes.TICKET_DETAIL,
                    arguments = listOf(navArgument(Routes.ARG_BOOKING_ID) {
                        type = NavType.StringType
                    })
                ) {
                    TicketDetailScreen(onBack = { navController.popBackStack() })
                }

                // ---------------------------------------------- organizer

                composable(Routes.ORG_DASHBOARD) {
                    OrganizerDashboardScreen(
                        onEventClick = { navController.navigate(Routes.eventEditor(it)) },
                        onCreateEvent = {
                            navController.navigate(Routes.eventEditor(Routes.NEW_EVENT_ID))
                        },
                        onScanTickets = { navController.switchTab(Routes.ORG_SCAN_PICKER) }
                    )
                }

                composable(Routes.ORG_EVENTS) {
                    OrganizerEventsScreen(
                        onEventClick = { navController.navigate(Routes.eventEditor(it)) },
                        onCreateEvent = {
                            navController.navigate(Routes.eventEditor(Routes.NEW_EVENT_ID))
                        },
                        // Organizers hold tickets of their own; same screen as attendees use.
                        onMyTickets = { navController.navigate(Routes.MY_TICKETS) }
                    )
                }

                composable(Routes.ORG_SCAN_PICKER) {
                    ScanPickerScreen(
                        onPickEvent = { navController.navigate(Routes.scanner(it)) }
                    )
                }

                composable(
                    route = Routes.ORG_EVENT_EDITOR,
                    arguments = listOf(navArgument(Routes.ARG_EVENT_ID) {
                        type = NavType.LongType
                    })
                ) { entry ->
                    val eventId = entry.arguments?.getLong(Routes.ARG_EVENT_ID)
                        ?: Routes.NEW_EVENT_ID

                    EventEditorScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                        onDeleted = { navController.popBackStack() },
                        onViewAttendees = { navController.navigate(Routes.attendees(it)) },
                        onScanTickets = { navController.navigate(Routes.scanner(it)) },
                        eventId = eventId
                    )
                }

                composable(
                    route = Routes.ORG_ATTENDEES,
                    arguments = listOf(navArgument(Routes.ARG_EVENT_ID) {
                        type = NavType.LongType
                    })
                ) {
                    AttendeesScreen(onBack = { navController.popBackStack() })
                }

                composable(
                    route = Routes.ORG_SCANNER,
                    arguments = listOf(navArgument(Routes.ARG_EVENT_ID) {
                        type = NavType.LongType
                    })
                ) {
                    ScannerScreen(onBack = { navController.popBackStack() })
                }

                // ------------------------------------------------- shared

                composable(Routes.ACCOUNT) {
                    AccountScreen(
                        onSignedOut = { /* the session flow returns us to sign-in */ }
                    )
                }
            }
        }
    }
}

/** Standard bottom-bar behaviour: single instance per tab, state preserved. */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
