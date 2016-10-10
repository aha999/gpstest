/*
 * Copyright (C) 2008-2013 The Android Open Source Project,
 * Sean J. Barbeau
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

package com.android.gpstest;

import com.android.gpstest.util.GnssType;
import com.android.gpstest.util.GpsTestUtil;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.v4.app.Fragment;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;


public class GpsStatusFragment extends Fragment implements GpsTestActivity.GpsTestListener {

    private final static String TAG = "GpsStatusActivity";

    private static final int PRN_COLUMN = 0;

    private static final int FLAG_IMAGE_COLUMN = 1;

    private static final int SNR_COLUMN = 2;

    private static final int ELEVATION_COLUMN = 3;

    private static final int AZIMUTH_COLUMN = 4;

    private static final int FLAGS_COLUMN = 5;

    private static final int COLUMN_COUNT = 6;

    private static final String EMPTY_LAT_LONG = "             ";

    private Resources mRes;

    private TextView mLatitudeView, mLongitudeView, mFixTimeView, mTTFFView, mAltitudeView,
            mAccuracyView, mSpeedView, mBearingView;

    private SvGridAdapter mAdapter;

    private int mSvCount, mPrns[], mConstellationType[];

    private float mSnrs[], mSvElevations[], mSvAzimuths[];

    /**
     * Key is combination of sat ID and constellation type (GpsTestUtil.createGnssSatelliteKey()),
     * and value is the SNR for that satellite.  Needed to match GnssMeasurement events up with
     * GnssStatus events.
     */
    HashMap<String, Double> mSnrsForSats;

    private boolean mHasEphemeris[], mHasAlmanac[], mUsedInFix[];

    private long mFixTime;

    private boolean mNavigating, mGotFix;

    private Drawable flagUsa, flagRussia, flagJapan, flagChina, flagGalileo;

    private static String doubleToString(double value, int decimals) {
        String result = Double.toString(value);
        // truncate to specified number of decimal places
        int dot = result.indexOf('.');
        if (dot > 0) {
            int end = dot + decimals + 1;
            if (end < result.length()) {
                result = result.substring(0, end);
            }
        }
        return result;
    }

    public void onLocationChanged(Location location) {
        if (!mGotFix) {
            mTTFFView.setText(GpsTestActivity.getInstance().mTtff);
            mGotFix = true;
        }
        mLatitudeView.setText(doubleToString(location.getLatitude(), 7) + " ");
        mLongitudeView.setText(doubleToString(location.getLongitude(), 7) + " ");
        mFixTime = location.getTime();
        if (location.hasAltitude()) {
            mAltitudeView.setText(doubleToString(location.getAltitude(), 1) + " m");
        } else {
            mAltitudeView.setText("");
        }
        if (location.hasAccuracy()) {
            mAccuracyView.setText(doubleToString(location.getAccuracy(), 1) + " m");
        } else {
            mAccuracyView.setText("");
        }
        if (location.hasSpeed()) {
            mSpeedView.setText(doubleToString(location.getSpeed(), 1) + " m/sec");
        } else {
            mSpeedView.setText("");
        }
        if (location.hasBearing()) {
            mBearingView.setText(doubleToString(location.getBearing(), 1) + " deg");
        } else {
            mBearingView.setText("");
        }
        updateFixTime();
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    public void onProviderEnabled(String provider) {
    }

    public void onProviderDisabled(String provider) {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        mRes = getResources();
        View v = inflater.inflate(R.layout.gps_status, container,
                false);

        mLatitudeView = (TextView) v.findViewById(R.id.latitude);
        mLongitudeView = (TextView) v.findViewById(R.id.longitude);
        mFixTimeView = (TextView) v.findViewById(R.id.fix_time);
        mTTFFView = (TextView) v.findViewById(R.id.ttff);
        mAltitudeView = (TextView) v.findViewById(R.id.altitude);
        mAccuracyView = (TextView) v.findViewById(R.id.accuracy);
        mSpeedView = (TextView) v.findViewById(R.id.speed);
        mBearingView = (TextView) v.findViewById(R.id.bearing);

        mLatitudeView.setText(EMPTY_LAT_LONG);
        mLongitudeView.setText(EMPTY_LAT_LONG);

        flagUsa = getResources().getDrawable(R.drawable.ic_flag_usa);
        flagRussia = getResources().getDrawable(R.drawable.ic_flag_russia);
        flagJapan = getResources().getDrawable(R.drawable.ic_flag_japan);
        flagChina = getResources().getDrawable(R.drawable.ic_flag_china);
        flagGalileo = getResources().getDrawable(R.drawable.ic_flag_galileo);

        GridView gridView = (GridView) v.findViewById(R.id.sv_grid);
        mAdapter = new SvGridAdapter(getActivity());
        gridView.setAdapter(mAdapter);
        gridView.setFocusable(false);
        gridView.setFocusableInTouchMode(false);

        GpsTestActivity.getInstance().addListener(this);

        return v;
    }

    private void setStarted(boolean navigating) {
        if (navigating != mNavigating) {
            if (navigating) {

            } else {
                mLatitudeView.setText(EMPTY_LAT_LONG);
                mLongitudeView.setText(EMPTY_LAT_LONG);
                mFixTime = 0;
                updateFixTime();
                mTTFFView.setText("");
                mAltitudeView.setText("");
                mAccuracyView.setText("");
                mSpeedView.setText("");
                mBearingView.setText("");
                mSvCount = 0;
                mAdapter.notifyDataSetChanged();
            }
            mNavigating = navigating;
        }
    }

    private void updateFixTime() {
        if (mFixTime == 0 || !GpsTestActivity.getInstance().mStarted) {
            mFixTimeView.setText("");
        } else {
            mFixTimeView.setText(DateUtils.getRelativeTimeSpanString(
                    mFixTime, System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        GpsTestActivity gta = GpsTestActivity.getInstance();
        setStarted(gta.mStarted);
    }

    public void onGpsStarted() {
        setStarted(true);
    }

    public void onGpsStopped() {
        setStarted(false);
    }

    @SuppressLint("NewApi")
    public void gpsStart() {
        //Reset flag for detecting first fix
        mGotFix = false;
    }

    public void gpsStop() {
    }

    @Deprecated
    public void onGpsStatusChanged(int event, GpsStatus status) {
        switch (event) {
            case GpsStatus.GPS_EVENT_STARTED:
                setStarted(true);
                break;

            case GpsStatus.GPS_EVENT_STOPPED:
                setStarted(false);
                break;

            case GpsStatus.GPS_EVENT_FIRST_FIX:
                break;

            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                updateLegacyStatus(status);
                break;
        }
    }

    @Override
    public void onGnssFirstFix(int ttffMillis) {

    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onSatelliteStatusChanged(GnssStatus status) {
        updateGnssStatus(status);
    }

    @Override
    public void onGnssStarted() {
        setStarted(true);
    }

    @Override
    public void onGnssStopped() {
        setStarted(false);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
        if (mPrns == null) {
            // We don't have any satellites to store PRNs for yet (no GnssStatus updates) - no op;
            return;
        }
        if (mSnrs == null) {
            mSnrs = new float[mPrns.length];
        }
        if (mSnrsForSats == null) {
            mSnrsForSats = new HashMap<>(mPrns.length);
        }
        mSnrsForSats.clear();

        Collection<GnssMeasurement> measurements = event.getMeasurements();

        // Write all SNRs for svid/constellation types to HashMap for easy retrieval
        for (GnssMeasurement m : measurements) {
            String key = GpsTestUtil.createGnssSatelliteKey(m.getSvid(), m.getConstellationType());
            mSnrsForSats.put(key, m.getSnrInDb());
        }

        // Write correct SNR value for the given satellite/constellation to correct value in array
        for (int i = 0; i < mPrns.length; i++) {
            String key = GpsTestUtil.createGnssSatelliteKey(mPrns[i], mConstellationType[i]);
            mSnrs[i] = mSnrsForSats.get(key).floatValue();
        }

        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onOrientationChanged(double orientation, double tilt) {
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void updateGnssStatus(GnssStatus status) {
        setStarted(true);
        // update the fix time regularly, since it is displaying relative time
        updateFixTime();

        int length = status.getSatelliteCount();
        if (mPrns == null) {
            mPrns = new int[length];
            mSnrs = new float[length];
            mSvElevations = new float[length];
            mSvAzimuths = new float[length];
            mConstellationType = new int[length];
            mHasEphemeris = new boolean[length];
            mHasAlmanac = new boolean[length];
            mUsedInFix = new boolean[length];
        }

        mSvCount = 0;
        while (mSvCount < length) {
            int prn = status.getSvid(mSvCount);
            mPrns[mSvCount] = prn;
            mConstellationType[mSvCount] = status.getConstellationType(mSvCount);
            mSnrs[mSvCount] = 0.0f; // This is replaced later by GnssMeasurement.getSnrInDb()
            mSvElevations[mSvCount] = status.getElevationDegrees(mSvCount);
            mSvAzimuths[mSvCount] = status.getAzimuthDegrees(mSvCount);
            mHasEphemeris[mSvCount] = status.hasEphemerisData(mSvCount);
            mHasAlmanac[mSvCount] = status.hasAlmanacData(mSvCount);
            mUsedInFix[mSvCount] = status.usedInFix(mSvCount);

            mSvCount++;
        }

        mAdapter.notifyDataSetChanged();
    }

    @Deprecated
    private void updateLegacyStatus(GpsStatus status) {
        setStarted(true);
        // update the fix time regularly, since it is displaying relative time
        updateFixTime();

        Iterator<GpsSatellite> satellites = status.getSatellites().iterator();

        if (mPrns == null) {
            int length = status.getMaxSatellites();
            mPrns = new int[length];
            mSnrs = new float[length];
            mSvElevations = new float[length];
            mSvAzimuths = new float[length];
            // Constellation type isn't used, but instantiate it to avoid NPE in legacy devices
            mConstellationType = new int[length];
            mHasEphemeris = new boolean[length];
            mHasAlmanac = new boolean[length];
            mUsedInFix = new boolean[length];
        }

        mSvCount = 0;
        while (satellites.hasNext()) {
            GpsSatellite satellite = satellites.next();
            int prn = satellite.getPrn();
            int prnBit = (1 << (prn - 1));
            mPrns[mSvCount] = prn;
            mSnrs[mSvCount] = satellite.getSnr();
            mSvElevations[mSvCount] = satellite.getElevation();
            mSvAzimuths[mSvCount] = satellite.getAzimuth();
            mHasEphemeris[mSvCount] = satellite.hasEphemeris();
            mHasAlmanac[mSvCount] = satellite.hasAlmanac();
            mUsedInFix[mSvCount] = satellite.usedInFix();
            mSvCount++;
        }

        mAdapter.notifyDataSetChanged();
    }

    private class SvGridAdapter extends BaseAdapter {

        private Context mContext;

        public SvGridAdapter(Context c) {
            mContext = c;
        }

        public int getCount() {
            // add 1 for header row
            return (mSvCount + 1) * COLUMN_COUNT;
        }

        public Object getItem(int position) {
            Log.d(TAG, "getItem(" + position + ")");
            return "foo";
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            TextView textView = null;
            ImageView imageView = null;

            int row = position / COLUMN_COUNT;
            int column = position % COLUMN_COUNT;

            if (convertView != null) {
                if (convertView instanceof ImageView) {
                    imageView = (ImageView) convertView;
                } else if (convertView instanceof TextView) {
                    textView = (TextView) convertView;
                }
            }

            CharSequence text = null;

            if (row == 0) {
                switch (column) {
                    case PRN_COLUMN:
                        text = mRes.getString(R.string.gps_prn_column_label);
                        break;
                    case FLAG_IMAGE_COLUMN:
                        text = mRes.getString(R.string.gps_flag_image_label);
                        break;
                    case SNR_COLUMN:
                        text = mRes.getString(R.string.gps_snr_column_label);
                        break;
                    case ELEVATION_COLUMN:
                        text = mRes.getString(R.string.gps_elevation_column_label);
                        break;
                    case AZIMUTH_COLUMN:
                        text = mRes.getString(R.string.gps_azimuth_column_label);
                        break;
                    case FLAGS_COLUMN:
                        text = mRes.getString(R.string.gps_flags_column_label);
                        break;
                }
            } else {
                row--;
                switch (column) {
                    case PRN_COLUMN:
                        text = Integer.toString(mPrns[row]);
                        break;
                    case FLAG_IMAGE_COLUMN:
                        if (imageView == null) {
                            imageView = new ImageView(mContext);
                            imageView.setScaleType(ImageView.ScaleType.FIT_START);
                        }
                        GnssType type;
                        if (GpsTestUtil.isGnssStatusListenerSupported()) {
                            type = GpsTestUtil.getGnssConstellationType(mConstellationType[row]);
                        } else {
                            type = GpsTestUtil.getGnssType(mPrns[row]);
                        }
                        switch (type) {
                            case NAVSTAR:
                                imageView.setImageDrawable(flagUsa);
                                break;
                            case GLONASS:
                                imageView.setImageDrawable(flagRussia);
                                break;
                            case QZSS:
                                imageView.setImageDrawable(flagJapan);
                                break;
                            case BEIDOU:
                                imageView.setImageDrawable(flagChina);
                                break;
                            case GALILEO:
                                imageView.setImageDrawable(flagGalileo);
                                break;
                        }
                        return imageView;
                    case SNR_COLUMN:
                        if (mSnrs[row] != 0.0f) {
                            text = Float.toString(mSnrs[row]);
                        } else {
                            text = "";
                        }
                        break;
                    case ELEVATION_COLUMN:
                        text = Float.toString(mSvElevations[row]);
                        break;
                    case AZIMUTH_COLUMN:
                        text = Float.toString(mSvAzimuths[row]);
                        break;
                    case FLAGS_COLUMN:
                        char[] flags = new char[3];
                        flags[0] = !mHasEphemeris[row] ? ' ' : 'E';
                        flags[1] = !mHasAlmanac[row] ? ' ' : 'A';
                        flags[2] = !mUsedInFix[row] ? ' ' : 'U';
                        text = new String(flags);
                        break;
                }
            }

            if (textView == null) {
                textView = new TextView(mContext);
            }

            textView.setText(text);
            return textView;
        }
    }
}