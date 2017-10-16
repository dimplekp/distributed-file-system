package naming;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class RegistrationListener extends Thread {
	Registration stub;
	boolean askedToStop = false;

	public RegistrationListener(Registration stub) {
		this.stub = stub;
	}

	public void run() {
		ServerSocket ss = null;
		try {

			ss = new ServerSocket(NamingStubs.REGISTRATION_PORT);

			while (!askedToStop) {
				synchronized (ss) {
					Socket s = ss.accept();
					new Thread(new Runnable() {

						@Override
						public void run() {
							try {
								DataInputStream din = new DataInputStream(
										s.getInputStream());
								ObjectOutputStream out = new ObjectOutputStream(
										s.getOutputStream());
								String msgFromStorageServer = "";
								while (true) {
									msgFromStorageServer = din.readUTF();
									if (msgFromStorageServer
											.equals("register me")) {
										out.writeObject(stub);
										out.flush();
										out.close();
										din.close();
										s.close();
										return;
									}
								}
							} catch (IOException e) {
								System.out.println(e.getMessage());
								e.printStackTrace();
							}
						}
					}).start();
				}
			}
		} catch (IOException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				ss.close();
			} catch (IOException e) {
				System.out.println(e.getMessage());
				e.printStackTrace();
			}
		}
	}

	public void stopGracefully() {
		askedToStop = true;
	}
}
