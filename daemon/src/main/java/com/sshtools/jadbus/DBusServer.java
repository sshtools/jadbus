package com.sshtools.jadbus;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.freedesktop.dbus.Marshalling;
import org.freedesktop.dbus.connections.transports.TransportConnection;
import org.freedesktop.dbus.errors.AccessDenied;
import org.freedesktop.dbus.errors.MatchRuleInvalid;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.interfaces.Introspectable;
import org.freedesktop.dbus.interfaces.Peer;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.messages.ExportedObject;
import org.freedesktop.dbus.messages.Message;
import org.freedesktop.dbus.messages.MessageFactory;
import org.freedesktop.dbus.messages.MethodCall;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.bithatch.nativeimage.annotations.Proxy;
import uk.co.bithatch.nativeimage.annotations.Reflectable;
import uk.co.bithatch.nativeimage.annotations.TypeReflect;

@Reflectable
@TypeReflect(methods = true, classes = true)
public class DBusServer implements DBus, Jadbus, Introspectable, Peer {

	private static final Logger LOGGER = LoggerFactory.getLogger(DBusServer.class);


	private final DBusDaemon dBusDaemon;
//	private final String machineId;
	private ConnectionStruct connStruct;
	private final AtomicInteger nextUnique = new AtomicInteger(0);

	public DBusServer(DBusDaemon dBusDaemon) {
		this.dBusDaemon = dBusDaemon;
	}
	
	@Override
	public void AddMatch(String _matchrule) throws MatchRuleInvalid {

		LOGGER.trace("Adding match rule: {}", _matchrule);

		synchronized (this.dBusDaemon.sigrecips) {
			if (!this.dBusDaemon.sigrecips.contains(connStruct)) {
				this.dBusDaemon.sigrecips.add(connStruct);
			}
		}
	}
	
	@Override
	public Byte[] GetAdtAuditSessionData(String _busName) {
		return null;
	}

	@Override
	public Map<String, Variant<?>> GetConnectionCredentials(String _busName) {
		var map = new HashMap<String, Variant<?>>();
		var conn = dBusDaemon.connsByUnique.get(_busName);
		if(conn != null && conn.getCredentials() != null) {
			map.put("UnixUserID", new Variant<>(conn.getCredentials().getUid()));
			var gids = new ArrayList<UInt32>();
			for(var gid : conn.getCredentials().getGids()) {
				gids.add(new UInt32(gid));
			}
			map.put("UnixGroupIDs", new Variant<>(gids, "au"));
			if(conn.getCredentials().getUUID() != null) {
				map.put("ProcessUUID", new Variant<>(conn.getCredentials().getUUID()));
			}
			map.put("UnixGroupID", new Variant<>(conn.getCredentials().getGid()));
			map.put("ProcessID", new Variant<>(conn.getCredentials().getPid()));
		}
		return map;
	}

	@Override
	public Byte[] GetConnectionSELinuxSecurityContext(String _args) {
		return new Byte[0];
	}

	@Override
	public UInt32 GetConnectionUnixProcessID(String _connectionName) {
		return new UInt32(dBusDaemon.getPid(_connectionName));
	}

	@Override
	public UInt32 GetConnectionUnixUser(String _connectionName) {
		var conn = dBusDaemon.connsByUnique.get(_connectionName);
		if(conn != null && conn.getCredentials() != null) {
			return new UInt32(conn.getCredentials().getUid());
		}
		return new UInt32(0);
	}

	@Override
	public String GetId() {
		return dBusDaemon.getId();
	}

	@Override
	public String GetMachineId() {
		return dBusDaemon.getMachineId();
	}

	@Override
	public String GetNameOwner(String _name) {

		ConnectionStruct owner = this.dBusDaemon.names.get(_name);
		String o;
		if (null == owner) {
			o = "";
		} else {
			o = owner.unique;
		}

		return o;
	}

	@Override
	public String getObjectPath() {
		return null;
	}

