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
      	section("Select a motion sensor") {
      		input "motionSensor", "capability.motionSensor", title: "Which Motion Sensor?", multiple: false, required: true
      	}
        section("Select light delay") {
              input "delay", "number", title: "Seconds", multiple: false, required: true
          }
        section("Select how long after motion stops to turn lights off") {
              input "minutes", "number", required: true, title: "Minutes", multiple: false
        }
        section("Select the number of lights you want to control") {
            input "numLights", "number", title: "Number of Lights", required: true
        }
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
    subscribe(motionSensor, "motion", motionHandler)
}

def motionHandler(evt) {
    log.debug "motionHandler called: $evt"
    if (evt.value == "active") {
        LinkedHashSet lights = settings.keySet().findAll { it.contains("light") }
        for(int i = 0; i < lights.size(); i++){
            log.debug "turring on light$i"
            settings["light$i"].on()
            pause(1000 * delay)
        }
    } else if (evt.value == "inactive") {
        runIn(60 * minutes, checkMotion)
    }
}

def checkMotion() {
  log.debug "In checkMotion scheduled method"
  def motionState = motionSensor.currentState("motion")
  if (motionState.value == "inactive") {
        // get the time elapsed between now and when the motion reported inactive
        def elapsed = now() - motionState.date.time

        // elapsed time is in milliseconds, so the threshold must be converted to milliseconds too
        def threshold = 1000 * 60 * minutes
        if (elapsed >= threshold) {
            log.debug "Motion has stayed inactive long enough since last check ($elapsed ms):  turning off lights"
            LinkedHashSet lights = settings.keySet().findAll { it.contains("light") }
            for(int i = 0; i < lights.size(); i++){
                log.debug "turring off light$i"
                settings["light$i"].off()
                pause(1000 * delay)
            }
        } else {
            log.debug "Motion has not stayed inactive long enough since last check ($elapsed ms):  doing nothing"
        }
    } else {
        // Motion active; just log it and do nothing
        log.debug "Motion is active, do nothing and wait for inactive"
    }
}
