# Native DSP playback path

The native player is being built as a separate playback route, independent from Deck 1 and Deck 2.

- `ScLiveDspAudioProcessor` processes PCM inside the Media3 audio sink, before Android outputs it to the currently selected route (including a connected Bluetooth speaker).
- It does not use Android's deprecated global session-0 effects and does not capture or alter calls, alarms, notifications, or other applications.
- The first processing stage exposes four configurable EQ bands, crossover frequencies, master gain, and a soft limiter.
- This file is the DSP stage only. It is not yet wired to the native player UI or the existing DriverRack controls. Do not display an active status or claim playback is processed until that wiring is complete and verified.
- YouTube playback inside the existing browser remains a separate WebView audio path and is not processed by this class.
- Bluetooth routing is delegated to Android's normal audio route; the Corn Sound Plus 8-inch speaker is a physical validation target, not a route we can certify from CI.
