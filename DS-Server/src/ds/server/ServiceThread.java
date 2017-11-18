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

    private void send(String text) throws IOException {
        clientWriter.write(text);
        clientWriter.newLine();
        if (this.client != null) {
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
        clientWriter.write("EOF");
        clientWriter.newLine();
        clientWriter.flush();
    }

    private void send(String text, boolean flush) throws IOException {
        clientWriter.write(text);
        clientWriter.newLine();
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

    @Override
    public void run() {

        try {
            // Mở luồng vào ra trên Socket tại Server.
            clientReader = new BufferedReader(new InputStreamReader(socketOfServer.getInputStream()));
            clientWriter = new BufferedWriter(new OutputStreamWriter(socketOfServer.getOutputStream()));

            System.out.println("Thread is running...");
            send("You are connecting to ssh server...", false);
            send("Establish connection...", false);

            boolean isLoggedIn = false;
            while (!isLoggedIn && isConnected) {
                try {
                    if (login()) {
                        isLoggedIn = true;
                        send("Connected, start your session!");
                        processRequests();
                    } else {
                        send("Incorrect username or password!\nLogin again? (y/n) ");
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
                send("Something fail! exiting... ");
            } catch (IOException ex) {
                Logger.getLogger(ServiceThread.class.getName()).log(Level.SEVERE, null, ex);
            }
        } finally {
            System.out.println("Thread stopped!");
        }
    }

    private boolean login() throws ClassNotFoundException, IOException {
        send("username: ");
        String username = receive();
        System.out.println("username: " + username + " ----");
        send("password: ");
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
            if (usercmdstring == "" || usercmdstring == null) {
                send("");
                return;
            }
            Command usercmd = new Command(usercmdstring);
            System.out.println("usercmd: " + usercmd.getCommandName());
            if (usercmd.getCommandName().equals("exit")) {
                break;
            } else if (usercmd.getCommandName().equals("hello")) {
                send("Hi, enjoy it!");
            } else if (usercmd.getCommandName().equals("time")) {
                time();
            } else if (usercmd.getCommandName().equals("echo")) {
                echo(usercmd);
            } else if (usercmd.getCommandName().equals("ls")) {
                ls(usercmd);
            } else if (usercmd.getCommandName().equals("mkdir")) {
                mkdir(usercmd);
            } else if (usercmd.getCommandName().equals("pwd")) {
                send(String.join("/", this.client.getCurrentPath()));
            } else if (usercmd.getCommandName().equals("cd")) {
                cd(usercmd);
            } else if (usercmd.getCommandName().equals("rm")) {
                rm(usercmd);
            } else if (usercmd.getCommandName().equals("mv")) {
                mv(usercmd);
            } else if (usercmd.getCommandName().equals("cp")) {
                cp(usercmd);
            } else {
                send("Function is not available right now!");
            }
        }
    }

    private void time() throws IOException {
        Date now = new Date(System.currentTimeMillis());
        SimpleDateFormat format = new SimpleDateFormat("hh:mm:ss dd/MM/yyyy");
        String s = format.format(now.getTime());
        send(s);
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
            send("");
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
                    send("Created file!");
                } else if (appendToFile) {
                    PrintWriter pw = new PrintWriter(new FileOutputStream(desFile, true), true);
                    pw.write(content);
                    pw.close();
                    send("Appended to file!");
                }
            }
        } else {
            send(content);
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
                send(result.toString());
            } else if (file.isFile()) {
                result.append(String.format("%-4s %-10s %-20s %-10s\n", "f", getSize(file), sdf.format(file.lastModified()), file.getName()));
                send(result.toString());
            } else {
                send("No such file or directory");
            }

        }

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
            send("Invalid path");
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
                    send("Permission deinied!");
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
            send("Permission deinied!");
            return null;
        } else {
            return path;
        }
    }

    private void mkdir(Command cmd) throws IOException {
        String path = cmd.getParameter(1);
        boolean createMultipleDir = false;
        if (cmd.getParameters().indexOf("-p") != -1) {
            createMultipleDir = true;
        }
        boolean result = false;
        path = validatePath(path);
        if (path != null) {
            if (createMultipleDir) {
                result = new File(path).mkdirs();
                if (result) {
                    send("Folder created!");
                } else {
                    send("Cannot create directory ‘" + path + "’: Folder exists");
                }
            } else {
                String[] fs = path.split("/");
                String parentFolder = String.join("/", Arrays.copyOf(fs, fs.length - 1));
                File f = new File(parentFolder);
                if (!f.isDirectory()) {
                    send("Cannot create directory ‘" + parentFolder + "’: No such file or directory");
                } else {
                    result = new File(path).mkdirs();
                    if (result) {
                        send("Folder created!");
                    } else {
                        send("Cannot create directory ‘" + path + "’: Folder exists");
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
                send(path + ": Not a directory");
            } else if (!f.isDirectory()) {
                send(path + ": No such file or directory");
            } else {
                this.client.setCurrentPath(path);
                send("");
            }
        }
    }

    private void rm(Command cmd) throws IOException {
        String p1 = cmd.getParameter(1);
        if (p1 == null) {
            send("missing parameters");
        } else if (p1.equals(".") || p1.equals("..")) {
            send("refusing to remove '.' or '..' directory: skipping '" + p1 + "'");
            return;
        }

        boolean rmdir = false;
        if (cmd.getParameters().indexOf("-r") != -1) {
            rmdir = true;
        }

        String path = validatePath(p1);
        if (path != null) {
            File f = new File(path);
            if (f.exists()) {
                if (rmdir) {
                    removeDir(f);
                    send("Removed!");
                } else {
                    boolean result = f.delete();
                    if (result) {
                        send("Removed!");
                    } else {
                        send("Cannot remove '" + path + "': Is a directory");
                    }
                }
            } else {
                send("Cannot remove '" + path + "': No such file or directory");
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
        if (cmd.getParameters().indexOf("-f") != -1) {
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
                                    send("Moved!");
                                } else {
                                    send("Cannot move: Something failed!");
                                }
                            } else {
                                send("Cannot move '" + des + "': File exists");
                            }
                        } else {
                            boolean result = srcFile.renameTo(desFile);
                            if (result) {
                                send("Moved!");
                            } else {
                                send("Cannot move: Something failed!");
                            }
                        }
                    } else if (desFile.isFile()) {
                        if (forceWrite) {
                            boolean result = srcFile.renameTo(desFile);
                            send("Moved!");
                        } else {
                            send("Cannot move '" + des + "': File exists");
                        }
                    } else {
                        String[] fs = des.split("/");
                        String parentFolder = String.join("/", Arrays.copyOf(fs, fs.length - 1));
                        File f = new File(parentFolder);
                        if (f.exists()) {
                            boolean result = srcFile.renameTo(desFile);
                            if (result) {
                                send("Moved!");
                            } else {
                                send("Cannot move: Something failed!");
                            }
                        } else {
                            send("Cannot copy '" + parentFolder + "': No such file or directory");
                        }
                    }
                } else {
                    send("Cannot move '" + src + "': No such file or directory");
                }
            }
        }
    }

    private void cp(Command cmd) throws IOException {
        String src = cmd.getParameter(1);
        String des = cmd.getParameter(2);
        boolean forceWrite = false;

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
                                Files.copy(srcFile.toPath(), desFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                send("Copied!");
                            } else {
                                send("Cannot copy '" + des + "': File exists");
                            }
                        } else {
                            Files.copy(srcFile.toPath(), desFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            send("Copied!");
                        }
                    } else if (desFile.isFile()) {
                        if (forceWrite) {
                            Files.copy(srcFile.toPath(), desFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            send("Copied!");
                        } else {
                            send("Cannot copy '" + des + "': File exists");
                        }
                    } else { // not exists
                        String[] fs = des.split("/");
                        String parentFolder = String.join("/", Arrays.copyOf(fs, fs.length - 1));
                        File f = new File(parentFolder);
                        if (f.exists()) {
                            Files.copy(srcFile.toPath(), desFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            send("Copied!");
                        } else {
                            send("Cannot copy '" + parentFolder + "': No such file or directory");
                        }
                    }
                } else {
                    send("Cannot copy '" + src + "': No such file or directory");
                }
            }
        }
    }
}
