# EventMS Mobile

Android companion to the EventMS event-management system. Organizers create and
run events; attendees buy tickets and show a QR code at the gate, which the
organizer scans to admit them.

Built with **Kotlin**, **Jetpack Compose** (Material 3), **Retrofit**, **Room**
and **Hilt**.

## Running it

Open the project in Android Studio and press Run. Nothing else needs to be
started — no local server, no database — because the app's backend is Neon
itself.

On first launch you are asked to create an account. Pick **Organizer** to get the
dashboard, event editor and gate scanner; pick **Attendee** to browse and buy.
You can switch an attendee account to an organizer later from **Account**.

## Architecture

```
Android app (Kotlin, Compose)
  ├── Retrofit ──► Neon Auth (Better Auth)   accounts, JWT
  ├── Retrofit ──► Neon Data API (PostgREST) events, bookings, scanning
  ├── Retrofit ──► Neon Function `payments`  M-Pesa + card checkout
  ├── Retrofit ──► Neon Function `media`     cover image uploads
  └── Room                                    offline cache + scan queue

EventMS web app (Next.js) ──► Spring Boot ──► the same Neon Postgres
```

The app has **no application server of its own**. It reads and writes the Neon
database directly through the Data API, and the Spring backend behind the web app
writes the same tables — so an event created on the phone shows up on the web
dashboard, and a ticket bought on the web scans fine at the gate.

The logic that has to be transactional lives in SQL rather than in the client, in
`backend/src/main/resources/db/postgres/V2__mobile_data_api.sql`:

| Function | What it does |
|---|---|
| `api_ensure_profile()` | Creates or links the `users` row behind a Neon Auth token |
| `api_become_organizer()` | Grants `ROLE_ORGANIZER` |
| `api_create_booking(...)` | Locks the event row, checks availability, decrements it and issues the ticket number — all in one transaction |
| `api_confirm_booking_payment(id)` | Marks a booking paid and confirmed |
| `api_verify_ticket(code, event, checkIn)` | Verifies **and** admits in one statement, so a ticket cannot be admitted twice |
| `api_event_bookings(event)` | The guest list — refused unless you manage that event |
| `api_organizer_stats()` | Dashboard totals |

Events are read straight from the `events` table; row-level security decides what
is visible (published events to everyone signed in, plus your own drafts).

## Payments

Checkout offers two providers, both running on a Neon Function
(`neon/functions/payments`) rather than in the app. Merchant credentials are
function environment variables, so they never appear in the APK where they could
be extracted.

**M-Pesa** (the default) pushes a PIN prompt to the customer's handset via Daraja
STK Push — they never leave the app:

```
app ──POST /mpesa/initiate──► function ──► Daraja   → PIN prompt on the phone
     ◄─────────────────────── function ◄── callback → booking marked PAID
app ──POST /status──────────► function ──► Daraja   → confirms and syncs
```

**Card**, via Pesapal's hosted page, opened in a **Chrome Custom Tab**:

```
app ──POST /initiate──► function ──► Pesapal        → redirect URL
app ──(Custom Tab)────────────────► Pesapal page    → customer pays
     ◄──────────────── function ◄── Pesapal IPN     → booking marked PAID
app ──POST /status───► function ──► Pesapal         → confirms and syncs
```

The Custom Tab is deliberate, and not a WebView: card fields rendered in a view
this app owns would pull the whole app into PCI scope and mean a bug here could
read what someone types. A Custom Tab runs in the browser's own process, so the
app hands it a URL and nothing else, and the customer keeps the real address bar
and padlock.

It opens as a **partial** Custom Tab — a sheet covering 72% of the screen with
the checkout still visible above it, rounded at the top and themed in the app's
orange, so paying reads as a step inside the app rather than a jump out of it.
The sheet drags to full height when the card form needs the room. Chrome below
version 107 ignores the height and opens full screen, which is a clean
degradation. Closing it returns straight to checkout, where the payment is then
confirmed automatically without the customer tapping anything.

The browser is warmed up and the payment page pre-fetched while the customer is
still choosing a method, so the tab opens onto a rendered page rather than a
white rectangle.

`payments.provider` records which one took a given booking, so `/status` knows
whether to ask Daraja or Pesapal.

Two rules make this safe:

- **The app cannot mark its own booking paid.** `api_confirm_booking_payment` is
  revoked from the client roles (`V3__payments_lockdown.sql`); only the function,
  which connects as the table owner *after* verifying the charge with Pesapal,
  may settle a booking.
- **A ticket has no QR and no PDF until it is paid.** The ticket screen shows a
  locked placeholder while the booking is `PENDING`, and the download is refused.

