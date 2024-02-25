package com.sshtools.jadbus.lib;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JadbusAddress {

    public final static String processAddress(String address) {
        return address.replace("~", System.getProperty("user.home")).replace("%u", System.getProperty("user.name"));
    }

    public final static Path systemBusPath() {
        if(OS.isWindows()) {
            var publicDir = Paths.get("C:\\Users\\Public");
            if (Files.exists(publicDir)) {
                return publicDir.resolve("AppData").resolve("Jadbus").resolve("system-bus");
            } 
        }
        else if(OS.isMacOs()) {
            return Paths.get("/tmp").resolve("jadbus").resolve("system-bus");
        }
        return Paths.get(System.getProperty("java.io.tmpdir")).resolve("jadbus").resolve("system-bus");
    }

    public final static String systemBus() {
    	return "unix:path=" + systemBusPath().toString();
    }
    
    public final static String sessionBus() {
        return sessionBus(true);
    }
    
    public final static String sessionBus(boolean fallbackToStandardEnvVar) {
        var envvar = System.getenv("JADDBUS_SESSION_BUS_ADDRESS");
        if(fallbackToStandardEnvVar && (envvar == null || envvar.equals(""))) {
            envvar = System.getenv("DBUS_SESSION_BUS_ADDRESS");
        }
        if(envvar == null || envvar.equals("")) {
            envvar = "unix:path=~/.jadbus/session-bus";
        }
        envvar = processAddress(envvar);
        return envvar;
    }
}
