namespace Assets.Scripts.Commons
{
    public class MessageName
    {
        public const int CONNECT_SERVER = -128;
        public const int VERSION_SOURCE = -127;
        public const int DIALOG_OK = -126;

        /*
            sub id:
            0 - list monster template
            1 - list item template
            2 - list item option template
            3 - list level
            4 - list npc template
         */
        public const int UPDATE_DATA = -125;

        public const int LOGIN = -124;
        public const int START_CREATE_PLAYER_SCREEN = -123;
        public const int CREATE_PALYER = -122;
        public const int MAP_INFO = -121;
        public const int ADD_MONSTER = -120;
        public const int REMOVE_MONSTER = -119;
        public const int INFO_PLAYER = -117;
        public const int FINISH_LOAD_MAP = -115;
        public const int NPC_CHAT = -91;

        public const int ADD_ITEM_MAP = -81;
        public const int REMOVE_ITEM_MAP = -80;

        public const int ADD_NPC = -48;
        public const int REMOVE_NPC = -47;

        public const int UPDATE_CLAN_PART = -32;
        public const int SET_POSITION_PLAYER = -28;
    }
}
