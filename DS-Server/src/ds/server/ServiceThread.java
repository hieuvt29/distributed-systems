/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ds.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author hieu
 */
public class ServiceThread extends Thread {

    private final long clientId;
    private Socket socketOfServer;
    private BufferedReader clientReader;
    private BufferedWriter clientWriter;
    private User client;
    private boolean isConnected = false;

    public ServiceThread(Socket socketOfServer, long clientId) {
        this.clientId = clientId;
        this.socketOfServer = socketOfServer;
        isConnected = true;

        // Log
        System.out.println("New connection with client# " + this.clientId + " at " + socketOfServer);
    }

    private void send(String text, boolean flush, boolean sendPath) throws IOException {
        clientWriter.write(text);
        clientWriter.newLine();
        if (this.client != null && sendPath) {
            ArrayList<String> pseudoPath = new ArrayList<>(this.client.getCurrentPath());
            if (this.client.getUsername().equals("root")) {
                pseudoPath.set(0, "");
                clientWriter.write(client.getUsername() + "@" + socketOfServer.getLocalAddress() + ":" + String.join("/", pseudoPath));
            } else {
                pseudoPath.remove(1);
                pseudoPath.set(0, "~");
                clientWriter.write(client.getUsername() + "@" + socketOfServer.getLocalAddress() + ":" + String.join("/", pseudoPath));
            }
            clientWriter.newLine();
        }
        if (flush) {
            clientWriter.write("EOF");
            clientWriter.newLine();
            clientWriter.flush();
        }
    }

