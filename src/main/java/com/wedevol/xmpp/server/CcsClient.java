package com.wedevol.xmpp.server;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocketFactory;

import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.roster.Roster;
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
import com.wedevol.xmpp.util.Util;

/**
 * Sample Smack implementation of a client for FCM Cloud Connection Server. Most of it has been taken more or less
 * verbatim from Google's documentation: https://firebase.google.com/docs/cloud-messaging/xmpp-server-ref
 */
public class CcsClient implements StanzaListener {

	private static final Logger logger = Logger.getLogger(CcsClient.class.getName());

	private static CcsClient ccsInstance = null;
	private XMPPTCPConnection connection;
	private String apiKey = null;
	private boolean debuggable = false;
	private String username = null;
	private Boolean isConnectionDraining = false;
	private final Map<String, String> pendingMessages = new ConcurrentHashMap<>();

	public static CcsClient getInstance() {
		if (ccsInstance == null) {
			throw new IllegalStateException("You have to prepare the client first");
		}
		return ccsInstance;
	}

	public static CcsClient prepareCcsClient(String projectId, String apiKey, boolean debuggable) {
		synchronized (CcsClient.class) {
			if (ccsInstance == null) {
				ccsInstance = new CcsClient(projectId, apiKey, debuggable);
			}
		}
		return ccsInstance;
	}
	
	private CcsClient() {
		// Add FCMPacketExtension
		ProviderManager.addExtensionProvider(Util.FCM_ELEMENT_NAME, Util.FCM_NAMESPACE,
				new ExtensionElementProvider<FcmPacketExtension>() {
					@Override
					public FcmPacketExtension parse(XmlPullParser parser, int initialDepth) 
							throws XmlPullParserException, IOException, SmackException {
						final String json = parser.nextText();
						return new FcmPacketExtension(json);
					}
				});
	}

	private CcsClient(String projectId, String apiKey, boolean debuggable) {
		this();
		this.apiKey = apiKey;
		this.debuggable = debuggable;
		this.username = projectId + "@" + Util.FCM_SERVER_CONNECTION;
	}

	/**
	 * Connects to FCM Cloud Connection Server using the supplied credentials
	 */
	public void connect() throws XMPPException, SmackException, IOException, InterruptedException {
		logger.log(Level.INFO, "Initiating connection ...");
		
		// Set connection draining to false when there is a new connection
		isConnectionDraining = false;

		final XMPPTCPConnectionConfiguration.Builder config = XMPPTCPConnectionConfiguration.builder();
		config.setXmppDomain("FCM XMPP Client Connection Server");
		config.setHost(Util.FCM_SERVER);
		config.setPort(Util.FCM_PORT);
		config.setSecurityMode(SecurityMode.ifpossible);
		config.setSendPresence(false);
		config.setSocketFactory(SSLSocketFactory.getDefault());
		config.setDebuggerEnabled(debuggable); // launch a window with info about packets sent and received

		// Create the connection
		connection = new XMPPTCPConnection(config.build());

		// Connect
		connection.connect();

		// Enable automatic reconnection
		ReconnectionManager.getInstanceFor(connection).enableAutomaticReconnection();
		
		// Disable Roster at login
		Roster.getInstanceFor(connection).setRosterLoadedAtLogin(false);
		
		// Check SASL authentication
		logger.log(Level.INFO, "SASL PLAIN authentication enabled? " + SASLAuthentication.isSaslMechanismRegistered("PLAIN"));
		SASLAuthentication.unBlacklistSASLMechanism("PLAIN");
		SASLAuthentication.blacklistSASLMechanism("DIGEST-MD5");

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
				logger.log(Level.INFO, "Connection closed. The current connectionDraining flag is: %s.", isConnectionDraining);
				if (isConnectionDraining) {
					reconnect();
				}
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

		// Handle incoming packets and reject messages that are not from FCM CCS
		connection.addAsyncStanzaListener(this, stanza -> stanza.hasExtension(Util.FCM_ELEMENT_NAME, Util.FCM_NAMESPACE));

		// Log all outgoing packets
		connection.addPacketInterceptor(stanza -> logger.log(Level.INFO, "Sent: " + stanza.toXML()), ForEveryStanza.INSTANCE);

		// Set the ping interval
		final PingManager pingManager = PingManager.getInstanceFor(connection);
		pingManager.setPingInterval(100);
		pingManager.registerPingFailedListener(() -> {
			logger.info("The ping failed, restarting the ping interval again ...");
			pingManager.setPingInterval(100);
		});

		connection.login(username, apiKey);
		logger.log(Level.INFO, "User logged in: " + username);
	}

	public synchronized void reconnect() {
		logger.log(Level.INFO, "Initiating reconnection ...");
		try {
			// TODO: use exponential back-off!
			connect();
			resendPendingMessages();
		} catch (XMPPException | SmackException | IOException | InterruptedException e) {
			logger.log(Level.INFO, "The notifier server could not reconnect after the connection draining message");
		}
	}
	
	private void resendPendingMessages() {
		logger.log(Level.INFO, "Sending pending messages through the new connection");
		logger.log(Level.INFO, "Pending messages size: {}", pendingMessages.size());
		final Map<String, String> messagesToResend = new HashMap<>(pendingMessages);
		for (Map.Entry<String, String> message : messagesToResend.entrySet()) {
	        sendPacket(message.getKey(), message.getValue());
	    }
	}

