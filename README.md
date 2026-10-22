# Odyssey - Intelligent Travel Disruption & Rebooking Engine

A high-performance Spring Boot 3 microservice engineered in Java 21 for travel disruption mitigation, dependency graph risk evaluation, dynamic itinerary recovery, real-time status monitoring, and secure user account management with booking hand-off.

---

## Tech Stack
- **Framework**: Spring Boot 3.3.4
- **Language**: Java 21
- **Build Tool**: Apache Maven
- **Security**: Spring Security 6 (BCrypt, Session Management, CSRF Protection, Strict CSP)
- **Persistence**: MySQL 8.0, Flyway Migrations (V1–V7), Spring Data JPA
- **Database Schema**: Managed strictly through forward-only Flyway migrations
- **Local Dev Server**: `http://localhost:8085`

---

## Phase 9 Architecture: User Accounts, Ownership, and Booking Hand-off

Phase 9 transforms Odyssey into a multi-user platform with strict data isolation, session-based authentication, unified ownership enforcement, and booking completion workflows.

```
                           ┌───────────────────────────────┐
                           │      Client Browser (UI)      │
                           │   CSP: script-src 'self'      │
                           │   No inline scripts / onclick │
                           └───────────────┬───────────────┘
                                           │ Session Cookie + X-XSRF-TOKEN
                                           ▼
                           ┌───────────────────────────────┐
                           │       Security Filter Chain   │
                           │  - BCrypt Authentication      │
                           │  - Login Rate Limiter (5/15m) │
                           │  - Session Fixation Migration │
                           └───────────────┬───────────────┘
                                           │
                                           ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          TripAccessService (Single Gate)                    │
│                                                                             │
│  requireOwner(tripId):                                                      │
│    - Anonymous -> 401 Unauthorized                                          │
│    - Non-owner -> 403 Forbidden                                             │
│    - Mutating TRIP-DEMO-001 (except /api/demo/reset) -> 403 Forbidden       │
│                                                                             │
│  requireReadAccess(tripId):                                                 │
│    - Anonymous -> 401 Unauthorized                                          │
│    - TRIP-DEMO-001 -> Allowed for all authenticated users                   │
│    - Owner -> Allowed                                                       │
│    - Non-owner / Unowned -> 404 Not Found (Information hiding)              │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
     ┌─────────────────────────────────┼─────────────────────────────────┐
     ▼                                 ▼                                 ▼
┌──────────────────┐         ┌───────────────────┐             ┌───────────────────┐
│ TripController   │         │ StatusController  │             │ HandoffService    │
│ - CRUD / MyTrips │         │ - Status checks   │             │ - Deep link gen   │
│ - Booking ops    │         │ - Simulation      │             │ - Book / Unbook   │
│ - Constraints    │         │ - Live polling    │             │ - Status sync     │
└──────────────────┘         └───────────────────┘             └───────────────────┘
```

### 1. Unified Ownership Enforcement
Ownership checks are centralized in `TripAccessService`:
- **`requireOwner(tripId)`**: Guarantees that only the authenticated owner can mutate a trip. Invoked on **every** endpoint that accepts a `tripId` or affects trip state:
  - Trips (`GET`, `PUT`, `DELETE` on `/api/trips/{id}`)
  - Bookings (`POST`, `PUT`, `DELETE`, `/book`, `/unbook` on `/api/trips/{id}/bookings/...`)
  - Constraints (`PUT /api/trips/{id}/constraints`)
  - Disruptions & Problems (`POST /api/trips/{id}/disruptions`, `/report-problem`)
  - Recovery & Plans (`POST /api/trips/{id}/recover`, `/plan`, `/approve`, `/reject`)
  - Transit Status & Monitoring (`POST /api/trips/{id}/status/check`, `POST /api/simulate/status`)
  - Notifications & Hotel Messages (`POST /api/trips/{id}/notifications/test`, `/plan/hotel-message/send`)
- **`requireReadAccess(tripId)`**: Validates read privileges. Non-owned trips return `404 Not Found` to prevent trip ID enumeration.

### 2. Demo Trip Permissions (`TRIP-DEMO-001`)
- **Read Access**: Any logged-in user can view `TRIP-DEMO-001`.
- **Reset Permission**: Only `POST /api/demo/reset` can reset `TRIP-DEMO-001` back to its canonical Bengaluru-Mangaluru itinerary.
- **Immutability for Non-Owners**: All other mutating calls on `TRIP-DEMO-001` (such as adding/deleting bookings, updating constraints, reporting disruptions, or approving plans) return `403 Forbidden` for users who do not own the demo record.
- **Unowned Trips**: Legacy trips without an owner remain hidden from non-owners. No "claim trip" feature is exposed.

