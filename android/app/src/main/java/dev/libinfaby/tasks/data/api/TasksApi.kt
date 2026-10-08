package dev.libinfaby.tasks.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dev.libinfaby.tasks.BuildConfig
import dev.libinfaby.tasks.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.QueryMap
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

interface TasksApi {
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): TokenResponse
    @POST("auth/refresh") suspend fun refresh(): TokenResponse

    @GET("tasks") suspend fun tasks(@QueryMap filters: Map<String, String> = emptyMap()): TasksResponse
    @POST("tasks") suspend fun createTask(@Body body: TaskWrite): CreatedResponse
    @PUT("tasks/{id}") suspend fun updateTask(@Path("id") id: Long, @Body body: TaskWrite): MessageResponse
    @DELETE("tasks/{id}") suspend fun deleteTask(@Path("id") id: Long): MessageResponse
    @PATCH("tasks/{id}/toggle") suspend fun toggleTask(@Path("id") id: Long): MessageResponse

    @POST("subtasks") suspend fun createSubtask(@Body body: SubtaskWrite): CreatedResponse
    @PUT("subtasks/{id}") suspend fun updateSubtask(@Path("id") id: Long, @Body body: SubtaskWrite): MessageResponse
    @DELETE("subtasks/{id}") suspend fun deleteSubtask(@Path("id") id: Long): MessageResponse
    @PATCH("subtasks/{id}/toggle") suspend fun toggleSubtask(@Path("id") id: Long): MessageResponse

    @GET("tag-types") suspend fun tagTypes(): TagTypesResponse
    @POST("tag-types") suspend fun createTagType(@Body body: TagTypeWrite): CreatedResponse
    @PUT("tag-types/{id}") suspend fun updateTagType(@Path("id") id: Long, @Body body: TagTypeWrite): MessageResponse
    @DELETE("tag-types/{id}") suspend fun deleteTagType(@Path("id") id: Long): MessageResponse
    @POST("tag-types/tags") suspend fun createTag(@Body body: TagWrite): CreatedResponse
    @PUT("tag-types/tags/{id}") suspend fun updateTag(@Path("id") id: Long, @Body body: TagWrite): MessageResponse
    @DELETE("tag-types/tags/{id}") suspend fun deleteTag(@Path("id") id: Long): MessageResponse

    @GET("groups") suspend fun groups(): GroupsResponse
    @POST("groups") suspend fun createGroup(@Body body: GroupWrite): CreatedResponse
    @PUT("groups/{id}") suspend fun updateGroup(@Path("id") id: Long, @Body body: GroupWrite): MessageResponse
    @DELETE("groups/{id}") suspend fun deleteGroup(@Path("id") id: Long): MessageResponse

    @GET("settings") suspend fun settings(): SettingsResponse
    @PUT("settings") suspend fun updateDefaultGroup(@Body body: DefaultGroupWrite): MessageResponse
    @PUT("settings") suspend fun updateHiddenGroups(@Body body: HiddenGroupsWrite): MessageResponse

    @GET("daily") suspend fun dailyLogs(@QueryMap params: Map<String, String>): DailyLogsResponse
    @POST("daily") suspend fun createDailyLog(@Body body: DailyLogWrite): CreatedResponse
    @PUT("daily/{id}") suspend fun updateDailyLog(@Path("id") id: Long, @Body body: DailyLogUpdate): MessageResponse
    @DELETE("daily/{id}") suspend fun deleteDailyLog(@Path("id") id: Long): MessageResponse
}

val tasksJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    // explicitNulls stays on: PUT /tasks needs nulls sent to clear fields
}

/** Thrown for 401s so callers can send the user back to sign-in. */
class UnauthorizedException : IOException("Signed out — please sign in again")

/** A user-facing message for any API failure (the worker returns `{ "error": "..." }`). */
fun Throwable.userMessage(): String = when (this) {
    is UnauthorizedException -> message!!
    is HttpException -> response()?.errorBody()?.string()
        ?.let { runCatching { tasksJson.decodeFromString(ErrorResponse.serializer(), it).error }.getOrNull() }
        ?: "Request failed (${code()})"
    is IOException -> "Can't reach the server. Check your connection."
    else -> message ?: "Something went wrong"
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun client(settings: SettingsRepository): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            // DataStore reads are cached after the first; interceptors run off the main thread.
            val s = runBlocking { settings.current() }
            // The API base URL can be changed on the sign-in screen, so it's applied per request.
            val base = s.apiUrl.toHttpUrlOrNull()
            val original = chain.request()
            val url = if (base != null) {
                original.url.newBuilder()
                    .scheme(base.scheme).host(base.host).port(base.port)
                    .build()
            } else original.url
            val req = original.newBuilder().url(url).apply {
                if (s.token != null) header("Authorization", "Bearer ${s.token}")
            }.build()
            val res = chain.proceed(req)
            if (res.code == 401 && s.token != null && !original.url.encodedPath.endsWith("/auth/login")) {
                res.close()
                runBlocking { settings.signOut() }
                throw UnauthorizedException()
            }
            res
        }
        .apply {
            if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
        }
        .build()

    @Provides
    @Singleton
    fun api(client: OkHttpClient): TasksApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(tasksJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(TasksApi::class.java)
}
