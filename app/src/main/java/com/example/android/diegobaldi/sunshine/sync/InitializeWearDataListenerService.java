package com.example.android.diegobaldi.sunshine.sync;

import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.example.android.diegobaldi.sunshine.data.WeatherContract;
import com.example.android.diegobaldi.sunshine.utilities.SunshineDateUtils;
import com.example.android.diegobaldi.sunshine.utilities.SunshineWeatherUtils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.example.android.diegobaldi.sunshine.utilities.NotificationUtils.INDEX_MAX_TEMP;
import static com.example.android.diegobaldi.sunshine.utilities.NotificationUtils.INDEX_MIN_TEMP;
import static com.example.android.diegobaldi.sunshine.utilities.NotificationUtils.INDEX_WEATHER_ID;
import static com.example.android.diegobaldi.sunshine.utilities.NotificationUtils.WEATHER_NOTIFICATION_PROJECTION;

/**
 * Created by diego on 24/01/2017.
 */

public class InitializeWearDataListenerService extends WearableListenerService {

    private static final String TAG = "DataLayerSample";
    private static final String REQUEST_WEATHER_PATH = "/request";

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onDataChanged: " + dataEventBuffer);
        }
        final List events = FreezableUtils
                .freezeIterable(dataEventBuffer);

        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();

        ConnectionResult connectionResult =
                googleApiClient.blockingConnect(30, TimeUnit.SECONDS);

        if (!connectionResult.isSuccess()) {
            Log.e(TAG, "Failed to connect to GoogleApiClient.");
            return;
        }

        // Loop through the events and send a message
        // to the node that created the data item.
        for (DataEvent dataEvent : dataEventBuffer) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                DataItem dataItem = dataEvent.getDataItem();
                String path = dataItem.getUri().getPath();
                if (REQUEST_WEATHER_PATH.equals(path)) {
                    /* Build the URI for today's weather in order to show up to date data in notification */
                    Uri todaysWeatherUri = WeatherContract.WeatherEntry.buildWeatherUriWithDate(SunshineDateUtils.normalizeDate(System.currentTimeMillis()));

                /*
                 * The MAIN_FORECAST_PROJECTION array passed in as the second parameter is defined in our WeatherContract
                 * class and is used to limit the columns returned in our cursor.
                 */
                    Cursor todayWeatherCursor = getApplicationContext().getContentResolver().query(
                            todaysWeatherUri,
                            WEATHER_NOTIFICATION_PROJECTION,
                            null,
                            null,
                            null);

                /*
                 * If todayWeatherCursor is empty, moveToFirst will return false. If our cursor is not
                 * empty, we want to show the notification.
                 */
                    if (todayWeatherCursor.moveToFirst()) {

                    /* Weather ID as returned by API, used to identify the icon to be used */
                        int weatherId = todayWeatherCursor.getInt(INDEX_WEATHER_ID);
                        double high = todayWeatherCursor.getDouble(INDEX_MAX_TEMP);
                        double low = todayWeatherCursor.getDouble(INDEX_MIN_TEMP);


                    /* getSmallArtResourceIdForWeatherCondition returns the proper art to show given an ID */
                        int smallArtResourceId = SunshineWeatherUtils.getSmallArtResourceIdForWeatherCondition(weatherId);
                        String weatherString = SunshineWeatherUtils.getStringForWeatherCondition(getApplicationContext(), weatherId);

                        new SunshineWearSyncTask(getApplicationContext(), weatherString, smallArtResourceId, high, low);

                    }

                /* Always close your cursor when you're done with it to avoid wasting resources. */
                    todayWeatherCursor.close();
                }
            }
        }
    }
}