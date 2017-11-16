/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ds.server;

/**
 *
 * @author hieu
 */
import java.io.Serializable;
import java.util.ArrayList;
import java.util.StringTokenizer;

public class Command implements Serializable {

    private ArrayList<String> parameters;

    public Command(String userInput) {
        parameters = new ArrayList<String>();
        if (userInput.contains(" ")) {
            StringTokenizer strToken = new StringTokenizer(userInput, " ");
            while (strToken.hasMoreTokens()) {
                parameters.add(strToken.nextToken());
            }
        } else {
            parameters.add(userInput);
        }
    }

    public String getCommandName() {
        return parameters.get(0);
    }

    public String getParameter(int number) {
        if (number >= parameters.size()) {
            return null;
        } else {
            return parameters.get(number);
        }
    }
    
    public ArrayList<String> getParameters() {
        return this.parameters;
    }
    
    public void addParameter(String parameter) {
        parameters.add(parameter);
    }

    public boolean hasParameters() {
        return (parameters.size() > 1);
    }
}
