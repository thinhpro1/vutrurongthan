using Assets.Scripts.Effects;
using Assets.Scripts.Entites.Players;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.Items;
using Assets.Scripts.Models;
using Assets.Scripts.Screens;

namespace Assets.Scripts.Entites.ItemMaps
{
    public class ItemMap : Entity
    {
        public ItemTemplate template;

        public int xEnd;

        public int yEnd;

        public int vx;

        public int vy;

        public static Image imgLight;

        public int playerId;

        static ItemMap()
        {
            imgLight = GameCanvas.LoadImage("imgLight.png");
        }

        public ItemMap()
        {
            status = ItemMapStatus.NONE;
        }

        public override void Paint(MyGraphics g)
        {
            if (GameCanvas.gameTick % 20 < 10)
            {
                g.DrawImage(imgLight, x, y - h / 2 - 20, StaticObj.VCENTER_HCENTER);
            }

            GraphicManager.instance.Draw(g, template.iconId, x, y - 20, 0, StaticObj.BOTTOM_HCENTER);

            if (Player.me.itemFocus != null && Player.me.itemFocus.Equals(this))
            {
                g.DrawImage(GameScreen.imgSelect, x, y - h - 35, 3);
            }
            foreach (Effect effect in effects)
            {
                effect.Paint(g);
            }
        }

        public void SetPoint(int xEnd, int yEnd)
        {
            this.xEnd = xEnd;
            this.yEnd = yEnd;
            vx = xEnd - x >> 2;
            vy = yEnd - y >> 2;
            status = ItemMapStatus.REMOVE;
        }

        public override void Update()
        {
            base.UpdateEffect();
            if (status == ItemMapStatus.REMOVE && x == xEnd && y == yEnd)
            {
                ScreenManager.instance.gameScreen.itemMaps.Remove(this);
                if (Player.me.itemFocus != null && Player.me.itemFocus.Equals(this))
                {
                    Player.me.itemFocus = null;
                }
                return;
            }
            if (vx == 0)
            {
                x = xEnd;
            }
            if (vy == 0)
            {
                y = yEnd;
            }
            if (x != xEnd)
            {
                x += vx;
                if ((vx > 0 && x > xEnd) || (vx < 0 && x < xEnd))
                {
                    x = xEnd;
                }
            }
            if (y != yEnd)
            {
                y += vy;
                if ((vy > 0 && y > yEnd) || (vy < 0 && y < yEnd))
                {
                    y = yEnd;
                }
            }
            if (status != ItemMapStatus.REMOVE && !Map.IsWall(x, y))
            {
                y += 8;
                if (y % 8 != 0)
                {
                    y -= y % 8;
                }
                yEnd = y;
            }
        }

        public void GetWidthAndHeight()
        {
            GraphicManager.instance.CreateImage(template.iconId);
            try
            {
                h = GraphicManager.instance.images[template.iconId].GetHeight();
                w = GraphicManager.instance.images[template.iconId].GetWidth();
            }
            catch
            {
                h = 50;
                w = 50;
            }
        }
    }
}
