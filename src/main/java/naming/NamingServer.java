package naming;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import rmi.*;
import common.*;
import storage.*;

/**
 * Naming server.
 * 
 * <p>
 * Each instance of the filesystem is centered on a single naming server. The
 * naming server maintains the filesystem directory tree. It does not store any
 * file data - this is done by separate storage servers. The primary purpose of
 * the naming server is to map each file name (path) to the storage server which
 * hosts the file's contents.
 * 
 * <p>
 * The naming server provides two interfaces, <code>Service</code> and
 * <code>Registration</code>, which are accessible through RMI. Storage servers
 * use the <code>Registration</code> interface to inform the naming server of
 * their existence. Clients use the <code>Service</code> interface to perform
 * most filesystem operations. The documentation accompanying these interfaces
 * provides details on the methods supported.
 * 
 * <p>
 * Stubs for accessing the naming server must typically be created by directly
 * specifying the remote network address. To make this possible, the client and
 * registration interfaces are available at well-known ports defined in
 * <code>NamingStubs</code>.
 */
public class NamingServer implements Service, Registration, Serializable {

	private static final long serialVersionUID = -378262223963186055L;
	Set<Path> serverfiles;
	Map<String, List<Storage>> clientStubsForFile;
	Map<String, List<Command>> commandStubsForFile;
	Set<Map<String, Object>> storageServerStubs;
	Directory directoryTree;

	// Lock data structures
	// Good luck with that queue!! 😑 🙄 🔥
	Queue<Entry<Entry<Path, Boolean>, Condition>> waitQueue;
	Set<Path> exclusiveLockList;
	Set<Path> sharedLockList;

	String rootDirName = "data";

	// This lock is used to lock the set that contains the paths to lock
	// And not the path itself
	// It should be unlocked after path to lock is added to the lock sets
	final Lock lock;

	// Replication data structures
	private Map<Path, Integer> accessCount;

	// Threads
	NamingListener namingListeners;
	LockScheduler lockScheduler;
	ReplicationHandler replicationHandler;

	/**
	 * Creates the naming server object.
	 * 
	 * <p>
	 * The naming server is not started.
	 */
	public NamingServer() {
		this.serverfiles = new HashSet<Path>();
		this.clientStubsForFile = new HashMap<String, List<Storage>>();
		this.commandStubsForFile = new HashMap<String, List<Command>>();
		this.directoryTree = new Directory(rootDirName,
				new Hashtable<String, Directory>(), new HashSet<String>());
		this.storageServerStubs = new HashSet<Map<String, Object>>();
		this.waitQueue = new ConcurrentLinkedQueue<Entry<Entry<Path, Boolean>, Condition>>();
		this.exclusiveLockList = new HashSet<Path>();
		this.sharedLockList = new HashSet<Path>();
		this.lock = new ReentrantLock();
		this.accessCount = new HashMap<Path, Integer>();
		this.replicationHandler = new ReplicationHandler(this);
	}

	/**
	 * Starts the naming server.
	 * 
	 * <p>
	 * After this method is called, it is possible to access the client and
	 * registration interfaces of the naming server remotely.
	 * 
	 * @throws RMIException
	 *             If either of the two skeletons, for the client or
	 *             registration server interfaces, could not be started. The
	 *             user should not attempt to start the server again if an
	 *             exception occurs.
	 */
	public synchronized void start() throws RMIException {
		this.namingListeners = new NamingListener(this);
		this.lockScheduler = new LockScheduler(waitQueue, exclusiveLockList,
				sharedLockList, accessCount, replicationHandler);
		this.lockScheduler.start();
	}

	/**
	 * Stops the naming server.
	 * 
	 * <p>
	 * This method commands both the client and registration interface skeletons
	 * to stop. It attempts to interrupt as many of the threads that are
	 * executing naming server code as possible. After this method is called,
	 * the naming server is no longer accessible remotely. The naming server
	 * should not be restarted.
	 */
	public void stop() {
		this.lockScheduler.stopGracefully();
		this.namingListeners.stopListeners();
	}

	/**
	 * Indicates that the server has completely shut down.
	 * 
	 * <p>
	 * This method should be overridden for error reporting and application exit
	 * purposes. The default implementation does nothing.
	 * 
	 * @param cause
	 *            The cause for the shutdown, or <code>null</code> if the
	 *            shutdown was by explicit user request.
	 */
	protected void stopped(Throwable cause) {
	}

