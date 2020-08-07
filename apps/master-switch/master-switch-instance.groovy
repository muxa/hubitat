/**
 *  Master Switch v1.1.0
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
	parent: "muxa:Master Switch",
    name: "Master Switch Instance",
    namespace: "muxa",
    author: "Muxa",
    description: "Child app that is instantiated by the Master Switch app.",
    category: "Convenience",
	iconUrl: "",
    iconX2Url: "",
    iconX3Url: "")

def switches = [
		name:				"switches",
		type:				"capability.switch",
		title:				"Linked Switches",
		description:		"Select switches to link.",
		multiple:			true,
		required:			true
	]

def masterSwitch = [
		name:				"masterSwitch",
		type:				"capability.switch",
		title:				"Master Switch",
		multiple:			false,
		required:			true
	]

def restoreStates = [
		name:				"restoreStates",
		type:				"bool",
		title:				"Restore Previous Light States",
		defaultValue:		true,
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

preferences {
	page(name: "mainPage", title: "", install: true, uninstall: true) {
		section("<h2>Master Switch</h2>") {
		}
		section("") {
			input masterSwitch
			input switches
			paragraph "<b>WARINING:</b> do not include the Master Switch"
		}
		section ("<b>Advanced Settings</b>", hideable: true, hidden: true) {
			paragraph "<br/><b>OPTIONAL:</b> Override the displayed name of the binding."
			input nameOverride
			paragraph "<br/>Restore previous light states (TRUE) or always turn on all lights (FALSE)"
			input restoreStates
		}
		section () {
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
	def newLabel = "${masterSwitch.displayName} to"
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
	
	// create default state for the linked switches (all on)
	state.switchStates = [:]
	switches.each { s -> 
		state.switchStates[s.deviceId.toString()] = "on"
	}

	setupSubscriptions()

	log "Initialised ${newLabel}"
}

def masterSwitchHandler(evt) {
    log "Master switch ${evt.value}"
    
    if (evt.value == "on") {
		// restore previously on switches OR all switches if restoreStates is False
		log "restoreStates is set to $restoreStates. (True = restore states; False = turn on all lights)."
		bypassLinkedSwitchesSubscription {
			switches.each { s -> 
				def switchState = state.switchStates[s.deviceId.toString()]
				if (switchState == "on" || !restoreStates) {
					log "Restore ${s.displayName} to on"
					s.on()
				}
			}
		}
    } else {
        // remember last state of switches	
	    def switchStates = [:]
        switches.each { s -> 
            switchStates[s.deviceId.toString()] = s.currentValue("switch")
        }	
	    log "Remembering linked switches state: ${switchStates}"
	    state.switchStates = switchStates

	    // switch all switches off
	    bypassLinkedSwitchesSubscription {
		    switches.each { s -> 
                if (s.currentValue('switch') == "on") {
			        s.off()
                }
		    }
	    }
    }
}

def linkedSwitchHandler(evt) {
	// log "Linked switch ${evt.displayName} event: ${evt.name} ${evt.value} ${evt.deviceId}"
    log "Linked switch ${evt.displayName} is ${evt.value}"
    
    state.switchStates[evt.deviceId.toString()] = evt.value.toString()    
    
	if (evt.value == "on") {
    	log "Remembering linked switches state: ${state.switchStates}"

        // if any of the linked switches are on the master switch should be on
        if (masterSwitch.currentValue('switch') == "off") {
	        bypassMasterSwitchSubscription {            
		        masterSwitch.on()
	        }
        }
	} else if (allSwitchesAreOff()) {
        // Remember the last switch as on, so we can restore it by switching the master switch on
        state.switchStates[evt.deviceId.toString()] = "on"        
        log "Remembering linked switches state: ${state.switchStates}"
        
        log "All linked switches are off. Turn off the master switch"
        // all linked switches are off so switch the master switch off    
        bypassMasterSwitchSubscription {
            masterSwitch.off()
        }
    } else {
        // some switches are swill on, so keep the master switch on
        log "Remembering linked switches state: ${state.switchStates}"
	}
}

def allSwitchesAreOff() {
    return switches.find { it.currentValue("switch") == "on" } == null
}

def bypassMasterSwitchSubscription(Closure callback) {	
    // we need to bypass subscriptions, so that we don't get a feedback switches what we toggle in `callback`
    // to do that we need to delay restoring subscriptions, since switch events arrive asynchrounousl
	log "Unsubscribe from master switch events"
    unsubscribe(masterSwitch, "switch", 'masterSwitchHandler')

	callback();

	// restore subscription
    runInMillis(500, 'setupMasterSwitchesSubscription')
}

def bypassLinkedSwitchesSubscription(Closure callback) {
    // we need to bypass subscriptions, so that we don't get a feedback switches what we toggle in `callback`
    // to do that we need to delay restoring subscriptions, since switch events arrive asynchrounously
    
	log "Unsubscribe from linked switches events"
	unsubscribe(switches, "switch", 'linkedSwitchHandler')
	
	callback();

	// restore subscription
    runInMillis(500, 'setupLinkedSwitchesSubscription')
}

def setupSubscriptions() {
    setupMasterSwitchesSubscription()
    setupLinkedSwitchesSubscription()
}

def setupMasterSwitchesSubscription() {
    log "(Re)subscribe to master switch events"
    
    subscribe(masterSwitch, "switch", masterSwitchHandler)    
}

def setupLinkedSwitchesSubscription() {
    log "(Re)subscribe to linked switches events"
    
    subscribe(switches, "switch", linkedSwitchHandler)
}

def log(msg) {
	if (enableLogging) {
		log.debug msg
	}
}