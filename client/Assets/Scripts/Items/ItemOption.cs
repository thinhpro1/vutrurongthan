using Assets.Scripts.Commons;
using Assets.Scripts.Games;

namespace Assets.Scripts.Items
{
    public class ItemOption
    {
        public int param;

        public ItemOptionTemplate template;

        public string GetStrOption()
        {
            string name;
            if (template.id == 176)
            {
                name = template.name.Replace("#", "<color=green>" + ItemManager.instance.itemTemplates[param].name + "</color>");
            }
            else
            {
                name = template.name.Replace("#", "<color=green>" + Utils.GetMoneys(param) + "</color>");
            }

            if (name.Contains("</color>%"))
            {
                name = name.Replace("</color>%", "%</color>");
            }
            if (name.Contains("+<color=green>"))
            {
                name = name.Replace("+<color=green>", "<color=green>+");
            }

            return name;
        }

        public ItemOption()
        {

        }

        public ItemOption(int id, int param)
        {
            this.param = param;
            this.template = ItemManager.instance.itemOptionTemplates[id];
        }
    }
}
