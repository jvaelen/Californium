package ch.inf.vs.californium.network;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;

import ch.inf.vs.californium.MessageDeliverer;
import ch.inf.vs.californium.Server;
import ch.inf.vs.californium.coap.EmptyMessage;
import ch.inf.vs.californium.coap.Request;
import ch.inf.vs.californium.coap.Response;
import ch.inf.vs.californium.network.connector.Connector;
import ch.inf.vs.californium.network.connector.UDPConnector;
import ch.inf.vs.californium.network.layer.CoapStack;
import ch.inf.vs.californium.network.serializer.DataParser;
import ch.inf.vs.californium.network.serializer.Serializer;

/**
 * A CoAP Endpoint is is identified by transport layer multiplexing information
 * that can include a UDP port number and a security association.
 * (draft-ietf-core-coap-14: 1.2)
 * <p>
 * TODO: A litlle more detailed...
 */
public class Endpoint {

	private final static Logger LOGGER = Logger.getLogger(Server.class.getName());
	
	private final EndpointAddress address;
	private final CoapStack coapstack;
	private final Connector connector;
	private final NetworkConfig config;
	
	private ScheduledExecutorService executor;
	private boolean started;
	
	private List<EndpointObserver> observers = new ArrayList<>(0);
	private List<MessageIntercepter> interceptors = new ArrayList<>(0); // TODO: encapsulate

	// TODO: Use a thread-local DataSerializer
	private Serializer serializer;
	private Matcher matcher;
	private StackBottomImpl handler;
	
	// TODO: These are too many constructors! Make a Builder or something.
	
	public Endpoint() {
		this(0);
	}
	
	public Endpoint(int port) {
		this(null, port);
	}
	
	public Endpoint(int port, NetworkConfig config) {
		this(null, port, config);
	}
	
	public Endpoint(InetAddress address, int port) {
		this(new EndpointAddress(address, port));
	}
	
	public Endpoint(InetAddress address, int port, NetworkConfig config) {
		this(new EndpointAddress(address, port), config);
	}
	
	public Endpoint(EndpointAddress address) {
		this(address, new NetworkConfig());
	}
	
	public Endpoint(EndpointAddress address, NetworkConfig config) {
		this(new UDPConnector(address), address, config);
	}
	
	public Endpoint(Connector connector, EndpointAddress address, NetworkConfig config) {
		this.connector = connector;
		this.address = address;
		this.config = config;
		this.handler = new StackBottomImpl();
		this.serializer = new Serializer();
		this.matcher = new Matcher(handler);
		
		coapstack = new CoapStack(this, config, handler);
		connector.setRawDataReceiver(handler); // connector delivers bytes to CoAP stack
	}
	
	public void start() {
		if (started) {
			LOGGER.info("Endpoint for "+getAddress()+" hsa already started");
			return;
		}
		if (executor == null) {
			LOGGER.info("Endpoint "+toString()+" has no executer yet to start. Creates default single-threaded executor.");
			setExecutor(Executors.newSingleThreadScheduledExecutor());
		}
		
		try {
			LOGGER.info("Start endpoint for address "+getAddress());
			connector.start();
			EndpointManager.getEndpointManager().registerEndpoint(this);
			started = true;
			for (EndpointObserver obs:observers)
				obs.started(this);
			
		} catch (IOException e) {
			stop();
			throw new RuntimeException(e);
		}
	}
	
	public void stop() {
		if (!started) {
			LOGGER.info("Endpoint for address "+getAddress()+" is already stopped");
		} else {
			LOGGER.info("Stop endpoint for address "+getAddress());
			started = false;
			EndpointManager.getEndpointManager().unregisterEndpoint(this);
			connector.stop();
			for (EndpointObserver obs:observers)
				obs.stopped(this);
			matcher.clear();
		}
	}
	
	public void destroy() {
		LOGGER.info("Destroy endpoint for address "+getAddress());
		if (started)
			stop();
		connector.destroy();
		for (EndpointObserver obs:observers)
			obs.destroyed(this);
	}
	
	public boolean isStarted() {
		return started;
	}
	
	public void setExecutor(ScheduledExecutorService executor) {
		this.executor = executor;
		this.coapstack.setExecutor(executor);
	}
	
	public void addObserver(EndpointObserver obs) {
		observers.add(obs);
	}
	
	public void removeObserver(EndpointObserver obs) {
		observers.remove(obs);
	}
	
	public void addInterceptor(MessageIntercepter interceptor) {
		interceptors.add(interceptor);
	}
	
