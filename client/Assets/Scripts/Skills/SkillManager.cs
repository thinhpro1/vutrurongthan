using Assets.Scripts.Commons;
using Assets.Scripts.Entites.Monsters;
using Assets.Scripts.Frames;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.IOs;
using Assets.Scripts.Libs.Jsons;
using Assets.Scripts.Networks;
using Assets.Scripts.Sounds;
using System;
using System.Collections.Generic;
using System.Linq;
using UnityEngine;

namespace Assets.Scripts.Skills
{
    public class SkillManager
    {
        public static SkillManager instance = new SkillManager();

        public Dictionary<int, SkillEffectInfo> effects;

        public Dictionary<int, DartTemplate> darts;

        public Dictionary<int, SkillPaint> paints;

        public int versionSkillPaint;

        public SkillManager()
        {
            effects = new Dictionary<int, SkillEffectInfo>();
            darts = new Dictionary<int, DartTemplate>();
            paints = new Dictionary<int, SkillPaint>();
            versionSkillPaint = -1;
        }

        public void CreateImage()
        {
            try
            {
                foreach (SkillEffectInfo skillEffectInfo in effects.Values)
                {
                    foreach (int icon in skillEffectInfo.icons)
                    {
                        GraphicManager.instance.CreateImage(icon);
                    }
                }
                foreach (DartTemplate dartTemplate in darts.Values)
                {
                    if (dartTemplate.bullet != null)
                    {
                        foreach (int icon in dartTemplate.bullet.icon)
                        {
                            GraphicManager.instance.CreateImage(icon);
                        }
                    }
                    if (dartTemplate.explode != null)
                    {
                        foreach (int icon in dartTemplate.explode.icon)
                        {
                            GraphicManager.instance.CreateImage(icon);
                        }
                    }
                }
            }
            catch
            {
            }
        }

        public void Init()
        {
            if (ServerManager.instance.isLocal)
            {
                JsonData dartJsonList = JsonMapper.ToObject(Utils.LoadJson("Dart"));
                foreach (KeyValuePair<string, JsonData> item in dartJsonList)
                {
                    DartTemplate template = new DartTemplate();
                    template.isTarget = (bool)item.Value["is_target"];
                    template.isLine = (bool)item.Value["is_line"];
                    template.bullet = new DartTemplateInfo();
                    template.bullet.dx = (int)item.Value["bullet"]["dx"];
                    template.bullet.dy = (int)item.Value["bullet"]["dy"];
                    template.bullet.delay = (int)item.Value["bullet"]["delay"];
                    template.bullet.icon = Utils.ReadIntList(item.Value["bullet"]["icon"]);
                    template.explode = new DartTemplateInfo();
                    template.explode.dx = (int)item.Value["explode"]["dx"];
                    template.explode.dy = (int)item.Value["explode"]["dy"];
                    template.explode.delay = (int)item.Value["explode"]["delay"];
                    template.explode.icon = Utils.ReadIntList(item.Value["explode"]["icon"]);
                    darts.Add(int.Parse(item.Key), template);
                }
                JsonData effectJsonList = JsonMapper.ToObject(Utils.LoadJson("SkillEffect"));
                foreach (KeyValuePair<string, JsonData> item in effectJsonList)
                {
                    SkillEffectInfo effectInfo = new SkillEffectInfo();
                    effectInfo.icons = Utils.ReadIntList(item.Value["icons"]);
                    effectInfo.dx = (int)item.Value["dx"];
                    effectInfo.dy = (int)item.Value["dy"];
                    effects.Add(int.Parse(item.Key), effectInfo);
                }
                JsonData skillPaintJsonList = JsonMapper.ToObject(Utils.LoadJson("SkillPaint"));
                foreach (KeyValuePair<string, JsonData> item in skillPaintJsonList)
                {
                    SkillPaint skillPaint = new SkillPaint();
                    skillPaint.id = int.Parse(item.Key);
                    skillPaint.dxFly = (int)item.Value["dx_fly"];
                    skillPaint.dyFly = (int)item.Value["dy_fly"];
                    foreach (JsonData info in item.Value["info"])
                    {
                        SkillPaintInfo skillPaintInfo = new SkillPaintInfo();
                        int soundId = (int)info["sound_id"];
                        if (soundId != -1)
                        {
                            skillPaintInfo.sound = SoundManager.instance.sounds[soundId];
                        }
                        int dartId = (int)info["dart_id"];
                        if (dartId != -1)
                        {
                            skillPaintInfo.dart = darts[dartId];
                        }
                        skillPaintInfo.action = Utils.ReadIntList(info["action"]);
                        foreach (JsonData effect in info["effect"])
                        {
                            SkillEffect skillEffect = new SkillEffect();
                            skillEffect.effectInfo = effects[(int)effect["id"]];
                            skillEffect.loop = (int)effect["loop"];
                            skillPaintInfo.effects.Add(skillEffect);
                        }
                        skillPaintInfo.timeOut = (long)info["time_out"];
                        skillPaint.info.Add(skillPaintInfo);
                    }
                    paints.Add(skillPaint.id, skillPaint);
                }
                return;
            }
            try
            {
                MyReader reader = new MyReader(Rms.Load("skill_paint"));
                versionSkillPaint = reader.ReadSbyte();
                int count = reader.ReadShort();
                for (int i = 0; i < count; i++)
                {
                    DartTemplate template = new DartTemplate();
                    template.id = reader.ReadShort();
                    template.isTarget = reader.ReadBool();
                    template.isLine = reader.ReadBool();
                    template.bullet = new DartTemplateInfo();
                    int count_img = reader.ReadSbyte();
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
                    darts.Add(template.id, template);
                }
                count = reader.ReadShort();
                for (int i = 0; i < count; i++)
                {
                    SkillEffectInfo effectInfo = new SkillEffectInfo();
                    effectInfo.id = reader.ReadShort();
                    int count_img = reader.ReadSbyte();
                    for (int j = 0; j < count_img; j++)
                    {
                        effectInfo.icons.Add(reader.ReadShort());
                    }
                    effectInfo.dx = reader.ReadShort();
                    effectInfo.dy = reader.ReadShort();
                    effects.Add(effectInfo.id, effectInfo);
                }
                count = reader.ReadShort();
                for (int i = 0; i < count; i++)
                {
                    SkillPaint skillPaint = new SkillPaint();
                    skillPaint.id = reader.ReadShort();
                    skillPaint.dxFly = reader.ReadShort();
                    skillPaint.dyFly = reader.ReadShort();
                    int count_info = reader.ReadSbyte();
                    for (int j = 0; j < count_info; j++)
                    {
                        SkillPaintInfo skillPaintInfo = new SkillPaintInfo();
                        int soundId = reader.ReadShort();
                        if (soundId != -1)
                        {
                            skillPaintInfo.sound = SoundManager.instance.sounds[soundId];
                        }
                        int dartId = reader.ReadShort();
                        if (dartId != -1)
                        {
                            skillPaintInfo.dart = darts[dartId];
                        }
                        skillPaintInfo.timeOut = reader.ReadShort();
                        int count_action = reader.ReadSbyte();
                        for (int k = 0; k < count_action; k++)
                        {
                            skillPaintInfo.action.Add(reader.ReadShort());
                        };
                        int count_effect = reader.ReadSbyte();
                        for (int k = 0; k < count_effect; k++)
                        {
                            SkillEffect skillEffect = new SkillEffect();
                            skillEffect.effectInfo = effects[(int)reader.ReadShort()];
                            skillEffect.loop = reader.ReadSbyte();
                            skillPaintInfo.effects.Add(skillEffect);
                        }
                        skillPaint.info.Add(skillPaintInfo);
                    }
                    paints.Add(skillPaint.id, skillPaint);
                }
            }
            catch (Exception ex)
            {
                versionSkillPaint = -1;
                darts.Clear();
                effects.Clear();
                paints.Clear();
            }
        }

