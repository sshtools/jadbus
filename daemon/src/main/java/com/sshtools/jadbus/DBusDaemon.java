package com.sshtools.jadbus;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.lang.ProcessBuilder.Redirect;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.Preferences;

import org.freedesktop.dbus.connections.BusAddress;
import org.freedesktop.dbus.connections.transports.AbstractTransport;
import org.freedesktop.dbus.connections.transports.TransportBuilder;
import org.freedesktop.dbus.connections.transports.TransportBuilder.SaslAuthMode;
import org.freedesktop.dbus.connections.transports.TransportConnection;
import org.freedesktop.dbus.exceptions.AuthenticationException;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.SocketClosedException;
import org.freedesktop.dbus.interfaces.DBus.NameOwnerChanged;
import org.freedesktop.dbus.interfaces.Introspectable;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.messages.Message;
import org.freedesktop.dbus.messages.MessageFactory;
import org.freedesktop.dbus.messages.MethodCall;
import org.freedesktop.dbus.utils.Hexdump;
import org.freedesktop.dbus.utils.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import com.sshtools.jadbus.lib.JadbusAddress;
import com.sshtools.jadbus.lib.OS;
import com.sshtools.jini.INI;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;

/**
 * A replacement DBusDaemon
 */
@Command(name = "dbus-java-daemon", mixinStandardHelpOptions = true, description = "Alternative DBus broker", versionProvider = DBusDaemon.Version.class)
public class DBusDaemon implements Closeable, Callable<Integer> {
    public final static class Version implements IVersionProvider {

        private static final String VERSION = System.getProperty("build.version", "Unknown");

        @Override
        public String[] getVersion() throws Exception {
            return new String[] { VERSION };
        }
    }

    public static final int QUEUE_POLL_WAIT = 500;

    private static Logger LOGGER;

    public static void main(String[] args) throws Exception {
        System.exit(new CommandLine(new DBusDaemon()).execute(args));
    }

