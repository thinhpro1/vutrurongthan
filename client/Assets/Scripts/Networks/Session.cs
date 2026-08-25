using Assets.Scripts.Commons;
using Assets.Scripts.Controllers;
using System.Collections.Generic;
using System.IO;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System;
using Assets.Scripts.IOs;
using UnityEngine;
using Assets.Scripts.Services;
using Assets.Scripts.GraphicCustoms;
using System.Collections.Concurrent;

namespace Assets.Scripts.Networks
{
    public class Session
    {
        private NetworkStream dataStream;

        private BinaryReader dis;

        private BinaryWriter dos;

        public TcpClient socket;

        public bool isConnected;

        public bool isConnecting;

        public Thread initThread;

        public Thread collectorThread;

        public Thread collectorThread2;

        public Thread senderThread;

        private bool isGetKeyComplete;

        public sbyte[] key;

        private sbyte curR;

        private sbyte curW;

        private string host;

        private int port;

        public List<Message> recieveMessages;

        //public List<Message> sendMessages;

        private readonly BlockingCollection<Message> sendMessages = new BlockingCollection<Message>(new ConcurrentQueue<Message>());

        public List<Message> recieveMessages2;

        public List<int> iconRequest = new List<int>();

        public long timeConnect;

        public Controller controller;

        public Session(Controller controller)
        {
            this.controller = controller;
            recieveMessages = new List<Message>();
            //sendMessages = new List<Message>();
            recieveMessages2 = new List<Message>();
            timeConnect = Utils.CurrentTimeMillis();
        }

        private void NetworkInit()
        {
            isConnecting = true;
            Thread.CurrentThread.Priority = System.Threading.ThreadPriority.Highest;
            isConnected = true;
            try
            {
                socket = new TcpClient();
                socket.NoDelay = true;
                socket.Connect(host, port);
                dataStream = socket.GetStream();
                dis = new BinaryReader(dataStream, new UTF8Encoding());
                dos = new BinaryWriter(dataStream, new UTF8Encoding());
                senderThread = new Thread(() =>
                {
                    SenderMessage();
                });
                senderThread.IsBackground = true;
                senderThread.Start();
                collectorThread = new Thread(() =>
                {
                    CollectorMessage();
                });
                collectorThread.IsBackground = true;
                collectorThread.Start();
                collectorThread2 = new Thread(() =>
                {
                    CollectorMessage2();
                });
                collectorThread2.IsBackground = true;
                collectorThread2.Start();
                isConnecting = false;
                DoSendMessage(new Message(MessageName.CONNECT_SERVER));
            }
            catch
            {
                Close();
            }
        }

        public void Connect(string host, int port)
        {
            if (!isConnected && !isConnecting)
            {
                timeConnect = Utils.CurrentTimeMillis();
                this.host = host;
                this.port = port;
                isGetKeyComplete = false;
                socket = null;
                initThread = new Thread(NetworkInit);
                initThread.Start();
            }
        }

        public void SendMessage(Message message)
        {
            sendMessages.Add(message);
        }

        private void DoSendMessage(Message m)
        {
            try
            {
                sbyte[] data = m.GetData();
                if (isGetKeyComplete)
                {
                    sbyte value = WriteKey(m.id);
                    dos.Write(value);
                }
                else
                {
                    dos.Write(m.id);
                }
                if (data != null)
                {
                    int num = data.Length;
                    if (isGetKeyComplete)
                    {
                        int num2 = WriteKey((sbyte)(num >> 8));
                        dos.Write((sbyte)num2);
                        int num3 = WriteKey((sbyte)(num & 0xFF));
                        dos.Write((sbyte)num3);
                    }
                    else
                    {
                        dos.Write((ushort)num);
                    }
                    if (isGetKeyComplete)
                    {
                        for (int i = 0; i < data.Length; i++)
                        {
                            sbyte value2 = WriteKey(data[i]);
                            dos.Write(value2);
                        }
                    }
                }
                else
                {
                    if (isGetKeyComplete)
                    {
                        int num4 = 0;
                        int num5 = WriteKey((sbyte)(num4 >> 8));
                        dos.Write((sbyte)num5);
                        int num6 = WriteKey((sbyte)(num4 & 0xFF));
                        dos.Write((sbyte)num6);
                    }
                    else
                    {
                        dos.Write((ushort)0);
                    }
                }
                dos.Flush();
            }
            catch
            {
            }
        }

        public sbyte ReadKey(sbyte b)
        {
            sbyte result = (sbyte)((key[curR++] & 0xFF) ^ (b & 0xFF));
            if (curR >= key.Length)
            {
                curR = (sbyte)(curR % (sbyte)key.Length);
            }
            return result;
        }

        public sbyte WriteKey(sbyte b)
        {
            sbyte result = (sbyte)((key[curW++] & 0xFF) ^ (b & 0xFF));
            if (curW >= key.Length)
            {
                curW = (sbyte)(curW % (sbyte)key.Length);
            }
            return result;
        }

        public void Update()
        {
            while (recieveMessages.Count > 0)
            {
                Message message = recieveMessages[0];
                if (Controller.isStopReadMessage)
                {
                    break;
                }
                if (message != null)
                {
                    controller.OnMessage(message);
                }
                recieveMessages.RemoveAt(0);
            }
        }

        public void Close()
        {
            CleanNetwork();
            recieveMessages.Clear();
            //sendMessages.Clear();
            iconRequest.Clear();
        }

        public bool IsConnected()
        {
            return isConnected;
        }

