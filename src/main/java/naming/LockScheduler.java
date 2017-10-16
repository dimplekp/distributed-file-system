package naming;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.locks.Condition;

import common.Path;

public class LockScheduler extends Thread {

	private Queue<Entry<Entry<Path, Boolean>, Condition>> waitQueue;
	private Set<Path> exclusiveLockList;
	private Set<Path> sharedLockList;
	private int currentWaitQueueSize = 0;
	private Map<Path, Integer> accessCount;
	private boolean askedToStopThread = false;
	ReplicationHandler replicationHandler;

	public LockScheduler(
			Queue<Entry<Entry<Path, Boolean>, Condition>> waitQueue,
			Set<Path> exclusiveLockList, Set<Path> sharedLockList,
			Map<Path, Integer> accessCount,
			ReplicationHandler replicationHandler) {
		this.waitQueue = waitQueue;
		this.exclusiveLockList = exclusiveLockList;
		this.sharedLockList = sharedLockList;
		this.currentWaitQueueSize = waitQueue.size();
		this.accessCount = accessCount;
		this.replicationHandler = replicationHandler;
	}

	public void run() {
		while (!askedToStopThread) {
			if (waitQueue.size() > currentWaitQueueSize) {
				Entry<Entry<Path, Boolean>, Condition> poppedLockRequest = waitQueue
						.poll();
				Path path = poppedLockRequest.getKey().getKey();
				boolean exclusive = poppedLockRequest.getKey().getValue();
				Condition conditionVariable = poppedLockRequest.getValue();

				Path[] parentPathList = path.getParentPathList();

				// Parent directories will always only need shared lock
				for (int i = 0; i < parentPathList.length; i++) {
					while (sharedLockList.contains(parentPathList[i])) {
						// Wait until path is removed from the shared lock set
					}
					sharedLockList.add(parentPathList[i]);
				}

				if (exclusive) {
					while (exclusiveLockList.contains(path)) {
						// Wait until path is removed from the exclusive lock
						// set
					}
					exclusiveLockList.add(path);
				} else {
					while (sharedLockList.contains(path)) {
						// Wait until path is removed from the shared lock set
					}
					sharedLockList.add(path);
					if (accessCount.containsKey(path)) {
						int newCount = accessCount.get(path) + 1;
						accessCount.put(path, newCount);
						if (newCount % 20 == 0) {
							replicationHandler.replicateFile(path);
						}
					} else {
						accessCount.put(path, 1);
					}
				}
				// signal path is locked
				conditionVariable.signal();
			}
			currentWaitQueueSize = waitQueue.size();
		}
	}

	public void stopGracefully() {
		askedToStopThread = true;
	}

}
