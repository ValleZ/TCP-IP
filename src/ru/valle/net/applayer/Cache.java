/**
 The MIT License (MIT)

 Copyright (c) 2013 Valentin Konovalov

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 THE SOFTWARE.*/
package ru.valle.net.applayer;

import android.util.BuildConfig;
import android.util.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author vkonova
 */
public final class Cache {

    private static final Cache instance = new Cache();

    static Cache getInstance() {
        return instance;
    }

    private static final String TAG = "Cache";
    private final Map<String, byte[]> memCache = new HashMap<String, byte[]>();
    private final Map<String, Long> memCacheExpirationTimes = new HashMap<String, Long>();

    private Cache() {
    }


    public byte[] get(String urlStr, String httpMethod) throws IOException {
        byte[] result = null;
        if (urlStr != null && httpMethod != null) {
            String key = buildKey(urlStr, httpMethod);
            synchronized (memCache) {
                result = memCache.get(key);
                if (result != null && System.currentTimeMillis() > memCacheExpirationTimes.get(key)) {
                    memCacheExpirationTimes.remove(key);
                    memCache.remove(key);
                    result = null;
                }
            }
        }
        if (result != null) {
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "CACHE HIT");
            }
        } else {
            if (BuildConfig.DEBUG) {
                Log.i(TAG, "CACHE MISS " + urlStr);
            }
        }
        return result;
    }

    public void put(final String key, final byte[] bytes) {
        if (bytes != null && key != null && bytes.length > 0) {
            synchronized (memCache) {
                memCache.put(key, bytes);
                memCacheExpirationTimes.put(key, 60000 + System.currentTimeMillis());
                memCache.notifyAll();
            }
        }
    }

    public static String buildKey(String urlStr, String httpMethod) {
        return httpMethod + urlStr;
    }

}
