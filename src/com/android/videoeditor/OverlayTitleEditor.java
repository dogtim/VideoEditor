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

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.videoeditor.service.MovieOverlay;
import com.android.videoeditor.util.ImageUtils;

/**
 * Activity that lets user add or edit title overlay of a media item.
 */
public class OverlayTitleEditor extends NoSearchActivity {
    // Parameter names
    public static final String PARAM_OVERLAY_ATTRIBUTES = "attributes";
    public static final String PARAM_OVERLAY_ID = "overlay_id";
    public static final String PARAM_MEDIA_ITEM_ID = "media_item_id";

    private static final String LOG_TAG = "OverlayTitleEditor";
    private static final int REQUEST_CODE_PICK_TITLE_TEMPLATE = 1;

    private int mOverlayType;
    private ImageView mOverlayImageView;
    private Button mOverlayChangeTitleTemplateButton;
    private TextView mTitleView, mSubtitleView;
    private Bitmap mOverlayBitmap;
    private int mPreviewWidth, mPreviewHeight;

    private final TextWatcher mTextWatcher = new TextWatcher() {
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            // no-op
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // no-op
        }

        @Override
        public void afterTextChanged(Editable s) {
            // Update preview image as user types in the title or sub-title fields.
            updatePreviewImage();
            invalidateOptionsMenu();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.overlay_title_editor);
        setFinishOnTouchOutside(true);

        mOverlayImageView = (ImageView) findViewById(R.id.overlay_preview);

        mOverlayChangeTitleTemplateButton = (Button) findViewById(
                R.id.overlay_change_title_template);
        mOverlayChangeTitleTemplateButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    launchOverlayTitleTemplatePicker();
                }
        });

        mTitleView = (TextView) findViewById(R.id.overlay_title);
        mTitleView.addTextChangedListener(mTextWatcher);

        mSubtitleView = (TextView) findViewById(R.id.overlay_subtitle);
        mSubtitleView.addTextChangedListener(mTextWatcher);

        // Determine bitmap dimensions.
        final BitmapFactory.Options dbo = new BitmapFactory.Options();
        dbo.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(getResources(), R.drawable.effects_generic, dbo);
        mPreviewWidth = dbo.outWidth;
        mPreviewHeight = dbo.outHeight;

        final Bundle attributes = getIntent().getBundleExtra(PARAM_OVERLAY_ATTRIBUTES);
        if (attributes != null) {
            // The media item already has a title overlay. Fill in the contents in the input fields
            // and let user edit them.
            mOverlayType = MovieOverlay.getType(attributes);
            mTitleView.setText(MovieOverlay.getTitle(attributes));
            mSubtitleView.setText(MovieOverlay.getSubtitle(attributes));
        } else {
            // Default overlay type that puts title at the bottom of the media item.
            mOverlayType = MovieOverlay.OVERLAY_TYPE_BOTTOM_1;
        }
        updatePreviewImage();
    }

    private void launchOverlayTitleTemplatePicker() {
        final Intent intent = new Intent(this, OverlayTitleTemplatePicker.class);
        startActivityForResult(intent, REQUEST_CODE_PICK_TITLE_TEMPLATE);
    }

    private void updatePreviewImage() {
        mOverlayBitmap = ImageUtils.buildOverlayBitmap(this, mOverlayBitmap, mOverlayType,
                mTitleView.getText().toString(), mSubtitleView.getText().toString(),
                mPreviewWidth, mPreviewHeight);
        mOverlayImageView.setImageBitmap(mOverlayBitmap);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent extras) {
        if (resultCode != RESULT_OK)
            return;

        switch (requestCode) {
            case REQUEST_CODE_PICK_TITLE_TEMPLATE:
                // Get chosen overlay type from extras and then update preview image.
                final Bundle attributes = extras.getBundleExtra(
                        OverlayTitleTemplatePicker.PARAM_OVERLAY_ATTRIBUTES);
                mOverlayType = MovieOverlay.getType(attributes);
                updatePreviewImage();
                break;
            default:
                Log.w(LOG_TAG, "Invalid request code received: " + requestCode);
                break;
        }
    }

    /**
     * Handler used to responds to "OK" and "Cancel" buttons.
     * @param target "OK" or "Cancel" button
     */
    public void onClickHandler(View target) {
        switch (target.getId()) {
            case R.id.overlay_ok: {
                // Extras to be returned to the caller of this activity.
                final Intent extras = new Intent();
                extras.putExtra(PARAM_MEDIA_ITEM_ID,
                        getIntent().getStringExtra(PARAM_MEDIA_ITEM_ID));

                String overlayId = getIntent().getStringExtra(PARAM_OVERLAY_ID);
                if (overlayId != null) {
                    extras.putExtra(PARAM_OVERLAY_ID, overlayId);
                }

                final TextView titleView = (TextView) findViewById(R.id.overlay_title);
                final TextView subTitleView = (TextView) findViewById(R.id.overlay_subtitle);
                final Bundle attributes = MovieOverlay.buildUserAttributes(mOverlayType,
                        titleView.getText().toString(), subTitleView.getText().toString());
                extras.putExtra(PARAM_OVERLAY_ATTRIBUTES, attributes);

                setResult(RESULT_OK, extras);
                finish();
                break;
            }

            case R.id.overlay_cancel: {
                finish();
                break;
            }
        }
    }
}