    private String receive() throws IOException {
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

    private void disconnect() throws IOException {
        isConnected = false;
        clientReader.close();
        clientWriter.close();
        socketOfServer.close();
        DSServer.clientThreads.remove(this);
        System.out.println("Disconnected with client# " + this.clientId + " at " + socketOfServer);
    }

    private String validatePath(String path) throws IOException {
        /* cases:
        /path: => root/path
        path: => root/username/path
        ./a: => root/username/a
        ../: => root/a
        a/../b: => root/username/b
         */
        // if no path is supplied, set path to current directory
        if (path == null) {
            return String.join("/", this.client.getCurrentPath());
        }
        // check if path is valid
        Pattern pattern = Pattern.compile("^(/)?([^/\\0123456789]+(/)?)+$");
        Matcher mc = pattern.matcher(path);
        if (!mc.find()) {
            send("Invalid path", true, true);
            return null;
        }

        // append root folder to path to get absolute path
        ArrayList<String> processPath = new ArrayList<>();
        if (path.startsWith("/")) {
            path.replaceFirst("root/", "/");
        } else {
            for (String s : this.client.getCurrentPath()) {
                processPath.add(s);
            }
        }
        // catch . or .. folder
        String[] folders = path.split("/");
        for (String folder : folders) {
            if (folder.equals(".")) {
                continue;
            } else if (folder.equals("..")) {
                processPath.remove(processPath.size() - 1);
                if (processPath.size() == 0) {
                    send("Permission denied!", true, true);
                    return null;
                }
            } else {
                processPath.add(folder);
            }
        }
        path = String.join("/", processPath);

        // root user have right to modify every child folders
        if (this.client.getUsername().equals("root")) {
            return path;
        }

        // other users just can change their root folder
        if (processPath.size() == 1 || (!processPath.get(0).equals(DSServer.rootPath) || !processPath.get(1).equals(this.client.getUsername()))) {
            send("Permission denied!", true, true);
            return null;
        } else {
            return path;
        }
    }

    @Override
    public void run() {

        try {
            // Mở luồng vào ra trên Socket tại Server.
            clientReader = new BufferedReader(new InputStreamReader(socketOfServer.getInputStream()));
            clientWriter = new BufferedWriter(new OutputStreamWriter(socketOfServer.getOutputStream()));

            System.out.println("Thread is running...");
            send("You are connecting to ssh server...", false, false);
            send("Establish connection...", false, false);

            boolean isLoggedIn = false;
            while (!isLoggedIn && isConnected) {
                try {
                    if (login()) {
                        isLoggedIn = true;
                        send("Connected, start your session!", true, true);
                        processRequests();
                    } else {
                        send("Incorrect username or password!\nLogin again? (y/n) ", true, true);
                        String res = receive();
                        if (res.equals("y") || res.equals("Y")) {
                            isLoggedIn = false;
                        } else {
                            isConnected = false;
                            break;
                        }
                    }
                } catch (ClassNotFoundException ex) {
                    Logger.getLogger(ServiceThread.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            disconnect();

        } catch (IOException e) {
            System.out.println(e);
            e.printStackTrace();
            try {
                send("Something fail! exiting... ", true, true);
            } catch (IOException ex) {
                Logger.getLogger(ServiceThread.class.getName()).log(Level.SEVERE, null, ex);
            }
        } finally {
            System.out.println("Thread stopped!");
        }
    }

    private boolean login() throws ClassNotFoundException, IOException {
        send("username: ", true, false);
        String username = receive();
        System.out.println("username: " + username + " ----");
        send("password: ", true, false);
        String password = receive();
        System.out.println("password: " + password + " ----");
        if (DSServer.checkUser(username, password)) {
            this.client = DSServer.getUser(username);
            return true;
        } else {
            return false;
        }
    }

    private void processRequests() throws IOException {
        while (isConnected) {
            String usercmdstring = receive();
            System.out.println("user command: " + usercmdstring);
            Command usercmd = new Command(usercmdstring);
            System.out.println("usercmd: " + usercmd.getCommandName());
            if (usercmd.getCommandName().equals("exit")) {
                break;
            } else if (usercmd.getCommandName().equals("hello")) {
                send("Hi, enjoy it!", true, true);
            } else if (usercmd.getCommandName().equals("time")) {
                time();
            } else if (usercmd.getCommandName().equals("echo")) {
                echo(usercmd);
            } else if (usercmd.getCommandName().equals("ls")) {
                ls(usercmd);
            } else if (usercmd.getCommandName().equals("mkdir")) {
                mkdir(usercmd);
            } else if (usercmd.getCommandName().equals("pwd")) {
                send(String.join("/", this.client.getCurrentPath()), true, true);
            } else if (usercmd.getCommandName().equals("cd")) {
                cd(usercmd);
            } else if (usercmd.getCommandName().equals("rm")) {
                rm(usercmd);
            } else if (usercmd.getCommandName().equals("mv")) {
                mv(usercmd);
            } else if (usercmd.getCommandName().equals("cp")) {
                cp(usercmd);
            } else if (usercmd.getCommandName().equals("userctl")) {
                userctl(usercmd);
            } else {
                send("Function is not available right now!", true, true);
            }
        }
    }

    private void userctl(Command cmd) throws IOException {
        System.out.println("username: " + this.client.getUsername());
        if (!this.client.getUsername().equals("root")) {
            send("Permission denied!", true, true);
            return;
        } else {
            send("Confirm password: ", true, false);
            String password = receive();
            if (!DSServer.checkUser("root", password)) {
                send("Wrong password!", true, true);
                return;
            }
        }
        boolean isCreate = false;
        boolean isDelete = false;
        if (cmd.getParameters().indexOf("--create") != -1 || cmd.getParameters().indexOf("-c") != -1) {
            isCreate = true;
        } else if (cmd.getParameters().indexOf("--delete") != -1 || cmd.getParameters().indexOf("-d") != -1) {
            isDelete = true;
        } else {
            send("Not support parameters!", true, true);
        }
        if (isCreate) {
            int userIndex = cmd.getParameters().indexOf("-u");
            String username = cmd.getParameter(userIndex + 1);
            int passwordIndex = cmd.getParameters().indexOf("-p");
            String password = cmd.getParameter(passwordIndex + 1);
            if (userIndex == -1 || passwordIndex == -1) {
                send("Not support parameters!: -u <username> -p <password>", true, true);
                return;
            }
            User newUser = new User(username, password);
            if (DSServer.checkUser(username, password)) {
                send("Username has been used!", true, true);
                return;
            } else {
                DSServer.users.add(newUser);
                send("Created User!", true, true);
                DSServer.saveUsers();
                return;
            }
        } else if (isDelete) {
            int userIndex = cmd.getParameters().indexOf("-u");
            if (userIndex == -1) {
                send("Not support parameters!: add -u option for username", true, true);
                return;
            }
            String username = cmd.getParameter(userIndex + 1);
            userIndex = DSServer.checkUsername(username);
            if (userIndex == -1) {
                send("User is not exist", true, true);
            } else {
                DSServer.users.remove(userIndex);
                send("Removed User!", true, true);
                DSServer.saveUsers();
            }

        }
        
    }

    private void time() throws IOException {
        Date now = new Date(System.currentTimeMillis());
        SimpleDateFormat format = new SimpleDateFormat("hh:mm:ss dd/MM/yyyy");
        String s = format.format(now.getTime());
        send(s, true, true);
    }

    private void echo(Command cmd) throws IOException {
        boolean appendToFile = false;
        boolean rewriteFile = false;
        String des = null;
        ArrayList<String> ct = new ArrayList<>();
        int numParams = cmd.getParameters().size();
        for (int i = 1; i < numParams; i++) {
            if (cmd.getParameter(i).equals(">")) {
                rewriteFile = true;
                des = cmd.getParameter(i + 1);
                break;
            }
            if (cmd.getParameter(i).equals(">>")) {
                appendToFile = true;
                des = cmd.getParameter(i + 1);
                break;
            } else {
                ct.add(cmd.getParameter(i));
            }
        }
        String content = String.join(" ", ct);
        if (content == "" || content == null) {
            send("", true, true);
        } else if (des != null) {
            des = validatePath(des);
            if (des == null) {
                return;
            } else {
                File desFile = new File(des);
                if (rewriteFile) {
                    PrintWriter pw = new PrintWriter(new FileOutputStream(desFile, false), true);
                    pw.write(content);
                    pw.close();
                    send("Created file!", true, true);
                } else if (appendToFile) {
                    PrintWriter pw = new PrintWriter(new FileOutputStream(desFile, true), true);
                    pw.write(content);
                    pw.close();
                    send("Appended to file!", true, true);
                }
            }
        } else {
            send(content, true, true);
        }
    }

    public String getSize(File f) {
        long length = f.length();
        String size = null;
        if ((length / 1024l) == 0) {
            size = String.format("%d Byte", length);
        } else {
            length = length / 1024l;
            if ((length / 1024l) == 0) {
                size = String.format("%d KB", length);
            } else {
                length = length / 1024l;
                size = String.format("%d MB", length);
            }
        }
        return size;
    }

    private void ls(Command cmd) throws IOException {
        String path = cmd.getParameter(1);

        path = validatePath(path);
        if (path != null) {
            System.out.println("path: " + path);
            StringBuilder result = new StringBuilder();
            SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
            File file = new File(path);
            if (file.isDirectory()) {
                File[] lsFile = file.listFiles();
                for (File i : lsFile) {
                    if (i.isDirectory()) {
                        result.append(String.format("%-4s %-10s %-20s %-10s\n", "d", getSize(i), sdf.format(i.lastModified()), i.getName()));
                    } else if (i.isFile()) {
                        result.append(String.format("%-4s %-10s %-20s %-10s\n", "f", getSize(i), sdf.format(i.lastModified()), i.getName()));
                    }
                }
                send(result.toString(), true, true);
            } else if (file.isFile()) {
                result.append(String.format("%-4s %-10s %-20s %-10s\n", "f", getSize(file), sdf.format(file.lastModified()), file.getName()));
                send(result.toString(), true, true);
            } else {
                send("No such file or directory", true, true);
            }

        }

    }

    private void mkdir(Command cmd) throws IOException {
        String path = cmd.getParameter(1);
        boolean createMultipleDir = false;
        if (cmd.getParameters().indexOf("-p") != -1 || cmd.getParameters().indexOf("--multiple") != -1) {
            createMultipleDir = true;
        }
        boolean result = false;
        path = validatePath(path);
        if (path != null) {
            if (createMultipleDir) {
                result = new File(path).mkdirs();
                if (result) {
                    send("Folder created!", true, true);
                } else {
                    send("Cannot create directory ‘" + path + "’: Folder exists", true, true);
                }
            } else {
                String[] fs = path.split("/");
                String parentFolder = String.join("/", Arrays.copyOf(fs, fs.length - 1));
                File f = new File(parentFolder);
                if (!f.isDirectory()) {
                    send("Cannot create directory ‘" + parentFolder + "’: No such file or directory", true, true);
                } else {
                    result = new File(path).mkdirs();
                    if (result) {
                        send("Folder created!", true, true);
                    } else {
                        send("Cannot create directory ‘" + path + "’: Folder exists", true, true);
                    }
                }
            }

        }
    }

    private void cd(Command cmd) throws IOException {
        String path = cmd.getParameter(1);
        path = validatePath(path);
        if (path != null) {
            File f = new File(path);
            if (f.isFile()) {
                send(path + ": Not a directory", true, true);
            } else if (!f.isDirectory()) {
                send(path + ": No such file or directory", true, true);
            } else {
                this.client.setCurrentPath(path);
                send("", true, true);
            }
        }
    }

    private void rm(Command cmd) throws IOException {
        String p1 = cmd.getParameter(1);
        if (p1 == null) {
            send("missing parameters", true, true);
        } else if (p1.equals(".") || p1.equals("..")) {
            send("refusing to remove '.' or '..' directory: skipping '" + p1 + "'", true, true);
            return;
        }

        boolean rmdir = false;
        if (cmd.getParameters().indexOf("-r") != -1 || cmd.getParameters().indexOf("--recursive") != -1) {
            rmdir = true;
        }

        String path = validatePath(p1);
        if (path != null) {
            File f = new File(path);
            if (f.exists()) {
                if (f.isDirectory()) {
                    if (rmdir) {
                        removeDir(f);
                        send("Removed!", true, true);
                    } else {
                        send("Cannot remove '" + path + "': Is a directory", true, true);
                    }
                } else if (f.isFile()) {
                    boolean result = f.delete();
                    if (result) {
                        send("Removed!", true, true);
                    } else {
                        send("Something failed!", true, true);
                    }
                }
            } else {
                send("Cannot remove '" + path + "': No such file or directory", true, true);
            }

        }
    }

    private void removeDir(File file) {
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                removeDir(f);
            }
        }
        file.delete();
    }

    private void mv(Command cmd) throws IOException {
        String src = cmd.getParameter(1);
        String des = cmd.getParameter(2);
        boolean forceWrite = false;
        if (cmd.getParameters().indexOf("-f") != -1 || cmd.getParameters().indexOf("--force") != -1) {
            forceWrite = true;
        }

        src = validatePath(src);

        if (src != null) {
            des = validatePath(des);
            if (des != null) {
                File srcFile = new File(src);
                File desFile = new File(des);
                if (srcFile.exists()) {
                    if (desFile.isDirectory()) {
                        des += "/" + srcFile.getName();
                        desFile = new File(des);
                        if (desFile.isFile()) {
                            if (forceWrite) {
                                boolean result = srcFile.renameTo(desFile);
                                if (result) {
                                    send("Moved!", true, true);
                                } else {
                                    send("Cannot move: Something failed!", true, true);
                                }
                            } else {
                                send("Cannot move '" + des + "': File exists", true, true);
                            }
                        } else {
                            boolean result = srcFile.renameTo(desFile);
                            if (result) {
                                send("Moved!", true, true);
                            } else {
                                send("Cannot move: Something failed!", true, true);
                            }
                        }
                    } else if (desFile.isFile()) {
                        if (forceWrite) {
                            boolean result = srcFile.renameTo(desFile);
                            send("Moved!", true, true);
                        } else {
                            send("Cannot move '" + des + "': File exists", true, true);
                        }
                    } else {
                        String[] fs = des.split("/");
                        String parentFolder = String.join("/", Arrays.copyOf(fs, fs.length - 1));
                        File f = new File(parentFolder);
                        if (f.exists()) {
                            boolean result = srcFile.renameTo(desFile);
                            if (result) {
                                send("Moved!", true, true);
                            } else {
                                send("Cannot move: Something failed!", true, true);
                            }
                        } else {
                            send("Cannot copy '" + parentFolder + "': No such file or directory", true, true);
                        }
                    }
                } else {
                    send("Cannot move '" + src + "': No such file or directory", true, true);
                }
            }
        }
    }

    private void copyDir(File file, String filePath, File des) throws IOException {
        Files.copy(file.toPath(), des.toPath(), StandardCopyOption.REPLACE_EXISTING);
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                copyDir(f, filePath + "/" + f.getName(), new File(des.toPath().toString() + "/" + f.getName()));
            }
        }
    }

