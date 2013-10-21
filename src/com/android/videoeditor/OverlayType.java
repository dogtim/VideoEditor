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

package com.android.videoeditor;

import android.content.Context;

import com.android.videoeditor.service.MovieOverlay;

/**
 * An overlay
 */
public class OverlayType {
    /**
     * Get overlays
     *
     * @param context The context
     *
     * @return The array of overlay
     */
    public static OverlayType[] getOverlays(Context context) {
        final OverlayType[] overlays = new OverlayType[4];
        overlays[0] = new OverlayType(context.getString(R.string.overlay_preview_center),
                MovieOverlay.OVERLAY_TYPE_CENTER_1);
        overlays[1] = new OverlayType(context.getString(R.string.overlay_preview_bottom),
                MovieOverlay.OVERLAY_TYPE_BOTTOM_1);
        overlays[2] = new OverlayType(context.getString(R.string.overlay_preview_center),
                MovieOverlay.OVERLAY_TYPE_CENTER_2);
        overlays[3] = new OverlayType(context.getString(R.string.overlay_preview_bottom),
                MovieOverlay.OVERLAY_TYPE_BOTTOM_2);

        return overlays;
    }

    // Instance variables
    private final String mName;
    private final int mType;

    /**
     * Constructor
     */
    public OverlayType(String name, int type) {
        mName = name;
        mType = type;
    }

    /**
     * @return The theme name
     */
    public String getName() {
        return mName;
    }

    /**
     * @return The type
     */
    public int getType() {
        return mType;
    }
}
