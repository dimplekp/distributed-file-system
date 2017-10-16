package mytest;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;

import common.Path;
import naming.NamingServer;

public class TestNamingServer {
	public static void main(String[] args) throws IOException {

		HashSet<Path> fileList = new HashSet<Path>();
		fileList.add(new Path("/data/file1"));
		fileList.add(new Path("/data/files/file2"));
		fileList.add(new Path("/data/xoxo/whaaaaat/no"));
		fileList.add(new Path("/data/files/bollywood"));
		fileList.add(new Path("/data/xoxo/files/tvf/pr"));

		NamingServer ns = new NamingServer();

		System.out.println("Add status : "
				+ ns.addFilesToDirectoryTree(fileList, null, null));

		System.out.println(
				"is Directory : " + ns.isDirectory(new Path("/data/files/xx")));

		System.out.println(
				"Files : " + Arrays.asList(ns.list(new Path("/data/files"))));
	}
}
