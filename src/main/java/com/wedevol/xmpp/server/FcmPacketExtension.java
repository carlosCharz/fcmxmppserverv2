package com.wedevol.xmpp.server;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;
import com.wedevol.xmpp.util.Util;

/**
 * XMPP Packet Extension for FCM Cloud Connection Server
 */
public class FcmPacketExtension implements ExtensionElement {

  private String json;

  public FcmPacketExtension(String json) {
    this.json = json;
  }

  public String getJson() {
    return json;
  }

  @Override
  public String toXML(String enclosingNamespace) {
    // TODO: 1. Do we need to scape the json? StringUtils.escapeForXML(json) 2. How to use the enclosing namespace?
    return String.format("<%s xmlns=\"%s\">%s</%s>", getElementName(), getNamespace(), json, Util.FCM_ELEMENT_NAME);
  }

  public Stanza toPacket() {
    final Message message = new Message();
    message.addExtension(this);
    return message;
  }

  @Override
  public String getElementName() {
    return Util.FCM_ELEMENT_NAME;
  }

  @Override
  public String getNamespace() {
    return Util.FCM_NAMESPACE;
  }
}
