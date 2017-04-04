package com.wedevol.xmpp.server;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocketFactory;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.sm.predicates.ForEveryStanza;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.ping.PingManager;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import com.wedevol.xmpp.bean.CcsInMessage;
import com.wedevol.xmpp.bean.CcsOutMessage;
import com.wedevol.xmpp.service.PayloadProcessor;
import com.wedevol.xmpp.util.Util;

/**
 * Sample Smack implementation of a client for FCM Cloud Connection Server. Most of it has been taken more or less
 * verbatim from Google's documentation: https://firebase.google.com/docs/cloud-messaging/xmpp-server-ref
 */
public class CcsClient implements StanzaListener {

	private static final Logger logger = Logger.getLogger(CcsClient.class.getName());

	private static CcsClient sInstance = null;
	private XMPPTCPConnection connection;
	private String mApiKey = null;
	private boolean mDebuggable = false;
	private String fcmServerUsername = null;

	public static CcsClient getInstance() {
		if (sInstance == null) {
			throw new IllegalStateException("You have to prepare the client first");
		}
		return sInstance;
	}

	public static CcsClient prepareClient(String projectId, String apiKey, boolean debuggable) {
		synchronized (CcsClient.class) {
			if (sInstance == null) {
				sInstance = new CcsClient(projectId, apiKey, debuggable);
			}
		}
		return sInstance;
	}

	private CcsClient(String projectId, String apiKey, boolean debuggable) {
		this();
		mApiKey = apiKey;
		mDebuggable = debuggable;
		fcmServerUsername = projectId + "@" + Util.FCM_SERVER_CONNECTION;
	}

	private CcsClient() {
		// Add GcmPacketExtension
		ProviderManager.addExtensionProvider(Util.FCM_ELEMENT_NAME, Util.FCM_NAMESPACE,
				new ExtensionElementProvider<GcmPacketExtension>() {
					@Override
					public GcmPacketExtension parse(XmlPullParser parser, int initialDepth)
							throws XmlPullParserException, IOException, SmackException {
						String json = parser.nextText();
						return new GcmPacketExtension(json);
					}
				});
	}

	/**
	 * Connects to FCM Cloud Connection Server using the supplied credentials
	 */
	public void connect() throws XMPPException, SmackException, IOException, InterruptedException {
		XMPPTCPConnection.setUseStreamManagementResumptionDefault(true);
		XMPPTCPConnection.setUseStreamManagementDefault(true);

		XMPPTCPConnectionConfiguration.Builder config = XMPPTCPConnectionConfiguration.builder();
		config.setXmppDomain("FCM XMPP Client Connection Server");
		config.setHost(Util.FCM_SERVER);
		config.setPort(Util.FCM_PORT);
		config.setSecurityMode(ConnectionConfiguration.SecurityMode.ifpossible);
		config.setSendPresence(false);
		config.setSocketFactory(SSLSocketFactory.getDefault());
		// Launch a window with info about packets sent and received
		config.setDebuggerEnabled(mDebuggable);

		// Create the connection
		connection = new XMPPTCPConnection(config.build());

		// Connect
		connection.connect();

		// Enable automatic reconnection
		ReconnectionManager.getInstanceFor(connection)
							.enableAutomaticReconnection();

		// Handle reconnection and connection errors
		connection.addConnectionListener(new ConnectionListener() {

			@Override
			public void reconnectionSuccessful() {
				logger.log(Level.INFO, "Reconnection successful ...");
				// TODO: handle the reconnecting successful
			}

			@Override
			public void reconnectionFailed(Exception e) {
				logger.log(Level.INFO, "Reconnection failed: ", e.getMessage());
				// TODO: handle the reconnection failed
			}

			@Override
			public void reconnectingIn(int seconds) {
				logger.log(Level.INFO, "Reconnecting in %d secs", seconds);
				// TODO: handle the reconnecting in
			}

			@Override
			public void connectionClosedOnError(Exception e) {
				logger.log(Level.INFO, "Connection closed on error");
				// TODO: handle the connection closed on error
			}

			@Override
			public void connectionClosed() {
				logger.log(Level.INFO, "Connection closed");
				// TODO: handle the connection closed
			}

			@Override
			public void authenticated(XMPPConnection arg0, boolean arg1) {
				logger.log(Level.INFO, "User authenticated");
				// TODO: handle the authentication
			}

			@Override
			public void connected(XMPPConnection arg0) {
				logger.log(Level.INFO, "Connection established");
				// TODO: handle the connection
			}
		});

		// Handle incoming packets (the class implements the StanzaListener)
		connection.addAsyncStanzaListener(this, new StanzaFilter() {
			@Override
			public boolean accept(Stanza stanza) {
				return stanza.hasExtension(Util.FCM_ELEMENT_NAME, Util.FCM_NAMESPACE);
			}
		});

		// Log all outgoing packets
		connection.addPacketInterceptor(stanza -> logger.log(Level.INFO, "Sent: {}", stanza.toXML()),
				ForEveryStanza.INSTANCE);

		// Set the ping interval
		final PingManager pingManager = PingManager.getInstanceFor(connection);
		pingManager.setPingInterval(100);
		pingManager.registerPingFailedListener(() -> {
			logger.info("The ping failed, restarting the ping interval again ...");
			pingManager.setPingInterval(100);
		});

		connection.login(fcmServerUsername, mApiKey);
		logger.log(Level.INFO, "Logged in: " + fcmServerUsername);
	}

	public synchronized void reconnect() {
		// Try to connect again using exponential back-off!
	}

