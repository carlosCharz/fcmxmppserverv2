# XMPP Connection Server for FCM (Upgrade from Smack 3.x to 4.x)
This is an upgrade of my last [FCM XMPP Connection Server](https://github.com/carlosCharz/fcmxmppserver) application. Now, this project uses the latest version at this time of the Smack library (4.2.1). _**If you had some problems check my troubleshooting section!**_

The new version has introduced new terminology, deprecated some older methods and enriched the library in general. The problem started when there is a no working example out there using the new version to build a XMPP CCS for FCM. In summary, the API changes from the 3.x to the 4.x version are:

1. XMPPConnection is now an interface. Use either AbstractXMPPConnection or one of its subclasses (XMPPTCPConnection).
2. XMPPConnection.addPacketListener is deprecated: use either addAsyncPacketListener or addSyncPacketListener.
3. Packet became a deprecated interface. Use the new Stanza class.
4. The Packet Extension term is now Extension Element.
  
For more information you must read the following documentation: 
 
* [The upgrade guide](https://github.com/igniterealtime/Smack/wiki/Smack-4.1-Readme-and-Upgrade-Guide)
* [New Smack Terminology](https://github.com/igniterealtime/Smack/wiki/New-Smack-Terminology)
* [The Smack Javadoc](http://download.igniterealtime.org/smack/docs/latest/javadoc/)

_**ADDITIONAL USEFUL LINKS**_

* [XMPP Connection Server for FCM](https://github.com/carlosCharz/fcmxmppserver): This project is the original code base and it gives you a simple explanation of FCM XMPP Connection Server.
* [FCM Connection Draining solution explanation](https://youtu.be/6AQCnNWPksg): This video explains how I handle the FCM Connection Draining message.

## New Smack libraries

 * [Smack java 7](https://mvnrepository.com/artifact/org.igniterealtime.smack/smack-java7)
 * [Smack tcp](https://mvnrepository.com/artifact/org.igniterealtime.smack/smack-tcp)
 * [Smack extensions](https://mvnrepository.com/artifact/org.igniterealtime.smack/smack-extensions)

## How to start the server
Just because it is the same project as my prior solution, the way to start the server is exactly the same. You can read my [how to start the server](https://github.com/carlosCharz/fcmxmppserver).

## Troubleshooting
This is a simple java code. You can integrate with just spring or spring in a container (Tomcat for example). In any case you need to take into account 3 issues: 

1. _**If using a simple java application, keep the application alive listening messages.**_ This problem occurs when you use a simple java code as a daemon in linux (that's why I put the while true workaround).

2. _**If using a java server (with spring boot at least), close the XMPP connection when the server goes down.**_ This problem occurs when even if you shutdown the server the XMPP connection is still open and handling incoming messages. I do not know yet if this is a spring boot problem. The thing is like we are wrapping the XMPP CCS Server into a HTTP interface just to treat it as a normal server. My workaround was putting the disconnection in the spring preDestroy. @PreDestroy public void springPreDestroy() { //ccsConnection.disconnect(); }

3. _**Reconnection when connection draining.**_ The reconnection should be handled to connect again to the FCM using the same parameters with a backoff strategy. Smack can handle the automatic reconnection if you enable it but if the connection will be closed from the FCM side you should call your reconnection method when FCM sends connection draining control message or connection draining error message.

## About me
I am Carlos Becerra - MSc. Softwware & Systems. You can contact me via:

* [Google+](https://plus.google.com/+CarlosBecerraRodr%C3%ADguez)
* [Twitter](https://twitter.com/CarlosBecerraRo)

## Thanks
To tell the truth. I was really worried looking for the right solution. Finally, I made a list of useful links (apart from the above documentation links).

* [gcm server](http://www.marothiatechs.com/2015/08/building-your-own-android-chat_18.html)
* [stanza](http://www.programcreek.com/java-api-examples/index.php?api=org.jivesoftware.smack.packet.Stanza)
* [a problem](https://community.igniterealtime.org/thread/59532)
* [other gcm server](https://github.com/googlesamples/friendlyping/blob/master/server/Java/src/main/java/com/gcm/samples/friendlyping/GcmServer.java)

_**Any improvement or comment about the project is always welcome! As well as others shared their code publicly I want to share mine! Thanks!**_

## License
```javas
Copyright 2016 Carlos Becerra

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
