# Distributed File System With Dynamic Replication
Files are hosted remotely on one or more storage servers. Separately, a single naming server indexes the files. Files are obtained by locating the storage server through these indexes. Commonly accessed files are replicated automatically on multiple storage servers. Supported file operations are - reading, writing, creation, deletion, locking and size queries. Supported directory operations include - listing, creation and deletion. Read-write synchronization and consistency are carefully taken into consideration. Uses [this RMI][1] for remote services.
### Storage Servers
```sh
src/main/java/storage/StorageServer.java
```
### Naming Server
```sh
src/main/java/naming/NamingServer.java
```
 [1]: https://github.com/dimplekp/rmi
