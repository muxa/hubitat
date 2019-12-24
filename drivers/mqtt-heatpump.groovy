/*
 * ESP8266 Heatpump control (https://github.com/SwiCago/HeatPump) MQTT driver
 * (Work in progress)
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
    definition (name: "MQTT Heatpump", namespace: "muxa", author: "Mikhail Diatchenko") {
        capability "Sensor"
        // capability "TemperatureMeasurement"
        capability "Thermostat"
        capability "FanControl"
        capability "Actuator"
        capability "Switch"
        
        command "reconnect"
    }
}

preferences {
    section("URIs") {
        input "mqttBroker", "string", title: "MQTT Broker Address:Port", required: true
		input "mqttTopic", "string", title: "MQTT Topic (e.g. heatpump/bedroom)", required: true
        input "mqttControlTopic", "string", title: "MQTT Control Topic (e.g. heatpump/bedroom/set)", required: true
		input "mqttStatusTopic", "string", title: "MQTT Status Topic (e.g. heatpump/bedroom/status)", required: true
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def installed() {
    log.info "Installed..."
}

def parse(String description) {
    //logDebug description
	
    mqtt = interfaces.mqtt.parseMessage(description)
	//logDebug mqtt
	
	json = new groovy.json.JsonSlurper().parseText(mqtt.payload)
	logDebug json
    
    if (mqtt.topic == settings.mqttStatusTopic) {
        // temperature reading
        def temp = (location.temperatureScale == "F") ? ((json.roomTemperature * 1.8) + 32) : json.roomTemperature
        sendEvent(name: 'temperature', value: temp, unit: "°${location.temperatureScale}", descriptionText: "Temperature is ${temp}°${location.temperatureScale}", translatable:true)        
    } else {
        // heatpump status: [topic:heatpump/master_bedroom, payload:{"power":"ON","mode":"DRY","temperature":21,"fan":"3","vane":"AUTO","wideVane":"|"}]
        
        // "ON",  /* ON/OFF */
        // "FAN", /* HEAT/COOL/FAN/DRY/AUTO */
        // 26,    /* Between 16 and 31 */
        // "4",   /* Fan speed: 1-4, AUTO, or QUIET */
        // "3",   /* Air direction (vertical): 1-5, SWING, or AUTO */
        // "|"    /* Air direction (horizontal): <<, <, |, >, >>, <>, or SWING */
        
        def temp = (location.temperature == "F") ? ((json.temperature * 1.8) + 32) : json.temperature
        
        switch (json.mode) {
            case 'HEAT':
                sendEvent(name: "thermostatMode", value: "heat")
                sendEvent(name: "heatingSetpoint", value: temp, unit: "°${location.temperatureScale}")
                break
            case 'COOL':
                sendEvent(name: "thermostatMode", value: "cool")
                sendEvent(name: "coolingSetpoint", value: temp, unit: "°${location.temperatureScale}")
                break
            case 'FAN':
                sendEvent(name: "thermostatMode", value: "fan")
                break
            case 'DRY':
                sendEvent(name: "thermostatMode", value: "dry")
                break
            case 'AUTO':
                sendEvent(name: "thermostatMode", value: "auto")
                break
        }
        
        if (json.power == "OFF") {
            sendEvent(name: "switch", value: "off", descriptionText: "Turned off")
            sendEvent(name: "thermostatMode", value: "off")
            sendEvent(name: "speed", value: "off")
        } else if (json.power == "ON") {
            sendEvent(name: "switch", value: "on", descriptionText: "Turned on")            
        }
        
        switch (json.fan) {
        case "QUIET":
            sendEvent(name: "speed", value: "low")
            sendEvent(name: "thermostatFanMode", value: "circulate")
            break
        case "1":
            sendEvent(name: "speed", value: "medium-low")
            sendEvent(name: "thermostatFanMode", value: "on")
            break
        case "2":
            sendEvent(name: "speed", value: "medium")
            sendEvent(name: "thermostatFanMode", value: "on")
            break
        case "3":
            sendEvent(name: "speed", value: "medium-high")
            sendEvent(name: "thermostatFanMode", value: "on")
            break
        case "4":
            sendEvent(name: "speed", value: "high")
            sendEvent(name: "thermostatFanMode", value: "on")
            break
        case "AUTO":
            sendEvent(name: "speed", value: "auto")
            sendEvent(name: "thermostatFanMode", value: "auto")
            break
        }
    }
}

def on() {
    logInfo "On"
    publishCommand([ "power": "ON" ])
}

def off() {
    logInfo "Off"
    publishCommand([ "power": "OFF" ])
}

def auto() {
    publishCommand([ "power": "ON", "mode": "AUTO", "fan": "AUTO" ])
}

