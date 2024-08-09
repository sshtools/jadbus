module com.sshtools.jadbus {
	requires transitive org.freedesktop.dbus;
	requires java.prefs;
	requires transitive static org.freedesktop.dbus.transport.junixsocket;
	requires transitive static org.freedesktop.dbus.transport.jre;
	requires static org.freedesktop.dbus.transport.tcp;
	requires info.picocli; 
	requires com.sshtools.jini;
	requires com.sshtools.jadbus.lib;
	requires org.slf4j.simple;
	requires static uk.co.bithatch.nativeimage.annotations;
    requires com.sun.jna.platform;
	exports com.sshtools.jadbus;
	opens com.sshtools.jadbus;
}