/**
 *  Heiman ZigBee Key Fob
 *  Device Driver for Hubitat Elevation hub
 *  Version 0.1.1
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
	definition (name: "Heiman ZigBee Key Fob", namespace: "muxa", author: "Mikhail Diatchenko") {
		capability "PushableButton"
		capability "Battery"        
        capability "Configuration"

		fingerprint endpointId: "01", profileId: "0104", inClusters: "0000,0001,0003,0500", outClusters: "0003,0501", manufacturer: "HEIMAN", model: "RC-EM"
        
		command "resetBatteryReplacedDate"
        command "identify"
	}

	preferences {
		//Battery Voltage Range
 		input name: "voltsmin", title: "Min Volts (0% battery = ___ volts, range 2.0 to 2.7). Default = 2.5 Volts", description: "", type: "decimal", range: "2..2.7"
 		input name: "voltsmax", title: "Max Volts (100% battery = ___ volts, range 2.8 to 3.4). Default = 3.0 Volts", description: "", type: "decimal", range: "2.8..3.4"
 		//Logging Message Config
		input name: "infoLogging", type: "bool", title: "Enable info message logging", description: ""
		input name: "debugLogging", type: "bool", title: "Enable debug message logging", description: ""
	}
}

// Parse incoming device messages to generate events
def parse(String description) {    
    def descMap = zigbee.parseDescriptionAsMap(description)
    
    displayDebugLog("descMap: ${descMap}")

    def map = [:]
    if (!descMap)
        return map
    
	// Send message data to appropriate parsing function based on the type of report
    if (descMap.clusterId == "0501") {
        map = parseButtonMessage(descMap.command, descMap.data)
    } else if (cluster == "0001" & attrId == "0020")
		// Parse battery level from hourly announcement message
		map = parseBattery(descMap.value)
	else 
		displayDebugLog("Unable to parse message")

	if (map != [:]) {
        displayDebugLog("Creating event ${map}")
		return createEvent(map)
	} else
		return [:]
}

private parseButtonMessage(command, data) {
    def button = 0
    def buttonName = ""
    switch (command) {
        case "00":
            switch (data[0]) {
                case "00":
                    button = 2
                    buttonName = "Disarm"
                    break
                case "01":
                    button = 3
                    buttonName = "Arm Home"
                    break
                case "03":
                    button = 1
                    buttonName = "Arm Away"
                    break
                default:
                    log.warn "Unknown button: ${data[0]}"            
                    return [:]
            }
            break
        case "02":
            // panic button
            button = 4
            buttonName = "Panic"
            break
        default:
            log.warn "Unknown command: ${descMap.command}"            
            return [:]
    }
    
    displayInfoLog("${buttonName} button (${button}) pushed")
    return [
        name: "pushed",
        value: button,
        isStateChange: true,
        descriptionText: "${buttonName} button pushed"
    ]
}

// Convert 2-byte hex string to voltage
// 0x0020 BatteryVoltage -  The BatteryVoltage attribute is 8 bits in length and specifies the current actual (measured) battery voltage, in units of 100mV.
private parseBattery(valueHex) {
	displayDebugLog("Battery parse string = ${valueHex}")
	def rawVolts = hexStrToSignedInt(valueHex)/10
	def minVolts = voltsmin ? voltsmin : 2.5
	def maxVolts = voltsmax ? voltsmax : 3.0
	def pct = (rawVolts - minVolts) / (maxVolts - minVolts)
	def roundedPct = Math.min(100, Math.round(pct * 100))
	def descText = "Battery level is ${roundedPct}% (${rawVolts} Volts)"
	displayInfoLog(descText)
    sendEvent(name: "batteryLevelLastReceived", value: new Date())    
	def result = [
		name: 'battery',
		value: roundedPct,
		unit: "%",
		isStateChange: true,
		descriptionText: descText
	]
	return result
}

private def displayDebugLog(message) {
	if (debugLogging) log.debug "${device.displayName}: ${message}"
}

private def displayInfoLog(message) {
	if (infoLogging || state.prefsSetCount != 1)
		log.info "${device.displayName}: ${message}"
}

//Reset the batteryLastReplaced date to current date
def resetBatteryReplacedDate(paired) {
	def newlyPaired = paired ? " for newly paired sensor" : ""
	sendEvent(name: "batteryLastReplaced", value: new Date())
	displayInfoLog("Setting Battery Last Replaced to current date${newlyPaired}")
}

def identify() {
    displayDebugLog("Sending Identify command to flash device light for 5 seconds")
    return zigbee.command(0x0003, 0x00, "0500")
}

// installed() runs just after a sensor is paired
def installed() {
	state.prefsSetCount = 0
	displayInfoLog("Installing")
}

// configure() runs after installed() when a sensor is paired or reconnected
def configure() {
	displayInfoLog("Configuring")
	displayInfoLog("Number of buttons = 4")
	init()
	state.prefsSetCount = 1
	return
}

// updated() runs every time user saves preferences
def updated() {
	displayInfoLog("Updating preference settings")
	init()
	displayInfoLog("Info message logging enabled")
	displayDebugLog("Debug message logging enabled")
}

def init() {
	if (!device.currentState('batteryLastReplaced')?.value)
		resetBatteryReplacedDate(true)
	sendEvent(name: "numberOfButtons", value: 4)
}