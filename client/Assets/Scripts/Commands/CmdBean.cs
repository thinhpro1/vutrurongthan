using Assets.Scripts.Actions;
using Assets.Scripts.Commons;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;

namespace Assets.Scripts.Commands
{
    public class CmdBean : Command
    {
        public Image imgCooldown;

        public long lastTimeUseBean;

        public CmdBean(IActionListener actionListener, int action, object p)
        {
            this.imageFocus = this.image = GameCanvas.LoadImage("MainImages/GameScrs/img_pean");
            this.imageClick = GameCanvas.LoadImage("MainImages/GameScrs/img_pean_click");
            imgCooldown = GameCanvas.LoadImage("MainImages/GameScrs/img_pean_cool_down");
            actionId = action;
            this.actionListener = actionListener;
            this._object = p;
            w = image.GetWidth();
            h = image.GetHeight();
            isShow = true;
            anchor = StaticObj.TOP_LEFT;
        }

        public override void Paint(MyGraphics g)
        {
            if (isClick)
            {
                g.DrawImage(imageClick, x, y);
            }
            else
            {
                g.DrawImage(!isFocus ? image : imageFocus, x, y);
            }
            long num = 10000 - (Utils.CurrentTimeMillis() - lastTimeUseBean);
            if (num > 0)
            {
                g.DrawImage(imgCooldown, x + w / 2, y + h / 2, StaticObj.VCENTER_HCENTER);
                if (num > 1000L)
                {
                    MyFont.text_mini_yellow.DrawString(g, Utils.GetMoneys(num).Substring(0, 3), x + w / 2 + 2, y + h / 2 - 10, 2, MyFont.text_mini_grey);
                    return;
                }
                MyFont.text_mini_yellow.DrawString(g, "0." + num.ToString().Substring(0, 2), x + w / 2 + 2, y + h / 2 - 10, 2, MyFont.text_mini_grey);
            }
            else
            {
                GameScreen.hpPoint = GameScreen.GetHpPoint();
                MyFont.text_mini_yellow.DrawString(g, GameScreen.hpPoint.ToString(), x + w / 2 + 2, y + h / 2, 2, MyFont.text_mini_grey);
            }
        }
    }
}
