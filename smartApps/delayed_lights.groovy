definition(
    name: "Delayed Lights",
    namespace: "evcallia",
    author: "Evan Callia",
    description: "This SmartApp uses a motion sensor to trigger lights to turn on with the specified delay between each light.",
    category: "Convenience",
    iconUrl: "http://chapter-media.betterjournalism.org/sites/37/2015/04/24195919/IMG_8855.jpg",
    iconX2Url: "http://chapter-media.betterjournalism.org/sites/37/2015/04/24195919/IMG_8855.jpg"
)

preferences {
    page(name: "pageOne", title: "Initial Setup", nextPage: "pageTwo", install: false, uninstall: true) {
        // TODO: Make this an enum
        section("Select a motion sensor") {
      		input "sensorOne", "capability.motionSensor", title: "First Sensor", multiple: false, required: true
      	}
        section("Select light delay") {
              input "delay", "number", title: "Seconds", multiple: false, required: true
          }
        section("Select how long after activity stops to turn lights off") {
              input "minutes", "number", required: true, title: "Minutes", multiple: false
        }
        section("Select the number of lights you want to control") {
            input "numLights", "number", title: "Number of Lights", required: true
        }
        // TODO: Make this an enum
        // section("Select a door sensor on the other end") {
        //     input "sensorTwo", "capability.doorControl", title: "Second Sensor", multiple: false, required: false
        // }
    }

    page(name: "pageTwo", title: "Devices to Control", install: true, uninstall: false)
}

def pageTwo() {
    dynamicPage(name: "pageTwo") {
        section("Select your lights. Order Matters.") {
            for(int i = 0; i < numLights; i++) {
                input "light$i", "capability.switch", title: "Light $i", required: true, multiple: false
            }
        }

        section("Advanced Settings") {}

        section("Don't run in these modes") {
            input "noRunModes", "mode", title: "Modes", multiple: true, required: false
        }

        section("Don't run after these times") {
            input "sunNoRun", "enum", title: "Sunrise/Sunset", multiple: true, required: false, options: ["sunrise", "sunset"]
            input "sunOffset", "string", title: "Offset in the form HH:MM", multiple: false, required: false
            input "sunOffsetDirection", "enum", title: "Before or after sunset/sunrise", multiple: true, required: false, options: ["before", "after"]
        }
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    subscribe(sensorOne, "motion", motionHandler)
    // if (sensorTwo) {
    //     // subscribe(sensorTwo, "door.opening", doorHandler)
    // }
}

def motionHandler(evt) {
    log.debug "motionHandler called: $evt"
    def shouldRun = true

    //
    // This section will handle all advanced options and determine if we need to run
    //

    // Determine if should run based off hub mode
    if (noRunModes && location.mode in noRunModes){
        log.info("Hub mode set to $location.mode and no run modes are $noRunModes . Not running")
        shouldRun = false
    }

    // Determine if should run based of sunrise/sunset
    if (sunNoRun && shouldRun) {
        // Get offset info if any
        def offset = sunOffset
        if (!offset) {
            offset = "00:00"
        }
        if (sunOffsetDirection && sunOffsetDirection == "before") {
            offset = "-" + offset
        } else {
            offset = "+" + offset
        }

        // Get sunrise/set with offset and compare to activation time
        log.info("Check sunset with offet of $offset")
        def sunriseAndSunset = getSunriseAndSunset(sunsetOffset: offset)
        log.info("sunrise: $sunriseAndSunset.sunrise sunset: $sunriseAndSunset.sunset")
        def motionState = sensorOne.currentState("motion")
        def isAfterSunrise = timeOfDayIsBetween(sunriseAndSunset.sunrise, sunriseAndSunset.sunset, new Date(), location.timeZone)
        if ("sunrise" in sunNoRun && isAfterSunrise) {
            log.info("App set not to run after sunrise. Will not run")
            shouldRun = false
        }
        if ("sunset" in sunNoRun && !isAfterSunrise) {
            log.info("App set not to run after sunset. Will not run")
            shouldRun = false
        }
    }

    //
    // If the advanced options are met, then run
    //
    if (shouldRun){
        if (evt.value == "active") {
            // Check to see if the lights are already on. If it is then we don't need to run
            def lightState = settings["light0"].currentState("switch")
            if (lightState.value == "off") {
                LinkedHashSet lights = settings.keySet().findAll { it.contains("light") }
                for(int i = 0; i < lights.size(); i++){
                    log.debug "turring on light$i"
                    settings["light$i"].on()
                    pause(1000 * delay)
                }
            } else {
                log.info("Lights are already on. Will not turn on again")
            }

        } else if (evt.value == "inactive") {
            // runIn takes seconds a a param so convert
            runIn(60 * minutes, checkMotion)
        }
    }
}

def checkMotion() {
    log.debug "In checkMotion scheduled method"
    def motionState = sensorOne.currentState("motion")
    if (motionState.value == "inactive") {
        // Get the time elapsed between now and when the motion reported inactive
        def elapsed = now() - motionState.date.time

        // Elapsed time is in milliseconds, so the threshold must be converted to milliseconds too
        def threshold = 1000 * 60 * minutes
        if (elapsed >= threshold) {
            log.debug "Motion has stayed inactive long enough since last check ($elapsed ms): turning off lights"
            LinkedHashSet lights = settings.keySet().findAll { it.contains("light") }
            for(int i = 0; i < lights.size(); i++){
                log.debug "turring off light$i"
                settings["light$i"].off()
                pause(1000 * delay)
            }
        } else {
            log.debug "Motion has not stayed inactive long enough since last check ($elapsed ms): doing nothing"
        }
    } else {
        // Motion active; just log it and do nothing
        log.debug "Motion is active, do nothing and wait for inactive"
    }
}

// def doorHandler(evt) {
//
// }