    private void cp(Command cmd) throws IOException {
        String src = cmd.getParameter(1);
        String des = cmd.getParameter(2);
        boolean forceWrite = false;
        boolean cpDir = false;
        if (cmd.getParameters().indexOf("-f") != -1 || cmd.getParameters().indexOf("--force") != -1) {
            forceWrite = true;
        }
        if (cmd.getParameters().indexOf("-r") != -1 || cmd.getParameters().indexOf("--recursive") != -1) {
            cpDir = true;
        }
        src = validatePath(src);
        if (src != null) {
            des = validatePath(des);
            if (des != null) {
                File srcFile = new File(src);
                File desFile = new File(des);
                if (srcFile.isFile()) {
                    if (desFile.isDirectory()) { // if destination if a folder
                        des += "/" + srcFile.getName();
                        desFile = new File(des);
                        if (desFile.isFile()) {
                            if (forceWrite) {
                                Files.copy(srcFile.toPath(), desFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                send("Copied!", true, true);
                            } else {
                                send("Cannot copy '" + des + "': File exists", true, true);
                            }
                        } else {
                            Files.copy(srcFile.toPath(), desFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            send("Copied!", true, true);
                        }
                    } else if (desFile.isFile()) { // if it a file then check overwrite
                        if (forceWrite) {
                            Files.copy(srcFile.toPath(), desFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            send("Copied!", true, true);
                        } else {
                            send("Cannot copy '" + des + "': File exists", true, true);
                        }
                    } else { // if destination is not exists
                        String[] fs = des.split("/");
                        String parentFolder = String.join("/", Arrays.copyOf(fs, fs.length - 1));
                        File f = new File(parentFolder);
                        if (f.exists()) {
                            Files.copy(srcFile.toPath(), desFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            send("Copied!", true, true);
                        } else {
                            send("Cannot copy '" + parentFolder + "': No such file or directory", true, true);
                        }
                    }
                } else if (srcFile.isDirectory()) {
                    if (cpDir) {
                        copyDir(srcFile, src, desFile);
                        send("Copied!", true, true);
                    } else {
                        send("Cannot copy: add -r option to copy folder!", true, true);
                    }
                } else {
                    send("Cannot copy '" + src + "': No such file or directory", true, true);
                }
            }
        }
    }
}
