/*
 * Tasmota MQTT Switch
 *  Device Driver for Hubitat Elevation hub
 *  Version 1.0.0
 *
 * Control and monitor a Tasmotized swithc via MQTT broker.
 * Multi-gang switches are detected and for that child devices are created automatically (might require switch reboot).
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
    definition (name: "Tasmota MQTT Switch", namespace: "muxa", author: "Mikhail Diatchenko") {
        capability "Initialize"
        capability "Configuration"
        capability "Actuator"
        capability "Switch"
        capability "PresenceSensor"
    }
}

preferences {
    section("MQTT") {
        input "mqttBroker", "string", title: "MQTT Broker Address:Port", required: true
		input "mqttTopic", "string", title: "MQTT Topic", required: true, description: "E.g. tasmota_garage"
        input "mqttFullTopic", "string", title: "MQTT Full Topic Template", required: true, description: "E.g. %prefix%/%topic%/", defaultValue: "%prefix%/%topic%/"
    }
    section("Logging") {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

private def getTelemetryTopic() {
    return mqttFullTopic.replace("%prefix%", "tele").replace("%topic%", mqttTopic)
}

private def getStatsTopic() {
    return mqttFullTopic.replace("%prefix%", "stat").replace("%topic%", mqttTopic)
}

private def getCommandTopic() {
    return mqttFullTopic.replace("%prefix%", "cmnd").replace("%topic%", mqttTopic)
}

def installed() {
    log.info "installed..."
}

def parse(String description) {
    def mqtt = interfaces.mqtt.parseMessage(description) 
    
    if (mqtt.topic.startsWith(getStatsTopic())) {
        // stats topic
        def subTopic = mqtt.topic.substring(getStatsTopic().length())
        
        switch (subTopic) {
            case 'RESULT':
                // result is handled by individual POWER<i> topics below
                break
            default:
                if (subTopic.startsWith("POWER")) {
                    def childSwitch = getChildDevice(getChildDeviceId(subTopic))
                    if (childSwitch) {
                        childSwitch.parse([[name: 'switch', value: mqtt.payload.toLowerCase(), descriptionText: "Turned ${mqtt.payload}"]])
                        syncChildSwitches(childSwitch, mqtt.payload.toLowerCase())
                    } else {                        
                        sendEvent([name: 'switch', value: mqtt.payload.toLowerCase(), descriptionText: "Turned ${mqtt.payload}"])
                    }
                } else {
                    logDebug "Unhandled topic: ${mqtt.topic}"
                }
                break
        }
    } else if (mqtt.topic.startsWith(getTelemetryTopic())) {
        // telemetry topic
        def subTopic = mqtt.topic.substring(getStatsTopic().length())

        switch (subTopic) {
            case 'LWT':
                if (mqtt.payload == 'Online') {
                    log.info "Online"
                    sendEvent([name: 'presence', value: 'present', descriptionText: 'Device online'])
                } else {
                    log.info "Offline"
                    sendEvent([name: 'presence', value: 'not present', descriptionText: 'Device offline'])
                }
                break
            case 'STATE':
                def json = new groovy.json.JsonSlurper().parseText(mqtt.payload)
                logDebug "State: $json"
                state.tasmotaState = json
                createChildDevices()
                break
            case 'INFO2':
                def json = new groovy.json.JsonSlurper().parseText(mqtt.payload)
                logDebug "Info 2: $json"
                state.ip = json.IPAddress
                break
            default:
                //logDebug "Unhandled topic: ${mqtt.topic}"
                break
        }
    } else {
         logDebug "Unhandled message: ${mqtt}"
    }
}

private def syncChildSwitches(changedSwitch, changedValue) {
    if (changedValue == 'on') {
        // at least one switch is on. master switch should be on
        sendEvent([name: 'switch', value: changedValue, descriptionText: "Turned ${changeValue}"])
    } else {
        def children = getChildDevices()
        def someOn = children.any { changedSwitch.id != it.id && it.currentValue('switch') == 'on'}
        if (someOn) {
            sendEvent([name: 'switch', value: 'on', descriptionText: "Turned ${changeValue}"])
        } else {
            sendEvent([name: 'switch', value: 'off', descriptionText: "Turned ${changeValue}"])
        }
    }
}

private def getTaskmotaSwitchIds() {
    def result = [] 
    
    state.tasmotaState.each{ k, v -> 
        if (k.startsWith("POWER")) {
            result.add(k)
        }
    }
    
    return result
}

private def createChildDevices() {
    def switchIds = getTaskmotaSwitchIds()
    
    // only create child devices there's more that 1 switch (otherwise parent device will be used as switch)
    if (switchIds.size() > 1) {
        def children = getChildDevices()    
    
        getTaskmotaSwitchIds().each{ id -> 
            def childId = getChildDeviceId(id)
            def existingDevice = children?.find { it.deviceNetworkId == childId }
            if (existingDevice == null) {
                logInfo "Create child switch for ${id}"
                def switchNumber = id.substring(5)
                addChildDevice("hubitat", "Generic Component Switch", childId, [isComponent: false, name: "Tasmota Child Switch", label: "${device.label} ${switchNumber}"])
            }
        }
    }
}

private def getChildDeviceId(postfix) {
    return "${device.id}-${postfix}"
}

private def getTasmotaDeviceIdFromChildDeviceNetworkId(networkId) {
    return networkId.substring(device.id.length()+1)
}

def componentRefresh(childDevice) {
    //logDebug "componentRefresh ${childDevice.deviceNetworkId} ${childDevice}"
}

def componentOn(childDevice) {
    //logDebug "componentOn ${childDevice.deviceNetworkId}"
    publishCommand(getTasmotaDeviceIdFromChildDeviceNetworkId(childDevice.deviceNetworkId), "ON")
}

def componentOff(childDevice) {
    //logDebug "componentOff ${childDevice.deviceNetworkId}"
    publishCommand(getTasmotaDeviceIdFromChildDeviceNetworkId(childDevice.deviceNetworkId), "OFF")
}

def on() {
    logInfo "Turning On"
    getTaskmotaSwitchIds().each{ id -> 
        publishCommand(id, "ON")
    }
}

def off() {
    logInfo "Turning Off"
    getTaskmotaSwitchIds().each{ id -> 
        publishCommand(id, "OFF")
    }
}

private def publishCommand(command, value) {
    logDebug "Publish ${getCommandTopic()}${command}: ${value}"
    interfaces.mqtt.publish("${getCommandTopic()}${command}", value)
}

def updated() {
    log.info "Updated..."
    initialize()
}

def uninstalled() {
    disconnect()
}

private def disconnect() {
    if (state.connected) {
        log.info "Disconnecting from MQTT"
        interfaces.mqtt.unsubscribe(getTelemetryTopic())
        interfaces.mqtt.unsubscribe(getStatsTopic())
        interfaces.mqtt.disconnect()
    }
}

private def delayedConnect() {
    // increase delay by 5 seconds every time, to max of 10 minutes
    if (state.delay < 600)
        state.delay = (state.delay ?: 0) + 5

    logDebug "Reconnecting in ${state.delay}s"
    runIn(state.delay, connect)
}

def initialize() {
    logDebug "Initialize"

    state.connected = false
    state.delay = 0
    disconnect()
    connect()
}

def connect() {
    try {
        // open connection
        log.info "Connecting to ${settings.mqttBroker}"
        interfaces.mqtt.connect("tcp://" + settings.mqttBroker, "hubitat_tasmota_${device.id}", null, null)
        // subscribe once received connection succeeded status update below        
    } catch(e) {
        log.error "MQTT Connect error: ${e.message}."
        delayedConnect()
    }
}

def subscribe() {
    interfaces.mqtt.subscribe("${getTelemetryTopic()}#")
    logDebug "Subscribed to topic ${getTelemetryTopic()}#"

    interfaces.mqtt.subscribe("${getStatsTopic()}#")
    logDebug "Subscribed to topic ${getStatsTopic()}#"
    
    state.connected = true
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
                    state.connected = false
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
                    runInMillis(1000, subscribe)
                    break
            }
            break
        default:
            logDebug "MQTT ${status}"
            break
    }
}

private def logInfo(msg) {
    if (logEnable) log.info msg
}

private def logDebug(msg) {
    if (logEnable) log.debug msg
}