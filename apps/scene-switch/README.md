# Scene Switch app for Hubitat

Use one switch to toggle scenes.

* Specify which switches to turn off
* Specify switches to turn for up to 3 scenes
* When scene switch is turned off and quickly tured back on it activates the next scene

Usage example:

* Use a one light switch to optionnally turn on or off other light switches (changing the schene)
* Use a downstairs switch (dummy switch, not wired to any light) to be able to switch off all lights upstairs, and turn on only a subset of those lights

## App Installation

1. On the Hubitat hub, go to the _Apps Code_ page and click _+ New App_
2. Copy and paste the contents of `scene-switch.groovy` and _Save_
3. Click _+ New App_ again
4. Copy and paste the contents of `scene-switch-instance.groovy` and click _Save_
5. Go to the _Apps_ page, click _Add User App_, select _Scene Switch_ and click _Done_

## App Usage

1. Click on Scene Switch_ in your apps list
2. Click on _Add a new Scene Switch_
3. Choose the scene switch
4. Choose the linked switches for different scenes
5. Click _Done_