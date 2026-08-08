# ClipLex UI V2 design direction

## Design read

ClipLex is an Android-native language-learning product for people who turn real media moments into private lessons. The interface should feel playful enough to encourage daily use, but mature enough to keep captured media, transcript accuracy and learning progress at the center.

- Design variance: 7/10
- Motion intensity: 5/10
- Visual density: 4/10
- Platform mode: Android-native premium
- Palette: mineral neutrals with one teal product accent
- Signature surfaces: media capture stage, compact learning path, integrated captions, transcript timeline

## Audit of V1

V1 fixed the major usability problems, especially bounded video captions and scrollable audio text. Its remaining design weaknesses were mostly systemic:

- Too many raised cards and nested containers
- Pill-shaped controls used for selectors, status and navigation at the same time
- Green, blue, purple, amber and coral competing as product accents
- Mascot and celebration styling appearing on too many functional screens
- Heavy button depth making the interface feel toy-like
- Repeated all-caps labels and decorative emoji
- Transcript cards behaving like isolated widgets instead of one continuous lesson
- Hard-coded streak presentation without a real streak data source
- Determinate-looking preparation progress without a real percentage

## V2 system

### Hierarchy

Captured media is the strongest visual element. Supporting controls are quieter and placed close to the task they affect. Full transcript content stays below the player and never competes with playback.

### Color

Teal is the only product accent. Amber communicates learning momentum or the currently spoken word. Coral is reserved for errors and destructive actions. Existing blue and purple aliases resolve to the same teal family so older screens remain coherent during gradual migration.

### Surfaces

Cards are used only when grouping or elevation clarifies hierarchy. Most V2 cards are flat with one border. Icon containers use compact squircles rather than circles everywhere. Selector controls use moderate corner radii instead of full pills.

### Motion

Press feedback is subtle scaling rather than a large fake 3D slab. Continuous motion is limited to states where it communicates listening or processing. The preparation screen uses indeterminate progress because the pipeline does not expose a reliable completion percentage.

### Typography and copy

Headings use fewer extra-bold weights. Body text has more breathing room. Labels use sentence case. Copy is direct and functional. Decorative emoji, fake metrics and repeated micro-labels are removed.

## Key V2 changes

- Rebuilt the home capture experience as a dark media stage with waveform focus
- Replaced the hard-coded streak count with an honest Today state
- Replaced the thick daily progress bar with a three-step learning path
- Integrated video captions into a bottom gradient scrim
- Kept video captions bounded and reduced the moving token window
- Kept audio captions bounded and internally scrollable
- Reworked transcript moments into a continuous timeline with a current-state accent
- Simplified saved-word cards into a dictionary-like hierarchy
- Rebuilt lesson history around real lesson and duration metrics
- Replaced fake preparation percentage with indeterminate progress
- Unified existing secondary colors into one accent family
- Flattened shared cards, buttons, selectors, icon containers and navigation

## Functional guardrails

- Video and audio playback behavior is unchanged
- Caption bounds and transcript scrolling remain enforced
- Word lookup, saving and pronunciation actions remain available
- Capture, overlay, history, practice, onboarding and model-management flows retain their existing entry points
- The safe debug variant remains the validation and APK output target