	@Override
	public void lock(Path path, boolean exclusive)
			throws FileNotFoundException {
		if (!serverfiles.contains(path)) {
			throw new FileNotFoundException();
		}
		final Condition notPushedToQueue = lock.newCondition();
		lock.lock();
		lock(path, exclusive, notPushedToQueue);
		try {
			if (exclusive) {
				while (exclusiveLockList.contains(path)) {
					notPushedToQueue.await();
				}
			} else {
				while (sharedLockList.contains(path)) {
					notPushedToQueue.await();
				}
			}
		} catch (InterruptedException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		} finally {
			lock.unlock();
		}
	}

	// Helper lock method
	private void lock(Path path, boolean exclusive,
			Condition notPushedToQueue) {
		synchronized (waitQueue) {
			waitQueue
					.add(new AbstractMap.SimpleEntry<Entry<Path, Boolean>, Condition>(
							new AbstractMap.SimpleEntry<Path, Boolean>(path,
									exclusive),
							notPushedToQueue));
		}
	}

	@Override
	public void unlock(Path path, boolean exclusive) {
		Path[] parentPathList = path.getParentPathList();
		if (exclusive) {
			if (!exclusiveLockList.contains(path)) {
				throw new IllegalArgumentException(
						path.getPath() + " does not exist");
			}
			exclusiveLockList.remove(path);
		} else {
			if (!sharedLockList.contains(path)) {
				throw new IllegalArgumentException(
						path.getPath() + " does not exist");
			}
			sharedLockList.remove(path);
		}

		for (int i = 0; i < parentPathList.length; i++) {
			if (!sharedLockList.contains(parentPathList[i])) {
				throw new IllegalArgumentException(
						parentPathList[i].getPath() + " does not exist");
			}
			sharedLockList.remove(parentPathList[i]);
		}
	}

	@Override
	public boolean isDirectory(Path path) throws FileNotFoundException {
		lock(path, false);
		java.nio.file.Path dir = Paths.get(path.getPath());

		// Validate root directory
		if (!dir.getName(0).toString().equals(directoryTree.getName())) {
			throw new FileNotFoundException("Invalid directory path");
		}

		int length = dir.getNameCount();
		if (length == 1) {
			// Since length is 1, the directory is root directory
			// Root directory is already validated above
			return true;
		}

		Hashtable<String, Directory> currentDirs = directoryTree.getSubDirs();
		for (int i = 0; i < length; i++) {

			if (!currentDirs.keySet().contains(dir.getName(i).toString())) {
				if (i == length - 1) {
					return false;
				} else {
					throw new FileNotFoundException("Invalid directory path");
				}
			}
			currentDirs = currentDirs.get(dir.getName(i).toString())
					.getSubDirs();
		}
		unlock(path, false);
		return true;
	}

	@Override
	public String[] list(Path directory) throws FileNotFoundException {
		lock(directory, false);
		java.nio.file.Path dir = Paths.get(directory.getPath());
		HashSet<String> files = null;
		// Validate root directory
		if (!dir.getName(0).toString().equals(directoryTree.getName())) {
			throw new FileNotFoundException("Invalid directory path");
		}

		int length = dir.getNameCount();
		if (length == 1) {
			// Since length is 1, the directory is root directory
			// Root directory is already validated above
			files = directoryTree.getFiles();
		}

		Hashtable<String, Directory> currentDirs = directoryTree.getSubDirs();
		for (int i = 0; i < length; i++) {

			if (!currentDirs.keySet().contains(dir.getName(i).toString())) {
				throw new FileNotFoundException("Invalid directory path");
			}
			if (i == length - 1) {
				files = currentDirs
						.get(dir.getName(dir.getNameCount() - 1).toString())
						.getFiles();
			}
			currentDirs = currentDirs.get(dir.getName(i).toString())
					.getSubDirs();
		}

		files.addAll(currentDirs.keySet());
		unlock(directory, false);
		return files.toArray(new String[files.size()]);
	}

	@Override
	public boolean createFile(Path file)
			throws RMIException, FileNotFoundException {
		lock(file, true);
		Command cmd_stub = null;
		Storage clnt_stub = null;
		synchronized (storageServerStubs) {
			java.nio.file.Path path = Paths.get(file.getPath());
			if (!serverfiles
					.contains(path.subpath(0, path.getNameCount() - 1))) {
				throw new FileNotFoundException(
						"Parent directory does not exist");
			}

			Long serverSize = 0L;
			Iterator<Map<String, Object>> iter = storageServerStubs.iterator();
			while (iter.hasNext()) {
				Map<String, Object> currObj = (Map<String, Object>) iter.next();
				Long currServerSize = (Long) currObj.get("size");
				if (currServerSize > serverSize) {
					serverSize = (Long) currObj.get("size");
					cmd_stub = (Command) currObj.get("command_stub");
					clnt_stub = (Storage) currObj.get("client_stub");
				}
			}
		}

		boolean isFileCreated = cmd_stub.create(file);

		if (isFileCreated) {
			HashSet<Path> fileSet = new HashSet<Path>();
			fileSet.add(file);
			serverfiles.add(file);
			addFilesToDirectoryTree(fileSet, clnt_stub, cmd_stub);
		}
		unlock(file, true);
		return isFileCreated;
	}

