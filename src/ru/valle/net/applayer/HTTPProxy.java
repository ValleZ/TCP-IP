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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * @author vkonova
 */
public class HTTPProxy implements Runnable {

    private final BufferedReader in;
    private final OutputStream os;
    private static final String TAG = "HTTPProxy";
    private final Cache cache;

    HTTPProxy(Cache cache, InputStream downstream, OutputStream upstream) throws UnsupportedEncodingException {
        this.cache = cache;
        in = new BufferedReader(new InputStreamReader(downstream, "UTF-8"), 2000);
        os = upstream;
    }

    public void run() {
        String urlStr = null;
        try {
            String httpMethod = null;
            Map<String, String> headers = new HashMap<String, String>();
            while (true) {
                String line = in.readLine();
                if (line == null) {
                    return;
                }
                if (urlStr == null) {
                    String[] req = line.split("\\s+");
                    if (req.length >= 2) {
                        httpMethod = req[0];
                        if (httpMethod == null) {
                            httpMethod = "GET";
                        }
                        urlStr = req[1];
                        if (urlStr == null) {
                            urlStr = "";
                        }
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "raw request " + urlStr);
                        }

                    } else {
                        if (BuildConfig.DEBUG) {
                            Log.w(TAG, "req.length is " + req.length);
                        }
                    }
                } else {
                    if (line.length() == 0) { //received request and headers
                        try {
                            processHttpRequest(httpMethod, urlStr, headers, os, cache);
                            return;
                        } catch (Exception e) {
                            Log.w(TAG, "nerr3", e);
                        }
//                                Log.d(TAG, "req url FIN '" + urlStr + "'");
                    } else {//process header line
                        int pos = line.indexOf(':');
                        if (pos > 0) {
                            String key = line.substring(0, pos);
                            if (allowedHeaders.contains(key)) {//outgoing headers (from extdevice to web)
                                String val = line.substring(pos + 1).trim();
                                headers.put(key, val);
                                if (BuildConfig.DEBUG) {
                                    Log.i(TAG, "pass header " + key + ":" + val + " to " + urlStr);
                                }
                            } else if (BuildConfig.DEBUG) {
                                Log.w(TAG, "ignore header " + key + " to " + urlStr);
                            }
                        }
                    }
                }
            }

        } catch (IOException ex) {
            Log.e(TAG, "proxy err", ex);
        }
    }

    private static final HashSet<String> allowedHeaders = new HashSet<String>();

    static {
        allowedHeaders.add("Content-Type");
        allowedHeaders.add("Content-Disposition");
        allowedHeaders.add("Date");
        allowedHeaders.add("Server");
//        allowedHeaders.add("Vary");
        allowedHeaders.add("Expires");
        allowedHeaders.add("Pragma");
//        allowedHeaders.add("Transfer-Encoding");
        allowedHeaders.add("Set-Cookie");
        allowedHeaders.add("Content-Language");
        allowedHeaders.add("Cache-Control");
        allowedHeaders.add("Access-Control-Allow-Origin");
        allowedHeaders.add("Last-Modified");
    }

    public static void writeHeadersFromConnection(StringBuilder sb, HttpURLConnection con) {
        Map<String, List<String>> headers = con.getHeaderFields();
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            String key = header.getKey();
            if (allowedHeaders.contains(key)) {//incoming headers (from web to exdev)
                List<String> values = header.getValue();
                sb.append(key).append(":");
                for (int j = 0; j < Math.max(values.size(), 1); j++) {
                    String value = values.get(j);
                    if (j > 0) {
                        sb.append(",");
                    }
                    sb.append(value);
                }
                sb.append("\r\n");
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "pass header " + key + ":" + values);
                }
            } else if (BuildConfig.DEBUG) {
                Log.w(TAG, "ignore header " + key);
            }
        }
    }

    public static void writeStream(InputStream in, OutputStream out, ByteArrayOutputStream copyOs) throws IOException {
        byte[] buf = new byte[5000];
        try {
            while (true) {
                int bytesReadCurr = in.read(buf);
                if (bytesReadCurr == -1) {
                    out.flush();
                    in.close();
                    break;
                } else if (bytesReadCurr > 0) {
                    out.write(buf, 0, bytesReadCurr);
                    if (copyOs != null) {
                        copyOs.write(buf, 0, bytesReadCurr);
                    }
                }
            }
        } finally {
            if (out != null) {
                out.close();
            }
            if (in != null) {
                in.close();
            }
        }
    }

    public void processHttpRequest(String httpMethod, String urlStr, Map<String, String> headers,
                                   OutputStream os, Cache cache) throws IOException {

        String key = Cache.buildKey(urlStr, httpMethod);
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "REQ " + httpMethod + " " + urlStr);
        }
        URL url = new URL(urlStr);
        HttpURLConnection basicConnection = (HttpURLConnection) url.openConnection();
        basicConnection.setRequestMethod(httpMethod);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            basicConnection.setRequestProperty(entry.getKey(), entry.getValue());
        }
        final byte[] bytes = cache == null ? null : cache.get(urlStr, httpMethod);
        int respCode = 200;
        String respMessage = "OK";
        if (bytes == null) {
            respCode = basicConnection.getResponseCode();
            respMessage = basicConnection.getResponseMessage();
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "resp HTTP " + respCode + " " + respMessage + " for " + urlStr);
            }
        }
        StringBuilder headersBuf = new StringBuilder();
        headersBuf.append("HTTP/1.1 ").append(respCode < 100 ? 200 : respCode).append(" ").append(respMessage == null || respMessage.length() == 0 ? "OK" : respMessage).append("\r\n");
        if (bytes == null) {
            HTTPProxy.writeHeadersFromConnection(headersBuf, basicConnection); //write web headers to the buf
        } else {
            headersBuf.append("Content-Length: ").append(bytes.length).append("\r\n");
        }
        if (respCode == -1) {
            headersBuf.append("\r\n");
            String headersStr = headersBuf.toString();
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "stream headers\n" + headersStr);
            }
            byte[] headerBytes = headersStr.getBytes("UTF-8");
            os.write(headerBytes);
            os.flush();
            InputStream is;
            is = basicConnection.getInputStream();
            HTTPProxy.writeStream(is, os, null);
        } else {
            headersBuf.append("Connection: close\r\n");
            headersBuf.append("\r\n");
            byte[] headerBytes = headersBuf.toString().getBytes("UTF-8");
            os.write(headerBytes);
            os.flush();
            if (bytes == null) {
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "CACHE MISS " + urlStr);
                }
                ByteArrayOutputStream copyOs;
                InputStream is = basicConnection.getInputStream();
                copyOs = new ByteArrayOutputStream(1024);
                HTTPProxy.writeStream(is, os, copyOs);
                if (cache != null) {
                    byte[] dataToCache = copyOs.toByteArray();
                    cache.put(key, dataToCache);
                }
            } else {
                if (BuildConfig.DEBUG) {
                    Log.i(TAG, "CACHE HIT " + bytes.length + " " + urlStr);
                }
                try {
                    os.write(bytes);
                    os.flush();
                } finally {
                    os.close();
                }
            }
        }
    }
}
