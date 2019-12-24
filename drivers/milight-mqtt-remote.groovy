/*
 * ESP8266 MiLight Hub MQTT Remote Control
 *  Device Driver for Hubitat Elevation hub
 *  Version 1.0.0
 *
 * Creates child RGB devices that react to MiLight remote events sent from ESP8266 MiLight Hub (https://github.com/sidoh/esp8266_milight_hub) via MQTT broker.
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
    definition(name: "MiLight MQTT Remote", namespace: "muxa", author: "Mikhail Diatchenko") {
        capability "Initialize"
    }
}

preferences {
    section("URIs") {
        input name: "mqttBroker", type: "text", title: "MQTT broker IP:Port", required: true
        input "mqttUpdatesTopic", "string", title: "MQTT Updates Topic (e.g. milight/updates/rgbw/0x1234)", required: true
        
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def installed() {
    log.info "Installed..."
}

def parse(String description) {
    //log.debug description
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
        sendChildEvent(groupIndex, [name: "level", value: level, unit: "%", isStateChange: true, descriptionText: "Brightness set to ${level}%"])
    }    
    
    if (json.hue >= 0) {
        def hue = Math.round(json.hue.toInteger() / 3.6)
        sendChildEvent(groupIndex, [name: "hue", value: hue, isStateChange: true, descriptionText: "Hue set to ${hue}"])
    }
    
    switch (state.type) {
        case 'rgb_cct':
        case 'cct':
            if (json.color_temp >= 0) {
                //  153..370 White temperature measured in mireds. Lower values are cooler.
            }
            if (json.saturation >= 0) {
                sendChildEvent(groupIndex, [name: "saturation", value: json.saturation, unit: "%", isStateChange: true, descriptionText: "Saturation set to ${json.saturation}%"])
            }  
            break
        default:
            //def saturation = 100
    
            switch (json.command) {
                case 'set_white':
                    saturation = 0
                    def colorName = 'White'
                    logDebug "${device.getDisplayName()} color is ${colorName}"
                    sendChildEvent(groupIndex, [name: "colorName", value: colorName, descriptionText: "Color set to ${colorName}%"])
                    break
            }
    
            //if (saturation != device.currentValue("saturation")) {
            //    sendChildEvent(groupIndex, [name: "saturation", value: saturation, unit: "%", isStateChange: true, descriptionText: "Saturation set to ${saturation}%"])
            //}
            break
    }
}

def sendChildEvent(groupIndex, event) {
    logDebug "Send child event ${groupIndex}: ${event}"
    def cd = getChildDevice(getChildId(groupIndex))
    cd.parse([event])
}

def componentRefresh(childDevice) {
    logDebug "componentRefresh ${childDevice.deviceNetworkId} ${childDevice}"
}

def componentOn(childDevice) {
    logDebug "componentOn ${childDevice.deviceNetworkId}"
}

def componentOff(childDevice) {
    logDebug "componentOff ${childDevice.deviceNetworkId}"
}

def componentSetColor(childDevice, value) {
    logDebug "componentSetColor ${childDevice.deviceNetworkId} ${value}"
}

def componentSetHue(childDevice, value) {
    logDebug "componentSetHue ${childDevice.deviceNetworkId} ${value}"
}

def componentSetLevel(childDevice, value) {
    logDebug "componentSetLevel ${childDevice.deviceNetworkId} ${value}"
}

def componentSetSaturation(childDevice, value) {
    logDebug "componentSetSaturation ${childDevice.deviceNetworkId} ${value}"
}

def componentStartLevelChange(childDevice, value) {
    logDebug "componentStartLevelChange ${childDevice.deviceNetworkId} ${value}"
}

def componentStopLevelChange(childDevice) {
    logDebug "componentStopLevelChange ${childDevice.deviceNetworkId}"
}

def getChildId(groupIndex) {
    return "${device.deviceNetworkId}-B${groupIndex}"
}

def createChildDevices(int buttons) {
    logInfo "Create child devices for ${buttons} buttons"
    
    def children = getChildDevices()    
    
    for (i in 0..buttons) {
        def childId = getChildId(i)
        def existingChild = children?.find { it.deviceNetworkId == childId }
        //if (existingChild != null) {
        //    deleteChildDevice(childId)
        //    existingChild = null
        //}
        if (existingChild == null) {
            addChildDevice("hubitat", "Generic Component RGB", childId, [isComponent: true, name: "MiLight Remote Group $i", label: "MiLight Remote Group $i"])
        }
    }
}

def updated() {
    log.info "Updated..."
    initialize()
    
    if (settings.mqttUpdatesTopic.contains("rgb_cct")) {
        state.type = "rgb_cct"
        createChildDevices(4)
    } else if (settings.mqttUpdatesTopic.contains("rgbw")) {
        state.type = "rgbw"
        createChildDevices(4)
    } else if (settings.mqttUpdatesTopic.contains("rgb")) {
        state.type = "rgb"
        createChildDevices(0)
    } else if (settings.mqttUpdatesTopic.contains("cct")) {
        state.type = "cct"
        createChildDevices(4)
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
        interfaces.mqtt.unsubscribe(settings.mqttUpdatesTopic+'/#')
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