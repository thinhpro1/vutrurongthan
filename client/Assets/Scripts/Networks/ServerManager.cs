using Assets.Scripts.Commons;
using Assets.Scripts.Controllers;
using Assets.Scripts.Dialogs;
using Assets.Scripts.Displays;
using Assets.Scripts.IOs;
using Assets.Scripts.Screens;
using System;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.Networking;

namespace Assets.Scripts.Networks
{
    public class ServerManager
    {
        public static ServerManager instance;

        public static string VERSION = "0.9.5";

        public static string LINKWEB = "http://rongthanmax.online";

        public static string baseIP = "14.225.213.112";

        public static int LOGIN_VERSION = 1;

        public const int UPDATE_START = 0;

        public const int UPDATE_LOAD_RMS_COMPLETED = 1;

        public const int UPDATE_SERVER_COMPLETED = 2;

        public const int UPDATE_ALL_COMPLETED = 3;

        public ScreenManager screenManager;

        public Session session;

        public Controller controller;

        public List<Server> servers;

        public UnityWebRequest request;

        public int indexServer;

        public long timeGetData;

        public int statusUpdate;

        public long timeConnect;

        public bool isConnecting;

        public bool isLocal = true;

        public bool[] isUpdateCompleted;

        public ServerManager(ScreenManager screenManager)
        {
            this.screenManager = screenManager;
            //request = UnityWebRequest.Get("http://client.rongthan2.com/api/admin/get-server");
            request = UnityWebRequest.Get("https://rongthanmax.online/servers.json");
            request.SendWebRequest();
            servers = new List<Server>();
            statusUpdate = UPDATE_START;
            timeGetData = Utils.CurrentTimeMillis();
            isLocal = true;
            if (isLocal)
            {
                servers.Add(new Server()
                {
                    name = "1 Sao",
                    ip = "127.0.0.1",
                    port = 1707
                });
                statusUpdate = UPDATE_SERVER_COMPLETED;
                indexServer = 0;
            }
            isUpdateCompleted = new bool[12];
            controller = new Controller(screenManager);
            session = new Session(controller);
        }
        public static class JsonHelper
        {
            public static T[] FromJson<T>(string json)
            {
                string newJson = "{ \"array\": " + json + "}";
                Wrapper<T> wrapper = JsonUtility.FromJson<Wrapper<T>>(newJson);
                return wrapper.array;
            }

            [Serializable]
            private class Wrapper<T>
            {
                public T[] array;
            }
        }
        [Serializable]
        public class ServerInfo
        {
            public string name;
            public string ip;
            public int port;
        }

