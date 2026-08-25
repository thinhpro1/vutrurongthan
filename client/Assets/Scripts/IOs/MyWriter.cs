using System.Text;
using System.IO;
using UnityEngine;

namespace Assets.Scripts.IOs
{
    public class MyWriter
    {
        public sbyte[] buffer = new sbyte[2048];

        private int posWrite;

        private int length = 2048;

        public void writeSByte(sbyte value)
        {
            CheckLength(1);
            buffer[posWrite++] = value;
        }

        public void WriteSByte(int value)
        {
            CheckLength(1);
            buffer[posWrite++] = (sbyte)value;
        }

        public void WriteSByteUncheck(sbyte value)
        {
            buffer[posWrite++] = value;
        }

        public void writeByte(sbyte value)
        {
            writeSByte(value);
        }

        public void writeByte(int value)
        {
            writeSByte((sbyte)value);
        }

        public void writeChar(char value)
        {
            WriteSByte(0);
            writeSByte((sbyte)value);
        }

        public void writeUnsignedByte(byte value)
        {
            writeSByte((sbyte)value);
        }

        public void writeUnsignedByte(byte[] value)
        {
            CheckLength(value.Length);
            for (int i = 0; i < value.Length; i++)
            {
                WriteSByteUncheck((sbyte)value[i]);
            }
        }

        public void writeSByte(sbyte[] value)
        {
            CheckLength(value.Length);
            for (int i = 0; i < value.Length; i++)
            {
                WriteSByteUncheck(value[i]);
            }
        }

        public void WriteShort(short value)
        {
            CheckLength(2);
            for (int num = 1; num >= 0; num--)
            {
                WriteSByteUncheck((sbyte)(value >> num * 8));
            }
        }

        public void WriteShort(int value)
        {
            CheckLength(2);
            short num = (short)value;
            for (int num2 = 1; num2 >= 0; num2--)
            {
                WriteSByteUncheck((sbyte)(num >> num2 * 8));
            }
        }

        public void writeUnsignedShort(ushort value)
        {
            CheckLength(2);
            for (int num = 1; num >= 0; num--)
            {
                WriteSByteUncheck((sbyte)(value >> num * 8));
            }
        }

        public void WriteInt(int value)
        {
            CheckLength(4);
            for (int num = 3; num >= 0; num--)
            {
                WriteSByteUncheck((sbyte)(value >> num * 8));
            }
        }

        public void WriteLong(long value)
        {
            CheckLength(8);
            for (int num = 7; num >= 0; num--)
            {
                WriteSByteUncheck((sbyte)(value >> num * 8));
            }
        }

        public void writeBoolean(bool value)
        {
            writeSByte((sbyte)(value ? 1 : 0));
        }

        public void WriteBool(bool value)
        {
            writeSByte((sbyte)(value ? 1 : 0));
        }

        private void writeString(string value)
        {
            char[] array = value.ToCharArray();
            WriteShort((short)array.Length);
            CheckLength(array.Length);
            for (int i = 0; i < array.Length; i++)
            {
                WriteSByteUncheck((sbyte)array[i]);
            }
        }

        public void WriteUTF(string value)
        {
            Encoding unicode = Encoding.Unicode;
            Encoding encoding = Encoding.GetEncoding(65001);
            byte[] bytes = unicode.GetBytes(value);
            byte[] array = Encoding.Convert(unicode, encoding, bytes);
            WriteShort((short)array.Length);
            CheckLength(array.Length);
            for (int i = 0; i < array.Length; i++)
            {
                sbyte value2 = (sbyte)array[i];
                WriteSByteUncheck(value2);
            }
        }

        public void write(sbyte value)
        {
            writeSByte(value);
        }

        public void write(ref sbyte[] data, int arg1, int arg2)
        {
            if (data == null)
            {
                return;
            }
            for (int i = 0; i < arg2; i++)
            {
                writeSByte(data[i + arg1]);
                if (posWrite > buffer.Length)
                {
                    break;
                }
            }
        }

        public void write(sbyte[] value)
        {
            writeSByte(value);
        }

        public sbyte[] GetData()
        {
            if (posWrite <= 0)
            {
                return null;
            }
            sbyte[] array = new sbyte[posWrite];
            for (int i = 0; i < posWrite; i++)
            {
                array[i] = buffer[i];
            }
            return array;
        }

        public void CheckLength(int ltemp)
        {
            if (posWrite + ltemp > length)
            {
                sbyte[] array = new sbyte[length + 1024 + ltemp];
                for (int i = 0; i < length; i++)
                {
                    array[i] = buffer[i];
                }
                buffer = null;
                buffer = array;
                length += 1024 + ltemp;
            }
        }

        private static void convertString(string[] args)
        {
            string path = args[0];
            string path2 = args[1];
            using StreamReader input = new StreamReader(path, Encoding.Unicode);
            using StreamWriter output = new StreamWriter(path2, append: false, Encoding.UTF8);
            CopyContents(input, output);
        }

        private static void CopyContents(TextReader input, TextWriter output)
        {
            char[] array = new char[8192];
            int count;
            while ((count = input.Read(array, 0, array.Length)) != 0)
            {
                output.Write(array, 0, count);
            }
            output.Flush();
            string message = output.ToString();
            Debug.Log(message);
        }

        public byte convertSbyteToByte(sbyte var)
        {
            if (var > 0)
            {
                return (byte)var;
            }
            return (byte)(var + 256);
        }

        public byte[] convertSbyteToByte(sbyte[] var)
        {
            byte[] array = new byte[var.Length];
            for (int i = 0; i < var.Length; i++)
            {
                if (var[i] > 0)
                {
                    array[i] = (byte)var[i];
                }
                else
                {
                    array[i] = (byte)(var[i] + 256);
                }
            }
            return array;
        }

        public void close()
        {
            buffer = null;
        }
    }
}
