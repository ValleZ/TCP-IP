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

package ru.valle.net.applayer;

import android.util.Log;
import ru.valle.net.tcpip.IPLink;
import ru.valle.net.tcpip.TCPLink;

import java.io.IOException;

/**
 *
 * @author vkonova
 */
public final class TCPLinkFactory {

    public static TCPLink create(int sourceAddress, int remotePort, int localPort, Long key, IPLink ipLink) throws IOException {
        final TCPLink link = new TCPLink(localPort, remotePort, sourceAddress, key, ipLink);

        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    HTTPProxy proxy = new HTTPProxy(Cache.getInstance(), link.getDownstream(), link.getUpstream());
                    proxy.run();
                } catch (IOException ex) {
                    Log.e("TL", "err", ex);
                } catch(Throwable th) { 
                    Log.e("TL", "tr err", th);
                }
            }
        });
        thread.start();
        link.setAppLayerThread(thread);

        return link;
    }
}