	public void removeInterceptor(MessageIntercepter interceptor) {
		interceptors.remove(interceptor);
	}
	
	public List<MessageIntercepter> getInterceptors() {
		return new ArrayList<>(interceptors);
	}
	
	public void sendRequest(final Request request) {
		executor.execute(new Runnable() {
			public void run() {
				try {
					LOGGER.info("Endpoint sends request: "+request);
					coapstack.sendRequest(request);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	// TODO: Maybe we can do this a little nicer (e.g. call-back object)
	public void sendResponse(final Exchange exchange, final Response response) {
		// TODO: This should only be done if the executing thread is not already a thread pool thread
		executor.execute(new Runnable() {
			public void run() {
				try {
					LOGGER.info("Endpoint sends response back: "+response);
					coapstack.sendResponse(exchange, response);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	public void sendEmptyMessage(final Exchange exchange, final EmptyMessage message) {
		executor.execute(new Runnable() {
			public void run() {
				try {
					LOGGER.info("Endpoint sends empty message: "+message);
					coapstack.sendEmptyMessage(exchange, message);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	public void setMessageDeliverer(MessageDeliverer deliverer) {
		coapstack.setDeliverer(deliverer);
	}

	public EndpointAddress getAddress() {
		return address;
	}

	public NetworkConfig getConfig() {
		return config;
	}

	private class StackBottomImpl implements StackBottom, RawDataChannel {

		@Override
		public void sendRequest(Exchange exchange, Request request) {
			matcher.sendRequest(exchange, request);
			for (MessageIntercepter interceptor:interceptors)
				interceptor.receiveRequest(request);
			LOGGER.info("<== " + address.getPort() + " send request "+request);
			connector.send(serializer.serialize(request));
		}

		@Override
		public void sendResponse(Exchange exchange, Response response) {
			matcher.sendResponse(exchange, response);
			for (MessageIntercepter interceptor:interceptors)
				interceptor.sendResponse(response);
			LOGGER.info("<== " + address.getPort() + " send response "+response);
			connector.send(serializer.serialize(response));
		}

		@Override
		public void sendEmptyMessage(Exchange exchange, EmptyMessage message) {
			matcher.sendEmptyMessage(exchange, message);
			for (MessageIntercepter interceptor:interceptors)
				interceptor.sendEmptyMessage(message);
			LOGGER.info("<== " + address.getPort() + " send empty message "+message);
			connector.send(serializer.serialize(message));
		}

		@Override
		public void receiveData(final RawData raw) {
			if (raw.getAddress() == null)
				throw new NullPointerException();
			if (raw.getPort() == 0)
				throw new NullPointerException();
			
			Runnable task = new Runnable() {
				public void run() {
					DataParser parser = new DataParser(raw.getBytes()); // TODO: ThreadLocal<T>
					if (parser.isRequest()) {
						Request request = parser.parseRequest();
						request.setSource(raw.getAddress());
						request.setSourcePort(raw.getPort());
						LOGGER.info("==> " + address.getPort() + " receive request "+request);
						for (MessageIntercepter interceptor:interceptors)
							interceptor.receiveRequest(request);
						Exchange exchange = matcher.receiveRequest(request);
						if (exchange != null)
							coapstack.receiveRequest(exchange, request);
						
					} else if (parser.isResponse()) {
						Response response = parser.parseResponse();
						response.setSource(raw.getAddress());
						response.setSourcePort(raw.getPort());
						LOGGER.info("==> " + address.getPort() + " receive response "+response);
						for (MessageIntercepter interceptor:interceptors)
							interceptor.receiveResponse(response);
						Exchange exchange = matcher.receiveResponse(response);
						if (exchange != null)
							coapstack.receiveResponse(exchange, response);
						
					} else {
						EmptyMessage message = parser.parseEmptyMessage();
						message.setSource(raw.getAddress());
						message.setSourcePort(raw.getPort());
						LOGGER.info("==> " + address.getPort() + " receive empty message "+message);
						for (MessageIntercepter interceptor:interceptors)
							interceptor.receiveEmptyMessage(message);
						Exchange exchange = matcher.receiveEmptyMessage(message);
						if (exchange != null)
							coapstack.receiveEmptyMessage(exchange, message);
					}
				}
			};
			executeTask(task);
		}

		@Override
		public void sendData(RawData msg) {
			connector.send(msg);
		}
	}
	
	private void executeTask(final Runnable task) {
		executor.execute(new Runnable() {
			public void run() {
				try {
					task.run();
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		});
	}
}