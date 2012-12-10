audioAndLocationRecorder
========================

This is an android app written by Cormac Parle for Bat Conservation Ireland.

One of the ways Bat Conservation Ireland monitors bat populations is via car surveys. Volunteers drive along pre-defined routes at specified times of the year with a bat detector (a device that transforms bats' ultrasonic sonar into human-audible sounds) plugged into a recording device. The recordings are later analysed to determine which species are present, and the species records undergo statistical processing to determine whether populations are rising, falling, or stable.

This app allows an android phone to be used as the recording device. The volunteer enters a 3-character code at the beginning of their transect, and the device records both the audio from the bat detector (to a .wav file) and its own position (to a .csv file).

Reported issues
----------------

1. when phone screen changes orientation, app stops recording location data
2. When you hit stop, the time it takes to register the hit is too long, it seems like you didn’t hit it at all.
3. When the ‘Are you sure?’ question comes up the Yes and No are too close together for people with large fingers. 
4. Team V93 had a card error prior to start of Transect 7. It seems that the app kept recording sound but stopped recording GPS and so when they tried to start Transect 7 they got a card error as the phone was still writing sound to the card. This sound file was +3GB in size and could not be opened because of its large size.
5. Not able to hear sound as it is being recorded, so impossible for volunteer to know if recording is proceeding correctly (e.g. if a lead is faulty). Don't know if there's a software fix for this, unless the audio data could be visualised in real time on the screen, like on commercial audio recording software.
