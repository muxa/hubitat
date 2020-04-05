/**
 *  Virtual Simple Garage Door Controller
 *  Device Driver for Hubitat Elevation hub
 *  Version 1.0.0
 *
 *  This is a simple virtual garage door controller driver that doos not change its state automatically. 
 *  Used by the Garage Opener app.
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

metadata {
	definition (name: "Virtual Simple Garage Door Controller", namespace: "muxa", author: "Mikhail Diatchenko") {
		capability "GarageDoorControl"
	}

	preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
	}
}

//handler for hub to hub integrations
void parse(String description) {
    log.debug "parse ${description}"
}

def installed() {
    log.debug "installed"
    sendEvent(name: "door", value: "unknown")
}

def configure() {
    log.debug "configure"
}

def updated() {
	log.debug "updated"
}

def close() {
    if (device.currentValue("door") != "closed") {
        displayInfoLog "closing"
        sendEvent(name: "door", value: "closing")
    }
}

def open() {
    if (device.currentValue("door") != "open") {
        displayInfoLog "opening"
        sendEvent(name: "door", value: "opening")
    }
}

private def displayDebugLog(message) {
	if (debugLogging) log.debug "${device.displayName}: ${message}"
}

private def displayInfoLog(message) {
	log.info "${device.displayName}: ${message}"
}