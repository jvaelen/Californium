package ch.ethz.inf.vs.californium.observe;

import java.util.concurrent.ConcurrentHashMap;

import ch.ethz.inf.vs.californium.network.EndpointAddress;


// TODO: find a better name... how about ObserveObserver -.-
public class ObserveManager {

	private final ConcurrentHashMap<EndpointAddress, ObservingEndpoint> endpoints;
	
	public ObserveManager() {
		endpoints = new ConcurrentHashMap<EndpointAddress, ObservingEndpoint>();
	}
	
	public ObservingEndpoint findObservingEndpoint(EndpointAddress address) {
		ObservingEndpoint ep = endpoints.get(address);
		if (ep == null)
			ep = createObservingEndpoint(address);
		return ep;
	}
	
	public ObservingEndpoint getObservingEndpoint(EndpointAddress address) {
		return endpoints.get(address);
	}
	
	private ObservingEndpoint createObservingEndpoint(EndpointAddress address) {
		ObservingEndpoint ep = new ObservingEndpoint(address);
		
		// Make sure, there is exactly one ep with the specified address (atomic creation)
		ObservingEndpoint previous = endpoints.putIfAbsent(address, ep);
		if (previous != null) {
			return previous; // and forget ep again
		} else {
			return ep;
		}
	}
	
}