	@Override
	public boolean createDirectory(Path directory)
			throws FileNotFoundException, RMIException {
		lock(directory, true);
		Command cmd_stub = null;
		Storage clnt_stub = null;
		synchronized (storageServerStubs) {
			java.nio.file.Path path = Paths.get(directory.getPath());
			if (!serverfiles
					.contains(path.subpath(0, path.getNameCount() - 1))) {
				throw new FileNotFoundException(
						"Parent directory does not exist");
			}
			Long serverSize = 0L;
			Iterator<Map<String, Object>> iter = storageServerStubs.iterator();
			while (iter.hasNext()) {
				Map<String, Object> currObj = (Map<String, Object>) iter.next();
				Long currServerSize = (Long) currObj.get("size");
				if (currServerSize > serverSize) {
					serverSize = (Long) currObj.get("size");
					cmd_stub = (Command) currObj.get("command_stub");
					clnt_stub = (Storage) currObj.get("client_stub");
				}
			}
		}
		boolean isDirCreated = cmd_stub.create(directory);

		if (isDirCreated) {
			HashSet<Path> dirSet = new HashSet<Path>();
			dirSet.add(directory);
			serverfiles.add(directory);
			addDirectoriesToDirectoryTree(dirSet, clnt_stub, cmd_stub);
		}
		unlock(directory, true);
		return isDirCreated;
	}

	@Override
	public boolean delete(Path path) throws FileNotFoundException {
		lock(path, true);
		if (!serverfiles.contains(path.getPath())) {
			throw new FileNotFoundException();
		}
		boolean isDeleted = false;
		try {

			// Delete from all the nodes
			for (int i = 0; i < commandStubsForFile.get(path.getPath())
					.size(); i++) {
				isDeleted = commandStubsForFile.get(path.getPath()).get(i)
						.delete(path);
				if (!isDeleted) {
					return isDeleted;
				}
			}

			// To remove from naming server

			// Remove from directory tree
			boolean deletedFromTree = false;
			Directory parentDir = getParentDir(path);
			String fileName = Paths.get(path.getPath()).getFileName()
					.toString();
			for (String file : parentDir.getFiles()) {
				if (file.equals(fileName)) {
					parentDir.getFiles().remove(file);
					deletedFromTree = true;
					break;
				}
			}
			if (!deletedFromTree) {
				for (String dir : parentDir.getSubDirs().keySet()) {
					if (dir.equals(fileName)) {
						parentDir.getSubDirs().remove(fileName);
					}
				}
			}
			// Remove from file list
			serverfiles.remove(path.getPath());
			// Delete from stub command stub list
			commandStubsForFile.remove(path.getPath());
			// Delete from stub storage stub list
			clientStubsForFile.remove(path.getPath());
			isDeleted = true;
		} catch (RMIException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
			isDeleted = false;
		}
		unlock(path, true);
		return isDeleted;
	}

	@Override
	public Storage getStorage(Path file) throws FileNotFoundException {
		lock(file, false);
		if (!serverfiles.contains(file.getPath())) {
			throw new FileNotFoundException();
		}
		unlock(file, false);
		return clientStubsForFile.get(file.getPath())
				.get(new Random().nextInt(storageServerStubs.size() - 0));
	}

	// The method register is documented in Registration.java.
	@Override
	public Path[] register(Storage client_stub, Command command_stub,
			Path[] files) {
		// Store the stubs for server
		try {
			Map<String, Object> stubs = new HashMap<String, Object>();
			stubs.put("command_stub", command_stub);
			stubs.put("client_stub", client_stub);
			stubs.put("size", client_stub.size(new Path("/data")));
			storageServerStubs.add(stubs);
		} catch (FileNotFoundException | RMIException e) {
			System.out.println("Registration failed : " + e.getMessage());
			e.printStackTrace();
		}

		// Add files to directory tree
		synchronized (serverfiles) {
			serverfiles.addAll(Arrays.asList(files));
			boolean addToTreeStatus = addFilesToDirectoryTree(serverfiles,
					client_stub, command_stub);
			if (addToTreeStatus) {
				// TODO : log files that should be deleted and returned on
				// storage server
			}
		}
		return null;
	}

