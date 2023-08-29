// Fake encryption/decryption that doesn't really do much
// Should be replaced with a proper encryption method

using System;
using System.Text;

public static class Encrypter
{
    private static bool doEncrypt = true;

    public static byte[] DumbEncrypt(string msg)
    {
        var msgBytes = Encoding.UTF8.GetBytes(msg);
        if (!doEncrypt) return msgBytes;

        var lm = msgBytes.Length;

        for (int i = 0; i < lm; i++)
        {
            if (i % 2 == 0)
            {
                if (msgBytes[i] > 16) msgBytes[i] -= 16;
            }
            else
            {
                if (msgBytes[i] < 128) msgBytes[i] += 128;
            }
        }

        return msgBytes;
    }

    public static string DumbDecrypt(byte[] msgBytes)
    {
        if (!doEncrypt)
            return Encoding.UTF8.GetString(msgBytes);

        var lm = msgBytes.Length;

        for (int i = 0; i < lm; i++)
        {
            if (i % 2 == 0)
            {
                msgBytes[i] += 16;
            }
            else
            {
                msgBytes[i] -= 128;
            }
        }

        return Encoding.UTF8.GetString(msgBytes);
    }
}