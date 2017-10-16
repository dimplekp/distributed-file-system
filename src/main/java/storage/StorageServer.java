package storage;

import java.io.*;
import java.net.*;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.*;

import common.Path;
import rmi.*;
import naming.*;

/**
 * Storage server.
 * 
 * <p>
 * Storage servers respond to client file access requests. The files accessible
 * through a storage server are those accessible under a given directory of the
 * local filesystem.
 */
public class StorageServer implements Storage, Command, Serializable {
	private static final long serialVersionUID = -8695381589816038827L;
	private int client_port;
	private int command_port;
	private File root;
	private Storage client_stub;
	private Command command_stub;
	Skeleton<Command> cmdSkeleton;
	Skeleton<Storage> strgSkeleton;

	/**
	 * Creates a storage server, given a directory on the local filesystem, and
	 * ports to use for the client and command interfaces.
	 * 
	 * <p>
	 * The ports may have to be specified if the storage server is running
	 * behind a firewall, and specific ports are open.
	 * 
	 * @param root
	 *            Directory on the local filesystem. The contents of this
	 *            directory will be accessible through the storage server.
	 * @param client_port
	 *            Port to use for the client interface, or zero if the system
	 *            should decide the port.
	 * @param command_port
	 *            Port to use for the command interface, or zero if the system
	 *            should decide the port.
	 * @throws NullPointerException
	 *             If <code>root</code> is <code>null</code>.
	 */
	public StorageServer(File root, int client_port, int command_port) {
		if (null == root) {
			throw new NullPointerException("root parameter is null");
		}
		this.root = root.getAbsoluteFile();
		this.client_port = client_port;
		this.command_port = command_port;
	}

	/**
	 * Creats a storage server, given a directory on the local filesystem.
	 * 
	 * <p>
	 * This constructor is equivalent to <code>StorageServer(root, 0, 0)</code>.
	 * The system picks the ports on which the interfaces are made available.
	 * 
	 * @param root
	 *            Directory on the local filesystem. The contents of this
	 *            directory will be accessible through the storage server.
	 * @throws NullPointerException
	 *             If <code>root</code> is <code>null</code>.
	 */
	public StorageServer(File root) {
		if (null == root) {
			throw new NullPointerException("root parameter is null");
		}
		this.root = root.getAbsoluteFile();
	}

