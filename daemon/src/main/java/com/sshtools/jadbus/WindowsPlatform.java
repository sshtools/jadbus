package com.sshtools.jadbus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.ResourceBundle;

import uk.co.bithatch.nativeimage.annotations.Bundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.jadbus.lib.OS;
import com.sun.jna.platform.win32.Advapi32Util;

@Bundle
public class WindowsPlatform implements Platform {
    public final static ResourceBundle BUNDLE = ResourceBundle.getBundle(WindowsPlatform.class.getName());

    
    public final static String SID_ADMINISTRATORS_GROUP = "S-1-5-32-544";
    public final static String SID_WORLD = "S-1-1-0";
    public final static String SID_USERS = "S-1-5-32-545";
    public final static String SID_SYSTEM = "S-1-5-18";

    static Logger log = LoggerFactory.getLogger(WindowsPlatform.class);

    public WindowsPlatform() {
    }

    @Override
    public long usernameToUid(String username) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void restrictToUser(Path path) throws IOException {
        var file = path.toFile();
        file.setReadable(false, false);
        file.setWritable(false, false);
        file.setExecutable(false, false);
        file.setReadable(true, true);
        file.setWritable(true, true);
        if (OS.isWindows()) {
            List<AclEntry> acl = new ArrayList<>();
            if (OS.isAdministrator()) {
                try {
                    acl.add(set(true, path, "Administrators", SID_ADMINISTRATORS_GROUP, AclEntryType.ALLOW,
                            AclEntryPermission.values()));
                } catch (Throwable upnfe2) {
                    log.debug("Failed to add administrators permission.", upnfe2);
                }
                try {
                    acl.add(set(true, path, "SYSTEM", SID_SYSTEM, AclEntryType.ALLOW, AclEntryPermission.values()));
                } catch (Throwable upnfe2) {
                    log.debug("Failed to add administrators permission.", upnfe2);
                }
            }
            if (acl.isEmpty()) {
                log.warn(String.format("Only basic permissions set for %s", path));
            } else {
                log.info(String.format("Setting permissions on %s to %s", path, acl));
                AclFileAttributeView aclAttr = Files.getFileAttributeView(path, AclFileAttributeView.class);
                aclAttr.setAcl(acl);
            }
        }
    }

    @Override
    public void openToEveryone(Path path) throws IOException {
        AclFileAttributeView aclAttr = Files.getFileAttributeView(path, AclFileAttributeView.class);
        List<AclEntry> acl = new ArrayList<>();
        if (OS.isAdministrator()) {
            try {
                acl.add(set(true, path, "Administrators", SID_ADMINISTRATORS_GROUP, AclEntryType.ALLOW,
                        AclEntryPermission.values()));
            } catch (Throwable upnfe2) {
                log.debug("Failed to add administrators permission.", upnfe2);
            }
            try {
                acl.add(set(true, path, "SYSTEM", SID_SYSTEM, AclEntryType.ALLOW, AclEntryPermission.values()));
            } catch (Throwable upnfe2) {
                log.debug("Failed to add administrators permission.", upnfe2);
            }
        }
        try {
            acl.add(set(true, path, "Everyone", SID_WORLD, AclEntryType.ALLOW, AclEntryPermission.READ_DATA,
                    AclEntryPermission.WRITE_DATA));
        } catch (Throwable upnfe) {
            log.warn("Failed to set Everyone permission.", upnfe);
        }
        try {
            acl.add(set(true, path, "Users", SID_USERS, AclEntryType.ALLOW, AclEntryPermission.READ_DATA,
                    AclEntryPermission.WRITE_DATA));
        } catch (Throwable upnfe2) {
            log.warn("Failed to set Users permission.", upnfe2);
        }
        if (acl.isEmpty()) {

            /* No everyone user, fallback to basic java.io.File methods */
            log.warn("Falling back to basic file permissions method.");
            path.toFile().setReadable(true, false);
            path.toFile().setWritable(true, false);
        } else {
            aclAttr.setAcl(acl);
        }
    }

    protected static AclEntry set(boolean asGroup, Path path, String name, String sid, AclEntryType type,
            AclEntryPermission... perms) throws IOException {
        try {
            log.debug("Trying to set '" + sid + "' or name of " + name + " on " + path + " as Group: " + asGroup + " : "
                    + type + " : " + Arrays.asList(perms));
            String bestRealName = getBestRealName(sid, name);
            log.debug("Best real name : " + bestRealName);
            return perms(asGroup, path, bestRealName, type, perms);
        } catch (Throwable t) {
            log.debug("Failed to get AclEntry using either SID of '" + sid + "' or name of " + name
                    + ". Attempting using english name.", t);
            return perms(asGroup, path, name, type, perms);
        }
    }

    public static String getBestRealName(String sid, String name) {
        try {
            if(sid == null)
                throw new NullPointerException();
            var acc = Advapi32Util.getAccountBySid(sid);
            return acc.name;
        }
        catch(Exception e) {
            /* Fallback to i18n */
            log.warn("Falling back to I18N strings to determine best real group name for {}", name);
            return BUNDLE.getString(name);
        }
    }

    protected static AclEntry perms(boolean asGroup, Path path, String name, AclEntryType type,
            AclEntryPermission... perms) throws IOException {
        UserPrincipalLookupService upls = path.getFileSystem().getUserPrincipalLookupService();
        UserPrincipal user = asGroup ? upls.lookupPrincipalByGroupName(name) : upls.lookupPrincipalByName(name);
        AclEntry.Builder builder = AclEntry.newBuilder();
        builder.setPermissions(EnumSet.copyOf(Arrays.asList(perms)));
        builder.setPrincipal(user);
        builder.setType(type);
        return builder.build();
    }
}
