# Compliance Audit Pipeline — What This Project Is and Why

## The idea in one paragraph

Banks and financial firms have to watch every trade that happens and flag anything suspicious — a trade with a sanctioned country, an unusually large transaction, or a customer suddenly trading way more than usual. Today, a lot of that checking happens after the fact, in batches, sometimes hours or even a day later. This project builds a small system that does that checking **the moment a trade happens**, automatically, without a human needing to run a report or click a button. It's a working, hands-on demonstration of how a bank might build a real-time compliance monitoring system.

## Why build this

Two reasons, one practical and one personal:

1. **It's a real, useful pattern in banking and fintech.** Nomura, Barclays, JPMC, and basically every financial institution needs exactly this kind of thing: something happens (a trade, a payment, a transfer), and the system needs to react to it instantly and reliably — checking it against rules, flagging risk, alerting the right people — without slowing down the original transaction or ever silently dropping an event. That reliability requirement is the hard part, and it's what makes this a genuinely interesting engineering problem rather than a toy exercise.

2. **It's a portfolio project for interviews.** Since interviewing at Barclays and JPMC (alongside working at Nomura), having a real, working system — not just a slide deck — to point to and talk through in an interview is far more convincing than describing skills on a resume. This project is built specifically so it can be demoed live, walked through end-to-end, and discussed in technical depth, including the real bugs hit and fixed along the way (which interviewers tend to find more interesting than a project that "just worked").

## The core problem it solves

Imagine a trade gets recorded in a database, and separately, a message needs to go out saying "hey, a trade just happened, someone should check it." The tricky part: what if the trade gets saved but the "someone should check it" message never goes out, because the system crashed at the wrong moment, or the network blipped? Now you have a trade sitting in the system that nobody ever reviewed for compliance — which, in a bank, is a real problem.

This project solves that using a well-known technique sometimes called the **transactional outbox pattern**: instead of trying to save the trade AND send the message as two separate, riskier steps, the system saves the trade AND a "please send this message" note in the very same database transaction — so either both happen, or neither does. There's no in-between state where one succeeded and the other silently failed. A separate piece of the system then reliably picks up that note and turns it into a real message for the rest of the pipeline to react to.

## How the pieces fit together (plain-language version)

- **A trade comes in.** Something (a trading system, in real life) records a trade — who traded, with whom, how much, in which country.
- **The trade is safely recorded, with a "check this" note attached**, using the reliability trick described above.
- **A watcher notices the note and passes it along** to a messaging system, which is essentially a reliable, high-speed conveyor belt for "things that just happened" — this is where Kafka comes in, a tool used heavily in banking for exactly this kind of real-time event streaming.
- **A compliance checker picks it up off the belt and runs it through a set of rules** — is the counterparty in a sanctioned country? Is the trade unusually large? Has this account been trading suspiciously often in a short window? Each rule is simple on its own, but running them automatically, on every trade, the instant it happens, is the valuable part.
- **If something looks wrong, an alert goes out immediately**, ready to show up on a live dashboard a compliance officer could be watching in real time — no waiting for an end-of-day report.

The system is also built to survive the messy realities of real infrastructure: messages sometimes get delivered twice (the system is built to recognize and ignore duplicates, so nothing gets double-counted), and pieces of the system can restart or briefly go down without losing data.

## The plan, phase by phase

- **Phase 0 — Foundation.** Set up all the supporting infrastructure locally (the database, the messaging system, and the tool that watches the database for changes) so the rest of the project has something to run on.
- **Phase 1 — Recording trades.** Build the part of the system that takes in a new trade and safely records it, using the "save the trade and the note together" trick described above. *(Done — proven working end-to-end.)*
- **Phase 2 — Checking trades.** Build the part that picks up each trade the moment it's recorded, runs it through the compliance rules, and raises an alert if something looks off. *(Done — proven working end-to-end, after tracking down and fixing several real infrastructure bugs along the way — documented in detail in `PROGRESS.md`.)*
- **Phase 3 — Seeing it happen live.** Build a simple live dashboard that shows compliance alerts appearing in real time, the moment they're raised — so the whole system is something that can actually be watched working, not just described. *(Not started yet — next up.)*

## Why this makes a strong talking point

Beyond the finished result, the process of building this hit several real, subtle problems along the way — the kind that come from actually running infrastructure rather than just writing code: timing quirks, configuration mismatches between the different pieces, and one especially interesting bug around how two different tools disagreed on how to represent a date. Working through and explaining those is arguably more valuable in an interview than a system that never had any problems at all — it shows real debugging ability, not just following a tutorial.
