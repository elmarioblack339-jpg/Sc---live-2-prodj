# Native DSP playback path

The native player is a separate playback route, independent from Deck 1 and Deck 2.

- `ScLiveDspAudioProcessor` processes PCM inside the Media3 audio sink, before Android sends it to the selected output route.
- The five-band graphic/parametric EQ feeds a real two-way crossover.
- LOW is processed by a subsonic high-pass filter and a low-pass crossover. MID/HIGH receives the complementary high-pass crossover. The crossover frequency is adjustable from 40–400 Hz; subsonic protection is adjustable from 15–50 Hz.
- Crossover slopes are selectable: 12 dB/octave Butterworth, or 24/48 dB per octave Linkwitz-Riley-style cascades.
- With stereo output, LEFT carries LOW only and RIGHT carries MID/HIGH only. This is required to feed two separate physical amplifier inputs from a stereo output.
- For physical separation, use a stereo breakout cable/interface: left output to the LOW amplifier input and right output to the MID/HIGH amplifier input. Do not combine the two channels before the amplifiers. If Android or an external device downmixes the output to mono, the two bands will be recombined and cannot remain physically separate.
- The six legacy output-level values are grouped into two real output levels: LOW (the first two values) and MID/HIGH (the remaining four values). Each amplifier also has independent power and volume controls.
- A final soft limiter is applied in this native PCM route. CI verifies compilation and wiring; it cannot certify a particular phone, Bluetooth speaker, USB interface, cable, or physical amplifier.
- YouTube playback inside the browser remains a separate WebView audio path and is not processed by this native-player class.
