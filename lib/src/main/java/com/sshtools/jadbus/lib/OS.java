package com.sshtools.jadbus.lib;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

public final class OS {

	public static final String OS_NAME = System.getProperty("os.name");
	
	private static Boolean administrator;

    public static boolean isAdministrator() {
        if (administrator != null)
            return administrator;
        if(isWindows()) {
	        try {
	            String programFiles = System.getenv("ProgramFiles");
	            if (programFiles == null) {
	                programFiles = "C:\\Program Files";
	            }
	            File temp = new File(programFiles, UUID.randomUUID().toString() + ".txt");
	            temp.deleteOnExit();
	            if (temp.createNewFile()) {
	                temp.delete();
	                return administrator = true;
	            } else {
	                return administrator = false;
	            }
	        } catch (IOException e) {
	            return administrator = false;
	        }
        }
        else {
        	return administrator = System.getProperty("user.name").equals("root");
        }
    }

	public static boolean isDeveloperWorkspace() {
		return Files.exists(Paths.get("pom.xml"));
	}

	public static boolean isAix() {
		return OS_NAME.toLowerCase().contains("aix");
	}

	public static boolean isBSD() {
		return OS_NAME.toLowerCase().contains("bsd");
	}

	public static boolean isLinux() {
		return OS_NAME.toLowerCase().contains("linux");
	}

	public static boolean isMacOs() {
		return OS_NAME.toLowerCase().contains("mac os");
	}

	public static boolean isSolaris() {
		return OS_NAME.toLowerCase().contains("sunos");
	}

	public static boolean isUnixLike() {
		return isLinux() || isMacOs() || isBSD() || isAix() || isSolaris();
	}

	public static boolean isWindows() {
		return OS_NAME.toLowerCase().contains("windows");
	}

	private OS() {
	}
}
