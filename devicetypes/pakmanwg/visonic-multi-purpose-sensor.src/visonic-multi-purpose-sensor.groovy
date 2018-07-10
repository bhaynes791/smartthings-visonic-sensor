/**
 *
 *  Visonic Multi Purpose Sensor
 *
 *  Copyright 2017 pakmanw@sbcglobal.net
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
 *  Author: pakmanw@sbcglobal.net
 *
 *  Change Log
 *  2018-01-05 - v01.01 Created 
 *  2018-02-10 - v01.02 fix actiontile smoke detection issue
 *  2018-02-17 - v01.03 support smoke detector closed as clear
 *  2018-02-17 - v01.04 fix smart monitor issues with smoke detector closed as clear
 *  2018-02-20 - v01.05 add switch function type
 *  2018-04-13 - v01.06 add lock function type
 *  2018-06-20 - v01.07 default to contact sensor if no function was selected
 *  2018-07-09 - v01.08 add carbon monoxide detector
 *
 */

import physicalgraph.zigbee.clusters.iaszone.ZoneStatus

metadata {
    definition (name: "Visonic Multi Purpose Sensor", namespace: "pakmanwg", author: "pakmanw@sbcglobal.net") {
        capability "Battery"
        capability "Configuration"
        capability "Contact Sensor"
        capability "Water Sensor"
        capability "Smoke Detector"
        capability "Carbon Monoxide Detector"
        capability "Switch"
        capability "Refresh"
        capability "Temperature Measurement"
        capability "Health Check"
        capability "Sensor"
        capability "Lock"

        command "enrollResponse"

        fingerprint inClusters: "0000,0001,0003,0402,0500,0020,0B05", outClusters: "0019", manufacturer: "Visonic", model: "MCT-340 SMA"
        fingerprint inClusters: "0000,0001,0003,0402,0500,0020,0B05", outClusters: "0019", manufacturer: "Visonic", model: "MCT-340 E"
    }

    simulator {

    }

    preferences {
        input title: "Temperature Offset", description: "This feature allows you to correct any temperature variations by selecting an offset. Ex: If your sensor consistently reports a temp that's 5 degrees too warm, you'd enter \"-5\". If 3 degrees too cold, enter \"+3\".", displayDuringSetup: false, type: "paragraph", element: "paragraph"
        input "tempOffset", "number", title: "Degrees", description: "Adjust temperature by this many degrees", range: "*..*", displayDuringSetup: false
        input "function", "enum", title: "Sensor Function", options : ["Contact Sensor", "Water Sensor", "Smoke Detector", "Smoke Detector Closed as Clear", "Switch", "Lock", "Carbon Monoxide Detector", "Carbon Monoxide Detector Closed as Clear"], defaultValue: "Contact Sensor", required: false, displayDuringSetup: true
    }

    tiles(scale: 2) {

        multiAttributeTile(name: "status", type: "generic", width: 6, height: 4) {
            tileAttribute("device.status", key: "PRIMARY_CONTROL") {
                attributeState "open", label: '${name}', icon: "st.contact.contact.open", backgroundColor: "#e86d13"
                attributeState "closed", label: '${name}', icon: "st.contact.contact.closed", backgroundColor: "#00A0DC"
                attributeState "dry", label: '${name}', icon: "st.alarm.water.dry", backgroundColor: "#00A0DC"
                attributeState "wet", label: '${name}', icon: "st.alarm.water.wet", backgroundColor: "#e86d13"
                attributeState "clear", label: '${name}', icon: "st.alarm.smoke.clear", backgroundColor: "#00A0DC"
                attributeState "detected", label: '${name}', icon: "st.alarm.smoke.smoke", backgroundColor: "#e86d13"
                attributeState "on", label: '${name}', icon: "st.switches.switch.on", backgroundColor: "#00A0DC"
                attributeState "off", label: '${name}', icon: "st.switches.switch.off", backgroundColor: "#ffffff"
                attributeState "locked", label: '${name}', icon: "st.locks.lock.locked", backgroundColor: "#00A0DC"
                attributeState "unlocked", label: '${name}', icon: "st.locks.lock.unlocked", backgroundColor: "#ffffff"
            }
        }

        valueTile("temperature", "device.temperature", inactiveLabel: false, width: 2, height: 2) {
            state "temperature", label: '${currentValue}°',
                backgroundColors: [
                    [value: 31, color: "#153591"],
                    [value: 44, color: "#1e9cbb"],
                    [value: 59, color: "#90d2a7"],
                    [value: 74, color: "#44b621"],
                    [value: 84, color: "#f1d801"],
                    [value: 95, color: "#d04e00"],
                    [value: 96, color: "#bc2323"]
                ]
        }

        valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
            state "battery", label: '${currentValue}% battery', unit: ""
        }

        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", action: "refresh.refresh", icon: "st.secondary.refresh"
        }

        standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
        }

        main(["status", "temperature"])
        details(["status", "temperature", "battery", "refresh", "configure"])
    }
}

