package com.sshtools.jadbus;

import java.io.IOException;

import org.freedesktop.dbus.connections.transports.TransportConnection;
import org.newsclub.net.unix.AFUNIXSocketChannel;
import org.newsclub.net.unix.AFUNIXSocketCredentials;

public class ConnectionStruct {
    final TransportConnection       connection;
    String                          unique;
    AFUNIXSocketCredentials 		credentials;

    ConnectionStruct(TransportConnection _c) throws IOException {
        connection = _c;
        if(connection.getChannel() instanceof AFUNIXSocketChannel) {
        	var afChannel = (AFUNIXSocketChannel)connection.getChannel();
        	credentials = afChannel.getPeerCredentials();
        }
    }
    
    public AFUNIXSocketCredentials getCredentials() {
    	return credentials;
    }

    @Override
    public String toString() {
        return null == unique ? ":?-?" : unique;
    }
}