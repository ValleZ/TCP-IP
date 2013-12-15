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

/**
 * Created by IntelliJ IDEA. User: vakonova Date: 3/7/12
 */
public final class IP {

    int totalLength;
    int protocol;
    private int checksum;
    int sourceAddress;
    private int сhecksumCalculated;
    TCP tcp;

    public IP(byte[] packet) throws IOException {
//        System.out.println("         ip "+toHex(packet));
//        ipbytes = packet;
        byte version = (byte) ((packet[0] & 0xFF) >> 4);
        if (version == 4) {
            byte internetHeaderLength = (byte) (packet[0] & 7);
//            typeOfService = packet[1];
            totalLength = ((packet[2] & 0xff) << 8) | (packet[3] & 0xff);
//            id = ((packet[4] & 0xff) << 8) | (packet[5] & 0xff);
//            flags = (byte) ((packet[6] & 0xE0) >> 5);
//            fragmentOffset = ((packet[6] & 0x1F) << 8) | (packet[7] & 0xff);
//            ttl = packet[8] & 0xFF;
            protocol = packet[9] & 0xFF;
            checksum = ((packet[10] & 0xff) << 8) | (packet[11] & 0xff);
            sourceAddress = ((packet[12] & 0xff) << 24) | ((packet[13] & 0xff) << 16) | ((packet[14] & 0xff) << 8) | (packet[15] & 0xff);
            int destAddress = ((packet[16] & 0xff) << 24) | ((packet[17] & 0xff) << 16) | ((packet[18] & 0xff) << 8) | (packet[19] & 0xff);
            int offs = internetHeaderLength * 4;
            int len = totalLength - offs;
            if (len < 0 || len > 65536 || offs < 0 || offs >= packet.length || offs + len > packet.length) {
                throw new IOException();
            }
            byte[] payload = new byte[len];
            System.arraycopy(packet, offs, payload, 0, len);

            if (protocol == 6) {
                tcp = new TCP(sourceAddress, destAddress, payload);
            }
            сhecksumCalculated = IP.ip_sum_calc(packet);
        }
    }

    public static int readIPVersion(byte[] packet) {
        return (packet[0] & 0xFF) >> 4;
    }

    public static int readDestAddress(byte[] packet) {
        return ((packet[16] & 0xff) << 24) | ((packet[17] & 0xff) << 16) | ((packet[18] & 0xff) << 8) | (packet[19] & 0xff);
    }
    private static short idCounter = 0x1234;

    public static byte[] build(int sourceAddress, int destAddress, byte[] tcp) {
//        this.sourceAddress = sourceAddress;
//        this.destAddress = destAddress;
//        this.tcp = tcp;
//        version = 4;
//        internetHeaderLength = 5;
        int typeOfService = 0;
        int flags = 0;
        int id = ++idCounter;
        int fragmentOffset = 0;
        int ttl = 0x20;
        int protocol = 0x06;
//        return new IP(sourceAddress, destAddress, id, tcp).getBytes();//wrap tcpbytes

        int totalLength = 20 + tcp.length;
        byte[] result = new byte[totalLength];
        result[0] = (byte) (0x45);
        result[1] = (byte) (typeOfService);
        result[2] = (byte) ((totalLength >>> 8) & 0xFF);
        result[3] = (byte) ((totalLength) & 0xFF);
//        --- 1
        result[4] = (byte) ((id >>> 8) & 0xFF);
        result[5] = (byte) (id & 0xff);
        result[6] = (byte) ((flags << 5) | (fragmentOffset >> 8) & 0x1F);
        result[7] = (byte) ((fragmentOffset) & 0xFF);
//        --- 2
        result[8] = (byte) (ttl);
        result[9] = (byte) (protocol);
//        --- 3
        result[12] = (byte) ((sourceAddress >>> 24) & 0xFF);
        result[13] = (byte) ((sourceAddress >>> 16) & 0xFF);
        result[14] = (byte) ((sourceAddress >>> 8) & 0xFF);
        result[15] = (byte) ((sourceAddress) & 0xFF);
//        --- 4
        result[16] = (byte) ((destAddress >>> 24) & 0xFF);
        result[17] = (byte) ((destAddress >>> 16) & 0xFF);
        result[18] = (byte) ((destAddress >>> 8) & 0xFF);
        result[19] = (byte) ((destAddress) & 0xFF);
        System.arraycopy(tcp, 0, result, 20, tcp.length);





        int sum = 0;
        for (int i = 0; i < 20; i = i + 2) {
            int word16 = (((result[i] & 0xFF) << 8) & 0xFF00) + (result[i + 1] & 0xFF);
            sum += word16;
        }
        while (sum >>> 16 > 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        sum = ~sum;
        int checksum = sum & 0xFFFF;

        result[10] = (byte) (((checksum & 0xff00) >>> 8) & 0xFF);
        result[11] = (byte) ((checksum) & 0xFF);
        return result;
    }

    boolean verify() {
        if (tcp.checksum != tcp.сhecksumCalculated) {
//            Log.e(TAG, "TERROR " + Integer.toHexString(tcp.checksum) + "!=" + Integer.toHexString(tcp.сhecksumCalculated));
        } else if (checksum != сhecksumCalculated) {
//            Log.e(TAG, "IERROR " + Integer.toHexString(checksum) + "!=" + Integer.toHexString(сhecksumCalculated));
//            verify();
        } else {
//            System.out.println("OK!!!");
            return true;
        }
        return false;
    }

    private static int ip_sum_calc(byte[] buff) {
        buff[10] = buff[11] = 0;
        int sum = 0;
        for (int i = 0; i < 20; i = i + 2) {
            int word16 = (((buff[i] & 0xFF) << 8) & 0xFF00) + (buff[i + 1] & 0xFF);
            sum += word16;
        }
        while (sum >>> 16 > 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        sum = ~sum;
        return sum & 0xFFFF;
    }

    public static String toHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        final String chars = "0123456789ABCDEF";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte aByte : bytes) {
            int b = aByte & 0xFF;
            sb.append(chars.charAt(b >> 4));
            sb.append(chars.charAt(b & 15));
        }
        return sb.toString();
    }

