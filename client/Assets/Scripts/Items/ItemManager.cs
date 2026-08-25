using Assets.Scripts.IOs;
using System;
using System.Collections.Generic;
using System.Linq;
using UnityEngine;

namespace Assets.Scripts.Items
{
    public class ItemManager
    {
        public static ItemManager instance = new ItemManager();

        public Dictionary<int, ItemTemplate> itemTemplates;

        public Dictionary<int, ItemOptionTemplate> itemOptionTemplates;

        public int versionItemTemplate;

        public int versionItemOptionTemplate;

        public ItemManager()
        {
            itemTemplates = new Dictionary<int, ItemTemplate>();
            itemOptionTemplates = new Dictionary<int, ItemOptionTemplate>();
            versionItemTemplate = -1;
            versionItemOptionTemplate = -1;
        }

        public void Init()
        {
            LoadItemTemplate();
            LoadItemOptionTemplate();
        }

        private void LoadItemTemplate()
        {
            try
            {
                MyReader reader = new MyReader(Rms.Load("item_template"));
                versionItemTemplate = reader.ReadSbyte();
                int count = reader.ReadShort();
                for (int i = 0; i < count; i++)
                {
                    ItemTemplate template = new ItemTemplate();
                    template.id = reader.ReadShort();
                    template.name = reader.ReadUTF();
                    template.description = reader.ReadUTF();
                    template.gender = reader.ReadSbyte();
                    template.type = reader.ReadSbyte();
                    template.iconId = reader.ReadShort();
                    template.isUp = reader.ReadBool();
                    template.levelRequire = reader.ReadShort();
                    template.isMaster = reader.ReadBool();
                    template.isDisciple = reader.ReadBool();
                    template.isPet = reader.ReadBool();
                    itemTemplates.Add(template.id, template);
                }
            }
            catch
            {
                versionItemTemplate = -1;
                itemTemplates.Clear();
            }
        }

        private void LoadItemOptionTemplate()
        {
            try
            {
                MyReader reader = new MyReader(Rms.Load("item_option_template"));
                versionItemOptionTemplate = reader.ReadSbyte();
                int count = reader.ReadShort();
                for (int i = 0; i < count; i++)
                {
                    ItemOptionTemplate template = new ItemOptionTemplate();
                    template.id = reader.ReadShort();
                    template.name = reader.ReadUTF();
                    template.type = reader.ReadSbyte();
                    itemOptionTemplates.Add(template.id, template);
                }
            }
            catch
            {
                versionItemOptionTemplate = -1;
                itemOptionTemplates.Clear();
            }
        }


        public void SaveItemTemplate()
        {
            MyWriter writer = new MyWriter();
            writer.WriteSByte(versionItemTemplate);
            writer.WriteShort(itemTemplates.Count);
            for (int i = 0; i < itemTemplates.Count; i++)
            {
                ItemTemplate template = itemTemplates.ElementAt(i).Value;
                writer.WriteShort(template.id);
                writer.WriteUTF(template.name);
                writer.WriteUTF(template.description);
                writer.WriteSByte(template.gender);
                writer.WriteSByte(template.type);
                writer.WriteShort(template.iconId);
                writer.WriteBool(template.isUp);
                writer.WriteShort(template.levelRequire);
                writer.WriteBool(template.isMaster);
                writer.WriteBool(template.isDisciple);
                writer.WriteBool(template.isPet);
            }
            Rms.Save("item_template", writer.GetData());
        }

        public void SaveItemOptionTemplate()
        {
            MyWriter writer = new MyWriter();
            writer.WriteSByte(versionItemOptionTemplate);
            writer.WriteShort(itemOptionTemplates.Count);
            for (int i = 0; i < itemOptionTemplates.Count; i++)
            {
                ItemOptionTemplate template = itemOptionTemplates.ElementAt(i).Value;
                writer.WriteShort(template.id);
                writer.WriteUTF(template.name);
                writer.WriteSByte(template.type);
            }
            Rms.Save("item_option_template", writer.GetData());
        }


    }
}
