using Assets.Scripts.Commons;
using System;
using System.Threading;
using UnityEngine;

namespace Assets.Scripts.IOs
{
    public class DataInputStream
    {
        public MyReader r;

        private const int INTERVAL = 5;

        private const int MAXTIME = 500;

        public static DataInputStream istemp;

        private static int status;

        private static string filenametemp;

        public DataInputStream(string filename)
        {
            TextAsset textAsset = (TextAsset)Resources.Load(filename, typeof(TextAsset));
            r = new MyReader(Utils.Cast(textAsset.bytes));
        }

        public DataInputStream(sbyte[] data)
        {
            r = new MyReader(data);
        }

        public static void update()
        {
            if (status == 2)
            {
                status = 1;
                istemp = __getResourceAsStream(filenametemp);
                status = 0;
            }
        }

        public static DataInputStream getResourceAsStream(string filename)
        {
            return __getResourceAsStream(filename);
        }

        private static DataInputStream _getResourceAsStream(string filename)
        {
            if (status != 0)
            {
                for (int i = 0; i < 500; i++)
                {
                    Thread.Sleep(5);
                    if (status == 0)
                    {
                        break;
                    }
                }
                if (status != 0)
                {
                    Debug.LogError("CANNOT GET INPUTSTREAM " + filename + " WHEN GETTING " + filenametemp);
                    return null;
                }
            }
            istemp = null;
            filenametemp = filename;
            status = 2;
            int j;
            for (j = 0; j < 500; j++)
            {
                Thread.Sleep(5);
                if (status == 0)
                {
                    break;
                }
            }
            if (j == 500)
            {
                Debug.LogError("TOO LONG FOR CREATE INPUTSTREAM " + filename);
                status = 0;
                return null;
            }
            return istemp;
        }

        private static DataInputStream __getResourceAsStream(string filename)
        {
            try
            {
                return new DataInputStream(filename);
            }
            catch (Exception)
            {
                return null;
            }
        }

        public short readShort()
        {
            return r.ReadShort();
        }

        public int readInt()
        {
            return r.ReadInt();
        }

        public int read()
        {
            return r.ReadUnsignedByte();
        }

        public void read(ref sbyte[] data)
        {
            r.Read(ref data);
        }

        public void close()
        {
            r.Close();
        }

        public void Close()
        {
            r.Close();
        }

        public string readUTF()
        {
            return r.ReadUTF();
        }

        public sbyte readByte()
        {
            return r.ReadSbyte();
        }

        public long readLong()
        {
            return r.ReadLong();
        }

        public bool readBoolean()
        {
            return r.ReadBoolean();
        }

        public int readUnsignedByte()
        {
            return (byte)r.ReadSbyte();
        }

        public int readUnsignedShort()
        {
            return r.ReadUnsignedShort();
        }

        public void readFully(ref sbyte[] data)
        {
            r.Read(ref data);
        }

        public int available()
        {
            return r.Available();
        }

        internal void read(ref sbyte[] byteData, int p, int size)
        {
            throw new NotImplementedException();
        }
    }
}
