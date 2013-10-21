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

package dogtim.android.videoeditor.service;

import dogtim.android.videoeditor.EffectType;

import android.graphics.Rect;
import android.media.videoeditor.Effect;
import android.media.videoeditor.EffectColor;
import android.media.videoeditor.EffectKenBurns;

/**
 * An effect can only be applied to a single media item.
 */
public class MovieEffect {
    // Instance variables
    private final String mUniqueId;
    private final int mType;
    private long mDurationMs;
    private long mStartTimeMs;
    private Rect mStartRect, mEndRect;

    /**
     * Default constructor
     */
    @SuppressWarnings("unused")
    private MovieEffect() {
        this (null);
    }

    /**
     * Constructor
     *
     * @param effect The effect
     */
    MovieEffect(Effect effect) {
        mUniqueId = effect.getId();
        mStartTimeMs = effect.getStartTime();
        mDurationMs = effect.getDuration();
        if (effect instanceof EffectKenBurns) {
            mStartRect = ((EffectKenBurns)effect).getStartRect();
            mEndRect = ((EffectKenBurns)effect).getEndRect();
        } else {
            mStartRect = null;
            mEndRect = null;
        }

        mType = toType(effect);
    }

    /**
     * @return The effect type
     */
    public int getType() {
        return mType;
    }
    /**
     * @return The id of the effect
     */
    public String getId() {
        return mUniqueId;
    }

    /**
     * Set the duration of the effect. If a preview or export is in progress,
     * then this change is effective for next preview or export session. s
     *
     * @param durationMs of the effect in milliseconds
     */
    void setDuration(long durationMs) {
        mDurationMs = durationMs;
    }

    /**
     * Get the duration of the effect
     *
     * @return The duration of the effect in milliseconds
     */
    public long getDuration() {
        return mDurationMs;
    }

    /**
     * Set start time of the effect. If a preview or export is in progress, then
     * this change is effective for next preview or export session.
     *
     * @param startTimeMs The start time of the effect relative to the beginning
     *            of the media item in milliseconds
     */
    void setStartTime(long startTimeMs) {
        mStartTimeMs = startTimeMs;
    }

    /**
     * @return The start time in milliseconds
     */
    long getStartTime() {
        return mStartTimeMs;
    }

    /**
     * Set the Ken Burns start and end rectangles
     *
     * @param startRect The start rectangle
     * @param endRect The end rectangle
     */
    void setRectangles(Rect startRect, Rect endRect) {
        mStartRect = startRect;
        mEndRect = endRect;
    }

    /**
     * @return The start rectangle
     */
    public Rect getStartRect() {
        return mStartRect;
    }

    /**
     * @return The end rectangle
     */
    public Rect getEndRect() {
        return mEndRect;
    }

    /**
     * Get the effect type
     *
     * @param effect The effect
     *
     * @return The effect type
     */
    private static int toType(Effect effect) {
        if (effect instanceof EffectKenBurns) {
            return EffectType.EFFECT_KEN_BURNS;
        } else if (effect instanceof EffectColor) {
            final EffectColor colorEffect = (EffectColor)effect;
            switch (colorEffect.getType()) {
                case EffectColor.TYPE_GRADIENT: {
                    return EffectType.EFFECT_COLOR_GRADIENT;
                }

                case EffectColor.TYPE_SEPIA: {
                    return EffectType.EFFECT_COLOR_SEPIA;
                }

                case EffectColor.TYPE_NEGATIVE: {
                    return EffectType.EFFECT_COLOR_NEGATIVE;
                }

                case EffectColor.TYPE_COLOR:
                default: {
                    throw new IllegalArgumentException("Unsupported color type effect: " +
                            colorEffect.getType());
                }
            }
        } else {
            throw new IllegalArgumentException("Unsupported effect: " + effect.getClass());
        }
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof MovieEffect)) {
            return false;
        }
        return mUniqueId.equals(((MovieEffect)object).mUniqueId);
    }

    @Override
    public int hashCode() {
        return mUniqueId.hashCode();
    }
}