	@Override
	public String Hello() {
		synchronized (connStruct) {
			if (null != connStruct.unique) {
				throw new AccessDenied("Connection has already sent a Hello message");
			}
			dBusDaemon.setUnique(connStruct, ":1." + nextUnique.incrementAndGet()); 
		}
		this.dBusDaemon.names.put(connStruct.unique, connStruct);

		LOGGER.info("Client {} registered", connStruct.unique);

		try {
			this.dBusDaemon.send(connStruct,
					generateNameAcquiredSignal(connStruct.connection, connStruct.unique));
			this.dBusDaemon.send(null, generatedNameOwnerChangedSignal(connStruct.connection,
					connStruct.unique, "", connStruct.unique));
		} catch (DBusException _ex) {
			LOGGER.debug("", _ex);
		}

		return connStruct.unique;
	}

	@Override
	public String Introspect() {
        try {
			var string = "<!DOCTYPE node PUBLIC \"-//freedesktop//DTD D-BUS Object Introspection 1.0//EN\" "
			        + "\"http://www.freedesktop.org/standards/dbus/1.0/introspect.dtd\">\n<node>" + new ExportedObject(this, false).getIntrospectiondata() + "\n</node>";
			return string;
		} catch (DBusException e) {
			throw new IllegalStateException(e);
		}
    }

	@Override
	public boolean isRemote() {
		return false;
	}

	@Override
	public String[] ListActivatableNames() {
		return dBusDaemon.getServices().keySet().toArray(new String[0]);
	}

	@Override
	public String[] ListNames() {
		String[] ns;
		Set<String> nss = this.dBusDaemon.names.keySet();
		ns = nss.toArray(new String[0]);
		return ns;
	}

	@Override
	public String[] ListQueuedOwners(String _name) {
		return new String[0];
	}

	@Override
	public boolean NameHasOwner(String _name) {
		return this.dBusDaemon.names.containsKey(_name);
	}

	@Override
	public void Ping() {
	}


	@Override
	public UInt32 ReleaseName(String _name) {

		boolean exists = this.dBusDaemon.releaseName(_name, connStruct);

		int rv;
		if (!exists) {
			rv = DBus.DBUS_RELEASE_NAME_REPLY_NON_EXISTANT;
		} else {
			LOGGER.info("Client {} acquired name {}", connStruct.unique, _name);
			rv = DBus.DBUS_RELEASE_NAME_REPLY_RELEASED;
			try {
				this.dBusDaemon.send(connStruct, new NameLost("/org/freedesktop/DBus", _name));
				this.dBusDaemon.send(null, new NameOwnerChanged("/org/freedesktop/DBus", _name, connStruct.unique, ""));
			} catch (DBusException _ex) {
				LOGGER.debug("", _ex);
			}
		}

		return new UInt32(rv);
	}

	@Override
	public void ReloadConfig() {
//		dBusDaemon.reloadConfig();
	}

	@Override
	public void RemoveMatch(String _matchrule) throws MatchRuleInvalid {
		LOGGER.trace("Removing match rule: {}", _matchrule);

		synchronized (this.dBusDaemon.sigrecips) {
			this.dBusDaemon.sigrecips.remove(connStruct);
		}
	}

	@Override
	public UInt32 RequestName(String _name, UInt32 _flags) {
		boolean exists = this.dBusDaemon.requestName(_name, connStruct);

		int rv;
		if (exists) {
			rv = DBus.DBUS_REQUEST_NAME_REPLY_EXISTS;
		} else {

			LOGGER.info("Client {} acquired name {}", connStruct.unique, _name);

			rv = DBus.DBUS_REQUEST_NAME_REPLY_PRIMARY_OWNER;
			try {
				this.dBusDaemon.send(connStruct,
						generateNameAcquiredSignal(connStruct.connection, _name));
				this.dBusDaemon.send(null, generatedNameOwnerChangedSignal(connStruct.connection, _name,
						"", connStruct.unique));
			} catch (DBusException _ex) {
				LOGGER.debug("", _ex);
			}
		}
		return new UInt32(rv);
	}

	@Override
	public UInt32 StartServiceByName(String _name, UInt32 _flags) {
		return new UInt32(0);
	}

	@Override
	public void UpdateActivationEnvironment(Map<String, String> _environment) {
		dBusDaemon.updateActivationEnvironment(_environment);
	}

