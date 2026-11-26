# OpenAssistant

A classic Google Assistant replacement for Android that replaces Gemini, runs your voice completely on your phone, uses **zero Google servers**, and uses **zero AI/LLMs**.

## How to Set It Up (3 Simple Steps)

1. **Install the APK** from the [Releases](https://github.com/blueprintnerd/OpenAssistant/releases) page.
2. Open **OpenAssistant** and tap **Download** on step 1 (**Voice Pack**).
3. Tap **Set as Default Assistant** to pick OpenAssistant instead of Gemini, then turn on **"Hey Google" Voice Match**.

That's it. Hold your power button, swipe from the corner, or say **"Hey Google"** anytime.

## What You Can Ask It

- **Timers & Alarms**: "Set a timer for 5 minutes", "Wake me up at 7:30 AM"
- **Phone Controls**: "Turn on the flashlight", "Volume up", "Set volume to 50%", "Battery level", "Open Spotify"
- **Calls & Texts**: "Call Mom", "Text Alex saying I'm on my way"
- **Weather**: "What's the weather?", "Weather in Chicago"
- **Math & Conversions**: "15% of 85", "42 times 17", "100 Fahrenheit to Celsius", "5 miles to km"
- **Quick Answers**: "Who is Nikola Tesla?", "Define serendipity", "What time is it in Tokyo?"
- **Lists & Reminders**: "Add milk to my shopping list", "Remind me to call the dentist in 30 minutes"
- **Media & Navigation**: "Pause music", "Next song", "Navigate to downtown"
- **Routines & Fun**: "Good morning", "Flip a coin", "Roll a d20", "Tell me a joke"
- **Smart Home (Optional)**: Connect your local Home Assistant server in Settings -> More Options.

## Privacy

- **Zero Google servers**: Wake word ("Hey Google" / "OK Google" / custom), speech recognition, and voice replies all run locally on your phone using Sherpa-ONNX.
- **Zero LLMs**: Fast, predictable, classic assistant commands.
- Weather uses Open-Meteo, definitions use Free Dictionary API, factual answers use Wikipedia & DuckDuckGo Instant Answers, and web searches use your choice of DuckDuckGo, Brave, Startpage, or Kagi.

## Building from Source

```bash
git clone https://github.com/blueprintnerd/OpenAssistant.git
cd OpenAssistant
gradle testDebugUnitTest assembleDebug
```

## License

MIT License. See [LICENSE](LICENSE).
