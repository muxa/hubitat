/**
 *  Scene Switch v1.0.1
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
	parent: "muxa:Scene Switch",
    name: "Scene Switch Instance",
    namespace: "muxa",
    author: "Mikhail Diatchenko",
    description: "Child app that is instantiated by the Scene Switch app.",
    category: "Convenience",
	iconUrl: "",
    iconX2Url: "",
    iconX3Url: "")

def switches0 = [
		name:				"switches0",
		type:				"capability.switch",
		title:				"Switches Scene 0 (off)",
		description:		"Switches to turn off by the scene switch (Scene 0).",
		multiple:			true,
		required:			true
	]

def switches1 = [
		name:				"switches1",
		type:				"capability.switch",
		title:				"Switches Scene 1",
		description:		"Switches to turn on by the scene switch for Scene 1.",
		multiple:			true,
		required:			true
	]

def switches2 = [
		name:				"switches2",
		type:				"capability.switch",
		title:				"Switches Scene 2",
		description:		"Switches to turn on by the scene switch for Scene 2.",
		multiple:			true,
		required:			false
	]

def switches3 = [
		name:				"switches3",
		type:				"capability.switch",
		title:				"Switches Scene 3",
		description:		"Switches to turn on by the scene switch for Scene 3.",
		multiple:			true,
		required:			false
	]

def sceneSwitch = [
		name:				"sceneSwitch",
		type:				"capability.switch",
		title:				"Scene Switch",
		multiple:			false,
		required:			true
	]

def nameOverride = [
		name:				"nameOverride",
		type:				"string",
		title:				"Name",
		multiple:			false,
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
	page(name: "mainPage", title: "", install: true, uninstall: true) {
		section("<h2>Scene Switch</h2>") {
			input nameOverride
		}
		section("") {
			input sceneSwitch
			input switches0
			input switches1
			input switches2
			input switches3
			paragraph "<b>WARINING:</b> do not include the Scene Switch"
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
	app.updateLabel(nameOverride)
	
	state.currentScene = 0 // off
	setupSubscriptions()       

	log "Initialised ${nameOverride}"
}

def getNextScene() {
	switch (state.currentScene) {
		case -1:
			return 0
		case 0:
			return 1
		case 1:
			if (switches2 && switches2.size() > 0) {
				return 2
			}
			if (switches3 && switches3.size() > 0) {
				return 3
			}
			return 0
		case 2:
			if (switches3 && switches3.size() > 0) {
				return 3
			}
			return 0
		case 3: 
			return 0
	}
}

def getSceneSwitches(sceneNumber) {
	switch (sceneNumber) {
		case 0:
			return switches0
		case 1:
			return switches1
		case 2:
			return switches2
		case 3: 
			return switches3
	}
}

def applySwitchQueue(queue) {    
    log.debug "Apply switch queue: ${queue} (${queue.size()})"
    
    if (queue.size() == 0) {
        state.remove('switchQueue')
        return 
    }
    
    state.switchQueue = queue
    
    // TODO: add a delay to automatically clear queue if it takes to long
    
    switches0.each { s -> 
        def targetValue = queue.get(s.id)        
        //log.debug "Target value for ${s.id}: ${targetValue}"
        switch (targetValue) {
            case 'on':
                log.debug "Turn ${s} on"
                s.on()
                break
            case 'off':
                log.debug "Turn ${s} off"
                s.off()
                break
        }
    }
}

def activateScene(sceneNumber) {
	log "Activate scene ${sceneNumber}"

	def previousScene = state.currentScene
	state.currentScene = sceneNumber

	if (sceneNumber == 0) {
        
        def switchQueue = [:]
        
        switches0
            .findAll { it.currentValue('switch') == 'on' }
            .each { switchQueue[it.id] = 'off' }
        
        applySwitchQueue(switchQueue)
        
	} else {
		def previousSwitches = getSceneSwitches(previousScene)
		def currentSwitches = getSceneSwitches(sceneNumber)
        def switchQueue = [:]
        //log "Previous scene ${previousScene}. Switches: ${previousSwitches}"
        previousSwitches.each { s -> 
            def onSwitch = currentSwitches.find { it.id == s.id }
            if (onSwitch == null) {                
                if (s.currentValue('switch') == 'on') {
                    //log "- ${s} turn off"
                    switchQueue[s.id] = 'off'
                } else {
                    //log "- ${s} is already off"
                }
            } else {
                //log "- ${s} keep on for next scene"
            }
        }
		
        //log "Current scene ${sceneNumber}. Switches ${currentSwitches}"
        currentSwitches.each { s -> 
            if (s.currentValue('switch') == 'off') {
                //log "- ${s} turn on"
                switchQueue[s.id] = 'on'
            } else {
                //log "- ${s} already on"
            }
        }
        
        applySwitchQueue(switchQueue)
	}
}

def sceneSwitchHandler(evt) {
    log "Scene switch ${evt.value}"

	if (evt.value == "off") {
		// if next scene is available then turn back on and activate scene
		def nextScene = getNextScene()
        log "Next scene: ${nextScene} (from ${state.currentScene})"
		activateScene(nextScene)
		if (nextScene == 0) {
			// leave the scene switch off
		} else {
			// toggle scene switch back of for the next scene
			bypassSceneSwitchSubscription {
				sceneSwitch.on()
			}
		}
	} else {
		// switch on first scene
		activateScene(1)
	}
}

def linkedSwitchHandler(evt) {    
    log "Linked switch ${evt.displayName} is ${evt.value}."
       
    def deviceId = evt.getDeviceId().toString()
    if (state.switchQueue?.containsKey(deviceId)) {
        // linked switch toggled by master switch        
        state.switchQueue.remove(deviceId)
        if (state.switchQueue.size() == 0) {
            log "Scene ${state.currentScene} activated"
        } else {
            log "Waiting for queue to be processed: ${state.switchQueue}"
        }
    } else {
        // linked switch turned manually. 
        // detect scene and set scene switch accordingly
    
	    if (evt.value == "off") {
		    if (allSwitchesAreOff()) {
			    // all switches are off. activate scene 0 and turn the scene switch off
			    state.currentScene = 0
			    log "All linked switches are off. Turn off the scene switch"
			
			    bypassSceneSwitchSubscription {
				    sceneSwitch.off()
			    }
		    }
        } else if (sceneSwitch.currentValue("switch") == "off") {
		    state.currentScene = -1
		    log "Scene override. Turning scene switch on" // because one of the linked switched switched on
		    // TODO: detect scene
		    bypassSceneSwitchSubscription {
    			sceneSwitch.on()
		    }
	    }
    }
}

def allSwitchesAreOff() {
    return switches0.find { it.currentValue("switch") == "on" } == null
}

def bypassSceneSwitchSubscription(Closure callback) {	
    // we need to bypass subscriptions, so that we don't get a feedback switches what we toggle in `callback`
    // to do that we need to delay restoring subscriptions, since switch events arrive asynchrounousl
	log "Unsubscribe from scene switch events"
    unsubscribe(sceneSwitch, "switch", 'sceneSwitchHandler')

	callback();

	// restore subscription
    runInMillis(500, 'setupSceneSwitchSubscription')
}

def setupSubscriptions() {
    setupSceneSwitchSubscription()
    setupLinkedSwitchesSubscription()
}

def setupSceneSwitchSubscription() {
    log "(Re)subscribe to scene switch events"
    
    subscribe(sceneSwitch, "switch", sceneSwitchHandler)    
}

def setupLinkedSwitchesSubscription() {
    log "(Re)subscribe to linked switches events"
    
    subscribe(switches0, "switch", linkedSwitchHandler)
    // TODO: make sure that all switches are handled (assuming switches0 contains all switches from other scenes)
    //subscribe(switches1, "switch", linkedSwitchHandler)
    //subscribe(switches2, "switch", linkedSwitchHandler)
    //subscribe(switches3, "switch", linkedSwitchHandler)
}

def log(msg) {
	if (enableLogging) {
		log.debug msg
	}
}