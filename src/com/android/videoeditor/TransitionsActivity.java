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

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Activity that lists all transition effects for user to choose.
 */
public class TransitionsActivity extends ListActivity {
    // Input transition category
    public static final String PARAM_AFTER_MEDIA_ITEM_ID = "media_item_id";
    public static final String PARAM_TRANSITION_ID = "transition_id";
    public static final String PARAM_MINIMUM_DURATION = "min_duration";
    public static final String PARAM_DEFAULT_DURATION = "default_duration";
    public static final String PARAM_MAXIMUM_DURATION = "max_duration";

    // Increment transition duration in milliseconds
    private static final long INCREMENT_TRANSITION = 100;

    // Output transition type
    public static final String PARAM_TRANSITION_TYPE = "transition";
    public static final String PARAM_TRANSITION_DURATION = "duration";

    // State keys
    private static final String STATE_KEY_TRANSITION_DURATION = "duration";

    // Instance variables
    private TextView mTransitionDurationView;
    private View mTransitionLeftBtn, mTransitionRightBtn;
    private TransitionsAdapter mAdapter;
    private long mMinTransitionDurationMs, mMaxTransitionDurationMs, mTransitionDurationMs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.transition_list_view);
        setFinishOnTouchOutside(true);

        mTransitionDurationView = (TextView)findViewById(R.id.transition_duration);
        mTransitionLeftBtn = findViewById(R.id.duration_left);
        mTransitionRightBtn = findViewById(R.id.duration_right);

        mMinTransitionDurationMs = getIntent().getLongExtra(PARAM_MINIMUM_DURATION, 0);
        mMinTransitionDurationMs = (mMinTransitionDurationMs / INCREMENT_TRANSITION) *
            INCREMENT_TRANSITION;

        mMaxTransitionDurationMs = getIntent().getLongExtra(PARAM_MAXIMUM_DURATION, 0);
        mMaxTransitionDurationMs = (mMaxTransitionDurationMs / INCREMENT_TRANSITION) *
            INCREMENT_TRANSITION;

        if (savedInstanceState == null) {
            mTransitionDurationMs = getIntent().getLongExtra(PARAM_DEFAULT_DURATION, 0);
        } else {
            mTransitionDurationMs = savedInstanceState.getLong(STATE_KEY_TRANSITION_DURATION);
        }
        mTransitionDurationMs = (mTransitionDurationMs / INCREMENT_TRANSITION) *
            INCREMENT_TRANSITION;

        updateTransitionDuration();

        // Create the list adapter
        mAdapter = new TransitionsAdapter(this, getListView());
        setListAdapter(mAdapter);

        final int transitionType = getIntent().getIntExtra(PARAM_TRANSITION_TYPE, -1);
        if (transitionType >= 0) {
            // Select the current transition
            final TransitionType[] transitions = mAdapter.getTransitions();
            for (int i = 0; i < transitions.length; i++) {
                if (transitions[i].getType() == transitionType) {
                    setSelection(i);
                    break;
                }
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mAdapter != null) {
            mAdapter.onPause();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mAdapter != null) {
            mAdapter.onDestroy();
            mAdapter = null;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putLong(STATE_KEY_TRANSITION_DURATION, mTransitionDurationMs);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        final Intent extras = new Intent();
        extras.putExtra(PARAM_TRANSITION_TYPE,
                ((TransitionType)mAdapter.getItem(position)).getType());
        extras.putExtra(PARAM_AFTER_MEDIA_ITEM_ID,
                getIntent().getStringExtra(PARAM_AFTER_MEDIA_ITEM_ID));
        extras.putExtra(PARAM_TRANSITION_ID,
                getIntent().getStringExtra(PARAM_TRANSITION_ID));
        extras.putExtra(PARAM_TRANSITION_DURATION, mTransitionDurationMs);

        setResult(RESULT_OK, extras);
        finish();
    }

    public void onClickHandler(View target) {
        switch (target.getId()) {
            case R.id.duration_left: {
                if (mTransitionDurationMs > mMinTransitionDurationMs) {
                    mTransitionDurationMs -= INCREMENT_TRANSITION;
                    updateTransitionDuration();
                }
                break;
            }

            case R.id.duration_right: {
                if (mTransitionDurationMs < mMaxTransitionDurationMs) {
                    mTransitionDurationMs += INCREMENT_TRANSITION;
                    updateTransitionDuration();
                }
                break;
            }

            default: {
                break;
            }
        }
    }

    @Override
    public boolean onSearchRequested() {
        return false;
    }

    /**
     * Updates the transition duration and the state of the buttons.
     */
    private void updateTransitionDuration() {
        mTransitionDurationView.setText(getString(R.string.transitions_duration,
                (((float)mTransitionDurationMs) / 1000)));

        mTransitionLeftBtn.setEnabled(mTransitionDurationMs > mMinTransitionDurationMs);
        mTransitionRightBtn.setEnabled(mTransitionDurationMs < mMaxTransitionDurationMs);
    }
}
