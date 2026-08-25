using System;
using System.Text;

namespace Assets.Scripts.IOs
{
    public class MyReader
    {
        private sbyte[] buffer;

        private int posRead;

        private int posMark;

        public MyReader()
        {
        }

        public MyReader(sbyte[] data)
        {
            buffer = data;
        }

        public sbyte ReadSbyte()
        {
            if (posRead < buffer.Length)
            {
                return buffer[posRead++];
            }
            posRead = buffer.Length;
            throw new Exception(" loi doc sbyte eof ");
        }

        public void Mark(int readlimit)
        {
            posMark = posRead;
        }

        public void Reset()
        {
            posRead = posMark;
        }

        public byte ReadUnsignedByte()
        {
            return ConvertSbyteToByte(ReadSbyte());
        }

        public short ReadShort()
        {
            short num = 0;
            for (int i = 0; i < 2; i++)
            {
                num = (short)(num << 8);
                num = (short)(num | (short)(0xFF & buffer[posRead++]));
            }
            return num;
        }

        public ushort ReadUnsignedShort()
        {
            ushort num = 0;
            for (int i = 0; i < 2; i++)
            {
                num = (ushort)(num << 8);
                num = (ushort)(num | (ushort)(0xFFu & (uint)buffer[posRead++]));
            }
            return num;
        }

        public int ReadInt()
        {
            int num = 0;
            for (int i = 0; i < 4; i++)
            {
                num <<= 8;
                num |= 0xFF & buffer[posRead++];
            }
            return num;
        }

        public long ReadLong()
        {
            long num = 0L;
            for (int i = 0; i < 8; i++)
            {
                num <<= 8;
                num |= 0xFF & buffer[posRead++];
            }
            return num;
        }

        public bool ReadBoolean()
        {
            return ReadSbyte() > 0;
        }

        public bool ReadBool()
        {
            return ReadBoolean();
        }

        private string ReadString()
        {
            short num = ReadShort();
            byte[] array = new byte[num];
            for (int i = 0; i < num; i++)
            {
                array[i] = ConvertSbyteToByte(ReadSbyte());
            }
            UTF8Encoding uTF8Encoding = new UTF8Encoding();
            return uTF8Encoding.GetString(array);
        }

        private string ReadStringUTF()
        {
            short num = ReadShort();
            byte[] array = new byte[num];
            for (int i = 0; i < num; i++)
            {
                array[i] = ConvertSbyteToByte(ReadSbyte());
            }
            UTF8Encoding uTF8Encoding = new UTF8Encoding();
            return uTF8Encoding.GetString(array);
        }

        public string ReadUTF()
        {
            return ReadStringUTF();
        }

        private int Read()
        {
            if (posRead < buffer.Length)
            {
                return ReadSbyte();
            }
            return -1;
        }

        public int Read(ref sbyte[] data)
        {
            if (data == null)
            {
                return 0;
            }
            int num = 0;
            for (int i = 0; i < data.Length; i++)
            {
                data[i] = ReadSbyte();
                if (posRead > buffer.Length)
                {
                    return -1;
                }
                num++;
            }
            return num;
        }

        public void ReadFully(ref sbyte[] data)
        {
            if (data != null && data.Length + posRead <= buffer.Length)
            {
                for (int i = 0; i < data.Length; i++)
                {
                    data[i] = ReadSbyte();
                }
            }
        }

        public int Available()
        {
            return buffer.Length - posRead;
        }

        public static byte ConvertSbyteToByte(sbyte var)
        {
            if (var > 0)
            {
                return (byte)var;
            }
            return (byte)(var + 256);
        }

        public static byte[] ConvertSbyteToByte(sbyte[] var)
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

        public void Close()
        {
            buffer = null;
        }

        public void Read(ref sbyte[] data, int arg1, int arg2)
        {
            if (data == null)
            {
                return;
            }
            for (int i = 0; i < arg2; i++)
            {
                data[i + arg1] = ReadSbyte();
                if (posRead > buffer.Length)
                {
                    break;
                }
            }
        }
    }
}
