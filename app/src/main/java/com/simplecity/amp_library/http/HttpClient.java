package com.simplecity.amp_library.http;

import com.simplecity.amp_library.http.lastfm.LastFmService;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class HttpClient {

    public static final String TAG = "HttpClient";

    private static final String URL_LAST_FM = "https://ws.audioscrobbler.com/2.0/";
    private static final String URL_ITUNES = "https://itunes.apple.com/search/";

    public static final String TAG_ARTWORK = "artwork";

    public final OkHttpClient okHttpClient;
    public final LastFmService lastFmService;

    private HttpClient() {
        okHttpClient = new OkHttpClient.Builder()
                // .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("192.168.0.3", 8888)))
                .build();

        Retrofit lastFmRestAdapter = new Retrofit.Builder()
                .baseUrl(URL_LAST_FM)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        lastFmService = lastFmRestAdapter.create(LastFmService.class);
    }

    public static HttpClient getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final HttpClient INSTANCE = new HttpClient();
    }
}
