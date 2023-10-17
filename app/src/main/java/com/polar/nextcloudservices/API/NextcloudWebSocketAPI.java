package com.polar.nextcloudservices.API;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.polar.nextcloudservices.Services.Settings.ServiceSettings;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class NextcloudWebSocketAPI extends WebSocketListener{
    private static final String TAG = "NextcloudWebSocketAPI";
    private String endpoint;
    private ServiceSettings serviceSettings;
    private boolean connected;
    private boolean notificationAvailable;
    private boolean connectionTested = false;

    public NextcloudWebSocketAPI(ServiceSettings serviceSettings){
        this.serviceSettings = serviceSettings;
        this.notificationAvailable = false;
    }

    public boolean connect(){
        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build();

        try {
            System.err.println(endpoint);
            Request request = new Request.Builder()
                    .url(new URL(endpoint))
                    .build();

            WebSocket webSocket = client.newWebSocket(request, this);
            webSocket.send(serviceSettings.getUsername());
            webSocket.send(serviceSettings.getPassword());
            client.dispatcher().executorService().shutdown();
        } catch (MalformedURLException e) {
            System.err.println("error connecting to web socket.");
            setConnected(false);
        }
        connectionTested = true;
        return connected;
    }

    public boolean isNotificationAvailable() {
        return notificationAvailable;
    }

    public void setNotificationAvailable(boolean notificationAvailable) {
        this.notificationAvailable = notificationAvailable;
    }

    public boolean isConnected() {
        return connected;
    }

    private void setConnected(boolean connected) {
        this.connected = connected;
    }

    public boolean isConnectionTested() {
        return connectionTested;
    }

    public void setEndpoint(String endpoint) {
        if(endpoint != null) {
            if (serviceSettings.getUseHttp()) {
                this.endpoint = endpoint.replace("wss", "http");
            } else {
                this.endpoint = endpoint.replace("wss", "https");
            }
        }
    }

    @Override
    public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
        super.onClosed(webSocket, code, reason);
        setConnected(false);
    }

    @Override
    public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
        super.onClosing(webSocket, code, reason);
    }

    @Override
    public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
        super.onFailure(webSocket, t, response);
        setConnected(false);
        Log.e(TAG, "websocket connection failed! "+t+" "+response);
    }

    @Override
    public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
        super.onMessage(webSocket, text);
        switch (text) {
            case "notify_notification":
                setNotificationAvailable(true);
                break;
            case "notify_file":
                // nothing to do ..
                break;
            case "notify_activity":
                // nothing to do ..
                break;
            case "authenticated":
                Log.d(TAG, "websocket connection successfully authenticated.");
                break;
            default:
                Log.d(TAG, "received unhandled text message on websocket: " + text);
                break;
        }
    }

    @Override
    public void onMessage(@NonNull WebSocket webSocket, @NonNull ByteString bytes) {
        super.onMessage(webSocket, bytes);
        Log.d(TAG, "received unhandled byte message on websocket: "+bytes);
    }

    @Override
    public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
        super.onOpen(webSocket, response);
        Log.d(TAG, "websocket connection opened: "+response.message());
        setConnected(true);
    }
}
