using Assets.Scripts.Entites.Players;
using Assets.Scripts.Games;
using Assets.Scripts.GraphicCustoms;
using Assets.Scripts.InputCustoms;
using Assets.Scripts.IOs;
using Assets.Scripts.Models;
using Assets.Scripts.Networks;
using Assets.Scripts.Screens;
using System.Threading;
using UnityEngine;

public class Main : MonoBehaviour
{
    public static bool isPC;

    public static string mainThreadName;

    private static MyGraphics myGraphic;

    public static Main main;

    public int count;

    public bool isRun;
    int maxW = 1366, maxH = 768;
    public float originalSize = 500;
    public int originalHeight = 768;
    private int lastHeight = 0;
    void Start()
    {
        Time.timeScale = 2f;
        if (Thread.CurrentThread.Name != "Main")
        {
            Thread.CurrentThread.Name = "Main";
        }
        mainThreadName = Thread.CurrentThread.Name;
        Screen.sleepTimeout = SleepTimeout.NeverSleep;
        Screen.orientation = ScreenOrientation.LandscapeLeft;
        Rms.persistentDataPath = Application.persistentDataPath;
        isPC = !(Application.platform == RuntimePlatform.Android || Application.platform == RuntimePlatform.IPhonePlayer);
        if (!isPC)
        {
            Screen.fullScreen = true;
            Screen.SetResolution(720 * Screen.width / Screen.height, 720, false);
        }
        else
        {
            Screen.SetResolution(maxW, maxH, false);
            Camera.main.orthographicSize = originalSize * (Screen.height / (float)originalHeight);
        }
    }
    void Update()
    {

    }

    void OnApplicationQuit()
    {
        Debug.Log("Application Quit");
        if (count >= 10)
        {
            ServerManager.instance.session.Close();
        }
        Application.Quit();
    }

    public void Exit()
    {
        OnApplicationQuit();
    }

    void FixedUpdate()
    {
        Rms.update();
        count++;
        if (count >= 10)
        {
            Init();
            GameCanvas.Update();
            Image.Update();
            ServerManager.instance.Update();
            count = 10;
        }
    }

    public void Init()
    {
        if (isRun)
        {
            return;
        }
        isRun = true;
        if ((Application.platform == RuntimePlatform.Android) || (Application.platform == RuntimePlatform.IPhonePlayer))
        {
            isPC = false;
        }
        else
        {
            isPC = true;
        }
        mainThreadName = "Main";
        Screen.orientation = ScreenOrientation.LandscapeLeft;
        Application.targetFrameRate = 60;
        Application.runInBackground = true;
        base.useGUILayout = false;
        if (isPC)
        {
            Screen.fullScreen = false;
        }
        ScreenManager.instance.Init();
        ServerManager.instance = new ServerManager(ScreenManager.instance);
        GameCanvas.LoadData();
        SoundMn.Load();
        myGraphic = new MyGraphics();
        myGraphic.CreateLineMaterial();
        main = this;
    }

    void OnGUI()
    {
        if (count >= 10)
        {
            CheckInput();
            ServerManager.instance.session.Update();
            if (Event.current.type.Equals(EventType.Repaint))
            {
                GameCanvas.Paint(myGraphic);
                myGraphic.Reset();
            }
        }
    }

    void OnHideUnity(bool isGameShown)
    {
        if (!isGameShown)
        {
            Time.timeScale = 0f;
        }
        else
        {
            Time.timeScale = 1f;
        }
    }

    void SetInit()
    {
        base.enabled = true;
    }

    private void CheckInput()
    {
        if (Input.GetMouseButtonDown(0))
        {
            GameCanvas.PointerClicked((int)Input.mousePosition.x, (int)(((float)Screen.height - Input.mousePosition.y)));
        }
        if (Input.GetMouseButtonUp(0))
        {
            GameCanvas.PointerReleased((int)(Input.mousePosition.x), (int)((float)Screen.height - Input.mousePosition.y));
        }

        GameCanvas.PointerMove((int)Input.mousePosition.x, (int)(((float)Screen.height - Input.mousePosition.y)));

        if (Input.anyKeyDown && Event.current.type == EventType.KeyDown
            && (MyKeyMap.KeyInputs.Contains(Event.current.keyCode) || MyKeyMap.KeyActions.Contains(Event.current.keyCode)))
        {
            KeyCode keyCode = Event.current.keyCode;
            if (Input.GetKey(KeyCode.LeftShift) || Input.GetKey(KeyCode.RightShift))
            {
                switch (keyCode)
                {
                    case KeyCode.BackQuote:
                        keyCode = KeyCode.Tilde;
                        break;
                    case KeyCode.Alpha1:
                        keyCode = KeyCode.Exclaim;
                        break;
                    case KeyCode.Alpha2:
                        keyCode = KeyCode.At;
                        break;
                    case KeyCode.Alpha3:
                        keyCode = KeyCode.Hash;
                        break;
                    case KeyCode.Alpha4:
                        keyCode = KeyCode.Dollar;
                        break;
                    case KeyCode.Alpha5:
                        keyCode = KeyCode.Percent;
                        break;
                    case KeyCode.Alpha6:
                        keyCode = KeyCode.Caret;
                        break;
                    case KeyCode.Alpha7:
                        keyCode = KeyCode.Ampersand;
                        break;
                    case KeyCode.Alpha8:
                        keyCode = KeyCode.Asterisk;
                        break;
                    case KeyCode.Alpha9:
                        keyCode = KeyCode.LeftParen;
                        break;
                    case KeyCode.Alpha0:
                        keyCode = KeyCode.RightParen;
                        break;
                    case KeyCode.Minus:
                        keyCode = KeyCode.Underscore;
                        break;
                    case KeyCode.Equals:
                        keyCode = KeyCode.Plus;
                        break;
                    case KeyCode.Quote:
                        keyCode = KeyCode.DoubleQuote;
                        break;
                    case KeyCode.LeftBracket:
                        keyCode = KeyCode.LeftCurlyBracket;
                        break;
                    case KeyCode.RightBracket:
                        keyCode = KeyCode.RightCurlyBracket;
                        break;
                    case KeyCode.Backslash:
                        keyCode = KeyCode.Pipe;
                        break;
                    case KeyCode.Semicolon:
                        keyCode = KeyCode.Colon;
                        break;
                    case KeyCode.Comma:
                        keyCode = KeyCode.Less;
                        break;
                    case KeyCode.Period:
                        keyCode = KeyCode.Greater;
                        break;
                    case KeyCode.Slash:
                        keyCode = KeyCode.Question;
                        break;
                }
            }
            GameCanvas.KeyPress(keyCode);
        }

        if (Event.current.type == EventType.KeyUp && MyKeyMap.KeyActions.Contains(Event.current.keyCode))
        {
            KeyCode keyCode = Event.current.keyCode;
            switch (keyCode)
            {
                case KeyCode.UpArrow:
                    Player.isMoveUp = false;
                    break;
                case KeyCode.LeftArrow:
                    Player.isMoveLeft = false;
                    break;
                case KeyCode.RightArrow:
                    Player.isMoveRight = false;
                    break;
            }
        }

        GameCanvas.PointerScroll((int)(Input.GetAxis("Mouse ScrollWheel") * 10f));
    }
}