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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * @author vkonova
 */
public final class DataPipe {
    private static final DataPipe dataPipe;

    static {
        DataPipe dp;
        try {
            dp = new DataPipe();
        } catch (IOException ex) {
            dp = null;
        }
        dataPipe = dp;
    }

    static DataPipe getInstance() {
        return dataPipe;
    }

    private final PipedInputStream downstream;
    private final PipedOutputStream upstream;
    private final PipedOutputStream downstreamServ;
    private final PipedInputStream upstreamServ;

    public DataPipe() throws IOException {
        downstreamServ = new PipedOutputStream();
//        downstreamServ.setName("server.phys.down");
        downstream = new PipedInputStream(256);
//        downstream.setName("client.phys.down");
        upstream = new PipedOutputStream();
//        upstream.setName("client.phys.up");
        upstreamServ = new PipedInputStream(256);
//        upstreamServ.setName("server.phys.up");


        downstreamServ.connect(downstream);
        upstreamServ.connect(upstream);
    }

    OutputStream getUpstream() {
        return upstream;
    }

    InputStream getDownstream() {
        return downstream;
    }


    InputStream getUpstreamServ() {
        return upstreamServ;
    }

    OutputStream getDownstreamServ() {
        return downstreamServ;
    }

}
