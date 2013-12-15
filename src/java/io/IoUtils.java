package java.io;

public class IoUtils {
    public static void throwInterruptedIoException() throws IOException {
        throw new IOException("interrupted");
    }
}