def cool() {
    publishCommand([ "power": "ON", "mode": "COOL", "fan": "AUTO" ])
}

def emergencyHeat() {
    publishCommand([ "power": "ON", "mode": "HEAT", "fan": "4" ])
}

def fanAuto() {
    publishCommand([ "power": "ON", "mode": "HEAT", "fan": "AUTO" ])
}

def fanCirculate() {
    publishCommand([ "power": "ON", "mode": "FAN", "fan": "QUIET" ])
}

def fanOn() {
    publishCommand([ "power": "ON", "mode": "FAN", "fan": "4" ])
}

def heat() {
    publishCommand([ "power": "ON", "mode": "HEAT", "fan": "AUTO" ])
}

def setCoolingSetpoint(temperature) {
    publishCommand([ "power": "ON", "mode": "COOL", "fan": "AUTO", "temperature": temperature ])
}

def setHeatingSetpoint(temperature) {
    publishCommand([ "power": "ON", "mode": "HEAT", "fan": "AUTO", "temperature": temperature ])
}

def setThermostatFanMode(fanmode) {
    logDebug fanmode
}

def setThermostatMode(thermostatmode) {
    logDebug thermostatmode
}

def setSpeed(fanspeed) {
    logInfo "Set fan speed to ${fanspeed}"
    switch (fanspeed) {
        case "low":
            publishCommand([ "power": "ON", "fan": "QUIET" ])
            break
        case "medium-low":
            publishCommand([ "power": "ON", "fan": "1" ])
            break
        case "medium":
            publishCommand([ "power": "ON", "fan": "2" ])
            break
        case "medium-high":
            publishCommand([ "power": "ON", "fan": "3" ])
            break
        case "high":
            publishCommand([ "power": "ON", "fan": "4" ])
            break
        case "on":
            publishCommand([ "power": "ON" ])
            break
        case "off":
            publishCommand([ "power": "OFF" ])
            break
        case "auto":
            publishCommand([ "power": "ON", "fan": "AUTO" ])
            break
    }
}

def publishCommand(command) {
    logDebug "Publish ${settings.mqttTopic} ${command}"
    def json = new groovy.json.JsonBuilder(command).toString()
    interfaces.mqtt.publish(settings.mqttControlTopic, json)
}

def updated() {
    log.info "Updated..."
    
    /* Fan speed: 1-4, AUTO, or QUIET */
    sendEvent(name: "supportedThermostatFanModes", value:  ["1", "2", "3", "4", "auto", "quiet"])
     /* HEAT/COOL/FAN/DRY/AUTO */
    sendEvent(name: "supportedThermostatModes", value:  ["heat", "cool", "fan", "dry", "auto", "off"])
    
    initialize()
}

def uninstalled() {
    disconnect()
}

def reconnect() {
    disconnect()
    initialize()
}

def disconnect() {
    if (state.connected) {
        log.info "Disconnecting from MQTT"
        interfaces.mqtt.unsubscribe(settings.mqttTopic)
        interfaces.mqtt.unsubscribe(settings.mqttStatusTopic)
        interfaces.mqtt.disconnect()
    }
}

def delayedInitialise() {
    // increase delay by 5 seconds evety time
    state.delay = (state.delay ?: 0) + 5
    logDebug "Reconnecting in ${state.delay}s"
    runIn(state.delay, initialize)
}

def initialize() {
    logDebug "Initialize"
    try {
        // open connection
        interfaces.mqtt.connect("tcp://" + settings.mqttBroker, "hubitat_heatpump_${device.id}", null, null)
        // subscribe once received connection succeeded status update below        
    } catch(e) {
        log.error "MQTT Initialize error: ${e.message}."
        delayedInitialise()
    }
}

def subscribe() {
    interfaces.mqtt.subscribe(settings.mqttTopic)
    logDebug "Subscribed to topic ${settings.mqttTopic}"
    interfaces.mqtt.subscribe(settings.mqttStatusTopic)
    logDebug "Subscribed to topic ${settings.mqttStatusTopic}"
    
    // sendEvent(name: "presence", value: "present", descriptionText: "MQTT connected", isStateChange: true)
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
                    state.connected = false
                    state.delay = 0
                    delayedInitialise()
                    break
                case 'send error':
                    state.connected = false
                    state.delay = 0
                    delayedInitialise()
                    break
            }
            break
        case 'Status':
            log.info "MQTT ${status}"
            switch (parts[1]) {
                case 'Connection succeeded':
                    state.connected = true
                    // without this delay the `parse` method is never called
                    // (it seems that there needs to be some delay after cinnection to subscribe)
                    runInMillis(100, subscribe)
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