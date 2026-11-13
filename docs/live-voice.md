# Live voice

The purple microphone starts a live voice conversation. It is SAIF AI's own code (`app/src/main/java/com/example/live`).

## How a conversation runs

1. `SaifLive.startLive` checks two things: the microphone permission, and that something can answer (a provider
   API key in Settings, or the connected chat agent). If one is missing the reason is shown with the matching button.
2. `SaifLiveService` starts as a foreground service of type "microphone" and shows a notification with a Stop
   button. It holds a wake lock, so the conversation goes on with the screen off.
3. `LiveTurnLoop` does the talking: it listens until the user stops speaking, turns the speech into text, gets the
   answer and speaks it with the voice chosen in Settings. Talking while SAIF AI speaks cuts it off (barge-in).
4. The answer comes from, in this order: the phone agent (for "open ...", "call ..." and similar tasks), the chat
   agent (for coding and file requests), or the provider and model chosen in Settings.
5. After each exchange the app-wide memory learns from it, the same as from a typed chat.
6. A session nobody speaks into for 30 minutes ends by itself. Signing out of the app account ends it at once.

## Speech to text

OpenAI, Groq and Gemini keys transcribe in the cloud. For every other provider the words are recognised on the
phone with the Vosk library (`OfflineSpeech`). Its English model (about 40 MB) is downloaded once from the Vosk
project's server into the app's private storage; until it is there, the live screen says so instead of asking for
another key.

## Sharing the screen

Sharing is off until the user taps "Screen" inside the conversation and allows the Android capture dialog. The
service then also becomes a "mediaProjection" service (Android 14 requires that order). The screen is mirrored at
a reduced size (longest side 1024 pixels), at most one picture per second, as JPEG in memory only. A provider
that can see images gets the newest picture with the question; other providers get the visible text of the screen
from the accessibility service instead. Sharing stops with the conversation, with the "Screen on" button, or when
Android ends the capture.

## The orb

`SaifLiveOrb` is drawn with Compose Canvas: a glowing core, three orbit rings and a circle of sound bars. Violet
means listening, deep violet with chasing arcs means thinking, green with travelling rings means speaking. A tap
mutes the microphone (or cuts SAIF AI off while it speaks); a long press ends the conversation.

## Not verified on a phone yet

The engine is compiled and unit-tested by the CI build. Before a release, check on a device: start and stop,
barge-in, mute, the first download of the speech model, and screen sharing on Android 14 or newer.
