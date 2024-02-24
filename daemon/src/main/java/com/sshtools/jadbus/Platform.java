package com.sshtools.jadbus;

import com.sshtools.jadbus.lib.OS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.LinkedHashSet;

public interface Platform {

    public static Platform get() {
        if (OS.isWindows())
            return new WindowsPlatform();
        else if (OS.isMacOs())
            return new MacPlatform();
        else if (OS.isUnixLike())
            return new UnixPlatform();
        else
            throw new UnsupportedOperationException("Not supported on this platform.");
    }
    
    boolean  isAdministrator();

    long usernameToUid(String username);

    default void restrictToUser(Path path) throws IOException {
        var prms = Arrays.asList(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        Files.setPosixFilePermissions(path, new LinkedHashSet<>(prms));
    }

    default void openToEveryone(Path path) throws IOException {
        Files.setPosixFilePermissions(path,
                new LinkedHashSet<>(Arrays.asList(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE,
                        PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE)));
    }
}
