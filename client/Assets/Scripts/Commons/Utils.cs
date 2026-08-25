using Assets.Scripts.IOs;
using Assets.Scripts.Libs.Jsons;
using System;
using System.Collections.Generic;
using System.Threading;
using UnityEngine;

namespace Assets.Scripts.Commons
{
    public class Utils
    {
        public static System.Random r = new System.Random();

        private static int[] tanz;

        private static short[] sinz = new short[91]
        {
            0, 18, 36, 54, 71, 89, 107, 125, 143, 160,
            178, 195, 213, 230, 248, 265, 282, 299, 316, 333,
            350, 367, 384, 400, 416, 433, 449, 465, 481, 496,
            512, 527, 543, 558, 573, 587, 602, 616, 630, 644,
            658, 672, 685, 698, 711, 724, 737, 749, 761, 773,
            784, 796, 807, 818, 828, 839, 849, 859, 868, 878,
            887, 896, 904, 912, 920, 928, 935, 943, 949, 956,
            962, 968, 974, 979, 984, 989, 994, 998, 1002, 1005,
            1008, 1011, 1014, 1016, 1018, 1020, 1022, 1023, 1023, 1024,
            1024
        };

        private static short[] cosz;

        static Utils()
        {
            cosz = new short[91];
            tanz = new int[91];
            for (int i = 0; i <= 90; i++)
            {
                cosz[i] = sinz[90 - i];
                if (cosz[i] == 0)
                {
                    tanz[i] = int.MaxValue;
                }
                else
                {
                    tanz[i] = (sinz[i] << 10) / cosz[i];
                }
            }
        }

        public static string LoadJson(string filename)
        {
            TextAsset textAsset = (TextAsset)Resources.Load("Jsons/" + filename, typeof(TextAsset));
            return textAsset.text;
        }

        public static List<int> ReadIntList(JsonData jsonData)
        {
            List<int> result = new List<int>();
            foreach (JsonData item in jsonData)
            {
                result.Add((int)item);
            }
            return result;
        }

        public static Dictionary<int, int> ReadIntDictionary(JsonData jsonData)
        {
            Dictionary<int, int> result = new Dictionary<int, int>();
            foreach (string key in jsonData.Keys)
            {
                result[int.Parse(key)] = (int)jsonData[key];
            }
            return result;
        }

        public static long CurrentTimeMillis()
        {
            DateTime dateTime = new DateTime(1970, 1, 1, 0, 0, 0, DateTimeKind.Utc);
            return (DateTime.UtcNow.Ticks - dateTime.Ticks) / 10000;
        }

        public static sbyte[] convertToSbyteArray(byte[] scr)
        {
            sbyte[] array = new sbyte[scr.Length];
            for (int i = 0; i < scr.Length; i++)
            {
                array[i] = (sbyte)scr[i];
            }
            return array;
        }

        public static byte[] convertToByteArray(sbyte[] data)
        {
            byte[] array = new byte[data.Length];
            for (int i = 0; i < array.Length; i++)
            {
                array[i] = (byte)data[i];
            }
            return array;
        }

        public static char[] convertToCharArray(sbyte[] data)
        {
            char[] array = new char[data.Length];
            for (int i = 0; i < array.Length; i++)
            {
                array[i] = (char)data[i];
            }
            return array;
        }

        public static int distance(int x1, int y1, int x2, int y2)
        {
            return sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
        }

        public static int sqrt(int a)
        {
            if (a <= 0)
            {
                return 0;
            }
            int num = (a + 1) / 2;
            int num2;
            do
            {
                num2 = num;
                num = num / 2 + a / (2 * num);
            }
            while (Math.Abs(num2 - num) > 1);
            return num;
        }

        public static string FormatNumber(long number)
        {
            string empty = string.Empty;
            string empty2 = string.Empty;
            empty = string.Empty;
            if (number >= 1000000000000000)
            {
                empty2 = "Tr Tỉ";
                long num = number % 1000000000000000 / 100000000000000;
                number /= 1000000000000000;
                empty = number + string.Empty;
                if (num > 0)
                {
                    string text = empty;
                    return text + "," + num + empty2;
                }
                return empty + empty2;
            }
            if (number >= 1000000000000)
            {
                empty2 = "K Tỉ";
                long num = number % 1000000000000 / 100000000000;
                number /= 1000000000000;
                empty = number + string.Empty;
                if (num > 0)
                {
                    string text = empty;
                    return text + "," + num + empty2;
                }
                return empty + empty2;
            }
            if (number >= 1000000000)
            {
                empty2 = "Tỉ";
                long num = number % 1000000000 / 100000000;
                number /= 1000000000;
                empty = number + string.Empty;
                if (num > 0)
                {
                    string text = empty;
                    return text + "," + num + empty2;
                }
                return empty + empty2;
            }
            if (number >= 1000000)
            {
                empty2 = "Tr";
                long num2 = number % 1000000 / 100000;
                number /= 1000000;
                empty = number + string.Empty;
                if (num2 > 0)
                {
                    string text = empty;
                    return text + "," + num2 + empty2;
                }
                return empty + empty2;
            }
            if (number >= 1000)
            {
                empty2 = "K";
                long num2 = number % 100000 / 100000;
                number /= 1000;
                empty = number + string.Empty;
                if (num2 > 0)
                {
                    string text = empty;
                    return text + "," + num2 + empty2;
                }
                return empty + empty2;
            }
            return GetMoneys(number);
        }

