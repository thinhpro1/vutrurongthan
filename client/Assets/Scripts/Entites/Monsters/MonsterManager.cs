using Assets.Scripts.Commons;
using Assets.Scripts.IOs;
using Assets.Scripts.Libs.Jsons;
using Assets.Scripts.Models;
using Assets.Scripts.Networks;
using Assets.Scripts.Skills;
using System;
using System.Collections.Generic;
using System.Linq;
using UnityEngine;

namespace Assets.Scripts.Entites.Monsters
{
    public class MonsterManager
    {
        public static MonsterManager instance = new MonsterManager();

        public Dictionary<int, MonsterTemplate> monsterTemplates;

        public Dictionary<int, MonsterDartTemplate> monsterDartTemplates;

        public List<MonsterDart> darts;

        public int versionMonster;

        public MonsterManager()
        {
            monsterTemplates = new Dictionary<int, MonsterTemplate>();
            monsterDartTemplates = new Dictionary<int, MonsterDartTemplate>();
            darts = new List<MonsterDart>();
            versionMonster = -1;
        }

        public void Init()
        {
            try
            {
                MyReader reader = new MyReader(Rms.Load("monster"));
                versionMonster = reader.ReadSbyte();
                int count = reader.ReadShort();
                for (int i = 0; i < count; i++)
                {
                    MonsterDartTemplate template = new MonsterDartTemplate();
                    template.id = reader.ReadShort();
                    template.isMeteorite = reader.ReadBool();
                    template.light = new DartTemplateInfo();
                    int count_img = reader.ReadSbyte();
                    for (int j = 0; j < count_img; j++)
                    {
                        template.light.icon.Add(reader.ReadShort());
                    }
                    template.light.dx = reader.ReadShort();
                    template.light.dy = reader.ReadShort();
                    template.light.delay = reader.ReadShort();
                    template.bullet = new DartTemplateInfo();
                    count_img = reader.ReadSbyte();
                    for (int j = 0; j < count_img; j++)
                    {
                        template.bullet.icon.Add(reader.ReadShort());
                    }
                    template.bullet.dx = reader.ReadShort();
                    template.bullet.dy = reader.ReadShort();
                    template.bullet.delay = reader.ReadShort();
                    template.explode = new DartTemplateInfo();
                    count_img = reader.ReadSbyte();
                    for (int j = 0; j < count_img; j++)
                    {
                        template.explode.icon.Add(reader.ReadShort());
                    }
                    template.explode.dx = reader.ReadShort();
                    template.explode.dy = reader.ReadShort();
                    template.explode.delay = reader.ReadShort();
                    monsterDartTemplates.Add(template.id, template);
                }
                count = reader.ReadShort();
                for (int i = 0; i < count; i++)
                {
                    MonsterTemplate template = new MonsterTemplate();
                    template.id = reader.ReadShort();
                    template.name = reader.ReadUTF();
                    template.rangeMove = reader.ReadShort();
                    template.speed = reader.ReadSbyte();
                    template.type = reader.ReadSbyte();
                    template.dart = monsterDartTemplates[reader.ReadSbyte()];
                    int count_img = reader.ReadSbyte();
                    for (int j = 0; j < count_img; j++)
                    {
                        template.iconsMove.Add(reader.ReadShort());
                    }
                    template.iconInjure = reader.ReadShort();
                    template.iconAttack = reader.ReadShort();
                    template.w = reader.ReadShort();
                    template.h = reader.ReadShort();
                    template.dx = reader.ReadSbyte();
                    template.dy = reader.ReadSbyte();
                    monsterTemplates.Add(template.id, template);
                }
            }
            catch (Exception ex)
            {
                Debug.LogError(ex);
                versionMonster = -1;
                monsterTemplates.Clear();
                monsterDartTemplates.Clear();
            }
        }

        public void SaveMonsterTemplate()
        {
            MyWriter writer = new MyWriter();
            writer.WriteSByte(versionMonster);
            writer.WriteShort(monsterDartTemplates.Count);
            for (int i = 0; i < monsterDartTemplates.Count; i++)
            {
                MonsterDartTemplate template = monsterDartTemplates.ElementAt(i).Value;
                writer.WriteShort(template.id);
                writer.WriteBool(template.isMeteorite);
                writer.WriteSByte(template.light.icon.Count);
                foreach (int icon in template.light.icon)
                {
                    writer.WriteShort(icon);
                }
                writer.WriteShort(template.light.dx);
                writer.WriteShort(template.light.dy);
                writer.WriteShort(template.light.delay);
                writer.WriteSByte(template.bullet.icon.Count);
                foreach (int icon in template.bullet.icon)
                {
                    writer.WriteShort(icon);
                }
                writer.WriteShort(template.bullet.dx);
                writer.WriteShort(template.bullet.dy);
                writer.WriteShort(template.bullet.delay);
                writer.WriteSByte(template.explode.icon.Count);
                foreach (int icon in template.explode.icon)
                {
                    writer.WriteShort(icon);
                }
                writer.WriteShort(template.explode.dx);
                writer.WriteShort(template.explode.dy);
                writer.WriteShort(template.explode.delay);
            }
            writer.WriteShort(monsterTemplates.Count);
            for (int i = 0; i < monsterTemplates.Count; i++)
            {
                MonsterTemplate template = monsterTemplates.ElementAt(i).Value;
                writer.WriteShort(template.id);
                writer.WriteUTF(template.name);
                writer.WriteShort(template.rangeMove);
                writer.WriteSByte(template.speed);
                writer.WriteSByte(template.type);
                writer.WriteSByte(template.dart.id);
                writer.WriteSByte(template.iconsMove.Count);
                foreach (int icon in template.iconsMove)
                {
                    writer.WriteShort(icon);
                }
                writer.WriteShort(template.iconInjure);
                writer.WriteShort(template.iconAttack);
                writer.WriteShort(template.w);
                writer.WriteShort(template.h);
                writer.WriteSByte(template.dx);
                writer.WriteSByte(template.dy);
            }
            Rms.Save("monster", writer.GetData());
        }

    }
}
