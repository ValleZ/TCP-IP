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
import android.util.Log;
import ru.valle.net.applayer.TCPLinkFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.SynchronousQueue;

/**
 *
 * @author vkonova
 */
public final class IPLink implements Runnable {

    final int localAddress;
//    volatile int remoteAddress;
    private final SLIP slip;
    private volatile boolean readerAlive = true;
    private final SynchronousQueue<byte[]> queue = new SynchronousQueue<byte[]>(false);
    private static final String TAG = "IPLink";
//    private TCPLinkFactory tcpLinkFactory;
    private final HashMap<Long, TCPLink> links = new HashMap<Long, TCPLink>();

    public IPLink(int localAddress, SLIP slip) {
        this.localAddress = localAddress;
        this.slip = slip;
    }

    public void receivePackets() throws IOException {
//        long start = 0;
//        int received = 0;
        while (!Thread.interrupted()) {
//                                    long end = System.nanoTime();
//                        System.out.println("recp2 " + received + " bytes in " + (end - start)/1000 + "mks ");

            byte[] packet = slip.recvPacket();

            if (packet != null && packet.length > 0) {
//            received = packet.length;
//                    start = System.nanoTime();

                try {
                    int ipVer = IP.readIPVersion(packet);
                    if (ipVer == 4 && IP.readDestAddress(packet) == localAddress) {//todo check remote addr
                        IP ip = new IP(packet);
                        if (ip.protocol == 6 && ip.verify()) {
                            long remoteAddress = ip.sourceAddress;
                            int remotePort = ip.tcp.sourcePort;
                            int localPort = ip.tcp.destPort;
                            Long key = (remoteAddress << 32) | ((localPort & 0xFFFF) << 16) | (remotePort & 0xFFFF);
                            TCPLink link;
                            synchronized (links) {
                                link = links.get(key);
                                if (link != null) {
//                                    System.out.println("process incoming packet from port " + ip.tcp.sourcePort + " by " + link.hashCode());
                                } else {
                                    if ((ip.tcp.flags & TCP.SYN) == TCP.SYN) {

                                        link = TCPLinkFactory.create(ip.sourceAddress,
                                                remotePort, localPort, key, this);
                                        links.put(key, link);
//                                        System.out.println("process SYN packet " + packet + " by " + link.hashCode());

                                    } else if (BuildConfig.DEBUG) {
                                        Log.e(TAG, "BAD IP PACKET (no such connection exist) " + key + " " + desc(ip) + " FROM " + slip.getISDesc() + " " + Utils.toHex(packet));
                                    }
                                }
                            }
                            if (link != null) {
                                link.processPacket(ip);
                            }
                        } else if (BuildConfig.DEBUG) {
                            Log.w(TAG, "BAD IP PACKET " + desc(ip) + " FROM " + slip.getISDesc() + " " + Utils.toHex(packet));
                        }

                    } else if (BuildConfig.DEBUG) {
                        Log.w(TAG, "NOT OUR IP PACKET IPv" + ipVer + " TO "
                                + (ipVer == 4 ? Integer.toHexString(IP.readDestAddress(packet)) : "unknown")
                                + " desc " + (ipVer == 4 ? desc(new IP(packet)) : "unkn") + " hex " + Utils.toHex(packet));
                    }

                } catch (InterruptedException e) {
                    readerAlive = false;
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "interrupted");
                    }
                    return;
                } catch (IOException e) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "bad ip packet " + e);
                    }
                }
            }
        }
        readerAlive = false;
    }

    public void send(byte[] tcpBytes, int remoteAddress) throws IOException {
        byte[] bytes = IP.build(localAddress, remoteAddress, tcpBytes);
        try {
            queue.put(bytes);
        } catch (InterruptedException ex) {
            throw new IOException("interrupted");
        }
    }

    public static String desc(IP ip) {
        if (BuildConfig.DEBUG) {
            if (ip == null) {
                return "nulli";
            }
            if (ip.tcp == null) {
                return "nullt";
            }
            return ip.tcp.seqNumber + "/" + ip.tcp.ackNumber + " len " + ip.totalLength;
        } else {
            return ip.toString();
        }
    }

    public void run() {
        while (true) {
            try {
                byte[] bytesToSend = queue.take();
                slip.sendPacket(bytesToSend);
                if (!readerAlive) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "IPLINK FINISHED");
                    }
                    return;
                }
            } catch (InterruptedException ex) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "IPLINK FINISHED because of interruption");
                }
                break;
            } catch (IOException ex) {
                //ignore bad packet
            }
        }
    }

    public void start() {
        Thread senderThread = new Thread(this);
        senderThread.start();
    }

    public void destroy() {
        synchronized (links) {
            for (Map.Entry<Long, TCPLink> entry : links.entrySet()) {
//                Long key = entry.getKey();
                TCPLink link = entry.getValue();
                link.close();
            }
        }
        Thread.currentThread().interrupt();
    }

    void close(TCPLink link) {
        synchronized (links) {
            link.close();
            links.remove(link.getKey());
        }
    }

    /**
     * send android originated app-level bytes (HTTP request) to
     * bt:/192.168.1.1:80
     *
     * @param httpRequest ex: "GET /path?query HTTP/1.1\r\nHost: 192.168.1.1\r\n\r\n"
     * @throws IOException
     */
    public void sendReq(final String httpRequest) throws IOException {
        long remoteAddress = 0xC0A80101;
        int localPort = 23432;
        int remotePort = 80;
        Long key = (remoteAddress << 32) | ((localPort & 0xFFFFL) << 16) | (remotePort & 0xFFFF);
        final TCPLink link;
        synchronized (links) {
            link = new TCPLink(localPort, remotePort, localAddress,
                    key, this);

            Thread thread = new Thread(new RespReader(link, httpRequest));
            thread.start();
            link.setAppLayerThread(thread);

            links.put(key, link);
        }
    }

    /**
     * application level reader (like sockets, HTTP) to read response of
     * android-originated request
     */
    private static class RespReader implements Runnable {

        private final TCPLink link;
        private final String httpRequest;

        private RespReader(TCPLink link, String httpRequest) {
            this.link = link;
            this.httpRequest = httpRequest;
        }

        @SuppressWarnings("ConstantConditions")
        public void run() {
            try {
                InputStream in = link.getDownstream();
                OutputStream out = link.getUpstream();
                out.write(httpRequest.getBytes("UTF-8"));
                out.flush();

                byte[] resp = Utils.readAll(in);
                //noinspection PointlessBooleanExpression
                if (resp != null && BuildConfig.DEBUG) {
                    Log.i(TAG, "upcmd response '" + new String(resp, "UTF-8") + "'");
                }
            } catch (IOException ex) {
                Log.e(TAG, "err", ex);
            }
        }
    }
}
