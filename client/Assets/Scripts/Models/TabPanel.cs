using Assets.Scripts.Commands;
using Assets.Scripts.Dialogs;
using Assets.Scripts.GraphicCustoms;
using System;

namespace Assets.Scripts.Models
{
    public class TabPanel : Command
    {
        public string name;

        public int type;

        public static TabPanel tabInventory;

        public static TabPanel tabSkill;

        public static TabPanel tabTask;

        public static TabPanel tabTaskOrther;

        public static TabPanel tabArea;

        public static TabPanel tabUpgrade;

        public static TabPanel tabShop;

        public static TabPanel tabTeam;

        public static TabPanel tabFriend;

        public static TabPanel tabEnemy;

        public static TabPanel tabClanMember;

        public static TabPanel tabClanInfo;

        public static TabPanel tabChatGlobal;

        public static TabPanel tabChatPlayer;

        public static TabPanel tabChatClan;

        public static TabPanel tabChatTeam;

        public static TabPanel tabMapSpaceship;

        public static TabPanel tabTrade;

        public static TabPanel tabBox;

        public static TabPanel tabTop;

        public static TabPanel tabSetting;

        public static TabPanel tabAchievement;

        public static TabPanel tabNotification;

        public static TabPanel tabDisciple;

        public static TabPanel tabViewPlayer;

        public static TabPanel tabFlag;

        public static TabPanel tabChatServer;

        public static TabPanel tabMiniGame;

        public static TabPanel tabReward;

        public static TabPanel tabGift;

        public static TabPanel tabPlayerInMap;

        public static TabPanel tabSettingFocus;

        public static TabPanel tabPet;

        public static TabPanel tabIntrinsic;

        public static TabPanel tabPickMe;

        public static TabPanel tabLucky;

        public TabPanel()
        {

        }

        public TabPanel(string name, int type)
        {
            this.name = name;
            this.type = type;
        }

        static TabPanel()
        {
            tabInventory = new TabPanel("Trang bị", 0);
            tabSkill = new TabPanel("Kỹ năng", 1);
            tabUpgrade = new TabPanel("Cường hóa", 2);
            tabShop = new TabPanel("Cửa hàng", 3);
            tabTask = new TabPanel("Chính tuyến", 4);
            tabTaskOrther = new TabPanel("Phụ tuyến", 5);
            tabArea = new TabPanel("Khu vực", 6);
            tabFriend = new TabPanel("Bạn bè", 7);
            tabEnemy = new TabPanel("Kẻ thù", 8);
            tabTop = new TabPanel("Bảng XH", 9);
            tabMapSpaceship = new TabPanel("Điểm đến", 10);
            tabAchievement = new TabPanel("Thành tựu", 11);
            tabNotification = new TabPanel("Thông báo", 12);
            tabTeam = new TabPanel("Tổ đội", 13);
            tabChatGlobal = new TabPanel("Thế giới", 14);
            tabChatClan = new TabPanel("Bang hội", 15);
            tabChatTeam = new TabPanel("Tổ đội", 16);
            tabChatPlayer = new TabPanel("Tin nhắn", 17);
            tabTrade = new TabPanel("Giao dịch", 18);
            tabClanInfo = new TabPanel("Thông tin", 19);
            tabClanMember = new TabPanel("Thành viên", 20);
            tabViewPlayer = new TabPanel("Thông tin", 21);
            tabSetting = new TabPanel("Cài đặt", 22);
            tabFlag = new TabPanel("PK", 23);
            tabChatServer = new TabPanel("Thông báo", 24);
            tabMiniGame = new TabPanel("Quản trò", 25);
            tabDisciple = new TabPanel("Đệ tử", 26);
            tabReward = new TabPanel("Quà tặng", 27);
            tabGift = new TabPanel("Nhiệm vụ", 28);
            tabPlayerInMap = new TabPanel("Danh sách", 29);
            tabSettingFocus = new TabPanel("Mục tiêu", 30);
            tabPet = new TabPanel("Thú nuôi", 31);
            tabIntrinsic = new TabPanel("Bản năng", 32);
            tabPickMe = new TabPanel("Chọn ai đây", 33);
            tabLucky = new TabPanel("Ô may mắn", 34);
        }

        public void Paint(MyGraphics g, bool isSelect)
        {
            if (isClick)
            {
                g.DrawImage(imageClick, x, y);
            }
            else
            {
                g.DrawImage(!isFocus ? image : imageFocus, x, y);
            }
            if (isSelect)
            {
                MyFont.text_white.DrawString(g, caption, x + w / 2, y + (h - MyFont.text_white.GetHeight()) / 2 + 4, 2);
            }
            else
            {
                MyFont.text_blue.DrawString(g, caption, x + w / 2, y + (h - MyFont.text_blue.GetHeight()) / 2 + 2, 2);
            }
        }
    }
}
