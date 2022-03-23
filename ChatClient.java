package com.company;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Font;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.UUID;
import javax.swing.JFrame;

public class ChatClient extends JFrame {

    private static final long serialVersionUID = -2270001423738681797L;

    private static final String userName =
            System.getProperty("user.name",
                    "Passer" + UUID.randomUUID ().toString().substring (0,8));

    protected boolean loggedIn;

    protected JFrame windows;

    protected static final int PORT_NUM = ChatProtocol.PORT_NUM;

    protected int port;

    protected Socket sock;

    protected BufferedReader is;

    protected PrintWriter pw;

    protected TextField input;

    protected TextArea messageView;

    protected Button loginButton;

    protected Button logoutButton;

    protected static String TITLE = "Chat Room Client";


    protected String serverHost = "localhost";

    /**
     * Установить графический интерфейс
     */
    public ChatClient() {
        windows = this;
        windows.setTitle(TITLE);
        windows.setLayout(new BorderLayout());
        port = PORT_NUM;

        // GUI, стиль интерфейса отображения сообщений
        messageView = new TextArea(30, 80);
        messageView.setEditable(false);
        messageView.setFont(new Font("Monospaced", Font.PLAIN, 15));
        windows.add(BorderLayout.NORTH, messageView);

        // Создать раздел
        Panel panel = new Panel();

        // Добавить кнопку входа в систему на доске
        panel.add(loginButton = new Button("Login"));
        loginButton.setEnabled(true);
        loginButton.requestFocus();
        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                login();
                loginButton.setEnabled(false);
                logoutButton.setEnabled(true);
                input.requestFocus();
            }
        });

        // Добавить кнопку выхода на доску
        panel.add(logoutButton = new Button("Logout"));
        logoutButton.setEnabled(false);
        logoutButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                logout();
                loginButton.setEnabled(true);
                logoutButton.setEnabled(false);
                loginButton.requestFocus();
            }
        });

        // Поле ввода сообщения
        panel.add(new Label("Message here..."));
        input = new TextField(40);
        input.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Оцениваем, есть ли логин или нет перед отправкой сообщения
                if (loggedIn) {
                    // Отправить по трансляции, видимой всем
                    pw.println(ChatProtocol.CMD_BCAST + input.getText());
                    // После отправки поле ввода сообщения будет пустым
                    input.setText("");
                }
            }
        });

        // Добавить поле ввода сообщения на доску
        panel.add(input);

        // Добавить доску внизу основного интерфейса
        windows.add(BorderLayout.SOUTH, panel);
        windows.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        windows.pack();
    }


    /**
     * Войдите в чат
     */
    public void login() {


        if (loggedIn) {
            return;
        }

        try {

            sock = new Socket(serverHost, port);
            TITLE += userName;
            is = new BufferedReader(
                    new InputStreamReader(sock.getInputStream(),"utf-8"));
            pw = new PrintWriter(
                    new OutputStreamWriter(sock.getOutputStream(),"utf-8"),true);

            // Теперь фальшивый логин, не нужно вводить пароль
            pw.println(ChatProtocol.CMD_LOGIN + userName);
            loggedIn = true;
        } catch (IOException ex) {
            showStatus(ex.toString ());
            return;
        }

        // Сборка и запуск считывателя: чтение сообщения с сервера в область отображения сообщений
        new Thread(new Runnable() {
            @Override
            public void run() {
                String line;
                try {
                    // Просто авторизируйтесь и на сервере есть сообщение для чтения
                    while (loggedIn && ((line = is.readLine()) != null)) {
                        // Каждый раз, когда читается строка информации, разрыв строки
                        messageView.append(line + "\n");
                    }
                } catch (IOException ex) {
                    showStatus (ex.toString ());
                    return;
                }
            }
        }).start();
    }

    public void logout() {
        // Если вы вышли из системы, вернитесь напрямую
        if(!loggedIn) {
            return;
        }
        // Изменить статус входа и освободить ресурсы сокета
        loggedIn = false;
        try {
            if (sock != null) {
                sock.close();
            }
        } catch (Exception ex) {
            // обработать исключение
            System.out.println ("Чаты закрыты ненормально:" + ex.toString ());
        }
    }

    public void showStatus(String message) {
        System.out.println(message);
    }

    public static void main(String[] args) {
        ChatClient room = new ChatClient();
        room.pack();
        room.setVisible(true);
    }

}