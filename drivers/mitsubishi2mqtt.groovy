/*
 * Mitsubishi Air Conditioner MQTT
 *  Device Driver for Hubitat Elevation hub
 *  Version 0.1.0
 * 
 * Control Mitsubishi AC via MQTT using ESP8266 (https://github.com/gysmo38/mitsubishi2MQTT)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */
metadata {
    definition (name: "Mitsubishi2MQTT", namespace: "muxa", author: "Mikhail Diatchenko") {
        capability "Initialize"
        capability "Sensor"
        capability "TemperatureMeasurement"
        capability "Thermostat"
    }
}

preferences {
    section("URIs") {
        input "mqttBroker", "string", title: "MQTT Broker Address:Port", required: true
		input "mqttTopic", "string", title: "MQTT Topic", description: "(e.g. mitsubishi2mqtt/bedroom)", required: true
        input "tempSmoothing", "integer", title: "Temperature smoothing", description: "Number of readings to average for smoother temperature changes", required: true, defaultValue: 5
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

import groovy.transform.Field

@Field static Map heatpumpFanToHubitatFanMode = [
    "QUIET": "circulate",
    "1": "circulate",
    "2": "on",
    "3": "on",
    "4": "on",
    "AUTO": "auto"
] // supported enums: ENUM ["auto", "circulate", "on"]
        
@Field static Map heatpumpModeToHubitatMode = [
    "heat": "heat",
    "cool": "cool",
    "fan_only": "fan",
    "dryu": "dry", // there's no "dry" mode, so need to test what happens 
    "auto": "auto",
    "off": "off"
] // supported enums: ENUM ["auto", "off", "heat", "emergency heat", "cool"]

@Field static Map heatpumpModeToHubitatOperatingState = [
    "heat": "heating",
    "cool": "cooling",
    "fan_only": "fan only",
    "dry": "drying", // there's no "dry" mode, so need to test what happens 
    "heat_cool": "vent economizer",
    "off": "idle"
] // supported enums: ENUM ["heating", "pending cool", "pending heat", "vent economizer", "idle", "cooling", "fan only"]

def installed() {
    logDebug "Installed"

    sendEvent(name: "thermostatMode", value: "off", descriptionText: "Initialized to OFF")
}

def parse(String description) {
    //logDebug description
	
    mqtt = interfaces.mqtt.parseMessage(description)
	//logDebug mqtt
	
    def events = [:]
    
    if (mqtt.topic.endsWith('/set')) {
        // feedback from set command. do nothing
    } else if (mqtt.topic.endsWith('/state')) {
        json = new groovy.json.JsonSlurper().parseText(mqtt.payload)
	    logDebug json

        def previousStatus = state.status
        state.status = json

        // temperature reading
        def roomTemp = smoothenTemperatureChange(convertCelciusToLocalTemp(json.roomTemperature))
        events.temperature = [name: 'temperature', value: roomTemp, unit: "°${location.temperatureScale}", descriptionText: "Temperature is ${roomTemp}°${location.temperatureScale}", translatable:true]

        /*
        if (!json.operating) {
            // `operating` status only works with COOL, HEAT and AUTO modes, so in theres modes we can use to to detect idle state
            switch (state.status?.mode) {
                case 'COOL':
                case 'HEAT':
                case 'AUTO':
                    events.thermostatOperatingState = [name: "thermostatOperatingState", value: "idle"]
                    break
            }
        }
        */

        // "ON",  /* ON/OFF */
        // "FAN", /* HEAT/COOL/FAN/DRY/AUTO */
        // 26,    /* Between 16 and 31 */
        // "4",   /* Fan speed: 1-4, AUTO, or QUIET */
        // "3",   /* Air direction (vertical): 1-5, SWING, or AUTO */
        // "|"    /* Air direction (horizontal): <<, <, |, >, >>, <>, or SWING */

        if (previousStatus?.temperature != json.temperature || previousStatus?.mode != json.mode) {
            def temp = convertCelciusToLocalTemp(json.temperature)
            switch (json.mode) {
                case 'heat':
                    if (device.currentValue("heatingSetpoint") != temp) {
                        events.heatingSetpoint = [name: "heatingSetpoint", value: temp, unit: "°${location.temperatureScale}"]
                    }
                    break
                case 'cool':
                    if (device.currentValue("coolingSetpoint") != temp) {
                        events.coolingSetpoint = [name: "coolingSetpoint", value: temp, unit: "°${location.temperatureScale}"]
                    }
                    break
                default:
                    log.warn "Ignoring temperature setpoint in ${json.mode} mode"
                    break
            }
        }

        if (previousStatus?.mode != json.mode) {
            events.thermostatMode = [name: "thermostatMode", value: heatpumpModeToHubitatMode[json.mode]]
            events.thermostatOperatingState = [name: "thermostatOperatingState", value: heatpumpModeToHubitatOperatingState[json.mode]]
        }
        
        if (previousStatus?.fan != json.fan || previousStatus?.mode != json.mode) {
            if (json.mode == "off") {
                // when unit is OFF fan in 'auto' is considered off
                events.thermostatFanMode = [name: "thermostatFanMode", value: "auto"]                
            } else {
                events.thermostatFanMode = [name: "thermostatFanMode", value: heatpumpFanToHubitatFanMode[json.fan]]
            }
        }
    } else if (mqtt.topic.endsWith('/debug')) {
        logDebug mqtt.payload
    } else {
        logDebug "Unhandled topic ${mqtt.topic}: ${mqtt.payload}"
    }

    events.each {
        sendEvent(it.value)
    }
}

def smoothenTemperatureChange(temp) {
    // average the temperature to avoid jumping up and down between two values, e.g. between 22 and 23 when temperature is 22.5
    def averageTemp = state.averageTemperature ? state.averageTemperature : temp
    def smoothing = tempSmoothing ? tempSmoothing.toInteger() : 5
    averageTemp = Math.round((temp + averageTemp * smoothing) / (smoothing + 1) * 1000000000)/1000000000
    state.averageTemperature = averageTemp
    temp = Math.round(averageTemp)
}

def on() {
    logInfo "Turning On"
    publishCommand("power", "on")
}

def off() {
    logInfo "Turning Off"
    publishCommand("power", "off")
}

def auto() {
    logInfo "Setting to Auto"
    // publishCommand("power", "ON")
    publishCommand("mode", "auto")
    // publishCommand("fan", "AUTO")
}

def cool() {
    logInfo "Setting to Cool (Auto)"
    sendEvent(name: "thermostatOperatingState", value: "pending cool")
    // publishCommand("power", "ON")
    publishCommand("mode", "cool")
}

def emergencyHeat() {
    logInfo "Setting to Heat (Max)"
    sendEvent(name: "thermostatOperatingState", value: "pending heat")
    // publishCommand("power", "ON")
    publishCommand("mode", "HEAT")
    publishCommand("fan", "4")
}

def fanAuto() {
    // this can be called from Home Bridge when turning off the Fan
    if (state.status?.mode != 'off') {
        logInfo "Setting to Fan (Auto)"
        // publishCommand("power", "ON")
        // publishCommand("mode", "FAN")
        publishCommand("fan", "auto")
    }
}

def fanCirculate() {
    logInfo "Setting to Fan (Quiet)"
    // publishCommand("power", "ON")
    // publishCommand("mode", "FAN")
    publishCommand("fan", "QUIET")
}

def fanOn() {
    logInfo "Setting to Fan (On)"
    // use previous fan speed or default to lowest setting
    // publishCommand("power", "ON")
    publishCommand("mode", "fan")
    // publishCommand("fan", state.status?.fan ?: "1")
}

def heat() {
    logInfo "Setting to Heat (Auto)"
    sendEvent(name: "thermostatOperatingState", value: "pending heat")
    // publishCommand("power", "ON")
    publishCommand("mode", "heat")
}

def setCoolingSetpoint(temperature) {    
    if (state.status?.mode == "cool") {
        logInfo "Setting cooling temperature to ${temperature}"
        publishCommand("temp", convertLocalToCelsiusTemp(temperature))
    } else {
        log.warn "Not setting cooling temperature to ${temperature}, since current mode is ${state.status?.mode}"
    }
}

def setHeatingSetpoint(temperature) {    
    if (state.status?.mode == "heat") {
        logInfo "Setting heating temperature to ${temperature}"
        publishCommand("temp", convertLocalToCelsiusTemp(temperature))
    } else {
        log.warn "Not setting heating temperature to ${temperature}, since current mode is ${state.status?.mode}"
    }
}

def setThermostatFanMode(fanmode) {
    logInfo "Setting Fan to ${fanmode}"
    // Hubitat: ENUM ["on", "circulate", "auto"]
    // Heatpump: Fan speed: 1-4, AUTO, or QUIET
    switch(fanmode) {
        case "on":
            // use previous fan speed or default to lowest setting
            publishCommand("fan", state.status?.fan ?: "1")
            break
        case "circulate":
            publishCommand("fan", "QUIET")
            break
        case "auto":
            publishCommand("fan", "AUTO")
            break
        // below modes can be triggerred from Dashboards
        case "quiet":
            publishCommand("fan", "QUIET")
            break
        case "1":
        case "2":
        case "3":
        case "4":
            publishCommand("fan", fanmode)
            break
        default:
            log.warn "Unknown fan mode ${fanmode}"
            break
    }
}

def setThermostatMode(thermostatmode) {
    logInfo "Setting to ${thermostatmode}"
    // Hubitat: ENUM ["auto", "off", "heat", "emergency heat", "cool"]
    // Heatpump: HEAT/COOL/FAN/DRY/AUTO
    switch(thermostatmode) {
        case "auto":
            auto()
            break
        case "off":
            off()
            break
        case "heat":
            heat()
            break
        case "emergency heat":
            emergencyHeat()
            break
        case "cool":
            cool()
            break
        // below modes can be triggerred from Dashboards
        case "fan":
            //publishCommand("power", "ON")
            publishCommand("mode", "FAN")
            break
        case "dry":
            // publishCommand("power", "ON")
            publishCommand("mode", "DRY")
            break
        default:
            log.warn "Unknown mode ${thermostatmode}"
            break
    }
}

def publishCommand(command, value) {
    def topic = "${settings.mqttTopic}/${command}/set"
    logDebug "Publish ${topic}: ${value}"
    interfaces.mqtt.publish(topic, "${value}")
}

def convertCelciusToLocalTemp(temp) {
    return (location.temperatureScale == "F") ? ((temp * 1.8) + 32) : temp
}

def convertLocalToCelsiusTemp(temp) {
    return (location.temperatureScale == "F") ? Math.round((temp - 32) / 1.8) : temp
}

def updated() {
    logDebug "Updated"
    
    /* Fan speed: 1-4, AUTO, or QUIET */
    sendEvent(name: "supportedThermostatFanModes", value:  ["1", "2", "3", "4", "auto", "quiet"])
     /* HEAT/COOL/FAN/DRY/AUTO */
    sendEvent(name: "supportedThermostatModes", value:  ["heat", "cool", "fan", "dry", "auto", "off"])
    
    initialize()
}

def uninstalled() {
    logDebug "Uninstalled"
    disconnect()
}

def disconnect() {
    log.info "Disconnecting from MQTT"
    interfaces.mqtt.unsubscribe(settings.mqttTopic)
    interfaces.mqtt.disconnect()
}

def delayedConnect() {
    // increase delay by 5 seconds every time, to max of 1 hour
    if (state.delay < 3600)
        state.delay = (state.delay ?: 0) + 5

    logDebug "Reconnecting in ${state.delay}s"
    runIn(state.delay, connect)
}

def initialize() {
    logDebug "Initialize"
    state.delay = 0
    connect()
}

def connect() {
    try {
        // open connection
        log.info "Connecting to ${settings.mqttBroker}"
        interfaces.mqtt.connect("tcp://" + settings.mqttBroker, "hubitat_mitsu2mqtt_${device.id}", null, null)
        // subscribe once received connection succeeded status update below        
    } catch(e) {
        log.error "MQTT Connect error: ${e.message}."
        delayedConnect()
    }
}

def subscribe() {
    interfaces.mqtt.subscribe(settings.mqttTopic+'/#')
    logDebug "Subscribed to topic ${settings.mqttTopic}/#"
}

def mqttClientStatus(String status){
    // This method is called with any status messages from the MQTT client connection (disconnections, errors during connect, etc) 
    // The string that is passed to this method with start with "Error" if an error occurred or "Status" if this is just a status message.
    def parts = status.split(': ')
    switch (parts[0]) {
        case 'Error':
            log.warn status
            switch (parts[1]) {
                case 'Connection lost':
                case 'send error':
                    state.delay = 0
                    delayedConnect()
                    break
            }
            break
        case 'Status':
            log.info "MQTT ${status}"
            switch (parts[1]) {
                case 'Connection succeeded':
                    state.remove('delay')
                    // without this delay the `parse` method is never called
                    // (it seems that there needs to be some delay after connection to subscribe)
                    runInMillis(1000, subscribe)
                    break
            }
            break
        default:
            logDebug "MQTT ${status}"
            break
    }
}

def logInfo(msg) {
    if (logEnable) log.info msg
}

def logDebug(msg) {
    if (logEnable) log.debug msg
}