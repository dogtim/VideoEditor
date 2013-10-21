/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.videoeditor.widgets;

import com.android.videoeditor.service.MovieMediaItem;

/**
 * Listener that listens to state changes of {@link MediaLinearLayout}.
 */
public interface MediaLinearLayoutListener {
    /**
     * Request scrolling by an offset amount
     *
     * @param scrollBy The amount to scroll
     * @param smooth true to scroll smoothly
     */
    void onRequestScrollBy(int scrollBy, boolean smooth);

    /**
     * Request scrolling to a specified time position
     *
     * @param scrollToTime The scroll position
     * @param smooth true to scroll smoothly
     */
    void onRequestMovePlayhead(long scrollToTime, boolean smooth);

    /**
     * Add a new media item
     *
     * @param afterMediaItemId Add media item after this media item id
     */
    void onAddMediaItem(String afterMediaItemId);

    /**
     * A media item enters trimming mode
     *
     * @param mediaItem The media item
     */
    void onTrimMediaItemBegin(MovieMediaItem mediaItem);

    /**
     * A media item is being trimmed
     *
     * @param mediaItem The media item
     * @param timeMs The time where the trim occurs
     */
    void onTrimMediaItem(MovieMediaItem mediaItem, long timeMs);

    /**
     * A media has been trimmed
     *
     * @param mediaItem The media item
     * @param timeMs The time where the trim occurs
     */
    void onTrimMediaItemEnd(MovieMediaItem mediaItem, long timeMs);
}
