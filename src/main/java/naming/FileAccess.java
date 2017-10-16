package naming;

import common.Path;

public class FileAccess implements Comparable<FileAccess> {
	Path path;
	Integer accessCount;

	public FileAccess(Path path, Integer accessCount) {
		this.path = path;
		this.accessCount = accessCount;
	}

	public Path getPath() {
		return path;
	}

	public void setPath(Path path) {
		this.path = path;
	}

	public Integer getAccessCount() {
		return accessCount;
	}

	public void setAccessCount(Integer accessCount) {
		this.accessCount = accessCount;
	}

	@Override
	public int compareTo(FileAccess fileAccessObject) {
		if (accessCount > fileAccessObject.accessCount) {
			return 1;
		} else if (accessCount < fileAccessObject.accessCount) {
			return -1;
		} else {
			return 0;
		}
	}

}