        private void CleanNetwork()
        {
            key = null;
            curR = 0;
            curW = 0;
            try
            {
                isConnected = false;
                isConnecting = false;
                if (socket != null)
                {
                    socket.Close();
                    socket = null;
                }
                if (dataStream != null)
                {
                    dataStream.Close();
                    dataStream = null;
                }
                if (dos != null)
                {
                    dos.Close();
                    dos = null;
                }
                if (dis != null)
                {
                    dis.Close();
                    dis = null;
                }
                senderThread = null;
                collectorThread = null;
                collectorThread2 = null;
            }
            catch
            {
            }
        }

        public void SenderMessage()
        {
            while (isConnected)
            {
                try
                {
                    if (isGetKeyComplete)
                    {
                        Message message = sendMessages.Take();
                        DoSendMessage(message);
                        /*while (sendMessages.Count > 0)
                        {
                            Message m = sendMessages[0];
                            DoSendMessage(m);
                            sendMessages.RemoveAt(0);
                        }*/
                    }
                }
                catch
                {
                }
            }
        }

        public void CollectorMessage()
        {
            try
            {
                while (isConnected)
                {
                    Message message = readMessage();
                    if (message == null)
                    {
                        break;
                    }
                    try
                    {
                        if (message.id == -128)
                        {
                            GetKey(message);
                        }
                        else if (message.id == MessageName.UPDATE_DATA || message.id == -22)
                        {
                            recieveMessages2.Add(message);
                        }
                        else
                        {
                            recieveMessages.Add(message);
                        }
                    }
                    catch (Exception)
                    {
                    }
                    Thread.Sleep(1);
                }
            }
            catch
            {
            }
            if (!isConnected)
            {
                return;
            }
            if (socket != null)
            {
                CleanNetwork();
            }
        }

        public void CollectorMessage2()
        {
            try
            {
                while (isConnected)
                {
                    try
                    {
                        while (recieveMessages2.Count > 0)
                        {
                            Message m = recieveMessages2[0];
                            controller.OnMessage(m);
                            recieveMessages2.RemoveAt(0);
                        }
                        while (iconRequest.Count > 0)
                        {
                            int id = iconRequest[0];
                            sbyte[] array = null;
                            try
                            {
                                string hex = Rms.LoadString("icon_" + GraphicManager.instance.versionImage + "_" + id);
                                string key = GraphicManager.instance.versionImage + "" + GraphicManager.instance.versionImage;
                                array = Utils.Cast(Convert.FromBase64String(GraphicManager.instance.Decrypt(GraphicManager.instance.HexToString(hex), key)));
                            }
                            catch
                            {
                            }
                            if (array != null)
                            {
                                GraphicManager.instance.datas.Add(id, array);
                            }
                            else
                            {
                                Service.instance.RequestIcon(id);
                            }
                            iconRequest.RemoveAt(0);
                        }
                        Thread.Sleep(1);
                    }
                    catch(Exception e) {
                        Debug.LogException(e);
                    }
                }
            }
            catch(Exception e)
            {
                Debug.LogException(e);
            }
        }

        private void GetKey(Message message)
        {
            try
            {
                sbyte b = message.reader().ReadSbyte();
                key = new sbyte[b];
                for (int i = 0; i < b; i++)
                {
                    key[i] = message.reader().ReadSbyte();
                }
                for (int j = 0; j < key.Length - 1; j++)
                {
                    ref sbyte reference = ref key[j + 1];
                    reference = (sbyte)(reference ^ key[j]);
                }
                isGetKeyComplete = true;
            }
            catch (Exception e)
            {
                Debug.LogException(e);
            }
        }

        private Message readMessage2(sbyte cmd)
        {
            int num = ReadKey(dis.ReadSByte()) + 128;
            int num2 = ReadKey(dis.ReadSByte()) + 128;
            int num3 = ReadKey(dis.ReadSByte()) + 128;
            int num4 = (num3 * 256 + num2) * 256 + num;
            sbyte[] array = new sbyte[num4];
            byte[] src = dis.ReadBytes(num4);
            Buffer.BlockCopy(src, 0, array, 0, num4);
            if (isGetKeyComplete)
            {
                for (int i = 0; i < array.Length; i++)
                {
                    array[i] = ReadKey(array[i]);
                }
            }
            return new Message(cmd, array);
        }

        private Message readMessage()
        {
            try
            {
                sbyte b = dis.ReadSByte();
                if (isGetKeyComplete)
                {
                    b = ReadKey(b);
                }
                if (b == -127 || b == -22 || b == -125)
                {
                    return readMessage2(b);
                }
                int num;
                if (isGetKeyComplete)
                {
                    sbyte b2 = dis.ReadSByte();
                    sbyte b3 = dis.ReadSByte();
                    num = ((ReadKey(b2) & 0xFF) << 8) | (ReadKey(b3) & 0xFF);
                }
                else
                {
                    sbyte b4 = dis.ReadSByte();
                    sbyte b5 = dis.ReadSByte();
                    num = (b4 & 0xFF00) | (b5 & 0xFF);
                }
                sbyte[] array = new sbyte[num];
                byte[] src = dis.ReadBytes(num);
                Buffer.BlockCopy(src, 0, array, 0, num);
                if (isGetKeyComplete)
                {
                    for (int i = 0; i < array.Length; i++)
                    {
                        array[i] = ReadKey(array[i]);
                    }
                }
                return new Message(b, array);
            }
            catch
            {
            }
            return null;
        }
    }
}
