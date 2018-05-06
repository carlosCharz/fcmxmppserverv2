# Changelog
All notable changes to the fcm xmpp server v2 project will be documented in this file, in a per release basis.

## [unreleased]
**Tag**: xmpp_r1_v8

### Added
- a better solution for the connection draining (A manager class will be added)

-------------------------------------------------------------------------------------

## [xmpp_r1_v7] - 2018-05-0
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