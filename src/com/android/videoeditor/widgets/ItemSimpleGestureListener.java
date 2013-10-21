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

package com.android.videoeditor.widgets;

import android.view.MotionEvent;
import android.view.View;

/**
 * A simple gesture listener which listens to taps and long presses
 */
interface ItemSimpleGestureListener {
    // Area tapped
    public static final int LEFT_AREA = 0;
    public static final int CENTER_AREA = 1;
    public static final int RIGHT_AREA = 2;

    /**
     * This method will only be called after the detector is confident
     * that the user's first tap is not followed by a second tap leading
     * to a double-tap gesture.
     *
     * @param view The view which received the event
     * @param area The area tapped
     * @param e The down motion event of the single-tap.
     *
     * @return true if the event is consumed, else false
     */
    public boolean onSingleTapConfirmed(View view, int area, MotionEvent e);

    /**
     * Notified when a long press occurs with the initial on down
     * MotionEvent that triggered it.
     *
     * @param view The view which received the event
     * @param e The initial on down motion event that started the
     *      long press.
     */
    public void onLongPress(View view, MotionEvent e);
}
