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

import java.io.IOException;

import android.content.Context;
import android.media.videoeditor.MediaItem;
import android.media.videoeditor.Transition;
import android.media.videoeditor.TransitionAlpha;
import android.media.videoeditor.TransitionCrossfade;
import android.media.videoeditor.TransitionFadeBlack;
import android.media.videoeditor.TransitionSliding;

import com.android.videoeditor.R;
import com.android.videoeditor.TransitionType;
import com.android.videoeditor.util.FileUtils;

/**
 * This class represents a transition in the user interface
 */
public class MovieTransition {
    // The unique id of the transition
    private final String mUniqueId;
    private final Class<?> mTypeClass;
    private final int mType;
    private final int mBehavior;
    private final int mSlidingDirection;
    private final String mAlphaMaskFilename;
    private final int mAlphaMaskResId;
    private final int mAlphaMaskBlendingPercent;
    private final boolean mAlphaInvert;
    private long mDurationMs;

    private long mAppDurationMs;

    /**
     * Constructor
     *
     * @param transition The transition
     */
    MovieTransition(Transition transition) {
        mTypeClass = transition.getClass();
        mUniqueId = transition.getId();
        mAppDurationMs = mDurationMs = transition.getDuration();
        mBehavior = transition.getBehavior();
        if (transition instanceof TransitionSliding) {
            mSlidingDirection = ((TransitionSliding)transition).getDirection();
        } else {
            mSlidingDirection = -1;
        }

        if (transition instanceof TransitionAlpha) {
            final TransitionAlpha ta = (TransitionAlpha)transition;
            mAlphaMaskFilename = ta.getMaskFilename();
            mAlphaMaskResId = 0;
            mAlphaMaskBlendingPercent = ta.getBlendingPercent();
            mAlphaInvert = ta.isInvert();
        } else {
            mAlphaMaskFilename = null;
            mAlphaMaskResId = 0;
            mAlphaMaskBlendingPercent = 0;
            mAlphaInvert = false;
        }

        mType = toType();
    }

    /**
     * Constructor
     *
     * @param type The transition type
     * @param id The transition id
     * @param durationMs The duration in milliseconds
     * @param behavior The behavior
     */
    MovieTransition(Class<?> type, String id, long durationMs, int behavior) {
        mTypeClass = type;
        mUniqueId = id;
        mAppDurationMs = mDurationMs = durationMs;
        mBehavior = behavior;
        mSlidingDirection = -1;
        mAlphaMaskFilename = null;
        mAlphaMaskResId = 0;
        mAlphaMaskBlendingPercent = 0;
        mAlphaInvert = false;

        mType = toType();
    }

    /**
     * Constructor for sliding transitions
     *
     * @param type The transition type
     * @param id The transition id
     * @param durationMs The duration in milliseconds
     * @param behavior The behavior
     * @param slidingDirection The sliding direction
     */
    MovieTransition(Class<?> type, String id, long durationMs, int behavior,
            int slidingDirection) {
        mTypeClass = type;
        mUniqueId = id;
        mAppDurationMs = mDurationMs = durationMs;
        mBehavior = behavior;
        mSlidingDirection = slidingDirection;
        mAlphaMaskFilename = null;
        mAlphaMaskResId = 0;
        mAlphaMaskBlendingPercent = 0;
        mAlphaInvert = false;

        mType = toType();
    }

    /**
     * Constructor for alpha transitions
     *
     * @param type The transition type
     * @param id The transition id
     * @param durationMs The duration in milliseconds
     * @param behavior The behavior
     * @param maskResId The mask resource id
     * @param blendingPercent The blending (in percentages)
     * @param invert true to invert the direction of the alpha blending
     */
    MovieTransition(Class<?> type, String id, long durationMs, int behavior,
            int maskResId, int blendingPercent, boolean invert) {
        mTypeClass = type;
        mUniqueId = id;
        mAppDurationMs = mDurationMs = durationMs;
        mBehavior = behavior;
        mSlidingDirection = -1;
        mAlphaMaskFilename = null;
        mAlphaMaskResId = maskResId;
        mAlphaMaskBlendingPercent = blendingPercent;
        mAlphaInvert = invert;

        mType = toType();
    }

    /**
     * @return The type class of the transition
     */
    public Class<?> getTypeClass() {
        return mTypeClass;
    }

    /**
     * @return The type of the transition
     */
    public int getType() {
        return mType;
    }

    /**
     * @return The id of the transition
     */
    public String getId() {
        return mUniqueId;
    }

    /**
     * Set the duration of the transition.
     *
     * @param durationMs the duration of the transition in milliseconds
     */
    void setDuration(long durationMs) {
        mDurationMs = durationMs;
    }

    /**
     * @return the duration of the transition in milliseconds
     */
    long getDuration() {
        return mDurationMs;
    }

