package naming;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

public class Directory {

	private String name;
	private Hashtable<String, Directory> subDirs;
	private HashSet<String> files;

	public Directory(String name, Hashtable<String, Directory> subDirs,
			HashSet<String> files) {
		this.name = name;
		this.subDirs = subDirs;
		this.files = files;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Hashtable<String, Directory> getSubDirs() {
		return subDirs;
	}

	public void setSubDirs(Hashtable<String, Directory> subDirs) {
		this.subDirs = subDirs;
	}

	public HashSet<String> getFiles() {
		return files;
	}

	public void setFiles(HashSet<String> files) {
		this.files = files;
	}

}