	/**
	 * Handle incoming messages
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void processStanza(Stanza packet) {
		logger.log(Level.INFO, "Received: " + packet.toXML());
		final FcmPacketExtension fcmPacket = (FcmPacketExtension) packet.getExtension(Util.FCM_NAMESPACE);
		final String json = fcmPacket.getJson();
		try {
			final Map<String, Object> jsonMap = (Map<String, Object>) JSONValue.parseWithException(json);
			final Object messageType = jsonMap.get("message_type");

			if (messageType == null) { // normal upstream message
				final CcsInMessage inMessage = MessageHelper.createCcsInMessage(jsonMap);
				handleUpstreamMessage(inMessage);
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
		// The custom 'action' payload attribute defines what the message action is about.
		final String action = inMessage.getDataPayload()
										.get(Util.PAYLOAD_ATTRIBUTE_ACTION);
		if (action == null) {
			throw new IllegalStateException("Action must not be null! Options: 'ECHO', 'MESSAGE'");
		}
		
		// 1. send ACK to FCM
		final String ack = MessageHelper.createJsonAck(inMessage.getFrom(), inMessage.getMessageId());
		sendPacket(ack);
		
		// 2. process and send message
		if (action.equals(Util.BACKEND_ACTION_ECHO)) { // send a message to the sender (user itself)
			final String messageId = Util.getUniqueMessageId();
			final String to = inMessage.getFrom();
			
			final CcsOutMessage outMessage = new CcsOutMessage(to, messageId, inMessage.getDataPayload());
			final String jsonRequest = MessageHelper.createJsonOutMessage(outMessage);
			sendPacket(messageId, jsonRequest);
		} else if (action.equals(Util.BACKEND_ACTION_MESSAGE)) { // send a message to the recipient
			final String messageId = Util.getUniqueMessageId();
			// TODO: it should be the user id to be retrieved from the data base
			final String to = inMessage.getDataPayload().get(Util.PAYLOAD_ATTRIBUTE_RECIPIENT);
		    
		    // TODO: handle the data payload sent to the client device. Here, I just resend the incoming one.
			final CcsOutMessage outMessage = new CcsOutMessage(to, messageId, inMessage.getDataPayload());
			final String jsonRequest = MessageHelper.createJsonOutMessage(outMessage);
		    sendPacket(messageId, jsonRequest);
		}
	}

	/**
	 * Handles an ACK message from FCM
	 */
	private void handleAckReceipt(Map<String, Object> jsonMap) {
		removeMessageFromPendingMessages(jsonMap);
	}

	/**
	 * Handles a NACK message from FCM
	 */
	private void handleNackReceipt(Map<String, Object> jsonMap) {
		removeMessageFromPendingMessages(jsonMap);
				
		final String errorCode = (String) jsonMap.get("error");

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
		final String controlType = (String) jsonMap.get("control_type");

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
		logger.log(Level.INFO, "Unrecoverable error: " + jsonMap.get("error") + " -> " + jsonMap.get("error_description"));
	}

	private void handleConnectionDrainingFailure() {
		logger.log(Level.INFO, "FCM Connection is draining!");
		isConnectionDraining = true;
	}
	
	/**
	 * Sends a downstream message to FCM
	 */
	public void sendPacket(String messageId, String jsonRequest) {
		pendingMessages.put(messageId, jsonRequest);
		if (!isConnectionDraining) {
			sendPacket(jsonRequest);
		}
	}

	/**
	 * Sends a downstream message to FCM with back off strategy
	 */
	public void sendPacket(String jsonRequest) {
		sendPacket(jsonRequest, 0);
	}
	
	/**
	 * Sends a downstream message to FCM with back off strategy
	 * 
	 * Delay must greater than 0
	 */
	public void sendPacket(String jsonRequest, final int delayPower) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				final Stanza request = new FcmPacketExtension(jsonRequest).toPacket();
				try {
					synchronized(this) {
						this.wait(1000 * (long)Math.pow(2, delayPower));
						connection.sendStanza(request);
					}
				} catch (NotConnectedException | InterruptedException e) {
					int nextAttempt = delayPower + 1;
					logger.log(Level.INFO, "The packet could not be sent due to a connection problem. Packet: {}", request.toXML());
					logger.log(Level.INFO, "Retrying in " + (int) Math.pow(2, nextAttempt) + " second(s).");
					sendPacket(jsonRequest, nextAttempt);
				}
			}
		}).start();
	}

	/**
	 * Sends a message to multiple recipients (list). Kind of like the old HTTP message with the list of regIds in the
	 * "registration_ids" field.
	 */
	public void sendBroadcast(CcsOutMessage outMessage, List<String> recipients) {
		final Map<String, Object> map = MessageHelper.createAttributeMap(outMessage);
		for (String toRegId : recipients) {
			final String messageId = Util.getUniqueMessageId();
			map.put("message_id", messageId);
			map.put("to", toRegId);
			final String jsonRequest = MessageHelper.createJsonMessage(map);
			sendPacket(messageId, jsonRequest);
		}
	}
	
	/**
	 * Remove the message from the pending messages list
	 */
	private void removeMessageFromPendingMessages(Map<String, Object> jsonMap) {
		// Get the message_id attribute
		final String messageId = (String) jsonMap.get("message_id");
		if (messageId != null) {
			// Remove the messageId from the pending messages list
			pendingMessages.remove(messageId);
		}
	}
}
