package fileReader;

import java.io.File;

public class folder {

    //String FolderPath = "F:\\Ahmed\\yellout\\Yellout-Forms";
    String FolderPath = "C:\\Users\\Ahmed\\Documents\\NetBeansProjects\\networks_lab\\src\\resources";
    File folder = new File(FolderPath);
    File[] allFiles = folder.listFiles();

    public void folder() {
        // No Cooking Recipe for now
    }

    public boolean checkFile(String fileName) {
        for (File aFile : allFiles) {
            if (aFile.getName().equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    public File getFile(String fileName) {
        for (File aFile : allFiles) {
            if (aFile.getName().equals(fileName)) {
                return aFile;
            }
        }
        return null;
    }
}
