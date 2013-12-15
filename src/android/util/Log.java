/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package android.util;

import java.io.IOException;

/**
 * @author vkonova
 */
public class Log {

    public static void w(String TAG, String string) {
        System.out.println(string);
    }

    public static void d(String TAG, String string) {
        System.out.println(string);
    }

    public static void e(String TAG, String string) {
        System.out.println(string);
    }

    public static void e(String TAG, String string, Throwable ex) {
        System.out.println(string);
        ex.printStackTrace();
    }

    public static void i(String TAG, String string) {
        System.out.println(string);
    }

    public static void w(String TAG, String string, Throwable ex) {
        System.out.println(string);
        ex.printStackTrace();
    }
}
