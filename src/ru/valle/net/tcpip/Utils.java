/**
 The MIT License (MIT)

 Copyright (c) 2012-2014 Valentin Konovalov

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

package ru.valle.net.tcpip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author vkonovalov
 */
public final class Utils {

    public static byte[] readAll(InputStream is) throws IOException {
        if (is != null) {
            byte[] buf = new byte[5000];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                while (true) {
                    int bytesReadCurr = is.read(buf);
                    if (bytesReadCurr == -1) {
                        baos.close();
                        byte[] data = baos.toByteArray();
                        baos = null;
//                        Log.w("util", "stream readed OK "+data.length);
                        return data;
                    } else if (bytesReadCurr > 0) {
//                        Log.w("util", "stream readed "+bytesReadCurr);
                        baos.write(buf, 0, bytesReadCurr);
                    }
                }
            } finally {
//                Log.w("util", "stream readed finally...");
                if (baos != null) {
                    baos.close();
                }
            }
        } else {
            return null;
        }
    }

    private static final String hexChars = "0123456789ABCDEF";

    public static String toHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte aByte : bytes) {
            int b = aByte & 0xFF;
            sb.append(hexChars.charAt(b >> 4));
            sb.append(hexChars.charAt(b & 15));
        }
        return sb.toString();
    }

}
