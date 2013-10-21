/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.videoeditor.service;

import java.util.Map;

import android.media.videoeditor.Overlay;
import android.os.Bundle;


/**
 * The representation of an overlay in the user interface
 */
public class MovieOverlay {
    // Overlay types
    public static final int OVERLAY_TYPE_CENTER_1 = 0;
    public static final int OVERLAY_TYPE_BOTTOM_1 = 1;
    public static final int OVERLAY_TYPE_CENTER_2 = 2;
    public static final int OVERLAY_TYPE_BOTTOM_2 = 3;

    // User attribute keys
    private static final String KEY_TYPE = "type";
    private static final String KEY_TITLE = "title";
    private static final String KEY_SUBTITLE = "subtitle";

    // Instance variables
    private final String mUniqueId;
    private long mStartTimeMs;
    private long mDurationMs;
    private String mTitle;
    private String mSubtitle;
    private int mType;

    private long mAppStartTimeMs;
    private long mAppDurationMs;

    /**
     * Default constructor
     */
    @SuppressWarnings("unused")
    private MovieOverlay() {
        this(null);
    }

    /**
     * Constructor
     *
     * @param overlay The overlay
     */
    MovieOverlay(Overlay overlay) {
        mUniqueId = overlay.getId();
        mAppStartTimeMs = mStartTimeMs = overlay.getStartTime();
        mAppDurationMs = mDurationMs = overlay.getDuration();

        final Map<String, String> userAttributes = overlay.getUserAttributes();
        mTitle = userAttributes.get(KEY_TITLE);
        mSubtitle = userAttributes.get(KEY_SUBTITLE);
        mType = Integer.parseInt(userAttributes.get(KEY_TYPE));
    }

    /**
     * Constructor
     *
     * @param id The overlay id
     * @param startTimeMs The start time
     * @param durationMs The duration
     * @param title The title
     * @param subTitle The sub title
     * @param type The title type
     */
    MovieOverlay(String id, long startTimeMs, long durationMs, String title,
            String subTitle, int type) {
        mUniqueId = id;
        mAppStartTimeMs = mStartTimeMs = startTimeMs;
        mAppDurationMs = mDurationMs = durationMs;

        mTitle = title;
        mSubtitle = subTitle;
        mType = type;
    }

    /**
     * @return The id of this overlay
     */
    public String getId() {
        return mUniqueId;
    }

    /**
     * If a preview or export is in progress, then this change is effective for
     * next preview or export session.
     *
     * @param durationMs The duration in milliseconds
     */
    void setDuration(long durationMs) {
        mDurationMs = durationMs;
    }

    /**
     * @return The duration of the overlay effect
     */
    long getDuration() {
        return mDurationMs;
    }

    /**
     * If a preview or export is in progress, then this change is effective for
     * next preview or export session.
     *
     * @param durationMs The duration in milliseconds
     */
    public void setAppDuration(long durationMs) {
        mAppDurationMs = durationMs;
    }

    /**
     * @return The duration of the overlay effect
     */
    public long getAppDuration() {
        return mAppDurationMs;
    }

    /**
     * Set the start time for the overlay. If a preview or export is in
     * progress, then this change is effective for next preview or export
     * session.
     *
     * @param startTimeMs start time in milliseconds
     */
    void setStartTime(long startTimeMs) {
        mStartTimeMs = startTimeMs;
    }

    /**
     * @return the start time of the overlay
     */
    long getStartTime() {
        return mStartTimeMs;
    }

    /**
     * Set the start time of this audio track relative to the storyboard
     * timeline. Default value is 0.
     *
     * @param startTimeMs the start time in milliseconds
     */
    public void setAppStartTime(long startTimeMs) {
        mAppStartTimeMs = startTimeMs;
    }

    /**
     * Get the start time of this audio track relative to the storyboard
     * timeline.
     *
     * @return The start time in milliseconds
     */
    public long getAppStartTime() {
        return mAppStartTimeMs;
    }

    /**
     * @return The title
     */
    public String getTitle() {
        return mTitle;
    }

    /**
     * @return The subtitle
     */
    public String getSubtitle() {
        return mSubtitle;
    }

    /**
     * @return The type
     */
    public int getType() {
        return mType;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object object) {
        if (!(object instanceof MovieOverlay)) {
            return false;
        }
        return mUniqueId.equals(((MovieOverlay)object).mUniqueId);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return mUniqueId.hashCode();
    }

    /**
     * Build the user attributes
     *
     * @return The user attributes
     */
    public Bundle buildUserAttributes() {
        final Bundle userAttributes = new Bundle(4);
        userAttributes.putInt(KEY_TYPE, mType);
        userAttributes.putString(KEY_TITLE, mTitle);
        userAttributes.putString(KEY_SUBTITLE, mSubtitle);
        return userAttributes;
    }

    /**
     * Build the user attributes
     *
     * @param type The overlay type
     * @param title The overlay title
     * @param subtitle The overlay subtitle
     *
     * @return The user attributes
     */
    public static Bundle buildUserAttributes(int type, String title, String subtitle) {
        final Bundle userAttributes = new Bundle(4);
        userAttributes.putInt(KEY_TYPE, type);
        userAttributes.putString(KEY_TITLE, title);
        userAttributes.putString(KEY_SUBTITLE, subtitle);
        return userAttributes;
    }

    /**
     * @param userAttributes The user attributes
     */
    void updateUserAttributes(Bundle userAttributes) {
        mType = userAttributes.getInt(KEY_TYPE);
        mTitle = userAttributes.getString(KEY_TITLE);
        mSubtitle = userAttributes.getString(KEY_SUBTITLE);
    }

    /**
     * Get the type of the value corresponding to the specified key
     *
     * @param name The key name
     *
     * @return The type
     */
    public static Class<?> getAttributeType(String name) {
        if (KEY_TYPE.equals(name)) {
            return Integer.class;
        } else {
            return String.class;
        }
    }

    /**
     * @param userAttributes The user attributes
     *
     * @return The type
     */
    public static int getType(Bundle userAttributes) {
        return userAttributes.getInt(KEY_TYPE);
    }

    /**
     * @param userAttributes The user attributes
     *
     * @return The title
     */
    public static String getTitle(Bundle userAttributes) {
        return userAttributes.getString(KEY_TITLE);
    }

    /**
     * @param userAttributes The user attributes
     *
     * @return The subtitle
     */
    public static String getSubtitle(Bundle userAttributes) {
        return userAttributes.getString(KEY_SUBTITLE);
    }
}
