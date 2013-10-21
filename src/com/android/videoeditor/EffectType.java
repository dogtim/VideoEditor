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

/**
 * Represents an effect that consists of name and type.
 */
public class EffectType {
    // Effect categories
    public static final int CATEGORY_IMAGE = 0;
    public static final int CATEGORY_VIDEO = 1;

    // Effect types
    public static final int EFFECT_KEN_BURNS = 0;
    public static final int EFFECT_COLOR_GRADIENT = 1;
    public static final int EFFECT_COLOR_SEPIA = 2;
    public static final int EFFECT_COLOR_NEGATIVE = 3;

    /**
     * Gets effects for the specified category, either image or video.
     *
     * @param context The context
     * @param category The category
     *
     * @return The array of effects of the specified category
     */
    public static EffectType[] getEffects(Context context, int category) {
        final EffectType[] effects;

        switch (category) {
            case CATEGORY_IMAGE: {
                effects = new EffectType[4];
                effects[0] = new EffectType(
                        context.getString(R.string.effect_pan_zoom), EFFECT_KEN_BURNS);
                effects[1] = new EffectType(
                        context.getString(R.string.effect_gradient), EFFECT_COLOR_GRADIENT);
                effects[2] = new EffectType(
                        context.getString(R.string.effect_sepia), EFFECT_COLOR_SEPIA);
                effects[3] = new EffectType(
                        context.getString(R.string.effect_negative), EFFECT_COLOR_NEGATIVE);
                break;
            }

            case CATEGORY_VIDEO: {
                effects = new EffectType[3];
                effects[0] = new EffectType(
                        context.getString(R.string.effect_gradient), EFFECT_COLOR_GRADIENT);
                effects[1] = new EffectType(
                        context.getString(R.string.effect_sepia), EFFECT_COLOR_SEPIA);
                effects[2] = new EffectType(
                        context.getString(R.string.effect_negative), EFFECT_COLOR_NEGATIVE);
                break;
            }

            default: {
                effects = new EffectType[0];
                break;
            }
        }

        return effects;
    }

    // Instance variables
    private final String mName;
    private final int mType;

    public EffectType(String name, int type) {
        mName = name;
        mType = type;
    }

    public String getName() {
        return mName;
    }

    public int getType() {
        return mType;
    }
}
