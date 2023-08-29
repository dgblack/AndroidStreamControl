package com.rcl.androidstreamcontrol.utils;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class Utils {


    public static Integer twoBytesToInt(byte[] bytes) {

        if (bytes == null) {
            return null;
        }

        return ((bytes[0] & 0xFF) << 8) | ((bytes[1] & 0xFF));
    }

    public static byte[] intToTwoBytes(Integer intVal) {

        if (intVal == null) {
            return null;
        }

        return new byte[] {
                (byte) ((intVal >> 8) & 0xFF),
                (byte) ((intVal) & 0xFF)
        };
    }

    public static Float bytesToFloat(byte[] bytes) {

        if (bytes[0] == 0 && bytes[1] == 0 && bytes[2] == 0 && bytes[3] == 0) {
            return null;
        }

        int intBits =
                bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
        return Float.intBitsToFloat(intBits);
    }

    public static byte[] floatToBytes(Float floatVal) {

        if (floatVal == null) {
            return new byte[4];
        }

        int intBits = Float.floatToIntBits(floatVal);
        return new byte[] {
                (byte) (intBits >> 24),
                (byte) (intBits >> 16),
                (byte) (intBits >> 8),
                (byte) (intBits)
        };
    }

    public static byte[] byteBufferToBytes(ByteBuffer byteBuffer) {
        byte[] bytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(bytes, 0, bytes.length);
        return bytes;
    }

    public static ByteBuffer bytesToByteBuffer(byte[] bytes) {
        return ByteBuffer.wrap(bytes);
    }

    public static byte[] stringToBytes(String string) {
        return string.getBytes(StandardCharsets.UTF_8);
    }

    public static String bytesToString(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static ByteBuffer stringToByteBuffer(String string) {
        return ByteBuffer.wrap(string.getBytes(Charset.defaultCharset()));
    }

    public static String byteBufferToString(ByteBuffer byteBuffer) {
        byte[] bytes;
        if (byteBuffer.hasArray()) {
            bytes = byteBuffer.array();
        } else {
            bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
        }
        return new String(bytes, Charset.defaultCharset());
    }





}
