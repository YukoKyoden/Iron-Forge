# Kingdoms & Claims 0.4.26.4

Registered wheat fields now credit the kingdom stockpile passively once per scheduled minute. Each hydrated, planted wheat plot contributes one wheat per cycle, matching the field's displayed maximum rate. Growth stage does not need to be reset or harvested. The mod never writes crop block states during surveys or production; vanilla crops can mature normally and a full-grown field stays full-grown.

The field must remain planted, hydrated, loaded and in owned territory. Destroyed/invalid fields stop contributing. Full storage limits actual credited wheat; only accepted output appears in the Production history. Ordinary manual harvesting remains available. No saved building/stockpile data migration is needed.

This removes per-plant growth resets, crop block updates and the associated production-triggered client notifications. Production remains server-owned and bounded to its existing schedule. Army-map redesign remains a separate design discussion.