def parse(String description) {
    log.debug "description: $description"

    Map map = [:]
    if (description?.startsWith('catchall:')) {
        map = parseCatchAllMessage(description)
    }
    else if (description?.startsWith('read attr -')) {
        map = parseReportAttributeMessage(description)
    }
    else if (description?.startsWith('temperature: ')) {
        map = parseCustomMessage(description)
    }
    else if (description?.startsWith('zone status')) {
        map = parseIasMessage(description)
    }

    log.debug "Parse returned $map"
    def result = map ? createEvent(map) : null

    if (description?.startsWith('enroll request')) {
        List cmds = enrollResponse()
        log.debug "enroll response: ${cmds}"
        result = cmds?.collect { new physicalgraph.device.HubAction(it) }
    }
    return result
}

private Map parseCatchAllMessage(String description) {
    Map resultMap = [:]
    def cluster = zigbee.parse(description)
    if (shouldProcessMessage(cluster)) {
        switch(cluster.clusterId) {
            case 0x0001:
                resultMap = getBatteryResult(cluster.data.last())
                break

            case 0x0402:
                // log.debug 'TEMP'
                // temp is last 2 data values. reverse to swap endian
                String temp = cluster.data[-2..-1].reverse().collect { cluster.hex1(it) }.join()
                def value = getTemperature(temp)
                resultMap = getTemperatureResult(value)
                break
        }
    }

    return resultMap
}

private boolean shouldProcessMessage(cluster) {
    // 0x0B is default response indicating message got through
    // 0x07 is bind message
    boolean ignoredMessage = cluster.profileId != 0x0104 ||
        cluster.command == 0x0B ||
        cluster.command == 0x07 ||
        (cluster.data.size() > 0 && cluster.data.first() == 0x3e)
    return !ignoredMessage
}

private Map parseReportAttributeMessage(String description) {
    Map descMap = (description - "read attr - ").split(",").inject([:]) { map, param ->
        def nameAndValue = param.split(":")
        map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
    }
    log.debug "Desc Map: $descMap"

    Map resultMap = [:]
    if (descMap.cluster == "0402" && descMap.attrId == "0000") {
        def value = getTemperature(descMap.value)
        resultMap = getTemperatureResult(value)
    }
    else if (descMap.cluster == "0001" && descMap.attrId == "0020") {
        resultMap = getBatteryResult(Integer.parseInt(descMap.value, 16))
    }

    return resultMap
}

private Map parseCustomMessage(String description) {
    Map resultMap = [:]
    if (description?.startsWith('temperature: ')) {
        def value = zigbee.parseHATemperatureValue(description, "temperature: ", getTemperatureScale())
        resultMap = getTemperatureResult(value)
    }
    return resultMap
}

private Map getStatus() {
    if (function) {
        switch (function) {
            case "Contact Sensor":
                state.status = state.alarmSet ? "open" : "closed"
                sendEvent(name: 'status', value: state.status, descriptionText: descriptionText, translatable: true)
                return getContactResult(state.status)
            case "Water Sensor":
                state.status = state.alarmSet ? "dry" : "wet"
                sendEvent(name: 'status', value: state.status, descriptionText: descriptionText, translatable: true)
                return getWaterResult(state.status)
            case "Smoke Detector":
                state.status = state.alarmSet ? "clear" : "detected"
                sendEvent(name: 'status', value: state.status, descriptionText: descriptionText, translatable: true)
                return getSmokeResult(state.status);
            case "Smoke Detector Closed as Clear":
                state.status = state.alarmSet ? "detected" : "clear"
                sendEvent(name: 'status', value: state.status, descriptionText: descriptionText, translatable: true)
                return getSmokeResult(state.status);
            case "Switch":
                state.status = state.alarmSet ? "off" : "on"
                sendEvent(name: 'status', value: state.status, descriptionText: descriptionText, translatable: true)
                return getSwitchResult(state.status);
            case "Lock":
                state.status = state.alarmSet ? "unlocked" : "locked"
                sendEvent(name: 'status', value: state.status, descriptionText: descriptionText, translatable: true)
                return getLockResult(state.status);
            case "Carbon Monoxide Detector":
                state.status = state.alarmSet ? "clear" : "detected"
                sendEvent(name: 'status', value: state.status, descriptionText: descriptionText, translatable: true)
                return getCarbonMonoxideResult(state.status);
            case "Carbon Monoxide Detector Closed as Clear":
                state.status = state.alarmSet ? "detected" : "clear"
                sendEvent(name: 'status', value: state.status, descriptionText: descriptionText, translatable: true)
                return getCarbonMonoxideResult(state.status);
        }
    } else {
         state.status = state.alarmSet ? "open" : "closed"
         sendEvent(name: 'status', value: state.status, descriptionText: descriptionText, translatable: true)
         return getContactResult(state.status)
    }
}

