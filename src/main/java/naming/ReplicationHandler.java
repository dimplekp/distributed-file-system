package naming;

import java.util.Queue;

import common.Path;

public class ReplicationHandler extends Thread {
	private NamingServer namingServer;
	private Queue<Path> fileQueue;

	public ReplicationHandler(NamingServer namingServer) {
		this.namingServer = namingServer;
	}

	public void run() {
		while (true) {
			if (!fileQueue.isEmpty()) {
				Path fileToReplicate = fileQueue.poll();
			}
		}
	}

	public void replicateFile(Path path) {
		fileQueue.add(path);
	}
}
