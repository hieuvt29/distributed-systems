package ds.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

public class DSServer {

    public static final int MAX_CLIENT_NUMBER = 1;
    public static ArrayList<ServiceThread> clientThreads = new ArrayList<>();
    public static ArrayList<User> users = new ArrayList<>();
    public static ServerSocket listener = null;
    public static InetAddress hostAddress = null;
    public static String rootPath = "root";

    private static void init() throws FileNotFoundException, IOException {
        // load users
        BufferedReader br = new BufferedReader(new FileReader("src/users.dat"));
        try {
            String line = br.readLine();
            while (line != null) {
                String username = line.split(":")[0];
                String password = line.split(":")[1];
                User u = new User(username, password);
                users.add(u);
                line = br.readLine();
            }
            User admin = new User("root", "admin");
            users.add(admin);
        } finally {
            br.close();
        }

        // create root directory
        new File(rootPath).mkdir();
    }

    public static void saveUsers() throws IOException {
        BufferedWriter wr = new BufferedWriter(new FileWriter("src/users.dat"));
        for (User u : users) {
            wr.write(u.getUsername() + ":" + u.getPassword() + "\n");
        }
        wr.close();
    }

    public static boolean checkUser(String username, String password) {
        System.out.println("Checking user");
        return users.stream().anyMatch((user) -> (user.getUsername().equals(username) && user.getPassword().equals(password)));
    }

    public static int checkUsername(String username) {
        for (User u : users) {
            if (u.getUsername().equals(username)) {
                return users.indexOf(u);
            }
        }
        return -1;
    }

    public static User getUser(String username) {
        for (User u : users) {
            if (u.getUsername().equals(username)) {
                return u;
            }
        }
        return null;
    }

    public static void main(String args[]) throws IOException {

        BufferedReader is;
        BufferedWriter os;
        init();
        // Mở một ServerSocket tại cổng 9999.
        // Chú ý bạn không thể chọn cổng nhỏ hơn 1023 nếu không là người dùng
        // đặc quyền (privileged users (root)).
        try {
            listener = new ServerSocket(9999);
            hostAddress = listener.getInetAddress();
        } catch (IOException e) {
            System.out.println(e);
            System.exit(1);
        }

        try {
            System.out.println("Server is waiting to accept user...");

            while (true) {

                Socket socketOfServer = listener.accept();
                if (clientThreads.size() < MAX_CLIENT_NUMBER) {
                    // Chấp nhận một yêu cầu kết nối từ phía Client.
                    // Đồng thời nhận được một đối tượng Socket tại server.

                    System.out.println("Accept a client!\nNumber of clients: " + clientThreads.size());
                    ServiceThread newClientThread = new ServiceThread(socketOfServer, System.currentTimeMillis());
                    clientThreads.add(newClientThread);
                    newClientThread.start();
                } else {
                    System.out.println("Reached the limit number of clients!");
                    os = new BufferedWriter(new OutputStreamWriter(socketOfServer.getOutputStream()));
                    os.write("Reached the limit number of clients!");
                    os.newLine();
                    os.write("exiting...");
                    os.newLine();
                    os.write("EOF");
                    os.flush();
                    os.close();
                    socketOfServer.close();
                }
            }

        } catch (IOException e) {
            System.out.println(e);
            e.printStackTrace();
        } finally {
            listener.close();
            saveUsers();
            System.out.println("Sever stopped!");
            System.exit(1);
        }
    }
}
