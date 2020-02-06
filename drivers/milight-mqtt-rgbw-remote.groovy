/*
 * ESP8266 MiLight Hub MQTT RGBW Remote Control
 *  Device Driver for Hubitat Elevation hub
 *  Version 1.0.0
 *
 * Creates child RGB devices that react to MiLight 4-zone RGBW remote control events sent from ESP8266 MiLight Hub (https://github.com/sidoh/esp8266_milight_hub) via MQTT broker.
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
    definition(name: "MiLight MQTT RGBW Remote", namespace: "muxa", author: "Mikhail Diatchenko") {
        capability "Initialize"
        capability "Refresh"
        capability "Actuator"
        capability "Switch"
        capability "SwitchLevel"
        capability "Light"
        capability "ColorControl"
    }
}

preferences {
    section("URIs") {
        input name: "mqttBroker", type: "text", title: "MQTT broker IP:Port", required: true
        input name: "mqttTopic", type: "string", title: "MQTT Control Topic (e.g. milight/rgbw/0x1234)", required: true
        input name: "mqttUpdatesTopic", type: "string", title: "MQTT Updates Topic (e.g. milight/updates/rgbw/0x1234)", required: true
        
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def installed() {
    logDebug "Installed"
}

def parse(String description) {
    // log.debug description
    def mqtt = interfaces.mqtt.parseMessage(description)
	logDebug mqtt
    def parts = mqtt.topic.split('/')
    //logDebug parts
    
    def groupIndex = parts.last().toInteger()
    
	json = new groovy.json.JsonSlurper().parseText(mqtt.payload)
	//logDebug json

    if (json.state == "OFF") {
        sendChildEvent(groupIndex, [name: "switch", value: "off", descriptionText: "Turned off"])
    } else if (json.state == "ON") {
        sendChildEvent(groupIndex, [name: "switch", value: "on", descriptionText: "Turned on"])
    }
    
    if (json.brightness >= 0) {
        def level = Math.round(json.brightness.toInteger() / 2.55)
        sendChildEvent(groupIndex, [name: "level", value: level, unit: "%", descriptionText: "Brightness set to ${level}%"])
    }    
    
    if (json.hue >= 0) {
        def hue = json.hue.toInteger()        
        sendChildEvent(groupIndex, [name: "hue", value:  Math.round(hue / 3.6), descriptionText: "Hue set to ${hue}"])
        sendChildEvent(groupIndex, [name: "saturation", value: 100, unit: "%", descriptionText: "Saturation set to 100%"])
        setGenericName(hue, groupIndex)
    }
    
    switch (json.command) {
        case 'set_white':
            def colorName = 'White'
            logDebug "${device.getDisplayName()} color is ${colorName}"
            sendChildEvent(groupIndex, [name: "colorName", value: colorName, descriptionText: "Color set to ${colorName}"])
            sendChildEvent(groupIndex, [name: "saturation", value: 0, unit: "%", descriptionText: "Saturation set to 0%"])
            break
    }
}

def sendChildEvent(groupIndex, event) {
    if (groupIndex > 0) {
        logDebug "Send child event ${groupIndex}: ${event}"
        def cd = getChildDevice(getChildId(groupIndex))
        cd.parse([event])
    } else {
        logDebug "Send parent event ${event}"
        sendEvent(event)
        // send event to all children
        def children = getChildDevices()    
        children.each { it.parse([event]) }
    }
}

def refresh() {
    if (device.currentValue('switch') == null) {
        sendChildEvent(0, [name: "switch", value: "off", descriptionText: "Switch initialised to off"])
    }
    
    if (device.currentValue('saturation') == null) {
        sendChildEvent(0, [name: "saturation", value: 0, unit: "%", descriptionText: "Saturation initialised to 0%"])
    }
    
    if (device.currentValue('level') == null) {
        sendChildEvent(0, [name: "level", value: 100, unit: "%", descriptionText: "Brightness initialised to 100%"])
    }
    
    if (device.currentValue('hue') == null) {
        sendChildEvent(0, [name: "hue", value: 0, descriptionText: "Hue initialised to 0"])
    }
}

def on() {
    logInfo "On"
    publishCommand(0, [ "status": "on" ])
}

def off() {
    logInfo "Off"
    publishCommand(0, [ "status": "off" ])
}

def setColor(value) {
    logInfo "Set Color $value"
    publishCommand(0, [ "hue": Math.round(value.hue * 3.6), "saturation": value.saturation, "level": value.level ])
}

def setHue(value) {
    logInfo "Set Hue $value"
    publishCommand(0, [ "hue": Math.round(value * 3.6) ])
}

def setSaturation(value) {
    logInfo "Set Saturation $value"
    publishCommand(0, [ "saturation": value ])
}

def setLevel(value) {
    logInfo "Set Level $value"
    publishCommand(0, [ "level": value ])
}

def setWhite() {
    logInfo "Set to White"
    publishCommand(0, [ "command": "set_white" ])
}

def setNight() {
    logInfo "Set to Night Mode"
    publishCommand(0, [ "command": "night_mode" ])
}

def setEffectSpeedUp() {
    logInfo "Increase effect speed"
    publishCommand(0, [ "command": "mode_speed_up" ])
}

def setEffectSpeedDown() {
    logInfo "Decrease effect speed"
    publishCommand(0, [ "command": "mode_speed_down" ])
}

// def setTransition(String field, int startValue, int durationSeconds, ) {
//     logInfo "Decrease effect speed"
//     publishCommand(0, [ "command": "mode_speed_down" ])
// }


def setGenericName(int hue, int groupIndex){
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
    sendChildEvent(groupIndex, [name: "colorName", value: colorName])
}

def publishCommand(groupIndex, command) {
    def topic = "${settings.mqttTopic}/${groupIndex}"
    logDebug "Publish ${topic} ${command}"
    def json = new groovy.json.JsonBuilder(command).toString()
    interfaces.mqtt.publish(topic, json)
}

def componentRefresh(childDevice) {
    logDebug "componentRefresh ${childDevice.deviceNetworkId} ${childDevice}"
}

def componentOn(childDevice) {
    logDebug "componentOn ${childDevice.deviceNetworkId}"
    publishCommand(getZoneId(childDevice.deviceNetworkId), [ "status": "on" ])
}

def componentOff(childDevice) {
    logDebug "componentOff ${childDevice.deviceNetworkId}"
    publishCommand(getZoneId(childDevice.deviceNetworkId), [ "status": "off" ])
}

def componentSetColor(childDevice, value) {
    logDebug "componentSetColor ${childDevice.deviceNetworkId} ${value}"
    publishCommand(getZoneId(childDevice.deviceNetworkId), [ "hue": Math.round(value.hue * 3.6), "saturation": value.saturation, "level": value.level ])
}

def componentSetHue(childDevice, value) {
    logDebug "componentSetHue ${childDevice.deviceNetworkId} ${value}"
    publishCommand(getZoneId(childDevice.deviceNetworkId), [ "hue": Math.round(value * 3.6) ])
}

def componentSetLevel(childDevice, value) {
    logDebug "componentSetLevel ${childDevice.deviceNetworkId} ${value}"
    publishCommand(getZoneId(childDevice.deviceNetworkId), [ "level": value ])
}

def componentSetSaturation(childDevice, value) {
    logDebug "componentSetSaturation ${childDevice.deviceNetworkId} ${value}"
    publishCommand(getZoneId(childDevice.deviceNetworkId), [ "saturation": value ])
}

def componentStartLevelChange(childDevice, value) {
    logDebug "componentStartLevelChange ${childDevice.deviceNetworkId} ${value}"
}

def componentStopLevelChange(childDevice) {
    logDebug "componentStopLevelChange ${childDevice.deviceNetworkId}"
}

def getZoneId(childDeviceId) {
    return childDeviceId.substring(childDeviceId.length() - 1)
}

def getChildId(groupIndex) {
    return "${device.deviceNetworkId}-B${groupIndex}"
}

def createChildDevices(int buttons) {
    logInfo "Create child devices for ${buttons} buttons"
    
    def children = getChildDevices()    

    for (i in 1..buttons) {
        def childId = getChildId(i)
        def existingChild = children?.find { it.deviceNetworkId == childId }
        if (existingChild == null) {
            logInfo "Create child device for Zone $i"
            addChildDevice("hubitat", "Generic Component RGB", childId, [isComponent: true, name: "MiLight RGBW Remote Zone $i", label: "MiLight RGBW Remote Zone $i"])
        }
    }
}

def updated() {
    logDebug "Updated"
    initialize()
}

def uninstalled() {
    logDebug "Uninstalled"
    disconnect()
}

def disconnect() {
    log.info "Disconnecting from MQTT"
    interfaces.mqtt.unsubscribe(settings.mqttUpdatesTopic+'/#')
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

    createChildDevices(4)

    state.delay = 0
    connect()
}

def connect() {
    try {
        // open connection
        log.info "Connecting to ${settings.mqttBroker}"
        interfaces.mqtt.connect("tcp://" + settings.mqttBroker, "hubitat_milight_${device.id}", null, null)
        // subscribe once received connection succeeded status update below        
    } catch(e) {
        log.error "MQTT Connect error: ${e.message}."
        delayedConnect()
    }
}

def subscribe() {
    interfaces.mqtt.subscribe(settings.mqttUpdatesTopic+'/#')
    logDebug "Subscribed to topic ${settings.mqttUpdatesTopic}/#"
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
                    state.connected = true
                    // without this delay the `parse` method is never called
                    // (it seems that there needs to be some delay after connection to subscribe)
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
    log.info msg
}

def logDebug(msg) {
    if (logEnable) log.debug msg
}