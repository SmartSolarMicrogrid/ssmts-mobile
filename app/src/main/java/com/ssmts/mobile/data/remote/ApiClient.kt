package com.ssmts.mobile.data.remote

import android.content.Context
import com.ssmts.mobile.BuildConfig
import com.ssmts.mobile.data.local.SessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Singleton Retrofit client with JWT bearer injection. */
object ApiClient {

    @Volatile
    private var service: ApiService? = null
    private lateinit var session: SessionManager

    fun init(context: Context) {
        session = SessionManager(context.applicationContext)
    }

    val api: ApiService
        get() = service ?: synchronized(this) {
            service ?: build().also { service = it }
        }

    private fun build(): ApiService {
        val authInterceptor = Interceptor { chain ->
            val token = session.token
            val request = if (token.isNullOrEmpty()) {
                chain.request()
            } else {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            }
            chain.proceed(request)
        }

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