	public boolean addFilesToDirectoryTree(Set<Path> files, Storage client_stub,
			Command command_stub) {
		try {
			Directory currentDir;
			synchronized (directoryTree) {
				for (Path file : files) {
					currentDir = directoryTree;
					java.nio.file.Path path = Paths.get(file.getPath());

					if (!currentDir.getFiles()
							.contains(path.getFileName().toString())) {
						// If not present in root directory,
						// iterate over sub-directories for current file
						for (int i = 0; i < path.getNameCount() - 1; i++) {

							if (!currentDir.getSubDirs().keySet()
									.contains(path.getName(i).toString())) {
								Directory newDir = new Directory(
										path.getName(i).toString(),
										new Hashtable<String, Directory>(),
										new HashSet<String>());
								currentDir.getSubDirs().put(
										path.getName(i).toString(), newDir);

								String currSubPath = null;
								if (i == 0) {
									currSubPath = path.toString();
								} else {
									currSubPath = path.subpath(0, i).toString();
								}

								// Add storage stubs for the directory
								if (!commandStubsForFile
										.containsKey(currSubPath)) {
									commandStubsForFile.put(currSubPath,
											new ArrayList<Command>());
								}
								commandStubsForFile.get(currSubPath)
										.add(command_stub);

								if (!clientStubsForFile
										.containsKey(currSubPath)) {
									clientStubsForFile.put(currSubPath,
											new ArrayList<Storage>());
								}
								clientStubsForFile.get(currSubPath)
										.add(client_stub);

							}
							currentDir = currentDir.getSubDirs()
									.get(path.getName(i).toString());
						}
						currentDir.getFiles().add(path
								.getName(path.getNameCount() - 1).toString());
					}

					// Add storage stubs for the file
					if (!commandStubsForFile.containsKey(file.getPath())) {
						commandStubsForFile.put(file.getPath(),
								new ArrayList<Command>());
					}
					commandStubsForFile.get(file.getPath()).add(command_stub);

					if (!clientStubsForFile.containsKey(file.getPath())) {
						clientStubsForFile.put(file.getPath(),
								new ArrayList<Storage>());
					}
					clientStubsForFile.get(file.getPath()).add(client_stub);
				}
				return true;
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	private boolean addDirectoriesToDirectoryTree(HashSet<Path> dirSet,
			Storage client_stub, Command command_stub) {
		try {
			Directory currentDir;
			synchronized (directoryTree) {
				for (Path dir : dirSet) {
					currentDir = directoryTree;
					java.nio.file.Path path = Paths.get(dir.getPath());

					if (!currentDir.getSubDirs().keySet()
							.contains(path.getFileName().toString())) {
						// If not present in root directory,
						// iterate over sub-directories for current dir
						for (int i = 0; i < path.getNameCount() - 1; i++) {
							currentDir = currentDir.getSubDirs()
									.get(path.getName(i).toString());
						}
						currentDir.getFiles().add(path
								.getName(path.getNameCount() - 1).toString());
						currentDir.getSubDirs().put(
								path.getName(path.getNameCount() - 1)
										.toString(),
								new Directory(
										path.getName(path.getNameCount() - 1)
												.toString(),
										new Hashtable<String, Directory>(),
										new HashSet<String>()));
					}

					// Add storage stubs for the dir
					if (!commandStubsForFile.containsKey(dir.getPath())) {
						commandStubsForFile.put(dir.getPath(),
								new ArrayList<Command>());
					}
					commandStubsForFile.get(dir.getPath()).add(command_stub);

					if (!clientStubsForFile.containsKey(dir.getPath())) {
						clientStubsForFile.put(dir.getPath(),
								new ArrayList<Storage>());
					}
					clientStubsForFile.get(dir.getPath()).add(client_stub);
				}
				return true;
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
			return false;
		}
	}

	private Directory getParentDir(Path path) {
		java.nio.file.Path dir = Paths.get(path.getPath());
		int pathLength = dir.getNameCount();
		Hashtable<String, Directory> currentDirs = directoryTree.getSubDirs();
		for (int i = 0; i < pathLength; i++) {
			if (i == pathLength - 2) {
				return currentDirs.get(dir.getName(i).toString());
			}

			currentDirs = currentDirs.get(dir.getName(i).toString())
					.getSubDirs();
		}
		return null;
	}

	private Directory getDirFromPath(Path path) throws FileNotFoundException {
		java.nio.file.Path dir = Paths.get(path.getPath());
		int pathLength = dir.getNameCount();
		Hashtable<String, Directory> currentDirs = directoryTree.getSubDirs();
		for (int i = 0; i < pathLength; i++) {
			if (i == pathLength - 1) {
				return currentDirs.get(dir.getName(i).toString());
			}

			currentDirs = currentDirs.get(dir.getName(i).toString())
					.getSubDirs();
		}
		return null;
	}

}
