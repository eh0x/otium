package app.otium.data.backup.drive

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

internal const val DRIVE_BASE_URL = "https://www.googleapis.com/"

/** The only Drive space Otium touches: a folder only this app can see, with the `drive.appdata` scope. */
internal const val APP_DATA_FOLDER = "appDataFolder"

private const val FILE_FIELDS = "id,name,md5Checksum,appProperties"
private const val LIST_FIELDS = "files($FILE_FIELDS)"
private const val PAGE_SIZE = 100

/**
 * The part of the Drive v3 REST API Otium uses. Every call carries the `Authorization` header
 * itself, because the token comes from a suspending call and can change between calls.
 */
internal interface DriveApi {
    /** One page is plenty: Otium keeps at most three files here. */
    @GET("drive/v3/files")
    suspend fun listFiles(
        @Header("Authorization") auth: String,
        @Query("spaces") spaces: String = APP_DATA_FOLDER,
        @Query("fields") fields: String = LIST_FIELDS,
        @Query("pageSize") pageSize: Int = PAGE_SIZE,
    ): DriveFileList

    @GET("drive/v3/files/{id}")
    suspend fun getFile(
        @Path("id") id: String,
        @Header("Authorization") auth: String,
        @Query("fields") fields: String = FILE_FIELDS,
    ): DriveFile

    /**
     * Starts a resumable upload, the way Drive wants files of any real size sent. The answer's
     * `Location` header is the address to send the bytes to.
     */
    @POST("upload/drive/v3/files?uploadType=resumable")
    suspend fun startUpload(
        @Header("Authorization") auth: String,
        @Header("X-Upload-Content-Type") contentType: String,
        @Header("X-Upload-Content-Length") contentLength: Long,
        @Body metadata: DriveFileMetadata,
    ): Response<Unit>

    /** Sends the bytes to the address [startUpload] returned. The file exists once this returns. */
    @PUT
    suspend fun uploadContent(
        @Url sessionUrl: String,
        @Header("Authorization") auth: String,
        @Body content: RequestBody,
    ): DriveFile

    /** Renames a file or changes its properties. */
    @PATCH("drive/v3/files/{id}")
    suspend fun updateFile(
        @Path("id") id: String,
        @Header("Authorization") auth: String,
        @Body metadata: DriveFileMetadata,
        @Query("fields") fields: String = FILE_FIELDS,
    ): DriveFile

    @DELETE("drive/v3/files/{id}")
    suspend fun deleteFile(@Path("id") id: String, @Header("Authorization") auth: String)

    /** The file's bytes, streamed: a backup can be large, so it is never held in memory. */
    @Streaming
    @GET("drive/v3/files/{id}")
    suspend fun download(
        @Path("id") id: String,
        @Header("Authorization") auth: String,
        @Query("alt") alt: String = "media",
    ): ResponseBody

    @GET("drive/v3/about")
    suspend fun about(
        @Header("Authorization") auth: String,
        @Query("fields") fields: String = "storageQuota(limit,usage)",
    ): DriveAbout
}
