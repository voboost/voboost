# Design

One root Java coordinator owns the canonical prepared machine, API session and product mutation sequence. A cross-process file lease prevents concurrent root runners; the Voyemu ownership receipt remains authoritative for the remote session. Durable reservations precede mutation dispatch. Unknown outcomes permit reconciliation only and prevent automatic guest shutdown.

The existing functional plan remains the requirement source. Required agent scenarios are matched against the candidate catalog, including target-level applicability. Missing scenarios or evidence block full acceptance. Focused verification remains distinct.

Implementation precedes one candidate build and the complete acceptance cycle. Source changes, packaging and runtime evidence are separate outcomes.
