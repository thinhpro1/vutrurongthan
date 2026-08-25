using Assets.Scripts.Commons;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Skills;

namespace Assets.Scripts.Commands
{
    public class CmdSkill : Command
    {
        public static Image imgBgr;

        public static Image imgBgrClick;

        public static Image imgCooldown;

        public Skill skill;

        static CmdSkill()
        {
            imgBgr = GameCanvas.LoadImage("MainImages/GameScrs/Skills/img_skill_bgr");
            imgBgrClick = GameCanvas.LoadImage("MainImages/GameScrs/Skills/img_skill_bgr_click");
            imgCooldown = GameCanvas.LoadImage("MainImages/GameScrs/Skills/img_skill_cooldown");
        }

        public CmdSkill()
        {
            isShow = true;
            anchor = StaticObj.VCENTER_HCENTER;
            w = imgBgr.GetWidth();
            h = imgBgr.GetHeight();
        }

        public override void Paint(MyGraphics g)
        {
            //hoang
            if (skill == null)
                return;

            if (isClick)
            {
                g.DrawImage(imgBgrClick, x, y, anchor);
            }
            else
            {
                g.DrawImage(imgBgr, x, y, anchor);
            }

            if (skill != null)
            {
                //skill.Paint(g, x + 17, y + 14);
                GraphicManager.instance.Draw(g, skill.GetIcon(), x, y, 0, StaticObj.VCENTER_HCENTER);
                if (skill.isPaintCanNotUse)
                {
                    g.DrawImage(imgCooldown, x, y + 1, StaticObj.VCENTER_HCENTER);
                    try
                    {
                        long num = skill.timeCanUse - Utils.CurrentTimeMillis();
                        if (num > 10000L)
                        {
                            MyFont.text_yellow.DrawString(g, Utils.GetMoneys(num).Split('.')[0], x, y - 12, 2);
                        }
                        else if (num > 1000L)
                        {
                            MyFont.text_yellow.DrawString(g, Utils.GetMoneys(num).Substring(0, 3), x, y - 12, 2);
                        }
                        else if (num > 0)
                        {
                            MyFont.text_yellow.DrawString(g, "0." + num.ToString().Substring(0, 2), x, y - 12, 2);
                        }
                    }
                    catch
                    {
                    }
                }
                return;
            }
        }
    }
}
