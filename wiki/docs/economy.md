# Economy modules and balancing

Money features need Vault and an actual economy provider. Choose a starting economy and test transfers before opening orders, gambling or reward systems.

## Player orders

The orders board lets players request items for payment. Test order posting, escrow deduction, partial fulfillment, complete fulfillment, bids and cancellation using two accounts. Check item metadata rules so renamed or custom items are not accidentally treated as interchangeable. Review configured price and quantity limits rather than assuming the custom-amount GUI has no cap.

## Bank notes

Withdrawals create redeemable value. Check that withdrawals decrease the original balance/experience, and redemption increases it once. Verify displayed withdrawn and remaining amounts against actual state. Test full inventory and repeated use; never issue compensation before checking whether the note or payout already exists.

## Wishing well

Review contribution limits, draw timing and payouts. A scheduled draw requires the correct server clock/time assumptions. Test a restart around draw time and ensure the same pot cannot pay twice. Explain your configured odds to players instead of describing contribution as a guaranteed return.

## Harvest market and archaeology

Compare seasonal multipliers and mutation payouts with every other shop. Buying an input cheaply and selling a crafted or harvested output elsewhere can create an unintended loop. Test brushing to completion and cancellation; repeated partial brushing should not mint unlimited fragments.

## Gambling

Review min/max wagers, payout multipliers and each game's configured odds. Player-versus-player wagers and house games have different fund flows. Validate balances and recorded statistics across wins, losses, disconnects and restart.

The underlying audit feature can change real odds for configured accounts. Public defaults remove the owner account but do not remove that implementation. Keep it unconfigured for ordinary gameplay unless you explicitly disclose and intend the behavior. Do not use misleading leaderboard presentation to describe actual economy results.

## A practical balancing process

1. List all sources of money, keys and materials across enabled modules.
2. List sinks: purchases, repairs, entry fees, upgrades and consumables.
3. Simulate one new player and one optimized experienced player over the same period.
4. Check interactions with external shops, crafting recipes and farms.
5. Change one group of values, document it and observe actual transactions.

Default prices and rewards are examples, not a guarantee of a balanced economy on another server.
