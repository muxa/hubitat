/**
 *  Konke ZigBee Temperature Humidity Sensor
 *  Device Driver for Hubitat Elevation hub
 *  Version 0.1.1
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
 *  Based on Xiaomi/Awara Temperature Humidity Sensor device handler code v0.8.2 by @veeceeoh
 *
 */

metadata {
	definition (name: "Konke ZigBee Temperature Humidity Sensor", namespace: "muxa", author: "Mikhail Diatchenko") {
		capability "Battery"
		capability "RelativeHumidityMeasurement"
		capability "TemperatureMeasurement"
		capability "Sensor"

		attribute "lastCheckinEpoch", "String"
		attribute "lastCheckinTime", "Date"
		attribute "batteryLastReplaced", "String"

		fingerprint profileId: "0104", inClusters: "0000,0001,0003,0402,0405", outClusters: "0003", manufacturer: "Konke", model: "3AFE140103020000"

		command "resetBatteryReplacedDate"
	}

	preferences {
		//Temp and Humidity Offsets
		input "tempOffset", "decimal", title:"Temperature Offset", description:"", range:"*..*"
		input "humidityOffset", "decimal", title:"Humidity Offset", description:"", range: "*..*"
		//Battery Voltage Range
		input name: "voltsmin", title: "Min Volts (0% battery = ___ volts, range 2.0 to 2.9). Default = 2.9 Volts", description: "", type: "decimal", range: "2..2.9"
		input name: "voltsmax", title: "Max Volts (100% battery = ___ volts, range 2.95 to 3.4). Default = 3.05 Volts", description: "", type: "decimal", range: "2.95..3.4"
		//Date/Time Stamp Events Config
		input name: "lastCheckinEnable", type: "bool", title: "Enable custom date/time stamp events for lastCheckin", description: ""
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
    if (descMap.cluster == "0402") {
        if (descMap.value == "F8CD")
            displayDebugLog("Reset button was short-pressed")
        else
		    map = parseTemperature(descMap.value)
    } else if (descMap.cluster == "0405")
		map = parseHumidity(descMap.value)
	else if	(descMap.cluster == "0001" & descMap.attrId == "0020")
		map = parseBattery(descMap.value)
	else
		displayDebugLog("Unable to parse message")

	if (map != [:]) {
		displayInfoLog(map.descriptionText)
        displayDebugLog("Creating event ${map}")
		return createEvent(map)
	} else
		return map
}

// Reverses order of bytes in hex string
def reverseHexString(hexString) {
	def reversed = ""
	for (int i = hexString.length(); i > 0; i -= 2) {
		reversed += hexString.substring(i - 2, i )
	}
	return reversed
}

// Calculate temperature with 0.01 precision in C or F unit as set by hub location settings
private parseTemperature(valueHex) {
	float temp = Integer.parseInt(valueHex,16)/100
	temp = (temp > 100) ? (temp - 655.35) : temp
	displayDebugLog("Raw reported temperature = ${temp}°C")
	temp = (location.temperatureScale == "F") ? ((temp * 1.8) + 32) : temp
	temp = tempOffset ? (temp + tempOffset) : temp
	temp = temp.round(2)
	return [
		name: 'temperature',
		value: temp,
		unit: "°${location.temperatureScale}",
		descriptionText: "Temperature is ${temp}°${location.temperatureScale}",
		translatable:true
	]
}

// Calculate humidity with 0.1 precision
private parseHumidity(valueHex) {
	float humidity = Integer.parseInt(valueHex,16)/100
	displayDebugLog("Raw reported humidity = ${humidity}%")
	humidity = humidityOffset ? (humidity + humidityOffset) : humidity
	humidity = humidity.round(1)
	return [
		name: 'humidity',
		value: humidity,
		unit: "%",
		descriptionText: "Humidity is ${humidity}%",
	]
}

// Convert raw 2 digit integer voltage value into percentage based on minVolts/maxVolts range
private parseBattery(valueHex) {
	displayDebugLog("Battery parse string = ${valueHex}")
	def rawValue = Integer.parseInt(valueHex,16)
	def rawVolts = rawValue / 10
	def minVolts = voltsmin ? voltsmin : 2.9
	def maxVolts = voltsmax ? voltsmax : 3.05
	def pct = (rawVolts - minVolts) / (maxVolts - minVolts)
	def roundedPct = Math.min(100, Math.round(pct * 100))
	def descText = "Battery level is ${roundedPct}% (${rawVolts} Volts)"
	return [
		name: 'battery',
		value: roundedPct,
		unit: "%",
		descriptionText: descText
	]
	// lastCheckinEpoch is for apps that can use Epoch time/date and lastCheckinTime can be used with Hubitat Dashboard
	if (lastCheckinEnable) {
		sendEvent(name: "lastCheckinEpoch", value: now())
		sendEvent(name: "lastCheckinTime", value: new Date().toLocaleString())
	}
	return result
}

// installed() runs just after a sensor is paired
def installed() {
	displayDebugLog("Installing")
	state.prefsSetCount = 0
	init()
}

// configure() runs after installed() when a sensor is paired
def configure() {
	displayInfoLog("Configuring")
	init()
	state.prefsSetCount = 1
}

// updated() will run every time user saves preferences
def updated() {
	displayInfoLog("Updating preference settings")
	init()
	if (lastCheckinEnable)
		displayInfoLog("Last checkin events enabled")
	displayInfoLog("Info message logging enabled")
	displayDebugLog("Debug message logging enabled")
}

def init() {
	if (!device.currentValue('batteryLastReplaced'))
		resetBatteryReplacedDate(true)
}

//Reset the batteryLastReplaced date to current date
def resetBatteryReplacedDate(paired) {
	def newlyPaired = paired ? " for newly paired sensor" : ""
	sendEvent(name: "batteryLastReplaced", value: new Date().format("MMM dd yyyy", location.timeZone))
	displayInfoLog("Setting Battery Last Replaced to current date${newlyPaired}")
}

private def displayDebugLog(message) {
	if (debugLogging) log.debug "${device.displayName}: ${message}"
}

private def displayInfoLog(message) {
	if (infoLogging || state.prefsSetCount != 1)
		log.info "${device.displayName}: ${message}"
}