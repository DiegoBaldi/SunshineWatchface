/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.diegobaldi.sunshinewear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.example.android.diegobaldi.R;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }


    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        private final String LOG_TAG = Engine.class.getSimpleName();
        private static final String LOW_KEY = "low";
        private static final String HIGH_KEY = "high";
        private static final String IMAGE_KEY = "image";
        private static final String WEATHER_KEY = "weather_string";
        private static final String TIMESTAMP_KEY = "timestamp";
        private static final String WEATHER_PATH = "/weather";

        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mHourPaint;
        Paint mDatePaint;
        Paint mHighPaint;
        Paint mLowPaint;
        Paint mDividerPaint;
        String mLowTemp, mHighTemp, mWeatherString;
        Bitmap mWeatherIcon;

        GoogleApiClient mGoogleApiClient;


        boolean mAmbient;
        Calendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        float mXOffset;
        float mYOffset;
        float mLineOffset;
        float mImageOffset;
        float mTempOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mHourPaint = new Paint();
            mHourPaint = createTextPaint(ContextCompat.getColor(getApplicationContext(), R.color.digital_text));

            mDatePaint = new Paint();
            mDatePaint = createTextPaint(ContextCompat.getColor(getApplicationContext(), R.color.date_text));

            mHighPaint = new Paint();
            mHighPaint.setColor(ContextCompat.getColor(getApplicationContext(), R.color.digital_text));

            mLowPaint = new Paint();
            mLowPaint.setColor(ContextCompat.getColor(getApplicationContext(), R.color.date_text));

            mDividerPaint = new Paint();
            mDividerPaint.setColor(ContextCompat.getColor(getApplicationContext(), R.color.date_text));
            mDividerPaint.setAntiAlias(true);
            mDividerPaint.setStrokeWidth(2f);

            mCalendar = Calendar.getInstance();

            mGoogleApiClient = new GoogleApiClient.Builder(SunshineWatchFace.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            mGoogleApiClient.disconnect();
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                mGoogleApiClient.connect();
                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                mGoogleApiClient.connect();
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();

            mYOffset = resources.getDimension(isRound
                    ? R.dimen.digital_y_offset_round : R.dimen.digital_y_offset);

            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);

            mLineOffset = resources.getDimension(isRound
                    ? R.dimen.digital_line_y_offset_round : R.dimen.digital_line_y_offset);

            mImageOffset = resources.getDimension(isRound
                    ? R.dimen.digital_image_y_offset_round : R.dimen.digital_image_y_offset);

            mTempOffset = (isRound
                    ? 150 : 100);

            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_hour_size_round : R.dimen.digital_hour_size);
            float dateSize = resources.getDimension(isRound
                    ? R.dimen.digital_date_size_round : R.dimen.digital_date_size);
            float highTempSize = resources.getDimension(isRound
                    ? R.dimen.digital_high_size_round : R.dimen.digital_high_size);
            float lowTempSize = resources.getDimension(isRound
                    ? R.dimen.digital_low_size_round : R.dimen.digital_low_size);

            mHourPaint.setTextSize(textSize);
            mDatePaint.setTextSize(dateSize);
            mHighPaint.setTextSize(highTempSize);
            mLowPaint.setTextSize(lowTempSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mHourPaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                    mHighPaint.setAntiAlias(!inAmbientMode);
                    mLowPaint.setAntiAlias(!inAmbientMode);
                }
                if(mAmbient)
                    mBackgroundPaint.setColor(Color.BLACK);
                else
                    mBackgroundPaint.setColor(ContextCompat.getColor(getApplicationContext(), R.color.background));
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    requestInitialData();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            float centerX = bounds.exactCenterX();
            float centerY = bounds.exactCenterY();

            String time = mAmbient
                    ? String.format(Locale.getDefault(), "%d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format(Locale.getDefault(), "%d:%02d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));
