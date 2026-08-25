using Assets.Scripts.Items;
using System.Collections.Generic;

namespace Assets.Scripts.Tasks
{
    public class Task
    {
        public int id;
        public string name;
        public int index;
        public int param;
        public List<TaskSub> subTasks = new List<TaskSub>();
        public List<Item> items = new List<Item>();
    }
}