private Map parseIasMessage(String description) {
    ZoneStatus zs = zigbee.parseZoneStatus(description)

    state.alarmSet = zs.isAlarm1Set()
    return getStatus()
}

def getTemperature(value) {
    def celsius = Integer.parseInt(value, 16).shortValue() / 100
    if (getTemperatureScale() == "C") {
        return celsius
    } else {
        return celsiusToFahrenheit(celsius) as Integer
    }
}

private Map getBatteryResult(rawValue) {
    log.debug 'Battery'
    def linkText = getLinkText(device)

    def result = [:]

    if (!(rawValue == 0 || rawValue == 255)) {
        def volts = rawValue / 10
        def minVolts = 2.1
        def maxVolts = 3.0
        def pct = (volts - minVolts) / (maxVolts - minVolts)
        def roundedPct = Math.round(pct * 100)
        result.value = Math.min(100, roundedPct)
        result.descriptionText = "${linkText} battery was ${result.value}%"
        result.name = 'battery'
    }

    return result
}

private Map getTemperatureResult(value) {
    log.debug 'TEMP'
    def linkText = getLinkText(device)
    if (tempOffset) {
        def offset = tempOffset as int
        def v = value as int
        value = v + offset
    }
    def descriptionText = "${linkText} was ${value}°${temperatureScale}"
    return [
        name: 'temperature',
        value: value,
        descriptionText: descriptionText,
        unit: temperatureScale
    ]
}

private Map getContactResult(value) {
    log.debug 'Contact Status'
    def linkText = getLinkText(device)
    def descriptionText = "${linkText} was ${value == 'open' ? 'opened' : 'closed'}"
    return [
        name: 'contact',
        value: value,
        descriptionText: descriptionText
    ]
}

private Map getWaterResult(value) {
    log.debug 'Water Status'
    def linkText = getLinkText(device)
    def descriptionText = "${linkText} was ${value == 'dry' ? 'dry' : 'wet'}"
    return [
        name: 'water',
        value: value,
        descriptionText: descriptionText
    ]
}

private Map getSmokeResult(value) {
    log.debug 'Smoke Status'
    def linkText = getLinkText(device)
    def descriptionText = "${linkText} was ${value == 'clear' ? 'clear' : 'detected'}"
    return [
        name: 'smoke',
        value: value,
        descriptionText: descriptionText
    ]
}

private Map getCarbonMonoxideResult(value) {
    log.debug 'Carbon Monoxide Status'
    def linkText = getLinkText(device)
    def descriptionText = "${linkText} was ${value == 'clear' ? 'clear' : 'detected'}"
    return [
        name: 'carbonMonoxide',
        value: value,
        descriptionText: descriptionText
    ]
}

private Map getSwitchResult(value) {
    log.debug 'Switch Status'
    def linkText = getLinkText(device)
    def descriptionText = "${linkText} was ${value == 'on' ? 'on' : 'off'}"
    return [
        name: 'switch',
        value: value,
        descriptionText: descriptionText
    ]
}

/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
    return zigbee.readAttribute(0x0402, 0x0000) // Read the Temperature Cluster
}

def refresh()
{
    log.debug "Refreshing Temperature and Battery"
    def refreshCmds = [
        "st rattr 0x${device.deviceNetworkId} 1 0x402 0", "delay 200",
        "st rattr 0x${device.deviceNetworkId} 1 1 0x20"
    ]
    
    // sendEvent(name: "status", value: "dry")

    return refreshCmds + enrollResponse()
}

