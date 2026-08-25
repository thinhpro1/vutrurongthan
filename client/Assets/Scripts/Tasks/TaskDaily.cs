namespace Assets.Scripts.Tasks
{
    public class TaskDaily
    {
        public string name;

        public int param;

        public int maxParam;

        public string description;

        public override string ToString()
        {
            return name + " [" + param + "/" + maxParam + "]";
        }
    }
}
