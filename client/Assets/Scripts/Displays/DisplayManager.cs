using Assets.Scripts.Actions;
using Assets.Scripts.Commands;
using Assets.Scripts.Commons;
using Assets.Scripts.Dialogs;
using Assets.Scripts.Services;

namespace Assets.Scripts.Displays
{
    public class DisplayManager : IActionListener
    {
        public static DisplayManager instance = new DisplayManager();

        public Dialog dialog;

        public DisplayManager()
        {
            dialog = new Dialog();
        }

        public void Perform(int actionId, object p)
        {
            switch (actionId)
            {
                case 1:
                    InfoDlg.Hide();
                    dialog.Close();
                    break;

                case 2:
                    InfoDlg.Hide();
                    dialog.Close();
                    Service.instance.ConfirmMenu(9, (int)p);
                    break;
            }
        }

        public void StartDialogYesNo(string info, string yes, string no)
        {
            dialog.SetInfo(info, new Command(yes, this, 2, 0), null, new Command(no, this, 2, 1));
        }

        public void StartDialogYesNo(string info, Command yes, Command no)
        {
            dialog.SetInfo(info, yes, null, no);
        }

        public void StartDialogOk(string info)
        {
            dialog.SetInfo(info, null, new Command(PlayerText.OK, this, 1), null);
        }

        public void StartDialogOk(string info, Command command)
        {
            dialog.SetInfo(info, null, command, null);
        }


    }
}
