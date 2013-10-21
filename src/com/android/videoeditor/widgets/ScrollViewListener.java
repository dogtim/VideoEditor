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

import android.view.View;

/**
 * A listener for scroll events
 */
public interface ScrollViewListener {
    /**
     * Scroll begin
     *
     * @param view The view
     * @param scrollX The current horizontal position
     * @param scrollY The current vertical position
     * @param appScroll true if scroll was initiate in code
     *      (as opposed to by the user)
     */
    public void onScrollBegin(View view, int scrollX, int scrollY, boolean appScroll);

    /**
     * Scroll in progress
     *
     * @param view The view
     * @param scrollX The current horizontal position
     * @param scrollY The current vertical position
     * @param appScroll true if scroll was initiate in code
     *      (as opposed to by the user)
     */
    public void onScrollProgress(View view, int scrollX, int scrollY, boolean appScroll);

    /**
     * Scroll end
     *
     * @param view The view
     * @param scrollX The current horizontal position
     * @param scrollY The current vertical position
     * @param appScroll true if scroll was initiate in code
     *      (as opposed to by the user)
     */
    public void onScrollEnd(View view, int scrollX, int scrollY, boolean appScroll);
}
