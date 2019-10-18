# Master Switch app for Hubitat

Use one master switch to control multiple linked switches.

* Turning master switch off will turn off linked switches.
* Turning master switch on will restore previously on switches (all on by default).
* When any of the linked switches are on, the master switch will remain on.
* When all linked switches are off the master switch will be off.


## App Installation

1. On the Hubitat hub, go to the "Apps Code" page and click "+ New App"
2. Copy and paste the contents of `master-switch.groovy` and "Save"
3. Click "+ New App" again
4. Copy and paste the contents of `master-switch-instance.groovy` and "Save"
5. Go to the "Apps" page, click "Add User App", select `Master Switch` and click "Done"

## App Usage

1. Click on "Master Switch" in your apps list
2. Click on "Add a new Master Switch"
3. Choose the master switches
4. Chose the linked switches
5. Click "Done"