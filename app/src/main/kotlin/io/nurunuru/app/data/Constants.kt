package io.nurunuru.app.data

/**
 * Centralized application constants.
 * Auto-generated from design-tokens/constants.json — DO NOT EDIT
 * Run: npm run tokens
 */
object Constants {

    object Connection {
        const val MAX_CONCURRENT_REQUESTS = 4
        const val MAX_REQUESTS_PER_RELAY = 2
        const val REQUEST_TIMEOUT_MS = 15_000L
        const val EOSE_TIMEOUT_MS = 15_000L
        const val HEALTH_CHECK_INTERVAL_MS = 60_000L
        const val FAILED_RELAY_COOLDOWN_MS = 120_000L
        const val MAX_FAILURES_BEFORE_COOLDOWN = 3
        const val RETRY_MAX_ATTEMPTS = 3
        const val RETRY_BASE_DELAY_MS = 500L
        const val RETRY_MAX_DELAY_MS = 10_000L
    }

    object RateLimit {
        const val REQUESTS_PER_SECOND = 10
        const val BURST_SIZE = 20
    }

    object CacheDuration {
        const val PROFILE = 300_000L
        const val MUTE_LIST = 600_000L
        const val FOLLOW_LIST = 600_000L
        const val EMOJI = 1_800_000L
        const val TIMELINE = 600_000L
        const val SHORT = 60_000L
        const val NIP05 = 300_000L
        const val RELAY_INFO = 3_600_000L
        const val NOTIFICATION = 86_400_000L
    }

    object CacheMaxEntries {
        const val PROFILES = 500
        const val TIMELINE = 100
        const val REACTIONS = 1000
    }

    object Upload {
        const val MAX_CONCURRENT_UPLOADS = 3
        const val UPLOAD_TIMEOUT_MS = 30_000L
        const val RETRY_MAX_ATTEMPTS = 3
        const val RETRY_BASE_DELAY_MS = 1_000L
        const val MAX_IMAGES_PER_POST = 3
    }

    object UI {
        const val DEBOUNCE_SEARCH_MS = 300L
        const val DEBOUNCE_SCROLL_MS = 100L
        const val PAGE_SIZE_TIMELINE = 50
        const val PAGE_SIZE_SEARCH = 30
        const val PAGE_SIZE_NOTIFICATIONS = 50
        const val SKELETON_TIMELINE_COUNT = 5
        const val NIP05_VERIFY_TIMEOUT_MS = 5_000L
        const val PROFILE_FETCH_TIMEOUT_MS = 10_000L
    }

    object Time {
        const val MINUTE_SECS = 60L
        const val HOUR_SECS = 3_600L
        const val DAY_SECS = 86_400L
        const val WEEK_SECS = 604_800L
        const val MS_SECOND = 1_000L
        const val MS_MINUTE = 60_000L
        const val MS_HOUR = 3_600_000L
        const val MS_DAY = 86_400_000L
    }

    object Engagement {
        const val WEIGHT_ZAP = 100.0
        const val WEIGHT_CUSTOM_REACTION = 60.0
        const val WEIGHT_QUOTE = 35.0
        const val WEIGHT_REPLY = 30.0
        const val WEIGHT_REPOST = 25.0
        const val WEIGHT_BOOKMARK = 15.0
        const val WEIGHT_LIKE = 5.0
    }

    object NegativeSignal {
        const val NOT_INTERESTED = -50.0
        const val MUTED_AUTHOR = -1000.0
        const val REPORTED = -200.0
    }

    object SocialBoost {
        const val SECOND_DEGREE = 3.0
        const val MUTUAL_FOLLOW = 2.5
        const val HIGH_ENGAGEMENT_AUTHOR = 2.0
        const val FIRST_DEGREE = 0.5
        const val UNKNOWN = 1.0
    }

    object TimeDecay {
        const val HALF_LIFE_HOURS = 6.0
        const val MAX_AGE_HOURS = 48.0
        const val FRESHNESS_BOOST = 1.5
    }

    object ErrorMessages {
        const val NO_SIGNING_METHOD = "Signing is not available"
        const val SIGNING_FAILED = "Signing failed"
        const val PASSKEY_SIGNING_FAILED = "Passkey signing failed. Please export your private key from the mini-app screen."
        const val AMBER_SIGNING_FAILED = "Signing with Amber failed. Please try again."
        const val ENCRYPTION_FAILED = "Encryption failed"
        const val DECRYPTION_FAILED = "Decryption failed"
        const val NO_ENCRYPTION_METHOD = "Encryption is not available. Please export your private key from the mini-app screen."
        const val REQUEST_TIMEOUT = "Request timed out"
        const val ALL_RETRIES_FAILED = "All retries failed"
        const val CONNECTION_FAILED = "Connection failed"
        const val ALREADY_FOLLOWING = "Already following"
        const val NO_FOLLOW_LIST = "Follow list not found"
        const val AMBER_TIMEOUT = "Amber did not respond in time"
        const val AMBER_INVALID_EVENT = "Amber returned an invalid event"
        const val STATUS_OFFLINE = "Offline"
        const val STATUS_RECONNECTING = "Reconnecting..."
        const val STATUS_ERROR = "Connection problem"
        const val STATUS_CONNECTED = "Connected"
        const val STATUS_DISCONNECTED = "Disconnected"
    }

    // ─── Connection State ────────────────────────────────────────────────────
    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, ERROR
    }
}
