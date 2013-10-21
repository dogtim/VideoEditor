/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.videoeditor.service.MovieOverlay;


public class OverlayTitleTemplatePicker extends ListActivity {
    // Incoming parameter keys.
    public static final String PARAM_MEDIA_ITEM_ID = "media_item_id";
    public static final String PARAM_OVERLAY_ATTRIBUTES = "attributes";
    public static final String PARAM_OVERLAY_ID = "overlay_id";

    private OverlaysAdapter mAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_view);
        setFinishOnTouchOutside(true);

        // Create the list adapter
        mAdapter = new OverlaysAdapter(this, getListView());
        setListAdapter(mAdapter);
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
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        // Put selected overlay type into extras and finish.
        final Intent extras = new Intent();
        final int overlayType = ((OverlayType) mAdapter.getItem(position)).getType();
        final Bundle bundle = MovieOverlay.buildUserAttributes(overlayType, "", "");
        extras.putExtra(PARAM_OVERLAY_ATTRIBUTES, bundle);
        setResult(RESULT_OK, extras);
        finish();
    }

    @Override
    public boolean onSearchRequested() {
        return false;
    }
}
