package storage;

import java.net.InetSocketAddress;

import rmi.Stub;

/**
 * Default port numbers for the storage server and convenience methods for
 * making storage server stubs.
 */
public abstract class StorageStubs {

	/** Default storage server command port. */
	public static final int COMMAND_PORT = 5000;
	/** Default storage server storage service port. */
	public static final int STORAGE_PORT = 5001;

	/**
	 * Returns a stub for a storage server command interface.
	 * 
	 * @param hostname
	 *            Storage server hostname.
	 * @param port
	 *            Command interface port.
	 */
	public static Command command(String hostname, int port) {
		InetSocketAddress address = new InetSocketAddress(hostname, port);
		return Stub.create(Command.class, address);
	}

	/**
	 * Returns a stub for a storage server command interface.
	 * 
	 * <p>
	 * The default port is used.
	 * 
	 * @param hostname
	 *            Storage server hostname.
	 */
	public static Command command(String hostname) {
		return command(hostname, COMMAND_PORT);
	}

	/**
	 * Returns a stub for a storage server client service interface.
	 * 
	 * @param hostname
	 *            Storage server hostname.
	 * @param port
	 *            Client service interface port.
	 */
	public static Storage storage(String hostname, int port) {
		InetSocketAddress address = new InetSocketAddress(hostname, port);
		return Stub.create(Storage.class, address);
	}

	/**
	 * Returns a stub for a storage server client service interface.
	 * 
	 * <p>
	 * The default port is used.
	 * 
	 * @param hostname
	 *            Storage server hostname.
	 */
	public static Storage storage(String hostname) {
		return storage(hostname, STORAGE_PORT);
	}

}
