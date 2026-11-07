({
  /* Native Tangling Hair contact handling; Attack replaces Speed. */
  onDamagingHit(damage, target, source, move) {
    if (this.checkMoveMakesContact(move, source, target, true)) {
      this.add("-ability", target, "Futakuchi's Grasp");
      this.boost({ atk: -1 }, source, target, null, true);
    }
  },
  flags: {},
  name: "Futakuchi's Grasp",
  rating: 2.5,
  num: -9308
})