        public void SavePaint()
        {
            MyWriter writer = new MyWriter();
            writer.WriteSByte(versionSkillPaint);
            writer.WriteShort(darts.Count);
            for (int i = 0; i < darts.Count; i++)
            {
                DartTemplate template = darts.ElementAt(i).Value;
                writer.WriteShort(template.id);
                writer.WriteBool(template.isTarget);
                writer.WriteBool(template.isLine);
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
            writer.WriteShort(effects.Count);
            for (int i = 0; i < effects.Count; i++)
            {
                SkillEffectInfo effectInfo = effects.ElementAt(i).Value;
                writer.WriteShort(effectInfo.id);
                writer.WriteSByte(effectInfo.icons.Count);
                foreach (int icon in effectInfo.icons)
                {
                    writer.WriteShort(icon);
                }
                writer.WriteShort(effectInfo.dx);
                writer.WriteShort(effectInfo.dy);
            }
            writer.WriteShort(paints.Count);
            for (int i = 0; i < paints.Count; i++)
            {
                SkillPaint skillPaint = paints.ElementAt(i).Value;
                writer.WriteShort(skillPaint.id);
                writer.WriteShort(skillPaint.dxFly);
                writer.WriteShort(skillPaint.dyFly);
                writer.WriteSByte(skillPaint.info.Count);
                foreach (SkillPaintInfo info in skillPaint.info)
                {
                    writer.WriteShort(info.sound == null ? -1 : info.sound.id);
                    writer.WriteShort(info.dart == null ? -1 : info.dart.id);
                    writer.WriteShort((int)(short)info.timeOut);
                    writer.WriteSByte(info.action.Count);
                    foreach (int action in info.action)
                    {
                        writer.WriteShort(action);
                    }
                    writer.WriteSByte(info.effects.Count);
                    foreach (SkillEffect effect in info.effects)
                    {
                        writer.WriteShort(effect.effectInfo.id);
                        writer.WriteSByte(effect.loop);
                    }
                }
            }
            Rms.Save("skill_paint", writer.GetData());
        }


    }


}
