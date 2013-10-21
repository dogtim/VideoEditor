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
 * A gesture listener which specializes in move events
 */
interface ItemMoveGestureListener extends ItemSimpleGestureListener {
    /**
     * Move begin
     *
     * @param view The view
     * @param e The beginning position
     *
     * @return true if the moving should continue
     */
    public boolean onMoveBegin(View view, MotionEvent e);

    /**
     * Move
     *
     * @param view The view
     * @param e1 The beginning position
     * @param e2 The end position
     *
     * @return true if the moving should continue
     */
    public boolean onMove(View view, MotionEvent e1, MotionEvent e2);

    /**
     * Move end
     *
     * @param view The view
     */
    public void onMoveEnd(View view);
}
