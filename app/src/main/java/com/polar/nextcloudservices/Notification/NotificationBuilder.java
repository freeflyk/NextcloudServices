package com.polar.nextcloudservices.Notification;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.polar.nextcloudservices.NotificationService;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Vector;

public class NotificationBuilder {
    private Vector<AbstractNotificationProcessor> processors;
    private final static String TAG="NotificationBuilder";

    public NotificationBuilder(){
        processors = new Vector<>();
    }

    public Notification buildNotification(int id, JSONObject rawNotification, Context context, NotificationService service) throws Exception {
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, rawNotification.getString("app"));
        for(int i=0; i<processors.size(); ++i){
            Log.d(TAG, "Will call notification processor: "+processors.get(i).toString());
            mBuilder = processors.get(i).updateNotification(id, mBuilder, mNotificationManager,
                    rawNotification, context, service);
        }
        return mBuilder.build();
    }

    public void addProcessor(AbstractNotificationProcessor processor){
        int place=0;
        for(;place<processors.size(); ++place){
            if(processors.get(place).getPriority()>=processor.getPriority()){
               break;
            }
        }
        processors.insertElementAt(processor, place);
    }

    public void onNotificationEvent(NotificationEvent event, Intent intent, NotificationService service) {
        for(AbstractNotificationProcessor processor: processors){
            processor.onNotificationEvent(event, intent, service);
        }
    }
}
