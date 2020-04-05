import groovy.json.JsonSlurper
/**
 *  Garage Opener v1.0.0
 *
 *  Copyright 2020 Mikhail Diatchenko (@muxa)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

definition(
    name: "Garage Opener",
    namespace: "muxa",
    author: "Mikhail Diatchenko",
    description: "Control your garage door with a switch and optional contact sensors",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

def garageControl = [
		name:				"garageControl",
		type:				"capability.garageDoorControl",
		title:				"Garage Door Control",
		description:		"Use a Virtual Garage Door Control device",
		multiple:			false,
		required:			true
	]

def garageSwitch = [
		name:				"garageSwitch",
		type:				"capability.switch",
		title:				"Garage Switch",
		description:		"Physical switch that controls your garage door",
		multiple:			false,
		required:			true
	]

def isMomentarySwitch = [
		name:				"isMomentarySwitch",
		type:				"bool",
		title:				"Is Momentary Switch?",
		description:		"Does the switch automatically turn off?",
		defaultValue:		false,
		required:			true
	]

def closedContact = [
		name:				"closedContact",
		type:				"capability.contactSensor",
		title:				"Garage Fully Closed Contact",
		description:		"Contact Sensor that indicates when garage is fully closed",
		multiple:			false,
		required:			false
	]

def openContact = [
		name:				"openContact",
		type:				"capability.contactSensor",
		title:				"Garage Fully Open Contact",
		description:		"Contact Sensor that indicates when garage is fully open",
		multiple:			false,
		required:			false
	]

def garageTime = [
		name:				"garageTime",
		type:				"number",
		title:				"Garage opening time (in seconds)",
		description:		"Time it takes for the garage door to open or close",
		defaultValue:		15,
		required:			true
	]

def switchOffDelay = [
		name:				"switchOffDelay",
		type:				"long",
		title:				"Switch off delay (in milliseconds)",
		description:		"Delay before automatically turning the switch off (to act as a momentary switch)",
		defaultValue:		1000,
		required:			true
	]

def reversalDelay = [
		name:				"reversalDelay",
		type:				"long",
		title:				"Reversal delay (in milliseconds)",
		description:		"Delay before turning the switch on again to reverse door direction",
		defaultValue:		1000,
		required:			true
	]

def enableLogging = [
		name:				"enableLogging",
		type:				"bool",
		title:				"Enable Debug Logging?",
		defaultValue:		false,
		required:			true
	]

preferences {
    //dynamicPage(name: "controls", uninstall: true, install: true) {
    page(name: "mainPage", title: "Garage Opener", install: true, uninstall: true) {
		section("<h2>Controls</h2>") {
            input garageControl
            input garageSwitch
    	}
        
        section("<h2>Contacts</h2>") {
            input closedContact
            input openContact
    	}
        
        section("<h2>Options</h2>") {
            input garageTime
            input switchOffDelay
            input reversalDelay
            input enableLogging
    	}
    }
}

def installed() {
    initialize()
}

def updated() {
    log "Updating with settings: ${settings}"
    
    unsubscribe()
    initialize()
}

def initialize() {
    setupSubscriptions()
    
    atomicState.openingBySwitch = false
    atomicState.doorMoving = false
        
    log "Initialised"
}

def setupSubscriptions() {
    subscribe(garageControl, "door", garageControlHandler)
    subscribe(garageSwitch, "switch", garageSwitchHandler)
    if (closedContact)
        subscribe(closedContact, "contact", garageClosedContactHandler)    
    if (openContact)
        subscribe(openContact, "contact", garageOpenContactHandler)
}

def garageControlHandler(evt) {    
    logDebug "Garage door event: ${evt.value}"
/*
IF (Variable Garage door manual action(false) = true(F) [FALSE]) THEN
	IF (Garage Door open(F)  OR 
	Garage Door closed(T) [TRUE]) THEN
		Set Garage door manual action to false
	END-IF
ELSE
	IF (Garage Door opening(F) [FALSE]) THEN
		On: Garage Door Switch
		Off: Garage Door Switch --> delayed: 0:00:00.5
	ELSE-IF (Garage Door closing(F) [FALSE]) THEN
		On: Garage Door Switch
		Off: Garage Door Switch --> delayed: 0:00:00.5
	END-IF
END-IF */
    
    if (evt.value == 'opening' || evt.value == 'closing') {
        
        if (atomicState.lastDoorStatus == 'opening' || atomicState.lastDoorStatus == 'closing') {
            log "Engage garage switch from controller to stop motion and reverse direction"
            stopAndReverseDoorDirection()
        } else {
            startTimeout()
        
            if (!state.openingBySwitch) {
                // switch should be driven by the garage controller
            
                if (atomicState.lastDoorStatus == 'unknown') {
                    // door stopped mid way
                    if (evt.value != atomicState.lastDoorAction) {
                        // want to go into reverse direction, which is what will happen when engaging the switch
                        log "Engage garage switch from controller"
                        garageSwitch.on()
                    } else {
                        log "Engage garage switch from controller to reverse direction"
                        reverseDoorDirection()
                    }
                } else {
                    log "Engage garage switch from controller"
                    garageSwitch.on()
                }
            }
        }
        
        atomicState.lastDoorAction = evt.value
    } else {
        // opening or closing is done or is interrupted
        atomicState.doorMoving = false
        atomicState.openingBySwitch = false
    }
    atomicState.lastDoorStatus = evt.value
}

