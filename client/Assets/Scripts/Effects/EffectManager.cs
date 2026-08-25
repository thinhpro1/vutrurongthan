using Assets.Scripts.Entites.Npcs;
using Assets.Scripts.IOs;
using Assets.Scripts.Models;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using UnityEngine;

namespace Assets.Scripts.Effects
{
    public class EffectManager
    {
        public static EffectManager instance = new EffectManager();

        public Dictionary<int, EffectImage> effectImages;

        public Dictionary<int, EffectTimeTemplate> effectTemplates;

        public int versionEffect;

        public EffectManager()
        {
            effectImages = new Dictionary<int, EffectImage>();
            effectTemplates = new Dictionary<int, EffectTimeTemplate>();
            versionEffect = -1;
        }

        public void Init()
        {
            LoadEffect();
        }

        private void LoadEffect()
        {
            try
            {
                MyReader reader = new MyReader(Rms.Load("effect"));
                versionEffect = reader.ReadSbyte();
                int count = reader.ReadShort();
                for (int i = 0; i < count; i++)
                {
                    EffectImage effect = new EffectImage();
                    effect.id = reader.ReadShort();
                    effect.dx = reader.ReadShort();
                    effect.dy = reader.ReadShort();
                    effect.delay = reader.ReadShort();
                    effect.icons = new List<int>();
                    int count_icon = reader.ReadSbyte();
                    for (int j = 0; j < count_icon; j++)
                    {
                        int iconId = reader.ReadShort();
                        effect.icons.Add(iconId);
                    }
                    effectImages.Add(effect.id, effect);
                }
                count = reader.ReadShort();
                for (int i = 0; i < count; i++)
                {
                    EffectTimeTemplate template = new EffectTimeTemplate();
                    template.id = reader.ReadShort();
                    int effectImageId = reader.ReadShort();
                    if (effectImageId != -1)
                    {
                        template.effectImage = effectImages[effectImageId];
                    }
                    template.iconId = reader.ReadShort();
                    template.isClearWhenDie = reader.ReadBool();
                    template.isStun = reader.ReadBool();
                    effectTemplates.Add(template.id, template);
                }
            }
            catch (Exception ex)
            {
                versionEffect = -1;
                effectImages.Clear();
            }
        }

        public void SaveEffect()
        {
            MyWriter writer = new MyWriter();
            writer.WriteSByte(versionEffect);
            writer.WriteShort(effectImages.Count);
            for (int i = 0; i < effectImages.Count; i++)
            {
                EffectImage effect = effectImages.ElementAt(i).Value;
                writer.WriteShort(effect.id);
                writer.WriteShort(effect.dx);
                writer.WriteShort(effect.dy);
                writer.WriteShort((int)(short)effect.delay);
                writer.WriteSByte(effect.icons.Count);
                foreach (int icon in effect.icons)
                {
                    writer.WriteShort(icon);
                }
            }
            writer.WriteShort(effectTemplates.Count);
            for (int i = 0; i < effectTemplates.Count; i++)
            {
                EffectTimeTemplate template = effectTemplates.ElementAt(i).Value;
                writer.WriteShort(template.id);
                if (template.effectImage != null)
                {
                    writer.WriteShort(template.effectImage.id);
                }
                else
                {
                    writer.WriteShort(-1);
                }
                writer.WriteShort(template.iconId);
                writer.WriteBool(template.isClearWhenDie);
                writer.WriteBool(template.isStun);
            }
            Rms.Save("effect", writer.GetData());
        }

    }
}
