package com.example.android.diegobaldi.sunshine.sync;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;

/**
 * Created by diego on 24/01/2017.
 */
public class SunshineWearSyncTask implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {


    private static final String LOW_KEY = "low";
    private static final String HIGH_KEY = "high";
    private static final String IMAGE_KEY = "image";
    private static final String WEATHER_PATH = "/weather";
    private static final String WEATHER_KEY = "weather_string";

    private static final String LOG_TAG = SunshineWearSyncTask.class.getSimpleName();

    private GoogleApiClient mGoogleApiClient;
    private int mSmallArtResourceId;
    private String mWeatherString;
    private double mHighTemp, mLowTemp;
    private Context mContext;

    public SunshineWearSyncTask(Context context, String weatherString, int smallArtResourceId, double high, double low) {

        mContext = context;
        mSmallArtResourceId = smallArtResourceId;
        mHighTemp = high;
        mLowTemp = low;
        mWeatherString = weatherString;

        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        mGoogleApiClient.connect();

    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(LOG_TAG, "Google api client connected");
        sendDataToWearDevice();

    }

    private void sendDataToWearDevice() {
        PutDataMapRequest dataMapRequest = PutDataMapRequest.create(WEATHER_PATH);
        dataMapRequest.getDataMap().putDouble(HIGH_KEY, mHighTemp);
        dataMapRequest.getDataMap().putDouble(LOW_KEY, mLowTemp);
        dataMapRequest.getDataMap().putString(WEATHER_KEY, mWeatherString);
        dataMapRequest.getDataMap().putLong("now", System.currentTimeMillis());
        Bitmap bitmap = BitmapFactory.decodeResource(mContext.getResources(), mSmallArtResourceId);
        Asset asset = createAssetFromBitmap(bitmap);
        dataMapRequest.getDataMap().putAsset(IMAGE_KEY, asset);
        PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
        putDataRequest.setUrgent();
        Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                if (dataItemResult.getStatus().isSuccess()) {
                    Log.d(LOG_TAG, "Data sent to wearable!!!");
                } else {
                    Log.d(LOG_TAG, "Sending data to wearable failed");
                }
            }
        });
    }

    private static Asset createAssetFromBitmap(Bitmap bitmap) {
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
        return Asset.createFromBytes(byteStream.toByteArray());
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(LOG_TAG, "Google api client connection suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(LOG_TAG, "Google api client connection failed");
    }
}
