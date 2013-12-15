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

import android.util.BuildConfig;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author vkonova
 */
public final class SLIP {

    private final InputStream is;
    private final OutputStream os;
    private Sniffer mSniffer;
//    private static final Random random = new Random();

    public SLIP(InputStream is, OutputStream os) {
//          this.is = is;
//        this.os=os;
        this.is = new BufferedInputStream(is, 16384 / 2);
        this.os = new BufferedOutputStream(os, 16384);
    }

    private static final int END = 192;/*
     * indicates end of packet
     */

    private static final int ESC = 219;/*
     * indicates byte stuffing
     */

    private static final int ESC_END = 220;/*
     * ESC ESC_END means END data byte
     */

    private static final int ESC_ESC = 221;/*
     * ESC ESC_ESC means ESC data byte
     */


    byte[] recvPacket() throws IOException {
        int len = 150;
        ByteArrayOutputStream p = new ByteArrayOutputStream(len);

        int c;
        int received = 0;
        while (true) {
            c = is.read();
            switch (c) {
                case -1:
                    return received > 0 ? null : p.toByteArray();
                case END:
                    if (received > 0) {
                        byte[] packet = p.toByteArray();
                        //noinspection PointlessBooleanExpression,ConstantConditions
                        if (BuildConfig.DEBUG && mSniffer != null) {
                            mSniffer.onPacketReceived(packet);
                        }
                        return packet;
                    } else {
                        break;
                    }
                case ESC:
                    c = is.read();
                    switch (c) {
                        case -1:
                            return null;
                        case ESC_END:
                            c = END;
                            break;
                        case ESC_ESC:
                            c = ESC;
                            break;
                        default:
                            break;
                    }
                default:
                    p.write(c);
                    received++;
            }
        }
//            finally {

//                if (System.nanoTime() - start > 10000000)
//                {
//                    System.out.println(System.currentTimeMillis()+" received packet in " + (System.nanoTime() - start) / 1000 + " mks from " + is);
//                }
//            }
//        }
    }

    void sendPacket(final byte[] p) throws IOException {
//            System.out.println("send IP packet to " + os);
        os.write(END);
        for (byte b : p) {
            int ch = b & 0xff;
            switch (ch) {
                case END:
                    os.write(ESC);
                    os.write(ESC_END);
                    break;
                case ESC:
                    os.write(ESC);
                    os.write(ESC_ESC);
                    break;
                default:
                    os.write(ch);
            }
        }
        os.write(END);
        os.flush();

        //noinspection PointlessBooleanExpression,ConstantConditions
        if (BuildConfig.DEBUG && mSniffer != null) {
            mSniffer.onPacketSent(p);
        }
//                        long end = System.nanoTime();
//                        System.out.println("recp2 " + len + " bytes in " + (end - start) + "ns " + len * 1000000000L / (end - start) + " bytes/s");
    }

    String getISDesc() {
        return is.toString();
    }

    public void setSniffer(Sniffer sniffer) {
        this.mSniffer = sniffer;
    }
}
