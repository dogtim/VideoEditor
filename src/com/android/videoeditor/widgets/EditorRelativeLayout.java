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

import com.android.videoeditor.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

/**
 * The RelativeLayout which is the container for the editor layout.
 * The only reason we customize this layout is to listen to click events
 * which will be used to unselect timeline views
 * (media item views, transition views, ...).
 */
public class EditorRelativeLayout extends RelativeLayout implements View.OnClickListener {
    /*
     * {@inheritDoc}
     */
    public EditorRelativeLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setOnClickListener(this);
    }

    public EditorRelativeLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EditorRelativeLayout(Context context) {
        this(context, null, 0);
    }

    @Override
    public void onClick(View view) {
        findViewById(R.id.timeline).setSelected(false);
    }
}
