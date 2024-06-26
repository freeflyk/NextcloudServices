package com.polar.nextcloudservices.Notification.Processors.basic;

import static com.polar.nextcloudservices.Notification.NotificationEvent.NOTIFICATION_EVENT_DELETE;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.polar.nextcloudservices.Notification.AbstractNotificationProcessor;
import com.polar.nextcloudservices.Config;
import com.polar.nextcloudservices.Notification.NotificationBuilderResult;
import com.polar.nextcloudservices.Notification.NotificationController;
import com.polar.nextcloudservices.Notification.NotificationEvent;
import com.polar.nextcloudservices.R;
import com.polar.nextcloudservices.Services.Settings.ServiceSettings;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BasicNotificationProcessor implements AbstractNotificationProcessor {
    public final int priority = 0;
    private final static String TAG = "Notification.Processors.BasicNotificationProcessor";

    public int iconByApp(String appName) {
        switch (appName) {
            case "spreed":
                return R.drawable.ic_icon_foreground;
            case "deck":
                return R.drawable.ic_deck;
            case "twofactor_nextcloud_notification":
                return android.R.drawable.ic_partial_secure;
            default:
                return R.drawable.ic_logo;
        }
    }


    //@SuppressLint("UnspecifiedImmutableFlag")
    private PendingIntent createNotificationDeleteIntent(Context context, int id) {
        Intent intent = new Intent();
        intent.setAction(Config.NotificationEventAction);
        intent.putExtra("notification_id", id);
        intent.putExtra("notification_event", NOTIFICATION_EVENT_DELETE);
        intent.setPackage(context.getPackageName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return PendingIntent.getBroadcast(
                    context,
                    id,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        } else {
            return PendingIntent.getBroadcast(
                    context,
                    id,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
            );
        }
    }

    @Override
    public NotificationBuilderResult updateNotification(int id, NotificationBuilderResult builderResult,
                                                        NotificationManager manager,
                                                        JSONObject rawNotification,
                                                        Context context,
                                                        NotificationController controller) throws JSONException {
        final ServiceSettings settings = new ServiceSettings(context);
        final boolean removeOnDismiss = settings.isRemoveOnDismissEnabled();
        final String app = AppNameMapper.getPrettifiedAppName(context,
                rawNotification.getString("app"));
        final String title = rawNotification.getString("subject");
        final String text = rawNotification.getString("message");
        final String app_name = rawNotification.getString("app");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(app_name, app, NotificationManager.IMPORTANCE_HIGH);
            Log.i(TAG, "Creating channel " + app_name);
            manager.createNotificationChannel(channel);
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        final String dateStr = rawNotification.getString("datetime");
        long unixTime = 0;
        try {
            Date date = format.parse(dateStr);
            if(date == null){
                throw new ParseException("Date was not parsed: result is null", 0);
            }
            unixTime = date.getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        builderResult.builder = builderResult.builder.setSmallIcon(iconByApp(app_name))
                .setContentTitle(title)
                .setAutoCancel(true)
                .setContentText(text)
                .setChannelId(app_name);
        if(unixTime != 0){
            builderResult.builder.setWhen(unixTime);
        }else{
            Log.w(TAG, "unixTime is 0, maybe parse failure?");
        }
        if(removeOnDismiss){
            Log.d(TAG, "Adding intent for delete notification event");
            builderResult.builder = builderResult.builder.setDeleteIntent(createNotificationDeleteIntent(context,
                    rawNotification.getInt("notification_id")));
        }
        return builderResult;
    }

    @Override
    public void onNotificationEvent(NotificationEvent event, Intent intent,
                                    NotificationController controller) {
        if(event != NOTIFICATION_EVENT_DELETE){
            return;
        }
        int id = intent.getIntExtra("notification_id", -1);
        Log.d(TAG, "Should remove notification " + id);
        if(id < 0){
            Log.wtf(TAG, "Notification delete event has not provided an id of notification deleted!");
        }
        Thread thread = new Thread(() -> callRemoveNotification(controller, id));
        thread.start();
    }

    private void callRemoveNotification(NotificationController controller, int id){
        controller.getAPI().removeNotification(id);
    }

    @Override
    public int getPriority() {
        return priority;
    }
}
