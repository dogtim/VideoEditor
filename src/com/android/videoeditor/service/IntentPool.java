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

package com.android.videoeditor.service;

import java.util.ArrayList;
import java.util.List;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * A pool of Intents
 */
class IntentPool {
    // Logging
    private static final String TAG = "IntentPool";

    // Instance variables
    private final List<Intent> mIntentPool;

    /**
     * Constructor
     *
     * @param initialSize The initial size of the pool
     */
    public IntentPool(int initialSize) {
        mIntentPool = new ArrayList<Intent>(initialSize);
    }

    /**
     * @return The Intent is retrieved from the pool or if the pool is empty
     *      a new Intent is allocated
     */
    public synchronized Intent get(Context context, Class<?> cls) {
        final Intent intent = get();
        intent.setComponent(new ComponentName(context, cls));
        return intent;
    }

    /**
     * @return The Intent is retrieved from the pool or if the pool is empty
     *      a new Intent is allocated
     */
    public synchronized Intent get() {
        if (mIntentPool.size() > 0) {
            final Intent intent = mIntentPool.remove(0);
            // Clear the content of the Intent
            final Bundle extras = intent.getExtras();
            for (String keys : extras.keySet()) {
                intent.removeExtra(keys);
            }
            intent.setComponent(null);
            return intent;
        } else {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Pool enlarged");
            }
            return new Intent();
        }
    }

    /**
     * @param intent Return an Intent to the pool
     */
    public synchronized void put(Intent intent) {
        mIntentPool.add(intent);
    }
}
