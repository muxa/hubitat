# Master Switch app for Hubitat

Use one master switch to control multiple linked switches.

* Turning master switch off will turn off linked switches.
* Turning master switch on will restore previously on switches (all on by default).
* When any of the linked switches are on, the master switch will remain on.
* When all linked switches are off the master switch will be off.


## App Installation

1. On the Hubitat hub, go to the _Apps Code_ page and click _+ New App_
2. Copy and paste the contents of `master-switch.groovy` and _Save_
3. Click _+ New App+ again
4. Copy and paste the contents of `master-switch-instance.groovy` and click _Save_
5. Go to the _Apps_ page, click _Add User App_, select _Master Switch_ and click _Done_

## App Usage

1. Click on _Master Switch_ in your apps list
2. Click on _Add a new Master Switch_
3. Choose the master switches
4. Chose the linked switches
5. Click _Done_