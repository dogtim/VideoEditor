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

package com.android.videoeditor.util;

import com.android.videoeditor.service.MovieMediaItem;

/**
 * Media item utilities
 */
public class MediaItemUtils {
    /**
     * It is not possible to instantiate this type
     */
    private MediaItemUtils() {
    }

    /**
     * @return The default duration of an image in the timeline
     */
    public static long getDefaultImageDuration() {
        return 3000;
    }

    /**
     * @return The minimum image item duration
     */
    public static long getMinimumImageItemDuration() {
        return 1000;
    }

    /**
     * @return The minimum video clip duration
     */
    public static long getMinimumVideoItemDuration() {
        return 1000;
    }

    /**
     * @return The minimum media duration
     */
    public static long getMinimumMediaItemDuration(MovieMediaItem mediaItem) {
        return mediaItem.isVideoClip() ? getMinimumVideoItemDuration() :
            getMinimumVideoItemDuration();
    }
}