    /**
     * Set the duration of the transition
     *
     * @param durationMs The duration in milliseconds
     */
    public void setAppDuration(long durationMs) {
        mAppDurationMs = durationMs;
    }

    /**
     * @return The duration of the transition
     */
    public long getAppDuration() {
        return mAppDurationMs;
    }

    /**
     * @return The behavior
     */
    public int getBehavior() {
        return mBehavior;
    }

    /**
     * @return The sliding direction (only for TransitionSliding)
     */
    public int getSlidingDirection() {
        return mSlidingDirection;
    }

    /**
     * @return The alpha mask filename
     */
    public String getAlphaMaskFilename() {
        return mAlphaMaskFilename;
    }

    /**
     * @return The alpha mask resource id
     */
    public int getAlphaMaskResId() {
        return mAlphaMaskResId;
    }

    /**
     * @return The alpha blending percentage
     */
    public int getAlphaMaskBlendingPercent() {
        return mAlphaMaskBlendingPercent;
    }

    /**
     * @return true if the direction of the alpha blending is inverted
     */
    public boolean isAlphaInverted() {
        return mAlphaInvert;
    }

    /**
     * Create a VideoEditor transition
     *
     * @param context the context
     * @param afterMediaItem Add the transition after this media item
     * @param beforeMediaItem Add the transition before this media item
     *
     * @return The transition
     */
    Transition buildTransition(Context context, MediaItem afterMediaItem,
            MediaItem beforeMediaItem) throws IOException {
        if (TransitionCrossfade.class.equals(mTypeClass)) {
            return new TransitionCrossfade(ApiService.generateId(), afterMediaItem,
                    beforeMediaItem, mDurationMs, mBehavior);
        } else if (TransitionAlpha.class.equals(mTypeClass)) {
            return new TransitionAlpha(ApiService.generateId(), afterMediaItem,
                    beforeMediaItem, mDurationMs, mBehavior,
                    FileUtils.getMaskFilename(context, mAlphaMaskResId), mAlphaMaskBlendingPercent,
                    mAlphaInvert);
        } else if (TransitionFadeBlack.class.equals(mTypeClass)) {
            return new TransitionFadeBlack(ApiService.generateId(), afterMediaItem,
                    beforeMediaItem, mDurationMs, mBehavior);
        } else if (TransitionSliding.class.equals(mTypeClass)) {
            return new TransitionSliding(ApiService.generateId(), afterMediaItem,
                    beforeMediaItem, mDurationMs, mBehavior, mSlidingDirection);
        } else {
            return null;
        }
    }

    /**
     * Convert the type to an integer
     *
     * @return The type
     */
    private int toType() {
        if (TransitionCrossfade.class.equals(mTypeClass)) {
            return TransitionType.TRANSITION_TYPE_CROSSFADE;
        } else if (TransitionAlpha.class.equals(mTypeClass)) {
            final int rawId = FileUtils.getMaskRawId(mAlphaMaskFilename);
            switch (rawId) {
                case R.raw.mask_contour: {
                    return TransitionType.TRANSITION_TYPE_ALPHA_CONTOUR;
                }

                case R.raw.mask_diagonal: {
                    return TransitionType.TRANSITION_TYPE_ALPHA_DIAGONAL;
                }

                default: {
                    throw new IllegalArgumentException("Unknown id for: " + mAlphaMaskFilename);
                }
            }
        } else if (TransitionFadeBlack.class.equals(mTypeClass)) {
            return TransitionType.TRANSITION_TYPE_FADE_BLACK;
        } else if (TransitionSliding.class.equals(mTypeClass)) {
            switch (mSlidingDirection) {
                case TransitionSliding.DIRECTION_BOTTOM_OUT_TOP_IN: {
                    return TransitionType.TRANSITION_TYPE_SLIDING_BOTTOM_OUT_TOP_IN;
                }

                case TransitionSliding.DIRECTION_LEFT_OUT_RIGHT_IN: {
                    return TransitionType.TRANSITION_TYPE_SLIDING_LEFT_OUT_RIGHT_IN;
                }

                case TransitionSliding.DIRECTION_RIGHT_OUT_LEFT_IN: {
                    return TransitionType.TRANSITION_TYPE_SLIDING_RIGHT_OUT_LEFT_IN;
                }

                case TransitionSliding.DIRECTION_TOP_OUT_BOTTOM_IN: {
                    return TransitionType.TRANSITION_TYPE_SLIDING_TOP_OUT_BOTTOM_IN;
                }

                default: {
                    throw new IllegalArgumentException("Unknown direction: " + mSlidingDirection);
                }
            }
        } else {
            throw new IllegalArgumentException("Unknown type: " + mTypeClass);
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object object) {
        if (!(object instanceof MovieTransition)) {
            return false;
        }
        return mUniqueId.equals(((MovieTransition)object).mUniqueId);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return mUniqueId.hashCode();
    }
}
