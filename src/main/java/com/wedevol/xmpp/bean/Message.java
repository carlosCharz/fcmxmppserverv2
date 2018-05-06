package com.wedevol.xmpp.bean;

import com.wedevol.xmpp.util.Util;

/**
 * Represents a message for the sync and pending list
 */
public class Message {

  private Long timestamp; // in millis
  private String jsonRequest;

  public static Message from(String jsonRequest) {
    return new Message(Util.getCurrentTimeMillis(), jsonRequest);
  }

  public Message(Long timestamp, String jsonRequest) {
    this.timestamp = timestamp;
    this.jsonRequest = jsonRequest;
  }

  public Long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Long timestamp) {
    this.timestamp = timestamp;
  }

  public String getJsonRequest() {
    return jsonRequest;
  }

  public void setJsonRequest(String jsonRequest) {
    this.jsonRequest = jsonRequest;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((jsonRequest == null) ? 0 : jsonRequest.hashCode());
    result = prime * result + ((timestamp == null) ? 0 : timestamp.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Message other = (Message) obj;
    if (jsonRequest == null) {
      if (other.jsonRequest != null)
        return false;
    } else if (!jsonRequest.equals(other.jsonRequest))
      return false;
    if (timestamp == null) {
      if (other.timestamp != null)
        return false;
    } else if (!timestamp.equals(other.timestamp))
      return false;
    return true;
  }

}
