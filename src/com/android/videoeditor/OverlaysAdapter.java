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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;

import com.android.videoeditor.util.ImageUtils;

/**
 * Adapter which displays a list of supported overlays
 */
public class OverlaysAdapter extends BaseAdapterWithImages<Integer> {
    // Static member variables
    private static final Paint sCopyPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    // Instance variables
    private final Bitmap mGenericBitmap;
    private final String mTitle, mSubtitle;
    private final OverlayType[] mOverlays;

    /**
     * Constructor
     *
     * @param context The context
     * @param listView The list view
     */
    public OverlaysAdapter(Context context, AbsListView listView) {
        super(context, listView);

        mGenericBitmap = BitmapFactory.decodeResource(context.getResources(),
                R.drawable.effects_generic);
        mTitle = context.getString(R.string.overlay_title_sample);
        mSubtitle = context.getString(R.string.overlay_subtitle_sample);
        mOverlays = OverlayType.getOverlays(context);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public int getCount() {
        return mOverlays.length;
    }

    /*
     * {@inheritDoc}
     */
    public Object getItem(int position) {
        return mOverlays[position];
    }

    /*
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public View getView(int position, View convertView, ViewGroup parent) {
        final ImageTextViewHolder<Integer> viewHolder;
        final View rowView;
        if (convertView == null) {
            final LayoutInflater vi = (LayoutInflater)mContext.getSystemService(
                    Context.LAYOUT_INFLATER_SERVICE);
            rowView = vi.inflate(R.layout.image_with_text_row_view, null);
            viewHolder = new ImageTextViewHolder<Integer>(rowView);
            rowView.setTag(viewHolder);
        } else {
            rowView = convertView;
            viewHolder = (ImageTextViewHolder<Integer>)convertView.getTag();
        }

        final OverlayType overlayType = mOverlays[position];
        initiateLoad(overlayType.getType(), overlayType.getType(), viewHolder);

        // Set the data in the views
        viewHolder.mNameView.setText(overlayType.getName());

        return rowView;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected Bitmap loadImage(Object data) {
        final Bitmap overlayBitmap = Bitmap.createBitmap(mGenericBitmap.getWidth(),
                mGenericBitmap.getHeight(), Bitmap.Config.ARGB_8888);

        final Canvas canvas = new Canvas(overlayBitmap);
        canvas.drawBitmap(mGenericBitmap, 0, 0, sCopyPaint);
        canvas.drawBitmap(ImageUtils.buildOverlayBitmap(mContext, null, (Integer)data, mTitle,
                mSubtitle,
                mGenericBitmap.getWidth(), mGenericBitmap.getHeight()), 0, 0, sCopyPaint);
        return overlayBitmap;
    }
}
