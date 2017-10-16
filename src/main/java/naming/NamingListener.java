package naming;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import rmi.RMIException;
import rmi.RMIRegistry;
import rmi.Skeleton;

public class NamingListener {
	NamingServer namingServer;
	Registration regStub;
	Service servStub;
	Skeleton<Registration> regSkeleton;
	Skeleton<Service> servSkeleton;
	RMIRegistry namingRegistry;
	RegistrationListener regListener;

	public NamingListener(NamingServer namingServer) {
		this.namingServer = namingServer;
		initializeStubs();
		startRegistrationListener();
	}

	public void initializeStubs() {
		String hostname = "172.17.0.2";

		// Create registration stub
		InetSocketAddress regAddr = new InetSocketAddress(hostname,
				NamingStubs.REGISTRATION_PORT);
		regSkeleton = new Skeleton<Registration>(Registration.class,
				namingServer, regAddr);
		try {
			regSkeleton.start();
		} catch (RMIException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
		regStub = NamingStubs.registration(hostname);

		// Create service stub
		InetSocketAddress servAddr = new InetSocketAddress(hostname,
				NamingStubs.SERVICE_PORT);
		servSkeleton = new Skeleton<Service>(Service.class, namingServer,
				servAddr);
		try {
			servSkeleton.start();
		} catch (Exception e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
		servStub = NamingStubs.service(hostname);
	}

	public void startRegistrationListener() {
		regListener = new RegistrationListener(regStub);
		regListener.start();
	}

	public void stopListeners() {
		namingRegistry.closeRegistry();
		regListener.stopGracefully();
		regSkeleton.stop();
		servSkeleton.stop();
	}
}
