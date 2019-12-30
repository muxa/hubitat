/*
 * ESP8266 MiLight Hub MQTT Light
 *  Device Driver for Hubitat Elevation hub
 *  Version 1.1.0
 *
 * Controls a single RGB light using MiLight Hub (https://github.com/sidoh/esp8266_milight_hub) via MQTT broker.
 *
 * KNOWN ISSUES:
 * - Master On/Off (group 0) does not trigger event for groups 1-4
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

import groovy.transform.Field

@Field static List lightEffects = [
    "Rainbow",
    "White Breathe",
    "RGBW Breathe",
    "RGBW Cycle",
    "Color Cycle",
    "Red Alert",
    "Green Alert",
    "Blue Alert",
    "Loop All Effects"
]

metadata {
    definition (name: "MiLight MQTT Light", namespace: "muxa", author: "Mikhail Diatchenko") {
        capability "Actuator"
        capability "Switch"
        capability "SwitchLevel"
        capability "Light"
        capability "LightEffects"
        capability "ColorControl"
        
        command "reconnect"

        attribute "effectNumber", "number"
    }
}

preferences {
    section("URIs") {
        input "mqttBroker", "string", title: "MQTT Broker Address:Port", required: true
		input "mqttTopic", "string", title: "MQTT Control Topic", required: true
		input "mqttUpdatesTopic", "string", title: "MQTT Updates Topic", required: true
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def installed() {
    def effectMap = lightEffects.collect { element, index ->
      [(index): element] 
    }
    def le = new groovy.json.JsonBuilder(lightEffects)
    sendEvent(name:"lightEffects",value:le)

    log.info "Installed..."
}

def parse(String description) {
    //logDebug description
	
    mqtt = interfaces.mqtt.parseMessage(description)
	//logDebug mqtt
	
	json = new groovy.json.JsonSlurper().parseText(mqtt.payload)
	logDebug json

    if (state.check > 0) {
        state.check++
    }
    
    if (json.state == "OFF") {
        sendEvent(name: "switch", value: "off")
    } else if (json.state == "ON") {
        sendEvent(name: "switch", value: "on")
    }
    
    if (json.brightness >= 0) {
        def level = Math.round(json.brightness.toInteger() / 2.55)
        sendEvent(name: "level", value: level, unit: "%", isStateChange: true)
    }    
    
    if (json.hue >= 0) {
        def hue = json.hue.toInteger()
        sendEvent(name: "hue", value:  Math.round(hue / 3.6), isStateChange: true)
        setGenericName(hue)
        effectOff()
    }

    if (json.mode >= 0) {
        // effect changed
        def effectIndex = json.mode.toInteger()
        def effectName = lightEffects[effectIndex]
        def descr = "Effect was set to ${effectName} (${effectIndex})"
        logDebug "${descr}"
        sendEvent(name:"effectNumber", value:effectIndex, descriptionText: descr)
        sendEvent(name:"effectName", value:effectName, descriptionText: descr)
    }
    
    switch (state.type) {
        case 'rgb_cct':
        case 'cct':
            if (json.color_temp >= 0) {
                //  153..370 White temperature measured in mireds. Lower values are cooler.
            }
            if (json.saturation >= 0) {
                sendEvent(name: "saturation", value: json.saturation, unit: "%", isStateChange: true)
            }  
            break
        default:
            def saturation = 100
    
            switch (json.command) {
                case 'set_white':
                    saturation = 0
                    def colorName = 'White'
                    logDebug "${device.getDisplayName()} color is ${colorName}"
                    sendEvent(name: "colorName", value: colorName)
                    effectOff()
                    break
            }
    
            if (saturation != device.currentValue("saturation")) {
                sendEvent(name: "saturation", value: saturation, unit: "%", isStateChange: true)
            }
            break
    }
}

private def effectOff(){
    if (device.currentValue("effectNumber") < 0) 
        return

    def descr = "Effect was turned off"
    logDebug "${descr}"
    sendEvent(name:"effectNumber", value:-1, descriptionText: descr)
    sendEvent(name:"effectName", value:"", descriptionText: descr)
} 

def on() {
    logInfo "On"
    publishCommand([ "status": "on" ])
}

def off() {
    logInfo "Off"
    publishCommand([ "status": "off" ])
}

def setColor(value) {
    logDebug "Set Color $value"
    publishCommand([ "hue": Math.round(value.hue * 3.6), "saturation": value.saturation, "level": value.level ])
}

def setHue(value) {
    logDebug "Set Hue $value"
    publishCommand([ "hue": Math.round(value * 3.6) ])
}

def setSaturation(value) {
    logDebug "Set Saturation $value"
    publishCommand([ "saturation": value ])
}

def setLevel(value) {
    logInfo "Set Level $value"
    publishCommand([ "level": value ])
}

def setEffect(String effect){
    logInfo "Set Effect $effect"
    def index = lightEffects.indexOf(effect)
    if (index >= 0) setEffect(index)
}

def setEffect(id){
    logInfo "Set Effect $id"
    publishCommand([ "mode": id ])
} 

def setNextEffect(){
    def currentEffectId = device.currentValue("effectNumber") ?: 0
    currentEffectId++
    if (currentEffectId >= lightEffects.size()) 
        currentEffectId = 0
    setEffect(currentEffectId)
}

def setPreviousEffect(){
    def currentEffectId = device.currentValue("effectNumber") ?: 0
    currentEffectId--
    if (currentEffectId < 0) 
        currentEffectId = lightEffects.size() - 1
    setEffect(currentEffectId)
}

def setGenericName(int hue){
    def colorName
    switch (hue){
        case 0..15: colorName = "Red"
            break
        case 16..45: colorName = "Orange"
            break
        case 46..75: colorName = "Yellow"
            break
        case 76..105: colorName = "Chartreuse"
            break
        case 106..135: colorName = "Green"
            break
        case 136..165: colorName = "Spring"
            break
        case 166..195: colorName = "Cyan"
            break
        case 196..225: colorName = "Azure"
            break
        case 226..255: colorName = "Blue"
            break
        case 256..285: colorName = "Violet"
            break
        case 286..315: colorName = "Magenta"
            break
        case 316..345: colorName = "Rose"
            break
        case 346..360: colorName = "Red"
            break
    }
    logDebug "${device.getDisplayName()} color is ${colorName}"
    sendEvent(name: "colorName", value: colorName)
}

def publishCommand(command) {
    logDebug "Publish ${settings.mqttTopic} ${command}"
    def json = new groovy.json.JsonBuilder(command).toString()
    interfaces.mqtt.publish(settings.mqttTopic, json)
}

def updated() {
    log.info "Updated..."
    initialize()
    
    if (settings.mqttTopic.contains("rgb_cct")) {
        state.type = "rgb_cct"
    } else if (settings.mqttTopic.contains("rgbw")) {
        state.type = "rgbw"
    } else if (settings.mqttTopic.contains("rgb")) {
        state.type = "rgb"
    } else if (settings.mqttTopic.contains("cct")) {
        state.type = "cct"
    }
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
        interfaces.mqtt.unsubscribe(settings.mqttUpdatesTopic)
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
        interfaces.mqtt.connect("tcp://" + settings.mqttBroker, "hubitat_milight_${device.id}", null, null)
        // subscribe once received connection succeeded status update below        
    } catch(e) {
        log.error "MQTT Initialize error: ${e.message}."
        delayedInitialise()
    }
}

def subscribe() {
    interfaces.mqtt.subscribe(settings.mqttUpdatesTopic)
    logDebug "Subscribed to topic ${settings.mqttUpdatesTopic}"
    
    // sendEvent(name: "presence", value: "present", descriptionText: "MQTT connected", isStateChange: true)

    runIn(3, beginCheck)
}

def beginCheck() {
    // check connection
    // 1. turn light on
    // 2. turn light off
    // 3. if not received any MQTT messages reconnect
    state.check = 1
    on()
    runIn(3, checkConnection)  
}

def checkConnection() {
    if (state.check == 1) {
        // check started but not completed. reconnect
        log.warn 'Light not reacting'
        state.remove('check')
        delayedInitialise()
    } else if (state.check > 1) {
        state.remove('check')
        off()    
        log.info 'Light check completed'
    }
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
                    //sendEvent(name: "presence", value: "not present", descriptionText: "MQTT disconnected")
                    state.delay = 0
                    delayedInitialise()
                    break
                case 'send error':
                    state.connected = false
                    //sendEvent(name: "presence", value: "not present", descriptionText: "MQTT disconnected")
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