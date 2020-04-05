# Garage Opener app for Hubitat

Make your garage door switch smart. 

Features:
* Works with a switch that starts or stops your garage door
* Optionally detects when garage door is fully open with a contact sensor
* Optionally detects when garage door is fully closed with a contact sensor
* Detects if garage door is stuck half way though (when using contact sensors)
* Can still use the switch to manually control the garage door, keeping the device state in sync

> Requires a switch that starts and stops your garage door, and every time it starts it goes in the opposite direction. 

> If you have a simple contact button that controls the garage door, you can connect a smart relay in parallel to the button. Alternatively, install a smart switch, connect an AC relay (e.g. LY2NJ) instead of a light to short the garage door opener contacts. _Consult with your electrician before dealing with mains voltage_!

## Basic Installation

1. Install [driver](../../drivers/virtual-simple-garage-door-controller.groovy) and [app](garage-opener.groovy) code.
2. Add a virtual device using the installed _Virtual Simple Garage Door Controller_ driver.
3. Add the _Garage Opener_ app.
4. Configure the app with the virtual garage door controller and the smart switch that controls your garage door.

## Configuration

* `Garage Door Control` - virtual device which uses `Virtual Simple Garage Door Controller` driver
* `Garage Switch` - the switch that controls your garage door
* `Garage opening time (in seconds)` - the time it takes for the garage door to open
   * If using contact sensors, this value will be used to detect if the door is stuck, so use a slightly large value, e.g. increase the actual time by 1 second
   * If not using contact sensors, this value will be used to time the garage door movement to sync up the virual door state with the physical door state
* `Switch off delay (in milliseconds)` - the delay to automatically turn the Garage Switch off so it acts as a momentary switch
* `Reversal delay (in milliseconds)` - the delay between stopping the door and starting it again in the opposite direction (used when changing from _closing_ to _opening_ of vice versa)

## Step-by-step instructions

### Virtual Simple Garage Door Controller

1. On the Hubitat hub, go to the _Drivers Code_ page and click _+ New Driver_
2. Copy and paste the contents of [virtual-simple-garage-door-controller.groovy](../../drivers/virtual-simple-garage-door-controller.groovy) and _Save_
5. Go to the _Devices_ page, click _Add Virtual Device_, specify _Device Name_, _Device Label_, select _Virtual Manual Garage Door Controller_ for _Type_ and click _Done_

This device will represent the state of your physical garage door and will enable controlling the door through it. 

> This driver is needed to avoid automatic change of state of the built-in Virtual Garage Door Controller driver.

### Contact Sensors

The app can detect when your garage door is fully closed or fully open. 

* Attach a contact sensor to the garage door when it's fully _closed_ (the contact should be `closed` in that position)
* Attach a contact sensor to the garage door when it's fully _open_ (the contact should be `closed` in that position)

> If you only have a single contact sensor it's recommented to use it for detecting fully closed door. 


## App Installation

1. On the Hubitat hub, go to the _Apps Code_ page and click _+ New App_
2. Copy and paste the contents of [garage-opener.groovy](garage-opener.groovy) and _Save_
5. Go to the _Apps_ page, click _Add User App_, select _Garage Opener_ and click _Done_
