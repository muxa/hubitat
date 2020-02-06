/*
 * Child driver for "ESP8266 MiLight Hub MQTT RGBW Remote Control"
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
    definition (name: "MiLight RGBW Light Component", namespace: "muxa", author: "Mikhail Diatchenko") {
        capability "Actuator"
        capability "Switch"
        capability "SwitchLevel"
        capability "Light"
        capability "LightEffects"
        capability "ColorControl"
        
        command "setWhite"
        command "setNight"
        command "setEffectSpeedUp"
        command "setEffectSpeedDown"

        attribute "effectNumber", "number"
    }
}

preferences {
    section("URIs") {
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

void parse(List<Map> description) {
    logDebug description
    // description.each {        
    //     if (it.name in ["switch","level"]) {            
    //         sendEvent(it)
    //     }
    // }
}

// TODO: call this
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
    parent?.componentOn(this.device)
}

def off() {
    logInfo "Off"
    parent?.componentOff(this.device)
}

def setColor(value) {
    logInfo "Set Color $value"
    parent?.componentSetColor(this.device, value)
}

def setHue(value) {
    logInfo "Set Hue $value"
    parent?.componentSetHue(this.device, value)
}

def setSaturation(value) {
    logInfo "Set Saturation $value"
    parent?.componentSetSaturation(this.device, value)
}

def setLevel(value) {
    logInfo "Set Level $value"
    parent?.componentSetLevel(this.device, value)
}

def setWhite() {
    logInfo "Set to White"
    parent?.componentSetWhite(this.device)
}

def setNight() {
    logInfo "Set to Night Mode"
    parent?.componentSetWhite(this.device)
}

def setEffectSpeedUp() {
    logInfo "Increase effect speed"
    parent?.componentSetEffectSpeedUp(this.device)
}

def setEffectSpeedDown() {
    logInfo "Decrease effect speed"
    parent?.componentSetEffectSpeedDown(this.device)
}

def setEffect(id){
    logInfo "Set Effect $id"
    // TODO add `componentSetEffect` to parent
    parent?.componentSetEffect(this.device, id)
}

def setEffect(String effect){
    logInfo "Set Effect $effect"
    def index = lightEffects.indexOf(effect)
    if (index >= 0) setEffect(index)
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

def updated() {
    log.info "Updated..."
    initialize()
}

def uninstalled() {
}

def initialize() {
    logDebug "Initialize"
}

def logInfo(msg) {
    if (logEnable) log.info msg
}

def logDebug(msg) {
    if (logEnable) log.debug msg
}