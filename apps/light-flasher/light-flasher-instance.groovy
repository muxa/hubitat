/**
 *  Light Flasher v1.0.0
 *
 *  Copyright 2019 Mikhail Diatchenko (@muxa)
 * 
 *  Based on Switch Bindings Instance code by Joel Wetzel
 *
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

import groovy.json.*
	
definition(
	parent: "muxa:Light Flasher",
    name: "Light Flasher Instance",
    namespace: "muxa",
    author: "Mikhail Diatchenko",
    description: "Child app that is instantiated by the Light Flasher app.",
    category: "Convenience",
	iconUrl: "",
    iconX2Url: "",
    iconX3Url: "")

def switches = [
		name:				"switches",
		type:				"capability.switch",
		title:				"Light Switches",
		description:		"Select switches to flash.",
		multiple:			true,
		required:			true
	]

def controlSwitch = [
		name:				"controlSwitch",
		type:				"capability.switch",
		title:				"Control Switch",
		description:		"Select switch to control flashing.",
		multiple:			false,
		required:			true
	]

def nameOverride = [
		name:				"nameOverride",
		type:				"string",
		title:				"Name",
		multiple:			false,
		required:			false
	]

def enableLogging = [
		name:				"enableLogging",
		type:				"bool",
		title:				"Enable Debug Logging?",
		defaultValue:		false,
		required:			true
	]

def flashingPeriod = [
		name:				"flashingPeriod",
		type:				"number",
		title:				"Flash duration (in seconds)",
		defaultValue:		1,
		required:			true,
        range: '1..360'
	]

def flashes = [
		name:				"flashes",
		type:				"number",
		title:				"Number of flashes (0 for indefinite)",
		defaultValue:		0,
		required:			true,
        range: '0..9999'
	]

def rememberLights = [
		name:				"rememberLights",
		type:				"bool",
		title:				"Restore light state after flashing?",
		defaultValue:		true,
		required:			true
	]

preferences {
	page(name: "mainPage", title: "", install: true, uninstall: true) {
		section("<h2>Light Flasher</h2>") {
		}
		section("") {
			input controlSwitch
			input switches
			input flashingPeriod
			input flashes
			input rememberLights
		}
		section ("<b>Advanced Settings</b>", hideable: true, hidden: true) {
			paragraph "<br/><b>OPTIONAL:</b> Override the displayed name of the binding."
			input nameOverride
			input enableLogging
		}
	}
}

def installed() {
	log.info "Installed with settings: ${settings}"

	initialize()
}


def updated() {
	log.info "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	// Generate a label for this child app
	def newLabel = "${controlSwitch.displayName} to"
	for (def i = 0; i < switches.size(); i++) {
		newLabel = newLabel + " ${switches[i].displayName}"
		if (i < (switches.size() - 1)) {
			newLabel = newLabel + ", "
		}
	}
	
	if (nameOverride && nameOverride.size() > 0) {
		newLabel = nameOverride	
	}
	
	app.updateLabel(newLabel)
	
	state.flashCount = 0
	state.switchStates = [:]

    log "Subscribe to control switch events"    
    subscribe(controlSwitch, "switch", controlSwitchHandler) 

	log "Initialised ${newLabel}"
}

def controlSwitchHandler(evt) {
    log "Control switch ${evt.value}"
    
    if (evt.value == "on") {		
		// start flashing
		log "Start flashing"
		// remember light state
		switches.each { s -> 
			state.switchStates[s.deviceId.toString()] = s.currentValue('switch')
		}		
		state.flashCount = 0
		schedule("0/${flashingPeriod} * * * * ? *", toggleFlash)
    } else {		
        // stop flashing
		log "Stop flashing"
		unschedule(toggleFlash)
		if (rememberLights) {
			// restore lights state
			switches.each { s -> 
				def switchState = state.switchStates[s.deviceId.toString()]
				if (switchState == "on") {
					log "Restore ${s.displayName} to on"
					s.on()
				} else {
					log "Restore ${s.displayName} to off"
					s.off()
				}
			}
		} else {
			// switch lights off
			switches.each { s -> 
				s.off()
			}
		}
    }
}

def toggleFlash() {
	state.flashCount++
	
	// toggle
	if ((state.flashCount % 2) == 1) {
	    log "Toggle on"
        switches.each { s -> 
			s.on()
		}
	} else {
	    log "Toggle off"
        switches.each { s -> 
			s.off()
		}
    }
	
    if (flashes > 0 && state.flashCount >= (flashes * 2)) {
		// stop flashing
		controlSwitch.off()
	}
}

def log(msg) {
	if (enableLogging) {
		log.debug msg
	}
}