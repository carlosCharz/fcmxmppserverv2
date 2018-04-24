package com.wedevol.xmpp.server;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
import org.jivesoftware.smackx.ping.PingFailedListener;
import org.jivesoftware.smackx.ping.PingManager;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import com.wedevol.xmpp.bean.CcsInMessage;
import com.wedevol.xmpp.bean.CcsOutMessage;
import com.wedevol.xmpp.util.BackOffStrategy;
import com.wedevol.xmpp.util.MessageMapper;
import com.wedevol.xmpp.util.Util;

/**
 * Class that connects to FCM Cloud Connection Server and handles stanzas (ACK, NACK, upstream,
 * downstream). Sample Smack implementation of a client for FCM Cloud Connection Server. Most of it
 * has been taken more or less verbatim from Google's documentation: <a href=
 * "https://firebase.google.com/docs/cloud-messaging/xmpp-server-ref">https://firebase.google.com/docs/cloud-messaging/xmpp-server-ref</a>
 * 
 * @author Charz++
 */
public class CcsClient implements StanzaListener, ReconnectionListener, ConnectionListener, PingFailedListener {

  protected static final Logger logger = LoggerFactory.getLogger(CcsClient.class);

  private XMPPTCPConnection xmppConn;
  private String apiKey = null;
  private boolean debuggable = false;
  private String username = null;
  private Boolean isConnectionDraining = false;
  private final Map<String, String> syncMessages = new ConcurrentHashMap<>(); // messages to sync ack and nack
  private final Map<String, String> pendingMessages = new ConcurrentHashMap<>(); // messages from backoff failures

  /**
   * Public constructor for the CCS Client
   * 
   * @param projectId
   * @param apiKey
   * @param debuggable
   */

