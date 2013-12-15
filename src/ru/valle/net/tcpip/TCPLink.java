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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * TCP level
 *
 * @author vkonova
 */
public final class TCPLink {

    private boolean started;//synch on this
    private int mss;//synch on this
    private int state = STATE_CLOSED;//synch on this
    private int lastReceivedRecepientBufferSize = 256;//synch intraThreadLock
    private int lastReceivedAck;// synch intraThreadLock
    private int mineSeq;
    private int heSentData;//synch intraThreadLock
    private final int localPort;
    private final IPLink ipLink;
    private final PipedInputStream downstream;
    private final NamedPipedOutputStream upstream;
    private final PipedOutputStream downstreamFeed;
    private final PipedInputStream upstreamFeed;
    private final Object intraThreadLock = new Object();
    private static final int STATE_CLOSED = 0;
    private static final int STATE_LISTEN = 1;
    private static final int STATE_SYN_SENT = 2;
    private static final int STATE_SYN_RECEIVED = 3;
    private static final int STATE_ESTABLISHED = 4;
    private static final int STATE_NO_MORE_DATA_TO_DOWNLOAD = 5;
    private static final int STATE_NO_MORE_DATA_TO_UPLOAD = 6;
    private static final int STATE_E_DESTROYED = 7;
    private final boolean isClient;
    private int remotePort;
    private Thread appLayerThread;
    private final int remoteAddress;
    private final ArrayBlockingQueue<IP> incomingPackets = new ArrayBlockingQueue<IP>(16, true);
    private Thread downloadThread;
    private Thread uploadThread;
    private final Long key;

//    final byte[] readBuf = new byte[65000];
//    volatile int readBufFreeSpace;
    public TCPLink(int localPort, int remotePort, int remoteAddress, Long key, IPLink ipLink) throws IOException {
        this.key = key;
        this.localPort = localPort;
        this.remotePort = remotePort;
        isClient = localPort > 1024;
        this.ipLink = ipLink;
        this.remoteAddress = remoteAddress;

        downstream = new PipedInputStream(downstreamFeed = new PipedOutputStream(), 5100);//2 sent data received from server

        upstream = new NamedPipedOutputStream(upstreamFeed = new PipedInputStream(5100));//2 read data sent to server
//        readBufFreeSpace = 65000;
    }

    public synchronized OutputStream getUpstream() {
        start();
        return upstream;
    }

    public synchronized InputStream getDownstream() {
        start();
        return downstream;
    }

    private int getReadBufSize() {
        int available;
        try {
            available = downstream.available();
//            if(available==64) {
//                Log.d(TAG, "");
//                downstream.available();
//            }
        } catch (IOException ex) {
            available = -1;
        }
        return available >= 0 ? 65000 - available : 65000;
    }

    synchronized int getState() {
        return state;
    }

