# Android Sample App Change Log

## v1.3 released on 2018 11-16:

### Resolved Issues

* The issue where the PhoneCallController UI  was limited and could not provide a way to enter a phone number and generate  a unique call identifier to initiate a call outside the scope of an Alexa  interaction is fixed.
* The issue of the Sample App not running on some emulators, such as  Nexus 6 API 25 and ARM 64-bit is fixed.

### Known Issues

* Display card rendering is not adaptable to a variety of screen sizes.
* When authenticated with CBL, if network connectivity is lost while refreshing the access token, the Sample App does not automatically attempt to refresh the token when connectivity is restored, and the GUI incorrectly displays the "log in" button even though a refresh token is present. Restoring an authorized state requires either restarting the app or following the log in steps again with network connectivity.
* Particular sections of the Flash Briefing do not resume from a paused state properly. Attempting to play after pause in some cases may restart the media playback.
* The Sample App does not implement managing inter-app audio focus, so other apps will not recognize its audio playback appropriately.
* Alexa dialog playback may stop abruptly when switching between wifi and mobile data.
* The Android Sample App can sometimes fails to configure and initialize correctly, leaving a blank screen.
* Sample App disconnects after remaining idle for some time and takes a while to reconnect.

## v1.1 released on 2018-08-31:

### Resolved Issues

There are no resolved issues in this release.

### Known Issues

* Display card rendering is not adaptable to a variety of screen sizes.
* When authenticated with CBL, if network connectivity is lost while refreshing the access token, the Sample App does not automatically attempt to refresh the token when connectivity is restored, and the GUI incorrectly displays the "log in" button even though a refresh token is present. Restoring an authorized state requires either restarting the app or following the log in steps again with network connectivity.
* Particular sections of the Flash Briefing do not resume from a paused state properly. Attempting to play after pause in some cases may restart the media playback.
* The Sample App does not implement managing inter-app audio focus, so other apps will not recognize its audio playback appropriately.
* Alexa dialog playback may stop abruptly when switching between wifi and mobile data.
* The `PhoneCallController` UI is limited and does not provide a way to enter a phone number and generate a unique call identifier to initiate a call outside the scope of an Alexa interaction.
* The Sample App may not run on some emulators, such as Nexus 6 API 25 and ARM 64-bit.
* The Android Sample App can sometimes fails to configure and initialize correctly, leaving a blank and empty screen.
* Sample App disconnects after remaining idle for some time and takes up to a while to reconnect.

## v1.0.1 released on 2018-07-31:

### Resolved Issues

* The issue related to music playing after saying "stop" for music playback then saying "flash briefing" is fixed. The flash briefing response plays as expected.
* The Login with Amazon browser implementation now refreshes the auth token after one hour.
* Examples were added about how to fetch user profiles for LWA with browser and CBL.

### Known Issues

* Display card rendering is not adaptable to a variety of screen sizes.
* When authenticated with CBL, if network connectivity is lost while refreshing the access token, the Sample App does not automatically attempt to refresh the token when connectivity is restored, and the GUI incorrectly displays the "log in" button even though a refresh token is present. Restoring an authorized state requires either restarting the app or following the log in steps again with network connectivity.
* Particular sections of the Flash Briefing do not resume from a paused state properly. Attempting to play after pause in some cases may restart the media playback.
* The Sample App does not implement managing inter-app audio focus, so other apps will not recognize its audio playback appropriately.
* Alexa dialog playback may stop abruptly when switching between wifi and mobile data.
* The `PhoneCallController` UI is limited and does not provide a way to enter a phone number and generate a unique call identifier to initiate a call outside the scope of an Alexa interaction.
* The Sample App may not run on some emulators, such as Nexus 6 API 25 and ARM 64-bit.

## v1.0.0

* v1.0.0 released on 2018-06-29

### Known issues
* Display card rendering is not adaptable to a variety of screen sizes.
* When authenticated with CBL, if network connectivity is lost while refreshing the access token, the Sample App does not automatically attempt to refresh the token when connectivity is restored, and the GUI displays the "log in" button even though a refresh token is present. Restoring an authorized state requires either putting the app into the background and bringing it back to the foreground or following the log in steps again.
* Particular sections of the Flash Briefing do not resume from a paused state properly. Attempting to play after pause in some cases may restart the media playback.
* The Sample App does not implement managing audio focus, so other apps will not recognize its audio playback appropriately.
* Alexa dialog playback may stop abruptly when switching between wifi and mobile data.
* After saying 'stop' to stop music playback then saying 'flash briefing', the content of the flash briefing does not play and the stopped music resumes instead.
* The PhoneCallController UI is limited and does not provide means to enter a phone number to initiate a call outside the scope of an Alexa interaction. When clicking "Initiate Call" to make a call from the stored number from the previous dial() payload, if a call with this identifier was already activated and terminated, the implementation does not generate a new call identifier when attempting to activate a new call to the same number.
* The Sample App may not run on some emulators, such as Nexus 6 API 25 and ARM 64-bit.