    static void logMessage(String _logStr, Message _m, String _connUniqueId) {
        Object logMsg = _m;
        if (_m != null && Introspectable.class.getName().equals(_m.getInterface()) && !LOGGER.isTraceEnabled()) {
            logMsg = "<Introspection data only visible in loglevel trace>";
        }

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace(_logStr, logMsg, _connUniqueId);
        } else {
            LOGGER.debug(_logStr, _m, _connUniqueId);
        }
    }

    private static List<String> parseQuotedString(String command) {
        var args = new ArrayList<String>();
        var escaped = false;
        var quoted = false;
        var word = new StringBuilder();
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '"' && !escaped) {
                if (quoted) {
                    quoted = false;
                } else {
                    quoted = true;
                }
            } else if (c == '"' && !escaped) {
                escaped = true;
            } else if (c == ' ' && !escaped && !quoted) {
                if (word.length() > 0) {
                    args.add(word.toString());
                    word.setLength(0);
                    ;
                }
            } else {
                word.append(c);
            }
        }
        if (escaped)
            throw new IllegalArgumentException("Invalid escape.");
        if (quoted)
            throw new IllegalArgumentException("Unbalanced quotes.");
        if (word.length() > 0)
            args.add(word.toString());
        return args;
    }

    @Option(names = { "--listen", "-l" }, description = "Address to listen on.")
    private Optional<String> addr;

    @Option(names = { "--log-level", "-L" }, description = "Logging level.")
    private Optional<Level> level;

    @Option(names = { "--insecure",
            "-I" }, description = "Create ADDRESSFILE and PIDFILE with open permissions, readable by anyone.")
    private boolean insecure;

    @Option(names = { "--system-bus",
            "-sb" }, negatable = true, description = "Use paths appropriate for the system bus on this platform.")
    private Optional<Boolean> systemBus;

    @Option(names = { "--pidfile", "-p" }, description = "Path of store to store the PID of this process.")
    private Optional<Path> pidfile;

    @Option(names = { "--addressfile", "-a" }, description = "Path of store to store the DBus address for this broker.")
    private Optional<Path> addressfile;

    @Option(names = { "--configuration", "-C" }, description = "Path to configuration file. Defaults to conf/dbus.ini.")
    private Optional<Path> configurationPath;

    @Option(names = { "--output", "-O" }, description = "Redirect all other output (logging etc) to a file. When not provided, will be decided based on current environment.")
    private Optional<Path> outputPath;

    @Option(names = { "--print-address", "-r" }, description = "Print the DBus address on stdout.")
    private boolean printaddress;

    @Option(names = { "--unix",
            "-u" }, description = "If no explicit address provided, for use of Unix Domain Sockets (not required, this is the default).")
    private boolean unix = true;

    @Option(names = { "--tcp", "-t" }, description = "If no explicit address provided, for use of TCP Sockets.")
    private boolean tcp;

    @Option(names = { "--authmode", "--auth-mode", "-m" }, description = "Authentication mode.")
    private Optional<SaslAuthMode> authMode;
    @Option(names = { "--addresspref",
            "-A" }, paramLabel = "PREFSPEC", description = "Save the dbus address in a Java preference node. The key is specified as [system:]<path/to/node>/<key>.")
    private Optional<String> addresspref;

    /* TODO report this to Install4J. */
    @Option(names = {
            "start-launchd" }, hidden = true, description = "Work-around for apparent bug in Install4j for SystemD native launchers for *Linux*. This argument for Mac OS is added!")
    private boolean startLaunchD;
    private Thread thread;

    private INI configuration;
                                                                                              private final Map<ConnectionStruct, DBusDaemonReaderThread> conns = new ConcurrentHashMap<>();
    final Map<String, ConnectionStruct> connsByUnique = new ConcurrentHashMap<>();

    final Map<String, ConnectionStruct> names = Collections.synchronizedMap(new HashMap<>()); // required because of

    // "null" key
    final BlockingDeque<Pair<Message, WeakReference<ConnectionStruct>>> outqueue = new LinkedBlockingDeque<>();

    final BlockingDeque<Pair<Message, WeakReference<ConnectionStruct>>> inqueue = new LinkedBlockingDeque<>();
    final List<ConnectionStruct> sigrecips = new ArrayList<>();

    private final DBusServer dbusServer = new DBusServer(this);

    private final DBusDaemonSenderThread sender = new DBusDaemonSenderThread(this);
    private final AtomicBoolean run = new AtomicBoolean(false);
    private AbstractTransport transport;

    final Map<String, INI> services = new ConcurrentHashMap<>();

    final Map<String, String> activationEnvironment = new ConcurrentHashMap<>(System.getenv());
    final Map<String, Process> servicePids = new ConcurrentHashMap<>();
    private final Map<String, Long> pendingActivation = new ConcurrentHashMap<>();

	private BusAddress clientAddress;

    private Path actualConfigurationPath;

    private Platform platform = Platform.get();

    private Thread watchThread;
	private PrintStream originalOut = System.out;

	public DBusDaemon() {
        names.put("org.freedesktop.DBus", null);
    }

    @Override
    public Integer call() throws Exception {
    	redirectOutput();

        level.ifPresent(lvl -> System.getProperty("org.slf4j.simpleLogger.defaultLogLevel", lvl.toString()));
        LOGGER = LoggerFactory.getLogger(DBusDaemon.class);

        var addr = JadbusAddress.processAddress(this.addr.orElseGet(this::defaultAddress));

        var address = BusAddress.of(addr);
        if (!address.hasParameter("listen")) {
            addr = addr + ",listen=true";
            address = BusAddress.of(addr);
        }
        if (!address.hasParameter("guid")) {
            addr = addr + ",guid=" + Util.genGUID();
            ;
            address = BusAddress.of(addr);
        }
        
        // Check the path
        if(address.hasParameter("path")) {
            var path = address.getParameterValue("path");
            path =  path.replace("\\", File.separator).replace("/", File.separator);
            
            address.removeParameter("path");
            addr = address.toString() + ",path=" + path;
            address = BusAddress.of(addr);
            
            var pathObj = Paths.get(path);
            var dir = pathObj.getParent();
            if(dir != null) {
                Files.createDirectories(dir);
            }
        }

        clientAddress = BusAddress.of(address).removeParameter("listen");
        activationEnvironment.put("DBUS_STARTER_ADDRESS", clientAddress.toString());

        // TODO On Windows/Mac we are more likely to be the system or session bus
        // activationEnvironment.put("DBUS_STARTER_BUS_TYPE", "system" || "session");

        // print address to stdout
        if (printaddress) {
        	originalOut.println(clientAddress.toString());
        }

        // print address to file
        addressfile.ifPresent(f -> saveFile(clientAddress.toString(), f, insecure));

        // print PID to file
        pidfile.ifPresent(f -> saveFile(String.valueOf(ProcessHandle.current().pid()), f, insecure));

        // print address to a preference key
        addresspref.ifPresent(prf -> {
            Preferences addrpref;
            String addrkey;
            if (prf.startsWith("user:")) {
                prf = prf.substring(5);
                if (prf.equals("default")) {
                    addrkey = "dbusAddress";
                    addrpref = Preferences.userNodeForPackage(DBusDaemon.class);
                } else {
                    int idx = prf.indexOf(':');
                    String node = idx == -1 ? prf : prf.substring(0, idx);
                    addrkey = idx == -1 ? "dbusAddress" : prf.substring(idx + 1);
                    addrpref = Preferences.userRoot().node(node);
                }
            } else {
                if (prf.startsWith("system:")) {
                    prf = prf.substring(7);
                }
                if (prf.equals("default")) {
                    addrkey = "dbusAddress";
                    addrpref = Preferences.systemNodeForPackage(DBusDaemon.class);
                } else {
                    int idx = prf.indexOf(':');
                    String node = idx == -1 ? prf : prf.substring(0, idx);
                    addrkey = idx == -1 ? "dbusAddress" : prf.substring(idx + 1);
                    addrpref = Preferences.systemRoot().node(node);
                }
            }

            addrpref.put(addrkey, clientAddress.toString());
        });

        // start the daemon
        LOGGER.info("Binding to {}", addr);

        LOGGER.debug("About to initialize transport on: {}", address);
        var transportPath = address.getParameterValue("path");
        transport = TransportBuilder.create(address).
                configure().
                    withAfterBindCallback(transport -> {
                        if(transportPath != null) {
                            setFilePermissions(Paths.get(transportPath), insecure);
                        }
                    }).
                    withAutoConnect(false).
                    configureSasl().
                    withAuthMode(authMode.orElse(SaslAuthMode.AUTH_ANONYMOUS)).
                        back().
                    back().
                build();


        actualConfigurationPath = configuration();
        reloadConfig();
        
        // watch for config changes
        var watchService = FileSystems.getDefault().newWatchService();
        var confDir = actualConfigurationPath.getParent();
        var servicesDir = getServicesDir();
        confDir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
        if(!confDir.equals(servicesDir)) {
            servicesDir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
        }
        watchThread =new Thread(() -> {
        	WatchKey key;
        	try {
	            while ((key = watchService.take()) != null) {
	                for (var event : key.pollEvents()) {
	                	try {
	                		var changedPath = (Path)event.context();
	                		if(changedPath.equals(actualConfigurationPath)) {
	    	                	if(event.kind().equals(StandardWatchEventKinds.ENTRY_DELETE)) {
	                    			LOGGER.error("Configuration file {} deleted!", changedPath);
	    	                	}
	                			reloadConfig();
	                		}
	                		else {
	                			synchronized(services) {
		                			if(event.context().toString().endsWith(".service")) {
		        	                	if(event.kind().equals(StandardWatchEventKinds.ENTRY_DELETE)) {
		        	                		var srvName = changedPath.toString().substring(0, changedPath.toString().length() - 8);
		                        			LOGGER.warn("Service {} deleted from {}", srvName, changedPath);
		                        			services.remove(srvName);
		                        			var prc = servicePids.get(srvName); 
		                        			if(prc != null) {
		                        				LOGGER.warn("{} is currently active, stopping now.");
		                        				/* TODO maybe need to be more forceful */
		                        				prc.destroy();
		                        			}
		        	                	}
		        	                	else {
											var serviceIni = INI.fromFile(servicesDir.resolve(changedPath));
		                                    var serviceSection = serviceIni.section("D-Bus Service");
		                                    var name = serviceSection.get("Name");
		                                    if(changedPath.getFileName().toString().equals(name + ".service")) {
			                                    var was = services.put(name, serviceIni);
			                                    if(was == null) {
			                                    	LOGGER.info("Added service {} from {}", name, changedPath);
			                                    }
			                                    else {
			                                    	LOGGER.info("Changed service {} from {}", name, changedPath);
			                                    }
		                                    }
		                                    else {
		                                    	LOGGER.warn("Ignoring {}. The service Name inside {} should be the same as the file name, less the .service extension.", changedPath, name);
		                                    }
		        	                	}
		                			}
		                			else {
		                				if(LOGGER.isTraceEnabled())
		                					LOGGER.trace("Other file changed in configuration directory or services. {}, {}", event.kind(), event.context());
		                			}
	                			}
	                		}
	                	}
	                	catch(Exception e) {
	                		LOGGER.error("Failed to handle file change.", e);
	                	}
	                }
	                key.reset();
	            }
        	}
        	catch(InterruptedException ie) {
        	}
        	
        }, "FileChangeMonitor");
        watchThread.setDaemon(true);
        watchThread.start();
       
        
        
//        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//            try {
//                transport.close();
//            } catch (IOException e) {
//            }
//            finally {
//                if(path != null) {
//                    try {
//                        Files.delete(Paths.get(path));
//                    } catch (IOException e) {
//                    }
//                }
//            }
//        }));

        thread = new Thread(this::runLoop, getClass().getSimpleName() + "-Thread");
        thread.start();
        // use tail-controlled loop so we at least try to get a client connection once
        do {
            try {
                LOGGER.debug("Begin listening to: {}", transport);
                TransportConnection s = transport.listen();
                addSock(s);
            } catch (AuthenticationException _ex) {
                LOGGER.error("Authentication failed", _ex);
            } catch (SocketClosedException _ex) {
                LOGGER.debug("Connection closed", _ex);
            }

        } while (isRunning());

        return 0;

    }

	@Override
    public void close() {
        run.set(false);
        if (!conns.isEmpty()) {
            // disconnect all remaining connections
            Set<ConnectionStruct> connections = new HashSet<>(conns.keySet());
            for (ConnectionStruct c : connections) {
                removeConnection(c);
            }
        }
        sender.terminate();
        if (transport != null) {
            LOGGER.debug("Terminating transport {}", transport);
            try {
                // shutdown listener
                transport.close();
            } catch (IOException _ex) {
                LOGGER.debug("Error closing transport", _ex);
            }
        }
        thread.interrupt();
        watchThread.interrupt();
    }

    public synchronized boolean isRunning() {
        return run.get();
    }

    boolean activate(String service) throws DBusException {
        // TODO synchronize somehow so only one connection at a time can start a
        // particular service
        LOGGER.info("Activating {}", service);
        synchronized(services) {
	        var srv = services.get(service);
			var srvSection = srv.section("D-Bus Service");
	        if (srvSection.contains("Exec")) {
	            var args = parseQuotedString(srvSection.get("Exec")).stream().map(this::processArg).toList();
	
	            LOGGER.info("Executing {}", String.join(" ", args));
	
	            var pb = new ProcessBuilder(args);
	            var dir = srvSection.getOr("Directory").map(File::new)
	                    .orElseGet(() -> new File(System.getProperty("user.home")));
				pb.directory(dir);
	
	            pb.environment().putAll(activationEnvironment);
	            
	            srv.sectionOr("Redirection").ifPresentOrElse(redir -> {
            		var stdOutOr = redir.getOr("StdOut");
					stdOutOr.ifPresentOrElse(stdout -> {
            			if(stdout.equalsIgnoreCase("$null") || stdout.equalsIgnoreCase("nul") || stdout.equalsIgnoreCase("/dev/null")) {
            				LOGGER.info("Directing stdout to /dev/null");
            				pb.redirectOutput(Redirect.DISCARD);
            			}
            			else {
            				LOGGER.info("Directing stdout to {}", stdout);
            				pb.redirectOutput(new File(dir, stdout));
            			}
            		}, () -> {
        				LOGGER.info("Directing stderr to parent");
            			pb.redirectOutput(Redirect.INHERIT);
            		});
            		redir.getOr("StdErr").ifPresentOrElse(stderr -> {
            			if(stderr.equalsIgnoreCase("stdout") || stderr.equals("&1")) {
            				LOGGER.info("Directing stderr to stdout");
        					pb.redirectErrorStream(true);
            			}
            			else if(stderr.equalsIgnoreCase("$null") || stderr.equalsIgnoreCase("nul") || stderr.equalsIgnoreCase("/dev/null")) {
            				LOGGER.info("Directing stderr to /dev/null");
            				pb.redirectError(Redirect.DISCARD);
            			}
            			else {
            				LOGGER.info("Directing stderr to {}", stderr);
            				pb.redirectError(new File(dir, stderr));
            			}
            		}, () -> {
        				LOGGER.info("Directing stderr to parent");
            			pb.redirectOutput(Redirect.INHERIT);
            		});
	            }, () -> pb.inheritIO());

	            try {
	                var process = pb.start();
	                process.onExit().thenAccept(prc -> {
	                	LOGGER.info("Activated service {} exited with {}", service, prc.exitValue());
	                	servicePids.remove(service);
	                });
	                servicePids.put(service, process);
	                return true;
	            } catch (IOException e) {
	                throw new DBusException("Failed to activate service. ", e);
	            }
	        } else {
	            throw new UnsupportedOperationException(
	                    MessageFormat.format("'Exec' directive missing in service {0}", service));
	        }
        }
    }

    void addSock(TransportConnection _s) throws IOException {
        LOGGER.debug("New Client");

        ConnectionStruct c = new ConnectionStruct(_s);
        DBusDaemonReaderThread r = new DBusDaemonReaderThread(this, c);
        conns.put(c, r);
        r.start();
    }

    String getId() {
        return configuration.getOr("id").map(str -> str.equals("auto") ? getDefaultId() : str)
                .orElseGet(() -> getDefaultId());
    }

    String getMachineId() {
        return configuration.getOr("machine-id").map(str -> str.equals("auto") ? getDefaultMachineId() : str)
                .orElseGet(() -> getDefaultMachineId());
    }

    long getPid(String unique) {
        var c = connsByUnique.get(unique);
        if (c == null || c.credentials == null) {
            return servicePids.get(unique).pid();
        }
        return c.getCredentials().getPid();
    }

    boolean isSystemBus() {
		return systemBus.orElseGet(OS::isAdministrator);
	}

    boolean releaseName(String _name, ConnectionStruct connStruct) {
        boolean exists = false;
        synchronized (names) {
            if (names.containsKey(_name) && names.get(_name).equals(connStruct)) {
                exists = names.remove(_name) != null;
            }
        }
        return exists;
    }

    void reloadConfig() {
    	LOGGER.info("Reloading configuration");
    	/* TODO stop any services that are now gone */
    	/* TODO check pending services */
    	synchronized(services) {
	    	services.clear();
	        if (Files.exists(actualConfigurationPath)) {
	            configuration = INI.fromFile(actualConfigurationPath);
	            var servicesDir = getServicesDir();
	            if (Files.exists(servicesDir) && Files.isDirectory(servicesDir)) {
	                try (var str = Files.newDirectoryStream(servicesDir)) {
	                    for (var servicePath : str) {
	                        var serviceIni = INI.fromFile(servicePath);
	                        var serviceSection = serviceIni.section("D-Bus Service");
	                        var name = serviceSection.get("Name");
                            if(servicePath.getFileName().toString().equals(name + ".service")) {
                            	services.put(name, serviceIni);
                            }
                            else {
                            	LOGGER.warn("Ignoring {}. The service Name inside {} should be the same as the file name, less the .service extension.", servicePath, name);                            	
                            }
	                    }
	                } catch (IOException ioe) {
	                    throw new UncheckedIOException(ioe);
	                }
	            } else {
	                LOGGER.warn("No services configuration directory at {} or is not a directory.", servicesDir);
	            }
	        } else {
	            LOGGER.warn("No configuration file at {}", configurationPath);
	        }
    	}
    }

    void removeConnection(ConnectionStruct _c) {

        DBusDaemonReaderThread oldThread = conns.remove(_c);
        if (_c.unique != null) {
            connsByUnique.remove(_c.unique);
        }

        if (oldThread != null) {
            LOGGER.debug("Terminating reader thread for {}", _c);
            oldThread.terminate();

            try {
                if (_c.connection != null) {
                    _c.connection.close();
                    LOGGER.debug("Terminated connection {}", _c.connection);
                }
            } catch (IOException _exIo) {
                LOGGER.debug("Error while closing socketchannel", _exIo);
            }
        }

        LOGGER.debug("Removing signal destination {}", _c);
        synchronized (sigrecips) {
            if (sigrecips.removeIf(e -> e.equals(_c))) {
                LOGGER.debug("Removed one or more signal destinations for {}", _c);
            }
        }

        LOGGER.debug("Removing name registration for {}", _c);
        synchronized (names) {
            List<String> toRemove = new ArrayList<>();

            // find connection by name
            for (String name : names.keySet()) {
                if (names.get(name) == _c) {
                    toRemove.add(name);
                }
            }

            // remove registered name and send signal to remaining connections
            for (String name : toRemove) {
                names.remove(name);
                try {
                    send(null, new NameOwnerChanged("/org/freedesktop/DBus", name, _c.unique, ""));
                } catch (DBusException _ex) {
                    LOGGER.debug("Unable to change owner", _ex);
                }
            }
        }

    }

    boolean requestName(String _name, ConnectionStruct connStruct) {
        boolean exists = false;
        synchronized (names) {
            if (!(exists = names.containsKey(_name))) {
                names.put(_name, connStruct);
            }
        }
        if (!exists) {
            pendingActivation.remove(_name);
        }
        return exists;

    }

    void send(ConnectionStruct _connStruct, Message _msg) {
        send(_connStruct, _msg, false);
    }

    void send(ConnectionStruct _connStruct, Message _msg, boolean _head) {

        // send to all connections
        if (_connStruct == null) {
            LOGGER.trace("Queuing message {} for all connections", _msg);
            for (ConnectionStruct d : conns.keySet()) {
                if (d.connection == null || d.connection.getChannel() == null
                        || !d.connection.getChannel().isConnected()) {
                    LOGGER.debug("Ignoring broadcast message for disconnected connection {}: {}", d.connection, _msg);
                } else {
                    if (_head) {
                        outqueue.addFirst(new Pair<>(_msg, new WeakReference<>(d)));
                    } else {
                        outqueue.addLast(new Pair<>(_msg, new WeakReference<>(d)));
                    }
                }
            }
        } else {
            LOGGER.trace("Queuing message {} for {}", _msg, _connStruct.unique);
            if (_head) {
                outqueue.addFirst(new Pair<>(_msg, new WeakReference<>(_connStruct)));
            } else {
                outqueue.addLast(new Pair<>(_msg, new WeakReference<>(_connStruct)));
            }
        }
    }

    void setUnique(ConnectionStruct connStruct, String unique) {
        connStruct.unique = unique;
        connsByUnique.put(unique, connStruct);
    }

    void updateActivationEnvironment(Map<String, String> _environment) {
		if(isSystemBus())
			throw new IllegalStateException("Update of activation environment not allowed on system bus.");
		activationEnvironment.putAll(_environment);
	}

    private void redirectOutput() {
		try {
	    	if(outputPath.isPresent()) {
			    var strm = new PrintStream(Files.newOutputStream(outputPath.get()));
	            System.setErr(strm);
	            System.setOut(strm);
	    	}
	    	else if(OS.isDeveloperWorkspace()) {
	    		return;
	    	}
	    	else  {
	    		var cmdOr = ProcessHandle.current().info().command();
	    		if(cmdOr.isPresent()) {
	    			var cmd = cmdOr.get();
	    			if(cmd.indexOf("java") == -1) {
	    				Path dir;
	    				if(OS.isAdministrator()) {
	    		    		dir = Paths.get(System.getProperty("user.dir")).resolve("logs");
	    				}
	    		    	else {
	    		    		dir = Paths.get(System.getProperty("user.home")).resolve(".jadbus");
	    		    	}
			    		Files.createDirectories(dir);
		    		    var strm = new PrintStream(Files.newOutputStream(dir.resolve("jadbus.log")));
		                System.setErr(strm);
		                System.setOut(strm);
	    			}
	    		}
	    	}
		}
		catch(IOException ioe) {
			System.err.println("Failed to setup output redirection. " + ioe.getMessage());
		}
    }

    private Path configuration() {
    	if(configurationPath.isPresent()) {
    		return configurationPath.get();
    	}
    	else if(OS.isDeveloperWorkspace()) {
    		return Paths.get("conf").resolve("session-bus.ini");
    	}
    	else  {
    		var cmdOr = ProcessHandle.current().info().command();
    		if(cmdOr.isPresent()) {
    			var cmd = cmdOr.get();
    			if(cmd.indexOf("java") == -1) {
    				/* Natively compiled, can derive configuration directory from this */
    				var path = Paths.get(cmd).toAbsolutePath();
    				if(OS.isAdministrator())
    	    			return path.getParent().resolve("system-bus.ini");
    		    	else {
    	    			return path.getParent().resolve("session-bus.ini");
    		    	}
    			}
    		}
   			throw new IllegalStateException("Cannot determine configuration directory, please supply -C option.");
    	}
    }

    private String defaultAddress() {
        if (unix && !tcp) {
            if (isSystemBus()) {
                var busAddress = BusAddress.of(TransportBuilder.createDynamicSession("UNIX", true));
                busAddress.removeParameter("path");
                var publicPath = JadbusAddress.systemBusPath();
                return busAddress.toString() + ",path=" + publicPath;
            } else {
            	if(OS.isWindows())
            		return "unix:path=%LOCALAPPDATA%\\DBus-Java\\%u";
            	else
            		return "unix:path=~/.jadbus/session-bus";
            }
        } else {
            return TransportBuilder.createDynamicSession("TCP", true);
        }
    }

    private String getDefaultId() {
        try {
            return Hexdump.toHex(MessageDigest.getInstance("MD5")
                    .digest((System.getProperty("user.name") + ":" + System.getProperty("user.dir")).getBytes()));
        } catch (NoSuchAlgorithmException _ex) {
            return this.hashCode() + "";
        }
    }

    private String getDefaultMachineId() {
        try {
            return Hexdump.toHex(
                    MessageDigest.getInstance("MD5").digest(InetAddress.getLocalHost().getHostName().getBytes()));
        } catch (NoSuchAlgorithmException | UnknownHostException _ex) {
            return this.hashCode() + "";
        }
    }

    private Path getServicesDir() {
		return configuration.getOr("services").map(p -> { 
			var a = Paths.get(p);
			if(a.isAbsolute())
				return a;
			else
				return actualConfigurationPath.getParent().resolve(p);
			}).orElseGet(() -> getServicesDirForConfigurationFile(actualConfigurationPath));
	}

    private Path getServicesDirForConfigurationFile(Path path) {
		return path.getParent().resolve(path.getFileName().toString().replace("bus", "services").replace(".ini", ".d"));
	}

    private String processArg(String arg) {
        var rnd = UUID.randomUUID().toString();
        arg = arg.replace("%%", rnd);
        arg = arg.replace("%A", clientAddress.toString());
        arg = arg.replace(rnd, "%%");
        return arg;
    }

    private void runLoop() {
        run.set(true);
        sender.start();

        while (isRunning()) {
            try {
                Pair<Message, WeakReference<ConnectionStruct>> pollFirst = inqueue.take();
                ConnectionStruct connectionStruct = pollFirst.second.get();
                if (connectionStruct != null) {
                    Message m = pollFirst.first;
                    logMessage("<inqueue> Got message {} from {}", m, connectionStruct.unique);
                    MessageFactory messageFactory = connectionStruct.connection.getMessageFactory();
                    // check if they have hello'd
                    if (null == connectionStruct.unique && (!(m instanceof MethodCall)
                            || !"org.freedesktop.DBus".equals(m.getDestination()) || !"Hello".equals(m.getName()))) {
                        send(connectionStruct,
                                messageFactory.createError("org.freedesktop.DBus", null,
                                        "org.freedesktop.DBus.Error.AccessDenied", m.getSerial(), "s",
                                        "You must send a Hello message"));
                    } else {
                        try {
                            if (null != connectionStruct.unique) {
                                m.setSource(connectionStruct.unique);
                                LOGGER.trace("Updated source to {}", connectionStruct.unique);
                            }
                        } catch (DBusException _ex) {
                            LOGGER.debug("Error setting source", _ex);
                            send(connectionStruct,
                                    messageFactory.createError("org.freedesktop.DBus", null,
                                            "org.freedesktop.DBus.Error.GeneralError", m.getSerial(), "s",
                                            "Sending message failed"));
                        }

                        if ("org.freedesktop.DBus".equals(m.getDestination())) {
                            dbusServer.handleMessage(connectionStruct, pollFirst.first);
                        } else {
                            if (m instanceof DBusSignal) {
                                List<ConnectionStruct> l;
                                synchronized (sigrecips) {
                                    l = new ArrayList<>(sigrecips);
                                }
                                List<ConnectionStruct> list = l;
                                for (ConnectionStruct d : list) {
                                    send(d, m);
                                }
                            } else {
                                ConnectionStruct dest = names.get(m.getDestination());

                                if (null == dest) {
                                    if (services.containsKey(m.getDestination()) && activate(m.getDestination())) {
                                        pendingActivation.put(m.getDestination(), System.currentTimeMillis());
                                        send(dest, m);
                                    } else {
                                        send(connectionStruct, messageFactory.createError("org.freedesktop.DBus", null,
                                                "org.freedesktop.DBus.Error.ServiceUnknown", m.getSerial(), "s",
                                                String.format("The name `%s' does not exist", m.getDestination())));
                                    }
                                } else {
                                    send(dest, m);
                                }
                            }
                        }
                    }
                }

            } catch (DBusException _ex) {
                LOGGER.debug("Error processing connection", _ex);
            } catch (InterruptedException _ex) {
                LOGGER.debug("Interrupted");
                close();
                thread.interrupt();
            }
        }

    }

    private void saveFile(String data, Path path, boolean insecure) {
        var file = path.toFile();
        file.deleteOnExit();
        try (var w = new PrintWriter(Files.newBufferedWriter(path))) {
            w.println(data);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        setFilePermissions(path, insecure);
    }

	private void setFilePermissions(Path path, boolean insecure) {
        try {
            if (insecure) {

                platform.openToEveryone(path);
            } else {
                if (isSystemBus()) {
                    platform.openToEveryone(path);
                } else {
                    platform.restrictToUser(path);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
