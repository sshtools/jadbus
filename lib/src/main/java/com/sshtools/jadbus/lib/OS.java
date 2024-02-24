package com.sshtools.jadbus.lib;

public final class OS {

	public static final String OS_NAME = System.getProperty("os.name");


	public static boolean isAix() {
		return OS_NAME.toLowerCase().contains("aix");
	}

	public static boolean isBSD() {
		return OS_NAME.toLowerCase().contains("bsd");
	}

	public static boolean isLinux() {
		return OS_NAME.toLowerCase().contains("linux");
	}

	public static boolean isMacOs() {
		return OS_NAME.toLowerCase().contains("mac os");
	}

	public static boolean isSolaris() {
		return OS_NAME.toLowerCase().contains("sunos");
	}

	public static boolean isUnixLike() {
		return isLinux() || isMacOs() || isBSD() || isAix() || isSolaris();
	}

	public static boolean isWindows() {
		return OS_NAME.toLowerCase().contains("windows");
	}

	private OS() {
	}
}
