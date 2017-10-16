package mytest;

import java.io.File;
import java.util.Iterator;

import common.Path;

public class TestPath {
	public static void main(String[] args) {
		Path root = new Path();
		System.out.println("root : " + root);

		Path newpath = new Path(root, "file1.txt");
		System.out.println("newpath : " + newpath);

		Path newpath2 = new Path("/docker/data/file1.txt");
		System.out.println("newpath2 : " + newpath2);

		System.out.print("Iterator : ");
		Iterator<String> it = newpath2.iterator();
		while (it.hasNext()) {
			System.out.print(it.next() + ", ");
		}

		System.out.println("\nisRoot : " + root.isRoot());

		System.out.println("last : " + new Path("/docker/data/file/").last());
		
		System.out.println("parent : " + newpath.parent());

		System.out.println("isSubpath : " + newpath2.isSubpath(root));

		System.out.println(
				"toFile : " + newpath2.toFile(new File(root.getPath())));

		Path newpath3 = new Path("/xyz/fff/cc.py");
		System.out.println("compareTo : " + newpath3.compareTo(newpath2));

		System.out.println("equals : " + newpath2.equals(newpath3));
		
		System.out.println("hashCode : " + newpath.hashCode());
		
		System.out.println("toString : " + root);
	}
}
