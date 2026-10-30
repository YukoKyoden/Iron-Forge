# Kingdoms & Claims v0.3.8 — Treasury Interface & Deposit Repair

Forge 1.20.1 / Java 17. Back up worlds before upgrading.

## War Table treasury

- Open the War Table and select **TREASURY** (third right-side tab), rather than scrolling the Operations orders.
- Balances for treasury iron and wheat are shown at the top. Deposit up to 16, up to 64, or ALL carried ingots/wheat (up to 2,304 per transaction). A partial stack is accepted; no longer require exactly 16/64 items.
- Server counts the player's actual item stacks before consuming items and credits the correct faction treasury. Success or error is echoed in the treasury panel. Requires leadership and proximity to the War Table, same as other military actions.
- Five-minute upkeep panel shows required iron/wheat, estimated settlement size, seconds until next payment, status of last charge, whether the next charge is fully funded, and cumulative successful charges.
- Existing Treasury balances and operations saves are unchanged.

## Test

1. Ensure 3 iron ingots and 7 wheat in inventory. Treasury > Iron <=16 should deposit 3, Wheat <=16 should deposit 7 (without needing a whole stack).
2. Confirm balances and success messages. If empty inventory, button should show explicit error instead of appearing to do nothing.
3. Deposit enough for a payment, check 'Next charge: COVERED'. Leave game running five minutes in an unpaused world, refresh treasury and check that balance decreased and successful payments increased.
4. Remove resources, check 'Next charge: INSUFFICIENT'. After next payment, 'Last charge: UNPAID' and operations should pause.
5. Switch Army, Operations, Treasury; test map icon selection and army movement rules still work.

Compiling this build is not a substitute for live game testing.
