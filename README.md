# Jadbus

A DBus daemon for Windows, Mac OS and optionally Linux. While based on dbus-java, it is natively compiled and has a low
memory footprint and fast start-up, at least compared to when execute with a full runtime.  

*Linux of course already has it's own dbus broker. It is included here for demonstration purposes, and can co-exist with
Jadbus*

## Who Is This For?

 * You are developing a Linux application on Mac OS or Windows and want to support and test DBus functionality
 * You are porting a Linux application to Mac OS or Windows that makes us of DBus
 * You have a cross platform, multi-language application made up of multiple components, and you need 
   a well documented and supported RPC mechanism for all the bits to talk to each other.   

## Features

 * Installs a system bus service and a user bus on all platforms (in a similar way Linux does).
 * Supplies an environment variable in a users environment indicating location of bus.
 
## Caveats

At the moment, security of the dbus is just supplied by file permissions. Work is in progress
to tighten up the security a bit.

All applications share the bus(es), and this is perfectly safe for open desktop applications 
that wish to expose public APIs, but for more secure usages the moment, you will have to make your
own arrangements.

## TODO

 * More security
 * Configuration (using drop-in files like Linux)

## Announcements

### 1.0.0

 * Initial release

## Development

Find the source and submit pull requests at the GitHub project page, you know the drill.

## License

jadbus is made available under [GPL version 3](https://www.gnu.org/licenses/gpl-3.0.en.html). Other components under their respective licenses. 

