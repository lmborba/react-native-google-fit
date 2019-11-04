/**
 * Copyright (c) 2017-present, Stanislav Doskalenko - doskalenko.s@gmail.com
 * All rights reserved.
 *
 * This source code is licensed under the MIT-style license found in the
 * LICENSE file in the root directory of this source tree.
 *
 * Based on Asim Malik android source code, copyright (c) 2015
 *
 **/

package com.reactnative.googlefit;

import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.Session;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResult;
import com.google.android.gms.fitness.result.DataSourcesResult;
import com.google.android.gms.fitness.data.Device;

import java.text.DateFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.ArrayList;


import static com.google.android.gms.fitness.data.Device.TYPE_PHONE;
import static com.google.android.gms.fitness.data.Device.TYPE_TABLET;
import static com.google.android.gms.fitness.data.Device.TYPE_WATCH;
import static com.google.android.gms.fitness.data.Device.TYPE_CHEST_STRAP;
import static com.google.android.gms.fitness.data.Device.TYPE_SCALE;
import static com.google.android.gms.fitness.data.Device.TYPE_HEAD_MOUNTED;

public class ActivityHistory {

    private ReactContext mReactContext;
    private GoogleFitManager googleFitManager;

    private static final String STEPS_FIELD_NAME = "steps";
    private static final String DISTANCE_FIELD_NAME = "distance";
    private static final String HIGH_LONGITUDE = "high_longitude";
    private static final String LOW_LONGITUDE = "low_longitude";
    private static final String HIGH_LATITUDE = "high_latitude";
    private static final String LOW_LATITUDE = "low_latitude";


    private static final int KCAL_MULTIPLIER = 1000;
    private static final int ONGOING_ACTIVITY_MIN_TIME_FROM_END = 10 * 60000;
    private static final String CALORIES_FIELD_NAME = "calories";

    private static final String TAG = "RNGoogleFit";

    public ActivityHistory(ReactContext reactContext, GoogleFitManager googleFitManager){
        this.mReactContext = reactContext;
        this.googleFitManager = googleFitManager;
    }

