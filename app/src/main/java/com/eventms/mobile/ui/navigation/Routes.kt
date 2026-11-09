package com.eventms.mobile.ui.navigation

object Routes {

    // Attendee
    const val EXPLORE = "explore"
    const val MY_TICKETS = "tickets"
    const val ACCOUNT = "account"

    const val EVENT_DETAIL = "event/{eventId}"
    const val CHECKOUT = "checkout/{eventId}"
    const val TICKET_DETAIL = "ticket/{bookingId}"

    // Organizer
    const val ORG_DASHBOARD = "organizer/dashboard"
    const val ORG_EVENTS = "organizer/events"
    const val ORG_SCAN_PICKER = "organizer/scan"

    const val ORG_EVENT_EDITOR = "organizer/editor/{eventId}"
    const val ORG_ATTENDEES = "organizer/attendees/{eventId}"
    const val ORG_SCANNER = "organizer/scanner/{eventId}"

    // Shared
    const val SIGN_IN = "signin"
    const val SETTINGS = "settings"

    fun eventDetail(eventId: Long) = "event/$eventId"
    fun checkout(eventId: Long) = "checkout/$eventId"
    fun ticketDetail(bookingId: String) = "ticket/$bookingId"

    /** [NEW_EVENT_ID] opens the editor on a blank event. */
    fun eventEditor(eventId: Long) = "organizer/editor/$eventId"
    fun attendees(eventId: Long) = "organizer/attendees/$eventId"
    fun scanner(eventId: Long) = "organizer/scanner/$eventId"

    const val NEW_EVENT_ID = -1L
    const val ARG_EVENT_ID = "eventId"
    const val ARG_BOOKING_ID = "bookingId"
}
