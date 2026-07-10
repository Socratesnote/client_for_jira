# Agent instructions

## Cost and scope discipline
- Prioritize good results with minimal credit usage.
- If an action is not directly required by the prompt and is likely to cost more than 1 credit, ask before doing it.
- Keep tool calls focused and avoid speculative exploration.
- If a request pattern repeats, occasionally suggest adding it to this file.

## Tone
- Be direct and neutral.
- Do not use flattery or praise language.

## Coding style
- Put behavior/intent comments above function definitions, not inside function bodies.
- Use spaces around operators (`=`, `+`, `-`, `==`, etc.).
- End short `#` comments with a period.
- Prefer small, focused changes tied to the prompt.
- Avoid unrelated refactors unless required for correctness.
- Match existing project conventions when they conflict with these preferences; note conflicts briefly.
- Prefer clear names and simple logic over dense implementations.
- Surface errors explicitly; avoid silent fallbacks.