# <img align="left" src="https://www.tracman.org/static/img/icon/by/48.png" alt="[]" title="The Tracman Logo">Tracman for Android
###### v 0.6.1

Android app to upload a user's GPS location to the [tracman server](https://github.com/tracman-org/Server). Cloned from [fs/android-base](https://github.com/fs/android-base). 

## Installation

Install from the [Google Play Store](https://play.google.com/store/apps/details?id=us.keithirwin.tracman) or with `adb`/Android Studio. 

## Usage

In order to apply changes to settings, you need to hit the back button and return to the main menu (where it just says "General settings").  Don't hit back again, or you will be logged out.  This will be fixed as [#9](https://github.com/Tracman-org/Android/issues/9) someday... 

The app will switch between "realtime updates" (Every second, or whatever interval you set up in the settings) and "occasional updates", depending on if there are visitors to your map.  This prevents the app from draining your battery when nobody's looking at your location anyway. 

More information is available on the [help page](https://www.tracman.org/help#android).

## Development

### Prerequisites
* [Android Studio](https://developer.android.com/sdk/installing/studio.html) 3.x
* JDK 8
* Android SDK

### Setup
 1. Clone application

    	mkdir tracman-android && cd tracman-android
    	git clone git@github.com:Tracman-Org/Android.git

 2. Import the project into your favourite IDE.
Just select the root `build.gradle` and your IDE will do the rest.

### Codestyles
Project contains default codestyle scheme at `./idea/codestyleSettings.xml`. Make sure that you use default project scheme to avoid mess with codestyle with your collegues.
Go to the Settings (`cmd + ,`) -> Editor -> Code Style look on top of right panel and select `Project` from `Scheme` dropdown. Apply -> Ok.

## Changelog

#### v0.6.1

* Updated server URLs

#### v0.6.0

* [#1](https://github.com/Tracman-org/Android/issues/1) Deals with lost connections
* [#6](https://github.com/Tracman-org/Android/issues/6) Loads on boot
* [#7](https://github.com/Tracman-org/Android/issues/7) Added email/password login
* [#8](https://github.com/Tracman-org/Android/issues/8) Doesn't log back in after logging out

#### v0.5.0

* Fixed compatibility issues with TLS

## License

###### see [LICENSE.md](https://github.com/Tracman-org/Android/blob/master/LICENSE.md)

Tracman: GPS tracking app for android
Copyright © 2018 [Keith Irwin](https://www.keithirwin.us/)

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program.  If not, see <[http://www.gnu.org/licenses/](http://www.gnu.org/licenses/)>.

## Credits
Android app skeleton is maintained by [Adel Nizamutdinov](http://github.com/adelnizamutdinov) and [Ilya Eremin](http://github.com/ilyaeremin).
It was written by [Flatstack](http://www.flatstack.com) with the help of 
[contributors](http://github.com/fs/android-base/contributors)

[<img src="http://www.flatstack.com/logo.svg" width="100"/>](http://www.flatstack.com)