### 3. Booking Hand-Off & Status Transitions
When a recovery plan is approved, replacement legs enter the `REBOOKED` status. The hand-off system assists the traveler in completing actual carrier reservations:
- **Provider Deep Links**:
  - **IRCTC**: Official train booking portal link.
  - **redBus**: Pre-filled search link with sanitized origin, destination, and departure date.
  - **KSRTC**: State road transport booking portal link.
  - **Google Flights**: Pre-filled one-way flight search with travel date and route.
- **Manual Booking Confirmation**:
  - `POST /api/trips/{id}/bookings/{bookingId}/book` marks a leg as `BOOKED` and attaches an optional carrier reference/PNR.
  - When **all** replacement legs for an itinerary are `BOOKED`, the overall trip status transitions automatically to **`COMPLETED_BOOKINGS`**.
- **Unbooking Support**:
  - `POST /api/trips/{id}/bookings/{bookingId}/unbook` reverts a leg back to `REBOOKED`.
  - If the trip was previously in `COMPLETED_BOOKINGS`, it automatically moves back to **`RESOLVED`** as soon as any replacement leg is no longer booked.

### 4. Security Hardening
- **Passwords**: Hashed with BCrypt (cost factor 10).
- **Session Management**: Cookie-based server sessions with `HttpOnly` and `SameSite=Lax`. Session fixation attacks are mitigated with `.sessionManagement().sessionFixation().migrateSession()`.
- **Brute Force Protection**: In-memory sliding window rate limiter (`LoginRateLimiter`) allowing maximum 5 failed attempts per 15 minutes per IP/email.
- **CSRF Protection**: Standard `CookieCsrfTokenRepository.withHttpOnlyFalse()` issuing `XSRF-TOKEN` cookie. Client fetch requests automatically include the `X-XSRF-TOKEN` header. Safe endpoints (registration, login, demo reset) explicitly bypass CSRF.
- **Content Security Policy (CSP)**:
  ```http
  Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self';
  ```
  Zero inline scripts (`<script>...`) and zero inline event handlers (`onclick`, `onsubmit`, etc.) exist across the application. All events are registered dynamically in `app.js` via `addEventListener`.
- **Secret Redaction**: Session IDs, CSRF tokens, BCrypt hashes, and environment variables (`SMTP_PASSWORD`, `TWILIO_AUTH_TOKEN`, `DB_PASSWORD`) are never written to log files. Email addresses and phone numbers in logs are masked (e.g. `u***@example.com`, `+91*****3210`).

---

## API Endpoints

### Authentication & Account (`/api/auth`)
| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| `POST` | `/api/auth/register` | Register a new user (`name`, `email`, `password`, `phone`) | No |
| `POST` | `/api/auth/login` | Authenticate with email and password | No |
| `POST` | `/api/auth/logout` | Invalidate current session | Yes |
| `GET` | `/api/auth/me` | Fetch profile for current authenticated user | Yes |
| `PUT` | `/api/auth/profile` | Update user name and phone | Yes |
| `POST`/`PUT` | `/api/auth/password` | Change user password (`currentPassword`, `newPassword`) | Yes |

### Trips & Bookings (`/api/trips`)
| Method | Endpoint | Description | Ownership Check |
|---|---|---|---|
| `GET` | `/api/trips` | List trips owned by current user (or demo trip) | Read |
| `POST` | `/api/trips` | Create a new trip (auto-linked to logged-in user) | Owner |
| `GET` | `/api/trips/{id}` | Get trip details (hides unowned trips with 404) | Read |
| `PUT` | `/api/trips/{id}` | Update trip title, traveller details, constraints | Owner |
| `DELETE` | `/api/trips/{id}` | Delete trip and associated bookings | Owner |
| `POST` | `/api/trips/{id}/bookings` | Add a leg/booking to trip | Owner |
| `PUT` | `/api/trips/{id}/bookings/{bId}` | Update a specific booking | Owner |
| `DELETE` | `/api/trips/{id}/bookings/{bId}` | Delete a booking leg | Owner |
| `PUT` | `/api/trips/{id}/constraints` | Update itinerary constraints | Owner |

### Booking Hand-Off (`/api/trips/{id}/bookings/{bId}`)
| Method | Endpoint | Description | Ownership Check |
|---|---|---|---|
| `POST` | `/api/trips/{id}/bookings/{bId}/book` | Mark replacement leg as booked with optional reference | Owner |
| `POST` | `/api/trips/{id}/bookings/{bId}/unbook` | Revert booked leg to REBOOKED; shifts trip to RESOLVED | Owner |