    public ReadableArray getActivitySamples(long startTime, long endTime) {
        WritableArray results = Arguments.createArray();
        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                .aggregate(DataType.TYPE_CALORIES_EXPENDED, DataType.AGGREGATE_CALORIES_EXPENDED)
                .aggregate(DataType.TYPE_DISTANCE_DELTA, DataType.AGGREGATE_DISTANCE_DELTA)
                .bucketByActivitySegment(1, TimeUnit.SECONDS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();

        DataReadResult dataReadResult = Fitness.HistoryApi.readData(googleFitManager.getGoogleApiClient(), readRequest).await(1, TimeUnit.MINUTES);

        List<Bucket> buckets = dataReadResult.getBuckets();
        for (Bucket bucket : buckets) {
            String activityName = bucket.getActivity();
            int activityType = bucket.getBucketType();
            if (!bucket.getDataSets().isEmpty()) {
                long start = bucket.getStartTime(TimeUnit.MILLISECONDS);
                long end = bucket.getEndTime(TimeUnit.MILLISECONDS);
                Date startDate = new Date(start);
                Date endDate = new Date(end);
                WritableMap map = Arguments.createMap();
                map.putDouble("start",start);
                map.putDouble("end",end);
                map.putString("activityName", activityName);
                String deviceName = "";
                String sourceId = "";
                boolean isTracked = true;
                for (DataSet dataSet : bucket.getDataSets()) {
                    for (DataPoint dataPoint : dataSet.getDataPoints()) {
                        try {
                            int deviceType = dataPoint.getOriginalDataSource().getDevice().getType();
                            if (deviceType == TYPE_WATCH) {
                                deviceName = "Android Wear";
                            } else {
                                deviceName = "Android";
                            }
                        } catch (Exception e) {
                        }
                        sourceId = dataPoint.getOriginalDataSource().getAppPackageName();
                        if (startDate.getTime() % 1000 == 0 && endDate.getTime() % 1000 == 0) {
                            isTracked = false;
                        }
                        for (Field field : dataPoint.getDataType().getFields()) {
                            String fieldName = field.getName();
                            switch (fieldName) {
                                case STEPS_FIELD_NAME:
                                    map.putInt("quantity", dataPoint.getValue(field).asInt());
                                    break;
                                case DISTANCE_FIELD_NAME:
                                    map.putDouble(fieldName, dataPoint.getValue(field).asFloat());
                                    break;
                                case CALORIES_FIELD_NAME:
                                    map.putDouble(fieldName, dataPoint.getValue(field).asFloat());
                                default:
                                    Log.w(TAG, "don't specified and handled: " + fieldName);
                            }
                        }
                    }
                }
                map.putString("device", deviceName);
                map.putString("sourceName", deviceName);
                map.putString("sourceId", sourceId);
                map.putBoolean("tracked", isTracked);
                results.pushMap(map);
            }
        }

        return results;
    }

    public ReadableArray getSessionSamples(long startTime, long endTime) {
        WritableArray results = Arguments.createArray();
        DataReadRequest readRequest = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                .aggregate(DataType.TYPE_CALORIES_EXPENDED, DataType.AGGREGATE_CALORIES_EXPENDED)
                .aggregate(DataType.TYPE_DISTANCE_DELTA, DataType.AGGREGATE_DISTANCE_DELTA)
                .aggregate(DataType.TYPE_SPEED, DataType.AGGREGATE_SPEED_SUMMARY)
                .bucketBySession(5, TimeUnit.SECONDS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();
                // .aggregate(DataType.TYPE_ACTIVITY_SEGMENT, DataType.AGGREGATE_ACTIVITY_SUMMARY)

        DataReadResult dataReadResult = Fitness.HistoryApi.readData(googleFitManager.getGoogleApiClient(), readRequest).await(1, TimeUnit.MINUTES);

        List<Bucket> buckets = dataReadResult.getBuckets();

        for (Bucket bucket : buckets) {
            // Log.i(TAG, "=============================: " + bucket);
            Session session = bucket.getSession();

            if (!bucket.getDataSets().isEmpty()) {
                long start = bucket.getStartTime(TimeUnit.MILLISECONDS);
                long end = bucket.getEndTime(TimeUnit.MILLISECONDS);
                Date startDate = new Date(start);
                Date endDate = new Date(end);

                WritableMap map = Arguments.createMap();

                map.putDouble("start",start);
                map.putDouble("end",end);
                map.putString("type", session.getActivity());
                map.putString("activityId", session.getIdentifier());
                map.putString("activityName", session.getName());
                map.putString("sourceId", session.getAppPackageName());
                map.putBoolean("isOngoing", session.isOngoing());

                map.putInt("elapsedTime", (int) (endDate.getTime() - startDate.getTime()) / 1000 );

                if (session.hasActiveTime()) {
                map.putInt("movingTime",(int) session.getActiveTime(TimeUnit.SECONDS));
                }else{
                    map.putNull("movingTime");
                }

                String deviceName = "";
                String device = "";
                boolean isTracked = true;
                boolean userInput = false;

                for (DataSet dataSet : bucket.getDataSets()) {
                    for (DataPoint dataPoint : dataSet.getDataPoints()) {
                        DataSource ds = dataPoint.getOriginalDataSource();

                        try{
                            String streamName = ds.getStreamName();
                            if(streamName.equals("user_input")){
                              userInput = true;
                            }
                        } catch (Exception e) {}

                        try{
                            String model = ds.getDevice().getModel();
                            String manuf = ds.getDevice().getManufacturer();
                            device = manuf+":"+model;
                        } catch (Exception e) {}

                        try {
                            int deviceType = ds.getDevice().getType();
                            if (deviceType == TYPE_PHONE) {
                                deviceName = "phone";
                            } else if (deviceType == TYPE_TABLET) {
                                deviceName = "tablet";
                            } else if (deviceType == TYPE_WATCH) {
                                deviceName = "watch";
                            } else if (deviceType == TYPE_CHEST_STRAP) {
                                deviceName = "chest_strap";
                            } else if (deviceType == TYPE_SCALE) {
                                deviceName = "scale";
                            } else if (deviceType == TYPE_HEAD_MOUNTED) {
                                deviceName = "head_mounted";
                            }
                        } catch (Exception e) { }


                        if (startDate.getTime() % 1000 == 0 && endDate.getTime() % 1000 == 0) {
                            isTracked = false;
                        }

                        for (Field field : dataPoint.getDataType().getFields()) {
                            String fieldName = field.getName();

                            if(fieldName.equals(STEPS_FIELD_NAME)){
                              map.putInt("quantity", dataPoint.getValue(field).asInt());
                            }else if(fieldName.equals(DISTANCE_FIELD_NAME)){
                              map.putDouble(fieldName, dataPoint.getValue(field).asFloat());
                            }else if(fieldName.equals(CALORIES_FIELD_NAME)){
                              map.putDouble(fieldName, dataPoint.getValue(field).asFloat());

                            }else if(fieldName.equals("max")){
                              map.putDouble("speedMax", dataPoint.getValue(field).asFloat());
                            }else if(fieldName.equals("min")){
                              map.putDouble("speedMin", dataPoint.getValue(field).asFloat());
                            }else if(fieldName.equals("average")){
                              map.putDouble("speedAverage", dataPoint.getValue(field).asFloat());
                            }else{
                              Log.w(TAG, "don't specified and handled: " + fieldName);
                            }

                        }
                    }
                }
                map.putString("device", device);
                map.putString("sourceName", deviceName);

                map.putBoolean("tracked", isTracked);
                map.putBoolean("userInput", userInput);
                results.pushMap(map);
            }
        }

        return results;
    }

}
