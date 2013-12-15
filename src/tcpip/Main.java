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

package tcpip;

import android.util.Log;
import ru.valle.net.tcpip.IPLink;
import ru.valle.net.tcpip.SLIP;

import java.util.UUID;

/**
 * @author vkonova
 */
public class Main {
    private static final int TIMEOUT_MINUTES = 10;
    private static final String TAG = "Main";
    private static boolean SEND_SOMETHING = true;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try {
            //obtain a socket with SLIP/IP/TCP/HTTP stream:
            BluetoothServerSocket serverSocket = BluetoothAdapter.getDefaultAdapter().listenUsingRfcommWithServiceRecord("Peer Communication Framework", UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
            BluetoothSocket socket = serverSocket.accept(TIMEOUT_MINUTES * 60 * 1000);
            SLIP slip = new SLIP(socket.getInputStream(), socket.getOutputStream());
            final IPLink ipLink = new IPLink(0xC0A80101, slip);
            ipLink.start();

            if (SEND_SOMETHING) {
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            ipLink.sendReq("GET /remotePath?command=doFunStuff HTTP/1.1\r\nHost: 192.168.1.1\r\n\r\n");
                        } catch (Exception ex) {
                            Log.e(TAG, "ee", ex);
                        }
                    }
                }).start();
            }
            ipLink.receivePackets();//incoming HTTP requests will be processed by HTTPProxy class
        } catch (Exception e) {
            Log.e(TAG, "connection error", e);
        }
    }
}
