package app.otium.data.backup.drive

import okhttp3.HttpUrl

private const val GOOGLE_API_HOST = "googleapis.com"

/**
 * Whether [url] may receive the backup and the access token that goes with it. The upload address
 * comes from the `Location` header Drive returns, so it is checked before the token is sent there:
 * https only, on Google's API host or one of its subdomains.
 *
 * The check runs on the parsed host, so `https://www.googleapis.com@evil.example/` (host
 * `evil.example`) and `https://googleapis.com.evil.example/` are both refused.
 */
internal fun isGoogleUploadAddress(url: HttpUrl): Boolean =
    url.isHttps && (url.host == GOOGLE_API_HOST || url.host.endsWith(".$GOOGLE_API_HOST"))
