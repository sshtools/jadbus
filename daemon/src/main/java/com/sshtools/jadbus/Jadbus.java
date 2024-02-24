package com.sshtools.jadbus;

import java.util.Map;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;

@DBusInterfaceName("com.sshtools.jadbus.DBus")
public interface Jadbus extends DBusInterface {

	void ReloadConfig();
	
	/* NOTE: This is here due to incorrect interface specification in DBus interface */
	/* TODO: Report this upstream */
	void UpdateActivationEnvironment(Map<String, String> env);
}