### Disruptions, Recovery & Status
| Method | Endpoint | Description | Ownership Check |
|---|---|---|---|
| `POST` | `/api/trips/{id}/disruptions` | Report flight delay, cancellation, or hotel overbooking | Owner |
| `POST` | `/api/trips/{id}/bookings/{bId}/report-problem` | Report problem on a booking leg | Owner |
| `GET` | `/api/trips/{id}/impact` | Get dependency graph risk & cascade analysis | Owner |
| `POST` | `/api/trips/{id}/recover` | Generate LLM / Rule-based recovery plans | Owner |
| `POST` | `/api/trips/{id}/approve` | Approve a plan (updates leg statuses to REBOOKED) | Owner |
| `POST` | `/api/trips/{id}/reject` | Reject all options; prompts relaxation | Owner |
| `POST` | `/api/trips/{id}/status/check` | Trigger on-demand transit status check | Owner |
| `GET` | `/api/trips/{id}/status` | View latest carrier transit statuses | Read |
| `POST` | `/api/simulate/status` | Inject simulated carrier delay/cancellation | Owner |
| `GET` | `/api/trips/{id}/notifications` | Fetch dispatch history & audit logs | Owner |
| `POST` | `/api/trips/{id}/notifications/test` | Dispatch test notification alert | Owner |
| `POST` | `/api/trips/{id}/plan/hotel-message/send` | Dispatch automated hotel delay message | Owner |
| `POST` | `/api/demo/reset` | Restore `TRIP-DEMO-001` itinerary | Allowed (Public) |

---

## Database Migrations (Flyway)

All schema changes use standard MySQL 8.0 syntax without altering historical migrations:
- `V1__init_schema.sql`: Core tables (`trips`, `bookings`, `audit_entries`, `notifications`).
- `V2__seed_demo_trip.sql`: Initial seed data for `TRIP-DEMO-001`.
- `V3__phase3_recovery_tables.sql`: Recovery plans, recovery options, and leg replacements.
- `V4__phase4_relaxation_leastbad.sql`: Constraint relaxation logs and least-bad recovery metadata.
- `V5__phase5_transit_status.sql`: Scheduled status checks, transit providers, and auto-disruptions.
- `V6__phase6_real_notifications.sql`: Multi-channel notification delivery records.
- `V7__phase9_user_accounts_and_handoff.sql`:
  - `users` table: `id`, `name`, `email` (unique), `password_hash`, `phone`, `created_at`.
  - `trips.user_id`: Foreign key linked to `users(id)` on delete cascade.
  - `bookings.booking_reference`: Carrier booking reference / PNR string.

---

## Running the Application & Test Suite

### 1. Build and Run All Tests
```bash
mvn clean test
```
**Test Results**: 91 passing tests across all test suites, including:
- `Phase9SecurityAndHandoffTest`: 9 comprehensive security tests:
  - Registration & password hashing verification.
  - Session login, rate limiting (5 bad attempts), session fixation prevention.
  - Demo trip read access and immutability rules.
  - Loop across **all 22 endpoints** verifying `403 Forbidden` / `404 Not Found` for non-owners.
  - Booking hand-off deep link generation, manual booking confirmation, and `COMPLETED_BOOKINGS` <-> `RESOLVED` state transitions on unbook.
  - Strict Content Security Policy header verification.
- `StatusCheckAndDisruptionDetectionTest`: 16 transit status monitoring and disruption detection tests.
- `RealNotificationServiceTest`: 16 multi-channel notification and deduplication tests.
- `RecoveryServiceTest`, `ScoringServiceTest`, `TripValidationAndScoringTest`: Recovery graph and optimization tests.

### 2. Start the Server Locally
```bash
mvn spring-boot:run
```
Open [http://localhost:8085](http://localhost:8085) in your web browser.

---

## Configuration (`.env.example`)

Copy placeholder variables into your local environment before enabling real external providers:
```ini
# Database Credentials
DB_USER=your_db_username
DB_PASSWORD=your_db_password

# SMTP Settings for Real Email Notifications
SMTP_HOST=your_smtp_host
SMTP_PORT=587
SMTP_USERNAME=your_smtp_username
SMTP_PASSWORD=your_smtp_password
MAIL_FROM=your_mail_from_address

# Twilio Settings for Real SMS/WhatsApp Notifications
TWILIO_ACCOUNT_SID=your_twilio_account_sid
TWILIO_AUTH_TOKEN=your_twilio_auth_token
TWILIO_FROM_NUMBER=your_twilio_phone_number
TWILIO_WHATSAPP_FROM=your_twilio_whatsapp_number

# LLM API Key (Optional)
LLM_API_KEY=your_llm_api_key
```
No passwords, secrets, or keys are committed to the codebase or displayed in application output.
