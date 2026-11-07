({
  /* Native Gooey behavior, including contact protection and stat-drop rules. */
  onDamagingHit(damage, target, source, move) {
    if (this.checkMoveMakesContact(move, source, target, true)) {
      this.add("-ability", target, "Inky Love");
      this.boost({ spe: -1 }, source, target, null, true);
    }
  },
  flags: {},
  name: "Inky Love",
  rating: 2,
  num: -9307
})