    public static byte[] fromHex(String s) {
        if (s == null) {
            return null;
        }
        final String chars = "0123456789ABCDEF";
        int len = s.length() / 2;
        ByteArrayOutputStream baos = new ByteArrayOutputStream(len);
        for (int i = 0; i < len; i++) {
            baos.write((chars.indexOf(s.charAt(i * 2)) << 4) + chars.indexOf(s.charAt(i * 2 + 1)));
        }
        return baos.toByteArray();
    }
}

final class TCP {

    final int sourcePort, destPort, seqNumber, ackNumber;
    final byte flags;
    final int windowSize, mss;
    final int checksum;
//    final int urgentPointer;
    final int сhecksumCalculated;
//    final byte[] options;
    final byte[] payload;
    public static final byte FIN = 1;
    public static final byte SYN = 2;
    public static final byte RST = 4;
    public static final byte PSH = 8;
    public static final byte ACK = 16;

    TCP(int sourceAddress, int destAddress, byte[] packet) throws IOException {
//        bytes = packet;
        sourcePort = ((packet[0] & 0xff) << 8) | (packet[1] & 0xff);
        destPort = ((packet[2] & 0xff) << 8) | (packet[3] & 0xff);
        seqNumber = ((packet[4] & 0xff) << 24) | ((packet[5] & 0xff) << 16) | ((packet[6] & 0xff) << 8) | (packet[7] & 0xff);
        ackNumber = ((packet[8] & 0xff) << 24) | ((packet[9] & 0xff) << 16) | ((packet[10] & 0xff) << 8) | (packet[11] & 0xff);
        byte dataOffset = (byte) ((packet[12] & 0xF0) >> 4);
        flags = packet[13];
        int windowSizeLoc = ((packet[14] & 0xff) << 8) | (packet[15] & 0xff);
        checksum = ((packet[16] & 0xff) << 8) | (packet[17] & 0xff);
//        urgentPointer = ((packet[18] & 0xff) << 8) | (packet[19] & 0xff);
        int len = (dataOffset - 5) * 4;
        if (len < 0 || len > 20 + packet.length) {
            throw new IOException();
        }
        int mssLoc = 0;
//        options = new byte[len];
//        System.arraycopy(packet, 20, options, 0, options.length);
        int pos = 20;
        while (pos < len + 20) {
            byte kind = packet[pos++];
            if (kind == 0) {
                break;
            } else if (kind == 1) {
            } else {
                byte optLen = packet[pos];
                if (kind == 2) {//Maximum segment size
                    mssLoc = ((packet[pos + 1] & 0xFF) << 8) | packet[pos + 2] & 0xFF;
                } else if (kind == 3) {//Window scale
                    if ((flags & SYN) == 0) {
                        windowSizeLoc <<= ((packet[pos + 1] & 0xFF) << 8);
                    }
                }//kind == 4 is selective ACK permission
                //kind == 8 is timestamp option
                pos += optLen - 1;
            }
        }
        windowSize = windowSizeLoc;
        mss = mssLoc;
        if (len < 0 || len > 65536) {
            throw new IOException();
        }
        len = packet.length - dataOffset * 4;
        payload = new byte[len];
        System.arraycopy(packet, dataOffset * 4, payload, 0, payload.length);
        сhecksumCalculated = tcp_sum_calc(packet, sourceAddress, destAddress);
    }

