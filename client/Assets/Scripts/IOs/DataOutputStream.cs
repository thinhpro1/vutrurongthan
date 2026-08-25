namespace Assets.Scripts.IOs
{
    public class DataOutputStream
    {
        private MyWriter w = new MyWriter();

        public void writeShort(short i)
        {
            w.WriteShort(i);
        }

        public void writeInt(int i)
        {
            w.WriteInt(i);
        }

        public void write(sbyte[] data)
        {
            w.writeSByte(data);
        }

        public sbyte[] toByteArray()
        {
            return w.GetData();
        }

        public void close()
        {
            w.close();
        }

        public void writeByte(sbyte b)
        {
            w.writeByte(b);
        }

        public void writeUTF(string name)
        {
            w.WriteUTF(name);
        }

        public void writeBoolean(bool b)
        {
            w.writeBoolean(b);
        }
    }
}
