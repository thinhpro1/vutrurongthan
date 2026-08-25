using Assets.Scripts.IOs;
using Assets.Scripts.Items;
using System.Collections.Generic;
using System.Linq;

namespace Assets.Scripts.Entites.Npcs
{
    public class NpcManager
    {
        public static NpcManager instance = new NpcManager();

        public Dictionary<int, NpcTemplate> npcTemplates;

        public int versionNpcTemplate;

        public NpcManager()
        {
            npcTemplates = new Dictionary<int, NpcTemplate>();
            versionNpcTemplate = 1;
        }

        public void Init()
        {
            LoadNpcTemplate();
        }

        private void LoadNpcTemplate()
        {
            try
            {
                MyReader reader = new MyReader(Rms.Load("npc_template"));
                versionNpcTemplate = reader.ReadSbyte();
                int count = reader.ReadShort();
                for (int i = 0; i < count; i++)
                {
                    NpcTemplate template = new NpcTemplate();
                    template.id = reader.ReadSbyte();
                    template.name = reader.ReadUTF();
                    int count_img = reader.ReadSbyte();
                    for (int j = 0; j < count_img; j++)
                    {
                        template.icons.Add(reader.ReadShort());
                    }
                    template.avatar = reader.ReadShort();
                    template.dx = reader.ReadSbyte();
                    template.dy = reader.ReadSbyte();
                    template.w = reader.ReadShort();
                    template.h = reader.ReadShort();
                    npcTemplates.Add(template.id, template);
                }
            }
            catch
            {
                versionNpcTemplate = -1;
                npcTemplates.Clear();
            }
        }

        public void SaveNpcTemplate()
        {
            MyWriter writer = new MyWriter();
            writer.WriteSByte(versionNpcTemplate);
            writer.WriteShort(npcTemplates.Count);
            for (int i = 0; i < npcTemplates.Count; i++)
            {
                NpcTemplate template = npcTemplates.ElementAt(i).Value;
                writer.WriteSByte(template.id);
                writer.WriteUTF(template.name);
                writer.WriteSByte(template.icons.Count);
                foreach (int icon in template.icons)
                {
                    writer.WriteShort(icon);
                }
                writer.WriteShort(template.avatar);
                writer.WriteSByte(template.dx);
                writer.WriteSByte(template.dy);
                writer.WriteShort(template.w);
                writer.WriteShort(template.h);
            }
            Rms.Save("npc_template", writer.GetData());
        }

    }
}