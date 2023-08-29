package com.rcl.androidstreamcontrol;

import org.junit.Test;

import static org.junit.Assert.*;

import com.rcl.androidstreamcontrol.utils.Utils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class UtilsTests {

    private static final float testF1 = 0.4103020717f;
    private static final float testF2 = 0.8738664541f;
    private static final float testF3 = 0.1702606734f;
    private static final float testF4 = 0.4294712207f;

    @Test
    public void float_byte_conversions() {
        Float testFloat;
        byte[] testBytes;

        testBytes= Utils.floatToBytes(testF1);
        testFloat = Utils.bytesToFloat(testBytes);
        System.out.println(String.format("%.7f", testFloat) + " " + String.format("%.7f", testF1));
        assertEquals(String.format("%.7f", testFloat), String.format("%.7f", testF1));

        testBytes= Utils.floatToBytes(testF2);
        testFloat = Utils.bytesToFloat(testBytes);
        System.out.println(String.format("%.7f", testFloat) + " " + String.format("%.7f", testF2));
        assertEquals(String.format("%.7f", testFloat), String.format("%.7f", testF2));

        testBytes= Utils.floatToBytes(testF3);
        testFloat = Utils.bytesToFloat(testBytes);
        System.out.println(String.format("%.7f", testFloat) + " " + String.format("%.7f", testF3));
        assertEquals(String.format("%.7f", testFloat), String.format("%.7f", testF3));

        testBytes= Utils.floatToBytes(testF4);
        testFloat = Utils.bytesToFloat(testBytes);
        System.out.println(String.format("%.7f", testFloat) + " " + String.format("%.7f", testF4));
        assertEquals(String.format("%.7f", testFloat), String.format("%.7f", testF4));

        testBytes= Utils.floatToBytes(null);
        assertEquals(testBytes[0], 0);
        assertEquals(testBytes[1], 0);
        assertEquals(testBytes[2], 0);
        assertEquals(testBytes[3], 0);
        assertNotNull(testBytes);
        testFloat = Utils.bytesToFloat(testBytes);
        assertNull(testFloat);
    }

    private static final byte[] testB1 = {(byte) 0x12, (byte) 0xEF, (byte) 0xEF, (byte) 0xEF};
    private static final byte[] testB2 = new byte[10];
    private static final byte[] testB3 = new byte[0];

    @Test
    public void bytes_bytebuffer_conversions() {
        byte[] testBytes;
        ByteBuffer testByteBuffer;

        testByteBuffer = Utils.bytesToByteBuffer(testB1);
        assertEquals(testByteBuffer.remaining(), 4);
        testBytes = Utils.byteBufferToBytes(testByteBuffer);
        assertEquals(testB1[0], testBytes[0]);
        assertEquals(testB1[1], testBytes[1]);
        assertEquals(testB1[2], testBytes[2]);
        assertEquals(testB1[3], testBytes[3]);

        testByteBuffer = Utils.bytesToByteBuffer(testB2);
        assertEquals(testByteBuffer.remaining(), 10);
        testBytes = Utils.byteBufferToBytes(testByteBuffer);
        assertEquals(testB2.length, testBytes.length);

        testByteBuffer = Utils.bytesToByteBuffer(testB3);
        assertEquals(testByteBuffer.remaining(), 0);
        testBytes = Utils.byteBufferToBytes(testByteBuffer);
        assertEquals(testB3.length, testBytes.length);
    }

    @Test
    public void combo_test() {
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream(20);
        byteOutputStream.write(Utils.floatToBytes(testF1), 0, 4);
        byteOutputStream.write(Utils.floatToBytes(testF2), 0, 4);
        byteOutputStream.write(Utils.floatToBytes(null), 0, 4);
        byteOutputStream.write(Utils.floatToBytes(testF3), 0, 4);
        byteOutputStream.write(Utils.floatToBytes(testF4), 0, 4);

        byte[] inputByteArray = byteOutputStream.toByteArray();

        ByteBuffer testByteBuffer = Utils.bytesToByteBuffer(inputByteArray);
        assertEquals(testByteBuffer.remaining(), 20);
        byte[] outputByteArray = Utils.byteBufferToBytes(testByteBuffer);
        assertEquals(outputByteArray.length, 20);

        Float t1 = Utils.bytesToFloat(Arrays.copyOfRange(outputByteArray, 0, 4));
        Float t2 = Utils.bytesToFloat(Arrays.copyOfRange(outputByteArray, 4, 8));
        Float t3 = Utils.bytesToFloat(Arrays.copyOfRange(outputByteArray, 8, 12));
        Float t4 = Utils.bytesToFloat(Arrays.copyOfRange(outputByteArray, 12, 16));
        Float t5 = Utils.bytesToFloat(Arrays.copyOfRange(outputByteArray, 16, 20));

        assertEquals(String.format("%.7f", t1), String.format("%.7f", testF1));
        assertEquals(String.format("%.7f", t2), String.format("%.7f", testF2));
        assertNull(t3);
        assertEquals(String.format("%.7f", t4), String.format("%.7f", testF3));
        assertEquals(String.format("%.7f", t5), String.format("%.7f", testF4));
    }
}