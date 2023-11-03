package com.cyr1en.commandprompter.dependencies;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;

import com.cyr1en.commandprompter.util.Util;

public class Dependency {

    private final String filename;
    private final String url;
    private final String[] relocation;
    private final String sha1;

    public Dependency(String filename, String url, String[] relocation, String sha1) {
        this.filename = filename;
        this.url = url;
        this.relocation = relocation;
        this.sha1 = sha1;
    }

    public boolean downloadChecked(File downloadDir) throws IOException {
        if (!downloadDir.exists())
            downloadDir.mkdir();

        var file = new File(downloadDir, filename);
        
        if (!file.exists()) {
            var in = new URL(getURL()).openStream();
            Files.copy(in, file.toPath());
        }
            
        var sha1 = getSHA1();
        if (!sha1.isBlank() && !Util.checkSHA1(file, sha1)) {
            file.delete();
            return false;
        }
        return true;
    }

    public String getFileName() {
        return filename;
    }

    public String getURL() {
        return url;
    }

    public String[] getRelocation() {
        return relocation;
    }

    public String getSHA1() {
        return sha1;
    }


}