def configure() {
    // Device-Watch allows 2 check-in misses from device
    sendEvent(name: "checkInterval", value: 60 * 12, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])

    String zigbeeEui = swapEndianHex(device.hub.zigbeeEui)
    log.debug "Configuring Reporting, IAS CIE, and Bindings."
    def enrollCmds = [
        "delay 1000",
        "zcl global write 0x500 0x10 0xf0 {${zigbeeEui}}", "delay 200",
        "send 0x${device.deviceNetworkId} 1 1", "delay 1500",
        //"raw 0x500 {01 23 00 00 00}", "delay 200",
        //"send 0x${device.deviceNetworkId} 1 1", "delay 1500",
    ]

    return enrollCmds + zigbee.batteryConfig() + zigbee.temperatureConfig(30, 300) + refresh() // send refresh cmds as part of config
}

def enrollResponse() {
    log.debug "Sending enroll response"
    [

    "raw 0x500 {01 23 00 00 00}", "delay 200",
    "send 0x${device.deviceNetworkId} 1 1"

    ]
}

private hex(value) {
    new BigInteger(Math.round(value).toString()).toString(16)
}

private String swapEndianHex(String hex) {
    reverseArray(hex.decodeHex()).encodeHex()
}

private byte[] reverseArray(byte[] array) {
    int i = 0;
    int j = array.length - 1;
    byte tmp;
    while (j > i) {
        tmp = array[j];
        array[j] = array[i];
        array[i] = tmp;
        j--;
        i++;
    }
    return array
}

