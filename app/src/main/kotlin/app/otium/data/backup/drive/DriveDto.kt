package app.otium.data.backup.drive

import kotlinx.serialization.Serializable

/** A file in the app data folder. Only the fields Otium asks Drive for are modelled. */
@Serializable
internal data class DriveFile(
    val id: String,
    val name: String? = null,
    /** SHA-256 of the content, in hex, as Drive computed it. */
    val sha256Checksum: String? = null,
    val appProperties: Map<String, String> = emptyMap(),
)

@Serializable
internal data class DriveFileList(val files: List<DriveFile> = emptyList())

/** What Otium sets on a file: its name, where it lives, and the facts about the backup. */
@Serializable
internal data class DriveFileMetadata(
    val name: String? = null,
    val parents: List<String>? = null,
    val appProperties: Map<String, String>? = null,
)

@Serializable
internal data class DriveAbout(val storageQuota: DriveStorageQuota? = null)

/** Byte counts arrive as strings. A missing [limit] means the account has no cap. */
@Serializable
internal data class DriveStorageQuota(val limit: String? = null, val usage: String? = null)

/** The body of a Drive error response, of which only the reasons are used. */
@Serializable
internal data class DriveErrorEnvelope(val error: DriveErrorBody? = null)

@Serializable
internal data class DriveErrorBody(val errors: List<DriveErrorItem> = emptyList())

@Serializable
internal data class DriveErrorItem(val reason: String? = null)
