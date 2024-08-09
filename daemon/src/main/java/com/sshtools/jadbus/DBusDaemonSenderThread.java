package com.sshtools.jadbus;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

import org.freedesktop.dbus.messages.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DBusDaemonSenderThread extends Thread {
    /**
	 * 
	 */
	private final DBusDaemon dBusDaemon;
	private final Logger logger = LoggerFactory.getLogger(getClass());
    private final AtomicBoolean running = new AtomicBoolean(false); // switch running status when thread begins

    public DBusDaemonSenderThread(DBusDaemon dBusDaemon) {
        this.dBusDaemon = dBusDaemon;
		setName(getClass().getSimpleName().replace('$', '-'));
    }

    @Override
    public void run() {
        logger.debug(">>>> Sender thread started <<<<");
        running.set(true);
        while (this.dBusDaemon.isRunning() && running.get()) {

            logger.trace("Acquiring lock on outqueue and blocking for data");

            // block on outqueue
            try {
                Pair<Message, WeakReference<ConnectionStruct>> pollFirst = this.dBusDaemon.getOutQueue().take();
                if (pollFirst != null) {
                    ConnectionStruct connectionStruct = pollFirst.second.get();
                    if (connectionStruct != null) {
                        if (connectionStruct.connection.getChannel().isConnected()) {
                            logger.debug("<outqueue> Got message {} for {}", pollFirst.first, connectionStruct.unique);

                            try {
                                connectionStruct.connection.getWriter().writeMessage(pollFirst.first);
                            } catch (IOException _ex) {
                                logger.debug("Disconnecting client due to previous exception", _ex);
                                this.dBusDaemon.removeConnection(connectionStruct);
                            }
                        } else {
                            logger.warn("Connection to {} broken", connectionStruct.connection);
                            this.dBusDaemon.removeConnection(connectionStruct);
                        }

                    } else {
                        logger.info("Discarding {} connection reaped", pollFirst.first);
                    }
                }
            } catch (InterruptedException _ex) {
                logger.debug("Got interrupted", _ex);
            }
        }
        logger.debug(">>>> Sender Thread terminated <<<<");
    }

    public synchronized void terminate() {
        running.set(false);
        interrupt();
    }
}