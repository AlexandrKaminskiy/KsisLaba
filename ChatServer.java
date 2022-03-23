package com.company;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatServer {

    /**
     *
     */
    protected final static String CHATMASTER_ID = "Server";

    /**
     * Отдельные строки между любой ручкой и сообщением
     */
    protected final static String SEP = ": ";

    /**
     * Сокет сервера
     */
    protected ServerSocket serverSocket;

    /**
     * Список клиентов, подключенных к серверу
     */
    protected List<ChatHandler> clients;

    /**
     * Состояние отладки, настройте, следует ли запускать в форме отладки
     */
    private static boolean DEBUG = false;

    /**
     * основной метод, только построить ChatServer, никогда не возвращаться
     */
    public static void main(String[] args) throws IOException {
        System.out.println("java.ChatServer 0.1 starting...");
        // Введите -debug во время запуска, он запустится в режиме отладки и будет выведена отладочная информация
        if (args.length == 1 && args[0].equals("-debug")) {
            DEBUG = true;
        }
        // Сервер не прекратит работу после запуска
        ChatServer chatServer = new ChatServer();
        chatServer.runServer();
        // Если завершено, это означает, что произошла исключительная ситуация, и программа остановилась
        System.out.println("**Error* java.ChatServer 0.1 quitting");
    }

    /**
     * Построить и запустить службу чата
     * @throws IOException
     */
    public ChatServer() throws IOException {
        clients = new ArrayList<>();
        serverSocket = new ServerSocket(ChatProtocol.PORT_NUM);
        System.out.println("Chat Server Listening on port " + ChatProtocol.PORT_NUM);
    }

    /**
     * Запустите сервер
     */
    public void runServer() {
        try {
            // Бесконечный цикл продолжает получать все доступные сокеты
            while (true) {
                // Начать мониторинг
                Socket userSocket = serverSocket.accept();
                // Введите имя хоста клиента, подключенного к серверу
                String hostName = userSocket.getInetAddress().getHostName();
                System.out.println("Accepted from " + hostName);
                // Каждое клиентское соединение открывает поток, отвечающий за связь
                ChatHandler client = new ChatHandler(userSocket, hostName);
                // Возвращаем сообщение для входа клиенту
                String welcomeMessage;
                synchronized (clients) {
                    // Сохраняем ссылку на поток, которая обрабатывает информацию о пользовательском соединении
                    clients.add(client);
                    // построить приветственное сообщение
                    if (clients.size() == 1) {
                        welcomeMessage = "Welcome! you're the first one here";
                    } else {
                        welcomeMessage = "Welcome! you're the latest of " + clients.size() + " users.";
                    }
                }
                // Запускаем клиентский поток для обработки связи
                client.start();
                client.send(CHATMASTER_ID, welcomeMessage);
            }
        } catch (IOException ex) {
            // Текущий клиент обрабатывает ошибку и выводит сообщение об ошибке, но не выдает исключение. Сервер должен продолжать работать для обслуживания других клиентов
            log("IO　Exception in runServer:  " + ex.toString());
        }
    }

    /**
     * Печать журнала
     * @param logMessage информация для печати
     */
    protected void log(String logMessage) {
        System.out.println(logMessage);
    }

    protected class ChatHandler extends Thread {

        protected Socket clientSocket;

        protected BufferedReader is;

        protected PrintWriter pw;

        protected String clientIp;

        protected String login;

        public ChatHandler(Socket clientSocket, String clientIp) throws IOException {
            this.clientSocket = clientSocket;
            this.clientIp = clientIp;
            this.clientIp = "passenger" + UUID.randomUUID (). toString (). substring (0,8);
            is = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream(),"utf-8"));
            pw = new PrintWriter(
                    new OutputStreamWriter(clientSocket.getOutputStream(),"utf-8"), true);
        }

        @Override
        public void run() {
            String line;
            try {
                /**
                 * Пока клиент остается на связи, мы всегда должны быть в этом цикле
                 * Когда цикл заканчивается, мы отключаем это соединение
                 */
                while ((line = is.readLine()) != null) {
                    // Первый символ сообщения - это тип сообщения
                    char messageType = line.charAt(0);
                    line = line.substring(1);
                    switch (messageType) {
                        case ChatProtocol.CMD_LOGIN: // Тип сообщения для входа: A + login (имя для входа)
                            // Информация для входа не содержит имени для входа
                            if (!ChatProtocol.isValidLoginName(line)) {
                                // Ответить на сообщение для входа, информация для входа недопустима
                                send(CHATMASTER_ID, "LOGIN " + line + " invalid");
                                // регистрируем записи
                                log("LOGIN INVALID from " + clientIp);
                                continue;
                            }
                            // Содержит логин
                            login = line;
                            broadcast(CHATMASTER_ID, login + " joins us, for a total of " +
                                    clients.size() + " users");
                            break;
                        case ChatProtocol.CMD_MESG: // Тип частного сообщения: B + принять имя пользователя +: + message (содержимое сообщения) ps: сообщение частного права не было реализовано на клиенте
                            // Не авторизован, не могу отправить сообщение
                            if (login == null) {
                                send(CHATMASTER_ID, "please login first");
                                continue;
                            }
                            // Анализируем имя пользователя и содержание полученного сообщения
                            int where = line.indexOf(ChatProtocol.SEPARATOR);
                            String recip = line.substring(0, where);
                            String message = line.substring(where + 1);
                            log("MESG: " + login + "-->" + recip + ": " + message);
                            // Найти пользовательскую ветку, получающую сообщение
                            ChatHandler client = lookup(recip);
                            if (client == null) {
                                // После того как не найден, отправить пользователя не залогинен
                                psend(CHATMASTER_ID, recip + "not logged in");
                            } else {
                                // Найти пользователя и отправить сообщение в частном порядке
                                client.psend(login, message);
                            }
                            break;
                        case ChatProtocol.CMD_QUIT: // Тип сообщения офлайн: C
                            broadcast(CHATMASTER_ID, "Goodbye to " + login + "@" + clientIp);
                            close();
                            return; // В настоящее время поток ChatHandler заканчивается
                        case ChatProtocol.CMD_BCAST: // Тип широковещательного сообщения: D + сообщение (содержание сообщения)
                            if (login != null) {
                                // this.send(login + "@" + clientIp , line);
                                login = clientIp; // Удалить эту строку, когда TODO официально используется для локальной отладки нескольких клиентов
                                broadcast(login, line);
                            } else {
                                // Записать, кто транслировал сообщение и что это за сообщение
                                log("B<L FROM " + clientIp);
                            }
                            break;
                        default: // нераспознанный тип сообщения
                            log("Unknown cmd " + messageType + " from" + login + "@" + clientIp);
                    }
                }
            } catch (IOException ex) {
                log("IO Exception: " + ex.toString());
            } finally {
                // Клиентский сокет заканчивается (клиент отключается и пользователь уходит в автономный режим)
                System.out.println(login + SEP + "All Done");
                String message = "This should never appear";
                synchronized (clients) {
                    // Удалить офлайн пользователей
                    clients.remove(this);
                    if (clients.size() == 0) {
                        System.out.println(CHATMASTER_ID + SEP + "I'm so lonely I could cry...");
                    } else if (clients.size() == 1) {
                        message = "Hey, you're talking to yourself again";
                    } else {
                        message = "There are now " + clients.size() + " users";
                    }
                }
                // Трансляция текущего состояния чата
                broadcast(CHATMASTER_ID, message);
            }
        }

        /**
         * Отключить клиента
         */
        protected void close() {
            // Клиентский сокет изначально был нулевым
            if (clientSocket == null) {
                log("close when not open");
                return;
            }

            try {
                // Закрыть подключенный клиентский сокет
                clientSocket.close();
                clientSocket = null;
            } catch (IOException ex) {
                log("Failure during close to " + clientIp);
            }
        }


        public void send(String sender, String message) {
            pw.println(sender + SEP + message);
        }

        public void psend(String sender, String message) {
            send("<*" + sender + "*>", message);
        }

        /**
         * Отправить сообщение всем пользователям
         * @param sender sender
         * @param message message content
         */
        public void broadcast(String sender, String message) {
            System.out.println("Boradcasting " + sender + SEP + message);
            // Обходим клиента, вызываем его метод send и транслируем
            clients.forEach(client -> {
                if (DEBUG) {
                    // Журнал печати для отправки сообщения пользователю
                    System.out.println("Sending to " + client);
                }
                client.send(sender, message);


            });
            // Распечатать журнал и завершить трансляцию
            if (DEBUG) {
                System.out.println("Done broadcast");
            }
        }


        protected ChatHandler lookup(String nick) {
            // Синхронизация, обход поиска
            synchronized (clients) {
                for (ChatHandler client: clients) {
                    if (client.login.equals(nick)) {
                        return client;
                    }
                }
            }
            // не могу найти ноль
            return null;
        }

        public String toString() {
            return "ChatHandler[" + login +"]";
        }
    }
}