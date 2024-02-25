package com.sshtools.jadbus;

public class UnixPlatform implements Platform {

    public UnixPlatform() {
    }

    @Override
    public long usernameToUid(String username) {
        return 0;
    }

}
