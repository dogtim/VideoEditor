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
 * A movie transition type
 */
public class TransitionType {
    // Transition types
    public static final int TRANSITION_TYPE_ALPHA_CONTOUR = 0;
    public static final int TRANSITION_TYPE_ALPHA_DIAGONAL= 1;
    public static final int TRANSITION_TYPE_CROSSFADE = 2;
    public static final int TRANSITION_TYPE_FADE_BLACK = 3;
    public static final int TRANSITION_TYPE_SLIDING_RIGHT_OUT_LEFT_IN = 4;
    public static final int TRANSITION_TYPE_SLIDING_LEFT_OUT_RIGHT_IN = 5;
    public static final int TRANSITION_TYPE_SLIDING_TOP_OUT_BOTTOM_IN = 6;
    public static final int TRANSITION_TYPE_SLIDING_BOTTOM_OUT_TOP_IN = 7;

    // Transition preview resources
    public final static int TRANSITION_RESOURCE_IDS[] = {
        R.drawable.transition_alpha_contour,
        R.drawable.transition_alpha_diagonal,
        R.drawable.transition_crossfade,
        R.drawable.transition_fade_black,
        R.drawable.transition_sliding_right_out_left_in,
        R.drawable.transition_sliding_left_out_right_in,
        R.drawable.transition_sliding_top_out_bottom_in,
        R.drawable.transition_sliding_bottom_out_top_in
    };

    /**
     * Get transitions for the specified category
     *
     * @param context The context
     *
     * @return The array of transitions of the specified category
     */
    public static TransitionType[] getTransitions(Context context) {
        final TransitionType[] transitions = new TransitionType[8];
        transitions[0] = new TransitionType(
                context.getString(R.string.transitions_alpha_countour),
                TRANSITION_TYPE_ALPHA_CONTOUR);
        transitions[1] = new TransitionType(
                context.getString(R.string.transitions_alpha_diagonal),
                TRANSITION_TYPE_ALPHA_DIAGONAL);
        transitions[2] = new TransitionType(
                context.getString(R.string.transitions_crossfade),
                TRANSITION_TYPE_CROSSFADE);
        transitions[3] = new TransitionType(
                context.getString(R.string.transitions_fade_black),
                TRANSITION_TYPE_FADE_BLACK);
        transitions[4] = new TransitionType(
                context.getString(R.string.transitions_sliding_right_out_left_in),
                TRANSITION_TYPE_SLIDING_RIGHT_OUT_LEFT_IN);
        transitions[5] = new TransitionType(
                context.getString(R.string.transitions_sliding_left_out_right_in),
                TRANSITION_TYPE_SLIDING_LEFT_OUT_RIGHT_IN);
        transitions[6] = new TransitionType(
                context.getString(R.string.transitions_sliding_top_out_bottom_in),
                TRANSITION_TYPE_SLIDING_TOP_OUT_BOTTOM_IN);
        transitions[7] = new TransitionType(
                context.getString(R.string.transitions_sliding_bottom_out_top_in),
                TRANSITION_TYPE_SLIDING_BOTTOM_OUT_TOP_IN);

        return transitions;
    }

    // Instance variables
    private final String mName;
    private final int mType;

    /**
     * Constructor
     */
    public TransitionType(String name, int type) {
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