	/**
	 * Handles incoming messages
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void processStanza(Stanza packet) {
		logger.log(Level.INFO, "Received: " + packet.toXML());
		GcmPacketExtension gcmPacket = (GcmPacketExtension) packet.getExtension(Util.FCM_NAMESPACE);
		String json = gcmPacket.getJson();
		try {
			Map<String, Object> jsonMap = (Map<String, Object>) JSONValue.parseWithException(json);
			Object messageType = jsonMap.get("message_type");

			if (messageType == null) {
				CcsInMessage inMessage = MessageHelper.createCcsInMessage(jsonMap);
				handleUpstreamMessage(inMessage); // normal upstream message
				return;
			}

			switch (messageType.toString()) {
			case "ack":
				handleAckReceipt(jsonMap);
				break;
			case "nack":
				handleNackReceipt(jsonMap);
				break;
			case "receipt":
				handleDeliveryReceipt(jsonMap);
				break;
			case "control":
				handleControlMessage(jsonMap);
				break;
			default:
				logger.log(Level.INFO, "Received unknown FCM message type: " + messageType.toString());
			}
		} catch (ParseException e) {
			logger.log(Level.INFO, "Error parsing JSON: " + json, e.getMessage());
		}

	}

	/**
	 * Handles an upstream message from a device client through FCM
	 */
	private void handleUpstreamMessage(CcsInMessage inMessage) {
		final String action = inMessage.getDataPayload()
										.get(Util.PAYLOAD_ATTRIBUTE_ACTION);
		if (action != null) {
			PayloadProcessor processor = ProcessorFactory.getProcessor(action);
			processor.handleMessage(inMessage);
		}

		// Send ACK to FCM
		String ack = MessageHelper.createJsonAck(inMessage.getFrom(), inMessage.getMessageId());
		send(ack);
	}

	/**
	 * Handles an ACK message from FCM
	 */
	private void handleAckReceipt(Map<String, Object> jsonMap) {
		// TODO: handle the ACK in the proper way
	}

	/**
	 * Handles a NACK message from FCM
	 */
	private void handleNackReceipt(Map<String, Object> jsonMap) {
		String errorCode = (String) jsonMap.get("error");

		if (errorCode == null) {
			logger.log(Level.INFO, "Received null FCM Error Code");
			return;
		}

		switch (errorCode) {
		case "INVALID_JSON":
			handleUnrecoverableFailure(jsonMap);
			break;
		case "BAD_REGISTRATION":
			handleUnrecoverableFailure(jsonMap);
			break;
		case "DEVICE_UNREGISTERED":
			handleUnrecoverableFailure(jsonMap);
			break;
		case "BAD_ACK":
			handleUnrecoverableFailure(jsonMap);
			break;
		case "SERVICE_UNAVAILABLE":
			handleServerFailure(jsonMap);
			break;
		case "INTERNAL_SERVER_ERROR":
			handleServerFailure(jsonMap);
			break;
		case "DEVICE_MESSAGE_RATE_EXCEEDED":
			handleUnrecoverableFailure(jsonMap);
			break;
		case "TOPICS_MESSAGE_RATE_EXCEEDED":
			handleUnrecoverableFailure(jsonMap);
			break;
		case "CONNECTION_DRAINING":
			handleConnectionDrainingFailure();
			break;
		default:
			logger.log(Level.INFO, "Received unknown FCM Error Code: " + errorCode);
		}
	}

	/**
	 * Handles a Delivery Receipt message from FCM (when a device confirms that it received a particular message)
	 */
	private void handleDeliveryReceipt(Map<String, Object> jsonMap) {
		// TODO: handle the delivery receipt
	}

	/**
	 * Handles a Control message from FCM
	 */
	private void handleControlMessage(Map<String, Object> jsonMap) {
		// TODO: handle the control message
		String controlType = (String) jsonMap.get("control_type");

		if (controlType.equals("CONNECTION_DRAINING")) {
			handleConnectionDrainingFailure();
		} else {
			logger.log(Level.INFO, "Received unknown FCM Control message: " + controlType);
		}
	}

	private void handleServerFailure(Map<String, Object> jsonMap) {
		// TODO: Resend the message
		logger.log(Level.INFO, "Server error: " + jsonMap.get("error") + " -> " + jsonMap.get("error_description"));

	}

	private void handleUnrecoverableFailure(Map<String, Object> jsonMap) {
		// TODO: handle the unrecoverable failure
		logger.log(Level.INFO,
				"Unrecoverable error: " + jsonMap.get("error") + " -> " + jsonMap.get("error_description"));
	}

	private void handleConnectionDrainingFailure() {
		// TODO: handle the connection draining failure. Force reconnect?
		logger.log(Level.INFO, "FCM Connection is draining! Initiating reconnection ...");
	}

	/**
	 * Sends a downstream message to FCM
	 */
	public void send(String jsonRequest) {
		// TODO: Resend the message using exponential back-off!
		Stanza request = new GcmPacketExtension(jsonRequest).toPacket();
		try {
			connection.sendStanza(request);
		} catch (NotConnectedException | InterruptedException e) {
			logger.log(Level.INFO, "The packet could not be sent due to a connection problem. Packet: {}", request.toXML());
		}
	}

	/**
	 * Sends a message to multiple recipients (list). Kind of like the old HTTP message with the list of regIds in the
	 * "registration_ids" field.
	 */
	public void sendBroadcast(CcsOutMessage outMessage, List<String> recipients) {
		Map<String, Object> map = MessageHelper.createAttributeMap(outMessage);
		for (String toRegId : recipients) {
			String messageId = Util.getUniqueMessageId();
			map.put("message_id", messageId);
			map.put("to", toRegId);
			String jsonRequest = MessageHelper.createJsonMessage(map);
			send(jsonRequest);
		}
	}
}
