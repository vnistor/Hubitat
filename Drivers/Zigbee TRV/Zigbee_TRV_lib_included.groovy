/**
 *  Zigbee TRV - Device Driver for Hubitat Elevation
 *
 *  https://community.hubitat.com/t/dynamic-capabilities-commands-and-attributes-for-drivers/98342
 *
 *     Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *     in compliance with the License. You may obtain a copy of the License at:
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *     on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *     for the specific language governing permissions and limitations under the License.
 *
 * This driver is inspired by @w35l3y work on Tuya device driver (Edge project).
 * For a big portions of code all credits go to Jonathan Bradshaw.
 *
 * ver. 3.0.0  2023-11-16 kkossev  - (dev. branch) Refactored version 2.x.x drivers and libraries
 *
 *                                   TODO:  
 */

static String version() { "3.0.0" }
static String timeStamp() {"2023/11/16 9:36 PM"}

@Field static final Boolean _DEBUG = false

import groovy.transform.Field
import hubitat.device.HubMultiAction
import hubitat.device.Protocol
import hubitat.helper.HexUtils
import hubitat.zigbee.zcl.DataType
import java.util.concurrent.ConcurrentHashMap
import groovy.json.JsonOutput


deviceType = "Thermostat"
@Field static final String DEVICE_TYPE = "Thermostat"





metadata {
    definition (
        name: 'Zigbee TRV',
        importUrl: 'https://raw.githubusercontent.com/kkossev/Hubitat/development/Drivers/Zigbee_TRV/Zigbee_TRV_lib_included.groovy',
        namespace: 'kkossev', author: 'Krassimir Kossev', singleThreaded: true 
    )
    {    
        
    capability "ThermostatHeatingSetpoint"
    //capability "ThermostatCoolingSetpoint"
    capability "ThermostatOperatingState"
    capability "ThermostatSetpoint"
    capability "ThermostatMode"
    //capability "Thermostat"
    
    /*
        capability "Actuator"
        capability "Refresh"
        capability "Sensor"
        capability "Temperature Measurement"
        capability "Thermostat"
        capability "ThermostatHeatingSetpoint"
        capability "ThermostatCoolingSetpoint"
        capability "ThermostatOperatingState"
        capability "ThermostatSetpoint"
        capability "ThermostatMode"    
    */

    // Aqara E1 thermostat attributes
    attribute "system_mode", 'enum', SystemModeOpts.options.values() as List<String>
    attribute "preset", 'enum', PresetOpts.options.values() as List<String>
    attribute "window_detection", 'enum', WindowDetectionOpts.options.values() as List<String>
    attribute "valve_detection", 'enum', ValveDetectionOpts.options.values() as List<String>
    attribute "valve_alarm", 'enum', ValveAlarmOpts.options.values() as List<String>
    attribute "child_lock", 'enum', ChildLockOpts.options.values() as List<String>
    attribute "away_preset_temperature", 'number'
    attribute "window_open", 'enum', WindowOpenOpts.options.values() as List<String>
    attribute "calibrated", 'enum', CalibratedOpts.options.values() as List<String>
    attribute "sensor", 'enum', SensorOpts.options.values() as List<String>
    attribute "battery", 'number'

    command "preset", [[name:"select preset option", type: "ENUM",   constraints: ["--- select ---"]+PresetOpts.options.values() as List<String>]]

    if (_DEBUG) { command "testT", [[name: "testT", type: "STRING", description: "testT", defaultValue : ""]]  }

    // TODO - add Sonoff TRVZB fingerprint

    //fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,FCC0,000A,0201", outClusters:"0003,FCC0,0201", model:"lumi.airrtc.agl001", manufacturer:"LUMI", deviceJoinName: "Aqara E1 Thermostat"     // model: 'SRTS-A01'
    // fingerprints are inputed from the deviceProfile maps

    // https://github.com/Koenkk/zigbee-herdsman-converters/blob/6339b6034de34f8a633e4f753dc6e506ac9b001c/src/devices/xiaomi.ts#L3197
    // https://github.com/Smanar/deconz-rest-plugin/blob/6efd103c1a43eb300a19bf3bf3745742239e9fee/devices/xiaomi/xiaomi_lumi.airrtc.agl001.json 
    // https://github.com/dresden-elektronik/deconz-rest-plugin/issues/6351
    }
    
    
    preferences {
        input name: 'temperaturePollingInterval', type: 'enum', title: '<b>Temperature polling interval</b>', options: TemperaturePollingIntervalOpts.options, defaultValue: TemperaturePollingIntervalOpts.defaultValue, required: true, description: '<i>Changes how often the hub will poll the TRV for faster temperature reading updates.</i>'
    }
    
}

@Field static final Map TemperaturePollingIntervalOpts = [
    defaultValue: 600,
    options     : [0: 'Disabled', 60: 'Every minute (not recommended)', 120: 'Every 2 minutes', 300: 'Every 5 minutes', 600: 'Every 10 minutes', 900: 'Every 15 minutes', 1800: 'Every 30 minutes', 3600: 'Every 1 hour']
]

@Field static final Map SystemModeOpts = [        //system_mode
    defaultValue: 1,
    options     : [0: 'off', 1: 'heat']
]
@Field static final Map PresetOpts = [            // preset
    defaultValue: 1,
    options     : [0: 'manual', 1: 'auto', 2: 'away']
]
@Field static final Map WindowDetectionOpts = [   // window_detection
    defaultValue: 1,
    options     : [0: 'off', 1: 'on']
]
@Field static final Map ValveDetectionOpts = [    // valve_detection
    defaultValue: 1,
    options     : [0: 'off', 1: 'on']
]
@Field static final Map ValveAlarmOpts = [    // valve_alarm
    defaultValue: 1,
    options     : [0: false, 1: true]
]
@Field static final Map ChildLockOpts = [    // child_lock
    defaultValue: 1,
    options     : [0: 'unlock', 1: 'lock']
]
@Field static final Map WindowOpenOpts = [    // window_open
    defaultValue: 1,
    options     : [0: false, 1: true]
]
@Field static final Map CalibratedOpts = [    // calibrated
    defaultValue: 1,
    options     : [0: false, 1: true]
]
@Field static final Map SensorOpts = [    // child_lock
    defaultValue: 1,
    options     : [0: 'internal', 1: 'external']
]

@Field static final Map deviceProfilesV2 = [
    // isAqaraTRV()
    "AQARA_E1_TRV"   : [
            description   : "Aqara E1`Thermostat model SRTS-A01",
            models        : ["LUMI"],
            device        : [type: "TRV", powerSource: "battery", isSleepy:false],
            capabilities  : ["ThermostatHeatingSetpoint": true, "ThermostatOperatingState": true, "ThermostatSetpoint":true, "ThermostatMode":true],

            preferences   : ["window_detection":"0xFCC0:0x0273", "valve_detection":"0xFCC0:0x0274",, "child_lock":"0xFCC0:0x0277", "away_preset_temperature":"0xFCC0:0x0279"],
            fingerprints  : [
                [profileId:"0104", endpointId:"01", inClusters:"0000,0001,0003,FCC0,000A,0201", outClusters:"0003,FCC0,0201", model:"lumi.airrtc.agl001", manufacturer:"LUMI", deviceJoinName: "Aqara E1 Thermostat"] 
            ],
            commands      : ["resetStats":"resetStats", 'refresh':'refresh', "initialize":"initialize", "updateAllPreferences": "updateAllPreferences", "resetPreferencesToDefaults":"resetPreferencesToDefaults", "validateAndFixPreferences":"validateAndFixPreferences"],
            tuyaDPs       : [:],
            attributes    : [
                [at:"0xFCC0:0x040A",  name:'battery',                       type:"number",  dt: "0x21",   rw: "ro", min:0,    max:100,  step:1,  scale:1,    unit:"%",  description:'<i>Battery percentage remaining</i>'],
                [at:"0xFCC0:0x0271",  name:'system_mode',                   type:"enum",    dt: "0x20",   mfgCode:"0x115f",  rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b>System Mode</b>",                   description:'<i>System Mode</i>'],
                [at:"0xFCC0:0x0272",  name:'preset',                        type:"enum",    dt: "0x20",   mfgCode:"0x115f",  rw: "rw", min:0,    max:2,    step:1,  scale:1,    map:[0: "manual", 1: "auto", 2: "away"], unit:"",         title: "<b>Preset</b>",                        description:'<i>Preset</i>'],
                [at:"0xFCC0:0x0273",  name:'window_detection',              type:"enum",    dt: "0x20",   mfgCode:"0x115f",  rw: "rw", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "off", 1: "on"], unit:"",         title: "<b>Window Detection</b>",              description:'<i>Window detection</i>'],
                [at:"0xFCC0:0x0274",  name:'valve_detection',               type:"enum",    dt: "0x20",   mfgCode:"0x115f",  rw: "rw", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "off", 1: "on"], unit:"",         title: "<b>Valve Detection</b>",               description:'<i>Valve detection</i>'],
                [at:"0xFCC0:0x0275",  name:'valve_alarm',                   type:"enum",    dt: "0x20",   mfgCode:"0x115f",  rw: "ro", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "false", 1: "true"], unit:"",         title: "<b>Valve Alarm</b>",                   description:'<i>Valve alarm</i>'],
                [at:"0xFCC0:0x0277",  name:'child_lock',                    type:"enum",    dt: "0x20",   mfgCode:"0x115f",  rw: "rw", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "unlock", 1: "lock"], unit:"",         title: "<b>Child Lock</b>",                    description:'<i>Child lock</i>'],
                [at:"0xFCC0:0x0279",  name:'away_preset_temperature',       type:"decimal", dt: "0x23",   mfgCode:"0x115f",  rw: "rw", min:5.0,  max:35.0, defaultValue:5.0,    step:0.5, scale:100,  unit:"°C", title: "<b>Away Preset Temperature</b>",       description:'<i>Away preset temperature</i>'],
                [at:"0xFCC0:0x027A",  name:'window_open',                   type:"enum",    dt: "0x20",   mfgCode:"0x115f",  rw: "ro", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "false", 1: "true"], unit:"",         title: "<b>Window Open</b>",                   description:'<i>Window open</i>'],
                [at:"0xFCC0:0x027B",  name:'calibrated',                    type:"enum",    dt: "0x20",   mfgCode:"0x115f",  rw: "ro", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "false", 1: "true"], unit:"",         title: "<b>Calibrated</b>",                    description:'<i>Calibrated</i>'],
                [at:"0xFCC0:0x027E",  name:'sensor',                        type:"enum",    dt: "0x20",   mfgCode:"0x115f",  rw: "ro", min:0,    max:1,    defaultValue:"0",    step:1,  scale:1,    map:[0: "internal", 1: "external"], unit:"",         title: "<b>Sensor</b>",                        description:'<i>Sensor</i>'],
                //
                [at:"0x0201:0x0000",  name:'temperature',                   type:"decimal", dt: "0x21",   rw: "ro", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Temperature</b>",                   description:'<i>Measured temperature</i>'],
                [at:"0x0201:0x0011",  name:'coolingSetpoint',               type:"decimal", dt: "0x21",   rw: "rw", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Cooling Setpoint</b>",              description:'<i>cooling setpoint</i>'],
                [at:"0x0201:0x0012",  name:'heatingSetpoint',               type:"decimal", dt: "0x21",   rw: "rw", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Current Heating Setpoint</b>",      description:'<i>Current heating setpoint</i>'],
                [at:"0x0201:0x001C",  name:'mode',                          type:"enum",    dt: "0x20",   rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b> Mode</b>",                   description:'<i>System Mode ?</i>'],
                //                      ^^^^ TODO - check if this is the same as system_mode    
                [at:"0x0201:0x001E",  name:'thermostatRunMode',             type:"enum",    dt: "0x20",   rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b>thermostatRunMode</b>",                   description:'<i>thermostatRunMode</i>'],
                //                          ^^ TODO  
                [at:"0x0201:0x0020",  name:'battery2',                      type:"number",  dt: "0x20",   rw: "ro", min:0,    max:100,  step:1,  scale:1,    unit:"%",  description:'<i>Battery percentage remaining</i>'],
                //                          ^^ TODO  
                [at:"0x0201:0x0023",  name:'thermostatHoldMode',            type:"enum",    dt: "0x20",   rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b>thermostatHoldMode</b>",                   description:'<i>thermostatHoldMode</i>'],
                //                          ^^ TODO  
                [at:"0x0201:0x0029",  name:'thermostatOperatingState',      type:"enum",    dt: "0x20",   rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b>thermostatOperatingState</b>",                   description:'<i>thermostatOperatingState</i>'],
                //                          ^^ TODO  
                [at:"0x0201:0xFFF2",  name:'unknown',                       type:"number",  dt: "0x21",   rw: "ro", min:0,    max:100,  step:1,  scale:1,    unit:"%",  description:'<i>Battery percentage remaining</i>'],
            ],
            deviceJoinName: "Aqara E1 Thermostat",
            configuration : [:]
    ],

    "UNKNOWN"   : [
            description   : "GENERIC TRV",
            models        : ["*"],
            device        : [type: "TRV", powerSource: "battery", isSleepy:false],
            capabilities  : ["ThermostatHeatingSetpoint": true, "ThermostatOperatingState": true, "ThermostatSetpoint":true, "ThermostatMode":true],

            preferences   : [],
            fingerprints  : [],
            commands      : ["resetStats":"resetStats", 'refresh':'refresh', "initialize":"initialize", "updateAllPreferences": "updateAllPreferences", "resetPreferencesToDefaults":"resetPreferencesToDefaults", "validateAndFixPreferences":"validateAndFixPreferences"],
            tuyaDPs       : [:],
            attributes    : [
                [at:"0x0201:0x0000",  name:'temperature',                   type:"decimal", dt: "UINT16", rw: "ro", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Temperature</b>",                   description:'<i>Measured temperature</i>'],
                [at:"0x0201:0x0011",  name:'coolingSetpoint',               type:"decimal", dt: "UINT16", rw: "rw", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Cooling Setpoint</b>",              description:'<i>cooling setpoint</i>'],
                [at:"0x0201:0x0012",  name:'heatingSetpoint',               type:"decimal", dt: "UINT16", rw: "rw", min:5.0,  max:35.0, step:0.5, scale:100,  unit:"°C", title: "<b>Current Heating Setpoint</b>",      description:'<i>Current heating setpoint</i>'],
                [at:"0x0201:0x001C",  name:'mode',                          type:"enum",    dt: "UINT8",  rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b> Mode</b>",                   description:'<i>System Mode ?</i>'],
                [at:"0x0201:0x001E",  name:'thermostatRunMode',             type:"enum",    dt: "UINT8",  rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b>thermostatRunMode</b>",                   description:'<i>thermostatRunMode</i>'],
                [at:"0x0201:0x0020",  name:'battery2',                     type:"number",  dt: "UINT16", rw: "ro", min:0,    max:100,  step:1,  scale:1,    unit:"%",  description:'<i>Battery percentage remaining</i>'],
                [at:"0x0201:0x0023",  name:'thermostatHoldMode',           type:"enum",    dt: "UINT8",  rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b>thermostatHoldMode</b>",                   description:'<i>thermostatHoldMode</i>'],
                [at:"0x0201:0x0029",  name:'thermostatOperatingState',      type:"enum",    dt: "UINT8",  rw: "rw", min:0,    max:1,    step:1,  scale:1,    map:[0: "off", 1: "heat"], unit:"",         title: "<b>thermostatOperatingState</b>",                   description:'<i>thermostatOperatingState</i>'],
            ],
            deviceJoinName: "UNKWNOWN TRV",
            configuration : [:]
    ]

]



void thermostatEvent(eventName, value, raw) {
    sendEvent(name: eventName, value: value, type: "physical")
    logInfo "${eventName} is ${value} (raw ${raw})"
}

// called from parseXiaomiClusterLib in xiaomiLib.groovy (xiaomi cluster 0xFCC0 )
//
void parseXiaomiClusterThermostatLib(final Map descMap) {
    //logWarn "parseXiaomiClusterThermostatLib: received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})"
    final Integer raw
    final String  value
    switch (descMap.attrInt as Integer) {
        case 0x040a:    // E1 battery - read only
            raw = hexStrToUnsignedInt(descMap.value)
            thermostatEvent("battery", raw, raw)
            break
        case 0x00F7 :   // XIAOMI_SPECIAL_REPORT_ID:  0x00F7 sent every 55 minutes
            final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value)
            parseXiaomiClusterThermostatTags(tags)
            break
        case 0x0271:    // result['system_mode'] = {1: 'heat', 0: 'off'}[value]; (heating state) - rw
            raw = hexStrToUnsignedInt(descMap.value)
            value = SystemModeOpts.options[raw as int]
            thermostatEvent("system_mode", value, raw)
            break;
        case 0x0272:    // result['preset'] = {2: 'away', 1: 'auto', 0: 'manual'}[value]; - rw  ['manual', 'auto', 'holiday']
            raw = hexStrToUnsignedInt(descMap.value)
            value = PresetOpts.options[raw as int]
            thermostatEvent("preset", value, raw)
            break;
        case 0x0273:    // result['window_detection'] = {1: 'ON', 0: 'OFF'}[value]; - rw
            raw = hexStrToUnsignedInt(descMap.value)
            value = WindowDetectionOpts.options[raw as int]
            thermostatEvent("window_detection", value, raw)
            break;
        case 0x0274:    // result['valve_detection'] = {1: 'ON', 0: 'OFF'}[value]; -rw 
            raw = hexStrToUnsignedInt(descMap.value)
            value = ValveDetectionOpts.options[raw as int]
            thermostatEvent("valve_detection", value, raw)
            break;
        case 0x0275:    // result['valve_alarm'] = {1: true, 0: false}[value]; - read only!
            raw = hexStrToUnsignedInt(descMap.value)
            value = ValveAlarmOpts.options[raw as int]
            thermostatEvent("valve_alarm", value, raw)
            break;
        case 0x0277:    // result['child_lock'] = {1: 'LOCK', 0: 'UNLOCK'}[value]; - rw
            raw = hexStrToUnsignedInt(descMap.value)
            value = ChildLockOpts.options[raw as int]
            thermostatEvent("child_lock", value, raw)
            break;
        case 0x0279:    // result['away_preset_temperature'] = (value / 100).toFixed(1); - rw
            raw = hexStrToUnsignedInt(descMap.value)
            value = raw / 100
            thermostatEvent("away_preset_temperature", value, raw)
            break;
        case 0x027a:    // result['window_open'] = {1: true, 0: false}[value]; - read only
            raw = hexStrToUnsignedInt(descMap.value)
            value = WindowOpenOpts.options[raw as int]
            thermostatEvent("window_open", value, raw)
            break;
        case 0x027b:    // result['calibrated'] = {1: true, 0: false}[value]; - read only
            raw = hexStrToUnsignedInt(descMap.value)
            value = CalibratedOpts.options[raw as int]
            thermostatEvent("calibrated", value, raw)
            break;
        case 0x0276:    // unknown
        case 0x027c:    // unknown
        case 0x027d:    // unknown
        case 0x0280:    // unknown
        case 0xfff2:    // unknown
        case 0x00ff:    // unknown
        case 0x00f7:    // unknown
        case 0xfff2:    // unknown
        case 0x00FF:
            try {
                raw = hexStrToUnsignedInt(descMap.value)
                logDebug "Aqara E1 TRV unknown attribute ${descMap.attrInt} value raw = ${raw}"
            }
            catch (e) {
                logWarn "exception caught while processing Aqara E1 TRV unknown attribute ${descMap.attrInt} descMap.value = ${descMap.value}"
            }
            break;
        case 0x027e:    // result['sensor'] = {1: 'external', 0: 'internal'}[value]; - read only?
            raw = hexStrToUnsignedInt(descMap.value)
            value = SensorOpts.options[raw as int]
            thermostatEvent("sensor", value, raw)
            break;
        default:
            logWarn "parseXiaomiClusterThermostatLib: received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})"
            break
    }
}

// called from parseXiaomiClusterThermostatLib 
void parseXiaomiClusterThermostatTags(final Map<Integer, Object> tags) {
    tags.each { final Integer tag, final Object value ->
        switch (tag) {
            case 0x01:    // battery voltage
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery voltage is ${value/1000}V (raw=${value})"
                break
            case 0x03:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} device internal chip temperature is ${value}&deg; (ignore it!)"
                break
            case 0x05:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} RSSI is ${value}"
                break
            case 0x06:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} LQI is ${value}"
                break
            case 0x08:            // SWBUILD_TAG_ID:
                final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0')
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})"
                device.updateDataValue("aqaraVersion", swBuild)
                break
            case 0x0a:
                String nwk = intToHexStr(value as Integer,2)
                if (state.health == null) { state.health = [:] }
                String oldNWK = state.health['parentNWK'] ?: 'n/a'
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>"
                if (oldNWK != nwk ) {
                    logWarn "parentNWK changed from ${oldNWK} to ${nwk}"
                    state.health['parentNWK']  = nwk
                    state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1
                }
                break
            case 0x0d:
                logDebug "xiaomi decode E1 thermostat unknown tag: 0x${intToHexStr(tag, 1)}=${value}"
                break            
            case 0x11:
                logDebug "xiaomi decode E1 thermostat unknown tag: 0x${intToHexStr(tag, 1)}=${value}"
                break            
            case 0x64:
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} temperature is ${value/100} (raw ${value})"    // Aqara TVOC
                break
            case 0x65:
                logDebug "xiaomi decode E1 thermostat unknown tag: 0x${intToHexStr(tag, 1)}=${value}"
                break
            case 0x66:
                logDebug "xiaomi decode E1 thermostat temperature tag: 0x${intToHexStr(tag, 1)}=${value}"
                handleTemperatureEvent(value/100.0)
                break
            case 0x67:
                logDebug "xiaomi decode E1 thermostat heatingSetpoint tag: 0x${intToHexStr(tag, 1)}=${value}"
                break
            case 0x68:
                logDebug "xiaomi decode E1 thermostat unknown tag: 0x${intToHexStr(tag, 1)}=${value}"
                break
            case 0x69:
                logDebug "xiaomi decode E1 thermostat battery tag: 0x${intToHexStr(tag, 1)}=${value}"
                break
            case 0x6a:
                logDebug "xiaomi decode E1 thermostat unknown tag: 0x${intToHexStr(tag, 1)}=${value}"
                break
            default:
                logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}"
        }
    }
}


/*
 * -----------------------------------------------------------------------------
 * thermostat cluster 0x0201
 * called from parseThermostatCluster() in the main code ...
 * -----------------------------------------------------------------------------
*/

void parseThermostatClusterThermostat(final Map descMap) {
    final Integer value = safeToInt(hexStrToUnsignedInt(descMap.value))
    if (settings.logEnable) {
        log.trace "zigbee received Thermostat cluster (0x0201) attribute 0x${descMap.attrId} value ${value} (raw ${descMap.value})"
    }

    switch (descMap.attrInt as Integer) {
        case 0x000:                      // temperature
            logDebug "temperature = ${value/100.0} (raw ${value})"
            handleTemperatureEvent(value/100.0)
            break
        case 0x0011:                      // cooling setpoint
            logInfo "cooling setpoint = ${value/100.0} (raw ${value})"
            break
        case 0x0012:                      // heating setpoint
            logInfo "heating setpoint = ${value/100.0} (raw ${value})"
            handleHeatingSetpointEvent(value/100.0)
            break
        case 0x001c:                      // mode
            logInfo "mode = ${value} (raw ${value})"
            break
        case 0x001e:                      // thermostatRunMode
            logInfo "thermostatRunMode = ${value} (raw ${value})"
            break
        case 0x0020:                      // battery
            logInfo "battery = ${value} (raw ${value})"
            break
        case 0x0023:                      // thermostatHoldMode
            logInfo "thermostatHoldMode = ${value} (raw ${value})"
            break
        case 0x0029:                      // thermostatOperatingState
            logInfo "thermostatOperatingState = ${value} (raw ${value})"
            break
        case 0xfff2:    // unknown
            logDebug "Aqara E1 TRV unknown attribute ${descMap.attrInt} value raw = ${value}"
            break;
        default:
            log.warn "zigbee received unknown Thermostat cluster (0x0201) attribute 0x${descMap.attrId} (value ${descMap.value})"
            break
    }
}

def handleHeatingSetpointEvent( temperature ) {
    setHeatingSetpoint(temperature)
}

//  ThermostatHeatingSetpoint command
//  sends TuyaCommand and checks after 4 seconds
//  1°C steps. (0.5°C setting on the TRV itself, rounded for zigbee interface)
def setHeatingSetpoint( temperature ) {
    def previousSetpoint = device.currentState('heatingSetpoint')?.value ?: 0
    double tempDouble
    //logDebug "setHeatingSetpoint temperature = ${temperature}  as int = ${temperature as int} (previousSetpointt = ${previousSetpoint})"
    if (true) {
        //logDebug "0.5 C correction of the heating setpoint${temperature}"
        tempDouble = safeToDouble(temperature)
        tempDouble = Math.round(tempDouble * 2) / 2.0
    }
    else {
        if (temperature != (temperature as int)) {
            if ((temperature as double) > (previousSetpoint as double)) {
                temperature = (temperature + 0.5 ) as int
            }
            else {
                temperature = temperature as int
            }
        logDebug "corrected heating setpoint ${temperature}"
        }
        tempDouble = temperature
    }
    def maxTemp = settings?.maxThermostatTemp ?: 50
    def minTemp = settings?.minThermostatTemp ?: 5
    if (tempDouble > maxTemp ) tempDouble = maxTemp
    if (tempDouble < minTemp) tempDouble = minTemp
    tempDouble = tempDouble.round(1)
    Map eventMap = [name: "heatingSetpoint",  value: tempDouble, unit: "\u00B0"+"C"]
    eventMap.descriptionText = "heatingSetpoint is ${tempDouble}"
    sendHeatingSetpointEvent(eventMap)
    eventMap = [name: "thermostatSetpoint", value: tempDouble, unit: "\u00B0"+"C"]
    eventMap.descriptionText = null
    sendHeatingSetpointEvent(eventMap)
    updateDataValue("lastRunningMode", "heat")
    // 
    zigbee.writeAttribute(0x0201, 0x12, 0x29, (tempDouble * 100) as int)        // raw:F6690102010A1200299808, dni:F669, endpoint:01, cluster:0201, size:0A, attrId:0012, encoding:29, command:0A, value:0898, clusterInt:513, attrInt:18
}

private void sendHeatingSetpointEvent(Map eventMap) {
    if (eventMap.descriptionText != null) { logInfo "${eventMap.descriptionText}" }
    sendEvent(eventMap)
}

// TODO - not called 
void processTuyaDpThermostat(descMap, dp, dp_id, fncmd) {

    switch (dp) {
        case 0x01 : // on/off
            sendSwitchEvent(fncmd)
            break
        default :
            logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}" 
            break            
    }
}


def preset( preset ) {
    logDebug "preset(${preset}) called!"
    if (preset == "auto") {
        setPresetMode("auto")               // hand symbol NOT shown
    }
    else if (preset == "manual") {
        setPresetMode("manual")             // hand symbol is shown on the LCD
    }
    else if (preset == "away") {
        setPresetMode("away")               // 5 degreees 
    }
    else {
        logWarn "preset: unknown preset ${preset}"
    }
}

def setPresetMode(mode) {
    List<String> cmds = []
    logDebug "sending setPresetMode(${mode})"    
    if (isAqaraTRV()) {
        // {'manual': 0, 'auto': 1, 'away': 2}), type: 0x20}
        if (mode == "auto") {
            cmds = zigbee.writeAttribute(0xFCC0, 0x0272, 0x20, 0x01, [mfgCode: 0x115F], delay=200)
        }
        else if (mode == "manual") {
            cmds = zigbee.writeAttribute(0xFCC0, 0x0272, 0x20, 0x00, [mfgCode: 0x115F], delay=200)
        }
        else if (mode == "away") {
            cmds = zigbee.writeAttribute(0xFCC0, 0x0272, 0x20, 0x02, [mfgCode: 0x115F], delay=200)
        }
        else {
            logWarn "setPresetMode: Aqara TRV unknown preset ${mode}"
        }
    }
    else {
        // TODO - set generic thermostat mode
        log.warn "setPresetMode NOT IMPLEMENTED"
        return
    }
    if (cmds == []) { cmds = ["delay 299"] }
    sendZigbeeCommands(cmds)

}

def setThermostatMode( mode ) {
    List<String> cmds = []
    logDebug "sending setThermostatMode(${mode})"
    //state.mode = mode
    if (isAqaraTRV()) {
        // TODO - set Aqara E1 thermostat mode
        switch(mode) {
            case "off":
                cmds = zigbee.writeAttribute(0xFCC0, 0x0271, 0x20, 0x00, [mfgCode: 0x115F], delay=200)        // 'off': 0, 'heat': 1
                break
            case "heat":
                cmds = zigbee.writeAttribute(0xFCC0, 0x0271, 0x20, 0x01, [mfgCode: 0x115F], delay=200)        // 'off': 0, 'heat': 1
                break
            default:
                logWarn "setThermostatMode: unknown AqaraTRV mode ${mode}"
                break
        }
    }
    else {
        // TODO - set generic thermostat mode
        log.warn "setThermostatMode NOT IMPLEMENTED"
        return
    }
    if (cmds == []) { cmds = ["delay 299"] }
    sendZigbeeCommands(cmds)
}

def setCoolingSetpoint(temperature){
    logDebug "setCoolingSetpoint(${temperature}) called!"
    if (temperature != (temperature as int)) {
        temperature = (temperature + 0.5 ) as int
        logDebug "corrected temperature: ${temperature}"
    }
    sendEvent(name: "coolingSetpoint", value: temperature, unit: "\u00B0"+"C")
}


def heat(){
    setThermostatMode("heat")
}

def thermostatOff(){
    setThermostatMode("off")
}

def thermostatOn() {
    heat()
}

def setThermostatFanMode(fanMode) { sendEvent(name: "thermostatFanMode", value: "${fanMode}", descriptionText: getDescriptionText("thermostatFanMode is ${fanMode}")) }
def auto() { setThermostatMode("auto") }
def emergencyHeat() { setThermostatMode("emergency heat") }
def cool() { setThermostatMode("cool") }
def fanAuto() { setThermostatFanMode("auto") }
def fanCirculate() { setThermostatFanMode("circulate") }
def fanOn() { setThermostatFanMode("on") }

def sendThermostatOperatingStateEvent( st ) {
    sendEvent(name: "thermostatOperatingState", value: st)
    state.lastThermostatOperatingState = st
}

void sendSupportedThermostatModes() {
    def supportedThermostatModes = []
    supportedThermostatModes = ["off", "heat", "auto"]
    logInfo "supportedThermostatModes: ${supportedThermostatModes}"
    sendEvent(name: "supportedThermostatModes", value:  JsonOutput.toJson(supportedThermostatModes), isStateChange: true)
}


/**
 * Schedule thermostat polling
 * @param intervalMins interval in seconds
 */
private void scheduleThermostatPolling(final int intervalSecs) {
    String cron = getCron( intervalSecs )
    logDebug "cron = ${cron}"
    schedule(cron, 'autoPollThermostat')
}

private void unScheduleThermostatPolling() {
    unschedule('autoPollThermostat')
}

/**
 * Scheduled job for polling device specific attribute(s)
 */
void autoPollThermostat() {
    logDebug "autoPollThermostat()..."
    checkDriverVersion()
    List<String> cmds = []
    if (state.states == null) state.states = [:]
    //state.states["isRefresh"] = true
    
    cmds += zigbee.readAttribute(0x0201, 0x0000, [:], delay=3500)      // 0x0000=local temperature, 0x0011=cooling setpoint, 0x0012=heating setpoint, 0x001B=controlledSequenceOfOperation, 0x001C=system mode (enum8 )       
    
    if (cmds != null && cmds != [] ) {
        sendZigbeeCommands(cmds)
    }    
}

//
// called from updated() in the main code ...
void updatedThermostat() {
    logDebug "updatedThermostat()..."
    //
    if (settings?.forcedProfile != null) {
        logDebug "current state.deviceProfile=${state.deviceProfile}, settings.forcedProfile=${settings?.forcedProfile}, getProfileKey()=${getProfileKey(settings?.forcedProfile)}"
        if (getProfileKey(settings?.forcedProfile) != state.deviceProfile) {
            logWarn "changing the device profile from ${state.deviceProfile} to ${getProfileKey(settings?.forcedProfile)}"
            state.deviceProfile = getProfileKey(settings?.forcedProfile)
            //initializeVars(fullInit = false) 
            initVarsThermostat(fullInit = false)
            resetPreferencesToDefaults(debug=true)
            logInfo "press F5 to refresh the page"
        }
    }
    else {
        logDebug "forcedProfile is not set"
    }    
        final int pollingInterval = (settings.temperaturePollingInterval as Integer) ?: 0
        if (pollingInterval > 0) {
            logInfo "updatedThermostat: scheduling temperature polling every ${pollingInterval} seconds"
            scheduleThermostatPolling(pollingInterval)
        }
        else {
            unScheduleThermostatPolling()
            logInfo "updatedThermostat: thermostat polling is disabled!"
        }
}

def refreshThermostat() {
    List<String> cmds = []
    //cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)                                         // battery voltage (E1 does not send percentage)
    cmds += zigbee.readAttribute(0x0201, [0x0000, 0x0011, 0x0012, 0x001B, 0x001C], [:], delay=3500)       // 0x0000=local temperature, 0x0011=cooling setpoint, 0x0012=heating setpoint, 0x001B=controlledSequenceOfOperation, 0x001C=system mode (enum8 )       

    cmds += zigbee.readAttribute(0xFCC0, [0x0271, 0x0272, 0x0273, 0x0274, 0x0275, 0x0277, 0x0279, 0x027A, 0x027B, 0x027E], [mfgCode: 0x115F], delay=3500)       
    cmds += zigbee.readAttribute(0xFCC0, 0x040a, [mfgCode: 0x115F], delay=500)       
   
    // stock Generic Zigbee Thermostat Refresh answer:
    // raw:F669010201441C0030011E008600000029640A2900861B0000300412000029540B110000299808, dni:F669, endpoint:01, cluster:0201, size:44, attrId:001C, encoding:30, command:01, value:01, clusterInt:513, attrInt:28, additionalAttrs:[[status:86, attrId:001E, attrInt:30], [value:0A64, encoding:29, attrId:0000, consumedBytes:5, attrInt:0], [status:86, attrId:0029, attrInt:41], [value:04, encoding:30, attrId:001B, consumedBytes:4, attrInt:27], [value:0B54, encoding:29, attrId:0012, consumedBytes:5, attrInt:18], [value:0898, encoding:29, attrId:0011, consumedBytes:5, attrInt:17]]
    // conclusion : binding and reporting configuration for this Aqara E1 thermostat does nothing... We need polling mechanism for faster updates of the internal temperature readings.
    if (cmds == []) { cmds = ["delay 299"] }
    logDebug "refreshThermostat: ${cmds} "
    return cmds
}

def configureThermostat() {
    List<String> cmds = []
    // TODO !!
    logDebug "configureThermostat() : ${cmds}"
    if (cmds == []) { cmds = ["delay 299"] }    // no , 
    return cmds    
}

def initializeThermostat()
{
    List<String> cmds = []
    int intMinTime = 300
    int intMaxTime = 600    // report temperature every 10 minutes !

    logDebug "configuring cluster 0x0201 ..."
    cmds += ["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0201 {${device.zigbeeId}} {}", "delay 251", ]
    //cmds += zigbee.configureReporting(0x0201, 0x0012, 0x29, intMinTime as int, intMaxTime as int, 0x01, [:], delay=541)
    //cmds += zigbee.configureReporting(0x0201, 0x0000, 0x29, 20, 120, 0x01, [:], delay=542)

    cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0201 0x0012 0x29 1 600 {}", "delay 551", ]
    cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0201 0x0000 0x29 20 300 {}", "delay 551", ]
    cmds += ["he cr 0x${device.deviceNetworkId} 0x01 0x0201 0x001C 0x30 1 600 {}", "delay 551", ]
    
    cmds +=  zigbee.reportingConfiguration(0x0201, 0x0012, [:], 551)    // read it back - doesn't work
    cmds +=  zigbee.reportingConfiguration(0x0201, 0x0000, [:], 552)    // read it back - doesn't wor
    cmds +=  zigbee.reportingConfiguration(0x0201, 0x001C, [:], 552)    // read it back - doesn't wor


    logDebug "initializeThermostat() : ${cmds}"
    if (cmds == []) { cmds = ["delay 299",] }
    return cmds        
}


void initVarsThermostat(boolean fullInit=false) {
    logDebug "initVarsThermostat(${fullInit})"
    if (state.deviceProfile == null) {
        setDeviceNameAndProfile()               // in deviceProfileiLib.groovy
    }

    if (fullInit == true || state.lastThermostatMode == null) state.lastThermostatMode = "unknown"
    if (fullInit == true || state.lastThermostatOperatingState == null) state.lastThermostatOperatingState = "unknown"
    if (fullInit || settings?.temperaturePollingInterval == null) device.updateSetting('temperaturePollingInterval', [value: TemperaturePollingIntervalOpts.defaultValue.toString(), type: 'enum'])

    if (fullInit == true) {
        resetPreferencesToDefaults()
    }
    //    
    
}


void initEventsThermostat(boolean fullInit=false) {
    sendSupportedThermostatModes()
    sendEvent(name: "supportedThermostatFanModes", value: JsonOutput.toJson(["auto"]), isStateChange: true)    
    sendEvent(name: "thermostatMode", value: "heat", isStateChange: true, description: "inital attribute setting")
    sendEvent(name: "thermostatFanMode", value: "auto", isStateChange: true, description: "inital attribute setting")
    state.lastThermostatMode = "heat"
    sendThermostatOperatingStateEvent( "idle" )
    sendEvent(name: "thermostatOperatingState", value: "idle", isStateChange: true, description: "inital attribute setting")
    sendEvent(name: "thermostatSetpoint", value:  12.3, unit: "\u00B0"+"C", isStateChange: true, description: "inital attribute setting")        // Google Home compatibility
    sendEvent(name: "heatingSetpoint", value: 12.3, unit: "\u00B0"+"C", isStateChange: true, description: "inital attribute setting")
    sendEvent(name: "coolingSetpoint", value: 34.5, unit: "\u00B0"+"C", isStateChange: true, description: "inital attribute setting")
    sendEvent(name: "temperature", value: 23.4, unit: "\u00B0"+"C", isStateChange: true, description: "inital attribute setting")    
    updateDataValue("lastRunningMode", "heat")    
    
}

private getDescriptionText(msg) {
    def descriptionText = "${device.displayName} ${msg}"
    if (settings?.txtEnable) log.info "${descriptionText}"
    return descriptionText
}

def testT(par) {
    logWarn "testT(${par})"
}


// ~~~~~ start include (144) kkossev.commonLib ~~~~~
library ( // library marker kkossev.commonLib, line 1
    base: "driver", // library marker kkossev.commonLib, line 2
    author: "Krassimir Kossev", // library marker kkossev.commonLib, line 3
    category: "zigbee", // library marker kkossev.commonLib, line 4
    description: "Common ZCL Library", // library marker kkossev.commonLib, line 5
    name: "commonLib", // library marker kkossev.commonLib, line 6
    namespace: "kkossev", // library marker kkossev.commonLib, line 7
    importUrl: "https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/commonLib.groovy", // library marker kkossev.commonLib, line 8
    version: "3.0.0", // library marker kkossev.commonLib, line 9
    documentationLink: "" // library marker kkossev.commonLib, line 10
) // library marker kkossev.commonLib, line 11
/* // library marker kkossev.commonLib, line 12
 *  Common ZCL Library // library marker kkossev.commonLib, line 13
 * // library marker kkossev.commonLib, line 14
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.commonLib, line 15
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.commonLib, line 16
 * // library marker kkossev.commonLib, line 17
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.commonLib, line 18
 * // library marker kkossev.commonLib, line 19
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.commonLib, line 20
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.commonLib, line 21
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.commonLib, line 22
 * // library marker kkossev.commonLib, line 23
 * ver. 1.0.0  2022-06-18 kkossev  - first beta version // library marker kkossev.commonLib, line 24
 * ver. 2.0.0  2023-05-08 kkossev  - first published version 2.x.x // library marker kkossev.commonLib, line 25
 * ver. 2.1.6  2023-11-06 kkossev  - last update on version 2.x.x // library marker kkossev.commonLib, line 26
 * ver. 3.0.0  2023-11-16 kkossev  - (dev.branch) first version 3.x.x // library marker kkossev.commonLib, line 27
 * // library marker kkossev.commonLib, line 28
 *                                   TODO:  // library marker kkossev.commonLib, line 29
*/ // library marker kkossev.commonLib, line 30

def commonLibVersion()   {"3.0.0"} // library marker kkossev.commonLib, line 32
def thermostatLibStamp() {"2023/11/16 9:54 PM"} // library marker kkossev.commonLib, line 33

//@Field static final Boolean _DEBUG = false // library marker kkossev.commonLib, line 35

import groovy.transform.Field // library marker kkossev.commonLib, line 37
import hubitat.device.HubMultiAction // library marker kkossev.commonLib, line 38
import hubitat.device.Protocol // library marker kkossev.commonLib, line 39
import hubitat.helper.HexUtils // library marker kkossev.commonLib, line 40
import hubitat.zigbee.zcl.DataType // library marker kkossev.commonLib, line 41
import java.util.concurrent.ConcurrentHashMap // library marker kkossev.commonLib, line 42
import groovy.json.JsonOutput // library marker kkossev.commonLib, line 43


@Field static final Boolean _THREE_STATE = true // library marker kkossev.commonLib, line 46

metadata { // library marker kkossev.commonLib, line 48

        if (_DEBUG) { // library marker kkossev.commonLib, line 50
            command 'test', [[name: "test", type: "STRING", description: "test", defaultValue : ""]]  // library marker kkossev.commonLib, line 51
            command 'parseTest', [[name: "parseTest", type: "STRING", description: "parseTest", defaultValue : ""]] // library marker kkossev.commonLib, line 52
            command "tuyaTest", [ // library marker kkossev.commonLib, line 53
                [name:"dpCommand", type: "STRING", description: "Tuya DP Command", constraints: ["STRING"]], // library marker kkossev.commonLib, line 54
                [name:"dpValue",   type: "STRING", description: "Tuya DP value", constraints: ["STRING"]], // library marker kkossev.commonLib, line 55
                [name:"dpType",    type: "ENUM",   constraints: ["DP_TYPE_VALUE", "DP_TYPE_BOOL", "DP_TYPE_ENUM"], description: "DP data type"] // library marker kkossev.commonLib, line 56
            ] // library marker kkossev.commonLib, line 57
        } // library marker kkossev.commonLib, line 58


        // common capabilities for all device types // library marker kkossev.commonLib, line 61
        capability 'Configuration' // library marker kkossev.commonLib, line 62
        capability 'Refresh' // library marker kkossev.commonLib, line 63
        capability 'Health Check' // library marker kkossev.commonLib, line 64

        // common attributes for all device types // library marker kkossev.commonLib, line 66
        attribute 'healthStatus', 'enum', ['unknown', 'offline', 'online'] // library marker kkossev.commonLib, line 67
        attribute "rtt", "number"  // library marker kkossev.commonLib, line 68
        attribute "Info", "string" // library marker kkossev.commonLib, line 69

        // common commands for all device types // library marker kkossev.commonLib, line 71
        // removed from version 2.0.6    //command "initialize", [[name: "Manually initialize the device after switching drivers.  \n\r     ***** Will load device default values! *****"]]    // do NOT declare Initialize capability! // library marker kkossev.commonLib, line 72
        command "configure", [[name:"normally it is not needed to configure anything", type: "ENUM",   constraints: ["--- select ---"]+ConfigureOpts.keySet() as List<String>]] // library marker kkossev.commonLib, line 73

        // deviceType specific capabilities, commands and attributes          // library marker kkossev.commonLib, line 75
        if (deviceType in ["Device"]) { // library marker kkossev.commonLib, line 76
            if (_DEBUG) { // library marker kkossev.commonLib, line 77
                command "getAllProperties",       [[name: "Get All Properties"]] // library marker kkossev.commonLib, line 78
            } // library marker kkossev.commonLib, line 79
        } // library marker kkossev.commonLib, line 80
        if (_DEBUG || (deviceType in ["Dimmer", "ButtonDimmer", "Switch", "Valve"])) { // library marker kkossev.commonLib, line 81
            command "zigbeeGroups", [ // library marker kkossev.commonLib, line 82
                [name:"command", type: "ENUM",   constraints: ZigbeeGroupsOpts.options.values() as List<String>], // library marker kkossev.commonLib, line 83
                [name:"value",   type: "STRING", description: "Group number", constraints: ["STRING"]] // library marker kkossev.commonLib, line 84
            ] // library marker kkossev.commonLib, line 85
        }         // library marker kkossev.commonLib, line 86
        if (deviceType in  ["Device", "THSensor", "MotionSensor", "LightSensor", "AirQuality", "Thermostat", "AqaraCube", "Radar"]) { // library marker kkossev.commonLib, line 87
            capability "Sensor" // library marker kkossev.commonLib, line 88
        } // library marker kkossev.commonLib, line 89
        if (deviceType in  ["Device", "MotionSensor", "Radar"]) { // library marker kkossev.commonLib, line 90
            capability "MotionSensor" // library marker kkossev.commonLib, line 91
        } // library marker kkossev.commonLib, line 92
        if (deviceType in  ["Device", "Switch", "Relay", "Plug", "Outlet", "Thermostat", "Fingerbot", "Dimmer", "Bulb", "IRBlaster"]) { // library marker kkossev.commonLib, line 93
            capability "Actuator" // library marker kkossev.commonLib, line 94
        } // library marker kkossev.commonLib, line 95
        if (deviceType in  ["Device", "THSensor", "LightSensor", "MotionSensor", "Thermostat", "Fingerbot", "ButtonDimmer", "AqaraCube", "IRBlaster"]) { // library marker kkossev.commonLib, line 96
            capability "Battery" // library marker kkossev.commonLib, line 97
            attribute "batteryVoltage", "number" // library marker kkossev.commonLib, line 98
        } // library marker kkossev.commonLib, line 99
        if (deviceType in  ["Thermostat"]) { // library marker kkossev.commonLib, line 100
            capability "ThermostatHeatingSetpoint" // library marker kkossev.commonLib, line 101
        } // library marker kkossev.commonLib, line 102
        if (deviceType in  ["Plug", "Outlet"]) { // library marker kkossev.commonLib, line 103
            capability "Outlet" // library marker kkossev.commonLib, line 104
        }         // library marker kkossev.commonLib, line 105
        if (deviceType in  ["Device", "Switch", "Plug", "Outlet", "Dimmer", "Fingerbot", "Bulb"]) { // library marker kkossev.commonLib, line 106
            capability "Switch" // library marker kkossev.commonLib, line 107
            if (_THREE_STATE == true) { // library marker kkossev.commonLib, line 108
                attribute "switch", "enum", SwitchThreeStateOpts.options.values() as List<String> // library marker kkossev.commonLib, line 109
            } // library marker kkossev.commonLib, line 110
        }         // library marker kkossev.commonLib, line 111
        if (deviceType in ["Dimmer", "ButtonDimmer", "Bulb"]) { // library marker kkossev.commonLib, line 112
            capability "SwitchLevel" // library marker kkossev.commonLib, line 113
        } // library marker kkossev.commonLib, line 114
        if (deviceType in  ["Button", "ButtonDimmer", "AqaraCube"]) { // library marker kkossev.commonLib, line 115
            capability "PushableButton" // library marker kkossev.commonLib, line 116
            capability "DoubleTapableButton" // library marker kkossev.commonLib, line 117
            capability "HoldableButton" // library marker kkossev.commonLib, line 118
            capability "ReleasableButton" // library marker kkossev.commonLib, line 119
        } // library marker kkossev.commonLib, line 120
        if (deviceType in  ["Device", "Fingerbot"]) { // library marker kkossev.commonLib, line 121
            capability "Momentary" // library marker kkossev.commonLib, line 122
        } // library marker kkossev.commonLib, line 123
        if (deviceType in  ["Device", "THSensor", "AirQuality", "Thermostat"]) { // library marker kkossev.commonLib, line 124
            capability "TemperatureMeasurement" // library marker kkossev.commonLib, line 125
        } // library marker kkossev.commonLib, line 126
        if (deviceType in  ["Device", "THSensor", "AirQuality"]) { // library marker kkossev.commonLib, line 127
            capability "RelativeHumidityMeasurement"             // library marker kkossev.commonLib, line 128
        } // library marker kkossev.commonLib, line 129
        if (deviceType in  ["Device", "LightSensor", "Radar"]) { // library marker kkossev.commonLib, line 130
            capability "IlluminanceMeasurement" // library marker kkossev.commonLib, line 131
        } // library marker kkossev.commonLib, line 132
        if (deviceType in  ["AirQuality"]) { // library marker kkossev.commonLib, line 133
            capability "AirQuality"            // Attributes: airQualityIndex - NUMBER, range:0..500 // library marker kkossev.commonLib, line 134
        } // library marker kkossev.commonLib, line 135

        // trap for Hubitat F2 bug // library marker kkossev.commonLib, line 137
        fingerprint profileId:"0104", endpointId:"F2", inClusters:"", outClusters:"", model:"unknown", manufacturer:"unknown", deviceJoinName: "Zigbee device affected by Hubitat F2 bug"  // library marker kkossev.commonLib, line 138
        if (deviceType in  ["LightSensor"]) { // library marker kkossev.commonLib, line 139
            fingerprint profileId:"0104", endpointId:"01", inClusters:"0000,0400,0001,0500", outClusters:"0019,000A", model:"TS0222", manufacturer:"_TYZB01_4mdqxxnn", deviceJoinName: "Tuya Illuminance Sensor TS0222" // library marker kkossev.commonLib, line 140
            fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_khx7nnka", deviceJoinName: "Tuya Illuminance Sensor TS0601" // library marker kkossev.commonLib, line 141
            fingerprint profileId:"0104", endpointId:"01", inClusters:"0004,0005,EF00,0000", outClusters:"0019,000A", model:"TS0601", manufacturer:"_TZE200_yi4jtqq1", deviceJoinName: "Tuya Illuminance Sensor TS0601" // library marker kkossev.commonLib, line 142
        } // library marker kkossev.commonLib, line 143

    preferences { // library marker kkossev.commonLib, line 145
        input name: 'txtEnable', type: 'bool', title: '<b>Enable descriptionText logging</b>', defaultValue: true, description: '<i>Enables command logging.</i>' // library marker kkossev.commonLib, line 146
        input name: 'logEnable', type: 'bool', title: '<b>Enable debug logging</b>', defaultValue: true, description: '<i>Turns on debug logging for 24 hours.</i>' // library marker kkossev.commonLib, line 147
        if (advancedOptions == true || advancedOptions == false) { // groovy ... // library marker kkossev.commonLib, line 148
            if (device.hasCapability("TemperatureMeasurement") || device.hasCapability("RelativeHumidityMeasurement") || device.hasCapability("IlluminanceMeasurement")) { // library marker kkossev.commonLib, line 149
                input name: "minReportingTime", type: "number", title: "<b>Minimum time between reports</b>", description: "<i>Minimum reporting interval, seconds (1..300)</i>", range: "1..300", defaultValue: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 150
                input name: "maxReportingTime", type: "number", title: "<b>Maximum time between reports</b>", description: "<i>Maximum reporting interval, seconds (120..10000)</i>", range: "120..10000", defaultValue: DEFAULT_MAX_REPORTING_TIME // library marker kkossev.commonLib, line 151
            } // library marker kkossev.commonLib, line 152
            if (device.hasCapability("IlluminanceMeasurement")) { // library marker kkossev.commonLib, line 153
                input name: "illuminanceThreshold", type: "number", title: "<b>Illuminance Reporting Threshold</b>", description: "<i>Illuminance reporting threshold, range (1..255)<br>Bigger values will result in less frequent reporting</i>", range: "1..255", defaultValue: DEFAULT_ILLUMINANCE_THRESHOLD // library marker kkossev.commonLib, line 154
                input name: "illuminanceCoeff", type: "decimal", title: "<b>Illuminance Correction Coefficient</b>", description: "<i>Illuminance correction coefficient, range (0.10..10.00)</i>", range: "0.10..10.00", defaultValue: 1.00 // library marker kkossev.commonLib, line 155

            } // library marker kkossev.commonLib, line 157
        } // library marker kkossev.commonLib, line 158

        input name: 'advancedOptions', type: 'bool', title: '<b>Advanced Options</b>', description: "<i>These advanced options should be already automatically set in an optimal way for your device...</i>", defaultValue: false // library marker kkossev.commonLib, line 160
        if (advancedOptions == true || advancedOptions == true) { // library marker kkossev.commonLib, line 161
            input name: 'healthCheckMethod', type: 'enum', title: '<b>Healthcheck Method</b>', options: HealthcheckMethodOpts.options, defaultValue: HealthcheckMethodOpts.defaultValue, required: true, description: '<i>Method to check device online/offline status.</i>' // library marker kkossev.commonLib, line 162
            //if (healthCheckMethod != null && safeToInt(healthCheckMethod.value) != 0) { // library marker kkossev.commonLib, line 163
                input name: 'healthCheckInterval', type: 'enum', title: '<b>Healthcheck Interval</b>', options: HealthcheckIntervalOpts.options, defaultValue: HealthcheckIntervalOpts.defaultValue, required: true, description: '<i>How often the hub will check the device health.<br>3 consecutive failures will result in status "offline"</i>' // library marker kkossev.commonLib, line 164
            //} // library marker kkossev.commonLib, line 165
            if (device.hasCapability("Battery")) { // library marker kkossev.commonLib, line 166
                input name: 'voltageToPercent', type: 'bool', title: '<b>Battery Voltage to Percentage</b>', defaultValue: false, description: '<i>Convert battery voltage to battery Percentage remaining.</i>' // library marker kkossev.commonLib, line 167

            } // library marker kkossev.commonLib, line 169
            if ((deviceType in  ["Switch", "Plug", "Dimmer"]) && _THREE_STATE == true) { // library marker kkossev.commonLib, line 170
                input name: 'threeStateEnable', type: 'bool', title: '<b>Enable three-states events</b>', description: '<i>What\'s wrong with the three-state concept?</i>', defaultValue: false // library marker kkossev.commonLib, line 171
            } // library marker kkossev.commonLib, line 172
            input name: 'traceEnable', type: 'bool', title: '<b>Enable trace logging</b>', defaultValue: false, description: '<i>Turns on detailed extra trace logging for 30 minutes.</i>' // library marker kkossev.commonLib, line 173
        } // library marker kkossev.commonLib, line 174
    } // library marker kkossev.commonLib, line 175

} // library marker kkossev.commonLib, line 177


@Field static final Integer DIGITAL_TIMER = 1000             // command was sent by this driver // library marker kkossev.commonLib, line 180
@Field static final Integer REFRESH_TIMER = 5000             // refresh time in miliseconds // library marker kkossev.commonLib, line 181
@Field static final Integer DEBOUNCING_TIMER = 300           // ignore switch events  // library marker kkossev.commonLib, line 182
@Field static final Integer COMMAND_TIMEOUT = 10             // timeout time in seconds // library marker kkossev.commonLib, line 183
@Field static final Integer MAX_PING_MILISECONDS = 10000     // rtt more than 10 seconds will be ignored // library marker kkossev.commonLib, line 184
@Field static final String  UNKNOWN = "UNKNOWN" // library marker kkossev.commonLib, line 185
@Field static final Integer DEFAULT_MIN_REPORTING_TIME = 10  // send the report event no more often than 10 seconds by default // library marker kkossev.commonLib, line 186
@Field static final Integer DEFAULT_MAX_REPORTING_TIME = 3600 // library marker kkossev.commonLib, line 187
@Field static final Integer PRESENCE_COUNT_THRESHOLD = 3     // missing 3 checks will set the device healthStatus to offline // library marker kkossev.commonLib, line 188
@Field static final int DELAY_MS = 200                       // Delay in between zigbee commands // library marker kkossev.commonLib, line 189
@Field static final Integer DEFAULT_ILLUMINANCE_THRESHOLD = 5 // library marker kkossev.commonLib, line 190
@Field static final Integer INFO_AUTO_CLEAR_PERIOD = 30      // automatically clear the Info attribute after 30 seconds // library marker kkossev.commonLib, line 191

@Field static final Map HealthcheckMethodOpts = [            // used by healthCheckMethod // library marker kkossev.commonLib, line 193
    defaultValue: 1, // library marker kkossev.commonLib, line 194
    options     : [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 195
] // library marker kkossev.commonLib, line 196
@Field static final Map HealthcheckIntervalOpts = [          // used by healthCheckInterval // library marker kkossev.commonLib, line 197
    defaultValue: 240, // library marker kkossev.commonLib, line 198
    options     : [10: 'Every 10 Mins', 30: 'Every 30 Mins', 60: 'Every 1 Hour', 240: 'Every 4 Hours', 720: 'Every 12 Hours'] // library marker kkossev.commonLib, line 199
] // library marker kkossev.commonLib, line 200
@Field static final Map SwitchThreeStateOpts = [ // library marker kkossev.commonLib, line 201
    defaultValue: 0, // library marker kkossev.commonLib, line 202
    options     : [0: 'off', 1: 'on', 2: 'switching_off', 3: 'switching_on', 4: 'switch_failure'] // library marker kkossev.commonLib, line 203
] // library marker kkossev.commonLib, line 204

@Field static final Map ZigbeeGroupsOptsDebug = [ // library marker kkossev.commonLib, line 206
    defaultValue: 0, // library marker kkossev.commonLib, line 207
    options     : [99: '--- select ---', 0: 'Add group', 1: 'View group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups', 5: 'Add group if identifying'] // library marker kkossev.commonLib, line 208
] // library marker kkossev.commonLib, line 209
@Field static final Map ZigbeeGroupsOpts = [ // library marker kkossev.commonLib, line 210
    defaultValue: 0, // library marker kkossev.commonLib, line 211
    options     : [99: '--- select ---', 0: 'Add group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups'] // library marker kkossev.commonLib, line 212
] // library marker kkossev.commonLib, line 213

@Field static final Map ConfigureOpts = [ // library marker kkossev.commonLib, line 215
    "Configure the device only"  : [key:2, function: 'configure'], // library marker kkossev.commonLib, line 216
    "Reset Statistics"           : [key:9, function: 'resetStatistics'], // library marker kkossev.commonLib, line 217
    "           --            "  : [key:3, function: 'configureHelp'], // library marker kkossev.commonLib, line 218
    "Delete All Preferences"     : [key:4, function: 'deleteAllSettings'], // library marker kkossev.commonLib, line 219
    "Delete All Current States"  : [key:5, function: 'deleteAllCurrentStates'], // library marker kkossev.commonLib, line 220
    "Delete All Scheduled Jobs"  : [key:6, function: 'deleteAllScheduledJobs'], // library marker kkossev.commonLib, line 221
    "Delete All State Variables" : [key:7, function: 'deleteAllStates'], // library marker kkossev.commonLib, line 222
    "Delete All Child Devices"   : [key:8, function: 'deleteAllChildDevices'], // library marker kkossev.commonLib, line 223
    "           -             "  : [key:1, function: 'configureHelp'], // library marker kkossev.commonLib, line 224
    "*** LOAD ALL DEFAULTS ***"  : [key:0, function: 'loadAllDefaults'] // library marker kkossev.commonLib, line 225
] // library marker kkossev.commonLib, line 226


def isChattyDeviceReport(description)  {return false /*(description?.contains("cluster: FC7E")) */} // library marker kkossev.commonLib, line 229
def isVINDSTYRKA() { (device?.getDataValue('model') ?: 'n/a') in ['VINDSTYRKA'] } // library marker kkossev.commonLib, line 230
def isAqaraTVOC()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airmonitor.acn01'] } // library marker kkossev.commonLib, line 231
def isAqaraTRV()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.airrtc.agl001'] } // library marker kkossev.commonLib, line 232
def isAqaraFP1()   { (device?.getDataValue('model') ?: 'n/a') in ['lumi.motion.ac01'] } // library marker kkossev.commonLib, line 233
def isFingerbot()  { (device?.getDataValue('manufacturer') ?: 'n/a') in ['_TZ3210_dse8ogfy'] } // library marker kkossev.commonLib, line 234
def isAqaraCube()  { (device?.getDataValue('model') ?: 'n/a') in ['lumi.remote.cagl02'] } // library marker kkossev.commonLib, line 235

/** // library marker kkossev.commonLib, line 237
 * Parse Zigbee message // library marker kkossev.commonLib, line 238
 * @param description Zigbee message in hex format // library marker kkossev.commonLib, line 239
 */ // library marker kkossev.commonLib, line 240
void parse(final String description) { // library marker kkossev.commonLib, line 241
    checkDriverVersion() // library marker kkossev.commonLib, line 242
    if (!isChattyDeviceReport(description)) { logDebug "parse: ${description}" } // library marker kkossev.commonLib, line 243
    if (state.stats != null) state.stats['rxCtr'] = (state.stats['rxCtr'] ?: 0) + 1 else state.stats=[:] // library marker kkossev.commonLib, line 244
    unschedule('deviceCommandTimeout') // library marker kkossev.commonLib, line 245
    setHealthStatusOnline() // library marker kkossev.commonLib, line 246

    if (description?.startsWith('zone status')  || description?.startsWith('zone report')) {     // library marker kkossev.commonLib, line 248
        logDebug "parse: zone status: $description" // library marker kkossev.commonLib, line 249
        if (true /*isHL0SS9OAradar() && _IGNORE_ZCL_REPORTS == true*/) {    // TODO! // library marker kkossev.commonLib, line 250
            logDebug "ignored IAS zone status" // library marker kkossev.commonLib, line 251
            return // library marker kkossev.commonLib, line 252
        } // library marker kkossev.commonLib, line 253
        else { // library marker kkossev.commonLib, line 254
            parseIasMessage(description)    // TODO! // library marker kkossev.commonLib, line 255
        } // library marker kkossev.commonLib, line 256
    } // library marker kkossev.commonLib, line 257
    else if (description?.startsWith('enroll request')) { // library marker kkossev.commonLib, line 258
        logDebug "parse: enroll request: $description" // library marker kkossev.commonLib, line 259
        /* The Zone Enroll Request command is generated when a device embodying the Zone server cluster wishes to be  enrolled as an active  alarm device. It  must do this immediately it has joined the network  (during commissioning). */ // library marker kkossev.commonLib, line 260
        if (settings?.logEnable) logInfo "Sending IAS enroll response..." // library marker kkossev.commonLib, line 261
        ArrayList<String> cmds = zigbee.enrollResponse() + zigbee.readAttribute(0x0500, 0x0000) // library marker kkossev.commonLib, line 262
        logDebug "enroll response: ${cmds}" // library marker kkossev.commonLib, line 263
        sendZigbeeCommands( cmds )   // library marker kkossev.commonLib, line 264
    }  // library marker kkossev.commonLib, line 265
    if (isTuyaE00xCluster(description) == true || otherTuyaOddities(description) == true) { // library marker kkossev.commonLib, line 266
        return // library marker kkossev.commonLib, line 267
    }         // library marker kkossev.commonLib, line 268
    final Map descMap = myParseDescriptionAsMap(description) // library marker kkossev.commonLib, line 269

    if (descMap.profileId == '0000') { // library marker kkossev.commonLib, line 271
        parseZdoClusters(descMap) // library marker kkossev.commonLib, line 272
        return // library marker kkossev.commonLib, line 273
    } // library marker kkossev.commonLib, line 274
    if (descMap.isClusterSpecific == false) { // library marker kkossev.commonLib, line 275
        parseGeneralCommandResponse(descMap) // library marker kkossev.commonLib, line 276
        return // library marker kkossev.commonLib, line 277
    } // library marker kkossev.commonLib, line 278
    if (!isChattyDeviceReport(description)) {logDebug "parse: descMap = ${descMap} description=${description}"} // library marker kkossev.commonLib, line 279
    // // library marker kkossev.commonLib, line 280
    final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 281
    final String attribute = descMap.attrId ? " attribute 0x${descMap.attrId} (value ${descMap.value})" : '' // library marker kkossev.commonLib, line 282
    //if (settings.logEnable) { log.trace "zigbee received ${clusterName} message" + attribute } // library marker kkossev.commonLib, line 283

    switch (descMap.clusterInt as Integer) { // library marker kkossev.commonLib, line 285
        case zigbee.BASIC_CLUSTER:                          // 0x0000 // library marker kkossev.commonLib, line 286
            parseBasicCluster(descMap) // library marker kkossev.commonLib, line 287
            descMap.remove('additionalAttrs')?.each { final Map map -> parseBasicCluster(descMap + map) } // library marker kkossev.commonLib, line 288
            break // library marker kkossev.commonLib, line 289
        case zigbee.POWER_CONFIGURATION_CLUSTER:            // 0x0001 // library marker kkossev.commonLib, line 290
            parsePowerCluster(descMap) // library marker kkossev.commonLib, line 291
            descMap.remove('additionalAttrs')?.each { final Map map -> parsePowerCluster(descMap + map) } // library marker kkossev.commonLib, line 292
            break // library marker kkossev.commonLib, line 293
        case zigbee.IDENTIFY_CLUSTER:                      // 0x0003 // library marker kkossev.commonLib, line 294
            parseIdentityCluster(descMap) // library marker kkossev.commonLib, line 295
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIdentityCluster(descMap + map) } // library marker kkossev.commonLib, line 296
            break // library marker kkossev.commonLib, line 297
        case zigbee.GROUPS_CLUSTER:                        // 0x0004 // library marker kkossev.commonLib, line 298
            parseGroupsCluster(descMap) // library marker kkossev.commonLib, line 299
            descMap.remove('additionalAttrs')?.each {final Map map -> parseGroupsCluster(descMap + map) } // library marker kkossev.commonLib, line 300
            break // library marker kkossev.commonLib, line 301
        case zigbee.SCENES_CLUSTER:                         // 0x0005 // library marker kkossev.commonLib, line 302
            parseScenesCluster(descMap) // library marker kkossev.commonLib, line 303
            descMap.remove('additionalAttrs')?.each {final Map map -> parseScenesCluster(descMap + map) } // library marker kkossev.commonLib, line 304
            break // library marker kkossev.commonLib, line 305
        case zigbee.ON_OFF_CLUSTER:                         // 0x0006 // library marker kkossev.commonLib, line 306
            parseOnOffCluster(descMap) // library marker kkossev.commonLib, line 307
            descMap.remove('additionalAttrs')?.each { final Map map -> parseOnOffCluster(descMap + map) } // library marker kkossev.commonLib, line 308
            break // library marker kkossev.commonLib, line 309
        case zigbee.LEVEL_CONTROL_CLUSTER:                  // 0x0008 // library marker kkossev.commonLib, line 310
            parseLevelControlCluster(descMap) // library marker kkossev.commonLib, line 311
            descMap.remove('additionalAttrs')?.each { final Map map -> parseLevelControlCluster(descMap + map) } // library marker kkossev.commonLib, line 312
            break // library marker kkossev.commonLib, line 313
        case 0x000C :                                       // Aqara TVOC Air Monitor; Aqara Cube T1 Pro // library marker kkossev.commonLib, line 314
            parseAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 315
            descMap.remove('additionalAttrs')?.each { final Map map -> parseAnalogInputCluster(descMap + map) } // library marker kkossev.commonLib, line 316
            break // library marker kkossev.commonLib, line 317
        case 0x0012 :                                       // Aqara Cube - Multistate Input // library marker kkossev.commonLib, line 318
            parseMultistateInputCluster(descMap) // library marker kkossev.commonLib, line 319
            break // library marker kkossev.commonLib, line 320
         case 0x0102 :                                      // window covering  // library marker kkossev.commonLib, line 321
            parseWindowCoveringCluster(descMap) // library marker kkossev.commonLib, line 322
            break        // library marker kkossev.commonLib, line 323
        case 0x0201 :                                       // Aqara E1 TRV  // library marker kkossev.commonLib, line 324
            parseThermostatCluster(descMap) // library marker kkossev.commonLib, line 325
            descMap.remove('additionalAttrs')?.each { final Map map -> parseThermostatCluster(descMap + map) } // library marker kkossev.commonLib, line 326
            break // library marker kkossev.commonLib, line 327
        case 0x0300 :                                       // Aqara LED Strip T1 // library marker kkossev.commonLib, line 328
            parseColorControlCluster(descMap, description) // library marker kkossev.commonLib, line 329
            descMap.remove('additionalAttrs')?.each { final Map map -> parseColorControlCluster(descMap + map, description) } // library marker kkossev.commonLib, line 330
            break // library marker kkossev.commonLib, line 331
        case zigbee.ILLUMINANCE_MEASUREMENT_CLUSTER :       //0x0400 // library marker kkossev.commonLib, line 332
            parseIlluminanceCluster(descMap) // library marker kkossev.commonLib, line 333
            descMap.remove('additionalAttrs')?.each { final Map map -> parseIlluminanceCluster(descMap + map) } // library marker kkossev.commonLib, line 334
            break // library marker kkossev.commonLib, line 335
        case zigbee.TEMPERATURE_MEASUREMENT_CLUSTER :       //0x0402 // library marker kkossev.commonLib, line 336
            parseTemperatureCluster(descMap) // library marker kkossev.commonLib, line 337
            break // library marker kkossev.commonLib, line 338
        case zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER : //0x0405 // library marker kkossev.commonLib, line 339
            parseHumidityCluster(descMap) // library marker kkossev.commonLib, line 340
            break // library marker kkossev.commonLib, line 341
        case 0x042A :                                       // pm2.5 // library marker kkossev.commonLib, line 342
            parsePm25Cluster(descMap) // library marker kkossev.commonLib, line 343
            break // library marker kkossev.commonLib, line 344
        case zigbee.ELECTRICAL_MEASUREMENT_CLUSTER: // library marker kkossev.commonLib, line 345
            parseElectricalMeasureCluster(descMap) // library marker kkossev.commonLib, line 346
            descMap.remove('additionalAttrs')?.each { final Map map -> parseElectricalMeasureCluster(descMap + map) } // library marker kkossev.commonLib, line 347
            break // library marker kkossev.commonLib, line 348
        case zigbee.METERING_CLUSTER: // library marker kkossev.commonLib, line 349
            parseMeteringCluster(descMap) // library marker kkossev.commonLib, line 350
            descMap.remove('additionalAttrs')?.each { final Map map -> parseMeteringCluster(descMap + map) } // library marker kkossev.commonLib, line 351
            break // library marker kkossev.commonLib, line 352
        case 0xE002 : // library marker kkossev.commonLib, line 353
            parseE002Cluster(descMap) // library marker kkossev.commonLib, line 354
            descMap.remove('additionalAttrs')?.each { final Map map -> parseE002Cluster(descMap + map) } // library marker kkossev.commonLib, line 355
            break // library marker kkossev.commonLib, line 356
        case 0xEF00 :                                       // Tuya famous cluster // library marker kkossev.commonLib, line 357
            parseTuyaCluster(descMap) // library marker kkossev.commonLib, line 358
            descMap.remove('additionalAttrs')?.each { final Map map -> parseTuyaCluster(descMap + map) } // library marker kkossev.commonLib, line 359
            break // library marker kkossev.commonLib, line 360
        case 0xfc7e :                                       // tVOC 'Sensirion VOC index' https://sensirion.com/media/documents/02232963/6294E043/Info_Note_VOC_Index.pdf // library marker kkossev.commonLib, line 361
            parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 362
            break // library marker kkossev.commonLib, line 363
        case 0xFCC0 :                                       // XIAOMI_CLUSTER_ID Xiaomi cluster // library marker kkossev.commonLib, line 364
            parseXiaomiCluster(descMap) // library marker kkossev.commonLib, line 365
            descMap.remove('additionalAttrs')?.each { final Map m -> parseXiaomiCluster(descMap + m) } // library marker kkossev.commonLib, line 366
            break // library marker kkossev.commonLib, line 367
        default: // library marker kkossev.commonLib, line 368
            if (settings.logEnable) { // library marker kkossev.commonLib, line 369
                logWarn "zigbee received <b>unknown cluster:${descMap.clusterId}</b> message (${descMap})" // library marker kkossev.commonLib, line 370
            } // library marker kkossev.commonLib, line 371
            break // library marker kkossev.commonLib, line 372
    } // library marker kkossev.commonLib, line 373

} // library marker kkossev.commonLib, line 375

/** // library marker kkossev.commonLib, line 377
 * ZDO (Zigbee Data Object) Clusters Parsing // library marker kkossev.commonLib, line 378
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 379
 */ // library marker kkossev.commonLib, line 380
void parseZdoClusters(final Map descMap) { // library marker kkossev.commonLib, line 381
    final Integer clusterId = descMap.clusterInt as Integer // library marker kkossev.commonLib, line 382
    final String clusterName = ZdoClusterEnum[clusterId] ?: "UNKNOWN_CLUSTER (0x${descMap.clusterId})" // library marker kkossev.commonLib, line 383
    final String statusHex = ((List)descMap.data)[1] // library marker kkossev.commonLib, line 384
    final Integer statusCode = hexStrToUnsignedInt(statusHex) // library marker kkossev.commonLib, line 385
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${statusHex}" // library marker kkossev.commonLib, line 386
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 387
        logWarn "parseZdoClusters: ZDO ${clusterName} error: ${statusName} (statusCode: 0x${statusHex})" // library marker kkossev.commonLib, line 388
    }  // library marker kkossev.commonLib, line 389
    else { // library marker kkossev.commonLib, line 390
        logDebug "parseZdoClusters: ZDO ${clusterName} success: ${descMap.data}" // library marker kkossev.commonLib, line 391
    } // library marker kkossev.commonLib, line 392
} // library marker kkossev.commonLib, line 393

/** // library marker kkossev.commonLib, line 395
 * Zigbee General Command Parsing // library marker kkossev.commonLib, line 396
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 397
 */ // library marker kkossev.commonLib, line 398
void parseGeneralCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 399
    final int commandId = hexStrToUnsignedInt(descMap.command) // library marker kkossev.commonLib, line 400
    switch (commandId) { // library marker kkossev.commonLib, line 401
        case 0x01: // read attribute response // library marker kkossev.commonLib, line 402
            parseReadAttributeResponse(descMap) // library marker kkossev.commonLib, line 403
            break // library marker kkossev.commonLib, line 404
        case 0x04: // write attribute response // library marker kkossev.commonLib, line 405
            parseWriteAttributeResponse(descMap) // library marker kkossev.commonLib, line 406
            break // library marker kkossev.commonLib, line 407
        case 0x07: // configure reporting response // library marker kkossev.commonLib, line 408
            parseConfigureResponse(descMap) // library marker kkossev.commonLib, line 409
            break // library marker kkossev.commonLib, line 410
        case 0x09: // read reporting configuration response // library marker kkossev.commonLib, line 411
            parseReadReportingConfigResponse(descMap) // library marker kkossev.commonLib, line 412
            break // library marker kkossev.commonLib, line 413
        case 0x0B: // default command response // library marker kkossev.commonLib, line 414
            parseDefaultCommandResponse(descMap) // library marker kkossev.commonLib, line 415
            break // library marker kkossev.commonLib, line 416
        default: // library marker kkossev.commonLib, line 417
            final String commandName = ZigbeeGeneralCommandEnum[commandId] ?: "UNKNOWN_COMMAND (0x${descMap.command})" // library marker kkossev.commonLib, line 418
            final String clusterName = clusterLookup(descMap.clusterInt) // library marker kkossev.commonLib, line 419
            final String status = descMap.data in List ? ((List)descMap.data).last() : descMap.data // library marker kkossev.commonLib, line 420
            final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 421
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 422
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 423
                log.warn "zigbee ${commandName} ${clusterName} error: ${statusName}" // library marker kkossev.commonLib, line 424
            } else if (settings.logEnable) { // library marker kkossev.commonLib, line 425
                log.trace "zigbee ${commandName} ${clusterName}: ${descMap.data}" // library marker kkossev.commonLib, line 426
            } // library marker kkossev.commonLib, line 427
            break // library marker kkossev.commonLib, line 428
    } // library marker kkossev.commonLib, line 429
} // library marker kkossev.commonLib, line 430

/** // library marker kkossev.commonLib, line 432
 * Zigbee Read Attribute Response Parsing // library marker kkossev.commonLib, line 433
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 434
 */ // library marker kkossev.commonLib, line 435
void parseReadAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 436
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 437
    final String attribute = data[1] + data[0] // library marker kkossev.commonLib, line 438
    final int statusCode = hexStrToUnsignedInt(data[2]) // library marker kkossev.commonLib, line 439
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 440
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 441
        logWarn "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} error: ${status}" // library marker kkossev.commonLib, line 442
    } // library marker kkossev.commonLib, line 443
    else { // library marker kkossev.commonLib, line 444
        logDebug "zigbee read ${clusterLookup(descMap.clusterInt)} attribute 0x${attribute} response: ${status} ${data}" // library marker kkossev.commonLib, line 445
    } // library marker kkossev.commonLib, line 446
} // library marker kkossev.commonLib, line 447

/** // library marker kkossev.commonLib, line 449
 * Zigbee Write Attribute Response Parsing // library marker kkossev.commonLib, line 450
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 451
 */ // library marker kkossev.commonLib, line 452
void parseWriteAttributeResponse(final Map descMap) { // library marker kkossev.commonLib, line 453
    final String data = descMap.data in List ? ((List)descMap.data).first() : descMap.data // library marker kkossev.commonLib, line 454
    final int statusCode = hexStrToUnsignedInt(data) // library marker kkossev.commonLib, line 455
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data}" // library marker kkossev.commonLib, line 456
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 457
        logWarn "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute error: ${statusName}" // library marker kkossev.commonLib, line 458
    } // library marker kkossev.commonLib, line 459
    else { // library marker kkossev.commonLib, line 460
        logDebug "zigbee response write ${clusterLookup(descMap.clusterInt)} attribute response: ${statusName}" // library marker kkossev.commonLib, line 461
    } // library marker kkossev.commonLib, line 462
} // library marker kkossev.commonLib, line 463

/** // library marker kkossev.commonLib, line 465
 * Zigbee Configure Reporting Response Parsing // library marker kkossev.commonLib, line 466
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 467
 */ // library marker kkossev.commonLib, line 468

void parseConfigureResponse(final Map descMap) { // library marker kkossev.commonLib, line 470
    // TODO - parse the details of the configuration respose - cluster, min, max, delta ... // library marker kkossev.commonLib, line 471
    final String status = ((List)descMap.data).first() // library marker kkossev.commonLib, line 472
    final int statusCode = hexStrToUnsignedInt(status) // library marker kkossev.commonLib, line 473
    if (statusCode == 0x00 && settings.enableReporting != false) { // library marker kkossev.commonLib, line 474
        state.reportingEnabled = true // library marker kkossev.commonLib, line 475
    } // library marker kkossev.commonLib, line 476
    final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${status}" // library marker kkossev.commonLib, line 477
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 478
        log.warn "zigbee configure reporting error: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 479
    } else { // library marker kkossev.commonLib, line 480
        logDebug "zigbee configure reporting response: ${statusName} ${descMap.data}" // library marker kkossev.commonLib, line 481
    } // library marker kkossev.commonLib, line 482
} // library marker kkossev.commonLib, line 483

/** // library marker kkossev.commonLib, line 485
 * Zigbee Default Command Response Parsing // library marker kkossev.commonLib, line 486
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 487
 */ // library marker kkossev.commonLib, line 488
void parseDefaultCommandResponse(final Map descMap) { // library marker kkossev.commonLib, line 489
    final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 490
    final String commandId = data[0] // library marker kkossev.commonLib, line 491
    final int statusCode = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 492
    final String status = ZigbeeStatusEnum[statusCode] ?: "0x${data[1]}" // library marker kkossev.commonLib, line 493
    if (statusCode > 0x00) { // library marker kkossev.commonLib, line 494
        logWarn "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} error: ${status}" // library marker kkossev.commonLib, line 495
    } else { // library marker kkossev.commonLib, line 496
        logDebug "zigbee ${clusterLookup(descMap.clusterInt)} command 0x${commandId} response: ${status}" // library marker kkossev.commonLib, line 497
    } // library marker kkossev.commonLib, line 498
} // library marker kkossev.commonLib, line 499


// Zigbee Attribute IDs // library marker kkossev.commonLib, line 502
@Field static final int AC_CURRENT_DIVISOR_ID = 0x0603 // library marker kkossev.commonLib, line 503
@Field static final int AC_CURRENT_MULTIPLIER_ID = 0x0602 // library marker kkossev.commonLib, line 504
@Field static final int AC_FREQUENCY_ID = 0x0300 // library marker kkossev.commonLib, line 505
@Field static final int AC_POWER_DIVISOR_ID = 0x0605 // library marker kkossev.commonLib, line 506
@Field static final int AC_POWER_MULTIPLIER_ID = 0x0604 // library marker kkossev.commonLib, line 507
@Field static final int AC_VOLTAGE_DIVISOR_ID = 0x0601 // library marker kkossev.commonLib, line 508
@Field static final int AC_VOLTAGE_MULTIPLIER_ID = 0x0600 // library marker kkossev.commonLib, line 509
@Field static final int ACTIVE_POWER_ID = 0x050B // library marker kkossev.commonLib, line 510
@Field static final int ATTRIBUTE_READING_INFO_SET = 0x0000 // library marker kkossev.commonLib, line 511
@Field static final int FIRMWARE_VERSION_ID = 0x4000 // library marker kkossev.commonLib, line 512
@Field static final int PING_ATTR_ID = 0x01 // library marker kkossev.commonLib, line 513
@Field static final int POWER_ON_OFF_ID = 0x0000 // library marker kkossev.commonLib, line 514
@Field static final int POWER_RESTORE_ID = 0x4003 // library marker kkossev.commonLib, line 515
@Field static final int RMS_CURRENT_ID = 0x0508 // library marker kkossev.commonLib, line 516
@Field static final int RMS_VOLTAGE_ID = 0x0505 // library marker kkossev.commonLib, line 517

@Field static final Map<Integer, String> ZigbeeStatusEnum = [ // library marker kkossev.commonLib, line 519
    0x00: 'Success', // library marker kkossev.commonLib, line 520
    0x01: 'Failure', // library marker kkossev.commonLib, line 521
    0x02: 'Not Authorized', // library marker kkossev.commonLib, line 522
    0x80: 'Malformed Command', // library marker kkossev.commonLib, line 523
    0x81: 'Unsupported COMMAND', // library marker kkossev.commonLib, line 524
    0x85: 'Invalid Field', // library marker kkossev.commonLib, line 525
    0x86: 'Unsupported Attribute', // library marker kkossev.commonLib, line 526
    0x87: 'Invalid Value', // library marker kkossev.commonLib, line 527
    0x88: 'Read Only', // library marker kkossev.commonLib, line 528
    0x89: 'Insufficient Space', // library marker kkossev.commonLib, line 529
    0x8A: 'Duplicate Exists', // library marker kkossev.commonLib, line 530
    0x8B: 'Not Found', // library marker kkossev.commonLib, line 531
    0x8C: 'Unreportable Attribute', // library marker kkossev.commonLib, line 532
    0x8D: 'Invalid Data Type', // library marker kkossev.commonLib, line 533
    0x8E: 'Invalid Selector', // library marker kkossev.commonLib, line 534
    0x94: 'Time out', // library marker kkossev.commonLib, line 535
    0x9A: 'Notification Pending', // library marker kkossev.commonLib, line 536
    0xC3: 'Unsupported Cluster' // library marker kkossev.commonLib, line 537
] // library marker kkossev.commonLib, line 538

@Field static final Map<Integer, String> ZdoClusterEnum = [ // library marker kkossev.commonLib, line 540
    0x0002: 'Node Descriptor Request', // library marker kkossev.commonLib, line 541
    0x0005: 'Active Endpoints Request', // library marker kkossev.commonLib, line 542
    0x0006: 'Match Descriptor Request', // library marker kkossev.commonLib, line 543
    0x0022: 'Unbind Request', // library marker kkossev.commonLib, line 544
    0x0013: 'Device announce', // library marker kkossev.commonLib, line 545
    0x0034: 'Management Leave Request', // library marker kkossev.commonLib, line 546
    0x8002: 'Node Descriptor Response', // library marker kkossev.commonLib, line 547
    0x8004: 'Simple Descriptor Response', // library marker kkossev.commonLib, line 548
    0x8005: 'Active Endpoints Response', // library marker kkossev.commonLib, line 549
    0x801D: 'Extended Simple Descriptor Response', // library marker kkossev.commonLib, line 550
    0x801E: 'Extended Active Endpoint Response', // library marker kkossev.commonLib, line 551
    0x8021: 'Bind Response', // library marker kkossev.commonLib, line 552
    0x8022: 'Unbind Response', // library marker kkossev.commonLib, line 553
    0x8023: 'Bind Register Response', // library marker kkossev.commonLib, line 554
    0x8034: 'Management Leave Response' // library marker kkossev.commonLib, line 555
] // library marker kkossev.commonLib, line 556

@Field static final Map<Integer, String> ZigbeeGeneralCommandEnum = [ // library marker kkossev.commonLib, line 558
    0x00: 'Read Attributes', // library marker kkossev.commonLib, line 559
    0x01: 'Read Attributes Response', // library marker kkossev.commonLib, line 560
    0x02: 'Write Attributes', // library marker kkossev.commonLib, line 561
    0x03: 'Write Attributes Undivided', // library marker kkossev.commonLib, line 562
    0x04: 'Write Attributes Response', // library marker kkossev.commonLib, line 563
    0x05: 'Write Attributes No Response', // library marker kkossev.commonLib, line 564
    0x06: 'Configure Reporting', // library marker kkossev.commonLib, line 565
    0x07: 'Configure Reporting Response', // library marker kkossev.commonLib, line 566
    0x08: 'Read Reporting Configuration', // library marker kkossev.commonLib, line 567
    0x09: 'Read Reporting Configuration Response', // library marker kkossev.commonLib, line 568
    0x0A: 'Report Attributes', // library marker kkossev.commonLib, line 569
    0x0B: 'Default Response', // library marker kkossev.commonLib, line 570
    0x0C: 'Discover Attributes', // library marker kkossev.commonLib, line 571
    0x0D: 'Discover Attributes Response', // library marker kkossev.commonLib, line 572
    0x0E: 'Read Attributes Structured', // library marker kkossev.commonLib, line 573
    0x0F: 'Write Attributes Structured', // library marker kkossev.commonLib, line 574
    0x10: 'Write Attributes Structured Response', // library marker kkossev.commonLib, line 575
    0x11: 'Discover Commands Received', // library marker kkossev.commonLib, line 576
    0x12: 'Discover Commands Received Response', // library marker kkossev.commonLib, line 577
    0x13: 'Discover Commands Generated', // library marker kkossev.commonLib, line 578
    0x14: 'Discover Commands Generated Response', // library marker kkossev.commonLib, line 579
    0x15: 'Discover Attributes Extended', // library marker kkossev.commonLib, line 580
    0x16: 'Discover Attributes Extended Response' // library marker kkossev.commonLib, line 581
] // library marker kkossev.commonLib, line 582


/* // library marker kkossev.commonLib, line 585
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 586
 * Xiaomi cluster 0xFCC0 parser. // library marker kkossev.commonLib, line 587
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 588
 */ // library marker kkossev.commonLib, line 589
void parseXiaomiCluster(final Map descMap) { // library marker kkossev.commonLib, line 590
    if (xiaomiLibVersion() != null) { // library marker kkossev.commonLib, line 591
        parseXiaomiClusterLib(descMap) // library marker kkossev.commonLib, line 592
    }     // library marker kkossev.commonLib, line 593
    else { // library marker kkossev.commonLib, line 594
        logWarn "Xiaomi cluster 0xFCC0" // library marker kkossev.commonLib, line 595
    } // library marker kkossev.commonLib, line 596
} // library marker kkossev.commonLib, line 597


/* // library marker kkossev.commonLib, line 600
@Field static final int XIAOMI_CLUSTER_ID = 0xFCC0 // library marker kkossev.commonLib, line 601

// Zigbee Attributes // library marker kkossev.commonLib, line 603
@Field static final int DIRECTION_MODE_ATTR_ID = 0x0144 // library marker kkossev.commonLib, line 604
@Field static final int MODEL_ATTR_ID = 0x05 // library marker kkossev.commonLib, line 605
@Field static final int PRESENCE_ACTIONS_ATTR_ID = 0x0143 // library marker kkossev.commonLib, line 606
@Field static final int PRESENCE_ATTR_ID = 0x0142 // library marker kkossev.commonLib, line 607
@Field static final int REGION_EVENT_ATTR_ID = 0x0151 // library marker kkossev.commonLib, line 608
@Field static final int RESET_PRESENCE_ATTR_ID = 0x0157 // library marker kkossev.commonLib, line 609
@Field static final int SENSITIVITY_LEVEL_ATTR_ID = 0x010C // library marker kkossev.commonLib, line 610
@Field static final int SET_EDGE_REGION_ATTR_ID = 0x0156 // library marker kkossev.commonLib, line 611
@Field static final int SET_EXIT_REGION_ATTR_ID = 0x0153 // library marker kkossev.commonLib, line 612
@Field static final int SET_INTERFERENCE_ATTR_ID = 0x0154 // library marker kkossev.commonLib, line 613
@Field static final int SET_REGION_ATTR_ID = 0x0150 // library marker kkossev.commonLib, line 614
@Field static final int TRIGGER_DISTANCE_ATTR_ID = 0x0146 // library marker kkossev.commonLib, line 615
@Field static final int XIAOMI_RAW_ATTR_ID = 0xFFF2 // library marker kkossev.commonLib, line 616
@Field static final int XIAOMI_SPECIAL_REPORT_ID = 0x00F7 // library marker kkossev.commonLib, line 617
@Field static final Map MFG_CODE = [ mfgCode: 0x115F ] // library marker kkossev.commonLib, line 618

// Xiaomi Tags // library marker kkossev.commonLib, line 620
@Field static final int DIRECTION_MODE_TAG_ID = 0x67 // library marker kkossev.commonLib, line 621
@Field static final int SENSITIVITY_LEVEL_TAG_ID = 0x66 // library marker kkossev.commonLib, line 622
@Field static final int SWBUILD_TAG_ID = 0x08 // library marker kkossev.commonLib, line 623
@Field static final int TRIGGER_DISTANCE_TAG_ID = 0x69 // library marker kkossev.commonLib, line 624
@Field static final int PRESENCE_ACTIONS_TAG_ID = 0x66 // library marker kkossev.commonLib, line 625
@Field static final int PRESENCE_TAG_ID = 0x65 // library marker kkossev.commonLib, line 626
*/ // library marker kkossev.commonLib, line 627


// TODO - move to xiaomiLib // library marker kkossev.commonLib, line 630
// TODO - move to thermostatLib // library marker kkossev.commonLib, line 631
// TODO - move to aqaraQubeLib // library marker kkossev.commonLib, line 632




@Field static final int ROLLING_AVERAGE_N = 10 // library marker kkossev.commonLib, line 637
double approxRollingAverage (double avg, double new_sample) { // library marker kkossev.commonLib, line 638
    if (avg == null || avg == 0) { avg = new_sample} // library marker kkossev.commonLib, line 639
    avg -= avg / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 640
    avg += new_sample / ROLLING_AVERAGE_N // library marker kkossev.commonLib, line 641
    // TOSO: try Method II : New average = old average * (n-1)/n + new value /n // library marker kkossev.commonLib, line 642
    return avg // library marker kkossev.commonLib, line 643
} // library marker kkossev.commonLib, line 644

/* // library marker kkossev.commonLib, line 646
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 647
 * Standard clusters reporting handlers // library marker kkossev.commonLib, line 648
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 649
*/ // library marker kkossev.commonLib, line 650
@Field static final Map powerSourceOpts =  [ defaultValue: 0, options: [0: 'unknown', 1: 'mains', 2: 'mains', 3: 'battery', 4: 'dc', 5: 'emergency mains', 6: 'emergency mains']] // library marker kkossev.commonLib, line 651

/** // library marker kkossev.commonLib, line 653
 * Zigbee Basic Cluster Parsing  0x0000 // library marker kkossev.commonLib, line 654
 * @param descMap Zigbee message in parsed map format // library marker kkossev.commonLib, line 655
 */ // library marker kkossev.commonLib, line 656
void parseBasicCluster(final Map descMap) { // library marker kkossev.commonLib, line 657
    def now = new Date().getTime() // library marker kkossev.commonLib, line 658
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 659
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 660
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 661
    if (state.stats == null) { state.stats = [:] } // library marker kkossev.commonLib, line 662
    state.lastRx["checkInTime"] = now // library marker kkossev.commonLib, line 663
    switch (descMap.attrInt as Integer) { // library marker kkossev.commonLib, line 664
        case 0x0000: // library marker kkossev.commonLib, line 665
            logDebug "Basic cluster: ZCLVersion = ${descMap?.value}" // library marker kkossev.commonLib, line 666
            break // library marker kkossev.commonLib, line 667
        case PING_ATTR_ID: // 0x01 - Using 0x01 read as a simple ping/pong mechanism // library marker kkossev.commonLib, line 668
            boolean isPing = state.states["isPing"] ?: false // library marker kkossev.commonLib, line 669
            if (isPing) { // library marker kkossev.commonLib, line 670
                def timeRunning = now.toInteger() - (state.lastTx["pingTime"] ?: '0').toInteger() // library marker kkossev.commonLib, line 671
                if (timeRunning > 0 && timeRunning < MAX_PING_MILISECONDS) { // library marker kkossev.commonLib, line 672
                    state.stats['pingsOK'] = (state.stats['pingsOK'] ?: 0) + 1 // library marker kkossev.commonLib, line 673
                    if (timeRunning < safeToInt((state.stats['pingsMin'] ?: '999'))) { state.stats['pingsMin'] = timeRunning } // library marker kkossev.commonLib, line 674
                    if (timeRunning > safeToInt((state.stats['pingsMax'] ?: '0')))   { state.stats['pingsMax'] = timeRunning } // library marker kkossev.commonLib, line 675
                    state.stats['pingsAvg'] = approxRollingAverage(safeToDouble(state.stats['pingsAvg']),safeToDouble(timeRunning)) as int // library marker kkossev.commonLib, line 676
                    sendRttEvent() // library marker kkossev.commonLib, line 677
                } // library marker kkossev.commonLib, line 678
                else { // library marker kkossev.commonLib, line 679
                    logWarn "unexpected ping timeRunning=${timeRunning} " // library marker kkossev.commonLib, line 680
                } // library marker kkossev.commonLib, line 681
                state.states["isPing"] = false // library marker kkossev.commonLib, line 682
            } // library marker kkossev.commonLib, line 683
            else { // library marker kkossev.commonLib, line 684
                logDebug "Tuya check-in message (attribute ${descMap.attrId} reported: ${descMap.value})" // library marker kkossev.commonLib, line 685
            } // library marker kkossev.commonLib, line 686
            break // library marker kkossev.commonLib, line 687
        case 0x0004: // library marker kkossev.commonLib, line 688
            logDebug "received device manufacturer ${descMap?.value}" // library marker kkossev.commonLib, line 689
            // received device manufacturer IKEA of Sweden // library marker kkossev.commonLib, line 690
            def manufacturer = device.getDataValue("manufacturer") // library marker kkossev.commonLib, line 691
            if ((manufacturer == null || manufacturer == "unknown") && (descMap?.value != null) ) { // library marker kkossev.commonLib, line 692
                logWarn "updating device manufacturer from ${manufacturer} to ${descMap?.value}" // library marker kkossev.commonLib, line 693
                device.updateDataValue("manufacturer", descMap?.value) // library marker kkossev.commonLib, line 694
            } // library marker kkossev.commonLib, line 695
            break // library marker kkossev.commonLib, line 696
        case 0x0005: // library marker kkossev.commonLib, line 697
            logDebug "received device model ${descMap?.value}" // library marker kkossev.commonLib, line 698
            // received device model Remote Control N2 // library marker kkossev.commonLib, line 699
            def model = device.getDataValue("model") // library marker kkossev.commonLib, line 700
            if ((model == null || model == "unknown") && (descMap?.value != null) ) { // library marker kkossev.commonLib, line 701
                logWarn "updating device model from ${model} to ${descMap?.value}" // library marker kkossev.commonLib, line 702
                device.updateDataValue("model", descMap?.value) // library marker kkossev.commonLib, line 703
            } // library marker kkossev.commonLib, line 704
            break // library marker kkossev.commonLib, line 705
        case 0x0007: // library marker kkossev.commonLib, line 706
            def powerSourceReported = powerSourceOpts.options[descMap?.value as int] // library marker kkossev.commonLib, line 707
            logDebug "received Power source <b>${powerSourceReported}</b> (${descMap?.value})" // library marker kkossev.commonLib, line 708
            //powerSourceEvent( powerSourceReported ) // library marker kkossev.commonLib, line 709
            break // library marker kkossev.commonLib, line 710
        case 0xFFDF: // library marker kkossev.commonLib, line 711
            logDebug "Tuya check-in (Cluster Revision=${descMap?.value})" // library marker kkossev.commonLib, line 712
            break // library marker kkossev.commonLib, line 713
        case 0xFFE2: // library marker kkossev.commonLib, line 714
            logDebug "Tuya check-in (AppVersion=${descMap?.value})" // library marker kkossev.commonLib, line 715
            break // library marker kkossev.commonLib, line 716
        case [0xFFE0, 0xFFE1, 0xFFE3, 0xFFE4] : // library marker kkossev.commonLib, line 717
            logDebug "Tuya unknown attribute ${descMap?.attrId} value=${descMap?.value}" // library marker kkossev.commonLib, line 718
            break // library marker kkossev.commonLib, line 719
        case 0xFFFE: // library marker kkossev.commonLib, line 720
            logDebug "Tuya attributeReportingStatus (attribute FFFE) value=${descMap?.value}" // library marker kkossev.commonLib, line 721
            break // library marker kkossev.commonLib, line 722
        case FIRMWARE_VERSION_ID:    // 0x4000 // library marker kkossev.commonLib, line 723
            final String version = descMap.value ?: 'unknown' // library marker kkossev.commonLib, line 724
            log.info "device firmware version is ${version}" // library marker kkossev.commonLib, line 725
            updateDataValue('softwareBuild', version) // library marker kkossev.commonLib, line 726
            break // library marker kkossev.commonLib, line 727
        default: // library marker kkossev.commonLib, line 728
            logWarn "zigbee received unknown Basic cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 729
            break // library marker kkossev.commonLib, line 730
    } // library marker kkossev.commonLib, line 731
} // library marker kkossev.commonLib, line 732

/* // library marker kkossev.commonLib, line 734
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 735
 * power cluster            0x0001 // library marker kkossev.commonLib, line 736
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 737
*/ // library marker kkossev.commonLib, line 738
void parsePowerCluster(final Map descMap) { // library marker kkossev.commonLib, line 739
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 740
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 741
    if (descMap.attrId in ["0020", "0021"]) { // library marker kkossev.commonLib, line 742
        state.lastRx["batteryTime"] = new Date().getTime() // library marker kkossev.commonLib, line 743
        state.stats["battCtr"] = (state.stats["battCtr"] ?: 0 ) + 1 // library marker kkossev.commonLib, line 744
    } // library marker kkossev.commonLib, line 745

    final long rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 747
    if (descMap.attrId == "0020") { // library marker kkossev.commonLib, line 748
        sendBatteryVoltageEvent(rawValue) // library marker kkossev.commonLib, line 749
        if ((settings.voltageToPercent ?: false) == true) { // library marker kkossev.commonLib, line 750
            sendBatteryVoltageEvent(rawValue, convertToPercent=true) // library marker kkossev.commonLib, line 751
        } // library marker kkossev.commonLib, line 752
    } // library marker kkossev.commonLib, line 753
    else if (descMap.attrId == "0021") { // library marker kkossev.commonLib, line 754
        sendBatteryPercentageEvent(rawValue * 2)     // library marker kkossev.commonLib, line 755
    } // library marker kkossev.commonLib, line 756
    else { // library marker kkossev.commonLib, line 757
        logWarn "zigbee received unknown Power cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.commonLib, line 758
    } // library marker kkossev.commonLib, line 759
} // library marker kkossev.commonLib, line 760

def sendBatteryVoltageEvent(rawValue, Boolean convertToPercent=false) { // library marker kkossev.commonLib, line 762
    logDebug "batteryVoltage = ${(double)rawValue / 10.0} V" // library marker kkossev.commonLib, line 763
    def result = [:] // library marker kkossev.commonLib, line 764
    def volts = rawValue / 10 // library marker kkossev.commonLib, line 765
    if (!(rawValue == 0 || rawValue == 255)) { // library marker kkossev.commonLib, line 766
        def minVolts = 2.2 // library marker kkossev.commonLib, line 767
        def maxVolts = 3.2 // library marker kkossev.commonLib, line 768
        def pct = (volts - minVolts) / (maxVolts - minVolts) // library marker kkossev.commonLib, line 769
        def roundedPct = Math.round(pct * 100) // library marker kkossev.commonLib, line 770
        if (roundedPct <= 0) roundedPct = 1 // library marker kkossev.commonLib, line 771
        if (roundedPct >100) roundedPct = 100 // library marker kkossev.commonLib, line 772
        if (convertToPercent == true) { // library marker kkossev.commonLib, line 773
            result.value = Math.min(100, roundedPct) // library marker kkossev.commonLib, line 774
            result.name = 'battery' // library marker kkossev.commonLib, line 775
            result.unit  = '%' // library marker kkossev.commonLib, line 776
            result.descriptionText = "battery is ${roundedPct} %" // library marker kkossev.commonLib, line 777
        } // library marker kkossev.commonLib, line 778
        else { // library marker kkossev.commonLib, line 779
            result.value = volts // library marker kkossev.commonLib, line 780
            result.name = 'batteryVoltage' // library marker kkossev.commonLib, line 781
            result.unit  = 'V' // library marker kkossev.commonLib, line 782
            result.descriptionText = "battery is ${volts} Volts" // library marker kkossev.commonLib, line 783
        } // library marker kkossev.commonLib, line 784
        result.type = 'physical' // library marker kkossev.commonLib, line 785
        result.isStateChange = true // library marker kkossev.commonLib, line 786
        logInfo "${result.descriptionText}" // library marker kkossev.commonLib, line 787
        sendEvent(result) // library marker kkossev.commonLib, line 788
    } // library marker kkossev.commonLib, line 789
    else { // library marker kkossev.commonLib, line 790
        logWarn "ignoring BatteryResult(${rawValue})" // library marker kkossev.commonLib, line 791
    }     // library marker kkossev.commonLib, line 792
} // library marker kkossev.commonLib, line 793

def sendBatteryPercentageEvent( batteryPercent, isDigital=false ) { // library marker kkossev.commonLib, line 795
    if ((batteryPercent as int) == 255) { // library marker kkossev.commonLib, line 796
        logWarn "ignoring battery report raw=${batteryPercent}" // library marker kkossev.commonLib, line 797
        return // library marker kkossev.commonLib, line 798
    } // library marker kkossev.commonLib, line 799
    def map = [:] // library marker kkossev.commonLib, line 800
    map.name = 'battery' // library marker kkossev.commonLib, line 801
    map.timeStamp = now() // library marker kkossev.commonLib, line 802
    map.value = batteryPercent < 0 ? 0 : batteryPercent > 100 ? 100 : (batteryPercent as int) // library marker kkossev.commonLib, line 803
    map.unit  = '%' // library marker kkossev.commonLib, line 804
    map.type = isDigital ? 'digital' : 'physical'     // library marker kkossev.commonLib, line 805
    map.descriptionText = "${map.name} is ${map.value} ${map.unit}" // library marker kkossev.commonLib, line 806
    map.isStateChange = true // library marker kkossev.commonLib, line 807
    //  // library marker kkossev.commonLib, line 808
    def latestBatteryEvent = device.latestState('battery', skipCache=true) // library marker kkossev.commonLib, line 809
    def latestBatteryEventTime = latestBatteryEvent != null ? latestBatteryEvent.getDate().getTime() : now() // library marker kkossev.commonLib, line 810
    //log.debug "battery latest state timeStamp is ${latestBatteryTime} now is ${now()}" // library marker kkossev.commonLib, line 811
    def timeDiff = ((now() - latestBatteryEventTime) / 1000) as int // library marker kkossev.commonLib, line 812
    if (settings?.batteryDelay == null || (settings?.batteryDelay as int) == 0 || timeDiff > (settings?.batteryDelay as int)) { // library marker kkossev.commonLib, line 813
        // send it now! // library marker kkossev.commonLib, line 814
        sendDelayedBatteryPercentageEvent(map) // library marker kkossev.commonLib, line 815
    } // library marker kkossev.commonLib, line 816
    else { // library marker kkossev.commonLib, line 817
        def delayedTime = (settings?.batteryDelay as int) - timeDiff // library marker kkossev.commonLib, line 818
        map.delayed = delayedTime // library marker kkossev.commonLib, line 819
        map.descriptionText += " [delayed ${map.delayed} seconds]" // library marker kkossev.commonLib, line 820
        logDebug "this  battery event (${map.value}%) will be delayed ${delayedTime} seconds" // library marker kkossev.commonLib, line 821
        runIn( delayedTime, 'sendDelayedBatteryEvent', [overwrite: true, data: map]) // library marker kkossev.commonLib, line 822
    } // library marker kkossev.commonLib, line 823
} // library marker kkossev.commonLib, line 824

private void sendDelayedBatteryPercentageEvent(Map map) { // library marker kkossev.commonLib, line 826
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 827
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 828
    sendEvent(map) // library marker kkossev.commonLib, line 829
} // library marker kkossev.commonLib, line 830

private void sendDelayedBatteryVoltageEvent(Map map) { // library marker kkossev.commonLib, line 832
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 833
    //map.each {log.trace "$it"} // library marker kkossev.commonLib, line 834
    sendEvent(map) // library marker kkossev.commonLib, line 835
} // library marker kkossev.commonLib, line 836


/* // library marker kkossev.commonLib, line 839
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 840
 * Zigbee Identity Cluster 0x0003 // library marker kkossev.commonLib, line 841
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 842
*/ // library marker kkossev.commonLib, line 843

void parseIdentityCluster(final Map descMap) { // library marker kkossev.commonLib, line 845
    logDebug "unprocessed parseIdentityCluster" // library marker kkossev.commonLib, line 846
} // library marker kkossev.commonLib, line 847



/* // library marker kkossev.commonLib, line 851
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 852
 * Zigbee Scenes Cluster 0x005 // library marker kkossev.commonLib, line 853
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 854
*/ // library marker kkossev.commonLib, line 855

void parseScenesCluster(final Map descMap) { // library marker kkossev.commonLib, line 857
    if (DEVICE_TYPE in ["ButtonDimmer"]) { // library marker kkossev.commonLib, line 858
        parseScenesClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 859
    }     // library marker kkossev.commonLib, line 860
    else { // library marker kkossev.commonLib, line 861
        logWarn "unprocessed ScenesCluste attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 862
    } // library marker kkossev.commonLib, line 863
} // library marker kkossev.commonLib, line 864


/* // library marker kkossev.commonLib, line 867
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 868
 * Zigbee Groups Cluster Parsing 0x004    ZigbeeGroupsOpts // library marker kkossev.commonLib, line 869
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 870
*/ // library marker kkossev.commonLib, line 871

void parseGroupsCluster(final Map descMap) { // library marker kkossev.commonLib, line 873
    // :catchall: 0104 0004 01 01 0040 00 F396 01 00 0000 00 01 00C005, profileId:0104, clusterId:0004, clusterInt:4, sourceEndpoint:01, destinationEndpoint:01, options:0040, messageType:00, dni:F396, isClusterSpecific:true, isManufacturerSpecific:false, manufacturerId:0000, command:00, direction:01, data:[00, C0, 05]] // library marker kkossev.commonLib, line 874
    logDebug "parseGroupsCluster: command=${descMap.command} data=${descMap.data}" // library marker kkossev.commonLib, line 875
    if (state.zigbeeGroups == null) state.zigbeeGroups = [:]     // library marker kkossev.commonLib, line 876
    switch (descMap.command as Integer) { // library marker kkossev.commonLib, line 877
        case 0x00: // Add group    0x0001 – 0xfff7 // library marker kkossev.commonLib, line 878
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 879
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 880
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 881
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 882
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 883
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 884
                logWarn "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) <b>error: ${statusName}</b>" // library marker kkossev.commonLib, line 885
            } // library marker kkossev.commonLib, line 886
            else { // library marker kkossev.commonLib, line 887
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} (${groupIdInt}) statusCode: ${statusName}" // library marker kkossev.commonLib, line 888
                // add the group to state.zigbeeGroups['groups'] if not exist // library marker kkossev.commonLib, line 889
                int groupCount = state.zigbeeGroups['groups'].size() // library marker kkossev.commonLib, line 890
                for (int i=0; i<groupCount; i++ ) { // library marker kkossev.commonLib, line 891
                    if (safeToInt(state.zigbeeGroups['groups'][i]) == groupIdInt) { // library marker kkossev.commonLib, line 892
                        logDebug "Zigbee group ${groupIdInt} (0x${groupId}) already exist" // library marker kkossev.commonLib, line 893
                        return // library marker kkossev.commonLib, line 894
                    } // library marker kkossev.commonLib, line 895
                } // library marker kkossev.commonLib, line 896
                state.zigbeeGroups['groups'].add(groupIdInt) // library marker kkossev.commonLib, line 897
                logInfo "Zigbee group added new group ${groupIdInt} (0x${zigbee.convertToHexString(groupIdInt,4)})" // library marker kkossev.commonLib, line 898
                state.zigbeeGroups['groups'].sort() // library marker kkossev.commonLib, line 899
            } // library marker kkossev.commonLib, line 900
            break // library marker kkossev.commonLib, line 901
        case 0x01: // View group // library marker kkossev.commonLib, line 902
            // The view group command allows the sending device to request that the receiving entity or entities respond with a view group response command containing the application name string for a particular group. // library marker kkossev.commonLib, line 903
            logDebug "received View group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 904
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 905
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 906
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 907
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 908
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 909
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 910
                logWarn "zigbee response View group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 911
            } // library marker kkossev.commonLib, line 912
            else { // library marker kkossev.commonLib, line 913
                logInfo "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 914
            } // library marker kkossev.commonLib, line 915
            break // library marker kkossev.commonLib, line 916
        case 0x02: // Get group membership // library marker kkossev.commonLib, line 917
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 918
            final int capacity = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 919
            final int groupCount = hexStrToUnsignedInt(data[1]) // library marker kkossev.commonLib, line 920
            final Set<String> groups = [] // library marker kkossev.commonLib, line 921
            for (int i = 0; i < groupCount; i++) { // library marker kkossev.commonLib, line 922
                int pos = (i * 2) + 2 // library marker kkossev.commonLib, line 923
                String group = data[pos + 1] + data[pos] // library marker kkossev.commonLib, line 924
                groups.add(hexStrToUnsignedInt(group)) // library marker kkossev.commonLib, line 925
            } // library marker kkossev.commonLib, line 926
            state.zigbeeGroups['groups'] = groups // library marker kkossev.commonLib, line 927
            state.zigbeeGroups['capacity'] = capacity // library marker kkossev.commonLib, line 928
            logInfo "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groups ${groups} groupCount: ${groupCount} capacity: ${capacity}" // library marker kkossev.commonLib, line 929
            break // library marker kkossev.commonLib, line 930
        case 0x03: // Remove group // library marker kkossev.commonLib, line 931
            logInfo "received  Remove group GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 932
            final List<String> data = descMap.data as List<String> // library marker kkossev.commonLib, line 933
            final int statusCode = hexStrToUnsignedInt(data[0]) // library marker kkossev.commonLib, line 934
            final String statusName = ZigbeeStatusEnum[statusCode] ?: "0x${data[0]}" // library marker kkossev.commonLib, line 935
            final String groupId = data[2] + data[1] // library marker kkossev.commonLib, line 936
            final int groupIdInt = hexStrToUnsignedInt(groupId) // library marker kkossev.commonLib, line 937
            if (statusCode > 0x00) { // library marker kkossev.commonLib, line 938
                logWarn "zigbee response remove group ${groupIdInt} (0x${groupId}) error: ${statusName}" // library marker kkossev.commonLib, line 939
            } // library marker kkossev.commonLib, line 940
            else { // library marker kkossev.commonLib, line 941
                logDebug "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId ${groupIdInt} (0x${groupId})  statusCode: ${statusName}" // library marker kkossev.commonLib, line 942
            } // library marker kkossev.commonLib, line 943
            // remove it from the states, even if status code was 'Not Found' // library marker kkossev.commonLib, line 944
            def index = state.zigbeeGroups['groups'].indexOf(groupIdInt) // library marker kkossev.commonLib, line 945
            if (index >= 0) { // library marker kkossev.commonLib, line 946
                state.zigbeeGroups['groups'].remove(index) // library marker kkossev.commonLib, line 947
                logDebug "Zigbee group ${groupIdInt} (0x${groupId}) removed" // library marker kkossev.commonLib, line 948
            } // library marker kkossev.commonLib, line 949
            break // library marker kkossev.commonLib, line 950
        case 0x04: //Remove all groups // library marker kkossev.commonLib, line 951
            logInfo "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 952
            logWarn "not implemented!" // library marker kkossev.commonLib, line 953
            break // library marker kkossev.commonLib, line 954
        case 0x05: // Add group if identifying // library marker kkossev.commonLib, line 955
            //  add group membership in a particular group for one or more endpoints on the receiving device, on condition that it is identifying itself. Identifying functionality is controlled using the identify cluster, (see 3.5).  // library marker kkossev.commonLib, line 956
            logInfo "received zigbee GROUPS cluster response for command: ${descMap.command} \'${ZigbeeGroupsOpts.options[descMap.command as int]}\' : groupId 0x${groupId} statusCode: ${statusName}" // library marker kkossev.commonLib, line 957
            logWarn "not implemented!" // library marker kkossev.commonLib, line 958
            break // library marker kkossev.commonLib, line 959
        default: // library marker kkossev.commonLib, line 960
            logWarn "received unknown GROUPS cluster command: ${descMap.command} (${descMap})" // library marker kkossev.commonLib, line 961
            break // library marker kkossev.commonLib, line 962
    } // library marker kkossev.commonLib, line 963
} // library marker kkossev.commonLib, line 964

List<String> addGroupMembership(groupNr) { // library marker kkossev.commonLib, line 966
    List<String> cmds = [] // library marker kkossev.commonLib, line 967
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 968
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 969
        logWarn "addGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 970
        return // library marker kkossev.commonLib, line 971
    } // library marker kkossev.commonLib, line 972
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 973
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x00, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 974
    logDebug "addGroupMembership: adding group ${group} to ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 975
    return cmds // library marker kkossev.commonLib, line 976
} // library marker kkossev.commonLib, line 977

List<String> viewGroupMembership(groupNr) { // library marker kkossev.commonLib, line 979
    List<String> cmds = [] // library marker kkossev.commonLib, line 980
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 981
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 982
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x01, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 983
    logDebug "viewGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 984
    return cmds // library marker kkossev.commonLib, line 985
} // library marker kkossev.commonLib, line 986

List<String> getGroupMembership(dummy) { // library marker kkossev.commonLib, line 988
    List<String> cmds = [] // library marker kkossev.commonLib, line 989
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, "00") // library marker kkossev.commonLib, line 990
    logDebug "getGroupMembership: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 991
    return cmds // library marker kkossev.commonLib, line 992
} // library marker kkossev.commonLib, line 993

List<String> removeGroupMembership(groupNr) { // library marker kkossev.commonLib, line 995
    List<String> cmds = [] // library marker kkossev.commonLib, line 996
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 997
    if (group < 1 || group > 0xFFF7) { // library marker kkossev.commonLib, line 998
        logWarn "removeGroupMembership: invalid group ${groupNr}" // library marker kkossev.commonLib, line 999
        return // library marker kkossev.commonLib, line 1000
    } // library marker kkossev.commonLib, line 1001
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1002
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x03, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1003
    logDebug "removeGroupMembership: deleting group ${group} from ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1004
    return cmds // library marker kkossev.commonLib, line 1005
} // library marker kkossev.commonLib, line 1006

List<String> removeAllGroups(groupNr) { // library marker kkossev.commonLib, line 1008
    List<String> cmds = [] // library marker kkossev.commonLib, line 1009
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1010
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1011
    cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x04, [:], DELAY_MS, "${groupHex} 00") // library marker kkossev.commonLib, line 1012
    logDebug "removeAllGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1013
    return cmds // library marker kkossev.commonLib, line 1014
} // library marker kkossev.commonLib, line 1015

List<String> notImplementedGroups(groupNr) { // library marker kkossev.commonLib, line 1017
    List<String> cmds = [] // library marker kkossev.commonLib, line 1018
    final Integer group = safeToInt(groupNr) // library marker kkossev.commonLib, line 1019
    final String groupHex = DataType.pack(group, DataType.UINT16, true) // library marker kkossev.commonLib, line 1020
    logWarn "notImplementedGroups: zigbeeGroups is ${state.zigbeeGroups['groups']} cmds=${cmds}" // library marker kkossev.commonLib, line 1021
    return cmds // library marker kkossev.commonLib, line 1022
} // library marker kkossev.commonLib, line 1023

@Field static final Map GroupCommandsMap = [ // library marker kkossev.commonLib, line 1025
    "--- select ---"           : [ min: null, max: null,   type: 'none',   defaultValue: 99, function: 'GroupCommandsHelp'], // library marker kkossev.commonLib, line 1026
    "Add group"                : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 0,  function: 'addGroupMembership'], // library marker kkossev.commonLib, line 1027
    "View group"               : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 1,  function: 'viewGroupMembership'], // library marker kkossev.commonLib, line 1028
    "Get group membership"     : [ min: null, max: null,   type: 'none',   defaultValue: 2,  function: 'getGroupMembership'], // library marker kkossev.commonLib, line 1029
    "Remove group"             : [ min: 0,    max: 0xFFF7, type: 'number', defaultValue: 3,  function: 'removeGroupMembership'], // library marker kkossev.commonLib, line 1030
    "Remove all groups"        : [ min: null, max: null,   type: 'none',   defaultValue: 4,  function: 'removeAllGroups'], // library marker kkossev.commonLib, line 1031
    "Add group if identifying" : [ min: 1,    max: 0xFFF7, type: 'number', defaultValue: 5,  function: 'notImplementedGroups'] // library marker kkossev.commonLib, line 1032
] // library marker kkossev.commonLib, line 1033
/* // library marker kkossev.commonLib, line 1034
@Field static final Map ZigbeeGroupsOpts = [ // library marker kkossev.commonLib, line 1035
    defaultValue: 0, // library marker kkossev.commonLib, line 1036
    options     : [99: '--- select ---', 0: 'Add group', 1: 'View group', 2: 'Get group membership', 3: 'Remove group', 4: 'Remove all groups', 5: 'Add group if identifying'] // library marker kkossev.commonLib, line 1037
] // library marker kkossev.commonLib, line 1038
*/ // library marker kkossev.commonLib, line 1039

def zigbeeGroups( command=null, par=null ) // library marker kkossev.commonLib, line 1041
{ // library marker kkossev.commonLib, line 1042
    logInfo "executing command \'${command}\', parameter ${par}" // library marker kkossev.commonLib, line 1043
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 1044
    if (state.zigbeeGroups == null) state.zigbeeGroups = [:] // library marker kkossev.commonLib, line 1045
    if (state.zigbeeGroups['groups'] == null) state.zigbeeGroups['groups'] = [] // library marker kkossev.commonLib, line 1046
    def value // library marker kkossev.commonLib, line 1047
    Boolean validated = false // library marker kkossev.commonLib, line 1048
    if (command == null || !(command in (GroupCommandsMap.keySet() as List))) { // library marker kkossev.commonLib, line 1049
        logWarn "zigbeeGroups: command <b>${command}</b> must be one of these : ${GroupCommandsMap.keySet() as List}" // library marker kkossev.commonLib, line 1050
        return // library marker kkossev.commonLib, line 1051
    } // library marker kkossev.commonLib, line 1052
    value = GroupCommandsMap[command]?.type == "number" ? safeToInt(par, -1) : 0 // library marker kkossev.commonLib, line 1053
    if (GroupCommandsMap[command]?.type == 'none' || (value >= GroupCommandsMap[command]?.min && value <= GroupCommandsMap[command]?.max)) validated = true // library marker kkossev.commonLib, line 1054
    if (validated == false && GroupCommandsMap[command]?.min != null && GroupCommandsMap[command]?.max != null) { // library marker kkossev.commonLib, line 1055
        log.warn "zigbeeGroups: command <b>command</b> parameter <b>${par}</b> must be within ${GroupCommandsMap[command]?.min} and  ${GroupCommandsMap[command]?.max} " // library marker kkossev.commonLib, line 1056
        return // library marker kkossev.commonLib, line 1057
    } // library marker kkossev.commonLib, line 1058
    // // library marker kkossev.commonLib, line 1059
    def func // library marker kkossev.commonLib, line 1060
   // try { // library marker kkossev.commonLib, line 1061
        func = GroupCommandsMap[command]?.function // library marker kkossev.commonLib, line 1062
        def type = GroupCommandsMap[command]?.type // library marker kkossev.commonLib, line 1063
        // device.updateSetting("$par", [value:value, type:type])  // TODO !!! // library marker kkossev.commonLib, line 1064
        cmds = "$func"(value) // library marker kkossev.commonLib, line 1065
 //   } // library marker kkossev.commonLib, line 1066
//    catch (e) { // library marker kkossev.commonLib, line 1067
//        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1068
//        return // library marker kkossev.commonLib, line 1069
//    } // library marker kkossev.commonLib, line 1070

    logDebug "executed <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 1072
    sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 1073
} // library marker kkossev.commonLib, line 1074

def GroupCommandsHelp( val ) { // library marker kkossev.commonLib, line 1076
    logWarn "GroupCommands: select one of the commands in this list!"              // library marker kkossev.commonLib, line 1077
} // library marker kkossev.commonLib, line 1078

/* // library marker kkossev.commonLib, line 1080
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1081
 * on/off cluster            0x0006 // library marker kkossev.commonLib, line 1082
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1083
*/ // library marker kkossev.commonLib, line 1084

void parseOnOffCluster(final Map descMap) { // library marker kkossev.commonLib, line 1086
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1087
    if (DEVICE_TYPE in ["ButtonDimmer"]) { // library marker kkossev.commonLib, line 1088
        parseOnOffClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 1089
    }     // library marker kkossev.commonLib, line 1090

    else if (descMap.attrId == "0000") { // library marker kkossev.commonLib, line 1092
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseOnOffCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1093
        final long rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1094
        sendSwitchEvent(rawValue) // library marker kkossev.commonLib, line 1095
    } // library marker kkossev.commonLib, line 1096
    else if (descMap.attrId in ["4000", "4001", "4002", "4004", "8000", "8001", "8002", "8003"]) { // library marker kkossev.commonLib, line 1097
        parseOnOffAttributes(descMap) // library marker kkossev.commonLib, line 1098
    } // library marker kkossev.commonLib, line 1099
    else { // library marker kkossev.commonLib, line 1100
        logWarn "unprocessed OnOffCluster attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1101
    } // library marker kkossev.commonLib, line 1102
} // library marker kkossev.commonLib, line 1103

def clearIsDigital()        { state.states["isDigital"] = false } // library marker kkossev.commonLib, line 1105
def switchDebouncingClear() { state.states["debounce"]  = false } // library marker kkossev.commonLib, line 1106
def isRefreshRequestClear() { state.states["isRefresh"] = false } // library marker kkossev.commonLib, line 1107

def toggle() { // library marker kkossev.commonLib, line 1109
    def descriptionText = "central button switch is " // library marker kkossev.commonLib, line 1110
    def state = "" // library marker kkossev.commonLib, line 1111
    if ((device.currentState('switch')?.value ?: 'n/a') == 'off' ) { // library marker kkossev.commonLib, line 1112
        state = "on" // library marker kkossev.commonLib, line 1113
    } // library marker kkossev.commonLib, line 1114
    else { // library marker kkossev.commonLib, line 1115
        state = "off" // library marker kkossev.commonLib, line 1116
    } // library marker kkossev.commonLib, line 1117
    descriptionText += state // library marker kkossev.commonLib, line 1118
    sendEvent(name: "switch", value: state, descriptionText: descriptionText, type: "physical", isStateChange: true) // library marker kkossev.commonLib, line 1119
    logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1120
} // library marker kkossev.commonLib, line 1121

def off() { // library marker kkossev.commonLib, line 1123
    if (DEVICE_TYPE in ["Thermostat"]) { thermostatOff(); return } // library marker kkossev.commonLib, line 1124
    if ((settings?.alwaysOn ?: false) == true) { // library marker kkossev.commonLib, line 1125
        logWarn "AlwaysOn option for ${device.displayName} is enabled , the command to switch it OFF is ignored!" // library marker kkossev.commonLib, line 1126
        return // library marker kkossev.commonLib, line 1127
    } // library marker kkossev.commonLib, line 1128
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1129
    state.states["isDigital"] = true // library marker kkossev.commonLib, line 1130
    logDebug "Switching ${device.displayName} Off" // library marker kkossev.commonLib, line 1131
    def cmds = zigbee.off() // library marker kkossev.commonLib, line 1132
    /* // library marker kkossev.commonLib, line 1133
    if (device.getDataValue("model") == "HY0105") { // library marker kkossev.commonLib, line 1134
        cmds += zigbee.command(0x0006, 0x00, "", [destEndpoint: 0x02]) // library marker kkossev.commonLib, line 1135
    } // library marker kkossev.commonLib, line 1136
        else if (state.model == "TS0601") { // library marker kkossev.commonLib, line 1137
            if (isDinRail() || isRTXCircuitBreaker()) { // library marker kkossev.commonLib, line 1138
                cmds = sendTuyaCommand("10", DP_TYPE_BOOL, "00") // library marker kkossev.commonLib, line 1139
            } // library marker kkossev.commonLib, line 1140
            else { // library marker kkossev.commonLib, line 1141
                cmds = zigbee.command(0xEF00, 0x0, "00010101000100") // library marker kkossev.commonLib, line 1142
            } // library marker kkossev.commonLib, line 1143
        } // library marker kkossev.commonLib, line 1144
        else if (isHEProblematic()) { // library marker kkossev.commonLib, line 1145
            cmds = ["he cmd 0x${device.deviceNetworkId}  0x01 0x0006 0 {}","delay 200"] // library marker kkossev.commonLib, line 1146
            logWarn "isHEProblematic() : sending off() : ${cmds}" // library marker kkossev.commonLib, line 1147
        } // library marker kkossev.commonLib, line 1148
        else if (device.endpointId == "F2") { // library marker kkossev.commonLib, line 1149
            cmds = ["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 0 {}","delay 200"] // library marker kkossev.commonLib, line 1150
        } // library marker kkossev.commonLib, line 1151
*/ // library marker kkossev.commonLib, line 1152
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1153
        if ((device.currentState('switch')?.value ?: 'n/a') == 'off' ) { // library marker kkossev.commonLib, line 1154
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1155
        } // library marker kkossev.commonLib, line 1156
        def value = SwitchThreeStateOpts.options[2]    // 'switching_on' // library marker kkossev.commonLib, line 1157
        def descriptionText = "${value} (2)" // library marker kkossev.commonLib, line 1158
        sendEvent(name: "switch", value: value, descriptionText: descriptionText, type: "digital", isStateChange: true) // library marker kkossev.commonLib, line 1159
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1160
    } // library marker kkossev.commonLib, line 1161
    else { // library marker kkossev.commonLib, line 1162
        logWarn "_THREE_STATE=${_THREE_STATE} settings?.threeStateEnable=${settings?.threeStateEnable}" // library marker kkossev.commonLib, line 1163
    } // library marker kkossev.commonLib, line 1164


    runInMillis( DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1167
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1168
} // library marker kkossev.commonLib, line 1169

def on() { // library marker kkossev.commonLib, line 1171
    if (DEVICE_TYPE in ["Thermostat"]) { thermostatOn(); return } // library marker kkossev.commonLib, line 1172
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 1173
    state.states["isDigital"] = true // library marker kkossev.commonLib, line 1174
    logDebug "Switching ${device.displayName} On" // library marker kkossev.commonLib, line 1175
    def cmds = zigbee.on() // library marker kkossev.commonLib, line 1176
/* // library marker kkossev.commonLib, line 1177
    if (device.getDataValue("model") == "HY0105") { // library marker kkossev.commonLib, line 1178
        cmds += zigbee.command(0x0006, 0x01, "", [destEndpoint: 0x02]) // library marker kkossev.commonLib, line 1179
    }     // library marker kkossev.commonLib, line 1180
    else if (state.model == "TS0601") { // library marker kkossev.commonLib, line 1181
        if (isDinRail() || isRTXCircuitBreaker()) { // library marker kkossev.commonLib, line 1182
            cmds = sendTuyaCommand("10", DP_TYPE_BOOL, "01") // library marker kkossev.commonLib, line 1183
        } // library marker kkossev.commonLib, line 1184
        else { // library marker kkossev.commonLib, line 1185
            cmds = zigbee.command(0xEF00, 0x0, "00010101000101") // library marker kkossev.commonLib, line 1186
        } // library marker kkossev.commonLib, line 1187
    } // library marker kkossev.commonLib, line 1188
    else if (isHEProblematic()) { // library marker kkossev.commonLib, line 1189
        cmds = ["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 1 {}","delay 200"] // library marker kkossev.commonLib, line 1190
        logWarn "isHEProblematic() : sending off() : ${cmds}" // library marker kkossev.commonLib, line 1191
    } // library marker kkossev.commonLib, line 1192
    else if (device.endpointId == "F2") { // library marker kkossev.commonLib, line 1193
        cmds = ["he cmd 0x${device.deviceNetworkId} 0x01 0x0006 1 {}","delay 200"] // library marker kkossev.commonLib, line 1194
    } // library marker kkossev.commonLib, line 1195
*/ // library marker kkossev.commonLib, line 1196
    if (_THREE_STATE == true && settings?.threeStateEnable == true) { // library marker kkossev.commonLib, line 1197
        if ((device.currentState('switch')?.value ?: 'n/a') == 'on' ) { // library marker kkossev.commonLib, line 1198
            runIn(1, 'refresh',  [overwrite: true]) // library marker kkossev.commonLib, line 1199
        } // library marker kkossev.commonLib, line 1200
        def value = SwitchThreeStateOpts.options[3]    // 'switching_on' // library marker kkossev.commonLib, line 1201
        def descriptionText = "${value} (3)" // library marker kkossev.commonLib, line 1202
        sendEvent(name: "switch", value: value, descriptionText: descriptionText, type: "digital", isStateChange: true) // library marker kkossev.commonLib, line 1203
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 1204
    } // library marker kkossev.commonLib, line 1205
    else { // library marker kkossev.commonLib, line 1206
        logWarn "_THREE_STATE=${_THREE_STATE} settings?.threeStateEnable=${settings?.threeStateEnable}" // library marker kkossev.commonLib, line 1207
    } // library marker kkossev.commonLib, line 1208


    runInMillis( DIGITAL_TIMER, clearIsDigital, [overwrite: true]) // library marker kkossev.commonLib, line 1211
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 1212
} // library marker kkossev.commonLib, line 1213

def sendSwitchEvent( switchValue ) { // library marker kkossev.commonLib, line 1215
    def value = (switchValue == null) ? 'unknown' : (switchValue == 0x00) ? 'off' : (switchValue == 0x01) ? 'on' : 'unknown' // library marker kkossev.commonLib, line 1216
    def map = [:]  // library marker kkossev.commonLib, line 1217
    boolean bWasChange = false // library marker kkossev.commonLib, line 1218
    boolean debounce   = state.states["debounce"] ?: false // library marker kkossev.commonLib, line 1219
    def lastSwitch = state.states["lastSwitch"] ?: "unknown" // library marker kkossev.commonLib, line 1220
    if (debounce == true && value == lastSwitch) {    // some devices send only catchall events, some only readattr reports, but some will fire both... // library marker kkossev.commonLib, line 1221
        logDebug "Ignored duplicated switch event ${value}" // library marker kkossev.commonLib, line 1222
        runInMillis( DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true]) // library marker kkossev.commonLib, line 1223
        return null // library marker kkossev.commonLib, line 1224
    } // library marker kkossev.commonLib, line 1225
    else { // library marker kkossev.commonLib, line 1226
        //log.trace "value=${value}  lastSwitch=${state.states['lastSwitch']}" // library marker kkossev.commonLib, line 1227
    } // library marker kkossev.commonLib, line 1228
    def isDigital = state.states["isDigital"] // library marker kkossev.commonLib, line 1229
    map.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 1230
    if (lastSwitch != value ) { // library marker kkossev.commonLib, line 1231
        bWasChange = true // library marker kkossev.commonLib, line 1232
        logDebug "switch state changed from <b>${lastSwitch}</b> to <b>${value}</b>" // library marker kkossev.commonLib, line 1233
        state.states["debounce"]   = true // library marker kkossev.commonLib, line 1234
        state.states["lastSwitch"] = value // library marker kkossev.commonLib, line 1235
        runInMillis( DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])         // library marker kkossev.commonLib, line 1236
    } // library marker kkossev.commonLib, line 1237
    else { // library marker kkossev.commonLib, line 1238
        state.states["debounce"] = true // library marker kkossev.commonLib, line 1239
        runInMillis( DEBOUNCING_TIMER, switchDebouncingClear, [overwrite: true])      // library marker kkossev.commonLib, line 1240
    } // library marker kkossev.commonLib, line 1241

    map.name = "switch" // library marker kkossev.commonLib, line 1243
    map.value = value // library marker kkossev.commonLib, line 1244
    boolean isRefresh = state.states["isRefresh"] ?: false // library marker kkossev.commonLib, line 1245
    if (isRefresh == true) { // library marker kkossev.commonLib, line 1246
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1247
        map.isStateChange = true // library marker kkossev.commonLib, line 1248
    } // library marker kkossev.commonLib, line 1249
    else { // library marker kkossev.commonLib, line 1250
        map.descriptionText = "${device.displayName} is ${value} [${map.type}]" // library marker kkossev.commonLib, line 1251
    } // library marker kkossev.commonLib, line 1252
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1253
    sendEvent(map) // library marker kkossev.commonLib, line 1254
    clearIsDigital() // library marker kkossev.commonLib, line 1255
} // library marker kkossev.commonLib, line 1256

@Field static final Map powerOnBehaviourOptions = [    // library marker kkossev.commonLib, line 1258
    '0': 'switch off', // library marker kkossev.commonLib, line 1259
    '1': 'switch on', // library marker kkossev.commonLib, line 1260
    '2': 'switch last state' // library marker kkossev.commonLib, line 1261
] // library marker kkossev.commonLib, line 1262

@Field static final Map switchTypeOptions = [    // library marker kkossev.commonLib, line 1264
    '0': 'toggle', // library marker kkossev.commonLib, line 1265
    '1': 'state', // library marker kkossev.commonLib, line 1266
    '2': 'momentary' // library marker kkossev.commonLib, line 1267
] // library marker kkossev.commonLib, line 1268

Map myParseDescriptionAsMap( String description ) // library marker kkossev.commonLib, line 1270
{ // library marker kkossev.commonLib, line 1271
    def descMap = [:] // library marker kkossev.commonLib, line 1272
    try { // library marker kkossev.commonLib, line 1273
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1274
    } // library marker kkossev.commonLib, line 1275
    catch (e1) { // library marker kkossev.commonLib, line 1276
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1277
        // try alternative custom parsing // library marker kkossev.commonLib, line 1278
        descMap = [:] // library marker kkossev.commonLib, line 1279
        try { // library marker kkossev.commonLib, line 1280
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1281
                def pair = entry.split(':') // library marker kkossev.commonLib, line 1282
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1283
            }         // library marker kkossev.commonLib, line 1284
        } // library marker kkossev.commonLib, line 1285
        catch (e2) { // library marker kkossev.commonLib, line 1286
            logWarn "exception ${e2} caught while parsing using an alternative method <b>myParseDescriptionAsMap</b> description:  ${description}" // library marker kkossev.commonLib, line 1287
            return [:] // library marker kkossev.commonLib, line 1288
        } // library marker kkossev.commonLib, line 1289
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1290
    } // library marker kkossev.commonLib, line 1291
    return descMap // library marker kkossev.commonLib, line 1292
} // library marker kkossev.commonLib, line 1293

boolean isTuyaE00xCluster( String description ) // library marker kkossev.commonLib, line 1295
{ // library marker kkossev.commonLib, line 1296
    if(description == null || !(description.indexOf('cluster: E000') >= 0 || description.indexOf('cluster: E001') >= 0)) { // library marker kkossev.commonLib, line 1297
        return false  // library marker kkossev.commonLib, line 1298
    } // library marker kkossev.commonLib, line 1299
    // try to parse ... // library marker kkossev.commonLib, line 1300
    //logDebug "Tuya cluster: E000 or E001 - try to parse it..." // library marker kkossev.commonLib, line 1301
    def descMap = [:] // library marker kkossev.commonLib, line 1302
    try { // library marker kkossev.commonLib, line 1303
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1304
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1305
    } // library marker kkossev.commonLib, line 1306
    catch ( e ) { // library marker kkossev.commonLib, line 1307
        logDebug "<b>exception</b> caught while parsing description:  ${description}" // library marker kkossev.commonLib, line 1308
        logDebug "TuyaE00xCluster Desc Map: ${descMap}" // library marker kkossev.commonLib, line 1309
        // cluster E001 is the one that is generating exceptions... // library marker kkossev.commonLib, line 1310
        return true // library marker kkossev.commonLib, line 1311
    } // library marker kkossev.commonLib, line 1312

    if (descMap.cluster == "E000" && descMap.attrId in ["D001", "D002", "D003"]) { // library marker kkossev.commonLib, line 1314
        logDebug "Tuya Specific cluster ${descMap.cluster} attribute ${descMap.attrId} value is ${descMap.value}" // library marker kkossev.commonLib, line 1315
    } // library marker kkossev.commonLib, line 1316
    else if (descMap.cluster == "E001" && descMap.attrId == "D010") { // library marker kkossev.commonLib, line 1317
        if (settings?.logEnable) { logInfo "power on behavior is <b>${powerOnBehaviourOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1318
    } // library marker kkossev.commonLib, line 1319
    else if (descMap.cluster == "E001" && descMap.attrId == "D030") { // library marker kkossev.commonLib, line 1320
        if (settings?.logEnable) { logInfo "swith type is <b>${switchTypeOptions[safeToInt(descMap.value).toString()]}</b> (${descMap.value})" } // library marker kkossev.commonLib, line 1321
    } // library marker kkossev.commonLib, line 1322
    else { // library marker kkossev.commonLib, line 1323
        logDebug "<b>unprocessed</b> TuyaE00xCluster Desc Map: $descMap" // library marker kkossev.commonLib, line 1324
        return false  // library marker kkossev.commonLib, line 1325
    } // library marker kkossev.commonLib, line 1326
    return true    // processed // library marker kkossev.commonLib, line 1327
} // library marker kkossev.commonLib, line 1328

// return true if further processing in the main parse method should be cancelled ! // library marker kkossev.commonLib, line 1330
boolean otherTuyaOddities( String description ) { // library marker kkossev.commonLib, line 1331
  /* // library marker kkossev.commonLib, line 1332
    if (description.indexOf('cluster: 0000') >= 0 && description.indexOf('attrId: 0004') >= 0) { // library marker kkossev.commonLib, line 1333
        if (logEnable) log.debug "${device.displayName} skipping Tuya parse of  cluster 0 attrId 4"             // parseDescriptionAsMap throws exception when processing Tuya cluster 0 attrId 4  // library marker kkossev.commonLib, line 1334
        return true // library marker kkossev.commonLib, line 1335
    } // library marker kkossev.commonLib, line 1336
*/ // library marker kkossev.commonLib, line 1337
    def descMap = [:] // library marker kkossev.commonLib, line 1338
    try { // library marker kkossev.commonLib, line 1339
        descMap = zigbee.parseDescriptionAsMap(description) // library marker kkossev.commonLib, line 1340
    } // library marker kkossev.commonLib, line 1341
    catch (e1) { // library marker kkossev.commonLib, line 1342
        logWarn "exception ${e1} caught while parseDescriptionAsMap <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1343
        // try alternative custom parsing // library marker kkossev.commonLib, line 1344
        descMap = [:] // library marker kkossev.commonLib, line 1345
        try { // library marker kkossev.commonLib, line 1346
            descMap += description.replaceAll('\\[|\\]', '').split(',').collectEntries { entry -> // library marker kkossev.commonLib, line 1347
                def pair = entry.split(':') // library marker kkossev.commonLib, line 1348
                [(pair.first().trim()): pair.last().trim()] // library marker kkossev.commonLib, line 1349
            }         // library marker kkossev.commonLib, line 1350
        } // library marker kkossev.commonLib, line 1351
        catch (e2) { // library marker kkossev.commonLib, line 1352
            logWarn "exception ${e2} caught while parsing using an alternative method <b>otherTuyaOddities</b> description:  ${description}" // library marker kkossev.commonLib, line 1353
            return true // library marker kkossev.commonLib, line 1354
        } // library marker kkossev.commonLib, line 1355
        logDebug "alternative method parsing success: descMap=${descMap}" // library marker kkossev.commonLib, line 1356
    } // library marker kkossev.commonLib, line 1357
    //if (logEnable) {log.trace "${device.displayName} Checking Tuya Oddities Desc Map: $descMap"}         // library marker kkossev.commonLib, line 1358
    if (descMap.attrId == null ) { // library marker kkossev.commonLib, line 1359
        //logDebug "otherTuyaOddities: descMap = ${descMap}" // library marker kkossev.commonLib, line 1360
        //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${descMap.clusterId} NO ATTRIBUTE, skipping" // library marker kkossev.commonLib, line 1361
        return false // library marker kkossev.commonLib, line 1362
    } // library marker kkossev.commonLib, line 1363
    boolean bWasAtLeastOneAttributeProcessed = false // library marker kkossev.commonLib, line 1364
    boolean bWasThereAnyStandardAttribite = false // library marker kkossev.commonLib, line 1365
    // attribute report received // library marker kkossev.commonLib, line 1366
    List attrData = [[cluster: descMap.cluster ,attrId: descMap.attrId, value: descMap.value, status: descMap.status]] // library marker kkossev.commonLib, line 1367
    descMap.additionalAttrs.each { // library marker kkossev.commonLib, line 1368
        attrData << [cluster: descMap.cluster, attrId: it.attrId, value: it.value, status: it.status] // library marker kkossev.commonLib, line 1369
        //log.trace "Tuya oddity: filling in attrData ${attrData}" // library marker kkossev.commonLib, line 1370
    } // library marker kkossev.commonLib, line 1371
    attrData.each { // library marker kkossev.commonLib, line 1372
        //log.trace "each it=${it}" // library marker kkossev.commonLib, line 1373
        def map = [:] // library marker kkossev.commonLib, line 1374
        if (it.status == "86") { // library marker kkossev.commonLib, line 1375
            logWarn "Tuya Cluster ${descMap.cluster} unsupported attrId ${it.attrId}" // library marker kkossev.commonLib, line 1376
            // TODO - skip parsing? // library marker kkossev.commonLib, line 1377
        } // library marker kkossev.commonLib, line 1378
        switch (it.cluster) { // library marker kkossev.commonLib, line 1379
            case "0000" : // library marker kkossev.commonLib, line 1380
                if (it.attrId in ["FFE0", "FFE1", "FFE2", "FFE4"]) { // library marker kkossev.commonLib, line 1381
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1382
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1383
                } // library marker kkossev.commonLib, line 1384
                else if (it.attrId in ["FFFE", "FFDF"]) { // library marker kkossev.commonLib, line 1385
                    logDebug "Cluster ${descMap.cluster} Tuya specific attrId ${it.attrId} value ${it.value})" // library marker kkossev.commonLib, line 1386
                    bWasAtLeastOneAttributeProcessed = true // library marker kkossev.commonLib, line 1387
                } // library marker kkossev.commonLib, line 1388
                else { // library marker kkossev.commonLib, line 1389
                    //logDebug "otherTuyaOddities? - Cluster ${descMap.cluster} attrId ${it.attrId} value ${it.value}) N/A, skipping" // library marker kkossev.commonLib, line 1390
                    bWasThereAnyStandardAttribite = true // library marker kkossev.commonLib, line 1391
                } // library marker kkossev.commonLib, line 1392
                break // library marker kkossev.commonLib, line 1393
            default : // library marker kkossev.commonLib, line 1394
                //if (logEnable) log.trace "${device.displayName} otherTuyaOddities - Cluster ${it.cluster} N/A, skipping" // library marker kkossev.commonLib, line 1395
                break // library marker kkossev.commonLib, line 1396
        } // switch // library marker kkossev.commonLib, line 1397
    } // for each attribute // library marker kkossev.commonLib, line 1398
    return bWasAtLeastOneAttributeProcessed && !bWasThereAnyStandardAttribite // library marker kkossev.commonLib, line 1399
} // library marker kkossev.commonLib, line 1400

private boolean isCircuitBreaker()      { device.getDataValue("manufacturer") in ["_TZ3000_ky0fq4ho"] } // library marker kkossev.commonLib, line 1402
private boolean isRTXCircuitBreaker()   { device.getDataValue("manufacturer") in ["_TZE200_abatw3kj"] } // library marker kkossev.commonLib, line 1403

def parseOnOffAttributes( it ) { // library marker kkossev.commonLib, line 1405
    logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1406
    def mode // library marker kkossev.commonLib, line 1407
    def attrName // library marker kkossev.commonLib, line 1408
    if (it.value == null) { // library marker kkossev.commonLib, line 1409
        logDebug "OnOff attribute ${it.attrId} cluster ${it.cluster } skipping NULL value status=${it.status}" // library marker kkossev.commonLib, line 1410
        return // library marker kkossev.commonLib, line 1411
    } // library marker kkossev.commonLib, line 1412
    def value = zigbee.convertHexToInt(it.value) // library marker kkossev.commonLib, line 1413
    switch (it.attrId) { // library marker kkossev.commonLib, line 1414
        case "4000" :    // non-Tuya GlobalSceneControl (bool), read-only // library marker kkossev.commonLib, line 1415
            attrName = "Global Scene Control" // library marker kkossev.commonLib, line 1416
            mode = value == 0 ? "off" : value == 1 ? "on" : null // library marker kkossev.commonLib, line 1417
            break // library marker kkossev.commonLib, line 1418
        case "4001" :    // non-Tuya OnTime (UINT16), read-only // library marker kkossev.commonLib, line 1419
            attrName = "On Time" // library marker kkossev.commonLib, line 1420
            mode = value // library marker kkossev.commonLib, line 1421
            break // library marker kkossev.commonLib, line 1422
        case "4002" :    // non-Tuya OffWaitTime (UINT16), read-only // library marker kkossev.commonLib, line 1423
            attrName = "Off Wait Time" // library marker kkossev.commonLib, line 1424
            mode = value // library marker kkossev.commonLib, line 1425
            break // library marker kkossev.commonLib, line 1426
        case "4003" :    // non-Tuya "powerOnState" (ENUM8), read-write, default=1  // library marker kkossev.commonLib, line 1427
            attrName = "Power On State" // library marker kkossev.commonLib, line 1428
            mode = value == 0 ? "off" : value == 1 ? "on" : value == 2 ?  "Last state" : "UNKNOWN" // library marker kkossev.commonLib, line 1429
            break // library marker kkossev.commonLib, line 1430
        case "8000" :    // command "childLock", [[name:"Child Lock", type: "ENUM", description: "Select Child Lock mode", constraints: ["off", "on"]]] // library marker kkossev.commonLib, line 1431
            attrName = "Child Lock" // library marker kkossev.commonLib, line 1432
            mode = value == 0 ? "off" : "on" // library marker kkossev.commonLib, line 1433
            break // library marker kkossev.commonLib, line 1434
        case "8001" :    // command "ledMode", [[name:"LED mode", type: "ENUM", description: "Select LED mode", constraints: ["Disabled", "Lit when On", "Lit when Off", "Always Green", "Red when On; Green when Off", "Green when On; Red when Off", "Always Red" ]]] // library marker kkossev.commonLib, line 1435
            attrName = "LED mode" // library marker kkossev.commonLib, line 1436
            if (isCircuitBreaker()) { // library marker kkossev.commonLib, line 1437
                mode = value == 0 ? "Always Green" : value == 1 ? "Red when On; Green when Off" : value == 2 ? "Green when On; Red when Off" : value == 3 ? "Always Red" : null // library marker kkossev.commonLib, line 1438
            } // library marker kkossev.commonLib, line 1439
            else { // library marker kkossev.commonLib, line 1440
                mode = value == 0 ? "Disabled"  : value == 1 ? "Lit when On" : value == 2 ? "Lit when Off" : value == 3 ? "Freeze": null // library marker kkossev.commonLib, line 1441
            } // library marker kkossev.commonLib, line 1442
            break // library marker kkossev.commonLib, line 1443
        case "8002" :    // command "powerOnState", [[name:"Power On State", type: "ENUM", description: "Select Power On State", constraints: ["off","on", "Last state"]]] // library marker kkossev.commonLib, line 1444
            attrName = "Power On State" // library marker kkossev.commonLib, line 1445
            mode = value == 0 ? "off" : value == 1 ? "on" : value == 2 ?  "Last state" : null // library marker kkossev.commonLib, line 1446
            break // library marker kkossev.commonLib, line 1447
        case "8003" : //  Over current alarm // library marker kkossev.commonLib, line 1448
            attrName = "Over current alarm" // library marker kkossev.commonLib, line 1449
            mode = value == 0 ? "Over Current OK" : value == 1 ? "Over Current Alarm" : null // library marker kkossev.commonLib, line 1450
            break // library marker kkossev.commonLib, line 1451
        default : // library marker kkossev.commonLib, line 1452
            logWarn "Unprocessed Tuya OnOff attribute ${it.attrId} cluster ${it.cluster } reported: value=${it.value}" // library marker kkossev.commonLib, line 1453
            return // library marker kkossev.commonLib, line 1454
    } // library marker kkossev.commonLib, line 1455
    if (settings?.logEnable) { logInfo "${attrName} is ${mode}" } // library marker kkossev.commonLib, line 1456
} // library marker kkossev.commonLib, line 1457

def sendButtonEvent(buttonNumber, buttonState, isDigital=false) { // library marker kkossev.commonLib, line 1459
    def event = [name: buttonState, value: buttonNumber.toString(), data: [buttonNumber: buttonNumber], descriptionText: "button $buttonNumber was $buttonState", isStateChange: true, type: isDigital==true ? 'digital' : 'physical'] // library marker kkossev.commonLib, line 1460
    if (txtEnable) {log.info "${device.displayName} $event.descriptionText"} // library marker kkossev.commonLib, line 1461
    sendEvent(event) // library marker kkossev.commonLib, line 1462
} // library marker kkossev.commonLib, line 1463

def push() {                // Momentary capability // library marker kkossev.commonLib, line 1465
    logDebug "push momentary" // library marker kkossev.commonLib, line 1466
    if (DEVICE_TYPE in ["Fingerbot"])     { pushFingerbot(); return }     // library marker kkossev.commonLib, line 1467
    logWarn "push() not implemented for ${(DEVICE_TYPE)}" // library marker kkossev.commonLib, line 1468
} // library marker kkossev.commonLib, line 1469

def push(buttonNumber) {    //pushableButton capability // library marker kkossev.commonLib, line 1471
    if (DEVICE_TYPE in ["Fingerbot"])     { pushFingerbot(buttonNumber); return }     // library marker kkossev.commonLib, line 1472
    sendButtonEvent(buttonNumber, "pushed", isDigital=true) // library marker kkossev.commonLib, line 1473
} // library marker kkossev.commonLib, line 1474

def doubleTap(buttonNumber) { // library marker kkossev.commonLib, line 1476
    sendButtonEvent(buttonNumber, "doubleTapped", isDigital=true) // library marker kkossev.commonLib, line 1477
} // library marker kkossev.commonLib, line 1478

def hold(buttonNumber) { // library marker kkossev.commonLib, line 1480
    sendButtonEvent(buttonNumber, "held", isDigital=true) // library marker kkossev.commonLib, line 1481
} // library marker kkossev.commonLib, line 1482

def release(buttonNumber) { // library marker kkossev.commonLib, line 1484
    sendButtonEvent(buttonNumber, "released", isDigital=true) // library marker kkossev.commonLib, line 1485
} // library marker kkossev.commonLib, line 1486

void sendNumberOfButtonsEvent(numberOfButtons) { // library marker kkossev.commonLib, line 1488
    sendEvent(name: "numberOfButtons", value: numberOfButtons, isStateChange: true, type: "digital") // library marker kkossev.commonLib, line 1489
} // library marker kkossev.commonLib, line 1490

void sendSupportedButtonValuesEvent(supportedValues) { // library marker kkossev.commonLib, line 1492
    sendEvent(name: "supportedButtonValues", value: JsonOutput.toJson(supportedValues), isStateChange: true, type: "digital") // library marker kkossev.commonLib, line 1493
} // library marker kkossev.commonLib, line 1494


/* // library marker kkossev.commonLib, line 1497
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1498
 * Level Control Cluster            0x0008 // library marker kkossev.commonLib, line 1499
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1500
*/ // library marker kkossev.commonLib, line 1501
void parseLevelControlCluster(final Map descMap) { // library marker kkossev.commonLib, line 1502
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1503
    if (DEVICE_TYPE in ["ButtonDimmer"]) { // library marker kkossev.commonLib, line 1504
        parseLevelControlClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 1505
    } // library marker kkossev.commonLib, line 1506
    else if (DEVICE_TYPE in ["Bulb"]) { // library marker kkossev.commonLib, line 1507
        parseLevelControlClusterBulb(descMap) // library marker kkossev.commonLib, line 1508
    } // library marker kkossev.commonLib, line 1509
    else if (descMap.attrId == "0000") { // library marker kkossev.commonLib, line 1510
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1511
        final long rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1512
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1513
    } // library marker kkossev.commonLib, line 1514
    else { // library marker kkossev.commonLib, line 1515
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1516
    } // library marker kkossev.commonLib, line 1517
} // library marker kkossev.commonLib, line 1518


def sendLevelControlEvent( rawValue ) { // library marker kkossev.commonLib, line 1521
    def value = rawValue as int // library marker kkossev.commonLib, line 1522
    if (value <0) value = 0 // library marker kkossev.commonLib, line 1523
    if (value >100) value = 100 // library marker kkossev.commonLib, line 1524
    def map = [:]  // library marker kkossev.commonLib, line 1525

    def isDigital = state.states["isDigital"] // library marker kkossev.commonLib, line 1527
    map.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 1528

    map.name = "level" // library marker kkossev.commonLib, line 1530
    map.value = value // library marker kkossev.commonLib, line 1531
    boolean isRefresh = state.states["isRefresh"] ?: false // library marker kkossev.commonLib, line 1532
    if (isRefresh == true) { // library marker kkossev.commonLib, line 1533
        map.descriptionText = "${device.displayName} is ${value} [Refresh]" // library marker kkossev.commonLib, line 1534
        map.isStateChange = true // library marker kkossev.commonLib, line 1535
    } // library marker kkossev.commonLib, line 1536
    else { // library marker kkossev.commonLib, line 1537
        map.descriptionText = "${device.displayName} was set ${value} [${map.type}]" // library marker kkossev.commonLib, line 1538
    } // library marker kkossev.commonLib, line 1539
    logInfo "${map.descriptionText}" // library marker kkossev.commonLib, line 1540
    sendEvent(map) // library marker kkossev.commonLib, line 1541
    clearIsDigital() // library marker kkossev.commonLib, line 1542
} // library marker kkossev.commonLib, line 1543

/** // library marker kkossev.commonLib, line 1545
 * Get the level transition rate // library marker kkossev.commonLib, line 1546
 * @param level desired target level (0-100) // library marker kkossev.commonLib, line 1547
 * @param transitionTime transition time in seconds (optional) // library marker kkossev.commonLib, line 1548
 * @return transition rate in 1/10ths of a second // library marker kkossev.commonLib, line 1549
 */ // library marker kkossev.commonLib, line 1550
private Integer getLevelTransitionRate(final Integer desiredLevel, final Integer transitionTime = null) { // library marker kkossev.commonLib, line 1551
    int rate = 0 // library marker kkossev.commonLib, line 1552
    final Boolean isOn = device.currentValue('switch') == 'on' // library marker kkossev.commonLib, line 1553
    Integer currentLevel = (device.currentValue('level') as Integer) ?: 0 // library marker kkossev.commonLib, line 1554
    if (!isOn) { // library marker kkossev.commonLib, line 1555
        currentLevel = 0 // library marker kkossev.commonLib, line 1556
    } // library marker kkossev.commonLib, line 1557
    // Check if 'transitionTime' has a value // library marker kkossev.commonLib, line 1558
    if (transitionTime > 0) { // library marker kkossev.commonLib, line 1559
        // Calculate the rate by converting 'transitionTime' to BigDecimal, multiplying by 10, and converting to Integer // library marker kkossev.commonLib, line 1560
        rate = transitionTime * 10 // library marker kkossev.commonLib, line 1561
    } else { // library marker kkossev.commonLib, line 1562
        // Check if the 'levelUpTransition' setting has a value and the current level is less than the desired level // library marker kkossev.commonLib, line 1563
        if (((settings.levelUpTransition ?: 0) as Integer) > 0 && currentLevel < desiredLevel) { // library marker kkossev.commonLib, line 1564
            // Set the rate to the value of the 'levelUpTransition' setting converted to Integer // library marker kkossev.commonLib, line 1565
            rate = settings.levelUpTransition.toInteger() // library marker kkossev.commonLib, line 1566
        } // library marker kkossev.commonLib, line 1567
        // Check if the 'levelDownTransition' setting has a value and the current level is greater than the desired level // library marker kkossev.commonLib, line 1568
        else if (((settings.levelDownTransition ?: 0) as Integer) > 0 && currentLevel > desiredLevel) { // library marker kkossev.commonLib, line 1569
            // Set the rate to the value of the 'levelDownTransition' setting converted to Integer // library marker kkossev.commonLib, line 1570
            rate = settings.levelDownTransition.toInteger() // library marker kkossev.commonLib, line 1571
        } // library marker kkossev.commonLib, line 1572
    } // library marker kkossev.commonLib, line 1573
    logDebug "using level transition rate ${rate}" // library marker kkossev.commonLib, line 1574
    return rate // library marker kkossev.commonLib, line 1575
} // library marker kkossev.commonLib, line 1576

// Command option that enable changes when off // library marker kkossev.commonLib, line 1578
@Field static final String PRE_STAGING_OPTION = '01 01' // library marker kkossev.commonLib, line 1579

/** // library marker kkossev.commonLib, line 1581
 * Constrain a value to a range // library marker kkossev.commonLib, line 1582
 * @param value value to constrain // library marker kkossev.commonLib, line 1583
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1584
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1585
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1586
 */ // library marker kkossev.commonLib, line 1587
private static BigDecimal constrain(final BigDecimal value, final BigDecimal min = 0, final BigDecimal max = 100, final BigDecimal nullValue = 0) { // library marker kkossev.commonLib, line 1588
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1589
        return value // library marker kkossev.commonLib, line 1590
    } // library marker kkossev.commonLib, line 1591
    return value != null ? max.min(value.max(min)) : nullValue // library marker kkossev.commonLib, line 1592
} // library marker kkossev.commonLib, line 1593

/** // library marker kkossev.commonLib, line 1595
 * Constrain a value to a range // library marker kkossev.commonLib, line 1596
 * @param value value to constrain // library marker kkossev.commonLib, line 1597
 * @param min minimum value (default 0) // library marker kkossev.commonLib, line 1598
 * @param max maximum value (default 100) // library marker kkossev.commonLib, line 1599
 * @param nullValue value to return if value is null (default 0) // library marker kkossev.commonLib, line 1600
 */ // library marker kkossev.commonLib, line 1601
private static Integer constrain(final Object value, final Integer min = 0, final Integer max = 100, final Integer nullValue = 0) { // library marker kkossev.commonLib, line 1602
    if (min == null || max == null) { // library marker kkossev.commonLib, line 1603
        return value as Integer // library marker kkossev.commonLib, line 1604
    } // library marker kkossev.commonLib, line 1605
    return value != null ? Math.min(Math.max(value as Integer, min) as Integer, max) : nullValue // library marker kkossev.commonLib, line 1606
} // library marker kkossev.commonLib, line 1607

// Delay before reading attribute (when using polling) // library marker kkossev.commonLib, line 1609
@Field static final int POLL_DELAY_MS = 1000 // library marker kkossev.commonLib, line 1610

/** // library marker kkossev.commonLib, line 1612
 * If the device is polling, delay the execution of the provided commands // library marker kkossev.commonLib, line 1613
 * @param delayMs delay in milliseconds // library marker kkossev.commonLib, line 1614
 * @param commands commands to execute // library marker kkossev.commonLib, line 1615
 * @return list of commands to be sent to the device // library marker kkossev.commonLib, line 1616
 */ // library marker kkossev.commonLib, line 1617
private List<String> ifPolling(final int delayMs = 0, final Closure commands) { // library marker kkossev.commonLib, line 1618
    if (state.reportingEnabled == false) { // library marker kkossev.commonLib, line 1619
        final int value = Math.max(delayMs, POLL_DELAY_MS) // library marker kkossev.commonLib, line 1620
        return ["delay ${value}"] + (commands() as List<String>) as List<String> // library marker kkossev.commonLib, line 1621
    } // library marker kkossev.commonLib, line 1622
    return [] // library marker kkossev.commonLib, line 1623
} // library marker kkossev.commonLib, line 1624

def intTo16bitUnsignedHex(value) { // library marker kkossev.commonLib, line 1626
    def hexStr = zigbee.convertToHexString(value.toInteger(),4) // library marker kkossev.commonLib, line 1627
    return new String(hexStr.substring(2, 4) + hexStr.substring(0, 2)) // library marker kkossev.commonLib, line 1628
} // library marker kkossev.commonLib, line 1629

def intTo8bitUnsignedHex(value) { // library marker kkossev.commonLib, line 1631
    return zigbee.convertToHexString(value.toInteger(), 2) // library marker kkossev.commonLib, line 1632
} // library marker kkossev.commonLib, line 1633

/** // library marker kkossev.commonLib, line 1635
 * Send 'switchLevel' attribute event // library marker kkossev.commonLib, line 1636
 * @param isOn true if light is on, false otherwise // library marker kkossev.commonLib, line 1637
 * @param level brightness level (0-254) // library marker kkossev.commonLib, line 1638
 */ // library marker kkossev.commonLib, line 1639
private List<String> setLevelPrivate(final Object value, final Integer rate = 0, final Integer delay = 0, final Boolean levelPreset = false) { // library marker kkossev.commonLib, line 1640
    List<String> cmds = [] // library marker kkossev.commonLib, line 1641
    final Integer level = constrain(value) // library marker kkossev.commonLib, line 1642
    final String hexLevel = DataType.pack(Math.round(level * 2.54).intValue(), DataType.UINT8) // library marker kkossev.commonLib, line 1643
    final String hexRate = DataType.pack(rate, DataType.UINT16, true) // library marker kkossev.commonLib, line 1644
    final int levelCommand = levelPreset ? 0x00 : 0x04 // library marker kkossev.commonLib, line 1645
    if (device.currentValue('switch') == 'off' && level > 0 && levelPreset == false) { // library marker kkossev.commonLib, line 1646
        // If light is off, first go to level 0 then to desired level // library marker kkossev.commonLib, line 1647
        cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x00, [destEndpoint:safeToInt(getDestinationEP())], delay, "00 0000 ${PRE_STAGING_OPTION}") // library marker kkossev.commonLib, line 1648
    } // library marker kkossev.commonLib, line 1649
    // Payload: Level | Transition Time | Options Mask | Options Override // library marker kkossev.commonLib, line 1650
    // Options: Bit 0x01 enables pre-staging level // library marker kkossev.commonLib, line 1651
    /* // library marker kkossev.commonLib, line 1652
    cmds += zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, levelCommand, [destEndpoint:safeToInt(getDestinationEP())], delay, "${hexLevel} ${hexRate} ${PRE_STAGING_OPTION}") + // library marker kkossev.commonLib, line 1653
        ifPolling(DELAY_MS + (rate * 100)) { zigbee.levelRefresh(0) } // library marker kkossev.commonLib, line 1654
    */ // library marker kkossev.commonLib, line 1655
    int duration = 10            // TODO !!! // library marker kkossev.commonLib, line 1656
    String endpointId = "01"     // TODO !!! // library marker kkossev.commonLib, line 1657
     cmds +=  ["he cmd 0x${device.deviceNetworkId} 0x${endpointId} 0x0008 4 { 0x${intTo8bitUnsignedHex(level)} 0x${intTo16bitUnsignedHex(duration)} }",] // library marker kkossev.commonLib, line 1658

    return cmds // library marker kkossev.commonLib, line 1660
} // library marker kkossev.commonLib, line 1661


/** // library marker kkossev.commonLib, line 1664
 * Set Level Command // library marker kkossev.commonLib, line 1665
 * @param value level percent (0-100) // library marker kkossev.commonLib, line 1666
 * @param transitionTime transition time in seconds // library marker kkossev.commonLib, line 1667
 * @return List of zigbee commands // library marker kkossev.commonLib, line 1668
 */ // library marker kkossev.commonLib, line 1669
void /*List<String>*/ setLevel(final Object value, final Object transitionTime = null) { // library marker kkossev.commonLib, line 1670
    logInfo "setLevel (${value}, ${transitionTime})" // library marker kkossev.commonLib, line 1671
    if (DEVICE_TYPE in  ["ButtonDimmer"]) { setLevelButtonDimmer(value, transitionTime); return } // library marker kkossev.commonLib, line 1672
    if (DEVICE_TYPE in  ["Bulb"]) { setLevelBulb(value, transitionTime); return } // library marker kkossev.commonLib, line 1673
    else { // library marker kkossev.commonLib, line 1674
        final Integer rate = getLevelTransitionRate(value as Integer, transitionTime as Integer) // library marker kkossev.commonLib, line 1675
        scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 1676
        /*return*/ sendZigbeeCommands ( setLevelPrivate(value, rate)) // library marker kkossev.commonLib, line 1677
    } // library marker kkossev.commonLib, line 1678
} // library marker kkossev.commonLib, line 1679

/* // library marker kkossev.commonLib, line 1681
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1682
 * Color Control Cluster            0x0300 // library marker kkossev.commonLib, line 1683
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1684
*/ // library marker kkossev.commonLib, line 1685
void parseColorControlCluster(final Map descMap, description) { // library marker kkossev.commonLib, line 1686
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1687
    if (DEVICE_TYPE in ["Bulb"]) { // library marker kkossev.commonLib, line 1688
        parseColorControlClusterBulb(descMap, description) // library marker kkossev.commonLib, line 1689
    } // library marker kkossev.commonLib, line 1690
    else if (descMap.attrId == "0000") { // library marker kkossev.commonLib, line 1691
        if (descMap.value == null || descMap.value == 'FFFF') { logDebug "parseLevelControlCluster: invalid value: ${descMap.value}"; return } // invalid or unknown value // library marker kkossev.commonLib, line 1692
        final long rawValue = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1693
        sendLevelControlEvent(rawValue) // library marker kkossev.commonLib, line 1694
    } // library marker kkossev.commonLib, line 1695
    else { // library marker kkossev.commonLib, line 1696
        logWarn "unprocessed LevelControl attribute ${descMap.attrId}" // library marker kkossev.commonLib, line 1697
    } // library marker kkossev.commonLib, line 1698
} // library marker kkossev.commonLib, line 1699

/* // library marker kkossev.commonLib, line 1701
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1702
 * Illuminance    cluster 0x0400 // library marker kkossev.commonLib, line 1703
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1704
*/ // library marker kkossev.commonLib, line 1705
void parseIlluminanceCluster(final Map descMap) { // library marker kkossev.commonLib, line 1706
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1707
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1708
    final long value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1709
    def lux = value > 0 ? Math.round(Math.pow(10,(value/10000))) : 0 // library marker kkossev.commonLib, line 1710
    handleIlluminanceEvent(lux) // library marker kkossev.commonLib, line 1711
} // library marker kkossev.commonLib, line 1712

void handleIlluminanceEvent( illuminance, Boolean isDigital=false ) { // library marker kkossev.commonLib, line 1714
    def eventMap = [:] // library marker kkossev.commonLib, line 1715
    if (state.stats != null) state.stats['illumCtr'] = (state.stats['illumCtr'] ?: 0) + 1 else state.stats=[:] // library marker kkossev.commonLib, line 1716
    eventMap.name = "illuminance" // library marker kkossev.commonLib, line 1717
    Integer illumCorrected = Math.round((illuminance * ((settings?.illuminanceCoeff ?: 1.00) as float))) // library marker kkossev.commonLib, line 1718
    eventMap.value  = illumCorrected // library marker kkossev.commonLib, line 1719
    eventMap.type = isDigital ? "digital" : "physical" // library marker kkossev.commonLib, line 1720
    eventMap.unit = "lx" // library marker kkossev.commonLib, line 1721
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1722
    Integer timeElapsed = Math.round((now() - (state.lastRx['illumTime'] ?: now()))/1000) // library marker kkossev.commonLib, line 1723
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1724
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1725
    Integer lastIllum = device.currentValue("illuminance") ?: 0 // library marker kkossev.commonLib, line 1726
    Integer delta = Math.abs(lastIllum- illumCorrected) // library marker kkossev.commonLib, line 1727
    if (delta < ((settings?.illuminanceThreshold ?: DEFAULT_ILLUMINANCE_THRESHOLD) as int)) { // library marker kkossev.commonLib, line 1728
        logDebug "<b>skipped</b> illuminance ${illumCorrected}, less than delta ${settings?.illuminanceThreshold} (lastIllum=${lastIllum})" // library marker kkossev.commonLib, line 1729
        return // library marker kkossev.commonLib, line 1730
    } // library marker kkossev.commonLib, line 1731
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1732
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1733
        unschedule("sendDelayedIllumEvent")        //get rid of stale queued reports // library marker kkossev.commonLib, line 1734
        state.lastRx['illumTime'] = now() // library marker kkossev.commonLib, line 1735
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1736
    }         // library marker kkossev.commonLib, line 1737
    else {         // queue the event // library marker kkossev.commonLib, line 1738
        eventMap.type = "delayed" // library marker kkossev.commonLib, line 1739
        logDebug "${device.displayName} <b>delaying ${timeRamaining} seconds</b> event : ${eventMap}" // library marker kkossev.commonLib, line 1740
        runIn(timeRamaining, 'sendDelayedIllumEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1741
    } // library marker kkossev.commonLib, line 1742
} // library marker kkossev.commonLib, line 1743

private void sendDelayedIllumEvent(Map eventMap) { // library marker kkossev.commonLib, line 1745
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1746
    state.lastRx['illumTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1747
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1748
} // library marker kkossev.commonLib, line 1749

@Field static final Map tuyaIlluminanceOpts = [0: 'low', 1: 'medium', 2: 'high'] // library marker kkossev.commonLib, line 1751


/* // library marker kkossev.commonLib, line 1754
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1755
 * temperature // library marker kkossev.commonLib, line 1756
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1757
*/ // library marker kkossev.commonLib, line 1758
void parseTemperatureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1759
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1760
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1761
    final long value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1762
    handleTemperatureEvent(value/100.0F as Float) // library marker kkossev.commonLib, line 1763
} // library marker kkossev.commonLib, line 1764

void handleTemperatureEvent( Float temperature, Boolean isDigital=false ) { // library marker kkossev.commonLib, line 1766
    def eventMap = [:] // library marker kkossev.commonLib, line 1767
    if (state.stats != null) state.stats['tempCtr'] = (state.stats['tempCtr'] ?: 0) + 1 else state.stats=[:] // library marker kkossev.commonLib, line 1768
    eventMap.name = "temperature" // library marker kkossev.commonLib, line 1769
    def Scale = location.temperatureScale // library marker kkossev.commonLib, line 1770
    if (Scale == "F") { // library marker kkossev.commonLib, line 1771
        temperature = (temperature * 1.8) + 32 // library marker kkossev.commonLib, line 1772
        eventMap.unit = "\u00B0"+"F" // library marker kkossev.commonLib, line 1773
    } // library marker kkossev.commonLib, line 1774
    else { // library marker kkossev.commonLib, line 1775
        eventMap.unit = "\u00B0"+"C" // library marker kkossev.commonLib, line 1776
    } // library marker kkossev.commonLib, line 1777
    def tempCorrected = (temperature + safeToDouble(settings?.temperatureOffset ?: 0)) as Float // library marker kkossev.commonLib, line 1778
    eventMap.value  =  (Math.round(tempCorrected * 10) / 10.0) as Float // library marker kkossev.commonLib, line 1779
    eventMap.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 1780
    //eventMap.isStateChange = true // library marker kkossev.commonLib, line 1781
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1782
    Integer timeElapsed = Math.round((now() - (state.lastRx['tempTime'] ?: now()))/1000) // library marker kkossev.commonLib, line 1783
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1784
    Integer timeRamaining = (minTime - timeElapsed) as Integer // library marker kkossev.commonLib, line 1785
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1786
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1787
        unschedule("sendDelayedTempEvent")        //get rid of stale queued reports // library marker kkossev.commonLib, line 1788
        state.lastRx['tempTime'] = now() // library marker kkossev.commonLib, line 1789
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1790
    }         // library marker kkossev.commonLib, line 1791
    else {         // queue the event // library marker kkossev.commonLib, line 1792
        eventMap.type = "delayed" // library marker kkossev.commonLib, line 1793
        logDebug "${device.displayName} DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1794
        runIn(timeRamaining, 'sendDelayedTempEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1795
    } // library marker kkossev.commonLib, line 1796
} // library marker kkossev.commonLib, line 1797

private void sendDelayedTempEvent(Map eventMap) { // library marker kkossev.commonLib, line 1799
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1800
    state.lastRx['tempTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1801
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1802
} // library marker kkossev.commonLib, line 1803

/* // library marker kkossev.commonLib, line 1805
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1806
 * humidity // library marker kkossev.commonLib, line 1807
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1808
*/ // library marker kkossev.commonLib, line 1809
void parseHumidityCluster(final Map descMap) { // library marker kkossev.commonLib, line 1810
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1811
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1812
    final long value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1813
    handleHumidityEvent(value/100.0F as Float) // library marker kkossev.commonLib, line 1814
} // library marker kkossev.commonLib, line 1815

void handleHumidityEvent( Float humidity, Boolean isDigital=false ) { // library marker kkossev.commonLib, line 1817
    def eventMap = [:] // library marker kkossev.commonLib, line 1818
    if (state.stats != null) state.stats['humiCtr'] = (state.stats['humiCtr'] ?: 0) + 1 else state.stats=[:] // library marker kkossev.commonLib, line 1819
    double humidityAsDouble = safeToDouble(humidity) + safeToDouble(settings?.humidityOffset ?: 0) // library marker kkossev.commonLib, line 1820
    if (humidityAsDouble <= 0.0 || humidityAsDouble > 100.0) { // library marker kkossev.commonLib, line 1821
        logWarn "ignored invalid humidity ${humidity} (${humidityAsDouble})" // library marker kkossev.commonLib, line 1822
        return // library marker kkossev.commonLib, line 1823
    } // library marker kkossev.commonLib, line 1824
    eventMap.value = Math.round(humidityAsDouble) // library marker kkossev.commonLib, line 1825
    eventMap.name = "humidity" // library marker kkossev.commonLib, line 1826
    eventMap.unit = "% RH" // library marker kkossev.commonLib, line 1827
    eventMap.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 1828
    //eventMap.isStateChange = true // library marker kkossev.commonLib, line 1829
    eventMap.descriptionText = "${eventMap.name} is ${humidityAsDouble.round(1)} ${eventMap.unit}" // library marker kkossev.commonLib, line 1830
    Integer timeElapsed = Math.round((now() - (state.lastRx['humiTime'] ?: now()))/1000) // library marker kkossev.commonLib, line 1831
    Integer minTime = settings?.minReportingTime ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1832
    Integer timeRamaining = (minTime - timeElapsed) as Integer     // library marker kkossev.commonLib, line 1833
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1834
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1835
        unschedule("sendDelayedHumidityEvent") // library marker kkossev.commonLib, line 1836
        state.lastRx['humiTime'] = now() // library marker kkossev.commonLib, line 1837
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1838
    } // library marker kkossev.commonLib, line 1839
    else { // library marker kkossev.commonLib, line 1840
        eventMap.type = "delayed" // library marker kkossev.commonLib, line 1841
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1842
        runIn(timeRamaining, 'sendDelayedHumidityEvent',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1843
    } // library marker kkossev.commonLib, line 1844
} // library marker kkossev.commonLib, line 1845

private void sendDelayedHumidityEvent(Map eventMap) { // library marker kkossev.commonLib, line 1847
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1848
    state.lastRx['humiTime'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1849
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1850
} // library marker kkossev.commonLib, line 1851

/* // library marker kkossev.commonLib, line 1853
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1854
 * Electrical Measurement Cluster 0x0702 // library marker kkossev.commonLib, line 1855
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1856
*/ // library marker kkossev.commonLib, line 1857

void parseElectricalMeasureCluster(final Map descMap) { // library marker kkossev.commonLib, line 1859
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1860
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1861
    def value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1862
    if (DEVICE_TYPE in  ["Switch"]) { // library marker kkossev.commonLib, line 1863
        parseElectricalMeasureClusterSwitch(descMap) // library marker kkossev.commonLib, line 1864
    } // library marker kkossev.commonLib, line 1865
    else { // library marker kkossev.commonLib, line 1866
        logWarn "parseElectricalMeasureCluster is NOT implemented1" // library marker kkossev.commonLib, line 1867
    } // library marker kkossev.commonLib, line 1868
} // library marker kkossev.commonLib, line 1869

/* // library marker kkossev.commonLib, line 1871
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1872
 * Metering Cluster 0x0B04 // library marker kkossev.commonLib, line 1873
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1874
*/ // library marker kkossev.commonLib, line 1875

void parseMeteringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1877
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1878
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1879
    def value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1880
    if (DEVICE_TYPE in  ["Switch"]) { // library marker kkossev.commonLib, line 1881
        parseMeteringClusterSwitch(descMap) // library marker kkossev.commonLib, line 1882
    } // library marker kkossev.commonLib, line 1883
    else { // library marker kkossev.commonLib, line 1884
        logWarn "parseMeteringCluster is NOT implemented1" // library marker kkossev.commonLib, line 1885
    } // library marker kkossev.commonLib, line 1886
} // library marker kkossev.commonLib, line 1887


/* // library marker kkossev.commonLib, line 1890
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1891
 * pm2.5 // library marker kkossev.commonLib, line 1892
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1893
*/ // library marker kkossev.commonLib, line 1894
void parsePm25Cluster(final Map descMap) { // library marker kkossev.commonLib, line 1895
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1896
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1897
    def value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1898
    Float floatValue = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1899
    //logDebug "pm25 float value = ${floatValue}" // library marker kkossev.commonLib, line 1900
    handlePm25Event(floatValue as Integer) // library marker kkossev.commonLib, line 1901
} // library marker kkossev.commonLib, line 1902

void handlePm25Event( Integer pm25, Boolean isDigital=false ) { // library marker kkossev.commonLib, line 1904
    def eventMap = [:] // library marker kkossev.commonLib, line 1905
    if (state.stats != null) state.stats['pm25Ctr'] = (state.stats['pm25Ctr'] ?: 0) + 1 else state.stats=[:] // library marker kkossev.commonLib, line 1906
    double pm25AsDouble = safeToDouble(pm25) + safeToDouble(settings?.pm25Offset ?: 0) // library marker kkossev.commonLib, line 1907
    if (pm25AsDouble <= 0.0 || pm25AsDouble > 999.0) { // library marker kkossev.commonLib, line 1908
        logWarn "ignored invalid pm25 ${pm25} (${pm25AsDouble})" // library marker kkossev.commonLib, line 1909
        return // library marker kkossev.commonLib, line 1910
    } // library marker kkossev.commonLib, line 1911
    eventMap.value = Math.round(pm25AsDouble) // library marker kkossev.commonLib, line 1912
    eventMap.name = "pm25" // library marker kkossev.commonLib, line 1913
    eventMap.unit = "\u03BCg/m3"    //"mg/m3" // library marker kkossev.commonLib, line 1914
    eventMap.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 1915
    eventMap.isStateChange = true // library marker kkossev.commonLib, line 1916
    eventMap.descriptionText = "${eventMap.name} is ${pm25AsDouble.round()} ${eventMap.unit}" // library marker kkossev.commonLib, line 1917
    Integer timeElapsed = Math.round((now() - (state.lastRx['pm25Time'] ?: now()))/1000) // library marker kkossev.commonLib, line 1918
    Integer minTime = settings?.minReportingTimePm25 ?: DEFAULT_MIN_REPORTING_TIME // library marker kkossev.commonLib, line 1919
    Integer timeRamaining = (minTime - timeElapsed) as Integer     // library marker kkossev.commonLib, line 1920
    if (timeElapsed >= minTime) { // library marker kkossev.commonLib, line 1921
        logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1922
        unschedule("sendDelayedPm25Event") // library marker kkossev.commonLib, line 1923
        state.lastRx['pm25Time'] = now() // library marker kkossev.commonLib, line 1924
        sendEvent(eventMap) // library marker kkossev.commonLib, line 1925
    } // library marker kkossev.commonLib, line 1926
    else { // library marker kkossev.commonLib, line 1927
        eventMap.type = "delayed" // library marker kkossev.commonLib, line 1928
        logDebug "DELAYING ${timeRamaining} seconds event : ${eventMap}" // library marker kkossev.commonLib, line 1929
        runIn(timeRamaining, 'sendDelayedPm25Event',  [overwrite: true, data: eventMap]) // library marker kkossev.commonLib, line 1930
    } // library marker kkossev.commonLib, line 1931
} // library marker kkossev.commonLib, line 1932

private void sendDelayedPm25Event(Map eventMap) { // library marker kkossev.commonLib, line 1934
    logInfo "${eventMap.descriptionText} (${eventMap.type})" // library marker kkossev.commonLib, line 1935
    state.lastRx['pm25Time'] = now()     // TODO - -(minReportingTimeHumidity * 2000) // library marker kkossev.commonLib, line 1936
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1937
} // library marker kkossev.commonLib, line 1938

/* // library marker kkossev.commonLib, line 1940
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1941
 * Analog Input Cluster 0x000C // library marker kkossev.commonLib, line 1942
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1943
*/ // library marker kkossev.commonLib, line 1944
void parseAnalogInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1945
    if (DEVICE_TYPE in ["AirQuality"]) { // library marker kkossev.commonLib, line 1946
        parseAirQualityIndexCluster(descMap) // library marker kkossev.commonLib, line 1947
    } // library marker kkossev.commonLib, line 1948
    else if (DEVICE_TYPE in ["AqaraCube"]) { // library marker kkossev.commonLib, line 1949
        parseAqaraCubeAnalogInputCluster(descMap) // library marker kkossev.commonLib, line 1950
    } // library marker kkossev.commonLib, line 1951
    else { // library marker kkossev.commonLib, line 1952
        logWarn "parseAnalogInputCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 1953
    } // library marker kkossev.commonLib, line 1954
} // library marker kkossev.commonLib, line 1955


/* // library marker kkossev.commonLib, line 1958
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1959
 * Multistate Input Cluster 0x0012 // library marker kkossev.commonLib, line 1960
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1961
*/ // library marker kkossev.commonLib, line 1962

void parseMultistateInputCluster(final Map descMap) { // library marker kkossev.commonLib, line 1964
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1965
    if (descMap.value == null || descMap.value == 'FFFF') { return } // invalid or unknown value // library marker kkossev.commonLib, line 1966
    def value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.commonLib, line 1967
    Float floatValue = Float.intBitsToFloat(value.intValue()) // library marker kkossev.commonLib, line 1968
    if (DEVICE_TYPE in  ["AqaraCube"]) { // library marker kkossev.commonLib, line 1969
        parseMultistateInputClusterAqaraCube(descMap) // library marker kkossev.commonLib, line 1970
    } // library marker kkossev.commonLib, line 1971
    else { // library marker kkossev.commonLib, line 1972
        handleMultistateInputEvent(value as Integer) // library marker kkossev.commonLib, line 1973
    } // library marker kkossev.commonLib, line 1974
} // library marker kkossev.commonLib, line 1975

void handleMultistateInputEvent( Integer value, Boolean isDigital=false ) { // library marker kkossev.commonLib, line 1977
    def eventMap = [:] // library marker kkossev.commonLib, line 1978
    eventMap.value = value // library marker kkossev.commonLib, line 1979
    eventMap.name = "multistateInput" // library marker kkossev.commonLib, line 1980
    eventMap.unit = "" // library marker kkossev.commonLib, line 1981
    eventMap.type = isDigital == true ? "digital" : "physical" // library marker kkossev.commonLib, line 1982
    eventMap.descriptionText = "${eventMap.name} is ${eventMap.value} ${eventMap.unit}" // library marker kkossev.commonLib, line 1983
    sendEvent(eventMap) // library marker kkossev.commonLib, line 1984
    logInfo "${eventMap.descriptionText}" // library marker kkossev.commonLib, line 1985
} // library marker kkossev.commonLib, line 1986

/* // library marker kkossev.commonLib, line 1988
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1989
 * Window Covering Cluster 0x0102 // library marker kkossev.commonLib, line 1990
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 1991
*/ // library marker kkossev.commonLib, line 1992

void parseWindowCoveringCluster(final Map descMap) { // library marker kkossev.commonLib, line 1994
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 1995
    if (DEVICE_TYPE in  ["ButtonDimmer"]) { // library marker kkossev.commonLib, line 1996
        parseWindowCoveringClusterButtonDimmer(descMap) // library marker kkossev.commonLib, line 1997
    } // library marker kkossev.commonLib, line 1998
    else { // library marker kkossev.commonLib, line 1999
        logWarn "parseWindowCoveringCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 2000
    } // library marker kkossev.commonLib, line 2001
} // library marker kkossev.commonLib, line 2002

/* // library marker kkossev.commonLib, line 2004
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2005
 * thermostat cluster 0x0201 // library marker kkossev.commonLib, line 2006
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2007
*/ // library marker kkossev.commonLib, line 2008
void parseThermostatCluster(final Map descMap) { // library marker kkossev.commonLib, line 2009
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2010
    if (DEVICE_TYPE in  ["Thermostat"]) { // library marker kkossev.commonLib, line 2011
        parseThermostatClusterThermostat(descMap) // library marker kkossev.commonLib, line 2012
    } // library marker kkossev.commonLib, line 2013
    else { // library marker kkossev.commonLib, line 2014
        logWarn "parseThermostatCluster: don't know how to handle descMap=${descMap}" // library marker kkossev.commonLib, line 2015
    } // library marker kkossev.commonLib, line 2016
} // library marker kkossev.commonLib, line 2017



// ------------------------------------------------------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2021

def parseE002Cluster( descMap ) { // library marker kkossev.commonLib, line 2023
    if (DEVICE_TYPE in ["Radar"])     { parseE002ClusterRadar(descMap) }     // library marker kkossev.commonLib, line 2024
    else { // library marker kkossev.commonLib, line 2025
        logWarn "Unprocessed cluster 0xE002 command ${descMap.command} attrId ${descMap.attrId} value ${value} (0x${descMap.value})" // library marker kkossev.commonLib, line 2026
    } // library marker kkossev.commonLib, line 2027
} // library marker kkossev.commonLib, line 2028


/* // library marker kkossev.commonLib, line 2031
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2032
 * Tuya cluster EF00 specific code // library marker kkossev.commonLib, line 2033
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2034
*/ // library marker kkossev.commonLib, line 2035
private static getCLUSTER_TUYA()       { 0xEF00 } // library marker kkossev.commonLib, line 2036
private static getSETDATA()            { 0x00 } // library marker kkossev.commonLib, line 2037
private static getSETTIME()            { 0x24 } // library marker kkossev.commonLib, line 2038

// Tuya Commands // library marker kkossev.commonLib, line 2040
private static getTUYA_REQUEST()       { 0x00 } // library marker kkossev.commonLib, line 2041
private static getTUYA_REPORTING()     { 0x01 } // library marker kkossev.commonLib, line 2042
private static getTUYA_QUERY()         { 0x02 } // library marker kkossev.commonLib, line 2043
private static getTUYA_STATUS_SEARCH() { 0x06 } // library marker kkossev.commonLib, line 2044
private static getTUYA_TIME_SYNCHRONISATION() { 0x24 } // library marker kkossev.commonLib, line 2045

// tuya DP type // library marker kkossev.commonLib, line 2047
private static getDP_TYPE_RAW()        { "01" }    // [ bytes ] // library marker kkossev.commonLib, line 2048
private static getDP_TYPE_BOOL()       { "01" }    // [ 0/1 ] // library marker kkossev.commonLib, line 2049
private static getDP_TYPE_VALUE()      { "02" }    // [ 4 byte value ] // library marker kkossev.commonLib, line 2050
private static getDP_TYPE_STRING()     { "03" }    // [ N byte string ] // library marker kkossev.commonLib, line 2051
private static getDP_TYPE_ENUM()       { "04" }    // [ 0-255 ] // library marker kkossev.commonLib, line 2052
private static getDP_TYPE_BITMAP()     { "05" }    // [ 1,2,4 bytes ] as bits // library marker kkossev.commonLib, line 2053


void parseTuyaCluster(final Map descMap) { // library marker kkossev.commonLib, line 2056
    if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == "24") {        //getSETTIME // library marker kkossev.commonLib, line 2057
        logDebug "Tuya time synchronization request from device, descMap = ${descMap}" // library marker kkossev.commonLib, line 2058
        def offset = 0 // library marker kkossev.commonLib, line 2059
        try { // library marker kkossev.commonLib, line 2060
            offset = location.getTimeZone().getOffset(new Date().getTime()) // library marker kkossev.commonLib, line 2061
            //if (settings?.logEnable) log.debug "${device.displayName} timezone offset of current location is ${offset}" // library marker kkossev.commonLib, line 2062
        } // library marker kkossev.commonLib, line 2063
        catch(e) { // library marker kkossev.commonLib, line 2064
            logWarn "cannot resolve current location. please set location in Hubitat location setting. Setting timezone offset to zero" // library marker kkossev.commonLib, line 2065
        } // library marker kkossev.commonLib, line 2066
        def cmds = zigbee.command(CLUSTER_TUYA, SETTIME, "0008" +zigbee.convertToHexString((int)(now()/1000),8) +  zigbee.convertToHexString((int)((now()+offset)/1000), 8)) // library marker kkossev.commonLib, line 2067
        logDebug "sending time data : ${cmds}" // library marker kkossev.commonLib, line 2068
        cmds.each{ sendHubCommand(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) } // library marker kkossev.commonLib, line 2069
        //if (state.txCounter != null) state.txCounter = state.txCounter + 1 // library marker kkossev.commonLib, line 2070
    } // library marker kkossev.commonLib, line 2071
    else if (descMap?.clusterInt == CLUSTER_TUYA && descMap?.command == "0B") {    // ZCL Command Default Response // library marker kkossev.commonLib, line 2072
        String clusterCmd = descMap?.data[0] // library marker kkossev.commonLib, line 2073
        def status = descMap?.data[1]             // library marker kkossev.commonLib, line 2074
        logDebug "device has received Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data}" // library marker kkossev.commonLib, line 2075
        if (status != "00") { // library marker kkossev.commonLib, line 2076
            logWarn "ATTENTION! manufacturer = ${device.getDataValue("manufacturer")} unsupported Tuya cluster ZCL command 0x${clusterCmd} response 0x${status} data = ${descMap?.data} !!!"                 // library marker kkossev.commonLib, line 2077
        } // library marker kkossev.commonLib, line 2078
    }  // library marker kkossev.commonLib, line 2079
    else if ((descMap?.clusterInt == CLUSTER_TUYA) && (descMap?.command == "01" || descMap?.command == "02" || descMap?.command == "05" || descMap?.command == "06")) // library marker kkossev.commonLib, line 2080
    { // library marker kkossev.commonLib, line 2081
        def dataLen = descMap?.data.size() // library marker kkossev.commonLib, line 2082
        //log.warn "dataLen=${dataLen}" // library marker kkossev.commonLib, line 2083
        def transid = zigbee.convertHexToInt(descMap?.data[1])           // "transid" is just a "counter", a response will have the same transid as the command // library marker kkossev.commonLib, line 2084
        if (dataLen <= 5) { // library marker kkossev.commonLib, line 2085
            logWarn "unprocessed short Tuya command response: dp_id=${descMap?.data[3]} dp=${descMap?.data[2]} fncmd_len=${fncmd_len} data=${descMap?.data})" // library marker kkossev.commonLib, line 2086
            return // library marker kkossev.commonLib, line 2087
        } // library marker kkossev.commonLib, line 2088
        for (int i = 0; i < (dataLen-4); ) { // library marker kkossev.commonLib, line 2089
            def dp = zigbee.convertHexToInt(descMap?.data[2+i])          // "dp" field describes the action/message of a command frame // library marker kkossev.commonLib, line 2090
            def dp_id = zigbee.convertHexToInt(descMap?.data[3+i])       // "dp_identifier" is device dependant // library marker kkossev.commonLib, line 2091
            def fncmd_len = zigbee.convertHexToInt(descMap?.data[5+i])  // library marker kkossev.commonLib, line 2092
            def fncmd = getTuyaAttributeValue(descMap?.data, i)          // // library marker kkossev.commonLib, line 2093
            logDebug "dp_id=${dp_id} dp=${dp} fncmd=${fncmd} fncmd_len=${fncmd_len} (index=${i})" // library marker kkossev.commonLib, line 2094
            processTuyaDP( descMap, dp, dp_id, fncmd) // library marker kkossev.commonLib, line 2095
            i = i + fncmd_len + 4; // library marker kkossev.commonLib, line 2096
        } // library marker kkossev.commonLib, line 2097
    } // library marker kkossev.commonLib, line 2098
    else { // library marker kkossev.commonLib, line 2099
        logWarn "unprocessed Tuya command ${descMap?.command}" // library marker kkossev.commonLib, line 2100
    } // library marker kkossev.commonLib, line 2101
} // library marker kkossev.commonLib, line 2102

void processTuyaDP(descMap, dp, dp_id, fncmd) { // library marker kkossev.commonLib, line 2104
    if (DEVICE_TYPE in ["Radar"])         { processTuyaDpRadar(descMap, dp, dp_id, fncmd); return }     // library marker kkossev.commonLib, line 2105
    if (DEVICE_TYPE in ["Fingerbot"])     { processTuyaDpFingerbot(descMap, dp, dp_id, fncmd); return }     // library marker kkossev.commonLib, line 2106
    switch (dp) { // library marker kkossev.commonLib, line 2107
        case 0x01 : // on/off // library marker kkossev.commonLib, line 2108
            if (DEVICE_TYPE in  ["LightSensor"]) { // library marker kkossev.commonLib, line 2109
                logDebug "LightSensor BrightnessLevel = ${tuyaIlluminanceOpts[fncmd as int]} (${fncmd})" // library marker kkossev.commonLib, line 2110
            } // library marker kkossev.commonLib, line 2111
            else { // library marker kkossev.commonLib, line 2112
                sendSwitchEvent(fncmd) // library marker kkossev.commonLib, line 2113
            } // library marker kkossev.commonLib, line 2114
            break // library marker kkossev.commonLib, line 2115
        case 0x02 : // library marker kkossev.commonLib, line 2116
            if (DEVICE_TYPE in  ["LightSensor"]) { // library marker kkossev.commonLib, line 2117
                handleIlluminanceEvent(fncmd) // library marker kkossev.commonLib, line 2118
            } // library marker kkossev.commonLib, line 2119
            else { // library marker kkossev.commonLib, line 2120
                logDebug "Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"  // library marker kkossev.commonLib, line 2121
            } // library marker kkossev.commonLib, line 2122
            break // library marker kkossev.commonLib, line 2123
        case 0x04 : // battery // library marker kkossev.commonLib, line 2124
            sendBatteryPercentageEvent(fncmd) // library marker kkossev.commonLib, line 2125
            break // library marker kkossev.commonLib, line 2126
        default : // library marker kkossev.commonLib, line 2127
            logWarn "<b>NOT PROCESSED</b> Tuya cmd: dp=${dp} value=${fncmd} descMap.data = ${descMap?.data}"  // library marker kkossev.commonLib, line 2128
            break             // library marker kkossev.commonLib, line 2129
    } // library marker kkossev.commonLib, line 2130
} // library marker kkossev.commonLib, line 2131

private int getTuyaAttributeValue(ArrayList _data, index) { // library marker kkossev.commonLib, line 2133
    int retValue = 0 // library marker kkossev.commonLib, line 2134

    if (_data.size() >= 6) { // library marker kkossev.commonLib, line 2136
        int dataLength = _data[5+index] as Integer // library marker kkossev.commonLib, line 2137
        int power = 1; // library marker kkossev.commonLib, line 2138
        for (i in dataLength..1) { // library marker kkossev.commonLib, line 2139
            retValue = retValue + power * zigbee.convertHexToInt(_data[index+i+5]) // library marker kkossev.commonLib, line 2140
            power = power * 256 // library marker kkossev.commonLib, line 2141
        } // library marker kkossev.commonLib, line 2142
    } // library marker kkossev.commonLib, line 2143
    return retValue // library marker kkossev.commonLib, line 2144
} // library marker kkossev.commonLib, line 2145


private sendTuyaCommand(dp, dp_type, fncmd) { // library marker kkossev.commonLib, line 2148
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2149
    def ep = safeToInt(state.destinationEP) // library marker kkossev.commonLib, line 2150
    if (ep==null || ep==0) ep = 1 // library marker kkossev.commonLib, line 2151
    def tuyaCmd = isFingerbot() ? 0x04 : SETDATA // library marker kkossev.commonLib, line 2152

    cmds += zigbee.command(CLUSTER_TUYA, tuyaCmd, [destEndpoint :ep], PACKET_ID + dp + dp_type + zigbee.convertToHexString((int)(fncmd.length()/2), 4) + fncmd ) // library marker kkossev.commonLib, line 2154
    logDebug "${device.displayName} sendTuyaCommand = ${cmds}" // library marker kkossev.commonLib, line 2155
    return cmds // library marker kkossev.commonLib, line 2156
} // library marker kkossev.commonLib, line 2157

private getPACKET_ID() { // library marker kkossev.commonLib, line 2159
    return zigbee.convertToHexString(new Random().nextInt(65536), 4)  // library marker kkossev.commonLib, line 2160
} // library marker kkossev.commonLib, line 2161

def tuyaTest( dpCommand, dpValue, dpTypeString ) { // library marker kkossev.commonLib, line 2163
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2164
    def dpType   = dpTypeString=="DP_TYPE_VALUE" ? DP_TYPE_VALUE : dpTypeString=="DP_TYPE_BOOL" ? DP_TYPE_BOOL : dpTypeString=="DP_TYPE_ENUM" ? DP_TYPE_ENUM : null // library marker kkossev.commonLib, line 2165
    def dpValHex = dpTypeString=="DP_TYPE_VALUE" ? zigbee.convertToHexString(dpValue as int, 8) : dpValue // library marker kkossev.commonLib, line 2166

    if (settings?.logEnable) log.warn "${device.displayName}  sending TEST command=${dpCommand} value=${dpValue} ($dpValHex) type=${dpType}" // library marker kkossev.commonLib, line 2168

    sendZigbeeCommands( sendTuyaCommand(dpCommand, dpType, dpValHex) ) // library marker kkossev.commonLib, line 2170
} // library marker kkossev.commonLib, line 2171

private getANALOG_INPUT_BASIC_CLUSTER() { 0x000C } // library marker kkossev.commonLib, line 2173
private getANALOG_INPUT_BASIC_PRESENT_VALUE_ATTRIBUTE() { 0x0055 } // library marker kkossev.commonLib, line 2174

def tuyaBlackMagic() { // library marker kkossev.commonLib, line 2176
    def ep = safeToInt(state.destinationEP ?: 01) // library marker kkossev.commonLib, line 2177
    if (ep==null || ep==0) ep = 1 // library marker kkossev.commonLib, line 2178
    return zigbee.readAttribute(0x0000, [0x0004, 0x000, 0x0001, 0x0005, 0x0007, 0xfffe], [destEndpoint :ep], delay=200) // library marker kkossev.commonLib, line 2179
} // library marker kkossev.commonLib, line 2180

void aqaraBlackMagic() { // library marker kkossev.commonLib, line 2182
    List<String> cmds = [] // library marker kkossev.commonLib, line 2183
    if (isAqaraTVOC() || isAqaraTRV()) { // library marker kkossev.commonLib, line 2184
        cmds += ["he raw 0x${device.deviceNetworkId} 0 0 0x8002 {40 00 00 00 00 40 8f 5f 11 52 52 00 41 2c 52 00 00} {0x0000}", "delay 200",] // library marker kkossev.commonLib, line 2185
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0xFCC0 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2186
        cmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0406 {${device.zigbeeId}} {}" // library marker kkossev.commonLib, line 2187
        cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)    // TODO: check - battery voltage // library marker kkossev.commonLib, line 2188
        if (isAqaraTVOC()) { // library marker kkossev.commonLib, line 2189
            cmds += zigbee.readAttribute(0xFCC0, [0x0102, 0x010C], [mfgCode: 0x115F], delay=200)    // TVOC only // library marker kkossev.commonLib, line 2190
        } // library marker kkossev.commonLib, line 2191
        sendZigbeeCommands( cmds ) // library marker kkossev.commonLib, line 2192
        logDebug "sent aqaraBlackMagic()" // library marker kkossev.commonLib, line 2193
    } // library marker kkossev.commonLib, line 2194
    else { // library marker kkossev.commonLib, line 2195
        logDebug "aqaraBlackMagic() was SKIPPED" // library marker kkossev.commonLib, line 2196
    } // library marker kkossev.commonLib, line 2197
} // library marker kkossev.commonLib, line 2198


/** // library marker kkossev.commonLib, line 2201
 * initializes the device // library marker kkossev.commonLib, line 2202
 * Invoked from configure() // library marker kkossev.commonLib, line 2203
 * @return zigbee commands // library marker kkossev.commonLib, line 2204
 */ // library marker kkossev.commonLib, line 2205
def initializeDevice() { // library marker kkossev.commonLib, line 2206
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2207
    logInfo 'initializeDevice...' // library marker kkossev.commonLib, line 2208

    // start with the device-specific initialization first. // library marker kkossev.commonLib, line 2210
    if (DEVICE_TYPE in  ["AirQuality"])          { return initializeDeviceAirQuality() } // library marker kkossev.commonLib, line 2211
    else if (DEVICE_TYPE in  ["IRBlaster"])      { return initializeDeviceIrBlaster() } // library marker kkossev.commonLib, line 2212
    else if (DEVICE_TYPE in  ["Radar"])          { return initializeDeviceRadar() } // library marker kkossev.commonLib, line 2213
    else if (DEVICE_TYPE in  ["ButtonDimmer"])   { return initializeDeviceButtonDimmer() } // library marker kkossev.commonLib, line 2214


    // not specific device type - do some generic initializations // library marker kkossev.commonLib, line 2217
    if (DEVICE_TYPE in  ["THSensor"]) { // library marker kkossev.commonLib, line 2218
        cmds += zigbee.configureReporting(zigbee.TEMPERATURE_MEASUREMENT_CLUSTER, 0 /*TEMPERATURE_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.INT16, 15, 300, 100 /* 100=0.1도*/)                // 402 - temperature // library marker kkossev.commonLib, line 2219
        cmds += zigbee.configureReporting(zigbee.RELATIVE_HUMIDITY_MEASUREMENT_CLUSTER, 0 /*RALATIVE_HUMIDITY_MEASUREMENT_MEASURED_VALUE_ATTRIBUTE*/, DataType.UINT16, 15, 300, 400/*10/100=0.4%*/)   // 405 - humidity // library marker kkossev.commonLib, line 2220
    } // library marker kkossev.commonLib, line 2221
    // // library marker kkossev.commonLib, line 2222
    if (cmds == []) { // library marker kkossev.commonLib, line 2223
        cmds = ["delay 299"] // library marker kkossev.commonLib, line 2224
    } // library marker kkossev.commonLib, line 2225
    return cmds // library marker kkossev.commonLib, line 2226
} // library marker kkossev.commonLib, line 2227


/** // library marker kkossev.commonLib, line 2230
 * configures the device // library marker kkossev.commonLib, line 2231
 * Invoked from updated() // library marker kkossev.commonLib, line 2232
 * @return zigbee commands // library marker kkossev.commonLib, line 2233
 */ // library marker kkossev.commonLib, line 2234
def configureDevice() { // library marker kkossev.commonLib, line 2235
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2236
    logInfo 'configureDevice...' // library marker kkossev.commonLib, line 2237

    if (DEVICE_TYPE in  ["AirQuality"]) { cmds += configureDeviceAirQuality() } // library marker kkossev.commonLib, line 2239
    else if (DEVICE_TYPE in  ["Fingerbot"])  { cmds += configureDeviceFingerbot() } // library marker kkossev.commonLib, line 2240
    else if (DEVICE_TYPE in  ["AqaraCube"])  { cmds += configureDeviceAqaraCube() } // library marker kkossev.commonLib, line 2241
    else if (DEVICE_TYPE in  ["Switch"])     { cmds += configureDeviceSwitch() } // library marker kkossev.commonLib, line 2242
    else if (DEVICE_TYPE in  ["IRBlaster"])  { cmds += configureDeviceIrBlaster() } // library marker kkossev.commonLib, line 2243
    else if (DEVICE_TYPE in  ["Radar"])      { cmds += configureDeviceRadar() } // library marker kkossev.commonLib, line 2244
    else if (DEVICE_TYPE in  ["ButtonDimmer"]) { cmds += configureDeviceButtonDimmer() } // library marker kkossev.commonLib, line 2245
    else if (DEVICE_TYPE in  ["Bulb"])       { cmds += configureBulb() } // library marker kkossev.commonLib, line 2246
    if (cmds == []) {  // library marker kkossev.commonLib, line 2247
        cmds = ["delay 277",] // library marker kkossev.commonLib, line 2248
    } // library marker kkossev.commonLib, line 2249
    sendZigbeeCommands(cmds)   // library marker kkossev.commonLib, line 2250
} // library marker kkossev.commonLib, line 2251

/* // library marker kkossev.commonLib, line 2253
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2254
 * Hubitat default handlers methods // library marker kkossev.commonLib, line 2255
 * ----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2256
*/ // library marker kkossev.commonLib, line 2257

def refresh() { // library marker kkossev.commonLib, line 2259
    logInfo "refresh()... DEVICE_TYPE is ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2260
    checkDriverVersion() // library marker kkossev.commonLib, line 2261
    List<String> cmds = [] // library marker kkossev.commonLib, line 2262
    if (state.states == null) state.states = [:] // library marker kkossev.commonLib, line 2263
    state.states["isRefresh"] = true // library marker kkossev.commonLib, line 2264

    // device type specific refresh handlers // library marker kkossev.commonLib, line 2266
    if (DEVICE_TYPE in  ["AqaraCube"])       { cmds += refreshAqaraCube() } // library marker kkossev.commonLib, line 2267
    else if (DEVICE_TYPE in  ["Fingerbot"])  { cmds += refreshFingerbot() } // library marker kkossev.commonLib, line 2268
    else if (DEVICE_TYPE in  ["AirQuality"]) { cmds += refreshAirQuality() } // library marker kkossev.commonLib, line 2269
    else if (DEVICE_TYPE in  ["Switch"])     { cmds += refreshSwitch() } // library marker kkossev.commonLib, line 2270
    else if (DEVICE_TYPE in  ["IRBlaster"])  { cmds += refreshIrBlaster() } // library marker kkossev.commonLib, line 2271
    else if (DEVICE_TYPE in  ["Radar"])      { cmds += refreshRadar() } // library marker kkossev.commonLib, line 2272
    else if (DEVICE_TYPE in  ["Thermostat"]) { cmds += refreshThermostat() } // library marker kkossev.commonLib, line 2273
    else if (DEVICE_TYPE in  ["Bulb"])       { cmds += refreshBulb() } // library marker kkossev.commonLib, line 2274
    else { // library marker kkossev.commonLib, line 2275
        // generic refresh handling, based on teh device capabilities  // library marker kkossev.commonLib, line 2276
        if (device.hasCapability("Battery")) { // library marker kkossev.commonLib, line 2277
            cmds += zigbee.readAttribute(0x0001, 0x0020, [:], delay=200)         // battery voltage // library marker kkossev.commonLib, line 2278
            cmds += zigbee.readAttribute(0x0001, 0x0021, [:], delay=200)         // battery percentage  // library marker kkossev.commonLib, line 2279
        } // library marker kkossev.commonLib, line 2280
        if (DEVICE_TYPE in  ["Plug", "Dimmer"]) { // library marker kkossev.commonLib, line 2281
            cmds += zigbee.readAttribute(0x0006, 0x0000, [:], delay=200) // library marker kkossev.commonLib, line 2282
            cmds += zigbee.command(zigbee.GROUPS_CLUSTER, 0x02, [:], DELAY_MS, '00')            // Get group membership // library marker kkossev.commonLib, line 2283
        } // library marker kkossev.commonLib, line 2284
        if (DEVICE_TYPE in  ["Dimmer"]) { // library marker kkossev.commonLib, line 2285
            cmds += zigbee.readAttribute(0x0008, 0x0000, [:], delay=200)         // library marker kkossev.commonLib, line 2286
        } // library marker kkossev.commonLib, line 2287
        if (DEVICE_TYPE in  ["THSensor", "AirQuality"]) { // library marker kkossev.commonLib, line 2288
            cmds += zigbee.readAttribute(0x0402, 0x0000, [:], delay=200)         // library marker kkossev.commonLib, line 2289
            cmds += zigbee.readAttribute(0x0405, 0x0000, [:], delay=200)         // library marker kkossev.commonLib, line 2290
        } // library marker kkossev.commonLib, line 2291
    } // library marker kkossev.commonLib, line 2292

    runInMillis( REFRESH_TIMER, clearRefreshRequest, [overwrite: true])                 // 3 seconds // library marker kkossev.commonLib, line 2294
    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2295
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2296
    } // library marker kkossev.commonLib, line 2297
    else { // library marker kkossev.commonLib, line 2298
        logDebug "no refresh() commands defined for device type ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2299
    } // library marker kkossev.commonLib, line 2300
} // library marker kkossev.commonLib, line 2301

def clearRefreshRequest() { if (state.states == null) {state.states = [:] }; state.states["isRefresh"] = false } // library marker kkossev.commonLib, line 2303

void clearInfoEvent() { // library marker kkossev.commonLib, line 2305
    sendInfoEvent('clear') // library marker kkossev.commonLib, line 2306
} // library marker kkossev.commonLib, line 2307

void sendInfoEvent(String info=null) { // library marker kkossev.commonLib, line 2309
    if (info == null || info == "clear") { // library marker kkossev.commonLib, line 2310
        logDebug "clearing the Info event" // library marker kkossev.commonLib, line 2311
        sendEvent(name: "Info", value: " ", isDigital: true) // library marker kkossev.commonLib, line 2312
    } // library marker kkossev.commonLib, line 2313
    else { // library marker kkossev.commonLib, line 2314
        logInfo "${info}" // library marker kkossev.commonLib, line 2315
        sendEvent(name: "Info", value: info, isDigital: true) // library marker kkossev.commonLib, line 2316
        runIn(INFO_AUTO_CLEAR_PERIOD, "clearInfoEvent")            // automatically clear the Info attribute after 1 minute // library marker kkossev.commonLib, line 2317
    } // library marker kkossev.commonLib, line 2318
} // library marker kkossev.commonLib, line 2319

def ping() { // library marker kkossev.commonLib, line 2321
    if (!(isAqaraTVOC())) { // library marker kkossev.commonLib, line 2322
        if (state.lastTx == nill ) state.lastTx = [:]  // library marker kkossev.commonLib, line 2323
        state.lastTx["pingTime"] = new Date().getTime() // library marker kkossev.commonLib, line 2324
        if (state.states == nill ) state.states = [:]  // library marker kkossev.commonLib, line 2325
        state.states["isPing"] = true // library marker kkossev.commonLib, line 2326
        scheduleCommandTimeoutCheck() // library marker kkossev.commonLib, line 2327
        sendZigbeeCommands( zigbee.readAttribute(zigbee.BASIC_CLUSTER, 0x01, [:], 0) ) // library marker kkossev.commonLib, line 2328
        logDebug 'ping...' // library marker kkossev.commonLib, line 2329
    } // library marker kkossev.commonLib, line 2330
    else { // library marker kkossev.commonLib, line 2331
        // Aqara TVOC is sleepy or does not respond to the ping. // library marker kkossev.commonLib, line 2332
        logInfo "ping() command is not available for this sleepy device." // library marker kkossev.commonLib, line 2333
        sendRttEvent("n/a") // library marker kkossev.commonLib, line 2334
    } // library marker kkossev.commonLib, line 2335
} // library marker kkossev.commonLib, line 2336

/** // library marker kkossev.commonLib, line 2338
 * sends 'rtt'event (after a ping() command) // library marker kkossev.commonLib, line 2339
 * @param null: calculate the RTT in ms // library marker kkossev.commonLib, line 2340
 *        value: send the text instead ('timeout', 'n/a', etc..) // library marker kkossev.commonLib, line 2341
 * @return none // library marker kkossev.commonLib, line 2342
 */ // library marker kkossev.commonLib, line 2343
void sendRttEvent( String value=null) { // library marker kkossev.commonLib, line 2344
    def now = new Date().getTime() // library marker kkossev.commonLib, line 2345
    if (state.lastTx == null ) state.lastTx = [:] // library marker kkossev.commonLib, line 2346
    def timeRunning = now.toInteger() - (state.lastTx["pingTime"] ?: now).toInteger() // library marker kkossev.commonLib, line 2347
    def descriptionText = "Round-trip time is ${timeRunning} ms (min=${state.stats["pingsMin"]} max=${state.stats["pingsMax"]} average=${state.stats["pingsAvg"]})" // library marker kkossev.commonLib, line 2348
    if (value == null) { // library marker kkossev.commonLib, line 2349
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2350
        sendEvent(name: "rtt", value: timeRunning, descriptionText: descriptionText, unit: "ms", isDigital: true)     // library marker kkossev.commonLib, line 2351
    } // library marker kkossev.commonLib, line 2352
    else { // library marker kkossev.commonLib, line 2353
        descriptionText = "Round-trip time : ${value}" // library marker kkossev.commonLib, line 2354
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2355
        sendEvent(name: "rtt", value: value, descriptionText: descriptionText, isDigital: true)     // library marker kkossev.commonLib, line 2356
    } // library marker kkossev.commonLib, line 2357
} // library marker kkossev.commonLib, line 2358

/** // library marker kkossev.commonLib, line 2360
 * Lookup the cluster name from the cluster ID // library marker kkossev.commonLib, line 2361
 * @param cluster cluster ID // library marker kkossev.commonLib, line 2362
 * @return cluster name if known, otherwise "private cluster" // library marker kkossev.commonLib, line 2363
 */ // library marker kkossev.commonLib, line 2364
private String clusterLookup(final Object cluster) { // library marker kkossev.commonLib, line 2365
    if (cluster != null) { // library marker kkossev.commonLib, line 2366
        return zigbee.clusterLookup(cluster.toInteger()) ?: "private cluster 0x${intToHexStr(cluster.toInteger())}" // library marker kkossev.commonLib, line 2367
    } // library marker kkossev.commonLib, line 2368
    else { // library marker kkossev.commonLib, line 2369
        logWarn "cluster is NULL!" // library marker kkossev.commonLib, line 2370
        return "NULL" // library marker kkossev.commonLib, line 2371
    } // library marker kkossev.commonLib, line 2372
} // library marker kkossev.commonLib, line 2373

private void scheduleCommandTimeoutCheck(int delay = COMMAND_TIMEOUT) { // library marker kkossev.commonLib, line 2375
    runIn(delay, 'deviceCommandTimeout') // library marker kkossev.commonLib, line 2376
} // library marker kkossev.commonLib, line 2377

void deviceCommandTimeout() { // library marker kkossev.commonLib, line 2379
    logWarn 'no response received (sleepy device or offline?)' // library marker kkossev.commonLib, line 2380
    sendRttEvent("timeout") // library marker kkossev.commonLib, line 2381
    state.stats['pingsFail'] = (state.stats['pingsFail'] ?: 0) + 1 // library marker kkossev.commonLib, line 2382
} // library marker kkossev.commonLib, line 2383

/** // library marker kkossev.commonLib, line 2385
 * Schedule a device health check // library marker kkossev.commonLib, line 2386
 * @param intervalMins interval in minutes // library marker kkossev.commonLib, line 2387
 */ // library marker kkossev.commonLib, line 2388
private void scheduleDeviceHealthCheck(final int intervalMins, final int healthMethod) { // library marker kkossev.commonLib, line 2389
    if (healthMethod == 1 || healthMethod == 2)  { // library marker kkossev.commonLib, line 2390
        String cron = getCron( intervalMins*60 ) // library marker kkossev.commonLib, line 2391
        schedule(cron, 'deviceHealthCheck') // library marker kkossev.commonLib, line 2392
        logDebug "deviceHealthCheck is scheduled every ${intervalMins} minutes" // library marker kkossev.commonLib, line 2393
    } // library marker kkossev.commonLib, line 2394
    else { // library marker kkossev.commonLib, line 2395
        logWarn "deviceHealthCheck is not scheduled!" // library marker kkossev.commonLib, line 2396
        unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2397
    } // library marker kkossev.commonLib, line 2398
} // library marker kkossev.commonLib, line 2399

private void unScheduleDeviceHealthCheck() { // library marker kkossev.commonLib, line 2401
    unschedule('deviceHealthCheck') // library marker kkossev.commonLib, line 2402
    device.deleteCurrentState('healthStatus') // library marker kkossev.commonLib, line 2403
    logWarn "device health check is disabled!" // library marker kkossev.commonLib, line 2404

} // library marker kkossev.commonLib, line 2406

// called when any event was received from the Zigbee device in the parse() method. // library marker kkossev.commonLib, line 2408
void setHealthStatusOnline() { // library marker kkossev.commonLib, line 2409
    if(state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2410
    state.health['checkCtr3']  = 0 // library marker kkossev.commonLib, line 2411
    if (!((device.currentValue('healthStatus') ?: 'unknown') in ['online'])) {    // library marker kkossev.commonLib, line 2412
        sendHealthStatusEvent('online') // library marker kkossev.commonLib, line 2413
        logInfo "is now online!" // library marker kkossev.commonLib, line 2414
    } // library marker kkossev.commonLib, line 2415
} // library marker kkossev.commonLib, line 2416


def deviceHealthCheck() { // library marker kkossev.commonLib, line 2419
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2420
    def ctr = state.health['checkCtr3'] ?: 0 // library marker kkossev.commonLib, line 2421
    if (ctr  >= PRESENCE_COUNT_THRESHOLD) { // library marker kkossev.commonLib, line 2422
        if ((device.currentValue("healthStatus") ?: 'unknown') != 'offline' ) { // library marker kkossev.commonLib, line 2423
            logWarn "not present!" // library marker kkossev.commonLib, line 2424
            sendHealthStatusEvent('offline') // library marker kkossev.commonLib, line 2425
        } // library marker kkossev.commonLib, line 2426
    } // library marker kkossev.commonLib, line 2427
    else { // library marker kkossev.commonLib, line 2428
        logDebug "deviceHealthCheck - online (notPresentCounter=${ctr})" // library marker kkossev.commonLib, line 2429
    } // library marker kkossev.commonLib, line 2430
    state.health['checkCtr3'] = ctr + 1 // library marker kkossev.commonLib, line 2431
} // library marker kkossev.commonLib, line 2432

void sendHealthStatusEvent(value) { // library marker kkossev.commonLib, line 2434
    def descriptionText = "healthStatus changed to ${value}" // library marker kkossev.commonLib, line 2435
    sendEvent(name: "healthStatus", value: value, descriptionText: descriptionText, isStateChange: true, isDigital: true) // library marker kkossev.commonLib, line 2436
    if (value == 'online') { // library marker kkossev.commonLib, line 2437
        logInfo "${descriptionText}" // library marker kkossev.commonLib, line 2438
    } // library marker kkossev.commonLib, line 2439
    else { // library marker kkossev.commonLib, line 2440
        if (settings?.txtEnable) { log.warn "${device.displayName}} <b>${descriptionText}</b>" } // library marker kkossev.commonLib, line 2441
    } // library marker kkossev.commonLib, line 2442
} // library marker kkossev.commonLib, line 2443



/** // library marker kkossev.commonLib, line 2447
 * Scheduled job for polling device specific attribute(s) // library marker kkossev.commonLib, line 2448
 */ // library marker kkossev.commonLib, line 2449
void autoPoll() { // library marker kkossev.commonLib, line 2450
    logDebug "autoPoll()..." // library marker kkossev.commonLib, line 2451
    checkDriverVersion() // library marker kkossev.commonLib, line 2452
    List<String> cmds = [] // library marker kkossev.commonLib, line 2453
    if (state.states == null) state.states = [:] // library marker kkossev.commonLib, line 2454
    //state.states["isRefresh"] = true // library marker kkossev.commonLib, line 2455

    if (DEVICE_TYPE in  ["AirQuality"]) { // library marker kkossev.commonLib, line 2457
        cmds += zigbee.readAttribute(0xfc7e, 0x0000, [mfgCode: 0x117c], delay=200)      // tVOC   !! mfcode="0x117c" !! attributes: (float) 0: Measured Value; 1: Min Measured Value; 2:Max Measured Value; // library marker kkossev.commonLib, line 2458
    } // library marker kkossev.commonLib, line 2459

    if (cmds != null && cmds != [] ) { // library marker kkossev.commonLib, line 2461
        sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2462
    }     // library marker kkossev.commonLib, line 2463
} // library marker kkossev.commonLib, line 2464


/** // library marker kkossev.commonLib, line 2467
 * Invoked by Hubitat when the driver configuration is updated // library marker kkossev.commonLib, line 2468
 */ // library marker kkossev.commonLib, line 2469
void updated() { // library marker kkossev.commonLib, line 2470
    logInfo 'updated...' // library marker kkossev.commonLib, line 2471
    logInfo"driver version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2472
    unschedule() // library marker kkossev.commonLib, line 2473

    if (settings.logEnable) { // library marker kkossev.commonLib, line 2475
        logDebug settings // library marker kkossev.commonLib, line 2476
        runIn(86400, logsOff) // library marker kkossev.commonLib, line 2477
    } // library marker kkossev.commonLib, line 2478
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 2479
        logDebug settings // library marker kkossev.commonLib, line 2480
        runIn(1800, traceOff) // library marker kkossev.commonLib, line 2481
    }     // library marker kkossev.commonLib, line 2482

    final int healthMethod = (settings.healthCheckMethod as Integer) ?: 0 // library marker kkossev.commonLib, line 2484
    if (healthMethod == 1 || healthMethod == 2) {                            //    [0: 'Disabled', 1: 'Activity check', 2: 'Periodic polling'] // library marker kkossev.commonLib, line 2485
        // schedule the periodic timer // library marker kkossev.commonLib, line 2486
        final int interval = (settings.healthCheckInterval as Integer) ?: 0 // library marker kkossev.commonLib, line 2487
        if (interval > 0) { // library marker kkossev.commonLib, line 2488
            //log.trace "healthMethod=${healthMethod} interval=${interval}" // library marker kkossev.commonLib, line 2489
            log.info "scheduling health check every ${interval} minutes by ${HealthcheckMethodOpts.options[healthCheckMethod as int]} method" // library marker kkossev.commonLib, line 2490
            scheduleDeviceHealthCheck(interval, healthMethod) // library marker kkossev.commonLib, line 2491
        } // library marker kkossev.commonLib, line 2492
    } // library marker kkossev.commonLib, line 2493
    else { // library marker kkossev.commonLib, line 2494
        unScheduleDeviceHealthCheck()        // unschedule the periodic job, depending on the healthMethod // library marker kkossev.commonLib, line 2495
        log.info "Health Check is disabled!" // library marker kkossev.commonLib, line 2496
    } // library marker kkossev.commonLib, line 2497

    if (DEVICE_TYPE in ["AirQuality"])  { updatedAirQuality() } // library marker kkossev.commonLib, line 2499
    if (DEVICE_TYPE in ["IRBlaster"])   { updatedIrBlaster() } // library marker kkossev.commonLib, line 2500
    if (DEVICE_TYPE in ["Thermostat"])  { updatedThermostat() } // library marker kkossev.commonLib, line 2501

    configureDevice()    // sends Zigbee commands // library marker kkossev.commonLib, line 2503

    sendInfoEvent("updated") // library marker kkossev.commonLib, line 2505
} // library marker kkossev.commonLib, line 2506

/** // library marker kkossev.commonLib, line 2508
 * Disable logging (for debugging) // library marker kkossev.commonLib, line 2509
 */ // library marker kkossev.commonLib, line 2510
void logsOff() { // library marker kkossev.commonLib, line 2511
    logInfo "debug logging disabled..." // library marker kkossev.commonLib, line 2512
    device.updateSetting('logEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2513
} // library marker kkossev.commonLib, line 2514
void traceOff() { // library marker kkossev.commonLib, line 2515
    logInfo "trace logging disabled..." // library marker kkossev.commonLib, line 2516
    device.updateSetting('traceEnable', [value: 'false', type: 'bool']) // library marker kkossev.commonLib, line 2517
} // library marker kkossev.commonLib, line 2518

def configure(command) { // library marker kkossev.commonLib, line 2520
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2521
    logInfo "configure(${command})..." // library marker kkossev.commonLib, line 2522

    Boolean validated = false // library marker kkossev.commonLib, line 2524
    if (!(command in (ConfigureOpts.keySet() as List))) { // library marker kkossev.commonLib, line 2525
        logWarn "configure: command <b>${command}</b> must be one of these : ${ConfigureOpts.keySet() as List}" // library marker kkossev.commonLib, line 2526
        return // library marker kkossev.commonLib, line 2527
    } // library marker kkossev.commonLib, line 2528
    // // library marker kkossev.commonLib, line 2529
    def func // library marker kkossev.commonLib, line 2530
   // try { // library marker kkossev.commonLib, line 2531
        func = ConfigureOpts[command]?.function // library marker kkossev.commonLib, line 2532
        cmds = "$func"() // library marker kkossev.commonLib, line 2533
 //   } // library marker kkossev.commonLib, line 2534
//    catch (e) { // library marker kkossev.commonLib, line 2535
//        logWarn "Exception ${e} caught while processing <b>$func</b>(<b>$value</b>)" // library marker kkossev.commonLib, line 2536
//        return // library marker kkossev.commonLib, line 2537
//    } // library marker kkossev.commonLib, line 2538

    logInfo "executed '${func}'" // library marker kkossev.commonLib, line 2540
} // library marker kkossev.commonLib, line 2541

def configureHelp( val ) { // library marker kkossev.commonLib, line 2543
    if (settings?.txtEnable) { log.warn "${device.displayName} configureHelp: select one of the commands in this list!" } // library marker kkossev.commonLib, line 2544
} // library marker kkossev.commonLib, line 2545

def loadAllDefaults() { // library marker kkossev.commonLib, line 2547
    logWarn "loadAllDefaults() !!!" // library marker kkossev.commonLib, line 2548
    deleteAllSettings() // library marker kkossev.commonLib, line 2549
    deleteAllCurrentStates() // library marker kkossev.commonLib, line 2550
    deleteAllScheduledJobs() // library marker kkossev.commonLib, line 2551
    deleteAllStates() // library marker kkossev.commonLib, line 2552
    deleteAllChildDevices() // library marker kkossev.commonLib, line 2553
    initialize() // library marker kkossev.commonLib, line 2554
    configure() // library marker kkossev.commonLib, line 2555
    updated() // calls  also   configureDevice() // library marker kkossev.commonLib, line 2556
    sendInfoEvent("All Defaults Loaded!") // library marker kkossev.commonLib, line 2557
} // library marker kkossev.commonLib, line 2558

/** // library marker kkossev.commonLib, line 2560
 * Send configuration parameters to the device // library marker kkossev.commonLib, line 2561
 * Invoked when device is first installed and when the user updates the configuration // library marker kkossev.commonLib, line 2562
 * @return sends zigbee commands // library marker kkossev.commonLib, line 2563
 */ // library marker kkossev.commonLib, line 2564
def configure() { // library marker kkossev.commonLib, line 2565
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2566
    logInfo 'configure...' // library marker kkossev.commonLib, line 2567
    logDebug settings // library marker kkossev.commonLib, line 2568
    cmds += tuyaBlackMagic() // library marker kkossev.commonLib, line 2569
    if (isAqaraTVOC() || isAqaraTRV()) { // library marker kkossev.commonLib, line 2570
        aqaraBlackMagic() // library marker kkossev.commonLib, line 2571
    } // library marker kkossev.commonLib, line 2572
    cmds += initializeDevice() // library marker kkossev.commonLib, line 2573
    cmds += configureDevice() // library marker kkossev.commonLib, line 2574
    sendZigbeeCommands(cmds) // library marker kkossev.commonLib, line 2575
    sendInfoEvent("sent device configuration") // library marker kkossev.commonLib, line 2576
} // library marker kkossev.commonLib, line 2577

/** // library marker kkossev.commonLib, line 2579
 * Invoked by Hubitat when driver is installed // library marker kkossev.commonLib, line 2580
 */ // library marker kkossev.commonLib, line 2581
void installed() { // library marker kkossev.commonLib, line 2582
    logInfo 'installed...' // library marker kkossev.commonLib, line 2583
    // populate some default values for attributes // library marker kkossev.commonLib, line 2584
    sendEvent(name: 'healthStatus', value: 'unknown') // library marker kkossev.commonLib, line 2585
    sendEvent(name: 'powerSource', value: 'unknown') // library marker kkossev.commonLib, line 2586
    sendInfoEvent("installed") // library marker kkossev.commonLib, line 2587
    runIn(3, 'updated') // library marker kkossev.commonLib, line 2588
} // library marker kkossev.commonLib, line 2589

/** // library marker kkossev.commonLib, line 2591
 * Invoked when initialize button is clicked // library marker kkossev.commonLib, line 2592
 */ // library marker kkossev.commonLib, line 2593
void initialize() { // library marker kkossev.commonLib, line 2594
    logInfo 'initialize...' // library marker kkossev.commonLib, line 2595
    initializeVars(fullInit = true) // library marker kkossev.commonLib, line 2596
    updateTuyaVersion() // library marker kkossev.commonLib, line 2597
    updateAqaraVersion() // library marker kkossev.commonLib, line 2598
} // library marker kkossev.commonLib, line 2599


/* // library marker kkossev.commonLib, line 2602
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2603
 * kkossev drivers commonly used functions // library marker kkossev.commonLib, line 2604
 *----------------------------------------------------------------------------- // library marker kkossev.commonLib, line 2605
*/ // library marker kkossev.commonLib, line 2606

static Integer safeToInt(val, Integer defaultVal=0) { // library marker kkossev.commonLib, line 2608
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal // library marker kkossev.commonLib, line 2609
} // library marker kkossev.commonLib, line 2610

static Double safeToDouble(val, Double defaultVal=0.0) { // library marker kkossev.commonLib, line 2612
    return "${val}"?.isDouble() ? "${val}".toDouble() : defaultVal // library marker kkossev.commonLib, line 2613
} // library marker kkossev.commonLib, line 2614

void sendZigbeeCommands(ArrayList<String> cmd) { // library marker kkossev.commonLib, line 2616
    logDebug "sendZigbeeCommands(cmd=$cmd)" // library marker kkossev.commonLib, line 2617
    hubitat.device.HubMultiAction allActions = new hubitat.device.HubMultiAction() // library marker kkossev.commonLib, line 2618
    cmd.each { // library marker kkossev.commonLib, line 2619
            allActions.add(new hubitat.device.HubAction(it, hubitat.device.Protocol.ZIGBEE)) // library marker kkossev.commonLib, line 2620
            if (state.stats != null) { state.stats['txCtr'] = (state.stats['txCtr'] ?: 0) + 1 } else { state.stats=[:] } // library marker kkossev.commonLib, line 2621
    } // library marker kkossev.commonLib, line 2622
    if (state.lastTx != null) { state.lastTx['cmdTime'] = now() } else { state.lastTx = [:] } // library marker kkossev.commonLib, line 2623
    sendHubCommand(allActions) // library marker kkossev.commonLib, line 2624
} // library marker kkossev.commonLib, line 2625

def driverVersionAndTimeStamp() { version() + ' ' + timeStamp() + ((_DEBUG) ? " (debug version!) " : " ") + "(${device.getDataValue('model')} ${device.getDataValue('manufacturer')}) (${getModel()} ${location.hub.firmwareVersionString}) "} // library marker kkossev.commonLib, line 2627

def getDeviceInfo() { // library marker kkossev.commonLib, line 2629
    return "model=${device.getDataValue('model')} manufacturer=${device.getDataValue('manufacturer')} destinationEP=${state.destinationEP ?: UNKNOWN} <b>deviceProfile=${state.deviceProfile ?: UNKNOWN}</b>" // library marker kkossev.commonLib, line 2630
} // library marker kkossev.commonLib, line 2631

def getDestinationEP() {    // [destEndpoint:safeToInt(getDestinationEP())] // library marker kkossev.commonLib, line 2633
    return state.destinationEP ?: device.endpointId ?: "01" // library marker kkossev.commonLib, line 2634
} // library marker kkossev.commonLib, line 2635

def checkDriverVersion() { // library marker kkossev.commonLib, line 2637
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) { // library marker kkossev.commonLib, line 2638
        logDebug "updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}" // library marker kkossev.commonLib, line 2639
        sendInfoEvent("Updated to version ${driverVersionAndTimeStamp()}") // library marker kkossev.commonLib, line 2640
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2641
        initializeVars(fullInit = false) // library marker kkossev.commonLib, line 2642
        updateTuyaVersion() // library marker kkossev.commonLib, line 2643
        updateAqaraVersion() // library marker kkossev.commonLib, line 2644
    } // library marker kkossev.commonLib, line 2645
    else { // library marker kkossev.commonLib, line 2646
        // no driver version change // library marker kkossev.commonLib, line 2647
    } // library marker kkossev.commonLib, line 2648
} // library marker kkossev.commonLib, line 2649

// credits @thebearmay // library marker kkossev.commonLib, line 2651
String getModel(){ // library marker kkossev.commonLib, line 2652
    try{ // library marker kkossev.commonLib, line 2653
        String model = getHubVersion() // requires >=2.2.8.141 // library marker kkossev.commonLib, line 2654
    } catch (ignore){ // library marker kkossev.commonLib, line 2655
        try{ // library marker kkossev.commonLib, line 2656
            httpGet("http://${location.hub.localIP}:8080/api/hubitat.xml") { res -> // library marker kkossev.commonLib, line 2657
                model = res.data.device.modelName // library marker kkossev.commonLib, line 2658
            return model // library marker kkossev.commonLib, line 2659
            }         // library marker kkossev.commonLib, line 2660
        } catch(ignore_again) { // library marker kkossev.commonLib, line 2661
            return "" // library marker kkossev.commonLib, line 2662
        } // library marker kkossev.commonLib, line 2663
    } // library marker kkossev.commonLib, line 2664
} // library marker kkossev.commonLib, line 2665

// credits @thebearmay // library marker kkossev.commonLib, line 2667
boolean isCompatible(Integer minLevel) { //check to see if the hub version meets the minimum requirement ( 7 or 8 ) // library marker kkossev.commonLib, line 2668
    String model = getModel()            // <modelName>Rev C-7</modelName> // library marker kkossev.commonLib, line 2669
    String[] tokens = model.split('-') // library marker kkossev.commonLib, line 2670
    String revision = tokens.last() // library marker kkossev.commonLib, line 2671
    return (Integer.parseInt(revision) >= minLevel) // library marker kkossev.commonLib, line 2672
} // library marker kkossev.commonLib, line 2673

/** // library marker kkossev.commonLib, line 2675
 * called from TODO // library marker kkossev.commonLib, line 2676
 *  // library marker kkossev.commonLib, line 2677
 */ // library marker kkossev.commonLib, line 2678

def deleteAllStatesAndJobs() { // library marker kkossev.commonLib, line 2680
    state.clear()    // clear all states // library marker kkossev.commonLib, line 2681
    unschedule() // library marker kkossev.commonLib, line 2682
    device.deleteCurrentState('*') // library marker kkossev.commonLib, line 2683
    device.deleteCurrentState('') // library marker kkossev.commonLib, line 2684

    log.info "${device.displayName} jobs and states cleared. HE hub is ${getHubVersion()}, version is ${location.hub.firmwareVersionString}" // library marker kkossev.commonLib, line 2686
} // library marker kkossev.commonLib, line 2687


def resetStatistics() { // library marker kkossev.commonLib, line 2690
    runIn(1, "resetStats") // library marker kkossev.commonLib, line 2691
    sendInfoEvent("Statistics are reset. Refresh the web page") // library marker kkossev.commonLib, line 2692
} // library marker kkossev.commonLib, line 2693

/** // library marker kkossev.commonLib, line 2695
 * called from TODO // library marker kkossev.commonLib, line 2696
 *  // library marker kkossev.commonLib, line 2697
 */ // library marker kkossev.commonLib, line 2698
def resetStats() { // library marker kkossev.commonLib, line 2699
    logDebug "resetStats..." // library marker kkossev.commonLib, line 2700
    state.stats = [:] // library marker kkossev.commonLib, line 2701
    state.states = [:] // library marker kkossev.commonLib, line 2702
    state.lastRx = [:] // library marker kkossev.commonLib, line 2703
    state.lastTx = [:] // library marker kkossev.commonLib, line 2704
    state.health = [:] // library marker kkossev.commonLib, line 2705
    state.zigbeeGroups = [:]  // library marker kkossev.commonLib, line 2706
    state.stats["rxCtr"] = 0 // library marker kkossev.commonLib, line 2707
    state.stats["txCtr"] = 0 // library marker kkossev.commonLib, line 2708
    state.states["isDigital"] = false // library marker kkossev.commonLib, line 2709
    state.states["isRefresh"] = false // library marker kkossev.commonLib, line 2710
    state.health["offlineCtr"] = 0 // library marker kkossev.commonLib, line 2711
    state.health["checkCtr3"] = 0 // library marker kkossev.commonLib, line 2712
} // library marker kkossev.commonLib, line 2713

/** // library marker kkossev.commonLib, line 2715
 * called from TODO // library marker kkossev.commonLib, line 2716
 *  // library marker kkossev.commonLib, line 2717
 */ // library marker kkossev.commonLib, line 2718
void initializeVars( boolean fullInit = false ) { // library marker kkossev.commonLib, line 2719
    logDebug "InitializeVars()... fullInit = ${fullInit}" // library marker kkossev.commonLib, line 2720
    if (fullInit == true ) { // library marker kkossev.commonLib, line 2721
        state.clear() // library marker kkossev.commonLib, line 2722
        unschedule() // library marker kkossev.commonLib, line 2723
        resetStats() // library marker kkossev.commonLib, line 2724
        //setDeviceNameAndProfile() // library marker kkossev.commonLib, line 2725
        //state.comment = 'Works with Tuya Zigbee Devices' // library marker kkossev.commonLib, line 2726
        logInfo "all states and scheduled jobs cleared!" // library marker kkossev.commonLib, line 2727
        state.driverVersion = driverVersionAndTimeStamp() // library marker kkossev.commonLib, line 2728
        logInfo "DEVICE_TYPE = ${DEVICE_TYPE}" // library marker kkossev.commonLib, line 2729
        state.deviceType = DEVICE_TYPE // library marker kkossev.commonLib, line 2730
        sendInfoEvent("Initialized") // library marker kkossev.commonLib, line 2731
    } // library marker kkossev.commonLib, line 2732

    if (state.stats == null)  { state.stats  = [:] } // library marker kkossev.commonLib, line 2734
    if (state.states == null) { state.states = [:] } // library marker kkossev.commonLib, line 2735
    if (state.lastRx == null) { state.lastRx = [:] } // library marker kkossev.commonLib, line 2736
    if (state.lastTx == null) { state.lastTx = [:] } // library marker kkossev.commonLib, line 2737
    if (state.health == null) { state.health = [:] } // library marker kkossev.commonLib, line 2738
    if (state.zigbeeGroups == null) { state.zigbeeGroups = [:] } // library marker kkossev.commonLib, line 2739

    if (fullInit || settings?.txtEnable == null) device.updateSetting("txtEnable", true) // library marker kkossev.commonLib, line 2741
    if (fullInit || settings?.logEnable == null) device.updateSetting("logEnable", true) // library marker kkossev.commonLib, line 2742
    if (fullInit || settings?.traceEnable == null) device.updateSetting("traceEnable", false) // library marker kkossev.commonLib, line 2743
    if (fullInit || settings?.advancedOptions == null) device.updateSetting("advancedOptions", [value:false, type:"bool"]) // library marker kkossev.commonLib, line 2744
    if (fullInit || settings?.healthCheckMethod == null) device.updateSetting('healthCheckMethod', [value: HealthcheckMethodOpts.defaultValue.toString(), type: 'enum']) // library marker kkossev.commonLib, line 2745
    if (fullInit || settings?.healthCheckInterval == null) device.updateSetting('healthCheckInterval', [value: HealthcheckIntervalOpts.defaultValue.toString(), type: 'enum']) // library marker kkossev.commonLib, line 2746
    if (device.currentValue('healthStatus') == null) sendHealthStatusEvent('unknown') // library marker kkossev.commonLib, line 2747
    if (fullInit || settings?.threeStateEnable == null) device.updateSetting("threeStateEnable", false) // library marker kkossev.commonLib, line 2748
    if (fullInit || settings?.voltageToPercent == null) device.updateSetting("voltageToPercent", false) // library marker kkossev.commonLib, line 2749
    if (device.hasCapability("IlluminanceMeasurement")) { // library marker kkossev.commonLib, line 2750
        if (fullInit || settings?.minReportingTime == null) device.updateSetting("minReportingTime", [value:DEFAULT_MIN_REPORTING_TIME, type:"number"]) // library marker kkossev.commonLib, line 2751
        if (fullInit || settings?.maxReportingTime == null) device.updateSetting("maxReportingTime", [value:DEFAULT_MAX_REPORTING_TIME, type:"number"]) // library marker kkossev.commonLib, line 2752
    } // library marker kkossev.commonLib, line 2753
    if (device.hasCapability("IlluminanceMeasurement")) { // library marker kkossev.commonLib, line 2754
        if (fullInit || settings?.illuminanceThreshold == null) device.updateSetting("illuminanceThreshold", [value:DEFAULT_ILLUMINANCE_THRESHOLD, type:"number"]) // library marker kkossev.commonLib, line 2755
        if (fullInit || settings?.illuminanceCoeff == null) device.updateSetting("illuminanceCoeff", [value:1.00, type:"decimal"]) // library marker kkossev.commonLib, line 2756
    } // library marker kkossev.commonLib, line 2757
    // device specific initialization should be at the end // library marker kkossev.commonLib, line 2758
    if (DEVICE_TYPE in ["AirQuality"]) { initVarsAirQuality(fullInit) } // library marker kkossev.commonLib, line 2759
    if (DEVICE_TYPE in ["Fingerbot"])  { initVarsFingerbot(fullInit); initEventsFingerbot(fullInit) } // library marker kkossev.commonLib, line 2760
    if (DEVICE_TYPE in ["AqaraCube"])  { initVarsAqaraCube(fullInit); initEventsAqaraCube(fullInit) } // library marker kkossev.commonLib, line 2761
    if (DEVICE_TYPE in ["Switch"])     { initVarsSwitch(fullInit);    initEventsSwitch(fullInit) }         // none // library marker kkossev.commonLib, line 2762
    if (DEVICE_TYPE in ["IRBlaster"])  { initVarsIrBlaster(fullInit); initEventsIrBlaster(fullInit) }      // none // library marker kkossev.commonLib, line 2763
    if (DEVICE_TYPE in ["Radar"])      { initVarsRadar(fullInit);     initEventsRadar(fullInit) }          // none // library marker kkossev.commonLib, line 2764
    if (DEVICE_TYPE in ["ButtonDimmer"]) { initVarsButtonDimmer(fullInit);     initEventsButtonDimmer(fullInit) } // library marker kkossev.commonLib, line 2765
    if (DEVICE_TYPE in ["Thermostat"]) { initVarsThermostat(fullInit);     initEventsThermostat(fullInit) } // library marker kkossev.commonLib, line 2766
    if (DEVICE_TYPE in ["Bulb"])       { initVarsBulb(fullInit);     initEventsBulb(fullInit) } // library marker kkossev.commonLib, line 2767

    def mm = device.getDataValue("model") // library marker kkossev.commonLib, line 2769
    if ( mm != null) { // library marker kkossev.commonLib, line 2770
        logDebug " model = ${mm}" // library marker kkossev.commonLib, line 2771
    } // library marker kkossev.commonLib, line 2772
    else { // library marker kkossev.commonLib, line 2773
        logWarn " Model not found, please re-pair the device!" // library marker kkossev.commonLib, line 2774
    } // library marker kkossev.commonLib, line 2775
    def ep = device.getEndpointId() // library marker kkossev.commonLib, line 2776
    if ( ep  != null) { // library marker kkossev.commonLib, line 2777
        //state.destinationEP = ep // library marker kkossev.commonLib, line 2778
        logDebug " destinationEP = ${ep}" // library marker kkossev.commonLib, line 2779
    } // library marker kkossev.commonLib, line 2780
    else { // library marker kkossev.commonLib, line 2781
        logWarn " Destination End Point not found, please re-pair the device!" // library marker kkossev.commonLib, line 2782
        //state.destinationEP = "01"    // fallback // library marker kkossev.commonLib, line 2783
    }     // library marker kkossev.commonLib, line 2784
} // library marker kkossev.commonLib, line 2785


/** // library marker kkossev.commonLib, line 2788
 * called from TODO // library marker kkossev.commonLib, line 2789
 *  // library marker kkossev.commonLib, line 2790
 */ // library marker kkossev.commonLib, line 2791
def setDestinationEP() { // library marker kkossev.commonLib, line 2792
    def ep = device.getEndpointId() // library marker kkossev.commonLib, line 2793
    if (ep != null && ep != 'F2') { // library marker kkossev.commonLib, line 2794
        state.destinationEP = ep // library marker kkossev.commonLib, line 2795
        logDebug "setDestinationEP() destinationEP = ${state.destinationEP}" // library marker kkossev.commonLib, line 2796
    } // library marker kkossev.commonLib, line 2797
    else { // library marker kkossev.commonLib, line 2798
        logWarn "setDestinationEP() Destination End Point not found or invalid(${ep}), activating the F2 bug patch!" // library marker kkossev.commonLib, line 2799
        state.destinationEP = "01"    // fallback EP // library marker kkossev.commonLib, line 2800
    }       // library marker kkossev.commonLib, line 2801
} // library marker kkossev.commonLib, line 2802


def logDebug(msg) { // library marker kkossev.commonLib, line 2805
    if (settings.logEnable) { // library marker kkossev.commonLib, line 2806
        log.debug "${device.displayName} " + msg // library marker kkossev.commonLib, line 2807
    } // library marker kkossev.commonLib, line 2808
} // library marker kkossev.commonLib, line 2809

def logInfo(msg) { // library marker kkossev.commonLib, line 2811
    if (settings.txtEnable) { // library marker kkossev.commonLib, line 2812
        log.info "${device.displayName} " + msg // library marker kkossev.commonLib, line 2813
    } // library marker kkossev.commonLib, line 2814
} // library marker kkossev.commonLib, line 2815

def logWarn(msg) { // library marker kkossev.commonLib, line 2817
    if (settings.logEnable) { // library marker kkossev.commonLib, line 2818
        log.warn "${device.displayName} " + msg // library marker kkossev.commonLib, line 2819
    } // library marker kkossev.commonLib, line 2820
} // library marker kkossev.commonLib, line 2821

def logTrace(msg) { // library marker kkossev.commonLib, line 2823
    if (settings.traceEnable) { // library marker kkossev.commonLib, line 2824
        log.trace "${device.displayName} " + msg // library marker kkossev.commonLib, line 2825
    } // library marker kkossev.commonLib, line 2826
} // library marker kkossev.commonLib, line 2827



// _DEBUG mode only // library marker kkossev.commonLib, line 2831
void getAllProperties() { // library marker kkossev.commonLib, line 2832
    log.trace 'Properties:'     // library marker kkossev.commonLib, line 2833
    device.properties.each { it-> // library marker kkossev.commonLib, line 2834
        log.debug it // library marker kkossev.commonLib, line 2835
    } // library marker kkossev.commonLib, line 2836
    log.trace 'Settings:'     // library marker kkossev.commonLib, line 2837
    settings.each { it-> // library marker kkossev.commonLib, line 2838
        log.debug "${it.key} =  ${it.value}"    // https://community.hubitat.com/t/how-do-i-get-the-datatype-for-an-app-setting/104228/6?u=kkossev // library marker kkossev.commonLib, line 2839
    }     // library marker kkossev.commonLib, line 2840
    log.trace 'Done'     // library marker kkossev.commonLib, line 2841
} // library marker kkossev.commonLib, line 2842

// delete all Preferences // library marker kkossev.commonLib, line 2844
void deleteAllSettings() { // library marker kkossev.commonLib, line 2845
    settings.each { it-> // library marker kkossev.commonLib, line 2846
        logDebug "deleting ${it.key}" // library marker kkossev.commonLib, line 2847
        device.removeSetting("${it.key}") // library marker kkossev.commonLib, line 2848
    } // library marker kkossev.commonLib, line 2849
    logInfo  "All settings (preferences) DELETED" // library marker kkossev.commonLib, line 2850
} // library marker kkossev.commonLib, line 2851

// delete all attributes // library marker kkossev.commonLib, line 2853
void deleteAllCurrentStates() { // library marker kkossev.commonLib, line 2854
    device.properties.supportedAttributes.each { it-> // library marker kkossev.commonLib, line 2855
        logDebug "deleting $it" // library marker kkossev.commonLib, line 2856
        device.deleteCurrentState("$it") // library marker kkossev.commonLib, line 2857
    } // library marker kkossev.commonLib, line 2858
    logInfo "All current states (attributes) DELETED" // library marker kkossev.commonLib, line 2859
} // library marker kkossev.commonLib, line 2860

// delete all State Variables // library marker kkossev.commonLib, line 2862
void deleteAllStates() { // library marker kkossev.commonLib, line 2863
    state.each { it-> // library marker kkossev.commonLib, line 2864
        logDebug "deleting state ${it.key}" // library marker kkossev.commonLib, line 2865
    } // library marker kkossev.commonLib, line 2866
    state.clear() // library marker kkossev.commonLib, line 2867
    logInfo "All States DELETED" // library marker kkossev.commonLib, line 2868
} // library marker kkossev.commonLib, line 2869

void deleteAllScheduledJobs() { // library marker kkossev.commonLib, line 2871
    unschedule() // library marker kkossev.commonLib, line 2872
    logInfo "All scheduled jobs DELETED" // library marker kkossev.commonLib, line 2873
} // library marker kkossev.commonLib, line 2874

void deleteAllChildDevices() { // library marker kkossev.commonLib, line 2876
    logDebug "deleteAllChildDevices : not implemented!" // library marker kkossev.commonLib, line 2877
} // library marker kkossev.commonLib, line 2878

def parseTest(par) { // library marker kkossev.commonLib, line 2880
//read attr - raw: DF8D0104020A000029280A, dni: DF8D, endpoint: 01, cluster: 0402, size: 0A, attrId: 0000, encoding: 29, command: 0A, value: 280A // library marker kkossev.commonLib, line 2881
    log.warn "parseTest(${par})" // library marker kkossev.commonLib, line 2882
    parse(par) // library marker kkossev.commonLib, line 2883
} // library marker kkossev.commonLib, line 2884

def testJob() { // library marker kkossev.commonLib, line 2886
    log.warn "test job executed" // library marker kkossev.commonLib, line 2887
} // library marker kkossev.commonLib, line 2888

/** // library marker kkossev.commonLib, line 2890
 * Calculates and returns the cron expression // library marker kkossev.commonLib, line 2891
 * @param timeInSeconds interval in seconds // library marker kkossev.commonLib, line 2892
 */ // library marker kkossev.commonLib, line 2893
def getCron( timeInSeconds ) { // library marker kkossev.commonLib, line 2894
    //schedule("${rnd.nextInt(59)} ${rnd.nextInt(9)}/${intervalMins} * ? * * *", 'ping') // library marker kkossev.commonLib, line 2895
    // TODO: runEvery1Minute runEvery5Minutes runEvery10Minutes runEvery15Minutes runEvery30Minutes runEvery1Hour runEvery3Hours // library marker kkossev.commonLib, line 2896
    final Random rnd = new Random() // library marker kkossev.commonLib, line 2897
    def minutes = (timeInSeconds / 60 ) as int // library marker kkossev.commonLib, line 2898
    def hours = (minutes / 60 ) as int // library marker kkossev.commonLib, line 2899
    if (hours > 23) { hours = 23 } // library marker kkossev.commonLib, line 2900
    String cron // library marker kkossev.commonLib, line 2901
    if (timeInSeconds < 60) { // library marker kkossev.commonLib, line 2902
        cron = "*/$timeInSeconds * * * * ? *" // library marker kkossev.commonLib, line 2903
    } // library marker kkossev.commonLib, line 2904
    else { // library marker kkossev.commonLib, line 2905
        if (minutes < 60) { // library marker kkossev.commonLib, line 2906
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(9)}/$minutes * ? * *"   // library marker kkossev.commonLib, line 2907
        } // library marker kkossev.commonLib, line 2908
        else { // library marker kkossev.commonLib, line 2909
            cron = "${rnd.nextInt(59)} ${rnd.nextInt(59)} */$hours ? * *"                    // library marker kkossev.commonLib, line 2910
        } // library marker kkossev.commonLib, line 2911
    } // library marker kkossev.commonLib, line 2912
    return cron // library marker kkossev.commonLib, line 2913
} // library marker kkossev.commonLib, line 2914

boolean isTuya() { // library marker kkossev.commonLib, line 2916
    def model = device.getDataValue("model")  // library marker kkossev.commonLib, line 2917
    def manufacturer = device.getDataValue("manufacturer")  // library marker kkossev.commonLib, line 2918
    if (model?.startsWith("TS") && manufacturer?.startsWith("_TZ")) { // library marker kkossev.commonLib, line 2919
        return true // library marker kkossev.commonLib, line 2920
    } // library marker kkossev.commonLib, line 2921
    return false // library marker kkossev.commonLib, line 2922
} // library marker kkossev.commonLib, line 2923

void updateTuyaVersion() { // library marker kkossev.commonLib, line 2925
    if (!isTuya()) { // library marker kkossev.commonLib, line 2926
        logDebug "not Tuya" // library marker kkossev.commonLib, line 2927
        return // library marker kkossev.commonLib, line 2928
    } // library marker kkossev.commonLib, line 2929
    def application = device.getDataValue("application")  // library marker kkossev.commonLib, line 2930
    if (application != null) { // library marker kkossev.commonLib, line 2931
        Integer ver // library marker kkossev.commonLib, line 2932
        try { // library marker kkossev.commonLib, line 2933
            ver = zigbee.convertHexToInt(application) // library marker kkossev.commonLib, line 2934
        } // library marker kkossev.commonLib, line 2935
        catch (e) { // library marker kkossev.commonLib, line 2936
            logWarn "exception caught while converting application version ${application} to tuyaVersion" // library marker kkossev.commonLib, line 2937
            return // library marker kkossev.commonLib, line 2938
        } // library marker kkossev.commonLib, line 2939
        def str = ((ver&0xC0)>>6).toString() + "." + ((ver&0x30)>>4).toString() + "." + (ver&0x0F).toString() // library marker kkossev.commonLib, line 2940
        if (device.getDataValue("tuyaVersion") != str) { // library marker kkossev.commonLib, line 2941
            device.updateDataValue("tuyaVersion", str) // library marker kkossev.commonLib, line 2942
            logInfo "tuyaVersion set to $str" // library marker kkossev.commonLib, line 2943
        } // library marker kkossev.commonLib, line 2944
    } // library marker kkossev.commonLib, line 2945
} // library marker kkossev.commonLib, line 2946

boolean isAqara() { // library marker kkossev.commonLib, line 2948
    def model = device.getDataValue("model")  // library marker kkossev.commonLib, line 2949
    def manufacturer = device.getDataValue("manufacturer")  // library marker kkossev.commonLib, line 2950
    if (model?.startsWith("lumi")) { // library marker kkossev.commonLib, line 2951
        return true // library marker kkossev.commonLib, line 2952
    } // library marker kkossev.commonLib, line 2953
    return false // library marker kkossev.commonLib, line 2954
} // library marker kkossev.commonLib, line 2955

def updateAqaraVersion() { // library marker kkossev.commonLib, line 2957
    if (!isAqara()) { // library marker kkossev.commonLib, line 2958
        logDebug "not Aqara" // library marker kkossev.commonLib, line 2959
        return // library marker kkossev.commonLib, line 2960
    }     // library marker kkossev.commonLib, line 2961
    def application = device.getDataValue("application")  // library marker kkossev.commonLib, line 2962
    if (application != null) { // library marker kkossev.commonLib, line 2963
        def str = "0.0.0_" + String.format("%04d", zigbee.convertHexToInt(application.substring(0, Math.min(application.length(), 2)))); // library marker kkossev.commonLib, line 2964
        if (device.getDataValue("aqaraVersion") != str) { // library marker kkossev.commonLib, line 2965
            device.updateDataValue("aqaraVersion", str) // library marker kkossev.commonLib, line 2966
            logInfo "aqaraVersion set to $str" // library marker kkossev.commonLib, line 2967
        } // library marker kkossev.commonLib, line 2968
    } // library marker kkossev.commonLib, line 2969
    else { // library marker kkossev.commonLib, line 2970
        return null // library marker kkossev.commonLib, line 2971
    } // library marker kkossev.commonLib, line 2972
} // library marker kkossev.commonLib, line 2973

def test(par) { // library marker kkossev.commonLib, line 2975
    ArrayList<String> cmds = [] // library marker kkossev.commonLib, line 2976
    log.warn "test... ${par}" // library marker kkossev.commonLib, line 2977

    parse(par) // library marker kkossev.commonLib, line 2979

   // sendZigbeeCommands(cmds)     // library marker kkossev.commonLib, line 2981
} // library marker kkossev.commonLib, line 2982

// /////////////////////////////////////////////////////////////////// Libraries ////////////////////////////////////////////////////////////////////// // library marker kkossev.commonLib, line 2984



// ~~~~~ end include (144) kkossev.commonLib ~~~~~

// ~~~~~ start include (141) kkossev.xiaomiLib ~~~~~
library ( // library marker kkossev.xiaomiLib, line 1
    base: "driver", // library marker kkossev.xiaomiLib, line 2
    author: "Krassimir Kossev", // library marker kkossev.xiaomiLib, line 3
    category: "zigbee", // library marker kkossev.xiaomiLib, line 4
    description: "Xiaomi Library", // library marker kkossev.xiaomiLib, line 5
    name: "xiaomiLib", // library marker kkossev.xiaomiLib, line 6
    namespace: "kkossev", // library marker kkossev.xiaomiLib, line 7
    importUrl: "https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/xiaomiLib.groovy", // library marker kkossev.xiaomiLib, line 8
    version: "1.0.1", // library marker kkossev.xiaomiLib, line 9
    documentationLink: "" // library marker kkossev.xiaomiLib, line 10
) // library marker kkossev.xiaomiLib, line 11
/* // library marker kkossev.xiaomiLib, line 12
 *  Xiaomi Library // library marker kkossev.xiaomiLib, line 13
 * // library marker kkossev.xiaomiLib, line 14
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.xiaomiLib, line 15
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.xiaomiLib, line 16
 * // library marker kkossev.xiaomiLib, line 17
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.xiaomiLib, line 18
 * // library marker kkossev.xiaomiLib, line 19
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.xiaomiLib, line 20
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.xiaomiLib, line 21
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.xiaomiLib, line 22
 * // library marker kkossev.xiaomiLib, line 23
 * ver. 1.0.0  2023-09-09 kkossev  - added xiaomiLib // library marker kkossev.xiaomiLib, line 24
 * ver. 1.0.1  2023-11-07 kkossev  - (dev. branch) // library marker kkossev.xiaomiLib, line 25
 * // library marker kkossev.xiaomiLib, line 26
 *                                   TODO:  // library marker kkossev.xiaomiLib, line 27
*/ // library marker kkossev.xiaomiLib, line 28


def xiaomiLibVersion()   {"1.0.1"} // library marker kkossev.xiaomiLib, line 31
def xiaomiLibStamp() {"2023/11/07 5:23 PM"} // library marker kkossev.xiaomiLib, line 32

// no metadata for this library! // library marker kkossev.xiaomiLib, line 34

@Field static final int XIAOMI_CLUSTER_ID = 0xFCC0 // library marker kkossev.xiaomiLib, line 36

// Zigbee Attributes // library marker kkossev.xiaomiLib, line 38
@Field static final int DIRECTION_MODE_ATTR_ID = 0x0144 // library marker kkossev.xiaomiLib, line 39
@Field static final int MODEL_ATTR_ID = 0x05 // library marker kkossev.xiaomiLib, line 40
@Field static final int PRESENCE_ACTIONS_ATTR_ID = 0x0143 // library marker kkossev.xiaomiLib, line 41
@Field static final int PRESENCE_ATTR_ID = 0x0142 // library marker kkossev.xiaomiLib, line 42
@Field static final int REGION_EVENT_ATTR_ID = 0x0151 // library marker kkossev.xiaomiLib, line 43
@Field static final int RESET_PRESENCE_ATTR_ID = 0x0157 // library marker kkossev.xiaomiLib, line 44
@Field static final int SENSITIVITY_LEVEL_ATTR_ID = 0x010C // library marker kkossev.xiaomiLib, line 45
@Field static final int SET_EDGE_REGION_ATTR_ID = 0x0156 // library marker kkossev.xiaomiLib, line 46
@Field static final int SET_EXIT_REGION_ATTR_ID = 0x0153 // library marker kkossev.xiaomiLib, line 47
@Field static final int SET_INTERFERENCE_ATTR_ID = 0x0154 // library marker kkossev.xiaomiLib, line 48
@Field static final int SET_REGION_ATTR_ID = 0x0150 // library marker kkossev.xiaomiLib, line 49
@Field static final int TRIGGER_DISTANCE_ATTR_ID = 0x0146 // library marker kkossev.xiaomiLib, line 50
@Field static final int XIAOMI_RAW_ATTR_ID = 0xFFF2 // library marker kkossev.xiaomiLib, line 51
@Field static final int XIAOMI_SPECIAL_REPORT_ID = 0x00F7 // library marker kkossev.xiaomiLib, line 52
@Field static final Map MFG_CODE = [ mfgCode: 0x115F ] // library marker kkossev.xiaomiLib, line 53

// Xiaomi Tags // library marker kkossev.xiaomiLib, line 55
@Field static final int DIRECTION_MODE_TAG_ID = 0x67 // library marker kkossev.xiaomiLib, line 56
@Field static final int SENSITIVITY_LEVEL_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 57
@Field static final int SWBUILD_TAG_ID = 0x08 // library marker kkossev.xiaomiLib, line 58
@Field static final int TRIGGER_DISTANCE_TAG_ID = 0x69 // library marker kkossev.xiaomiLib, line 59
@Field static final int PRESENCE_ACTIONS_TAG_ID = 0x66 // library marker kkossev.xiaomiLib, line 60
@Field static final int PRESENCE_TAG_ID = 0x65 // library marker kkossev.xiaomiLib, line 61

// called from parseXiaomiCluster() in the main code ... // library marker kkossev.xiaomiLib, line 63
// // library marker kkossev.xiaomiLib, line 64
void parseXiaomiClusterLib(final Map descMap) { // library marker kkossev.xiaomiLib, line 65
    if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 66
        //log.trace "zigbee received xiaomi cluster attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 67
    } // library marker kkossev.xiaomiLib, line 68
    if (DEVICE_TYPE in  ["Thermostat"]) { // library marker kkossev.xiaomiLib, line 69
        parseXiaomiClusterThermostatLib(descMap) // library marker kkossev.xiaomiLib, line 70
        return // library marker kkossev.xiaomiLib, line 71
    } // library marker kkossev.xiaomiLib, line 72
    if (DEVICE_TYPE in  ["Bulb"]) { // library marker kkossev.xiaomiLib, line 73
        parseXiaomiClusterRgbLib(descMap) // library marker kkossev.xiaomiLib, line 74
        return // library marker kkossev.xiaomiLib, line 75
    } // library marker kkossev.xiaomiLib, line 76
    // TODO - refactor AqaraCube specific code // library marker kkossev.xiaomiLib, line 77
    // TODO - refactor FP1 specific code // library marker kkossev.xiaomiLib, line 78
    switch (descMap.attrInt as Integer) { // library marker kkossev.xiaomiLib, line 79
        case 0x0009:                      // Aqara Cube T1 Pro // library marker kkossev.xiaomiLib, line 80
            if (DEVICE_TYPE in  ["AqaraCube"]) { logDebug "AqaraCube 0xFCC0 attribute 0x009 value is ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 81
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 82
            break // library marker kkossev.xiaomiLib, line 83
        case 0x00FC:                      // FP1 // library marker kkossev.xiaomiLib, line 84
            log.info "unknown attribute - resetting?" // library marker kkossev.xiaomiLib, line 85
            break // library marker kkossev.xiaomiLib, line 86
        case PRESENCE_ATTR_ID:            // 0x0142 FP1 // library marker kkossev.xiaomiLib, line 87
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 88
            parseXiaomiClusterPresence(value) // library marker kkossev.xiaomiLib, line 89
            break // library marker kkossev.xiaomiLib, line 90
        case PRESENCE_ACTIONS_ATTR_ID:    // 0x0143 FP1 // library marker kkossev.xiaomiLib, line 91
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 92
            parseXiaomiClusterPresenceAction(value) // library marker kkossev.xiaomiLib, line 93
            break // library marker kkossev.xiaomiLib, line 94
        case REGION_EVENT_ATTR_ID:        // 0x0151 FP1 // library marker kkossev.xiaomiLib, line 95
            // Region events can be sent fast and furious so buffer them // library marker kkossev.xiaomiLib, line 96
            final Integer regionId = HexUtils.hexStringToInt(descMap.value[0..1]) // library marker kkossev.xiaomiLib, line 97
            final Integer value = HexUtils.hexStringToInt(descMap.value[2..3]) // library marker kkossev.xiaomiLib, line 98
            if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 99
                log.debug "xiaomi: region ${regionId} action is ${value}" // library marker kkossev.xiaomiLib, line 100
            } // library marker kkossev.xiaomiLib, line 101
            if (device.currentValue("region${regionId}") != null) { // library marker kkossev.xiaomiLib, line 102
                RegionUpdateBuffer.get(device.id).put(regionId, value) // library marker kkossev.xiaomiLib, line 103
                runInMillis(REGION_UPDATE_DELAY_MS, 'updateRegions') // library marker kkossev.xiaomiLib, line 104
            } // library marker kkossev.xiaomiLib, line 105
            break // library marker kkossev.xiaomiLib, line 106
        case SENSITIVITY_LEVEL_ATTR_ID:   // 0x010C FP1 // library marker kkossev.xiaomiLib, line 107
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 108
            log.info "sensitivity level is '${SensitivityLevelOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 109
            device.updateSetting('sensitivityLevel', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 110
            break // library marker kkossev.xiaomiLib, line 111
        case TRIGGER_DISTANCE_ATTR_ID:    // 0x0146 FP1 // library marker kkossev.xiaomiLib, line 112
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 113
            log.info "approach distance is '${ApproachDistanceOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 114
            device.updateSetting('approachDistance', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 115
            break // library marker kkossev.xiaomiLib, line 116
        case DIRECTION_MODE_ATTR_ID:     // 0x0144 FP1 // library marker kkossev.xiaomiLib, line 117
            final Integer value = hexStrToUnsignedInt(descMap.value) // library marker kkossev.xiaomiLib, line 118
            log.info "monitoring direction mode is '${DirectionModeOpts.options[value]}' (0x${descMap.value})" // library marker kkossev.xiaomiLib, line 119
            device.updateSetting('directionMode', [value: value.toString(), type: 'enum']) // library marker kkossev.xiaomiLib, line 120
            break // library marker kkossev.xiaomiLib, line 121
        case 0x0148 :                    // Aqara Cube T1 Pro - Mode // library marker kkossev.xiaomiLib, line 122
            if (DEVICE_TYPE in  ["AqaraCube"]) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 123
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 124
            break // library marker kkossev.xiaomiLib, line 125
        case 0x0149:                     // (329) Aqara Cube T1 Pro - i side facing up (0..5) // library marker kkossev.xiaomiLib, line 126
            if (DEVICE_TYPE in  ["AqaraCube"]) { parseXiaomiClusterAqaraCube(descMap) } // library marker kkossev.xiaomiLib, line 127
            else { logDebug "XiaomiCluster unknown attribute ${descMap.attrInt} value raw = ${hexStrToUnsignedInt(descMap.value)}" } // library marker kkossev.xiaomiLib, line 128
            break // library marker kkossev.xiaomiLib, line 129
        case XIAOMI_SPECIAL_REPORT_ID:   // 0x00F7 sent every 55 minutes // library marker kkossev.xiaomiLib, line 130
            final Map<Integer, Integer> tags = decodeXiaomiTags(descMap.value) // library marker kkossev.xiaomiLib, line 131
            parseXiaomiClusterTags(tags) // library marker kkossev.xiaomiLib, line 132
            if (isAqaraCube()) { // library marker kkossev.xiaomiLib, line 133
                sendZigbeeCommands(refreshAqaraCube()) // library marker kkossev.xiaomiLib, line 134
            } // library marker kkossev.xiaomiLib, line 135
            break // library marker kkossev.xiaomiLib, line 136
        case XIAOMI_RAW_ATTR_ID:        // 0xFFF2 FP1  // library marker kkossev.xiaomiLib, line 137
            final byte[] rawData = HexUtils.hexStringToByteArray(descMap.value) // library marker kkossev.xiaomiLib, line 138
            if (rawData.size() == 24 && settings.enableDistanceDirection) { // library marker kkossev.xiaomiLib, line 139
                final int degrees = rawData[19] // library marker kkossev.xiaomiLib, line 140
                final int distanceCm = (rawData[17] << 8) | (rawData[18] & 0x00ff) // library marker kkossev.xiaomiLib, line 141
                if (settings.logEnable) { // library marker kkossev.xiaomiLib, line 142
                    log.debug "location ${degrees}&deg;, ${distanceCm}cm" // library marker kkossev.xiaomiLib, line 143
                } // library marker kkossev.xiaomiLib, line 144
                runIn(1, 'updateLocation', [ data: [ degrees: degrees, distanceCm: distanceCm ] ]) // library marker kkossev.xiaomiLib, line 145
            } // library marker kkossev.xiaomiLib, line 146
            break // library marker kkossev.xiaomiLib, line 147
        default: // library marker kkossev.xiaomiLib, line 148
            log.warn "zigbee received unknown xiaomi cluster 0xFCC0 attribute 0x${descMap.attrId} (value ${descMap.value})" // library marker kkossev.xiaomiLib, line 149
            break // library marker kkossev.xiaomiLib, line 150
    } // library marker kkossev.xiaomiLib, line 151
} // library marker kkossev.xiaomiLib, line 152

void parseXiaomiClusterTags(final Map<Integer, Object> tags) { // library marker kkossev.xiaomiLib, line 154
    tags.each { final Integer tag, final Object value -> // library marker kkossev.xiaomiLib, line 155
        switch (tag) { // library marker kkossev.xiaomiLib, line 156
            case 0x01:    // battery voltage // library marker kkossev.xiaomiLib, line 157
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} battery voltage is ${value/1000}V (raw=${value})" // library marker kkossev.xiaomiLib, line 158
                break // library marker kkossev.xiaomiLib, line 159
            case 0x03: // library marker kkossev.xiaomiLib, line 160
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} device temperature is ${value}&deg;" // library marker kkossev.xiaomiLib, line 161
                break // library marker kkossev.xiaomiLib, line 162
            case 0x05: // library marker kkossev.xiaomiLib, line 163
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} RSSI is ${value}" // library marker kkossev.xiaomiLib, line 164
                break // library marker kkossev.xiaomiLib, line 165
            case 0x06: // library marker kkossev.xiaomiLib, line 166
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} LQI is ${value}" // library marker kkossev.xiaomiLib, line 167
                break // library marker kkossev.xiaomiLib, line 168
            case 0x08:            // SWBUILD_TAG_ID: // library marker kkossev.xiaomiLib, line 169
                final String swBuild = '0.0.0_' + (value & 0xFF).toString().padLeft(4, '0') // library marker kkossev.xiaomiLib, line 170
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} swBuild is ${swBuild} (raw ${value})" // library marker kkossev.xiaomiLib, line 171
                device.updateDataValue("aqaraVersion", swBuild) // library marker kkossev.xiaomiLib, line 172
                break // library marker kkossev.xiaomiLib, line 173
            case 0x0a: // library marker kkossev.xiaomiLib, line 174
                String nwk = intToHexStr(value as Integer,2) // library marker kkossev.xiaomiLib, line 175
                if (state.health == null) { state.health = [:] } // library marker kkossev.xiaomiLib, line 176
                String oldNWK = state.health['parentNWK'] ?: 'n/a' // library marker kkossev.xiaomiLib, line 177
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} <b>Parent NWK is ${nwk}</b>" // library marker kkossev.xiaomiLib, line 178
                if (oldNWK != nwk ) { // library marker kkossev.xiaomiLib, line 179
                    logWarn "parentNWK changed from ${oldNWK} to ${nwk}" // library marker kkossev.xiaomiLib, line 180
                    state.health['parentNWK']  = nwk // library marker kkossev.xiaomiLib, line 181
                    state.health['nwkCtr'] = (state.health['nwkCtr'] ?: 0) + 1 // library marker kkossev.xiaomiLib, line 182
                } // library marker kkossev.xiaomiLib, line 183
                break // library marker kkossev.xiaomiLib, line 184
            case 0x0b: // library marker kkossev.xiaomiLib, line 185
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} light level is ${value}" // library marker kkossev.xiaomiLib, line 186
                break // library marker kkossev.xiaomiLib, line 187
            case 0x64: // library marker kkossev.xiaomiLib, line 188
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} temperature is ${value/100} (raw ${value})"    // Aqara TVOC // library marker kkossev.xiaomiLib, line 189
                // TODO - also smoke gas/density if UINT ! // library marker kkossev.xiaomiLib, line 190
                break // library marker kkossev.xiaomiLib, line 191
            case 0x65: // library marker kkossev.xiaomiLib, line 192
                if (isAqaraFP1()) { logDebug "xiaomi decode PRESENCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 193
                else              { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} humidity is ${value/100} (raw ${value})" }    // Aqara TVOC // library marker kkossev.xiaomiLib, line 194
                break // library marker kkossev.xiaomiLib, line 195
            case 0x66: // library marker kkossev.xiaomiLib, line 196
                if (isAqaraFP1()) { logDebug "xiaomi decode SENSITIVITY_LEVEL_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 197
                else if (isAqaraTVOC()) { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} airQualityIndex is ${value}" }        // Aqara TVOC level (in ppb) // library marker kkossev.xiaomiLib, line 198
                else                    { logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} presure is ${value}" }  // library marker kkossev.xiaomiLib, line 199
                break // library marker kkossev.xiaomiLib, line 200
            case 0x67: // library marker kkossev.xiaomiLib, line 201
                if (isAqaraFP1()) { logDebug "xiaomi decode DIRECTION_MODE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" }     // library marker kkossev.xiaomiLib, line 202
                else              { logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" }                        // Aqara TVOC:  // library marker kkossev.xiaomiLib, line 203
                // air quality (as 6 - #stars) ['excellent', 'good', 'moderate', 'poor', 'unhealthy'][val - 1] // library marker kkossev.xiaomiLib, line 204
                break // library marker kkossev.xiaomiLib, line 205
            case 0x69: // library marker kkossev.xiaomiLib, line 206
                if (isAqaraFP1()) { logDebug "xiaomi decode TRIGGER_DISTANCE_TAG_ID tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 207
                else              { logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 208
                break // library marker kkossev.xiaomiLib, line 209
            case 0x6a: // library marker kkossev.xiaomiLib, line 210
                if (isAqaraFP1()) { logDebug "xiaomi decode FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 211
                else              { logDebug "xiaomi decode MOTION SENSITIVITY tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 212
                break // library marker kkossev.xiaomiLib, line 213
            case 0x6b: // library marker kkossev.xiaomiLib, line 214
                if (isAqaraFP1()) { logDebug "xiaomi decode FP1 unknown tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 215
                else              { logDebug "xiaomi decode MOTION LED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 216
                break // library marker kkossev.xiaomiLib, line 217
            case 0x95: // library marker kkossev.xiaomiLib, line 218
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} energy is ${value}" // library marker kkossev.xiaomiLib, line 219
                break // library marker kkossev.xiaomiLib, line 220
            case 0x96: // library marker kkossev.xiaomiLib, line 221
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} voltage is ${value}" // library marker kkossev.xiaomiLib, line 222
                break // library marker kkossev.xiaomiLib, line 223
            case 0x97: // library marker kkossev.xiaomiLib, line 224
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} current is ${value}" // library marker kkossev.xiaomiLib, line 225
                break // library marker kkossev.xiaomiLib, line 226
            case 0x98: // library marker kkossev.xiaomiLib, line 227
                logDebug "xiaomi decode tag: 0x${intToHexStr(tag, 1)} power is ${value}" // library marker kkossev.xiaomiLib, line 228
                break // library marker kkossev.xiaomiLib, line 229
            case 0x9b: // library marker kkossev.xiaomiLib, line 230
                if (isAqaraCube()) {  // library marker kkossev.xiaomiLib, line 231
                    logDebug "Aqara cubeMode tag: 0x${intToHexStr(tag, 1)} is '${AqaraCubeModeOpts.options[value as int]}' (${value})"  // library marker kkossev.xiaomiLib, line 232
                    sendAqaraCubeOperationModeEvent(value as int) // library marker kkossev.xiaomiLib, line 233
                } // library marker kkossev.xiaomiLib, line 234
                else { logDebug "xiaomi decode CONSUMER CONNECTED tag: 0x${intToHexStr(tag, 1)}=${value}" } // library marker kkossev.xiaomiLib, line 235
                break // library marker kkossev.xiaomiLib, line 236
            default: // library marker kkossev.xiaomiLib, line 237
                logDebug "xiaomi decode unknown tag: 0x${intToHexStr(tag, 1)}=${value}" // library marker kkossev.xiaomiLib, line 238
        } // library marker kkossev.xiaomiLib, line 239
    } // library marker kkossev.xiaomiLib, line 240
} // library marker kkossev.xiaomiLib, line 241


/** // library marker kkossev.xiaomiLib, line 244
 *  Reads a specified number of little-endian bytes from a given // library marker kkossev.xiaomiLib, line 245
 *  ByteArrayInputStream and returns a BigInteger. // library marker kkossev.xiaomiLib, line 246
 */ // library marker kkossev.xiaomiLib, line 247
private static BigInteger readBigIntegerBytes(final ByteArrayInputStream stream, final int length) { // library marker kkossev.xiaomiLib, line 248
    final byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 249
    stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 250
    BigInteger bigInt = BigInteger.ZERO // library marker kkossev.xiaomiLib, line 251
    for (int i = byteArr.length - 1; i >= 0; i--) { // library marker kkossev.xiaomiLib, line 252
        bigInt |= (BigInteger.valueOf((byteArr[i] & 0xFF) << (8 * i))) // library marker kkossev.xiaomiLib, line 253
    } // library marker kkossev.xiaomiLib, line 254
    return bigInt // library marker kkossev.xiaomiLib, line 255
} // library marker kkossev.xiaomiLib, line 256

/** // library marker kkossev.xiaomiLib, line 258
 *  Decodes a Xiaomi Zigbee cluster attribute payload in hexadecimal format and // library marker kkossev.xiaomiLib, line 259
 *  returns a map of decoded tag number and value pairs where the value is either a // library marker kkossev.xiaomiLib, line 260
 *  BigInteger for fixed values or a String for variable length. // library marker kkossev.xiaomiLib, line 261
 */ // library marker kkossev.xiaomiLib, line 262
private static Map<Integer, Object> decodeXiaomiTags(final String hexString) { // library marker kkossev.xiaomiLib, line 263
    final Map<Integer, Object> results = [:] // library marker kkossev.xiaomiLib, line 264
    final byte[] bytes = HexUtils.hexStringToByteArray(hexString) // library marker kkossev.xiaomiLib, line 265
    new ByteArrayInputStream(bytes).withCloseable { final stream -> // library marker kkossev.xiaomiLib, line 266
        while (stream.available() > 2) { // library marker kkossev.xiaomiLib, line 267
            int tag = stream.read() // library marker kkossev.xiaomiLib, line 268
            int dataType = stream.read() // library marker kkossev.xiaomiLib, line 269
            Object value // library marker kkossev.xiaomiLib, line 270
            if (DataType.isDiscrete(dataType)) { // library marker kkossev.xiaomiLib, line 271
                int length = stream.read() // library marker kkossev.xiaomiLib, line 272
                byte[] byteArr = new byte[length] // library marker kkossev.xiaomiLib, line 273
                stream.read(byteArr, 0, length) // library marker kkossev.xiaomiLib, line 274
                value = new String(byteArr) // library marker kkossev.xiaomiLib, line 275
            } else { // library marker kkossev.xiaomiLib, line 276
                int length = DataType.getLength(dataType) // library marker kkossev.xiaomiLib, line 277
                value = readBigIntegerBytes(stream, length) // library marker kkossev.xiaomiLib, line 278
            } // library marker kkossev.xiaomiLib, line 279
            results[tag] = value // library marker kkossev.xiaomiLib, line 280
        } // library marker kkossev.xiaomiLib, line 281
    } // library marker kkossev.xiaomiLib, line 282
    return results // library marker kkossev.xiaomiLib, line 283
} // library marker kkossev.xiaomiLib, line 284


def refreshXiaomi() { // library marker kkossev.xiaomiLib, line 287
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 288
    if (cmds == []) { cmds = ["delay 299"] } // library marker kkossev.xiaomiLib, line 289
    return cmds // library marker kkossev.xiaomiLib, line 290
} // library marker kkossev.xiaomiLib, line 291

def configureXiaomi() { // library marker kkossev.xiaomiLib, line 293
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 294
    logDebug "configureThermostat() : ${cmds}" // library marker kkossev.xiaomiLib, line 295
    if (cmds == []) { cmds = ["delay 299"] }    // no ,  // library marker kkossev.xiaomiLib, line 296
    return cmds     // library marker kkossev.xiaomiLib, line 297
} // library marker kkossev.xiaomiLib, line 298

def initializeXiaomi() // library marker kkossev.xiaomiLib, line 300
{ // library marker kkossev.xiaomiLib, line 301
    List<String> cmds = [] // library marker kkossev.xiaomiLib, line 302
    logDebug "initializeXiaomi() : ${cmds}" // library marker kkossev.xiaomiLib, line 303
    if (cmds == []) { cmds = ["delay 299",] } // library marker kkossev.xiaomiLib, line 304
    return cmds         // library marker kkossev.xiaomiLib, line 305
} // library marker kkossev.xiaomiLib, line 306

void initVarsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 308
    logDebug "initVarsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 309
} // library marker kkossev.xiaomiLib, line 310

void initEventsXiaomi(boolean fullInit=false) { // library marker kkossev.xiaomiLib, line 312
    logDebug "initEventsXiaomi(${fullInit})" // library marker kkossev.xiaomiLib, line 313
} // library marker kkossev.xiaomiLib, line 314


// ~~~~~ end include (141) kkossev.xiaomiLib ~~~~~

// ~~~~~ start include (142) kkossev.deviceProfileLib ~~~~~
library ( // library marker kkossev.deviceProfileLib, line 1
    base: "driver", // library marker kkossev.deviceProfileLib, line 2
    author: "Krassimir Kossev", // library marker kkossev.deviceProfileLib, line 3
    category: "zigbee", // library marker kkossev.deviceProfileLib, line 4
    description: "Device Profile Library", // library marker kkossev.deviceProfileLib, line 5
    name: "deviceProfileLib", // library marker kkossev.deviceProfileLib, line 6
    namespace: "kkossev", // library marker kkossev.deviceProfileLib, line 7
    importUrl: "https://raw.githubusercontent.com/kkossev/hubitat/development/libraries/deviceProfileLib.groovy", // library marker kkossev.deviceProfileLib, line 8
    version: "1.0.0", // library marker kkossev.deviceProfileLib, line 9
    documentationLink: "" // library marker kkossev.deviceProfileLib, line 10
) // library marker kkossev.deviceProfileLib, line 11
/* // library marker kkossev.deviceProfileLib, line 12
 *  Device Profile Library // library marker kkossev.deviceProfileLib, line 13
 * // library marker kkossev.deviceProfileLib, line 14
 *  Licensed Virtual the Apache License, Version 2.0 (the "License"); you may not use this file except // library marker kkossev.deviceProfileLib, line 15
 *  in compliance with the License. You may obtain a copy of the License at: // library marker kkossev.deviceProfileLib, line 16
 * // library marker kkossev.deviceProfileLib, line 17
 *      http://www.apache.org/licenses/LICENSE-2.0 // library marker kkossev.deviceProfileLib, line 18
 * // library marker kkossev.deviceProfileLib, line 19
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed // library marker kkossev.deviceProfileLib, line 20
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License // library marker kkossev.deviceProfileLib, line 21
 *  for the specific language governing permissions and limitations under the License. // library marker kkossev.deviceProfileLib, line 22
 * // library marker kkossev.deviceProfileLib, line 23
 * ver. 1.0.0  2023-11-04 kkossev  - added deviceProfileLib (based on Tuya 4 In 1 driver) // library marker kkossev.deviceProfileLib, line 24
 * ver. 1.0.1  2023-11-04 kkossev  - (dev. branch) // library marker kkossev.deviceProfileLib, line 25
 * // library marker kkossev.deviceProfileLib, line 26
 *                                   TODO: setPar refactoring // library marker kkossev.deviceProfileLib, line 27
*/ // library marker kkossev.deviceProfileLib, line 28

def deviceProfileLibVersion()   {"1.0.1"} // library marker kkossev.deviceProfileLib, line 30
def deviceProfileLibtamp() {"2023/11/05 9:45 AM"} // library marker kkossev.deviceProfileLib, line 31

metadata { // library marker kkossev.deviceProfileLib, line 33
    // no capabilities // library marker kkossev.deviceProfileLib, line 34
    // no attributes // library marker kkossev.deviceProfileLib, line 35
    command "sendCommand", [[name: "sendCommand", type: "STRING", constraints: ["STRING"], description: "send device commands"]] // library marker kkossev.deviceProfileLib, line 36
    command "setPar", [ // library marker kkossev.deviceProfileLib, line 37
            [name:"par", type: "STRING", description: "preference parameter name", constraints: ["STRING"]], // library marker kkossev.deviceProfileLib, line 38
            [name:"val", type: "STRING", description: "preference parameter value", constraints: ["STRING"]] // library marker kkossev.deviceProfileLib, line 39
    ]     // library marker kkossev.deviceProfileLib, line 40
    // // library marker kkossev.deviceProfileLib, line 41
    // itterate over DEVICE.preferences map and inputIt all! // library marker kkossev.deviceProfileLib, line 42
    (DEVICE.preferences).each { key, value -> // library marker kkossev.deviceProfileLib, line 43
        if (inputIt(key) != null) { // library marker kkossev.deviceProfileLib, line 44
            input inputIt(key) // library marker kkossev.deviceProfileLib, line 45
        } // library marker kkossev.deviceProfileLib, line 46
    }     // library marker kkossev.deviceProfileLib, line 47
    preferences { // library marker kkossev.deviceProfileLib, line 48
        if (advancedOptions == true) { // library marker kkossev.deviceProfileLib, line 49
            input (name: "forcedProfile", type: "enum", title: "<b>Device Profile</b>", description: "<i>Forcely change the Device Profile, if the model/manufacturer was not recognized automatically.<br>Warning! Manually setting a device profile may not always work!</i>",  options: getDeviceProfilesMap()) // library marker kkossev.deviceProfileLib, line 50
        } // library marker kkossev.deviceProfileLib, line 51
    } // library marker kkossev.deviceProfileLib, line 52
} // library marker kkossev.deviceProfileLib, line 53

def getDeviceGroup()     { state.deviceProfile ?: "UNKNOWN" } // library marker kkossev.deviceProfileLib, line 55
def getDEVICE()          { deviceProfilesV2[getDeviceGroup()] } // library marker kkossev.deviceProfileLib, line 56
def getDeviceProfiles()      { deviceProfilesV2.keySet() } // library marker kkossev.deviceProfileLib, line 57
def getDeviceProfilesMap()   {deviceProfilesV2.values().description as List<String>} // library marker kkossev.deviceProfileLib, line 58


/** // library marker kkossev.deviceProfileLib, line 61
 * Returns the profile key for a given profile description. // library marker kkossev.deviceProfileLib, line 62
 * @param valueStr The profile description to search for. // library marker kkossev.deviceProfileLib, line 63
 * @return The profile key if found, otherwise null. // library marker kkossev.deviceProfileLib, line 64
 */ // library marker kkossev.deviceProfileLib, line 65
def getProfileKey(String valueStr) { // library marker kkossev.deviceProfileLib, line 66
    def key = null // library marker kkossev.deviceProfileLib, line 67
    deviceProfilesV2.each {  profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 68
        if (profileMap.description.equals(valueStr)) { // library marker kkossev.deviceProfileLib, line 69
            key = profileName // library marker kkossev.deviceProfileLib, line 70
        } // library marker kkossev.deviceProfileLib, line 71
    } // library marker kkossev.deviceProfileLib, line 72
    return key // library marker kkossev.deviceProfileLib, line 73
} // library marker kkossev.deviceProfileLib, line 74

/** // library marker kkossev.deviceProfileLib, line 76
 * Finds the preferences map for the given parameter. // library marker kkossev.deviceProfileLib, line 77
 * @param param The parameter to find the preferences map for. // library marker kkossev.deviceProfileLib, line 78
 * @param debug Whether or not to output debug logs. // library marker kkossev.deviceProfileLib, line 79
 * @return returns either tuyaDPs or attributes map, depending on where the preference (param) is found // library marker kkossev.deviceProfileLib, line 80
 * @return null if param is not defined for this device. // library marker kkossev.deviceProfileLib, line 81
 */ // library marker kkossev.deviceProfileLib, line 82
def getPreferencesMap( String param, boolean debug=false ) { // library marker kkossev.deviceProfileLib, line 83
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 84
    if (!(param in DEVICE.preferences)) { // library marker kkossev.deviceProfileLib, line 85
        if (debug) log.warn "getPreferencesMap: preference ${param} not defined for this device!" // library marker kkossev.deviceProfileLib, line 86
        return null // library marker kkossev.deviceProfileLib, line 87
    } // library marker kkossev.deviceProfileLib, line 88
    def preference  // library marker kkossev.deviceProfileLib, line 89
    try { // library marker kkossev.deviceProfileLib, line 90
        preference = DEVICE.preferences["$param"] // library marker kkossev.deviceProfileLib, line 91
        if (debug) log.debug "getPreferencesMap: preference ${param} found. value is ${preference}" // library marker kkossev.deviceProfileLib, line 92
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 93
            // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLib, line 94
            logDebug "getPreferencesMap: preference ${param} is boolean" // library marker kkossev.deviceProfileLib, line 95
            return null     // no maps for predefined preferences ! // library marker kkossev.deviceProfileLib, line 96
        } // library marker kkossev.deviceProfileLib, line 97
        if (preference.isNumber()) { // library marker kkossev.deviceProfileLib, line 98
            // find the preference in the tuyaDPs map // library marker kkossev.deviceProfileLib, line 99
            int dp = safeToInt(preference) // library marker kkossev.deviceProfileLib, line 100
            def dpMaps   =  DEVICE.tuyaDPs  // library marker kkossev.deviceProfileLib, line 101
            foundMap = dpMaps.find { it.dp == dp } // library marker kkossev.deviceProfileLib, line 102
        } // library marker kkossev.deviceProfileLib, line 103
        else { // cluster:attribute // library marker kkossev.deviceProfileLib, line 104
            if (debug) log.trace "${DEVICE.attributes}" // library marker kkossev.deviceProfileLib, line 105
            def dpMaps   =  DEVICE.tuyaDPs  // library marker kkossev.deviceProfileLib, line 106
            foundMap = DEVICE.attributes.find { it.at == preference } // library marker kkossev.deviceProfileLib, line 107
        } // library marker kkossev.deviceProfileLib, line 108
        // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 109
    } catch (Exception e) { // library marker kkossev.deviceProfileLib, line 110
        if (debug) log.warn "getPreferencesMap: exception ${e} caught when getting preference ${param} !" // library marker kkossev.deviceProfileLib, line 111
        return null // library marker kkossev.deviceProfileLib, line 112
    } // library marker kkossev.deviceProfileLib, line 113
    if (debug) log.debug "getPreferencesMap: foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 114
    return foundMap      // library marker kkossev.deviceProfileLib, line 115
} // library marker kkossev.deviceProfileLib, line 116

/** // library marker kkossev.deviceProfileLib, line 118
 * Resets the device preferences to their default values. // library marker kkossev.deviceProfileLib, line 119
 * @param debug A boolean indicating whether to output debug information. // library marker kkossev.deviceProfileLib, line 120
 */ // library marker kkossev.deviceProfileLib, line 121
def resetPreferencesToDefaults(boolean debug=false ) { // library marker kkossev.deviceProfileLib, line 122
    Map preferences = DEVICE?.preferences // library marker kkossev.deviceProfileLib, line 123
    if (preferences == null) { // library marker kkossev.deviceProfileLib, line 124
        logWarn "Preferences not found!" // library marker kkossev.deviceProfileLib, line 125
        return // library marker kkossev.deviceProfileLib, line 126
    } // library marker kkossev.deviceProfileLib, line 127
    Map parMap = [:] // library marker kkossev.deviceProfileLib, line 128
    preferences.each{ parName, mapValue ->  // library marker kkossev.deviceProfileLib, line 129
        if (debug) log.trace "$parName $mapValue" // library marker kkossev.deviceProfileLib, line 130
        // TODO - could be also 'true' or 'false' ... // library marker kkossev.deviceProfileLib, line 131
        if (mapValue in [true, false]) { // library marker kkossev.deviceProfileLib, line 132
            logDebug "Preference ${parName} is predefined -> (${mapValue})" // library marker kkossev.deviceProfileLib, line 133
            // TODO - set the predefined value // library marker kkossev.deviceProfileLib, line 134
            /* // library marker kkossev.deviceProfileLib, line 135
            if (debug) log.info "par ${parName} defaultValue = ${parMap.defaultValue}" // library marker kkossev.deviceProfileLib, line 136
            device.updateSetting("${parMap.name}",[value:parMap.defaultValue, type:parMap.type])      // library marker kkossev.deviceProfileLib, line 137
            */        // library marker kkossev.deviceProfileLib, line 138
            return // continue // library marker kkossev.deviceProfileLib, line 139
        } // library marker kkossev.deviceProfileLib, line 140
        // find the individual preference map // library marker kkossev.deviceProfileLib, line 141
        parMap = getPreferencesMap(parName, false) // library marker kkossev.deviceProfileLib, line 142
        if (parMap == null) { // library marker kkossev.deviceProfileLib, line 143
            logWarn "Preference ${parName} not found in tuyaDPs or attributes map!" // library marker kkossev.deviceProfileLib, line 144
            return // continue // library marker kkossev.deviceProfileLib, line 145
        }    // library marker kkossev.deviceProfileLib, line 146
        // parMap = [at:0xE002:0xE005, name:staticDetectionSensitivity, type:number, dt:UINT8, rw:rw, min:0, max:5, step:1, scale:1, unit:x, title:Static Detection Sensitivity, description:Static detection sensitivity] // library marker kkossev.deviceProfileLib, line 147
        if (parMap.defaultValue == null) { // library marker kkossev.deviceProfileLib, line 148
            logWarn "no default value for preference ${parName} !" // library marker kkossev.deviceProfileLib, line 149
            return // continue // library marker kkossev.deviceProfileLib, line 150
        } // library marker kkossev.deviceProfileLib, line 151
        if (debug) log.info "par ${parName} defaultValue = ${parMap.defaultValue}" // library marker kkossev.deviceProfileLib, line 152
        device.updateSetting("${parMap.name}",[value:parMap.defaultValue, type:parMap.type]) // library marker kkossev.deviceProfileLib, line 153
    } // library marker kkossev.deviceProfileLib, line 154
    logInfo "Preferences reset to default values" // library marker kkossev.deviceProfileLib, line 155
} // library marker kkossev.deviceProfileLib, line 156

/** // library marker kkossev.deviceProfileLib, line 158
 * Returns a list of valid parameters per model based on the device preferences. // library marker kkossev.deviceProfileLib, line 159
 * // library marker kkossev.deviceProfileLib, line 160
 * @return List of valid parameters. // library marker kkossev.deviceProfileLib, line 161
 */ // library marker kkossev.deviceProfileLib, line 162
def getValidParsPerModel() { // library marker kkossev.deviceProfileLib, line 163
    List<String> validPars = [] // library marker kkossev.deviceProfileLib, line 164
    if (DEVICE?.preferences != null && DEVICE?.preferences != [:]) { // library marker kkossev.deviceProfileLib, line 165
        // use the preferences to validate the parameters // library marker kkossev.deviceProfileLib, line 166
        validPars = DEVICE.preferences.keySet().toList() // library marker kkossev.deviceProfileLib, line 167
    } // library marker kkossev.deviceProfileLib, line 168
    return validPars // library marker kkossev.deviceProfileLib, line 169
} // library marker kkossev.deviceProfileLib, line 170


/** // library marker kkossev.deviceProfileLib, line 173
 * Called from setPar() method only! // library marker kkossev.deviceProfileLib, line 174
 * Validates the parameter value based on the given dpMap type and scales it if needed. // library marker kkossev.deviceProfileLib, line 175
 *  // library marker kkossev.deviceProfileLib, line 176
 * @param dpMap The map containing the parameter type, minimum and maximum values. // library marker kkossev.deviceProfileLib, line 177
 * @param val The value to be validated and scaled. // library marker kkossev.deviceProfileLib, line 178
 * @return The validated and scaled value if it is within the specified range, null otherwise. // library marker kkossev.deviceProfileLib, line 179
 */ // library marker kkossev.deviceProfileLib, line 180
def validateAndScaleParameterValue(Map dpMap, String val) { // library marker kkossev.deviceProfileLib, line 181
    def value = null    // validated value - integer, floar // library marker kkossev.deviceProfileLib, line 182
    def scaledValue = null // library marker kkossev.deviceProfileLib, line 183
    logDebug "validateAndScaleParameterValue dpMap=${dpMap} val=${val}" // library marker kkossev.deviceProfileLib, line 184
    switch (dpMap.type) { // library marker kkossev.deviceProfileLib, line 185
        case "number" : // library marker kkossev.deviceProfileLib, line 186
            value = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 187
            scaledValue = value // library marker kkossev.deviceProfileLib, line 188
            // scale the value - added 10/26/2023 also for integer values ! // library marker kkossev.deviceProfileLib, line 189
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 190
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 191
            }             // library marker kkossev.deviceProfileLib, line 192
            break // library marker kkossev.deviceProfileLib, line 193
        case "decimal" : // library marker kkossev.deviceProfileLib, line 194
             value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 195
            // scale the value // library marker kkossev.deviceProfileLib, line 196
            if (dpMap.scale != null) { // library marker kkossev.deviceProfileLib, line 197
                scaledValue = (value * dpMap.scale) as Integer // library marker kkossev.deviceProfileLib, line 198
            } // library marker kkossev.deviceProfileLib, line 199
            break // library marker kkossev.deviceProfileLib, line 200
        case "bool" : // library marker kkossev.deviceProfileLib, line 201
            if (val == '0' || val == 'false')     { value = scaledValue = 0 } // library marker kkossev.deviceProfileLib, line 202
            else if (val == '1' || val == 'true') { value = scaledValue = 1 } // library marker kkossev.deviceProfileLib, line 203
            else { // library marker kkossev.deviceProfileLib, line 204
                log.warn "${device.displayName} sevalidateAndScaleParameterValue: bool parameter <b>${val}</b>. value must be one of <b>0 1 false true</b>" // library marker kkossev.deviceProfileLib, line 205
                return null // library marker kkossev.deviceProfileLib, line 206
            } // library marker kkossev.deviceProfileLib, line 207
            break // library marker kkossev.deviceProfileLib, line 208
        case "enum" : // library marker kkossev.deviceProfileLib, line 209
            // val could be both integer or float value ... check if the scaling is different than 1 in dpMap  // library marker kkossev.deviceProfileLib, line 210

            if (dpMap.scale != null && safeToInt(dpMap.scale) != 1) { // library marker kkossev.deviceProfileLib, line 212
                // we have a float parameter input - convert it to int // library marker kkossev.deviceProfileLib, line 213
                value = safeToDouble(val, -1.0) // library marker kkossev.deviceProfileLib, line 214
                scaledValue = (value * safeToInt(dpMap.scale)) as Integer // library marker kkossev.deviceProfileLib, line 215
            } // library marker kkossev.deviceProfileLib, line 216
            else { // library marker kkossev.deviceProfileLib, line 217
                value = scaledValue = safeToInt(val, -1) // library marker kkossev.deviceProfileLib, line 218
            } // library marker kkossev.deviceProfileLib, line 219
            if (scaledValue == null || scaledValue < 0) { // library marker kkossev.deviceProfileLib, line 220
                // get the keys of dpMap.map as a List // library marker kkossev.deviceProfileLib, line 221
                List<String> keys = dpMap.map.keySet().toList() // library marker kkossev.deviceProfileLib, line 222
                log.warn "${device.displayName} validateAndScaleParameterValue: enum parameter <b>${val}</b>. value must be one of <b>${keys}</b>" // library marker kkossev.deviceProfileLib, line 223
                return null // library marker kkossev.deviceProfileLib, line 224
            } // library marker kkossev.deviceProfileLib, line 225
            break // library marker kkossev.deviceProfileLib, line 226
        default : // library marker kkossev.deviceProfileLib, line 227
            logWarn "validateAndScaleParameterValue: unsupported dpMap type <b>${parType}</b>" // library marker kkossev.deviceProfileLib, line 228
            return null // library marker kkossev.deviceProfileLib, line 229
    } // library marker kkossev.deviceProfileLib, line 230
    //log.warn "validateAndScaleParameterValue before checking  scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 231
    // check if the value is within the specified range // library marker kkossev.deviceProfileLib, line 232
    if ((dpMap.min != null && value < dpMap.min) || (dpMap.max != null && value > dpMap.max)) { // library marker kkossev.deviceProfileLib, line 233
        log.warn "${device.displayName} validateAndScaleParameterValue: invalid ${dpMap.name} parameter value <b>${value}</b> (scaled ${scaledValue}). Value must be within ${dpMap.min} and ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 234
        return null // library marker kkossev.deviceProfileLib, line 235
    } // library marker kkossev.deviceProfileLib, line 236
    //log.warn "validateAndScaleParameterValue returning scaledValue=${scaledValue}" // library marker kkossev.deviceProfileLib, line 237
    return scaledValue // library marker kkossev.deviceProfileLib, line 238
} // library marker kkossev.deviceProfileLib, line 239

/** // library marker kkossev.deviceProfileLib, line 241
 * Sets the parameter value for the device. // library marker kkossev.deviceProfileLib, line 242
 * @param par The name of the parameter to set. // library marker kkossev.deviceProfileLib, line 243
 * @param val The value to set the parameter to. // library marker kkossev.deviceProfileLib, line 244
 * @return Nothing. // library marker kkossev.deviceProfileLib, line 245
 */ // library marker kkossev.deviceProfileLib, line 246
def setPar( par=null, val=null ) // library marker kkossev.deviceProfileLib, line 247
{ // library marker kkossev.deviceProfileLib, line 248
    if (DEVICE?.preferences == null || DEVICE?.preferences == [:]) { // library marker kkossev.deviceProfileLib, line 249
        return null // library marker kkossev.deviceProfileLib, line 250
    } // library marker kkossev.deviceProfileLib, line 251
    // new method // library marker kkossev.deviceProfileLib, line 252
    logDebug "setPar new method: setting parameter ${par} to ${val}" // library marker kkossev.deviceProfileLib, line 253
    ArrayList<String> cmds = [] // library marker kkossev.deviceProfileLib, line 254
    Boolean validated = false // library marker kkossev.deviceProfileLib, line 255
    if (par == null) { // library marker kkossev.deviceProfileLib, line 256
        log.warn "${device.displayName} setPar: 'parameter' must be one of these : ${getValidParsPerModel()}" // library marker kkossev.deviceProfileLib, line 257
        return // library marker kkossev.deviceProfileLib, line 258
    }         // library marker kkossev.deviceProfileLib, line 259
    if (!(par in getValidParsPerModel())) { // library marker kkossev.deviceProfileLib, line 260
        log.warn "${device.displayName} setPar: parameter '${par}' must be one of these : ${getValidParsPerModel()}" // library marker kkossev.deviceProfileLib, line 261
        return // library marker kkossev.deviceProfileLib, line 262
    } // library marker kkossev.deviceProfileLib, line 263
    // find the tuayDPs map for the par // library marker kkossev.deviceProfileLib, line 264
    Map dpMap = getPreferencesMap(par, false) // library marker kkossev.deviceProfileLib, line 265
    if ( dpMap == null ) { // library marker kkossev.deviceProfileLib, line 266
        log.warn "${device.displayName} setPar: tuyaDPs map not found for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 267
        return // library marker kkossev.deviceProfileLib, line 268
    } // library marker kkossev.deviceProfileLib, line 269
    if (val == null) { // library marker kkossev.deviceProfileLib, line 270
        log.warn "${device.displayName} setPar: 'value' must be specified for parameter <b>${par}</b> in the range ${dpMap.min} to ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 271
        return // library marker kkossev.deviceProfileLib, line 272
    } // library marker kkossev.deviceProfileLib, line 273
    // convert the val to the correct type and scale it if needed // library marker kkossev.deviceProfileLib, line 274
    def scaledValue = validateAndScaleParameterValue(dpMap, val as String) // library marker kkossev.deviceProfileLib, line 275
    if (scaledValue == null) { // library marker kkossev.deviceProfileLib, line 276
        log.warn "${device.displayName} setPar: invalid parameter value <b>${val}</b>. Must be in the range ${dpMap.min} to ${dpMap.max}" // library marker kkossev.deviceProfileLib, line 277
        return // library marker kkossev.deviceProfileLib, line 278
    } // library marker kkossev.deviceProfileLib, line 279
    // update the device setting // library marker kkossev.deviceProfileLib, line 280
    // TODO: decide whether the setting must be updated here, or after it is echeod back from the device // library marker kkossev.deviceProfileLib, line 281
    try { // library marker kkossev.deviceProfileLib, line 282
        device.updateSetting("$par", [value:val, type:dpMap.type]) // library marker kkossev.deviceProfileLib, line 283
    } // library marker kkossev.deviceProfileLib, line 284
    catch (e) { // library marker kkossev.deviceProfileLib, line 285
        logWarn "setPar: Exception '${e}'caught while updateSetting <b>$par</b>(<b>$val</b>) type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 286
        return // library marker kkossev.deviceProfileLib, line 287
    } // library marker kkossev.deviceProfileLib, line 288
    logDebug "parameter ${par} value ${val}, type ${dpMap.type} validated and scaled to ${scaledValue} type=${dpMap.type}" // library marker kkossev.deviceProfileLib, line 289
    // if there is a dedicated set function, use it // library marker kkossev.deviceProfileLib, line 290
    String capitalizedFirstChar = par[0].toUpperCase() + par[1..-1] // library marker kkossev.deviceProfileLib, line 291
    String setFunction = "set${capitalizedFirstChar}" // library marker kkossev.deviceProfileLib, line 292
    if (this.respondsTo(setFunction)) { // library marker kkossev.deviceProfileLib, line 293
        logDebug "setPar: found setFunction=${setFunction}, scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 294
        // execute the setFunction // library marker kkossev.deviceProfileLib, line 295
        try { // library marker kkossev.deviceProfileLib, line 296
            cmds = "$setFunction"(scaledValue) // library marker kkossev.deviceProfileLib, line 297
        } // library marker kkossev.deviceProfileLib, line 298
        catch (e) { // library marker kkossev.deviceProfileLib, line 299
            logWarn "setPar: Exception '${e}'caught while processing <b>$setFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 300
            return // library marker kkossev.deviceProfileLib, line 301
        } // library marker kkossev.deviceProfileLib, line 302
        logDebug "setFunction result is ${cmds}"        // library marker kkossev.deviceProfileLib, line 303
        if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 304
            logInfo "setPar: successfluly executed setPar <b>$setFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 305
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 306
            return // library marker kkossev.deviceProfileLib, line 307
        }             // library marker kkossev.deviceProfileLib, line 308
        else { // library marker kkossev.deviceProfileLib, line 309
            logWarn "setPar: setFunction <b>$setFunction</b>(<b>$scaledValue</b>) returned null or empty list" // library marker kkossev.deviceProfileLib, line 310
            // continue with the default processing // library marker kkossev.deviceProfileLib, line 311
        } // library marker kkossev.deviceProfileLib, line 312
    } // library marker kkossev.deviceProfileLib, line 313
    // check whether this is a tuya DP or a cluster:attribute parameter // library marker kkossev.deviceProfileLib, line 314
    if (dpMap.dp != null && dpMap.dp.isNumber()) { // library marker kkossev.deviceProfileLib, line 315
        // Tuya DP // library marker kkossev.deviceProfileLib, line 316
        cmds = sendTuyaParameter(dpMap,  par, scaledValue)  // library marker kkossev.deviceProfileLib, line 317
        if (cmds == null || cmds == []) { // library marker kkossev.deviceProfileLib, line 318
            logWarn "setPar: sendTuyaParameter par ${par} scaledValue ${scaledValue} returned null or empty list" // library marker kkossev.deviceProfileLib, line 319
            return // library marker kkossev.deviceProfileLib, line 320
        } // library marker kkossev.deviceProfileLib, line 321
        else { // library marker kkossev.deviceProfileLib, line 322
            logInfo "setPar: successfluly executed setPar <b>$setFunction</b>(<b>$val</b> (scaledValue=${scaledValue}))" // library marker kkossev.deviceProfileLib, line 323
            sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 324
            return // library marker kkossev.deviceProfileLib, line 325
        } // library marker kkossev.deviceProfileLib, line 326
    } // library marker kkossev.deviceProfileLib, line 327
    else if (dpMap.at != null) { // library marker kkossev.deviceProfileLib, line 328
        // cluster:attribute // library marker kkossev.deviceProfileLib, line 329
        int cluster // library marker kkossev.deviceProfileLib, line 330
        int attribute // library marker kkossev.deviceProfileLib, line 331
        int dt // library marker kkossev.deviceProfileLib, line 332
        int mfgCode // library marker kkossev.deviceProfileLib, line 333
        try { // library marker kkossev.deviceProfileLib, line 334
            cluster = hubitat.helper.HexUtils.hexStringToInt(dpMap.at.split(":")[0]) // library marker kkossev.deviceProfileLib, line 335
            attribute = hubitat.helper.HexUtils.hexStringToInt(dpMap.at.split(":")[1]) // library marker kkossev.deviceProfileLib, line 336
            dt = hubitat.helper.HexUtils.hexStringToInt(dpMap.dt) // library marker kkossev.deviceProfileLib, line 337
            mfgCode = dpMap.mfgCode != null ? hubitat.helper.HexUtils.hexStringToInt(dpMap.mfgCode) : null // library marker kkossev.deviceProfileLib, line 338
        } // library marker kkossev.deviceProfileLib, line 339
        catch (e) { // library marker kkossev.deviceProfileLib, line 340
            logWarn "setPar: Exception '${e}'caught while splitting cluser and attribute <b>$setFunction</b>(<b>$scaledValue</b>) (val=${val}))" // library marker kkossev.deviceProfileLib, line 341
            return // library marker kkossev.deviceProfileLib, line 342
        } // library marker kkossev.deviceProfileLib, line 343
        Map mapMfCode = ["mfgCode":mfgCode] // library marker kkossev.deviceProfileLib, line 344
        logDebug "setPar: found cluster=${cluster} attribute=${attribute} dt=${dpMap.dt} mapMfCode=${mapMfCode} scaledValue=${scaledValue}  (val=${val})" // library marker kkossev.deviceProfileLib, line 345
        if (mfgCode != null) { // library marker kkossev.deviceProfileLib, line 346
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, mapMfCode, delay=200) // library marker kkossev.deviceProfileLib, line 347
        } // library marker kkossev.deviceProfileLib, line 348
        else { // library marker kkossev.deviceProfileLib, line 349
            cmds = zigbee.writeAttribute(cluster, attribute, dt, scaledValue, [:], delay=200) // library marker kkossev.deviceProfileLib, line 350
        } // library marker kkossev.deviceProfileLib, line 351
    } // library marker kkossev.deviceProfileLib, line 352
    else { // library marker kkossev.deviceProfileLib, line 353
        logWarn "setPar: invalid dp or at value <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 354
        return // library marker kkossev.deviceProfileLib, line 355
    } // library marker kkossev.deviceProfileLib, line 356


    logInfo "setPar: successfluly executed setPar <b>$setFunction</b>(<b>$scaledValue</b>)" // library marker kkossev.deviceProfileLib, line 359
    sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 360
    return // library marker kkossev.deviceProfileLib, line 361
} // library marker kkossev.deviceProfileLib, line 362

// function to send a Tuya command to data point taken from dpMap with value tuyaValue and type taken from dpMap // library marker kkossev.deviceProfileLib, line 364
// TODO - reuse it !!! // library marker kkossev.deviceProfileLib, line 365
def sendTuyaParameter( Map dpMap, String par, tuyaValue) { // library marker kkossev.deviceProfileLib, line 366
    //logDebug "sendTuyaParameter: trying to send parameter ${par} value ${tuyaValue}" // library marker kkossev.deviceProfileLib, line 367
    ArrayList<String> cmds = [] // library marker kkossev.deviceProfileLib, line 368
    if (dpMap == null) { // library marker kkossev.deviceProfileLib, line 369
        log.warn "${device.displayName} sendTuyaParameter: tuyaDPs map not found for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 370
        return null // library marker kkossev.deviceProfileLib, line 371
    } // library marker kkossev.deviceProfileLib, line 372
    String dp = zigbee.convertToHexString(dpMap.dp, 2) // library marker kkossev.deviceProfileLib, line 373
    if (dpMap.dp <= 0 || dpMap.dp >= 256) { // library marker kkossev.deviceProfileLib, line 374
        log.warn "${device.displayName} sendTuyaParameter: invalid dp <b>${dpMap.dp}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 375
        return null  // library marker kkossev.deviceProfileLib, line 376
    } // library marker kkossev.deviceProfileLib, line 377
    String dpType = dpMap.type == "bool" ? DP_TYPE_BOOL : dpMap.type == "enum" ? DP_TYPE_ENUM : (dpMap.type in ["value", "number", "decimal"]) ? DP_TYPE_VALUE: null // library marker kkossev.deviceProfileLib, line 378
    //log.debug "dpType = ${dpType}" // library marker kkossev.deviceProfileLib, line 379
    if (dpType == null) { // library marker kkossev.deviceProfileLib, line 380
        log.warn "${device.displayName} sendTuyaParameter: invalid dpType <b>${dpMap.type}</b> for parameter <b>${par}</b>" // library marker kkossev.deviceProfileLib, line 381
        return null  // library marker kkossev.deviceProfileLib, line 382
    } // library marker kkossev.deviceProfileLib, line 383
    // sendTuyaCommand // library marker kkossev.deviceProfileLib, line 384
    def dpValHex = dpType == DP_TYPE_VALUE ? zigbee.convertToHexString(tuyaValue as int, 8) : zigbee.convertToHexString(tuyaValue as int, 2)  // library marker kkossev.deviceProfileLib, line 385
    logDebug "sendTuyaParameter: sending parameter ${par} dpValHex ${dpValHex} (raw=${tuyaValue}) Tuya dp=${dp} dpType=${dpType} " // library marker kkossev.deviceProfileLib, line 386
    cmds = sendTuyaCommand( dp, dpType, dpValHex) // library marker kkossev.deviceProfileLib, line 387
    return cmds // library marker kkossev.deviceProfileLib, line 388
} // library marker kkossev.deviceProfileLib, line 389

/** // library marker kkossev.deviceProfileLib, line 391
 * Sends a command to the device. // library marker kkossev.deviceProfileLib, line 392
 * @param command The command to send. Must be one of the commands defined in the DEVICE.commands map. // library marker kkossev.deviceProfileLib, line 393
 * @param val The value to send with the command. // library marker kkossev.deviceProfileLib, line 394
 * @return void // library marker kkossev.deviceProfileLib, line 395
 */ // library marker kkossev.deviceProfileLib, line 396
def sendCommand( command=null, val=null ) // library marker kkossev.deviceProfileLib, line 397
{ // library marker kkossev.deviceProfileLib, line 398
    //logDebug "sending command ${command}(${val}))" // library marker kkossev.deviceProfileLib, line 399
    ArrayList<String> cmds = [] // library marker kkossev.deviceProfileLib, line 400
    def supportedCommandsMap = DEVICE.commands  // library marker kkossev.deviceProfileLib, line 401
    if (supportedCommandsMap == null || supportedCommandsMap == []) { // library marker kkossev.deviceProfileLib, line 402
        logWarn "sendCommand: no commands defined for device profile ${getDeviceGroup()} !" // library marker kkossev.deviceProfileLib, line 403
        return // library marker kkossev.deviceProfileLib, line 404
    } // library marker kkossev.deviceProfileLib, line 405
    // TODO: compare ignoring the upper/lower case of the command. // library marker kkossev.deviceProfileLib, line 406
    def supportedCommandsList =  DEVICE.commands.keySet() as List  // library marker kkossev.deviceProfileLib, line 407
    // check if the command is defined in the DEVICE commands map // library marker kkossev.deviceProfileLib, line 408
    if (command == null || !(command in supportedCommandsList)) { // library marker kkossev.deviceProfileLib, line 409
        logWarn "sendCommand: the command <b>${(command ?: '')}</b> for device profile '${DEVICE.description}' must be one of these : ${supportedCommandsList}" // library marker kkossev.deviceProfileLib, line 410
        return // library marker kkossev.deviceProfileLib, line 411
    } // library marker kkossev.deviceProfileLib, line 412
    def func // library marker kkossev.deviceProfileLib, line 413
    try { // library marker kkossev.deviceProfileLib, line 414
        func = DEVICE.commands.find { it.key == command }.value // library marker kkossev.deviceProfileLib, line 415
        if (val != null) { // library marker kkossev.deviceProfileLib, line 416
            cmds = "${func}"(val) // library marker kkossev.deviceProfileLib, line 417
            logInfo "executed <b>$func</b>($val)" // library marker kkossev.deviceProfileLib, line 418
        } // library marker kkossev.deviceProfileLib, line 419
        else { // library marker kkossev.deviceProfileLib, line 420
            cmds = "${func}"() // library marker kkossev.deviceProfileLib, line 421
            logInfo "executed <b>$func</b>()" // library marker kkossev.deviceProfileLib, line 422
        } // library marker kkossev.deviceProfileLib, line 423
    } // library marker kkossev.deviceProfileLib, line 424
    catch (e) { // library marker kkossev.deviceProfileLib, line 425
        logWarn "sendCommand: Exception '${e}' caught while processing <b>$func</b>(${val})" // library marker kkossev.deviceProfileLib, line 426
        return // library marker kkossev.deviceProfileLib, line 427
    } // library marker kkossev.deviceProfileLib, line 428
    if (cmds != null && cmds != []) { // library marker kkossev.deviceProfileLib, line 429
        sendZigbeeCommands( cmds ) // library marker kkossev.deviceProfileLib, line 430
    } // library marker kkossev.deviceProfileLib, line 431
} // library marker kkossev.deviceProfileLib, line 432

/** // library marker kkossev.deviceProfileLib, line 434
 * This method takes a string parameter and a boolean debug flag as input and returns a map containing the input details. // library marker kkossev.deviceProfileLib, line 435
 * The method checks if the input parameter is defined in the device preferences and returns null if it is not. // library marker kkossev.deviceProfileLib, line 436
 * It then checks if the input parameter is a boolean value and skips it if it is. // library marker kkossev.deviceProfileLib, line 437
 * The method also checks if the input parameter is a number and sets the isTuyaDP flag accordingly. // library marker kkossev.deviceProfileLib, line 438
 * If the input parameter is read-only, the method returns null. // library marker kkossev.deviceProfileLib, line 439
 * The method then populates the input map with the name, type, title, description, range, options, and default value of the input parameter. // library marker kkossev.deviceProfileLib, line 440
 * If the input parameter type is not supported, the method returns null. // library marker kkossev.deviceProfileLib, line 441
 * @param param The input parameter to be checked. // library marker kkossev.deviceProfileLib, line 442
 * @param debug A boolean flag indicating whether to log debug messages or not. // library marker kkossev.deviceProfileLib, line 443
 * @return A map containing the input details. // library marker kkossev.deviceProfileLib, line 444
 */ // library marker kkossev.deviceProfileLib, line 445
def inputIt( String param, boolean debug=false ) { // library marker kkossev.deviceProfileLib, line 446
    Map input = [:] // library marker kkossev.deviceProfileLib, line 447
    Map foundMap = [:] // library marker kkossev.deviceProfileLib, line 448
    if (!(param in DEVICE.preferences)) { // library marker kkossev.deviceProfileLib, line 449
        if (debug) log.warn "inputIt: preference ${param} not defined for this device!" // library marker kkossev.deviceProfileLib, line 450
        return null // library marker kkossev.deviceProfileLib, line 451
    } // library marker kkossev.deviceProfileLib, line 452
    def preference // library marker kkossev.deviceProfileLib, line 453
    boolean isTuyaDP  // library marker kkossev.deviceProfileLib, line 454
    try { // library marker kkossev.deviceProfileLib, line 455
        preference = DEVICE.preferences["$param"] // library marker kkossev.deviceProfileLib, line 456
    } // library marker kkossev.deviceProfileLib, line 457
    catch (e) { // library marker kkossev.deviceProfileLib, line 458
        if (debug) log.warn "inputIt: exception ${e} caught while parsing preference ${param} value ${preference}" // library marker kkossev.deviceProfileLib, line 459
        return null // library marker kkossev.deviceProfileLib, line 460
    }    // library marker kkossev.deviceProfileLib, line 461
    //  check for boolean values // library marker kkossev.deviceProfileLib, line 462
    try { // library marker kkossev.deviceProfileLib, line 463
        if (preference in [true, false]) { // library marker kkossev.deviceProfileLib, line 464
            if (debug) log.warn "inputIt: preference ${param} is boolean value ${preference} - skipping it for now!" // library marker kkossev.deviceProfileLib, line 465
            return null // library marker kkossev.deviceProfileLib, line 466
        } // library marker kkossev.deviceProfileLib, line 467
    } // library marker kkossev.deviceProfileLib, line 468
    catch (e) { // library marker kkossev.deviceProfileLib, line 469
        if (debug) log.warn "inputIt: exception ${e} caught while checking for boolean values preference ${param} value ${preference}" // library marker kkossev.deviceProfileLib, line 470
        return null // library marker kkossev.deviceProfileLib, line 471
    }  // library marker kkossev.deviceProfileLib, line 472

    try { // library marker kkossev.deviceProfileLib, line 474
        isTuyaDP = preference.isNumber() // library marker kkossev.deviceProfileLib, line 475
    } // library marker kkossev.deviceProfileLib, line 476
    catch (e) { // library marker kkossev.deviceProfileLib, line 477
        if (debug) log.warn "inputIt: exception ${e} caught while checking isNumber() preference ${param} value ${preference}" // library marker kkossev.deviceProfileLib, line 478
        return null // library marker kkossev.deviceProfileLib, line 479
    }  // library marker kkossev.deviceProfileLib, line 480

    //if (debug) log.debug "inputIt: preference ${param} found. value is ${preference} isTuyaDP=${isTuyaDP}" // library marker kkossev.deviceProfileLib, line 482
    foundMap = getPreferencesMap(param) // library marker kkossev.deviceProfileLib, line 483
    //if (debug) log.debug "foundMap = ${foundMap}" // library marker kkossev.deviceProfileLib, line 484
    if (foundMap == null) { // library marker kkossev.deviceProfileLib, line 485
        if (debug) log.warn "inputIt: map not found for param '${param}'!" // library marker kkossev.deviceProfileLib, line 486
        return null // library marker kkossev.deviceProfileLib, line 487
    } // library marker kkossev.deviceProfileLib, line 488
    if (foundMap.rw != "rw") { // library marker kkossev.deviceProfileLib, line 489
        if (debug) log.warn "inputIt: param '${param}' is read only!" // library marker kkossev.deviceProfileLib, line 490
        return null // library marker kkossev.deviceProfileLib, line 491
    }         // library marker kkossev.deviceProfileLib, line 492
    input.name = foundMap.name // library marker kkossev.deviceProfileLib, line 493
    input.type = foundMap.type    // bool, enum, number, decimal // library marker kkossev.deviceProfileLib, line 494
    input.title = foundMap.title // library marker kkossev.deviceProfileLib, line 495
    input.description = foundMap.description // library marker kkossev.deviceProfileLib, line 496
    if (input.type in ["number", "decimal"]) { // library marker kkossev.deviceProfileLib, line 497
        if (foundMap.min != null && foundMap.max != null) { // library marker kkossev.deviceProfileLib, line 498
            input.range = "${foundMap.min}..${foundMap.max}" // library marker kkossev.deviceProfileLib, line 499
        } // library marker kkossev.deviceProfileLib, line 500
        if (input.range != null && input.description !=null) { // library marker kkossev.deviceProfileLib, line 501
            input.description += "<br><i>Range: ${input.range}</i>" // library marker kkossev.deviceProfileLib, line 502
            if (foundMap.unit != null && foundMap.unit != "") { // library marker kkossev.deviceProfileLib, line 503
                input.description += " <i>(${foundMap.unit})</i>" // library marker kkossev.deviceProfileLib, line 504
            } // library marker kkossev.deviceProfileLib, line 505
        } // library marker kkossev.deviceProfileLib, line 506
    } // library marker kkossev.deviceProfileLib, line 507
    else if (input.type == "enum") { // library marker kkossev.deviceProfileLib, line 508
        input.options = foundMap.map // library marker kkossev.deviceProfileLib, line 509
    }/* // library marker kkossev.deviceProfileLib, line 510
    else if (input.type == "bool") { // library marker kkossev.deviceProfileLib, line 511
        input.options = ["true", "false"] // library marker kkossev.deviceProfileLib, line 512
    }*/ // library marker kkossev.deviceProfileLib, line 513
    else { // library marker kkossev.deviceProfileLib, line 514
        if (debug) log.warn "inputIt: unsupported type ${input.type} for param '${param}'!" // library marker kkossev.deviceProfileLib, line 515
        return null // library marker kkossev.deviceProfileLib, line 516
    }    // library marker kkossev.deviceProfileLib, line 517
    if (input.defaultValue != null) { // library marker kkossev.deviceProfileLib, line 518
        input.defaultValue = foundMap.defaultValue // library marker kkossev.deviceProfileLib, line 519
    } // library marker kkossev.deviceProfileLib, line 520
    return input // library marker kkossev.deviceProfileLib, line 521
} // library marker kkossev.deviceProfileLib, line 522


/** // library marker kkossev.deviceProfileLib, line 525
 * Returns the device name and profile based on the device model and manufacturer. // library marker kkossev.deviceProfileLib, line 526
 * @param model The device model (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 527
 * @param manufacturer The device manufacturer (optional). If not provided, it will be retrieved from the device data value. // library marker kkossev.deviceProfileLib, line 528
 * @return A list containing the device name and profile. // library marker kkossev.deviceProfileLib, line 529
 */ // library marker kkossev.deviceProfileLib, line 530
def getDeviceNameAndProfile( model=null, manufacturer=null) { // library marker kkossev.deviceProfileLib, line 531
    def deviceName         = UNKNOWN // library marker kkossev.deviceProfileLib, line 532
    def deviceProfile      = UNKNOWN // library marker kkossev.deviceProfileLib, line 533
    String deviceModel        = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 534
    String deviceManufacturer = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 535
    deviceProfilesV2.each { profileName, profileMap -> // library marker kkossev.deviceProfileLib, line 536
        profileMap.fingerprints.each { fingerprint -> // library marker kkossev.deviceProfileLib, line 537
            if (fingerprint.model == deviceModel && fingerprint.manufacturer == deviceManufacturer) { // library marker kkossev.deviceProfileLib, line 538
                deviceProfile = profileName // library marker kkossev.deviceProfileLib, line 539
                deviceName = fingerprint.deviceJoinName ?: deviceProfilesV2[deviceProfile].deviceJoinName ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 540
                logDebug "<b>found exact match</b> for model ${deviceModel} manufacturer ${deviceManufacturer} : <b>profileName=${deviceProfile}</b> deviceName =${deviceName}" // library marker kkossev.deviceProfileLib, line 541
                return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 542
            } // library marker kkossev.deviceProfileLib, line 543
        } // library marker kkossev.deviceProfileLib, line 544
    } // library marker kkossev.deviceProfileLib, line 545
    if (deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 546
        logWarn "<b>NOT FOUND!</b> deviceName =${deviceName} profileName=${deviceProfile} for model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 547
    } // library marker kkossev.deviceProfileLib, line 548
    return [deviceName, deviceProfile] // library marker kkossev.deviceProfileLib, line 549
} // library marker kkossev.deviceProfileLib, line 550

// called from  initializeVars( fullInit = true) // library marker kkossev.deviceProfileLib, line 552
def setDeviceNameAndProfile( model=null, manufacturer=null) { // library marker kkossev.deviceProfileLib, line 553
    def (String deviceName, String deviceProfile) = getDeviceNameAndProfile(model, manufacturer) // library marker kkossev.deviceProfileLib, line 554
    if (deviceProfile == null || deviceProfile == UNKNOWN) { // library marker kkossev.deviceProfileLib, line 555
        logWarn "unknown model ${deviceModel} manufacturer ${deviceManufacturer}" // library marker kkossev.deviceProfileLib, line 556
        // don't change the device name when unknown // library marker kkossev.deviceProfileLib, line 557
        state.deviceProfile = UNKNOWN // library marker kkossev.deviceProfileLib, line 558
    } // library marker kkossev.deviceProfileLib, line 559
    def dataValueModel = model != null ? model : device.getDataValue('model') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 560
    def dataValueManufacturer  = manufacturer != null ? manufacturer : device.getDataValue('manufacturer') ?: UNKNOWN // library marker kkossev.deviceProfileLib, line 561
    if (deviceName != NULL && deviceName != UNKNOWN  ) { // library marker kkossev.deviceProfileLib, line 562
        device.setName(deviceName) // library marker kkossev.deviceProfileLib, line 563
        state.deviceProfile = deviceProfile // library marker kkossev.deviceProfileLib, line 564
        device.updateSetting("forcedProfile", [value:deviceProfilesV2[deviceProfile].description, type:"enum"]) // library marker kkossev.deviceProfileLib, line 565
        //logDebug "after : forcedProfile = ${settings.forcedProfile}" // library marker kkossev.deviceProfileLib, line 566
        logInfo "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was set to : <b>deviceProfile=${deviceProfile} : deviceName=${deviceName}</b>" // library marker kkossev.deviceProfileLib, line 567
    } else { // library marker kkossev.deviceProfileLib, line 568
        logWarn "device model ${dataValueModel} manufacturer ${dataValueManufacturer} was not found!" // library marker kkossev.deviceProfileLib, line 569
    }     // library marker kkossev.deviceProfileLib, line 570
} // library marker kkossev.deviceProfileLib, line 571

def refreshDeviceProfile() { // library marker kkossev.deviceProfileLib, line 573
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 574
    if (cmds == []) { cmds = ["delay 299"] } // library marker kkossev.deviceProfileLib, line 575
    logDebug "refreshDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 576
    return cmds // library marker kkossev.deviceProfileLib, line 577
} // library marker kkossev.deviceProfileLib, line 578

def configureDeviceProfile() { // library marker kkossev.deviceProfileLib, line 580
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 581
    logDebug "configureDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 582
    if (cmds == []) { cmds = ["delay 299"] }    // no ,  // library marker kkossev.deviceProfileLib, line 583
    return cmds     // library marker kkossev.deviceProfileLib, line 584
} // library marker kkossev.deviceProfileLib, line 585

def initializeDeviceProfile() // library marker kkossev.deviceProfileLib, line 587
{ // library marker kkossev.deviceProfileLib, line 588
    List<String> cmds = [] // library marker kkossev.deviceProfileLib, line 589
    logDebug "initializeDeviceProfile() : ${cmds}" // library marker kkossev.deviceProfileLib, line 590
    if (cmds == []) { cmds = ["delay 299",] } // library marker kkossev.deviceProfileLib, line 591
    return cmds         // library marker kkossev.deviceProfileLib, line 592
} // library marker kkossev.deviceProfileLib, line 593

void initVarsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 595
    logDebug "initVarsDeviceProfile(${fullInit})" // library marker kkossev.deviceProfileLib, line 596
    if (state.deviceProfile == null) { // library marker kkossev.deviceProfileLib, line 597
        setDeviceNameAndProfile() // library marker kkossev.deviceProfileLib, line 598
    }     // library marker kkossev.deviceProfileLib, line 599
} // library marker kkossev.deviceProfileLib, line 600

void initEventsDeviceProfile(boolean fullInit=false) { // library marker kkossev.deviceProfileLib, line 602
    logDebug "initEventsDeviceProfile(${fullInit})" // library marker kkossev.deviceProfileLib, line 603
} // library marker kkossev.deviceProfileLib, line 604


// ~~~~~ end include (142) kkossev.deviceProfileLib ~~~~~