def stopAndReverseDoorDirection() {
    // 1. stop motion
    garageSwitch.on()
    
    // 2. start motion to reverse direction after a delay
    // delay is twice the switch off time so that there's the same delay after switch turns off before it turns on again 
    runInMillis(switchOffDelay.toLong()+reversalDelay.toLong(), garageOnOppositeDirection)
}

def reverseDoorDirection() {
    // 1. start motion going in the wrong direction
    garageSwitch.on()
    
    // 2. stop and reverse direction to go in the desired direction after a delay
    // delay is twice the switch off time so that there's the same delay after switch turns off before it turns on again 
    runInMillis(switchOffDelay.toLong()+reversalDelay.toLong(), stopAndReverseDoorDirection)
}

def garageOnOppositeDirection() {
    // since direction can only be change by controller, we would have recorded the intended action as last action
    // however this is a future action, not the last action
    // so reverse it, and then trigger the switch to then drive the controler into the right direction
    atomicState.lastDoorAction = atomicState.lastDoorAction == 'opening' ? 'closing' : 'opening'
    garageSwitch.on()
}

def garageSwitchHandler(evt) {    
    // logDebug "Garage switch event: ${evt.value}"
    
    if (evt.value == 'on') {
/* 
IF (NOT Garage Door opening(F)  AND 
NOT Garage Door closing(F) [TRUE]) THEN
	Set Garage door manual action to true
	IF (Garage Door open(F) [FALSE]) THEN
		Garage close: Garage Door
	ELSE
		Garage open: Garage Door
	END-IF
END-IF */       
        
        atomicState.doorMoving = !atomicState.doorMoving
        def doorStatus = garageControl.currentValue('door')
        if (atomicState.doorMoving) {
            logDebug "Physical door moving"            
            if (doorStatus != 'opening' && doorStatus != 'closing') {
                // switch should drive the garage controller            
                log "Engage garage controller from switch"
                atomicState.openingBySwitch = true
                if (atomicState.lastDoorAction == 'opening') {
                    garageControl.close()
                } else {
                    garageControl.open()
                }
            }
        } else {
            logDebug "Physical door stopped"
            if (doorStatus != 'open' && doorStatus != 'closed') {
                // door stopped in a mid position
                log.warn "${garageControl.label} stopped while ${doorStatus}"
                garageControl.sendEvent(name: "door", value: "unknown", descriptionText: "${garageControl.label} stopped while ${doorStatus}")
            }            
        }
        
        runInMillis(switchOffDelay.toLong(), garageSwitchOff)
    }
}

def garageSwitchOff() {
    garageSwitch.off()
}

def startTimeout() {
    runIn(garageTime.toLong(), handleTimeout)
}

def handleTimeout() {
    def doorStatus = garageControl.currentValue('door')
    if (doorStatus == 'opening') {
        if (openContact) {
            // we have a contact that detects fully open position.
            // however the contact has not yet closed (otherwise the door status would be `open`)
            // this means that the garage door is stuck while opening
            log.warn "${garageControl.label} might be stuck while opening"
            garageControl.sendEvent(name: "door", value: "unknown", descriptionText: "${garageControl.label} might be stuck while opening")
        } else {
            // no contact that detects fully open position.
            // use timeout to set controller to `open`
            garageControl.sendEvent(name: "door", value: "open", descriptionText: "${garageControl.label} is open after ${garageTime}s")
        }
    }
    else if (doorStatus == 'closing') {
        if (openContact) {
            // we have a contact that detects fully closed position.
            // however the contact has not yet closed (otherwise the door status would be `closed`)
            // this means that the garage door is stuck while closing
            log.warn "${garageControl.label} might be stuck while closing"
            garageControl.sendEvent(name: "door", value: "unknown", descriptionText: "${garageControl.label} might be stuck while closing")
        } else {
            // no contact that detects fully closed position.
            // use timeout to set controller to `closed`
            garageControl.sendEvent(name: "door", value: "closed", descriptionText: "${garageControl.label} is closed after ${garageTime}s")
        }
    }
}

def garageOpenContactHandler(evt) {    
    //logDebug "Garage open contact event: ${evt.value}"
    if (evt.value == 'closed' && atomicState.doorMoving) {
        log "${openContact.label} detected that ${garageControl.label} is fully open"
        garageControl.sendEvent(name: "door", value: "open", descriptionText: "${openContact.label} detected that ${garageControl.label} is fully open")
    }
}

def garageClosedContactHandler(evt) {    
    //logDebug "Garage closed contact event: ${evt.value}"
    if (evt.value == 'closed' && atomicState.doorMoving) {
        log "${closedContact.label} detected that ${garageControl.label} is fully closed"
        garageControl.sendEvent(name: "door", value: "closed", descriptionText: "${closedContact.label} detected that ${garageControl.label} is fully closed")
    }
}

def log(msg) {
	if (enableLogging) {
		log.info msg
	}
}

def logDebug(msg) {
	if (enableLogging) {
		log.debug msg
	}
}