    public static byte[] build(int sourcePort, int destPort, int seqNumber, int ackNumber, byte flags, int windowSize, byte[] payload, int srcAddr, int destAddr) {
        if (sourcePort == 0 || destPort == 0) {
            throw new IllegalArgumentException();
        }
        int payloadLength = payload == null ? 0 : payload.length;
        byte[] bytes = new byte[20 + payloadLength];

        bytes[0] = (byte) ((sourcePort >>> 8) & 0xFF);
        bytes[1] = (byte) (sourcePort & 0xff);
        bytes[2] = (byte) ((destPort >>> 8) & 0xFF);
        bytes[3] = (byte) (destPort & 0xff);

        bytes[4] = (byte) ((seqNumber >>> 24) & 0xFF);
        bytes[5] = (byte) ((seqNumber >>> 16) & 0xFF);
        bytes[6] = (byte) ((seqNumber >>> 8) & 0xFF);
        bytes[7] = (byte) ((seqNumber) & 0xFF);

        bytes[8] = (byte) ((ackNumber >>> 24) & 0xFF);
        bytes[9] = (byte) ((ackNumber >>> 16) & 0xFF);
        bytes[10] = (byte) ((ackNumber >>> 8) & 0xFF);
        bytes[11] = (byte) ((ackNumber) & 0xFF);
        //        --- 8
        bytes[12] = (byte) ((5) << 4);
        bytes[13] = (byte) (flags & 0xff);
        bytes[14] = (byte) ((windowSize >>> 8) & 0xFF);
        bytes[15] = (byte) (windowSize & 0xff);

        if (payloadLength > 0) {
            System.arraycopy(payload, 0, bytes, 20, payloadLength);
        }

        int word16;
//        int sum = (buff.length & 1) == 1 ? (((buff[buff.length - 1] & 0xFF) << 8) & 0xFF00) : 0;
        int sum = 0;
        for (int i = 0; i < bytes.length; i = i + 2) {
            byte secondByte = i + 1 < bytes.length ? bytes[i + 1] : 0;
            word16 = (((bytes[i] & 0xFF) << 8) & 0xFF00) | (secondByte & 0xFF);
            sum += word16;
        }
        sum += ((srcAddr >>> 16) & 0xFFFF) + ((srcAddr) & 0xFFFF);
        sum += ((destAddr >>> 16) & 0xFFFF) + ((destAddr) & 0xFFFF);
        sum += 6 + bytes.length;
        while (sum >>> 16 > 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        sum = ~sum;
        int checksum = sum & 0xFFFF;
        bytes[16] = (byte) ((checksum >>> 8) & 0xFF);
        bytes[17] = (byte) (checksum & 0xff);

        return bytes;
    }
//    private static final byte[] EMPTY = new byte[0];

    private static int tcp_sum_calc(byte[] buff, int srcAddr, int destAddr) {
        buff[16] = buff[17] = 0;

        int word16;
        //seems there is a strange bug in other side
//        int sum = (buff.length & 1) == 1 ? (((buff[buff.length - 1] & 0xFF) << 8) & 0xFF00) : 0;
        int sum = 0;
        for (int i = 0; i < buff.length; i = i + 2) {
            byte secondByte = i + 1 < buff.length ? buff[i + 1] : 0;
            word16 = (((buff[i] & 0xFF) << 8) & 0xFF00) | (secondByte & 0xFF);
            sum += word16;
        }
        sum += ((srcAddr >>> 16) & 0xFFFF) + ((srcAddr) & 0xFFFF);
        sum += ((destAddr >>> 16) & 0xFFFF) + ((destAddr) & 0xFFFF);
        sum += 6 + buff.length;
        while (sum >>> 16 > 0) {
            sum = (sum & 0xFFFF) + (sum >> 16);
        }
        sum = ~sum;
        return sum & 0xFFFF;
    }
//    @Override
//    public String toString() {
//        return "TCP{" + "sourcePort=" + sourcePort + ", destPort=" + destPort + ", seqNumber=" + seqNumber + ", ackNumber=" + ackNumber + ", flags=" + showFlags(flags) + ", windowSize=" + windowSize + ", checksum=" + checksum + ", urgentPointer=" + urgentPointer + ", payload=" + IP.toHex(payload) + '}';
//    }
//    public static String showFlags(byte flags) {
//        StringBuilder sb = new StringBuilder();
//        if ((flags & 1) == 1) {
//            sb.append("FIN ");
//        }
//        if ((flags & 2) == 2) {
//            sb.append("SYN ");
//        }
//        if ((flags & 4) == 4) {
//            sb.append("RST ");
//        }
//        if ((flags & 8) == 8) {
//            sb.append("PSH ");
//        }
//        if ((flags & 16) == 16) {
//            sb.append("ACK ");
//        }
//        if ((flags & 32) == 32) {
//            sb.append("URG ");
//        }
//        if ((flags & 64) == 64) {
//            sb.append("ECE ");
//        }
//        if ((flags & 128) == 128) {
//            sb.append("CWR ");
//        }
//        return sb.toString();
//
//    }
}
