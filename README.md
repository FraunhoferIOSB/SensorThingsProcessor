# SensorThingsProcessor [![Build Status](https://github.com/FraunhoferIOSB/SensorThingsProcessor/workflows/Maven%20Build/badge.svg)](https://github.com/FraunhoferIOSB/SensorThingsProcessor/actions)
Automatic processors for the OGC SensorThings API

## Configuring

Start the jar with no options to open the configuration GUI.
```
java -jar SensorThingsProcessor-0.10-jar-with-dependencies.jar
```


## Running

The Processor takes the following command line options.
```
-noact -n :
    Read the file and give output, but do not actually post observations.

-config -c [file path] :
    The path to the config json file.

-daemon -d :
    Run in daemon mode, not listening for 'Enter' to exit.

-online -o :
    Run in on-line mode, listening for changes and processing as needed.
```

Start the Processor with no options to open the configuration GUI.