def updated() {
    if (!state.prev_function) {
        state.prev_function = "Contact Sensor"
    }
    if (state.prev_function != function) {
        log.debug "updated called change from ${state.prev_function} to ${function}"
        if (function == "Water Sensor") {
            log.debug "update to water sensor"
            def descriptionText = "Updating device to water sensor"
            if (device.latestValue("status") == "open" || device.latestValue("status") == "off" ||
                device.latestValue("status") == "unlocked" ||
                (device.latestValue("status") == "clear" && state.prev_function == "Smoke Detector") || 
                (device.latestValue("status") == "detected" && state.prev_function == "Smoke Detector Closed as Clear") ||
                (device.latestValue("status") == "clear" && state.prev_function == "Carbon Monoxide Detector") ||
                (device.latestValue("status") == "detected" && state.prev_function == "Carbon Monoxide Detector Closed as Clear")) {
                sendEvent(name: 'status', value: 'dry', descriptionText: descriptionText, translatable: true)
            } else {
                sendEvent(name: 'status', value: 'wet', descriptionText: descriptionText, translatable: true)
            }
        } else if (function == "Smoke Detector") {
            log.debug "update to smoke detector"
            def descriptionText = "Updating device to smoke detector"
            if (device.latestValue("status") == "open" || device.latestValue("status") == "dry" || 
                device.latestValue("status") == "off" || device.latestValue("status") == "unlocked" ||
                (device.latestValue("status") == "detected" && state.prev_function == "Smoke Detector Closed as Clear") ||
                (device.latestValue("status") == "clear" && state.prev_function == "Carbon Monoxide Detector") ||
                (device.latestValue("status") == "cetected" && state.prev_function == "Carbon Monoxide Detector Closed as Clear")) {
                sendEvent(name: 'status', value: 'clear', descriptionText: descriptionText, translatable: true)
            } else {
                sendEvent(name: 'status', value: 'detected', descriptionText: descriptionText, translatable: true)
            }
        } else if (function == "Smoke Detector Closed as Clear") {
            log.debug "update to smoke detector closed as clear"
            def descriptionText = "Updating device to smoke detector closed as clear"
            if (device.latestValue("status") == "open" || device.latestValue("status") == "dry" || 
                device.latestValue("status") == "off" || device.latestValue("status") == "unlocked" ||
                (device.latestValue("status") == "clear" && state.prev_function == "Smoke Detector") ||
                (device.latestValue("status") == "clear" && state.prev_function == "Carbon Monoxide Detector") ||
                (device.latestValue("status") == "detected" && state.prev_function == "Carbon Monoxide Detector Closed as Clear")) {
                sendEvent(name: 'status', value: 'detected', descriptionText: descriptionText, translatable: true)
            } else {
                sendEvent(name: 'status', value: 'clear', descriptionText: descriptionText, translatable: true)
            }
        } else if (function == "Switch") {
            log.debug "update to switch"
            def descriptionText = "Updating device to switch"
            if (device.latestValue("status") == "open" || device.latestValue("status") == "dry" ||
                device.latestValue("status") == "unlocked" ||
                (device.latestValue("status") == "detected" && state.prev_function == "Smoke Detector Closed as Clear") ||
                (device.latestValue("status") == "clear" && state.prev_function == "Smoke Detector") ||
                (device.latestValue("status") == "clear" && state.prev_function == "Carbon Monoxide Detector") ||
                (device.latestValue("status") == "detected" && state.prev_function == "Carbon Monoxide Detector Closed as Clear")) {
                sendEvent(name: 'status', value: 'off', descriptionText: descriptionText, translatable: true)
            } else {
                sendEvent(name: 'status', value: 'on', descriptionText: descriptionText, translatable: true)
            }
        } else if (function == "Lock") {
            log.debug "update to lock"
            def descriptionText = "Updating device to lock"
            if (device.latestValue("status") == "open" || device.latestValue("status") == "dry" ||
                (device.latestValue("status") == "detected" && state.prev_function == "Smoke Detector Closed as Clear") ||
                (device.latestValue("status") == "clear" && state.prev_function == "Smoke Detector") ||
                (device.latestValue("status") == "clear" && state.prev_function == "Carbon Monoxide Detector") ||
                (device.latestValue("status") == "detected" && state.prev_function == "Carbon Monoxide Detector Closed as Clear")) {
                sendEvent(name: 'status', value: 'unlocked', descriptionText: descriptionText, translatable: true)
            } else {
                sendEvent(name: 'status', value: 'locked', descriptionText: descriptionText, translatable: true)
            }
        } else if (function == "Carbon Monoxide Detector") {
            log.debug "update to carbon monoxide detector"
            def descriptionText = "Updating device to carbon monoxide detector"
            if (device.latestValue("status") == "open" || device.latestValue("status") == "dry" || 
                device.latestValue("status") == "off" || device.latestValue("status") == "unlocked" ||
                (device.latestValue("status") == "detected" && state.prev_function == "Smoke Detector Closed as Clear") ||
                (device.latestValue("status") == "clear" && state.prev_function == "Smoke Detector") ||
                (device.latestValue("status") == "detected" && state.prev_function == "Carbon Monoxide Detector Closed as Clear")) {
                sendEvent(name: 'status', value: 'clear', descriptionText: descriptionText, translatable: true)
            } else {
                sendEvent(name: 'status', value: 'detected', descriptionText: descriptionText, translatable: true)
            }
        } else if (function == "Carbon Monoxide Detector Closed as Clear") {
            log.debug "update to carbon monoxide detector closed as clear"
            def descriptionText = "Updating device to carbon monoxide detector closed as clear"
            if (device.latestValue("status") == "open" || device.latestValue("status") == "dry" || 
                device.latestValue("status") == "off" || device.latestValue("status") == "unlocked" ||
                (device.latestValue("status") == "detected" && state.prev_function == "Smoke Detector Closed as Clear") ||
                (device.latestValue("status") == "clear" && state.prev_function == "Smoke Detector") ||
                (device.latestValue("status") == "clear" && state.prev_function == "Carbon Monoxide Detector")) {
                sendEvent(name: 'status', value: 'detected', descriptionText: descriptionText, translatable: true)
            } else {
                sendEvent(name: 'status', value: 'clear', descriptionText: descriptionText, translatable: true)
            }   
        } else {
            log.debug "update to contact sensor"
            def descriptionText = "Updating device to contact sensor"
            if (device.latestValue("status") == "dry" || device.latestValue("status") == "off" ||
                device.latestValue("status") == "unlocked" ||
                (device.latestValue("status") == "clear" && state.prev_function == "Smoke Detector") ||
                (device.latestValue("status") == "detected" && state.prev_function == "Smoke Detector Closed as Clear")) {
                sendEvent(name: 'status', value: 'open', descriptionText: descriptionText, translatable: true)
            } else {
                sendEvent(name: 'status', value: 'closed', descriptionText: descriptionText, translatable: true)
            }
        }
        state.prev_function = function
    }
}

def installed() {
    reset()
    configure()
    refresh()
}

def reset() {
    if (function) {
        function = "Contact Sensor"
    }
    state.prev_function = "Contact Sensor"
}
