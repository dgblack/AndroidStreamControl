using System.Runtime.InteropServices;

public class Timer
{
    //[DllImport("CppTimer", CharSet = CharSet.Unicode, EntryPoint = "GetTime", CallingConvention = CallingConvention.Cdecl)]
    [DllImport("CppTimer")] public static extern long GetTime();
}