	/**
	 * Starts the storage server and registers it with the given naming server.
	 * 
	 * @param hostname
	 *            The externally-routable hostname of the local host on which
	 *            the storage server is running. This is used to ensure that the
	 *            stub which is provided to the naming server by the
	 *            <code>start</code> method carries the externally visible
	 *            hostname or address of this storage server.
	 * @param naming_server
	 *            Remote interface for the naming server with which the storage
	 *            server is to register.
	 * @throws UnknownHostException
	 *             If a stub cannot be created for the storage server because a
	 *             valid address has not been assigned.
	 * @throws FileNotFoundException
	 *             If the directory with which the server was created does not
	 *             exist or is in fact a file.
	 * @throws RMIException
	 *             If the storage server cannot be started, or if it cannot be
	 *             registered.
	 */
	public synchronized void start(String hostname, Registration naming_server)
			throws RMIException, UnknownHostException, FileNotFoundException {
		// Gather this storage server files
		List<java.nio.file.Path> paths = new ArrayList<java.nio.file.Path>();
		Path[] files = null;
		try {
			paths.addAll(Files
					.find(Paths.get("/data"), Integer.MAX_VALUE,
							(filePath, fileAttr) -> fileAttr.isRegularFile())
					.collect(Collectors.toList()));
			paths.addAll(Files
					.find(Paths.get("/data"), Integer.MAX_VALUE,
							(filePath, fileAttr) -> fileAttr.isDirectory())
					.collect(Collectors.toList()));
			files = new Path[paths.size()];
			for (int i = 0; i < paths.size(); i++) {
				files[i] = new Path(paths.get(i).toString());
			}
		} catch (IOException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}

		// Initialize client and command stubs
		initializeStubs(hostname);

		// register using naming_server, client_stub, command_stub
		try {
			Path[] filesToDelete = naming_server.register(client_stub,
					command_stub, files);
			if (filesToDelete != null) {
				for (Path file : filesToDelete) {
					delete(file);
				}
			}
		} catch (RMIException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
		// Prune this storage server directories
		pruneLocalStorage(Paths.get("/data"));
	}

	/**
	 * Stops the storage server.
	 * 
	 * <p>
	 * The server should not be restarted.
	 */
	public void stop() {
		// TODO: NOTIFY NAMING SERVER
		cmdSkeleton.stop();
		strgSkeleton.stop();
	}

	/**
	 * Called when the storage server has shut down.
	 * 
	 * @param cause
	 *            The cause for the shutdown, if any, or <code>null</code> if
	 *            the server was shut down by the user's request.
	 */
	protected void stopped(Throwable cause) {
		System.out.println(cause.getMessage());
	}

	@Override
	public synchronized long size(Path file) throws FileNotFoundException {
		File f = new File(file.getPath());
		if (!f.exists()) {
			throw new FileNotFoundException("not implemented");
		}
		return f.length();
	}

	@Override
	public synchronized byte[] read(Path file, long offset, int length)
			throws IOException {
		File f = new File(file.getPath());
		if (!f.exists()) {
			throw new FileNotFoundException("not implemented");
		}
		DataInputStream in = new DataInputStream(new FileInputStream(f));
		byte fileContent[] = new byte[(int) f.length()];
		// REVISIT TYPE CAST
		in.read(fileContent, (int) offset, length);
		in.close();
		return fileContent;
	}

	@Override
	public synchronized void write(Path file, long offset, byte[] data)
			throws FileNotFoundException, IOException {
		File f = new File(file.getPath());
		if (!f.exists()) {
			throw new FileNotFoundException("not implemented");
		}
		DataOutputStream out = new DataOutputStream(new FileOutputStream(f));
		// REVISIT TYPE CAST
		out.write(data, (int) offset, data.length);
		out.flush();
		out.close();
	}

	@Override
	public synchronized boolean create(Path file) {
		java.nio.file.Path p = Paths.get(file.getPath());
		try {
			OutputStream out = Files.newOutputStream(p, CREATE_NEW);
			out.close();
			return true;
		} catch (IOException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public synchronized boolean delete(Path path) {
		// REVISIT WHEN DIRECTORY IS NOT DELETABLE?
		try {
			Files.delete(Paths.get(path.getPath()));
			return true;
		} catch (IOException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public synchronized boolean copy(Path file, Storage server)
			throws RMIException, FileNotFoundException, IOException {
		try {
			// REVISIT TYPE CAST
			byte data[] = server.read(file, 0, (int) server.size(file));
			java.nio.file.Path p = Paths.get(file.getPath());
			OutputStream out = new BufferedOutputStream(
					Files.newOutputStream(p, CREATE_NEW));
			out.write(data, 0, data.length);
			out.flush();
			out.close();
			return true;
		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	private void initializeStubs(String hostname) {
		// Command stub
		InetSocketAddress cmdAddr = new InetSocketAddress(hostname,
				StorageStubs.COMMAND_PORT);
		cmdSkeleton = new Skeleton<Command>(Command.class, this, cmdAddr);
		try {
			cmdSkeleton.start();
		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
		command_stub = StorageStubs.command(hostname);

		// Storage stub
		InetSocketAddress strgAddr = new InetSocketAddress(hostname,
				StorageStubs.STORAGE_PORT);
		strgSkeleton = new Skeleton<Storage>(Storage.class, this, strgAddr);
		try {
			strgSkeleton.start();
		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
		client_stub = StorageStubs.storage(hostname);
	}

	private void pruneLocalStorage(java.nio.file.Path dir) {
		try (DirectoryStream<java.nio.file.Path> stream = Files
				.newDirectoryStream(dir)) {
			System.out.println("Iterating : " + dir);
			Iterator<java.nio.file.Path> it = stream.iterator();
			if (!it.hasNext()) {
				// Directory is empty, delete it
				File currentDir = new File(dir.toString());
				currentDir.delete();
				return;
			}
			while (it.hasNext()) {
				java.nio.file.Path file = it.next();
				if (Files.isDirectory(file)) {
					pruneLocalStorage(file);
				}
			}
		} catch (IOException | DirectoryIteratorException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		}
	}

	public int getClient_port() {
		return client_port;
	}

	public void setClient_port(int client_port) {
		this.client_port = client_port;
	}

	public int getCommand_port() {
		return command_port;
	}

	public void setCommand_port(int command_port) {
		this.command_port = command_port;
	}

	public File getRoot() {
		return root;
	}

	public void setRoot(File root) {
		this.root = root;
	}
}
