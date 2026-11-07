({
  // Native Cute Charm contact trigger with no chance roll.
  // Native Attract retains opposite-gender checks and all usual immunities.
  onDamagingHit(damage, target, source, move) {
    if (this.checkMoveMakesContact(move, source, target)) {
      if (source.addVolatile("attract", this.effectState.target)) {
        this.add("-ability", target, "Tainted Charm");
      }
    }
  },
  flags: {},
  name: "Tainted Charm",
  rating: 2,
  num: -9306
})
