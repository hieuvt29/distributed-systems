/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ds.client;

import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DSClient {

    public static void main(String[] args) {

        // Địa chỉ máy chủ.
        final String serverHost = "localhost";

        Socket socketOfClient = null;
        BufferedWriter os = null;
        BufferedReader is = null;

        try {
            // Gửi yêu cầu kết nối tới Server đang lắng nghe
            // trên máy 'localhost' cổng 9999.
            socketOfClient = new Socket(serverHost, 9999);

            // Tạo luồng đầu ra tại client (Gửi dữ liệu tới server)
            os = new BufferedWriter(new OutputStreamWriter(socketOfClient.getOutputStream()));

            // Luồng đầu vào tại Client (Nhận dữ liệu từ server).
            is = new BufferedReader(new InputStreamReader(socketOfClient.getInputStream()));

        } catch (UnknownHostException e) {
            System.err.println("Don't know about host " + serverHost);
            return;
        } catch (IOException e) {
            System.err.println("Couldn't get I/O for the connection to " + serverHost);
            return;
        }

        try {
            while (true) {
                // Đọc dữ liệu trả lời từ phía server
                // Bằng cách đọc luồng đầu vào của Socket tại Client.
                String responseLine;
                responseLine = receive(is);

                if (responseLine.contains("username") || responseLine.contains("password")) {
                    System.out.print(responseLine);
                } else {
                    System.out.print(responseLine);
                    System.out.print("$ ");
                }

                Scanner sc = new Scanner(System.in);
                String usercmd = sc.nextLine();
                if (responseLine.contains("exit")) {
                    disconnect(is, os, socketOfClient, false);
                    return;
                }
                send(os, usercmd);

                if (usercmd.contains("exit")) {
                    break;
                }
            }
            disconnect(is, os, socketOfClient, true);

        } catch (UnknownHostException e) {
            System.err.println("Trying to connect to unknown host: " + e);
        } catch (IOException e) {
            System.err.println("IOException:  " + e);
        }
    }

    static void disconnect(BufferedReader is, BufferedWriter os, Socket socketOfClient, boolean send) throws IOException {
        if (send) {
            send(os, "exit");
        }
        os.close();
        is.close();
        socketOfClient.close();
    }

    static String receive(BufferedReader clientReader) throws IOException {
        StringBuilder res = new StringBuilder();
        String buff;
        while ((buff = clientReader.readLine()) != null) {
            if (buff.contains("EOF")) {
                break;
            }
            if (res.length() != 0) {
                res.append("\n");
            }
            res.append(buff);
        }
        return res.toString();
    }

    static void send(BufferedWriter clientWriter, String text, boolean flush) throws IOException {
        clientWriter.write(text);
        clientWriter.newLine();
        if (flush) {
            clientWriter.write("EOF");
            clientWriter.newLine();
            clientWriter.flush();
        }
    }

    static void send(BufferedWriter clientWriter, String text) throws IOException {
        clientWriter.write(text);
        clientWriter.newLine();
        clientWriter.write("EOF");
        clientWriter.newLine();
        clientWriter.flush();
    }

}
