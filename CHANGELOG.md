# Changelog
All notable changes to the fcm xmpp server v2 project will be documented in this file, in a per release basis.

## [unreleased]

### Added
- a better solution for the connection draining (A manager class will be added)


-------------------------------------------------------------------------------------


## [xmpp_r1_v16] - 2019-12-24

### Modified
- Upgrade jackson from 2.10.0 to 2.10.1 https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.10.1


-------------------------------------------------------------------------------------


## [xmpp_r1_v15] - 2019-11-09

### Modified
- Upgrade jackson from 2.9.9 to 2.10.0 https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.10


-------------------------------------------------------------------------------------


## [xmpp_r1_v14] - 2019-07-03

### Modified
- Upgrade smack lib from 4.3.3 to 4.3.4 https://download.igniterealtime.org/smack/docs/latest/changelog.html


-------------------------------------------------------------------------------------


## [xmpp_r1_v13] - 2019-05-25

### Modified
- upgrade jackson from 2.9.8 to 2.9.9 https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.9.9

-------------------------------------------------------------------------------------


## [xmpp_r1_v12] - 2019-03-27

### Modified
- upgrade smack from 4.3.1 to 4.3.3 https://download.igniterealtime.org/smack/docs/latest/changelog.html

-------------------------------------------------------------------------------------

## [xmpp_r1_v11] - 2019-02-02

### Modified
- upgrade jackson from 2.9.7 to 2.9.8 https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.9.8

-------------------------------------------------------------------------------------

## [xmpp_r1_v10] - 2018-11-19

### Modified
- upgrade smack from 4.3.0 to 4.3.1 https://download.igniterealtime.org/smack/docs/4.3.1/changelog.html
- upgrade jackson from 2.9.6 to 2.9.7 https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.9.7

-------------------------------------------------------------------------------------


## [xmpp_r1_v9] - 2018-08-08

### Modified
- upgrade smack from 4.2.4 to 4.3.0 https://github.com/igniterealtime/Smack/wiki/Smack-4.3-Readme-and-Upgrade-Guide
- Changes:
	- setDebuggerEnabled deprecated (there is a factory instead)
	- remove reconnection listeners from connection listeners because they are part of other interface
	- change toXml() to toXml(null) (Smack had a TODO internally when they fully support java 8)

-------------------------------------------------------------------------------------


## [xmpp_r1_v8] - 2018-06-28

### Modified
- upgrade jackson to 2.9.6

-------------------------------------------------------------------------------------

## [xmpp_r1_v7] - 2018-05-06
**Tag**: xmpp_r1_v7

### Modified
- refactor the syncList to handle timestmaps
- send pending and sync messages in order
- send all the queued sync messages that occurred before 5 seconds (5000 ms) ago

-------------------------------------------------------------------------------------

## [xmpp_r1_v6] - 2018-05-01
**Tag**: xmpp_r1_v6

### Modified
- upgrade smack to 4.2.4 - http://download.igniterealtime.org/smack/docs/latest/changelog.html
- upgrade logback to 1.2.3

-------------------------------------------------------------------------------------

## [xmpp_r1_v5] - 2018-04-24
**Tag**: xmpp_r1_v5

### Added
- upload formatter
- add to the pending message list the backoff failed messages
- handle two lists for pending messages (synMessages and pendingMessages)
- add listeners removal when disconnect (helper method)
- add java docs
- java 8 optional usage
- logback dependency for logging

## Modified
- change json simple library to jackson fasterxml
- separate ack from send packet basic
- ping failed listener
- rename class MessageHelper to MessageMapper and move it to the util package

-------------------------------------------------------------------------------------

## [xmpp_r1_v4] - 2018-04-23
**Tag**: xmpp_r1_v4

### Added
- expose a method to handle the packet received by the server

-------------------------------------------------------------------------------------

## [xmpp_r1_v3] - 2018-04-20
**Tag**: xmpp_r1_v3

### Modified
- minor code refactor

-------------------------------------------------------------------------------------

## [xmpp_r1_v2] - 2018-04-18

### Modified
- Rearranging and redoing the ccsClient to work more as a library/API. Thanks to @Turkey2349
   - add listeners to the CcsClient directly
   - add the reconnection listener
   - add more comments to the code
   
### Fixed
- Workaround for the connection draining. It still needs a better solution.

-------------------------------------------------------------------------------------

## [xmpp_r1_v1] - 2018-03-30
**Tag**: xmpp_r1_v1

### Added
- tls/ssl support in connection configuration builder
- enable compression in connection configuration builder
- log security checks
- add unique message generation
- enhance logs

### Fixed

### Removed