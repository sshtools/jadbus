package com.sshtools.jadbus;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.FatalException;
import org.freedesktop.dbus.messages.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DBusDaemonReaderThread extends Thread {
    /**
	 * 
	 */
	private final DBusDaemon dBusDaemon;
	private final Logger LOGGER = LoggerFactory.getLogger(getClass());
    private ConnectionStruct                      conn;
    private final WeakReference<ConnectionStruct> weakconn;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DBusDaemonReaderThread(DBusDaemon dBusDaemon, ConnectionStruct _conn) {
        this.dBusDaemon = dBusDaemon;
		this.conn = _conn;
        weakconn = new WeakReference<>(_conn);
        setName(getClass().getSimpleName());
    }

    public void terminate() {
        running.set(false);
    }

    @Override
    public void run() {
    	LOGGER.debug(">>>> Reader Thread started <<<<");
        running.set(true);
        while (this.dBusDaemon.isRunning() && running.get()) {

            Message m = null;
            try {
                m = conn.connection.getReader().readMessage();
            } catch (IOException _ex) {
                LOGGER.debug("Error reading message", _ex);
                this.dBusDaemon.removeConnection(conn);
            } catch (DBusException _ex) {
                LOGGER.debug("", _ex);
                if (_ex instanceof FatalException) {
                    this.dBusDaemon.removeConnection(conn);
                }
            }

            if (null != m) {
            	DBusDaemon.logMessage("Read {} from {}", m, conn.unique);

                this.dBusDaemon.inqueue.add(new Pair<>(m, weakconn));
            }
        }
        conn = null;
        LOGGER.debug(">>>> Reader Thread terminated <<<<");
    }
}