package service;

import model.*;
import java.io.*;

public class FileService {

    private AccessControlService acs = new AccessControlService();

    public String readFile(User user, FileResource file) {

        if (!acs.checkAccess(user, file, 'r')) return "DENIED";

        try (BufferedReader br = new BufferedReader(new FileReader(file.getFileName()))) {
            return br.readLine();
        } catch (Exception e) {
            return "ERROR";
        }
    }

    public String writeFile(User user, FileResource file, String data) {

        if (!acs.checkAccess(user, file, 'w')) return "DENIED";

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file.getFileName()))) {
            bw.write(data);
            return "WRITE SUCCESS";
        } catch (Exception e) {
            return "ERROR";
        }
    }

    public String executeFile(User user, FileResource file) {

        if (!acs.checkAccess(user, file, 'x')) return "DENIED";

        return "EXECUTED (simulated)";
    }
}