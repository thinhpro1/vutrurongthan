namespace Assets.Scripts.IOs
{
    public class Message
    {
        public sbyte id;

        private MyReader dis;

        private MyWriter dos;

        public Message(int command)
        {
            this.id = (sbyte)command;
            dos = new MyWriter();
        }

        public Message()
        {
            dos = new MyWriter();
        }

        public Message(sbyte command)
        {
            this.id = command;
            dos = new MyWriter();
        }

        public Message(sbyte command, sbyte[] data)
        {
            this.id = command;
            dis = new MyReader(data);
        }

        public sbyte[] GetData()
        {
            return dos.GetData();
        }

        public MyReader reader()
        {
            return dis;
        }

        public int ReadInt()
        {
            return reader().ReadInt();
        }

        public short ReadShort()
        {
            return reader().ReadShort();
        }

        public sbyte ReadSByte()
        {
            return reader().ReadSbyte();
        }

        public long ReadLong()
        {
            return reader().ReadLong();
        }

        public string ReadUTF()
        {
            return reader().ReadUTF();
        }

        public bool ReadBool()
        {
            return reader().ReadBoolean();
        }


        public MyWriter writer()
        {
            return dos;
        }

        public int readInt3Byte()
        {
            return dis.ReadInt();
        }

        public void cleanup()
        {
        }
    }
}