A booking is held the moment it is made, so a customer can close the app and
finish paying later from **My tickets** — the ticket screen offers *Pay* and
*Check payment* until it clears. Free events are settled by the function itself,
based on the stored amount rather than anything the client sends.

Deploying or rotating credentials:

```bash
neon functions deploy payments --src neon/functions/payments --runtime nodejs24 \
  --env PESAPAL_CONSUMER_KEY=... --env PESAPAL_CONSUMER_SECRET=... \
  --env MPESA_CONSUMER_KEY=...   --env MPESA_CONSUMER_SECRET=...
```

Unmentioned env keys carry over, so a code-only redeploy needs no `--env`.
`MPESA_SHORTCODE` and `MPESA_PASSKEY` default to Safaricom's published sandbox
till; set both for a real one.

## Venue and map

The venue field is backed by **Google Places autocomplete**, so an organizer
picks somewhere that provably exists rather than typing free text. That matters
for more than tidiness: a geocoder will happily turn a typo into coordinates on
the wrong side of town. Choosing a suggestion stores Google's own latitude and
longitude on the event, and the field shows a green tick once it is pinned.

Typing is never blocked - if suggestions cannot load, whatever was typed is
still saved and the map falls back to geocoding it.

The event page shows the venue as a **lite-mode map preview**: a static image
rather than the full interactive engine, which is far lighter and cannot swallow
the page's scroll the way a live map nested in a scrolling list does. Tapping
anywhere on it opens Google Maps for directions.

> Requires **Places API (New)** enabled on the Maps key. The legacy Places API
> is not sufficient - `Places.initializeWithNewPlacesApiEnabled()` is what the
> app calls, and the legacy endpoint returns REQUEST_DENIED on new projects.

## Cover images

Organizers pick a cover from the device with the system photo picker, which
needs no storage permission. The image is downscaled to 1600px JPEG on the phone
(a camera frame would otherwise be 8-12 MB), uploaded to the branch's object
storage through the `media` function, and the resulting public URL is stored in
`events.image_url` — so the web app renders the same picture.

Skipping the image is fine: a stock still for the event's category is shown
rather than an empty card. Categories come from a fixed list
(`EventCategories.ALL`) so the attendee's filter row stays meaningful instead of
filling up with every organizer's own spelling of "tech".

## Configuration

Machine-specific values live in `local.properties`, which is not in version
control:

```properties
sdk.dir=...
MAPS_API_KEY=...              # Google Maps SDK key, for the venue map
NEON_AUTH_BASE_URL=https://<endpoint>.neonauth.<region>.aws.neon.tech/neondb/auth
NEON_DATA_API_URL=https://<endpoint>.apirest.<region>.aws.neon.tech/neondb/rest/v1
NEON_PAYMENTS_URL=https://<branch>-payments.compute.<cell>.<region>.aws.neon.tech
NEON_MEDIA_URL=https://<branch>-media.compute.<cell>.<region>.aws.neon.tech
```

These come from `neon neon-auth status`, `neon data-api get` and
`neon functions get payments` in the repo linked to the Neon project. Note that
none of them is a secret — they are public endpoints that require a signed-in
token. The payment credentials are not here; they live in the function.

## Features

**Attendee**
- Browse published events, search and filter by category
- Event page with the venue on Google Maps — the organizer's dropped pin when
  there is one, otherwise the venue text is geocoded
- Past events by the same organizer
- Buy tickets; seats are held atomically so the last ticket cannot be oversold
- Pay by M-Pesa (PIN prompt on the phone) or card, or leave and finish paying
  later from My tickets
- Ticket with a QR code and a PDF download — both only once the payment clears

**Organizer**
- Everything an attendee can do — organizers buy tickets too, reachable from
  **My tickets** at the top of the Events tab
- Dashboard: revenue, tickets sold, event counts
- Create, edit, publish and delete events, with a cover image picked from the
  device, a category from the shared list, and an optional map pin
- Guest list per event
- Gate scanner: CameraX preview + ML Kit barcode reading, with a colour-coded
  verdict (admit / already in / not paid / wrong event / not valid)
- **Offline scanning**: the roster is downloaded up front, so the scanner keeps
  working with no signal and queues check-ins to sync later

## Things worth knowing

- **An account is required.** The Neon Data API gateway rejects unauthenticated
  requests, so there is no signed-out browsing and no guest checkout.
- **Offline duplicate scans.** Two phones scanning the same ticket while both are
  offline will each admit it; the clash is only visible once both have synced.
  Online, the database prevents it outright.
- Minimum SDK is 24, but the Outfit variable font only varies its weight on API
  26+; on 24–25 it renders at a single weight.
