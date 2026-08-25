using Assets.Scripts.Actions;
using Assets.Scripts.Commands;
using Assets.Scripts.Commons;
using Assets.Scripts.Displays;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.InputCustoms;
using Assets.Scripts.IOs;
using Assets.Scripts.Models;
using Assets.Scripts.Networks;
using Assets.Scripts.Services;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using UnityEngine;

namespace Assets.Scripts.Screens
{
    public class RegisterScreen : MyScreen, IActionListener
    {
        public TextField txtUsername;

        public TextField txtPassword; 

        public int w;

        public int h;

        public int x;

        public int y;

        private Image imgPopupRegister;

        private Command cmdRegister;

        private Command cmdCancel;

        public RegisterScreen(ScreenManager screenManager) : base(screenManager)
        {
            imgPopupRegister = GameCanvas.LoadImage("MainImages/RegisterScreens/img_bgr_reg");
            int width = 400; 

            txtUsername = new TextField("MainImages/Logins/input_login");
            txtUsername.name = PlayerText.username;
            txtUsername.x = ScreenManager.instance.w / 2 - txtUsername.w / 2;
            txtUsername.type = TextField.TYPE_USERNAME;

            txtPassword = new TextField("MainImages/Logins/input_login");
            txtPassword.name = PlayerText.password;
            txtPassword.x = txtUsername.x;
            txtPassword.type = TextField.TYPE_PASSWORD;

            cmdRegister = new Command(PlayerText.register, this, 1, null);
            cmdRegister.x = ScreenManager.instance.w / 2 - cmdRegister.w - 10;
            cmdRegister.image = GameCanvas.LoadImage("MainImages/Logins/btn_login");
            cmdRegister.imageClick = GameCanvas.LoadImage("MainImages/Logins/btn_login_click");
            cmdRegister.imageFocus = GameCanvas.LoadImage("MainImages/Logins/btn_login_focus");

            cmdCancel = new Command("Hủy", this, 2, null);
            cmdCancel.image = GameCanvas.LoadImage("MainImages/Logins/btn_register");
            cmdCancel.imageClick = GameCanvas.LoadImage("MainImages/Logins/btn_register_click");
            cmdCancel.imageFocus = GameCanvas.LoadImage("MainImages/Logins/btn_register_focus");
            cmdCancel.x = ScreenManager.instance.w / 2 + 10;

            w = width + 120;
            h = 370;
            x = ScreenManager.instance.w / 2 - w / 2;
            y = ScreenManager.instance.h / 2 - h / 2 + 40;
             
            txtUsername.y = y + txtUsername.h + 25;
            txtPassword.y = txtUsername.y + txtUsername.h + 30;

            cmdRegister.y = y + h - 10;
            cmdCancel.y = cmdRegister.y;

            /*cmdSelectServer = new Command("MainImages/Logins/btn_select_server", "MainImages/Logins/btn_select_server_focus", "MainImages/Logins/btn_select_server_click", "Chọn", this, 3, null);
            cmdSelectServer.w = 120;
            cmdSelectServer.x = imgNameServer.GetWidth() + 25;
            cmdSelectServer.y = 20;*/
        }

        public override void Paint(MyGraphics g)
        {
            ScreenManager.instance.PaintBackground(g);
            if (!DisplayManager.instance.dialog.isShow && !GameCanvas.menu.isShow)
            {
                g.DrawImage(ScreenManager.instance.imgLogo, ScreenManager.instance.w / 2, y - 30, StaticObj.BOTTOM_HCENTER);
                g.DrawImage(imgPopupRegister, ScreenManager.instance.w / 2, y - 30, StaticObj.TOP_CENTER);
                cmdRegister.Paint(g);
                cmdCancel.Paint(g); 
                txtUsername.Paint(g);
                txtPassword.Paint(g);
            }
        }

        public override void Update()
        {
            txtUsername.Update();
            txtPassword.Update(); 
        }

        public override void SwitchToMe()
        {
            txtUsername.isFocus = false;
            txtPassword.isFocus = false; 
            base.SwitchToMe();
            SoundMn.playSoundBgr();
        }

        public override void PointerClicked(int x, int y)
        {
            if (cmdRegister.PointerClicked(x, y))
            {
                return;
            }
            if (cmdCancel.PointerClicked(x, y))
            {
                return;
            }
            txtUsername.PointerClicked(x, y);
            txtPassword.PointerClicked(x, y); 
        }

        public override void PointerReleased(int x, int y)
        {
            if (cmdRegister.PointerReleased(x, y))
            {
                return;
            }
            if (cmdCancel.PointerReleased(x, y))
            {
                return;
            }
        }

        public override void PointerMove(int x, int y)
        {
            if (cmdRegister.PointerMove(x, y))
            {
                return;
            }
            if (cmdCancel.PointerMove(x, y))
            {
                return;
            }
        }

        public override void KeyPress(KeyCode keyCode)
        {
            if (keyCode == KeyCode.Tab && !DisplayManager.instance.dialog.isShow && !GameCanvas.menu.isShow)
            { 
                if (txtUsername.isFocus)
                { 
                    txtUsername.isFocus = false;
                    txtPassword.isFocus = true;
                }
                else
                { 
                    txtUsername.isFocus = false;
                    txtPassword.isFocus = false;
                }
            }
            if (keyCode == KeyCode.Return && !DisplayManager.instance.dialog.isShow && !GameCanvas.menu.isShow)
            {
                //DoLogin();
            } 
            if (txtUsername.isFocus)
            {
                txtUsername.KeyPress(keyCode);
            }
            else if (txtPassword.isFocus)
            {
                txtPassword.KeyPress(keyCode);
            }
        }

        public void Perform(int actionId, object p)
        {
            switch (actionId)
            {
                case 1:
                    DoRegister();
                    break;

                case 2:
                    ScreenManager.instance.loginScreen.SwitchToMe();
                    break;
            }
        }

        public void DoRegister()
        { 
            if (txtUsername.GetText().Equals(string.Empty))
            {
                GameCanvas.StartDialogOk(PlayerText.userBlank);
                return;
            }
            if (txtPassword.GetText().Equals(string.Empty))
            {
                GameCanvas.StartDialogOk(PlayerText.passwordBlank);
                return;
            }
            ServerManager.instance.Connect();
            Service.instance.Register( txtUsername.GetText(), txtPassword.GetText());
            GameCanvas.startWaitDlg();
        }
    }
}
