package com.wedevol.xmpp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;

import com.wedevol.xmpp.bean.CcsOutMessage;
import com.wedevol.xmpp.server.CcsClient;
import com.wedevol.xmpp.server.MessageHelper;
import com.wedevol.xmpp.util.Util;

/**
 * Entry Point class for the XMPP Server in dev mode for debugging and testing
 * purposes
 */
public class EntryPoint {
	public static void main(String[] args) throws SmackException, IOException {
		final String fcmProjectSenderId = args[0];
		final String fcmServerKey = args[1];
		final String toRegId = args[2];

		CcsClient ccsClient = CcsClient.prepareClient(fcmProjectSenderId, fcmServerKey, true);

		try {
			ccsClient.connect();
		} catch (XMPPException | InterruptedException e) {
			e.printStackTrace();
		}

		// Send a sample downstream message to a device
		String messageId = Util.getUniqueMessageId();
		Map<String, String> dataPayload = new HashMap<String, String>();
		dataPayload.put(Util.PAYLOAD_ATTRIBUTE_MESSAGE, "This is the simple sample message");
		CcsOutMessage message = new CcsOutMessage(toRegId, messageId, dataPayload);
		String jsonRequest = MessageHelper.createJsonOutMessage(message);
		ccsClient.send(jsonRequest);

		while (true) {
			// TODO: Improve this because the app closes itself after the execution of the connect method
		}
	}
}
