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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;

/**
 * Adapter which displays a list of supported transitions
 */
public class TransitionsAdapter extends BaseAdapterWithImages<Integer> {
    // Instance variables
    private final TransitionType[] mTransitions;

    /**
     * Constructor
     *
     * @param context The context
     * @param listView The list view
     */
    public TransitionsAdapter(Context context, AbsListView listView) {
        super(context, listView);

        mTransitions = TransitionType.getTransitions(context);
    }

    /**
     * @return The array of transitions
     */
    public TransitionType[] getTransitions() {
        return mTransitions;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public int getCount() {
        return mTransitions.length;
    }

    /*
     * {@inheritDoc}
     */
    public Object getItem(int position) {
        return mTransitions[position];
    }

    /*
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    public View getView(int position, View convertView, ViewGroup parent) {
        final ImageTextViewHolder<Integer> viewHolder;
        final View rowView;
        if (convertView == null) {
            final LayoutInflater vi =
                (LayoutInflater)mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            rowView = vi.inflate(R.layout.image_with_text_row_view, null);
            viewHolder = new ImageTextViewHolder<Integer>(rowView);
            rowView.setTag(viewHolder);
        } else {
            rowView = convertView;
            viewHolder = (ImageTextViewHolder<Integer>)convertView.getTag();
        }

        final TransitionType transitionType = mTransitions[position];
        final int type = transitionType.getType();
        initiateLoad(type, type, viewHolder);

        // Set the data in the views
        viewHolder.mNameView.setText(transitionType.getName());

        return rowView;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected Bitmap loadImage(Object data) {
        return BitmapFactory.decodeResource(mContext.getResources(),
                TransitionType.TRANSITION_RESOURCE_IDS[(Integer)data]);
    }
}
