/**
 *  Konke Button
 *  Device Driver for Hubitat Elevation hub
 *  Version 0.1.2
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
 *  Based on Xiaomi "Original" Button device handler code v0.8.5
 *
 */

metadata {
	definition (name: "Konke Button", namespace: "muxa", author: "Muxa") {
		capability "PushableButton"
        capability "DoubleTapableButton"
		capability "HoldableButton"
		capability "Battery"

		attribute "lastCheckinEpoch", "String"
		attribute "lastCheckinTime", "String"
		attribute "batteryLastReplaced", "String"
		attribute "buttonPressedEpoch", "String"
		attribute "buttonPressedTime", "String"
		attribute "buttonHeldEpoch", "String"
		attribute "buttonHeldTime", "String"
		attribute "buttonReleasedEpoch", "String"
		attribute "buttonReleasedTime", "String"

		fingerprint endpointId: "01", profileId: "0104", deviceId: "0104", inClusters: "0000,0001,0003,0004,0005,0006", outClusters: "0003", manufacturer: "Konke", model: "3AFE170100510001"

		command "resetBatteryReplacedDate"
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
	def cluster = description.split(",").find {it.split(":")[0].trim() == "cluster"}?.split(":")[1].trim()
	def attrId = description.split(",").find {it.split(":")[0].trim() == "attrId"}?.split(":")[1].trim()
	def encoding = Integer.parseInt(description.split(",").find {it.split(":")[0].trim() == "encoding"}?.split(":")[1].trim(), 16)
	def valueHex = description.split(",").find {it.split(":")[0].trim() == "value"}?.split(":")[1].trim()
	Map map = [:]

	displayDebugLog("Parsing message: ${description}")
	displayDebugLog("Message payload: ${valueHex}")

	// lastCheckinEpoch is for apps that can use Epoch time/date and lastCheckinTime can be used with Hubitat Dashboard
	sendEvent(name: "lastCheckinEpoch", value: now())
	sendEvent(name: "lastCheckinTime", value: new Date().toLocaleString())

	// Send message data to appropriate parsing function based on the type of report
	if (cluster == "0006")
		// Parse button press message
		map = parseButtonMessage(valueHex)
    else if (cluster == "0001" & attrId == "0020")
		// Parse battery level from hourly announcement message
		map = parseBattery(valueHex)
	else 
		displayDebugLog("Unable to parse message")

	if (map != [:]) {
		displayDebugLog("Creating event $map")
		return createEvent(map)
	} else
		return [:]
}

// Parse button message (press, double-click, triple-click, quad-click, and release)
private parseButtonMessage(attrValue) {
	def attribute = ""
    def button = 1
    def descr = ""
    switch (attrValue) {
        case "80": 
            attribute = "pushed"
            descr = "Button was pressed"
            break
        case "81": 
            attribute = "doubleTapped"
            descr = "Button was double-tapped"
            break
        case "82": 
            attribute = "held "
            descr = "Button was held"
            break
        case "CD": 
            attribute = "pushed"
            button = 2
            descr = "Button has reset pressed"
            break
        default:
            return [:]
    }
    updateDateTimeStamp("Pressed")
    
    return [
			name: attribute,
			value: button,
			isStateChange: true,
			descriptionText: descr
		]
}

// Generate buttonPressedEpoch/Time, buttonHeldEpoch/Time, or buttonReleasedEpoch/Time event for Epoch time/date app or Hubitat dashboard use
def updateDateTimeStamp(timeStampType) {
	displayDebugLog("Setting button${timeStampType}Epoch and button${timeStampType}Time to current date/time")
	sendEvent(name: "button${timeStampType}Epoch", value: now(), descriptionText: "Updated button${timeStampType}Epoch")
	sendEvent(name: "button${timeStampType}Time", value: new Date().toLocaleString(), descriptionText: "Updated button${timeStampType}Time")
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

// this call is here to avoid Groovy errors when the Push command is used
// it is empty because the Konke button is non-controllable
def push() {
	displayDebugLog("No action taken on Push Command. This button cannot be controlled.")
}

// this call is here to avoid Groovy errors when the Hold command is used
// it is empty because the Konke button is non-controllable
def hold() {
	displayDebugLog("No action taken on Hold Command. This button cannot be controlled!")
}

// installed() runs just after a sensor is paired
def installed() {
	state.prefsSetCount = 0
	displayInfoLog("Installing")
}

// configure() runs after installed() when a sensor is paired or reconnected
def configure() {
	displayInfoLog("Configuring")
	displayInfoLog("Number of buttons = 2")
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
	sendEvent(name: "numberOfButtons", value: 2)
}