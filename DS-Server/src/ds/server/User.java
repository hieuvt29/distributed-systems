/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ds.server;

import java.io.File;
import java.util.ArrayList;
import java.util.Stack;

/**
 *
 * @author hieu
 */
public class User {
    private String username;
    private String password;
    private ArrayList<String> currentPath = null;
    private String rootPath = null;
    
    public User(String username, String password) {
        this.username = username;
        this.password = password;
        if (this.username.equals("root")){
            this.rootPath = DSServer.rootPath;
        } else {
            this.rootPath = DSServer.rootPath + "/" + this.username;
        }
        
        this.currentPath = new ArrayList<>();
        this.currentPath.add(DSServer.rootPath);
        this.currentPath.add(this.username);
        
        // create user root folder
        new File(this.rootPath).mkdir();
        
    }
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
    
    public ArrayList<String> getCurrentPath() {
        return this.currentPath;
    }
    
    public String getRootPath() {
        return this.rootPath;
    }
    
    public String addFolderToCurrentPath(String folderName){
        this.currentPath.add(folderName);
        return String.join("/", this.currentPath);
    }

    public void setCurrentPath(String currentPath) {
        ArrayList<String> crp = new ArrayList<>();
        for (String s: currentPath.split("/")){
            crp.add(s);
        }
        this.currentPath = crp;
    }
}
