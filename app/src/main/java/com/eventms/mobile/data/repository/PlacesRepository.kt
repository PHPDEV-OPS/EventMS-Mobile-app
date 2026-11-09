package com.eventms.mobile.data.repository

import com.eventms.mobile.core.Outcome
import com.google.android.gms.tasks.Task
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A venue the organizer can pick, as returned by Google Places. */
data class PlaceSuggestion(
    val placeId: String,
    /** The venue's name, e.g. "KICC". */
    val primaryText: String,
    /** Where it is, e.g. "Harambee Ave, Nairobi". */
    val secondaryText: String
) {
    val fullText: String
        get() = listOf(primaryText, secondaryText)
            .filter { it.isNotBlank() }
            .joinToString(", ")
}

/** A venue that has been chosen, with the coordinates Google holds for it. */
data class ResolvedPlace(
    val name: String,
    val address: String,
    val latitude: Double?,
    val longitude: Double?
) {
    /** What goes in `events.location`. */
    val displayLocation: String
        get() = when {
            address.isBlank() -> name
            address.startsWith(name) -> address
            else -> "$name, $address"
        }
}

/**
 * Venue lookup against Google Places.
 *
 * The point is not convenience typing — it is that an event's venue should be
 * somewhere that actually exists. Picking a prediction gives us Google's own
 * coordinates, so the map on the event page shows the real place rather than
 * whatever a geocoder made of a free-typed string.
 */
@Singleton
class PlacesRepository @Inject constructor(
    private val client: PlacesClient
) {

    /**
     * One token per typing session, reset once a place is chosen. Google bills
     * autocomplete per session rather than per keystroke, so reusing the token
     * across a session is what keeps this from being expensive.
     */
    private var sessionToken: AutocompleteSessionToken =
        AutocompleteSessionToken.newInstance()

    suspend fun search(query: String, countryCode: String? = "KE"): Outcome<List<PlaceSuggestion>> {
        if (query.trim().length < MIN_QUERY_LENGTH) return Outcome.Success(emptyList())

        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query.trim())
            .setSessionToken(sessionToken)
            .apply { if (countryCode != null) setCountries(countryCode) }
            .build()

        return runCatching {
            val response = client.findAutocompletePredictions(request).await()
            Outcome.Success(
                response.autocompletePredictions.take(MAX_SUGGESTIONS).map { prediction ->
                    PlaceSuggestion(
                        placeId = prediction.placeId,
                        primaryText = prediction.getPrimaryText(null).toString(),
                        secondaryText = prediction.getSecondaryText(null).toString()
                    )
                }
            )
        }.getOrElse {
            // Usually a missing Places API entitlement on the key, or no network.
            // Neither should block the organizer: the field still accepts typing.
            Outcome.Failure("Venue suggestions are unavailable right now")
        }
    }

    /** Fetches the coordinates behind a chosen suggestion. */
    suspend fun resolve(placeId: String): Outcome<ResolvedPlace> {
        val request = FetchPlaceRequest.builder(
            placeId,
            listOf(Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG)
        ).setSessionToken(sessionToken).build()

        return runCatching {
            val place = client.fetchPlace(request).await().place
            // The session ends when a place is picked; the next search starts a new one.
            sessionToken = AutocompleteSessionToken.newInstance()

            Outcome.Success(
                ResolvedPlace(
                    name = place.name.orEmpty(),
                    address = place.address.orEmpty(),
                    latitude = place.latLng?.latitude,
                    longitude = place.latLng?.longitude
                )
            )
        }.getOrElse {
            Outcome.Failure("That venue could not be looked up")
        }
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 3
        const val MAX_SUGGESTIONS = 5
    }
}

/** Bridges a Play Services [Task] into a coroutine. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { error -> continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