    private void start() {
        if (!started) {
            started = true;
            uploadThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (isClient) {
                            int seqNumber = 0;
                            int ackNumber = 0;

                            byte[] tcp = TCP.build(localPort, remotePort, seqNumber, ackNumber,
                                    TCP.SYN, getReadBufSize(), null,
                                    ipLink.localAddress, remoteAddress);
//                            Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                    + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U: send SYN " + seqNumber + "/" + ackNumber);
                            synchronized (TCPLink.this) {
                                if (state != STATE_CLOSED) {
                                    throw new RuntimeException();//"not closed on " + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U start");
                                }
                                synchronized (intraThreadLock) {
                                    mineSeq = 1;//sent so far
                                }
                                ipLink.send(tcp, remoteAddress);
                                state = STATE_SYN_SENT;
                                TCPLink.this.notifyAll();
                            }
                        }
                        byte[] sendingBuf;
                        long start;// = System.currentTimeMillis();
                        synchronized (TCPLink.this) {
                            if (!isClient && !(state == STATE_CLOSED || state == STATE_LISTEN || state == STATE_SYN_RECEIVED)) {
                                throw new RuntimeException();//"not closed or listen or synrec on " + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U start, now " + state);
                            }

                            while (state != STATE_ESTABLISHED) {
//                                Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                        + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U: wait  STATE_ESTABLISHED");

                                try {
                                    TCPLink.this.wait();
                                } catch (InterruptedException ex) {
                                    return;
                                }
                                if (state == STATE_E_DESTROYED || state == STATE_NO_MORE_DATA_TO_DOWNLOAD) {
//                                    Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                            + "U: " + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U " + " instead of STATE_ESTABLISHED have got " + state + ", exit U thread");
                                    return;
                                }


                            }
                            sendingBuf = new byte[mss > 0 ? mss : 1200];
                        }
//                        if (System.currentTimeMillis() - start > 1000) {
//                            Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                    + "SPEED ALERT: connection await wait in " + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U " + "took " + (System.currentTimeMillis() - start));
//                        }
//                        long startms;// = System.currentTimeMillis();
                        boolean closed = false;
                        while (true) {


                            int dataReaded = 0;
                            boolean pushRequested = false;
                            int mineSeqOld;

                            final int NAGLES_TIME = 200;

                            long startr = -1;
                            while (true) {
                                int maxLen;
                                synchronized (intraThreadLock) {
                                    maxLen = Math.min(sendingBuf.length, lastReceivedRecepientBufferSize);
                                    mineSeqOld = mineSeq;//sent so far
                                }
//                                Log.d(TAG, "readts maxlen " + maxLen + " dataReaded " + dataReaded + " to read " + (maxLen - dataReaded) + " avail " + upstreamFeed.available() + " " + upstreamFeed);
                                if (maxLen > 0 && dataReaded < maxLen) {
                                    int dataLenToReadNow = dataReaded > 0 ? Math.min(upstreamFeed.available(), maxLen - dataReaded) : maxLen - dataReaded;
                                    int readedRightNow = upstreamFeed.read(sendingBuf, dataReaded, dataLenToReadNow);
                                    if (startr < 0 && readedRightNow >= 0) {
                                        startr = System.nanoTime();
                                    }

                                    if (readedRightNow < 0) {
//                                        Log.d(TAG, "readts CLOSED" + TCPLink.this.hashCode());
                                        closed = true;
                                        break;
                                    }
                                    dataReaded += readedRightNow;
//                                    Log.d(TAG, "readts rsf dataReaded " + dataReaded + " to read " + (maxLen - dataReaded));
                                }
                                pushRequested |= upstream.popPushStatus();
                                int timeSinceStartReading = startr < 0 ? 0 : (int) ((System.nanoTime() - startr) / 1000000);

                                if (closed || (pushRequested && dataReaded > 0) || (maxLen > 0 && dataReaded == maxLen) || (timeSinceStartReading >= NAGLES_TIME && dataReaded > 0)) {
//                                    Log.d(TAG, "readts rsf dataReaded END " + dataReaded
//                                            + " because closed? " + closed
//                                            + ", push & readed some " + (pushRequested && dataReaded > 0)
//                                            + ", maximum amount " + (maxLen > 0 && dataReaded == maxLen)
//                                            + ", nagle timeout " + (timeSinceStartReading >= NAGLES_TIME && dataReaded > 0));
                                    break;
                                }
                                synchronized (intraThreadLock) {
                                    int timeToWait = Math.max(10, (NAGLES_TIME - timeSinceStartReading) / 16);
                                    intraThreadLock.wait(timeToWait);
                                }
                            }

//                            if (startr >= 0 && (System.nanoTime() - startr) / 1000000 > 500) {
//                                Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                        + "SPEED ALERT: indata read in " + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U " + "took " + (System.nanoTime() - startr) / 1000000);
//                            }

                            if (dataReaded > 0) {
//                                receivedBytes += dataReaded;
//                                Log.d(TAG, "!readts " + dataReaded);
                                byte[] sendData = new byte[dataReaded];
                                System.arraycopy(sendingBuf, 0, sendData, 0, dataReaded);
                                //we have got some data to send (like HTTP)


                                while (true) {
                                    byte flags = TCP.ACK;
                                    if (pushRequested) {
                                        pushRequested = false;
//                                        Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U: PSH REQ");
                                        flags |= TCP.PSH;
                                    }
                                    final int heSentDataLoc;
                                    synchronized (intraThreadLock) {
                                        heSentDataLoc = heSentData;
//                                        mineSeq = mineSeqOld + dataReaded;
                                    }
                                    byte[] tcp = TCP.build(localPort, remotePort, mineSeqOld, heSentDataLoc,
                                            flags, getReadBufSize(), sendData,
                                            ipLink.localAddress, remoteAddress);

//                                    Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                            + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U: have " + sendData.length + " bytes to send, SEND ACK " + mineSeqOld + "/" + heSentDataLoc + " len " + sendData.length);
                                    ipLink.send(tcp, remoteAddress);

//                                    startms = System.currentTimeMillis();
                                    synchronized (intraThreadLock) {
//                                        if (Math.abs(mineSeqOld + dataReaded - lastReceivedAck) > dataReaded) {
//                                            Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                    + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U: !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
//                                        }

                                        if (mineSeqOld + dataReaded != lastReceivedAck) {
                                            int time = 7000;
//                                            Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                    + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U: await mineseq " + (mineSeqOld + dataReaded) + " == lastReceivedAck " + lastReceivedAck
//                                                    + " sent len " + dataReaded
//                                                    + " received/sent/confirmedSent " + receivedBytes + "/" + sentBytes + "/" + confirmedSentBytes);
                                            while (true) {
                                                start = System.nanoTime() / 1000000;
                                                intraThreadLock.wait(time);

                                                time -= System.nanoTime() / 1000000 - start;
                                                if (mineSeqOld + dataReaded == lastReceivedAck || time < 100) {
                                                    break;
                                                }
                                            }


//                                            Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                    + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U: wait for sent so far(mineseq) " + (mineSeqOld + dataReaded) + ", confirmed sent so far(lastReceivedAck) " + lastReceivedAck + " state " + state);

                                        }
//                                        if (System.currentTimeMillis() - startms > 1000) {
//                                            Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                    + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U: SPEED ALERT: packet confirmation for mineseq " + (mineSeqOld + dataReaded) + " in " + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U " + "took " + (System.currentTimeMillis() - startms));
//                                        }

//                                    lastReceivedAckLocal = lastReceivedAck;
                                        if (mineSeqOld + dataReaded == lastReceivedAck) {
                                            mineSeq = mineSeqOld + dataReaded;
//                                            lastReceivedRecepientBufferSize /= 2;
                                            lastReceivedRecepientBufferSize -= dataReaded;
                                            break;
                                        } else {
//                                            if (state == STATE_NO_MORE_DATA_TO_DOWNLOAD) {
//                                                Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                        + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U: seems there is a lost packet, he already sent all data( FINWAIT) " + lastReceivedAck + ", now " + mineSeq + ", ack is " + mineSeqOld + " ack is uptodate? " + (heSentDataLoc == heSentData));
//                                            }
//                                            Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                    + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U: seems there is a lost packet, need to retry data sending from pos " + lastReceivedAck + ", now " + mineSeq + ", ack is " + mineSeqOld + " ack is uptodate? " + (heSentDataLoc == heSentData));
                                        }
                                    }

                                }
//                                Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                        + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U: ack ok ");

                            } else if (dataReaded == 0) {
                                if (closed) {//there will be no data from this side
                                    byte flags = TCP.FIN | TCP.ACK;
                                    final int heSentDataLoc;
                                    synchronized (intraThreadLock) {
                                        heSentDataLoc = heSentData;
                                        mineSeqOld = mineSeq;
                                        mineSeq++;
                                    }
                                    byte[] tcp = TCP.build(localPort, remotePort, mineSeqOld, heSentDataLoc,
                                            flags, getReadBufSize(), null,
                                            ipLink.localAddress, remoteAddress);

//                                    Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                            + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U: have FIN to send, SEND FIN " + mineSeqOld + "/" + heSentDataLoc);

                                    ipLink.send(tcp, remoteAddress);

                                    synchronized (intraThreadLock) {
                                        if (mineSeq != lastReceivedAck) {
                                            int time = 3000;
//                                            Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                    + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U: await FIN mineseq " + mineSeq + " == lastReceivedAck " + lastReceivedAck);
                                            while (true) {
                                                start = System.nanoTime() / 1000000;
                                                intraThreadLock.wait(time);
                                                time -= System.nanoTime() / 1000000 - start;
                                                if (mineSeq == lastReceivedAck || time < 100) {
                                                    break;
                                                }
                                            }
//                                            if (time < 2500) {
//                                                Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                        + "SPEED ALERT: waiting for packet in " + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U " + "took " + (3000 - time));
//                                            }
//                                            Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                    + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U: end wait FIN for sent so far(mineseq) " + mineSeq + ", confirmed sent so far(lastReceivedAck) " + lastReceivedAck);

                                        }
//                                        if (mineSeq != lastReceivedAck) {
//                                            Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                    + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U: FIN seems there is a lost packet, close anyway, state " + state);
//                                        }

                                    }
                                    synchronized (TCPLink.this) {
//                                        Log.d(TAG, (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U" + " START waiting for read side end, to keep ESTABLISHED state, curr state is " + state);
                                        if (state == STATE_ESTABLISHED) {
                                            state = STATE_NO_MORE_DATA_TO_UPLOAD;
                                        }
//                                        start = System.currentTimeMillis();
                                        while (!(state == STATE_NO_MORE_DATA_TO_DOWNLOAD || state == STATE_E_DESTROYED)) {

//                                            Log.d(TAG, (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U" + " waits for read side end, to keep ESTABLISHED state, curr state is " + state);
                                            TCPLink.this.wait(20000);



                                        }
//                                        if (System.currentTimeMillis() - start > 1000) {
//                                            Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                    + "SPEED ALERT: fin in " + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U " + "took " + (System.currentTimeMillis() - start));
//                                        }
//                                        Log.d(TAG, (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U" + " correctly closed");
                                    }
                                    return;
                                } else {
//                                    start = System.currentTimeMillis();
                                    synchronized (intraThreadLock) {
//                                        if (lastReceivedRecepientBufferSize < 2000) {
//                                            Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                    + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U: wait, receiver is not ready, recepient buff size is " + lastReceivedRecepientBufferSize + " state " + state);
//
//                                        } else {
//                                            Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                    + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U: wait, no data to send, recepient buff size is " + lastReceivedRecepientBufferSize + " state " + state + " upstreamFeed avail " + upstreamFeed.available());
//
//                                        }

                                        intraThreadLock.wait(150);
                                    }
                                    synchronized (TCPLink.this) {
                                        if (state == STATE_E_DESTROYED) {
                                            return;
                                        }
                                    }
//                                    if (System.currentTimeMillis() - start > 1000) {
//                                        Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                + "SPEED ALERT: uncongestion wait in " + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U " + "took " + (System.currentTimeMillis() - start));
//                                    }

                                }
                            }
                        }
                    } catch (Exception ex) {
                        if (BuildConfig.DEBUG) {
                            Log.d("TCP", "ex " + ex + " in " + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U");
                        }
                    } finally {
                        synchronized (TCPLink.this) {
//                            Log.d(TAG, "thread death " + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "U" + " state " + state + " received/sent/confirmedSent " + receivedBytes + "/" + sentBytes + "/" + confirmedSentBytes);
                            state = STATE_E_DESTROYED;
                            ipLink.close(TCPLink.this);
                        }

                    }

                }
            });
            uploadThread.start();

            downloadThread = new Thread(new Runnable() {
//                private long finWaitStart;
                @Override
                public void run() {
                    try {
                        if (!isClient) {
                            synchronized (TCPLink.this) {
                                if (state != STATE_CLOSED) {
                                    throw new RuntimeException();//"not closed on " + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D start");
                                }

                                state = STATE_LISTEN;
                                synchronized (intraThreadLock) {
                                    mineSeq = 0;
                                }
//                                Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                        + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D: in STATE_LISTEN");
                            }
                        } else {
//                            Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                    + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D: in STATE_CLOSED");
                            synchronized (TCPLink.this) {
                                while (state != STATE_SYN_SENT) {
                                    TCPLink.this.wait(10000);
                                }
                            }
                        }
                        while (true) {
                            final int stateLoc = getState();
                            if (stateLoc == STATE_E_DESTROYED) {
                                break;
                            } else {
//                                long start = System.currentTimeMillis();
//                                Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                        + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D wait for a new packet " + state);

                                IP ip = incomingPackets.take();
//                                if (System.currentTimeMillis() - start > 1000) {
//                                    Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                            + "SPEED ALERT: packet receive in " + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D " + "took " + (System.currentTimeMillis() - start));
//                                }

                                if (ip != null) {
                                    if ((ip.tcp.flags & TCP.RST) == TCP.RST) {
                                        synchronized (TCPLink.this) {
                                            state = STATE_E_DESTROYED;
                                            TCPLink.this.notifyAll();
                                            synchronized (intraThreadLock) {
                                                intraThreadLock.notifyAll();
                                            }
                                            return;
                                        }
                                    }
                                    switch (stateLoc) {
                                        case STATE_CLOSED:
//                                            Log.e(TAG, "ERROR: D thread in closed state");
                                            break;
                                        case STATE_LISTEN:
                                            if ((ip.tcp.flags & TCP.SYN) == TCP.SYN) {
                                                processSYN(ip);

                                            } else {
//                                                Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                        + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D: received NOT SYN in STATE_LISTEN");
                                            }
                                            break;
                                        case STATE_SYN_SENT:
                                            if ((ip.tcp.flags & TCP.SYN) == TCP.SYN
                                                    && (ip.tcp.flags & TCP.ACK) == TCP.ACK) {

                                                synchronized (TCPLink.this) {
                                                    if (state != STATE_SYN_SENT) {
//                                                        Log.e(TAG, !isClient ? "" : "                                                    "
//                                                                + "ERROR received SYN ACK in state " + state);//closed or destroyed?
                                                        return;
                                                    }
                                                    final int mineSeqLoc, heSentDataLoc;
                                                    synchronized (intraThreadLock) {
                                                        heSentDataLoc = heSentData = ip.tcp.seqNumber + 1;
                                                        mineSeqLoc = mineSeq;
                                                    }

                                                    byte[] tcp = TCP.build(localPort, remotePort, mineSeqLoc, heSentDataLoc,
                                                            TCP.ACK, getReadBufSize(), null,
                                                            ipLink.localAddress, remoteAddress);
//                                                    Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                            + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D: received SYN ACK " + ip.tcp.seqNumber + "/" + ip.tcp.ackNumber + " send ACK " + mineSeqLoc + "/" + heSentDataLoc);
                                                    ipLink.send(tcp, remoteAddress);

                                                    state = STATE_ESTABLISHED;
//                                                    Log.d(TAG, "* " + (!isClient ? "" : "                                                    ")
//                                                            + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D: in STATE_ESTABLISHED SS");
                                                    TCPLink.this.notifyAll();
                                                }
                                            } else {
//                                                Log.w(TAG, !isClient ? "" : "                                                    "
//                                                        + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D: ignore packet in STATE_SYN_SENT with flags  " + TCP.showFlags(ip.tcp.flags));//closed or destroyed?
                                            }
                                            break;
                                        case STATE_SYN_RECEIVED:
                                            if ((ip.tcp.flags & TCP.ACK) == TCP.ACK) {
                                                synchronized (TCPLink.this) {
                                                    if (state != STATE_SYN_RECEIVED) {
//                                                        Log.d(TAG, !isClient ? "" : "                                                    "
//                                                                + "ERROR received ACK in state " + state);//closed or destroyed?
                                                        return;
                                                    }
                                                    synchronized (intraThreadLock) {
                                                        lastReceivedAck = ip.tcp.ackNumber;
                                                        lastReceivedRecepientBufferSize = ip.tcp.windowSize;
                                                        intraThreadLock.notifyAll();
                                                        heSentData = ip.tcp.seqNumber;
                                                    }
                                                    state = STATE_ESTABLISHED;
                                                    TCPLink.this.notifyAll();
//                                                    Log.d(TAG, "* " + (!isClient ? "" : "                                                    ")
//                                                            + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D: in STATE_ESTABLISHED SR");
//                                                    if (ip.tcp.payload != null && ip.tcp.payload.length > 0) {
//                                                        Log.e(TAG, (!isClient ? "" : "                                                    ")
//                                                                + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D: in STATE_SYN_RECEIVED received GOOD packet, but payload will be ignored " + ip.tcp.payload.length + " " + ip.tcp.flags + " " + ip.tcp.seqNumber + "/" + ip.tcp.ackNumber);
//
//                                                    }
                                                }
                                            } else {
//                                                Log.e(TAG, (!isClient ? "" : "                                                    ")
//                                                        + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D: in STATE_SYN_RECEIVED received strange packet " + ip.tcp.seqNumber + "/" + ip.tcp.ackNumber + " flags " + TCP.showFlags(ip.tcp.flags));
                                            }
                                            break;
                                        case STATE_NO_MORE_DATA_TO_DOWNLOAD:
//                                            if ((System.nanoTime() - finWaitStart) / 1000000 > 120000L) {
//                                                Log.w(TAG, (!isClient ? "" : "                                                    ")
//                                                        + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D: received FIN long time ago");
//
//                                            }
                                        case STATE_NO_MORE_DATA_TO_UPLOAD:
                                        case STATE_ESTABLISHED:

                                            if ((ip.tcp.flags & TCP.SYN) == TCP.SYN) {
//                                                Log.e(TAG, "SYN in established state! " + ip.tcp.seqNumber + "/" + ip.tcp.ackNumber + " flags " + TCP.showFlags(ip.tcp.flags));
                                                synchronized (TCPLink.this) {
                                                    state = STATE_LISTEN;
                                                    synchronized (intraThreadLock) {
                                                        heSentData = lastReceivedAck = 0;
                                                        mineSeq = 0;
                                                    }
                                                    TCPLink.this.notifyAll();
                                                }
                                                processSYN(ip);
                                                break;
                                            }

                                            final boolean flowOk;
                                            final boolean fin;
                                            final int mineSeqLoc,
                                             heSentDataLoc;
                                            int payloadLen = ip.tcp.payload.length;
                                            synchronized (intraThreadLock) {
                                                lastReceivedRecepientBufferSize = ip.tcp.windowSize;

                                                fin = (ip.tcp.flags & (TCP.FIN)) == TCP.FIN;
                                                flowOk = heSentData == ip.tcp.seqNumber;

                                                if (flowOk) {
                                                    heSentData += payloadLen;
                                                    if (fin) {
                                                        heSentData++;
                                                    }
                                                }
//                                                if (payloadLen > 0) {
//                                                    Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                            + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D: received (NONZERO " + payloadLen + ") " + ip.tcp.seqNumber + "/" + ip.tcp.ackNumber + " flags " + TCP.showFlags(ip.tcp.flags)
//                                                            + " set lastReceivedAck to " + ip.tcp.ackNumber + (" it's " + (((ip.tcp.flags & TCP.ACK) == TCP.ACK))) + ", was " + lastReceivedAck
//                                                            + " heSentData now " + heSentData + ", " + (flowOk ? "FLOW OK " : "FLOW ERROR ")
//                                                            + " in " + TCPLink.this);
//                                                } else {
//                                                    Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                            + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D: received (ZERO bytes)" + ip.tcp.seqNumber + "/" + ip.tcp.ackNumber + " flags " + TCP.showFlags(ip.tcp.flags)
//                                                            + " heSentData now " + heSentData + ", set lastReceivedAck to " + ip.tcp.ackNumber + (" it's " + (((ip.tcp.flags & TCP.ACK) == TCP.ACK))) + ", was " + lastReceivedAck + " in " + TCPLink.this);
//                                                }
                                                if ((ip.tcp.flags & TCP.ACK) == TCP.ACK) {
                                                    lastReceivedAck = ip.tcp.ackNumber;
                                                }
                                                heSentDataLoc = heSentData;
                                                mineSeqLoc = mineSeq;
                                                intraThreadLock.notifyAll();
                                            }

                                            if (payloadLen > 0) {
                                                if (flowOk) {
                                                    downstreamFeed.write(ip.tcp.payload);
                                                    if ((ip.tcp.flags & TCP.PSH) == TCP.PSH) {
                                                        downstreamFeed.flush();
                                                    }
//                                                    Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                            + (isClient ? "C" : "S")+TCPLink.this.hashCode() +"D: data feed was fed) " + TCPLink.this);
                                                } else {
//                                                    Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                            + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D: !!!!!DISCARD PACKET " + ip.tcp.seqNumber + "/" + ip.tcp.ackNumber + " should be " + heSentData + "/x, so request packet from position " + heSentData + " instead " + ip.tcp.seqNumber + " " + TCPLink.this);
                                                }
                                                byte[] tcp = TCP.build(localPort, remotePort, mineSeqLoc, heSentDataLoc,
                                                        TCP.ACK, getReadBufSize(), null,
                                                        ipLink.localAddress, remoteAddress);
//                                                Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                        + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D: SEND E ACK " + mineSeqLoc + "/" + heSentDataLoc);
//                                                start = System.currentTimeMillis();

                                                ipLink.send(tcp, remoteAddress);
//                                                if (System.currentTimeMillis() - start > 500) {
//                                                    Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                            + "SPEED ALERT: packet response sending in " + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D " + "took " + (System.currentTimeMillis() - start) + " ms");
//                                                }

//                                                Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                        + (isClient ? "C" : "S")+TCPLink.this.hashCode() +"D: the e packet was sent " + TCPLink.this);
                                            } else {
//                                                if (!flowOk) {
//                                                    Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                            + "!!!!!STRANGE E PACKET " + ip.tcp.seqNumber + "/" + ip.tcp.ackNumber + " should be " + heSentData + "/x, it means I'm waiting for confirmation of sending, FIN? " + fin);
//                                                }
                                            }
                                            if (flowOk && fin) {
                                                byte[] tcp = TCP.build(localPort, remotePort, mineSeqLoc, heSentDataLoc,
                                                        TCP.ACK, getReadBufSize(), null,
                                                        ipLink.localAddress, remoteAddress);
//                                                Log.d(TAG, (!isClient ? "" : "                                                    ")
//                                                        + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D: received FIN (" + ip.tcp.payload.length + " bytes) " + ip.tcp.seqNumber + "/" + ip.tcp.ackNumber + " flags " + TCP.showFlags(ip.tcp.flags)
//                                                        + " heSentData " + heSentData + ", " + (flowOk ? "FLOW OK " : "FLOW ERROR ")
//                                                        + "   !SEND E ACK " + mineSeqLoc + "/" + heSentDataLoc);
                                                ipLink.send(tcp, remoteAddress);

                                                downstreamFeed.flush();
                                                downstreamFeed.close();
                                                synchronized (TCPLink.this) {

                                                    if (state == STATE_NO_MORE_DATA_TO_UPLOAD) {
//                                                        Log.d(TAG, (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D" + " correctly closed");
                                                        return;
                                                    }
                                                    TCPLink.this.notifyAll();
                                                    state = STATE_NO_MORE_DATA_TO_DOWNLOAD;
//                                                    finWaitStart = System.nanoTime();
                                                }
                                            }

                                            break;
                                    }
                                }
                            }
                        }
                    } catch (Exception ex) {
                        if (BuildConfig.DEBUG) {
                            Log.d("TCP", "ex " + ex + " in " + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D");
                        }
                    } finally {
                        synchronized (TCPLink.this) {
//                            Log.d(TAG, "thread death " + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D state " + state + " received/sent " + receivedBytes + "/" + sentBytes);
                            state = STATE_E_DESTROYED;
                            ipLink.close(TCPLink.this);
                        }

                    }

                }

                private void processSYN(IP ip) throws IOException {
                    final int mineSeqLoc, heSentDataLoc;
                    synchronized (TCPLink.this) {
                        if (state != STATE_LISTEN) {
//                            Log.d(TAG, !isClient ? "" : "                                                    "
//                                    + "ERROR received SYN in state " + state);//closed or destroyed?
                            return;
                        }
                        synchronized (intraThreadLock) {
                            remotePort = ip.tcp.sourcePort;
                            heSentData = ip.tcp.seqNumber;
                            if ((ip.tcp.flags & TCP.ACK) == TCP.ACK) {
                                lastReceivedAck = ip.tcp.ackNumber;
                            }
                            lastReceivedRecepientBufferSize = ip.tcp.windowSize;
                            intraThreadLock.notifyAll();
                            mineSeqLoc = mineSeq;
                            heSentDataLoc = heSentData + 1;
                            mineSeq++;
                        }
                        state = STATE_SYN_RECEIVED;
                        TCPLink.this.notifyAll();
                        mss = ip.tcp.mss;
//                        Log.d(TAG, (!isClient ? "" : "                                                  * ")
//                                + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D: in STATE_SYN_RECEIVED, MSS " + mss);
                    }
//                                            if(heSentData==0) Log.d(TAG, "!!!!!!!!!! send tcp with zero ack (ON LIST) "+TCPLink.this);
                    byte[] tcp = TCP.build(localPort, remotePort, mineSeqLoc, heSentDataLoc,
                            (byte) (TCP.SYN | TCP.ACK), getReadBufSize(), null,
                            ipLink.localAddress, remoteAddress);
//                    Log.d(TAG, (!isClient ? "" : "                                                    ")
//                            + (isClient ? "C" : "S") + TCPLink.this.hashCode() + "D: received SYN " + ip.tcp.seqNumber + "/" + ip.tcp.ackNumber + " send SYN ACK " + mineSeqLoc + "/" + heSentDataLoc);
                    ipLink.send(tcp, remoteAddress);

                }
            });
            downloadThread.start();
        }
    }

    public void setAppLayerThread(Thread thread) {
        appLayerThread = thread;
    }

    void processPacket(IP ip) throws InterruptedException {
        incomingPackets.put(ip);
    }

    void close() {
        if (appLayerThread != null && appLayerThread.isAlive()) {
            appLayerThread.interrupt();
        }
        if (downloadThread != null && downloadThread.isAlive()) {
            downloadThread.interrupt();
        }
        if (uploadThread != null && uploadThread.isAlive()) {
            uploadThread.interrupt();
        }
    }

    Long getKey() {
        return key;
    }
}
