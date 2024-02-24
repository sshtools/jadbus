package com.sshtools.jadbus.tools;

import com.sshtools.jaul.AppCategory;
import com.sshtools.jaul.AppRegistry;
import com.sshtools.jaul.ArtifactVersion;
import com.sshtools.jaul.JaulApp;
import com.sshtools.jaul.UpdateDescriptor.MediaType;
import com.sshtools.jaul.UpdateService;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "jaul", mixinStandardHelpOptions = true, description = "Register or de-register to and from the Jaul update system", subcommands = {
        Manager.Register.class, Manager.Deregister.class, Manager.Update.class })
@JaulApp(id = Manager.TOOLBOX_APP_ID, category = AppCategory.SERVICE, updaterId = "54", updatesUrl = "https://sshtools-public.s3.eu-west-1.amazonaws.com/jadbus/${phase}/updates.xml")
public class Manager implements Callable<Integer> {

    final static String TOOLBOX_APP_ID = "com.sshtools.Jadbus";

    @Override
    public final Integer call() throws Exception {
        throw new IllegalArgumentException("Missing sub-command");
    }

    @Command(name = "register", mixinStandardHelpOptions = true, description = "Register")
    public final static class Register implements Callable<Integer> {

        @Option(names = { "--packaging" }, description = "Type of package to register as")
        private Optional<MediaType> packaging;

        @Override
        public final Integer call() throws Exception {
            AppRegistry.get().register(Manager.class, packaging.orElse(MediaType.INSTALLER));
            return 0;
        }
    }

    @Command(name = "deregister", mixinStandardHelpOptions = true, description = "Register")
    public final static class Deregister implements Callable<Integer> {

        @Override
        public final Integer call() throws Exception {
            AppRegistry.get().deregister(Manager.class);
            return 0;
        }
    }

    @Command(name = "update", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Update the client.")
    public final static class Update implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Option(names = { "y", "yes" })
        private boolean yes;

        @Option(names = { "c", "check" })
        private boolean checkOnly;

        @Override
        public Integer call() throws Exception {
            var console = System.console();
            var writer = console.writer();
            
            var updateService = UpdateService.autoConsoleUpdateService(
                    Preferences.userNodeForPackage(Manager.class), 
                    Optional.empty(), 
                    Optional.of(AppRegistry.get().get(Manager.class)),
                    ArtifactVersion.getVersion("com.sshtools", "jadbus-tools"),  
                    Executors.newSingleThreadScheduledExecutor()
            );
            
            updateService.checkForUpdate();
            if (updateService.isNeedsUpdating()) {
                if (checkOnly) {
                    writer.println(
                            String.format("Version %s available.", updateService.getAvailableVersion()));
                    console.flush();
                    return 0;
                } else if (!yes) {
                    String answer = console.readLine("Version %s available. Update? (Y)/N: ",
                            updateService.getAvailableVersion()).toLowerCase();
                    if (!answer.equals("") && !answer.equals("y") && !answer.equals("yes")) {
                        writer.println("Cancelled");
                        console.flush();
                        return 1;
                    }
                }
            } else {
                writer.println("You are on the latest version.");
                console.flush();
                return 3;
            }

            updateService.update();
            return 0;
        }
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new Manager()).execute(args));
    }
}