  public CcsClient(String projectId, String apiKey, boolean debuggable) {
    // Add FCM Packet Extension Provider
    ProviderManager.addExtensionProvider(Util.FCM_ELEMENT_NAME, Util.FCM_NAMESPACE,
        new ExtensionElementProvider<FcmPacketExtension>() {
          @Override
          public FcmPacketExtension parse(XmlPullParser parser, int initialDepth)
              throws XmlPullParserException, IOException, SmackException {
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
  public void connect() throws XMPPException, SmackException, IOException, InterruptedException,
      NoSuchAlgorithmException, KeyManagementException {
    logger.info("Initiating connection ...");

    isConnectionDraining = false; // Set connection draining to false when there is a new connection

    // create connection configuration
    XMPPTCPConnection.setUseStreamManagementResumptionDefault(true);
    XMPPTCPConnection.setUseStreamManagementDefault(true);

    final SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, null, new SecureRandom());

    final XMPPTCPConnectionConfiguration.Builder config = XMPPTCPConnectionConfiguration.builder();
    logger.info("Connecting to the server ...");
    config.setXmppDomain("FCM XMPP Client Connection Server");
    config.setHost(Util.FCM_SERVER);
    config.setPort(Util.FCM_PORT);
    config.setSendPresence(false);
    config.setSecurityMode(SecurityMode.ifpossible);
    config.setDebuggerEnabled(debuggable); // launch a window with info about packets sent and received
    config.setCompressionEnabled(true);
    config.setSocketFactory(sslContext.getSocketFactory());
    config.setCustomSSLContext(sslContext);

    xmppConn = new XMPPTCPConnection(config.build()); // Create the connection

    xmppConn.connect(); // Connect

    // Enable automatic reconnection and add the listener (if not, remove the the listener, the
    // interface and the override methods)
    ReconnectionManager.getInstanceFor(xmppConn).enableAutomaticReconnection();
    ReconnectionManager.getInstanceFor(xmppConn).addReconnectionListener(this);

    // Disable Roster at login (in XMPP the contact list is called a "roster")
    Roster.getInstanceFor(xmppConn).setRosterLoadedAtLogin(false);

    // Security checks
    SASLAuthentication.unBlacklistSASLMechanism("PLAIN"); // FCM CCS requires a SASL PLAIN authentication mechanism
    SASLAuthentication.blacklistSASLMechanism("DIGEST-MD5");
    logger.info("SASL PLAIN authentication enabled ? {}", SASLAuthentication.isSaslMechanismRegistered("PLAIN"));
    logger.info("Is compression enabled ? {}", xmppConn.isUsingCompression());
    logger.info("Is the connection secure ? {}", xmppConn.isSecureConnection());

    // Handle connection errors
    xmppConn.addConnectionListener(this);

    // Handle incoming packets and reject messages that are not from FCM CCS
    xmppConn.addAsyncStanzaListener(this, stanza -> stanza.hasExtension(Util.FCM_ELEMENT_NAME, Util.FCM_NAMESPACE));

    // Log all outgoing packets
    xmppConn.addPacketInterceptor(stanza -> logger.info("Sent: {}", stanza.toXML()), ForEveryStanza.INSTANCE);

    // Set the ping interval
    final PingManager pingManager = PingManager.getInstanceFor(xmppConn);
    pingManager.setPingInterval(100);
    pingManager.registerPingFailedListener(this);

    xmppConn.login(username, apiKey);
    logger.info("User logged in: {}", username);
  }

  private void sendPendingMessages() {
    logger.info("Sending pending messages through the new connection.");
    logger.info("Pending messages size: {}", pendingMessages.size());
    final Map<String, String> messagesToResend = new HashMap<>(pendingMessages);
    for (Map.Entry<String, String> message : messagesToResend.entrySet()) {
      pendingMessages.remove(message.getKey());
      sendDownstreamMessage(message.getKey(), message.getValue());
    }
  }

  private void sendPendingForeverSyncMessages() {
    logger.info("Sending pending forever sync messages ...");
    logger.info("Sync messages size: {}", syncMessages.size());
    final Map<String, String> messagesToResend = new HashMap<>(syncMessages);
    for (Map.Entry<String, String> message : messagesToResend.entrySet()) {
      syncMessages.remove(message.getKey());
      sendDownstreamMessage(message.getKey(), message.getValue());
    }
  }

  /**
   * Handle incoming messages
   */
  @SuppressWarnings("unchecked")
  @Override
  public void processStanza(Stanza packet) {
    logger.info("Processing packet in thread {} - {}", Thread.currentThread().getName(),
        Thread.currentThread().getId());
    logger.info("Received: {}", packet.toXML());
    final FcmPacketExtension fcmPacket = (FcmPacketExtension) packet.getExtension(Util.FCM_NAMESPACE);
    final String json = fcmPacket.getJson();
    try {
      final Map<String, Object> jsonMap = (Map<String, Object>) JSONValue.parseWithException(json);
      final Object messageType = jsonMap.get("message_type");

      if (messageType == null) { // normal upstream message
        final CcsInMessage inMessage = MessageMapper.createCcsInMessage(jsonMap);
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
          // TODO: handle the delivery receipt when a device confirms that it received a particular message.
          break;
        case "control":
          handleControlMessage(jsonMap);
          break;
        default:
          logger.info("Received unknown FCM message type: {}", messageType.toString());
      }
    } catch (ParseException e) {
      logger.info("Error parsing JSON: {}. Error: {}", json, e.getMessage());
    }

  }

  /**
   * Handles an upstream message from a device client through FCM
   */
  private void handleUpstreamMessage(CcsInMessage inMessage) {
    // The custom 'action' payload attribute defines what the message action is about.
    final String action = inMessage.getDataPayload().get(Util.PAYLOAD_ATTRIBUTE_ACTION);
    if (action == null) {
      throw new IllegalStateException("Action must not be null! Options: 'ECHO', 'MESSAGE'");
    }

    // 1. send ACK to FCM
    final String ackJsonRequest = MessageMapper.createJsonAck(inMessage.getFrom(), inMessage.getMessageId());
    sendAck(ackJsonRequest);

    // 2. process and send message
    if (action.equals(Util.BACKEND_ACTION_ECHO)) { // send a message to the sender (user itself)
      final String messageId = Util.getUniqueMessageId();
      final String to = inMessage.getFrom();

      final CcsOutMessage outMessage = new CcsOutMessage(to, messageId, inMessage.getDataPayload());
      final String jsonRequest = MessageMapper.createJsonOutMessage(outMessage);
      sendDownstreamMessage(messageId, jsonRequest);
    } else if (action.equals(Util.BACKEND_ACTION_MESSAGE)) { // send a message to the recipient
      this.handlePacketRecieved(inMessage);
    }
  }

  /**
   * Handles an ACK message from FCM
   */
  private void handleAckReceipt(Map<String, Object> jsonMap) {
    removeMessageFromSyncMessages(jsonMap);
  }

  /**
   * Handles a NACK message from FCM
   */
  private void handleNackReceipt(Map<String, Object> jsonMap) {
    removeMessageFromSyncMessages(jsonMap);

    final String errorCode = (String) jsonMap.get("error");

    if (errorCode == null) {
      logger.info("Received null FCM Error Code.");
      return;
    }

    if (errorCode.equals("INVALID_JSON") || errorCode.equals("BAD_REGISTRATION")
        || errorCode.equals("DEVICE_UNREGISTERED") || errorCode.equals("BAD_ACK")
        || errorCode.equals("TOPICS_MESSAGE_RATE_EXCEEDED") || errorCode.equals("DEVICE_MESSAGE_RATE_EXCEEDED")) {
      logger.info("Device error: {} -> {}", jsonMap.get("error"), jsonMap.get("error_description"));
    } else if (errorCode.equals("SERVICE_UNAVAILABLE") || errorCode.equals("INTERNAL_SERVER_ERROR")) {
      logger.info("Server error: {} -> {}", jsonMap.get("error"), jsonMap.get("error_description"));
    } else if (errorCode.equals("CONNECTION_DRAINING")) {
      logger.info("Connection draining from Nack ...");
      handleConnectionDraining();
    } else {
      logger.info("Received unknown FCM Error Code: {}", errorCode);
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
      logger.info("Received unknown FCM Control message: {}", controlType);
    }
  }

  private void handleConnectionDraining() {
    logger.info("FCM Connection is draining!");
    isConnectionDraining = true;
  }

  /**
   * Remove the message from the sync messages list
   */
  private void removeMessageFromSyncMessages(Map<String, Object> jsonMap) {
    final String messageId = (String) jsonMap.get("message_id");
    if (messageId != null) {
      syncMessages.remove(messageId); // Remove the messageId from the sync messages list
    }
  }

  private void onUserAuthentication() {
    isConnectionDraining = false;
    sendPendingMessages();
    sendPendingForeverSyncMessages();
  }

  /**
   * ===============================================================================================
   * 
   * API Helper methods:
   * 
   * These are methods that implementers can use, call, or override. Help give the implementer more
   * control/ customization.
   * 
   * ===============================================================================================
   */

  /**
   * Note: This method is only called if {@link ReconnectionManager#isAutomaticReconnectEnabled()}
   * returns true
   */
  @Override
  public void reconnectionFailed(Exception e) {
    logger.info("Reconnection failed! Error: {}", e.getMessage());
  }

  /**
   * Note: This method is only called if {@link ReconnectionManager#isAutomaticReconnectEnabled()}
   * returns true
   */
  @Override
  public void reconnectingIn(int seconds) {
    logger.info("Reconnecting in {} ...", seconds);
  }

  /**
   * This method will be removed in Smack 4.3. Use {@link #connected(XMPPConnection)} or
   * {@link #authenticated(XMPPConnection, boolean)} instead.
   */
  @Deprecated
  @Override
  public void reconnectionSuccessful() {
    logger.info("Reconnection successful.");
  }

  @Override
  public void connectionClosedOnError(Exception e) {
    logger.info("Connection closed on error.");
  }

  @Override
  public void connectionClosed() {
    logger.info("Connection closed. The current connectionDraining flag is: {}", isConnectionDraining);
    if (isConnectionDraining) {
      reconnect();
    }
  }

  @Override
  public void authenticated(XMPPConnection arg0, boolean arg1) {
    logger.info("User authenticated.");
    // This is the last step after a connection or reconnection
    onUserAuthentication();
  }

  @Override
  public void connected(XMPPConnection arg0) {
    logger.info("Connection established.");
  }

  @Override
  public void pingFailed() {
    logger.info("The ping failed, restarting the ping interval again ...");
    final PingManager pingManager = PingManager.getInstanceFor(xmppConn);
    pingManager.setPingInterval(100);
  }

  /**
   * Called when a custom packet has been received by the server. By default this method just resends
   * the packet.
   */
  public void handlePacketRecieved(CcsInMessage inMessage) {
    final String messageId = Util.getUniqueMessageId();
    // TODO: it should be the user id to be retrieved from the data base
    final String to = inMessage.getDataPayload().get(Util.PAYLOAD_ATTRIBUTE_RECIPIENT);

    // TODO: handle the data payload sent to the client device. Here, I just resend the incoming one.
    final CcsOutMessage outMessage = new CcsOutMessage(to, messageId, inMessage.getDataPayload());
    final String jsonRequest = MessageMapper.createJsonOutMessage(outMessage);
    sendDownstreamMessage(messageId, jsonRequest);
  }

  /**
   * Sends a downstream message to FCM
   */
  public void sendDownstreamMessage(String messageId, String jsonRequest) {
    logger.info("Sending downstream message.");
    syncMessages.put(messageId, jsonRequest);
    if (!isConnectionDraining) {
      sendDownstreamMessageInternal(messageId, jsonRequest);
    }
  }

  /**
   * Sends a downstream message to FCM with back off strategy
   */
  private void sendDownstreamMessageInternal(String messageId, String jsonRequest) {
    final Stanza request = new FcmPacketExtension(jsonRequest).toPacket();
    final BackOffStrategy backoff = new BackOffStrategy();
    while (backoff.shouldRetry()) {
      try {
        xmppConn.sendStanza(request);
        backoff.doNotRetry();
      } catch (NotConnectedException | InterruptedException e) {
        logger.info("The packet could not be sent due to a connection problem. Backing off the packet: {}", request.toXML());
        try {
          backoff.errorOccured2();
        } catch (Exception e2) { // all the attempts failed
          syncMessages.remove(messageId);
          pendingMessages.put(messageId, jsonRequest);
        }
      }
    }
  }

  /**
   * Sends an ACK to FCM with back off strategy
   * 
   * @param jsonRequest
   */
  public void sendAck(String jsonRequest) {
    logger.info("Sending ack.");
    final Stanza packet = new FcmPacketExtension(jsonRequest).toPacket();
    final BackOffStrategy backoff = new BackOffStrategy();
    while (backoff.shouldRetry()) {
      try {
        xmppConn.sendStanza(packet);
        backoff.doNotRetry();
      } catch (NotConnectedException | InterruptedException e) {
        logger.info(
            "The packet could not be sent due to a connection problem. Backing off the packet: {}", packet.toXML());
        backoff.errorOccured();
      }
    }
  }

  /**
   * Sends a message to multiple recipients (list). Kind of like the old HTTP message with the list of
   * regIds in the "registration_ids" field.
   */
  public void sendBroadcast(CcsOutMessage outMessage, List<String> recipients) {
    final Map<String, Object> map = MessageMapper.createAttributeMap(outMessage);
    for (String toRegId : recipients) {
      final String messageId = Util.getUniqueMessageId();
      map.put("message_id", messageId);
      map.put("to", toRegId);
      final String jsonRequest = MessageMapper.createJsonMessage(map);
      sendDownstreamMessage(messageId, jsonRequest);
    }
  }

  public synchronized void reconnect() {
    logger.info("Initiating reconnection ...");
    final BackOffStrategy backoff = new BackOffStrategy(5, 1000);
    while (backoff.shouldRetry()) {
      try {
        connect();
        sendPendingMessages();
        sendPendingForeverSyncMessages();
        backoff.doNotRetry();
      } catch (XMPPException | SmackException | IOException | InterruptedException | KeyManagementException
          | NoSuchAlgorithmException e) {
        logger.info("The notifier server could not reconnect after the connection draining message.");
        backoff.errorOccured();
      }
    }
  }

  /*** BEGIN: Methods for the Manager ***/

  private boolean isConnected() {
    return xmppConn != null ? xmppConn.isConnected() : false;
  }

  private boolean isAuthenticated() {
    return xmppConn != null ? xmppConn.isAuthenticated() : false;
  }

  public boolean isAlive() {
    logger.info("Connection parameters -> isConnected: {}, isAuthenticated: {}", isConnected(), isAuthenticated());
    return isConnected() && isAuthenticated();
  }

  public void disconnectAll() {
    logger.info("Disconnecting all ...");
    if (xmppConn.isConnected()) {
      logger.info("Detaching all the listeners for the connection.");
      PingManager.getInstanceFor(xmppConn).unregisterPingFailedListener(this);
      ReconnectionManager.getInstanceFor(xmppConn).removeReconnectionListener(this);
      xmppConn.removeAsyncStanzaListener(this);
      xmppConn.removeConnectionListener(this);
      xmppConn.removePacketInterceptor(this);
      xmppConn.removeAllRequestAckPredicates();
      xmppConn.removeAllStanzaAcknowledgedListeners();
      xmppConn.removeAllStanzaIdAcknowledgedListeners();
      xmppConn.removePacketSendingListener(this);
      xmppConn.removeStanzaAcknowledgedListener(this);
      xmppConn.removeAllRequestAckPredicates();
      logger.info("Disconnecting the xmpp server from FCM.");
      xmppConn.disconnect();
    }
  }

  public void disconnectGracefully() {
    logger.info("Disconnecting ...");
    if (xmppConn.isConnected()) {
      logger.info("Disconnecting the xmpp server from FCM");
      xmppConn.disconnect(); // this method call the onClosed listener because it have not been detached
    }
  }

  /*** END: Methods for the REST entry point ***/

}
