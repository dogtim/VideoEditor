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

import com.android.videoeditor.R;

import android.content.Context;
import android.media.videoeditor.Transition;
import android.media.videoeditor.TransitionAlpha;
import android.media.videoeditor.TransitionCrossfade;
import android.media.videoeditor.TransitionFadeBlack;
import android.media.videoeditor.TransitionSliding;

/**
 * Movie theme description
 */
public class MovieTheme {
    // Defined themes
    public static final String THEME_TRAVEL = "travel";
    public static final String THEME_SURFING = "surfing";
    public static final String THEME_FILM = "film";
    public static final String THEME_ROCKANDROLL = "rockandroll";

    /**
     * Get theme by name
     *
     * @param context The context
     * @param theme The theme id
     * @return The theme
     */
    public static MovieTheme getTheme(Context context, String theme) {
        if (THEME_TRAVEL.equals(theme)) {
            return new MovieTheme(THEME_TRAVEL, R.string.theme_name_travel,
                    R.drawable.theme_preview_travel, 0,
                    new MovieTransition(TransitionFadeBlack.class, null, 1500,
                            Transition.BEHAVIOR_SPEED_UP),
                    new MovieTransition(TransitionCrossfade.class, null, 1000,
                            Transition.BEHAVIOR_LINEAR),
                    new MovieTransition(TransitionFadeBlack.class, null, 1500,
                            Transition.BEHAVIOR_SPEED_DOWN),
                    new MovieOverlay(null, 0, 1000, context.getString(R.string.theme_travel_title),
                            context.getString(R.string.theme_travel_subtitle),
                            MovieOverlay.OVERLAY_TYPE_CENTER_1),
                    new MovieAudioTrack(R.raw.theme_travel_audio_track));
        } else if (THEME_SURFING.equals(theme)) {
            return new MovieTheme(THEME_SURFING, R.string.theme_name_surfing,
                    R.drawable.theme_preview_surfing, 0,
                    new MovieTransition(TransitionFadeBlack.class, null, 1500,
                            Transition.BEHAVIOR_SPEED_UP),
                    new MovieTransition(TransitionAlpha.class, null, 1000,
                            Transition.BEHAVIOR_LINEAR, R.raw.mask_diagonal, 50, false),
                    new MovieTransition(TransitionFadeBlack.class, null, 1500,
                            Transition.BEHAVIOR_SPEED_DOWN),
                    new MovieOverlay(null, 0, 1000,
                            context.getString(R.string.theme_surfing_title),
                            context.getString(R.string.theme_surfing_subtitle),
                            MovieOverlay.OVERLAY_TYPE_BOTTOM_1),
                    new MovieAudioTrack(R.raw.theme_surfing_audio_track));
        } else if (THEME_FILM.equals(theme)) {
            return new MovieTheme(THEME_FILM, R.string.theme_name_film,
                    R.drawable.theme_preview_film, 0,
                    new MovieTransition(TransitionFadeBlack.class, null, 1500,
                            Transition.BEHAVIOR_SPEED_UP),
                    new MovieTransition(TransitionCrossfade.class, null, 1000,
                            Transition.BEHAVIOR_LINEAR),
                    new MovieTransition(TransitionFadeBlack.class, null, 1500,
                            Transition.BEHAVIOR_SPEED_DOWN),
                    new MovieOverlay(null, 0, 1000, context.getString(R.string.theme_film_title),
                            context.getString(R.string.theme_film_subtitle),
                            MovieOverlay.OVERLAY_TYPE_BOTTOM_1),
                     new MovieAudioTrack(R.raw.theme_film_audio_track));
        } else if (THEME_ROCKANDROLL.equals(theme)) {
            return new MovieTheme(THEME_ROCKANDROLL, R.string.theme_name_rock_and_roll,
                    R.drawable.theme_preview_rock_and_roll, 0,
                    new MovieTransition(TransitionFadeBlack.class, null, 1500,
                            Transition.BEHAVIOR_SPEED_UP),
                    new MovieTransition(TransitionSliding.class, null, 1000,
                            Transition.BEHAVIOR_LINEAR,
                            TransitionSliding.DIRECTION_LEFT_OUT_RIGHT_IN),
                    new MovieTransition(TransitionFadeBlack.class, null, 1500,
                            Transition.BEHAVIOR_SPEED_DOWN),
                    new MovieOverlay(null, 0, 1000, context.getString(
                            R.string.theme_rock_and_roll_title),
                            context.getString(R.string.theme_rock_and_roll_subtitle),
                            MovieOverlay.OVERLAY_TYPE_BOTTOM_1),
                    new MovieAudioTrack(R.raw.theme_rockandroll_audio_track));
        } else {
            return null;
        }
    }

    // Instance variables
    private final String mId;
    private final int mNameResId;
    private final int mPreviewImageResId;
    private final int mPreviewMovieResId;
    private final MovieTransition mBeginTransition;
    private final MovieTransition mMidTransition;
    private final MovieTransition mEndTransition;
    private final MovieOverlay mOverlay;
    private final MovieAudioTrack mAudioTrack;

    /**
     * Constructor
     *
     * @param id The theme id
     * @param nameResId The string resource id of the theme name
     * @param previewImageResId The preview image
     * @param previewMovieResId The preview movie
     * @param beginTransition The movie begin transition
     * @param midTransition Transitions between media items
     * @param endTransition The movie end transition
     * @param overlay The title (applied only to the first media item)
     * @param audioTrack The audio track
     */
    private MovieTheme(String id, int nameResId, int previewImageResId, int previewMovieResId,
            MovieTransition beginTransition, MovieTransition midTransition,
            MovieTransition endTransition, MovieOverlay overlay, MovieAudioTrack audioTrack) {
        mId = id;

        mNameResId = nameResId;
        mPreviewImageResId = previewImageResId;
        mPreviewMovieResId = previewMovieResId;

        mBeginTransition = beginTransition;
        mMidTransition = midTransition;
        mEndTransition = endTransition;

        mOverlay = overlay;

        mAudioTrack = audioTrack;
    }

    /**
     * @return The id
     */
    public String getId() {
        return mId;
    }

    /**
     * @return The name resource id
     */
    public int getNameResId() {
        return mNameResId;
    }

    /**
     * @return The preview image resource id
     */
    public int getPreviewImageResId() {
        return mPreviewImageResId;
    }

    /**
     * @return The preview movie resource id
     */
    public int getPreviewMovieResId() {
        return mPreviewMovieResId;
    }

    /**
     * @return The begin transition
     */
    public MovieTransition getBeginTransition() {
        return mBeginTransition;
    }

    /**
     * @return The mid transition
     */
    public MovieTransition getMidTransition() {
        return mMidTransition;
    }

    /**
     * @return The end transition
     */
    public MovieTransition getEndTransition() {
        return mEndTransition;
    }

    /**
     * @return The overlay
     */
    public MovieOverlay getOverlay() {
        return mOverlay;
    }

    /**
     * @return The audio track
     */
    public MovieAudioTrack getAudioTrack() {
        return mAudioTrack;
    }
}
