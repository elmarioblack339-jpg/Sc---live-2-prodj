# Native DSP playback path

The native player is a separate playback route, independent from Deck 1 and Deck 2.

- `ScLiveDspAudioProcessor` processes PCM inside the Media3 audio sink, before Android sends it to the selected output route.
- The DriveRack EQ has five separate sections: LOW, LOW-MID, MID, HIGH-MID, and HIGH. It supports fixed graphic centers and a parametric mode with per-band frequency and Q.
- The six crossover outputs use these exact ranges: LOW 1 25–80 Hz; LOW 2 90–240 Hz; MID 1 300–400 Hz; MID 2 500–700 Hz; HIGH 1 1,000–4,000 Hz; HIGH 2 10,000–16,000 Hz. Frequencies outside those intervals are not assigned to an output band.
- Each output has an independent level. The low amplifier controls LOW 1/2; the mid/high amplifier controls MID 1/2 and HIGH 1/2. Amplifiers provide power and output volume only.
- The native bridge receives DriveRack EQ, output levels, amplifier power/volume, master gain, and limiter settings from the SC LIVE 2 controls.
- A final soft limiter is applied in this native PCM route. CI verifies compilation and wiring; it cannot certify a particular phone, Bluetooth speaker, or physical amplifier.
- YouTube playback inside the browser remains a separate WebView audio path and is not processed by this native-player class.
