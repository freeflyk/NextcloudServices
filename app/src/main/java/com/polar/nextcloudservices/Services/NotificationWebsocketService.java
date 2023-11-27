package com.polar.nextcloudservices.Services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.polar.nextcloudservices.API.INextcloudAbstractAPI;
import com.polar.nextcloudservices.API.websocket.NotificationWebsocket;
import com.polar.nextcloudservices.API.websocket.INotificationWebsocketEventListener;
import com.polar.nextcloudservices.Notification.NotificationController;
import com.polar.nextcloudservices.Services.Settings.ServiceSettings;
import com.polar.nextcloudservices.Services.Status.StatusController;
import com.polar.nextcloudservices.Utils.CommonUtil;

import org.json.JSONObject;

public class NotificationWebsocketService extends Service
        implements INotificationWebsocketEventListener, 
        IConnectionStatusListener, INotificationService {
    private INextcloudAbstractAPI mAPI;
    private ServiceSettings mServiceSettings;
    private NotificationController mNotificationController;
    private ConnectionController mConnectionController;
    private StatusController mStatusController;
    private NotificationWebsocket mNotificationWebsocket;
    private final static String TAG = "Services.NotificationWebsocketService";

    @Override
    public void onCreate(){
        mServiceSettings = new ServiceSettings(this);
        mAPI = mServiceSettings.getAPIFromSettings();
        mNotificationController = new NotificationController(this, mServiceSettings);
        mStatusController = new StatusController(this);
        mConnectionController = new ConnectionController(mServiceSettings);
        mStatusController.addComponent(NotificationServiceComponents.SERVICE_COMPONENT_CONNECTION,
                mNotificationController,
                NotificationServiceConfig.NOTIFICATION_CONTROLLER_PRIORITY);
        mStatusController.addComponent(NotificationServiceComponents.SERVICE_COMPONENT_CONNECTION,
                mConnectionController, NotificationServiceConfig.CONNECTION_COMPONENT_PRIORITY);
        mConnectionController.addConnectionStatusListener(this, this);
        startListening();
        startForeground(1, mNotificationController.getServiceNotification());
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onNewNotifications(JSONObject response) {
        if(response != null){
            mNotificationController.onNotificationsUpdated(response);
        }else{
            Log.e(TAG, "null response for notifications");
        }
    }

    @Override
    public void onConnectionStatusChanged(boolean isConnected){
        if(!mNotificationWebsocket.getConnected() && isConnected){
            Log.d(TAG, "Not connected: restarting connection");
            startListening();
        }
    }

    private void startListening(){
        try {
            mNotificationWebsocket = mAPI.getNotificationsWebsocket(this);
            mStatusController.addComponent(
                    NotificationServiceComponents.SERVICE_COMPONENT_WEBSOCKET,
                    mNotificationWebsocket, NotificationServiceConfig.API_COMPONENT_PRIORITY);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        //Initially get notifications
        mAPI.getNotifications(this);
    }

    /**
     * @param isError whether disconnect resulted from error
     */
    @Override
    public void onWebsocketDisconnected(boolean isError) {
        if(mConnectionController.checkConnection(this)){
            Log.w(TAG, "Received disconnect from websocket. Restart pause 3 seconds");
            CommonUtil.safeSleep(3000);
            startListening();
        } else {
            Log.w(TAG, "Disconnected from websocket. Seems that we have no network");
        }
    }

    @Override
    public void onWebsocketConnected() {
        /* stub */
    }

    @Override
    public String getStatus() {
        return mStatusController.getStatusString();
    }

    @Override
    public void onPreferencesChanged() {
        if(!mServiceSettings.isWebsocketEnabled()){
            Log.i(TAG, "Websocket is no more enabled. Disconnecting websocket and stopping service");
            mNotificationWebsocket.close();
            stopForeground(true);
        }
        Log.i(TAG, "Preferences changed. Re-connecting to websocket.");
        mNotificationWebsocket.close();
        mAPI = mServiceSettings.getAPIFromSettings();
        startListening();
    }

    @Override
    public void onAccountChanged() {
        Log.i(TAG, "Account changed. Re-connecting to websocket.");
        mNotificationWebsocket.close();
        mAPI = mServiceSettings.getAPIFromSettings();
        startListening();
    }
}