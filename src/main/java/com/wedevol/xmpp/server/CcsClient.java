package com.wedevol.xmpp.server;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.ReconnectionListener;
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
import com.wedevol.xmpp.util.BackOffStrategy;
import com.wedevol.xmpp.util.Util;

/**
 * Sample Smack implementation of a client for FCM Cloud Connection Server. Most of it has been taken more or less
 * verbatim from Google's documentation: https://firebase.google.com/docs/cloud-messaging/xmpp-server-ref
 */
public class CcsClient implements StanzaListener, ReconnectionListener, ConnectionListener {

	private static final Logger logger = Logger.getLogger(CcsClient.class.getName());

	private XMPPTCPConnection connection;
	private String apiKey = null;
	private boolean debuggable = false;
	private String username = null;
	private Boolean isConnectionDraining = false;
	private final Map<String, String> pendingMessages = new ConcurrentHashMap<>();
	
	public CcsClient(String projectId, String apiKey, boolean debuggable) {
		// Add FCM Packet Extension Provider
		ProviderManager.addExtensionProvider(Util.FCM_ELEMENT_NAME, Util.FCM_NAMESPACE,
				new ExtensionElementProvider<FcmPacketExtension>() {
					@Override
					public FcmPacketExtension parse(XmlPullParser parser, int initialDepth) throws XmlPullParserException, IOException, SmackException {
						final String json = parser.nextText();
						return new FcmPacketExtension(json);
					}
				});
		this.apiKey = apiKey;
		this.debuggable = debuggable;
		this.username = projectId + "@" + Util.FCM_SERVER_AUTH_CONNECTION;
	}

