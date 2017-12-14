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

    Socket socketOfClient = null;
    BufferedWriter os = null;
    BufferedReader is = null;
    // Địa chỉ máy chủ.
    String host = null;
    int port = 0;
    boolean isConnected = false;

    public DSClient(String host, int port) throws IOException {
        this.host = host;
        this.port = port;
        init();
    }

    private void init() throws IOException {
        this.socketOfClient = new Socket(host, port);
        // Tạo luồng đầu ra tại client (Gửi dữ liệu tới server)
        this.os = new BufferedWriter(new OutputStreamWriter(this.socketOfClient.getOutputStream()));

        // Luồng đầu vào tại Client (Nhận dữ liệu từ server).
        this.is = new BufferedReader(new InputStreamReader(this.socketOfClient.getInputStream()));
        isConnected = true;
    }

    public void disconnect(boolean send) throws IOException {
        if (send) {
            send("exit");
        }
        os.close();
        is.close();
        isConnected = false;
        socketOfClient.close();
    }

    public String receive() throws IOException {
        StringBuilder res = new StringBuilder();
        String buff;
        while ((buff = is.readLine()) != null) {
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

    public void send(String text, boolean flush) throws IOException {
        os.write(text);
        os.newLine();
        if (flush) {
            os.write("EOF");
            os.newLine();
            os.flush();
        }
    }

    public void send(String text) throws IOException {
        os.write(text);
        os.newLine();
        os.write("EOF");
        os.newLine();
        os.flush();
    }

    public static void main(String[] args) throws IOException {

        DSClient client = new DSClient("localhost", 9999);

        while (true) {
            // Đọc dữ liệu trả lời từ phía server
            // Bằng cách đọc luồng đầu vào của Socket tại Client.
            String responseLine;
            responseLine = client.receive();

            if (responseLine.contains("username") || responseLine.contains("password")) {
                System.out.print(responseLine);
            } else {
                System.out.print(responseLine);
                System.out.print("$ ");
            }

            Scanner sc = new Scanner(System.in);
            String usercmd = sc.nextLine();
            if (responseLine.contains("exit")) {
                client.disconnect(false);
                return;
            }
            client.send(usercmd);

            if (usercmd.contains("exit")) {
                break;
            }
        }
        client.disconnect(true);
    }
}