        public static string NumberToString(long number)
        {
            string text = "";
            string text2 = "";
            if (number >= 1000000000L)
            {
                text2 = "Tỉ";
                long num = number % 1000000000L / 10000000L;
                number /= 1000000000L;
                text = number + "";
                if (num >= 10L)
                {
                    if (num % 10L == 0L)
                    {
                        num /= 10L;
                    }
                    text = text + "," + num + text2;

                }
                else if (num > 0L)
                {
                    text = text + ",0" + num + text2;
                }
                else
                {
                    text += text2;
                }
            }
            else if (number >= 1000000L)
            {
                text2 = "Tr";
                long num2 = number % 1000000L / 10000L;
                number /= 1000000L;
                text = number + "";
                if (num2 >= 10L)
                {
                    if (num2 % 10L == 0L)
                    {
                        num2 /= 10L;
                    }
                    text = text + "," + num2 + text2;
                }
                else if (num2 > 0L)
                {
                    text = text + ",0" + num2 + text2;
                }
                else
                {
                    text += text2;
                }
            }
            else if (number >= 10000L)
            {
                text2 = "K";
                long num3 = number % 1000L / 10L;
                number /= 1000L;
                text = number + "";
                if (num3 >= 10L)
                {
                    if (num3 % 10L == 0L)
                    {
                        num3 /= 10L;
                    }
                    text = text + "," + num3 + text2;
                }
                else if (num3 > 0L)
                {
                    text = text + ",0" + num3 + text2;
                }
                else
                {
                    text += text2;
                }
            }
            else if (number >= 1000L)
            {
                text2 = "K";
                long num3 = number % 1000L / 10L;
                number /= 1000L;
                text = number + "";
                if (num3 >= 10L)
                {
                    if (num3 % 10L == 0L)
                    {
                        num3 /= 10L;
                    }
                    text = text + "," + num3 + text2;
                }
                else if (num3 > 0L)
                {
                    text = text + ",0" + num3 + text2;
                }
                else
                {
                    text += text2;
                }
            }
            else
            {
                text = number + "";
            }
            return text;
        }

        public static int random(int start, int end)
        {
            return r.Next(start, end + 1);
        }

        public static sbyte[] readByteArray(MyReader dos)
        {
            //Discarded unreachable code: IL_001e
            try
            {
                int num = dos.ReadInt();
                sbyte[] data = new sbyte[num];
                dos.Read(ref data);
                return data;
            }
            catch (Exception)
            {
                Debug.Log("LOI DOC readByteArray dos");
            }
            return null;
        }

        public static sbyte[] readByteArray(Message msg)
        {
            //Discarded unreachable code: IL_0028
            try
            {
                int num = msg.reader().ReadInt();
                sbyte[] data = new sbyte[num];
                msg.reader().Read(ref data);
                return data;
            }
            catch (Exception)
            {
                Debug.Log("LOI DOC readByteArray NINJAUTIL");
            }
            return null;
        }

        public static int atan(int a)
        {
            for (int i = 0; i <= 90; i++)
            {
                if (tanz[i] >= a)
                {
                    return i;
                }
            }
            return 0;
        }

        public static int Angle(int dx, int dy)
        {
            int num;
            if (dx != 0)
            {
                int a = Math.Abs((dy << 10) / dx);
                num = atan(a);
                if (dy >= 0 && dx < 0)
                {
                    num = 180 - num;
                }
                if (dy < 0 && dx < 0)
                {
                    num = 180 + num;
                }
                if (dy < 0 && dx >= 0)
                {
                    num = 360 - num;
                }
            }
            else
            {
                num = ((dy <= 0) ? 270 : 90);
            }
            return num;
        }

        public static int sin(int a)
        {
            a = fixangle(a);
            if (a >= 0 && a < 90)
            {
                return sinz[a];
            }
            if (a >= 90 && a < 180)
            {
                return sinz[180 - a];
            }
            if (a >= 180 && a < 270)
            {
                return -sinz[a - 180];
            }
            return -sinz[360 - a];
        }

        public static int cos(int a)
        {
            a = fixangle(a);
            if (a >= 0 && a < 90)
            {
                return cosz[a];
            }
            if (a >= 90 && a < 180)
            {
                return -cosz[180 - a];
            }
            if (a >= 180 && a < 270)
            {
                return -cosz[a - 180];
            }
            return cosz[360 - a];
        }

        public static int fixangle(int angle)
        {
            if (angle >= 360)
            {
                angle -= 360;
            }
            if (angle < 0)
            {
                angle += 360;
            }
            return angle;
        }

        public static string GetMoneys(long m)
        {
            string text = string.Empty;
            long num = m / 1000 + 1;
            for (int i = 0; i < num; i++)
            {
                if (m >= 1000)
                {
                    long num2 = m % 1000;
                    text = ((num2 != 0L) ? ((num2 >= 10) ? ((num2 >= 100) ? ("." + num2 + text) : (".0" + num2 + text)) : (".00" + num2 + text)) : (".000" + text));
                    m /= 1000;
                    continue;
                }
                text = m + text;
                break;
            }
            return text;
        }

        public static sbyte[] Cast(byte[] data)
        {
            sbyte[] array = new sbyte[data.Length];
            for (int i = 0; i < array.Length; i++)
            {
                array[i] = (sbyte)data[i];
            }
            return array;
        }

        public static byte[] Cast(sbyte[] data)
        {
            byte[] array = new byte[data.Length];
            for (int i = 0; i < array.Length; i++)
            {
                array[i] = (byte)data[i];
            }
            return array;
        }

        public static void Run(Action action)
        {
            Thread thread = new Thread(() =>
            {
                try
                {
                    action();
                }
                catch
                {
                }
            });
            thread.IsBackground = true;
            thread.Start();
        }

    }
}