	/**
	 * Connects to FCM Cloud Connection Server using the supplied credentials
	 */
	public void connect() throws XMPPException, SmackException, IOException, InterruptedException, NoSuchAlgorithmException, KeyManagementException {
		logger.log(Level.INFO, "Initiating connection ...");
		
		isConnectionDraining = false; // Set connection draining to false when there is a new connection

		// create connection configuration
		XMPPTCPConnection.setUseStreamManagementResumptionDefault(true);
		XMPPTCPConnection.setUseStreamManagementDefault(true);
		
		final SSLContext sslContext = SSLContext.getInstance("TLS");
		sslContext.init(null, null, new SecureRandom());
		
		final XMPPTCPConnectionConfiguration.Builder config = XMPPTCPConnectionConfiguration.builder();
		logger.log(Level.INFO, "Connecting to the server ...");
		config.setXmppDomain("FCM XMPP Client Connection Server");
		config.setHost(Util.FCM_SERVER);
		config.setPort(Util.FCM_PORT);
		config.setSendPresence(false);
		config.setSecurityMode(SecurityMode.ifpossible);
		config.setDebuggerEnabled(debuggable); // launch a window with info about packets sent and received
		config.setCompressionEnabled(true);
		config.setSocketFactory(sslContext.getSocketFactory());
		config.setCustomSSLContext(sslContext);
		
		connection = new XMPPTCPConnection(config.build()); // Create the connection

		connection.connect(); // Connect

		// Enable automatic reconnection and add the listener (if not, remove the the listener, the interface and the override methods)
		ReconnectionManager.getInstanceFor(connection).enableAutomaticReconnection();
		ReconnectionManager.getInstanceFor(connection).addReconnectionListener(this);
		
		// Disable Roster at login (in XMPP the contact list is called a "roster")
		Roster.getInstanceFor(connection).setRosterLoadedAtLogin(false);
		
		// Security checks
		SASLAuthentication.unBlacklistSASLMechanism("PLAIN"); // FCM CCS requires a SASL PLAIN authentication mechanism using
		SASLAuthentication.blacklistSASLMechanism("DIGEST-MD5");
		logger.log(Level.INFO, "SASL PLAIN authentication enabled ? " + SASLAuthentication.isSaslMechanismRegistered("PLAIN"));
		logger.log(Level.INFO, "Is compression enabled ? " + connection.isUsingCompression());
		logger.log(Level.INFO, "Is the connection secure ? " + connection.isSecureConnection());

		// Handle connection errors
		connection.addConnectionListener(this);

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
	
	private void resendPendingMessages() {
		logger.log(Level.INFO, "Sending pending messages through the new connection.");
		logger.log(Level.INFO, "Pending messages size: " + pendingMessages.size());
		final Map<String, String> messagesToResend = new HashMap<>(pendingMessages);
		for (Map.Entry<String, String> message : messagesToResend.entrySet()) {
	        sendPacketBasic(message.getValue());
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
				// TODO: handle the delivery receipt. It is when a device confirms that it received a particular message.
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
		sendPacketBasic(ack);
		
		// 2. process and send message
		if (action.equals(Util.BACKEND_ACTION_ECHO)) { // send a message to the sender (user itself)
			final String messageId = Util.getUniqueMessageId();
			final String to = inMessage.getFrom();
			
			final CcsOutMessage outMessage = new CcsOutMessage(to, messageId, inMessage.getDataPayload());
			final String jsonRequest = MessageHelper.createJsonOutMessage(outMessage);
			sendPacket(messageId, jsonRequest);
		} else if (action.equals(Util.BACKEND_ACTION_MESSAGE)) { // send a message to the recipient
			this.handlePacketRecieved(inMessage);
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
			logger.log(Level.INFO, "Received null FCM Error Code.");
			return;
		}

		switch (errorCode) {
		case "INVALID_JSON":
			handleDeviceError(jsonMap);
			break;
		case "BAD_REGISTRATION":
			handleDeviceError(jsonMap);
			break;
		case "DEVICE_UNREGISTERED":
			handleDeviceError(jsonMap);
			break;
		case "BAD_ACK":
			handleDeviceError(jsonMap);
			break;
		case "SERVICE_UNAVAILABLE":
			handleServerFailure(jsonMap);
			break;
		case "INTERNAL_SERVER_ERROR":
			handleServerFailure(jsonMap);
			break;
		case "DEVICE_MESSAGE_RATE_EXCEEDED":
			handleDeviceError(jsonMap);
			break;
		case "TOPICS_MESSAGE_RATE_EXCEEDED":
			handleDeviceError(jsonMap);
			break;
		case "CONNECTION_DRAINING":
			logger.log(Level.INFO, "Connection draining from Nack ...");
			handleConnectionDraining();
			break;
		default:
			logger.log(Level.INFO, "Received unknown FCM Error Code: " + errorCode);
		}
	}

	/**
	 * Handles a Control message from FCM
	 */
	private void handleControlMessage(Map<String, Object> jsonMap) {
		final String controlType = (String) jsonMap.get("control_type");

		if (controlType.equals("CONNECTION_DRAINING")) {
			handleConnectionDraining();
		} else {
			logger.log(Level.INFO, "Received unknown FCM Control message: " + controlType);
		}
	}

	private void handleServerFailure(Map<String, Object> jsonMap) {
		// TODO: Resend the message
		logger.log(Level.INFO, "Server error: " + jsonMap.get("error") + " -> " + jsonMap.get("error_description"));

	}

	private void handleDeviceError(Map<String, Object> jsonMap) {
		logger.log(Level.INFO, "Device error: " + jsonMap.get("error") + " -> " + jsonMap.get("error_description"));
	}

	private void handleConnectionDraining() {
		logger.log(Level.INFO, "FCM Connection is draining!");
		isConnectionDraining = true;
	}
	
	/**
	 * Remove the message from the pending messages list
	 */
	private void removeMessageFromPendingMessages(Map<String, Object> jsonMap) {
		final String messageId = (String) jsonMap.get("message_id");
		if (messageId != null) {
			pendingMessages.remove(messageId); // Remove the messageId from the pending messages list
		}
	}
	
	private void onUserAuthentication() {
		isConnectionDraining = false;
		resendPendingMessages();
	}
	
	/**
	 * ========================================================================
	 * 
	 * API Helper methods:
	 * 
	 * These are methods that implementers can use, call, or override.
	 * 
	 * Help give the implementer more control/ customization.
	 * ========================================================================
	 */

	/**
	 * Note: This method is only called if {@link ReconnectionManager#isAutomaticReconnectEnabled()} returns true
	 */
	@Override
	public void reconnectionFailed(Exception e) {
		logger.log(Level.INFO, "Reconnection failed!", e.getMessage());
	}

	/**
	 * Note: This method is only called if {@link ReconnectionManager#isAutomaticReconnectEnabled()} returns true
	 */
	@Override
	public void reconnectingIn(int seconds) {
		logger.log(Level.INFO, "Reconnecting in " + seconds + " ...");
	}
	
	/**
	 * This method will be removed in Smack 4.3. Use {@link #connected(XMPPConnection)} or {@link #authenticated(XMPPConnection, boolean)} instead.
	 */
	@Deprecated
	@Override
	public void reconnectionSuccessful() {
		logger.log(Level.INFO, "Reconnection successful.");
	}

	@Override
	public void connectionClosedOnError(Exception e) {
		logger.log(Level.INFO, "Connection closed on error.");
	}

	@Override
	public void connectionClosed() {
		logger.log(Level.INFO, "Connection closed. The current connectionDraining flag is: " + isConnectionDraining);
		if (isConnectionDraining) {
			reconnect();
		}
	}

	@Override
	public void authenticated(XMPPConnection arg0, boolean arg1) {
		logger.log(Level.INFO, "User authenticated.");
		// This is the last step after a connection or reconnection
		onUserAuthentication();
	}

	@Override
	public void connected(XMPPConnection arg0) {
		logger.log(Level.INFO, "Connection established.");
	}
	
	/**
	 * Called when a custom packet has been received by the server. By default this method just resends the packet.
	 */
	 public void handlePacketRecieved(CcsInMessage inMessage) {
		 final String messageId = Util.getUniqueMessageId();
		 // TODO: it should be the user id to be retrieved from the data base
		 final String to = inMessage.getDataPayload().get(Util.PAYLOAD_ATTRIBUTE_RECIPIENT);
		    
		 // TODO: handle the data payload sent to the client device. Here, I just resend the incoming one.
		 final CcsOutMessage outMessage = new CcsOutMessage(to, messageId, inMessage.getDataPayload());
		 final String jsonRequest = MessageHelper.createJsonOutMessage(outMessage);
		 sendPacket(messageId, jsonRequest);
	 }
	
	/**
	 * Sends a downstream message to FCM
	 */
	public void sendPacket(String messageId, String jsonRequest) {
		pendingMessages.put(messageId, jsonRequest);
		if (!isConnectionDraining) {
			sendPacketBasic(jsonRequest);
		}
	}

	/**
	 * Sends a downstream message to FCM with back off strategy
	 */
	public void sendPacketBasic(String jsonRequest) {
		final Stanza request = new FcmPacketExtension(jsonRequest).toPacket();
		final BackOffStrategy backoff = new BackOffStrategy();
		while (backoff.shouldRetry()) {
			try {
				connection.sendStanza(request);
				backoff.doNotRetry();
			} catch (NotConnectedException | InterruptedException e) {
				logger.log(Level.INFO, "The packet could not be sent due to a connection problem. Packet: " + request.toXML());
				backoff.errorOccured();
			}
		}
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
	
	public synchronized void reconnect() {
		logger.log(Level.INFO, "Initiating reconnection ...");
		final BackOffStrategy backoff = new BackOffStrategy(5, 1000);
		while (backoff.shouldRetry()) {
			try {
				connect();
				resendPendingMessages();
				backoff.doNotRetry();
			} catch (XMPPException | SmackException | IOException | InterruptedException | KeyManagementException | NoSuchAlgorithmException e) {
				logger.log(Level.INFO, "The notifier server could not reconnect after the connection draining message.");
				backoff.errorOccured();
			}
		}
	}

}
