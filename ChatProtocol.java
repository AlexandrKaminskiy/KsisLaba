package com.company;


public class ChatProtocol {


    public static final int PORT_NUM = 8080;


    public static final char CMD_LOGIN = 'A';


    public static final char CMD_MESG = 'B';


    public static final char CMD_QUIT = 'C';


    public static final char CMD_BCAST = 'D';


    public static final int SEPARATOR = '|';

    public static boolean isValidLoginName(String message) {
        return message != null && message.length() != 0;
    }
}