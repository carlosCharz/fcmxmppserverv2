package com.wedevol.xmpp.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedevol.xmpp.bean.CcsInMessage;
import com.wedevol.xmpp.bean.CcsOutMessage;

/**
 * Mapper for the transformation of JSON messages to attribute maps and vice versa in the XMPP
 * Server
 * 
 * @author Charz++
 */

public class MessageMapper {

  private static final Logger logger = LoggerFactory.getLogger(MessageMapper.class);
  private static ObjectMapper mapper = new ObjectMapper();

  /**
   * Creates a JSON from a FCM outgoing message attributes
   */
  public static String toJsonString(CcsOutMessage outMessage) {
    return toJsonString(mapFrom(outMessage));
  }

  /**
   * Creates a JSON from a FCM incoming message attributes
   */
  public static String toJsonString(CcsInMessage inMessage) {
    return toJsonString(mapFrom(inMessage));
  }

  /**
   * Creates a JSON encoded ACK message for a received upstream message
   */
  public static String createJsonAck(String to, String messageId) {
    final Map<String, Object> map = new HashMap<String, Object>();
    map.put("message_type", "ack");
    map.put("to", to);
    map.put("message_id", messageId);
    return toJsonString(map);
  }

  public static String toJsonString(Map<String, Object> jsonMap) {
    try {
      return mapper.writeValueAsString(jsonMap);
    } catch (JsonProcessingException e) {
      logger.error("Error parsing JSON map: {}", jsonMap.values());
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> toMapFromJsonString(String json) {
    try {
      return mapper.readValue(json, HashMap.class);
    } catch (IOException e) {
      logger.error("Error parsing JSON string: {}", json);
    }
    return null;
  }

  /**
   * Creates a MAP from a FCM outgoing message attributes
   */
  public static Map<String, Object> mapFrom(CcsOutMessage msg) {
    final Map<String, Object> map = new HashMap<String, Object>();
    if (msg.getTo() != null) {
      map.put("to", msg.getTo());
    }
    if (msg.getMessageId() != null) {
      map.put("message_id", msg.getMessageId());
    }
    if (msg.getDataPayload() != null) {
      map.put("data", msg.getDataPayload());
    }
    if (msg.getNotificationPayload() != null) {
      map.put("notification", msg.getNotificationPayload());
    }
    if (msg.getCondition() != null) {
      map.put("condition", msg.getCondition());
    }
    if (msg.getCollapseKey() != null) {
      map.put("collapse_key", msg.getCollapseKey());
    }
    if (msg.getPriority() != null) {
      map.put("priority", msg.getPriority());
    }
    if (msg.isContentAvailable() != null && msg.isContentAvailable()) {
      map.put("content_available", true);
    }
    if (msg.getTimeToLive() != null) {
      map.put("time_to_live", msg.getTimeToLive());
    }
    if (msg.isDeliveryReceiptRequested() != null && msg.isDeliveryReceiptRequested()) {
      map.put("delivery_receipt_requested", true);
    }
    if (msg.isDryRun() != null && msg.isDryRun()) {
      map.put("dry_run", true);
    }
    return map;
  }

  /**
   * Creates a MAP from a FCM incoming message attributes
   */
  private static Map<String, Object> mapFrom(CcsInMessage msg) {
    final Map<String, Object> map = new HashMap<String, Object>();
    if (msg.getFrom() != null) {
      map.put("from", msg.getFrom());
    }
    if (msg.getCategory() != null) {
      map.put("category", msg.getCategory());
    }
    if (msg.getMessageId() != null) {
      map.put("message_id", msg.getMessageId());
    }
    map.put("data", msg.getDataPayload());
    return map;
  }

  /**
   * Creates an incoming message according the bean
   */
  @SuppressWarnings("unchecked")
  public static CcsInMessage ccsInMessageFrom(Map<String, Object> jsonMap) {
    String from = null;
    String category = null;
    String messageId = null;
    Map<String, String> dataPayload = null;

    if (jsonMap.get("from") != null) {
      from = jsonMap.get("from").toString();
    }

    // Package name of the application that sent this message
    if (jsonMap.get("category") != null) {
      category = jsonMap.get("category").toString();
    }

    // Unique message id
    if (jsonMap.get("message_id") != null) {
      messageId = jsonMap.get("message_id").toString();
    }

    if (jsonMap.get("data") != null) {
      dataPayload = (Map<String, String>) jsonMap.get("data");
    }

    final CcsInMessage msg = new CcsInMessage(from, category, messageId, dataPayload);
    return msg;
  }

}