        public void Update()
        {
            if (isConnecting)
            {
                long now = Utils.CurrentTimeMillis();
                if (now - timeConnect > 5000)
                {
                    isConnecting = false;
                    if (screenManager.currentScreen is ServerListScreen)
                    {
                        if (!session.IsConnected())
                        {
                            string text = "Không thể kết nối đến máy chủ " + servers[indexServer].name + "\nVui lòng thử lại sau hoặc chọn máy chủ khác";
                            DisplayManager.instance.StartDialogOk(text);
                        }
                    }
                    else if (screenManager.currentScreen is SplashScreen)
                    {
                        InfoDlg.Hide();
                        if (screenManager.serverListScreen == null)
                        {
                            screenManager.serverListScreen = new ServerListScreen(screenManager);
                        }
                        screenManager.serverListScreen.SwitchToMe();
                    }
                }
            }
            if (statusUpdate == UPDATE_ALL_COMPLETED)
            {
                return;
            }
            if (statusUpdate == UPDATE_SERVER_COMPLETED)
            {
                statusUpdate = UPDATE_ALL_COMPLETED;
                if (indexServer != -1)
                {
                    Connect();
                }
                else
                {
                    if (screenManager.serverListScreen == null)
                    {
                        screenManager.serverListScreen = new ServerListScreen(screenManager);
                    }
                    screenManager.serverListScreen.SwitchToMe();
                }
                return;
            }
            if (statusUpdate == UPDATE_LOAD_RMS_COMPLETED)
            {
                long now = Utils.CurrentTimeMillis();
                if (now - timeGetData < 10000)
                {
                    if (request != null && request.isDone)
                    {
                        try
                        {
                            if (request.result == UnityWebRequest.Result.Success)
                            {
                                servers.Clear();
                                string json = request.downloadHandler.text;

                                ServerInfo[] list = JsonHelper.FromJson<ServerInfo>(json);

                                foreach (var s in list)
                                {
                                    servers.Add(new Server()
                                    {
                                        name = s.name,
                                        ip = s.ip,
                                        port = s.port
                                    });
                                }
                            }

                        }
                        catch
                        {
                            servers.Clear();
                            servers.Add(new Server()
                            {
                                name = "1 sao",
                                ip = baseIP,
                                port = 1707
                            });
                        }
                        finally
                        {
                            request = null;
                            statusUpdate = UPDATE_SERVER_COMPLETED;
                        }
                    }
                }
                else
                {
                    statusUpdate = UPDATE_SERVER_COMPLETED;
                }
            }
        }
        //public void Update()
        //{
        //    if (isConnecting)
        //    {
        //        long now = Utils.CurrentTimeMillis();
        //        if (now - timeConnect > 5000)
        //        {
        //            isConnecting = false;
        //            if (screenManager.currentScreen is ServerListScreen)
        //            {
        //                if (!session.IsConnected())
        //                {
        //                    string text = "Không thể kết nối đến máy chủ " + servers[indexServer].name + "\nVui lòng thử lại sau hoặc chọn máy chủ khác";
        //                    DisplayManager.instance.StartDialogOk(text);
        //                }
        //            }
        //            else if (screenManager.currentScreen is SplashScreen)
        //            {
        //                InfoDlg.Hide();
        //                if (screenManager.serverListScreen == null)
        //                {
        //                    screenManager.serverListScreen = new ServerListScreen(screenManager);
        //                }
        //                screenManager.serverListScreen.SwitchToMe();
        //            }
        //        }
        //    }
        //    if (statusUpdate == UPDATE_ALL_COMPLETED)
        //    {
        //        return;
        //    }
        //    if (statusUpdate == UPDATE_SERVER_COMPLETED)
        //    {
        //        statusUpdate = UPDATE_ALL_COMPLETED;
        //        if (indexServer != -1)
        //        {
        //            Connect();
        //        }
        //        else
        //        {
        //            if (screenManager.serverListScreen == null)
        //            {
        //                screenManager.serverListScreen = new ServerListScreen(screenManager);
        //            }
        //            screenManager.serverListScreen.SwitchToMe();
        //        }
        //        return;
        //    }
        //    if (statusUpdate == UPDATE_LOAD_RMS_COMPLETED)
        //    {
        //        long now = Utils.CurrentTimeMillis();
        //        if (now - timeGetData < 10000)
        //        {
        //            if (request != null && request.isDone)
        //            {
        //                try
        //                {
        //                    if (request.result == UnityWebRequest.Result.Success)
        //                    {
        //                        servers.Clear();
        //                        byte[] bytes = request.downloadHandler.data;
        //                        MyReader reader = new MyReader(Utils.Cast(bytes));
        //                        int size = reader.ReadSbyte();
        //                        for (int i = 0; i < size; i++)
        //                        {
        //                            servers.Add(new Server()
        //                            {
        //                                name = reader.ReadUTF(),
        //                                ip = reader.ReadUTF(),
        //                                port = reader.ReadShort()
        //                            });
        //                        }
        //                    }
        //                }
        //                catch
        //                {
        //                    servers.Clear();
        //                    servers.Add(new Server()
        //                    {
        //                        name = "1 sao",
        //                        ip = baseIP,
        //                        port = 1707
        //                    });
        //                }
        //                finally
        //                {
        //                    request = null;
        //                    statusUpdate = UPDATE_SERVER_COMPLETED;
        //                }
        //            }
        //        }
        //        else
        //        {
        //            statusUpdate = UPDATE_SERVER_COMPLETED;
        //        }
        //    }
        //}

        public void Init()
        {
            if (isLocal)
            {
                return;
            }
            try
            {
                indexServer = Rms.LoadInt("index_server");
            }
            finally
            {
                statusUpdate = UPDATE_LOAD_RMS_COMPLETED;
            }
        }

        public void Connect()
        {
            if (indexServer < 0)
            {
                indexServer = 0;
            }
            else if (indexServer >= servers.Count)
            {
                indexServer = servers.Count - 1;
            }
            if (!session.IsConnected())
            {
                timeConnect = Utils.CurrentTimeMillis();
                isConnecting = true;
                InfoDlg.ShowWait();
                Server server = servers[indexServer];
                session.Connect(server.ip, server.port);
                //session.Connect("127.0.0.1", 1707);
            }
        }

        public bool IsUpdateCompleted()
        {
            for (int i = 0; i < isUpdateCompleted.Length; i++)
            {
                if (!isUpdateCompleted[i])
                {
                    return false;
                }
            }
            return true;
        }
    }
}