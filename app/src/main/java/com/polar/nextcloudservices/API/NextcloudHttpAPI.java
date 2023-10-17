package com.polar.nextcloudservices.API;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import com.polar.nextcloudservices.BuildConfig;
import com.polar.nextcloudservices.Services.PollUpdateListener;
import com.polar.nextcloudservices.Services.Settings.ServiceSettings;
import com.polar.nextcloudservices.Services.Status.Status;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.HttpsURLConnection;

public class NextcloudHttpAPI implements NextcloudAbstractAPI {
    private final String TAG = "NextcloudHttpAPI";
    private final String UA = "NextcloudServices/" + BuildConfig.VERSION_NAME;
    private String mStatusString = "Updating settings";
    private boolean lastPollSuccessful = false;
    private final ServiceSettings mServiceSettings;
    private NextcloudWebSocketAPI nextcloudWebSocketAPI;

    public NextcloudHttpAPI(ServiceSettings settings){
        mServiceSettings = settings;
        nextcloudWebSocketAPI = new NextcloudWebSocketAPI(mServiceSettings);
    }

    private static String getAuth(String user, String password) {
        //Log.d("NotificationService.PollTask",user+":"+password);
        return Base64.encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);
    }

    private HttpURLConnection request(String path, String method,
                                      Boolean setAccept) throws IOException {
        String user = mServiceSettings.getUsername();
        String password = mServiceSettings.getPassword();
        String baseUrl = mServiceSettings.getServer();
        String prefix = "https://";
        if (mServiceSettings.getUseHttp()) {
            prefix = "http://";
        }
        String endpoint = prefix + baseUrl + path;
        //Log.d(TAG, endpoint);
        URL url = new URL(endpoint);
        HttpURLConnection conn;
        if (mServiceSettings.getUseHttp()) {
            conn = (HttpURLConnection) url.openConnection();
        } else {
            conn = (HttpsURLConnection) url.openConnection();
        }
        conn.setRequestProperty("Authorization", "Basic " + getAuth(user, password));
        conn.setRequestProperty("Host", url.getHost());
        conn.setRequestProperty("User-agent", UA);
        conn.setRequestProperty("OCS-APIRequest", "true");
        if (setAccept) {
            conn.setRequestProperty("Accept", "application/json");
        }
        conn.setRequestMethod(method);
        conn.setReadTimeout(60000);
        conn.setConnectTimeout(5000);
        return conn;
    }

    @Override
    public void removeNotification(int id) {
        Log.d(TAG, "Removing notification " + id);
        try {
            String prefix = "https://";
            if (mServiceSettings.getUseHttp()) {
                prefix = "http://";
            }
            HttpURLConnection conn =
                    request("/ocs/v2.php/apps/notifications/api/v2/notifications/" + id,
                    "DELETE", false);
            String responseCode = Integer.toString(conn.getResponseCode());
            Log.d(TAG, "--> DELETE " + prefix + mServiceSettings.getServer()
                    + "/ocs/v2.php/apps/notifications/api/v2/notifications/" +
                    id + " -- " + responseCode);
        } catch (IOException e) {
            Log.e(TAG, "Failed to DELETE notification: " + e.getLocalizedMessage());
            Log.d(TAG, "Exception was: " + e);
        }
    }

    private static String getEndpoint(@NonNull  ServiceSettings settings){
        String baseUrl = settings.getServer();
        String prefix = "https://";
        if (settings.getUseHttp()) {
            prefix = "http://";
        }
        return prefix + baseUrl;
    }

    private HttpURLConnection getBaseConnection(URL url, String method)
            throws IOException {
        HttpURLConnection conn;
        if (mServiceSettings.getUseHttp()) {
            conn = (HttpURLConnection) url.openConnection();
        } else {
            conn = (HttpsURLConnection) url.openConnection();
        }
        conn.setRequestProperty("Authorization", "Basic " + getAuth(mServiceSettings.getUsername(),
                mServiceSettings.getPassword()));
        conn.setRequestProperty("Host", url.getHost());
        conn.setRequestProperty("User-agent", UA);
        conn.setRequestProperty("OCS-APIRequest", "true");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestMethod(method);
        return conn;
    }

    @Override
    public void sendTalkReply(String chatroom, String message) throws IOException {
        String endpoint = getEndpoint(mServiceSettings) + "/ocs/v2.php/apps/spreed/api/v1/chat/" + chatroom;
        URL url = new URL(endpoint);
        HttpURLConnection conn = getBaseConnection(url, "POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(5000);

        //FIXME: create separate params generator
        final String params = "{\"message\": \"" + message + "\", \"chatroom\": \"" + chatroom + "\"}";
        OutputStream os = conn.getOutputStream();
        os.write(params.getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        Log.d(TAG, "--> POST " + endpoint + " -- " + code);

    }

    public Bitmap getUserAvatar(String userId) throws IOException {
        HttpURLConnection connection = request("/index.php/avatar/"+userId+"/256",
                "GET", false);
        connection.setDoInput(true);
        return BitmapFactory.decodeStream(connection.getInputStream());
    }

    @Override
    public Bitmap getImagePreview(String imageId) throws Exception {
        HttpURLConnection connection = request( "/index.php/core/preview?fileId="
                        + imageId + "&x=100&y=100&a=1",
                "GET", false);
        connection.setRequestProperty("Accept", "image/*");
        connection.setDoInput(true);

        int responseCode = connection.getResponseCode();
        Log.d(TAG, "--> GET " + getEndpoint(mServiceSettings)
                + "/index.php/core/preview?fileId="+imageId+"&x=100&y=100&a=1 -- " + responseCode);

        return BitmapFactory.decodeStream(connection.getInputStream());
    }

    @Override
    public void sendAction(String link,
                           String method) throws Exception {
        String endpoint = getEndpoint(mServiceSettings) + link;
        URL url = new URL(endpoint);
        HttpURLConnection connection = getBaseConnection(url, method);
        connection.setConnectTimeout(5000);
        connection.setDoInput(true);

        int responseCode = connection.getResponseCode();
        Log.d(TAG, "--> " + method + getEndpoint(mServiceSettings)
                + link + "--" + responseCode);
    }

    public void probeForWebSocket(){
        try {
            HttpURLConnection conn = request("/ocs/v1.php/cloud/capabilities",
                    "GET", true);
            conn.setDoInput(true);

            String responseCode = Integer.toString(conn.getResponseCode());
            Log.d(TAG, "--> GET "+ getEndpoint(mServiceSettings) + "/ocs/v1.php/cloud/capabilities -- " + responseCode);

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder buffer = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                buffer.append(line);
            }
            in.close();

            JSONObject response = new JSONObject(buffer.toString());
            JSONObject notifyPushCapabilities = response.getJSONObject("ocs").getJSONObject("data").getJSONObject("capabilities").getJSONObject("notify_push");
            if(notifyPushCapabilities.getJSONObject("endpoints").has("websocket")) {
                String endpoint = notifyPushCapabilities.getJSONObject("endpoints").getString("websocket");
                nextcloudWebSocketAPI.setEndpoint(endpoint);
                boolean websocketConnected = nextcloudWebSocketAPI.connect();
                if(websocketConnected){
                    Log.i(TAG, "websocket successfully connected!");
                }else{
                    Log.e(TAG, "websocket connection failed!");
                }
            }else{
                Log.d(TAG, "no websocket endpoint found!");
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON");
            e.printStackTrace();
            mStatusString = "Disconnected: server has sent bad response: " + e.getLocalizedMessage();
        } catch (java.io.FileNotFoundException e) {
            e.printStackTrace();
            mStatusString = "Disconnected: File not found: check your credentials and Nextcloud instance.";
        } catch (IOException e) {
            Log.e(TAG, "Error while getting response");
            e.printStackTrace();
            mStatusString = "Disconnected: I/O error: " + e.getLocalizedMessage();
        } catch (Exception e) {
            e.printStackTrace();
            mStatusString = "Disconnected: " + e.getLocalizedMessage();
        }
    }

    @Override
    public JSONObject getNotifications(PollUpdateListener service) {
        if (!nextcloudWebSocketAPI.isConnectionTested()) {
            // if a websocket connection was never tested we probe for the connection.
            Log.d(TAG, "Probing for websocket connection.");
            probeForWebSocket();
        }
        if (nextcloudWebSocketAPI.isConnected() && nextcloudWebSocketAPI.isNotificationAvailable()) {
            // if the websocket connection is established and a notification is available we fetch it using the standard approach.
            Log.d(TAG, "Websocket is connected and notification available.");
            nextcloudWebSocketAPI.setNotificationAvailable(false);
            return fetchNotification(service);
        } else if (!nextcloudWebSocketAPI.isConnected()) {
            // if the websocket connection is closed for some reason we fall back to checking "manually" for notifications.
            Log.d(TAG, "Websocket is disconnected. Fallback to manual checks.");
            nextcloudWebSocketAPI.setNotificationAvailable(false);
            return fetchNotification(service);
        }
        return null;
    }

    private JSONObject fetchNotification(PollUpdateListener service) {
        try {
            HttpURLConnection conn = request("/ocs/v2.php/apps/notifications/api/v2/notifications",
                    "GET", true);
            conn.setDoInput(true);

            String responseCode = Integer.toString(conn.getResponseCode());
            Log.d(TAG, "--> GET "+ getEndpoint(mServiceSettings) + "/ocs/v2.php/apps/notifications/api/v2/notifications -- " + responseCode);

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder buffer = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                buffer.append(line);
            }
            in.close();
            //Log.d(TAG, buffer.toString());
            JSONObject response = new JSONObject(buffer.toString());
            lastPollSuccessful = true;

            service.onPollFinished(response);
            return response;
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON");
            e.printStackTrace();
            mStatusString = "Disconnected: server has sent bad response: " + e.getLocalizedMessage();
            return null;
        } catch (java.io.FileNotFoundException e) {
            e.printStackTrace();
            mStatusString = "Disconnected: File not found: check your credentials and Nextcloud instance.";
            return null;
        } catch (IOException e) {
            Log.e(TAG, "Error while getting response");
            e.printStackTrace();
            mStatusString = "Disconnected: I/O error: " + e.getLocalizedMessage();
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            mStatusString = "Disconnected: " + e.getLocalizedMessage();
            return null;
        }
    }

    @Override
    public Status getStatus(Context context) {
        if(lastPollSuccessful){
            return Status.Ok();
        }
        return Status.Failed(mStatusString);
    }
}