//            canvas.drawText(text, mXOffset, mYOffset, mTextPaint);
            Rect timeBounds = new Rect();
            mHourPaint.getTextBounds(time, 0, time.length(), timeBounds);
            float xOffset = mHourPaint.measureText(time)/2f;

            if(!mAmbient) {
                SimpleDateFormat format1 = new SimpleDateFormat("EEE, MMM yyyy", Locale.getDefault());
                String date = format1.format(mCalendar.getTime());

                Rect dateBounds = new Rect();
                mDatePaint.getTextBounds(date, 0, date.length(), dateBounds);
                canvas.drawText(time, centerX-xOffset, mYOffset, mHourPaint);

                float xDateOffset = mDatePaint.measureText(date)/2f;
                canvas.drawText(date, centerX-xDateOffset, mYOffset+dateBounds.height()+10, mDatePaint);

                canvas.drawLine(centerX-50, centerY+mLineOffset, centerX+50, centerY+mLineOffset, mDividerPaint);

                if(mWeatherIcon!=null)
                    canvas.drawBitmap(mWeatherIcon, centerX-(dateBounds.width()/2), centerY+mLineOffset+20, mHourPaint);
                if(mHighTemp!=null){
                    canvas.drawText(mHighTemp, centerX-(dateBounds.width()/2)+100, centerY+mTempOffset, mHighPaint);
                }
                if(mHighTemp!=null && mLowTemp!=null)  {
                    float xHighTempOffset = mHighPaint.measureText(mHighTemp);
                    canvas.drawText(mLowTemp, centerX-(dateBounds.width()/2)+110+xHighTempOffset, centerY+mTempOffset, mLowPaint);
                }

            } else {
                canvas.drawText(time, centerX-xOffset, centerY, mHourPaint);
                if(mWeatherString!=null){
                    float xWeatherOffset = mLowPaint.measureText(mWeatherString);
                    canvas.drawText(mWeatherString, centerX-(xWeatherOffset/2), centerY+timeBounds.height(), mLowPaint);
                }
            }

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.d(LOG_TAG, "connection successful to google services");
            Wearable.DataApi.addListener(mGoogleApiClient, this);
            if(mWeatherIcon==null){
                requestInitialData();
            }
        }

        private void requestInitialData() {
            PutDataMapRequest dataMapRequest = PutDataMapRequest.create("/request");
            dataMapRequest.getDataMap().putLong(TIMESTAMP_KEY, System.currentTimeMillis());
            PutDataRequest putDataRequest = dataMapRequest.asPutDataRequest();
            putDataRequest.setUrgent();
            Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                    if (dataItemResult.getStatus().isSuccess()) {
                        Log.d(LOG_TAG, "Data sent to mobile app!!!");
                    } else {
                        Log.d(LOG_TAG, "Sending data to wearable failed");
                    }
                }
            });
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(LOG_TAG, "connection suspended to the google services");
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d(LOG_TAG, "connection failed to the google services");
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            // Loop through the events and send a message back to the node that created the data item.
            for (DataEvent dataEvent : dataEventBuffer) {
                if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem dataItem = dataEvent.getDataItem();
                    String path = dataItem.getUri().getPath();
                    if (WEATHER_PATH.equals(path)) {
                        DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                        DataMap weather = dataMapItem.getDataMap();
                        mHighTemp = String.format("%d°",Math.round(weather.getDouble(HIGH_KEY)));
                        mLowTemp = String.format("%d°", Math.round(weather.getDouble(LOW_KEY)));
                        mWeatherString = weather.getString(WEATHER_KEY);
                        Asset asset = weather.getAsset(IMAGE_KEY);
                        loadBitmapFromAsset(asset);
                    }
                }
            }
        }

        private void loadBitmapFromAsset(Asset asset) {
            if (asset == null) {
                throw new IllegalArgumentException("Asset must be non-null");
            }

            Wearable.DataApi.getFdForAsset(mGoogleApiClient, asset).setResultCallback(
                    new ResultCallback<DataApi.GetFdForAssetResult>() {
                        @Override
                        public void onResult(DataApi.GetFdForAssetResult getFdForAssetResult) {
                            InputStream assetInputStream = getFdForAssetResult.getInputStream();
                            // decode the stream into a bitmap
                            Bitmap bitmap = BitmapFactory.decodeStream(assetInputStream);
                            mWeatherIcon = Bitmap.createScaledBitmap(bitmap, 100, 100, false);
                            invalidate();
                        }
                    });
        }
    }
}