	@Override
	public void UpdateActivationEnvironment(Map<String, String>[] _environment) {
		throw new UnsupportedOperationException("BUG: Use UpdateActivationEnvironment(Map<String, String>)");
	}

	@SuppressWarnings("unchecked")
	void handleMessage(ConnectionStruct _connStruct, Message _msg) throws DBusException {
		LOGGER.trace("Handling message {}  from {}", _msg, _connStruct.unique);

		if (!(_msg instanceof MethodCall)) {
			return;
		}
		Object[] args = _msg.getParameters();

		Class<? extends Object>[] cs = new Class[args.length];

		for (int i = 0; i < cs.length; i++) {
			cs[i] = args[i].getClass();
		}

		java.lang.reflect.Method meth = null;
		Object rv = null;
		MessageFactory messageFactory = _connStruct.connection.getMessageFactory();

		try {
			meth = DBusServer.class.getMethod(_msg.getName(), cs);
			try {
				this.connStruct = _connStruct;
				rv = meth.invoke(this, args);
				if (null == rv) {
					this.dBusDaemon.send(_connStruct,
							messageFactory.createMethodReturn("org.freedesktop.DBus", (MethodCall) _msg, null), true);
				} else {
					String sig = Marshalling.getDBusType(meth.getGenericReturnType())[0];
					this.dBusDaemon.send(_connStruct,
							messageFactory.createMethodReturn("org.freedesktop.DBus", (MethodCall) _msg, sig, rv),
							true);
				}
			} catch (InvocationTargetException _exIte) {
				LOGGER.debug("", _exIte);
				this.dBusDaemon.send(_connStruct,
						messageFactory.createError("org.freedesktop.DBus", _msg, _exIte.getCause()));
			} catch (DBusExecutionException _exDnEe) {
				LOGGER.debug("", _exDnEe);
				this.dBusDaemon.send(_connStruct, messageFactory.createError("org.freedesktop.DBus", _msg, _exDnEe));
			} catch (Exception _ex) {
				LOGGER.debug("", _ex);
				this.dBusDaemon.send(_connStruct,
						messageFactory.createError("org.freedesktop.DBus", _connStruct.unique,
								"org.freedesktop.DBus.Error.GeneralError", _msg.getSerial(), "s",
								"An error occurred while calling " + _msg.getName()));
			}
		} catch (NoSuchMethodException _exNsm) {
			this.dBusDaemon.send(_connStruct,
					messageFactory.createError("org.freedesktop.DBus", _connStruct.unique,
							"org.freedesktop.DBus.Error.UnknownMethod", _msg.getSerial(), "s",
							"This service does not support " + _msg.getName()));
		}

	}


	/**
	 * Create a 'NameOwnerChanged' signal manually. <br>
	 * This is required because the implementation in DBusNameAquired is for
	 * receiving of this signal only.
	 *
	 * @param _connection connection
	 * @param _name       name to announce
	 * @param _oldOwner   previous owner
	 * @param _newOwner   new owner
	 *
	 * @return signal
	 * @throws DBusException if signal creation fails
	 */
	private DBusSignal generatedNameOwnerChangedSignal(TransportConnection _connection, String _name, String _oldOwner,
			String _newOwner) throws DBusException {
		return _connection.getMessageFactory().createSignal("org.freedesktop.DBus", "/org/freedesktop/DBus",
				"org.freedesktop.DBus", "NameOwnerChanged", "sss", _name, _oldOwner, _newOwner);
	}

	/**
	 * Create a 'NameAcquired' signal manually.<br>
	 * This is required because the implementation in DBusNameAquired is for
	 * receiving of this signal only.
	 *
	 * @param _connection connection
	 * @param _name       name to announce
	 *
	 * @return signal
	 * @throws DBusException if signal creation fails
	 */
	private DBusSignal generateNameAcquiredSignal(TransportConnection _connection, String _name) throws DBusException {
		return _connection.getMessageFactory().createSignal("org.freedesktop.DBus", "/org/freedesktop/DBus",
				"org.freedesktop.DBus", "NameAcquired", "s", _name);
	}
}