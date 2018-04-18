# Changelog
All notable changes to the fcm xmpp server v2 project will be documented in this file, in a per release basis.

## [unreleased]
**Tag**: xmpp_r1_v3

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